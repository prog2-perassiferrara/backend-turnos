package com.prog2.appointment.integration.application.usecases;

import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.out.CatalogTechnicalTokenGateway;
import com.prog2.appointment.integration.domain.ports.out.CentralIntegrationGateway;

/** Conserva el acceso técnico y coordina su readquisición bajo demanda. */
@RequiredArgsConstructor
public final class TechnicalAccessUseCaseImpl implements TechnicalAccessUseCase {
    private static final System.Logger LOG = System.getLogger(TechnicalAccessUseCaseImpl.class.getName());
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private final CatalogTechnicalTokenGateway catalog;
    private final CentralIntegrationGateway central;
    private final Clock clock;
    private final String expectedGroup;
    private final Executor executor;
    // El monitor protege la admisión y la sesión. Las llamadas HTTP se hacen fuera del monitor.
    private TechnicalAccess current;
    private String rejectedToken;
    private boolean initialized;
    private boolean acquiring;
    private Instant nextAttempt; // Sin fecha antes del arranque o tras un fallo permanente.

    /**
     * Ejecuta una vez el ciclo inicial, con hasta tres intentos ante fallos recuperables.
     */
    public void initialize() {
        synchronized (this) {
            if (initialized) return;
            initialized = true;
            acquiring = true;
        }
        acquire(3);
    }

    @Override
    public TechnicalAccess getAccess() {
        synchronized (this) {
            var now = clock.instant();
            if (current != null && current.token().isUsableAt(now)) return current;
            if (acquiring || nextAttempt == null || now.isBefore(nextAttempt)) {
                throw new TechnicalAccessUnavailableException();
            }
            current = null;
            acquiring = true;
        }
        try {
            executor.execute(() -> acquire(1));
        } catch (RejectedExecutionException _) {
            finishAcquisition(null, AccessFailure.TEMPORARY_UNAVAILABLE);
        }
        throw new TechnicalAccessUnavailableException();
    }

    @Override
    public synchronized void rejectAccess(String token) {
        if (current != null && current.token().value().equals(token)) {
            rejectedToken = token;
            current = null;
        }
    }

    private void acquire(int maxAttempts) {
        TechnicalAccess access = null;
        AccessFailure failure = AccessFailure.INVALID_RESPONSE;
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    access = fetchAccess();
                    LOG.log(System.Logger.Level.INFO, "Technical access available");
                    return;
                } catch (IntegrationGatewayException exception) {
                    failure = exception.failure();
                    LOG.log(System.Logger.Level.WARNING, "Technical access attempt {0}/{1}: {2}",
                            attempt, maxAttempts, failure);
                    if (!failure.isRetryable()) return;
                }
            }
        } finally {
            finishAcquisition(access, failure);
        }
    }

    private TechnicalAccess fetchAccess() {
        var token = catalog.fetchToken();
        if (token == null) throw new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE);
        synchronized (this) {
            if (!token.isUsableAt(clock.instant()) || token.value().equals(rejectedToken)) {
                throw new IntegrationGatewayException(AccessFailure.CENTRAL_TOKEN_REJECTED);
            }
        }
        var configuration = fetchConfiguration(token);
        if (configuration == null || !expectedGroup.equals(configuration.groupId()) || !configuration.isComplete()) {
            throw new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE);
        }
        if (!token.isUsableAt(clock.instant())) {
            throw new IntegrationGatewayException(AccessFailure.CENTRAL_TOKEN_REJECTED);
        }
        return new TechnicalAccess(token, configuration);
    }

    private IntegrationConfiguration fetchConfiguration(TechnicalToken token) {
        try {
            return central.fetchConfiguration(token);
        } catch (IntegrationGatewayException exception) {
            if (exception.failure() == AccessFailure.CENTRAL_TOKEN_REJECTED) {
                synchronized (this) { rejectedToken = token.value(); }
            }
            throw exception;
        }
    }

    private synchronized void finishAcquisition(TechnicalAccess access, AccessFailure failure) {
        current = access;
        acquiring = false;
        if (access != null || failure.isRetryable() || failure == AccessFailure.CENTRAL_TOKEN_REJECTED) {
            // Un 401 central detiene el ciclo actual. Otro JWT puede estar disponible más adelante.
            nextAttempt = clock.instant().plus(RETRY_DELAY);
        } else {
            nextAttempt = null;
        }
    }
}

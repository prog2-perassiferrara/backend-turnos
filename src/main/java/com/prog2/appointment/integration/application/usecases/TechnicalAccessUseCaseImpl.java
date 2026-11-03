package com.prog2.appointment.integration.application.usecases;

import lombok.RequiredArgsConstructor;
import java.time.Clock;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.out.CatalogTechnicalTokenGateway;
import com.prog2.appointment.integration.domain.ports.out.CentralIntegrationGateway;

/** Acceso adquirido al arrancar y conservado en memoria, sin renovación ni tareas propias. */
@RequiredArgsConstructor
public final class TechnicalAccessUseCaseImpl implements TechnicalAccessUseCase {
    private static final System.Logger LOG = System.getLogger(TechnicalAccessUseCaseImpl.class.getName());
    private final CatalogTechnicalTokenGateway catalog;
    private final CentralIntegrationGateway central;
    private final Clock clock;
    private final String expectedGroup;
    private volatile TechnicalAccess current;

    /**
     * Inicializa una vez desde el arranque: hasta tres intentos de GET ante fallos recuperables.
     * Sin acceso completo, el backend conserva sus operaciones locales; otra adquisición requiere reinicio.
     * No imprime tokens, cuerpos ni mensajes externos y nunca efectúa login directo en cátedra.
     */
    public void initialize() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var token = catalog.fetchToken();
                if (token == null || !token.isUsableAt(clock.instant())) {
                    throw new IntegrationGatewayException(AccessFailure.CENTRAL_TOKEN_REJECTED);
                }
                var configuration = central.fetchConfiguration(token);
                if (configuration == null || !expectedGroup.equals(configuration.groupId())
                        || !configuration.isComplete()) {
                    throw new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE);
                }
                if (!token.isUsableAt(clock.instant())) {
                    throw new IntegrationGatewayException(AccessFailure.CENTRAL_TOKEN_REJECTED);
                }
                current = new TechnicalAccess(token, configuration);
                LOG.log(System.Logger.Level.INFO, "Technical access available");
                return;
            } catch (IntegrationGatewayException failure) {
                LOG.log(System.Logger.Level.WARNING, "Technical access attempt {0}/3: {1}", attempt, failure.failure());
                if (!failure.failure().isRetryable()) return;
            }
        }
    }

    @Override
    public TechnicalAccess getAccess() {
        var access = current;
        if (access == null || !access.token().isUsableAt(clock.instant())) {
            throw new TechnicalAccessUnavailableException();
        }
        return access;
    }

    @Override
    public void rejectAccess(String rejectedToken) {
        var access = current;
        if (access != null && access.token().value().equals(rejectedToken)) current = null;
    }
}

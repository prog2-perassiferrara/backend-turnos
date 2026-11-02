package com.prog2.appointment.reservation.domain.model;

import com.prog2.appointment.reservation.domain.enums.CancellationKind;
import com.prog2.appointment.reservation.domain.enums.CancellationState;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Intención durable con presupuesto por ciclo; el reloj no acredita un resultado central. */
public record CancellationProgress(
        CancellationKind kind,
        CancellationState state,
        Instant requestedAt,
        int retryVersion,
        int attempts,
        Instant nextAttemptAt,
        String lastFailure) {
    public static final int MAX_ATTEMPTS = 3;

    public CancellationProgress {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(state);
        Objects.requireNonNull(requestedAt);
        if (retryVersion < 0 || attempts < 0 || attempts > MAX_ATTEMPTS
                || (state == CancellationState.RESOLVED) != (nextAttemptAt == null)) {
            throw new IllegalArgumentException("Invalid cancellation progress");
        }
    }

    public static CancellationProgress create(CancellationKind kind, Instant now) {
        return new CancellationProgress(kind, CancellationState.PENDING, now, 0, 0, now, null);
    }

    /** Esperar el plazo central no consume presupuesto de efectos ni de reconciliación. */
    public CancellationProgress awaitExpiry(Instant expiresAt) {
        return new CancellationProgress(kind, state, requestedAt, retryVersion, attempts,
                expiresAt.isAfter(requestedAt) ? expiresAt : requestedAt, lastFailure);
    }

    public boolean isDue(Instant now) {
        return state == CancellationState.PENDING && attempts < MAX_ATTEMPTS
                && !now.isBefore(nextAttemptAt);
    }

    /** @return si el propietario puede abrir otro ciclo explícito, nunca por un GET o replay */
    public boolean canRetry(Instant now) {
        return state != CancellationState.RESOLVED
                && (state == CancellationState.REJECTED || attempts == MAX_ATTEMPTS)
                && !now.isBefore(nextAttemptAt);
    }

    /** Una versión vieja es replay; solo la versión actual disponible abre un ciclo. */
    public CancellationProgress retry(int expectedVersion, Instant now) {
        if (expectedVersion < 0) throw new IllegalArgumentException("Invalid retry version");
        if (expectedVersion < retryVersion || expectedVersion == retryVersion && state == CancellationState.RESOLVED) return this;
        if (expectedVersion != retryVersion || !canRetry(now) || retryVersion == Integer.MAX_VALUE) {
            throw new ReservationCancellationException(ReservationFailure.CANCELLATION_RETRY_NOT_READY);
        }
        return new CancellationProgress(kind, CancellationState.PENDING, requestedAt,
                retryVersion + 1, 0, now, null);
    }

    /** La reclamación consume presupuesto antes de salir de la transacción local. */
    public CancellationProgress claim(Instant now, Duration executionBudget) {
        if (!isDue(now)) return this;
        return new CancellationProgress(kind, state, requestedAt, retryVersion, attempts + 1,
                now.plus(executionBudget), ReservationFailure.CANCELLATION_UNCERTAIN.name());
    }

    /** @return si el callback pertenece a la reclamación todavía vigente */
    public boolean matches(CancellationProgress claimed) {
        return claimed != null && state == CancellationState.PENDING
                && retryVersion == claimed.retryVersion && attempts == claimed.attempts;
    }

    public CancellationProgress failed(String code, boolean rejected, Instant now,
            Duration retry, Duration cooldown) {
        if (state == CancellationState.RESOLVED) return this;
        return new CancellationProgress(kind,
                rejected ? CancellationState.REJECTED : CancellationState.PENDING,
                requestedAt, retryVersion, attempts,
                now.plus(rejected || attempts == MAX_ATTEMPTS ? cooldown : retry), code);
    }

    /** Una confirmación nueva habilita continuar el abandono, sin crear otro presupuesto. */
    public CancellationProgress confirmed(Instant now) {
        if (attempts == MAX_ATTEMPTS || state == CancellationState.RESOLVED
                || state == CancellationState.PENDING && attempts > 0) return this;
        return new CancellationProgress(kind, CancellationState.PENDING, requestedAt, retryVersion,
                attempts, now, lastFailure);
    }

    public CancellationProgress resolve() {
        return state == CancellationState.RESOLVED ? this
                : new CancellationProgress(kind, CancellationState.RESOLVED, requestedAt,
                        retryVersion, attempts, null, null);
    }
}

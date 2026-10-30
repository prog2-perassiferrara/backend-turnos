package com.prog2.appointment.reservation.application.exception;

import java.util.UUID;
import java.time.Instant;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;

/**
 * Rechazo de la operación propia; processId solo se adjunta después de comprobar su propietario.
 */
public final class ReservationOperationException extends RuntimeException {
    private final ReservationFailure failure;
    private final UUID processId;
    private final Instant recheckAt;

    public ReservationOperationException(ReservationFailure failure, UUID processId) {
        this(failure, processId, null);
    }

    public ReservationOperationException(
            ReservationFailure failure, UUID processId, Instant recheckAt) {
        super("Reservation operation could not complete");
        this.failure = failure;
        this.processId = processId;
        this.recheckAt = recheckAt;
    }

    public ReservationFailure failure() {
        return failure;
    }

    /**
     * @return instante central para reconsultar la posición HELD; no es un hold propio
     */
    public Instant recheckAt() {
        return recheckAt;
    }

    public UUID processId() {
        return processId;
    }
}

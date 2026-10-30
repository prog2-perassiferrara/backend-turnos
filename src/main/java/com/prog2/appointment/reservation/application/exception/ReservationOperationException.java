package com.prog2.appointment.reservation.application.exception;

import java.util.UUID;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;

/**
 * Rechazo de la operación propia; processId solo se adjunta después de comprobar su propietario.
 */
public final class ReservationOperationException extends RuntimeException {
    private final ReservationFailure failure;
    private final UUID processId;

    public ReservationOperationException(ReservationFailure failure, UUID processId) {
        super("Reservation operation could not complete");
        this.failure = failure;
        this.processId = processId;
    }

    public ReservationFailure failure() {
        return failure;
    }

    public UUID processId() {
        return processId;
    }
}

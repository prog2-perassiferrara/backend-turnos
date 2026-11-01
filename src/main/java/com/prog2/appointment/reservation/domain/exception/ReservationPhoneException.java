package com.prog2.appointment.reservation.domain.exception;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;

/** Rechazo seguro de una operación atómica del repositorio de respuestas. */
public final class ReservationPhoneException extends RuntimeException {
    private final ReservationFailure failure;

    public ReservationPhoneException(ReservationFailure failure) {
        super("Reservation phone operation could not complete");
        this.failure = failure;
    }

    public ReservationFailure failure() {
        return failure;
    }
}

package com.prog2.appointment.reservation.domain.exception;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;

/** Rechazo seguro del contrato de aceptación o reintento de una cancelación propia. */
public final class ReservationCancellationException extends RuntimeException {
    private final ReservationFailure failure;

    public ReservationCancellationException(ReservationFailure failure) {
        super("Reservation cancellation could not complete");
        this.failure = failure;
    }

    public ReservationFailure failure() {
        return failure;
    }
}

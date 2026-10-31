package com.prog2.appointment.reservation.domain.exception;

/** Conflicto de clave local sin exponer el proceso de otro propietario. */
public final class ReservationConflictException extends RuntimeException {
    public ReservationConflictException() {
        super("Reservation request identifier is unavailable");
    }
}

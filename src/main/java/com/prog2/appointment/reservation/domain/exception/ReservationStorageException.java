package com.prog2.appointment.reservation.domain.exception;

/** Almacenamiento no verificable; no propaga SQL ni concede permiso para un POST. */
public final class ReservationStorageException extends RuntimeException {
    public ReservationStorageException() {
        super("Reservation storage unavailable");
    }
}

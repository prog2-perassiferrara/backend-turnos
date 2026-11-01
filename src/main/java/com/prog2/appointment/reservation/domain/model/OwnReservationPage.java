package com.prog2.appointment.reservation.domain.model;

import java.util.List;

/** Lectura SQL consistente: pendiente y última respuesta, separados del historial contado. */
public record OwnReservationPage(
        ReservationProcess pending,
        PhoneSubmission phone,
        List<ReservationProcess> history,
        long totalElements,
        int page,
        int size) {
    public OwnReservationPage {
        validate(page, size);
        history = List.copyOf(history);
        if (totalElements < 0
                || phone != null && (pending == null || !phone.processId().equals(pending.id()))) {
            throw new IllegalArgumentException("Invalid reservation page");
        }
    }

    /**
     * @throws IllegalArgumentException si la página, tamaño o desplazamiento no son válidos
     */
    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid reservation page");
        }
    }
}

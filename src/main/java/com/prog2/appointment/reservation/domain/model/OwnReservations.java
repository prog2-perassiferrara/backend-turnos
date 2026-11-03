package com.prog2.appointment.reservation.domain.model;

import java.util.List;

/** Resultado local: pendiente separado e historial propio paginado. */
public record OwnReservations(
        ReservationStatus pending,
        List<ReservationStatus> history,
        long totalElements,
        int page,
        int size) {
    public OwnReservations {
        history = List.copyOf(history);
    }
}

package com.prog2.appointment.reservation.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidad de una acción de KMP, reutilizada durante sus reintentos. */
public record StartReservationRequest(UUID requestId, ReservationSelection selection) {
    public StartReservationRequest {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(selection);
    }
}

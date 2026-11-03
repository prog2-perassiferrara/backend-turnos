package com.prog2.appointment.reservation.domain.enums;

/** Intención pendiente o detenida para retry explícito; la fase CANCELLED acredita el resultado. */
public enum CancellationState {
    PENDING,
    RETRY_REQUIRED
}

package com.prog2.appointment.reservation.domain.enums;

/** Progreso durable: una intención persistida no acredita el resultado del POST central. */
public enum ReservationPhase {
    HOLD_PENDING,
    HELD,
    CONFIRMATION_PENDING,
    WAITING_FOR_REQUEST,
    WAITING_FOR_PHONE,
    PHONE_QUEUED,
    WAITING_FOR_RESULT,
    RECONCILIATION_REQUIRED,
    FAILED,
    EXPIRED,
    CONFIRMED,
    CANCELLED;

    /**
     * @return si existe un cierre local; no acredita por sí solo cierre
     *     central
     */
    public boolean isFinal() {
        return this == FAILED || this == EXPIRED || this == CONFIRMED || this == CANCELLED;
    }
}

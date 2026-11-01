package com.prog2.appointment.reservation.domain.enums;

/** Progreso local: una intención persistida no acredita el resultado del POST central. */
public enum ReservationPhase {
    CREATING_HOLD,
    HOLD_UNCERTAIN,
    HELD,
    CONFIRMING,
    CONFIRMATION_UNCERTAIN,
    WAITING_FOR_REQUEST,
    WAITING_FOR_PHONE,
    PHONE_QUEUED,
    WAITING_FOR_RESULT,
    FAILED,
    RECONCILIATION_REQUIRED,
    HOLD_ATTEMPT_ABANDONED,
    REJECTED,
    EXPIRED,
    CONFIRMED,
    CANCELLED;

    /**
     * @return si existe un cierre local, independiente de un abandono pendiente; no acredita cierre
     *     central
     */
    public boolean isFinal() {
        return this == HOLD_ATTEMPT_ABANDONED
                || this == FAILED
                || this == REJECTED
                || this == EXPIRED
                || this == CONFIRMED
                || this == CANCELLED;
    }
}

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
    RECONCILIATION_REQUIRED,
    REJECTED,
    EXPIRED,
    CONFIRMED,
    CANCELLED;

    /**
     * @return si existe un resultado final conocido, independiente de un abandono pendiente
     */
    public boolean isFinal() {
        return this == REJECTED || this == EXPIRED || this == CONFIRMED || this == CANCELLED;
    }
}

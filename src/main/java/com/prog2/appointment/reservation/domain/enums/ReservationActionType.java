package com.prog2.appointment.reservation.domain.enums;

/** Hechos reconocidos de v1; los demás se conservan sin inventar un resultado. */
public enum ReservationActionType {
    PHONE_REQUESTED,
    PHONE_REJECTED,
    EXPIRED,
    INVALID,
    CONFIRMED,
    CANCELLED,
    OTHER
}

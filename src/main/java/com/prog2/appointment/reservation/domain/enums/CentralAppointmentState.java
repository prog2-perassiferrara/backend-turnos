package com.prog2.appointment.reservation.domain.enums;

/** Estados publicados por la consulta REST central, distintos del progreso local. */
public enum CentralAppointmentState {
    PHONE_PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}

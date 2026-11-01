package com.prog2.appointment.reservation.domain.model;

import com.prog2.appointment.reservation.domain.enums.VerificationStatus;
import java.util.List;
import java.util.Objects;

/** Evidencia propia recibida y alcance del recorrido; parcialidad nunca demuestra ausencia. */
public record CentralAppointmentsBatch(
        List<CentralAppointment> appointments, VerificationStatus status) {
    public CentralAppointmentsBatch {
        appointments = List.copyOf(appointments);
        Objects.requireNonNull(status);
        if (status == VerificationStatus.LOCAL) {
            throw new IllegalArgumentException("Invalid central verification");
        }
    }
}

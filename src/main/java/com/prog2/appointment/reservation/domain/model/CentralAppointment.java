package com.prog2.appointment.reservation.domain.model;

import com.prog2.appointment.reservation.domain.enums.CentralAppointmentState;

import java.util.Objects;

/** Evidencia REST de una reserva; su ausencia no demuestra cierre ni liberación. */
public record CentralAppointment(
        String centralProcessId,
        String externalPatientId,
        ReservationSelection selection,
        long reservationId,
        CentralAppointmentState state) {
    public CentralAppointment {
        if (!HoldDetails.identifier(centralProcessId)
                || !HoldDetails.identifier(externalPatientId)
                || reservationId <= 0)
            throw new IllegalArgumentException("Invalid central appointment");
        Objects.requireNonNull(selection);
        Objects.requireNonNull(state);
    }

    /**
     * @return correspondencia exacta, independiente de los filtros del proveedor
     */
    public boolean belongsTo(ReservationProcess process) {
        return process.hold() != null
                && centralProcessId.equals(process.hold().centralProcessId())
                && externalPatientId.equals(process.patient().externalPatientId())
                && selection.equals(process.selection());
    }
}

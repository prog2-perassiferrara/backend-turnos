package com.prog2.appointment.reservation.domain.model;

import com.prog2.appointment.reservation.domain.enums.CentralAppointmentState;

import java.util.Objects;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;

/** Evidencia REST de una reserva; su ausencia no demuestra cierre ni liberación. */
public record CentralAppointment(
        String centralProcessId,
        String externalPatientId,
        ReservationSelection selection,
        long reservationId,
        CentralAppointmentState state,
        ProfessionalName professionalName) {
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
                && selection.equals(process.selection())
                && (process.reservationId() == null || process.reservationId() == reservationId);
    }

    /**
     * @return si el resultado observado es compatible con el progreso actual, sin reabrir cierres
     */
    public boolean agreesWith(ReservationProcess process) {
        if (!belongsTo(process)) return false;
        return switch (state) {
            case PHONE_PENDING -> !process.phase().isFinal();
            case CONFIRMED -> process.phase() == ReservationPhase.CONFIRMED;
            case CANCELLED -> process.phase() == ReservationPhase.CANCELLED;
            case FAILED -> process.phase() == ReservationPhase.FAILED;
        };
    }
}

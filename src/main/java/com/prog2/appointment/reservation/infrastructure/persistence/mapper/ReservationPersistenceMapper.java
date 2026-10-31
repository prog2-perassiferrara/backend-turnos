package com.prog2.appointment.reservation.infrastructure.persistence.mapper;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.ReservationPatient;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationProcessEntity;

/** Traduce estado propio dentro de la transacción; no entrega entidades al caso de uso. */
@Component
public final class ReservationPersistenceMapper {
    public ReservationProcess toDomain(ReservationProcessEntity entity) {
        var hold =
                entity.getHoldId() == null
                        ? null
                        : new HoldDetails(
                                entity.getHoldId(),
                                entity.getCentralProcessId(),
                                entity.getExpiresAt());
        return new ReservationProcess(
                UUID.fromString(entity.getId()),
                new ReservationPatient(
                        entity.getOwnerId(),
                        entity.getPatientFirstName(),
                        entity.getPatientLastName()),
                new ReservationSelection(
                        entity.getProfessionalId(),
                        entity.getAppointmentDate(),
                        entity.getStartTime()),
                ReservationPhase.valueOf(entity.getPhase()),
                hold,
                entity.getRequestEventId(),
                entity.getReservationId(),
                entity.getReasonCode(),
                entity.getVersion(),
                UUID.fromString(entity.getWriteId()),
                entity.getClosedAt(),
                entity.getAbandonmentPending());
    }
}

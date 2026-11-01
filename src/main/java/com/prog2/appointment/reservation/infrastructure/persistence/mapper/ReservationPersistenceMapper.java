package com.prog2.appointment.reservation.infrastructure.persistence.mapper;

import com.prog2.appointment.reservation.domain.model.ProfessionalName;

import java.util.UUID;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.PhoneSubmissionEntity;
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
    public PhoneSubmission toDomain(PhoneSubmissionEntity entity) {
        var event =
                new PhoneSubmission.Event(
                        entity.getGroupId(),
                        entity.getCentralProcessId(),
                        entity.getHoldId(),
                        entity.getRequestEventId(),
                        entity.getPhoneNumber(),
                        entity.getOccurredAt());
        return new PhoneSubmission(
                UUID.fromString(entity.getId()),
                UUID.fromString(entity.getProcessId()),
                event,
                entity.getExpiresAt(),
                entity.getPublishedAt(),
                entity.getClosedAt(),
                entity.getCycleAttempt(),
                entity.getNextAttemptAt(),
                entity.getLastFailure(),
                entity.getVersion());
    }

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
                entity.getAbandonmentPending(),
                ProfessionalName.from(
                        entity.getProfessionalFirstName(), entity.getProfessionalLastName()));
    }
}

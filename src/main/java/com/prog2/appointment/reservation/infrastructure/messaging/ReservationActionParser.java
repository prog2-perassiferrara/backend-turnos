package com.prog2.appointment.reservation.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import tools.jackson.databind.json.JsonMapper;

/** Frontera v1: convierte datos externos sin cargar clases indicadas por el mensaje. */
@RequiredArgsConstructor
public final class ReservationActionParser {
    private final JsonMapper json;
    private final String group;

    /**
     * @return hecho validado o vacío para mensajes inválidos; no conserva ni expone el payload
     */
    public Optional<ReservationAction> parse(String key, String payload) {
        String body = payload == null ? "" : payload;
        if (body.length() > 1_048_576) {
            return Optional.empty();
        }
        try {
            var dto = json.readValue(body, ActionDto.class);
            if (dto == null
                    || !Integer.valueOf(1).equals(dto.schemaVersion())
                    || dto.occurredAt() == null
                    || !HoldDetails.identifier(dto.eventType())
                    || !dto.reservationProcessId().equals(key)
                    || dto.groupId() != null && !group.equals(dto.groupId())) {
                throw new IllegalArgumentException();
            }
            var type = type(dto.eventType());
            var correlation =
                    new ReservationAction.Correlation(
                            dto.eventId(),
                            dto.reservationProcessId(),
                            dto.holdId(),
                            dto.requestEventId());
            var selection = selection(dto, type);
            if ((type == ReservationActionType.CONFIRMED
                            || type == ReservationActionType.EXPIRED
                            || type == ReservationActionType.INVALID
                            || type == ReservationActionType.PHONE_REJECTED)
                    && !HoldDetails.identifier(dto.requestEventId())) {
                throw new IllegalArgumentException();
            }
            var action =
                    new ReservationAction(
                            type, correlation, dto.expiresAt(), dto.reservationId(), selection);
            return Optional.of(action);
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    private ReservationSelection selection(ActionDto dto, ReservationActionType type) {
        if (type == ReservationActionType.PHONE_REJECTED
                && (!group.equals(dto.groupId())
                        || dto.expiresAt() == null
                        || !HoldDetails.identifier(dto.reason()))) {
            throw new IllegalArgumentException();
        }
        if (type == ReservationActionType.PHONE_REQUESTED
                && (dto.expiresAt() == null || !"PHONE_NUMBER".equals(dto.requiredField()))) {
            throw new IllegalArgumentException();
        }
        if (type != ReservationActionType.CONFIRMED && type != ReservationActionType.CANCELLED) {
            return null;
        }
        String status = type == ReservationActionType.CONFIRMED ? "CONFIRMED" : "CANCELLED";
        if (!group.equals(dto.groupId())
                || dto.reservationId() == null
                || dto.reservationId() <= 0
                || !status.equals(dto.status())) {
            throw new IllegalArgumentException();
        }
        return new ReservationSelection(
                dto.professionalId(), dto.date(), LocalTime.parse(dto.startTime()));
    }

    private static ReservationActionType type(String eventType) {
        return switch (eventType) {
            case "AdditionalInformationRequested" -> ReservationActionType.PHONE_REQUESTED;
            case "AdditionalInformationRejected" -> ReservationActionType.PHONE_REJECTED;
            case "AppointmentProcessExpired" -> ReservationActionType.EXPIRED;
            case "AppointmentProcessInvalid" -> ReservationActionType.INVALID;
            case "AppointmentConfirmed" -> ReservationActionType.CONFIRMED;
            case "AppointmentCancelled" -> ReservationActionType.CANCELLED;
            default -> ReservationActionType.OTHER;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ActionDto(
            String eventId,
            String eventType,
            Integer schemaVersion,
            String reservationProcessId,
            String holdId,
            String groupId,
            String requiredField,
            Instant expiresAt,
            Instant occurredAt,
            String requestEventId,
            Long reservationId,
            Long professionalId,
            LocalDate date,
            String startTime,
            String status,
            String reason) {}

}

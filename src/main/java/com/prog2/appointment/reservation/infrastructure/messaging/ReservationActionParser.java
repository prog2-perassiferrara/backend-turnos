package com.prog2.appointment.reservation.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import tools.jackson.databind.json.JsonMapper;

/** Frontera v1: convierte datos externos sin cargar clases indicadas por el mensaje. */
public final class ReservationActionParser {
    private final JsonMapper json;
    private final String group;

    public ReservationActionParser(JsonMapper json, String group) {
        this.json = json;
        this.group = group;
    }

    /**
     * @return hecho validado o rechazo durable; nunca lanza el cuerpo de entrada como error
     */
    public ReservationDelivery parse(ReservationDelivery.Origin origin, String payload) {
        String body = payload == null ? "" : payload;
        if (body.length() > 1_048_576) {
            return new ReservationDelivery(
                    origin, null, body.substring(0, 65536), hash(body), "PAYLOAD_TOO_LARGE");
        }
        try {
            var dto = json.readValue(body, ActionDto.class);
            if (dto == null
                    || !Integer.valueOf(1).equals(dto.schemaVersion())
                    || dto.occurredAt() == null
                    || !HoldDetails.identifier(dto.eventType())
                    || !dto.reservationProcessId().equals(origin.key())
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
                            || type == ReservationActionType.INVALID)
                    && !HoldDetails.identifier(dto.requestEventId())) {
                throw new IllegalArgumentException();
            }
            var action =
                    new ReservationAction(
                            type, correlation, dto.expiresAt(), dto.reservationId(), selection);
            // El orden de propiedades JSON y los campos adicionales no cambian la identidad del
            // hecho.
            var content = new EventContent(dto.eventType(), dto.occurredAt(), dto.reason(), action);
            return new ReservationDelivery(
                    origin, action, body, hash(json.writeValueAsString(content)), null);
        } catch (RuntimeException _) {
            return new ReservationDelivery(origin, null, body, hash(body), "INVALID_ACTION");
        }
    }

    private ReservationSelection selection(ActionDto dto, ReservationActionType type) {
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
            case "AppointmentProcessExpired" -> ReservationActionType.EXPIRED;
            case "AppointmentProcessInvalid" -> ReservationActionType.INVALID;
            case "AppointmentConfirmed" -> ReservationActionType.CONFIRMED;
            case "AppointmentCancelled" -> ReservationActionType.CANCELLED;
            default -> ReservationActionType.OTHER;
        };
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
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

    private record EventContent(
            String eventType, Instant occurredAt, String reason, ReservationAction action) {}
}

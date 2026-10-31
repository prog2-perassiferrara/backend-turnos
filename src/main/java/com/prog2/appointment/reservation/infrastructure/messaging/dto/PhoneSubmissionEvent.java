package com.prog2.appointment.reservation.infrastructure.messaging.dto;

import com.prog2.appointment.reservation.domain.model.PhoneSubmission;

import java.time.Instant;
import java.util.Map;

/** Contrato v1 explícito; se serializa como texto JSON, sin cabeceras Java. */
public record PhoneSubmissionEvent(
        String eventId,
        String eventType,
        String groupId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        Map<String, String> information,
        Instant occurredAt,
        int schemaVersion) {
    public static PhoneSubmissionEvent from(PhoneSubmission phone) {
        var event = phone.event();
        return new PhoneSubmissionEvent(
                phone.id().toString(),
                "AdditionalInformationSubmitted",
                event.groupId(),
                event.centralProcessId(),
                event.holdId(),
                event.requestEventId(),
                Map.of("phoneNumber", event.phoneNumber()),
                event.occurredAt(),
                1);
    }

    @Override
    public String toString() {
        return "PhoneSubmissionEvent[eventId=" + eventId + "]";
    }
}

package com.prog2.appointment.reservation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Respuesta durable: identidad y contenido inmutables, avance de publicación independiente del
 * resultado funcional. El cierre de esta respuesta no acredita cancelar el hold.
 */
public record PhoneSubmission(
        UUID id,
        UUID processId,
        Event event,
        Instant expiresAt,
        Instant publishedAt,
        Instant closedAt,
        int cycleAttempt,
        Instant nextAttemptAt,
        String lastFailure,
        long version) {
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{7,15}");

    public PhoneSubmission {
        Objects.requireNonNull(id);
        Objects.requireNonNull(processId);
        Objects.requireNonNull(event);
        Objects.requireNonNull(expiresAt);
        if (cycleAttempt < 0
                || version < 0
                || publishedAt == null && closedAt == null && nextAttemptAt == null) {
            throw new IllegalArgumentException("Invalid phone submission progress");
        }
    }

    /** Contenido v1 conservado; ninguna republicación genera otra fecha o correlación. */
    public record Event(
            String groupId,
            String centralProcessId,
            String holdId,
            String requestEventId,
            String phoneNumber,
            Instant occurredAt) {
        public Event {
            if (!HoldDetails.identifier(groupId)
                    || !HoldDetails.identifier(centralProcessId)
                    || !HoldDetails.identifier(holdId)
                    || !HoldDetails.identifier(requestEventId)) {
                throw new IllegalArgumentException("Invalid phone correlation");
            }
            phoneNumber = normalize(phoneNumber);
            Objects.requireNonNull(occurredAt);
        }

        @Override
        public String toString() {
            return "PhoneSubmission.Event[REDACTED]";
        }
    }

    /**
     * @param value teléfono con los separadores permitidos por cátedra
     * @return teléfono normalizado, conservando signo y ceros iniciales
     * @throws IllegalArgumentException si no cumple el contrato central
     */
    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid phone number");
        String normalized =
                value.replace(" ", "")
                        .replace("-", "")
                        .replace("(", "")
                        .replace(")", "")
                        .replace(".", "");
        if (!PHONE.matcher(normalized).matches())
            throw new IllegalArgumentException("Invalid phone number");
        return normalized;
    }

    /** Crea una respuesta únicamente para la solicitud vigente ya recibida. */
    public static PhoneSubmission create(
            UUID id, ReservationProcess process, String groupId, String phoneNumber, Instant now) {
        if (!process.canSubmitPhone(now))
            throw new IllegalArgumentException("Phone is not requested");
        var event =
                new Event(
                        groupId,
                        process.hold().centralProcessId(),
                        process.hold().holdId(),
                        process.requestEventId(),
                        phoneNumber,
                        now);
        return new PhoneSubmission(
                id, process.id(), event, process.hold().expiresAt(), null, null, 0, now, null, 0);
    }

    /**
     * @return si la clave histórica pertenece al proceso y al mismo contenido normalizado
     */
    public boolean matches(UUID targetProcess, SubmitPhoneRequest request) {
        return id.equals(request.requestId())
                && processId.equals(targetProcess)
                && event.phoneNumber().equals(request.phoneNumber());
    }

    /**
     * @return si corresponde intentar; el plazo se comprueba al registrar la intención
     */
    public boolean isDue(Instant now) {
        return closedAt == null && publishedAt == null && !now.isBefore(nextAttemptAt);
    }

    /**
     * Registra el intento y su próxima fecha antes de Kafka. Al agotar un ciclo, el siguiente
     * comienza después del enfriamiento; el vencimiento nunca se amplía.
     */
    public PhoneSubmission beginAttempt(
            Instant now, int attempts, Duration retry, Duration cooldown) {
        if (!isDue(now)) return this;
        if (!now.isBefore(expiresAt)) return finish(now, "PHONE_WINDOW_CLOSED");
        int count = cycleAttempt + 1;
        boolean exhausted = count >= attempts;
        return new PhoneSubmission(
                id,
                processId,
                event,
                expiresAt,
                null,
                null,
                exhausted ? 0 : count,
                now.plus(exhausted ? cooldown : retry),
                "PHONE_PUBLICATION_UNCERTAIN",
                version);
    }

    /** Un ACK tardío nunca reabre una respuesta resuelta. */
    public PhoneSubmission received(Instant now) {
        if (closedAt != null || publishedAt != null) return this;
        return new PhoneSubmission(
                id, processId, event, expiresAt, now, null, cycleAttempt, null, null, version);
    }

    /** Detiene esta publicación; el proceso conserva su evidencia central independiente. */
    public PhoneSubmission finish(Instant now, String reason) {
        if (closedAt != null) return this;
        return new PhoneSubmission(
                id,
                processId,
                event,
                expiresAt,
                publishedAt,
                now,
                cycleAttempt,
                null,
                reason,
                version);
    }

    @Override
    public String toString() {
        return "PhoneSubmission[id=" + id + ", processId=" + processId + "]";
    }
}

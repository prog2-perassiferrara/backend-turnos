package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Identificadores opacos y plazo central en microsegundos. Truncar antes de persistir evita
 * redondearlo hacia el futuro; REST y Kafka se comparan con la misma precisión conservada.
 */
public record HoldDetails(String holdId, String centralProcessId, Instant expiresAt) {
    public HoldDetails {
        if (!identifier(holdId) || !identifier(centralProcessId)) {
            throw new IllegalArgumentException("Invalid hold correlation");
        }
        expiresAt = Objects.requireNonNull(expiresAt).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * @param value vencimiento central, que puede contener nanosegundos
     * @return si representa el mismo microsegundo conservado; null o un plazo diferente no coinciden
     */
    public boolean expiresAtMatches(Instant value) {
        return value != null && expiresAt.equals(value.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * @return si el identificador puede conservarse sin truncamiento
     */
    public static boolean identifier(String value) {
        return value != null && !value.isBlank() && value.length() <= 255;
    }
}

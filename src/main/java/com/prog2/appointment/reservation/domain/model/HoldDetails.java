package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Identificadores opacos y plazo central; conservarlos no extiende su vigencia. */
public record HoldDetails(String holdId, String centralProcessId, Instant expiresAt) {
    public HoldDetails {
        if (!identifier(holdId) || !identifier(centralProcessId)) {
            throw new IllegalArgumentException("Invalid hold correlation");
        }
        Objects.requireNonNull(expiresAt);
    }

    /**
     * @return si el identificador puede conservarse sin truncamiento
     */
    public static boolean identifier(String value) {
        return value != null && !value.isBlank() && value.length() <= 255;
    }
}

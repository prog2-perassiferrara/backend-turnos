package com.prog2.appointment.integration.domain.model;

import java.time.Instant;

/** JWT central recibido por contrato protegido; no verifica su firma ni inventa un vencimiento. */
public record TechnicalToken(String value, Instant expiresAt) {
    public TechnicalToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Technical token is required");
        }
    }

    /**
     * Evalúa únicamente la vigencia conocida; un 401 central siempre prevalece.
     * @param now instante UTC actual
     * @return false desde el instante exacto de expiración; true si no existe exp conocido
     */
    public boolean isUsableAt(Instant now) {
        return expiresAt == null || now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "TechnicalToken[REDACTED]";
    }
}

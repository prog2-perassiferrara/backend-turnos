package com.prog2.appointment.integration.domain.model;

import java.util.Objects;

/** Unidad inmutable de JWT/configuración: nunca se publica parcialmente. */
public record TechnicalAccess(TechnicalToken token, IntegrationConfiguration configuration) {
    public TechnicalAccess {
        Objects.requireNonNull(token);
        Objects.requireNonNull(configuration);
    }

    @Override
    public String toString() {
        return "TechnicalAccess[REDACTED]";
    }
}

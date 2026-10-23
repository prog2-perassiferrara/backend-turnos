package com.prog2.appointment.user.domain.model;

import java.time.Instant;

/** Credencial de usuario final y vencimiento; su representación de texto oculta el bearer. */
public record UserToken(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "UserToken[REDACTED]";
    }
}

package com.prog2.appointment.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Único dato del éxito HTTP de autenticación; mantiene el nombre id_token de JHipster. */
public record AuthenticationResponse(@JsonProperty("id_token") String idToken) {
    @Override
    public String toString() {
        return "AuthenticationResponse[REDACTED]";
    }
}

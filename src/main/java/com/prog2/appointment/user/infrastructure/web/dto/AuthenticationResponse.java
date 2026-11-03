package com.prog2.appointment.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Único dato del éxito HTTP de autenticación; mantiene el nombre id_token de JHipster. */
public record AuthenticationResponse(
        @JsonProperty("id_token") @Schema(description = "JWT de usuario emitido por Turnos.") String idToken) {
    @Override
    public String toString() {
        return "AuthenticationResponse[REDACTED]";
    }
}

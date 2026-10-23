package com.prog2.appointment.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.infrastructure.web.validation.MaxUtf8Bytes;

/** Credenciales de usuario/email; rememberMe omitido equivale a false y no guarda contraseñas. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthenticationRequest(
        @NotBlank @Size(max = 254) String username,
        @NotNull @Size(min = 6) @MaxUtf8Bytes String password,
        Boolean rememberMe) {
    public AuthenticationRequest {
        username = RegistrationData.normalizeIdentifier(username);
        rememberMe = Boolean.TRUE.equals(rememberMe);
    }

    @Override
    public String toString() {
        return "AuthenticationRequest[REDACTED]";
    }
}

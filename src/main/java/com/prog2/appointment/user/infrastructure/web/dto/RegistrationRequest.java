package com.prog2.appointment.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.infrastructure.web.validation.MaxUtf8Bytes;

/**
 * Entrada compatible de registro; ignora campos internos y valida identificadores ya normalizados.
 * El patrón procede de Constants.LOGIN_REGEX del ejemplo oficial de JHipster.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistrationRequest(
        @NotBlank @Size(min = 1, max = 50)
        @Pattern(regexp = "^(?>[a-zA-Z0-9!$&*+=?^_\\x60{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$")
        String login,
        @NotNull @Size(min = 6) @MaxUtf8Bytes String password,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email @Size(min = 5, max = 254) String email,
        @Size(max = 256) String imageUrl,
        @NotBlank @Size(min = 2, max = 10) String langKey) {

    public RegistrationRequest {
        login = RegistrationData.normalizeIdentifier(login);
        email = RegistrationData.normalizeIdentifier(email);
        langKey = langKey == null ? "es" : langKey;
    }

    @Override
    public String toString() {
        return "RegistrationRequest[REDACTED]";
    }
}

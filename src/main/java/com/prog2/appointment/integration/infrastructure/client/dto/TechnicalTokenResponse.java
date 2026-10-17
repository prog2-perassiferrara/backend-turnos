package com.prog2.appointment.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** DTO exclusivo del proveedor protegido, sin conservar campos desconocidos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TechnicalTokenResponse(String idToken) {
    @Override
    public String toString() {
        return "TechnicalTokenResponse[REDACTED]";
    }
}

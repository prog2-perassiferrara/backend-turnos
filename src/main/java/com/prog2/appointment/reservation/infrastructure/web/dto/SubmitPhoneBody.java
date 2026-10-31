package com.prog2.appointment.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Solo datos del intento propio; las correlaciones centrales se obtienen del proceso persistido.
 */
public record SubmitPhoneBody(@NotNull UUID requestId, @NotNull String phoneNumber) {
    @Override
    public String toString() {
        return "SubmitPhoneBody[requestId=" + requestId + "]";
    }
}

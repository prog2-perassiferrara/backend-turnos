package com.prog2.appointment.reservation.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Acción de teléfono del propietario; el UUID se conserva al reintentar. */
public record SubmitPhoneRequest(UUID requestId, String phoneNumber) {
    public SubmitPhoneRequest {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(phoneNumber);
    }

    @Override
    public String toString() {
        return "SubmitPhoneRequest[id=" + requestId + "]";
    }
}

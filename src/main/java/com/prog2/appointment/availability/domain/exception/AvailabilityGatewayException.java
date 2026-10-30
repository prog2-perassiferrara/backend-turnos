package com.prog2.appointment.availability.domain.exception;

import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;

/** Fallo definido por los puertos salientes, sin conservar mensajes o causas del proveedor. */
public final class AvailabilityGatewayException extends RuntimeException {
    private final AvailabilityFailure failure;

    public AvailabilityGatewayException(AvailabilityFailure failure) {
        super("Availability provider failed");
        this.failure = failure;
    }

    /** @return clasificación funcional segura, independiente del transporte */
    public AvailabilityFailure failure() {
        return failure;
    }
}

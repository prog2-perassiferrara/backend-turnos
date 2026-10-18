package com.prog2.appointment.integration.domain.exception;

import com.prog2.appointment.integration.domain.enums.AccessFailure;

/** Fallo del contrato saliente sin excepciones externas ni información sensible como causa. */
public final class IntegrationGatewayException extends RuntimeException {
    private final AccessFailure failure;

    /** @param failure motivo fijo y seguro del fallo remoto */
    public IntegrationGatewayException(AccessFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    /** @return clasificación independiente del cliente HTTP */
    public AccessFailure failure() {
        return failure;
    }
}

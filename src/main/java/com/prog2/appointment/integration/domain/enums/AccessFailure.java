package com.prog2.appointment.integration.domain.enums;

/** Motivos seguros para diagnóstico: nunca contienen texto ni cuerpos del proveedor. */
public enum AccessFailure {
    NONE(false), TEMPORARY_UNAVAILABLE(true), PROVISIONING_PENDING(true),
    CATALOG_AUTHENTICATION(false), ACCESS_DENIED(false), INTEGRATION_NOT_FOUND(false),
    CENTRAL_TOKEN_REJECTED(false), INVALID_RESPONSE(false), INTEGRATION_NOT_READY(false);

    private final boolean retryable;

    AccessFailure(boolean retryable) {
        this.retryable = retryable;
    }

    /** @return si el mismo ciclo puede efectuar otro intento limitado */
    public boolean isRetryable() {
        return retryable;
    }
}

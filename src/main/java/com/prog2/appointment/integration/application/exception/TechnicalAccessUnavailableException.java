package com.prog2.appointment.integration.application.exception;

/** Indisponibilidad local segura; no divulga causas internas ni secretos del proveedor. */
public final class TechnicalAccessUnavailableException extends IllegalStateException {
    public TechnicalAccessUnavailableException() {
        super("Technical integration is unavailable");
    }
}

package com.prog2.appointment.integration.domain.enums;

/** Resultado de un intento; solo RETRYABLE permite continuar el mismo ciclo. */
public enum AcquisitionOutcome {
    AVAILABLE, RETRYABLE, TERMINAL
}

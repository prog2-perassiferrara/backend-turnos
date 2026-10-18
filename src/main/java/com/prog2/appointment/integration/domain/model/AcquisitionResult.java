package com.prog2.appointment.integration.domain.model;

import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.enums.AcquisitionOutcome;

/** Resultado seguro; el candidato solo conserva progreso de un ciclo, no acredita acceso disponible. */
public record AcquisitionResult(AcquisitionOutcome outcome, TechnicalToken candidate, AccessFailure failure) {
    /** @return resultado de sesión completa ya publicada */
    public static AcquisitionResult available() {
        return new AcquisitionResult(AcquisitionOutcome.AVAILABLE, null, AccessFailure.NONE);
    }

    /**
     * @param token candidato opcional que puede reutilizarse dentro del ciclo
     * @param failure motivo que determina si continuar
     * @return resultado recuperable o terminal según el motivo
     */
    public static AcquisitionResult failed(TechnicalToken token, AccessFailure failure) {
        return new AcquisitionResult(failure.isRetryable() ? AcquisitionOutcome.RETRYABLE
                : AcquisitionOutcome.TERMINAL, token, failure);
    }
}

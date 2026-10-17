package com.prog2.appointment.integration.domain.ports.in;

import com.prog2.appointment.integration.domain.model.AcquisitionResult;
import com.prog2.appointment.integration.domain.model.TechnicalToken;

/** Contrato de un intento de adquisición; no administra esperas ni reintentos. */
public interface AcquireTechnicalAccessUseCase {
    /**
     * @param candidate token pendiente del mismo ciclo, o null para solicitarlo al proveedor
     * @return resultado seguro; AVAILABLE implica sesión completa publicada
     */
    AcquisitionResult attemptAcquisition(TechnicalToken candidate);
}

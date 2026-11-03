package com.prog2.appointment.integration.domain.ports.out;

import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;
import com.prog2.appointment.integration.domain.model.TechnicalToken;

/** Puerto para recuperar el objeto de integración central sin suponer otro envoltorio JSON. */
public interface CentralIntegrationGateway {
    /**
     * @param token JWT central recibido del proveedor protegido
     * @return parámetros aprovisionados de Turnos
     * @throws IntegrationGatewayException ante rechazo, configuración no aprovisionada, respuesta inválida o fallo de comunicación
     */
    IntegrationConfiguration fetchConfiguration(TechnicalToken token);
}

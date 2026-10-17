package com.prog2.appointment.integration.domain.ports.out;

import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.TechnicalToken;

/** Puerto de entrega protegida: el adaptador autentica al servicio Turnos, no al usuario. */
public interface CatalogTechnicalTokenGateway {
    /**
     * @return JWT central existente en el proveedor, sin efectuar login de cátedra
     * @throws IntegrationGatewayException si falla el contrato o la comunicación
     */
    TechnicalToken fetchToken();
}

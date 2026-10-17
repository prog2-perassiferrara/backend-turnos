package com.prog2.appointment.integration.infrastructure.client.adapter;

import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.appointment.integration.infrastructure.client.dto.IntegrationResponse;

/** Adapter del GET de integración actual, sin login central ni otro envoltorio. */
public final class WebClientCentralIntegrationAdapter implements CentralIntegrationGateway {
    private final SafeIntegrationHttpClient client;

    public WebClientCentralIntegrationAdapter(SafeIntegrationHttpClient client) {
        this.client = client;
    }

    @Override
    public IntegrationConfiguration fetchConfiguration(TechnicalToken token) {
        var response = client.get("/api/student/integration", token.value(), IntegrationResponse.class);
        try {
            return response.toDomain();
        } catch (IllegalArgumentException | NullPointerException _) {
            throw new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE);
        }
    }
}

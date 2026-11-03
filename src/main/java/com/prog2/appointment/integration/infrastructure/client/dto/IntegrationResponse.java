package com.prog2.appointment.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;

/** Campos centrales pertinentes a Turnos; ignora Redis y extensiones compatibles del contrato. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntegrationResponse(String groupId, String provisioningStatus, String kafkaBootstrapServers,
        String kafkaConsumerGroupId, String kafkaAppointmentActionsTopic, String kafkaAppointmentPhoneTopic) {
    /** @return datos propios sin dependencia de JSON; estado desconocido/inexistente no se habilita */
    public IntegrationConfiguration toDomain() {
        if ("PENDING".equals(provisioningStatus)) {
            throw new IntegrationGatewayException(AccessFailure.PROVISIONING_PENDING);
        }
        if ("FAILED".equals(provisioningStatus) || "REVOKED".equals(provisioningStatus)) {
            throw new IntegrationGatewayException(AccessFailure.INTEGRATION_NOT_READY);
        }
        if (!"PROVISIONED".equals(provisioningStatus)) throw new IllegalArgumentException("Invalid provisioning status");
        return new IntegrationConfiguration(groupId,
                kafkaBootstrapServers, kafkaConsumerGroupId, kafkaAppointmentActionsTopic, kafkaAppointmentPhoneTopic);
    }
}

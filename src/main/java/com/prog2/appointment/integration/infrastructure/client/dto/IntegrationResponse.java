package com.prog2.appointment.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;

/** Campos centrales pertinentes a Turnos; ignora Redis y extensiones compatibles del contrato. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntegrationResponse(String groupId, String provisioningStatus, String kafkaBootstrapServers,
        String kafkaConsumerGroupId, String kafkaAppointmentActionsTopic, String kafkaAppointmentPhoneTopic) {
    /** @return datos propios sin dependencia de JSON; estado desconocido/inexistente no se habilita */
    public IntegrationConfiguration toDomain() {
        return new IntegrationConfiguration(groupId, ProvisioningStatus.valueOf(provisioningStatus),
                kafkaBootstrapServers, kafkaConsumerGroupId, kafkaAppointmentActionsTopic, kafkaAppointmentPhoneTopic);
    }
}

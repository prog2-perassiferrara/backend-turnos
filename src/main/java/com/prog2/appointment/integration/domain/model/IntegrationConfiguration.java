package com.prog2.appointment.integration.domain.model;

import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;

/** Configuración asignada a Turnos; no duplica catálogo ni exige credenciales Redis. */
public record IntegrationConfiguration(String groupId, ProvisioningStatus provisioningStatus,
        String kafkaBootstrapServers, String kafkaConsumerGroupId,
        String kafkaAppointmentActionsTopic, String kafkaAppointmentPhoneTopic) {

    /** @return si existen todos los parámetros necesarios para preparar REST/Kafka de Turnos */
    public boolean isComplete() {
        return present(groupId) && present(kafkaBootstrapServers) && present(kafkaConsumerGroupId)
                && present(kafkaAppointmentActionsTopic) && present(kafkaAppointmentPhoneTopic);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}

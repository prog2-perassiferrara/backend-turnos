package com.prog2.appointment.availability.infrastructure.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Contrato central de posiciones ocupadas; no incorpora datos de pacientes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentOccupanciesResponse(Long professionalId, LocalDate from, LocalDate to,
        List<OccupancyResponse> occupancies) {
    /** Posición por fecha/inicio, con vencimiento UTC exclusivamente para holds. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OccupancyResponse(LocalDate date, String startTime, String status, Instant expiresAt) { }
}

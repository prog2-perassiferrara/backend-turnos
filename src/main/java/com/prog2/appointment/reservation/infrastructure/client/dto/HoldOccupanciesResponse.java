package com.prog2.appointment.reservation.infrastructure.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Respuesta de ocupaciones de un día, independiente del propietario de cada posición. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HoldOccupanciesResponse(
        Long professionalId, LocalDate from, LocalDate to, List<Occupancy> occupancies) {
    /** Evidencia central sin identificadores ni paciente; HELD conserva su vencimiento exacto. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Occupancy(
            LocalDate date, LocalTime startTime, String status, Instant expiresAt) {}
}

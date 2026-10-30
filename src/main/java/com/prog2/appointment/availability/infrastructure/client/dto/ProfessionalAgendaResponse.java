package com.prog2.appointment.availability.infrastructure.client.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Campos del contrato de Catálogo que necesita CU-03; wrappers distinguen ausencia de false o cero. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfessionalAgendaResponse(ProfessionalResponse professional,
        List<WeeklyScheduleResponse> weeklySchedules, CatalogResponse catalog) {
    /** Identidad y habilitación; los datos descriptivos adicionales no se replican. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfessionalResponse(Long id, Boolean enabled) { }

    /** Horas externas sin conversión de zona; las valida el adaptador antes de crear el modelo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeeklyScheduleResponse(Long professionalId, String dayOfWeek, String startTime,
            String endTime, Integer slotDurationMinutes, Boolean enabled) { }

    /** Garantía de vigencia de la misma lectura; no contiene secretos o diagnósticos técnicos. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogResponse(String state, Boolean copyAvailable, Long appliedVersion,
            Long observedVersion, Instant lastCheckedAt, Instant evaluatedAt) { }
}

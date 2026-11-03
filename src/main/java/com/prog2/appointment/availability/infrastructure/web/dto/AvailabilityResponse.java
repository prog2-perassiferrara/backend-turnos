package com.prog2.appointment.availability.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** Contrato de consulta para KMP: horarios locales sin credenciales ni datos de ocupantes. */
public record AvailabilityResponse(long professionalId, LocalDate date, String timeZone,
        @Schema(description = "Horas locales HH:mm:ss sin ocupaciones centrales; una lista vacía es válida.")
        List<String> availableStartTimes) { }

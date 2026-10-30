package com.prog2.appointment.availability.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Contrato de consulta para KMP: horarios locales sin credenciales ni datos de ocupantes. */
public record AvailabilityResponse(long professionalId, LocalDate date, String timeZone,
        List<String> availableStartTimes) { }

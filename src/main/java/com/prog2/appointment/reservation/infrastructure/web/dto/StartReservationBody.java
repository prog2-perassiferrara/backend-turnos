package com.prog2.appointment.reservation.infrastructure.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Solo selección/acción; nombre, paciente y propietario proceden de la cuenta autenticada. */
public record StartReservationBody(
        @NotNull UUID requestId,
        @Positive long professionalId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime) {}

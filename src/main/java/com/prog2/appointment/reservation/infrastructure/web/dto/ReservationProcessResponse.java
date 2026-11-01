package com.prog2.appointment.reservation.infrastructure.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Progreso público propio; los identificadores técnicos/credenciales centrales no se entregan a
 * KMP.
 */
public record ReservationProcessResponse(
        UUID processId,
        long professionalId,
        LocalDate date,
        String startTime,
        String state,
        Instant expiresAt,
        String reasonCode,
        UUID phoneRequestId,
        String requiredField,
        boolean canSubmitPhone,
        String verificationStatus,
        Instant verifiedAt,
        String warningCode,
        String professionalFirstName,
        String professionalLastName) {}

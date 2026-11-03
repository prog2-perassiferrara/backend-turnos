package com.prog2.appointment.reservation.infrastructure.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Progreso público propio; los identificadores técnicos/credenciales centrales no se entregan a
 * KMP.
 */
public record ReservationProcessResponse(
        UUID processId,
        long professionalId,
        LocalDate date,
        @Schema(description = "Hora local de atención en formato HH:mm:ss.") String startTime,
        @Schema(description = "Fase durable del proceso; no acredita por sí sola el resultado central.") String state,
        @Schema(description = "Vencimiento UTC del hold, si existe.") Instant expiresAt,
        String reasonCode,
        UUID phoneRequestId,
        String requiredField,
        boolean canSubmitPhone,
        String warningCode,
        String professionalFirstName,
        String professionalLastName,
        @Schema(description = "Intención de cancelación; la fase CANCELLED acredita el resultado.") CancellationView cancellation) {
    /** Intención propia mínima; la fase CANCELLED acredita la cancelación. */
    public record CancellationView(
            @Schema(description = "PENDING o RETRY_REQUIRED.") String status,
            boolean canRetry) {}
}

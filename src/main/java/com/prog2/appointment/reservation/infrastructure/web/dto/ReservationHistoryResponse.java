package com.prog2.appointment.reservation.infrastructure.web.dto;

import java.util.List;

/** Página del historial propio; sus totales excluyen el proceso pendiente separado. */
public record ReservationHistoryResponse(
        List<ReservationProcessResponse> content,
        int page,
        int size,
        long totalElements,
        long totalPages) {}

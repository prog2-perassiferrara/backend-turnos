package com.prog2.appointment.reservation.infrastructure.web.dto;

import java.time.Instant;

/** Vista HTTP propia con pendiente, historial paginado y alcance de la comprobación actual. */
public record OwnReservationsResponse(
        ReservationProcessResponse pending,
        ReservationHistoryResponse history,
        String verificationStatus,
        Instant verifiedAt,
        String warningCode) {}

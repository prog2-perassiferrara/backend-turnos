package com.prog2.appointment.reservation.domain.model;

import com.prog2.appointment.reservation.domain.enums.VerificationStatus;
import java.time.Instant;
import java.util.List;

/** Resultado propio del listado; la verificación resume solo los registros mostrados. */
public record OwnReservations(
        ReservationStatus pending,
        List<ReservationStatus> history,
        long totalElements,
        int page,
        int size,
        VerificationStatus verificationStatus,
        Instant verifiedAt,
        String warningCode) {
    public OwnReservations {
        history = List.copyOf(history);
    }
}

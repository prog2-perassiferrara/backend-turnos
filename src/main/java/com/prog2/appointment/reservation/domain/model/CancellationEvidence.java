package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Resultado REST correlacionado con la reserva central que se canceló. */
public record CancellationEvidence(String centralProcessId, Long reservationId, Instant cancelledAt) {
    public CancellationEvidence {
        if (!HoldDetails.identifier(centralProcessId) || reservationId == null || reservationId <= 0) {
            throw new IllegalArgumentException("Invalid cancellation evidence");
        }
        Objects.requireNonNull(cancelledAt);
    }

    public boolean belongsTo(ReservationProcess process) {
        return process.hold() != null
                && centralProcessId.equals(process.hold().centralProcessId())
                && (process.reservationId() == null || process.reservationId().equals(reservationId));
    }
}

package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;

/** Hecho externo validado; no contiene JWT ni tipos de Kafka/Jackson. */
public record ReservationAction(
        ReservationActionType type,
        Correlation correlation,
        Instant expiresAt,
        Long reservationId,
        ReservationSelection selection) {
    public ReservationAction {
        Objects.requireNonNull(type);
        Objects.requireNonNull(correlation);
    }

    /** Identidad funcional del evento y su correlación central. */
    public record Correlation(
            String eventId, String centralProcessId, String holdId, String requestEventId) {
        public Correlation {
            if (!HoldDetails.identifier(eventId)
                    || !HoldDetails.identifier(centralProcessId)
                    || !HoldDetails.identifier(holdId)) {
                throw new IllegalArgumentException("Invalid action correlation");
            }
        }
    }
}

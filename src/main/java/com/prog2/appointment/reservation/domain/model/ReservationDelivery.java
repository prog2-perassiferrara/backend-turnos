package com.prog2.appointment.reservation.domain.model;

import java.util.Objects;

/**
 * Entrega original conservable: contenido opaco y origen, sin dependencia de la biblioteca Kafka.
 */
public record ReservationDelivery(
        Origin origin,
        ReservationAction action,
        String payload,
        String fingerprint,
        String rejection) {
    public ReservationDelivery {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(fingerprint);
    }

    /** Coordenadas que permiten identificar incluso una entrega sin eventId válido. */
    public record Origin(String topic, int partition, long offset, String key) {
        public Origin {
            if (topic == null
                    || topic.isBlank()
                    || topic.length() > 249
                    || partition < 0
                    || offset < 0) {
                throw new IllegalArgumentException("Invalid delivery origin");
            }
        }
    }

    @Override
    public String toString() {
        return "ReservationDelivery[REDACTED]";
    }
}

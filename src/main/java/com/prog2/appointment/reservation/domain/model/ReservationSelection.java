package com.prog2.appointment.reservation.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Selección normalizada: HH:mm y HH:mm:ss equivalentes identifican el mismo inicio. */
public record ReservationSelection(long professionalId, LocalDate date, LocalTime startTime) {
    public ReservationSelection {
        if (professionalId <= 0) {
            throw new IllegalArgumentException("Invalid professional identifier");
        }
        Objects.requireNonNull(date);
        Objects.requireNonNull(startTime);
        if (startTime.getNano() != 0) {
            throw new IllegalArgumentException("Invalid start time precision");
        }
    }
}

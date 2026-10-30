package com.prog2.appointment.availability.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import com.prog2.appointment.availability.domain.enums.OccupancyStatus;

/** Posición ocupada por fecha/inicio; el contrato central no informa su duración histórica. */
public record AppointmentOccupancy(LocalDate date, LocalTime startTime, OccupancyStatus status,
        Instant expiresAt) {
    public AppointmentOccupancy {
        Objects.requireNonNull(date);
        Objects.requireNonNull(startTime);
        Objects.requireNonNull(status);
        if (status == OccupancyStatus.HELD && expiresAt == null) {
            throw new IllegalArgumentException("A held position requires its expiration");
        }
    }

    /**
     * @param now instante UTC usado también para filtrar inicios pasados
     * @return true para confirmaciones o holds anteriores a su vencimiento exacto
     */
    public boolean blocksAt(Instant now) {
        return status == OccupancyStatus.CONFIRMED || now.isBefore(expiresAt);
    }
}

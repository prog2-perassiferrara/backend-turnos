package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;

/** Posición ocupada observada en cátedra; no acredita propiedad ni recupera IDs de un hold. */
public record HoldOccupancy(boolean confirmed, Instant expiresAt) {
    public HoldOccupancy {
        if (confirmed != (expiresAt == null)) {
            throw new IllegalArgumentException("Invalid observed occupancy");
        }
    }
}

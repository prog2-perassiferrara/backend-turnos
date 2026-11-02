package com.prog2.appointment.reservation.infrastructure.concurrency;

import com.prog2.appointment.reservation.domain.ports.out.ReservationExecutionGuard;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adapter para la instancia actual; no conserva históricos ni sustituye las reclamaciones SQL. */
public final class InMemoryReservationExecutionGuard implements ReservationExecutionGuard {
    private final ConcurrentHashMap<UUID, Object> active = new ConcurrentHashMap<>();

    @Override
    public Optional<Scope> tryAcquire(UUID processId) {
        var token = new Object();
        if (active.putIfAbsent(processId, token) != null) return Optional.empty();
        return Optional.of(() -> active.remove(processId, token));
    }
}

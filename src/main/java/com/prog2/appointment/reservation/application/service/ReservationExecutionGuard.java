package com.prog2.appointment.reservation.application.service;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Evita recuperar una ejecución que sigue activa en la misma instancia; SQL conserva el permiso. */
@Component
public final class ReservationExecutionGuard {
    private final ConcurrentHashMap<UUID, Object> active = new ConcurrentHashMap<>();
    /**
     * @param processId identidad local que se va a releer antes de actuar
     * @return scope propio o vacío si ya existe trabajo activo; no permite reentrancia
     */
    public Optional<Scope> tryAcquire(UUID processId) {
        var token = new Object();
        if (active.putIfAbsent(processId, token) != null) return Optional.empty();
        return Optional.of(() -> active.remove(processId, token));
    }

    /** Recurso temporal: cerrar libera únicamente la adquisición propia, incluso tras un error. */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

package com.prog2.appointment.reservation.domain.ports.out;

import java.util.Optional;
import java.util.UUID;

/** Evita recuperar una ejecución que sigue activa en la misma instancia; SQL conserva el permiso. */
public interface ReservationExecutionGuard {
    /**
     * @param processId identidad local que se va a releer antes de actuar
     * @return scope propio o vacío si ya existe trabajo activo; no permite reentrancia
     */
    Optional<Scope> tryAcquire(UUID processId);

    /** Recurso temporal: cerrar libera únicamente la adquisición propia, incluso tras un error. */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

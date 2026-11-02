package com.prog2.appointment.reservation.infrastructure.scheduling;

import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduling de intenciones autorizadas, con presupuesto persistido y sin esperas activas. */
public final class CancellationDispatcher {
    private final RecoverReservationCancellationsUseCase recover;

    public CancellationDispatcher(RecoverReservationCancellationsUseCase recover) {
        this.recover = recover;
    }

    @Scheduled(fixedDelayString = "${app.reservation.retry-delay:5s}")
    public void dispatch() {
        try {
            recover.recoverDue();
        } catch (ReservationStorageException _) {
            // El siguiente disparo relee SQL sin descartar ni recrear intenciones.
        }
    }
}

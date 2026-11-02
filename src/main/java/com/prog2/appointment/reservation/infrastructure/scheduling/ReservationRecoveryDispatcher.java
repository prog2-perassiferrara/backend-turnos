package com.prog2.appointment.reservation.infrastructure.scheduling;

import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationProcessesUseCase;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/** Habilita revisiones tras el arranque, sin ejecutar HTTP en el hilo de ApplicationReadyEvent. */
public final class ReservationRecoveryDispatcher {
    private static final System.Logger LOG = System.getLogger(ReservationRecoveryDispatcher.class.getName());
    private final RecoverReservationProcessesUseCase recover;
    private volatile boolean ready;

    public ReservationRecoveryDispatcher(RecoverReservationProcessesUseCase recover) {
        this.recover = recover;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        ready = true;
    }

    @Scheduled(fixedDelayString = "${app.reservation.retry-delay:5s}")
    public void dispatch() {
        if (!ready) return;
        try {
            recover.recoverDue();
        } catch (ReservationStorageException _) {
            LOG.log(System.Logger.Level.WARNING, "Reservation recovery storage unavailable");
        }
    }
}

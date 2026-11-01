package com.prog2.appointment.reservation.infrastructure.messaging;

import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.ports.in.PublishPendingPhonesUseCase;

import org.springframework.scheduling.annotation.Scheduled;

/** Scheduling de un solo worker gestionado por Spring; ningún bucle de espera en aplicación. */
public final class PhoneSubmissionDispatcher {
    private final PublishPendingPhonesUseCase publish;

    public PhoneSubmissionDispatcher(PublishPendingPhonesUseCase publish) {
        this.publish = publish;
    }

    @Scheduled(fixedDelayString = "${app.reservation.retry-delay:5s}")
    public void dispatch() {
        try {
            publish.publishPending();
        } catch (ReservationStorageException _) {
            /* Recuperación en el siguiente ciclo, sin efectos no persistidos. */
        }
    }
}

package com.prog2.appointment.reservation.application.usecases;

import lombok.RequiredArgsConstructor;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.ports.in.PublishPendingPhonesUseCase;
import com.prog2.appointment.reservation.domain.ports.out.PhonePublicationGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

import java.time.Clock;
import java.time.Duration;

/** Procesa lotes pequeños; registra cada intención antes de cruzar la frontera Kafka. */
@RequiredArgsConstructor
public final class PublishPendingPhonesUseCaseImpl implements PublishPendingPhonesUseCase {
    private final ReservationProcessRepository repository;
    private final PhonePublicationGateway publisher;
    private final Clock clock;
    private final int attempts;
    private final Duration retry;
    private final Duration cooldown;

    @Override
    public void publishPending() {
        for (var id : repository.duePhones(clock.instant(), 10)) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                var pending =
                        repository.beginPhonePublication(
                                id, clock.instant(), attempts, retry, cooldown);
                if (pending.isPresent()) {
                    publisher.publish(pending.get());
                    repository.phonePublished(id, clock.instant());
                }
            } catch (ReservationGatewayException | ReservationStorageException _) {
                // El próximo intento ya está persistido; jamás cambiar el ID ante un ACK incierto.
            }
        }
    }
}

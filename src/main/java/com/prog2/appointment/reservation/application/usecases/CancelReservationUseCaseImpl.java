package com.prog2.appointment.reservation.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.CancelReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.util.UUID;

/** Acepta la acción propia antes de delegar su ejecución al recuperador compartido. */
@Component
@RequiredArgsConstructor
public final class CancelReservationUseCaseImpl implements CancelReservationUseCase {
    private final ReservationProcessRepository repository;
    private final RecoverReservationCancellationsUseCase recover;
    private final Clock clock;

    @Override
    public ReservationStatus cancel(long ownerId, UUID processId) {
        try {
            repository.requestCancellation(ownerId, processId, clock.instant());
            return recover.recover(processId);
        } catch (ReservationCancellationException failure) {
            throw new ReservationOperationException(failure.failure(), null);
        }
    }
}

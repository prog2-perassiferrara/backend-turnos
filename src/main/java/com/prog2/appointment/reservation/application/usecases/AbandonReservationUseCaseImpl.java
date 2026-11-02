package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.CancellationKind;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.AbandonReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.util.UUID;

/** Acepta la acción propia antes de delegar su ejecución al recuperador compartido. */
public final class AbandonReservationUseCaseImpl implements AbandonReservationUseCase {
    private final ReservationProcessRepository repository;
    private final RecoverReservationCancellationsUseCase recover;
    private final Clock clock;

    public AbandonReservationUseCaseImpl(ReservationProcessRepository repository,
            RecoverReservationCancellationsUseCase recover, Clock clock) {
        this.repository = repository;
        this.recover = recover;
        this.clock = clock;
    }

    @Override
    public ReservationStatus abandon(long ownerId, UUID processId, Integer retryVersion) {
        try {
            var result = repository.requestCancellation(ownerId, processId,
                    CancellationKind.ABANDON, retryVersion, clock.instant());
            if (result.accepted()) return recover.recover(processId);
            return ReservationStatus.local(result.process(),
                    repository.latestPhone(processId).orElse(null), clock.instant());
        } catch (ReservationCancellationException failure) {
            throw new ReservationOperationException(failure.failure(), null);
        }
    }
}

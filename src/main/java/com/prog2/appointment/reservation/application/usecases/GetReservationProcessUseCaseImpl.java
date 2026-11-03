package com.prog2.appointment.reservation.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.util.UUID;

/** Consulta el progreso durable propio; la recuperación central se ejecuta por separado. */
@Component
@RequiredArgsConstructor
public final class GetReservationProcessUseCaseImpl implements GetReservationProcessUseCase {
    private final ReservationProcessRepository repository;
    private final Clock clock;

    @Override
    public ReservationStatus get(long ownerId, UUID processId) {
        var process = repository.find(processId)
                .filter(value -> value.ownerId() == ownerId)
                .orElseThrow(() -> new ReservationOperationException(
                        ReservationFailure.RESERVATION_PROCESS_NOT_FOUND, null));
        return ReservationStatus.local(process, repository.latestPhone(processId).orElse(null), clock.instant());
    }
}

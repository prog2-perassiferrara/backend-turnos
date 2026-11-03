package com.prog2.appointment.reservation.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.OwnReservationPage;
import com.prog2.appointment.reservation.domain.model.OwnReservations;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.ListOwnReservationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;

/** Lee pendiente e historial propios en la instantánea SQL del repositorio, sin consultas remotas. */
@Component
@RequiredArgsConstructor
public final class ListOwnReservationsUseCaseImpl implements ListOwnReservationsUseCase {
    private final ReservationProcessRepository repository;
    private final Clock clock;

    @Override
    public OwnReservations list(long ownerId, int page, int size) {
        try {
            OwnReservationPage.validate(page, size);
        } catch (IllegalArgumentException _) {
            throw new ReservationOperationException(ReservationFailure.INVALID_RESERVATION_PAGE, null);
        }
        var result = repository.readOwn(ownerId, page, size);
        if (result.history().stream().anyMatch(value -> value.ownerId() != ownerId)
                || result.pending() != null && result.pending().ownerId() != ownerId) {
            throw new ReservationStorageException();
        }
        var now = clock.instant();
        return new OwnReservations(
                result.pending() == null ? null : ReservationStatus.local(result.pending(), result.phone(), now),
                result.history().stream().map(value -> ReservationStatus.local(value, null, now)).toList(),
                result.totalElements(), page, size);
    }
}

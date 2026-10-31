package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationPhoneException;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

import java.time.Clock;
import java.util.UUID;

/** Acepta durabilidad local sin depender de Kafka ni de un JWT técnico vigente. */
public final class SubmitReservationPhoneUseCaseImpl implements SubmitReservationPhoneUseCase {
    private final ReservationProcessRepository repository;
    private final String groupId;
    private final Clock clock;

    public SubmitReservationPhoneUseCaseImpl(
            ReservationProcessRepository repository, String groupId, Clock clock) {
        this.repository = repository;
        this.groupId = groupId;
        this.clock = clock;
    }

    @Override
    public Result submit(long ownerId, UUID processId, SubmitPhoneRequest request) {
        repository
                .find(processId)
                .filter(process -> process.ownerId() == ownerId)
                .orElseThrow(
                        () ->
                                new ReservationOperationException(
                                        ReservationFailure.RESERVATION_PROCESS_NOT_FOUND, null));
        String phone;
        try {
            phone = PhoneSubmission.normalize(request.phoneNumber());
        } catch (IllegalArgumentException _) {
            throw new ReservationOperationException(
                    ReservationFailure.INVALID_PHONE_NUMBER, processId);
        }
        try {
            var result =
                    repository.submitPhone(
                            ownerId,
                            processId,
                            new SubmitPhoneRequest(request.requestId(), phone),
                            groupId,
                            clock.instant());
            var latest = repository.latestPhone(processId).orElse(null);
            return new Result(
                    ReservationStatus.local(result.process(), latest, clock.instant()),
                    !result.created());
        } catch (ReservationPhoneException failure) {
            throw new ReservationOperationException(
                    failure.failure(),
                    failure.failure() == ReservationFailure.RESERVATION_PROCESS_NOT_FOUND
                            ? null
                            : processId);
        }
    }
}

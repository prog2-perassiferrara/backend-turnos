package com.prog2.appointment.reservation.application.usecases;

import java.time.Clock;
import java.time.Instant;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.StartReservationRequest;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.out.AppointmentHoldGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservableSlotGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationMessagingReadiness;
import com.prog2.appointment.reservation.domain.ports.out.ReservationPatientGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

/**
 * Una intención durable por acción; las repeticiones nunca ejecutan los POST de una ejecución
 * anterior.
 */
public final class StartReservationUseCaseImpl implements StartReservationUseCase {
    private final ReservationPatientGateway patients;
    private final ReservableSlotGateway slots;
    private final AppointmentHoldGateway holds;
    private final ReservationProcessRepository repository;
    private final ReservationMessagingReadiness messaging;
    private final Clock clock;
    private final int holdAttempts;

    public StartReservationUseCaseImpl(
            ReservationPatientGateway patients,
            ReservableSlotGateway slots,
            AppointmentHoldGateway holds,
            ReservationProcessRepository repository,
            ReservationMessagingReadiness messaging,
            Clock clock,
            int holdAttempts) {
        this.patients = patients;
        this.slots = slots;
        this.holds = holds;
        this.repository = repository;
        this.messaging = messaging;
        this.clock = clock;
        if (holdAttempts < 1 || holdAttempts > 10) {
            throw new IllegalArgumentException("Invalid hold attempt budget");
        }
        this.holdAttempts = holdAttempts;
    }

    @Override
    public StartResult start(long ownerId, StartReservationRequest request) {
        if (ownerId <= 0 || request == null) {
            throw error(ReservationFailure.INVALID_RESERVATION_SELECTION, null);
        }
        messaging.requestPreparation();
        var existing = repository.find(request.requestId());
        if (existing.isPresent()) {
            return replay(existing.get(), ownerId, request);
        }
        repository
                .findPending(ownerId)
                .ifPresent(
                        value -> {
                            throw error(ReservationFailure.ACTIVE_RESERVATION_PROCESS, value);
                        });
        validTime(request.selection());
        var patient = patients.get(ownerId);
        if (patient.ownerId() != ownerId) {
            throw error(ReservationFailure.PROFILE_NOT_RESERVABLE, null);
        }
        slots.verify(request.selection());
        messaging.requireReady();
        var begin = repository.begin(ReservationProcess.begin(request, patient));
        var process = begin.process();
        if (!begin.created()) {
            if (!process.id().equals(request.requestId())) {
                throw error(ReservationFailure.ACTIVE_RESERVATION_PROCESS, process);
            }
            return replay(process, ownerId, request);
        }
        return new StartResult(execute(process), false);
    }

    private StartResult replay(
            ReservationProcess process, long ownerId, StartReservationRequest request) {
        if (!process.matches(ownerId, request)) {
            throw error(ReservationFailure.REQUEST_ID_CONFLICT, null);
        }
        return new StartResult(process, true);
    }

    private ReservationProcess execute(ReservationProcess process) {
        try {
            if (!isFuture(process.selection())) {
                throw new ReservationGatewayException(
                        ReservationFailure.INVALID_RESERVATION_SELECTION, false, true);
            }
            process = createHold(process);
            if (process.phase() != ReservationPhase.HELD) {
                return process;
            }
            var hold = process.hold();
            if (!clock.instant().isBefore(hold.expiresAt())) {
                throw new ReservationGatewayException(
                        ReservationFailure.CONFIRMATION_REJECTED, false, true);
            }
            process = repository.update(process.id(), ReservationProcess::beginConfirmation);
            if (process.phase() != ReservationPhase.CONFIRMING) {
                return process;
            }
            if (!clock.instant().isBefore(hold.expiresAt())) {
                throw new ReservationGatewayException(
                        ReservationFailure.CONFIRMATION_REJECTED, false, true);
            }
            var accepted = holds.confirm(process);
            return repository.update(
                    process.id(), value -> value.acceptInitialConfirmation(accepted));
        } catch (ReservationGatewayException failure) {
            var current =
                    repository.update(
                            process.id(), value -> value.recordFailure(failure, clock.instant()));
            if (current.phase() == ReservationPhase.WAITING_FOR_PHONE
                    || current.phase() == ReservationPhase.WAITING_FOR_REQUEST
                    || current.phase() == ReservationPhase.CONFIRMED
                    || current.phase() == ReservationPhase.CANCELLED) {
                return current;
            }
            throw error(failure.failure(), current);
        } catch (ReservationStorageException _) {
            throw error(ReservationFailure.STORAGE_UNAVAILABLE, process);
        }
    }

    private ReservationProcess createHold(ReservationProcess process) {
        for (int attempt = 1; attempt <= holdAttempts; attempt++) {
            if (process.phase() != ReservationPhase.CREATING_HOLD) {
                return process;
            }
            if (!isFuture(process.selection())) {
                if (attempt > 1) {
                    throw abandon(process, ReservationFailure.INVALID_RESERVATION_SELECTION, null);
                }
                throw new ReservationGatewayException(
                        ReservationFailure.INVALID_RESERVATION_SELECTION, false, true);
            }
            try {
                var hold = holds.create(process);
                return repository.update(process.id(), value -> value.acceptHold(hold));
            } catch (ReservationGatewayException failure) {
                if (!failure.attempted() || failure.definitive()) {
                    if (attempt > 1) {
                        throw abandon(process, failure.failure(), null);
                    }
                    throw failure;
                }
                process = retryUnknownCreation(process, failure, attempt);
            }
        }
        throw abandon(process, ReservationFailure.HOLD_CREATION_UNCERTAIN, null);
    }

    private ReservationProcess retryUnknownCreation(
            ReservationProcess process, ReservationGatewayException failure, int attempt) {
        process =
                repository.update(
                        process.id(), value -> value.recordFailure(failure, clock.instant()));
        try {
            var occupation = holds.findOccupancy(process.selection());
            if (occupation.isPresent()) {
                // Su expiresAt pertenece a la posición observada, no a un hold propio recuperado.
                throw abandon(
                        process,
                        ReservationFailure.SLOT_NOT_AVAILABLE,
                        occupation.get().expiresAt());
            }
            if (attempt >= holdAttempts) {
                throw abandon(process, ReservationFailure.HOLD_CREATION_UNCERTAIN, null);
            }
            if (!isFuture(process.selection())) {
                throw abandon(process, ReservationFailure.INVALID_RESERVATION_SELECTION, null);
            }
            slots.verify(process.selection());
            messaging.requireReady();
        } catch (ReservationGatewayException unavailable) {
            throw abandon(process, unavailable.failure(), null);
        }
        return repository.update(process.id(), ReservationProcess::prepareHoldRetry);
    }

    private ReservationOperationException abandon(
            ReservationProcess process, ReservationFailure failure, Instant recheckAt) {
        var closed =
                repository.update(
                        process.id(),
                        value ->
                                value.abandonHoldAttempt(
                                        ReservationFailure.HOLD_CREATION_UNCERTAIN.name(),
                                        clock.instant()));
        return new ReservationOperationException(failure, closed.id(), recheckAt);
    }

    private void validTime(ReservationSelection selection) {
        if (!isFuture(selection)) {
            throw error(ReservationFailure.INVALID_RESERVATION_SELECTION, null);
        }
    }

    private boolean isFuture(ReservationSelection selection) {
        var now = clock.instant().atZone(clock.getZone()).toLocalDateTime();
        return selection.date().atTime(selection.startTime()).isAfter(now);
    }

    private static ReservationOperationException error(
            ReservationFailure failure, ReservationProcess process) {
        return new ReservationOperationException(failure, process == null ? null : process.id());
    }
}

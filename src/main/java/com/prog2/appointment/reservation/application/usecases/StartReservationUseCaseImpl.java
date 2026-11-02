package com.prog2.appointment.reservation.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.prog2.appointment.reservation.domain.ports.out.ReservationExecutionGuard;
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
    private final ReservationExecutionGuard guard;
    private final int holdAttempts;

    // Los seis colaboradores y el reloj se inyectan explícitamente; agruparlos ocultaría dependencias.
    @SuppressWarnings("java:S107")
    public StartReservationUseCaseImpl(
            ReservationPatientGateway patients,
            ReservableSlotGateway slots,
            AppointmentHoldGateway holds,
            ReservationProcessRepository repository,
            ReservationMessagingReadiness messaging,
            ReservationExecutionGuard guard,
            Clock clock,
            int holdAttempts) {
        this.patients = patients;
        this.slots = slots;
        this.holds = holds;
        this.repository = repository;
        this.messaging = messaging;
        this.guard = guard;
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
        return new StartResult(runGuarded(process.id(), true), false);
    }

    @Override
    public ReservationProcess resumeKnownHold(UUID processId) {
        return runGuarded(processId, false);
    }

    private ReservationProcess current(UUID id) {
        return repository.find(id).orElseThrow(ReservationStorageException::new);
    }

    private ReservationProcess runGuarded(UUID id, boolean creating) {
        var acquired = guard.tryAcquire(id);
        if (acquired.isEmpty()) return current(id);
        try (var _ = acquired.orElseThrow()) {
            // Otro ejecutor pudo avanzar entre insertar la intención y obtener este scope.
            var process = current(id);
            if (!creating && (process.phase() != ReservationPhase.HELD || process.abandonmentPending()))
                return process;
            if (process.phase() == ReservationPhase.HELD
                    && !clock.instant().isBefore(process.hold().expiresAt()))
                return repository.update(id, value -> value.expireUnconfirmedHold(clock.instant()));
            if (!creating) {
                messaging.requestPreparation();
                messaging.requireReady();
            }
            return execute(process);
        }
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
            if (process.phase() == ReservationPhase.CREATING_HOLD && !isFuture(process.selection())) {
                throw new ReservationGatewayException(
                        ReservationFailure.INVALID_RESERVATION_SELECTION, false, true);
            }
            process = createHold(process);
            if (process.phase() != ReservationPhase.HELD) {
                return process;
            }
            var now = clock.instant();
            if (!now.isBefore(process.hold().expiresAt()))
                return repository.update(process.id(), value -> value.expireUnconfirmedHold(now));
            var claimed = repository.beginInitialConfirmation(process.id(), now);
            if (claimed.isEmpty()) return current(process.id());
            process = claimed.orElseThrow();
            var beforeTransport = clock.instant();
            if (!beforeTransport.isBefore(process.hold().expiresAt())) {
                var ownClaim = process;
                return repository.update(process.id(),
                        value -> value.expireUnsentConfirmation(ownClaim, beforeTransport));
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
            requireFutureCreation(process, attempt);
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

    /** Impide el POST fuera de horario; un reintento cierra la incertidumbre previa sin IDs. */
    private void requireFutureCreation(ReservationProcess process, int attempt) {
        if (isFuture(process.selection())) {
            return;
        }
        if (attempt > 1) {
            throw abandon(process, ReservationFailure.INVALID_RESERVATION_SELECTION, null);
        }
        throw new ReservationGatewayException(
                ReservationFailure.INVALID_RESERVATION_SELECTION, false, true);
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

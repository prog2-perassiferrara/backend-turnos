package com.prog2.appointment.reservation.application.usecases;

import java.time.Clock;
import java.time.ZoneId;
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
    private final ZoneId zone;

    public StartReservationUseCaseImpl(
            ReservationPatientGateway patients,
            ReservableSlotGateway slots,
            AppointmentHoldGateway holds,
            ReservationProcessRepository repository,
            ReservationMessagingReadiness messaging,
            Clock clock,
            ZoneId zone) {
        this.patients = patients;
        this.slots = slots;
        this.holds = holds;
        this.repository = repository;
        this.messaging = messaging;
        this.clock = clock;
        this.zone = zone;
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
            var hold = holds.create(process);
            process = repository.update(process.id(), value -> value.acceptHold(hold));
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

    private void validTime(ReservationSelection selection) {
        if (!isFuture(selection)) {
            throw error(ReservationFailure.INVALID_RESERVATION_SELECTION, null);
        }
    }

    private boolean isFuture(ReservationSelection selection) {
        var now = clock.instant().atZone(zone).toLocalDateTime();
        return selection.date().atTime(selection.startTime()).isAfter(now);
    }

    private static ReservationOperationException error(
            ReservationFailure failure, ReservationProcess process) {
        return new ReservationOperationException(failure, process == null ? null : process.id());
    }
}

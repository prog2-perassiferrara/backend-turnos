package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationRecoveryPolicy;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationProcessesUseCase;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationExecutionGuard;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Reutiliza los avances autorizados; cada revisión y cada lote tienen presupuesto propio. */
public final class RecoverReservationProcessesUseCaseImpl implements RecoverReservationProcessesUseCase {
    private static final System.Logger LOG = System.getLogger(RecoverReservationProcessesUseCaseImpl.class.getName());
    private final ReservationProcessRepository repository;
    private final CentralAppointmentGateway central;
    private final StartReservationUseCase start;
    private final ReservationExecutionGuard guard;
    private final Clock clock;
    private final ReservationRecoveryPolicy policy;

    public RecoverReservationProcessesUseCaseImpl(ReservationProcessRepository repository,
            CentralAppointmentGateway central, StartReservationUseCase start, ReservationExecutionGuard guard,
            Clock clock, ReservationRecoveryPolicy policy) {
        this.repository = repository;
        this.central = central;
        this.start = start;
        this.guard = guard;
        this.clock = clock;
        this.policy = policy;
    }

    @Override
    // Cada excepción expone failure() en su propio tipo; un multicatch pierde ese contrato.
    @SuppressWarnings("java:S2147")
    public void recoverDue() {
        try {
            repository.replayDeferredKnown(10);
        } catch (ReservationStorageException _) {
            LOG.log(System.Logger.Level.WARNING, "Deferred reservation recovery unavailable");
        }
        for (var id : repository.dueRecovery(clock.instant(), policy.cooldown(), 10)) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                recover(id);
            } catch (ReservationGatewayException failure) {
                pending(id, failure.failure());
            } catch (ReservationOperationException failure) {
                pending(id, failure.failure());
            } catch (ReservationStorageException _) {
                pending(id, ReservationFailure.STORAGE_UNAVAILABLE);
            }
        }
    }

    private static void pending(UUID id, ReservationFailure code) {
        LOG.log(System.Logger.Level.WARNING, "Reservation recovery pending: process={0}, code={1}", id, code);
    }

    private void recover(UUID id) {
        var acquired = guard.tryAcquire(id);
        if (acquired.isEmpty()) {
            repository.deferRecovery(id, clock.instant().plus(policy.retryDelay()));
            return;
        }
        boolean resume = false;
        try (var _ = acquired.orElseThrow()) {
            var now = clock.instant();
            var claimed = repository.beginRecovery(
                    id, now, policy.attempts(), policy.retryDelay(), policy.cooldown());
            if (claimed.isEmpty()) return;
            var process = claimed.orElseThrow();
            if (process.hold() == null) {
                repository.update(id, value -> value.abandonHoldAttempt(
                        ReservationFailure.HOLD_CREATION_UNCERTAIN.name(), now));
            } else if (process.phase() == ReservationPhase.HELD) {
                if (!now.isBefore(process.hold().expiresAt()))
                    repository.update(id, value -> value.expireUnconfirmedHold(now));
                else resume = !process.abandonmentPending();
            } else if (shouldVerify(process, now)) {
                central.find(process).filter(value -> value.belongsTo(process))
                        .ifPresent(value -> repository.reconcile(id, value, clock.instant()));
            }
        }
        // La continuación obtiene su propio scope y vuelve a leer: no anidar ni pasar permisos viejos.
        if (resume) start.resumeKnownHold(id);
    }

    private boolean shouldVerify(ReservationProcess process, Instant now) {
        if (!now.isBefore(process.hold().expiresAt())) return true;
        if (process.phase() == ReservationPhase.PHONE_QUEUED) return false;
        if (process.phase() != ReservationPhase.WAITING_FOR_RESULT) return true;
        var phone = repository.latestPhone(process.id());
        return phone.isEmpty() || phone.get().publishedAt() == null
                || !now.isBefore(phone.get().publishedAt().plus(policy.verificationDelay()));
    }
}

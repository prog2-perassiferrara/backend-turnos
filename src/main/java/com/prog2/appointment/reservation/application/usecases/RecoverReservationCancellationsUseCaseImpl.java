package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.enums.VerificationStatus;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationCancellationGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Reclama presupuesto durable, reconcilia y ejecuta un solo efecto fuera de SQL. */
public final class RecoverReservationCancellationsUseCaseImpl implements RecoverReservationCancellationsUseCase {
    private static final System.Logger LOG = System.getLogger(RecoverReservationCancellationsUseCaseImpl.class.getName());
    private final ReservationProcessRepository repository;
    private final CentralAppointmentGateway central;
    private final ReservationCancellationGateway cancellation;
    private final Clock clock;
    private final Duration retry;
    private final Duration cooldown;
    private final Duration budget;

    public RecoverReservationCancellationsUseCaseImpl(ReservationProcessRepository repository,
            CentralAppointmentGateway central, ReservationCancellationGateway cancellation,
            Clock clock, Duration httpTimeout, Duration retry, Duration cooldown) {
        this.repository = repository;
        this.central = central;
        this.cancellation = cancellation;
        this.clock = clock;
        this.retry = retry;
        this.cooldown = cooldown;
        this.budget = httpTimeout.multipliedBy(4).plus(retry);
    }

    @Override
    public void recoverDue() {
        for (var id : repository.dueCancellations(clock.instant(), 10)) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                recover(id);
            } catch (ReservationStorageException _) {
                LOG.log(System.Logger.Level.WARNING, "Cancellation recovery unavailable: process={0}", id);
            }
        }
    }

    @Override
    public ReservationStatus recover(UUID processId) {
        expireStableAbandonment(processId);
        var claim = repository.beginCancellationAttempt(processId, clock.instant(), budget);
        if (claim.isEmpty()) return view(processId, VerificationStatus.LOCAL, null);
        var claimed = claim.get();
        LOG.log(System.Logger.Level.DEBUG, "Cancellation attempt: process={0}, cycle={1}, attempt={2}",
                processId, claimed.cancellation().retryVersion(), claimed.cancellation().attempts());
        try {
            if (requiresCentralCheck(claimed)) {
                var evidence = central.find(claimed).filter(value -> value.belongsTo(claimed));
                if (evidence.isPresent()) {
                    var updated = repository.reconcile(processId, evidence.get(), clock.instant());
                    if (updated.cancellation().state()
                            == com.prog2.appointment.reservation.domain.enums.CancellationState.RESOLVED) {
                        return view(processId, VerificationStatus.VERIFIED, null);
                    }
                }
            }
            var current = expireStableAbandonment(processId);
            if (current.cancellation() == null || !current.cancellation().matches(claimed.cancellation())) {
                return view(processId, VerificationStatus.LOCAL, null);
            }
            if (current.phase() != ReservationPhase.CONFIRMED) {
                var pending = new ReservationGatewayException(ReservationFailure.ABANDONMENT_UNCERTAIN, false, false);
                repository.update(processId, value -> value.cancellationFailed(claimed, pending,
                        clock.instant(), retry, cooldown));
                return view(processId, VerificationStatus.INCONCLUSIVE, pending.failure().name());
            }
            var evidence = cancellation.cancel(current);
            var result = repository.confirmCancellation(processId, evidence, clock.instant());
            boolean verified = result.phase()
                    == com.prog2.appointment.reservation.domain.enums.ReservationPhase.CANCELLED;
            return view(processId, verified ? VerificationStatus.VERIFIED : VerificationStatus.INCONCLUSIVE,
                    verified ? null : ReservationFailure.CORRELATION_MISMATCH.name());
        } catch (ReservationGatewayException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "Cancellation outcome pending: process={0}, cycle={1}, attempt={2}, code={3}",
                    processId, claimed.cancellation().retryVersion(), claimed.cancellation().attempts(), failure.failure());
            repository.update(processId, value -> value.cancellationFailed(claimed, failure,
                    clock.instant(), retry, cooldown));
            return view(processId, failure.definitive()
                    ? VerificationStatus.INCONCLUSIVE : VerificationStatus.UNAVAILABLE, failure.failure().name());
        }
    }

    private boolean requiresCentralCheck(ReservationProcess claimed) {
        return claimed.phase() != ReservationPhase.CONFIRMED
                || claimed.cancellation().attempts() > 1 || claimed.cancellation().retryVersion() > 0;
    }

    /** La aceptación ya impide nuevos teléfonos; cualquier respuesta previa exige reconciliar. */
    private ReservationProcess expireStableAbandonment(UUID processId) {
        var process = repository.find(processId).orElseThrow(ReservationStorageException::new);
        var now = clock.instant();
        if (process.canExpireAbandonment(now) && repository.latestPhone(processId).isEmpty()) {
            return repository.update(processId, value -> value.expireAbandonment(now));
        }
        return process;
    }

    private ReservationStatus view(UUID id, VerificationStatus verification, String warning) {
        var process = repository.find(id).orElseThrow(ReservationStorageException::new);
        var phone = repository.latestPhone(id).orElse(null);
        var now = clock.instant();
        var local = ReservationStatus.local(process, phone, now);
        return new ReservationStatus(process, local.phoneRequestId(), local.canSubmitPhone(), verification,
                verification == VerificationStatus.VERIFIED ? now : null, warning);
    }
}

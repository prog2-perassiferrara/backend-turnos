package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.application.service.ReservationExecutionGuard;
import com.prog2.appointment.reservation.domain.enums.CancellationState;
import com.prog2.appointment.reservation.domain.enums.CentralAppointmentState;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationCancellationGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Hasta tres POST por ejecución, con evidencia central previa y sin historial durable de ciclos. */
public final class RecoverReservationCancellationsUseCaseImpl implements RecoverReservationCancellationsUseCase {
    private static final int MAX_ATTEMPTS = 3;
    private static final System.Logger LOG = System.getLogger(RecoverReservationCancellationsUseCaseImpl.class.getName());
    private final ReservationProcessRepository repository;
    private final CentralAppointmentGateway central;
    private final ReservationCancellationGateway cancellation;
    private final Clock clock;
    private final Duration retry;
    private final Duration budget;
    private final ReservationExecutionGuard guard;

    public RecoverReservationCancellationsUseCaseImpl(ReservationProcessRepository repository,
            CentralAppointmentGateway central, ReservationCancellationGateway cancellation,
            Clock clock, Duration httpTimeout, Duration retry, ReservationExecutionGuard guard) {
        this.repository = repository;
        this.central = central;
        this.cancellation = cancellation;
        this.clock = clock;
        this.retry = retry;
        this.guard = guard;
        // Tres pares GET/POST, cada llamada limitada a 2 * httpTimeout; no es un contador de ciclos.
        this.budget = httpTimeout.multipliedBy(12).plus(retry);
    }

    @Override
    public ReservationStatus recover(UUID processId) {
        var acquired = guard.tryAcquire(processId);
        if (acquired.isEmpty()) return view(processId, null);
        try (var _ = acquired.orElseThrow()) {
            var claim = repository.beginCancellationAttempt(processId, clock.instant(), budget);
            return view(processId, claim.isEmpty() ? null : execute(claim.orElseThrow()));
        }
    }

    private String execute(ReservationProcess claimed) {
        int attempts = 0;
        String failure = null;
        try {
            while (attempts < MAX_ATTEMPTS) {
                var current = repository.find(claimed.id()).orElseThrow(ReservationStorageException::new);
                if (!current.writeId().equals(claimed.writeId()) || current.cancellation() == null) return null;
                var evidence = central.find(current).filter(value -> value.belongsTo(current));
                if (evidence.isEmpty()) {
                    failure = ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE.name();
                    return failure;
                }
                if (requiresReconciliation(current, evidence.get())) {
                    repository.reconcile(current.id(), evidence.get(), clock.instant());
                    failure = null;
                    return null;
                }
                // El estado detenido se sigue consultando, pero solo el propietario puede reabrirlo.
                if (current.cancellation() == CancellationState.RETRY_REQUIRED) return current.reasonCode();
                var latest = repository.find(current.id()).orElseThrow(ReservationStorageException::new);
                if (!canPost(latest, claimed)) return null;
                failure = ReservationFailure.CANCELLATION_UNCERTAIN.name();
                attempts++;
                LOG.log(System.Logger.Level.INFO, "Cancellation POST: process={0}, attempt={1}/{2}",
                        current.id(), attempts, MAX_ATTEMPTS);
                var result = cancelOnce(current.id(), latest);
                failure = result.failure();
                if (!result.retryable()) return failure;
            }
            return failure;
        } catch (ReservationGatewayException unavailable) {
            failure = unavailable.failure().name();
            return failure;
        } finally {
            finishAttempt(claimed, attempts, failure);
        }
    }

    private void finishAttempt(ReservationProcess claimed, int attempts, String failure) {
        if (failure != null) LOG.log(System.Logger.Level.WARNING,
                "Cancellation pending: process={0}, code={1}", claimed.id(), failure);
        var now = clock.instant();
        repository.finishCancellationAttempt(claimed.id(), claimed.writeId(), attempts > 0,
                failure, now, now.plus(retry));
    }

    private static boolean requiresReconciliation(ReservationProcess current, CentralAppointment evidence) {
        return evidence.state() != CentralAppointmentState.CONFIRMED
                || current.phase() != ReservationPhase.CONFIRMED;
    }

    private static boolean canPost(ReservationProcess latest, ReservationProcess claimed) {
        return latest.writeId().equals(claimed.writeId()) && latest.phase() == ReservationPhase.CONFIRMED;
    }

    /** Solo los fallos recuperables de este POST permiten volver al bucle con otra consulta previa. */
    private CancellationAttempt cancelOnce(UUID processId, ReservationProcess latest) {
        try {
            var result = repository.confirmCancellation(processId, cancellation.cancel(latest), clock.instant());
            var warning = result.phase() == ReservationPhase.CANCELLED
                    ? null : ReservationFailure.CORRELATION_MISMATCH.name();
            return new CancellationAttempt(warning, false);
        } catch (ReservationGatewayException unavailable) {
            return new CancellationAttempt(unavailable.failure().name(), !unavailable.definitive());
        }
    }

    /** Resultado local de una llamada, sin estado persistido ni ciclo de recuperación propio. */
    private record CancellationAttempt(String failure, boolean retryable) {}

    private ReservationStatus view(UUID id, String warning) {
        var process = repository.find(id).orElseThrow(ReservationStorageException::new);
        var phone = repository.latestPhone(id).orElse(null);
        var local = ReservationStatus.local(process, phone, clock.instant());
        return new ReservationStatus(process, local.phoneRequestId(), local.canSubmitPhone(), warning);
    }
}

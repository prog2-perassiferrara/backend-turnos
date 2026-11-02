package com.prog2.appointment.reservation.infrastructure.persistence.adapter;

import com.prog2.appointment.reservation.domain.model.OwnReservationPage;

import java.sql.SQLException;
import com.prog2.appointment.reservation.domain.enums.CancellationKind;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.model.CancellationEvidence;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationPhoneException;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import com.prog2.appointment.reservation.domain.exception.ReservationConflictException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import com.prog2.appointment.reservation.infrastructure.persistence.service.TransactionalReservationStore;

/**
 * Adapter de persistencia: verifica escrituras ambiguas después del proxy, sin repetir efectos
 * externos.
 */
@Component
public final class JpaReservationProcessAdapter implements ReservationProcessRepository {
    private final TransactionalReservationStore store;

    public JpaReservationProcessAdapter(TransactionalReservationStore store) {
        this.store = store;
    }

    @Override
    public OwnReservationPage readOwn(long ownerId, int page, int size) {
        return read(() -> store.readOwn(ownerId, page, size));
    }

    @Override
    public Optional<ReservationProcess> find(UUID id) {
        try {
            return store.find(id);
        } catch (DataAccessException | TransactionException _) {
            throw new ReservationStorageException();
        }
    }

    @Override
    public Optional<ReservationProcess> findPending(long ownerId) {
        try {
            return store.pending(ownerId);
        } catch (DataAccessException | TransactionException _) {
            throw new ReservationStorageException();
        }
    }

    @Override
    public BeginResult begin(ReservationProcess candidate) {
        try {
            return new BeginResult(store.create(candidate), true);
        } catch (DataAccessException | TransactionException failure) {
            var same = find(candidate.id());
            if (same.isPresent()) {
                if (same.get().ownerId() != candidate.ownerId()) {
                    throw new ReservationConflictException();
                }
                boolean ownWrite =
                        !duplicate(failure) && same.get().writeId().equals(candidate.writeId());
                return new BeginResult(same.get(), ownWrite);
            }
            if (duplicate(failure)) {
                return new BeginResult(
                        findPending(candidate.ownerId())
                                .orElseThrow(ReservationStorageException::new),
                        false);
            }
            throw new ReservationStorageException();
        }
    }

    @Override
    public ReservationProcess update(UUID id, UnaryOperator<ReservationProcess> change) {
        UUID writeId = UUID.randomUUID();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return store.update(id, writeId, change);
            } catch (OptimisticLockingFailureException _) {
                /* Releer en una transacción nueva, sin repetir HTTP. */
            } catch (DataAccessException | TransactionException _) {
                return find(id).filter(value -> value.writeId().equals(writeId))
                        .orElseThrow(ReservationStorageException::new);
            }
        }
        throw new ReservationStorageException();
    }

    @Override
    public void accept(ReservationDelivery delivery) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                store.accept(delivery);
                return;
            } catch (OptimisticLockingFailureException _) {
                /* El recibo también revierte; volver a leer ambos. */
            } catch (DataAccessException | TransactionException failure) {
                try {
                    if (store.hasReceipt(delivery)) {
                        return;
                    }
                } catch (DataAccessException | TransactionException _) {
                    throw new ReservationStorageException();
                }
                if (!duplicate(failure)) {
                    throw new ReservationStorageException();
                }
            }
        }
        throw new ReservationStorageException();
    }

    @Override
    public Optional<ReservationProcess> beginInitialConfirmation(UUID id, Instant now) {
        UUID writeId = UUID.randomUUID();
        return cancellationWrite(() -> store.beginInitialConfirmation(id, now, writeId),
                () -> find(id).filter(value -> value.writeId().equals(writeId)
                        && value.phase() == ReservationPhase.CONFIRMING));
    }

    @Override
    public List<UUID> dueRecovery(Instant now, Duration initialDelay, int limit) {
        return read(() -> store.dueRecovery(now, initialDelay, limit));
    }

    @Override
    public Optional<ReservationProcess> beginRecovery(UUID id, Instant now, int attempts,
            Duration retry, Duration cooldown) {
        return write(() -> store.beginRecovery(id, now, attempts, retry, cooldown));
    }

    @Override
    public void deferRecovery(UUID id, Instant nextAttempt) {
        write(() -> { store.deferRecovery(id, nextAttempt); return null; });
    }

    @Override
    public void replayDeferredKnown(int limit) {
        for (var id : read(() -> store.deferredKnown(limit))) {
            write(() -> { store.replayDeferred(id); return null; });
        }
    }

    @Override
    public PhoneResult submitPhone(
            long ownerId, UUID processId, SubmitPhoneRequest request, String groupId, Instant now) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var result = store.submitPhone(ownerId, processId, request, groupId, now);
                return new PhoneResult(
                        result.process(),
                        findPhone(request.requestId())
                                .orElseThrow(ReservationStorageException::new),
                        result.created());
            } catch (OptimisticLockingFailureException _) {
                // No hubo commit: repetir solo la aceptación local sobre datos actuales.
            } catch (DataAccessException | TransactionException failure) {
                var saved = committedPhone(ownerId, processId, request);
                if (saved.isPresent()) return saved.get();
                if (!duplicate(failure)) throw new ReservationStorageException();
            }
        }
        throw new ReservationStorageException();
    }

    private Optional<PhoneResult> committedPhone(
            long ownerId, UUID processId, SubmitPhoneRequest request) {
        var own =
                find(processId)
                        .filter(value -> value.ownerId() == ownerId)
                        .orElseThrow(
                                () ->
                                        new ReservationPhoneException(
                                                ReservationFailure.RESERVATION_PROCESS_NOT_FOUND));
        return findPhone(request.requestId())
                .map(
                        phone -> {
                            if (!phone.matches(processId, request))
                                throw new ReservationPhoneException(
                                        ReservationFailure.REQUEST_ID_CONFLICT);
                            return new PhoneResult(own, phone, false);
                        });
    }

    @Override
    public Optional<PhoneSubmission> findPhone(UUID id) {
        return read(() -> store.findPhone(id));
    }

    @Override
    public Optional<PhoneSubmission> latestPhone(UUID processId) {
        return read(() -> store.latestPhone(processId));
    }

    @Override
    public List<UUID> duePhones(Instant now, int limit) {
        return read(() -> store.duePhones(now, limit));
    }

    @Override
    public Optional<PhoneSubmission> beginPhonePublication(
            UUID id, Instant now, int attempts, Duration retry, Duration cooldown) {
        return write(() -> store.beginPhonePublication(id, now, attempts, retry, cooldown));
    }

    @Override
    public void phonePublished(UUID id, Instant now) {
        write(
                () -> {
                    store.phonePublished(id, now);
                    return null;
                });
    }

    @Override
    public ReservationProcess reconcile(UUID id, CentralAppointment evidence, Instant now) {
        return write(() -> store.reconcile(id, evidence, now));
    }

    @Override
    public CancellationAcceptance requestCancellation(long ownerId, UUID id, CancellationKind kind,
            Integer retryVersion, Instant now) {
        UUID writeId = UUID.randomUUID();
        return cancellationWrite(
                () -> store.requestCancellation(ownerId, id, kind, retryVersion, now, writeId),
                () -> committedCancellation(ownerId, id, kind, retryVersion, writeId));
    }

    private CancellationAcceptance committedCancellation(long ownerId, UUID id, CancellationKind kind,
            Integer retryVersion, UUID writeId) {
        var current = find(id).filter(value -> value.ownerId() == ownerId)
                .orElseThrow(() -> new ReservationCancellationException(
                        ReservationFailure.RESERVATION_PROCESS_NOT_FOUND));
        var progress = current.cancellation();
        if (progress == null || progress.kind() != kind
                || retryVersion != null && progress.retryVersion() <= retryVersion) {
            throw new ReservationStorageException();
        }
        return new CancellationAcceptance(current, current.writeId().equals(writeId));
    }

    @Override
    public List<UUID> dueCancellations(Instant now, int limit) {
        return read(() -> store.dueCancellations(now, limit));
    }

    @Override
    public Optional<ReservationProcess> beginCancellationAttempt(UUID id, Instant now, Duration budget) {
        UUID writeId = UUID.randomUUID();
        return cancellationWrite(() -> store.beginCancellationAttempt(id, now, budget, writeId),
                () -> find(id).filter(value -> value.writeId().equals(writeId)));
    }

    @Override
    public ReservationProcess confirmCancellation(UUID id, CancellationEvidence evidence, Instant now) {
        return cancellationWrite(() -> store.confirmCancellation(id, evidence, now),
                () -> find(id).filter(value -> value.phase() == ReservationPhase.CANCELLED
                        && evidence.belongsTo(value)).orElseThrow(ReservationStorageException::new));
    }

    /** Repite solo transacciones locales revertidas, nunca el POST central. */
    private static <T> T cancellationWrite(Supplier<T> operation, Supplier<T> ambiguous) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException _) {
                // Releer y volver a evaluar las condiciones en otra transacción.
            } catch (DataAccessException | TransactionException _) {
                return ambiguous.get();
            }
        }
        throw new ReservationStorageException();
    }

    private static <T> T read(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException | TransactionException _) {
            throw new ReservationStorageException();
        }
    }

    private static <T> T write(Supplier<T> operation) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException _) {
                /* Nueva transacción en cada intento. */
            } catch (DataAccessException | TransactionException _) {
                throw new ReservationStorageException();
            }
        }
        throw new ReservationStorageException();
    }

    private static boolean duplicate(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            // Comprobación de tipo únicamente en la frontera SQL; nunca decidir por mensajes del
            // proveedor.
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }
}

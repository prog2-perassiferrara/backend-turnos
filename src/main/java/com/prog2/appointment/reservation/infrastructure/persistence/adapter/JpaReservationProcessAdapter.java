package com.prog2.appointment.reservation.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import com.prog2.appointment.reservation.domain.model.OwnReservationPage;

import java.sql.SQLException;
import com.prog2.appointment.reservation.domain.model.CancellationEvidence;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
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
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import com.prog2.appointment.reservation.infrastructure.persistence.service.TransactionalReservationStore;

/**
 * Adapter de persistencia: solo un retorno normal del proxy permite continuar.
 * Los errores de commit se propagan; recovery relee el estado sin atribuir autoría del commit.
 */
@Component
@RequiredArgsConstructor
public final class JpaReservationProcessAdapter implements ReservationProcessRepository {
    private final TransactionalReservationStore store;

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
            if (!duplicate(failure)) throw new ReservationStorageException();
            // Un conflicto único nunca otorga permiso de creación, aunque exista la misma fila.
            var same = find(candidate.id());
            if (same.isPresent()) {
                if (same.get().ownerId() != candidate.ownerId()) throw new ReservationConflictException();
                return new BeginResult(same.get(), false);
            }
            return new BeginResult(findPending(candidate.ownerId())
                    .orElseThrow(ReservationStorageException::new), false);
        }
    }

    @Override
    public ReservationProcess update(UUID id, UnaryOperator<ReservationProcess> change) {
        return write(() -> store.update(id, change));
    }

    @Override
    public void accept(ReservationAction action) {
        // Incluso ante un commit incierto se propaga el fallo; redelivery verificará el eventId.
        write(() -> { store.accept(action); return null; });
    }


    @Override
    public Optional<ReservationProcess> beginInitialConfirmation(UUID id, Instant now) {
        return write(() -> store.beginInitialConfirmation(id, now));
    }

    @Override
    public List<UUID> dueRecovery(Instant now, Duration initialDelay, int limit) {
        return read(() -> store.dueRecovery(now, initialDelay, limit));
    }

    @Override
    public Optional<ReservationProcess> beginRecovery(UUID id, Instant now, Duration retry, Duration initialDelay) {
        return write(() -> store.beginRecovery(id, now, retry, initialDelay));
    }

    @Override
    public void deferRecovery(UUID id, Instant nextAttempt) {
        write(() -> { store.deferRecovery(id, nextAttempt); return null; });
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
                if (!duplicate(failure)) throw new ReservationStorageException();
                // Solo un conflicto único conocido permite repetir la validación local, nunca un
                // commit incierto. El UUID de la respuesta sigue siendo la clave de idempotencia.
            }
        }
        throw new ReservationStorageException();
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
    public ReservationProcess requestCancellation(long ownerId, UUID id, Instant now) {
        return write(() -> store.requestCancellation(ownerId, id, now));
    }

    @Override
    public Optional<ReservationProcess> beginCancellationAttempt(UUID id, Instant now, Duration budget) {
        return write(() -> store.beginCancellationAttempt(id, now, budget));
    }

    @Override
    public void finishCancellationAttempt(UUID id, UUID writeId, boolean requireRetry, String failure,
            Instant now, Instant nextReview) {
        write(() -> { store.finishCancellationAttempt(id, writeId, requireRetry, failure, now, nextReview); return null; });
    }

    @Override
    public ReservationProcess confirmCancellation(UUID id, CancellationEvidence evidence, Instant now) {
        return write(() -> store.confirmCancellation(id, evidence, now));
    }

    /** Repite solo transacciones locales revertidas, nunca el POST central. */
    private static <T> T write(Supplier<T> operation) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException _) {
                // Releer y volver a evaluar las condiciones en otra transacción.
            } catch (DataAccessException | TransactionException _) {
                throw new ReservationStorageException();
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

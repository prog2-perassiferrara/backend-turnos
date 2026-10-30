package com.prog2.appointment.reservation.infrastructure.persistence.adapter;

import java.sql.SQLException;
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

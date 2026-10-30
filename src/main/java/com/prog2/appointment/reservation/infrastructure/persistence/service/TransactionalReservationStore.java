package com.prog2.appointment.reservation.infrastructure.persistence.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationInboxEntity;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import com.prog2.appointment.reservation.infrastructure.persistence.mapper.ReservationPersistenceMapper;
import com.prog2.appointment.reservation.infrastructure.persistence.repository.JpaReservationInboxRepository;
import com.prog2.appointment.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;

/**
 * Transacciones cortas detrás del proxy; el retorno al adaptador incluye confirmación del commit.
 */
@Component
public class TransactionalReservationStore {
    private static final String REJECTED = "REJECTED";
    private final JpaReservationProcessRepository processes;
    private final JpaReservationInboxRepository inbox;
    private final ReservationPersistenceMapper mapper;
    private final Clock clock;

    public TransactionalReservationStore(
            JpaReservationProcessRepository processes,
            JpaReservationInboxRepository inbox,
            ReservationPersistenceMapper mapper,
            Clock clock) {
        this.processes = processes;
        this.inbox = inbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public ReservationProcess create(ReservationProcess candidate) {
        return mapper.toDomain(
                processes.saveAndFlush(new ReservationProcessEntity(candidate, clock.instant())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public Optional<ReservationProcess> find(UUID id) {
        return processes.findById(id.toString()).map(mapper::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public Optional<ReservationProcess> pending(long ownerId) {
        return processes.findPending(ownerId).map(mapper::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public ReservationProcess update(
            UUID id, UUID writeId, UnaryOperator<ReservationProcess> change) {
        var entity = processes.findById(id.toString()).orElseThrow();
        var before = mapper.toDomain(entity);
        var after = change.apply(before);
        if (after.equals(before)) {
            return before;
        }
        if (!before.selection().equals(after.selection())
                || !before.patient().equals(after.patient())) {
            throw new IllegalArgumentException("Cannot change reservation selection or owner");
        }
        entity.apply(after, writeId, clock.instant());
        processes.flush();
        return mapper.toDomain(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public boolean hasReceipt(ReservationDelivery delivery) {
        var origin = delivery.origin();
        var receipt = inbox.findDelivery(origin.topic(), origin.partition(), origin.offset());
        if (receipt.isPresent()) {
            return receipt.get().getFingerprint().equals(delivery.fingerprint());
        }
        if (delivery.action() == null) {
            return false;
        }
        return inbox.findByEventId(delivery.action().correlation().eventId())
                .filter(value -> value.getFingerprint().equals(delivery.fingerprint()))
                .isPresent();
    }

    /** Recibo y cambio son indivisibles; un registro inválido también se conserva antes del ACK. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void accept(ReservationDelivery delivery) {
        var origin = delivery.origin();
        if (inbox.findDelivery(origin.topic(), origin.partition(), origin.offset()).isPresent()) {
            return;
        }
        var action = delivery.action();
        String eventId = action == null ? null : action.correlation().eventId();
        var previous =
                eventId == null
                        ? Optional.<ReservationInboxEntity>empty()
                        : inbox.findByEventId(eventId);
        if (previous.isPresent()) {
            if (previous.get().getFingerprint().equals(delivery.fingerprint())) {
                return;
            }
            var rejected = new ReservationInboxEntity(delivery, null, clock.instant());
            rejected.classify(REJECTED, "EVENT_ID_CONFLICT", null);
            inbox.saveAndFlush(rejected);
            return;
        }
        var receipt = new ReservationInboxEntity(delivery, eventId, clock.instant());
        if (action == null || delivery.rejection() != null) {
            receipt.classify(REJECTED, delivery.rejection(), null);
        } else {
            var entity = processes.findByCentralProcessId(action.correlation().centralProcessId());
            if (entity.isEmpty()) {
                receipt.classify("DEFERRED", "UNKNOWN_PROCESS", null);
            } else {
                apply(entity.get(), receipt, action);
            }
        }
        inbox.saveAndFlush(receipt);
    }

    private void apply(
            ReservationProcessEntity entity,
            ReservationInboxEntity receipt,
            ReservationAction action) {
        var before = mapper.toDomain(entity);
        if (!before.hold().holdId().equals(action.correlation().holdId())) {
            receipt.classify(REJECTED, "HOLD_MISMATCH", null);
            return;
        }
        if (action.type() == ReservationActionType.OTHER) {
            receipt.classify("DEFERRED", "UNHANDLED_ACTION", entity.getId());
            return;
        }
        var after = before.apply(action, clock.instant());
        if (!after.equals(before)) {
            entity.apply(after, UUID.randomUUID(), clock.instant());
            processes.flush();
        }
        receipt.classify(after.equals(before) ? "IGNORED" : "APPLIED", null, entity.getId());
    }
}

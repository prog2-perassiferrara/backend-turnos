package com.prog2.appointment.reservation.infrastructure.persistence.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationPhoneException;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository.PhoneResult;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.PhoneSubmissionEntity;
import com.prog2.appointment.reservation.infrastructure.persistence.repository.JpaPhoneSubmissionRepository;
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
    private final JpaPhoneSubmissionRepository phones;

    public TransactionalReservationStore(
            JpaReservationProcessRepository processes,
            JpaReservationInboxRepository inbox,
            ReservationPersistenceMapper mapper,
            Clock clock,
            JpaPhoneSubmissionRepository phones) {
        this.processes = processes;
        this.inbox = inbox;
        this.mapper = mapper;
        this.clock = clock;
        this.phones = phones;
    }

    /** Aceptación atómica: propietario, UUID histórico y exclusión activa se comprueban aquí. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public PhoneResult submitPhone(
            long ownerId, UUID processId, SubmitPhoneRequest request, String groupId, Instant now) {
        var entity =
                processes
                        .findById(processId.toString())
                        .filter(value -> value.getOwnerId() == ownerId)
                        .orElseThrow(
                                () ->
                                        new ReservationPhoneException(
                                                ReservationFailure.RESERVATION_PROCESS_NOT_FOUND));
        var process = mapper.toDomain(entity);
        var previous = phones.findById(request.requestId().toString());
        if (previous.isPresent()) {
            var phone = mapper.toDomain(previous.get());
            if (!phone.matches(processId, request)) {
                throw new ReservationPhoneException(ReservationFailure.REQUEST_ID_CONFLICT);
            }
            return new PhoneResult(process, phone, false);
        }
        if (phones.active(processId.toString()).isPresent()) {
            throw new ReservationPhoneException(ReservationFailure.PHONE_SUBMISSION_ACTIVE);
        }
        if (process.hold() != null && !now.isBefore(process.hold().expiresAt())) {
            throw new ReservationPhoneException(ReservationFailure.PHONE_WINDOW_CLOSED);
        }
        if (!process.canSubmitPhone(now)) {
            throw new ReservationPhoneException(ReservationFailure.PHONE_NOT_REQUESTED);
        }
        var phone =
                PhoneSubmission.create(
                        request.requestId(), process, groupId, request.phoneNumber(), now);
        var row = phones.saveAndFlush(new PhoneSubmissionEntity(phone));
        entity.apply(process.queuePhone(), UUID.randomUUID(), now);
        processes.flush();
        // Releer timestamps de precisión SQL antes de que cualquier publicación use el evento.
        return new PhoneResult(mapper.toDomain(entity), mapper.toDomain(row), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public Optional<PhoneSubmission> findPhone(UUID id) {
        return phones.findById(id.toString()).map(mapper::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public Optional<PhoneSubmission> latestPhone(UUID processId) {
        return phones.latest(processId.toString()).map(mapper::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public List<UUID> duePhones(Instant now, int limit) {
        return phones.due(now, PageRequest.of(0, limit)).stream().map(UUID::fromString).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<PhoneSubmission> beginPhonePublication(
            UUID id, Instant now, int attempts, Duration retry, Duration cooldown) {
        var row = phones.findById(id.toString()).orElseThrow();
        var before = mapper.toDomain(row);
        if (!before.isDue(now)) return Optional.empty();
        var entity = processes.findById(before.processId().toString()).orElseThrow();
        var process = mapper.toDomain(entity);
        var after =
                process.phase().isFinal()
                        ? before.finish(now, "PROCESS_CLOSED")
                        : before.beginAttempt(now, attempts, retry, cooldown);
        row.apply(after);
        if (after.closedAt() != null) {
            entity.apply(process.phoneWindowClosed(), UUID.randomUUID(), now);
        }
        phones.flush();
        return after.closedAt() == null ? Optional.of(mapper.toDomain(row)) : Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void phonePublished(UUID id, Instant now) {
        var row = phones.findById(id.toString()).orElseThrow();
        var before = mapper.toDomain(row);
        var after = before.received(now);
        if (after.equals(before)) return;
        row.apply(after);
        var entity = processes.findById(before.processId().toString()).orElseThrow();
        entity.apply(mapper.toDomain(entity).phonePublished(), UUID.randomUUID(), now);
        phones.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public ReservationProcess reconcile(UUID id, CentralAppointment evidence, Instant now) {
        var entity = processes.findById(id.toString()).orElseThrow();
        var before = mapper.toDomain(entity);
        var after = before.reconcile(evidence, now);
        if (!after.equals(before)) {
            entity.apply(after, UUID.randomUUID(), now);
            if (after.phase().isFinal()) closeActive(entity.getId(), now, after.phase().name());
            processes.flush();
        }
        return mapper.toDomain(entity);
    }

    private void closeActive(String processId, Instant now, String reason) {
        phones.active(processId)
                .ifPresent(row -> row.apply(mapper.toDomain(row).finish(now, reason)));
        phones.flush();
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
            if (after.phase().isFinal()
                    || after.phase() == ReservationPhase.RECONCILIATION_REQUIRED
                    || action.type() == ReservationActionType.PHONE_REJECTED
                    || action.type() == ReservationActionType.INVALID) {
                closeActive(entity.getId(), clock.instant(), action.type().name());
            }
            processes.flush();
        }
        receipt.classify(after.equals(before) ? "IGNORED" : "APPLIED", null, entity.getId());
    }
}

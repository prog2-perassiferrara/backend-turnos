package com.prog2.appointment.reservation.infrastructure.persistence.service;

import lombok.RequiredArgsConstructor;
import com.prog2.appointment.reservation.domain.model.OwnReservationPage;

import java.time.Clock;
import com.prog2.appointment.reservation.domain.model.CancellationEvidence;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
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
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import com.prog2.appointment.reservation.infrastructure.persistence.mapper.ReservationPersistenceMapper;
import com.prog2.appointment.reservation.infrastructure.persistence.repository.JpaProcessedReservationEventRepository;
import com.prog2.appointment.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;

/**
 * Transacciones cortas detrás del proxy; el retorno al adaptador incluye confirmación del commit.
 */
@Component
@RequiredArgsConstructor
public class TransactionalReservationStore {
    private static final System.Logger LOG = System.getLogger(TransactionalReservationStore.class.getName());
    private final JpaReservationProcessRepository processes;
    private final JpaProcessedReservationEventRepository events;
    private final ReservationPersistenceMapper mapper;
    private final Clock clock;
    private final JpaPhoneSubmissionRepository phones;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 10)
    public List<UUID> dueRecovery(Instant now, Duration initialDelay, int limit) {
        return processes.dueRecovery(now, now.minus(initialDelay), PageRequest.of(0, limit))
                .stream().map(UUID::fromString).toList();
    }

    /** Solo programa una revisión; cualquier efecto posterior necesita su permiso de negocio. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<ReservationProcess> beginRecovery(UUID id, Instant now, Duration retry, Duration initialDelay) {
        var entity = processes.findById(id.toString()).orElseThrow();
        if (!entity.recoveryDue(now, initialDelay)) return Optional.empty();
        var next = now.plus(retry);
        // Una revisión previa al vencimiento no posterga su comprobación hasta otro intervalo.
        if (entity.getExpiresAt() != null && now.isBefore(entity.getExpiresAt())
                && entity.getExpiresAt().isBefore(next)) next = entity.getExpiresAt();
        entity.scheduleRecovery(next);
        processes.flush();
        return Optional.of(mapper.toDomain(entity));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void deferRecovery(UUID id, Instant nextAttempt) {
        processes.findById(id.toString()).ifPresent(value -> value.deferRecovery(nextAttempt));
        processes.flush();
    }

    /** Una sola instantánea SQL para que un cierre concurrente no duplique el pendiente. */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,
            timeout = 10)
    public OwnReservationPage readOwn(long ownerId, int page, int size) {
        OwnReservationPage.validate(page, size);
        var pending = processes.findPending(ownerId).map(mapper::toDomain).orElse(null);
        var phone =
                pending == null
                        ? null
                        : phones.latest(pending.id().toString()).map(mapper::toDomain).orElse(null);
        var history = processes.history(ownerId, PageRequest.of(page, size));
        return new OwnReservationPage(
                pending,
                phone,
                history.getContent().stream().map(mapper::toDomain).toList(),
                history.getTotalElements(),
                page,
                size);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public ReservationProcess requestCancellation(long ownerId, UUID id, Instant now) {
        var entity = processes.findById(id.toString())
                .filter(value -> value.getOwnerId() == ownerId)
                .orElseThrow(() -> new ReservationCancellationException(
                        ReservationFailure.RESERVATION_PROCESS_NOT_FOUND));
        var before = mapper.toDomain(entity);
        var after = before.requestCancellation();
        if (after.equals(before)) return before;
        entity.apply(after, UUID.randomUUID(), now);
        entity.scheduleRecovery(now);
        processes.flush();
        return mapper.toDomain(entity);
    }

    /** Una sola fecha compartida limita la reclamación; no representa un ciclo histórico. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<ReservationProcess> beginCancellationAttempt(UUID id, Instant now,
            Duration budget) {
        var entity = processes.findById(id.toString()).orElseThrow();
        var process = mapper.toDomain(entity);
        if (process.cancellation() == null || !entity.recoveryDue(now, Duration.ZERO)) return Optional.empty();
        entity.apply(process, UUID.randomUUID(), now);
        entity.scheduleRecovery(now.plus(budget));
        processes.flush();
        return Optional.of(mapper.toDomain(entity));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void finishCancellationAttempt(UUID id, UUID writeId, boolean requireRetry,
            String failure, Instant now, Instant nextReview) {
        var entity = processes.findById(id.toString()).orElseThrow();
        if (!entity.getWriteId().equals(writeId.toString()) || entity.getCancellationStatus() == null) return;
        if (requireRetry) entity.apply(mapper.toDomain(entity).requireCancellationRetry(failure), writeId, now);
        entity.scheduleRecovery(nextReview);
        processes.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public ReservationProcess confirmCancellation(UUID id, CancellationEvidence evidence, Instant now) {
        var entity = processes.findById(id.toString()).orElseThrow();
        var before = mapper.toDomain(entity);
        var after = before.cancel(evidence, now);
        if (!after.equals(before)) {
            entity.apply(after, UUID.randomUUID(), now);
            closeActive(entity.getId(), now, "PROCESS_CANCELLED");
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

    /** El estado ajeno CONFIRMATION_PENDING nunca concede permiso de transporte a este reclamante. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<ReservationProcess> beginInitialConfirmation(UUID id, Instant now) {
        var entity = processes.findById(id.toString()).orElseThrow();
        var before = mapper.toDomain(entity);
        if (before.phase() != ReservationPhase.HELD || !now.isBefore(before.hold().expiresAt())) return Optional.empty();
        entity.apply(before.beginConfirmation(), UUID.randomUUID(), now);
        processes.flush();
        return Optional.of(mapper.toDomain(entity));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public ReservationProcess update(
            UUID id, UnaryOperator<ReservationProcess> change) {
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
        entity.apply(after, UUID.randomUUID(), clock.instant());
        processes.flush();
        return mapper.toDomain(entity);
    }

    /**
     * Deduplica por eventId y modifica el proceso en la misma transacción.
     * Un proceso desconocido se ignora definitivamente: la confirmación central solo se envía
     * después de persistir sus IDs. No se importan reservas ni se conservan mensajes para replay.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void accept(ReservationAction action) {
        String eventId = action.correlation().eventId();
        if (events.existsById(eventId)) return;
        var entity = processes.findByCentralProcessId(action.correlation().centralProcessId());
        if (entity.isPresent()) {
            apply(entity.get(), action);
        } else {
            LOG.log(System.Logger.Level.WARNING, "Reservation action for unknown process ignored");
        }
        events.markProcessed(eventId);
    }

    private void apply(ReservationProcessEntity entity, ReservationAction action) {
        if (action.type() == ReservationActionType.OTHER) return;
        var before = mapper.toDomain(entity);
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
    }
}

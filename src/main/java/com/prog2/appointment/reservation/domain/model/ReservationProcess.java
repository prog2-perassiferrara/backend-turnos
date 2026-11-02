package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
import java.time.Duration;
import com.prog2.appointment.reservation.domain.enums.CancellationKind;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
import java.util.Objects;
import java.util.UUID;
import com.prog2.appointment.reservation.domain.enums.CentralAppointmentState;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;

/**
 * Agregado inmutable del proceso. Las transiciones conservan evidencia y nunca renuevan el hold. La
 * versión y writeId identifican la escritura local; no son IDs de cátedra.
 */
public record ReservationProcess(
        UUID id,
        ReservationPatient patient,
        ReservationSelection selection,
        ReservationPhase phase,
        HoldDetails hold,
        String requestEventId,
        Long reservationId,
        String reasonCode,
        long version,
        UUID writeId,
        Instant closedAt,
        boolean abandonmentPending,
        ProfessionalName professionalName,
        CancellationProgress cancellation) {
    public ReservationProcess {
        Objects.requireNonNull(id);
        Objects.requireNonNull(patient);
        Objects.requireNonNull(selection);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(writeId);
        if (version < 0 || phase.isFinal() != (closedAt != null)) {
            throw new IllegalArgumentException("Inconsistent reservation process");
        }
    }

    public static ReservationProcess begin(
            StartReservationRequest request, ReservationPatient patient) {
        return new ReservationProcess(
                request.requestId(),
                patient,
                request.selection(),
                ReservationPhase.CREATING_HOLD,
                null,
                null,
                null,
                null,
                0,
                UUID.randomUUID(),
                null,
                false,
                null,
                null);
    }

    public long ownerId() {
        return patient.ownerId();
    }

    public boolean isPending() {
        return closedAt == null || abandonmentPending;
    }

    public boolean matches(long ownerId, StartReservationRequest request) {
        return ownerId() == ownerId
                && id.equals(request.requestId())
                && selection.equals(request.selection());
    }

    public ReservationProcess acceptHold(HoldDetails value) {
        if (phase != ReservationPhase.CREATING_HOLD && phase != ReservationPhase.HOLD_UNCERTAIN) {
            return this;
        }
        return change(ReservationPhase.HELD, value, requestEventId, reservationId, null, null);
    }

    /** Nueva intención después de observar la posición libre; conserva la identidad lógica. */
    public ReservationProcess prepareHoldRetry() {
        if (phase != ReservationPhase.HOLD_UNCERTAIN || hold != null) {
            return this;
        }
        return change(ReservationPhase.CREATING_HOLD, null, null, null, null, null);
    }

    /**
     * Cierra solo el intento no identificable; no acredita cancelar ni vencer el hold central.
     *
     * @param reason motivo seguro de salida
     * @param now instante del cierre local
     * @return estado final local sin liberar un hold conocido
     */
    public ReservationProcess abandonHoldAttempt(String reason, Instant now) {
        if (hold != null
                || phase != ReservationPhase.HOLD_UNCERTAIN
                        && phase != ReservationPhase.CREATING_HOLD) {
            return this;
        }
        return change(ReservationPhase.HOLD_ATTEMPT_ABANDONED, null, null, null, reason, now);
    }

    public ReservationProcess beginConfirmation() {
        if (phase != ReservationPhase.HELD || abandonmentPending) {
            return this;
        }
        return change(ReservationPhase.CONFIRMING, hold, requestEventId, reservationId, null, null);
    }

    /** Una solicitud Kafka ya recibida prevalece sobre la respuesta REST más lenta. */
    public ReservationProcess acceptInitialConfirmation(HoldDetails value) {
        if (!Objects.equals(hold, value)) {
            return phase.isFinal()
                    ? this
                    : change(
                            ReservationPhase.RECONCILIATION_REQUIRED,
                            hold,
                            requestEventId,
                            reservationId,
                            ReservationFailure.CORRELATION_MISMATCH.name(),
                            null);
        }
        if (phase != ReservationPhase.CONFIRMING
                && phase != ReservationPhase.CONFIRMATION_UNCERTAIN) {
            return this;
        }
        return change(
                ReservationPhase.WAITING_FOR_REQUEST,
                hold,
                requestEventId,
                reservationId,
                null,
                null);
    }

    /** Clasifica efectos potenciales sin convertir un rechazo de confirmación en cancelación. */
    public ReservationProcess recordFailure(ReservationGatewayException failure, Instant now) {
        if (phase.isFinal()) {
            return this;
        }
        if (phase == ReservationPhase.PHONE_QUEUED
                || phase == ReservationPhase.WAITING_FOR_RESULT) {
            return this;
        }
        if (failure.failure() == ReservationFailure.HOLD_EXPIRED) {
            return change(
                    ReservationPhase.EXPIRED,
                    hold,
                    requestEventId,
                    reservationId,
                    failure.failure().name(),
                    now);
        }
        if (phase == ReservationPhase.WAITING_FOR_PHONE
                || phase == ReservationPhase.WAITING_FOR_REQUEST) {
            return this;
        }
        if (hold == null) {
            var state =
                    !failure.attempted() || failure.definitive()
                            ? ReservationPhase.REJECTED
                            : ReservationPhase.HOLD_UNCERTAIN;
            return change(
                    state,
                    null,
                    requestEventId,
                    reservationId,
                    failure.failure().name(),
                    state.isFinal() ? now : null);
        }
        var state =
                failure.definitive()
                        ? ReservationPhase.RECONCILIATION_REQUIRED
                        : ReservationPhase.CONFIRMATION_UNCERTAIN;
        return change(state, hold, requestEventId, reservationId, failure.failure().name(), null);
    }

    /**
     * @return nuevo progreso únicamente si el hecho pertenece al proceso y no contradice un cierre
     */
    public ReservationProcess apply(ReservationAction action, Instant now) {
        var correlation = action.correlation();
        if (hold == null
                || !hold.centralProcessId().equals(correlation.centralProcessId())
                || !hold.holdId().equals(correlation.holdId())) {
            return this;
        }
        if (phase.isFinal()
                && !(phase == ReservationPhase.CONFIRMED
                        && action.type()
                                == com.prog2.appointment.reservation.domain.enums
                                        .ReservationActionType.CANCELLED)) {
            return this;
        }
        if (action.selection() != null && !selection.equals(action.selection())) {
            return inconsistent();
        }
        if (action.type()
                        != com.prog2.appointment.reservation.domain.enums.ReservationActionType
                                .PHONE_REQUESTED
                && requestEventId != null
                && !requestEventId.equals(correlation.requestEventId())) {
            return inconsistent();
        }
        return switch (action.type()) {
            case PHONE_REQUESTED -> phoneRequested(action);
            case PHONE_REJECTED -> phoneRejected(action);
            case EXPIRED ->
                    change(
                            ReservationPhase.EXPIRED,
                            hold,
                            requestEventId,
                            reservationId,
                            "HOLD_EXPIRED",
                            now);
            case INVALID -> inconsistent();
            case CONFIRMED ->
                    change(
                            ReservationPhase.CONFIRMED,
                            hold,
                            correlation.requestEventId(),
                            action.reservationId(),
                            null,
                            now);
            case CANCELLED ->
                    change(
                            ReservationPhase.CANCELLED,
                            hold,
                            correlation.requestEventId(),
                            action.reservationId(),
                            null,
                            now);
            case OTHER -> this;
        };
    }

    private ReservationProcess phoneRequested(ReservationAction action) {
        if (!hold.expiresAtMatches(action.expiresAt())
                || requestEventId != null
                        && !requestEventId.equals(action.correlation().eventId())) {
            return inconsistent();
        }
        if (phase == ReservationPhase.WAITING_FOR_PHONE
                || phase == ReservationPhase.PHONE_QUEUED
                || phase == ReservationPhase.WAITING_FOR_RESULT) {
            return this;
        }
        return change(
                ReservationPhase.WAITING_FOR_PHONE,
                hold,
                action.correlation().eventId(),
                reservationId,
                null,
                null);
    }

    /**
     * @return si el propietario puede responder a la solicitud conocida ahora
     */
    public boolean canSubmitPhone(Instant now) {
        return phase == ReservationPhase.WAITING_FOR_PHONE
                && hold != null
                && requestEventId != null
                && !abandonmentPending
                && now.isBefore(hold.expiresAt());
    }

    /** Conserva la intención telefónica antes de cualquier publicación. */
    public ReservationProcess queuePhone() {
        return change(
                ReservationPhase.PHONE_QUEUED, hold, requestEventId, reservationId, null, null);
    }

    /** El ACK solo permite avanzar la respuesta todavía activa. */
    public ReservationProcess phonePublished() {
        return phase == ReservationPhase.PHONE_QUEUED && !abandonmentPending
                ? change(
                        ReservationPhase.WAITING_FOR_RESULT,
                        hold,
                        requestEventId,
                        reservationId,
                        null,
                        null)
                : this;
    }

    /** Detiene el envío sin presentar un plazo local como cierre central. */
    public ReservationProcess phoneWindowClosed() {
        return phase == ReservationPhase.PHONE_QUEUED
                        || phase == ReservationPhase.WAITING_FOR_RESULT
                ? change(
                        ReservationPhase.RECONCILIATION_REQUIRED,
                        hold,
                        requestEventId,
                        reservationId,
                        "PHONE_WINDOW_CLOSED",
                        null)
                : this;
    }

    private ReservationProcess phoneRejected(ReservationAction action) {
        if (requestEventId == null || !hold.expiresAtMatches(action.expiresAt()))
            return inconsistent();
        return change(
                ReservationPhase.WAITING_FOR_PHONE,
                hold,
                requestEventId,
                reservationId,
                "PHONE_REJECTED",
                null);
    }

    /** Aplica evidencia REST sobre la versión actual sin fabricar eventos ni reabrir cierres. */
    public ReservationProcess reconcile(CentralAppointment evidence, Instant now) {
        if (!evidence.belongsTo(this)) return this;
        var result = reconcileState(evidence, now);
        return result.withProfessionalName(evidence.professionalName());
    }

    private ReservationProcess withProfessionalName(ProfessionalName name) {
        if (professionalName != null || name == null) return this;
        return new ReservationProcess(
                id,
                patient,
                selection,
                phase,
                hold,
                requestEventId,
                reservationId,
                reasonCode,
                version,
                writeId,
                closedAt,
                abandonmentPending,
                name,
                cancellation);
    }

    private ReservationProcess reconcileState(CentralAppointment evidence, Instant now) {
        if (phase.isFinal()
                && !(phase == ReservationPhase.CONFIRMED
                        && evidence.state() == CentralAppointmentState.CANCELLED)) return this;
        return switch (evidence.state()) {
            case PHONE_PENDING ->
                    phase == ReservationPhase.CONFIRMATION_UNCERTAIN
                                    || phase == ReservationPhase.CONFIRMING
                            ? change(
                                    ReservationPhase.WAITING_FOR_REQUEST,
                                    hold,
                                    requestEventId,
                                    reservationId,
                                    null,
                                    null)
                            : this;
            case CONFIRMED ->
                    change(
                            ReservationPhase.CONFIRMED,
                            hold,
                            requestEventId,
                            evidence.reservationId(),
                            null,
                            now);
            case CANCELLED ->
                    change(
                            ReservationPhase.CANCELLED,
                            hold,
                            requestEventId,
                            evidence.reservationId(),
                            null,
                            now);
            case FAILED ->
                    change(
                            ReservationPhase.FAILED,
                            hold,
                            requestEventId,
                            evidence.reservationId(),
                            "CENTRAL_PROCESS_FAILED",
                            now);
        };
    }

    private ReservationProcess inconsistent() {
        return phase.isFinal()
                ? this
                : change(
                        ReservationPhase.RECONCILIATION_REQUIRED,
                        hold,
                        requestEventId,
                        reservationId,
                        ReservationFailure.CORRELATION_MISMATCH.name(),
                        null);
    }

    private ReservationProcess change(
            ReservationPhase state,
            HoldDetails value,
            String request,
            Long reservation,
            String reason,
            Instant closed) {
        boolean pendingAbandonment =
                abandonmentPending
                        && state != ReservationPhase.CANCELLED
                        && state != ReservationPhase.EXPIRED
                        && state != ReservationPhase.FAILED
                        && state != ReservationPhase.HOLD_ATTEMPT_ABANDONED;
        return new ReservationProcess(
                id,
                patient,
                selection,
                state,
                value,
                request,
                reservation,
                reason,
                version,
                writeId,
                closed,
                pendingAbandonment,
                professionalName,
                progressAfter(state, closed));
    }

    /**
     * Acepta una intención propia sin ejecutar REST. La aceptación y el cierre del outbox
     * se conservan juntos por el repositorio.
     * @param kind acción explícita
     * @param retryVersion versión observada para reintento; null significa solicitud/replay
     * @param now instante de aceptación
     * @return proceso actualizado o el resultado ya conocido
     */
    public ReservationProcess requestCancellation(CancellationKind kind, Integer retryVersion, Instant now) {
        Objects.requireNonNull(kind);
        if (cancellation != null) {
            if (kind != cancellation.kind()) {
                throw new ReservationCancellationException(ReservationFailure.CANCELLATION_KIND_CONFLICT);
            }
            return retryVersion == null ? this
                    : withCancellation(cancellation.retry(retryVersion, now), abandonmentPending);
        }
        if (retryVersion != null) {
            throw new ReservationCancellationException(ReservationFailure.CANCELLATION_RETRY_NOT_READY);
        }
        if (phase == ReservationPhase.CANCELLED
                || kind == CancellationKind.ABANDON && phase.isFinal() && phase != ReservationPhase.CONFIRMED) {
            return this;
        }
        if (hold == null || phase.isFinal() && phase != ReservationPhase.CONFIRMED
                || kind == CancellationKind.CANCEL && phase != ReservationPhase.CONFIRMED) {
            throw new ReservationCancellationException(ReservationFailure.CANCELLATION_NOT_ALLOWED);
        }
        return withCancellation(initialCancellation(kind, now),
                kind == CancellationKind.ABANDON && !phase.isFinal());
    }

    private CancellationProgress initialCancellation(CancellationKind kind, Instant now) {
        var progress = CancellationProgress.create(kind, now);
        return kind == CancellationKind.ABANDON && phase != ReservationPhase.CONFIRMED
                ? progress.awaitExpiry(hold.expiresAt()) : progress;
    }

    /**
     * @param now instante de evaluación
     * @return si venció una espera estable de un abandono; el llamador debe comprobar además
     *     que nunca se registró una respuesta telefónica antes de aplicar el cierre
     */
    public boolean canExpireAbandonment(Instant now) {
        return abandonmentPending && hold != null && !now.isBefore(hold.expiresAt())
                && (phase == ReservationPhase.HELD || phase == ReservationPhase.WAITING_FOR_REQUEST
                        || phase == ReservationPhase.WAITING_FOR_PHONE);
    }

    /**
     * Cierre local por el plazo central de una espera estable sin teléfono registrado.
     * No acredita cancelar ni garantiza que el horario siga disponible.
     * @param now instante de cierre
     * @return proceso vencido, o el estado actual si una transición concurrente impide el cierre
     */
    public ReservationProcess expireAbandonment(Instant now) {
        return canExpireAbandonment(now)
                ? change(ReservationPhase.EXPIRED, hold, requestEventId, reservationId,
                        ReservationFailure.HOLD_EXPIRED.name(), now) : this;
    }

    /** Reclamación pura: el adapter solo habilita HTTP después del commit verificable. */
    public ReservationProcess claimCancellation(Instant now, Duration budget) {
        return cancellation == null ? this
                : withCancellation(cancellation.claim(now, budget), abandonmentPending);
    }

    /** Un resultado antiguo puede acreditar cancelación, pero no reabrir otro cierre. */
    public ReservationProcess cancel(CancellationEvidence evidence, Instant now) {
        if (!evidence.belongsTo(this)
                || phase.isFinal() && phase != ReservationPhase.CONFIRMED) return this;
        return change(ReservationPhase.CANCELLED, hold, requestEventId,
                evidence.reservationId(), null, now);
    }

    /** Un fallo solo afecta su intento; una confirmación concurrente preserva el abandono. */
    public ReservationProcess cancellationFailed(ReservationProcess claimed,
            ReservationGatewayException failure, Instant now, Duration retry, Duration cooldown) {
        if (cancellation == null || !cancellation.matches(claimed.cancellation())) return this;
        boolean confirmedSinceClaim = abandonmentPending && phase == ReservationPhase.CONFIRMED
                && claimed.phase() != ReservationPhase.CONFIRMED;
        return withCancellation(cancellation.failed(failure.failure().name(),
                failure.definitive() && !confirmedSinceClaim, now, retry, cooldown), abandonmentPending);
    }

    private CancellationProgress progressAfter(ReservationPhase next, Instant at) {
        if (cancellation == null) return null;
        if (next == ReservationPhase.CANCELLED || next == ReservationPhase.EXPIRED
                || next == ReservationPhase.FAILED) return cancellation.resolve();
        return next == ReservationPhase.CONFIRMED && phase != ReservationPhase.CONFIRMED
                ? cancellation.confirmed(at) : cancellation;
    }

    private ReservationProcess withCancellation(CancellationProgress progress, boolean abandoning) {
        if (Objects.equals(progress, cancellation) && abandoning == abandonmentPending) return this;
        return new ReservationProcess(id, patient, selection, phase, hold, requestEventId,
                reservationId, reasonCode, version, writeId, closedAt, abandoning, professionalName, progress);
    }

    @Override
    public String toString() {
        return "ReservationProcess[id=" + id + ", phase=" + phase + "]";
    }
}

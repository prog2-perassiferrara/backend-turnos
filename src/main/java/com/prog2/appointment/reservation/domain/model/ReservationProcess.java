package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
import com.prog2.appointment.reservation.domain.exception.ReservationCancellationException;
import java.util.Objects;
import com.prog2.appointment.reservation.domain.enums.CancellationState;
import java.util.UUID;
import com.prog2.appointment.reservation.domain.enums.CentralAppointmentState;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;

/**
 * Agregado inmutable del proceso. Las transiciones conservan evidencia y nunca renuevan el hold. El
 * writeId acredita una reclamación local cuando un efecto lo requiere; no es un ID de cátedra.
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
        UUID writeId,
        Instant closedAt,
        ProfessionalName professionalName,
        CancellationState cancellation) {
    public ReservationProcess {
        Objects.requireNonNull(id);
        Objects.requireNonNull(patient);
        Objects.requireNonNull(selection);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(writeId);
        if (phase.isFinal() != (closedAt != null)) {
            throw new IllegalArgumentException("Inconsistent reservation process");
        }
    }

    public static ReservationProcess begin(
            StartReservationRequest request, ReservationPatient patient) {
        return new ReservationProcess(
                request.requestId(),
                patient,
                request.selection(),
                ReservationPhase.HOLD_PENDING,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null);
    }

    public long ownerId() {
        return patient.ownerId();
    }

    public boolean isPending() {
        return closedAt == null;
    }

    public boolean matches(long ownerId, StartReservationRequest request) {
        return ownerId() == ownerId
                && id.equals(request.requestId())
                && selection.equals(request.selection());
    }

    public ReservationProcess acceptHold(HoldDetails value) {
        if (phase != ReservationPhase.HOLD_PENDING) {
            return this;
        }
        return change(ReservationPhase.HELD, value, requestEventId, reservationId, null, null);
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
                || phase != ReservationPhase.HOLD_PENDING) {
            return this;
        }
        return change(ReservationPhase.FAILED, null, null, null, reason, now);
    }

    public ReservationProcess beginConfirmation() {
        if (phase != ReservationPhase.HELD) {
            return this;
        }
        return change(ReservationPhase.CONFIRMATION_PENDING, hold, requestEventId, reservationId, null, null);
    }

    /**
     * @param now instante de evaluación del plazo central
     * @return EXPIRED solo si el hold conocido venció sin comenzar la confirmación
     */
    public ReservationProcess expireUnconfirmedHold(Instant now) {
        if (phase != ReservationPhase.HELD || hold == null || now.isBefore(hold.expiresAt())) return this;
        return expireKnownHold(now);
    }

    /**
     * Solo puede usarlo quien conserva su reclamación y todavía no invocó el transporte.
     * Tras un reinicio CONFIRMATION_PENDING no demuestra que el POST no se haya enviado.
     * @param claimed permiso propio obtenido en esta ejecución
     * @param now instante justo antes del transporte
     * @return vencimiento seguro o estado actual si otra transición avanzó
     */
    public ReservationProcess expireUnsentConfirmation(ReservationProcess claimed, Instant now) {
        if (phase != ReservationPhase.CONFIRMATION_PENDING || !writeId.equals(claimed.writeId())
                || hold == null || now.isBefore(hold.expiresAt())) return this;
        return expireKnownHold(now);
    }

    private ReservationProcess expireKnownHold(Instant now) {
        return change(ReservationPhase.EXPIRED, hold, requestEventId, reservationId,
                "UNCONFIRMED_HOLD_EXPIRED", now);
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
        if (phase != ReservationPhase.CONFIRMATION_PENDING) {
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
                            ? ReservationPhase.FAILED
                            : ReservationPhase.HOLD_PENDING;
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
                        : ReservationPhase.CONFIRMATION_PENDING;
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
                && now.isBefore(hold.expiresAt());
    }

    /** Conserva la intención telefónica antes de cualquier publicación. */
    public ReservationProcess queuePhone() {
        return change(
                ReservationPhase.PHONE_QUEUED, hold, requestEventId, reservationId, null, null);
    }

    /** El ACK solo permite avanzar la respuesta todavía activa. */
    public ReservationProcess phonePublished() {
        return phase == ReservationPhase.PHONE_QUEUED
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
                writeId,
                closedAt,
                name,
                cancellation);
    }

    private ReservationProcess reconcileState(CentralAppointment evidence, Instant now) {
        if (phase.isFinal()
                && !(phase == ReservationPhase.CONFIRMED
                        && evidence.state() == CentralAppointmentState.CANCELLED)) return this;
        return switch (evidence.state()) {
            case PHONE_PENDING ->
                    phase == ReservationPhase.CONFIRMATION_PENDING
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
        return new ReservationProcess(
                id,
                patient,
                selection,
                state,
                value,
                request,
                reservation,
                reason,
                writeId,
                closed,
                professionalName,
                cancellationAfter(state));
    }

    /** @return intención propia de cancelar o retry, sin alterar la fase acreditada por cátedra */
    public ReservationProcess requestCancellation() {
        if (phase == ReservationPhase.CANCELLED) return this;
        if (cancellation == CancellationState.PENDING) return this;
        if (hold == null || phase != ReservationPhase.CONFIRMED) {
            throw new ReservationCancellationException(ReservationFailure.CANCELLATION_NOT_ALLOWED);
        }
        return withCancellation(CancellationState.PENDING, null);
    }

    /** Un resultado antiguo puede acreditar cancelación, pero no reabrir otro cierre. */
    public ReservationProcess cancel(CancellationEvidence evidence, Instant now) {
        if (!evidence.belongsTo(this)
                || phase.isFinal() && phase != ReservationPhase.CONFIRMED) return this;
        return change(ReservationPhase.CANCELLED, hold, requestEventId,
                evidence.reservationId(), null, now);
    }

    /** Detiene nuevos POST automáticos sin presentar el fallo como cancelación central. */
    public ReservationProcess requireCancellationRetry(String code) {
        return cancellation != CancellationState.PENDING ? this
                : withCancellation(CancellationState.RETRY_REQUIRED, code);
    }

    private CancellationState cancellationAfter(ReservationPhase next) {
        return next == ReservationPhase.CANCELLED || next == ReservationPhase.EXPIRED
                || next == ReservationPhase.FAILED ? null : cancellation;
    }

    private ReservationProcess withCancellation(CancellationState state, String code) {
        if (state == cancellation && Objects.equals(code, reasonCode)) return this;
        return new ReservationProcess(id, patient, selection, phase, hold, requestEventId,
                reservationId, code, writeId, closedAt, professionalName, state);
    }

    @Override
    public String toString() {
        return "ReservationProcess[id=" + id + ", phase=" + phase + "]";
    }
}

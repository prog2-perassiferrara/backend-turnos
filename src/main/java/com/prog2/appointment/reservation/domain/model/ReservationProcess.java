package com.prog2.appointment.reservation.domain.model;

import java.time.Instant;
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
        boolean abandonmentPending) {
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
                false);
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
        if (phase != ReservationPhase.HELD) {
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
                || phase == ReservationPhase.WAITING_FOR_REQUEST
                || phase == ReservationPhase.PHONE_QUEUED
                || phase == ReservationPhase.WAITING_FOR_RESULT) {
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
        if (!hold.expiresAt().equals(action.expiresAt())
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
        if (requestEventId == null || !hold.expiresAt().equals(action.expiresAt()))
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
                pendingAbandonment);
    }

    @Override
    public String toString() {
        return "ReservationProcess[id=" + id + ", phase=" + phase + "]";
    }
}

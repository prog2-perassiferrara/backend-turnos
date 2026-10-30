package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationPatient;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.StartReservationRequest;

/** Reglas observables con evidencia conocida, sin transporte ni persistencia sustituidos. */
class ReservationProcessTests {
    static final Instant NOW = Instant.parse("2026-11-02T11:00:00Z");
    static final HoldDetails HOLD =
            new HoldDetails("hold-fictional", "process-fictional", NOW.plusSeconds(300));
    static final ReservationSelection SLOT =
            new ReservationSelection(15, LocalDate.of(2026, 11, 2), LocalTime.of(9, 0));

    static ReservationProcess creating() {
        return ReservationProcess.begin(
                new StartReservationRequest(UUID.randomUUID(), SLOT),
                new ReservationPatient(42, "Paciente", "Ficticio"));
    }

    static ReservationProcess confirming() {
        return creating().acceptHold(HOLD).beginConfirmation();
    }

    static ReservationAction action(ReservationActionType type, String eventId) {
        return new ReservationAction(
                type,
                new ReservationAction.Correlation(
                        eventId, HOLD.centralProcessId(), HOLD.holdId(), "request-1"),
                type == ReservationActionType.PHONE_REQUESTED ? HOLD.expiresAt() : null,
                type == ReservationActionType.CONFIRMED || type == ReservationActionType.CANCELLED
                        ? 8251L
                        : null,
                type == ReservationActionType.CONFIRMED || type == ReservationActionType.CANCELLED
                        ? SLOT
                        : null);
    }

    @Test
    void createsAnOwnedPendingIntentWithAStablePatientIdentifier() {
        var process = creating();
        assertThat(process.ownerId()).isEqualTo(42);
        assertThat(process.patient().externalPatientId()).isEqualTo("user-42");
        assertThat(process.phase()).isEqualTo(ReservationPhase.CREATING_HOLD);
        assertThat(process.isPending()).isTrue();
        assertThat(process.hold()).isNull();
        assertThat(process.toString()).doesNotContain("Paciente", "Ficticio");
    }

    @Test
    void normalizesMinuteAndSecondRepresentationsForTheSameAction() {
        var process = creating();
        var equivalent = new ReservationSelection(15, SLOT.date(), LocalTime.parse("09:00:00"));
        assertThat(process.matches(42, new StartReservationRequest(process.id(), equivalent)))
                .isTrue();
        assertThat(process.matches(43, new StartReservationRequest(process.id(), equivalent)))
                .isFalse();
    }

    @Test
    void anInitialRestAcceptanceIsNotAFinalReservation() {
        var process = confirming().acceptInitialConfirmation(HOLD);
        assertThat(process.phase()).isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        assertThat(process.closedAt()).isNull();
        assertThat(process.requestEventId()).isNull();
        assertThat(process.reservationId()).isNull();
    }

    @Test
    void aPhoneRequestBeforeTheRestResponseNeverRegresses() {
        var phone =
                confirming().apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW);
        assertThat(phone.phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(phone.requestEventId()).isEqualTo("request-1");
        assertThat(phone.acceptInitialConfirmation(HOLD)).isSameAs(phone);
        assertThat(phone.apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW))
                .isSameAs(phone);
    }

    @Test
    void aLostCreationResponseKeepsThePendingLimitWithoutInventingIds() {
        var process =
                creating()
                        .recordFailure(
                                new ReservationGatewayException(
                                        ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false),
                                NOW);
        assertThat(process.phase()).isEqualTo(ReservationPhase.HOLD_UNCERTAIN);
        assertThat(process.isPending()).isTrue();
        assertThat(process.hold()).isNull();
    }

    @Test
    void onlyAKnownRejectionBeforeAHoldCanReleaseTheLimit() {
        var rejected =
                creating()
                        .recordFailure(
                                new ReservationGatewayException(
                                        ReservationFailure.SLOT_NOT_AVAILABLE, true, true),
                                NOW);
        assertThat(rejected.phase()).isEqualTo(ReservationPhase.REJECTED);
        assertThat(rejected.isPending()).isFalse();
        var held =
                confirming()
                        .recordFailure(
                                new ReservationGatewayException(
                                        ReservationFailure.CONFIRMATION_REJECTED, true, true),
                                NOW);
        assertThat(held.phase()).isEqualTo(ReservationPhase.RECONCILIATION_REQUIRED);
        assertThat(held.hold()).isEqualTo(HOLD);
        assertThat(held.isPending()).isTrue();
    }

    @Test
    void aDefiniteExpirationWinsOverAnEarlierPhoneRequest() {
        var phone =
                confirming().apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW);
        var expired =
                phone.recordFailure(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_EXPIRED, true, true),
                        NOW);
        assertThat(expired.phase()).isEqualTo(ReservationPhase.EXPIRED);
        assertThat(expired.isPending()).isFalse();
        assertThat(expired.apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW))
                .isSameAs(expired);
    }

    @Test
    void anUnknownConfirmationCannotDowngradeAKnownPhoneRequest() {
        var phone =
                confirming().apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW);
        assertThat(
                        phone.recordFailure(
                                new ReservationGatewayException(
                                        ReservationFailure.CONFIRMATION_UNCERTAIN, true, false),
                                NOW))
                .isSameAs(phone);
    }

    @Test
    void foreignHoldCorrelationCannotChangeTheProcess() {
        var process = confirming();
        var foreign =
                new ReservationAction(
                        ReservationActionType.PHONE_REQUESTED,
                        new ReservationAction.Correlation(
                                "request-1", "another-process", "another-hold", null),
                        HOLD.expiresAt(),
                        null,
                        null);
        assertThat(process.apply(foreign, NOW)).isSameAs(process);
    }

    @Test
    void aDifferentRequestOrExpirationDoesNotRenewTheHold() {
        var process =
                confirming().apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW);
        var other = action(ReservationActionType.PHONE_REQUESTED, "request-2");
        var changed = process.apply(other, NOW);
        assertThat(changed.phase()).isEqualTo(ReservationPhase.RECONCILIATION_REQUIRED);
        assertThat(changed.hold().expiresAt()).isEqualTo(HOLD.expiresAt());
    }

    @Test
    void confirmedWithPendingAbandonmentRemainsExclusiveUntilCancellation() {
        var phone =
                confirming().apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW);
        var abandoning =
                new ReservationProcess(
                        phone.id(),
                        phone.patient(),
                        phone.selection(),
                        phone.phase(),
                        phone.hold(),
                        phone.requestEventId(),
                        null,
                        null,
                        phone.version(),
                        phone.writeId(),
                        null,
                        true);
        var confirmed =
                abandoning.apply(action(ReservationActionType.CONFIRMED, "confirmed-1"), NOW);
        assertThat(confirmed.phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(confirmed.isPending()).isTrue();
        var cancelled =
                confirmed.apply(
                        action(ReservationActionType.CANCELLED, "cancelled-1"), NOW.plusSeconds(1));
        assertThat(cancelled.phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(cancelled.isPending()).isFalse();
        assertThat(cancelled.apply(action(ReservationActionType.CONFIRMED, "confirmed-late"), NOW))
                .isSameAs(cancelled);
    }

    @ParameterizedTest
    @EnumSource(
            value = ReservationActionType.class,
            names = {"PHONE_REQUESTED", "EXPIRED", "INVALID"})
    void finalConfirmationDoesNotReopenForLateNonCancellationEvents(ReservationActionType type) {
        var process =
                confirming()
                        .apply(action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW)
                        .apply(action(ReservationActionType.CONFIRMED, "confirmed-1"), NOW);
        assertThat(
                        process.apply(
                                action(
                                        type,
                                        type == ReservationActionType.PHONE_REQUESTED
                                                ? "request-1"
                                                : "late"),
                                NOW))
                .isSameAs(process);
    }
}

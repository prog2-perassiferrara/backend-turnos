package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.SubmitReservationPhoneUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** Teléfono y recuperación con ejemplos contractuales y un reloj explícito. */
class SubmitReservationPhoneTests {
    static ReservationProcess waiting() {
        return ReservationProcessTests.confirming()
                .apply(
                        ReservationProcessTests.action(
                                ReservationActionType.PHONE_REQUESTED, "request-1"),
                        ReservationProcessTests.NOW);
    }

    static PhoneSubmission submission() {
        return PhoneSubmission.create(
                UUID.randomUUID(),
                waiting(),
                "fictional-group",
                "+54 (9261) 555-12.34",
                ReservationProcessTests.NOW);
    }

    @Test
    void onlyContractualSeparatorsAreRemovedAndLeadingZerosArePreserved() {
        assertThat(PhoneSubmission.normalize(" +54 (9261) 555-12.34 ")).isEqualTo("+5492615551234");
        assertThat(PhoneSubmission.normalize("0001234")).isEqualTo("0001234");
        assertThat(PhoneSubmission.normalize("+123456789012345")).isEqualTo("+123456789012345");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "123456",
                "1234567890123456",
                "++1234567",
                "123abc4567",
                "1234567\t",
                "１２３４５６７"
            })
    void incompatiblePhonesAreRejectedWithoutInventingAPrefix(String value) {
        assertThatThrownBy(() -> PhoneSubmission.normalize(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void threeAttemptsStartANewBoundedCycleWithoutChangingTheEvent() {
        var phone = submission();
        var original = phone.event();
        var first =
                phone.beginAttempt(
                        ReservationProcessTests.NOW,
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        assertThat(first.nextAttemptAt()).isEqualTo(ReservationProcessTests.NOW.plusSeconds(5));
        var second =
                first.beginAttempt(
                        ReservationProcessTests.NOW.plusSeconds(5),
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        var third =
                second.beginAttempt(
                        ReservationProcessTests.NOW.plusSeconds(10),
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        assertThat(third.nextAttemptAt()).isEqualTo(ReservationProcessTests.NOW.plusSeconds(40));
        assertThat(third.cycleAttempt()).isZero();
        assertThat(third.event()).isEqualTo(original);
        assertThat(third.id()).isEqualTo(phone.id());
    }

    @Test
    void deadlineStopsPublicationWithoutClaimingCentralExpiration() {
        var phone = submission();
        var expired =
                phone.beginAttempt(
                        phone.expiresAt(), 3, Duration.ofSeconds(5), Duration.ofSeconds(30));
        assertThat(expired.closedAt()).isEqualTo(phone.expiresAt());
        assertThat(expired.publishedAt()).isNull();
        assertThat(expired.nextAttemptAt()).isNull();
    }

    @Test
    void aLateAcknowledgementCannotReopenAClosedSubmission() {
        var phone = submission();
        var closed = phone.finish(ReservationProcessTests.NOW.plusSeconds(1), null);
        assertThat(closed.received(ReservationProcessTests.NOW.plusSeconds(2))).isSameAs(closed);
        assertThat(phone.toString()).doesNotContain("5492615551234");
        assertThat(phone.event().toString()).doesNotContain("5492615551234");
    }

    @Test
    void ownershipIsCheckedBeforeNormalizationOrReadingAnExistingPhoneId() {
        var repository = mock(ReservationProcessRepository.class);
        var process = waiting();
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        var useCase =
                new SubmitReservationPhoneUseCaseImpl(
                        repository,
                        "fictional-group",
                        Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC));
        assertThatThrownBy(
                        () ->
                                useCase.submit(
                                        process.ownerId() + 1,
                                        process.id(),
                                        new SubmitPhoneRequest(UUID.randomUUID(), "invalid")))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure -> {
                            assertThat(failure.failure())
                                    .isEqualTo(ReservationFailure.RESERVATION_PROCESS_NOT_FOUND);
                            assertThat(failure.processId()).isNull();
                        });
        verify(repository, never()).submitPhone(anyLong(), any(), any(), any(), any());
        verify(repository, never()).latestPhone(any());
    }

    @Test
    void normalizationPrecedesAtomicAcceptanceAndInvalidInputNeverWrites() {
        var repository = mock(ReservationProcessRepository.class);
        var process = waiting();
        var phone = submission();
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        var useCase =
                new SubmitReservationPhoneUseCaseImpl(
                        repository,
                        "fictional-group",
                        Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC));
        assertThatThrownBy(
                        () ->
                                useCase.submit(
                                        process.ownerId(),
                                        process.id(),
                                        new SubmitPhoneRequest(phone.id(), "123")))
                .isInstanceOf(ReservationOperationException.class);
        verify(repository, never()).submitPhone(anyLong(), any(), any(), any(), any());
        var normalized = new SubmitPhoneRequest(phone.id(), "+5492615551234");
        when(repository.submitPhone(
                        process.ownerId(),
                        process.id(),
                        normalized,
                        "fictional-group",
                        ReservationProcessTests.NOW))
                .thenReturn(
                        new ReservationProcessRepository.PhoneResult(
                                process.queuePhone(), phone, true));
        when(repository.latestPhone(process.id())).thenReturn(Optional.of(phone));
        var result =
                useCase.submit(
                        process.ownerId(),
                        process.id(),
                        new SubmitPhoneRequest(phone.id(), "+54 (9261) 555-12.34"));
        assertThat(result.replayed()).isFalse();
        assertThat(result.status().process().phase()).isEqualTo(ReservationPhase.PHONE_QUEUED);
        assertThat(result.status().canSubmitPhone()).isFalse();
    }

    @Test
    void acceptedPhoneProgressWinsOverALateInitialRestFailure() {
        var queued = waiting().queuePhone();
        var expiredFailure =
                new ReservationGatewayException(ReservationFailure.HOLD_EXPIRED, true, true);
        assertThat(queued.recordFailure(expiredFailure, ReservationProcessTests.NOW))
                .isEqualTo(queued);
        assertThat(queued.acceptInitialConfirmation(queued.hold())).isEqualTo(queued);
        assertThat(
                        queued.apply(
                                ReservationProcessTests.action(
                                        ReservationActionType.PHONE_REQUESTED, "request-1"),
                                ReservationProcessTests.NOW))
                .isEqualTo(queued);
    }

    @Test
    void rejectionPreservesTheRequestAndDeadlineButFinalResultsCannotBeReopened() {
        var queued = waiting().queuePhone();
        var rejected =
                new com.prog2.appointment.reservation.domain.model.ReservationAction(
                        ReservationActionType.PHONE_REJECTED,
                        ReservationProcessTests.action(
                                        ReservationActionType.CONFIRMED, "rejected-1")
                                .correlation(),
                        queued.hold().expiresAt(),
                        null,
                        null);
        var corrected = queued.apply(rejected, ReservationProcessTests.NOW);
        assertThat(corrected.phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(corrected.requestEventId()).isEqualTo("request-1");
        assertThat(corrected.hold()).isEqualTo(queued.hold());
        assertThat(corrected.canSubmitPhone(queued.hold().expiresAt().minusNanos(1))).isTrue();
        assertThat(corrected.canSubmitPhone(queued.hold().expiresAt())).isFalse();
        var confirmed =
                queued.apply(
                        ReservationProcessTests.action(
                                ReservationActionType.CONFIRMED, "confirmed-1"),
                        ReservationProcessTests.NOW);
        assertThat(confirmed.apply(rejected, ReservationProcessTests.NOW)).isEqualTo(confirmed);
        assertThat(confirmed.phonePublished()).isEqualTo(confirmed);
    }
}

package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.*;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.*;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Reglas y casos de uso reales; solo las fronteras externas se sustituyen por puertos controlados. */
class ReservationCancellationTests {
    private static final Instant NOW = ReservationProcessTests.NOW;
    private static final Duration RETRY = Duration.ofSeconds(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    private final StartReservationTests.Memory repository = new StartReservationTests.Memory();
    private final CentralAppointmentGateway central = mock(CentralAppointmentGateway.class);
    private final ReservationCancellationGateway gateway = mock(ReservationCancellationGateway.class);
    private final Clock clock = mock(Clock.class);
    private RecoverReservationCancellationsUseCaseImpl recovery;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(central.find(any())).thenReturn(Optional.empty());
        doAnswer(call -> evidence(call.getArgument(0))).when(gateway).cancel(any());
        recovery = new RecoverReservationCancellationsUseCaseImpl(repository, central, gateway,
                clock, Duration.ofSeconds(2), RETRY, COOLDOWN);
    }

    private static ReservationProcess confirmed() {
        return ReservationProcessTests.confirming().apply(
                ReservationProcessTests.action(ReservationActionType.CONFIRMED, "confirmed"), NOW);
    }

    private static CancellationEvidence evidence(ReservationProcess process) {
        return new CancellationEvidence(process.hold().centralProcessId(), process.reservationId(), NOW);
    }

    private ReservationProcess save(ReservationProcess process) {
        repository.values.put(process.id(), process);
        return process;
    }

    private static ReservationGatewayException uncertain() {
        return new ReservationGatewayException(ReservationFailure.CANCELLATION_UNCERTAIN, true, false);
    }

    @Test
    void confirmedReservationRemainsHistoricalWhileItsCancellationIsPending() {
        var process = confirmed().requestCancellation(CancellationKind.CANCEL, null, NOW);
        assertThat(process.phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(process.isPending()).isFalse();
        assertThat(process.cancellation().state()).isEqualTo(CancellationState.PENDING);
        assertThat(process.requestCancellation(CancellationKind.CANCEL, null, NOW.plusSeconds(100)))
                .isSameAs(process);
    }

    @Test
    void abandonmentPreservesExclusionAcrossConfirmationAndResolvesOnlyWithEvidence() {
        var process = ReservationProcessTests.confirming()
                .requestCancellation(CancellationKind.ABANDON, null, NOW);
        assertThat(process.beginConfirmation()).isSameAs(process);
        var confirmed = process.apply(ReservationProcessTests.action(ReservationActionType.CONFIRMED, "confirmed"), NOW);
        assertThat(confirmed.abandonmentPending()).isTrue();
        assertThat(confirmed.isPending()).isTrue();
        var cancelled = confirmed.cancel(evidence(confirmed), NOW.plusSeconds(1));
        assertThat(cancelled.phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(cancelled.isPending()).isFalse();
        assertThat(cancelled.cancellation().state()).isEqualTo(CancellationState.RESOLVED);
        assertThat(cancelled.apply(ReservationProcessTests.action(ReservationActionType.CONFIRMED, "old"), NOW))
                .isSameAs(cancelled);
    }

    @Test
    void abandoningAnAlreadyKnownConfirmationDoesNotOccupyCreationSlot() {
        var process = confirmed().requestCancellation(CancellationKind.ABANDON, null, NOW);
        assertThat(process.abandonmentPending()).isFalse();
        assertThat(process.isPending()).isFalse();
    }

    @Test
    void heldProcessCannotStartInitialConfirmationAfterAbandonment() {
        var process = ReservationProcessTests.creating().acceptHold(ReservationProcessTests.HOLD)
                .requestCancellation(CancellationKind.ABANDON, null, NOW);
        assertThat(process.beginConfirmation()).isSameAs(process);
    }

    @Test
    void phoneRequestAndAckCannotEnablePhoneAfterAbandonment() {
        var phone = ReservationProcessTests.confirming()
                .apply(ReservationProcessTests.action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW);
        var process = phone.queuePhone().requestCancellation(CancellationKind.ABANDON, null, NOW);
        assertThat(process.phonePublished()).isSameAs(process);
        assertThat(process.canSubmitPhone(NOW)).isFalse();
        assertThat(process.apply(ReservationProcessTests.action(ReservationActionType.PHONE_REQUESTED, "request-1"), NOW)
                .canSubmitPhone(NOW)).isFalse();
    }

    @Test
    void failuresAndExpiryResolveAbandonmentWithoutClaimingCancellation() {
        var process = ReservationProcessTests.confirming().requestCancellation(CancellationKind.ABANDON, null, NOW);
        var failed = process.reconcile(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251,
                CentralAppointmentState.FAILED, null), NOW);
        assertThat(failed.phase()).isEqualTo(ReservationPhase.FAILED);
        assertThat(failed.isPending()).isFalse();
        assertThat(failed.cancellation().state()).isEqualTo(CancellationState.RESOLVED);
        var expired = process.apply(ReservationProcessTests.action(ReservationActionType.EXPIRED, "expired"), NOW);
        assertThat(expired.phase()).isEqualTo(ReservationPhase.EXPIRED);
        assertThat(expired.cancellation().state()).isEqualTo(CancellationState.RESOLVED);
    }

    @Test
    void invalidOrForeignEvidenceDoesNotCloseTheProcess() {
        var process = confirmed().requestCancellation(CancellationKind.CANCEL, null, NOW);
        assertThat(process.cancel(new CancellationEvidence("other", 8251L, NOW), NOW)).isSameAs(process);
        assertThat(process.cancel(new CancellationEvidence(process.hold().centralProcessId(), 99L, NOW), NOW))
                .isSameAs(process);
        assertThatThrownBy(() -> new CancellationEvidence("", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationEvidence("process", 0L, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationEvidence("process", 8251L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cancellationEvidenceRequiresTheCentralReservationIdentifier() {
        assertThatThrownBy(() -> new CancellationEvidence("process", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noIdentifiersAndWrongModeDoNotCreateAnIntent() {
        assertThatThrownBy(() -> ReservationProcessTests.creating()
                .requestCancellation(CancellationKind.ABANDON, null, NOW))
                .isInstanceOf(ReservationCancellationException.class);
        assertThatThrownBy(() -> ReservationProcessTests.confirming()
                .requestCancellation(CancellationKind.CANCEL, null, NOW))
                .isInstanceOf(ReservationCancellationException.class);
        var process = confirmed().requestCancellation(CancellationKind.CANCEL, null, NOW);
        assertThatThrownBy(() -> process.requestCancellation(CancellationKind.ABANDON, null, NOW))
                .isInstanceOf(ReservationCancellationException.class);
        assertThatThrownBy(() -> confirmed().requestCancellation(CancellationKind.CANCEL, 0, NOW))
                .isInstanceOf(ReservationCancellationException.class);
    }

    @Test
    void alreadyCancelledAndExpiredDoNotReopenAnAttempt() {
        var process = confirmed();
        var cancelled = process.cancel(evidence(process), NOW);
        assertThat(cancelled.requestCancellation(CancellationKind.CANCEL, null, NOW)).isSameAs(cancelled);
        var expired = ReservationProcessTests.confirming()
                .apply(ReservationProcessTests.action(ReservationActionType.EXPIRED, "expired"), NOW);
        assertThat(expired.requestCancellation(CancellationKind.ABANDON, null, NOW)).isSameAs(expired);
        assertThatThrownBy(() -> expired.requestCancellation(CancellationKind.CANCEL, null, NOW))
                .isInstanceOf(ReservationCancellationException.class);
    }

    @Test
    void onlyTheCurrentAvailableRetryVersionOpensOneNewCycle() {
        var p = CancellationProgress.create(CancellationKind.CANCEL, NOW);
        assertThat(p.canRetry(NOW)).isFalse();
        assertThatThrownBy(() -> p.retry(0, NOW)).isInstanceOf(ReservationCancellationException.class);
        var exhausted = p.claim(NOW, RETRY).claim(NOW.plusSeconds(5), RETRY)
                .claim(NOW.plusSeconds(10), COOLDOWN);
        assertThat(exhausted.isDue(NOW.plusSeconds(100))).isFalse();
        assertThat(exhausted.canRetry(NOW.plusSeconds(39))).isFalse();
        var retry = exhausted.retry(0, NOW.plusSeconds(40));
        assertThat(retry.retryVersion()).isEqualTo(1);
        assertThat(retry.attempts()).isZero();
        assertThat(retry.retry(0, NOW.plusSeconds(100))).isSameAs(retry);
        assertThatThrownBy(() -> retry.retry(2, NOW.plusSeconds(100))).isInstanceOf(ReservationCancellationException.class);
        assertThatThrownBy(() -> retry.retry(-1, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressRejectsImpossiblePersistentStates() {
        assertThatThrownBy(() -> new CancellationProgress(CancellationKind.CANCEL, CancellationState.PENDING,
                NOW, -1, 0, NOW, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationProgress(CancellationKind.CANCEL, CancellationState.PENDING,
                NOW, 0, 4, NOW, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationProgress(CancellationKind.CANCEL, CancellationState.RESOLVED,
                NOW, 0, 0, NOW, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationProgress(CancellationKind.CANCEL, CancellationState.PENDING,
                NOW, 0, 0, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successfulUseCasePersistsThenCancelsAndReplayHasNoEffects() {
        var process = save(confirmed());
        var useCase = new CancelReservationUseCaseImpl(repository, recovery, clock);
        var result = useCase.cancel(process.ownerId(), process.id(), null);
        assertThat(result.process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(useCase.cancel(process.ownerId(), process.id(), null).process().phase())
                .isEqualTo(ReservationPhase.CANCELLED);
        verify(gateway, times(1)).cancel(any());
        verifyNoInteractions(central);
    }

    @Test
    void ownerIsCheckedBeforeReplayAndNoGatewayCanCancelAnotherUsersProcess() {
        var process = save(confirmed());
        var useCase = new CancelReservationUseCaseImpl(repository, recovery, clock);
        assertThatThrownBy(() -> useCase.cancel(99, process.id(), null)).isInstanceOf(ReservationOperationException.class);
        assertThatThrownBy(() -> useCase.cancel(42, UUID.randomUUID(), null)).isInstanceOf(ReservationOperationException.class);
        var abandon = new AbandonReservationUseCaseImpl(repository, recovery, clock);
        assertThatThrownBy(() -> abandon.abandon(99, process.id(), null)).isInstanceOf(ReservationOperationException.class);
        verifyNoInteractions(gateway, central);
    }

    @Test
    void abandonmentWaitsUntilCentralExpiryWithoutCancellingOrConsumingAttempts() {
        var process = save(ReservationProcessTests.confirming());
        var useCase = new AbandonReservationUseCaseImpl(repository, recovery, clock);
        assertThat(useCase.abandon(process.ownerId(), process.id(), null).process().phase())
                .isEqualTo(ReservationPhase.CONFIRMING);
        assertThat(useCase.abandon(process.ownerId(), process.id(), null).process().phase())
                .isEqualTo(ReservationPhase.CONFIRMING);
        when(clock.instant()).thenReturn(process.hold().expiresAt().minusSeconds(1));
        recovery.recoverDue();
        assertThat(repository.values.get(process.id()).cancellation().attempts()).isZero();
        assertThat(repository.values.get(process.id()).cancellation().nextAttemptAt()).isEqualTo(process.hold().expiresAt());
        verifyNoInteractions(gateway, central);
    }

    @Test
    void stableAbandonmentExpiresWithoutInventingCancellationOrRequiringCentralAccess() {
        var process = save(ReservationProcessTests.creating().acceptHold(ReservationProcessTests.HOLD));
        new AbandonReservationUseCaseImpl(repository, recovery, clock).abandon(42, process.id(), null);
        when(clock.instant()).thenReturn(process.hold().expiresAt());
        recovery.recoverDue();
        var result = repository.values.get(process.id());
        assertThat(result.phase()).isEqualTo(ReservationPhase.EXPIRED);
        assertThat(result.isPending()).isFalse();
        assertThat(result.cancellation().state()).isEqualTo(CancellationState.RESOLVED);
        assertThat(result.cancellation().attempts()).isZero();
        verifyNoInteractions(gateway, central);
    }

    @Test
    void anUncertainInitialConfirmationRequiresEvidenceEvenAfterExpiry() {
        var process = save(ReservationProcessTests.confirming());
        new AbandonReservationUseCaseImpl(repository, recovery, clock).abandon(42, process.id(), null);
        when(clock.instant()).thenReturn(process.hold().expiresAt());
        recovery.recoverDue();
        assertThat(repository.values.get(process.id()).isPending()).isTrue();
        assertThat(repository.values.get(process.id()).cancellation().attempts()).isEqualTo(1);
        verify(central).find(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void confirmedPhonePendingEvidenceAllowsAnUnsentAbandonmentToExpire() {
        var process = save(ReservationProcessTests.confirming());
        new AbandonReservationUseCaseImpl(repository, recovery, clock).abandon(42, process.id(), null);
        when(clock.instant()).thenReturn(process.hold().expiresAt());
        when(central.find(any())).thenReturn(Optional.of(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251,
                CentralAppointmentState.PHONE_PENDING, null)));
        assertThat(recovery.recover(process.id()).process().phase()).isEqualTo(ReservationPhase.EXPIRED);
        verifyNoInteractions(gateway);
    }

    @Test
    void threeInterruptedAttemptsExhaustBudgetAndOnlyExplicitRetryCanResume() {
        var process = save(confirmed());
        doThrow(uncertain()).when(gateway).cancel(any());
        var useCase = new CancelReservationUseCaseImpl(repository, recovery, clock);
        useCase.cancel(process.ownerId(), process.id(), null);
        for (int i = 1; i <= 2; i++) {
            when(clock.instant()).thenReturn(NOW.plusSeconds(i * 10L));
            recovery.recoverDue();
        }
        when(clock.instant()).thenReturn(NOW.plusSeconds(100));
        recovery.recoverDue();
        useCase.cancel(process.ownerId(), process.id(), null);
        assertThat(repository.values.get(process.id()).cancellation().attempts()).isEqualTo(3);
        verify(gateway, times(3)).cancel(any());
        doAnswer(call -> evidence(call.getArgument(0))).when(gateway).cancel(any());
        var done = useCase.cancel(process.ownerId(), process.id(), 0);
        assertThat(done.process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(done.process().cancellation().retryVersion()).isEqualTo(1);
        useCase.cancel(process.ownerId(), process.id(), 0);
        verify(gateway, times(4)).cancel(any());
    }

    @Test
    void responseLostAfterCentralEffectIsReconciledWithoutAnotherPost() {
        var process = save(confirmed());
        doThrow(uncertain()).when(gateway).cancel(any());
        new CancelReservationUseCaseImpl(repository, recovery, clock).cancel(process.ownerId(), process.id(), null);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        when(central.find(any())).thenReturn(Optional.of(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251,
                CentralAppointmentState.CANCELLED, null)));
        assertThat(recovery.recover(process.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void failedReconciliationDoesNotRepeatThePost() {
        var process = save(confirmed());
        doThrow(uncertain()).when(gateway).cancel(any());
        new CancelReservationUseCaseImpl(repository, recovery, clock).cancel(process.ownerId(), process.id(), null);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        when(central.find(any())).thenThrow(new ReservationGatewayException(
                ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false));
        assertThat(recovery.recover(process.id()).verificationStatus()).isEqualTo(VerificationStatus.UNAVAILABLE);
        verify(gateway, times(1)).cancel(any());
        assertThat(repository.values.get(process.id()).cancellation().attempts()).isEqualTo(2);
    }

    @Test
    void newConfirmationMakesWaitingAbandonmentImmediatelyCancellable() {
        var process = save(ReservationProcessTests.confirming());
        new AbandonReservationUseCaseImpl(repository, recovery, clock).abandon(42, process.id(), null);
        when(clock.instant()).thenReturn(NOW.plusSeconds(5));
        recovery.recoverDue();
        verifyNoInteractions(gateway);
        repository.update(process.id(), p -> p.apply(ReservationProcessTests.action(
                ReservationActionType.CONFIRMED, "new-confirmation"), NOW.plusSeconds(5)));
        recovery.recoverDue();
        assertThat(repository.values.get(process.id()).phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(repository.values.get(process.id()).cancellation().attempts()).isEqualTo(1);
        verify(gateway).cancel(any());
    }

    @Test
    void lateRejectionAfterConcurrentConfirmationCannotDeactivateRecovery() {
        var process = save(ReservationProcessTests.confirming());
        new AbandonReservationUseCaseImpl(repository, recovery, clock).abandon(42, process.id(), null);
        when(clock.instant()).thenReturn(process.hold().expiresAt());
        when(central.find(any())).thenAnswer(call -> {
            repository.update(process.id(), p -> p.apply(ReservationProcessTests.action(
                    ReservationActionType.CONFIRMED, "new-confirmation"), process.hold().expiresAt()));
            throw new ReservationGatewayException(ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false);
        });
        recovery.recover(process.id());
        assertThat(repository.values.get(process.id()).cancellation().state()).isEqualTo(CancellationState.PENDING);
        verifyNoInteractions(gateway);
    }

    @Test
    void oldFailureCannotOverwriteResolutionOrANewCycle() {
        var claimed = confirmed().requestCancellation(CancellationKind.CANCEL, null, NOW)
                .claimCancellation(NOW, RETRY);
        var resolved = claimed.cancel(evidence(claimed), NOW);
        assertThat(resolved.cancellationFailed(claimed, uncertain(), NOW, RETRY, COOLDOWN)).isSameAs(resolved);
        var exhausted = claimed.claimCancellation(NOW.plusSeconds(5), RETRY)
                .claimCancellation(NOW.plusSeconds(10), RETRY);
        var newCycle = exhausted.requestCancellation(CancellationKind.CANCEL, 0, NOW.plusSeconds(20));
        assertThat(newCycle.cancellationFailed(claimed, uncertain(), NOW, RETRY, COOLDOWN)).isSameAs(newCycle);
    }

    @ParameterizedTest
    @EnumSource(value = CancellationKind.class)
    void resolvedProgressNeverBecomesDueOrRetryable(CancellationKind kind) {
        var progress = CancellationProgress.create(kind, NOW).resolve();
        assertThat(progress.isDue(NOW.plusSeconds(100))).isFalse();
        assertThat(progress.canRetry(NOW.plusSeconds(100))).isFalse();
        assertThat(progress.resolve()).isSameAs(progress);
    }
}

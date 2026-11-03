package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.service.ReservationExecutionGuard;
import com.prog2.appointment.reservation.application.usecases.*;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.*;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Cancelación acotada observable; SQL/concurrencia real se verifican en integración. */
class ReservationCancellationTests {
    private static final Instant NOW = ReservationProcessTests.NOW;
    private static final Duration RETRY = Duration.ofSeconds(5);
    private final StartReservationTests.Memory repository = new StartReservationTests.Memory();
    private final CentralAppointmentGateway central = mock(CentralAppointmentGateway.class);
    private final ReservationCancellationGateway gateway = mock(ReservationCancellationGateway.class);
    private final Clock clock = mock(Clock.class);
    private final ReservationExecutionGuard guard = new ReservationExecutionGuard();
    private RecoverReservationCancellationsUseCaseImpl recovery;
    private CancelReservationUseCaseImpl useCase;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        doAnswer(call -> Optional.of(central(call.getArgument(0), CentralAppointmentState.CONFIRMED))).when(central).find(any());
        doAnswer(call -> evidence(call.getArgument(0))).when(gateway).cancel(any());
        recovery = new RecoverReservationCancellationsUseCaseImpl(repository, central, gateway,
                clock, Duration.ofSeconds(2), RETRY, guard);
        useCase = new CancelReservationUseCaseImpl(repository, recovery, clock);
    }

    private static ReservationProcess confirmed() {
        return ReservationProcessTests.confirming().apply(
                ReservationProcessTests.action(ReservationActionType.CONFIRMED, "confirmed"), NOW);
    }

    private static CancellationEvidence evidence(ReservationProcess process) {
        return new CancellationEvidence(process.hold().centralProcessId(), process.reservationId(), NOW);
    }

    private static CentralAppointment central(ReservationProcess p, CentralAppointmentState state) {
        return new CentralAppointment(p.hold().centralProcessId(), p.patient().externalPatientId(),
                p.selection(), p.reservationId(), state, null);
    }

    private ReservationProcess save() {
        var process = confirmed(); repository.values.put(process.id(), process); return process;
    }

    private static ReservationGatewayException uncertain() {
        return new ReservationGatewayException(ReservationFailure.CANCELLATION_UNCERTAIN, true, false);
    }

    @Test
    void confirmedReservationRemainsHistoricalWhileItsCancellationIsPending() {
        var process = confirmed().requestCancellation();
        assertThat(process.phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(process.isPending()).isFalse();
        assertThat(process.cancellation()).isEqualTo(CancellationState.PENDING);
        assertThat(process.requestCancellation()).isSameAs(process);
    }

    @Test
    void invalidOrForeignEvidenceDoesNotCloseTheProcess() {
        var process = confirmed().requestCancellation();
        assertThat(process.cancel(new CancellationEvidence("other", 8251L, NOW), NOW)).isSameAs(process);
        assertThat(process.cancel(new CancellationEvidence(process.hold().centralProcessId(), 99L, NOW), NOW)).isSameAs(process);
        assertThatThrownBy(() -> new CancellationEvidence("", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationEvidence("process", 0L, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationEvidence("process", 8251L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cancellationEvidenceRequiresTheCentralReservationIdentifier() {
        assertThatThrownBy(() -> new CancellationEvidence("process", null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyConfirmedReservationsAcceptCancellationAndFinalsDoNotReopen() {
        var creating = ReservationProcessTests.creating();
        var pending = ReservationProcessTests.confirming();
        assertThatThrownBy(creating::requestCancellation).isInstanceOf(ReservationCancellationException.class);
        assertThatThrownBy(pending::requestCancellation).isInstanceOf(ReservationCancellationException.class);
        var confirmed = confirmed();
        var cancelled = confirmed.cancel(evidence(confirmed), NOW);
        assertThat(cancelled.requestCancellation()).isSameAs(cancelled);
        assertThat(cancelled.cancellation()).isNull();
        var expired = pending.apply(ReservationProcessTests.action(ReservationActionType.EXPIRED, "expired"), NOW);
        assertThatThrownBy(expired::requestCancellation).isInstanceOf(ReservationCancellationException.class);
    }

    @Test
    void successfulUseCasePersistsThenCancelsAndReplayHasNoEffects() {
        var p = save();
        var result = useCase.cancel(p.ownerId(), p.id());
        assertThat(result.process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(result.warningCode()).isNull();
        assertThat(useCase.cancel(p.ownerId(), p.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        var order = inOrder(central, gateway);
        order.verify(central).find(any()); order.verify(gateway).cancel(any()); order.verifyNoMoreInteractions();
    }

    @Test
    void ownerIsCheckedBeforeReplayAndNoGatewayCanCancelAnotherUsersProcess() {
        var p = save();
        var id = p.id();
        var missing = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.cancel(99, id)).isInstanceOf(ReservationOperationException.class);
        assertThatThrownBy(() -> useCase.cancel(42, missing)).isInstanceOf(ReservationOperationException.class);
        verifyNoInteractions(gateway, central);
    }

    @Test
    void threePostsStopAutomaticallyAndTheOwnerCanRetryWithoutAVersion() {
        var p = save(); doThrow(uncertain()).when(gateway).cancel(any());
        var stopped = useCase.cancel(p.ownerId(), p.id());
        assertThat(stopped.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(stopped.process().cancellation()).isEqualTo(CancellationState.RETRY_REQUIRED);
        assertThat(stopped.warningCode()).isEqualTo("CANCELLATION_UNCERTAIN");
        var order = inOrder(central, gateway);
        for (int i=0;i<3;i++) { order.verify(central).find(any()); order.verify(gateway).cancel(any()); }
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        recovery.recover(p.id()); recovery.recover(p.id());
        verify(gateway, times(3)).cancel(any());
        doAnswer(call -> evidence(call.getArgument(0))).when(gateway).cancel(any());
        assertThat(useCase.cancel(p.ownerId(), p.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        verify(gateway, times(4)).cancel(any());
    }

    @Test
    void responseLostAfterCentralEffectIsReconciledWithoutAnotherPost() {
        var p = save(); doThrow(uncertain()).when(gateway).cancel(any());
        doReturn(Optional.of(central(p, CentralAppointmentState.CONFIRMED)),
                Optional.of(central(p, CentralAppointmentState.CANCELLED))).when(central).find(any());
        assertThat(useCase.cancel(p.ownerId(), p.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        verify(gateway, times(1)).cancel(any()); verify(central, times(2)).find(any());
    }

    @Test
    void aFailedReadAfterAnUncertainPostDoesNotRepeatItAndLaterRecoveryFindsItsEffect() {
        var p = save(); doThrow(uncertain()).when(gateway).cancel(any());
        doReturn(Optional.of(central(p, CentralAppointmentState.CONFIRMED)))
                .doThrow(new ReservationGatewayException(ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false)).when(central).find(any());
        assertThat(useCase.cancel(p.ownerId(), p.id()).process().cancellation()).isEqualTo(CancellationState.RETRY_REQUIRED);
        verify(gateway, times(1)).cancel(any());
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        doReturn(Optional.of(central(p, CentralAppointmentState.CANCELLED))).when(central).find(any());
        assertThat(recovery.recover(p.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void anUnavailableInitialReadKeepsTheIntentForSpacedRecoveryWithoutPosting() {
        var p = save(); doReturn(Optional.empty()).when(central).find(any());
        assertThat(useCase.cancel(p.ownerId(), p.id()).process().cancellation()).isEqualTo(CancellationState.PENDING);
        verifyNoInteractions(gateway);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        doReturn(Optional.of(central(p, CentralAppointmentState.CONFIRMED))).when(central).find(any());
        assertThat(recovery.recover(p.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
    }

    @Test
    void aDefinitiveRejectionStopsAfterOnePostAndCannotChangeTheReservationPhase() {
        var p = save(); doThrow(new ReservationGatewayException(
                ReservationFailure.CANCELLATION_REJECTED, true, true)).when(gateway).cancel(any());
        var result = useCase.cancel(p.ownerId(), p.id());
        assertThat(result.process().cancellation()).isEqualTo(CancellationState.RETRY_REQUIRED);
        assertThat(result.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10)); recovery.recover(p.id());
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void aMismatchedPostResponseStopsWithoutAnotherAttemptOrClaimingCancellation() {
        var p = save();
        doReturn(new CancellationEvidence("another-process", p.reservationId(), NOW)).when(gateway).cancel(any());
        var result = useCase.cancel(p.ownerId(), p.id());
        assertThat(result.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(result.process().cancellation()).isEqualTo(CancellationState.RETRY_REQUIRED);
        assertThat(result.warningCode()).isEqualTo(ReservationFailure.CORRELATION_MISMATCH.name());
        verify(gateway, times(1)).cancel(any());
        verify(central, times(1)).find(any());
    }

    @Test
    void anInitialReadFailureKeepsTheIntentWithoutTryingThePost() {
        var p = save();
        doThrow(new ReservationGatewayException(ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false))
                .when(central).find(any());
        var result = useCase.cancel(p.ownerId(), p.id());
        assertThat(result.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(result.process().cancellation()).isEqualTo(CancellationState.PENDING);
        assertThat(result.warningCode()).isEqualTo(ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE.name());
        verify(central, times(1)).find(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void anActiveLocalExecutionPreventsAnotherTransportAttempt() {
        var p = save(); repository.requestCancellation(p.ownerId(), p.id(), NOW);
        try (var _ = guard.tryAcquire(p.id()).orElseThrow()) {
            assertThat(recovery.recover(p.id()).process().cancellation()).isEqualTo(CancellationState.PENDING);
            verifyNoInteractions(central, gateway);
        }
        assertThat(recovery.recover(p.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
    }
}

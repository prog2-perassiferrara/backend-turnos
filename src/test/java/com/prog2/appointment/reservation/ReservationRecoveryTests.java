package com.prog2.appointment.reservation;

import com.prog2.appointment.reservation.application.usecases.RecoverReservationProcessesUseCaseImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.usecases.StartReservationUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.*;
import com.prog2.appointment.reservation.infrastructure.concurrency.InMemoryReservationExecutionGuard;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** Continuación real de una intención guardada; solo se sustituyen sus fronteras externas. */
class ReservationRecoveryTests {
    private static final Instant NOW = ReservationProcessTests.NOW;
    private final StartReservationTests.Memory repository = new StartReservationTests.Memory();
    private final InMemoryReservationExecutionGuard guard = new InMemoryReservationExecutionGuard();
    private final AppointmentHoldGateway holds = mock(AppointmentHoldGateway.class);
    private final ReservationPatientGateway patients = mock(ReservationPatientGateway.class);
    private final ReservableSlotGateway slots = mock(ReservableSlotGateway.class);
    private final ReservationMessagingReadiness messaging = mock(ReservationMessagingReadiness.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private StartReservationUseCaseImpl start;

    @BeforeEach
    void setup() {
        start = new StartReservationUseCaseImpl(patients, slots, holds, repository, messaging, guard, clock, 3);
        when(holds.confirm(any())).thenReturn(ReservationProcessTests.HOLD);
    }

    private ReservationProcess held() {
        var value = ReservationProcessTests.creating().acceptHold(ReservationProcessTests.HOLD);
        repository.begin(value);
        return value;
    }

    private RecoverReservationProcessesUseCaseImpl
            recovery(Instant now, CentralAppointmentGateway central) {
        return new RecoverReservationProcessesUseCaseImpl(
                repository, central, start, guard, Clock.fixed(now, ZoneOffset.UTC),
                new ReservationRecoveryPolicy(3, Duration.ofSeconds(5),
                        Duration.ofSeconds(30), Duration.ofSeconds(30)));
    }

    @Test
    void anOrphanWithoutIdsIsArchivedWithoutCreatingAnotherHold() {
        var process = ReservationProcessTests.creating(); repository.begin(process);
        var central = mock(CentralAppointmentGateway.class);
        recovery(NOW.plusSeconds(31), central).recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase())
                .isEqualTo(ReservationPhase.HOLD_ATTEMPT_ABANDONED);
        verifyNoInteractions(holds, central);
    }

    @Test
    void readCyclesKeepCheckingAfterExpiryWithoutRepeatingEffects() {
        var process = held().beginConfirmation(); repository.update(process.id(), _ -> process);
        var central = mock(CentralAppointmentGateway.class);
        when(central.find(any())).thenReturn(java.util.Optional.empty());
        for (int second : new int[] {300, 301, 305, 310, 339})
            recovery(NOW.plusSeconds(second), central).recoverDue();
        verify(central, times(3)).find(any());
        assertThat(repository.find(process.id()).orElseThrow().isPending()).isTrue();
        when(central.find(any())).thenReturn(java.util.Optional.of(new CentralAppointment(
                process.hold().centralProcessId(), process.patient().externalPatientId(), process.selection(),
                8251, CentralAppointmentState.FAILED, null)));
        recovery(NOW.plusSeconds(340), central).recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.FAILED);
        verify(central, times(4)).find(any()); verifyNoInteractions(holds);
    }

    @Test
    void aFailureInOneReviewDoesNotLoseTheScopeOrStopAnotherRecord() {
        var first = held().beginConfirmation(); repository.update(first.id(), _ -> first);
        var other = ReservationProcessTests.confirming(); repository.values.put(other.id(), other);
        var central = mock(CentralAppointmentGateway.class);
        when(central.find(any())).thenThrow(new ReservationGatewayException(
                ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false));
        recovery(NOW.plusSeconds(31), central).recoverDue();
        verify(central, times(2)).find(any());
        try (var _ = guard.tryAcquire(first.id()).orElseThrow()) {
            assertThat(repository.find(first.id()).orElseThrow().isPending()).isTrue();
        }
    }

    @Test
    void resumesOnlyTheKnownHoldAndDoesNotRecreateOrRefreshItsPatient() {
        var process = held();
        var result = start.resumeKnownHold(process.id());
        assertThat(result.phase()).isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        assertThat(start.resumeKnownHold(process.id())).isEqualTo(result);
        verify(holds, times(1)).confirm(any());
        verify(holds, never()).create(any());
        verifyNoInteractions(patients, slots);
    }

    @Test
    void anotherActiveScopePreventsContinuationAndAnOldCloseCannotUnlockANewOwner() {
        var process = held();
        var first = guard.tryAcquire(process.id()).orElseThrow();
        assertThat(start.resumeKnownHold(process.id()).phase()).isEqualTo(ReservationPhase.HELD);
        verifyNoInteractions(holds);
        first.close();
        var second = guard.tryAcquire(process.id()).orElseThrow();
        first.close();
        assertThat(guard.tryAcquire(process.id())).isEmpty();
        second.close();
        assertThat(start.resumeKnownHold(process.id()).phase()).isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
    }

    @Test
    void aLostConfirmationResponseIsNeverRetriedAndItsScopeIsReleased() {
        var process = held();
        when(holds.confirm(any())).thenThrow(new ReservationGatewayException(
                ReservationFailure.CONFIRMATION_UNCERTAIN, true, false));
        UUID id = process.id();
        assertThatThrownBy(() -> start.resumeKnownHold(id))
                .isInstanceOf(com.prog2.appointment.reservation.application.exception.ReservationOperationException.class);
        assertThat(start.resumeKnownHold(id).phase()).isEqualTo(ReservationPhase.CONFIRMATION_UNCERTAIN);
        try (var _ = guard.tryAcquire(id).orElseThrow()) {
            assertThat(repository.find(id).orElseThrow().isPending()).isTrue();
        }
        verify(holds, times(1)).confirm(any());
    }

    @Test
    void expiryClosesOnlyAKnownHoldWhoseConfirmationWasNeverAttempted() {
        var process = held();
        var deadline = process.hold().expiresAt();
        assertThat(process.expireUnconfirmedHold(deadline.minusNanos(1))).isSameAs(process);
        var expired = process.expireUnconfirmedHold(deadline);
        assertThat(expired.phase()).isEqualTo(ReservationPhase.EXPIRED);
        assertThat(expired.isPending()).isFalse();
        assertThat(process.beginConfirmation().expireUnconfirmedHold(deadline).phase())
                .isEqualTo(ReservationPhase.CONFIRMING);
    }

    @Test
    void expiryBeforeTransportClosesOnlyTheStillOwnedLiveClaim() {
        var process = held().beginConfirmation();
        var expired = process.expireUnsentConfirmation(process, process.hold().expiresAt());
        assertThat(expired.phase()).isEqualTo(ReservationPhase.EXPIRED);
        var confirmed = process.apply(ReservationProcessTests.action(ReservationActionType.CONFIRMED, "confirmed"), NOW);
        assertThat(confirmed.expireUnsentConfirmation(process, process.hold().expiresAt())).isSameAs(confirmed);
    }

    @Test
    @Timeout(10)
    void twoContinuationsDoNotShareTheSameConfirmationPermission() throws Exception {
        var process = held();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(holds.confirm(any())).thenAnswer(_ -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Gate not released");
            return ReservationProcessTests.HOLD;
        });
        try (var workers = Executors.newSingleThreadExecutor()) {
            var first = workers.submit(() -> start.resumeKnownHold(process.id()));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(start.resumeKnownHold(process.id()).phase()).isEqualTo(ReservationPhase.CONFIRMING);
            } finally {
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).phase()).isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        }
        verify(holds, times(1)).confirm(any());
    }
}

package com.prog2.appointment.reservation;

import com.prog2.appointment.reservation.application.service.ReservationExecutionGuard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.StartReservationUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.HoldOccupancy;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationPatient;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.StartReservationRequest;
import com.prog2.appointment.reservation.domain.ports.out.AppointmentHoldGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservableSlotGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationMessagingReadiness;
import com.prog2.appointment.reservation.domain.ports.out.ReservationPatientGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

/**
 * Orquestación real: los doubles controlan únicamente fronteras y observan los efectos exigidos.
 */
class StartReservationTests {
    private final Memory repository = new Memory();
    private final ReservationPatientGateway patients = mock(ReservationPatientGateway.class);
    private final ReservableSlotGateway slots = mock(ReservableSlotGateway.class);
    private final AppointmentHoldGateway holds = mock(AppointmentHoldGateway.class);
    private final ReservationMessagingReadiness messaging =
            mock(ReservationMessagingReadiness.class);
    private final ReservationExecutionGuard guard =
            new ReservationExecutionGuard();
    private StartReservationUseCaseImpl start;
    private StartReservationRequest request;

    @BeforeEach
    void setup() {
        start =
                new StartReservationUseCaseImpl(
                        patients,
                        slots,
                        holds,
                        repository,
                        messaging,
                        guard,
                        Clock.fixed(
                                ReservationProcessTests.NOW,
                                ZoneId.of("America/Argentina/Buenos_Aires")),
                        3);
        request = new StartReservationRequest(UUID.randomUUID(), ReservationProcessTests.SLOT);
        when(patients.get(42)).thenReturn(new ReservationPatient(42, "Paciente", "Ficticio"));
        when(holds.create(any()))
                .thenAnswer(
                        call -> {
                            var value = call.getArgument(0, ReservationProcess.class);
                            assertThat(repository.find(value.id()).orElseThrow().phase())
                                    .isEqualTo(ReservationPhase.HOLD_PENDING);
                            return ReservationProcessTests.HOLD;
                        });
        when(holds.confirm(any()))
                .thenAnswer(
                        call -> {
                            var value = call.getArgument(0, ReservationProcess.class);
                            assertThat(repository.find(value.id()).orElseThrow().phase())
                                    .isEqualTo(ReservationPhase.CONFIRMATION_PENDING);
                            return ReservationProcessTests.HOLD;
                        });
    }

    @Test
    void admissionPreservesTheValidatedNameAndReplayDoesNotReadTheAgendaAgain() {
        var name = new com.prog2.appointment.reservation.domain.model.ProfessionalName("Ana", "Torres");
        when(slots.verify(any())).thenReturn(name);
        var created = start.start(42, request);
        assertThat(repository.find(request.requestId()).orElseThrow().professionalName()).isEqualTo(name);
        assertThat(created.process().professionalName()).isEqualTo(name);
        assertThat(start.start(42, request).process().professionalName()).isEqualTo(name);
        verify(slots, times(1)).verify(any());
    }

    @Test
    void absentOptionalNameDoesNotPreventStarting() {
        assertThat(start.start(42, request).process().professionalName()).isNull();
        verify(holds, times(1)).create(any());
    }

    @Test
    void preservesIntentAndIdsBeforeEachPostAndReplaysWithoutEffects() {
        var created = start.start(42, request);
        assertThat(created.replayed()).isFalse();
        assertThat(created.process().phase()).isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        var replay = start.start(42, request);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.process().id()).isEqualTo(request.requestId());
        verify(holds, times(1)).create(any());
        verify(holds, times(1)).confirm(any());
        verify(slots, times(1)).verify(any());
    }

    @Test
    void aProfileFailureNeverRegistersOrCreatesAHold() {
        when(patients.get(42))
                .thenThrow(
                        new ReservationGatewayException(
                                ReservationFailure.PROFILE_NOT_RESERVABLE, false, true));
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOf(ReservationGatewayException.class);
        assertThat(repository.values).isEmpty();
        verifyNoInteractions(holds);
    }

    @Test
    void unavailableMessagingPreventsAnyHoldAndLocalAdmission() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.MESSAGING_NOT_READY, false, false))
                .when(messaging)
                .requireReady();
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOf(ReservationGatewayException.class);
        assertThat(repository.values).isEmpty();
        verifyNoInteractions(holds);
    }

    @Test
    void aPastSelectionIsRejectedBeforeProfileOrNetwork() {
        var old =
                new StartReservationRequest(
                        request.requestId(),
                        new ReservationSelection(
                                15, LocalDate.of(2026, 11, 1), LocalTime.of(9, 0)));
        assertThatThrownBy(() -> start.start(42, old))
                .isInstanceOf(ReservationOperationException.class);
        verifyNoInteractions(patients, slots, holds);
    }

    @Test
    void anExistingRequestCannotBeClaimedByAnotherOwnerOrAnotherSelection() {
        start.start(42, request);
        clearInvocations(holds, patients, slots);
        assertThatThrownBy(() -> start.start(43, request))
                .isInstanceOf(ReservationOperationException.class);
        var changed =
                new StartReservationRequest(
                        request.requestId(),
                        new ReservationSelection(
                                15, request.selection().date(), LocalTime.of(10, 0)));
        assertThatThrownBy(() -> start.start(42, changed))
                .isInstanceOf(ReservationOperationException.class);
        verifyNoInteractions(holds, patients, slots);
    }

    @Test
    void uncertainCreationIsBoundedAndItsClosedKeyNeverReplaysPosts() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOf(ReservationOperationException.class);
        assertThat(repository.find(request.requestId()).orElseThrow().phase())
                .isEqualTo(ReservationPhase.FAILED);
        assertThat(start.start(42, request).replayed()).isTrue();
        assertThat(repository.findPending(42)).isEmpty();
        verify(holds, times(3)).create(any());
        verify(holds, times(3)).findOccupancy(request.selection());
        verify(holds, never()).confirm(any());
    }

    @Test
    void anObservedHoldStopsRetriesWithoutClaimingItsOwnership() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any()))
                .thenReturn(
                        Optional.of(
                                new HoldOccupancy(
                                        false, ReservationProcessTests.HOLD.expiresAt())));
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure -> {
                            assertThat(failure.failure())
                                    .isEqualTo(ReservationFailure.SLOT_NOT_AVAILABLE);
                            assertThat(failure.recheckAt())
                                    .isEqualTo(ReservationProcessTests.HOLD.expiresAt());
                        });
        var closed = repository.find(request.requestId()).orElseThrow();
        assertThat(closed.phase()).isEqualTo(ReservationPhase.FAILED);
        assertThat(closed.hold()).isNull();
        assertThat(closed.isPending()).isFalse();
        verify(holds, times(1)).create(any());
        verify(holds, never()).confirm(any());
        assertThat(start.start(42, request).replayed()).isTrue();
        verify(holds, times(1)).findOccupancy(any());
    }

    @Test
    void confirmedOccupancyDoesNotBecomeAnOwnedReservation() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any())).thenReturn(Optional.of(new HoldOccupancy(true, null)));
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure -> {
                            assertThat(failure.failure())
                                    .isEqualTo(ReservationFailure.SLOT_NOT_AVAILABLE);
                            assertThat(failure.recheckAt()).isNull();
                        });
        var closed = repository.find(request.requestId()).orElseThrow();
        assertThat(closed.reservationId()).isNull();
        assertThat(closed.isPending()).isFalse();
        verify(holds, times(1)).create(any());
    }

    @Test
    void aFreeRecheckPermitsANewPostWithDurableIntent() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(
                        call -> {
                            var process = call.getArgument(0, ReservationProcess.class);
                            assertThat(repository.find(process.id()).orElseThrow().phase())
                                    .isEqualTo(ReservationPhase.HOLD_PENDING);
                            if (attempts.incrementAndGet() == 1) {
                                throw new ReservationGatewayException(
                                        ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false);
                            }
                            return ReservationProcessTests.HOLD;
                        })
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any())).thenReturn(Optional.empty());
        assertThat(start.start(42, request).process().phase())
                .isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        verify(holds, times(2)).create(any());
        verify(holds, times(1)).findOccupancy(any());
        verify(holds, times(1)).confirm(any());
    }

    @Test
    void failedRecheckCannotAuthorizeAnotherCreation() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any()))
                .thenThrow(
                        new ReservationGatewayException(
                                ReservationFailure.OCCUPANCIES_UNAVAILABLE, false, false));
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(ReservationFailure.OCCUPANCIES_UNAVAILABLE));
        assertThat(repository.findPending(42)).isEmpty();
        verify(holds, times(1)).create(any());
    }

    @Test
    void anExpiredObservedHeldPositionStillRequiresAnotherQuery() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any()))
                .thenReturn(
                        Optional.of(
                                new HoldOccupancy(
                                        false, ReservationProcessTests.NOW.minusSeconds(600))));
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure ->
                                assertThat(failure.recheckAt())
                                        .isEqualTo(ReservationProcessTests.NOW.minusSeconds(600)));
        verify(holds, times(1)).create(any());
    }

    @Test
    void aConcurrentConflictAfterAFreeCheckClosesWithoutAnotherRetry() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.SLOT_NOT_AVAILABLE, true, true))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(ReservationFailure.SLOT_NOT_AVAILABLE));
        assertThat(repository.find(request.requestId()).orElseThrow().phase())
                .isEqualTo(ReservationPhase.FAILED);
        verify(holds, times(2)).create(any());
        verify(holds, times(1)).findOccupancy(any());
    }

    @Test
    void timePassingDuringTheRecheckPreventsAnotherPost() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(ReservationProcessTests.NOW);
        var ticking = mock(Clock.class);
        when(ticking.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(ticking.instant()).thenAnswer(_ -> now.get());
        start =
                new StartReservationUseCaseImpl(
                        patients, slots, holds, repository, messaging, guard, ticking, 3);
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any()))
                .thenAnswer(
                        _ -> {
                            now.set(ReservationProcessTests.NOW.plusSeconds(3600));
                            return Optional.empty();
                        });
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(
                                                ReservationFailure.INVALID_RESERVATION_SELECTION));
        verify(holds, times(1)).create(any());
        assertThat(repository.findPending(42)).isEmpty();
    }

    @Test
    void kafkaFailureDuringTheRecheckPreventsAnotherPost() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        when(holds.findOccupancy(any()))
                .thenAnswer(
                        _ -> {
                            doThrow(
                                            new ReservationGatewayException(
                                                    ReservationFailure.MESSAGING_NOT_READY,
                                                    false,
                                                    false))
                                    .when(messaging)
                                    .requireReady();
                            return Optional.empty();
                        });
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(ReservationFailure.MESSAGING_NOT_READY));
        verify(holds, times(1)).create(any());
        assertThat(repository.findPending(42)).isEmpty();
    }

    @Test
    void anEarlyPhoneRequestWinsOverTheLaterRestAcceptance() {
        doAnswer(
                        call -> {
                            var value = call.getArgument(0, ReservationProcess.class);
                            repository.update(
                                    value.id(),
                                    process ->
                                            process.apply(
                                                    ReservationProcessTests.action(
                                                            ReservationActionType.PHONE_REQUESTED,
                                                            "request-1"),
                                                    ReservationProcessTests.NOW));
                            return ReservationProcessTests.HOLD;
                        })
                .when(holds)
                .confirm(any());
        assertThat(start.start(42, request).process().phase())
                .isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
    }

    @Test
    void aClosedRequestIsRecognizedEvenAfterItsAppointmentDate() {
        var candidate =
                ReservationProcess.begin(
                        request, new ReservationPatient(42, "Paciente", "Ficticio"), null);
        repository.begin(candidate);
        repository.update(
                candidate.id(),
                value ->
                        value.recordFailure(
                                new ReservationGatewayException(
                                        ReservationFailure.SLOT_NOT_AVAILABLE, true, true),
                                ReservationProcessTests.NOW));
        var later =
                new StartReservationUseCaseImpl(
                        patients,
                        slots,
                        holds,
                        repository,
                        messaging,
                        guard,
                        Clock.fixed(
                                Instant.parse("2027-01-01T00:00:00Z"),
                                ZoneId.of("America/Argentina/Buenos_Aires")),
                        3);
        assertThat(later.start(42, request).process().phase()).isEqualTo(ReservationPhase.FAILED);
        verifyNoInteractions(patients, slots, holds);
    }

    @Test
    void anExpiredKnownHoldClosesWithoutAttemptingConfirmation() {
        doReturn(new HoldDetails("hold-expired", "process-expired", ReservationProcessTests.NOW))
                .when(holds)
                .create(any());
        assertThat(start.start(42, request).process().phase()).isEqualTo(ReservationPhase.EXPIRED);
        verify(holds, never()).confirm(any());
        assertThat(repository.find(request.requestId()).orElseThrow().isPending()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void anUncertainConfirmationCheckpointCannotReachTheTransport(CommitFaultDataSource.Mode mode) {
        var storage = spy(repository);
        doAnswer(call -> {
            if (mode == CommitFaultDataSource.Mode.AFTER) call.callRealMethod();
            throw new com.prog2.appointment.reservation.domain.exception.ReservationStorageException();
        }).when(storage).beginInitialConfirmation(any(), any());
        var operation = new StartReservationUseCaseImpl(patients, slots, holds, storage, messaging, guard,
                Clock.fixed(ReservationProcessTests.NOW, ZoneId.of("America/Argentina/Buenos_Aires")), 3);
        assertThatThrownBy(() -> operation.start(42, request)).isInstanceOf(ReservationOperationException.class);
        verify(holds, times(1)).create(any());
        verify(holds, never()).confirm(any());
        assertThat(storage.find(request.requestId()).orElseThrow().phase()).isEqualTo(
                mode == CommitFaultDataSource.Mode.AFTER ? ReservationPhase.CONFIRMATION_PENDING : ReservationPhase.HELD);
    }

    static final class Memory implements ReservationProcessRepository {
        final Map<UUID, ReservationProcess> values = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<UUID, Instant> recoveryNext = new HashMap<>();

        @Override
        public com.prog2.appointment.reservation.domain.model.OwnReservationPage readOwn(
                long ownerId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ReservationProcess> find(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<ReservationProcess> findPending(long owner) {
            return values.values().stream()
                    .filter(value -> value.ownerId() == owner && value.isPending())
                    .findFirst();
        }

        @Override
        public BeginResult begin(ReservationProcess value) {
            var existing = find(value.id()).or(() -> findPending(value.ownerId()));
            if (existing.isPresent()) {
                return new BeginResult(existing.get(), false);
            }
            values.put(value.id(), value);
            return new BeginResult(value, true);
        }

        @Override
        public ReservationProcess update(UUID id, UnaryOperator<ReservationProcess> change) {
            var changed = change.apply(values.get(id));
            values.put(id, changed);
            return changed;
        }

        @Override
        public synchronized Optional<ReservationProcess> beginInitialConfirmation(UUID id, Instant now) {
            var before = values.get(id);
            if (before.phase() != ReservationPhase.HELD
                    || !now.isBefore(before.hold().expiresAt())) return Optional.empty();
            var after = update(id, ReservationProcess::beginConfirmation);
            return Optional.of(after);
        }

        @Override
        public java.util.List<UUID> dueRecovery(Instant now, java.time.Duration initialDelay, int limit) {
            return values.values().stream().filter(p -> p.isPending() || p.cancellation() != null)
                    .filter(p -> !now.isBefore(recoveryNext.getOrDefault(p.id(),
                            p.cancellation() != null ? ReservationProcessTests.NOW : ReservationProcessTests.NOW.plus(initialDelay))))
                    .sorted(java.util.Comparator.comparing(ReservationProcess::id)).limit(limit)
                    .map(ReservationProcess::id).toList();
        }

        @Override
        public synchronized Optional<ReservationProcess> beginRecovery(UUID id, Instant now,
                java.time.Duration retry, java.time.Duration initialDelay) {
            if (!dueRecovery(now, initialDelay, Integer.MAX_VALUE).contains(id)) return Optional.empty();
            var process = values.get(id);
            var next = now.plus(retry);
            if (process.hold() != null && now.isBefore(process.hold().expiresAt())
                    && process.hold().expiresAt().isBefore(next)) next = process.hold().expiresAt();
            recoveryNext.put(id, next);
            return find(id);
        }

        @Override
        public void deferRecovery(UUID id, Instant nextAttempt) {
            recoveryNext.merge(id, nextAttempt, (old, next) -> old.isAfter(next) ? old : next);
        }



        @Override
        public PhoneResult submitPhone(
                long ownerId,
                UUID id,
                com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest request,
                String group,
                Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.prog2.appointment.reservation.domain.model.PhoneSubmission> findPhone(
                UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.prog2.appointment.reservation.domain.model.PhoneSubmission> latestPhone(
                UUID id) {
            return Optional.empty();
        }

        @Override
        public java.util.List<UUID> duePhones(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.prog2.appointment.reservation.domain.model.PhoneSubmission>
                beginPhonePublication(
                        UUID id,
                        Instant now,
                        int attempts,
                        java.time.Duration retry,
                        java.time.Duration cooldown) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void phonePublished(UUID id, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReservationProcess reconcile(
                UUID id,
                com.prog2.appointment.reservation.domain.model.CentralAppointment evidence,
                Instant now) {
            return update(id, value -> value.reconcile(evidence, now));
        }

        private static ReservationProcess cancellationWrite(ReservationProcess p) {
            return new ReservationProcess(p.id(), p.patient(), p.selection(), p.phase(), p.hold(),
                    p.requestEventId(), p.reservationId(), p.reasonCode(), UUID.randomUUID(), p.closedAt(),
                    p.professionalName(), p.cancellation());
        }

        @Override
        public synchronized ReservationProcess requestCancellation(long ownerId, UUID id, Instant now) {
            var before = values.get(id);
            if (before == null || before.ownerId() != ownerId) throw new
                    com.prog2.appointment.reservation.domain.exception.ReservationCancellationException(
                            com.prog2.appointment.reservation.domain.enums.ReservationFailure.RESERVATION_PROCESS_NOT_FOUND);
            var after = before.requestCancellation();
            if (!after.equals(before)) {
                after = cancellationWrite(after);
                values.put(id, after);
                recoveryNext.put(id, now);
            }
            return after;
        }

        @Override
        public synchronized Optional<ReservationProcess> beginCancellationAttempt(UUID id, Instant now,
                java.time.Duration budget) {
            var before = values.get(id);
            if (before.cancellation() == null || now.isBefore(recoveryNext.getOrDefault(id, now))) return Optional.empty();
            var after = cancellationWrite(before);
            values.put(id, after);
            recoveryNext.put(id, now.plus(budget));
            return Optional.of(after);
        }

        @Override
        public synchronized void finishCancellationAttempt(UUID id, UUID writeId, boolean requireRetry, String failure,
                Instant now, Instant nextReview) {
            var before = values.get(id);
            if (!before.writeId().equals(writeId) || before.cancellation() == null) return;
            if (requireRetry) values.put(id, before.requireCancellationRetry(failure));
            recoveryNext.put(id, nextReview);
        }

        @Override
        public ReservationProcess confirmCancellation(UUID id,
                com.prog2.appointment.reservation.domain.model.CancellationEvidence evidence, Instant now) {
            return update(id, value -> value.cancel(evidence, now));
        }

        @Override
        public void accept(ReservationAction delivery) {
            throw new UnsupportedOperationException();
        }
    }
}

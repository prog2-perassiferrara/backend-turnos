package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import java.time.ZoneOffset;
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
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
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
                        Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC),
                        ZoneId.of("America/Argentina/Buenos_Aires"));
        request = new StartReservationRequest(UUID.randomUUID(), ReservationProcessTests.SLOT);
        when(patients.get(42)).thenReturn(new ReservationPatient(42, "Paciente", "Ficticio"));
        when(holds.create(any()))
                .thenAnswer(
                        call -> {
                            var value = call.getArgument(0, ReservationProcess.class);
                            assertThat(repository.find(value.id()).orElseThrow().phase())
                                    .isEqualTo(ReservationPhase.CREATING_HOLD);
                            return ReservationProcessTests.HOLD;
                        });
        when(holds.confirm(any()))
                .thenAnswer(
                        call -> {
                            var value = call.getArgument(0, ReservationProcess.class);
                            assertThat(repository.find(value.id()).orElseThrow().phase())
                                    .isEqualTo(ReservationPhase.CONFIRMING);
                            return ReservationProcessTests.HOLD;
                        });
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
    void aNewKeyDoesNotBypassAnUncertainPendingProcess() {
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false))
                .when(holds)
                .create(any());
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOf(ReservationOperationException.class);
        assertThat(repository.find(request.requestId()).orElseThrow().phase())
                .isEqualTo(ReservationPhase.HOLD_UNCERTAIN);
        var another = new StartReservationRequest(UUID.randomUUID(), request.selection());
        assertThatThrownBy(() -> start.start(42, another))
                .isInstanceOf(ReservationOperationException.class);
        verify(holds, times(1)).create(any());
        verify(holds, never()).confirm(any());
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
                        request, new ReservationPatient(42, "Paciente", "Ficticio"));
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
                        Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC),
                        ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(later.start(42, request).process().phase()).isEqualTo(ReservationPhase.REJECTED);
        verifyNoInteractions(patients, slots, holds);
    }

    @Test
    void anAlreadyExpiredHoldDoesNotStartConfirmationOrReleaseAnUnverifiedClose() {
        doReturn(new HoldDetails("hold-expired", "process-expired", ReservationProcessTests.NOW))
                .when(holds)
                .create(any());
        assertThatThrownBy(() -> start.start(42, request))
                .isInstanceOf(ReservationOperationException.class);
        verify(holds, never()).confirm(any());
        assertThat(repository.find(request.requestId()).orElseThrow().isPending()).isTrue();
    }

    static final class Memory implements ReservationProcessRepository {
        final Map<UUID, ReservationProcess> values = new HashMap<>();

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
        public void accept(ReservationDelivery delivery) {
            throw new UnsupportedOperationException();
        }
    }
}

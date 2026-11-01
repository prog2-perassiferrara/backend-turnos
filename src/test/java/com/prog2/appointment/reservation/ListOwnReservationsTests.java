package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.ListOwnReservationsUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.*;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.*;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Recorrido del caso real con persistencia/proveedor controlados; no sustituye las pruebas SQL. */
class ListOwnReservationsTests {
    private final ReservationProcessRepository repository =
            mock(ReservationProcessRepository.class);
    private final CentralAppointmentGateway central = mock(CentralAppointmentGateway.class);
    private final ListOwnReservationsUseCaseImpl useCase =
            new ListOwnReservationsUseCaseImpl(
                    repository, central, Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC));
    private final AtomicReference<OwnReservationPage> stored = new AtomicReference<>();
    private final ReservationProcess waiting = SubmitReservationPhoneTests.waiting();

    @BeforeEach
    void setup() {
        stored.set(view(waiting));
        when(repository.readOwn(42, 0, 20)).thenAnswer(_ -> stored.get());
        when(central.listOwn(42))
                .thenReturn(new CentralAppointmentsBatch(List.of(), VerificationStatus.VERIFIED));
        lenient()
                .when(repository.reconcile(any(), any(), any()))
                .thenAnswer(invocation -> {
                    var before = stored.get().pending();
                    if (before == null) before = stored.get().history().getFirst();
                    var result =
                            before.reconcile(invocation.getArgument(1), invocation.getArgument(2));
                    stored.set(view(result));
                    return result;
                });
    }

    static OwnReservationPage view(ReservationProcess process) {
        return new OwnReservationPage(
                process.isPending() ? process : null,
                null,
                process.isPending() ? List.of() : List.of(process),
                process.isPending() ? 0 : 1,
                0,
                20);
    }

    static CentralAppointment evidence(
            ReservationProcess process, CentralAppointmentState state, ProfessionalName name) {
        return new CentralAppointment(
                process.hold().centralProcessId(),
                process.patient().externalPatientId(),
                process.selection(),
                8251,
                state,
                name);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101", "2147483647,100"})
    void invalidPageFailsBeforeStorageOrCentral(int page, int size) {
        assertThatThrownBy(() -> useCase.list(42, page, size))
                .isInstanceOfSatisfying(
                        ReservationOperationException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(ReservationFailure.INVALID_RESERVATION_PAGE));
        verifyNoInteractions(central);
        verify(repository, never()).readOwn(anyLong(), anyInt(), anyInt());
    }

    @Test
    void aCentralConfirmationMovesThePendingToHistoryWithoutDuplicatingIt() {
        var name = new ProfessionalName("Ana", "Gomez");
        when(central.listOwn(42))
                .thenReturn(
                        new CentralAppointmentsBatch(
                                List.of(evidence(waiting, CentralAppointmentState.CONFIRMED, name)),
                                VerificationStatus.VERIFIED));
        var result = useCase.list(42, 0, 20);
        assertThat(result.pending()).isNull();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.history())
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
                    assertThat(value.process().professionalName()).isEqualTo(name);
                    assertThat(value.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
                    assertThat(value.verifiedAt()).isEqualTo(ReservationProcessTests.NOW);
                });
        assertThat(result.warningCode()).isNull();
        verify(central).listOwn(42);
        verify(central, never()).find(any());
    }

    @Test
    void absenceDoesNotCloseAPendingOrHideItBehindAnEmptyHistory() {
        var result = useCase.list(42, 0, 20);
        assertThat(result.pending().process()).isEqualTo(waiting);
        assertThat(result.pending().canSubmitPhone()).isTrue();
        assertThat(result.history()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.INCONCLUSIVE);
        verify(repository, never()).reconcile(any(), any(), any());
    }

    @Test
    void centralReservationsWithoutLocalAssociationAreNotImported() {
        stored.set(new OwnReservationPage(null, null, List.of(), 0, 0, 20));
        when(central.listOwn(42))
                .thenReturn(
                        new CentralAppointmentsBatch(
                                List.of(evidence(waiting, CentralAppointmentState.CONFIRMED, null)),
                                VerificationStatus.VERIFIED));
        var result = useCase.list(42, 0, 20);
        assertThat(result.history()).isEmpty();
        assertThat(result.pending()).isNull();
        assertThat(result.totalElements()).isZero();
        verify(repository, never()).reconcile(any(), any(), any());
    }

    @Test
    void failureReturnsEvenAnEmptyLocalViewWithAnExplicitWarning() {
        stored.set(new OwnReservationPage(null, null, List.of(), 0, 0, 20));
        when(central.listOwn(42))
                .thenThrow(
                        new ReservationGatewayException(
                                ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false));
        var result = useCase.list(42, 0, 20);
        assertThat(result.history()).isEmpty();
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.UNAVAILABLE);
        assertThat(result.verifiedAt()).isNull();
        assertThat(result.warningCode()).isEqualTo("APPOINTMENT_STATUS_UNAVAILABLE");
    }

    @Test
    void partialEvidenceCanVerifyAnItemButNotTheCompleteResponse() {
        when(central.listOwn(42))
                .thenReturn(
                        new CentralAppointmentsBatch(
                                List.of(
                                        evidence(
                                                waiting,
                                                CentralAppointmentState.PHONE_PENDING,
                                                null)),
                                VerificationStatus.INCONCLUSIVE));
        var result = useCase.list(42, 0, 20);
        assertThat(result.pending().verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.INCONCLUSIVE);
        assertThat(result.verifiedAt()).isNull();
    }

    @Test
    void aConcurrentCancellationWinsOverAnOlderConfirmation() {
        var confirmed =
                waiting.reconcile(
                        evidence(waiting, CentralAppointmentState.CONFIRMED, null),
                        ReservationProcessTests.NOW);
        var cancelled =
                confirmed.reconcile(
                        evidence(confirmed, CentralAppointmentState.CANCELLED, null),
                        ReservationProcessTests.NOW);
        when(central.listOwn(42))
                .thenAnswer(_ -> {
                    stored.set(view(cancelled));
                    return new CentralAppointmentsBatch(
                            List.of(evidence(waiting, CentralAppointmentState.CONFIRMED, null)),
                            VerificationStatus.VERIFIED);
                });
        var result = useCase.list(42, 0, 20);
        assertThat(result.pending()).isNull();
        assertThat(result.history().getFirst().process().phase())
                .isEqualTo(ReservationPhase.CANCELLED);
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.INCONCLUSIVE);
    }

    @Test
    void conflictingEvidenceAndForeignPatientCannotConfirmTheProcess() {
        var own = evidence(waiting, CentralAppointmentState.CONFIRMED, null);
        var other =
                new CentralAppointment(
                        own.centralProcessId(),
                        "user-43",
                        own.selection(),
                        8251,
                        own.state(),
                        null);
        when(central.listOwn(42))
                .thenReturn(
                        new CentralAppointmentsBatch(List.of(other), VerificationStatus.VERIFIED));
        assertThat(useCase.list(42, 0, 20).pending().process()).isEqualTo(waiting);
        when(central.listOwn(42))
                .thenReturn(
                        new CentralAppointmentsBatch(
                                List.of(
                                        own,
                                        evidence(waiting, CentralAppointmentState.CANCELLED, null)),
                                VerificationStatus.INCONCLUSIVE));
        assertThat(useCase.list(42, 0, 20).pending().process()).isEqualTo(waiting);
        verify(repository, never()).reconcile(any(), any(), any());
    }

    @Test
    void anUnreadableOrForeignLocalViewIsNeverReportedAsAnEmptySuccess() {
        when(repository.readOwn(42, 0, 20)).thenThrow(new ReservationStorageException());
        assertThatThrownBy(() -> useCase.list(42, 0, 20))
                .isInstanceOf(ReservationStorageException.class);
        verifyNoInteractions(central);
        when(repository.readOwn(41, 0, 20)).thenReturn(view(waiting));
        assertThatThrownBy(() -> useCase.list(41, 0, 20))
                .isInstanceOf(ReservationStorageException.class);
    }

    @Test
    void namesSurviveEveryLaterTransitionAndCannotBeReplacedOrInvented() {
        var name = new ProfessionalName("Ana", "Gomez");
        var confirmed =
                waiting.reconcile(
                        evidence(waiting, CentralAppointmentState.CONFIRMED, name),
                        ReservationProcessTests.NOW);
        var changedName = new ProfessionalName("Otra", "Persona");
        var cancelled =
                confirmed.reconcile(
                        evidence(confirmed, CentralAppointmentState.CANCELLED, changedName),
                        ReservationProcessTests.NOW);
        assertThat(cancelled.professionalName()).isEqualTo(name);
        assertThat(
                        cancelled.reconcile(
                                evidence(cancelled, CentralAppointmentState.CONFIRMED, null),
                                ReservationProcessTests.NOW))
                .isSameAs(cancelled);
        assertThat(ProfessionalName.from(null, "Gomez")).isNull();
        assertThat(ProfessionalName.from("", "Gomez")).isNull();
        assertThat(ProfessionalName.from("Ana", " ")).isNull();
        assertThat(ProfessionalName.from("A".repeat(256), "Gomez")).isNull();
        var finalWithoutName =
                waiting.reconcile(
                        evidence(waiting, CentralAppointmentState.CONFIRMED, null),
                        ReservationProcessTests.NOW);
        var enriched =
                finalWithoutName.reconcile(
                        evidence(finalWithoutName, CentralAppointmentState.CONFIRMED, name),
                        ReservationProcessTests.NOW.plusSeconds(10));
        assertThat(enriched.professionalName()).isEqualTo(name);
        assertThat(enriched.closedAt()).isEqualTo(finalWithoutName.closedAt());
    }

    @ParameterizedTest
    @CsvSource({"CANCELLED,CANCELLED", "FAILED,FAILED"})
    void verifiedTerminalEvidenceIsReflectedWithoutInventingAnotherProcess(
            String centralState, String expected) {
        when(central.listOwn(42))
                .thenReturn(
                        new CentralAppointmentsBatch(
                                List.of(
                                        evidence(
                                                waiting,
                                                CentralAppointmentState.valueOf(centralState),
                                                null)),
                                VerificationStatus.VERIFIED));
        var result = useCase.list(42, 0, 20);
        assertThat(result.pending()).isNull();
        assertThat(result.history())
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.process().phase().name()).isEqualTo(expected);
                    assertThat(value.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
                });
        assertThat(result.totalElements()).isEqualTo(1);
    }
}

package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.GetReservationProcessUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

/** Lectura observable: incertidumbre explícita y resultado concurrente prevaleciente. */
class GetReservationProcessTests {
    private final ReservationProcessRepository repository =
            mock(ReservationProcessRepository.class);
    private final CentralAppointmentGateway central = mock(CentralAppointmentGateway.class);
    private final ReservationProcess process = SubmitReservationPhoneTests.waiting();

    private GetReservationProcessUseCaseImpl useCase(long seconds) {
        return new GetReservationProcessUseCaseImpl(
                repository,
                central,
                Clock.fixed(ReservationProcessTests.NOW.plusSeconds(seconds), ZoneOffset.UTC),
                Duration.ofSeconds(30));
    }

    @Test
    void normalAndHistoricalReadsAvoidCentralAndForeignReadsRevealNothing() {
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        assertThat(useCase(0).get(process.ownerId(), process.id()).verificationStatus())
                .isEqualTo(VerificationStatus.LOCAL);
        var reader = useCase(0);
        long foreignOwner = process.ownerId() + 1;
        var processId = process.id();
        assertThatThrownBy(() -> reader.get(foreignOwner, processId))
                .isInstanceOf(ReservationOperationException.class);
        verifyNoInteractions(central);
    }

    @Test
    void delayedResultUsesThirtySecondBoundaryAndRereadsConcurrentKafkaResult() {
        var phone = SubmitReservationPhoneTests.submission().received(ReservationProcessTests.NOW);
        var waiting = process.queuePhone().phonePublished();
        when(repository.find(process.id())).thenReturn(Optional.of(waiting));
        when(repository.latestPhone(process.id())).thenReturn(Optional.of(phone));
        useCase(29).get(process.ownerId(), process.id());
        verifyNoInteractions(central);
        var confirmed =
                waiting.apply(
                        ReservationProcessTests.action(
                                ReservationActionType.CONFIRMED, "confirmation-1"),
                        ReservationProcessTests.NOW.plusSeconds(30));
        when(central.find(waiting))
                .thenAnswer(
                        _ -> {
                            when(repository.find(process.id())).thenReturn(Optional.of(confirmed));
                            return Optional.empty();
                        });
        var status = useCase(30).get(process.ownerId(), process.id());
        assertThat(status.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(status.verificationStatus()).isEqualTo(VerificationStatus.INCONCLUSIVE);
        assertThat(status.canSubmitPhone()).isFalse();
        verify(central, times(1)).find(waiting);
    }

    @Test
    void failedOrMissingEvidenceNeverClosesTheLocalProcess() {
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        when(central.find(process))
                .thenThrow(
                        new ReservationGatewayException(
                                ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false));
        var status = useCase(300).get(process.ownerId(), process.id());
        assertThat(status.process()).isEqualTo(process);
        assertThat(status.canSubmitPhone()).isFalse();
        assertThat(status.verificationStatus()).isEqualTo(VerificationStatus.UNAVAILABLE);
        verify(repository, never()).reconcile(any(), any(), any());
    }

    @Test
    void exactVerifiedEvidenceUsesTheCurrentRepositoryVersion() {
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        var evidence =
                new CentralAppointment(
                        process.hold().centralProcessId(),
                        process.patient().externalPatientId(),
                        process.selection(),
                        8251,
                        CentralAppointmentState.CONFIRMED,
                        null);
        when(central.find(process)).thenReturn(Optional.of(evidence));
        when(repository.reconcile(eq(process.id()), eq(evidence), any()))
                .thenAnswer(
                        _ -> {
                            var confirmed =
                                    process.reconcile(
                                            evidence, ReservationProcessTests.NOW.plusSeconds(300));
                            when(repository.find(process.id())).thenReturn(Optional.of(confirmed));
                            return confirmed;
                        });
        var status = useCase(300).get(process.ownerId(), process.id());
        assertThat(status.process().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(status.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(status.verifiedAt()).isEqualTo(ReservationProcessTests.NOW.plusSeconds(300));
    }
}

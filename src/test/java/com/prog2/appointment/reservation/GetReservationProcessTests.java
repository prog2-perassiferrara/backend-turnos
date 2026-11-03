package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.GetReservationProcessUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Lecturas locales autorizadas: el reloj no reemplaza la evidencia central. */
class GetReservationProcessTests {
    private final ReservationProcessRepository repository = mock(ReservationProcessRepository.class);
    private final ReservationProcess process = SubmitReservationPhoneTests.waiting();
    private GetReservationProcessUseCaseImpl reader(long seconds) {
        return new GetReservationProcessUseCaseImpl(repository,
                Clock.fixed(ReservationProcessTests.NOW.plusSeconds(seconds), ZoneOffset.UTC));
    }

    @Test
    void readsTheDurableProcessAndRejectsForeignOrMissingIdentifiers() {
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        assertThat(reader(0).get(process.ownerId(), process.id()).process()).isEqualTo(process);
        var reader = reader(0);
        var id = process.id();
        assertThatThrownBy(() -> reader.get(99, id)).isInstanceOf(ReservationOperationException.class);
        var missing = UUID.randomUUID();
        assertThatThrownBy(() -> reader.get(42, missing)).isInstanceOf(ReservationOperationException.class);
        verify(repository, never()).reconcile(any(), any(), any());
    }

    @Test
    void aLateReadDisablesPhoneWithoutInventingFailureOrExpiry() {
        when(repository.find(process.id())).thenReturn(Optional.of(process));
        var result = reader(3600).get(42, process.id());
        assertThat(result.process().phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(result.canSubmitPhone()).isFalse();
        assertThat(result.warningCode()).isNull();
        verify(repository, never()).update(any(), any());
    }

    @Test
    void aPersistedCancellationCannotBeReopenedByOlderConfirmationEvidence() {
        var evidence = ListOwnReservationsTests.evidence(process, CentralAppointmentState.CONFIRMED, null);
        var cancelled = process.reconcile(evidence, ReservationProcessTests.NOW)
                .apply(ReservationProcessTests.action(ReservationActionType.CANCELLED, "cancelled"), ReservationProcessTests.NOW);
        assertThat(cancelled.reconcile(evidence, ReservationProcessTests.NOW)).isEqualTo(cancelled);
        when(repository.find(process.id())).thenReturn(Optional.of(cancelled));
        assertThat(reader(3600).get(42, process.id()).process().phase()).isEqualTo(ReservationPhase.CANCELLED);
        verify(repository, never()).reconcile(any(), any(), any());
    }
}

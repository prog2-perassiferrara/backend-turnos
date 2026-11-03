package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.application.usecases.ListOwnReservationsUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pendiente e historial salen de una lectura SQL propia, sin certificación remota. */
class ListOwnReservationsTests {
    private final ReservationProcessRepository repository = mock(ReservationProcessRepository.class);
    private final ListOwnReservationsUseCaseImpl useCase = new ListOwnReservationsUseCaseImpl(
            repository, Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC));
    private final ReservationProcess waiting = SubmitReservationPhoneTests.waiting();

    static OwnReservationPage view(ReservationProcess process) {
        return new OwnReservationPage(process.isPending() ? process : null, null,
                process.isPending() ? List.of() : List.of(process), process.isPending() ? 0 : 1, 0, 20);
    }
    static CentralAppointment evidence(ReservationProcess process, CentralAppointmentState state, ProfessionalName name) {
        return new CentralAppointment(process.hold().centralProcessId(), process.patient().externalPatientId(),
                process.selection(), 8251, state, name);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101", "2147483647,100"})
    void invalidPageFailsBeforeStorage(int page, int size) {
        assertThatThrownBy(() -> useCase.list(42, page, size)).isInstanceOf(ReservationOperationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void readsThePendingSeparatelyAndPreservesTheLocalHistoryAndTotal() {
        when(repository.readOwn(42, 0, 20)).thenReturn(view(waiting));
        var result = useCase.list(42, 0, 20);
        assertThat(result.pending().process()).isEqualTo(waiting);
        assertThat(result.history()).isEmpty();
        assertThat(result.totalElements()).isZero();
        var confirmed = waiting.reconcile(evidence(waiting, CentralAppointmentState.CONFIRMED,
                new ProfessionalName("Ana", "Gomez")), ReservationProcessTests.NOW);
        when(repository.readOwn(42, 0, 20)).thenReturn(view(confirmed));
        var history = useCase.list(42, 0, 20);
        assertThat(history.pending()).isNull();
        assertThat(history.history()).extracting(value -> value.process().professionalName())
                .containsExactly(new ProfessionalName("Ana", "Gomez"));
        assertThat(history.totalElements()).isEqualTo(1);
        verify(repository, never()).reconcile(any(), any(), any());
    }

    @Test
    void foreignDataFromStorageIsNeverReturnedAsOwned() {
        when(repository.readOwn(43, 0, 20)).thenReturn(view(waiting));
        assertThatThrownBy(() -> useCase.list(43, 0, 20)).isInstanceOf(ReservationStorageException.class);
    }
}

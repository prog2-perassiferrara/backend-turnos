package com.prog2.appointment.reservation;

import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationProcessesUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationMessagingReadiness;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.infrastructure.scheduling.ReservationRecoveryDispatcher;
import org.junit.jupiter.api.Test;

/** El ciclo técnico sigue solicitando preparación tras fallos; el canal limita sus intentos. */
class ReservationRecoveryDispatcherTests {
    @Test
    void eachReadyCycleRequestsPreparationEvenWhenRecoveryStorageFails() {
        var recover = mock(RecoverReservationProcessesUseCase.class);
        var messaging = mock(ReservationMessagingReadiness.class);
        var dispatcher = new ReservationRecoveryDispatcher(recover, messaging);
        dispatcher.dispatch();
        verifyNoInteractions(recover, messaging);
        dispatcher.applicationReady();
        doThrow(new ReservationStorageException()).doNothing().when(recover).recoverDue();
        dispatcher.dispatch();
        dispatcher.dispatch();
        verify(messaging, times(2)).requestPreparation();
        verify(recover, times(2)).recoverDue();
    }
}

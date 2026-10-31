package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.application.usecases.PublishPendingPhonesUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.ports.out.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Fronteras controladas: ninguna publicación antes de registrar la intención durable. */
class PublishPendingPhonesTests {
    private final ReservationProcessRepository repository =
            mock(ReservationProcessRepository.class);
    private final PhonePublicationGateway gateway = mock(PhonePublicationGateway.class);
    private final Clock clock = Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC);
    private final PublishPendingPhonesUseCaseImpl useCase =
            new PublishPendingPhonesUseCaseImpl(
                    repository, gateway, clock, 3, Duration.ofSeconds(5), Duration.ofSeconds(30));

    @Test
    void uncertainPersistenceOrAClosedRowPreventsSending() {
        var phone = SubmitReservationPhoneTests.submission();
        when(repository.duePhones(clock.instant(), 10)).thenReturn(List.of(phone.id()));
        when(repository.beginPhonePublication(eq(phone.id()), any(), eq(3), any(), any()))
                .thenThrow(new ReservationStorageException());
        useCase.publishPending();
        verifyNoInteractions(gateway);
        when(repository.beginPhonePublication(eq(phone.id()), any(), eq(3), any(), any()))
                .thenReturn(Optional.empty());
        useCase.publishPending();
        verifyNoInteractions(gateway);
    }

    @Test
    void failuresNeverAcknowledgeOrReplaceTheDurableIdentity() {
        var phone = SubmitReservationPhoneTests.submission();
        when(repository.duePhones(clock.instant(), 10)).thenReturn(List.of(phone.id()));
        when(repository.beginPhonePublication(eq(phone.id()), any(), eq(3), any(), any()))
                .thenReturn(Optional.of(phone));
        doThrow(
                        new ReservationGatewayException(
                                ReservationFailure.PHONE_PUBLICATION_UNCERTAIN, true, false))
                .when(gateway)
                .publish(phone);
        useCase.publishPending();
        verify(repository, never()).phonePublished(any(), any());
        doNothing().when(gateway).publish(phone);
        useCase.publishPending();
        var ordered = inOrder(repository, gateway);
        ordered.verify(repository).duePhones(clock.instant(), 10);
        ordered.verify(repository)
                .beginPhonePublication(eq(phone.id()), any(), eq(3), any(), any());
        ordered.verify(gateway).publish(phone);
        ordered.verify(repository).duePhones(clock.instant(), 10);
        ordered.verify(repository)
                .beginPhonePublication(eq(phone.id()), any(), eq(3), any(), any());
        ordered.verify(gateway).publish(phone);
        ordered.verify(repository).phonePublished(phone.id(), clock.instant());
    }
}

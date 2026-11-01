package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.infrastructure.messaging.KafkaPhoneSubmissionAdapter;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationKafkaChannel;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

/** Guardas anteriores al transporte; el contrato y ACK reales se prueban con Kafka temporal. */
class KafkaPhoneSubmissionTests {
    private final ReservationKafkaChannel channel = mock(ReservationKafkaChannel.class);

    @Test
    void expiryAndExplicitClosurePreventAccessToTheTransport() {
        var phone = SubmitReservationPhoneTests.submission();
        try (var producer =
                new KafkaPhoneSubmissionAdapter(
                        channel,
                        tools.jackson.databind.json.JsonMapper.builder()
                                .findAndAddModules()
                                .build(),
                        Clock.fixed(phone.expiresAt(), ZoneOffset.UTC),
                        Duration.ofMillis(100))) {
            assertThatThrownBy(() -> producer.publish(phone))
                    .isInstanceOf(ReservationGatewayException.class);
        }
        verifyNoInteractions(channel);
    }

    @Test
    void anUnpreparedOrForeignDestinationCannotPublish() {
        var phone = SubmitReservationPhoneTests.submission();
        when(channel.phoneDestination())
                .thenReturn(
                        new ReservationKafkaChannel.PhoneDestination(
                                "fictional:9092", "fictional-phone", "foreign-group"));
        try (var producer =
                new KafkaPhoneSubmissionAdapter(
                        channel,
                        tools.jackson.databind.json.JsonMapper.builder()
                                .findAndAddModules()
                                .build(),
                        Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC),
                        Duration.ofMillis(100))) {
            assertThatThrownBy(() -> producer.publish(phone))
                    .isInstanceOf(ReservationGatewayException.class);
        }
    }

    @Test
    void aClosedPublisherDoesNotAcquireOrRecreateResources() {
        var producer =
                new KafkaPhoneSubmissionAdapter(
                        channel,
                        tools.jackson.databind.json.JsonMapper.builder()
                                .findAndAddModules()
                                .build(),
                        Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC),
                        Duration.ofMillis(100));
        producer.close();
        var phone = SubmitReservationPhoneTests.submission();
        assertThatThrownBy(() -> producer.publish(phone))
                .isInstanceOf(ReservationGatewayException.class);
        verifyNoInteractions(channel);
    }
}

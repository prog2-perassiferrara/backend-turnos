package com.prog2.appointment.reservation.infrastructure.messaging;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.ports.out.PhonePublicationGateway;
import com.prog2.appointment.reservation.infrastructure.messaging.dto.PhoneSubmissionEvent;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;

import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Productor reutilizable con espera acotada; su ACK acredita recepción, nunca confirmación. */
public final class KafkaPhoneSubmissionAdapter implements PhonePublicationGateway, AutoCloseable {
    private final ReservationKafkaChannel channel;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration timeout;
    private DefaultKafkaProducerFactory<String, String> factory;
    private KafkaTemplate<String, String> template;
    private ReservationKafkaChannel.PhoneDestination destination;
    private boolean closed;

    public KafkaPhoneSubmissionAdapter(
            ReservationKafkaChannel channel, JsonMapper json, Clock clock, Duration timeout) {
        this.channel = channel;
        this.json = json;
        this.clock = clock;
        this.timeout = timeout;
    }

    @Override
    public synchronized void publish(PhoneSubmission phone) {
        if (closed || phone.closedAt() != null || !clock.instant().isBefore(phone.expiresAt()))
            throw unavailable();
        try {
            var assigned = channel.phoneDestination();
            if (!assigned.groupId().equals(phone.event().groupId())) throw unavailable();
            verifyTopic(assigned);
            prepare(assigned);
            String payload = json.writeValueAsString(PhoneSubmissionEvent.from(phone));
            if (!clock.instant().isBefore(phone.expiresAt())) throw unavailable();
            template.send(assigned.topic(), phone.event().centralProcessId(), payload)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException | TimeoutException | RuntimeException _) {
            throw unavailable();
        }
    }

    private void prepare(ReservationKafkaChannel.PhoneDestination assigned) {
        if (assigned.equals(destination)) return;
        if (factory != null) factory.destroy();
        var settings = new HashMap<String, Object>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, assigned.bootstrap());
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        settings.put(ProducerConfig.RETRIES_CONFIG, 0);
        settings.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        settings.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        settings.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, metadataBudgetMillis());
        settings.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.toIntExact(timeout.toMillis()));
        settings.put(
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Math.toIntExact(timeout.toMillis()));
        factory =
                new DefaultKafkaProducerFactory<>(
                        settings, new StringSerializer(), new StringSerializer());
        factory.setPhysicalCloseTimeout(0);
        template = new KafkaTemplate<>(factory);
        // El listener predeterminado incluye key y payload en fallos: no registrar datos privados.
        template.setProducerListener(new ProducerListener<>() {});
        destination = assigned;
    }

    private long metadataBudgetMillis() {
        return Math.max(1, timeout.toMillis() / 2);
    }

    /**
     * Describe sin autocreación antes de send: un destino ausente no debe provisionarse por el
     * productor. Metadata y bloqueo de send comparten el primer presupuesto; el ACK usa el segundo.
     */
    private void verifyTopic(ReservationKafkaChannel.PhoneDestination assigned)
            throws ExecutionException, TimeoutException, InterruptedException {
        long budget = metadataBudgetMillis();
        var admin =
                Admin.create(
                        Map.of(
                                "bootstrap.servers",
                                assigned.bootstrap(),
                                "default.api.timeout.ms",
                                Math.toIntExact(budget),
                                "request.timeout.ms",
                                Math.toIntExact(budget),
                                "retries",
                                0));
        try {
            var descriptions =
                    admin.describeTopics(List.of(assigned.topic()))
                            .allTopicNames()
                            .get(budget, TimeUnit.MILLISECONDS);
            if (descriptions.get(assigned.topic()).partitions().isEmpty()) throw unavailable();
        } finally {
            admin.close(Duration.ZERO);
        }
    }

    private static ReservationGatewayException unavailable() {
        return new ReservationGatewayException(
                ReservationFailure.PHONE_PUBLICATION_UNCERTAIN, true, false);
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (factory != null) factory.destroy();
    }
}

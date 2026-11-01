package com.prog2.appointment.reservation.infrastructure.messaging;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;
import com.prog2.appointment.infrastructure.config.ReservationProperties;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.domain.ports.in.ReceiveReservationActionUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationMessagingReadiness;

/**
 * Consumer propio con configuración asignada. Preparación acotada y coordinada, sin confirmar antes
 * de MySQL. El silencio del topic no se utiliza como criterio de fallo ni como confirmación
 * funcional.
 */
public final class ReservationKafkaChannel implements ReservationMessagingReadiness, AutoCloseable {
    private final TechnicalAccessUseCase access;
    private final ReceiveReservationActionUseCase receive;
    private final ReservationActionParser parser;
    private final ReservationProperties properties;
    private final ExecutorService executor;
    // Referencia y configuración protegidas por el monitor de esta instancia.
    private KafkaMessageListenerContainer<String, String> container;
    private String bootstrap;
    private String topic;
    private PhoneDestination phoneDestination;
    private boolean preparing;
    private boolean closed;
    private long nextPreparation = System.nanoTime();

    public ReservationKafkaChannel(
            TechnicalAccessUseCase access,
            ReceiveReservationActionUseCase receive,
            ReservationActionParser parser,
            ReservationProperties properties,
            ExecutorService executor) {
        this.access = access;
        this.receive = receive;
        this.parser = parser;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public synchronized void requestPreparation() {
        if (closed || preparing || isRunning() || System.nanoTime() - nextPreparation < 0) {
            return;
        }
        preparing = true;
        try {
            executor.execute(this::prepare);
        } catch (RejectedExecutionException _) {
            preparing = false;
        }
    }

    private void prepare() {
        boolean ready = false;
        try {
            KafkaMessageListenerContainer<String, String> previous;
            synchronized (this) {
                previous = container;
            }
            if (previous != null) {
                previous.stop();
            }
            for (int attempt = 0; attempt < properties.attempts() && !isClosed(); attempt++) {
                if (tryPreparation()) {
                    ready = true;
                    return;
                }
                if (attempt + 1 < properties.attempts() && !isClosed()) {
                    Thread.sleep(properties.retryDelay());
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } finally {
            finishPreparation(ready);
        }
    }

    private boolean tryPreparation() {
        try {
            var assigned = access.getAccess().configuration();
            verifyMetadata(
                    assigned.kafkaBootstrapServers(),
                    List.of(assigned.kafkaAppointmentActionsTopic(), assigned.kafkaAppointmentPhoneTopic()));
            start(
                    assigned.kafkaBootstrapServers(),
                    assigned.kafkaAppointmentActionsTopic(),
                    assigned.kafkaConsumerGroupId(),
                    assigned.kafkaAppointmentPhoneTopic(),
                    assigned.groupId());
            return true;
        } catch (RuntimeException _) {
            return false;
        }
    }

    private synchronized void finishPreparation(boolean ready) {
        preparing = false;
        nextPreparation =
                System.nanoTime() + (ready ? 0 : properties.preparationCooldown().toNanos());
    }

    private synchronized boolean isClosed() {
        return closed;
    }

    private synchronized boolean isRunning() {
        var current = container;
        return current != null && current.isRunning();
    }

    private synchronized void start(
            String servers, String actions, String group, String phoneTopic, String groupId) {
        if (closed) {
            return;
        }
        var settings =
                Map.<String, Object>of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        servers,
                        ConsumerConfig.GROUP_ID_CONFIG,
                        group,
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                        false,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest",
                        ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG,
                        false);
        var factory =
                new DefaultKafkaConsumerFactory<>(
                        settings, new StringDeserializer(), new StringDeserializer());
        var options = new ContainerProperties(actions);
        options.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        options.setSyncCommits(true);
        options.setPollTimeout(1000);
        options.setMessageListener(
                (AcknowledgingMessageListener<String, String>)
                        (message, ack) -> {
                            var origin =
                                    new ReservationDelivery.Origin(
                                            message.topic(),
                                            message.partition(),
                                            message.offset(),
                                            message.key());
                            receive.receive(parser.parse(origin, message.value()));
                            ack.acknowledge();
                        });
        var next = new KafkaMessageListenerContainer<>(factory, options);
        var handler =
                new DefaultErrorHandler(
                        (message, failure) -> next.stop(() -> {}),
                        new FixedBackOff(
                                properties.retryDelay().toMillis(), properties.attempts() - 1L));
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(false);
        next.setCommonErrorHandler(handler);
        next.setBeanName("reservation-actions");
        phoneDestination = new PhoneDestination(servers, phoneTopic, groupId);
        bootstrap = servers;
        topic = actions;
        container = next;
        next.start();
    }

    @Override
    public void requireReady() {
        requestPreparation();
        KafkaMessageListenerContainer<String, String> current;
        String servers;
        String actions;
        String phones;
        synchronized (this) {
            if (closed) {
                throw unavailable();
            }
            current = container;
            servers = bootstrap;
            actions = topic;
            phones = phoneDestination == null ? null : phoneDestination.topic();
        }
        var partitions = current == null ? null : current.getAssignedPartitions();
        if (current == null
                || !current.isRunning()
                || partitions == null
                || partitions.stream().noneMatch(value -> value.topic().equals(actions))) {
            throw unavailable();
        }
        try {
            verifyMetadata(servers, List.of(actions, phones));
        } catch (RuntimeException _) {
            throw unavailable();
        }
    }

    private void verifyMetadata(String servers, List<String> topics) {
        var settings =
                Map.<String, Object>of(
                        "bootstrap.servers",
                        servers,
                        "default.api.timeout.ms",
                        Math.toIntExact(properties.metadataTimeout().toMillis()),
                        "request.timeout.ms",
                        Math.toIntExact(properties.metadataTimeout().toMillis()),
                        "retries",
                        0);
        var admin = Admin.create(settings);
        try {
            var description =
                    admin.describeTopics(topics)
                            .allTopicNames()
                            .get(properties.metadataTimeout().toMillis(), TimeUnit.MILLISECONDS);
            for (String assigned : topics) {
                if (description.get(assigned).partitions().isEmpty()) {
                    throw unavailable();
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException _) {
            throw unavailable();
        } finally {
            admin.close(Duration.ZERO);
        }
    }

    /** Snapshot asignado retenido: publicar no exige volver a obtener el JWT REST. */
    public synchronized PhoneDestination phoneDestination() {
        if (closed || phoneDestination == null) throw unavailable();
        return phoneDestination;
    }

    /** Datos mínimos del destino; no incluye credenciales ni referencias al consumidor. */
    public record PhoneDestination(String bootstrap, String topic, String groupId) {}

    private static ReservationGatewayException unavailable() {
        return new ReservationGatewayException(
                ReservationFailure.MESSAGING_NOT_READY, false, false);
    }

    /**
     * Cierra la admisión antes de cancelar tareas/consumo; ningún arranque tardío publica recursos.
     */
    @Override
    public void close() {
        KafkaMessageListenerContainer<String, String> current;
        synchronized (this) {
            closed = true;
            current = container;
        }
        executor.shutdownNow();
        if (current != null) {
            current.stop();
        }
    }
}

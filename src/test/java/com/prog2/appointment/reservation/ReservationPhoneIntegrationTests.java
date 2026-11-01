package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.reservation.application.usecases.PublishPendingPhonesUseCaseImpl;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.*;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.*;
import com.prog2.appointment.reservation.infrastructure.messaging.*;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** SQL, HTTP y ambos topics reales temporales; solo los proveedores externos son ficticios. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReservationPhoneIntegrationTests.Settings.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@Timeout(60)
class ReservationPhoneIntegrationTests {
    @Container
    static final org.testcontainers.mysql.MySQLContainer MYSQL =
            new org.testcontainers.mysql.MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("phone_test")
                    .withUsername("phone_test")
                    .withPassword("fictional-database");

    @Container
    static final org.testcontainers.kafka.KafkaContainer KAFKA =
            new org.testcontainers.kafka.KafkaContainer("apache/kafka-native:3.8.0");

    static final ReservationTestSupport PROVIDERS =
            new ReservationTestSupport(KAFKA::getBootstrapServers);
    static final String PASSWORD = "fictional-phone-password";
    static final AtomicBoolean TOPICS_CREATED = new AtomicBoolean();
    private final int port;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final ReservationProcessRepository repository;
    private final ReservationKafkaChannel channel;
    private final CommitFaultDataSource faults;
    private final ControlledPublisher publisher;
    private final HttpClient http = HttpClient.newHttpClient();
    private String token;
    private long owner;
    private long lastSent = -1;

    ReservationPhoneIntegrationTests(
            @LocalServerPort int port,
            JsonMapper json,
            JdbcTemplate jdbc,
            ReservationProcessRepository repository,
            ReservationKafkaChannel channel,
            CommitFaultDataSource faults,
            ControlledPublisher publisher) {
        this.port = port;
        this.json = json;
        this.jdbc = jdbc;
        this.repository = repository;
        this.channel = channel;
        this.faults = faults;
        this.publisher = publisher;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) throws Exception {
        if (TOPICS_CREATED.compareAndSet(false, true)) {
            try (var admin =
                    Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                admin.createTopics(
                                List.of(
                                        new NewTopic(ReservationTestSupport.TOPIC, 1, (short) 1),
                                        new NewTopic(
                                                ReservationTestSupport.PHONE_TOPIC, 1, (short) 1)))
                        .all()
                        .get(15, TimeUnit.SECONDS);
            }
        }
        PROVIDERS.settings().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("RESERVATION_METADATA_TIMEOUT", () -> "1s");
        registry.add("RESERVATION_PHONE_PUBLISH_TIMEOUT", () -> "300ms");
        registry.add("RESERVATION_RETRY_DELAY", () -> "100ms");
        registry.add("RESERVATION_PREPARATION_COOLDOWN", () -> "400ms");
    }

    @BeforeEach
    void setup() throws Exception {
        faults.clear();
        publisher.enabled.set(false);
        publisher.before.set(_ -> {});
        publisher.after.set(_ -> {});
        jdbc.update(
                "UPDATE reservation_phone_submission SET closed_at = CURRENT_TIMESTAMP(6),"
                        + " next_attempt_at = NULL WHERE closed_at IS NULL");
        PROVIDERS.reset();
        lastSent = -1;
        await().atMost(Duration.ofSeconds(25))
                .untilAsserted(
                        () -> assertThatCode(channel::requireReady).doesNotThrowAnyException());
        String login = "phone_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        token = account(login);
        owner = jdbc.queryForObject("SELECT id FROM app_user WHERE login=?", Long.class, login);
    }

    @AfterEach
    void cleanup() {
        faults.clear();
        publisher.enabled.set(false);
        publisher.before.set(_ -> {});
        publisher.after.set(_ -> {});
        if (lastSent >= 0)
            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
        http.close();
    }

    @AfterAll
    static void closeProviders() {
        PROVIDERS.close();
    }

    private String account(String login) throws IOException, InterruptedException {
        var profile =
                Map.of(
                        "login",
                        login,
                        "password",
                        PASSWORD,
                        "firstName",
                        "Paciente",
                        "lastName",
                        "Ficticio",
                        "email",
                        login + "@example.test");
        assertThat(post("/api/register", json.writeValueAsString(profile), null).statusCode())
                .isEqualTo(201);
        var loginResponse =
                post(
                        "/api/authenticate",
                        json.writeValueAsString(Map.of("username", login, "password", PASSWORD)),
                        null);
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        return body(loginResponse).path("id_token").asString();
    }

    private HttpResponse<String> post(String path, String data, String bearer)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(data));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(UUID id, String bearer)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/api/reservation-processes/"
                                                + id))
                        .timeout(Duration.ofSeconds(15))
                        .GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private HttpResponse<String> phone(UUID id, UUID request, String number)
            throws IOException, InterruptedException {
        return post(
                "/api/reservation-processes/" + id + "/phone",
                json.writeValueAsString(Map.of("requestId", request, "phoneNumber", number)),
                token);
    }

    private UUID waiting() throws IOException, InterruptedException {
        UUID id = UUID.randomUUID();
        var response =
                post(
                        "/api/reservation-processes",
                        json.writeValueAsString(
                                Map.of(
                                        "requestId",
                                        id,
                                        "professionalId",
                                        15,
                                        "date",
                                        "2026-11-02",
                                        "startTime",
                                        "09:00")),
                        token);
        assertThat(response.statusCode()).isEqualTo(202);
        send(
                PROVIDERS.last.get().process(),
                ReservationTestSupport.event(
                        PROVIDERS.last.get(), "AdditionalInformationRequested", "request-1-" + id));
        phase(id, ReservationPhase.WAITING_FOR_PHONE);
        return id;
    }

    private void phase(UUID id, ReservationPhase expected) {
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(repository.find(id).orElseThrow().phase())
                                        .isEqualTo(expected));
    }

    private void send(String key, String payload) {
        try (var producer =
                new KafkaProducer<String, String>(
                        Map.of("bootstrap.servers", KAFKA.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer())) {
            var sent =
                    producer.send(new ProducerRecord<>(ReservationTestSupport.TOPIC, key, payload))
                            .get(10, TimeUnit.SECONDS);
            lastSent = Math.max(lastSent, sent.offset());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private long committed() {
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var offset =
                    admin.listConsumerGroupOffsets(ReservationTestSupport.CONSUMER)
                            .partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS)
                            .get(new TopicPartition(ReservationTestSupport.TOPIC, 0));
            return offset == null ? 0 : offset.offset();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String result(UUID id, String type, String eventId) {
        var process = repository.find(id).orElseThrow();
        var value =
                (ObjectNode)
                        json.readTree(
                                ReservationTestSupport.event(PROVIDERS.last.get(), type, eventId));
        value.put("requestEventId", process.requestEventId());
        value.put("reservationId", 8251)
                .put("professionalId", 15)
                .put("date", "2026-11-02")
                .put("startTime", "09:00")
                .put("status", type.equals("AppointmentCancelled") ? "CANCELLED" : "CONFIRMED");
        return json.writeValueAsString(value);
    }

    private List<ConsumerRecord<String, String>> records(UUID eventId) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer =
                new KafkaConsumer<String, String>(
                        Map.of(
                                "bootstrap.servers",
                                KAFKA.getBootstrapServers(),
                                "group.id",
                                "observer-" + UUID.randomUUID(),
                                "enable.auto.commit",
                                false),
                        new StringDeserializer(),
                        new StringDeserializer())) {
            var partition = new TopicPartition(ReservationTestSupport.PHONE_TOPIC, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end =
                    consumer.endOffsets(List.of(partition), Duration.ofSeconds(5)).get(partition);
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (consumer.position(partition) < end && System.nanoTime() < deadline) {
                for (var item : consumer.poll(Duration.ofMillis(100))) {
                    if (json.readTree(item.value())
                            .path("eventId")
                            .asString()
                            .equals(eventId.toString())) records.add(item);
                }
            }
        }
        return records;
    }

    @Test
    void acceptsDurablyWithoutBrokerAndReplaysNormalizedContentWithoutAnotherRow(
            CapturedOutput output) throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        var accepted = phone(id, request, " +54 (9261) 555-12.34 ");
        assertThat(accepted.statusCode()).isEqualTo(202);
        assertThat(body(accepted).path("state").asString()).isEqualTo("PHONE_QUEUED");
        assertThat(accepted.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(body(accepted).path("canSubmitPhone").asBoolean()).isFalse();
        assertThat(phone(id, request, "+5492615551234").statusCode()).isEqualTo(200);
        assertThat(phone(id, request, "+5492615559999").statusCode()).isEqualTo(409);
        assertThat(phone(id, UUID.randomUUID(), "0001234").statusCode()).isEqualTo(409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM reservation_phone_submission WHERE"
                                        + " process_id=?",
                                Integer.class,
                                id.toString()))
                .isEqualTo(1);
        assertThat(records(request)).isEmpty();
        assertThat(output).doesNotContain("+5492615551234", PASSWORD, token, PROVIDERS.token);
    }

    @Test
    void authorizationAndValidationDoNotExposeForeignProcessesOrWritePhones() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        assertThat(get(id, null).statusCode()).isEqualTo(401);
        assertThat(post("/api/reservation-processes/" + id + "/phone", "{}", null).statusCode())
                .isEqualTo(401);
        String other = account("foreign_" + UUID.randomUUID().toString().substring(0, 10));
        assertThat(get(id, other).statusCode()).isEqualTo(404);
        assertThat(
                        post(
                                        "/api/reservation-processes/" + id + "/phone",
                                        json.writeValueAsString(
                                                Map.of(
                                                        "requestId",
                                                        request,
                                                        "phoneNumber",
                                                        "1234567")),
                                        other)
                                .statusCode())
                .isEqualTo(404);
        assertThat(body(get(UUID.randomUUID(), token)).path("code").asString())
                .isEqualTo(body(get(id, other)).path("code").asString());
        assertThat(phone(id, request, "123").statusCode()).isEqualTo(400);
        assertThat(post("/api/reservation-processes/not-a-uuid/phone", "{}", token).statusCode())
                .isEqualTo(400);
        assertThat(post("/api/reservation-processes/" + id + "/phone", "{}", token).statusCode())
                .isEqualTo(400);
        assertThat(repository.latestPhone(id)).isEmpty();
    }

    @Test
    void confirmsOnlyAfterCentralEventAndUsesTheV1Contract(CapturedOutput output) throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        assertThat(phone(id, request, "0001234").statusCode()).isEqualTo(202);
        publisher.enabled.set(true);
        phase(id, ReservationPhase.WAITING_FOR_RESULT);
        var sent = records(request);
        assertThat(sent).isNotEmpty();
        assertThat(sent.stream().map(ConsumerRecord::value).distinct()).hasSize(1);
        var message = sent.getFirst();
        var event = json.readTree(message.value());
        var process = repository.find(id).orElseThrow();
        assertThat(message.key()).isEqualTo(process.hold().centralProcessId());
        assertThat(message.headers()).isEmpty();
        assertThat(event.path("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
        assertThat(event.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(event.path("groupId").asString()).isEqualTo(ReservationTestSupport.GROUP);
        assertThat(event.path("holdId").asString()).isEqualTo(process.hold().holdId());
        assertThat(event.path("requestEventId").asString()).isEqualTo(process.requestEventId());
        assertThat(event.path("information").path("phoneNumber").asString()).isEqualTo("0001234");
        assertThat(Instant.parse(event.path("occurredAt").asString()))
                .isBetween(ReservationProcessTests.NOW, process.hold().expiresAt());
        send(
                process.hold().centralProcessId(),
                result(id, "AppointmentConfirmed", "confirmed-" + id));
        phase(id, ReservationPhase.CONFIRMED);
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNotNull();
        assertThat(body(get(id, token)).path("state").asString()).isEqualTo("CONFIRMED");
        assertThat(phone(id, request, "0001234").statusCode()).isEqualTo(200);
        assertThat(body(get(id, token)).path("canSubmitPhone").asBoolean()).isFalse();
        assertThat(PROVIDERS.appointmentQueries).hasValue(0);
        assertThat(output).doesNotContain("0001234", PASSWORD, token, PROVIDERS.token);
    }

    @Test
    void acceptanceRollbackIsNotReportedAsAcceptedAndCommitResponseLossIsRecovered()
            throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        faults.arm(
                CommitFaultDataSource.Mode.BEFORE, "insert into reservation_phone_submission", 1);
        assertThat(phone(id, request, "1234567").statusCode()).isEqualTo(503);
        assertThat(repository.findPhone(request)).isEmpty();
        phase(id, ReservationPhase.WAITING_FOR_PHONE);
        faults.arm(CommitFaultDataSource.Mode.AFTER, "insert into reservation_phone_submission", 1);
        assertThat(phone(id, request, "1234567").statusCode()).isEqualTo(200);
        assertThat(repository.findPhone(request)).isPresent();
        phase(id, ReservationPhase.PHONE_QUEUED);
    }

    @Test
    void concurrentDistinctIdsHaveOneWinnerAndEquivalentReplaysHaveOneRow() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                return phone(id, request, "1234567").statusCode();
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                return phone(id, UUID.randomUUID(), "7654321").statusCode();
                            });
            gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(202, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM reservation_phone_submission WHERE"
                                        + " process_id=?",
                                Integer.class,
                                id.toString()))
                .isEqualTo(1);
    }

    @Test
    void rejectionAllowsCorrectionAndAnOldAckCannotChangeTheNewSubmission() throws Exception {
        UUID id = waiting();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        publisher.after.set(
                phone -> {
                    if (phone.id().equals(first)) {
                        entered.countDown();
                        awaitGate(release);
                    }
                });
        phone(id, first, "1234567");
        publisher.enabled.set(true);
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            publisher.enabled.set(false);
            send(
                    repository.find(id).orElseThrow().hold().centralProcessId(),
                    result(id, "AdditionalInformationRejected", "rejected-" + id));
            phase(id, ReservationPhase.WAITING_FOR_PHONE);
            assertThat(body(get(id, token)).path("canSubmitPhone").asBoolean()).isTrue();
            assertThat(phone(id, second, "7654321").statusCode()).isEqualTo(202);
        } finally {
            release.countDown();
        }
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(publisher.completed).contains(first));
        phase(id, ReservationPhase.PHONE_QUEUED);
        assertThat(repository.findPhone(first).orElseThrow().publishedAt()).isNull();
        assertThat(repository.findPhone(second).orElseThrow().publishedAt()).isNull();
        assertThat(phone(id, first, "1234567").statusCode()).isEqualTo(200);
        assertThat(repository.latestPhone(id).orElseThrow().id()).isEqualTo(second);
    }

    @Test
    void confirmationBeforeAckAndDuplicateResultsDoNotReopenTheProcess() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        publisher.after.set(
                phone -> {
                    if (phone.id().equals(request)) {
                        entered.countDown();
                        awaitGate(release);
                    }
                });
        phone(id, request, "1234567");
        publisher.enabled.set(true);
        String event = result(id, "AppointmentConfirmed", "confirmed-" + id);
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            send(repository.find(id).orElseThrow().hold().centralProcessId(), event);
            phase(id, ReservationPhase.CONFIRMED);
        } finally {
            release.countDown();
        }
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(publisher.completed).contains(request));
        send(repository.find(id).orElseThrow().hold().centralProcessId(), event);
        phase(id, ReservationPhase.CONFIRMED);
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNotNull();
        assertThat(phone(id, UUID.randomUUID(), "7654321").statusCode()).isEqualTo(409);
    }

    @Test
    void realBrokerOutageRetainsTheAcceptedPhoneAndRecoveryPublishesAutomatically()
            throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            publisher.enabled.set(true);
            assertThat(phone(id, request, "1234567").statusCode()).isEqualTo(202);
            await().atMost(Duration.ofSeconds(8))
                    .untilAsserted(
                            () ->
                                    assertThat(
                                                    repository
                                                            .findPhone(request)
                                                            .orElseThrow()
                                                            .lastFailure())
                                            .isEqualTo("PHONE_PUBLICATION_UNCERTAIN"));
            assertThat(body(get(id, token)).path("state").asString()).isEqualTo("PHONE_QUEUED");
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
        phase(id, ReservationPhase.WAITING_FOR_RESULT);
        assertThat(records(request)).isNotEmpty();
    }

    @Test
    void commitFailureAfterBrokerAckRepublishesTheIdenticalEventAfterRecovery() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        // Cada fallo se arma después del ACK del broker y antes del commit local.
        var once = new AtomicBoolean();
        publisher.after.set(
                phone -> {
                    if (phone.id().equals(request) && once.compareAndSet(false, true)) {
                        faults.arm(
                                CommitFaultDataSource.Mode.BEFORE,
                                "update reservation_phone_submission",
                                1);
                    }
                });
        publisher.enabled.set(true);
        phase(id, ReservationPhase.WAITING_FOR_RESULT);
        var sent = records(request);
        assertThat(sent).hasSizeGreaterThanOrEqualTo(2);
        assertThat(sent.stream().map(ConsumerRecord::value).distinct()).hasSize(1);
        assertThat(sent.stream().map(ConsumerRecord::key).distinct()).hasSize(1);
    }

    @Test
    void centralAbsenceAndFailureReturnLocalProgressWithWarningsAndExactFilters() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        jdbc.update(
                "UPDATE reservation_process SET phase='RECONCILIATION_REQUIRED' WHERE id=?",
                id.toString());
        var missing = get(id, token);
        assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(body(missing).path("verificationStatus").asString()).isEqualTo("INCONCLUSIVE");
        assertThat(PROVIDERS.appointmentFilters.get())
                .containsEntry("externalPatientId", "user-" + owner)
                .containsEntry("from", "2026-11-02")
                .containsEntry("to", "2026-11-02")
                .containsEntry("page", "0")
                .containsEntry("size", "100");
        PROVIDERS.appointmentsOverride.set(
                exchange -> TestIntegrationSupport.respond(exchange, 503, "{}"));
        assertThat(body(get(id, token)).path("verificationStatus").asString())
                .isEqualTo("UNAVAILABLE");
        assertThat(PROVIDERS.appointmentQueries).hasValue(2);
        assertThat(PROVIDERS.confirmations).hasValue(1);
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    @Test
    void verifiedCentralResultClosesBothRowsAndForeignEvidenceIsIgnored() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        jdbc.update(
                "UPDATE reservation_process SET phase='RECONCILIATION_REQUIRED' WHERE id=?",
                id.toString());
        var process = repository.find(id).orElseThrow();
        PROVIDERS.appointmentsOverride.set(
                exchange ->
                        TestIntegrationSupport.respond(
                                exchange,
                                200,
                                "[{\"reservationId\":8251,\"reservationProcessId\":\""
                                        + process.hold().centralProcessId()
                                        + "\",\"externalPatientId\":\"user-"
                                        + owner
                                        + "\",\"professionalId\":15,\"appointmentDate\":\"2026-11-02\",\"startTime\":\"09:00\",\"status\":\"CONFIRMED\"}]"));
        var response = get(id, token);
        assertThat(body(response).path("verificationStatus").asString()).isEqualTo("VERIFIED");
        assertThat(body(response).path("state").asString()).isEqualTo("CONFIRMED");
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNotNull();
    }

    @Test
    void persistedAttemptsSurviveANewWorkerAndStopAtTheServerDeadline() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        var deadline = repository.findPhone(request).orElseThrow().expiresAt();
        var worker =
                new PublishPendingPhonesUseCaseImpl(
                        repository,
                        publisher,
                        Clock.fixed(deadline, ZoneOffset.UTC),
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        worker.publishPending();
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isEqualTo(deadline);
        phase(id, ReservationPhase.RECONCILIATION_REQUIRED);
        assertThat(records(request)).isEmpty();
        assertThat(phone(id, request, "1234567").statusCode()).isEqualTo(200);
    }

    @Test
    void sameUuidConcurrentEquivalentInputsAndConcurrentConflictingContentAreStable()
            throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                return phone(id, request, "123-45.67").statusCode();
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                return phone(id, request, "1234567").statusCode();
                            });
            gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(202, 200);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM reservation_phone_submission WHERE"
                                        + " process_id=?",
                                Integer.class,
                                id.toString()))
                .isEqualTo(1);
        assertThat(phone(id, request, "7654321").statusCode()).isEqualTo(409);
    }

    @Test
    void invalidResultStopsPublicationAndCancelledResultCannotBeReopened() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        String key = repository.find(id).orElseThrow().hold().centralProcessId();
        send(key, result(id, "AppointmentProcessInvalid", "invalid-" + id));
        phase(id, ReservationPhase.RECONCILIATION_REQUIRED);
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNotNull();
        assertThat(phone(id, UUID.randomUUID(), "7654321").statusCode()).isEqualTo(409);
        send(key, result(id, "AppointmentCancelled", "cancelled-" + id));
        phase(id, ReservationPhase.CANCELLED);
        send(key, result(id, "AppointmentConfirmed", "late-confirmed-" + id));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
        phase(id, ReservationPhase.CANCELLED);
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    @Test
    void rejectionReceiptProcessAndPhoneCloseRollBackTogetherBeforeOffsetAdvance()
            throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        faults.arm(CommitFaultDataSource.Mode.BEFORE, "insert into reservation_inbox", 3);
        send(
                repository.find(id).orElseThrow().hold().centralProcessId(),
                result(id, "AdditionalInformationRejected", "rejected-" + id));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(faults.remaining).hasValue(0);
                            try (var admin =
                                    Admin.create(
                                            Map.of(
                                                    "bootstrap.servers",
                                                    KAFKA.getBootstrapServers()))) {
                                assertThat(
                                                admin.describeConsumerGroups(
                                                                List.of(
                                                                        ReservationTestSupport
                                                                                .CONSUMER))
                                                        .all()
                                                        .get(5, TimeUnit.SECONDS)
                                                        .get(ReservationTestSupport.CONSUMER)
                                                        .members())
                                        .isEmpty();
                            }
                        });
        assertThat(committed()).isLessThanOrEqualTo(lastSent);
        phase(id, ReservationPhase.PHONE_QUEUED);
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM reservation_inbox WHERE event_id=?",
                                Integer.class,
                                "rejected-" + id))
                .isZero();
        faults.clear();
        channel.requestPreparation();
        phase(id, ReservationPhase.WAITING_FOR_PHONE);
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNotNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM reservation_inbox WHERE event_id=?",
                                Integer.class,
                                "rejected-" + id))
                .isEqualTo(1);
    }

    @Test
    void aNewWorkerResumesPersistedCyclesWithoutResettingTheEventOrTheDeadline() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        var original = repository.findPhone(request).orElseThrow();
        Instant base = original.event().occurredAt().plusSeconds(30);
        // Apartar esta fila del worker real antes de controlar el ciclo con relojes explícitos.
        jdbc.update(
                "UPDATE reservation_phone_submission SET cycle_attempt=0, next_attempt_at=?,"
                        + " version=version+1 WHERE id=?",
                java.sql.Timestamp.from(base),
                request.toString());
        // Relojes separados del scheduler controlan exactamente los intentos y el cooldown.
        var first =
                repository
                        .beginPhonePublication(
                                request, base, 3, Duration.ofSeconds(5), Duration.ofSeconds(30))
                        .orElseThrow();
        assertThat(first.cycleAttempt()).isEqualTo(1);
        repository
                .beginPhonePublication(
                        request,
                        base.plusSeconds(5),
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30))
                .orElseThrow();
        var third =
                repository
                        .beginPhonePublication(
                                request,
                                base.plusSeconds(10),
                                3,
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(30))
                        .orElseThrow();
        assertThat(third.cycleAttempt()).isZero();
        assertThat(third.nextAttemptAt()).isEqualTo(base.plusSeconds(40));
        var recorded = new AtomicReference<PhoneSubmission>();
        var worker =
                new PublishPendingPhonesUseCaseImpl(
                        repository,
                        recorded::set,
                        Clock.fixed(base.plusSeconds(39), ZoneOffset.UTC),
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        worker.publishPending();
        assertThat(recorded.get()).isNull();
        var restarted =
                new PublishPendingPhonesUseCaseImpl(
                        repository,
                        recorded::set,
                        Clock.fixed(base.plusSeconds(40), ZoneOffset.UTC),
                        3,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        restarted.publishPending();
        assertThat(recorded.get().id()).isEqualTo(request);
        assertThat(recorded.get().event()).isEqualTo(original.event());
        assertThat(recorded.get().expiresAt()).isEqualTo(original.expiresAt());
        assertThat(repository.findPhone(request).orElseThrow().publishedAt())
                .isEqualTo(base.plusSeconds(40));
    }

    @Test
    void v4MigratesPopulatedV3WithoutChangingAccountsProcessesOrInbox() throws Exception {
        String url = MYSQL.getJdbcUrl().replace("/phone_test", "/phone_upgrade");
        try (var connection =
                        java.sql.DriverManager.getConnection(
                                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE DATABASE phone_upgrade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        }
        var source = new DriverManagerDataSource(url, "root", MYSQL.getPassword());
        var old =
                org.flywaydb.core.Flyway.configure()
                        .dataSource(source)
                        .locations("classpath:db/migration")
                        .target("3")
                        .load();
        old.migrate();
        var data = new JdbcTemplate(source);
        data.update(
                "INSERT INTO"
                    + " app_user(id,login,email,first_name,last_name,lang_key,password_hash,activated,created_at,last_modified_at)"
                    + " VALUES(42,'migration-user','migration@example.test','Paciente','Ficticio','es','fictional-hash',true,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");
        UUID id = UUID.randomUUID();
        data.update(
                "INSERT INTO"
                    + " reservation_process(id,owner_id,professional_id,appointment_date,start_time,patient_first_name,patient_last_name,phase,hold_id,central_process_id,expires_at,request_event_id,version,write_id,created_at,updated_at)"
                    + " VALUES(?,42,15,'2026-11-02','09:00','Paciente','Ficticio','WAITING_FOR_PHONE','migration-hold','migration-process','2026-11-02"
                    + " 11:05:00','migration-request',0,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                id.toString(),
                UUID.randomUUID().toString());
        data.update(
                "INSERT INTO"
                    + " reservation_inbox(topic,partition_number,record_offset,event_id,process_id,fingerprint,payload,outcome,received_at)"
                    + " VALUES('migration-actions',0,0,'migration-request',?,'abc','{}','APPLIED',CURRENT_TIMESTAMP(6))",
                id.toString());
        var migrated =
                org.flywaydb.core.Flyway.configure()
                        .dataSource(source)
                        .locations("classpath:db/migration")
                        .target("4")
                        .load();
        assertThat(migrated.migrate().migrationsExecuted).isEqualTo(1);
        migrated.validate();
        assertThat(data.queryForObject("SELECT login FROM app_user WHERE id=42", String.class))
                .isEqualTo("migration-user");
        assertThat(
                        data.queryForObject(
                                "SELECT phase FROM reservation_process WHERE id=?",
                                String.class,
                                id.toString()))
                .isEqualTo("WAITING_FOR_PHONE");
        assertThat(
                        data.queryForObject(
                                "SELECT outcome FROM reservation_inbox WHERE"
                                        + " event_id='migration-request'",
                                String.class))
                .isEqualTo("APPLIED");
        assertThat(
                        data.queryForObject(
                                "SELECT count(*) FROM reservation_phone_submission", Integer.class))
                .isZero();
    }

    @Test
    void aLegacyDeferredRejectionIsDeduplicatedWithoutReclassifyingHistory() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        var process = repository.find(id).orElseThrow();
        String payload = result(id, "AdditionalInformationRejected", "legacy-" + id);
        var parser = new ReservationActionParser(json, ReservationTestSupport.GROUP);
        var delivery =
                parser.parse(
                        new ReservationDelivery.Origin(
                                "legacy-actions", 0, 1, process.hold().centralProcessId()),
                        payload);
        jdbc.update(
                "INSERT INTO"
                    + " reservation_inbox(topic,partition_number,record_offset,message_key,event_id,process_id,fingerprint,payload,outcome,rejection,received_at)"
                    + " VALUES('legacy-actions',0,1,?,?,?,?,?,'DEFERRED','UNHANDLED_ACTION',CURRENT_TIMESTAMP(6))",
                process.hold().centralProcessId(),
                "legacy-" + id,
                id.toString(),
                delivery.fingerprint(),
                payload);
        send(process.hold().centralProcessId(), payload);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
        phase(id, ReservationPhase.PHONE_QUEUED);
        assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT outcome FROM reservation_inbox WHERE event_id=?",
                                String.class,
                                "legacy-" + id))
                .isEqualTo("DEFERRED");
    }

    @Test
    void initialRestAcceptanceCannotReplaceTheMissingKafkaRequest() throws Exception {
        UUID id = UUID.randomUUID();
        var response =
                post(
                        "/api/reservation-processes",
                        json.writeValueAsString(
                                Map.of(
                                        "requestId",
                                        id,
                                        "professionalId",
                                        15,
                                        "date",
                                        "2026-11-02",
                                        "startTime",
                                        "09:00")),
                        token);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(phone(id, UUID.randomUUID(), "1234567").statusCode()).isEqualTo(409);
        assertThat(repository.latestPhone(id)).isEmpty();
        assertThat(body(get(id, token)).path("canSubmitPhone").asBoolean()).isFalse();
    }

    @Test
    void cancellationDuringTheCentralGetWinsOverItsOlderConfirmation() throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        jdbc.update(
                "UPDATE reservation_process SET phase='RECONCILIATION_REQUIRED' WHERE id=?",
                id.toString());
        var process = repository.find(id).orElseThrow();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        PROVIDERS.appointmentsOverride.set(
                exchange -> {
                    entered.countDown();
                    awaitGate(release);
                    TestIntegrationSupport.respond(
                            exchange,
                            200,
                            "[{\"reservationId\":8251,\"reservationProcessId\":\""
                                    + process.hold().centralProcessId()
                                    + "\",\"externalPatientId\":\"user-"
                                    + owner
                                    + "\",\"professionalId\":15,\"appointmentDate\":\"2026-11-02\",\"startTime\":\"09:00\",\"status\":\"CONFIRMED\"}]");
                });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> get(id, token));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                send(
                        process.hold().centralProcessId(),
                        result(id, "AppointmentCancelled", "cancelled-" + id));
                phase(id, ReservationPhase.CANCELLED);
            } finally {
                release.countDown();
            }
            var response = pending.get(5, TimeUnit.SECONDS);
            assertThat(body(response).path("state").asString()).isEqualTo("CANCELLED");
            assertThat(repository.findPhone(request).orElseThrow().closedAt()).isNotNull();
        }
    }

    @Test
    void producerReusesTheAssignedDestinationAndClosesItWhenTheAssignmentChanges()
            throws Exception {
        UUID id = waiting();
        UUID request = UUID.randomUUID();
        phone(id, request, "1234567");
        var persisted = repository.findPhone(request).orElseThrow();
        String secondTopic = "phone-destination-change";
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(secondTopic, 1, (short) 1)))
                    .all()
                    .get(5, TimeUnit.SECONDS);
        }
        var assigned = org.mockito.Mockito.mock(ReservationKafkaChannel.class);
        org.mockito.Mockito.when(assigned.phoneDestination())
                .thenReturn(
                        new ReservationKafkaChannel.PhoneDestination(
                                KAFKA.getBootstrapServers(),
                                ReservationTestSupport.PHONE_TOPIC,
                                ReservationTestSupport.GROUP));
        var producer =
                new KafkaPhoneSubmissionAdapter(
                        assigned,
                        json,
                        Clock.fixed(persisted.event().occurredAt(), ZoneOffset.UTC),
                        Duration.ofSeconds(2));
        try (producer) {
            org.mockito.Mockito.when(assigned.phoneDestination())
                    .thenReturn(
                            new ReservationKafkaChannel.PhoneDestination(
                                    KAFKA.getBootstrapServers(),
                                    "missing-phone-topic",
                                    ReservationTestSupport.GROUP));
            assertThatThrownBy(() -> producer.publish(persisted))
                    .isInstanceOf(ReservationGatewayException.class);
            try (var admin =
                    Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                assertThat(admin.listTopics().names().get(5, TimeUnit.SECONDS))
                        .isNotEmpty()
                        .contains(
                                ReservationTestSupport.TOPIC,
                                ReservationTestSupport.PHONE_TOPIC,
                                secondTopic)
                        .doesNotContain("missing-phone-topic");
            }
            org.mockito.Mockito.when(assigned.phoneDestination())
                    .thenReturn(
                            new ReservationKafkaChannel.PhoneDestination(
                                    KAFKA.getBootstrapServers(),
                                    ReservationTestSupport.PHONE_TOPIC,
                                    ReservationTestSupport.GROUP));
            producer.publish(persisted);
            org.mockito.Mockito.when(assigned.phoneDestination())
                    .thenReturn(
                            new ReservationKafkaChannel.PhoneDestination(
                                    KAFKA.getBootstrapServers(),
                                    secondTopic,
                                    ReservationTestSupport.GROUP));
            producer.publish(persisted);
        }
        assertThatThrownBy(() -> producer.publish(persisted))
                .isInstanceOf(ReservationGatewayException.class);
        var first = records(request).getFirst().value();
        try (var observer =
                new KafkaConsumer<String, String>(
                        Map.of(
                                "bootstrap.servers",
                                KAFKA.getBootstrapServers(),
                                "group.id",
                                "changed-observer",
                                "enable.auto.commit",
                                false),
                        new StringDeserializer(),
                        new StringDeserializer())) {
            var partition = new TopicPartition(secondTopic, 0);
            observer.assign(List.of(partition));
            observer.seekToBeginning(List.of(partition));
            var received = new AtomicReference<String>();
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(
                            () -> {
                                observer.poll(Duration.ofMillis(100))
                                        .forEach(message -> received.set(message.value()));
                                assertThat(received.get()).isEqualTo(first);
                            });
        }
        // Este ensayo del adaptador no atribuye un ACK a la aplicación ni confirma la reserva.
        phase(id, ReservationPhase.PHONE_QUEUED);
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            if (!gate.await(15, TimeUnit.SECONDS))
                throw new IllegalStateException("Fictional gate timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    /** Controla cortes y ACK tardíos, conservando el productor Kafka real debajo de la frontera. */
    static final class ControlledPublisher implements PhonePublicationGateway {
        final AtomicBoolean enabled = new AtomicBoolean();
        final AtomicReference<Consumer<PhoneSubmission>> before = new AtomicReference<>(_ -> {});
        final AtomicReference<Consumer<PhoneSubmission>> after = new AtomicReference<>(_ -> {});
        final Set<UUID> completed = ConcurrentHashMap.newKeySet();
        private final KafkaPhoneSubmissionAdapter delegate;

        ControlledPublisher(KafkaPhoneSubmissionAdapter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(PhoneSubmission phone) {
            if (!enabled.get())
                throw new ReservationGatewayException(
                        ReservationFailure.PHONE_PUBLICATION_UNCERTAIN, true, false);
            var beforeSend = before.get();
            var afterSend = after.get();
            beforeSend.accept(phone);
            delegate.publish(phone);
            afterSend.accept(phone);
            completed.add(phone.id());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Settings {
        @Bean
        @Primary
        Clock phoneClock() {
            return Clock.offset(
                    Clock.systemUTC(),
                    Duration.between(Instant.now(), ReservationProcessTests.NOW));
        }

        @Bean
        CommitFaultDataSource commitFaultDataSource() {
            return new CommitFaultDataSource(
                    new DriverManagerDataSource(
                            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        }

        @Bean
        @Primary
        ControlledPublisher controlledPublisher(KafkaPhoneSubmissionAdapter delegate) {
            return new ControlledPublisher(delegate);
        }
    }
}

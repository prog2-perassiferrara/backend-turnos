package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.model.ReservationPatient;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.StartReservationRequest;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationKafkaChannel;
import com.prog2.appointment.user.domain.ports.in.GetUserProfileUseCase;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integración observable con SQL/broker reales descartables, HTTP ficticio y autenticación
 * productiva.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReservationIntegrationTests.Settings.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@Timeout(45)
class ReservationIntegrationTests {
    @Container
    static final org.testcontainers.mysql.MySQLContainer MYSQL =
            new org.testcontainers.mysql.MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("reservation_test")
                    .withUsername("reservation_test")
                    .withPassword("fictional-database");

    @Container
    static final org.testcontainers.kafka.KafkaContainer KAFKA =
            new org.testcontainers.kafka.KafkaContainer("apache/kafka-native:3.8.0");

    static final ReservationTestSupport PROVIDERS =
            new ReservationTestSupport(KAFKA::getBootstrapServers);
    static final String PASSWORD = "fictional-reservation-password";
    private static final AtomicBoolean TOPIC_CREATED = new AtomicBoolean();
    private final int port;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final ReservationProcessRepository repository;
    private final ReservationKafkaChannel channel;
    private final GetUserProfileUseCase profiles;
    private final CommitFaultDataSource faults;
    private final HttpClient http = HttpClient.newHttpClient();
    private String token;
    private long owner;
    private long lastSent = -1;

    ReservationIntegrationTests(
            @LocalServerPort int port,
            JsonMapper json,
            JdbcTemplate jdbc,
            ReservationProcessRepository repository,
            ReservationKafkaChannel channel,
            GetUserProfileUseCase profiles,
            CommitFaultDataSource faults) {
        this.port = port;
        this.json = json;
        this.jdbc = jdbc;
        this.repository = repository;
        this.channel = channel;
        this.profiles = profiles;
        this.faults = faults;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) throws Exception {
        if (TOPIC_CREATED.compareAndSet(false, true)) {
            try (var admin =
                    Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                admin.createTopics(
                                List.of(new NewTopic(ReservationTestSupport.TOPIC, 1, (short) 1),
                                        new NewTopic(ReservationTestSupport.PHONE_TOPIC, 1, (short) 1)))
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
        registry.add("RESERVATION_METADATA_TIMEOUT", () -> "2s");
        registry.add("RESERVATION_RETRY_DELAY", () -> "100ms");
        registry.add("RESERVATION_PREPARATION_COOLDOWN", () -> "50ms");
    }

    @BeforeEach
    void prepare() throws Exception {
        faults.clear();
        PROVIDERS.reset();
        lastSent = -1;
        await().atMost(Duration.ofSeconds(25))
                .untilAsserted(
                        () -> assertThatCode(channel::requireReady).doesNotThrowAnyException());
        String login = "reserve_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        token = account(login, "Paciente");
        owner = jdbc.queryForObject("SELECT id FROM app_user WHERE login=?", Long.class, login);
    }

    @AfterEach
    void cleanup() {
        faults.clear();
        if (lastSent >= 0) {
            channel.requestPreparation();
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
        }
        http.close();
    }

    @AfterAll
    static void closeProviders() {
        PROVIDERS.close();
    }

    private String account(String login, String firstName) throws Exception {
        String profile =
                json.writeValueAsString(
                        Map.of(
                                "login",
                                login,
                                "password",
                                PASSWORD,
                                "firstName",
                                firstName,
                                "lastName",
                                "Ficticio",
                                "email",
                                login + "@example.test"));
        assertThat(post("/api/register", profile, null).statusCode()).isEqualTo(201);
        var response =
                post(
                        "/api/authenticate",
                        json.writeValueAsString(Map.of("username", login, "password", PASSWORD)),
                        null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("id_token").asString();
    }

    private String request(UUID id, String time) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "requestId",
                        id,
                        "professionalId",
                        15,
                        "date",
                        "2026-11-02",
                        "startTime",
                        time));
    }

    private HttpResponse<String> start(UUID id, String time) throws Exception {
        return post("/api/reservation-processes", request(id, time), token);
    }

    private HttpResponse<String> post(String path, String body, String bearer)
            throws java.io.IOException, InterruptedException {
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private void phase(UUID id, ReservationPhase phase) {
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(repository.find(id).orElseThrow().phase())
                                        .isEqualTo(phase));
    }

    private void send(String key, String value) {
        try (var producer =
                new KafkaProducer<String, String>(
                        Map.of("bootstrap.servers", KAFKA.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer())) {
            var sent =
                    producer.send(new ProducerRecord<>(ReservationTestSupport.TOPIC, key, value))
                            .get(10, TimeUnit.SECONDS);
            lastSent = Math.max(lastSent, sent.offset());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot publish fictional action", failure);
        }
    }

    private long committed() {
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var offsets =
                    admin.listConsumerGroupOffsets(ReservationTestSupport.CONSUMER)
                            .partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS);
            var value = offsets.get(new TopicPartition(ReservationTestSupport.TOPIC, 0));
            return value == null ? 0 : value.offset();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot inspect fictional offsets", failure);
        }
    }

    @Test
    void startsAndReplaysTheOwnedProcessWithRealSqlAndUserAuthentication(CapturedOutput output)
            throws Exception {
        UUID id = UUID.randomUUID();
        var created = start(id, "09:00");
        assertThat(created.statusCode()).isEqualTo(202);
        assertThat(body(created).path("state").asString()).isEqualTo("WAITING_FOR_REQUEST");
        assertThat(body(created).path("startTime").asString()).isEqualTo("09:00:00");
        assertThat(created.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(PROVIDERS.submittedPatient).hasValue("user-" + owner);
        assertThat(PROVIDERS.submittedFirstName).hasValue("Paciente");
        assertThat(PROVIDERS.credentialFailures).hasValue(0);
        assertThat(start(id, "09:00:00").statusCode()).isEqualTo(200);
        assertThat(start(id, "09:30").statusCode()).isEqualTo(409);
        assertThat(PROVIDERS.creates).hasValue(1);
        assertThat(PROVIDERS.confirmations).hasValue(1);
        assertThat(output).doesNotContain(token, PASSWORD, PROVIDERS.token);
    }

    @Test
    void rejectsAnonymousAndInvalidBodiesBeforeHolds() throws Exception {
        assertThat(
                        post(
                                        "/api/reservation-processes",
                                        request(UUID.randomUUID(), "09:00"),
                                        null)
                                .statusCode())
                .isEqualTo(401);
        assertThat(post("/api/reservation-processes", "{}", token).statusCode()).isEqualTo(400);
        assertThat(post("/api/reservation-processes", "{bad-json", token).statusCode())
                .isEqualTo(400);
        assertThat(PROVIDERS.creates).hasValue(0);
    }

    @Test
    void readsMinimalProfilesAndRejectsAnExistingIncompatibleProfileBeforeHolds() throws Exception {
        var profile = profiles.get(owner).orElseThrow();
        assertThat(profile.firstName()).isEqualTo("Paciente");
        assertThat(profile.id()).isEqualTo(owner);
        assertThat(profiles.get(Long.MAX_VALUE)).isEmpty();
        // Simula un perfil anterior; el registro actual ya rechaza ese nombre.
        jdbc.update("UPDATE app_user SET first_name=? WHERE id=?", "A", owner);
        var response = start(UUID.randomUUID(), "09:00");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).path("code").asString()).isEqualTo("PROFILE_NOT_RESERVABLE");
        assertThat(PROVIDERS.creates).hasValue(0);
    }

    @Test
    void aNewAccountWithAMinimumLengthNameCanStartAReservation() throws Exception {
        token = account("minimum_" + UUID.randomUUID().toString().substring(0, 8), "Al");
        var response = start(UUID.randomUUID(), "09:00");
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(PROVIDERS.submittedFirstName).hasValue("Al");
        assertThat(PROVIDERS.creates).hasValue(1);
        assertThat(PROVIDERS.confirmations).hasValue(1);
    }

    @Test
    void aLostCreationWithAnObservedHoldClosesOnlyTheAttemptAndRequiresAFreshCheck()
            throws Exception {
        PROVIDERS.dropCreation.set(true);
        UUID id = UUID.randomUUID();
        var response = start(id, "09:00");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(body(response).path("recheckAt").asString())
                .isEqualTo(ReservationProcessTests.HOLD.expiresAt().toString());
        var abandoned = repository.find(id).orElseThrow();
        assertThat(abandoned.phase()).isEqualTo(ReservationPhase.HOLD_ATTEMPT_ABANDONED);
        assertThat(abandoned.hold()).isNull();
        assertThat(repository.findPending(owner)).isEmpty();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(200);
        assertThat(start(UUID.randomUUID(), "09:00").statusCode()).isEqualTo(409);
        assertThat(PROVIDERS.creates).hasValue(1);
        // Es el proveedor quien libera la posición; no avanza un temporizador local de diez
        // minutos.
        PROVIDERS.releaseHolds();
        PROVIDERS.dropCreation.set(false);
        assertThat(start(UUID.randomUUID(), "09:00").statusCode()).isEqualTo(202);
        assertThat(PROVIDERS.creates).hasValue(2);
        assertThat(PROVIDERS.confirmations).hasValue(1);
    }

    @Test
    void freeOccupancyAfterALostResponseAllowsOneNewAttemptWithinTheSameProcess() throws Exception {
        PROVIDERS.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    PROVIDERS.createOverride.set(null);
                    exchange.close();
                });
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        assertThat(start(id, "09:00").statusCode()).isEqualTo(200);
        assertThat(PROVIDERS.creates).hasValue(2);
        assertThat(PROVIDERS.confirmations).hasValue(1);
        assertThat(PROVIDERS.occupancyQueries.get()).isGreaterThanOrEqualTo(2);
        assertThat(repository.find(id).orElseThrow().phase())
                .isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
    }

    @Test
    void repeatedUncertainFreeAttemptsStopAtTheBudgetAndArchiveOneProcess() throws Exception {
        PROVIDERS.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.close();
                });
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(503);
        assertThat(PROVIDERS.creates).hasValue(3);
        assertThat(PROVIDERS.confirmations).hasValue(0);
        assertThat(repository.find(id).orElseThrow().phase())
                .isEqualTo(ReservationPhase.HOLD_ATTEMPT_ABANDONED);
        assertThat(repository.findPending(owner)).isEmpty();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(200);
        assertThat(PROVIDERS.creates).hasValue(3);
    }

    @Test
    void aConcurrent409AfterAFreeRecheckDoesNotTriggerAnotherPost() throws Exception {
        PROVIDERS.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    PROVIDERS.createOverride.set(null);
                    PROVIDERS.creationStatus.set(409);
                    exchange.close();
                });
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(409);
        assertThat(PROVIDERS.creates).hasValue(2);
        assertThat(repository.find(id).orElseThrow().phase())
                .isEqualTo(ReservationPhase.HOLD_ATTEMPT_ABANDONED);
    }

    @Test
    void anUnavailableOccupancyCheckNeverAuthorizesAnotherPost() throws Exception {
        PROVIDERS.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    PROVIDERS.occupanciesOverride.set(
                            query ->
                                    com.prog2.appointment.integration.TestIntegrationSupport
                                            .respond(query, 503, "{}"));
                    exchange.close();
                });
        UUID id = UUID.randomUUID();
        var response = start(id, "09:00");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(body(response).path("code").asString()).isEqualTo("OCCUPANCIES_UNAVAILABLE");
        assertThat(PROVIDERS.creates).hasValue(1);
        assertThat(repository.findPending(owner)).isEmpty();
    }

    @Test
    void sqlExclusionAndTheInsertionReceiptDoNotAuthorizeADuplicateExecution() {
        var request = new StartReservationRequest(UUID.randomUUID(), ReservationProcessTests.SLOT);
        var candidate =
                ReservationProcess.begin(
                        request, new ReservationPatient(owner, "Paciente", "Ficticio"));
        assertThat(repository.begin(candidate).created()).isTrue();
        assertThat(repository.begin(candidate).created()).isFalse();
        var another =
                ReservationProcess.begin(
                        new StartReservationRequest(UUID.randomUUID(), request.selection()),
                        candidate.patient());
        assertThat(repository.begin(another).process().id()).isEqualTo(candidate.id());
        repository.update(
                candidate.id(),
                value ->
                        value.recordFailure(
                                new com.prog2.appointment.reservation.domain.exception
                                        .ReservationGatewayException(
                                        ReservationFailure.SLOT_NOT_AVAILABLE, true, true),
                                ReservationProcessTests.NOW));
        assertThat(repository.begin(another).created()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reservation_process WHERE owner_id=?",
                                Integer.class,
                                owner))
                .isEqualTo(2);
    }

    @Test
    void aFailureBeforeCommitCannotCreateAHoldAndCanRetryAfterRollback() throws Exception {
        faults.arm(CommitFaultDataSource.Mode.BEFORE, "insert into reservation_process", 1);
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(503);
        assertThat(PROVIDERS.creates).hasValue(0);
        assertThat(repository.find(id)).isEmpty();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    @Test
    void aLostCommitResponseUsesTheWriteReceiptBeforeCreatingExactlyOneHold() throws Exception {
        faults.arm(CommitFaultDataSource.Mode.AFTER, "insert into reservation_process", 1);
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        assertThat(start(id, "09:00").statusCode()).isEqualTo(200);
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    @Test
    void anActualKafkaRequestCanArriveBeforeTheRestResponse() throws Exception {
        UUID id = UUID.randomUUID();
        String event = "request-" + UUID.randomUUID();
        PROVIDERS.beforeConfirm.set(
                hold -> {
                    send(
                            hold.process(),
                            ReservationTestSupport.event(
                                    hold, "AdditionalInformationRequested", event));
                    phase(id, ReservationPhase.WAITING_FOR_PHONE);
                });
        var response = start(id, "09:00");
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(body(response).path("state").asString()).isEqualTo("WAITING_FOR_PHONE");
        assertThat(repository.find(id).orElseThrow().requestEventId()).isEqualTo(event);
        long version = repository.find(id).orElseThrow().version();
        send(
                PROVIDERS.last.get().process(),
                ReservationTestSupport.event(
                        PROVIDERS.last.get(), "AdditionalInformationRequested", event));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reservation_inbox WHERE event_id=?",
                                Integer.class,
                                event))
                .isEqualTo(1);
        assertThat(repository.find(id).orElseThrow().version()).isEqualTo(version);
    }

    @Test
    void expirationClosesOnceAndTheSameRequestReturnsHistory() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        var hold = PROVIDERS.last.get();
        send(
                hold.process(),
                ReservationTestSupport.event(
                        hold, "AppointmentProcessExpired", "expired-" + UUID.randomUUID()));
        phase(id, ReservationPhase.EXPIRED);
        assertThat(start(id, "09:00").statusCode()).isEqualTo(200);
        assertThat(PROVIDERS.creates).hasValue(1);
        assertThat(start(UUID.randomUUID(), "10:00").statusCode()).isEqualTo(202);
        assertThat(PROVIDERS.creates).hasValue(2);
    }

    @Test
    void aRejectedConfirmationDoesNotReleaseTheKnownHold() throws Exception {
        PROVIDERS.confirmationStatus.set(404);
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(409);
        assertThat(repository.find(id).orElseThrow().phase())
                .isEqualTo(ReservationPhase.RECONCILIATION_REQUIRED);
        assertThat(repository.find(id).orElseThrow().hold()).isNotNull();
        assertThat(start(UUID.randomUUID(), "10:00").statusCode()).isEqualTo(409);
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    @Test
    void simultaneousDifferentRequestsForOneUserCreateOnlyOneHold() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        PROVIDERS.beforeCreate.set(
                () -> {
                    entered.countDown();
                    awaitGate(release);
                });
        UUID first = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var pending = executor.submit(() -> start(first, "09:00"));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var blocked = start(UUID.randomUUID(), "10:00");
                assertThat(blocked.statusCode()).isEqualTo(409);
                assertThat(body(blocked).path("processId").asString()).isEqualTo(first.toString());
                assertThat(PROVIDERS.creates).hasValue(1);
            } finally {
                release.countDown();
            }
            assertThat(pending.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(202);
        }
    }

    @Test
    void simultaneousRepetitionReturnsTheSameInProgressProcessWithoutAnotherPost()
            throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        PROVIDERS.beforeCreate.set(
                () -> {
                    entered.countDown();
                    awaitGate(release);
                });
        UUID id = UUID.randomUUID();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> start(id, "09:00"));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var repeated = start(id, "09:00:00");
                assertThat(repeated.statusCode()).isEqualTo(200);
                assertThat(body(repeated).path("state").asString()).isEqualTo("CREATING_HOLD");
                assertThat(PROVIDERS.creates).hasValue(1);
            } finally {
                release.countDown();
            }
            assertThat(first.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(202);
        }
    }

    @Test
    void differentUsersAreArbitratedByCentralWithoutMixingOwners() throws Exception {
        String other = account("other_" + UUID.randomUUID().toString().substring(0, 8), "Otra");
        PROVIDERS.createBarrier.set(new CountDownLatch(2));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> start(first, "09:00"));
            var b =
                    executor.submit(
                            () ->
                                    post(
                                            "/api/reservation-processes",
                                            request(second, "09:00"),
                                            other));
            var responses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(responses)
                    .extracting(HttpResponse::statusCode)
                    .containsExactlyInAnyOrder(202, 409);
        }
        assertThat(PROVIDERS.creates).hasValue(2);
        assertThat(PROVIDERS.confirmations).hasValue(1);
        assertThat(repository.find(first).orElseThrow().ownerId()).isEqualTo(owner);
        assertThat(repository.find(second).orElseThrow().ownerId()).isNotEqualTo(owner);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reservation_process WHERE id IN (?,?) AND"
                                        + " active_owner_id IS NOT NULL",
                                Integer.class,
                                first.toString(),
                                second.toString()))
                .isEqualTo(1);
    }

    @Test
    void anotherUsersRequestIdDoesNotRevealOrChangeTheirProcess() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        token = account("foreign_" + UUID.randomUUID().toString().substring(0, 8), "Otra");
        var response = start(id, "09:00");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(body(response).has("processId")).isFalse();
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    @Test
    void clientSuppliedPatientOrNameCannotReplaceTheAuthenticatedAccount() throws Exception {
        var value = json.readTree(request(UUID.randomUUID(), "09:00"));
        var body = (tools.jackson.databind.node.ObjectNode) value;
        body.put("ownerId", 999999);
        body.put("externalPatientId", "forged-patient");
        body.put("patientFirstName", "Inventado");
        var response = post("/api/reservation-processes", json.writeValueAsString(body), token);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(PROVIDERS.submittedPatient).hasValue("user-" + owner);
        assertThat(PROVIDERS.submittedFirstName).hasValue("Paciente");
    }

    @Test
    void offsetCannotAdvanceWhileTheActualInboxCommitIsBlocked() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        var hold = PROVIDERS.last.get();
        faults.arm(CommitFaultDataSource.Mode.BLOCK, "insert into reservation_inbox", 1);
        try {
            send(
                    hold.process(),
                    ReservationTestSupport.event(
                            hold,
                            "AdditionalInformationRequested",
                            "blocked-" + UUID.randomUUID()));
            assertThat(faults.entered.get().await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(committed()).isLessThanOrEqualTo(lastSent);
            assertThat(repository.find(id).orElseThrow().phase())
                    .isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        } finally {
            faults.release.get().countDown();
        }
        phase(id, ReservationPhase.WAITING_FOR_PHONE);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
    }

    @Test
    void exhaustedCommitFailuresStopWithoutDiscardAndDemandCanResumeTheSameRecord()
            throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        var hold = PROVIDERS.last.get();
        String event = "retry-" + UUID.randomUUID();
        faults.arm(CommitFaultDataSource.Mode.BEFORE, "insert into reservation_inbox", 3);
        send(
                hold.process(),
                ReservationTestSupport.event(hold, "AdditionalInformationRequested", event));
        await().atMost(Duration.ofSeconds(15))
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
        assertThat(repository.find(id).orElseThrow().phase())
                .isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reservation_inbox WHERE event_id=?",
                                Integer.class,
                                event))
                .isZero();
        faults.clear();
        channel.requestPreparation();
        phase(id, ReservationPhase.WAITING_FOR_PHONE);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(committed()).isGreaterThan(lastSent));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reservation_inbox WHERE event_id=?",
                                Integer.class,
                                event))
                .isEqualTo(1);
    }

    @Test
    void malformedOrForeignActionsAreStoredWithoutChangingTheOwnedProcess() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        var hold = PROVIDERS.last.get();
        send(
                "wrong-key",
                ReservationTestSupport.event(
                        hold, "AdditionalInformationRequested", "foreign-" + UUID.randomUUID()));
        long offset = lastSent;
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForList(
                                                        "SELECT outcome FROM reservation_inbox"
                                                                + " WHERE topic=? AND"
                                                                + " partition_number=0 AND"
                                                                + " record_offset=?",
                                                        ReservationTestSupport.TOPIC,
                                                        offset))
                                        .hasSize(1));
        assertThat(repository.find(id).orElseThrow().phase())
                .isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT outcome FROM reservation_inbox WHERE topic=? AND"
                                        + " partition_number=0 AND record_offset=?",
                                String.class,
                                ReservationTestSupport.TOPIC,
                                offset))
                .isEqualTo("REJECTED");
    }

    @Test
    void pendingAbandonmentKeepsSqlExclusionEvenAfterAConfirmation() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(start(id, "09:00").statusCode()).isEqualTo(202);
        jdbc.update(
                "UPDATE reservation_process SET phase='CONFIRMED', closed_at=CURRENT_TIMESTAMP(6),"
                        + " abandonment_pending=TRUE WHERE id=?",
                id.toString());
        assertThat(repository.findPending(owner).orElseThrow().id()).isEqualTo(id);
        assertThat(start(UUID.randomUUID(), "09:30").statusCode()).isEqualTo(409);
        assertThat(PROVIDERS.creates).hasValue(1);
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            if (!gate.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Fictional gate timed out");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Settings {
        @Bean
        @Primary
        Clock reservationClock() {
            return Clock.fixed(ReservationProcessTests.NOW, ZoneOffset.UTC);
        }

        @Bean
        CommitFaultDataSource commitFaultDataSource() {
            return new CommitFaultDataSource(
                    new DriverManagerDataSource(
                            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        }
    }
}

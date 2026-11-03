package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;

import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationProcessesUseCase;
import com.prog2.appointment.reservation.domain.ports.out.*;
import com.prog2.appointment.reservation.infrastructure.messaging.PhoneSubmissionDispatcher;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationActionParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** API, transacciones e inbox reales; solamente los proveedores externos son ficticios. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReservationCancellationIntegrationTests.Settings.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(60)
class ReservationCancellationIntegrationTests {
    private static final Instant NOW = ReservationProcessTests.NOW;
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("cancellation").withUsername("cancellation_test")
            .withPassword("fictional-database");
    static final ReservationTestSupport PROVIDERS = new ReservationTestSupport(() -> "127.0.0.1:1");
    private final int port;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final ReservationProcessRepository repository;
    private final RecoverReservationCancellationsUseCase recover;
    private final RecoverReservationProcessesUseCase reviews;
    private final ReservationActionParser parser;
    private final CommitFaultDataSource faults;
    private final TestClock clock;
    private final TestPublisher publisher;
    private final PhoneSubmissionDispatcher phones;
    private final ThreadPoolTaskScheduler scheduler;
    private HttpClient http;
    private String token;
    private long owner;

    ReservationCancellationIntegrationTests(@LocalServerPort int port, JsonMapper json,
            JdbcTemplate jdbc, ReservationProcessRepository repository,
            RecoverReservationCancellationsUseCase recover, RecoverReservationProcessesUseCase reviews, ReservationActionParser parser,
            CommitFaultDataSource faults, TestClock clock, TestPublisher publisher,
            PhoneSubmissionDispatcher phones, ThreadPoolTaskScheduler scheduler) {
        this.port = port; this.json = json; this.jdbc = jdbc; this.repository = repository;
        this.recover = recover; this.reviews = reviews; this.parser = parser; this.faults = faults; this.clock = clock;
        this.publisher = publisher; this.phones = phones; this.scheduler = scheduler;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        PROVIDERS.settings().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("RESERVATION_METADATA_TIMEOUT", () -> "20ms");
        registry.add("RESERVATION_HTTP_TIMEOUT", () -> "2s");
        registry.add("RESERVATION_RETRY_DELAY", () -> "1h");
        registry.add("RESERVATION_PREPARATION_COOLDOWN", () -> "30s");
    }

    @BeforeEach
    void setup() throws Exception {
        faults.clear(); clock.value.set(NOW); publisher.reset(); PROVIDERS.reset();
        jdbc.update("DELETE FROM reservation_processed_event");
        jdbc.update("DELETE FROM reservation_phone_submission");
        jdbc.update("DELETE FROM reservation_process");
        http = HttpClient.newHttpClient();
        String login = "cancel_" + UUID.randomUUID().toString().substring(0, 12);
        String password = UUID.randomUUID().toString();
        assertThat(request("POST", "/api/register", Map.of("login", login, "password", password,
                "firstName", "Paciente", "lastName", "Ficticio", "email", login + "@example.test"), null)
                .statusCode()).isEqualTo(201);
        var auth = request("POST", "/api/authenticate", Map.of("username", login, "password", password), null);
        assertThat(auth.statusCode()).isEqualTo(200);
        token = json.readTree(auth.body()).path("id_token").asString();
        owner = jdbc.queryForObject("SELECT id FROM app_user WHERE login=?", Long.class, login);
    }

    @AfterEach
    void cleanup() { faults.clear(); publisher.release.get().countDown(); http.close(); }

    @AfterAll
    static void closeProviders() { PROVIDERS.close(); }

    private HttpResponse<String> request(String method, String path, Object body, String bearer)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String path(ReservationProcess process, String action) {
        return "/api/reservation-processes/" + process.id() + "/" + action;
    }

    private ReservationProcess seed(boolean confirmed) {
        var process = ReservationProcess.begin(new StartReservationRequest(UUID.randomUUID(), ReservationProcessTests.SLOT),
                new ReservationPatient(owner, "Paciente", "Ficticio"));
        process = process.acceptHold(new HoldDetails("hold-" + process.id(), "central-" + process.id(), NOW.plusSeconds(300)))
                .beginConfirmation();
        if (confirmed) {
            process = process.reconcile(evidence(process, CentralAppointmentState.CONFIRMED), NOW);
            central(process, "CONFIRMED");
        }
        assertThat(repository.begin(process).created()).isTrue();
        return repository.find(process.id()).orElseThrow();
    }

    private static CentralAppointment evidence(ReservationProcess process, CentralAppointmentState state) {
        return new CentralAppointment(process.hold().centralProcessId(), process.patient().externalPatientId(),
                process.selection(), 8251, state, new ProfessionalName("Profesional", "Ficticio"));
    }

    private void central(ReservationProcess process, String state) {
        PROVIDERS.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "1");
            TestIntegrationSupport.respond(exchange, 200, json.writeValueAsString(List.of(Map.of(
                    "reservationId", 8251, "reservationProcessId", process.hold().centralProcessId(),
                    "externalPatientId", process.patient().externalPatientId(),
                    "professionalId", process.selection().professionalId(),
                    "appointmentDate", process.selection().date().toString(), "startTime", "09:00", "status", state))));
        });
    }

    private void unavailable() {
        PROVIDERS.cancellationOverride.set(exchange -> TestIntegrationSupport.respond(exchange, 503, "{}"));
    }

    private static ReservationAction phoneRequest(ReservationProcess process) {
        return new ReservationAction(ReservationActionType.PHONE_REQUESTED,
                new ReservationAction.Correlation("request-1", process.hold().centralProcessId(),
                        process.hold().holdId(), null), process.hold().expiresAt(), null, null);
    }

    private void apply(ReservationProcess process, String type, String eventId) {
        var row = new HashMap<String, Object>();
        row.put("eventId", eventId); row.put("eventType", type); row.put("schemaVersion", 1);
        row.put("occurredAt", NOW.toString()); row.put("groupId", ReservationTestSupport.GROUP);
        row.put("reservationProcessId", process.hold().centralProcessId()); row.put("holdId", process.hold().holdId());
        row.put("requestEventId", "request-1");
        if (type.equals("AppointmentConfirmed") || type.equals("AppointmentCancelled")) {
            row.put("reservationId", 8251); row.put("professionalId", process.selection().professionalId());
            row.put("date", process.selection().date().toString()); row.put("startTime", "09:00");
            row.put("status", type.equals("AppointmentConfirmed") ? "CONFIRMED" : "CANCELLED");
        }
        var action = parser.parse(process.hold().centralProcessId(), json.writeValueAsString(row)).orElseThrow();
        repository.accept(action);
    }

    @Test
    void confirmedCancellationUsesOnlyRestAndItsReplayIsIdempotent() throws Exception {
        var process = seed(true);
        var response = request("POST", path(process, "cancel"), null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.path("state").asString()).isEqualTo("CANCELLED");
        assertThat(body.path("cancellation").isNull()).isTrue();
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.body()).doesNotContain(process.hold().centralProcessId(), process.hold().holdId(), PROVIDERS.token);
        assertThat(request("POST", path(process, "cancel"), null, token).statusCode()).isEqualTo(200);
        assertThat(PROVIDERS.cancellations).hasValue(1);
        assertThat(PROVIDERS.cancellationBody.get()).isEmpty();
        assertThat(repository.find(process.id()).orElseThrow().isPending()).isFalse();
    }

    @Test
    void theRemovedAbandonRouteCannotChangeAPendingProcess() throws Exception {
        var process = seed(false);
        assertThat(request("POST", path(process, "abandon"), null, token).statusCode()).isEqualTo(404);
        assertThat(repository.find(process.id()).orElseThrow()).isEqualTo(process);
        assertThat(PROVIDERS.cancellations).hasValue(0);
    }

    @Test
    void ownershipIsCheckedBeforeAcknowledgingReplay() throws Exception {
        var process = seed(true);
        // Se conserva el registro existente; un JWT válido de otro usuario no puede operarlo.
        String login = "other_" + UUID.randomUUID().toString().substring(0, 12);
        String password = UUID.randomUUID().toString();
        request("POST", "/api/register", Map.of("login", login, "password", password,
                "firstName", "Otro", "lastName", "Ficticio", "email", login + "@example.test"), null);
        var auth = request("POST", "/api/authenticate", Map.of("username", login, "password", password), null);
        String other = json.readTree(auth.body()).path("id_token").asString();
        assertThat(request("POST", path(process, "cancel"), null, other).statusCode()).isEqualTo(404);
        assertThat(request("POST", path(process, "cancel"), null, null).statusCode()).isEqualTo(401);
        assertThat(PROVIDERS.cancellations).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid/cancel", "00000000-0000-0000-0000-000000000000/cancel"})
    void invalidAndMissingIdsNeverCallCentral(String suffix) throws Exception {
        var response = request("POST", "/api/reservation-processes/" + suffix, null, token);
        assertThat(response.statusCode()).isIn(400, 404);
        assertThat(PROVIDERS.cancellations).hasValue(0);
    }

    @Test
    void anIncompatibleStateIsRejectedBeforeEffectsWithoutARetryBody() throws Exception {
        var pending = seed(false);
        assertThat(request("POST", path(pending, "cancel"), null, token).statusCode()).isEqualTo(409);
        assertThat(PROVIDERS.cancellations).hasValue(0);
        assertThat(PROVIDERS.appointmentQueries).hasValue(0);
    }

    @Test
    void historicalCancellationDoesNotStealTheOtherCreationSlot() throws Exception {
        var confirmed = seed(true);
        var pending = seed(false);
        unavailable();
        request("POST", path(confirmed, "cancel"), null, token);
        assertThat(repository.findPending(owner)).map(ReservationProcess::id).contains(pending.id());
        var result = request("GET", "/api/reservations", null, token);
        var body = json.readTree(result.body());
        assertThat(body.path("pending").path("processId").asString()).isEqualTo(pending.id().toString());
        assertThat(body.path("history").path("totalElements").asLong()).isEqualTo(1);
        assertThat(body.path("history").path("content").get(0).path("cancellation").path("status").asString()).isEqualTo("RETRY_REQUIRED");
        assertThat(PROVIDERS.cancellations).hasValue(3);
    }

    @Test
    void recoveryReconcilesCancelledConfirmationWhileGetOnlyReads() throws Exception {
        var process = seed(true); unavailable();
        request("POST", path(process, "cancel"), null, token);
        int queries = PROVIDERS.appointmentQueries.get();
        central(process, "CANCELLED");
        var local = request("GET", "/api/reservation-processes/" + process.id(), null, token);
        assertThat(json.readTree(local.body()).path("state").asString()).isEqualTo("CONFIRMED");
        assertThat(PROVIDERS.appointmentQueries).hasValue(queries);
        clock.value.set(NOW.plusSeconds(3601)); recover.recover(process.id());
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(PROVIDERS.cancellations).hasValue(3);
        assertThat(repository.find(process.id()).orElseThrow().cancellation()).isNull();
    }

    @Test
    void automaticRetriesStopAndConcurrentExplicitRetriesNeedNoVersion() throws Exception {
        var process = seed(true); unavailable();
        request("POST", path(process, "cancel"), null, token);
        assertThat(repository.find(process.id()).orElseThrow().cancellation()).isEqualTo(CancellationState.RETRY_REQUIRED);
        for (int i = 1; i <= 2; i++) {
            clock.value.set(NOW.plusSeconds(i * 3601L)); reviews.recoverDue();
        }
        assertThat(PROVIDERS.cancellations).hasValue(3);
        var response = request("GET", "/api/reservation-processes/" + process.id(), null, token);
        var cancellation = json.readTree(response.body()).path("cancellation");
        assertThat(cancellation.path("canRetry").asBoolean()).isTrue();
        assertThat(cancellation.has("retryVersion")).isFalse();
        assertThat(cancellation.has("requestedAt")).isFalse();
        PROVIDERS.cancellationOverride.set(null);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> request("POST", path(process, "cancel"), null, token));
            var second = executor.submit(() -> request("POST", path(process, "cancel"), null, token));
            assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isIn(200, 202);
            assertThat(second.get(10, TimeUnit.SECONDS).statusCode()).isIn(200, 202);
        }
        assertThat(PROVIDERS.cancellations).hasValue(4);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
    }

    @Test
    void restAndInboxAreIdempotentAndLateConfirmationCannotUndoCancellation() throws Exception {
        var process = seed(true);
        request("POST", path(process, "cancel"), null, token);
        apply(process, "AppointmentCancelled", "cancel-event");
        apply(process, "AppointmentCancelled", "cancel-event");
        apply(process, "AppointmentConfirmed", "old-confirmation");
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_processed_event", Integer.class)).isEqualTo(2);
    }

    @Test
    void failedCommitBeforeAcceptingDoesNotExecuteCentralEffect() throws Exception {
        var process = seed(true);
        faults.arm(CommitFaultDataSource.Mode.BEFORE, "update reservation_process", 1);
        assertThat(request("POST", path(process, "cancel"), null, token).statusCode()).isEqualTo(503);
        assertThat(PROVIDERS.cancellations).hasValue(0);
        assertThat(repository.find(process.id()).orElseThrow().cancellation()).isNull();
    }

    @Test
    void commitResponseLostAfterAcceptanceReturnsAnErrorAndRecoveryReadsTheIntent() throws Exception {
        var process = seed(true);
        faults.arm(CommitFaultDataSource.Mode.AFTER, "update reservation_process", 1);
        assertThat(request("POST", path(process, "cancel"), null, token).statusCode()).isEqualTo(503);
        assertThat(PROVIDERS.cancellations).hasValue(0);
        assertThat(repository.find(process.id()).orElseThrow().cancellation()).isEqualTo(CancellationState.PENDING);
        recover.recover(process.id());
        assertThat(PROVIDERS.cancellations).hasValue(1);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void uncertainClaimNeverGrantsAnUnverifiedPostPermission(CommitFaultDataSource.Mode mode) {
        var process = seed(true);
        repository.requestCancellation(owner, process.id(), NOW);
        faults.arm(mode, "update reservation_process", 1);
        assertThatThrownBy(() -> recover.recover(process.id())).isInstanceOf(
                com.prog2.appointment.reservation.domain.exception.ReservationStorageException.class);
        assertThat(PROVIDERS.cancellations).hasValue(0);
        assertThat(PROVIDERS.appointmentQueries).hasValue(0);
        assertThat(repository.find(process.id()).orElseThrow().cancellation()).isEqualTo(CancellationState.PENDING);
        faults.clear(); clock.value.set(NOW.plusSeconds(7200));
        recover.recover(process.id());
        assertThat(PROVIDERS.cancellations).hasValue(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void aCancellationResultCommitErrorIsNotRescuedAndRecoveryUsesCentralEvidence(CommitFaultDataSource.Mode mode) throws Exception {
        var process = seed(true);
        PROVIDERS.cancellationOverride.set(exchange -> {
            faults.arm(mode, "update reservation_process", 1);
            TestIntegrationSupport.respond(exchange, 200, json.writeValueAsString(Map.of(
                    "reservationProcessId", process.hold().centralProcessId(), "reservationId", 8251,
                    "status", "CANCELLED", "cancelledAt", NOW.toString())));
        });
        assertThat(request("POST", path(process, "cancel"), null, token).statusCode()).isEqualTo(503);
        assertThat(PROVIDERS.cancellations).hasValue(1);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(
                mode == CommitFaultDataSource.Mode.AFTER ? ReservationPhase.CANCELLED : ReservationPhase.CONFIRMED);
        faults.clear(); central(process, "CANCELLED"); clock.value.set(NOW.plusSeconds(7200));
        recover.recover(process.id());
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(PROVIDERS.cancellations).hasValue(1);
    }

    @Test
    void aPersistedInterruptedClaimRequiresReconciliationBeforeAnotherPost() {
        var process = seed(true);
        repository.requestCancellation(owner, process.id(), NOW);
        repository.beginCancellationAttempt(process.id(), NOW, Duration.ofSeconds(10));
        clock.value.set(NOW.plusSeconds(11));
        recover.recover(process.id());
        assertThat(PROVIDERS.appointmentQueries).hasValue(1);
        assertThat(PROVIDERS.cancellations).hasValue(1);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
    }

    @Test
    void blockedCancellationWorkerDoesNotBlockThePhoneScheduler() throws Exception {
        var confirmed = seed(true);
        repository.requestCancellation(owner, confirmed.id(), NOW);
        var pending = seed(false);
        repository.update(pending.id(), p -> p.apply(phoneRequest(p), NOW));
        repository.submitPhone(owner, pending.id(), new SubmitPhoneRequest(UUID.randomUUID(), "1234567"), ReservationTestSupport.GROUP, NOW);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        PROVIDERS.cancellationOverride.set(exchange -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Fictional cancellation gate timed out"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
            TestIntegrationSupport.respond(exchange, 503, "{}");
        });
        var future = scheduler.schedule(reviews::recoverDue, Instant.now());
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var publication = scheduler.schedule(phones::dispatch, Instant.now());
            assertThat(publisher.entered.get().await(2, TimeUnit.SECONDS)).isTrue();
            publication.get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void migrationFromV5PreservesAccountsProcessAndHistoricalNames() throws Exception {
        var process = seed(true);
        String baseUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getFirstMappedPort() + "/";
        try (var connection = DriverManager.getConnection(baseUrl, "root", MYSQL.getPassword());
                var statement = connection.createStatement()) { statement.execute("CREATE DATABASE cancellation_upgrade"); }
        String url = baseUrl + "cancellation_upgrade";
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("5").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, "root", MYSQL.getPassword()));
        old.update("INSERT INTO app_user SELECT * FROM cancellation.app_user WHERE id=?", owner);
        final String columns = "id,owner_id,professional_id,appointment_date,start_time,patient_first_name,patient_last_name,"
                + "phase,hold_id,central_process_id,expires_at,request_event_id,reservation_id,reason_code,version,write_id,"
                + "closed_at,abandonment_pending,created_at,updated_at,professional_first_name,professional_last_name";
        old.update("INSERT INTO reservation_process (" + columns + ") SELECT " + columns.replace("abandonment_pending", "FALSE").replace("cancellation_kind", "IF(cancellation_status IS NULL,NULL,'CANCEL')")
                + " FROM cancellation.reservation_process WHERE id=?", process.id().toString());
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        assertThat(old.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
        var row = old.queryForMap("SELECT * FROM reservation_process WHERE id=?", process.id().toString());
        assertThat(row).containsEntry("phase", "CONFIRMED")
                .containsEntry("professional_first_name", "Profesional");
        assertThat(row).doesNotContainKeys("cancellation_attempts", "cancellation_retry_version");
        assertThat(row.get("cancellation_status")).isNull();
        assertThat(row.get("active_owner_id")).isNull();
    }

    @Test
    void onlyOneConcurrentSqlClaimCanOwnTheCancellationExecution() throws Exception {
        var p = seed(true); repository.requestCancellation(owner, p.id(), NOW);
        var gate = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> { gate.await(); return repository.beginCancellationAttempt(p.id(), NOW, Duration.ofSeconds(30)); });
            var b = workers.submit(() -> { gate.await(); return repository.beginCancellationAttempt(p.id(), NOW, Duration.ofSeconds(30)); });
            gate.countDown();
            assertThat(java.util.stream.Stream.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Optional::isPresent).count()).isEqualTo(1);
        }
        assertThat(PROVIDERS.cancellations).hasValue(0);
    }

    @Test
    void aLateFailureCannotStopANewerExecutionOrUndoAConfirmedCancellation() {
        var p = seed(true); repository.requestCancellation(owner, p.id(), NOW);
        var old = repository.beginCancellationAttempt(p.id(), NOW, Duration.ofSeconds(30)).orElseThrow();
        repository.finishCancellationAttempt(p.id(), old.writeId(), true, "CANCELLATION_UNCERTAIN", NOW, NOW.plusSeconds(5));
        repository.requestCancellation(owner, p.id(), NOW);
        var current = repository.beginCancellationAttempt(p.id(), NOW, Duration.ofSeconds(30)).orElseThrow();
        repository.finishCancellationAttempt(p.id(), old.writeId(), true, "CANCELLATION_REJECTED", NOW, NOW);
        assertThat(repository.find(p.id()).orElseThrow().writeId()).isEqualTo(current.writeId());
        assertThat(repository.find(p.id()).orElseThrow().cancellation()).isEqualTo(CancellationState.PENDING);
        repository.confirmCancellation(p.id(), new CancellationEvidence(p.hold().centralProcessId(), 8251L, NOW), NOW);
        repository.finishCancellationAttempt(p.id(), current.writeId(), true, "CANCELLATION_UNCERTAIN", NOW, NOW);
        assertThat(repository.find(p.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CANCELLED);
        assertThat(repository.find(p.id()).orElseThrow().cancellation()).isNull();
    }

    @Test
    void v12ConservativelyMigratesPendingStoppedAndResolvedIntentionsWithoutLosingOtherData() throws Exception {
        String baseUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getFirstMappedPort() + "/";
        try (var connection = DriverManager.getConnection(baseUrl, "root", MYSQL.getPassword());
                var statement = connection.createStatement()) { statement.execute("CREATE DATABASE cancellation_v11"); }
        String url = baseUrl + "cancellation_v11";
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("11").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, "root", MYSQL.getPassword()));
        old.update("INSERT INTO app_user SELECT * FROM cancellation.app_user WHERE id=?", owner);
        String columns = "id,owner_id,professional_id,appointment_date,start_time,patient_first_name,patient_last_name,"
                + "phase,hold_id,central_process_id,expires_at,request_event_id,reservation_id,reason_code,version,write_id,"
                + "closed_at,created_at,updated_at,professional_first_name,professional_last_name,cancellation_status,recovery_next_attempt_at";
        var ids = new java.util.ArrayList<UUID>();
        var phases = new java.util.ArrayList<String>();
        String[] previous = {"PENDING", "PENDING", "REJECTED", "RESOLVED"};
        int[] attempts = {0, 3, 1, 3};
        for (int i=0;i<previous.length;i++) {
            var process = seed(true); ids.add(process.id());
            if (i==3) repository.confirmCancellation(process.id(), new CancellationEvidence(process.hold().centralProcessId(), 8251L, NOW), NOW);
            phases.add(repository.find(process.id()).orElseThrow().phase().name());
            old.update("INSERT INTO reservation_process (" + columns + ") SELECT " + columns
                    + " FROM cancellation.reservation_process WHERE id=?", process.id().toString());
            old.update("UPDATE reservation_process SET cancellation_status=?, cancellation_requested_at=?,"
                    + "cancellation_retry_version=7,cancellation_attempts=?,cancellation_next_attempt_at=?,"
                    + "cancellation_last_failure='CANCELLATION_UNCERTAIN',recovery_next_attempt_at=? WHERE id=?",
                    previous[i], java.sql.Timestamp.from(NOW), attempts[i], java.sql.Timestamp.from(NOW.plusSeconds(60)),
                    java.sql.Timestamp.from(NOW.plusSeconds(30)), process.id().toString());
        }
        var waiting = seed(false); repository.accept(phoneRequest(waiting));
        var phoneId = UUID.randomUUID(); repository.submitPhone(owner, waiting.id(),
                new SubmitPhoneRequest(phoneId, "1234567"), ReservationTestSupport.GROUP, NOW);
        old.update("INSERT INTO reservation_process (" + columns + ") SELECT " + columns
                + " FROM cancellation.reservation_process WHERE id=?", waiting.id().toString());
        String phoneColumns = "id,sequence_id,process_id,group_id,central_process_id,hold_id,request_event_id,phone_number,"
                + "occurred_at,expires_at,published_at,closed_at,cycle_attempt,next_attempt_at,last_failure,version";
        old.update("INSERT INTO reservation_phone_submission (" + phoneColumns + ") SELECT " + phoneColumns
                + " FROM cancellation.reservation_phone_submission");
        old.update("INSERT INTO reservation_processed_event SELECT * FROM cancellation.reservation_processed_event");
        var phone = jdbc.queryForMap("SELECT * FROM reservation_phone_submission WHERE id=?", phoneId.toString());
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        for (int i=0;i<ids.size();i++) {
            var row = old.queryForMap("SELECT * FROM reservation_process WHERE id=?", ids.get(i).toString());
            assertThat(row).containsEntry("phase", phases.get(i));
            String expectedStatus = switch (i) {
                case 0 -> "PENDING";
                case 3 -> null;
                default -> "RETRY_REQUIRED";
            };
            assertThat(row.get("cancellation_status")).isEqualTo(expectedStatus);
            assertThat(row).doesNotContainKeys("cancellation_requested_at", "cancellation_retry_version", "cancellation_attempts",
                    "cancellation_next_attempt_at", "cancellation_last_failure");
            if (i<3) {
                var next = old.queryForObject("SELECT recovery_next_attempt_at FROM reservation_process WHERE id=?",
                        (rs,n) -> rs.getTimestamp(1).toInstant(), ids.get(i).toString());
                assertThat(next).isEqualTo(NOW.plusSeconds(60));
                assertThat(row.get("reason_code")).isEqualTo("CANCELLATION_UNCERTAIN");
            }
            assertThat(row.get("central_process_id")).isEqualTo(repository.find(ids.get(i)).orElseThrow().hold().centralProcessId());
        }
        assertThat(old.queryForMap("SELECT * FROM reservation_phone_submission WHERE id=?", phoneId.toString())).isEqualTo(phone);
        assertThat(old.queryForList("SELECT event_id FROM reservation_processed_event", String.class))
                .containsExactlyElementsOf(jdbc.queryForList("SELECT event_id FROM reservation_processed_event", String.class));
        assertThat(old.queryForObject("SELECT login FROM app_user WHERE id=?", String.class, owner))
                .isEqualTo(jdbc.queryForObject("SELECT login FROM app_user WHERE id=?", String.class, owner));
    }

    static final class TestClock extends Clock {
        final AtomicReference<Instant> value = new AtomicReference<>(NOW);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return value.get(); }
        // El reloj mutable tiene identidad propia; cambiar el instante no altera igualdad/hash.
        @Override public boolean equals(Object other) { return this == other; }
        @Override public int hashCode() { return System.identityHashCode(this); }
    }

    static final class TestPublisher implements PhonePublicationGateway {
        final AtomicInteger count = new AtomicInteger();
        final AtomicBoolean block = new AtomicBoolean();
        final AtomicReference<CountDownLatch> entered = new AtomicReference<>();
        final AtomicReference<CountDownLatch> release = new AtomicReference<>();
        void reset() { count.set(0); block.set(false); entered.set(new CountDownLatch(1)); release.set(new CountDownLatch(1)); }
        @Override public void publish(PhoneSubmission phone) {
            count.incrementAndGet(); entered.get().countDown();
            if (block.get()) {
                try { if (!release.get().await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Fictional publication gate timed out"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Settings {
        @Bean @Primary TestClock cancellationClock() { return new TestClock(); }
        @Bean CommitFaultDataSource commitFaultDataSource() {
            return new CommitFaultDataSource(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        }
        @Bean @Primary TestPublisher testPublisher() { return new TestPublisher(); }
    }
}

package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;

import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationProcessesUseCase;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.*;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationActionParser;
import com.prog2.appointment.reservation.infrastructure.messaging.PhoneSubmissionDispatcher;
import com.prog2.appointment.reservation.infrastructure.persistence.adapter.JpaReservationProcessAdapter;
import com.prog2.appointment.reservation.infrastructure.persistence.service.TransactionalReservationStore;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

/** MySQL, commit y HTTP reales con proveedores ficticios; no sustituye el comportamiento recuperado. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReservationRecoveryIntegrationTests.Settings.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(60)
class ReservationRecoveryIntegrationTests {
    private static final Instant NOW = ReservationProcessTests.NOW;
    private static final Duration RETRY = Duration.ofSeconds(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("reservation_recovery").withUsername("recovery_test").withPassword("fictional-database");
    static final ReservationTestSupport PROVIDERS = new ReservationTestSupport(() -> "127.0.0.1:1");
    private final int port;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final ReservationProcessRepository repository;
    private final TransactionalReservationStore store;
    private final RecoverReservationProcessesUseCase recover;
    private final ReservationExecutionGuard guard;
    private final ReservationActionParser parser;
    private final CommitFaultDataSource faults;
    private final RecoveryClock clock;
    private final RecoverReservationCancellationsUseCase cancel;
    private final PhoneSubmissionDispatcher phones;
    private final ThreadPoolTaskScheduler scheduler;
    private final CountingPublisher publisher;
    private long owner;

    ReservationRecoveryIntegrationTests(@LocalServerPort int port, JsonMapper json, JdbcTemplate jdbc,
            ReservationProcessRepository repository, TransactionalReservationStore store,
            RecoverReservationProcessesUseCase recover, ReservationExecutionGuard guard,
            ReservationActionParser parser, CommitFaultDataSource faults, RecoveryClock clock,
            RecoverReservationCancellationsUseCase cancel, PhoneSubmissionDispatcher phones,
            ThreadPoolTaskScheduler scheduler, CountingPublisher publisher) {
        this.port = port; this.json = json; this.jdbc = jdbc; this.repository = repository;
        this.store = store; this.recover = recover; this.guard = guard; this.parser = parser;
        this.faults = faults; this.clock = clock;
        this.cancel = cancel; this.phones = phones; this.scheduler = scheduler; this.publisher = publisher;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        PROVIDERS.settings().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("RESERVATION_METADATA_TIMEOUT", () -> "20ms");
        registry.add("RESERVATION_HTTP_TIMEOUT", () -> "2s");
        registry.add("RESERVATION_RETRY_DELAY", () -> "1h");
    }

    @BeforeEach
    void setup() throws Exception {
        faults.clear(); clock.value.set(NOW); PROVIDERS.reset(); publisher.count.set(0);
        jdbc.update("DELETE FROM reservation_inbox");
        jdbc.update("DELETE FROM reservation_phone_submission");
        jdbc.update("DELETE FROM reservation_process");
        String login = "recovery_" + UUID.randomUUID().toString().substring(0, 12);
        String body = json.writeValueAsString(Map.of("login", login, "password", UUID.randomUUID().toString(),
                "firstName", "Paciente", "lastName", "Ficticio", "email", login + "@example.test"));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/register"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(201);
        owner = jdbc.queryForObject("SELECT id FROM app_user WHERE login=?", Long.class, login);
    }

    @AfterEach
    void clear() { faults.clear(); }

    @AfterAll
    static void close() { PROVIDERS.close(); }

    private ReservationProcess candidate(boolean withHold) {
        var process = ReservationProcess.begin(new StartReservationRequest(UUID.randomUUID(), ReservationProcessTests.SLOT),
                new ReservationPatient(owner, "Paciente", "Ficticio"));
        return withHold ? process.acceptHold(new HoldDetails("hold-" + process.id(), "central-" + process.id(),
                NOW.plusSeconds(300))) : process;
    }

    private ReservationProcess save(ReservationProcess process) {
        assertThat(repository.begin(process).created()).isTrue();
        return repository.find(process.id()).orElseThrow();
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

    private ReservationDelivery request(ReservationProcess process) {
        String body = json.writeValueAsString(Map.of("schemaVersion", 1, "eventId", "request-" + process.id(),
                "eventType", "AdditionalInformationRequested", "occurredAt", NOW.toString(),
                "reservationProcessId", process.hold().centralProcessId(), "holdId", process.hold().holdId(),
                "requiredField", "PHONE_NUMBER", "expiresAt", process.hold().expiresAt().toString()));
        return parser.parse(new ReservationDelivery.Origin("fictional-actions", 0, 1, process.hold().centralProcessId()), body);
    }

    @Test
    void onlyOneOfTwoSqlClaimsOwnsTheInitialConfirmation() throws Exception {
        var process = save(candidate(true));
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { release.await(); return repository.beginInitialConfirmation(process.id(), NOW); });
            var b = workers.submit(() -> { release.await(); return repository.beginInitialConfirmation(process.id(), NOW); });
            release.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .filteredOn(Optional::isPresent).hasSize(1);
        }
        assertThat(repository.beginInitialConfirmation(process.id(), NOW)).isEmpty();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMING);
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void ambiguousConfirmationCommitNeverTransfersAnotherExecutorsPermission(CommitFaultDataSource.Mode mode) {
        var process = save(candidate(true));
        faults.arm(mode, "update reservation_process", 1);
        var own = repository.beginInitialConfirmation(process.id(), NOW);
        assertThat(own.isPresent()).isEqualTo(mode == CommitFaultDataSource.Mode.AFTER);
        assertThat(repository.beginInitialConfirmation(process.id(), NOW).isPresent())
                .isEqualTo(mode == CommitFaultDataSource.Mode.BEFORE);
    }

    @Test
    void reviewBudgetSurvivesANewAdapterWithoutChangingBusinessWriteId() {
        var process = save(candidate(true).beginConfirmation());
        assertThat(repository.dueRecovery(NOW, COOLDOWN, 10)).isEmpty();
        var now = NOW.plusSeconds(30);
        for (int i = 0; i < 3; i++) {
            assertThat(repository.beginRecovery(process.id(), now, 3, RETRY, COOLDOWN)).isPresent();
            now = now.plusSeconds(5);
        }
        var restarted = new JpaReservationProcessAdapter(store);
        assertThat(restarted.dueRecovery(NOW.plusSeconds(69), COOLDOWN, 10)).isEmpty();
        assertThat(restarted.beginRecovery(process.id(), NOW.plusSeconds(70), 3, RETRY, COOLDOWN)).isPresent();
        assertThat(repository.find(process.id()).orElseThrow().writeId()).isEqualTo(process.writeId());
        assertThat(jdbc.queryForMap("SELECT recovery_attempts FROM reservation_process WHERE id=?", process.id().toString()))
                .containsEntry("recovery_attempts", 1);
    }

    @Test
    void ambiguousReviewCommitConservesTheNextDateWithoutGrantingPermission() {
        var process = save(candidate(true).beginConfirmation());
        UUID id = process.id();
        faults.arm(CommitFaultDataSource.Mode.AFTER, "update reservation_process", 1);
        var observed = NOW.plusSeconds(30);
        assertThatThrownBy(() -> repository.beginRecovery(id, observed, 3, RETRY, COOLDOWN))
                .isInstanceOf(ReservationStorageException.class);
        faults.clear();
        assertThat(repository.beginRecovery(id, NOW.plusSeconds(30), 3, RETRY, COOLDOWN)).isEmpty();
        assertThat(repository.find(id).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMING);
    }

    @Test
    void recoveryConvergesToFailedAfterExpiryWithoutAUserQueryOrKafkaEvent() {
        var process = save(candidate(true).beginConfirmation());
        central(process, "FAILED"); clock.value.set(NOW.plusSeconds(301));
        recover.recoverDue();
        var current = repository.find(process.id()).orElseThrow();
        assertThat(current.phase()).isEqualTo(ReservationPhase.FAILED);
        assertThat(repository.findPending(owner)).isEmpty();
        assertThat(PROVIDERS.appointmentQueries).hasValue(1);
        assertThat(PROVIDERS.creates).hasValue(0); assertThat(PROVIDERS.confirmations).hasValue(0);
        assertThat(PROVIDERS.cancellations).hasValue(0);
        assertThat(PROVIDERS.appointmentFilters.get()).doesNotContainKeys("from", "to", "status");
    }

    @Test
    void phonePendingDoesNotInventTheMissingRequest() {
        var process = save(candidate(true).beginConfirmation());
        central(process, "PHONE_PENDING"); clock.value.set(NOW.plusSeconds(31));
        recover.recoverDue();
        var current = repository.find(process.id()).orElseThrow();
        assertThat(current.phase()).isEqualTo(ReservationPhase.WAITING_FOR_REQUEST);
        assertThat(current.requestEventId()).isNull();
        assertThat(current.canSubmitPhone(clock.instant())).isFalse();
        assertThat(repository.latestPhone(process.id())).isEmpty();
    }

    @Test
    void anEmptyListingAfterExpiryKeepsAnAttemptedConfirmationPending() {
        var process = save(candidate(true).beginConfirmation()); clock.value.set(NOW.plusSeconds(301));
        recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMING);
        assertThat(repository.findPending(owner)).isPresent();
        assertThat(PROVIDERS.confirmations).hasValue(0);
    }

    @Test
    void aLiveCreationIsNotArchivedAndAnOrphanNeverCreatesAnotherHold() {
        var process = save(candidate(false)); clock.value.set(NOW.plusSeconds(31));
        try (var _ = guard.tryAcquire(process.id()).orElseThrow()) {
            recover.recoverDue();
            assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CREATING_HOLD);
        }
        clock.value.set(NOW.plusSeconds(3632)); recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.HOLD_ATTEMPT_ABANDONED);
        assertThat(repository.findPending(owner)).isEmpty();
        assertThat(PROVIDERS.creates).hasValue(0);
    }

    @Test
    void anExpiredKnownHoldWithoutAnAttemptClosesLocally() {
        var process = save(candidate(true)); clock.value.set(process.hold().expiresAt());
        recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.EXPIRED);
        assertThat(repository.findPending(owner)).isEmpty();
        assertThat(PROVIDERS.confirmations).hasValue(0); assertThat(PROVIDERS.appointmentQueries).hasValue(0);
    }

    @Test
    void aDeferredRequestIsAppliedOnceWhenItsAssociationBecomesKnown() {
        var process = candidate(true).beginConfirmation();
        var delivery = request(process); repository.accept(delivery);
        assertThat(jdbc.queryForObject("SELECT outcome FROM reservation_inbox", String.class)).isEqualTo("DEFERRED");
        save(process); repository.replayDeferredKnown(10);
        var current = repository.find(process.id()).orElseThrow();
        assertThat(current.phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(current.requestEventId()).isEqualTo(delivery.action().correlation().eventId());
        long version = current.version();
        repository.replayDeferredKnown(10); repository.accept(delivery);
        assertThat(repository.find(process.id()).orElseThrow().version()).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_inbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT outcome FROM reservation_inbox", String.class)).isEqualTo("APPLIED");
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void replayCommitFailureKeepsOneReceiptAndCanBeRecovered(CommitFaultDataSource.Mode mode) {
        var process = candidate(true).beginConfirmation(); repository.accept(request(process)); save(process);
        faults.arm(mode, "update reservation_inbox", 1);
        assertThatThrownBy(() -> repository.replayDeferredKnown(10)).isInstanceOf(ReservationStorageException.class);
        faults.clear(); repository.replayDeferredKnown(10);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_inbox", Integer.class)).isEqualTo(1);
    }

    @Test
    void anOldDeferredRequestCannotReopenAConfirmedProcess() {
        var process = candidate(true).beginConfirmation(); repository.accept(request(process));
        process = process.reconcile(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251,
                CentralAppointmentState.CONFIRMED, null), NOW);
        save(process); repository.replayDeferredKnown(10);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(jdbc.queryForObject("SELECT outcome FROM reservation_inbox", String.class)).isEqualTo("IGNORED");
    }

    @Test
    void twoReplaysDoNotApplyTheSameReceiptTwice() throws Exception {
        var process = candidate(true).beginConfirmation(); repository.accept(request(process)); save(process);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { release.await(); repository.replayDeferredKnown(10); return true; });
            var b = workers.submit(() -> { release.await(); repository.replayDeferredKnown(10); return true; });
            release.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(repository.find(process.id()).orElseThrow().version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_inbox", Integer.class)).isEqualTo(1);
    }

    @Test
    void aDeferredReceiptCannotBindToADifferentHold() {
        var original = candidate(true); repository.accept(request(original));
        var different = ReservationProcess.begin(new StartReservationRequest(original.id(), original.selection()), original.patient())
                .acceptHold(new HoldDetails("different-hold", original.hold().centralProcessId(), original.hold().expiresAt()))
                .beginConfirmation();
        save(different); repository.replayDeferredKnown(10);
        assertThat(repository.find(different.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMING);
        assertThat(jdbc.queryForMap("SELECT outcome,rejection FROM reservation_inbox"))
                .containsEntry("outcome", "REJECTED").containsEntry("rejection", "HOLD_MISMATCH");
    }

    @ParameterizedTest
    @ValueSource(strings = {"group", "fingerprint"})
    void replayRevalidatesStoredContentInsteadOfTrustingItsOldClassification(String change) {
        var process = candidate(true).beginConfirmation(); repository.accept(request(process)); save(process);
        if ("group".equals(change))
            jdbc.update("UPDATE reservation_inbox SET payload=JSON_SET(payload,'$.groupId','another-group')");
        else jdbc.update("UPDATE reservation_inbox SET fingerprint=?", "0".repeat(64));
        repository.replayDeferredKnown(10);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMING);
        assertThat(jdbc.queryForMap("SELECT outcome,rejection FROM reservation_inbox"))
                .containsEntry("outcome", "REJECTED").containsEntry("rejection", "INVALID_STORED_ACTION");
    }

    @Test
    void aFutureActionTypeNeverBecomesASupportedRequestDuringReplay() {
        var process = candidate(true).beginConfirmation();
        var original = request(process);
        repository.accept(parser.parse(original.origin(),
                original.payload().replace("AdditionalInformationRequested", "FutureAction")));
        save(process); repository.replayDeferredKnown(10);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMING);
        assertThat(jdbc.queryForMap("SELECT outcome,rejection FROM reservation_inbox"))
                .containsEntry("outcome", "DEFERRED").containsEntry("rejection", "UNHANDLED_ACTION");
    }

    @Test
    void aRecentAckKeepsTheVerificationDelayAndLaterRecoveryClosesTheSamePhone() {
        var process = save(candidate(true).beginConfirmation()); repository.accept(request(process));
        UUID phoneId = UUID.randomUUID();
        repository.submitPhone(owner, process.id(), new SubmitPhoneRequest(phoneId, "1234567"),
                ReservationTestSupport.GROUP, NOW);
        repository.beginRecovery(process.id(), NOW.plusSeconds(30), 3, RETRY, COOLDOWN);
        repository.beginPhonePublication(phoneId, NOW.plusSeconds(31), 3, RETRY, COOLDOWN);
        repository.phonePublished(phoneId, NOW.plusSeconds(33));
        clock.value.set(NOW.plusSeconds(35)); recover.recoverDue();
        assertThat(PROVIDERS.appointmentQueries).hasValue(0);
        central(process, "FAILED"); clock.value.set(NOW.plusSeconds(3636)); recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.FAILED);
        var phone = repository.findPhone(phoneId).orElseThrow();
        assertThat(phone.cycleAttempt()).isEqualTo(1); assertThat(phone.closedAt()).isNotNull();
        assertThat(repository.latestPhone(process.id()).orElseThrow().id()).isEqualTo(phoneId);
    }

    @Test
    void exhaustedCancellationIsOnlyVerifiedWithoutAnotherPostOrCycle() {
        var process = candidate(true).beginConfirmation();
        process = save(process.reconcile(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251, CentralAppointmentState.CONFIRMED, null), NOW));
        UUID id = process.id(); repository.requestCancellation(owner, id, CancellationKind.CANCEL, null, NOW);
        for (int i = 0; i < 3; i++) repository.beginCancellationAttempt(id, NOW.plusSeconds(i), Duration.ofSeconds(1));
        central(process, "CONFIRMED"); clock.value.set(NOW.plusSeconds(40)); recover.recoverDue();
        var current = repository.find(id).orElseThrow();
        assertThat(current.cancellation().attempts()).isEqualTo(3);
        assertThat(current.cancellation().retryVersion()).isZero();
        assertThat(current.cancellation().state()).isEqualTo(CancellationState.PENDING);
        assertThat(PROVIDERS.cancellations).hasValue(0);
    }

    @Test
    void slowReadsAndCancellationDoNotBlockPhonePublication() throws Exception {
        var read = candidate(true).beginConfirmation();
        read = save(read.reconcile(new CentralAppointment(read.hold().centralProcessId(),
                read.patient().externalPatientId(), read.selection(), 8251, CentralAppointmentState.CONFIRMED, null), NOW));
        repository.requestCancellation(owner, read.id(), CancellationKind.CANCEL, null, NOW);
        for (int i = 0; i < 3; i++) repository.beginCancellationAttempt(read.id(), NOW.plusSeconds(i), Duration.ofSeconds(1));
        clock.value.set(NOW.plusSeconds(40));
        var cancelled = candidate(true).beginConfirmation();
        cancelled = save(cancelled.reconcile(new CentralAppointment(cancelled.hold().centralProcessId(),
                cancelled.patient().externalPatientId(), cancelled.selection(), 8252, CentralAppointmentState.CONFIRMED, null), NOW));
        repository.requestCancellation(owner, cancelled.id(), CancellationKind.CANCEL, null, clock.instant());
        var waiting = save(candidate(true).beginConfirmation()); repository.accept(request(waiting));
        UUID phone = UUID.randomUUID();
        repository.submitPhone(owner, waiting.id(), new SubmitPhoneRequest(phone, "1234567"),
                ReservationTestSupport.GROUP, clock.instant());
        var reading = new CountDownLatch(1); var cancelling = new CountDownLatch(1); var release = new CountDownLatch(1);
        PROVIDERS.appointmentsOverride.set(exchange -> {
            reading.countDown(); awaitGate(release);
            exchange.getResponseHeaders().set("X-Total-Count", "0");
            TestIntegrationSupport.respond(exchange, 200, "[]");
        });
        var cancelledId = cancelled.id();
        PROVIDERS.cancellationOverride.set(exchange -> {
            cancelling.countDown(); awaitGate(release);
            TestIntegrationSupport.respond(exchange, 200, json.writeValueAsString(Map.of(
                    "reservationProcessId", "central-" + cancelledId, "reservationId", 8252,
                    "status", "CANCELLED", "cancelledAt", clock.instant().toString())));
        });
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
        var readTask = scheduler.schedule(recover::recoverDue, Instant.now());
        var cancelTask = scheduler.schedule(() -> cancel.recover(cancelledId), Instant.now());
        try {
            assertThat(reading.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(cancelling.await(3, TimeUnit.SECONDS)).isTrue();
            scheduler.schedule(phones::dispatch, Instant.now()).get(3, TimeUnit.SECONDS);
            assertThat(publisher.count).hasValue(1);
            assertThat(repository.findPhone(phone).orElseThrow().publishedAt()).isNotNull();
        } finally {
            release.countDown(); readTask.get(5, TimeUnit.SECONDS); cancelTask.get(5, TimeUnit.SECONDS);
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Fictional HTTP gate timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(failure);
        }
    }

    @Test
    void v7PreservesAV6DatabaseAndSchedulesItsExistingProcess() throws Exception {
        var process = save(candidate(true).beginConfirmation());
        repository.accept(request(process));
        UUID phoneId = UUID.randomUUID();
        repository.submitPhone(owner, process.id(), new SubmitPhoneRequest(phoneId, "1234567"),
                ReservationTestSupport.GROUP, NOW);
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS recovery_previous"); statement.execute("CREATE DATABASE recovery_previous");
        }
        String url = MYSQL.getJdbcUrl().replace("/reservation_recovery", "/recovery_previous");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("6").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, "root", MYSQL.getPassword()));
        old.update("INSERT INTO app_user SELECT * FROM reservation_recovery.app_user");
        old.update("INSERT INTO user_identifier SELECT * FROM reservation_recovery.user_identifier");
        old.update("INSERT INTO user_authority SELECT * FROM reservation_recovery.user_authority");
        final String columns = "id,owner_id,professional_id,appointment_date,start_time,patient_first_name,patient_last_name,"
                + "phase,hold_id,central_process_id,expires_at,request_event_id,reservation_id,reason_code,version,write_id,"
                + "closed_at,abandonment_pending,created_at,updated_at,professional_first_name,professional_last_name,"
                + "cancellation_kind,cancellation_status,cancellation_requested_at,cancellation_retry_version,"
                + "cancellation_attempts,cancellation_next_attempt_at,cancellation_last_failure";
        old.update("INSERT INTO reservation_process (" + columns + ") SELECT " + columns
                + " FROM reservation_recovery.reservation_process WHERE id=?", process.id().toString());
        old.update("INSERT INTO reservation_inbox SELECT * FROM reservation_recovery.reservation_inbox");
        final String phoneColumns = "id,sequence_id,process_id,group_id,central_process_id,hold_id,request_event_id,"
                + "phone_number,occurred_at,expires_at,published_at,closed_at,cycle_attempt,next_attempt_at,last_failure,version";
        old.update("INSERT INTO reservation_phone_submission (" + phoneColumns + ") SELECT " + phoneColumns
                + " FROM reservation_recovery.reservation_phone_submission");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        assertThat(old.queryForMap("SELECT phase,recovery_attempts,recovery_next_attempt_at FROM reservation_process"))
                .containsEntry("phase", "PHONE_QUEUED").containsEntry("recovery_attempts", 0)
                .containsEntry("recovery_next_attempt_at", null);
        assertThat(old.queryForObject("SELECT COUNT(*) FROM user_identifier", Integer.class))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM user_identifier", Integer.class));
        assertThat(old.queryForObject("SELECT id FROM reservation_phone_submission", String.class)).isEqualTo(phoneId.toString());
        assertThat(old.queryForObject("SELECT COUNT(*) FROM reservation_inbox", Integer.class)).isEqualTo(1);
    }

    static final class RecoveryClock extends Clock {
        final AtomicReference<Instant> value;
        private final ZoneId zone;
        RecoveryClock() { this(new AtomicReference<>(NOW), ZoneOffset.UTC); }
        private RecoveryClock(AtomicReference<Instant> value, ZoneId zone) { this.value = value; this.zone = zone; }
        @Override public Instant instant() { return value.get(); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new RecoveryClock(value, zone); }
        @Override public boolean equals(Object other) { return this == other; }
        @Override public int hashCode() { return System.identityHashCode(this); }
    }

    static final class CountingPublisher implements PhonePublicationGateway {
        final AtomicInteger count = new AtomicInteger();
        @Override public void publish(PhoneSubmission phone) { count.incrementAndGet(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Settings {
        @Bean @Primary RecoveryClock recoveryClock() { return new RecoveryClock(); }
        @Bean @Primary CountingPublisher countingPublisher() { return new CountingPublisher(); }
        @Bean CommitFaultDataSource commitFaultDataSource() {
            return new CommitFaultDataSource(new DriverManagerDataSource(
                    MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        }
    }
}

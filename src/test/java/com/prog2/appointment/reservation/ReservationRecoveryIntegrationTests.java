package com.prog2.appointment.reservation;

import com.prog2.appointment.reservation.application.service.ReservationExecutionGuard;

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
    private final AppointmentHoldGateway holds;
    private long owner;

    ReservationRecoveryIntegrationTests(@LocalServerPort int port, JsonMapper json, JdbcTemplate jdbc,
            ReservationProcessRepository repository, TransactionalReservationStore store,
            RecoverReservationProcessesUseCase recover, ReservationExecutionGuard guard,
            ReservationActionParser parser, CommitFaultDataSource faults, RecoveryClock clock,
            RecoverReservationCancellationsUseCase cancel, PhoneSubmissionDispatcher phones,
            ThreadPoolTaskScheduler scheduler, CountingPublisher publisher, AppointmentHoldGateway holds) {
        this.port = port; this.json = json; this.jdbc = jdbc; this.repository = repository;
        this.store = store; this.recover = recover; this.guard = guard; this.parser = parser;
        this.faults = faults; this.clock = clock;
        this.cancel = cancel; this.phones = phones; this.scheduler = scheduler; this.publisher = publisher; this.holds = holds;
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
        jdbc.update("DELETE FROM reservation_processed_event");
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

    private ReservationAction request(ReservationProcess process) {
        String body = json.writeValueAsString(Map.of("schemaVersion", 1, "eventId", "request-" + process.id(),
                "eventType", "AdditionalInformationRequested", "occurredAt", NOW.toString(),
                "reservationProcessId", process.hold().centralProcessId(), "holdId", process.hold().holdId(),
                "requiredField", "PHONE_NUMBER", "expiresAt", process.hold().expiresAt().toString()));
        return parser.parse(process.hold().centralProcessId(), body).orElseThrow();
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
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMATION_PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void aConfirmationCommitErrorNeverGrantsPermissionEvenIfItPersisted(CommitFaultDataSource.Mode mode) {
        var process = save(candidate(true));
        faults.arm(mode, "update reservation_process", 1);
        assertThatThrownBy(() -> repository.beginInitialConfirmation(process.id(), NOW))
                .isInstanceOf(ReservationStorageException.class);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(
                mode == CommitFaultDataSource.Mode.AFTER ? ReservationPhase.CONFIRMATION_PENDING : ReservationPhase.HELD);
        faults.clear();
        assertThat(repository.beginInitialConfirmation(process.id(), NOW).isPresent())
                .isEqualTo(mode == CommitFaultDataSource.Mode.BEFORE);
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Mode.class, names = {"BEFORE", "AFTER"})
    void recoveryReadsThePersistedHoldCheckpointWithoutRepeatingCreation(CommitFaultDataSource.Mode mode) {
        var process = save(candidate(false));
        var hold = holds.create(process);
        assertThat(PROVIDERS.creates).hasValue(1);
        faults.arm(mode, "update reservation_process", 1);
        assertThatThrownBy(() -> repository.update(process.id(), value -> value.acceptHold(hold)))
                .isInstanceOf(ReservationStorageException.class);
        assertThat(PROVIDERS.confirmations).hasValue(0);
        var durable = repository.find(process.id()).orElseThrow();
        assertThat(durable.hold()).isEqualTo(mode == CommitFaultDataSource.Mode.AFTER ? hold : null);
        faults.clear(); clock.value.set(NOW.plusSeconds(31));
        // Este caso verifica el checkpoint SQL con canal disponible; el broker se cubre en su suite.
        var ready = org.mockito.Mockito.mock(ReservationMessagingReadiness.class);
        var continuation = new com.prog2.appointment.reservation.application.usecases.StartReservationUseCaseImpl(
                org.mockito.Mockito.mock(ReservationPatientGateway.class), org.mockito.Mockito.mock(ReservableSlotGateway.class),
                holds, repository, ready, guard, clock, 3);
        var restarted = new com.prog2.appointment.reservation.application.usecases.RecoverReservationProcessesUseCaseImpl(
                repository, org.mockito.Mockito.mock(CentralAppointmentGateway.class), continuation, guard,
                cancel, clock, new ReservationRecoveryPolicy(RETRY, COOLDOWN, COOLDOWN));
        restarted.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(
                mode == CommitFaultDataSource.Mode.AFTER ? ReservationPhase.WAITING_FOR_REQUEST : ReservationPhase.FAILED);
        assertThat(PROVIDERS.creates).hasValue(1);
        assertThat(PROVIDERS.confirmations).hasValue(mode == CommitFaultDataSource.Mode.AFTER ? 1 : 0);
    }

    @Test
    void anAmbiguousConfirmationCheckpointIsReconciledWithoutPostingAgain() {
        var process = save(candidate(true));
        faults.arm(CommitFaultDataSource.Mode.AFTER, "update reservation_process", 1);
        assertThatThrownBy(() -> repository.beginInitialConfirmation(process.id(), NOW))
                .isInstanceOf(ReservationStorageException.class);
        faults.clear(); clock.value.set(NOW.plusSeconds(31));
        recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMATION_PENDING);
        assertThat(PROVIDERS.confirmations).hasValue(0);
        central(process, "CONFIRMED"); clock.value.set(NOW.plusSeconds(3632)); recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(PROVIDERS.confirmations).hasValue(0);
    }

    @Test
    void reviewDateSurvivesANewAdapterWithoutChangingBusinessWriteId() {
        var process = save(candidate(true).beginConfirmation());
        assertThat(repository.dueRecovery(NOW, COOLDOWN, 10)).isEmpty();
        var now = NOW.plusSeconds(30);
        for (int i = 0; i < 3; i++) {
            assertThat(repository.beginRecovery(process.id(), now, RETRY, COOLDOWN)).isPresent();
            now = now.plusSeconds(5);
        }
        var restarted = new JpaReservationProcessAdapter(store);
        assertThat(restarted.dueRecovery(NOW.plusSeconds(44), COOLDOWN, 10)).isEmpty();
        assertThat(restarted.beginRecovery(process.id(), NOW.plusSeconds(45), RETRY, COOLDOWN)).isPresent();
        assertThat(repository.find(process.id()).orElseThrow().writeId()).isEqualTo(process.writeId());
        assertThat(restarted.dueRecovery(NOW.plusSeconds(49), COOLDOWN, 10)).doesNotContain(process.id());
        assertThat(restarted.dueRecovery(NOW.plusSeconds(50), COOLDOWN, 10)).contains(process.id());
    }

    @Test
    void ambiguousReviewCommitConservesTheNextDateWithoutGrantingPermission() {
        var process = save(candidate(true).beginConfirmation());
        UUID id = process.id();
        faults.arm(CommitFaultDataSource.Mode.AFTER, "update reservation_process", 1);
        var observed = NOW.plusSeconds(30);
        assertThatThrownBy(() -> repository.beginRecovery(id, observed, RETRY, COOLDOWN))
                .isInstanceOf(ReservationStorageException.class);
        faults.clear();
        assertThat(repository.beginRecovery(id, NOW.plusSeconds(30), RETRY, COOLDOWN)).isEmpty();
        assertThat(repository.find(id).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMATION_PENDING);
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
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMATION_PENDING);
        assertThat(repository.findPending(owner)).isPresent();
        assertThat(PROVIDERS.confirmations).hasValue(0);
    }

    @Test
    void aLiveCreationIsNotArchivedAndAnOrphanNeverCreatesAnotherHold() {
        var process = save(candidate(false)); clock.value.set(NOW.plusSeconds(31));
        try (var _ = guard.tryAcquire(process.id()).orElseThrow()) {
            recover.recoverDue();
            assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.HOLD_PENDING);
        }
        clock.value.set(NOW.plusSeconds(3632)); recover.recoverDue();
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.FAILED);
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
    void anUnknownProcessIsIgnoredDurablyWithoutFutureAssociationReplay() {
        var process = candidate(true).beginConfirmation();
        var action = request(process);
        repository.accept(action);
        assertThat(repository.find(process.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT event_id FROM reservation_processed_event", String.class))
                .isEqualTo(action.correlation().eventId());
        save(process);
        repository.accept(action);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMATION_PENDING);
    }

    @Test
    void aLateRequestForAnExistingFinalProcessIsDeduplicatedWithoutReopeningIt() {
        var process = candidate(true).beginConfirmation();
        process = save(process.reconcile(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251,
                CentralAppointmentState.CONFIRMED, null), NOW));
        var action = request(process);
        long version = jdbc.queryForObject("SELECT version FROM reservation_process WHERE id=?", Long.class, process.id().toString());
        repository.accept(action);
        repository.accept(action);
        assertThat(repository.find(process.id()).orElseThrow().phase()).isEqualTo(ReservationPhase.CONFIRMED);
        assertThat(jdbc.queryForObject("SELECT version FROM reservation_process WHERE id=?", Long.class, process.id().toString())).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_processed_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentDeliveryOfTheSameEventChangesTheProcessOnlyOnce() throws Exception {
        var process = save(candidate(true).beginConfirmation());
        var action = request(process);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { release.await(); repository.accept(action); return true; });
            var b = workers.submit(() -> { release.await(); repository.accept(action); return true; });
            release.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(b.get(10, TimeUnit.SECONDS)).isTrue();
        }
        var current = repository.find(process.id()).orElseThrow();
        assertThat(current.phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(jdbc.queryForObject("SELECT version FROM reservation_process WHERE id=?", Long.class, process.id().toString())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_processed_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void reusingAnEventIdForDifferentContentKeepsTheFirstEffect() {
        var process = save(candidate(true).beginConfirmation());
        var action = request(process);
        repository.accept(action);
        var changed = new ReservationAction(ReservationActionType.INVALID, action.correlation(), null, null, null);
        var current = repository.find(process.id()).orElseThrow();
        repository.accept(changed);
        assertThat(repository.find(process.id()).orElseThrow()).isEqualTo(current);
    }

    @Test
    void aMismatchedHoldOrAnUnsupportedTypeCannotChangeAnExistingProcess() {
        var process = save(candidate(true).beginConfirmation());
        var wrongHold = new ReservationAction(ReservationActionType.PHONE_REQUESTED,
                new ReservationAction.Correlation("wrong-hold", process.hold().centralProcessId(), "another-hold", null),
                process.hold().expiresAt(), null, null);
        repository.accept(wrongHold);
        repository.accept(new ReservationAction(ReservationActionType.OTHER,
                request(process).correlation(), null, null, null));
        assertThat(repository.find(process.id()).orElseThrow()).isEqualTo(process);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_processed_event", Integer.class)).isEqualTo(2);
    }

    @Test
    void aCommitResponseLostAfterSqlDoesNotReturnSuccessAndRedeliveryDoesNotRepeatEffects() {
        var process = save(candidate(true).beginConfirmation());
        var action = request(process);
        faults.arm(CommitFaultDataSource.Mode.AFTER, "insert into reservation_processed_event", 1);
        assertThatThrownBy(() -> repository.accept(action)).isInstanceOf(ReservationStorageException.class);
        faults.clear();
        var committed = repository.find(process.id()).orElseThrow();
        assertThat(committed.phase()).isEqualTo(ReservationPhase.WAITING_FOR_PHONE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_processed_event", Integer.class)).isEqualTo(1);
        repository.accept(action);
        assertThat(repository.find(process.id()).orElseThrow()).isEqualTo(committed);
    }

    @Test
    void aRecentAckKeepsTheVerificationDelayAndLaterRecoveryClosesTheSamePhone() {
        var process = save(candidate(true).beginConfirmation()); repository.accept(request(process));
        UUID phoneId = UUID.randomUUID();
        repository.submitPhone(owner, process.id(), new SubmitPhoneRequest(phoneId, "1234567"),
                ReservationTestSupport.GROUP, NOW);
        repository.beginRecovery(process.id(), NOW.plusSeconds(30), RETRY, COOLDOWN);
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
        UUID id = process.id(); repository.requestCancellation(owner, id, NOW);
        var claim = repository.beginCancellationAttempt(id, NOW, Duration.ofSeconds(1)).orElseThrow();
        repository.finishCancellationAttempt(id, claim.writeId(), true, "CANCELLATION_UNCERTAIN", NOW, NOW.plusSeconds(5));
        central(process, "CONFIRMED"); clock.value.set(NOW.plusSeconds(40)); recover.recoverDue();
        var current = repository.find(id).orElseThrow();
        assertThat(current.cancellation()).isEqualTo(CancellationState.RETRY_REQUIRED);
        assertThat(PROVIDERS.cancellations).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aSlowReviewOrCancellationDoesNotBlockPhonePublication(boolean readOnly) throws Exception {
        var process = candidate(true).beginConfirmation();
        process = save(process.reconcile(new CentralAppointment(process.hold().centralProcessId(),
                process.patient().externalPatientId(), process.selection(), 8251, CentralAppointmentState.CONFIRMED, null), NOW));
        repository.requestCancellation(owner, process.id(), NOW);
        if (readOnly) {
            var claim = repository.beginCancellationAttempt(process.id(), NOW, Duration.ofSeconds(1)).orElseThrow();
            repository.finishCancellationAttempt(process.id(), claim.writeId(), true, "CANCELLATION_UNCERTAIN", NOW, NOW.plusSeconds(5));
        }
        central(process, "CONFIRMED");
        clock.value.set(NOW.plusSeconds(40));
        var waiting = save(candidate(true).beginConfirmation()); repository.accept(request(waiting));
        UUID phone = UUID.randomUUID();
        repository.submitPhone(owner, waiting.id(), new SubmitPhoneRequest(phone, "1234567"),
                ReservationTestSupport.GROUP, clock.instant());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        PROVIDERS.appointmentsOverride.set(exchange -> {
            entered.countDown(); awaitGate(release);
            exchange.getResponseHeaders().set("X-Total-Count", "0");
            TestIntegrationSupport.respond(exchange, 200, "[]");
        });
        var centralId = process.hold().centralProcessId();
        PROVIDERS.cancellationOverride.set(exchange -> {
            entered.countDown(); awaitGate(release);
            TestIntegrationSupport.respond(exchange, 200, json.writeValueAsString(Map.of(
                    "reservationProcessId", centralId, "reservationId", 8251,
                    "status", "CANCELLED", "cancelledAt", clock.instant().toString())));
        });
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
        var recovery = scheduler.schedule(recover::recoverDue, Instant.now());
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            scheduler.schedule(phones::dispatch, Instant.now()).get(3, TimeUnit.SECONDS);
            assertThat(publisher.count).hasValue(1);
            assertThat(repository.findPhone(phone).orElseThrow().publishedAt()).isNotNull();
        } finally {
            release.countDown(); recovery.get(5, TimeUnit.SECONDS);
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
    void v7AndV11PreserveProcessesOutboxAndEveryHistoricalEventId() throws Exception {
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
        old.update("INSERT INTO reservation_process (" + columns + ") SELECT " + columns.replace("abandonment_pending", "FALSE").replace("cancellation_kind", "IF(cancellation_status IS NULL,NULL,'CANCEL')").replace("cancellation_requested_at", "IF(cancellation_status IS NULL,NULL,updated_at)").replace("cancellation_retry_version", "0").replace("cancellation_attempts", "0").replace("cancellation_next_attempt_at", "IF(cancellation_status IS NULL,NULL,updated_at)").replace("cancellation_last_failure", "NULL")
                + " FROM reservation_recovery.reservation_process WHERE id=?", process.id().toString());
        old.update("INSERT INTO reservation_inbox (topic,partition_number,record_offset,event_id,fingerprint,payload,outcome,received_at) "
                + "SELECT 'migration',0,0,event_id,REPEAT('0',64),'{}','APPLIED',CURRENT_TIMESTAMP(6) "
                + "FROM reservation_recovery.reservation_processed_event");
        for (String outcome : List.of("IGNORED", "REJECTED", "DEFERRED")) {
            old.update("INSERT INTO reservation_inbox (topic,partition_number,record_offset,event_id,fingerprint,payload,outcome,received_at) "
                    + "VALUES(?,0,0,?,REPEAT('0',64),'{}',?,CURRENT_TIMESTAMP(6))", outcome, outcome, outcome);
        }
        old.update("INSERT INTO reservation_inbox (topic,partition_number,record_offset,event_id,fingerprint,payload,outcome,received_at) "
                + "VALUES('invalid',0,0,NULL,REPEAT('0',64),'{}','REJECTED',CURRENT_TIMESTAMP(6))");
        final String phoneColumns = "id,sequence_id,process_id,group_id,central_process_id,hold_id,request_event_id,"
                + "phone_number,occurred_at,expires_at,published_at,closed_at,cycle_attempt,next_attempt_at,last_failure,version";
        old.update("INSERT INTO reservation_phone_submission (" + phoneColumns + ") SELECT " + phoneColumns
                + " FROM reservation_recovery.reservation_phone_submission");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        assertThat(old.queryForMap("SELECT phase,recovery_next_attempt_at FROM reservation_process"))
                .containsEntry("phase", "PHONE_QUEUED")
                .containsEntry("recovery_next_attempt_at", null);
        assertThat(old.queryForObject("SELECT COUNT(*) FROM user_identifier", Integer.class))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM user_identifier", Integer.class));
        assertThat(old.queryForObject("SELECT id FROM reservation_phone_submission", String.class)).isEqualTo(phoneId.toString());
        assertThat(old.queryForList("SELECT event_id FROM reservation_processed_event", String.class))
                .containsExactlyInAnyOrder(request(process).correlation().eventId(), "IGNORED", "REJECTED", "DEFERRED");
        assertThat(old.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema='recovery_previous' AND table_name='reservation_inbox'", Integer.class)).isZero();
        assertThat(old.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema='recovery_previous' AND table_name='reservation_processed_event'", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ReservationPhase.class,
            names = {"CONFIRMATION_PENDING", "CONFIRMED"})
    void v10RetiresOldAbandonIntentWithoutChangingEvidenceOrNormalCancellation(ReservationPhase phase) throws Exception {
        var initial = candidate(true).beginConfirmation();
        var pending = save(phase == ReservationPhase.CONFIRMED
                ? initial.reconcile(new CentralAppointment(initial.hold().centralProcessId(),
                        initial.patient().externalPatientId(), initial.selection(), 8251,
                        CentralAppointmentState.CONFIRMED, null), NOW) : initial);
        var base = candidate(true).beginConfirmation();
        var confirmed = save(base.reconcile(new CentralAppointment(base.hold().centralProcessId(),
                base.patient().externalPatientId(), base.selection(), 8251,
                CentralAppointmentState.CONFIRMED, null), NOW).requestCancellation());
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS stage1_previous");
            statement.execute("CREATE DATABASE stage1_previous");
        }
        String url = MYSQL.getJdbcUrl().replace("/reservation_recovery", "/stage1_previous");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("9").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, "root", MYSQL.getPassword()));
        old.update("INSERT INTO app_user SELECT * FROM reservation_recovery.app_user");
        String columns = "id,owner_id,professional_id,appointment_date,start_time,patient_first_name,patient_last_name,"
                + "phase,hold_id,central_process_id,expires_at,request_event_id,reservation_id,reason_code,version,write_id,"
                + "closed_at,created_at,updated_at,professional_first_name,professional_last_name,cancellation_status,"
                + "cancellation_requested_at,cancellation_retry_version,cancellation_attempts,cancellation_next_attempt_at,"
                + "cancellation_last_failure,recovery_next_attempt_at";
        old.update("INSERT INTO reservation_process (" + columns + ") SELECT " + columns.replace("cancellation_requested_at", "IF(cancellation_status IS NULL,NULL,updated_at)").replace("cancellation_retry_version", "0").replace("cancellation_attempts", "0").replace("cancellation_next_attempt_at", "IF(cancellation_status IS NULL,NULL,updated_at)").replace("cancellation_last_failure", "NULL")
                + " FROM reservation_recovery.reservation_process WHERE id IN (?,?)",
                pending.id().toString(), confirmed.id().toString());
        old.update("UPDATE reservation_process SET abandonment_pending=TRUE, cancellation_kind='ABANDON',"
                + " cancellation_status='PENDING', cancellation_requested_at=?, cancellation_next_attempt_at=? WHERE id=?",
                java.time.LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC),
                java.time.LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC), pending.id().toString());
        old.update("UPDATE reservation_process SET cancellation_kind='CANCEL' WHERE id=?", confirmed.id().toString());
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        var row = old.queryForMap("SELECT * FROM reservation_process WHERE id=?", pending.id().toString());
        assertThat(row).containsEntry("phase", phase.name())
                .containsEntry("hold_id", pending.hold().holdId())
                .containsEntry("central_process_id", pending.hold().centralProcessId())
                .containsEntry("cancellation_status", null)
                .containsEntry("active_owner_id", phase == ReservationPhase.CONFIRMED ? null : owner);
        assertThat(row).doesNotContainKeys("abandonment_pending", "cancellation_kind");
        java.time.LocalDateTime expiry = old.queryForObject("SELECT expires_at FROM reservation_process WHERE id=?",
                (result, rowNumber) -> result.getObject(1, java.time.LocalDateTime.class), pending.id().toString());
        assertThat(expiry).isEqualTo(java.time.LocalDateTime.ofInstant(pending.hold().expiresAt(), java.time.ZoneOffset.UTC));
        assertThat(old.queryForMap("SELECT phase,cancellation_status FROM reservation_process WHERE id=?",
                confirmed.id().toString())).containsEntry("phase", "CONFIRMED")
                .containsEntry("cancellation_status", "PENDING");
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
    @Test
    void migrationConsolidatesDurablePhasesWithoutLosingIdsOrDeadlines() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS recovery_phases");
            statement.execute("CREATE DATABASE recovery_phases");
        }
        String url = MYSQL.getJdbcUrl().replace("/reservation_recovery", "/recovery_phases");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("7").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, "root", MYSQL.getPassword()));
        String[][] phases = {{"CREATING_HOLD", "HOLD_PENDING"}, {"HOLD_UNCERTAIN", "HOLD_PENDING"},
                {"CONFIRMING", "CONFIRMATION_PENDING"}, {"CONFIRMATION_UNCERTAIN", "CONFIRMATION_PENDING"},
                {"REJECTED", "FAILED"}, {"HOLD_ATTEMPT_ABANDONED", "FAILED"}};
        for (int i = 0; i < phases.length; i++) {
            long user = i + 1L;
            old.update("INSERT INTO app_user (id,login,email,first_name,last_name,lang_key,password_hash,activated,created_at,last_modified_at)"
                    + " VALUES (?,?,?,'Test','Patient','es','fictional',true,?,?)", user, "migration" + i,
                    "migration" + i + "@example.test", java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
            boolean known = i == 2 || i == 3;
            old.update("INSERT INTO reservation_process (id,owner_id,professional_id,appointment_date,start_time,patient_first_name,"
                    + "patient_last_name,phase,hold_id,central_process_id,expires_at,reason_code,write_id,closed_at,created_at,updated_at,version)"
                    + " VALUES (?,?,15,'2026-11-04','10:00','Test','Patient',?,?,?,?,?,?,?,?,?,0)", "migration-" + i, user,
                    phases[i][0], known ? "hold-" + i : null, known ? "central-" + i : null,
                    known ? java.sql.Timestamp.from(NOW.plusSeconds(300)) : null, "PRESERVED_REASON", UUID.randomUUID().toString(),
                    i >= 4 ? java.sql.Timestamp.from(NOW) : null, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        }
        var before = old.queryForList("SELECT id,owner_id,hold_id,central_process_id,expires_at,reason_code,write_id,closed_at FROM reservation_process ORDER BY id");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).load().migrate();
        assertThat(old.queryForList("SELECT id,owner_id,hold_id,central_process_id,expires_at,reason_code,write_id,closed_at FROM reservation_process ORDER BY id"))
                .isEqualTo(before);
        for (int i = 0; i < phases.length; i++)
            assertThat(old.queryForObject("SELECT phase FROM reservation_process WHERE id=?", String.class, "migration-" + i))
                    .isEqualTo(phases[i][1]);
        assertThat(old.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='recovery_phases' AND column_name='recovery_attempts'", Integer.class))
                .isZero();
    }

}

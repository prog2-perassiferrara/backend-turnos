package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;

import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.*;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import com.prog2.appointment.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * API/SQL reales aislados; los proveedores son ficticios y Kafka no es necesario para consultar.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OwnReservationsIntegrationTests.Settings.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(60)
class OwnReservationsIntegrationTests {
    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("own_history")
                    .withUsername("history_test")
                    .withPassword("fictional-database");

    @Container
    static final MySQLContainer MIGRATION =
            new MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("migration_test")
                    .withUsername("migration_test")
                    .withPassword("fictional-migration");

    static final ReservationTestSupport PROVIDERS = new ReservationTestSupport(() -> "127.0.0.1:1");
    private final int port;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final ReservationProcessRepository repository;
    private final ReadBarrier barrier;
    private final HttpClient http = HttpClient.newHttpClient();
    private String token;
    private long owner;

    OwnReservationsIntegrationTests(
            @LocalServerPort int port,
            JsonMapper json,
            JdbcTemplate jdbc,
            ReservationProcessRepository repository,
            ReadBarrier barrier) {
        this.port = port;
        this.json = json;
        this.jdbc = jdbc;
        this.repository = repository;
        this.barrier = barrier;
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
        registry.add("RESERVATION_PREPARATION_COOLDOWN", () -> "1h");
        registry.add("RESERVATION_ATTEMPTS", () -> "1");
    }

    @BeforeEach
    void setup() throws Exception {
        PROVIDERS.reset();
        emptyCentral();
        var login = "history_" + UUID.randomUUID().toString().substring(0, 12);
        String password = "fictional-history-password";
        assertThat(
                        post(
                                        "/api/register",
                                        Map.of(
                                                "login",
                                                login,
                                                "password",
                                                password,
                                                "firstName",
                                                "Paciente",
                                                "lastName",
                                                "Ficticio",
                                                "email",
                                                login + "@example.test"))
                                .statusCode())
                .isEqualTo(201);
        var result = post("/api/authenticate", Map.of("username", login, "password", password));
        assertThat(result.statusCode()).isEqualTo(200);
        token = json.readTree(result.body()).path("id_token").asString();
        owner = jdbc.queryForObject("SELECT id FROM app_user WHERE login=?", Long.class, login);
    }

    private void emptyCentral() {
        PROVIDERS.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "0");
            TestIntegrationSupport.respond(exchange, 200, "[]");
        });
    }

    @AfterEach
    void cleanup() {
        barrier.beforeHistory.set(() -> {});
        http.close();
    }

    @AfterAll
    static void closeProviders() {
        PROVIDERS.close();
    }

    private HttpResponse<String> post(String path, Map<String, String> body)
            throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String query, String bearer)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:" + port + "/api/reservations" + query))
                        .timeout(Duration.ofSeconds(20))
                        .GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ReservationProcess seed(LocalDate date, boolean pending, boolean central) {
        var process =
                ReservationProcess.begin(
                        new StartReservationRequest(
                                UUID.randomUUID(),
                                new ReservationSelection(15, date, LocalTime.of(9, 0))),
                        new ReservationPatient(owner, "Paciente", "Ficticio"));
        if (central)
            process =
                    process.acceptHold(
                                    new HoldDetails(
                                            "hold-" + process.id(),
                                            "central-" + process.id(),
                                            ReservationProcessTests.NOW.plusSeconds(300)))
                            .beginConfirmation();
        if (!pending) {
            process =
                    central
                            ? process.reconcile(
                                    ListOwnReservationsTests.evidence(
                                            process, CentralAppointmentState.CONFIRMED, null),
                                    ReservationProcessTests.NOW)
                            : process.recordFailure(
                                    new ReservationGatewayException(
                                            ReservationFailure.SLOT_NOT_AVAILABLE, false, true),
                                    ReservationProcessTests.NOW);
        }
        assertThat(repository.begin(process).created()).isTrue();
        return process;
    }

    private void central(ReservationProcess process, String state, String first, String last) {
        PROVIDERS.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "1");
            var row = new HashMap<String, Object>();
            row.put("reservationId", 8251);
            row.put("reservationProcessId", process.hold().centralProcessId());
            row.put("externalPatientId", process.patient().externalPatientId());
            row.put("professionalId", process.selection().professionalId());
            row.put("appointmentDate", process.selection().date().toString());
            row.put("startTime", "09:00");
            row.put("status", state);
            row.put("professionalFirstName", first);
            row.put("professionalLastName", last);
            TestIntegrationSupport.respond(exchange, 200, json.writeValueAsString(List.of(row)));
        });
    }

    @Test
    void historyIsPagedLocallyWithStableOrderAndThePendingAlwaysSeparate() throws Exception {
        for (int i = 0; i < 25; i++)
            seed(LocalDate.of(2026, 11, 2).plusDays((long) i - 12), false, false);
        var pending = seed(LocalDate.of(2026, 11, 2), true, false);
        var response = get("", token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var body = json.readTree(response.body());
        assertThat(body.path("history").path("content").size()).isEqualTo(20);
        assertThat(body.path("history").path("totalElements").asLong()).isEqualTo(25);
        assertThat(body.path("history").path("totalPages").asLong()).isEqualTo(2);
        assertThat(body.path("history").path("content").get(0).path("date").asString())
                .isEqualTo("2026-11-14");
        assertThat(body.path("pending").path("processId").asString())
                .isEqualTo(pending.id().toString());
        var second = json.readTree(get("?page=1", token).body());
        assertThat(second.path("history").path("content").size()).isEqualTo(5);
        assertThat(second.path("history").path("content").get(4).path("date").asString())
                .isEqualTo("2026-10-21");
        var outside = json.readTree(get("?page=99&size=100", token).body());
        assertThat(outside.path("history").path("content").isEmpty()).isTrue();
        assertThat(outside.path("history").path("totalElements").asLong()).isEqualTo(25);
        assertThat(outside.path("pending").path("processId").asString())
                .isEqualTo(pending.id().toString());
    }

    @Test
    void namesArePersistedWithoutCatalogAndSurviveLaterProviderFailures() throws Exception {
        var process = seed(LocalDate.of(2026, 11, 2), false, true);
        central(process, "CONFIRMED", "Ana", "Gomez");
        var body = json.readTree(get("", token).body());
        assertThat(
                        body.path("history")
                                .path("content")
                                .get(0)
                                .path("professionalFirstName")
                                .asString())
                .isEqualTo("Ana");
        assertThat(repository.find(process.id()).orElseThrow().professionalName())
                .isEqualTo(new ProfessionalName("Ana", "Gomez"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT professional_last_name FROM reservation_process WHERE id=?",
                                String.class,
                                process.id().toString()))
                .isEqualTo("Gomez");
        PROVIDERS.appointmentsOverride.set(
                exchange -> TestIntegrationSupport.respond(exchange, 503, "{}"));
        var fallback = json.readTree(get("", token).body());
        assertThat(
                        fallback.path("history")
                                .path("content")
                                .get(0)
                                .path("professionalFirstName")
                                .asString())
                .isEqualTo("Ana");
        assertThat(fallback.path("warningCode").asString())
                .isEqualTo("APPOINTMENT_STATUS_UNAVAILABLE");
        assertThat(fallback.path("verifiedAt").isNull()).isTrue();
        assertThat(fallback.path("history").path("content").get(0).has("endTime")).isFalse();
        assertThat(PROVIDERS.creates).hasValue(0);
        assertThat(PROVIDERS.confirmations).hasValue(0);
        assertThat(PROVIDERS.occupancyQueries).hasValue(0);
    }

    @Test
    void localOwnershipAndTotalsRemainIndependentOfCentralFiltersAndReservations()
            throws Exception {
        var own = seed(LocalDate.of(2026, 11, 2), false, true);
        central(own, "CONFIRMED", "Ana", "Gomez");
        assertThat(get("", null).statusCode()).isEqualTo(401);
        var anotherLogin = "second_" + UUID.randomUUID().toString().substring(0, 12);
        var anotherPassword = "fictional-other-password";
        assertThat(
                        post(
                                        "/api/register",
                                        Map.of(
                                                "login",
                                                anotherLogin,
                                                "password",
                                                anotherPassword,
                                                "firstName",
                                                "Otro",
                                                "lastName",
                                                "Usuario",
                                                "email",
                                                anotherLogin + "@example.test"))
                                .statusCode())
                .isEqualTo(201);
        var anotherToken =
                json.readTree(
                                post(
                                                "/api/authenticate",
                                                Map.of(
                                                        "username",
                                                        anotherLogin,
                                                        "password",
                                                        anotherPassword))
                                        .body())
                        .path("id_token")
                        .asString();
        var other =
                json.readTree(
                        get("?ownerId=" + owner + "&externalPatientId=user-" + owner, anotherToken)
                                .body());
        assertThat(other.path("history").path("content").isEmpty()).isTrue();
        assertThat(other.path("history").path("totalElements").asLong()).isZero();
        assertThat(other.path("pending").isNull()).isTrue();
        assertThat(other.toString())
                .doesNotContain(own.id().toString(), "Ana", "Gomez", own.hold().centralProcessId());
        jdbc.update("DELETE FROM reservation_process WHERE id=?", own.id().toString());
        var orphan = json.readTree(get("", token).body());
        assertThat(orphan.path("history").path("content").isEmpty()).isTrue();
        assertThat(orphan.path("history").path("totalElements").asLong()).isZero();
        assertThat(repository.find(own.id())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "?page=-1",
                "?size=0",
                "?size=101",
                "?page=x",
                "?size=x",
                "?page=2147483647&size=100",
                "?page=9999999999999"
            })
    void invalidPagesAreRejectedBeforeCentralAccess(String query) throws Exception {
        var result = get(query, token);
        assertThat(result.statusCode()).isEqualTo(400);
        assertThat(json.readTree(result.body()).path("code").asString())
                .isEqualTo("INVALID_RESERVATION_PAGE");
        assertThat(PROVIDERS.appointmentQueries).hasValue(0);
    }

    @Test
    void anEmptyFallbackStillWarnsAndMissingLocalStorageReturns503() throws Exception {
        PROVIDERS.appointmentsOverride.set(
                exchange -> TestIntegrationSupport.respond(exchange, 503, "{}"));
        var fallback = get("", token);
        assertThat(fallback.statusCode()).isEqualTo(200);
        assertThat(json.readTree(fallback.body()).path("warningCode").asString())
                .isEqualTo("APPOINTMENT_STATUS_UNAVAILABLE");
        jdbc.execute("RENAME TABLE reservation_process TO unavailable_reservation_process");
        try {
            var failed = get("", token);
            assertThat(failed.statusCode()).isEqualTo(503);
            assertThat(json.readTree(failed.body()).path("code").asString())
                    .isEqualTo("STORAGE_UNAVAILABLE");
        } finally {
            jdbc.execute("RENAME TABLE unavailable_reservation_process TO reservation_process");
        }
    }

    @Test
    void sqlSnapshotKeepsPendingAndHistoryConsistentAcrossAConcurrentCommit() throws Exception {
        seed(LocalDate.of(2026, 11, 1), false, false);
        var pending = seed(LocalDate.of(2026, 11, 2), true, true);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        barrier.beforeHistory.set(() -> {
            entered.countDown();
            try {
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                var reading = executor.submit(() -> repository.readOwn(owner, 0, 20));
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                repository.reconcile(
                        pending.id(),
                        ListOwnReservationsTests.evidence(
                                pending, CentralAppointmentState.CONFIRMED, null),
                        ReservationProcessTests.NOW);
                release.countDown();
                var snapshot = reading.get(10, TimeUnit.SECONDS);
                assertThat(snapshot.pending().id()).isEqualTo(pending.id());
                assertThat(snapshot.history()).hasSize(1);
                assertThat(snapshot.totalElements()).isEqualTo(1);
            } finally {
                release.countDown();
                barrier.beforeHistory.set(() -> {});
            }
        }
        var current = repository.readOwn(owner, 0, 20);
        assertThat(current.pending()).isNull();
        assertThat(current.history()).hasSize(2);
        assertThat(current.totalElements()).isEqualTo(2);
    }

    @Test
    void v5AddsNullableNamesToPopulatedV4WithoutLosingProgress() throws Exception {
        var database = MIGRATION;
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("4")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO"
                        + " app_user(id,login,email,first_name,last_name,lang_key,password_hash,activated,created_at,last_modified_at)"
                        + " VALUES(42,'fixture','fixture@example.test','Paciente','Ficticio','es','unused',true,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");
            statement.executeUpdate(
                    "INSERT INTO"
                        + " reservation_process(id,owner_id,professional_id,appointment_date,start_time,patient_first_name,patient_last_name,phase,version,write_id,created_at,updated_at)"
                        + " VALUES('00000000-0000-0000-0000-000000000042',42,15,'2026-11-02','09:00','Paciente','Ficticio','CREATING_HOLD',0,'00000000-0000-0000-0000-000000000043',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");
            Flyway.configure()
                    .dataSource(
                            database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .load()
                    .migrate();
            try (var result =
                    statement.executeQuery(
                            "SELECT"
                                + " phase,professional_first_name,professional_last_name,active_owner_id"
                                + " FROM reservation_process")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("CREATING_HOLD");
                assertThat(result.getString(2)).isNull();
                assertThat(result.getString(3)).isNull();
                assertThat(result.getLong(4)).isEqualTo(42);
                assertThat(result.next()).isFalse();
            }
        }
    }

    @Test
    void aValidUserJwtWithoutTheRequiredRoleCannotReadHistory() throws Exception {
        var original = com.nimbusds.jwt.SignedJWT.parse(token);
        var limited =
                new com.nimbusds.jwt.SignedJWT(
                        original.getHeader(),
                        new com.nimbusds.jwt.JWTClaimsSet.Builder(original.getJWTClaimsSet())
                                .claim("auth", "ROLE_OTHER")
                                .build());
        limited.sign(
                new com.nimbusds.jose.crypto.RSASSASigner(
                        TestIntegrationSupport.KEYS.getPrivate()));
        assertThat(get("", limited.serialize()).statusCode()).isEqualTo(403);
        assertThat(PROVIDERS.appointmentQueries).hasValue(0);
    }

    @Test
    void aCancellationCommittedWhileRestIsInFlightIsNotReopened() throws Exception {
        var process = seed(LocalDate.of(2026, 11, 2), false, true);
        central(process, "CONFIRMED", "Ana", "Gomez");
        var response = PROVIDERS.appointmentsOverride.get();
        PROVIDERS.appointmentsOverride.set(exchange -> {
            repository.reconcile(
                    process.id(),
                    ListOwnReservationsTests.evidence(
                            process, CentralAppointmentState.CANCELLED, null),
                    ReservationProcessTests.NOW);
            response.handle(exchange);
        });
        var body = json.readTree(get("", token).body());
        assertThat(body.path("pending").isNull()).isTrue();
        assertThat(body.path("history").path("content").get(0).path("state").asString())
                .isEqualTo("CANCELLED");
        assertThat(body.path("verificationStatus").asString()).isEqualTo("INCONCLUSIVE");
        assertThat(repository.find(process.id()).orElseThrow().phase())
                .isEqualTo(ReservationPhase.CANCELLED);
    }

    @Test
    void equalDatesAndTimesUseLexicalUuidOrderAcrossPageBoundaries() throws Exception {
        String first = "00000000-0000-0000-0000-000000000001";
        String second = "80000000-0000-0000-0000-000000000001";
        for (String id : List.of(second, first)) {
            var process =
                    ReservationProcess.begin(
                                    new StartReservationRequest(
                                            UUID.fromString(id),
                                            new ReservationSelection(
                                                    15,
                                                    LocalDate.of(2026, 11, 2),
                                                    LocalTime.of(9, 0))),
                                    new ReservationPatient(owner, "Paciente", "Ficticio"))
                            .recordFailure(
                                    new ReservationGatewayException(
                                            ReservationFailure.SLOT_NOT_AVAILABLE, false, true),
                                    ReservationProcessTests.NOW);
            assertThat(repository.begin(process).created()).isTrue();
        }
        var page0 = json.readTree(get("?size=1", token).body());
        var page1 = json.readTree(get("?page=1&size=1", token).body());
        assertThat(page0.path("history").path("content").get(0).path("processId").asString())
                .isEqualTo(first);
        assertThat(page1.path("history").path("content").get(0).path("processId").asString())
                .isEqualTo(second);
        assertThat(page0.path("history").path("totalElements").asLong()).isEqualTo(2);
    }

    static class ReadBarrier {
        final AtomicReference<Runnable> beforeHistory = new AtomicReference<>(() -> {});

        JpaReservationProcessRepository wrap(JpaReservationProcessRepository delegate) {
            return (JpaReservationProcessRepository)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {JpaReservationProcessRepository.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("history")) beforeHistory.get().run();
                                try {
                                    return method.invoke(delegate, args);
                                } catch (InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
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
        ReadBarrier readBarrier() {
            return new ReadBarrier();
        }

        @Bean
        @Primary
        JpaReservationProcessRepository observedRepository(
                @Qualifier("jpaReservationProcessRepository") JpaReservationProcessRepository real,
                ReadBarrier barrier) {
            return barrier.wrap(real);
        }
    }
}

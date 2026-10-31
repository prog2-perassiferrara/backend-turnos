package com.prog2.appointment.availability;

import java.io.IOException;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Recorrido HTTP/SQL completo: usuario real, filtros y clientes productivos, datos e
 * infraestructura descartables.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AvailabilityHttpTests.TimeConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
@Timeout(30)
class AvailabilityHttpTests {
    private static final AvailabilityTestSupport PROVIDERS = new AvailabilityTestSupport();
    private static final String PATH = "/api/appointment-availability";
    private static final String QUERY = "?professionalId=15&date=2026-11-02";
    private static final String PASSWORD = "fictional-availability-password";

    @Container
    static final org.testcontainers.mysql.MySQLContainer MYSQL =
            new org.testcontainers.mysql.MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("availability_test").withUsername("availability_test")
                    .withPassword("fictional-availability-database");

    private final int port;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final TechnicalAccessUseCase access;
    private final HttpClient http = HttpClient.newHttpClient();
    private String token;

    /** TestConstructor configura la entrega al constructor, sin Autowired ni inyección por campos. */
    AvailabilityHttpTests(@LocalServerPort int port, JsonMapper json, JdbcTemplate jdbc, TechnicalAccessUseCase access) {
        this.port = port;
        this.json = json;
        this.jdbc = jdbc;
        this.access = access;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        PROVIDERS.settings().forEach((name, value) -> registry.add(name, () -> value));
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
    }

    @BeforeEach
    void prepareUserAndQueries() throws IOException, InterruptedException {
        PROVIDERS.resetQueries();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(access.getAccess()).isNotNull());
        String login =
                "availability_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String profile = json.writeValueAsString(Map.of("login", login, "password", PASSWORD,
                "firstName", "Paciente", "lastName", "Ficticio", "email", login + "@example.test"));
        var registration = post("/api/register", profile);
        assertThat(registration.statusCode()).isEqualTo(201);
        var authentication = post("/api/authenticate", json.writeValueAsString(
                Map.of("username", login, "password", PASSWORD, "rememberMe", false)));
        assertThat(authentication.statusCode()).isEqualTo(200);
        token = json.readTree(authentication.body()).path("id_token").asString();
    }

    @AfterAll
    static void closeProviders() {
        PROVIDERS.close();
    }

    @AfterEach
    void closeOwnedHttpClient() {
        http.close();
    }

    @Test
    void returnsOnlyFreeStartTimesWithTheAttentionZoneAndNoSensitiveData(CapturedOutput output)
            throws IOException, InterruptedException {
        String entries =
                AvailabilityTestSupport.position(
                                AvailabilityTestSupport.DATE, "09:00", "CONFIRMED", null)
                        + ","
                        + AvailabilityTestSupport.position(
                                AvailabilityTestSupport.DATE,
                                "09:30",
                                "HELD",
                                AvailabilityTestSupport.NOW.plusSeconds(60).toString());
        PROVIDERS.occupancyBody.set(AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE, entries));
        long accountsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        var response = get(QUERY, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()))
                .isEqualTo(
                        json.readTree(
                                """
                                {"professionalId":15,"date":"2026-11-02","timeZone":"America/Argentina/Buenos_Aires",
                                 "availableStartTimes":["10:00:00","10:30:00"]}
                                """));
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(PROVIDERS.agendaCalls).hasValue(1);
        assertThat(PROVIDERS.occupancyCalls).hasValue(1);
        assertThat(PROVIDERS.invalidCredentials).hasValue(0);
        assertThat(PROVIDERS.unexpectedCalls).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class))
                .isEqualTo(accountsBefore);
        assertThat(output).doesNotContain(token, PASSWORD, PROVIDERS.centralToken);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "?date=2026-11-02",
                "?professionalId=15",
                "?professionalId=0&date=2026-11-02",
                "?professionalId=-1&date=2026-11-02",
                "?professionalId=letters&date=2026-11-02",
                "?professionalId=9223372036854775808&date=2026-11-02",
                "?professionalId=15&date=invalid",
                "?professionalId=15&date=2026-02-30",
                "?professionalId=15&date=2026-11-01"
            })
    void rejectsInvalidInputBeforeProviders(String query) throws IOException, InterruptedException {
        problem(get(query, token), 400, "INVALID_AVAILABILITY_QUERY");
        assertThat(PROVIDERS.agendaCalls).hasValue(0);
        assertThat(PROVIDERS.occupancyCalls).hasValue(0);
    }

    @Test
    void securityRejectsAnonymousMalformedAndInsufficientCredentialsBeforeProviders()
            throws IOException, InterruptedException, JOSEException {
        problem(get(QUERY, null), 401, "AUTHENTICATION_REQUIRED");
        problem(get(QUERY, "fictional-invalid-token"), 401, "AUTHENTICATION_REQUIRED");
        problem(get(QUERY, signedToken("ROLE_OTHER", "user")), 403, "ACCESS_DENIED");
        problem(get(QUERY, signedToken("ROLE_USER", "service")), 401, "AUTHENTICATION_REQUIRED");
        assertThat(PROVIDERS.agendaCalls).hasValue(0);
        assertThat(PROVIDERS.occupancyCalls).hasValue(0);
    }

    @Test
    void reportsMissingAndDisabledProfessionalSeparatelyFromUnavailability() throws IOException, InterruptedException {
        PROVIDERS.agendaStatus.set(404);
        PROVIDERS.agendaBody.set("{\"code\":\"PROFESSIONAL_NOT_FOUND\"}");
        problem(get(QUERY, token), 404, "PROFESSIONAL_NOT_FOUND");
        PROVIDERS.agendaStatus.set(200);
        PROVIDERS.agendaBody.set(AvailabilityTestSupport.agenda(15, false));
        problem(get(QUERY, token), 400, "PROFESSIONAL_DISABLED");
        assertThat(PROVIDERS.occupancyCalls).hasValue(0);
    }

    @Test
    void noApplicableAgendaIsAValidEmptyResultEvenIfOccupanciesAreUnavailable()
            throws IOException, InterruptedException {
        PROVIDERS.occupancyStatus.set(503);
        // El martes no coincide con la única agenda semanal del proveedor ficticio.
        var response = get("?professionalId=15&date=2026-11-03", token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("availableStartTimes").size()).isZero();
        assertThat(PROVIDERS.occupancyCalls).hasValue(0);
    }

    @Test
    void distinguishesEmptyOccupanciesFromUnavailableOrUninterpretableData() throws IOException, InterruptedException {
        var free = get(QUERY, token);
        assertThat(free.statusCode()).isEqualTo(200);
        assertThat(json.readTree(free.body()).path("availableStartTimes").size()).isEqualTo(4);
        PROVIDERS.occupancyStatus.set(503);
        PROVIDERS.occupancyBody.set("{\"detail\":\"fictional-private-response\"}");
        problem(get(QUERY, token), 503, "OCCUPANCIES_UNAVAILABLE");
        PROVIDERS.occupancyStatus.set(200);
        PROVIDERS.occupancyBody.set("{}");
        problem(get(QUERY, token), 503, "OCCUPANCIES_UNAVAILABLE");
        PROVIDERS.agendaStatus.set(503);
        PROVIDERS.agendaBody.set("{\"code\":\"CATALOG_NOT_CURRENT\"}");
        problem(get(QUERY, token), 503, "CATALOG_NOT_CURRENT");
    }

    @Test
    void doesNotPresentAProviderAuthenticationFailureAsTheUsers401() throws IOException, InterruptedException {
        PROVIDERS.agendaStatus.set(401);
        PROVIDERS.agendaBody.set("{}");
        problem(get(QUERY, token), 503, "CATALOG_UNAVAILABLE");
        assertThat(PROVIDERS.occupancyCalls).hasValue(0);
    }

    @Test
    void canRecoverARejectedTechnicalTokenWithoutReplayingTheFailedQuery() throws IOException, InterruptedException {
        int tokenCalls = PROVIDERS.tokenCalls.get();
        PROVIDERS.occupancyStatus.set(401);
        problem(get(QUERY, token), 503, "TECHNICAL_INTEGRATION_UNAVAILABLE");
        assertThat(PROVIDERS.occupancyCalls).hasValue(1);
        assertThat(PROVIDERS.tokenCalls).hasValue(tokenCalls);
        PROVIDERS.occupancyStatus.set(200);
        PROVIDERS.rotateCentralToken();
        // La siguiente demanda dispara el ciclo de #4; no espera ni repite ocupaciones automáticamente.
        problem(get(QUERY, token), 503, "TECHNICAL_INTEGRATION_UNAVAILABLE");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(access.getAccess()).isNotNull());
        assertThat(PROVIDERS.occupancyCalls).hasValue(1);
        assertThat(PROVIDERS.tokenCalls).hasValue(tokenCalls + 1);
        assertThat(get(QUERY, token).statusCode()).isEqualTo(200);
        assertThat(PROVIDERS.occupancyCalls).hasValue(2);
    }

    @Test
    void concurrentQueriesKeepTheirProfessionalAndDateAndOnlySendReads() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        PROVIDERS.occupancyHandler.set(
                exchange -> {
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IOException("Fictional concurrency barrier timed out");
                        }
                        var query = AvailabilityTestSupport.query(exchange);
                        TestIntegrationSupport.respond(
                                exchange,
                                200,
                                AvailabilityTestSupport.occupancies(
                                        Long.parseLong(query.get("professionalId")),
                                        java.time.LocalDate.parse(query.get("from")),
                                        ""));
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        exchange.close();
                    }
                });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> get(QUERY, token));
            var second = executor.submit(() -> get("?professionalId=16&date=2026-11-09", token));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            var firstResponse = first.get(10, TimeUnit.SECONDS);
            var secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse.statusCode()).isEqualTo(200);
            assertThat(secondResponse.statusCode()).isEqualTo(200);
            JsonNode firstBody = json.readTree(firstResponse.body());
            JsonNode secondBody = json.readTree(secondResponse.body());
            assertThat(firstBody.path("professionalId").asLong()).isEqualTo(15);
            assertThat(firstBody.path("date").asString()).isEqualTo("2026-11-02");
            assertThat(secondBody.path("professionalId").asLong()).isEqualTo(16);
            assertThat(secondBody.path("date").asString()).isEqualTo("2026-11-09");
        }
        assertThat(PROVIDERS.agendaCalls).hasValue(2);
        assertThat(PROVIDERS.occupancyCalls).hasValue(2);
        assertThat(PROVIDERS.unexpectedCalls).hasValue(0);
    }

    private HttpResponse<String> get(String query, String bearer) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH + query))
                .timeout(Duration.ofSeconds(20));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        var body = json.readTree(response.body());
        assertThat(body.path("code").asString()).isEqualTo(code);
        if (status == 401 || status == 403) {
            // El contrato de los filtros existentes no añade instance; CU-03 lo conserva.
            assertThat(body.has("instance")).isFalse();
        } else {
            assertThat(body.path("instance").asString()).isEqualTo(PATH);
        }
        assertThat(response.headers().firstValue("Content-Type"))
                .contains("application/problem+json");
        assertThat(response.body())
                .doesNotContain("fictional-private", token, PROVIDERS.centralToken);
    }

    private static String signedToken(String authority, String use) throws JOSEException {
        var claims =
                Map.<String, Object>of(
                        "iss",
                        "backend-turnos",
                        "sub",
                        "999",
                        "aud",
                        List.of("turnos"),
                        "token_use",
                        use,
                        "auth",
                        authority,
                        "iat",
                        AvailabilityTestSupport.NOW.getEpochSecond(),
                        "exp",
                        AvailabilityTestSupport.NOW.plusSeconds(86400).getEpochSecond());
        var jwt = new JWSObject(new JWSHeader(JWSAlgorithm.RS256), new Payload(claims));
        jwt.sign(new RSASSASigner(TestIntegrationSupport.KEYS.getPrivate()));
        return jwt.serialize();
    }

    /** El mismo reloj controlado gobierna JWT, admisión y cálculo; no usa la fecha de la máquina. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        Clock availabilityTestClock() {
            return Clock.fixed(AvailabilityTestSupport.NOW, ZoneOffset.UTC);
        }
    }
}

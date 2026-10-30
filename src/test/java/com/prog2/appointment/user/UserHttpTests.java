package com.prog2.appointment.user;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jwt.SignedJWT;
import com.prog2.appointment.AppointmentApplication;
import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.user.domain.exception.UserAccountUnavailableException;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.domain.ports.out.UserAccountRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** HTTP, SQL, concurrencia y fallos de commit reales, sin .env, base de desarrollo ni servicios de cátedra. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UserFailureConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserHttpTests {
    private static final String PASSWORD = "fictional-user-password";
    private static final TestIntegrationSupport INTEGRATION = new TestIntegrationSupport();

    @Container
    static final org.testcontainers.mysql.MySQLContainer MYSQL =
            new org.testcontainers.mysql.MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("user_test").withUsername("user_test")
                    .withPassword("fictional-user-database");

    private final int port;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final UserFailureConfiguration.Faults faults;
    private final UserAccountRepository accounts;
    private final TechnicalAccessUseCase technicalAccess;

    /** TestConstructor configura DI por constructor, sin Autowired ni inyección de dependencias en campos. */
    UserHttpTests(@LocalServerPort int port, JdbcTemplate jdbc, JsonMapper json,
            UserFailureConfiguration.Faults faults, UserAccountRepository accounts,
            TechnicalAccessUseCase technicalAccess) {
        this.port = port;
        this.jdbc = jdbc;
        this.json = json;
        this.faults = faults;
        this.accounts = accounts;
        this.technicalAccess = technicalAccess;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        INTEGRATION.settings().forEach((name, value) -> registry.add(name, () -> value));
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
    }

    @BeforeEach
    void isolateAccountsAndFaults() {
        faults.reset();
        jdbc.update("DELETE FROM user_authority");
        jdbc.update("DELETE FROM user_identifier");
        jdbc.update("DELETE FROM app_user");
    }

    @AfterAll
    static void closeProviders() {
        INTEGRATION.close();
    }

    @Test
    void confirmsActiveAccountBeforeAutomaticLoginAndKeepsTechnicalHealth(CapturedOutput output) throws IOException, InterruptedException, ParseException {
        var response = register(profile("Patient", "PATIENT@example.test"));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEmpty();
        assertNoSessionAndNoCache(response);
        var row = jdbc.queryForMap("SELECT * FROM app_user");
        assertThat(row).containsEntry("login", "patient")
                .containsEntry("email", "patient@example.test")
                .containsEntry("lang_key", "es")
                .containsEntry("activated", true);
        assertThat(row.get("image_url")).isNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("last_modified_at")).isNotNull();
        String hash = row.get("password_hash").toString();
        assertThat(hash).hasSize(97).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForList("SELECT authority FROM user_authority", String.class))
                .containsExactly("ROLE_USER");
        var login = authenticate("PATIENT", PASSWORD, null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertNoSessionAndNoCache(login);
        var body = json.readTree(login.body());
        assertThat(body.properties()).hasSize(1);
        String token = body.path("id_token").asString();
        assertThat(login.headers().firstValue("Authorization")).contains("Bearer " + token);
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(row.get("id").toString());
        assertThat(claims.getStringClaim("auth")).isEqualTo("ROLE_USER");
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofHours(24));
        assertThat(get("/actuator/health").body()).isEqualTo("{\"status\":\"UP\"}");
        assertProblem(get("/api/account"), 401, "AUTHENTICATION_REQUIRED");
        // Con identidad válida se alcanza MVC: la ruta sigue sin implementarse y conserva el 404.
        assertThat(get("/api/account", token).statusCode()).isEqualTo(404);
        assertProblem(get("/error", token), 403, "ACCESS_DENIED");
        assertThat(output).doesNotContain(PASSWORD, hash, token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/register", "/api/authenticate", "/actuator/health"})
    void explicitlyInvalidBearerIsRejectedEvenOnPublicRoutes(String path) throws IOException, InterruptedException {
        String marker = "fictional-invalid-bearer";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + marker);
        if (path.equals("/actuator/health")) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
        }
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertProblem(response, 401, "AUTHENTICATION_REQUIRED");
            assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
            assertThat(response.body()).doesNotContain(marker);
        }
        assertThat(count("app_user")).isZero();
    }

    static Stream<Arguments> invalidRegistrationFields() {
        return Stream.of(
                Arguments.of("login", ""), Arguments.of("login", "x".repeat(51)),
                Arguments.of("login", "bad login"), Arguments.of("login", "user/name"),
                Arguments.of("login", "prefix patient+tag@example.test"),
                Arguments.of("login", "patient+tag@example.test suffix"),
                Arguments.of("login", "patient+tag@sub..example.test"),
                Arguments.of("login", "patient+tag@sub.example.test."),
                Arguments.of("login", "patient+tag@.example.test"),
                Arguments.of("login", "patient+tag@"), Arguments.of("login", "patient+tag"),
                Arguments.of("login", "patient\n"),
                Arguments.of("password", null), Arguments.of("password", "short"),
                Arguments.of("password", "x".repeat(101)), Arguments.of("password", "á".repeat(101)),
                Arguments.of("password", "🙂".repeat(101)), Arguments.of("password", "🙂".repeat(5)),
                Arguments.of("firstName", null), Arguments.of("firstName", "  "),
                Arguments.of("firstName", "A"),
                Arguments.of("firstName", "x".repeat(51)), Arguments.of("lastName", null),
                Arguments.of("lastName", "  "), Arguments.of("lastName", "L"),
                Arguments.of("lastName", "x".repeat(51)),
                Arguments.of("email", null), Arguments.of("email", "bad-email"),
                Arguments.of("email", "x@x"), Arguments.of("email", "x".repeat(250) + "@example.test"),
                Arguments.of("imageUrl", "x".repeat(257)),
                Arguments.of("langKey", ""), Arguments.of("langKey", "e"),
                Arguments.of("langKey", "x".repeat(11)));
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrationFields")
    void rejectsInvalidRegistrationWithoutPersistenceOrLeakingValue(String field, Object value,
            CapturedOutput output) throws IOException, InterruptedException {
        var body = profile("patient", "patient@example.test");
        body.put(field, value);
        var response = register(body);
        assertProblem(response, 400, "VALIDATION_ERROR");
        assertThat(json.readTree(response.body()).path("fieldErrors").toString()).contains(field);
        assertThat(count("app_user")).isZero();
        assertThat(response.body()).doesNotContain(PASSWORD, "rejectedValue", "passwordHash");
        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    void acceptsTwoCharacterNamesWithoutChangingThem() throws IOException, InterruptedException {
        var body = profile("patient", "patient@example.test");
        body.put("firstName", "Al");
        body.put("lastName", "Li");
        assertThat(register(body).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT first_name FROM app_user", String.class)).isEqualTo("Al");
        assertThat(jdbc.queryForObject("SELECT last_name FROM app_user", String.class)).isEqualTo("Li");
        assertThat(authenticate("patient", PASSWORD, null).statusCode()).isEqualTo(200);
    }

    @Test
    void acceptsMaximumProfileFieldsAndAssignsInternalValues() throws IOException, InterruptedException {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "."
                + "d".repeat(56) + ".test";
        assertThat(email).hasSize(254);
        var body = profile("p".repeat(50), email);
        body.put("firstName", "a".repeat(50));
        body.put("lastName", "b".repeat(50));
        body.put("imageUrl", "x".repeat(256));
        body.put("langKey", "x".repeat(10));
        body.put("id", 999999);
        body.put("activated", false);
        body.put("authorities", List.of("ROLE_ADMIN", "ROLE_CATALOG_INTEGRATION"));
        body.put("createdDate", "1970-01-01T00:00:00Z");
        body.put("lastModifiedDate", "1970-01-01T00:00:00Z");
        assertThat(register(body).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT id FROM app_user", Long.class)).isNotEqualTo(999999L);
        assertThat(jdbc.queryForObject("SELECT activated FROM app_user", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("SELECT authority FROM user_authority", String.class))
                .containsExactly("ROLE_USER");
        assertThat(jdbc.queryForObject("SELECT YEAR(created_at) FROM app_user", Integer.class)).isGreaterThan(1970);
        assertThat(jdbc.queryForObject("SELECT image_url FROM app_user", String.class)).hasSize(256);
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient.name-1_2", "patient+tag@sub.example.test",
            "a!$&*+=?^_`{|}~.-@sub.example.test", "x+y@localhost"})
    void retainsCompatibleLoginFormatsWhenRegisteringAndAuthenticating(String login)
            throws IOException, InterruptedException {
        assertThat(register(profile(login, "patient@example.test")).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT login FROM app_user", String.class)).isEqualTo(login);
        assertThat(authenticate(login, PASSWORD, null).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOGIN@example.test,free@example.test", "free,EMAIL@example.test",
            "EMAIL@example.test,free@example.test", "free,LOGIN@example.test"})
    void rejectsDuplicatesAndBothCrossIdentifierDirections(String pair) throws IOException, InterruptedException {
        assertThat(register(profile("login@example.test", "email@example.test")).statusCode()).isEqualTo(201);
        String[] fields = pair.split(",");
        var response = register(profile(fields[0], fields[1]));
        assertProblem(response, 400, "USER_IDENTIFIER_ALREADY_USED");
        assertThat(count("app_user")).isEqualTo(1);
        assertThat(count("user_identifier")).isEqualTo(2);
        assertThat(count("user_authority")).isEqualTo(1);
        assertThat(authenticate("login@example.test", PASSWORD, null).statusCode()).isEqualTo(200);
    }

    @Test
    void permitsOwnLoginAndEmailToCoincideWithoutDuplicateIdentifierRow() throws IOException, InterruptedException {
        assertThat(register(profile("SAME@example.test", "same@example.test")).statusCode()).isEqualTo(201);
        assertThat(count("user_identifier")).isEqualTo(1);
        assertThat(authenticate("SAME@example.test", PASSWORD, false).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(30)
    void concurrentRegistrationsCannotClaimSameOrCrossedIdentifier(boolean crossed)
            throws InterruptedException, java.util.concurrent.ExecutionException,
            java.util.concurrent.TimeoutException {
        var first = profile(crossed ? "shared@example.test" : "shared", "one@example.test");
        var second = profile(crossed ? "another" : "SHARED",
                crossed ? "SHARED@example.test" : "two@example.test");
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var responses = Stream.of(first, second).map(body -> executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
                return register(body).statusCode();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(responses.get(0).get(15, TimeUnit.SECONDS),
                    responses.get(1).get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 400);
        }
        assertThat(count("app_user")).isEqualTo(1);
        assertThat(count("user_identifier")).isEqualTo(2);
        assertThat(count("user_authority")).isEqualTo(1);
    }

    static Stream<String> validPasswordBoundaries() {
        return Stream.of("sixsix", "x".repeat(100), "á".repeat(100), "🙂".repeat(100),
                "🙂".repeat(6), "x".repeat(73), "  exact password  ");
    }

    @ParameterizedTest
    @MethodSource("validPasswordBoundaries")
    void registersAndAuthenticatesCompleteBoundaryPassword(String password) throws IOException, InterruptedException {
        var body = profile("patient", "patient@example.test");
        body.put("password", password);
        assertThat(register(body).statusCode()).isEqualTo(201);
        assertThat(authenticate("patient", password, false).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATIENT", "PATIENT@EXAMPLE.TEST"})
    void resolvesUsernameOrEmailWithoutChangingAccount(String identifier) throws IOException, InterruptedException {
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
        var before = jdbc.queryForMap("SELECT * FROM app_user");
        assertThat(authenticate(identifier, PASSWORD, null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForMap("SELECT * FROM app_user")).isEqualTo(before);
    }

    @Test
    void authenticatesEachAccountWithItsOwnIdentityAndPassword()
            throws IOException, InterruptedException, ParseException {
        assertThat(register(profile("first", "first@example.test")).statusCode()).isEqualTo(201);
        var second = profile("second", "second@example.test");
        second.put("password", "fictional-second-password");
        assertThat(register(second).statusCode()).isEqualTo(201);
        var firstLogin = authenticate("FIRST", PASSWORD, null);
        var secondLogin = authenticate("SECOND@example.test", "fictional-second-password", null);
        assertThat(firstLogin.statusCode()).isEqualTo(200);
        assertThat(secondLogin.statusCode()).isEqualTo(200);
        String firstId = SignedJWT.parse(json.readTree(firstLogin.body()).path("id_token").asString())
                .getJWTClaimsSet().getSubject();
        String secondId = SignedJWT.parse(json.readTree(secondLogin.body()).path("id_token").asString())
                .getJWTClaimsSet().getSubject();
        assertThat(firstId).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM app_user WHERE login = 'first'", Long.class).toString()).isNotEqualTo(secondId);
        assertThat(secondId).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM app_user WHERE login = 'second'", Long.class).toString());
        assertProblem(authenticate("second", PASSWORD, null), 401, "INVALID_CREDENTIALS");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void appliesRememberMeFromIssuance(boolean rememberMe) throws IOException, InterruptedException, ParseException {
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
        var response = authenticate("patient", PASSWORD, rememberMe);
        assertThat(response.statusCode()).isEqualTo(200);
        var claims = SignedJWT.parse(json.readTree(response.body()).path("id_token").asString()).getJWTClaimsSet();
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(rememberMe ? Duration.ofDays(30) : Duration.ofHours(24));
    }

    @Test
    void rejectsWrongUnknownAndInactiveCredentialsWithSameCode() throws IOException, InterruptedException {
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
        assertProblem(authenticate("patient", "wrong-password", null), 401, "INVALID_CREDENTIALS");
        assertProblem(authenticate("unknown", PASSWORD, null), 401, "INVALID_CREDENTIALS");
        jdbc.update("UPDATE app_user SET activated = false");
        assertProblem(authenticate("patient", PASSWORD, null), 401, "INVALID_CREDENTIALS");
    }

    static Stream<String> invalidLoginPasswords() {
        return Stream.of(null, "short", "", "x".repeat(101), "á".repeat(101),
                "🙂".repeat(101), "🙂".repeat(5));
    }

    @ParameterizedTest
    @MethodSource("invalidLoginPasswords")
    void rejectsInvalidLoginPasswordBeforeAuthentication(String password) throws IOException, InterruptedException {
        assertProblem(authenticate("unknown", password, null), 400, "VALIDATION_ERROR");
    }

    @Test
    void rejectsSuffixBeyond100CharactersInsteadOfAcceptingMatchingPrefix() throws IOException, InterruptedException {
        String password = "🙂".repeat(100);
        var body = profile("patient", "patient@example.test");
        body.put("password", password);
        assertThat(register(body).statusCode()).isEqualTo(201);
        assertProblem(authenticate("patient", password + "x", null), 400, "VALIDATION_ERROR");
        assertProblem(authenticate("patient", "🙂".repeat(99) + "é", null), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void manualRetirementRemovesOnlyLegacyAccountsAndPreservesIdentitySequence()
            throws IOException, InterruptedException, java.sql.SQLException {
        assertThat(register(profile("legacy", "legacy@example.test")).statusCode()).isEqualTo(201);
        assertThat(register(profile("current", "current@example.test")).statusCode()).isEqualTo(201);
        Long legacyId = jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'legacy'", Long.class);
        Long currentId = jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'current'", Long.class);
        String legacyHash = "$2a$10$" + "a".repeat(53);
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", legacyHash, legacyId);
        assertProblem(authenticate("legacy", PASSWORD, null), 503, "AUTHENTICATION_UNAVAILABLE");

        // Mismo procedimiento manual del README, ejecutado solo en el contenedor temporal.
        try (var connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection();
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            assertThat(statement.executeUpdate("""
                    DELETE a FROM user_authority a JOIN app_user u ON u.id = a.user_id
                    WHERE LEFT(u.password_hash, 4) IN ('$2a$', '$2b$', '$2y$')
                    """)).isEqualTo(1);
            assertThat(statement.executeUpdate("""
                    DELETE i FROM user_identifier i JOIN app_user u ON u.id = i.user_id
                    WHERE LEFT(u.password_hash, 4) IN ('$2a$', '$2b$', '$2y$')
                    """)).isEqualTo(2);
            assertThat(statement.executeUpdate("""
                    DELETE FROM app_user WHERE LEFT(password_hash, 4) IN ('$2a$', '$2b$', '$2y$')
                    """)).isEqualTo(1);
            connection.commit();
        }
        assertThat(count("app_user")).isEqualTo(1);
        assertThat(count("user_identifier")).isEqualTo(2);
        assertThat(count("user_authority")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM app_user", Long.class)).isEqualTo(currentId);
        assertProblem(authenticate("legacy", PASSWORD, null), 401, "INVALID_CREDENTIALS");
        assertThat(authenticate("current", PASSWORD, null).statusCode()).isEqualTo(200);
        assertThat(register(profile("legacy", "legacy@example.test")).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'legacy'", Long.class))
                .isGreaterThan(currentId).isGreaterThan(legacyId);
    }

    @Test
    void malformedJsonAndTypesReturnSafeValidationError() throws IOException, InterruptedException {
        assertProblem(post("/api/register", "{\"password\":\"" + PASSWORD + "\""), 400, "VALIDATION_ERROR");
        assertProblem(post("/api/authenticate",
                "{\"username\":\"patient\",\"password\":\"" + PASSWORD + "\",\"rememberMe\":{}}"),
                400, "VALIDATION_ERROR");
    }

    @Test
    void committedAccountSurvivesSigningFailureAndCanLoginLater() throws IOException, InterruptedException {
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
        Long id = jdbc.queryForObject("SELECT id FROM app_user", Long.class);
        faults.signing.set(true);
        assertProblem(authenticate("patient", PASSWORD, null), 503, "AUTHENTICATION_UNAVAILABLE");
        faults.signing.set(false);
        assertThat(authenticate("patient", PASSWORD, null).statusCode()).isEqualTo(200);
        assertThat(count("app_user")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM app_user", Long.class)).isEqualTo(id);
    }

    @Test
    void reportsOwnDatabaseOutageWithoutGrantingAccess() throws IOException, InterruptedException {
        faults.database.set(true);
        try {
            assertProblem(register(profile("patient", "patient@example.test")), 503, "USER_REGISTRATION_UNAVAILABLE");
            assertProblem(authenticate("patient", PASSWORD, null), 503, "AUTHENTICATION_UNAVAILABLE");
        } finally {
            faults.reset();
        }
        assertThat(count("app_user")).isZero();
    }

    @Test
    void corruptStoredHashIsUnavailableAndNotAnOrdinaryCredentialRejection() throws IOException, InterruptedException {
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
        jdbc.update("UPDATE app_user SET password_hash = 'fictional-corrupt-hash'");
        var response = authenticate("patient", PASSWORD, null);
        assertProblem(response, 503, "AUTHENTICATION_UNAVAILABLE");
        assertThat(response.body()).doesNotContain("fictional-corrupt-hash", PASSWORD);
    }

    @Test
    void lostCommitResponseRetainsAccountAndRepeatDoesNotAuthenticateByExistence() throws IOException, InterruptedException {
        faults.commit.set(UserFailureConfiguration.CommitFailure.AFTER);
        assertProblem(register(profile("patient", "patient@example.test")), 503, "USER_REGISTRATION_UNAVAILABLE");
        assertThat(count("app_user")).isEqualTo(1);
        var duplicate = register(profile("patient", "patient@example.test"));
        assertProblem(duplicate, 400, "USER_IDENTIFIER_ALREADY_USED");
        assertThat(duplicate.body()).doesNotContain("id_token");
        assertThat(authenticate("patient", PASSWORD, null).statusCode()).isEqualTo(200);
    }

    @Test
    void failureBeforeCommitDoesNotLeavePartialAccount() throws IOException, InterruptedException {
        faults.commit.set(UserFailureConfiguration.CommitFailure.BEFORE);
        assertProblem(register(profile("patient", "patient@example.test")), 503, "USER_REGISTRATION_UNAVAILABLE");
        assertThat(count("app_user")).isZero();
        assertThat(count("user_identifier")).isZero();
        assertThat(count("user_authority")).isZero();
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
    }

    @Test
    void nonUniqueIntegrityFailureDoesNotMasqueradeAsDuplicateIdentifier() {
        var invalid = new RegistrationData("patient", null, "Example", "patient@example.test", null, null);
        assertThatThrownBy(() -> accounts.createActiveUser(invalid, "fictional-unused-hash"))
                .isInstanceOf(UserAccountUnavailableException.class).hasNoCause();
        assertThat(count("app_user")).isZero();
    }

    @Test
    void remoteOutageDoesNotCreateRemoteCallsDuringUserFlow() throws IOException, InterruptedException {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(technicalAccess.getAccess()).isNotNull());
        int catalogCalls = INTEGRATION.catalogCalls.get();
        int centralCalls = INTEGRATION.centralCalls.get();
        INTEGRATION.catalogStatus.set(503);
        INTEGRATION.centralStatus.set(503);
        try {
            assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
            var login = authenticate("patient", PASSWORD, null);
            assertThat(login.statusCode()).isEqualTo(200);
            String token = json.readTree(login.body()).path("id_token").asString();
            assertThat(get("/api/account", token).statusCode()).isEqualTo(404);
            assertProblem(get("/api/account", "fictional-invalid-token"), 401, "AUTHENTICATION_REQUIRED");
            assertThat(INTEGRATION.catalogCalls.get()).isEqualTo(catalogCalls);
            assertThat(INTEGRATION.centralCalls.get()).isEqualTo(centralCalls);
        } finally {
            INTEGRATION.catalogStatus.set(200);
            INTEGRATION.centralStatus.set(200);
        }
    }

    @Test
    @Timeout(45)
    void recreatedContextRetainsAccountAndCanAuthenticate() throws IOException, InterruptedException, ParseException {
        assertThat(register(profile("patient", "patient@example.test")).statusCode()).isEqualTo(201);
        Long id = jdbc.queryForObject("SELECT id FROM app_user", Long.class);
        var settings = new LinkedHashMap<>(INTEGRATION.settings());
        settings.put("DB_HOST", MYSQL.getHost());
        settings.put("DB_PORT", MYSQL.getFirstMappedPort().toString());
        settings.put("DB_NAME", MYSQL.getDatabaseName());
        settings.put("DB_USER", MYSQL.getUsername());
        settings.put("DB_PASSWORD", MYSQL.getPassword());
        var application = new SpringApplication(AppointmentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        String[] arguments = settings.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        try (var restarted = application.run(arguments)) {
            var token = restarted.getBean(AuthenticateUserUseCase.class).authenticate("PATIENT", PASSWORD, false);
            assertThat(SignedJWT.parse(token.value()).getJWTClaimsSet().getSubject()).isEqualTo(id.toString());
        }
        assertThat(count("app_user")).isEqualTo(1);
    }

    static Map<String, Object> profile(String login, String email) {
        var body = new LinkedHashMap<String, Object>();
        body.put("login", login);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("firstName", "Fictional");
        body.put("lastName", "Patient");
        return body;
    }

    private HttpResponse<String> register(Map<String, Object> body) throws IOException, InterruptedException {
        return post("/api/register", json.writeValueAsString(body));
    }

    private HttpResponse<String> authenticate(String identifier, String password, Boolean rememberMe) throws IOException, InterruptedException {
        var body = new LinkedHashMap<String, Object>();
        body.put("username", identifier);
        body.put("password", password);
        if (rememberMe != null) {
            body.put("rememberMe", rememberMe);
        }
        return post("/api/authenticate", json.writeValueAsString(body));
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).GET();
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as("HTTP %s, code %s", status, code).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.path("code").asString()).isEqualTo(code);
        assertThat(problem.path("status").asInt()).isEqualTo(status);
        assertThat(response.body()).doesNotContain("id_token", PASSWORD, "stackTrace");
        assertNoSessionAndNoCache(response);
    }

    private static void assertNoSessionAndNoCache(HttpResponse<String> response) {
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }
}

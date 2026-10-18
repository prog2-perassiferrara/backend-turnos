package com.prog2.appointment.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.application.usecases.AcquireTechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.infrastructure.client.adapter.SafeIntegrationHttpClient;
import com.prog2.appointment.integration.infrastructure.client.adapter.WebClientCatalogTechnicalTokenAdapter;
import com.prog2.appointment.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;
import com.prog2.appointment.integration.infrastructure.config.TechnicalAccessRecoveryAdapter;
import com.prog2.appointment.integration.infrastructure.persistence.adapter.InMemoryTechnicalAccessAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contratos HTTP/criptográficos reales hacia proveedores ficticios; nunca usa cátedra ni Catálogo reales. */
class TechnicalAccessHttpTests {
    private static final TestIntegrationSupport PROVIDERS = new TestIntegrationSupport();
    private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private final ServiceTokenIssuer issuer = new ServiceTokenIssuer(PROVIDERS.privateKey(), clock);
    private final WebClientCatalogTechnicalTokenAdapter catalog = new WebClientCatalogTechnicalTokenAdapter(
            new SafeIntegrationHttpClient(PROVIDERS.catalogUrl(), false, Duration.ofSeconds(1)), issuer);
    private final WebClientCentralIntegrationAdapter central = new WebClientCentralIntegrationAdapter(
            new SafeIntegrationHttpClient(PROVIDERS.centralUrl(), true, Duration.ofSeconds(1)));

    @BeforeEach
    void resetProviders() {
        PROVIDERS.catalogStatus.set(200);
        PROVIDERS.centralStatus.set(200);
        PROVIDERS.catalogBody.set("{\"idToken\":\"" + PROVIDERS.centralToken + "\",\"futureField\":true}");
        PROVIDERS.centralBody.set(TestIntegrationSupport.configuration("PROVISIONED"));
        PROVIDERS.catalogCalls.set(0);
        PROVIDERS.centralCalls.set(0);
        PROVIDERS.serviceValidationFailure.set(null);
    }

    @AfterAll
    static void closeProviders() {
        PROVIDERS.close();
    }

    @Test
    void acquiresAccessThroughRealHttpAndKeepsItAfterCatalogFails()
            throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
        var repository = new InMemoryTechnicalAccessAdapter(clock);
        var acquire = new AcquireTechnicalAccessUseCaseImpl(catalog, central, repository, clock, TestIntegrationSupport.GROUP);
        var executor = Executors.newSingleThreadExecutor();
        try (var recovery = new TechnicalAccessRecoveryAdapter(acquire, repository, clock, executor,
                _ -> {}, Duration.ZERO, Duration.ofSeconds(30))) {
            recovery.requestRecovery();
            executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
            var access = new TechnicalAccessUseCaseImpl(repository, recovery, clock);
            assertThat(access.getAccess().configuration().kafkaAppointmentActionsTopic()).isEqualTo("assigned-actions");
            assertThat(PROVIDERS.catalogCalls).hasValue(1);
            assertThat(PROVIDERS.centralCalls).hasValue(1);
            assertThat(PROVIDERS.serviceValidationFailure.get()).isNull();
            assertThat(PROVIDERS.centralBearer.get()).isEqualTo("Bearer " + PROVIDERS.centralToken);
            assertThat(PROVIDERS.catalogBearer.get()).isNotEqualTo(PROVIDERS.centralBearer.get());
            PROVIDERS.catalogStatus.set(503);
            assertThat(access.getAccess().token().value()).isEqualTo(PROVIDERS.centralToken);
            assertThat(PROVIDERS.catalogCalls).hasValue(1);
            assertThat(PROVIDERS.unexpectedCalls).hasValue(0);
        }
    }

    @Test
    void signsNewServiceCredentialAfterPreviousOneExpiredWithoutUserLogin() {
        catalog.fetchToken();
        String first = PROVIDERS.catalogBearer.get();
        var later = new WebClientCatalogTechnicalTokenAdapter(
                new SafeIntegrationHttpClient(PROVIDERS.catalogUrl(), false, Duration.ofSeconds(1)),
                new ServiceTokenIssuer(PROVIDERS.privateKey(), Clock.offset(clock, Duration.ofMinutes(6))));
        later.fetchToken();
        assertThat(PROVIDERS.serviceValidationFailure.get()).isNull();
        assertThat(first).isNotEqualTo(PROVIDERS.catalogBearer.get());
        assertThat(PROVIDERS.unexpectedCalls).hasValue(0);
    }

    @ParameterizedTest
    @CsvSource({"401,CATALOG_AUTHENTICATION", "403,ACCESS_DENIED", "503,TEMPORARY_UNAVAILABLE",
            "500,TEMPORARY_UNAVAILABLE", "400,INVALID_RESPONSE", "302,INVALID_RESPONSE"})
    void classifiesCatalogErrorsWithoutCodeOrDetail(int status, AccessFailure expected) {
        PROVIDERS.catalogStatus.set(status);
        PROVIDERS.catalogBody.set("{\"detail\":\"fictional-sensitive-value\"}");
        assertThatThrownBy(catalog::fetchToken).isInstanceOfSatisfying(IntegrationGatewayException.class,
                error -> assertThat(error.failure()).isEqualTo(expected))
                .hasNoCause().hasMessageNotContaining("fictional-sensitive-value");
        assertThat(PROVIDERS.catalogCalls).hasValue(1);
    }

    @Test
    void distinguishesCentral401AndIntegrationNotFound() {
        var token = new TechnicalToken(PROVIDERS.centralToken, null);
        PROVIDERS.centralStatus.set(401);
        PROVIDERS.centralBody.set("{}");
        assertThatThrownBy(() -> central.fetchConfiguration(token)).isInstanceOfSatisfying(
                IntegrationGatewayException.class,
                error -> assertThat(error.failure()).isEqualTo(AccessFailure.CENTRAL_TOKEN_REJECTED));
        PROVIDERS.centralStatus.set(403);
        PROVIDERS.centralBody.set("{\"code\":\"STUDENT_INTEGRATION_NOT_FOUND\",\"detail\":\"irrelevant\"}");
        assertThatThrownBy(() -> central.fetchConfiguration(token)).isInstanceOfSatisfying(
                IntegrationGatewayException.class,
                error -> assertThat(error.failure()).isEqualTo(AccessFailure.INTEGRATION_NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"idToken\":\"\"}", "{\"idToken\":\"invalid-jwt\"}", "not-json", ""})
    void rejectsMalformedOrMissingCatalogToken(String body) {
        PROVIDERS.catalogBody.set(body);
        assertThatThrownBy(catalog::fetchToken).isInstanceOfSatisfying(IntegrationGatewayException.class,
                error -> assertThat(error.failure()).isEqualTo(AccessFailure.INVALID_RESPONSE)).hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"integration\":{}}", "{\"provisioningStatus\":\"UNKNOWN\"}", "not-json", ""})
    void rejectsInvalidCentralEnvelopeOrStatus(String body) {
        PROVIDERS.centralBody.set(body);
        var token = new TechnicalToken(PROVIDERS.centralToken, null);
        assertThatThrownBy(() -> central.fetchConfiguration(token)).isInstanceOfSatisfying(
                IntegrationGatewayException.class,
                error -> assertThat(error.failure()).isEqualTo(AccessFailure.INVALID_RESPONSE)).hasNoCause();
    }

    @Test
    void tokenWithoutExpIsAcceptedAndDiagnosticRepresentationsAreRedacted() {
        String noExp = TestIntegrationSupport.token(null);
        PROVIDERS.catalogBody.set("{\"idToken\":\"" + noExp + "\"}");
        var token = catalog.fetchToken();
        assertThat(token.expiresAt()).isNull();
        assertThat(token.toString()).doesNotContain(noExp);
    }

    @Test
    void timesOutAnUnresponsiveProviderWithoutRetryingTheRequest() throws IOException {
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/catedra/token", exchange -> {
            calls.incrementAndGet();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    TestIntegrationSupport.respond(exchange, 503, "{}");
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            assertTemporaryTransportFailure(server);
            assertThat(calls).hasValue(1);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void reportsAnInterruptedConnectionWithoutRetryingOrKeepingTheCause() throws IOException {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/catedra/token", exchange -> {
            calls.incrementAndGet();
            exchange.close();
        });
        server.start();
        try {
            assertTemporaryTransportFailure(server);
            assertThat(calls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    private void assertTemporaryTransportFailure(HttpServer server) {
        var gateway = new WebClientCatalogTechnicalTokenAdapter(new SafeIntegrationHttpClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), false, Duration.ofSeconds(1)), issuer);
        assertThatThrownBy(gateway::fetchToken).isInstanceOfSatisfying(IntegrationGatewayException.class,
                error -> assertThat(error.failure()).isEqualTo(AccessFailure.TEMPORARY_UNAVAILABLE)).hasNoCause();
    }
}

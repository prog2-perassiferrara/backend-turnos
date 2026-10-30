package com.prog2.appointment.availability;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.enums.OccupancyStatus;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.infrastructure.client.adapter.WebClientAppointmentOccupanciesAdapter;
import com.prog2.appointment.availability.infrastructure.client.adapter.WebClientProfessionalAgendaAdapter;
import com.prog2.appointment.availability.infrastructure.config.AvailabilityConfiguration;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.sun.net.httpserver.HttpExchange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Transporte y adaptación reales, con firmas y servidores HTTP separados para detectar cruces de credenciales. */
@Timeout(15)
class AvailabilityClientTests {
    private AvailabilityTestSupport providers;
    private TechnicalAccessUseCase access;
    private WebClientProfessionalAgendaAdapter catalog;
    private WebClientAppointmentOccupanciesAdapter central;

    @BeforeEach
    void startProviders() {
        providers = new AvailabilityTestSupport();
        access = mock(TechnicalAccessUseCase.class);
        when(access.getAccess()).thenReturn(session(providers.centralToken));
        var issuer = new ServiceTokenIssuer((java.security.interfaces.RSAPrivateCrtKey)
                TestIntegrationSupport.KEYS.getPrivate(), Clock.fixed(AvailabilityTestSupport.NOW, ZoneOffset.UTC));
        catalog = new WebClientProfessionalAgendaAdapter(AvailabilityConfiguration.httpClient(providers.catalogUrl(),
                Duration.ofSeconds(5), AvailabilityFailure.CATALOG_UNAVAILABLE), issuer);
        central = central(Duration.ofSeconds(5));
    }

    @AfterEach
    void stopProviders() {
        providers.close();
    }

    @Test
    void readsBothTimeFormatsAndUsesExactlyTheDailyRangeAndExpectedCredentials() {
        var agenda = catalog.find(15).orElseThrow();
        assertThat(agenda.candidateStarts(AvailabilityTestSupport.DATE))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
        String entries = AvailabilityTestSupport.position(AvailabilityTestSupport.DATE, "09:00", "CONFIRMED", null)
                + "," + AvailabilityTestSupport.position(AvailabilityTestSupport.DATE, "09:30:00", "HELD",
                        AvailabilityTestSupport.NOW.plusSeconds(60).toString());
        providers.occupancyBody.set(AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE, entries));
        var occupied = central.get(15, AvailabilityTestSupport.DATE);
        assertThat(occupied).hasSize(2);
        assertThat(occupied.getFirst().status()).isEqualTo(OccupancyStatus.CONFIRMED);
        assertThat(occupied.getLast().startTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(providers.lastOccupancyQuery).hasValue("professionalId=15&from=2026-11-02&to=2026-11-02");
        assertThat(providers.invalidCredentials).hasValue(0);
        assertThat(providers.unexpectedCalls).hasValue(0);
    }

    @Test
    void emptyCollectionsAndDisabledFlagsAreValidData() {
        providers.agendaBody.set(AvailabilityTestSupport.agenda(15, false));
        assertThat(catalog.find(15).orElseThrow().enabled()).isFalse();
        assertThat(central.get(15, AvailabilityTestSupport.DATE)).isEmpty();
        providers.agendaBody.set(AvailabilityTestSupport.agenda(15, true)
                .replace("\"enabled\":true}],", "\"enabled\":false}],"));
        assertThat(catalog.find(15).orElseThrow().candidateStarts(AvailabilityTestSupport.DATE)).isEmpty();
    }

    @Test
    void onlyTheContractual404MeansAbsentProfessional() {
        providers.agendaStatus.set(404);
        providers.agendaBody.set("{\"code\":\"PROFESSIONAL_NOT_FOUND\"}");
        assertThat(catalog.find(15)).isEmpty();
        providers.agendaBody.set("{\"detail\":\"PROFESSIONAL_NOT_FOUND\",\"code\":\"OTHER\"}");
        catalogFails(AvailabilityFailure.CATALOG_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STALE", "NO_COPY", "SYNCHRONIZING", "RECOVERING", "FAILED", "UNVERIFIABLE"})
    void neverTreatsANonCurrentAgendaAsAvailable(String state) {
        providers.agendaBody.set(AvailabilityTestSupport.agenda(15, true).replace("CURRENT", state));
        catalogFails(AvailabilityFailure.CATALOG_NOT_CURRENT);
    }

    @ParameterizedTest
    @MethodSource("invalidAgendas")
    void malformedOrInconsistentCatalogDataIsNotAbsenceOrAnEmptyAgenda(String body) {
        providers.agendaBody.set(body);
        catalogFails(AvailabilityFailure.CATALOG_UNAVAILABLE);
        assertThat(providers.agendaCalls).hasValue(1);
    }

    static Stream<String> invalidAgendas() {
        String valid = AvailabilityTestSupport.agenda(15, true);
        return Stream.of("{}", "null", "{", "", valid.replace("\"id\":15", "\"id\":16"),
                valid.replace("\"professionalId\":15", "\"professionalId\":16"),
                valid.replace("\"copyAvailable\":true", "\"copyAvailable\":false"),
                valid.replace("\"observedVersion\":7", "\"observedVersion\":8"),
                valid.replace("\"appliedVersion\":7", "\"appliedVersion\":null"),
                valid.replace("\"slotDurationMinutes\":30", "\"slotDurationMinutes\":0"),
                valid.replace("\"startTime\":\"09:00\"", "\"startTime\":\"25:00\""),
                valid.replace("\"dayOfWeek\":\"MONDAY\"", "\"dayOfWeek\":null"),
                valid.replace("\"enabled\":true", "\"enabled\":null"),
                valid.replace("2026-11-02T10:59:59Z", "2026-11-02T11:00:01Z"));
    }

    @ParameterizedTest
    @MethodSource("catalogErrors")
    void classifiesByStatusAndCodeInsteadOfDetail(int status, String code, AvailabilityFailure expected) {
        providers.agendaStatus.set(status);
        providers.agendaBody.set("{\"code\":\"" + code + "\",\"detail\":\"fictional-private-response\"}");
        catalogFails(expected);
        assertThat(providers.agendaCalls).hasValue(1);
    }

    static Stream<Arguments> catalogErrors() {
        return Stream.of(Arguments.of(503, "CATALOG_NOT_CURRENT", AvailabilityFailure.CATALOG_NOT_CURRENT),
                Arguments.of(500, "CATALOG_NOT_CURRENT", AvailabilityFailure.CATALOG_UNAVAILABLE),
                Arguments.of(401, "AUTHENTICATION_REQUIRED", AvailabilityFailure.CATALOG_UNAVAILABLE),
                Arguments.of(403, "ACCESS_DENIED", AvailabilityFailure.CATALOG_UNAVAILABLE),
                Arguments.of(400, "OTHER", AvailabilityFailure.CATALOG_UNAVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("invalidOccupancies")
    void incompatibleOccupanciesNeverBecomeAnEmptyValidCollection(String body) {
        providers.occupancyBody.set(body);
        centralFails(central, AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
        assertThat(providers.occupancyCalls).hasValue(1);
    }

    static Stream<String> invalidOccupancies() {
        String valid = AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE, "");
        return Stream.of("{}", "null", "{", "", valid.replace("\"professionalId\":15", "\"professionalId\":16"),
                valid.replace("\"occupancies\":[]", "\"occupancies\":null"),
                valid.replace("\"from\":\"2026-11-02\"", "\"from\":\"2026-11-01\""),
                valid.replace("\"to\":\"2026-11-02\"", "\"to\":\"2026-11-03\""),
                AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE,
                        AvailabilityTestSupport.position(AvailabilityTestSupport.DATE, "09:00", "UNKNOWN", null)),
                AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE,
                        AvailabilityTestSupport.position(AvailabilityTestSupport.DATE, "09:00", "HELD", null)),
                AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE,
                        AvailabilityTestSupport.position(AvailabilityTestSupport.DATE.plusDays(1), "09:00", "CONFIRMED", null)),
                AvailabilityTestSupport.occupancies(15, AvailabilityTestSupport.DATE,
                        AvailabilityTestSupport.position(AvailabilityTestSupport.DATE, "invalid", "CONFIRMED", null)));
    }

    @Test
    void unavailableSessionDoesNotCallCentralAndDoesNotExposeThePortException() {
        when(access.getAccess()).thenThrow(new IllegalStateException("fictional-private-session"));
        centralFails(central, AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE);
        assertThat(providers.occupancyCalls).hasValue(0);
    }

    @Test
    void a401InvalidatesOnlyTheUsedTokenWithoutAnotherHttpAttempt() {
        providers.occupancyStatus.set(401);
        providers.occupancyBody.set("{}");
        centralFails(central, AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE);
        verify(access).rejectAccess(providers.centralToken);
        verify(access, times(1)).getAccess();
        assertThat(providers.occupancyCalls).hasValue(1);
    }

    @Test
    void missingIntegrationOrOtherFailuresDoNotRejectAValidToken() {
        providers.occupancyStatus.set(403);
        providers.occupancyBody.set("{\"code\":\"STUDENT_INTEGRATION_NOT_FOUND\"}");
        centralFails(central, AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE);
        providers.occupancyStatus.set(503);
        providers.occupancyBody.set("not-json");
        centralFails(central, AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
        verify(access, never()).rejectAccess(anyString());
    }

    @Test
    void doesNotFollowRedirectsOrRetryAConnectionClosedBeforeHeaders() {
        providers.occupancyHandler.set(exchange -> {
            exchange.getResponseHeaders().set("Location", providers.catalogUrl() + "/unexpected");
            TestIntegrationSupport.respond(exchange, 302, "{}");
        });
        centralFails(central, AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
        assertThat(providers.unexpectedCalls).hasValue(0);
        providers.resetQueries();
        providers.occupancyHandler.set(HttpExchange::close);
        centralFails(central, AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
        assertThat(providers.occupancyCalls).hasValue(1);
    }

    @Test
    void boundsTheWholeResponseWhenSuccessHeadersArriveButTheBodyStalls() {
        assertThat(central.get(15, AvailabilityTestSupport.DATE)).isEmpty();
        providers.resetQueries();
        var release = new CountDownLatch(1);
        providers.occupancyHandler.set(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write("{".getBytes(StandardCharsets.UTF_8));
                body.flush();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                exchange.close();
            }
        });
        var shortClient = central(Duration.ofMillis(500));
        try {
            centralFails(shortClient, AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
            assertThat(providers.occupancyCalls).hasValue(1);
        } finally {
            release.countDown();
        }
    }

    private WebClientAppointmentOccupanciesAdapter central(Duration timeout) {
        return new WebClientAppointmentOccupanciesAdapter(AvailabilityConfiguration.httpClient(providers.centralUrl(),
                timeout, AvailabilityFailure.OCCUPANCIES_UNAVAILABLE), access);
    }

    private void catalogFails(AvailabilityFailure expected) {
        assertThatThrownBy(() -> catalog.find(15)).hasNoCause()
                .isInstanceOfSatisfying(AvailabilityGatewayException.class,
                        failure -> assertThat(failure.failure()).isEqualTo(expected));
    }

    private static void centralFails(WebClientAppointmentOccupanciesAdapter gateway, AvailabilityFailure expected) {
        assertThatThrownBy(() -> gateway.get(15, AvailabilityTestSupport.DATE)).hasNoCause()
                .isInstanceOfSatisfying(AvailabilityGatewayException.class,
                        failure -> assertThat(failure.failure()).isEqualTo(expected));
    }

    private static TechnicalAccess session(String token) {
        return new TechnicalAccess(new TechnicalToken(token, AvailabilityTestSupport.NOW.plusSeconds(3600)),
                new IntegrationConfiguration(TestIntegrationSupport.GROUP, ProvisioningStatus.PROVISIONED,
                        "fictional-broker:9092", "fictional-group", "fictional-actions", "fictional-phone"));
    }
}

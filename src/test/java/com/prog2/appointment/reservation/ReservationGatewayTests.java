package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prog2.appointment.infrastructure.config.ReservationProperties;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientAppointmentHoldAdapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * HTTP real ficticio: se comprueban contrato, presupuesto y un solo intento, no respuestas mock del
 * cliente.
 */
@Timeout(15)
class ReservationGatewayTests {
    private ReservationTestSupport providers;
    private TechnicalAccessUseCase access;
    private WebClientAppointmentHoldAdapter gateway;
    private ReservationProcess process;

    @BeforeEach
    void setup() {
        providers = new ReservationTestSupport(() -> "fictional:9092");
        access = mock(TechnicalAccessUseCase.class);
        when(access.getAccess())
                .thenReturn(
                        new TechnicalAccess(
                                new TechnicalToken(providers.token, null),
                                new IntegrationConfiguration(
                                        ReservationTestSupport.GROUP,
                                        ProvisioningStatus.PROVISIONED,
                                        "fictional:9092",
                                        "fictional-consumer",
                                        "fictional-actions",
                                        "fictional-phone")));
        gateway = gateway(Duration.ofSeconds(2));
        process = ReservationProcessTests.creating();
    }

    private WebClientAppointmentHoldAdapter gateway(Duration timeout) {
        var transport =
                HttpClient.create()
                        .disableRetry(true)
                        .followRedirect(false)
                        .responseTimeout(timeout);
        var client =
                WebClient.builder()
                        .baseUrl(providers.settings().get("CATEDRA_BASE_URL"))
                        .clientConnector(new ReactorClientHttpConnector(transport))
                        .build();
        return new WebClientAppointmentHoldAdapter(client, access, timeout);
    }

    @AfterEach
    void close() {
        providers.close();
    }

    @Test
    void sendsTheSameStablePatientAndAccountNamesInBothSteps() {
        var hold = gateway.create(process);
        var accepted = gateway.confirm(process.acceptHold(hold).beginConfirmation());
        assertThat(accepted).isEqualTo(hold);
        assertThat(providers.creates).hasValue(1);
        assertThat(providers.confirmations).hasValue(1);
        assertThat(providers.submittedPatient).hasValue("user-42");
        assertThat(providers.submittedFirstName).hasValue("Paciente");
    }

    @ParameterizedTest
    @CsvSource({
        "400,INVALID_SLOT,INVALID_RESERVATION_SELECTION,true",
        "409,SLOT_ALREADY_HELD,SLOT_NOT_AVAILABLE,true",
        "500,SLOT_ALREADY_HELD,HOLD_CREATION_UNCERTAIN,false",
        "503,,HOLD_CREATION_UNCERTAIN,false",
        "403,STUDENT_INTEGRATION_NOT_FOUND,TECHNICAL_INTEGRATION_UNAVAILABLE,true"
    })
    void classifiesByStatusAndCodeWithoutReplaying(
            int status, String code, ReservationFailure expected, boolean definitive) {
        providers.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    com.prog2.appointment.integration.TestIntegrationSupport.respond(
                            exchange,
                            status,
                            code == null
                                    ? "not-json"
                                    : "{\"code\":\""
                                            + code
                                            + "\",\"detail\":\"fictional-private-detail\"}");
                });
        var error = assertThrows(ReservationGatewayException.class, () -> gateway.create(process));
        assertThat(error.failure()).isEqualTo(expected);
        assertThat(error.definitive()).isEqualTo(definitive);
        assertThat(error.attempted()).isTrue();
        assertThat(error.getCause()).isNull();
        assertThat(error.getMessage()).doesNotContain("fictional-private-detail", providers.token);
        assertThat(providers.creates).hasValue(1);
    }

    @Test
    void rejectsOnlyTheTokenUsedByA401AndDoesNotReplay() {
        providers.creationStatus.set(401);
        assertThatThrownBy(() -> gateway.create(process))
                .isInstanceOf(ReservationGatewayException.class);
        verify(access).rejectAccess(providers.token);
        assertThat(providers.creates).hasValue(1);
    }

    @Test
    void missingAccessIsKnownToBeBeforeThePost() {
        doThrow(new IllegalStateException()).when(access).getAccess();
        var error = assertThrows(ReservationGatewayException.class, () -> gateway.create(process));
        assertThat(error.attempted()).isFalse();
        assertThat(providers.creates).hasValue(0);
    }

    @Test
    void aRedirectOrAnIncompleteSuccessDoesNotCreateAnAutomaticSecondRequest() {
        providers.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders()
                            .set(
                                    "Location",
                                    providers.settings().get("CATALOG_BASE_URL") + "/unexpected");
                    com.prog2.appointment.integration.TestIntegrationSupport.respond(
                            exchange, 302, "{}");
                });
        assertThatThrownBy(() -> gateway.create(process))
                .isInstanceOf(ReservationGatewayException.class);
        assertThat(providers.creates).hasValue(1);
        providers.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    com.prog2.appointment.integration.TestIntegrationSupport.respond(
                            exchange, 201, "{\"status\":\"HELD\"}");
                });
        var error = assertThrows(ReservationGatewayException.class, () -> gateway.create(process));
        assertThat(error.failure()).isEqualTo(ReservationFailure.HOLD_CREATION_UNCERTAIN);
        assertThat(error.definitive()).isFalse();
        assertThat(providers.creates).hasValue(2);
    }

    @Test
    void aStalledBodyAfterSuccessHeadersIsBoundedAndRemainsUncertain() {
        var release = new CountDownLatch(1);
        providers.createOverride.set(
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(201, 0);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        try {
            var error =
                    assertThrows(
                            ReservationGatewayException.class,
                            () -> gateway(Duration.ofMillis(150)).create(process));
            assertThat(error.failure()).isEqualTo(ReservationFailure.HOLD_CREATION_UNCERTAIN);
            assertThat(error.attempted()).isTrue();
            assertThat(providers.creates).hasValue(1);
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "app.reservation.http-timeout,0s",
        "app.reservation.http-timeout,2147483648ms",
        "app.reservation.metadata-timeout,invalid",
        "app.reservation.attempts,0",
        "app.reservation.attempts,11",
        "app.reservation.retry-delay,-1s",
        "app.reservation.preparation-cooldown,0s"
    })
    void invalidSettingsFailSafely(String key, String value) {
        var environment = new MockEnvironment().withProperty(key, value);
        assertThatThrownBy(() -> ReservationProperties.from(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid app.reservation configuration")
                .hasNoCause();
    }

    @Test
    void optionalSettingsUseTheApprovedDefaults() {
        var settings = ReservationProperties.from(new MockEnvironment());
        assertThat(settings.httpTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.attempts()).isEqualTo(3);
        assertThat(settings.metadataTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.preparationCooldown()).isEqualTo(Duration.ofSeconds(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HELD", "CONFIRMED"})
    void readsTheExactSlotWithoutInferringOwnershipOrDroppingAnExpiredHeldEntry(String status) {
        String expiry = status.equals("HELD") ? "\"2026-11-02T10:00:00Z\"" : "null";
        providers.occupanciesOverride.set(
                exchange -> {
                    assertThat(exchange.getRequestURI().getRawQuery())
                            .contains("professionalId=15", "from=2026-11-02", "to=2026-11-02");
                    var entries =
                            "[{\"date\":\"2026-11-02\",\"startTime\":\"09:00:00\",\"status\":\""
                                    + status
                                    + "\",\"expiresAt\":"
                                    + expiry
                                    + ",\"futureField\":true}]";
                    com.prog2.appointment.integration.TestIntegrationSupport.respond(
                            exchange, 200, occupancyBody(entries));
                });
        var occupied = gateway.findOccupancy(process.selection()).orElseThrow();
        assertThat(occupied.confirmed()).isEqualTo(status.equals("CONFIRMED"));
        if (status.equals("HELD")) {
            assertThat(occupied.expiresAt())
                    .isEqualTo(java.time.Instant.parse("2026-11-02T10:00:00Z"));
        } else {
            assertThat(occupied.expiresAt()).isNull();
        }
        assertThat(providers.creates).hasValue(0);
    }

    @Test
    void anEmptyListIsFreeAndConfirmedTakesPrecedenceOverHeld() {
        assertThat(gateway.findOccupancy(process.selection())).isEmpty();
        String entries =
                """
                [{"date":"2026-11-02","startTime":"09:00","status":"HELD","expiresAt":"2026-11-02T11:05:00Z"},
                 {"date":"2026-11-02","startTime":"09:00:00","status":"CONFIRMED","expiresAt":null}]
                """;
        providers.occupanciesOverride.set(
                exchange ->
                        com.prog2.appointment.integration.TestIntegrationSupport.respond(
                                exchange, 200, occupancyBody(entries)));
        assertThat(gateway.findOccupancy(process.selection()).orElseThrow().confirmed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "[null]",
                "[{\"date\":\"2026-11-02\",\"startTime\":\"09:00\",\"status\":\"HELD\"}]",
                "[{\"date\":\"2026-11-02\",\"startTime\":\"09:00\",\"status\":\"PHONE_PENDING\"}]",
                "[{\"date\":\"2026-11-03\",\"startTime\":\"09:00\",\"status\":\"CONFIRMED\"}]",
                "[{\"date\":\"2026-11-02\",\"startTime\":\"09:00\",\"status\":\"CONFIRMED\",\"expiresAt\":\"2026-11-02T11:05:00Z\"}]"
            })
    void incompatibleOccupancyDataNeverMeansTheSlotIsFree(String entries) {
        providers.occupanciesOverride.set(
                exchange ->
                        com.prog2.appointment.integration.TestIntegrationSupport.respond(
                                exchange, 200, occupancyBody(entries)));
        var failure =
                assertThrows(
                        ReservationGatewayException.class,
                        () -> gateway.findOccupancy(process.selection()));
        assertThat(failure.failure()).isEqualTo(ReservationFailure.OCCUPANCIES_UNAVAILABLE);
        assertThat(providers.creates).hasValue(0);
    }

    @Test
    void anUnauthorizedOccupancyReadRejectsOnlyTheUsedTechnicalToken() {
        providers.occupanciesOverride.set(
                exchange ->
                        com.prog2.appointment.integration.TestIntegrationSupport.respond(
                                exchange, 401, "{}"));
        assertThrows(
                ReservationGatewayException.class,
                () -> gateway.findOccupancy(process.selection()));
        verify(access).rejectAccess(providers.token);
        assertThat(providers.occupancyQueries).hasValue(1);
        assertThat(providers.creates).hasValue(0);
    }

    private static String occupancyBody(String entries) {
        return "{\"professionalId\":15,\"from\":\"2026-11-02\",\"to\":\"2026-11-02\",\"occupancies\":"
                + entries
                + ",\"futureField\":true}";
    }
}

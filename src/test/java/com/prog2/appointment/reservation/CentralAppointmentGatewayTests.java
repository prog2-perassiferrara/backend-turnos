package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.model.*;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientCentralAppointmentAdapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Un GET HTTP real contra un proveedor ficticio, filtrado propio y presupuesto acotado. */
@Timeout(15)
class CentralAppointmentGatewayTests {
    private ReservationTestSupport provider;
    private TechnicalAccessUseCase access;
    private WebClientCentralAppointmentAdapter gateway;

    @BeforeEach
    void setup() {
        provider = new ReservationTestSupport(() -> "fictional:9092");
        access = mock(TechnicalAccessUseCase.class);
        when(access.getAccess())
                .thenReturn(
                        new TechnicalAccess(
                                new TechnicalToken(provider.token, null),
                                new IntegrationConfiguration(
                                        ReservationTestSupport.GROUP,
                                        ProvisioningStatus.PROVISIONED,
                                        "fictional:9092",
                                        "fictional-consumer",
                                        "fictional-actions",
                                        "fictional-phone")));
        gateway = client(Duration.ofSeconds(1));
    }

    private WebClientCentralAppointmentAdapter client(Duration timeout) {
        var transport =
                HttpClient.create()
                        .disableRetry(true)
                        .followRedirect(false)
                        .responseTimeout(timeout);
        var client =
                WebClient.builder()
                        .baseUrl(provider.settings().get("CATEDRA_BASE_URL"))
                        .clientConnector(new ReactorClientHttpConnector(transport))
                        .build();
        return new WebClientCentralAppointmentAdapter(client, access, timeout);
    }

    @AfterEach
    void close() {
        provider.close();
    }

    private String appointment(String patient, String time, String state) {
        return "[{\"reservationId\":8251,\"reservationProcessId\":\"process-fictional\",\"externalPatientId\":\""
                + patient
                + "\",\"professionalId\":15,\"appointmentDate\":\"2026-11-02\",\"startTime\":\""
                + time
                + "\",\"status\":\""
                + state
                + "\",\"futureField\":true}]";
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00", "09:00:00"})
    void matchesOnlyTheExactProcessPatientAndSelection(String time) {
        provider.appointmentsOverride.set(
                exchange ->
                        TestIntegrationSupport.respond(
                                exchange, 200, appointment("user-42", time, "CONFIRMED")));
        var result = gateway.find(SubmitReservationPhoneTests.waiting()).orElseThrow();
        assertThat(result.reservationId()).isEqualTo(8251);
        assertThat(provider.appointmentFilters.get())
                .containsEntry("externalPatientId", "user-42")
                .containsEntry("from", "2026-11-02")
                .containsEntry("to", "2026-11-02")
                .containsEntry("page", "0")
                .containsEntry("size", "100")
                .doesNotContainKey("status");
        assertThat(provider.appointmentQueries).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "[{\"reservationProcessId\":\"process-fictional\",\"externalPatientId\":\"user-42\"}]"
            })
    void missingEvidenceStaysInconclusive(String data) {
        provider.appointmentsOverride.set(
                exchange -> TestIntegrationSupport.respond(exchange, 200, data));
        assertThat(gateway.find(SubmitReservationPhoneTests.waiting())).isEmpty();
    }

    @Test
    void foreignPatientAndDifferentSelectionCannotConfirmTheOwnedProcess() {
        provider.appointmentsOverride.set(
                exchange ->
                        TestIntegrationSupport.respond(
                                exchange, 200, appointment("another-user", "09:00", "CONFIRMED")));
        assertThat(gateway.find(SubmitReservationPhoneTests.waiting())).isEmpty();
        provider.appointmentsOverride.set(
                exchange ->
                        TestIntegrationSupport.respond(
                                exchange, 200, appointment("user-42", "09:30", "CONFIRMED")));
        assertThat(gateway.find(SubmitReservationPhoneTests.waiting())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void statusErrorsAreSingleAttemptsAndUnauthorizedOnlyInvalidatesTheUsedToken(int status) {
        provider.appointmentsOverride.set(
                exchange -> TestIntegrationSupport.respond(exchange, status, "{}"));
        var process = SubmitReservationPhoneTests.waiting();
        assertThatThrownBy(() -> gateway.find(process))
                .isInstanceOf(ReservationGatewayException.class);
        assertThat(provider.appointmentQueries).hasValue(1);
        if (status == 401) verify(access).rejectAccess(provider.token);
        else verify(access, never()).rejectAccess(any());
    }

    @Test
    void stalledBodyIsBoundedWithoutRetry() {
        var release = new CountDownLatch(1);
        provider.appointmentsOverride.set(
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, 1000);
                    exchange.getResponseBody().write('[');
                    exchange.getResponseBody().flush();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        try {
            var bounded = client(Duration.ofMillis(150));
            var process = SubmitReservationPhoneTests.waiting();
            assertThatThrownBy(() -> bounded.find(process))
                    .isInstanceOf(ReservationGatewayException.class);
            assertThat(provider.appointmentQueries).hasValue(1);
        } finally {
            release.countDown();
        }
    }
}

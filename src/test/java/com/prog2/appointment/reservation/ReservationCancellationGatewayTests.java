package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.model.*;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.enums.*;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientReservationCancellationAdapter;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Contrato y timeout del cliente real contra HTTP ficticio; no se sustituye WebClient. */
@Timeout(15)
class ReservationCancellationGatewayTests {
    private ReservationTestSupport providers;
    private TechnicalAccessUseCase access;
    private ReservationProcess process;

    @BeforeEach
    void setup() {
        providers = new ReservationTestSupport(() -> "fictional:9092");
        access = mock(TechnicalAccessUseCase.class);
        when(access.getAccess()).thenReturn(new TechnicalAccess(new TechnicalToken(providers.token, null),
                new IntegrationConfiguration(ReservationTestSupport.GROUP, ProvisioningStatus.PROVISIONED,
                        "fictional:9092", "fictional-consumer", "fictional-actions", "fictional-phone")));
        process = ReservationProcessTests.confirming().apply(
                ReservationProcessTests.action(ReservationActionType.CONFIRMED, "confirmed"), ReservationProcessTests.NOW);
    }

    @AfterEach
    void close() { providers.close(); }

    private WebClientReservationCancellationAdapter gateway(Duration timeout) {
        var transport = HttpClient.create().disableRetry(true).followRedirect(false).responseTimeout(timeout);
        return new WebClientReservationCancellationAdapter(WebClient.builder()
                .baseUrl(providers.settings().get("CATEDRA_BASE_URL"))
                .clientConnector(new ReactorClientHttpConnector(transport)).build(), access, timeout);
    }

    @Test
    void sendsOnlyTheKnownProcessAndNoReasonOrPatientBody() {
        var result = gateway(Duration.ofSeconds(2)).cancel(process);
        assertThat(result.centralProcessId()).isEqualTo(process.hold().centralProcessId());
        assertThat(result.reservationId()).isEqualTo(8251L);
        assertThat(providers.cancellationPath.get()).isEqualTo("/api/appointments/process-fictional/cancel");
        assertThat(providers.cancellationBody.get()).isEmpty();
        assertThat(providers.credentialFailures).hasValue(0);
        assertThat(providers.cancellations).hasValue(1);
    }

    @Test
    void acceptsCompatibleAdditionalFieldsOnACancelledReservation() {
        providers.cancellationOverride.set(exchange -> TestIntegrationSupport.respond(exchange, 200,
                "{\"reservationProcessId\":\"process-fictional\",\"reservationId\":8251,\"status\":\"CANCELLED\","
                + "\"cancelledAt\":\"2026-11-02T11:00:00Z\",\"extra\":true}"));
        var result = gateway(Duration.ofSeconds(2)).cancel(process);
        assertThat(result.reservationId()).isEqualTo(8251L);
        assertThat(result.cancelledAt()).isEqualTo(ReservationProcessTests.NOW);
    }

    @Test
    void anUnconfirmedProcessCannotReachTheCancellationTransport() {
        var client = gateway(Duration.ofSeconds(2));
        var unconfirmed = ReservationProcessTests.confirming();
        var failure = assertThrows(ReservationGatewayException.class,
                () -> client.cancel(unconfirmed));
        assertThat(failure.failure()).isEqualTo(ReservationFailure.CANCELLATION_NOT_ALLOWED);
        assertThat(failure.attempted()).isFalse();
        assertThat(providers.cancellations).hasValue(0);
        verify(access, never()).getAccess();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "",
            "{\"reservationProcessId\":\"process-fictional\",\"reservationId\":8251,\"status\":\"CONFIRMED\",\"cancelledAt\":\"2026-11-02T11:00:00Z\"}",
            "{\"reservationProcessId\":\"other\",\"reservationId\":8251,\"status\":\"CANCELLED\",\"cancelledAt\":\"2026-11-02T11:00:00Z\"}",
            "{\"reservationProcessId\":\"process-fictional\",\"reservationId\":1,\"status\":\"CANCELLED\",\"cancelledAt\":\"2026-11-02T11:00:00Z\"}",
            "{\"reservationProcessId\":\"process-fictional\",\"status\":\"CANCELLED\",\"cancelledAt\":\"2026-11-02T11:00:00Z\"}",
            "{\"reservationProcessId\":\"process-fictional\",\"reservationId\":8251,\"status\":\"CANCELLED\"}",
            "{\"reservationProcessId\":\"process-fictional\",\"reservationId\":8251,\"status\":\"CANCELLED\",\"cancelledAt\":\"invalid\"}"
    })
    void insufficientOrContradictorySuccessRemainsUncertain(String body) {
        providers.cancellationOverride.set(exchange -> TestIntegrationSupport.respond(exchange, 200, body));
        var client = gateway(Duration.ofSeconds(2));
        var failure = assertThrows(ReservationGatewayException.class,
                () -> client.cancel(process));
        assertThat(failure.failure()).isEqualTo(ReservationFailure.CANCELLATION_UNCERTAIN);
        assertThat(failure.definitive()).isFalse();
        assertThat(providers.cancellations).hasValue(1);
    }

    @ParameterizedTest
    @CsvSource({
            "400,VALIDATION_ERROR,true,CANCELLATION_REJECTED",
            "403,RESERVATION_OWNER_MISMATCH,true,CANCELLATION_OWNER_MISMATCH",
            "403,STUDENT_INTEGRATION_NOT_FOUND,true,CANCELLATION_REJECTED",
            "404,RESERVATION_NOT_FOUND,true,CANCELLATION_NOT_FOUND",
            "404,UNKNOWN,true,CANCELLATION_REJECTED",
            "409,RESERVATION_NOT_CANCELLABLE,true,CANCELLATION_REJECTED",
            "401,UNKNOWN,false,CANCELLATION_UNCERTAIN",
            "503,UNKNOWN,false,CANCELLATION_UNCERTAIN",
            "500,UNKNOWN,false,CANCELLATION_UNCERTAIN",
            "302,UNKNOWN,false,CANCELLATION_UNCERTAIN"
    })
    void usesStatusAndCodeWithoutReadingTheDetailOrRepeatingThePost(int status,
            String code, boolean definitive, ReservationFailure expected) {
        providers.cancellationOverride.set(exchange -> TestIntegrationSupport.respond(exchange, status,
                "{\"code\":\"" + code + "\",\"detail\":\"CANCELLED successfully\"}"));
        var adapter = gateway(Duration.ofSeconds(2));
        var failure = assertThrows(ReservationGatewayException.class, () -> adapter.cancel(process));
        assertThat(failure.failure()).isEqualTo(expected);
        assertThat(failure.definitive()).isEqualTo(definitive);
        assertThat(providers.cancellations).hasValue(1);
        verify(access, times(1)).getAccess();
        if (status == 401) verify(access).rejectAccess(providers.token);
        else verify(access, never()).rejectAccess(any());
    }

    @Test
    void securityErrorWithoutCodeStillInvalidatesOnlyTheUsedTechnicalToken() {
        providers.cancellationOverride.set(exchange -> TestIntegrationSupport.respond(exchange, 401, ""));
        var adapter = gateway(Duration.ofSeconds(2));
        assertThrows(ReservationGatewayException.class, () -> adapter.cancel(process));
        verify(access).rejectAccess(providers.token);
        assertThat(providers.cancellations).hasValue(1);
    }

    @Test
    void missingTechnicalAccessDoesNotCallTheProvider() {
        when(access.getAccess()).thenThrow(new IllegalStateException("Unavailable"));
        var adapter = gateway(Duration.ofSeconds(2));
        assertThrows(ReservationGatewayException.class, () -> adapter.cancel(process));
        assertThat(providers.cancellations).hasValue(0);
    }

    @Test
    void stalledResponseBodyHasABoundedWaitAndOnePost() {
        var release = new CountDownLatch(1);
        providers.cancellationOverride.set(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Fictional response gate timed out");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException(failure);
            } finally { exchange.close(); }
        });
        var adapter = gateway(Duration.ofMillis(150));
        long started = System.nanoTime();
        try {
            assertThrows(ReservationGatewayException.class, () -> adapter.cancel(process));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
            assertThat(providers.cancellations).hasValue(1);
        } finally { release.countDown(); }
    }
}

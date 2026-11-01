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

    private String rows(int count, int offset) {
        return java.util.stream.IntStream.range(offset, offset + count)
                .mapToObj(i -> {
                    String value =
                            appointment("user-42", "09:00", "CONFIRMED")
                                    .replace("process-fictional", "process-" + i);
                    return value.substring(1, value.length() - 1);
                })
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    @Test
    void ownListingTraversesPagesWithoutDatesOrStatusAndKeepsItsPatient() {
        provider.appointmentsOverride.set(exchange -> {
            int page = Integer.parseInt(provider.appointmentFilters.get().get("page"));
            exchange.getResponseHeaders().set("X-Total-Count", "205");
            if (page < 2)
                exchange.getResponseHeaders()
                        .set("Link", "<http://untrusted.test/arbitrary>; rel=\"next\"");
            TestIntegrationSupport.respond(exchange, 200, rows(page < 2 ? 100 : 5, page * 100));
        });
        var batch = gateway.listOwn(42);
        assertThat(batch.status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus.VERIFIED);
        assertThat(batch.appointments()).hasSize(205);
        assertThat(provider.appointmentQueries).hasValue(3);
        assertThat(provider.appointmentFilters.get())
                .containsEntry("externalPatientId", "user-42")
                .containsEntry("page", "2")
                .containsEntry("size", "100")
                .doesNotContainKeys("status", "from", "to");
    }

    @Test
    void listingStopsAtThreePagesAndDoesNotPresentThemAsACompleteScan() {
        provider.appointmentsOverride.set(exchange -> {
            int page = Integer.parseInt(provider.appointmentFilters.get().get("page"));
            exchange.getResponseHeaders().set("X-Total-Count", "400");
            exchange.getResponseHeaders()
                    .set("Link", "<http://untrusted.test/never-follow>; rel=\"next\"");
            TestIntegrationSupport.respond(exchange, 200, rows(100, page * 100));
        });
        var batch = gateway.listOwn(42);
        assertThat(batch.status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                .INCONCLUSIVE);
        assertThat(batch.appointments()).hasSize(300);
        assertThat(provider.appointmentQueries).hasValue(3);
    }

    @Test
    void aLaterFailureKeepsEarlierEvidenceWithoutRetry() {
        provider.appointmentsOverride.set(exchange -> {
            if ("1".equals(provider.appointmentFilters.get().get("page"))) {
                TestIntegrationSupport.respond(exchange, 503, "{}");
                return;
            }
            exchange.getResponseHeaders().set("X-Total-Count", "150");
            exchange.getResponseHeaders().set("Link", "</next>; rel=\"next\"");
            TestIntegrationSupport.respond(exchange, 200, rows(100, 0));
        });
        var batch = gateway.listOwn(42);
        assertThat(batch.status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                .UNAVAILABLE);
        assertThat(batch.appointments()).hasSize(100);
        assertThat(provider.appointmentQueries).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "-1", "bad", "0", "2"})
    void contradictoryOrMissingTotalsPreserveEvidenceWithAnInconclusiveResult(String total) {
        provider.appointmentsOverride.set(exchange -> {
            if (!"missing".equals(total)) exchange.getResponseHeaders().set("X-Total-Count", total);
            TestIntegrationSupport.respond(exchange, 200, rows(1, 0));
        });
        var batch = gateway.listOwn(42);
        assertThat(batch.status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                .INCONCLUSIVE);
        assertThat(batch.appointments()).hasSize(1);
        assertThat(provider.appointmentQueries).hasValue(1);
    }

    @Test
    void conflictingDuplicatesAreRemovedAndCannotBeReintroduced() {
        provider.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "3");
            String own = appointment("user-42", "09:00", "CONFIRMED");
            String row = own.substring(1, own.length() - 1);
            TestIntegrationSupport.respond(
                    exchange,
                    200,
                    "[" + row + "," + row.replace("CONFIRMED", "CANCELLED") + "," + row + "]");
        });
        var batch = gateway.listOwn(42);
        assertThat(batch.appointments()).isEmpty();
        assertThat(batch.status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                .INCONCLUSIVE);
    }

    @Test
    void foreignAndMalformedRecordsDoNotBecomeOwnedEvidence() {
        provider.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "3");
            String foreign = appointment("user-43", "09:00", "CONFIRMED");
            TestIntegrationSupport.respond(
                    exchange,
                    200,
                    "[null,"
                            + foreign.substring(1, foreign.length() - 1)
                            + ",{\"externalPatientId\":\"user-42\"}]");
        });
        var batch = gateway.listOwn(42);
        assertThat(batch.appointments()).isEmpty();
        assertThat(batch.status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                .INCONCLUSIVE);
    }

    @Test
    void aKnownNameIsReadButIncompleteDescriptionsDoNotInvalidateTheState() {
        provider.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "1");
            String response =
                    appointment("user-42", "09:00:00", "CONFIRMED")
                            .replace(
                                    "\"futureField\":true",
                                    "\"professionalFirstName\":\"Ana\",\"professionalLastName\":\"Gomez\"");
            TestIntegrationSupport.respond(exchange, 200, response);
        });
        assertThat(gateway.listOwn(42).appointments().getFirst().professionalName().firstName())
                .isEqualTo("Ana");
        provider.appointmentsOverride.set(exchange -> {
            exchange.getResponseHeaders().set("X-Total-Count", "1");
            TestIntegrationSupport.respond(
                    exchange, 200, appointment("user-42", "09:00", "CONFIRMED"));
        });
        assertThat(gateway.listOwn(42).appointments().getFirst().professionalName()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void listingErrorsRemainSingleAttemptsAndInvalidateOnlyUnauthorizedAccess(int status) {
        provider.appointmentsOverride.set(
                exchange -> TestIntegrationSupport.respond(exchange, status, "{}"));
        assertThat(gateway.listOwn(42).status())
                .isEqualTo(
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                .UNAVAILABLE);
        assertThat(provider.appointmentQueries).hasValue(1);
        if (status == 401) verify(access).rejectAccess(provider.token);
        else verify(access, never()).rejectAccess(any());
    }

    @Test
    void theDeadlineIsSharedAcrossPagesAndIncludesAStalledLaterBody() {
        var release = new CountDownLatch(1);
        try (var responses = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
            provider.appointmentsOverride.set(exchange -> {
                int page = Integer.parseInt(provider.appointmentFilters.get().get("page"));
                exchange.getResponseHeaders().set("X-Total-Count", "300");
                if (page < 2) {
                    exchange.getResponseHeaders().set("Link", "</next>; rel=\"next\"");
                    responses.schedule(
                            () -> {
                                try {
                                    TestIntegrationSupport.respond(
                                            exchange, 200, rows(100, page * 100));
                                } catch (java.io.IOException failure) {
                                    throw new java.io.UncheckedIOException(failure);
                                }
                            },
                            600,
                            TimeUnit.MILLISECONDS);
                } else {
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
                }
            });
            var transport =
                    HttpClient.create()
                            .disableRetry(true)
                            .followRedirect(false)
                            .responseTimeout(Duration.ofSeconds(10));
            var client =
                    WebClient.builder()
                            .baseUrl(provider.settings().get("CATEDRA_BASE_URL"))
                            .clientConnector(new ReactorClientHttpConnector(transport))
                            .build();
            var bounded =
                    new WebClientCentralAppointmentAdapter(client, access, Duration.ofSeconds(1));
            long started = System.nanoTime();
            try {
                var result = bounded.listOwn(42);
                assertThat(result.status())
                        .isEqualTo(
                                com.prog2.appointment.reservation.domain.enums.VerificationStatus
                                        .UNAVAILABLE);
                assertThat(result.appointments()).hasSize(200);
                assertThat(provider.appointmentQueries).hasValue(3);
                assertThat(Duration.ofNanos(System.nanoTime() - started))
                        .isLessThan(Duration.ofMillis(2800));
            } finally {
                release.countDown();
            }
        }
    }
}

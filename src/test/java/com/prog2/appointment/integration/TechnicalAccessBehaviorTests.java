package com.prog2.appointment.integration;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.*;
import com.prog2.appointment.integration.TestIntegrationSupport.TestClock;

/** Adquisición inicial, vigencia y readquisición bajo demanda con reloj controlado. */
@ExtendWith(OutputCaptureExtension.class)
class TechnicalAccessBehaviorTests {
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static IntegrationConfiguration configuration() {
        return new IntegrationConfiguration("appointment-test", "fictional:9092", "consumer", "actions", "phone");
    }

    @Test
    void startupAcquiresOnceAndReadersReuseTheSameAccess() {
        var fetched = new AtomicInteger();
        var configured = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> {
            fetched.incrementAndGet(); return new TechnicalToken("fictional", NOW.plusSeconds(60));
        }, _ -> { configured.incrementAndGet(); return configuration(); }, CLOCK, "appointment-test", Runnable::run);
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        service.initialize();
        var first = service.getAccess();
        service.rejectAccess("fictional-other-token");
        assertThat(service.getAccess()).isSameAs(first);
        assertThat(fetched).hasValue(1);
        assertThat(configured).hasValue(1);
    }

    @Test
    void prolongedCatalogOutageCanRecoverOnDemandAfterExhaustingStartup() {
        var calls = new AtomicInteger();
        var unavailable = new AtomicBoolean(true);
        var clock = new TestClock(NOW);
        var service = new TechnicalAccessUseCaseImpl(() -> {
            calls.incrementAndGet();
            if (unavailable.get()) throw new IntegrationGatewayException(AccessFailure.TEMPORARY_UNAVAILABLE);
            return new TechnicalToken("fictional", null);
        }, _ -> configuration(), clock, "appointment-test", Runnable::run);
        service.initialize();
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class).hasNoCause();
        assertThat(calls).hasValue(3);
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofMinutes(5));
            assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
            assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
            assertThat(calls).hasValue(4 + i);
        }
        unavailable.set(false);
        clock.advance(Duration.ofHours(1));
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(service.getAccess().configuration()).isEqualTo(configuration());
        assertThat(calls).hasValue(8);
    }

    @Test
    void aLaterStartupAttemptCanAcquireWithoutAnExecutor() {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> {
            if (calls.incrementAndGet() == 1)
                throw new IntegrationGatewayException(AccessFailure.TEMPORARY_UNAVAILABLE);
            return new TechnicalToken("fictional", null);
        }, _ -> configuration(), CLOCK, "appointment-test", Runnable::run);
        service.initialize();
        assertThat(service.getAccess().configuration()).isEqualTo(configuration());
        assertThat(calls).hasValue(2);
    }

    @Test
    void pendingProvisioningIsNotPublishedAndTheNextStartupAttemptMaySucceed() {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", null), _ -> {
            if (calls.incrementAndGet() == 1)
                throw new IntegrationGatewayException(AccessFailure.PROVISIONING_PENDING);
            return configuration();
        }, CLOCK, "appointment-test", Runnable::run);
        service.initialize();
        assertThat(service.getAccess().configuration()).isEqualTo(configuration());
        assertThat(calls).hasValue(2);
    }

    @ParameterizedTest
    @EnumSource(value = AccessFailure.class, names = {"CATALOG_AUTHENTICATION", "ACCESS_DENIED", "INTEGRATION_NOT_FOUND",
            "INVALID_RESPONSE", "INTEGRATION_NOT_READY"})
    void terminalFailuresDoNotRetryOrPublishPartialAccess(AccessFailure failure) {
        var clock = new TestClock(NOW);
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> {
            calls.incrementAndGet(); return new TechnicalToken("fictional", null);
        }, _ -> { throw new IntegrationGatewayException(failure); }, clock, "appointment-test", Runnable::run);
        service.initialize();
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofHours(1));
            assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        }
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-group", "incomplete"})
    void wrongGroupOrIncompleteConfigurationNeverBecomesAvailable(String scenario) {
        var config = scenario.equals("incomplete")
                ? new IntegrationConfiguration("appointment-test", null, "consumer", "actions", "phone") : configuration();
        var service = new TechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", null), _ -> config,
                CLOCK, scenario.equals("other-group") ? "different" : "appointment-test", Runnable::run);
        service.initialize();
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
    }

    @Test
    void expiredCandidateIsRejectedBeforeCallingCentral() {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", NOW), _ -> {
            calls.incrementAndGet(); return configuration();
        }, CLOCK, "appointment-test", Runnable::run);
        service.initialize();
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void expiredSessionIsReadquiredAndItsLate401DoesNotInvalidateTheNewToken(CapturedOutput output) {
        var clock = new TestClock(NOW);
        var calls = new AtomicInteger();
        String secret = "fictional-sensitive-token";
        var service = new TechnicalAccessUseCaseImpl(() -> calls.incrementAndGet() == 1
                ? new TechnicalToken(secret, NOW.plusSeconds(60)) : new TechnicalToken("replacement", null),
                _ -> configuration(), clock, "appointment-test", Runnable::run);
        service.initialize();
        assertThat(service.getAccess().toString()).doesNotContain(secret);
        clock.advance(Duration.ofSeconds(60));
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(service.getAccess().token().value()).isEqualTo("replacement");
        service.rejectAccess(secret);
        assertThat(service.getAccess().token().value()).isEqualTo("replacement");
        assertThat(calls).hasValue(2);
        assertThat(output).doesNotContain(secret);
    }

    @Test
    void aCandidateRejectedByCentralIsNotSentBackUntilCatalogOffersAnotherToken() {
        var clock = new TestClock(NOW);
        var token = new AtomicReference<>(new TechnicalToken("rejected", null));
        var configurationCalls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(token::get, candidate -> {
            configurationCalls.incrementAndGet();
            if (candidate.value().equals("rejected")) {
                throw new IntegrationGatewayException(AccessFailure.CENTRAL_TOKEN_REJECTED);
            }
            return configuration();
        }, clock, "appointment-test", Runnable::run);
        service.initialize();
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(configurationCalls).hasValue(1);
        token.set(new TechnicalToken("replacement", null));
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(service.getAccess().token()).isEqualTo(token.get());
        assertThat(configurationCalls).hasValue(2);
    }

    @Test
    void concurrentReadersDoNotWaitForValidationOrDuplicateAcquisition() throws Exception {
        var clock = new TestClock(NOW);
        var catalogCalls = new AtomicInteger();
        var validating = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor();
                var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = new TechnicalAccessUseCaseImpl(() -> {
                if (catalogCalls.incrementAndGet() <= 3) {
                    throw new IntegrationGatewayException(AccessFailure.TEMPORARY_UNAVAILABLE);
                }
                return new TechnicalToken("replacement", null);
            }, _ -> {
                validating.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                return configuration();
            }, clock, "appointment-test", worker);
            service.initialize();
            clock.advance(Duration.ofSeconds(5));
            try {
                var attempts = java.util.stream.IntStream.range(0, 12)
                        .mapToObj(_ -> readers.submit(() -> assertThatThrownBy(service::getAccess)
                                .isInstanceOf(TechnicalAccessUnavailableException.class)))
                        .toList();
                assertThat(validating.await(2, TimeUnit.SECONDS)).isTrue();
                for (var attempt : attempts) attempt.get(2, TimeUnit.SECONDS);
                assertThat(catalogCalls).hasValue(4);
            } finally {
                release.countDown();
            }
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertThat(service.getAccess().configuration()).isEqualTo(configuration());
            assertThat(catalogCalls).hasValue(4);
        }
    }

}

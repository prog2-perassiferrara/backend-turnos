package com.prog2.appointment.integration;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
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

/** Adquisición de arranque, memoria, fallos y vigencia; no hay ciclos de renovación. */
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
        }, _ -> { configured.incrementAndGet(); return configuration(); }, CLOCK, "appointment-test");
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        service.initialize();
        var first = service.getAccess();
        service.rejectAccess("fictional-other-token");
        assertThat(service.getAccess()).isSameAs(first);
        assertThat(fetched).hasValue(1);
        assertThat(configured).hasValue(1);
    }

    @Test
    void transientStartupFailureHasThreeAttemptsAndReadsNeverStartAnotherCycle() {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> {
            calls.incrementAndGet(); throw new IntegrationGatewayException(AccessFailure.TEMPORARY_UNAVAILABLE);
        }, _ -> configuration(), CLOCK, "appointment-test");
        service.initialize();
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class).hasNoCause();
        assertThat(calls).hasValue(3);
    }

    @Test
    void aLaterStartupAttemptCanAcquireWithoutAnExecutor() {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> {
            if (calls.incrementAndGet() == 1)
                throw new IntegrationGatewayException(AccessFailure.TEMPORARY_UNAVAILABLE);
            return new TechnicalToken("fictional", null);
        }, _ -> configuration(), CLOCK, "appointment-test");
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
        }, CLOCK, "appointment-test");
        service.initialize();
        assertThat(service.getAccess().configuration()).isEqualTo(configuration());
        assertThat(calls).hasValue(2);
    }

    @ParameterizedTest
    @EnumSource(value = AccessFailure.class, names = {"CATALOG_AUTHENTICATION", "ACCESS_DENIED", "INTEGRATION_NOT_FOUND",
            "CENTRAL_TOKEN_REJECTED", "INVALID_RESPONSE", "INTEGRATION_NOT_READY"})
    void terminalFailuresDoNotRetryOrPublishPartialAccess(AccessFailure failure) {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", null), _ -> {
            calls.incrementAndGet(); throw new IntegrationGatewayException(failure);
        }, CLOCK, "appointment-test");
        service.initialize();
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-group", "incomplete"})
    void wrongGroupOrIncompleteConfigurationNeverBecomesAvailable(String scenario) {
        var config = scenario.equals("incomplete")
                ? new IntegrationConfiguration("appointment-test", null, "consumer", "actions", "phone") : configuration();
        var service = new TechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", null), _ -> config,
                CLOCK, scenario.equals("other-group") ? "different" : "appointment-test");
        service.initialize();
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
    }

    @Test
    void expiredCandidateIsRejectedBeforeCallingCentral() {
        var calls = new AtomicInteger();
        var service = new TechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", NOW), _ -> {
            calls.incrementAndGet(); return configuration();
        }, CLOCK, "appointment-test");
        service.initialize();
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void expiryAndCentral401DisableAccessWithoutRenewingIt(CapturedOutput output) {
        var now = new AtomicReference<>(NOW);
        Clock clock = new Clock() {
            @Override public Instant instant() { return now.get(); }
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        };
        var calls = new AtomicInteger();
        String secret = "fictional-sensitive-token";
        var service = new TechnicalAccessUseCaseImpl(() -> {
            calls.incrementAndGet(); return new TechnicalToken(secret, NOW.plusSeconds(60));
        }, _ -> configuration(), clock, "appointment-test");
        service.initialize();
        assertThat(service.getAccess().toString()).doesNotContain(secret);
        now.set(NOW.plusSeconds(60));
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        now.set(NOW);
        service.rejectAccess(secret);
        assertThatThrownBy(service::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class).hasNoCause();
        assertThat(calls).hasValue(1);
        assertThat(output).doesNotContain(secret);
    }
}

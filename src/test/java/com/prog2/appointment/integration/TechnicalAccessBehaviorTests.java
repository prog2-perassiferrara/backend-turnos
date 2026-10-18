package com.prog2.appointment.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.application.usecases.AcquireTechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.enums.AcquisitionOutcome;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.IntegrationConfiguration;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.infrastructure.persistence.adapter.InMemoryTechnicalAccessAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas reales de adquisición y memoria: publicación completa, vencimiento, rechazo y aislamiento de sesiones. */
class TechnicalAccessBehaviorTests {
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    static IntegrationConfiguration configuration(ProvisioningStatus status) {
        return new IntegrationConfiguration("appointment-test", status, "fictional:9092", "consumer", "actions", "phone");
    }

    static TechnicalAccess access(String value, Instant expires) {
        return new TechnicalAccess(new TechnicalToken(value, expires), configuration(ProvisioningStatus.PROVISIONED));
    }

    @Test
    void publishesOnlyCompleteProvisionedAccessAndReusesPendingCandidate() {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var fetched = new AtomicInteger();
        var state = new AtomicReference<>(ProvisioningStatus.PENDING);
        var token = new TechnicalToken("fictional-central", NOW.plusSeconds(60));
        var acquire = new AcquireTechnicalAccessUseCaseImpl(() -> {
            fetched.incrementAndGet();
            return token;
        }, _ -> configuration(state.get()), repository, CLOCK, "appointment-test");

        var pending = acquire.attemptAcquisition(null);
        assertThat(pending.outcome()).isEqualTo(AcquisitionOutcome.RETRYABLE);
        assertThat(repository.current()).isEmpty();
        state.set(ProvisioningStatus.PROVISIONED);
        assertThat(acquire.attemptAcquisition(pending.candidate()).outcome()).isEqualTo(AcquisitionOutcome.AVAILABLE);
        assertThat(repository.current()).isPresent();
        assertThat(fetched).hasValue(1);
    }

    @ParameterizedTest
    @EnumSource(value = ProvisioningStatus.class, names = {"FAILED", "REVOKED"})
    void doesNotPublishTerminalProvisioningStates(ProvisioningStatus status) {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var acquire = new AcquireTechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", null),
                _ -> configuration(status), repository, CLOCK, "appointment-test");
        assertThat(acquire.attemptAcquisition(null).outcome()).isEqualTo(AcquisitionOutcome.TERMINAL);
        assertThat(repository.current()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-group", "incomplete"})
    void rejectsOtherIntegrationOrIncompleteConfiguration(String scenario) {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var config = "incomplete".equals(scenario)
                ? new IntegrationConfiguration("appointment-test", ProvisioningStatus.PROVISIONED, null, "consumer", "actions", "phone")
                : configuration(ProvisioningStatus.PROVISIONED);
        var acquire = new AcquireTechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional", null),
                _ -> config, repository, CLOCK, scenario.equals("other-group") ? scenario : "appointment-test");
        assertThat(acquire.attemptAcquisition(null).failure()).isEqualTo(AccessFailure.INVALID_RESPONSE);
        assertThat(repository.current()).isEmpty();
    }

    @Test
    void servesValidAccessWithoutRequestingRecoveryAndInvalidatesAtExactExpiry() {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var value = access("fictional", NOW.plusSeconds(60));
        assertThat(repository.publishIfAccepted(value)).isTrue();
        var recoveryCalls = new AtomicInteger();
        var before = new TechnicalAccessUseCaseImpl(repository, recoveryCalls::incrementAndGet,
                Clock.offset(CLOCK, java.time.Duration.ofSeconds(59)));
        assertThat(before.getAccess()).isSameAs(value);
        assertThat(recoveryCalls).hasValue(0);
        var atExpiry = new TechnicalAccessUseCaseImpl(repository, recoveryCalls::incrementAndGet,
                Clock.offset(CLOCK, java.time.Duration.ofSeconds(60)));
        assertThatThrownBy(atExpiry::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
        assertThat(repository.current()).isEmpty();
        assertThat(recoveryCalls).hasValue(1);
        assertThat(repository.publishIfAccepted(value)).isFalse();
    }

    @Test
    void acceptsTokenWithoutExpiryButCentralRejectionAlwaysInvalidatesIt() {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var acquire = new AcquireTechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional-no-exp", null),
                _ -> configuration(ProvisioningStatus.PROVISIONED), repository, CLOCK, "appointment-test");
        assertThat(acquire.attemptAcquisition(null).outcome()).isEqualTo(AcquisitionOutcome.AVAILABLE);
        var access = new TechnicalAccessUseCaseImpl(repository, () -> {}, CLOCK);
        access.rejectAccess("fictional-no-exp");
        assertThat(acquire.attemptAcquisition(null).failure()).isEqualTo(AccessFailure.CENTRAL_TOKEN_REJECTED);
        assertThatThrownBy(access::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
    }

    @Test
    void central401MarksCandidateRejectedEvenBeforeFirstPublication() {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var calls = new AtomicInteger();
        var acquire = new AcquireTechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional-rejected", null), _ -> {
            calls.incrementAndGet();
            throw new IntegrationGatewayException(AccessFailure.CENTRAL_TOKEN_REJECTED);
        }, repository, CLOCK, "appointment-test");
        assertThat(acquire.attemptAcquisition(null).outcome()).isEqualTo(AcquisitionOutcome.TERMINAL);
        assertThat(acquire.attemptAcquisition(null).failure()).isEqualTo(AccessFailure.CENTRAL_TOKEN_REJECTED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void lateRejectionDoesNotDeleteNewerSessionAndRejectedTokensCannotReturn() {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var old = access("fictional-old", null);
        var newer = access("fictional-new", null);
        repository.publishIfAccepted(old);
        repository.publishIfAccepted(newer);
        repository.rejectToken(old.token().value());
        assertThat(repository.current()).contains(newer);
        repository.rejectToken(newer.token().value());
        repository.rejectToken(old.token().value());
        assertThat(repository.publishIfAccepted(newer)).isFalse();
        assertThat(repository.publishIfAccepted(old)).isFalse();
    }

    @Test
    void rejectsExpiredCandidateAndPreventsPublicationAfterClose() {
        var repository = new InMemoryTechnicalAccessAdapter(CLOCK);
        var centralCalls = new AtomicInteger();
        var acquire = new AcquireTechnicalAccessUseCaseImpl(() -> new TechnicalToken("fictional-expired", NOW), _ -> {
            centralCalls.incrementAndGet();
            return configuration(ProvisioningStatus.PROVISIONED);
        }, repository, CLOCK, "appointment-test");
        assertThat(acquire.attemptAcquisition(null).failure()).isEqualTo(AccessFailure.CENTRAL_TOKEN_REJECTED);
        assertThat(centralCalls).hasValue(0);
        repository.close();
        assertThat(repository.publishIfAccepted(access("fictional", null))).isFalse();
        assertThat(repository.current()).isEmpty();
    }
}

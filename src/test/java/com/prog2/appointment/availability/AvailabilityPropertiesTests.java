package com.prog2.appointment.availability;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.availability.infrastructure.config.AvailabilityConfiguration;
import com.prog2.appointment.infrastructure.config.AvailabilityProperties;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Defaults, sobrescritura y fallo de arranque de los parámetros nuevos, sin claves o proveedores reales. */
class AvailabilityPropertiesTests {
    @Test
    void keepsExplicitDefaultsAndAllowsOverrides() {
        var defaults = AvailabilityProperties.from(new MockEnvironment());
        assertThat(defaults.timeZone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(defaults.httpTimeout()).isEqualTo(Duration.ofSeconds(5));
        var override = AvailabilityProperties.from(new MockEnvironment()
                .withProperty("app.availability.time-zone", "UTC")
                .withProperty("app.availability.http-timeout", "250ms"));
        assertThat(override.timeZone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(override.httpTimeout()).isEqualTo(Duration.ofMillis(250));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fictional-sensitive-zone", "+99:00"})
    void rejectsInvalidZoneWithoutExposingIt(String value) {
        var environment = new MockEnvironment().withProperty("app.availability.time-zone", value);
        assertThatThrownBy(() -> AvailabilityProperties.from(environment))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessage("Invalid availability setting: app.availability.time-zone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0s", "-1s", "0.1ms", "3000000000ms", "9223372036854775807d", "invalid"})
    void rejectsNonPositiveUnrepresentableOrInvalidTimeout(String value) {
        var environment = new MockEnvironment().withProperty("app.availability.http-timeout", value);
        assertThatThrownBy(() -> AvailabilityProperties.from(environment))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessage("Invalid availability setting: app.availability.http-timeout");
    }

    @Test
    void createsTheUseCaseAndFailsTheContextForInvalidConfiguration() {
        var clock = Clock.fixed(AvailabilityTestSupport.NOW, ZoneOffset.UTC);
        var access = new TechnicalAccessProperties(URI.create("http://catalog.test"), URI.create("http://central.test"),
                TestIntegrationSupport.GROUP, (java.security.interfaces.RSAPrivateCrtKey) TestIntegrationSupport.KEYS.getPrivate());
        var runner = new ApplicationContextRunner().withUserConfiguration(AvailabilityConfiguration.class)
                .withBean(Clock.class, () -> clock)
                .withBean(TechnicalAccessProperties.class, () -> access)
                .withBean(ServiceTokenIssuer.class, () -> new ServiceTokenIssuer(access.privateKey(), clock))
                .withBean(TechnicalAccessUseCase.class, () -> mock(TechnicalAccessUseCase.class));
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(GetAvailabilityUseCase.class));
        runner.withPropertyValues("app.availability.time-zone=fictional-sensitive-zone")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("app.availability.time-zone");
                });
    }
}

package com.prog2.appointment.security;

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import com.prog2.appointment.infrastructure.security.config.JwtSecurityProperties;
import com.prog2.appointment.infrastructure.security.config.SecurityConfiguration;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Comprueba confianza y configuración externas sin archivos de claves ni conexiones de red. */
class JwtSecurityPropertiesTests {
    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfiguration.class)
            .withBean(TechnicalAccessProperties.class, JwtTestTokens::access)
            .withBean(Clock.class, () -> Clock.fixed(JwtTestTokens.NOW, ZoneOffset.UTC));

    @Test
    void keepsDecoderAvailableWithoutCreatingServletFiltersInNonWebContexts() {
        application.run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
                .doesNotHaveBean(org.springframework.security.web.SecurityFilterChain.class));
    }

    @Test
    void invalidCorsPreventsContextStartupWithoutExposingTheOrigin() {
        application.withPropertyValues("CORS_ALLOWED_ORIGINS=https://fictional-sensitive.test/path")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("CORS_ALLOWED_ORIGINS");
                    assertThat(context.getStartupFailure().getMessage()).doesNotContain("fictional-sensitive.test");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void derivesMatchingPublicKeyAndClosesCorsByDefault(String origins) {
        var environment = new MockEnvironment();
        if (origins != null) {
            environment.setProperty("CORS_ALLOWED_ORIGINS", origins);
        }
        var properties = JwtSecurityProperties.from(JwtTestTokens.access(), environment);
        assertThat(properties.publicKey().getEncoded()).isEqualTo(JwtTestTokens.KEYS.getPublic().getEncoded());
        assertThat(properties.allowedOrigins()).isEmpty();
    }

    @Test
    void acceptsExplicitOriginsAndKeepsTheirCollectionImmutable() {
        var environment = new MockEnvironment().withProperty("CORS_ALLOWED_ORIGINS",
                "https://app.test, http://localhost:8082");
        var properties = JwtSecurityProperties.from(JwtTestTokens.access(), environment);
        var allowedOrigins = properties.allowedOrigins();
        assertThat(allowedOrigins).containsExactly("https://app.test", "http://localhost:8082");
        assertThatThrownBy(() -> allowedOrigins.add("http://other.test"))
                .isInstanceOf(UnsupportedOperationException.class);
        var origins = new ArrayList<>(List.of("https://app.test"));
        var copied = new JwtSecurityProperties((RSAPublicKey) JwtTestTokens.KEYS.getPublic(), origins);
        origins.clear();
        assertThat(copied.allowedOrigins()).containsExactly("https://app.test");
        assertThat(properties.toString()).doesNotContain("app.test", "localhost", "modulus");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.test", "null", "file:///tmp/app", "https://app.test/",
            "https://app.test/path", "https://name:fictional-secret@app.test", "https://app.test?secret=value",
            "https://app.test#part", "https://app.test:0", "https://app.test:65536", "https://app.test:-2",
            "https://app.test,", ",https://app.test", "https://app.test,,https://other.test", "not a URI"})
    void rejectsInvalidOriginWithoutExposingItsValueOrCause(String origin) {
        var environment = new MockEnvironment().withProperty("CORS_ALLOWED_ORIGINS", origin);
        var access = JwtTestTokens.access();
        assertThatThrownBy(() -> JwtSecurityProperties.from(access, environment))
                .isInstanceOf(BeanCreationException.class).hasNoCause()
                .hasMessage("Invalid security setting: CORS_ALLOWED_ORIGINS");
    }
}

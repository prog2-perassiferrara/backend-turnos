package com.prog2.appointment.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.config.TechnicalAccessSettingsConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Valida la configuración real y su arranque Spring sin necesitar MySQL ni leer secretos del usuario. */
class TechnicalAccessPropertiesTests {
    private static final TestIntegrationSupport PROVIDERS = new TestIntegrationSupport();

    static MockEnvironment environment() {
        var environment = new MockEnvironment();
        PROVIDERS.settings().forEach(environment::setProperty);
        return environment;
    }

    @AfterAll
    static void closeProviders() {
        PROVIDERS.close();
    }

    @Test
    void startsWithExternalPrivateKeyAndRedactsItsRepresentation() {
        new ApplicationContextRunner().withUserConfiguration(TechnicalAccessSettingsConfiguration.class)
                .withPropertyValues(PROVIDERS.settings().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(TechnicalAccessProperties.class);
                    assertThat(properties.expectedGroup()).isEqualTo(TestIntegrationSupport.GROUP);
                    assertThat(properties).hasToString("TechnicalAccessProperties[REDACTED]");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"CATALOG_BASE_URL", "CATEDRA_BASE_URL", "CATEDRA_GROUP_ID", "JWT_PRIVATE_KEY_PATH"})
    void rejectsMissingOrBlankRequiredSetting(String name) {
        var absent = new MockEnvironment();
        PROVIDERS.settings().forEach((key, value) -> {
            if (!key.equals(name)) {
                absent.setProperty(key, value);
            }
        });
        assertThatThrownBy(() -> TechnicalAccessProperties.from(absent))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name);
        var blank = environment().withProperty(name, " ");
        assertThatThrownBy(() -> TechnicalAccessProperties.from(blank))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-url", "ftp://localhost", "http://fictional-user@localhost", "http://localhost:0", "http://localhost:99999", "http://localhost?query=1", "http://localhost#fragment"})
    void rejectsInvalidUrlsWithoutEchoingTheirValues(String value) {
        var environment = environment().withProperty("CATALOG_BASE_URL", value);
        assertThatThrownBy(() -> TechnicalAccessProperties.from(environment))
                .hasMessage("Invalid CATALOG_BASE_URL").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "a", "wrong group", "-group", "group-"})
    void rejectsUnexpectedGroupFormat(String value) {
        var environment = environment().withProperty("CATEDRA_GROUP_ID", value);
        assertThatThrownBy(() -> TechnicalAccessProperties.from(environment)).hasMessage("Invalid CATEDRA_GROUP_ID");
    }

    @Test
    void rejectsPublicKeyGarbageMissingFileAndWeakPrivateKey() throws IOException {
        Path directory = Path.of("target", "test-keys");
        Path publicFile = Files.createTempFile(directory, "fictional-public-", ".pem");
        Path garbage = Files.createTempFile(directory, "fictional-invalid-", ".pem");
        Path weak = TestIntegrationSupport.writePrivateKey(TestIntegrationSupport.generateKeys(1024));
        try {
            Files.writeString(publicFile, "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(TestIntegrationSupport.KEYS.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----");
            Files.writeString(garbage, "fictional-invalid-key-material");
            for (Path file : java.util.List.of(publicFile, garbage, weak, directory.resolve("missing-private.pem"), directory)) {
                var environment = environment().withProperty("JWT_PRIVATE_KEY_PATH", file.toAbsolutePath().toString());
                assertThatThrownBy(() -> TechnicalAccessProperties.from(environment))
                        .hasMessage("Invalid JWT_PRIVATE_KEY_PATH").hasNoCause();
            }
        } finally {
            Files.deleteIfExists(publicFile);
            Files.deleteIfExists(garbage);
            Files.deleteIfExists(weak);
        }
    }

    @Test
    void invalidPrivateKeyPreventsSpringContextStartup() {
        new ApplicationContextRunner().withUserConfiguration(TechnicalAccessSettingsConfiguration.class)
                .withPropertyValues(PROVIDERS.settings().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                .withPropertyValues("JWT_PRIVATE_KEY_PATH=target/test-keys/nonexistent-private.pem")
                .run(context -> assertThat(context).hasFailed());
    }
}

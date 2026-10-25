package com.prog2.appointment.user;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.infrastructure.security.SpringPasswordHasherAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Criptografía real en fronteras ASCII/Unicode; detecta truncamiento, sal fija y hashes inválidos. */
class PasswordHasherTests {
    private final SpringPasswordHasherAdapter passwords =
            new SpringPasswordHasherAdapter(new Argon2PasswordEncoder(16, 32, 1, 19456, 2));

    static Stream<String> validPasswords() {
        return Stream.of("sixsix", "a".repeat(100), "á".repeat(100), "🙂".repeat(100),
                "🙂".repeat(6), "  exact value  ");
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void verifiesCompleteValueWithDifferentSaltEachTime(String password) {
        String first = passwords.encode(password);
        String second = passwords.encode(password);
        assertThat(first).hasSize(97).startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                .isNotEqualTo(second).doesNotContain(password);
        assertThat(passwords.matches(password, first)).isTrue();
        assertThat(passwords.matches(password, second)).isTrue();
        assertThat(passwords.matches("different-password", first)).isFalse();
    }

    @Test
    void distinguishesLastCharacterAndRejectsSuffixBeyondMaximum() {
        String password = "🙂".repeat(99) + "a";
        String hash = passwords.encode(password);
        assertThat(passwords.matches("🙂".repeat(99) + "b", hash)).isFalse();
        assertThatThrownBy(() -> passwords.matches(password + "c", hash))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
    }

    @Test
    void neverNormalizesOrTrimsPassword() {
        String hash = passwords.encode("  Mixed Case  ");
        assertThat(passwords.matches("Mixed Case", hash)).isFalse();
        assertThat(passwords.matches("  mixed case  ", hash)).isFalse();
    }

    static Stream<String> invalidPasswords() {
        return Stream.of(null, "", "short", "🙂".repeat(5), "a".repeat(101), "á".repeat(101), "🙂".repeat(101));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void rejectsOversizedUnicodeAndTooShortInputs(String password) {
        assertThatThrownBy(() -> passwords.encode(password)).isInstanceOf(UserSecurityException.class).hasNoCause();
        assertThatThrownBy(() -> passwords.matches(password, "unused-hash"))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
    }

    @Test
    void corruptHashIsTechnicalFailureRatherThanWrongPassword() {
        assertThatThrownBy(() -> passwords.matches("valid-password", "corrupt-stored-value"))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
        assertThatThrownBy(() -> passwords.matches("valid-password", null))
                .isInstanceOf(UserSecurityException.class);
        String legacyHash = "$2a$10$" + "a".repeat(53);
        assertThatThrownBy(() -> passwords.matches("valid-password", legacyHash))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
    }

    @Test
    void rejectsDifferentArgonVersionOrProfileBeforeVerification() {
        String hash = passwords.encode("valid-password");
        String wrongVersion = hash.replace("v=19", "v=16");
        String wrongMemory = hash.replace("m=19456", "m=16384");
        assertThatThrownBy(() -> passwords.matches("valid-password", wrongVersion))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
        assertThatThrownBy(() -> passwords.matches("valid-password", wrongMemory))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
    }

    @Test
    void doesNotNormalizeCanonicallyEquivalentUnicodePasswords() {
        String hash = passwords.encode("é".repeat(6));
        assertThat(passwords.matches("e\u0301".repeat(6), hash)).isFalse();
    }
}

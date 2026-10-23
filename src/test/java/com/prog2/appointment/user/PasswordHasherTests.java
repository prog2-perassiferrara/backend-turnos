package com.prog2.appointment.user;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.infrastructure.security.SpringPasswordHasherAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Criptografía real en fronteras ASCII/Unicode; detecta truncamiento, sal fija y hashes inválidos. */
class PasswordHasherTests {
    private final SpringPasswordHasherAdapter passwords =
            new SpringPasswordHasherAdapter(new BCryptPasswordEncoder(10));

    static Stream<String> validPasswords() {
        return Stream.of("sixsix", "a".repeat(72), "á".repeat(36), "🙂".repeat(18), "  exact value  ");
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void verifiesCompleteValueWithDifferentSaltEachTime(String password) {
        String first = passwords.encode(password);
        String second = passwords.encode(password);
        assertThat(first).hasSize(60).startsWith("$2a$10$").isNotEqualTo(second).doesNotContain(password);
        assertThat(passwords.matches(password, first)).isTrue();
        assertThat(passwords.matches(password, second)).isTrue();
        assertThat(passwords.matches("different-password", first)).isFalse();
    }

    @Test
    void distinguishesLastByteAndRejectsSuffixBeyondMaximum() {
        String password = "a".repeat(71) + "b";
        String hash = passwords.encode(password);
        assertThat(passwords.matches("a".repeat(71) + "c", hash)).isFalse();
        assertThatThrownBy(() -> passwords.matches(password + "c", hash))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
    }

    @Test
    void neverNormalizesOrTrimsPassword() {
        String hash = passwords.encode("  Mixed Case  ");
        assertThat(passwords.matches("Mixed Case", hash)).isFalse();
        assertThat(passwords.matches("  mixed case  ", hash)).isFalse();
    }

    @Test
    void rejectsOversizedUnicodeAndTooShortInputs() {
        String oversizedPassword = "á".repeat(37);
        assertThatThrownBy(() -> passwords.encode(oversizedPassword)).isInstanceOf(UserSecurityException.class);
        assertThatThrownBy(() -> passwords.encode("short")).isInstanceOf(UserSecurityException.class);
        assertThatThrownBy(() -> passwords.encode(null)).isInstanceOf(UserSecurityException.class);
    }

    @Test
    void corruptHashIsTechnicalFailureRatherThanWrongPassword() {
        assertThatThrownBy(() -> passwords.matches("valid-password", "corrupt-stored-value"))
                .isInstanceOf(UserSecurityException.class).hasNoCause();
        assertThatThrownBy(() -> passwords.matches("valid-password", null))
                .isInstanceOf(UserSecurityException.class);
        String invalidCostHash = "$2a$99$" + "a".repeat(53);
        assertThatThrownBy(() -> passwords.matches("valid-password", invalidCostHash))
                .isInstanceOf(UserSecurityException.class);
    }
}

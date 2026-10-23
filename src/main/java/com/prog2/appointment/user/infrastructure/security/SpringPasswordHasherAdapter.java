package com.prog2.appointment.user.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.domain.ports.out.PasswordHasher;

/** Adapter de hashing: delega la criptografía a Spring y rechaza verificaciones parciales de BCrypt. */
public final class SpringPasswordHasherAdapter implements PasswordHasher {
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./0-9A-Za-z]{53}");
    private final PasswordEncoder encoder;

    public SpringPasswordHasherAdapter(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String encode(String password) {
        try {
            requireCompletePassword(password);
            return encoder.encode(password);
        } catch (IllegalArgumentException _) {
            throw new UserSecurityException();
        }
    }

    @Override
    public boolean matches(String password, String encodedPassword) {
        try {
            requireCompletePassword(password);
            if (encodedPassword == null || !BCRYPT.matcher(encodedPassword).matches()) {
                throw new UserSecurityException();
            }
            return encoder.matches(password, encodedPassword);
        } catch (IllegalArgumentException _) {
            throw new UserSecurityException();
        }
    }

    private static void requireCompletePassword(String password) {
        // El adaptador también protege invocaciones internas, fuera de Bean Validation HTTP.
        if (password == null || password.length() < 6
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new UserSecurityException();
        }
    }
}

package com.prog2.appointment.user.infrastructure.security;

import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.domain.ports.out.PasswordHasher;

/** Adapter de Argon2id: delega la criptografía a Spring y verifica el perfil persistido de esta entrega. */
public final class SpringPasswordHasherAdapter implements PasswordHasher {
    // Solo el perfil emitido: evita tratar hashes corruptos como contraseña incorrecta o aceptar otro coste.
    private static final Pattern ARGON2ID = Pattern.compile(
            "\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}");
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
            if (encodedPassword == null || !ARGON2ID.matcher(encodedPassword).matches()) {
                throw new UserSecurityException();
            }
            return encoder.matches(password, encodedPassword);
        } catch (IllegalArgumentException _) {
            throw new UserSecurityException();
        }
    }

    private static void requireCompletePassword(String password) {
        // El adaptador también protege invocaciones internas, fuera de Bean Validation HTTP.
        if (password == null) {
            throw new UserSecurityException();
        }
        int length = password.codePointCount(0, password.length());
        if (length < 6 || length > 100) {
            throw new UserSecurityException();
        }
    }
}

package com.prog2.appointment.user.application.usecases;

import java.time.Duration;
import com.prog2.appointment.user.application.exception.InvalidCredentialsException;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.domain.model.UserToken;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.domain.ports.out.PasswordHasher;
import com.prog2.appointment.user.domain.ports.out.UserAccountRepository;
import com.prog2.appointment.user.domain.ports.out.UserTokenIssuer;

/** Verifica cuenta y contraseña antes de emitir una credencial de usuario; no modifica el perfil. */
public final class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {
    private final UserAccountRepository accounts;
    private final PasswordHasher passwords;
    private final UserTokenIssuer tokens;

    public AuthenticateUserUseCaseImpl(UserAccountRepository accounts, PasswordHasher passwords,
            UserTokenIssuer tokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Override
    public UserToken authenticate(String identifier, String password, boolean rememberMe) {
        var account = accounts.findByIdentifier(RegistrationData.normalizeIdentifier(identifier))
                .orElseThrow(InvalidCredentialsException::new);
        if (!account.activated() || !passwords.matches(password, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(account.id(), account.authorities(),
                rememberMe ? Duration.ofDays(30) : Duration.ofHours(24));
    }
}

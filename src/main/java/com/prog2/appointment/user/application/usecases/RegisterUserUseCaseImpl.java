package com.prog2.appointment.user.application.usecases;

import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.domain.ports.in.RegisterUserUseCase;
import com.prog2.appointment.user.domain.ports.out.PasswordHasher;
import com.prog2.appointment.user.domain.ports.out.UserAccountRepository;

/** Coordina hashing y alta; el hashing ocurre antes de abrir la transacción de persistencia. */
public final class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserAccountRepository accounts;
    private final PasswordHasher passwords;

    public RegisterUserUseCaseImpl(UserAccountRepository accounts, PasswordHasher passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Override
    public void register(RegistrationData data, String password) {
        accounts.createActiveUser(data, passwords.encode(password));
    }
}

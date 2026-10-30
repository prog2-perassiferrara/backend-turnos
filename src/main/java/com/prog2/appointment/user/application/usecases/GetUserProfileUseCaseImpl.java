package com.prog2.appointment.user.application.usecases;

import java.util.Optional;
import com.prog2.appointment.user.domain.model.UserProfile;
import com.prog2.appointment.user.domain.ports.in.GetUserProfileUseCase;
import com.prog2.appointment.user.domain.ports.out.UserAccountRepository;

/** Publica una lectura mínima sin exponer persistencia ni ampliar registro/login. */
public final class GetUserProfileUseCaseImpl implements GetUserProfileUseCase {
    private final UserAccountRepository accounts;
    public GetUserProfileUseCaseImpl(UserAccountRepository accounts) { this.accounts = accounts; }
    @Override public Optional<UserProfile> get(long id) {
        if (id <= 0) { throw new IllegalArgumentException("Invalid account identifier"); }
        return accounts.findProfileById(id);
    }
}

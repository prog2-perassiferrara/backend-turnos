package com.prog2.appointment.user.infrastructure.persistence.adapter;

import java.sql.SQLException;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import com.prog2.appointment.user.domain.exception.UserAccountUnavailableException;
import com.prog2.appointment.user.domain.exception.UserIdentifierAlreadyUsedException;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.domain.model.UserAccount;
import com.prog2.appointment.user.domain.model.UserProfile;
import com.prog2.appointment.user.domain.ports.out.UserAccountRepository;
import com.prog2.appointment.user.infrastructure.persistence.service.TransactionalUserAccountStore;

/** Adapter/Repository: traduce errores técnicos después del proxy, sin propagar SQL ni datos privados. */
@Component
public final class JpaUserAccountAdapter implements UserAccountRepository {
    private final TransactionalUserAccountStore store;

    public JpaUserAccountAdapter(TransactionalUserAccountStore store) {
        this.store = store;
    }

    @Override
    public void createActiveUser(RegistrationData data, String passwordHash) {
        try {
            store.create(data, passwordHash);
        } catch (DataAccessException | TransactionException failure) {
            if (isDuplicateIdentifier(failure)) {
                throw new UserIdentifierAlreadyUsedException();
            }
            throw new UserAccountUnavailableException();
        }
    }

    @Override
    public Optional<UserAccount> findByIdentifier(String normalizedIdentifier) {
        try {
            return store.findByIdentifier(normalizedIdentifier);
        } catch (DataAccessException | TransactionException _) {
            throw new UserAccountUnavailableException();
        }
    }

    @Override
    public Optional<UserProfile> findProfileById(long id) {
        try { return store.findProfileById(id); }
        catch (DataAccessException | TransactionException _) { throw new UserAccountUnavailableException(); }
    }

    private static boolean isDuplicateIdentifier(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            // Inspección de tipo en la frontera SQL: el código MySQL evita decidir por texto sensible.
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }
}

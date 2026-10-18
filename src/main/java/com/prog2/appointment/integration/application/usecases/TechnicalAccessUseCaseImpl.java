package com.prog2.appointment.integration.application.usecases;

import java.time.Clock;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.out.RequestTechnicalAccessRecovery;
import com.prog2.appointment.integration.domain.ports.out.TechnicalAccessRepository;

/** Entrega acceso local utilizable y solicita recuperación solo ante ausencia o vencimiento. */
public final class TechnicalAccessUseCaseImpl implements TechnicalAccessUseCase {
    private final TechnicalAccessRepository repository;
    private final RequestTechnicalAccessRecovery recovery;
    private final Clock clock;

    public TechnicalAccessUseCaseImpl(TechnicalAccessRepository repository,
            RequestTechnicalAccessRecovery recovery, Clock clock) {
        this.repository = repository;
        this.recovery = recovery;
        this.clock = clock;
    }

    @Override
    public TechnicalAccess getAccess() {
        var access = repository.current();
        if (access.isPresent() && access.get().token().isUsableAt(clock.instant())) {
            return access.get();
        }
        access.ifPresent(value -> repository.rejectToken(value.token().value()));
        recovery.requestRecovery();
        throw new TechnicalAccessUnavailableException();
    }

    @Override
    public void rejectAccess(String rejectedToken) {
        repository.rejectToken(rejectedToken);
    }
}

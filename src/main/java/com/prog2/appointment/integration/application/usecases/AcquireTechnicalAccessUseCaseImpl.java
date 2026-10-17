package com.prog2.appointment.integration.application.usecases;

import java.time.Clock;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.enums.ProvisioningStatus;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.AcquisitionResult;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.in.AcquireTechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.out.CatalogTechnicalTokenGateway;
import com.prog2.appointment.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.appointment.integration.domain.ports.out.TechnicalAccessRepository;

/** Coordina un intento: verifica identidad, aprovisionamiento y sesión antes de publicarla completa. */
public final class AcquireTechnicalAccessUseCaseImpl implements AcquireTechnicalAccessUseCase {
    private final CatalogTechnicalTokenGateway catalog;
    private final CentralIntegrationGateway central;
    private final TechnicalAccessRepository repository;
    private final Clock clock;
    private final String expectedGroup;

    public AcquireTechnicalAccessUseCaseImpl(CatalogTechnicalTokenGateway catalog,
            CentralIntegrationGateway central, TechnicalAccessRepository repository, Clock clock, String expectedGroup) {
        this.catalog = catalog;
        this.central = central;
        this.repository = repository;
        this.clock = clock;
        this.expectedGroup = expectedGroup;
    }

    @Override
    public AcquisitionResult attemptAcquisition(TechnicalToken candidate) {
        TechnicalToken token = candidate;
        try {
            if (token == null) {
                token = catalog.fetchToken();
            }
            if (!token.isUsableAt(clock.instant()) || repository.isRejected(token.value())) {
                return AcquisitionResult.failed(null, AccessFailure.CENTRAL_TOKEN_REJECTED);
            }
            var configuration = central.fetchConfiguration(token);
            if (!expectedGroup.equals(configuration.groupId())) {
                return AcquisitionResult.failed(null, AccessFailure.INVALID_RESPONSE);
            }
            if (configuration.provisioningStatus() == ProvisioningStatus.PENDING) {
                return AcquisitionResult.failed(token, AccessFailure.PROVISIONING_PENDING);
            }
            if (configuration.provisioningStatus() != ProvisioningStatus.PROVISIONED) {
                return AcquisitionResult.failed(null, AccessFailure.INTEGRATION_NOT_READY);
            }
            if (!configuration.isComplete()) {
                return AcquisitionResult.failed(null, AccessFailure.INVALID_RESPONSE);
            }
            return repository.publishIfAccepted(new TechnicalAccess(token, configuration))
                    ? AcquisitionResult.available()
                    : AcquisitionResult.failed(null, AccessFailure.CENTRAL_TOKEN_REJECTED);
        } catch (IntegrationGatewayException exception) {
            if (exception.failure() == AccessFailure.CENTRAL_TOKEN_REJECTED && token != null) {
                repository.rejectToken(token.value());
            }
            return AcquisitionResult.failed(token, exception.failure());
        }
    }
}

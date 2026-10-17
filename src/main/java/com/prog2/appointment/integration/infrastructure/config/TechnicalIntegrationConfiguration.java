package com.prog2.appointment.integration.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.application.usecases.AcquireTechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.ports.in.AcquireTechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.out.CatalogTechnicalTokenGateway;
import com.prog2.appointment.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.appointment.integration.domain.ports.out.TechnicalAccessRepository;
import com.prog2.appointment.integration.infrastructure.client.adapter.SafeIntegrationHttpClient;
import com.prog2.appointment.integration.infrastructure.client.adapter.WebClientCatalogTechnicalTokenAdapter;
import com.prog2.appointment.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;
import com.prog2.appointment.integration.infrastructure.persistence.adapter.InMemoryTechnicalAccessAdapter;

/** Wiring específico del slice; las propiedades compartidas y la firma permanecen fuera de él. */
@Configuration(proxyBeanMethods = false)
public class TechnicalIntegrationConfiguration {
    @Bean
    TechnicalAccessRepository technicalAccessRepository(Clock clock) {
        return new InMemoryTechnicalAccessAdapter(clock);
    }

    @Bean
    CatalogTechnicalTokenGateway catalogTechnicalTokenGateway(TechnicalAccessProperties properties, ServiceTokenIssuer issuer) {
        return new WebClientCatalogTechnicalTokenAdapter(
                new SafeIntegrationHttpClient(properties.catalogBaseUrl(), false, Duration.ofSeconds(5)), issuer);
    }

    @Bean
    CentralIntegrationGateway centralIntegrationGateway(TechnicalAccessProperties properties) {
        return new WebClientCentralIntegrationAdapter(
                new SafeIntegrationHttpClient(properties.centralBaseUrl(), true, Duration.ofSeconds(5)));
    }

    @Bean
    AcquireTechnicalAccessUseCase acquireTechnicalAccessUseCase(CatalogTechnicalTokenGateway catalog,
            CentralIntegrationGateway central, TechnicalAccessRepository repository, Clock clock,
            TechnicalAccessProperties properties) {
        return new AcquireTechnicalAccessUseCaseImpl(catalog, central, repository, clock, properties.expectedGroup());
    }

    @Bean
    TechnicalAccessRecoveryAdapter technicalAccessRecovery(AcquireTechnicalAccessUseCase acquire,
            TechnicalAccessRepository repository, Clock clock) {
        return new TechnicalAccessRecoveryAdapter(acquire, repository, clock,
                Executors.newSingleThreadExecutor(task -> {
                    Thread thread = new Thread(task, "technical-access-recovery");
                    thread.setDaemon(true);
                    return thread;
                }), Thread::sleep, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    @Bean
    TechnicalAccessUseCase technicalAccessUseCase(TechnicalAccessRepository repository,
            TechnicalAccessRecoveryAdapter recovery, Clock clock) {
        return new TechnicalAccessUseCaseImpl(repository, recovery, clock);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> initializeTechnicalAccess(TechnicalAccessRecoveryAdapter recovery) {
        return _ -> recovery.requestRecovery();
    }
}

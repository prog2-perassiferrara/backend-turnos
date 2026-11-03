package com.prog2.appointment.integration.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.ports.out.CatalogTechnicalTokenGateway;
import com.prog2.appointment.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.appointment.integration.infrastructure.client.adapter.SafeIntegrationHttpClient;
import com.prog2.appointment.integration.infrastructure.client.adapter.WebClientCatalogTechnicalTokenAdapter;
import com.prog2.appointment.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;

/** Wiring específico del slice; las propiedades compartidas y la firma permanecen fuera de él. */
@Configuration(proxyBeanMethods = false)
public class TechnicalIntegrationConfiguration {
    @Bean
    CatalogTechnicalTokenGateway catalogTechnicalTokenGateway(TechnicalAccessProperties properties,
            ServiceTokenIssuer issuer,
            @Value("${app.integration.http-timeout}") Duration httpTimeout) {
        return new WebClientCatalogTechnicalTokenAdapter(
                new SafeIntegrationHttpClient(properties.catalogBaseUrl(), false, httpTimeout), issuer);
    }

    @Bean
    CentralIntegrationGateway centralIntegrationGateway(TechnicalAccessProperties properties,
            @Value("${app.integration.http-timeout}") Duration httpTimeout) {
        return new WebClientCentralIntegrationAdapter(
                new SafeIntegrationHttpClient(properties.centralBaseUrl(), true, httpTimeout));
    }

    @Bean
    TechnicalAccessUseCaseImpl technicalAccessUseCase(CatalogTechnicalTokenGateway catalog,
            CentralIntegrationGateway central, Clock clock, TechnicalAccessProperties properties) {
        return new TechnicalAccessUseCaseImpl(catalog, central, clock, properties.expectedGroup());
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> initializeTechnicalAccess(TechnicalAccessUseCaseImpl access) {
        return _ -> access.initialize();
    }
}

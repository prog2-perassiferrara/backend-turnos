package com.prog2.appointment.availability.infrastructure.config;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import com.prog2.appointment.availability.application.usecases.GetAvailabilityUseCaseImpl;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.availability.domain.ports.out.AppointmentOccupanciesGateway;
import com.prog2.appointment.availability.domain.ports.out.ProfessionalAgendaGateway;
import com.prog2.appointment.availability.infrastructure.client.AvailabilityHttpClient;
import com.prog2.appointment.availability.infrastructure.client.adapter.WebClientAppointmentOccupanciesAdapter;
import com.prog2.appointment.availability.infrastructure.client.adapter.WebClientProfessionalAgendaAdapter;
import com.prog2.appointment.infrastructure.config.AvailabilityProperties;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;

/** Cableado del slice; crea los colaboradores y transportes sin cabecera bearer global ni recuperación duplicada. */
@Configuration(proxyBeanMethods = false)
public class AvailabilityConfiguration {
    @Bean
    AvailabilityProperties availabilityProperties(Environment environment) {
        return AvailabilityProperties.from(environment);
    }

    @Bean
    ProfessionalAgendaGateway professionalAgendaGateway(TechnicalAccessProperties access,
            AvailabilityProperties properties, ServiceTokenIssuer issuer) {
        return new WebClientProfessionalAgendaAdapter(httpClient(access.catalogBaseUrl(), properties.httpTimeout(),
                AvailabilityFailure.CATALOG_UNAVAILABLE), issuer);
    }

    @Bean
    AppointmentOccupanciesGateway appointmentOccupanciesGateway(TechnicalAccessProperties properties,
            AvailabilityProperties availability, TechnicalAccessUseCase access) {
        return new WebClientAppointmentOccupanciesAdapter(httpClient(properties.centralBaseUrl(), availability.httpTimeout(),
                AvailabilityFailure.OCCUPANCIES_UNAVAILABLE), access);
    }

    @Bean
    GetAvailabilityUseCase getAvailabilityUseCase(ProfessionalAgendaGateway agendas,
            AppointmentOccupanciesGateway occupancies, Clock clock, AvailabilityProperties properties) {
        return new GetAvailabilityUseCaseImpl(agendas, occupancies, clock, properties.timeZone());
    }

    /**
     * @param baseUrl destino externo validado, nunca recibido de KMP
     * @param timeout presupuesto de conexión/lectura; el total es su doble
     * @param unavailable clasificación segura para fallos de transporte/decodificación
     * @return cliente GET sin redirects ni reintentos ocultos
     */
    public static AvailabilityHttpClient httpClient(URI baseUrl, Duration timeout, AvailabilityFailure unavailable) {
        var transport = HttpClient.create().disableRetry(true).followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout);
        var webClient = WebClient.builder().baseUrl(baseUrl.toString())
                .clientConnector(new ReactorClientHttpConnector(transport)).build();
        return new AvailabilityHttpClient(webClient, timeout.multipliedBy(2), unavailable);
    }
}

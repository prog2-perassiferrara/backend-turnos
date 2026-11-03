package com.prog2.appointment.openapi;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.availability.infrastructure.web.controller.AvailabilityController;
import com.prog2.appointment.availability.infrastructure.web.mapper.AvailabilityDtoMapper;
import com.prog2.appointment.infrastructure.config.OpenApiConfiguration;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.config.SecurityConfiguration;
import com.prog2.appointment.reservation.domain.ports.in.CancelReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.ports.in.ListOwnReservationsUseCase;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.reservation.infrastructure.web.controller.ReservationController;
import com.prog2.appointment.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import com.prog2.appointment.security.JwtTestTokens;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.domain.ports.in.RegisterUserUseCase;
import com.prog2.appointment.user.infrastructure.web.controller.AuthenticationController;
import com.prog2.appointment.user.infrastructure.web.controller.RegistrationController;
import com.prog2.appointment.user.infrastructure.web.mapper.RegistrationDtoMapper;

import static org.mockito.Mockito.mock;

/** Generador, MVC y seguridad reales; los puertos permiten detectar llamadas de negocio indebidas. */
@Configuration(proxyBeanMethods = false)
@Profile("openapi-test")
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class, KafkaAutoConfiguration.class})
@Import({OpenApiConfiguration.class, SecurityConfiguration.class, RegistrationController.class,
        AuthenticationController.class, AvailabilityController.class, ReservationController.class,
        RegistrationDtoMapper.class, AvailabilityDtoMapper.class, ReservationDtoMapper.class})
class OpenApiTestConfiguration {
    @Bean
    Clock clock() {
        return Clock.fixed(JwtTestTokens.NOW, ZoneOffset.UTC);
    }

    @Bean
    TechnicalAccessProperties technicalAccessProperties() {
        return JwtTestTokens.access();
    }

    @Bean
    RegisterUserUseCase registration() {
        return mock(RegisterUserUseCase.class);
    }

    @Bean
    AuthenticateUserUseCase authentication() {
        return mock(AuthenticateUserUseCase.class);
    }

    @Bean
    GetAvailabilityUseCase availability() {
        return mock(GetAvailabilityUseCase.class);
    }

    @Bean
    StartReservationUseCase start() {
        return mock(StartReservationUseCase.class);
    }

    @Bean
    SubmitReservationPhoneUseCase phone() {
        return mock(SubmitReservationPhoneUseCase.class);
    }

    @Bean
    GetReservationProcessUseCase get() {
        return mock(GetReservationProcessUseCase.class);
    }

    @Bean
    ListOwnReservationsUseCase list() {
        return mock(ListOwnReservationsUseCase.class);
    }

    @Bean
    CancelReservationUseCase cancel() {
        return mock(CancelReservationUseCase.class);
    }
}

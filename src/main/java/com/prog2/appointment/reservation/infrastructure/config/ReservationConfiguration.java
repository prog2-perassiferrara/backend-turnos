package com.prog2.appointment.reservation.infrastructure.config;

import java.time.Clock;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.prog2.appointment.reservation.application.usecases.SubmitReservationPhoneUseCaseImpl;
import com.prog2.appointment.reservation.application.usecases.PublishPendingPhonesUseCaseImpl;
import com.prog2.appointment.reservation.application.usecases.GetReservationProcessUseCaseImpl;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.reservation.domain.ports.in.PublishPendingPhonesUseCase;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.PhonePublicationGateway;
import com.prog2.appointment.reservation.infrastructure.messaging.KafkaPhoneSubmissionAdapter;
import com.prog2.appointment.reservation.infrastructure.messaging.PhoneSubmissionDispatcher;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientCentralAppointmentAdapter;
import java.util.concurrent.Executors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.infrastructure.config.AvailabilityProperties;
import com.prog2.appointment.infrastructure.config.ReservationProperties;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.application.usecases.ReceiveReservationActionUseCaseImpl;
import com.prog2.appointment.reservation.application.usecases.StartReservationUseCaseImpl;
import com.prog2.appointment.reservation.domain.ports.in.ReceiveReservationActionUseCase;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.out.AppointmentHoldGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservableSlotGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationPatientGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import com.prog2.appointment.reservation.infrastructure.availability.adapter.AvailabilityReservationSlotAdapter;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientAppointmentHoldAdapter;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationActionParser;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationKafkaChannel;
import com.prog2.appointment.reservation.infrastructure.user.adapter.UserReservationPatientAdapter;
import com.prog2.appointment.user.domain.ports.in.GetUserProfileUseCase;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring del slice; solo infraestructura compone puertos públicos de user, availability e
 * integration.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReservationConfiguration {
    @Bean
    ReservationProperties reservationProperties(Environment environment) {
        return ReservationProperties.from(environment);
    }

    @Bean
    ReservationPatientGateway reservationPatientGateway(GetUserProfileUseCase profiles) {
        return new UserReservationPatientAdapter(profiles);
    }

    @Bean
    ReservableSlotGateway reservableSlotGateway(GetAvailabilityUseCase availability) {
        return new AvailabilityReservationSlotAdapter(availability);
    }

    @Bean
    AppointmentHoldGateway appointmentHoldGateway(
            TechnicalAccessProperties access,
            TechnicalAccessUseCase session,
            ReservationProperties properties) {
        var timeout = properties.httpTimeout();
        var transport =
                HttpClient.create()
                        .disableRetry(true)
                        .followRedirect(false)
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                Math.toIntExact(timeout.toMillis()))
                        .responseTimeout(timeout);
        var client =
                WebClient.builder()
                        .baseUrl(access.centralBaseUrl().toString())
                        .clientConnector(new ReactorClientHttpConnector(transport))
                        .build();
        return new WebClientAppointmentHoldAdapter(client, session, timeout);
    }

    @Bean
    ReceiveReservationActionUseCase receiveReservationActionUseCase(
            ReservationProcessRepository repository) {
        return new ReceiveReservationActionUseCaseImpl(repository);
    }

    @Bean
    ReservationActionParser reservationActionParser(
            JsonMapper json, TechnicalAccessProperties properties) {
        return new ReservationActionParser(json, properties.expectedGroup());
    }

    @Bean
    ReservationKafkaChannel reservationKafkaChannel(
            TechnicalAccessUseCase session,
            ReceiveReservationActionUseCase receive,
            ReservationActionParser parser,
            ReservationProperties properties) {
        return new ReservationKafkaChannel(
                session,
                receive,
                parser,
                properties,
                Executors.newSingleThreadExecutor(
                        task -> {
                            var thread = new Thread(task, "reservation-messaging-preparation");
                            thread.setDaemon(true);
                            return thread;
                        }));
    }

    @Bean
    StartReservationUseCase startReservationUseCase(
            ReservationPatientGateway patients,
            ReservableSlotGateway slots,
            AppointmentHoldGateway holds,
            ReservationProcessRepository repository,
            ReservationKafkaChannel messaging,
            Clock clock,
            AvailabilityProperties properties,
            ReservationProperties reservationProperties) {
        return new StartReservationUseCaseImpl(
                patients,
                slots,
                holds,
                repository,
                messaging,
                clock.withZone(properties.timeZone()),
                reservationProperties.attempts());
    }

    @Bean
    SubmitReservationPhoneUseCase submitReservationPhoneUseCase(
            ReservationProcessRepository repository,
            TechnicalAccessProperties properties,
            Clock clock) {
        return new SubmitReservationPhoneUseCaseImpl(repository, properties.expectedGroup(), clock);
    }

    @Bean
    KafkaPhoneSubmissionAdapter phonePublicationGateway(
            ReservationKafkaChannel channel,
            JsonMapper json,
            Clock clock,
            ReservationProperties properties) {
        return new KafkaPhoneSubmissionAdapter(
                channel, json, clock, properties.phonePublishTimeout());
    }

    @Bean
    PublishPendingPhonesUseCase publishPendingPhonesUseCase(
            ReservationProcessRepository repository,
            PhonePublicationGateway gateway,
            Clock clock,
            ReservationProperties properties) {
        return new PublishPendingPhonesUseCaseImpl(
                repository,
                gateway,
                clock,
                properties.attempts(),
                properties.retryDelay(),
                properties.preparationCooldown());
    }

    @Bean
    PhoneSubmissionDispatcher phoneSubmissionDispatcher(PublishPendingPhonesUseCase publish) {
        return new PhoneSubmissionDispatcher(publish);
    }

    @Bean
    CentralAppointmentGateway centralAppointmentGateway(
            TechnicalAccessProperties properties,
            TechnicalAccessUseCase access,
            ReservationProperties reservations) {
        var timeout = reservations.httpTimeout();
        var transport =
                HttpClient.create()
                        .disableRetry(true)
                        .followRedirect(false)
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                Math.toIntExact(timeout.toMillis()))
                        .responseTimeout(timeout);
        var client =
                WebClient.builder()
                        .baseUrl(properties.centralBaseUrl().toString())
                        .clientConnector(new ReactorClientHttpConnector(transport))
                        .build();
        return new WebClientCentralAppointmentAdapter(client, access, timeout);
    }

    @Bean
    GetReservationProcessUseCase getReservationProcessUseCase(
            ReservationProcessRepository repository,
            CentralAppointmentGateway central,
            Clock clock,
            ReservationProperties properties) {
        return new GetReservationProcessUseCaseImpl(
                repository, central, clock, properties.resultVerificationDelay());
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> prepareReservationMessaging(
            ReservationKafkaChannel channel) {
        return _ -> channel.requestPreparation();
    }
}

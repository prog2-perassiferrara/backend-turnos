package com.prog2.appointment.reservation.infrastructure.config;

import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationProcessesUseCase;
import com.prog2.appointment.reservation.application.usecases.RecoverReservationProcessesUseCaseImpl;
import com.prog2.appointment.reservation.domain.model.ReservationRecoveryPolicy;

import java.time.Clock;
import com.prog2.appointment.reservation.application.service.ReservationExecutionGuard;
import com.prog2.appointment.reservation.domain.ports.in.RecoverReservationCancellationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationCancellationGateway;
import com.prog2.appointment.reservation.application.usecases.RecoverReservationCancellationsUseCaseImpl;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientReservationCancellationAdapter;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.prog2.appointment.reservation.application.usecases.SubmitReservationPhoneUseCaseImpl;
import com.prog2.appointment.reservation.application.usecases.PublishPendingPhonesUseCaseImpl;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.reservation.domain.ports.in.PublishPendingPhonesUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.PhonePublicationGateway;
import com.prog2.appointment.reservation.infrastructure.messaging.KafkaPhoneSubmissionAdapter;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientCentralAppointmentAdapter;
import java.util.concurrent.Executors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import com.prog2.appointment.infrastructure.config.AvailabilityProperties;
import com.prog2.appointment.infrastructure.config.ReservationProperties;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.application.usecases.StartReservationUseCaseImpl;
import com.prog2.appointment.reservation.domain.ports.in.ReceiveReservationActionUseCase;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.out.AppointmentHoldGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservableSlotGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationPatientGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import com.prog2.appointment.reservation.infrastructure.client.adapter.WebClientAppointmentHoldAdapter;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationActionParser;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationKafkaChannel;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configura recursos y políticas del slice; los componentes simples se descubren por Spring.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReservationConfiguration {
    @Bean
    RecoverReservationCancellationsUseCase recoverReservationCancellationsUseCase(
            ReservationProcessRepository repository, CentralAppointmentGateway central,
            ReservationCancellationGateway cancellation, Clock clock, ReservationProperties properties,
            ReservationExecutionGuard guard) {
        return new RecoverReservationCancellationsUseCaseImpl(repository, central, cancellation, clock,
                properties.httpTimeout(), properties.retryDelay(), guard);
    }

    @Bean
    ReservationCancellationGateway reservationCancellationGateway(TechnicalAccessProperties access,
            TechnicalAccessUseCase session, ReservationProperties properties) {
        return new WebClientReservationCancellationAdapter(centralClient(access, properties),
                session, properties.httpTimeout());
    }

    @Bean
    ReservationProperties reservationProperties(Environment environment) {
        return ReservationProperties.from(environment);
    }

    @Bean
    AppointmentHoldGateway appointmentHoldGateway(
            TechnicalAccessProperties access,
            TechnicalAccessUseCase session,
            ReservationProperties properties) {
        return new WebClientAppointmentHoldAdapter(centralClient(access, properties),
                session, properties.httpTimeout());
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
    RecoverReservationProcessesUseCase recoverReservationProcessesUseCase(
            ReservationProcessRepository repository, CentralAppointmentGateway central,
            StartReservationUseCase start, ReservationExecutionGuard guard,
            RecoverReservationCancellationsUseCase cancellations, Clock clock,
            ReservationProperties properties) {
        return new RecoverReservationProcessesUseCaseImpl(repository, central, start, guard, cancellations, clock,
                new ReservationRecoveryPolicy(properties.retryDelay(),
                        properties.preparationCooldown(), properties.resultVerificationDelay()));
    }

    @Bean
    StartReservationUseCase startReservationUseCase(
            ReservationPatientGateway patients,
            ReservableSlotGateway slots,
            AppointmentHoldGateway holds,
            ReservationProcessRepository repository,
            ReservationKafkaChannel messaging,
            ReservationExecutionGuard guard,
            Clock clock,
            AvailabilityProperties properties,
            ReservationProperties reservationProperties) {
        return new StartReservationUseCaseImpl(
                patients,
                slots,
                holds,
                repository,
                messaging,
                guard,
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
    CentralAppointmentGateway centralAppointmentGateway(
            TechnicalAccessProperties properties,
            TechnicalAccessUseCase access,
            ReservationProperties reservations) {
        return new WebClientCentralAppointmentAdapter(centralClient(properties, reservations),
                access, reservations.httpTimeout());
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> prepareReservationMessaging(
            ReservationKafkaChannel channel) {
        return _ -> channel.requestPreparation();
    }
    /** Mismo transporte sin reintentos ni redirecciones; cada adaptador conserva su cliente. */
    private static WebClient centralClient(TechnicalAccessProperties access, ReservationProperties properties) {
        var timeout = properties.httpTimeout();
        var transport = HttpClient.create().disableRetry(true).followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout);
        return WebClient.builder().baseUrl(access.centralBaseUrl().toString())
                .clientConnector(new ReactorClientHttpConnector(transport)).build();
    }
}

package com.prog2.appointment.integration.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.model.*;

/** El evento de arranque ejecuta la adquisición en su propio hilo, sin un worker adicional. */
class TechnicalIntegrationLifecycleTests {
    @Test
    void applicationReadyAcquiresSynchronouslyAndConsumptionDoesNotStartBackgroundWork() {
        var calls = new AtomicInteger();
        var execution = new AtomicReference<Thread>();
        var access = new TechnicalAccessUseCaseImpl(() -> {
            execution.set(Thread.currentThread()); calls.incrementAndGet();
            return new TechnicalToken("fictional", null);
        }, _ -> new IntegrationConfiguration("appointment-test", "fictional:9092", "consumer", "actions", "phone"),
                Clock.systemUTC(), "appointment-test");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.addApplicationListener(new TechnicalIntegrationConfiguration().initializeTechnicalAccess(access));
            context.refresh();
            assertThatThrownBy(access::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
            assertThat(access.getAccess().configuration().groupId()).isEqualTo("appointment-test");
            assertThat(execution.get()).isSameAs(Thread.currentThread());
            access.getAccess();
            assertThat(calls).hasValue(1);
        }
    }
}

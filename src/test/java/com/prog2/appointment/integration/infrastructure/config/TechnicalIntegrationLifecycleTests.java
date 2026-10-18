package com.prog2.appointment.integration.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.model.AcquisitionResult;
import com.prog2.appointment.integration.domain.ports.in.AcquireTechnicalAccessUseCase;
import com.prog2.appointment.integration.infrastructure.persistence.adapter.InMemoryTechnicalAccessAdapter;
import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba el cierre inferido por Spring con el ejecutor real creado por la configuración productiva. */
class TechnicalIntegrationLifecycleTests {
    @Test
    void closingSpringContextInterruptsAndTerminatesOwnedWorker() throws InterruptedException {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        var attempts = new AtomicInteger();
        Clock clock = Clock.systemUTC();
        var repository = new InMemoryTechnicalAccessAdapter(clock);
        AcquireTechnicalAccessUseCase acquire = _ -> {
            attempts.incrementAndGet();
            worker.set(Thread.currentThread());
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException _) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return AcquisitionResult.failed(null, AccessFailure.TEMPORARY_UNAVAILABLE);
        };

        try {
            TechnicalAccessRecoveryAdapter recovery;
            try (var context = new AnnotationConfigApplicationContext()) {
                // Aísla el ciclo de vida: usa la fábrica productiva, sin proveedores HTTP ni base de datos.
                context.registerBean(TechnicalAccessRecoveryAdapter.class,
                        () -> new TechnicalIntegrationConfiguration().technicalAccessRecovery(acquire, repository, clock));
                context.refresh();
                recovery = context.getBean(TechnicalAccessRecoveryAdapter.class);
                recovery.requestRecovery();
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.get().join(Duration.ofSeconds(5))).isTrue();
            recovery.requestRecovery();
            assertThat(attempts).hasValue(1);
        } finally {
            // Evita dejar un hilo propio incluso si una regresión impide que Spring lo cierre.
            Thread thread = worker.get();
            if (thread != null) {
                thread.interrupt();
                thread.join(Duration.ofSeconds(5));
            }
        }
    }
}

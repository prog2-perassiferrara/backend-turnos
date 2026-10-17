package com.prog2.appointment.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.prog2.appointment.integration.application.exception.TechnicalAccessUnavailableException;
import com.prog2.appointment.integration.application.usecases.TechnicalAccessUseCaseImpl;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.model.AcquisitionResult;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.in.AcquireTechnicalAccessUseCase;
import com.prog2.appointment.integration.infrastructure.config.TechnicalAccessRecoveryAdapter;
import com.prog2.appointment.integration.infrastructure.persistence.adapter.InMemoryTechnicalAccessAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Coordinador real con executor de un hilo; relojes y barreras controlan carreras sin sleeps arbitrarios. */
@ExtendWith(OutputCaptureExtension.class)
class TechnicalAccessRecoveryTests {
    private static final Instant START = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void limitsAttemptsAndAdmitsNextDemandAtExactCooldownBoundary()
            throws InterruptedException, ExecutionException, TimeoutException {
        var attempts = new AtomicInteger();
        var pauses = new ArrayList<Duration>();
        try (var harness = new Harness(_ -> {
            attempts.incrementAndGet();
            return AcquisitionResult.failed(null, AccessFailure.TEMPORARY_UNAVAILABLE);
        }, pauses::add)) {
            harness.recovery.requestRecovery();
            harness.drain();
            assertThat(attempts).hasValue(3);
            assertThat(pauses).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(5));
            harness.now.set(START.plusSeconds(29));
            for (int i = 0; i < 10; i++) {
                harness.recovery.requestRecovery();
            }
            harness.drain();
            assertThat(attempts).hasValue(3);
            harness.now.set(START.plusSeconds(30));
            harness.recovery.requestRecovery();
            harness.drain();
            assertThat(attempts).hasValue(6);
            harness.now.set(START.plusSeconds(300));
            harness.drain(); // Avanzar tiempo no programa ningún ciclo por sí solo.
            assertThat(attempts).hasValue(6);
        }
    }

    @Test
    void preservesPendingCandidateAndStopsAfterSuccess()
            throws InterruptedException, ExecutionException, TimeoutException {
        var attempts = new AtomicInteger();
        var token = new TechnicalToken("fictional-pending", null);
        try (var harness = new Harness(candidate -> {
            if (attempts.incrementAndGet() == 1) {
                assertThat(candidate).isNull();
                return AcquisitionResult.failed(token, AccessFailure.PROVISIONING_PENDING);
            }
            assertThat(candidate).isSameAs(token);
            return AcquisitionResult.available();
        }, _ -> {})) {
            harness.recovery.requestRecovery();
            harness.drain();
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void stopsTerminalCycleWithoutSleeping()
            throws InterruptedException, ExecutionException, TimeoutException {
        var attempts = new AtomicInteger();
        var pauses = new AtomicInteger();
        try (var harness = new Harness(_ -> {
            attempts.incrementAndGet();
            return AcquisitionResult.failed(null, AccessFailure.CATALOG_AUTHENTICATION);
        }, _ -> pauses.incrementAndGet())) {
            harness.recovery.requestRecovery();
            harness.drain();
            harness.recovery.requestRecovery();
            harness.drain();
            assertThat(attempts).hasValue(1);
            assertThat(pauses).hasValue(0);
        }
    }

    @Test
    void concurrentForegroundRequestsShareOneCycleAndReturnWithoutWaiting()
            throws InterruptedException, ExecutionException, TimeoutException {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var reference = new AtomicReference<InMemoryTechnicalAccessAdapter>();
        try (var harness = new Harness(_ -> {
            attempts.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test barrier was not released");
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return AcquisitionResult.failed(null, AccessFailure.TEMPORARY_UNAVAILABLE);
            }
            reference.get().publishIfAccepted(TechnicalAccessBehaviorTests.access("fictional-ready", null));
            return AcquisitionResult.available();
        }, _ -> {}); var callers = Executors.newFixedThreadPool(8)) {
            reference.set(harness.repository);
            var access = new TechnicalAccessUseCaseImpl(harness.repository, harness.recovery, harness.clock);
            assertThatThrownBy(access::getAccess).isInstanceOf(TechnicalAccessUnavailableException.class);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                List<java.util.concurrent.Future<?>> requests = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    requests.add(callers.submit(() -> assertThatThrownBy(access::getAccess)
                            .isInstanceOf(TechnicalAccessUnavailableException.class)));
                }
                for (var request : requests) {
                    request.get(2, TimeUnit.SECONDS);
                }
                assertThat(attempts).hasValue(1);
            } finally {
                release.countDown();
            }
            harness.drain();
            assertThat(access.getAccess().configuration().groupId()).isEqualTo(TestIntegrationSupport.GROUP);
            assertThat(attempts).hasValue(1);
        }
    }

    @Test
    void closeInterruptsDelayAndPreventsFurtherAttempts() throws InterruptedException {
        var sleeping = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        try (var harness = new Harness(_ -> {
            attempts.incrementAndGet();
            return AcquisitionResult.failed(null, AccessFailure.TEMPORARY_UNAVAILABLE);
        }, duration -> {
            sleeping.countDown();
            Thread.sleep(duration);
        })) {
            harness.recovery.requestRecovery();
            assertThat(sleeping.await(5, TimeUnit.SECONDS)).isTrue();
            harness.recovery.close();
            assertThat(harness.executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            harness.recovery.requestRecovery();
            assertThat(attempts).hasValue(1);
        }
    }

    @Test
    void closePreventsPublicationEvenWhenLateCallReturns() throws InterruptedException {
        var entered = new CountDownLatch(1);
        var repository = new AtomicReference<InMemoryTechnicalAccessAdapter>();
        var published = new AtomicBoolean(true);
        try (var harness = new Harness(_ -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            published.set(repository.get().publishIfAccepted(TechnicalAccessBehaviorTests.access("fictional-late", null)));
            return AcquisitionResult.available();
        }, _ -> {})) {
            repository.set(harness.repository);
            harness.recovery.requestRecovery();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            harness.recovery.close();
            assertThat(harness.executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(published).isFalse();
            assertThat(harness.repository.current()).isEmpty();
        }
    }

    @Test
    void unexpectedFailureDoesNotLeakExceptionMessageAndAllowsLaterDemand(CapturedOutput output)
            throws InterruptedException, ExecutionException, TimeoutException {
        var attempts = new AtomicInteger();
        try (var harness = new Harness(_ -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("fictional-sensitive-internal-cause");
            }
            return AcquisitionResult.available();
        }, _ -> {})) {
            harness.recovery.requestRecovery();
            harness.drain();
            assertThat(output).doesNotContain("fictional-sensitive-internal-cause");
            harness.now.set(START.plusSeconds(30));
            harness.recovery.requestRecovery();
            harness.drain();
            assertThat(attempts).hasValue(2);
        }
    }

    /** Controla tiempo y observa finalización mediante una barrera en el mismo executor real. */
    private static final class Harness implements AutoCloseable {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);
        private final Clock clock = mock(Clock.class);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final InMemoryTechnicalAccessAdapter repository;
        private final TechnicalAccessRecoveryAdapter recovery;

        private Harness(AcquireTechnicalAccessUseCase acquire, TechnicalAccessRecoveryAdapter.RecoveryDelay delay) {
            when(clock.instant()).thenAnswer(_ -> now.get());
            repository = new InMemoryTechnicalAccessAdapter(clock);
            recovery = new TechnicalAccessRecoveryAdapter(acquire, repository, clock, executor,
                    delay, Duration.ofSeconds(5), Duration.ofSeconds(30));
        }

        private void drain() throws InterruptedException, ExecutionException, TimeoutException {
            executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            recovery.close();
        }
    }
}

package com.prog2.appointment.integration.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.prog2.appointment.integration.domain.enums.AcquisitionOutcome;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.in.AcquireTechnicalAccessUseCase;
import com.prog2.appointment.integration.domain.ports.out.RequestTechnicalAccessRecovery;
import com.prog2.appointment.integration.domain.ports.out.TechnicalAccessRepository;

/** Coordina un único ciclo finito en segundo plano; ninguna espera programa el siguiente ciclo. */
public final class TechnicalAccessRecoveryAdapter implements RequestTechnicalAccessRecovery, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TechnicalAccessRecoveryAdapter.class);
    private final AcquireTechnicalAccessUseCase acquire;
    private final TechnicalAccessRepository repository;
    private final Clock clock;
    private final ExecutorService executor;
    private final RecoveryDelay delay;
    private final Duration betweenAttempts;
    private final Duration betweenCycles;
    private boolean running;
    private boolean closed;
    private Instant nextCycleAt = Instant.MIN;

    /**
     * @param acquire caso de uso que ejecuta un único intento
     * @param repository estado que se cierra antes de interrumpir HTTP para impedir publicaciones tardías
     * @param clock reloj UTC controlable
     * @param executor ejecutor dedicado, propiedad de este coordinador
     * @param delay espera interrumpible, sustituible en pruebas
     * @param betweenAttempts espera entre intentos del mismo ciclo
     * @param betweenCycles espera mínima entre ciclos fallidos
     */
    public TechnicalAccessRecoveryAdapter(AcquireTechnicalAccessUseCase acquire, TechnicalAccessRepository repository,
            Clock clock, ExecutorService executor, RecoveryDelay delay,
            Duration betweenAttempts, Duration betweenCycles) {
        this.acquire = acquire;
        this.repository = repository;
        this.clock = clock;
        this.executor = executor;
        this.delay = delay;
        this.betweenAttempts = betweenAttempts;
        this.betweenCycles = betweenCycles;
    }

    @Override
    public synchronized void requestRecovery() {
        if (closed || running || clock.instant().isBefore(nextCycleAt)) {
            return;
        }
        running = true; // Reservar antes de encolar impide acumular tareas concurrentes.
        try {
            executor.execute(this::runCycle);
        } catch (RejectedExecutionException _) {
            running = false;
            nextCycleAt = clock.instant().plus(betweenCycles);
            LOG.warn("Technical access executor rejected a recovery cycle");
        }
    }

    private void runCycle() {
        boolean success = false;
        TechnicalToken candidate = null;
        try {
            for (int attempt = 1; attempt <= 3 && !isClosed(); attempt++) {
                var result = acquire.attemptAcquisition(candidate);
                LOG.info("Technical access attempt {}/3: {} ({})", attempt, result.outcome(), result.failure());
                if (result.outcome() == AcquisitionOutcome.AVAILABLE) {
                    success = true;
                    return;
                }
                if (result.outcome() == AcquisitionOutcome.TERMINAL) {
                    return;
                }
                candidate = result.candidate();
                if (attempt < 3 && !isClosed()) {
                    delay.pause(betweenAttempts);
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException _) {
            LOG.warn("Technical access cycle stopped unexpectedly");
        } finally {
            finishCycle(success);
        }
    }

    private synchronized boolean isClosed() {
        return closed;
    }

    private synchronized void finishCycle(boolean success) {
        nextCycleAt = success ? Instant.MIN : clock.instant().plus(betweenCycles);
        running = false;
    }

    /** Cierra primero el repositorio: un HTTP tardío no puede publicar después de cerrar el backend. */
    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            repository.close();
        }
        executor.shutdownNow();
    }

    /** Espera técnica interrumpible; en producción se utiliza Thread.sleep solo en el hilo dedicado. */
    @FunctionalInterface
    public interface RecoveryDelay {
        /**
         * @param duration espera entre intentos
         * @throws InterruptedException si se cierra el contexto
         */
        void pause(Duration duration) throws InterruptedException;
    }
}

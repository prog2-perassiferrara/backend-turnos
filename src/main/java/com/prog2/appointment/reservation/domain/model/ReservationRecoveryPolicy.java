package com.prog2.appointment.reservation.domain.model;

import java.time.Duration;

/**
 * Plazos de revisión, independientes de los presupuestos de efectos externos.
 * @param retryDelay separación entre revisiones
 * @param initialDelay espera antes de la primera revisión
 * @param verificationDelay espera tras el ACK del teléfono antes de consultar el resultado
 */
public record ReservationRecoveryPolicy(
        Duration retryDelay, Duration initialDelay, Duration verificationDelay) {}

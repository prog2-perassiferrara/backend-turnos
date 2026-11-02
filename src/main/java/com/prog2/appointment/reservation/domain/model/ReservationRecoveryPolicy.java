package com.prog2.appointment.reservation.domain.model;

import java.time.Duration;

/**
 * Presupuesto y plazos de las revisiones de un proceso; no autoriza efectos remotos.
 *
 * @param attempts máximo de revisiones consecutivas por ciclo
 * @param retryDelay separación entre revisiones del mismo ciclo
 * @param cooldown pausa al agotar el ciclo y espera antes de la primera revisión
 * @param verificationDelay espera tras publicar el teléfono antes de consultar el resultado
 */
public record ReservationRecoveryPolicy(
        int attempts, Duration retryDelay, Duration cooldown, Duration verificationDelay) {}

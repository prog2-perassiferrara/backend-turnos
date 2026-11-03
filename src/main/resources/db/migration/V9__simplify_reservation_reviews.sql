-- Las revisiones solo consultan evidencia; el presupuesto queda en cada efecto.
ALTER TABLE reservation_process
    DROP CHECK ck_recovery_attempts,
    DROP COLUMN recovery_attempts;

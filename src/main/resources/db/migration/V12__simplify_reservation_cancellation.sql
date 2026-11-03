-- Conservar la fecha más conservadora de una ejecución/revisión previa, sin ampliar el hold.
UPDATE reservation_process
SET recovery_next_attempt_at = GREATEST(
        COALESCE(recovery_next_attempt_at, cancellation_next_attempt_at, CURRENT_TIMESTAMP(6)),
        COALESCE(cancellation_next_attempt_at, recovery_next_attempt_at, CURRENT_TIMESTAMP(6))),
    reason_code = COALESCE(reason_code, cancellation_last_failure)
WHERE cancellation_status IN ('PENDING', 'REJECTED')
  AND phase NOT IN ('CANCELLED', 'FAILED', 'EXPIRED');

UPDATE reservation_process
SET cancellation_status = CASE
    WHEN phase IN ('CANCELLED', 'FAILED', 'EXPIRED') OR cancellation_status = 'RESOLVED' THEN NULL
    WHEN cancellation_status = 'REJECTED' OR cancellation_attempts >= 3 THEN 'RETRY_REQUIRED'
    ELSE 'PENDING'
END
WHERE cancellation_status IS NOT NULL;

ALTER TABLE reservation_process
    DROP CHECK ck_cancellation_attempts,
    DROP CHECK ck_cancellation_retry_version,
    DROP INDEX ix_reservation_cancellation,
    DROP COLUMN cancellation_requested_at,
    DROP COLUMN cancellation_retry_version,
    DROP COLUMN cancellation_attempts,
    DROP COLUMN cancellation_next_attempt_at,
    DROP COLUMN cancellation_last_failure,
    ADD CONSTRAINT ck_cancellation_status CHECK (
        cancellation_status IS NULL OR cancellation_status IN ('PENDING', 'RETRY_REQUIRED'));

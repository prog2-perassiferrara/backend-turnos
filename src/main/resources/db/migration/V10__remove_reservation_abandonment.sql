-- Retirar el abandono no acredita cancelación ni cambia la fase o el plazo central.
UPDATE reservation_process SET cancellation_status = NULL, cancellation_requested_at = NULL,
    cancellation_retry_version = 0, cancellation_attempts = 0,
    cancellation_next_attempt_at = NULL, cancellation_last_failure = NULL
WHERE cancellation_kind = 'ABANDON';

ALTER TABLE reservation_process
    DROP INDEX uk_reservation_active_owner,
    DROP COLUMN active_owner_id,
    DROP COLUMN abandonment_pending,
    DROP COLUMN cancellation_kind,
    ADD active_owner_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN closed_at IS NULL THEN owner_id ELSE NULL END) STORED,
    ADD CONSTRAINT uk_reservation_active_owner UNIQUE (active_owner_id);

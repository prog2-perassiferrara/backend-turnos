ALTER TABLE reservation_process
    ADD cancellation_kind VARCHAR(16) NULL,
    ADD cancellation_status VARCHAR(16) NULL,
    ADD cancellation_requested_at TIMESTAMP(6) NULL,
    ADD cancellation_retry_version INT NOT NULL DEFAULT 0,
    ADD cancellation_attempts INT NOT NULL DEFAULT 0,
    ADD cancellation_next_attempt_at TIMESTAMP(6) NULL,
    ADD cancellation_last_failure VARCHAR(64) NULL,
    ADD CONSTRAINT ck_cancellation_attempts CHECK (cancellation_attempts BETWEEN 0 AND 3),
    ADD CONSTRAINT ck_cancellation_retry_version CHECK (cancellation_retry_version >= 0),
    ADD INDEX ix_reservation_cancellation (cancellation_status, cancellation_next_attempt_at);

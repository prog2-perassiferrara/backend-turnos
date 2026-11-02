ALTER TABLE reservation_process
    ADD recovery_attempts INT NOT NULL DEFAULT 0,
    ADD recovery_next_attempt_at TIMESTAMP(6) NULL,
    ADD CONSTRAINT ck_recovery_attempts CHECK (recovery_attempts >= 0),
    ADD INDEX ix_reservation_recovery (recovery_next_attempt_at, id);

CREATE INDEX ix_reservation_deferred ON reservation_inbox (outcome, rejection, message_key);

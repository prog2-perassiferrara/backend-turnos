CREATE TABLE reservation_phone_submission (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    process_id VARCHAR(36) NOT NULL,
    group_id VARCHAR(255) NOT NULL,
    central_process_id VARCHAR(255) NOT NULL,
    hold_id VARCHAR(255) NOT NULL,
    request_event_id VARCHAR(255) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    closed_at TIMESTAMP(6) NULL,
    cycle_attempt INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    last_failure VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_process_id VARCHAR(36) GENERATED ALWAYS AS
        (CASE WHEN closed_at IS NULL THEN process_id ELSE NULL END) STORED,
    CONSTRAINT fk_phone_process FOREIGN KEY (process_id) REFERENCES reservation_process(id),
    CONSTRAINT uq_phone_active_process UNIQUE (active_process_id),
    INDEX idx_phone_due (closed_at, published_at, next_attempt_at),
    INDEX idx_phone_history (process_id, sequence_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

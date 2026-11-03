CREATE TABLE reservation_processed_event (
    event_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (event_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

-- Conservar también IDs ignorados/rechazados/diferidos evita aplicar hechos históricos como nuevos.
INSERT INTO reservation_processed_event (event_id)
SELECT event_id FROM reservation_inbox WHERE event_id IS NOT NULL;

-- El payload, la huella y las clasificaciones anteriores eran exclusivamente diagnósticos/replay.
DROP TABLE reservation_inbox;

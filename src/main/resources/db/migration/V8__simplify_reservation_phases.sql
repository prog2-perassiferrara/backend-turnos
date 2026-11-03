-- Las intenciones pendientes no prueban si el POST llegó a ejecutarse.
UPDATE reservation_process SET phase = 'HOLD_PENDING'
WHERE phase IN ('CREATING_HOLD', 'HOLD_UNCERTAIN');
UPDATE reservation_process SET phase = 'CONFIRMATION_PENDING'
WHERE phase IN ('CONFIRMING', 'CONFIRMATION_UNCERTAIN');
-- Conserva reason_code, identificadores y closed_at; FAILED no acredita liberación central.
UPDATE reservation_process SET phase = 'FAILED'
WHERE phase IN ('REJECTED', 'HOLD_ATTEMPT_ABANDONED');

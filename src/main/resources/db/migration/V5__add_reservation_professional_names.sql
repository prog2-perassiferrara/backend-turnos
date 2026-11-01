-- Descripción histórica opcional; las filas existentes conservan identidad y progreso.
ALTER TABLE reservation_process
    ADD COLUMN professional_first_name VARCHAR(255) NULL,
    ADD COLUMN professional_last_name VARCHAR(255) NULL;

-- Recurso exclusivo de pruebas: no se incluye en el JAR ni en la imagen del backend.
CREATE TABLE setup_probe (
    id INTEGER PRIMARY KEY,
    description VARCHAR(100) NOT NULL
);

INSERT INTO setup_probe (id, description) VALUES (1, 'fictional setup data');

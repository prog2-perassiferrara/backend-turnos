package com.prog2.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend de turnos y reservas.
 * El arranque habilita únicamente la base técnica; no confirma disponibilidad ni reservas.
 */
@SpringBootApplication
public class AppointmentApplication {

    /**
     * Inicia el contexto Spring Boot y el servidor HTTP con configuración externa.
     *
     * @param args argumentos de configuración del proceso
     */
    public static void main(String[] args) {
        SpringApplication.run(AppointmentApplication.class, args);
    }
}

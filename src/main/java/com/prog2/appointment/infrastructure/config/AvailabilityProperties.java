package com.prog2.appointment.infrastructure.config;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;

/** Parámetros de consulta con defaults explícitos, independientes de la zona y configuración del equipo. */
public record AvailabilityProperties(ZoneId timeZone, Duration httpTimeout) {
    public AvailabilityProperties {
        if (timeZone == null) {
            throw new IllegalArgumentException("Invalid availability setting: app.availability.time-zone");
        }
        if (httpTimeout == null || httpTimeout.isNegative() || httpTimeout.isZero()
                || httpTimeout.toMillis() < 1 || httpTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid availability setting: app.availability.http-timeout");
        }
    }

    /**
     * @param environment propiedades resueltas por Spring, incluido YAML y sobrescritura por entorno
     * @return zona de atención y presupuesto HTTP representables; no lee .env
     * @throws IllegalArgumentException con el nombre del parámetro, sin encadenar su valor original
     */
    public static AvailabilityProperties from(Environment environment) {
        ZoneId zone;
        Duration timeout;
        try {
            zone = ZoneId.of(environment.getProperty("app.availability.time-zone", "America/Argentina/Buenos_Aires"));
        } catch (DateTimeException _) {
            throw new IllegalArgumentException("Invalid availability setting: app.availability.time-zone");
        }
        try {
            timeout = DurationStyle.detectAndParse(environment.getProperty("app.availability.http-timeout", "5s"));
            return new AvailabilityProperties(zone, timeout);
        } catch (IllegalArgumentException | ArithmeticException _) {
            throw new IllegalArgumentException("Invalid availability setting: app.availability.http-timeout");
        }
    }
}

package com.prog2.appointment.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;

/** Parámetros operativos con defaults; no almacena sesión, claves ni estado del consumidor. */
public record ReservationProperties(
        Duration httpTimeout,
        Duration metadataTimeout,
        int attempts,
        Duration retryDelay,
        Duration preparationCooldown) {
    public ReservationProperties {
        validate(httpTimeout);
        validate(metadataTimeout);
        validate(retryDelay);
        validate(preparationCooldown);
        if (attempts < 1 || attempts > 10) {
            throw new IllegalArgumentException("Invalid app.reservation.attempts");
        }
    }

    private static void validate(Duration value) {
        if (value == null || value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid reservation duration");
        }
    }

    public static ReservationProperties from(Environment environment) {
        try {
            return new ReservationProperties(
                    duration(environment, "http-timeout", "5s"),
                    duration(environment, "metadata-timeout", "5s"),
                    Integer.parseInt(environment.getProperty("app.reservation.attempts", "3")),
                    duration(environment, "retry-delay", "5s"),
                    duration(environment, "preparation-cooldown", "30s"));
        } catch (RuntimeException _) {
            throw new IllegalArgumentException("Invalid app.reservation configuration");
        }
    }

    private static Duration duration(Environment environment, String key, String fallback) {
        return DurationStyle.detectAndParse(
                environment.getProperty("app.reservation." + key, fallback));
    }
}

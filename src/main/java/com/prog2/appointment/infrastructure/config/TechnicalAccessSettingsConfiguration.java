package com.prog2.appointment.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;

/** Configuración compartida fuera de slices; no contiene estado de sesión ni reglas de recuperación. */
@Configuration(proxyBeanMethods = false)
public class TechnicalAccessSettingsConfiguration {
    @Bean
    TechnicalAccessProperties technicalAccessProperties(Environment environment) {
        return TechnicalAccessProperties.from(environment);
    }

    @Bean
    Clock technicalAccessClock() {
        return Clock.systemUTC();
    }

    @Bean
    ServiceTokenIssuer serviceTokenIssuer(TechnicalAccessProperties properties, Clock clock) {
        return new ServiceTokenIssuer(properties.privateKey(), clock);
    }
}

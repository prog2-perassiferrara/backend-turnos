package com.prog2.appointment.infrastructure.config;

import java.util.List;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Valida la configuración obligatoria antes de que Spring cree las conexiones a la base.
 * Los diagnósticos identifican la variable ausente o vacía, sin divulgar su valor.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfiguration {

    /**
     * Registra la validación temprana mediante el punto de extensión de Spring.
     * El procesador lanza {@link BeanCreationException} si falta una variable obligatoria
     * o contiene únicamente espacios. Los errores de conexión los verifica la inicialización de Flyway.
     *
     * @param environment configuración externa resuelta por Spring
     * @return procesador que impide crear beans de conexión con configuración incompleta
     */
    @Bean
    static BeanFactoryPostProcessor requiredDatabaseSettings(Environment environment) {
        return beanFactory -> {
            // Comprobar presencia sin incluir los valores sensibles en el diagnóstico.
            for (String setting : List.of("DB_NAME", "DB_USER", "DB_PASSWORD")) {
                if (!StringUtils.hasText(environment.getProperty(setting))) {
                    throw new BeanCreationException("databaseConfiguration",
                            "Required database setting is missing or blank: " + setting);
                }
            }
        };
    }
}

package com.prog2.appointment.infrastructure.security.config;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.Environment;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;

/**
 * Confianza JWT local y orígenes web explícitos; no descarga claves ni conserva la privada.
 * La privada ya fue validada por la configuración compartida y solo se usa para derivar su pública.
 * @param publicKey pública correspondiente a la privada externa de Turnos
 * @param allowedOrigins orígenes CORS inmutables, vacíos por defecto
 */
public record JwtSecurityProperties(RSAPublicKey publicKey, List<String> allowedOrigins) {
    private static final String CORS_SETTING = "CORS_ALLOWED_ORIGINS";

    public JwtSecurityProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    /**
     * @param access configuración compartida con privada RSA CRT previamente validada
     * @param environment propiedades del proceso, sin leer archivos .env
     * @return pública local y orígenes comprobados
     * @throws BeanCreationException si la pública no puede construirse o los orígenes son inválidos
     */
    public static JwtSecurityProperties from(TechnicalAccessProperties access, Environment environment) {
        try {
            var privateKey = access.privateKey();
            var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new JwtSecurityProperties(publicKey,
                    readOrigins(environment.getProperty(CORS_SETTING, "")));
        } catch (GeneralSecurityException _) {
            throw invalid(TechnicalAccessProperties.PRIVATE_KEY_PATH);
        }
    }

    private static List<String> readOrigins(String configured) {
        if (configured.isBlank()) {
            return List.of();
        }
        List<String> origins = Arrays.stream(configured.split(",", -1)).map(String::trim).toList();
        for (String origin : origins) {
            try {
                URI uri = URI.create(origin);
                boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
                if (!http || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) {
                    throw invalid(CORS_SETTING);
                }
            } catch (IllegalArgumentException _) {
                throw invalid(CORS_SETTING);
            }
        }
        return origins;
    }

    private static BeanCreationException invalid(String setting) {
        // No adjuntar causas ni valores: pueden contener datos de configuración privados.
        return new BeanCreationException("Invalid security setting: " + setting);
    }

    @Override
    public String toString() {
        return "JwtSecurityProperties[publicKey=RSA, allowedOriginCount=" + allowedOrigins.size() + "]";
    }
}

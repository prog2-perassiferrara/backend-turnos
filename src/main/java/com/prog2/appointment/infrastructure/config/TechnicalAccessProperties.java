package com.prog2.appointment.infrastructure.config;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.env.Environment;

/** Configuración externa compartida; valida límites locales sin imprimir rutas sensibles ni material de firma. */
public record TechnicalAccessProperties(URI catalogBaseUrl, URI centralBaseUrl, String expectedGroup,
        RSAPrivateCrtKey privateKey) {
    public static final String PRIVATE_KEY_PATH = "JWT_PRIVATE_KEY_PATH";

    /**
     * @param environment propiedades del proceso; no lee archivos .env
     * @return configuración completa con privada externa RSA PKCS#8 de al menos 2048 bits
     * @throws IllegalArgumentException si falta configuración o no cumple el contrato local
     */
    public static TechnicalAccessProperties from(Environment environment) {
        URI catalog = baseUrl(required(environment, "CATALOG_BASE_URL"), "CATALOG_BASE_URL");
        URI central = baseUrl(required(environment, "CATEDRA_BASE_URL"), "CATEDRA_BASE_URL");
        String group = required(environment, "CATEDRA_GROUP_ID");
        if (group.length() < 3 || group.length() > 100 || !group.matches("[a-z0-9]++(?:-[a-z0-9]++)*+")) {
            throw new IllegalArgumentException("Invalid CATEDRA_GROUP_ID");
        }
        return new TechnicalAccessProperties(catalog, central, group,
                readPrivateKey(required(environment, PRIVATE_KEY_PATH)));
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required integration setting is missing or blank: " + name);
        }
        return value;
    }

    private static URI baseUrl(String value, String name) {
        try {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(String location) {
        try {
            String pem = Files.readString(Path.of(location));
            if (!pem.startsWith("-----BEGIN PRIVATE KEY-----") || !pem.strip().endsWith("-----END PRIVATE KEY-----")) {
                throw new IllegalArgumentException();
            }
            byte[] bytes = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
            if (!(key instanceof RSAPrivateCrtKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException();
            }
            return rsa;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException _) {
            // Nunca encadenar excepciones de parsing: pueden contener parte del secreto leído.
            throw new IllegalArgumentException("Invalid " + PRIVATE_KEY_PATH);
        }
    }

    @Override
    public String toString() {
        return "TechnicalAccessProperties[REDACTED]";
    }
}

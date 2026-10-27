package com.prog2.appointment.security;

import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.integration.TestIntegrationSupport;

/** Firmas reales de prueba con claves efímeras; nunca carga material privado del usuario. */
final class JwtTestTokens {
    static final KeyPair KEYS = TestIntegrationSupport.KEYS;
    static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private JwtTestTokens() {
    }

    static TechnicalAccessProperties access() {
        return new TechnicalAccessProperties(URI.create("http://fictional-catalog.test"),
                URI.create("http://fictional-central.test"), "fictional-group",
                (RSAPrivateCrtKey) KEYS.getPrivate());
    }

    static Map<String, Object> claims() {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", "backend-turnos");
        claims.put("aud", List.of("catalog", "turnos"));
        claims.put("sub", "42");
        claims.put("token_use", "user");
        claims.put("auth", "ROLE_USER");
        claims.put("iat", NOW.getEpochSecond());
        claims.put("exp", NOW.plusSeconds(86400).getEpochSecond());
        return claims;
    }

    static String signed(Map<String, Object> claims) throws JOSEException {
        return signed(claims, KEYS, JWSAlgorithm.RS256);
    }

    static String signed(Map<String, Object> claims, KeyPair keys, JWSAlgorithm algorithm) throws JOSEException {
        // Payload conserva también tipos inválidos para comprobar la frontera de datos externos.
        var jwt = new JWSObject(new JWSHeader(algorithm), new Payload(claims));
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }
}

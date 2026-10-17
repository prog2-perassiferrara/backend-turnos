package com.prog2.appointment.infrastructure.security.jwt;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Firma credenciales propias de servicio; no crea JWT de cátedra ni autentica usuarios de KMP. */
public final class ServiceTokenIssuer {
    private final JwtEncoder encoder;
    private final Clock clock;

    /**
     * @param privateKey privada validada de Turnos; la pública correspondiente se distribuye externamente
     * @param clock reloj UTC para emisión y pruebas deterministas
     */
    public ServiceTokenIssuer(RSAPrivateCrtKey privateKey, Clock clock) {
        try {
            var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            this.encoder = NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                    .algorithm(SignatureAlgorithm.RS256).build();
        } catch (GeneralSecurityException _) {
            throw new IllegalArgumentException("Invalid service signing key");
        }
        this.clock = clock;
    }

    /** @return JWT de servicio nuevo, RS256, de cinco minutos; nunca se guarda ni se registra */
    public String issue() {
        var issued = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").subject("backend-turnos")
                .audience(java.util.List.of("catalog")).issuedAt(issued)
                .expiresAt(issued.plus(Duration.ofMinutes(5)))
                .claim("token_use", "service").claim("auth", "ROLE_CATALOG_INTEGRATION").build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}

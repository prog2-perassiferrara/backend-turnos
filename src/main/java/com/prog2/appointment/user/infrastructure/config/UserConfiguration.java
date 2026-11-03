package com.prog2.appointment.user.infrastructure.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;

/** Configura el hash de contraseñas y la firma de tokens; los casos de uso se descubren por Spring. */
@Configuration(proxyBeanMethods = false)
public class UserConfiguration {
    @Bean
    PasswordEncoder userPasswordEncoder() {
        // Sal 16 B, hash 32 B; memoria en KiB. El perfil persistido se verifica en el adaptador.
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    JwtEncoder userJwtEncoder(TechnicalAccessProperties properties) {
        try {
            var key = properties.privateKey();
            var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
            return NimbusJwtEncoder.withKeyPair(publicKey, key).algorithm(SignatureAlgorithm.RS256).build();
        } catch (GeneralSecurityException _) {
            throw new IllegalArgumentException("Invalid user signing key");
        }
    }
}

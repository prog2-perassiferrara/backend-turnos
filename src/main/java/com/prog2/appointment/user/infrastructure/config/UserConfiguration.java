package com.prog2.appointment.user.infrastructure.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.user.application.usecases.AuthenticateUserUseCaseImpl;
import com.prog2.appointment.user.application.usecases.GetUserProfileUseCaseImpl;
import com.prog2.appointment.user.application.usecases.RegisterUserUseCaseImpl;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.domain.ports.in.GetUserProfileUseCase;
import com.prog2.appointment.user.domain.ports.in.RegisterUserUseCase;
import com.prog2.appointment.user.domain.ports.out.PasswordHasher;
import com.prog2.appointment.user.domain.ports.out.UserAccountRepository;
import com.prog2.appointment.user.domain.ports.out.UserTokenIssuer;
import com.prog2.appointment.user.infrastructure.security.NimbusUserTokenIssuerAdapter;
import com.prog2.appointment.user.infrastructure.security.SpringPasswordHasherAdapter;

/** Wiring del slice; utiliza privada/reloj compartidos sin acoplar los casos de uso a infraestructura. */
@Configuration(proxyBeanMethods = false)
public class UserConfiguration {
    @Bean
    GetUserProfileUseCase getUserProfileUseCase(UserAccountRepository accounts) {
        return new GetUserProfileUseCaseImpl(accounts);
    }

    @Bean
    PasswordEncoder userPasswordEncoder() {
        // Sal 16 B, hash 32 B; memoria en KiB. El perfil persistido se verifica en el adaptador.
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    PasswordHasher passwordHasher(PasswordEncoder encoder) {
        return new SpringPasswordHasherAdapter(encoder);
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

    @Bean
    UserTokenIssuer userTokenIssuer(JwtEncoder encoder, Clock clock) {
        return new NimbusUserTokenIssuerAdapter(encoder, clock);
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserAccountRepository accounts, PasswordHasher passwords) {
        return new RegisterUserUseCaseImpl(accounts, passwords);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserAccountRepository accounts, PasswordHasher passwords,
            UserTokenIssuer tokens) {
        return new AuthenticateUserUseCaseImpl(accounts, passwords, tokens);
    }
}

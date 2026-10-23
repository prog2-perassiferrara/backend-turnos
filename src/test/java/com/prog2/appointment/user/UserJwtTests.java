package com.prog2.appointment.user;

import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.prog2.appointment.integration.TestIntegrationSupport;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.domain.model.UserAccount;
import com.prog2.appointment.user.infrastructure.security.NimbusUserTokenIssuerAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Firma y claims reales con claves efímeras; no utiliza la privada externa del usuario. */
class UserJwtTests {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    @ParameterizedTest
    @ValueSource(longs = {86400, 2592000})
    void signsStableIdentityPermissionsAndExactLifetime(long seconds) throws java.text.ParseException, com.nimbusds.jose.JOSEException {
        var keys = TestIntegrationSupport.KEYS;
        var encoder = NimbusJwtEncoder.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate())
                .algorithm(SignatureAlgorithm.RS256).build();
        var issuer = new NimbusUserTokenIssuerAdapter(encoder, Clock.fixed(NOW, ZoneOffset.UTC));
        var token = issuer.issue(42, Set.of("ROLE_USER"), Duration.ofSeconds(seconds));
        var jwt = SignedJWT.parse(token.value());
        var claims = jwt.getJWTClaimsSet();
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(claims.getIssuer()).isEqualTo("backend-turnos");
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getAudience()).containsExactly("catalog", "turnos");
        assertThat(claims.getStringClaim("token_use")).isEqualTo("user");
        assertThat(claims.getStringClaim("auth")).isEqualTo("ROLE_USER");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(seconds));
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(seconds));
        assertThat(token.toString()).doesNotContain(token.value());
        var otherKeys = TestIntegrationSupport.generateKeys(2048);
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) otherKeys.getPublic()))).isFalse();
    }

    @Test
    void signingFailureDoesNotExposeEncoderCause() {
        var issuer = new NimbusUserTokenIssuerAdapter(_ -> {
            throw new JwtEncodingException("fictional-sensitive-encoder-message");
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        var roles = Set.of("ROLE_USER");
        var lifetime = Duration.ofHours(24);
        assertThatThrownBy(() -> issuer.issue(1, roles, lifetime))
                .isInstanceOf(UserSecurityException.class).hasNoCause()
                .hasMessageNotContaining("fictional-sensitive");
    }

    @Test
    void accountRepresentationDoesNotRevealHash() {
        var account = new UserAccount(1, "fictional-protected-hash", true, Set.of("ROLE_USER"));
        assertThat(account.toString()).doesNotContain(account.passwordHash());
    }

    @Test
    void signedUserTokenExpiresWithAgreedConsumerClockTolerance() {
        var keys = TestIntegrationSupport.KEYS;
        var encoder = NimbusJwtEncoder.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate())
                .algorithm(SignatureAlgorithm.RS256).build();
        var token = new NimbusUserTokenIssuerAdapter(encoder, Clock.fixed(NOW, ZoneOffset.UTC))
                .issue(42, Set.of("ROLE_USER"), Duration.ofHours(24));
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setAllowEmptyExpiryClaim(false);
        timestamps.setClock(Clock.fixed(token.expiresAt().plusSeconds(29), ZoneOffset.UTC));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator("backend-turnos"), new JwtAudienceValidator("catalog")));
        String encodedToken = token.value();
        assertThat(decoder.decode(encodedToken).getSubject()).isEqualTo("42");
        timestamps.setClock(Clock.fixed(token.expiresAt().plusSeconds(31), ZoneOffset.UTC));
        assertThatThrownBy(() -> decoder.decode(encodedToken)).isInstanceOf(JwtValidationException.class);
    }
}

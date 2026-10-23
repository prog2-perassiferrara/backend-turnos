package com.prog2.appointment.user.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.domain.model.UserToken;
import com.prog2.appointment.user.domain.ports.out.UserTokenIssuer;

/** Adapter de firma RS256 de usuarios; mantiene separado el perfil técnico existente. */
public final class NimbusUserTokenIssuerAdapter implements UserTokenIssuer {
    private final JwtEncoder encoder;
    private final Clock clock;

    public NimbusUserTokenIssuerAdapter(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public UserToken issue(long userId, Set<String> authorities, Duration lifetime) {
        var issued = clock.instant();
        var expires = issued.plus(lifetime);
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").subject(Long.toString(userId))
                .audience(List.of("catalog", "turnos")).issuedAt(issued).expiresAt(expires)
                .claim("token_use", "user")
                .claim("auth", authorities.stream().sorted().collect(Collectors.joining(" "))).build();
        try {
            String value = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
            return new UserToken(value, expires);
        } catch (JwtEncodingException _) {
            throw new UserSecurityException();
        }
    }
}

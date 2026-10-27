package com.prog2.appointment.infrastructure.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Comprueba el contrato de usuario después de verificar firma, emisor, audiencia y vigencia.
 * Valida datos externos en la frontera de seguridad; no consulta cuentas ni decide propiedad.
 */
public final class JwtContractValidator implements OAuth2TokenValidator<Jwt> {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Pattern AUTHORITIES = Pattern.compile("ROLE_[A-Z0-9_]++(?: ROLE_[A-Z0-9_]++)*+");
    private static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "JWT does not satisfy the agreed contract", null));
    private final Clock clock;

    /** @param clock reloj UTC para comprobar emisión futura, sustituible en pruebas */
    public JwtContractValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Exige sujeto de cuenta, perfil user, autoridades y tiempos coherentes.
     * Una cadena de autoridades vacía autentica, pero no otorga permisos de ruta.
     * @param jwt token cuya firma fue comprobada por el decoder
     * @return resultado genérico, sin copiar claims ni credenciales al diagnóstico
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object subject = jwt.getClaims().get("sub");
        Object authorities = jwt.getClaims().get("auth");
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        // Las comprobaciones de tipo validan datos externos; no seleccionan comportamiento de negocio.
        if (!(subject instanceof String value) || !isAccountId(value)
                || !"user".equals(jwt.getClaims().get("token_use"))
                || !(authorities instanceof String roles)
                || (!roles.isEmpty() && !AUTHORITIES.matcher(roles).matches())
                || issued == null || expires == null || !expires.isAfter(issued)
                || issued.isAfter(clock.instant().plus(CLOCK_SKEW))) {
            return INVALID;
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isAccountId(String value) {
        try {
            long id = Long.parseLong(value);
            return id > 0 && Long.toString(id).equals(value);
        } catch (NumberFormatException _) {
            return false;
        }
    }
}

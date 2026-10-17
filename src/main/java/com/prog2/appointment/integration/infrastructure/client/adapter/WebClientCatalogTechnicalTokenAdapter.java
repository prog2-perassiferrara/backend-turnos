package com.prog2.appointment.integration.infrastructure.client.adapter;

import java.text.ParseException;
import com.nimbusds.jwt.JWTParser;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import com.prog2.appointment.integration.domain.model.TechnicalToken;
import com.prog2.appointment.integration.domain.ports.out.CatalogTechnicalTokenGateway;
import com.prog2.appointment.integration.infrastructure.client.dto.TechnicalTokenResponse;

/** Adapter del proveedor protegido; firma una credencial propia nueva por solicitud. */
public final class WebClientCatalogTechnicalTokenAdapter implements CatalogTechnicalTokenGateway {
    private final SafeIntegrationHttpClient client;
    private final ServiceTokenIssuer issuer;

    public WebClientCatalogTechnicalTokenAdapter(SafeIntegrationHttpClient client, ServiceTokenIssuer issuer) {
        this.client = client;
        this.issuer = issuer;
    }

    @Override
    public TechnicalToken fetchToken() {
        try {
            var response = client.get("/api/internal/catedra/token", issuer.issue(), TechnicalTokenResponse.class);
            if (response.idToken() == null || response.idToken().isBlank()) {
                throw new IllegalArgumentException();
            }
            var claims = JWTParser.parse(response.idToken()).getJWTClaimsSet();
            // Leer exp no verifica firma: este JWT viene del proveedor protegido y lo valida cátedra.
            var expiration = claims.getExpirationTime();
            return new TechnicalToken(response.idToken(), expiration == null ? null : expiration.toInstant());
        } catch (ParseException | IllegalArgumentException _) {
            throw new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE);
        }
    }
}

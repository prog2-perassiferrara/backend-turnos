package com.prog2.appointment.infrastructure.security.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.jwt.JwtContractValidator;
import com.prog2.appointment.infrastructure.security.web.SecurityProblemHandler;

/**
 * Configura seguridad servlet transversal con filtros y criptografía de Spring Security.
 * La validación de usuario es local; el acceso técnico saliente conserva su contrato independiente.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    JwtSecurityProperties jwtSecurityProperties(TechnicalAccessProperties access, Environment environment) {
        return JwtSecurityProperties.from(access, environment);
    }

    /**
     * @param properties pública derivada de la privada local validada
     * @param clock reloj UTC compartido, sin consultar proveedores remotos
     * @return decoder de usuario con RS256 y contrato de Turnos explícitos
     */
    @Bean
    JwtDecoder jwtDecoder(JwtSecurityProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withPublicKey(properties.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator("backend-turnos"), new JwtAudienceValidator("turnos"),
                new JwtContractValidator(clock)));
        return decoder;
    }

    /**
     * Declara operaciones públicas exactas, contexto por petición y denegación por defecto.
     * CSRF se desactiva por el contrato bearer explícito, sin cookies de autenticación.
     * @param http constructor servlet del framework
     * @param properties orígenes web admitidos
     * @param mapper serializador de errores sin datos de las excepciones originales
     * @return cadena presente únicamente en aplicaciones web servlet
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtSecurityProperties properties,
            JsonMapper mapper) {
        var problems = new SecurityProblemHandler(mapper);
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("auth");
        authorities.setAuthorityPrefix("");
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(authorities);
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource(properties)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
                        .authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(routes -> routes
                        // Permite el despacho interno de errores, no una petición externa a /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/register", "/api/authenticate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/internal/**").denyAll()
                        .requestMatchers("/api/**").hasAuthority("ROLE_USER")
                        .anyRequest().denyAll())
                .build();
    }

    private static UrlBasedCorsConfigurationSource corsSource(JwtSecurityProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}

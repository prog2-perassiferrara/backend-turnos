package com.prog2.appointment.integration.infrastructure.client.adapter;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.integration.domain.enums.AccessFailure;
import com.prog2.appointment.integration.domain.exception.IntegrationGatewayException;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Transporte compartido de los adaptadores: limita tiempos y traduce fallos sin propagar cuerpos o causas sensibles. */
public final class SafeIntegrationHttpClient {
    private final WebClient client;
    private final Duration totalTimeout;
    private final boolean central;

    /**
     * @param baseUrl único destino autorizado para este cliente
     * @param central distingue errores de JWT central frente a la identidad propia de servicio
     * @param timeout límites de conexión/lectura; el total es el doble
     */
    public SafeIntegrationHttpClient(URI baseUrl, boolean central, Duration timeout) {
        this.central = central;
        this.totalTimeout = timeout.multipliedBy(2);
        var transport = HttpClient.create().disableRetry(true).followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout);
        this.client = WebClient.builder().baseUrl(baseUrl.toString())
                .clientConnector(new ReactorClientHttpConnector(transport)).build();
    }

    /**
     * @param path ruta fija del contrato, no elegida por KMP
     * @param bearer credencial correspondiente exclusivamente a este proveedor
     * @param responseType DTO esperado
     * @return cuerpo de un 200 contractual completo
     * @throws IntegrationGatewayException con un motivo seguro, sin conservar cuerpo/cadena causal
     */
    public <T> T get(String path, String bearer, Class<T> responseType) {
        try {
            T result = client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(response -> {
                        int status = response.statusCode().value();
                        if (status == 200) {
                            return response.bodyToMono(responseType)
                                    .onErrorMap(DecodingException.class,
                                            _ -> new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE));
                        }
                        return response.bodyToMono(ProblemCode.class).onErrorReturn(new ProblemCode(null))
                                .defaultIfEmpty(new ProblemCode(null))
                                .flatMap(problem -> Mono.error(new IntegrationGatewayException(classify(status, problem.code()))));
                    }).block(totalTimeout);
            if (result == null) {
                throw new IntegrationGatewayException(AccessFailure.INVALID_RESPONSE);
            }
            return result;
        } catch (IntegrationGatewayException exception) {
            throw exception;
        } catch (RuntimeException _) {
            throw new IntegrationGatewayException(AccessFailure.TEMPORARY_UNAVAILABLE);
        }
    }

    private AccessFailure classify(int status, String code) {
        if (status == 500 || status == 503) {
            return AccessFailure.TEMPORARY_UNAVAILABLE;
        }
        if (status == 401) {
            return central ? AccessFailure.CENTRAL_TOKEN_REJECTED : AccessFailure.CATALOG_AUTHENTICATION;
        }
        if ("STUDENT_INTEGRATION_NOT_FOUND".equals(code)) {
            return AccessFailure.INTEGRATION_NOT_FOUND;
        }
        if (status == 403) {
            return AccessFailure.ACCESS_DENIED;
        }
        return AccessFailure.INVALID_RESPONSE;
    }

    /** Solo se conserva el código funcional; los otros campos del problema se descartan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProblemCode(String code) {
    }
}

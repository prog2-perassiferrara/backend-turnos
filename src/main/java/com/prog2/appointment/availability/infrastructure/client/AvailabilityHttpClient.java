package com.prog2.appointment.availability.infrastructure.client;

import java.time.Duration;
import java.util.function.BiFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import reactor.core.publisher.Mono;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;

/** GET acotado compartido por los dos adaptadores; la clasificación del proveedor no sale de infraestructura. */
public final class AvailabilityHttpClient {
    private final WebClient client;
    private final Duration totalTimeout;
    private final AvailabilityFailure unavailable;

    public AvailabilityHttpClient(WebClient client, Duration totalTimeout, AvailabilityFailure unavailable) {
        this.client = client;
        this.totalTimeout = totalTimeout;
        this.unavailable = unavailable;
    }

    /**
     * @param path ruta contractual construida con identificadores/fechas validados
     * @param bearer credencial correspondiente solo a este destino
     * @param responseType DTO completo esperado para un 200
     * @param classify traduce HTTP/code a motivos propios sin decidir por mensajes remotos
     * @param <T> tipo de la respuesta externa
     * @return cuerpo decodificado dentro del presupuesto total, nunca null
     * @throws AvailabilityGatewayException sin conservar secretos o causas del transporte
     */
    public <T> T get(String path, String bearer, Class<T> responseType,
            BiFunction<Integer, String, AvailabilityFailure> classify) {
        try {
            T result = client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                    .accept(MediaType.APPLICATION_JSON).exchangeToMono(response -> {
                        int status = response.statusCode().value();
                        if (status == 200) {
                            return response.bodyToMono(responseType);
                        }
                        return response.bodyToMono(ProblemCode.class).onErrorReturn(new ProblemCode(null))
                                .defaultIfEmpty(new ProblemCode(null))
                                .flatMap(problem -> Mono.error(
                                        new AvailabilityGatewayException(classify.apply(status, problem.code()))));
                    }).block(totalTimeout);
            if (result == null) {
                throw new AvailabilityGatewayException(unavailable);
            }
            return result;
        } catch (AvailabilityGatewayException failure) {
            throw failure;
        } catch (RuntimeException _) {
            // El cliente puede incluir el destino o parte del cuerpo en su excepción original.
            throw new AvailabilityGatewayException(unavailable);
        }
    }

    /** Solo se conserva code; los demás campos del error externo no se utilizan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemCode(String code) { }
}

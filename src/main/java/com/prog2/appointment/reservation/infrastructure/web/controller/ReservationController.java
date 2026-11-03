package com.prog2.appointment.reservation.infrastructure.web.controller;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.FORBIDDEN_RESPONSE;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.PROBLEM_SCHEMA;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.UNAUTHORIZED_RESPONSE;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.ports.in.ListOwnReservationsUseCase;
import com.prog2.appointment.reservation.infrastructure.web.dto.OwnReservationsResponse;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;
import com.prog2.appointment.reservation.domain.ports.in.CancelReservationUseCase;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.enums.CancellationState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import com.prog2.appointment.reservation.infrastructure.web.dto.SubmitPhoneBody;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.infrastructure.web.dto.ReservationProcessResponse;
import com.prog2.appointment.reservation.infrastructure.web.dto.StartReservationBody;
import com.prog2.appointment.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import jakarta.validation.Valid;

/** Adaptador entrante; los filtros existentes validan usuario/ROLE_USER antes del caso de uso. */
@RestController
@RequiredArgsConstructor
public final class ReservationController {
    private final StartReservationUseCase start;
    private final ReservationDtoMapper mapper;
    private final SubmitReservationPhoneUseCase submit;
    private final GetReservationProcessUseCase get;
    private final ListOwnReservationsUseCase list;
    private final CancelReservationUseCase cancel;

    /**
     * @return pendiente e historial propios; el propietario procede exclusivamente del JWT
     */
    @Operation(summary = "Listar reservas propias", description = "JWT de usuario. Devuelve el pendiente y el historial paginado; el propietario procede exclusivamente del JWT.", tags = {"Reservas propias"})
    @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "400", description = "INVALID_RESERVATION_PAGE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "STORAGE_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @GetMapping(path = "/api/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnReservationsResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Página solicitada, desde cero.",
                    schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "Tamaño de página entre 1 y 100.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") String size) {
        int selectedPage;
        int selectedSize;
        try {
            selectedPage = Integer.parseInt(page);
            selectedSize = Integer.parseInt(size);
        } catch (NumberFormatException _) {
            throw new ReservationOperationException(
                    com.prog2.appointment.reservation.domain.enums.ReservationFailure
                            .INVALID_RESERVATION_PAGE,
                    null);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        mapper.toResponse(
                                list.list(
                                        Long.parseLong(jwt.getSubject()),
                                        selectedPage,
                                        selectedSize)));
    }

    /**
     * @return 202 por nueva operación o 200 por repetición propia, sin ejecutar nuevamente sus
     *     efectos
     */
    @Operation(summary = "Iniciar reserva", description = "JWT de usuario. Inicia el proceso desde la selección y devuelve el progreso durable; 202 no acredita una reserva confirmada.", tags = {"Reservas propias"})
    @ApiResponse(responseCode = "202", description = "Proceso iniciado; el resultado se consulta por su identificador.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "200", description = "Repetición propia equivalente, sin ejecutar nuevamente sus efectos.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "400", description = "INVALID_RESERVATION_SELECTION o PROFILE_NOT_RESERVABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "409", description = "ACTIVE_RESERVATION_PROCESS, REQUEST_ID_CONFLICT, SLOT_NOT_AVAILABLE, HOLD_EXPIRED o CONFIRMATION_REJECTED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "HOLD_CREATION_UNCERTAIN, CONFIRMATION_UNCERTAIN, CATALOG_UNAVAILABLE, OCCUPANCIES_UNAVAILABLE, TECHNICAL_INTEGRATION_UNAVAILABLE, MESSAGING_NOT_READY o STORAGE_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @PostMapping(path = "/api/reservation-processes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> start(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartReservationBody body) {
        var result = start.start(Long.parseLong(jwt.getSubject()), mapper.toRequest(body));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(result.process()));
    }

    /**
     * @return 202 solo después del commit local; un replay equivalente devuelve 200
     */
    @Operation(summary = "Enviar teléfono", description = "JWT de usuario. Corrige el teléfono rechazado dentro de la ventana vigente; 202 no acredita todavía la confirmación central.", tags = {"Reservas propias"})
    @ApiResponse(responseCode = "202", description = "Teléfono aceptado localmente; el resultado se reconcilia por REST y Kafka.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "200", description = "Repetición propia equivalente, sin ejecutar nuevamente sus efectos.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "400", description = "INVALID_PHONE_NUMBER",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "404", description = "RESERVATION_PROCESS_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "409", description = "PHONE_NOT_REQUESTED, PHONE_SUBMISSION_ACTIVE o PHONE_WINDOW_CLOSED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "PHONE_PUBLICATION_UNCERTAIN o STORAGE_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @PostMapping(
            path = "/api/reservation-processes/{processId}/phone",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> phone(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID processId,
            @Valid @RequestBody SubmitPhoneBody body) {
        var result =
                submit.submit(
                        Long.parseLong(jwt.getSubject()),
                        processId,
                        new SubmitPhoneRequest(body.requestId(), body.phoneNumber()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(result.status()));
    }

    /**
     * @return progreso propio vigente o histórico, sin exponer teléfono ni IDs centrales
     */
    @Operation(summary = "Consultar proceso propio", description = "JWT de usuario. Devuelve el progreso durable local del proceso propio, sin teléfono ni identificadores centrales.", tags = {"Reservas propias"})
    @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "INVALID_RESERVATION_SELECTION: processId no es un UUID válido",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "404", description = "RESERVATION_PROCESS_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "STORAGE_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @GetMapping(
            path = "/api/reservation-processes/{processId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID processId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(get.get(Long.parseLong(jwt.getSubject()), processId)));
    }
    /** @return estado conocido e intención propia; 202 no acredita liberación central */
    @Operation(summary = "Cancelar reserva propia", description = "JWT de usuario. Registra la intención de cancelación; 202 indica un intento pendiente y no acredita la liberación central.", tags = {"Reservas propias"})
    @ApiResponse(responseCode = "202", description = "Cancelación pendiente de reconciliación.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "200", description = "Estado conocido sin cancelación pendiente.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "404", description = "RESERVATION_PROCESS_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "409", description = "CANCELLATION_NOT_ALLOWED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "CANCELLATION_REJECTED, CANCELLATION_UNCERTAIN o STORAGE_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @PostMapping(path = "/api/reservation-processes/{processId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> cancel(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID processId) {
        return cancellationResponse(cancel.cancel(Long.parseLong(jwt.getSubject()), processId));
    }

    private ResponseEntity<ReservationProcessResponse> cancellationResponse(ReservationStatus status) {
        var progress = status.process().cancellation();
        boolean pending = progress != null && progress == CancellationState.PENDING;
        return ResponseEntity.status(pending ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(mapper.toResponse(status));
    }

}

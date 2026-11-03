package com.prog2.appointment.user.infrastructure.web.controller;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.PROBLEM_SCHEMA;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.user.domain.ports.in.RegisterUserUseCase;
import com.prog2.appointment.user.infrastructure.web.dto.RegistrationRequest;
import com.prog2.appointment.user.infrastructure.web.mapper.RegistrationDtoMapper;

/** Adaptador HTTP público de CU-01; el 201 confirma el alta antes del login automático del cliente. */
@RestController
@RequiredArgsConstructor
public final class RegistrationController {
    private final RegisterUserUseCase registration;
    private final RegistrationDtoMapper mapper;

    /** @return 201 sin cuerpo ni JWT; KMP ejecuta luego authenticate sin otra acción de la persona */
    @Operation(summary = "Registrar usuario", description = "Alta pública compatible con JHipster. Crea la cuenta activa sin emitir JWT.", tags = {"Cuenta"})
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Usuario creado.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o USER_IDENTIFIER_ALREADY_USED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "USER_REGISTRATION_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @PostMapping("/api/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest request) {
        registration.register(mapper.toDomain(request), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).build();
    }
}

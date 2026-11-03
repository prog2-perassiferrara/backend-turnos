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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.infrastructure.web.dto.AuthenticationRequest;
import com.prog2.appointment.user.infrastructure.web.dto.AuthenticationResponse;

/** Adaptador HTTP público de CU-02, reutilizado por el login automático y el independiente. */
@RestController
@RequiredArgsConstructor
public final class AuthenticationController {
    private final AuthenticateUserUseCase authentication;

    /** @return id_token y cabecera bearer de la misma cuenta autenticada; sin cookie ni sesión HTTP */
    @Operation(summary = "Autenticar usuario", description = "Entrega el JWT propio de usuario y la cabecera bearer. Los errores de credenciales no revelan la causa.", tags = {"Cuenta"})
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Autenticación exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "AUTHENTICATION_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @PostMapping("/api/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request) {
        var token = authentication.authenticate(request.username(), request.password(), request.rememberMe());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                .body(new AuthenticationResponse(token.value()));
    }
}

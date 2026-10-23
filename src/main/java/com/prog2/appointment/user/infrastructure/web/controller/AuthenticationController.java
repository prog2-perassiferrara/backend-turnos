package com.prog2.appointment.user.infrastructure.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.infrastructure.web.dto.AuthenticationRequest;
import com.prog2.appointment.user.infrastructure.web.dto.AuthenticationResponse;

/** Adaptador HTTP público de CU-02, reutilizado por el login automático y el independiente. */
@RestController
public final class AuthenticationController {
    private final AuthenticateUserUseCase authentication;

    public AuthenticationController(AuthenticateUserUseCase authentication) {
        this.authentication = authentication;
    }

    /** @return id_token y cabecera bearer de la misma cuenta autenticada; sin cookie ni sesión HTTP */
    @PostMapping("/api/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request) {
        var token = authentication.authenticate(request.username(), request.password(), request.rememberMe());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                .body(new AuthenticationResponse(token.value()));
    }
}

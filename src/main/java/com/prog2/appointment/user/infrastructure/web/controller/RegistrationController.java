package com.prog2.appointment.user.infrastructure.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.user.domain.ports.in.RegisterUserUseCase;
import com.prog2.appointment.user.infrastructure.web.dto.RegistrationRequest;
import com.prog2.appointment.user.infrastructure.web.mapper.RegistrationDtoMapper;

/** Adaptador HTTP público de CU-01; el 201 confirma el alta antes del login automático del cliente. */
@RestController
public final class RegistrationController {
    private final RegisterUserUseCase registration;
    private final RegistrationDtoMapper mapper;

    public RegistrationController(RegisterUserUseCase registration, RegistrationDtoMapper mapper) {
        this.registration = registration;
        this.mapper = mapper;
    }

    /** @return 201 sin cuerpo ni JWT; KMP ejecuta luego authenticate sin otra acción de la persona */
    @PostMapping("/api/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest request) {
        registration.register(mapper.toDomain(request), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).build();
    }
}

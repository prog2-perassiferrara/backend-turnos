package com.prog2.appointment.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.Min;

/** Body opcional: únicamente reintento explícito con la versión que mostró la consulta propia. */
public record CancellationRequestBody(@Min(0) Integer retryVersion) {}

package com.prog2.appointment.reservation.infrastructure.web.dto;


/** Vista HTTP propia con pendiente, historial paginado local. */
public record OwnReservationsResponse(
        ReservationProcessResponse pending,
        ReservationHistoryResponse history) {}

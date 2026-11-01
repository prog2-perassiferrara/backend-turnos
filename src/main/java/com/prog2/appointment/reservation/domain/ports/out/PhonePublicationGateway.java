package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.PhoneSubmission;

/** Publicación v1: retornar solo acredita recepción por el broker, nunca la reserva. */
public interface PhonePublicationGateway {
    /**
     * @param submission respuesta confirmada en persistencia, con contenido inmutable
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationGatewayException si no
     *     se verifica el ACK
     */
    void publish(PhoneSubmission submission);
}

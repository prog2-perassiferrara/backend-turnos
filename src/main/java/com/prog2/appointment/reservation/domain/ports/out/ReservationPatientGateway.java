package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.ReservationPatient;

/** Lectura del perfil propio; ninguna contraseña o entidad atraviesa este puerto. */
public interface ReservationPatientGateway {
    /**
     * @param ownerId cuenta autenticada
     * @return perfil activo compatible con la reserva
     */
    ReservationPatient get(long ownerId);
}

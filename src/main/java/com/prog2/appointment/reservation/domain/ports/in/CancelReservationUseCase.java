package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import java.util.UUID;

/** CU-08: solicitar cancelación de una reserva propia confirmada. */
public interface CancelReservationUseCase {
    /**
     * @param ownerId propietario autenticado
     * @param processId referencia local
     * @param retryVersion null para solicitar/repetir; versión observada para reintento explícito
     * @return resultado conocido e intención conservada; no implica cancelación completada
     */
    ReservationStatus cancel(long ownerId, UUID processId, Integer retryVersion);
}

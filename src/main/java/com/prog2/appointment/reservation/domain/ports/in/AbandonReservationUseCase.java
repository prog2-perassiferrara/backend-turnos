package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import java.util.UUID;

/** CU-10: abandono explícito que también autoriza cancelar una confirmación concurrente. */
public interface AbandonReservationUseCase {
    /**
     * @param ownerId propietario autenticado
     * @param processId referencia local
     * @param retryVersion null para solicitar/repetir; versión observada para reintento explícito
     * @return resultado conocido sin atribuir éxito a una intención todavía pendiente
     */
    ReservationStatus abandon(long ownerId, UUID processId, Integer retryVersion);
}

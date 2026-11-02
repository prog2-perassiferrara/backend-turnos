package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import java.util.UUID;

/** Recuperación de intenciones ya autorizadas; no crea otro ciclo por sí misma. */
public interface RecoverReservationCancellationsUseCase {
    /** @return vista actual tras intentar reclamar y ejecutar como máximo un intento */
    ReservationStatus recover(UUID processId);

    /** Procesa un lote acotado de intenciones cuyo presupuesto y fecha permiten continuar. */
    void recoverDue();
}

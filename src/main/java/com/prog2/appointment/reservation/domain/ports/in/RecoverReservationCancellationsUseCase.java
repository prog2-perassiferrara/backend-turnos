package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import java.util.UUID;

/** Recuperación de intenciones autorizadas; RETRY_REQUIRED solo consulta, sin reabrir POST. */
public interface RecoverReservationCancellationsUseCase {
    /** @return vista actual tras reclamar y ejecutar hasta tres POST con reconciliación previa */
    ReservationStatus recover(UUID processId);

}

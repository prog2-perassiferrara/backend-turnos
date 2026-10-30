package com.prog2.appointment.reservation.domain.ports.out;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;

/** Repository de procesos e inbox; todas sus escrituras regresan después del commit verificable. */
public interface ReservationProcessRepository {
    Optional<ReservationProcess> find(UUID id);

    Optional<ReservationProcess> findPending(long ownerId);

    /**
     * @param candidate intención con marca de escritura propia
     * @return proceso y permiso de ejecutar solo para la inserción propia confirmada
     */
    BeginResult begin(ReservationProcess candidate);

    /**
     * Aplica una transformación pura sobre la última versión, sin HTTP dentro de la transacción.
     *
     * @param id proceso local
     * @param change transición determinista, sin efectos externos
     * @return última versión confirmada
     */
    ReservationProcess update(UUID id, UnaryOperator<ReservationProcess> change);

    /** Guarda recibo y cambio juntos; una repetición nunca reaplica los efectos confirmados. */
    void accept(ReservationDelivery delivery);

    /** Permiso asociado a la inserción, distinto de la mera existencia de una fila. */
    record BeginResult(ReservationProcess process, boolean created) {}
}

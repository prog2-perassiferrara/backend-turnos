package com.prog2.appointment.reservation.domain.ports.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
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

    /**
     * Acepta proceso y respuesta juntos. Comprueba propietario antes de reconocer UUID históricos;
     * un replay equivalente se reconoce incluso después de vencer el plazo.
     *
     * @return respuesta confirmada y si esta llamada la creó
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationPhoneException por
     *     propiedad, conflicto o solicitud no vigente
     */
    PhoneResult submitPhone(
            long ownerId, UUID processId, SubmitPhoneRequest request, String groupId, Instant now);

    Optional<PhoneSubmission> findPhone(UUID id);

    Optional<PhoneSubmission> latestPhone(UUID processId);

    /**
     * @return como máximo limit identificadores pendientes, ordenados por próxima ejecución
     */
    List<UUID> duePhones(Instant now, int limit);

    /** Registra el intento antes de publicar; vacío si otro resultado o el plazo lo impide. */
    Optional<PhoneSubmission> beginPhonePublication(
            UUID id, Instant now, int attempts, Duration retry, Duration cooldown);

    /** Registra solo el ACK del UUID indicado, sin modificar respuestas posteriores. */
    void phonePublished(UUID id, Instant now);

    /** Aplica evidencia sobre la versión actual y cierra la respuesta en la misma transacción. */
    ReservationProcess reconcile(UUID id, CentralAppointment evidence, Instant now);

    /** Resultado durable de aceptación o replay. */
    record PhoneResult(ReservationProcess process, PhoneSubmission phone, boolean created) {}

    /** Permiso asociado a la inserción, distinto de la mera existencia de una fila. */
    record BeginResult(ReservationProcess process, boolean created) {}
}

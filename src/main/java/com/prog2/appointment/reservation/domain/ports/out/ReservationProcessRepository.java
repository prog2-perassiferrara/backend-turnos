package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.OwnReservationPage;

import com.prog2.appointment.reservation.domain.model.CancellationEvidence;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;

/** Persistencia de procesos y deduplicación; las escrituras retornan tras confirmar su transacción. */
public interface ReservationProcessRepository {
    Optional<ReservationProcess> find(UUID id);

    /**
     * Lee pendiente, último envío y página/total propios en una misma vista consistente.
     *
     * @param ownerId propietario autenticado
     * @param page página de historial desde cero
     * @param size tamaño entre 1 y 100
     * @return datos propios, sin incluir el pendiente en el total
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationStorageException ante
     *     fallo local
     */
    OwnReservationPage readOwn(long ownerId, int page, int size);

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

    /**
     * @param id hold local conocido
     * @param now instante de reclamación, anterior al vencimiento
     * @return permiso propio acreditado HELD a CONFIRMATION_PENDING; vacío nunca permite emitir el POST
     */
    Optional<ReservationProcess> beginInitialConfirmation(UUID id, Instant now);

    /** @return hasta limit pendientes elegibles, incluidas cancelaciones pendientes, sin seleccionar históricos ya resueltos */
    List<UUID> dueRecovery(Instant now, Duration initialDelay, int limit);

    /**
     * Conserva la próxima fecha de revisión antes de consultar cátedra.
     * @return progreso actual reclamado; no concede permiso para un POST
     */
    Optional<ReservationProcess> beginRecovery(UUID id, Instant now, Duration retry, Duration initialDelay);

    /** Difiere un trabajo que sigue activo, sin gastar intentos ni modificar su fase. */
    void deferRecovery(UUID id, Instant nextAttempt);

    /**
     * Guarda eventId y cambio juntos; una repetición nunca reaplica los efectos confirmados.
     * Los hechos válidos desconocidos se marcan sin crear procesos ni conservarlos para replay.
     *
     * @param action hecho externo validado con su correlación contractual
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationStorageException si
     *     no se puede acreditar el commit; el consumidor debe permitir redelivery
     */
    void accept(ReservationAction action);

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

    /**
     * Acepta una cancelación confirmada o su reintento, comprobando propietario antes del replay.
     * @return proceso con intención durable, sin conceder por sí mismo permiso para HTTP
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationCancellationException
     *     si no pertenece al propietario o la acción es incompatible
     */
    ReservationProcess requestCancellation(long ownerId, UUID id, Instant now);

    /** @return reclamación propia confirmada; vacío si ya no corresponde o la ganó otro worker */
    Optional<ReservationProcess> beginCancellationAttempt(UUID id, Instant now, Duration budget);

    /**
     * Finaliza solo la ejecución cuyo writeId sigue vigente, sin sobrescribir resultados tardíos.
     * @param requireRetry detiene POST automáticos hasta una nueva solicitud del propietario
     * @param nextReview fecha de próxima consulta central, sin contador durable de intentos
     */
    void finishCancellationAttempt(UUID id, UUID writeId, boolean requireRetry, String failure,
            Instant now, Instant nextReview);

    /** Aplica evidencia al estado actual y resuelve intención/outbox en la misma transacción. */
    ReservationProcess confirmCancellation(UUID id, CancellationEvidence evidence, Instant now);

    /** Resultado durable de aceptación o replay. */
    record PhoneResult(ReservationProcess process, PhoneSubmission phone, boolean created) {}

    /** Permiso asociado a la inserción, distinto de la mera existencia de una fila. */
    record BeginResult(ReservationProcess process, boolean created) {}
}

# CU-04. Iniciar reserva

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Iniciar un proceso propio de reserva y avanzar hasta el intercambio de información adicional. |
| Actor principal | Usuario final vía KMP. |
| Actores de apoyo | Backend Catálogo y servicio de cátedra. |
| Disparador | El usuario solicita reservar el horario seleccionado. |

## Precondiciones

- El usuario está autenticado y autorizado.
- Se identifica profesional, fecha y horario; la cuenta proporciona los nombres del paciente requeridos.

## Flujo principal

1. El usuario solicita reservar el horario desde KMP.
2. Turnos obtiene de Catálogo la información vigente y comprueba que el profesional y el horario sean válidos y estén habilitados.
3. Turnos comprueba que el usuario no tenga otro proceso pendiente, registra el inicio de la operación y su propietario y solicita a cátedra el hold con el identificador estable del usuario.
4. Cátedra devuelve el hold, el identificador de proceso y el vencimiento; Turnos conserva esos datos.
5. Turnos inicia inmediatamente la confirmación REST del hold con los nombres tomados de la cuenta y el mismo identificador de paciente.
6. Cátedra acepta el inicio del intercambio Kafka; Turnos registra el progreso e informa que la reserva está en proceso.

## Flujos alternativos y de excepción

- **2a. Sin información vigente, referencia inválida o deshabilitada:** informa la condición y no inicia el hold.
- **3a. Horario ocupado por una reserva u otro hold:** informa el conflicto; el usuario puede consultar disponibilidad nuevamente.
- **3b. Timeout o respuesta perdida al crear el hold, sin IDs:** conserva la intención y consulta ocupaciones. Si la posición está HELD, informa su expiresAt para esperar y reconsultar sin asumir propiedad; si está CONFIRMED, no está disponible. Si está libre, permite una nueva creación acotada, sujeta a 409. Al detener los intentos archiva localmente el intento no identificable, sin afirmar cancelación ni expiración central.
- **3c. El usuario ya tiene un proceso pendiente o con cierre sin verificar:** informa esa condición y permite consultar el proceso existente; termina sin solicitar otro hold. Las solicitudes concurrentes tampoco permiten crear más de un proceso pendiente por usuario.
- **5a. Hold vencido o no recuperable, horario invalidado o conflicto central:** registra el resultado conocido; no informa una reserva confirmada.
- **5b. Respuesta perdida o confirmación inicial ya realizada:** reconcilia el resultado con CU-09 antes de repetir efectos.
- **6a. El evento Kafka llega antes que la respuesta REST:** conserva el progreso sin hacerlo retroceder cuando llegue la respuesta.

## Postcondiciones

- **Éxito:** proceso propio persistido y confirmación inicial aceptada; todavía no hay garantía de reserva confirmada.
- **Fallo o incertidumbre:** queda distinguido un rechazo conocido de una operación pendiente de reconciliación.

## Reglas y relaciones

- Crear el hold y confirmar inicialmente son dos operaciones centrales, aunque respondan a una sola acción del usuario.
- Nombre y apellido se toman de la cuenta; `externalPatientId` y propiedad se obtienen del usuario autenticado.
- El hold no se renueva por conservarlo localmente o salir de KMP. CU-05 completa el teléfono; CU-06 informa el estado; CU-10 permite abandonar explícitamente.
- Se permite un único proceso pendiente por usuario. Para iniciar otro, el anterior debe alcanzar un resultado verificado: confirmación, cancelación, vencimiento o fallo terminal. Una confirmación de resultado incierto, la intención de abandono o el mero vencimiento de pantalla no liberan el límite. La creación sin IDs admite el cierre local del intento y una nueva creación condicionada por ocupaciones según la aclaración docente; no acredita cancelación central.
- Si existe una solicitud de abandono aún sin resolver, conocer que la reserva se confirmó no libera el límite: primero se verifica el resultado de esa solicitud, conforme a CU-10.
- El límite no restringe la cantidad de reservas ya confirmadas ni las consultas de disponibilidad. Cada operación y resultado conserva su identidad de proceso; no reemplaza el historial de otro.

## Fuentes y pendientes

**Fuentes:** [enunciado, sección 7](../../../PROJECT_STATEMENT-v1.md); [anexo, secciones 9, 10, 13 y 17](../../../INTEGRATION_REFERENCE-v2.md); [aclaración sobre creación incierta](../../../dudas_Slack.md#duda-18-resuelta); [decisiones registradas](README.md).

**Decisión contractual:** la aclaración docente resuelve el tratamiento sin IDs mediante ocupaciones e intentos limitados, sin recuperar la propiedad de un hold observado. CU-09 conserva la recuperación completa de otras interrupciones. La acción única de inicio fue confirmada con la aprobación del caso.

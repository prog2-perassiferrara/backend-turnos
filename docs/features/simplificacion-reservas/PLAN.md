# PLAN: Simplificación estructural de reservas, Etapa 1

> **Registro histórico SDD.** Alcance, diagramas y evidencia de su revisión; no describen necesariamente el sistema actual. Consultar [arquitectura vigente](../../ARQUITECTURA.md), [contratos](../../CONTRATOS.md) e [índice histórico](../README.md).

**Alcance y ejecución:** Etapa 1 autorizada explícitamente el 2026-11-02. Este registro nuevo está pendiente de revisión documental; no atribuye su aprobación por inferencia.

**SPEC de referencia:** [SPEC.md](SPEC.md)
**Versión de la spec revisada:** alcance explícito del 2026-11-02
**Estado:** En revisión <!-- Borrador | En revisión | Aprobado -->

## Contexto técnico verificado

<!-- Qué existe hoy y cómo participa en la funcionalidad. -->

| Componente | Ruta verificada | Responsabilidad |
| --- | --- | --- |
| Reservas | src/main/java/com/prog2/appointment/reservation/ | Flujos, puertos y adaptadores existentes. |
| Migraciones | src/main/resources/db/migration/ | V2 a V9 previas; V10 para retirar abandono. |

**Convención:** hexagonal existente, transacciones cortas y constructor DI. Se trabaja sobre el árbol actual, sin Git mutable.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido desde la solicitud, evento o arranque hasta el resultado del servicio.
Usa un diagrama si aporta claridad. -->

Eliminar la acción opcional de abandono de aplicación, dominio, HTTP y persistencia. GET/List dejan de depender del gateway central y solo proyectan la lectura propia. La recuperación y cancelación conservan la consulta paginada central, que devuelve evidencia correlacionada sin certificación pública. No se agrega un coordinador ni otro estado.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Módulo | Estado | Cambio | Dependencias |
| --- | --- | --- | --- |
| reservation | Existente | Retirar abandono y certificación por lectura. | Puertos reales y adaptadores conservados. |

| Componente | Acción | Cambio | RF |
| --- | --- | --- | --- |
| AbandonReservationUseCase y CancellationKind | Eliminar | Quitar acción y discriminador sin alternativa útil. | RF-01 |
| Get/List y DTO | Modificar | Leer localmente y retirar certificados. | RF-02 |
| Gateway central | Modificar | Conservar find paginado para recuperación/cancelación. | RF-03 |
| V10 | Crear | Retirar columnas y actualizar exclusión por cierre. | RF-05 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Entrada/salida:** retirar POST /api/reservation-processes/{processId}/abandon, cancellation.kind, verificationStatus y verifiedAt. El envelope del listado queda pending/history; warningCode individual permanece para errores de cancelación.
- **Identificadores y propiedad:** no cambian IDs locales/centrales, eventId ni externalPatientId.
- **Origen:** lecturas propias exclusivamente SQL; recuperación conserva REST/Kafka.
- **Persistencia:** mantener @Version, writeId, inbox/outbox y presupuesto de cancelación.
- **Convivencia:** evidencia central sigue aplicándose sobre la versión actual, sin reabrir cierres.
- **Migración:** V10 limpia solo intenciones ABANDON, elimina cancellation_kind/abandonment_pending y redefine active_owner_id por closed_at. No editar migraciones previas.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y completa solo los puntos pertinentes al backend. -->

- Las doce fases actuales permanecen. No convertir incertidumbre en FAILED/EXPIRED por esta poda.
- Cancelar exige propietario y CONFIRMED; se mantienen claims, versiones de retry y reconciliación actuales.
- GET/List no ejecutan efectos ni llaman a cátedra. Los nombres históricos disponibles se conservan; no se enriquecen en la lectura.
- Entradas inválidas y almacenamiento inaccesible conservan problemas seguros, sin secretos.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

Sin dependencias ni propiedades nuevas. Se conservan configuración compartida, clientes de Catálogo/cátedra, acceso técnico y configuración Kafka.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, contrato o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Usar el cierre de TASKS.md definido en AGENTS.md: CA/evidencia,
regresión y desviaciones del PLAN. Distingue pruebas ejecutadas, fallidas,
no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

| CA | Método | Entorno | Evidencia |
| --- | --- | --- | --- |
| CA-01 | Ruta retirada y transición normal hasta confirmación/cancelación explícita. | API y MySQL temporal. | Suite de cancelación y proceso. |
| CA-02 | Lecturas locales, roles, propiedad, SQL snapshot, paginación y ausencia de HTTP central. | Unitarias y API/SQL. | Get/List/OwnReservations. |
| CA-03 | Checkpoints, commits ambiguos, replay, ACK, IDs estables y recovery. | Testcontainers MySQL/Kafka. | Suites existentes adaptadas sin retirar requisitos. |
| CA-04 | Registro, JWT y gateways HTTP ficticios. | Suite completa aislada. | clean verify. |
| CA-05 | V9 con abandono pendiente y cancelación normal. | MySQL temporal. | Regresión V10. |

**Regresión:** ./mvnw -B -ntp clean verify en copia temporal del árbol, para evitar interferencia del IDE.

**Prueba operativa:** APIs reales embebidas con bases y Kafka temporales. No modificar Compose ni invocar infraestructura real.

**Límites:** las pruebas de contratos usan proveedores ficticios; no acreditan un nuevo ensayo end-to-end de cátedra.

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Registrar alcance y baseline.
2. Retirar abandono y migrar su estado.
3. Sustituir lecturas certificadas por lecturas locales, conservando reconciliación.
4. Reemplazar pruebas mixtas por regresiones de requisitos; ejecutar suite completa y revisión de calidad.
5. Registrar evidencia y detenerse, sin iniciar Etapa 2.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. Antes de implementar, realizar el chequeo de coherencia de
AGENTS.md con esos documentos, sin otro informe ni aprobación adicional.
No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->

- **Riesgos:** migración de intenciones antiguas, persistencia de cancelaciones normales y pruebas que mezclan auditoría con no regresión. Cubrir V10, carreras y cierres.
- **Decisiones pendientes:** ninguna dentro del alcance autorizado.


## Etapa 2: acceso técnico mínimo

Etapa 1 aceptada el 2026-11-02; su comportamiento no se modifica. Etapa 2 autorizada explícitamente. Mantener los contratos GET /api/internal/catedra/token (JWT de servicio firmado) y GET /api/student/integration (JWT técnico compartido). El anexo §§4,5.3/5.4 fija vigencia larga y excluye revocación anticipada; no obliga a renovar en segundo plano. Catálogo ya solicita rememberMe=true; no requiere cambios.

Una adquisición síncrona al recibir ApplicationReadyEvent conserva JWT/configuración en un valor volatile de TechnicalAccessUseCaseImpl. Hasta tres intentos consecutivos solo para TEMPORARY_UNAVAILABLE/PROVISIONING_PENDING, con timeouts HTTP existentes y sin pausas, executor ni programación posterior. Un fallo terminal detiene el arranque técnico; el backend sigue atendiendo operaciones locales. getAccess solo lee y valida vigencia; tras fallo agotado, expiración o 401 se requiere reiniciar. Esto retira continuidad transparente, no validación ni autorización. El puerto público y ambos gateways HTTP reales permanecen.

Solo PROVISIONED genera IntegrationConfiguration. Los demás estados se traducen en la frontera HTTP a errores seguros ya existentes; se retira el enum del dominio. No se agrega otra FSM. El JWT de servicio que protege la llamada a Catálogo sigue siendo independiente del JWT central y del usuario.

### Clasificación de las pruebas antes de retirarlas

| Prueba anterior | Garantía | Acción |
| --- | --- | --- |
| TechnicalAccessRecoveryTests | Ciclos/cooldown, candidato retenido, foreground no bloqueante, interrupción del worker y publicación tardía. | Autoimpuesta: retirar la clase; el límite y disponibilidad se prueban con el arranque mínimo. La sanitización HTTP permanece cubierta. |
| TechnicalIntegrationLifecycleTests | Cerrar executor/worker propio. | Retirar ese escenario; reemplazar por adquisición síncrona al evento de arranque y ausencia de nueva adquisición por consumo. |
| TechnicalAccessBehaviorTests | Mezcla adquisición/configuración/vigencia con blacklist, repositorio y renovación. | Reemplazar por comportamiento mínimo; conservar validación y fallos seguros. |
| TechnicalAccessHttpTests | Contratos reales con proveedores ficticios y credenciales separadas. | Conservar; adaptar solo adquisición inicial y agregar estados no aprovisionados. |
| Fixtures de gateways de reservas/disponibilidad | Consumo de configuración disponible. | Retirar únicamente el argumento enum eliminado, sin cambiar su comportamiento. |
| AvailabilityHttpTests, rechazo técnico | Mezcla 401 seguro con readquisición automática sin reinicio. | Conservar 503 seguro y ausencia de replay; ahora exige reinicio y aísla el contexto invalidado. |
| UserHttpTests, contexto recreado | Persistencia/login tras reiniciar. | Ampliar a acceso técnico correcto, 503 y 401 al arrancar, sin afectar autenticación propia. |

Validación: foco de integración/acceso y después ./mvnw -B -ntp clean verify sobre copia temporal idéntica. La suite conserva las regresiones de Etapa 1 y autenticación. No cambiar Catálogo, inbox, outbox, cancelación ni writeId. Baseline Etapa 2: 182 archivos Java / 8.878 líneas físicas. Detenerse tras este cierre, antes de Etapa 3.

## Etapa 3: deduplicación Kafka mínima

Autorizada explícitamente tras aceptar Etapas 1 y 2. RF-08 a RF-10 / CA-08 a CA-10.

**Orden comprobado:** StartReservationUseCaseImpl.createHold persiste el hold recibido mediante repository.update, y beginInitialConfirmation confirma otro checkpoint antes de holds.confirm. El anexo §§10 y 17 ubica AdditionalInformationRequested después de confirmar el hold; §§15.8/15.9 correlacionan vencimiento/invalidez con requestEventId. Por tanto el evento puede adelantarse a la respuesta HTTP, pero el POST que lo provoca ya tiene asociación durable. No hay recuperación de IDs perdidos al crear un hold; esos intentos siguen el flujo de ocupaciones existente y sus eventos desconocidos no se importan.

**Implementación:** parser valida y devuelve ReservationAction sin Origin/payload/huella. Listener entrega hechos válidos al caso de uso y confirma después del retorno; inválidos se descartan con diagnóstico seguro. El store transaccional existente comprueba eventId, aplica el agregado y registra la clave en reservation_processed_event dentro del mismo commit. Su PK y @Version resuelven concurrencia; un error SQL se propaga para redelivery, sin rescatar el commit como éxito. Los IDs de eventos válidos desconocidos, no soportados o sin efecto también quedan registrados. No se agrega ningún agregado, puerto ni coordinador.

V11 copia todos los event_id no nulos del inbox anterior, incluyendo ignorados/rechazados/diferidos, y elimina la tabla anterior. Se pierden deliberadamente payload, hash, origen, clasificación y diagnósticos. Un ID repetido conserva el primer resultado, aunque cambie el contenido; el anexo §15.1 exige deduplicar por ID, no auditar su reutilización. El enunciado §8 requiere duplicados, desconexiones y reinicio, conservados por transacción, checkpoint y redelivery.

**Clasificación previa de tests:**

| Grupo existente | Garantía | Tratamiento |
| --- | --- | --- |
| Evento temprano, ACK bloqueado, fallo SQL y redelivery en ReservationIntegrationTests | Requisito Kafka y checkpoint REST | Conservar y adaptar tabla; agregar commit confirmado antes del ACK y dedup observable. |
| Rechazo, resultados terminales y outbox en ReservationPhoneIntegrationTests | Semántica contractual, atomicidad y republicación | Conservar; adaptar solo ingreso de eventos y consultas de dedup. |
| Parser de correlación, selección, schema, grupo, key y campos compatibles | Validación externa | Conservar sin aserciones de hash/payload. |
| Replay por asociación futura, reclasificación y huella histórica en ReservationRecoveryIntegrationTests | Garantías adicionales retiradas | Retirar; sustituir los casos mixtos por dedup concurrente, desconocido ignorado y terminal no regresivo contra procesos existentes. |
| Migraciones V4/V7 e inbox legado | Datos existentes e idempotencia histórica | Adaptar fixtures anteriores; agregar V11 con IDs de todas las clasificaciones. |

Validación enfocada con MySQL/Kafka temporales y clean verify completo, incluyendo usuarios, Catálogo, disponibilidad, acceso técnico y outbox. Sin cambios de arquitectura, TransactionTemplate ni efectos sobre otros repositorios.

## Etapa 4: cancelación simple

Autorización explícita del 2026-11-03, Etapas 1 a 3 aceptadas. RF-11/12/13 y CA-11/12/13. El anexo §12 garantiza POST cancel idempotente para CANCELLED; no exige presupuesto exacto ni versiones históricas por retry.

**Anterior:** CancellationProgress contiene estado, requestedAt, retryVersion, attempts, nextAttemptAt y lastFailure. La reclamación consume presupuesto durable; callbacks comparan versión/cantidad de intentos y el body de retry transporta la versión observada.

**Nuevo:** ReservationProcess conserva únicamente CancellationState nullable (PENDING o RETRY_REQUIRED). CANCELLED y otros cierres retiran la intención. reasonCode existente puede conservar el diagnóstico, sin decidir el control. Cada ejecución usa un contador local de hasta tres POST y consulta central antes de cada uno. Si una consulta falla antes del primer POST, queda PENDING para otra revisión espaciada; si falla tras un POST incierto, o se agotan/rechazan los POST, queda RETRY_REQUIRED. Recovery sigue consultando ese estado, sin más POST automáticos. Repetir el mismo endpoint propio sin body reabre únicamente RETRY_REQUIRED; PENDING es repetición y CANCELLED devuelve el cierre.

**Concurrencia mínima:** reutilizar ReservationExecutionGuard para trabajo activo local. Una reclamación SQL condicional con @Version comprueba intención y recovery_next_attempt_at, reserva esa misma fecha hasta cubrir los timeouts máximos y conserva writeId de la ejecución. No hay nuevo nonce/ciclo: writeId existente evita que un fallo anterior cambie el estado de una ejecución posterior. finishCancellationAttempt solo modifica intención/fecha si su writeId sigue vigente. Un éxito central correlacionado puede cerrar independientemente del reclamante; un fallo nunca retrocede CANCELLED. Los demás rescates de commit permanecen para Etapa 5.

Recovery general delega cancelaciones antes de reclamar su propia fecha/guard; adaptación necesaria para no reclamar dos veces el mismo calendario ni anidar scopes. El flujo general de procesos no se rediseña. No se incorporan nuevas clases, puertos ni TransactionTemplate. El store y adapter existentes mantienen la frontera SQL.

**V12:** mantener cancellation_status y recovery_next_attempt_at; mapear PENDING no agotado a PENDING, agotado/REJECTED a RETRY_REQUIRED, RESOLVED/cierres a null. Conservar la mayor fecha anterior de revisión/ejecución para no adelantar una reclamación existente. Copiar diagnóstico si falta reasonCode. Eliminar requestedAt, retryVersion, attempts, nextAttemptAt y lastFailure propios, constraints e índice asociados. No modificar migraciones previas, fase ni IDs/plazos del proceso.

**Clasificación previa de pruebas:**

| Pruebas existentes | Garantía | Tratamiento |
| --- | --- | --- |
| Propiedad, confirmada/CANCELLED, HTTP, timeout, central cancelado, terminales, Kafka tardío | Obligatoria | Conservar adaptando la API sin body y el estado mínimo. |
| Tres POST, consulta antes de repetir, consulta fallida, agotamiento y retry explícito | Obligatoria | Sustituir calendario/counter persistido por secuencia observable de llamadas y estado detenido. |
| Claim SQL y commit ambiguo | Exclusión necesaria y seguridad ante SQL | Conservar un permiso por ejecución y no efecto sin permiso; retirar solo rescate de aceptación dependiente del ciclo eliminado. |
| retryVersion exacto, requestedAt histórico y contadores entre reinicios | Adicional retirada | Retirar sus aserciones; reemplazar casos mixtos por recuperación con consulta previa y fallo tardío condicionado por writeId. |
| Migraciones V6/V10 y fechas de recovery | Datos y recuperación | Adaptar fixtures históricos con columnas literales, agregar V11 a V12 poblada con intenciones pendientes/agotadas/resueltas. |

Validación enfocada en cancelación/recuperación e integración SQL/HTTP/Kafka y después clean verify completo con servicios temporales. Baseline 172 archivos Java / 8.285 líneas físicas / 642 tests. Detenerse antes de Etapa 5.

La retirada de versiones también abandona deduplicar retries HTTP históricos: cada POST propio sobre RETRY_REQUIRED se interpreta como una nueva solicitud explícita. No habilita repeticiones ciegas del POST central, que mantienen consulta previa e idempotencia contractual.

## Etapa 5: commits ambiguos y clasificación de writeId

Autorizada explícitamente el 2026-11-03 tras aceptar Etapas 1 a 4. RF-14/15/16 y CA-14/15/16. Se mantienen store @Transactional, adapter, mapper, puertos y modelo separados; sin TransactionTemplate ni cambios de wiring.

### Clasificación antes de retirar usos

| Uso previo | Categoría | Decisión |
| --- | --- | --- |
| begin: comparar UUID candidato/fila tras excepción para devolver created=true | 1, solo rescate ambiguo | Eliminar. Solo retorno normal del insert concede creación. Conflicto único devuelve fila existente con created=false, nunca permiso. |
| beginInitialConfirmation: UUID transportado y reconsulta después de excepción | 1, solo rescate ambiguo | Eliminar fallback y transporte del UUID al adapter. Mantener HELD→CONFIRMATION_PENDING atómico y UUID de checkpoint para el uso independiente descrito abajo. |
| beginCancellationAttempt: UUID transportado y reconsulta para rescatar commit | 1 y 2 | Eliminar solo fallback; mantener reclamación SQL y UUID devuelto tras commit conocido. |
| confirmCancellation: reconsulta CANCELLED después del error | 1, solo continuidad transparente | Eliminar. La siguiente revisión recupera el estado con evidencia correlacionada. |
| committedPhone y reconsulta tras error genérico de aceptación | 1, continuidad adicional de respuesta HTTP | Eliminar rescate. No cambiar transacción, evento, tabla ni publicador del outbox. Solo un conflicto único conocido permite repetir la validación local del mismo UUID. |
| expireUnsentConfirmation: impedir cierre usando checkpoint ya sustituido | 2, exclusión/fencing del permiso previo a transporte | Conservar identidad y guard/transición existentes. Recovery no recibe ese permiso por observar la fase. |
| Cancelación: relectura previa a POST y finishCancellationAttempt | 2, ejecución vigente y fallo tardío frente a otra ejecución | Conservar writeId, fecha compartida y @Version. Eliminarlo aquí exigiría otro identificador equivalente, fuera del criterio autorizado. |
| IDs exigidos por integración y recovery contractual | 3 | Ninguno usa writeId: processId/holdId/requestEventId/eventId son independientes y no cambian. |
| Entidad/mapper/record/copias, inicialización uniforme y UUID en cada mutación | 4, soporte e invalidación del fencing anterior | Conservar campo y representación existentes, sin introducir nullables ni otro token; generar UUID dentro del store cuando corresponde, sin transportarlo para probar autoría tras un error. No se atribuye al contrato esa forma de representación. |

No se elimina ni renombra write_id: tiene usos 2 reales, protegidos por pruebas de Etapa 4 y de vencimiento antes del transporte. No se agrega un identificador sustituto ni una migración sin cambio de esquema.

**Frontera:** store retorna tras commit; adapter traduce excepción SQL/transaccional a ReservationStorageException. Reintentos optimistas siguen limitados a tres y solo ejecutan SQL revertido. Un conflicto único es una repetición conocida, no permiso; nunca se rescata error de conexión/commit leyendo quién escribió. Desaparece Supplier de fallback ambiguo y sus consultas. UUID de checkpoint se genera dentro del store, no antes de invocarlo para buscarlo si falla.

**Recovery:** el estado durable manda. HELD conservado puede continuar tras adquirir otra transición confirmada; CONFIRMATION_PENDING se reconcilia y no repite POST sin evidencia. Un insert ambiguo conservado como HOLD_PENDING sin IDs no autoriza crear: se conserva la política de recuperación de ese intento. Cancelación pendiente espera su fecha y consulta central antes de repetir. Se acepta menor disponibilidad, incluso mantener incertidumbre si el central no aporta evidencia, sin nuevo estado/flag para reconstruir autoría. Outbox sigue publicando desde su fila durable, con mismo UUID y contenido, independientemente de la respuesta de aceptación HTTP.

### Clasificación de tests de commit

| Pruebas | Garantía | Tratamiento |
| --- | --- | --- |
| BEFORE de inserción y confirmación | Ningún efecto sin checkpoint confirmado | Mantener; comprobar estado previo y reintento posterior conocido. |
| AFTER que esperaba crear inmediatamente o recuperar permiso por nonce | Continuidad adicional retirada | Reescribir: error/no POST; después releer estado durable y recuperar de forma conservadora. |
| Commit de revisión y Kafka, rollback + dedup | Atomicidad/recovery/ACK obligatorios | Mantener. |
| Commit de aceptación telefónica que esperaba 200 tras error | Continuidad adicional de aceptación, no durabilidad del outbox | Cambiar solo respuesta a503 y luego retry con mismo UUID; preservar una fila y evento idéntico. |
| Commit después del ACK de publicación | Outbox y republicación del mismo evento | Mantener sin cambios. |
| Claims concurrentes, fallo tardío y deadline previo al transporte | Exclusión/fencing necesarios | Mantener; extender BEFORE/AFTER de cancelación sin efecto desde ejecución fallida. |

CommitFaultDataSource se conserva: todavía demuestra rollback, commit realmente persistido que devuelve error, atomicidad Kafka y republicación outbox. No se elimina evidencia de requisitos por simplificar rescates.


## Etapa 6: composición Spring mínima

**RF-17/18, CA-17/18.** Sustituir nueve fábricas triviales de ReservationConfiguration y cinco de UserConfiguration por @Component en las mismas implementaciones. Conservar nombre userTokenIssuer por su contrato de inyección calificada. Sin puertos eliminados ni cambios en dirección de dependencias.

ReservationConfiguration mantiene propiedades, clientes HTTP con timeout y transporte explícitos, parser con grupo, canal Kafka/executor, políticas parametrizadas, publicador y preparación al arrancar. Un helper privado elimina construcción HTTP repetida conservando un cliente independiente por adaptador. UserConfiguration mantiene Argon2id y JwtEncoder; las demás configuraciones conservan recursos y parámetros derivados.

Lombok gestionado por Spring Boot, procesador explícito para Java 25 y excluido del JAR ejecutable. @RequiredArgsConstructor sustituye solo asignaciones de dependencias final sin lógica y en idéntico orden. @Getter se aplica únicamente a campos con accesores existentes y mecánicos en tres entidades; copias defensivas y constructores con validación permanecen manuales. Sin setters, equals/hashCode/toString ni cambios en dominio.

**Validación:** comparar firmas públicas y cuerpos funcionales con el working tree inicial; conservar tests existentes, ejecutar clean verify en copia aislada y comprobar arranque/inyección mediante contextos de integración. Verificar contenido del JAR, transacciones y ausencia de dependencias concretas de infraestructura en application. Medir primero wiring y luego Lombok frente a170/8.120. No introducir un test por componente ni alterar fronteras @Transactional para ahorrar beans.


**Fábricas retiradas y componentes equivalentes:**

| Fábrica manual | Componente descubierto |
| --- | --- |
| cancelReservationUseCase | CancelReservationUseCaseImpl |
| listOwnReservationsUseCase | ListOwnReservationsUseCaseImpl |
| reservationPatientGateway | UserReservationPatientAdapter |
| reservableSlotGateway | AvailabilityReservationSlotAdapter |
| receiveReservationActionUseCase | ReceiveReservationActionUseCaseImpl |
| reservationExecutionGuard | ReservationExecutionGuard |
| reservationRecoveryDispatcher | ReservationRecoveryDispatcher |
| phoneSubmissionDispatcher | PhoneSubmissionDispatcher |
| getReservationProcessUseCase | GetReservationProcessUseCaseImpl |
| getUserProfileUseCase | GetUserProfileUseCaseImpl |
| passwordHasher | SpringPasswordHasherAdapter |
| userTokenIssuer | NimbusUserTokenIssuerAdapter |
| registerUserUseCase | RegisterUserUseCaseImpl |
| authenticateUserUseCase | AuthenticateUserUseCaseImpl |

No desaparece ningún puerto. El guard sigue siendo el mismo singleton; dispatchers, casos de uso y adaptadores conservan constructor, dependencias y ciclo de vida. TransactionalReservationStore sigue siendo un componente distinto detrás del proxy @Transactional; no se mueve ninguna transacción ni se introduce TransactionTemplate.

**Configuración conservada:** trece beans en ReservationConfiguration: reservationProperties; reservationCancellationGateway, appointmentHoldGateway y centralAppointmentGateway (transporte HTTP); reservationActionParser (grupo esperado); reservationKafkaChannel (executor/cierre); recoverReservationCancellationsUseCase, recoverReservationProcessesUseCase, startReservationUseCase y submitReservationPhoneUseCase (parámetros derivados); phonePublicationGateway y publishPendingPhonesUseCase (publicación/política); prepareReservationMessaging (arranque). Las configuraciones de disponibilidad y acceso técnico conservan sus recursos, validación y parámetros; no se transforma un escalar en bean solo para eliminar una fábrica.

**Lombok aplicado:**37 constructores de dependencias final y40 getters existentes en ReservationProcessEntity, PhoneSubmissionEntity y UserAccountEntity. El constructor documentado de JwtContractValidator sigue manual. UserAccountEntity.getAuthorities mantiene Set.copyOf; no se generan nuevos getters de versión/campos privados, setters, representaciones de texto ni igualdad. Sin cambios en modelos de dominio.


### Ajuste de calidad posterior a Etapa 6 (2026-11-03)

Autorizadas las correcciones de inspecciones, sin cambios en RF/CA ni contratos. RecoverReservationCancellationsUseCaseImpl separa cancelOnce, finishAttempt y dos predicados; CancellationAttempt es un record privado del resultado de una llamada (motivo/reintento), no estado durable ni ciclo nuevo. El contador permanece local, el cierre sigue en finally y solo el fallo recuperable del POST autoriza otra consulta/intento. Complejidad execute23→15 comprobada con el analizador Java local. Renombrar record→markProcessed conserva SQL/transacción; limpiar únicamente variables/helpers sin uso y expresiones de tests. Se conservan todas las pruebas existentes y se agregan dos caracterizaciones de GET fallido y respuesta no correlacionada, verificadas también con la producción anterior.

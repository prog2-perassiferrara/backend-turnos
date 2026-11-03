# TASKS: Simplificación estructural de reservas, Etapa 1

> **Registro histórico SDD.** Alcance, diagramas y evidencia de su revisión; no describen necesariamente el sistema actual. Consultar [arquitectura vigente](../../ARQUITECTURA.md), [contratos](../../CONTRATOS.md) e [índice histórico](../README.md).

Alcance e implementación autorizados explícitamente el 2026-11-02. Documentos de referencia: [SPEC](SPEC.md) y [PLAN](PLAN.md). La aprobación de esos documentos nuevos no se presupone.

## Chequeo previo

RF-01 a RF-05 tienen CA, validación y tareas; sin decisiones bloqueantes. Se conserva el árbol de trabajo previo. Baseline productivo: 187 archivos Java, 9.329 líneas físicas.

## Tareas

- [x] **T-01 · CA-01/05:** retirar abandono, actualizar modelos/API y agregar V10. Dependencia: ninguna. Validación: ruta retirada y migración poblada.
- [x] **T-02 · CA-02/03:** lecturas locales sin certificados; conservar gateway central para recuperación/cancelación. Dependencia: T-01. Validación: lectura con central inaccesible y contratos del gateway.
- [x] **T-03 · CA-01/05:** separar pruebas mixtas y verificar invariantes, autenticación y contratos. Dependencia: T-02. Validación: suite completa, Javadoc y revisión de calidad local.
- [x] **T-04 · CA-01/05:** cerrar inventario, documentación y métricas. Dependencia: T-03. Validación: diff y coherencia de contratos.

## Cambios de contrato propios

Se retiran POST /api/reservation-processes/{processId}/abandon, cancellation.kind y los campos verificationStatus/verifiedAt. El listado devuelve pending/history. El aviso de error de cancelación individual permanece. No hay compatibilidad pendiente con reservas de KMP.

La V10 retira intenciones ABANDON sin fabricar resultado central; las cancelaciones normales conservan su progreso. Permanecen reintentos y claims de cancelación, technical access, inbox/replay, outbox, writeId y arquitectura actuales.

## Inventario de la poda

- Tipos retirados: AbandonReservationUseCase, AbandonReservationUseCaseImpl, CancellationKind, VerificationStatus y CentralAppointmentsBatch.
- Campos retirados: ReservationProcess.abandonmentPending, CancellationProgress.kind, columnas abandonment_pending/cancellation_kind, cancellation.kind y certificaciones verificationStatus/verifiedAt. El resumen del listado deja de incluir warningCode; el aviso individual de cancelación permanece.
- Métodos/ramas retirados: requestCancellation de tipo ABANDON, canExpireAbandonment/expireAbandonment, expireStableAbandonment, bloqueo de teléfonos/confirmación por abandono, cancelación compensatoria tras confirmación y reconciliación y certificación disparadas por GET/List. El listado central ahora se consume mediante find para recuperación/cancelación.
- Auxiliares retirados por quedar huérfanos: CentralAppointment.agreesWith, CentralAppointmentGateway.listOwn, construcción de lotes certificados del cliente central y helpers read/evidence/status/verification/match/warning del listado. Get/List pierden su dependencia central; Get pierde su verificationDelay privado, sin retirar el temporizador de recovery.
- Errores retirados: ABANDONMENT_UNCERTAIN y CANCELLATION_KIND_CONFLICT. Persistencia y DTO dejan de transportar el discriminador kind y la bandera de abandono; no se sustituye por otro indicador.
- Endpoint retirado: POST /api/reservation-processes/{processId}/abandon.
- Conservado: abandonHoldAttempt archiva únicamente una creación incierta sin IDs según el contrato operativo; no es la acción de abandono compensatorio retirada.
- Pruebas mixtas reemplazadas: vencimiento incierto por recuperación, cancelación tardía frente a confirmación antigua, cierre atómico de outbox, propiedad, exclusión del pendiente y consulta local. Se mantienen los escenarios obligatorios de SQL/Kafka.

## Cierre

| CA | Estado local | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado | ReservationCancellationIntegrationTests: ruta retirada sin efectos; ReservationProcessTests: confirmación histórica y cancelación explícita. |
| CA-02 | Verificado | GetReservationProcessTests, ListOwnReservationsTests y OwnReservationsIntegrationTests: lectura local, autorización, snapshot, orden/paginación y cero HTTP central. |
| CA-03 | Verificado | ReservationIntegrationTests, ReservationPhoneIntegrationTests, ReservationRecoveryIntegrationTests y ReservationCancellationIntegrationTests: checkpoints, deduplicación/ACK, ID/contenido estable, carreras y recuperación. |
| CA-04 | Verificado con proveedores ficticios | UserHttpTests (82 casos), UserJwtTests, SecurityHttpTests, AvailabilityClientTests y TechnicalAccessHttpTests; gateways de holds, ocupaciones, listado y cancelación. |
| CA-05 | Verificado | V10 sobre V9 poblada, parametrizada con CONFIRMATION_PENDING/CONFIRMED; conserva IDs/plazo y cancelación normal, sin cierre inventado. También pasan las migraciones históricas. |

**Regresión final:** `./mvnw -B -ntp clean verify`, 2026-11-02, 643 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS. Ejecutada sobre copia temporal idéntica de src para evitar interferencia del IDE. Incluye API embebida, MySQL/Kafka temporales y proveedores HTTP ficticios; no consume .env ni servicios reales. La corrida previa detectó tres expectativas/fixtures desactualizados y se corrigieron antes de esta repetición completa.

**Calidad:** Javadoc de 28 fuentes modificadas sin avisos; diff check y escaneo de secretos CLI sin hallazgos. Análisis de código CLI no ejecutable por Vortex 403; inspección IDE no disponible por content roots. No se afirma que esos análisis ni un nuevo Gate remoto hayan pasado.

**Métricas productivas:** 187 a 182 archivos Java y 9.329 a 8.878 líneas físicas (451 menos, incluidos comentarios/blancos en ambas mediciones). Comparación con el árbol de trabajo anterior a esta etapa, no con HEAD. No se agregan tipos productivos ni dependencias.

**Límites y riesgos:** contratos externos comprobados contra proveedores ficticios; no acredita un nuevo recorrido real de cátedra. Los nombres se muestran si ya fueron obtenidos por reconciliación, sin enriquecimiento por GET. V10 no reabre envíos telefónicos ya cerrados: los pendientes conservan su estado y esperan evidencia central. Los cambios de API propios son intencionales; KMP no consume reservas. Sonar de código queda pendiente de un entorno habilitado/publicación del usuario.

**Desviaciones del PLAN:** ninguna funcional. El listado central conserva su HTTP paginado mediante find, sin el tipo de certificación ya huérfano. Solo se implementó Etapa 1; las siguientes requieren revisión posterior. No se ejecutaron operaciones Git mutables.


## Etapa 2: implementada y validada localmente

Autorizada explícitamente el 2026-11-02, tras aceptar Etapa 1. Chequeo previo: RF-06/07 tienen CA-06/07, diseño y validación en PLAN, sin decisiones bloqueantes. Etapa 1 queda aceptada y no se reabre; etapas 3 a 6 no se implementan.

- [x] **T-05 · CA-06/07:** retirar recuperación asíncrona, resultados intermedios, repositorio/cache abstraído y estados internos de provisioning; conservar gateways y acceso público. Validar adquisición/vigencia/errores con pruebas mínimas.
- [x] **T-06 · CA-06/07:** clasificar/reemplazar pruebas de mecanismos retirados y verificar arranque, contratos con Catálogo/cátedra, secretos y usuarios finales mediante suite completa.
- [x] **T-07 · CA-06/07:** actualizar contrato operativo vigente, métricas y evidencia, sin avanzar a Etapa 3.


### Inventario de Etapa 2

Retirados nueve archivos productivos: AcquireTechnicalAccessUseCaseImpl, AcquireTechnicalAccessUseCase, RequestTechnicalAccessRecovery, TechnicalAccessRecoveryAdapter (incluida RecoveryDelay), TechnicalAccessRepository, InMemoryTechnicalAccessAdapter, AcquisitionResult, AcquisitionOutcome y ProvisioningStatus.

Se eliminan executor/hilo technical-access-recovery, candidato retenido, resultados/outcomes, cooldown, running/closed/nextCycleAt, blacklist de huellas y cierre/publicación tardía de la máquina anterior. El estado de aprovisionamiento permanece en el DTO contractual, validado antes de generar configuración del dominio. AccessFailure conserva códigos seguros y distingue únicamente qué error permite otro intento de arranque.

TechnicalAccessUseCaseImpl conserva un único valor volatile con JWT/configuración. Se mantienen TechnicalAccessUseCase, CatalogTechnicalTokenGateway y CentralIntegrationGateway, DTO/transportes seguros, claves externas y las identidades separadas. El arranque tiene hasta tres intentos de GET; getAccess no adquiere nada. Ante indisponibilidad agotada, expiración o rechazo, hace falta reiniciar Turnos. No se garantiza recuperación transparente, candidato reutilizado, calendario durable, blacklist ni protección de publicación tardía de una tarea inexistente.

La clasificación/reemplazo de pruebas está en PLAN. No se modificó producción fuera de integration; las adaptaciones en fixtures de reservas/disponibilidad solo retiran el argumento de provisioning. Se conserva toda la producción de Etapa 1. La prueba de disponibilidad sustituye únicamente la expectativa de readquisición automática por indisponibilidad sin replay y contexto reiniciado; login añade arranques 503/401.

### Evidencia de Etapa 2

Foco: 185 pruebas correctas de integración, propiedades, ciclo de vida y gateways. Javadoc de siete fuentes productivas sin avisos, secretosCLI sin hallazgos. CódigoCLI Vortex403 e IDE fuera de content roots; no se afirma que analizaran el código. 

| CA | Estado local | Evidencia |
| --- | --- | --- |
| CA-06 | Verificado | TechnicalAccessBehaviorTests, TechnicalAccessHttpTests, TechnicalIntegrationLifecycleTests y consumidores de disponibilidad/reservas: acceso completo al arrancar, lectura sin HTTP, validación de JWT/configuración y contratos firmados. |
| CA-07 | Verificado | Hasta tres intentos recuperables y uno terminal; 401/503 al arrancar no impiden login. UserHttpTests pasa 84 casos; expiración/rechazo no programan readquisición, y disponibilidad no repite su consulta. |

**Regresión final Etapa 2:** `./mvnw -B -ntp clean verify`, 2026-11-02, 645 pruebas, cero fallos/errores/omisiones y BUILD SUCCESS (9:17 min). Copia temporal idéntica de src, comprobada byte a byte. Conserva todas las regresiones de Etapa 1, con proveedores HTTP ficticios y MySQL/Kafka temporales. No modifica ni consulta servicios reales de Catálogo/cátedra.

**Métricas Etapa 2:** 182 archivos / 8.878 líneas Java físicas a 173 / 8.553, reducción de nueve archivos y 325 líneas (comentarios/blancos incluidos en ambas mediciones). No se agrega ningún tipo productivo ni se traslada código a otro paquete. Fuera de integration no hay cambios productivos frente al baseline aceptado.

**Fundamento contractual:** anexo §§4/5.4 fija JWT técnico de larga duración y deja fuera revocación anticipada; §19 deja la política concreta de reintentos al alumno. No exige renovación transparente ni caché con historial. El acceso inicial sigue obteniéndose por el contrato protegido de Catálogo; su login ya solicita rememberMe=true. Ninguna modificación de Catálogo es necesaria.

**Garantías retiradas:** renovación/readquisición automática por demanda, foreground no bloqueante durante adquisición, cooldown entre ciclos, reutilización de candidato PENDING y blacklist/publicación tardía de la máquina propia. La adquisición puede demorar el evento de arranque por sus timeouts, pero no crea trabajo técnico posterior. Si falla o expira, se requiere reiniciar; salud técnica no equivale a disponibilidad funcional.

**Límites:** contratos externos y arranque verificados con proveedores ficticios; no es un nuevo ensayo de infraestructura real. Análisis de código Sonar no acreditado por Vortex403 y content roots del IDE; no afirmar Gate remoto. Javadoc/secretos y suite sí ejecutados correctamente.

**Desviaciones:** ninguna funcional; la validación de estados de aprovisionamiento se conserva en el DTO/adaptador HTTP. Se retiró solamente su representación interna. Etapa 2 cerrada localmente para revisión; detenerse antes de Etapa 3. No hubo operaciones Git mutables.

## Etapa 3 autorizada

Chequeo previo: RF-08/09/10 tienen CA-08/09/10 y validación en PLAN; T08 a T10 mantienen exclusivamente el alcance autorizado. Etapas 1 y 2 aceptadas; sin decisiones bloqueantes.

- [x] **T08 · CA-08/09:** Sustituir Delivery/inbox/replay por acciones validadas y claves durables, preservando transacción y orden de ACK. Adaptar solo recuperación afectada por retirar replay. Verificar evento temprano, rollback y duplicados.
- [x] **T09 · CA-10; depende de T08:** Migración V11 con conservación de IDs históricos y regresiones de datos/procesos/outbox. Retirar tests exclusivos de auditoría tras conservar sus garantías obligatorias en pruebas menores.
- [x] **T10 · CA-08/09/10; depende de T09:** Ejecutar pruebas enfocadas, clean verify y controles de calidad disponibles. Medir archivos/LOC contra 173 / 8.553, documentar límites de evidencia y detenerse antes de Etapa 4.


### Cierre de Etapa 3

Implementada únicamente la Etapa 3 autorizada; Etapas 1 y 2 conservadas. Las etapas siguientes no se iniciaron.

| CA | Estado y evidencia local |
| --- | --- |
| CA-08 | Verificado. ReservationIntegrationTests bloquea el commit y comprueba offset sin avanzar; rollback deja proceso y clave sin cambios y redelivery procesa. El commit SQL seguido de pérdida de respuesta/ACK se deduplica sin incrementar otra vez la versión. ReservationRecoveryIntegrationTests verifica además duplicados concurrentes y propagación del commit incierto. |
| CA-09 | Verificado. anActualKafkaRequestCanArriveBeforeTheRestResponse comprueba IDs y CONFIRMATION_PENDING durables antes de publicar desde el proveedor, y consume el evento antes de devolver REST. Desconocido queda sin proceso y con ID marcado; un registro posterior no provoca replay. Agregado y pruebas de teléfono/cancelación conservan cierres y rechazo corregible. |
| CA-10 | Verificado. v7AndV11PreserveProcessesOutboxAndEveryHistoricalEventId conserva IDs APPLIED/IGNORED/REJECTED/DEFERRED, procesos y outbox al migrar de V6 hasta V11. La tabla nueva tiene una columna y la antigua desaparece. Suite completa conserva usuarios, disponibilidad, acceso técnico y contratos HTTP externos con proveedores ficticios. |

**Regresión local (2026-11-02, árbol de trabajo de Etapa 3):** foco de seis suites, 122 pruebas correctas. Después, `./mvnw -B -ntp clean verify`: 642 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS; MySQL y Kafka temporales. Ejecución sobre copia aislada con src idéntico byte a byte al árbol actual, para evitar compilación concurrente del IDE. No usa .env ni servicios reales. Compilación/Javadoc de contratos y componentes modificados sin avisos. git diff --check correcto.

**Conceptos retirados:** ReservationDelivery y Origin; EventContent/hash del parser; ReservationInboxEntity y JpaReservationInboxRepository; deferredKnown, replayDeferred, replayDeferredKnown, hasReceipt y rescate específico de recibos; clasificaciones APPLIED/IGNORED/REJECTED/DEFERRED y sus ramas de auditoría/reclasificación; reconstrucción desde payload y sondeo de asociaciones futuras. ReservationAction existente sustituye el transporte de Delivery. Se conserva el store/adaptador transaccional y todos los demás writeId/claims para su etapa correspondiente.

**Esquema:** V11 reemplaza reservation_inbox por reservation_processed_event(event_id PK), sin editar migraciones previas. Desaparecen id de recibo, topic, partition_number, record_offset, message_key, process_id del recibo, fingerprint, payload, outcome, rejection, received_at y sus índices/constraints. No se eliminan los IDs/correlaciones del proceso ni del outbox. Todos los eventId históricos no nulos se copian, incluidos los diferidos; no vuelven a considerarse hechos nuevos.

**Comportamiento deliberadamente retirado:** auditoría/reconstrucción del cuerpo, detección de eventId reutilizado con otro contenido y aplicación futura de mensajes de procesos desconocidos. El anexo §15.1 exige clave funcional eventId y at-least-once; no exige esas capacidades adicionales. El flujo §10/17 confirma solo después de guardar la asociación local. Un hecho desconocido se ignora durablemente, sin importar reservas; datos inválidos se descartan sin SQL. Un fallo SQL se distingue de ambos casos y se propaga sin ACK.

**Crash:** antes del commit no queda efecto ni clave y Kafka puede volver a entregar. Después del commit y antes del ACK, redelivery encuentra el ID y no repite efectos. No se agregó rescate de commits ambiguos; los fallos conservadores del consumidor permiten redelivery.

**Preservación:** producción de usuarios, integración técnica, disponibilidad, controladores y componentes de teléfono/outbox idéntica al baseline. Las reglas del agregado no cambian, incluido rechazo reintentable hasta expiresAt. Recovery general solo pierde el paso de replay del inbox; sus consultas centrales siguen vigentes. Contratos con Catálogo/cátedra conservados en pruebas, sin modificar otros repositorios ni acreditar un nuevo ensayo real.

| Java productivo, líneas físicas | Antes | Después | Diferencia |
| --- | ---: | ---: | ---: |
| Archivos | 173 | 172 | -1 |
| Líneas | 8.553 | 8.285 | -268 |

**Calidad y límites:** SonarQube CLI secrets sin hallazgos. Análisis de código CLI bloqueado por Vortex 403 Forbidden; el puente IDE informa fuera de content roots. No se afirma análisis de código/Gate remotos nuevos. No hubo operaciones Git mutables ni publicación.

**Desviaciones del PLAN:** ninguna. Se usa INSERT estricto de la clave para que la unicidad haga rollback del efecto concurrente perdedor; no merge que pudiera aceptar dos efectos bajo un mismo ID. Detenerse antes de Etapa 4.

## Etapa 4 autorizada

Chequeo previo: RF-11/12/13 tienen CA-11/12/13 y validación en PLAN. T11 a T13 implementan solo esta etapa, sin decisiones bloqueantes; Etapas 1 a 3 aceptadas.

- [x] **T11 · CA-11/12:** Sustituir progreso/ciclos por intención mínima; reducir body/DTO/puertos y ejecutar cancelación acotada con consulta previa. Clasificar tests según PLAN y conservar requisitos en reemplazos menores.
- [x] **T12 · CA-13; depende de T11:** Mantener reclamación condicional y guard, proteger fallo tardío con identidad mínima existente; V12 conservadora y regresiones de concurrencia/reinicio/datos.
- [x] **T13 · CA-11/12/13; depende de T12:** Foco, clean verify completo, revisión local de calidad disponible, métricas frente a baseline y cierre. No iniciar Etapa 5.


### Cierre de Etapa 4

Implementada únicamente Etapa 4 autorizada, 2026-11-03. Etapas 1 a 3 conservadas; no se inició Etapa 5.

| CA | Estado y evidencia local |
| --- | --- |
| CA-11 | Verificado. ReservationCancellationTests/IntegrationTests conservan propiedad, estado confirmado, repetición de CANCELLED, evidencia ajena/inválida y timeout sin atribuir cierre. Consultar central precede cada POST; consulta vacía/fallida impide el efecto. Respuesta perdida converge por REST sin otro POST si ya está cancelada. |
| CA-12 | Verificado. Tres POST inciertos dejan RETRY_REQUIRED; revisiones posteriores solo consultan. Retry explícito sin body habilita otra ejecución, y dos solicitudes concurrentes convergen con un solo POST exitoso. Intención/claim interrumpidos se leen de SQL antes de actuar. Consulta fallida antes de todo POST mantiene PENDING; después de un POST incierto requiere retry explícito y sigue reconciliando. |
| CA-13 | Verificado. onlyOneConcurrentSqlClaimCanOwnTheCancellationExecution concede un permiso SQL; aLateFailureCannotStopANewerExecutionOrUndoAConfirmedCancellation protege UUID vigente y cierre. V12 conserva pendientes, agotadas y resueltas sin cambiar fases/IDs/plazos, manteniendo mayor fecha previa. Compara outbox e IDs procesados antes/después. Suite completa conserva usuarios, acceso técnico, disponibilidad y contratos con proveedores ficticios. |

**Flujo:** POST propio sin body comprueba propietario, acepta intención PENDING y reclama ejecución mediante SQL. Se consulta central; CANCELLED cierra localmente, CONFIRMED permite POST y cualquier ausencia/incertidumbre impide repetir. Hasta tres POST por ejecución. Rechazo definitivo, agotamiento o consulta fallida tras un POST incierto detienen en RETRY_REQUIRED. Scheduler continúa GET sin más POST y el propietario puede repetir la operación para reabrirla. Un reinicio relee intención/fecha y reconcilia antes de actuar; PENDING recuperado dispone de un nuevo límite local, RETRY_REQUIRED permanece detenido.

**Concurrencia conservada:** guard existente excluye trabajo activo local. Reclamación condicional de fecha + @Version concede un permiso SQL por ejecución, reservado según los timeouts acotados. writeId existente identifica únicamente esa ejecución; se comprueba antes del POST y al finalizar, para no aplicar un fallo tardío a otra intención. Evidencia válida de cancelación puede cerrar; un fallo nunca reabre CANCELLED. No se conserva ownership histórico de ciclos ni retryVersion. Los demás claims/rescates de commit no se alteran.

**Retirado:** CancellationProgress (y create/retry/matches/claim/isDue/canRetry/failed/resolve), CancellationRequestBody, record CancellationAcceptance, campos de progreso y versión históricos, claimCancellation/cancellationFailed del agregado, committedCancellation del adapter y CANCELLATION_RETRY_NOT_READY. CancellationView pierde requestedAt/lastFailure/retryVersion; conserva status/canRetry. REJECTED/RESOLVED dejan de ser estados de la intención, sustituida por PENDING/RETRY_REQUIRED y el cierre de reserva. Se retira Clock del mapper al quedar huérfano. No se agregan clases, puertos ni otra máquina de ciclos.

**Esquema:** V12 nueva elimina cancellation_requested_at, cancellation_retry_version, cancellation_attempts, cancellation_next_attempt_at, cancellation_last_failure, sus dos constraints y el índice de calendario propio. Conserva cancellation_status y recovery_next_attempt_at existentes; el diagnóstico utiliza reasonCode sin decidir transiciones. Pendientes no agotadas siguen PENDING; agotadas/rechazadas pasan a RETRY_REQUIRED; resueltas/cerradas pierden solo intención. Se conservan fases, correlaciones y mayor fecha de revisión anterior. Ninguna migración histórica cambia.

**Garantía abandonada:** presupuesto exacto y deduplicación de retries HTTP entre ciclos/reinicios. Cada POST propio recibido sobre RETRY_REQUIRED es una nueva solicitud, aunque sea una repetición del cliente. No autoriza repetir ciegamente el POST central: permanece la consulta previa y la cancelación central idempotente. El anexo §12 exige esa idempotencia; enunciado §8 exige reintentos acotados y recuperación, no versiones/counters históricos exactos. Reiniciar durante una ejecución puede conceder otro límite local de tres, sin fabricar resultado ni duplicar el efecto funcional.

**Regresión local (2026-11-03, árbol de Etapa 4):** foco 81 correctas; después `./mvnw -B -ntp clean verify`, 643 correctas, cero fallos/errores/omisiones, BUILD SUCCESS. MySQL/Kafka temporales, sin .env ni servicios reales. src verificado idéntico byte a byte al árbol actual. Javadoc de ocho contratos/componentes sin avisos, git diff --check correcto. No se acredita un nuevo ensayo real de Catálogo/cátedra.

**Preservación:** producción de login/usuarios, technical access, disponibilidad, inbox mínimo, outbox, StartReservationUseCaseImpl, cliente de holds y guard intacta respecto al baseline. Recovery general solo adapta la delegación de cancelación para no anidar guard ni reclamar dos veces la fecha compartida. Modelo de proceso, persistencia y DTO cambian únicamente la intención de cancelación y su proyección.

| Java productivo, líneas físicas | Antes | Después | Diferencia |
| --- | ---: | ---: | ---: |
| Archivos | 172 | 170 | -2 |
| Líneas | 8.285 | 8.163 | -122 |

**Incidencia de validación:** la primera suite completa tuvo un único fallo: 503 al corregir teléfono tras rechazo (ReservationPhoneIntegrationTests), sin cambios en ese test ni en producción telefónica. Ocho repeticiones estrictas en copia diagnóstica pasaron; se observó conflicto de versión recuperable, sin reproducir el 503 ni confirmar su causa. Se conservó la evidencia del primer fallo y se repitió clean verify completo sin cambios de src ni aserciones. No se presenta esa primera ejecución como exitosa ni se acredita ausencia de intermitencia.

**Calidad y límites:** CLI secretos sin hallazgos. Análisis de código CLI bloqueado por Vortex 403 Forbidden, IDE fuera de content roots; no se afirma análisis/Gate remotos nuevos. Sin operaciones Git mutables, publicación ni Compose real.

**Desviaciones:** se conserva la identidad mínima writeId de ejecución, no versiones históricas; eliminarla permitiría que un fallo tardío detuviera una ejecución posterior, demostrado en SQL. No se retiran los demás mecanismos de commit ambiguo de Etapa 5. El rescate de aceptación ligado a CancellationAcceptance/versiones desaparece con ese contrato: un commit incierto de la intención devuelve error conservador y recovery relee SQL antes de HTTP. Sin cambios de alcance adicionales.

## Etapa 5 autorizada

Chequeo de coherencia: RF-14/15/16 tienen CA-14/15/16 y validación en PLAN; la clasificación separa autoría de commit de exclusión real. No se reemplaza writeId por otro identificador y no hay decisión bloqueante para retirar los rescates.

- [x] **T14 · CA-14/16:** Retirar lecturas de rescate, fallback ambiguo y transporte de UUID para autoría. Conservar permisos conocidos, conflicto único sin permiso y reintentos SQL optimistas limitados.
- [x] **T15 · CA-14/15; depende de T14:** Reescribir tests exclusivamente de continuidad, conservar CommitFaultDataSource y demostrar recuperación/concurrencia antes/después del commit. Outbox conserva esquema/evento/publicador y deduplicación.
- [x] **T16 · CA-14/15/16; depende de T15:** Foco y clean verify completo; calidad local disponible, métricas frente a170/8.163, límites y cierre. No iniciar Etapa 6.


### Cierre de Etapa 5

Implementada únicamente Etapa 5 autorizada, 2026-11-03. Etapas 1 a 4 conservadas; no se inició Etapa 6. Clasificación completa de writeId en PLAN, categorías1/2/3/4; sin reemplazarlo por otro identificador.

| CA | Estado y evidencia local |
| --- | --- |
| CA-14 | Verificado. Inserción AFTER devuelve503 con checkpoint existente y cero holds; BEFORE no deja fila y retry posterior conocido funciona. Orquestación real no llama confirm ante error BEFORE/AFTER del checkpoint; SQL confirma estado previo/persistido sin devolver permiso. Cancelación BEFORE/AFTER de claim no llama central; error al guardar su resultado no se rescata como éxito. Aceptación telefónica devuelve503 y retry del UUID conserva evento y una fila. |
| CA-15 | Verificado. recoveryReadsThePersistedHoldCheckpointWithoutRepeatingCreation usa hold central real ficticio y SQL real: AFTER recupera HELD y confirma una vez con canal disponible; BEFORE no inventa IDs ni repite creación. CONFIRMATION_PENDING ambiguo se reconcilia sin POST, incluso después de vencer; evidencia central posterior converge. Concurrencia existente de insert/confirmación/cancelación concede un permiso por estado. |
| CA-16 | Verificado. Suite completa mantiene login, technical access, disponibilidad, autorización, cierre, Kafka temprano/tardío/dedup y outbox con mismo eventId/contenido. Cancelación mantiene la identidad mínima de ejecución y protección frente a fallo tardío. CommitFaultDataSource sigue cubriendo atomicidad y fallos reales de frontera JDBC. |

**Semántica nueva:** retorno normal del proxy transaccional permite continuar. Error SQL/de commit devuelve ReservationStorageException sin leer autoría para convertirlo en permiso. Solo SQL revertido por conflicto optimista permite hasta tres reintentos locales; conflicto único valida repetición sin conceder nueva creación. No hay lecturas de rescate para conexión/commit incierto.

**Casos:** antes del commit, rollback conserva el estado previo y no se ejecuta POST. Si el commit persistió pero devolvió error, no se ejecuta desde ese caller: recovery relee el checkpoint. HELD puede reanudarse adquiriendo una transición confirmada; CONFIRMATION_PENDING exige consulta y nunca permite repetir a ciegas. Si no persistió, recovery no inventa IDs ni fases. Una intención de creación sin IDs mantiene la política previa de recuperación. Una consulta vacía no demuestra que no hubo transporte y puede conservar incertidumbre; no se agregó un estado/flag para reconstruirla. Error al persistir el resultado de un POST ya ejecutado tampoco acredita éxito local; evidencia central posterior resuelve sin otro POST si ya ocurrió.

**Qué permanece de writeId:** fencing independiente del rescate, para cerrar únicamente una confirmación aún no enviada por su ejecución vigente y para ignorar fallos tardíos de cancelación. Unicidad del insert y @Version sobre la transición adquieren permiso; writeId no lo rescata si falló el commit. El campo/columna y su invalidación por mutación permanecen, sin otro nonce ni una nueva API transaccional. No representa correlación contractual. No se requiere migración porque no cambia el esquema ni desaparece metadata usada.

**Retirado:** ownWrite tras excepción de insert; filtros/reconsultas por UUID de beginInitialConfirmation y beginCancellationAttempt; lectura CANCELLED de fallback de confirmCancellation; committedPhone y su lectura tras error genérico; overload write(Supplier, Supplier) y Supplier ambiguo; argumentos UUID de autoría transportados desde adapter a store para update, requestCancellation y los dos claims. El UUID necesario se genera dentro de la transacción. No desaparecen clases/columnas ni se eliminan claims reales. Mapper, entidades, dominio, puertos, configuración y wiring siguen iguales.

**Tests:** no se elimina CommitFaultDataSource ni ninguna suite. Expectativas de éxito inmediato tras commit AFTER se sustituyen por error/no permiso y recuperación posterior; las pruebas mixtas conservan propiedad, IDs, atomicidad y exclusión. Se agregan ocho casos observables de orquestación/JDBC/recovery. El test telefónico compara la identidad/contenido inmutable del evento, no contadores de publicación que cambian concurrentemente. El ensayo de recovery HELD declara canal disponible mediante su puerto, sin simular SQL ni el caso de uso.

**Garantía abandonada:** continuidad transparente y disponibilidad inmediata después de un error local de commit. El enunciado §8 exige idempotencia, reintentos y recovery; no obliga a transformar un error JDBC ambiguo en éxito demostrando qué ejecución escribió. Se preserva consistencia a costa de disponibilidad. No cambia la política de respuesta perdida del POST central de creación.

**Regresión local (2026-11-03, árbol de Etapa5):** foco acumulado115 correcto, después `./mvnw -B -ntp clean verify`: 651 correctas, cero fallos/errores/omisiones, BUILD SUCCESS. MySQL/Kafka temporales, sin .env ni servicios reales. src idéntico byte a byte al árbol actual. Javadoc de los dos componentes modificados sin avisos, git diff --check correcto. No se acredita nuevo ensayo real de Catálogo/cátedra.

**Preservación:** producción cambia solo JpaReservationProcessAdapter y TransactionalReservationStore. Usuarios/login, technical access, disponibilidad, aplicación/dominio/puertos, mapper/entidades, esquemas/migraciones, contrato HTTP, componentes de inbox/outbox y confirmación/creación no cambian. La aceptación HTTP telefónica sí pasa a error conservador ante commit ambiguo; su transacción, persistencia, evento y publicación permanecen iguales. Documentación solo en esta funcionalidad, sin reescribir README ni antecedentes.

| Java productivo, líneas físicas | Antes | Después | Diferencia |
| --- | ---: | ---: | ---: |
| Archivos | 170 | 170 | 0 |
| Líneas | 8.163 | 8.120 | -43 |

**Calidad y límites:** secretos CLI sin hallazgos; análisis de código CLI bloqueado por Vortex403, IDE fuera de content roots. No se afirma Gate/CI remoto nuevo ni publicación. Sin operaciones Git mutables.

**Desviaciones:** writeId no se retira por completo: sus usos de ejecución/fencing son distintos del rescate y eliminarlos exigiría otra identidad equivalente. Se conserva esa parte existente, sin renombrarla ni introducir sustitutos. No se inicia Etapa6.


## Etapa 6 autorizada

Chequeo previo RF-17/18 → CA-17/18 → PLAN/T17-19 completo, sin decisiones bloqueantes. Implementación autorizada explícitamente por el usuario; Etapas1-5 cerradas.

- [x] **T17 · CA-17:** Eliminar14 fábricas triviales, anotar implementaciones y conservar configuración real, nombres calificados y frontera transaccional. Medir reducción de wiring.
- [x] **T18 · CA-18; depende de T17:** Introducir Lombok solo para constructores/accesores mecánicos; verificar API y cuerpos funcionales, sin generar exposición adicional. Medir reducción separada.
- [x] **T19 · CA-17/18; depende de T18:** Ejecutar clean verify, comprobar contexto/JAR y regresiones; revisión local disponible, métricas y cierre con límites. Detenerse al reportar.


### Cierre de Etapa 6

Implementada únicamente Etapa6 autorizada, 2026-11-03. Se conservan las reglas de Etapas1-5. Inventario de las14 fábricas/componentes y de los13 beans explícitos restantes en PLAN. Sin puertos ni tipos Java eliminados, sin clases nuevas, sin cambios en migraciones/propiedades/contratos ni en otro servicio.

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-17 | Verificado localmente | clean verify:651 tests,0 fallos/errores/omisiones. Contextos Spring con servidor HTTP/MySQL temporal arrancan; no ambigüedad de beans ni ciclos. UserHttpTests84 y SecurityHttpTests48; TechnicalAccessBehaviorTests14, TechnicalAccessHttpTests25 y lifecycle1; disponibilidad HTTP18 y cliente45 preservan el contrato con Catálogo. |
| CA-18 | Verificado localmente | Los40 tipos con Lombok conservan su API pública; comparación con el working tree inicial confirma cuerpos funcionales iguales fuera de las dos configuraciones. Dominio, recursos/migraciones y todos los tests idénticos. Store y sus @Transactional conservados. RecoveryIntegration27, CancellationIntegration22, ReservationIntegration26, PhoneIntegration21 y demás suites mantienen concurrencia, commit conservador, inbox/outbox y estados finales. |

**Reducción medida:**170 archivos/8.120 líneas físicas Java productivas a170/7.742,378 líneas menos. Wiring/configuración:100; Lombok:278. ReservationConfiguration288→198 líneas,22→13 fábricas; UserConfiguration74→36,7→2. Lombok sustituye37 constructores y40 getters, sin lógica ni nuevas exposiciones; excluido del JAR ejecutable y sin dependencia de ejecución. Las configuraciones de recursos y parámetros derivados siguen explícitas. No se purgaron ni modificaron tests.

**Regresión:** ./mvnw -B -ntp clean verify en copia aislada de las fuentes actuales, BUILD SUCCESS,7:20min,2026-11-03 09:54:02-03. Fuentes y POM coinciden byte a byte con lo verificado;36 suites/651 tests sin omisiones. Una primera invocación detectó un cierre sintáctico faltante en UserConfiguration, corregido antes de ejecutar tests; la suite final completa es la evidencia de cierre. Sin ensayo real nuevo contra Catálogo/cátedra ni cambios en sus contratos.

**Calidad disponible:** Javadoc de43 fuentes modificadas sin avisos; diff --check correcto. SonarQube CLI1.10 no detectó secretos. Análisis de código bloqueado por Vortex403; IDE informa archivos fuera de content roots. No se acredita análisis local de código ni Quality Gate/CI remoto nuevos.

**Desviaciones:** ninguna funcional. No se anotan como componentes los casos de uso que necesitan parámetros derivados solo para eliminar sus fábricas, ni se crean beans escalares o abstracciones sustitutas. TransactionalReservationStore conserva su frontera detrás del proxy y la semántica de commit conocida. Detenerse tras este cierre para revisión del usuario.


### Verificación del ajuste de calidad posterior (2026-11-03)

Autorizadas correcciones sin cambio funcional (CA-17/18 conservados). Aplicadas las correcciones de diez avisos: nombre markProcessed con SQL idéntico, extracción de cancelOnce/finishAttempt/predicados, ternario y declaraciones de pruebas, recurso sin nombre y limpieza de owner/consulta y sqlVersion sin uso. CancellationAttempt privado representa solo el resultado local de una llamada; sin nuevas fases, metadata persistida, contratos o garantías.

Caracterización:14 pruebas de cancelación pasan con la producción anterior y con la nueva. Se agregan dos casos observables (fallo inicial de GET sin POST y respuesta no correlacionada sin segundo POST). Suite final ./mvnw -B -ntp clean verify:653 tests,0 fallos/errores/omisiones,BUILD SUCCESS,7:37min,2026-11-03 11:11:01-03; fuentes/POM idénticos a la copia verificada. CancelaciónSQL22, gateway26, recoverySQL27 y teléfonoSQL21 sin regresiones. No se eliminaron tests. Dominio, recursos/migraciones y los demás slices permanecen idénticos al cierre de Etapa6; Store/repositorio solo cambian el nombre de método interno. API pública del caso de uso sin cambios.

Complejidad execute23→15 comprobada con CognitiveComplexityVisitor del analizador Java instalado; sin try anidado. Javadoc3 sin avisos, diff --check correcto, secretosCLI0. Análisis general CLI sigue bloqueado por403 e IDE por content roots; no afirmar nuevo Quality Gate remoto. TestIntegrationSupport permanece sin cambios: Nimbus exige Date en expirationTime y el cálculo usa Instant. Producción170/7.742→170/7.765 líneas físicas (+23 por extracción, sin nuevos archivos Java). Sin desviaciones funcionales ni trabajo adicional iniciado.

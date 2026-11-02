# TASKS: Recuperar procesos pendientes y resultados inciertos (CU-09)

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), [#14](https://github.com/prog2-perassiferrara/backend-turnos/issues/14).
**Estado:** Implementación y validación local completas. Validación remota de esta revisión pendiente de publicación.
**Aprobaciones:** SPEC y PLAN aprobados explícitamente el 2026-11-02; TASKS aprobadas explícitamente el 2026-11-02.
**Autorización de implementación:** explícita el 2026-11-02.

## Chequeo previo

2026-11-02: chequeo documental RF → CA → PLAN/TASKS correcto: ocho RF y diez CA cubiertos; cinco tareas con dependencias sin ciclos ni alcance nuevo. D-01/D-02 confirmadas y sin decisiones pendientes en PLAN. Base de código `0deefd3`; carpeta documental local sin seguimiento. No se ejecutaron pruebas ni builds para la preparación. Autorizaciones y estado contrastados al iniciar: implementación autorizada, documentos aprobados y base de código sin cambios ajenos. Antes de implementar, contrastar autorizaciones y estado real; revalidar solo lo afectado si cambian los documentos.

## Tareas

- [x] **T-01 · Continuación segura y exclusión del trabajo activo.**
  - **Objetivo:** compartir la continuación del hold conocido sin duplicar confirmaciones ni recuperar una petición todavía activa.
  - **Alcance:** puerto/adaptador `ReservationExecutionGuard`, scope propio no anidado y liberación por token; relectura del inicio después de adquirirlo. Ampliar `StartReservationUseCase` con `resumeKnownHold` y agregar la reclamación transaccional HELD→CONFIRMING con permiso de escritura propia acreditado. Incorporar el cierre EXPIRED exclusivamente para el hold vencido cuya confirmación nunca se intentó. Respetar abandono, plazo y preparación del canal. Introducir contratos junto con implementaciones y dobles coherentes, sin métodos provisionales; Javadoc en español y DI por constructor.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-02/RF-03/RF-06/RF-07; CA-02/CA-03/CA-09/CA-10. PLAN: solución, identificadores y concurrencia.
  - **Validación:** iniciar `ReservationRecoveryTests` y ampliar las pruebas de inicio/transición y persistencia. Usar barreras para dos ejecutores, abandono concurrente y scope ocupado; exactamente un POST o ninguno si el permiso no se acredita. Simular fallo antes/después del commit y verificar que leer CONFIRMING ajeno no habilita otra confirmación. Contrastar HELD vencido con CONFIRMING/incierto y bordes de expiresAt; error o retorno anticipado libera solo el scope propio. Detectar cualquier archivado prematuro de trabajo activo o repetición del POST.

- [x] **T-02 · V7 y planificación durable de revisiones.**
  - **Objetivo:** seleccionar y reclamar recuperación acotada conservando fechas y contadores después de un reinicio.
  - **Alcance:** V7 aditiva, `recovery_attempts`, `recovery_next_attempt_at` e índices del PLAN; entidad, repositorios JPA, adapter/store y contratos correspondientes. Lotes ordenados de diez, elegibilidad inicial desde updated_at y pausa; tres revisiones por ciclo por defecto, separación y cooldown existentes. Reprogramar no modifica fase ni writeId de negocio. No mover estos metadatos al agregado ni reiniciar presupuestos de teléfono/cancelación. No editar V1–V6 ni crear tablas.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01/RF-04/RF-06/RF-07; CA-01/CA-05/CA-06/CA-08/CA-10. PLAN: persistencia y configuración.
  - **Validación:** iniciar `ReservationRecoveryIntegrationTests` con MySQL temporal. Migrar una base V6 poblada y conservar usuarios, procesos, inbox/outbox, intención y exclusión por propietario. Con Clock controlado, comprobar límites, orden, pausa, continuidad tras reinicio y registros anteriores sin programación. Probar reclamaciones simultáneas y commit ambiguo; una revisión no concede permiso para un POST ni altera contadores de negocio. Persistencia no acreditada impide actuar.

- [x] **T-03 · Reprocesar recibos diferidos ya asociables.**
  - **Objetivo:** aplicar una sola vez eventos soportados conservados antes de poder identificar su proceso local.
  - **Alcance:** selección acotada DEFERRED/UNKNOWN_PROCESS con asociación local; reutilizar `ReservationActionParser` y contenido/origen conservados. Releer y bloquear el recibo durante una transacción corta, validar fingerprint/correlación y aplicar sobre la versión actual del proceso. Actualizar el mismo recibo y los cierres correspondientes de forma atómica. No invocar accept ordinario, insertar otra entrega ni confirmar otro offset. Excluir rechazados, UNHANDLED_ACTION y registros sin asociación local.
  - **Dependencias:** T-02.
  - **Referencias:** RF-05/RF-07; CA-07/CA-10. PLAN: conservación/restauración e inbox.
  - **Validación:** ampliar la integración SQL con evento previo a la asociación, duplicado, rollback, reinicio y reproceso concurrente con consumidor/resultado final. Recepción durable antes del offset y una aplicación funcional cuando se acredita la asociación. Verificar que fallo transaccional deja el recibo recuperable, tipo/grupo/hold incompatibles no se aplican y un evento viejo no reabre finales. La prueba debe fallar si el reproceso usa accept y deja el evento sin aplicar por deduplicación.

- [x] **T-04 · Coordinación automática y wiring.**
  - **Objetivo:** recuperar sin consultas de KMP y mantener el trabajo pendiente hasta obtener evidencia suficiente.
  - **Alcance:** `RecoverReservationProcessesUseCase`/implementación y `ReservationRecoveryDispatcher`; aplicación lista habilita la tarea periódica sin HTTP en el hilo de arranque. Reexaminar recibos y procesar los lotes del PLAN usando guard y planificación durables. Delegar el hold vigente sin anidar scopes; archivar creación huérfana sin IDs según CU-04, sin otro hold. Reutilizar lectura central sin fechas/estado, paginada y exacta; continuar verificaciones espaciadas después de vencer. Conservar autoridad y presupuestos de los workers telefónico y de cancelación. Wiring por constructor, parser/guard compartidos y pool de scheduling de tres hilos, sin variables obligatorias ni dependencias nuevas.
  - **Dependencias:** T-01/T-02/T-03.
  - **Referencias:** RF-01/RF-02/RF-03/RF-04/RF-05/RF-06/RF-07/RF-08; CA-01/CA-02/CA-03/CA-04/CA-05/CA-06/CA-07/CA-08/CA-09/CA-10. PLAN: matriz de progreso, secuencia y operaciones.
  - **Validación:** completar unitarias de recuperación y su integración. Arrancar con trabajo pendiente y comprobar detección sin GET del usuario; caída REST antes/después del plazo y recuperación posterior con evidencia exacta. PHONE_PENDING sin evento no habilita teléfono; FAILED sigue siendo FAILED. Ausencia, contradicción o recorrido parcial conservan incertidumbre y no repiten confirmación. Reinicio no cambia UUID/contenido de teléfono ni abre otro ciclo de cancelación agotado. Una lectura HTTP lenta no impide que el worker telefónico progrese; consultas/recuperación/eventos simultáneos no reabren finales ni exponen datos ajenos.

- [x] **T-05 · Regresión, ensayo del artefacto y cierre.**
  - **Objetivo:** acreditar los diez CA y mantener documentación y límites coherentes con el código final.
  - **Alcance:** README/Javadoc pertinentes, regresión backend, calidad local disponible y ensayo del JAR con MySQL temporal/proveedores ficticios. Reutilizar escenarios existentes sin duplicarlos. Completar el cierre de este documento con evidencia, regresión y desviaciones reales. Las comprobaciones remotas posteriores son retroalimentación, no tareas de implementación. No modificar otros servicios ni inventar eventos centrales para completar un ensayo.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01/RF-02/RF-03/RF-04/RF-05/RF-06/RF-07/RF-08; CA-01/CA-02/CA-03/CA-04/CA-05/CA-06/CA-07/CA-08/CA-09/CA-10. PLAN: estrategia de validación.
  - **Validación:** foco nuevo `./mvnw -B -ntp -Dtest=ReservationRecoveryTests,ReservationRecoveryIntegrationTests verify`, regresiones afectadas y `./mvnw -B -ntp clean verify`. Confirmar descubrimiento/resultados y JaCoCo XML/HTML; no presentar suites repetidas como una corrida completa exitosa. Revisar Sonar CLI/IDE tras comprobar conexión/alcance, sin configurar credenciales ni hooks. Reiniciar el JAR con confirmación incierta, espera de solicitud y trabajo de teléfono/cancelación conservado; comprobar recuperación sin GET, mismo progreso y ausencia de nuevos holds/confirmaciones duplicadas. Mantener separadas evidencia controlada y central; una lectura real de un proceso propio existente no acredita todo el intercambio Kafka. Retirar solo recursos temporales propios y comprobar ausencia de secretos.

## Cierre

- **E-01 · Comportamiento nuevo:** `ReservationRecoveryTests` (9) y `ReservationRecoveryIntegrationTests` (23). Cobertura de continuación conocida, scopes concurrentes, permisos SQL, commit ambiguo, ciclos persistidos, V6→V7 con usuarios/inbox/outbox, contenido diferido alterado, tipos desconocidos, reproceso concurrente y cierres. La última suite verificó además dos HTTP bloqueados simultáneamente con publicación telefónica progresando en el tercer hilo.
- **E-02 · Regresión:** base `0deefd3` más estos cambios, 2026-11-02, en copia aislada exacta de pom/wrapper/src para evitar bytecode incremental ajeno a Maven. `./mvnw -B -ntp clean verify` recorrió 663 casos y terminó con dos fallos de la suite telefónica: fixtures de casos anteriores seguían pendientes y el recuperador los consultaba. Se aislaron esos fixtures sin reducir aserciones, y se añadieron pruebas de integridad/concurrencia. `./mvnw -B -ntp -Dtest=ReservationPhoneIntegrationTests,ReservationRecoveryIntegrationTests verify` pasó 43 casos; la nueva comprobación de los tres workers motivó otra ejecución completa de `ReservationRecoveryIntegrationTests`, 23 correctas y BUILD SUCCESS. Los 36 informes vigentes suman 667 pruebas, sin fallos, errores ni omisiones; no es una única corrida completa exitosa de 667. Las fuentes coinciden con el manifiesto de esta validación. La rama de #14 parte de `5eaafca`, merge de #29, con árbol idéntico a `0deefd3`; no hubo cambios de código al cambiar de base.
- **E-03 · Cobertura y documentación:** JaCoCo XML/HTML, 2946/3117 líneas (94,51 %) y 1428/1749 ramas (81,65 %). Javadoc de los 16 Java productivos modificados/nuevos, sin errores ni avisos con doclint habilitado salvo missing. README actualizado con recuperación, límites y tres tareas periódicas.
- **E-04 · JAR y reinicios:** 18 comprobaciones correctas con siete arranques, MySQL 8.4.8 temporal y proveedores ficticios. Recuperación sin GET del propietario, indisponibilidad después del plazo, restablecimiento tras reinicio, cierre seguro del hold nunca intentado, UUID/contador de teléfono conservados, cancelación agotada resuelta solo por lectura y un mismo recibo aplicado sin duplicarlo al reiniciar. Fixtures SQL/eventos controlados, no mensajes auténticos de cátedra. Sin nuevos holds/confirmaciones/cancelaciones en el ensayo de recuperación; no se modificaron los Compose reales y se retiraron contenedor/clave temporales. El JAR contiene V7 y las clases compiladas exactas, sin .env, PEM ni la sonda SQL de test. SHA-256 ensayado: `a4cf37a1ae526edc5795951f54a2a299f8fd3bb8e94c794623d121cad6f59ef2`.
- **E-05 · Calidad y límites:** Sonar CLI conectado y sin secretos. Vortex no disponible para esta conexión (403) e IDE con archivos fuera de sus raíces de análisis; no se cuentan como inspecciones de código superadas. El próximo CI/análisis de SonarCloud requiere que el usuario publique los cambios. Las incidencias centrales pendientes no se reinterpretaron como nuevos contratos.

| CA | Estado | Tareas y evidencia prevista |
| --- | --- | --- |
| CA-01 | Verificado localmente | T-02/T-04/T-05: recuperación tras reinicio sin consulta del usuario. |
| CA-02 | Verificado localmente | T-01/T-04/T-05: permiso propio y una confirmación como máximo. |
| CA-03 | Verificado localmente | T-01/T-04/T-05: sin archivar trabajo activo ni recrear un hold sin IDs. |
| CA-04 | Verificado localmente | T-04/T-05: reconciliación exacta y ausencia/PHONE_PENDING inconclusos. |
| CA-05 | Verificado localmente | T-02/T-04/T-05: envío y presupuestos telefónicos conservados. |
| CA-06 | Verificado localmente | T-02/T-04/T-05: cancelación/abandono sin reiniciar ciclos de efectos. |
| CA-07 | Verificado localmente | T-03/T-04/T-05: recibo durable y aplicación única tras asociación. |
| CA-08 | Verificado localmente | T-02/T-04/T-05: consultas espaciadas tras el plazo y convergencia automática. |
| CA-09 | Verificado localmente | T-01/T-04/T-05: EXPIRED local solo con confirmación nunca intentada. |
| CA-10 | Verificado localmente | T-01/T-02/T-03/T-04/T-05: concurrencia, propiedad y permiso durable. |

**Regresión:** E-02 identifica la ejecución completa fallida y las repeticiones exitosas; E-03/E-04 corresponden al código final. La evidencia local no sustituye el próximo CI ni acredita emisión central de Kafka.

**Desviaciones del PLAN:** ninguna de alcance o arquitectura. El detalle de vencimiento entre claim y transporte está aclarado en PLAN. Se corrigió el aislamiento de fixtures telefónicos para el nuevo trabajo automático, conservando las comprobaciones existentes; E-02 distingue ese fallo y su repetición.

**Límite de integración real:** siguen sin respuesta los reportes de eventos ausentes, FAILED sin evento y ocupaciones durante PHONE_PENDING. No se presupone su causa ni se modifica el contrato para compensarlos.

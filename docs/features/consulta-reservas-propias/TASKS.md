# TASKS: Consultar reservas y procesos propios

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), [#12/CU-07](https://github.com/prog2-perassiferrara/backend-turnos/issues/12).
**Estado:** Implementación y validación local completas; feedback remoto pendiente.
**Aprobaciones:** SPEC y PLAN aprobados explícitamente el 2026-11-01. TASKS aprobadas explícitamente el 2026-11-01.
**Autorización de implementación:** explícita el 2026-11-01 («apruebo implementa»).

## Chequeo previo

2026-11-01: comprobación documental RF → CA → PLAN/TASKS: ocho RF vinculados a nueve CA, todos con validación y tareas; seis tareas con dependencias sin ciclos y sin alcance nuevo. D-01 a D-05 resueltas. Antes de implementar, contrastar nuevamente el estado real y revalidar solo lo afectado por cambios sustanciales. Ninguna prueba funcional de CU-07 se ha ejecutado.

## Tareas

- [x] **T-01 · Contratos propios y nombres históricos.**
  - **Objetivo:** representar el listado y conservar la descripción del profesional sin alterar el ciclo del proceso.
  - **Alcance:** modelos y puerto entrante del PLAN; ampliar `ReservationProcess`, `CentralAppointment` y puertos salientes. Nombre opcional, primera captura válida completa y conservación en todas las transiciones, incluso tras cerrar. Correlación por propietario, proceso/paciente/selección y reserva conocida; estados y reglas existentes sin nuevas fases. Contratos/Javadoc en español, dominio puro y constructor DI.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-02/RF-03/RF-04/RF-07/RF-08; CA-02/CA-03/CA-04/CA-07/CA-09. PLAN: modelos, datos y estados.
  - **Validación:** ampliar `ReservationProcessTests` con nombres ausentes/presentes, enriquecimiento tras cierre, omisiones posteriores y transiciones existentes; ninguna evidencia ajena o contradictoria reabre un cierre. Comprobar validación de página/tamaño/desplazamiento con resultados conocidos, sin tipos Spring en dominio ni APIs centrales en los modelos públicos.

- [x] **T-02 · V5 y lectura local paginada consistente.**
  - **Objetivo:** conservar nombres y obtener pendiente, historial y total exclusivamente propios desde SQL.
  - **Alcance:** dos columnas opcionales de V5, entidad/mapper y `readOwn` en repositorio, adaptador y escritor existentes. Transacción de lectura `REPEATABLE_READ`; pendiente por `active_owner_id`, su último envío telefónico e historial propio con `active_owner_id IS NULL`. Orden fijo por fecha/hora descendentes y UUID ascendente, paginación SQL y total sin pendiente. Persistir nombres junto con la reconciliación existente; no alterar V3/V4 ni crear otra tabla.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01/RF-03/RF-04/RF-06/RF-07/RF-08; CA-01/CA-02/CA-04/CA-06/CA-07/CA-09. PLAN: persistencia y migración.
  - **Validación:** iniciar `OwnReservationsIntegrationTests` con MySQL temporal. Migrar V4 poblada sin pérdida de procesos/inbox/envíos; nombres inicialmente null y persistentes tras reiniciar el contexto. Mezclar propietarios, empates, fechas pasadas/futuras y estados; comprobar páginas de 20/100, total, página válida vacía y pendiente siempre separado. Usar barreras para comprobar que contenido/conteo/pendiente pertenecen a una lectura consistente y que fallos SQL no producen una lista vacía exitosa.

- [x] **T-03 · Consulta central paginada y acotada.**
  - **Objetivo:** obtener evidencia propia sin hacer una consulta remota por cada fila.
  - **Alcance:** ampliar `CentralAppointmentGateway`, adaptador y DTO existentes. Paciente derivado, sin filtros de estado/fecha; máximo tres páginas de 100 y presupuesto global `2 × httpTimeout`, sin retries. Interpretar metadata sin seguir destinos arbitrarios, conservar evidencia válida recibida y señalar recorrido incompleto. Deduplicar correlaciones, descartar evidencia ajena/contradictoria y mantener la lectura puntual anterior. Un 401 central invalida el token por el puerto público existente, sin otro login.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01/RF-02/RF-04/RF-05/RF-08; CA-01/CA-02/CA-04/CA-05/CA-08. PLAN: lectura central y límites.
  - **Validación:** ampliar `CentralAppointmentGatewayTests` con HTTP ficticio real: varias páginas, metadata ausente/incompatible, campos adicionales, horas HH:mm/HH:mm:ss, nombres ausentes, duplicados y paciente ajeno. Retener/interrumpir cuerpos y fallar una página posterior: como máximo tres GET, presupuesto compartido y evidencia previa conservada con aviso. Comprobar cero POST, ninguna URL fuera del proveedor configurado y una sola petición en la consulta puntual existente.

- [x] **T-04 · Listado propio y reconciliación sin retrocesos.**
  - **Objetivo:** combinar la página local con evidencia verificable y comunicar sus límites.
  - **Alcance:** `ListOwnReservationsUseCaseImpl`: lectura inicial, consulta central, reconciliación de coincidencias de hasta 101 procesos y relectura consistente. No importar reservas centrales sin asociación local, aunque coincida el paciente. Conservar la exclusión del pendiente en los totales y distinguir verificación por registro/envelope según PLAN. HTTP fuera de transacciones, control optimista y cierre de envíos existentes; ausencia no prueba cierre ni libera un proceso.
  - **Dependencias:** T-02/T-03.
  - **Referencias:** RF-01/RF-02/RF-03/RF-04/RF-05/RF-08; CA-01/CA-02/CA-03/CA-04/CA-05/CA-08/CA-09. PLAN: recorrido, concurrencia y verificación.
  - **Validación:** nueva `ListOwnReservationsTests`, con Clock y puertos controlados, más integración SQL. Cubrir central vacío/ajeno/parcial/caído, registro sin asociación, datos solo locales y verificación individual/global. Con barreras, cancelar mientras se espera REST y cerrar un pendiente durante la consulta: devolver la última versión, sin duplicarlo entre pendiente/historial ni inventar verificación para filas recién aparecidas. Nombres permanecen tras omisiones; repetir lecturas no genera acciones centrales, publicaciones, ampliaciones de plazo o reaperturas.

- [x] **T-05 · API protegida e integración del recorrido.**
  - **Objetivo:** exponer el listado con su contrato estable y errores seguros.
  - **Alcance:** GET `/api/reservations`, DTO/envelope, mapper, wiring y advice existentes; dos nombres opcionales en la respuesta de proceso. Página cero/tamaño 20 por defecto, máximo 100 y sin filtros ni orden configurable. Propietario desde JWT, `ROLE_USER`, no-store, 400 `INVALID_RESERVATION_PAGE` y 503 `STORAGE_UNAVAILABLE`; preservar 401/403 y contratos anteriores. No exponer teléfono ni identificadores centrales.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01/RF-03/RF-04/RF-05/RF-06/RF-07/RF-08; CA-01/CA-03/CA-04/CA-05/CA-06/CA-07/CA-09. PLAN: HTTP y configuración.
  - **Validación:** completar `OwnReservationsIntegrationTests` con registro/login y JWT reales. Dos usuarios y credenciales sin permiso; entrada no numérica, negativa, excesiva y desplazamiento no representable. Comprobar JSON, estados, nombres/null, orden/página/total/pendiente, códigos y no-store, también con cátedra/Catálogo inaccesibles. Parámetros de identidad no permiten consultar a otro usuario. Verificar respuesta de respaldo vacía con aviso y compatibilidad de los endpoints anteriores.

- [x] **T-06 · Regresión, ensayo y cierre.**
  - **Objetivo:** comprobar los nueve CA y dejar evidencia local reproducible.
  - **Alcance:** README y Javadoc pertinentes, suite completa, revisión local de calidad y ensayo del artefacto con MySQL temporal/proveedor ficticio: listar, paginar, conservar nombres, reiniciar y consultar con proveedor caído. Recursos aislados; no modificar servicios personales ni otros repositorios. Registrar aquí resultados, regresión y desviaciones, sin informes redundantes.
  - **Dependencias:** T-05.
  - **Referencias:** RF-01/RF-02/RF-03/RF-04/RF-05/RF-06/RF-07/RF-08; CA-01/CA-02/CA-03/CA-04/CA-05/CA-06/CA-07/CA-08/CA-09. PLAN: estrategia de validación.
  - **Validación:** foco y `./mvnw -B -ntp clean verify` según PLAN; revisar pruebas descubiertas, fallos/omisiones y JaCoCo. Conservar registro/login, JWT, acceso técnico, disponibilidad, inicio, teléfono/inbox y consulta puntual. Revisar Javadoc, artefacto y diff; comprobar disponibilidad/autenticación/vinculación de Sonar CLI/IDE antes de analizar, sin hooks ni credenciales nuevas. Anotar limitaciones reales y no presentar feedback remoto previo como evidencia de este código.

## Evidencia y cierre

- **E-01 · Validación enfocada:** 2026-11-01, sobre el código de CU-07 sin commit en la rama de #12. Las suites `ListOwnReservationsTests` (15), `CentralAppointmentGatewayTests` (24) y `OwnReservationsIntegrationTests` (16) completaron 55 pruebas, sin fallos/errores/omisiones, dentro de la corrida aislada vigente. Incluyen MySQL 8.4.8 real, migración V4 poblada, orden/paginación, HTTP/JWT, límites de red y carreras controladas. La regresión y su repetición enfocada se cierran en E-03.
- **E-02 · Calidad local:** SonarQube for IDE, IntelliJ y VS Code, analizaron los 33 Java modificados/nuevos (32 iniciales y el simulador JDBC reanalizado) y devolvieron cero hallazgos. SonarQube CLI 1.10.0 informó conexión activa y revisión de secretos correcta. No se repitió el análisis Vortex previamente no disponible; estos controles no sustituyen el análisis remoto ni su cobertura.

- **E-03 · Regresión y cobertura:** corrida aislada de `./mvnw -B -ntp clean verify`, 553 pruebas; una espera agotada en la prueba previa de rollback motivó precisar el simulador JDBC. Tras ese ajuste, `./mvnw -B -ntp -Dtest=ReservationPhoneIntegrationTests,ReservationIntegrationTests verify` pasó las 45 pruebas de las dos suites afectadas, sin fallos/errores/omisiones, BUILD SUCCESS a las 17:58:43 -03:00 del 2026-11-01. Los informes vigentes reúnen 553 pruebas correctas; no se presenta la primera ejecución como exitosa. Fuentes productivas sin cambios entre ambas corridas y copia verificada mediante hashes, fuera del target que recompila el IDE. Ambos comandos usaron límites transitorios `JAVA_TOOL_OPTIONS=-Xmx768m` y `MAVEN_OPTS=-Xmx256m`, sin cambios al POM. JaCoCo XML/HTML generado sin desalineación de clases: 2482/2643 líneas (93,91%) y 1063/1324 ramas (80,29%). La estimación local sobre líneas añadidas/modificadas es 225/230 (97,83%), no una métrica de SonarCloud. Javadoc de las 25 fuentes productivas cambiadas: código 0, sin avisos; revisión de artefacto y diff correctas.
- **E-04 · Artefacto ejecutable:** JAR final con MySQL 8.4.8 temporal en tmpfs y proveedores HTTP ficticios: 13 comprobaciones correctas. Incluyen registro/login reales, protección del GET, página/total/pendiente, captura de nombre, reinicio de la aplicación y respaldo con nombre durable al fallar la consulta central. Misma base entre ambos arranques, sin llamadas a servicios reales de cátedra ni cambios a Compose/servicios personales. Contenedor propio y clave efímera retirados. El JAR incluye V5 y excluye recursos exclusivos de test, .env y claves.

Actions y SonarCloud aportarán feedback después de la publicación del usuario; no son tareas de implementación ni acreditan aquí una ejecución local.

| CA | Estado | Tareas asociadas / evidencia |
| --- | --- | --- |
| CA-01 | Verificado localmente | E-01/E-03: JWT, dos propietarios y 401/403; sin datos ni conteos ajenos. |
| CA-02 | Verificado localmente | E-01/E-04: correlación única, huérfanos excluidos y pendiente fuera del total. |
| CA-03 | Verificado localmente | E-01/E-03: estados propios/centrales, sin confirmación ni solicitud inventadas. |
| CA-04 | Verificado localmente | E-01/E-04: V4 poblada, nombres opcionales conservados y reinicio con respaldo. |
| CA-05 | Verificado localmente | E-01/E-04: respaldo vacío/no vacío con aviso y fallo SQL diferenciado como 503. |
| CA-06 | Verificado localmente | E-01: historial propio de fechas pasadas/futuras y estados distintos, sin filtros. |
| CA-07 | Verificado localmente | E-01/E-04: orden, desempate, 20/100, entradas inválidas y página vacía con pendiente. |
| CA-08 | Verificado localmente | E-01: metadata/páginas incompletas, contradicciones y presupuesto compartido; sin cierres por ausencia. |
| CA-09 | Verificado localmente | E-01/E-03/E-04: instantánea SQL, cancelación durante REST, consultas repetidas y offsets/rollback. |

**Regresión:** E-03 documenta la corrida completa y la repetición de las dos suites afectadas por el ajuste del simulador. Los 553 casos están comprobados con el código productivo final; E-04 verifica su JAR y restauración. Actions, cobertura importada y Quality Gate de esta revisión siguen pendientes de la publicación del usuario.

**Desviaciones del PLAN:** sin cambios funcionales, de contrato o arquitectura. La prueba previa de V4 fija su versión objetivo para conservar su alcance al existir V5. Se precisó el simulador de fallo previo al commit con rollback JDBC real; mantiene las comprobaciones de atomicidad y offsets. Los límites JVM de la validación son transitorios y no cambian configuración ni dependencias del servicio.

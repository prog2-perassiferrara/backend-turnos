# TASKS: Completar teléfono y consultar progreso de reserva

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), #10/CU-05 y #11/CU-06.
**Estado:** Implementación y validación local completas.
**Aprobaciones:** SPEC y PLAN aprobados explícitamente el 2026-10-31. TASKS aprobadas explícitamente el 2026-10-31.
**Autorización de implementación:** explícita el 2026-10-31 («apruebo tasks. implementa»).

## Chequeo previo

2026-10-31: nueve RF vinculados a doce CA; todos tienen validación en PLAN y tareas asociadas. Ocho tareas, dependencias sin ciclos y sin alcance nuevo. D-01 a D-04 resueltas: UUID por envío, aceptación durable con Kafka caído, consultas HTTP y recuperación automática por ciclos hasta vencer. Revalidar solo lo afectado si cambian documentos antes de implementar.

## Tareas

- [x] **T-01 · Modelos, puertos y transiciones.**
  - **Objetivo:** expresar envío, correlación y progreso con tipos propios y guardas comprobables.
  - **Alcance:** modelos/puertos del PLAN, enums necesarios y ampliación de ReservationProcess. Normalización contractual; PHONE_QUEUED, WAITING_FOR_RESULT y FAILED; rechazo corregible y preservación de cierres. Actualizar guards para que REST inicial o solicitud duplicada no retrocedan un teléfono ya aceptado. Contratos y Javadoc español, sin frameworks en dominio/aplicación ni clases por estado.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-02/RF-03/RF-04/RF-05/RF-06/RF-07/RF-08/RF-09; CA-02/CA-03/CA-05/CA-06/CA-07/CA-08. PLAN: modelos y estados.
  - **Validación:** pruebas unitarias del agregado/modelos con Clock y ejemplos conocidos: 7/15 dígitos, límites inválidos, caracteres/separadores permitidos, instante anterior/igual/posterior al vencimiento. Probar confirmación/cancelación y solicitud/REST tardíos sin reapertura; no calcular el esperado con el algoritmo productivo. Extender ReservationProcessTests y el foco de teléfono según se incorporen sus colaboradores.

- [x] **T-02 · Aceptación durable, historial e invariantes SQL.**
  - **Objetivo:** aceptar el teléfono únicamente después de conservarlo, con una sola respuesta activa e idempotencia histórica.
  - **Alcance:** V4 aditiva, PhoneSubmissionEntity/JpaPhoneSubmissionRepository, mapper/escritor/adaptador existentes y SubmitReservationPhoneUseCaseImpl. Persistir proceso/envío juntos, evento/vencimiento inmutables, versiones y contador/próxima ejecución. UUID como PK y columna generada única por proceso activo; sin otra tabla de claves. Verificar propietario antes de buscar/revelar una clave ajena; reconocer replay normalizado antes de validar nuevamente un plazo pasado. Conservar V2/V3, cuentas, procesos e inbox.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01/RF-03/RF-04/RF-08/RF-09; CA-01/CA-03/CA-04/CA-05/CA-11. PLAN: aceptación, V4 y atomicidad.
  - **Validación:** SubmitReservationPhoneTests y primeros casos de ReservationPhoneIntegrationTests con MySQL temporal. Migrar V3 poblada; verificar rollback, commit con respuesta perdida y relectura por UUID mediante CommitFaultDataSource. Coordinar mismos UUID/contenidos equivalentes, UUID con otro contenido y dos UUID concurrentes del mismo proceso. Comprobar un único envío activo, históricos repetibles y rechazo seguro si la persistencia no es verificable. Kafka no participa en la aceptación.

- [x] **T-03 · Productor Kafka y configuración propia.**
  - **Objetivo:** publicar el evento v1 correcto con el destino asignado y esperas acotadas.
  - **Alcance:** KafkaPhoneSubmissionAdapter/PhoneSubmissionEvent; ampliar el snapshot del canal existente con destino telefónico, bajo su monitor. KafkaTemplate<String,String>/DefaultKafkaProducerFactory reutilizados; configuración asignada sin otro JWT por envío. Valores del PLAN: acks=all, retries=0, idempotencia funcional por eventId, serialización sin cabeceras de clases, timeouts y cierre acotados. Dos propiedades opcionales nuevas, validación y wiring inicial; no nuevas librerías ni topics productivos. Evitar payload/teléfono y volcado de configuración en logs, incluido el listener predeterminado.
  - **Dependencias:** T-01/T-02.
  - **Referencias:** RF-02/RF-04/RF-08; CA-02/CA-04/CA-11. PLAN: evento, productor y parámetros.
  - **Validación:** ReservationPhoneIntegrationTests con broker descartable y ambos topics de prueba. Observar key, v1, campos obligatorios y contenido idéntico desde la fila confirmada, también después de reiniciar. Comprobar ACK, fallo/timeout, destino no preparado y cambio de destino sin recursos abiertos. Verificar defaults 5 s de publicación y 30 s de verificación, valores inválidos, máximo de bloqueo/envío previsto y logs sin datos privados; un ACK no confirma la reserva.

- [x] **T-04 · Publicación pendiente y recuperación automática.**
  - **Objetivo:** recuperar lo aceptado sin depender de KMP ni publicar fuera del plazo.
  - **Alcance:** PublishPendingPhonesUseCaseImpl y PhoneSubmissionDispatcher con scheduling gestionado por Spring. Lotes de hasta diez; registrar intento/próxima fecha antes de Kafka; tres intentos por ciclo, separación retryDelay y cooldown preparationCooldown. Persistir ciclo/fecha para reinicios, conservar ID/contenido y actualizar solo el UUID activo. Al alcanzar expiresAt o un cierre, detener nuevas publicaciones y conservar la incertidumbre pertinente. Cerrar tarea antes del productor; no operaciones remotas dentro de transacciones o del monitor del canal.
  - **Dependencias:** T-02/T-03.
  - **Referencias:** RF-04/RF-06/RF-08/RF-09; CA-04/CA-07/CA-08/CA-11. PLAN: D-04, límites y restauración.
  - **Validación:** PublishPendingPhonesTests con Clock/puertos controlados y ReservationPhoneIntegrationTests con recursos reales temporales. Probar tres fallos, cooldown y siguiente ciclo; reiniciar entre registrar intento, publicar y guardar ACK. Forzar fallo del commit posterior al ACK: una republicación debe conservar ID/contenido, sin asumir una sola entrega física. Recuperar Kafka antes del plazo y verificar que reanuda automáticamente; una fecha alcanzada o cierre evita el siguiente send. Usar barreras/esperas acotadas, sin sleeps arbitrarios.

- [x] **T-05 · Resultados centrales y corrección sin retrocesos.**
  - **Objetivo:** integrar rechazo corregible, resultado final y publicación concurrente de forma durable.
  - **Alcance:** ampliar parser/agregado e integración en TransactionalReservationStore.accept. Validar correlación/grupo/v1; recibo, proceso y cierre del envío activo se confirman juntos. Rechazo vuelve a WAITING_FOR_PHONE, conserva solicitud/plazo y permite UUID nuevo. Confirmación, expiración, invalidez y cancelación siguen sus reglas, sin reinterpretar invalidez como cancelación ni modificar el flujo de abandono. Mantener fingerprints y tratamiento compatible del inbox previo.
  - **Dependencias:** T-01/T-02/T-04.
  - **Referencias:** RF-02/RF-05/RF-06/RF-08/RF-09; CA-02/CA-06/CA-07/CA-08/CA-12. PLAN: receptor, guardas y convivencia.
  - **Validación:** ReservationActionParserTests, ReservationProcessTests, StartReservationTests y ReservationPhoneIntegrationTests. Retener un ACK hasta recibir rechazo y aceptar la corrección: liberar ese ACK no debe alterar el envío nuevo. Confirmar antes del ACK y fallar la respuesta REST inicial después de encolar; no debe retroceder el proceso. Duplicados/ajenos/contradictorios y campos compatibles; rollback real sin avance del offset, redelivery de datos V3 sin falsos conflictos y una única reserva propia. No reprocesar automáticamente el histórico diferido de CU-09.

- [x] **T-06 · Consulta propia y reconciliación puntual.**
  - **Objetivo:** comunicar progreso e incertidumbre sin inventar resultados ni generar efectos centrales.
  - **Alcance:** GetReservationProcessUseCaseImpl, CentralAppointmentGateway y adaptador/DTO HTTP propios. Lectura local normal/histórica; lectura central por incertidumbre, plazo o espera sin novedad según PLAN. Un GET sin retry, paciente derivado y fecha exacta, página 0/size 100; coincidencia de proceso/paciente/selección. Respuesta vacía o incompleta sigue inconclusa. Converger estados verificables con la versión actual, preservando eventos concurrentes, y producir campos/acciones/avisos del contrato. Sin listado general ni nuevas políticas de acceso técnico.
  - **Dependencias:** T-02/T-05.
  - **Referencias:** RF-01/RF-05/RF-06/RF-07/RF-08/RF-09; CA-01/CA-07/CA-08/CA-09/CA-10. PLAN: GET, REST y errores.
  - **Validación:** GetReservationProcessTests y ReservationGatewayTests con HTTP ficticio, más integración SQL. Comprobar local sin llamada central innecesaria, espera de 30 s, expiración/horas y un único GET acotado. Variar respuesta ajena/vacía/contradictoria, 401/timeout y versión avanzada por Kafka durante HTTP; no inventar cierre ni solicitud. La caída central devuelve estado local con aviso; consultar no crea holds, publica teléfono, confirma inicialmente, cancela ni renueva el plazo.

- [x] **T-07 · API protegida y recorrido completo.**
  - **Objetivo:** exponer envío y consulta con los contratos aprobados y resultados distinguibles.
  - **Alcance:** ampliar ReservationController/DTO/mapper/advice y completar wiring por constructor. POST /api/reservation-processes/{processId}/phone y GET /api/reservation-processes/{processId}; 202 nuevo/200 replay, error seguro 400/404/409/503, filtros 401/403 existentes y no-store. UUID de ruta inválido no produce 500. Datos y roles proceden del usuario autenticado; ampliar la respuesta existente sin renombrar sus estados/campos previos. Tres casos de uso, sin fachada adicional, SSE o cambios de KMP.
  - **Dependencias:** T-04/T-05/T-06.
  - **Referencias:** RF-01 a RF-09; CA-01 a CA-12. PLAN: contratos HTTP y configuración.
  - **Validación:** completar ReservationPhoneIntegrationTests con registro/login reales, MySQL/Kafka temporales y cátedra ficticia. Enviar con Kafka caído y consultar pendiente, recuperar y observar resultado; corrección, históricos y replays tras cierre. Credenciales/propietarios distintos y campos de suplantación no filtran datos ni generan eventos ajenos. Comprobar estados, canSubmitPhone, verificación/avisos, códigos y no-store. Separar efectos del publicador automático de los que pudiera generar indebidamente una consulta. Constructor DI en pruebas Spring según la convención existente.

- [x] **T-08 · Regresión, ensayo aislado y cierre.**
  - **Objetivo:** validar los doce CA y mantener ejecución/documentación coherentes.
  - **Alcance:** README, Javadoc, configuración/Compose del PLAN, suite completa, calidad local y artefacto. Ensayo JAR con MySQL/Kafka descartables y HTTP ficticio: guardar con broker caído, reiniciar, recuperar y consultar resultado, incluyendo publicación con ACK/commit incierto. Registrar CA/evidencia, regresión y desviaciones en este archivo. Sin tocar servicios personales, secretos reales ni otros repositorios.
  - **Dependencias:** T-07.
  - **Referencias:** RF-01 a RF-09; CA-01 a CA-12. PLAN: estrategia de validación y límites.
  - **Validación:** ./mvnw -B -ntp clean verify; revisar pruebas descubiertas/fallos/omisiones y cobertura, Javadoc, artefacto sin .env/claves/fixtures y git diff --check. Conservar registro/login, JWT/CORS, acceso técnico, disponibilidad e inicio de reserva. Comprobar Sonar CLI/IDE, autenticación/vinculación y disponibilidad antes de declarar análisis; no repetir Vortex 403 conocido. Verificar modelo Compose con entorno ficticio, cerrar solo recursos efímeros propios y distinguir cada evidencia local/remota.

Cada incremento incorpora sus verificaciones de comportamiento. Mantener el nuevo recorrido integrado en ReservationPhoneIntegrationTests y reutilizar fixtures existentes, sin inflar la suite de inicio ni generar una jerarquía nueva. Actions/Sonar remotos son feedback posterior; esperar aprobación/publicación/merge no es una tarea de implementación. Git y publicación corresponden al usuario.

## Evidencia

- **E-01 · Preparación documental (2026-10-31):** SPEC y PLAN aprobados, D-01 a D-04 resueltas y TASKS derivadas con nueve RF/doce CA cubiertos. Revisadas dependencias, referencias, rutas existentes y límites. No se implementó, compiló, ejecutó pruebas, analizó código nuevo ni levantó servicios durante este desglose.

- **E-02 · Primeras verificaciones locales:** foco de modelos/parser/inicio, 59 pruebas sin fallos ni omisiones. La prueba nueva de guardas reprodujo el retroceso por un fallo REST tardío; se corrigió para preservar el teléfono aceptado. Parser previo ejecutado aisladamente para obtener la huella histórica de rechazo y comprobar compatibilidad.
- **E-03 · Integración enfocada:** `-Dtest=ReservationPhoneIntegrationTests,CentralAppointmentGatewayTests,ReservationGatewayTests test`, 56 pruebas (17 SQL/Kafka, nueve del GET central y treinta del gateway/configuración), sin fallos ni omisiones. MySQL/Kafka temporales y HTTP ficticio; no servicios personales.
- **E-04 · Calidad local preliminar:** Sonar CLI 1.10.0 conectado a la organización existente; detector de secretos sin hallazgos. Sonar IDE analizó los 48 archivos Java modificados/nuevos, dos observaciones corregidas (regex equivalente y separación de la recuperación de commit), repetición sin hallazgos. No es un análisis remoto ni una ejecución Vortex.

- **E-05 · Regresión final (2026-10-31):** `./mvnw -B -ntp clean verify`, 507 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS a las 20:19:26 -03:00. Ejecutado sobre copia temporal exacta de pom/wrapper/src, aislada de recompilaciones concurrentes; hashes contrastados con el árbol local. La ejecución anterior de 505 pruebas pasó, pero se descartó su cobertura por cambios de clases durante el test. La corrida final no presenta desalineaciones. JaCoCo XML/HTML: 93,14 % de líneas (2255/2421), 78,03 % de ramas (909/1165). No es el cálculo de código nuevo de Sonar.
- **E-06 · Foco final:** 86 pruebas sin fallos/errores/omisiones en ReservationIntegrationTests, ReservationPhoneIntegrationTests y las suites de modelos/parser/envío/consulta/productor. Incluye ambos topics, destino ausente sin autocreación, cambio de destino, cancelación durante el GET, rechazo/ACK tardío, rollback del resultado antes del offset y deduplicación de un rechazo histórico diferido. V4 se aplicó sobre V3 poblada conservando cuenta/proceso/inbox. La recuperación de commit perdido y los ciclos persistidos se comprobaron contra SQL real.
- **E-07 · Calidad y artefacto:** Sonar IDE, 53 archivos Java modificados/nuevos, sin hallazgos; Sonar CLI, detector de secretos sin hallazgos en código, pruebas, migración y configuración modificados. Conexión existente y projectKey comprobados; no se reintentó Vortex. Javadoc de 39 fuentes productivas con `-Xdoclint:all,-missing -Werror`, sin avisos. Modelo Compose validado con entorno ficticio, sin levantarlo. JAR sin .env, PEM, fixtures ni V1 de pruebas; contiene V2/V3/V4. `git diff --check` correcto.
- **E-08 · Evidencia remota de base:** consultados los checks y logs de Actions de cac8188, ambos checks correctos, y análisis Sonar correspondiente a esa misma revisión, Gate OK. No contienen esta funcionalidad sin publicar; no se les atribuyen las pruebas/cobertura locales. Actions/Sonar del nuevo PR quedan como feedback tras publicación del usuario.
- **E-09 · Ensayo del JAR:** artefacto final ejecutado con MySQL 8.4.8 y Kafka 3.8.0 descartables y proveedores HTTP ficticios. Registro/login, hold y solicitud; broker pausado, teléfono aceptado durablemente, reinicio del JAR con la misma base, broker recuperado y publicación automática con UUID/contenido/correlación conservados. ACK dejó espera, evento central ficticio confirmó y replay normalizado histórico devolvió 200. Exactamente un hold y una confirmación inicial REST. Procesos/recursos del ensayo cerrados; sin tocar Compose personal, Catálogo real ni cátedra. El commit posterior al ACK perdido se verificó en E-06, no se simuló en el JAR.

- **E-10 · Correcciones de calidad local (2026-10-31):** revisión completa de los 194 Java con los dos puentes de IDE. Corregidas 25 observaciones aplicables: capturas equivalentes unificadas, declaraciones UUID separadas, nombres de variables y excepciones comprobadas concretas en helpers HTTP. El caso CORS con userinfo conserva su validación usando un dato efímero generado por MethodSource; no contiene una contraseña literal ni pierde la comprobación de rechazo. Reanálisis de ambos puentes sin hallazgos nuevos; Sonar CLI sin secretos en los tres archivos corregidos. `./mvnw -B -ntp -Dtest=ReservationPhoneIntegrationTests,KafkaPhoneSubmissionTests,JwtSecurityPropertiesTests verify` en copia aislada: 47 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS a las 20:46:13 -03:00; hashes del código coincidentes. Sin cambios funcionales, de contratos ni supresiones. La regresión completa y el ensayo del JAR de E-05/E-09 corresponden al estado anterior a esta limpieza; no se presentan como nuevas ejecuciones.

- **E-11 · Feedback del PR #27 y corrección local (2026-11-01):** análisis remoto de d9545ec: Actions correcto con 507 pruebas, Gate fallido solo por fiabilidad B; cobertura nueva 87,1 %, duplicación 0 %, seguridad A, sin vulnerabilidades/hotspots. Corregidos diez hallazgos: dos condiciones ya descartadas en recordFailure, cinco lambdas de excepción con preparación fuera de la llamada, un import sin uso y dos aserciones de colección. La prueba de ausencia del topic exige lista no vacía y presencia de los topics esperados. Sin cambios funcionales, de contrato ni supresiones.
  Validación local: `./mvnw -B -ntp -Dtest=ReservationProcessTests,SubmitReservationPhoneTests,CentralAppointmentGatewayTests,GetReservationProcessTests,KafkaPhoneSubmissionTests,PublishPendingPhonesTests,ReservationPhoneIntegrationTests verify`, 70 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS a las 11:07:35 -03:00. Copia aislada con hashes iguales al código local; MySQL/Kafka descartables y proveedores ficticios. Ambos puentes IDE revisaron los 194 Java sin hallazgos nuevos; CLI autenticada y revisión de secretos correcta; diff sin errores. El nuevo resultado de Actions/Gate requiere publicación del usuario; no se modificaron estados remotos ni Git.

## Cierre

| CA | Estado | Tarea y evidencia prevista |
| --- | --- | --- |
| CA-01 | Verificado localmente | E-05/E-06: JWT/propiedad, 404 indistinto y entrada inválida sin escrituras ni eventos. |
| CA-02 | Verificado localmente | E-05/E-06: solicitud real necesaria, evento v1/key/correlación y huella V3 conservada. |
| CA-03 | Verificado localmente | E-02/E-05: separadores contractuales, ceros iniciales, ASCII y límites 7/15; inválidos sin publicación. |
| CA-04 | Verificado localmente | E-05/E-09: aceptación SQL con Kafka caído, ACK separado del resultado y confirmación central. |
| CA-05 | Verificado localmente | E-05/E-06: UUID/contenido normalizado, concurrencia SQL, exclusión activa y replay histórico. |
| CA-06 | Verificado localmente | E-05/E-06: rechazo permite corregir; ACK viejo no modifica el nuevo envío. |
| CA-07 | Verificado localmente | E-05/E-06: límites temporales con reloj, detención al vencer sin cierre ficticio y terminales sin reapertura. |
| CA-08 | Verificado localmente | E-05/E-06: confirmación antes del ACK, REST tardío, invalidez/cancelación y carrera GET/Kafka. |
| CA-09 | Verificado localmente | E-05/E-09: GET propio/histórico, no-store y consultas/replays sin nuevos efectos centrales. |
| CA-10 | Verificado localmente | E-05/E-06: filtros propios, lectura central acotada, evidencia vacía/ajena/fallida con avisos. |
| CA-11 | Verificado localmente | E-05/E-06/E-09: broker caído/recuperado, ciclos/cooldown durables, reinicio y mismo evento tras ACK incierto. |
| CA-12 | Verificado localmente | E-05/E-06: recibo/cambio/cierre atómicos antes del offset; rollback, duplicados y redelivery V3 compatibles. |

**Regresión:** E-11 valida las correcciones del PR; E-10 valida las correcciones posteriores de calidad; E-05 cubre registro/login, JWT/CORS, acceso técnico, disponibilidad e inicio de reserva, además de la funcionalidad nueva. E-06 valida los últimos ajustes de metadata y las carreras; E-09 verifica el artefacto ejecutable. No se acredita flujo contra servicios reales de cátedra. E-11 distingue el análisis remoto de d9545ec de las correcciones locales posteriores, cuyo Gate sigue pendiente de publicación.

**Desviaciones del PLAN:** sin cambios de alcance o contrato. Se concretó una secuencia SQL interna para ordenar históricos con timestamps iguales y se repartió el primer presupuesto entre metadata y bloqueo de envío, evitando autocreación. Tres suites Spring existentes cierran su contexto al terminar para detener el scheduler antes de retirar sus bases temporales. Se aisló la corrida final para obtener cobertura consistente; no se alteró el build del proyecto. Se conserva una sola tabla adicional y los tres casos de uso previstos.

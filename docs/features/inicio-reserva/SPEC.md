# SPEC: Iniciar reserva con hold y confirmación inicial

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-10-29. Issue [#9](https://github.com/prog2-perassiferrara/backend-turnos/issues/9), [CU-04 aprobado](../../use-cases/CU-04-iniciar-reserva.md).
**Aprobación:** explícita del usuario el 2026-10-29. No autoriza implementar.

**Ajuste autorizado, 2026-10-30:** el registro exige nombres no blancos de 2 a 50 caracteres, según el ajuste confirmado de [registro/autenticación](../registro-autenticacion/SPEC.md). Evita crear perfiles incompatibles con reservas; RF-01/CA-01 conservan la comprobación previa al hold para cuentas existentes. No cambia la autenticación ni el contrato central.

**Ajuste autorizado, 2026-10-30:** la respuesta docente sobre creación incierta sin IDs permite abandonar ese intento y consultar ocupaciones. `HELD` requiere esperar hasta su `expiresAt` y volver a consultar, sin atribuir propiedad; `CONFIRMED` impide crear; ausencia de ocupación habilita un nuevo intento limitado, sujeto a 409. No se libera por diez minutos locales. Actualización e implementación solicitadas explícitamente por el usuario; afecta RF-03/RF-04/RF-07/RF-08 y CA-03/CA-06/CA-07/CA-09. Fuente: [aclaración de cátedra](../../../../dudas_Slack.md#duda-18-resuelta).

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo según el enunciado y los contratos del backend.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto y los contratos pertinentes. Inspecciona el
  repositorio para comprobar el comportamiento actual.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Considera errores, concurrencia, integración y recuperación cuando apliquen,
  sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
- Para cambios de significado posteriores a la aprobación, registra una nota
  breve de cambio, motivo e impacto según AGENTS.md, sin historial separado.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Permitir que un usuario autenticado, desde KMP, inicie una reserva del horario elegido con una sola acción. Turnos valida la agenda vigente, crea el hold y solicita inmediatamente su confirmación inicial por REST. El resultado es un proceso propio pendiente de información adicional, no una reserva definitivamente confirmada.

Fuentes: [enunciado, 4.2 y 7 a 9](../../../../PROJECT_STATEMENT-v1.md), [anexo, 2, 9 a 13, 15 y 18](../../../../INTEGRATION_REFERENCE-v2.md) y [decisiones de los CU](../../use-cases/README.md). Coordina con [CU-05](../../use-cases/CU-05-completar-informacion.md), [CU-06](../../use-cases/CU-06-consultar-proceso.md), [CU-09](../../use-cases/CU-09-recuperar-procesos.md) y [CU-10](../../use-cases/CU-10-abandonar-proceso.md), sin sustituirlos.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Turnos ya registra/autentica usuarios, protege sus APIs y obtiene acceso técnico central mediante Catálogo. CU-03 está implementado, validado e integrado mediante el [PR #25](https://github.com/prog2-perassiferrara/backend-turnos/pull/25). Consultar disponibilidad no bloquea ni garantiza un horario.

Todavía no existen procesos propios de reserva ni creación/confirmación de holds o recepción de eventos de turnos. Se conservan los contratos aprobados de [registro/autenticación](../registro-autenticacion/SPEC.md), [seguridad](../seguridad-jwt/SPEC.md), [integración técnica](../integracion-tecnica/SPEC.md) y [disponibilidad](../consulta-disponibilidad/SPEC.md).

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Exigir usuario autenticado/autorizado y selección válida. Obtener propietario, paciente y nombres desde su cuenta; validar su compatibilidad antes del hold, sin aceptar sustituciones desde KMP.
- **RF-02:** Validar profesional y horario completo habilitados mediante una lectura vigente de Catálogo. Excluir inicios pasados o actuales según CU-03, aunque antes figuraran disponibles.
- **RF-03:** Garantizar un único proceso pendiente por usuario, incluso con concurrencia, reinicio o cierre incierto, salvo el cierre local de un intento sin IDs permitido por la aclaración. Si existe, informar su referencia sin otro hold ni sustitución del historial.
- **RF-04:** Conservar operación/propietario antes del hold y sus `holdId`, `reservationProcessId` y `expiresAt` antes de continuar. Una respuesta perdida/incompleta no demuestra ausencia del hold.
- **RF-05:** Confirmar inicialmente el hold por REST de inmediato, con el mismo paciente y nombres de la cuenta, sin otra acción. No confundir aceptación inicial con reserva definitiva.
- **RF-06:** Conservar la solicitud de teléfono y su `eventId`; deduplicar y reconciliar los eventos de inicio con REST. Eventos anticipados/repetidos/tardíos no retroceden progreso ni reabren resultados finales.
- **RF-07:** Distinguir rechazo, vencimiento verificado e incertidumbre. No repetir efectos a ciegas ni liberar el límite por reloj de KMP, abandono sin resolver o rechazo que no acredite liberación. La creación incierta sin IDs se trata con abandono del intento y consulta de ocupaciones según la aclaración docente.
- **RF-08:** Conservar propiedad, progreso e identificadores tras interrupciones/reinicios para CU-09; impedir exposición de procesos/credenciales ajenos. Salir de KMP no cancela ni renueva el hold.
- **RF-09:** Bloquear nuevos inicios antes del hold si se conoce indisponibilidad de Kafka, según D-01. Si cae durante el proceso, conservar progreso y necesidad de recuperación sin extender el plazo.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Envío/corrección del teléfono y tratamiento completo de sus resultados, propios de CU-05; esta funcionalidad conserva la solicitud necesaria para continuar.
- La consulta general de progreso, listado de reservas, cancelación y abandono explícito, que mantienen sus CU respectivos.
- La recuperación automática completa de operaciones interrumpidas, propia de CU-09. CU-04 debe conservar la información y distinguir la incertidumbre sin bloquear esa evolución ni prometer mecanismos ausentes del contrato.
- Cambiar registro/login fuera del ajuste de nombres autorizado, sincronización de Catálogo o contratos centrales; implementar pantallas de KMP. OpenAPI se mantiene para la consolidación de las APIs acordada previamente.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. KMP envía profesional, fecha y hora con el JWT de usuario.
2. Turnos verifica autorización, selección, perfil, proceso pendiente y dependencias necesarias. Si existe un proceso propio pendiente, informa su referencia sin otro hold.
3. Obtiene la agenda vigente y valida el horario, sin confiar en la disponibilidad anterior.
4. Conserva inicio/propietario, respetando el límite concurrente, y solicita el hold.
5. Conserva IDs/vencimiento recibidos y solicita inmediatamente la confirmación REST con los datos de la cuenta.
6. Conserva aceptación inicial y eventos correlacionados, cualquiera sea su orden de llegada, sin perder progreso.
7. Informa referencia estable, progreso e incertidumbre propios y vencimiento conocido. La aceptación inicial no se presenta como reserva confirmada.

Los rechazos conocidos se comunican según su significado. Ante respuesta perdida, interrupción o resultado no interpretable, se conserva la incertidumbre conforme a RF-07/RF-08; la creación sin IDs aplica abandono del intento y ocupaciones según la aclaración; una confirmación incierta conserva el proceso afectado.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Entrada: ID positivo de profesional, fecha ISO y hora local `HH:mm` o `HH:mm:ss`. Aplicar `America/Argentina/Buenos_Aires` por defecto y la configuración de atención existente; admitir fechas desde hoy y excluir inicios anteriores o iguales al momento de validar. El inicio debe corresponder a un turno completo de una agenda habilitada.
- La disponibilidad previa es orientativa: no es una autorización para reservar ni reemplaza la agenda vigente. La asignación del hold por cátedra resuelve el conflicto con otros usuarios que elijan el mismo horario.
- `externalPatientId` identifica de forma estable al usuario final y se mantiene igual entre creación y confirmación. No es el `groupId` técnico, el login ni un ID elegido libremente por el cliente; su representación se define en PLAN.
- `patientFirstName` y `patientLastName` proceden de la cuenta y deben satisfacer el contrato central: no blancos y de 2 a 100 caracteres. El registro exige de 2 a 50 caracteres, manteniendo el máximo compatible con JHipster y satisfaciendo el mínimo central. Un perfil incompatible impide solicitar el hold y obtiene un error distinguible, sin completar o inventar nombres.
- El proceso conserva identidad propia, propietario, selección, progreso e incertidumbres, además de los identificadores/vencimiento centrales cuando se reciben. Los tokens técnicos nunca forman parte de la respuesta a KMP.
- Crear el hold y confirmar inicialmente son dos efectos centrales de una sola acción. `201 HELD` informa el bloqueo temporal; `202 WAITING_FOR_PHONE` acepta la confirmación inicial y cátedra crea la reserva `PHONE_PENDING`. Ninguno acredita una reserva `CONFIRMED`.
- `AdditionalInformationRequested` corresponde a un proceso conocido; se conserva su `eventId` para el futuro `requestEventId`. La message key es `reservationProcessId`; entrega al menos una vez, con efectos persistidos antes de confirmar offsets. Una solicitud ajena/incoherente no se asocia por el usuario actualmente conectado ni modifica otro proceso.
- El límite por usuario abarca tanto un proceso en curso como uno de resultado incierto. Un fallo previo a cualquier efecto central puede cerrarse como rechazo conocido; un hold conocido mantiene el límite hasta verificar su cierre. La creación sin IDs es la excepción docente: su intento puede archivarse tras consultar, sin afirmar liberación central. Una confirmación inicial aceptada no es un resultado final.
- Los resultados finales verificados se conservan y no se reabren por eventos tardíos. Una intención de abandono pendiente mantiene el límite aunque se descubra una reserva confirmada, conforme a CU-10. No se restringen las reservas anteriores ya confirmadas.
- `expiresAt` es un instante UTC autoritativo, no una duración renovable. Al alcanzarlo se impiden nuevos pasos incompatibles, pero un efecto en curso de resultado desconocido se reconcilia antes de afirmar su resultado central. `HOLD_EXPIRED` y `AppointmentProcessExpired` acreditan vencimiento según el anexo; un simple timeout no lo acredita.
- **D-01, confirmada por el usuario el 2026-10-29:** si se sabe que Kafka está indisponible, rechazar nuevos inicios antes del hold. No se exige atomicidad entre esa comprobación y REST: una caída posterior conserva el proceso para recuperación. Tener configuración `PROVISIONED` no demuestra por sí solo conectividad operativa; la ausencia de mensajes tampoco acredita por sí sola indisponibilidad del broker.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Hold y confirmación inicial aceptados | Proceso propio pendiente con IDs/vencimiento; no reserva definitiva. |
| Confirmación inicial aceptada, solicitud adicional aún no recibida | Conservar espera y plazo, sin fabricar una solicitud ni su correlación, ni reiniciar la reserva. |
| Acceso, entrada o perfil inválidos | Rechazo sin hold ni suplantación del paciente. |
| Agenda no vigente, referencia deshabilitada/inexistente o turno inválido/iniciado | Sin hold; distinguir referencia/entrada inválida de indisponibilidad. |
| Acceso técnico o Kafka no disponibles antes del inicio | Indisponibilidad sin hold; no atribuir el fallo técnico al JWT del usuario. |
| Proceso pendiente o abandono sin resolver | Referencia propia existente, sin otro hold ni sustitución del historial. |
| Usuarios compiten por el horario; conflicto central | Respetar el resultado central sin mezclar propietarios ni repetir otra creación. |
| Creación con respuesta perdida/incompleta, sin IDs | Abandonar el intento no identificable y consultar ocupaciones: esperar/reconsultar si HELD, indisponible si CONFIRMED, nuevo intento limitado si libre; sin atribuir propiedad ni garantizar ausencia histórica. |
| Confirmación con respuesta perdida o ya realizada | Reconciliar evidencia sin duplicar efectos ni afirmar reserva definitiva. |
| Confirmación rechazada con hold conocido | Conservar rechazo y hold; no asumir cancelación ni cierre verificado. |
| Evento anticipado, repetido o tardío | Aplicación única y sin retroceso del progreso o reapertura de estados finales. |
| Caída/reinicio durante el proceso | Conservar propiedad, progreso y límite para CU-09. |
| Cerrar KMP o volver atrás | Sin cancelación implícita ni renovación de `expiresAt`. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Respetar los contratos REST/Kafka del anexo y las identidades aprobadas: usuario en KMP-Turnos, servicio ante Catálogo y acceso técnico ante cátedra. No acceder a datos internos de Catálogo ni a `catedra:holds:*`. Los holds se administran por REST.

**Límite contractual ya identificado en CU-04/CU-09:** el anexo no documenta consulta de holds ni clave de idempotencia de creación aportada por el cliente. No existe consulta ni idempotencia central para recuperar los IDs de una creación cuya respuesta se perdió. La aclaración docente permite abandonar ese intento y usar ocupaciones para decidir otro intento limitado. Un resultado vacío habilita intentar, pero no prueba que el POST previo falló ni garantiza disponibilidad futura. El TTL predeterminado es diez minutos desde la creación en el servidor; no habilita un cierre por diez minutos desde un timeout local.

La API propia, vocabulario técnico de estados, coordinación de concurrencia, recepción de eventos y presupuestos/reconciliación se desarrollarán en PLAN. La detección de Kafka indisponible debe tener un criterio verificable, sin presentar una comprobación previa como garantía de disponibilidad futura.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-08:** Acceso, selección o perfil inválidos impiden el hold. Paciente/nombres se obtienen de la cuenta autenticada; no se admiten sustituciones ni exposición de procesos ajenos.
- **CA-02 · RF-02:** El hold exige profesional habilitado y turno completo aún no iniciado en agenda vigente, aunque una consulta anterior hubiera mostrado otra disponibilidad.
- **CA-03 · RF-03, RF-08:** Concurrencia, repetición, reinicio o abandono sin resolver no permiten otro proceso pendiente. Una creación incierta sin IDs puede cerrarse como intento abandonado según el criterio docente; un nuevo intento exige ocupaciones recientes y presupuesto acotado, sin afirmar cancelación central.
- **CA-04 · RF-04, RF-05:** Conservar operación antes del hold, sus IDs/vencimiento antes de confirmar y utilizar paciente/nombres de la cuenta sin otra acción. La aceptación se informa como progreso pendiente.
- **CA-05 · RF-06:** La solicitud de teléfono anticipada/repetida conserva `eventId` y progreso una vez. REST/eventos tardíos no retroceden ni reabren resultados finales; los ajenos no modifican otro proceso.
- **CA-06 · RF-04, RF-07:** Diferenciar rechazo conocido de respuesta perdida/incompatible en ambos POST. Ante creación incierta sin IDs, consultar ocupaciones antes de otra creación limitada y conservar el intento abandonado. HELD/CONFIRMED impiden el nuevo POST; libre habilita intentar, sujeto a 409. La confirmación incierta con hold conocido conserva exclusión.
- **CA-07 · RF-07, RF-08:** Salir/reabrir KMP no renueva el hold. Alcanzar su plazo impide pasos incompatibles, sin resolver efectos inciertos por el reloj; un vencimiento central verificado no admite posteriores confirmaciones.
- **CA-08 · RF-03, RF-07:** La competencia por el horario conserva propietarios independientes y respeta el conflicto central. Rechazar confirmación de un hold conocido no acredita cancelación ni libera el límite sin evidencia.
- **CA-09 · RF-08:** Tras interrupción/reinicio se conservan selección, propietario, IDs conocidos y progreso/incertidumbre para CU-09; no se crea otra reserva por perder la memoria del proceso.
- **CA-10 · RF-09:** Kafka conocido como indisponible impide nuevos holds; su caída posterior conserva progreso/plazo sin afirmar confirmación.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Variar JWT/perfil, entrada y suplantación; incluir nombres de uno/dos caracteres. | Rechazo previo al hold cuando corresponda; datos solo de la cuenta propia. |
| CA-02 | Variar vigencia, flags, día, duración/cierre e inicio respecto del reloj. | Solo agenda vigente y turno completo aún no iniciado. |
| CA-03 | Superponer/repetir solicitudes con proceso pendiente, reinicio o intento sin IDs archivado. | Un único pendiente; repetición sin nuevos POST; otro intento tras el cierre exige ocupaciones recientes. |
| CA-04 | Crear y confirmar inicialmente, observando datos y progreso conservado. | IDs/vencimiento durables, mismo paciente y aceptación pendiente sin otra acción. |
| CA-05 | Retrasar/adelantar/repetir eventos, incluido uno ajeno o tardío tras el cierre. | Espera sin correlación inventada; solicitud real aplicada una vez, sin mezcla ni retroceso. |
| CA-06 | Rechazos y respuestas perdidas en ambos POST; creación incierta con HELD, CONFIRMED, libre, consulta fallida y carrera 409. | Nueva creación solo tras consulta libre y dentro del presupuesto; confirmación incierta conserva exclusión. |
| CA-07 | Salir/reabrir y distinguir reloj alcanzado de vencimiento central verificado. | Sin renovación; impedir pasos incompatibles y conservar incertidumbre/resultados conocidos. |
| CA-08 | Competir por horario y rechazar confirmación de un hold conocido. | Propietarios independientes, conflicto correcto y sin cancelación ficticia. |
| CA-09 | Reiniciar en las fronteras de creación/confirmación. | Progreso recuperable y límite mantenido, sin nueva reserva automática. |
| CA-10 | Kafka caído antes del inicio y durante uno existente. | Sin nuevo hold; progreso/plazo del existente conservados. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

D-01 y la SPEC completa aprobadas explícitamente por el usuario el 2026-10-29. No quedan opciones funcionales nuevas por elegir. El criterio de creación incierta sin IDs quedó resuelto por la aclaración docente del 2026-10-30. Se conserva el alcance pendiente de CU-09 para otras interrupciones/operaciones conocidas, sin prometer recuperación remota completa.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

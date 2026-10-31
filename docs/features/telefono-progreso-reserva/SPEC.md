# SPEC: Completar teléfono y consultar progreso de reserva

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-10-31. Issues [#10/CU-05](https://github.com/prog2-perassiferrara/backend-turnos/issues/10) y [#11/CU-06](https://github.com/prog2-perassiferrara/backend-turnos/issues/11). Aprobación explícita de la SPEC y autorización de PLAN el 2026-10-31; no autoriza implementar.

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

El propietario podrá completar/corregir el teléfono solicitado por cátedra y consultar su reserva desde KMP, también al reabrirla. Distinguirá envío aceptado, recepción por Kafka y confirmación central.

Fuentes: [CU-05](../../use-cases/CU-05-completar-informacion.md), [CU-06](../../use-cases/CU-06-consultar-proceso.md), [enunciado, 4.2 y 7 a 9](../../../../PROJECT_STATEMENT-v1.md), [anexo, 11, 15 y 18](../../../../INTEGRATION_REFERENCE-v2.md) y [aclaración sobre duplicados](../../../../dudas_Slack.md#duda-1-resuelta).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La base local `cac8188` integra [CU-04/PR #26](https://github.com/prog2-perassiferrara/backend-turnos/pull/26): procesos propios, solicitud, IDs y resultados recibidos, con deduplicación durable. La rama de #10 está actualizada a esa base.

Faltan envío del teléfono, rechazo corregible y consulta de progreso. Se conservan [CU-04](../inicio-reserva/SPEC.md), autenticación y seguridad.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Autorizar consulta/envío solo al propietario autenticado, sin filtrar procesos ajenos ni aceptar identidades centrales desde KMP.
- **RF-02:** Exigir solicitud correlacionada, conservando su `eventId` como `requestEventId`, información requerida y vencimiento; no inventarla desde REST o un topic vacío.
- **RF-03:** Validar teléfono, estado y vigencia; informar entradas inválidas sin publicarlas ni impedir su corrección.
- **RF-04:** Identificar cada respuesta de teléfono con un UUID de KMP y conservarla antes de publicar. Repetir UUID/contenido devuelve el envío existente; otro contenido produce conflicto. Distinguir guardado, publicación incierta, ACK y resultado central, según D-01 confirmada.
- **RF-05:** Procesar confirmación, rechazo corregible, expiración, invalidez y cancelación. Confirmar conserva una única reserva propia; rechazar permite un evento nuevo antes del vencimiento.
- **RF-06:** Deduplicar y persistir antes del offset. Publicación y resultado concurrentes no retroceden cierres verificados; eventos incompatibles no alteran otro proceso.
- **RF-07:** Consultar por HTTP progreso, información requerida, plazo conocido, envío, resultado/incertidumbre y acciones disponibles. KMP repetirá la consulta mientras esté pendiente y al reabrir, sin SSE; distinguir estado local de verificación central.
- **RF-08:** Aceptar y conservar una respuesta válida aunque Kafka esté caído, informando pendiente de publicación; reanudar con intentos acotados y plazo vigente. Conservar lo aceptado tras reinicios y republicar con la misma identidad/contenido. Reconciliar resultados inciertos con IDs conocidos.
- **RF-09:** Consultar/reabrir KMP no crea holds, confirma, publica teléfonos ni cancela por sí solo ni renueva `expiresAt`. Conservar identidad, históricos y único pendiente de CU-04.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Crear otro inicio de reserva, listado de reservas propias (CU-07), cancelación (CU-08) y abandono explícito (CU-10).
- Recuperación transversal completa de CU-09. Aquí sí se incluyen la recuperación de los envíos aceptados y la reconciliación puntual necesarias para estos dos CU.
- Modificar perfiles para convertir el teléfono en atributo permanente, autenticación, catálogo, contratos centrales o pantallas KMP. OpenAPI conserva la postergación acordada.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. Cátedra solicita teléfono; Turnos valida y conserva la solicitud para el proceso propio.
2. El propietario consulta el proceso por HTTP y distingue solicitud disponible, espera o resultado conocido.
3. Envía teléfono y UUID de la acción; Turnos verifica propiedad, correlación, entrada, estado y plazo.
4. Conserva la respuesta antes de aceptarla y publica el evento contractual. Si Kafka está caído, queda pendiente y se reanuda dentro del plazo; no se afirma confirmación central.
5. Cátedra comunica confirmación, rechazo, expiración o invalidez; Turnos conserva el resultado sin duplicar efectos. Ante rechazo, el propietario puede corregir con una nueva respuesta mientras siga vigente.
6. Consultar devuelve progreso e incertidumbres, también históricos; una discrepancia activa reconciliación acotada sin modificar la reserva central.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- KMP aporta proceso local, teléfono y UUID por acción. Reutiliza ese UUID al reintentar y genera otro para corregir tras un rechazo. El propietario procede del JWT y la correlación central, de Turnos; el UUID no sustituye IDs centrales.
- Eliminar espacios, guiones, paréntesis y puntos; exigir `^\+?[0-9]{7,15}$`, sin agregar país ni validaciones ajenas al contrato.
- La respuesta usa `AdditionalInformationSubmitted`, v1, todos los campos obligatorios y message key `reservationProcessId`. `requestEventId` referencia la solicitud real.
- Republicar conserva `eventId` y contenido: cátedra deduplica antes de comprobar el estado, sin reemitir confirmación. Corregir tras rechazo crea un evento nuevo; no sustituir una respuesta pendiente/incierta por otro teléfono.
- Al alcanzar `expiresAt` no se inician publicaciones; el reloj no demuestra cierre central ni fallo de un envío en curso. Resultados demorados se reconcilian sin reabrir cierres verificados.
- `AppointmentProcessInvalid` bloquea continuar y puede requerir reconciliación; no acredita cancelación. `AdditionalInformationRejected` permite corregir hasta el mismo vencimiento. El ACK no sustituye `AppointmentConfirmed`.
- Conservar estados públicos de CU-04, distinguiendo solicitud pendiente, teléfono requerido, envío pendiente/incierto, espera de resultado, rechazo corregible y cierre/reconciliación. Nombres adicionales y HTTP van en PLAN.
- Reconciliar con IDs conocidos y verificar proceso/paciente/selección: el listado central abarca toda la integración. Una respuesta vacía u ocupación ausente no acredita cancelación ni libera un hold conocido.
- Conservar lo necesario para completar/recuperar; no filtrar JWT, datos ajenos ni configuración privada en respuestas/logs.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| No llegó la solicitud adicional | Informar espera; impedir teléfono sin `requestEventId` auténtico. |
| Entrada inválida o teléfono rechazado | Entrada inválida no publica; rechazo central conserva corrección antes del plazo. |
| Proceso inexistente o ajeno | Rechazar sin revelar datos ni producir eventos. |
| Kafka inaccesible antes de aceptar | Aceptar solo si se conserva durablemente; informar pendiente y reanudar antes del vencimiento, con intentos acotados. |
| Respuesta HTTP o ACK del broker perdido | Conservar identidad/progreso; no crear una respuesta diferente para eludir incertidumbre. |
| Duplicados o envíos concurrentes | Mismo UUID/contenido devuelve lo guardado; otro contenido da conflicto. Un UUID nuevo no permite reemplazar una respuesta pendiente/incierta. |
| Resultado llega antes del ACK local | Aplicar resultado central; la actualización del envío no lo hace retroceder. |
| Plazo alcanzado o cierre verificado | Impedir publicaciones nuevas; distinguir reloj de evidencia central y no reabrir cierres. |
| Consulta con cátedra inaccesible | Entregar estado local disponible con aviso de falta de verificación actual. |
| Reinicio o interrupción | Recuperar lo aceptado sin perder correlación, sin ampliar el plazo ni recrear reservas. |
| Catálogo no disponible | La consulta local continúa; completar un proceso existente no exige una nueva agenda vigente. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Mantener CU-05/CU-06 separados dentro de la SPEC conjunta y conservar propiedad/límites de CU-04.

Respetar contratos e identidades existentes. Cátedra admite republicación idempotente y permite Outbox, sin imponerlo; persistencia, publicación, reintentos y transporte se diseñan en PLAN.

Un topic accesible o 202 REST no acredita intercambio funcional. D-01 a D-03 están confirmadas para este flujo; el contrato HTTP y la adaptación de KMP se coordinarán en PLAN, sin implementar su interfaz aquí.

Condición explícita del usuario: solución mínima, con estados que representen hechos verificables y sin duplicar mecanismos de identidad/progreso. El PLAN justificará la coordinación necesaria para guardar/publicar, limitar reintentos y respetar vencimientos; si requiere complejidad sustancial, se revisará antes de implementar.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Consultar o enviar sobre un proceso ajeno/inexistente no revela su contenido ni publica; credenciales inválidas no acceden.
- **CA-02 · RF-02:** Una solicitud válida habilita teléfono con correlación/vencimiento reales; sin solicitud, la aceptación REST no permite fabricar una respuesta.
- **CA-03 · RF-03:** Normalizar según el contrato acepta sus formatos válidos y rechaza entradas inválidas sin publicación; el usuario puede corregir.
- **CA-04 · RF-04:** El envío queda conservado antes de publicar con todos los campos y key correctos; guardado o ACK no se informan como reserva confirmada.
- **CA-05 · RF-04, RF-09:** Repetir UUID/contenido devuelve el envío existente; reutilizarlo con otro contenido da conflicto. Un UUID nuevo no reemplaza silenciosamente una respuesta pendiente/incierta.
- **CA-06 · RF-05:** Un rechazo central permite corrección vigente con evento nuevo y la solicitud original, sin convertir el rechazo en fallo terminal.
- **CA-07 · RF-03, RF-05, RF-09:** Antes/en/después del vencimiento se respeta el plazo; no se publica fuera de él ni se inventa cierre por reloj, y un proceso final no vuelve a pedir teléfono.
- **CA-08 · RF-05, RF-06:** Confirmación anticipada al ACK se conserva una sola vez y no revierte una cancelación verificada; invalidez bloquea envíos y señala reconciliación.
- **CA-09 · RF-07, RF-09:** El propietario consulta por HTTP espera, información requerida, envío, incertidumbre e históricos, también al reabrir; repetir consultas no crea efectos centrales ni renueva el plazo.
- **CA-10 · RF-07, RF-08:** Con dependencia inaccesible, el estado local se entrega con aviso; una reconciliación acotada usa evidencia propia y no considera una respuesta vacía como cierre.
- **CA-11 · RF-04, RF-08:** Kafka caído permite guardar una respuesta válida como pendiente; si no se puede conservar, no se informa aceptación. Cortes/reinicios no pierden lo aceptado; reanudar conserva ID/contenido, límites y plazo, sin éxito ficticio.
- **CA-12 · RF-02, RF-06:** Duplicados, eventos ajenos/incompatibles y campos adicionales compatibles se tratan sin mezclar procesos; ningún offset avanza antes de persistir sus efectos.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Consultar/enviar con JWT inválido, dueño distinto o proceso ausente. | Sin filtración ni publicación. |
| CA-02 | Solicitud correlacionada frente a aceptación REST sin evento. | Solo la primera habilita teléfono. |
| CA-03 | Normalización, 7/15 dígitos, fuera de límites y caracteres inválidos. | Solo entradas contractuales publicables. |
| CA-04 | Observar conservación, evento y ACK antes del resultado. | Correlación completa; progreso aún pendiente. |
| CA-05 | Repetir/superponer UUID y cambiar contenido o UUID durante un envío pendiente. | Resultado conservado o conflicto, sin sustitución silenciosa. |
| CA-06 | Rechazar y corregir mientras sigue vigente. | Nuevo evento, misma solicitud, sin cierre por rechazo. |
| CA-07 | Variar instante del envío y entregar resultados demorados. | Respeto del plazo sin cierres ficticios ni reapertura. |
| CA-08 | Intercalar confirmación con ACK/rechazos y enviar invalidez/cancelación. | Resultado coherente y único, sin retroceso. |
| CA-09 | Reabrir y repetir consultas HTTP de pendiente e históricos. | Progreso propio sin efectos centrales nuevos. |
| CA-10 | Fallar dependencias y reconciliar una respuesta ajena/vacía. | Estado local con aviso; ningún cierre inventado. |
| CA-11 | Guardar con Kafka caído, recuperar conectividad, reiniciar y alcanzar el plazo. | Solo aceptar tras conservar; mismo ID/contenido al reanudar, sin publicar fuera de plazo. |
| CA-12 | Duplicar eventos y fallar la persistencia antes del offset. | Efectos únicos; sin ACK prematuro ni cambios ajenos. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna decisión funcional nueva pendiente. Confirmadas por el usuario el 2026-10-31, con la condición de mantener una solución mínima y estados coherentes:

- **D-01 · UUID por envío:** KMP conserva el UUID al reintentar la misma acción; una corrección tras rechazo usa otro. RF-04/CA-05.
- **D-02 · Pendiente con Kafka caído:** aceptar solo después de conservar; reanudar con intentos acotados dentro del plazo. Pendiente, recibido por el broker y confirmado por cátedra siguen siendo hechos distintos. RF-04/RF-08 y CA-04/CA-11.
- **D-03 · Consultas HTTP:** KMP consulta mientras esté pendiente y al reabrir, sin SSE. RF-07/CA-09.

**D-04 · Recuperación automática, confirmada el 2026-10-31 durante PLAN:** tras tres intentos de publicación fallidos, esperar y abrir otro ciclo acotado mientras siga vigente, con el mismo evento/contenido. No depende de que KMP permanezca abierto. Concreta RF-08/CA-11 sin extender el plazo ni autorizar reenvíos después del vencimiento.

La SPEC y el PLAN fueron aprobados explícitamente el 2026-10-31; se autorizó derivar TASKS. CU-04 está integrado en la base verificada. La implementación requiere autorización explícita posterior.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

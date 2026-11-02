# SPEC: Cancelar reservas y abandonar procesos propios

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-11-02. SPEC conjunta para [#13/CU-08](https://github.com/prog2-perassiferrara/backend-turnos/issues/13) y [#15/CU-10](https://github.com/prog2-perassiferrara/backend-turnos/issues/15), aprobada explícitamente por el usuario, con alcance de cancelación de confirmadas y abandono con vencimiento/reconciliación confirmado el 2026-11-02. Ambos casos mantienen sus objetivos y trazabilidad separados; la autorización y el avance de implementación constan en [TASKS.md](TASKS.md).

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

El usuario autenticado podrá cancelar una reserva propia confirmada (CU-08) o abandonar explícitamente su proceso pendiente, detener nuevos pasos y esperar el vencimiento del hold (CU-10). Conocerá si la cancelación se verificó, fue rechazada o sigue pendiente, sin perder progreso ante interrupciones.

Fuentes: [CU-08](../../use-cases/CU-08-cancelar-reserva.md), [CU-10](../../use-cases/CU-10-abandonar-proceso.md), [enunciado, 4.2 y 7 a 9](../../../../PROJECT_STATEMENT-v1.md), [anexo, 12, 13.1, 15.10 y 18](../../../../INTEGRATION_REFERENCE-v2.md). Conserva las reglas de [inicio](../inicio-reserva/SPEC.md), [teléfono/progreso](../telefono-progreso-reserva/SPEC.md) y [reservas propias](../consulta-reservas-propias/SPEC.md). La recuperación transversal restante corresponde a [CU-09/#14](../../use-cases/CU-09-recuperar-procesos.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La base local revisada es `129b665`, en la rama de CU-08. El 2026-11-01 se comprobó que su contenido versionado coincide con `main` remoto (`cebeabe`), tras integrar CU-07. Existen inicio, teléfono, consulta individual y listado propio; se conserva progreso y se procesan cancelaciones recibidas mediante REST/Kafka. Aún no se puede solicitar cancelar o abandonar desde las APIs propias.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Autorizar ambas acciones exclusivamente al propietario autenticado, mediante la referencia local del proceso/reserva. No aceptar una identidad de paciente ni identificadores centrales elegidos por KMP.
- **RF-02 · CU-08:** Cancelar una reserva propia confirmada mediante la operación central. Comunicar cancelación completada solo tras verificarla; repetir sobre una ya cancelada conserva el resultado sin efectos funcionales duplicados. No pedir ni capturar motivo, según D-01.
- **RF-03 · CU-10:** Conservar la solicitud explícita de abandono de un proceso propio pendiente con identificadores centrales conocidos, detener nuevos pasos y esperar su vencimiento central. No solicitar cancelación mientras siga pendiente. Salir de la pantalla, cerrar KMP o consultar no equivale a abandonar ni extiende el vencimiento. Tampoco se pide motivo.
- **RF-04:** Una intención de abandono aceptada impide nuevos pasos de confirmación o envío/reenvío de teléfono. Si la reserva se confirma concurrentemente, esa misma intención autoriza cancelarla sin otra acción del usuario; un envío ya en curso no permite prometer que su efecto central fue impedido.
- **RF-05:** Conservar la intención antes de cualquier efecto central y mantenerla ante timeout, respuesta perdida o dependencia inaccesible. Reconciliar antes de repetir efectos, con hasta tres intentos automáticos; conservar el trabajo necesario tras reinicios. Al agotarlos, mantener pendiente y requerir reintento explícito para otro ciclo, sin reiniciar el presupuesto por consultar o repetir la solicitud original. La recuperación de estas acciones forma parte de su alcance, sin esperar a completar toda la recuperación de CU-09.
- **RF-06:** Reconciliar respuestas REST y eventos Kafka correlacionados, incluidos duplicados, carreras y resultados tardíos, sin duplicar reservas ni revertir una cancelación verificada. Distinguir rechazo, vencimiento/fallo central verificado y resultado todavía incierto.
- **RF-07:** Cancelar con los datos propios y el acceso técnico utilizable, sin exigir Catálogo vigente ni Kafka disponible para reconocer un resultado REST válido. Si falta acceso central, mantener la intención sin falso éxito; si no se puede conservar localmente, no aceptar ni iniciar el efecto central.
- **RF-08:** Exponer por las consultas propias el resultado conocido, la intención pendiente y las acciones disponibles. El abandono incierto mantiene el límite de un proceso de creación pendiente por usuario; cancelar una reserva confirmada no inicia ni reabre un proceso de creación. Conservar identidad, propietario e historial.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Recuperación transversal de todos los casos de CU-09; sí se incluye conservar y recuperar las intenciones de cancelación/abandono de esta funcionalidad.
- Cancelar un supuesto hold sin identificadores recuperables. Ese caso conserva la política docente ya incorporada en CU-04: archivar el intento y consultar ocupaciones antes de otra creación limitada, sin atribuirse el hold observado.
- Cambios al contrato de cátedra, acceso directo a holds en Redis y desarrollo de pantallas KMP. La consolidación OpenAPI/Swagger continúa postergada.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. El usuario solicita cancelar una reserva confirmada (CU-08) o abandonar su proceso pendiente (CU-10), usando su referencia local.
2. Turnos verifica autenticación, propiedad y estado. Si el resultado ya es terminal, lo comunica sin reabrir el proceso; una reserva confirmada puede pasar a cancelada.
3. Conserva la intención. Solo una reserva confirmada habilita solicitar la cancelación central. El abandono detiene nuevos pasos del flujo de reserva, sin asumir que se detuvieron operaciones ya enviadas.
4. En un pendiente espera el plazo central; si existe una operación incierta, verifica mediante los identificadores conocidos y reconcilia la evidencia REST/Kafka. Si aparece una confirmación concurrente, conserva la intención y continúa su cancelación.
5. Devuelve cancelación verificada, rechazo o pendiente de verificación. La intención y el resultado siguen consultables tras cortes/reinicios; una respuesta incierta no se transforma en liberación ni permite eludir el límite vigente.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- La referencia pública sigue siendo el UUID local existente. La propiedad proviene del usuario autenticado, nunca del `groupId` técnico ni de datos de propietario suministrados por KMP. No se exponen credenciales ni identificadores técnicos privados.
- **D-01 confirmada:** ambas acciones se solicitan sin motivo. El contrato central permite omitirlo; no se agrega un campo para capturarlo ni un motivo ficticio. Esto no elimina los estados/códigos de resultado conocidos.
- Cancelación solicitada y estado central son hechos diferentes: una reserva puede seguir confirmada mientras su cancelación está pendiente. Consultarla no informa `CANCELLED` antes de verificarlo. La cancelación de otra reserva no altera el proceso pendiente que el usuario ya pudiera tener.
- La cancelación central se solicita exclusivamente sobre reservas confirmadas; una ya cancelada conserva el resultado idempotente (anexo 12 y 13.1). Abandonar un pendiente detiene nuevos pasos y espera su vencimiento, sin enviar una cancelación del hold.
- Las distintas precisiones de representación del mismo vencimiento no deben producir una discrepancia falsa; la representación conservada nunca amplía el plazo central.
- Una respuesta o evento acredita el resultado solo si corresponde a la operación y al proceso propios. REST puede verificar la cancelación sin esperar Kafka; una respuesta inválida o insuficiente mantiene la incertidumbre. Persistir los efectos de un evento antes de confirmar su offset.
- `RESERVATION_NOT_FOUND`, `RESERVATION_NOT_CANCELLABLE`, una lista vacía o la desaparición del slot de ocupaciones no prueban cancelación ni expiración. Informar el rechazo o discrepancia y conservar lo conocido para reconciliar. El mero paso de `expiresAt` tampoco demuestra el resultado de una operación incierta. La espera estable sin teléfono registrado se distingue de esa incertidumbre.
- El plazo central permite cerrar como vencido un abandono en espera estable sin ninguna respuesta telefónica registrada; no acredita liberar el horario ni cancelar. Si hubo envío telefónico u otra operación incierta, el reloj no decide su resultado: se requiere evidencia REST/Kafka. Un vencimiento o fallo central válidamente verificado se informa como tal, sin llamarlo cancelación exitosa. Una intención pendiente no habilita a reabrir un proceso ya terminado ni a reanudar teléfono. Esperar hasta expiresAt no consume intentos de cancelación; una verificación incierta posterior usa el presupuesto acotado de recuperación.
- Repetir la misma solicitud no crea otra intención independiente ni multiplica intentos sin límite. Una intención aceptada no se borra al cerrar KMP. Un rechazo definitivo se informa, sin convertirlo en un bucle automático de efectos.
- Liberar el horario no garantiza que siga disponible: otro usuario puede ocuparlo. No crear otro hold ni una reserva sustituta como consecuencia de cancelar o abandonar.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Cancelación REST válida o evento correlacionado | Conservar y mostrar cancelación verificada una sola vez. |
| Proceso ajeno, inexistente o referencia inválida | Rechazar sin revelar datos ajenos ni ejecutar cancelación central. |
| Ya cancelado, vencido o fallido | Mostrar el resultado conocido, sin reabrirlo ni presentar todo cierre como cancelación. |
| Confirmación simultánea al abandono | Mantener intención y cancelar la reserva confirmada sin otra acción del usuario. |
| Teléfono pendiente o publicación en curso | No iniciar nuevos envíos tras aceptar abandono; reconciliar los ya enviados y continuar la cancelación si se confirmó. |
| Timeout, respuesta perdida o cátedra inaccesible | Intención conservada y resultado pendiente; recuperación acotada sin depender de que KMP siga abierto. |
| Rechazo central o evidencia incompatible | Informar el problema y conservar progreso; no afirmar liberación ni borrar el registro. |
| Catálogo o Kafka no disponibles | Usar el acceso central utilizable y aceptar evidencia REST válida; no exigir un evento para declarar lo ya verificado. |
| Persistencia local inaccesible | Informar indisponibilidad; sin aceptación ni efecto central nuevo. |
| Duplicados, resultados en otro orden o reinicio | Conservar identidad/intención y resultado coherente; ninguna confirmación vieja revierte una cancelación verificada. |
| Creación incierta sin IDs centrales | Mantener la política específica de CU-04; no anunciar abandono central ni cancelar un hold por su ocupación. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Mantener los CU/issues separados dentro de la especificación conjunta y respetar los contratos de cátedra, la restricción de estados del anexo y la autorización por usuario.
- Conservar las reglas aprobadas de inicio, envío de teléfono, consulta individual y listado, incluido el único proceso de creación pendiente. No duplicar el catálogo ni cambiar el contrato de cuentas.
- Integrar con las consultas HTTP existentes, sin SSE. Representación de la intención, endpoints propios y mecanismo/límites de recuperación se concretan en PLAN; no agregar infraestructura o estados especulativos.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Solicitudes sin autorización, ajenas, inexistentes o con referencia inválida no revelan contenido ajeno ni cancelan centralmente; los identificadores suministrados no permiten cambiar de propietario.
- **CA-02 · RF-02/RF-06:** Cancelar una reserva confirmada propia sin motivo produce `CANCELLED` solo tras evidencia central válida; repetir conserva el resultado sin duplicar efectos. Un resultado REST válido basta aunque Kafka no esté disponible.
- **CA-03 · RF-03:** Abandonar explícitamente un proceso propio identificable conserva la intención, detiene nuevos pasos y espera el vencimiento sin cancelar el hold; salir/cerrar KMP o consultar no la solicita ni renueva el plazo. La solicitud no exige motivo.
- **CA-04 · RF-04/RF-06:** Si la confirmación se cruza con el abandono, la intención se conserva y permite cancelar la reserva resultante sin otra acción; una confirmación anterior no revierte `CANCELLED` verificado.
- **CA-05 · RF-04:** Tras aceptar abandono no se inician nuevas confirmaciones ni envíos/reenvíos de teléfono; uno que ya estaba en curso no restaura el flujo ni borra la intención de cancelar.
- **CA-06 · RF-05:** Timeout, respuesta perdida o interrupción después de conservar la intención dejan resultado pendiente, también tras reiniciar. La recuperación reconcilia antes de repetir y realiza como máximo tres intentos por ciclo, sin depender de mantener KMP abierto. Al agotarlos no sigue enviando automáticamente ni reinicia el presupuesto por consultar/repetir; solo un reintento explícito habilita otro ciclo. No informa éxito sin verificarlo.
- **CA-07 · RF-06:** REST/Kafka en distinto orden, eventos duplicados o ajenos y fallo local antes del offset no duplican efectos ni alteran otro proceso; la cancelación verificada permanece estable.
- **CA-08 · RF-05/RF-06:** Rechazos centrales, respuesta inválida, ausencia en listados/ocupaciones o plazo local transcurrido no se presentan como cancelación. El plazo central cierra solo una espera estable sin teléfono registrado; una operación incierta requiere evidencia. Vencimiento/fallo central verificado se comunica con su resultado propio, sin reabrir ni repetir efectos rechazados indefinidamente.
- **CA-09 · RF-07:** Sin Catálogo/Kafka, un acceso central utilizable permite cancelar; sin acceso central se conserva la intención pendiente. Si falla conservarla localmente, no se acepta ni se inicia la cancelación central.
- **CA-10 · RF-08:** Consulta individual y listado distinguen el resultado conocido de la intención pendiente, conservando propietario e historial. Un abandono incierto bloquea otro inicio; cancelar una reserva confirmada no reabre creación ni altera otro pendiente.
- **CA-11 · RF-03/RF-08:** Sin IDs recuperables de un hold no se ejecuta una cancelación ficticia: se conserva el criterio previo de ocupaciones de CU-04, sin atribuirse un hold ni usar diez minutos locales como prueba de liberación.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Variar JWT, propietario, referencia y entradas de identidad. | Rechazo/aislamiento sin cancelación ni datos ajenos. |
| CA-02 | Cancelar confirmada sin motivo, repetir y omitir Kafka tras respuesta REST válida. | Una cancelación verificada, sin necesidad del evento ni efectos duplicados. |
| CA-03 | Abandonar pendiente identificable; comparar con salir, cerrar o consultar, y alcanzar expiresAt sin teléfono registrado. | Solo la acción explícita conserva intención; ningún POST de cancelación mientras siga pendiente y mismo plazo. |
| CA-04 | Intercalar confirmación con abandono y repetir confirmación vieja tras cancelar. | Intención conservada y cancelación sin otra acción; sin retroceso. |
| CA-05 | Solicitar abandono con teléfono aceptado, pendiente de publicación o envío ya iniciado. | Sin nuevos pasos del flujo; el envío en curso no restaura teléfono ni elimina la intención. |
| CA-06 | Perder respuesta antes/después del efecto, reiniciar, restablecer comunicación y agotar tres intentos; consultar/repetir antes de pedir reintento explícito. | Intención durable, reconciliación previa y presupuesto persistido; solo la acción explícita inicia otro ciclo, sin falso éxito. |
| CA-07 | Cambiar orden REST/Kafka, duplicar, mezclar eventos ajenos y fallar persistencia. | Resultado único/correlacionado, sin offset previo a conservar efectos. |
| CA-08 | Devolver 404/409, evidencia incompleta, listas vacías y vencimiento/fallo verificados; contrastar espera estable sin teléfono con operación incierta al alcanzar expiresAt. | Rechazo/incertidumbre/cierre distinguibles; el plazo solo cierra la espera estable, nunca una operación incierta, sin bucles de rechazo. |
| CA-09 | Retirar Catálogo, Kafka, acceso central o persistencia local por separado. | Uso de dependencias disponibles, intención pendiente o rechazo local sin efecto según el caso. |
| CA-10 | Consultar tras reinicio; combinar abandono incierto y cancelación de una confirmada con otro pendiente. | Progreso propio distinguible y límite preservado, sin mezclar procesos ni reabrir creación. |
| CA-11 | Perder la respuesta de creación sin IDs y observar HELD/CONFIRMED/libre. | Política de CU-04 conservada; ninguna cancelación o propiedad inventada. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional pendiente. Decisiones confirmadas por el usuario el 2026-11-01:

- **D-01:** cancelar y abandonar sin motivo por ahora.
- **D-02:** hasta tres intentos automáticos de cancelación; si se agotan, conservar pendiente y permitir reintento explícito, sin ciclos automáticos indefinidos.

Se conservan las decisiones ya aprobadas: abandono explícito, salida sin cancelación, un único proceso de creación pendiente y abandono que autoriza cancelar una confirmación concurrente. El PLAN precisará la representación y recuperación acotada de las intenciones; la cancelación se limita a reservas confirmadas conforme al anexo.

**Concreción posterior a la aprobación, 2026-11-01:** D-02 fija el límite de RF-05/CA-06 porque una reserva confirmada no tiene un vencimiento que corte los ciclos. Confirmada explícitamente por el usuario durante la preparación del PLAN; requiere presupuesto durable y validación de agotamiento/reintento, sin ampliar los CU incluidos.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

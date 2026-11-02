# SPEC: Recuperar procesos pendientes y resultados inciertos (CU-09)

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación:** 2026-11-02, explícita del usuario. La autorización de implementación se registra en TASKS.

**Issue:** [#14](https://github.com/prog2-perassiferrara/backend-turnos/issues/14)

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

Completar la recuperación automática de reservas interrumpidas para que el propietario pueda conocer y continuar su proceso después de un reinicio o una caída, sin tener que mantener KMP abierto ni repetir su solicitud. Recuperar una intención existente no autoriza crear otra reserva ni inventar el resultado central. Referencias: [CU-09](../../use-cases/CU-09-recuperar-procesos.md), [enunciado §8](../../../../PROJECT_STATEMENT-v1.md) y [anexo §15.1 y §18](../../../../INTEGRATION_REFERENCE-v2.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Base revisada: `5eaafca`, con el PR #29 integrado en main; su árbol coincide con `0deefd3`, utilizado al preparar y validar esta funcionalidad. Se conservan propietario, progreso, identificadores y eventos; teléfonos aceptados y cancelaciones ya tienen recuperación automática y presupuestos propios. Las consultas individual e histórica pueden reconciliar resultados centrales. Falta recuperar de forma general los procesos interrumpidos sin depender de esas consultas y resolver eventos válidos que llegaron antes de poder asociarlos. No se rehacen los mecanismos existentes ni se presupone disponible el circuito central Kafka para acreditar la implementación.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Detectar y recuperar operaciones propias interrumpidas al arrancar y durante el funcionamiento, conservando propietario, intención, selección, identificadores, progreso y plazos. No depender de una consulta ni de la conexión de KMP.
- **RF-02:** Continuar únicamente pasos autorizados cuyo inicio pueda acreditarse como pendiente. Un hold conocido, vigente y nunca intentado confirmar puede continuar su confirmación inicial; una intención de abandono impide ese avance. Una creación interrumpida sin IDs recuperables sigue la alternativa ya aprobada de CU-04, sin crear automáticamente otro hold ni atribuir propiedad por ocupaciones.
- **RF-03:** Reconciliar operaciones intentadas o inciertas mediante evidencia central correlacionada antes de repetir efectos. Una confirmación incierta no se reenvía por timeout, ausencia en listados o reinicio. CONFIRMED, CANCELLED y FAILED verificables actualizan el resultado; PHONE_PENDING no permite inventar una solicitud de teléfono.
- **RF-04:** Conservar las recuperaciones ya aprobadas de teléfono, cancelación y abandono: identidad y contenido del envío, vencimiento, contadores, pausas y autorización. El reinicio y las lecturas no renuevan plazos ni reinician presupuestos; una cancelación agotada requiere el reintento explícito de CU-08/CU-10.
- **RF-05:** Reexaminar eventos válidos de tipo soportado conservados sin asociación cuando posteriormente se pueda acreditar su proceso propio. Conservar la recepción y su necesidad de recuperación antes de confirmar el offset; al resolver la asociación, aplicar sus efectos una sola vez. No reinterpretar eventos rechazados o tipos desconocidos ni importar reservas sin asociación local comprobada.
- **RF-06:** Al vencer el plazo, impedir nuevos pasos de confirmación o teléfono. Si consta que un hold conocido nunca tuvo un intento de confirmación inicial, cerrarlo como EXPIRED y liberar el pendiente usando su expiresAt central. Si existe un efecto incierto, mantener el pendiente y continuar verificaciones REST automáticas espaciadas, con límites por ciclo, incluso después del vencimiento, hasta obtener evidencia resolutiva.
- **RF-07:** Conservar aislamiento entre propietarios y consistencia ante recuperación, solicitudes y eventos concurrentes. Un resultado final no se reabre por información vieja; la cancelación posterior de una confirmada sigue el contrato existente. No ejecutar efectos cuyo permiso local no esté conservado de forma verificable.
- **RF-08:** Exponer el progreso recuperado al propietario mediante las consultas existentes, distinguiendo resultado verificado, espera e indisponibilidad. No informar cancelación, confirmación ni liberación central sin evidencia; no exponer credenciales o datos de otros pacientes.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Nuevos flujos de registro, búsqueda o reserva, SSE y cambios de contratos KMP ya aprobados.
- Recuperar IDs de un hold mediante una API inexistente, inventar solicitudes Kafka o tratar una ocupación como prueba de propiedad.
- Sustituir las políticas de teléfono, cancelación o abandono ya implementadas; sus límites y resultados deben conservarse.
- Importar reservas centrales sin asociación local ni reconstruir una base eliminada. La recuperación parte del progreso persistido.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. Turnos detecta trabajo interrumpido o pendiente sin requerir una acción del usuario y recupera sus datos conservados.
2. Distingue el trabajo que sigue en ejecución, el que puede continuar con seguridad, la creación sin IDs y los resultados que requieren verificación.
3. Reexamina los eventos válidos asociables y obtiene evidencia central cuando corresponde. Una dependencia inaccesible conserva el progreso para una revisión posterior.
4. Aplica el resultado verificable o continúa exclusivamente el paso autorizado, respetando abandono, vencimiento y presupuestos anteriores.
5. Ante vencimiento, cierra el hold nunca intentado confirmar; los efectos inciertos siguen sujetos a verificación automática espaciada.
6. Conserva el resultado y lo hace consultable al propietario. Una respuesta o evento concurrente obliga a respetar el progreso más reciente.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- La identidad local del proceso y su propietario no cambian al recuperarlo; la cuenta técnica no identifica al usuario final.
- Un hold conocido conserva holdId, reservationProcessId y expiresAt. La falta de una marca local verificable de «confirmación nunca intentada» impide aplicar el cierre seguro por plazo.
- Una confirmación inicial aceptada no equivale a una reserva CONFIRMED. Ni una consulta vacía ni la ausencia de eventos demuestran que una operación falló.
- Los mensajes conservan eventId, message key y requestEventId cuando corresponda; el teléfono solo responde a una solicitud auténtica. Un ACK no sustituye el resultado central.
- El único pendiente continúa bloqueando nuevos inicios mientras exista cierre incierto. Se mantienen las excepciones ya aprobadas para el intento sin IDs y el abandono resuelto; el hold nunca intentado confirmar se libera al vencer según D-02.
- D-01, confirmada el 2026-11-02: seguir verificando resultados inciertos tras vencer, con consultas espaciadas y límites por ciclo. Esto no habilita más intentos con efectos ni un bucle continuo.
- D-02, confirmada el 2026-11-02: cerrar como EXPIRED el hold conocido vencido únicamente si consta que nunca se intentó confirmar. Una confirmación intentada o incierta requiere evidencia central.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Operación todavía en ejecución | No competir por sus efectos ni cerrarla como interrumpida sin acreditar que corresponde recuperarla. |
| Sin trabajo pendiente | No crear reservas ni alterar históricos. |
| Evento inválido, desconocido o ajeno | Conservar su tratamiento previo sin aplicarlo a otro proceso. |
| Error o espera excesiva | Conservar estado e incertidumbre; no repetir una confirmación incierta. |
| Acceso no autorizado | Las consultas existentes siguen rechazando al usuario ajeno sin revelar el proceso. |
| Cátedra inaccesible | Conservar el pendiente y verificar después, sin emitir éxito ficticio ni impedir consultas que pueden resolverse con datos propios. |
| Almacenamiento no disponible | No ejecutar un efecto cuyo permiso o resultado no se pueda conservar; retomar cuando vuelva. |
| Solicitudes o eventos concurrentes/duplicados | Un único avance autorizado; persistencia previa al offset y sin reapertura por información antigua. |
| Reinicio con trabajo pendiente | Recuperar desde progreso conservado, sin renovar plazos, presupuestos o identidades. |
| Hold vencido sin intento de confirmación | EXPIRED y liberación del pendiente, sin afirmar que el horario sigue libre. |
| Resultado incierto después de expiresAt | No confirmar ni publicar teléfono; conservar el pendiente y continuar verificaciones espaciadas. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Mantener los contratos y decisiones de [inicio de reserva](../inicio-reserva/SPEC.md), [teléfono y progreso](../telefono-progreso-reserva/SPEC.md), [historial](../consulta-reservas-propias/SPEC.md) y [cancelación/abandono](../cancelacion-abandono/SPEC.md), salvo la recuperación adicional definida aquí.
- Aplicar el [criterio de creación sin IDs](../../../../dudas_Slack.md#duda-18-resuelta) y las operaciones del anexo; no consultar claves internas de holds ni agregar dependencias de Catálogo para verificar estados propios.
- Mantener recuperaciones acotadas, observables y seguras frente a concurrencia, priorizando completar lo necesario sin rehacer el flujo existente.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01/RF-08:** Tras reiniciar con trabajo pendiente, Turnos lo detecta sin consultar desde KMP; conserva propietario, IDs y progreso y hace consultable el resultado recuperado.
- **CA-02 · RF-02/RF-07:** Con un hold vigente cuyo intento de confirmación no comenzó, recuperarlo permite un único avance autorizado; una ejecución concurrente o abandono impide otra confirmación.
- **CA-03 · RF-02:** Una creación interrumpida sin IDs no genera otro hold por el reinicio. El intento queda tratado según CU-04; una acción nueva exige sus comprobaciones de ocupación y presupuesto, sin atribuir propiedad.
- **CA-04 · RF-03/RF-08:** Con confirmación incierta o espera de resultado, una reserva central exactamente correlacionada en CONFIRMED/CANCELLED/FAILED converge sin otro POST ni evento inventado. Ausencia, contradicción o recorrido parcial conservan incertidumbre; PHONE_PENDING no habilita teléfono sin solicitud.
- **CA-05 · RF-04/RF-07:** Reiniciar con teléfono aceptado y ACK perdido conserva identidad, contenido y presupuesto; retoma solo el envío permitido antes de vencer y no lo reabre tras un resultado final o abandono.
- **CA-06 · RF-04:** Reiniciar con cancelación o abandono pendiente conserva intención, contadores y fechas; nunca cancela un pendiente ni abre otro ciclo agotado sin reintento explícito.
- **CA-07 · RF-05/RF-07:** Un evento soportado recibido antes de conocer los IDs locales se aplica una sola vez cuando la asociación queda acreditada. Un reinicio, duplicado o fallo antes del commit no pierde el efecto ni permite confirmar offset prematuramente.
- **CA-08 · RF-06:** Con plazo vencido y resultado incierto, Turnos mantiene el pendiente, no emite confirmación ni teléfono y realiza verificaciones espaciadas. Tras restablecerse cátedra, evidencia suficiente resuelve automáticamente sin necesitar una consulta del usuario.
- **CA-09 · RF-06:** Un hold conocido vencido, cuya confirmación inicial consta como nunca intentada, queda EXPIRED y libera el pendiente sin POST. Si se intentó confirmar o no se puede acreditar lo contrario, esa regla no se aplica.
- **CA-10 · RF-07/RF-08:** Eventos tardíos, consultas, recuperación y solicitudes simultáneas conservan el resultado más reciente y la propiedad. Fallar al conservar el permiso local impide el efecto; el propietario ve progreso/advertencia coherentes y otro usuario no obtiene sus datos.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Reiniciar con distintas etapas pendientes sin consultar desde KMP. | Recuperación autónoma y progreso propio conservado. |
| CA-02 | Interrumpir después de conservar el hold; superponer recuperación y abandono. | Solo continúa el paso acreditado y permitido, una vez. |
| CA-03 | Reiniciar después de una creación sin IDs recuperables. | Sin creación automática; tratamiento y nuevo intento según CU-04. |
| CA-04 | Consultar evidencia exacta, ausente, ajena, contradictoria y PHONE_PENDING sin evento. | Solo la evidencia suficiente converge; no hay POST ni teléfono inventado. |
| CA-05 | Reiniciar con teléfono aceptado/ACK perdido y luego aplicar un cierre. | Misma identidad/contenido; límites conservados y sin reapertura. |
| CA-06 | Reiniciar antes/después de agotar un ciclo de cancelación o durante abandono. | Intención y presupuesto durables; reintento explícito cuando corresponde. |
| CA-07 | Recibir un evento antes de asociar IDs; reiniciar y volver a entregarlo. | Recepción durable antes del offset y aplicación única al resolver la asociación. |
| CA-08 | Vencer con incertidumbre y cátedra caída; restablecerla después. | Verificación automática espaciada hasta evidencia, sin repetir efectos. |
| CA-09 | Comparar hold vencido nunca intentado confirmar con confirmación intentada/incierta. | Solo el primer caso cierra por plazo y libera el pendiente. |
| CA-10 | Cruzar eventos/recuperación/solicitudes, fallo local y consulta ajena. | Sin efectos no acreditados, reapertura de finales ni exposición ajena. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna. D-01 y D-02 confirmadas explícitamente por el usuario; SPEC aprobada el 2026-11-02.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

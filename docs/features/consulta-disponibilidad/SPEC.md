# SPEC: Consultar disponibilidad de turnos

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-10-29. Issue [#8](https://github.com/prog2-perassiferrara/backend-turnos/issues/8), [CU-03 aprobado](../../use-cases/CU-03-consultar-disponibilidad.md).
**Aprobación:** explícita del usuario el 2026-10-29. Autoriza preparar el PLAN, no implementar.

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

Permitir que un usuario autenticado, desde KMP, consulte los horarios libres de un profesional para una fecha. Turnos combina su agenda vigente de Catálogo con las ocupaciones centrales; consultar no bloquea ni garantiza un horario para una reserva posterior.

Fuentes: [enunciado, 4.2, 7, 8 y 9](../../../../PROJECT_STATEMENT-v1.md), [anexo, 4, 8 y 13](../../../../INTEGRATION_REFERENCE-v2.md) y [contrato proveedor de Catálogo](../../../../backend-catalogo/README.md#consultar-profesional-agenda-y-estado).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Turnos ya registra y autentica usuarios, protege las APIs con JWT y obtiene acceso técnico central mediante Catálogo. Conserva los contratos de [registro/autenticación](../registro-autenticacion/SPEC.md), [seguridad](../seguridad-jwt/SPEC.md) e [integración técnica](../integracion-tecnica/SPEC.md). Todavía no calcula ni expone disponibilidad.

Catálogo ya entrega profesional, agendas completas y estado de vigencia en una misma respuesta protegida. El anexo ofrece ocupaciones por profesional y rango de fechas, incluyendo reservas confirmadas y holds activos. Esos contratos son la base de CU-03.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Admitir la consulta de un usuario autenticado y autorizado para un ID positivo de profesional y una fecha válida; aplicar la política de fechas y zona de D-01/D-02. Rechazar entradas inválidas antes de consultar dependencias.
- **RF-02:** Obtener de Catálogo el profesional, sus agendas y la vigencia acreditada conjuntamente; distinguir profesional inexistente y deshabilitado de un proveedor indisponible o sin vigencia verificable.
- **RF-03:** Determinar los inicios de turnos completos de las agendas habilitadas correspondientes al día de la semana solicitado, respetando sus horas y duración; sin agenda aplicable, devolver un vacío válido.
- **RF-04:** Para horarios candidatos, consultar las ocupaciones centrales de ese profesional y fecha y excluir las posiciones confirmadas o con hold aún activo, según su fecha y hora de inicio.
- **RF-05:** Devolver los horarios libres en orden cronológico y sin duplicados, correspondientes al profesional/fecha consultados. No crear holds, procesos ni reservas; las consultas concurrentes o repetidas no producen efectos de negocio.
- **RF-06:** Distinguir una consulta válida sin horarios de la imposibilidad de calcularlos. Ante dependencia inaccesible, espera excesiva o respuesta incompatible/incompleta, informar indisponibilidad sin inventar datos ni exponer secretos.
- **RF-07:** Conservar las identidades y recuperación aprobadas: JWT de usuario en la entrada, identidad de servicio ante Catálogo y JWT central ante cátedra. No mantener otra réplica de catálogo ni usar datos históricos como información vigente.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Búsquedas y filtros de profesionales, responsabilidad de Catálogo.
- Crear/confirmar holds, completar información, cancelar o recuperar procesos y gestionar reservas, que conservan sus CU respectivos.
- Cambiar la sincronización de Catálogo, la política de vigencia del proveedor o la recuperación/emisión de JWT ya aprobadas.
- Modificar KMP o implementar consumidores/productores Kafka por esta consulta.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. KMP solicita disponibilidad de un profesional para una fecha con su JWT de usuario.
2. Turnos valida autorización y entrada conforme a RF-01.
3. Obtiene conjuntamente de Catálogo el profesional, las agendas y la vigencia. Sin vigencia o ante fallo, informa indisponibilidad; si no existe o está deshabilitado, informa esa condición.
4. Determina los horarios aplicables conforme a RF-03 y D-01/D-02. Si no hay candidatos, devuelve un vacío válido sin requerir ocupaciones para demostrarlo.
5. Consulta las ocupaciones centrales del mismo profesional y fecha. Si no puede interpretarlas con seguridad, informa indisponibilidad.
6. Devuelve únicamente los horarios candidatos libres, ordenados y sin duplicados. CU-04 deberá validar de nuevo al intentar reservar; esta respuesta no concede un hold.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Entrada: ID de profesional de cátedra positivo y una fecha `yyyy-MM-dd`. La identidad del usuario proviene del JWT validado, no de un identificador libre enviado por KMP.
- Catálogo entrega profesional y agendas con sus flags `enabled`, además de estado y versión coherentes de la misma lectura. La consulta interna solo habilita operaciones con `CURRENT` conforme a su [política aprobada](../../../../backend-catalogo/docs/features/consulta-agenda-estado/SPEC.md#datos-y-reglas-de-negocio). No usar un health ni una consulta de estado anterior como sustituto de esa garantía.
- Solo intervienen agendas del profesional solicitado, habilitadas y del día de la semana elegido. El primer inicio coincide con la apertura; los siguientes respetan la duración de esa agenda. El turno completo debe terminar como máximo en el cierre; un remanente más corto no constituye otro turno. Ejemplo: de 09:00 a 10:10, con duración de 30 minutos, corresponden 09:00 y 09:30, no 10:00.
- **D-01, confirmada por el usuario el 2026-10-29:** admitir fechas desde hoy, sin máximo futuro adicional; rechazar fechas anteriores a hoy. Para hoy, devolver solo horarios cuyo inicio sea estrictamente posterior al momento del cálculo. Un turno que ya comenzó o cuyo inicio coincide con ese momento no se ofrece.
- **D-02, confirmada por el usuario el 2026-10-29:** usar `America/Argentina/Buenos_Aires` como zona de atención configurable para determinar hoy y los horarios ya iniciados, independientemente de la zona del dispositivo o contenedor. Las horas de agenda y ocupaciones permanecen locales de atención; aceptar `HH:mm` y `HH:mm:ss` sin desplazarlas por la zona del equipo. Los vencimientos se comparan como instantes UTC.
- La consulta central abarca exactamente la fecha solicitada, con inicio y fin inclusivos. Su respuesta debe corresponder al profesional/fecha consultados; una colección vacía de ocupaciones es válida. La falta de colección o datos necesarios no equivale a cero ocupaciones.
- Una ocupación excluye la posición identificada por fecha/hora de inicio. El anexo no proporciona duración ni fin de la ocupación: no inventar intervalos históricos. `CONFIRMED` excluye siempre; `HELD` excluye mientras no se haya alcanzado `expiresAt`, comparado como instante UTC. Un hold con vencimiento desconocido o una ocupación de estado no interpretable no habilita horarios libres por omisión. Si coinciden ambas ocupaciones, prevalece la confirmada.
- Unir horarios aplicables sin repetir inicios. No revelar detalles de pacientes, reservas o dueños de los holds al informar disponibilidad.
- La disponibilidad describe los datos obtenidos, sin atomicidad entre servicios ni garantía de que sigan libres al reservar. Solicitudes simultáneas pueden devolver el mismo horario, porque ninguna lo bloquea.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Consulta válida, con agenda vigente y ocupaciones interpretables | Horarios libres ordenados, sin duplicados ni efectos de reserva. |
| Profesional inexistente o deshabilitado | Condición distinguible; no devolver una disponibilidad normal ni consultar ocupaciones. |
| Sin agenda habilitada para ese día, o sin candidatos según las reglas de fecha | Vacío válido con datos de Catálogo ya verificados. |
| Todos los candidatos ocupados | Vacío válido, con ocupaciones verificadas. |
| JWT de usuario ausente/inválido o sin permiso | Rechazo de seguridad existente, antes de consultar dependencias. |
| Entrada inválida | Error de entrada, sin consultas remotas. |
| Catálogo caído, sin vigencia o respuesta incompatible | Indisponibilidad; no usar otra réplica, un estado anterior o una lista vacía de reemplazo. |
| Ocupaciones inaccesibles, timeout o contenido no interpretable | Indisponibilidad; no confundir el fallo con horarios libres. |
| Acceso técnico todavía no disponible, vencido o rechazado | Conservar el comportamiento de #4: indisponibilidad y recuperación coordinada donde corresponda. Un rechazo técnico no se presenta como error del JWT del usuario. |
| Consultas concurrentes o repetidas | Lecturas independientes, sin crear ni extender holds o procesos. |
| Reinicio o interrupción | No queda una operación de reserva pendiente creada por esta consulta; una nueva solicitud vuelve a obtener los datos necesarios. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Respetar CU-03 y el anexo v1, sin acceder a tablas/Redis internos de Catálogo ni a claves de holds centrales. Catálogo es el proveedor de agenda vigente; cátedra, el de ocupaciones. No modificar esos proveedores para acomodar esta consulta.

Los contratos internos existentes y la tolerancia de vigencia de Catálogo se conservan. La ruta propia de consulta, los campos exactos de salida, códigos HTTP y presupuestos de espera se precisarán en PLAN dentro del comportamiento descrito, sin introducir funcionalidades nuevas.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-07:** Sin autenticación válida/permiso, o con ID/fecha inválidos conforme a D-01/D-02, la consulta se rechaza antes de invocar Catálogo o cátedra. No aceptar identidad de usuario libre.
- **CA-02 · RF-02:** Con profesional y agenda de una misma lectura vigente, continuar el cálculo; distinguir inexistencia y deshabilitación. Sin vigencia acreditada o con datos de otro profesional, no informar horarios libres.
- **CA-03 · RF-03:** Con agendas habilitadas del día solicitado, devolver solo turnos completos según apertura, cierre y duración; ignorar agendas deshabilitadas/de otros días. Sin agenda aplicable, devolver vacío válido; admitir ambas representaciones de hora.
- **CA-04 · RF-04:** Excluir reservas confirmadas y holds activos del profesional/fecha solicitados. En el vencimiento y después, un hold ya no excluye por sí solo; una confirmación coincidente sigue excluyendo. Repeticiones no duplican ni liberan una posición.
- **CA-05 · RF-05:** Obtener resultados ordenados y sin duplicados, incluso con agendas coincidentes y solicitudes simultáneas. No crear holds, procesos, reservas ni efectos centrales de escritura.
- **CA-06 · RF-06:** Una lista de ocupaciones vacía válida permite los candidatos libres; falta de datos, fallos remotos, timeout o estado/vencimiento no interpretable producen indisponibilidad sin una lista falsamente libre ni secretos.
- **CA-07 · RF-01, RF-03:** Rechazar fechas anteriores a hoy y admitir fechas futuras válidas sin un horizonte máximo adicional. Para hoy, excluir inicios anteriores o iguales al momento del cálculo y conservar los posteriores, aplicando `America/Argentina/Buenos_Aires` incluso al cambiar de día y aunque el dispositivo/contenedor use otra zona.
- **CA-08 · RF-07, RF-02, RF-06:** Consultar los contratos protegidos con las identidades aprobadas, sin reenviar el JWT central a KMP ni usar datos históricos como catálogo vigente; conservar adquisición/invalidez/recuperación de #4 ante una caída de proveedores.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Variar JWT/permisos, identidad libre, IDs no positivos/incompatibles y fechas inválidas. | Rechazo antes de consultas a proveedores. |
| CA-02 | Variar profesional habilitado, inexistente, deshabilitado y respuesta sin vigencia/coherencia. | Continuación solo con el profesional correcto y datos vigentes; errores diferenciados. |
| CA-03 | Agendas de distintos días/flags/duraciones, remanente incompleto y horas con/sin segundos. | Solo inicios de turnos completos aplicables; vacío legítimo cuando corresponda. |
| CA-04 | Mezclar confirmaciones, holds, repeticiones y vencimientos justo antes/en/después del límite. | Excluir posiciones ocupadas; un vencimiento no libera una confirmación coincidente. |
| CA-05 | Repetir y superponer consultas/agendas con inicios coincidentes. | Orden estable, sin duplicados ni escrituras de negocio. |
| CA-06 | Comparar ocupaciones `[]` con respuesta incompleta, incoherente, corte, timeout o datos no interpretables. | Vacío de ocupaciones válido distinto de fallo; sin horarios libres fabricados. |
| CA-07 | Consultar ayer, hoy y fechas futuras lejanas; controlar justo antes/en/después del inicio y de medianoche, variando la zona del sistema. | Ayer rechazado; fechas futuras válidas admitidas sin máximo adicional; hoy solo inicios posteriores al cálculo según la zona de atención. |
| CA-08 | Variar identidad saliente, sesión técnica, caída de Catálogo/central y respuesta rechazada. | Contratos e integración existentes conservados; sin secretos ni otra réplica. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional. D-01, D-02 y la SPEC completa están aprobadas por el usuario. La implementación requiere autorización posterior explícita.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

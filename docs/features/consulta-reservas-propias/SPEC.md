# SPEC: Consultar reservas y procesos propios

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-11-01. [Issue #12/CU-07](https://github.com/prog2-perassiferrara/backend-turnos/issues/12). Aprobada explícitamente por el usuario tras confirmar D-01 a D-05; autorizada la preparación del PLAN, sin implementación.

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

El usuario autenticado podrá consultar desde KMP su historial de reservas y procesos anteriores, junto con el único proceso pendiente. Obtendrá datos propios, estados diferenciados y un aviso cuando la información disponible sea local o no pueda verificarse completamente.

Fuentes: [CU-07 aprobado](../../use-cases/CU-07-consultar-reservas.md), [enunciado, 4.2, 5, 8 y 9](../../../../PROJECT_STATEMENT-v1.md) y [anexo, 11 y 18](../../../../INTEGRATION_REFERENCE-v2.md). Conserva [CU-04](../inicio-reserva/SPEC.md) y [CU-05/CU-06](../telefono-progreso-reserva/SPEC.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La base local revisada es `982c8db`, merge del [PR #27](https://github.com/prog2-perassiferrara/backend-turnos/pull/27), verificado el 2026-11-01. Ya existen inicio, teléfono, resultados y consulta individual del proceso, con propiedad y progreso durables.

Falta el listado propio y conservar los datos descriptivos históricos que aporta la consulta central. La consulta individual actual no sustituye ese listado ni permite considerar una página central parcial como historial completo.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Autorizar exclusivamente al propietario autenticado; derivar su identidad estable y verificar la asociación local antes de exponer datos, referencias o conteos centrales.
- **RF-02:** Construir el listado a partir de los registros locales propios y reconciliarlos con las reservas centrales por su correlación, sin duplicar un mismo proceso/reserva. Excluir reservas centrales sin asociación local comprobada, aunque su identificación central corresponda al usuario.
- **RF-03:** Distinguir reservas confirmadas, canceladas y fallidas, procesos anteriores y el único pendiente. El pendiente permanece visible separado del historial y no se presenta como confirmación por tener un hold o teléfono aceptado.
- **RF-04:** Conservar la identidad y el nombre histórico verificado del profesional para reutilizarlos en consultas posteriores y tras reiniciar. Un nombre ausente se reconoce como no disponible, sin inventarlo ni exigir Catálogo para consultar. La hora de finalización queda fuera del listado.
- **RF-05:** Ante fallo central, interrupción o verificación incompleta, entregar los datos locales propios con aviso y alcance de actualización reconocible, también para listas vacías. Un fallo local que impida leerlos no se comunica como lista vacía exitosa.
- **RF-06:** Consultar el historial propio sin filtros por estado o fechas del turno, incluyendo fechas pasadas y futuras. Mantener ese alcance tanto al reconciliar con cátedra como al devolver el respaldo local.
- **RF-07:** Ordenar el historial por fecha/hora del turno descendentes, con desempate estable; paginarlo con 20 registros por defecto, máximo 100. La paginación del historial no oculta el pendiente separado; totales y contenido corresponden solo al usuario.
- **RF-08:** La consulta preserva cambios concurrentes válidos y cierres conocidos. No crea holds, confirma inicialmente, publica teléfonos, cancela ni extiende plazos; las ausencias o discrepancias no prueban por sí mismas un resultado terminal.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Ejecutar cancelación (CU-08), abandono (CU-10) o nuevos pasos del inicio/teléfono ya especificados. Se conserva la consulta individual de CU-06 para retomar el proceso identificado.
- Recuperación transversal automática de CU-09; aquí sí corresponde reconciliar la evidencia obtenida al consultar. No importar registros centrales sin asociación local comprobada ni listar reservas de toda la cuenta técnica para usuarios comunes.
- Pantallas KMP, búsquedas/agenda de Catálogo, cambios al contrato central y OpenAPI/Swagger, cuya incorporación continúa postergada para consolidar las APIs.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. El usuario abre o actualiza sus reservas y puede solicitar una página del historial.
2. Turnos verifica autenticación y entrada, identifica al propietario y obtiene sus registros propios.
3. Intenta consultar cátedra con la identidad derivada. Comprueba correlaciones y conserva las actualizaciones compatibles y sus datos históricos.
4. Si la consulta falla o es insuficiente, conserva los registros y resultados conocidos, informando el límite de verificación.
5. Devuelve el pendiente separado y el historial propio ordenado/paginado, con estados y actualización distinguibles. Cada registro mantiene su referencia local para consultarlo individualmente.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Una única referencia local identifica cada proceso y su reserva asociada. El listado no entrega JWT ni identificadores técnicos privados, ni acepta un propietario o externalPatientId elegido libremente por KMP.
- Mostrar profesional identificado, fecha/hora de inicio del turno, estado y motivo conocido cuando corresponda; conservar nombre y apellido históricos del profesional cuando se hayan obtenido válidamente, sin incorporar la hora de finalización. No exigir nombres inexistentes a un proceso que aún no los tiene ni sustituirlos por datos inventados. No reutilizar ese historial para iniciar reservas.
- Conservar los estados públicos existentes. Un proceso rechazado, expirado o cuyo intento fue abandonado no se convierte en reserva confirmada. PHONE_PENDING central no sustituye una solicitud real de teléfono ni habilita una acción incompatible con CU-05/CU-06.
- D-01 confirmada: el historial no ofrece filtros por estado, fechas ni otros campos. Sus registros pasados y futuros permanecen consultables mediante la paginación acordada.
- D-02/D-03 confirmadas: fecha descendente, luego hora descendente y desempate estable; historial paginado y pendiente separado, siempre visible. Tamaño fuera de 1 a 100 o página inválida se rechaza; una página válida fuera de resultados devuelve historial vacío.
- Contar y paginar los registros locales propios reconciliados, sin incluir el pendiente separado en el total del historial ni trasladar automáticamente los totales del listado técnico. Una reserva central sin asociación local no se incorpora al contenido ni al total hasta recuperar esa asociación; este CU no reconstruye registros locales faltantes. Una respuesta central paginada o filtrada incompleta no acredita ausencia de los registros restantes ni autoriza borrarlos.
- Mantener información sobre la verificación y su fecha cuando se conozca. Datos locales conservados sin comprobación reciente se informan con esa limitación; una verificación parcial no hace pasar toda la vista por actualizada.
- Conservar resultados concurrentes más recientes, como una cancelación recibida mientras se consulta una confirmación anterior. El mismo proceso no se duplica entre pendiente e historial dentro de una respuesta.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Consulta normal | Reconciliar información propia verificable y devolver pendiente e historial diferenciados. |
| Sin registros propios | Vista vacía; si falta verificación central, conservar el aviso. |
| Entrada inválida | Rechazar sin consultar ni alterar datos de otro usuario. |
| Cátedra inaccesible, sesión técnica no utilizable o espera excesiva | Respuesta local propia con aviso; no descartar el historial ni declarar una verificación nueva. |
| Catálogo no disponible | Mantener la lectura local y usar cátedra si el acceso técnico disponible lo permite; no bloquear la vista por Catálogo. |
| Persistencia local inaccesible | Informar indisponibilidad, sin inventar resultado vacío ni propiedad. |
| Datos centrales ajenos, incompatibles o parciales | No atribuirlos; conservar lo conocido y advertir la incertidumbre relevante. |
| Reserva central identificada como propia, sin asociación local comprobada | Excluirla del listado y sus totales hasta recuperar la asociación; no crear el registro local durante esta consulta. |
| Pendiente ausente de la lista central | Mantenerlo visible; no inferir confirmación, cancelación o expiración por ausencia. |
| Kafka y consultas concurrentes/duplicadas | Una identidad por registro, sin duplicar efectos ni retroceder un cierre verificado. |
| Reinicio | Conservar el historial, las descripciones obtenidas y el pendiente, con sus límites de verificación. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Etapa SPEC: sin código, scaffolding, PLAN/TASKS ni operaciones Git mutables. Solo backend-turnos y continuidad compartida; no modificar los otros repositorios.
- Respetar el contrato central y la propiedad por usuario. Catálogo sigue siendo la única fuente local vigente de profesionales/agendas para búsquedas y nuevas reservas.
- Conservar único proceso pendiente, JWT de usuario, progreso y reglas de reconciliación aprobados. El diseño técnico del listado, su contrato HTTP y los límites de llamadas se concretan en PLAN, sin reintentos ilimitados.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Usuario no autorizado es rechazado; identidades enviadas libremente, registros y conteos ajenos no permiten consultar a otro propietario.
- **CA-02 · RF-02/RF-03:** Con información central y local del mismo proceso se devuelve un solo registro propio; los ajenos/no asociados no se incorporan al contenido ni a los totales, aunque coincida externalPatientId. Un pendiente ausente de cátedra sigue visible y separado del total del historial.
- **CA-03 · RF-03:** La vista diferencia reserva confirmada/cancelada/fallida, proceso anterior y pendiente; aceptación de hold/teléfono o PHONE_PENDING no fabrican confirmación ni solicitud.
- **CA-04 · RF-04:** Tras una consulta válida y un reinicio, la identidad y el nombre conocido del profesional siguen disponibles sin Catálogo. Un nombre ausente permanece reconocible sin datos inventados; no se incorpora la hora de finalización.
- **CA-05 · RF-05:** Al fallar cátedra se devuelven registros locales o vista vacía con aviso, sin verificación nueva. Si falla la lectura local se informa indisponibilidad.
- **CA-06 · RF-06:** El historial permite consultar en sus páginas registros propios de los distintos estados y de fechas pasadas y futuras, sin filtros por estado o fecha; el respaldo local conserva ese mismo alcance.
- **CA-07 · RF-07:** El historial respeta orden/desempate, tamaño 20 por defecto y máximo 100, totales propios y página vacía fuera de resultados; el pendiente separado no queda oculto por la página solicitada.
- **CA-08 · RF-02/RF-05/RF-08:** Respuesta central vacía, parcial o contradictoria conserva los resultados conocidos y señala el límite de verificación; no elimina registros ni inventa cierres.
- **CA-09 · RF-08:** Repetir la consulta o cruzarla con Kafka no crea efectos centrales nuevos, renueva plazos ni revierte un resultado más reciente; cada registro conserva su propietario e identidad.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Usuario anónimo, credencial sin permiso e intento de elegir otro paciente; mezcla de datos centrales. | Rechazo/aislamiento, sin referencias ni conteos ajenos. |
| CA-02 | Mismo proceso en ambas fuentes, central ajeno, reserva central con externalPatientId propio sin asociación local y pendiente solo local. | Registros propios únicos; no asociados fuera del contenido/totales, sin importarlos; pendiente conservado fuera del total del historial. |
| CA-03 | Variar fases finales, hold, espera de teléfono y resultado. | Estados y naturaleza del registro distinguibles, sin éxito inventado. |
| CA-04 | Obtener el nombre del profesional, reiniciar, retirar Catálogo y consultar; incluir un proceso sin nombre. | Nombre histórico durable y lectura posible, con faltantes explícitos y sin hora de finalización. |
| CA-05 | Fallar cátedra con y sin registros locales; fallar después la lectura local. | Respaldo con aviso o indisponibilidad distinguibles. |
| CA-06 | Consultar páginas con registros de distintos estados y fechas pasadas/futuras; repetir sin acceso central. | Historial sin filtros por estado o fecha, con el mismo alcance en el respaldo local. |
| CA-07 | Historial mayor que una página, empates, tamaño omitido/100/inválido y página fuera de rango. | Orden y paginación propios; pendiente separado visible. |
| CA-08 | Omitir un registro central, cortar una respuesta o devolver datos contradictorios. | Ningún borrado/cierre por ausencia ni verificación completa ficticia. |
| CA-09 | Repetir lecturas y recibir cancelación mientras viaja una confirmación anterior por REST. | Sin efectos nuevos ni retrocesos o duplicados. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna. Decisiones confirmadas:

- **D-01 · Confirmada el 2026-11-01:** historial sin filtros. Se mantiene la paginación acordada en D-03.
- **D-02 · Confirmada el 2026-11-01:** historial ordenado por fecha y hora del turno descendentes.
- **D-03 · Confirmada el 2026-11-01:** pendiente separado y siempre visible, historial paginado con 20 registros por defecto y máximo 100.
- **D-04 · Confirmada el 2026-11-01:** conservar nombre y apellido históricos del profesional cuando se obtengan; no incorporar la hora de finalización.
- **D-05 · Confirmada el 2026-11-01:** excluir reservas centrales sin asociación local comprobada hasta recuperar esa asociación, aunque la identificación central corresponda al usuario. No reconstruir registros locales faltantes en este CU.

Nombres HTTP, representación de paginación/verificación y mecanismo de actualización quedan para PLAN; no son decisiones funcionales nuevas de esta etapa.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

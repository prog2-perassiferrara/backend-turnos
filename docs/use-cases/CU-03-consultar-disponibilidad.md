# CU-03. Consultar disponibilidad

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Informar horarios libres para un profesional y una fecha. |
| Actor principal | Usuario final vía KMP. |
| Actores de apoyo | Backend Catálogo y servicio de cátedra. |
| Disparador | El usuario solicita horarios disponibles. |

## Precondiciones

- El usuario está autenticado y autorizado.
- La solicitud identifica el profesional y la fecha.

## Flujo principal

1. KMP solicita la disponibilidad del profesional para la fecha elegida.
2. Turnos obtiene de Catálogo el profesional, sus agendas y su vigencia; valida las condiciones habilitadas necesarias.
3. Turnos consulta las ocupaciones centrales, que incluyen reservas confirmadas y holds activos.
4. Turnos combina las reglas semanales aplicables con las ocupaciones y determina los horarios libres.
5. Turnos devuelve los horarios disponibles; KMP los presenta al usuario.

## Flujos alternativos y de excepción

- **1a. Entrada inválida:** informa el problema; no consulta disponibilidad con datos inválidos.
- **2a. Catálogo indisponible o sin vigencia verificable:** informa que no puede calcular disponibilidad vigente; no sustituye la consulta por otra réplica.
- **2b. Profesional inexistente o deshabilitado:** informa esa condición y termina.
- **2c. Sin agenda habilitada aplicable:** devuelve ausencia de horarios para esa fecha.
- **3a. Ocupaciones no disponibles:** informa indisponibilidad; no interpreta el fallo como ausencia de ocupaciones.
- **4a. Todos los horarios están ocupados:** devuelve un resultado vacío válido.

## Postcondiciones

- **Éxito:** el usuario conoce los horarios libres según los datos obtenidos, o que no hay horarios disponibles.
- **Fallo:** distingue imposibilidad de consulta de un resultado vacío.

## Reglas y relaciones

- Consultar disponibilidad no bloquea horarios. Otra operación puede ocuparlos antes de CU-04.
- La duración de cada turno proviene de la agenda, no de una elección unilateral de Turnos.
- Las horas de atención son locales; los vencimientos son instantes UTC. Se aceptan horas `HH:mm` y `HH:mm:ss`.
- El filtro de disponibilidad de Catálogo solo indica agenda aplicable, no huecos reales.

## Fuentes y pendientes

**Fuentes:** [enunciado, secciones 4.2 y 7](../../../PROJECT_STATEMENT-v1.md); [anexo, secciones 4 y 8](../../../INTEGRATION_REFERENCE-v2.md); [CU-05 de Catálogo](../../../backend-catalogo/docs/use-cases/CU-05-consultar-agenda-vigente.md).

**Pendiente para la SPEC:** límites de fechas y tratamiento de horarios ya pasados, sin inventar restricciones centrales no documentadas.

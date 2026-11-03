# CU-06. Consultar estado del proceso

> Descripción vigente: lectura local. La evolución se conserva en el [historial SDD](../features/README.md).


**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Conocer el progreso de un proceso propio y las acciones que todavía puede realizar el usuario. |
| Actor principal | Usuario final vía KMP. |
| Actores de apoyo | Ninguno en esta lectura; CU-09 reconcilia independientemente. |
| Disparador | El usuario consulta el proceso o vuelve a KMP para retomarlo. |

## Precondiciones

- El usuario está autenticado e identifica el proceso que quiere consultar.

## Flujo principal

1. KMP solicita el estado del proceso.
2. Turnos verifica su existencia y propiedad.
3. Turnos determina el progreso conocido, la información requerida y las operaciones con resultado todavía incierto.
4. Turnos entrega el estado durable local, el vencimiento y las acciones disponibles, sin consulta ni certificación central.
5. KMP presenta el resultado; el propietario puede completar el teléfono si sigue habilitado o cancelar una reserva confirmada mediante CU-08.

## Flujos alternativos y de excepción

- **2a. Proceso inexistente o ajeno:** rechaza la consulta sin exponer datos de otro usuario.
- **3a. Resultado incierto o discrepancia:** informa el progreso conocido sin inventar éxito o fallo; CU-09 lo reconcilia independientemente de esta consulta.
- **3b. Proceso terminado:** informa el resultado conocido; no lo reinicia ni solicita información adicional incompatible.
- **4a. Cátedra o Catálogo no disponibles:** la lectura local no depende de ellos ni agrega una certificación de frescura. Un fallo de MySQL sí informa indisponibilidad.

## Postcondiciones

- **Éxito:** el propietario conoce el progreso y distingue información pendiente, resultado conocido e incertidumbre.
- **Fallo:** no se exponen procesos ajenos.

## Reglas y relaciones

- Reabrir KMP o volver atrás no crea otro hold ni extiende `expiresAt`.
- El vencimiento del plazo impide nuevos envíos; si hay efectos en curso con resultado desconocido, se reconcilian antes de afirmar el resultado central.
- Este caso informa estado; no confirma ni cancela por el mero hecho de consultar.
- El usuario puede tener un único proceso pendiente y consultar procesos anteriores. La consulta y las acciones posteriores identifican el proceso concreto; un resultado de otro proceso no modifica el consultado.

## Fuentes y pendientes

**Fuentes:** [enunciado, secciones 7 y 8](../../../PROJECT_STATEMENT-v1.md); [anexo, sección 18](../../../INTEGRATION_REFERENCE-v2.md); [decisiones registradas](README.md).

**Contrato vigente:** [API propia](../CONTRATOS.md#api-propia-de-turnos) y [FSM](../ARQUITECTURA.md#flujo-y-fsm-actuales). No existe abandono funcional.

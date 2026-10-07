# CU-05. Completar información adicional

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Completar el teléfono requerido para obtener el resultado definitivo de una reserva. |
| Actor iniciador | Servicio de cátedra, que solicita información y comunica resultados. |
| Actor principal | Usuario propietario vía KMP, que proporciona el teléfono. |
| Disparador | Se recibe `AdditionalInformationRequested` para un proceso propio. |

## Precondiciones

- Existe un proceso local iniciado y asociado a su propietario.
- La solicitud corresponde al proceso y no se conoce un estado final que impida continuar.

## Flujo principal

1. Turnos recibe la solicitud, verifica su correspondencia y conserva el evento y su vencimiento.
2. Turnos hace consultable al propietario que se requiere un teléfono; KMP presenta la solicitud.
3. El usuario autenticado envía el teléfono para su proceso.
4. Turnos valida la entrada y verifica que el proceso permita continuar; conserva el envío y publica `AdditionalInformationSubmitted` correlacionado con la solicitud.
5. Cátedra procesa el teléfono y entrega el resultado mediante Kafka.
6. Turnos persiste el resultado y lo hace consultable: ante confirmación, asocia la reserva central con el propietario.

## Flujos alternativos y de excepción

- **1a o 5a. Evento duplicado:** no repite efectos; conserva el resultado ya aplicado.
- **3a. El usuario sale de la pantalla o cierra la app:** conserva el progreso sin solicitar cancelación; puede retomarlo si sigue vigente.
- **4a. Entrada inválida:** informa el problema y permite corregirla.
- **4b. Usuario ajeno al proceso:** rechaza el envío sin publicar información a cátedra.
- **4c. Se conoce vencimiento o resultado final:** impide nuevos envíos incompatibles y comunica el estado.
- **4d o 5b. Interrupción de comunicación o resultado incierto:** conserva el progreso para CU-09; no informa confirmación por el mero envío del teléfono.
- **5c. Teléfono rechazado:** informa el rechazo y permite enviar una corrección antes de `expiresAt`, con un evento nuevo.
- **5d. Proceso vencido o inválido:** registra la condición y evita continuar como si estuviera vigente; reconcilia el estado central cuando corresponda.

## Postcondiciones

- **Éxito:** reserva confirmada, persistida y asociada al propietario.
- **Alternativa:** solicitud corregible, progreso pendiente de verificación o resultado final conocido, sin confundirlos entre sí.

## Reglas y relaciones

- Se conserva el `eventId` de la solicitud como `requestEventId`; los eventos de turno utilizan `reservationProcessId` como message key.
- Cátedra elimina espacios, guiones, paréntesis y puntos del teléfono y valida el resultado con `^\+?[0-9]{7,15}$`.
- La pérdida de una respuesta no autoriza un reenvío indiscriminado ni la creación de otra reserva.
- Los eventos tardíos no reabren procesos finalizados ni revierten una cancelación conocida.

## Fuentes y pendientes

**Fuente:** [anexo, secciones 15.1 y 15.4 a 15.9](../../../INTEGRATION_REFERENCE-v2.md); [decisiones registradas](README.md).

**Pendiente para el PLAN:** transporte del estado a KMP, deduplicación y recuperación de envíos inciertos. No se presupone almacenamiento del teléfono como atributo permanente de la cuenta.

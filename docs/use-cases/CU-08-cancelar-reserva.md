# CU-08. Cancelar reserva propia

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Cancelar una reserva propia confirmada y reflejar el resultado central. |
| Actor principal | Usuario final vía KMP. |
| Actor de apoyo | Servicio de cátedra. |
| Disparador | El usuario solicita explícitamente cancelar una reserva. |

## Precondiciones

- El usuario está autenticado y la reserva pertenece a ese usuario.
- La reserva está confirmada; una repetición puede encontrarla ya cancelada.

## Flujo principal

1. El usuario identifica su reserva y solicita cancelarla.
2. Turnos verifica la propiedad y el estado conocido; conserva la intención de cancelación.
3. Turnos solicita la cancelación central mediante el identificador del proceso.
4. Turnos recibe el resultado REST y/o Kafka y reconcilia ambos sin repetir efectos.
5. Turnos persiste la cancelación y comunica al usuario el resultado verificado.

## Flujos alternativos y de excepción

- **2a. Reserva ajena:** rechaza la operación sin solicitar su cancelación a cátedra.
- **2b o 3a. Reserva ya cancelada:** informa el estado actual sin duplicar la cancelación.
- **3b. Reserva inexistente o estado no cancelable:** informa el rechazo y reconcilia discrepancias si corresponde.
- **3c o 4a. Timeout, respuesta perdida o caída de cátedra:** conserva la operación pendiente de verificación; no muestra una cancelación exitosa sin evidencia. Continúa mediante CU-09.
- **4b. Evento duplicado o resultado REST posterior al evento:** mantiene un único resultado coherente.

## Postcondiciones

- **Éxito:** reserva cancelada centralmente y estado local reconciliado.
- **Fallo o incertidumbre:** rechazo conocido o intención pendiente, sin falso éxito ni efectos sobre reservas ajenas.

## Reglas y relaciones

- La cancelación central de una reserva ya cancelada es idempotente.
- No requiere consultar Catálogo para utilizar los identificadores propios de la reserva.
- La aclaración docente indica que la cancelación libera el horario; otra operación concurrente puede volver a ocuparlo.
- Abandonar un proceso todavía pendiente se describe aparte en CU-10 por su objetivo y precondiciones. El profesor confirmó que se puede utilizar la operación de cancelar reserva para cancelar holds.
- La confirmación termina la creación de la reserva, pero no impide una cancelación posterior autorizada. `CONFIRMED` a `CANCELLED` es una transición válida, no la reapertura del proceso de creación; una confirmación tardía no revierte una cancelación ya verificada.

## Fuentes y pendientes

**Fuentes:** [anexo, secciones 12, 13.1 y 15.10](../../../INTEGRATION_REFERENCE-v2.md); [aclaración docente registrada](README.md).

**Pendiente para la SPEC:** presentación del motivo opcional y de la cancelación pendiente de verificación.

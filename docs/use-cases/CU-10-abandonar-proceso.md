# CU-10. Abandonar proceso propio

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Detener explícitamente un proceso propio pendiente y esperar el vencimiento del hold, preservando resultados inciertos. |
| Actor principal | Usuario final vía KMP. |
| Actor de apoyo | Servicio de cátedra. |
| Disparador | El usuario pulsa el botón de abandonar el proceso. |

## Precondiciones

- El usuario está autenticado y el proceso le pertenece.
- El estado conocido permite tratarlo como pendiente; se dispone del identificador central necesario.

## Flujo principal

1. El usuario solicita explícitamente abandonar su proceso.
2. Turnos comprueba propiedad y estado y conserva la intención de abandono.
3. Turnos detiene nuevos pasos de confirmación y teléfono y conserva el vencimiento central, sin solicitar cancelar el hold.
4. Al vencer el plazo central, cierra una espera estable sin ninguna respuesta telefónica registrada; si hubo una operación incierta, reconcilia su resultado mediante REST/Kafka.
5. Turnos informa intención pendiente, vencimiento o resultado central verificado, sin presentar el abandono local como cancelación central.

## Flujos alternativos y de excepción

- **Antes del paso 1, volver atrás o cerrar KMP:** no dispara este caso. Turnos conserva el proceso para retomarlo si todavía es vigente.
- **2a. Proceso ajeno:** rechaza el abandono sin efectos centrales.
- **2b. Proceso ya cancelado, vencido o fallido:** informa el resultado conocido; no lo reabre.
- **2c o 3a. La reserva se confirmó mientras el usuario esperaba:** reconcilia el estado y continúa con la cancelación de la reserva confirmada descrita en CU-08. La acción de abandono también autoriza esa cancelación; no se exige otra acción del usuario.
- **3b. La consulta central no determina el resultado:** conserva el progreso para reconciliación, sin afirmar liberación por una lista vacía o un rechazo.
- **3c o 4a. Timeout, respuesta perdida o cátedra indisponible:** mantiene la intención y el resultado incierto para recuperación; no acredita cancelación ni cierra una operación incierta por reloj.
- **4b. Vencimiento central verificado:** informa el vencimiento como resultado distinto de una cancelación exitosa.

## Postcondiciones

- **Éxito:** el proceso ya no continúa; su vencimiento o resultado central se conserva. Si llegó a confirmarse, CU-08 cancela la reserva y verifica el resultado.
- **Fallo o incertidumbre:** queda visible que la liberación no se verificó; no se borra el progreso necesario para recuperar.

## Reglas y relaciones

- Conservar el proceso al salir no extiende `expiresAt`.
- Liberar el hold no garantiza que el horario continúe disponible frente a otras reservas.
- No se solicita cancelar un hold: se respeta expiresAt. Solo una reserva confirmada habilita la operación de cancelación central; una ya cancelada conserva su resultado idempotente.
- La cancelación de una reserva confirmada corresponde a CU-08. El límite entre ambos casos debe cubrir la concurrencia con una confirmación tardía.
- El usuario confirmó que su intención de abandono también autoriza cancelar una reserva recién confirmada mientras esperaba. Solicitar la cancelación no equivale a verificar su éxito.
- Si el resultado del abandono sigue incierto, no se permite iniciar otro proceso de reserva para ese usuario hasta verificar el resultado del anterior.

**Ejemplo de concurrencia acordado:**

Este ejemplo explica la decisión de que el abandono también autorice cancelar una reserva recién confirmada. No define todavía la máquina de estados técnica.

| Momento | Estado central conocido | Intención y resultado local |
| --- | --- | --- |
| El teléfono ya fue enviado y KMP espera el resultado | Último estado conocido: `PHONE_PENDING` | El usuario todavía ve un proceso pendiente. |
| Cátedra confirma, pero KMP aún no recibió la actualización | `CONFIRMED` en cátedra | La pantalla puede seguir mostrando espera. |
| El usuario pulsa abandonar | Turnos puede descubrir `CONFIRMED` al reconciliar | Conserva la solicitud de cancelación; no inventa `CANCELLED`. |
| Cátedra acepta la cancelación de la reserva | `CANCELLED` | Turnos informa cancelación verificada. |

Si el resultado de cancelar se pierde, se mantiene la solicitud pendiente de verificación. Si aparece después un evento de confirmación anterior a la cancelación verificada, no se vuelve a mostrar la reserva como confirmada. La intención del usuario y el resultado central se distinguen sin afirmar que existen dos estados centrales simultáneos.

## Fuentes y pendientes

**Fuentes:** [decisiones registradas](README.md); [enunciado, 4.2 y 7](../../../PROJECT_STATEMENT-v1.md); [anexo, secciones 9, 10, 12, 13.1 y 15](../../../INTEGRATION_REFERENCE-v2.md).

**Pendiente funcional de esta decisión:** ninguno.

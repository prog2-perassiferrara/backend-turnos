# CU-10. Abandonar proceso propio

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Abandonar explícitamente un proceso propio pendiente y solicitar la liberación de su hold. |
| Actor principal | Usuario final vía KMP. |
| Actor de apoyo | Servicio de cátedra. |
| Disparador | El usuario pulsa el botón de abandonar el proceso. |

## Precondiciones

- El usuario está autenticado y el proceso le pertenece.
- El estado conocido permite tratarlo como pendiente; se dispone del identificador central necesario.

## Flujo principal

1. El usuario solicita explícitamente abandonar su proceso.
2. Turnos comprueba propiedad y estado y conserva la intención de abandono.
3. Turnos solicita la cancelación central usando el identificador del proceso, conforme a la aclaración docente.
4. Turnos verifica y persiste el resultado sin confundir solicitud enviada con abandono completado.
5. Turnos informa al propietario el abandono verificado y deja de continuar el intercambio de información de ese proceso.

## Flujos alternativos y de excepción

- **Antes del paso 1, volver atrás o cerrar KMP:** no dispara este caso. Turnos conserva el proceso para retomarlo si todavía es vigente.
- **2a. Proceso ajeno:** rechaza el abandono sin efectos centrales.
- **2b. Proceso ya cancelado, vencido o fallido:** informa el resultado conocido; no lo reabre.
- **2c o 3a. La reserva se confirmó mientras el usuario esperaba:** reconcilia el estado y continúa con la cancelación de la reserva confirmada descrita en CU-08. La acción de abandono también autoriza esa cancelación; no se exige otra acción del usuario.
- **3b. La API central devuelve un rechazo:** informa que no se verificó la liberación; no borra el proceso ni lo presenta como abandonado centralmente. Si el rechazo contradice el criterio docente confirmado, conserva la evidencia para revisar la integración.
- **3c o 4a. Timeout, respuesta perdida o cátedra indisponible:** mantiene la intención y el resultado incierto para CU-09; no confirma el abandono.
- **4b. Vencimiento central verificado:** informa el vencimiento como resultado distinto de una cancelación exitosa.

## Postcondiciones

- **Éxito:** cátedra canceló el proceso y liberó el hold; Turnos conserva el resultado y no continúa el proceso.
- **Fallo o incertidumbre:** queda visible que la liberación no se verificó; no se borra el progreso necesario para recuperar.

## Reglas y relaciones

- Conservar el proceso al salir no extiende `expiresAt`.
- Liberar el hold no garantiza que el horario continúe disponible frente a otras reservas.
- Se utiliza la operación de cancelar reserva para cancelar el hold conforme a la aclaración docente que admite ese criterio para el proyecto, pese a la restricción de estados de la matriz escrita.
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

**Fuentes:** [decisiones, conversación de Slack y aclaración de clase registradas](README.md); [anexo, secciones 12 y 13.1](../../../INTEGRATION_REFERENCE-v2.md).

**Pendiente para la validación de integración:** verificar las respuestas y eventos exactos de la cancelación de holds, que las aclaraciones no detallan. La posibilidad de utilizar esta operación está confirmada para el proyecto y no requiere una nueva decisión.

**Pendiente funcional de esta decisión:** ninguno.

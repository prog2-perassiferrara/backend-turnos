# CU-09. Recuperar procesos pendientes

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Recuperar progreso y reconciliar resultados inciertos sin duplicar reservas ni revertir resultados finales. |
| Actor iniciador | No hay un actor externo obligatorio: es un proceso automático de Turnos. |
| Actor de apoyo | Servicio de cátedra. |
| Disparador | Reinicio, restablecimiento de comunicación o detección de una operación interrumpida, incierta o inconsistente. |

## Precondiciones

- Existen registros locales de operaciones que requieren recuperación o verificación.

## Flujo principal

1. Turnos identifica las operaciones pendientes y recupera el progreso, propietario e identificadores conservados.
2. Turnos consulta los estados centrales que el contrato permite obtener y los reconcilia con resultados REST/Kafka registrados.
3. Turnos determina si el resultado está verificado, si puede continuar o si permanece incierto.
4. Para operaciones que pueden continuar de forma segura, Turnos ejecuta los pasos pendientes con reintentos limitados y sin repetir efectos conocidos.
5. Turnos persiste el progreso recuperado y lo hace consultable al propietario mediante CU-06 y CU-07.

## Flujos alternativos y de excepción

- **2a. Cátedra no disponible:** mantiene el progreso y la necesidad de recuperación; no descarta operaciones ni declara éxito.
- **2b. Creación sin IDs recuperables:** aplica el criterio docente de abandonar ese intento y consultar ocupaciones: HELD exige esperar su expiresAt y reconsultar, CONFIRMED impide crear y libre permite un intento acotado sujeto a 409. No atribuye propiedad ni libera por diez minutos locales. Otras operaciones con información insuficiente conservan incertidumbre; una lista vacía no prueba que el efecto anterior nunca ocurrió.
- **2c. Confirmación o cancelación central verificada:** actualiza el estado sin crear otra reserva ni volver a enviar pasos ya completados.
- **2d. REST informa `HOLD_EXPIRED` o Kafka entrega `AppointmentProcessExpired`:** cierra el proceso local y evita nuevas confirmaciones.
- **3a. Plazo vencido con operación en curso de resultado desconocido:** no inicia nuevos pasos; mantiene la necesidad de determinar el resultado central.
- **4a. Duplicados o eventos tardíos:** no repite efectos ni reabre estados finales.

## Postcondiciones

- **Éxito:** estado reconciliado y progreso recuperado, o resultado final verificado.
- **Pendiente:** la operación conserva su propietario, progreso e incertidumbre explícita para una recuperación posterior.

## Reglas y relaciones

- Persistir efectos antes de confirmar offsets Kafka y deduplicar por `eventId`.
- Un timeout no prueba un fallo. Los reintentos son limitados y observables.
- Recuperar el proceso no extiende el vencimiento central ni presupone que el usuario está conectado.
- Un proceso cuyo cierre siga incierto conserva el límite para su usuario; el reinicio no permite eludirlo. La excepción es el intento de creación sin IDs archivado según el criterio docente: otra acción exige consultar ocupaciones y no afirma liberar el hold anterior.
- Las alternativas de los casos afectados remiten a esta recuperación. No se agrega una relación `extend` en el diagrama: este caso también se inicia por reinicios o restablecimiento de comunicación, fuera de la ejecución original.

## Fuentes y pendientes

**Fuentes:** [enunciado, sección 8](../../../PROJECT_STATEMENT-v1.md); [anexo, secciones 15.1 y 18](../../../INTEGRATION_REFERENCE-v2.md); [aclaración sobre creación incierta](../../../dudas_Slack.md#duda-18-resuelta).

**Decisión contractual y pendiente de PLAN:** no hay consulta de holds ni idempotencia central para recuperar IDs perdidos. Se aplica el criterio docente de ocupaciones y nuevos intentos acotados; no se inventa recuperación de propiedad. El PLAN de este CU precisará la recuperación restante de operaciones interrumpidas y confirmaciones conocidas.

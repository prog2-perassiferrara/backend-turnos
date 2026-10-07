# CU-07. Consultar reservas propias

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Consultar reservas propias y procesos pendientes, diferenciando sus estados. |
| Actor principal | Usuario final vía KMP. |
| Actor de apoyo | Servicio de cátedra. |
| Disparador | El usuario abre o actualiza la consulta de sus reservas. |

## Precondiciones

- El usuario está autenticado y autorizado.

## Flujo principal

1. El usuario solicita sus reservas desde KMP.
2. Turnos obtiene las reservas centrales correspondientes a su identidad estable y comprueba la asociación con el usuario final.
3. Turnos reconcilia los datos con sus registros locales conservando propiedad y resultados conocidos.
4. Turnos devuelve reservas confirmadas, canceladas y fallidas, junto con los procesos propios pendientes claramente diferenciados.
5. KMP presenta los resultados y su estado de actualización.

## Flujos alternativos y de excepción

- **2a. Cátedra no disponible:** devuelve los datos locales propios con aviso de posible desactualización; continúa en el paso 5.
- **2b. Se reciben datos ajenos:** no los expone al usuario ni les atribuye su propiedad.
- **3a. Discrepancia o información insuficiente:** conserva la condición de incertidumbre y utiliza CU-09 cuando corresponda; no inventa estados definitivos.
- **4a. Sin registros propios:** devuelve una lista vacía. Si no se pudo verificar cátedra, mantiene el aviso de consulta solo local.

## Postcondiciones

- **Éxito:** el usuario obtiene exclusivamente sus registros, con estados diferenciados y aviso cuando los datos podrían estar desactualizados.
- La consulta no inicia reservas ni las cancela.

## Reglas y relaciones

- El usuario no elige libremente un `externalPatientId` para consultar reservas de terceros.
- Los procesos con hold pueden existir localmente antes de aparecer en el listado central; no se los presenta como reservas confirmadas.
- Los datos históricos de profesional conservados en la reserva no sirven como catálogo vigente para iniciar otra.
- La caída de Catálogo no bloquea esta consulta.
- La vista distingue el único proceso pendiente permitido por usuario de sus reservas y procesos anteriores. Cada registro puede identificarse y consultarse por separado.

## Fuentes y pendientes

**Fuentes:** [enunciado, secciones 4.2 y 9](../../../PROJECT_STATEMENT-v1.md); [anexo, sección 11](../../../INTEGRATION_REFERENCE-v2.md); [decisiones registradas](README.md).

**Pendiente para la SPEC:** filtros, orden y paginación de la vista propia. El orden central predeterminado no constituye por sí solo una elección para KMP.

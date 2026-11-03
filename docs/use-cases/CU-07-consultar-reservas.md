# CU-07. Consultar reservas propias

> Descripción vigente: historial local paginado. La evolución se conserva en el [historial SDD](../features/README.md).


**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Consultar reservas propias y procesos pendientes, diferenciando sus estados. |
| Actor principal | Usuario final vía KMP. |
| Actores de apoyo | Ninguno en esta lectura. |
| Disparador | El usuario abre o actualiza la consulta de sus reservas. |

## Precondiciones

- El usuario está autenticado y autorizado.

## Flujo principal

1. El usuario solicita sus reservas desde KMP.
2. Turnos obtiene el propietario del JWT y lee sus registros durables en una instantánea SQL propia.
3. Turnos separa el proceso pendiente y pagina únicamente el historial, con sus totales y orden cronológico descendente.
4. Turnos devuelve los estados conocidos, sin consultar cátedra ni certificar su frescura.
5. KMP presenta los resultados y permite consultar cada proceso por su UUID.

## Flujos alternativos y de excepción

- **2a. Cátedra o Catálogo no disponibles:** no afectan esta consulta local; no se agrega un aviso de verificación central.
- **2b. Datos ajenos:** no los expone al usuario ni les atribuye su propiedad.
- **2c. MySQL no disponible:** informa indisponibilidad; no la presenta como historial vacío.
- **3a. Página/tamaño inválidos:** rechaza la entrada; no hay filtros ni orden configurable.
- **4a. Sin registros propios:** devuelve contenido vacío y totales coherentes. No importa reservas centrales sin asociación local.

## Postcondiciones

- **Éxito:** el usuario obtiene exclusivamente sus registros locales, con pendiente e historial diferenciados, sin atribuirles verificación central actual.
- La consulta no inicia reservas ni las cancela.

## Reglas y relaciones

- El usuario no elige libremente un `externalPatientId` para consultar reservas de terceros.
- Los procesos con hold pueden existir localmente antes de aparecer en el listado central; no se los presenta como reservas confirmadas.
- Los datos históricos de profesional conservados en la reserva no sirven como catálogo vigente para iniciar otra.
- La caída de Catálogo no bloquea esta consulta.
- La vista distingue el único proceso pendiente permitido por usuario de sus reservas y procesos anteriores. Cada registro puede identificarse y consultarse por separado.

## Fuentes y pendientes

**Fuentes:** [enunciado, secciones 4.2 y 9](../../../PROJECT_STATEMENT-v1.md); [anexo, sección 11](../../../INTEGRATION_REFERENCE-v2.md); [decisiones registradas](README.md).

**Contrato vigente:** [historial propio](../CONTRATOS.md#identificadores-y-respuestas-de-reserva), página desde cero, tamaño 20 por defecto y máximo 100, sin filtros; el pendiente no cuenta en sus totales.

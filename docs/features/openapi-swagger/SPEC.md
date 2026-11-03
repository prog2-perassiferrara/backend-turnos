# SPEC: OpenAPI, Swagger UI y timeout de integración

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-11-03, junto con el PLAN y las cuatro decisiones
de alcance de la sesión.

## Qué construimos y para quién

Un contrato OpenAPI consultable y una Swagger UI para las ocho operaciones HTTP
existentes de Turnos, destinados a quien desarrolla o revisa la API (KMP, cátedra
o el propio equipo). Además, el timeout HTTP de los clientes de integración técnica
se toma de configuración externa, con el mismo valor por defecto actual.

## Situación actual

- Catálogo ya publica contrato y UI con springdoc bajo un interruptor.
- Turnos no expone documentación; sus controladores y DTO no describen contratos.
- `TechnicalIntegrationConfiguration` fija `Duration.ofSeconds(5)` para los clientes
  de Catálogo y cátedra. Los presupuestos de disponibilidad y reservas ya son
  externos y no forman parte de este cambio.

## Dentro del alcance

- **RF-01:** Ofrecer contrato OpenAPI y Swagger UI para `/api/register`,
  `/api/authenticate`, `/api/appointment-availability`, `/api/reservations`,
  `/api/reservation-processes`, `/api/reservation-processes/{processId}`,
  `.../phone` y `.../cancel`.
- **RF-02:** Describir parámetros, DTO, restricciones, estados HTTP y errores
  `application/problem+json` con los códigos reales, incluidos `page` y `size`
  como enteros sin cambiar su firma ni su comportamiento HTTP.
- **RF-03:** Permitir aportar el JWT de usuario desde la UI; cada API conserva sus
  validaciones y permisos, y las operaciones públicas quedan marcadas como anónimas.
- **RF-04:** Deshabilitar documento y UI por defecto y habilitarlos por entorno.
  Habilitados, se consultan sin JWT; el resto no amplía su acceso.
- **RF-05:** No incrustar secretos ni ejecutar negocio al leer la documentación, y
  documentar su habilitación y uso en el README.
- **RF-06:** Externalizar el timeout de conexión/lectura de los clientes de
  integración técnica, con `5s` por defecto y sin parametrizar otros timeouts.

## Fuera de alcance

- Cambios de lógica de negocio, contratos HTTP, firmas Java o reglas de autorización.
- Timeouts de MySQL, Flyway, Hikari, disponibilidad, reservas o Kafka.
- Documentación de endpoints internos, Actuator o integraciones salientes.
- Autenticación desde la UI, emisión de tokens o publicación de la documentación.

## Flujo del caso de uso

1. Un desarrollador habilita `API_DOCS_ENABLED` y reinicia el servicio.
2. Abre Swagger UI, elige una operación y, si es protegida, aporta su JWT de usuario.
3. Ejecuta la operación; el backend aplica permisos, errores y efectos habituales.
4. Al deshabilitar el interruptor, la documentación deja de responderse.

## Datos y reglas de negocio

- La documentación describe el contrato existente; no lo modifica.
- `page` se documenta como entero desde cero y `size` como entero entre 1 y 100.
- El JWT técnico de cátedra no autentica las APIs de usuario.
- Los ejemplos son ficticios y no contienen credenciales.

## Comportamiento del backend y casos alternativos

- Sin habilitación, las rutas de documentación no se sirven y responden como el
  resto de rutas no autorizadas.
- Con habilitación, solo los GET de documentación son anónimos; otros métodos o
  rutas similares siguen cerrados.
- Un valor inválido de `page` o `size` conserva el 400 `INVALID_RESERVATION_PAGE`.
- El timeout externo se aplica a los clientes de Catálogo y cátedra; un valor
  inválido impide el arranque con un diagnóstico sin secretos.

## Restricciones del pedido

- Replicar el enfoque ya usado en Catálogo, sin agregar abstracciones ni
  configuraciones adicionales.
- Mantener OpenAPI deshabilitado por defecto y la seguridad actual.
- No modificar el otro backend.

## Criterios de aceptación

- **CA-01 · RF-01, RF-02:** El contrato contiene exactamente las ocho operaciones,
  con parámetros, DTO, estados y errores reales.
- **CA-02 · RF-03:** La UI permite aportar bearer y las operaciones protegidas
  conservan sus permisos y rechazos.
- **CA-03 · RF-04:** Deshabilitado no se sirve documentación, ni siquiera con un
  usuario autenticado; habilitado se consulta sin JWT sin abrir otras rutas.
- **CA-04 · RF-05:** Documento, ejemplos y UI no incrustan credenciales; leer la
  documentación no invoca casos de uso.
- **CA-05 · RF-02:** `page` y `size` figuran como enteros con sus límites, sin
  alterar firmas ni respuestas HTTP.
- **CA-06 · RF-05:** El README permite habilitar, localizar y usar la documentación.
- **CA-07 · RF-06:** Los clientes de integración usan el timeout configurado y el
  valor por defecto sigue siendo `5s`.

## Cómo se comprueba el comportamiento

Pruebas HTTP con el generador, los controladores, los mappers y la seguridad reales,
observando los puertos de negocio. Revisión del documento generado y de la UI,
arranque con el interruptor apagado y encendido, y `./mvnw -B -ntp clean verify`.

## Decisiones pendientes

Ninguna.

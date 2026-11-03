# PLAN: OpenAPI, Swagger UI y timeout de integración

**SPEC de referencia:** [SPEC.md](SPEC.md)
**Versión de la spec revisada:** aprobada el 2026-11-03.
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

**Aprobación del usuario:** 2026-11-03.

## Contexto técnico verificado

- Turnos usa Spring Boot 4.1.1, Java 25 y springdoc aún no está declarado.
- Catálogo resuelve el mismo problema con `springdoc-openapi-starter-webmvc-ui`
  3.2.0, un bean `OpenAPI` condicional y apertura de GET documentales en seguridad.
- `SecurityConfiguration` exige `ROLE_USER` para `/api/**` y publica solo
  `/api/register` y `/api/authenticate`; con seguridad global, esas dos operaciones
  necesitan `@SecurityRequirements` para figurar como anónimas.
- `TechnicalIntegrationConfiguration` construye dos `SafeIntegrationHttpClient` con
  `Duration.ofSeconds(5)` fijo.

## Solución propuesta

1. Declarar el starter de springdoc con la misma versión que Catálogo.
2. Agregar `infrastructure/config/OpenApiConfiguration`, condicionado a
   `app.api-docs.enabled`, con metadatos, esquema `ApiProblem`, `bearerAuth`,
   respuestas 401/403 y tags *Cuenta*, *Disponibilidad* y *Reservas propias*.
3. Abrir condicionalmente solo los GET de `/v3/api-docs*`, `/swagger-ui.html` y
   `/swagger-ui/**`, sin tocar el resto de reglas.
4. Anotar los cuatro controladores y los DTO seleccionados; documentar `page` y
   `size` como enteros mediante `@Parameter`, conservando `String` y su parseo.
5. Externalizar el timeout como `app.integration.http-timeout`
   (`TECHNICAL_INTEGRATION_HTTP_TIMEOUT`, `5s`) e inyectarlo por constructor de bean.
6. Actualizar `.env.example`, `compose.yaml` y README con el interruptor y el timeout.

## Módulos y componentes afectados

- `infrastructure/config`: metadatos OpenAPI nuevos.
- `infrastructure/security/config`: apertura documental condicional.
- `user`, `availability` y `reservation` `infrastructure/web`: anotaciones de
  operaciones, DTO y parámetros.
- `integration/infrastructure/config`: timeout externo.
- `src/main/resources/application.yaml`, `.env.example`, `compose.yaml`, README.

No se agregan puertos, casos de uso, entidades ni migraciones.

## Datos y contratos

- No cambia ningún contrato HTTP existente.
- `AuthenticationResponse` conserva el nombre `id_token`.
- `ReservationProcessResponse.cancellation` sigue siendo `CancellationView`.
- Los códigos de error documentados son los emitidos por los handlers y filtros
  actuales (`AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `INVALID_CREDENTIALS`,
  `INVALID_RESERVATION_PAGE`, etc.).

## Estado, operaciones y errores

- El documento global declara `bearerAuth`; `/api/register` y `/api/authenticate`
  lo anulan con `@SecurityRequirements`.
- `page`/`size` se describen como enteros con mínimo y máximo, pero el adaptador
  mantiene `String` y responde 400 `INVALID_RESERVATION_PAGE` ante valores inválidos.
- Swagger UI no persiste la autorización y usa `/v3/api-docs` como URL por defecto.

## Dependencias y configuración

- `springdoc-openapi-starter-webmvc-ui` 3.2.0, ya validado con Boot 4.1.1 en Catálogo.
- `app.api-docs.enabled` gobierna el bean y los tres ajustes de springdoc.
- `app.integration.http-timeout` acepta el formato `Duration` de Spring (`5s`).

## Estrategia de validación

- `OpenApiHttpTests`: documento con las ocho operaciones, bearer global y operaciones
  públicas anónimas, esquema de error, `page`/`size` enteros, UI/YAML anónimos sin
  invocar puertos, permisos conservados, sin secretos.
- `OpenApiDisabledHttpTests`: documentación no servida por defecto, ni con usuario;
  el YAML mantiene `false` y una sola variable enciende los tres ajustes.
- `./mvnw -B -ntp clean verify` para la regresión completa.

## Orden de implementación

1. Dependencia, configuración y seguridad.
2. Contratos y esquemas.
3. Pruebas HTTP.
4. Timeout externo y documentación.
5. Regresión y cierre.

## Riesgos y decisiones pendientes

- Springdoc es una dependencia nueva; se acota al starter MVC ya probado.
- El documento podría exponer rutas no propias: `paths-to-match: /api/**` y las
  pruebas de rutas similares lo controlan.
- Ninguna decisión funcional pendiente.

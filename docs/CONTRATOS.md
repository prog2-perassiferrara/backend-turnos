# Contratos vigentes

Las rutas son relativas al backend indicado. Ejemplos e identificadores son ficticios;
reemplazar profesional, fecha y horario por disponibilidad vigente. Fechas de atención
son locales; `expiresAt` y marcas de eventos son instantes UTC. Se aceptan horas
`HH:mm` y `HH:mm:ss`; las respuestas propias usan `HH:mm:ss`.

## Seguridad y propiedad

| Credencial | Emisión y validación | Uso |
| --- | --- | --- |
| Usuario final | Turnos, RS256; `iss=backend-turnos`, `sub=<id>`, `aud=[catalog,turnos]`, `token_use=user`, `auth=ROLE_USER`. | APIs propias de ambos backends; 24 h o 30 días con `rememberMe`. |
| Servicio Turnos | Turnos, RS256; `iss=sub=backend-turnos`, `aud=catalog`, `token_use=service`, `auth=ROLE_CATALOG_INTEGRATION`; 5 min. Catálogo valida su pública externa. | Dos GET internos de Catálogo. No autoriza como usuario. |
| Técnico central | Login de la cuenta técnica administrado por Catálogo; Turnos recibe su JWT por el contrato protegido. Vigencia contractual de un año. | REST y configuración de cátedra. Nunca se entrega al cliente final. |

La privada PKCS#8 RSA externa de Turnos firma sus dos perfiles propios. El JWT
de usuario se valida localmente con firma, emisor, audiencia, perfil, autoridades y
tiempos; tolerancia de 30 s. No hay refresh ni revocación por petición. Una cuenta
inactiva no puede iniciar sesión. HTTPS es necesario fuera del entorno local controlado.

Todas las operaciones propias salvo registro/login/health exigen bearer de usuario
y `ROLE_USER`. Turnos deriva propietario y paciente del principal, nunca del body.
Un proceso ajeno o inexistente devuelve el mismo 404. No expone JWT técnico, teléfono
conservado ni IDs centrales en respuestas de reservas. CORS está cerrado por defecto;
solo habilita orígenes HTTP(S) explícitos, sin cookies de autenticación.

## API propia de Turnos

| Método y ruta | Entrada | Respuesta |
| --- | --- | --- |
| `GET /actuator/health` | Sin bearer. | `200 {"status":"UP"}`; solo salud técnica. |
| `POST /api/register` | `login`, `password`, `firstName`, `lastName`, `email`; `imageUrl`, `langKey` opcionales. | `201` vacío, cuenta activa. No devuelve JWT. |
| `POST /api/authenticate` | `username` (login o email), `password`, `rememberMe` opcional, falso por defecto. | `200 {"id_token":"..."}` y `Authorization: Bearer ...`. |
| `GET /api/appointment-availability` | `professionalId` positivo, `date=yyyy-MM-dd`, desde hoy. | Profesional, fecha, zona y `availableStartTimes`, ordenados sin duplicados. |
| `POST /api/reservation-processes` | UUID `requestId`, `professionalId`, `date`, `startTime`. | `202` nueva intención; `200` repetición equivalente propia. |
| `GET /api/reservation-processes/{processId}` | UUID local. | `200` estado durable propio, sin consultar cátedra. |
| `POST /api/reservation-processes/{processId}/phone` | UUID `requestId`, `phoneNumber`. | `202` tras aceptación SQL; `200` repetición equivalente. |
| `POST /api/reservation-processes/{processId}/cancel` | Sin body. Propietario; reserva confirmada, intención iniciada o ya cancelada. | `202` cancelación pendiente; `200` cierre o retry requerido. |
| `GET /api/reservations` | `page=0`, `size=20`; tamaño 1 a 100. | `pending` separado de `history`, con contenido y totales locales. |

No hay endpoint de abandono, SSE, administración de cuentas ni `/api/account`.
El registro y posterior authenticate son dos llamadas: el cliente coordina el login
automático. Si la segunda falla, la cuenta permanece activa. Un duplicado de registro
no autentica ni demuestra que la solicitud anterior fuera del mismo cliente.

Login admite 1 a 50 caracteres del patrón JHipster; nombre y apellido no blancos
de 2 a 50; email válido de 5 a 254; imagen opcional hasta 256; idioma de 2 a 10,
`es` por defecto. Login/email se normalizan para unicidad sin mayúsculas, incluidos
cruces entre cuentas; pueden coincidir dentro de la propia. Activación, ID, roles y
auditoría los decide el backend, ignorando campos internos enviados por el cliente.
Contraseña de 6 a 100 puntos de código Unicode, sin normalizar/truncar: Argon2id v19,
19 MiB, dos iteraciones, paralelismo 1, sal de 16 bytes y salida de 32 bytes.

Consultar disponibilidad no ocupa un slot. Se excluyen horarios ya iniciados y turnos
incompletos al cierre, reservas confirmadas y holds activos. No hay horizonte máximo
futuro adicional. Sin agenda aplicable devuelve lista vacía; un proveedor fallido no
se interpreta como ausencia de ocupaciones.

### Identificadores y respuestas de reserva

```json
{
  "requestId": "b7273764-9bc3-43fd-b42c-8a9ca65c5639",
  "professionalId": 15,
  "date": "2026-11-10",
  "startTime": "09:00"
}
```

El `requestId` inicial es el UUID local `processId`; conservarlo para reintentar la
misma acción. Otra selección/usuario con esa clave produce conflicto. Otra clave
mientras el usuario tiene un pendiente tampoco crea un segundo proceso. El envío
telefónico usa un UUID propio, que será el `eventId` de su outbox:

```json
{"requestId":"b726f4c6-fcd9-459b-aa41-6157e678cd18","phoneNumber":"+54 (9261) 555-12.34"}
```

Solo se eliminan espacios ASCII, guiones, paréntesis y puntos. Debe resultar un `+`
opcional y 7 a 15 dígitos ASCII, sin inventar prefijos ni quitar ceros iniciales.
Una corrección tras rechazo usa otro UUID; repetir un UUID exige el mismo contenido normalizado.

Las operaciones sobre un proceso devuelven `processId`, `professionalId`, `date`,
`startTime`, `state`, `expiresAt`, `reasonCode`, `phoneRequestId`, `requiredField`,
`canSubmitPhone`, `warningCode`, nombres históricos opcionales y `cancellation`.
Al iniciar una intención nueva, se conserva el nombre completo si está disponible
en la misma lectura vigente de Catálogo utilizada para validar la disponibilidad.
Si falta alguna parte, el nombre sigue siendo opcional; no se modifican reservas anteriores.
Los campos no aplicables pueden ser null. `phoneRequestId` identifica el último envío
local, no el evento central; solo `canSubmitPhone=true` habilita otro envío ahora.
`cancellation` es null o un objeto con `status` (`PENDING`/`RETRY_REQUIRED`) y el booleano `canRetry`.
Solo `state=CANCELLED` acredita cancelación. El retry explícito usa el mismo POST;
no hay motivo ni versión de ciclo que deba conservar el cliente.

El historial es `{content,page,size,totalElements,totalPages}`, ordenado por fecha y
hora del turno descendentes y UUID ascendente para desempatar. El pendiente no cuenta
en sus totales; ambas lecturas usan la misma instantánea SQL. No hay filtros ni orden
configurable. No certifica frescura central ni importa reservas sin asociación local.

### Errores

Las respuestas de usuario/reservas usan `Cache-Control: no-store`. Errores con
`application/problem+json`, `status`, `title`, `code`, `instance`; leer HTTP/`code`,
no `detail`. Validación de usuario añade nombres/códigos de campos, nunca valores.
El problema de inicio puede incluir `processId` propio o `recheckAt` de una ocupación
HELD observada, sin acreditar que ese hold pertenezca al usuario.

| HTTP | Casos y códigos principales |
| --- | --- |
| 400 | `VALIDATION_ERROR`, `USER_IDENTIFIER_ALREADY_USED`, `INVALID_AVAILABILITY_QUERY`, `PROFESSIONAL_DISABLED`, `INVALID_RESERVATION_SELECTION`, `PROFILE_NOT_RESERVABLE`, `INVALID_PHONE_NUMBER`, `INVALID_RESERVATION_PAGE`. |
| 401 | `INVALID_CREDENTIALS` en login; `AUTHENTICATION_REQUIRED` en filtros, con `WWW-Authenticate: Bearer`. Un bearer inválido explícito también se rechaza en rutas públicas. |
| 403 | `ACCESS_DENIED`, identidad válida sin permisos. |
| 404 | `PROFESSIONAL_NOT_FOUND` o `RESERVATION_PROCESS_NOT_FOUND` (también ajeno). |
| 409 | `ACTIVE_RESERVATION_PROCESS`, `REQUEST_ID_CONFLICT`, `SLOT_NOT_AVAILABLE`, `HOLD_EXPIRED`, `CONFIRMATION_REJECTED`, `PHONE_NOT_REQUESTED`, `PHONE_SUBMISSION_ACTIVE`, `PHONE_WINDOW_CLOSED`, `CANCELLATION_NOT_ALLOWED`. |
| 503 | `USER_REGISTRATION_UNAVAILABLE`, `AUTHENTICATION_UNAVAILABLE`, `CATALOG_NOT_CURRENT`, `CATALOG_UNAVAILABLE`, `OCCUPANCIES_UNAVAILABLE`, `TECHNICAL_INTEGRATION_UNAVAILABLE`, `MESSAGING_NOT_READY`, `STORAGE_UNAVAILABLE` o resultado central incierto. No afirma rollback. |
| 500 | `INTERNAL_ERROR`, respuesta genérica sin secretos. |

Un aviso de cancelación no sustituye el estado funcional conocido. Un 401 central
invalida el acceso técnico usado; no se presenta como credencial incorrecta del usuario.

## Contrato protegido con Catálogo

Los dos GET usan exclusivamente el JWT de **servicio**, nunca el JWT central ni la
sesión del usuario. Catálogo comprueba firma, vigencia, perfil, audiencia y permiso.
No se accede a su base ni a clases internas.

| Operación en Catálogo | DTO consumido por Turnos | Errores relevantes |
| --- | --- | --- |
| `GET /api/internal/catedra/token` | `{ "idToken": "..." }`, JWT central de la cuenta técnica compartida. | 401/403 rechazan el acceso; 503 representa indisponibilidad; token ausente/incompatible se rechaza. |
| `GET /api/internal/catalog/professionals/{id}` | `professional:{id,enabled}`, `weeklySchedules:[{professionalId,dayOfWeek,startTime,endTime,slotDurationMinutes,enabled}]`, `catalog:{state,copyAvailable,appliedVersion,observedVersion,lastCheckedAt,evaluatedAt}`. Campos adicionales compatibles se ignoran. | 404 `PROFESSIONAL_NOT_FOUND`; 503 `CATALOG_NOT_CURRENT`; otros fallos/respuestas inválidas se traducen a `CATALOG_UNAVAILABLE`. |

La lectura de agenda debe ser `CURRENT`, copia disponible, versiones iguales no
negativas y marcas coherentes, todo en la misma respuesta. Los datos descriptivos
adicionales no forman una réplica en Turnos. La credencial de servicio se firma nueva
por consulta. El acceso técnico central se adquiere al arranque, se conserva en memoria
y puede readquirirse bajo demanda; ver [ciclo de vida y limitaciones](ARQUITECTURA.md#recovery-cancelación-y-límites-operativos).

## REST y Kafka con cátedra

REST saliente usa el JWT técnico. Turnos obtiene configuración de
`GET /api/student/integration`; exige grupo esperado, `PROVISIONED` y parámetros
Kafka completos. Usa ocupaciones `GET /api/appointment-occupancies` por profesional
y `from=to=fecha`; crea por `POST /api/appointment-holds`; confirma por
`POST /api/appointment-holds/{holdId}/confirm`; reconcilia por `GET /api/appointments`
filtrado por `externalPatientId`, paginado **sin fechas**, y cancela por
`POST /api/appointments/{reservationProcessId}/cancel`. Los dos POST iniciales llevan
el mismo `externalPatientId=user-<id>` y nombres de la cuenta. Un 202 inicial espera
Kafka y no acredita reserva confirmada. No se accede al namespace privado de holds.

Kafka v1 admite campos opcionales adicionales. Los destinos y consumer group se
toman de la configuración asignada, sin autocrear topics productivos. Message key
siempre `reservationProcessId`; `eventId` es la idempotencia funcional.

| Dirección | Evento | Efecto |
| --- | --- | --- |
| Cátedra → Turnos, topic de acciones asignado | `AdditionalInformationRequested` | Conserva solicitud y plazo; permite teléfono. |
| Turnos → cátedra, topic telefónico asignado | `AdditionalInformationSubmitted` | Publicación de outbox; conserva ID y contenido en republicaciones. |
| Cátedra → Turnos | `AdditionalInformationRejected` | Permite corregir antes del plazo, no es fallo terminal. |
| Cátedra → Turnos | `AppointmentConfirmed` | Correlaciona reserva y confirma. |
| Cátedra → Turnos | `AppointmentProcessExpired` | Cierra por expiración acreditada. |
| Cátedra → Turnos | `AppointmentProcessInvalid` | Detiene progreso incompatible y requiere reconciliación. |
| Cátedra → Turnos | `AppointmentCancelled` | Acredita cancelación, sin reabrirla por confirmación tardía. |

El sobre usa `eventId`, `eventType`, `schemaVersion=1`, `reservationProcessId`,
`holdId` y `occurredAt`. La solicitud exige `requiredField=PHONE_NUMBER` y `expiresAt`.
La respuesta enviada añade `groupId`, `requestEventId` igual al `eventId` solicitado
e `information:{phoneNumber}`. Los resultados se validan con grupo, solicitud,
reserva y selección cuando corresponden. El grupo identifica la integración técnica,
no al propietario final. Idempotencia, orden, commit y recuperación están definidos
en [arquitectura vigente](ARQUITECTURA.md#atomicidad-concurrencia-e-idempotencia).

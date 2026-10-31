<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-59%2F100-yellow) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-OK-green) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-0.0%25-green) ![%POO](https://img.shields.io/badge/%25POO-81%25-green) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-6-red) ![D2](https://img.shields.io/badge/D2-100-green) ![D4](https://img.shields.io/badge/D4-20-red) ![D5](https://img.shields.io/badge/D5-0-red) ![D6](https://img.shields.io/badge/D6-100-green) ![D7](https://img.shields.io/badge/D7-100-green)

**Calidad (heurístico, 7 dimensiones):** índice **59/100** · build **OK** · aprobar **no (6/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# Servicio de turnos y reservas

Base técnica con Java 25, Spring Boot 4.1.1, Maven Wrapper 3.10.0,
MySQL 8.4.8, JPA/Hibernate y Flyway. Incluye acceso técnico a cátedra mediante
el contrato protegido de Catálogo, registro/login de usuarios, protección JWT transversal,
consulta de disponibilidad, inicio de reservas con hold, health y pruebas de integración.

## Requisitos

- Docker Engine disponible y Docker Compose.
- JDK 25 para ejecutar Java localmente o usar el wrapper en la máquina.
- Acceso a Maven Central y Docker Hub en la primera ejecución.
- No hace falta instalar Maven: el wrapper descarga la versión 3.10.0.
- `.mvn/maven.config` evita incorporar repositorios declarados por dependencias
  transitivas. Los artefactos utilizados se resuelven desde Maven Central, sin
  requerir acceso a los repositorios privados declarados por Flyway.
- En Windows, utilizar `mvnw.cmd`; ese entorno no fue validado aquí.

## Configuración local

```bash
cp .env.example .env
```

Completar las contraseñas ficticias de desarrollo, URLs, integración asignada y ruta
de la privada externa. Spring Boot no registra cuentas técnicas: debe existir una
cuenta registrada manualmente, cuya sesión de cátedra administra Catálogo.

| Variable | Uso |
| --- | --- |
| `DB_NAME` | Base exclusiva de Turnos; ejemplo `appointment`. |
| `DB_USER` | Usuario limitado a esa base, distinto de root. |
| `DB_PASSWORD` | Contraseña obligatoria del usuario de aplicación. |
| `DB_HOST` | `localhost` para Java local; Compose conecta internamente a `mysql`. |
| `DB_PORT` | Puerto local de MySQL; por defecto 3307. |
| `APP_PORT` | Puerto local del backend; por defecto 8081. |
| `MYSQL_ROOT_PASSWORD` | Solo inicializa MySQL, no se entrega al backend. |
| `CATALOG_BASE_URL` | URL de Catálogo para Java local, por defecto `http://localhost:8080`. Compose fija `http://catalog:8080`. |
| `CATEDRA_BASE_URL` | URL REST central suministrada por cátedra, sin usuario/contraseña en la URL. |
| `CATEDRA_GROUP_ID` | Identificador normalizado de la integración esperada; no es un paciente. |
| `JWT_PRIVATE_KEY_PATH` | Archivo externo PKCS#8 de privada RSA, al menos 2048 bits. En Compose es la ruta del host que se monta dentro del contenedor. |
| `CORS_ALLOWED_ORIGINS` | Opcional, orígenes web HTTP(S) separados por comas. Vacío por defecto; no admite comodines. Android nativo y llamadas entre backends no requieren un origen web. |
| `AVAILABILITY_TIME_ZONE` | Opcional, zona de atención; por defecto `America/Argentina/Buenos_Aires`, independiente de la del dispositivo/contenedor. |
| `AVAILABILITY_HTTP_TIMEOUT` | Opcional, conexión/lectura de las consultas de disponibilidad, por defecto `5s`; total por proveedor de `10s` con ese valor. |
| `RESERVATION_HTTP_TIMEOUT` | Opcional, conexión/lectura de los POST y las consultas de ocupaciones/reservas, `5s`; total por llamada de `10s`. Sin replay del transporte. |
| `RESERVATION_METADATA_TIMEOUT` | Opcional, espera de metadata Kafka, `5s`. |
| `RESERVATION_ATTEMPTS` | Opcional, presupuesto de preparación/recepción, `3` intentos en total; admite 1 a 10. También limita creaciones tras consulta de ocupaciones libre; no reintenta confirmaciones. |
| `RESERVATION_RETRY_DELAY` | Opcional, pausa entre intentos Kafka, `5s`. |
| `RESERVATION_PHONE_PUBLISH_TIMEOUT` | Opcional, metadata más bloqueo de envío, y espera de ACK: `5s` por presupuesto, hasta `10s` en total. |
| `RESERVATION_RESULT_VERIFICATION_DELAY` | Opcional, espera desde el ACK sin resultado antes de verificar por REST al consultar, `30s`. |
| `RESERVATION_PREPARATION_COOLDOWN` | Opcional, espera mínima tras agotar preparación, `30s`; un nuevo inicio/reintento puede solicitar otro ciclo. |

Los puertos por defecto difieren de Catálogo (8080/3306) para ejecutar ambos juntos.
Si están ocupados, cambiarlos en `.env`. Los contenedores de Turnos utilizan su propia
base, red y volumen; no comparten almacenamiento con Catálogo.

`.env`, archivos PEM/key y `secrets/` están ignorados y excluidos del contexto Docker.
No guardar claves dentro del repositorio. Turnos no recibe la contraseña de login
central: usa la sesión del proveedor protegido. Compose interpola `.env`; Spring
Boot no lo carga automáticamente.

## Preparar claves y conectividad

Utilizar un par RSA propio del entorno. La privada permanece en Turnos y la pública
correspondiente se configura en Catálogo según su contrato. Para desarrollo, generar
ambas fuera del repositorio, reemplazando las rutas por un directorio privado propio:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /ruta/privada/private.pem
openssl pkey -in /ruta/privada/private.pem -pubout -out /ruta/privada/public.pem
chmod 600 /ruta/privada/private.pem
```

No pegar el contenido de las claves ni tokens en chat, logs, documentación o Git.
La rotación es manual y debe coordinarse con la pública del proveedor. No se generan
claves ni se llama al login central al arrancar Turnos.

Compose ejecuta Java como UID 10001. El archivo privado debe ser legible por ese UID
sin conceder escritura; en Linux se puede otorgar lectura específica con una ACL
(`setfacl -m u:10001:r /ruta/privada/private.pem`). El montaje es de solo lectura,
no crea archivos ausentes y usa etiqueta SELinux privada para el contenedor.

Desde Java local, configurar `CATALOG_BASE_URL=http://localhost:8080` si Catálogo
publica ese puerto en el host. Para ambos backends en Compose, Turnos recibe
automáticamente `http://catalog:8080`: Docker resuelve `catalog` en la red compartida.
El valor de `.env` destinado a Java local no reemplaza esa URL interna de Compose.
Dentro de un contenedor, `localhost` apunta a ese mismo contenedor.
Usar HTTPS fuera del entorno de desarrollo controlado; nunca desactivar validación TLS.

## Ejecutar todo con Compose

Una vez por máquina, crear la red compartida. Si ya existe, omitir este paso:

```bash
docker network create prog2-services
```

Los dos Compose declaran `prog2-services` como externa: cada `up` conecta
automáticamente el backend y cada `down` conserva la red. Cada MySQL permanece
solo en la red propia de su proyecto, con sus volúmenes y credenciales actuales.
No hace falta un Compose conjunto ni conectar contenedores manualmente.

Levantar primero Catálogo desde su repositorio y comprobar que dispone de acceso
central. Después, desde este repositorio:

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8081/actuator/health
```

Al aplicar por primera vez esta configuración de redes, `up` recrea los backends
cuando detecta el cambio. `restart` por sí solo no aplica cambios de configuración.
Si se agota el ciclo inicial antes de que Catálogo disponga de sesión, las siguientes
recuperaciones siguen siendo a demanda; con Catálogo listo se puede repetir el
arranque de Turnos mediante `docker compose restart appointment`.

Usar el puerto configurado en `APP_PORT` si se modificó. Con base accesible,
health devuelve HTTP 200 y únicamente `{"status":"UP"}`. Es salud técnica:
no afirma que Catálogo esté vigente ni que ya se puedan reservar turnos.
No se exponen otros endpoints de Actuator ni detalles internos.

```bash
docker compose logs appointment
docker compose down
```

El volumen `appointment-mysql-data` conserva datos al detener o recrear contenedores.
Eliminarlo borra esos datos. Cambiar contraseñas en `.env` no modifica las cuentas
de una base ya inicializada; deben actualizarse en MySQL.

Flyway usa una conexión propia con espera de lectura de hasta 60 segundos por
operación; las consultas de la aplicación conservan 10 segundos y ambas conexiones
mantienen 5 segundos para conectar, sin reintentos de conexión de Flyway. Un timeout
durante DDL de MySQL puede dejar tablas creadas sin una migración confirmada:
inspeccionar el esquema y el historial antes de reintentar. No borrar datos ni
marcar la migración como aplicada automáticamente.

## Ejecutar desde el IDE o con Maven

```bash
docker compose up -d mysql
```

Proporcionar al IDE las variables de la tabla, excepto `MYSQL_ROOT_PASSWORD`,
y ejecutar `com.prog2.appointment.AppointmentApplication` con JDK 25. Usar una
`CATALOG_BASE_URL` alcanzable desde el host y la ruta real de la privada externa.
No pasar `MYSQL_ROOT_PASSWORD` a Java. Si el servicio `appointment` de Compose
está activo, detenerlo antes o elegir otro puerto para el proceso local.

En una terminal POSIX, cargar el archivo local propio:

```bash
set -a
. ./.env
set +a
unset MYSQL_ROOT_PASSWORD
./mvnw spring-boot:run
```

El backend rechaza el arranque con configuración obligatoria ausente o en blanco,
configuración inválida o base inaccesible. Los diagnósticos identifican el problema
sin imprimir contraseñas.

## Acceso técnico y recuperación

Al arrancar, Turnos firma un JWT propio de servicio RS256, de cinco minutos,
para `GET /api/internal/catedra/token` de Catálogo. El proveedor devuelve el JWT
central existente como `{ "idToken": "..." }`; Turnos consulta con él
`GET /api/student/integration` de cátedra. Son identidades distintas: el JWT de
servicio autentica a Turnos ante Catálogo; el central identifica la cuenta técnica
ante cátedra. Ambos permanecen fuera de las respuestas de KMP.

Solo se publica una sesión completa en memoria con integración esperada,
`PROVISIONED`, JWT utilizable y parámetros Kafka de Turnos. No se exige Redis ni
el topic de catálogo. Recibir esa configuración no conecta consumidores/productores
Kafka, no confirma vigencia del catálogo y no permite todavía reservar turnos.

La recuperación usa un solo hilo dedicado: tres intentos totales, 5 s entre fallos
recuperables, límites HTTP de 5 s para conexión/lectura y 10 s totales por petición.
Ante fallo terminal se detiene el ciclo. No se registran cuerpos remotos ni causas
sensibles; los logs muestran resultado/motivo fijo del intento.

La caída remota no impide mantener el proceso activo si la configuración local
y MySQL son válidos. Una consulta interna sin acceso solicita recuperación en
segundo plano e informa indisponibilidad inmediatamente. Las demandas concurrentes
comparten el ciclo; después de un ciclo fallido hay 30 s mínimos antes de admitir
otro. Cumplir esa espera no dispara un ciclo: debe llegar una nueva demanda.

Una sesión central utilizable se conserva si Catálogo cae, para operaciones que
no requieren información nueva del catálogo. Nuevas reservas seguirán exigiendo
información vigente. Al vencer el JWT central o recibir un 401 central se invalida
el token afectado; un rechazo tardío no borra una sesión más nueva. Un token rechazado
no se vuelve a publicar por obtenerlo otra vez sin cambios del proveedor.

La recuperación no llama a login central, no renueva su JWT por sí misma ni repite
reservas/cancelaciones de resultado incierto. Si Catálogo no tiene sesión utilizable,
su recuperación sigue su propio contrato. El cierre de Turnos cancela la tarea,
descarta la sesión y bloquea publicaciones tardías.

El puerto interno `TechnicalAccessUseCase` permite consultar acceso y comunicar
rechazos. No se agrega en #4 un endpoint HTTP de diagnóstico, login ni reservas.
El health sigue siendo técnico y no debe usarse para afirmar disponibilidad funcional.

## Registro y autenticación de usuarios

Turnos es propietario de las cuentas de KMP; no las registra en cátedra ni las
duplica en Catálogo. Estas dos operaciones son públicas y utilizan exclusivamente
la base propia. Su funcionamiento no depende de la disponibilidad de los
proveedores externos, aunque la configuración local del servicio siga siendo obligatoria.

| Operación | Entrada JSON | Resultado |
| --- | --- | --- |
| `POST /api/register` | `login`, `password`, `firstName`, `lastName`, `email`; `imageUrl` y `langKey` opcionales. | `201` sin cuerpo/JWT, después de confirmar la cuenta activa. |
| `POST /api/authenticate` | `username` (login o email), `password` y `rememberMe` opcional, falso por defecto. | `200` con `{"id_token":"..."}` y cabecera `Authorization: Bearer ...`. |

Tras recibir el 201, KMP ejecuta automáticamente authenticate con las mismas
credenciales, sin otra acción de la persona. Si el login posterior falla, la cuenta
permanece activa y se puede volver a iniciar sesión. Una respuesta perdida del
registro no demuestra que no hubo alta: no repetirlo automáticamente ni tratar
un error de duplicado como autenticación.

Reglas de entrada:

- Login de 1 a 50 caracteres, con el patrón del ejemplo oficial JHipster.
- Contraseña de **6 a 100 puntos de código Unicode**, incluidos espacios, en ambos
  endpoints. Un carácter suplementario cuenta una vez, aunque ocupe dos unidades
  UTF-16 o varios bytes. El exceso se rechaza; no se trunca, recorta ni normaliza.
- Nombre y apellido obligatorios, no blancos, de 2 a 50 caracteres; email válido
  obligatorio de 5 a 254.
- Imagen opcional hasta 256 caracteres; no incorpora carga de archivos. Idioma
  de 2 a 10 caracteres, `es` si se omite.
- Login/email únicos sin distinguir mayúsculas, incluidos cruces entre cuentas:
  el login de una no puede ser el email de otra. Dentro de la misma cuenta sí
  pueden coincidir. MySQL garantiza la regla también en altas concurrentes.

Identidad, activación, autoridades y auditoría las asigna Turnos. Campos internos
enviados por el cliente se ignoran; el alta solo concede `ROLE_USER`. Argon2id de
Spring Security protege la contraseña con sal aleatoria de 16 bytes, salida de
32 bytes, memoria de 19456 KiB (19 MiB), dos iteraciones y paralelismo 1. Requiere
`bcprov-jdk18on:1.86`; usa formato nativo Argon2id v19 de 97 caracteres, que cabe
en la columna existente. Se comprueba ese perfil antes de verificar el hash;
un valor corrupto o de otro perfil se informa como fallo técnico sin exponerlo.

### JWT de usuario y errores

El JWT final usa RS256 y la privada externa existente, `iss=backend-turnos`,
`sub` igual al ID estable de cuenta, audiencias `catalog`/`turnos`,
`token_use=user` y `auth=ROLE_USER`. Dura 24 horas, o 30 días con `rememberMe=true`,
desde la emisión. No hay renovación automática ni extensión por actividad.
Catálogo valida la pública correspondiente; este JWT no habilita la entrega
de credenciales técnicas. Nunca se entrega a KMP un JWT de servicio o cátedra.

| Estado y código del error | Significado |
| --- | --- |
| `400 VALIDATION_ERROR` | Entrada inválida, JSON mal formado o contraseña fuera del límite. |
| `400 USER_IDENTIFIER_ALREADY_USED` | Login/email reclamado por otra cuenta, también en un cruce. |
| `401 INVALID_CREDENTIALS` | Credenciales incorrectas, cuenta inexistente o no habilitada. |
| `503 USER_REGISTRATION_UNAVAILABLE` | No se pudo confirmar el resultado del alta; no afirma rollback. |
| `503 AUTHENTICATION_UNAVAILABLE` | No se pudo comprobar la cuenta/contraseña o emitir el JWT. |
| `500 INTERNAL_ERROR` | Fallo inesperado con respuesta genérica sin información interna. |

Los errores usan `application/problem+json`, con `status`, `title`, `code` e
`instance`; validación añade nombres/códigos de campos, nunca valores rechazados.
Respuestas con `Cache-Control: no-store`, sin cookies ni sesiones HTTP.
No registrar cuerpos de estas solicitudes, hashes ni cabeceras bearer.
Estas operaciones no agregan `/api/account`, administración ni recuperación de contraseña.

## Protección de las APIs de Turnos

Turnos verifica localmente firma RS256, emisor `backend-turnos`, audiencia `turnos`,
identidad de cuenta, perfil `user`, autoridades y tiempos. Deriva la pública desde
la privada RSA externa ya validada: no requiere otro archivo, configuración JWKS
ni consultas a Catálogo/cátedra. La tolerancia temporal es de 30 segundos; se exigen
emisión/vencimiento coherentes y se comprueba `nbf` si está presente.

| Acceso | Política |
| --- | --- |
| `POST /api/register`, `POST /api/authenticate`, `GET /actuator/health` | Admiten solicitudes sin bearer. |
| Otras operaciones bajo `/api/` | Requieren JWT de usuario válido y `ROLE_USER`, además de los permisos/propiedad del CU correspondiente. |
| `/api/internal/**` y rutas restantes | Denegadas; Turnos no ofrece una API interna entrante en este alcance. |

Una ruta pública permite omitir el token. Si se envía expresamente un bearer inválido
o vencido, se responde 401 antes del controlador. KMP debe omitir esa cabecera en
registro/login. Un token válido no crea una operación inexistente: una ruta de API
no implementada sigue dando 404 después de superar la seguridad.

Los rechazos de autenticación/autorización devuelven `401 AUTHENTICATION_REQUIRED` por autenticación ausente o
inválida, y `403 ACCESS_DENIED` por falta de permiso, en `application/problem+json`,
sin secretos y con `Cache-Control: no-store`. El 401 incluye `WWW-Authenticate: Bearer`.
Los errores propios de registro/login conservan los códigos de la sección anterior.
El despacho interno de errores no abre llamadas externas a `/error`.

La identidad procede del principal autenticado, cuyo sujeto es el ID estable de cuenta;
un ID o rol enviado libremente no lo sustituye. El contexto pertenece a cada petición,
sin cookies ni sesión HTTP de autenticación. No se consulta la cuenta por petición ni
se incorpora revocación: la activación se comprueba al iniciar sesión.
La comprobación de propiedad efectiva se incorpora con cada CU de procesos/reservas,
que todavía no están implementados. El acceso técnico saliente conserva su JWT propio
de servicio; no se utiliza como sesión de usuario ni se entrega a KMP.

CORS está cerrado por defecto. Si se configura, cada origen debe contener esquema
HTTP(S), host y puerto opcional, sin ruta, query, fragmento ni credenciales; por ejemplo,
`https://app.test,http://localhost:8082` con datos ficticios. Una configuración inválida
impide el arranque con un diagnóstico sin valores sensibles. Los orígenes admitidos
pueden utilizar `GET`, `POST`, `OPTIONS` y cabeceras `Authorization`/`Content-Type`, sin
cookies de autenticación. Un preflight permitido no concede acceso a la operación.

Se mantiene HTTP para desarrollo local/red privada de Compose y HTTPS al exponer el
servicio fuera de ese entorno. Este issue no instala certificados ni proxy.
Ver [SPEC](docs/features/seguridad-jwt/SPEC.md),
[PLAN](docs/features/seguridad-jwt/PLAN.md) y [TASKS](docs/features/seguridad-jwt/TASKS.md).

## Consultar disponibilidad

`GET /api/appointment-availability?professionalId=15&date=2026-11-02` requiere
JWT de usuario y `ROLE_USER`. Recibe un ID positivo y una fecha `yyyy-MM-dd`
desde hoy, sin horizonte máximo futuro adicional. Respuesta `200`, `no-store`:

```json
{
  "professionalId": 15,
  "date": "2026-11-02",
  "timeZone": "America/Argentina/Buenos_Aires",
  "availableStartTimes": ["09:00:00", "09:30:00"]
}
```

Consulta profesional, agendas y vigencia conjuntamente a Catálogo con el JWT de
servicio existente. Aplica las agendas habilitadas del día y su duración, sin
ofrecer turnos incompletos al cierre. Consulta las ocupaciones centrales del mismo
profesional con `from` y `to` iguales a la fecha solicitada, usando el JWT central
obtenido mediante #4. No persiste ni cachea agenda/ocupaciones y no crea holds.

Devuelve inicios ordenados y sin duplicados, excluyendo confirmaciones y holds
aún no vencidos (`expiresAt` UTC). La hora de inicio se filtra otra vez al resolver
la consulta: solo se ofrecen inicios posteriores al momento del cálculo, según
la zona de atención. Las horas locales no se desplazan a UTC en la respuesta.
Si no hay candidatos, no necesita consultar ocupaciones. Una lista vacía válida
significa que no hay horarios libres; un proveedor fallido no produce esa lista.
Consultar no garantiza que un horario siga libre al intentar reservar.

| HTTP / code | Significado |
| --- | --- |
| 400 `INVALID_AVAILABILITY_QUERY` | Campo ausente/incompatible, ID no positivo o fecha pasada. |
| 404 `PROFESSIONAL_NOT_FOUND` | Profesional ausente en el catálogo vigente. |
| 400 `PROFESSIONAL_DISABLED` | Profesional deshabilitado. |
| 503 `CATALOG_NOT_CURRENT` | Catálogo no acredita vigencia. |
| 503 `CATALOG_UNAVAILABLE` | No se puede obtener o interpretar la agenda. |
| 503 `OCCUPANCIES_UNAVAILABLE` | No se pueden obtener o interpretar ocupaciones. |
| 503 `TECHNICAL_INTEGRATION_UNAVAILABLE` | Sesión central no disponible o rechazada; se conserva la recuperación de #4. |
| 401 / 403 | Seguridad existente del JWT de usuario. |

Los errores propios usan `application/problem+json`, código seguro e `instance`
sin query; los filtros conservan su formato existente. Un 401 del proveedor no
se presenta como rechazo del usuario: el JWT central utilizado se invalida y no
se repite automáticamente la consulta. No hay reintentos HTTP ocultos ni redirects.
Los parámetros opcionales se definen en `app.availability` de `application.yaml`
y se sobrescriben por entorno al arrancar; zona o duración inválidas impiden el arranque.

Contratos y aceptación: [SPEC](docs/features/consulta-disponibilidad/SPEC.md),
[PLAN](docs/features/consulta-disponibilidad/PLAN.md) y
[TASKS](docs/features/consulta-disponibilidad/TASKS.md).

## Compilar y verificar

```bash
./mvnw -B -ntp clean verify
```

Compila, ejecuta pruebas y empaqueta `target/appointment-0.0.1-SNAPSHOT.jar`.
Testcontainers administra un MySQL 8.4.8 temporal: Docker debe estar accesible.
Las pruebas no usan `.env`, la base de desarrollo ni servicios reales de cátedra/Catálogo.
Comprueban la migración productiva de usuarios y la sonda SQL exclusiva de test,
el historial Flyway, health HTTP sin detalles,
configuración inválida/base inaccesible y el acceso técnico con proveedores HTTP
ficticios, firmas RSA efímeras, relojes controlados y barreras de concurrencia.
También verifican registro/login, límites por puntos de código Unicode, campos internos manipulados,
unicidad cruzada concurrente, commit incierto y persistencia al recrear el contexto.
Las pruebas de seguridad ejecutan filtros y decoder reales con JWT firmados, comprueban
perfiles, permisos, tiempos, identidad concurrente y CORS. Sus endpoints de observación
existen exclusivamente en tests y no se incluyen en el artefacto productivo.
No requieren una privada personal, `.env`, Kafka ni Catálogo/cátedra reales.
Las pruebas de disponibilidad cubren horarios, fechas y holds con reloj controlado,
dos proveedores HTTP separados, fallos de transporte/decodificación, login real,
consulta concurrente e invalidación/readquisición de acceso técnico sin reiniciar.
Reportes de pruebas en `target/surefire-reports/`. JaCoCo mide la cobertura de
las pruebas y genera `target/site/jacoco/jacoco.xml` e `index.html` durante `verify`.
El HTML se puede abrir localmente; el XML es el que importa Sonar.

El almacenamiento del volumen de Compose se comprueba por separado,
confirmando un dato ficticio y recreando contenedores sin borrar el volumen.
La evidencia local y sus límites se registran en [TASKS.md](docs/features/setup-backend/TASKS.md).

## CI y calidad

[El workflow](.github/workflows/ci.yml) ejecuta `clean verify` con Java 25 y,
si pasa, `sonar:sonar` en otro paso del mismo job. Testcontainers administra
MySQL temporal, sin Compose, `.env` ni base adicional en Actions.

Se activa por pushes a `main` y PR dirigidos a `main` al abrirlos, reabrirlos,
actualizar commits o editarlos. Cambiar el destino del PR a `main` activa CI
sin agregar un commit; editar título/descripción también repite la ejecución.
La concurrencia por workflow/referencia cancela una ejecución anterior si
se inicia otra para esa referencia.

Un PR apilado sobre otra rama, como la de setup #3, no ejecuta este workflow.
Validarlo localmente hasta integrar el PR base y cambiar su destino a `main`.
Este alcance es compatible con el plan Free de SonarQube Cloud.

El análisis publica en [el proyecto de Turnos](https://sonarcloud.io/project/overview?id=prog2-perassiferrara_backend-turnos),
organización `prog2-perassiferrara`. Sonar importa la cobertura de JaCoCo y
publica hallazgos y Quality Gate; no genera la medición de cobertura.
El Gate es informativo (`sonar.qualitygate.wait=false`); los errores de compilación,
pruebas o ejecución/publicación del scanner sí hacen fallar el job.
No se configura un mínimo de cobertura ni se ocultan errores.

`SONAR_TOKEN` ya está cargado como secreto de GitHub y Automatic Analysis
desactivado, según confirmación del usuario. El token se entrega únicamente
al paso de análisis; no debe guardarse en archivos ni imprimirse. En PR de
forks se ejecutan las pruebas y se omite el análisis por ausencia de secretos.
Si falta el token en un análisis habilitado, el job falla con un diagnóstico.

No se suben artefactos descargables de cobertura: los informes se consultan
localmente y la cobertura remota en Sonar. La validación local no demuestra
ejecución de Actions, importación en Sonar ni estado del Gate. Consultar
[SPEC](docs/features/ci-calidad/SPEC.md), [PLAN](docs/features/ci-calidad/PLAN.md)
y [TASKS](docs/features/ci-calidad/TASKS.md) para evidencia y pendientes del
[issue #17](https://github.com/prog2-perassiferrara/backend-turnos/issues/17).

## Iniciar una reserva

`POST /api/reservation-processes` requiere un JWT de usuario. El cliente genera un
UUID `requestId` por acción y lo conserva al reintentar la misma selección:

```json
{
  "requestId": "b7273764-9bc3-43fd-b42c-8a9ca65c5639",
  "professionalId": 15,
  "date": "2026-11-02",
  "startTime": "09:00"
}
```

Los valores son ficticios; consultar primero la disponibilidad vigente. Turnos toma
el paciente y los nombres de la cuenta autenticada, comprueba perfil, horario y canal
Kafka antes del hold, y ejecuta creación y confirmación inicial REST en una sola acción.
La confirmación inicial no significa que la reserva esté confirmada: el proceso espera
la solicitud de teléfono. El envío y la consulta de progreso se describen debajo; la cancelación tiene su propio CU.

Un inicio nuevo devuelve `202`; la repetición propia, `200` con el estado persistido,
incluso después del cierre, sin repetir los POST. Ambos usan `Cache-Control: no-store`
y devuelven `processId` (el `requestId` local), profesional, fecha, hora `HH:mm:ss`,
`state`, `expiresAt` y `reasonCode` opcionales. No exponen JWT ni IDs técnicos centrales.
Reutilizar la clave con otra selección o usuario produce `409 REQUEST_ID_CONFLICT`,
sin revelar el proceso ajeno. Otra clave del mismo propietario mientras hay un
pendiente produce `409 ACTIVE_RESERVATION_PROCESS` con la referencia local propia.

Un perfil incompatible o selección inválida produce `400`; la competencia por el
horario produce `409`; proveedores, almacenamiento o mensajería inaccesibles, `503`.
Los errores usan `application/problem+json` y `code`; no interpretar `detail`.
El registro exige nombres de 2 a 50 caracteres, compatibles con el mínimo central
y el máximo del modelo JHipster. Reservas conserva la validación central de 2 a 100
para impedir holds con perfiles existentes incompatibles.

V3 conserva intención, propietario, selección y progreso antes de cada efecto remoto.
MySQL impone un solo pendiente por usuario y permite históricos. Kafka utiliza la
configuración asignada por cátedra, sin crear topics productivos: debe ser accesible
desde Turnos y haber asignación del consumidor para admitir nuevos holds. Un topic
vacío es válido. La preparación es acotada al arranque y a demanda; health sigue
siendo técnico. El presupuesto agotado al guardar un mensaje detiene el consumo sin
descartarlo; otro intento puede preparar el canal de nuevo.

El inbox confirma los efectos y el recibo antes del offset; deduplica por `eventId`
y conserva mensajes incoherentes o aún no correlacionados. Un evento recibido antes
de la respuesta REST prevalece y no reinicia el plazo. La base y los offsets permiten
conservar el progreso tras reinicios; este CU no ejecuta recuperación remota completa.
Una confirmación incierta mantiene el proceso y no repite su POST. Si se pierde la
respuesta de creación sin IDs, Turnos guarda incertidumbre y consulta ocupaciones:
HELD/CONFIRMED impiden otro POST; HELD devuelve `recheckAt` en el problema HTTP
para esperar y volver a consultar, sin asumir propiedad. Una posición libre permite
otra creación dentro del presupuesto, tras revalidar agenda/canal/reloj y contemplando
un posible 409. Al detenerse archiva como `HOLD_ATTEMPT_ABANDONED`, que libera solo
la exclusión local; repetir su requestId devuelve ese cierre y una nueva acción usa
otro UUID y consultas nuevas. Consulta fallida o presupuesto agotado no habilitan
POST adicionales. El TTL predeterminado central de diez minutos empieza al crearse
el hold; nunca se libera por diez minutos desde un timeout local.
Desconectar el cliente tampoco cancela el proceso.

Contrato y diseño: [SPEC](docs/features/inicio-reserva/SPEC.md),
[PLAN](docs/features/inicio-reserva/PLAN.md) y [TASKS](docs/features/inicio-reserva/TASKS.md).

## Completar teléfono y consultar progreso

`POST /api/reservation-processes/{processId}/phone` acepta el teléfono del propietario
solo cuando existe una solicitud central correlacionada y sigue vigente. El cuerpo
contiene únicamente un UUID por envío lógico y el teléfono:

```json
{
  "requestId": "b726f4c6-fcd9-459b-aa41-6157e678cd18",
  "phoneNumber": "+54 (9261) 555-12.34"
}
```

Se eliminan espacios ASCII, guiones, paréntesis y puntos. El resultado admite un `+`
opcional y de 7 a 15 dígitos ASCII, conservando ceros iniciales. No agrega prefijos
ni modifica el perfil de la cuenta. Los valores de ejemplo son ficticios.

- `202`: proceso y respuesta conservados juntos en MySQL, incluso con Kafka caído.
- `200`: repetición del mismo UUID y teléfono normalizado, también tras cerrar.
- `400`: entrada inválida; `404`: proceso inexistente o ajeno, indistinguibles.
- `409`: UUID con contenido diferente, respuesta activa, fase o plazo incompatible.
- `503`: almacenamiento no verificable. Ningún error se interpreta por `detail`.

V4 agrega una tabla de outbox e historial; una restricción SQL permite una sola
respuesta activa por proceso. El publicador automático usa lotes de hasta diez,
tres intentos por ciclo y el cooldown configurado; conserva contador, próxima fecha,
eventId y contenido tras reiniciar. Nunca amplía `expiresAt`. El ACK del broker deja
`WAITING_FOR_RESULT`, sin afirmar una confirmación funcional. Una publicación incierta
puede repetirse físicamente con el mismo evento; cátedra lo deduplica por `eventId`.
El productor utiliza el destino asignado retenido, sin obtener otro JWT por envío.

Un rechazo correlacionado vuelve a `WAITING_FOR_PHONE`; una corrección usa un UUID
nuevo con la solicitud y el plazo originales. Una confirmación, cancelación,
expiración o invalidez detiene esa publicación. El recibo, el cambio de proceso y el
cierre del envío se guardan juntos antes del offset. Un ACK tardío no reabre el envío
anterior ni modifica la corrección. Alcanzar el plazo local impide publicar y mantiene
la incertidumbre; no acredita una expiración central.

`GET /api/reservation-processes/{processId}` devuelve progreso propio e histórico.
Los dos endpoints requieren JWT de usuario y usan `Cache-Control: no-store`. Conservan
los campos del inicio y agregan `phoneRequestId`, `requiredField`, `canSubmitPhone`,
`verificationStatus`, `verifiedAt` y `warningCode`. No exponen teléfono, JWT técnico ni
identificadores centrales. KMP puede consultar periódicamente mientras espera.

La lectura es local normalmente. Con incertidumbre, plazo alcanzado o espera del
resultado superior al límite configurado, realiza un GET central acotado por paciente
propio, fecha exacta y una página (0, tamaño 100). Solo una coincidencia de proceso,
paciente y horario permite reconciliar. Una página vacía/incompleta deja
`INCONCLUSIVE`; dependencia inaccesible devuelve el estado local con `UNAVAILABLE`
y aviso. `VERIFIED` indica evidencia central consultada y `LOCAL`, lectura local.
Consultar no crea holds, confirma, cancela ni publica teléfonos. No fabrica una
solicitud cuando solo conoce `PHONE_PENDING`.

Contrato y diseño: [SPEC](docs/features/telefono-progreso-reserva/SPEC.md),
[PLAN](docs/features/telefono-progreso-reserva/PLAN.md) y
[TASKS](docs/features/telefono-progreso-reserva/TASKS.md).

## Archivos y responsabilidades

- `pom.xml` y wrapper: dependencias y compilación reproducible, con versiones gestionadas por Boot; JaCoCo genera cobertura y SonarScanner publica el análisis.
- `.github/workflows/ci.yml`: eventos, entorno y pasos de validación remota; el secreto queda limitado al scanner.
- `AppointmentApplication`: punto de entrada del contexto y servidor HTTP.
- `DatabaseConfiguration`: validación temprana de variables obligatorias mediante un procesador de Spring.
- `application.yaml`: conexión, límites de espera, JPA, Flyway y exposición mínima de Actuator.
- `compose.yaml` y `Dockerfile`: base persistente y backend contenedorizado; el runtime Java usa un usuario sin privilegios.
- `AppointmentApplicationTests` y SQL en `src/test/resources/`: integración aislada, sin empaquetar recursos de test en producción.
- `TechnicalAccessProperties` y `TechnicalAccessSettingsConfiguration`: configuración compartida y validación de clave externa, fuera de slices.
- `ServiceTokenIssuer`: firma la credencial propia del servicio; no genera el JWT central.
- `AcquireTechnicalAccessUseCaseImpl`: obtiene y valida una sesión completa antes de publicarla.
- `TechnicalAccessUseCaseImpl`: entrega acceso válido o solicita recuperación; informa rechazos del token afectado.
- Clientes HTTP de `integration/infrastructure/client/adapter/`: patrón Adapter para los dos contratos salientes, separados por proveedor.
- `InMemoryTechnicalAccessAdapter`: patrón Repository, con sesión/invalidación/cierre atómicos y huellas de tokens rechazados, sin tablas.
- `TechnicalAccessRecoveryAdapter`: coordina executor, intentos y espera entre ciclos; son mecanismos de ejecución, sin agregar un patrón de negocio.
- `user/domain/ports/` y los casos de uso de `user/application/usecases/`: alta/autenticación y lectura mínima de perfil para otros slices, sin dependencias de JPA o HTTP.
- `JpaUserAccountAdapter` y `TransactionalUserAccountStore`: Adapter/Repository y límite transaccional; el proxy confirma antes de que el adaptador devuelva el alta o traduzca un fallo.
- `UserAccountEntity`, `JpaUserAccountRepository` y `UserAccountMapper`: persistencia propia y conversión a dominio; identificadores y autoridades son conjuntos de valores.
- `SpringPasswordHasherAdapter`, `NimbusUserTokenIssuerAdapter` y `UserConfiguration`: hashing, firma y wiring del slice con clave/reloj externos; conservan el firmante técnico existente.
- Los dos controladores de `user/infrastructure/web/`, DTO, mapper, validación y `UserProblemHandler`: frontera HTTP, límite Unicode compartido mediante `PasswordLength` y errores sin secretos. DTO y entidades permanecen separados.
- Modelos y puertos de `availability/domain/`: horarios completos, posiciones ocupadas y contratos propios, sin JWT ni infraestructura.
- `GetAvailabilityUseCaseImpl`: combina agenda y ocupaciones, valida la fecha y aplica un instante final consistente.
- Los adaptadores HTTP de `availability/infrastructure/client/`: patrón Adapter para los contratos de Catálogo y cátedra; solo el de ocupaciones compone el puerto público de integración.
- `AvailabilityProperties` y `AvailabilityConfiguration`: defaults externos validados, reloj y clientes con destinos/presupuestos separados, sin cabecera bearer global.
- `AvailabilityController`, su mapper y `AvailabilityProblemHandler`: contrato de consulta protegido y errores propios, sin persistencia o administración de reservas.
- `ReservationProcess` y sus puertos: identidad, transiciones y contratos de inicio/recepción, sin dependencias externas.
- `StartReservationUseCaseImpl`: coordina validación, intención durable y ambos POST, preservando idempotencia e incertidumbre.
- `TransactionalReservationStore` y `JpaReservationProcessAdapter`: transacciones cortas, exclusión SQL, versión, verificación de escritura y recibo/cambio indivisibles.
- `ReservationKafkaChannel` y `ReservationActionParser`: preparación del consumidor, frontera v1, aplicación durable y ACK posterior a MySQL.
- Adaptadores de `reservation/infrastructure/`: componen los puertos públicos de perfil, disponibilidad y acceso técnico; el cliente HTTP no repite efectos automáticamente.
- `PhoneSubmission`, `SubmitReservationPhoneUseCaseImpl` y los puertos: normalización, aceptación durable e idempotencia del envío.
- `PublishPendingPhonesUseCaseImpl`, `PhoneSubmissionDispatcher` y `KafkaPhoneSubmissionAdapter`: recuperación automática, publicación v1 y ACK separado del resultado.
- `GetReservationProcessUseCaseImpl` y `WebClientCentralAppointmentAdapter`: progreso propio y reconciliación REST puntual.
- `ReservationController`, DTO, mapper y advice: APIs de inicio, teléfono y consulta y errores con referencias exclusivamente propias.

Flyway es el único responsable de cambiar el esquema; Hibernate usa `ddl-auto=validate`.
`V2__create_users.sql` crea cuentas, identificadores y autoridades en
`src/main/resources/db/migration/`. V1 es una sonda exclusiva de pruebas;
no se empaqueta en producción. No editar una migración ya aplicada.
`V3__create_reservation_processes.sql` agrega procesos e inbox de reservas.
`V4__create_reservation_phone_submissions.sql` agrega respuestas durables e historial,
sin modificar migraciones previas ni cuentas existentes.

El paquete base es `com.prog2.appointment`. Las funcionalidades futuras respetarán
dominio, aplicación e infraestructura según la estructura acordada; no se generan slices vacíos.
El setup conserva autoconfiguración y un punto de extensión de Spring; el slice de
integración aplica los puertos y adaptadores previstos, sin estructuras futuras vacías.

Documentación: [SPEC](docs/features/setup-backend/SPEC.md),
[PLAN](docs/features/setup-backend/PLAN.md), [TASKS](docs/features/setup-backend/TASKS.md)
y [casos de uso](docs/use-cases/README.md).

Integración técnica: [SPEC](docs/features/integracion-tecnica/SPEC.md),
[PLAN](docs/features/integracion-tecnica/PLAN.md) y [TASKS](docs/features/integracion-tecnica/TASKS.md),
con evidencia local, entre servicios y remota diferenciadas.

Registro/autenticación: [SPEC](docs/features/registro-autenticacion/SPEC.md),
[PLAN](docs/features/registro-autenticacion/PLAN.md) y [TASKS](docs/features/registro-autenticacion/TASKS.md).

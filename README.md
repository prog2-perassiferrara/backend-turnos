# Servicio de turnos y reservas

Base técnica con Java 25, Spring Boot 4.1.1, Maven Wrapper 3.10.0,
MySQL 8.4.8, JPA/Hibernate y Flyway. Incluye health y pruebas de integración.
Los casos de uso, JWT e integración con Catálogo/cátedra se desarrollarán en sus issues.

## Requisitos

- Docker Engine disponible y Docker Compose.
- JDK 25 para ejecutar Java localmente o usar el wrapper en la máquina.
- Acceso a Maven Central y Docker Hub en la primera ejecución.
- No hace falta instalar Maven: el wrapper descarga la versión 3.10.0.
- En Windows, utilizar `mvnw.cmd`; ese entorno no fue validado aquí.

## Configuración local

```bash
cp .env.example .env
```

Completar las dos contraseñas ficticias de desarrollo en el archivo local.

| Variable | Uso |
| --- | --- |
| `DB_NAME` | Base exclusiva de Turnos; ejemplo `appointment`. |
| `DB_USER` | Usuario limitado a esa base, distinto de root. |
| `DB_PASSWORD` | Contraseña obligatoria del usuario de aplicación. |
| `DB_HOST` | `localhost` para Java local; Compose conecta internamente a `mysql`. |
| `DB_PORT` | Puerto local de MySQL; por defecto 3307. |
| `APP_PORT` | Puerto local del backend; por defecto 8081. |
| `MYSQL_ROOT_PASSWORD` | Solo inicializa MySQL, no se entrega al backend. |

Los puertos por defecto difieren de Catálogo (8080/3306) para ejecutar ambos juntos.
Si están ocupados, cambiarlos en `.env`. Los contenedores de Turnos utilizan su propia
base, red y volumen; no comparten almacenamiento con Catálogo.

`.env` está ignorado y excluido del contexto Docker. Usar datos ficticios,
sin credenciales de cátedra. Compose interpola ese archivo; Spring Boot no lo carga automáticamente.

## Ejecutar todo con Compose

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8081/actuator/health
```

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

## Ejecutar desde el IDE o con Maven

```bash
docker compose up -d mysql
```

Proporcionar al IDE `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`
y `APP_PORT`, y ejecutar `com.prog2.appointment.AppointmentApplication` con JDK 25.
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

## Compilar y verificar

```bash
./mvnw -B -ntp verify
```

Compila, ejecuta pruebas y empaqueta `target/appointment-0.0.1-SNAPSHOT.jar`.
Testcontainers administra un MySQL 8.4.8 temporal: Docker debe estar accesible.
Las pruebas no usan `.env`, la base de desarrollo ni servicios reales de cátedra/Catálogo.
Comprueban SQL exclusivo de test e historial Flyway, health HTTP sin detalles y
rechazo de configuración inválida/base inaccesible, con diagnósticos sin la contraseña ficticia.
Reportes en `target/surefire-reports/`.

El almacenamiento del volumen de Compose se comprueba por separado,
confirmando un dato ficticio y recreando contenedores sin borrar el volumen.
La evidencia local y sus límites se registran en [TASKS.md](docs/features/setup-backend/TASKS.md).
CI y calidad remota pertenecen al [issue #17](https://github.com/prog2-perassiferrara/backend-turnos/issues/17);
esta etapa no demuestra ejecución de Actions ni resultados de Sonar.

## Archivos y responsabilidades

- `pom.xml` y wrapper: dependencias y compilación reproducible, con versiones gestionadas por Boot.
- `AppointmentApplication`: punto de entrada del contexto y servidor HTTP.
- `DatabaseConfiguration`: validación temprana de variables obligatorias mediante un procesador de Spring.
- `application.yaml`: conexión, límites de espera, JPA, Flyway y exposición mínima de Actuator.
- `compose.yaml` y `Dockerfile`: base persistente y backend contenedorizado; el runtime Java usa un usuario sin privilegios.
- `AppointmentApplicationTests` y SQL en `src/test/resources/`: integración aislada, sin empaquetar recursos de test en producción.

Flyway es el único responsable de cambiar el esquema; Hibernate usa `ddl-auto=validate`.
No hay entidades ni migraciones de negocio. Las futuras migraciones SQL irán en
`src/main/resources/db/migration/`; no editar una migración ya aplicada.

El paquete base es `com.prog2.appointment`. Las funcionalidades futuras respetarán
dominio, aplicación e infraestructura según la estructura acordada; no se generan slices vacíos.
Este setup utiliza autoconfiguración y un punto de extensión de Spring, sin patrones de dominio añadidos.

Documentación: [SPEC](docs/features/setup-backend/SPEC.md),
[PLAN](docs/features/setup-backend/PLAN.md), [TASKS](docs/features/setup-backend/TASKS.md)
y [casos de uso](docs/use-cases/README.md).

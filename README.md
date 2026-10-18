<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-61%2F100-green) ![Aprobar](https://img.shields.io/badge/Aprobar-s%C3%AD-brightgreen) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-OK-green) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-3.9%25-orange) ![%POO](https://img.shields.io/badge/%25POO-72%25-yellow) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-17-red) ![D2](https://img.shields.io/badge/D2-45-orange) ![D4](https://img.shields.io/badge/D4-0-red) ![D5](https://img.shields.io/badge/D5-100-green) ![D6](https://img.shields.io/badge/D6-100-green) ![D7](https://img.shields.io/badge/D7-100-green)

**Calidad (heurístico, 7 dimensiones):** índice **61/100** · build **OK** · aprobar **sí (8/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# Servicio de turnos y reservas

Base técnica con Java 25, Spring Boot 4.1.1, Maven Wrapper 3.10.0,
MySQL 8.4.8, JPA/Hibernate y Flyway. Incluye acceso técnico a cátedra mediante
el contrato protegido de Catálogo, health y pruebas de integración.
Registro/login de usuarios, protección de APIs propias y reservas se desarrollan en sus issues.

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

## Compilar y verificar

```bash
./mvnw -B -ntp clean verify
```

Compila, ejecuta pruebas y empaqueta `target/appointment-0.0.1-SNAPSHOT.jar`.
Testcontainers administra un MySQL 8.4.8 temporal: Docker debe estar accesible.
Las pruebas no usan `.env`, la base de desarrollo ni servicios reales de cátedra/Catálogo.
Comprueban SQL exclusivo de test e historial Flyway, health HTTP sin detalles,
configuración inválida/base inaccesible y el acceso técnico con proveedores HTTP
ficticios, firmas RSA efímeras, relojes controlados y barreras de concurrencia.
No requieren una privada personal, `.env`, Kafka ni Catálogo/cátedra reales.
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

Flyway es el único responsable de cambiar el esquema; Hibernate usa `ddl-auto=validate`.
No hay entidades ni migraciones de negocio. Las futuras migraciones SQL irán en
`src/main/resources/db/migration/`; no editar una migración ya aplicada.

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

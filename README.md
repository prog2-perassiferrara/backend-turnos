<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-59%2F100-yellow) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-OK-green) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-0.0%25-green) ![%POO](https://img.shields.io/badge/%25POO-81%25-green) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-6-red) ![D2](https://img.shields.io/badge/D2-100-green) ![D4](https://img.shields.io/badge/D4-20-red) ![D5](https://img.shields.io/badge/D5-0-red) ![D6](https://img.shields.io/badge/D6-100-green) ![D7](https://img.shields.io/badge/D7-100-green)

**Calidad (heurístico, 7 dimensiones):** índice **59/100** · build **OK** · aprobar **no (6/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# Servicio de turnos y reservas

Backend Java 25, Spring Boot 4.1.1, Maven Wrapper 3.10.0, MySQL 8.4.8,
JPA/Hibernate y Flyway. Implementa registro/login, JWT de usuario, disponibilidad,
hold y confirmación REST, intercambio telefónico Kafka, consulta propia, cancelación
y recuperación durable. Catálogo y cátedra se consumen por contratos protegidos.

- [Arquitectura y modelo de datos vigentes](docs/ARQUITECTURA.md): límites, FSM, transacciones, idempotencia, outbox y recovery.
- [Contratos vigentes](docs/CONTRATOS.md): API propia, autenticación, errores, Catálogo y REST/Kafka central.
- [Casos de uso](docs/use-cases/README.md): comportamiento funcional; CU-10 quedó retirado.
- [Historial SDD](docs/features/README.md): decisiones y evidencias de revisiones anteriores, separado del diseño actual.

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

Completar contraseñas propias del entorno de desarrollo, URLs, integración asignada
y ruta de la privada externa. Spring Boot no registra cuentas técnicas: debe existir una
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
| `RESERVATION_RETRY_DELAY` | Opcional, pausa entre intentos Kafka y ciclos de recuperación, `5s`. |
| `RESERVATION_PHONE_PUBLISH_TIMEOUT` | Opcional, metadata más bloqueo de envío, y espera de ACK: `5s` por presupuesto, hasta `10s` en total. |
| `RESERVATION_RESULT_VERIFICATION_DELAY` | Opcional, espera desde el ACK sin resultado antes de la revisión automática por REST, `30s`. |
| `RESERVATION_PREPARATION_COOLDOWN` | Opcional, espera mínima tras agotar preparación, `30s`; el ciclo de recuperación solicita automáticamente otra preparación, respetando esta pausa. |

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
ambas fuera del repositorio. Ejecutar desde su raíz; para este ejemplo configurar
`JWT_PRIVATE_KEY_PATH=../turnos-keys/private.pem` en `.env`:

```bash
mkdir -p ../turnos-keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ../turnos-keys/private.pem
openssl pkey -in ../turnos-keys/private.pem -pubout -out ../turnos-keys/public.pem
chmod 600 ../turnos-keys/private.pem
```

No pegar el contenido de las claves ni tokens en chat, logs, documentación o Git.
La rotación es manual y debe coordinarse con la pública del proveedor. No se generan
claves ni se llama al login central al arrancar Turnos.

Compose ejecuta Java como UID 10001. El archivo privado debe ser legible por ese UID
sin conceder escritura; en Linux se puede otorgar lectura específica con una ACL
(`setfacl -m u:10001:r ../turnos-keys/private.pem`). El montaje es de solo lectura,
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
curl --fail --connect-timeout 2 --max-time 3 --retry 30 --retry-delay 2 --retry-all-errors http://localhost:8081/actuator/health
```

Al aplicar por primera vez esta configuración de redes, `up` recrea los backends
cuando detecta el cambio. `restart` por sí solo no aplica cambios de configuración.
Si Catálogo aún no dispone de sesión y se agotan los intentos iniciales, Turnos
permanece sin acceso técnico. Con Catálogo listo, repetir la adquisición reiniciando
Turnos mediante `docker compose restart appointment`. No se readquiere por consultas.

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

## Compilar y verificar

```bash
./mvnw -B -ntp clean verify
```

Docker debe estar accesible: Testcontainers administra MySQL 8.4.8 y Kafka temporal
para las pruebas que lo necesitan. No usa `.env`, servicios reales ni la base de
Compose; genera claves y proveedores ficticios propios. Compila, prueba y empaqueta
`target/appointment-0.0.1-SNAPSHOT.jar` sin fixtures ni recursos de test.

Informes: `target/surefire-reports/`; cobertura JaCoCo XML/HTML:
`target/site/jacoco/jacoco.xml` e `index.html`. Para repetir solo un área:

```bash
./mvnw -B -ntp -Dtest=CentralAppointmentGatewayTests,ReservationCancellationIntegrationTests test
```

La suite cubre usuarios/JWT, contratos HTTP, disponibilidad, REST + Kafka, SQL,
idempotencia, concurrencia, commit incierto, reinicios, outbox, cancelación y
migraciones históricas. No editar migraciones aplicadas; Hibernate solo valida.
Las pruebas con proveedores ficticios no sustituyen la validación real contra cátedra.

## CI y calidad

[GitHub Actions](.github/workflows/ci.yml) ejecuta Java 25 y `clean verify` en pushes
a `main` y PR hacia `main` (`opened`, `synchronize`, `reopened`, `edited`). Cambiar
la base a `main` activa CI; editar título/descripción también. Un PR contra otra
rama requiere validación local hasta apuntarlo a `main`.

Después publica `sonar:sonar` en la organización `prog2-perassiferrara`, proyecto
`prog2-perassiferrara_backend-turnos`, importando cobertura JaCoCo. Requiere el secreto
GitHub `SONAR_TOKEN`; en forks se omite el análisis. Quality Gate informativo
(`sonar.qualitygate.wait=false`); errores de build, tests o scanner hacen fallar el job.
No hay otra base en el workflow ni `continue-on-error`. Los resultados locales no
acreditan Actions ni un Gate remoto nuevo. El análisis automático de SonarCloud debe
permanecer desactivado para evitar duplicarlo con el scanner de CI.

## Límites operativos

- Health `UP` es técnico, no acredita vigencia de Catálogo ni preparación Kafka.
- Si se agotan los intentos iniciales de acceso técnico, o el JWT vence/se rechaza,
  corregir la causa y reiniciar Turnos. No hay readquisición por consulta.
- La búsqueda central `find` recorre hasta tres páginas de 100. Devuelve coincidencias
  exactas en páginas válidas; no encontrar no demuestra inexistencia ni liberación.
- Un commit JDBC incierto no se rescata como éxito inmediato: si era el checkpoint,
  no se dispara el POST; recovery relee/reconcilia lo persistido. Un fallo al guardar
  un resultado no deshace el efecto central ya enviado.
- Cancelación tiene hasta tres POST por ejecución, con consulta previa; no conserva
  un presupuesto exacto entre reinicios. Un retry explícito puede abrir otro intento.
- GET/List propios muestran estado durable local, sin certificar frescura central.
  No hay importación de reservas sin asociación local ni abandono funcional.
- Un ensayo controlado no acredita el circuito real completo de cátedra. Solicitud
  Kafka, respuesta, resultado y cancelación real requieren evidencia separada.

Los detalles y su impacto están en la [arquitectura vigente](docs/ARQUITECTURA.md).

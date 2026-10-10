# PLAN: Inicializar backend de Turnos y reservas

**SPEC de referencia:** [SPEC.md](SPEC.md).
**Versión revisada:** 2026-10-09, siete RF y nueve CA.
**Estado:** Aprobado.
**Autorización:** el usuario pidió avanzar hasta implementar si el setup era equivalente al de Catálogo. Se comprobó esa equivalencia; las adaptaciones propias se detallan abajo. No incluye CI #17 ni commits, push o merge.

<!-- PARA LA PERSONA
Este documento define la solución técnica de la SPEC aprobada. TASKS.md registra
ejecución, dependencias y evidencias reales, sin duplicar los requisitos.
-->

<!-- PARA EL AGENTE
Leer SPEC e instrucciones vigentes; respetar alcance y autorizaciones.
Consultar diferencias con impacto real, sin reabrir las decisiones confirmadas.
No marcar comprobaciones superadas sin ejecutarlas. Si cambia el comportamiento,
revisar SPEC y la aprobación correspondiente antes de implementar ese cambio.
-->

## Contexto técnico verificado

Al iniciar el setup, Turnos contenía documentación y `.gitignore`, sin código ni build previo. La rama local de #3 parte de la documentación integrada por el PR #16. Referencias de consulta, sin modificaciones: [setup de Catálogo](../../../../backend-catalogo/docs/features/setup-backend/PLAN.md), su `pom.xml`, wrapper, configuración, Compose y pruebas. Se consultó `MATERIA_REFERENCE.md` y los fragmentos de clases sobre Boot, migraciones y separación por funcionalidad.

**Convención:** `hexagonal-arch` y decisiones compartidas: dominio independiente, aplicación dependiente del dominio e infraestructura separada. Este setup no necesita slices, puertos ni entidades vacías; no se agregan por anticipación. Los futuros slices seguirán la estructura del profesor. No se necesita diagrama de clases para dos componentes técnicos sin relaciones de negocio.

## Solución propuesta

Reutilizar el enfoque de Catálogo: módulo Maven, arranque Spring Boot, MySQL propio, JPA/Hibernate, Flyway, configuración externa y health mínimo. Validar las variables obligatorias antes de abrir conexiones; una base inaccesible impide completar el arranque (RF-01 a RF-07).

Adaptaciones sin cambio funcional: identidad `appointment`, paquete `com.prog2.appointment`, clase `AppointmentApplication`, base/usuario de ejemplo `appointment`, volumen `appointment-mysql-data`, puertos locales 8081 y 3307. La separación permite ejecutar ambos repositorios con casi los mismos comandos y sin colisiones por defecto.

## Módulos y componentes afectados

Un único módulo Maven `com.prog2:appointment`, JAR. No se añade el segmento `hexagonal`, como en Catálogo. No hay dependencias de código entre repositorios.

| Ruta propuesta | Acción y responsabilidad | RF |
| --- | --- | --- |
| `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Crear build y wrapper equivalentes a Catálogo, sin plugins de CI todavía. | 01, 02, 05 |
| `src/main/java/com/prog2/appointment/AppointmentApplication.java` | Crear punto de arranque. | 01 |
| `src/main/java/com/prog2/appointment/infrastructure/config/DatabaseConfiguration.java` | Validar configuración obligatoria sin imprimir valores sensibles. | 04, 06 |
| `src/main/resources/application.yaml` | Configurar persistencia, límites de conexión y health. | 02, 04, 06, 07 |
| `src/test/java/com/prog2/appointment/`, `src/test/resources/` | Pruebas con MySQL temporal y migración exclusiva de test. | 01, 02, 04, 06, 07 |
| `compose.yaml`, `Dockerfile`, `.dockerignore`, `.env.example` | Ejecución aislada y volumen persistente propio. | 02, 03, 04 |
| `README.md` | Documentar ambos modos de ejecución y validación. | 05 |

## Datos y contratos

- Salud: `/actuator/health`, únicamente estado global, sin detalles, componentes, probes ni discovery. Con conexión válida devuelve HTTP 200 y `{"status":"UP"}`; incluye el indicador de base de datos y no informa vigencia del catálogo ni capacidad de reservar.
- MySQL propio con usuario de aplicación limitado a su base. No acceder a datos de Catálogo ni usar la cuenta root desde Java.
- Flyway es el único mecanismo de cambios de esquema; `ddl-auto=validate`, `open-in-view=false`, inicialización SQL básica deshabilitada. No se crean migraciones ni tablas de negocio. `setup_probe` es SQL exclusivo de tests y no se empaqueta en producción.
- No se implementan cuentas, JWT, clientes de cátedra/Catálogo, Redis ni Kafka.

## Estado, operaciones y errores

- Volumen nombrado propio en `/var/lib/mysql`; recrear contenedores preservándolo conserva los datos (CA-03).
- `DB_NAME`, `DB_USER` y `DB_PASSWORD` son obligatorias y no pueden estar en blanco. Un `BeanFactoryPostProcessor`, igual al de Catálogo, valida antes de crear conexiones. Es un mecanismo de extensión de Spring, no un patrón de dominio nuevo.
- JDBC: conexión 5 segundos, socket 10 segundos, adquisición del pool 10 segundos y sin reintentos adicionales de Flyway. Pruebas negativas limitadas a 30 segundos; no se ocultan fallos ni se imprimen contraseñas.
- Las dos clases técnicas y los contratos relevantes tendrán Javadoc tradicional en español. No se necesita Lombok; no usar `@Data`.

## Dependencias y configuración

Mismas versiones autorizadas: Java 25, Boot 4.1.1, Maven Wrapper 3.10.0, MySQL 8.4.8. Wrapper oficial 3.3.4. Runtime: Web MVC, Data JPA, Connector/J, starter Flyway, `flyway-mysql` y Actuator. Test: starters de test y Web MVC, `spring-boot-testcontainers`, módulos MySQL y JUnit Jupiter de Testcontainers. Las dependencias se gestionan con el parent de Boot, sin overrides.

Docker usa las mismas imágenes Temurin de Catálogo: `25.0.2_10-jdk-noble` para build y `25.0.2_10-jre-noble` para runtime, con usuario sin privilegios. Compose tiene servicios `appointment`/`mysql`, red y volumen propios; espera el healthcheck de MySQL. Puertos publicados solo en localhost: 8081 para Java y 3307 para MySQL; dentro de Docker se conservan 8080 y 3306.

Las variables conservan los nombres de Catálogo, con valores propios. `.env.example` deja contraseñas vacías; `.env` permanece ignorado y fuera del contexto Docker. Compose interpola `.env`; el proceso Java local recibe variables explícitas y no necesita `MYSQL_ROOT_PASSWORD` ni carga `.env` automáticamente.

## Estrategia de validación

| CA | Comprobación y defecto que detecta | Entorno |
| --- | --- | --- |
| 01 | Wrapper compila/empaqueta; arranque real en ambos modos. Detecta build o wiring inválido. | Java 25, Docker |
| 02 | SQL y dato de test e historial Flyway aplicados una vez. Detecta conexión incorrecta o Flyway deshabilitado. | MySQL 8.4.8 temporal |
| 03 | Confirmar dato ficticio en tabla de comprobación, recrear contenedores sin borrar volumen y consultar de nuevo. Detecta almacenamiento efímero. | Compose de comprobación aislado |
| 04 | Construir y levantar Compose, consultar health. Detecta imagen, red o arranque incorrectos. | Docker |
| 05 | Revisar ignorados, contexto, respuestas y logs capturados con contraseña ficticia. Detecta exposición de secretos. | Archivos, HTTP y tests |
| 06 | Reproducir README y verificar que SQL/clases de test no se empaquetan. | Compose y Java local |
| 07 | Arranques con cada variable obligatoria ausente/en blanco y puerto inválido. Detecta defaults o validación insuficiente. | Contexto real aislado del entorno del usuario |
| 08 | Arrancar contra puerto inaccesible, con límite temporal y sin secretos en diagnóstico. Detecta arranque falsamente exitoso. | Contexto real |
| 09 | HTTP 200 con solo estado UP y 404 en env/discovery. Detecta exposición de detalles o endpoints de Actuator. | HTTP real y MySQL temporal |

`test-design-first` guía estas pruebas: se reutilizan los escenarios observables de Catálogo, no mocks de persistencia. Comando previsto: `./mvnw -B -ntp verify`, sin `.env` ni servicios centrales. No hay pruebas previas de Turnos. Resultados en TASKS; las comprobaciones de Compose no se sustituyen por la suite. No se valida Windows ni integración real en #3. Actions, cobertura y Sonar se validarán en #17 tras publicar, distinguiendo PR y main; ahora no se declaran completados.

## Orden de implementación

1. Incorporar build, wrapper y las clases técnicas/configuración → compila con las versiones autorizadas.
2. Incorporar Compose y pruebas equivalentes → suite ejecutada con MySQL temporal, sin omisiones.
3. Verificar Compose, conservación del volumen y Java local → CA restantes demostrados.
4. Documentar y revisar archivos → README reproducible y evidencia breve en TASKS.

## Riesgos y decisiones pendientes

Sin decisiones pendientes para este setup equivalente. Riesgos concretos: confundir host/puerto dentro y fuera de Docker, mezclar bases/volúmenes y aceptar arranque sin configuración. Se previenen mediante valores propios y las comprobaciones anteriores. Si una versión no pudiera resolverse o surgiera un cambio funcional, informar antes de sustituir el stack o ampliar el alcance.

# TASKS: Inicializar backend de Turnos y reservas

**Referencias:** [SPEC](SPEC.md), [PLAN](PLAN.md).
**Autorización:** 2026-10-09, setup equivalente al de Catálogo; sin commits, push ni merge.
**Estado:** Implementado y validado localmente; publicación e integración del PR pendientes.

## Tareas y dependencias

- [x] **T-01 · Build y arranque**
  - Alcance: Maven/Wrapper, dependencias y dos clases técnicas con Javadoc.
  - Dependencias: ninguna. Referencias: RF-01, RF-02, RF-04, RF-06; CA-01, CA-02, CA-05, CA-07, CA-08.
  - Validación: wrapper y compilación; arranque verificado tras T-03.
- [x] **T-02 · Persistencia, configuración y Compose**
  - Alcance: YAML, health, Dockerfile, Compose, variables externas y volumen propio.
  - Dependencias: T-01. Referencias: RF-02, RF-03, RF-04, RF-06, RF-07; CA-02 a CA-05, CA-07 a CA-09.
  - Validación: configuración y contexto Docker; ejecución real en T-04.
- [x] **T-03 · Pruebas de integración**
  - Alcance: migración exclusiva de test, MySQL Testcontainers, health, configuración inválida y base inaccesible.
  - Dependencias: T-01, T-02. Referencias: CA-01, CA-02, CA-05, CA-07, CA-08, CA-09.
  - Validación: `./mvnw -B -ntp verify`, pruebas descubiertas, sin omisiones.
- [x] **T-04 · Ejecución y persistencia del volumen**
  - Alcance: Compose aislado, dato ficticio confirmado, recreación y Java local con MySQL Docker.
  - Dependencias: T-02, T-03. Referencias: CA-01 a CA-06, CA-09.
  - Validación: health, SQL, permisos y JAR sin recursos de test; limpiar solo recursos propios de comprobación.
- [x] **T-05 · README y revisión final**
  - Alcance: comandos reales, configuración y límites del setup; evidencia de las tareas.
  - Dependencias: T-03, T-04. Referencias: CA-05, CA-06.
  - Validación: reproducir instrucciones, referencias e ignorados; estado Git sin commits ni push.

## Evidencia

- Comparación con Catálogo: mismo stack y comportamiento; adaptaciones de identidad y puertos documentadas en PLAN.
- Entorno: Java Temurin 25.0.2, Docker 29.8.2 y Compose v5.6.0, el 2026-10-09. Maven Wrapper e imágenes equivalentes a Catálogo.

| CA | Evidencia local observada |
| --- | --- |
| 01, 02, 07, 08, 09 | `./mvnw -B -ntp verify`: BUILD SUCCESS, 10 pruebas, cero fallos/errores/omisiones. MySQL 8.4.8 temporal, SQL de prueba e historial Flyway, health mínimo y arranques negativos sin contraseña en diagnóstico. |
| 02, 04, 09 | Compose `config --quiet` y `up --build -d --wait` en proyecto aislado `prog2-appointment-setup-check`; health HTTP 200 con solo estado UP. SQL confirmó MySQL 8.4.8, permisos limitados a `appointment` y ausencia de `setup_probe` de test. Logs confirmaron arranque y conexión correctos. |
| 03 | Dato ficticio confirmado en `setup_volume_probe`, recreación de ambos contenedores mediante `up -d --force-recreate --wait`, consulta del mismo dato y eliminación de la tabla de comprobación. |
| 05, 06 | Backend de Compose detenido; `./mvnw -B -ntp spring-boot:run` local con variables externas y MySQL Docker, sin contraseña root. Health correcto; logs sin las contraseñas ficticias. JAR sin SQL ni clases de test; referencias, wrapper e ignorados revisados. |

La comprobación usó puertos libres y credenciales ficticias temporales fuera del repositorio. Se detuvo el proceso Java propio y se eliminaron únicamente los contenedores, red y volumen del proyecto de comprobación, después de demostrar persistencia. No se usaron `.env` ni servicios reales.

No se validó Windows. CI #17, análisis remoto e integración con servicios reales no forman parte de esta evidencia. Sin commits, push ni merge; Catálogo se consultó sin modificarlo. La SPEC de #17 está separada en `docs/features/ci-calidad/` y no pertenece al commit de #3.

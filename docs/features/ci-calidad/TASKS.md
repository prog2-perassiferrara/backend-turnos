# TASKS: CI y calidad

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementado y validado localmente; validación remota pendiente.
**Aprobación y autorización:** PLAN aprobado el 2026-10-10; implementación autorizada por el usuario.

## Tareas y dependencias

- [x] **T-01 · Configurar y comprobar Maven**
  - **Objetivo y alcance:** JaCoCo y SonarScanner en `pom.xml`, sin cambiar Java ni las pruebas existentes.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-02, RF-03, RF-04; CA-02, CA-03, CA-04.
  - **Validación:** `clean verify`, resultados de pruebas, XML/HTML y resolución del plugin Sonar sin publicar análisis.

- [x] **T-02 · Crear y validar workflow**
  - **Objetivo y alcance:** CI solo hacia main, con `edited`, permisos mínimos, token limitado al scanner y excepción de forks.
  - **Dependencias:** T-01 para el build; revisión estática independiente.
  - **Referencias:** RF-01, RF-04, RF-05; CA-01, CA-04, CA-05, CA-07.
  - **Validación:** actionlint, revisión de eventos/SHA, propagación de errores y diagnóstico con token vacío.

- [x] **T-03 · Documentar y revisar cambios locales**
  - **Objetivo y alcance:** README con responsabilidades, comandos, reportes y límites de PR apilados; evidencia en TASKS.
  - **Dependencias:** T-01, T-02.
  - **Referencias:** RF-06; CA-06.
  - **Validación:** enlaces, diff y coherencia entre SPEC, PLAN e implementación.

- [ ] **T-04 · Verificar PR hacia main y Sonar**
  - **Objetivo y alcance:** consultar Actions/logs/checks, cobertura importada, hallazgos y Quality Gate para el commit/PR publicados. Comprobar `edited` al cambiar base y al editar título/descripción.
  - **Dependencias:** T-01 a T-03, publicación del usuario y destino main tras integrar #18.
  - **Referencias:** RF-01, RF-03 a RF-06; CA-01, CA-03 a CA-07.
  - **Validación:** enlaces a ejecuciones y análisis reales. Secreto cargado y Automatic Analysis desactivado, ya confirmados por el usuario; no inspeccionar el token.

- [ ] **T-05 · Confirmar ejecución en main**
  - **Objetivo y alcance:** comprobar workflow y análisis por push a main después de integrar CI.
  - **Dependencias:** T-04 y merge realizado por el usuario.
  - **Referencias:** RF-01, RF-03, RF-06; CA-01, CA-03, CA-06.
  - **Validación:** enlace a ejecución y análisis de main, separado de la evidencia del PR.

## Evidencia

- `./mvnw -B -ntp clean verify`: BUILD SUCCESS el 2026-10-10, 11:04:31 -03:00; diez pruebas, cero fallos, errores u omisiones. Logs en `/tmp/turnos-ci-verify.log`; Surefire en `target/surefire-reports/`. Docker autorizado y MySQL 8.4.8 temporal de Testcontainers, sin `.env` ni servicios reales.
- JaCoCo 0.8.15: XML válido e HTML en `target/site/jacoco/`, dos clases. Líneas: 8 cubiertas y 2 sin cubrir (80 %); ramas: 4 cubiertas y 0 sin cubrir (100 %). Son métricas locales del setup, no cobertura de funcionalidades futuras ni importación remota demostrada.
- Logs revisados: fallos de arranque esperados por las pruebas negativas; advertencias de carga dinámica de Byte Buddy y un HTTP 401 al consultar metadata opcional de MavenFeed. No impidieron el build ni la generación de reportes; no se alteró configuración ajena para ocultarlas.
- `help:describe` del plugin `org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211`, goal `sonar`: BUILD SUCCESS. Plugin resuelto sin credenciales ni publicación de análisis; log en `/tmp/turnos-ci-scanner-plugin.log`.
- Workflow validado sin hallazgos por actionlint 1.7.12, descargado de su release oficial en `/tmp` y verificado con SHA-256. SHA de checkout v7.0.1 y setup-java v6.0.1 comprobados mediante la API oficial de GitHub.
- Revisados filtro main, `edited` sin condiciones especiales, concurrencia general, forks sin secretos, ausencia de servicios MySQL adicionales y propagación de errores. Bloque del scanner aceptado por `bash -n`; ejecutado con token vacío terminó con código 1 y diagnóstico antes de invocar Maven. No se probó un token real ni un Quality Gate incumplido.
- Enlaces locales de README/SPEC/PLAN/TASKS comprobados y `git diff --check` sin errores. Código Java y Catálogo sin modificaciones. No se hicieron operaciones Git que modifiquen estado.
- GitHub CLI no encontró PR ni ejecuciones de Actions para esta rama al consultar el 2026-10-10. No existe evidencia remota de los cambios locales; hallazgos, cobertura importada y Quality Gate de Sonar quedan pendientes para el commit publicado.
- Un PR dirigido a la rama de #3 no activa este workflow. La validación de Actions/Sonar requiere publicar y apuntar a main; el push a main se verifica después del merge.

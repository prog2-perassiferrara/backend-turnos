# PLAN: CI y calidad con GitHub Actions y SonarQube Cloud

**SPEC de referencia:** [SPEC.md](SPEC.md).
**Versión revisada:** 2026-10-10; seis RF y siete CA, CI solo hacia main con edited sin filtros adicionales.
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->
**Aprobación:** confirmada por el usuario el 2026-10-10 («lo veo bien»). Implementación autorizada previamente y ratificada al pedir finalizar para revisar y publicar.

<!-- PARA LA PERSONA
Este documento define la solución técnica de la SPEC aprobada. Revisar antes
de derivar TASKS.md; la autorización de implementación no se deduce de su estado.
-->

<!-- PARA EL AGENTE
- Leer instrucciones, SPEC y referencias verificadas; Mobile Guidelines no corresponde.
- Reutilizar Catálogo, consultar diferencias con impacto real y evitar diseños especulativos.
- Referenciar RF/CA sin duplicarlos; conservar secciones y comentarios proporcionales.
- Si cambia el comportamiento, volver a SPEC y confirmar el cambio.
- No marcar el PLAN aprobado sin confirmación ni implementar sin autorización.
- Respetar la prohibición de operaciones Git que modifiquen estado.
-->

## Contexto técnico verificado

Al comenzar #17, Turnos tiene Maven/Wrapper, Java 25, Boot 4.1.1, MySQL 8.4.8/Testcontainers y diez pruebas de setup, sin workflow ni plugins de cobertura/análisis. La rama elegida por el usuario, `17-integrar-github-actions-y-sonarqube-para-pruebas-y-calidad`, parte de `fc1af8e`, publicado en el PR #18 de #3.

Referencia de consulta: [CI de Catálogo](../../../../backend-catalogo/docs/features/ci-calidad/PLAN.md), su workflow y POM. Se conservan sus herramientas y separación build/análisis. Según `hexagonal-arch`, este cambio no afecta capas, APIs ni persistencia: no requiere clases, slices ni diagramas. No introduce patrones de diseño de negocio.

## Solución propuesta

Un workflow con un job en `ubuntu-24.04`, Java Temurin 25, historial completo y caché Maven. Secuencia:

1. `./mvnw -B -ntp clean verify`: compilar, ejecutar las pruebas existentes y generar cobertura XML/HTML con JaCoCo.
2. Ejecutar `./mvnw -B -ntp sonar:sonar` en otro paso del mismo job, usando las clases y el XML generados. El token se entrega solo a ese paso. En forks, omitir explícitamente el análisis por ausencia de secretos, como en Catálogo.

El workflow acepta únicamente pushes a `main` y PR hacia `main`, con `types: [opened, synchronize, reopened, edited]`. No agrega condiciones para distinguir ediciones de título, descripción o base: todas las ediciones hacia main pueden repetir CI. No publica artefactos descargables; el HTML/XML local sigue disponible en `target/site/jacoco/` y Sonar importa el XML en CI.

Testcontainers administra MySQL. No se ejecuta Compose ni se agrega otro servicio de base al workflow. Quality Gate informativo mediante `sonar.qualitygate.wait=false`; no usar `continue-on-error`.

## Módulos y componentes afectados

No hay módulos nuevos: se conserva el proyecto Maven existente.

| Ruta | Acción y responsabilidad | RF |
| --- | --- | --- |
| `pom.xml` | Agregar JaCoCo, SonarScanner e identificadores públicos propios. | 02, 03, 04 |
| `.github/workflows/ci.yml` | Crear eventos hacia main, entorno, build y análisis con excepción de forks. | 01 a 05 |
| `README.md` | Explicar ejecución, informes locales, Sonar y límites de PR apilados. | 06 |
| `docs/features/ci-calidad/` | Conservar seguimiento y evidencia local/remota separados. | 06 |

No cambia Java ni se crean pruebas Java para comprobar YAML. Catálogo y KMP permanecen sin modificaciones en esta etapa.

## Datos y contratos

- Entradas: commit, evento GitHub y secreto `SONAR_TOKEN` solo para análisis habilitados.
- Salidas: reportes Surefire, XML/HTML de JaCoCo y análisis Sonar asociado al commit/PR. El HTML/XML se consulta localmente; la cobertura publicada por CI se consulta en Sonar. No se agrega upload-artifact.
- Sonar: organización `prog2-perassiferrara`, projectKey `prog2-perassiferrara_backend-turnos`, URL `https://sonarcloud.io`. Identidad pública previamente verificada; token cargado y Automatic Analysis desactivado, confirmados por el usuario. No pedir valores ni nuevas confirmaciones.
- `sonar.coverage.jacoco.xmlReportPaths` apunta a `${project.basedir}/target/site/jacoco/jacoco.xml`.
- No cambian contratos HTTP, datos del servicio ni migraciones.

## Estado, operaciones y errores

<!-- Usar los eventos y la concurrencia de Catálogo, añadiendo edited. -->

| Evento | Pruebas/cobertura | Sonar |
| --- | --- | --- |
| Push a `main` | Ejecutar. | Ejecutar. |
| PR interno hacia `main`: opened/synchronize/reopened | Ejecutar. | Ejecutar. |
| PR hacia otra rama | No activa el workflow. | No ejecuta. |
| PR cambia su destino a `main`: edited | Ejecutar con nueva base. | Ejecutar para PR interno. |
| PR hacia `main` cambia título/descripción: edited | Ejecutar. | Ejecutar para PR interno. |
| PR de fork hacia `main` con evento habilitado | Ejecutar sin secretos. | Omitir por ausencia de secretos. |

Conservar la concurrencia de Catálogo por workflow/referencia, con `cancel-in-progress: true`, y el límite de veinte minutos. Una nueva ejecución, incluso por editar título/descripción, puede cancelar y reemplazar la anterior; se acepta repetir la validación para mantener la configuración simple.

El análisis requiere push a `main`, o PR hacia `main` desde el mismo repositorio. Un token ausente/inválido en un análisis habilitado o un error del scanner hacen fallar el job. No hay reintentos automáticos añadidos; cancelaciones, timeouts y publicaciones fallidas no son éxito. No se cambian reglas de merge ni aprobaciones.

## Dependencias y configuración

- Mismas versiones de Catálogo: JaCoCo `0.8.15`, con `prepare-agent` y `report` en `verify`, solo XML/HTML, sin exclusiones ni mínimos adicionales; SonarScanner for Maven `5.8.0.7211` fijado en POM.
- Mismas acciones de Catálogo: checkout `v7.0.1` y setup-java `v6.0.1`, fijadas por SHA con versión en comentario. Checkout: `fetch-depth: 0`, `persist-credentials: false`; Java: Temurin 25 y caché Maven.
- Permisos `contents: read`. Sin otros secretos, base real ni servicios de cátedra. No modificar la configuración administrativa de GitHub/Sonar.

## Estrategia de validación

| CA | Validación y evidencia prevista |
| --- | --- |
| 01 | Revisar filtro main; comprobar que el PR apilado no activa CI y observar ejecución hacia main y por push a main. Registrar base/commit y enlaces. |
| 02 | Ejecutar `clean verify` con Docker/MySQL temporal, sin leer `.env` ni usar servicios reales; comprobar las diez pruebas sin omisiones. |
| 03 | Revisar XML/HTML locales y contadores JaCoCo; comprobar importación en Sonar cuando el PR apunte a main. |
| 04 | Revisar códigos de salida, ausencia de `continue-on-error` y Gate informativo; consultar logs y checks reales. Un Gate fallido no disponible queda sin comprobar, sin provocar defectos artificiales. |
| 05 | Revisar condiciones de secretos/forks y confirmaciones administrativas registradas, sin mostrar el token. |
| 06 | Revisar README y referencias; registrar en TASKS evidencias locales, del PR y posteriores al merge por separado. |
| 07 | Revisar sintaxis y matriz de eventos; en GitHub comprobar que cambiar destino a main y editar título/descripción hacia main activan CI. |

Comando existente: `./mvnw -B -ntp verify`, ya verificado para setup. Tras incorporar plugins, ejecutar `./mvnw -B -ntp clean verify` y comprobar `target/site/jacoco/jacoco.xml` e `index.html`; resolver el plugin Sonar sin usar credenciales locales. La validación estática del workflow no demuestra ejecución remota.

No hacen falta nuevos tests de negocio, dispositivos ni otra prueba de persistencia Compose: este issue no cambia esos comportamientos. La evidencia remota requiere que el usuario publique el PR; Sonar necesita además que apunte a `main`. El push a main se comprueba después del merge. No declarar #17 terminado antes de estas comprobaciones.

## Orden de implementación

1. Tras aprobación del PLAN, derivar TASKS con dependencias y CA.
2. Agregar plugins y workflow → `clean verify`, cobertura y revisión de sintaxis/condiciones.
3. Documentar y revisar → README y evidencia local; el usuario publica el PR contra la rama de #3, todavía sin CI remoto.
4. Después de integrar #18 y retargetear a main, consultar Actions, logs, hallazgos, cobertura y Gate de Sonar. Tras integrar CI, comprobar push a main.

## Riesgos y decisiones pendientes

- Un PR apilado contra #3 no ejecuta CI; se valida localmente hasta cambiar el destino a main. Esto respeta Sonar Free sin automatización especial. Las métricas remotas deben interpretarse junto a la base y commit analizados.
- `edited` también repite CI al editar título/descripción y puede reemplazar una ejecución anterior: costo aceptado por el usuario para evitar filtros adicionales. No declarar validación remota completa mientras el PR no apunte a main.
- No quedan decisiones funcionales. Este PLAN cuenta con aprobación explícita propia; no se trasladan aprobaciones de la SPEC ni de Catálogo.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprobar cobertura de RF/CA, archivos reales, límites y validación local/remota.
Tras aprobación, derivar TASKS sin marcar evidencia futura como completada.
-->

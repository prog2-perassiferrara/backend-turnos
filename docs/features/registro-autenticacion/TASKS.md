# TASKS: Registro y autenticación de usuarios

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementación y validación local completadas el 2026-10-23, incluida la corrección de timeout y la comprobación autorizada del Compose de desarrollo. Límites de calidad en E-04; alcance de la comprobación adicional de Catálogo en E-07.
**Aprobaciones:** SPEC el 2026-10-20; ajuste de contraseña, PLAN y TASKS el 2026-10-23.
**Autorización de implementación:** explícita del usuario el 2026-10-23 («apruebo tasks. implementa»).
**Alcance:** CU-01/#6 y CU-02/#5, con P-01/P-02 confirmadas. Git y publicación a cargo del usuario.

**Chequeo previo (2026-10-23):** 10 RF cubiertos por 12 CA, todos con validación en PLAN y tareas asociadas; sin alcance añadido ni decisiones bloqueantes. Repetir solo la parte afectada si cambia lo aprobado antes de implementar.

## Tareas y dependencias

- [x] **T-01 · Definir contratos y hashing de contraseña**
  - **Objetivo y alcance:** modelos y puertos del slice `user`, excepciones según su responsabilidad y adaptador BCrypt con costo inicial 10. Incorporar las dependencias acordadas y wiring por constructor, sin framework en dominio ni exposición de secretos en representaciones de texto. Javadoc en español para responsabilidades y contratos.
  - **Dependencias:** autorización explícita de implementación.
  - **Referencias:** RF-01, RF-03, RF-08, RF-09; CA-02, CA-04. PLAN: componentes y «Contraseña y JWT».
  - **Validación:** encoder real, sal diferente para la misma contraseña, coincidencia exacta y rechazo al alterar el final de un valor de 72 bytes, también Unicode. Comprobar el costo en el entorno sin reducirlo para pruebas. El rechazo HTTP del exceso corresponde a T-04.

- [x] **T-02 · Persistir cuentas e identificadores de forma atómica**
  - **Objetivo y alcance:** migración V2, entidad, colecciones de identificadores/autoridades, mapper, repositorio y adaptador con almacén transaccional separado. Garantizar unicidad global normalizada, commit antes de devolver el alta y traducción segura de fallos; conservar V1 de prueba y MySQL propios.
  - **Dependencias:** T-01.
  - **Referencias:** RF-02, RF-03, RF-06, RF-07; CA-03, CA-05, CA-07, CA-08. PLAN: persistencia y errores.
  - **Validación:** MySQL temporal real, colisiones simples/cruzadas en ambos sentidos y mayúsculas, coincidencia dentro de la misma cuenta y altas concurrentes con transacciones independientes. Conflictos no dejan cuentas/autoridades parciales ni modifican otra cuenta. Distinguir conflicto único de indisponibilidad; comprobar lecturas sin entidades LAZY fuera de transacción.

- [x] **T-03 · Implementar registro y autenticación con JWT de usuario**
  - **Objetivo y alcance:** dos casos de uso, alta activa con permisos comunes, verificación de credenciales/activación y emisor JWT RS256. Reutilizar privada y reloj de configuración raíz; conservar el firmante de servicio y la separación de identidades. Aplicar 24 horas o 30 días con «recordarme» sin renovación.
  - **Dependencias:** T-01 y T-02.
  - **Referencias:** RF-03 a RF-10; CA-01, CA-04, CA-06, CA-09 a CA-12. PLAN: solución, contratos y JWT.
  - **Validación:** cuentas activas/inactivas/inexistentes, usuario/email sin distinguir mayúsculas, contraseña errónea y fallos de repositorio/firma sin JWT. Verificar firma y claims con RSA efímera y reloj controlado; identidad, permisos, audiencias y duración correctos, sin cambios de perfil ni permisos tras login.

- [x] **T-04 · Exponer los dos contratos HTTP y sus errores**
  - **Objetivo y alcance:** DTO, validación compartida del máximo de 72 bytes UTF-8 y mínimo de 6 caracteres en registro/login, controladores, mapper y errores seguros. Registro devuelve 201 sin JWT tras commit; autenticación devuelve `id_token` y cabecera bearer. Conservar los contratos públicos acordados, sin implementar #7 ni APIs futuras.
  - **Dependencias:** T-01 a T-03.
  - **Referencias:** RF-01, RF-03 a RF-05, RF-07 a RF-10; CA-01, CA-02, CA-04 a CA-06, CA-08, CA-10 a CA-12. PLAN: contrato HTTP y estado/errores.
  - **Validación:** HTTP real y MySQL temporal; registro seguido de login automático, formatos/defaults/límites y campos internos manipulados. Rechazar exceso UTF-8 en ambos endpoints, incluido añadir un sufijo a una contraseña válida de 72 bytes; comprobar 400/401/503, `no-store`, ausencia de cookies/sesión y de valores sensibles en errores/logs. Un duplicado no entrega JWT.

- [x] **T-05 · Comprobar recuperación, independencia e interoperabilidad**
  - **Objetivo y alcance:** pruebas de integración del flujo completo, reinicio, fallos de commit y autenticación posterior, más ensayo operativo con JAR/Compose y contraste del JWT con el artefacto existente de Catálogo sin modificar su repo.
  - **Dependencias:** T-02 a T-04.
  - **Referencias:** RF-04 a RF-10; CA-06 a CA-09, CA-12. PLAN: estrategia de validación y prueba operativa.
  - **Validación:** alta confirmada seguida de firma fallida conserva la cuenta y permite login posterior; rollback y commit con respuesta perdida no generan afirmaciones falsas ni duplicados. Cortar proveedores externos ficticios tras arranque no impide registro/login local; reiniciar conserva cuentas. Catálogo valida el JWT de usuario y rechaza el uso del perfil de usuario para entrega técnica, con fronteras temporales acordadas; health mínimo conservado. Usar MySQL temporal, claves efímeras y cátedra ficticia, sin `.env` ni servicios reales.

- [x] **T-06 · Documentar y cerrar la validación local**
  - **Objetivo y alcance:** README con contrato para KMP y comandos reproducibles, Javadoc y diagramas coherentes con el resultado; completar la evidencia por CA y registrar desviaciones. No añadir informes redundantes.
  - **Dependencias:** T-01 a T-05.
  - **Referencias:** RF-01 a RF-10; CA-01 a CA-12. PLAN: regresión, calidad local y cierre.
  - **Validación:** `./mvnw -B -ntp clean verify`, pruebas descubiertas y resultados reales, JaCoCo XML/HTML y regresión del setup/acceso técnico. Revisar secretos, ausencia de privadas en artefactos y `git diff --check`. Comprobar disponibilidad/autenticación/vinculación de Sonar CLI e IDE, ejecutar lo disponible y registrar bloqueos sin presentarlos como éxito. El usuario maneja Git; no hay tarea de publicación o espera de resultados remotos.

## Evidencia

- **E-01, pruebas automatizadas (2026-10-23):** `./mvnw -B -ntp clean verify` terminó `BUILD SUCCESS` a las 09:20:43 -03:00, con **139 pruebas**, cero fallos, errores u omisiones. Regresión existente: 69; nuevas: `PasswordHasherTests` 9, `UserHttpTests` 56 y `UserJwtTests` 5. BCrypt y RSA reales; MySQL 8.4.8 temporal con transacciones independientes. Los fallos de firma/conexión se introducen en las fronteras de prueba, sin sustituir persistencia, commit ni validación HTTP. El escenario de dos cuentas comprueba ID y contraseña propios. Log temporal: `/tmp/turnos-user-close-verify.log`.
- **E-02, ejecución operativa:** JAR y Dockerfile/Compose individual de Turnos, con variables de prueba explícitas, dos MySQL aislados, privada efímera montada de solo lectura y cátedra HTTP ficticia. Registro 201 vacío, login 200, 24 horas/30 días, sin cookies y health exactamente `{"status":"UP"}`. Se detuvo y arrancó el proceso Java; luego se recreó el contenedor, conservando cuenta e ID en el volumen dedicado. No se usaron `.env`, bases de desarrollo ni servicios centrales reales. Procesos, contenedores, redes, volúmenes y claves propios del ensayo eliminados.
- **E-03, interoperabilidad real entre artefactos:** el JAR existente de Catálogo se ejecutó sin modificar su repositorio, con la pública efímera correspondiente. Su ruta inexistente bajo `/api/` respondió 401 sin bearer y 404 con JWT de usuario válido, acreditando aceptación por su cadena de seguridad, sin afirmar búsquedas funcionales. El endpoint técnico respondió 403 al usuario y 200 al perfil de servicio. Firma alterada y JWT vencido dieron 401. Artefacto consumidor SHA-256 `86490b15fd3c14b08fceec30753c00aa06ae28e7364962740f42cd21f0f43fff`. Resultado y logs temporales: `/tmp/turnos-user-runtime-result.json` y `/tmp/turnos-user-runtime-*.log`. No demuestra conectividad real con cátedra ni implementación KMP.
- **E-04, calidad y límites:** JaCoCo local: líneas **454/476 (95,38 %)**, ramas **162/180 (90,00 %)**; slice `user`: líneas 179/187 y ramas 40/42. XML/HTML generados en `target/site/jacoco/`, sin fijar umbrales nuevos ni afirmar importación remota. Sonar CLI 1.10.0 autenticada en la organización existente, proyecto explícito de Turnos: Vortex devolvió **403**, con 35 archivos omitidos; no es un análisis satisfactorio ni cero hallazgos. Tras abrir/actualizar el IDE, puente 64121 HTTP 200: **35 Java nuevos sin hallazgos**; análisis de los **72 Java de src** solo conserva el INFO previo `java:S2143` de `TestIntegrationSupport`. Verificado con `javap` que Nimbus 10.9.1 exige `expirationTime(java.util.Date)`; se conserva `Date.from(Instant)` en esa frontera, sin supresiones ni cambios de estado. Informes: `/tmp/turnos-user-sonar-cli.json`, `/tmp/turnos-user-sonar-ide.json` y `/tmp/turnos-user-sonar-ide-src.json`.
- **E-05, documentación y artefactos:** Javadoc sobre los 31 Java productivos del slice con JDK 25 y `-Xdoclint:all,-missing`, sin errores ni avisos. El JAR contiene V2 y excluye V1 de prueba y PEM/key; imagen sin PEM/key en rutas de aplicación/runtime. Escaneo determinista de secretos y comprobación de espacios correctos. README describe contrato, errores, responsabilidades y límites. Medición exploratoria de BCrypt 10 tras dos calentamientos, mediana de cinco: hash 60,1 ms y verificación 61,2 ms en este entorno; no es un umbral ni garantiza el tiempo en otros equipos. Logs: `/tmp/turnos-user-javadoc.log` y `/tmp/turnos-user-bcrypt-cost.log`.
- **E-06, corrección y regresión de migraciones (2026-10-23):** el timeout del Compose dejó las tres tablas vacías sin registro V2. La conexión propia de Flyway usa lectura de 60 s y credenciales externas explícitas; la aplicación mantiene 10 s y ambas conexiones 5 s para conectar. La primera regresión detectó que faltaban las credenciales de Flyway, se informó al usuario y se corrigió con su confirmación. `./mvnw -B -ntp clean verify` terminó `BUILD SUCCESS` a las **18:46:58 -03:00**, con **140 pruebas**, cero fallos, errores u omisiones. `allowsSlowMigrationWithoutExtendingApplicationQueryTimeout` ejecuta `SELECT SLEEP(11)` contra MySQL temporal: termina por la conexión de migración y agota el timeout por la normal. JaCoCo conserva líneas 454/476 y ramas 162/180. Log: `/tmp/turnos-flyway-timeout-verify-credentials.log`. Sonar IDE sobre los dos archivos de código/configuración modificados devolvió HTTP 200 sin hallazgos; CLI mantiene el bloqueo Vortex 403, con dos archivos omitidos, sin presentarlo como éxito. Informes: `/tmp/turnos-flyway-final-sonar-ide.json` y `/tmp/turnos-flyway-final-sonar-cli.json`.
- **E-07, Compose de desarrollo real, autorizado excepcionalmente (2026-10-23):** se respaldó el esquema, se comprobó nuevamente que cuentas/identificadores/autoridades/historial estaban vacíos y se recrearon únicamente las tres tablas de usuario para ejecutar V2 correctamente, sin borrar el volumen ni agregar reparación automática. Imagen reconstruida, V2 `success=1` y health `UP`. HTTP real comprobó 201 vacío, login por usuario/email y mayúsculas, permisos internos controlados por servidor, 24 h/30 días, ausencia de cookies, duplicados y cruces, errores 400/401 y fronteras UTF-8. Tras recrear solo `appointment`, ambas cuentas ficticias conservaron ID y contraseña; permanecen dos cuentas de prueba en esa base. Resultados sin contraseñas/JWT: `/tmp/turnos-real-user-check-result.json` y `/tmp/turnos-real-user-check.log`; respaldo: `/tmp/turnos-before-flyway-recovery-20261023.sql`. La repetición adicional contra el Catálogo de desarrollo quedó pendiente: `localhost:8080` rechazó conexión y no había un contenedor Catálogo activo. No se levantó ni modificó ese servicio; esta limitación no sustituye la evidencia de interoperabilidad con artefacto aislado de E-03. Sin afirmar implementación KMP, integración central completa ni resultados remotos de estos cambios.

Los resultados de Actions/Sonar remoto que aporte el usuario se tratarán como retroalimentación, identificando la revisión y separándolos de la evidencia local. No constituyen una tarea ni una condición de cierre local; cualquier defecto demostrado requiere la corrección correspondiente.

## Cierre

Se acredita el backend y sus contratos. La orquestación de las dos llamadas desde Android corresponde a KMP y no se presenta como implementada aquí.

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado, contrato HTTP | E-01/E-02: `confirmsActiveAccountBeforeAutomaticLoginAndKeepsTechnicalHealth` y secuencia operativa. |
| CA-02 | Verificado | E-01: entradas inválidas, máximos de perfil y fronteras ASCII/Unicode en registro/login. |
| CA-03 | Verificado | E-01: duplicados/cruces, coincidencia propia y dos altas concurrentes contra MySQL. |
| CA-04 | Verificado | E-01/E-05: BCrypt real, valor completo, representaciones seguras, logs y artefactos. |
| CA-05 | Verificado | E-01: `acceptsMaximumProfileFieldsAndAssignsInternalValues` rechaza control de campos internos. |
| CA-06 | Verificado | E-01: `committedAccountSurvivesSigningFailureAndCanLoginLater` conserva cuenta/ID. |
| CA-07 | Verificado | E-01/E-02: flujo sin llamadas remotas durante caída y cuenta persistente tras nuevo contexto/proceso/contenedor. |
| CA-08 | Verificado | E-01: fallos antes/después del commit real; repetición sin JWT por mera existencia ni alta parcial. |
| CA-09 | Verificado | E-01/E-03: firma, perfil e identidad; consumidor real acepta usuario y deniega entrega técnica; health conservado. |
| CA-10 | Verificado | E-01: usuario/email sin distinguir mayúsculas, perfil sin cambios y dos cuentas con IDs/contraseñas propios. |
| CA-11 | Verificado | E-01: rechazo uniforme de credenciales y 503 ante fallo propio, sin JWT ni datos sensibles. |
| CA-12 | Verificado | E-01/E-02/E-03: 24 h/30 días, reloj controlado, tolerancia de consumidor de 30 s y JWT vencido rechazado. |

**Regresión:** `./mvnw -B -ntp clean verify`, 140 pruebas correctas, 2026-10-23 18:46:58 -03:00. Árbol local de la rama #6 sobre `5e84aaa52a1344561502f4b7b4a175f0e8da23e7`, sin commit de esta implementación. Evidencia local E-01 a E-07; sin resultado de CI/Sonar Cloud de estos cambios.

**Desviaciones del PLAN:** los escenarios SQL/concurrencia se agruparon en `UserHttpTests` para reutilizar el contexto/MySQL y verificar la frontera HTTP real, en vez de otra clase `UserAccountPersistenceTests`; PLAN actualiza esa referencia. El adaptador también comprueba el límite antes de `matches` para proteger llamadas internas. Se añadió la corrección operativa de Flyway documentada en PLAN y comprobada en E-06/E-07, con autorización explícita para recuperar y validar la base de desarrollo. Sin cambios de alcance funcional, contratos, algoritmo, tablas o vigencias aprobadas.

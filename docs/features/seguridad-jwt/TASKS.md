# TASKS: Protección JWT de las APIs de Turnos

**Referencias:** [SPEC aprobada](SPEC.md) y [PLAN aprobado](PLAN.md).
**Estado:** Implementación y verificación local completas. Feedback remoto posterior a cargo del usuario.
**Aprobaciones:** SPEC, PLAN y TASKS aprobados explícitamente el 2026-10-26.
**Autorización de implementación:** explícita del usuario el 2026-10-26 («apruebo. implementa»).
**Alcance:** #7, 8 RF y 8 CA. Git y publicación a cargo del usuario.

## Chequeo previo

2026-10-26: RF→CA→PLAN/TASKS coherente; los ocho RF tienen CA, los ocho CA tienen validación y tareas asociadas, las dependencias no forman ciclos y ninguna tarea amplía el alcance aprobado. Sin decisiones bloqueantes. Este chequeo documental no aprueba TASKS ni autoriza implementar.

## Tareas

- [x] **T-01 · Confianza local y configuración CORS.**
  - **Objetivo:** disponer de la pública correspondiente a la privada ya validada y de orígenes web explícitos, cerrados por defecto.
  - **Alcance:** `JwtSecurityProperties` y `JwtSecurityPropertiesTests`; derivación pública con JDK y validación de orígenes, sin otro archivo de claves. No cambiar emisores ni registrar todavía una cadena HTTP incompleta.
  - **Dependencias:** ninguna tarea previa.
  - **Referencias:** RF-01/RF-08; CA-01/CA-08. PLAN: configuración y confianza.
  - **Validación:** `./mvnw -B -ntp -Dtest=JwtSecurityPropertiesTests test`; pública correspondiente a la privada ficticia, CORS ausente/vacío, orígenes admitidos y rechazo seguro de valores inválidos. Sin claves reales ni red.

- [x] **T-02 · Protección servlet, contrato de usuario y errores.**
  - **Objetivo:** autenticar y autorizar antes de MVC mediante el contrato aprobado.
  - **Alcance:** starters Resource Server productivo/de test; `SecurityConfiguration`, `JwtContractValidator` y `SecurityProblemHandler`. Decoder RS256 local, identidad de cuenta, autoridades, tiempos, política HTTP y CORS. Cadena solo en contexto servlet, estado por petición y errores seguros; conservar registro/login/health y firmas existentes.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01/RF-02/RF-03/RF-04/RF-07/RF-08; CA-01/CA-02/CA-03/CA-04/CA-06/CA-07/CA-08. PLAN: solución, política HTTP y estado.
  - **Validación:** `./mvnw -B -ntp compile`; verificar dependencias gestionadas, ausencia de cambios en dominio/aplicación y de consultas remotas para validar. La compilación no acredita los CA; la verificación de comportamiento continúa en T-03/T-04 antes de considerar completa la protección.

- [x] **T-03 · Pruebas de filtros, permisos e identidad aislada.**
  - **Objetivo:** demostrar con firmas reales que las peticiones rechazadas no ejecutan la operación y que cada usuario mantiene su identidad.
  - **Alcance:** `SecurityHttpTests` y fixture `JwtTestTokens`, con probes registrados exclusivamente en tests. Cubrir firma/algoritmo/claims, fronteras temporales, autoridades vacías o insuficientes, perfiles cruzados, rutas internas/no habilitadas, errores y CORS. Alternar y ejecutar concurrentemente peticiones de dos usuarios sin aceptar IDs/roles libres. Inyección por constructor conforme al PLAN, sin sustituir la autenticación por un principal simulado.
  - **Dependencias:** T-02.
  - **Referencias:** RF-01/RF-02/RF-04/RF-05/RF-07/RF-08; CA-01/CA-02/CA-04/CA-05/CA-06/CA-07/CA-08. PLAN: estrategia de validación.
  - **Validación:** `./mvnw -B -ntp -Dtest=SecurityHttpTests,JwtSecurityPropertiesTests test`; filtros/decoder reales con pares RSA efímeros y reloj fijo. Observar respuesta y ejecución de la operación; coordinar concurrencia con esperas acotadas. Estas pruebas no acreditan propiedad de reservas futuras.

- [x] **T-04 · Regresión de contratos públicos y acceso técnico.**
  - **Objetivo:** incorporar la protección sin alterar registro/login, health ni el consumidor técnico de #4.
  - **Alcance:** adaptar y ampliar pruebas HTTP existentes para las rutas públicas, bearer inválido explícito, despacho de errores y ausencia de cookies/sesión. Ajustar a 401 las solicitudes anónimas a rutas Actuator no expuestas, conforme a la política aprobada. Conservar las comprobaciones de Argon2id, JWT emitidos, transacciones y caída de proveedores.
  - **Dependencias:** T-03.
  - **Referencias:** RF-03/RF-06/RF-07/RF-08; CA-03/CA-05/CA-06/CA-07. PLAN: regresión y conservación de contratos.
  - **Validación:** `./mvnw -B -ntp -Dtest=UserHttpTests,AppointmentApplicationTests,TechnicalAccessHttpTests test`; MySQL temporal y proveedores ficticios existentes, sin `.env` ni servicios centrales. Comprobar que los arranques no web y errores originales se conservan; sin relajar aserciones para ocultar fallos.

- [x] **T-05 · Ejecución documentada, verificación completa y cierre.**
  - **Resultado:** configuración, README/Javadoc, regresión completa, cobertura, revisión del artefacto y análisis IDE de los diez Java modificados terminados; E-04 distingue las herramientas y sus límites.
  - **Objetivo:** dejar configuración reproducible y evidencia por CA, con límites reales.
  - **Alcance:** variable opcional `CORS_ALLOWED_ORIGINS` en `.env.example` y Compose; README/Javadoc sobre confianza, rutas, errores, identidad y límites de los CU posteriores. Regresión completa, calidad local disponible y revisión del artefacto; actualizar evidencia y cierre de este documento.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01 a RF-08; CA-01 a CA-08. PLAN: dependencias, validación y cierre.
  - **Validación:** `./mvnw -B -ntp clean verify`, informes de pruebas/JaCoCo, Javadoc pertinente y `git diff --check`. Comprobar que el artefacto no incluya claves, `.env` ni probes. Usar Sonar CLI/IDE disponibles, sin repetir Vortex 403 conocido ni confundirlo con un análisis aprobado. Si queda una duda de empaquetado/configuración, contrastarla con un ensayo aislado, sin modificar el entorno real del usuario. No repetir pruebas aprobadas salvo cambios, fallos o incertidumbre que lo justifiquen.

El desglose no exige un commit por tarea; la unidad de entrega es una funcionalidad coherente y verificada. No incluye tareas de publicación, espera de Actions/Sonar remoto ni aprobación del profesor. Ese feedback se revisará sobre la revisión publicada correspondiente cuando el usuario lo aporte.

## Evidencia

- **E-01, preparación documental (2026-10-26):** SPEC y PLAN aprobados; TASKS derivado sin implementación. Estructura, enlaces y referencias RF/CA comprobados. No se ejecutaron compilación, pruebas, análisis de código ni ensayos operativos durante este desglose.
- Las evidencias siguientes distinguen comprobaciones locales y feedback remoto. No se trasladan resultados de features anteriores a #7.

- **E-02, implementación y pruebas enfocadas (2026-10-26):** cuatro componentes productivos en infraestructura raíz, sin cambios de negocio, tablas ni emisores. `./mvnw -B -ntp compile` correcto; primera prueba de configuración: 21 casos. La ejecución enfocada final, con recompilación limpia y las dos pruebas adicionales de contexto, aprobó **184 pruebas**, sin fallos, errores u omisiones: seguridad/configuración/CORS, servidor HTTP real, MySQL temporal, registro/login y acceso técnico. El ensayo concurrente usa una barrera y verifica IDs y roles separados. La regresión prueba el bearer emitido por el login durante la caída de proveedores ficticios, sin llamadas centrales adicionales. Log: `/tmp/turnos-jwt-focused.log`.
- **E-03, regresión completa (2026-10-26):** `./mvnw -B -ntp clean verify` terminó **BUILD SUCCESS a las 20:42:38 -03:00**, **244 pruebas**, cero fallos, errores u omisiones. Incluye 23 pruebas de configuración, 48 de filtros, una de CORS cerrado por defecto, 79 de usuarios y la regresión restante de setup/JWT/acceso técnico. JaCoCo: **565/591 líneas (95,60 %)** y **212/234 ramas (90,60 %)**. Los cuatro componentes nuevos cubren 108/110 líneas; no se simularon fallos imposibles del JDK para completar cobertura. Árbol sin commit sobre `ff099f9`; log `/tmp/turnos-jwt-verify.log`, métricas/artefacto `/tmp/turnos-jwt-validation.json`. No se utilizó `.env`, la base de desarrollo ni servicios centrales reales.
- **E-04, calidad local (2026-10-26):** Sonar CLI 1.10.0 autenticada con la organización existente mediante el llavero, sin mostrar tokens. Los escaneos de secretos del código y del conjunto final de documentación/configuración pasaron; log `/tmp/turnos-jwt-secrets-final.log`. El análisis general Vortex mantiene su limitación 403 conocida y no se reintentó. Tras refrescar el proyecto en IntelliJ, el puente IDE `localhost:64121` analizó individualmente los diez Java modificados, incluidos los ocho nuevos: todas las respuestas HTTP 200 y sin hallazgos. Esto resuelve el bloqueo de indexación del puente anterior. Informe `/tmp/turnos-jwt-sonar-ide-current.json`; no hay nuevo análisis remoto de estos cambios.
- **E-05, documentación y artefacto (2026-10-26):** Javadoc de los cuatro componentes nuevos con `-Xdoclint:all,-missing` sin avisos. JAR revisado: contiene las clases productivas de seguridad y solo V2 de usuarios; no hay claves, `.env`, fixtures/probes ni rutas `/api/security-test`. README, variable CORS opcional y Compose documentados sin modificar el entorno real. Explicación solicitada fuera de los repositorios: `z_explicaciones/2. EXPLICACION_turnos-seguridad-jwt.md`, con enlaces al código verificados. Logs `/tmp/turnos-jwt-javadoc.log` y `/tmp/turnos-jwt-validation.json`.
- **E-06, feedback remoto del PR #24 (2026-10-26):** revisión publicada `442963060ec02008bda1c02c0afd768513113af4`, [Actions 40425471086](https://github.com/prog2-perassiferrara/backend-turnos/actions/runs/40425471086) correcto con 244 pruebas e importación JaCoCo. Sonar informó S4502 sobre CSRF y S5778 en la prueba de inmutabilidad; cobertura nueva 96,3 %, duplicación 0 %. Tras revisión del contrato bearer y autorización del usuario, S4502 se marcó como falso positivo con justificación y referencia a las pruebas existentes, sin modificar la seguridad ni excluir la regla. Estado/comentario confirmados y [Quality Gate del PR](https://sonarcloud.io/dashboard?id=prog2-perassiferrara_backend-turnos&pullRequest=24) consultado nuevamente: **OK**, seguridad/fiabilidad/mantenibilidad A. Este resultado remoto corresponde a la revisión publicada; S5778 se corregirá en el análisis posterior a publicar el cambio local de E-07.
- **E-07, corrección local de S5778 (2026-10-26):** la lista se obtiene antes de la lambda, dejando únicamente `add` dentro de la aserción de excepción; se conserva el comportamiento comprobado. `./mvnw -B -ntp clean -Dtest=JwtSecurityPropertiesTests,SecurityHttpTests,CorsDefaultHttpTests test`: **72 pruebas correctas**, cero fallos, errores u omisiones, a las 21:23:58 -03:00; log `/tmp/turnos-pr24-security-tests.log`. Sonar for IDE HTTP 200 sin hallazgos en el archivo modificado, informe `/tmp/turnos-pr24-sonar-ide.json`. No se repitió la suite completa de E-03 ni el ensayo operativo por este ajuste de prueba; sin cambios productivos o de contratos.

## Cierre

Comportamiento funcional y controles locales disponibles verificados; T-01 a T-05 completas. E-04 conserva el límite del análisis CLI y distingue la revisión IDE del feedback remoto posterior. La identidad transversal no sustituye los controles de propiedad que deberán comprobar los CU reales.

| CA | Estado | Evidencia prevista |
| --- | --- | --- |
| CA-01 | Verificado | E-02/E-03: firma/algoritmo, contrato y fronteras temporales; rechazos sin ejecutar la operación. |
| CA-02 | Verificado | E-02/E-03: permisos insuficientes, autoridades vacías y rutas denegadas. |
| CA-03 | Verificado | E-02/E-03: registro/login/health reales, bearer inválido explícito y error interno conservado sin abrir /error externo. |
| CA-04 | Verificado, identidad transversal | E-02/E-03: dos sujetos concurrentes y datos libres no admitidos como actor; E-05 documenta la propiedad pendiente con cada CU. |
| CA-05 | Verificado | E-02/E-03: perfil de servicio/cátedra no admitido y adquisición técnica existente conservada. |
| CA-06 | Verificado | E-02/E-03: errores 401/403 serializados con código, sin bearer ni ejecución de operación rechazada. |
| CA-07 | Verificado | E-02/E-03: validación local durante caída de proveedores ficticios, sin llamadas adicionales, cookies ni sesión. |
| CA-08 | Verificado | E-02/E-03/E-05: CORS real configurado/cerrado por defecto, contexto rechazado ante configuración inválida y transporte documentado. |

**Regresión:** suite completa correcta según E-03. Acceso real a Catálogo/Kafka sigue sin verificarse y no es requisito para ejecutar la validación local de JWT. Sin cambios en datos existentes ni operaciones Git mutables.

**Desviaciones del PLAN:** se agregó `CorsDefaultHttpTests` para comprobar el cierre CORS con un contexto independiente; PLAN lo refleja sin cambiar alcance. No hay nuevos contratos de negocio, migraciones ni endpoints productivos.

# TASKS: Acceso técnico y configuración de cátedra para Turnos

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementación, validación local y acceso central real comprobados; validación remota del código pendiente.
**Aprobaciones:** SPEC, PLAN y TASKS confirmados explícitamente por el usuario el 2026-10-17.
**Autorización de implementación:** explícita el 2026-10-17 («Implementa»). Git y publicación siguen a cargo del usuario.

## Tareas y dependencias

- [x] **T-01 · Corregir los dos avisos de Sonar acordados**
  - **Objetivo y alcance:** constructor privado documentado en `DatabaseConfiguration`, conservando `proxyBeanMethods = false` y el bean estático; recurso sin nombre `var _` en el helper de arranque fallido, conservando su cierre. Preservar DI por constructor y cambios locales previos.
  - **Dependencias:** autorización explícita de implementación; independiente del nuevo slice.
  - **Referencias:** PLAN, orden de implementación 2; regresión del setup, sin nuevos requisitos de integración.
  - **Validación:** pruebas existentes de arranque, configuración/MySQL inválidos y health; comprobar que siguen descubriéndose los diez escenarios. Escaneo de secretos, calidad local y diff. Unidad de commit separada de integración; Git lo maneja el usuario.

- [x] **T-02 · Definir acceso técnico, configuración y firma de servicio**
  - **Objetivo y alcance:** modelos/puertos del slice, configuración externa compartida fuera de slices y firma RS256 con privada externa. Incorporar las dependencias del PLAN sin modificar MVC ni implementar APIs propias de seguridad.
  - **Dependencias:** autorización explícita; puede prepararse tras T-01 para conservar la separación acordada.
  - **Referencias:** RF-01, RF-03, RF-05; CA-01, CA-03, CA-04. PLAN: componentes, datos y dependencias.
  - **Validación:** firma/claims comprobados criptográficamente con pública ficticia, nueva credencial tras expirar la anterior, privada ausente/inválida/pública/débil rechazada al arrancar, diagnósticos sin secretos. Dominio sin imports externos, Javadoc en español y DI por constructor.

- [x] **T-03 · Adquirir, conservar e invalidar una sesión completa**
  - **Objetivo y alcance:** dos clientes HTTP, DTO mínimos, caso de adquisición, acceso local y repositorio en memoria. Validar JWT/configuración antes de publicar; conservar candidato pendiente dentro del ciclo y distinguir errores por origen/HTTP/código. Invalidación condicionada a la sesión afectada y rechazo de tokens centrales previamente rechazados.
  - **Dependencias:** T-02; el coordinador de recuperación se conecta en T-04.
  - **Referencias:** RF-01 a RF-05, RF-07; CA-01 a CA-04, CA-06, CA-07. PLAN: datos y errores.
  - **Validación:** casos de uso, almacenamiento y clientes reales frente a servidores HTTP ficticios; rutas/bearers, objeto sin envoltorio, campos adicionales, estados, identidad incorrecta y respuestas incompletas. Probar `exp` ausente/exacto/vencido, 401/403 sin código, timeout/corte de red y 401 tardío que no invalida una sesión nueva. Ninguna llamada a registro/login central ni a operaciones de reserva.

- [x] **T-04 · Integrar recuperación limitada y ciclo de vida**
  - **Objetivo y alcance:** arranque asíncrono y recuperación a demanda, con un solo ciclo concurrente, tres intentos totales, 5 s entre intentos recuperables, límites HTTP del PLAN y 30 s entre ciclos fallidos. Acceso sin sesión informa indisponibilidad sin esperar; una sesión utilizable sigue disponible durante caídas de Catálogo. Cierre e interrupción seguros.
  - **Dependencias:** T-02 y T-03.
  - **Referencias:** RF-03, RF-06, RF-07; CA-03, CA-05 a CA-07; D-01 = 1B y D-02 = 2A. PLAN: solución y estado.
  - **Validación:** reloj/espera controlados y sincronización determinista, sin sleeps arbitrarios. Comprobar límite exacto de intentos y de los 30 s, demandas simultáneas/continuas sin tareas duplicadas ni extensión de la espera, ausencia de sondeo sin demanda y recuperación tras volver el proveedor. Verificar que un token inválido no se publica durante una carrera, que el cierre cancela la tarea y que no se bloquean listener ni solicitudes.

- [x] **T-05 · Integrar ejecución, documentación y validación local completa**
  - **Objetivo y alcance:** montaje externo de privada en solo lectura, variables de YAML/Compose/ejemplo, README y regresión del contexto con proveedores ficticios. Ajuste posterior solicitado: ambos Compose individuales declaran la red externa compartida, con URL interna automática de Catálogo y MySQL en sus redes propias. Sin empaquetar claves ni ampliar #4 a consumidores Kafka o reservas.
  - **Dependencias:** T-01 a T-04.
  - **Referencias:** RF-05, RF-08; CA-04, CA-08 y regresión de CA-01 a CA-07. PLAN: configuración y validación.
  - **Validación:** `./mvnw -B -ntp clean verify`, resultados descubiertos sin omisiones, XML/HTML JaCoCo y revisión de caminos relevantes; Java local y Compose con MySQL temporal, HTTP ficticio y claves efímeras. Comprobar health mínimo, fallo por configuración obligatoria inválida, URL alcanzable desde Docker, montaje sin escritura, ausencia de clave en imagen/JAR y cierre de recursos propios. Revisar Javadoc, enlaces, secretos y `git diff --check`.
  - **Calidad local:** comprobar autenticación/vinculación/compatibilidad y ejecutar Sonar CLI antes de proponer commit/push; IDE complementario. Registrar cualquier bloqueo o análisis no realizado sin presentarlo como éxito. No instalar hooks, configurar tokens ni aplicar remediaciones automáticamente.

- [x] **T-06 · Verificar consumo entre backends y acceso central real**
  - **Objetivo y alcance:** comprobar el consumidor de Turnos contra el proveedor protegido, con preparación externa de claves/URLs. Distinguir prueba entre backends con cátedra ficticia de acceso real central.
  - **Dependencias:** T-05 y disponibilidad del entorno/configuración correspondiente. El ajuste de red afecta únicamente Compose/README de Catálogo, autorizado por el usuario; no cambia su código Java ni contrato proveedor y no requiere leer archivos privados.
  - **Referencias:** RF-01, RF-02, RF-08; CA-01, CA-02, CA-08.
  - **Validación:** registrar qué servicios/datos participaron y qué respuestas se observaron, sin valores secretos. Una prueba ficticia no acredita acceso a cátedra real; si este no se ejecuta, mantener la limitación explícita. No probar Kafka como si formara parte de #4.

- [ ] **T-07 · Revisar Actions y Sonar tras la publicación**
  - **Objetivo y alcance:** consultar logs/checks del PR, importación JaCoCo, hallazgos, hotspots y Quality Gate del commit publicado; comprobar después el push a main al integrar.
  - **Dependencias:** T-05 y publicación/merge realizados por el usuario; T-06 aporta evidencia independiente del entorno real.
  - **Referencias:** RF-08; CA-08 y controles de calidad del PLAN.
  - **Validación:** enlaces, commit y resultados reales, separados de la evidencia local. Gate informativo; fallos de build/pruebas/scanner no se ocultan. No marcar completa la validación de main antes de su ejecución.

## Evidencia local (2026-10-17)

- **T-01:** `verify` antes de integrar el nuevo slice pasó las diez pruebas de setup, sin fallos, errores ni omisiones. Conservados los cambios previos de DI por constructor. Los avisos originales son `java:S1118` y `java:S1481`; su resolución remota depende del siguiente análisis publicado.
- **T-02 a T-05:** `./mvnw -o -B -ntp clean verify`, con dependencias disponibles en caché, terminó `BUILD SUCCESS` a las 14:51:52 -03:00. **68 pruebas, cero fallos, errores u omisiones:** setup 10, comportamiento 10, HTTP 22, configuración 19 y recuperación 7. Log temporal: `/tmp/turnos-final-verify.log`.
- Las pruebas comprueban firma/claims, rutas y bearer por proveedor; configuración obligatoria y claves inválidas; publicación completa, estados y campos adicionales; expiración exacta/ausente, rechazo central y 401 tardío; timeout/corte sin reintento HTTP oculto; tres intentos, frontera de 30 s, demandas concurrentes, ausencia de sondeo y cierre sin publicación tardía. Usan MySQL temporal, HTTP ficticio, claves efímeras y tiempo/sincronización controlados, sin `.env` ni servicios reales.
- **JaCoCo local:** 275/289 líneas (**95,16 %**) y 120/138 ramas (**86,96 %**), XML/HTML en `target/site/jacoco/`. Son mediciones locales, no resultados importados por Sonar ni un umbral de aceptación.
- **Ejecución local y Compose:** JAR y contenedor de Turnos obtuvieron acceso a través del proveedor real protegido de Catálogo, usando su artefacto existente sin modificarlo, con cátedra HTTP ficticia y bases temporales separadas. El proveedor rechazó acceso anónimo con 401. En ambas modalidades el health fue exactamente `{"status":"UP"}` y se observó `AVAILABLE (NONE)` en el intento de adquisición.
- En esa comprobación solo el proveedor hizo el login central ficticio: un login y dos GET de integración, uno por modalidad; cero rutas imprevistas o bearer incorrectos. No acredita conexión central real ni Kafka. Log: `/tmp/turnos-runtime-final.log`.
- Compose validó `host.docker.internal` contra servicios alcanzables desde el contenedor, montaje de privada externo de solo lectura y acceso de lectura sin escritura para UID 10001. Imagen sin montaje y JAR sin archivos PEM/key. Logs de aplicación/build sin claves, bearer ni contraseñas ficticias. Eliminados los procesos, contenedores, redes, volúmenes y archivos de claves/configuración temporales propios.
- **Ajuste posterior de red:** se conservan ambos Compose individuales, con `prog2-services` externa para las aplicaciones y las redes `default` propias para cada MySQL. Turnos recibe `http://catalog:8080` de Compose aunque su archivo de entorno conserve `localhost` para Java local. Ambos README documentan la creación inicial de la red y la conexión automática en cada `up`; los archivos del Compose conjunto descartado ya no forman parte de la solución.
- **Validación de ese ajuste:** ambos modelos Compose correctos y prueba aislada de los dos backends/MySQL con cátedra ficticia. Health mínimo `UP`, proveedor protegido con 401 anónimo y Turnos `AVAILABLE (NONE)`. Se recreó solo Turnos mediante `up --force-recreate`: recuperó acceso sin `docker network connect` y conservó el mismo contenedor MySQL. Las bases no pertenecieron a la red común ni compartieron sus redes. Un login central ficticio y dos GET de integración, sin rutas/bearer incorrectos; red externa conservada tras ambos `down`. Recursos de prueba limpiados, sin cambiar los proyectos reales ni sus datos. Log: `/tmp/shared-network-runtime-result.log`. No se repitió Maven porque este ajuste solo afecta Compose/documentación, sin cambios Java.
- **Documentación y estructura:** Javadoc generado con JDK 25 y `-Xdoclint:all,-missing`, sin errores ni avisos (`/tmp/turnos-javadoc.log`). Dominio sin dependencias de Spring/Jackson; configuración compartida fuera de slices; sin `@Autowired` ni Lombok `@Data`. Escaneo determinista de secretos y `git diff --check` correctos.
- **Sonar CLI 1.10.0:** autenticación/vinculación comprobadas con la conexión existente. El análisis de las 36 fuentes fue rechazado por Vortex con **403 Forbidden**, con 36 archivos omitidos; no es análisis exitoso ni cero hallazgos. Sin cambios de credenciales, hooks ni estados remotos.
- **SonarQube for IDE:** puente local HTTP 200, 36 Java solicitados. Corregidos los avisos sobre excepciones genéricas/no utilizadas, `var _`, expresión regular y pruebas. El resultado final conserva una observación **INFO `java:S2143`**, revisada: `JWTClaimsSet.Builder.expirationTime` de Nimbus 10.9.1 exige `java.util.Date`; la prueba limita esa conversión a `Date.from(Instant)` y usa `java.time` para el resto. No se añadió supresión. Informe temporal: `/tmp/turnos-sonar-ide-final.json`.

## Evidencia remota anterior y pendientes

- **T-06 completada (2026-10-17):** después de aplicar la red compartida, el usuario informó acceso disponible con sus dos Compose y configuración del entorno central real. Se corroboró en `backend-turnos-appointment-1`, en ejecución, el resultado `Technical access attempt 1/3: AVAILABLE (NONE)` del arranque actual. Completa la evidencia entre backends/cátedra para adquirir JWT y configuración utilizable, sin leer `.env` ni el contenido de sus claves. No demuestra conexión Kafka ni operaciones de reserva, que pertenecen a sus próximos CU.
- **Referencia remota consultada:** [Actions de main](https://github.com/prog2-perassiferrara/backend-turnos/actions/runs/38391166302), commit `3733c22d63bd713835605b246b5b0c351878fd49`, finalizado correctamente. Sus logs muestran diez pruebas y publicación del análisis, anteriores a #4. Sonar remoto conserva los dos avisos originales, cobertura global 85,7 % y `projectStatus.status=NONE`, sin condiciones de Quality Gate; no equivale a un Gate aprobado de esta implementación.
- **T-07 pendiente:** después de que el usuario publique, revisar Actions/logs/checks, cobertura importada, hallazgos/hotspots y Gate del nuevo commit; después, los de main al integrar. Ninguna comprobación remota anterior valida los cambios locales actuales.
- README y PLAN describen los archivos, contratos y límites implementados. SPEC y PLAN conservan sus aprobaciones explícitas. El ajuste posterior autorizado en Catálogo se limita a Compose/README, preservando sus cambios locales previos. No se ejecutaron operaciones Git mutables, commits o publicaciones. El usuario mantiene la separación de la corrección de Sonar y la integración en unidades de commit coherentes.

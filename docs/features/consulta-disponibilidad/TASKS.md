# TASKS: Consultar disponibilidad de turnos

**Referencias:** [SPEC aprobada](SPEC.md) y [PLAN aprobado](PLAN.md), issue #8/CU-03.
**Estado:** Implementación y verificación local completas; feedback remoto posterior sobre la revisión publicada.
**Aprobaciones:** SPEC, PLAN y TASKS aprobados explícitamente el 2026-10-29.
**Autorización de implementación:** explícita del usuario el 2026-10-29 («apruebo tasks. implementa»).

## Chequeo previo

2026-10-29: siete RF con CA; los ocho CA tienen validación en PLAN y tareas asociadas. Dependencias sin ciclos, sin alcance nuevo ni decisiones bloqueantes. Este chequeo documental no aprueba TASKS ni autoriza implementar.

## Tareas

- [x] **T-01 · Contratos propios y reglas de disponibilidad.**
  - **Objetivo:** representar y calcular candidatos/ocupaciones sin dependencias de infraestructura.
  - **Alcance:** modelos, enums, excepciones de puertos y contratos de entrada/salida de `availability/domain/` según PLAN; `AvailabilityRulesTests`. Reglas de agendas completas, habilitación, día, orden, duplicados y vencimiento exacto; conservar horas locales y precedencia de confirmaciones. Sin HTTP, tablas ni modelos de otros slices.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-02/RF-03/RF-04/RF-05; CA-02/CA-03/CA-04/CA-05. PLAN: solución y datos/contratos.
  - **Validación:** `./mvnw -B -ntp -Dtest=AvailabilityRulesTests test`; agendas conocidas, cierre exacto/remanente incompleto, coincidencias y holds antes/en/después de vencer. Comprobar resultados concretos y ausencia de vuelta a medianoche, sin calcular el esperado con el mismo algoritmo.

- [x] **T-02 · Clientes de Catálogo/ocupaciones y configuración externa.**
  - **Objetivo:** obtener datos completos y verificables con las identidades y presupuestos aprobados.
  - **Alcance:** dos adaptadores y DTO externos; `AvailabilityProperties` y `AvailabilityConfiguration`; YAML, variables opcionales de ejemplo y paso en Compose. Fixture de dos proveedores ficticios para rutas de #4/CU-03, `AvailabilityClientTests` y `AvailabilityPropertiesTests`. Usar `ServiceTokenIssuer` y el puerto público `TechnicalAccessUseCase` únicamente en infraestructura; sin modificar sus internals. Conservar los errores, defaults y límites del PLAN.
  - **Dependencias:** T-01.
  - **Referencias:** RF-02/RF-04/RF-06/RF-07 y configuración de RF-01; CA-02/CA-04/CA-06/CA-07/CA-08. PLAN: datos, errores y dependencias/configuración.
  - **Validación:** `./mvnw -B -ntp -Dtest=AvailabilityClientTests,AvailabilityPropertiesTests test`; HTTP y decodificación reales contra servidores ficticios, claves efímeras y tiempos acotados. Verificar rutas/fecha/credenciales por proveedor, ausencia contractual frente a fallo, respuesta incompleta/incompatible, error sin `code`, redirect, corte y cuerpo detenido después de 200. Comprobar una sola petición por consulta, invalidación del JWT central utilizado ante 401 y que parámetros inválidos impiden arrancar. No leer `.env` ni claves reales.

- [x] **T-03 · Orquestación del caso de uso.**
  - **Objetivo:** combinar agenda vigente y ocupaciones, con el reloj y zona de atención aprobados.
  - **Alcance:** `GetAvailabilityUseCaseImpl`, rechazos de aplicación y `GetAvailabilityUseCaseTests`. Validar entrada antes de consultar puertos, distinguir ausencia/deshabilitación, evitar ocupaciones sin candidatos y filtrar con un instante final consistente. Constructor DI y modelos propios; sin estado compartido por consulta ni tokens en aplicación.
  - **Dependencias:** T-01; los puertos se sustituyen en estas pruebas, no el comportamiento del caso de uso.
  - **Referencias:** RF-01 a RF-07; CA-01/CA-02/CA-03/CA-04/CA-05/CA-06/CA-07. PLAN: recorrido y estado/operaciones.
  - **Validación:** `./mvnw -B -ntp -Dtest=GetAvailabilityUseCaseTests test`; comprobar salidas y efectos sobre puertos con Clock controlado. Cubrir fecha pasada/hoy/futura lejana, inicio anterior/igual/posterior al cálculo, cambio de día durante HTTP y ocupaciones vencidas. Comprobar que entrada inválida no invoca proveedores y que solicitudes independientes no comparten resultados.

- [x] **T-04 · API protegida, errores y concurrencia HTTP.**
  - **Objetivo:** exponer el contrato acordado a KMP preservando la seguridad existente.
  - **Alcance:** controlador, respuesta, mapper y `AvailabilityProblemHandler` limitado al slice; `AvailabilityHttpTests`. GET, JSON, horas y errores conforme al PLAN, con `no-store`; reutilizar filtros sin abrir rutas públicas ni alterar autenticación. Probar el recorrido real con proveedores de T-02.
  - **Dependencias:** T-02 y T-03.
  - **Referencias:** RF-01/RF-02/RF-05/RF-06/RF-07; CA-01/CA-02/CA-03/CA-05/CA-06/CA-07/CA-08. PLAN: contrato HTTP y estrategia de validación.
  - **Validación:** `./mvnw -B -ntp -Dtest=AvailabilityHttpTests test`; Spring Boot en puerto aleatorio, MySQL 8.4.8 temporal, cuenta ficticia y JWT obtenido mediante login real. Comprobar 200 con horarios/`[]`, 400/404/503 y rechazos 401/403 sin llamadas salientes; errores sin secretos ni query en `instance`. Coordinar dos consultas distintas con barrera/espera acotada para detectar mezcla de resultados; observar que no se envían escrituras ni se crean holds. La adquisición técnica inicial de #4 se cuenta por separado de las llamadas de cada consulta.

- [x] **T-05 · Regresión, ejecución documentada y cierre.**
  - **Objetivo:** verificar todos los CA y dejar ejecución/contratos reproducibles.
  - **Alcance:** README/Javadoc, suite completa, artefacto, ensayo operativo aislado y calidad local disponible. Actualizar la evidencia y el cierre de este documento. Conservar seguridad, usuarios, integración técnica y health; sin nuevas dependencias, migraciones o cambios en Catálogo/KMP.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01 a RF-07; CA-01 a CA-08. PLAN: regresión, prueba operativa y límites.
  - **Validación:** `./mvnw -B -ntp clean verify`; resultados descubiertos sin omisiones, informes JaCoCo, Javadoc y `git diff --check`. Revisar que el JAR no contenga claves, `.env` ni fixtures/probes. Validar configuración Compose y ejecutar JAR/Compose de Turnos con proveedores ficticios y MySQL descartable. No reconstruir/reiniciar Catálogo ni modificar datos/configuración reales para ese ensayo. Usar Sonar CLI/IDE disponibles comprobando acceso/vinculación; no repetir el Vortex 403 conocido ni presentarlo como éxito. Distinguir resultados locales del feedback remoto posterior.

Las pruebas anteriores se implementaron y ejecutaron según la evidencia siguiente. La unidad de commit no es cada tarea: mantener cambios coherentes y revisables. Git, publicación y revisión remota corresponden al usuario; Actions/Sonar son feedback y no tareas de espera.

## Evidencia

- **E-01, preparación documental (2026-10-29):** SPEC/PLAN aprobados y TASKS derivadas; comprobación de cobertura RF/CA, dependencias, enlaces y ausencia de alcance nuevo. Solo documentación: sin pruebas, compilación, análisis de código ni ensayos operativos de CU-03 ejecutados durante este desglose.
- **E-02, pruebas enfocadas (2026-10-29):** reglas/caso de uso, 22 correctas; enfoque ampliado de reglas/caso/clientes/configuración, 79 correctas; HTTP completo, 18 correctas. La primera ejecución HTTP detectó una expectativa de `instance` incompatible con los errores de filtros existentes; se corrigió la prueba y se conservó su contrato productivo. Cubiertos firma/credencial por proveedor, fechas y vencimientos, respuestas incompatibles, corte/cuerpo detenido, ausencia de reintentos, consulta concurrente y rechazo/readquisición técnica sin reiniciar. Logs `/tmp/turnos-availability-rules.log`, `/tmp/turnos-availability-clients.log` y `/tmp/turnos-availability-http-final.log`.
- **E-03, regresión final (2026-10-29):** `./mvnw -B -ntp clean verify`, **341 pruebas**, cero fallos, errores u omisiones; **BUILD SUCCESS a las 10:03:41 -03:00**. Incluye 97 pruebas nuevas y 244 previas. Una ejecución enfocada intermedia encontró bytecode incremental con marcador `Unresolved compilation`; el `clean verify` final recompiló desde cero y se comprobó ausencia del marcador en las clases nuevas. JaCoCo: **795/824 líneas (96,48 %)**, **306/339 ramas (90,27 %)**. Base `d164a36` con cambios locales sin commit. Log `/tmp/turnos-availability-verify-final.log`; métricas/artefacto `/tmp/turnos-availability-validation-final.json`.
- **E-04, calidad y artefacto (2026-10-29):** Sonar CLI autenticada con la conexión existente y escaneo de secretos del alcance correcto; no se repitió Vortex 403 conocido ni se presenta como aprobado. Sonar for IDE analizó individualmente los 29 Java nuevos; se ajustó una comparación semanal por `.equals()` y los reanálisis finales no tienen hallazgos. Informe agregado `/tmp/turnos-availability-sonar-ide-final.json`. Javadoc de los 23 Java productivos con `-Xdoclint:all,-missing` sin avisos, log `/tmp/turnos-availability-javadoc.log`. Capas y constructor DI comprobados. JAR final con 28 clases productivas de disponibilidad, sin claves, `.env`, fixtures/probes ni sonda SQL de test. README, YAML, ejemplo de entorno y Compose coherentes.
- **E-05, ejecución del artefacto (2026-10-29):** JAR de Turnos con MySQL 8.4.8 descartable y dos proveedores HTTP ficticios, con firmas verificadas y login real: **16 comprobaciones correctas**, incluido modelo Compose con entorno ficticio. Health, horarios libres/ocupados/vacíos, validación, permisos anónimos, profesional inexistente/deshabilitado, catálogo no vigente e indisponibilidad central correctos; cero escrituras a proveedores y cero cruces de credenciales. Resultado `/tmp/turnos-availability-runtime-result.json`. Recursos propios detenidos y privada efímera retirada; sin cambios productivos posteriores al ensayo. No se reconstruyeron/reiniciaron servicios del usuario ni se modificaron datos/configuración reales.
- **E-06, integración real (2026-10-29):** Compose de Turnos reconstruido y levantado con su volumen existente, tras autorización del usuario. Con Catálogo actualizado por el usuario y estado `CURRENT`, la consulta del 2026-11-02 devolvió **200 con seis horarios**, recorriendo agenda vigente y ocupaciones reales de cátedra. Comprobados registro/login, formato ordenado sin duplicados, zona de atención, `no-store`, rechazo anónimo 401 y consultas inválidas 400. Cuenta ficticia temporal retirada al finalizar; sin holds, reservas ni escrituras a proveedores. Catálogo no fue modificado ni reiniciado por el ensayo. Resultado `/tmp/turnos-availability-live-result.json`.

## Cierre

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado | E-02/E-03: 400/401/403 antes de proveedores; entrada y autorización reales. |
| CA-02 | Verificado | E-02/E-03/E-05/E-06: lectura vigente, ausencia/deshabilitación y respuestas incoherentes diferenciadas. |
| CA-03 | Verificado | E-02/E-03: agendas/días, duración/cierre, ambas horas y ausencia de vuelta a medianoche. |
| CA-04 | Verificado | E-02/E-03: límite exacto de vencimiento, confirmación coincidente, duplicados y estados desconocidos. |
| CA-05 | Verificado | E-02/E-03/E-05/E-06: unión ordenada, consultas simultáneas aisladas y solo GET a proveedores, sin holds. |
| CA-06 | Verificado | E-02/E-03/E-05: `[]` válido distinto de falta de datos, error remoto, corte y timeout del cuerpo. |
| CA-07 | Verificado | E-02/E-03: zona/defaults, fecha pasada/hoy/futura, inicio exacto y cambio de día durante HTTP. |
| CA-08 | Verificado localmente | E-02/E-03: JWT por destino y recuperación de #4 con una sesión distinta, sin replay de ocupaciones. |

**Regresión:** E-03 corresponde a la suite completa sobre el estado final; las pruebas y E-05 usan MySQL/proveedores ficticios, sin `.env` ni cátedra real. E-06 verifica por separado la comunicación con Catálogo y cátedra reales, usando la configuración existente sin leer ni exponer sus secretos. No se acredita Kafka ni reservas por CU-03. Feedback remoto de Actions/Sonar pendiente de publicación, sin tarea de espera ni nuevo Gate declarado.

**Desviaciones del PLAN:** GET común en `AvailabilityHttpClient` para evitar duplicar transporte/decodificación, registrado en PLAN; sin cambios de alcance, contrato, recuperación o dependencias. Sin otras desviaciones.

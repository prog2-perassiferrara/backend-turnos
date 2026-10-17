# SPEC: Acceso técnico y configuración de cátedra para Turnos

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Referencia:** [issue #4](https://github.com/prog2-perassiferrara/backend-turnos/issues/4).
**Revisión:** 2026-10-16. Se reutilizan los acuerdos de integración y el [contrato proveedor](../../../../backend-catalogo/docs/features/seguridad-jwt/PLAN.md#entrega-http-confirmada), sin copiar su implementación ni trasladar su aprobación a este documento.
**Decisiones confirmadas:** el usuario eligió 1B (recuperación a demanda) y 2A (conservar sesión central utilizable durante caída de Catálogo).
**Aprobación:** confirmada explícitamente por el usuario el 2026-10-17 («apruebo spec»). No autoriza implementación; el PLAN requiere revisión propia.

<!-- PARA LA PERSONA
SPEC define comportamiento y aceptación; PLAN define la solución técnica.
Esta revisión es documental: no autoriza implementación ni abre otro PR.
Mobile Guidelines no corresponde al backend.
-->

<!-- PARA EL AGENTE
- Leer instrucciones, contratos y referencia de Catálogo; verificar el estado real.
- Distinguir acuerdos previos, propuestas y decisiones pendientes.
- Consultar solo diferencias con impacto; no repetir decisiones ya confirmadas.
- Mantener diseño de clases, endpoints propios y algoritmos en PLAN.
- No aprobar ni implementar por inferencia; respetar autorizaciones posteriores y no modificar el estado de Git.
-->

## Qué construimos y para quién

<!-- Necesidad, destinatario y resultado observable. -->

Preparar el acceso técnico de Turnos a REST y la configuración de sus canales Kafka de cátedra. Catálogo centraliza el login de la cuenta técnica; Turnos recibe el mismo JWT mediante un contrato interno protegido y obtiene la configuración que necesita. No interviene el usuario final de KMP.

Fuentes: [enunciado](../../../../PROJECT_STATEMENT-v1.md), secciones 4.2, 8 y 9; [anexo](../../../../INTEGRATION_REFERENCE-v2.md), secciones 2 a 6, 13 y 15. La cuenta ya se registra manualmente una sola vez.

## Situación actual

<!-- Estado relevante y garantías que deben conservarse. -->

Al elaborar esta SPEC, Turnos tenía setup y CI integrados mediante los PR #18 y #19, sin acceso técnico implementado. Se conservaron los cambios locales previos de inyección por constructor en pruebas. Después se aprobaron PLAN y TASKS y el usuario autorizó implementar el 2026-10-17. El estado y las evidencias actuales se registran en [TASKS.md](TASKS.md).

El contrato proveedor de entrega protegida está disponible como referencia. Esta SPEC define su consumidor en Turnos; no vuelve a diseñar ni modificar ese servicio. La verificación del consumidor distingue el entorno local ficticio del acceso central real.

## Dentro del alcance

<!-- Requisitos con identificadores estables. -->

- **RF-01:** Emitir un JWT propio de servicio para autenticarse ante Catálogo y adquirir desde él el JWT de la cuenta técnica compartida, respetando el contrato proveedor. No registrar otra cuenta ni hacer login técnico independiente desde Turnos. La emisión de esta credencial de servicio no depende de un usuario conectado desde KMP.
- **RF-02:** Recuperar de cátedra la configuración de integración vigente usando ese JWT, respetando la respuesta sin envoltorio de `GET /api/student/integration`.
- **RF-03:** Habilitar el acceso técnico solo con JWT utilizable, integración esperada, estado `PROVISIONED` y configuración necesaria completa. Distinguir `PENDING`, `FAILED` y `REVOKED` sin tratarlos como acceso disponible.
- **RF-04:** Conservar los datos asignados de REST y Kafka necesarios para Turnos: servidores, consumer group, topic de acciones/resultados y topic de envío de información adicional. No utilizar el topic de catálogo ni requerir Redis para administrar holds.
- **RF-05:** Externalizar configuración y secretos, incluida la clave privada para firmar la credencial de servicio; mantener JWT/configuración adquiridos solo en memoria. No entregar la privada ni los JWT de servicio/cátedra a KMP ni incluirlos en logs, archivos versionados o diagnósticos. No guardar ni recibir en Turnos la contraseña de login central.
- **RF-06:** Intentar adquirir acceso al arrancar, con esperas y reintentos finitos ante fallos recuperables. Tras agotar intentos o invalidar la sesión, una operación que necesite acceso puede iniciar un nuevo ciclo limitado, sin reiniciar Turnos. Coordinar solicitudes concurrentes en un único ciclo y limitar la frecuencia entre ciclos, sin reintentos periódicos ilimitados. Conservar una sesión central utilizable durante caídas de Catálogo para operaciones que no requieran información nueva del catálogo. La indisponibilidad remota mantiene el backend activo y las operaciones con datos propios; no permite declarar completadas operaciones centrales inciertas ni iniciar reservas sin información vigente de Catálogo.
- **RF-07:** Distinguir errores de autenticación entre servicios, rechazo del JWT por cátedra, permisos, integración ausente, respuesta inválida y conectividad por estado HTTP y `code`, cuando exista. Ante vencimiento o 401 central, dejar de usar la sesión afectada, sin login alternativo ni repetición automática de operaciones de reserva con efectos.
- **RF-08:** Documentar preparación, responsabilidades, dependencias, diagnóstico y validación, separando pruebas con datos ficticios del acceso real al entorno central.

## Fuera de alcance

<!-- Límites derivados de los issues y acuerdos previos. -->

- Registro/login de usuarios finales y autorización de sus reservas, cubiertos por sus issues.
- Implementar en Turnos el login técnico ya centralizado en Catálogo, modificar su endpoint proveedor o construir aquí el sistema completo de seguridad de Turnos. #4 incluye únicamente la emisión del JWT de servicio necesaria para consumir ese contrato; la protección de APIs propias y los JWT de usuarios pertenecen a sus issues, coordinados con Turnos #7. No se admite una ruta provisional desprotegida.
- Consumir/producir eventos de negocio, conectar consumidores Kafka de reservas, crear holds o reconciliar procesos: se conserva su configuración, pero su ejecución corresponde a los CU respectivos.
- Réplica de catálogo en Turnos, acceso a tablas de Catálogo, Redis para holds o despliegue local de cátedra.

## Flujo de usuario

<!-- Flujo backend; no hay pantallas. -->

1. Quien configura el proyecto proporciona los parámetros externos y la clave privada de servicio correspondiente a la confianza configurada en el proveedor; la cuenta técnica ya existe.
2. Turnos firma un JWT propio de servicio, solicita con él a Catálogo el JWT compartido de cátedra y consulta con este último la integración actual central.
3. Solo publica acceso utilizable cuando verifica RF-03 y dispone de la configuración de Turnos.
4. Ante fallo recuperable, reintenta hasta el límite; ante fallo terminal o agotamiento, conserva la integración no disponible y un diagnóstico sin secretos. El backend sigue activo si su configuración local y MySQL son válidos.
5. Tras agotarse los intentos o invalidarse la sesión, una operación posterior que necesite acceso puede iniciar otro ciclo limitado. Las solicitudes concurrentes comparten el ciclo y respetan una espera entre ciclos, sin exigir reiniciar Turnos ni repetir automáticamente operaciones de reserva con efectos.
6. Si Catálogo cae y la sesión central sigue utilizable, Turnos la conserva para operaciones independientes, como intentar cancelar una reserva existente. Si el JWT vence o cátedra lo rechaza, deja de utilizarlo; solicitar de nuevo acceso no renueva por sí mismo la sesión central del proveedor.

## Datos y reglas de negocio

<!-- Significado, origen y reglas, sin diseñar DTO ni almacenamiento. -->

- Cuenta técnica y usuario final son independientes. `groupId` identifica la integración, no al paciente ni al propietario local de una reserva.
- El JWT propio de servicio identifica a Turnos ante Catálogo; el JWT de cátedra identifica la cuenta técnica ante el servicio central. Ninguno representa al usuario final ni se entrega a KMP.
- Consumir `GET /api/internal/catedra/token` con el perfil y permisos del [contrato JWT de servicio](../../../../backend-catalogo/docs/features/seguridad-jwt/PLAN.md#jwt-propios-propuesta-de-contrato-para-coordinar-con-turnos): firma RS256, emisor y sujeto `backend-turnos`, audiencia `catalog`, `token_use=service`, autoridad `ROLE_CATALOG_INTEGRATION` y vigencia de cinco minutos. No aceptar una clave pública como clave de firma ni inventar un JWT central localmente.
- Firmar una nueva credencial de servicio al solicitar el JWT central, sin requerir login de usuario ni persistir ese bearer. Los cinco minutos limitan esa credencial de servicio; no son la vigencia del JWT de cátedra ni un timeout de inactividad de KMP.
- Interpretar el 200 del proveedor como JSON con `idToken`; distinguir 401 (`AUTHENTICATION_REQUIRED`), 403 (`ACCESS_DENIED`) y 503 (`TECHNICAL_INTEGRATION_UNAVAILABLE`). Respetar `no-store`; el texto de error no determina la clasificación. La entrega del JWT no prueba que el catálogo esté vigente.
- Catálogo conserva las credenciales de login técnico. Turnos no necesita duplicar esa contraseña para adquirir el JWT.
- Los nombres reales de topics y consumer group provienen de la integración asignada, no de ejemplos ficticios ni de valores enviados por KMP.
- JWT y configuración deben pertenecer a la misma integración y ser utilizables como conjunto; una respuesta incompleta no habilita acceso.
- Recibir `PROVISIONED` y configuración no demuestra conectividad efectiva de Kafka, ni vigencia del catálogo, ni sincronización de reservas.
- La caída de Catálogo impide nuevas operaciones que requieren datos vigentes. Por decisión D-02, Turnos conserva JWT/configuración centrales utilizables para operaciones independientes, sin convertir esa sesión en una prueba de vigencia del catálogo ni de éxito de una llamada central. Las consultas resueltas exclusivamente con datos propios siguen disponibles.
- Por decisión D-01, la recuperación posterior al agotamiento o invalidación ocurre a demanda, con un único ciclo de adquisición concurrente y frecuencia limitada. Un nuevo pedido no reinicia continuamente el límite; no hay sondeo periódico permanente. Los límites y tiempos concretos se definen en PLAN.
- Catálogo no renueva automáticamente el JWT vencido/rechazado: su recuperación comienza al reiniciarlo. Turnos no puede resolver esa condición creando otro JWT por su cuenta.
- Un timeout no demuestra que una operación central falló. Este issue prepara acceso; no autoriza a repetir reservas, confirmaciones o cancelaciones sin su reconciliación.

## Comportamiento del backend y casos alternativos

<!-- Casos del servicio; Mobile Guidelines y escenarios de dispositivo no aplican. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Adquisición en curso | No declarar disponible el acceso sin verificarlo. |
| Catálogo no disponible o sesión aún no preparada | Diagnóstico sin secretos y reintentos limitados; no login alternativo. |
| JWT propio ausente, inválido o sin permisos ante Catálogo | Rechazar acceso a la entrega interna; no confundirlo con el JWT de cátedra. |
| Cátedra devuelve `PENDING`, `FAILED` o `REVOKED` | No habilitar integración; distinguir espera de fallo terminal. |
| Respuesta incompleta o integración distinta | No utilizar la sesión recibida. |
| HTTP 401/403 sin `code` | Clasificar por estado, sin interpretar `detail`. |
| Configuración local inválida o MySQL inaccesible | Conservar el fallo de arranque del setup. |
| Límite agotado | Backend activo sin acceso técnico; una operación posterior puede iniciar otro ciclo limitado respetando la espera entre ciclos. |
| Solicitudes concurrentes sin sesión utilizable | Un único ciclo de adquisición; no multiplicar los intentos por cada solicitud. |
| Catálogo cae con sesión central utilizable | Conservarla para operaciones independientes; impedir nuevas reservas sin información vigente. |
| JWT técnico vencido o rechazado | No seguir utilizándolo ni hacer login independiente; recuperación coordinada con Catálogo. |
| JWT propio de servicio vencido por inactividad | Firmar otro al solicitar acceso, sin pedir login a KMP; no confundirlo con un vencimiento del JWT central. |
| Reinicio de Turnos | Descartar sesión en memoria y comenzar una adquisición limitada. |
| Reinicio de Catálogo | Puede cambiar su JWT; el mecanismo para volver a obtener la sesión compartida debe definirse en PLAN conforme a la política acordada. |

## Restricciones del pedido

<!-- Condiciones confirmadas; las elecciones técnicas adicionales van en PLAN. -->

Respetar el contrato proveedor referenciado y el contrato v1 del anexo, la propiedad de datos y la protección JWT entre servicios. Conservar health técnico sin secretos ni afirmaciones de sincronización. Esta actualización solo autoriza documentación: no implementar ni realizar operaciones Git mutables. Las aprobaciones de otras funcionalidades no aprueban este documento.

## Criterios de aceptación

<!-- Resultados observables; pendientes de validación futura. -->

- **CA-01 · RF-01:** Al adquirir o recuperar acceso, Turnos presenta su JWT de servicio conforme al contrato y recibe de Catálogo el JWT compartido; no llama a registro ni login técnico de cátedra ni necesita una sesión de usuario. Una solicitud posterior puede firmar una credencial nueva aunque la anterior haya vencido.
- **CA-02 · RF-02, RF-04:** Con JWT aceptado, interpreta el objeto de integración sin envoltorio y conserva la configuración asignada a Turnos, sin requerir el topic de catálogo ni Redis para holds.
- **CA-03 · RF-03:** Solo una sesión utilizable, de la integración esperada, con `PROVISIONED` y datos necesarios completos habilita acceso; los restantes estados/respuestas no lo habilitan.
- **CA-04 · RF-05:** En éxito/error, reinicio y consultas desde KMP, no se exponen ni persisten los JWT adquiridos; la privada se suministra externamente y no aparece en salidas ni archivos versionados. Una privada ausente, inválida o incompatible no permite declarar acceso preparado, conservando el rechazo de configuración obligatoria inválida. No se duplica la contraseña central.
- **CA-05 · RF-06:** Ante fallos recuperables, el ciclo termina en éxito o al límite; ante fallo terminal se detiene. Tras agotamiento o invalidación, una operación posterior puede recuperar acceso mediante otro ciclo limitado sin reiniciar Turnos. Solicitudes concurrentes no multiplican ciclos y solicitudes continuas no evitan la espera entre ellos; sin demanda no se programa una recuperación periódica. El backend conserva operaciones locales y no presenta como completada una operación central fallida o incierta.
- **CA-06 · RF-07:** Rechazos entre servicios y de cátedra, errores sin `code`, timeouts y respuestas inválidas se distinguen sin depender de `detail` ni filtrar secretos.
- **CA-07 · RF-06, RF-07:** Si Catálogo cae con una sesión central utilizable, Turnos conserva acceso para operaciones independientes, sin habilitar nuevas reservas que requieren datos vigentes. Al vencer o rechazarse el JWT central, deja de entregarlo como utilizable y la recuperación a demanda obtiene acceso desde el proveedor sin login independiente. Renovar el JWT propio de servicio no renueva el JWT central ni vuelve utilizable uno rechazado.
- **CA-08 · RF-08:** La documentación identifica preparación manual única, parámetros, proveedor/consumidor, dependencias de seguridad y evidencia ficticia/real sin presentar acceso pendiente como verificado.

## Cómo se comprueba el comportamiento

<!-- Escenarios y resultados; selección de herramientas y evidencias en PLAN. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Consumir proveedor protegido con firma propia real ficticia y repetir después de vencer la credencial de servicio. | Obtención autorizada, nueva credencial cuando corresponde; sin usuario conectado ni llamadas a registro/login central. |
| CA-02 | Devolver integración ficticia completa con topics propios. | Configuración contractual de Turnos, sin dependencia de Redis para holds. |
| CA-03 | Variar JWT, integración, estados y campos necesarios. | Solo el conjunto válido habilita acceso. |
| CA-04 | Configurar privada válida, ausente e inválida; inspeccionar salidas y almacenamiento tras éxito/error/reinicio. | Configuración inválida rechazada; privada no divulgada ni versionada; JWT no persistidos. |
| CA-05 | Fallo temporal seguido de éxito, agotamiento, fallo terminal, proveedor recuperado y solicitudes simultáneas/continuas. Observar también ausencia de demanda. | Ciclos finitos, recuperación a demanda sin reiniciar, un único ciclo concurrente y espera entre ciclos; sin recuperación periódica permanente. |
| CA-06 | Variar HTTP, presencia de `code`, texto y conectividad. | Clasificación estable sin interpretar texto ni repetir operaciones con efectos. |
| CA-07 | Interrumpir Catálogo con sesión central válida, vencer/rechazar el JWT y recuperar después la sesión del proveedor. | Acceso independiente conservado durante la caída; token inválido no utilizado ni nuevas reservas habilitadas sin catálogo vigente; readquisición a demanda sin login central independiente. |
| CA-08 | Revisar instrucciones y evidencias identificadas por entorno. | Reproducción posible y límites explícitos. |

## Decisiones pendientes

<!-- Resolver antes de aprobar; los parámetros y mecanismos se concretan en PLAN. -->

Ninguna decisión funcional pendiente en esta revisión. Confirmadas el 2026-10-16:

1. **D-01 · Opción 1B:** recuperación a demanda después de agotar intentos o invalidar la sesión, sin reiniciar Turnos, con ciclos limitados, concurrencia coordinada y espera entre ciclos. No implica repetir automáticamente operaciones de reserva con efectos ni hacer login central independiente.
2. **D-02 · Opción 2A:** conservar JWT/configuración centrales utilizables durante una caída de Catálogo para operaciones independientes, como cancelar una reserva existente. No permite utilizar un JWT vencido/rechazado ni iniciar nuevas reservas sin información vigente.

Tras aprobar esta SPEC, el PLAN concretará el consumidor, la gestión externa de la privada y confianza RSA, tiempos/reintentos tomando Catálogo como base, espera entre ciclos y recuperación coordinada según D-01/D-02. Coordinar la emisión compartida con Turnos #7 sin anticipar registro/login de usuarios ni duplicar lógica. No declarar verificada la comunicación entre servicios antes de probarla.

<!-- ANTES DE SOLICITAR APROBACIÓN
Resolver pendientes, comprobar RF/CA y coherencia entre repositorios.
No trasladar aprobaciones ni resultados de pruebas de Catálogo a Turnos.
-->

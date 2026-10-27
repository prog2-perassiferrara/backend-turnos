# SPEC: Protección JWT de las APIs de Turnos

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-10-26. Issue [#7](https://github.com/prog2-perassiferrara/backend-turnos/issues/7).
**Aprobación:** explícita del usuario el 2026-10-26 («apruebo spec»). Habilita preparar el PLAN; no autoriza implementar.

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo según el enunciado y los contratos del backend.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto y los contratos pertinentes. Inspecciona el
  repositorio para comprobar el comportamiento actual.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Considera errores, concurrencia, integración y recuperación cuando apliquen,
  sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
- Para cambios de significado posteriores a la aprobación, registra una nota
  breve de cambio, motivo e impacto según AGENTS.md, sin historial separado.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Proteger las peticiones a Turnos mediante los JWT de usuario que ya emite, estableciendo una identidad autenticada y permisos comprobados antes de ejecutar operaciones protegidas. Conservar registro, login y health públicos, y la separación respecto de las credenciales técnicas.

Fuentes: [enunciado](../../../../PROJECT_STATEMENT-v1.md), secciones 3.2 y 9; [anexo](../../../../INTEGRATION_REFERENCE-v2.md), sección 2. Contratos relacionados: [registro/autenticación](../registro-autenticacion/SPEC.md), [acceso técnico de Turnos](../integracion-tecnica/SPEC.md), [confianza JWT de Catálogo](../../../../backend-catalogo/docs/features/seguridad-jwt/PLAN.md#jwt-propios-propuesta-de-contrato-para-coordinar-con-turnos) y [consultas de agenda/estado en revisión](../../../../backend-catalogo/docs/features/consulta-agenda-estado/SPEC.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Al elaborar esta SPEC, los PR #21 y #23 ya estaban integrados: Turnos registraba cuentas activas, verificaba contraseñas con Argon2id y emitía JWT de usuario de 24 horas o 30 días. El acceso técnico de #4 obtenía el JWT central desde Catálogo usando una credencial propia de servicio. Todavía no existían la protección transversal de las APIs ni operaciones de disponibilidad, procesos o reservas. TASKS registra el resultado de esta implementación.

Catálogo está indisponible durante este trabajo y la incidencia Kafka sigue pendiente. Esto limita el ensayo entre servicios reales, pero la verificación de JWT propios debe poder funcionar localmente. Esta SPEC define el alcance de #7; no declara implementada la autorización de recursos que todavía no existen.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Exigir JWT en las operaciones protegidas y comprobar firma, emisor, audiencia de Turnos, vigencia y contrato de identidad antes de ejecutarlas. No aceptar credenciales de cátedra ni de servicio como sesión de usuario.
- **RF-02:** Autorizar el acceso por perfil y autoridades comprobadas; un JWT válido no concede todos los permisos. Las APIs de usuario requieren el perfil de usuario final y `ROLE_USER`; los accesos no habilitados expresamente permanecen denegados.
- **RF-03:** Mantener públicos únicamente el registro, la autenticación y el health técnico, con sus métodos y contratos actuales. No exigir una sesión previa para obtener otra ni habilitar rutas internas o de negocio por omisión.
- **RF-04:** Hacer disponible la identidad estable del usuario autenticado para las operaciones protegidas. No admitir como identidad o autoridad sustituta valores libres del cuerpo, parámetros o cabeceras adicionales; las peticiones concurrentes de usuarios distintos no comparten identidad.
- **RF-05:** Establecer la regla de propiedad para los CU posteriores: procesos, reservas y el identificador estable enviado como `externalPatientId` deben corresponder al usuario autenticado. Cada CU incorporará su comprobación efectiva de propiedad antes de consultar, modificar o actuar centralmente sobre recursos; #7 no adelanta esas operaciones.
- **RF-06:** Conservar la entrega técnica de Catálogo mediante el JWT propio de servicio ya implementado. Los nuevos clientes utilizarán la identidad y permisos del contrato proveedor al aprobarse, preservando localmente usuario y autorización; no propagar automáticamente su JWT ni sustituirlo por el central. Su implementación corresponde a sus CU, sin exponer credenciales técnicas a KMP.
- **RF-07:** Distinguir autenticación ausente o inválida de permisos insuficientes mediante respuestas 401/403 estables, sin ejecutar la operación protegida ni divulgar secretos. Conservar los errores existentes de registro/login y evitar que el manejo de errores abra accesos externos.
- **RF-08:** Validar y autorizar peticiones localmente, sin depender de la disponibilidad de Catálogo/Kafka ni crear sesiones HTTP de autenticación. Documentar configuración externa, confianza, CORS y transporte con el mismo criterio de Catálogo, según las reglas siguientes.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Disponibilidad, creación/seguimiento de procesos, reservas, cancelación y recuperación: permanecen en #8 a #15. Sus controles de propiedad son obligatorios al implementar cada operación y no se consideran terminados por #7.
- Nuevos endpoints funcionales, administración de usuarios, roles administrativos, refresh/revocación de JWT y cambios al registro/login: se conservan los límites de la [SPEC de identidad](../registro-autenticacion/SPEC.md#fuera-de-alcance).
- Cambiar el proveedor de Catálogo, el ciclo de acceso técnico de #4, desplegar cátedra o corregir su infraestructura Kafka. Este trabajo no modifica otros repositorios ni espera conectividad central para sus pruebas locales.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. KMP obtiene el JWT mediante el login existente y lo presenta en una petición protegida a Turnos.
2. Turnos comprueba la credencial y sus permisos antes de ejecutar la operación. Si falta o no es válida, responde 401; si la identidad es admitida pero no tiene permisos, responde 403.
3. Una petición autorizada dispone del ID estable y las autoridades de su usuario. Valores libres enviados junto a ella no pueden cambiar esa identidad.
4. Al incorporarse un CU que administra recursos propios, este comprobará además que el recurso pertenece a esa identidad; superar la protección transversal no sustituye esa verificación.
5. Registro/login siguen admitiendo solicitudes sin JWT; health conserva su respuesta técnica mínima. Una caída externa no impide validar localmente el JWT ni cambia sus permisos.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

Se conserva el contrato existente de JWT de usuario: emisor `backend-turnos`, audiencia que incluye `turnos`, `token_use=user`, sujeto igual al ID estable de cuenta y autoridades en `auth`. La firma es RS256 y se mantiene la tolerancia temporal acordada de 30 segundos. Emisión y vencimiento son obligatorios y coherentes; se rechaza una emisión futura fuera de tolerancia y se comprueba `nbf` si está presente. Los detalles de validación pertenecen al PLAN.

El ID de cuenta no es login, email ni `groupId`. `externalPatientId` será derivado de esa identidad estable al implementar el CU que lo envía, sin permitir seleccionar otro paciente desde KMP. Los identificadores de procesos o reservas son referencias a recursos, no prueba de propiedad.

| Acceso | Política de #7 |
| --- | --- |
| `POST /api/register` y `POST /api/authenticate` | Públicos, conservando validación, respuestas y ausencia de caché existentes |
| `GET /actuator/health` | Público y mínimo, sin estado de sincronización ni credenciales |
| Operaciones protegidas de usuario | JWT admitido, perfil de usuario y `ROLE_USER`, más los permisos/propiedad exigidos por el CU |
| Accesos internos o no habilitados | Sin exposición pública por omisión; un token no crea una operación inexistente |
| Entrega técnica de Catálogo | Mantener el JWT propio de servicio de cinco minutos; no reenviar uno de usuario para obtener secretos |

Las vigencias de usuario continúan siendo 24 horas o 30 días desde la emisión. La actividad no las extiende. Los JWT no se aceptan desde parámetros de URL ni cookies; se utiliza la cabecera bearer del contrato existente.

El borrador proveedor de agenda/estado distingue consultas del servicio Turnos y consultas de usuarios de KMP. Se referencia para coordinar sus contratos, sin trasladar su aprobación ni fijar aquí permisos, rutas o reenvío de credenciales todavía pendientes. La identidad propia de servicio no sustituye la autorización del usuario en Turnos.

**Política CORS aprobada:** seguir Catálogo, con orígenes web explícitos por entorno y ninguno permitido por defecto, sin comodines ni cookies de autenticación. Android nativo y llamadas entre backends no necesitan habilitar un origen web. CORS no concede permisos ni reemplaza el JWT; un preflight permitido no ejecuta la operación de negocio.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| JWT ausente, alterado, vencido o ajeno al contrato de Turnos | 401, sin ejecutar la operación protegida |
| JWT admitido sin los permisos requeridos | 403, sin efectos de negocio |
| JWT de servicio o de cátedra presentado como usuario | No habilita operaciones de usuario ni expone secretos técnicos |
| Identidad libre distinta de la autenticada | No cambia el usuario efectivo; los CU de recursos rechazarán accesos ajenos |
| Dos usuarios realizan solicitudes simultáneas | Cada petición conserva exclusivamente su identidad y sus permisos |
| Registro/login sin JWT, con datos válidos | Conservan el comportamiento actual, incluso con Catálogo caído |
| Health sin JWT | Respuesta técnica mínima; no acredita disponibilidad de reservas |
| Catálogo, cátedra o Kafka indisponibles | La comprobación local de JWT continúa; cada CU mantiene sus condiciones de disponibilidad |
| Origen web no permitido | Sin autorización CORS; no se amplían accesos ni se ejecuta un preflight como operación |
| Error de aplicación o llamada externa a una ruta de errores | Conservar respuesta saneada sin abrir una vía que evite la protección |
| Reinicio | Mantener la confianza configurada y la validación temporal, sin sesiones HTTP que restaurar ni consulta central para validar el bearer |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Reutilizar RS256, la clave externa de Turnos, perfiles, vigencias y confianza ya acordados; no reabrirlos ni crear otro mecanismo de login. Mantener Argon2id y los contratos de registro/autenticación. No acceder a la base de Catálogo ni consultar servicios remotos para verificar la firma propia.

Conservar el criterio de transporte confirmado entre backends: HTTP solo en desarrollo local/red privada de Compose y HTTPS al exponer servicios fuera de ese entorno. La configuración permanece externa y los fallos obligatorios impiden un arranque inseguro sin mostrar secretos. Los detalles de configuración, protección HTTP y validación se concretarán en PLAN siguiendo `hexagonal-arch`.

Las pruebas deben poder ejecutarse con claves y dependencias ficticias o temporales, sin `.env`, usuarios reales ni conectividad de cátedra. La posterior prueba entre servicios reales se distinguirá de esa evidencia; no se agrega una tarea de espera del profesor, publicación o merge.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Una petición protegida sin JWT o con firma, algoritmo, emisor, audiencia, sujeto, perfil o tiempos incompatibles recibe 401 y no ejecuta la operación.
- **CA-02 · RF-02, RF-07:** Un JWT admitido con los permisos necesarios permite alcanzar una operación protegida; sin ellos recibe 403, en vez de acceso por mera autenticación.
- **CA-03 · RF-03:** Registro y login funcionan sin sesión previa y conservan sus contratos; health sigue público y mínimo. No se habilitan otras operaciones públicas por omisión ni mediante el tratamiento de errores.
- **CA-04 · RF-04, RF-05:** Peticiones de dos usuarios distintos disponen de sus respectivos IDs y autoridades comprobados, también simultáneamente. Datos libres no suplantan al usuario; queda documentada la obligación de aplicar propiedad en cada CU posterior, sin declarar implementadas reservas.
- **CA-05 · RF-01, RF-06:** Los perfiles de usuario, servicio y cátedra no son intercambiables. Se conserva la adquisición técnica protegida de #4 y la documentación referencia los contratos pendientes de nuevas consultas sin inventar permisos ni entregar credenciales técnicas a KMP.
- **CA-06 · RF-07:** Los rechazos 401/403 utilizan errores estables y saneados, sin tokens, claves, valores sensibles ni causas internas y sin efectos de la operación rechazada.
- **CA-07 · RF-08:** Con Catálogo/cátedra/Kafka indisponibles, se comprueba localmente un JWT válido y se rechaza uno inválido, sin llamadas remotas por esa validación, cookies ni creación de sesión HTTP de autenticación.
- **CA-08 · RF-08:** CORS se limita a los orígenes configurados, cerrado por defecto, sin conferir permisos; la documentación conserva límites de transporte, configuración externa y confianza. La configuración obligatoria inválida no habilita un arranque inseguro.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Presentar JWT válidos y variar firma, algoritmo, claims, fechas y ausencia de credencial. | Solo se admite el contrato de Turnos; los casos inválidos producen 401 sin ejecutar la operación. |
| CA-02 | Usar una identidad admitida con y sin autoridad requerida. | Acceso autorizado o 403 sin efectos. |
| CA-03 | Repetir registro/login/health sin bearer y comprobar accesos no habilitados y errores. | Contratos públicos conservados, sin apertura accidental. |
| CA-04 | Alternar y ejecutar simultáneamente solicitudes de dos usuarios, incluyendo datos libres de otro usuario. | Identidades aisladas; referencias libres no cambian al actor. El aislamiento de recursos se verificará con cada CU real. |
| CA-05 | Variar los tres perfiles y comprobar el consumidor técnico existente contra un proveedor controlado. | Separación de credenciales, contrato técnico conservado y ninguna entrega a KMP. |
| CA-06 | Inspeccionar cuerpos, cabeceras y diagnósticos de rechazos. | 401/403 estables sin secretos ni ejecución de la operación. |
| CA-07 | Interrumpir proveedores externos y repetir validación/local login. | Seguridad local independiente, sin autenticación alternativa ni sesión HTTP. |
| CA-08 | Variar orígenes permitidos/no permitidos, preflight y configuración inválida; revisar transporte documentado. | Política explícita sin comodines, permisos independientes de CORS y fallo seguro de configuración. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna decisión funcional pendiente para #7. El usuario aprobó esta SPEC, incluidas las reglas de acceso y la política CORS, el 2026-10-26.

El PLAN concreta la validación, el contexto de identidad y las comprobaciones con operaciones exclusivas de prueba, sin agregar endpoints productivos ficticios. La representación de identidad en cada CU y su comprobación de propiedad se cerrarán con ese CU; los nuevos clientes de Catálogo se desarrollarán con sus contratos funcionales. La aprobación posterior del PLAN/TASKS y la autorización explícita de implementación constan en TASKS.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

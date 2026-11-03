# SPEC: Simplificación estructural de reservas, Etapa 1

> **Registro histórico SDD.** Alcance, diagramas y evidencia de su revisión; no describen necesariamente el sistema actual. Consultar [arquitectura vigente](../../ARQUITECTURA.md), [contratos](../../CONTRATOS.md) e [índice histórico](../README.md).

**Alcance y ejecución:** Etapa 1 autorizada explícitamente el 2026-11-02. Este registro nuevo está pendiente de revisión documental; no atribuye su aprobación por inferencia.

**Estado:** En revisión <!-- Borrador | En revisión | Aprobada -->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Reducir funciones adicionales de reservas: el usuario consulta su progreso local y cancela reservas confirmadas; no solicita abandono compensatorio.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

El árbol de trabajo ya contiene una primera simplificación de fases y recuperación. Esta etapa separada retira el abandono y la certificación remota por lectura, conservando las garantías obligatorias.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Retirar la acción de abandono y su compensación automática.
- **RF-02:** Consultar progreso e historial propios desde el estado durable local, con paginación y pendiente separado. No certificar frescura central por cada lectura.
- **RF-03:** Conservar recuperación, correlación, deduplicación, outbox, checkpoints y cancelación confirmada con sus límites actuales.
- **RF-04:** Mantener autenticación, autorización y contratos de integración con Catálogo y cátedra.
- **RF-05:** Actualizar datos existentes sin fabricar cierres ni transformar abandonos anteriores en cancelaciones.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Etapa 6: wiring general Spring. Las ampliaciones autorizadas de Etapas 2 a 5 se registran al final de este documento.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. El usuario autenticado consulta su proceso o historial; recibe los datos propios persistidos.
2. Los eventos y la recuperación actualizan el progreso sin necesidad de abrir una consulta.
3. Solo una reserva confirmada permite solicitar cancelación. Salir de la app no detiene ni compensa el proceso.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

Se retiran abandono, tipo de intención y certificaciones de lectura. Se mantienen ID local, estado, plazo, solicitud telefónica, nombres históricos opcionales y progreso de cancelación. El aviso de error de una operación de cancelación permanece. Los nombres se muestran si ya fueron obtenidos por reconciliación; GET/List no los enriquecen remotamente. Un timeout, ausencia de evento o lista vacía no acredita un cierre.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Pendiente o vencimiento incierto | Conservar progreso y reconciliar mediante recuperación. |
| Central inaccesible al leer | Lectura local sin llamadas centrales ni certificación. |
| Datos locales inaccesibles | Error seguro de almacenamiento. |
| Acceso ajeno | Rechazar sin exponer datos. |
| Duplicados, concurrencia o reinicio | Conservar idempotencia, exclusión y recuperación. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Sin nuevas abstracciones, estados sustitutos ni compatibilidad de reservas autoimpuesta. KMP no consume reservas. No cambiar Git ni otros repositorios. Mantener dominio/entidad/DTO separados y constructor DI; no usar TransactionTemplate.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** El endpoint de abandono no existe y ningún proceso se cancela automáticamente por esa intención.
- **CA-02 · RF-02:** GET/List devuelven datos propios paginados sin REST central ni campos verificationStatus/verifiedAt.
- **CA-03 · RF-03:** Eventos adelantados/tardíos, duplicados, reinicios, incertidumbre y cancelación conservan sus garantías obligatorias.
- **CA-04 · RF-04:** Registro/login, JWT y contratos HTTP/Kafka existentes siguen funcionando.
- **CA-05 · RF-05:** La migración conserva fases, IDs y plazos; retira la intención antigua de abandono y mantiene cancelaciones normales.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Solicitar ruta retirada y observar proceso. | 404 y ningún efecto. |
| CA-02 | Consultar con proveedor central caído y usuarios diferentes. | Estado local, aislamiento y cero consultas centrales. |
| CA-03 | Ejecutar regresiones de reserva, publicación, recuperación y cancelación. | Invariantes conservadas. |
| CA-04 | Ejecutar pruebas de cuenta, seguridad y gateways. | Contratos conservados. |
| CA-05 | Migrar una base V9 poblada. | Progreso válido conservado. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->

Ninguna para implementar la Etapa 1. Las siguientes etapas requieren cierre y revisión de esta etapa.


## Etapa 2 autorizada: acceso técnico

Etapa 1 aceptada el 2026-11-02. Se amplía solo con esta etapa; las etapas 3 a 6 siguen fuera del alcance de implementación.

- **RF-06:** Adquirir JWT/configuración técnicos al arrancar, conservar acceso completo en memoria y mantener separación de credenciales. No se requiere renovación transparente durante ejecución.
- **RF-07:** Ante fallos, usar adquisición acotada y diagnósticos seguros; las operaciones locales y el login de usuarios siguen disponibles. Tras agotar intentos, expiración o rechazo central se requiere reiniciar.
- **CA-06 · RF-06:** Un arranque correcto permite a consumidores reutilizar el acceso sin nuevas llamadas, respetando ambos contratos HTTP y rechazando configuración incompleta/no aprovisionada o JWT inválido/vencido.
- **CA-07 · RF-07:** Los fallos recuperables permiten hasta tres intentos de arranque; 401/fallos terminales detienen esa adquisición. Leer posteriormente no programa recuperación ni expone secretos. La suite completa mantiene autenticación, autorización y reservas.

Validación: pruebas de acceso, ciclo de vida de arranque y contratos HTTP; regresión completa con MySQL/Kafka temporales. Sin decisiones pendientes en Etapa 2.

## Etapa 3 autorizada: deduplicación Kafka mínima

Etapas 1 y 2 aceptadas. Se autoriza únicamente Etapa 3; las etapas 4 a 6 permanecen pendientes.

- **RF-08:** Conservar idempotencia durable por eventId y atomicidad con los efectos en el proceso. Confirmar el offset únicamente tras persistir; redelivery resuelve tanto rollback como commit sin ACK.
- **RF-09:** Procesar eventos adelantados a la respuesta REST con correlación previamente persistida, conservar finales y rechazo corregible. Los eventos válidos para procesos desconocidos se ignoran definitivamente y se registra su eventId, sin importar reservas ni replay futuro. Mensajes inválidos se descartan sin almacenar su contenido.
- **RF-10:** Sustituir auditoría/replay por claves procesadas, preservando los eventId históricos no nulos. No detectar contenido distinto bajo un mismo ID. Outbox, seguridad, acceso técnico y contratos externos mantienen su comportamiento.
- **CA-08 · RF-08:** SQL y Kafka temporales demuestran actualización + dedup atómicos, rollback, redelivery, commit previo a ACK y no repetición.
- **CA-09 · RF-09:** Un evento rápido se aplica antes de la respuesta de confirmación, sin futuras asociaciones; procesos desconocidos no se crean. Rechazo permite corregir teléfono y los resultados terminales convergen sin retroceder.
- **CA-10 · RF-10:** Una migración nueva conserva IDs de todas las clasificaciones anteriores, retira diagnósticos y replay, sin alterar procesos/outbox. Suite completa sin regresiones de usuarios, Catálogo y acceso técnico.

No hay decisiones pendientes para esta etapa autorizada.

## Etapa 4 autorizada: cancelación simple

Etapas 1 a 3 aceptadas; se autoriza únicamente Etapa 4. Etapas 5 y 6 siguen pendientes.

- **RF-11:** Cancelar solo reservas propias confirmadas; conservar intención, autoridad central, reconciliación de incertidumbre y recuperación tras reinicio. Repetir CANCELLED es seguro y ningún fallo/reloj acredita cancelación.
- **RF-12:** Hasta tres POST por ejecución, con consulta central antes de cada POST. Agotados o ante rechazo definitivo, detener POST automáticos y permitir retry explícito por la misma operación sin versión histórica; las revisiones centrales siguen pudiendo detectar CANCELLED. El presupuesto no se conserva exactamente entre reinicios.
- **RF-13:** Conservar exclusión de ejecuciones, descartar fallos tardíos de otra ejecución y migrar intenciones existentes sin fabricar resultados. No cambiar creación, outbox, inbox mínimo, acceso técnico, usuarios ni contratos con Catálogo.
- **CA-11 · RF-11:** Propietario cancela y repite; usuario ajeno se rechaza. Timeout no cierra localmente y recovery converge por evidencia central, sin POST si la consulta es fallida/vacía/inconcluyente.
- **CA-12 · RF-12:** Con central CONFIRMED y POST incierto se ejecutan hasta tres POST; luego recovery solo consulta. Otro POST cancel del usuario puede habilitar una ejecución nueva. Reiniciar una intención pendiente no repite a ciegas.
- **CA-13 · RF-13:** Pruebas SQL concurrentes conceden un solo permiso; respuestas/fallos tardíos no reabren cierres ni sobrescriben una ejecución posterior. Migración nueva conserva procesos/correlaciones y deriva estados mínimos conservadoramente. Suite completa sin regresiones.

Se retiran fechas, contadores y versión propios de ciclos. El estado público de cancelación queda PENDING o RETRY_REQUIRED; CANCELLED se informa mediante la fase de reserva. El POST propio de cancelación ya no recibe retryVersion. Sin decisiones pendientes dentro de esta etapa.

La retirada de versiones también abandona deduplicar retries HTTP históricos: cada POST propio sobre RETRY_REQUIRED se interpreta como una nueva solicitud explícita. No habilita repeticiones ciegas del POST central, que mantienen consulta previa e idempotencia contractual.

## Etapa 5 autorizada: fallo conservador de commit local

Etapas 1 a 4 aceptadas. Se autoriza solo Etapa 5; wiring general de Etapa 6 pendiente.

- **RF-14:** Un error de commit local no concede permiso para emitir un POST. No convertirlo en éxito mediante una lectura posterior de autoría. Un retorno normal permite continuar; conflictos únicos conocidos mantienen idempotencia sin conceder permiso nuevo y conflictos optimistas solo reintentan SQL revertido.
- **RF-15:** Conservar checkpoints, exclusión concurrente y recuperación por estado durable. No mezclar commit JDBC incierto con respuesta perdida de creación central, ni repetir confirmaciones inciertas. Mantener el cierre seguro de una confirmación todavía no enviada y la protección frente a resultados tardíos de cancelación.
- **RF-16:** Retirar solo metadata/rescates que no tengan otra función real. Preservar outbox/inbox, usuarios, acceso técnico, propiedad, estados finales y contratos externos.
- **CA-14 · RF-14:** Fallos BEFORE y AFTER del commit devuelven error sin crear hold ni confirmar/cancelar desde esa ejecución. Si SQL persistió el checkpoint queda visible; si revirtió, no se fabrica progreso. La aceptación telefónica ambigua no se anuncia exitosa, pero repetir su UUID recupera su misma fila.
- **CA-15 · RF-15:** Relectura posterior recupera estados persistidos sin atribuirlos al ejecutor que recibió el error; unicidad y @Version conceden un permiso conocido por transición. Respuesta perdida de hold conserva ocupaciones/reintentos existentes. Confirmación recuperada requiere evidencia central, no autoriza otro POST por ausencia.
- **CA-16 · RF-16:** Se conserva únicamente writeId con función de ejecución/fencing independiente del rescate. Suite completa demuestra concurrencia, cancelación, outbox/inbox, login y contratos sin cambios funcionales adicionales.

Un CONFIRMATION_PENDING persistido no demuestra si el transporte llegó a iniciarse: recovery consulta central y conserva incertidumbre cuando falta evidencia. Una intención de creación sin IDs sigue la política de recuperación vigente, sin crear a ciegas. No se promete continuidad inmediata después del error local.


## Etapa 6 autorizada: wiring Spring y boilerplate

Etapas 1 a 5 aceptadas. Autorizada únicamente la simplificación estructural, sin cambios funcionales.

- **RF-17:** Sustituir fábricas manuales triviales por descubrimiento de componentes e inyección por constructor. Conservar configuración explícita de recursos, propiedades y construcción con parámetros derivados.
- **RF-18:** Reducir únicamente constructores y accesores mecánicos, manteniendo contratos públicos, validaciones, encapsulamiento y fronteras transaccionales. Sin Lombok @Data ni nuevas abstracciones.
- **CA-17 · RF-17:** El contexto arranca con una implementación inequívoca por puerto y las mismas dependencias; usuarios, acceso técnico, Catálogo y flujo de reservas conservan su comportamiento.
- **CA-18 · RF-18:** Dominio, DTO, entidades y mappers continúan separados; TransactionalReservationStore conserva sus transacciones. Suite completa sin regresiones ni cambios en FSM, concurrencia, recovery, inbox/outbox o contratos. Métricas separan wiring y Lombok.

Sin decisiones funcionales pendientes. No se autoriza una purga de tests ni otra etapa.

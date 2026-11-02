# Casos de uso de Turnos y reservas

**Estado:** Los diez casos fueron revisados y aprobados explícitamente por el usuario el 2026-10-07. Estos casos describen comportamiento y no sustituyen las `SPEC.md` de cada funcionalidad. Los detalles pendientes de especificación, planificación y validación se conservan en las fichas.

**Fuentes:** [enunciado](../../../PROJECT_STATEMENT-v1.md), secciones 3.2, 4.2, 5, 7, 8 y 9; [contrato de integración](../../../INTEGRATION_REFERENCE-v2.md), secciones 2, 5, 8 a 13, 15 y 18; [continuidad del proyecto](../../../SESSION_HANDOFF.md).

## Estructura de las descripciones

Cada ficha complementa el diagrama con objetivo, actores, disparador, precondiciones, flujo principal, alternativas vinculadas a pasos concretos y postcondiciones. Las reglas y decisiones pendientes se registran aparte para no mezclar el recorrido normal con restricciones o diseño técnico.

Las precondiciones describen la situación necesaria para iniciar el recorrido normal; las postcondiciones explican qué queda garantizado al terminar. Una alternativa indica el paso donde se desvía el recorrido y si continúa o termina. Esta es una convención narrativa del proyecto, no una plantilla textual obligatoria de UML.

CU-09 es un proceso automático de soporte: no requiere inventar un actor externo que lo inicie. CU-05 combina la solicitud de cátedra con la participación del usuario propietario. Cada proceso se identifica por separado; el usuario puede tener uno pendiente y conservar el historial de los anteriores.

## Actores y límite

- **Usuario final vía KMP:** se registra, inicia sesión y gestiona su disponibilidad, procesos y reservas mediante Turnos. Busca profesionales directamente en Catálogo.
- **Backend Catálogo:** proporciona profesionales, agendas y su vigencia mediante un contrato protegido con JWT.
- **Servicio de cátedra:** proporciona ocupaciones, administra holds y reservas y participa del intercambio REST/Kafka.
- Turnos es propietario de las cuentas de usuarios finales y emite el JWT que ambos backends validan. La cuenta técnica de cátedra es una identidad distinta y sus credenciales nunca llegan a KMP.
- La autenticación y configuración técnica son soporte de integración. El alta inicial de esa cuenta se realiza una vez con una herramienta HTTP, no desde KMP.
- Los reinicios y la detección de operaciones pendientes son disparadores internos, no actores.

## Diagrama

Requiere Mermaid 12.0.0 o superior (`usecase-beta`). Probado en Mermaid Live 12.0.0, incluida la relación entre registro y autenticación.

```mermaid
usecase-beta
direction LR
actor KMP("Usuario final vía KMP")
actor Central("Servicio de cátedra")
actor Catalogo("Backend Catálogo")

systemBoundary Turnos["Backend Turnos y reservas"]
  Registrar("CU-01 Registrar usuario")
  Autenticar("CU-02 Autenticar usuario")
  Disponibilidad("CU-03 Consultar disponibilidad")
  Iniciar("CU-04 Iniciar reserva")
  Informacion("CU-05 Completar información adicional")
  Estado("CU-06 Consultar estado del proceso")
  Reservas("CU-07 Consultar reservas propias")
  Cancelar("CU-08 Cancelar reserva propia")
  Recuperar("CU-09 Recuperar procesos pendientes")
  Abandonar("CU-10 Abandonar proceso propio")
end

KMP --> Registrar
KMP --> Autenticar
KMP --> Disponibilidad
KMP --> Iniciar
KMP --> Informacion
KMP --> Estado
KMP --> Reservas
KMP --> Cancelar
KMP --> Abandonar
Catalogo --> Disponibilidad
Catalogo --> Iniciar
Central --> Disponibilidad
Central --> Iniciar
Central --> Informacion
Central --> Estado
Central --> Reservas
Central --> Cancelar
Central --> Recuperar
Central --> Abandonar
Registrar ..> : include Autenticar
```

Las asociaciones muestran participación, no una secuencia temporal. Cátedra participa en CU-06 si se necesita reconciliar el estado mediante CU-09. El procesamiento de resultados Kafka se describe en los flujos de reserva, cancelación y recuperación; no se presupone un caso independiente por cada evento ni una correspondencia entre casos, endpoints y clases.

CU-01 incluye CU-02 porque el registro correcto inicia sesión automáticamente. CU-09 se conserva como caso de soporte referenciado desde las alternativas, sin agregar `extend` al diagrama; puede activarse también por reinicios y recuperación de conectividad. CU-04 y CU-05 describen etapas sucesivas, no una relación de extensión opcional.

## Índice

| Caso | Resultado principal |
| --- | --- |
| [CU-01](CU-01-registrar-usuario.md) | Crear una cuenta activa con datos válidos y contraseña protegida e iniciar sesión automáticamente. Revisado y aprobado por el usuario. |
| [CU-02](CU-02-autenticar-usuario.md) | Validar credenciales y emitir JWT para las APIs protegidas. Revisado y aprobado por el usuario. |
| [CU-03](CU-03-consultar-disponibilidad.md) | Combinar agendas vigentes y ocupaciones centrales para informar horarios libres de un profesional y fecha. Revisado y aprobado por el usuario. |
| [CU-04](CU-04-iniciar-reserva.md) | Asociar el proceso al usuario, crear el hold e iniciar la confirmación REST. El resultado inicial no es una reserva confirmada. Revisado y aprobado por el usuario. |
| [CU-05](CU-05-completar-informacion.md) | Recibir la solicitud de teléfono, enviar la respuesta y permitir corregir un rechazo antes del vencimiento. Revisado y aprobado por el usuario. |
| [CU-06](CU-06-consultar-proceso.md) | Informar al propietario la espera, la información requerida, el vencimiento o el resultado conocido. Revisado y aprobado por el usuario. |
| [CU-07](CU-07-consultar-reservas.md) | Consultar reservas y procesos propios, con sus estados diferenciados y aviso cuando solo haya datos locales. Revisado y aprobado por el usuario. |
| [CU-08](CU-08-cancelar-reserva.md) | Cancelar una reserva confirmada y reconciliar respuestas REST y eventos Kafka. Revisado y aprobado por el usuario. |
| [CU-09](CU-09-recuperar-procesos.md) | Recuperar operaciones interrumpidas o con resultado incierto sin repetir efectos ni reabrir procesos finalizados. Revisado y aprobado por el usuario. |
| [CU-10](CU-10-abandonar-proceso.md) | Detener explícitamente un proceso pendiente y esperar su vencimiento; cancelar si se confirmó concurrentemente. Revisado y aprobado por el usuario. |

**Decisión para propiedad de usuarios y JWT:** Confirmada previamente: Turnos administra cuentas y emite JWT; ambos backends validan autenticación y permisos.

**Decisión para registro y login:** Confirmada en esta revisión: el registro correcto incluye la autenticación automática y entrega una sesión iniciada sin exigir otra acción del usuario. Si la cuenta se creó pero no se completó la autenticación, se conserva la cuenta y se permite iniciar sesión por separado.

**Decisión para catálogo vigente:** Confirmada previamente: Turnos obtiene la información vigente de Catálogo para nuevas operaciones que la necesitan. No mantiene una segunda réplica.

**Decisión para inicio de reserva:** Confirmada con la aprobación de los casos el 2026-10-07: una acción crea el hold e inicia inmediatamente la confirmación REST, como muestra la sección 17 del anexo. Esa aceptación inicial no equivale a una reserva confirmada.

**Decisión para identidad del paciente:** Confirmada el 2026-10-06: nombre y apellido se toman de la cuenta. La propiedad y `externalPatientId` corresponden al usuario autenticado.

**Decisión para consulta con cátedra indisponible:** Confirmada el 2026-10-06: devolver reservas guardadas localmente con aviso de posible desactualización. No presentar esa consulta como verificación del estado central.

**Decisión para abandono del proceso:** Confirmada el 2026-10-06: acción explícita mediante un botón. Volver atrás o cerrar la app no solicita cancelación; se conserva el progreso para retomar el proceso si sigue vigente.

**Decisión para estados visibles:** Confirmada el 2026-10-06: mostrar reservas confirmadas, canceladas y fallidas, además de procesos pendientes claramente diferenciados.

**Decisión para reapertura de KMP:** Consecuencia de la conservación acordada: el propietario puede consultar y retomar un proceso todavía vigente. Si terminó o venció, se informa su resultado conocido; la reapertura no lo reinicia.

**Decisión para procesos simultáneos:** Revisada y confirmada con el usuario: se permite un único proceso pendiente por usuario para reducir el acaparamiento de holds. Reemplaza la decisión previa de permitir varios. Un cierre incierto conserva el límite hasta verificar su resultado; la [aclaración docente](../../../dudas_Slack.md#duda-18-resuelta) permite archivar un intento de creación sin IDs y consultar ocupaciones antes de otra creación acotada, sin afirmar cancelación central. Se pueden consultar horarios y conservar varias reservas ya confirmadas. Cada proceso conserva identidad, progreso e historial propios.

**Decisión para abandono concurrente con confirmación:** Confirmada el 2026-10-06: abandonar también autoriza cancelar la reserva si se confirmó mientras el usuario esperaba. Se conserva la intención y se informa el resultado central verificado, sin exigir otra acción del usuario.

**Cancelación y vencimiento:** la operación de las secciones 12 y 13.1 del anexo cancela reservas confirmadas y reconoce cancelaciones ya realizadas. Abandonar un pendiente conserva la intención y detiene nuevos pasos; el hold mantiene su vencimiento central. Una espera estable sin respuesta telefónica registrada puede cerrarse al alcanzar ese plazo; las operaciones inciertas requieren reconciliación. Si se confirma concurrentemente, la intención autoriza cancelar esa reserva.

**Reglas verificadas que no requieren una nueva elección:**

- Crear un hold no garantiza una reserva; la aceptación inicial REST tampoco constituye la confirmación final.
- La vigencia depende de `expiresAt`; conservar el proceso localmente no la extiende.
- El teléfono rechazado puede corregirse antes del vencimiento con un nuevo evento. No cerrar automáticamente el proceso por ese rechazo.
- Consultar o cancelar procesos y reservas requiere comprobar propiedad por usuario, aunque cátedra opere sobre toda la cuenta técnica.
- Conservar progreso e identificadores, deduplicar mensajes y reconciliar REST/Kafka ante timeouts, respuestas perdidas, reinicios y eventos tardíos.
- La caída de Catálogo no debe impedir consultas que solo requieran datos propios.
- No informar una cancelación como completada por un timeout o por una intención local: verificar su resultado central.

**Pendientes para las siguientes etapas:** los filtros, límites y validaciones concretos se precisarán en las SPEC pertinentes; los mecanismos técnicos se definirán en sus PLAN. Se conservan las dudas contractuales y comprobaciones de integración indicadas en las fichas. La revisión y aprobación de los diez casos está completada.

Los endpoints propios, slices, transporte de actualizaciones hacia KMP, persistencia, tecnologías, timeouts y políticas de reintentos se resolverán en los PLAN pertinentes. No hay implementación autorizada en esta etapa.

# SPEC: Inicializar backend de Turnos y reservas

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación y autorización:** el 2026-10-09 el usuario autorizó aprobar y avanzar hasta implementar si el setup era equivalente al de Catálogo. La comparación no identificó cambios funcionales ni de stack; solo identidad, almacenamiento y puertos propios para coexistencia. Esta autorización cubre #3; CI se conserva como #17 separado.

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo según los requisitos del backend.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto. Inspecciona el repositorio para comprobar
  el comportamiento actual y evalúa los requisitos propios del backend.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Evalúa conectividad, persistencia, seguridad y recuperación pertinentes al servicio,
  sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Preparar una base ejecutable y reproducible del backend de Turnos para desarrollar sus casos de uso aprobados. Esta etapa está destinada a quien desarrolla y verifica el servicio.

Referencias: [issue #3](https://github.com/prog2-perassiferrara/backend-turnos/issues/3), [enunciado](../../../../PROJECT_STATEMENT-v1.md), secciones 3, 9 y 12, y [casos de uso](../../use-cases/README.md). El [setup de Catálogo](../../../../backend-catalogo/docs/features/setup-backend/SPEC.md) sirve como referencia para mantener un entorno de desarrollo equivalente.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Al iniciar #3, Turnos tenía README mínimo, instrucciones, plantillas SDD, diez casos de uso aprobados y `.gitignore`, sin código, build, wrapper, pruebas ni infraestructura ejecutable. La ejecución y sus resultados se registran en [TASKS](TASKS.md).

El [PR documental #16](https://github.com/prog2-perassiferrara/backend-turnos/pull/16), correspondiente a los issues #1 y #2, figura integrado en `main`, verificado el 2026-10-09. La rama local del issue #3 parte de esa documentación y conserva esta SPEC sin versionar. Las referencias remotas se actualizaron; no se realizaron commits, push ni merge local.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** El backend debe poder compilarse y ejecutarse de manera reproducible mediante requisitos y comandos documentados y un build con wrapper.
- **RF-02:** El servicio debe disponer de persistencia principal en una base de datos servidor, con datos y gestión de migraciones propios. Los datos confirmados deben conservarse al reiniciar sin eliminar el almacenamiento.
- **RF-03:** El backend y su base de datos deben poder levantarse mediante Docker Compose.
- **RF-04:** La configuración necesaria debe estar documentada y suministrarse externamente; los secretos no deben publicarse en archivos versionados, respuestas ni logs.
- **RF-05:** El README debe describir requisitos, configuración y comandos reales de compilación, arranque y verificación disponibles.
- **RF-06:** Si falta configuración obligatoria, es inválida o la base de datos resulta inaccesible al iniciar, el backend no debe completar el arranque y debe mostrar un diagnóstico sin secretos.
- **RF-07:** El servicio debe permitir consultar su salud técnica, incluido el acceso a su base de datos, sin exponer detalles internos ni presentar ese estado como disponibilidad de turnos o verificación de las integraciones.

RF-01 a RF-05 desarrollan el alcance del issue #3 revisado por el usuario. RF-06 fue confirmado para Turnos en esta conversación; RF-07 incorpora el health técnico pedido al retomar el setup de Catálogo.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Registro, autenticación de usuarios y demás casos de uso de Turnos, separados del setup en el issue #3 y en sus issues propios.
- Autenticación técnica e integración funcional con REST/Kafka de cátedra, correspondientes al [issue #4](https://github.com/prog2-perassiferrara/backend-turnos/issues/4).
- Protección funcional de las APIs y comunicación con Catálogo mediante JWT, correspondiente al [issue #7](https://github.com/prog2-perassiferrara/backend-turnos/issues/7). Mantener secretos fuera del repositorio sí corresponde a este setup.
- Automatización de CI, cobertura y análisis de Sonar, correspondientes al [issue #17](https://github.com/prog2-perassiferrara/backend-turnos/issues/17), que se abordará después del setup.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. Quien desarrolla obtiene el repositorio y prepara los requisitos documentados.
2. Suministra la configuración externa requerida.
3. Compila y levanta el backend y su base de datos siguiendo el README.
4. Comprueba el arranque y el uso de la persistencia mediante la verificación documentada.

Se mantendrán las dos formas de trabajo de Catálogo: backend y base de datos en Compose, o backend local mediante IDE/wrapper con la base de datos en Docker. Los mecanismos concretos se definirán en PLAN.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Turnos es propietario exclusivo de sus datos y migraciones; no accede a tablas ni repositorios internos de Catálogo.
- H2, SQLite y bases en memoria no son válidos como almacenamiento principal de la entrega.
- Turnos no mantiene una segunda réplica de catálogo para búsquedas o nuevas reservas.
- Las cuentas de usuarios finales y la cuenta técnica central son identidades distintas. Su implementación pertenece a los issues posteriores.
- Usar únicamente datos ficticios para la verificación.
- Arrancar el setup no equivale a tener disponibilidad vigente ni a poder reservar turnos.

## Casos alternativos

<!-- Evalúa los casos alternativos del backend.
Expresa resultados, no mecanismos técnicos. -->

- Una base vacía puede iniciar sin cuentas, procesos ni reservas. No se crean funcionalidades ni migraciones de negocio para demostrar el setup.
- Con configuración obligatoria inválida o base inaccesible, aplicar RF-06; con reinicio y almacenamiento conservado, aplicar RF-02.
- Un health técnico correcto no afirma que Catálogo o cátedra estén integrados ni que se puedan reservar turnos (RF-07). La recuperación funcional de reservas se desarrolla en CU-09, fuera de esta etapa.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Java y Spring Boot, exigidos por el enunciado.
- Partir de Spring Boot sin generador JHipster, decisión confirmada por el usuario para Turnos.
- Respetar exactamente dos backends independientes y la propiedad exclusiva de sus datos.
- Seguir la arquitectura hexagonal indicada por las instrucciones del proyecto; el diseño concreto corresponde al PLAN.
- Mantener la compatibilidad requerida con el modelo y contrato de usuario JHipster al implementar registro y autenticación.
- El servicio central lo administra la cátedra y no se despliega como infraestructura propia.
- Tomar como base las decisiones técnicas del setup de Catálogo para facilitar el trabajo en ambos servicios. Adaptar únicamente lo necesario a la identidad y configuración propias de Turnos; consultar diferencias con impacto real. Las versiones y mecanismos se registrarán en PLAN.
- Las pruebas usarán MySQL temporal con Testcontainers, sin `.env`, bases de desarrollo ni servicios reales de cátedra o Catálogo. Documentar responsabilidades y contratos Java con Javadoc en español; no usar Lombok `@Data`.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado un checkout y los requisitos documentados, cuando se ejecutan los pasos de compilación y arranque, entonces el backend compila y arranca mediante el build y wrapper acordados.
- **CA-02 · RF-02:** Dada la base de datos servidor propia configurada, cuando el backend inicia, entonces puede utilizar su almacenamiento y gestionar sus migraciones sin acceder a la persistencia de Catálogo.
- **CA-03 · RF-02:** Dado un dato ficticio persistido y confirmado, cuando se reinicia el servicio y su infraestructura conservando el almacenamiento, entonces el dato permanece disponible.
- **CA-04 · RF-03:** Dada la configuración documentada, cuando se levanta la infraestructura mediante Docker Compose, entonces arrancan el backend de Turnos y su base de datos.
- **CA-05 · RF-04:** Dados los archivos de ejecución, documentación y salidas del servicio, cuando se revisa cómo se suministran credenciales y secretos, entonces están externalizados y no se publican sus valores sensibles.
- **CA-06 · RF-05:** Dado el README, cuando se siguen sus instrucciones, entonces se identifican los requisitos y se reproducen los comandos reales de compilación, arranque y verificación, tanto en Compose completo como con el backend local y la base de datos Docker.
- **CA-07 · RF-06:** Dada una configuración obligatoria ausente o inválida, cuando se intenta iniciar el backend, entonces no completa el arranque y muestra un diagnóstico sin revelar secretos.
- **CA-08 · RF-06:** Dada una base de datos inaccesible, cuando se intenta iniciar el backend, entonces no completa el arranque y muestra un diagnóstico sin revelar secretos.
- **CA-09 · RF-07:** Dado el servicio iniciado con su base accesible, cuando se consulta su salud, entonces informa un estado técnico correcto sin detalles internos ni afirmaciones de disponibilidad o sincronización.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Seguir los requisitos y comandos documentados desde un checkout. | Compilación y arranque reproducibles. |
| CA-02 | Iniciar con almacenamiento propio y revisar su separación respecto de Catálogo. | Persistencia accesible y gestión de migraciones propias. |
| CA-03 | Confirmar un dato ficticio y reiniciar conservando el almacenamiento. | El dato permanece disponible. |
| CA-04 | Levantar la infraestructura mediante Compose. | Backend y base de datos arrancan. |
| CA-05 | Revisar archivos versionados, configuración, respuestas y logs. | Secretos externos, sin valores sensibles publicados. |
| CA-06 | Reproducir el README en ambos modos de ejecución. | Requisitos y comandos coinciden con lo incorporado. |
| CA-07 | Intentar iniciar con configuración obligatoria ausente o inválida. | Arranque no completado y diagnóstico sin secretos. |
| CA-08 | Intentar iniciar sin acceso a la base de datos. | Arranque no completado y diagnóstico sin secretos. |
| CA-09 | Consultar la salud del servicio y revisar su respuesta. | Estado técnico con base accesible, sin detalles internos ni afirmaciones funcionales. |

Esta tabla define las comprobaciones de aceptación, sin certificar resultados por sí sola. Los métodos de validación están en PLAN y los resultados reales se registran en TASKS.

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna pendiente de alcance o comportamiento. Aprobada bajo la autorización condicional registrada arriba, tras verificar la equivalencia con Catálogo.

Las decisiones técnicas se desarrollarán en PLAN tomando como base Catálogo y el pedido de continuidad del 2026-10-09. Se consultarán las diferencias con impacto real, sin volver a preguntar por las elecciones ya autorizadas. CI y calidad (#17) se especificarán después del setup; no condicionan la aprobación de esta SPEC.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los casos
alternativos están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->



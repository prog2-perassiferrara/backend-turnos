# SPEC: Registro y autenticación de usuarios

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Revisión:** 2026-10-30. Funcionalidad #6/#5, hashing y contraseña de #22, y mínimo de nombres para reservas #9.
**Aprobación:** explícita del usuario el 2026-10-20 («apruebo spec»), con el ajuste siguiente confirmado el 2026-10-23. Las aprobaciones y la autorización de implementación posteriores constan en PLAN/TASKS.

**Ajuste confirmado, 2026-10-23:** el usuario acepta sustituir el máximo de 100 caracteres por 72 bytes UTF-8 para utilizar BCrypt como el ejemplo JHipster. Se conserva el mínimo de 6 caracteres y no se trunca la contraseña. Afecta RF-01/RF-03/RF-09, CA-02/CA-04 y sus validaciones/pruebas en PLAN. Al confirmar el ajuste aún no había TASKS ni implementación; la aprobación del PLAN fue posterior e independiente.

**Ajuste autorizado, 2026-10-25, [#22](https://github.com/prog2-perassiferrara/backend-turnos/issues/22):** migrar el hashing a Argon2id y aplicar entre 6 y 100 caracteres Unicode (puntos de código), incluidos espacios, en registro/login. Sustituye la restricción anterior de 72 bytes, sin truncar, recortar ni normalizar. Retirar manualmente las cuentas ficticias BCrypt y sus datos asociados, sin compatibilidad ni rehash automático; no borrar datos al arrancar. Se reutilizan estos documentos y la rama existente. Impacta RF-01/RF-03/RF-09 y CA-02/CA-04; conserva el contrato HTTP/JWT. El usuario autorizó explícitamente implementar esta migración; las aprobaciones originales se conservan.

**Ajuste autorizado, 2026-10-30:** exigir nombre y apellido no blancos de 2 a 50 caracteres al registrar, para que el perfil nuevo cumpla el mínimo central de reservas conservando el máximo y los campos del contrato compatible con JHipster. Afecta RF-01 y CA-02; se actualizan validación y pruebas de límites. Se conservan cuentas existentes, autenticación y esquema. El usuario confirmó el cambio y autorizó implementarlo directamente.

<!-- PARA LA PERSONA
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución. Esta funcionalidad es backend;
MOBILE_GUIDELINES.md corresponde exclusivamente a KMP.
-->

<!-- PARA EL AGENTE
- Leer instrucciones, contratos y casos aprobados; verificar el estado real.
- Consultar únicamente decisiones pendientes, sin trasladar aprobaciones.
- No inventar requisitos ni atribuir propuestas propias a la cátedra.
- Mantener diseño técnico y contratos HTTP concretos en PLAN.md.
- Conservar secciones y comentarios; mantener documentación proporcional.
- No aprobar ni implementar por inferencia.
-->

## Qué construimos y para quién

<!-- Necesidad, destinatario y resultado observable. -->

Permitir que una persona cree su cuenta desde KMP y quede autenticada sin otra acción, o que inicie sesión posteriormente con una cuenta existente. Turnos administra esa identidad local; no registra usuarios finales en cátedra ni duplica sus cuentas en Catálogo.

Issues: [#6, registro](https://github.com/prog2-perassiferrara/backend-turnos/issues/6) y [#5, autenticación](https://github.com/prog2-perassiferrara/backend-turnos/issues/5). Casos aprobados: [CU-01](../../use-cases/CU-01-registrar-usuario.md) y [CU-02](../../use-cases/CU-02-autenticar-usuario.md), conservados por separado; CU-01 incluye CU-02.
Fuentes: [enunciado](../../../../PROJECT_STATEMENT-v1.md), secciones 3.2 y 9; [anexo](../../../../INTEGRATION_REFERENCE-v2.md), sección 2.

El usuario confirmó una única SPEC/PLAN/TASKS y un PR desde la rama actual para ambos issues. El futuro PR indicará `Closes #6` y `Closes #5`; los commits serán unidades coherentes y verificables, sin forzar separación por caso de uso. Esta organización no aprueba los documentos ni autoriza código o publicaciones.

## Situación actual

<!-- Comportamiento relevante que existe y debe conservarse, sin diseñar la solución. -->

Al elaborar esta SPEC, setup, CI y acceso técnico de #4 ya estaban integrados; Turnos todavía no administraba cuentas finales ni ofrecía registro/login. El JWT de servicio existente sirve para adquirir acceso técnico desde Catálogo; no representa al usuario de KMP. El resultado de esta funcionalidad se registra en TASKS.

El issue #6 depende de #5 porque el login automático utiliza la misma autenticación que el inicio de sesión independiente. La entrega conjunta cubre ambos recorridos sin duplicar verificación de credenciales ni emisión de JWT. La protección transversal de APIs y autorización de futuras reservas conserva su issue #7.

## Dentro del alcance

<!-- Requisitos identificables y comprobables; el diseño corresponde al PLAN. -->

- **RF-01:** Recibir y validar `login`, `password`, `firstName`, `lastName`, `email`, `langKey` e `imageUrl` conforme a las reglas siguientes. Solo `imageUrl` es opcional entre los datos del perfil; `langKey` admite el valor predeterminado acordado.
- **RF-02:** Garantizar unicidad de login y email sin distinguir mayúsculas, también ante registros concurrentes. Impedir además que el login de una cuenta coincida con el email de otra, en ambos sentidos; login y email de la misma cuenta pueden coincidir. Rechazar datos inválidos o duplicados sin cuentas parciales ni modificación de cuentas existentes.
- **RF-03:** Crear una cuenta persistente, activa inmediatamente, con contraseña protegida mediante Argon2id. Turnos asigna identidad interna estable, autoridades y auditoría; el cliente no puede elegir esos campos ni obtener permisos administrativos o de integración técnica.
- **RF-04:** Después del alta efectiva, ejecutar CU-02 con las credenciales registradas sin otra acción del usuario. El éxito completo entrega un JWT de usuario compatible con la confianza acordada con Catálogo; no entrega JWT de servicio ni de cátedra.
- **RF-05:** Si la cuenta se creó pero la autenticación posterior falla, conservarla activa e informar por separado que no se inició sesión. Permitir el login mediante CU-02 sin exigir repetir el registro; no emitir un JWT por omisión ante ese fallo.
- **RF-06:** Resolver el registro y su autenticación con los datos propios de Turnos, independientemente de la disponibilidad de Catálogo o cátedra. Conservar las cuentas tras reinicios; no declarar éxito si no se verificó el alta.
- **RF-07:** Distinguir entradas inválidas, duplicados, indisponibilidad y cuenta creada sin sesión. Una respuesta perdida no demuestra que no se creó la cuenta; una repetición no puede duplicarla ni autenticar únicamente porque el login/email ya exista.
- **RF-08:** Evitar contraseñas, hashes, privadas y tokens en logs o errores. La respuesta exitosa solo entrega el JWT final al cliente autorizado por el flujo; no expone contraseña/hash ni otros secretos. Conservar el health técnico y la separación de identidades existentes.
- **RF-09:** Permitir login independiente con nombre de usuario o email, sin distinguir mayúsculas, verificando contraseña y estado activo de la cuenta antes de emitir el JWT con los permisos administrados por Turnos. Credenciales incorrectas, cuenta no habilitada o imposibilidad de comprobarla nunca conceden acceso. La misma autenticación sirve para CU-01.
- **RF-10:** Emitir JWT de usuario con identidad estable, permisos del backend y vigencia de 24 horas por defecto o 30 días al elegir «recordarme», compatibles con Catálogo. La duración se cuenta desde la emisión, no desde la última actividad. No hay renovación automática: al vencer se requiere nuevo login. No confundir estas vigencias con los cinco minutos del JWT de servicio ni con la sesión técnica central.

Trazabilidad de la entrega: CU-01/#6 comprende RF-01 a RF-05; CU-02/#5, RF-09/RF-10 y su reutilización en RF-04/RF-05. RF-06 a RF-08 son garantías compartidas de ambos recorridos. Cada issue conserva sus criterios de aceptación, aunque comparta documentos y PR.

## Fuera de alcance

<!-- Exclusiones derivadas de los casos e issues acordados, sin ampliar funcionalidades. -->

- Registro/login técnico central y cambios de Catálogo: el acceso técnico permanece en #4.
- Pantallas, almacenamiento de sesión y navegación de KMP: corresponden al frontend. Esta SPEC define el comportamiento que consume.
- Gestión de reservas, teléfono y datos adicionales: sus CU conservan alcance propio.
- Protección transversal de APIs y autorización de operaciones futuras: pertenecen a [#7](https://github.com/prog2-perassiferrara/backend-turnos/issues/7). Registro/login deben ser accesibles sin sesión previa; agruparlos no da por resuelto #7 ni autoriza publicar rutas provisionales desprotegidas de negocio.
- Recuperación de contraseña y administración de usuarios: no forman parte de CU-01/CU-02 ni de #6/#5. No se exige verificación de email para activar la cuenta.

## Flujo de usuario

<!-- Recorrido normal y alternativas relevantes; sin prescribir pantallas ni endpoints. -->

**CU-01, registro con autenticación incluida:**

1. La persona envía desde KMP sus datos de registro, sin necesitar una sesión previa.
2. Turnos valida entradas y unicidad. Si hay un problema, rechaza el registro para que la persona lo corrija.
3. Turnos crea y confirma la cuenta activa con contraseña protegida y permisos de usuario común.
4. El flujo ejecuta CU-02 con las credenciales recién registradas; la persona no vuelve a escribirlas ni confirma otra acción.
5. Si la autenticación tiene éxito, KMP recibe el JWT de usuario para las APIs protegidas de ambos backends.
6. Si el alta se confirmó pero falla el paso 4, se informa cuenta creada sin sesión. La persona puede utilizar CU-02 sin volver a registrarse.

**CU-02, inicio de sesión independiente:**

1. La persona proporciona su nombre de usuario o email y contraseña, y puede elegir «recordarme»; el registro automático puede iniciar este mismo comportamiento.
2. Turnos comprueba las credenciales y que la cuenta esté activa, utilizando sus datos locales.
3. Si corresponde, entrega un JWT con la identidad y los permisos de esa cuenta. Si la comprobación rechaza o no puede completarse, no entrega JWT y distingue rechazo de indisponibilidad.
4. El JWT habilita únicamente las operaciones autorizadas durante 24 horas, o 30 días con «recordarme». Al vencer se vuelve a iniciar sesión, sin sustituirlo por un JWT técnico ni renovar automáticamente su vigencia.

## Datos y reglas de negocio

<!-- Campos, significado y reglas observables; sin diseñar clases, tablas ni algoritmos. -->

Las reglas siguientes parten de la base de compatibilidad confirmada por el usuario el 2026-10-18, con los ajustes indicados arriba. El enunciado enumera los campos, pero no contiene estos límites numéricos; la sección 5.1 del anexo regula la cuenta técnica y no los prescribe para usuarios finales.

| Campo | Regla acordada |
| --- | --- |
| `login` | Obligatorio, de 1 a 50 caracteres, patrón de login del ejemplo oficial JHipster. Único sin distinguir mayúsculas. |
| `password` | Obligatoria, entre 6 y 100 puntos de código Unicode, incluidos espacios, tanto en registro como en login. Un emoji fuera del plano básico cuenta como un carácter; no se cuentan unidades UTF-16 ni bytes. Rechazar el exceso sin truncar, recortar ni normalizar su valor. Nunca conservarla en texto plano. |
| `firstName`, `lastName` | Obligatorios, no vacíos ni compuestos solo por espacios; de 2 a 50 caracteres cada uno. |
| `email` | Obligatorio, válido, de 5 a 254 caracteres. Único sin distinguir mayúsculas. |
| `imageUrl` | Opcional; máximo 256 caracteres si se informa. No requiere incorporar carga ni alojamiento de imágenes. |
| `langKey` | De 2 a 10 caracteres si se informa; `es` si se omite. |
| Identidad, activación, autoridades y auditoría | Administradas por Turnos. Cuenta activa y permiso de usuario común; nunca permisos de servicio/admin elegidos por KMP. |

Referencia consultada: [modelo oficial de usuario](https://github.com/jhipster/jhipster-sample-app/blob/main/src/main/java/io/github/jhipster/sample/domain/User.java), [datos del usuario](https://github.com/jhipster/jhipster-sample-app/blob/main/src/main/java/io/github/jhipster/sample/service/dto/AdminUserDTO.java), [contraseña de registro](https://github.com/jhipster/jhipster-sample-app/blob/main/src/main/java/io/github/jhipster/sample/web/rest/vm/ManagedUserVM.java) y [patrón de login](https://github.com/jhipster/jhipster-sample-app/blob/main/src/main/java/io/github/jhipster/sample/config/Constants.java). Son referencias de compatibilidad, no requisitos adicionales de cátedra. Perfil obligatorio, español predeterminado, activación inmediata y login automático son decisiones explícitas del proyecto.

Los nombres serán los datos del paciente en CU-04. La identidad interna estable permitirá asociar procesos y reservas al usuario; no será `groupId` ni una identidad elegida libremente por el cliente. Un fallo del login automático no revierte una cuenta cuyo alta ya se confirmó.

Para CU-02 se admite usuario o email, sin distinguir mayúsculas, y se comprueba la contraseña sin normalizarla ni tomar identidad/autoridades libres del cliente. Ningún identificador puede seleccionar dos cuentas: se rechazan coincidencias entre el login de una y el email de otra, incluso en altas simultáneas; dentro de una misma cuenta sí pueden coincidir. Un login correcto no modifica el perfil ni eleva permisos.

Sin elegir «recordarme», la vigencia del JWT es de 24 horas desde su emisión; al elegirlo, de 30 días. Ambos recorridos aplican la misma política. «Recordarme» no renueva ni prolonga un JWT ya emitido, no guarda contraseñas y no establece por sí solo cómo KMP conserva el token. Al vencer se autentica nuevamente por CU-02.

Referencia de autenticación: [credenciales del ejemplo JHipster](https://github.com/jhipster/jhipster-sample-app/blob/main/src/main/java/io/github/jhipster/sample/web/rest/vm/LoginVM.java) y [resolución de usuario/correo](https://github.com/jhipster/jhipster-sample-app/blob/main/src/main/java/io/github/jhipster/sample/security/DomainUserDetailsService.java). El ejemplo admite correo y usuario; la ausencia de ambigüedades y las vigencias de Turnos son decisiones confirmadas del proyecto.

## Comportamiento del backend y casos alternativos

<!-- Escenarios propios del servicio. Las decisiones exclusivas de UI y ciclo de vida de KMP no aplican aquí. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Datos válidos, login/email libres | Cuenta activa y autenticación automática sin intervención adicional. |
| Entrada inválida o duplicada | Error identificable, sin alta parcial ni reemplazo de una cuenta. |
| Dos solicitudes disputan el mismo login/email | Como máximo una crea la cuenta; la otra no duplica ni altera la primera. |
| Login de una cuenta coincide con email de otra, también con distinta capitalización | Rechazar el alta conflictiva en cualquiera de los dos sentidos; no resolver por prioridad una identidad ambigua. |
| Cliente envía campos internos o permisos | No adquiere control sobre identidad, activación, auditoría o autoridades. |
| Catálogo/cátedra no disponibles | Registro y login local siguen siendo posibles si las dependencias propias están disponibles. |
| No puede verificarse el alta | Informar error o resultado incierto, sin afirmar éxito ni que necesariamente no hubo efecto. |
| Cuenta creada y login automático fallido | Conservar cuenta activa; informar sesión no iniciada y habilitar la alternativa de CU-02. |
| Login independiente con cuenta activa y credenciales válidas | Emitir JWT con identidad y permisos de esa cuenta. |
| Contraseña incorrecta, cuenta inexistente o no habilitada | Rechazar autenticación sin emitir JWT ni cambiar la cuenta. |
| No se pueden verificar credenciales por fallo técnico | Informar indisponibilidad, sin autenticar por omisión. |
| JWT de usuario vencido | Requiere nuevo login, sin renovación automática. Su rechazo en APIs protegidas se coordina con #7. |
| Se pierde la respuesta y se repite el registro | Respetar unicidad; no entregar JWT solo por encontrar una cuenta coincidente. No equivale a recuperar la sesión. |
| Reinicio de Turnos | La cuenta confirmada permanece; no se modifica la política de sesión técnica de #4. |

## Restricciones del pedido

<!-- Restricciones impuestas o confirmadas; no convertir elecciones técnicas en requisitos. -->

Seguir el modelo/contrato observable de usuario compatible con JHipster, sin generador obligatorio. Conservar el stack y propiedad de datos acordados para Turnos. No copiar activación por correo del ejemplo oficial: el enunciado exige activación inmediata.

El JWT final debe respetar el [contrato de confianza ya acordado con Catálogo](../../../../backend-catalogo/docs/features/seguridad-jwt/PLAN.md#jwt-propios-propuesta-de-contrato-para-coordinar-con-turnos), diferenciando perfil de usuario y servicio. Se conservan la firma RS256 y los perfiles de confianza acordados; no se reabre esa elección. PLAN concretará el formato HTTP compatible de registro/autenticación, la aplicación técnica de D-01/D-02, protección del transporte y coordinación con #7. El include de CU-02 no obliga a devolver el JWT desde el mismo endpoint de registro.

La elaboración de esta SPEC fue una etapa documental. Su aprobación, la del PLAN y la autorización de implementar son independientes; TASKS conserva la autorización explícita y la evidencia de ejecución. Sin operaciones Git mutables.

## Criterios de aceptación

<!-- Resultados observables; no se declaran comprobados durante la especificación. -->

- **CA-01 · RF-01, RF-03, RF-04:** Un registro válido crea una cuenta activa y entrega un JWT final sin otra acción del usuario ni verificación por correo.
- **CA-02 · RF-01, RF-09:** Los límites de campos y obligatoriedad se respetan; ausencia de `imageUrl` se admite y omitir `langKey` produce `es`. Registro y login rechazan contraseñas con menos de 6 o más de 100 puntos de código Unicode, sin truncarlas. Una entrada inválida no crea cuenta ni emite JWT.
- **CA-03 · RF-02:** Login/email existentes se rechazan también con cambios de mayúsculas, al igual que las coincidencias cruzadas entre cuentas. Login/email iguales en la misma cuenta se admiten si cumplen sus validaciones. Solicitudes concurrentes no generan duplicados ni identidades ambiguas, ni alteran cuentas existentes.
- **CA-04 · RF-03, RF-08:** La cuenta confirmada conserva únicamente una representación protegida de la contraseña, verificable contra su valor completo sin truncarlo. Ninguna respuesta/log expone contraseña, hash, privadas o credenciales técnicas; la respuesta de autenticación entrega únicamente el token de usuario y los datos que su contrato permita.
- **CA-05 · RF-03, RF-08:** Enviar identidad, activación, autoridades o auditoría no permite elegirlos ni elevar permisos. La cuenta registrada queda activa con permisos comunes controlados por Turnos.
- **CA-06 · RF-04, RF-05:** Si falla la autenticación después del alta, se distingue cuenta creada de sesión no iniciada, se conserva la cuenta activa y CU-02 permite autenticarla sin otro registro.
- **CA-07 · RF-06:** Con Catálogo/cátedra caídos, el registro y autenticación local válidos funcionan; un reinicio conserva la cuenta confirmada. Si fallan las dependencias propias, no se concede acceso por omisión.
- **CA-08 · RF-07:** Ante fallo o pérdida de respuesta no se declara un resultado no verificado; repetir el registro respeta unicidad y no autentica por coincidencia de login/email.
- **CA-09 · RF-04, RF-08:** El JWT obtenido representa al usuario y satisface la confianza de Catálogo, sin permisos de entrega técnica. No se acepta como sustituto un JWT de servicio/cátedra. El health conserva su respuesta técnica mínima.
- **CA-10 · RF-09, RF-10:** Una cuenta activa existente obtiene un JWT al iniciar sesión independientemente del registro, tanto con su nombre de usuario como con su email, admitiendo cambios de mayúsculas. Token, identidad y permisos corresponden a la misma cuenta; no se crean otras cuentas ni se modifican sus datos.
- **CA-11 · RF-06, RF-09:** Contraseña incorrecta, cuenta inexistente o no habilitada no producen JWT. Si no se puede comprobar la cuenta, se informa indisponibilidad sin conceder acceso; Catálogo/cátedra no son necesarios para verificar credenciales locales.
- **CA-12 · RF-10:** El JWT vence 24 horas después de su emisión por defecto, o 30 días al elegir «recordarme». Login automático e independiente aplican las mismas reglas; no hay renovación automática ni extensión por actividad. Al vencer se requiere nuevo login, sin heredar la vigencia del JWT de servicio o de cátedra.

## Cómo se comprueba el comportamiento

<!-- Escenarios y resultados; herramientas, comandos y evidencias corresponden al PLAN. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Registrar datos válidos y completar el flujo incluido de autenticación. | Cuenta activa y sesión iniciada sin acción adicional. |
| CA-02 | Variar campos ausentes, blancos, formatos y límites; omitir imagen/idioma. En registro/login, probar 5/6/100/101 puntos de código con ASCII, acentos y emojis. | Rechazos sin alta parcial ni JWT ante entrada inválida, sin truncamiento; imagen opcional y `es` predeterminado. |
| CA-03 | Repetir login/email con distinta capitalización; cruzar el login de una cuenta y el email de otra, también simultáneamente; usar el mismo login/email en una cuenta. | Sin duplicados ni ambigüedades entre cuentas; coincidencia propia admitida si es válida. |
| CA-04 | Registrar/verificar credenciales e inspeccionar representación guardada y salidas; alterar el final de una contraseña válida de longitud máxima. | Contraseña completa verificable sin texto plano ni truncamiento; cambio rechazado y sin exposición de secretos. |
| CA-05 | Intentar asignar campos internos y permisos de servicio/admin al registrar. | Sin control de campos internos ni elevación de privilegios. |
| CA-06 | Hacer fallar la autenticación posterior al alta y luego iniciar sesión por CU-02. | Cuenta conservada, resultado distinguible y login posterior posible. |
| CA-07 | Interrumpir servicios externos y reiniciar Turnos después de un alta confirmada. | Independencia externa y persistencia de cuenta; fallos propios no autentican. |
| CA-08 | Interrumpir el flujo antes/después de confirmar el alta y repetir solicitudes. | Sin afirmaciones falsas, duplicados ni emisión de JWT por mera existencia. |
| CA-09 | Presentar el JWT final ante la validación real de Catálogo y variar perfiles. | Confianza de usuario compatible, sin acceso a secretos técnicos; health mínimo. |
| CA-10 | Iniciar sesión sin registrar otra vez, con nombre de usuario o email y distinta capitalización. | JWT de la misma cuenta, sin alta ni cambios de perfil/permisos. |
| CA-11 | Variar contraseña, existencia/activación de cuenta y disponibilidad de los datos propios/servicios externos. | Rechazo o indisponibilidad sin JWT; login local independiente de servicios externos. |
| CA-12 | Comparar login automático e independiente, con/sin «recordarme», actividad posterior y vencimiento. | 24 horas o 30 días desde emisión, sin extensión automática; nuevo login al vencer. |

## Decisiones pendientes

<!-- Actualizar secciones afectadas al resolverlas; no deducir aprobaciones. -->

Ninguna decisión funcional pendiente. El usuario confirmó validaciones/perfil, la entrega conjunta #6/#5 y las dos decisiones siguientes el 2026-10-18, aprobó la SPEC completa el 2026-10-20 y confirmó el ajuste anterior del 2026-10-23. El ajuste vigente #22, autorizado el 2026-10-25, está registrado al inicio: Argon2id, 6–100 puntos de código y retirada manual de cuentas ficticias anteriores.

- **D-01, confirmada:** login con usuario o email, evitando coincidencias entre cuentas. El usuario pidió conservar una solución proporcional; PLAN deberá demostrar una garantía sencilla también para altas concurrentes. Si aparece una complejidad excesiva, consultar una simplificación antes de cambiar esta regla.
- **D-02, confirmada:** JWT normal de 24 horas y extendido de 30 días con «recordarme», sin renovación automática. La elección no equivale a persistir la sesión en KMP ni a una renovación del token técnico.

PLAN deberá desarrollar la solución común de CU-01/CU-02 y la coordinación con #7, sin repetir decisiones confirmadas ni tratar el contrato técnico de cátedra como contrato de usuario final. Mantener la revisión/aprobación propia de PLAN antes de derivar TASKS y la autorización explícita antes de implementar.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprobar alcance, coherencia RF/CA, dependencias y referencias.
Mantener diseño técnico en PLAN y no trasladar aprobaciones.
-->

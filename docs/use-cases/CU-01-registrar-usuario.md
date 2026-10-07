# CU-01. Registrar usuario

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Crear una cuenta activa de usuario final e iniciar su sesión automáticamente. |
| Actor principal | Persona que se registra desde KMP. |
| Actores de apoyo | Ninguno; no se registra una cuenta técnica en cátedra. |
| Disparador | La persona envía sus datos de registro. |

## Precondiciones

- La persona dispone de los datos necesarios para el registro.

## Flujo principal

1. La persona envía `login`, `password`, `firstName`, `lastName`, `email`, `langKey` y, opcionalmente, `imageUrl` desde KMP.
2. Turnos valida los datos y comprueba las restricciones de unicidad que correspondan al contrato de usuario exigido.
3. Turnos crea la cuenta, protege la contraseña mediante hashing y administra identificadores, activación, autoridades y auditoría.
4. El flujo de registro ejecuta [CU-02](CU-02-autenticar-usuario.md) con las credenciales recién registradas, sin pedir otra acción a la persona.
5. KMP recibe el JWT y la persona queda con una sesión iniciada para acceder a las APIs protegidas.

## Flujos alternativos y de excepción

- **2a. Datos inválidos o duplicados:** informa el problema; no crea una cuenta parcial. La persona puede corregir y volver a enviar los datos.
- **3a. No se puede completar el registro:** informa el fallo; no declara creada una cuenta sin verificarlo.
- **4a. La cuenta se creó, pero no se logra completar la autenticación automática:** informa que el registro está completado y la sesión no se inició; conserva la cuenta activa y permite iniciar sesión mediante CU-02. No solicita registrar la cuenta nuevamente.

## Postcondiciones

- **Éxito:** cuenta activa, contraseña protegida, campos internos controlados por el backend y sesión iniciada mediante JWT.
- **Fallo de registro:** no se informa un registro exitoso ni se expone información sensible.
- **Fallo de autenticación posterior:** la cuenta creada permanece activa; no se declara una sesión iniciada sin completarla.

## Reglas y relaciones

- El modelo y contrato deben ser compatibles con el usuario JHipster exigido por el enunciado.
- No se requiere verificación por correo para activar la cuenta. El login automático fue acordado con el usuario.
- Incluye CU-02: la autenticación es parte del recorrido normal del registro, aunque CU-02 también puede ejecutarse por separado. Esta relación describe comportamiento, no prescribe endpoints ni altera por sí sola el contrato de registro compatible con JHipster.
- Los nombres de esta cuenta se utilizan como datos del paciente en CU-04.

## Fuentes y pendientes

**Fuente:** [enunciado, sección 3.2](../../../PROJECT_STATEMENT-v1.md); [decisiones registradas](README.md).

**Pendiente para la SPEC:** precisar las validaciones de usuario final exigidas por cátedra. No trasladar sin comprobación las restricciones del registro de cuenta técnica del anexo.

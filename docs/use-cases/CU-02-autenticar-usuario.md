# CU-02. Autenticar usuario

**Estado:** Revisado y aprobado por el usuario el 2026-10-07.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Turnos y reservas. |
| Objetivo | Permitir el acceso del usuario a las APIs protegidas de ambos backends. |
| Actor principal | Usuario final vía KMP. |
| Actores de apoyo | Ninguno; las credenciales se verifican en Turnos. |
| Disparador | El usuario solicita iniciar sesión o CU-01 inicia la autenticación automática después de crear la cuenta. |

## Precondiciones

- El usuario tiene una cuenta activa y proporciona sus credenciales.

## Flujo principal

1. KMP proporciona las credenciales, por solicitud de inicio de sesión del usuario o como parte de [CU-01](CU-01-registrar-usuario.md) después de un registro correcto.
2. Turnos verifica las credenciales y el estado de la cuenta.
3. Turnos emite un JWT con la identidad y los permisos correspondientes.
4. KMP recibe el JWT y lo utiliza para consultar las APIs protegidas de Turnos y Catálogo.

## Flujos alternativos y de excepción

- **2a. Credenciales incorrectas o cuenta no habilitada:** rechaza el acceso; no emite JWT.
- **2b. No se puede verificar la cuenta:** informa indisponibilidad; no autentica por omisión.
- **Después del paso 4, JWT vencido o inválido:** las APIs rechazan el acceso protegido; no se utiliza la identidad enviada libremente por el cliente como sustituto.

## Postcondiciones

- **Éxito:** el usuario dispone de un JWT para las operaciones autorizadas.
- **Fallo:** no obtiene acceso autenticado.

## Reglas y relaciones

- El JWT de usuario final es distinto del JWT técnico de cátedra.
- Ambos backends validan firma, vigencia y permisos. La autenticación no autoriza acceso a reservas ajenas.
- La disponibilidad de cátedra no es necesaria para verificar cuentas locales.
- CU-01 incluye este caso para iniciar sesión automáticamente. El usuario también puede ejecutarlo de forma independiente.

## Fuentes y pendientes

**Fuente:** [enunciado, secciones 3.2 y 9](../../../PROJECT_STATEMENT-v1.md); [decisiones registradas](README.md).

**Pendiente para la SPEC/PLAN:** completar el contrato compatible de autenticación, vigencia de sesión y mecanismo de firma y validación. No se presupone renovación automática del JWT.

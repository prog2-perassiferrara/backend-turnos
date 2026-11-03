# Casos de uso de Turnos y reservas

**Estado:** Nueve casos vigentes; CU-10 retirado y conservado como antecedente. La aprobación inicial del 2026-10-07 y su evolución están en el historial SDD. Estas fichas describen comportamiento; los detalles actuales se concentran en [contratos](../CONTRATOS.md) y [arquitectura](../ARQUITECTURA.md).

**Fuentes:** [enunciado](../../../PROJECT_STATEMENT-v1.md), secciones 3.2, 4.2, 5, 7, 8 y 9; [contrato de integración](../../../INTEGRATION_REFERENCE-v2.md), secciones 2, 5, 8 a 13, 15 y 18.

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
end

KMP --> Registrar
KMP --> Autenticar
KMP --> Disponibilidad
KMP --> Iniciar
KMP --> Informacion
KMP --> Estado
KMP --> Reservas
KMP --> Cancelar
Catalogo --> Disponibilidad
Catalogo --> Iniciar
Central --> Disponibilidad
Central --> Iniciar
Central --> Informacion
Central --> Cancelar
Central --> Recuperar
Registrar ..> : include Autenticar
```

Las asociaciones muestran participación, no una secuencia temporal. CU-06 y CU-07 leen estado local; CU-09 reconcilia con cátedra por separado. El procesamiento de resultados Kafka se describe en los flujos de reserva, cancelación y recuperación; no se presupone un caso independiente por cada evento ni una correspondencia entre casos, endpoints y clases.

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
| [CU-07](CU-07-consultar-reservas.md) | Consultar pendiente e historial propios desde la base local, sin certificar frescura central. Revisado y aprobado por el usuario. |
| [CU-08](CU-08-cancelar-reserva.md) | Cancelar una reserva confirmada y reconciliar respuestas REST y eventos Kafka. Revisado y aprobado por el usuario. |
| [CU-09](CU-09-recuperar-procesos.md) | Recuperar operaciones interrumpidas o con resultado incierto sin repetir efectos ni reabrir procesos finalizados. Revisado y aprobado por el usuario. |
| [CU-10](CU-10-abandonar-proceso.md) | Retirado por la simplificación de reservas; se conserva como antecedente. |

Los contratos HTTP/Kafka, las validaciones y la FSM están documentados en los
[contratos vigentes](../CONTRATOS.md) y la [arquitectura vigente](../ARQUITECTURA.md).
El [historial SDD](../features/README.md) conserva decisiones anteriores, incluida
la retirada de CU-10. No describe endpoints futuros ni autoriza funcionalidades adicionales.

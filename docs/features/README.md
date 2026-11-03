# Historial SDD

Estos SPEC/PLAN/TASKS conservan alcance, aprobaciones y evidencia de sus respectivas
revisiones. **No son una segunda descripción del diseño vigente.** Las cifras,
diagramas, pendientes de publicación y nombres de mecanismos corresponden a la fecha
de cada registro; no se deben interpretar como estado actual de main o del working tree.

La documentación vigente está en el [README de ejecución](../../README.md),
[arquitectura y modelo](../ARQUITECTURA.md) y [contratos](../CONTRATOS.md).
Las simplificaciones sustituyeron abandono, certificación por GET/List, recuperación
técnica asíncrona, inbox con replay y ciclos versionados de cancelación.

| Antecedente | Documentos conservados |
| --- | --- |
| Setup | [SPEC](setup-backend/SPEC.md), [PLAN](setup-backend/PLAN.md), [TASKS](setup-backend/TASKS.md) |
| CI | [SPEC](ci-calidad/SPEC.md), [PLAN](ci-calidad/PLAN.md), [TASKS](ci-calidad/TASKS.md) |
| Acceso técnico | [SPEC](integracion-tecnica/SPEC.md), [PLAN](integracion-tecnica/PLAN.md), [TASKS](integracion-tecnica/TASKS.md) |
| Registro/login | [SPEC](registro-autenticacion/SPEC.md), [PLAN](registro-autenticacion/PLAN.md), [TASKS](registro-autenticacion/TASKS.md) |
| Seguridad | [SPEC](seguridad-jwt/SPEC.md), [PLAN](seguridad-jwt/PLAN.md), [TASKS](seguridad-jwt/TASKS.md) |
| Disponibilidad | [SPEC](consulta-disponibilidad/SPEC.md), [PLAN](consulta-disponibilidad/PLAN.md), [TASKS](consulta-disponibilidad/TASKS.md) |
| Inicio | [SPEC](inicio-reserva/SPEC.md), [PLAN](inicio-reserva/PLAN.md), [TASKS](inicio-reserva/TASKS.md) |
| Teléfono/progreso | [SPEC](telefono-progreso-reserva/SPEC.md), [PLAN](telefono-progreso-reserva/PLAN.md), [TASKS](telefono-progreso-reserva/TASKS.md) |
| Historial propio | [SPEC](consulta-reservas-propias/SPEC.md), [PLAN](consulta-reservas-propias/PLAN.md), [TASKS](consulta-reservas-propias/TASKS.md) |
| Cancelación/abandono original | [SPEC](cancelacion-abandono/SPEC.md), [PLAN](cancelacion-abandono/PLAN.md), [TASKS](cancelacion-abandono/TASKS.md) |
| Recuperación | [SPEC](recuperacion-procesos/SPEC.md), [PLAN](recuperacion-procesos/PLAN.md), [TASKS](recuperacion-procesos/TASKS.md) |
| Simplificación, Etapas 1 a 6 | [SPEC](simplificacion-reservas/SPEC.md), [PLAN](simplificacion-reservas/PLAN.md), [TASKS](simplificacion-reservas/TASKS.md) |
| OpenAPI, Swagger UI y timeout de integración | [SPEC](openapi-swagger/SPEC.md), [PLAN](openapi-swagger/PLAN.md), [TASKS](openapi-swagger/TASKS.md) |

Se conservan resultados y revisiones originales; nombres de logs o ensayos temporales
son referencias históricas, **no archivos incluidos ni pasos necesarios para ejecutar**.
La evidencia reproducible nueva se genera con `./mvnw -B -ntp clean verify` y se consulta
en `target/surefire-reports/` y `target/site/jacoco/`. Un resultado local no sustituye
Actions, SonarCloud ni un ensayo contra proveedores reales.

Las [reglas](../GENERIC_RULES.md), [plantilla SPEC](../SPEC_TEMPLATE.md),
[plantilla PLAN](../PLAN_TEMPLATE.md) y [prompts](../PROMPTS.md) son herramientas de
trabajo, no contratos vigentes. `docs/EVALUACION.md` y el bloque inicial del README
son generados externamente y conservan la revisión que indican; no se editaron a mano.

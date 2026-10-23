# Prompt para iniciar una especificación

Quiero que me ayudes a escribir la especificación del [funcionalidad] para este repositorio. Seguimos un flujo SPEC → PLAN → TASKS → implementación → evidencia; esta solicitud cubre únicamente la especificación.

1. Lee las instrucciones del proyecto, `docs/GENERIC_RULES.md`, `docs/SPEC_TEMPLATE.md` y la documentación pertinente del enunciado y del contrato de integración.
2. Inspecciona el estado real del repositorio. No presupongas que existe código o comportamiento implementado.
3. Considera los contratos y los escenarios de errores, concurrencia y recuperación pertinentes al backend, sin añadir alcance ni responsabilidades de UI o dispositivo.
4. Prepara un borrador de `SPEC.md` en una carpeta de la funcionalidad dentro de `docs/`, con hechos comprobados, alcance, reglas, errores y criterios de aceptación verificables. Usa la plantilla y conserva sus comentarios.
5. Distingue requisitos confirmados, propuestas y decisiones pendientes. Haz pocas preguntas por vez y actualiza el borrador con mis respuestas. No conviertas propuestas en decisiones aprobadas.
6. Mantén el diseño técnico para `PLAN.md`. Cuando solicite esa etapa para un backend, utiliza la skill `hexagonal-arch` y `docs/PLAN_TEMPLATE.md`.

No marques documentos como aprobados sin mi confirmación. No implementes durante la especificación ni la planificación; la implementación requiere una solicitud explícita.

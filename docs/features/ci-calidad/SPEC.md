# SPEC: CI y calidad con GitHub Actions y SonarQube Cloud

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Referencia:** [issue #17](https://github.com/prog2-perassiferrara/backend-turnos/issues/17).
**Aprobación:** el 2026-10-10 el usuario confirmó el enfoque y luego eligió simplificarlo: CI solo para PR hacia `main` y pushes a `main`, agregando `edited` sin filtros adicionales. Los PR apilados son excepcionales y se validan localmente hasta cambiar su destino. La aprobación no se traslada al PLAN.
**Alcance entre repositorios:** aplicar primero en Turnos; después, en Catálogo. KMP queda excluido. Todas las operaciones Git que modifican estado corresponden al usuario.

<!-- PARA LA PERSONA
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza la ejecución. Revisar esta adaptación del enfoque de Catálogo.
-->

<!-- PARA EL AGENTE
- Leer las instrucciones, inspeccionar el repositorio y conservar las decisiones confirmadas.
- Las Mobile Guidelines no corresponden a este backend.
- Distinguir hechos, propuestas y pendientes; no inventar requisitos ni resultados.
- Mantener comportamiento y aceptación aquí, diseño técnico en PLAN y evidencia en TASKS.
- Conservar las secciones y comentarios con documentación proporcional.
- No marcar documentos aprobados por inferencia ni implementar sin autorización.
-->

## Qué construimos y para quién

Verificación automática para desarrolladores y revisores de Turnos: compilación, pruebas, calidad y cobertura por PR y por cambios en `main`, siguiendo el enfoque de [Catálogo](../../../../backend-catalogo/docs/features/ci-calidad/SPEC.md).

## Situación actual

Al comenzar #17, el setup #3 está implementado, commiteado y publicado por el usuario en el [PR #18](https://github.com/prog2-perassiferrara/backend-turnos/pull/18), todavía abierto al consultarlo el 2026-10-10. Sus pruebas usan MySQL/Testcontainers. En ese punto no hay workflow, cobertura JaCoCo ni configuración de SonarScanner en Turnos; el avance y la evidencia posterior se registran en [TASKS.md](TASKS.md).

El usuario creó y seleccionó `17-integrar-github-actions-y-sonarqube-para-pruebas-y-calidad`, que parte del commit de setup `fc1af8e`. El PR de CI tendrá inicialmente la rama de #3 como destino y pasará a `main` después de integrar #18. El agente solo consulta Git; todas las operaciones que modifican su estado las ejecuta el usuario.

El usuario confirmó que `SONAR_TOKEN` está cargado en GitHub y Automatic Analysis está desactivado también en Turnos. No se inspeccionó ese ajuste administrativo. La identidad del proyecto Sonar existente fue verificada mediante su API pública.

El 2026-10-10 el usuario confirmó el plan Free de Sonar. Según la [documentación oficial](https://docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/managing-subscription/subscription-plans), permite análisis de PR únicamente si su destino es la rama principal. Esa restricción no impide compilar, ejecutar pruebas ni generar cobertura en PR apilados.

## Dentro del alcance

- **RF-01:** Verificar automáticamente PR hacia `main` y pushes a `main`, compilando y ejecutando las pruebas existentes. Para PR, validar apertura, reapertura, nuevos commits y ediciones, incluido cambiar su destino a `main`. Una edición de título o descripción también puede repetir la validación; se acepta ese costo para evitar filtros adicionales. Con otro destino no se ejecuta CI.
- **RF-02:** Usar recursos temporales y aislados para las pruebas, sin `.env`, servicios reales ni credenciales de cátedra/Catálogo.
- **RF-03:** Publicar análisis y cobertura en el proyecto Sonar de Turnos para PR hacia `main` y pushes a `main`, asociados al commit y al PR correspondientes. JaCoCo genera XML/HTML también al ejecutar las pruebas localmente, sin depender de Sonar.
- **RF-04:** Hacer fallar el job ante errores de compilación, pruebas o ejecución/publicación del análisis. El Quality Gate es informativo: su incumplimiento permanece visible en Sonar y su check, sin hacer fallar por ese motivo el job de Actions.
- **RF-05:** Proteger secretos y conservar un único método de análisis activo.
- **RF-06:** Documentar ejecución, consulta de resultados y evidencia, distinguiendo comprobaciones locales, del PR y posteriores al merge.

## Fuera de alcance

Funcionalidades de negocio, cambios arquitectónicos, despliegue, modificaciones de otros repositorios, nuevos umbrales de cobertura y cambios del Quality Gate o de protección de ramas. Los hallazgos se usan como feedback, sin corregir código ajeno al alcance ni ocultarlos. La adopción en Catálogo queda para después de probar Turnos; no se modifica KMP. Todas las operaciones Git que cambian estado, incluida la gestión de ramas, las realiza el usuario.

## Flujo de usuario

1. El desarrollador abre o actualiza un PR hacia `main`, o publica cambios en `main`. Si el PR apunta a otra rama, comprueba el código localmente mientras espera.
2. Actions compila, ejecuta pruebas, genera cobertura y envía el análisis con esa cobertura a Sonar.
3. El revisor consulta logs, checks, hallazgos, cobertura y Quality Gate, identificando qué commit se verificó y qué quedó pendiente.
4. Tras integrar el PR inferior de una pila, el desarrollador cambia el destino del siguiente a `main`; esa edición activa el flujo completo sin necesitar otro commit. Las aprobaciones y el orden de merge no se modifican.

## Datos y reglas de negocio

- La cobertura procede de las pruebas ejecutadas; no se inventan valores ni mínimos adicionales.
- Un informe ausente o un error de publicación no equivale a calidad aprobada.
- El token solo se obtiene del secreto de GitHub, sin pedir su valor ni publicarlo en archivos o logs.
- El check de Sonar puede fallar por Quality Gate aunque el job de Actions termine correctamente. Las reglas de merge existentes no se modifican.
- Los PR de forks ejecutan pruebas sin recibir secretos; la publicación del análisis se omite explícitamente en ese caso, como en Catálogo.
- Distinguir cobertura XML/HTML generada localmente de su importación a Sonar. Un PR apilado con otro destino no tiene validación de Actions/Sonar; los informes de una ejecución local no prueban una ejecución remota.

## Casos alternativos

<!-- Escenarios propios de CI, sin responsabilidades mobile. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Error de compilación o pruebas | Job fallido; no publicar un análisis presentado como verificación exitosa. |
| Credencial inválida/ausente o fallo de red al analizar | Análisis fallido, diagnóstico sin secretos; no ocultarlo. |
| Quality Gate incumplido | Resultado visible en Sonar/check, sin hacer fallar el job por ese motivo. |
| PR de fork | Pruebas ejecutadas; análisis omitido explícitamente por ausencia de secretos. |
| PR contra una rama de otro issue | No ejecutar CI; el desarrollador utiliza la verificación local hasta que el destino sea `main`. |
| Cambio de destino a `main` | Ejecutar pruebas, cobertura y Sonar, sin exigir otro commit. |
| Edición de título o descripción de un PR hacia `main` | Repetir la validación; no agregar un filtro especial. |
| Ejecución cancelada o informes ausentes | No cuenta como validación exitosa ni cobertura demostrada. |
| PR o merge todavía no publicado | Evidencia remota pendiente, diferenciada de la local. |

## Restricciones del pedido

Java 25 y Maven Wrapper, `clean verify` seguido de análisis con SonarScanner for Maven; JaCoCo genera XML/HTML. Testcontainers administra MySQL, sin otro servicio de base en el workflow. Mantener `sonar.qualitygate.wait=false` y no usar `continue-on-error` para ocultar errores. Reutilizar el enfoque de Catálogo adaptando organización/projectKey verificados. No implementar CI antes de completar el setup #3.

## Criterios de aceptación

- **CA-01 · RF-01:** Abierto, reabierto o actualizado con nuevos commits un PR hacia `main`, o publicado un push a `main`, se ejecutan compilación y pruebas automáticamente. Un PR con otro destino no activa CI.
- **CA-02 · RF-02:** En CI sin `.env` ni servicios reales, las pruebas utilizan MySQL temporal administrado por Testcontainers.
- **CA-03 · RF-03:** Tras pruebas y análisis exitosos de un PR hacia `main` o push a `main`, Sonar identifica commit/PR e importa el XML de cobertura generado. La verificación local genera XML/HTML consultables sin necesitar Sonar.
- **CA-04 · RF-04:** Los errores de compilación, pruebas o análisis hacen fallar su job; un Quality Gate incumplido queda visible sin hacer fallar por sí solo ese job.
- **CA-05 · RF-05:** Solo CI realiza análisis; el secreto no aparece en archivos/logs y no se expone a forks, que conservan la verificación de pruebas.
- **CA-06 · RF-06:** La documentación permite ejecutar y consultar resultados, con evidencia real separada por entorno y pendientes explícitos.
- **CA-07 · RF-01, RF-03:** Cambiado el destino de un PR a `main`, se ejecutan pruebas, cobertura y Sonar sin necesitar otro commit. Editar título o descripción de un PR hacia `main` también activa la validación.

## Cómo se comprueba el comportamiento

<!-- No marcar criterios superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Consultar un PR con destino distinto de main y, posteriormente, hacia main y por push a main. | Sin ejecución para la otra base; ejecuciones asociadas a main con eventos/commits correctos. |
| CA-02 | Revisar entorno y logs de pruebas. | MySQL temporal, sin configuración real. |
| CA-03 | Revisar XML/HTML locales y logs de importación/análisis Sonar en CI hacia main. | Informes generados e importados para Turnos, sin confundir evidencia local con remota. |
| CA-04 | Revisar propagación de errores y resultados Actions/Sonar. | Fallos reales visibles y Gate informativo. |
| CA-05 | Revisar uso del secreto, alternativa de forks y confirmación administrativa. | Sin divulgación ni doble método de análisis. |
| CA-06 | Revisar README y evidencia de ejecución. | Comandos reproducibles y límites claros. |
| CA-07 | Cambiar destino del PR a main y, por separado, editar título/descripción de un PR hacia main. | El flujo se activa en ambos casos sin necesitar otro commit. |

## Decisiones pendientes

Ninguna funcional. El usuario eligió CI solo hacia main con edited sin filtros adicionales, conservando el reparto JaCoCo/Sonar y el tratamiento de errores acordados. Los PR apilados no requieren automatización especial.

El PLAN define archivos y validación concreta. No se trasladan aprobaciones de Catálogo ni de setup #3; la aprobación de este documento está registrada arriba.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprobar coherencia de alcance, alternativas y criterios, manteniendo diseño
técnico y evidencia fuera de esta SPEC.
-->

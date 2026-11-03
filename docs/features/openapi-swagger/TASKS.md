# TASKS: OpenAPI, Swagger UI y timeout de integración

**SPEC, PLAN y tareas:** aprobados, 2026-11-03. **Estado:** completado; alcance de
validación indicado abajo.

**Chequeo previo:** 6 RF cubiertos por 7 CA; validación y tareas asociadas, sin
alcance adicional ni decisiones bloqueantes.

- [x] **T-01 · Dependencia, interruptor y seguridad**
  - **Alcance:** starter MVC, configuración documental y acceso GET condicionado.
  - **Referencias:** RF-01, RF-03, RF-04; CA-02, CA-03. **Dependencias:** ninguna.
  - **Verificación:** documentación habilitada/deshabilitada sin ampliar el acceso
    a las APIs de negocio.
- [x] **T-02 · Contratos y esquemas**
  - **Alcance:** anotaciones de las ocho operaciones, parámetros enteros y DTO.
  - **Referencias:** RF-01, RF-02, RF-03, RF-05; CA-01, CA-02, CA-04, CA-05.
    **Dependencias:** T-01.
  - **Verificación:** documento generado con operaciones, estados, errores y
    nombres JSON reales.
- [x] **T-03 · Pruebas HTTP**
  - **Alcance:** generador, controladores, mappers y seguridad reales; puertos de
    negocio observados.
  - **Referencias:** RF-01 a RF-05; CA-01 a CA-05. **Dependencias:** T-02.
  - **Verificación:** documento, UI, bearer, denegaciones y cero invocaciones de
    negocio al leer documentación.
- [x] **T-04 · Timeout externo y documentación**
  - **Alcance:** propiedad `app.integration.http-timeout`, `.env.example`,
    `compose.yaml` y README.
  - **Referencias:** RF-06, RF-05; CA-06, CA-07. **Dependencias:** T-01.
  - **Verificación:** `5s` por defecto y clientes de integración usando el valor
    configurado.
- [x] **T-05 · Regresión y cierre**
  - **Alcance:** `clean verify` y evidencia por criterio.
  - **Referencias:** CA-01 a CA-07. **Dependencias:** T-02, T-03, T-04.
  - **Verificación:** suite completa y revisión de documentación.

## Cierre

| Criterio | Evidencia y resultado |
| --- | --- |
| CA-01 | `OpenApiHttpTests` verifica las ocho operaciones, sus métodos, respuestas y esquemas reales. Correcto. |
| CA-02 | El documento declara `bearerAuth` y las operaciones públicas lo anulan; las pruebas HTTP confirman 401 sin token y 403 con otro perfil. Correcto. |
| CA-03 | `OpenApiDisabledHttpTests` comprueba que sin habilitación no se sirve documentación, ni con usuario; habilitada, solo los GET documentales son anónimos y las rutas similares siguen cerradas. Correcto. |
| CA-04 | Cero invocaciones a los ocho puertos al leer documento, YAML y UI; el documento no contiene la ruta de la clave ni el grupo ficticio. Correcto. |
| CA-05 | `page` y `size` figuran como enteros con mínimo, máximo y valor por defecto; las firmas y el 400 `INVALID_RESERVATION_PAGE` no cambian. Correcto. |
| CA-06 | README con sección de habilitación/uso y filas de variables; `.env.example` y `compose.yaml` actualizados. Correcto. |
| CA-07 | `app.integration.http-timeout` toma `TECHNICAL_INTEGRATION_HTTP_TIMEOUT` con `5s` por defecto y ambos clientes lo reciben en su construcción; los contextos de la suite arrancan con ese valor. Verificado por revisión y arranque; no se agregó prueba automática de un valor no predeterminado. |

**Pruebas enfocadas:** `OpenApiHttpTests` (8) y `OpenApiDisabledHttpTests` (2),
sin fallos, dentro de la corrida completa.

**Regresión:** `./mvnw -B -ntp clean verify` → 672 pruebas, 0 fallos, 0 errores,
0 omitidas, `BUILD SUCCESS` y JAR generado (2026-11-03). La primera corrida completa
tuvo un fallo en `ReservationPhoneIntegrationTests#getReadsDurableUncertaintyWithoutCallingCentralOrInventingExpiry`
(contador central en 1 en lugar de 0); el método pasó aislado y la segunda corrida
completa pasó sin cambios de código. Se interpreta como una carrera de aislamiento
con la recuperación en segundo plano, previa a este cambio; no se modificó esa prueba
ni el comportamiento de recuperación.

**Desviaciones del PLAN:** ninguna funcional. Se documentó además `500 INTERNAL_ERROR`
en las operaciones que lo emiten, dentro de RF-02. La UI no se recorrió manualmente:
la cobertura es automática con MockMvc y seguridad real. Los ejemplos y resultados
locales no sustituyen CI ni un ensayo contra cátedra.

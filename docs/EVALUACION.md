<!-- evaluacion:inicio -->
# Evaluación — prog2-perassiferrara/backend-turnos

> Documento **generado automáticamente** por la cátedra de Programación II (UM): RepoSense + análisis de calidad de **7 dimensiones**. **No editar a mano**: se regenera en cada evaluación.
> Rama evaluada: `main`. Análisis **heurístico** (regex + métricas) + build: es una criba para orientar la revisión, **no una nota**. La **documentación** del repositorio no se evalúa.
> Generado: 2026-11-17.

## 1. Veredicto

| Aspecto | Resultado |
|---|---|
| Índice de calidad | **77/100** |
| Tipo de repositorio | `backend` |
| Build (`mvnw verify` / `gradlew check`) | OK |
| Código (LOC) | 17,968 |
| Archivos de código | 217 |
| Clases | 260 |
| Código dentro de clases (%POO) | 78% |
| Funciones fuera de clases | 0 |
| Duplicación (puerta) | 2.7% |
| Duplicación lógica | 2.8% |
| Duplicación de boilerplate | 2.5% |
| Cobertura | 95% |
| Tope duro aplicado a D1 | `POO_BAJO` → D1 ≤ 60 |

> Este informe **describe** lo que el análisis midió con los criterios ya establecidos de la materia. No es una nota.

## 2. Semáforo de aprobación

Criterios de la cátedra (`CRITERIOS-CALIDAD.md`): **Aprobar** se juega con el perfil `estandar`; **destacar**, con el `exigente` (meta de excelencia).

| Semáforo | Resultado |
|---|---|
| Aprobar (`estandar`) | **no cumple** — 7/11 |
| Destacar (`exigente`) | **no cumple** — 5/11 |

**Para aprobar, falta cumplir:** SOLID

**Para destacar, falta cumplir:** SOLID; POO; Duplicación

## 3. Autoría (RepoSense)

| Autor | Commits | Código (LOC) | Tests (LOC) | Docs (LOC) | Días activos | % del código |
|---|---:|---:|---:|---:|---:|---:|
| Valentino Perassi Ferrara | 46 | 7,921 | 11,498 | 7,852 | 19 | 100% |

> _El LOC de autoría lo calcula RepoSense sobre el historial y puede diferir del LOC de código del análisis (que mira el estado de `main`)._

## 4. Dimensiones evaluadas

Cada dimensión indica **qué mide**, **por qué importa** y **tu resultado**. Cuando el puntaje queda por debajo de la referencia, se agregan preguntas para **investigar la causa** (no hay recetas).

| Dimensión | Peso | Puntaje |
|:--|--:|--:|
| **D1** Diseño OO, SOLID y patrones (20%) | 20% | 2 |
| **D2** Arquitectura hexagonal — backend (15%) | 15% | 100 |
| **D3** Frontend KMP: MVVM y buenas prácticas (10%) | 10% | N/A |
| **D4** Integración y contrato (10%) | 10% | 90 |
| **D5** Seguridad y robustez (10%) | 10% | 100 |
| **D6** Pruebas y cobertura (20%) | 20% | 100 |
| **D7** Proceso y entrega (15%) | 15% | 100 |

### D1. Diseño OO, SOLID y patrones (20%)

- **Qué mide:** Métricas orientadas a objetos (clases, métodos, campos, cohesión, acoplamiento, herencia) y proxies de SOLID, más patrones de diseño.
- **Por qué importa:** El eje de la materia es el buen uso de la POO y su arquitectura: un diseño con clases claras y poco acopladas es más fácil de entender, probar y cambiar.
- **Tu resultado:** **2/100**
- **Para investigar la causa:**
    - ¿Se pensó el modelo de clases antes de codificar o se agrupó después?
    - ¿Qué clases concentran muchas responsabilidades a la vez?
    - ¿Los colaboradores llegan como parámetros o se crean por dentro?

### D2. Arquitectura hexagonal — backend (15%)

- **Qué mide:** Separación en capas (dominio, puertos de entrada/salida, adaptadores) y dirección de las dependencias del backend.
- **Por qué importa:** Aislar el dominio de la infraestructura permite cambiar la base de datos, el framework o el transporte sin tocar las reglas de negocio.
- **Tu resultado:** **100/100**

### D4. Integración y contrato (10%)

- **Qué mide:** Cumplimiento del contrato de integración de la cátedra (formato de errores, paginación, autenticación, endpoints esperados).
- **Por qué importa:** Los servicios deben poder integrarse entre sí y con la app sin sorpresas; el contrato es lo que hacen cumplir las pruebas de integración.
- **Tu resultado:** **90/100**

### D5. Seguridad y robustez (10%)

- **Qué mide:** Credenciales embebidas, consultas SQL por concatenación, y presencia de config de entorno; aislamiento de secretos.
- **Por qué importa:** Un secreto en el repositorio o una consulta construida por concatenación son riesgos concretos, no hipotéticos.
- **Tu resultado:** **100/100**

### D6. Pruebas y cobertura (20%)

- **Qué mide:** Cantidad y resultado de pruebas y cobertura de línea (JaCoCo/Kover, Surefire/Failsafe) cuando hay build.
- **Por qué importa:** Las pruebas son la red de seguridad para refactorizar e integrar; la cobertura acota las zonas sin verificar.
- **Tu resultado:** **100/100**

### D7. Proceso y entrega (15%)

- **Qué mide:** Artefactos verificables de proceso/entrega: `compose`, `.env.example`, CI (`.github/workflows/`), `CODEOWNERS` y wrapper de build (`mvnw`/`gradlew`). **No** puntúa documentación.
- **Por qué importa:** Un proyecto que compila, se levanta y se valida solo es más fácil de revisar y de integrar en equipo.
- **Tu resultado:** **100/100**

## 5. Señales detectadas

El análisis encontró las siguientes señales (archivo, sin número de línea a propósito):

**DIP/concretos** (107)
- clase 'ReservationRecoveryDispatcher' muy acoplada (CBO=9) — `src/main/java/com/prog2/appointment/reservation/infrastructure/scheduling/ReservationRecoveryDispatcher.java`
- clase 'JpaPhoneSubmissionRepository' muy acoplada (CBO=11) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/repository/JpaPhoneSubmissionRepository.java`
- clase 'JpaReservationProcessRepository' muy acoplada (CBO=19) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/repository/JpaReservationProcessRepository.java`
- clase 'TransactionalReservationStore' muy acoplada (CBO=58) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/service/TransactionalReservationStore.java`
- clase 'TransactionalReservationStore' instancia 12 concretos (CBO=58) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/service/TransactionalReservationStore.java`
- clase 'ReservationPersistenceMapper' muy acoplada (CBO=12) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/mapper/ReservationPersistenceMapper.java`
- clase 'ReservationPersistenceMapper' instancia 5 concretos (CBO=12) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/mapper/ReservationPersistenceMapper.java`
- clase 'JpaReservationProcessAdapter' muy acoplada (CBO=32) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/adapter/JpaReservationProcessAdapter.java`
- clase 'JpaReservationProcessAdapter' instancia 13 concretos (CBO=32) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/adapter/JpaReservationProcessAdapter.java`
- clase 'ReservationProcessEntity' muy acoplada (CBO=12) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/entity/ReservationProcessEntity.java`
- clase 'ReservationActionParser' muy acoplada (CBO=25) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- clase 'ReservationActionParser' instancia 7 concretos (CBO=25) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- clase 'KafkaPhoneSubmissionAdapter' muy acoplada (CBO=37) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/KafkaPhoneSubmissionAdapter.java`
- clase 'KafkaPhoneSubmissionAdapter' instancia 3 concretos (CBO=37) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/KafkaPhoneSubmissionAdapter.java`
- clase 'ReservationKafkaChannel' muy acoplada (CBO=45) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- clase 'ReservationKafkaChannel' instancia 7 concretos (CBO=45) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- clase 'WebClientCentralAppointmentAdapter' muy acoplada (CBO=23) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientCentralAppointmentAdapter.java`
- clase 'WebClientCentralAppointmentAdapter' instancia 4 concretos (CBO=23) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientCentralAppointmentAdapter.java`
- clase 'WebClientReservationCancellationAdapter' muy acoplada (CBO=25) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientReservationCancellationAdapter.java`
- clase 'WebClientReservationCancellationAdapter' instancia 7 concretos (CBO=25) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientReservationCancellationAdapter.java`
- clase 'WebClientAppointmentHoldAdapter' muy acoplada (CBO=37) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- clase 'WebClientAppointmentHoldAdapter' instancia 11 concretos (CBO=37) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- clase 'AvailabilityReservationSlotAdapter' muy acoplada (CBO=11) — `src/main/java/com/prog2/appointment/reservation/infrastructure/availability/adapter/AvailabilityReservationSlotAdapter.java`
- clase 'AvailabilityReservationSlotAdapter' instancia 3 concretos (CBO=11) — `src/main/java/com/prog2/appointment/reservation/infrastructure/availability/adapter/AvailabilityReservationSlotAdapter.java`
- clase 'ReservationConfiguration' muy acoplada (CBO=42) — `src/main/java/com/prog2/appointment/reservation/infrastructure/config/ReservationConfiguration.java`
- clase 'ReservationConfiguration' instancia 14 concretos (CBO=42) — `src/main/java/com/prog2/appointment/reservation/infrastructure/config/ReservationConfiguration.java`
- clase 'UserReservationPatientAdapter' muy acoplada (CBO=9) — `src/main/java/com/prog2/appointment/reservation/infrastructure/user/adapter/UserReservationPatientAdapter.java`
- clase 'UserReservationPatientAdapter' instancia 4 concretos (CBO=9) — `src/main/java/com/prog2/appointment/reservation/infrastructure/user/adapter/UserReservationPatientAdapter.java`
- clase 'ReservationProblemHandler' muy acoplada (CBO=40) — `src/main/java/com/prog2/appointment/reservation/infrastructure/web/ReservationProblemHandler.java`
- clase 'ReservationController' muy acoplada (CBO=72) — `src/main/java/com/prog2/appointment/reservation/infrastructure/web/controller/ReservationController.java`
- clase 'ReservationDtoMapper' muy acoplada (CBO=18) — `src/main/java/com/prog2/appointment/reservation/infrastructure/web/mapper/ReservationDtoMapper.java`
- clase 'ReservationDtoMapper' instancia 6 concretos (CBO=18) — `src/main/java/com/prog2/appointment/reservation/infrastructure/web/mapper/ReservationDtoMapper.java`
- clase 'ReservationProcessResponse' muy acoplada (CBO=11) — `src/main/java/com/prog2/appointment/reservation/infrastructure/web/dto/ReservationProcessResponse.java`
- clase 'RecoverReservationProcessesUseCaseImpl' muy acoplada (CBO=29) — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationProcessesUseCaseImpl.java`
- clase 'CancelReservationUseCaseImpl' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/reservation/application/usecases/CancelReservationUseCaseImpl.java`
- clase 'StartReservationUseCaseImpl' muy acoplada (CBO=41) — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- clase 'StartReservationUseCaseImpl' instancia 7 concretos (CBO=41) — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- clase 'PublishPendingPhonesUseCaseImpl' muy acoplada (CBO=10) — `src/main/java/com/prog2/appointment/reservation/application/usecases/PublishPendingPhonesUseCaseImpl.java`
- clase 'SubmitReservationPhoneUseCaseImpl' muy acoplada (CBO=14) — `src/main/java/com/prog2/appointment/reservation/application/usecases/SubmitReservationPhoneUseCaseImpl.java`
- clase 'SubmitReservationPhoneUseCaseImpl' instancia 5 concretos (CBO=14) — `src/main/java/com/prog2/appointment/reservation/application/usecases/SubmitReservationPhoneUseCaseImpl.java`
- clase 'RecoverReservationCancellationsUseCaseImpl' muy acoplada (CBO=34) — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationCancellationsUseCaseImpl.java`
- clase 'RecoverReservationCancellationsUseCaseImpl' instancia 3 concretos (CBO=34) — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationCancellationsUseCaseImpl.java`
- clase 'GetReservationProcessUseCaseImpl' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/reservation/application/usecases/GetReservationProcessUseCaseImpl.java`
- clase 'ListOwnReservationsUseCaseImpl' muy acoplada (CBO=11) — `src/main/java/com/prog2/appointment/reservation/application/usecases/ListOwnReservationsUseCaseImpl.java`
- clase 'ListOwnReservationsUseCaseImpl' instancia 3 concretos (CBO=11) — `src/main/java/com/prog2/appointment/reservation/application/usecases/ListOwnReservationsUseCaseImpl.java`
- clase 'ReservationPhase' muy acoplada (CBO=12) — `src/main/java/com/prog2/appointment/reservation/domain/enums/ReservationPhase.java`
- clase 'ReservationFailure' muy acoplada (CBO=29) — `src/main/java/com/prog2/appointment/reservation/domain/enums/ReservationFailure.java`
- clase 'AppointmentHoldGateway' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/reservation/domain/ports/out/AppointmentHoldGateway.java`
- clase 'ReservationProcessRepository' muy acoplada (CBO=31) — `src/main/java/com/prog2/appointment/reservation/domain/ports/out/ReservationProcessRepository.java`
- clase 'ReservationProcess' muy acoplada (CBO=58) — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- clase 'ReservationProcess' instancia 6 concretos (CBO=58) — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- clase 'PhoneSubmission' muy acoplada (CBO=21) — `src/main/java/com/prog2/appointment/reservation/domain/model/PhoneSubmission.java`
- clase 'PhoneSubmission' instancia 10 concretos (CBO=21) — `src/main/java/com/prog2/appointment/reservation/domain/model/PhoneSubmission.java`
- clase 'AvailabilityHttpClient' muy acoplada (CBO=16) — `src/main/java/com/prog2/appointment/availability/infrastructure/client/AvailabilityHttpClient.java`
- clase 'AvailabilityHttpClient' instancia 5 concretos (CBO=16) — `src/main/java/com/prog2/appointment/availability/infrastructure/client/AvailabilityHttpClient.java`
- clase 'WebClientAppointmentOccupanciesAdapter' muy acoplada (CBO=20) — `src/main/java/com/prog2/appointment/availability/infrastructure/client/adapter/WebClientAppointmentOccupanciesAdapter.java`
- clase 'WebClientAppointmentOccupanciesAdapter' instancia 6 concretos (CBO=20) — `src/main/java/com/prog2/appointment/availability/infrastructure/client/adapter/WebClientAppointmentOccupanciesAdapter.java`
- clase 'WebClientProfessionalAgendaAdapter' muy acoplada (CBO=19) — `src/main/java/com/prog2/appointment/availability/infrastructure/client/adapter/WebClientProfessionalAgendaAdapter.java`
- clase 'WebClientProfessionalAgendaAdapter' instancia 7 concretos (CBO=19) — `src/main/java/com/prog2/appointment/availability/infrastructure/client/adapter/WebClientProfessionalAgendaAdapter.java`
- clase 'AvailabilityConfiguration' muy acoplada (CBO=25) — `src/main/java/com/prog2/appointment/availability/infrastructure/config/AvailabilityConfiguration.java`
- clase 'AvailabilityConfiguration' instancia 5 concretos (CBO=25) — `src/main/java/com/prog2/appointment/availability/infrastructure/config/AvailabilityConfiguration.java`
- clase 'AvailabilityProblemHandler' muy acoplada (CBO=23) — `src/main/java/com/prog2/appointment/availability/infrastructure/web/AvailabilityProblemHandler.java`
- clase 'AvailabilityController' muy acoplada (CBO=28) — `src/main/java/com/prog2/appointment/availability/infrastructure/web/controller/AvailabilityController.java`
- clase 'GetAvailabilityUseCaseImpl' muy acoplada (CBO=13) — `src/main/java/com/prog2/appointment/availability/application/usecases/GetAvailabilityUseCaseImpl.java`
- clase 'GetAvailabilityUseCaseImpl' instancia 5 concretos (CBO=13) — `src/main/java/com/prog2/appointment/availability/application/usecases/GetAvailabilityUseCaseImpl.java`
- clase 'AvailabilityFailure' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/availability/domain/enums/AvailabilityFailure.java`
- clase 'AppointmentOccupancy' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/availability/domain/model/AppointmentOccupancy.java`
- clase 'SafeIntegrationHttpClient' muy acoplada (CBO=28) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/SafeIntegrationHttpClient.java`
- clase 'SafeIntegrationHttpClient' instancia 7 concretos (CBO=28) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/SafeIntegrationHttpClient.java`
- clase 'WebClientCentralIntegrationAdapter' muy acoplada (CBO=10) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- clase 'WebClientCatalogTechnicalTokenAdapter' muy acoplada (CBO=13) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/WebClientCatalogTechnicalTokenAdapter.java`
- clase 'WebClientCatalogTechnicalTokenAdapter' instancia 3 concretos (CBO=13) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/WebClientCatalogTechnicalTokenAdapter.java`
- clase 'IntegrationResponse' muy acoplada (CBO=11) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/dto/IntegrationResponse.java`
- clase 'IntegrationResponse' instancia 4 concretos (CBO=11) — `src/main/java/com/prog2/appointment/integration/infrastructure/client/dto/IntegrationResponse.java`
- clase 'TechnicalIntegrationConfiguration' muy acoplada (CBO=14) — `src/main/java/com/prog2/appointment/integration/infrastructure/config/TechnicalIntegrationConfiguration.java`
- clase 'TechnicalIntegrationConfiguration' instancia 5 concretos (CBO=14) — `src/main/java/com/prog2/appointment/integration/infrastructure/config/TechnicalIntegrationConfiguration.java`
- clase 'TechnicalAccessUseCaseImpl' muy acoplada (CBO=30) — `src/main/java/com/prog2/appointment/integration/application/usecases/TechnicalAccessUseCaseImpl.java`
- clase 'TechnicalAccessUseCaseImpl' instancia 7 concretos (CBO=30) — `src/main/java/com/prog2/appointment/integration/application/usecases/TechnicalAccessUseCaseImpl.java`
- clase 'AccessFailure' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/integration/domain/enums/AccessFailure.java`
- clase 'JwtSecurityProperties' muy acoplada (CBO=16) — `src/main/java/com/prog2/appointment/infrastructure/security/config/JwtSecurityProperties.java`
- clase 'JwtSecurityProperties' instancia 3 concretos (CBO=16) — `src/main/java/com/prog2/appointment/infrastructure/security/config/JwtSecurityProperties.java`
- clase 'SecurityConfiguration' muy acoplada (CBO=44) — `src/main/java/com/prog2/appointment/infrastructure/security/config/SecurityConfiguration.java`
- clase 'SecurityConfiguration' instancia 9 concretos (CBO=44) — `src/main/java/com/prog2/appointment/infrastructure/security/config/SecurityConfiguration.java`
- clase 'ServiceTokenIssuer' muy acoplada (CBO=21) — `src/main/java/com/prog2/appointment/infrastructure/security/jwt/ServiceTokenIssuer.java`
- clase 'JwtContractValidator' muy acoplada (CBO=16) — `src/main/java/com/prog2/appointment/infrastructure/security/jwt/JwtContractValidator.java`
- clase 'SecurityProblemHandler' muy acoplada (CBO=27) — `src/main/java/com/prog2/appointment/infrastructure/security/web/SecurityProblemHandler.java`
- clase 'DatabaseConfiguration' muy acoplada (CBO=14) — `src/main/java/com/prog2/appointment/infrastructure/config/DatabaseConfiguration.java`
- clase 'TechnicalAccessProperties' muy acoplada (CBO=25) — `src/main/java/com/prog2/appointment/infrastructure/config/TechnicalAccessProperties.java`
- clase 'TechnicalAccessProperties' instancia 9 concretos (CBO=25) — `src/main/java/com/prog2/appointment/infrastructure/config/TechnicalAccessProperties.java`
- clase 'OpenApiConfiguration' muy acoplada (CBO=41) — `src/main/java/com/prog2/appointment/infrastructure/config/OpenApiConfiguration.java`
- clase 'OpenApiConfiguration' instancia 23 concretos (CBO=41) — `src/main/java/com/prog2/appointment/infrastructure/config/OpenApiConfiguration.java`
- clase 'AvailabilityProperties' muy acoplada (CBO=15) — `src/main/java/com/prog2/appointment/infrastructure/config/AvailabilityProperties.java`
- clase 'AvailabilityProperties' instancia 5 concretos (CBO=15) — `src/main/java/com/prog2/appointment/infrastructure/config/AvailabilityProperties.java`
- clase 'NimbusUserTokenIssuerAdapter' muy acoplada (CBO=12) — `src/main/java/com/prog2/appointment/user/infrastructure/security/NimbusUserTokenIssuerAdapter.java`
- clase 'SpringPasswordHasherAdapter' muy acoplada (CBO=11) — `src/main/java/com/prog2/appointment/user/infrastructure/security/SpringPasswordHasherAdapter.java`
- clase 'SpringPasswordHasherAdapter' instancia 5 concretos (CBO=11) — `src/main/java/com/prog2/appointment/user/infrastructure/security/SpringPasswordHasherAdapter.java`
- clase 'TransactionalUserAccountStore' muy acoplada (CBO=10) — `src/main/java/com/prog2/appointment/user/infrastructure/persistence/service/TransactionalUserAccountStore.java`
- clase 'JpaUserAccountAdapter' muy acoplada (CBO=13) — `src/main/java/com/prog2/appointment/user/infrastructure/persistence/adapter/JpaUserAccountAdapter.java`
- clase 'JpaUserAccountAdapter' instancia 4 concretos (CBO=13) — `src/main/java/com/prog2/appointment/user/infrastructure/persistence/adapter/JpaUserAccountAdapter.java`
- clase 'UserAccountEntity' muy acoplada (CBO=9) — `src/main/java/com/prog2/appointment/user/infrastructure/persistence/entity/UserAccountEntity.java`
- clase 'UserConfiguration' muy acoplada (CBO=17) — `src/main/java/com/prog2/appointment/user/infrastructure/config/UserConfiguration.java`
- clase 'UserConfiguration' instancia 3 concretos (CBO=17) — `src/main/java/com/prog2/appointment/user/infrastructure/config/UserConfiguration.java`
- clase 'UserProblemHandler' muy acoplada (CBO=27) — `src/main/java/com/prog2/appointment/user/infrastructure/web/UserProblemHandler.java`
- clase 'AuthenticationController' muy acoplada (CBO=21) — `src/main/java/com/prog2/appointment/user/infrastructure/web/controller/AuthenticationController.java`
- clase 'RegistrationController' muy acoplada (CBO=22) — `src/main/java/com/prog2/appointment/user/infrastructure/web/controller/RegistrationController.java`
- clase 'AuthenticateUserUseCaseImpl' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/user/application/usecases/AuthenticateUserUseCaseImpl.java`
- clase 'UserAccountRepository' muy acoplada (CBO=8) — `src/main/java/com/prog2/appointment/user/domain/ports/out/UserAccountRepository.java`

**SRP/responsabilidad** (66)
- clase 'TransactionalReservationStore' grande (LOC=270, métodos=23, campos=6) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/service/TransactionalReservationStore.java`
- método 'ReservationPersistenceMapper.getNextAttemptAt' de 32 líneas — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/mapper/ReservationPersistenceMapper.java`
- clase 'JpaReservationProcessAdapter' grande (LOC=161, métodos=21, campos=1) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/adapter/JpaReservationProcessAdapter.java`
- clase 'ReservationProcessEntity' grande (LOC=121, métodos=6, campos=23) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/entity/ReservationProcessEntity.java`
- clase 'ReservationProcessEntity' con baja cohesión (33%) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/entity/ReservationProcessEntity.java`
- método 'ReservationProcessEntity.apply' con complejidad 8 — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/entity/ReservationProcessEntity.java`
- clase 'PhoneSubmissionEntity' grande (LOC=71, métodos=3, campos=15) — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/entity/PhoneSubmissionEntity.java`
- clase 'ReservationActionParser' grande (LOC=98, métodos=15, campos=2) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- método 'ReservationActionParser.parse' de 38 líneas — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- método 'ReservationActionParser.parse' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- método 'ReservationActionParser.selection' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- método 'ReservationActionParser.type' con complejidad 7 — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationActionParser.java`
- clase 'KafkaPhoneSubmissionAdapter' grande (LOC=92, métodos=7, campos=8) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/KafkaPhoneSubmissionAdapter.java`
- clase 'KafkaPhoneSubmissionAdapter' con baja cohesión (24%) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/KafkaPhoneSubmissionAdapter.java`
- método 'KafkaPhoneSubmissionAdapter.publish' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/KafkaPhoneSubmissionAdapter.java`
- clase 'ReservationKafkaChannel' grande (LOC=220, métodos=18, campos=13) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- clase 'ReservationKafkaChannel' con baja cohesión (26%) — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- método 'ReservationKafkaChannel.prepare' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- método 'ReservationKafkaChannel.kafkaAppointmentPhoneTopic' de 50 líneas — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- método 'ReservationKafkaChannel.requireReady' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- método 'ReservationKafkaChannel.verifyMetadata' de 32 líneas — `src/main/java/com/prog2/appointment/reservation/infrastructure/messaging/ReservationKafkaChannel.java`
- clase 'WebClientCentralAppointmentAdapter' grande (LOC=125, métodos=13, campos=3) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientCentralAppointmentAdapter.java`
- clase 'WebClientCentralAppointmentAdapter' con baja cohesión (19%) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientCentralAppointmentAdapter.java`
- método 'WebClientCentralAppointmentAdapter.find' con complejidad 8 — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientCentralAppointmentAdapter.java`
- clase 'WebClientReservationCancellationAdapter' con baja cohesión (7%) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientReservationCancellationAdapter.java`
- método 'WebClientReservationCancellationAdapter.cancel' con complejidad 7 — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientReservationCancellationAdapter.java`
- método 'WebClientReservationCancellationAdapter.defaultIfEmpty' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientReservationCancellationAdapter.java`
- método 'WebClientReservationCancellationAdapter.classify' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientReservationCancellationAdapter.java`
- clase 'WebClientAppointmentHoldAdapter' grande (LOC=227, métodos=24, campos=3) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- clase 'WebClientAppointmentHoldAdapter' con baja cohesión (3%) — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- método 'WebClientAppointmentHoldAdapter.findOccupancy' de 41 líneas — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- método 'WebClientAppointmentHoldAdapter.post' de 50 líneas — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- método 'WebClientAppointmentHoldAdapter.post' con complejidad 7 — `src/main/java/com/prog2/appointment/reservation/infrastructure/client/adapter/WebClientAppointmentHoldAdapter.java`
- clase 'RecoverReservationProcessesUseCaseImpl' grande (LOC=73, métodos=4, campos=8) — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationProcessesUseCaseImpl.java`
- método 'RecoverReservationProcessesUseCaseImpl.recoverDue' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationProcessesUseCaseImpl.java`
- método 'RecoverReservationProcessesUseCaseImpl.recover' de 35 líneas — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationProcessesUseCaseImpl.java`
- método 'RecoverReservationProcessesUseCaseImpl.recover' con complejidad 10 — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationProcessesUseCaseImpl.java`
- clase 'StartReservationUseCaseImpl' grande (LOC=221, métodos=14, campos=8) — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- clase 'StartReservationUseCaseImpl' con baja cohesión (20%) — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- método 'StartReservationUseCaseImpl.start' de 32 líneas — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- método 'StartReservationUseCaseImpl.start' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- método 'StartReservationUseCaseImpl.execute' de 40 líneas — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- método 'StartReservationUseCaseImpl.execute' con complejidad 9 — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- método 'StartReservationUseCaseImpl.createHold' con complejidad 6 — `src/main/java/com/prog2/appointment/reservation/application/usecases/StartReservationUseCaseImpl.java`
- método 'SubmitReservationPhoneUseCaseImpl.submit' de 35 líneas — `src/main/java/com/prog2/appointment/reservation/application/usecases/SubmitReservationPhoneUseCaseImpl.java`
- clase 'RecoverReservationCancellationsUseCaseImpl' grande (LOC=102, métodos=10, campos=9) — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationCancellationsUseCaseImpl.java`
- clase 'RecoverReservationCancellationsUseCaseImpl' con baja cohesión (33%) — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationCancellationsUseCaseImpl.java`
- método 'RecoverReservationCancellationsUseCaseImpl.execute' de 37 líneas — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationCancellationsUseCaseImpl.java`
- método 'RecoverReservationCancellationsUseCaseImpl.execute' con complejidad 9 — `src/main/java/com/prog2/appointment/reservation/application/usecases/RecoverReservationCancellationsUseCaseImpl.java`
- clase 'ReservationOperationException' con baja cohesión (33%) — `src/main/java/com/prog2/appointment/reservation/application/exception/ReservationOperationException.java`
- clase 'ReservationProcess' grande (LOC=422, métodos=35, campos=0) — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- método 'ReservationProcess.recordFailure' de 40 líneas — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- método 'ReservationProcess.recordFailure' con complejidad 9 — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- método 'ReservationProcess.apply' de 55 líneas — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- método 'ReservationProcess.apply' con complejidad 12 — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- método 'ReservationProcess.reconcileState' de 41 líneas — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- método 'ReservationProcess.reconcileState' con complejidad 7 — `src/main/java/com/prog2/appointment/reservation/domain/model/ReservationProcess.java`
- clase 'PhoneSubmission' grande (LOC=137, métodos=12, campos=1) — `src/main/java/com/prog2/appointment/reservation/domain/model/PhoneSubmission.java`
- método 'SafeIntegrationHttpClient.builder' de 35 líneas — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/SafeIntegrationHttpClient.java`
- método 'SafeIntegrationHttpClient.classify' con complejidad 6 — `src/main/java/com/prog2/appointment/integration/infrastructure/client/adapter/SafeIntegrationHttpClient.java`
- clase 'TechnicalAccessUseCaseImpl' grande (LOC=108, métodos=7, campos=12) — `src/main/java/com/prog2/appointment/integration/application/usecases/TechnicalAccessUseCaseImpl.java`
- método 'JwtSecurityProperties.JwtSecurityProperties' de 52 líneas — `src/main/java/com/prog2/appointment/infrastructure/security/config/JwtSecurityProperties.java`
- método 'JwtSecurityProperties.JwtSecurityProperties' con complejidad 6 — `src/main/java/com/prog2/appointment/infrastructure/security/config/JwtSecurityProperties.java`
- método 'OpenApiConfiguration.appointmentOpenApi' de 30 líneas — `src/main/java/com/prog2/appointment/infrastructure/config/OpenApiConfiguration.java`
- método 'AvailabilityProperties.AvailabilityProperties' de 31 líneas — `src/main/java/com/prog2/appointment/infrastructure/config/AvailabilityProperties.java`
- clase 'UserAccountEntity' grande (LOC=65, métodos=3, campos=13) — `src/main/java/com/prog2/appointment/user/infrastructure/persistence/entity/UserAccountEntity.java`

**OCP/type-switch** (4)
- clase 'JpaReservationProcessAdapter' con 1 type-checks — `src/main/java/com/prog2/appointment/reservation/infrastructure/persistence/adapter/JpaReservationProcessAdapter.java`
- clase 'JwtContractValidator' con 2 type-checks — `src/main/java/com/prog2/appointment/infrastructure/security/jwt/JwtContractValidator.java`
- clase 'TechnicalAccessProperties' con 1 type-checks — `src/main/java/com/prog2/appointment/infrastructure/config/TechnicalAccessProperties.java`
- clase 'JpaUserAccountAdapter' con 1 type-checks — `src/main/java/com/prog2/appointment/user/infrastructure/persistence/adapter/JpaUserAccountAdapter.java`

**LSP/overrides** (1)
- 'ReservationController.cacheControl' vacío o 'no implementado' — `src/main/java/com/prog2/appointment/reservation/infrastructure/web/controller/ReservationController.java`

**POO** (1)
- clase 'ReservationProperties' solo con métodos estáticos (¿utilitaria?) — `src/main/java/com/prog2/appointment/infrastructure/config/ReservationProperties.java`

**duplicación** (6)
- duplicación lógica: 2.7% · boilerplate: 2.5%
- bloque de 8 líneas repetido 6 veces en src/test/java/com/prog2/appointment/security/JwtSecurityPropertiesTests.java:102, src/test/java/com/prog2/appointment/reservation/ReservationGatewayTests.java:254, src/test/java/com/prog2/appointment/reservation/ReservationCancellationGatewayTests.java:107 — `src/test/java/com/prog2/appointment/security/JwtSecurityPropertiesTests.java`
- bloque de 8 líneas repetido 4 veces en src/test/java/com/prog2/appointment/reservation/ReservationPhoneIntegrationTests.java:115, src/test/java/com/prog2/appointment/reservation/ReservationCancellationIntegrationTests.java:79, src/test/java/com/prog2/appointment/reservation/OwnReservationsIntegrationTests.java:85 — `src/test/java/com/prog2/appointment/reservation/ReservationPhoneIntegrationTests.java`
- bloque de 8 líneas repetido 4 veces en src/test/java/com/prog2/appointment/reservation/ReservationPhoneIntegrationTests.java:116, src/test/java/com/prog2/appointment/reservation/ReservationCancellationIntegrationTests.java:80, src/test/java/com/prog2/appointment/reservation/OwnReservationsIntegrationTests.java:86 — `src/test/java/com/prog2/appointment/reservation/ReservationPhoneIntegrationTests.java`
- bloque de 8 líneas repetido 3 veces en src/test/java/com/prog2/appointment/reservation/ReservationGatewayTests.java:255, src/test/java/com/prog2/appointment/reservation/ReservationCancellationGatewayTests.java:109, src/test/java/com/prog2/appointment/availability/AvailabilityHttpTests.java:169 — `src/test/java/com/prog2/appointment/reservation/ReservationGatewayTests.java`
- bloque de 8 líneas repetido 3 veces en src/test/java/com/prog2/appointment/user/UserHttpTests.java:183, src/test/java/com/prog2/appointment/user/UserHttpTests.java:184, src/test/java/com/prog2/appointment/user/UserHttpTests.java:185 — `src/test/java/com/prog2/appointment/user/UserHttpTests.java`

## 6. Evolución por commit

Métricas calculadas sobre el estado de `main` en **cada commit** (18 commits con trabajo), del primero al último. La calidad usa el perfil `exigente`; el aporte de código lo mide RepoSense. El índice puede **bajar** al crecer el proyecto: se acumulan señales.

```mermaid
xychart-beta
    title "Índice de calidad a lo largo de los commits"
    x-axis ["c93d5d5", "3a1915d", "cef4c4f", "201a9bb", "3733c22", "5e84aaa", "1a2356f", "ff099f9", "093f285", "f0e452d", "cac8188", "982c8db", "cebeabe", "5eaafca", "8f5606a", "ded03db", "cbefa63", "b124b25"]
    y-axis "Índice" 0 --> 100
    line [0, 15, 15, 39, 44, 54, 57, 57, 59, 61, 65, 66, 66, 66, 66, 66, 66, 66]
```

| # | Fecha | Commit | Autor | LOC | Índice | %POO | Duplic. | Sueltas |
|---|---|---|---|---:|---:|---:|---:|---:|
| 1 | 2026-09-07 | `c93d5d5` | Valentino Perassi Ferrara | 0 | 0 | 0% | 0.0% | 0 |
| 2 | 2026-09-10 | `3a1915d` | Ing. Daniel Quinteros | 0 | 15 | 0% | 0.0% | 0 |
| 3 | 2026-10-09 | `cef4c4f` | Valentino Perassi Ferrara | 0 | 15 | 0% | 0.0% | 0 |
| 4 | 2026-10-12 | `201a9bb` | Valentino Perassi Ferrara | 207 | 39 | 60% | 0.0% | 0 |
| 5 | 2026-10-13 | `3733c22` | Valentino Perassi Ferrara | 207 | 44 | 60% | 0.0% | 0 |
| 6 | 2026-10-18 | `5e84aaa` | Valentino Perassi Ferrara | 1,948 | 54 | 77% | 0.0% | 0 |
| 7 | 2026-10-23 | `1a2356f` | Valentino Perassi Ferrara | 3,470 | 57 | 72% | 0.0% | 0 |
| 8 | 2026-10-25 | `ff099f9` | Valentino Perassi Ferrara | 3,540 | 57 | 72% | 0.0% | 0 |
| 9 | 2026-10-26 | `093f285` | Valentino Perassi Ferrara | 4,310 | 59 | 72% | 0.0% | 0 |
| 10 | 2026-10-29 | `f0e452d` | Valentino Perassi Ferrara | 6,035 | 61 | 72% | 0.0% | 0 |
| 11 | 2026-10-31 | `cac8188` | Valentino Perassi Ferrara | 10,950 | 65 | 78% | 1.1% | 0 |
| 12 | 2026-11-01 | `982c8db` | Valentino Perassi Ferrara | 14,390 | 66 | 80% | 2.8% | 0 |
| 13 | 2026-11-01 | `cebeabe` | Valentino Perassi Ferrara | 15,965 | 66 | 81% | 2.9% | 0 |
| 14 | 2026-11-02 | `5eaafca` | Valentino Perassi Ferrara | 17,956 | 66 | 81% | 3.0% | 0 |
| 15 | 2026-11-02 | `8f5606a` | Valentino Perassi Ferrara | 19,035 | 66 | 81% | 3.1% | 0 |
| 16 | 2026-11-03 | `ded03db` | Valentino Perassi Ferrara | 17,602 | 66 | 78% | 2.7% | 0 |
| 17 | 2026-11-09 | `cbefa63` | Valentino Perassi Ferrara | 17,710 | 66 | 78% | 2.7% | 0 |
| 18 | 2026-11-11 | `b124b25` | Valentino Perassi Ferrara | 17,968 | 66 | 78% | 2.7% | 0 |

---
_Informe generado por la cátedra. Las dimensiones y umbrales corresponden a `calidad.py` (perfil `exigente`); el semáforo, a `CRITERIOS-CALIDAD.md`. La documentación del repositorio no se evalúa. Ante dudas, revisá el archivo señalado._
<!-- evaluacion:fin -->

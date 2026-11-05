# EP-MET — Informe de Verificación y Aprobación

**Fecha**: 2026-11-05
**Alcance**: HU-30 (Crear y editar metas), HU-31 (Aportar y reservar dinero), HU-32 (Liberar, completar y archivar metas).
**Target**: Local Supabase (127.0.0.1:54322) & Samsung Galaxy A16 (`SM-A165M`, Android 16) / Samsung Galaxy S24+ físico.
**Veredicto General**: **APPROVED WITH OBSERVATIONS** (HU-30 y HU-31 CUMPLIDOS; HU-32 PARCIAL / OBSERVADO por 2 bugs pendientes en detalle de meta completada y archivada).

---

## 1. Resumen Ejecutivo de la Épica

La Épica **EP-MET** (Metas y Reservas, correspondiente a las historias **HU-30**, **HU-31** y **HU-32** de Sprint 6) ha completado su desarrollo funcional, superando las validaciones de base de datos PostgreSQL, integración Room y ejecución en hardware real:

1. **Base de Datos PostgreSQL (Local Supabase & Remoto)**:
   - pgTAP local: 74 archivos / 1.422 tests **PASS (Exit Code 0)**.
   - Nueva suite `supabase/tests/database/goal_plan_renewal_test.sql`: 10/10 aserciones PASS (selección RPC, historia conservada, renovación comercial sintética y aporte a meta fuera de selección Free).
   - Suites previas de metas en verde con `ROLLBACK` limpio: `goal_crud_test.sql`, `goal_reserve_test.sql`, `goal_lifecycle_test.sql` y `goal_opening_ledger_test.sql`.
   - Migración de base de datos aditiva aplicada en remoto y local al 2026-11-05 (114 migraciones totales):
     - `supabase/migrations/20261105180419_ep_met_premium_goal_reactivation.sql`: `update_goal_v1` admite reactivar metas en `LOCKED_BY_PLAN` cuando `private.is_user_pro(auth.uid())` es verdadero, manteniendo candados, recibos, revisión e historial intactos.

2. **Capa Android & Persistencia Room**:
   - Migración Room v21→v22 (`MIGRATION_21_22`): validada con `GoalRoomMigrationTest` y harness `KipuDatabaseSchemaTest` (T048).
   - Pruebas unitarias de dominio y presentación:
     - `com.kipu.app.feature.goals.domain.GoalManagementTest`: 4/4 PASS (incluye `historyAlonePreventsDeletion`, `linkedTransferAlonePreventsDeletion`, `pendingOutboxAlonePreventsDeletion` y `renewedPremiumAllowsPreviouslyPlanLockedGoalToBecomeOperationalAndKeepsReserve`).
     - `com.kipu.app.feature.goals.domain.GoalBackingCalculatorTest`: PASS (`oneGoalAggregatesSuccessiveReservationsFromTwoAccounts` y `laterExpenseCreatesSharedDeficitAndBlocksNewReservesWithoutReducingEitherGoal`).
     - `com.kipu.app.feature.goals.presentation.GoalDetailViewModelTest`: 3 casos corregidos con stub `cache.observe` para el flujo de entitlement; PASS.
     - `com.kipu.app.feature.goals.presentation.GoalListViewModelTest`: 1 caso corregido con stub `cacheDao.observe` para entitlement Premium; PASS.
     - `com.kipu.app.feature.plans.domain.PlanQuotaPolicyTest`: PASS (`goalDowngradeRequiresTwoSelectionsAndPremiumRenewalRestoresAllWithoutDeletingResources`).
   - Pruebas instrumentadas en hardware Samsung Galaxy A16 (`SM-A165M`, 2026-11-05):
     - `com.kipu.app.feature.goals.data.GoalReplacementRoomIntegrationTest`: **21 PASS, 0 FAIL** (GREEN) — edición con aportes persistidos sin mover dinero, cuota atómica de reemplazo Free, y conflicto de sincronización reteniendo comandos pendientes e historia.
     - `com.kipu.app.feature.goals.presentation.GoalReleaseThenTransferFlowTest`: **1 PASS, 0 FAIL** (GREEN) — liberar reserva mantiene los fondos en la cuenta original hasta que se guarde explícitamente una transferencia separada.
     - `com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest`: **11 PASS, 2 @Ignore BUG** (GREEN con 2 defectos pendientes de UI):
       - PASS: ciclo de vida, confirmaciones, reasignación, y los 4 casos corregidos en F1.5:
         - `backingDeficitIsVisibleWithoutChangingRecordedReserve` (HU-31.6)
         - `eachAffectedGoalDetailCommunicatesSharedBackingDeficit` (HU-31.6)
         - `deficitOffersDepositReleaseAndReassignmentOptions` (HU-31.6)
         - `synchronizationConflictOffersAllThreeResolutionOptions` (HU-32.7)
       - 2 @Ignore BUG pendientes:
         1. `completedGoalRetainsItsReserveAndCannotBeArchivedUntilResolved`: BUG: el detalle actual no explica la reserva de meta completada ni ofrece el estado de archivo esperado.
         2. `archivedGoalExplainsUnavailableActionsAndOffersSafeReturnWithoutFinancialCommands`: BUG: meta archivada no comunica estado ni ofrece el retorno seguro esperado.

---

## 2. Evidencia QA Previa

- `docs/qa/metas-visual-2026-10-26.md`:
  - Revisión visual y funcional en hardware físico Samsung Galaxy S24+ (31 unitarias y 34 instrumentadas PASS).
  - En Planificación se conserva el botón flotante «+» para crear metas; retiro de barra duplicada.
  - Formulario unificado de Aportar y Retirar con validación contra la reserva real de la meta, sin usar dinero reservado para otras metas.
  - Retiro del bloque confuso «Respaldo real» de la vista de detalle; comprobación de fondos integrada en flujos de aporte y retiro.
  - Gráfico de progreso con vistas Día (7 días), Semana (semanas del mes) y Mes (enero a diciembre), excluyendo retiros y eventos anulados.
  - Guía mensual sugerida condicionada (pendiente dividido entre meses restantes).
  - Selector de cuentas compartido con logos de bancos (BBVA, BCP) e icono de efectivo.
- `docs/qa/correcciones-celular-comprobantes-metas.md` (2026-10-21):
  - Corrección de visualización en pantallas móviles y comprobantes.
- `docs/qa/activacion-sync-planificacion-2026-10-26.md` y `docs/qa/sincronizacion-planificacion-2026-10-25.md`:
  - Verificación de outbox, feed de cambios y resolución de eventos pendientes.

---

## 3. Cambios y Correcciones de esta Ronda (Fase 1.5 y Fase 4)

1. **HU-30.8 (Reactivación de metas bloqueadas por plan tras renovación Premium)**:
   - `UpdateGoal.kt`: admite reactivar una meta en estado `LOCKED_BY_PLAN` cuando el usuario cuenta con entitlement Premium activo, recalculando `ACTIVE` o `COMPLETED` sin modificar reservas.
   - `OfflineFirstGoalRepository.kt` y `GoalsApi.kt`: sincronización de metas restauradas vía comando outbox `UPDATE_GOAL` conservando revisión e historia.
   - `GoalDetailViewModel.kt` y `GoalListViewModel.kt`: observación reactiva de cambios de entitlement para evitar sobrescribir el estado renovado.
   - Base de Datos: migración `20261105180419_ep_met_premium_goal_reactivation.sql` desplegada y aplicada en remoto el 2026-11-05.
   - Cobertura: test unitario `renewedPremiumAllowsPreviouslyPlanLockedGoalToBecomeOperationalAndKeepsReserve` habilitado y verificado PASS; test pgTAP `goal_plan_renewal_test.sql` (10 aserciones PASS).

2. **HU-31.6 (Manejo de déficit de respaldo compartido)**:
   - `GoalBackingCalculator.kt`: cálculo compartido de saldo disponible y déficit agregado en minor units por cuenta.
   - `GoalBackingStatus.kt`: componente Compose que explica el déficit compartido y expone botones de acción con callbacks: ingresar fondos, liberar y reasignar.
   - `GoalDetailScreen.kt` y `GoalContributionSheet.kt`: integración del respaldo por cuenta, preselección de la cuenta con déficit para liberar y navegación al registro rápido desde el historial filtrado.
   - Cobertura: tests instrumentados `backingDeficitIsVisibleWithoutChangingRecordedReserve`, `eachAffectedGoalDetailCommunicatesSharedBackingDeficit` y `deficitOffersDepositReleaseAndReassignmentOptions` en `GoalLifecycleFlowTest` habilitados y ejecutados PASS en Galaxy A16.

3. **HU-32.7 (Resolución de conflictos de sincronización)**:
   - `GoalConflictResolver.kt`: nuevo coordinador de recuperación y reintento con validación de sesión de propietario.
   - `GoalOutboxDao.kt`: consultas owner-scoped de comandos en conflicto y estado terminal `DISCARDED`.
   - `GoalListScreen.kt`: presentación de las tres opciones de resolución de conflicto (Descartar, Ajustar desde versión actual, Resolver/reintentar con identidad original).
   - Cobertura: tests instrumentados `conflictingFeedRetainsPendingCommandHistoryAndFinancialState` (21 PASS en `GoalReplacementRoomIntegrationTest`) y `synchronizationConflictOffersAllThreeResolutionOptions` (PASS en `GoalLifecycleFlowTest`).

---

## 4. Pendientes y Bloqueos

- **2 @Ignore BUG pendientes en `GoalLifecycleFlowTest`**:
  1. `completedGoalRetainsItsReserveAndCannotBeArchivedUntilResolved`: la pantalla de detalle no explica la reserva existente al completar la meta ni ofrece el estado de archivo esperado.
  2. `archivedGoalExplainsUnavailableActionsAndOffersSafeReturnWithoutFinancialCommands`: la meta archivada no comunica claramente las acciones no disponibles ni ofrece el retorno seguro esperado.
  Ambos comportamientos corresponden a deuda preexistente también ausente en HEAD previo; las tareas T055 y T056 permanecen abiertas `[ ]` en `tasks.md`.
- **Dispositivo Samsung Galaxy S24**: La ejecución instrumentada automatizada se limitó al Samsung Galaxy A16 debido a conflicto de firmas (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); el S24 conservó su versión instalada previa sin DEBUGGABLE.

---

## 5. Matriz de Evidencias

| Componente / Suite | Tipo | Resultado | Estado |
|---|---|---|---|
| `goal_plan_renewal_test.sql` | pgTAP (DB) | 10 / 10 PASS | ✅ Aprobado |
| `goal_crud_test.sql` | pgTAP (DB) | ROLLBACK (exit 0) | ✅ Aprobado |
| `goal_reserve_test.sql` | pgTAP (DB) | ROLLBACK (exit 0) | ✅ Aprobado |
| `goal_lifecycle_test.sql` | pgTAP (DB) | ROLLBACK (exit 0) | ✅ Aprobado |
| `goal_opening_ledger_test.sql` | pgTAP (DB) | ROLLBACK (exit 0) | ✅ Aprobado |
| `GoalManagementTest` | JVM UnitTest | 4 / 4 PASS | ✅ Aprobado |
| `GoalBackingCalculatorTest` | JVM UnitTest | PASS | ✅ Aprobado |
| `GoalDetailViewModelTest` | JVM UnitTest | 3 / 3 PASS | ✅ Aprobado |
| `GoalListViewModelTest` | JVM UnitTest | 1 / 1 PASS | ✅ Aprobado |
| `GoalReplacementRoomIntegrationTest` | AndroidTest (A16) | 21 / 21 PASS | ✅ Aprobado |
| `GoalReleaseThenTransferFlowTest` | AndroidTest (A16) | 1 / 1 PASS | ✅ Aprobado |
| `GoalLifecycleFlowTest` | AndroidTest (A16) | 11 PASS / 2 @Ignore | ⚠️ Aprobado con 2 BUGs |
| `git diff --check` | Calidad | 0 advertencias | ✅ Aprobado |

---

## 6. Veredicto por Historia de Usuario

| Historia de Usuario | Veredicto | Resumen de Justificación |
|---|---|---|
| **HU-30** (Crear y editar metas) | **CUMPLIDO** | Creación virtual o vinculada sin mover dinero, edición con preservación de aportes, cuota Free de 2 metas activas y reactivación Premium (HU-30.8) con migración remota 20261105180419. |
| **HU-31** (Aportar y reservar dinero) | **CUMPLIDO** | Reserva sin transacciones ficticias, traslado explícito net-zero, cálculo de respaldo agregado y resolución de déficit compartido (HU-31.6) con opciones de aporte/liberación/reasignación. |
| **HU-32** (Liberar, completar y archivar) | **PARCIAL / OBSERVADO** | Liberación, reasignación y resolución de conflictos (HU-32.7) plenamente funcionales y verificados; 2 defectos pendientes de UI en detalle de meta completada y archivada identificados en `GoalLifecycleFlowTest` (@Ignore BUG). |

---
description: "Tareas de implementación de EP-MET"
---

# Tareas: EP-MET — Metas y Reservas Virtuales

**Rama**: `006-ep-met-metas-reservas`
**Entrada**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contrato de comandos](contracts/goal-command-contract.md) y [quickstart.md](quickstart.md).

**Propagated**: 2026-10-17 — Updated from spec.md refinements for synthetic preflight validation and the separate hosted activation gate.

**Propagated**: 2026-10-18 — Applied active-only Free counting, COMPLETED exemption, and explicit atomic replacement when reactivating at capacity.

**Propagated**: 2026-10-18 — Applied the same user-selected atomic replacement for `RELEASE_GOAL`-triggered reactivation at the Free active-goal limit.

**Pruebas (2026-10-18)**: T013/T015 están implementadas localmente y QA independiente las aprobó con observaciones: CRUD, reserve, lifecycle y opening-ledger PASS con ROLLBACK/limpieza; la carrera CREATE/UPDATE mantuvo como máximo dos `ACTIVE`. T042 sigue abierta hasta cerrar su prueba Compose RED active-only; T043 implementa la integración Android de edición. T044/T045 y T046/T047 cubren por separado `RELEASE_GOAL`; T048 cubre el harness Room v11→v22. No se ejecutaron migraciones ni comandos alojados; T040 sigue siendo el gate obligatorio.

**Organización**: las tareas están agrupadas por HU. El formato es `- [ ] Tnnn [P?] [USn?] descripción con ruta`. `[P]` indica que la tarea puede paralelizarse con otra de archivos distintos y sin dependencia incompleta.

## Fase 1: Preparación

**Propósito**: crear el límite del feature Android existente en el único módulo de aplicación.

- [x] T001 Crear los paquetes `domain/model`, `domain/usecase`, `data/local`, `data/remote`, `data/sync`, `presentation` y `di` bajo `app/src/main/java/com/kipu/app/feature/goals/`.

---

## Fase 2: Fundamentos bloqueantes

**Propósito**: cerrar decisiones que cambian el modelo financiero, revisar la historia heredada y preparar persistencia/sync comunes a las tres HUs.

**Gate**: completar T002 antes de diseñar los comandos finales y T003–T004 antes de dar por validado localmente el diagnóstico. T004 no autoriza DDL alojado ni habilita RPC; eso depende de T040. Room y el modelo puro pueden avanzar localmente una vez que T002 esté resuelto.

- [x] T002 Resolver los puntos bloqueantes de `specs/006-ep-met-metas-reservas/checklists/financial.md` sobre selección y multiplicidad de cuentas de respaldo, reducción del objetivo, reasignación, eliminación con outbox pendiente y resolución de faltantes; actualizar `specs/006-ep-met-metas-reservas/spec.md`, `specs/006-ep-met-metas-reservas/data-model.md`, `specs/006-ep-met-metas-reservas/contracts/goal-command-contract.md` y `specs/006-ep-met-metas-reservas/quickstart.md`.
- [x] T003 [P] Crear una consulta de diagnóstico estrictamente de solo lectura que reporte migraciones/ACL, eventos por tipo y transacción, propietario/cuenta/moneda, y su conciliación con transacciones y `internal.ledger_entries` en `supabase/diagnostics/006_ep_met_goal_history_preflight.sql`.
- [x] T004 Ejecutar T003 en `kipu-met-local-20261017` con fixtures sintéticos: asignación y liberación conciliadas, ledger ausente, ledger de otro propietario/cuenta y evento sin transacción. Confirmar que los dos casos conciliados no requieren revisión, los dos casos inconsistentes sí la requieren, y los eventos sin cuenta de respaldo muestran `legacy_unlinked=true` sin inventar respaldo; registrar evidencia y límites en `legacy-data-decision.md` y `quickstart.md`. Esta tarea valida la consulta local y no sustituye el gate T040.
- [x] T005 [P] Escribir primero la prueba de preservación de datos al migrar Room v21→v22, con metas/eventos sintéticos y sin pérdida de cuentas/movimientos, en `app/src/androidTest/java/com/kipu/app/core/database/GoalRoomMigrationTest.kt`.
- [x] T006 [P] Definir `Goal`, `GoalEvent` y los cálculos puros de progreso/estado con `Money` y unidades menores en `app/src/main/java/com/kipu/app/feature/goals/domain/model/Goal.kt`, `app/src/main/java/com/kipu/app/feature/goals/domain/model/GoalEvent.kt` y `app/src/main/java/com/kipu/app/feature/goals/domain/GoalProgressCalculator.kt`.
- [x] T007 Añadir `GoalEntity`, `GoalEventEntity`, `GoalDao`, outbox y checkpoint owner-scoped con índices y referencias que preserven eventos en `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalEntities.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalDao.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalOutboxEntity.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalOutboxDao.kt` y `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalSyncCheckpointEntity.kt`.
- [x] T008 Implementar y registrar `MIGRATION_21_22`, agregar entidades/DAOs a Room v22 y guardar el snapshot 22 en `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`, `KipuDatabase.kt`, `app/src/main/java/com/kipu/app/core/di/CoreModule.kt` y `app/schemas/com.kipu.app.core.database.KipuDatabase/22.json`.
- [x] T009 [P] Después de T004, implementar y probar localmente la migración base aditiva de respaldo por evento, FKs owner-scoped, resumen derivado, restricciones de historia, RLS y grants del modelo de metas en `supabase/migrations/20261017180000_ep_met_goal_schema.sql`; ampliar el origen canónico del ledger para aperturas/correcciones en `supabase/migrations/20261018190000_ep_mov_opening_ledger.sql` y cubrirlo en `supabase/tests/database/goal_opening_ledger_test.sql`. Preservar IDs, `financial_movements` y el gate T040; no reinterpretar fuentes ambiguas ni aplicar migraciones alojadas antes de T040. Verificado localmente; véase evidencia de cierre al final de la Fase 6.
- [x] T010 Definir sobre la API común el sobre de comando, hash, revisión, recibo y errores reintentables/conflictivos; implementar cliente DTO/RPC y aplicación del feed en `app/src/main/java/com/kipu/app/feature/goals/domain/model/GoalCommandModels.kt`, `app/src/main/java/com/kipu/app/feature/goals/domain/GoalCommandHasher.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/remote/GoalsApi.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/remote/GoalDtos.kt` y `app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalChangeFeedApplier.kt`. La configuración remota por defecto seguirá desactivada hasta satisfacer T040.
- [x] T011 Implementar persistencia local atómica, repositorio offline-first, worker de sync y bindings Hilt para meta/evento/outbox/recibo/feed en `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalsSyncScheduler.kt`, `app/src/main/java/com/kipu/app/feature/goals/domain/GoalRepository.kt` y `app/src/main/java/com/kipu/app/feature/goals/di/GoalsModule.kt`.

**Checkpoint**: T004 valida localmente el diagnóstico con datos sintéticos. No aplicar migraciones ni habilitar RPC en un proyecto alojado hasta completar T040; ninguna reserva puede escribirse como gasto/ingreso; Room y el outbox deben conservar operación y reintento local.

---

## Fase 3: HU-30 — Crear y editar metas (Prioridad: P1) — MVP

**Objetivo**: crear y editar metas con cuenta real enlazada opcional, conservar aportes y aplicar el límite Free contando únicamente metas `ACTIVE`; `COMPLETED` no consume cupo.

**Prueba independiente**: crear una meta virtual sin cuenta enlazada y otra con cuenta propia; comprobar que crear no altera saldo ni historia, que editar conserva aportes y que un objetivo completado elevado por encima de la reserva vuelve a Activa. En Free, solo `ACTIVE` consume los dos espacios; con dos activas, reactivar una completada requiere selección explícita y reemplazo atómico sin pérdida de reservas/historia.

### Tareas de prueba (antes de implementación)

- [x] T012 [P] [US1] Añadir pruebas de dominio para cuenta enlazada opcional, validación de objetivo/moneda, edición y transición de COMPLETED a ACTIVE al subir el objetivo en `app/src/test/java/com/kipu/app/feature/goals/domain/GoalManagementTest.kt`.
- [x] T013 [P] [US1] Añadir pruebas de contrato SQL para propiedad, límite Free contando solo metas `ACTIVE` (excluir `COMPLETED`), reactivación al cupo mediante selección explícita de una meta `ACTIVE` a `LOCKED_BY_PLAN` con ambas transiciones atómicas, cancelación/selección inválida sin mutación, recibos idempotentes y borrado solo de metas vacías en `supabase/tests/database/goal_crud_test.sql`. QA independiente T013/T015: aprobado con observaciones; suites SQL locales PASS/ROLLBACK.
- [x] T014 [P] [US1] Añadir pruebas de flujo Compose para crear/editar sin movimiento, selector opcional de cuenta y bloqueo del límite Free en `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalManagementFlowTest.kt`.

### Implementación

- [x] T015 [P] [US1] Implementar `CREATE_GOAL`, `UPDATE_GOAL` y `DELETE_EMPTY_GOAL`, incluida revisión, propiedad, cupo Free solo para `ACTIVE` y reactivación con selección explícita de meta activa para bloquear, aplicando el reemplazo de dos estados atómicamente con recibos/revisiones/feed coherentes y protección de historial, mediante la migración aditiva `supabase/migrations/20261018190200_ep_met_active_goal_quota_atomic_replacement.sql` y cobertura en `supabase/tests/database/goal_crud_test.sql`; QA independiente aprobada con observaciones.
- [x] T016 [P] [US1] Implementar creación, edición, recálculo de estado/avance y borrado condicional de meta vacía en `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/CreateGoal.kt`, `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/UpdateGoal.kt` y `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/DeleteEmptyGoal.kt`.
- [x] T017 [US1] Conectar comandos de alta/edición/borrado vacío al repositorio local-first y actualizar proyecciones/outbox sin crear eventos financieros al crear en `app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt` y `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt`.
- [x] T018 [US1] Crear lista y formulario de metas, estados de validación, confirmación de borrado vacío y presentación del modo virtual/vinculado opcional y límite de plan en `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalListViewModel.kt`, `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalManagementUiState.kt`, `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalListScreen.kt` y `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalFormScreen.kt`.
- [x] T019 [US1] Añadir destinos de lista/formulario, registrar `goalDestinations` en el host autenticado y agregar un acceso visible desde el panel Dinero en `app/src/main/java/com/kipu/app/navigation/GoalsNavigation.kt`, `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`, `app/src/main/java/com/kipu/app/navigation/AccountsNavigation.kt` y `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`.

**Checkpoint**: HU-30 no cambia balances, no crea aporte al crear la meta y conserva eventos al editar; el borrado físico solo procede sin historia ni operación relacionada.

---

## Fase 4: HU-31 — Aportar y reservar dinero (Prioridad: P1)

**Objetivo**: reservar saldo libre una sola vez entre metas y registrar un traslado interno solo cuando el usuario lo elija expresamente.

**Prueba independiente**: con S/1,000 confirmados, reservar S/200 deja el saldo real en S/1,000 y el respaldo libre en S/800. La insuficiencia rechaza el importe completo; una transferencia explícita y el evento se confirman juntos; origen y destino iguales solo reservan.

### Tareas de prueba (antes de implementación)

- [x] T020 [P] [US2] Añadir pruebas puras de saldo confirmado, reservas compartidas, moneda y faltante de respaldo en `app/src/test/java/com/kipu/app/feature/goals/domain/GoalBackingCalculatorTest.kt`.
- [x] T021 [P] [US2] Añadir pruebas SQL de concurrencia por cuenta, rechazo sin parcialidad, propiedad/moneda, mismo origen-destino, transferencia atómica, retry y ausencia de gasto en `supabase/tests/database/goal_reserve_test.sql`.
- [x] T022 [P] [US2] Añadir pruebas Compose de reserva-only, selección de respaldo, transferencia explícita, insuficiencia y conflicto de sync en `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalContributionFlowTest.kt`.

### Implementación

- [x] T023 [P] [US2] Implementar cálculo puro de respaldo por cuenta (`saldo confirmado − reservas activas`) y rechazo de importes parciales en `app/src/main/java/com/kipu/app/feature/goals/domain/GoalBackingCalculator.kt`.
- [x] T024 [P] [US2] Implementar `RESERVE_GOAL` con bloqueo por cuenta de respaldo, validación owner/currency/free balance, evento, recibo/revisión/feed y transferencia interna atómica opcional en `supabase/migrations/20261017180200_ep_met_goal_reserve_commands.sql`.
- [x] T025 [US2] Implementar el caso de uso `ReserveGoalFunds` para reserva-only, traslado elegido y caso de misma cuenta sin transferencia en `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/ReserveGoalFunds.kt`.
- [x] T026 [US2] Integrar el comando de reserva con la persistencia local/outbox, la API y los resultados APPLIED/DUPLICATE/CONFLICT/REJECTED sin duplicar efectos al reintentar en `app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt`, `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt` y `app/src/main/java/com/kipu/app/feature/goals/data/remote/GoalsApi.kt`.
- [x] T027 [US2] Crear el flujo Compose de aporte para elegir importe, respaldo opcional/predeterminado y traslado explícito, mostrando saldo reservado/libre y rechazos íntegros en `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionViewModel.kt`, `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionUiState.kt` y `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionSheet.kt`.
- [x] T028 [US2] Integrar los efectos de transferencia únicamente con el comando y las proyecciones existentes de movimientos, y mostrar el shortfall cuando un gasto confirmado deja reservas sin respaldo en `app/src/main/java/com/kipu/app/feature/goals/data/GoalMovementIntegration.kt` y `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalBackingStatus.kt`.

**Checkpoint**: reservas y liberaciones no escriben ledger; solo la transferencia explícita cambia cuentas y nunca genera ingreso/gasto; las reservas concurrentes no exceden saldo.

---

## Fase 5: HU-32 — Liberar, completar y archivar metas (Prioridad: P1)

**Objetivo**: liberar y reasignar reservas de forma explícita, conservar la reserva al completar y archivar sin historia huérfana.

**Prueba independiente**: alcanzar objetivo marca Completed conservando el importe; liberar una parte reduce reserva y aumenta disponibilidad sin ingreso; liberar de más se rechaza; archivar requiere reserva cero o reasignación y conserva los eventos.

### Tareas de prueba (antes de implementación)

- [x] T029 [P] [US3] Añadir pruebas de dominio para liberación parcial/excesiva, estado completado/reactivado, reasignación y reserva requerida para archivar en `app/src/test/java/com/kipu/app/feature/goals/domain/GoalLifecycleTest.kt`.
- [x] T030 [P] [US3] Añadir pruebas SQL para no crear ingreso al liberar, revisión/feed, atomicidad de reasignación, archivo con reserva y preservación de eventos al borrar en `supabase/tests/database/goal_lifecycle_test.sql`.
- [x] T031 [P] [US3] Añadir pruebas Compose de finalización, liberación, reasignación, cancelar archivo y archivo con historia en `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalLifecycleFlowTest.kt`.

### Implementación

- [x] T032 [P] [US3] Implementar `RELEASE_GOAL`, `REASSIGN_GOAL_RESERVE` y `ARCHIVE_GOAL` con importes/revisiones, historia inmutable, reserva cero al archivar y sin ingreso/movimiento implícito en `supabase/migrations/20261017180300_ep_met_goal_lifecycle_commands.sql`.
- [x] T033 [P] [US3] Implementar casos de uso de liberar, reasignar con el mismo respaldo y archivar, recalculando estado sin usarlo como saldo en `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/ReleaseGoalReserve.kt`, `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/ReassignGoalReserve.kt` y `app/src/main/java/com/kipu/app/feature/goals/domain/usecase/ArchiveGoal.kt`.
- [x] T034 [US3] Integrar comandos de ciclo de vida en repositorio/outbox, aplicar revisiones/feed y preservar los eventos al archivar en `app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt` y `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt`.
- [x] T035 [US3] Crear detalle e historial de eventos con acciones de liberar, reasignar y archivar, mensajes de confirmación/cancelación y estado Completed/Active en `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailViewModel.kt`, `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt` y `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalEventHistory.kt`.
- [x] T036 [US3] Añadir rutas de detalle y ciclo de vida, conectando la navegación desde la lista sin duplicar estado financiero en el host, en `app/src/main/java/com/kipu/app/navigation/GoalsNavigation.kt` y `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`.

**Checkpoint**: completar no libera ni mueve; liberar no produce ingreso; reasignar con el mismo respaldo no produce transferencia; metas archivadas conservan eventos y no tienen reserva activa.

---

## Fase 6: Pulido y revisión transversal

**Propósito**: dejar trazabilidad de las decisiones aprobadas y validar los límites financieros, de seguridad y compatibilidad de las tres HUs.

- [x] T037 Actualizar los escenarios, prerequisitos, comandos y resultados esperados definitivos tras cerrar decisiones en `specs/006-ep-met-metas-reservas/quickstart.md`.
- [x] T038 Revisar todas las migraciones/RPC de EP-MET contra propiedad, RLS/grants, hash, revisión, concurrencia, cascadas, feed, transferencia sin ingreso/gasto y preservación de historia en `supabase/migrations/20261017180000_ep_met_goal_schema.sql`, `supabase/migrations/20261017180100_ep_met_goal_crud_commands.sql`, `supabase/migrations/20261017180200_ep_met_goal_reserve_commands.sql` y `supabase/migrations/20261017180300_ep_met_goal_lifecycle_commands.sql`. Revisión independiente PASS en el sandbox local; la carrera concurrente se había verificado previamente en dos sesiones psql, pero no se rerunió en esta revisión. Sin hallazgos confirmados; no implica aprobación hosted ni cierre de T040.
- [x] T039 Ejecutar los escenarios locales de `specs/006-ep-met-metas-reservas/quickstart.md`, registrar evidencia de implementación y cualquier bloqueo restante en `quickstart.md` y `legacy-data-decision.md`; usar solo la instancia local aislada para las pruebas de base de datos. QA independiente registrada abajo. `connectedDebugAndroidTest` sigue roja: triage encontró 1 fallo relacionado con EP-MET en el harness legado `KipuDatabaseSchemaTest` (v11 no migra a v22; no se confirmó defecto de producción) y 15 fallos sin evidencia de causalidad EP-MET; ver detalle abajo. T040 sigue pendiente como gate remoto; T042/T043/T044/T045/T046/T047/T048 permanecen abiertas.
- [ ] T040 [ACTIVACIÓN] Antes de aplicar migraciones alojadas o habilitar RPC remotos, ejecutar T003 en un entorno no productivo con historial representativo, conciliar ACL/migraciones/eventos/transacciones/ledger, revisar outboxes pendientes de clientes antiguos y registrar el resultado/aprobación en `legacy-data-decision.md`. Reintentar solo comandos con identidad/hash y validación verificables; pasar los ambiguos a revisión manual. No usar producción.
- [x] T041 Corregir BUG-QA-001 con prueba dual-source RED/GREEN en `supabase/tests/database/goal_opening_ledger_test.sql` y migración aditiva local `supabase/migrations/20261018190100_ep_mov_opening_ledger_ambiguous_source_guard.sql`: referencias REVERSAL/ADJUSTMENT simultáneas se reportan ambiguas y quedan fuera de candidatos/ledger. RED antes del fix: `ambiguous=0` y se insertaba una fila; GREEN en sandbox aislado: `ambiguous=1`, `candidates=5`, `inserted=0`, cero filas ledger para la referencia dual. QA independiente aprobó; suites SQL opening-ledger, CRUD, reserve y lifecycle pasaron. No se consultó Supabase alojado; T040 sigue pendiente.
- [ ] T042 [P] [US1] Añadir pruebas Compose RED del cupo Free active-only y del límite de dos metas `ACTIVE` en `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalManagementFlowTest.kt`. Excluir de T042 el selector/confirmación/cancelación de reactivación, que pertenece a T043, y los flujos `RELEASE_GOAL`, que pertenecen a T046/T047. La clase dirigida compila y ejecuta 5 casos: 4 PASS y el RED esperado de `completedGoalDoesNotConsumeFreePlanSlot` (1 `ACTIVE` + 1 `COMPLETED` aún muestra el contador incorrecto); falta completar formalmente el card Kanban con esa evidencia.
- [ ] T043 [US1] Implementar en Android el flujo local-first de reactivación al límite Free por edición de objetivo: selección/cancelación de la meta activa a bloquear, cambio atómico de ambas proyecciones/outbox según el contrato aprobado, y resolución visible de conflictos/revisiones en los componentes de goals (depende de T015, T017, T042 y la revisión arquitectónica del contrato).
- [ ] T044 [P] [US3] Añadir pruebas SQL de `RELEASE_GOAL` para el caso en que una meta `COMPLETED` quedaría bajo el objetivo al liberar y ya hay dos metas `ACTIVE`: selección válida bloquea la activa elegida y libera/reactiva en una transacción; cancelación/selección stale, inválida o ajena deja cero eventos/cambios; verificar reservas, historia, idempotencia, revisiones/feed y límite concurrente en `supabase/tests/database/goal_lifecycle_test.sql`.
- [ ] T045 [US3] Extender `RELEASE_GOAL` en una migración SQL aditiva según revisión arquitectónica para aceptar la selección explícita, serializar el cupo por usuario y aplicar el evento de liberación, el bloqueo y la reactivación atómicamente en un recibo/feed coherente; preservar el comportamiento de clientes antiguos de forma fail-safe. No editar migraciones ya aplicadas al sandbox.
- [ ] T046 [P] [US3] Añadir pruebas Compose del flujo de liberación que reactiva una meta `COMPLETED` al límite Free: selección de meta activa, confirmar/cancelar, operación y proyecciones sin parcialidad, reservas/historia conservadas, sin efectos monetarios implícitos, en la suite de `GoalLifecycleFlowTest.kt`.
- [ ] T047 [US3] Integrar en Android el flujo local-first de liberación con reemplazo atómico al límite: selector en detalle, cancelación sin outbox, persistencia conjunta de release + dos metas/outbox, receipt/feed y conflicto de revisión coherentes, según contrato aprobado (depende de T045, T046, T034 y la revisión arquitectónica).
- [ ] T048 [P] [US1] Corregir el harness `KipuDatabaseSchemaTest#representativeVersionOneRowsSurviveValidationAndNormalReopen` para migrar fixture legado v11 mediante la cadena de migraciones registrada hasta Room v22, manteniendo asserts de preservación; ejecutar la prueba instrumentada dirigida. No modificar producción ni debilitar el test.

### Evidencia de conciliación SQL local (2026-10-18)

- En `supabase_db_kipu-met-local-20261017`, ejecutados con `psql -v ON_ERROR_STOP=1 -f` los scripts `goal_crud_test.sql`, `goal_reserve_test.sql`, `goal_lifecycle_test.sql` y `goal_opening_ledger_test.sql`; los cuatro terminaron con `ROLLBACK` sin excepción. Opening-ledger observó `ambiguous=1`, `candidates=5`, `inserted=0`.
- T009 queda cerrado junto con la revisión T038: evidencia local de migraciones/contrato y ledger; revisión independiente previa confirmó RLS/grants/auth sin hallazgos. Esto no ejecuta migraciones alojadas y no elimina el gate T040.
- T030/T032 quedan cerrados por `goal_lifecycle_test.sql`: liberación sin ledger/ingreso, revisiones y resultados del feed, reasignación completa, archivo rechazado con reserva y permitido a cero, e historia retenida frente a borrado físico. El script confirmó cero movimientos `GOAL_FUNDING` para lifecycle y reservas esperadas por meta.
- Decisiones de producto recibidas el 2026-10-18: solo `ACTIVE` consume el cupo Free; `COMPLETED` no. Tanto la edición del objetivo como `RELEASE_GOAL` que reactiven al límite requieren selección explícita de una meta activa y operación atómica. T013/T015 quedaron implementadas localmente y con QA independiente `APPROVED WITH OBSERVATIONS` (tarea `t_6b704eeb`); su observación de integrar/reconciliar el resultado compuesto queda a cargo de T043 y la ruta `RELEASE_GOAL` permanece separada en T044/T045/T046/T047.
- T021/T024 quedan cerrados con el test SQL `goal_reserve_test.sql` y la implementación vigente `20261017180200_ep_met_goal_reserve_commands.sql`, revisados en este checkout. La evidencia de carrera está en el run completado de `t_24bf59ca` (2026-10-18): dos sesiones `psql`, ambas exit 0; asserts de una respuesta `APPLIED` y otra `INSUFFICIENT_BACKING`, reservado=80000 frente a confirmado=100000; los IDs sintéticos fueron limpiados y se verificaron en cero users/accounts/goals/events/movements/ledger. Esta evidencia corresponde a la misma migración de reserva no modificada desde esa ejecución. El script `goal_reserve_concurrency.ps1` no se ejecutó: el guard bloqueó su invocación; no hubo bypass. El test SQL transaccional cubre retry DUPLICATE, propiedad/moneda, mismo origen-destino (solo reserva), transferencia explícita net-zero y ausencia de movimiento implícito para reserva-only.
- T040 continúa `[ ]`: no se accedió a Supabase alojado ni se habilitaron RPC remotos.

### QA independiente T039 (2026-10-18)

- `./gradlew testDebugUnitTest --rerun-tasks`: BUILD SUCCESSFUL, exit 0, 1m31s; ejecutó 33 tareas. Compilación/tests unitarios completos pasaron, con warnings Kotlin existentes.
- `./gradlew :app:connectedDebugAndroidTest`: BUILD FAILED, exit 1, 6m33s en dispositivo físico SM-A165M (Android 16/API 36). El XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` registra 381 tests y 16 failures. Triage independiente: 1 fallo vinculado al cambio EP-MET es el harness legado `KipuDatabaseSchemaTest#representativeVersionOneRowsSurviveValidationAndNormalReopen` (fixture v11 registra solo migración 1→11 y no alcanza v22; la cadena productiva 11→22 sí está registrada, por lo que no hay defecto de producción confirmado); 15 restantes no tienen evidencia de causalidad EP-MET. `MovementRevisionLocalTest` reprodujo 4 fallos por categoría SYSTEM `userId=null` rechazada por el planner (fuera de EP-MET). La corrida global sigue sin aprobar; el fallo del harness se asignó a T048.
- Pruebas SQL corridas contra `supabase_db_kipu-met-local-20261017`, PostgreSQL 17.11. Se verificó el nombre exacto del contenedor/DB, contenedor healthy y cero filas preexistentes de los IDs sintéticos del fixture. `_goal_test_fixture.sql` usa IDs sintéticos fijos y cada suite inicia `BEGIN`, incluye el fixture y finaliza `ROLLBACK`; no usa conexiones alojadas. `goal_opening_ledger_test.sql`, `goal_crud_test.sql`, `goal_reserve_test.sql`, `goal_lifecycle_test.sql`: cada una exit 0 (0–1 s); las cuatro terminaron en `ROLLBACK`. Ledger: `{inserted:0, ambiguous:1, candidates:5}` y cero filas ledger para la referencia dual.
- Lectura de asserts y ejecución T039: CRUD previo a la migración 1902 no cubría la política Free; esa brecha quedó cubierta por T013/T015 y QA independiente en `t_6b704eeb`. Reserve verifica reserva-only sin transaction_id, saldo libre (100000→80000), retry DUPLICATE, suficiencia completa, owner/moneda, misma cuenta sin movimiento, transferencia explícita net-zero y ausencia de ingreso/gasto implícito. Lifecycle mantiene la cobertura previa de liberación/reactivación y todavía no cubre el reemplazo al límite Free disparado por `RELEASE_GOAL` (T044/T045).
- `git diff --check`: exit 0 en las corridas T039 y T013/T015. No se ejecutaron migraciones alojadas; no se habilitaron comandos remotos. T040 sigue `[ ]`; T042/T043/T044/T045/T046/T047/T048 siguen abiertas para completar y probar los flujos locales restantes. No se cierra la activación remota ni toda EP-MET.

### Implementación y QA independiente T013/T015 (2026-10-18)

- Backend `t_aadfc263` añadió `supabase/migrations/20261018190200_ep_met_active_goal_quota_atomic_replacement.sql` y extendió `supabase/tests/database/goal_crud_test.sql` y `contracts/goal-command-contract.md`. RED confirmó que la implementación previa rechazaba CREATE cuando había cuatro metas `COMPLETED`; GREEN aplica solo el conteo `ACTIVE`, soporta el selector/revisión de reemplazo, respuesta/feed compuesto y rechazos all-or-none.
- En `supabase_db_kipu-met-local-20261017`, PostgreSQL 17.11: `goal_crud_test.sql`, `goal_reserve_test.sql`, `goal_lifecycle_test.sql` y `goal_opening_ledger_test.sql` pasaron (cada una terminó `ROLLBACK`); limpieza de fixtures confirmada. La carrera CREATE/UPDATE reportó `CREATE APPLIED`, `UPDATE REJECTED/REPLACEMENT_REQUIRED`, conteo final `ACTIVE=2`, y limpieza de usuarios/metas en cero.
- QA independiente `t_6b704eeb`: `APPROVED WITH OBSERVATIONS`, sin defectos confirmados en cuota, reemplazo compuesto, ownership, revisiones, hash/recibo o feed; `git diff --check` PASS y definición efectiva del RPC verificada localmente. Observaciones: la integración Android debe reconciliar atómicamente ambas metas antes de habilitar el cliente, y `RELEASE_GOAL` queda fuera de este alcance; el auditor no repitió el script PowerShell de concurrencia por su limpieza no transaccional. No se ejecutaron build Android, migraciones alojadas ni despliegue.

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Preparación**: T001 crea el límite de feature y no depende de otra fase.
- **Fundamentos**: T002 bloquea el cierre del contrato. T003 precede a T004, que valida localmente el diagnóstico; T040 bloquea migraciones alojadas y activación remota. T005–T008 preparan Room; T009 prepara PostgreSQL localmente; T010–T011 implementan el comando y sync comunes.
- **HU-30**: depende de los fundamentos y establece metas editables para HU-31/HU-32. T015 y T043 requieren la revisión arquitectónica del contrato de reemplazo atómico; T043 además espera T042 y la integración local-first de T017. T044/T045 extienden el mismo límite compartido a `RELEASE_GOAL` después de T015.
- **HU-31**: depende de HU-30 y de HU-18/HU-19 para movimientos/cuentas.
- **HU-32**: depende de HU-30/HU-31; no empezar liberación/archivo hasta que exista reserva auditable. T044 depende del backend de cuota T015 y la revisión de `RELEASE_GOAL`; T045 sigue a las pruebas SQL, T046 prepara la prueba UI y T047 integra después de T043/T045/T046.
- **Pulido**: depende de todas las HUs incluidas y de la decisión de historia heredada.

### Dependencias de historias

- **US1 (P1, HU-30)**: después de Fase 2; crea y administra la entidad base.
- **US2 (P1, HU-31)**: después de US1 y de HU-18/HU-19; consume metas y cuentas ya existentes.
- **US3 (P1, HU-32)**: después de US1 y US2; consume el historial de reservas y transferencias.

Las historias no se planifican en paralelo por sus dependencias explícitas del backlog. Las tareas `[P]` dentro de una fase usan archivos distintos y pueden prepararse a la vez una vez satisfechas sus dependencias.

### Oportunidades de paralelización

- Tras T002, se pueden preparar en paralelo T003 (preflight), T005 (cobertura de migración Room) y T006 (modelo puro).
- Dentro de cada HU, los tests unitarios, SQL y Compose marcados `[P]` están en archivos separados y pueden escribirse en paralelo antes de la implementación.
- En el fundamento, T007 (Room) y T009 (migración Supabase local) pueden avanzar en paralelo una vez que sus respectivos gates estén satisfechos; la aplicación alojada de T009 espera a T040.
- Dentro de US1, T015 (comandos SQL) y T016 (casos de uso) pueden avanzar en paralelo tras los tests de la HU; T017 integra ambos. La extensión de reactivación T043 espera el contrato revisado, T015/T017 y las pruebas UI T042.
- Dentro de US2, T023 (cálculo local) y T024 (RPC de reserva) pueden avanzar en paralelo tras los tests; T025–T028 integran ambos lados.
- Dentro de US3, T032 (RPC de lifecycle) y T033 (dominio local) pueden avanzar en paralelo tras los tests; T034–T036 integran persistencia y UI.

### Ejemplo de paralelo por historia

```text
US1 baseline: T012 + T013 + T014 en paralelo; después T015 + T016; luego T017 → T018 → T019.
US1 cuota refinada: T013 + T042 (pruebas); T043 implementa reactivación atómica por edición después de la revisión arquitectónica, T015 y T017.
US2: T020 + T021 + T022 en paralelo; después T023 + T024; luego T025 → T026 → T027 → T028.
US3: T029 + T030 + T031 en paralelo; después T032 + T033; luego T034 → T035 → T036.
US3 cuota refinada: después de T015/T034, ejecutar T044 (SQL RED) → T045 (RPC/migración); T046 (Compose RED) puede avanzar tras T042, y T047 integra después de T043 + T045 + T046.
T048 (harness Room v11→v22) se ejecuta después del triage T039 y es independiente del cambio de cuota.
```

## Estrategia de implementación

### MVP: HU-30

1. Completar Fase 1 y los gates aplicables de Fase 2.
2. Entregar creación/edición sin efectos financieros, cuenta enlazada opcional, límite Free e historial preservado.
3. Validar HU-30 independientemente; mantener reservas y movimientos fuera de este primer incremento.

### Incremental

1. HU-30 añade metas editables y locales/sincronizadas.
2. HU-31 añade reserva compartida por cuenta y transferencia interna solo por elección explícita.
3. HU-32 añade liberación/reasignación y ciclo de finalización/archivo.
4. Cada fase conserva las anteriores y respeta el gate de datos heredados.

## Notas

- Todas las tareas nuevas permanecen sin marcar hasta ejecutarse.
- `[P]` solo indica independencia de archivos/dependencias, no prioridad.
- Los escenarios independientes describen criterios de aceptación; sus tareas de prueba aparecen antes de la implementación por HU.
- El preflight sintético T004 no autoriza una migración remota. T040 exige datos no productivos representativos, revisión de outboxes antiguos y aprobación explícita antes de activar el backend.
- No se incluye HU-24; pertenece a EP-MOV y mantiene su alcance independiente.

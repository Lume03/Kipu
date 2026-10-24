# Verification report — EP-MOV HU-24 (implementación)

Veredicto: APPROVED para el alcance de verificación de implementación descrito en este informe.
Fecha: 2026-10-19
Branch: `004-ep-mov-movimientos-ledger`
Baseline: `58764f019d1fa1e224e45d0e244c17bbc304bd27` (main/HU-44)
HEAD histórico de la primera verificación de implementación: `7940a3c07b1ed3ded9e0d60fb308bacde6f5dc5c`.
HEAD de implementación/código revisado después de las correcciones funcionales: `a0649fda60dd016cdc28a3e47cef65fd60f03d27` (`test(app): cover goal transfer concurrency and replay`). Este es el commit de código verificado; no es el HEAD del repositorio al cierre documental.

Este veredicto está limitado a la verificación de implementación HU-24; no significa aceptación de toda EP-MOV/S6, autorización de despliegue ni validación en un proyecto Supabase hosted. Las cross-reviews independientes de Security y QA se registran por separado abajo.

## Cambios verificados

- En la reserva local explícitamente financiada por transferencia, Room crea una transacción `TRANSFER` (`GOAL_FUNDING`), dos asientos fuente/destino con signo opuesto, proyecciones desde el ledger, evento de meta enlazado a la misma identidad y outbox/recibo dentro de la transacción de staging existente. Reserva-only sigue sin crear transacción.
- El ID remoto de la transacción ahora conserva el `operation_id` cliente. La migración `20261019190000_ep_mov_goal_transfer_identity.sql` sustituye el UUID aleatorio previo en el cuerpo vigente del RPC solo si identifica exactamente una ocurrencia, y mantiene el RPC `SECURITY DEFINER` y sus grants explícitos (`authenticated` sí; `anon`/`PUBLIC` no).
- La historia/localización existente de liquidación de obligaciones conserva la autoridad de EP-DEU. Se agregaron pruebas exactas de principal sin interés: pago reduce principal/caja sin elevar gasto; cobro reduce por cobrar, aumenta caja y no eleva ingreso. Interés mantiene clasificación independiente.
- Una meta sin estado/cupo elegible y una cuenta fuente ajena se rechazan con código explicable, sin ledger, transacción, evento ni outbox parcial. La prueba DB existente de propietario/moneda continúa en la suite de reserva.
- No cambia el esquema Room ni se agrega tipo principal de movimiento, gasto, ingreso o activo por la reserva.

## RED/GREEN y resultados

1. **DB RED reproducible**: se volvió a instalar localmente la definición pre-cambio desde `20261017180200_ep_met_goal_reserve_commands.sql` y se ejecutó `goal_reserve_test.sql`. Falló (exit 3) por la aserción `transfer transaction id must match local idempotency identity`; el RPC devolvió un UUID aleatorio distinto al `operation_id`.
2. **DB GREEN**: aplicada solo al contenedor local `supabase_db_kipu-met-local-20261017` la migración nueva; `goal_reserve_test.sql` terminó exit 0 con `ROLLBACK`. Cubre reserva-only, transferencia explícita, identidad de transacción, reintento, owner/cuenta ajena, moneda, origen=destino, dos asientos net-zero y rechazo de meta `LOCKED_BY_PLAN` sin transacción/evento parcial.
3. **Room (verificación inicial, antes de correcciones de revisión)**: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.data.GoalReplacementRoomIntegrationTest` — `BUILD SUCCESSFUL`, 13/13 tests, 0 fallos/errores/omitidos; XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`. Incluye transferencia offline, rollback al fallar outbox, rechazo de meta pausada y owner de fuente ajeno. La evidencia final de la suite tras las correcciones está registrada abajo.
4. **Room deuda y reversión**: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtSettlementSyncTest` — `BUILD SUCCESSFUL`, 2/2 tests, 0 fallos/errores/omitidos; se verifica reintento y anulación atómica principal/interés.
5. **Unitarias domain**: `./gradlew :app:testDebugUnitTest --tests 'com.kipu.app.feature.goals.domain.ReserveGoalFundsTest' --tests 'com.kipu.app.feature.debts.domain.SettleDebtTest' --tests 'com.kipu.app.feature.debts.domain.DebtPrincipalCalculatorTest'` — `BUILD SUCCESSFUL`: 4 + 8 + 4 = 16 tests; XML individual en `app/build/test-results/testDebugUnitTest/`, todos con 0 fallos/errores/omitidos.
6. **pgTAP deuda**: `debt_settlement_test.sql` ejecutado por `psql -v ON_ERROR_STOP=1` sobre el DB local después de habilitar la extensión local `pgtap`; 22/22 aserciones `ok`, `ROLLBACK`, exit 0. El fixture se actualizó con `preset_id='GENERIC'` para cumplir la regla vigente de cuentas savings. No se alteró un esquema hosted.
7. **Compilación**: `./gradlew :app:compileDebugKotlin` — `BUILD SUCCESSFUL` (exit 0). La compilación de tests Android ocurrió también en la ejecución conectada.
8. **Seguridad de RPC comprobada en DB local**: `has_function_privilege(anon,...)=false`, `has_function_privilege(authenticated,...)=true`, `prosecdef=true`.
9. **Integridad del diff**: `git diff --check` — exit 0. Tras commits, el único untracked es el worktree preexistente `?? .worktrees/`; se dejó intacto.

## Especificaciones y commits

El refinamiento mínimo aclara FR-021 respecto de la identidad estable para reconciliar transferencia→ledger offline; escenarios/alcance FR-020–022 permanecen iguales. Se propagó al plan, modelo de datos y tareas (T126–T131); el flujo read-only `speckit.refine.status` y su resultado se detallan abajo. No se cambiaron artefactos de HU-20–23 ni HU-25.

Commits granulares verificados en la rama:

- `9b77e95` `docs(specs): refine HU-24 goal transfer identity`
- `e707b6f` `feat(db): preserve goal transfer operation identity`
- `41ce0c4` `test(db): cover goal transfer identity and debt fixtures`
- `f0f8bac` `feat(app): persist goal transfers in local movement ledger`
- `7940a3c` `test(app): verify HU-24 atomic ledger outcomes`
- `89230048f37025a115491b7c88675c339cbd095d` `docs(specs): record HU-24 verification results`
- `0387d2d391140c2ddb00569a5560b2c66b7d4d7e` `docs(specs): record HU-24 review corrections`
- `6c381bb1b233b202fcd18700b09a408ead53ab7e` `fix(app): revalidate goal transfer source balance atomically`
- `a0649fda60dd016cdc28a3e47cef65fd60f03d27` `test(app): cover goal transfer concurrency and replay`

## Límites

- No se ejecutó la suite global de instrumentación; no era necesaria para el cambio focalizado.
- La migración se aplicó únicamente al contenedor PostgreSQL local indicado; no se probó ni modificó Supabase hosted o producción y no hubo despliegue.
- No se realizó push.

## Cross-review independiente previa al merge

- Security (`t_e8f9cc0a`): APPROVED, sin hallazgos accionables, para el código HU-24 en `a0649fda60dd016cdc28a3e47cef65fd60f03d27` y el reporte hasta `cef4204267790afa5edd87b5bd27436d290318b3`. Revisión estática; no se ejecutaron pruebas en este pase ni se probó/aplicó nada en hosted/producción.
- QA (`t_b1f28d03`): APPROVED WITH OBSERVATIONS, sin hallazgos accionables, para HU-24 en la misma versión de código y reporte. Ejecutó Room de metas 15/15, `SettleDebtTest` 8/8, `DebtSettlementSyncTest` 2/2 y `git diff --check` (exit 0); no ejecutó pgTAP ni la suite global y no utilizó hosted/producción.
- Estos dictámenes son revisiones independientes acotadas a HU-24; no equivalen a aceptación integral de EP-MOV/S6, autorización de despliegue ni validación de base de datos hosted. No se atribuyen al veredicto de implementación de este informe.

## Correcciones de revisión — 2026-10-19

- Room concurrency RED/GREEN: el nuevo `concurrentGoalTransfersCannotOverspendOneSourceAccount` falló antes del arreglo con `AssertionError: expected <1> but was <2>` (dos transferencias de 7_000 aplicadas con origen 10_000; exit 1). `GoalLocalDataSource.stageCommand` ahora consulta dentro del `database.withTransaction` el saldo/reservas actuales de la cuenta origen y rechaza si ya no hay fondos suficientes. La suite focalizada `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.data.GoalReplacementRoomIntegrationTest` volvió a pasar en `SM-A165M - 16` (`BUILD SUCCESSFUL`, exit 0); valida una aplicada, una rechazada y ledger/proyección/outbox coherentes.
- Persistencia/replay tras cerrar y reabrir Room: `explicitGoalTransferSurvivesRoomReopenAndIdentityReplayIsIdempotent` crea una DB Room en archivo, cierra/reabre, comprueba TRANSFER, dos asientos, evento, outbox y recibo; vuelve a stagear con la misma identidad y verifica `Duplicate` sin duplicar transacción ni evento. Verificación de Tech Lead: la suite focalizada terminó exit 0, `BUILD SUCCESSFUL`, XML `TEST-SM-A165M - 16.xml` con 15 tests, 0 failures/errors/skips. Este resultado corresponde al HEAD de implementación/código revisado `a0649fda60dd016cdc28a3e47cef65fd60f03d27`.
- Unitarias focalizadas finales (re-ejecutadas por Tech Lead en ronda 2): exit 0; XMLs confirman 4 + 8 + 4 = 16 tests, sin fallos. pgTAP local final (re-ejecutado por Tech Lead en ronda 2): `goal_reserve_test.sql` y `debt_settlement_test.sql` terminaron exit 0 con `ROLLBACK`; la prueba de deuda reportó 22/22 aserciones `ok`. `git diff --check` también terminó exit 0. En cambio, DebtSettlementSyncTest (2/2), `compileDebugKotlin`, y la primera evidencia pgTAP/domain indicada arriba pertenecen a la verificación previa de implementación, no a una re-ejecución de ronda 2.
- `speckit.refine.status` read-only (workflow de `.specify/extensions/refine/commands/speckit.refine.status.md`): spec 18:47:43, plan 18:42:29, tasks 18:47:50, data-model 18:42:45. Indicadores: spec tiene `**Refined**` HU-24 y 8 user stories; plan 16 secciones y sin marker `**STALE**`; tasks 16 secciones, 4 tareas abiertas en todo el historial, y propagación HU-24 T126–T131; data-model contiene el incremento HU-24 y no tiene marker `**STALE**`. Bajo regla timestamp/marker del status, plan quedaba STALE por spec posterior, pese a contenido sincronizado. Se registró reconciliación explícita en plan.md; estatus semántico: sincronizado para HU-24, sin cambiar HU-20–23/25.

## HU-25 — verificación parcial de implementación (2026-10-24)

Estado: **sin aceptación integral**. Este pase verifica implementación local S7 y no altera el veredicto histórico HU-24.

### Comandos y resultados

- `.\gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` — `BUILD SUCCESSFUL`; compilan Kotlin principal y fuentes instrumentadas.
- `.\gradlew :app:testDebugUnitTest` — `BUILD SUCCESSFUL`; **675 tests, 0 failures, 0 errors, 2 skipped**, según los XML de `app/build/test-results/testDebugUnitTest/`.
- `npx supabase migration up --local` — aplicó la migración `20261024182114_ep_mov_refund_v1.sql` a la base Supabase local.
- `npx supabase test db supabase/tests/database/movements_refund_test.sql --local` — **31/31 pgTAP PASS**.
- Ejecución dirigida previa: `RefundTransactionTest` 11/11, `MovementRefundRoomTest` 4/4 y `CreditRefundOutstandingRoomTest` 1/1; `MovementRevisionTest` 20/20.
- `git diff --check` — exit 0 sobre el diff de implementación y documentación.

Las pruebas AndroidTest para persistencia/reapertura, rechazo de sync y UI se compilaron, pero no se ejecutaron: no había dispositivo/emulador conectado. Room versión 26 y su prueba de migración 25→26 compilan; el runtime de migración no se certifica aquí. La prueba de integración con el provider real de EP-PRE/HU-36 no existe todavía y sigue en T144. No se obtuvo revisión externa de QA, Finanzas ni Seguridad (T149). Por esas limitaciones, HU-25 no se declara aprobada. Supabase hosted/producción no se consultó ni modificó.

La preparación de Supabase fue únicamente local. El reset inicial detectó DDL duplicado preexistente en `20261021200100...`; se reparó sólo el historial de migraciones local antes de aplicar HU-25. Ese baseline local debe revisarse al reproducir desde cero.

## Verificación integrada focalizada Sprint 7 — 2026-10-24

Esta actualización posterior sustituye para T144/T148 el estado histórico anterior que indicaba que no había emulador disponible ni integración con EP-PRE. Los resultados aquí registrados corresponden al worktree `s7-e2e`; no equivalen a aceptación integral de HU-25 ni EP-PRE. Se usó el emulador `emulator-5554` (Pixel_10 AVD), Room real y repositorios/casos de uso reales. No se accedió a Supabase remoto/hosted. El caso de replay de sync usa Ktor `MockEngine` en memoria, sin servidor externo.

### Comandos y resultados

- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.budgets.BudgetHierarchyAlertsIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.budgets.BudgetCrudFinancialIsolationIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.budgets.data.sync.BudgetTombstoneReplayIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `.\gradlew.bat :app:testDebugUnitTest --tests com.kipu.app.feature.budgets.domain.ExpenseEligibilityTest` — `BUILD SUCCESSFUL`, 1/1 JVM.
- `.\gradlew.bat :app:testDebugUnitTest` — `BUILD SUCCESSFUL`, 800 tests descubiertos, 0 fallos, 0 errores y 2 omitidos, según los resultados XML de `app/build/test-results/testDebugUnitTest/`.

### Resultado funcional y corrección

- **T010, RED/GREEN**: la prueba `ExpenseEligibilityTest` consulta `MovementLocalDataSource.queryExpenseConsumption` sobre Room. Antes del arreglo falló: esperaba 3.700 minor units elegibles y recibió 8.700 porque la consulta contaba pagos de principal. Se corrigió `MovementDao.getExpensesInPeriod` para excluir `DEBT_PAYMENT` y operaciones no elegibles. Después, T010 pasó 1/1; confirma exclusión de pendientes, VOIDED, transferencias, principal, aportes a metas y otra moneda, e inclusión única de compra con tarjeta. El arreglo de producto está en `1821cfc`.
- **T144 / T022 / SC-005**: presupuesto mensual S/500 en Restaurantes y compra de S/100 en septiembre. `RegisterRefundTransaction` registra S/40 en octubre: el consumo neto de septiembre queda en S/60, octubre en S/0 de gasto y el ledger de caja refleja la recuperación de S/40. Repetir la misma identidad devuelve replay duplicado sin añadir asientos. Se rechaza otro reembolso de S/70 cuando sólo restan S/60; al completar S/60 el consumo de septiembre queda en S/0. La fila de compra original y su ledger conservan exactamente sus valores. La notificación del umbral continúa única.
- **HU-37 jerarquía y alertas**: un gasto de S/50 en Restaurantes aparece como S/50 en los presupuestos de Restaurantes, Alimentación y total. Con límite S/100, el consumo pasa de 49% a 51%; se persiste una única `BUDGET_ALERT` en `app_notifications`, y tres reevaluaciones adicionales no duplican el aviso.
- **T029 / HU-38**: el E2E con Room compara antes y después de crear, editar límite, finalizar y eliminar confirmado las cuentas, movimientos, filas de ledger, saldos de proyección/ledger y conteos financieros: permanecen idénticos. El tombstone impide recreación local. Además, `BudgetTombstoneReplayIntegrationTest` ejecuta el worker real frente a una respuesta HTTP antigua de presupuesto y periodo servida por `MockEngine` local; el replay no resucita el presupuesto.
- Las cuatro clases instrumentadas indicadas pasaron 1/1 cada una. No se ejecutó la suite instrumentada completa.

### Commits de código de estas pruebas y arreglo

- `1821cfc` `fix(movements): excluir principal del consumo de gastos`
- `daec163` `test(movements): verificar reembolso con consumo de presupuesto`
- `035da80` `test(budgets): cubrir jerarquia y alerta persistente`
- `212b575` `test(budgets): cubrir CRUD y tombstone sin alterar finanzas`
- `87ef36e` `test(budgets): probar elegibilidad con consulta Room real`
- `e74935d` `test(budgets): bloquear replay remoto con tombstone`

### Límites y tareas abiertas

No se ejecutó la migración Room en runtime en este pase, la suite instrumentada completa, una auditoría manual de TalkBack/UI ni revisión independiente de QA/Finanzas/Seguridad. En EP-PRE quedan abiertos T023 (presentación de historial/aviso), T031, T032 y T033; en EP-MOV queda abierto T149. No declarar aceptación integral de HU-25 hasta cerrar T149. No se accedió a Supabase hosted/producción ni se tocó la migración SQL de periodos o los DTO de sync de presupuestos.

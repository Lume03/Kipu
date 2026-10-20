# Verification report — EP-MOV HU-24 (implementación)

Veredicto: APPROVED para el alcance de verificación de implementación descrito en este informe.
Fecha: 2026-10-19
Branch: `004-ep-mov-movimientos-ledger`
Baseline: `58764f019d1fa1e224e45d0e244c17bbc304bd27` (main/HU-44)
HEAD verificado después de commits de implementación/pruebas: `7940a3c`.

Este veredicto no significa aprobación independiente de Security/QA, aceptación de toda EP-MOV/S6, autorización de despliegue ni validación en un proyecto Supabase hosted. La cross-review de seguridad, integridad financiera y pruebas sigue pendiente (T130); no se abrió PR ni se hizo merge.

## Cambios verificados

- En la reserva local explícitamente financiada por transferencia, Room crea una transacción `TRANSFER` (`GOAL_FUNDING`), dos asientos fuente/destino con signo opuesto, proyecciones desde el ledger, evento de meta enlazado a la misma identidad y outbox/recibo dentro de la transacción de staging existente. Reserva-only sigue sin crear transacción.
- El ID remoto de la transacción ahora conserva el `operation_id` cliente. La migración `20261019190000_ep_mov_goal_transfer_identity.sql` sustituye el UUID aleatorio previo en el cuerpo vigente del RPC solo si identifica exactamente una ocurrencia, y mantiene el RPC `SECURITY DEFINER` y sus grants explícitos (`authenticated` sí; `anon`/`PUBLIC` no).
- La historia/localización existente de liquidación de obligaciones conserva la autoridad de EP-DEU. Se agregaron pruebas exactas de principal sin interés: pago reduce principal/caja sin elevar gasto; cobro reduce por cobrar, aumenta caja y no eleva ingreso. Interés mantiene clasificación independiente.
- Una meta sin estado/cupo elegible y una cuenta fuente ajena se rechazan con código explicable, sin ledger, transacción, evento ni outbox parcial. La prueba DB existente de propietario/moneda continúa en la suite de reserva.
- No cambia el esquema Room ni se agrega tipo principal de movimiento, gasto, ingreso o activo por la reserva.

## RED/GREEN y resultados

1. **DB RED reproducible**: se volvió a instalar localmente la definición pre-cambio desde `20261017180200_ep_met_goal_reserve_commands.sql` y se ejecutó `goal_reserve_test.sql`. Falló (exit 3) por la aserción `transfer transaction id must match local idempotency identity`; el RPC devolvió un UUID aleatorio distinto al `operation_id`.
2. **DB GREEN**: aplicada solo al contenedor local `supabase_db_kipu-met-local-20261017` la migración nueva; `goal_reserve_test.sql` terminó exit 0 con `ROLLBACK`. Cubre reserva-only, transferencia explícita, identidad de transacción, reintento, owner/cuenta ajena, moneda, origen=destino, dos asientos net-zero y rechazo de meta `LOCKED_BY_PLAN` sin transacción/evento parcial.
3. **Room**: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.data.GoalReplacementRoomIntegrationTest` — `BUILD SUCCESSFUL`, 13/13 tests, 0 fallos/errores/omitidos; XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`. Incluye transferencia offline, rollback al fallar outbox, rechazo de meta pausada y owner de fuente ajeno.
4. **Room deuda y reversión**: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtSettlementSyncTest` — `BUILD SUCCESSFUL`, 2/2 tests, 0 fallos/errores/omitidos; se verifica reintento y anulación atómica principal/interés.
5. **Unitarias domain**: `./gradlew :app:testDebugUnitTest --tests 'com.kipu.app.feature.goals.domain.ReserveGoalFundsTest' --tests 'com.kipu.app.feature.debts.domain.SettleDebtTest' --tests 'com.kipu.app.feature.debts.domain.DebtPrincipalCalculatorTest'` — `BUILD SUCCESSFUL`: 4 + 8 + 4 = 16 tests; XML individual en `app/build/test-results/testDebugUnitTest/`, todos con 0 fallos/errores/omitidos.
6. **pgTAP deuda**: `debt_settlement_test.sql` ejecutado por `psql -v ON_ERROR_STOP=1` sobre el DB local después de habilitar la extensión local `pgtap`; 22/22 aserciones `ok`, `ROLLBACK`, exit 0. El fixture se actualizó con `preset_id='GENERIC'` para cumplir la regla vigente de cuentas savings. No se alteró un esquema hosted.
7. **Compilación**: `./gradlew :app:compileDebugKotlin` — `BUILD SUCCESSFUL` (exit 0). La compilación de tests Android ocurrió también en la ejecución conectada.
8. **Seguridad de RPC comprobada en DB local**: `has_function_privilege(anon,...)=false`, `has_function_privilege(authenticated,...)=true`, `prosecdef=true`.
9. **Integridad del diff**: `git diff --check` — exit 0. Tras commits, el único untracked es el worktree preexistente `?? .worktrees/`; se dejó intacto.

## Especificaciones y commits

El refinamiento mínimo aclara FR-021 respecto de la identidad estable para reconciliar transferencia→ledger offline; escenarios/alcance FR-020–022 permanecen iguales. Se propagó al plan, modelo de datos y tareas (T126–T131); el diff manual no encontró warnings `STALE`. No se cambiaron artefactos de HU-20–23 ni HU-25.

Commits granulares verificados en la rama:

- `9b77e95` `docs(specs): refine HU-24 goal transfer identity`
- `e707b6f` `feat(db): preserve goal transfer operation identity`
- `41ce0c4` `test(db): cover goal transfer identity and debt fixtures`
- `f0f8bac` `feat(app): persist goal transfers in local movement ledger`
- `7940a3c` `test(app): verify HU-24 atomic ledger outcomes`

## Límites

- No se ejecutó la suite global de instrumentación; no era necesaria para el cambio focalizado.
- La sesión aplicó la migración únicamente al contenedor PostgreSQL local indicado; no hubo uso de Supabase hosted, producción, deploy, push, PR ni merge.
- `T130` requiere revisión independiente antes de merge. Ningún resultado de este reporte sustituye esa aprobación.

## Correcciones de revisión — 2026-10-19

- Room concurrency RED/GREEN: el nuevo `concurrentGoalTransfersCannotOverspendOneSourceAccount` falló antes del arreglo con `AssertionError: expected <1> but was <2>` (dos transferencias de 7_000 aplicadas con origen 10_000; exit 1). `GoalLocalDataSource.stageCommand` ahora consulta dentro del `database.withTransaction` el saldo/reservas actuales de la cuenta origen y rechaza si ya no hay fondos suficientes. La suite focalizada `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.data.GoalReplacementRoomIntegrationTest` volvió a pasar en `SM-A165M - 16` (`BUILD SUCCESSFUL`, exit 0); valida una aplicada, una rechazada y ledger/proyección/outbox coherentes.
- Persistencia/replay tras cerrar y reabrir Room: `explicitGoalTransferSurvivesRoomReopenAndIdentityReplayIsIdempotent` crea una DB Room en archivo, cierra/reabre, comprueba TRANSFER, dos asientos, evento, outbox y recibo; vuelve a stagear con la misma identidad y verifica `Duplicate` sin duplicar transacción ni evento. Suite focalizada actual: `BUILD SUCCESSFUL`, 15/15.
- `speckit.refine.status` read-only (workflow de `.specify/extensions/refine/commands/speckit.refine.status.md`): spec 18:47:43, plan 18:42:29, tasks 18:47:50, data-model 18:42:45. Indicadores: spec tiene `**Refined**` HU-24 y 8 user stories; plan 16 secciones y sin marker `**STALE**`; tasks 16 secciones, 4 tareas abiertas en todo el historial, y propagación HU-24 T126–T131; data-model contiene el incremento HU-24 y no tiene marker `**STALE**`. Bajo regla timestamp/marker del status, plan quedaba STALE por spec posterior, pese a contenido sincronizado. Se registró reconciliación explícita en plan.md; estatus semántico: sincronizado para HU-24, sin cambiar HU-20–23/25.

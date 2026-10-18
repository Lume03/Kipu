# Feature Specification: EP-MET — Metas y Reservas Virtuales (Sprint 6)

**Feature Branch**: `006-ep-met-metas-reservas`

**Created**: 2026-10-17

**Status**: Refined

**Refined**: 2026-10-17 — Clarified backing account selection and multi-account aggregation (CHK001, CHK002), confirmed balance definition excluding pending/voided entries (CHK003), overfunding and target reduction lifecycle (CHK004, CHK005), entity distinction between linked vs backing accounts (CHK006), full reassignment and archive outcomes (CHK007, CHK008), physical deletion constraints and outbox/transfer definitions (CHK009), Freemium downgrade and locked-by-plan behavior (CHK010), measurable concurrency and backing invariants (CHK011), transfer atomicity (CHK012), idempotency vs operation ID reuse (CHK013), shortfall identification and user resolution (CHK014), Constitution §III owner isolation (CHK015), offline-first rejection recovery flow (CHK016), and legacy event compatibility and non-destructive migration requirements (CHK017).

**Refined**: 2026-10-17 — Separated synthetic local preflight validation for implementation completion from the representative-data and device-outbox release gate before hosted migration or remote activation.

**Refined**: 2026-10-18 — Clarified the approved Free goal-quota policy: only ACTIVE goals consume the two-goal allowance; COMPLETED goals are exempt, and a completed-goal reactivation at capacity requires explicit selection and an atomic replacement.

**Refined**: 2026-10-18 — Applied the same active-goal replacement rule when `RELEASE_GOAL` would reactivate a completed goal at the Free limit; the release and both status changes must be atomic.

**Input**: Sprint 6 Product Backlog V4.2, EP-MET stories HU-30, HU-31 and HU-32; Sprint 6 roadmap; confirmed product decisions.

## Clarifications

### Session 2026-10-17

- Q: Si aumentas el objetivo de una meta completada por encima de lo que tiene reservado, ¿qué debe pasar con su estado? → A: La meta vuelve a Activa; conserva la reserva y todos sus aportes, que cuentan para alcanzar el nuevo objetivo.
- Q: Si reduces el objetivo de una meta activa hasta un importe igual o menor a lo ya reservado (`nuevo_objetivo <= reservado_actual`), ¿qué ocurre con su estado? → A: La meta pasa inmediatamente a Completada (`COMPLETED`); conserva íntegra su reserva y todos sus aportes, sin liberar fondos ni mover dinero.
- Q: ¿Una meta puede respaldarse con aportes provenientes de distintas cuentas reales? → A: Sí. Una meta puede recibir aportes respaldados por diferentes cuentas propias en la misma moneda. El progreso de la meta suma el neto de todos sus eventos de reserva vigentes, mientras que cada cuenta real mantiene su propio control de saldo libre.
- Q: ¿Cómo se determina la cuenta de respaldo al aportar si la cuenta vinculada a la meta es opcional? → A: Si la meta tiene una cuenta vinculada, esa cuenta se preselecciona por defecto en el formulario de aporte (pudiendo el usuario elegir otra cuenta propia compatible). Si la meta no tiene cuenta vinculada (modo reserva virtual pura), el usuario debe seleccionar obligatoriamente una cuenta propia elegible de la misma moneda para respaldar el aporte.
- Q: ¿Qué cuenta exactamente como saldo confirmado para calcular el respaldo libre? → A: Únicamente la suma neta acumulada de los asientos firmes y asentados en el ledger (`internal.ledger_entries`). Se excluyen expresamente transacciones pendientes/candidatas (`PENDING`), borradores y transacciones anuladas (`VOIDED`) o compensadas.
- Q: ¿Qué ocurre cuando un gasto confirmado deja varias metas con saldo insuficiente (déficit de respaldo)? → A: No se alteran ni reducen automáticamente las reservas de las metas (se preserva la autonomía del usuario). Se reporta un estado de déficit/faltante en la cuenta y en todas las metas afectadas, bloqueando nuevos aportes desde esa cuenta hasta que el usuario resuelva el faltante mediante: (a) ingreso/transferencia de fondos, (b) liberación explícita de reservas, o (c) reasignación de reservas hacia otra cuenta con saldo libre.
- Q: Si no existe una copia no productiva del historial real, ¿qué evidencia permite terminar la validación local de EP-MET? → A: Ejecutar T003 sobre datos sintéticos deterministas en la instancia Supabase local aislada y registrar los casos consistentes y ambiguos. Esto valida el diagnóstico para completar la implementación local, pero no afirma qué contiene una base usada por clientes ni autoriza un despliegue. Antes de ejecutar migraciones alojadas o activar RPC remotos, sigue siendo obligatorio revisar T003 contra un conjunto no productivo representativo y revisar los outboxes pendientes de clientes antiguos; los comandos ambiguos o sin identidad/hash verificables requieren revisión manual.

### Session 2026-10-18

- Q: ¿Las metas `COMPLETED` consumen el cupo Free de dos metas? → A (decisión de producto): Solo las metas con estado `ACTIVE` consumen cupo. `COMPLETED`, `ARCHIVED` y `LOCKED_BY_PLAN` quedan excluidas.
- Q: Si una persona Free ya tiene dos metas `ACTIVE` y eleva el objetivo de una meta `COMPLETED` por encima de su reserva, ¿cómo se reactiva sin exceder el límite? → A (decisión de producto): Solicitar que elija cuál meta actualmente `ACTIVE` pasa a `LOCKED_BY_PLAN`; aplicar esa elección y la reactivación de la meta completada en una sola operación atómica, preservando reservas e historial.
- Q: Si una liberación desde una meta `COMPLETED` deja su reserva bajo el objetivo y ya hay dos metas `ACTIVE`, ¿cómo se mantiene el límite Free? → A (decisión de producto): Aplicar la misma selección explícita: elegir una meta `ACTIVE` para `LOCKED_BY_PLAN` y confirmar la liberación, el cambio de esa meta y la reactivación en una sola operación atómica.

## User Scenarios & Testing

### User Story 1 — Crear y editar metas (HU-30, Priority: P1)

Como usuario de Kipu, quiero crear y editar metas de ahorro con o sin una cuenta real vinculada, para organizar mis objetivos sin alterar mis saldos al definirlos.

**Por qué esta prioridad**: HU-30 establece la meta que necesitan los aportes, las reservas y las acciones de ciclo de vida de HU-31 y HU-32.

**Prueba independiente**: Crear una meta sin cuenta real vinculada y otra con una cuenta vinculada. Confirmar que crear no mueve dinero, que vincular no duplica un activo y que editar el objetivo conserva los aportes y recalcula el avance.

**Acceptance Scenarios**:

1. **Given** the user has an available active-goal allowance and does not want a dedicated real account, **When** they create a goal with a name, positive target and currency, **Then** Kipu explains the virtual-reserve mode and creates the goal without moving money or recording a contribution.
2. **Given** the user chooses an eligible real account, **When** they save the goal, **Then** the goal is linked to that account as an organizational reference/default destination, and its existing balance is counted only once.
3. **Given** a goal has contributions, **When** the user edits its name, appearance, date or target, **Then** the contribution history remains intact, progress is recalculated, and no money is moved.
4. **Given** a goal is Completed and its reserve is below a newly increased target, **When** the user saves the higher target, **Then** the goal returns to Active if the Free allowance permits it, retains its reserve and contribution history, and recalculates progress toward the new target.
5. **Given** an Active goal has reserved funds and the user reduces its target to an amount equal to or less than its current reserve, **When** the user saves the reduced target, **Then** the goal immediately transitions to Completed, retains its full reserve and contribution history, and creates zero fund movements.
6. **Given** the user requests physical deletion of a goal with no contributions, no linked transfers, and no pending local outbox operations, **When** they confirm, **Then** Kipu deletes that empty goal.
7. **Given** the goal has contribution history, a linked transfer, or a pending outbox operation, **When** the user requests deletion, **Then** Kipu strictly preserves its history, rejects physical deletion, and offers the applicable reserve-resolution and archive flow instead.
8. **Given** only goals with status `ACTIVE` count toward the Free allowance of two (goals with status `COMPLETED` do not consume it), **When** the user downgrades from Premium with more than two active goals, **Then** Kipu prompts the user to select which two remain active and places only unselected surplus `ACTIVE` goals into `LOCKED_BY_PLAN`, preserving reserves/history without deletion or hiding. A `COMPLETED` goal remains completed and does not consume quota; renewing Premium restores full active status automatically for goals locked by plan.
9. **Given** a Free user already has two `ACTIVE` goals and a `COMPLETED` goal below a newly increased target, **When** the user raises that target, **Then** Kipu asks which existing `ACTIVE` goal should move to `LOCKED_BY_PLAN`; after explicit selection, it atomically locks that goal and reactivates the completed goal, preserving both reserves and histories. Cancelling or omitting the selection leaves all goals unchanged.
10. **Given** a Free user already has two `ACTIVE` goals and a `COMPLETED` goal whose reserve is at or above its target, **When** a release would reduce that reserve below its target, **Then** Kipu asks which existing `ACTIVE` goal should move to `LOCKED_BY_PLAN`; confirming atomically records the release, locks the selected goal, and reactivates the completed goal while preserving both histories. Cancelling or omitting the selection leaves reserves and goal states unchanged.

---

### User Story 2 — Aportar y reservar dinero (HU-31, Priority: P1)

Como usuario de Kipu, quiero asignar dinero disponible a una meta y, cuando corresponda, registrar su traslado entre mis cuentas, para separar ahorro sin duplicar activos ni crear ingresos o gastos ficticios.

**Por qué esta prioridad**: Los aportes y las reservas dan avance financiero a una meta y conservan una única fuente de verdad para los saldos y el patrimonio.

**Prueba independiente**: Con una meta, S/1,000 de respaldo elegible y ninguna reserva previa, reservar S/200 sin un destino distinto. Verificar que la cuenta real y el patrimonio sigan en S/1,000, que queden S/800 libres para otras metas y que una segunda reserva no pueda usar el mismo respaldo.

**Acceptance Scenarios**:

1. **Given** an owned account has S/1,000 confirmed balance and no active reserve uses that backing, **When** the user reserves S/200 without moving it to another account (using the linked account default or selecting an owned account in virtual mode), **Then** the account balance and net worth remain S/1,000, while free backing falls to S/800.
2. **Given** a goal already has S/200 reserved from Account A, **When** the user contributes S/100 from Account B of the same currency with sufficient free backing, **Then** Kipu records the allocation event against Account B, the goal's total reserve aggregates to S/300, and free backing in Account B decreases by S/100.
3. **Given** the user explicitly chooses a different, same-currency owned account as the linked destination, **When** they confirm a S/200 contribution from the source account, **Then** Kipu records the internal transfer (with balanced source and destination ledger entries) and goal allocation together in a single atomic database transaction; if either fails, both roll back entirely.
4. **Given** the source and linked destination are the same account, **When** the user contributes S/50, **Then** Kipu records only the reserve and creates no transfer to itself.
5. **Given** only S/20 of backing is free in the selected account, **When** the user tries to reserve S/30, **Then** Kipu rejects the entire contribution without a partial reserve or financial effect.
6. **Given** a later confirmed expense reduces an account below its existing reserve, **When** Kipu recalculates backing, **Then** it keeps the real expense visible, flags a backing shortfall on the account and all affected goals, and prompts the user to resolve the deficit by depositing funds, releasing reserves, or reassigning reserves.

---

### User Story 3 — Liberar, completar y archivar metas (HU-32, Priority: P1)

Como usuario de Kipu, quiero liberar reservas y completar o archivar metas conservando su historia, para controlar explícitamente el destino del dinero.

**Por qué esta prioridad**: Las acciones del ciclo de vida de una meta deben conservar la historia financiera y no crear operaciones financieras implícitas.

**Prueba independiente**: Completar una meta dejando la reserva intacta, liberar una parte, rechazar una liberación excesiva y archivar una meta con historia solo después de resolver su reserva vigente.

**Acceptance Scenarios**:

1. **Given** valid contributions bring a goal to or above its target (including overfunding), **When** Kipu recalculates progress, **Then** the goal becomes Completed, progress reflects the full mathematical ratio or surplus, and its reserve remains in place without an automatic release, expense or transfer.
2. **Given** S/200 is reserved, **When** the user explicitly releases S/50, **Then** the reserve falls to S/150 and free backing increases by S/50 without creating operating income or moving money between accounts.
3. **Given** only S/30 is reserved on a specific backing account for that goal, **When** the user tries to release S/40, **Then** Kipu rejects the action entirely without changing amounts or history.
4. **Given** a goal has historical contributions and an active reserve of S/100, **When** the user initiates archiving the goal, **Then** Kipu offers three explicit paths: (a) Release remaining reserve to 0 and archive, (b) Reassign reserve to another eligible goal and archive once reserve is 0, or (c) Cancel the archive request, preserving the goal in its current state.
5. **Given** the user reassigns S/50 of reserve to another eligible goal using the same backing account, **When** confirmed, **Then** the source reserve drops by S/50, destination reserve increases by S/50, both progress values update, zero ledger movements are created, and an auditable trail is recorded on both goals.
6. **Given** the user separately requests an actual transfer after releasing a reserve, **When** they confirm that transfer, **Then** Kipu records it as an explicit internal transfer; release alone never implies that transfer.
7. **Given** a goal operation was saved locally while offline and subsequently rejected by the server during sync due to a revision mismatch, insufficient backing, or quota violation, **When** sync runs, **Then** Kipu displays a sync conflict badge with the rejection reason and enables the user to discard the local mutation, adjust and retry, or resolve the blocking condition.

### Edge Cases

- The target, contribution or release amount is zero, negative, or expressed in a currency incompatible with the selected backing.
- The selected real account is not owned by the user (`user_id != auth.uid()`), is unavailable for the requested operation, or has a different currency.
- Only `ACTIVE` goals consume the Free allowance; `COMPLETED` goals do not. On downgrade, only surplus `ACTIVE` goals are candidates for `LOCKED_BY_PLAN`; goals that were `COMPLETED` remain completed. If either a target edit or release would reactivate a `COMPLETED` goal above the Free cap, require explicit selection of an existing `ACTIVE` goal to lock and apply the initiating operation plus both state changes atomically; cancellation makes no change.
- Several goals attempt to reserve the same backing account concurrently; row-level locking on backing accounts in deterministic order prevents over-allocation and guarantees that accepted reserves never exceed confirmed balance.
- An Active goal's target is edited down to an amount less than or equal to current reserve; goal transitions immediately to Completed without moving money.
- A goal is contributed beyond 100% of its target; the full reserved amount is retained, progress indicates the exact ratio or surplus, and status remains Completed.
- A confirmed expense reduces real backing below existing reserves; Kipu must not suppress or reverse that expense, surfaces the shortfall, and requires explicit user resolution.
- A request is retried with the exact same `operation_id` and canonical `request_hash`; Kipu returns the cached receipt (`DUPLICATE`) with zero duplicate effects.
- An `operation_id` is reused with a different canonical payload or hash; Kipu rejects the command with a deterministic conflict error (`CONFLICT/OPERATION_ID_REUSED`).
- A goal with history is archived while funds remain reserved; no orphan reserve may remain. Archiving is only committed when active reserve reaches zero.
- Legacy goal events from prior database versions lacking a backing account reference are preserved read-only as `LEGACY_UNLINKED` and are never destructively deleted or retroactively altered by automated guesswork.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST let the user create and manage their own goals, each with a name, positive target amount and currency; a target date MAY be omitted. Every goal MUST be strictly scoped to the authenticated user (`user_id = auth.uid()`) per Constitution §III.
- **FR-002**: The system MUST provide a virtual goals grouping for multiple goals. The grouping and each reserve MUST NOT be counted as an asset in addition to the real account backing it. A goal MAY be backed by multiple eligible accounts of the same currency across distinct contribution events.
- **FR-003**: A real account linked to a goal (`linked_account_id` on the Goal entity) MUST be optional. Kipu MUST explain the virtual-reserve and linked-account modes; choosing a linked account MUST NOT count its balance twice. When present, the linked account MUST serve as the preselected default backing account for contributions; if omitted or overridden, the user MUST explicitly select an eligible owned account of matching currency.
- **FR-004**: Creating a goal MUST NOT move money, create a contribution or change account balances.
- **FR-005**: The system MUST apply the active-goal allowance from HU-57 and P31. Only goals whose current status is `ACTIVE` consume the Free allowance of at most two; `COMPLETED`, `ARCHIVED` and `LOCKED_BY_PLAN` goals do not consume it. If a Free user exceeds the quota after a Premium downgrade, the system MUST prompt the user to select which two `ACTIVE` goals remain operational, marking only the unselected surplus `ACTIVE` goals as `LOCKED_BY_PLAN`. If a target edit or `RELEASE_GOAL` would reactivate a `COMPLETED` goal and exceed the cap, the system MUST ask the user to select an existing `ACTIVE` goal to move to `LOCKED_BY_PLAN` and apply the initiating operation plus both state changes atomically; cancellation or missing selection MUST leave all goal states and reserves unchanged. Plan restrictions MUST NOT delete, hide, or alter goals, reserves, or financial history, and goals in `LOCKED_BY_PLAN` MUST remain consultable and resolvable (archivable/releasable). Goals already `COMPLETED` remain completed and are not locked solely to satisfy the quota.
- **FR-006**: Editing goal details or target MUST preserve contribution and reserve history, recalculate progress, and MUST NOT move money or change the reserve amount by itself. If the user reduces the target of an Active goal such that `new_target <= current_reserved`, the system MUST immediately transition the goal to Completed (`COMPLETED`).
- **FR-007**: Changing a linked account while a goal has reserves MUST require the user to resolve the existing backing before the change is confirmed, or ensure that existing event backing remains linked to its historical account.
- **FR-008**: Physical deletion MUST be strictly limited to a pristine empty goal with zero contribution events, zero linked transfer operations, and zero pending local outbox operations, and MUST require explicit user confirmation (`DELETE_EMPTY_GOAL`). Any goal with historical events, linked movements, or pending outbox items MUST NOT be physically deleted (`REJECTED/HISTORY_PRESERVED`) and MUST be archived instead once reserves are resolved.
- **FR-009**: Before accepting a contribution, the system MUST validate that the goal is active, user-owned (`user_id = auth.uid()`), the amount is strictly positive, the backing account is user-owned and eligible, and currencies match. The backing account MUST be recorded explicitly on the resulting reservation event (`backing_account_id`).
- **FR-010**: Free backing MUST be calculated per account from confirmed real balance less all active reserves using that same backing:
  `free_backing(account) = confirmed_balance(account) - sum(active_reserved(account across all goals))`.
  Confirmed real balance (`confirmed_balance`) MUST be derived strictly from posted ledger entries (`internal.ledger_entries`), explicitly excluding pending candidate transactions (`PENDING`) and voided transactions (`VOIDED`). The same money MUST NOT be reserved more than once across goals.
- **FR-011**: If free backing is insufficient, the system MUST reject the entire contribution without a partial reserve or financial effect (`REJECTED/INSUFFICIENT_BACKING`). Database transactions modifying reserves MUST acquire row-level locks on the backing accounts in deterministic order by account ID, guaranteeing that `sum(active_reserved(account)) <= confirmed_balance(account)` holds across concurrent requests.
- **FR-012**: A reserve-only contribution MUST record an allocation event and reduce free backing without changing the real account balance, total assets or net worth.
- **FR-013**: If the user explicitly contributes by transferring funds to a different linked account, the system MUST record the internal transfer (with balanced source and destination ledger entries) and goal allocation event together in a single atomic database transaction. If either part fails, both MUST roll back completely. The system MUST NOT initiate or claim to execute an external bank transfer. If source and destination are the same account, it MUST record only the reserve.
- **FR-014**: When a later confirmed financial operation leaves real backing below its reserves (`confirmed_balance(account) < sum(active_reserved(account))`), the system MUST retain and show that operation, calculate the shortfall (`shortfall = sum(active_reserved) - confirmed_balance`), display a shortfall alert on the account and affected goals, and allow the user to resolve the deficit by depositing funds, releasing reserves, or reassigning reserves. The system MUST NOT arbitrarily deduct or alter reserves automatically.
- **FR-015**: Goal progress and reserved amounts MUST be derived strictly from valid contribution, release and adjustment history:
  `goal_reserved(goal) = sum(valid goal event deltas for that goal)`.
  The reserved amount MAY exceed the target amount (overfunding); the reserved amount MUST NOT be capped or truncated by the target amount, and progress MUST reflect the actual mathematical ratio or surplus. The status label MUST NOT be used as the financial amount.
- **FR-016**: Reaching or exceeding 100% of the target MUST mark the goal Completed (`COMPLETED`) while leaving the reserve in place. Completion MUST NOT automatically release funds, create an expense or transfer money.
- **FR-017**: If the user raises a Completed goal's target above its current reserve, the system MUST return the goal to Active (`ACTIVE`), preserve its reserve and contribution history, and recalculate progress toward the new target. For a Free user at the two-active-goal limit, the transition MUST follow FR-005: the user explicitly selects an existing active goal to lock, and both state changes are applied atomically.
- **FR-018**: A release MUST have a positive amount no greater than the current reserve backed by that specific account for that goal. It MUST reduce the reserve and increase free backing without creating operating income. If the released goal is `COMPLETED` and the resulting reserve falls below its target, it MUST reactivate; for a Free user already at the two-`ACTIVE` limit, the user MUST explicitly select another `ACTIVE` goal to lock, and the release event plus both state changes MUST be applied atomically under FR-005. Cancellation or missing selection MUST apply no release. A real transfer after release MUST be a separate, explicit operation.
- **FR-019**: Before archiving a goal with a remaining reserve (`reserved > 0`), the system MUST require the user to resolve the reserve by: (a) releasing the remaining reserve to free backing, (b) reassigning the reserve to another eligible goal, or (c) cancelling the archive request. An archived goal MUST have zero active reserves and MUST preserve all its historical events.
- **FR-020**: Reassignment between goals (`REASSIGN_GOAL_RESERVE`) MUST atomically reduce the reserve on the source goal and increase the reserve on the destination goal, recalculate progress on both, and preserve an auditable history linked by a shared operation ID. If the backing account does not change, it MUST NOT create a ledger movement. If backing must change between accounts, it MUST NOT simulate a transfer and MUST require an explicit internal transfer between the real accounts.
- **FR-021**: Goal changes and related financial effects MUST be atomic and retry-safe:
  - An identical retry (matching `operation_id` and canonical `request_hash`) MUST be idempotent and return the cached receipt (`DUPLICATE`) with zero duplicate effects.
  - Reusing an `operation_id` with a different payload or hash MUST be rejected as a conflict (`CONFLICT/OPERATION_ID_REUSED`).
  - Stale revisions MUST be rejected as conflicts (`CONFLICT/REVISION_MISMATCH`).
  - Local-first operations saved offline and subsequently rejected by the server MUST be surfaced in the UI with a conflict badge and explanation, enabling the user to discard the local mutation, adjust and retry, or resolve the underlying cause.
- **FR-022**: Migrations and legacy compatibility MUST be non-destructive. The `backing_account_id` column in `goal_events` MUST be nullable for legacy rows. Existing legacy events, transactions, and ledger entries MUST be preserved read-only without deletion or unverified automated modification. Legacy events without a determinable backing account MUST be flagged as `LEGACY_UNLINKED` pending audit. All new operations MUST strictly enforce non-null backing accounts and zero expense/income ledger generation. Before any hosted migration or remote command activation, an approved read-only preflight against representative non-production history and a review of pending commands on older clients MUST be recorded; synthetic local fixtures validate the diagnostic only and MUST NOT substitute for this release gate.

### Key Entities

- **Meta (Goal)**: Objetivo de ahorro del usuario (`public.goals`, Room `GoalEntity`) con identificador estable, `user_id` de propietario, nombre, importe objetivo positivo (`target_minor`), moneda, fecha opcional, cuenta real vinculada opcional (`linked_account_id`), estado (`ACTIVE`, `COMPLETED`, `ARCHIVED`, `LOCKED_BY_PLAN`), revisión y auditoría.
- **Evento de meta (GoalEvent)**: Hecho financiero inmutable (`public.goal_events`, Room `GoalEventEntity`) con identificador estable, `user_id`, `goal_id`, tipo (`ALLOCATION`, `RELEASE`, `ADJUSTMENT`), importe con signo (`delta_minor`), moneda, cuenta de respaldo obligatoria para nuevos eventos (`backing_account_id`), referencia opcional a transferencia interna (`transaction_id`) y clave de operación idempotente (`operation_id`).
- **Cuenta de respaldo (Backing Account)**: Cuenta real propia del usuario cuyo saldo confirmado en el ledger respalda una o más reservas.
- **Cuenta vinculada (Linked Account)**: Atributo opcional de la meta que actúa como cuenta organizativa y destino/origen predeterminado de aportes; no reserva fondos por su sola presencia.
- **Agrupador virtual de metas**: Agrupación lógica en UI/dominio que suma asignaciones; no constituye un activo contable adicional ni se agrega al patrimonio.
- **Transferencia interna**: Movimiento confirmado explícitamente entre dos cuentas reales del usuario con dos asientos compensatorios de ledger; no crea ingreso ni gasto.
- **Recibo de operación de meta (Goal Operation Receipt)**: Registro idempotente (`operation_id`, `request_hash`, `contract_version`, `sync_state`, `result`) que garantiza reintentos seguros y detección de conflictos.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In every creation and editing acceptance case, account balances and net worth change by zero unless the user separately confirms an internal transfer.
- **SC-002**: For every reserve-only contribution and release, total assets and net worth change by zero; a reserve is counted only within its underlying real backing.
- **SC-003**: Under all single and concurrent allocation requests, the backing invariant `sum(active_reserved(account)) <= confirmed_balance(account)` holds across all accounts and goals; insufficient backing and excessive releases produce zero partial financial effects and zero over-allocation.
- **SC-004**: Completing a goal creates zero automatic releases, expenses and transfers, and leaves its reserve unchanged.
- **SC-005**: Every goal with contribution history remains consultable after archive, and every archived goal has exactly zero unresolved active reserve.
- **SC-006**: Replaying a confirmed contribution, transfer or release with identical operation ID and hash produces zero duplicate financial effects (`DUPLICATE`). Reusing an operation ID with a modified payload deterministically produces a conflict error (`CONFLICT/OPERATION_ID_REUSED`).
- **SC-007**: Under the Free allowance, no more than two goals are `ACTIVE`; `COMPLETED`, `ARCHIVED` and `LOCKED_BY_PLAN` goals do not consume active slots. All transitions that would create an additional active goal at capacity require explicit selection and atomic replacement. Plan restrictions preserve 100% of existing goals, reserves, and history under `LOCKED_BY_PLAN`.
- **SC-008**: Database migrations preserve 100% of historical goal events and ledger entries without unverified destructive updates or fabricated account linkages.

## Assumptions and Scope Boundaries

- This EP-MET specification covers HU-30, HU-31 and HU-32, assigned to Integrante 3 in Sprint 6. HU-30 depends on HU-57 and has partial integration with HU-07; HU-31 depends on HU-18, HU-19 and HU-30 and has partial integration with HU-07; HU-32 depends on HU-30 and HU-31.
- Sprint 6 sequences HU-30 before HU-31 and HU-32 after HU-31. HU-24 belongs to EP-MOV and remains a separate story; this specification does not redefine its application of movements to goals.
- HU-57 and P31 allow at most two `ACTIVE` goals on Free. `COMPLETED` goals do not consume this allowance; any target-edit or release-triggered reactivation at quota requires the explicit, atomic selection described in FR-005. Only surplus `ACTIVE` goals upon downgrade enter `LOCKED_BY_PLAN` without loss of data or reserves.
- Local implementation completion is distinct from hosted backend activation. Synthetic fixtures may close the local diagnostic-validation task; the representative-history preflight and older-client outbox review remain an operational release gate and must be completed before hosted migrations or remote goal commands are enabled.
- A reserve is an allocation of existing money, not a new asset. Registering an explicit transfer between two owned accounts follows the existing transfer behavior and does not execute an external bank transfer.
- Insufficient backing rejects the complete contribution, as specified by process P18; it is not partially accepted.
- Multi-account backing allows a goal to hold reserves backed by different accounts in the same currency; progress aggregates across all valid events.
- Target reduction to an amount at or below current reserve marks the goal `COMPLETED` immediately. Overfunding preserves the full reserve amount without capping.
- Source references: Kipu Product Backlog V4.2 (HU-24, HU-30–HU-32, HU-57); Sprint 6 roadmap; process P18, “Gestionar metas y reservas”; process P31, “Aplicar cupos, accesos y vigencia offline”; Architecture and Data V4.2, goals and goal events; Kipu Constitution 2.0.0; Obsidian Mind notes “Spec Kit in Kipu” and “Spec Kit — Referencia Global”.

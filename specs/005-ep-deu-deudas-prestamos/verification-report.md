# Verification report — EP-DEU (HU-26 a HU-29)

- **Fecha:** 2026-11-05
- **Veredicto global:** APPROVED
- **Branch:** `005-ep-deu-deudas-prestamos` / `main`
- **Alcance:** HU-26 a HU-29 (Épica EP-DEU - Deudas y Préstamos):
  - HU-26: Registrar y administrar una deuda propia (PAYABLE, desembolso en cuenta vs histórico, cupo Free, edición de descriptores y protección de borrado).
  - HU-27: Registrar dinero prestado a otra persona (RECEIVABLE, cobro/desembolso en cuenta vs histórico, validación de cuenta de origen y protección de borrado).
  - HU-28: Pagar o cobrar principal e interés por separado (liquidación atómica `SETTLE_DEBT`, exclusión de principal de resultados operativos, reconocimiento de interés real, rechazo de sobrepago, e idempotencia). Incluye corrección de BUG-003.
  - HU-29: Planificar cuotas, recibir avisos y cerrar obligaciones (`SET_DEBT_SCHEDULE`, recordatorios sin efecto financiero, cierre con saldo cero, reapertura `SETTLED -> ACTIVE` tras anular liquidación [BUG-003]).

## Evidencia automatizada (2026-11-05)

1. **Unitarios JVM (`:app:testDebugUnitTest`):**
   - 1.678 tests totales: **1.675 PASS, 0 FAIL, 3 SKIPPED** (BUILD SUCCESSFUL en 1m59s).
   - Pruebas clave de EP-DEU aprobadas:
     - `DebtPaymentVoidViewModelTest` (4 tests PASS): `cancelSelectionDoesNotReadOrMutatePayment`, `confirmationUsesCurrentHeadAndPreventsDoubleSubmitOrDismissWhileRunning`, `voidedOrNonPaymentActivityCannotBeSelected`, `rejectionKeepsSelectionAndShowsErrorForRetry` (ejecutado PASS JVM 2026-11-05).
     - `SettleDebtTest`: validación de principal e interés, cálculo de pendientes, sobrepago y dirección del flujo.
     - `DebtScheduleTest`: división exacta de cuotas sin residuo ni efecto financiero.
     - `DebtMovementAccountingTest`: principal no computa en resultados operativos ni presupuesto; interés real computa una sola vez.
     - `DebtPrincipalCalculatorTest`: cálculo exacto de amortización.

2. **Instrumentados en dispositivo físico (Galaxy A16 / Android 16):**
   - 22 clases focalizadas ejecutadas: **142 PASS, 0 FAIL, 2 @Ignore BUG (en metas), 4 omitidos (opt-in remoto desactivado)**.
   - Pruebas instrumentadas directas de EP-DEU:
     - `com.kipu.app.feature.debts.data.DebtSettlementSyncTest`: **7 PASS** (ejecutado PASS A16 2026-11-05). Cubre:
       - `payablePrincipalReducesCashAndDebtWithoutOperatingExpense`: amortización reduce caja y pasivo sin aumentar gasto operativo.
       - `receivablePrincipalIncreasesCashAndReducesLoanWithoutOperatingIncome`: cobro aumenta caja y reduce cuenta por cobrar sin aumentar ingreso operativo.
       - BUG-003: `voidingPrincipalReversesPrincipalAndInterestTogether`: anular principal revierte atómicamente la pareja principal e interés.
       - BUG-003: `voidingInterestReversesPrincipalAndInterestTogether`: anular interés revierte atómicamente ambos tramos.
       - BUG-003: `voidPrincipalReopensSettledDebtRecalculatesInstallmentAndReplayAddsNothing`: anular liquidación reabre deuda cerrada (`SETTLED -> ACTIVE`), recalcula cuotas y replay no añade nada.
       - BUG-003: `alreadyVoidedInterestRejectsPrincipalVoidBeforeAnyPartialWrite`: segundo miembro ya anulado rechaza anulación antes de cualquier escritura parcial.
       - Sincronización y hashes SHA-256 reales (corregidos en F4).
     - `com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest`: **25 PASS** (ejecutado PASS A16 2026-11-05). Cubre sincronización dependiente de liquidación y anulación de deudas (`settlementVoidWaitsForDebtOutboxBeforeCallingRpc`, `failedSettlementDependencyMarksVoidAndDebtConflictWithoutRpc`, `voidRpcConflictMarksDependentSettlementConflict`, `permanentVoidRpcRejectionMarksDependentSettlementFailedWithoutUndoingLocalVoid`).
     - `com.kipu.app.feature.movements.presentation.MovementObligationEntryPointTest`: **3 PASS** (ejecutado PASS A16 2026-11-05; `expenseOffersDebtPrincipalApplication` y `incomeOffersLoanPrincipalCollection`).
     - Pruebas históricas previas en A16: `DebtSettlementFlowTest` (Compose flow), `DebtScheduleAndClosureFlowTest`, `DebtReminderSchedulerTest`, `DebtRoomMigrationTest` (v18->v19) aprobadas sin fallos.

3. **Base de datos local (pgTAP):**
   - 74 archivos / **1.422 tests PASS** (2026-11-05).
   - Suites específicas de EP-DEU:
     - `debt_settlement_test.sql`: **22 aserciones PASS** (RPC atómica de liquidación, dos asientos, interés clasificado, replay idempotente, rollback).
     - `debt_payable_opening_test.sql`: PASS (apertura PAYABLE con y sin flujo de caja).
     - `debt_receivable_opening_test.sql`: PASS (apertura RECEIVABLE y requerimiento de cuenta).
     - `debt_schedule_and_closure_test.sql`: PASS (suma exacta de cronograma, cierre y reapertura).
     - `debt_details_history_guard_test.sql`: PASS (protección contra borrado si existe historia).
     - `debt_sprint5_security_and_idempotency_test.sql`: PASS (RLS, ownership e idempotencia de comandos).
     - `debt_sprint5_migration_test.sql`: PASS (compatibilidad de migraciones).

## Evidencia QA previa (docs/qa/...)

- `docs/qa/auditoria-sprint-6-2026-10-19/informe.md`:
  - HU-26/27: Creación y consulta de deuda propia histórica (PEN 100) y de préstamo nuevo (PEN 1.00) verificadas en dispositivo PASS (`capturas/incidencias/bug-001-antes.png`).
  - HU-28: Pago parcial de principal PEN 1.00 en `QA_AUDIT_20261020_CRON` verificado PASS; rechazo de sobrepago en UI verificado PASS.
  - BUG-003 detectado originalmente: menú de movimiento advertía vínculo con deuda pero remitía a detalle sin acción visible de anulación en UI (`capturas/incidencias/bug-003-void-blocked-linked-debt.png`, `capturas/incidencias/bug-003-debt-detail-no-void-action.png`).
  - HU-29: Guardado de cronograma, ráfaga de pulsaciones y reinicio PASS; cierre controlado de préstamo con saldo cero PASS.
- `docs/qa/activacion-sync-planificacion-2026-10-26.md`:
  - Activación y verificación de sincronización de Planificación en servidor y dispositivo: siete comandos de Deudas sincronizados exitosamente (`SYNCED`, sin error).
  - Deuda demo remota: edición, idempotencia DUPLICATE, y rechazo `HISTORY_PRESERVED` al intentar borrado físico comprobados.
  - Checkpoint 316 alcanzado para Deudas, Metas y Movimientos.

## Cambios de esta ronda (2026-11-05)

1. **Corrección BUG-003 (Fase 1.1 `f1-1-anular-pago.patch`, `f1-1b-fix-tests-constructor.patch`):**
   - Se implementó la anulación de pagos de liquidación directamente desde la pantalla de detalle de la deuda (`DebtDetailScreen.kt`, `DebtDetailViewModel.kt`, composable `DebtPaymentVoidAction`).
   - Se reutiliza la misma RPC atómica `void_transaction_v1` a través de `VoidTransaction`, asegurando simetría de anulación tanto desde el detalle de la deuda como desde el movimiento en el historial de transacciones (Decisión Owner 2026-11-05).
   - Invalida atómicamente el par principal + interés vinculado sin escrituras parciales, revierte los saldos y cuotas, y reabre la obligación a estado `ACTIVE` si se encontraba liquidada (`SETTLED`).
   - Se añadió `DebtPaymentVoidViewModelTest.kt` (4/4 tests PASS).
   - Se agregaron y ejecutaron los tests de integración en `DebtSettlementSyncTest.kt` (4 nuevos tests instrumentados para BUG-003, 7/7 PASS en Galaxy A16) y `SyncMovementsWorkerTest.kt` (25 PASS en Galaxy A16).
2. **Actualización de contrato de comandos (`contracts/debt-command-contract.md`):**
   - Se retiró la autodenominación obsoleta de "no implementado", formalizando el estado como IMPLEMENTADO y verificado para HU-26–HU-29 con el comportamiento de anulación de pagos (BUG-003).

## Pendientes y bloqueos

- **Dispositivo Galaxy S24:** no ejecutado debido a `INSTALL_FAILED_UPDATE_INCOMPATIBLE` por diferencia de certificados entre release instalada y firma debug local. Validación instrumentada completada en Galaxy A16.
- Ningún bloqueo funcional o de datos para EP-DEU; comandos de backend desplegados y verificados en local (pgTAP 74/74 archivos PASS) y servidor remoto (114 migraciones aplicadas).

## Veredicto por HU

| Historia | Descripción | Veredicto | Justificación |
|---|---|---|---|
| **HU-26** | Registrar y administrar una deuda propia | **CUMPLIDO** | Apertura PAYABLE (desembolso en cuenta e histórica), límite de cupo Free, edición descriptiva y protección contra borrado físico verificados en unitarios, instrumentados y pgTAP. |
| **HU-27** | Registrar dinero prestado a otra persona | **CUMPLIDO** | Apertura RECEIVABLE (desembolso desde cuenta e histórica), validación de cuenta de origen y protección de historia verificados en unitarios, instrumentados y pgTAP. |
| **HU-28** | Pagar o cobrar principal e interés por separado | **CUMPLIDO** | Amortización y cobro atómicos, separación de interés real sin alterar gastos/ingresos operativos para principal, e idempotencia; BUG-003 corregido y verificado (anulación desde detalle y movimiento). |
| **HU-29** | Planificar cuotas, recibir avisos y cerrar obligaciones | **CUMPLIDO** | Cronograma con suma exacta, recordatorios sin efecto financiero, cierre con saldo cero, y reapertura de `SETTLED` a `ACTIVE` tras anulación de liquidación (BUG-003). |

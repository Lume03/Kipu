# L2 — Reembolsos y anulación de compras con tarjeta

Fecha: 2026-11-11. Rama: Lume03/audit-fixes. Estado: implementación y compilación completadas; pruebas escritas, no ejecutadas por instrucción del despacho.

## Contrato y comportamiento

- Migración nueva `20261111130000_card_purchase_void_and_refund_rules.sql`, sin aplicar: amplía `internal.apply_movement_revision_v1`, invocada por `public.void_transaction_v1`, para anular CARD_PURCHASE y REFUND conservando hash, recibos, revisiones, identidades de efectos y publicación del feed. Conserva también la excepción de categorías históricas introducida por 20261104051901.
- La compra se bloquea si sus cuotas tienen cualquier credit_payment_allocation. Se bloquea también cualquier compra con enlaces REFUND activos, incluidos gastos estándar con devoluciones históricas. La comprobación precede a la reversión y comparte el bloqueo de la compra con el RPC de reembolso; para la tarjeta se toma además su bloqueo de fila.
- Se anulan las cuotas y el movimiento, se compensa el asiento y se utiliza `private.refresh_credit_installment_statuses`; la deuda procede de `private.credit_installment_balances`, que excluye compras anuladas. Anular REFUND conserva el enlace histórico, pero su estado VOIDED lo excluye del cálculo y restaura el saldo de las cuotas.
- El RPC refund_transaction_v1 rechaza gastos sin tarjeta con REFUND_REQUIRES_CARD_PURCHASE y fechas fuera de [día de compra, hoy] en America/Lima. El dominio y el data source eliminan la elegibilidad de gastos estándar; la UI hereda esa regla. Se admiten compras de tarjeta de 1 a 36 cuotas, también para los extornos importados; ConfirmStatementImport ya usa isRefundEligible y el mismo caso de uso y no necesitó cambios.
- Room anula y revierte en una única transacción, recalcula saldo y estados de cuotas y reutiliza VOID_TRANSACTION. Historial y detalle reciben el bloqueo local para impedir eliminar compras con pagos/reembolsos; el detalle muestra el texto exacto aprobado para pagos aplicados.
- No cambió la proyección remota: la hidratación admite REFUND con card_id crudo y omite comparar cardId para REFUND cuando la proyección canónica lo omite. Compras VOIDED aceptan asientos cuyo neto es cero y sus cuotas quedan VOIDED. Las propuestas usan pending.idempotencyKey y pending.payload.
- El selector de fecha del reembolso usa Lima y restringe el rango aprobado.

## Pruebas escritas

- pgTAP nuevo: anulación sin pagos, cuotas VOIDED, deuda cero, ledger compensado, evento en feed, bloqueo por asignación, bloqueo por reembolso activo, anulación del reembolso/restauración del saldo, rechazo del gasto sin tarjeta, fechas anterior y futura, reintento idempotente. Actualizado el test anterior de reembolsos para usar compras con tarjeta en sus caminos válidos.
- Room nuevo: anulación local, pagos bloqueados, reembolso activo bloqueado, borrar reembolso/restaurar cuota, pull de REFUND en origen y dispositivo nuevo con avance del checkpoint, pull de compra VOIDED, descarte y rehacer propuesta hidratada.
- Unit: elegibilidad de tarjeta/cuotas, rechazo de estándar, fechas y predicados/mensaje de UI. Fixtures existentes Room e instrumentados actualizados al camino de tarjeta.

## Verificación y límites

`./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL, última compilación 22 s. `git diff --check`: sin errores.

No se ejecutaron pruebas, ADB ni dispositivos; no se aplicaron migraciones ni hubo acceso a Supabase remoto. No commits ni push. La corrección de propuestas solo modifica su formato; la política de outbox, el encadenamiento de revisiones y el tratamiento de la versión remota continúan bajo L3/L4 y no se modificaron. El SQL y las pruebas requieren ejecución posterior autorizada; compilación no equivale a validación de comportamiento ni de despliegue.

## Archivos del lote

- `app/src/androidTest/java/com/kipu/app/feature/movements/MovementRefundBudgetIntegrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementRefundRoomTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/MovementRefundSyncTest.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt`
- `app/src/main/java/com/kipu/app/feature/movements/domain/RegisterRefundTransaction.kt`
- `app/src/main/java/com/kipu/app/feature/movements/domain/model/MovementModels.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementDetailSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryDelete.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementRefundSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/VoidMovementDialog.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/local/MovementRefundRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/domain/RefundTransactionTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/MovementHistoryDeleteTest.kt`
- `supabase/tests/database/movements_refund_test.sql`
- `app/src/test/java/com/kipu/app/feature/movements/data/local/CardPurchaseVoidAndRefundRoomTest.kt`
- `supabase/migrations/20261111130000_card_purchase_void_and_refund_rules.sql`
- `supabase/tests/database/card_purchase_void_and_refund_rules.test.sql`
- `docs/qa/audit-2026-11-11/L2-implementation-report.md`

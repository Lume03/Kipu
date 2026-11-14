# Entrega FIX-A16

Fecha: 2026-11-14. Rama: `Lume03/audit-fixes`; base: `7e0d1c64`.

Estado: APPROVED para las verificaciones locales solicitadas; pendiente la repetición de escenarios en dispositivo. Alcance exclusivamente local: sin remoto, ADB, instalación, commits, push ni Spec Kit.

## A16-05: causa aislada y corrección

Se reprodujo el rechazo `PURCHASE_NOT_ELIGIBLE` en Supabase local creando la compra mediante `public.register_transaction_v1`, con `operation_kind=CARD_PURCHASE`, `source_account_id=null` e installment_count=1. El RPC crea la transacción `CONFIRMED`, una cuota, el asiento de pasivo y **`transactions.account_id=null`**; la cuenta de pasivo está en `cards.account_id`. No crea asignaciones de pago ni debt_events. El RPC de reembolso buscaba la tarjeta con `c.account_id = v_purchase.account_id`: comparar contra NULL impide encontrarla y dispara el rechazo de elegibilidad.

La migración nueva resuelve la cuenta de pasivo desde la tarjeta. Comprueba propietario, tarjeta de crédito, moneda, cuenta CREDIT_LIABILITY propia y no eliminada; si una compra legacy contiene account_id, exige que coincida. Registra el reembolso y su asiento en esa cuenta. Conserva el bloqueo de compra, hash, idempotencia, remanente, fechas, cuotas, movimientos de deuda, asignaciones y las reglas L2. No modifica la compra original ni permite reembolsar gastos normales.

El pgTAP nuevo usa el RPC real para una compra S/4 y un reembolso S/1. Su payload tiene exactamente los campos de MovementOutboxPayloadFactory.buildRefund, con un hash literal calculado por el cliente y comprobado independientemente en RefundWireContractTest. Prueba reintento DUPLICATE, pasivo correcto, ausencia de pagos/deudas, ID incorrecto, hash incorrecto, exceso y fecha anterior. La reproducción previa a la migración falló como REJECTED; tras ella aplica como APPLIED. Los fixtures SQL anteriores insertaban una compra con account_id y por eso no detectaban esta diferencia.

MovementHistoryViewModel distingue cada código conocido de rechazo y conserva el código desconocido en el mensaje. Un test JVM comprueba que los mensajes son diferentes.

## Cambios por hallazgo

| Hallazgo | Corrección |
|---|---|
| A16-01 | Omite la exclusión global de FAILED solamente si el filtro avanzado incluye LEGACY_FAILED; aplica a páginas y conteos. |
| A16-02 | Mueve las acciones al footer real de KipuBottomSheet, fuera del cuerpo con scroll; el contenedor ya aplica imePadding y reserva el espacio del footer al medir el cuerpo. |
| A16-03 | Consulta en lote los IDs con outbox rechazado y los bloqueos por reembolso/pagos; elimina el patrón de consultas por fila al mapear la página. Conserva el mismo criterio financiero y divide los IN en lotes de 500. |
| A16-04 | Observa la apertura efectiva POSTED no revertida, con las correcciones anteriores. Usa ese importe al inicializar el campo, mostrar la apertura y calcular diferencia/vista previa. |
| CAT1-04 | Descarga reglas privadas de alias y preferencias en sync normal y en hidratación completa, incluyendo tombstones. Pagina, valida propietario y sesión del API, y aplica en transacción sin pisar cambios locales pendientes ni revisiones superiores. |
| GOA1-10 | La rama ya conservaba PENDING cuando había sucesores. Se añade defensa en GoalDao: una revisión remota inferior no cambia el status. El test Room reproduce dos comandos, respuestas sucesivas y acuse antiguo. |
| GOA1-11 | SQL3-02 de `20261112120000_money_and_date_rules.sql` ya devuelve REPLACEMENT_REQUIRED si la reasignación excede el cupo final. El cliente replica la regla antes de persistir dentro de atomicCommand y pasa el acceso Premium verificado desde el ViewModel. Si el destino se completa, se descuenta del cupo; Premium mantiene su comportamiento. No se inventa un contrato de reemplazo que el RPC de reasignación no admite. |

## Rendimiento A16-03

Prueba JVM Room/Robolectric con 10.000 movimientos, páginas de 50, 20 muestras y System.nanoTime (no el reloj Android simulado). Compara el enriquecimiento legado que ejecutaba consultas por fila con el repositorio completo que usa consultas en lote. También valida 1.000 IDs paginados sin duplicados y un máximo de 19 SELECT por página.

EXPLAIN QUERY PLAN: `SEARCH TABLE transactions USING INDEX index_transactions_user_id_occurred_at (user_id=?)`, con `USE TEMP B-TREE FOR RIGHT PART OF ORDER BY`. El índice existente ya acota por propietario y fecha; el coste evitable dominante estaba en las consultas por fila. Se mantuvo el esquema 41 y se redujo la página normal de al menos 154 SELECT a 7. No se rebajó el presupuesto instrumentado de 300 ms.

Medición final JVM (p95 por rango más cercano, muestra 19 de 20): legado **50 ms**, repositorio completo en lote **7 ms** (aprox. 86 % menos). Muestras del repositorio en ms: `[4,4,4,4,5,5,5,5,5,5,5,5,5,5,5,5,5,6,7,8]`. Esta medición no certifica p95 en el A16 ni equivale a una prueba release.

## Fase A: fixtures y hosts

Los tests con host ComponentActivity reciben explícitamente un SyncRecoveryViewModel mediante una CompositionLocal y reglas compartidas de Compose. La vinculación normal de producción sigue usando Hilt. Los fixtures usan flujos vacíos de avisos y no abren la base persistente del propietario.

Se corrige sqlite_master por pragma_index_list para comprobar unicidad. Se actualizan los fixtures de claim/lease de categorías, el bloqueo de sucesores de presupuestos, el checkpoint con snapshot de conflicto de deuda, el acuse predecesor de revisiones, la sesión tipada del mock de rendimiento, los selectores de campos Auth, el explainer visual de avisos de Kipu, los calendarios readOnly, la selección de categoría principal, el botón del vacío de presupuestos, los textos de deuda/crédito, la compra anulable según L2, los tres shimmers de precios, la identidad de OCR sin cuenta y el reintento de errores genéricos de perfil. Las pruebas de evidencia reservadas a com.kipu.app.lab conservan esa restricción como asunción de JUnit: quedan omitidas en el paquete normal, sin ejecutar su cuerpo.

Estos tests instrumentados solo se compilaron, conforme al despacho. No se afirma que hayan pasado en dispositivo. Las pruebas de parser de fechas no se sustituyen por intentar escribir en campos que ahora son readOnly.

## Migraciones

- SQL nueva: `supabase/migrations/20261114100000_refund_fixes.sql`.
- Room: ninguna; no hay cambios de entidades ni nuevos índices. La versión permanece en 41; no se requiere 41→42.
- No se modificaron migraciones históricas ni se desplegó SQL fuera del stack local.

## Verificación

- Docker disponible y `npx --no-install supabase start`: correctos.
- `npx --no-install supabase db reset`: PASS, incluyendo la migración nueva.
- `npx --no-install supabase test db`: PASS, 83 archivos y 1.615 aserciones.
- `./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest`: BUILD SUCCESSFUL; 1.754 tests JVM, cero fallos/errores y una omisión. Tras los últimos ajustes de fixtures de permisos y presupuestos, `:app:compileDebugAndroidTestKotlin` volvió a pasar; no cambió el código JVM validado.
- `git diff --check`: PASS.

## Casos para volver a probar en el teléfono

1. Compra de tarjeta S/4 recién sincronizada, reembolso S/1, reintento y anulación del reembolso; deuda 4→3→4, sin duplicados. Repetir con varias cuotas y con propagación a otro teléfono después de desplegar la migración en una entrega autorizada.
2. Apertura 100→200→300, con y sin movimientos posteriores; en la segunda corrección diferencia +100 y saldo resultante correcto.
3. Filtro Fallo histórico con filas FAILED y combinado con estados confirmados.
4. Filtros en viewport compacto oscuro, fontScale 1.6 y teclado abierto; Máximo, Limpiar y Aplicar accesibles, sin superposición.
5. Historial con 10.000 movimientos: medir p95 de páginas en el A16 y verificar paginación, acciones de compra con pagos y compra con reembolsos.
6. Crear/modificar/eliminar alias y preferencias de comercio en otro dispositivo y recibirlos en sync normal; conservar una edición local pendiente.
7. Meta completada con dos comandos offline: recibo antiguo no deshace COMPLETED ni marca SYNCED la edición pendiente.
8. Free con una meta completada y otras dos activas: reasignación que deja tres activas se rechaza sin efectos; destino que se completa queda permitido. Repetir con Premium.
9. Repetir las clases instrumentadas afectadas de Fase A en un entorno autorizado; los tests de evidencia requieren la variante lab.

## Archivos modificados

Rutas relativas al repositorio. Los cambios instrumentados se agrupan por fixture; los de producto y sus regresiones por hallazgo.

### A16-05

- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/RefundWireContractTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/RefundRejectionMessageTest.kt`
- `supabase/migrations/20261114100000_refund_fixes.sql`
- `supabase/tests/database/a16_refund_real_purchase.test.sql`

### A16-01

- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilder.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilderTest.kt`

### A16-02

- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt`

### A16-03

- `app/src/main/java/com/kipu/app/feature/accounts/data/local/FinancialMovementDao.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/MovementPresentationObservationTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/local/CardPurchaseVoidAndRefundRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/MovementHistoryQueryCostRoomTest.kt`

### A16-04

- `app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/domain/FinancialInstrumentsRepository.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailSections.kt`
- `app/src/test/java/com/kipu/app/feature/accounts/data/OpeningAdjustmentRetryRoomTest.kt`

### CAT1-04

- `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoriesApi.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoryDtos.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/CategoryCatalogHydrationTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/CategoryMerchantRulesHydrationTest.kt`
- `app/src/test/java/com/kipu/app/feature/portability/FullHydrationRoomTest.kt`

### GOA1-10 / GOA1-11

- `app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt`
- `app/src/main/java/com/kipu/app/feature/goals/data/local/GoalDao.kt`
- `app/src/main/java/com/kipu/app/feature/goals/domain/GoalRepository.kt`
- `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/goals/data/GoalReceiptOrderingRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepositoryTest.kt`

### Fase A: hosts y fixtures

- `app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/Issue48ProductionLabelsTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardEmptyStateTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardFinancialMessagingTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardRedesignStatesTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/detail/AccountPresetScreenshotTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/auth/presentation/AuthVisualFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/budgets/data/sync/BudgetTombstoneReplayIntegrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/budgets/presentation/BudgetScreensTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/CategoriesScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/PickerSettingsDeviceTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtChangeFeedTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtMovementDetailTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtPlanningTabTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtScheduleAndClosureFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtSettlementFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/PayableDebtFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalLifecycleFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalManagementFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementRevisionLocalTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/Issue48ProductionCreditLabelsTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementDetailCorrectionsTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryPerformanceTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementRefundScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementSwipeReceiptsTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementUxRefinementTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/receipts/data/ReceiptCandidateRoomTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/receipts/presentation/ReceiptEvidenceTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceLifecycleFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceRegistrationFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/settings/data/sync/SyncProfilePreferencesWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/settings/presentation/PermissionsScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/setup/SetupEvidenceTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/statements/presentation/StatementEvidenceTest.kt`
- `app/src/androidTest/java/com/kipu/app/navigation/CellularCorrectionsTest.kt`
- `app/src/androidTest/java/com/kipu/app/navigation/ObjectSourceNavigationTest.kt`
- `app/src/androidTest/java/com/kipu/app/testing/SyncNoticeComposeRule.kt`
- `app/src/androidTest/java/com/kipu/app/ui/component/OptionalDateFormsTest.kt`
- `app/src/androidTest/java/com/kipu/app/ui/theme/PlanningVisualEvidenceTest.kt`
- `app/src/androidTest/java/com/kipu/app/ui/theme/ThemeScreenEvidenceTest.kt`
- `app/src/main/java/com/kipu/app/ui/component/UnsyncedNotice.kt`

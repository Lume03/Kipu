# Pruebas de auditoría en Samsung Galaxy A16

**Informe cerrado con fallos y limitaciones explícitas. No constituye aprobación de la rama.**

Fecha: 2026-11-14. Rama `Lume03/audit-fixes`. Dispositivo `SM-A165M`, Android 16, serial `RFGL70J9E2T`.

## Preservación del teléfono y límites de evidencia

- Se usó exclusivamente el adb del SDK y el serial indicado. No hubo `connectedAndroidTest`, `connectedDebugAndroidTest`, desinstalaciones, `pm clear`, SQL manual, Supabase, commits ni push. No se cambió código de producción.
- Instalación inicial: `gradlew.bat :app:installDebug :app:installDebugAndroidTest`, BUILD SUCCESSFUL. Las repeticiones instalaron solo el APK de tests con `:app:installDebugAndroidTest`.
- Paquete instalado antes: versión `0.1.0`, sin flag DEBUGGABLE; después: `0.1.0`, DEBUGGABLE.
- Copia anterior a la primera apertura explícita: `kipu-backup-before.tar`, **8.441.344 bytes, 33 entradas, TAR legible**. El encabezado de `databases/kipu.db` **ya indicaba 41**. No se puede certificar copia de versión 40 ni fijar cuándo migró. No hubo downgrade ni restauración.
- La primera apertura mostró dashboard y sesión iniciada, sin AndroidRuntime fatal ni excepción de migración. El dashboard posterior a los tests conserva los saldos observados en esa primera apertura. No hay dashboard previo a instalar: no se certifica igualdad pre/post migración.
- Scratch privado fuera del repo: `C:\Users\Alume\AppData\Local\Temp\kipu-a16-20261114`. Contiene capturas, XML, respaldo y logs con datos reales; no se agregaron al repositorio. Cada nombre de evidencia del informe se resuelve dentro de esa carpeta.

## Fase A: inventario, seguridad y resultado

Se inventariaron **183 clases** de `androidTest` y `androidTestDebug`. Búsqueda previa: `clearAllTables`, `deleteDatabase`, `signOut`, `deleteFile`, archivos de sesión, Room persistente, preferencias y sustitución de WorkManager. Las migraciones permitidas usan MigrationTestHelper con nombres de fixture separados de kipu.db; las escrituras de los DAO permitidos son en memoria. Los callbacks de cierre de sesión de UI son mocks.

Primera pasada: **164 clases: 107 PASS, 53 FAIL, 4 TIMEOUT**; 19 omitidas con motivo. Tras repeticiones focalizadas: **119 PASS, 41 FAIL, 4 TIMEOUT**. TIMEOUT significa clase parcial a los 210 s; no se certifican los métodos restantes. ADB puede devolver código de proceso 0 incluso con tests fallidos: el dictamen usa la salida de instrumentación, no solo el exit code.

Comando de ejecución: `adb -s RFGL70J9E2T shell am instrument -w -e class <FQCN> com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`. No se repitieron clases verdes sin cambios. En la continuación se cotejaron `inventory.json`, `results.json`, `reruns.json` y `final-reruns.json`: no quedan clases sin resultado y sin motivo de omisión. Los 15 archivos modificados tienen evidencia posterior a su última edición; se conservaron sus cambios y resultados, sin repetirlos. Las seis clases pendientes mencionadas en el despacho ya estaban resueltas en estos archivos. Las cuatro clases TIMEOUT siguen siendo parciales, con sus fallos Hilt documentados; no se certifica el resto de sus métodos.

| Clase | Resultado final | Evidencia / motivo |
|---|---|---|
| `com.kipu.app.core.database.BudgetRoomMigrationTest` | PASS | com.kipu.app.core.database.BudgetRoomMigrationTest.txt |
| `com.kipu.app.core.database.CaptureAtomicResolutionTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.core.database.CaptureRoomMigrationTest` | PASS | com.kipu.app.core.database.CaptureRoomMigrationTest.txt |
| `com.kipu.app.core.database.CardCycleTermsMigrationTest` | PASS | com.kipu.app.core.database.CardCycleTermsMigrationTest.txt |
| `com.kipu.app.core.database.CardInterestMigrationTest` | PASS | com.kipu.app.core.database.CardInterestMigrationTest.txt |
| `com.kipu.app.core.database.CreditLiabilityMigrationTest` | PASS | com.kipu.app.core.database.CreditLiabilityMigrationTest.txt |
| `com.kipu.app.core.database.CreditRoomMigrationTest` | PASS | com.kipu.app.core.database.CreditRoomMigrationTest.txt |
| `com.kipu.app.core.database.DebtRoomMigrationTest` | PASS | com.kipu.app.core.database.DebtRoomMigrationTest.txt |
| `com.kipu.app.core.database.ExpectedPaymentAssociationTest` | PASS | com.kipu.app.core.database.ExpectedPaymentAssociationTest.txt |
| `com.kipu.app.core.database.GoalRoomMigrationTest` | PASS | com.kipu.app.core.database.GoalRoomMigrationTest.txt |
| `com.kipu.app.core.database.HybridYapeMigrationTest` | PASS | com.kipu.app.core.database.HybridYapeMigrationTest.txt |
| `com.kipu.app.core.database.MerchantCatalogRoomMigrationTest` | PASS | com.kipu.app.core.database.MerchantCatalogRoomMigrationTest.txt |
| `com.kipu.app.core.database.MerchantRuleDatabaseTest` | PASS | com.kipu.app.core.database.MerchantRuleDatabaseTest.txt |
| `com.kipu.app.core.database.MovementRoomMigrationTest` | FAIL | com.kipu.app.core.database.MovementRoomMigrationTest.txt |
| `com.kipu.app.core.database.MovementSourceMigrationTest` | PASS | com.kipu.app.core.database.MovementSourceMigrationTest.txt |
| `com.kipu.app.core.database.ReceiptRoomMigrationTest` | PASS | com.kipu.app.core.database.ReceiptRoomMigrationTest.txt |
| `com.kipu.app.core.database.RecurrenceRoomMigrationTest` | PASS | com.kipu.app.core.database.RecurrenceRoomMigrationTest.txt |
| `com.kipu.app.core.database.StatementRoomMigrationTest` | PASS | com.kipu.app.core.database.StatementRoomMigrationTest.txt |
| `com.kipu.app.core.security.KeystoreEncryptedSessionStorageTest` | omitido | DESTRUCTIVO: borra y reemplaza files/kipu_session.enc real |
| `com.kipu.app.core.security.LocalLockScreenTest` | PASS tras fixture/texto | com.kipu.app.core.security.LocalLockScreenTest.txt; com.kipu.app.core.security.LocalLockScreenTest.rerun.txt |
| `com.kipu.app.feature.accounts.AccountDaoTest` | PASS | com.kipu.app.feature.accounts.AccountDaoTest.txt |
| `com.kipu.app.feature.accounts.CardDaoTest` | PASS | com.kipu.app.feature.accounts.CardDaoTest.txt |
| `com.kipu.app.feature.accounts.data.local.CreditCardLedgerQueriesTest` | PASS | com.kipu.app.feature.accounts.data.local.CreditCardLedgerQueriesTest.txt |
| `com.kipu.app.feature.accounts.data.local.InstrumentSyncDaoTest` | PASS | com.kipu.app.feature.accounts.data.local.InstrumentSyncDaoTest.txt |
| `com.kipu.app.feature.accounts.data.sync.SyncInstrumentCommandsWorkerTest` | PASS tras fixture/texto | com.kipu.app.feature.accounts.data.sync.SyncInstrumentCommandsWorkerTest.txt; com.kipu.app.feature.accounts.data.sync.SyncInstrumentCommandsWorkerTest.rerun.txt |
| `com.kipu.app.feature.accounts.FinancialRejectionCompensationTest` | PASS tras fixture/texto | com.kipu.app.feature.accounts.FinancialRejectionCompensationTest.txt; com.kipu.app.feature.accounts.FinancialRejectionCompensationTest.rerun.txt |
| `com.kipu.app.feature.accounts.OpeningAdjustmentRepositoryTest` | PASS | com.kipu.app.feature.accounts.OpeningAdjustmentRepositoryTest.txt |
| `com.kipu.app.feature.accounts.presentation.components.CardArtFaceTest` | PASS | com.kipu.app.feature.accounts.presentation.components.CardArtFaceTest.txt |
| `com.kipu.app.feature.accounts.presentation.CreditAccessibilitySemanticsTest` | PASS | com.kipu.app.feature.accounts.presentation.CreditAccessibilitySemanticsTest.txt |
| `com.kipu.app.feature.accounts.presentation.CreditPurchasePreviewTest` | PASS | com.kipu.app.feature.accounts.presentation.CreditPurchasePreviewTest.txt |
| `com.kipu.app.feature.accounts.presentation.CreditPurchaseTermsSectionTest` | PASS | com.kipu.app.feature.accounts.presentation.CreditPurchaseTermsSectionTest.txt |
| `com.kipu.app.feature.accounts.presentation.dashboard.DashboardEmptyStateTest` | FAIL | com.kipu.app.feature.accounts.presentation.dashboard.DashboardEmptyStateTest.txt |
| `com.kipu.app.feature.accounts.presentation.dashboard.DashboardFinancialMessagingTest` | FAIL | com.kipu.app.feature.accounts.presentation.dashboard.DashboardFinancialMessagingTest.txt |
| `com.kipu.app.feature.accounts.presentation.dashboard.DashboardRedesignStatesTest` | FAIL | com.kipu.app.feature.accounts.presentation.dashboard.DashboardRedesignStatesTest.txt |
| `com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest` | TIMEOUT | com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest.txt |
| `com.kipu.app.feature.accounts.presentation.detail.AccountPresetScreenshotTest` | TIMEOUT | com.kipu.app.feature.accounts.presentation.detail.AccountPresetScreenshotTest.txt |
| `com.kipu.app.feature.accounts.presentation.detail.BankCardFlipTest` | PASS | com.kipu.app.feature.accounts.presentation.detail.BankCardFlipTest.txt |
| `com.kipu.app.feature.accounts.presentation.detail.PayCardDialogTest` | PASS | com.kipu.app.feature.accounts.presentation.detail.PayCardDialogTest.txt |
| `com.kipu.app.feature.accounts.presentation.detail.QoreCardFlipTest` | PASS | com.kipu.app.feature.accounts.presentation.detail.QoreCardFlipTest.txt |
| `com.kipu.app.feature.accounts.presentation.InstallmentSimulatorSelectorsTest` | PASS | com.kipu.app.feature.accounts.presentation.InstallmentSimulatorSelectorsTest.txt |
| `com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentFormScreenTest` | PASS | com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentFormScreenTest.txt |
| `com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentWalletAffiliationTest` | PASS | com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentWalletAffiliationTest.txt |
| `com.kipu.app.feature.accounts.presentation.Issue48ProductionLabelsTest` | FAIL | com.kipu.app.feature.accounts.presentation.Issue48ProductionLabelsTest.txt |
| `com.kipu.app.feature.accounts.RateCatalogPersistenceTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.accounts.WalletChannelUniquenessRepositoryTest` | PASS | com.kipu.app.feature.accounts.WalletChannelUniquenessRepositoryTest.txt |
| `com.kipu.app.feature.analytics.AnalyticsDaoTest` | PASS | com.kipu.app.feature.analytics.AnalyticsDaoTest.txt |
| `com.kipu.app.feature.analytics.presentation.AnalyticsAccessibilityTest` | PASS | com.kipu.app.feature.analytics.presentation.AnalyticsAccessibilityTest.txt |
| `com.kipu.app.feature.analytics.presentation.AnalyticsScreenTest` | PASS | com.kipu.app.feature.analytics.presentation.AnalyticsScreenTest.txt |
| `com.kipu.app.feature.auth.data.OnboardingPreferencesTest` | PASS | com.kipu.app.feature.auth.data.OnboardingPreferencesTest.txt |
| `com.kipu.app.feature.auth.presentation.AuthCelebrationScreensTest` | PASS | com.kipu.app.feature.auth.presentation.AuthCelebrationScreensTest.txt |
| `com.kipu.app.feature.auth.presentation.AuthNavigationTest` | PASS | com.kipu.app.feature.auth.presentation.AuthNavigationTest.txt |
| `com.kipu.app.feature.auth.presentation.AuthScreensTest` | PASS | com.kipu.app.feature.auth.presentation.AuthScreensTest.txt |
| `com.kipu.app.feature.auth.presentation.AuthVisualFlowTest` | FAIL | com.kipu.app.feature.auth.presentation.AuthVisualFlowTest.txt |
| `com.kipu.app.feature.auth.presentation.SignOutFlowTest` | PASS | com.kipu.app.feature.auth.presentation.SignOutFlowTest.txt |
| `com.kipu.app.feature.budgets.BudgetCrudFinancialIsolationIntegrationTest` | PASS tras fixture/texto | com.kipu.app.feature.budgets.BudgetCrudFinancialIsolationIntegrationTest.txt; com.kipu.app.feature.budgets.BudgetCrudFinancialIsolationIntegrationTest.rerun.txt |
| `com.kipu.app.feature.budgets.BudgetHierarchyAlertsIntegrationTest` | PASS tras fixture/texto | com.kipu.app.feature.budgets.BudgetHierarchyAlertsIntegrationTest.txt; com.kipu.app.feature.budgets.BudgetHierarchyAlertsIntegrationTest.rerun.txt |
| `com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepositoryRoomTest` | PASS tras fixture/texto | com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepositoryRoomTest.txt; com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepositoryRoomTest.rerun.txt |
| `com.kipu.app.feature.budgets.data.sync.BudgetOfflineRestartIntegrationTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.budgets.data.sync.BudgetTombstoneReplayIntegrationTest` | FAIL | com.kipu.app.feature.budgets.data.sync.BudgetTombstoneReplayIntegrationTest.txt; com.kipu.app.feature.budgets.data.sync.BudgetTombstoneReplayIntegrationTest.final.txt |
| `com.kipu.app.feature.budgets.presentation.BudgetAppearanceStoreTest` | omitido | escribe preferencias persistentes: conservador, omitido |
| `com.kipu.app.feature.budgets.presentation.BudgetScreensTest` | TIMEOUT | com.kipu.app.feature.budgets.presentation.BudgetScreensTest.txt |
| `com.kipu.app.feature.calendar.presentation.CalendarAndHomeAccessibilityTest` | PASS | com.kipu.app.feature.calendar.presentation.CalendarAndHomeAccessibilityTest.txt |
| `com.kipu.app.feature.capture.presentation.CaptureConsentSheetTest` | PASS | com.kipu.app.feature.capture.presentation.CaptureConsentSheetTest.txt |
| `com.kipu.app.feature.capture.presentation.CaptureFlowScreenshotTest` | PASS | com.kipu.app.feature.capture.presentation.CaptureFlowScreenshotTest.txt |
| `com.kipu.app.feature.categories.data.local.CategoryDatabaseTest` | PASS | com.kipu.app.feature.categories.data.local.CategoryDatabaseTest.txt |
| `com.kipu.app.feature.categories.data.local.CategoryTypeMigrationTest` | PASS | com.kipu.app.feature.categories.data.local.CategoryTypeMigrationTest.txt |
| `com.kipu.app.feature.categories.data.sync.SyncCategoryCommandsWorkerTest` | FAIL | com.kipu.app.feature.categories.data.sync.SyncCategoryCommandsWorkerTest.txt; com.kipu.app.feature.categories.data.sync.SyncCategoryCommandsWorkerTest.final.txt |
| `com.kipu.app.feature.categories.EpCcoEndToEndTest` | PASS | com.kipu.app.feature.categories.EpCcoEndToEndTest.txt |
| `com.kipu.app.feature.categories.presentation.CategoriesScreenTest` | FAIL | com.kipu.app.feature.categories.presentation.CategoriesScreenTest.txt; método de icono personalizado PASS en .rerun.txt; resto conserva fallo Hilt |
| `com.kipu.app.feature.categories.presentation.MerchantAliasRulesScreenTest` | PASS | com.kipu.app.feature.categories.presentation.MerchantAliasRulesScreenTest.txt |
| `com.kipu.app.feature.categories.presentation.MerchantCategoryPreferenceScreenTest` | PASS | com.kipu.app.feature.categories.presentation.MerchantCategoryPreferenceScreenTest.txt |
| `com.kipu.app.feature.categories.presentation.MerchantPickerTest` | PASS | com.kipu.app.feature.categories.presentation.MerchantPickerTest.txt |
| `com.kipu.app.feature.categories.presentation.PickerSettingsDeviceTest` | FAIL | com.kipu.app.feature.categories.presentation.PickerSettingsDeviceTest.txt |
| `com.kipu.app.feature.debts.data.DebtChangeFeedTest` | FAIL | com.kipu.app.feature.debts.data.DebtChangeFeedTest.txt |
| `com.kipu.app.feature.debts.data.DebtLocalDataSourceTest` | PASS | com.kipu.app.feature.debts.data.DebtLocalDataSourceTest.txt |
| `com.kipu.app.feature.debts.data.DebtOutboxRecoveryTest` | PASS | com.kipu.app.feature.debts.data.DebtOutboxRecoveryTest.txt |
| `com.kipu.app.feature.debts.data.DebtReminderReconciliationTest` | PASS | com.kipu.app.feature.debts.data.DebtReminderReconciliationTest.txt |
| `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest` | omitido | sustituye WorkManager global del proceso real |
| `com.kipu.app.feature.debts.data.DebtReminderWorkerTest` | PASS | com.kipu.app.feature.debts.data.DebtReminderWorkerTest.txt |
| `com.kipu.app.feature.debts.data.DebtSettlementSyncTest` | PASS | com.kipu.app.feature.debts.data.DebtSettlementSyncTest.txt |
| `com.kipu.app.feature.debts.data.PayableDebtRepositoryTest` | PASS | com.kipu.app.feature.debts.data.PayableDebtRepositoryTest.txt |
| `com.kipu.app.feature.debts.data.ReceivableDebtRepositoryTest` | PASS | com.kipu.app.feature.debts.data.ReceivableDebtRepositoryTest.txt |
| `com.kipu.app.feature.debts.presentation.DebtEditDetailsFlowTest` | PASS | com.kipu.app.feature.debts.presentation.DebtEditDetailsFlowTest.txt |
| `com.kipu.app.feature.debts.presentation.DebtMovementDetailTest` | FAIL | com.kipu.app.feature.debts.presentation.DebtMovementDetailTest.txt |
| `com.kipu.app.feature.debts.presentation.DebtPlanningTabTest` | FAIL | com.kipu.app.feature.debts.presentation.DebtPlanningTabTest.txt |
| `com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest` | FAIL | com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest.txt |
| `com.kipu.app.feature.debts.presentation.DebtSettlementFlowTest` | FAIL | com.kipu.app.feature.debts.presentation.DebtSettlementFlowTest.txt |
| `com.kipu.app.feature.debts.presentation.PayableDebtFlowTest` | FAIL | com.kipu.app.feature.debts.presentation.PayableDebtFlowTest.txt |
| `com.kipu.app.feature.debts.presentation.ReceivableDebtFlowTest` | PASS | com.kipu.app.feature.debts.presentation.ReceivableDebtFlowTest.txt |
| `com.kipu.app.feature.debts.presentation.UpcomingDuesScreenTest` | PASS | com.kipu.app.feature.debts.presentation.UpcomingDuesScreenTest.txt |
| `com.kipu.app.feature.goals.data.GoalReplacementRoomIntegrationTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.goals.presentation.GoalContributionFlowTest` | PASS tras fixture/texto | com.kipu.app.feature.goals.presentation.GoalContributionFlowTest.txt; com.kipu.app.feature.goals.presentation.GoalContributionFlowTest.rerun.txt |
| `com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest` | TIMEOUT | com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest.txt |
| `com.kipu.app.feature.goals.presentation.GoalManagementFlowTest` | FAIL | com.kipu.app.feature.goals.presentation.GoalManagementFlowTest.txt |
| `com.kipu.app.feature.goals.presentation.GoalReleaseThenTransferFlowTest` | PASS | com.kipu.app.feature.goals.presentation.GoalReleaseThenTransferFlowTest.txt |
| `com.kipu.app.feature.goals.presentation.GoalVisualRefinementTest` | PASS | com.kipu.app.feature.goals.presentation.GoalVisualRefinementTest.txt |
| `com.kipu.app.feature.goals.presentation.PlanningCorrectionsTest` | omitido | escribe preferencias persistentes: conservador, omitido |
| `com.kipu.app.feature.movements.data.local.MovementHistoryQueryTest` | FAIL | com.kipu.app.feature.movements.data.local.MovementHistoryQueryTest.txt |
| `com.kipu.app.feature.movements.data.local.MovementLocalDataSourceTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.movements.data.local.MovementOutboxLeaseTest` | PASS | com.kipu.app.feature.movements.data.local.MovementOutboxLeaseTest.txt |
| `com.kipu.app.feature.movements.data.local.MovementRefundRoomTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.movements.data.local.MovementRevisionLocalTest` | FAIL | com.kipu.app.feature.movements.data.local.MovementRevisionLocalTest.txt |
| `com.kipu.app.feature.movements.data.local.YapeReceiptLedgerAcceptanceTest` | PASS | com.kipu.app.feature.movements.data.local.YapeReceiptLedgerAcceptanceTest.txt |
| `com.kipu.app.feature.movements.data.sync.MovementRefundSyncTest` | PASS | com.kipu.app.feature.movements.data.sync.MovementRefundSyncTest.txt |
| `com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest` | PASS tras fixture/texto | com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest.txt; com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest.rerun.txt |
| `com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest` | PASS tras fixture/texto | com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest.txt; com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest.rerun.txt |
| `com.kipu.app.feature.movements.presentation.AccountSelectorBottomSheetTest` | PASS | com.kipu.app.feature.movements.presentation.AccountSelectorBottomSheetTest.txt |
| `com.kipu.app.feature.movements.presentation.DuplicateWarningDialogTest` | PASS | com.kipu.app.feature.movements.presentation.DuplicateWarningDialogTest.txt |
| `com.kipu.app.feature.movements.presentation.Issue48ProductionCreditLabelsTest` | FAIL | com.kipu.app.feature.movements.presentation.Issue48ProductionCreditLabelsTest.txt |
| `com.kipu.app.feature.movements.presentation.ManualEntryAcceptanceTest` | PASS | com.kipu.app.feature.movements.presentation.ManualEntryAcceptanceTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementDetailCorrectionsTest` | FAIL | com.kipu.app.feature.movements.presentation.MovementDetailCorrectionsTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementEditorScreenTest` | PASS | com.kipu.app.feature.movements.presentation.MovementEditorScreenTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementFiltersCatalogTest` | PASS | com.kipu.app.feature.movements.presentation.MovementFiltersCatalogTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementFiltersIssue19Test` | FAIL | com.kipu.app.feature.movements.presentation.MovementFiltersIssue19Test.txt |
| `com.kipu.app.feature.movements.presentation.MovementFilterVisualEvidenceTest` | PASS | com.kipu.app.feature.movements.presentation.MovementFilterVisualEvidenceTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementHistoryAccessIntegrationTest` | PASS | com.kipu.app.feature.movements.presentation.MovementHistoryAccessIntegrationTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementHistoryPerformanceTest` | FAIL | com.kipu.app.feature.movements.presentation.MovementHistoryPerformanceTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementHistoryScreenTest` | FAIL | com.kipu.app.feature.movements.presentation.MovementHistoryScreenTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementObligationEntryPointTest` | PASS | com.kipu.app.feature.movements.presentation.MovementObligationEntryPointTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementRefundScreenTest` | FAIL | com.kipu.app.feature.movements.presentation.MovementRefundScreenTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementSourceFiltersTest` | PASS | com.kipu.app.feature.movements.presentation.MovementSourceFiltersTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementSwipeReceiptsTest` | FAIL | com.kipu.app.feature.movements.presentation.MovementSwipeReceiptsTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementSyncReauthenticationTest` | PASS | com.kipu.app.feature.movements.presentation.MovementSyncReauthenticationTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementThemeRegressionTest` | PASS | com.kipu.app.feature.movements.presentation.MovementThemeRegressionTest.txt |
| `com.kipu.app.feature.movements.presentation.MovementUxRefinementTest` | FAIL | com.kipu.app.feature.movements.presentation.MovementUxRefinementTest.txt |
| `com.kipu.app.feature.movements.presentation.PastelCategoryPickerSelectorTest` | PASS | com.kipu.app.feature.movements.presentation.PastelCategoryPickerSelectorTest.txt |
| `com.kipu.app.feature.movements.presentation.QuickMovementRecurrenceOptionTest` | PASS | com.kipu.app.feature.movements.presentation.QuickMovementRecurrenceOptionTest.txt |
| `com.kipu.app.feature.movements.presentation.QuickMovementScreenTest` | PASS tras fixture/texto | com.kipu.app.feature.movements.presentation.QuickMovementScreenTest.txt; com.kipu.app.feature.movements.presentation.QuickMovementScreenTest.final.txt |
| `com.kipu.app.feature.movements.presentation.ReceiptMovementEditorAcceptanceTest` | PASS | com.kipu.app.feature.movements.presentation.ReceiptMovementEditorAcceptanceTest.txt |
| `com.kipu.app.feature.movements.presentation.SavingMovementDialogTest` | PASS | com.kipu.app.feature.movements.presentation.SavingMovementDialogTest.txt |
| `com.kipu.app.feature.notifications.data.local.NotificationsDatabaseTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.notifications.data.local.ReminderFinancialIsolationTest` | PASS | com.kipu.app.feature.notifications.data.local.ReminderFinancialIsolationTest.txt |
| `com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinatorTest` | PASS | com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinatorTest.txt |
| `com.kipu.app.feature.notifications.data.local.ReminderReconciliationTest` | PASS | com.kipu.app.feature.notifications.data.local.ReminderReconciliationTest.txt |
| `com.kipu.app.feature.notifications.data.local.ReminderStorageTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.notifications.presentation.NotificationActionsTest` | PASS tras fixture/texto | com.kipu.app.feature.notifications.presentation.NotificationActionsTest.txt; com.kipu.app.feature.notifications.presentation.NotificationActionsTest.rerun.txt |
| `com.kipu.app.feature.notifications.presentation.NotificationCenterScreenTest` | PASS tras fixture/texto | com.kipu.app.feature.notifications.presentation.NotificationCenterScreenTest.txt; com.kipu.app.feature.notifications.presentation.NotificationCenterScreenTest.rerun.txt |
| `com.kipu.app.feature.notifications.presentation.NotificationDestinationTest` | PASS | com.kipu.app.feature.notifications.presentation.NotificationDestinationTest.txt |
| `com.kipu.app.feature.notifications.presentation.NotificationNavigationTest` | PASS | com.kipu.app.feature.notifications.presentation.NotificationNavigationTest.txt |
| `com.kipu.app.feature.plans.data.local.FeatureAccessCacheDaoTest` | PASS | com.kipu.app.feature.plans.data.local.FeatureAccessCacheDaoTest.txt |
| `com.kipu.app.feature.plans.data.local.KipuDatabaseSchemaTest` | omitido | base persistente / deleteDatabase: omitido por restricción del teléfono |
| `com.kipu.app.feature.plans.data.local.PlanPreferencesDaoTest` | PASS | com.kipu.app.feature.plans.data.local.PlanPreferencesDaoTest.txt |
| `com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDaoTest` | PASS | com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDaoTest.txt |
| `com.kipu.app.feature.plans.data.sync.SyncPlanQuotaWorkerTest` | PASS | com.kipu.app.feature.plans.data.sync.SyncPlanQuotaWorkerTest.txt |
| `com.kipu.app.feature.plans.data.sync.SyncPlanSelectionWorkerTest` | omitido | sustituye WorkManager global del proceso real |
| `com.kipu.app.feature.plans.presentation.PlanPurchaseScreenTest` | FAIL | com.kipu.app.feature.plans.presentation.PlanPurchaseScreenTest.txt |
| `com.kipu.app.feature.plans.presentation.PlanSelectionRouteTest` | PASS | com.kipu.app.feature.plans.presentation.PlanSelectionRouteTest.txt |
| `com.kipu.app.feature.plans.presentation.PlanSelectionScreenshotTest` | PASS | com.kipu.app.feature.plans.presentation.PlanSelectionScreenshotTest.txt |
| `com.kipu.app.feature.plans.presentation.PlanSelectionScreenTest` | PASS | com.kipu.app.feature.plans.presentation.PlanSelectionScreenTest.txt |
| `com.kipu.app.feature.receipts.data.ReceiptCandidateRoomTest` | FAIL | com.kipu.app.feature.receipts.data.ReceiptCandidateRoomTest.txt |
| `com.kipu.app.feature.receipts.data.YapeReceiptOcrInstrumentedTest` | omitido | muestras privadas / PDF / grants externos |
| `com.kipu.app.feature.receipts.presentation.ReceiptEvidenceTest` | FAIL | com.kipu.app.feature.receipts.presentation.ReceiptEvidenceTest.txt |
| `com.kipu.app.feature.receipts.presentation.ReceiptExperienceTest` | PASS | com.kipu.app.feature.receipts.presentation.ReceiptExperienceTest.txt |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` | omitido | sustituye WorkManager global del proceso real |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` | PASS | com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest.txt |
| `com.kipu.app.feature.recurrences.RecurrenceLifecycleFlowTest` | FAIL | com.kipu.app.feature.recurrences.RecurrenceLifecycleFlowTest.txt |
| `com.kipu.app.feature.recurrences.RecurrenceRegistrationFlowTest` | FAIL | com.kipu.app.feature.recurrences.RecurrenceRegistrationFlowTest.txt |
| `com.kipu.app.feature.settings.data.AndroidPermissionSettingsTest` | PASS | com.kipu.app.feature.settings.data.AndroidPermissionSettingsTest.txt |
| `com.kipu.app.feature.settings.data.local.DeviceAccountSettingsTest` | PASS | com.kipu.app.feature.settings.data.local.DeviceAccountSettingsTest.txt |
| `com.kipu.app.feature.settings.data.local.PermissionDataTest` | PASS | com.kipu.app.feature.settings.data.local.PermissionDataTest.txt |
| `com.kipu.app.feature.settings.data.local.ProfileLocalDataTest` | PASS | com.kipu.app.feature.settings.data.local.ProfileLocalDataTest.txt |
| `com.kipu.app.feature.settings.data.sync.SyncProfilePreferencesWorkerTest` | FAIL | com.kipu.app.feature.settings.data.sync.SyncProfilePreferencesWorkerTest.txt |
| `com.kipu.app.feature.settings.presentation.PermissionsScreenTest` | FAIL | com.kipu.app.feature.settings.presentation.PermissionsScreenTest.txt |
| `com.kipu.app.feature.settings.presentation.SettingsFinancialIntegrityTest` | PASS | com.kipu.app.feature.settings.presentation.SettingsFinancialIntegrityTest.txt |
| `com.kipu.app.feature.setup.SetupEvidenceTest` | FAIL | com.kipu.app.feature.setup.SetupEvidenceTest.txt |
| `com.kipu.app.feature.statements.presentation.StatementEvidenceTest` | FAIL | com.kipu.app.feature.statements.presentation.StatementEvidenceTest.txt |
| `com.kipu.app.navigation.AnalyticsNavigationTest` | PASS | com.kipu.app.navigation.AnalyticsNavigationTest.txt |
| `com.kipu.app.navigation.CalendarNavigationTest` | PASS | com.kipu.app.navigation.CalendarNavigationTest.txt |
| `com.kipu.app.navigation.CellularCorrectionsTest` | FAIL | com.kipu.app.navigation.CellularCorrectionsTest.txt |
| `com.kipu.app.navigation.GoalNavigationTest` | PASS | com.kipu.app.navigation.GoalNavigationTest.txt |
| `com.kipu.app.navigation.MovementHistoryNavigationTest` | PASS | com.kipu.app.navigation.MovementHistoryNavigationTest.txt |
| `com.kipu.app.navigation.ObjectSourceNavigationTest` | FAIL | com.kipu.app.navigation.ObjectSourceNavigationTest.txt |
| `com.kipu.app.navigation.PlanningTabsTest` | PASS | com.kipu.app.navigation.PlanningTabsTest.txt |
| `com.kipu.app.navigation.ReceiptShareUriGrantTest` | omitido | muestras privadas / PDF / grants externos |
| `com.kipu.app.navigation.RetainedRootTabsTest` | PASS | com.kipu.app.navigation.RetainedRootTabsTest.txt |
| `com.kipu.app.navigation.YapePrivateSamplesAcceptanceTest` | omitido | muestras privadas / PDF / grants externos |
| `com.kipu.app.ui.component.KipuOptionalDateFieldTest` | PASS | com.kipu.app.ui.component.KipuOptionalDateFieldTest.txt |
| `com.kipu.app.ui.component.OptionalDateFormsTest` | FAIL | com.kipu.app.ui.component.OptionalDateFormsTest.txt |
| `com.kipu.app.ui.component.OptionalDatePersistenceTest` | PASS | com.kipu.app.ui.component.OptionalDatePersistenceTest.txt |
| `com.kipu.app.ui.theme.KipuThemeContrastTest` | PASS | com.kipu.app.ui.theme.KipuThemeContrastTest.txt |
| `com.kipu.app.ui.theme.PlanningVisualEvidenceTest` | FAIL | com.kipu.app.ui.theme.PlanningVisualEvidenceTest.txt |
| `com.kipu.app.ui.theme.ThemeScreenEvidenceTest` | FAIL | com.kipu.app.ui.theme.ThemeScreenEvidenceTest.txt |
| `com.kipu.app.navigation.PlanningRemoteAcceptanceTest` | omitido | aceptación remota requiere argumentos; inicia MainActivity real: revisar antes de ejecutar |

### Clasificación de todos los fallos de la primera pasada

Las trazas completas están en los archivos por FQCN. Los fallos de fixtures no prueban un bug funcional en la actividad real. Las causas adicionales de clases mixtas se consignan por separado.

| Clase | Clasificación | Diagnóstico |
|---|---|---|
| `MovementRoomMigrationTest` | TEST ROTO | Consulta de metadatos del test inválida: sqlite_master no tiene columna unique (línea 35). No se modificó por no ser texto ni fixture. |
| `LocalLockScreenTest` | TEST ROTO · corregido y PASS | Fixture: faltaba currentOwner verificado; se añadió solo al mock. |
| `SyncInstrumentCommandsWorkerTest` | TEST ROTO · corregido y PASS | Fixture: faltaba lease IN_FLIGHT / attemptCount y se usaba MovementDao relajado en el caso con outbox real; corregido. |
| `FinancialRejectionCompensationTest` | TEST ROTO · corregido y PASS | Fixture: dueño null, remoteSession y expectedOwner sin preparar; corregido sin relajar aserciones financieras. |
| `DashboardEmptyStateTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `DashboardFinancialMessagingTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `DashboardRedesignStatesTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `AccountDetailScreenTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `AccountPresetScreenshotTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `Issue48ProductionLabelsTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `AuthVisualFlowTest` | TEST ROTO | Test roto: selectores ContentDescription antiguos no localizan campos; no se invocó Auth remoto. |
| `BudgetCrudFinancialIsolationIntegrationTest` | TEST ROTO · corregido y PASS | Fixture: setActiveOwner deja acceso Protected; se añadió resolveLocalProtection(ownerId, false). |
| `BudgetHierarchyAlertsIntegrationTest` | TEST ROTO · corregido y PASS | Fixture: setActiveOwner deja acceso Protected; se añadió resolveLocalProtection(ownerId, false). |
| `OfflineFirstBudgetRepositoryRoomTest` | TEST ROTO · corregido y PASS | Fixture: setActiveOwner deja acceso Protected; se añadió resolveLocalProtection(ownerId, false). |
| `BudgetTombstoneReplayIntegrationTest` | TEST ROTO | Fixture: setActiveOwner deja acceso Protected; se añadió resolveLocalProtection(ownerId, false). Tras preparar sesión remota, stale replay pasa; queda cuota por código esperado PARENT_COMMAND_FAILED frente a BLOCKED_BY_PREVIOUS (política anterior). |
| `BudgetScreensTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: test del selector espera seleccionar al abrir rama cat-food y test del vacío no desplaza hasta crear; fixtures de interacción antiguos. |
| `SyncCategoryCommandsWorkerTest` | TEST ROTO | Fixture: remoteSession relajado devolvía proxy sin tipo; se añadió RemoteSession.Valid tipado. Parcial: aun con dueño/sesión válidos, el fixture simula getPendingOutboxCommands y no el nuevo claim; devuelve un comando relajado sin dueño válido (Inactive category owner). |
| `CategoriesScreenTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: restaurant pertenece a iconos rápidos; el fixture personalizado ahora usa school. Solo ese método se repite. |
| `PickerSettingsDeviceTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `DebtChangeFeedTest` | TEST ROTO | Expectativa anterior: el feed persiste snapshot de conflicto y avanza cursor con Applied(1,0); el test espera Conflict y checkpoint ausente. |
| `DebtMovementDetailTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `DebtPlanningTabTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `DebtScheduleAndClosureFlowTest` | TEST ROTO | Textos antiguos: aviso actual Las cuotas son planificación: no registran pagos y ayuda abreviada; sin cambiar la prueba. |
| `DebtSettlementFlowTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: se busca texto anterior El principal reduce el saldo exigible; no existe en copia actual. |
| `PayableDebtFlowTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: intenta performTextInput en debt-due-date ahora readOnly; corresponde abrir selector de fecha. |
| `GoalContributionFlowTest` | TEST ROTO · corregido y PASS | Texto del selector ahora incluye S/; actualizado el texto esperado. |
| `GoalLifecycleFlowTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `GoalManagementFlowTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `MovementHistoryQueryTest` | BUG REAL | BUG REAL A16-01: el filtro Fallo histórico contradice la exclusión global de FAILED. |
| `MovementRevisionLocalTest` | TEST ROTO | Expectativa anterior: intenta reclamar revisión sucesora sin sincronizar el alta; ahora el predecessor debe recibir acuse. Fallo en línea 174, no en los saldos anteriores que ya pasaron. |
| `SyncMovementsWorkerTest` | TEST ROTO · corregido y PASS | Fixture VOIDED enviaba ledger vacío; el feed conserva original más reversión. Actualizado a dos entradas con neto cero, conservando las aserciones de dinero e identidad. |
| `MovementRefundBudgetIntegrationTest` | TEST ROTO · corregido y PASS | Fixture: setActiveOwner deja acceso Protected; se añadió resolveLocalProtection(ownerId, false). |
| `Issue48ProductionCreditLabelsTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: espera abreviatura Créd. disp.; UI actual usa terminología completa de crédito. |
| `MovementDetailCorrectionsTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `MovementFiltersIssue19Test` | BUG REAL | BUG REAL A16-02: campo Máximo recortado en viewport compacto, fontScale 1.6 y teclado; captura filter-compact-gap.png. |
| `MovementHistoryPerformanceTest` | MIXTO: TEST ROTO + BUG REAL | Mixto: prueba UI usa remoteSession relajado sin tipo; consulta real de 10000 filas incumple presupuesto p95 (A16-03). |
| `MovementHistoryScreenTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: espera bloquear CARD_PURCHASE sin voidBlockCode; decisión L2 permite anularla. Expectativa anterior. |
| `MovementRefundScreenTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `MovementSwipeReceiptsTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `MovementUxRefinementTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `QuickMovementScreenTest` | TEST ROTO · corregido y PASS | Texto antiguo Más detalles (opcional); actualizado a Más detalles. También se actualizó la nota a la sección Más detalles y la etiqueta Comercio; PASS final. |
| `NotificationActionsTest` | TEST ROTO · corregido y PASS | Etiqueta antigua Alerta · Condición ocurrida; actualizado a Alerta. |
| `NotificationCenterScreenTest` | TEST ROTO · corregido y PASS | Etiqueta antigua Recordatorio · Fecha prevista; actualizado a Recordatorio. |
| `PlanPurchaseScreenTest` | TEST ROTO | Test roto: espera un nodo purchase-price-shimmer pero la carga actual expone tres ofertas; no modificado. |
| `ReceiptCandidateRoomTest` | TEST ROTO | Contrato anterior: espera deduplicación solo por referencia OCR. Store actual pone referenceFingerprint=null porque OCR no identifica cuenta; líneas 28,39,62 de RoomReceiptCandidateStore.kt. |
| `ReceiptEvidenceTest` | TEST ROTO | Guard de fixture exige com.kipu.app.lab; build solicitado com.kipu.app. Se mantuvo la guardia; el cuerpo de evidencia no se ejecutó. |
| `RecurrenceLifecycleFlowTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `RecurrenceRegistrationFlowTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `SyncProfilePreferencesWorkerTest` | TEST ROTO | Política anterior: espera Failure por el texto de una IllegalStateException genérica. Worker actual reintenta fallos de repositorio; no clasifica cadenas de texto. |
| `PermissionsScreenTest` | TEST ROTO | Flujo anterior: el primer Explicación abre el explainer visual OCR KIP-18, no el diálogo clásico Aceptar y Continuar. |
| `SetupEvidenceTest` | TEST ROTO | Guard de fixture exige com.kipu.app.lab; build solicitado com.kipu.app. Se mantuvo la guardia; el cuerpo de evidencia no se ejecutó. |
| `StatementEvidenceTest` | TEST ROTO | Guard de fixture exige com.kipu.app.lab; build solicitado com.kipu.app. Se mantuvo la guardia; el cuerpo de evidencia no se ejecutó. |
| `CellularCorrectionsTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. Además: contributionRejectsLettersAndKeepsDecimalAmount invoca performScrollTo sin padre con Scroll SemanticsAction; fixture de interacción antiguo. |
| `ObjectSourceNavigationTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `OptionalDateFormsTest` | TEST ROTO | Interacción anterior: performTextInput en goal_form_date ahora readOnly (IsEditable=false); se debe abrir calendario. |
| `PlanningVisualEvidenceTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |
| `ThemeScreenEvidenceTest` | TEST ROTO | Host ComponentActivity sin Hilt al componer avisos nuevos EntitySyncNotice/RejectedInstrumentList. Algunas ejecuciones instrumentadas cierran el proceso por esta excepción. |

### Tests modificados, sin commit

Solo se cambiaron **15 archivos androidTest**. Los cambios preparan dueño/protección/sesión/lease, actualizan un payload VOIDED a original más reversión y corrigen textos/fixtures. Se conservaron las aserciones de dinero; no se rebajaron presupuestos de rendimiento ni se tocaron bugs de producción.

- `app/src/androidTest/java/com/kipu/app/core/security/LocalLockScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/FinancialRejectionCompensationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/budgets/BudgetCrudFinancialIsolationIntegrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/budgets/BudgetHierarchyAlertsIntegrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepositoryRoomTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/budgets/data/sync/BudgetTombstoneReplayIntegrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/CategoriesScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalContributionFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/MovementRefundBudgetIntegrationTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/QuickMovementScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationActionsTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreenTest.kt`

`SyncCategoryCommandsWorkerTest` y `BudgetTombstoneReplayIntegrationTest` siguen fallando por los contratos de fixture restantes descritos. De `CategoriesScreenTest` solo se corrigió y repitió el método del icono personalizado; los demás fallos Hilt siguen abiertos.

## Fase B: escenarios reales

| Escenario | Resultado | Evidencia y alcance |
|---|---|---|
| Migración 40→41 | omitido (parcial verificado) | La copia anterior a la apertura explícita ya tenía encabezado 41; no hay evidencia de 40 ni dashboard anterior a instalar. Primera apertura y regreso tras tests correctos, sesión intacta y saldos iguales a la primera observación postinstalación. Evidencia: `after-first-open.png; B-dashboard.png; kipu-backup-before.tar` |
| Apertura 100→200→300 | FALLA (vista previa) | Saldo real final 300 y cinco entradas esperadas de apertura/reversos/ajustes con neto 300, sin duplicados. La segunda vista previa anuncia 400 aunque se guarda 300 (A16-04). Evidencia: `B-correction-300-preview.png; B-apertura-300-result.png; B-apertura-history.png` |
| Eliminar compra de tarjeta en 3 cuotas | OK | UX-tarjeta antes: deuda 0, disponible 1000. Se registró QA-error por S/ 3 en 3 cuotas de S/ 1; detalle Sincronizado. Eliminar desde historial la oculta y devuelve deuda 0/disponible 1000. Evidencia: `R-card-before.png; R-three-ready.png; R-three-saved.png; R-three-detail.png; R-three-delete-dialog.png; R-three-deleted.png; R-card-restored.png` |
| Reembolso parcial de tarjeta y eliminación | FALLA | A16-05: compra directa QA-reembolso por S/ 4 sincronizada, reembolso de S/ 1 rechazado como compra no elegible; queda No sincronizado. La eliminación del reembolso funciona y la compra vuelve a mostrar S/ 4 Sincronizado; después se eliminó la compra y la deuda volvió a 0. Propagación: pendiente de 2 teléfonos. Evidencia: `R-refund-saved.png; R-refund-created.png; R-refund-failed-detail.png; R-refund-deleted.png; R-refund-purchase-after.png; R-refund-clean.png; R-refund-balance-restored.png; R-logcat-refund.txt` |
| Gasto normal sin opción Reembolso | OK | QA-normal de 1: Sincronizado; ofrece Anular y Editar y no Reembolso. Evidencia: `B-normal-actions.png` |
| Offline: 12 movimientos y dos ediciones | OK | Evidencia heredada completa: 12 altas y primera editada dos veces hasta S/ 3; los 12 detalles muestran Sincronizado y se eliminaron. Primera comprobación a 35,3 s de reconectar; comprobación secuencial del último a 398,5 s, que no mide su latencia individual. No se reabrió la app para sincronizar. Búsqueda actual sin resultados. Evidencia: `offline-created.jsonl; offline-sync-results.json; B-QA-offline-*-sync.png; B-QA-offline-*-deleted.png; R-offline-clean.png` |
| Privacidad / bloqueo / widgets / importes | OK parcial / omitido bloqueo | Importes ocultos en las secciones capturadas de dashboard, cuenta, tarjeta (incluido historial), meta, deuda y detalle de pago; sin cifras monetarias en sus descripciones accesibles. Restaurado ocultar=false. Bloqueo original desactivado; dispositivo sin PIN/biometría, por lo que no se puede probar estado bloqueado ni widget Kipu bloqueado sin configurar seguridad del dueño: omitido. FLAG_SECURE ausente en debug según flags 0x81810100 y MainActivity.kt:205–209; no se certifica privacidad de Recientes con bloqueo activo ni release. Evidencia: `R-lock-original.png; R-hidden-dashboard.png; R-hidden-account.png; R-hidden-card.png; R-hidden-card-bottom.png; R-hidden-goal.png; R-hidden-debt.png; R-hidden-payment.png; R-window-flags.txt; R-recents-debug.png; R-widget-unlocked.png; R-widget-state.txt` |
| Recurrencia con próximo vencimiento hoy | OK | QA-recurrente, 14/11/2026, mensual, aviso 2 días. Log registra RecurrenceReminderWorker SUCCESS y programación posterior. Cancelada por UI; el historial queda conservado por la app. Evidencia: `B-recurrence-saved.png; B-recurrence-list.png; recurrence-work-log.txt; B-recurrence-cancelled.png` |
| Fecha editada conserva el día | OK | Movimiento QA-normal editado a 12/11/2026; historial y detalle muestran 12, no 11. Evidencia: `B-date-edit-ready.png; B-date-history.png; B-normal-after-date.png` |
| Importe 150,50 y alias de 16 dígitos | OK | En corrección de apertura QA-apertura, 150,50 previsualiza y guarda S/ 150.50. La edición de alias 1234567890123456 muestra No ingreses el número completo de la tarjeta ni el CVV; no cierra la app y se descarta sin guardar. Setup inicial no se repite para preservar la cuenta. Evidencia: `R-decimal-ready.png; R-decimal-saved.png; R-alias-16.png` |
| Estabilidad de la sesión | OK Fase B | Sin FATAL EXCEPTION ni IllegalStateException: Migration desde PHASE_B_START (12:26:19) hasta captura final 13:30:40. Se analizaron los logs heredados y actuales; los FATAL anteriores son fixtures de Fase A, por lo que la ejecución completa de ambas fases contiene cierres. Saldos finales iguales a primera apertura postinstalación y sesión intacta. Evidencia: `R-stability-summary.json; R-phaseB-filtered.txt; logcat-session.txt; R-logcat-final.txt; R-final-dashboard.png` |

La validación de propagación hacia un segundo dispositivo queda **pendiente de 2 teléfonos**. No se usó otro serial.

## Bugs reales encontrados

### A16-01 — El filtro Fallo hist?rico nunca devuelve filas FAILED

- Severidad: media.
- Archivo/línea: `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilder.kt:52; :156`.
- Prueba: `MovementHistoryQueryTest.financialStateFiltersUseTheirDatabaseStateMappings` (línea 424).
- Salida/observación: expected:<[tx-failed]> but was:<[]>
- Detalle/evidencia: Predicado global status != FAILED combinado con filtro avanzado status IN FAILED. La UI sigue ofreciendo Fallo hist?rico en MovementFilterDraft.kt:85. No se modific? producci?n.

### A16-02 — Campo M?ximo recortado junto al pie fijo con texto grande y teclado abierto

- Severidad: media.
- Archivo/línea: `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:403; :943; :952`.
- Prueba: `MovementFiltersIssue19Test.compactDarkViewportWithLargeTextKeepsPrimaryActionsVisible` (línea 199).
- Salida/observación: field.bottom=281.6dp, footer.top=282.51428dp; clearance requerida 8dp, observada 0.91428dp
- Detalle/evidencia: Fixture Android con densidad x1.25 y fontScale 1.6, introducir 25 en m?nimo y desplazar al m?ximo. Captura filter-compact-gap.png confirma recorte inferior del campo. No afirma fallo con la configuraci?n normal del due?o.

### A16-03 — Consulta paginada del historial de 10000 movimientos supera presupuesto p95 de 300 ms

- Severidad: media.
- Archivo/línea: `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt:227; MovementLocalDataSource.kt:306`.
- Prueba: `MovementHistoryPerformanceTest.tenThousandMovementsLocalQueryP95LatencyUnder300ms` (línea 180).
- Salida/observación: p95=1692ms; 20 consultas tras warm-up: [944,1046,1118,1119,1122,1125,1133,1143,1156,1184,1198,1233,1238,1294,1326,1341,1363,1413,1530,1692]; repetición focalizada p95=1391ms (20 consultas, rango 513–1391ms).
- Detalle/evidencia: Medici?n real del A16, build debug instrumentado, base en memoria con 10000 fixtures; presupuesto del test 300ms. Causa de coste no aislada, no extrapolar a release. Repetición focalizada ya realizada: p95=1391 ms.

### A16-04 — La segunda corrección de apertura previsualiza 400 aunque guarda 300

- Severidad: media.
- Archivo/línea: `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailSections.kt:415; :497`.
- Prueba: `Manual: QA-apertura 100→200→300`.
- Salida/observación: Tras corregir 100→200, el diálogo conserva Saldo inicial registrado 100. Al escribir 300 muestra Diferencia +200 y Saldo actual 200 → 400; aplicar guarda correctamente 300.
- Detalle/evidencia: La diferencia se calcula contra account.initialBalance original en vez de la apertura efectiva corregida. Capturas B-correction-300-preview.png y B-apertura-300-result.png; historial B-apertura-history.png sin duplicados. No se modificó producción.

### A16-05 — Reembolso de compra de tarjeta recién sincronizada rechazado como no elegible

- Severidad: alta; el flujo ofrecido por la app no logra sincronizar un reembolso parcial.
- Prueba manual: tarjeta de prueba existente UX-tarjeta, sin deuda ni pagos nuevos; registrar compra directa QA-reembolso por S/ 4, esperar su alta, registrar reembolso de S/ 1 el mismo día (14/11/2026).
- Resultado: historial muestra No sincronizado / No se pudo sincronizar. Detalle muestra «La compra no cumple las condiciones para reembolso. Puedes anular el reembolso rechazado». La compra original muestra Sincronizado; no se registró pago ni anulación de esa compra antes del reembolso.
- Archivo/línea para investigar: `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:197; :217; :231`, `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt:1123`; el rechazo PURCHASE_NOT_ELIGIBLE está definido en `supabase/migrations/20261111130000_card_purchase_void_and_refund_rules.sql:545; :554`.
- El texto de UI corresponde a PURCHASE_NOT_ELIGIBLE; el log registra RETRY del worker a 13:24:28. No contiene el cuerpo RPC. La causa exacta y el estado remoto no se aislaron: no se ejecutó SQL ni se consultó Supabase, y la migración local no prueba despliegue remoto. Se informa el fallo del flujo observado, sin atribuirlo a una condición concreta del servidor.
- Evidencia: `R-refund-saved.png`, `R-refund-created.png`, `R-refund-failed-detail.png`, `R-logcat-refund.txt`. Tras eliminar el reembolso, `R-refund-purchase-after.png` muestra la compra completa de S/ 4 Sincronizado. Se eliminó después esa compra: `R-refund-clean.png`; tarjeta restaurada a deuda 0 y disponible 1000 en `R-refund-balance-restored.png`.
- Sin cambios de producción ni repetición de la compra para evitar más datos en el teléfono.

## Estado final del teléfono

- Sesión iniciada, dashboard visible. Disponible S/ 584.29, BCP S/ 372.71 y efectivo S/ 211.58, iguales a `after-first-open.xml`/`.png`. La tarjeta UX-tarjeta vuelve a deuda 0 y disponible 1000. No se conservó ninguna edición de su alias.
- Escalas establecidas con `settings put` y leídas: animator_duration_scale=1.0, transition_animation_scale=1.0, window_animation_scale=1.0. Fuente original 0.9, sin cambios. airplane_mode_on=0. Evidencia: `R-final-settings.json`.
- Ocultar saldos restaurado a false; bloqueo local permanece desactivado, sin configurar credenciales. No se cambiaron ajustes de seguridad del sistema.
- Limpieza por UI: QA-error, QA-reembolso (compra y reembolso), QA-normal y los doce QA-offline eliminados del historial; búsquedas sin resultados para los movimientos de estas pruebas. QA-recurrente cancelada por el worker anterior; se conserva su historial según la app. QA-apertura se corrigió a **cero** y se archivó: desaparece de cuentas activas y no aporta al saldo.
- **Límite de limpieza:** no hay borrado físico de la cuenta en esta UI. Permanecen QA-apertura en Archivados, sus aperturas/reversiones y los registros contables anulados de compras/reembolsos; el detalle de tarjeta puede seguir listando compras anuladas. No se borraron datos mediante acceso directo para eliminarlos. Evidencia: `R-zero-ready.png; R-zero-saved.png; R-archive-dialog.png; R-apertura-archived.png; R-normal-clean.png; R-recurrence-clean.png; R-offline-clean.png`.
- Al retomar se cerraron formularios con BACK sin guardar. Los datos QA-F5-* y UX-* del 5/6 de noviembre ya existían antes de esta sesión y se conservaron; no se eliminó indiscriminadamente por prefijo. En el historial de tarjeta también aparece QA-error de las 13:01 del relevo previo, ya sin efecto económico y oculto del historial general al retomar.
- Sin código de producción modificado, sin SQL/MCP Supabase, desinstalaciones, borrado de datos, commits ni push. Se conservaron los **15 androidTest modificados** existentes, listados arriba; esta continuación no añadió arreglos de tests.
- Pendientes externos: bloqueo/biometría no disponible en este teléfono, privacidad en release, propagación con **2 teléfonos** y certificación anterior/posterior de migración 40→41 por falta de evidencia previa. Cuatro clases instrumentadas siguen parciales por TIMEOUT, con las causas documentadas. Los cinco bugs reales quedan reportados para corrección posterior.


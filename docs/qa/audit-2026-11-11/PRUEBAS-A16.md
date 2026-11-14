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

## Nueva prueba tras FIX-A16 (2026-11-14)

**Repetición cerrada con fallos pendientes; no constituye aprobación global de la rama.**

La primera instalación y las 57 clases instrumentadas corresponden a `4253bffa`. Durante la prueba real se encontró un nuevo fallo al leer el reembolso ya sincronizado; se informó al coordinador y se conservó el caso hasta recibir su corrección `6e82e7d3`. Por instrucción expresa del coordinador se instaló después esa revisión y se verificaron el historial, ambos detalles y la limpieza financiera. No se repitieron las clases verdes después de esa instalación; esta distinción limita el alcance de cada evidencia.

Se usó exclusivamente el adb del SDK con `-s RFGL70J9E2T`. Instalación inicial mediante `gradlew.bat :app:installDebug :app:installDebugAndroidTest`; instalación adicional autorizada mediante `:app:installDebug`, ambas BUILD SUCCESSFUL y conservando los datos. Se ejecutó `am force-stop com.kipu.app` después de instalar. No se usaron tareas connected, desinstalaciones, `pm clear`, acceso SQL manual ni MCP Supabase. Este despacho no modificó producción ni tests y no hizo commits ni push; el commit adicional fue entregado por el coordinador.

FIX-A16 no incorpora migración Room: sigue en **41**, sin 41→42. Se guardó además `FIX-backup-before-open.tar` antes de la apertura explícita: **31.163.904 bytes, 117 entradas, TAR legible y encabezado de kipu.db 41**. No hubo restauración. La primera apertura conserva sesión y los saldos del informe anterior; no se tomó una captura inmediatamente anterior a esta instalación. La corrección adicional tampoco cambia el esquema.

Las evidencias `FIX-*` y `FIXH-*` se encuentran en el mismo scratch privado `C:\Users\Alume\AppData\Local\Temp\kipu-a16-20261114`; contienen datos del dueño y no se versionaron. Inventario y resultados: `FIX-selected.json`, `FIX-results.json` y `FIX-reviewed-results.json`. Instalaciones: `FIX-install.txt` y `FIX-HISTORY-install.txt`.

| Caso | Antes | Ahora | Evidencia |
|---|---|---|---|
| A16-05: compra directa S/4 y reembolso S/1 | Reembolso rechazado como no elegible | **PASS tras corrección adicional del historial.** Compra y reembolso muestran Sincronizado; detalle de ambos abre. Deuda 0→4→3; al anular reembolso 4 y al anular compra 0, crédito disponible 1.000. | `FIX-cards.png`, `FIX-purchase-detail.png`, `FIX-card-post-refund.png`, `FIXH-search-both.png`, `FIXH-refund-detail.png`, `FIXH-purchase-detail.png`, `FIXH-card-debt4.png`, `FIXH-card-debt0.png` |
| A16-04: apertura 100→200→300 | Segunda vista previa +200 y 200→400, aunque guardaba 300 | **PASS.** Apertura efectiva registrada 200; diferencia +100 y saldo 200→300. Guarda 300. Se corrigió después a cero y se archivó QA-apertura2. | `FIX-account-100.png`, `FIX-correct300-preview.png`, `FIX-account-300.png`, `FIX-zero-ready.png`, `FIX-apertura-archived.png`, `FIXH-archived-list.png` |
| A16-01: Fallo histórico | Filas FAILED excluidas globalmente; test fallaba | **PASS.** El filtro real devuelve una fila histórica del 5/11 con No se pudo sincronizar; al reabrir muestra contador 1. Se conservó esa fila preexistente. MovementHistoryQueryTest pasa 11 casos, incluido el mapeo de estados financieros. | `FIXH-failed-result-stable.png`, `FIXH-failed-count-reopened.png`, `FIX-com.kipu.app.feature.movements.data.local.MovementHistoryQueryTest.txt` |
| A16-02: Máximo y acciones con teclado | Máximo recortado en viewport compacto oscuro, fontScale 1.6 | **SIGUE FALLANDO** el método solicitado: separación 0,91428 dp frente a los 8 dp exigidos y recorte inferior visible. Con fuente normal 0.9, la captura manual con teclado sí muestra Máximo completo y acciones visibles. | `FIX-compact-before-clearance-check.png`, `FIX-filter-keyboard-max.png`, `FIX-com.kipu.app.feature.movements.presentation.MovementFiltersIssue19Test.txt` |
| A16-03: 10.000 movimientos | p95 1.692 ms; repetición 1.391 ms, presupuesto 300 ms | **PASS** de los dos métodos instrumentados, incluido p95 <300 ms y paginación de 1.000 IDs sin duplicados. La salida verde no imprime el valor exacto ni las 20 muestras: se certifica el límite, no se inventa una cifra puntual. | `FIX-com.kipu.app.feature.movements.presentation.MovementHistoryPerformanceTest.txt` |
| Scroll del historial real | Sin nueva medición comparable en el informe anterior | Responde a tres gestos y sigue mostrando filas, sin pantalla vacía ni error de historial. gfxinfo debug: 484 cuadros, p95 de cuadro 29 ms; janky actual 3 (0,62%), legacy 108 (22,31%). Es una comprobación del scroll; no es el p95 de consulta anterior. | `FIXH-scroll-start.png`, `FIXH-scroll-1.png`, `FIXH-scroll-2.png`, `FIXH-scroll-3.png`, `FIXH-scroll-gfxinfo.txt` |
| Fallo nuevo: historial tras reembolso sincronizado | No registrado en la prueba anterior, que rechazaba el reembolso | En 4253bffa aparece No pudimos cargar el historial / IllegalArgumentException después de sincronizar el reembolso. **Resuelto en dispositivo con 6e82e7d3:** historial, búsqueda, detalles y anulaciones funcionan. | `FIX-refund-result.png`, `FIX-after-retry.png`, `FIX-logcat-refund.txt`, `FIXH-history.png`, `FIXH-search-both.png`, `FIXH-refund-detail.png`, `FIXH-purchase-detail.png` |
| Estabilidad | Prueba anterior contenía FATAL de fixtures Hilt | **Sin FATAL EXCEPTION de com.kipu.app durante esta repetición.** Hubo 21 errores recuperables History read failed antes del arreglo adicional; desde el marcador de las 15:39:18 hay cero History read failed y cero FATAL. Intervalo total: 15:18:25-15:47:44. | `FIX-stability-logcat.txt`, `FIX-stability-filtered.txt`, `FIX-stability-summary.json` |

### Clases de Fase A repetidas

Se volvieron a ejecutar las **55 clases** marcadas TEST ROTO/MIXTO, incluidas las que ya habían pasado tras ajustes de la prueba anterior: **32 PASS, 20 FAIL y 3 omitidas por variante lab**, sin timeouts. Junto a MovementHistoryQueryTest y al método compacto de MovementFiltersIssue19Test son **57 clases: 33 PASS, 21 FAIL y 3 omitidas**. No se ejecutaron las 19 clases prohibidas del inventario previo ni una suite global indiscriminada.

Cada fila tiene su salida completa en `FIX-<FQCN>.txt`; el FQCN y la duración están en `FIX-reviewed-results.json`. FAIL significa que la clase no queda aprobada aunque algunos métodos pasen. Los tres tests lab imprimen OK desde el runner, pero su @Before usa Assume para omitir los cuerpos en com.kipu.app: aquí se cuentan como OMITIDOS. En SyncCategoryCommandsWorkerTest hay errores explícitos y no hay resumen final completo; no se certifican los métodos restantes.

| Clase | Antes | Ahora | Detalle |
|---|---|---|---|
| `MovementHistoryPerformanceTest` | FAIL | PASS | OK (2 tests). |
| `DashboardEmptyStateTest` | FAIL | PASS | OK (7 tests). |
| `DashboardFinancialMessagingTest` | FAIL | PASS | OK (3 tests). |
| `DashboardRedesignStatesTest` | FAIL | PASS | OK (8 tests). |
| `MovementRoomMigrationTest` | FAIL | PASS | OK (10 tests). |
| `LocalLockScreenTest` | PASS tras fixture/texto | PASS | OK (2 tests). |
| `SyncInstrumentCommandsWorkerTest` | PASS tras fixture/texto | PASS | OK (7 tests). |
| `FinancialRejectionCompensationTest` | PASS tras fixture/texto | PASS | OK (2 tests). |
| `AccountDetailScreenTest` | TIMEOUT | FAIL | 1 error(es) explícito(s); primero: `creditCardDetailShowsCanonicalLimitAndAvailableCreditCopy`. |
| `AccountPresetScreenshotTest` | TIMEOUT | PASS | OK (12 tests). |
| `Issue48ProductionLabelsTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `populatedDashboardRouteUsesEpCtaFinancialLabels`. Host sin Hilt. |
| `AuthVisualFlowTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `loginAndRegistrationRenderAndValidateConfirmation`. |
| `BudgetCrudFinancialIsolationIntegrationTest` | PASS tras fixture/texto | PASS | OK (1 tests). |
| `BudgetHierarchyAlertsIntegrationTest` | PASS tras fixture/texto | PASS | OK (1 tests). |
| `OfflineFirstBudgetRepositoryRoomTest` | PASS tras fixture/texto | PASS | OK (3 tests). |
| `BudgetTombstoneReplayIntegrationTest` | FAIL | PASS | OK (2 tests). |
| `BudgetScreensTest` | TIMEOUT | FAIL | 8 error(es) explícito(s); primero: `deleteConfirmationInvokesDeleteOnce`. |
| `SyncCategoryCommandsWorkerTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `workerDispatchesSourceAliasAndPreferenceCommandsAndAdvancesLocalRevisions`. Sin resumen final. |
| `CategoriesScreenTest` | FAIL | PASS | OK (10 tests). |
| `PickerSettingsDeviceTest` | FAIL | PASS | OK (2 tests). |
| `DebtChangeFeedTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `nonIncreasingSequenceRejectsWholePageWithoutPartialWrites`. |
| `DebtMovementDetailTest` | FAIL | PASS | OK (3 tests). |
| `DebtPlanningTabTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `withRecordsTheListShowsTotalsCountsAndDueSoonInstallment`. |
| `DebtScheduleAndClosureFlowTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `scheduledInstallmentIsClearlyNotAPaymentAndReminderOpensItsDebt`. |
| `DebtSettlementFlowTest` | FAIL | FAIL | 3 error(es) explícito(s); primero: `payableSheetKeepsPrincipalAndInterestSeparateWhenSubmitting`. |
| `PayableDebtFlowTest` | FAIL | FAIL | 3 error(es) explícito(s); primero: `dueDateFieldRetainsInputAndParsesACompleteDate`. |
| `GoalContributionFlowTest` | PASS tras fixture/texto | PASS | OK (4 tests). |
| `GoalLifecycleFlowTest` | TIMEOUT | FAIL | 1 error(es) explícito(s); primero: `synchronizationConflictOffersAllThreeResolutionOptions`. |
| `GoalManagementFlowTest` | FAIL | FAIL | 5 error(es) explícito(s); primero: `freePlanShowsItsTwoGoalLimitAndHidesCreateActionAtQuota`. |
| `MovementRevisionLocalTest` | FAIL | PASS | OK (7 tests). |
| `SyncMovementsWorkerTest` | PASS tras fixture/texto | PASS | OK (28 tests). |
| `MovementRefundBudgetIntegrationTest` | PASS tras fixture/texto | PASS | OK (2 tests). |
| `Issue48ProductionCreditLabelsTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `creditCardDetailShowsAndExposesTheProductionFinancialLabels`. |
| `MovementDetailCorrectionsTest` | FAIL | PASS | OK (1 tests). |
| `MovementHistoryScreenTest` | FAIL | PASS | OK (11 tests). |
| `MovementRefundScreenTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `showsPurchaseAndRefundPeriodsMasksMoneyAndSubmitsMinorUnits`. |
| `MovementSwipeReceiptsTest` | FAIL | PASS | OK (7 tests). |
| `MovementUxRefinementTest` | FAIL | PASS | OK (11 tests). |
| `QuickMovementScreenTest` | PASS tras fixture/texto | PASS | OK (7 tests). |
| `NotificationActionsTest` | PASS tras fixture/texto | PASS | OK (6 tests). |
| `NotificationCenterScreenTest` | PASS tras fixture/texto | PASS | OK (5 tests). |
| `PlanPurchaseScreenTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `processingToVerifiedKeepsStatusCardAnchoredAndPriceShimmerReservesSpace`. |
| `ReceiptCandidateRoomTest` | FAIL | PASS | OK (5 tests). |
| `ReceiptEvidenceTest` | FAIL | OMITIDO (requiere lab) | Asunción JUnit: requiere com.kipu.app.lab; cuerpo omitido. |
| `RecurrenceLifecycleFlowTest` | FAIL | FAIL | 2 error(es) explícito(s); primero: `resumeShowsFutureDateAndBackOutSendsNoCommand`. |
| `RecurrenceRegistrationFlowTest` | FAIL | PASS | OK (8 tests). |
| `SyncProfilePreferencesWorkerTest` | FAIL | PASS | OK (3 tests). |
| `PermissionsScreenTest` | FAIL | PASS | OK (4 tests). |
| `SetupEvidenceTest` | FAIL | OMITIDO (requiere lab) | Asunción JUnit: requiere com.kipu.app.lab; cuerpo omitido. |
| `StatementEvidenceTest` | FAIL | OMITIDO (requiere lab) | Asunción JUnit: requiere com.kipu.app.lab; cuerpo omitido. |
| `CellularCorrectionsTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `lightGoalDetailGroupsProgressAndBackingIntoCards`. |
| `ObjectSourceNavigationTest` | FAIL | PASS | OK (4 tests). |
| `OptionalDateFormsTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `debtEditCalendarAndClearReachSave`. |
| `PlanningVisualEvidenceTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `inspectReferenceStatesInBothThemesAndWithLargeLongText`. |
| `ThemeScreenEvidenceTest` | FAIL | FAIL | 1 error(es) explícito(s); primero: `captureCorrectedScreensInBothThemes`. Host sin Hilt. |


### Pendientes y límites

- **A16-02 continúa abierto** en viewport compacto oscuro y fontScale 1.6. Campo bottom=281,6 dp; footer top=282,51428 dp. La captura del test sigue mostrando el borde inferior de Máximo recortado. No se rebajó la separación ni se modificó el test para hacerlo pasar.
- **20 clases de Fase A siguen fallando**, detalladas arriba. Persisten errores de host Hilt, selectores/textos/interacciones y expectativas de fixtures. Su diagnóstico completo y corrección quedan pendientes; los fallos no se convierten automáticamente en decisiones de producto ni en aprobación de la implementación.
- Observación adicional de UI: la fila del reembolso y los diálogos de anulación de operaciones de tarjeta usan la etiqueta genérica Cuenta; estos últimos muestran No hay una cuenta disponible para proyectar este movimiento. Los detalles sí identifican UX-tarjeta y las anulaciones restituyen correctamente la deuda. Evidencia: `FIXH-search-both.png`, `FIXH-refund-delete-dialog.png`, `FIXH-purchase-delete-dialog.png`. Queda pendiente revisar esta presentación.
- El fallo de lectura del historial encontrado durante esta sesión queda **resuelto en la comprobación manual de 6e82e7d3**. El informe de su corrección es `FIX-HISTORY-delivery.md`; no se atribuye su autoría a este despacho.
- No se modificó ningún test en esta repetición: **lista de arreglos de fixtures/textos vacía**. Las clases lab conservan su restricción; no se afirma validación de sus cuerpos.
- Se mantienen fuera del alcance la propagación con dos teléfonos, bloqueo/biometría y validación release. No se consultó el servidor mediante SQL/MCP ni se deduce su despliegue a partir de las migraciones locales.

### Estado final del teléfono

- Sesión iniciada y dashboard visible. Disponible **S/ 584.29**, BCP **S/ 372.71**, efectivo **S/ 211.58**, iguales a la primera apertura y al informe anterior. UX-tarjeta: deuda **S/ 0**, disponible **S/ 1.000**.
- QA-apertura2 se llevó a cero y se archivó; está fuera de cuentas activas y no aporta al saldo. Se conserva su historial contable. Compra QA-reembolso y reembolso anulados por UI, con impacto financiero cero; sus registros permanecen visibles como Anulado para auditoría. No se borraron físicamente datos ni se modificaron registros QA/UX anteriores.
- Escalas de animación restauradas y leídas en **1.0** para animator, transition y window. Se usó 0 durante la pasada instrumentada y 1.0 en la comprobación final. Fuente **0.9**, sin cambios. Modo avión **0**. No se cambiaron credenciales, bloqueo ni preferencias de privacidad.
- Logs privados filtrados desde FIX_A16_START y, por separado, FIX_HISTORY_START_6e82e7d3. Los FATAL antiguos del buffer, anteriores al marcador de esta sesión, se excluyen del dictamen actual sin borrarlos de la evidencia original.
- Evidencia final: `FIXH-final-dashboard.png`, `FIXH-card-debt0.png`, `FIXH-archived-list.png`, `FIXH-archived-zero-detail.png`, `FIXH-final-settings.json`, `FIX-stability-summary.json`.

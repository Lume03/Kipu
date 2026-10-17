# Revalidación instrumentada completa — 2026-10-17

## Ejecución

- Head revisado: `fe6de0bf99a581905f6364eb20f7768f849fc3fe` (`fix/dark-mode-filters-theme-audit` / PR #55).
- Base integrada: `e440bc12c64afdf90faf606d62e34cc7bca42349`.
- Dispositivo: Pixel 10 AVD, Android 17 / API 37.
- Comando: `:app:connectedDebugAndroidTest`.
- Resultado: **348 tests, 16 fallos, 0 errores, 0 omitidos**.

## Pruebas del alcance de tema y filtros

| Suite | Resultado |
|---|---:|
| `MovementFiltersIssue19Test` | 10/10 |
| `MovementThemeRegressionTest` | 1/1 |
| `MovementUxRefinementTest` | 11/11 |
| `KipuThemeContrastTest` | 3/3 |
| `ThemeScreenEvidenceTest` | 1/1 |

Total del alcance directo: **26/26**. Incluye la prueba del viewport compacto, los cambios claro/oscuro con el borrador abierto y la evidencia de pantallas.

## Fallos de la suite completa

Los cuatro primeros ya estaban clasificados como reproducibles en la base integrada en el registro de auditoría. Los otros doce, antes sin atribución, se ejecutaron individualmente contra `e440bc1`; los doce también fallaron allí.

| Test | Comparación con `e440bc1` |
|---|---|
| `CreditLiabilityMigrationTest#migrationLinksCardsAndRebuildsPurchaseAndPaymentLiabilityEffects` | Reproducido anteriormente |
| `FinancialRejectionCompensationTest#cardPaymentPermanentRejectionAppliesVoidedAndCompensatesBothAccountsWithoutPhysicalDelete` | Reproducido anteriormente |
| `SyncInstrumentCommandsWorkerTest#confirmedPurchaseUsesCanonicalRpcAndAcknowledgesDuplicateIdempotently` | Reproducido anteriormente |
| `PlanPurchaseScreenTest#processingToVerifiedKeepsStatusCardAnchoredAndPriceShimmerReservesSpace` | Reproducido anteriormente |
| `AuthVisualFlowTest#loginAndRegistrationRenderAndValidateConfirmation` | Falló al repetirlo individualmente |
| `AuthVisualFlowTest#registrationFitsA16WindowWithoutScrollingWithKeyboardClosed` | Falló al repetirlo individualmente |
| `MerchantAliasRulesScreenTest#aliasExplainsExactMatchAndKeepsSourceAndCanonicalMerchantSeparate` | Falló al repetirlo individualmente |
| `MovementRevisionLocalTest#reviseAccountReversesOldAccountAndDebitsNewAccount` | Falló al repetirlo individualmente |
| `MovementRevisionLocalTest#dateChangeFromPeriodAToPeriodBUpdatesConsumptionToAZeroBTwentyWithCashBalanceIntact` | Falló al repetirlo individualmente |
| `MovementRevisionLocalTest#commitRevisionAtomicAppendsCompensationsAndRebuildsBalance` | Falló al repetirlo individualmente |
| `MovementRevisionLocalTest#reviseReplayReturnsDuplicateWithoutNewLedgerEntries` | Falló al repetirlo individualmente |
| `SyncMovementsWorkerTest#voidTransactionReplay100TimesProducesZeroDuplicateLedgerEntries` | Falló al repetirlo individualmente |
| `SyncMovementsWorkerTest#conflictResponseCreatesConflictProposalAndStopsRetry` | Falló al repetirlo individualmente |
| `NotificationsDatabaseTest#migration11To12CreatesAccountScopedNotificationProjectionAndOutbox` | Falló al repetirlo individualmente |
| `NotificationCenterScreenTest#billingReminderRendersStructuredDueDateAsExpectedFutureDate` | Falló al repetirlo individualmente |
| `KipuDatabaseSchemaTest#representativeVersionOneRowsSurviveValidationAndNormalReopen` | Falló al repetirlo individualmente |

No se identificaron fallos nuevos atribuibles al diff de #55: todos los fallos de la suite completa se reprodujeron en la base integrada, y las 26 pruebas del alcance directo pasaron. La suite global no queda verde.

## Pendientes de aceptación

- T122 sigue abierta por la preview de pantalla completa de `UnifiedInstrumentFormScreen`.
- T123 sigue abierta porque falta una captura verificable con el IME real; la captura compacta actual muestra el árbol Compose sin el teclado del sistema.
- T125 sigue abierta hasta aceptación visual humana. No se marca cerrado #39.

# W2 ? APPROVED

Implemented savings/debit imports in the existing statement pipeline, without editing Compose, branching, committing, installing, or running connected tests.

## Validation

- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain`: BUILD SUCCESSFUL in 1m 42s (48 actionable tasks: 13 executed, 35 up-to-date).
- Full XML results: 1454 tests, 0 failures, 0 errors, 0 skipped.
- Statements: ConfirmStatementImportTest 31, ImportCardStatementTest 13, StatementImportRoomTest 14; all green, as are existing statement UI/VM tests and parser tests.
- An explicit `exit $LASTEXITCODE` wrapper confirms process exit 0 with both requested Gradle tasks up-to-date (PowerShell had otherwise returned 1 for JVM warnings despite BUILD SUCCESSFUL).
- Logs: `.run/w2-gradle-final.log`, `.run/w2-gradle-confirm.log`.
- `git diff --check`: clean.

## Storage and behavior

Room version 33; registered MIGRATION_32_33 adds source TEXT NOT NULL DEFAULT 'CREDIT_CARD' and nullable account_reference, and exports schema 33.json. Reused card_id because both card types already share the cards catalog; source determines and validates the eligible type, and the debit card resolves its active linked account. Only the final four characters of accountReference are persisted.

DEPOSIT_ACCOUNT always starts without a chosen card; fingerprint stays statement-scoped after manual choice, preserving duplicate-PDF detection across card changes. Debit selection rejects credit cards, unavailable cards/accounts, and different currencies, recalculates account duplicates, and unselects duplicates without selecting previously excluded possible own transfers. Existing credit fingerprint behavior remains intact.

ACCOUNT_DEBIT and ACCOUNT_CREDIT delegate to RegisterTransaction as EXPENSE/INCOME with the linked account in sourceAccountId (the existing command also uses that field for income), required effective category, reviewed similarity override, and Lima noon. StatementResolutionKey is the deterministic command idempotency key; capture receipts recover split commits before validating edited draft values. Expense merchant preference prefill/learning is reused; income learning is omitted because preferences are not typed for income. Category checks enforce active, unlocked, owner-visible expense/income/general categories.

## W3 UI integration

StatementReviewUiState exposes source, debitCards, selectedCard: Card?, and incomeCategories; cards/card retain their CreditCard types and categories retains expense/general options. canConfirm/selectable/confirm use selectedCard; setCard selects the eligible type according to source. Use incomeCategories for ACCOUNT_CREDIT and categories for ACCOUNT_DEBIT; assignCategory already filters selected lines by category type. Compose remains unchanged and compiles; W3 must render the debit selector and source-aware labels/categories.

## Files modified by W2 (W1 files remain in the shared uncommitted tree)

- `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`
- `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`
- `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`
- `app/schemas/com.kipu.app.core.database.KipuDatabase/33.json`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportMatching.kt`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportDao.kt`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportEntities.kt`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportMatchingDao.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/ImportCardStatement.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/PrepareStatementImportReview.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/StatementImportMatching.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/StatementImportStore.kt`
- `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/statements/ConfirmStatementImportTest.kt`
- `app/src/test/java/com/kipu/app/feature/statements/ImportCardStatementTest.kt`
- `app/src/test/java/com/kipu/app/feature/statements/StatementImportRoomTest.kt`
- `app/src/androidTest/java/com/kipu/app/core/database/StatementRoomMigrationTest.kt`

# W2 review fixes ? APPROVED

All three coordinator findings implemented; no commits, phone access, account identity edits, or UI redesign.

## Verification

`./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain` completed with exit 0 and BUILD SUCCESSFUL in 1m 51s (48 actionable tasks: 7 executed, 41 up-to-date).

Full suite: 1468 tests, 0 failures, 0 errors, 0 skipped.

Log: `.run/w2-review-final.log`. Red regressions were reproduced first (43 focused tests, 2 expected failures; `.run/w2-review-red.log`). `git diff --check` clean. The initial full run exposed an old test expecting expense before income; its expectation now follows the requested ordering, and final full validation is green.

## Changes

1. Deposit confirmation sorts occurredDate ascending; within that calendar day all ACCOUNT_CREDIT rows precede ACCOUNT_DEBIT rows, then processDate and PDF position break ties within each side. This explicit within-day priority allows credits processed later on the same value day to fund that day's debits. Credit statement ordering is unchanged. Regression checks interleaved PDF order, previous/next days, process dates, PDF-position ties, and the deterministic resolution keys reaching RegisterTransaction.
2. possibleOwnTransfer is parser provenance, default false in StatementLine and StatementImportLine. The BCP savings parser applies its existing holder-name/TRANSF.BCO rule and persists the result through Room; defaultSelected remains its inverse. UI maybeOwnTransfer reads that field directly, so manually deselecting an ordinary row never creates the hint and selecting/deduplicating a hinted row never erases it.
3. StatementImport.accountTotals is a currency map of StatementAccountTotals(previousBalanceMinor, debitsMinor, creditsMinor, closingBalanceMinor). Eight nullable Room columns persist PEN/USD values from ParsedStatement for deposit imports. The existing deposit totals block displays Saldo anterior ? Saldo final and both monetary balances, preserving the existing statement_deposit_totals_PEN/USD tags, colors and typography. Cargos/Abonos use persisted PDF totals with the previous line-total fallback for legacy drafts.

All schema additions extend unpublished MIGRATION_32_33; database version remains 33 and schema 33.json is regenerated. Migration instrumentation test checks existing statement/line preservation, false hint defaults and null balance defaults; it was compiled only. Room unit tests cover totals in both currencies and hint persistence across selection, edit and duplicate updates; parser and UI regressions cover both positive and negative hints and both currencies' balances.

## Files modified in this revision

- `app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/StatementModels.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt`
- `app/src/main/java/com/kipu/app/feature/statements/domain/StatementImportStore.kt`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportEntities.kt`
- `app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt`
- `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`
- `app/schemas/com.kipu.app.core.database.KipuDatabase/33.json`
- `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementDesign.kt`
- `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt`
- `app/src/test/java/com/kipu/app/feature/statements/ConfirmStatementImportTest.kt`
- `app/src/test/java/com/kipu/app/feature/statements/BcpSavingsStatementParserTest.kt`
- `app/src/test/java/com/kipu/app/feature/statements/StatementImportRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/statements/presentation/StatementReviewScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/core/database/StatementRoomMigrationTest.kt`

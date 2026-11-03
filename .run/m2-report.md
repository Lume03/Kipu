# M2: import merchant suggestions and learning

## Implementation

- Pure `MerchantTextMatcher.suggest(rawText, catalog)` returns at most one active catalog merchant, matching whole words after conservative bank/processor cleanup. Excludes person wallet transfers, bank transfers, deposits, PAGO identifiers and maintenance. Exact saved user aliases still assign directly; other matches stay pending in `suggestedMerchantId` until accepted.
- Accepted/selected merchants resolve an eligible learned category first, otherwise eligible catalog default, for expenses and income. Existing explicit categories survive prefill.
- Shared unpublished 33 to 34 migration includes statement `suggested_merchant_id`, `remember_merchant_alias`/`remember_merchant_category` (default 1), plus `original_description` and `merchant_reviewed` (default 0). Extra provenance fields preserve the alias source through edits and persist rejected/removed suggestions across reopen. Room schema 34 regenerated; existing M1 capture migration remains intact.
- Learning runs after durable registration, observes verified Premium, respects per-line toggles, deduplicates repeated learning, and skips conflicting normalized descriptions/merchant categories throughout the import, including prior registrations and still-selected lines. Rule failures appear as warnings and never fail the financial registration. Alias and category failures are independent.

## UI contract

`StatementReviewUiState`:

- `merchants: List<MerchantCatalogEntry>` contains active choices; resolve merchant/suggestion names using IDs in `snapshot.lines`.
- `rememberAliasOptions: Map<String, RememberAliasOption>` keyed by line ID; options expose `label`, `checked`, `merchantId`, `sourceText`.
- `rememberCategoryOptions: Map<String, RememberOption>` keyed by line ID; uses M1 shared category option.
- `learningWarnings: List<StatementLearningWarning>` exposes `kind`, normalized description or merchant `key`, and relevant `lineIds`.
- `StatementImportLine` exposes `suggestedMerchantId`, `rememberMerchantAlias`, `rememberMerchantCategory`, `originalDescription`, `merchantReviewed`.

VM actions: `acceptMerchantSuggestion(lineId)`, `rejectMerchantSuggestion(lineId)`, `chooseMerchant(lineId, merchantId)`, `removeMerchant(lineId)`, `setRememberMerchantAlias(lineId, remember)`, `setRememberMerchantCategory(lineId, remember)`.

`ConfirmStatementResult.learningWarnings` kinds: `ALIAS_CONFLICT`, `CATEGORY_CONFLICT`, `ALIAS_SAVE_FAILED`, `CATEGORY_SAVE_FAILED`, `LEARNING_FAILED`.

Final visual UI remains assigned to the separate UI worker. No screen or edit sheet changes made by M2.

## Files changed by M2

- app/src/main/java/com/kipu/app/feature/categories/domain/MerchantTextMatcher.kt (new)
- app/src/main/java/com/kipu/app/feature/statements/domain/StatementImportStore.kt
- app/src/main/java/com/kipu/app/feature/statements/domain/PrepareStatementImportReview.kt
- app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt
- app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportEntities.kt
- app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportDao.kt
- app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt
- app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewViewModel.kt
- app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt (shared with M1)
- app/schemas/com.kipu.app.core.database.KipuDatabase/34.json (shared with M1)
- app/src/androidTest/java/com/kipu/app/core/database/StatementRoomMigrationTest.kt (shared with M1)
- app/src/test/java/com/kipu/app/feature/categories/domain/MerchantTextMatcherTest.kt (new)
- app/src/test/java/com/kipu/app/feature/statements/ConfirmStatementImportTest.kt
- app/src/test/java/com/kipu/app/feature/statements/StatementImportRoomTest.kt
- app/src/test/java/com/kipu/app/feature/statements/presentation/StatementViewModelsTest.kt
- app/src/test/java/com/kipu/app/feature/statements/BcpSavingsLocalStatementTest.kt

## Validation

`./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain` passed (BUILD SUCCESSFUL in 2m 4s); a final unchanged invocation confirmed shell exit 0 with all 48 tasks up to date. XML results: **1505 tests, 0 failures, 0 errors, 0 skipped**. M2-related suites: matcher 1 table test, confirmation 42, Room 19, ViewModel 18, local PDFs 2 (82 total). Android migration tests compiled; no device execution requested. `git diff --check` passed. Logs: `.run/m2-full2.log` (full execution) and `.run/m2-final.log` (explicit exit 0).

Real local PDFs (optional test skips when absent) produced **44 suggestions / 631 parsed lines** over four documents using a six-merchant reference catalog. The documents include a duplicated monthly statement; this is sample-level coverage, not a production-catalog recall metric. Examples: `DLC*RAPPI PERU` to Rappi; `YQ-TAMBO BARRERA-R` to Tambo; `STEAMGAMES.COM 425` to Steam; `YQ-BCP*CONAMU` to Conamu; `YP APUESTAS-BETANO` to Betano.

No branches, worktrees, commits, Spec Kit artifacts, remote posts, or phone/instrumented execution used.

# M3 - Merchant discovery verification

Verdict: **APPROVED** (local implementation and automated checks).
Date: 2026-11-02. No remote migrations, deployments, real Logo.dev calls, phone,
branches, commits, Spec Kit changes or helper workers were used. Existing M1/M2 work is preserved.

## Evidence

- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL,
  final run 1m36s, 48 tasks; 1514 unit tests, zero failures/errors/skips.
- Nine new Kotlin tests: repository (5), state/use cases (3), real Room isolation (1).
  The movement snapshot test was updated to assert the new owner-scoped DAO call.
- `deno test supabase/functions/merchant-search/index_test.ts supabase/functions/merchant-logo-sync/index_test.ts`:
  38 passed, zero failed (14 discovery + 24 existing logo tests), no network permission.
- `supabase/tests/database/merchant_discovery_test.sql`: 20/20 pgTAP on local Docker
  `supabase_db_kipu-met-local-20261017`, fixtures rolled back. Local database prepared
  with existing logo foundation/exclusion migrations and M3 SQL only; no remote touched.
- `git diff --check`: clean. Deno source formatted and typechecked by test execution.
- Local logs: `.run/m3-gradle-final.log`, `.run/m3-deno-final.log`, `.run/m3-pgtap.log`.

## Result and decisions

Authenticated users can explicitly search Logo.dev, add a cached candidate globally,
or create a private merchant. Search is local by default, remote only by button.
The provider secret stays server-side; preview hosts are restricted to img.logo.dev.
Search cache TTL is 30 days, user candidate authorization 1 hour, same-query miss lease
30 seconds. Limits: 20 searches/hour, 100/24h, 10 new global merchants/24h; idempotent
adds do not consume another addition or trigger another download.

Audit is in internal.merchant_catalog_audit, never synced to other users. Direct client
insertion/promotion of a merchant to is_system is blocked. Private RLS remains owner-only,
Room 35 adds owner filtering, and movement display resolves private merchants for its owner.
New global versions serialize until commit so incremental readers cannot skip concurrent
writes. Immediate local insertion uses version 0 to preserve the hydration cursor; private
rows are always included in hydration alongside incremental global updates.

Search/add remain available to Free and Premium authenticated users because they extend
the base merchant catalog; Premium learning/preference rules are a separate feature.
Private creation requires connection: there is no existing merchant creation outbox command.
UI integration and remote/provider validation are coordinator work, not certified here.
The logo job is invoked only for the first domain insertion; failures leave the merchant
available and retryable via the existing logo maintenance job.

## UI and deployment handoff

See [merchant-logo-assets](../ux-ui/references/merchant-logo-assets.md#descubrimiento-de-comercios-m3-2026-11-02)
for request bodies, state/actions and exact deployment order.

- Inject `MerchantDiscoveryViewModel`, collect `state`; typing calls onQueryChanged only.
- Explicit Internet button calls searchInternet; candidate action calls addCandidate.
- Private option calls createPrivate (current query); handle error/loading and addedMerchant,
  then consumeAddedMerchant after selecting its ID. MerchantPicker visual UI was not edited.
- Apply `20261102222216_merchant_discovery.sql` after the existing logo migrations.
- Ensure LOGO_DEV_SECRET_KEY via `supabase secrets set --env-file <secure-local-file>`.
- Deploy `supabase functions deploy merchant-search --project-ref <ref>` with JWT verification.
  The existing logo sync module is bundled by import; no separate sync redeployment required.
- Keep merchant-logos bucket/maintenance, ship app with Room 35 and validate using two users.

## Files in M3

- `supabase/migrations/20261102222216_merchant_discovery.sql`
- `supabase/tests/database/merchant_discovery_test.sql`
- `supabase/functions/merchant-search/index.ts`
- `supabase/functions/merchant-search/index_test.ts`
- `supabase/config.toml`
- `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`
- `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`
- `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`
- `app/schemas/com.kipu.app.core.database.KipuDatabase/35.json`
- `app/src/main/java/com/kipu/app/feature/categories/data/MerchantDiscoveryRepositoryImpl.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/local/CategoryEntities.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/local/MerchantCatalogDao.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoriesApi.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoryDtos.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/categories/di/CategoriesModule.kt`
- `app/src/main/java/com/kipu/app/feature/categories/domain/MerchantDiscoveryRepository.kt`
- `app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantDiscoveryViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepositoryTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/MerchantDiscoveryRepositoryTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/MerchantDiscoveryRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/presentation/MerchantDiscoveryViewModelTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/MovementPresentationObservationTest.kt`
- `docs/ux-ui/references/merchant-logo-assets.md`

This report is also part of M3. Shared core files include previous M1/M2 edits, which were preserved.

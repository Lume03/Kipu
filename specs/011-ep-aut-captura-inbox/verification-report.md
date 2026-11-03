# Verification report — EP-AUT Sprint 8

**Verdict: APPROVED**
**Branch:** `011-ep-aut-captura-inbox`
**Verified HEAD:** `63932975d0c2763f4164c01d18d73ded256ac24a`
**Date:** 2026-10-29

## Scope

Capture domain, local Room persistence and migration 28→29, candidate inbox/review, notification ingress, safe ledger confirmation, and the expected-payment association primitives consumed by capture.

## Results

- `:app:testDebugUnitTest --tests 'com.kipu.app.feature.capture.*'` — **121 passed, 0 failures, 0 errors, 0 skipped**.
- `:app:testDebugUnitTest --tests 'com.kipu.app.feature.notifications.data.OfflineFirstNotificationsRepositoryTest'` — **5 passed, 0 failures, 0 errors, 0 skipped**. This covers the local notification repository changed to keep capture review events out of the remote outbox.
- `:app:connectedDebugAndroidTest` filtered to `CaptureAtomicResolutionTest` — **6 passed** on Galaxy A16 (`SM-A165M`, Android 16).
- The same instrumentation task filtered to `CaptureRoomMigrationTest` — **2 passed**.
- The same instrumentation task filtered to `ExpectedPaymentAssociationTest` — **4 passed**.
- `git diff --check origin/main...HEAD` — **PASS**.

No Supabase/pgTAP migration belongs to this Room-only persistence slice. The report verifies local tests and migration behavior; it does not certify remote deployment or real-provider notification compatibility.


## HU-49 / Sprint 9 - 2026-11-03

**Verdict: APPROVED** (local unit/build gates; no device/provider acceptance claimed).

Authority: backlog HU-49 plus Owner D1/D2 recorded in spec.md. Work on main explicitly assigned by coordinator; no push, PR or remote deployment.

- Full `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 1543 tests, 0 failures, 0 errors, 3 skipped.
- After the final symmetric separation fix, `./gradlew :app:testDebugUnitTest --tests '*HybridYapeRoomTest' --tests '*CaptureCorrelationTest' :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 12 tests, 0 failures/errors/skips.
- `git diff --check`: PASS.
- Room **35 -> 36**: capture_evidence with unique owner/reference/source and root index; receipt_candidates.fields_edited protects corrections. Schema 36.json exported. Robolectric migration test executed; MigrationTestHelper full-schema test compiled in AndroidTest, not run on device by instruction.

Coverage: notification/share in either order, equal normalized reference with leading zeros, no second candidate after confirmation, edited fields retained, single/ambiguous weak matches, distinct references at equal amount, inclusive +/-10 minute limits, refund direction/history separation, owner isolation, explicit union with no ledger write, persisted separate decision in both directions, rollback/retry, blurry amount left null, invalid/unknown MIME rejected before bytes, Yape income extraction with confidence/integrity, Free OCR consent, and existing Premium expiry/manual conversion regressions.

The first runs exposed introduced Hilt/selection regressions and an incomplete migration fixture; these were fixed before the green full run. No unrelated preexisting failing tests remain. No Supabase changes or pgTAP migration applies to this local-only increment. Analytics was not touched. Real Yape/provider OCR quality, physical-device UI and instrumented migration execution remain outside this run as explicitly requested.

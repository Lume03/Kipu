# Quickstart: EP-NOT — Centro de Notificaciones y Avisos

## Prerequisites

- JDK 17 and Android SDK from the repository toolchain.
- Gradle wrapper available in the repository.
- For database tests: Docker plus a running local Supabase stack initialized from the migrations.
- For connected UI tests: an Android emulator or device.

## Validate the Android app

From PowerShell at the repository root:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

If a test emulator/device is available:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest
```

Expected: mapping, repository/outbox, Room migration, ViewModel, navigation fallback, badge, filters, empty state, and archive UI tests pass. The screen opens from the dashboard bell; card notice opens its existing card detail; an unavailable target leaves PNOT visible with a snackbar.

## Validate Supabase

Start the local Supabase stack, then run:

```powershell
supabase test db --local
```

Expected: the notification migration applies cleanly to the existing baseline, authenticated owner can SELECT and PATCH `is_read` / `deleted_at`, cross-account SELECT/PATCH affects no rows, anonymous clients have no table access, and authenticated clients cannot insert, delete, or update producer-owned columns.

## Manual acceptance walkthrough

1. Seed an unread `CREDIT_UTILIZATION_THRESHOLD_CROSSED` notice with a `CARD` reference and a `BILLING_DUE` notice with `event_payload.due_date` (`YYYY-MM-DD`) or a producer-supplied expected date in `body` for the signed-in test user.
2. Open the dashboard bell and verify the badge, newest-first list, alert/reminder distinction, and producer-supplied expected date labeled as a future reminder (not as payment confirmation).
3. Filter `Todos`, `Alertas`, and `Recordatorios`; mark one and all as read; verify badge counts update.
4. Dismiss a notice; verify it disappears locally and remains remotely with `deleted_at` set.
5. Disable network, mark/dismiss another notice, restore network, and verify the per-user outbox converges.
6. Tap an existing card reference and verify card detail. Tap a deleted or unsupported reference and verify a friendly message while PNOT remains visible.
7. Switch test accounts and verify no notice/count from the prior account appears.
8. Enable reduced motion and confirm all content/actions remain usable with decorative transitions removed.

The canonical acceptance scenarios are in [spec.md](spec.md); storage and REST details are in [data-model.md](data-model.md) and [contracts/notifications-rest.md](contracts/notifications-rest.md).

## Verification completed for this implementation

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`: passed; all 272 JVM tests passed and the debug APK assembled.
- `./gradlew.bat :app:compileDebugAndroidTestKotlin`: passed; Compose/Room instrumentation tests compile.
- `./gradlew.bat :app:connectedDebugAndroidTest`: not run because `adb devices` reported no attached emulator or device.
- `supabase test db --local`: passed; all 21 pgTAP files and 372 assertions passed. The pending notification migration was applied forward to the local Supabase database before rerunning the suite; no database reset was used.

## Sprint 6 HU-44 — Focused verification

The commands above document the earlier HU-42 baseline. For HU-44, do not run the full `:app:testDebugUnitTest` or full `:app:connectedDebugAndroidTest` suites; the global Android suite contains 413 tests and is explicitly out of scope.

### Focused Android tests

Use the class-filtered test tasks only. Before connected runs, verify `adb devices -l` identifies the attached Samsung Galaxy A16 (`SM_A165M`). Do not use Gradle `--tests` to filter `connectedDebugAndroidTest`; pass the Android instrumentation runner class argument instead.

Preflight observed on 2026-10-19: `adb devices -l` reports a connected device with `product:a16ub model:SM_A165M device:a16`. T036's initial debt-only run is reported by `android-core` as 2 expected behavioral failures of 6 scheduler cases (explicit-zero and permission-denied behavior); the connected XML currently on disk confirms the debt worker class has 3 tests, 0 failures, and 0 errors. Recurrence scheduler/worker tests have not run because those classes were absent during T036; by explicit owner approval they are sequenced in T051 after T040/T041/T042. These initial results are not final HU-44 acceptance evidence.

T051 recurrence evidence (2026-10-19, Samsung Galaxy A16, `SM_A165M`, Android 16; serial `RFGL70J9E2T`): the scheduler filter completed `BUILD SUCCESSFUL`; XML suite `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` at `2026-10-19T19:42:12` reports 3 tests, 0 failures, 0 errors, 0 skipped. The worker filter completed `BUILD SUCCESSFUL`; XML suite `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` at `2026-10-19T19:41:37` reports 3 tests, 0 failures, 0 errors, 0 skipped. Both XMLs were read from `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` immediately after their respective filtered runs; Gradle overwrites that file on the next invocation. Coverage includes default/custom/zero lead, unique work replacement/idempotence, owner-scoped cancellation, stale date/PAUSED/CANCELLED rejection, owner change, and local event persistence when permission is denied. These are focused test results, not full-epic acceptance.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.kipu.app.feature.notifications.data.OfflineFirstNotificationsRepositoryTest" --tests "com.kipu.app.feature.notifications.data.LocalReminderCenterTest" --tests "com.kipu.app.feature.notifications.data.sync.SyncNotificationsWorkerTest"
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.notifications.data.local.ReminderStorageTest --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderWorkerTest --console=plain
# Run the recurrence filters only after T040/T041/T042 and through T051 (approved post-implementation test sequence).
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.RecurrenceRegistrationFlowTest --console=plain
```

Verify the Gradle exit status and `app/build/outputs/androidTest-results/connected/debug/` XML for the intended class names, test count, failures/errors, timestamp, and Samsung device. These filtered runs establish only their listed coverage, not full-epic acceptance.

### Focused Supabase pgTAP

`supabase/config.toml` configures the local database on port 54322. Run only the HU-42 notification privilege/RLS test file; HU-44 adds no Supabase migration or client privilege:

```powershell
pnpm exec supabase test db --local supabase/tests/database/notifications_center_test.sql
```

Expected: pgTAP confirms owner scoping, soft-delete behavior, and that authenticated clients still cannot INSERT or physically DELETE server-owned `app_notifications`. Do not use `--linked`, reset the local database, or alter grants to support local reminder storage.

### HU-44 device acceptance

1. Create an active debt due date and an active recurrence due date; verify the effective default is two days, custom lead values change the schedule, and explicit 0 disables it.
2. Reconcile/retry each identical owner/source/type/date tuple; verify one local schedule/event and at most one Android system notification.
3. Change the due date or record a prórroga; verify the old job and local center event are withdrawn and only the new date remains eligible.
4. Pause/cancel a recurrence, cancel/settle a debt, then run stale work; verify no center or system reminder appears.
5. Deny or revoke `POST_NOTIFICATIONS`; verify the event remains in the HU-42 center on the device. With permission granted, verify the OS notification is only an additional rendering.
6. Switch accounts and verify no reminder event or work from the previous owner is visible or processed; confirm no financial entry/payment is created.

Record the actual targeted commands, model/device, pgTAP output, instrumentation XML totals, and any limitations here after execution. Do not substitute a full-suite run or infer that unexecuted tests passed.

### T045 focused verification evidence (2026-10-19)

Preflight: `adb devices -l` identified the connected Samsung Galaxy A16 (`SM_A165M`, product `a16ub`, device `a16`); `adb shell getprop ro.build.version.release` returned Android `16`. Device serials are intentionally omitted. All connected Gradle invocations below ended `BUILD SUCCESSFUL` and reported `Running tests on devices: SM-A165M - 16`. The instrumentation XML path is overwritten by each filtered invocation; each result was inspected immediately after its run.

JVM commands (each `:app:testDebugUnitTest`, exit 0):

```text
./gradlew :app:testDebugUnitTest --tests 'com.kipu.app.feature.notifications.data.OfflineFirstNotificationsRepositoryTest' --tests 'com.kipu.app.feature.notifications.data.LocalReminderCenterTest' --tests 'com.kipu.app.feature.notifications.data.sync.SyncNotificationsWorkerTest' --console=plain
./gradlew :app:testDebugUnitTest --tests 'com.kipu.app.feature.notifications.data.sync.SyncNotificationsWorkerTest' --console=plain
```

JUnit XML under `app/build/test-results/testDebugUnitTest/`: `OfflineFirstNotificationsRepositoryTest` — 5 tests, 0 failures/errors/skipped, timestamp `2026-10-19T19:49:21.503Z`; `LocalReminderCenterTest` — 3 tests, 0 failures/errors/skipped, timestamp `2026-10-19T19:49:19.024Z`; `SyncNotificationsWorkerTest` — 3 tests, 0 failures/errors/skipped, timestamp `2026-10-19T19:53:45.407Z`. Total focused JVM evidence: 11 tests, 0 failures/errors/skipped. The Sync worker class was also run separately to confirm and inspect its XML.

Connected filtered commands and inspected XML (`app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` after each invocation):

| Class | Gradle result | XML timestamp | Tests | Failures / errors / skipped |
| --- | --- | --- | ---: | --- |
| `com.kipu.app.feature.notifications.data.local.ReminderStorageTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:49:05` | 5 | 0 / 0 / 0 |
| `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:50:19` | 6 | 0 / 0 / 0 |
| `com.kipu.app.feature.debts.data.DebtReminderWorkerTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:50:49` | 3 | 0 / 0 / 0 |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:51:17` | 3 | 0 / 0 / 0 |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:51:42` | 3 | 0 / 0 / 0 |
| `com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:52:12` | 3 | 0 / 0 / 0 |
| `com.kipu.app.feature.recurrences.RecurrenceRegistrationFlowTest` | `BUILD SUCCESSFUL` | `2026-10-19T19:53:10` | 10 | 0 / 0 / 0 |

Each connected command used `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.Class> --console=plain`. Focused connected total: 33 tests, 0 failures/errors/skipped. The first debt scheduler launch encountered an ADB APK uninstall/install error and produced no test XML despite Gradle reporting success; the filtered command was retried, and the retry produced the recorded 6/6 passing XML. This is candidate verification only, not HU-44 acceptance.

T044 pgTAP evidence was reported by tech-lead (not rerun in T045): `pnpm exec supabase test db --local supabase/tests/database/notifications_center_test.sql`, exit 0; 1 file, 17 tests, 0 failures. Full output is attached to task `t_4844da3f` as `hu44-notifications-center-pgtap.log`.

Limitation: T046 returned NO PASS for SEC-044-01 (MEDIUM): owner-scoped tombstoning of already-emitted local events is missing when a source is disabled/deleted/settled or its due date changes. This run did not change production code; a separate remediation gate is required. No global Android suite, legacy debt expansion, or commit was performed.

### T054 post-remediation focused evidence (2026-10-19)

Preflight identified the Samsung Galaxy A16 (`SM_A165M`, Android 16). `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` completed successfully after remediation. Before the green behavior, the two Room reconciliation tests were run against a temporary no-op tombstone implementation: the 2-test XML at `2026-10-19T20:10:29` reported 2 expected failures (old event remained visible and invalid-source event remained visible). Restoring the tombstone writes produced the final passing run below. A first debt-reconciler test execution exposed an invalid fixture expectation (a zero-lead debt cannot retain a sibling installment of that same debt); the fixture was corrected to use a separate active sibling debt and its final rerun passed.

Final filtered connected runs (`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.Class> --console=plain`, each `BUILD SUCCESSFUL`):

| Class | XML timestamp | Tests | Failures / errors / skipped |
| --- | --- | ---: | --- |
| `com.kipu.app.feature.notifications.data.local.ReminderReconciliationTest` | `2026-10-19T20:11:21` | 2 | 0 / 0 / 0 |
| `com.kipu.app.feature.debts.data.DebtReminderReconciliationTest` | `2026-10-19T20:04:51` | 2 | 0 / 0 / 0 |
| `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest` | `2026-10-19T20:02:15` | 6 | 0 / 0 / 0 |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` | `2026-10-19T20:02:52` | 3 | 0 / 0 / 0 |
| `com.kipu.app.feature.notifications.data.local.ReminderStorageTest` (read-only regression; not edited) | `2026-10-19T20:03:21` | 5 | 0 / 0 / 0 |
| `com.kipu.app.feature.debts.data.DebtLocalDataSourceTest` | `2026-10-19T20:08:44` | 6 | 0 / 0 / 0 |

Final focused total: 24 instrumented tests, 0 failures/errors/skipped. XML paths were inspected after each run at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` (Gradle overwrites the file on the next invocation); XML properties identify the attached device. Coverage includes Room tombstones for changed dates and ineligible debt lead, owner/source isolation, preservation of another active debt, current-date debt scheduling, physical local deletion, and repeated cleanup from the idempotent receipt; scheduler regression classes verify replacement/idempotence and owner-scoped cancellation behavior.

Scope/limitations: the Room tests exercise event reconciliation, the local debt reconciler, and local physical deletion; remote feed deletion was verified statically as transactional code but has no dedicated feed integration test in this run. Room center tombstones do not cancel an OS notification already displayed in Android's notification tray; this is a separate presentation concern and is not claimed as resolved here. This is remediation evidence for SEC-044-01 only, not general HU-44 acceptance. No schema/migration, remote notification/outbox/API, permission, UI, or financial logic was changed; no global suite was run.

### T056 lead range regression evidence (2026-10-19)

TDD RED (Samsung Galaxy A16 `SM_A165M`, Android 16; test XML device property `RFGL70J9E2T`): with the legacy reconciler guard `leadDays !in 1..30`, the filtered `DebtReminderReconciliationTest` XML at `2026-10-19T20:22:47` reported 3 tests, 1 failure, 0 errors, 0 skipped. The new Room-backed case for leads 31 and 365 failed behaviorally: expected active events `[event-a, event-b]`, observed `[]` after tombstoning. Existing lead-0 isolation passed.

The minimal fix changed only the debt reconciler eligibility range to `1..365`; zero remains disabled and recurrence validation remains unchanged at `0..30`. GREEN: `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, and the filtered connected `DebtReminderReconciliationTest` completed with `BUILD SUCCESSFUL`. XML at `2026-10-19T20:23:37` identifies `com.kipu.app.feature.debts.data.DebtReminderReconciliationTest`, 3 tests, 0 failures/errors/skipped, device property `RFGL70J9E2T` (Samsung Galaxy A16 `SM_A165M`, Android 16). No dedicated integrated test exists for an incoming `DebtChangeFeedApplier` deletion; that path has only been statically inspected, not dynamically covered. This is a new candidate and requires Security post-fix gate before QA final.

### T057 hash-bound post-fix Security re-audit (2026-10-19)

Security reran `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderReconciliationTest --console=plain`. Result: `BUILD SUCCESSFUL`; XML at `2026-10-19T20:25:44` reports `DebtReminderReconciliationTest` 3 tests, 0 failures/errors/skipped, Samsung SM_A165M / Android 16 (device property `RFGL70J9E2T`). The independent verdict is PASS for SEC-044-01 only, bound to the dirty candidate's per-file SHA-256 manifest in Kanban task `t_a18bb984`; clean HEAD `aebe123d0dcb50b98fe5a3bc8a249f9438ef4c63` alone does not identify the reviewed bytes. Static review verified owner+source tombstone isolation and local/change-feed deletion transactions; no financial or remote notification/outbox writes were attributed to this fix.

Residuals for final HU-44 QA: no dedicated integrated test for incoming `DebtChangeFeedApplier` deletion; a Room tombstone does not cancel a system notification already posted in Android's tray, which must be assessed against FR-016, SC-010, and assumption 182. The global 413-test suite was not run. T057 does not constitute general HU-44 acceptance.

### T059 resumed run — current RED evidence (2026-10-19)

On Samsung Galaxy A16 `SM_A165M`, Android 16 (XML device property `RFGL70J9E2T`), ran `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain`. `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` passed, but connected test task failed. XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T20:43:53`: `DebtReminderSchedulerTest`, 7 tests, 1 failure, 0 errors, 0 skipped; failure at `DebtReminderSchedulerTest.kt:155`, assertion that another installment's notification remains after `cancelDebt(owner, debt)`. The fixture uses the same `debtId` for both target and “sibling”; by contract `cancelDebt` removes every installment of that debt, so this is not valid evidence of cross-source cancellation. No production or test source was changed in this run. This is a red test failure and blocks T059 validation; no GREEN result claimed. `git diff --check`, recurrence filters, build-only verification, and hashes remain pending.

Fixture correction and rerun: changed the “sibling” notification to use a distinct `debtId` (`OTHER_DEBT_ID`) so it is outside `cancelDebt(owner, debt)` scope. The rerun compiled main and androidTest Kotlin successfully but `connectedDebugAndroidTest` still failed. XML overwritten at `2026-10-19T20:45:01`: `DebtReminderSchedulerTest`, 7 tests, 1 failure, 0 errors, 0 skipped, device `RFGL70J9E2T`; assertion at line 157 says the other-debt notification was no longer active. The scheduler source appears to filter active tags by the exact user+debt prefix, so the observed result needs diagnosis before any source edit. This is unresolved RED; no GREEN/candidate validation claimed. Recurrence filters, final build, `git diff --check`, and SHA-256 manifest remain pending.

### T059 active-notification precondition attempt (2026-10-19)

Updated `DebtReminderSchedulerTest.cancelDebtRemovesOnlyItsPostedSystemNotifications` to explicitly grant `POST_NOTIFICATIONS` on API 33+, verify app notifications and the test channel are enabled, capture all three `(tag, id)` notifications in `NotificationManager.activeNotifications` before cancellation, assert selective cancellation, and always clean up in `finally`. The attempt did not reach the cancellation assertions: the precondition assertion `App notifications must be enabled` failed. Command `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain` compiled `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin`, then failed the connected task. XML immediately inspected at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`: timestamp `2026-10-19T20:52:08`, class `DebtReminderSchedulerTest`, 7 tests, 1 failure, 0 errors, 0 skipped; device `RFGL70J9E2T` (SM_A165M, Android 16). Device was reachable through SDK platform-tools ADB 37.0.1; the PATH `C:/Windows/adb.exe` 1.0.40 client was incompatible with server 1.0.41 and could not connect. `pm grant` did not enable app-level notifications (`NotificationManagerCompat.areNotificationsEnabled()` remained false), so the prior apparent sibling-cancel failure still has no valid active-notification precondition and this is NOT a valid behavioral RED. No production changes or GREEN claimed. Need resolve/confirm app-level notification enablement on the test device and rerun this filter before any implementation change. No recurrence filter, final build, diff-check or final hashes have been run for this attempt.

### T058 resumed run — notification platform precondition verified (2026-10-19)

Tech-lead reported installing the debug build and granting only `POST_NOTIFICATIONS` on SM_A165M (`RFGL70J9E2T`). I reran the focused debt scheduler filter using SDK ADB 37.0.1 (the PATH ADB 1.0.40 is incompatible with server 1.0.41): `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain`. Result: `BUILD SUCCESSFUL`; XML immediately inspected at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T21:05:07`, `DebtReminderSchedulerTest` 7 tests, 0 failures/errors/skipped, device `RFGL70J9E2T`. Crucially `cancelDebtRemovesOnlyItsPostedSystemNotifications` passed with the instrumentation's direct preconditions (`areNotificationsEnabled()`, enabled test channel, all three target/sibling/other-owner `(tag,id)` entries active in `NotificationManager.activeNotifications` before cancellation), selectivity checks, and `finally` cleanup. Thus the notification platform gate is now open and basic `cancelDebt` isolation is GREEN; this is not yet a valid RED for replan/obsolete-worker behavior. Further TDD must target stale notification after changed-date scheduling and stale work versus a newer current date before any production change. No production change was made in this filter.

### T058 TDD RED — stale OS notice on due-date replan (2026-10-19)

Added `DebtReminderSchedulerTest.reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification` before production changes. It checks app notification permission and channel, posts a real old-date reminder, asserts its exact `(tag,id)` is present in `NotificationManager.activeNotifications`, then schedules the same source with a later due date and asserts the old system notice is gone; cleanup is in `finally`. Ran the focused debt scheduler filter with the command above: Gradle `BUILD FAILED` due to test failures. XML immediately inspected: timestamp `2026-10-19T21:06:03`, `DebtReminderSchedulerTest` 8 tests, 2 failures, 0 errors/skipped, device `RFGL70J9E2T`. The new scenario reached the active-notification precondition then RED at line 162: replan left the old posted notice active. The pre-existing `cancelDebtRemovesOnlyItsPostedSystemNotifications` also failed its app-enabled precondition at line 188 in this same invocation (test ordering/device notification state is inconsistent across invocations); this is a platform-precondition failure, not the behavioral RED. No production code was changed after this RED. Before claiming GREEN, stabilize/verify app notification state and rerun all focused tests; investigate whether failed test changes app state, while preserving direct active-notification assertions.

### T058 focused retry after deterministic permission grant (2026-10-19)

Updated both debt notification instrumented tests so the API 33+ `pm grant ... POST_NOTIFICATIONS` shell command is drained to EOF via `ParcelFileDescriptor.AutoCloseInputStream` before checking `areNotificationsEnabled()`; this addresses the previously identified unsynchronized shell-command race without changing app/device settings outside the test. No production code was changed.

Isolation filter command: `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest#cancelDebtRemovesOnlyItsPostedSystemNotifications --console=plain`. Result: `BUILD SUCCESSFUL`; XML immediately inspected at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T21:09:08`, 1 test, 0 failures/errors/skipped, device property `RFGL70J9E2T`. This case proved all three exact `(tag,id)` entries active before cancellation and passed target-only cancellation with `finally` cleanup.

Replan RED filter command: same environment and task, class argument `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest#reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification`. Gradle returned `BUILD FAILED`; XML immediately inspected at the same path, timestamp `2026-10-19T21:09:32`, 1 test, 1 failure, 0 errors/skipped, same device. Failure is at `DebtReminderSchedulerTest.kt:157`, `Old reminder must be active before replan`: this invocation did not establish that the posted `(tag,id)` was active, so it is not a valid behavioral RED and does not justify a production fix. The app/channel preflight had passed; the notification itself was not present in `NotificationManager.activeNotifications`. Stop before source implementation or further filters until this platform posting discrepancy is resolved. Candidate remains unvalidated; recurrence filters, final build, `git diff --check`, and final hashes are not claimed.

T059 polling precondition and current candidate evidence (2026-10-19, Samsung Galaxy A16 `SM_A165M`, Android 16; device property `RFGL70J9E2T`): the test-only `awaitActiveNotification(tag, id, timeoutMillis=3000)` polls `NotificationManager.activeNotifications` every 50 ms and reports package, runtime permission, app notification state, channel importance, and active `(tag,id)` pairs on timeout. A run of the isolated replan filter before production edits was reported by the superseded worker at XML `2026-10-19T21:14:52`: 1 test, 1 failure, 0 errors/skipped; the exact notice was active before `schedule()`, and the old notice remained after the changed-date scheduling call. This is a valid behavioral RED. On this run, a temporary implementation that cancelled the exact debt notification tag on reschedule was exercised; that partial implementation was reverted after the filter exposed an unstable unrelated active-notification fixture. The full `DebtReminderSchedulerTest` filter completed compilation but Gradle returned `BUILD FAILED`; XML immediately inspected at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T21:16:57`, reports 8 tests, 1 failure, 0 errors/skipped. `reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification` passed, but `cancelDebtRemovesOnlyItsPostedSystemNotifications` failed its fixture active-notification precondition at line 200: expected the other-owner notice was not active before `cancelDebt()`. This candidate is NOT GREEN; no recurrence scheduler/worker filter was run after the temporary change, and no production implementation is retained. Continue only after stabilizing active-notification setup and implement/verify serialized authoritative revalidation plus due-date metadata guard. No final diff-check or hashes claimed.

### T059 continuation focused results (2026-10-19)

The isolation test now awaits each exact target/sibling/other-owner `(tag,id)` through the bounded active-notification polling helper before snapshot/assertion; it retains permission/channel prechecks and `finally` cleanup. On Samsung Galaxy A16 `SM_A165M`, Android 16 (XML device `RFGL70J9E2T`), the isolated `cancelDebtRemovesOnlyItsPostedSystemNotifications` filter completed `BUILD SUCCESSFUL`; XML inspected immediately at the connected results path timestamp `2026-10-19T21:22:52`: 1 test, 0 failures/errors/skipped. This validates the active-notice precondition and target-only cancellation oracle.

The previously recorded valid pre-implementation RED remains the isolated replan result at `2026-10-19T21:14:52` (exact notice active before scheduler call; old notice remained after due-date change). Added exact-tag cancellation of already-published debt notices during valid rescheduling, and exact recurrence notice cancellation on recurrence reschedule/ineligibility cancellation. The replan filter then completed `BUILD SUCCESSFUL`; XML immediately inspected timestamp `2026-10-19T21:23:51`: 1 test, 0 failures/errors/skipped, device `RFGL70J9E2T`.

Focused required class runs, each with `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.Class> --console=plain`, all reported `BUILD SUCCESSFUL` and the XML was inspected immediately before the next invocation:

| Class | XML timestamp | Tests | Failures / errors / skipped | Device |
| --- | --- | ---: | --- | --- |
| `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest` | `2026-10-19T21:25:05` | 8 | 0 / 0 / 0 | `RFGL70J9E2T` |
| `com.kipu.app.feature.debts.data.DebtReminderWorkerTest` | `2026-10-19T21:25:27` | 3 | 0 / 0 / 0 | `RFGL70J9E2T` |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` | `2026-10-19T21:25:46` | 3 | 0 / 0 / 0 | `RFGL70J9E2T` |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` | `2026-10-19T21:26:08` | 3 | 0 / 0 / 0 | `RFGL70J9E2T` |

The first debt scheduler run after changing the recurrence scheduler constructor did not compile because its instrumentation fixture still used the old positional constructor; the fixture was updated to pass its existing application `Context`, and all listed subsequent runs compiled and passed. This focused evidence does not establish serialization against concurrent stale worker publication or a due-date metadata guard; those race protections remain unverified and must not be inferred from these filters. No full Android suite, Security gate, or HU-44/T047 acceptance is claimed.

### T059 exact replan oracle rerun (2026-10-19)

Command executed (SDK ADB 37.0.1 platform-tools prepended to `PATH` to avoid the incompatible PATH ADB 1.0.40 client):

```sh
export ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk'
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest#reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification --console=plain
```

Gradle returned `BUILD SUCCESSFUL` and reported `Running tests on devices: SM-A165M - 16`. The shared XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` was inspected immediately at timestamp `2026-10-19T21:28:44`: `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest`, the requested `reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification` case, 1 test, 0 failures, 0 errors, 0 skipped; device property `RFGL70J9E2T` (Samsung Galaxy A16 `SM_A165M`, Android 16). The bounded polling precondition observed the exact active `(tag,id)` before scheduling; this run's replan assertion confirms the old notice was withdrawn. This is a passing post-change rerun, not a RED. The valid pre-change behavioral RED at `2026-10-19T21:14:52` remains recorded above. `git diff --check` completed with exit 0 after this run. This focused run does not verify the separate worker-vs-replan serialization/date-metadata race requirements; do not infer full T058/T059 or T047 acceptance from it.

### T058 implementation verification (2026-10-19)

Implemented the app-scoped `ReminderReconciliationCoordinator` (`Mutex`) shared by debt and recurrence reconciliation/workers; worker critical sections include authoritative DAO checks, local event/schedule persistence and the final notification post. Reconciliation no longer bulk-cancels eligible debt notices; scheduling only withdraws an exact active `(tag,id)` when its canonical ISO due-date metadata is present and proves stale. Missing metadata is treated as unknown and preserved. Recurrence pause/cancel/absence cancels only its exact owner/source notice. Notification permission denial continues to retain local reminder state without failing.

Verification on Samsung Galaxy A16 `SM_A165M`, Android 16 (`RFGL70J9E2T`):

- `:app:compileDebugKotlin`: `BUILD SUCCESSFUL`.
- `:app:testDebugUnitTest`: `BUILD SUCCESSFUL`.
- The changed-date debt notice oracle passed on the second run after correcting its fixture to attach the old notice's due-date metadata; the first post-change attempt failed because it used an untagged legacy notice, correctly treated as unknown by the new guard. Passing exact filter: `DebtReminderSchedulerTest#reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification`, `BUILD SUCCESSFUL`.
- `DebtReminderReconciliationTest`: 3 tests, 0 failures/errors/skips; XML timestamp `2026-10-19T21:43:20`.
- `RecurrenceReminderWorkerTest`: 3 tests, 0 failures/errors/skips; XML timestamp `2026-10-19T21:43:40`.
- `RecurrenceReminderSchedulerTest`: 3 tests, 0 failures/errors/skips; XML timestamp `2026-10-19T21:43:59`.
- `ReminderReconciliationCoordinatorTest`: 1 deterministic barrier/deferred serialization test, 0 failures/errors/skips; XML timestamp `2026-10-19T21:44:19`.
- `git diff --check`: exit 0.

The coordinator test verifies suspend-safe mutual exclusion in one shared instance, not a full worker-versus-reconciler race integration test. The mutex is process-local and does not close the known interval between a Room mutation and its later reconciliation; no T047/HU-44 pass is claimed. The initial full `DebtReminderSchedulerTest` invocation failed only the old-notice assertion because that fixture omitted due-date metadata; it was updated to represent valid metadata for the intended stale-date assertion and the isolated oracle then passed. Final complete `DebtReminderSchedulerTest` class rerun: 8 tests, 0 failures/errors/skips, XML timestamp `2026-10-19T21:44:51`; this included `cancelDebtRemovesOnlyItsPostedSystemNotifications` and the due-date replan case. Final `git diff --check` exit 0.

SHA-256 manifest (computed after code/test edits):

- Coordinator: `73f5fba12a91e8d9900aaded25dd9d82d385efc248df2ccf8a41d145706e1c66` (`ReminderReconciliationCoordinator.kt`).
- Debt scheduler: `a051c89642f0149e603256e3f991dcc234bc023cca36fa32cb0cc733cf5d76ed`.
- Debt worker: `67ef523dec5c63c0808ff4f4f4de19d4c7bf8593e596052e46e7ace16b4aac45`.
- Recurrence scheduler: `09c92ec8f190ec10d58f8fd5869d04d00d5a4e24bab848923dde5288168055a8`.
- Recurrence worker: `e7c1172489c3966ce1784ed00b166d954349d20dcfd2a964502682125ff5f45f`.
- Recurrence repository: `64b7de9115edf6e679c61de6b37cc1adf3558e531d3311f8578e7db1bffa0d2e`.
- Coordinator test: `6b9ca5e2b08289adddad9299c7771e657f06a041ac62c094680ce4e6e4929381`.
- Debt scheduler test: `e7f0bdf28291703a6fe1c5de56ffe3a318e63b862c538aa02c09efacad1b0cb5`.
- Debt worker test: `c4429c1769db9e6cf4bea81aa2b14dbdeb785119782e2c5e47dcaf7f7615ea2f`.
- Debt reconciliation test: `ff72bb190ea8c5445cfa0a81e75a54415fbaefc01079721842ed182f0b96c1db`.
- Recurrence worker test: `779424560efaf52ec3812465e5f94410cc2903e0a947fc20172fda8834a0fd1e`.
- Recurrence scheduler test: `295ae2e00be30b7805a515b9e817c08848f95af9232c586b8b67693135651553`.

### T059 QA auditor rerun (2026-10-19)

Build/compile command: `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain`. Result: `BUILD SUCCESSFUL`; both compilation tasks were up-to-date. Connected tests used SDK ADB 37.0.1 (PATH `C:/Windows/adb.exe` 1.0.40 could not communicate with the 1.0.41 server), attached device Samsung Galaxy A16 `SM_A165M`, Android 16, XML device property `RFGL70J9E2T`. Each exact class-filtered command below completed with `BUILD SUCCESSFUL`; immediately after each invocation the shared XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` was inspected before launching the next filter:

```sh
ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain
ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderWorkerTest --console=plain
ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain
ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest --console=plain
```

| Class | XML timestamp (2026-10-19) | Tests | Failures / errors / skipped | Device |
| --- | --- | ---: | --- | --- |
| `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest` | `21:47:05` | 8 | 0 / 0 / 0 | `RFGL70J9E2T` |
| `com.kipu.app.feature.debts.data.DebtReminderWorkerTest` | `21:47:19` | 3 | 0 / 0 / 0 | `RFGL70J9E2T` |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` | `21:47:32` | 3 | 0 / 0 / 0 | `RFGL70J9E2T` |
| `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` | `21:47:46` | 3 | 0 / 0 / 0 | `RFGL70J9E2T` |

Focused total: 17 instrumented tests, 0 failures/errors/skipped. Final `git diff --check` completed with exit 0 after recording this evidence. SHA-256 hashes for the implementation/test paths are unchanged from the immediately preceding T058 manifest above; the quickstart was extended in this T059 auditor run. These results verify compilation and these four filters only; they do not establish the full Android suite, worker/reconciler end-to-end race coverage, Security gate, or HU-44/T047 completion.

### T061 canonical due-date metadata guard (2026-10-19)

Changed both scheduler guards to parse `KEY_DUE_DATE` as `LocalDate` and accept it only when `parsed.toString()` round-trips exactly to the original string. Only a canonical, valid date different from the requested date cancels the exact active `(tag,id)`; absent, malformed, noncanonical, or matching metadata is preserved. Added direct `NotificationManager.activeNotifications` instrumentation coverage for debt metadata-preservation cases and recurrence preservation/canonical-stale cancellation plus other-owner isolation. No Room/schema, coordinator, worker, financial, API, or permission production flow was changed.

Compilation on Samsung Galaxy A16 `SM_A165M`, Android 16: `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain` — `BUILD SUCCESSFUL` (2026-10-19).

Connected preflight used SDK platform-tools ADB 37.0.1 because PATH ADB 1.0.40 is incompatible with the running server; `adb devices -l` identified `RFGL70J9E2T`, `SM_A165M`, and `adb shell getprop ro.build.version.release` returned `16`. Attempted the targeted filter `com.kipu.app.feature.debts.data.DebtReminderSchedulerTest#replanPreservesNoticeWhenDueDateMetadataIsUnknownOrCurrent`. The Gradle connected task failed at the test's required precondition `NotificationManagerCompat.areNotificationsEnabled() == true`, before posting a notice or asserting scheduler behavior. XML immediately inspected at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`: timestamp `2026-10-19T21:51:20`, 1 test, 1 precondition failure, 0 errors/skipped, device property `RFGL70J9E2T`. Per scope, no ADB/settings/permission changes were made and no further connected test was run; recurrence active-notification regression therefore remains unexecuted. This is a platform precondition limitation, NOT a behavioral pass/fail result.

`git diff --check` completed with exit 0 after the edits. SHA-256 (computed after source/test/doc changes; quickstart hash is reported in the task handoff because recording its own hash here would change it): debt scheduler `5ba22b3d847f624430d0e8466fa2eba634a32afb4c01b145a74be9c4bd923a1a`; recurrence scheduler `515539f9e985b0ed7574a4a00617c040ba842809ca5d31eedef6ac4e75a69df4`; debt scheduler test `20ee8ac73ae7119b568a3f8aa1a9a80cbd469c4d44b3ed9621c399b8544adf59`; recurrence scheduler test `eb1c857fe226f8629910b6189ac7b1206b18bbd26c8308bab453c2a2ac1c3d1a`. Connected runtime coverage remains blocked on notification permission precondition; no global suite, legacy debt suite, commit, push, PR, T047, Security, or HU-44 acceptance is claimed.

### T061 resumed verification after platform setup (2026-10-19)

Tech-lead reported installing only the debug `com.kipu.app` APK and granting `POST_NOTIFICATIONS` on Samsung Galaxy A16 `SM_A165M`, Android 16 (`RFGL70J9E2T`); package permission/app-op readback was reported successful. Used SDK ADB environment (`ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk'`; SDK platform-tools client 37.0.1) and ran only the focused scheduler filters below. Gradle's connected task compiles both main and androidTest Kotlin; compilation tasks were `UP-TO-DATE` on these runs.

Debt scheduler class command: `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain`. An initial class invocation returned `BUILD FAILED`; its immediately inspected shared XML at `2026-10-19T22:19:13` contained 9 tests, 1 failure, 0 errors/skipped. Only `cancelDebtRemovesOnlyItsPostedSystemNotifications` failed the target-cancellation assertion; the isolated exact filter then passed (XML `2026-10-19T22:19:47`, 1/1, 0 failures/errors/skipped). Reran the entire debt scheduler class with the same command: `BUILD SUCCESSFUL`; XML immediately read at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T22:20:31`, 9 tests, 0 failures/errors/skipped, device property `RFGL70J9E2T`. This passing run covers real active `NotificationManager` notices for canonical old-date cancellation, current/absent/malformed/noncanonical metadata preservation, and owner/debt notification isolation. No mocked `activeNotifications` oracle was used.

Recurrence scheduler command: `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain`. Gradle returned `BUILD FAILED`; XML immediately inspected at the same shared path, timestamp `2026-10-19T22:20:07`, reports 4 tests, 1 failure, 0 errors/skipped, device `RFGL70J9E2T`. The new NotificationManager regression stopped at its explicit `NotificationManagerCompat.areNotificationsEnabled()` precondition (line 84), before posting notices or exercising scheduler behavior; the other 3 recurrence scheduler tests passed. Per T061 stop condition, did not change ADB/settings/permissions or retry the recurrence filter. Recurrence NotificationManager behavioral verification therefore remains incomplete; the failing XML is a precondition failure, not evidence for or against the due-date guard.

`git diff --check` returned exit 0 before this evidence update. Source/test SHA-256 values match those listed above; final `quickstart.md` SHA-256 is reported in the task handoff. No global test suite, debt legacy suite, device setting changes from this run, commit/push/PR, or T047/Security/HU-44 acceptance is claimed.

Recurrence retry after the debt class passed: ran `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain` on Samsung Galaxy A16 `SM_A165M`, Android 16, device property `RFGL70J9E2T`. Gradle returned `BUILD FAILED`; the shared XML was read immediately at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T22:22:40`: 4 tests, 1 failure, 0 errors, 0 skipped. The regression test failed at its explicit `NotificationManagerCompat.areNotificationsEnabled()` precondition (line 84), before any test notices were posted or scheduler behavior was asserted; the other 3 recurrence scheduler tests passed. Per T061 stop condition, no further device/ADB/settings/permission changes or connected retries were made. Recurrence NotificationManager behavior remains unverified; this is not a behavioral failure/pass. No full/global suite or legacy debt suite was run. `git diff --check` was run before this evidence note and returned exit 0; hashes for code/tests are unchanged from the manifest above, and this update changes the quickstart hash.

### Focused revalidation attempt after T061 metadata regressions (2026-10-19)

After the debt/recurrence real-`NotificationManager` regression cases were added, performed read-only platform preflight on Samsung Galaxy A16 `SM_A165M`, Android 16 (`RFGL70J9E2T`) using SDK platform-tools ADB 37.0.1. `adb devices -l` reported the device online; `adb shell getprop ro.build.version.release` returned `16`. `adb shell dumpsys package com.kipu.app` and `adb shell appops get com.kipu.app POST_NOTIFICATION` returned `Error: No UID for com.kipu.app in user 0`; the package was absent, so its runtime permission/app notification state and the `hu44-test-notifications` channel could not satisfy the tests' required precondition. No package install, permission, settings, ADB, or other device state was changed.

Per the stop condition, did not compile or execute another Gradle test filter. No fresh XML was produced by this attempt. The existing shared XML was read (not generated by this attempt): `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`, timestamp `2026-10-19T22:22:40`, class `RecurrenceReminderSchedulerTest`, 4 tests, 1 failure, 0 errors/skipped, device `RFGL70J9E2T`; the failure was `areNotificationsEnabled()` before posting a notice or exercising scheduler behavior. Thus there is no new behavioral evidence for the newly added regressions; recurrence remains unverified. No global 413-test suite, legacy debt suite, commit, push, PR, T047, Security, or HU-44 acceptance is claimed.

Current candidate hashes (SHA-256; measured for this handoff): debt scheduler `5ba22b3d847f624430d0e8466fa2eba634a32afb4c01b145a74be9c4bd923a1a`; recurrence scheduler `515539f9e985b0ed7574a4a00617c040ba842809ca5d31eedef6ac4e75a69df4`; debt scheduler test `36079d13387fd75f2fd97b128435a7c74c3c0558af32dd3b504a9fc6417137ea`; recurrence scheduler test `3605a733697770e9d5a974f9225380d7863c46dfd67b6fc1b57eb4eb09070c2c`; debt worker test `c4429c1769db9e6cf4bea81aa2b14dbdeb785119782e2c5e47dcaf7f7615ea2f`; recurrence worker test `779424560efaf52ec3812465e5f94410cc2903e0a947fc20172fda8834a0fd1e`. `git diff --check` after this evidence update: exit 0.

### T061 validation handoff attempt (2026-10-19)

The preceding platform restoration was reported by tech-lead, but this run could not verify the connected-device precondition or run Gradle: the shell resolves `adb` to `C:/Windows/adb.exe` (client 1.0.40 per prior recorded inspection), while the connected ADB server was reported as 1.0.41. A read-only `adb devices -l` attempt emitted `adb server version (41) doesn't match this client (40); killing...`, then failed to connect (`could not read ok from ADB Server`). No further ADB/device command, Gradle compile, or test filter was attempted, to avoid changing device/ADB state or misrepresenting device readiness. No new XML was produced or inspected; the latest previously recorded shared XML remains the recurrence scheduler precondition failure at `2026-10-19T22:22:40` described above. Connected device state and permission/channel preconditions remain unverified in this run; no behavioral test result is claimed. The only identified obstacle is the incompatible ADB client/server path; use the already installed SDK platform-tools ADB in a separately authorized setup/retry, then verify permission and channel read-only before the combined focused scheduler run. Do not run the global suite, legacy debt tests, or claim T047/HU-44/Security acceptance.

### T061 focused validation retry (2026-10-19)

Read-only preflight on Samsung Galaxy A16 `SM_A165M`, Android 16 (`RFGL70J9E2T`) used the absolute SDK ADB path `C:/Users/Alume/AppData/Local/Android/Sdk/platform-tools/adb.exe` (1.0.41 / 37.0.1); device was online. Before Gradle, package permission readback reported `POST_NOTIFICATIONS: granted=true` and `appops get ... POST_NOTIFICATION` reported `No operations; Default mode: allow`. No ADB daemon, permission, setting, or package state was changed. The test classes create their `hu44-test-notifications` channel and assert it is enabled; no channel state was modified by preflight.

Compilation command `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain` completed `BUILD SUCCESSFUL` (2026-10-19; compilation tasks passed/up-to-date).

Attempted to combine debt and recurrence scheduler classes in one instrumentation invocation with `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest,com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest' --console=plain`. Gradle returned `BUILD SUCCESSFUL`, but the immediately inspected shared XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` at `2026-10-19T22:33:46` shows that the runner selected only `DebtReminderSchedulerTest`: 9 tests, 0 failures/errors/skipped, device property `RFGL70J9E2T`. Do not count recurrence tests as included in that combined invocation.

Then ran the recurrence class separately: `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain`. Gradle returned `BUILD FAILED`; immediately inspected the same shared XML at `2026-10-19T22:34:11`: `RecurrenceReminderSchedulerTest`, 4 tests, 1 failure, 0 errors/skipped, device `RFGL70J9E2T`. The new `replanPreservesUnknownOrCurrentMetadataAndCancelsOnlyCanonicalOldDate` regression failed its explicit `NotificationManagerCompat.areNotificationsEnabled()` precondition at line 87 before posting notices or asserting scheduler behavior; the other three recurrence scheduler tests passed. A subsequent read-only SDK ADB check returned `No UID for com.kipu.app in user 0` from package/appops queries, despite the earlier permission grant readback. The runtime app-notifications precondition is therefore not satisfied; this is not a behavioral pass/fail for the new regression. Per stop condition, no further connected run, install, permission, setting, or ADB change was made. Recurrence `NotificationManager` regression coverage and worker filters remain unverified.

`git diff --check` completed with exit 0 after this evidence was recorded. Current SHA-256: coordinator `73f5fba12a91e8d9900aaded25dd9d82d385efc248df2ccf8a41d145706e1c66`; debt scheduler `5ba22b3d847f624430d0e8466fa2eba634a32afb4c01b145a74be9c4bd923a1a`; debt worker `67ef523dec5c63c0808ff4f4f4de19d4c7bf8593e596052e46e7ace16b4aac45`; recurrence scheduler `515539f9e985b0ed7574a4a00617c040ba842809ca5d31eedef6ac4e75a69df4`; recurrence worker `e7c1172489c3966ce1784ed00b166d954349d20dcfd2a964502682125ff5f45`; debt scheduler test `36079d13387fd75f2fd97b128435a7c74c3c0558af32dd3b504a9fc6417137ea`; debt worker test `c4429c1769db9e6cf4bea81aa2b14dbdeb785119782e2c5e47dcaf7f7615ea2f`; recurrence scheduler test `3605a733697770e9d5a974f9225380d7863c46dfd67b6fc1b57eb4eb09070c2c`; recurrence worker test `779424560efaf52ec3812465e5f94410cc2903e0a947fc20172fda8834a0fd1e`. The quickstart's own SHA-256 is included in the task handoff. No global 413-test suite, legacy debt tests, commit, push, PR, T047, Security, or HU-44 acceptance is claimed.

### T061 focused recurrence scheduler rerun after device restoration (2026-10-19)

Read-only preflight on Samsung Galaxy A16 (`SM_A165M`, Android 16, serial `RFGL70J9E2T`) used SDK platform-tools ADB 37.0.1 at `C:/Users/Alume/AppData/Local/Android/Sdk/platform-tools/adb.exe`. The device was online; `com.kipu.app` was installed, `POST_NOTIFICATIONS` reported `granted=true`, and `appops get com.kipu.app POST_NOTIFICATION` reported `No operations; Default mode: allow`. `dumpsys notification` reported app importance `DEFAULT`. The `hu44-test-notifications` channel did not exist before instrumentation; the test creates it and asserts that its importance is not `IMPORTANCE_NONE`, without changing system settings. No ADB daemon, permission, app setting, or device state was changed by preflight.

Ran only the recurrence scheduler class (no global or legacy suite):

```sh
export ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk'
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain
```

Gradle returned `BUILD SUCCESSFUL` (13 seconds); `compileDebugKotlin` and `compileDebugAndroidTestKotlin` were `UP-TO-DATE`. Immediately inspected shared XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`: timestamp `2026-10-19T22:40:07`, class `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest`, 4 tests, 0 failures, 0 errors, 0 skipped, device property `RFGL70J9E2T`. This includes the real `NotificationManager.activeNotifications` regression `replanPreservesUnknownOrCurrentMetadataAndCancelsOnlyCanonicalOldDate`; its notification permission and test-channel preconditions passed, and the scheduler assertions passed. This closes the recurrence scheduler precondition limitation recorded above for this run only; it does not establish worker behavior or broader HU-44 acceptance.

No source or test files were changed in this validation run. Final `git diff --check` and SHA-256 manifests for schedulers, tests, and this documentation update are reported in the task handoff. No global 413-test suite, legacy debt tests, commit, push, PR, T047, Security, or HU-44 acceptance is claimed.

### T061 final focused verification and hash manifest (2026-10-19)

Commands executed on Samsung Galaxy A16 `SM_A165M`, Android 16 (device/XML property `RFGL70J9E2T`), using `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk'` and `ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk'`:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest --console=plain
git diff --check
sha256sum app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderScheduler.kt app/src/main/java/com/kipu/app/feature/recurrences/data/sync/RecurrenceReminderScheduler.kt app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderSchedulerTest.kt app/src/androidTest/java/com/kipu/app/feature/recurrences/data/RecurrenceReminderSchedulerTest.kt specs/010-ep-not-notificaciones-avisos/quickstart.md
```

The debt scheduler class returned `BUILD SUCCESSFUL`; XML `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml` was inspected immediately after that run at `2026-10-19T22:33:46`: 9 tests, 0 failures/errors/skipped. The recurrence scheduler class returned `BUILD SUCCESSFUL`; its XML was inspected immediately at `2026-10-19T22:40:07`: 4 tests, 0 failures/errors/skipped. Both results are on the device above. The instrumented regression tests use the real `NotificationManager.activeNotifications`, verify target notices active before replanning, cover current/absent/invalid/noncanonical and canonical-old date metadata, and validate cancellation isolation; cleanup uses `finally`. `git diff --check` returned exit 0.

Current SHA-256 manifest:

- `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderScheduler.kt`: `5ba22b3d847f624430d0e8466fa2eba634a32afb4c01b145a74be9c4bd923a1a`
- `app/src/main/java/com/kipu/app/feature/recurrences/data/sync/RecurrenceReminderScheduler.kt`: `515539f9e985b0ed7574a4a00617c040ba842809ca5d31eedef6ac4e75a69df4`
- `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderSchedulerTest.kt`: `36079d13387fd75f2fd97b128435a7c74c3c0558af32dd3b504a9fc6417137ea`
- `app/src/androidTest/java/com/kipu/app/feature/recurrences/data/RecurrenceReminderSchedulerTest.kt`: `3605a733697770e9d5a974f9225380d7863c46dfd67b6fc1b57eb4eb09070c2c`
- SHA-256 de este `quickstart.md`: se reporta en el handoff para evitar una referencia autorreflexiva dentro del propio archivo.

Only the two scheduler classes were run for this focused task. No global 413-test suite, debt legacy suite, commit, push, or PR was run/performed; T047, Security audit, and HU-44 acceptance remain independent gates.

### T059 canonical continuation run — current environment stop (2026-10-19)

Compiled main and instrumented Kotlin with `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain`; Gradle returned `BUILD SUCCESSFUL` (all 33 tasks up-to-date). Then ran the required focused debt scheduler class on Samsung Galaxy A16 `SM_A165M`, Android 16 (device property `RFGL70J9E2T`) using `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ANDROID_SDK_ROOT='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.data.DebtReminderSchedulerTest --console=plain`. Gradle returned `BUILD FAILED`. Immediately inspected `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`: timestamp `2026-10-19T22:44:16`, class `DebtReminderSchedulerTest`, 9 tests, 3 failures, 0 errors, 0 skipped, device `RFGL70J9E2T`. All failures were explicit `App notifications must be enabled` preconditions in the platform notification cases (replan withdrawal, unknown/current metadata preservation, and installment cancellation), before their notice assertions; the other 6 tests passed. This is an invalid platform precondition, not behavioral evidence against the scheduler. Per stop condition, did not run further debt/recurrence filters, alter ADB/settings/permissions, or modify production/test assertions. `git diff --check` was run after recording this entry and returned exit 0. This continuation's worker/recurrence filter requirements therefore remain unverified; previous passing results recorded above are historical and do not override this current failed filter. No T047, Security, or HU-44 acceptance is claimed.

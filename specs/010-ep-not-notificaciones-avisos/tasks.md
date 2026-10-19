# Tasks: EP-NOT — Centro de Notificaciones y Avisos (HU-42 + Sprint 6 HU-44)

**Input**: Design documents in `/specs/010-ep-not-notificaciones-avisos/`.
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, and `contracts/notifications-rest.md`.
**Tests**: Included because HU-42, its plan, and the user prompt explicitly require JVM, Room, Compose, and Supabase verification. Write tests before the implementation they cover.

**Propagated**: 2026-10-19 — Updated from spec.md refinement for Sprint 6 HU-44.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files and no dependency on an unfinished task.
- **[Story]**: Maps to a user story from `spec.md`; setup and foundational tasks have no story label.
- Every task names concrete file paths.

## Phase 1: Setup — Boundary Tests

**Purpose**: Establish failing database and persistence boundary tests before implementation.

- [X] T001 [P] Add pgTAP coverage for owner SELECT/UPDATE, cross-user isolation, anon denial, column privileges, soft-delete column/index, and physical-delete denial (FR-007, FR-009) in `supabase/tests/database/notifications_center_test.sql`.
- [X] T002 [P] Add Room migration 11→12 and account-scoped DAO schema tests (FR-009, FR-010) in `app/src/androidTest/java/com/kipu/app/feature/notifications/data/local/NotificationsDatabaseTest.kt`.

---

## Phase 2: Foundational — Shared Data and Domain

**Purpose**: Build the account-scoped storage, migration, domain types, and remote boundary required by all stories.

- [X] T003 Add a forward-only Supabase migration for nullable `deleted_at`, active/unread ordering index, and least-privilege authenticated column UPDATE grants (FR-007, FR-008, FR-009) in `supabase/migrations/20260928000000_ep_not_notification_center_hu42.sql`.
- [X] T004 [P] Add `AppNotificationEntity.kt` (including `NotificationSyncOutboxEntity`) and `AppNotificationDao.kt` (including `NotificationSyncOutboxDao`) with account-scoped queries (FR-001, FR-006–FR-010) in `app/src/main/java/com/kipu/app/feature/notifications/data/local/`.
- [X] T005 Register both notification entities and DAO accessors in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`; add `MIGRATION_11_12` in `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`; register it in `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`.
- [X] T006 [P] Add type/reference normalization and category unit tests for canonical values, the historic credit alias, case variants, and unknown values (FR-002, FR-004) in `app/src/test/java/com/kipu/app/feature/notifications/domain/NotificationTypeTest.kt`.
- [X] T007 Implement `Notification.kt`, `NotificationsRepository.kt`, and `NotificationType.kt` with alert/reminder classification and action results (FR-001, FR-002, FR-004–FR-010) in `app/src/main/java/com/kipu/app/feature/notifications/domain/`.
- [X] T008 [P] Add authenticated PostgREST DTO/API contract tests for owner filters, ordering, allowed PATCH fields, failure mapping, and legacy payload decoding in `app/src/test/java/com/kipu/app/feature/notifications/data/remote/NotificationsApiTest.kt`.
- [X] T009 Implement authenticated GET/PATCH API, DTOs, and tolerant unknown-field decoding in `app/src/main/java/com/kipu/app/feature/notifications/data/remote/`.
- [X] T010 Bind repository and provide feature DAOs/API in `app/src/main/java/com/kipu/app/feature/notifications/di/NotificationsModule.kt`.

**Checkpoint**: Supabase/Room boundaries, migrations, account-scoped domain types, and the API contract are ready.

---

## Phase 3: User Story 1 — Consultar alertas y recordatorios (Priority: P1, MVP)

**Goal**: Show the active account's latest notices, distinguish alerts from future reminders, and filter the list.

**Independent Test**: Seed account-owned Room/remote notices, open PNOT, verify newest-first alert/reminder presentation and all three filters, including the exact empty-state copy.

### Tests for User Story 1

- [X] T011 [P] [US1] Add repository tests for cache-first reads, remote refresh, pending-command merge protection, tombstone exclusion, and user scoping (FR-001, FR-009, FR-010, SC-004, SC-005) in `app/src/test/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepositoryTest.kt`.
- [X] T012 [P] [US1] Add ViewModel tests for newest-first order, Todos/Alertas/Recordatorios filters, empty state, and account switch (FR-001–FR-003, FR-009, SC-004) in `app/src/test/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModelTest.kt`.
- [X] T013 [P] [US1] Add Compose tests for alert/reminder distinction, all filters, structured `event_payload.due_date` plus body fallback, exact empty copy, and local first-render within 2 seconds (FR-001–FR-003, SC-001) in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreenTest.kt`; test dashboard bell routing to PNOT and never P19 in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationNavigationTest.kt` (FR-013).

### Implementation for User Story 1

- [X] T014 [US1] Implement local-first repository refresh/upsert while preserving outbox-pending values (FR-001, FR-009, FR-010) in `app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt`.
- [X] T015 [US1] Implement cache observation, refresh, category filters, and error-safe UI state (FR-001–FR-003, FR-009) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModel.kt`.
- [X] T016 [US1] Build the notification center list, category chips, semantic alert/reminder rows, structured expected-date text with body fallback, loading and exact empty state using Kipu tokens (FR-001–FR-003, FR-011, FR-012) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt` and `NotificationDueDate.kt`.
- [X] T017 [US1] Add the PNOT route and authenticated center destination (FR-013) in `app/src/main/java/com/kipu/app/navigation/NotificationsNavigation.kt` and `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`.

**Checkpoint**: PNOT reads the active owner's local snapshot and displays/filter notices without requiring the later mutation or badge story.

---

## Phase 4: User Story 2 — Abrir el objeto relacionado con seguridad (Priority: P1)

**Goal**: Open the exact registered card destination and handle missing or unsupported targets without crashing or leaving PNOT unexpectedly.

**Independent Test**: Resolve an existing owner-scoped card reference to card detail; resolve a missing card or unregistered budget/debt/goal reference to a friendly message while keeping the center visible and notice state unchanged.

### Tests for User Story 2

- [X] T018 [P] [US2] Add destination resolver tests for card route, deleted card, unknown type, unregistered budget/debt/goal route, and case normalization (FR-004, FR-005, SC-003) in `app/src/test/java/com/kipu/app/feature/notifications/domain/NotificationDestinationResolverTest.kt`.
- [X] T019 [P] [US2] Add navigation/screen tests proving unresolved links keep PNOT visible and emit the friendly unavailable message without marking read (FR-005, SC-003) in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationDestinationTest.kt` and `app/src/test/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModelTest.kt`.

### Implementation for User Story 2

- [X] T020 [US2] Implement the destination resolver using `CardDao.getById` and registered-route availability for owner-scoped resolution (FR-004, FR-005) in `app/src/main/java/com/kipu/app/feature/notifications/data/RegisteredNotificationDestinationResolver.kt`.
- [X] T021 [US2] Connect notice selection to existing card detail navigation and snackbar fallback without auto-marking unavailable notices read (FR-004, FR-005) in `app/src/main/java/com/kipu/app/navigation/NotificationsNavigation.kt` and `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt`.

**Checkpoint**: Existing card links navigate safely; absent and future destinations remain in PNOT with a friendly explanation.

---

## Phase 5: User Story 3 — Mantener organizado el centro (Priority: P2)

**Goal**: Mark one/all notices as read, soft-delete notices offline-first, synchronize safely, and show the active owner's unread badge.

**Independent Test**: Read/dismiss offline and verify the Room list/count changes immediately; reconnect and verify the remote row updates without physical deletion; switch accounts and verify isolation.

### Tests for User Story 3

- [X] T022 [P] [US3] Add DAO/repository tests for atomic read/dismiss plus outbox coalescing, read-all active-row scope, badge count, and account isolation (FR-006–FR-010, SC-002, SC-004, SC-005) in `app/src/test/java/com/kipu/app/feature/notifications/data/NotificationMutationTest.kt`.
- [X] T023 [P] [US3] Add worker tests for verified-owner match, retry behavior, idempotent patches, and clearing only successful outbox rows (FR-009, FR-010, SC-004, SC-005) in `app/src/test/java/com/kipu/app/feature/notifications/data/sync/SyncNotificationsWorkerTest.kt`.
- [X] T024 [P] [US3] Add Compose tests for mark-one/read-all, archive collapse, unread badge semantics/99+, 48dp touch targets, and reduced-motion final state (FR-006–FR-008, FR-011, FR-012, SC-002, SC-005, SC-006) in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationActionsTest.kt`.

### Implementation for User Story 3

- [X] T025 [US3] Implement transactional read/read-all/dismiss mutations and outbox coalescing in `app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt` and `app/src/main/java/com/kipu/app/feature/notifications/data/local/AppNotificationDao.kt` (which also defines `NotificationSyncOutboxDao`) (FR-006, FR-007, FR-009, FR-010, SC-005).
- [X] T026 [US3] Implement per-user WorkManager scheduling and verified-owner retry worker in `app/src/main/java/com/kipu/app/feature/notifications/data/sync/NotificationSyncScheduler.kt` and `app/src/main/java/com/kipu/app/feature/notifications/data/sync/SyncNotificationsWorker.kt` (FR-009, FR-010, SC-005).
- [X] T027 [US3] Add mark-one/read-all/archive actions and user-visible mutation feedback (FR-006, FR-007) to `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModel.kt` and `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt`.
- [X] T028 [US3] Add reduced-motion-aware entry/archive transitions and semantic unread badge component (FR-008, FR-011, FR-012) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationMotion.kt` and `app/src/main/java/com/kipu/app/feature/notifications/presentation/UnreadNotificationBadge.kt`.
- [X] T029 [US3] Observe unread Room count from the dashboard and add a 48dp bell badge action routing to PNOT (FR-008, FR-009, FR-013, SC-002, SC-004) in `app/src/main/java/com/kipu/app/navigation/AccountsNavigation.kt` and `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`.

**Checkpoint**: Read, read-all, dismiss, badge, account isolation, and retry synchronization work online and offline.

---

## Phase 6: Polish & Cross-Cutting Validation

**Purpose**: Validate migration history, accessibility, route safety, tests, and quickstart acceptance.

- [X] T030 [P] Review Kipu color/typography tokens, 48dp targets, contrast, content descriptions, focus behavior, and reduced-motion behavior (FR-011, FR-012, SC-006) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/` and `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`.
- [X] T031 Run `supabase test db --local` and resolve migration/RLS privilege regressions documented in `supabase/tests/database/notifications_center_test.sql`.
- [X] T032 Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` and, when a device/emulator is available, `./gradlew.bat :app:connectedDebugAndroidTest`; record actual test results in `specs/010-ep-not-notificaciones-avisos/quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Tests can be authored immediately; they define the required boundaries.
- **Foundational (Phase 2)**: Depends on Setup tests and blocks all story implementation.
- **US1 (Phase 3)**: Depends on Foundation. Delivers the independent center/read/filter increment.
- **US2 (Phase 4)**: Depends on Foundation and the US1 list selection/route surface.
- **US3 (Phase 5)**: Depends on Foundation and US1 observation/UI; navigation (US2) is not required for read/dismiss.
- **Polish (Phase 6)**: Depends on desired story phases being complete.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2; independent MVP.
- **US2 (P1)**: Starts after Phase 2; integrates with the US1 notification row and center route.
- **US3 (P2)**: Starts after Phase 2; integrates with the US1 Room observations and dashboard; can proceed independently of US2.

### Parallel Opportunities

- T001, T002, T004, T006, and T008 touch separate test/model/storage files and can be authored in parallel.
- T011, T012, and T013 are separate US1 test files and can be authored in parallel.
- T018 and T019 are separate US2 test files and can be authored in parallel.
- T022, T023, and T024 are separate US3 test files and can be authored in parallel.
- After Foundation, US2 and US3 can be developed in parallel if shared navigation/screen files are coordinated; tasks that edit the same files remain sequential.

## Implementation Strategy

1. Complete migration and Room/API/domain foundations.
2. Deliver US1 as the MVP and validate it independently.
3. Add safe deep links (US2) and local-first management/badge sync (US3).
4. Run cross-cutting database, JVM, build, and available Compose tests from Phase 6.

## Notes

- Keep raw historical notification type/reference values unchanged; normalize only at the domain boundary.
- A missing target does not auto-mark a notice read.
- No commit or push is part of this task list; those actions require separate user confirmation.

## Phase 7: Convergence

- [ ] T033 CRITICAL: Execute `./gradlew.bat :app:connectedDebugAndroidTest` on a representative Android emulator/device, resolve any failures, and record the runtime results in `specs/010-ep-not-notificaciones-avisos/quickstart.md` for platform-dependent navigation, badge, accessibility, and reduced-motion behavior per Constitution IX (missing).

## Phase 8: HU-44 — Architecture Gate and RED Tests

**Purpose**: Confirm the HU-44 local-only fallback boundary against HU-42, then add focused failing tests before implementation where the target API exists. The owner explicitly approved one bounded TDD sequencing exception: recurrence scheduler/worker tests move to T051 after T040 creates those classes.

- [ ] T034 [US4] Read-only architecture review of the HU-44 persistence, idempotency, reprogramming, lead-zero, timezone, and permission assumptions in `specs/010-ep-not-notificaciones-avisos/{spec.md,plan.md,data-model.md,contracts/notifications-rest.md}`; return PASS or hold for a material unresolved decision. Do not edit code.
- [ ] T035 [US4] Add RED Room tests for the non-destructive v23→v24 migration, owner scoping, tuple uniqueness, rescheduling/tombstone behavior, and retry idempotency in `app/src/androidTest/java/com/kipu/app/feature/notifications/data/local/ReminderStorageTest.kt` (depends on T034, T036).
- [ ] T036 [US4] Add and execute RED instrumentation tests for debt scheduler/worker behavior only—default 2 days, custom lead, explicit 0 disabled, duplicate retries, date replacement, owner changes, and permission denied/revoked handling—in `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderSchedulerTest.kt` and `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderWorkerTest.kt` (depends on T034). Do not add recurrence tests here; those classes do not exist until T040.
- [ ] T037 [US4] Add RED repository/domain tests proving local events join the center, remain owner-scoped, and never enter the remote PATCH outbox in `app/src/test/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepositoryTest.kt` and `app/src/test/java/com/kipu/app/feature/notifications/data/LocalReminderCenterTest.kt` (depends on T039, T050).
- [ ] T038 [US4] Add RED UI tests for default/custom lead and explicit-zero behavior without changing due-date or payment state in `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtScheduleAndClosureFlowTest.kt` and `app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceRegistrationFlowTest.kt` (depends on T034, T035).

## Phase 9: HU-44 — Local Persistence, Scheduling, Integration, and UI

**Purpose**: Implement the approved local reminder contract in bounded vertical slices; production code follows failing tests where APIs exist. Recurrence scheduler/worker tests are the explicitly owner-approved exception and are added after T040 under T051.

- [ ] T039 [US4] Add owner-scoped `ReminderSchedule` and `LocalReminderEvent` Room entities/DAOs with a unique `(user_id, source_id, event_type, due_date)` key, register the v23→v24 migration and schema only; do not touch Supabase migrations or legacy migrations in `app/src/main/java/com/kipu/app/feature/notifications/data/local/{ReminderScheduleEntity.kt,ReminderScheduleDao.kt,LocalReminderEventEntity.kt,LocalReminderEventDao.kt}`, `app/src/main/java/com/kipu/app/core/database/{KipuDatabase.kt,RoomMigrations.kt}`, and `app/schemas/` (depends on T034, T035, T038, T050).
- [ ] T040 [US4] Implement/reconcile WorkManager debt scheduling with permission-independent Room event persistence and Android-only permission gating; add the recurrence scheduler/worker using the same idempotent store and revalidation contract in `app/src/main/java/com/kipu/app/feature/debts/data/sync/{DebtReminderScheduler.kt,DebtReminderWorker.kt}` and new `app/src/main/java/com/kipu/app/feature/recurrences/data/sync/{RecurrenceReminderScheduler.kt,RecurrenceReminderWorker.kt}`. Run the debt-only T036 tests; do not claim recurrence behavioral coverage until T051 (depends on T034, T036, T037, T039).
- [ ] T041 [US4] Reconcile scheduling on debt/recurrence creation, due-date change, prórroga, pause/cancel, and active-account changes; ensure old work cannot emit after state changes in `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt` and `app/src/main/java/com/kipu/app/feature/recurrences/{data/repository/OfflineFirstRecurrenceRepository.kt,data/local/RecurrenceDao.kt,di/RecurrencesModule.kt}` (depends on T040).
- [ ] T042 [US4] Merge local reminder events into the HU-42 center model/flow and handle local read/dismiss without remote PATCH or outbox insertion in `app/src/main/java/com/kipu/app/feature/notifications/{domain/Notification.kt,domain/NotificationsRepository.kt,data/OfflineFirstNotificationsRepository.kt,presentation/NotificationsViewModel.kt}` (depends on T037, T039, T040, T041, T050).
- [ ] T043 [US4] Expose per-rule lead configuration in debt/recurrence UI, default both to 2 days, preserve existing per-domain ranges, and keep 0 disabled in `app/src/main/java/com/kipu/app/feature/debts/presentation/{DebtScheduleScreen.kt,DebtScheduleViewModel.kt}` and `app/src/main/java/com/kipu/app/feature/recurrences/presentation/{RecurrenceRegistrationScreen.kt,RecurrencePresentationViewModel.kt}` (depends on T038, T041, T051).

## Phase 10: HU-44 — Focused Verification and Release Gates

**Purpose**: Verify only HU-44 acceptance on the configured local database and target device, then converge and release after independent PASS verdicts.

- [ ] T044 [US4] Run only `pnpm exec supabase test db --local supabase/tests/database/notifications_center_test.sql` against the local Supabase database configured at port 54322; preserve raw pgTAP output and do not reset the database, apply a hosted migration, or change grants (depends on T039, T040, T041, T042, T043).
- [ ] T045 [US4] Run focused JVM regression tests including the existing `SyncNotificationsWorkerTest` plus selected Room, debt/recurrence scheduler/worker, and lead-setting UI instrumentation classes on Samsung Galaxy A16 (`SM_A165M`), using instrumentation-runner class filters; verify XML reports intended classes, tests, device, and zero failures, and record evidence in `specs/010-ep-not-notificaciones-avisos/quickstart.md`. Do not run the global 413-test Android suite (depends on T039, T040, T041, T042, T043, T051).
- [ ] T046 [US4] Initial independent Security review of the exact HU-44 candidate: owner-scoped Room keys/queries, account switching, permission separation, local-event no-remote-write boundary, recurrence scheduler/worker behavior, and no financial side effects; preserve its explicit PASS or findings as historical evidence (depends on T039, T040, T041, T042, T043, T051).
- [ ] T047 [US4] Independent final QA audit of FR-014–FR-020, SC-007–SC-011, and assumptions 181–183 against the exact post-fix candidate and focused pgTAP/JVM/Room/WorkManager/A16 evidence; preserve T046 NO PASS and T055's pre-fix context, require current hash-bound T057 Security PASS, and explicitly assess Android-tray cancellation and the `DebtChangeFeedApplier` test gap without broadening product scope (depends on T044, T045, T046, T055, T057).
- [ ] T048 [US4] After Security and QA PASS, run `speckit-converge` only if the owner explicitly invokes that exact step and the installed prerequisites verify `speckit-implement` ran against the current `tasks.md`; reconcile only HU-44 spec/plan/tasks/code drift and record the actual result (depends on T047).
- [ ] T049 [US4] After GREEN and convergence, create granular conventional commits by docs/Room data/core/UI/tests, push this branch, open a PR to `main`, wait for required exact-head CI, merge, and verify the merge on `main`; user explicitly authorized this release sequence. No monolithic commit (depends on T048).

### HU-44 Dependencies and Parallel Opportunities

- T034 is the initial architecture gate. T036 is debt-only RED evidence; after it, T035 then T038 run serially on the shared worktree/device, and T039 waits for both test tasks plus T050. T037 waits for T039/T050 and provides RED center-integration evidence before T040/T042.
- T040 waits for T036/T037/T039; T041 follows T040, then T042 follows the scheduling/lifecycle implementation. Owner-approved TDD exception: T051 adds and executes recurrence scheduler/worker tests only after T040, T041, and T042. T043 waits for T038, T041, and T051. T044–T046 are distinct pgTAP, pre-remediation device-test, and initial Security evidence gates. T053 analyzes SEC-044-01 read-only; T054 waits for T045, T046, and T053; T055 re-audits the post-T054/pre-T056 candidate; T056 corrects debt lead eligibility; T057 independently audits the exact post-T056 candidate. Run final T047 QA only after explicit T057 PASS while preserving T046/T055 history; T048/T049 remain sequential, separately authorized release gates.
- T033 remains an incomplete HU-42 convergence item and is not part of HU-44. Do not run it as a substitute for focused HU-44 device tests.
- Exclude BUG-CORE-MIG-001, legacy balance tests, and the global 413-test Android suite from HU-44 scope.

## Phase 11: HU-44 — Notification Center API Contract Gate

**Purpose**: Pin the local-event/domain/repository interface before center-integration tests or code consume it.

- [ ] T050 [US4] Define and document the exact HU-44 local notification-center API against the existing `AppNotification`/`NotificationsRepository`: origin representation, collision-safe stable local ID derived from owner/source/type/date, mapping/display data, merged flow ordering, unread-count behavior, and local `markRead`/`markAllRead`/`dismiss` routing with no remote PATCH/outbox. Update only `data-model.md` and the relevant HU-44 plan section; inspect current symbols and preserve the HU-42 REST/RLS contract. No production code or tests. Return concrete Kotlin model/repository/DAO signatures and evidence; flag any product-level ambiguity instead of guessing (depends on T034).

## Phase 12: HU-44 — Approved Recurrence Test Sequencing Gate

**Purpose**: Add and run recurrence scheduler/worker behavioral tests after their production classes and lifecycle/center integrations exist; this is the explicitly authorized exception to the feature's test-first sequence.

- [ ] T051 [US4] Add recurrence-only instrumentation coverage in `app/src/androidTest/java/com/kipu/app/feature/recurrences/data/RecurrenceReminderSchedulerTest.kt` and `app/src/androidTest/java/com/kipu/app/feature/recurrences/data/RecurrenceReminderWorkerTest.kt` for default/custom/zero lead, idempotent owner/source/type/date scheduling, date/prórroga replacement, PAUSED/CANCELLED revalidation, active-owner isolation, and permission-independent local event persistence. Run only these class filters on Samsung Galaxy A16 (`SM_A165M`); preserve exact XML/test counts and route any product failures without weakening assertions or changing production code in this test task. These tests intentionally follow T040/T041/T042 under explicit owner approval, and must be GREEN before T045/QA/release (depends on T040, T041, T042).

## Phase 13: HU-44 — SEC-044-01 Remediation and Re-audit

**Purpose**: Close the initial T046 Security finding without erasing its NO PASS history or expanding beyond HU-44.

- [X] T052 [US4] Repair only `app/src/androidTest/java/com/kipu/app/feature/notifications/data/local/ReminderStorageTest.kt` so the Room test executes and validates the real v23→v24 migration, seeds fixtures for tombstone/reschedule/idempotency, and verifies owner isolation; focused SM-A165M run passed 5/5 with zero failures/errors/skips and `git diff --check` PASS.
- [ ] T053 [US4] Read-only architecture assessment of SEC-044-01 covering debt/recurrence owner-scoped source IDs, state/date/lead reconciliation, local and change-feed physical deletion paths, test seams, and the minimum safe no-schema remediation; do not edit code/tests/spec artifacts (depends on T046).
- [ ] T054 [US4] Following T053's accepted design, add Room-backed failing tests first and then remediate immediate logical withdrawal of stale local events on source date changes, inactive/deleted/settled/no-longer-eligible state, or lead 0; preserve valid sibling and other-owner events and cancel obsolete work. Include only the minimum deletion-transition paths needed to retain source IDs; no remote writes, financial effects, migration/schema, or unrelated debt work. Run the focused affected tests on Samsung Galaxy A16 and append exact commands/XML counts/timestamps to `quickstart.md`; do not alter `ReminderStorageTest.kt` (depends on T045, T046, T053).
- [ ] T055 [US4] Independent Security re-review of the exact post-T054 candidate and evidence. Return explicit PASS/NO PASS for SEC-044-01; verify owner+source scoping, immediate event withdrawal, active sibling/other-owner preservation, old-work cancellation, no remote/outbox or financial side effects, and record reviewed HEAD plus candidate file hashes (depends on T054).
- [X] T056 [US4] Correct the debt reconciler eligibility range to `1..365` while preserving `0` as disabled; add Room-backed coverage for leads 31 and 365 and verify current event dates. TDD RED reproduced the old-range tombstone failure; GREEN compiled Kotlin/main AndroidTest and passed the filtered `DebtReminderReconciliationTest` 3/3 on Samsung Galaxy A16 SM_A165M / Android 16; no global 413-test suite or commit (depends on T054, T055).
- [X] T057 [US4] Independently re-audit the exact post-T056 candidate for SEC-044-01, hash-bound to candidate file bytes rather than clean HEAD. PASS limited to SEC-044-01; filtered `DebtReminderReconciliationTest` passed 3/3 on SM_A165M / Android 16 and static review verified owner+source tombstones and transactional local/remote deletion paths. Preserve residuals: no dedicated incoming `DebtChangeFeedApplier` integration test and no Android notification-tray cancellation; this is not general HU-44 acceptance (depends on T056, T055).
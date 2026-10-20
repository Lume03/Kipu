# Implementation Plan: EP-NOT — Centro de Notificaciones y Avisos

**Branch**: `feat/010-ep-not-hu-44-recordatorios` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)

**Propagated**: 2026-10-19 — Updated from spec.md refinement for Sprint 6 HU-44.

## Summary

Implement HU-42 as a local-first financial notification center. Supabase remains the owner-scoped source for server-created notices; a Room projection provides immediate reads and offline actions, and a per-user WorkManager outbox retries idempotent read/soft-delete patches. Compose exposes PNOT from the dashboard bell, with a resilient destination resolver and accessible filtering, empty, read, and dismissed states.

Sprint 6 HU-44 adds configurable local reminders for debt and recurrence due dates, idempotent per-owner scheduling, rescheduling on due-date/state changes, and a Room-backed local center event independent of Android's notification permission. Server-owned `public.app_notifications` and its no-client-INSERT contract remain unchanged.

## Technical Context

**Language/Version**: Kotlin, JDK 17, Android minSdk 24 / targetSdk 36.
**Primary Dependencies**: Jetpack Compose Material 3, AndroidX Navigation, Room, Hilt, Kotlin Coroutines/Flow, WorkManager, Ktor, existing Supabase Auth/PostgREST client. No new library required.
**Storage**: HU-42 was planned at Room schema v12; this branch starts from KipuDatabase v23 and adds only a forward Room migration 23→24 for local reminder state/events. Supabase `public.app_notifications` remains server-owned and unchanged.
**Testing**: Focused JUnit/Room/Compose and WorkManager tests; connected instrumentation filtered to reminder classes on Samsung Galaxy A16; targeted Supabase pgTAP file via `pnpm exec supabase test db --local <path>` (configured local database port 54322). Do not run the global 413-test Android suite for HU-44.
**Target Platform**: Android.
**Project Type**: Existing Android mobile application with Supabase backend.
**Performance Goals**: Display the latest Room snapshot within the existing dashboard navigation and within the specified 2-second center-load criterion; badge and filters derive from Room Flow.
**Constraints**: Offline reads and management; all Room access filtered by active `user_id`; RLS remains authoritative remotely; retries are idempotent; physical remote deletion is prohibited; legacy notification types remain readable; no navigation to unregistered destinations.
**Scale/Scope**: One account-scoped list, five canonical notice types plus the historical credit-threshold alias, individual and bulk read, soft-delete, unread badge, and one existing deep-link destination (card detail).

## Constitution Check

*Gate before research — PASS.*

- **Financial Integrity First**: Notices are descriptive only; they do not create or change ledger entries, balances, budgets, or payment state.
- **Local-First, Retry-Safe Operation**: Cache-first observation and durable per-notice desired-state commands make read/soft-delete retry-safe. Remote refresh must not overwrite a pending local command.
- **Security and Privacy by Design**: Room queries include the active user ID; Supabase RLS isolates `auth.uid() = user_id`; PostgREST grants only SELECT and column-level UPDATE for `is_read` and `deleted_at`; clients cannot insert or physically delete notices.
- **Financial Lifecycles Preserve History**: Dismissal sets `deleted_at`; it never removes the remote row.
- **Quality Is Part of Correctness**: Versioned Room and Supabase migrations, RLS/privilege tests, offline/retry tests, route fallback tests, and accessibility coverage are required.
- **Protect the Approved Product Boundary**: PNOT remains separate from P19 import review; this change adds no payment or external financial action.

No constitutional exceptions are required.

## Research Decisions

See [research.md](research.md) for source evidence and alternatives. Key decisions:

1. Extend the existing `app_notifications` table, not a second notification store. Its owner RLS policy and credit-event uniqueness fields already exist; add nullable `deleted_at` and the minimum update privilege in a forward-only migration.
2. Preserve legacy `CREDIT_UTILIZATION_THRESHOLD_CROSSED` and uppercase `CARD` records. Normalize aliases at the domain boundary; do not rewrite historical rows or constrain the server enum in a way that breaks producers.
3. Use Room as the immediate presentation source. Pull remote rows on center entry; retain pending outbox state during refresh; schedule per-user retry work after mutations.
4. Only `BILLING_DUE` is currently classified as a reminder. Existing card detail is routable. Budget, debt, and goal screens are not registered, so show a non-blocking unavailable message and remain in PNOT.
5. Use an explicit per-row archive action with collapse/fade motion. This satisfies the prototype's quick-action alternative without requiring gesture-only interaction.
6. Apply Kipu design tokens as authoritative. UI Pro Max style-search results did not match the product palette; use semantic Kipu MaterialTheme colors and Inter already bundled in the app. Adapt transition timing guidance to Compose APIs and system reduced-motion settings.

## Architecture

### Data flow

1. `NotificationsViewModel` observes an account-scoped Room Flow and requests a remote refresh when PNOT opens.
2. `NotificationsRepository` maps DTOs to domain models, normalizes type/reference aliases, and upserts the remote snapshot. Rows with pending local outbox commands are excluded from remote overwrite.
3. Mark-read, mark-all-read, and dismiss run in Room transactions: update projection plus upsert a durable desired-state outbox record. The UI reflects the change immediately.
4. A unique WorkManager job per `user_id` checks `SessionCoordinator.currentOwner` before work, PATCHes pending rows through authenticated PostgREST, and clears successful commands. Network/auth failures retry; a different active owner never processes the queued user's work.
5. Dashboard badge count is a Room Flow query for the active user where `is_read = false AND deleted_at IS NULL`. Center filters derive from the same scoped list.

### Remote database and API

- New timestamped migration alters `public.app_notifications` to add `deleted_at timestamptz` and an index supporting active unread/created-time queries.
- Preserve existing ENABLE/FORCE RLS and `app_notifications_own`; test owner access and cross-user denial.
- Keep SELECT for `authenticated`; grant UPDATE only on `is_read` and `deleted_at`; do not grant INSERT or DELETE.
- GET the account's notifications ordered by `created_at DESC`, including `deleted_at` so Room can learn tombstones; screen and badge exclude tombstones. PATCH filters by both `id` and `user_id`; RLS is still the security boundary.
- The REST DTO includes existing `event_key` only if needed for decoding compatibility, while client mutations never touch producer-owned event fields.

### Android modules

- `feature/notifications/domain/model`: notification model, category, destination and action result.
- `feature/notifications/domain`: repository contract and observe/refresh/read/read-all/dismiss use cases.
- `feature/notifications/data/local`: Room entity, DAO and outbox entity/DAO.
- `feature/notifications/data/remote`: PostgREST API and serialization DTOs.
- `feature/notifications/data`: offline-first repository and DTO/entity/domain mappers.
- `feature/notifications/data/sync`: account-keyed WorkManager scheduler and worker.
- `feature/notifications/di`: Hilt bindings.
- `feature/notifications/presentation`: Hilt ViewModel, UI state, center screen, row/filter/empty components and reduced-motion helper.
- `navigation`: PNOT route, dashboard bell callback and destination routing. Card links use the existing `accounts/instrument/{instrumentId}?isCard=true` destination; unsupported/unavailable links return a friendly event without changing read state.
- `core/database`: Room schema v12, migration 11→12, database registration and DAO providers.
- `supabase/migrations` and `supabase/tests/database`: nullable soft-delete field, index, least-privilege grant, and RLS/privilege regression tests.

### Interaction and visual behavior

- Background `#F7F9FB`; white notice cards; unread state uses subtle tonal surface and a teal `#0F766E` dot plus semantic text/state, never color alone.
- Kipu theme Inter typography, 48dp minimum touch areas, WCAG AA contrast, all filter chips `Todos`, `Alertas`, `Recordatorios`, empty copy exactly as specified, and a `Marcar todo como leído` action.
- `app_notifications` has no dedicated due-date column; its pre-existing `event_payload` JSONB carries `due_date` (ISO `YYYY-MM-DD`) when supplied. Persist and render that value as an expected future date, falling back to the producer-supplied `body`; never infer a due date from `created_at` or claim the reminder was paid.
- Use Compose animated visibility/size for route entrance, badge changes, and archive collapse. Read reduced-motion preference and render the same final state with no decorative animation when enabled.
- Accessibility labels expose notice category/read state; badge semantics communicate count (including 99+) without moving focus or creating competing live regions.

## Project Structure

### Feature documentation

```text
specs/010-ep-not-notificaciones-avisos/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/notifications-rest.md
└── tasks.md
```

### Source code

```text
app/src/main/java/com/kipu/app/
├── core/database/{KipuDatabase.kt,RoomMigrations.kt}
├── core/di/CoreModule.kt
├── feature/notifications/
│   ├── data/{OfflineFirstNotificationsRepository.kt}
│   ├── data/local/{AppNotificationDao.kt,AppNotificationEntity.kt,NotificationSyncOutboxEntity.kt}
│   ├── data/remote/{NotificationsApi.kt,NotificationDtos.kt}
│   ├── data/sync/{NotificationSyncScheduler.kt,SyncNotificationsWorker.kt}
│   ├── di/NotificationsModule.kt
│   ├── domain/{NotificationsRepository.kt,usecase/*}
│   ├── domain/Notification.kt
│   └── presentation/{NotificationsViewModel.kt,NotificationCenterScreen.kt}
├── navigation/{AccountsNavigation.kt,KipuNavHost.kt,NotificationsNavigation.kt}
└── feature/accounts/presentation/dashboard/DashboardScreen.kt
app/src/test/java/com/kipu/app/feature/notifications/...
app/src/androidTest/java/com/kipu/app/feature/notifications/...
supabase/migrations/<timestamp>_notification_center_hu42.sql
supabase/tests/database/notifications_center_test.sql
```

**Structure Decision**: Add a vertical-slice `feature/notifications` to the existing single Android app. Database migration remains versioned under the existing Supabase directory; no new backend service or project is introduced.

## Constitution Check After Design

*PASS.* Design preserves existing financial truth and producer records, scopes local/remote access by account, stores offline mutations durably, performs only soft deletion, and defines tests at Room, API, RLS, navigation, and UI boundaries. Cross-review and validation remain implementation completion gates, not design exceptions.

## Sprint 6 Increment — HU-44

### Scope and architectural boundary

- Deliver HU-44 for debts (HU-29) and recurrence rules/occurrences (HU-33/HU-35), using the existing per-source lead setting and a default of two days. Preserve the current per-domain validation ranges; explicit `0` disables reminders.
- Extend the current Room database from v23 to v24 with owner-scoped `ReminderSchedule` and local center-event records. Uniqueness is enforced by `(user_id, source_id, event_type, due_date)`; `event_type` is `BILLING_DUE`, and `source_id` identifies a recurrence, debt installment, or single-due debt.
- Keep HU-42's server-created `public.app_notifications` rows and REST contract unchanged. HU-44 center events are local Room data, never client INSERTs or remote outbox mutations; the event is visible on this device but cross-device sync/recovery after app-data loss is not promised.
- The center combines server notices and local HU-44 events while retaining their origin. Read/dismiss operations on a local reminder remain local and must not enqueue a remote PATCH.

### Notification-center API contract gate

- `AppNotification` is the existing type in `feature/notifications/domain/Notification.kt`, package `com.kipu.app.feature.notifications.domain` (not `domain.model.Notification`). Preserve its HU-42 fields and append `origin: NotificationOrigin = NotificationOrigin.REMOTE`; define `NotificationOrigin { REMOTE, LOCAL_REMINDER }` alongside it. Keep the existing `NotificationsRepository` API: `observeActive(userId): Flow<List<AppNotification>>`, `observeUnreadCount(userId): Flow<Int>`, `refresh(userId): Result<Unit>`, `markRead(userId, notificationId): Result<Unit>`, `markAllRead(userId): Result<Unit>`, `dismiss(userId, notificationId): Result<Unit>`, and `syncPending(userId): Result<Unit>` (suspend for the five `Result` operations).
- T039 adds `LocalReminderEventDao` at `feature/notifications/data/local/LocalReminderEventDao.kt`: `observeActive(userId): Flow<List<LocalReminderEventEntity>>`, `observeUnreadCount(userId): Flow<Int>`, `markRead(userId, id): Int`, `markAllRead(userId): Int`, and `dismiss(userId, id, deletedAt: Instant): Int`. Each query/mutation filters by `user_id`; active rows require `deleted_at IS NULL`. `OfflineFirstNotificationsRepository` combines the existing remote DAO flow and this local flow; `observeActive` maps explicit origins and orders by `createdAt DESC`, then origin (`REMOTE` first on ties), then ID ascending. `observeUnreadCount` sums both scoped active unread counts (the badge keeps its existing `99+` presentation).
- Local events use ID `local-reminder:v1:<lowercase SHA-256 hex>` over an unambiguously length-prefixed UTF-8 tuple `(normalized user_id, normalized source_id, BILLING_DUE, ISO due_date)`; retain the `(user_id, source_id, event_type, due_date)` unique constraint and reject rather than overwrite an ID/tuple collision. Local presentation is title `Recordatorio de vencimiento`, body `Recordatorio local de vencimiento.`, `BILLING_DUE`, null destination references, and stored ISO `due_date` consumed by the existing `expectedDueDateLabel()` helper; mapping and collision details are normative in `data-model.md`.
- `markRead`/`dismiss` route this local ID namespace only to the owner-scoped local DAO. `markAllRead` updates both sources, but only remote rows update/merge the HU-42 outbox and schedule remote sync; local operations never touch `NotificationSyncOutboxDao`, `NotificationsApi`, or remote PATCH. Remote UUID IDs and their current mutation behavior remain unchanged. There is no cross-origin semantic deduplication: keep both records if their meaning/date appears similar because HU-42 has no shared correlation key, and do not alter DTO, `app_notifications`, RLS, or grants.
- T050 pins this contract for T039/T037/T042. Tests should assert owner scoping, deterministic merge order/count, stable collision-safe local IDs, local read/read-all/dismiss without outbox/PATCH, and unchanged HU-42 behavior.

### Reminder scheduling and delivery

- Reconcile the existing `DebtReminderScheduler`/`DebtReminderWorker` path rather than leave two independent schedulers for debts; add the recurrence scheduling path against the same idempotent local storage contract.
- Schedule one current WorkManager job per owner/source/type. On a due-date change or prórroga, cancel/replace the prior job, withdraw any old local event with a logical tombstone, and schedule only the current due date.
- Revalidate active owner, current source date, and source state at execution. Recurrences in `PAUSED` or `CANCELLED` and non-active debts do not emit, including on delayed/retried work. On every source reconciliation, immediately tombstone active local events when the source is missing, inactive, deleted, settled, no longer eligible, or has lead 0; scope each update by owner and source so other owners and active sibling installments remain intact.
- Persist the local center event regardless of `POST_NOTIFICATIONS`; permission gates only Android system presentation. If allowed, OS notification is an additional rendering of the same event, not a second logical event. Recheck permission at execution in case it was revoked after scheduling.
- Use due-date civil dates in `America/Lima`; WorkManager may defer execution and exact-second delivery is not an acceptance criterion. Reminders never modify financial state.
- The repository has `DebtReminderWorker`, not an existing class named `NotificationWorker`; Android acceptance targets the actual debt worker and the new recurrence worker path.

### HU-44 verification and exclusions

- Add Room migration/storage, debt scheduler/worker, center-merge, and lead-setting tests before their corresponding production code. By explicit owner approval, recurrence scheduler/worker behavioral tests are the one bounded sequencing exception: they are added and run under T051 only after T040 provides the production types, with T051 and final T045 as blocking GREEN gates. A compile error caused by a missing recurrence type is not accepted as behavioral RED. Verify only the Room v23→24 migration; do not repair legacy migration/balance failures.
- Run the focused `notifications_center_test.sql` pgTAP file against the configured local Supabase database on port 54322 to protect the HU-42 RLS/privilege boundary; no Supabase DDL or privilege change is part of this increment.
- Run only selected Android test classes on the Samsung Galaxy A16 and verify the instrumentation XML identifies the intended classes/device. Do not run the global 413-test Android suite or broaden into BUG-CORE-MIG-001/legacy balance tests.
- Preserve the original T046 verdict and findings. T055 is historical Security evidence for the pre-T056 candidate; after T056, require an explicit T057 hash-bound Security PASS against the exact post-fix candidate before final QA. Do not treat lifecycle `done` as a positive verdict.
- After focused post-remediation tests, current hash-bound Security PASS, and independent QA PASS, convergence remains a separate owner-invoked gate: do not run `speckit-converge` unless the owner explicitly invokes that step and its installed prerequisites verify `speckit-implement` ran against the current `tasks.md`. The release steps remain sequential and gated; no monolithic commit.

### Structure delta

```text
app/src/main/java/com/kipu/app/
├── core/database/{KipuDatabase.kt,RoomMigrations.kt}  # v23→v24
├── feature/notifications/data/local/{ReminderScheduleEntity.kt,ReminderScheduleDao.kt,LocalReminderEventEntity.kt,LocalReminderEventDao.kt}
├── feature/notifications/data/{OfflineFirstNotificationsRepository.kt}
├── feature/debts/data/sync/{DebtReminderScheduler.kt,DebtReminderWorker.kt}
└── feature/recurrences/data/{repository/OfflineFirstRecurrenceRepository.kt,local/RecurrenceDao.kt,sync/RecurrenceReminderScheduler.kt,RecurrenceReminderWorker.kt}
```

**Complexity**: One Room migration and a separate device-local event source are required to satisfy strict idempotency and the no-permission center fallback without weakening HU-42 server privileges. No new remote service, Supabase schema, or Android dependency is introduced.

## Complexity Tracking

No constitution violations or extra projects. The outbox is necessary to satisfy offline mutation durability, retry safety, and account-change isolation already required by HU-42.

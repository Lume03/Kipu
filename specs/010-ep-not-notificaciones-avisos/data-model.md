# Data Model: EP-NOT — Centro de Notificaciones y Avisos

## AppNotification

One server-created financial notice owned by one Kipu account. It may reference an object that is no longer available.

| Field | Remote type | Room type | Rules |
|---|---|---|---|
| `id` | UUID | UUID | Primary identifier; composite local key with `user_id`. |
| `user_id` | UUID | UUID | Required owner; local query predicate and Supabase RLS owner. |
| `title` | text | String | Required, producer-owned. |
| `body` | text | String | Required, producer-owned. |
| `notification_type` | text | String | Preserve raw string; normalize known canonical and legacy aliases in domain. |
| `reference_entity_type` | text, nullable | String? | Weak reference: card, budget, debt, goal; case-insensitive. |
| `reference_entity_id` | UUID/text, nullable | String? | Weak reference only; target may be deleted. |
| `is_read` | boolean, default false | Boolean | User interaction state. |
| `created_at` | timestamptz, default now() | Instant | Server creation time; list sorts descending. |
| `deleted_at` | timestamptz, nullable | Instant? | Soft-delete tombstone; dismissed notices never appear in center or badge. |
| `event_key` | text, nullable, pre-existing | not required for presentation | Producer idempotency metadata, not client mutable. |
| `event_key` | text, nullable, pre-existing | Not projected to Room | Producer idempotency metadata; client read-only. |
| `event_payload` | jsonb, NOT NULL DEFAULT `{}`, pre-existing | Stored in Room as JSON text | Producer metadata; `due_date` (ISO `YYYY-MM-DD`) is preferred for `BILLING_DUE`; client read-only. |

### Domain interpretation

- Category `REMINDER`: `BILLING_DUE`.
- Category `ALERT`: `BUDGET_ALERT`, `GOAL_REACHED`, `CREDIT_THRESHOLD`, historical `CREDIT_UTILIZATION_THRESHOLD_CROSSED`, `SYSTEM`, and unknown values.
- Credit legacy alias and reference casing are normalized only in memory; persisted raw values remain unchanged.
- No dedicated due-date column exists. When supplied, `event_payload.due_date` (ISO `YYYY-MM-DD`) is preferred and rendered with an explicit future prefix; otherwise display the producer's `body` verbatim with future/reminder labeling. Never derive a due date from `created_at` or imply payment occurred.

## NotificationSyncOutbox

Durable local desired state for one notice. Its unique key is `(user_id, notification_id)` so newer intent coalesces older retry work.

| Field | Type | Rules |
|---|---|---|
| `operation_id` | UUID | Stable primary key for diagnostics and deterministic local handling. |
| `user_id` | UUID | Must match both the active owner and notice owner before remote work. |
| `notification_id` | UUID | Identifies the remote row; unique with owner. |
| `is_read` | Boolean? | Desired value to PATCH when non-null. |
| `deleted_at` | Instant? | Desired tombstone timestamp; dismissal stores a non-null value. |
| `attempt_count` | Int | Increments on failed sync attempts. |
| `created_at` | Instant | Local enqueue time. |

The DAO transaction updates the notice and inserts/updates its outbox record atomically. Read-all creates or merges desired read state only for active, non-dismissed notices owned by the current user.

## Derived values

- **Active notices**: `user_id = activeUserId AND deleted_at IS NULL`.
- **Unread badge**: active notices with `is_read = false`; display `99+` above 99, with a semantic count.
- **Filtered list**: active rows ordered by `created_at DESC`; alert/reminder classification is performed by the domain mapper.
- **Destination**: a weak reference resolved by normalized type, registered route, and owner-scoped object existence; unresolved destinations do not mutate the notice.

## HU-44 Local ReminderSchedule (Room only)

`ReminderSchedule` is the durable local scheduling record for one due reminder. It is not a row in Supabase `public.app_notifications` and does not use the HU-42 remote mutation outbox.

| Field | Room type | Rules |
|---|---|---|
| `user_id` | UUID | Required account owner; every query and worker verifies the active owner. |
| `source_id` | UUID/text | Identifies the recurrence rule, debt installment, or single-due debt. |
| `event_type` | String | Canonical reminder type `BILLING_DUE`. |
| `due_date` | LocalDate / ISO `YYYY-MM-DD` | Civil date used for scheduling; never inferred from `created_at`. |
| `lead_days` | Int | Existing per-source validated range; default 2; explicit 0 disables. |
| `status` | Enum | `SCHEDULED`, `DELIVERED`, or `CANCELLED`; cancelled schedules cannot be emitted by stale workers. |
| `updated_at` | Instant | Last local reconciliation time. |

The composite key `(user_id, source_id, event_type, due_date)` is unique. Changing a due date cancels the prior WorkManager job and creates/reconciles the current tuple; no duplicate active job is allowed for an owner/source/type.

## HU-44 LocalReminderEvent (Room only)

Kotlin entity symbol: `com.kipu.app.feature.notifications.data.local.LocalReminderEventEntity`, declared in `app/src/main/java/com/kipu/app/feature/notifications/data/local/LocalReminderEventEntity.kt`. Its persisted properties correspond to the fields below; `id` is a stored String primary key, with the owner/source/type/date tuple also uniquely indexed.

`LocalReminderEvent` is the center-visible local representation emitted when a schedule becomes due. It is separate from server-owned `AppNotification`, carries an explicit local origin in the center domain model, and is never sent through the REST PATCH/outbox path.

| Field | Room type | Rules |
|---|---|---|
| `id` | String | Stable local-only ID `local-reminder:v1:<lowercase SHA-256 hex>`; derived from the canonical owner/source/type/date tuple. |
| `user_id` | UUID | Required owner; account-scoped queries prevent cross-account display. |
| `source_id` | UUID/text | Same stable source identity as its schedule. |
| `event_type` | String | `BILLING_DUE`. |
| `due_date` | LocalDate / ISO `YYYY-MM-DD` | Part of the idempotency key and visible expected date. |
| `is_read` | Boolean | Local center interaction state. |
| `deleted_at` | Instant? | Local logical withdrawal on reschedule/cancel; no physical-history deletion. |
| `created_at` | Instant | Local event creation time for center ordering. |

The same owner/source/type/date tuple is unique for the local event, and `id` is unique within `user_id`. Reconciliation is an upsert; retries do not create duplicates. To compute `id`, normalize UUID owner/source values to lowercase canonical UUID text (otherwise preserve source text), use canonical `BILLING_DUE` and ISO `YYYY-MM-DD`, UTF-8 encode the four tuple parts in this order `(user_id, source_id, event_type, due_date)` with each part prefixed by its 4-byte big-endian byte length, then SHA-256 the bytes. The prefix and version keep this identity disjoint from HU-42 server UUID IDs; the tuple unique constraint remains the authoritative collision check. If the computed ID already belongs to a different tuple, fail the write and surface a storage error; never overwrite the other event. If Android permission is denied or revoked, the event remains in the local center. If permission is granted, OS presentation is additional and does not create another logical event. Local-only events are not cross-device synchronized and are not recoverable after app-data loss.

## HU-44 Center domain/repository/DAO contract

These are the exact Kotlin symbols for T039, T037, and T042. `AppNotification` remains declared in `app/src/main/java/com/kipu/app/feature/notifications/domain/Notification.kt` (package `com.kipu.app.feature.notifications.domain`); do not create or import `domain.model.Notification`. Preserve every existing `AppNotification` property and append `val origin: NotificationOrigin = NotificationOrigin.REMOTE` so HU-42 construction and remote mapping keep their current behavior. Add `enum class NotificationOrigin { REMOTE, LOCAL_REMINDER }` in that same file. Existing repository declarations stay in `domain/NotificationsRepository.kt` and retain their signatures:

```kotlin
fun observeActive(userId: String): Flow<List<AppNotification>>
fun observeUnreadCount(userId: String): Flow<Int>
suspend fun refresh(userId: String): Result<Unit>
suspend fun markRead(userId: String, notificationId: String): Result<Unit>
suspend fun markAllRead(userId: String): Result<Unit>
suspend fun dismiss(userId: String, notificationId: String): Result<Unit>
suspend fun syncPending(userId: String): Result<Unit>
```

`AppNotificationDao` remains the HU-42 remote-projection DAO. Add `LocalReminderEventDao` in `feature/notifications/data/local/LocalReminderEventDao.kt`, with these owner-scoped operations (all list/count flows must query `user_id = :userId` and active means `deleted_at IS NULL`):

```kotlin
fun observeActive(userId: String): Flow<List<LocalReminderEventEntity>>
fun observeUnreadCount(userId: String): Flow<Int>
suspend fun markRead(userId: String, id: String): Int
suspend fun markAllRead(userId: String): Int
suspend fun dismiss(userId: String, id: String, deletedAt: Instant): Int
```

`OfflineFirstNotificationsRepository` injects this DAO. `observeActive(userId)` combines the remote DAO flow and local DAO flow, maps each remote row to `AppNotification(origin = REMOTE)` and each local row to `AppNotification(origin = LOCAL_REMINDER)`, then returns one list ordered by `createdAt` descending; ties are deterministic by `origin` (`REMOTE` before `LOCAL_REMINDER`) and then `id` ascending. Remote display fields preserve the existing projection verbatim. Local display mapping is: `id` from the stable local ID above, owner from `user_id`, title `Recordatorio de vencimiento`, body `Recordatorio local de vencimiento.`, `notificationType = BILLING_DUE`, null reference fields, local `isRead`/`createdAt`/`deletedAt`, and `eventPayloadJson` containing `due_date` as ISO `YYYY-MM-DD`. The existing `expectedDueDateLabel()` presentation helper then supplies the localized `Vence el <fecha>` label from that payload; do not repeat the date in `body`. This is informational only and does not assert a payment or expose an unverified destination.

`observeUnreadCount(userId)` combines the remote and local unread DAO flows and emits their sum: all owner-scoped rows with `is_read = false AND deleted_at IS NULL`, counting each source's active event once. The UI's existing `99+` presentation remains unchanged. There is no cross-origin semantic deduplication: local and remote identities occupy separate ID namespaces and represent different producers; if both describe a similar due date, retain both rather than dropping either source without a shared server correlation key. Within each origin, use its established ID/tuple uniqueness and owner scoping.

For `markRead` and `dismiss`, IDs with the exact `local-reminder:v1:` prefix route to `LocalReminderEventDao` using both `userId` and `notificationId`; require exactly one active row update or return failure. This branch changes only Room local event state, does not call `NotificationSyncOutboxDao`, does not schedule HU-42 sync, and cannot call `NotificationsApi.patchForUser`. Other IDs follow existing HU-42 remote DAO + outbox behavior unchanged. `markAllRead` performs the existing remote/outbox operation for remote unread notices and the local DAO update for local unread events; it never creates local outbox rows. Every local DAO mutation is owner-scoped. Remote refresh continues to read/write only the existing remote projection and cannot replace or delete local events.

HU-42 remote UUID IDs, DTOs, `app_notifications`, RLS, grants, and remote outbox semantics are unchanged. Local rows are device-only Room state and must not be inserted into the server table or remote mutation outbox.

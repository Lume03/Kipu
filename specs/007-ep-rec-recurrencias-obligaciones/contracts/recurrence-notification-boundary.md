# Recurrence ↔ Notifications Boundary (Draft)

**Owner split**: EP-REC owns the recurrence rule/configuration and its authoritative lifecycle state. EP-NOT/HU-44 owns notice scheduling/delivery, retry, deduplication, notification center, and delivery history. This interface documents the handoff only; it does not add HU-44 implementation to EP-REC.

## State signal

The consumer needs the recurrence identity, owner-scoped authorization context, authoritative `status`, `revision`, `next_due_date`, and required non-null integer `reminder_lead_days` as defined by the finalized contract. DP-REC-05 approves client default 2 before command hashing, rejects missing/`null`, and sets inclusive range 0–30. This boundary carries a validated integer value; it does not preserve or inspect the source JSON token spelling. Command numeric canonicalization and field validation are defined in `recurrence-command-contract.md` §3. The consumer must use the newest revision and treat only `ACTIVE` as eligible for future notices. `PAUSED` and `CANCELLED` suppress new notices; a stale/out-of-order active update must not reactivate notice eligibility. An already delivered notice is not undone by pause/cancel. Delivery semantics for a zero-day lead time remain part of the EP-NOT/HU-44 integration contract.

For legacy compatibility only, `is_active` is a read-only projection (`ACTIVE=true`; `PAUSED`/`CANCELLED=false`); the consumer must use authoritative `status`, not this boolean. Retain the projection until an authorized inventory confirms no legacy readers; remove it only in a separately reviewed later migration. That inventory has not been performed.

## Contract conditions

- Lifecycle updates and the sync state signal commit atomically; a local optimistic transition is not authoritative for delivery until accepted by the server.
- EP-REC does not enqueue notification jobs or call Android `NotificationManager`; no notification delivery is claimed by this epic.
- HU-44 owns deduplication/idempotency keys and cancellation of its own pending jobs after a newer non-active revision.
- A recurrence create/update/state change does not create a financial event, occurrence, transaction, payment, ledger entry, or budget consumption.
- If the current HU-44 integration cannot consume status/revision before dispatch, the cross-epic behavior is a blocker: do not claim “no future alerts” is satisfied by an EP-REC local flag alone. Route the change to EP-NOT's owner and update the shared contract with both owners.

## Required integration verification

1. ACTIVE revision is visible to the consumer with the approved lead time.
2. PAUSED/CANCELLED newer revision prevents any newly scheduled notice; stale ACTIVE cannot win.
3. Resume produces eligibility only for the approved next date; missed periods do not create backfilled alerts or financial records.
4. Retries/duplicate state messages do not duplicate deliveries (HU-44 test ownership).

**Upstream review and remaining boundary gates**: Product decisions DP-REC-01/02/05, including the `is_active` read-only mapping, explicit resume confirmation, and required non-null lead time, are resolved and must be reflected by dependent writers. T003 approved TECH-REC-HASH-01, TECH-REC-STRING-01, TECH-REC-PAYLOAD-01 and TECH-REC-CALENDAR-01 in its read-only architecture review; implementation and Kotlin/PostgreSQL parity tests remain pending. The authorized hosted inventory remains unperformed and blocks remote migration/deployment. The EP-NOT contract owner must approve the exact event/API and zero-day lead-time semantics before that integration is considered ready.

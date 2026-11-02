# D7 — Development plan RPCs

**Propagated**: 2026-11-02 — FR-068–FR-073. Authenticated session required; no administrative key in Android.

- `POST /rest/v1/rpc/dev_plan_mode`, body `{}` → boolean, STABLE. Only the server gate selects development versus Play.
- `POST /rest/v1/rpc/dev_activate_plan`, body `{ "p_operation_id": "uuid", "p_plan_type": "FREE|PRO_MONTHLY|PRO_ANNUAL|PRO_LIFETIME" }` → JSON object.
- `APPLIED`: `operationId`, `planType`, `expiresAt` (30/365 server days or null). Same user/operation/plan returns the original object without another write or renewed expiry.
- `CONFLICT` / `OPERATION_CONFLICT`: same user/operation, different plan. Require a new explicit attempt.
- `REJECTED` / `DEV_MODE_DISABLED`: gate off, no writes, including receipts or audit. `INVALID_REQUEST` rejects invalid/null inputs; no session raises 42501.

No user parameter exists. Receipt, override and SET_PLAN audit commit atomically. FREE is a permanent neutralizing override while enabled; historical bounded overrides keep their original expiry. Existing permanent DEVELOPER entitlement precedence remains unchanged. Operator-only `internal.set_dev_plan_mode(boolean,text)` delegates to the existing audited gate setter; PUBLIC/anon/authenticated/service_role cannot execute it.

Activation response does not grant client access. Call `refresh-entitlement` and validate the signed owner/installation-bound grant through the existing cache and lease policy. FREE clears the owner's entitlement cache; no financial repository is called. The app keeps the operation UUID for network/refresh retries, drops it on terminal resolution, and never applies a response after its session owner changes. Mode changes require reloading the catalog; a development offer never launches Play.

Before launch: disable the gate with a reason, configure real billing_products, and validate verify-purchase support for the one-time Lifetime Play product. Previously issued offline grants remain bounded by their existing 72-hour maximum lease.

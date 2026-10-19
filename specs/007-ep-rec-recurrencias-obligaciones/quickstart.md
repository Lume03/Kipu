# EP-REC Quickstart: local validation

**Status**: Candidate implementation and tests exist; Android evidence is tracked separately in Kanban. The SQL baseline/apply/GREEN stage has not completed, and no local SQL target is authorized. This guide does not authorize provisioning, Docker/Compose, SQL or test execution. Never point commands at hosted Supabase.

## Prerequisites

- Checkout `007-ep-rec-recurrencias-obligaciones` in `C:\Users\Alume\orca\Kipu`.
- Node/pnpm dependencies installed from the committed lockfile; Android SDK/JDK configured as used by the repository.
- Docker Desktop/local target may be used only after hash-bound Security review, explicit written human approval for the exact fresh target, and independent QA approval of runtime isolation. No target is currently authorized.
- Do **not** expose or copy `.env` values, access tokens, service-role keys, or device serials into logs/comments.
- For connected tests, `adb devices -l` must show the target Samsung Galaxy A16 as `device` (not `unauthorized`/`offline`); re-check immediately before running.

## Local backend test — PASSED

> **Resolución Ejecutiva del Owner**: Se autorizó formalmente la instancia local oficial `supabase_db_kipu` (127.0.0.1:54322, linked_project: null). Se ejecutó la suite completa pgTAP 80/80 mediante `pnpm exec supabase test db --local ...` con resultado PASS (exit code 0), certificada en `pgtap-80-80-execution.log`. Quedan superados todos los gates de backend local.

```bash
# NO EJECUTAR hasta que todos los gates anteriores estén aprobados.
docker compose --profile ep-rec-tests run --rm test-runner
```

El runner explícito cubre las tres suites: `recurrence_schema_test.sql`, `recurrence_create_update_test.sql` y `recurrence_lifecycle_test.sql`. Cuando la ejecución sea autorizada, debe registrar exit code, resultados/conteos y logs; el estado del target, la configuración estática o un checksum por sí solos no son GREEN. Las aserciones incluyen schemas cerrados/unknown keys, ownership/referencias, create/update/lifecycle, retry/hash/revisión, cero efectos financieros/ocurrencias, canonicalización numérica exacta y los vectores de bytes de `recurrence-command-contract.md` §3. Deben probarse las validaciones antes de digest/receipt/mutación y la frontera JSONB real (último valor retenido tras parseo; sin prometer rechazo lexical de claves duplicadas).

## Android unit/build validation

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Expected result after implementation: both commands exit 0; suite includes exact canonical strings/numbers, exact CREATE/UPDATE/SET_STATUS schemas, presence/null, custom/date limits, month-end/leap-year/no-drift, Room migration/outbox retry/revision. Record actual Kotlin/PostgreSQL byte parity; documented SHA-256 fixtures alone are not execution evidence.

## Connected Android validation

```bash
adb devices -l
./gradlew :app:connectedDebugAndroidTest
```

Expected result after implementation: ADB reports the Samsung device as connected; Gradle exits 0; Compose/device tests cover create-from-movement without source mutation, fixed/approximate presentation, monthly/yearly date boundaries, pause/resume/cancel, showing and explicitly confirming the proposed resume date (abandonment emits no ACTIVE command/outbox), cancellation-dialog back-out, offline retry/conflict, and no synthetic payment/history effects. Record Android version/model and Gradle test summary; redact serials and credentials.

## Manual acceptance checklist

- Create from movement details and verify the source movement/history is unchanged.
- Save only with the required valid next due date; show estimated amount as an estimate, not paid.
- Verify supported frequency choices, amount mode, and required non-null integer reminder lead time: client default 2 is applied on CREATE; every UPDATE re-sends the current value unless the person changes it. Accept 0 and 30, reject values outside range, and reject missing/`null` fields. Confirm the EP-NOT owner defines the 0-day delivery meaning.
- Verify semantic JSONB numeric canonicalization: `100`, `100.00` and `1e2` normalize identically; fractional `100.5` and non-integer exponent `1e-1` fail integer validation before an accepted hash or write. Compare the exact bytes/digests in Kotlin and local SQL; do not claim lexical exponent rejection after JSONB parsing.
- Verify the string encoder against every golden byte fixture: controls use the defined short escapes and lowercase `\u00xx` for other C0 controls except rejected U+0000; slash is not escaped, quote/backslash are deterministic, non-ASCII/U+2028/U+2029 emit UTF-8, equivalent JSON escape forms match, and NFC/NFD remain distinct. Reject unpaired surrogates; prove Kotlin/SQL parity only with actual tests.
- Verify exact command schemas: CREATE has every required field; UPDATE is a non-empty patch with required/non-null `reminder_lead_days` (omit=unchanged for other fields, null=clear only for nullable values); SET_STATUS has exact target-specific fields; `template_transaction` rejects extra/omitted nested keys. Kotlin command serialization emits unique object-member names. At the SQL `jsonb` boundary, duplicate raw member names are not promised to reject: test the last-value semantic object after parsing and confirm schema/reference/date checks precede hash acceptance, receipt lookup or write.
- Verify date range `0001-01-01`..`9999-12-31`, count 1 for built-ins, CUSTOM WEEK [1,5200]/MONTH [1,1200], and anchor-based month-end behavior without progressive drift; overflow/out-of-range dates reject.
- Verify `is_active` is read-only (`ACTIVE=true`; `PAUSED`/`CANCELLED=false`) and distinct from `deleted_at`; do not retire it unless a later authorized inventory confirms no legacy readers and a separately reviewed migration is approved. The inventory has not been performed.
- Verify `end_date` is optional and rejected when earlier than `start_date`; after cancellation, verify the rule is terminal and reuse requires a new/cloned rule.
- Pause then request resume; show the calculated first future valid date and persist `ACTIVE` with that exact confirmed date only after explicit user confirmation. Abandonment leaves the rule `PAUSED` and emits no command/outbox. Confirm no missed-period payments/occurrences appear.
- Confirm cancellation; separately leave confirmation and verify no state/configuration change.
- Only after the target-specific gates pass, inspect the approved local test database for zero new transactions, ledger entries, budget consumption and recurrence-occurrence rows; never use hosted data for this check.
- Validate HU-44 status/lead-time integration with its owner; EP-REC alone cannot certify notice delivery or deduplication.

## Evidence to attach to Kanban

For each command, record exact command, exit code, summarized test count, log/artifact path, branch/head, and whether it was local-only. QA's read-only review must name the exact commit/files reviewed and separate unit, SQL, Room, Compose, and connected-device coverage. No hosted deployment is part of this quickstart.

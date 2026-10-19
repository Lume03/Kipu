# Recurrence Command Contract

**Status**: Contrato documental aprobado por T003 (`t_a0fdee9b`) para HU-33/HU-35; DP-REC-01..06 y respuestas complementarias aprobadas. TECH-REC-HASH-01, TECH-REC-STRING-01, TECH-REC-PAYLOAD-01, TECH-REC-CALENDAR-01 y TECH-REC-JSON-01 fijan números, bytes de strings, schemas, límites y frontera JSONB. La aprobación fue read-only: paridad runtime y pruebas de implementación siguen pendientes. `is_active` sigue siendo proyección read-only (`ACTIVE=true`; `PAUSED`/`CANCELLED=false`), retenida hasta inventario autorizado sin lectores legacy y retirada solo en migración posterior revisada. Reanudación requiere confirmación explícita de fecha en payload. El contrato de entrada del comando es `jsonb`, no texto JSON crudo. Lead time 0 permanece en EP-NOT/HU-44. Evidencia local de convenciones: `MovementDtos.kt:19-44`, `MovementModels.kt:63-122`, migración `20261002234503_s4_register_transaction_v2_hash.sql:39-64`; fuente funcional de plantilla y frecuencia: P19 §§3–4 y diccionario V4.2 §10.

## 1. Principios compartidos

1. El propietario lo deriva el backend de `auth.uid()`; no aceptar `user_id` de confianza del cliente. Cada referencia de cuenta/categoría/comercio se valida bajo esa identidad y según las reglas de movimientos existentes.
2. Montos son valores enteros exactos en unidades menores más código moneda; una fracción, valor fuera de rango, valor no finito o conversión implícita es inválido. Una grafía JSON decimal/exponencial cuyo valor matemático exacto sea entero puede aceptarse tras JSONB y canonicaliza igual al entero equivalente; nunca se convierte mediante binary float/double. Una regla o monto aproximado no crea evento financiero.
3. Room persiste proyección/outbox en una transacción. La identidad estable (`operation_id`) y bytes/payload no cambian en reintentos; respuesta temporal no se presenta como confirmación remota.
4. El servidor serializa por `(user_id, operation_id)`, valida revisión y almacena un recibo atómico. Reintento con misma operación y mismo hash/payload devuelve el recibo original (`DUPLICATE`); el mismo ID con tipo/hash/payload distinto devuelve `CONFLICT/OPERATION_ID_REUSED`; revisión obsoleta devuelve `CONFLICT/REVISION_MISMATCH` sin mutación.
5. Cada transición aceptada incrementa `revision` y publica su cambio de sync en la misma transacción. Rechazos/fallos no dejan estado parcial.
6. El contrato de este epic no crea `recurrence_occurrences`, transacciones, ledger, presupuesto ni notificaciones; estas últimas se entregan/deduplican en HU-44.
7. `status` es la fuente de verdad del lifecycle. La columna legacy `is_active` solo se proyecta en lectura con `ACTIVE=true` y `PAUSED`/`CANCELLED=false`; no se acepta como input de comando. Retenerla hasta inventario autorizado sin lectores legacy y retirarla solo mediante una migración posterior revisada.

## 2. Envelope v1

Los nombres son propuestos y pueden cambiar antes del primer despliegue, nunca silenciosamente después.

### Create

```json
{
  "command_type": "CREATE_RECURRENCE",
  "contract_version": 1,
  "operation_id": "<stable UUID>",
  "request_hash": "<lowercase 64-char SHA-256>",
  "recurrence_id": "<client UUID>",
  "payload": {
    "amount_mode": "FIXED",
    "custom_interval_unit": null,
    "end_date": null,
    "frequency": "MONTHLY",
    "interval_count": 1,
    "next_due_date": "2026-10-31",
    "reminder_lead_days": 2,
    "start_date": "2026-10-31",
    "template_transaction": {
      "category_id": "018f47d9-7b6a-7c01-9b14-8d40c2a8a002",
      "concept": "Alquiler",
      "currency_code": "PEN",
      "destination_account_id": null,
      "merchant_id": null,
      "merchant_provisional_text": null,
      "source_account_id": "018f47d9-7b6a-7c01-9b14-8d40c2a8a003",
      "type": "EXPENSE",
      "amount_minor": 125000
    }
  }
}
```

`expected_revision` no se envía en CREATE. El ID de regla se genera en el cliente y permanece igual durante los reintentos.

`payload.reminder_lead_days` es un entero requerido no nulo con rango inclusivo 0–30. CREATE y cada UPDATE de configuración deben incluirlo; en UPDATE se reenvía el valor vigente si no cambia y solo se sustituye si la persona lo modifica. El cliente resuelve el default 2 al crear; en UPDATE conserva/reenvía el valor configurado, y el servidor no introduce defaults. Omitir la clave o enviar `null` se rechaza antes de aceptar el hash. El valor forma parte de bytes canónicos. SET_STATUS no lleva este campo.

### Update and lifecycle

```json
{
  "command_type": "UPDATE_RECURRENCE",
  "contract_version": 1,
  "operation_id": "<stable UUID>",
  "request_hash": "<lowercase 64-char SHA-256>",
  "recurrence_id": "<UUID>",
  "expected_revision": 7,
  "payload": { "end_date": "2027-10-31", "reminder_lead_days": 2 }
}
```

- `UPDATE_RECURRENCE` carries the explicitly editable recurrence fields.
- `SET_RECURRENCE_STATUS` carries a target state from the HU-35 lifecycle. To resume from `PAUSED`, the client first calculates and shows the first future valid date; only after the person explicitly confirms it may the client emit `status=ACTIVE`, with that exact date in required `payload.confirmed_next_due_date`. The server recomputes/validates that it is the first future valid date and rejects a missing or mismatched value; no command is emitted/applied before confirmation and no expenses/payments/occurrences are backfilled (DP-REC-02). `CANCELLED` may be sent only after the UI's explicit confirmation (FR-009) and is terminal; reuse requires a new/cloned rule (approved DP-REC-04).
- The server validates the allowed transition/current state; it does not trust a client-computed next revision.
- Reject unknown fields/command-specific fields rather than silently ignoring a newer client payload.

### Closed v1 field schemas

Every object below is validated against its allowlist after JSONB parsing: reject unknown keys, invalid types and invalid values. The RPC handler cannot detect or reject duplicate member names in the original JSON text because PostgreSQL `jsonb` has already collapsed them; if duplicate names are supplied, `jsonb` keeps only the last value. This contract therefore validates and hashes the resulting semantic object and makes no promise of lexical duplicate-key rejection (TECH-REC-JSON-01). Clients MUST construct and emit duplicate-free command objects. `operation_id` and `recurrence_id` are canonical lowercase hyphenated UUID strings; `request_hash` is 64 lowercase hex characters. Common envelope keys are exactly `command_type`, `contract_version`, `operation_id`, `request_hash`, `recurrence_id`, and, for UPDATE/SET_STATUS only, `expected_revision`; `contract_version` is exactly integer 1. No command accepts `user_id`, `revision`, `status` outside SET_STATUS, or `is_active`. `operation_id` and `request_hash` are excluded only from canonical hash input.

The database-side tests exercise the real `jsonb` interface by casting duplicate-member JSON text to `jsonb` before invoking the RPC. For example, `'{"frequency":"MONTHLY","frequency":"WEEKLY"}'::jsonb` has the semantic value `{"frequency":"WEEKLY"}`; tests assert validation/hash behavior for that resulting value, not rejection of the discarded lexical member. Kotlin tests assert that command serialization emits unique member names.

| Command | Exact payload keys / presence | Rules |
|---|---|---|
| `CREATE_RECURRENCE` | Required: `amount_mode`, `custom_interval_unit`, `end_date`, `frequency`, `interval_count`, `next_due_date`, `reminder_lead_days`, `start_date`, `template_transaction`. No extra keys. | Server initializes `status=ACTIVE`, `revision=1`; these are not client inputs. Nullable values use explicit JSON `null`. No server defaults. |
| `UPDATE_RECURRENCE` | Required non-null `reminder_lead_days` plus a non-empty subset of `template_transaction`, `amount_mode`, `frequency`, `interval_count`, `custom_interval_unit`, `end_date`, `next_due_date`. Omitted optional patch key means unchanged; unknown keys reject. A supplied `template_transaction` replaces the complete nested object; every nested key is required. | `null` clears only nullable `end_date`, `custom_interval_unit` when non-CUSTOM, and nullable template references/text. Other supplied fields are non-null. Validate merged stored rule. Any cadence change requires `frequency`, `interval_count`, `custom_interval_unit` together plus `next_due_date`; `start_date` is immutable. `reminder_lead_days` is mandatory on every UPDATE, including when unchanged; it cannot be null. Reject no-op configuration updates. |
| `SET_RECURRENCE_STATUS` | Pause: exactly `{"status":"PAUSED"}`. Cancel: exactly `{"status":"CANCELLED"}`. Resume: exactly `{"status":"ACTIVE","confirmed_next_due_date":"YYYY-MM-DD"}`. | No extra keys or nulls. Resume requires explicit user confirmation before command construction; server recomputes and validates the first future schedule date. Cancel confirmation is a UI gate; no boolean can substitute for it in the payload. |

`template_transaction` has exactly these required keys: `concept` (non-empty string), `type` (`EXPENSE`, `INCOME`, or `TRANSFER`), `amount_minor` (exact integer 1..9223372036854775807), `currency_code` (uppercase supported three-letter currency code), `source_account_id`, `destination_account_id`, `category_id`, `merchant_id`, and `merchant_provisional_text`. The final five reference/text fields are always present and may be JSON `null`; non-null IDs are canonical UUID strings. `merchant_id` and `merchant_provisional_text` are mutually exclusive. The schema intentionally excludes movement IDs, owner IDs, event/occurred timestamps, status, operation metadata, card/installment/interest fields, payment channel, ledger values and occurrence data. `concept` is the recurring-template label described by P19; when created from a movement, the user may edit the seeded value. Canonical envelope bytes, including escaped control characters, are limited to 16,384 UTF-8 bytes.

Reference rules follow P19 §4.1 and the current movement domain (`MovementModels.kt:92-121`): accounts, categories and merchant references are checked server-side under `auth.uid()`; account currency must match `currency_code`; EXPENSE requires an eligible source account and category; INCOME requires an eligible source account; TRANSFER requires distinct eligible source and destination accounts. Non-applicable account/category references are explicit null. Merchant catalog references must be visible/eligible; provisional merchant text is used only without a catalog merchant. These checks do not create a movement or financial effect.

`frequency` is exactly `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `ANNUALLY`, or `CUSTOM`; `amount_mode` is exactly `FIXED` or `APPROXIMATE`. Date values are strict Gregorian `YYYY-MM-DD` strings in the inclusive common implementation range `0001-01-01`..`9999-12-31`; invalid dates, noncanonical date strings and out-of-range arithmetic reject. For built-in frequencies, `interval_count` is required integer 1 and `custom_interval_unit` is required `null`; CUSTOM requires unit `WEEK` or `MONTH` and integer `interval_count` in [1,5200] or [1,1200], respectively. Client supplies `1` for built-in cadence; server never fills it. `start_date` is the immutable calendar anchor after creation; `end_date` is required and nullable on CREATE, and when non-null is >= `start_date`; `next_due_date` is required and must belong to the sequence from the anchor and not exceed `end_date`. A cadence update must include `frequency`, `interval_count`, `custom_interval_unit`, and a schedule-valid `next_due_date`. Calculation is from the original anchor (month-end clamp does not drift); overflow or a candidate beyond the common date range rejects.

All payload strings must be valid Unicode scalar sequences representable by JSONB; U+0000/unpaired surrogates are rejected. Field-specific canonical UUID/enum/date rules apply before hashing. Full schema, reference, and numeric validation MUST precede digest acceptance, receipt lookup, revision/idempotency processing, and any mutation.

## 3. Exact idempotency and canonical hash (v1 proposal)

The server verifies `request_hash`; it must not trust an arbitrary client digest. The v1 byte rule is aligned with the existing Kipu goal command pattern:

1. Start from the complete envelope JSON object after it has been parsed into the RPC's `jsonb` argument. Raw source spelling, whitespace, key order and duplicate member occurrences are not part of the hash input.
2. Remove exactly the top-level keys `operation_id` and `request_hash`; retain `command_type`, `contract_version`, `recurrence_id`, `expected_revision` when applicable, and the entire `payload`.
3. Canonicalize recursively:
   - Objects: sort keys by UTF-8 byte order equivalent to PostgreSQL `COLLATE "C"` (v1 keys are allowlisted ASCII names), quote keys as JSON strings, emit `key:value` pairs separated by commas, no spaces.
   - Arrays: preserve input order and emit comma-separated elements with no spaces. V1 command payloads currently define no arrays; any later array field must specify whether order is semantic and have new golden vectors.
   - Scalars: booleans are lowercase `true`/`false`, and null is `null`. Numbers use exact semantic decimal normalization from JSONB as defined above, not raw token spelling or bare `jsonb::text`. For strings, quote with `"`, escape backslash as `\\`, leave `/` unescaped, use short escapes `\\b`, `\\t`, `\\n`, `\\f`, `\\r` for U+0008/U+0009/U+000A/U+000C/U+000D, and `\\u00xx` (lowercase hex) for every other U+0001..U+001F. Emit every other valid Unicode scalar literally as UTF-8, including non-ASCII, U+2028 and U+2029. Emit no BOM; do not normalize Unicode or case-fold. Reject U+0000 and invalid/unpaired surrogates before digest acceptance. Equivalent input spellings that decode to the same JSONB string have identical canonical bytes; NFC and NFD remain distinct. Kotlin and SQL must implement this explicit algorithm, not rely on platform JSON serializers by assumption.
   - Before accepting/verifying the digest or mutating state, validate every numeric path admitted by that command schema as an exact mathematical integer and within its field range: `contract_version = 1`; `expected_revision`/stored `revision` in [1, 9223372036854775807]; `payload.reminder_lead_days` in [0,30]; built-in `payload.interval_count = 1`; CUSTOM weekly count [1,5200] or monthly count [1,1200]; `payload.template_transaction.amount_minor` in [1,9223372036854775807]. Reject fractional values, out-of-range values, non-number types, and all numeric paths not allowlisted by the command schema. Decimal/exponent spelling is not itself an error when its exact value is an in-range integer. Invalid candidates are rejected before hash acceptance and produce no write.
   - Enums use exact uppercase ASCII; dates exactly valid `YYYY-MM-DD`; UUIDs lowercase canonical hyphenated form; currency codes uppercase.
   - For CREATE and SET_STATUS, every schema key is required; nullable values are explicit JSON `null`. UPDATE is a non-empty partial patch except `reminder_lead_days`, which is required/non-null on every UPDATE; other omitted keys mean unchanged and `null` clears only fields permitted by the closed schema. These forms are distinct canonical commands; the server validates the merged result after parsing the patch.
   - `reminder_lead_days` is required/non-null on CREATE and every UPDATE, range-checked 0–30, and included in canonical bytes. The client resolves default 2 on CREATE; UPDATE re-sends the configured current value unless changed by the person. Missing or `null` is invalid, not a defaultable canonical form.
   - Resume payloads with `status=ACTIVE` require `confirmed_next_due_date`, exactly the first future valid date that the person reviewed and confirmed. The client creates this command only after confirmation; the server validates the date and rejects absent/incorrect dates.
4. SHA-256 the UTF-8 bytes of the canonical JSON string. `request_hash` is exactly 64 lowercase hexadecimal characters. The hash excludes only `operation_id` and `request_hash` themselves.
5. Before the protocol is accepted, Kotlin unit tests and SQL contract tests must prove matching canonical bytes/digests for golden vectors, object-key insertion-order invariance, retry identity, changed-field inequality, explicit-null/patch semantics, and escaping/Unicode scalars (controls, slash, quote, backslash, non-ASCII, U+2028/U+2029, NFC/NFD). Canonicalization changes require a new `contract_version`.

### Golden vectors for v1

These expected digests were computed from the displayed UTF-8 canonical bytes with Python's `hashlib.sha256`; they are contract fixtures, not evidence that the current Android/PostgreSQL implementations already agree. Implementation acceptance requires both Kotlin and PostgreSQL test paths to reproduce the exact bytes and digest. The explicitly marked partial CREATE fragments below are canonicalizer-only oracles: they intentionally omit required schema keys, are not valid commands, and no request digest may be accepted for them. Duplicate-member source text is not a golden hash input: its test expectation is the single semantic JSONB value retained by PostgreSQL, which is then validated/canonicalized under this section.

| Case | Canonical bytes | SHA-256 |
|---|---|---|
| Lifecycle: `PAUSED` | `{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":7,"payload":{"status":"PAUSED"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `0085caad3e569114dbd6399a53d9d667384fbbc65b767664ec19efeccffcc67c` |
| Same semantic object with a different insertion order | Same canonical bytes and digest as the `PAUSED` vector. | `0085caad3e569114dbd6399a53d9d667384fbbc65b767664ec19efeccffcc67c` |
| Resume after explicit confirmation of `2026-10-20` | `{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":7,"payload":{"confirmed_next_due_date":"2026-10-20","status":"ACTIVE"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `be94fc8ae436a2746aeb486f7aa5509ca382363c4ee634cbc5b97753a1c1fe0f` |
| Explicit-null optional `end_date` | `{"command_type":"CREATE_RECURRENCE","contract_version":1,"payload":{"end_date":null,"frequency":"MONTHLY"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `59c7ccef28123bb7ebad8d5ec5d05ffc73303f72b8415dc9a3f2291b56e2f508` |
| String escaping/Unicode: `concept` contains `á`, quote, backslash, newline | `{"command_type":"CREATE_RECURRENCE","contract_version":1,"payload":{"amount_mode":"APPROXIMATE","template_transaction":{"concept":"á\"\\\n","currency_code":"PEN"}},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `d9eb9ed360f0ccc4eabc49f9525a2996aaef8fffc8310ed7098ab4579c39e2eb` |
| Create with normalized `reminder_lead_days=2` | `{"command_type":"CREATE_RECURRENCE","contract_version":1,"payload":{"amount_mode":"APPROXIMATE","reminder_lead_days":2,"template_transaction":{"concept":"x","currency_code":"PEN"}},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `b89a573522265c85c6b348689502f7bb8b81eefcdd1f13d597ab9a1434e2542c` |
| Change `reminder_lead_days` from 2 to 3 | `{"command_type":"CREATE_RECURRENCE","contract_version":1,"payload":{"amount_mode":"APPROXIMATE","reminder_lead_days":3,"template_transaction":{"concept":"x","currency_code":"PEN"}},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `649fac8789974eff2499a5b3617cc78c91874c5735e5306cde36b00c9ee334bb` |
| Same resume with a changed confirmed date (`2026-10-21`) | `{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":7,"payload":{"confirmed_next_due_date":"2026-10-21","status":"ACTIVE"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `53f62e8698674eb3a278c8bc78cf4213be389d97a9950faf51c309b98ec8ce27` |
| Numeric integer: `expected_revision` token `100` | Validation PASS. Canonical bytes: `{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":100,"payload":{"status":"PAUSED"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}` | `55ee6fee2909de6a4e72bb75956be25eb91f59e77f2339c33072e63fb2d6bbf9` |
| Numeric decimal fraction: `expected_revision` token `100.5` | Validation REJECT (`expected_revision` is not integral). Canonicalizer-only oracle bytes: `{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":100.5,"payload":{"status":"PAUSED"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}`; not an acceptable request hash and no write. | `77f2ad90c94efffbdbf0d2c4e5d1addb8f2c72497d303f3457e8ac76e8da16cb` |
| Numeric integer-equivalent exponent: `expected_revision` token `1e2` | Validation PASS; normalizes to `100`, with exactly the integer vector's canonical bytes. | `55ee6fee2909de6a4e72bb75956be25eb91f59e77f2339c33072e63fb2d6bbf9` |
| Numeric non-integer exponent: `expected_revision` token `1e-1` | Validation REJECT (`expected_revision` is not integral). Canonicalizer-only oracle bytes: `{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":0.1,"payload":{"status":"PAUSED"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}`; not an acceptable request hash and no write. | `4646527efbb6cc97cf3aa24224e6a055848a24024d9521a0c3dbb5d21f24adf1` |
| Numeric integral decimal: `expected_revision` token `100.00` | Validation PASS; normalizes to `100`, with exactly the integer vector's canonical bytes. | `55ee6fee2909de6a4e72bb75956be25eb91f59e77f2339c33072e63fb2d6bbf9` |
| Omit `end_date` where the command schema defines it | Invalid command; no digest is accepted. Omission is not interchangeable with the explicit-null vector. | N/A |
| Omit or set `reminder_lead_days` to `null` | Invalid command; rejected before hashing. | N/A |
| Resume with `status=ACTIVE` but no `confirmed_next_due_date` | Invalid command; no resume mutation is accepted. | N/A |

For the rejected numeric candidates above, the displayed digest is solely an oracle for the pure canonicalizer; schema validation must reject before digest acceptance/side effects. These vectors do not assert Kotlin/PostgreSQL parity has been run. The exponent/decimal examples describe semantic JSONB values, not validation of original token spelling.

#### Complete schema-valid CREATE envelope

This input is the hash envelope after excluding `operation_id` and `request_hash`; all CREATE and nested template keys are present. Its `concept` exercises literal non-ASCII, quote, backslash, slash, newline, U+0001, U+2028 and U+2029. The source JSON uses equivalent escape spellings for controls/separators; canonical output follows §3. Expected canonical UTF-8 length is 629 bytes.

```json
{
  "command_type": "CREATE_RECURRENCE",
  "contract_version": 1,
  "recurrence_id": "018f47d9-7b6a-7c01-9b14-8d40c2a8a001",
  "payload": {
    "amount_mode": "APPROXIMATE",
    "custom_interval_unit": null,
    "end_date": null,
    "frequency": "MONTHLY",
    "interval_count": 1,
    "next_due_date": "2026-10-31",
    "reminder_lead_days": 2,
    "start_date": "2026-10-31",
    "template_transaction": {
      "amount_minor": 125000,
      "category_id": "018f47d9-7b6a-7c01-9b14-8d40c2a8a002",
      "concept": "á\"\\/\n\u0001\u2028\u2029",
      "currency_code": "PEN",
      "destination_account_id": null,
      "merchant_id": null,
      "merchant_provisional_text": null,
      "source_account_id": "018f47d9-7b6a-7c01-9b14-8d40c2a8a003",
      "type": "EXPENSE"
    }
  }
}
```

With `reminder_lead_days=2`, SHA-256 is `8a4dc1abcca8607b0bd4df1f36ec3e1768bbae179a4971c151465a893ccf029e`; changing only that field to `3` produces `a69a98f0872bc2fd3300744e8463360326314fbfc7dcaf6100abe006453a89f1`. These are full-envelope hash oracles calculated from the specified canonical bytes, not Kotlin/PostgreSQL parity evidence.

### String-encoder golden vectors

The following hashes are SHA-256 of the exact UTF-8 bytes shown in hexadecimal for the canonical JSON string token alone. They pin the string encoder; they are not complete command request hashes. A rejected scalar (U+0000 or an unpaired surrogate) has no canonical bytes and cannot be accepted for hashing.

| Logical string value / input equivalence | Canonical token UTF-8 bytes (hex) | SHA-256 |
|---|---|---|
| `é`, quote, backslash, slash, U+0008, U+000C, U+000A, U+000D, U+0009, U+0001, U+001F | `22c3a95c225c5c2f5c625c665c6e5c725c745c75303030315c753030316622` | `982f93929969c10fd43f868cf05150ec7e954d105e43e283fe69cfb6b305f16e` |
| Slash supplied as `/` or JSON escape `\/` | `222f22` | `df7e940f72aa93cbcb6d70cc35124b0767c7a0e353043c7c24b660ea9be7b952` |
| U+00E9 supplied as literal `é` or JSON escape `\u00e9` | `22c3a922` | `f2886017e9c7abacf804b54d64787dce2b611c9544ba21f3affdd126a6e50086` |
| U+2028 followed by U+2029 (both emitted literally, not as `\\u2028`/`\\u2029`) | `22e280a8e280a922` | `e3a0e2262ac7790f4df36b522a8fbdd4ee3370d568eb15dc25c5f1775f14ed6c` |
| NFC `é` (no normalization) | `22c3a922` | `f2886017e9c7abacf804b54d64787dce2b611c9544ba21f3affdd126a6e50086` |
| NFD `e` + U+0301 (distinct bytes from NFC) | `2265cc8122` | `3d68ce21f2899a475713cdbe7562ba9bdb6b1dfde8af1f221bdff4a0935b53b2` |

Before implementation acceptance, Kotlin unit tests and PostgreSQL SQL tests must exercise these exact byte fixtures plus complete envelope digests, including escape-equivalent source tokens, all C0 controls, U+2028/U+2029, raw non-ASCII and NFC/NFD distinction. They must establish parity; these documented fixtures do not establish it.

## 4. Conceptual commands

| Command | Required input | Behavior |
|---|---|---|
| `CREATE_RECURRENCE` | Stable recurrence ID, required template/configuration, start and valid next date, initial ACTIVE state, operation identity | Validate owner-scoped references and fields; insert one rule/revision 1 and one receipt/feed item atomically. Does not mutate source movement or create payment/occurrence. |
| `UPDATE_RECURRENCE` | Recurrence ID, expected revision, explicitly allowed fields | Validate state/editability and date/calendar invariants; update one rule/revision; no history or finance side effects. |
| `SET_RECURRENCE_STATUS` | Recurrence ID, expected revision, target state; `confirmed_next_due_date` for resume; confirmed user intent for cancel | Validate transition and confirmation protocol; pause/cancel suppress future HU-44 notices at the data boundary; resume accepts only the explicitly confirmed first future valid date, increments revision, and preserves history. |

Callable RPC names `create_recurrence_v1`, `update_recurrence_v1` and `set_recurrence_status_v1` were approved as part of the local contract in T003's read-only architecture review (`t_a0fdee9b`). Runtime implementation/parity tests are still required before claiming interoperability. Authorized hosted-schema inventory is a separate gate required before any hosted migration/deployment; it does not imply local inspection authorizes remote changes. Product decisions are resolved; lead-time-zero delivery remains for EP-NOT/HU-44.

## 5. Responses

- **APPLIED**: authoritative recurrence fields and revision, plus operation receipt.
- **DUPLICATE**: original receipt/result with status marked duplicate; no repeated mutation.
- **CONFLICT/REVISION_MISMATCH**: current revision/state is newer; no write from this command.
- **CONFLICT/OPERATION_ID_REUSED**: same operation ID was already bound to a different command type/hash/payload; no write.
- **REJECTED/INVALID_COMMAND or VALIDATION_FAILED**: missing/invalid field, unsupported transition/frequency/date, or hash mismatch; no write.
- **REJECTED/OWNER_OR_REFERENCE_INVALID**: unauthorized or incompatible linked object; do not reveal existence of another user's data.
- **RETRYABLE**: transient failure before commit; retry only the same immutable command with backoff.

The exact error-code list is a proposal and must be made stable together with the RPC contract before implementation merges.

## 6. Persistence, RLS, and privileges

- Rule and command receipt use owner-scoped keys; cross-owner foreign keys/references are constrained in the database, not only the UI.
- Enforce RLS on exposed rows; RPC repeats owner/revision checks. Prefer `SECURITY INVOKER`; if `SECURITY DEFINER` is necessary, pin `search_path`, revoke public/anon execution and grant only to the authenticated role after a security review.
- State, revision, sync feed and receipt are one DB transaction. Do not use cascade deletion to remove a recurrence's financial history; this feature does not implement delete.
- Migration must be forward-only and tested from a fresh local DB and against an authorized schema inventory before hosted use. Local ignored baseline files are not authoritative. No hosted migration/deployment is authorized by this document.

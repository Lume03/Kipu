BEGIN;
SELECT plan(12);

CREATE TEMP TABLE debt_settlement_ids (
    owner_id uuid,
    other_id uuid,
    account_id uuid,
    foreign_account_id uuid,
    expense_category_id uuid,
    income_category_id uuid,
    payable_debt_id uuid,
    receivable_debt_id uuid,
    installment_id uuid,
    payable_operation_id uuid,
    receivable_operation_id uuid,
    request_payload jsonb,
    void_payload jsonb
);
INSERT INTO debt_settlement_ids VALUES (
    '67000000-0000-4000-8000-000000000001',
    '67000000-0000-4000-8000-000000000002',
    '67000000-0000-4000-8000-000000000003',
    '67000000-0000-4000-8000-000000000004',
    '67000000-0000-4000-8000-000000000005',
    '67000000-0000-4000-8000-000000000006',
    '67000000-0000-4000-8000-000000000007',
    '67000000-0000-4000-8000-000000000008',
    '67000000-0000-4000-8000-000000000009',
    '67000000-0000-4000-8000-000000000010',
    '67000000-0000-4000-8000-000000000011',
    NULL, NULL
);
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@debt-settlement.kipu.test' FROM debt_settlement_ids
UNION ALL
SELECT other_id, other_id::text || '@debt-settlement.kipu.test' FROM debt_settlement_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
SELECT account_id, owner_id, 'Owner cash', 'SAVINGS'::public.account_type, 'PEN', 'GENERIC' FROM debt_settlement_ids
UNION ALL
SELECT foreign_account_id, other_id, 'Other cash', 'SAVINGS'::public.account_type, 'PEN', 'GENERIC' FROM debt_settlement_ids;
INSERT INTO public.categories (id, user_id, name, category_type, origin, is_active)
SELECT expense_category_id, owner_id, 'Debt interest', 'EXPENSE', 'CUSTOM', true FROM debt_settlement_ids
UNION ALL
SELECT income_category_id, owner_id, 'Other income', 'INCOME', 'CUSTOM', true FROM debt_settlement_ids;
INSERT INTO public.debts (
    id, user_id, obligation_type, counterparty_name, total_minor, currency_code,
    opened_on, opening_mode, status, revision
)
SELECT payable_debt_id, owner_id, 'PAYABLE'::public.obligation_type, 'Supplier', 10000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1
FROM debt_settlement_ids
UNION ALL
SELECT receivable_debt_id, owner_id, 'RECEIVABLE'::public.obligation_type, 'Friend', 10000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1
FROM debt_settlement_ids;
INSERT INTO public.debt_installments (id, user_id, debt_id, installment_number, due_date, amount_minor)
SELECT installment_id, owner_id, payable_debt_id, 1, DATE '2026-11-01', 5000 FROM debt_settlement_ids;

UPDATE debt_settlement_ids d
SET request_payload = jsonb_build_object(
    'contract_version', 1,
    'operation_id', payable_operation_id,
    'debt_id', payable_debt_id,
    'expected_revision', 1,
    'account_id', account_id,
    'principal_minor', 2000,
    'interest_minor', 100,
    'interest_category_id', expense_category_id,
    'installment_id', installment_id,
    'occurred_at', '2026-10-08T10:00:00Z',
    'notes', 'First installment'
);
UPDATE debt_settlement_ids d
SET request_payload = d.request_payload || jsonb_build_object(
    'request_hash', encode(extensions.digest(d.request_payload::text, 'sha256'), 'hex')
);
UPDATE debt_settlement_ids d
SET void_payload = (
    SELECT body.command || jsonb_build_object(
        'request_hash', internal.movement_revision_hash_v1(body.command)
    )
    FROM (
    SELECT jsonb_build_object(
        'contract_version', 1,
        'command_type', 'VOID_TRANSACTION',
        'idempotency_key', '67000000-0000-4000-8000-000000000012',
        'transaction_id', md5('kipu:debt-interest:' || d.payable_operation_id::text)::uuid,
        'expected_revision', 1,
        'depends_on_command_id', NULL,
        'reason', 'Correct debt settlement',
        'revised_payload', NULL
    ) AS command
    ) body
);
GRANT SELECT ON debt_settlement_ids TO authenticated;


SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM debt_settlement_ids), true);
SELECT is((SELECT public.close_debt_v1(jsonb_build_object(
 'contract_version',1,'operation_id','67000000-0000-4000-8000-000000000040','debt_id',receivable_debt_id,
 'expected_revision',1,'action','FORGIVE','amount_minor',100,'reason','Offline record',
 'occurred_at','2026-10-05T15:45:00.123Z','request_hash',repeat('f',64)))->>'status' FROM debt_settlement_ids),
 'APPLIED','offline forgiveness applies');
SELECT is((SELECT occurred_at FROM public.debt_events WHERE debt_id=(SELECT receivable_debt_id FROM debt_settlement_ids)),
 '2026-10-05T15:45:00.123Z'::timestamptz,'offline forgiveness retains the exact event timestamp');
SELECT throws_ok($$SELECT public.close_debt_v1(jsonb_build_object(
 'contract_version',1,'operation_id','67000000-0000-4000-8000-000000000041','debt_id',receivable_debt_id,
 'expected_revision',2,'action','FORGIVE','amount_minor',100,'reason','Future',
 'occurred_at',now()+interval '1 day','request_hash',repeat('a',64))) FROM debt_settlement_ids$$,
 '22023','INVALID_CLOSURE_DATE','future closure rejected');
SELECT throws_ok($$SELECT public.close_debt_v1(jsonb_build_object(
 'contract_version',1,'operation_id','67000000-0000-4000-8000-000000000042','debt_id',receivable_debt_id,
 'expected_revision',2,'action','ADJUST','principal_delta_minor',100,'reason','Before opening',
 'occurred_at','2026-09-30T23:59:59Z','request_hash',repeat('b',64))) FROM debt_settlement_ids$$,
 '22023','INVALID_CLOSURE_DATE','closure before opening rejected');
RESET ROLE;
UPDATE public.debt_installments SET status='CANCELLED' WHERE id=(SELECT installment_id FROM debt_settlement_ids);
SET LOCAL ROLE authenticated;
SELECT throws_ok($$SELECT public.settle_debt_v1(request_payload) FROM debt_settlement_ids$$,
 'P0001','INSTALLMENT_CANCELLED','cannot pay cancelled installment');
SELECT is((SELECT count(*)::int FROM public.transactions WHERE user_id=(SELECT owner_id FROM debt_settlement_ids)),0,'rejected payment creates no movement');
RESET ROLE;
UPDATE public.debt_installments SET status='PENDING' WHERE id=(SELECT installment_id FROM debt_settlement_ids);
SET LOCAL ROLE authenticated;
SELECT is((SELECT public.settle_debt_v1(request_payload)->>'status' FROM debt_settlement_ids),'APPLIED','valid payment still applies');
SELECT is((SELECT public.void_transaction_v1(void_payload)->>'status' FROM debt_settlement_ids),'APPLIED','interest void applies to group');
RESET ROLE;
UPDATE debt_settlement_ids SET void_payload=void_payload || jsonb_build_object(
 'idempotency_key','67000000-0000-4000-8000-000000000043','transaction_id',payable_operation_id);
UPDATE debt_settlement_ids SET void_payload=void_payload || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(void_payload));
SET LOCAL ROLE authenticated;
SELECT is((SELECT public.void_transaction_v1(void_payload)->>'result' FROM debt_settlement_ids),'ALREADY_VOIDED','other device with stale principal revision succeeds idempotently');
SELECT is((SELECT public.void_transaction_v1(void_payload)->>'status' FROM debt_settlement_ids),'DUPLICATE','same void command replay is idempotent');
SELECT is((SELECT count(*)::int FROM public.transactions WHERE user_id=(SELECT owner_id FROM debt_settlement_ids) AND status='VOIDED'),2,'both members remain voided');
RESET ROLE;
SELECT is((SELECT sum(signed_amount_minor)::bigint FROM internal.ledger_entries WHERE user_id=(SELECT owner_id FROM debt_settlement_ids)),0::bigint,'group reversal never duplicates cash effects');
SELECT * FROM finish();
ROLLBACK;

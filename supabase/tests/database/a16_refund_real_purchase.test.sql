BEGIN;
SELECT no_plan();

INSERT INTO auth.users (id, email) VALUES
    ('99000000-0000-0000-0000-000000000001', 'overutil-owner@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts(id, user_id, name, account_type, currency_code) VALUES
(internal.credit_liability_identity_v1('account', '99000000-0000-0000-0000-000000000001'::uuid, '99000000-0000-0000-0000-000000000002'::uuid),
 '99000000-0000-0000-0000-000000000001', 'Credit liability fixture', 'CREDIT_LIABILITY', 'PEN');

INSERT INTO public.cards (id, user_id, creation_operation_id, network, alias, last4, is_credit, credit_limit_minor, closing_day, due_day, created_at, updated_at, account_id, currency_code, issuer, closing_day_inclusive)
VALUES (
    '99000000-0000-0000-0000-000000000002',
    '99000000-0000-0000-0000-000000000001',
    '99000000-0000-0000-0000-000000000010',
    'VISA', 'Tarjeta Overutil', '9999', true, 10000, 20, 10, now(), now(),
    internal.credit_liability_identity_v1('account', '99000000-0000-0000-0000-000000000001'::uuid, '99000000-0000-0000-0000-000000000002'::uuid),
    'PEN', 'BCP', true
);

INSERT INTO public.categories (id, user_id, parent_id, name, origin, is_active, remote_revision, category_type)
VALUES ('99000000-0000-0000-0000-000000000003', '99000000-0000-0000-0000-000000000001', NULL, 'Compras', 'CUSTOM', true, 1, 'EXPENSE');

SET LOCAL request.jwt.claim.sub = '99000000-0000-0000-0000-000000000001';
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', '99000000-0000-0000-0000-000000000011',
        'request_hash', 'dummy-hash-1',
        'transaction', jsonb_build_object(
            'id', '99000000-0000-0000-0000-000000000011',
            'type', 'EXPENSE',
            'operation_kind', 'CARD_PURCHASE',
            'amount_minor', 400,
            'currency_code', 'PEN',
            'source_account_id', NULL,
            'card_id', '99000000-0000-0000-0000-000000000002',
            'category_id', '99000000-0000-0000-0000-000000000003',
            'merchant_provisional_text', 'First Purchase',
            'occurred_at', '2026-09-20T12:00:00Z',
            'installment_count', 1,
            'rounding_policy', 'FIRST_INSTALLMENT_RESIDUAL',
            'interest_mode', 'NONE'
        )
    ))->>'status',
    'APPLIED',
    'Initial purchase under limit is accepted'
);



RESET ROLE;
CREATE TEMP TABLE refund_wire AS
SELECT jsonb_build_object('contract_version',1,
'idempotency_key','99000000-0000-0000-0000-000000000021',
'purchase_transaction_id','99000000-0000-0000-0000-000000000011',
'refund_transaction',jsonb_build_object('id','99000000-0000-0000-0000-000000000022',
'amount_minor',100,'currency_code','PEN','occurred_at','2026-09-20T12:01:00Z')) AS body;
-- Literal wire hash is cross-checked by the JVM factory contract test.
UPDATE refund_wire SET body=body||jsonb_build_object('request_hash','1862cc6e047a19c7813c7a2d15fac94a84390aeb36a041544338437847863e42');
SELECT set_config('request.jwt.claim.sub','99000000-0000-0000-0000-000000000001',true);
SELECT is(public.refund_transaction_v1((SELECT body FROM refund_wire))->>'status','APPLIED','real RPC purchase supports app partial refund');
SELECT is(public.refund_transaction_v1((SELECT body FROM refund_wire))->>'status','DUPLICATE','retry creates no second refund');
SELECT is((SELECT count(*) FROM public.credit_installments WHERE transaction_id='99000000-0000-0000-0000-000000000011'),1::bigint,'real registration creates one installment');
SELECT ok((SELECT account_id IS NULL FROM public.transactions WHERE id='99000000-0000-0000-0000-000000000011'),'canonical purchase account stays null');
SELECT is((SELECT signed_amount_minor FROM internal.ledger_entries WHERE transaction_id='99000000-0000-0000-0000-000000000022'),100::bigint,'refund liability ledger credits exactly 100');
SELECT is((SELECT account_id FROM internal.ledger_entries WHERE transaction_id='99000000-0000-0000-0000-000000000022'),(SELECT account_id FROM public.cards WHERE id='99000000-0000-0000-0000-000000000002'),'refund posts to owned card liability');
SELECT is((SELECT count(*) FROM public.credit_payment_allocations WHERE user_id=auth.uid()),0::bigint,'registration creates no payment allocations');
SELECT is((SELECT count(*) FROM public.debt_events WHERE user_id=auth.uid()),0::bigint,'registration creates no debt events');
-- Error branches do not depend on synthetic purchase INSERTs either.
CREATE FUNCTION pg_temp.refund_variant(p_patch jsonb, p_refund_patch jsonb DEFAULT '{}'::jsonb) RETURNS jsonb LANGUAGE sql AS $$
WITH c AS (SELECT (body || jsonb_build_object('idempotency_key',extensions.gen_random_uuid()::text) || p_patch)
 || jsonb_build_object('refund_transaction',(body->'refund_transaction') || jsonb_build_object('id',extensions.gen_random_uuid()::text) || p_refund_patch) AS body FROM refund_wire)
SELECT body || jsonb_build_object('request_hash',internal.refund_transaction_hash_v1(body)) FROM c;
$$;
SELECT is(public.refund_transaction_v1(pg_temp.refund_variant('{}','{"amount_minor":401}'))#>>'{error,code}','REFUND_EXCEEDS_REMAINDER','excess rejected');
SELECT is(public.refund_transaction_v1(pg_temp.refund_variant('{}','{"occurred_at":"2026-09-19T12:00:00Z"}'))#>>'{error,code}','INVALID_REFUND_DATE','date before purchase rejected');
SELECT is(public.refund_transaction_v1(pg_temp.refund_variant('{"purchase_transaction_id":"99000000-0000-0000-0000-000000000099"}'))#>>'{error,code}','PURCHASE_NOT_FOUND','wrong local or remote purchase ID rejected distinctly');
SELECT is(public.refund_transaction_v1((SELECT body || jsonb_build_object('request_hash',repeat('0',64)) FROM refund_wire))#>>'{error,code}','INVALID_REQUEST_HASH','incorrect hash rejected distinctly');
SELECT * FROM finish();
ROLLBACK;

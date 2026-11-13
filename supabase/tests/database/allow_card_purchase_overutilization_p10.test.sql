BEGIN;
SELECT plan(6);

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

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '99000000-0000-0000-0000-000000000001';

-- 1. Initial purchase S/ 90 under S/ 100 limit is APPLIED
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', '99000000-0000-0000-0000-000000000011',
        'request_hash', 'dummy-hash-1',
        'transaction', jsonb_build_object(
            'id', '99000000-0000-0000-0000-000000000011',
            'type', 'EXPENSE',
            'operation_kind', 'CARD_PURCHASE',
            'amount_minor', 9000,
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

-- Production bug documented in SQL3-13: register_transaction_v1_pre_liability_s3 still enforces CREDIT_LIMIT_EXCEEDED
SELECT todo_start('Production bug: register_transaction_v1_pre_liability_s3 still rejects overutilization with CREDIT_LIMIT_EXCEEDED despite P10');

-- 2. Second purchase S/ 20 exceeding card limit (total S/ 110 > S/ 100) should be accepted (overutilization allowed)
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', '99000000-0000-0000-0000-000000000012',
        'request_hash', 'dummy-hash-2',
        'transaction', jsonb_build_object(
            'id', '99000000-0000-0000-0000-000000000012',
            'type', 'EXPENSE',
            'operation_kind', 'CARD_PURCHASE',
            'amount_minor', 2000,
            'currency_code', 'PEN',
            'source_account_id', NULL,
            'card_id', '99000000-0000-0000-0000-000000000002',
            'category_id', '99000000-0000-0000-0000-000000000003',
            'merchant_provisional_text', 'Overutil Purchase',
            'occurred_at', '2026-09-20T13:00:00Z',
            'installment_count', 1,
            'rounding_policy', 'FIRST_INSTALLMENT_RESIDUAL',
            'interest_mode', 'NONE'
        )
    ))->>'status',
    'APPLIED',
    'Overutilization purchase exceeding credit limit is APPLIED without error'
);

-- 3. Total outstanding principal across installments is 11000
SELECT is(
    (SELECT coalesce(sum(principal_minor), 0)::bigint FROM public.credit_installments WHERE user_id = auth.uid()),
    11000::bigint,
    'Total outstanding debt reflects full overutilized balance of 11000'
);

-- 4. Retrying the overutilization purchase is recognized as DUPLICATE
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', '99000000-0000-0000-0000-000000000012',
        'request_hash', 'dummy-hash-2',
        'transaction', jsonb_build_object(
            'id', '99000000-0000-0000-0000-000000000012',
            'type', 'EXPENSE',
            'operation_kind', 'CARD_PURCHASE',
            'amount_minor', 2000,
            'currency_code', 'PEN',
            'source_account_id', NULL,
            'card_id', '99000000-0000-0000-0000-000000000002',
            'category_id', '99000000-0000-0000-0000-000000000003',
            'merchant_provisional_text', 'Overutil Purchase',
            'occurred_at', '2026-09-20T13:00:00Z',
            'installment_count', 1,
            'rounding_policy', 'FIRST_INSTALLMENT_RESIDUAL',
            'interest_mode', 'NONE'
        )
    ))->>'status',
    'DUPLICATE',
    'Retry of overutilization transaction returns DUPLICATE status'
);

-- 5. Exactly 1 installment exists for the second transaction (no duplicate installment inserted)
SELECT is(
    (SELECT count(*)::bigint FROM public.credit_installments WHERE transaction_id = '99000000-0000-0000-0000-000000000012'),
    1::bigint,
    'Duplicate retry did not insert extra installments'
);

SELECT todo_end();

-- 6. Verified that first purchase is recorded
SELECT is(
    (SELECT count(*)::bigint FROM public.credit_installments WHERE transaction_id = '99000000-0000-0000-0000-000000000011'),
    1::bigint,
    'Initial purchase installment was recorded'
);

SELECT * FROM finish();
ROLLBACK;

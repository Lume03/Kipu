BEGIN;
SELECT plan(8);

INSERT INTO auth.users (id, email) VALUES
    ('74111111-1111-1111-1111-111111111111', 'wallet-channel-owner@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
VALUES ('74222222-2222-2222-2222-222222222222', '74111111-1111-1111-1111-111111111111',
        'Ahorros BCP', 'SAVINGS', 'PEN', 'BCP');

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id, linked_account_id)
VALUES ('74333333-3333-3333-3333-333333333333', '74111111-1111-1111-1111-111111111111',
        'Yape', 'WALLET', 'PEN', 'YAPE', '74222222-2222-2222-2222-222222222222');

CREATE TEMP TABLE wallet_payment_channel_request AS
WITH transaction_payload AS (
    SELECT jsonb_build_object(
        'id', '74444444-4444-4444-4444-444444444444',
        'type', 'EXPENSE',
        'amount_minor', 2500,
        'currency_code', 'PEN',
        'source_account_id', '74222222-2222-2222-2222-222222222222',
        'category_id', '00000000-0000-0000-0000-000000000001',
        'occurred_at', '2026-10-17T12:00:00.000Z',
        'note', 'Pago por Yape',
        'payment_channel', 'YAPE'
    ) AS payload
)
SELECT jsonb_build_object(
    'contract_version', 2,
    'idempotency_key', '74555555-5555-5555-5555-555555555555',
    'request_hash', internal.movement_register_hash_v2(
        '74111111-1111-1111-1111-111111111111'::uuid,
        transaction_payload.payload
    ),
    'transaction', transaction_payload.payload
) AS command
FROM transaction_payload;
GRANT SELECT ON wallet_payment_channel_request TO authenticated;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '74111111-1111-1111-1111-111111111111';

SELECT is(
    (public.register_transaction_v2(command)->>'status'),
    'APPLIED',
    'an affiliated Yape movement is accepted'
) FROM wallet_payment_channel_request;

SELECT is(
    (SELECT payment_channel FROM public.transactions
     WHERE id = '74444444-4444-4444-4444-444444444444'),
    'YAPE',
    'the movement keeps Yape as its payment channel'
);

SELECT is(
    (SELECT source_account_id::text FROM public.transactions
     WHERE id = '74444444-4444-4444-4444-444444444444'),
    '74222222-2222-2222-2222-222222222222',
    'the linked bank account remains the transaction source'
);

RESET ROLE;

SELECT is(
    (SELECT count(*)::integer FROM internal.ledger_entries
     WHERE transaction_id = '74444444-4444-4444-4444-444444444444'),
    1,
    'wallet attribution creates one ledger entry'
);

SELECT is(
    (SELECT account_id::text FROM internal.ledger_entries
     WHERE transaction_id = '74444444-4444-4444-4444-444444444444'),
    '74222222-2222-2222-2222-222222222222',
    'the ledger entry belongs to the linked bank account'
);

SELECT is(
    (SELECT sum(signed_amount_minor)::bigint FROM internal.ledger_entries
     WHERE transaction_id = '74444444-4444-4444-4444-444444444444'),
    -2500::bigint,
    'the bank account receives the expense exactly once'
);

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '74111111-1111-1111-1111-111111111111';

SELECT ok(
    (public.register_transaction_v2(command)->>'status') IN ('APPLIED', 'DUPLICATE'),
    'replaying the wallet movement returns its existing result'
) FROM wallet_payment_channel_request;

RESET ROLE;

SELECT ok(
    (SELECT count(*) = 1 FROM public.transactions
     WHERE id = '74444444-4444-4444-4444-444444444444')
    AND (SELECT count(*) = 1 FROM internal.ledger_entries
         WHERE transaction_id = '74444444-4444-4444-4444-444444444444'),
    'replaying the wallet movement does not duplicate its transaction or ledger effect'
);

SELECT * FROM finish();
ROLLBACK;

BEGIN;
SELECT plan(12);

INSERT INTO auth.users (id, email) VALUES
    ('53111111-1111-1111-1111-111111111111', 'wallet-owner@kipu.app'),
    ('53222222-2222-2222-2222-222222222222', 'wallet-foreign@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
VALUES ('53333333-3333-3333-3333-333333333333', '53222222-2222-2222-2222-222222222222',
        'Cuenta de otro usuario', 'SAVINGS', 'PEN', 'BCP');

SELECT ok(
    EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conname = 'accounts_linked_account_owner_fkey'
          AND c.conrelid = 'public.accounts'::regclass
          AND c.contype = 'f'
          AND c.conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'public.accounts'::regclass AND attname = 'user_id'),
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'public.accounts'::regclass AND attname = 'linked_account_id')
          ]::smallint[]
          AND c.confkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'public.accounts'::regclass AND attname = 'user_id'),
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'public.accounts'::regclass AND attname = 'id')
          ]::smallint[]
    ),
    'wallet linked account foreign key uses both owner and account IDs'
);

SELECT throws_like(
    $$
    INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id, linked_account_id)
    VALUES ('53444444-4444-4444-4444-444444444444', '53111111-1111-1111-1111-111111111111',
            'Yape de prueba', 'WALLET', 'PEN', 'YAPE', '53333333-3333-3333-3333-333333333333');
    $$,
    '%accounts_linked_account_owner_fkey%',
    'wallet cannot link to an account owned by another user'
);

CREATE TEMP TABLE wallet_account_creation_request AS
SELECT jsonb_build_object(
    'operation_id', '53555555-5555-5555-5555-555555555555',
    'account_id', '53666666-6666-6666-6666-666666666666',
    'opening_movement_id', '53777777-7777-7777-7777-777777777777',
    'alias', 'Yape de prueba',
    'type', 'WALLET',
    'currency', 'PEN',
    'preset_id', 'YAPE',
    'initial_balance_minor_units', 0,
    'opened_at', '2026-10-17T12:00:00Z',
    'payload_hash', 'wallet-create-hash-5355',
    'linked_account_id', '53999999-9999-9999-9999-999999999999'
) AS command;
GRANT SELECT ON wallet_account_creation_request TO authenticated;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '53111111-1111-1111-1111-111111111111';

SELECT is(
    (public.create_account_v1(jsonb_build_object(
        'operation_id', '53888888-8888-8888-8888-888888888888',
        'account_id', '53999999-9999-9999-9999-999999999999',
        'opening_movement_id', '53aaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'alias', 'Ahorros BCP',
        'type', 'SAVINGS',
        'currency', 'PEN',
        'preset_id', 'BCP',
        'initial_balance_minor_units', 10000,
        'opened_at', '2026-10-17T12:00:00Z',
        'payload_hash', 'bank-account-hash-5388'
    ))->>'status'),
    'APPLIED',
    'owner bank account can be created before wallet affiliation'
);

SELECT is(
    (public.register_card_v1(jsonb_build_object(
        'operation_id', '53bbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'card_id', '53cccccc-cccc-cccc-cccc-cccccccccccc',
        'type', 'DEBIT',
        'issuer', 'BCP',
        'network', 'VISA',
        'last_four_digits', '5555',
        'currency', 'PEN',
        'account_id', '53999999-9999-9999-9999-999999999999',
        'payload_hash', 'debit-card-hash-53bb'
    ))->>'status'),
    'APPLIED',
    'owner debit card satisfies Yape affiliation eligibility'
);

SELECT is(
    (SELECT public.create_account_v1(command)->>'status' FROM wallet_account_creation_request),
    'APPLIED',
    'wallet creation applies with an active eligible linked account'
);

RESET ROLE;
UPDATE public.accounts
SET is_archived = true
WHERE id = '53999999-9999-9999-9999-999999999999'
  AND user_id = '53111111-1111-1111-1111-111111111111';

SELECT is(
    (SELECT is_archived FROM public.accounts WHERE id = '53999999-9999-9999-9999-999999999999'),
    true,
    'linked bank account is archived before the wallet create retry'
);

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '53111111-1111-1111-1111-111111111111';

SELECT is(
    (SELECT public.create_account_v1(command)->>'status' FROM wallet_account_creation_request),
    'APPLIED',
    'wallet create retry returns its stored result after the linked account is archived'
);

SELECT is(
    (SELECT public.create_account_v1(command)->>'account_id' FROM wallet_account_creation_request),
    '53666666-6666-6666-6666-666666666666',
    'wallet create retry preserves the original account identity'
);

SELECT throws_like(
    $$
    SELECT public.create_account_v1(jsonb_set(
        command, '{payload_hash}', '"different-wallet-create-hash"'::jsonb
    ))
    FROM wallet_account_creation_request;
    $$,
    '%OPERATION_COLLISION%',
    'wallet create rejects a reused operation ID with a different payload hash'
);

RESET ROLE;

SELECT is(
    (SELECT count(*)::integer FROM public.accounts
     WHERE id = '53666666-6666-6666-6666-666666666666'
       AND user_id = '53111111-1111-1111-1111-111111111111'),
    1,
    'wallet create retry does not insert a second account'
);

SELECT is(
    (SELECT count(*)::integer FROM public.financial_movements
     WHERE account_id = '53666666-6666-6666-6666-666666666666' AND kind = 'OPENING'),
    1,
    'wallet create retry does not duplicate the opening movement'
);

SELECT is(
    (SELECT count(*)::integer FROM internal.command_receipts
     WHERE user_id = '53111111-1111-1111-1111-111111111111'
       AND idempotency_key = '53555555-5555-5555-5555-555555555555'),
    1,
    'wallet create retry retains exactly one idempotency receipt'
);

SELECT * FROM finish();
ROLLBACK;

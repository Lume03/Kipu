BEGIN;
SELECT plan(6);

INSERT INTO auth.users (id, email) VALUES
    ('75111111-1111-1111-1111-111111111111', 'wallet-unique-owner@kipu.app')
ON CONFLICT (id) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '75111111-1111-1111-1111-111111111111';

SELECT is(
    (SELECT public.create_account_v1(jsonb_build_object(
        'operation_id', '75222222-2222-2222-2222-222222222222',
        'account_id', '75333333-3333-3333-3333-333333333333',
        'opening_movement_id', '75444444-4444-4444-4444-444444444444',
        'alias', 'Yape principal',
        'type', 'WALLET',
        'currency', 'PEN',
        'preset_id', 'YAPE',
        'initial_balance_minor_units', 0,
        'opened_at', '2026-10-17T12:00:00Z',
        'payload_hash', repeat('a', 64)
    ))->>'status'),
    'APPLIED',
    'owner can create the first active Yape wallet'
);

SELECT throws_like(
    $$ SELECT public.create_account_v1(jsonb_build_object(
        'operation_id', '75555555-5555-5555-5555-555555555555',
        'account_id', '75666666-6666-6666-6666-666666666666',
        'opening_movement_id', '75777777-7777-7777-7777-777777777777',
        'alias', 'Yape duplicado',
        'type', 'WALLET',
        'currency', 'PEN',
        'preset_id', 'YAPE',
        'initial_balance_minor_units', 0,
        'opened_at', '2026-10-17T12:01:00Z',
        'payload_hash', repeat('b', 64)
    )); $$,
    '%ACTIVE_WALLET_CHANNEL_EXISTS%',
    'RPC rejects a second active wallet in the same channel'
);

SELECT is(
    (SELECT public.create_account_v1(jsonb_build_object(
        'operation_id', '75888888-8888-8888-8888-888888888888',
        'account_id', '75999999-9999-9999-9999-999999999999',
        'opening_movement_id', '75aaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'alias', 'Plin principal',
        'type', 'WALLET',
        'currency', 'PEN',
        'preset_id', 'PLIN',
        'initial_balance_minor_units', 0,
        'opened_at', '2026-10-17T12:02:00Z',
        'payload_hash', repeat('c', 64)
    ))->>'status'),
    'APPLIED',
    'the other payment channel can have its own active wallet'
);

RESET ROLE;

SELECT ok(
    EXISTS (
        SELECT 1 FROM pg_catalog.pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'accounts'
          AND indexname = 'accounts_one_active_wallet_per_channel_uidx'
          AND indexdef ILIKE '%UNIQUE%'
          AND indexdef ILIKE '%YAPE%'
          AND indexdef ILIKE '%PLIN%'
          AND indexdef ILIKE '%is_archived%'
    ),
    'accounts has a partial unique index for active Yape/Plin wallets'
);

SELECT throws_like(
    $$ INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
       VALUES ('75bbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
               '75111111-1111-1111-1111-111111111111',
               'Yape directo duplicado', 'WALLET', 'PEN', 'YAPE'); $$,
    '%ACTIVE_WALLET_CHANNEL_EXISTS%',
    'database trigger rejects duplicate writes outside the RPC'
);

UPDATE public.accounts
SET is_archived = true
WHERE id = '75333333-3333-3333-3333-333333333333'
  AND user_id = '75111111-1111-1111-1111-111111111111';

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '75111111-1111-1111-1111-111111111111';

SELECT is(
    (SELECT public.create_account_v1(jsonb_build_object(
        'operation_id', '75cccccc-cccc-cccc-cccc-cccccccccccc',
        'account_id', '75dddddd-dddd-dddd-dddd-dddddddddddd',
        'opening_movement_id', '75eeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        'alias', 'Yape renovado',
        'type', 'WALLET',
        'currency', 'PEN',
        'preset_id', 'YAPE',
        'initial_balance_minor_units', 0,
        'opened_at', '2026-10-17T12:03:00Z',
        'payload_hash', repeat('d', 64)
    ))->>'status'),
    'APPLIED',
    'archiving preserves the old wallet and releases the active channel'
);

SELECT * FROM finish();
ROLLBACK;

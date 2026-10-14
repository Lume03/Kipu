BEGIN;
SELECT plan(3);

INSERT INTO auth.users (id, email) VALUES
    ('53111111-1111-1111-1111-111111111111', 'wallet-owner@kipu.app'),
    ('53222222-2222-2222-2222-222222222222', 'wallet-foreign@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES ('53333333-3333-3333-3333-333333333333', '53222222-2222-2222-2222-222222222222',
        'Cuenta de otro usuario', 'SAVINGS', 'PEN');

SELECT ok(
    EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'accounts_linked_account_owner_fkey'
          AND conrelid = 'public.accounts'::regclass
          AND contype = 'f'
    ),
    'wallet linked account has a composite owner foreign key'
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

SELECT ok(
    has_function('public', 'create_account_v1', ARRAY['jsonb']),
    'wallet creation uses the idempotent account RPC'
);

SELECT * FROM finish();
ROLLBACK;

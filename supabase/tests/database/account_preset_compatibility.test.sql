BEGIN;
SELECT plan(4);

INSERT INTO auth.users (id, email)
VALUES ('12121212-1212-1212-1212-121212121212', 'preset-policy@kipu.app')
ON CONFLICT (id) DO NOTHING;

SELECT lives_ok(
    $$
    INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
    VALUES ('12121212-0000-0000-0000-000000000001', '12121212-1212-1212-1212-121212121212', 'Caja chica', 'CASH', 'PEN', 'CASH');
    $$,
    'cash account accepts the cash preset'
);

SELECT lives_ok(
    $$
    INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
    VALUES ('12121212-0000-0000-0000-000000000002', '12121212-1212-1212-1212-121212121212', 'Ahorro', 'SAVINGS', 'PEN', 'BANCO_NACION');
    $$,
    'savings account accepts an approved bank preset'
);

SELECT throws_ok(
    $$
    INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, preset_id)
    VALUES ('12121212-0000-0000-0000-000000000003', '12121212-1212-1212-1212-121212121212', 'Efectivo', 'CASH', 'PEN', 'BCP');
    $$,
    'INVALID_REQUEST: CASH accounts must use the CASH preset',
    'cash account rejects a bank preset'
);

SELECT throws_ok(
    $$
    UPDATE public.accounts SET preset_id = 'YAPE'
    WHERE id = '12121212-0000-0000-0000-000000000002';
    $$,
    'INVALID_REQUEST: Savings and bank accounts require a bank preset',
    'savings account rejects a wallet preset on update'
);

SELECT * FROM finish();
ROLLBACK;

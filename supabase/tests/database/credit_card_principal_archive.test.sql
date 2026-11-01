BEGIN;
SELECT plan(3);


INSERT INTO auth.users (id, email) VALUES
    ('44444444-4444-4444-4444-444444444444', 'user4@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, creation_operation_id, name, account_type, currency_code, initial_balance_minor_units, opened_at, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-000000000001', '44444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-000000000011', 'Ahorros BCP', 'SAVINGS', 'PEN', 100000, now(), now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO public.cards (id, user_id, creation_operation_id, network, alias, last4, is_credit, credit_limit_minor, closing_day, due_day, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-000000000002', '44444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-000000000012', 'VISA', 'BCP', '4444', true, 500000, 15, 5, now(), now())
ON CONFLICT DO NOTHING;

-- Seed the already accepted S/300 debt through the canonical schedule model.
INSERT INTO public.transactions (
    id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
    occurred_at,installment_count,interest_mode,status,revision
) VALUES (
    '44444444-4444-4444-4444-000000000022','44444444-4444-4444-4444-444444444444',NULL,
    '44444444-4444-4444-4444-000000000002','EXPENSE','CARD_PURCHASE',30000,'PEN',
    now()-interval '20 days',1,'NONE','CONFIRMED',1
);
INSERT INTO public.credit_installments (user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status)
VALUES ('44444444-4444-4444-4444-444444444444','44444444-4444-4444-4444-000000000022',1,current_date,30000,5000,'PENDING');

SELECT set_config('request.jwt.claim.sub','44444444-4444-4444-4444-444444444444',true);
SELECT throws_like($$SELECT public.set_instrument_archived_v1(jsonb_build_object(
 'instrument_id','44444444-4444-4444-4444-000000000002','instrument_type','CARD','is_archived',true))$$,
 '%CARD_HAS_OUTSTANDING_DEBT%','card with principal debt cannot be archived');
SELECT is(public.pay_credit_card_v1(jsonb_build_object(
 'operation_id','a4444444-4444-4444-4444-444444444441',
 'card_id','44444444-4444-4444-4444-000000000002',
 'source_account_id','44444444-4444-4444-4444-000000000001',
 'amount_minor_units',30000,'currency','PEN','payload_hash','hash-pay-1'))->>'status',
 'APPLIED','payment of principal alone succeeds despite estimated interest');
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='44444444-4444-4444-4444-000000000022'),
 'PAID','payment of principal alone marks the installment paid');
SELECT * FROM finish();
ROLLBACK;

BEGIN;
SELECT plan(16);


INSERT INTO auth.users (id, email) VALUES
    ('44444444-4444-4444-4444-444444444444', 'user4@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, creation_operation_id, name, account_type, currency_code, initial_balance_minor_units, opened_at, created_at, updated_at, preset_id)
VALUES ('44444444-4444-4444-4444-000000000001', '44444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-000000000011', 'Ahorros BCP', 'SAVINGS', 'PEN', 100000, now(), now(), now(), 'GENERIC')
ON CONFLICT DO NOTHING;

-- The account mirror is not spendable money; post the fixture's opening fact.
INSERT INTO public.financial_movements(operation_id,user_id,kind,amount_minor_units,currency,account_id,opening_account_id)
SELECT extensions.gen_random_uuid(),user_id,'OPENING',initial_balance_minor_units,currency_code,id,id
FROM public.accounts WHERE user_id IN ('44444444-4444-4444-4444-444444444444') AND initial_balance_minor_units>0;

-- Canonical cards always reference their own liability account.
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code) VALUES
(internal.credit_liability_identity_v1('account', '44444444-4444-4444-4444-444444444444'::uuid, '44444444-4444-4444-4444-000000000002'::uuid), '44444444-4444-4444-4444-444444444444', 'Credit liability fixture', 'CREDIT_LIABILITY', 'PEN');

INSERT INTO public.cards (id,user_id,creation_operation_id,network,alias,last4,is_credit,credit_limit_minor,closing_day,due_day,created_at,updated_at,account_id,currency_code,issuer) VALUES
('44444444-4444-4444-4444-000000000002','44444444-4444-4444-4444-444444444444','44444444-4444-4444-4444-000000000012','VISA','BCP','4444',true,500000,15,5,now(),now(),internal.credit_liability_identity_v1('account', '44444444-4444-4444-4444-444444444444'::uuid, '44444444-4444-4444-4444-000000000002'::uuid),'PEN','BCP');

-- Seed the already accepted S/300 debt through the canonical schedule model.
INSERT INTO public.transactions (
    id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
    occurred_at,installment_count,interest_mode,status,revision,category_id
) VALUES (
    '44444444-4444-4444-4444-000000000022','44444444-4444-4444-4444-444444444444',NULL,
    '44444444-4444-4444-4444-000000000002','EXPENSE','CARD_PURCHASE',30000,'PEN',
    now()-interval '20 days',1,'NONE','CONFIRMED',1,'00000000-0000-0000-0000-000000000001'
);
INSERT INTO public.credit_installments (user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status,purchase_type,financing,tea_bps,rate_source)
VALUES ('44444444-4444-4444-4444-444444444444','44444444-4444-4444-4444-000000000022',1,current_date,30000,5000,'PENDING','INSTALLMENTS','WITH_INTEREST',1000,'USER_ENTERED');


SELECT set_config('request.jwt.claim.sub','44444444-4444-4444-4444-444444444444',true);
SELECT is((SELECT sum(outstanding_minor)::bigint FROM private.credit_installment_balances(auth.uid(),'44444444-4444-4444-4444-000000000002','PEN')),35000::bigint,'confirmed interest adds to debt');
SELECT is((SELECT used_credit_minor FROM public.v_credit_summary WHERE card_id='44444444-4444-4444-4444-000000000002'),35000::numeric,'interest consumes line');
SELECT is((SELECT available_credit_minor FROM public.v_credit_summary WHERE card_id='44444444-4444-4444-4444-000000000002'),465000::numeric,'available line subtracts interest');
SET LOCAL ROLE authenticated;
SELECT is((SELECT used_credit_minor FROM public.v_credit_summary WHERE card_id='44444444-4444-4444-4444-000000000002'),35000::numeric,'authenticated summary preserves RLS and includes interest');
RESET ROLE;
SELECT is(public.pay_credit_card_v1(jsonb_build_object(
 'operation_id','a4444444-4444-4444-4444-444444444441','card_id','44444444-4444-4444-4444-000000000002',
 'source_account_id','44444444-4444-4444-4444-000000000001','amount_minor_units',30000,'currency','PEN','payload_hash','principal'))->>'status','APPLIED','principal portion accepted');
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='44444444-4444-4444-4444-000000000022'),'PARTIAL','principal paid still leaves interest');
SELECT throws_like($$SELECT public.set_instrument_archived_v1(jsonb_build_object(
 'operation_id','44444444-4444-4444-4444-000000000099','instrument_id','44444444-4444-4444-4444-000000000002','instrument_type','CARD','is_archived',true))$$,
 '%CARD_HAS_OUTSTANDING_DEBT%','interest alone blocks archive');
SELECT is(public.pay_credit_card_v1(jsonb_build_object(
 'operation_id','a4444444-4444-4444-4444-444444444442','card_id','44444444-4444-4444-4444-000000000002',
 'source_account_id','44444444-4444-4444-4444-000000000001','amount_minor_units',5000,'currency','PEN','payload_hash','interest'))->>'remaining_debt_minor','0','paying remaining interest clears debt');
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='44444444-4444-4444-4444-000000000022'),'PAID','total payment settles installment');
SELECT is((SELECT sum(outstanding_minor)::bigint FROM private.credit_installment_balances(auth.uid(),'44444444-4444-4444-4444-000000000002','PEN')),0::bigint,'balance zero after total');
-- A legacy interest estimate on an interest-free purchase is not contractual debt.
UPDATE public.credit_installments SET financing='INTEREST_FREE',tea_bps=NULL,rate_source=NULL,status='PENDING';
UPDATE public.transactions SET status='VOIDED' WHERE operation_kind='CARD_PAYMENT';
SELECT is((SELECT sum(outstanding_minor)::bigint FROM private.credit_installment_balances(auth.uid(),'44444444-4444-4444-4444-000000000002','PEN')),30000::bigint,'interest-free debt remains principal');
-- A single full payment, rather than two partial payments, also settles interest.
UPDATE public.credit_installments SET financing='WITH_INTEREST',tea_bps=1000,rate_source='USER_ENTERED';
SELECT is(public.pay_credit_card_v1(jsonb_build_object(
 'operation_id','a4444444-4444-4444-4444-444444444443','card_id','44444444-4444-4444-4444-000000000002',
 'source_account_id','44444444-4444-4444-4444-000000000001','amount_minor_units',35000,'currency','PEN','payload_hash','full'))->>'remaining_debt_minor','0','single full payment clears principal and interest');
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='44444444-4444-4444-4444-000000000022'),'PAID','full payment marks PAID');
SELECT is((SELECT internal.credit_transaction_sync_projection_v1(t)->'installments'->0->>'outstanding_minor'
 FROM public.transactions t WHERE id='44444444-4444-4444-4444-000000000022'),'0','feed projects settled total');
UPDATE public.transactions SET status='VOIDED' WHERE operation_kind='CARD_PURCHASE';
SELECT is((SELECT coalesce(sum(outstanding_minor),0)::bigint FROM private.credit_installment_balances(auth.uid(),'44444444-4444-4444-4444-000000000002','PEN')),0::bigint,'voided purchase creates no debt');
SELECT ok((SELECT count(*)=1 FROM public.credit_installments),'schedule retained');
SELECT * FROM finish();
ROLLBACK;

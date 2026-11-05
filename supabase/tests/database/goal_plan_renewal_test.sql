-- HU-30.8: synthetic commercial entitlement only; this does not accept Play Billing.
BEGIN;
SELECT plan(10);
\ir _goal_test_fixture.sql.inc

INSERT INTO public.billing_products(id,store_product_id,name,plan_type)
VALUES ('goal-renewal-monthly','goal-renewal-monthly','Goal renewal fixture','PRO_MONTHLY');
INSERT INTO public.billing_purchases(user_id,purchase_token_hash,product_id,purchase_state,entitlement_state,expires_at)
VALUES ('96000000-0000-4000-8000-000000000001',repeat('d',64),'goal-renewal-monthly','PURCHASED','ACTIVE',now()+interval '1 month');
SELECT ok(private.is_user_pro('96000000-0000-4000-8000-000000000001'), 'initial entitlement is Premium');

INSERT INTO public.goals(id,user_id,name,target_minor,currency_code,status,revision)
SELECT ('96000000-0000-4000-8000-00000000010'||n)::uuid,
  '96000000-0000-4000-8000-000000000001','Goal '||n,100000,'PEN','ACTIVE',1
FROM generate_series(1,4) n;
INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,currency_code,backing_account_id,operation_id)
VALUES ('96000000-0000-4000-8000-000000000201','96000000-0000-4000-8000-000000000001',
  '96000000-0000-4000-8000-000000000104','ALLOCATION',500,500,'PEN',
  '96000000-0000-4000-8000-000000000011','96000000-0000-4000-8000-000000000301');
CREATE TEMP TABLE renewal_history_before AS SELECT * FROM public.goal_events;
CREATE TEMP TABLE renewal_goals_before AS SELECT * FROM public.goals;
CREATE TEMP TABLE renewal_finance_before AS
SELECT (SELECT count(*) FROM public.transactions) transactions,
       (SELECT count(*) FROM internal.ledger_entries) entries;

UPDATE public.billing_purchases SET expires_at=now()-interval '1 day'
WHERE user_id='96000000-0000-4000-8000-000000000001';
SELECT ok(NOT private.is_user_pro('96000000-0000-4000-8000-000000000001'), 'expiry downgrades to Free');
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(1,
  '96000000-0000-4000-8000-000000000401','GOALS',1,
  '[{"resource_id":"96000000-0000-4000-8000-000000000101","resource_type":"GOAL"},{"resource_id":"96000000-0000-4000-8000-000000000102","resource_type":"GOAL"}]'::jsonb)),
  'APPLIED','Free accepts an explicit selection of two active goals');
RESET ROLE;
SELECT is((SELECT count(*) FROM private.plan_quota_selection_items
  WHERE user_id='96000000-0000-4000-8000-000000000001' AND feature_key='GOALS'),2::bigint,'exactly two selections persist');
SELECT results_eq('SELECT id,status,revision FROM public.goals ORDER BY id',
  'SELECT id,status,revision FROM renewal_goals_before ORDER BY id','downgrade selection preserves all four functional goal states');
SELECT results_eq('SELECT * FROM public.goal_events ORDER BY id',
  'SELECT * FROM renewal_history_before ORDER BY id','downgrade keeps contribution history byte for byte');

UPDATE public.billing_purchases SET expires_at=now()+interval '1 month'
WHERE user_id='96000000-0000-4000-8000-000000000001';
SELECT ok(private.is_user_pro('96000000-0000-4000-8000-000000000001'), 'commercial renewal restores Premium');
SET LOCAL ROLE authenticated;
SELECT is(public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',
  '96000000-0000-4000-8000-000000000104',1,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000202',
    'backing_account_id','96000000-0000-4000-8000-000000000011',
    'currency_code','PEN','amount_minor',100)))->>'status', 'APPLIED',
  'renewed Premium can contribute to a goal outside the retained Free selection');
RESET ROLE;
SELECT is((SELECT count(*) FROM public.goals WHERE user_id='96000000-0000-4000-8000-000000000001'),4::bigint,
  'renewal does not duplicate or remove goals');
SELECT results_eq('SELECT (SELECT count(*) FROM public.transactions) transactions, (SELECT count(*) FROM internal.ledger_entries) entries',
  'SELECT * FROM renewal_finance_before','downgrade, renewal and reserve-only contribution move no money');
SELECT * FROM finish();
ROLLBACK;

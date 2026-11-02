BEGIN;
SELECT no_plan();

INSERT INTO auth.users(id,email) VALUES
 ('75000000-0000-4000-8000-000000000001','dev-plan-a@example.test'),
 ('75000000-0000-4000-8000-000000000002','dev-plan-b@example.test');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id)
SELECT md5('dev-plan-account-'||n)::uuid,'75000000-0000-4000-8000-000000000001','Existing bank '||n,'BANK','PEN','GENERIC'
FROM generate_series(1,4) n;
INSERT INTO public.financial_movements(id,operation_id,user_id,kind,amount_minor_units,currency,account_id,status,effective_at,created_at)
VALUES ('77000000-0000-4000-8000-000000000001','77000000-0000-4000-8000-000000000002',
 '75000000-0000-4000-8000-000000000001','ADJUSTMENT',5000,'PEN',md5('dev-plan-account-1')::uuid,'POSTED',now(),now());

CREATE TEMP TABLE dev_before AS SELECT
 (SELECT coalesce(jsonb_agg(to_jsonb(a) ORDER BY a.id),'[]') FROM public.accounts a) AS accounts,
 (SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id),'[]') FROM public.financial_movements m) AS movements,
 (SELECT coalesce(jsonb_agg(to_jsonb(b) ORDER BY b.id),'[]') FROM public.billing_products b) AS products,
 (SELECT coalesce(jsonb_agg(to_jsonb(b) ORDER BY b.id),'[]') FROM public.billing_purchases b) AS purchases,
 (SELECT count(*) FROM internal.billing_events) AS billing_events;

SELECT ok(NOT public.dev_plan_mode(),'gate remains disabled after migration');
SELECT ok(NOT has_function_privilege('anon','public.dev_plan_mode()','EXECUTE'),'anon cannot query mode');
SELECT ok(NOT has_function_privilege('anon','public.dev_activate_plan(uuid,text)','EXECUTE'),'anon cannot activate');
SELECT ok(NOT has_function_privilege('authenticated','internal.set_dev_plan_mode(boolean,text)','EXECUTE'),'clients cannot enable mode');
SELECT ok(NOT has_function_privilege('service_role','internal.set_dev_plan_mode(boolean,text)','EXECUTE'),'operator is not service role');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='internal.dev_plan_receipts'::regclass),'receipts enforce RLS');
SELECT ok(NOT has_table_privilege('authenticated','internal.dev_plan_receipts','SELECT,INSERT,UPDATE,DELETE'),'receipts are private');

SET LOCAL ROLE anon;
SELECT throws_ok($$SELECT public.dev_plan_mode()$$,'42501',NULL,'anon RPC is denied');
SELECT throws_ok($$SELECT public.dev_activate_plan(gen_random_uuid(),'PRO_MONTHLY')$$,'42501',NULL,'anon activation is denied');
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT throws_ok($$SELECT public.dev_activate_plan(gen_random_uuid(),'PRO_MONTHLY')$$,'42501','UNAUTHENTICATED','missing session is rejected');
SET LOCAL "request.jwt.claim.sub" = '75000000-0000-4000-8000-000000000001';
SELECT is(public.dev_plan_mode(),false,'signed-in user sees disabled mode');
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000001','PRO_MONTHLY')->>'code','DEV_MODE_DISABLED','disabled gate rejects activation');
RESET ROLE;
SELECT is((SELECT count(*)::int FROM internal.dev_plan_receipts),0,'disabled gate writes no receipt');
SELECT is((SELECT count(*)::int FROM internal.test_plan_overrides),0,'disabled gate writes no override');
SELECT is((SELECT count(*)::int FROM internal.test_plan_override_audit WHERE user_id='75000000-0000-4000-8000-000000000001'),0,'disabled gate writes no audit');

SELECT internal.set_dev_plan_mode(true,'isolated D7 pgTAP');
SET LOCAL ROLE authenticated;
SELECT is(public.dev_plan_mode(),true,'server mode is visible to authenticated');
SELECT is(public.dev_activate_plan(gen_random_uuid(),'UNKNOWN')->>'code','INVALID_REQUEST','unknown plan is rejected');
SELECT is(public.dev_activate_plan(NULL,'FREE')->>'code','INVALID_REQUEST','missing operation is rejected');
SELECT set_config('test.dev_original',public.dev_activate_plan('76000000-0000-4000-8000-000000000001','PRO_MONTHLY')::text,true);
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000001','PRO_MONTHLY'),current_setting('test.dev_original')::jsonb,'retry returns the original result');
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000001','PRO_ANNUAL')->>'outcome','CONFLICT','reusing operation for a different plan conflicts');
SELECT throws_ok($$SELECT * FROM internal.test_plan_overrides$$,'42501',NULL,'cannot read any private overrides');
SELECT throws_ok($$UPDATE internal.test_plan_overrides SET tier='FREE' WHERE user_id='75000000-0000-4000-8000-000000000002'$$,'42501',NULL,'cannot modify another user');
SELECT throws_ok($$INSERT INTO internal.test_plan_override_gate(singleton,enabled) VALUES (true,true)$$,'42501',NULL,'cannot write gate');
SELECT throws_ok($$DELETE FROM internal.test_plan_override_audit$$,'42501',NULL,'cannot change audit');
RESET ROLE;
SELECT is((SELECT expires_at FROM internal.test_plan_overrides WHERE user_id='75000000-0000-4000-8000-000000000001'),now()+interval '30 days','monthly expires in exactly 30 server days');
SELECT is((SELECT plan_type FROM internal.test_plan_overrides WHERE user_id='75000000-0000-4000-8000-000000000001'),'PRO_MONTHLY','monthly plan is persisted');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000001')),'PREMIUM','monthly grants Premium');
SELECT is((SELECT count(*)::int FROM internal.test_plan_override_audit WHERE user_id='75000000-0000-4000-8000-000000000001'),1,'retry/conflict do not add audit');
SELECT is((SELECT actor FROM internal.test_plan_override_audit WHERE user_id='75000000-0000-4000-8000-000000000001'),'75000000-0000-4000-8000-000000000001','audit actor derives from Auth');
SELECT ok(NOT private.is_user_pro('75000000-0000-4000-8000-000000000002'),'other user stays Free');

SET LOCAL ROLE authenticated;
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000002','PRO_ANNUAL')->>'outcome','APPLIED','annual activation succeeds');
RESET ROLE;
SELECT is((SELECT expires_at FROM internal.test_plan_overrides WHERE user_id='75000000-0000-4000-8000-000000000001'),now()+interval '365 days','annual expires in 365 server days');
SELECT ok(private.is_user_pro('75000000-0000-4000-8000-000000000001'),'annual grants Premium in quota view');
SET LOCAL ROLE authenticated;
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000003','PRO_LIFETIME')->>'outcome','APPLIED','Lifetime activation succeeds');
RESET ROLE;
SELECT is((SELECT expires_at FROM internal.test_plan_overrides WHERE user_id='75000000-0000-4000-8000-000000000001'),NULL::timestamptz,'Lifetime has no expiry');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000001')),'TEST_OVERRIDE','Lifetime uses override source');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000001')),'PREMIUM','Lifetime refresh grants Premium');
SELECT ok(private.is_user_pro('75000000-0000-4000-8000-000000000001'),'Lifetime view grants Premium');
SELECT is((SELECT expires_at FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000001')),NULL::timestamptz,'Lifetime refresh has no entitlement expiry');

SET LOCAL ROLE authenticated;
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000004','FREE')->>'outcome','APPLIED','Free downgrade succeeds');
RESET ROLE;
SELECT ok(NOT private.is_user_pro('75000000-0000-4000-8000-000000000001'),'Free downgrade removes Premium');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000001')),'FREE','Free refresh clears Premium');
SELECT ok(private.is_instrument_quota_blocked('75000000-0000-4000-8000-000000000001',4),'four instruments still block Free creation');
SET LOCAL ROLE authenticated;
SELECT lives_ok($q$
 SELECT public.create_category_v1(jsonb_build_object('category_id',md5('dev-expense-'||n)::uuid,
 'category_type','EXPENSE','name','Expense '||n,'icon','shopping_cart','color','#112233'))
 FROM generate_series(1,5) n;
$q$,'Free downgrade permits five expense roots');
SELECT lives_ok($q$
 SELECT public.create_category_v1(jsonb_build_object('category_id',md5('dev-income-'||n)::uuid,
 'category_type','INCOME','name','Income '||n,'icon','work','color','#112233'))
 FROM generate_series(1,5) n;
$q$,'Free downgrade permits five income roots independently');
SELECT throws_ok($q$
 SELECT public.create_category_v1(jsonb_build_object('category_id',gen_random_uuid(),
 'category_type','EXPENSE','name','Sixth expense','icon','shopping_cart','color','#112233'));
$q$,'P0001','Free plan limit reached: maximum 5 active custom root categories per type','sixth expense is blocked on Free');
SELECT throws_ok($q$
 SELECT public.create_category_v1(jsonb_build_object('category_id',gen_random_uuid(),
 'category_type','INCOME','name','Sixth income','icon','work','color','#112233'));
$q$,'P0001','Free plan limit reached: maximum 5 active custom root categories per type','sixth income is blocked on Free');
RESET ROLE;
SELECT is((SELECT coalesce(jsonb_agg(to_jsonb(a) ORDER BY a.id),'[]') FROM public.accounts a),(SELECT accounts FROM dev_before),'plan changes preserve every account byte');
SELECT is((SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id),'[]') FROM public.financial_movements m),(SELECT movements FROM dev_before),'plan changes preserve financial history');

SET LOCAL ROLE authenticated;
SELECT public.dev_activate_plan('76000000-0000-4000-8000-000000000005','PRO_MONTHLY');
RESET ROLE;
UPDATE internal.test_plan_overrides SET expires_at=now()-interval '1 second' WHERE user_id='75000000-0000-4000-8000-000000000001';
SELECT ok(NOT private.is_user_pro('75000000-0000-4000-8000-000000000001'),'expired monthly is Free');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000001')),'FREE','expired monthly refresh is Free');
SELECT throws_ok($$UPDATE internal.test_plan_overrides SET expires_at=NULL WHERE user_id='75000000-0000-4000-8000-000000000001'$$,'23514',NULL,'recurring override cannot have a null expiry');

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '75000000-0000-4000-8000-000000000002';
SELECT is(public.dev_activate_plan('76000000-0000-4000-8000-000000000001','PRO_LIFETIME')->>'outcome','APPLIED','operation identity is scoped to its owner');
RESET ROLE;
SELECT is((SELECT plan_type FROM internal.test_plan_overrides WHERE user_id='75000000-0000-4000-8000-000000000001'),'PRO_MONTHLY','second owner cannot replace first owner plan');
SELECT internal.set_dev_plan_mode(false,'disable test mode before rollback');
SELECT ok(NOT private.is_user_pro('75000000-0000-4000-8000-000000000002'),'disabled gate ignores even Lifetime');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('75000000-0000-4000-8000-000000000002')),'FREE','disabled Lifetime refresh returns commercial Free');
SELECT is((SELECT coalesce(jsonb_agg(to_jsonb(b) ORDER BY b.id),'[]') FROM public.billing_products b),(SELECT products FROM dev_before),'billing products unchanged');
SELECT is((SELECT coalesce(jsonb_agg(to_jsonb(b) ORDER BY b.id),'[]') FROM public.billing_purchases b),(SELECT purchases FROM dev_before),'billing purchases unchanged');
SELECT is((SELECT count(*) FROM public.billing_purchases WHERE user_id IN ('75000000-0000-4000-8000-000000000001','75000000-0000-4000-8000-000000000002')),0::bigint,'activations create no simulated purchases');
SELECT is((SELECT count(*) FROM internal.billing_events),(SELECT billing_events FROM dev_before),'billing events unchanged');
SELECT * FROM finish();
ROLLBACK;

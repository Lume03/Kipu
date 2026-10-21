BEGIN;
SELECT plan(44);

-- Exercise the migration's ACL change transactionally; this test rolls it back.
REVOKE ALL ON public.v_feature_access FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON public.v_feature_access TO service_role;
-- The isolated plans-test database omits this unrelated table used by the legacy RPC.
CREATE TABLE IF NOT EXISTS public.user_devices (user_id uuid, is_active boolean);

INSERT INTO auth.users(id, email) VALUES
 ('73000000-0000-4000-8000-000000000001', 'qa-plan-owner@example.test'),
 ('73000000-0000-4000-8000-000000000002', 'qa-plan-other@example.test');

SELECT ok((SELECT NOT enabled FROM internal.test_plan_override_gate WHERE singleton), 'global QA gate defaults disabled');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='internal.test_plan_overrides'::regclass), 'overrides have forced RLS');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='internal.test_plan_override_gate'::regclass), 'gate has forced RLS');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='internal.test_plan_override_audit'::regclass), 'audit has forced RLS');
SELECT ok(NOT has_table_privilege('anon','internal.test_plan_overrides','SELECT,INSERT,UPDATE,DELETE'), 'anon cannot access overrides');
SELECT ok(NOT has_table_privilege('authenticated','internal.test_plan_overrides','SELECT,INSERT,UPDATE,DELETE'), 'authenticated cannot access overrides');
SELECT ok(NOT has_table_privilege('service_role','internal.test_plan_overrides','SELECT,INSERT,UPDATE,DELETE'), 'service role cannot write overrides');
SELECT ok(NOT has_function_privilege('authenticated','internal.set_test_plans_enabled(boolean,text)','EXECUTE'), 'authenticated cannot change global gate');
SELECT ok(NOT has_function_privilege('service_role','internal.set_test_plans_enabled(boolean,text)','EXECUTE'), 'service role cannot change global gate');
SELECT ok(NOT has_function_privilege('authenticated','internal.set_test_plan_override(uuid,text,timestamptz,text)','EXECUTE'), 'authenticated cannot assign overrides');
SELECT ok(NOT has_function_privilege('service_role','internal.set_test_plan_override(uuid,text,timestamptz,text)','EXECUTE'), 'service role cannot assign overrides');
SELECT ok(has_function_privilege('service_role','public.get_effective_plan_for_refresh(uuid)','EXECUTE'), 'server can read effective entitlement projection');
SELECT ok(NOT has_function_privilege('authenticated','public.get_effective_plan_for_refresh(uuid)','EXECUTE'), 'authenticated cannot call service projection');
SELECT ok(has_table_privilege('postgres','public.v_feature_access','SELECT'), 'view owner retains direct access');
SELECT ok(has_table_privilege('service_role','public.v_feature_access','SELECT'), 'trusted server role retains view access');
SELECT ok(NOT has_table_privilege('authenticated','public.v_feature_access','SELECT'), 'authenticated cannot query user feature rows directly');
SELECT ok(NOT has_table_privilege('anon','public.v_feature_access','SELECT'), 'anonymous cannot query user feature rows directly');

SELECT is((SELECT plan_tier FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 'FREE', 'missing override defaults to commercial Free');
SELECT lives_ok($$SELECT internal.set_test_plans_enabled(true,'isolated pgTAP scenario')$$, 'operator can enable gate');
SELECT lives_ok($$SELECT internal.set_test_plan_override('73000000-0000-4000-8000-000000000001','PREMIUM',now()+interval '1 hour','premium quota test')$$, 'operator can set bounded Premium override');
SELECT is((SELECT plan_tier FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 'PRO', 'Premium override preserves legacy PRO tier');
SELECT is((SELECT max_budgets FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 100, 'Premium override opens existing backend quotas');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('73000000-0000-4000-8000-000000000001')), 'PREMIUM', 'refresh API tier remains PREMIUM while legacy view maps to PRO');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('73000000-0000-4000-8000-000000000001')), 'TEST_OVERRIDE', 'refresh projection reports QA source');
SELECT set_config('request.jwt.claim.sub', '73000000-0000-4000-8000-000000000001', true);
SELECT ok((public.get_feature_access()->>'user_id') = '73000000-0000-4000-8000-000000000001'
          AND public.get_feature_access() ? 'limits', 'owner-scoped feature access RPC still returns its contract');
SELECT ok(private.is_user_pro('73000000-0000-4000-8000-000000000001'), 'category quota helper observes QA Premium');
SELECT ok(NOT private.is_instrument_quota_blocked('73000000-0000-4000-8000-000000000001', 4), 'Premium can create the fifth active instrument');
SELECT ok(NOT private.is_user_pro('73000000-0000-4000-8000-000000000002'), 'another user does not inherit the QA override');
SELECT ok(position('private.is_instrument_quota_blocked' IN pg_get_functiondef('public.create_account_v1(jsonb)'::regprocedure)) > 0, 'account creation uses the shared instrument quota guard');
SELECT ok(position('private.is_instrument_quota_blocked' IN pg_get_functiondef('public.register_card_v1(jsonb)'::regprocedure)) > 0, 'card registration uses the shared instrument quota guard');

SELECT lives_ok($$SELECT internal.set_test_plan_override('73000000-0000-4000-8000-000000000001','FREE',now()+interval '1 hour','free quota test')$$, 'operator can replace with Free override');
SELECT is((SELECT plan_tier FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 'FREE', 'Free override applies while enabled');
SELECT is((SELECT max_budgets FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 2, 'Free override applies normal limits');
SELECT ok(NOT private.is_user_pro('73000000-0000-4000-8000-000000000001'), 'category quota helper observes QA Free');
SELECT ok(private.is_instrument_quota_blocked('73000000-0000-4000-8000-000000000001', 4), 'Free blocks creation of the fifth active instrument');

UPDATE internal.test_plan_overrides SET expires_at=now()-interval '1 second' WHERE user_id='73000000-0000-4000-8000-000000000001';
SELECT is((SELECT plan_tier FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 'FREE', 'expired override falls back to commercial Free');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('73000000-0000-4000-8000-000000000001')), 'COMMERCIAL', 'expired override is not returned by refresh');

SELECT lives_ok($$SELECT internal.set_test_plan_override('73000000-0000-4000-8000-000000000001','PREMIUM',now()+interval '1 hour','gate-off fallback')$$, 'active override exists for gate-off case');
SELECT lives_ok($$SELECT internal.set_test_plans_enabled(false,'verify disabled fallback')$$, 'operator can disable gate');
SELECT is((SELECT plan_tier FROM public.v_feature_access WHERE user_id='73000000-0000-4000-8000-000000000001'), 'FREE', 'disabled gate ignores active Premium override');
SELECT is((SELECT test_plans_enabled FROM public.get_effective_plan_for_refresh('73000000-0000-4000-8000-000000000001')), false, 'refresh detects disabled gate');
SELECT ok(NOT private.is_user_pro('73000000-0000-4000-8000-000000000001'), 'quota guard returns to commercial Free when gate is disabled');

SELECT is((SELECT count(*)::integer FROM public.financial_movements WHERE user_id='73000000-0000-4000-8000-000000000001'), 0, 'QA plan transitions do not create financial movements');
SELECT is((SELECT count(*)::integer FROM internal.test_plan_override_audit WHERE user_id='73000000-0000-4000-8000-000000000001'), 3, 'each user override action has an audit receipt');

SELECT * FROM finish();
ROLLBACK;

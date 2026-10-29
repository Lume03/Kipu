BEGIN;
SELECT no_plan();

INSERT INTO auth.users(id, email) VALUES
 ('74000000-0000-4000-8000-000000000001', 'developer-plan-owner@example.test'),
 ('74000000-0000-4000-8000-000000000002', 'developer-plan-other@example.test');
SELECT internal.set_test_plans_enabled(false, 'developer entitlement test');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='internal.developer_entitlements'::regclass), 'developer entitlements force RLS');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='internal.developer_entitlement_audit'::regclass), 'developer audit forces RLS');
SELECT ok(NOT has_table_privilege(r, 'internal.developer_entitlements', 'SELECT,INSERT,UPDATE,DELETE'), r || ' cannot access developer entitlements') FROM unnest(ARRAY['anon','authenticated','service_role']) r;
SELECT ok(NOT has_table_privilege(r, 'internal.developer_entitlement_audit', 'SELECT,INSERT,UPDATE,DELETE'), r || ' cannot access developer audit') FROM unnest(ARRAY['anon','authenticated','service_role']) r;
SELECT ok(NOT has_function_privilege(r, 'internal.grant_developer_plan(uuid,text)', 'EXECUTE'), r || ' cannot grant developer') FROM unnest(ARRAY['anon','authenticated','service_role']) r;
SELECT ok(NOT has_function_privilege(r, 'internal.revoke_developer_plan(uuid,text)', 'EXECUTE'), r || ' cannot revoke developer') FROM unnest(ARRAY['anon','authenticated','service_role']) r;
SELECT throws_ok($$SELECT internal.grant_developer_plan(NULL, 'invalid')$$, '22023', NULL, 'null user rejected');
SELECT throws_ok($$SELECT internal.grant_developer_plan('74000000-0000-4000-8000-000000000001', ' ')$$, '22023', NULL, 'empty reason rejected');
SELECT throws_ok($$SELECT internal.revoke_developer_plan('74000000-0000-4000-8000-000000000001', ' ')$$, '22023', NULL, 'revoke requires reason');
SELECT lives_ok($$SELECT internal.grant_developer_plan('74000000-0000-4000-8000-000000000001', 'developer test')$$, 'postgres grants developer');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'PREMIUM', 'developer has Premium without purchase');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'DEVELOPER', 'source is developer with gate off');
SELECT ok((SELECT expires_at IS NULL AND NOT test_plans_enabled FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'no expiry and no QA gate needed');
SELECT is((SELECT plan_tier FROM public.v_feature_access WHERE user_id='74000000-0000-4000-8000-000000000001'), 'PRO', 'legacy tier remains PRO');
SELECT ok((SELECT max_devices=5 AND max_budgets=100 AND max_obligations=100 FROM public.v_feature_access WHERE user_id='74000000-0000-4000-8000-000000000001'), 'developer receives all Premium quotas');
SELECT ok(private.is_user_pro('74000000-0000-4000-8000-000000000001'), 'private quota helper follows developer view');
SELECT ok(NOT private.is_user_pro('74000000-0000-4000-8000-000000000002'), 'another user stays Free');
SELECT internal.set_test_plans_enabled(true, 'developer precedence');
SELECT internal.set_test_plan_override('74000000-0000-4000-8000-000000000001', 'FREE', now()+interval '1 hour', 'QA Free');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'DEVELOPER', 'developer beats enabled QA Free');
SELECT ok(private.is_user_pro('74000000-0000-4000-8000-000000000001'), 'QA Free cannot lower developer quotas');
SELECT internal.grant_developer_plan('74000000-0000-4000-8000-000000000001', 'repeat grant');
SELECT is((SELECT count(*)::integer FROM internal.developer_entitlements WHERE user_id='74000000-0000-4000-8000-000000000001'), 1, 'regrant remains one entitlement');
SELECT lives_ok($$SELECT internal.revoke_developer_plan('74000000-0000-4000-8000-000000000001', 'revoke test')$$, 'postgres revokes developer');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'TEST_OVERRIDE', 'revoke restores active QA override');
SELECT ok(NOT private.is_user_pro('74000000-0000-4000-8000-000000000001'), 'revocation restores QA Free quotas');
SELECT internal.set_test_plans_enabled(false, 'commercial fallback');
SELECT is((SELECT source FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'COMMERCIAL', 'gate off restores commercial source');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'FREE', 'revoked user without purchase is Free');
INSERT INTO public.billing_purchases(user_id, purchase_token_hash, product_id, purchase_state, entitlement_state, expires_at)
SELECT '74000000-0000-4000-8000-000000000001', repeat('7',64), id, 'PURCHASED', 'ACTIVE', now()+interval '1 month'
FROM public.billing_products WHERE plan_type='PRO_MONTHLY' LIMIT 1;
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'PREMIUM', 'commercial Premium still applies');
SELECT internal.grant_developer_plan('74000000-0000-4000-8000-000000000001', 'beats commercial');
SELECT ok((SELECT source='DEVELOPER' AND expires_at IS NULL FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'developer beats expiring purchase');
SELECT internal.revoke_developer_plan('74000000-0000-4000-8000-000000000001', 'return to purchase');
SELECT ok((SELECT source='COMMERCIAL' AND effective_tier='PREMIUM' AND expires_at IS NOT NULL FROM public.get_effective_plan_for_refresh('74000000-0000-4000-8000-000000000001')), 'revoke preserves commercial entitlement');
SELECT is((SELECT count(*)::integer FROM internal.developer_entitlement_audit WHERE user_id='74000000-0000-4000-8000-000000000001' AND action='GRANT'), 3, 'every grant audited');
SELECT is((SELECT count(*)::integer FROM internal.developer_entitlement_audit WHERE user_id='74000000-0000-4000-8000-000000000001' AND action='REVOKE'), 2, 'every revoke audited');
SELECT internal.grant_developer_plan('74000000-0000-4000-8000-000000000002', 'cascade test');
DELETE FROM auth.users WHERE id='74000000-0000-4000-8000-000000000002';
SELECT is((SELECT count(*)::integer FROM internal.developer_entitlements WHERE user_id='74000000-0000-4000-8000-000000000002'), 0, 'user deletion cascades entitlement');
SELECT is((SELECT count(*)::integer FROM internal.developer_entitlement_audit WHERE user_id='74000000-0000-4000-8000-000000000002'), 1, 'audit survives user deletion');
SELECT * FROM finish();
ROLLBACK;

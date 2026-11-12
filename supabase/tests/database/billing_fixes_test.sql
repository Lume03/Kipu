BEGIN;
SELECT no_plan();
SELECT is((SELECT count(*) FROM auth.users WHERE id='00000000-0000-0000-0000-000000000000'),0::bigint,'no sentinel Auth user exists');
SELECT lives_ok($$SELECT internal.set_dev_plan_mode(true,'L7 enable without sentinel')$$,'gate enables without a fake account');
SELECT is((SELECT enabled FROM internal.test_plan_override_gate WHERE singleton),true,'enabled state is persisted');
SELECT lives_ok($$SELECT internal.set_dev_plan_mode(false,'L7 disable without sentinel')$$,'gate disables without a fake account');
SELECT is((SELECT enabled FROM internal.test_plan_override_gate WHERE singleton),false,'disabled state is persisted');
SELECT is((SELECT count(*) FROM internal.test_plan_override_audit WHERE action IN ('GATE_ENABLED','GATE_DISABLED') AND user_id IS NULL AND reason LIKE 'L7 %'),2::bigint,'global gate audits have no user subject');
SELECT throws_ok($$INSERT INTO internal.test_plan_override_audit(user_id,action,reason,actor) VALUES (NULL,'RESET','invalid user event','test')$$,'23514',NULL,'user audit events still require a subject');
SELECT ok(NOT has_function_privilege('authenticated','internal.set_dev_plan_mode(boolean,text)','EXECUTE'),'clients cannot change the gate');
SELECT ok(NOT has_function_privilege('service_role','internal.set_test_plans_enabled(boolean,text)','EXECUTE'),'service role cannot change the gate');

INSERT INTO auth.users(id,email) VALUES ('78000000-0000-4000-8000-000000000001','l7-billing@example.test');
INSERT INTO public.billing_products(id,store_product_id,name,plan_type) VALUES
 ('l7-monthly','l7_monthly','L7 monthly','PRO_MONTHLY'),
 ('l7-lifetime','l7_lifetime','L7 lifetime','PRO_LIFETIME');
INSERT INTO public.billing_purchases(user_id,purchase_token_hash,product_id,purchase_state,entitlement_state,acknowledgement_state,starts_at,expires_at,verified_at)
VALUES ('78000000-0000-4000-8000-000000000001',repeat('7',64),'l7-monthly','PURCHASED','ACTIVE','ACKNOWLEDGED',now(),now()+interval '1 day',now());

-- Each case is isolated on the same purchase: expired, held and revoked rows cannot
-- borrow a future end from a different valid purchase.
CREATE TEMP TABLE billing_refresh_cases(lifecycle text,remaining interval,premium boolean,tier text,ends_at timestamptz,view_tier text);
DO $cases$
DECLARE c record; r record;
BEGIN
 FOR c IN SELECT * FROM (VALUES
   ('PURCHASED','ACTIVE',interval '1 day',true),
   ('PURCHASED','IN_GRACE_PERIOD',interval '1 day',true),
   ('CANCELLED','CANCELED_ACTIVE',interval '1 day',true),
   ('CANCELLED','CANCELED_ACTIVE',interval '-1 second',false),
   ('PURCHASED','IN_GRACE_PERIOD',interval '-1 second',false),
   ('PURCHASED','ACCOUNT_HOLD',interval '1 day',false),
   ('PURCHASED','PAUSED',interval '1 day',false),
   ('PURCHASED','REVOKED',interval '1 day',false)
 ) AS cases(purchase,lifecycle,remaining,premium)
 LOOP
   UPDATE public.billing_purchases SET purchase_state=c.purchase,entitlement_state=c.lifecycle,expires_at=now()+c.remaining
    WHERE purchase_token_hash=repeat('7',64);
   SELECT * INTO r FROM public.get_effective_plan_for_refresh('78000000-0000-4000-8000-000000000001');
   RAISE NOTICE '%',c.lifecycle;
   -- Emit pgTAP via separate queries below; the temporary results preserve each projection.
   INSERT INTO billing_refresh_cases VALUES (c.lifecycle,c.remaining,c.premium,r.effective_tier,r.expires_at,
     (SELECT plan_tier FROM public.v_feature_access WHERE user_id='78000000-0000-4000-8000-000000000001'));
 END LOOP;
END $cases$;

SELECT is(tier,CASE WHEN premium THEN 'PREMIUM' ELSE 'FREE' END,lifecycle||' refresh with remaining '||remaining::text) FROM billing_refresh_cases;
SELECT is(view_tier,CASE WHEN premium THEN 'PRO' ELSE 'FREE' END,lifecycle||' view agrees with refresh') FROM billing_refresh_cases;
SELECT is(ends_at,now()+remaining,lifecycle||' retains paid expiry') FROM billing_refresh_cases WHERE premium;
UPDATE public.billing_purchases SET product_id='l7-lifetime',purchase_state='PURCHASED',entitlement_state='ACTIVE',expires_at=NULL WHERE purchase_token_hash=repeat('7',64);
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('78000000-0000-4000-8000-000000000001')),'PREMIUM','Lifetime remains Premium');
SELECT is((SELECT expires_at FROM public.get_effective_plan_for_refresh('78000000-0000-4000-8000-000000000001')),NULL::timestamptz,'Lifetime stays unbounded');
SELECT internal.set_test_plan_override('78000000-0000-4000-8000-000000000001','FREE',now()+interval '1 day','L7 override precedence');
SELECT internal.set_dev_plan_mode(true,'L7 override precedence');
SELECT is((SELECT effective_tier FROM public.get_effective_plan_for_refresh('78000000-0000-4000-8000-000000000001')),'FREE','enabled Free override still masks commercial access');
SELECT * FROM finish();
ROLLBACK;

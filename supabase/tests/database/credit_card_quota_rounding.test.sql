BEGIN;
SET LOCAL search_path = public, extensions;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
CREATE TEMP TABLE quota_tap(line text);
INSERT INTO quota_tap SELECT plan(15);
-- Global gate audit receipts use this sentinel; the deletion guard requires Auth.
INSERT INTO auth.users(id,email) VALUES
 ('00000000-0000-0000-0000-000000000000','quota-gate-audit@example.test');
INSERT INTO auth.users(id,email) VALUES ('a0211111-1111-4111-8111-111111111111','quota-fixture@kipu.test');
SELECT set_config('request.jwt.claim.sub','a0211111-1111-4111-8111-111111111111',true);

CREATE FUNCTION pg_temp.account_command(n integer, kind text DEFAULT 'SAVINGS', parent uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql AS $$
 SELECT jsonb_build_object('operation_id',md5('quota-op-'||n)::uuid,'account_id',md5('quota-account-'||n)::uuid,
 'opening_movement_id',md5('quota-opening-'||n)::uuid,'alias','QA '||n,'type',kind,'currency','PEN',
 'preset_id',CASE WHEN kind='WALLET' THEN 'YAPE' ELSE 'BCP' END,'initial_balance_minor_units',0,
 'opened_at','2026-10-21T12:00:00Z','payload_hash','quota-hash-'||n,'linked_account_id',parent);
$$;

INSERT INTO quota_tap SELECT lives_ok($$SELECT public.create_account_v1(pg_temp.account_command(1))$$,'bank without debit digits can be created');
SELECT public.create_account_v1(pg_temp.account_command(n)) FROM generate_series(2,4) n;
INSERT INTO quota_tap SELECT is((SELECT count(DISTINCT quota_id)::integer FROM private.instrument_quota_groups(auth.uid())),4,'four bank accounts consume four slots');
INSERT INTO quota_tap SELECT lives_ok($$SELECT public.create_account_v1(pg_temp.account_command(5,'WALLET',md5('quota-account-1')::uuid))$$,'affiliated wallet allowed at full quota without debit card');
INSERT INTO quota_tap SELECT is((SELECT linked_account_id FROM public.accounts WHERE id=md5('quota-account-5')::uuid),md5('quota-account-1')::uuid,'affiliation persists');
INSERT INTO quota_tap SELECT is((SELECT count(DISTINCT quota_id)::integer FROM private.instrument_quota_groups(auth.uid())),4,'wallet does not consume another slot');
INSERT INTO quota_tap SELECT lives_ok($$SELECT public.register_card_v1(jsonb_build_object(
 'operation_id',md5('quota-debit-op')::uuid,'card_id',md5('quota-debit')::uuid,'account_id',md5('quota-account-1')::uuid,
 'alias','QA debit','type','DEBIT','currency','PEN','network','VISA','issuer','BCP','last_four_digits','1234','payload_hash','qa-debit-hash'))$$,'debit card allowed at full quota');
INSERT INTO quota_tap SELECT is((SELECT count(DISTINCT quota_id)::integer FROM private.instrument_quota_groups(auth.uid())),4,'debit card shares its bank slot');
INSERT INTO quota_tap SELECT throws_like($$SELECT public.create_account_v1(pg_temp.account_command(6))$$,'%FREE_LIMIT_REACHED%','fifth independent bank remains blocked');
INSERT INTO quota_tap SELECT throws_like($$SELECT public.create_account_v1(pg_temp.account_command(7,'WALLET','a0222222-2222-4222-8222-222222222222'))$$,'%INVALID_WALLET_AFFILIATION%','invalid or foreign parent remains rejected');

INSERT INTO quota_tap SELECT throws_like($$SELECT public.register_card_v1(jsonb_build_object(
 'operation_id',md5('quota-credit-op')::uuid,'card_id',md5('quota-credit')::uuid,'account_id',internal.credit_liability_identity_v1('account',auth.uid(),md5('quota-credit')::uuid),
 'alias','QA credit','type','CREDIT','currency','PEN','network','VISA','issuer','BCP','last_four_digits','4321',
 'credit_limit_minor_units',100000,'billing_day',15,'due_day',5,'payload_hash','qa-credit-hash'))$$,'%FREE_LIMIT_REACHED%','Free rejects fifth independent card');
SELECT internal.set_test_plans_enabled(true,'credit quota test');
SELECT internal.set_test_plan_override(auth.uid(),'PREMIUM',now()+interval '1 hour','credit quota test');
INSERT INTO quota_tap SELECT lives_ok($$SELECT public.register_card_v1(jsonb_build_object(
 'operation_id',md5('quota-credit-op')::uuid,'card_id',md5('quota-credit')::uuid,'account_id',internal.credit_liability_identity_v1('account',auth.uid(),md5('quota-credit')::uuid),
 'alias','QA credit','type','CREDIT','currency','PEN','network','VISA','issuer','BCP','last_four_digits','4321',
 'credit_limit_minor_units',100000,'billing_day',15,'due_day',5,'payload_hash','qa-credit-hash'))$$,'Premium accepts fifth independent card');
INSERT INTO quota_tap SELECT is((SELECT array_agg(principal_minor ORDER BY installment_number) FROM private.credit_purchase_schedule(10000::bigint,3::integer,NULL::integer,'FIRST_INSTALLMENT_RESIDUAL'::text)),ARRAY[3334,3333,3333]::bigint[],'interest free residual is assigned to first installment');
INSERT INTO quota_tap SELECT is((SELECT array_agg(principal_minor ORDER BY installment_number) FROM private.credit_purchase_schedule(120000::bigint,6::integer,4500::integer,'FIRST_INSTALLMENT_RESIDUAL'::text)),ARRAY[18485,19066,19665,20284,20921,21579]::bigint[],'French schedule retains interest and shifts principal residual');
INSERT INTO quota_tap SELECT ok((SELECT bool_and(principal_minor>=0) AND sum(principal_minor)=25 FROM private.credit_purchase_schedule(25::bigint,11::integer,4500::integer,'FIRST_INSTALLMENT_RESIDUAL'::text)),'negative residual preserves nonnegative principal and total');
INSERT INTO quota_tap SELECT is((SELECT array_agg(principal_minor ORDER BY installment_number) FROM private.credit_purchase_schedule(10000::bigint,3::integer,NULL::integer,'LAST_INSTALLMENT_RESIDUAL'::text)),ARRAY[3333,3333,3334]::bigint[],'legacy residual remains on last installment');
INSERT INTO quota_tap SELECT * FROM finish();
SELECT line FROM quota_tap;
ROLLBACK;

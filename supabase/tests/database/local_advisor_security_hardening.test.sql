BEGIN;
SELECT no_plan();

SELECT is((SELECT count(*)::integer FROM pg_class c
  JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('public','private','internal') AND c.relkind IN ('r','p')
    AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity)), 0,
  'all application tables enable and force RLS, including internal ledger evidence');
SELECT is((SELECT count(*)::integer FROM pg_class c
  JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('public','private','internal') AND c.relkind IN ('r','p','v','m')
    AND has_table_privilege('anon',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')), 0,
  'anonymous clients have no table or view privileges');
SELECT is((SELECT count(*)::integer FROM pg_class c
  JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('public','private','internal') AND c.relkind IN ('r','p')
    AND has_table_privilege('authenticated',c.oid,'TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')), 0,
  'authenticated clients have no table-wide administrative privileges');
SELECT is((SELECT count(*)::integer FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname IN ('public','private','internal') AND has_function_privilege('anon',p.oid,'EXECUTE')), 0,
  'anonymous clients cannot execute application functions or trigger helpers');
SELECT ok(NOT has_table_privilege('authenticated','public.v_feature_access','SELECT'),
  'the privileged entitlement projection remains backend-only');
SELECT ok(has_table_privilege('authenticated','public.budgets','INSERT,UPDATE')
  AND has_table_privilege('authenticated','public.budget_periods','INSERT,UPDATE'),
  'BudgetApi retains authenticated owner-scoped direct writes');
SELECT ok(has_function_privilege('authenticated','public.register_transaction_v1(jsonb)','EXECUTE')
  AND has_function_privilege('authenticated','public.register_transaction_v2(jsonb)','EXECUTE')
  AND has_function_privilege('authenticated','public.update_category_presentation_v1(jsonb)','EXECUTE')
  AND has_function_privilege('authenticated','public.update_profile_preferences(uuid,bigint,jsonb)','EXECUTE'),
  'current Android financial, category and profile RPC boundaries remain callable');
SELECT ok(has_function_privilege('service_role','public.consume_auth_rate_bucket(text)','EXECUTE')
  AND has_function_privilege('service_role','public.check_email_exists(text)','EXECUTE')
  AND has_function_privilege('service_role','public.get_effective_plan_for_refresh(uuid)','EXECUTE'),
  'Edge auth and entitlement service-role RPCs remain callable');

-- Test defaults using new objects, rather than relying only on ACL text.
CREATE TABLE public._advisor_default_probe (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY);
CREATE FUNCTION public._advisor_default_probe() RETURNS integer LANGUAGE sql AS 'SELECT 1';
SELECT ok(NOT has_table_privilege('anon','public._advisor_default_probe','SELECT,INSERT,TRUNCATE')
  AND NOT has_table_privilege('authenticated','public._advisor_default_probe','SELECT,INSERT,TRUNCATE'),
  'new public tables require explicit client grants');
SELECT ok(NOT has_sequence_privilege('anon','public._advisor_default_probe_id_seq','USAGE,SELECT,UPDATE')
  AND NOT has_sequence_privilege('authenticated','public._advisor_default_probe_id_seq','USAGE,SELECT,UPDATE'),
  'new public sequences require explicit client grants');
SELECT ok(NOT has_function_privilege('anon','public._advisor_default_probe()','EXECUTE')
  AND NOT has_function_privilege('authenticated','public._advisor_default_probe()','EXECUTE'),
  'new public functions do not inherit public or authenticated execution');

\ir _recurrence_test_fixture.sql.inc
INSERT INTO public.merchant_rules (id,user_id,pattern,is_system) VALUES
 ('ad000000-0000-4000-8000-000000000001',NULL,'advisor-shared-rule',true),
 ('ad000000-0000-4000-8000-000000000002','a7000000-0000-4000-8000-000000000002','advisor-other-rule',false);

SET LOCAL ROLE anon;
SELECT throws_ok('TRUNCATE public.category_presentations','42501',NULL,
  'anon cannot truncate user data even though TRUNCATE bypasses RLS');
SELECT throws_ok('SELECT public.apply_sync_command(''{}''::jsonb)','42501',NULL,
  'anon cannot call the legacy dispatcher');
SELECT throws_ok('SELECT public.register_transaction(''{}''::jsonb)','42501',NULL,
  'anon cannot call the legacy transaction endpoint');
SELECT throws_ok('SELECT public.allocate_credit_payment(''{}''::jsonb)','42501',NULL,
  'anon cannot call the legacy credit-payment endpoint');
SELECT throws_ok('SELECT * FROM public.accounts','42501',NULL,
  'anonymous account reads fail before row access');
RESET ROLE;

SET LOCAL ROLE authenticated;
SELECT throws_ok('TRUNCATE public.category_presentations','42501',NULL,
  'authenticated clients cannot bypass RLS using TRUNCATE');
SELECT throws_ok('SELECT public.apply_sync_command(''{}''::jsonb)','42501',NULL,
  'authenticated clients cannot reach legacy upserts that bypass current commands');
SELECT throws_ok('SELECT public.register_transaction(''{}''::jsonb)','42501',NULL,
  'authenticated clients must use versioned transaction commands');
SELECT throws_ok('SELECT public.allocate_credit_payment(''{}''::jsonb)','42501',NULL,
  'authenticated clients must use versioned credit-payment commands');
SELECT is((SELECT count(*)::integer FROM public.accounts WHERE id='a7100000-0000-4000-8000-000000000001'),1,
  'owner still reads their account with cached identity');
SELECT is((SELECT count(*)::integer FROM public.accounts WHERE id='a7100000-0000-4000-8000-000000000002'),0,
  'owner cannot read another account');
SELECT ok(EXISTS (SELECT FROM public.categories WHERE user_id IS NULL AND is_system AND origin='SYSTEM'),
  'shared system categories remain readable');
SELECT lives_ok($$INSERT INTO public.category_presentations(category_id,user_id,name,icon,color)
  VALUES ('a7200000-0000-4000-8000-000000000001','a7000000-0000-4000-8000-000000000001','Owner presentation','folder','#112233')$$,
  'owner-scoped existing presentation writes remain available');
SELECT throws_ok($$INSERT INTO public.category_presentations(category_id,user_id,name,icon,color)
  VALUES ('a7200000-0000-4000-8000-000000000002','a7000000-0000-4000-8000-000000000002','Other presentation','folder','#112233')$$,
  '42501',NULL,'inserting another owner presentation is rejected');
SELECT throws_ok($$UPDATE public.category_presentations SET user_id='a7000000-0000-4000-8000-000000000002'
  WHERE category_id='a7200000-0000-4000-8000-000000000001'$$,'42501',NULL,
  'WITH CHECK prevents changing a presentation owner');

SELECT is((SELECT count(*)::integer FROM public.merchant_rules WHERE id='ad000000-0000-4000-8000-000000000001'),1,
  'shared merchant rule remains readable');
WITH changed AS (UPDATE public.merchant_rules SET pattern='hijacked'
  WHERE id='ad000000-0000-4000-8000-000000000001' RETURNING id)
SELECT is((SELECT count(*)::integer FROM changed),0,
  'a shared read predicate cannot authorize a system-rule update');
WITH removed AS (DELETE FROM public.merchant_rules
  WHERE id='ad000000-0000-4000-8000-000000000001' RETURNING id)
SELECT is((SELECT count(*)::integer FROM removed),0,
  'a shared read predicate cannot authorize deleting a system rule');
SELECT is((SELECT count(*)::integer FROM public.merchant_rules WHERE id='ad000000-0000-4000-8000-000000000002'),0,
  'another owner merchant rule stays hidden');
SELECT lives_ok($$INSERT INTO public.merchant_rules(id,user_id,pattern)
  VALUES ('ad000000-0000-4000-8000-000000000003','a7000000-0000-4000-8000-000000000001','advisor-own-rule')$$,
  'owner can still create their own rule');
WITH removed AS (DELETE FROM public.merchant_rules
  WHERE id='ad000000-0000-4000-8000-000000000003' RETURNING id)
SELECT is((SELECT count(*)::integer FROM removed),1,
  'owner can still delete their own rule');

SET LOCAL "request.jwt.claim.sub"='a7000000-0000-4000-8000-000000000002';
SELECT is((SELECT count(*)::integer FROM public.category_presentations),0,
  'switching JWT identity isolates the previous owner presentation');
WITH removed AS (DELETE FROM public.category_presentations
  WHERE category_id='a7200000-0000-4000-8000-000000000001' RETURNING category_id)
SELECT is((SELECT count(*)::integer FROM removed),0,
  'another owner cannot delete a presentation');
RESET ROLE;

SELECT ok(to_regclass('public.transactions_user_id_id_uq') IS NULL
  AND to_regclass('public.idx_transactions_owner_id_uq') IS NOT NULL,
  'duplicate transaction index is removed while its referenced twin remains');
SELECT is((SELECT count(*)::integer FROM pg_constraint c
  JOIN pg_namespace n ON n.oid=c.connamespace
  WHERE c.contype='f' AND n.nspname IN ('public','private','internal')
    AND NOT EXISTS (SELECT FROM pg_index i WHERE i.indrelid=c.conrelid AND i.indisvalid
      AND c.conkey=(string_to_array(i.indkey::text,' ')::smallint[])[1:cardinality(c.conkey)])),0,
  'all application foreign keys have a valid index covering their leading columns');

SELECT * FROM finish();
ROLLBACK;

-- Run with psql -v ON_ERROR_STOP=1 -f goal_crud_test.sql (local isolated DB).
BEGIN;
\ir _goal_test_fixture.sql
-- Completed goals do not consume Free quota. Seed three independently of the
-- authenticated command surface so the test can exercise legacy/current state.
INSERT INTO public.goals(id,user_id,name,target_minor,currency_code,status,revision)
VALUES
  ('96000000-0000-4000-8000-000000000110','96000000-0000-4000-8000-000000000001','Done 1',100,'PEN','COMPLETED',1),
  ('96000000-0000-4000-8000-000000000111','96000000-0000-4000-8000-000000000001','Done 2',100,'PEN','COMPLETED',1),
  ('96000000-0000-4000-8000-000000000112','96000000-0000-4000-8000-000000000001','Done 3',100,'PEN','COMPLETED',1),
  ('96000000-0000-4000-8000-000000000120','96000000-0000-4000-8000-000000000001','Completed source',100,'PEN','COMPLETED',4),
  ('96000000-0000-4000-8000-000000000130','96000000-0000-4000-8000-000000000002','Foreign active',100,'PEN','ACTIVE',1);
INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,
    currency_code,backing_account_id,operation_id)
VALUES ('96000000-0000-4000-8000-000000000140','96000000-0000-4000-8000-000000000001',
  '96000000-0000-4000-8000-000000000120','ALLOCATION',50,50,'PEN',
  '96000000-0000-4000-8000-000000000011','96000000-0000-4000-8000-000000000141');
SET LOCAL ROLE authenticated;
DO $$
DECLARE
  g uuid := '96000000-0000-4000-8000-000000000101';
  c jsonb; r jsonb; second uuid := '96000000-0000-4000-8000-000000000102';
  source_goal uuid := '96000000-0000-4000-8000-000000000120';
BEGIN
  c := pg_temp.goal_cmd('CREATE_GOAL',g,NULL,
    jsonb_build_object('name','Viaje','target_minor',50000,'currency_code','PEN'));
  r := public.create_goal_v1(c);
  IF r->>'status'<>'APPLIED' OR r->>'revision'<>'1' THEN RAISE EXCEPTION 'CREATE: %',r; END IF;
  IF (public.create_goal_v1(c)->>'status')<>'DUPLICATE' THEN RAISE EXCEPTION 'retry failed'; END IF;
  IF (SELECT count(*) FROM public.goal_events WHERE goal_id=g)<>0 THEN RAISE EXCEPTION 'create emitted event'; END IF;
  IF (SELECT linked_account_id IS NOT NULL FROM public.goals WHERE id=g) THEN RAISE EXCEPTION 'linked account not optional'; END IF;
  IF (SELECT count(*) FROM public.goals WHERE user_id=auth.uid() AND deleted_at IS NULL AND status='COMPLETED') <> 4
  THEN RAISE EXCEPTION 'completed goal fixture missing'; END IF;
  c := c || jsonb_build_object('request_hash',repeat('a',64));
  IF (public.create_goal_v1(c)->>'error')<>'REQUEST_HASH_MISMATCH' THEN RAISE EXCEPTION 'hash validation failed'; END IF;
  c := pg_temp.goal_cmd('UPDATE_GOAL',g,1,jsonb_build_object('name','Viaje editado',
    'target_minor',50000,'currency_code','PEN','linked_account_id','96000000-0000-4000-8000-000000000011'));
  r := public.update_goal_v1(c);
  IF r->>'status'<>'APPLIED' OR r->>'revision'<>'2' THEN RAISE EXCEPTION 'UPDATE: %',r; END IF;
  r := public.update_goal_v1(pg_temp.goal_cmd('UPDATE_GOAL',g,1,jsonb_build_object(
    'name','Stale','target_minor',50000,'currency_code','PEN')));
  IF r->>'status'<>'CONFLICT' OR r->>'error'<>'REVISION_MISMATCH' THEN RAISE EXCEPTION 'stale revision: %',r; END IF;
  r := public.update_goal_v1(pg_temp.goal_cmd('UPDATE_GOAL',g,2,jsonb_build_object(
    'name','Wrong','target_minor',50000,'currency_code','PEN','linked_account_id',
    '96000000-0000-4000-8000-000000000013')));
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'foreign account: %',r; END IF;
  r := public.create_goal_v1(pg_temp.goal_cmd('CREATE_GOAL',second,NULL,
    jsonb_build_object('name','Reserva','target_minor',1000,'currency_code','PEN')));
  IF r->>'status'<>'APPLIED' THEN RAISE EXCEPTION 'second goal: %',r; END IF;
  r := public.create_goal_v1(pg_temp.goal_cmd('CREATE_GOAL',
    '96000000-0000-4000-8000-000000000103',NULL,
    jsonb_build_object('name','Third','target_minor',1000,'currency_code','PEN')));
  IF r->>'error'<>'GOAL_LIMIT' THEN RAISE EXCEPTION 'free limit: %',r; END IF;
  -- At the two-ACTIVE limit a completed goal may be reactivated only when the
  -- request explicitly selects an owned, current ACTIVE replacement.
  c := pg_temp.goal_cmd('UPDATE_GOAL',source_goal,4,jsonb_build_object(
    'name','Must remain completed','target_minor',200,'currency_code','PEN'));
  r := public.update_goal_v1(c);
  IF r->>'error'<>'REPLACEMENT_REQUIRED' THEN RAISE EXCEPTION 'missing replacement accepted: %',r; END IF;
  IF (SELECT status FROM public.goals WHERE id=source_goal)<>'COMPLETED'
    OR (SELECT revision FROM public.goals WHERE id=source_goal)<>4
    OR (SELECT count(*) FROM public.goal_events WHERE goal_id=source_goal)<>1
    THEN RAISE EXCEPTION 'rejected replacement changed state/history/balance'; END IF;
  c := pg_temp.goal_cmd('UPDATE_GOAL',source_goal,4,jsonb_build_object(
    'name','Completed source reactivated','target_minor',200,'currency_code','PEN',
    'replacement_goal_id',second,'replacement_expected_revision',1));
  r := public.update_goal_v1(c);
  IF r->>'status'<>'APPLIED' THEN RAISE EXCEPTION 'atomic replacement update: %',r; END IF;
  IF jsonb_typeof(r->'goals')<>'array' OR jsonb_array_length(r->'goals')<>2
    THEN RAISE EXCEPTION 'missing composite result: %',r; END IF;
  IF r->'goals'->0->>'goal_id'<>source_goal::text OR r->'goals'->0->>'status'<>'ACTIVE'
    OR r->'goals'->0->>'revision'<>'5' OR r->'goals'->0->>'sequence' IS NULL
    OR r->'goals'->1->>'goal_id'<>second::text OR r->'goals'->1->>'status'<>'LOCKED_BY_PLAN'
    OR r->'goals'->1->>'revision'<>'2' OR r->'goals'->1->>'sequence' IS NULL
    THEN RAISE EXCEPTION 'composite response omitted one goal result: %',r; END IF;
  IF (SELECT status FROM public.goals WHERE id=source_goal)<>'ACTIVE'
    OR (SELECT revision FROM public.goals WHERE id=source_goal)<>5
    OR (SELECT status FROM public.goals WHERE id=second)<>'LOCKED_BY_PLAN'
    OR (SELECT revision FROM public.goals WHERE id=second)<>2
    OR (SELECT count(*) FROM public.goal_events WHERE goal_id=source_goal)<>1
    OR (SELECT sum(delta_minor) FROM public.goal_events WHERE goal_id=source_goal)<>50
    THEN RAISE EXCEPTION 'replacement state/revisions not atomic'; END IF;
  IF (public.update_goal_v1(c)->>'status')<>'DUPLICATE' THEN RAISE EXCEPTION 'composite retry failed'; END IF;

  -- Stale/invalid replacement must leave source and destination untouched.
  c := pg_temp.goal_cmd('UPDATE_GOAL',source_goal,5,jsonb_build_object(
    'name','Must not apply','target_minor',300,'currency_code','PEN',
    'replacement_goal_id',g,'replacement_expected_revision',999));
  r := public.update_goal_v1(c);
  IF r->>'status'<>'CONFLICT' THEN RAISE EXCEPTION 'stale replacement accepted: %',r; END IF;
  IF (SELECT revision FROM public.goals WHERE id=source_goal)<>5
    OR (SELECT status FROM public.goals WHERE id=g)<>'ACTIVE'
    THEN RAISE EXCEPTION 'stale replacement mutated goals'; END IF;
  c := pg_temp.goal_cmd('UPDATE_GOAL',source_goal,5,jsonb_build_object(
    'name','Non-active replacement','target_minor',300,'currency_code','PEN',
    'replacement_goal_id',second,'replacement_expected_revision',2));
  r := public.update_goal_v1(c);
  IF r->>'error'<>'REPLACEMENT_NOT_ACTIVE' THEN RAISE EXCEPTION 'non-active replacement accepted: %',r; END IF;
  c := pg_temp.goal_cmd('UPDATE_GOAL',source_goal,5,jsonb_build_object(
    'name','Foreign replacement','target_minor',300,'currency_code','PEN',
    'replacement_goal_id','96000000-0000-4000-8000-000000000130',
    'replacement_expected_revision',1));
  r := public.update_goal_v1(c);
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'foreign replacement accepted: %',r; END IF;
  IF (SELECT revision FROM public.goals WHERE id=source_goal)<>5 THEN
    RAISE EXCEPTION 'foreign replacement changed source'; END IF;
  r := public.delete_empty_goal_v1(pg_temp.goal_cmd('DELETE_EMPTY_GOAL',g,2,jsonb_build_object('goal_id',g)));
  IF r->>'status'<>'APPLIED' THEN RAISE EXCEPTION 'delete empty: %',r; END IF;
  IF EXISTS(SELECT 1 FROM public.goals WHERE id=g) THEN RAISE EXCEPTION 'not deleted'; END IF;
END;
$$;
SELECT pg_catalog.set_config('request.jwt.claim.sub','96000000-0000-4000-8000-000000000002',true);
DO $$
DECLARE r jsonb;
BEGIN
  r := public.update_goal_v1(pg_temp.goal_cmd('UPDATE_GOAL',
    '96000000-0000-4000-8000-000000000102',1,
    jsonb_build_object('name','Intruder','target_minor',1000,'currency_code','PEN')));
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'owner violation: %',r; END IF;
END;
$$;
RESET ROLE;
DO $$ BEGIN
  IF (SELECT count(*) FROM public.financial_movements WHERE account_id='96000000-0000-4000-8000-000000000011')<>1
  THEN RAISE EXCEPTION 'replacement changed backing balance'; END IF;
  IF (SELECT count(*) FROM internal.sync_changes WHERE entity_type='GOAL'
      AND entity_id IN ('96000000-0000-4000-8000-000000000102',
                        '96000000-0000-4000-8000-000000000120')
      AND operation='UPSERT')<>3
  THEN RAISE EXCEPTION 'expected feed rows for both goals'; END IF;
  IF NOT EXISTS(SELECT 1 FROM internal.sync_changes WHERE
    entity_id='96000000-0000-4000-8000-000000000101' AND operation='DELETE' AND revision=3)
  THEN RAISE EXCEPTION 'delete revision/feed'; END IF;
END $$;
ROLLBACK;

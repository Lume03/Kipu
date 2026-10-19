BEGIN;

\ir _goal_test_fixture.sql
SELECT internal.reconcile_opening_ledger();
SET LOCAL ROLE authenticated;
DO $$
DECLARE
  a uuid := '96000000-0000-4000-8000-000000000011';
  g1 uuid := '96000000-0000-4000-8000-000000000101';
  g2 uuid := '96000000-0000-4000-8000-000000000102';
  r jsonb;
BEGIN
  PERFORM public.create_goal_v1(pg_temp.goal_cmd('CREATE_GOAL',g1,NULL,
    jsonb_build_object('name','Meta A','target_minor',60000,'currency_code','PEN')));
  PERFORM public.create_goal_v1(pg_temp.goal_cmd('CREATE_GOAL',g2,NULL,
    jsonb_build_object('name','Meta B','target_minor',80000,'currency_code','PEN')));
  r := public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',g1,1,
    jsonb_build_object('goal_id',g1,'event_id','96000000-0000-4000-8000-000000000201',
      'amount_minor',60000,'currency_code','PEN','backing_account_id',a)));
  IF r->>'status'<>'APPLIED' OR (SELECT status::text FROM public.goals WHERE id=g1)<>'COMPLETED' THEN
    RAISE EXCEPTION 'completion should preserve backed reservation: %',r; END IF;
  r := public.archive_goal_v1(pg_temp.goal_cmd('ARCHIVE_GOAL',g1,2,jsonb_build_object('goal_id',g1)));
  IF r->>'error'<>'RESERVE_UNRESOLVED' THEN RAISE EXCEPTION 'archive with reserve: %',r; END IF;
  r := public.release_goal_v1(pg_temp.goal_cmd('RELEASE_GOAL',g1,2,
    jsonb_build_object('event_id','96000000-0000-4000-8000-000000000202',
      'amount_minor',10000,'currency_code','PEN','backing_account_id',a)));
  IF r->>'status'<>'APPLIED' OR (SELECT status::text FROM public.goals WHERE id=g1)<>'ACTIVE' THEN
    RAISE EXCEPTION 'release should reactivate goal: %',r; END IF;
  r := public.release_goal_v1(pg_temp.goal_cmd('RELEASE_GOAL',g1,3,
    jsonb_build_object('event_id',extensions.gen_random_uuid(),
      'amount_minor',60000,'currency_code','PEN','backing_account_id',a)));
  IF r->>'error'<>'RESERVE_EXCEEDED' THEN RAISE EXCEPTION 'release over-reserve: %',r; END IF;
  r := public.reassign_goal_reserve_v1(pg_temp.goal_cmd('REASSIGN_GOAL_RESERVE',g1,3,
    jsonb_build_object('source_goal_id',g1,'destination_goal_id',g2,
      'source_expected_revision',3,'destination_expected_revision',1,
      'source_event_id','96000000-0000-4000-8000-000000000203',
      'destination_event_id','96000000-0000-4000-8000-000000000204',
      'backing_account_id',a,'amount_minor',30000,'currency_code','PEN')));
  IF r->>'status'<>'APPLIED' OR r->>'revision'<>'4' THEN RAISE EXCEPTION 'reassign: %',r; END IF;
  IF (SELECT count(*) FROM public.goal_events WHERE event_type='REASSIGNMENT_IN' AND goal_id=g2)<>1 THEN
    RAISE EXCEPTION 'destination assignment event missing'; END IF;
  r := public.archive_goal_v1(pg_temp.goal_cmd('ARCHIVE_GOAL',g1,4,jsonb_build_object('goal_id',g1)));
  IF r->>'error'<>'RESERVE_UNRESOLVED' THEN RAISE EXCEPTION 'partial reassignment archive: %',r; END IF;
  r := public.release_goal_v1(pg_temp.goal_cmd('RELEASE_GOAL',g1,4,
    jsonb_build_object('event_id','96000000-0000-4000-8000-000000000205',
      'amount_minor',20000,'currency_code','PEN','backing_account_id',a)));
  IF r->>'status'<>'APPLIED' THEN RAISE EXCEPTION 'final release: %',r; END IF;
  r := public.archive_goal_v1(pg_temp.goal_cmd('ARCHIVE_GOAL',g1,5,jsonb_build_object('goal_id',g1)));
  IF r->>'status'<>'APPLIED' OR (SELECT status::text FROM public.goals WHERE id=g1)<>'ARCHIVED' THEN
    RAISE EXCEPTION 'archive after release: %',r; END IF;
  r := public.delete_empty_goal_v1(pg_temp.goal_cmd('DELETE_EMPTY_GOAL',g1,6,
    jsonb_build_object('goal_id',g1)));
  IF r->>'error'<>'HISTORY_PRESERVED' THEN RAISE EXCEPTION 'cannot delete history: %',r; END IF;
  IF (SELECT count(*) FROM public.goal_events WHERE goal_id=g1)<>4 THEN
    RAISE EXCEPTION 'goal history lost'; END IF;
  IF (SELECT count(*) FROM public.transactions WHERE operation_kind='GOAL_FUNDING')<>0 THEN
    RAISE EXCEPTION 'lifecycle generated movement'; END IF;
END;
$$;
RESET ROLE;
DO $$ BEGIN
  IF (SELECT count(*) FROM internal.sync_changes WHERE
      entity_id='96000000-0000-4000-8000-000000000101'
      AND operation='UPSERT' AND revision IN (2,3,4,5))<>4
     OR NOT EXISTS(SELECT 1 FROM internal.sync_changes WHERE
      entity_id='96000000-0000-4000-8000-000000000102'
      AND operation='UPSERT' AND revision=2) THEN
    RAISE EXCEPTION 'lifecycle revision/feed missing';
  END IF;
END $$;
DO $$ BEGIN
  IF internal.goal_reserved('96000000-0000-4000-8000-000000000001',
    '96000000-0000-4000-8000-000000000101')<>0
    OR internal.goal_reserved('96000000-0000-4000-8000-000000000001',
    '96000000-0000-4000-8000-000000000102')<>30000 THEN
    RAISE EXCEPTION 'incorrect reserved balance after lifecycle'; END IF;
  IF (SELECT count(*) FROM internal.ledger_entries WHERE source_kind='TRANSACTION')<>0 THEN
    RAISE EXCEPTION 'release/reassignment must not create ledger'; END IF;
END $$;
-- Isolated owner: two ACTIVE goals at the Free quota, plus completed goals
-- with synthetic reservations to exercise RELEASE without sharing prior state.
INSERT INTO public.goals(id,user_id,name,target_minor,currency_code,status,revision)
VALUES
  ('96000000-0000-4000-8000-000000000301','96000000-0000-4000-8000-000000000002','Active replacement A',1000,'PEN','ACTIVE',1),
  ('96000000-0000-4000-8000-000000000302','96000000-0000-4000-8000-000000000002','Active replacement B',1000,'PEN','ACTIVE',1),
  ('96000000-0000-4000-8000-000000000303','96000000-0000-4000-8000-000000000002','Completed remains completed',50,'PEN','COMPLETED',4),
  ('96000000-0000-4000-8000-000000000304','96000000-0000-4000-8000-000000000002','Completed needs reactivation',200,'PEN','COMPLETED',7),
  ('96000000-0000-4000-8000-000000000305','96000000-0000-4000-8000-000000000001','Foreign active replacement',1000,'PEN','ACTIVE',1),
  ('96000000-0000-4000-8000-000000000306','96000000-0000-4000-8000-000000000002','Deleted replacement',1000,'PEN','ACTIVE',1);
UPDATE public.goals SET deleted_at=pg_catalog.now()
  WHERE id='96000000-0000-4000-8000-000000000306';
INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,
    currency_code,backing_account_id,operation_id)
VALUES
  ('96000000-0000-4000-8000-000000000311','96000000-0000-4000-8000-000000000002',
    '96000000-0000-4000-8000-000000000303','ALLOCATION',100,100,'PEN',
    '96000000-0000-4000-8000-000000000013','96000000-0000-4000-8000-000000000321'),
  ('96000000-0000-4000-8000-000000000312','96000000-0000-4000-8000-000000000002',
    '96000000-0000-4000-8000-000000000304','ALLOCATION',100,100,'PEN',
    '96000000-0000-4000-8000-000000000013','96000000-0000-4000-8000-000000000322');
SELECT pg_catalog.set_config('request.jwt.claim.sub','96000000-0000-4000-8000-000000000002',true);
SET LOCAL ROLE authenticated;
DO $$
DECLARE
  r jsonb;
  c jsonb;
  failed_operation uuid;
  source_normal uuid := '96000000-0000-4000-8000-000000000303';
  source_replace uuid := '96000000-0000-4000-8000-000000000304';
  replacement uuid := '96000000-0000-4000-8000-000000000301';
BEGIN
  -- At quota, release that still meets the target stays COMPLETED and does not
  -- need or lock a replacement.
  c := pg_temp.goal_cmd('RELEASE_GOAL',source_normal,4,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000331','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013'));
  r := public.release_goal_v1(c);
  IF r->>'status'<>'APPLIED' OR (SELECT status FROM public.goals WHERE id=source_normal)<>'COMPLETED'
    OR (SELECT revision FROM public.goals WHERE id=source_normal)<>5
    OR (SELECT status FROM public.goals WHERE id=replacement)<>'ACTIVE'
    THEN RAISE EXCEPTION 'release preserving completion should not need replacement: %',r; END IF;

  -- Missing selector at the two-ACTIVE quota has no event, state, revision,
  -- reservation or feed side effects.
  c := pg_temp.goal_cmd('RELEASE_GOAL',source_replace,7,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000332','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013'));
  failed_operation := (c->>'operation_id')::uuid;
  r := public.release_goal_v1(c);
  IF r->>'status'<>'REJECTED' OR r->>'error'<>'REPLACEMENT_REQUIRED'
    THEN RAISE EXCEPTION 'release without replacement must reject: %',r; END IF;
  IF (SELECT status FROM public.goals WHERE id=source_replace)<>'COMPLETED'
    OR (SELECT revision FROM public.goals WHERE id=source_replace)<>7
    OR pg_temp.goal_reserved_test(auth.uid(),source_replace)<>100
    OR EXISTS(SELECT 1 FROM public.goal_events WHERE id='96000000-0000-4000-8000-000000000332')
    OR pg_temp.goal_sync_exists_test(source_replace)
    OR pg_temp.goal_receipt_exists_test(failed_operation)
    THEN RAISE EXCEPTION 'missing replacement rejection mutated source/reserve/event'; END IF;
END;
$$;
RESET ROLE;
SET LOCAL ROLE authenticated;
DO $$
DECLARE r jsonb; c jsonb; failed_operations uuid[] := ARRAY[]::uuid[];
  source_goal uuid := '96000000-0000-4000-8000-000000000304';
  replacement uuid := '96000000-0000-4000-8000-000000000301';
BEGIN
  c := pg_temp.goal_cmd('RELEASE_GOAL',source_goal,7,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000333','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013',
    'replacement_goal_id',replacement,'replacement_expected_revision',99));
  failed_operations := array_append(failed_operations,(c->>'operation_id')::uuid);
  r := public.release_goal_v1(c);
  IF r->>'status'<>'CONFLICT' THEN RAISE EXCEPTION 'stale replacement accepted: %',r; END IF;
  c := pg_temp.goal_cmd('RELEASE_GOAL',source_goal,7,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000334','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013',
    'replacement_goal_id','96000000-0000-4000-8000-000000000305','replacement_expected_revision',1));
  failed_operations := array_append(failed_operations,(c->>'operation_id')::uuid);
  r := public.release_goal_v1(c);
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'foreign replacement accepted: %',r; END IF;
  c := pg_temp.goal_cmd('RELEASE_GOAL',source_goal,7,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000335','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013',
    'replacement_goal_id','96000000-0000-4000-8000-000000000306','replacement_expected_revision',1));
  failed_operations := array_append(failed_operations,(c->>'operation_id')::uuid);
  r := public.release_goal_v1(c);
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'deleted replacement accepted: %',r; END IF;
  c := pg_temp.goal_cmd('RELEASE_GOAL',source_goal,7,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000336','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013',
    'replacement_goal_id','96000000-0000-4000-8000-000000000303','replacement_expected_revision',5));
  failed_operations := array_append(failed_operations,(c->>'operation_id')::uuid);
  r := public.release_goal_v1(c);
  IF r->>'error'<>'REPLACEMENT_NOT_ACTIVE' THEN RAISE EXCEPTION 'non-ACTIVE replacement accepted: %',r; END IF;
  IF (SELECT status FROM public.goals WHERE id=source_goal)<>'COMPLETED'
    OR (SELECT revision FROM public.goals WHERE id=source_goal)<>7
    OR (SELECT status FROM public.goals WHERE id=replacement)<>'ACTIVE'
    OR pg_temp.goal_reserved_test(auth.uid(),source_goal)<>100
    OR EXISTS(SELECT 1 FROM public.goal_events WHERE id IN
      ('96000000-0000-4000-8000-000000000333','96000000-0000-4000-8000-000000000334',
       '96000000-0000-4000-8000-000000000335','96000000-0000-4000-8000-000000000336'))
    OR pg_temp.goal_sync_selection_exists_test(ARRAY[source_goal,replacement])
    OR pg_temp.goal_receipt_selection_exists_test(failed_operations)
    THEN RAISE EXCEPTION 'invalid selectors changed source/replacement state'; END IF;

  c := pg_temp.goal_cmd('RELEASE_GOAL',source_goal,7,jsonb_build_object(
    'event_id','96000000-0000-4000-8000-000000000337','amount_minor',20,
    'currency_code','PEN','backing_account_id','96000000-0000-4000-8000-000000000013',
    'replacement_goal_id',replacement,'replacement_expected_revision',1));
  r := public.release_goal_v1(c);
  IF r->>'status'<>'APPLIED' OR jsonb_typeof(r->'goals')<>'array'
    OR jsonb_array_length(r->'goals')<>2
    OR r->'goals'->0->>'goal_id'<>source_goal::text
    OR r->'goals'->0->>'status'<>'ACTIVE' OR r->'goals'->0->>'revision'<>'8'
    OR r->'goals'->1->>'goal_id'<>replacement::text
    OR r->'goals'->1->>'status'<>'LOCKED_BY_PLAN' OR r->'goals'->1->>'revision'<>'2'
    THEN RAISE EXCEPTION 'atomic release response incomplete: %',r; END IF;
  IF (SELECT status FROM public.goals WHERE id=source_goal)<>'ACTIVE'
    OR (SELECT revision FROM public.goals WHERE id=source_goal)<>8
    OR (SELECT status FROM public.goals WHERE id=replacement)<>'LOCKED_BY_PLAN'
    OR (SELECT revision FROM public.goals WHERE id=replacement)<>2
    OR pg_temp.goal_reserved_test(auth.uid(),source_goal)<>80
    OR (SELECT count(*) FROM public.goal_events WHERE id='96000000-0000-4000-8000-000000000337')<>1
    OR (SELECT count(*) FROM public.goals WHERE user_id=auth.uid()
      AND deleted_at IS NULL AND status='ACTIVE')<>2
    THEN RAISE EXCEPTION 'atomic release event/state/reservation incorrect'; END IF;
  IF (public.release_goal_v1(c)->>'status')<>'DUPLICATE'
    THEN RAISE EXCEPTION 'release retry did not return duplicate receipt'; END IF;
  c := c || jsonb_build_object('request_hash',repeat('b',64));
  IF (public.release_goal_v1(c)->>'error')<>'REQUEST_HASH_MISMATCH'
    THEN RAISE EXCEPTION 'release retry hash mismatch accepted'; END IF;
  c := c || jsonb_build_object('payload',(c->'payload') || jsonb_build_object('amount_minor',21));
  c := c || jsonb_build_object('request_hash',encode(extensions.digest(convert_to(
    pg_temp.goal_canonical_test(c - 'request_hash' - 'operation_id'),'UTF8'),'sha256'),'hex'));
  r := public.release_goal_v1(c);
  IF r->>'status'<>'CONFLICT' OR r->>'error'<>'OPERATION_ID_REUSED'
    THEN RAISE EXCEPTION 'valid rehashed retry with changed payload must conflict: %',r; END IF;
  IF (SELECT count(*) FROM public.goal_events WHERE id='96000000-0000-4000-8000-000000000337')<>1
    THEN RAISE EXCEPTION 'retry repeated release event'; END IF;
END;
$$;
RESET ROLE;
-- Cancellation is a client-side no-dispatch path: it cannot produce a SQL
-- command, event, receipt, sync row, or server outbox item by contract.
DO $$ BEGIN
  IF (SELECT count(*) FROM internal.sync_changes WHERE entity_id IN
      ('96000000-0000-4000-8000-000000000301','96000000-0000-4000-8000-000000000304')
      AND operation='UPSERT' AND revision IN (2,8))<>2
    THEN RAISE EXCEPTION 'composite replacement feed is incomplete'; END IF;
END $$;
ROLLBACK;

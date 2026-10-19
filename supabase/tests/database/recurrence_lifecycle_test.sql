BEGIN;
SELECT plan(24);
\ir _recurrence_test_fixture.sql

SELECT ok(to_regprocedure('public.set_recurrence_status_v1(jsonb)') IS NOT NULL,
  'lifecycle RPC is exposed with a single jsonb argument');
SELECT ok(to_regprocedure('public._rec_json_canonical(jsonb)') IS NOT NULL,
  'lifecycle uses the shared canonical hash implementation');

-- Resume confirmation is a required semantic payload field; parsing does not
-- synthesize or default it. The actual RPC rejection is exercised in GREEN.
SELECT ok(NOT ('{"status":"ACTIVE"}'::jsonb ? 'confirmed_next_due_date'),
  'missing explicit resume date remains missing after jsonb parsing');
SELECT is(
  '{"status":"ACTIVE","confirmed_next_due_date":"2026-10-20"}'::jsonb->>'confirmed_next_due_date',
  '2026-10-20',
  'explicitly confirmed resume date is preserved');

CREATE TEMP TABLE recurrence_lifecycle_oracles (missing_resume_status text, extra_cancel_field_status text);
DO $$
DECLARE v_missing jsonb; v_extra jsonb;
BEGIN
  IF to_regprocedure('public.set_recurrence_status_v1(jsonb)') IS NOT NULL THEN
    v_missing := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
      'operation_id','a7000000-0000-4000-8000-000000000011','request_hash',repeat('0',64),
      'recurrence_id','a7000000-0000-4000-8000-000000000021','expected_revision',1,
      'payload',jsonb_build_object('status','ACTIVE'));
    v_extra := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
      'operation_id','a7000000-0000-4000-8000-000000000012','request_hash',repeat('0',64),
      'recurrence_id','a7000000-0000-4000-8000-000000000021','expected_revision',1,
      'payload',jsonb_build_object('status','CANCELLED','confirmed',true));
    EXECUTE 'INSERT INTO recurrence_lifecycle_oracles SELECT public.set_recurrence_status_v1($1)->>''status'', public.set_recurrence_status_v1($2)->>''status''' USING v_missing,v_extra;
  ELSE
    INSERT INTO recurrence_lifecycle_oracles VALUES (NULL,NULL);
  END IF;
END;
$$;
SELECT is((SELECT missing_resume_status FROM recurrence_lifecycle_oracles),'REJECTED',
  'resume without an explicit confirmed date is rejected by the RPC');
SELECT is((SELECT extra_cancel_field_status FROM recurrence_lifecycle_oracles),'REJECTED',
  'cancel rejects extra confirmation fields in the closed payload');

SELECT ok(pg_catalog.to_regclass('public.recurrence_occurrences') IS NULL,
  'lifecycle has no recurrence occurrence table');
SELECT ok(NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname='public' AND c.relname IN ('recurrence_occurrences')),
  'lifecycle does not create occurrence storage');
SELECT ok(NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname='public' AND c.relname IN ('recurrence_payments')),
  'lifecycle does not create payment storage');
SELECT ok(NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname='public' AND c.relname IN ('recurrence_ledger')),
  'lifecycle does not create recurrence ledger storage');

-- End-to-end lifecycle behavior is guarded so this suite reports functional
-- RED assertions against the untouched baseline instead of a missing-relation
-- or missing-function setup error. On the post-migration run these exercise
-- real state transitions and assert zero side effects.
CREATE TEMP TABLE recurrence_lifecycle_assertions (
  assertion_id integer PRIMARY KEY, passed boolean NOT NULL
);
DO $$
DECLARE
  v_uid uuid := 'a7000000-0000-4000-8000-000000000001';
  v_id uuid := 'a7300000-0000-4000-8000-000000000021';
  v_start date := (CURRENT_DATE + INTERVAL '1 month')::date;
  v_cmd jsonb; v_result jsonb; v_row record;
  v_available boolean; v_receipts bigint; v_feed bigint;
  v_financial_before jsonb := '{}'::jsonb; v_financial_after jsonb := '{}'::jsonb;
  v_rel text; v_count bigint;
BEGIN
  v_available := to_regclass('public.recurrences') IS NOT NULL
    AND to_regprocedure('public.set_recurrence_status_v1(jsonb)') IS NOT NULL
    AND to_regprocedure('public._rec_json_canonical(jsonb)') IS NOT NULL;
  IF NOT v_available THEN
    INSERT INTO recurrence_lifecycle_assertions VALUES
      (1,false),(2,false),(3,false),(4,false),(5,false),(6,false),(7,false),
      (8,false),(9,false),(10,false),(11,false),(12,false),(13,false),(14,false);
    RETURN;
  END IF;

  FOREACH v_rel IN ARRAY ARRAY['internal.ledger_entries','public.financial_movements','public.budgets'] LOOP
    IF to_regclass(v_rel) IS NOT NULL THEN
      EXECUTE pg_catalog.format('SELECT count(*) FROM %s',v_rel) INTO v_count;
      v_financial_before := v_financial_before || jsonb_build_object(v_rel,v_count);
    END IF;
  END LOOP;

  v_cmd := jsonb_build_object(
    'command_type','CREATE_RECURRENCE','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000020','recurrence_id',v_id::text,'request_hash','',
    'payload',jsonb_build_object(
      'amount_mode','FIXED','custom_interval_unit',NULL,'end_date',NULL,
      'frequency','MONTHLY','interval_count',1,'next_due_date',to_char(v_start,'YYYY-MM-DD'),
      'reminder_lead_days',2,'start_date',to_char(v_start,'YYYY-MM-DD'),
      'template_transaction',jsonb_build_object(
        'concept','Lifecycle test','type','EXPENSE','amount_minor',100,
        'currency_code','PEN','source_account_id','a7100000-0000-4000-8000-000000000001',
        'destination_account_id',NULL,'category_id','a7200000-0000-4000-8000-000000000001',
        'merchant_id',NULL,'merchant_provisional_text',NULL)));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  PERFORM public.create_recurrence_v1(v_cmd);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000021','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',1,'payload',jsonb_build_object('status','PAUSED'));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (1,v_result->>'status'='APPLIED'),(2,v_row.status='PAUSED' AND NOT v_row.is_active AND v_row.revision=2);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000022','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',1,'payload',jsonb_build_object('status','CANCELLED'));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (3,v_result->>'status'='CONFLICT' AND v_row.status='PAUSED' AND v_row.revision=2);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000023','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',2,'payload',jsonb_build_object('status','ACTIVE'));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (4,v_result->>'status'='REJECTED' AND v_row.status='PAUSED' AND v_row.revision=2);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000024','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',2,'payload',jsonb_build_object(
      'status','ACTIVE','confirmed_next_due_date',to_char(v_start+1,'YYYY-MM-DD')));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (5,v_result->>'status'='REJECTED' AND v_row.status='PAUSED' AND v_row.revision=2);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000025','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',2,'payload',jsonb_build_object(
      'status','ACTIVE','confirmed_next_due_date',to_char(v_start,'YYYY-MM-DD')));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (6,v_result->>'status'='APPLIED'),
    (7,v_row.status='ACTIVE' AND v_row.is_active AND v_row.next_due_date=v_start AND v_row.revision=3);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000026','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',3,'payload',jsonb_build_object('status','CANCELLED'));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (8,v_result->>'status'='APPLIED'),
    (9,v_row.status='CANCELLED' AND NOT v_row.is_active AND v_row.revision=4),
    (11,v_row.id=v_id AND v_row.deleted_at IS NULL);

  v_cmd := jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000027','request_hash',repeat('0',64),
    'recurrence_id',v_id,'expected_revision',4,'payload',jsonb_build_object('status','PAUSED'));
  v_cmd := jsonb_set(v_cmd,'{request_hash}',to_jsonb(encode(extensions.digest(
    public._rec_json_canonical(v_cmd-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  EXECUTE 'SELECT public.set_recurrence_status_v1($1)' INTO v_result USING v_cmd;
  SELECT * INTO v_row FROM public.recurrences WHERE id=v_id;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (10,v_result->>'status'='REJECTED' AND v_row.status='CANCELLED' AND v_row.revision=4);

  SELECT count(*) INTO v_receipts FROM internal.command_receipts
   WHERE user_id=v_uid AND command_type='SET_RECURRENCE_STATUS'
     AND idempotency_key IN ('a7400000-0000-4000-8000-000000000021','a7400000-0000-4000-8000-000000000025','a7400000-0000-4000-8000-000000000026');
  SELECT count(*) INTO v_feed FROM internal.sync_changes
   WHERE user_id=v_uid AND entity_type='RECURRENCE' AND entity_id=v_id AND revision IN (2,3,4);
  INSERT INTO recurrence_lifecycle_assertions VALUES (12,v_receipts=3),(13,v_feed=3);

  FOREACH v_rel IN ARRAY ARRAY['internal.ledger_entries','public.financial_movements','public.budgets'] LOOP
    IF to_regclass(v_rel) IS NOT NULL THEN
      EXECUTE pg_catalog.format('SELECT count(*) FROM %s',v_rel) INTO v_count;
      v_financial_after := v_financial_after || jsonb_build_object(v_rel,v_count);
    END IF;
  END LOOP;
  INSERT INTO recurrence_lifecycle_assertions VALUES
    (14,v_financial_before=v_financial_after AND to_regclass('public.recurrence_occurrences') IS NULL);
END;
$$;
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=1), 'pause applies and advances revision');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=2), 'pause persists PAUSED and projects is_active=false');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=3), 'stale lifecycle revision conflicts without mutation');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=4), 'resume without explicit date rejects without mutation');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=5), 'resume with a mismatched confirmed date rejects');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=6), 'resume with explicit first future date applies');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=7), 'resume persists ACTIVE, confirmed date, and next revision');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=8), 'active recurrence can be cancelled');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=9), 'cancel persists CANCELLED and projects is_active=false');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=10), 'CANCELLED is terminal');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=11), 'lifecycle preserves the recurrence row/history');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=12), 'applied lifecycle commands write one receipt each');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=13), 'applied lifecycle commands write one sync change each');
SELECT ok((SELECT passed FROM recurrence_lifecycle_assertions WHERE assertion_id=14), 'lifecycle creates no financial or occurrence effects');

SELECT * FROM finish();
ROLLBACK;

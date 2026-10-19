BEGIN;
SELECT plan(30);
\ir _recurrence_test_fixture.sql

SELECT ok(to_regprocedure('public.create_recurrence_v1(jsonb)') IS NOT NULL,
  'CREATE RPC is exposed with a single jsonb argument');
SELECT ok(to_regprocedure('public.update_recurrence_v1(jsonb)') IS NOT NULL,
  'UPDATE RPC is exposed with a single jsonb argument');
SELECT ok(to_regprocedure('public._rec_json_canonical(jsonb)') IS NOT NULL,
  'canonical JSON helper is available to the command implementation');
SELECT ok(to_regprocedure('public._rec_valid_integer(jsonb,numeric,numeric)') IS NOT NULL,
  'exact integer/range validator is available to the command implementation');

-- PostgreSQL's jsonb boundary keeps the last duplicate member. The contract is
-- the semantic object after parsing, not the discarded raw JSON spelling.
SELECT is(
  '{"frequency":"MONTHLY","frequency":"WEEKLY"}'::jsonb,
  '{"frequency":"WEEKLY"}'::jsonb,
  'duplicate source keys collapse to the last semantic value'
);

-- Exercise exact numeric normalization at the jsonb/numeric boundary. These
-- fixtures do not use floating-point casts and are the inputs the RPC validates.
SELECT is(trim_scale(('100'::jsonb #>> '{}')::numeric)::text, '100', 'integer JSON number is exact');
SELECT is(trim_scale(('100.00'::jsonb #>> '{}')::numeric)::text, '100', 'fractional zeroes normalize away');
SELECT is(trim_scale(('1e2'::jsonb #>> '{}')::numeric)::text, '100', 'integer-equivalent exponent normalizes to base ten');
SELECT is(trim_scale(('1e-1'::jsonb #>> '{}')::numeric)::text, '0.1', 'non-integer exponent remains fractional and must be rejected by RPC');
SELECT is(trim_scale(('100.5'::jsonb #>> '{}')::numeric)::text, '100.5', 'fractional amount remains exact and must be rejected by integer fields');

CREATE TEMP TABLE recurrence_numeric_oracles (
  canonical_100 text, canonical_100_00 text, canonical_1e2 text,
  accepts_integer boolean, rejects_fraction boolean, rejects_fractional_exponent boolean
);
DO $$
BEGIN
  IF to_regprocedure('public._rec_json_canonical(jsonb)') IS NOT NULL
     AND to_regprocedure('public._rec_valid_integer(jsonb,numeric,numeric)') IS NOT NULL THEN
    EXECUTE $q$INSERT INTO recurrence_numeric_oracles VALUES
      (public._rec_json_canonical('100'::jsonb), public._rec_json_canonical('100.00'::jsonb), public._rec_json_canonical('1e2'::jsonb),
       public._rec_valid_integer('1e2'::jsonb,1,9223372036854775807),
       public._rec_valid_integer('100.5'::jsonb,1,9223372036854775807),
       public._rec_valid_integer('1e-1'::jsonb,1,9223372036854775807))$q$;
  ELSE
    INSERT INTO recurrence_numeric_oracles VALUES (NULL,NULL,NULL,NULL,NULL,NULL);
  END IF;
END;
$$;
SELECT is((SELECT canonical_100 FROM recurrence_numeric_oracles),'100','canonicalizer emits base-10 integer without exponent');
SELECT is((SELECT canonical_100_00 FROM recurrence_numeric_oracles),'100','canonicalizer removes fractional trailing zeroes');
SELECT is((SELECT canonical_1e2 FROM recurrence_numeric_oracles),'100','integer-equivalent exponent has same canonical value');
SELECT ok((SELECT accepts_integer AND NOT rejects_fraction AND NOT rejects_fractional_exponent FROM recurrence_numeric_oracles),
  'exact integer validator accepts 1e2 and rejects non-integral values');

SELECT ok(pg_catalog.to_regclass('internal.ledger_entries') IS NOT NULL,
  'financial ledger exists for zero-effect assertions after command execution');

-- These end-to-end assertions are executable after applying the candidate to
-- the isolated local DB. Against the intact baseline they report RED rather
-- than silently skipping absent RPC/schema behavior.
CREATE TEMP TABLE recurrence_create_update_assertions (
  assertion_id integer PRIMARY KEY, passed boolean NOT NULL, description text NOT NULL
);
DO $$
DECLARE
  v_user uuid := 'a7000000-0000-4000-8000-000000000001';
  v_id uuid := 'a7300000-0000-4000-8000-000000000001';
  v_create_op uuid := 'a7400000-0000-4000-8000-000000000001';
  v_update_op uuid := 'a7400000-0000-4000-8000-000000000002';
  v_command jsonb; v_update jsonb; v_bad_ref jsonb; v_bad_date jsonb;
  v_result jsonb; v_replay jsonb; v_changed jsonb; v_stale jsonb;
  v_oversized jsonb; v_base_canonical text; v_padding_length integer;
  v_before_recurrence jsonb; v_before_receipts bigint; v_before_feed bigint;
  v_financial_before jsonb; v_financial_after jsonb;
  v_available boolean; v_create_receipts bigint; v_create_feed bigint;
  v_update_receipts bigint; v_update_feed bigint;
  v_rel text; v_fingerprint text;
BEGIN
  v_available := to_regclass('public.recurrences') IS NOT NULL
    AND to_regprocedure('public.create_recurrence_v1(jsonb)') IS NOT NULL
    AND to_regprocedure('public.update_recurrence_v1(jsonb)') IS NOT NULL
    AND to_regprocedure('public._rec_json_canonical(jsonb)') IS NOT NULL;
  IF NOT v_available THEN
    INSERT INTO recurrence_create_update_assertions VALUES
      (1,false,'CREATE validates owner-scoped references and applies valid date/cadence'),
      (2,false,'same CREATE operation and hash returns its original receipt'),
      (3,false,'same operation id with changed canonical command conflicts'),
      (4,false,'UPDATE applies a valid patch and advances expected_revision'),
      (5,false,'same UPDATE operation and hash returns its original receipt'),
      (6,false,'stale expected_revision conflicts without changing recurrence'),
      (7,false,'cross-owner reference and off-schedule date are rejected'),
      (8,false,'receipt and sync feed rows commit exactly once with each write'),
      (9,false,'CREATE/UPDATE leave ledger, movements, budgets and occurrences unchanged'),
      (10,false,'tampered request hash is rejected before receipt persistence'),
      (11,false,'unknown envelope fields are rejected without trusting caller owner identity'),
      (12,false,'every UPDATE requires non-null reminder_lead_days before receipt lookup'),
      (13,false,'custom cadence rejects next_due_date outside the start-date-anchored sequence'),
      (14,false,'rejected commands leave no recurrence, receipt or sync-feed residue'),
      (15,false,'UPDATE rejects 16,385 canonical bytes with valid hash and no writes');
    RETURN;
  END IF;

  v_financial_before := '{}'::jsonb;
  FOREACH v_rel IN ARRAY ARRAY['internal.ledger_entries','public.financial_movements','public.budgets','public.recurrence_occurrences'] LOOP
    IF to_regclass(v_rel) IS NOT NULL THEN
      EXECUTE format('SELECT md5(coalesce(string_agg(to_jsonb(t)::text, %L ORDER BY to_jsonb(t)::text), %L)) FROM %s t',
        '', '', to_regclass(v_rel)) INTO v_fingerprint;
      v_financial_before := v_financial_before || jsonb_build_object(v_rel, v_fingerprint);
    END IF;
  END LOOP;

  v_command := jsonb_build_object(
    'command_type','CREATE_RECURRENCE','contract_version',1,
    'operation_id',v_create_op::text,'recurrence_id',v_id::text,'request_hash','',
    'payload',jsonb_build_object(
      'amount_mode','APPROXIMATE','custom_interval_unit',NULL,'end_date',NULL,
      'frequency','WEEKLY','interval_count',1,'next_due_date','2026-10-19',
      'reminder_lead_days',2,'start_date','2026-10-19',
      'template_transaction',jsonb_build_object(
        'concept','Synthetic recurring expense','type','EXPENSE','amount_minor',12500,
        'currency_code','PEN','source_account_id','a7100000-0000-4000-8000-000000000001',
        'destination_account_id',NULL,'category_id','a7200000-0000-4000-8000-000000000001',
        'merchant_id',NULL,'merchant_provisional_text',NULL)));
  v_command := jsonb_set(v_command,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_command-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_result := public.create_recurrence_v1(v_command);
  INSERT INTO recurrence_create_update_assertions VALUES (1,
    v_result->>'status'='APPLIED' AND (SELECT revision=1 AND amount_mode='APPROXIMATE' AND next_due_date=DATE '2026-10-19' FROM public.recurrences WHERE id=v_id AND user_id=v_user),
    'CREATE validates owner-scoped references and applies valid date/cadence');

  v_replay := public.create_recurrence_v1(v_command);
  INSERT INTO recurrence_create_update_assertions VALUES (2,v_replay=v_result, 'same CREATE operation and hash returns its original receipt');
  v_changed := jsonb_set(v_command,'{payload,reminder_lead_days}','3'::jsonb);
  v_changed := jsonb_set(v_changed,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_changed-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_changed := public.create_recurrence_v1(v_changed);
  INSERT INTO recurrence_create_update_assertions VALUES (3,v_changed->>'status'='CONFLICT' AND v_changed#>>'{error,code}'='OPERATION_ID_REUSED',
    'same operation id with changed canonical command conflicts');

  v_update := jsonb_build_object('command_type','UPDATE_RECURRENCE','contract_version',1,
    'operation_id',v_update_op::text,'recurrence_id',v_id::text,'expected_revision',1,
    'request_hash','','payload',jsonb_build_object('reminder_lead_days',3));
  v_update := jsonb_set(v_update,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_update-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_result := public.update_recurrence_v1(v_update);
  INSERT INTO recurrence_create_update_assertions VALUES (4,v_result->>'status'='APPLIED' AND (SELECT revision=2 AND reminder_lead_days=3 FROM public.recurrences WHERE id=v_id AND user_id=v_user),
    'UPDATE applies a valid patch and advances expected_revision');
  v_replay := public.update_recurrence_v1(v_update);
  INSERT INTO recurrence_create_update_assertions VALUES (5,v_replay=v_result, 'same UPDATE operation and hash returns its original receipt');

  -- Construct a syntactically and semantically valid UPDATE whose canonical
  -- envelope is exactly 16,385 UTF-8 bytes. The hash is valid for that exact
  -- canonical command; only the contractual 16,384-byte limit should reject it.
  v_oversized := jsonb_build_object('command_type','UPDATE_RECURRENCE','contract_version',1,
    'operation_id','a7400000-0000-4000-8000-000000000010','recurrence_id',v_id::text,
    'expected_revision',2,'request_hash','',
    'payload',jsonb_build_object('template_transaction',
      jsonb_set(v_command->'payload'->'template_transaction','{concept}','""'::jsonb)));
  v_base_canonical := public._rec_json_canonical(v_oversized-ARRAY['operation_id','request_hash']);
  v_padding_length := 16385 - pg_catalog.octet_length(v_base_canonical);
  v_oversized := jsonb_set(v_oversized,'{payload,template_transaction,concept}',to_jsonb(pg_catalog.repeat('x',v_padding_length)));
  v_base_canonical := public._rec_json_canonical(v_oversized-ARRAY['operation_id','request_hash']);
  -- Canonical concept text is ASCII, so each padding character contributes one byte.
  IF pg_catalog.octet_length(v_base_canonical) <> 16385 THEN
    RAISE EXCEPTION 'oversized UPDATE fixture expected 16385 canonical bytes, got %', pg_catalog.octet_length(v_base_canonical);
  END IF;
  v_oversized := jsonb_set(v_oversized,'{request_hash}',to_jsonb(encode(extensions.digest(v_base_canonical,'sha256'),'hex')));
  v_before_recurrence := (SELECT to_jsonb(r) FROM public.recurrences r WHERE id=v_id AND user_id=v_user);
  SELECT count(*) INTO v_before_receipts FROM internal.command_receipts WHERE user_id=v_user AND idempotency_key='a7400000-0000-4000-8000-000000000010';
  SELECT count(*) INTO v_before_feed FROM internal.sync_changes WHERE user_id=v_user AND entity_type='RECURRENCE' AND entity_id=v_id;
  v_result := public.update_recurrence_v1(v_oversized);
  INSERT INTO recurrence_create_update_assertions VALUES (15,
    COALESCE(
      pg_catalog.octet_length(public._rec_json_canonical(v_oversized-ARRAY['operation_id','request_hash']))=16385
      AND v_result->>'status'='REJECTED'
      AND (SELECT to_jsonb(r) FROM public.recurrences r WHERE id=v_id AND user_id=v_user) IS NOT DISTINCT FROM v_before_recurrence
      AND (SELECT count(*) FROM internal.command_receipts WHERE user_id=v_user AND idempotency_key='a7400000-0000-4000-8000-000000000010')=v_before_receipts
      AND (SELECT count(*) FROM internal.sync_changes WHERE user_id=v_user AND entity_type='RECURRENCE' AND entity_id=v_id)=v_before_feed,
      false),
    'UPDATE rejects exactly 16,385 canonical bytes with valid hash and no database writes');

  v_stale := jsonb_set(v_update,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000005'::text));
  v_stale := jsonb_set(v_stale,'{expected_revision}','1'::jsonb);
  v_stale := jsonb_set(v_stale,'{payload,reminder_lead_days}','4'::jsonb);
  v_stale := jsonb_set(v_stale,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_stale-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_stale := public.update_recurrence_v1(v_stale);
  INSERT INTO recurrence_create_update_assertions VALUES (6,v_stale->>'status'='CONFLICT' AND v_stale#>>'{error,code}'='REVISION_MISMATCH'
    AND (SELECT revision=2 AND reminder_lead_days=3 FROM public.recurrences WHERE id=v_id),
    'stale expected_revision conflicts without changing recurrence');

  v_changed := jsonb_set(v_command,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000006'::text));
  v_changed := jsonb_set(v_changed,'{recurrence_id}',to_jsonb('a7300000-0000-4000-8000-000000000004'::text));
  v_changed := jsonb_set(v_changed,'{request_hash}',to_jsonb(repeat('0',64)));
  v_changed := public.create_recurrence_v1(v_changed);
  INSERT INTO recurrence_create_update_assertions VALUES (10,v_changed->>'status'='REJECTED' AND v_changed#>>'{error,code}'='HASH_MISMATCH'
    AND NOT EXISTS (SELECT 1 FROM internal.command_receipts WHERE user_id=v_user AND idempotency_key='a7400000-0000-4000-8000-000000000006'),
    'tampered request hash is rejected before receipt persistence');

  v_changed := jsonb_set(v_command,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000007'::text));
  v_changed := jsonb_set(v_changed,'{recurrence_id}',to_jsonb('a7300000-0000-4000-8000-000000000005'::text));
  v_changed := jsonb_set(v_changed,'{user_id}',to_jsonb(v_user::text));
  v_changed := jsonb_set(v_changed,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_changed-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_changed := public.create_recurrence_v1(v_changed);
  INSERT INTO recurrence_create_update_assertions VALUES (11,v_changed->>'status'='REJECTED',
    'unknown envelope fields are rejected without trusting caller owner identity');

  v_changed := jsonb_set(v_update,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000008'::text));
  v_changed := jsonb_set(v_changed,'{payload}',jsonb_build_object('end_date','2026-10-26'));
  v_changed := jsonb_set(v_changed,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_changed-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_changed := public.update_recurrence_v1(v_changed);
  INSERT INTO recurrence_create_update_assertions VALUES (12,v_changed->>'status'='REJECTED',
    'every UPDATE requires non-null reminder_lead_days before receipt lookup');

  v_changed := jsonb_set(v_command,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000009'::text));
  v_changed := jsonb_set(v_changed,'{recurrence_id}',to_jsonb('a7300000-0000-4000-8000-000000000006'::text));
  v_changed := jsonb_set(v_changed,'{payload,frequency}',to_jsonb('CUSTOM'::text));
  v_changed := jsonb_set(v_changed,'{payload,interval_count}','2'::jsonb);
  v_changed := jsonb_set(v_changed,'{payload,custom_interval_unit}',to_jsonb('WEEK'::text));
  v_changed := jsonb_set(v_changed,'{payload,next_due_date}',to_jsonb('2026-10-26'::text));
  v_changed := jsonb_set(v_changed,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_changed-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_changed := public.create_recurrence_v1(v_changed);
  INSERT INTO recurrence_create_update_assertions VALUES (13,v_changed->>'status'='REJECTED',
    'custom cadence rejects next_due_date outside the start-date-anchored sequence');

  v_bad_ref := jsonb_set(v_command,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000003'::text));
  v_bad_ref := jsonb_set(v_bad_ref,'{recurrence_id}',to_jsonb('a7300000-0000-4000-8000-000000000002'::text));
  v_bad_ref := jsonb_set(v_bad_ref,'{payload,template_transaction,source_account_id}',to_jsonb('a7100000-0000-4000-8000-000000000002'::text));
  v_bad_ref := jsonb_set(v_bad_ref,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_bad_ref-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_bad_ref := public.create_recurrence_v1(v_bad_ref);
  v_bad_date := jsonb_set(v_command,'{operation_id}',to_jsonb('a7400000-0000-4000-8000-000000000004'::text));
  v_bad_date := jsonb_set(v_bad_date,'{recurrence_id}',to_jsonb('a7300000-0000-4000-8000-000000000003'::text));
  v_bad_date := jsonb_set(v_bad_date,'{payload,next_due_date}',to_jsonb('2026-10-20'::text));
  v_bad_date := jsonb_set(v_bad_date,'{request_hash}',to_jsonb(encode(extensions.digest(public._rec_json_canonical(v_bad_date-ARRAY['operation_id','request_hash']),'sha256'),'hex')));
  v_bad_date := public.create_recurrence_v1(v_bad_date);
  INSERT INTO recurrence_create_update_assertions VALUES (7,v_bad_ref->>'status'='REJECTED' AND v_bad_date->>'status'='REJECTED',
    'cross-owner reference and off-schedule date are rejected');
  INSERT INTO recurrence_create_update_assertions VALUES (14,
    NOT EXISTS (SELECT 1 FROM public.recurrences WHERE id IN (
      'a7300000-0000-4000-8000-000000000002','a7300000-0000-4000-8000-000000000003',
      'a7300000-0000-4000-8000-000000000004','a7300000-0000-4000-8000-000000000005',
      'a7300000-0000-4000-8000-000000000006'))
    AND NOT EXISTS (SELECT 1 FROM internal.command_receipts WHERE user_id=v_user
      AND idempotency_key IN ('a7400000-0000-4000-8000-000000000003','a7400000-0000-4000-8000-000000000004',
        'a7400000-0000-4000-8000-000000000006','a7400000-0000-4000-8000-000000000007',
        'a7400000-0000-4000-8000-000000000008','a7400000-0000-4000-8000-000000000009'))
    AND NOT EXISTS (SELECT 1 FROM internal.sync_changes WHERE user_id=v_user
      AND entity_id IN ('a7300000-0000-4000-8000-000000000002','a7300000-0000-4000-8000-000000000003',
        'a7300000-0000-4000-8000-000000000004','a7300000-0000-4000-8000-000000000005',
        'a7300000-0000-4000-8000-000000000006')),
    'rejected commands leave no recurrence, receipt or sync-feed residue');

  SELECT count(*) INTO v_create_receipts FROM internal.command_receipts WHERE user_id=v_user AND idempotency_key=v_create_op::text;
  SELECT count(*) INTO v_create_feed FROM internal.sync_changes WHERE user_id=v_user AND entity_type='RECURRENCE' AND entity_id=v_id AND revision=1;
  SELECT count(*) INTO v_update_receipts FROM internal.command_receipts WHERE user_id=v_user AND idempotency_key=v_update_op::text;
  SELECT count(*) INTO v_update_feed FROM internal.sync_changes WHERE user_id=v_user AND entity_type='RECURRENCE' AND entity_id=v_id AND revision=2;
  INSERT INTO recurrence_create_update_assertions VALUES (8,v_create_receipts=1 AND v_create_feed=1 AND v_update_receipts=1 AND v_update_feed=1,
    'receipt and sync feed rows commit exactly once with each write');

  v_financial_after := '{}'::jsonb;
  FOREACH v_rel IN ARRAY ARRAY['internal.ledger_entries','public.financial_movements','public.budgets','public.recurrence_occurrences'] LOOP
    IF to_regclass(v_rel) IS NOT NULL THEN
      EXECUTE format('SELECT md5(coalesce(string_agg(to_jsonb(t)::text, %L ORDER BY to_jsonb(t)::text), %L)) FROM %s t',
        '', '', to_regclass(v_rel)) INTO v_fingerprint;
      v_financial_after := v_financial_after || jsonb_build_object(v_rel, v_fingerprint);
    END IF;
  END LOOP;
  INSERT INTO recurrence_create_update_assertions VALUES (9,v_financial_after=v_financial_before,
    'CREATE/UPDATE leave ledger, movements, budgets and occurrences unchanged');
END;
$$;

SELECT ok(passed, description) FROM recurrence_create_update_assertions ORDER BY assertion_id;

SELECT * FROM finish();
ROLLBACK;

BEGIN;

-- Canonical JSON v1. This helper is deliberately private to the command RPCs.
CREATE OR REPLACE FUNCTION public._rec_json_canonical(p_value jsonb)
RETURNS text
LANGUAGE plpgsql IMMUTABLE STRICT
SET search_path = ''
AS $$
DECLARE
  v_type text := pg_catalog.jsonb_typeof(p_value);
  v_result text;
BEGIN
  CASE v_type
    WHEN 'object' THEN
      SELECT '{' || COALESCE(pg_catalog.string_agg(
        pg_catalog.to_jsonb(k)::text || ':' || public._rec_json_canonical(val), ',' ORDER BY k COLLATE "C"), '') || '}'
        INTO v_result FROM pg_catalog.jsonb_each(p_value) AS e(k, val);
      RETURN v_result;
    WHEN 'array' THEN
      SELECT '[' || COALESCE(pg_catalog.string_agg(public._rec_json_canonical(val), ',' ORDER BY ord), '') || ']'
        INTO v_result FROM pg_catalog.jsonb_array_elements(p_value) WITH ORDINALITY AS e(val, ord);
      RETURN v_result;
    WHEN 'number' THEN
      RETURN pg_catalog.trim_scale((p_value #>> '{}')::numeric)::text;
    WHEN 'string' THEN
      RETURN pg_catalog.to_jsonb(p_value #>> '{}')::text;
    ELSE
      RETURN p_value::text;
  END CASE;
END;
$$;

CREATE OR REPLACE FUNCTION public._rec_valid_integer(p_value jsonb, p_min numeric, p_max numeric)
RETURNS boolean
LANGUAGE plpgsql IMMUTABLE
SET search_path = ''
AS $$
DECLARE v numeric;
BEGIN
  IF p_value IS NULL OR pg_catalog.jsonb_typeof(p_value) <> 'number' THEN RETURN false; END IF;
  v := (p_value #>> '{}')::numeric;
  RETURN v = pg_catalog.trunc(v) AND v BETWEEN p_min AND p_max;
EXCEPTION WHEN OTHERS THEN RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION public._rec_valid_date(p_value jsonb)
RETURNS boolean
LANGUAGE plpgsql IMMUTABLE
SET search_path = ''
AS $$
DECLARE v_text text;
BEGIN
  IF p_value IS NULL OR pg_catalog.jsonb_typeof(p_value) <> 'string' THEN RETURN false; END IF;
  v_text := p_value #>> '{}';
  IF v_text !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN RETURN false; END IF;
  RETURN (v_text::date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
    AND pg_catalog.to_char(v_text::date, 'YYYY-MM-DD') = v_text;
EXCEPTION WHEN OTHERS THEN RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION public._rec_schedule_valid(
  p_start date, p_candidate date, p_frequency text, p_interval_count bigint,
  p_custom_interval_unit text, p_end_date date
)
RETURNS boolean
LANGUAGE plpgsql IMMUTABLE
SET search_path = ''
AS $$
DECLARE v_days integer; v_month_step integer; v_month_delta integer; v_month_start date; v_expected date;
BEGIN
  IF p_candidate < p_start OR (p_end_date IS NOT NULL AND p_candidate > p_end_date) THEN RETURN false; END IF;
  IF p_frequency IN ('WEEKLY', 'BIWEEKLY') OR (p_frequency = 'CUSTOM' AND p_custom_interval_unit = 'WEEK') THEN
    v_days := CASE WHEN p_frequency = 'WEEKLY' THEN 7
                   WHEN p_frequency = 'BIWEEKLY' THEN 14
                   ELSE (p_interval_count * 7)::integer END;
    RETURN (p_candidate - p_start) % v_days = 0;
  END IF;
  v_month_step := CASE WHEN p_frequency = 'MONTHLY' THEN 1
                       WHEN p_frequency = 'ANNUALLY' THEN 12
                       ELSE p_interval_count::integer END;
  v_month_delta := (pg_catalog.date_part('year',p_candidate)::integer - pg_catalog.date_part('year',p_start)::integer) * 12
                   + pg_catalog.date_part('month',p_candidate)::integer - pg_catalog.date_part('month',p_start)::integer;
  IF v_month_delta < 0 OR v_month_delta % v_month_step <> 0 THEN RETURN false; END IF;
  v_month_start := pg_catalog.date_trunc('month', p_candidate)::date;
  v_expected := v_month_start + (LEAST(pg_catalog.date_part('day',p_start)::integer,
                       pg_catalog.date_part('day',v_month_start + INTERVAL '1 month - 1 day')::integer) - 1);
  RETURN p_candidate = v_expected;
EXCEPTION WHEN OTHERS THEN RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION public._rec_template_references_valid(p_template jsonb, p_uid uuid)
RETURNS boolean
LANGUAGE plpgsql STABLE
SET search_path = ''
AS $$
DECLARE v_type text; v_currency text; v_source uuid; v_destination uuid; v_category uuid; v_merchant uuid;
BEGIN
  IF pg_catalog.jsonb_typeof(p_template) <> 'object'
     OR NOT (p_template ?& ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text'])
     OR p_template - ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text'] <> '{}'::jsonb
     OR pg_catalog.jsonb_typeof(p_template->'concept') <> 'string' OR pg_catalog.length(p_template->>'concept') = 0
     OR NOT public._rec_valid_integer(p_template->'amount_minor',1,9223372036854775807)
     OR (p_template->>'currency_code') !~ '^[A-Z]{3}$'
     OR pg_catalog.jsonb_typeof(p_template->'type') <> 'string'
     OR p_template->>'type' NOT IN ('EXPENSE','INCOME','TRANSFER')
     OR pg_catalog.jsonb_typeof(p_template->'source_account_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'destination_account_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'category_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'merchant_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'merchant_provisional_text') NOT IN ('string','null')
     OR (p_template->>'source_account_id' IS NOT NULL AND (p_template->>'source_account_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
     OR (p_template->>'destination_account_id' IS NOT NULL AND (p_template->>'destination_account_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
     OR (p_template->>'category_id' IS NOT NULL AND (p_template->>'category_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
     OR (p_template->>'merchant_id' IS NOT NULL AND (p_template->>'merchant_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') THEN RETURN false; END IF;
  v_type := p_template->>'type';
  v_currency := p_template->>'currency_code';
  v_source := NULLIF(p_template->>'source_account_id', '')::uuid;
  v_destination := NULLIF(p_template->>'destination_account_id', '')::uuid;
  v_category := NULLIF(p_template->>'category_id', '')::uuid;
  v_merchant := NULLIF(p_template->>'merchant_id', '')::uuid;
  IF NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code::text = v_currency) THEN RETURN false; END IF;
  IF (v_type IN ('EXPENSE', 'INCOME', 'TRANSFER')) IS NOT TRUE THEN RETURN false; END IF;
  IF v_source IS NULL OR NOT EXISTS (SELECT 1 FROM public.accounts a WHERE a.id=v_source AND a.user_id=p_uid
       AND NOT a.is_archived AND a.deleted_at IS NULL AND a.currency_code::text=v_currency) THEN RETURN false; END IF;
  IF v_type = 'TRANSFER' THEN
    IF v_destination IS NULL OR v_destination = v_source OR NOT EXISTS (
      SELECT 1 FROM public.accounts a WHERE a.id=v_destination AND a.user_id=p_uid
        AND NOT a.is_archived AND a.deleted_at IS NULL AND a.currency_code::text=v_currency) THEN RETURN false; END IF;
  ELSIF v_destination IS NOT NULL THEN RETURN false;
  END IF;
  IF v_type = 'EXPENSE' THEN
    IF v_category IS NULL OR NOT EXISTS (SELECT 1 FROM public.categories c WHERE c.id=v_category
       AND (c.user_id=p_uid OR c.user_id IS NULL) AND c.is_active AND c.deleted_at IS NULL) THEN RETURN false; END IF;
  ELSIF v_category IS NOT NULL THEN RETURN false;
  END IF;
  IF v_merchant IS NOT NULL AND (p_template->>'merchant_provisional_text' IS NOT NULL
       OR NOT EXISTS (SELECT 1 FROM public.merchant_services m WHERE m.id=v_merchant AND m.is_active)) THEN RETURN false; END IF;
  IF v_merchant IS NULL AND p_template->>'merchant_provisional_text' IS NOT NULL
       AND pg_catalog.length(pg_catalog.btrim(p_template->>'merchant_provisional_text')) = 0 THEN RETURN false; END IF;
  RETURN true;
EXCEPTION WHEN OTHERS THEN RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_recurrence_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid(); v_payload jsonb; v_id uuid; v_op uuid; v_hash text;
  v_canonical text; v_receipt internal.command_receipts%ROWTYPE; v_response jsonb;
  v_template jsonb; v_now timestamptz := pg_catalog.clock_timestamp();
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED')); END IF;
  IF pg_catalog.jsonb_typeof(p_command) <> 'object'
     OR NOT (p_command ?& ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','payload'])
     OR p_command - ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','payload'] <> '{}'::jsonb
     OR p_command->>'command_type' <> 'CREATE_RECURRENCE'
     OR NOT public._rec_valid_integer(p_command->'contract_version',1,1)
     OR pg_catalog.jsonb_typeof(p_command->'payload') <> 'object'
     OR NOT (p_command->'payload' ?& ARRAY['amount_mode','custom_interval_unit','end_date','frequency','interval_count','next_due_date','reminder_lead_days','start_date','template_transaction'])
     OR (p_command->'payload') - ARRAY['amount_mode','custom_interval_unit','end_date','frequency','interval_count','next_due_date','reminder_lead_days','start_date','template_transaction'] <> '{}'::jsonb
     OR NOT public._rec_valid_integer(p_command->'payload'->'reminder_lead_days',0,30)
     OR NOT public._rec_valid_integer(p_command->'payload'->'interval_count',1,9223372036854775807)
     OR pg_catalog.jsonb_typeof(p_command->'payload'->'template_transaction') <> 'object' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_op := (p_command->>'operation_id')::uuid; v_id := (p_command->>'recurrence_id')::uuid;
  IF p_command->>'operation_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'recurrence_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_payload := p_command->'payload'; v_template := v_payload->'template_transaction';
  IF pg_catalog.jsonb_typeof(v_template->'concept') <> 'string' OR pg_catalog.length(v_template->>'concept') = 0
     OR v_template->>'type' NOT IN ('EXPENSE','INCOME','TRANSFER')
     OR NOT public._rec_valid_integer(v_template->'amount_minor',1,9223372036854775807)
     OR v_template->>'currency_code' !~ '^[A-Z]{3}$'
     OR v_template - ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text'] <> '{}'::jsonb
     OR (v_template - ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text']) <> '{}'::jsonb
     OR NOT (v_template ?& ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text']) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_TEMPLATE'));
  END IF;
  IF (v_payload->>'amount_mode') IS NULL OR v_payload->>'amount_mode' NOT IN ('FIXED','APPROXIMATE')
     OR (v_payload->>'frequency') IS NULL OR v_payload->>'frequency' NOT IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY','CUSTOM')
     OR pg_catalog.jsonb_typeof(v_payload->'custom_interval_unit') NOT IN ('string','null')
     OR NOT public._rec_valid_date(v_payload->'start_date')
     OR NOT public._rec_valid_date(v_payload->'next_due_date')
     OR (v_payload->'end_date' <> 'null'::jsonb AND NOT public._rec_valid_date(v_payload->'end_date'))
     OR (v_payload->>'frequency' <> 'CUSTOM' AND (v_payload->'custom_interval_unit' <> 'null'::jsonb OR (v_payload->'interval_count')::numeric <> 1))
     OR (v_payload->>'frequency' = 'CUSTOM' AND NOT ((v_payload->>'custom_interval_unit'='WEEK' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 5200) OR (v_payload->>'custom_interval_unit'='MONTH' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 1200)))
     OR (v_payload->'end_date' <> 'null'::jsonb AND (v_payload->>'end_date')::date < (v_payload->>'start_date')::date)
     OR NOT public._rec_schedule_valid((v_payload->>'start_date')::date,(v_payload->>'next_due_date')::date,
          v_payload->>'frequency',(v_payload->>'interval_count')::bigint,NULLIF(v_payload->>'custom_interval_unit','null'),
          NULLIF(v_payload->>'end_date','null')::date) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_SCHEDULE'));
  END IF;
  IF NOT public._rec_template_references_valid(v_template,v_uid) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','REFERENCE_NOT_FOUND'));
  END IF;
  v_canonical := public._rec_json_canonical(p_command - ARRAY['operation_id','request_hash']);
  IF pg_catalog.octet_length(v_canonical) > 16384 OR pg_catalog.encode(extensions.digest(v_canonical,'sha256'),'hex') <> p_command->>'request_hash' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','HASH_MISMATCH'));
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text || ':' || v_op::text,0));
  SELECT * INTO v_receipt FROM internal.command_receipts WHERE user_id=v_uid AND idempotency_key=v_op::text;
  IF FOUND THEN
    IF v_receipt.command_type='CREATE_RECURRENCE' AND v_receipt.request_hash=p_command->>'request_hash' THEN RETURN v_receipt.response_payload; END IF;
    RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','OPERATION_ID_REUSED'));
  END IF;
  INSERT INTO public.recurrences(id,user_id,template_transaction,amount_mode,frequency,interval_count,custom_interval_unit,start_date,end_date,next_due_date,reminder_lead_days,status,revision)
  VALUES(v_id,v_uid,v_template,v_payload->>'amount_mode',v_payload->>'frequency',(v_payload->>'interval_count')::bigint,NULLIF(v_payload->>'custom_interval_unit','null'),(v_payload->>'start_date')::date,NULLIF(v_payload->>'end_date','null')::date,(v_payload->>'next_due_date')::date,(v_payload->>'reminder_lead_days')::smallint,'ACTIVE',1);
  v_response := jsonb_build_object('status','APPLIED','recurrence_id',v_id,'revision',1,'updated_at',v_now);
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) VALUES(v_uid,'RECURRENCE',v_id,1,'UPSERT',v_response || jsonb_build_object('rule',v_payload));
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status) VALUES(v_uid,v_op::text,'CREATE_RECURRENCE',p_command->>'request_hash',v_response,'APPLIED');
  RETURN v_response;
EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
END;
$$;

CREATE OR REPLACE FUNCTION public.update_recurrence_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_uid uuid:=auth.uid(); v_id uuid; v_op uuid; v_rev bigint; v_rec public.recurrences%ROWTYPE;
  v_receipt internal.command_receipts%ROWTYPE; v_hash text; v_canonical text; v_patch jsonb; v_payload jsonb; v_response jsonb;
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED')); END IF;
  IF pg_catalog.jsonb_typeof(p_command)<>'object'
     OR NOT (p_command ?& ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])
     OR (p_command-ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])<>'{}'::jsonb
     OR p_command->>'command_type'<>'UPDATE_RECURRENCE' OR NOT public._rec_valid_integer(p_command->'contract_version',1,1)
     OR NOT public._rec_valid_integer(p_command->'expected_revision',1,9223372036854775807)
     OR p_command->>'operation_id' IS NULL OR p_command->>'operation_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'recurrence_id' IS NULL OR p_command->>'recurrence_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'request_hash' IS NULL OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$'
     OR pg_catalog.jsonb_typeof(p_command->'payload')<>'object'
     OR (p_command->'payload') - ARRAY['template_transaction','amount_mode','frequency','interval_count','custom_interval_unit','end_date','next_due_date','reminder_lead_days'] <> '{}'::jsonb
     OR NOT public._rec_valid_integer(p_command->'payload'->'reminder_lead_days',0,30)
     OR (p_command->'payload') = '{}'::jsonb THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_id:=(p_command->>'recurrence_id')::uuid; v_op:=(p_command->>'operation_id')::uuid; v_rev:=(p_command->>'expected_revision')::bigint;
  v_patch:=p_command->'payload';
  v_canonical:=public._rec_json_canonical(p_command-ARRAY['operation_id','request_hash']);
  IF pg_catalog.octet_length(v_canonical) > 16384
     OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$'
     OR pg_catalog.encode(extensions.digest(v_canonical,'sha256'),'hex')<>p_command->>'request_hash' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','HASH_MISMATCH'));
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text||':'||v_op::text,0));
  SELECT * INTO v_receipt FROM internal.command_receipts WHERE user_id=v_uid AND idempotency_key=v_op::text;
  IF FOUND THEN
    IF v_receipt.command_type='UPDATE_RECURRENCE' AND v_receipt.request_hash=p_command->>'request_hash' THEN RETURN v_receipt.response_payload; END IF;
    RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','OPERATION_ID_REUSED'));
  END IF;
  SELECT * INTO v_rec FROM public.recurrences WHERE id=v_id AND user_id=v_uid AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NOT_FOUND')); END IF;
  IF v_rec.revision<>v_rev THEN RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','REVISION_MISMATCH','revision',v_rec.revision)); END IF;
  v_payload:=jsonb_build_object('template_transaction',v_rec.template_transaction,'amount_mode',v_rec.amount_mode,'frequency',v_rec.frequency,'interval_count',v_rec.interval_count,'custom_interval_unit',v_rec.custom_interval_unit,'end_date',v_rec.end_date,'next_due_date',v_rec.next_due_date,'reminder_lead_days',v_rec.reminder_lead_days) || v_patch;
  IF v_payload->>'frequency'<>v_rec.frequency AND NOT (v_patch ?& ARRAY['frequency','interval_count','custom_interval_unit','next_due_date']) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','CADENCE_FIELDS_REQUIRED'));
  END IF;
  IF (v_payload->>'amount_mode') IS NULL OR v_payload->>'amount_mode' NOT IN ('FIXED','APPROXIMATE')
     OR (v_payload->>'frequency') IS NULL OR v_payload->>'frequency' NOT IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY','CUSTOM')
     OR pg_catalog.jsonb_typeof(v_payload->'custom_interval_unit') NOT IN ('string','null')
     OR NOT public._rec_valid_integer(v_payload->'interval_count',1,9223372036854775807)
     OR NOT public._rec_valid_integer(v_payload->'reminder_lead_days',0,30)
     OR NOT public._rec_valid_date(v_payload->'next_due_date')
     OR (v_payload->'end_date' <> 'null'::jsonb AND NOT public._rec_valid_date(v_payload->'end_date'))
     OR (v_payload->>'frequency' IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY') AND
         ((v_payload->'interval_count')::numeric <> 1 OR v_payload->'custom_interval_unit' <> 'null'::jsonb))
     OR (v_payload->>'frequency'='CUSTOM' AND NOT (
         (v_payload->>'custom_interval_unit'='WEEK' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 5200) OR
         (v_payload->>'custom_interval_unit'='MONTH' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 1200)))
     OR NOT public._rec_template_references_valid(v_payload->'template_transaction',v_uid)
     OR NOT public._rec_schedule_valid(v_rec.start_date,(v_payload->>'next_due_date')::date,
         v_payload->>'frequency',(v_payload->>'interval_count')::bigint,NULLIF(v_payload->>'custom_interval_unit','null'),
         NULLIF(v_payload->>'end_date','null')::date) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_SCHEDULE'));
  END IF;
  IF (v_payload - ARRAY['template_transaction','amount_mode','frequency','interval_count','custom_interval_unit','end_date','next_due_date','reminder_lead_days']) <> '{}'::jsonb THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND')); END IF;
  IF v_payload=jsonb_build_object('template_transaction',v_rec.template_transaction,'amount_mode',v_rec.amount_mode,'frequency',v_rec.frequency,'interval_count',v_rec.interval_count,'custom_interval_unit',v_rec.custom_interval_unit,'end_date',v_rec.end_date,'next_due_date',v_rec.next_due_date,'reminder_lead_days',v_rec.reminder_lead_days) THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NO_OP')); END IF;
  UPDATE public.recurrences SET template_transaction=v_payload->'template_transaction',amount_mode=v_payload->>'amount_mode',frequency=v_payload->>'frequency',interval_count=(v_payload->>'interval_count')::bigint,custom_interval_unit=NULLIF(v_payload->>'custom_interval_unit','null'),end_date=NULLIF(v_payload->>'end_date','null')::date,next_due_date=(v_payload->>'next_due_date')::date,reminder_lead_days=(v_payload->>'reminder_lead_days')::smallint,revision=revision+1,updated_at=pg_catalog.clock_timestamp() WHERE id=v_id RETURNING * INTO v_rec;
  v_response:=jsonb_build_object('status','APPLIED','recurrence_id',v_id,'revision',v_rec.revision,'updated_at',v_rec.updated_at);
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) VALUES(v_uid,'RECURRENCE',v_id,v_rec.revision,'UPSERT',v_response||jsonb_build_object('rule',to_jsonb(v_rec)));
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status) VALUES(v_uid,v_op::text,'UPDATE_RECURRENCE',p_command->>'request_hash',v_response,'APPLIED');
  RETURN v_response;
EXCEPTION WHEN OTHERS THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
END;
$$;

REVOKE ALL ON FUNCTION public._rec_schedule_valid(date,date,text,bigint,text,date), public._rec_template_references_valid(jsonb,uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.create_recurrence_v1(jsonb), public.update_recurrence_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_recurrence_v1(jsonb), public.update_recurrence_v1(jsonb), public._rec_json_canonical(jsonb), public._rec_valid_integer(jsonb,numeric,numeric), public._rec_valid_date(jsonb) TO authenticated;
COMMIT;

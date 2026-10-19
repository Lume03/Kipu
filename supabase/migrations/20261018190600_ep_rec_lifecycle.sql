BEGIN;

CREATE OR REPLACE FUNCTION public.set_recurrence_status_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid(); v_id uuid; v_op uuid; v_expected bigint;
  v_target text; v_confirmed date; v_first_future date; v_anchor date; v_first_future_anchor date;
  v_rec public.recurrences%ROWTYPE; v_receipt internal.command_receipts%ROWTYPE;
  v_canonical text; v_response jsonb; v_n integer; v_step integer; v_month_delta integer;
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED')); END IF;
  IF pg_catalog.jsonb_typeof(p_command)<>'object'
     OR NOT (p_command ?& ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])
     OR p_command->>'command_type'<>'SET_RECURRENCE_STATUS'
     OR NOT public._rec_valid_integer(p_command->'contract_version',1,1)
     OR NOT public._rec_valid_integer(p_command->'expected_revision',1,9223372036854775807)
     OR pg_catalog.jsonb_typeof(p_command->'payload')<>'object'
     OR (p_command-ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])<>'{}'::jsonb
     OR pg_catalog.jsonb_typeof(p_command->'payload'->'status') IS DISTINCT FROM 'string'
     OR p_command->'payload'->>'status' NOT IN ('ACTIVE','PAUSED','CANCELLED') THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_target:=p_command->'payload'->>'status';
  IF (v_target='ACTIVE' AND ((p_command->'payload')-ARRAY['status','confirmed_next_due_date']<>'{}'::jsonb OR NOT (p_command->'payload'?'confirmed_next_due_date')))
     OR (v_target<>'ACTIVE' AND (p_command->'payload')-ARRAY['status']<>'{}'::jsonb) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_STATUS_PAYLOAD'));
  END IF;
  v_id:=(p_command->>'recurrence_id')::uuid; v_op:=(p_command->>'operation_id')::uuid;
  v_expected:=(p_command->>'expected_revision')::bigint;
  IF p_command->>'operation_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'recurrence_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  IF v_target='ACTIVE' THEN
    IF NOT public._rec_valid_date(p_command->'payload'->'confirmed_next_due_date') THEN
      RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_RESUME_DATE'));
    END IF;
    v_confirmed:=(p_command->'payload'->>'confirmed_next_due_date')::date;
  END IF;
  v_canonical:=public._rec_json_canonical(p_command-ARRAY['operation_id','request_hash']);
  IF pg_catalog.octet_length(v_canonical)>16384
     OR pg_catalog.encode(extensions.digest(v_canonical,'sha256'),'hex')<>p_command->>'request_hash' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','HASH_MISMATCH'));
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text||':'||v_op::text,0));
  SELECT * INTO v_receipt FROM internal.command_receipts WHERE user_id=v_uid AND idempotency_key=v_op::text;
  IF FOUND THEN
    IF v_receipt.command_type='SET_RECURRENCE_STATUS' AND v_receipt.request_hash=p_command->>'request_hash' THEN RETURN v_receipt.response_payload; END IF;
    RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','OPERATION_ID_REUSED'));
  END IF;
  SELECT * INTO v_rec FROM public.recurrences WHERE id=v_id AND user_id=v_uid AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NOT_FOUND')); END IF;
  IF v_rec.revision<>v_expected THEN RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','REVISION_MISMATCH','revision',v_rec.revision)); END IF;
  IF v_target='PAUSED' AND v_rec.status<>'ACTIVE' OR v_target='ACTIVE' AND v_rec.status<>'PAUSED' OR v_target='CANCELLED' AND v_rec.status NOT IN ('ACTIVE','PAUSED') THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_TRANSITION'));
  END IF;
  IF v_target='ACTIVE' THEN
    v_anchor:=v_rec.start_date;
    v_first_future_anchor:=CURRENT_DATE+1;
    IF v_rec.frequency IN ('WEEKLY','BIWEEKLY') OR (v_rec.frequency='CUSTOM' AND v_rec.custom_interval_unit='WEEK') THEN
      v_step:=CASE WHEN v_rec.frequency='WEEKLY' THEN 7 WHEN v_rec.frequency='BIWEEKLY' THEN 14 ELSE v_rec.interval_count::integer*7 END;
      v_n:=GREATEST(0,CEIL((v_first_future_anchor-v_anchor)::numeric/v_step)::integer);
      v_first_future:=v_anchor+(v_n*v_step);
      IF v_first_future<v_first_future_anchor THEN v_first_future:=v_first_future+v_step; END IF;
    ELSE
      v_step:=CASE WHEN v_rec.frequency='ANNUALLY' THEN 12 WHEN v_rec.frequency='CUSTOM' THEN v_rec.interval_count::integer ELSE 1 END;
      v_month_delta:=(pg_catalog.date_part('year',v_first_future_anchor)::integer-pg_catalog.date_part('year',v_anchor)::integer)*12
        +(pg_catalog.date_part('month',v_first_future_anchor)::integer-pg_catalog.date_part('month',v_anchor)::integer);
      v_n:=GREATEST(0,CEIL(v_month_delta::numeric/v_step)::integer);
      v_first_future:=(v_anchor+pg_catalog.make_interval(months=>v_n*v_step))::date;
      IF v_first_future<v_first_future_anchor THEN v_first_future:=(v_anchor+pg_catalog.make_interval(months=>(v_n+1)*v_step))::date; END IF;
    END IF;
    IF v_first_future IS NULL OR v_first_future>COALESCE(v_rec.end_date,'9999-12-31'::date) OR v_confirmed<>v_first_future THEN
      RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','RESUME_DATE_MISMATCH'));
    END IF;
    UPDATE public.recurrences SET status='ACTIVE',next_due_date=v_confirmed,revision=revision+1,updated_at=pg_catalog.clock_timestamp() WHERE id=v_id RETURNING * INTO v_rec;
  ELSE
    UPDATE public.recurrences SET status=v_target,revision=revision+1,updated_at=pg_catalog.clock_timestamp() WHERE id=v_id RETURNING * INTO v_rec;
  END IF;
  v_response:=jsonb_build_object('status','APPLIED','recurrence_id',v_id,'state',v_rec.status,'revision',v_rec.revision,'updated_at',v_rec.updated_at);
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) VALUES(v_uid,'RECURRENCE',v_id,v_rec.revision,'UPSERT',v_response||jsonb_build_object('rule',to_jsonb(v_rec)));
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status) VALUES(v_uid,v_op::text,'SET_RECURRENCE_STATUS',p_command->>'request_hash',v_response,'APPLIED');
  RETURN v_response;
EXCEPTION WHEN OTHERS THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
END;
$$;

REVOKE ALL ON FUNCTION public.set_recurrence_status_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_recurrence_status_v1(jsonb) TO authenticated;
COMMIT;

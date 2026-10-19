-- EP-MET CRUD: all entry points validate the authenticated owner and use a stable receipt.
BEGIN;

CREATE OR REPLACE FUNCTION public.create_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; a uuid; c bpchar(3); n text; target bigint;
  target_day date; color integer; prior jsonb; response jsonb; seq bigint;
BEGIN
  prior := internal.goal_check_command(p_payload,'CREATE_GOAL');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    g := (p_payload->>'goal_id')::uuid;
    n := pg_catalog.btrim(p_payload->'payload'->>'name');
    target := (p_payload->'payload'->>'target_minor')::bigint;
    c := (p_payload->'payload'->>'currency_code')::bpchar(3);
    a := NULLIF(p_payload->'payload'->>'linked_account_id','')::uuid;
    target_day := NULLIF(p_payload->'payload'->>'target_date','')::date;
    color := (p_payload->'payload'->>'color_argb')::integer;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR datetime_field_overflow OR invalid_datetime_format THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF g IS NULL OR n IS NULL OR pg_catalog.length(n) NOT BETWEEN 1 AND 120 OR
    target IS NULL OR target <= 0 OR c IS NULL OR c NOT IN ('PEN','USD') THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_GOAL');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.currencies WHERE code=c) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_CURRENCY');
  END IF;
  IF a IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.accounts
      WHERE id=a AND user_id=u AND currency_code=c AND NOT is_archived AND deleted_at IS NULL) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID');
  END IF;
  -- Owner-specific quota lock: concurrent creates cannot both consume the last slot.
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('kipu:goal-quota:'||u::text,0));
  IF EXISTS(SELECT 1 FROM public.goals WHERE id=g) THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','GOAL_ID_REUSED');
  END IF;
  IF NOT private.is_user_pro(u) AND
    (SELECT pg_catalog.count(*) FROM public.goals WHERE user_id=u AND deleted_at IS NULL
      AND status IN ('ACTIVE','COMPLETED')) >= 2 THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_LIMIT');
  END IF;
  INSERT INTO public.goals(id,user_id,name,target_minor,currency_code,target_date,
      linked_account_id,icon_key,color_argb,status,revision)
  VALUES(g,u,n,target,c,target_day,a,p_payload->'payload'->>'icon_key',color,'ACTIVE',1);
  seq := internal.goal_emit(g);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,'revision',1,
    'sequence',seq);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

CREATE OR REPLACE FUNCTION public.update_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; a uuid; c bpchar(3); n text; target bigint;
  expected bigint; target_day date; color integer; old public.goals%ROWTYPE;
  reserved bigint; new_status public.goal_status; prior jsonb; response jsonb; seq bigint;
BEGIN
  prior := internal.goal_check_command(p_payload,'UPDATE_GOAL');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    g := (p_payload->>'goal_id')::uuid;
    expected := (p_payload->>'expected_revision')::bigint;
    n := pg_catalog.btrim(p_payload->'payload'->>'name');
    target := (p_payload->'payload'->>'target_minor')::bigint;
    c := (p_payload->'payload'->>'currency_code')::bpchar(3);
    a := NULLIF(p_payload->'payload'->>'linked_account_id','')::uuid;
    target_day := NULLIF(p_payload->'payload'->>'target_date','')::date;
    color := (p_payload->'payload'->>'color_argb')::integer;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR datetime_field_overflow OR invalid_datetime_format THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF expected IS NULL OR expected < 1 OR n IS NULL OR pg_catalog.length(n) NOT BETWEEN 1 AND 120
    OR target IS NULL OR target <= 0 OR c IS NULL OR c NOT IN ('PEN','USD') THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_GOAL');
  END IF;
  SELECT * INTO old FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF old.revision <> expected THEN RETURN pg_catalog.jsonb_build_object('status','CONFLICT',
    'error','REVISION_MISMATCH','current_revision',old.revision); END IF;
  IF old.status IN ('ARCHIVED','LOCKED_BY_PLAN') THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_NOT_EDITABLE');
  END IF;
  IF old.currency_code <> c THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','CURRENCY_IMMUTABLE');
  END IF;
  IF a IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.accounts WHERE id=a AND user_id=u
    AND currency_code=c AND NOT is_archived AND deleted_at IS NULL) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID');
  END IF;
  reserved := internal.goal_reserved(u,g);
  IF old.linked_account_id IS DISTINCT FROM a AND reserved > 0 THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','RESERVE_UNRESOLVED');
  END IF;
  new_status := CASE WHEN reserved >= target THEN 'COMPLETED'::public.goal_status
     ELSE 'ACTIVE'::public.goal_status END;
  UPDATE public.goals SET name=n,target_minor=target,target_date=target_day,
    linked_account_id=a,icon_key=p_payload->'payload'->>'icon_key', color_argb=color,
    status=new_status,revision=revision+1,updated_at=pg_catalog.now() WHERE id=g AND user_id=u;
  seq := internal.goal_emit(g);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,
    'revision',old.revision+1,'sequence',seq);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_empty_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; expected bigint; old public.goals%ROWTYPE;
  prior jsonb; response jsonb; seq bigint;
BEGIN
  prior := internal.goal_check_command(p_payload,'DELETE_EMPTY_GOAL');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    g := (p_payload->>'goal_id')::uuid;
    expected := (p_payload->>'expected_revision')::bigint;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF expected IS NULL OR expected < 1 THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END IF;
  SELECT * INTO old FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF expected <> old.revision THEN RETURN pg_catalog.jsonb_build_object('status','CONFLICT',
    'error','REVISION_MISMATCH','current_revision',old.revision); END IF;
  IF EXISTS(SELECT 1 FROM public.goal_events WHERE goal_id=g AND user_id=u)
    OR EXISTS(SELECT 1 FROM internal.command_receipts WHERE user_id=u
      AND command_type IN ('RESERVE_GOAL','RELEASE_GOAL','REASSIGN_GOAL_RESERVE','ARCHIVE_GOAL')
      AND (command_payload->>'goal_id'=g::text OR command_payload->'payload'->>'source_goal_id'=g::text
        OR command_payload->'payload'->>'destination_goal_id'=g::text)) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','HISTORY_PRESERVED');
  END IF;
  seq := internal.goal_emit(g,NULL,true);
  DELETE FROM public.goals WHERE id=g AND user_id=u;
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,'revision',old.revision+1,'sequence',seq);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

REVOKE ALL ON FUNCTION public.create_goal_v1(jsonb),public.update_goal_v1(jsonb),
  public.delete_empty_goal_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_goal_v1(jsonb),public.update_goal_v1(jsonb),
  public.delete_empty_goal_v1(jsonb) TO authenticated;
COMMIT;

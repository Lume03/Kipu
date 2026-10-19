-- EP-MET release, reassign and archive: financial movements never occur here.
BEGIN;
CREATE OR REPLACE FUNCTION public.release_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; eid uuid; backing uuid; c bpchar(3);
  amount bigint; expected bigint; goal_row public.goals%ROWTYPE;
  reserved bigint; seq bigint; prior jsonb; response jsonb;
BEGIN
  prior := internal.goal_check_command(p_payload,'RELEASE_GOAL');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    g := (p_payload->>'goal_id')::uuid;
    expected := (p_payload->>'expected_revision')::bigint;
    eid := (p_payload->'payload'->>'event_id')::uuid;
    amount := (p_payload->'payload'->>'amount_minor')::bigint;
    backing := (p_payload->'payload'->>'backing_account_id')::uuid;
    c := (p_payload->'payload'->>'currency_code')::bpchar(3);
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF g IS NULL OR eid IS NULL OR backing IS NULL OR amount IS NULL OR amount<=0
    OR expected IS NULL OR expected<1 OR c IS NULL THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND'); END IF;
  -- Account lock shares serialization with RESERVE and REASSIGN.
  PERFORM 1 FROM public.accounts WHERE id=backing AND user_id=u AND currency_code=c AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  SELECT * INTO goal_row FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND OR goal_row.currency_code<>c THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF goal_row.revision<>expected THEN RETURN pg_catalog.jsonb_build_object('status','CONFLICT',
    'error','REVISION_MISMATCH','current_revision',goal_row.revision); END IF;
  IF goal_row.status NOT IN ('ACTIVE','COMPLETED','LOCKED_BY_PLAN') THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_NOT_ELIGIBLE'); END IF;
  IF EXISTS(SELECT 1 FROM public.goal_events WHERE id=eid) THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','EVENT_ID_REUSED'); END IF;
  reserved := internal.goal_reserved(u,g,backing);
  IF reserved<amount THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','RESERVE_EXCEEDED'); END IF;
  INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,
    currency_code,backing_account_id,operation_id)
  VALUES(eid,u,g,'RELEASE',-amount,-amount,c,backing,(p_payload->>'operation_id')::uuid);
  reserved := internal.goal_reserved(u,g);
  UPDATE public.goals SET status=CASE WHEN status='LOCKED_BY_PLAN' THEN 'LOCKED_BY_PLAN'::public.goal_status
     WHEN reserved>=target_minor THEN 'COMPLETED'::public.goal_status ELSE 'ACTIVE'::public.goal_status END,
     revision=revision+1,updated_at=pg_catalog.now() WHERE id=g AND user_id=u;
  seq := internal.goal_emit(g,eid);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,'event_id',eid,
      'revision',goal_row.revision+1,'sequence',seq,'reserved_minor',reserved);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

CREATE OR REPLACE FUNCTION public.reassign_goal_reserve_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); src uuid; dest uuid; backing uuid; eid_src uuid; eid_dest uuid;
  amount bigint; rev_src bigint; rev_dest bigint; c bpchar(3); g uuid;
  src_row public.goals%ROWTYPE; dest_row public.goals%ROWTYPE;
  seq bigint; prior jsonb; response jsonb;
BEGIN
  prior := internal.goal_check_command(p_payload,'REASSIGN_GOAL_RESERVE');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    src := (p_payload->'payload'->>'source_goal_id')::uuid;
    dest := (p_payload->'payload'->>'destination_goal_id')::uuid;
    backing := (p_payload->'payload'->>'backing_account_id')::uuid;
    eid_src := (p_payload->'payload'->>'source_event_id')::uuid;
    eid_dest := (p_payload->'payload'->>'destination_event_id')::uuid;
    amount := (p_payload->'payload'->>'amount_minor')::bigint;
    c := (p_payload->'payload'->>'currency_code')::bpchar(3);
    rev_src := (p_payload->'payload'->>'source_expected_revision')::bigint;
    rev_dest := (p_payload->'payload'->>'destination_expected_revision')::bigint;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF src IS NULL OR dest IS NULL OR src=dest OR eid_src IS NULL OR eid_dest IS NULL OR eid_src=eid_dest
    OR backing IS NULL OR amount IS NULL OR amount<=0 OR rev_src IS NULL OR rev_dest IS NULL OR
    rev_src<1 OR rev_dest<1 OR c IS NULL OR p_payload->>'goal_id' <> src::text THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND'); END IF;
  PERFORM 1 FROM public.accounts WHERE id=backing AND user_id=u AND currency_code=c AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  -- Deterministic lock order between source and destination.
  FOR g IN SELECT x FROM pg_catalog.unnest(ARRAY[src,dest]) AS x ORDER BY x LOOP
    PERFORM 1 FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  END LOOP;
  SELECT * INTO src_row FROM public.goals WHERE id=src AND user_id=u;
  SELECT * INTO dest_row FROM public.goals WHERE id=dest AND user_id=u;
  IF src_row.currency_code<>c OR dest_row.currency_code<>c
    OR src_row.status='ARCHIVED' OR dest_row.status<>'ACTIVE' THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_NOT_ELIGIBLE'); END IF;
  IF src_row.revision<>rev_src OR dest_row.revision<>rev_dest OR
      (p_payload->>'expected_revision')::bigint<>rev_src THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','REVISION_MISMATCH',
      'current_revision',src_row.revision); END IF;
  IF EXISTS(SELECT 1 FROM public.goal_events WHERE id IN (eid_src,eid_dest)) THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','EVENT_ID_REUSED'); END IF;
  IF internal.goal_reserved(u,src,backing)<amount THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','RESERVE_EXCEEDED'); END IF;
  INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,currency_code,
      backing_account_id,operation_id)
  VALUES(eid_src,u,src,'REASSIGNMENT_OUT',-amount,-amount,c,backing,(p_payload->>'operation_id')::uuid),
        (eid_dest,u,dest,'REASSIGNMENT_IN',amount,amount,c,backing,(p_payload->>'operation_id')::uuid);
  UPDATE public.goals SET status=CASE WHEN status='LOCKED_BY_PLAN' THEN 'LOCKED_BY_PLAN'::public.goal_status
      WHEN internal.goal_reserved(u,src)>=target_minor THEN 'COMPLETED'::public.goal_status
      ELSE 'ACTIVE'::public.goal_status END,revision=revision+1,updated_at=pg_catalog.now()
    WHERE id=src AND user_id=u;
  UPDATE public.goals SET status=CASE WHEN internal.goal_reserved(u,dest)>=target_minor
      THEN 'COMPLETED'::public.goal_status ELSE 'ACTIVE'::public.goal_status END,
    revision=revision+1,updated_at=pg_catalog.now() WHERE id=dest AND user_id=u;
  PERFORM internal.goal_emit(src,eid_src);
  seq := internal.goal_emit(dest,eid_dest);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',src,
    'event_id',eid_src,'revision',src_row.revision+1,'sequence',seq);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

CREATE OR REPLACE FUNCTION public.archive_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; expected bigint; goal_row public.goals%ROWTYPE;
  prior jsonb; response jsonb; seq bigint;
BEGIN
  prior := internal.goal_check_command(p_payload,'ARCHIVE_GOAL');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    g := (p_payload->>'goal_id')::uuid;
    expected := (p_payload->>'expected_revision')::bigint;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF g IS NULL OR expected IS NULL OR expected<1 THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND'); END IF;
  -- Lock every backing account before the goal to match the reserve/release order.
  PERFORM 1 FROM public.accounts a WHERE a.id IN (
    SELECT DISTINCT backing_account_id FROM public.goal_events WHERE user_id=u AND goal_id=g)
    AND a.user_id=u ORDER BY a.id FOR UPDATE;
  SELECT * INTO goal_row FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF goal_row.revision<>expected THEN RETURN pg_catalog.jsonb_build_object('status','CONFLICT',
    'error','REVISION_MISMATCH','current_revision',goal_row.revision); END IF;
  IF internal.goal_reserved(u,g)<>0 THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
    'error','RESERVE_UNRESOLVED'); END IF;
  IF goal_row.status='ARCHIVED' THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
    'error','ALREADY_ARCHIVED'); END IF;
  UPDATE public.goals SET status='ARCHIVED',revision=revision+1,updated_at=pg_catalog.now()
    WHERE id=g AND user_id=u;
  seq := internal.goal_emit(g);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,
    'revision',goal_row.revision+1,'sequence',seq);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;
REVOKE ALL ON FUNCTION public.release_goal_v1(jsonb),public.reassign_goal_reserve_v1(jsonb),
  public.archive_goal_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.release_goal_v1(jsonb),public.reassign_goal_reserve_v1(jsonb),
  public.archive_goal_v1(jsonb) TO authenticated;
COMMIT;

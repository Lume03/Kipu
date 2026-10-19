-- EP-MET T045: serialize RELEASE_GOAL with Free quota consumers and support
-- an explicit atomic replacement when releasing reactivates a completed goal.
BEGIN;

CREATE OR REPLACE FUNCTION public.release_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; replacement_id uuid; eid uuid; backing uuid;
  c bpchar(3); amount bigint; expected bigint; replacement_expected bigint;
  account_to_lock uuid; goal_to_lock uuid; goal_row public.goals%ROWTYPE;
  replacement public.goals%ROWTYPE; reserved bigint; seq bigint;
  source_sequence bigint; replacement_sequence bigint; source_revision bigint;
  needs_replacement boolean; prior jsonb; response jsonb;
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
    replacement_id := NULLIF(p_payload->'payload'->>'replacement_goal_id','')::uuid;
    replacement_expected := NULLIF(p_payload->'payload'->>'replacement_expected_revision','')::bigint;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF g IS NULL OR eid IS NULL OR backing IS NULL OR amount IS NULL OR amount<=0
    OR expected IS NULL OR expected<1 OR c IS NULL OR c NOT IN ('PEN','USD')
    OR ((replacement_id IS NULL) <> (replacement_expected IS NULL))
    OR (replacement_expected IS NOT NULL AND replacement_expected<1) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END IF;

  -- Every quota consumer acquires this first; RELEASE must not race CREATE/UPDATE.
  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('kipu:goal-quota:'||u::text,0));

  -- Preliminary reads only discover lock candidates. Ownership is revalidated
  -- after deterministic account and goal locks are acquired.
  IF NOT EXISTS(SELECT 1 FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL)
    THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF replacement_id IS NOT NULL AND NOT EXISTS(
    SELECT 1 FROM public.goals WHERE id=replacement_id AND user_id=u AND deleted_at IS NULL)
    THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;

  -- Lock all relevant backing/linked accounts, then source/replacement goals,
  -- each in UUID order, matching UPDATE_GOAL and the approved lock hierarchy.
  FOR account_to_lock IN
    SELECT DISTINCT candidates.account_id FROM (
      SELECT backing AS account_id
      UNION ALL
      SELECT linked_account_id FROM public.goals
        WHERE id IN (g,replacement_id) AND user_id=u AND deleted_at IS NULL
      UNION ALL
      SELECT DISTINCT backing_account_id FROM public.goal_events
        WHERE user_id=u AND goal_id IN (g,replacement_id)
    ) AS candidates
    WHERE candidates.account_id IS NOT NULL ORDER BY candidates.account_id
  LOOP
    PERFORM 1 FROM public.accounts WHERE id=account_to_lock AND user_id=u FOR UPDATE;
  END LOOP;
  FOR goal_to_lock IN SELECT x FROM pg_catalog.unnest(ARRAY[g,replacement_id]) AS x
      WHERE x IS NOT NULL ORDER BY x
  LOOP
    PERFORM 1 FROM public.goals WHERE id=goal_to_lock AND user_id=u AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  END LOOP;
  SELECT * INTO goal_row FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL;
  IF replacement_id IS NOT NULL THEN
    SELECT * INTO replacement FROM public.goals WHERE id=replacement_id AND user_id=u AND deleted_at IS NULL;
  END IF;

  IF goal_row.currency_code<>c OR NOT EXISTS(SELECT 1 FROM public.accounts
    WHERE id=backing AND user_id=u AND currency_code=c AND deleted_at IS NULL) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID');
  END IF;
  IF goal_row.revision<>expected THEN RETURN pg_catalog.jsonb_build_object('status','CONFLICT',
    'error','REVISION_MISMATCH','current_revision',goal_row.revision); END IF;
  IF goal_row.status NOT IN ('ACTIVE','COMPLETED','LOCKED_BY_PLAN') THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_NOT_ELIGIBLE'); END IF;
  IF EXISTS(SELECT 1 FROM public.goal_events WHERE id=eid) THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','EVENT_ID_REUSED'); END IF;

  reserved := internal.goal_reserved(u,g,backing);
  IF reserved<amount THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','RESERVE_EXCEEDED'); END IF;
  needs_replacement := goal_row.status='COMPLETED'
    AND internal.goal_reserved(u,g)-amount<goal_row.target_minor
    AND NOT private.is_user_pro(u)
    AND (SELECT pg_catalog.count(*) FROM public.goals WHERE user_id=u AND deleted_at IS NULL
      AND status='ACTIVE')>=2;
  IF needs_replacement AND replacement_id IS NULL THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','REPLACEMENT_REQUIRED');
  END IF;
  IF replacement_id IS NOT NULL THEN
    IF replacement_id=g OR replacement.status<>'ACTIVE' THEN
      RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','REPLACEMENT_NOT_ACTIVE');
    END IF;
    IF replacement.revision<>replacement_expected THEN
      RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','REPLACEMENT_REVISION_MISMATCH',
        'replacement_current_revision',replacement.revision);
    END IF;
    IF NOT needs_replacement THEN
      RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','REPLACEMENT_NOT_REQUIRED');
    END IF;
  END IF;

  INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,
    currency_code,backing_account_id,operation_id)
  VALUES(eid,u,g,'RELEASE',-amount,-amount,c,backing,(p_payload->>'operation_id')::uuid);
  reserved := internal.goal_reserved(u,g);
  IF replacement_id IS NOT NULL THEN
    UPDATE public.goals SET status='LOCKED_BY_PLAN',revision=revision+1,
      updated_at=pg_catalog.now() WHERE id=replacement_id AND user_id=u;
    replacement_sequence := internal.goal_emit(replacement_id);
    replacement.revision := replacement.revision+1;
  END IF;
  UPDATE public.goals SET status=CASE
      WHEN status='LOCKED_BY_PLAN' THEN 'LOCKED_BY_PLAN'::public.goal_status
      WHEN reserved>=target_minor THEN 'COMPLETED'::public.goal_status
      ELSE 'ACTIVE'::public.goal_status END,
    revision=revision+1,updated_at=pg_catalog.now() WHERE id=g AND user_id=u;
  source_sequence := internal.goal_emit(g,eid);
  source_revision := goal_row.revision+1;
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,'event_id',eid,
    'revision',source_revision,'sequence',source_sequence,'reserved_minor',reserved);
  IF replacement_id IS NOT NULL THEN
    response := response || pg_catalog.jsonb_build_object('goals',pg_catalog.jsonb_build_array(
      pg_catalog.jsonb_build_object('goal_id',g,'status',
        CASE WHEN goal_row.status='LOCKED_BY_PLAN' THEN 'LOCKED_BY_PLAN' ELSE 'ACTIVE' END,
        'revision',source_revision,'sequence',source_sequence),
      pg_catalog.jsonb_build_object('goal_id',replacement_id,'status','LOCKED_BY_PLAN',
        'revision',replacement.revision,'sequence',replacement_sequence)));
  END IF;
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;
REVOKE ALL ON FUNCTION public.release_goal_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.release_goal_v1(jsonb) TO authenticated;
COMMIT;

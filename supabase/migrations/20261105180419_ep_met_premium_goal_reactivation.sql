-- HU-30.8: Premium renewal can reactivate a plan-locked goal through the existing
-- revisioned command, preserving events, reserves, receipts and locking order.
-- New migration only; no historical migration is rewritten.
BEGIN;
CREATE OR REPLACE FUNCTION public.update_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; a uuid; c bpchar(3); n text; target bigint;
  expected bigint; target_day date; color integer; old public.goals%ROWTYPE;
  reserved bigint; new_status public.goal_status; prior jsonb; response jsonb;
  seq bigint; replacement_id uuid; replacement_expected bigint;
  replacement public.goals%ROWTYPE; account_id uuid; source_revision bigint;
  source_sequence bigint; replacement_sequence bigint; needs_replacement boolean;
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
    replacement_id := NULLIF(p_payload->'payload'->>'replacement_goal_id','')::uuid;
    replacement_expected := NULLIF(p_payload->'payload'->>'replacement_expected_revision','')::bigint;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR datetime_field_overflow OR invalid_datetime_format THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF expected IS NULL OR expected < 1 OR n IS NULL OR pg_catalog.length(n) NOT BETWEEN 1 AND 120
    OR target IS NULL OR target <= 0 OR c IS NULL OR c NOT IN ('PEN','USD') THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_GOAL');
  END IF;
  IF (replacement_id IS NULL) <> (replacement_expected IS NULL) OR
     (replacement_expected IS NOT NULL AND replacement_expected < 1) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_REPLACEMENT');
  END IF;

  -- Serialize quota consumers first, then backing accounts and goal rows in UUID order.
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('kipu:goal-quota:'||u::text,0));
  SELECT * INTO old FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF replacement_id IS NOT NULL THEN
    SELECT * INTO replacement FROM public.goals WHERE id=replacement_id AND user_id=u AND deleted_at IS NULL;
    IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  END IF;
  FOR account_id IN
    SELECT DISTINCT candidates.account_id FROM (
      SELECT a AS account_id
      UNION ALL
      SELECT linked_account_id FROM public.goals
        WHERE id IN (g, replacement_id) AND user_id=u
    ) AS candidates
    WHERE candidates.account_id IS NOT NULL
    ORDER BY candidates.account_id
  LOOP
    PERFORM 1 FROM public.accounts WHERE id=account_id AND user_id=u FOR UPDATE;
  END LOOP;
  FOR account_id IN
    SELECT id FROM public.goals WHERE id IN (g, replacement_id) AND user_id=u ORDER BY id
  LOOP
    PERFORM 1 FROM public.goals WHERE id=account_id AND user_id=u AND deleted_at IS NULL FOR UPDATE;
  END LOOP;
  SELECT * INTO old FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL;
  IF replacement_id IS NOT NULL THEN
    SELECT * INTO replacement FROM public.goals WHERE id=replacement_id AND user_id=u AND deleted_at IS NULL;
  END IF;
  IF old.revision <> expected THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','REVISION_MISMATCH','current_revision',old.revision);
  END IF;
  IF old.status = 'ARCHIVED' OR (old.status = 'LOCKED_BY_PLAN' AND NOT private.is_user_pro(u)) THEN
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
  needs_replacement := old.status='COMPLETED' AND new_status='ACTIVE'
    AND NOT private.is_user_pro(u)
    AND (SELECT pg_catalog.count(*) FROM public.goals WHERE user_id=u AND deleted_at IS NULL
      AND status='ACTIVE' AND id<>g) >= 2;
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

  IF replacement_id IS NOT NULL THEN
    UPDATE public.goals SET status='LOCKED_BY_PLAN',revision=revision+1,
      updated_at=pg_catalog.now() WHERE id=replacement_id AND user_id=u;
    replacement_sequence := internal.goal_emit(replacement_id);
    replacement.revision := replacement.revision+1;
  END IF;
  UPDATE public.goals SET name=n,target_minor=target,target_date=target_day,
    linked_account_id=a,icon_key=p_payload->'payload'->>'icon_key', color_argb=color,
    status=new_status,revision=revision+1,updated_at=pg_catalog.now() WHERE id=g AND user_id=u;
  source_sequence := internal.goal_emit(g);
  source_revision := old.revision+1;
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,
    'revision',source_revision,'sequence',source_sequence);
  IF replacement_id IS NOT NULL THEN
    response := response || pg_catalog.jsonb_build_object('goals',pg_catalog.jsonb_build_array(
      pg_catalog.jsonb_build_object('goal_id',g,'status',new_status,
        'revision',source_revision,'sequence',source_sequence),
      pg_catalog.jsonb_build_object('goal_id',replacement_id,'status','LOCKED_BY_PLAN',
        'revision',replacement.revision,'sequence',replacement_sequence)));
  END IF;
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

REVOKE ALL ON FUNCTION public.update_goal_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_goal_v1(jsonb) TO authenticated;
COMMIT;

-- EP-MET reservation / optional explicit intra-user transfer.
BEGIN;
CREATE OR REPLACE FUNCTION public.reserve_goal_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); g uuid; eid uuid; backing uuid; source_account uuid;
  destination_account uuid; account_to_lock uuid; account_row public.accounts%ROWTYPE;
  goal_row public.goals%ROWTYPE; amount bigint; expected bigint; c bpchar(3);
  transfer_obj jsonb; tx uuid; free_backing bigint; free_source bigint;
  goal_total bigint; prior jsonb; response jsonb; seq bigint;
BEGIN
  prior := internal.goal_check_command(p_payload,'RESERVE_GOAL');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    g := (p_payload->>'goal_id')::uuid;
    expected := (p_payload->>'expected_revision')::bigint;
    eid := (p_payload->'payload'->>'event_id')::uuid;
    amount := (p_payload->'payload'->>'amount_minor')::bigint;
    c := (p_payload->'payload'->>'currency_code')::bpchar(3);
    backing := NULLIF(p_payload->'payload'->>'backing_account_id','')::uuid;
    transfer_obj := p_payload->'payload'->'transfer';
    IF transfer_obj IS NOT NULL THEN
      source_account := (transfer_obj->>'source_account_id')::uuid;
      destination_account := (transfer_obj->>'destination_account_id')::uuid;
    END IF;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF g IS NULL OR eid IS NULL OR backing IS NULL OR amount IS NULL OR amount <= 0
    OR expected IS NULL OR expected < 1 OR c IS NULL OR c NOT IN ('PEN','USD')
    OR (transfer_obj IS NOT NULL AND (pg_catalog.jsonb_typeof(transfer_obj) <> 'object'
      OR source_account IS NULL OR destination_account IS NULL)) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END IF;
  IF transfer_obj IS NOT NULL AND (destination_account <> backing
      OR transfer_obj->>'currency_code' IS DISTINCT FROM pg_catalog.btrim(c)) THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID');
  END IF;
  IF source_account=destination_account THEN
    transfer_obj := NULL;
  END IF;
  -- Every account involved in a transfer is locked in UUID order, preventing
  -- both double-reserve races and transfer/reverse-transfer deadlocks.
  FOR account_to_lock IN SELECT DISTINCT x FROM pg_catalog.unnest(
      ARRAY[backing,CASE WHEN transfer_obj IS NOT NULL THEN source_account ELSE NULL END]) AS x
      WHERE x IS NOT NULL ORDER BY x LOOP
    SELECT * INTO account_row FROM public.accounts WHERE id=account_to_lock AND user_id=u
      AND currency_code=c AND NOT is_archived AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
      'error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  END LOOP;
  SELECT * INTO goal_row FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
    'error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  IF goal_row.revision <> expected THEN RETURN pg_catalog.jsonb_build_object('status','CONFLICT',
    'error','REVISION_MISMATCH','current_revision',goal_row.revision); END IF;
  IF goal_row.status <> 'ACTIVE' OR goal_row.currency_code <> c THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_NOT_ELIGIBLE');
  END IF;
  IF EXISTS(SELECT 1 FROM public.goal_events WHERE id=eid) THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','EVENT_ID_REUSED');
  END IF;
  free_backing := internal.goal_confirmed_balance(u,backing) - internal.goal_reserved(u,NULL,backing);
  IF transfer_obj IS NOT NULL THEN
    free_source := internal.goal_confirmed_balance(u,source_account) - internal.goal_reserved(u,NULL,source_account);
    IF free_source < amount THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
      'error','INSUFFICIENT_BACKING'); END IF;
    IF free_backing < 0 THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
      'error','INSUFFICIENT_BACKING'); END IF;
    -- Only an explicit transfer modifies accounts; two ledger entries balance to zero.
    tx := extensions.gen_random_uuid();
    INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,
      amount_minor,currency_code,transfer_group_id,status,revision,notes)
    VALUES(tx,u,source_account,'TRANSFER','GOAL_FUNDING',amount,c,tx,'CONFIRMED',1,
      'Transferencia explícita para reserva de meta');
    INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role)
    VALUES(tx,u,source_account,-amount,c,'SOURCE'),(tx,u,backing,amount,c,'DESTINATION');
    free_backing := free_backing+amount;
    -- Standard movement sync can consume the transaction before the GOAL_EVENT.
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    SELECT u,'TRANSACTION',tx,1,'UPSERT',pg_catalog.to_jsonb(t) FROM public.transactions t WHERE t.id=tx;
  END IF;
  IF free_backing < amount THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED',
    'error','INSUFFICIENT_BACKING'); END IF;
  INSERT INTO public.goal_events(id,user_id,goal_id,transaction_id,event_type,amount_minor,
    delta_minor,currency_code,backing_account_id,operation_id)
  VALUES(eid,u,g,tx,'ALLOCATION',amount,amount,c,backing,(p_payload->>'operation_id')::uuid);
  goal_total := internal.goal_reserved(u,g);
  UPDATE public.goals SET status=CASE WHEN goal_total>=target_minor THEN 'COMPLETED'::public.goal_status
     ELSE 'ACTIVE'::public.goal_status END, revision=revision+1,updated_at=pg_catalog.now()
  WHERE id=g AND user_id=u;
  seq := internal.goal_emit(g,eid);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',g,'event_id',eid,
    'transaction_id',tx,'revision',goal_row.revision+1,'sequence',seq,
    'reserved_minor',goal_total,'confirmed_balance_minor',internal.goal_confirmed_balance(u,backing),
    'available_minor',internal.goal_confirmed_balance(u,backing)-internal.goal_reserved(u,NULL,backing));
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;
REVOKE ALL ON FUNCTION public.reserve_goal_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.reserve_goal_v1(jsonb) TO authenticated;
COMMIT;

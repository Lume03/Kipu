-- Owner-approved account archive feed correction. No account/ledger data is rewritten.
CREATE OR REPLACE FUNCTION internal.publish_account_state_v1(p_account public.accounts)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE latest internal.sync_changes;
BEGIN
  IF p_account.deleted_at IS NOT NULL THEN RETURN; END IF;
  SELECT * INTO latest FROM internal.sync_changes
  WHERE user_id=p_account.user_id AND entity_type='ACCOUNT' AND entity_id=p_account.id
  ORDER BY sequence DESC LIMIT 1;
  IF latest.revision IS NOT DISTINCT FROM p_account.revision
     AND (latest.payload->>'is_archived')::boolean IS NOT DISTINCT FROM p_account.is_archived
     AND latest.operation IN ('UPSERT','ARCHIVE') THEN RETURN; END IF;
  -- UPSERT carries the full snapshot, so a fresh restore needs no preexisting row.
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
  VALUES(p_account.user_id,'ACCOUNT',p_account.id,p_account.revision,'UPSERT',jsonb_build_object(
    'id',p_account.id,'creation_operation_id',p_account.creation_operation_id,
    'alias',p_account.name,'type',p_account.account_type,'currency',p_account.currency_code,
    'preset_id',p_account.preset_id,'initial_balance_minor_units',p_account.initial_balance_minor_units,
    'opened_at',p_account.opened_at,'is_archived',p_account.is_archived,
    'linked_account_id',p_account.linked_account_id,'color',p_account.color,'icon',p_account.icon));
END;
$$;
REVOKE ALL ON FUNCTION internal.publish_account_state_v1(public.accounts) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION internal.publish_account_update_v1()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE current_account public.accounts;
BEGIN
  -- Deferred until RPCs have emitted their existing events; reread the final row.
  SELECT * INTO current_account FROM public.accounts WHERE id=NEW.id AND user_id=NEW.user_id FOR UPDATE;
  IF FOUND THEN PERFORM internal.publish_account_state_v1(current_account); END IF;
  RETURN NULL;
END;
$$;
REVOKE ALL ON FUNCTION internal.publish_account_update_v1() FROM PUBLIC,anon,authenticated;
CREATE CONSTRAINT TRIGGER accounts_sync_update
AFTER UPDATE ON public.accounts DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION internal.publish_account_update_v1();

-- Idempotent repair, serialized with concurrent account commands. Append evidence only.
DO $$
DECLARE account_row public.accounts;
BEGIN
  FOR account_row IN SELECT * FROM public.accounts WHERE deleted_at IS NULL ORDER BY id FOR UPDATE LOOP
    PERFORM internal.publish_account_state_v1(account_row);
  END LOOP;
END;
$$;

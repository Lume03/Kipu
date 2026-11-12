BEGIN;
CREATE OR REPLACE FUNCTION internal.account_sync_projection_v1(a public.accounts)
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object('id',a.id,'creation_operation_id',a.creation_operation_id,
   'alias',a.name,'type',a.account_type,'currency',a.currency_code,'preset_id',a.preset_id,
   'initial_balance_minor_units',a.initial_balance_minor_units,'opened_at',a.opened_at,
   'is_archived',a.is_archived,'linked_account_id',a.linked_account_id,'color',a.color,'icon',a.icon,
   'effective_opening_balance_minor_units',COALESCE((SELECT sum(m.amount_minor_units)
     FROM public.financial_movements m WHERE m.user_id=a.user_id AND m.account_id=a.id
       AND m.status='POSTED' AND (m.kind='OPENING' OR m.adjusts_movement_id IN
         (SELECT id FROM public.financial_movements WHERE user_id=a.user_id AND opening_account_id=a.id)
         OR m.reverses_movement_id IN (SELECT id FROM public.financial_movements
           WHERE user_id=a.user_id AND account_id=a.id AND kind IN ('OPENING','ADJUSTMENT')))),0),
   'opening_movements',COALESCE((SELECT jsonb_agg(to_jsonb(m) ORDER BY m.created_at,m.operation_sequence,m.id)
     FROM public.financial_movements m WHERE m.user_id=a.user_id AND m.account_id=a.id
       AND (m.kind='OPENING' OR m.adjusts_movement_id IN
         (SELECT id FROM public.financial_movements WHERE user_id=a.user_id AND opening_account_id=a.id)
         OR m.reverses_movement_id IN (SELECT id FROM public.financial_movements
           WHERE user_id=a.user_id AND account_id=a.id AND kind IN ('OPENING','ADJUSTMENT')))), '[]'::jsonb))
$$;
REVOKE ALL ON FUNCTION internal.account_sync_projection_v1(public.accounts) FROM PUBLIC,anon,authenticated,service_role;
DO $$
DECLARE executor_name text;
BEGIN
 SELECT pg_catalog.pg_get_userbyid(proowner) INTO executor_name FROM pg_catalog.pg_proc
 WHERE oid='public.pull_financial_changes_v1(integer,bigint,integer)'::regprocedure;
 EXECUTE pg_catalog.format('ALTER FUNCTION internal.account_sync_projection_v1(public.accounts) OWNER TO %I',executor_name);
END $$;


-- Publish complete account snapshots; preserve the existing ACCOUNT entity and cursor.
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
  VALUES(p_account.user_id,'ACCOUNT',p_account.id,p_account.revision,'UPSERT',internal.account_sync_projection_v1(p_account));
END;
$$;
REVOKE ALL ON FUNCTION internal.publish_account_state_v1(public.accounts) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION internal.post_opening_movement_ledger_v1()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE source public.financial_movements%ROWTYPE; posting_role internal.ledger_entries.entry_role%TYPE;
BEGIN
  IF NEW.status <> 'POSTED' OR NEW.amount_minor_units = 0 THEN RETURN NEW; END IF;
  IF NEW.kind = 'OPENING' AND NEW.opening_account_id = NEW.account_id THEN
    NULL;
  ELSIF NEW.kind IN ('REVERSAL','ADJUSTMENT')
      AND NOT (NEW.reverses_movement_id IS NOT NULL AND NEW.adjusts_movement_id IS NOT NULL) THEN
    SELECT * INTO source FROM public.financial_movements
      WHERE id=COALESCE(NEW.reverses_movement_id,NEW.adjusts_movement_id)
        AND status='POSTED' AND (kind='OPENING' AND opening_account_id=account_id
          OR kind='ADJUSTMENT' AND EXISTS (SELECT 1 FROM public.financial_movements o
            WHERE o.id=financial_movements.adjusts_movement_id AND o.kind='OPENING'
              AND o.user_id=financial_movements.user_id AND o.account_id=financial_movements.account_id));
    IF NOT FOUND OR source.user_id<>NEW.user_id OR source.account_id<>NEW.account_id
        OR source.currency<>NEW.currency THEN RETURN NEW; END IF;
  ELSE RETURN NEW;
  END IF;
  posting_role := CASE WHEN NEW.kind='REVERSAL' THEN 'REVERSAL' ELSE 'PRIMARY' END;
  INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,
      currency_code,entry_role,occurred_at,source_kind,financial_movement_id)
  VALUES(NULL,NEW.user_id,NEW.account_id,NEW.amount_minor_units,NEW.currency,
      posting_role,
      NEW.effective_at,'FINANCIAL_MOVEMENT',NEW.id)
  ON CONFLICT (financial_movement_id) WHERE financial_movement_id IS NOT NULL DO NOTHING;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION internal.post_opening_movement_ledger_v1() FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.record_opening_adjustment_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_account_id uuid;
    v_reversal_movement_id uuid;
    v_adjustment_movement_id uuid;
    v_new_amount bigint;
    v_new_effective_at timestamptz;
    v_opening_movement record;
    v_response jsonb;
    v_account public.accounts;
    v_active public.financial_movements;
    v_receipt internal.command_receipts;
    v_hash text;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_account_id := (p_command->>'account_id')::uuid;
    v_reversal_movement_id := (p_command->>'reversal_movement_id')::uuid;
    v_adjustment_movement_id := (p_command->>'adjustment_movement_id')::uuid;
    v_new_amount := (p_command->>'new_amount_minor_units')::bigint;
    v_new_effective_at := (p_command->>'new_effective_at')::timestamptz;

    IF v_operation_id IS NULL OR v_account_id IS NULL OR v_reversal_movement_id IS NULL
       OR v_adjustment_movement_id IS NULL OR v_new_amount IS NULL OR v_new_effective_at IS NULL THEN
        RAISE EXCEPTION 'INVALID_COMMAND: Missing opening adjustment fields';
    END IF;
    -- Hash the actual JSON, never trust a caller-provided hash.
    v_hash := pg_catalog.encode(extensions.digest(p_command::text, 'sha256'), 'hex');
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user_id::text || ':' || v_operation_id::text, 0));
    SELECT * INTO v_receipt FROM internal.command_receipts
      WHERE user_id=v_user_id AND idempotency_key=v_operation_id::text;
    IF FOUND THEN
      IF v_receipt.command_type='RECORD_OPENING_ADJUSTMENT_V1' AND v_receipt.request_hash=v_hash THEN
        RETURN v_receipt.response_payload;
      END IF;
      RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object(
        'code','IDEMPOTENCY_CONFLICT','field','operation_id','retryable',false));
    END IF;
    SELECT * INTO v_account FROM public.accounts
      WHERE id=v_account_id AND user_id=v_user_id AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'ACCOUNT_NOT_FOUND'; END IF;
    IF v_new_amount < 0 THEN
        RAISE EXCEPTION 'INVALID_AMOUNT: Adjusted opening balance cannot be negative';
    END IF;

    SELECT * INTO v_opening_movement
    FROM public.financial_movements
    WHERE user_id = v_user_id AND account_id = v_account_id AND kind = 'OPENING';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'OPENING_NOT_FOUND: Opening movement not found for account';
    END IF;

    SELECT m.* INTO v_active FROM public.financial_movements m
    WHERE m.user_id=v_user_id AND m.account_id=v_account_id AND m.status='POSTED'
      AND (m.id=v_opening_movement.id OR (m.kind='ADJUSTMENT' AND m.adjusts_movement_id=v_opening_movement.id))
      AND NOT EXISTS (SELECT 1 FROM public.financial_movements r
        WHERE r.user_id=m.user_id AND r.account_id=m.account_id AND r.kind='REVERSAL'
          AND r.status='POSTED' AND r.reverses_movement_id=m.id)
    ORDER BY (m.kind='ADJUSTMENT') DESC,m.created_at DESC,m.operation_sequence DESC,m.id DESC LIMIT 1;
    IF NOT FOUND THEN RAISE EXCEPTION 'ACTIVE_OPENING_NOT_FOUND'; END IF;

    -- Revert the current unreversed fact, while keeping the original anchor below.
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units,
        currency, account_id, effective_at, status, reverses_movement_id
    ) VALUES (
        v_reversal_movement_id, v_operation_id, 0, v_user_id, 'REVERSAL', -v_active.amount_minor_units,
        v_opening_movement.currency, v_account_id, v_active.effective_at, 'POSTED', v_active.id
    );

    -- 2. Insert adjustment with corrected amount
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units,
        currency, account_id, effective_at, status, adjusts_movement_id
    ) VALUES (
        v_adjustment_movement_id, v_operation_id, 1, v_user_id, 'ADJUSTMENT', v_new_amount,
        v_opening_movement.currency, v_account_id, v_new_effective_at, 'POSTED', v_opening_movement.id
    );

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'account_id', v_account_id,
        'reversal_movement_id', v_reversal_movement_id,
        'adjustment_movement_id', v_adjustment_movement_id
    );

    UPDATE public.accounts SET initial_balance_minor_units=v_new_amount,
      revision=revision+1 WHERE id=v_account_id AND user_id=v_user_id RETURNING * INTO v_account;
    PERFORM internal.publish_account_state_v1(v_account);
    INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status,command_payload)
    VALUES(v_user_id,v_operation_id::text,'RECORD_OPENING_ADJUSTMENT_V1',v_hash,v_response,'APPLIED',p_command);
    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.record_opening_adjustment_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.record_opening_adjustment_v1(jsonb) TO authenticated;
CREATE OR REPLACE FUNCTION public.pull_financial_changes_v1(
    contract_version integer DEFAULT 1, after_sequence bigint DEFAULT 0, "limit" integer DEFAULT 100
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner uuid:=auth.uid(); v_result jsonb; v_changes jsonb;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_result:=internal.pull_financial_changes_pre_credit_terms_v1(contract_version,after_sequence,"limit");
    SELECT coalesce(jsonb_agg(
        CASE WHEN item->>'entity_type'='TRANSACTION' AND item->>'operation'='UPSERT'
             AND t.id IS NOT NULL
        THEN jsonb_set(jsonb_set(item,'{payload}',internal.transaction_sync_projection_v1(t)),
            '{revision}',to_jsonb(t.revision))
        WHEN item->>'entity_type'='ACCOUNT' AND item->>'operation'='UPSERT' AND a.id IS NOT NULL
        THEN jsonb_set(jsonb_set(item,'{payload}',internal.account_sync_projection_v1(a)),
          '{revision}',to_jsonb(a.revision)) ELSE item END ORDER BY ordinal), '[]'::jsonb)
    INTO v_changes
    FROM jsonb_array_elements(coalesce(v_result->'changes','[]'::jsonb)) WITH ORDINALITY changes(item,ordinal)
    LEFT JOIN public.transactions t ON t.user_id=v_owner AND t.id=(item->>'entity_id')::uuid
    LEFT JOIN public.accounts a ON a.user_id=v_owner AND a.id=(item->>'entity_id')::uuid;
    RETURN v_result || jsonb_build_object('changes',v_changes);
END $$;
REVOKE ALL ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) FROM PUBLIC,anon,service_role;
GRANT EXECUTE ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
-- Read-only diagnostic; do not repair historical data in this migration.
-- WITH histories AS (
--   SELECT a.id,a.user_id,count(*) FILTER (WHERE m.kind='ADJUSTMENT') adjustments,
--     sum(m.amount_minor_units) FILTER (WHERE m.status='POSTED') actual,
--     (array_agg(m.amount_minor_units ORDER BY m.created_at DESC,m.id DESC)
--       FILTER (WHERE m.kind='ADJUSTMENT' AND m.status='POSTED'))[1] expected
--   FROM public.accounts a JOIN public.financial_movements m ON m.account_id=a.id AND m.user_id=a.user_id
--   WHERE m.kind IN ('OPENING','ADJUSTMENT','REVERSAL') GROUP BY a.id,a.user_id
-- ) SELECT h.*, (SELECT sum(l.signed_amount_minor) FROM internal.ledger_entries l
--   JOIN public.financial_movements m ON m.id=l.financial_movement_id
--   WHERE m.account_id=h.id AND m.user_id=h.user_id) ledger_actual
-- FROM histories h WHERE adjustments>1 AND actual IS DISTINCT FROM expected;

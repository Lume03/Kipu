-- EP-MET: additive goal reserve model. Apply to a hosted environment only after T040.
BEGIN;

ALTER TYPE public.goal_status ADD VALUE IF NOT EXISTS 'LOCKED_BY_PLAN';

ALTER TABLE public.goals ADD COLUMN IF NOT EXISTS linked_account_id uuid;
ALTER TABLE public.goal_events ADD COLUMN IF NOT EXISTS delta_minor bigint;
ALTER TABLE public.goal_events ADD COLUMN IF NOT EXISTS currency_code bpchar(3);
ALTER TABLE public.goal_events ADD COLUMN IF NOT EXISTS backing_account_id uuid;
ALTER TABLE public.goal_events ADD COLUMN IF NOT EXISTS operation_id uuid;

-- No ledger/account is inferred from historical goal_events. Only new events have
-- an operation_id and a verified backing account. Historical delta remains NULL.
ALTER TABLE public.goal_events DROP CONSTRAINT IF EXISTS goal_events_event_type_check;
ALTER TABLE public.goal_events ADD CONSTRAINT goal_events_event_type_check
  CHECK (event_type IN ('ALLOCATION','RELEASE','DIRECT_DEPOSIT','ADJUSTMENT',
                       'REASSIGNMENT_IN','REASSIGNMENT_OUT'));
ALTER TABLE public.goal_events ADD CONSTRAINT goal_events_new_delta_check
  CHECK (operation_id IS NULL OR (delta_minor IS NOT NULL AND delta_minor <> 0
    AND backing_account_id IS NOT NULL AND currency_code IS NOT NULL));

CREATE UNIQUE INDEX IF NOT EXISTS goals_owner_ref ON public.goals(id,user_id);
CREATE UNIQUE INDEX IF NOT EXISTS accounts_owner_ref_for_goals ON public.accounts(id,user_id);
CREATE UNIQUE INDEX IF NOT EXISTS transactions_owner_ref_for_goals ON public.transactions(id,user_id);
ALTER TABLE public.goal_events DROP CONSTRAINT IF EXISTS goal_events_goal_id_fkey;
ALTER TABLE public.goal_events ADD CONSTRAINT goal_events_owner_goal_fk
  FOREIGN KEY (goal_id,user_id) REFERENCES public.goals(id,user_id) ON DELETE RESTRICT;
ALTER TABLE public.goal_events ADD CONSTRAINT goal_events_owner_account_fk
  FOREIGN KEY (backing_account_id,user_id) REFERENCES public.accounts(id,user_id) ON DELETE RESTRICT;
ALTER TABLE public.goal_events ADD CONSTRAINT goal_events_owner_tx_fk
  FOREIGN KEY (transaction_id,user_id) REFERENCES public.transactions(id,user_id) ON DELETE SET NULL (transaction_id);
ALTER TABLE public.goal_events DROP CONSTRAINT IF EXISTS goal_events_transaction_id_fkey;
ALTER TABLE public.goals ADD CONSTRAINT goals_owner_linked_account_fk
  FOREIGN KEY (linked_account_id,user_id) REFERENCES public.accounts(id,user_id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS goal_events_backing_sum_idx
  ON public.goal_events(user_id,backing_account_id,goal_id) WHERE operation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS goal_events_command_idx ON public.goal_events(user_id,operation_id);

-- The original RPCs convert allocations into EXPENSE/INCOME; disable them for
-- authenticated clients before exposing the new write protocol.
REVOKE ALL ON FUNCTION public.allocate_goal_funds(uuid,bigint,uuid) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.release_goal_funds(uuid,bigint,uuid) FROM PUBLIC,anon,authenticated;
REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON public.goals,public.goal_events FROM PUBLIC,anon,authenticated;
ALTER TABLE public.goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goal_events ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION internal.goal_canonical_json(v jsonb)
RETURNS text LANGUAGE plpgsql IMMUTABLE STRICT SET search_path = '' AS $$
DECLARE s text;
BEGIN
  CASE pg_catalog.jsonb_typeof(v)
    WHEN 'object' THEN
      SELECT '{' || COALESCE(pg_catalog.string_agg(pg_catalog.to_json(k)::text || ':' ||
        internal.goal_canonical_json(v -> k),',' ORDER BY k COLLATE "C"),'') || '}'
        INTO s FROM pg_catalog.jsonb_object_keys(v) AS k;
      RETURN s;
    WHEN 'array' THEN
      SELECT '[' || COALESCE(pg_catalog.string_agg(internal.goal_canonical_json(value),',' ORDER BY ord),'') || ']'
        INTO s FROM pg_catalog.jsonb_array_elements(v) WITH ORDINALITY AS a(value,ord);
      RETURN s;
    ELSE RETURN v::text;
  END CASE;
END;
$$;

CREATE OR REPLACE FUNCTION internal.goal_check_command(p jsonb, expected_type text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE u uuid := auth.uid(); op uuid; h text; expected text; prior internal.command_receipts%ROWTYPE;
BEGIN
  IF u IS NULL THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','UNAUTHENTICATED'); END IF;
  IF pg_catalog.jsonb_typeof(p) <> 'object' OR p->>'command_type' IS DISTINCT FROM expected_type
    OR p->'contract_version' IS DISTINCT FROM '1'::jsonb
    OR pg_catalog.jsonb_typeof(p->'payload') <> 'object'
    OR NOT (p ?& ARRAY['operation_id','request_hash','goal_id'])
  THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND'); END IF;
  BEGIN op := (p->>'operation_id')::uuid;
  EXCEPTION WHEN invalid_text_representation THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND'); END;
  h := p->>'request_hash';
  expected := pg_catalog.encode(extensions.digest(pg_catalog.convert_to(
    internal.goal_canonical_json(p - 'request_hash' - 'operation_id'),'UTF8'),'sha256'),'hex');
  IF h IS NULL OR h !~ '^[0-9a-f]{64}$' OR h <> expected THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','REQUEST_HASH_MISMATCH');
  END IF;
  -- Synchronize retries before checking the receipt, even when no row exists.
  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('kipu:goal-command:' || u::text || ':' || op::text,0));
  SELECT * INTO prior FROM internal.command_receipts
    WHERE user_id=u AND idempotency_key=op::text FOR UPDATE;
  IF FOUND THEN
    IF prior.request_hash <> h OR prior.command_type <> expected_type
      OR prior.command_payload IS DISTINCT FROM (p - 'request_hash' - 'operation_id') THEN
      RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','OPERATION_ID_REUSED');
    END IF;
    RETURN prior.response_payload || pg_catalog.jsonb_build_object('status','DUPLICATE');
  END IF;
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION internal.goal_store_receipt(p jsonb, response jsonb)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,
      response_payload,status,contract_version,command_payload)
  VALUES (auth.uid(),p->>'operation_id',p->>'command_type',p->>'request_hash',response,
      'APPLIED',1,p - 'request_hash' - 'operation_id');
END;
$$;

CREATE OR REPLACE FUNCTION internal.goal_reserved(p_user uuid,p_goal uuid DEFAULT NULL,p_account uuid DEFAULT NULL)
RETURNS bigint LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT COALESCE(SUM(e.delta_minor),0)::bigint FROM public.goal_events e
  WHERE e.user_id=p_user AND e.operation_id IS NOT NULL AND e.backing_account_id IS NOT NULL
    AND (p_goal IS NULL OR e.goal_id=p_goal)
    AND (p_account IS NULL OR e.backing_account_id=p_account);
$$;

-- Balances: posted financial movements (including opening and reversals) plus
-- confirmed transaction ledger. Never double count account.initial_balance.
CREATE OR REPLACE FUNCTION internal.goal_confirmed_balance(p_user uuid,p_account uuid)
RETURNS bigint LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT (COALESCE((SELECT SUM(m.amount_minor_units) FROM public.financial_movements m
      WHERE m.user_id=p_user AND m.account_id=p_account AND m.status='POSTED'),0)
    + COALESCE((SELECT SUM(l.signed_amount_minor) FROM internal.ledger_entries l
      INNER JOIN public.transactions t ON t.id=l.transaction_id AND t.user_id=l.user_id
      WHERE l.user_id=p_user AND l.account_id=p_account AND t.status='CONFIRMED'
        AND t.deleted_at IS NULL),0))::bigint;
$$;

CREATE OR REPLACE FUNCTION internal.goal_emit(p_goal uuid,p_event uuid DEFAULT NULL,p_delete boolean DEFAULT false)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE u uuid := auth.uid(); g public.goals%ROWTYPE; e public.goal_events%ROWTYPE; seq bigint;
BEGIN
  IF p_delete THEN
    SELECT * INTO g FROM public.goals WHERE id=p_goal AND user_id=u;
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(u,'GOAL',p_goal,COALESCE(g.revision,0)+1,'DELETE',pg_catalog.jsonb_build_object('id',p_goal)) RETURNING sequence INTO seq;
    RETURN seq;
  END IF;
  SELECT * INTO g FROM public.goals WHERE id=p_goal AND user_id=u;
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
  VALUES(u,'GOAL',g.id,g.revision,'UPSERT',pg_catalog.to_jsonb(g)) RETURNING sequence INTO seq;
  IF p_event IS NOT NULL THEN
    SELECT * INTO e FROM public.goal_events WHERE id=p_event AND user_id=u;
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(u,'GOAL_EVENT',e.id,g.revision,'UPSERT',pg_catalog.to_jsonb(e)) RETURNING sequence INTO seq;
  END IF;
  RETURN seq;
END;
$$;

REVOKE ALL ON FUNCTION internal.goal_canonical_json(jsonb),
  internal.goal_check_command(jsonb,text),internal.goal_store_receipt(jsonb,jsonb),
  internal.goal_reserved(uuid,uuid,uuid),internal.goal_confirmed_balance(uuid,uuid),
  internal.goal_emit(uuid,uuid,boolean) FROM PUBLIC,anon,authenticated;
COMMIT;

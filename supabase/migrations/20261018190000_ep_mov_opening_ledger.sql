-- EP-MOV/EP-MET: canonical, owner-scoped ledger origins for opening movements.
-- Additive only. Never rewrites financial_movements or account projections.
BEGIN;

ALTER TABLE internal.ledger_entries
  ALTER COLUMN transaction_id DROP NOT NULL;
ALTER TABLE internal.ledger_entries
  ADD COLUMN IF NOT EXISTS source_kind text NOT NULL DEFAULT 'TRANSACTION',
  ADD COLUMN IF NOT EXISTS financial_movement_id uuid;

ALTER TABLE internal.ledger_entries
  ADD CONSTRAINT ledger_entries_source_kind_check
    CHECK (source_kind IN ('TRANSACTION','FINANCIAL_MOVEMENT')),
  ADD CONSTRAINT ledger_entries_exactly_one_source_check
    CHECK ((source_kind='TRANSACTION' AND transaction_id IS NOT NULL AND financial_movement_id IS NULL)
        OR (source_kind='FINANCIAL_MOVEMENT' AND transaction_id IS NULL AND financial_movement_id IS NOT NULL));

-- Composite reference proves source movement, owner and account agree.
CREATE UNIQUE INDEX IF NOT EXISTS financial_movements_owner_account_ref
  ON public.financial_movements(id,user_id,account_id);
ALTER TABLE internal.ledger_entries
  ADD CONSTRAINT ledger_entries_financial_movement_owner_account_fk
  FOREIGN KEY(financial_movement_id,user_id,account_id)
  REFERENCES public.financial_movements(id,user_id,account_id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX IF NOT EXISTS ledger_entries_one_movement_source
  ON internal.ledger_entries(financial_movement_id)
  WHERE financial_movement_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ledger_entries_transaction_account_role
  ON internal.ledger_entries(transaction_id,account_id,entry_role)
  WHERE transaction_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ledger_entries_user_account_source
  ON internal.ledger_entries(user_id,account_id,source_kind);

-- Import only opening records and explicitly linked opening reversals/adjustments.
-- Existing entries are checked for exact equality; source drift raises a conflict.
CREATE OR REPLACE FUNCTION internal.reconcile_opening_ledger()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  candidate_count bigint;
  inserted_count bigint;
  conflict_count bigint;
  ambiguous_count bigint;
BEGIN
  WITH opening_rows AS (
    SELECT m.id,m.user_id,m.account_id,m.amount_minor_units,m.currency,
           m.effective_at,m.kind
    FROM public.financial_movements m
    WHERE m.kind='OPENING' AND m.opening_account_id=m.account_id
  ), candidates AS (
    SELECT o.id,o.user_id,o.account_id,o.amount_minor_units,o.currency,o.effective_at,o.kind
    FROM opening_rows o
    UNION ALL
    SELECT m.id,m.user_id,m.account_id,m.amount_minor_units,m.currency,m.effective_at,m.kind
    FROM public.financial_movements m
    JOIN opening_rows o ON o.id=COALESCE(m.reverses_movement_id,m.adjusts_movement_id)
    WHERE m.kind IN ('REVERSAL','ADJUSTMENT')
  )
  SELECT count(*) INTO candidate_count FROM candidates;

  SELECT count(*) INTO ambiguous_count
  FROM public.financial_movements m
  JOIN public.financial_movements o
    ON o.id=COALESCE(m.reverses_movement_id,m.adjusts_movement_id)
   AND o.kind='OPENING' AND o.opening_account_id=o.account_id
  WHERE m.kind IN ('REVERSAL','ADJUSTMENT')
    AND (m.user_id IS DISTINCT FROM o.user_id OR m.account_id IS DISTINCT FROM o.account_id
      OR m.currency IS DISTINCT FROM o.currency OR m.status<>'POSTED');

  WITH opening_rows AS (
    SELECT m.id,m.user_id,m.account_id,m.amount_minor_units,m.currency,m.effective_at,m.kind
    FROM public.financial_movements m
    WHERE m.kind='OPENING' AND m.opening_account_id=m.account_id AND m.status='POSTED'
  ), candidates AS (
    SELECT o.id,o.user_id,o.account_id,o.amount_minor_units,o.currency,o.effective_at,o.kind
    FROM opening_rows o
    UNION ALL
    SELECT m.id,m.user_id,m.account_id,m.amount_minor_units,m.currency,m.effective_at,m.kind
    FROM public.financial_movements m
    JOIN opening_rows o ON o.id=COALESCE(m.reverses_movement_id,m.adjusts_movement_id)
    WHERE m.kind IN ('REVERSAL','ADJUSTMENT') AND m.user_id=o.user_id
      AND m.account_id=o.account_id AND m.currency=o.currency AND m.status='POSTED'
  )
  SELECT count(*) INTO conflict_count
  FROM candidates c JOIN internal.ledger_entries l
    ON l.source_kind='FINANCIAL_MOVEMENT' AND l.financial_movement_id=c.id
  WHERE l.user_id IS DISTINCT FROM c.user_id OR l.account_id IS DISTINCT FROM c.account_id
    OR l.signed_amount_minor IS DISTINCT FROM c.amount_minor_units
    OR l.currency_code IS DISTINCT FROM c.currency;
  IF conflict_count>0 THEN
    RAISE EXCEPTION 'OPENING_LEDGER_SOURCE_CONFLICT: % existing source rows diverge',conflict_count
      USING ERRCODE='23514';
  END IF;

  WITH opening_rows AS (
    SELECT m.id,m.user_id,m.account_id,m.amount_minor_units,m.currency,m.effective_at,m.kind
    FROM public.financial_movements m
    WHERE m.kind='OPENING' AND m.opening_account_id=m.account_id AND m.status='POSTED'
  ), candidates AS (
    SELECT o.id,o.user_id,o.account_id,o.amount_minor_units,o.currency,o.effective_at,o.kind
    FROM opening_rows o
    UNION ALL
    SELECT m.id,m.user_id,m.account_id,m.amount_minor_units,m.currency,m.effective_at,m.kind
    FROM public.financial_movements m
    JOIN opening_rows o ON o.id=COALESCE(m.reverses_movement_id,m.adjusts_movement_id)
    WHERE m.kind IN ('REVERSAL','ADJUSTMENT') AND m.user_id=o.user_id
      AND m.account_id=o.account_id AND m.currency=o.currency AND m.status='POSTED'
  )
  INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,
    currency_code,entry_role,occurred_at,source_kind,financial_movement_id)
  SELECT NULL,c.user_id,c.account_id,c.amount_minor_units,c.currency,
    CASE WHEN c.kind='REVERSAL' THEN 'REVERSAL'::internal.entry_role ELSE 'PRIMARY'::internal.entry_role END,
    c.effective_at,'FINANCIAL_MOVEMENT',c.id
  FROM candidates c
  WHERE NOT EXISTS (SELECT 1 FROM internal.ledger_entries l
    WHERE l.financial_movement_id=c.id)
  ON CONFLICT (financial_movement_id) WHERE financial_movement_id IS NOT NULL DO NOTHING;
  GET DIAGNOSTICS inserted_count=ROW_COUNT;

  RETURN jsonb_build_object('candidates',candidate_count,'inserted',inserted_count,
    'ambiguous',ambiguous_count);
END;
$$;

CREATE OR REPLACE FUNCTION internal.goal_confirmed_balance(p_user uuid,p_account uuid)
RETURNS bigint LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT COALESCE(sum(l.signed_amount_minor),0)::bigint
  FROM internal.ledger_entries l
  WHERE l.user_id=p_user AND l.account_id=p_account
    AND ((l.source_kind='TRANSACTION' AND EXISTS (
          SELECT 1 FROM public.transactions t
          WHERE t.id=l.transaction_id AND t.user_id=l.user_id
            AND t.status='CONFIRMED' AND t.deleted_at IS NULL))
      OR (l.source_kind='FINANCIAL_MOVEMENT' AND EXISTS (
          SELECT 1 FROM public.financial_movements m
          WHERE m.id=l.financial_movement_id AND m.user_id=l.user_id
            AND m.account_id=l.account_id AND m.status='POSTED')));
$$;

REVOKE ALL ON FUNCTION internal.reconcile_opening_ledger() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION internal.goal_confirmed_balance(uuid,uuid) FROM PUBLIC,anon,authenticated;
COMMIT;

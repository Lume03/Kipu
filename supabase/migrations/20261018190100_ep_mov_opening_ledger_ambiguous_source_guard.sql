-- BUG-QA-001: report and exclude corrections carrying both source references.
-- Additive corrective migration; does not rewrite historical movement data.
BEGIN;

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
      AND NOT (m.reverses_movement_id IS NOT NULL AND m.adjusts_movement_id IS NOT NULL)
  )
  SELECT count(*) INTO candidate_count FROM candidates;

  -- Count each malformed correction once. A dual reference is ambiguous even
  -- when COALESCE could resolve one side to a valid opening movement.
  SELECT count(*) INTO ambiguous_count
  FROM public.financial_movements m
  WHERE m.kind IN ('REVERSAL','ADJUSTMENT')
    AND (
      (m.reverses_movement_id IS NOT NULL AND m.adjusts_movement_id IS NOT NULL)
      OR EXISTS (
        SELECT 1
        FROM public.financial_movements o
        WHERE o.id=COALESCE(m.reverses_movement_id,m.adjusts_movement_id)
          AND o.kind='OPENING' AND o.opening_account_id=o.account_id
          AND (m.user_id IS DISTINCT FROM o.user_id
            OR m.account_id IS DISTINCT FROM o.account_id
            OR m.currency IS DISTINCT FROM o.currency
            OR m.status<>'POSTED')
      )
    );

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
    WHERE m.kind IN ('REVERSAL','ADJUSTMENT')
      AND NOT (m.reverses_movement_id IS NOT NULL AND m.adjusts_movement_id IS NOT NULL)
      AND m.user_id=o.user_id AND m.account_id=o.account_id
      AND m.currency=o.currency AND m.status='POSTED'
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
    WHERE m.kind IN ('REVERSAL','ADJUSTMENT')
      AND NOT (m.reverses_movement_id IS NOT NULL AND m.adjusts_movement_id IS NOT NULL)
      AND m.user_id=o.user_id AND m.account_id=o.account_id
      AND m.currency=o.currency AND m.status='POSTED'
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

REVOKE ALL ON FUNCTION internal.reconcile_opening_ledger() FROM PUBLIC,anon,authenticated;
COMMIT;

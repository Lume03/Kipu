-- Activation follows the audited debt/goal migrations; preserve append-only opening facts.
BEGIN;

-- All financial debt mutations must pass the validated, idempotent commands.
REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON public.debts,public.debt_events,public.debt_installments
  FROM PUBLIC,anon,authenticated;
DO $$
DECLARE signature text;
BEGIN
  FOREACH signature IN ARRAY ARRAY['public.record_debt_payment(jsonb)','public.record_obligation_payment(jsonb)'] LOOP
    IF pg_catalog.to_regprocedure(signature) IS NOT NULL THEN
      EXECUTE 'REVOKE ALL ON FUNCTION ' || signature || ' FROM PUBLIC,anon,authenticated';
    END IF;
  END LOOP;
END;
$$;

-- Historical stacks placed the enum in different schemas. Bind the backfill to
-- the actual ledger column type instead of assuming internal.entry_role exists.
DO $$
DECLARE role_type text; definition text;
BEGIN
  SELECT pg_catalog.format('%I.%I',n.nspname,t.typname) INTO role_type
    FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_type t ON t.oid=a.atttypid
    JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace
    WHERE a.attrelid='internal.ledger_entries'::regclass AND a.attname='entry_role';
  IF role_type IS NULL THEN RAISE EXCEPTION 'LEDGER_ROLE_TYPE_MISSING'; END IF;
  definition := pg_catalog.pg_get_functiondef('internal.reconcile_opening_ledger()'::regprocedure);
  definition := pg_catalog.replace(definition,'::internal.entry_role','::' || role_type);
  -- Zero opening facts have no financial effect and need no ledger posting.
  definition := pg_catalog.replace(definition,
      'WHERE NOT EXISTS (SELECT 1 FROM internal.ledger_entries l',
      'WHERE c.amount_minor_units <> 0 AND NOT EXISTS (SELECT 1 FROM internal.ledger_entries l');
  EXECUTE definition;
END;
$$;

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
        AND kind='OPENING' AND opening_account_id=account_id AND status='POSTED';
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
DROP TRIGGER IF EXISTS post_opening_movement_ledger_v1 ON public.financial_movements;
CREATE TRIGGER post_opening_movement_ledger_v1 AFTER INSERT ON public.financial_movements
  FOR EACH ROW EXECUTE FUNCTION internal.post_opening_movement_ledger_v1();

DO $$
DECLARE result jsonb;
BEGIN
  result := internal.reconcile_opening_ledger();
  IF (result->>'ambiguous')::bigint <> 0 THEN
    RAISE EXCEPTION 'OPENING_HISTORY_REVIEW_REQUIRED: activation stopped';
  END IF;
END;
$$;
NOTIFY pgrst, 'reload schema';
COMMIT;

-- Activation follows the audited debt/goal migrations; preserve append-only opening facts.
BEGIN;

CREATE OR REPLACE FUNCTION internal.post_opening_movement_ledger_v1()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE source public.financial_movements%ROWTYPE;
BEGIN
  IF NEW.status <> 'POSTED' THEN RETURN NEW; END IF;
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
  INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,
      currency_code,entry_role,occurred_at,source_kind,financial_movement_id)
  VALUES(NULL,NEW.user_id,NEW.account_id,NEW.amount_minor_units,NEW.currency,
      CASE WHEN NEW.kind='REVERSAL' THEN 'REVERSAL'::internal.entry_role ELSE 'PRIMARY'::internal.entry_role END,
      NEW.effective_at,'FINANCIAL_MOVEMENT',NEW.id)
  ON CONFLICT (financial_movement_id) WHERE financial_movement_id IS NOT NULL DO NOTHING;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION internal.post_opening_movement_ledger_v1() FROM PUBLIC,anon,authenticated;
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

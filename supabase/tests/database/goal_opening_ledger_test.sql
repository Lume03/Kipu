-- Regression for the canonical account balance: openings and opening corrections
-- must be represented once in ledger_entries, never added from legacy mirrors.
BEGIN;
\ir _goal_test_fixture.sql

DO $$
DECLARE
  u uuid := '96000000-0000-4000-8000-000000000001';
  a uuid := '96000000-0000-4000-8000-000000000011';

  correction_reversal uuid := extensions.gen_random_uuid();
  correction_adjustment uuid := extensions.gen_random_uuid();
  spend uuid := extensions.gen_random_uuid();
  opening_movement uuid;
  reversal_movement uuid := extensions.gen_random_uuid();
  adjustment_movement uuid := extensions.gen_random_uuid();
  spend_movement uuid := extensions.gen_random_uuid();
  dual_source_movement uuid := extensions.gen_random_uuid();
  second_opening_movement uuid;
  reconcile_result jsonb;
  ledger_count bigint;
BEGIN
  -- Transaction entries keep a transaction FK; opening sources use a typed FK
  -- to the immutable financial movement row instead.
  INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,
    amount_minor,currency_code,status,revision)
  VALUES(spend,u,a,'EXPENSE','DEBT_DISBURSEMENT',2500,'PEN','CONFIRMED',1);
  INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,
    signed_amount_minor,currency_code,entry_role)
  VALUES(spend,u,a,-2500,'PEN','SOURCE');

  SELECT id INTO opening_movement FROM public.financial_movements
    WHERE user_id=u AND opening_account_id=a AND kind='OPENING';
  UPDATE public.financial_movements SET amount_minor_units=10000 WHERE id=opening_movement;
  INSERT INTO public.financial_movements(id,operation_id,user_id,kind,amount_minor_units,
    currency,account_id,reverses_movement_id,adjusts_movement_id)
  VALUES(reversal_movement,correction_reversal,u,'REVERSAL',-10000,'PEN',a,opening_movement,NULL),
    (adjustment_movement,correction_adjustment,u,'ADJUSTMENT',9000,'PEN',a,NULL,opening_movement),
    (spend_movement,extensions.gen_random_uuid(),u,'ADJUSTMENT',-2500,'PEN',a,NULL,NULL);

  -- This DML is intentionally written against the approved source-column API.
  PERFORM internal.reconcile_opening_ledger();
  ledger_count := (SELECT count(*) FROM internal.ledger_entries WHERE user_id=u AND account_id=a);
  PERFORM internal.reconcile_opening_ledger();
  IF (SELECT count(*) FROM internal.ledger_entries WHERE user_id=u AND account_id=a) <> ledger_count THEN
    RAISE EXCEPTION 'reconciliation retry added duplicate entries';
  END IF;

  UPDATE public.accounts SET initial_balance_minor_units=10000 WHERE id=a;
  IF internal.goal_confirmed_balance(u,a) <> 6500 THEN
    RAISE EXCEPTION 'canonical balance expected 6500, got %',internal.goal_confirmed_balance(u,a);
  END IF;
  IF (SELECT count(*) FROM public.financial_movements WHERE id IN
      (opening_movement,reversal_movement,adjustment_movement)) <> 3 THEN
    RAISE EXCEPTION 'legacy opening history was not preserved';
  END IF;
  ledger_count := (SELECT count(*) FROM internal.ledger_entries WHERE user_id=u AND account_id=a);
  IF ledger_count <> 4 THEN RAISE EXCEPTION 'expected four canonical entries, got %',ledger_count; END IF;

  -- Two distinct openings must not be collapsed into one inferred source when
  -- a correction references both supported source columns.
  SELECT id INTO second_opening_movement FROM public.financial_movements
    WHERE user_id=u AND account_id='96000000-0000-4000-8000-000000000012' AND kind='OPENING';
  INSERT INTO public.financial_movements(id,operation_id,user_id,kind,amount_minor_units,
    currency,account_id,reverses_movement_id,adjusts_movement_id)
  VALUES(dual_source_movement,extensions.gen_random_uuid(),u,'ADJUSTMENT',500,'PEN',a,
    opening_movement,second_opening_movement);
  reconcile_result := internal.reconcile_opening_ledger();
  IF (reconcile_result->>'ambiguous')::bigint <> 1 THEN
    RAISE EXCEPTION 'dual-source row should report ambiguous=1, got %',reconcile_result;
  END IF;
  IF (reconcile_result->>'candidates')::bigint <> 5 THEN
    RAISE EXCEPTION 'dual-source row should be excluded from candidates; expected 5, got %',reconcile_result;
  END IF;
  IF EXISTS (SELECT 1 FROM internal.ledger_entries WHERE financial_movement_id=dual_source_movement) THEN
    RAISE EXCEPTION 'dual-source row unexpectedly created a ledger entry';
  END IF;
  RAISE NOTICE 'dual-source reconciliation result=%, ledger_rows_for_dual=%',
    reconcile_result,
    (SELECT count(*) FROM internal.ledger_entries WHERE financial_movement_id=dual_source_movement);

  -- The mirror and legacy projection cannot change the canonical answer.
  UPDATE public.financial_movements SET amount_minor_units=20000 WHERE id=opening_movement;
  UPDATE public.accounts SET initial_balance_minor_units=20000 WHERE id=a;
  IF internal.goal_confirmed_balance(u,a) <> 6500 THEN
    RAISE EXCEPTION 'legacy mirror changed canonical balance';
  END IF;
  BEGIN
    PERFORM internal.reconcile_opening_ledger();
    RAISE EXCEPTION 'divergent source unexpectedly reconciled';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO internal.ledger_entries(user_id,account_id,signed_amount_minor,
      currency_code,entry_role,source_kind)
    VALUES(u,a,1,'PEN','PRIMARY','TRANSACTION');
    RAISE EXCEPTION 'source-less ledger row unexpectedly inserted';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  -- VOIDED and deleted transactions are excluded even if legacy ledger rows remain.
  INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,
    amount_minor,currency_code,status,revision)
  VALUES('97000000-0000-4000-8000-000000000001',u,a,'EXPENSE','DEBT_DISBURSEMENT',500,'PEN','VOIDED',1),
    ('97000000-0000-4000-8000-000000000002',u,a,'EXPENSE','DEBT_DISBURSEMENT',500,'PEN','CONFIRMED',1);
  INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,
    currency_code,entry_role)
  VALUES('97000000-0000-4000-8000-000000000001',u,a,-500,'PEN','SOURCE'),
    ('97000000-0000-4000-8000-000000000002',u,a,-500,'PEN','SOURCE');
  UPDATE public.transactions SET deleted_at=now()
    WHERE id='97000000-0000-4000-8000-000000000002';
  IF internal.goal_confirmed_balance(u,a) <> 6500 THEN
    RAISE EXCEPTION 'voided/deleted transactions affected canonical balance';
  END IF;
END;
$$;
ROLLBACK;

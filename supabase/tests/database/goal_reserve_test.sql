BEGIN;

\ir _goal_test_fixture.sql
-- New opening facts must already be posted by the activation trigger.
SET LOCAL ROLE authenticated;
DO $$
DECLARE
  a uuid := '96000000-0000-4000-8000-000000000011';
  b uuid := '96000000-0000-4000-8000-000000000012';
  g1 uuid := '96000000-0000-4000-8000-000000000101';
  g2 uuid := '96000000-0000-4000-8000-000000000102';
  e uuid := '96000000-0000-4000-8000-000000000201';
  command jsonb; r jsonb;
BEGIN
  r := public.create_goal_v1(pg_temp.goal_cmd('CREATE_GOAL',g1,NULL,
    jsonb_build_object('name','Vacaciones','target_minor',60000,'currency_code','PEN')));
  IF r->>'status'<>'APPLIED' THEN RAISE EXCEPTION 'seed g1: %',r; END IF;
  r := public.create_goal_v1(pg_temp.goal_cmd('CREATE_GOAL',g2,NULL,
    jsonb_build_object('name','Estudios','target_minor',60000,'currency_code','PEN')));
  IF r->>'status'<>'APPLIED' THEN RAISE EXCEPTION 'seed g2: %',r; END IF;
  command := pg_temp.goal_cmd('RESERVE_GOAL',g1,1,jsonb_build_object(
    'event_id',e,'goal_id',g1,'backing_account_id',a,'currency_code','PEN','amount_minor',20000));
  r := public.reserve_goal_v1(command);
  IF r->>'status'<>'APPLIED' OR r->>'revision'<>'2' OR r->>'available_minor'<>'80000'
    OR r->>'transaction_id' IS NOT NULL THEN RAISE EXCEPTION 'reserve-only: %',r; END IF;
  r := public.reserve_goal_v1(command);
  IF r->>'status'<>'DUPLICATE' THEN RAISE EXCEPTION 'reserve retry: %',r; END IF;
  r := public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',g2,1,
    jsonb_build_object('event_id',extensions.gen_random_uuid(),'goal_id',g2,
    'backing_account_id',a,'currency_code','PEN','amount_minor',85000)));
  IF r->>'error'<>'INSUFFICIENT_BACKING' THEN RAISE EXCEPTION 'over-reserve: %',r; END IF;
  r := public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',g2,1,
    jsonb_build_object('event_id',extensions.gen_random_uuid(),'goal_id',g2,
    'backing_account_id','96000000-0000-4000-8000-000000000013','currency_code','PEN','amount_minor',1000)));
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'foreign backing: %',r; END IF;
  r := public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',g2,1,
    jsonb_build_object('event_id',extensions.gen_random_uuid(),'goal_id',g2,
    'backing_account_id','96000000-0000-4000-8000-000000000014','currency_code','PEN','amount_minor',1000)));
  IF r->>'error'<>'OWNER_OR_ACCOUNT_INVALID' THEN RAISE EXCEPTION 'wrong currency: %',r; END IF;
  command := pg_temp.goal_cmd('RESERVE_GOAL',g2,1,
    jsonb_build_object('event_id','96000000-0000-4000-8000-000000000202','goal_id',g2,
      'backing_account_id',b,'currency_code','PEN','amount_minor',10000,
      'transfer',jsonb_build_object('source_account_id',a,'destination_account_id',b,'currency_code','PEN')));
  r := public.reserve_goal_v1(command);
  IF r->>'status'<>'APPLIED' OR r->>'transaction_id' IS NULL OR
    r->>'confirmed_balance_minor'<>'30000' THEN RAISE EXCEPTION 'explicit transfer: %',r; END IF;
  IF r->>'transaction_id' IS DISTINCT FROM command->>'operation_id' THEN
    RAISE EXCEPTION 'transfer transaction id must match local idempotency identity: %',r; END IF;
  r := public.reserve_goal_v1(command);
  IF r->>'status'<>'DUPLICATE' THEN RAISE EXCEPTION 'transfer retry: %',r; END IF;
  r := public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',g2,2,
    jsonb_build_object('event_id','96000000-0000-4000-8000-000000000203','goal_id',g2,
      'backing_account_id',b,'currency_code','PEN','amount_minor',1000,
      'transfer',jsonb_build_object('source_account_id',b,'destination_account_id',b,'currency_code','PEN'))));
  IF r->>'status'<>'APPLIED' OR r->>'transaction_id' IS NOT NULL THEN
    RAISE EXCEPTION 'same-account transfer must reserve only: %',r; END IF;
  IF (SELECT count(*) FROM public.transactions WHERE operation_kind='GOAL_FUNDING' AND user_id=auth.uid())<>1 THEN
    RAISE EXCEPTION 'transfer count changed'; END IF;
  IF (SELECT count(*) FROM public.goal_events WHERE user_id=auth.uid())<>3 THEN
    RAISE EXCEPTION 'reserve events duplicated'; END IF;
END;
$$;
RESET ROLE;
UPDATE public.goals SET status='LOCKED_BY_PLAN',revision=revision+1
WHERE id='96000000-0000-4000-8000-000000000102';
SET LOCAL ROLE authenticated;
DO $$
DECLARE r jsonb; transactions_before bigint; events_before bigint;
BEGIN
  SELECT count(*) INTO transactions_before FROM public.transactions
    WHERE operation_kind='GOAL_FUNDING' AND user_id=auth.uid();
  SELECT count(*) INTO events_before FROM public.goal_events WHERE user_id=auth.uid();
  r := public.reserve_goal_v1(pg_temp.goal_cmd('RESERVE_GOAL',
    '96000000-0000-4000-8000-000000000102',4,jsonb_build_object(
      'event_id','96000000-0000-4000-8000-000000000204',
      'goal_id','96000000-0000-4000-8000-000000000102',
      'backing_account_id','96000000-0000-4000-8000-000000000012',
      'currency_code','PEN','amount_minor',1000,
      'transfer',jsonb_build_object('source_account_id','96000000-0000-4000-8000-000000000011',
        'destination_account_id','96000000-0000-4000-8000-000000000012','currency_code','PEN'))));
  IF r->>'error'<>'GOAL_NOT_ELIGIBLE' THEN RAISE EXCEPTION 'inactive goal must reject transfer: %',r; END IF;
  IF (SELECT count(*) FROM public.transactions WHERE operation_kind='GOAL_FUNDING' AND user_id=auth.uid())
      <>transactions_before OR (SELECT count(*) FROM public.goal_events WHERE user_id=auth.uid())<>events_before THEN
    RAISE EXCEPTION 'inactive goal rejection left partial transaction or event'; END IF;
END;
$$;
RESET ROLE;
DO $$
DECLARE a uuid := '96000000-0000-4000-8000-000000000011';
  b uuid := '96000000-0000-4000-8000-000000000012';
BEGIN
  IF (SELECT sum(signed_amount_minor) FROM internal.ledger_entries l
      JOIN public.transactions t ON t.id=l.transaction_id WHERE t.operation_kind='GOAL_FUNDING')<>0
     OR (SELECT count(*) FROM internal.ledger_entries l
      JOIN public.transactions t ON t.id=l.transaction_id WHERE t.operation_kind='GOAL_FUNDING')<>2 THEN
    RAISE EXCEPTION 'transfer ledger must net zero with exactly two entries'; END IF;
  IF internal.goal_confirmed_balance('96000000-0000-4000-8000-000000000001',a)<>90000
    OR internal.goal_confirmed_balance('96000000-0000-4000-8000-000000000001',b)<>30000 THEN
    RAISE EXCEPTION 'transfer accounting mismatch'; END IF;
END;
$$;
ROLLBACK;

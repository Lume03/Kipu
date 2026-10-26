BEGIN;
SELECT plan(8);
\ir _goal_test_fixture.sql
INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,amount_minor,currency_code,status,revision)
VALUES('96000000-0000-4000-8000-000000000881','96000000-0000-4000-8000-000000000001',
  '96000000-0000-4000-8000-000000000011','EXPENSE','DEBT_PAYMENT',1000,'PEN','CONFIRMED',2),
  ('96000000-0000-4000-8000-000000000882','96000000-0000-4000-8000-000000000002',
  '96000000-0000-4000-8000-000000000013','EXPENSE','DEBT_PAYMENT',1000,'PEN','CONFIRMED',1);
INSERT INTO internal.ledger_entries(user_id,transaction_id,account_id,signed_amount_minor,currency_code,entry_role)
VALUES('96000000-0000-4000-8000-000000000001','96000000-0000-4000-8000-000000000881',
  '96000000-0000-4000-8000-000000000011',-1000,'PEN','PRIMARY');
INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
SELECT user_id,'TRANSACTION',id,1,'UPSERT',to_jsonb(t) FROM public.transactions t
WHERE id IN ('96000000-0000-4000-8000-000000000881','96000000-0000-4000-8000-000000000882');
CREATE TEMP TABLE projected AS
SELECT item FROM jsonb_array_elements(public.pull_financial_changes_v1(1,0,500)->'changes') item
WHERE item->>'entity_id'='96000000-0000-4000-8000-000000000881';
SELECT is((SELECT item->'payload'->>'type' FROM projected LIMIT 1),'EXPENSE','raw debt row is projected to client type');
SELECT is((SELECT item->'payload'->>'operation_kind' FROM projected LIMIT 1),'DEBT_PAYMENT','principal keeps specialized identity');
SELECT is((SELECT (item->>'revision')::bigint FROM projected LIMIT 1),2::bigint,'current snapshot has matching current revision');
SELECT is((SELECT item->'payload'->>'source_account_id' FROM projected LIMIT 1),'96000000-0000-4000-8000-000000000011','expense has source account');
SELECT is((SELECT jsonb_array_length(item->'payload'->'ledger_entries') FROM projected LIMIT 1),1,'canonical ledger is included');
SELECT is((SELECT (item->'payload'->'ledger_entries'->0->>'signed_amount_minor')::bigint FROM projected LIMIT 1),-1000::bigint,'no synthetic principal effect is invented');
SELECT is((SELECT count(*)::integer FROM jsonb_array_elements(public.pull_financial_changes_v1(1,0,500)->'changes') item
  WHERE item->>'entity_id'='96000000-0000-4000-8000-000000000882'),0,'other owner remains excluded');
UPDATE public.transactions SET status='VOIDED',revision=3 WHERE id='96000000-0000-4000-8000-000000000881';
SELECT is((SELECT jsonb_array_length(item->'payload'->'ledger_entries') FROM
  jsonb_array_elements(public.pull_financial_changes_v1(1,0,500)->'changes') item
  WHERE item->>'entity_id'='96000000-0000-4000-8000-000000000881' LIMIT 1),0,'voided history is financially inert in restore');
SELECT * FROM finish();
ROLLBACK;

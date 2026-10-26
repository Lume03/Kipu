BEGIN;
SELECT plan(6);
\ir _goal_test_fixture.sql
RESET ROLE;

SELECT ok(NOT has_table_privilege('authenticated','public.debts','INSERT'), 'debt creation cannot bypass commands');
SELECT ok(NOT has_table_privilege('authenticated','public.debt_events','UPDATE'), 'debt history cannot be rewritten directly');
SELECT ok(NOT has_function_privilege('authenticated','public.record_debt_payment(jsonb)','EXECUTE'), 'legacy payment bypass is disabled');

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id,creation_operation_id)
VALUES('96000000-0000-4000-8000-000000000019','96000000-0000-4000-8000-000000000001','Zero opening','CASH','PEN','CASH',extensions.gen_random_uuid());
INSERT INTO public.financial_movements(operation_id,user_id,kind,amount_minor_units,currency,account_id,opening_account_id)
VALUES('96000000-0000-4000-8000-000000000991','96000000-0000-4000-8000-000000000001','OPENING',0,'PEN',
  '96000000-0000-4000-8000-000000000019','96000000-0000-4000-8000-000000000019');
SELECT is((SELECT count(*)::integer FROM internal.ledger_entries l JOIN public.financial_movements m ON m.id=l.financial_movement_id
  WHERE m.operation_id='96000000-0000-4000-8000-000000000991'),0,'zero opening stays in history without a ledger effect');
SELECT lives_ok('SELECT internal.reconcile_opening_ledger()', 'zero opening does not block reconciliation');
SELECT is(internal.goal_confirmed_balance('96000000-0000-4000-8000-000000000001','96000000-0000-4000-8000-000000000011'),
  100000::bigint,'new opening is usable by goals without a manual backfill');
SELECT * FROM finish();
ROLLBACK;

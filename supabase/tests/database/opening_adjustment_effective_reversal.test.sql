BEGIN;
SELECT plan(11);
INSERT INTO auth.users(id,email) VALUES ('91100000-0000-4000-8000-000000000001','opening-l1@kipu.test') ON CONFLICT DO NOTHING;
SET LOCAL "request.jwt.claim.sub"='91100000-0000-4000-8000-000000000001';
SELECT public.create_account_v1(jsonb_build_object(
 'operation_id','91200000-0000-4000-8000-000000000001','account_id','91300000-0000-4000-8000-000000000001',
 'opening_movement_id','91400000-0000-4000-8000-000000000001','alias','Opening L1','type','CASH',
 'currency','PEN','preset_id','CASH','initial_balance_minor_units',10000,'opened_at','2026-11-01T00:00:00Z','payload_hash','l1-create'));
CREATE TEMP TABLE cursor_before AS SELECT coalesce(max(sequence),0) cursor,
 (SELECT revision FROM public.accounts WHERE id='91300000-0000-4000-8000-000000000001') revision
 FROM internal.sync_changes;
CREATE TEMP TABLE commands AS
SELECT n,jsonb_build_object('operation_id','91500000-0000-4000-8000-'||lpad(n::text,12,'0'),
 'account_id','91300000-0000-4000-8000-000000000001',
 'reversal_movement_id','91600000-0000-4000-8000-'||lpad(n::text,12,'0'),
 'adjustment_movement_id','91700000-0000-4000-8000-'||lpad(n::text,12,'0'),
 'new_amount_minor_units',(n+1)*10000,'new_effective_at','2026-11-02T00:00:00Z') command
FROM generate_series(1,2) n;
SELECT is(public.record_opening_adjustment_v1(command)->>'status','APPLIED','first correction applied') FROM commands WHERE n=1;
CREATE TEMP TABLE second_result AS SELECT public.record_opening_adjustment_v1(command) result FROM commands WHERE n=2;
SELECT is((SELECT result->>'status' FROM second_result),'APPLIED','second correction applied');
SELECT is((SELECT sum(amount_minor_units) FROM public.financial_movements WHERE account_id='91300000-0000-4000-8000-000000000001'),30000::numeric,'opening facts net to 30000');
SELECT is((SELECT sum(signed_amount_minor) FROM internal.ledger_entries WHERE account_id='91300000-0000-4000-8000-000000000001'),30000::numeric,'ledger includes reversal of active adjustment');
CREATE TEMP TABLE counts_before AS SELECT (SELECT count(*) FROM public.financial_movements) facts,
 (SELECT count(*) FROM internal.ledger_entries) ledger,(SELECT count(*) FROM internal.sync_changes) feed;
SELECT is((SELECT public.record_opening_adjustment_v1(command) FROM commands WHERE n=2),(SELECT result FROM second_result),'retry returns identical response');
SELECT ok((SELECT facts=(SELECT count(*) FROM public.financial_movements) AND ledger=(SELECT count(*) FROM internal.ledger_entries)
 AND feed=(SELECT count(*) FROM internal.sync_changes) FROM counts_before),'retry inserts no facts ledger or events');
SELECT is((SELECT public.record_opening_adjustment_v1(command || '{"new_amount_minor_units":40000}'::jsonb)->'error'->>'code' FROM commands WHERE n=2),'IDEMPOTENCY_CONFLICT','changed payload explicitly rejected');
CREATE TEMP TABLE pulled AS SELECT item FROM cursor_before,
 LATERAL jsonb_array_elements(public.pull_financial_changes_v1(1,cursor,500)->'changes') item
 WHERE item->>'entity_type'='ACCOUNT' AND item->>'entity_id'='91300000-0000-4000-8000-000000000001';
SELECT ok((SELECT count(*)>0 FROM pulled),'cursor before correction receives ACCOUNT');
SELECT ok((SELECT bool_and((item->>'revision')::bigint=a.revision AND a.revision>(SELECT revision FROM cursor_before)) FROM pulled CROSS JOIN public.accounts a
 WHERE a.id='91300000-0000-4000-8000-000000000001'),'feed carries new account revision');
SELECT ok((SELECT bool_and(jsonb_array_length(item->'payload'->'opening_movements')=5) FROM pulled),'full projection includes all five facts');
SELECT ok((SELECT bool_and(item->'payload'->>'effective_opening_balance_minor_units'='30000') FROM pulled),'feed carries effective opening');
SELECT * FROM finish();
ROLLBACK;

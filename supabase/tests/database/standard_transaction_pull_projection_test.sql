BEGIN;
SELECT plan(23);

INSERT INTO auth.users(id,email) VALUES
 ('80000000-0000-4000-8000-000000000001','s4-revision-owner@kipu.test'),
 ('80000000-0000-4000-8000-000000000002','s4-revision-other@kipu.test')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code, preset_id) VALUES
 ('85100000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001','Bank 1','BANK','PEN', 'GENERIC'),
 ('85100000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000001','Bank 2','BANK','PEN', 'GENERIC'),
 ('85100000-0000-4000-8000-000000000003','80000000-0000-4000-8000-000000000001','Bank 3','BANK','PEN', 'GENERIC'),
 ('85100000-0000-4000-8000-000000000004','80000000-0000-4000-8000-000000000001','Bank 4','BANK','PEN', 'GENERIC'),
 ('85100000-0000-4000-8000-000000000005','80000000-0000-4000-8000-000000000001','Bank 5','BANK','PEN', 'GENERIC');

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code, preset_id) VALUES
 ('81000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001','Cash A','CASH','PEN', 'CASH'),
 ('81000000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000001','Cash B','CASH','PEN', 'CASH')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.categories(id,user_id,name,origin,category_type) VALUES
 ('86000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001','Alimentos','USER_CREATED','EXPENSE')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.transactions(
 id,user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,
 occurred_at,status,revision
) VALUES
 ('82000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001',
  '81000000-0000-4000-8000-000000000001','86000000-0000-4000-8000-000000000001','EXPENSE','STANDARD',2000,'PEN',
  '2026-09-15T12:00:00Z','CONFIRMED',1),
 ('82000000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000001',
  '81000000-0000-4000-8000-000000000001','86000000-0000-4000-8000-000000000001','EXPENSE','CARD_PURCHASE',500,'PEN',
  '2026-09-15T12:00:00Z','CONFIRMED',1),
 ('82000000-0000-4000-8000-000000000003','80000000-0000-4000-8000-000000000001',
  '81000000-0000-4000-8000-000000000001',NULL,'TRANSFER','STANDARD',1000,'PEN',
  '2026-09-15T12:00:00Z','CONFIRMED',1);

INSERT INTO internal.ledger_entries(
 id,transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
) VALUES
 ('83000000-0000-4000-8000-000000000001','82000000-0000-4000-8000-000000000001',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001',-2000,'PEN','PRIMARY','2026-09-15T12:00:00Z'),
 ('83000000-0000-4000-8000-000000000002','82000000-0000-4000-8000-000000000002',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001',-500,'PEN','PRIMARY','2026-09-15T12:00:00Z'),
 ('83000000-0000-4000-8000-000000000003','82000000-0000-4000-8000-000000000003',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001',-1000,'PEN','SOURCE','2026-09-15T12:00:00Z'),
 ('83000000-0000-4000-8000-000000000004','82000000-0000-4000-8000-000000000003',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000002',1000,'PEN','DESTINATION','2026-09-15T12:00:00Z');


SET LOCAL "request.jwt.claim.sub"='80000000-0000-4000-8000-000000000001';
INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) SELECT user_id,'TRANSACTION',id,revision,'UPSERT',to_jsonb(t) FROM public.transactions t WHERE id='82000000-0000-4000-8000-000000000002';
CREATE FUNCTION pg_temp.pull_tx() RETURNS jsonb LANGUAGE sql AS $$
 SELECT jsonb_agg(item ORDER BY (item->>'sequence')::bigint)
 FROM jsonb_array_elements(public.pull_financial_changes_v1(1,0,500)->'changes') item
 WHERE item->>'entity_id'='82000000-0000-4000-8000-000000000099'
$$;
SELECT is(public.register_transaction_v1(jsonb_build_object(
 'contract_version',1,'idempotency_key','standard-pull-register','request_hash','standard-pull-hash',
 'transaction',jsonb_build_object('id','82000000-0000-4000-8000-000000000099','type','INCOME','amount_minor',2500,
 'currency_code','PEN','source_account_id','81000000-0000-4000-8000-000000000001','occurred_at','2026-09-15T12:00:00Z')
))->>'status','APPLIED','REGISTER succeeds');
SELECT is(jsonb_array_length(pg_temp.pull_tx()->0->'payload'->'ledger_entries'),1,'REGISTER includes real ledger');
SELECT is(pg_temp.pull_tx()->0->>'revision','1','REGISTER matches revision');
SELECT is(pg_temp.pull_tx()->0->'payload'->>'source_account_id','81000000-0000-4000-8000-000000000001','income keeps Android single-account field');
SELECT is((SELECT public.revise_transaction_v1(c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c)))->>'status'
 FROM (SELECT jsonb_build_object('contract_version',1,'command_type','REVISE_TRANSACTION',
 'idempotency_key','84000000-0000-4000-8000-000000000099','transaction_id','82000000-0000-4000-8000-000000000099',
 'expected_revision',1,'depends_on_command_id',NULL,'reason','pull regression',
 'revised_payload',jsonb_build_object('type','INCOME','operation_kind','STANDARD','amount_minor',1500,
 'currency_code','PEN','source_account_id','81000000-0000-4000-8000-000000000001','destination_account_id',NULL,
 'category_id',NULL,'merchant_id',NULL,'merchant_provisional_text',NULL,
 'occurred_at','2026-09-15T12:00:00.000Z','note','revised')) c) commands),
 'APPLIED','REVISE succeeds');
SELECT ok((SELECT bool_and(item->>'revision'='2' AND item->'payload'->>'status'='REVISED')
 FROM jsonb_array_elements(pg_temp.pull_tx()) item),'all historical events carry coherent current revision');
SELECT ok((SELECT bool_and(jsonb_array_length(item->'payload'->'ledger_entries')=3)
 FROM jsonb_array_elements(pg_temp.pull_tx()) item),'all revisions include original reversal replacement');
SELECT is((SELECT sum((entry->>'signed_amount_minor')::bigint)
 FROM jsonb_array_elements(pg_temp.pull_tx()->0->'payload'->'ledger_entries') entry),1500::numeric,'revision net is authoritative');
SELECT is(pg_temp.pull_tx()->0->'payload'->'ledger_entries',
 (SELECT jsonb_agg(jsonb_build_object('id',l.id,'account_id',l.account_id,'role',l.entry_role,
 'signed_amount_minor',l.signed_amount_minor,'currency_code',l.currency_code,'created_at',l.created_at) ORDER BY l.created_at,l.id)
 FROM internal.ledger_entries l WHERE l.user_id='80000000-0000-4000-8000-000000000001' AND l.transaction_id='82000000-0000-4000-8000-000000000099'),
 'projection uses canonical rows and physical IDs, never ledger_effects guesses');
CREATE TEMP TABLE page_one AS SELECT public.pull_financial_changes_v1(1,0,1) data;
SELECT is(jsonb_array_length((SELECT data->'changes' FROM page_one)),1,'limit remains one');
SELECT is((SELECT data->>'has_more' FROM page_one),'true','has_more preserved');
SELECT is((SELECT data->>'next_sequence' FROM page_one),(SELECT data->'changes'->0->>'sequence' FROM page_one),'cursor is feed sequence');
SELECT ok((SELECT bool_and((item->>'sequence')::bigint>(p.data->>'next_sequence')::bigint)
 FROM page_one p, LATERAL jsonb_array_elements(public.pull_financial_changes_v1(1,(p.data->>'next_sequence')::bigint,500)->'changes') item),
 'next page starts strictly after cursor');
SELECT is((SELECT public.void_transaction_v1(c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c)))->>'status'
 FROM (SELECT jsonb_build_object('contract_version',1,'command_type','VOID_TRANSACTION',
 'idempotency_key','84000000-0000-4000-8000-000000000098','transaction_id','82000000-0000-4000-8000-000000000099',
 'expected_revision',2,'depends_on_command_id',NULL,'reason','void pull regression','revised_payload',NULL) c) commands),
 'APPLIED','VOID succeeds');
SELECT ok((SELECT bool_and(item->>'revision'='3' AND item->'payload'->>'status'='VOIDED')
 FROM jsonb_array_elements(pg_temp.pull_tx()) item),'all events now match VOID head');
SELECT ok((SELECT bool_and(jsonb_array_length(item->'payload'->'ledger_entries')=4)
 FROM jsonb_array_elements(pg_temp.pull_tx()) item),'VOID retains complete canonical ledger');
SELECT is((SELECT sum((entry->>'signed_amount_minor')::bigint)
 FROM jsonb_array_elements(pg_temp.pull_tx()->0->'payload'->'ledger_entries') entry),0::numeric,'VOID net is zero');
SELECT is((SELECT item->'payload' FROM jsonb_array_elements(public.pull_financial_changes_v1(1,0,500)->'changes') item
 WHERE item->>'entity_id'='82000000-0000-4000-8000-000000000002' LIMIT 1),
 (SELECT internal.credit_transaction_sync_projection_v1(t) FROM public.transactions t WHERE t.id='82000000-0000-4000-8000-000000000002'),
 'card projection remains unchanged');
-- CARD_PAYMENT uses exactly the existing specialized projection too.
INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,amount_minor,currency_code,status,revision)
VALUES('82000000-0000-4000-8000-000000000098','80000000-0000-4000-8000-000000000001',
 '81000000-0000-4000-8000-000000000001','TRANSFER','CARD_PAYMENT',100,'PEN','CONFIRMED',1);
INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
SELECT user_id,'TRANSACTION',id,revision,'UPSERT',to_jsonb(t) FROM public.transactions t
WHERE id='82000000-0000-4000-8000-000000000098';
SELECT is((SELECT item->'payload' FROM jsonb_array_elements(public.pull_financial_changes_v1(1,0,500)->'changes') item
 WHERE item->>'entity_id'='82000000-0000-4000-8000-000000000098' LIMIT 1),
 (SELECT internal.credit_transaction_sync_projection_v1(t) FROM public.transactions t WHERE t.id='82000000-0000-4000-8000-000000000098'),
 'card payment projection remains unchanged');
SET LOCAL "request.jwt.claim.sub"='80000000-0000-4000-8000-000000000002';
SELECT is(public.pull_financial_changes_v1(1,0,500)->'changes','[]'::jsonb,'other owner sees none of these changes');
SELECT ok(NOT has_function_privilege('anon','public.pull_financial_changes_v1(integer,bigint,integer)','EXECUTE'),'anon excluded');
SELECT ok(has_function_privilege('authenticated','public.pull_financial_changes_v1(integer,bigint,integer)','EXECUTE'),'authenticated retains execute');
SELECT ok(NOT has_function_privilege('authenticated','internal.transaction_sync_projection_v1(public.transactions)','EXECUTE'),'internal projection stays private');
SELECT * FROM finish();
ROLLBACK;

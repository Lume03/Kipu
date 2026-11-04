BEGIN;
SELECT plan(20);
SELECT is((SELECT count(*)::int FROM public.categories WHERE origin='SYSTEM' AND user_id IS NULL AND category_type='EXPENSE'),51,'seeded system roots and children classify expenses');
SELECT is((SELECT count(*)::int FROM public.categories WHERE origin='SYSTEM' AND user_id IS NULL AND category_type IN ('GENERAL','INCOME')),0,'system expense catalog cannot populate the income tab');
SELECT is((SELECT count(*)::int FROM public.categories c JOIN public.categories p ON c.parent_id=p.id WHERE c.category_type<>p.category_type),0,'child types match their root');
SELECT ok((SELECT tgenabled='O' FROM pg_trigger WHERE tgrelid='public.categories'::regclass AND tgname='trg_inherit_category_type_from_root'),'root type guard remains enabled');

INSERT INTO auth.users(id,email) VALUES('97000000-0000-4000-8000-000000000001','catalog-regression@kipu.test');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id) VALUES
('97000000-0000-4000-8000-000000000002','97000000-0000-4000-8000-000000000001','Cash','CASH','PEN','CASH');
INSERT INTO public.categories(id,user_id,name,origin,category_type) VALUES
('97000000-0000-4000-8000-000000000003','97000000-0000-4000-8000-000000000001','Compras','CUSTOM','INCOME'),
('97000000-0000-4000-8000-000000000004','97000000-0000-4000-8000-000000000001','General','CUSTOM','GENERAL');
SELECT is((SELECT category_type FROM public.categories WHERE id='97000000-0000-4000-8000-000000000003'),'INCOME','custom names never determine type');
SELECT is((SELECT category_type FROM public.categories WHERE id='97000000-0000-4000-8000-000000000004'),'GENERAL','custom GENERAL roots remain shared');
SELECT throws_ok($$UPDATE public.categories SET category_type='INCOME' WHERE id='00000000-0000-0000-0000-000000000006'$$,'23514',NULL,'ordinary root retyping with children stays forbidden');
SELECT throws_ok($$INSERT INTO public.transactions(id,user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,occurred_at,status,revision) VALUES
('97000000-0000-4000-8000-000000000005','97000000-0000-4000-8000-000000000001','97000000-0000-4000-8000-000000000002','00000000-0000-0000-0000-000000000601','INCOME','STANDARD',100,'PEN',now(),'CONFIRMED',1)$$,'23514',NULL,'new income cannot assign an expense subcategory');

-- Reproduce a pre-migration income association without changing its identity or ledger.
ALTER TABLE public.transactions DISABLE TRIGGER trg_validate_transaction_category_type;
INSERT INTO public.transactions(id,user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,occurred_at,status,revision) VALUES
('97000000-0000-4000-8000-000000000006','97000000-0000-4000-8000-000000000001','97000000-0000-4000-8000-000000000002','00000000-0000-0000-0000-000000000006','INCOME','STANDARD',100,'PEN','2026-09-15T12:00:00Z','CONFIRMED',1);
ALTER TABLE public.transactions ENABLE TRIGGER trg_validate_transaction_category_type;
INSERT INTO internal.ledger_entries(id,transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at) VALUES
('97000000-0000-4000-8000-000000000007','97000000-0000-4000-8000-000000000006','97000000-0000-4000-8000-000000000001','97000000-0000-4000-8000-000000000002',100,'PEN','PRIMARY','2026-09-15T12:00:00Z');
SELECT lives_ok($$UPDATE public.transactions SET category_id=category_id,amount_minor=amount_minor WHERE id='97000000-0000-4000-8000-000000000006'$$,'unchanged historical category survives an unrelated update');
SELECT throws_ok($$UPDATE public.transactions SET category_id='00000000-0000-0000-0000-000000000007' WHERE id='97000000-0000-4000-8000-000000000006'$$,'23514',NULL,'historical exemption does not allow a new incompatible assignment');
SET LOCAL "request.jwt.claim.sub"='97000000-0000-4000-8000-000000000001';
SELECT is((SELECT public.revise_transaction_v1(c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c)))->>'status'
FROM (SELECT jsonb_build_object('contract_version',1,'command_type','REVISE_TRANSACTION',
'idempotency_key','97000000-0000-4000-8000-000000000008','transaction_id','97000000-0000-4000-8000-000000000006',
'expected_revision',1,'depends_on_command_id',NULL,'reason','Preserve history',
'revised_payload',jsonb_build_object('type','INCOME','operation_kind','STANDARD','amount_minor',100,'currency_code','PEN',
'source_account_id','97000000-0000-4000-8000-000000000002','destination_account_id',NULL,
'category_id','00000000-0000-0000-0000-000000000006','merchant_id',NULL,'merchant_provisional_text',NULL,
'occurred_at','2026-09-15T12:00:00.000Z','note','Revised historic')) AS c) command),'APPLIED','revision RPC preserves an unchanged historical income category');
SELECT is((SELECT category_id::text FROM public.transactions WHERE id='97000000-0000-4000-8000-000000000006'),'00000000-0000-0000-0000-000000000006','historical category ID stays linked');
SELECT is((SELECT sum(signed_amount_minor) FROM internal.ledger_entries WHERE transaction_id='97000000-0000-4000-8000-000000000006'),100::numeric,'unrelated revision preserves income balance');
SELECT is((SELECT count(*)::int FROM public.categories WHERE id IN ('97000000-0000-4000-8000-000000000003','97000000-0000-4000-8000-000000000004')),2,'personal roots remain intact');

-- A queued legacy create inherits the corrected catalog type. A prior GENERAL receipt
-- is recognized without changing its payload, preserving idempotency across the repair.
SELECT lives_ok($$SELECT public.create_category_v1(jsonb_build_object(
'operation_id','97000000-0000-4000-8000-000000000009','category_id','97000000-0000-4000-8000-000000000010',
'parent_id','00000000-0000-0000-0000-000000000006','category_type','GENERAL',
'name','Queued legacy child','icon','star','color','#123456'))$$,'queued legacy GENERAL child create inherits repaired system root');
SELECT is((SELECT category_type FROM public.categories WHERE id='97000000-0000-4000-8000-000000000010'),'EXPENSE','queued child persists the correct expense type');
UPDATE internal.command_receipts SET command_payload=jsonb_set(command_payload,'{category_type}','"GENERAL"'),
request_hash=encode(extensions.digest(jsonb_set(command_payload,'{category_type}','"GENERAL"')::text,'sha256'),'hex')
WHERE user_id='97000000-0000-4000-8000-000000000001' AND idempotency_key='97000000-0000-4000-8000-000000000009';
SELECT lives_ok($$SELECT public.create_category_v1(jsonb_build_object(
'operation_id','97000000-0000-4000-8000-000000000009','category_id','97000000-0000-4000-8000-000000000010',
'parent_id','00000000-0000-0000-0000-000000000006','category_type','GENERAL',
'name','Queued legacy child','icon','star','color','#123456'))$$,'retry recognizes otherwise identical pre-correction receipt');
SELECT is((SELECT count(*)::int FROM public.categories WHERE id='97000000-0000-4000-8000-000000000010'),1,'replay never duplicates the custom child');
SELECT is((SELECT command_payload->>'category_type' FROM internal.command_receipts
WHERE user_id='97000000-0000-4000-8000-000000000001' AND idempotency_key='97000000-0000-4000-8000-000000000009'),'GENERAL','historical receipt payload is not rewritten');
SELECT throws_ok($$SELECT public.create_category_v1(jsonb_build_object(
'operation_id','97000000-0000-4000-8000-000000000009','category_id','97000000-0000-4000-8000-000000000010',
'parent_id','00000000-0000-0000-0000-000000000006','category_type','GENERAL',
'name','Different child','icon','star','color','#123456'))$$,'23505',NULL,'legacy receipt exception rejects a genuinely different payload');
SELECT * FROM finish();
ROLLBACK;

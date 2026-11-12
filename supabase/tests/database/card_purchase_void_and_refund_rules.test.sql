BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;
SELECT no_plan();

SELECT has_function('public','refund_transaction_v1',ARRAY['jsonb'],'specialized refund RPC exists');
SELECT ok(has_function_privilege('authenticated','public.refund_transaction_v1(jsonb)','EXECUTE'),'authenticated can call refund RPC');
SELECT ok(NOT has_function_privilege('anon','public.refund_transaction_v1(jsonb)','EXECUTE'),'anon cannot call refund RPC');
SELECT ok(NOT has_function_privilege('authenticated','internal.refund_transaction_hash_v1(jsonb)','EXECUTE'),'hash helper is private');
SELECT ok(NOT has_table_privilege('authenticated','public.transaction_links','INSERT'),'clients cannot insert links directly');
SELECT ok(NOT has_table_privilege('authenticated','public.transaction_links','UPDATE'),'clients cannot update links directly');
SELECT ok(NOT has_table_privilege('authenticated','public.transaction_links','DELETE'),'clients cannot delete links directly');
SELECT ok(NOT has_table_privilege('authenticated','public.transactions','DELETE'),'clients cannot delete financial transactions directly');

INSERT INTO auth.users(id,email) VALUES
 ('8f000000-0000-4000-8000-000000000001','refund-owner@kipu.test'),
 ('8f000000-0000-4000-8000-000000000002','refund-other@kipu.test')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id,initial_balance_minor_units) VALUES
 ('8f100000-0000-4000-8000-000000000001','8f000000-0000-4000-8000-000000000001','Cash','CASH','PEN','CASH',0),
 ('8f100000-0000-4000-8000-000000000002','8f000000-0000-4000-8000-000000000002','Other cash','CASH','PEN','CASH',0),
 ('8f100000-0000-4000-8000-000000000003','8f000000-0000-4000-8000-000000000001','Visa liability','CREDIT_LIABILITY','PEN',NULL,0)
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.cards(id,user_id,creation_operation_id,account_id,network,alias,last4,is_credit,
    credit_limit_minor,closing_day,due_day,currency_code,issuer) VALUES
 ('8f200000-0000-4000-8000-000000000001','8f000000-0000-4000-8000-000000000001',
  '8f210000-0000-4000-8000-000000000001','8f100000-0000-4000-8000-000000000003',
  'VISA','Visa','1234',true,100000,15,5,'PEN','BCP')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.categories(id,user_id,name,origin,category_type) VALUES
 ('8f300000-0000-4000-8000-000000000001','8f000000-0000-4000-8000-000000000001','Alimentos','USER_CREATED','EXPENSE'),
 ('8f300000-0000-4000-8000-000000000002','8f000000-0000-4000-8000-000000000002','Alimentos','USER_CREATED','EXPENSE')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.transactions(id,user_id,account_id,card_id,category_id,transaction_type,operation_kind,
    amount_minor,currency_code,occurred_at,installment_count,status,revision) VALUES
 ('8f400000-0000-4000-8000-000000000001','8f000000-0000-4000-8000-000000000001',
  '8f100000-0000-4000-8000-000000000001',NULL,'8f300000-0000-4000-8000-000000000001',
  'EXPENSE','STANDARD',10000,'PEN','2026-09-15T12:00:00Z',1,'CONFIRMED',1),
 ('8f400000-0000-4000-8000-000000000002','8f000000-0000-4000-8000-000000000002',
  '8f100000-0000-4000-8000-000000000002',NULL,'8f300000-0000-4000-8000-000000000002',
  'EXPENSE','STANDARD',10000,'PEN','2026-09-15T12:00:00Z',1,'CONFIRMED',1),
 ('8f400000-0000-4000-8000-000000000003','8f000000-0000-4000-8000-000000000001',
  '8f100000-0000-4000-8000-000000000001',NULL,'8f300000-0000-4000-8000-000000000001',
  'EXPENSE','STANDARD',10000,'PEN','2026-09-15T12:00:00Z',2,'CONFIRMED',1),
 ('8f400000-0000-4000-8000-000000000004','8f000000-0000-4000-8000-000000000001',
  '8f100000-0000-4000-8000-000000000003','8f200000-0000-4000-8000-000000000001','8f300000-0000-4000-8000-000000000001',
  'EXPENSE','CARD_PURCHASE',10000,'PEN','2026-09-15T12:00:00Z',1,'CONFIRMED',1),
 ('8f400000-0000-4000-8000-000000000005','8f000000-0000-4000-8000-000000000001',
  '8f100000-0000-4000-8000-000000000001',NULL,'8f300000-0000-4000-8000-000000000001',
  'EXPENSE','STANDARD',2000,'PEN','2026-09-16T12:00:00Z',1,'CONFIRMED',1)
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.credit_installments(id,user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status) VALUES
 ('8f500000-0000-4000-8000-000000000001','8f000000-0000-4000-8000-000000000001',
  '8f400000-0000-4000-8000-000000000004',1,'2026-10-05',10000,0,'PENDING')
ON CONFLICT(id) DO NOTHING;

CREATE FUNCTION pg_temp.make_refund_command(
    p_key uuid,p_purchase uuid,p_refund uuid,p_amount bigint,p_currency text,p_occurred timestamptz
) RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = pg_temp,public,internal,extensions AS $$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version',1,'idempotency_key',p_key::text,
            'purchase_transaction_id',p_purchase::text,
            'refund_transaction',jsonb_build_object(
                'id',p_refund::text,'amount_minor',p_amount,'currency_code',p_currency,
                'occurred_at',p_occurred
            )
        ) AS command
    )
    SELECT command || jsonb_build_object('request_hash',internal.refund_transaction_hash_v1(command))
      FROM body;
$$;


-- Existing ledger evidence for the purchase fixture.
INSERT INTO internal.ledger_entries(user_id,transaction_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at)
VALUES('8f000000-0000-4000-8000-000000000001','8f400000-0000-4000-8000-000000000004',
'8f100000-0000-4000-8000-000000000003',-10000,'PEN','LIABILITY','2026-09-15T12:00:00Z');
CREATE FUNCTION pg_temp.void_command(p_tx uuid,p_key uuid) RETURNS jsonb LANGUAGE sql AS $$
WITH c AS (SELECT jsonb_build_object('contract_version',1,'command_type','VOID_TRANSACTION',
'idempotency_key',p_key,'transaction_id',p_tx,'expected_revision',1,'depends_on_command_id',NULL,
'reason','L2 test','revised_payload',NULL) body)
SELECT body || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(body)) FROM c;
$$;
CREATE TEMP TABLE l2_commands AS SELECT
pg_temp.void_command('8f400000-0000-4000-8000-000000000004','8f600000-0000-4000-8000-000000000010') AS void_purchase,
pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000011','8f400000-0000-4000-8000-000000000004',
'8f700000-0000-4000-8000-000000000011',4000,'PEN','2026-10-01T12:00:00Z') AS refund,
pg_temp.void_command('8f700000-0000-4000-8000-000000000011','8f600000-0000-4000-8000-000000000012') AS void_refund;
SELECT set_config('request.jwt.claim.sub','8f000000-0000-4000-8000-000000000001',true);
SELECT is(public.refund_transaction_v1(pg_temp.make_refund_command(extensions.gen_random_uuid(),
'8f400000-0000-4000-8000-000000000001',extensions.gen_random_uuid(),100,'PEN','2026-10-01T12:00:00Z'))#>>'{error,code}',
'REFUND_REQUIRES_CARD_PURCHASE','standard expense refund rejected');
SELECT is(public.refund_transaction_v1(pg_temp.make_refund_command(extensions.gen_random_uuid(),
'8f400000-0000-4000-8000-000000000004',extensions.gen_random_uuid(),100,'PEN','2026-09-14T12:00:00Z'))#>>'{error,code}',
'INVALID_REFUND_DATE','refund before purchase rejected');
SELECT is(public.refund_transaction_v1(pg_temp.make_refund_command(extensions.gen_random_uuid(),
'8f400000-0000-4000-8000-000000000004',extensions.gen_random_uuid(),100,'PEN',date_trunc('day',now())+interval '2 days'))#>>'{error,code}',
'INVALID_REFUND_DATE','future refund rejected in Lima');
SELECT is(public.refund_transaction_v1((SELECT refund FROM l2_commands))->>'status','APPLIED','card refund applies');
SELECT is(public.void_transaction_v1((SELECT void_purchase FROM l2_commands))#>>'{error,code}',
'PURCHASE_HAS_ACTIVE_REFUNDS','purchase with active refund cannot be voided');
SELECT is(public.void_transaction_v1((SELECT void_refund FROM l2_commands))->>'status',
'APPLIED','refund can be voided');
SELECT is((SELECT sum(outstanding_minor)::bigint FROM private.credit_installment_balances(auth.uid(),
'8f200000-0000-4000-8000-000000000001',NULL)),10000::bigint,'refund void restores installment balance');
-- A real allocation is enough to prevent purchase void.
INSERT INTO public.credit_payment_allocations(id,user_id,payment_transaction_id,installment_id,allocated_minor)
VALUES('8f800000-0000-4000-8000-000000000001',auth.uid(),'8f400000-0000-4000-8000-000000000005',
'8f500000-0000-4000-8000-000000000001',100);
SELECT is(public.void_transaction_v1((SELECT void_purchase FROM l2_commands))#>>'{error,code}',
'CARD_PURCHASE_HAS_PAYMENTS','allocation blocks purchase void');
DELETE FROM public.credit_payment_allocations WHERE id='8f800000-0000-4000-8000-000000000001';
SELECT is(public.void_transaction_v1((SELECT void_purchase FROM l2_commands))->>'status',
'APPLIED','unpaid purchase void applies');
SELECT is((SELECT status::text FROM public.credit_installments WHERE id='8f500000-0000-4000-8000-000000000001'),
'VOIDED','installment voided');
SELECT is((SELECT coalesce(sum(outstanding_minor),0)::bigint FROM private.credit_installment_balances(auth.uid(),
'8f200000-0000-4000-8000-000000000001',NULL)),0::bigint,'card debt becomes zero');
SELECT is((SELECT sum(signed_amount_minor)::bigint FROM internal.ledger_entries WHERE transaction_id='8f400000-0000-4000-8000-000000000004'),
0::bigint,'purchase ledger reversed');
SELECT ok(EXISTS(SELECT 1 FROM internal.sync_changes WHERE entity_id='8f400000-0000-4000-8000-000000000004'
AND revision=2),'void published to feed');
SELECT is(public.void_transaction_v1((SELECT void_purchase FROM l2_commands))->>'status',
'DUPLICATE','void retry is idempotent');
SELECT * FROM finish();
ROLLBACK;

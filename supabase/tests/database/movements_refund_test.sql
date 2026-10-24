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

CREATE TEMP TABLE refund_commands AS
SELECT
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000001',
        '8f400000-0000-4000-8000-000000000001','8f700000-0000-4000-8000-000000000001',4000,'PEN','2026-10-24T12:30:00Z') AS partial,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000002',
        '8f400000-0000-4000-8000-000000000001','8f700000-0000-4000-8000-000000000002',6000,'PEN','2026-10-25T12:30:00Z') AS final,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000003',
        '8f400000-0000-4000-8000-000000000001','8f700000-0000-4000-8000-000000000003',1,'PEN','2026-10-26T12:30:00Z') AS excess,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000004',
        '8f400000-0000-4000-8000-000000000002','8f700000-0000-4000-8000-000000000004',1000,'PEN','2026-10-24T12:30:00Z') AS foreign_purchase,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000005',
        '8f400000-0000-4000-8000-000000000003','8f700000-0000-4000-8000-000000000005',1000,'PEN','2026-10-24T12:30:00Z') AS installments,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000006',
        '8f400000-0000-4000-8000-000000000004','8f700000-0000-4000-8000-000000000006',1000,'PEN','2026-10-24T12:30:00Z') AS card_refund,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000001',
        '8f400000-0000-4000-8000-000000000001','8f700000-0000-4000-8000-000000000007',3000,'PEN','2026-10-27T12:30:00Z') AS idempotency_conflict,
    pg_temp.make_refund_command('8f600000-0000-4000-8000-000000000008',
        '8f400000-0000-4000-8000-000000000005','8f700000-0000-4000-8000-000000000008',1000,'PEN','2026-10-24T12:30:00Z') AS rollback;
GRANT SELECT ON refund_commands TO authenticated;

SELECT is(internal.refund_transaction_hash_v1(jsonb_build_object(
    'purchase_transaction_id','8f400000-0000-4000-8000-000000000001',
    'refund_transaction',jsonb_build_object('id','8f700000-0000-4000-8000-000000000001',
      'amount_minor',4000,'currency_code','PEN','occurred_at','2026-10-24T12:30:00Z')
)), (SELECT partial->>'request_hash' FROM refund_commands), 'SQL hash matches the canonical command fields');

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub"='8f000000-0000-4000-8000-000000000001';

SELECT is(public.refund_transaction_v1((SELECT partial FROM refund_commands))->>'status','APPLIED','partial standard refund applies');
SELECT is(public.refund_transaction_v1((SELECT partial FROM refund_commands))->>'status','DUPLICATE','exact retry is idempotent');
SELECT is(public.refund_transaction_v1((SELECT idempotency_conflict FROM refund_commands))->>'status','CONFLICT','same key with changed command is rejected');
SELECT is(public.refund_transaction_v1((SELECT final FROM refund_commands))->>'status','APPLIED','remaining balance can be refunded exactly');
SELECT is(public.refund_transaction_v1((SELECT excess FROM refund_commands))->'error'->>'code','REFUND_EXCEEDS_REMAINDER','excess is rejected with a stable code');
SELECT is(public.refund_transaction_v1((SELECT foreign_purchase FROM refund_commands))->'error'->>'code','PURCHASE_NOT_FOUND','another owners purchase is not disclosed');
SELECT is(public.refund_transaction_v1((SELECT installments FROM refund_commands))->'error'->>'code','PURCHASE_NOT_ELIGIBLE','purchase with installments is rejected');
SELECT is(public.refund_transaction_v1((SELECT card_refund FROM refund_commands))->>'status','APPLIED','one-installment credit-card purchase can be refunded to its liability account');
RESET ROLE;
CREATE FUNCTION public.refund_test_reject_ledger() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
    IF NEW.transaction_id = '8f700000-0000-4000-8000-000000000008'::uuid THEN
        RAISE EXCEPTION 'forced refund ledger failure' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.refund_test_reject_ledger() FROM PUBLIC, anon, authenticated, service_role;
CREATE TRIGGER refund_test_reject_ledger BEFORE INSERT ON internal.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION public.refund_test_reject_ledger();
SET LOCAL ROLE authenticated;
SELECT throws_ok($$SELECT public.refund_transaction_v1((SELECT rollback FROM refund_commands))$$,
    'P0001','forced refund ledger failure','ledger failure aborts the entire refund transaction');
RESET ROLE;
DROP TRIGGER refund_test_reject_ledger ON internal.ledger_entries;
DROP FUNCTION public.refund_test_reject_ledger();
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE id='8f700000-0000-4000-8000-000000000008'),0,'rollback leaves no refund transaction');
SELECT is((SELECT count(*)::integer FROM public.transaction_links WHERE target_transaction_id='8f700000-0000-4000-8000-000000000008'),0,'rollback leaves no refund link');
SELECT is((SELECT count(*)::integer FROM internal.command_receipts WHERE idempotency_key='8f600000-0000-4000-8000-000000000008'),0,'rollback leaves no idempotency receipt');
SELECT is((SELECT sum(amount_minor)::bigint FROM public.transactions WHERE id IN
    ('8f700000-0000-4000-8000-000000000001','8f700000-0000-4000-8000-000000000002')),10000::bigint,'refund amount is positive and purchase total is capped');
SELECT is((SELECT count(*)::integer FROM public.transaction_links WHERE user_id='8f000000-0000-4000-8000-000000000001'
    AND source_transaction_id='8f400000-0000-4000-8000-000000000001' AND link_type='REFUND'),2,'links point from purchase to its two refunds');
SELECT ok((SELECT bool_and(t.transaction_type='INCOME' AND t.operation_kind='REFUND'
    AND t.refund_of_transaction_id IS NULL AND t.category_id IS NULL)
    FROM public.transactions t WHERE t.id IN ('8f700000-0000-4000-8000-000000000001','8f700000-0000-4000-8000-000000000002')),
    'refunds are INCOME/REFUND and do not set the legacy refund pointer or an incompatible expense category');
SELECT is((SELECT signed_amount_minor FROM internal.ledger_entries WHERE transaction_id='8f700000-0000-4000-8000-000000000001'),4000::bigint,'cash refund posts a positive ledger entry');
SELECT is((SELECT entry_role::text FROM internal.ledger_entries WHERE transaction_id='8f700000-0000-4000-8000-000000000006'),'LIABILITY','credit-card refund posts against the original liability account');
SELECT is((SELECT account_id FROM internal.ledger_entries WHERE transaction_id='8f700000-0000-4000-8000-000000000006'),'8f100000-0000-4000-8000-000000000003'::uuid,'card refund uses the original card account');
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE id='8f700000-0000-4000-8000-000000000003'),0,'rejected excess creates no transaction');
SELECT is((SELECT count(*)::integer FROM public.transaction_links WHERE target_transaction_id='8f700000-0000-4000-8000-000000000003'),0,'rejected excess creates no link');
SELECT is((SELECT count(*)::integer FROM internal.sync_changes WHERE entity_type='TRANSACTION'
    AND entity_id='8f700000-0000-4000-8000-000000000001' AND payload->>'operation_kind'='REFUND'),1,'applied refund is included in sync changes');
SET LOCAL ROLE authenticated;
SELECT throws_ok($$INSERT INTO public.transaction_links(user_id,source_transaction_id,target_transaction_id,link_type)
    VALUES('8f000000-0000-4000-8000-000000000001','8f400000-0000-4000-8000-000000000001',
    '8f700000-0000-4000-8000-000000000001','REFUND')$$,'42501',NULL,'direct link insert is denied');

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;

BEGIN;
SELECT plan(6);

-- HU-19 scenario 2: interrupt the STANDARD transfer after SOURCE was posted.
-- All test DDL and fixtures disappear with the final rollback.
INSERT INTO auth.users(id,email) VALUES ('f3200000-0000-4000-8000-000000000001','f32-atomic@kipu.app');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id) VALUES
 ('f3200000-0000-4000-8000-000000000002','f3200000-0000-4000-8000-000000000001','Source','SAVINGS','PEN','GENERIC'),
 ('f3200000-0000-4000-8000-000000000003','f3200000-0000-4000-8000-000000000001','Destination','CASH','PEN','CASH');

CREATE FUNCTION pg_temp.fail_transfer_destination() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.transaction_id = 'f3200000-0000-4000-8000-000000000004'::uuid AND NEW.entry_role::text = 'DESTINATION' THEN
    IF NOT EXISTS (SELECT 1 FROM internal.ledger_entries WHERE transaction_id=NEW.transaction_id AND entry_role::text='SOURCE') THEN
      RAISE EXCEPTION 'destination reached without source';
    END IF;
    RAISE EXCEPTION 'injected failure between transfer entries';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER test_transfer_destination_failure BEFORE INSERT ON internal.ledger_entries
FOR EACH ROW EXECUTE FUNCTION pg_temp.fail_transfer_destination();

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub"='f3200000-0000-4000-8000-000000000001';
SELECT throws_ok($call$
  SELECT public.register_transaction_v1(jsonb_build_object('contract_version',1,
    'idempotency_key','f32-transfer-atomic','request_hash','f32-transfer-atomic',
    'transaction',jsonb_build_object('id','f3200000-0000-4000-8000-000000000004',
      'type','TRANSFER','amount_minor',4200,'currency_code','PEN',
      'source_account_id','f3200000-0000-4000-8000-000000000002',
      'destination_account_id','f3200000-0000-4000-8000-000000000003',
      'occurred_at','2026-11-05T12:00:00Z')))
$call$, 'P0001', 'injected failure between transfer entries', 'Failure really occurs after first posting');
RESET ROLE;
SELECT is((SELECT count(*) FROM public.transactions WHERE id='f3200000-0000-4000-8000-000000000004'),0::bigint,'No partial transaction');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='f3200000-0000-4000-8000-000000000004'),0::bigint,'Neither transfer posting survives');
SELECT is((SELECT count(*) FROM internal.command_receipts WHERE user_id='f3200000-0000-4000-8000-000000000001' AND idempotency_key='f32-transfer-atomic'),0::bigint,'No applied receipt survives');
SELECT is((SELECT count(*) FROM internal.sync_changes WHERE user_id='f3200000-0000-4000-8000-000000000001' AND entity_id='f3200000-0000-4000-8000-000000000004'),0::bigint,'No partial movement sync event');
SELECT is((SELECT coalesce(sum(signed_amount_minor),0) FROM internal.ledger_entries WHERE user_id='f3200000-0000-4000-8000-000000000001'),0::numeric,'Authoritative balance stays unchanged');
SELECT * FROM finish();
ROLLBACK;

BEGIN;
SET LOCAL search_path = public, extensions;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
CREATE TEMP TABLE quota_tap(line text);
INSERT INTO quota_tap SELECT plan(14);
INSERT INTO auth.users(id,email) VALUES ('a0211111-1111-4111-8111-111111111111','quota-fixture@kipu.test');
SELECT set_config('request.jwt.claim.sub','a0211111-1111-4111-8111-111111111111',true);

CREATE FUNCTION pg_temp.account_command(n integer, kind text DEFAULT 'SAVINGS', parent uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql AS $$
 SELECT jsonb_build_object('operation_id',md5('quota-op-'||n)::uuid,'account_id',md5('quota-account-'||n)::uuid,
 'opening_movement_id',md5('quota-opening-'||n)::uuid,'alias','QA '||n,'type',kind,'currency','PEN',
 'preset_id',CASE WHEN kind='WALLET' THEN 'YAPE' ELSE 'BCP' END,'initial_balance_minor_units',0,
 'opened_at','2026-10-21T12:00:00Z','payload_hash','quota-hash-'||n,'linked_account_id',parent);
$$;

INSERT INTO quota_tap SELECT lives_ok($$SELECT public.create_account_v1(pg_temp.account_command(1))$$,'bank without debit digits can be created');
SELECT public.create_account_v1(pg_temp.account_command(n)) FROM generate_series(2,4) n;
INSERT INTO quota_tap SELECT is((SELECT count(DISTINCT quota_id)::integer FROM private.instrument_quota_groups(auth.uid())),4,'four bank accounts consume four slots');
INSERT INTO quota_tap SELECT lives_ok($$SELECT public.create_account_v1(pg_temp.account_command(5,'WALLET',md5('quota-account-1')::uuid))$$,'affiliated wallet allowed at full quota without debit card');
INSERT INTO quota_tap SELECT is((SELECT linked_account_id FROM public.accounts WHERE id=md5('quota-account-5')::uuid),md5('quota-account-1')::uuid,'affiliation persists');
INSERT INTO quota_tap SELECT is((SELECT count(DISTINCT quota_id)::integer FROM private.instrument_quota_groups(auth.uid())),4,'wallet does not consume another slot');
INSERT INTO quota_tap SELECT lives_ok($$SELECT public.register_card_v1(jsonb_build_object(
 'operation_id',md5('quota-debit-op')::uuid,'card_id',md5('quota-debit')::uuid,'account_id',md5('quota-account-1')::uuid,
 'alias','QA debit','type','DEBIT','currency','PEN','network','VISA','issuer','BCP','last_four_digits','1234','payload_hash','qa-debit-hash'))$$,'debit card allowed at full quota');
INSERT INTO quota_tap SELECT is((SELECT count(DISTINCT quota_id)::integer FROM private.instrument_quota_groups(auth.uid())),4,'debit card shares its bank slot');
INSERT INTO quota_tap SELECT throws_like($$SELECT public.create_account_v1(pg_temp.account_command(6))$$,'%FREE_LIMIT_REACHED%','fifth independent bank remains blocked');
INSERT INTO quota_tap SELECT throws_like($$SELECT public.create_account_v1(pg_temp.account_command(7,'WALLET','a0222222-2222-4222-8222-222222222222'))$$,'%INVALID_WALLET_AFFILIATION%','invalid or foreign parent remains rejected');
CREATE FUNCTION pg_temp.appearance_command() RETURNS jsonb LANGUAGE sql AS $$
 SELECT jsonb_build_object('operation_id',md5('quota-appearance')::uuid,'instrument_id',md5('quota-account-1')::uuid,
 'instrument_type','ACCOUNT','alias','BCP','expected_revision',1);
$$;
INSERT INTO quota_tap SELECT is(public.update_instrument_appearance_v1(pg_temp.appearance_command())->>'status','APPLIED','mobile alias contract accepts account appearance');
INSERT INTO quota_tap SELECT is(public.update_instrument_appearance_v1(pg_temp.appearance_command())->>'status','DUPLICATE','appearance replay is idempotent');
INSERT INTO quota_tap SELECT is((SELECT name FROM public.accounts WHERE id=md5('quota-account-1')::uuid),'BCP','appearance persists the account name');
CREATE FUNCTION pg_temp.wallet_movement() RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE tx jsonb:=jsonb_build_object('id',md5('quota-tx')::uuid,'type','INCOME','amount_minor',1000,
 'currency_code','PEN','source_account_id',md5('quota-account-1')::uuid,'occurred_at','2026-10-21T12:10:00Z','payment_channel','YAPE');
BEGIN
 RETURN public.register_transaction_v2(jsonb_build_object('contract_version',2,
  'idempotency_key',md5('quota-wallet-movement')::uuid,
  'request_hash',internal.movement_register_hash_v2(auth.uid(),tx),'transaction',tx));
END;
$$;
INSERT INTO quota_tap SELECT is(pg_temp.wallet_movement()->>'status','APPLIED','wallet ledger accepts four shared instrument groups');
INSERT INTO quota_tap SELECT is((SELECT sum(signed_amount_minor)::bigint FROM internal.ledger_entries WHERE account_id=md5('quota-account-1')::uuid),1000::bigint,'wallet movement affects bank ledger once');
INSERT INTO quota_tap SELECT * FROM finish();
SELECT line FROM quota_tap;
ROLLBACK;

-- Fixture shared by EP-MET suites. Each caller starts a transaction and rolls back.
INSERT INTO auth.users(id,email) VALUES
('96000000-0000-4000-8000-000000000001','goal-owner-1@kipu.test'),
('96000000-0000-4000-8000-000000000002','goal-owner-2@kipu.test');

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,initial_balance_minor_units,creation_operation_id,preset_id)
VALUES
('96000000-0000-4000-8000-000000000011','96000000-0000-4000-8000-000000000001','A','CASH','PEN',0,extensions.gen_random_uuid(),'CASH'),
('96000000-0000-4000-8000-000000000012','96000000-0000-4000-8000-000000000001','B','BANK','PEN',0,extensions.gen_random_uuid(),'GENERIC'),
('96000000-0000-4000-8000-000000000013','96000000-0000-4000-8000-000000000002','Other','CASH','PEN',0,extensions.gen_random_uuid(),'CASH'),
('96000000-0000-4000-8000-000000000014','96000000-0000-4000-8000-000000000001','USD','BANK','USD',0,extensions.gen_random_uuid(),'GENERIC');

INSERT INTO public.financial_movements(operation_id,user_id,kind,amount_minor_units,currency,account_id,opening_account_id)
VALUES
(extensions.gen_random_uuid(),'96000000-0000-4000-8000-000000000001','OPENING',100000,'PEN','96000000-0000-4000-8000-000000000011','96000000-0000-4000-8000-000000000011'),
(extensions.gen_random_uuid(),'96000000-0000-4000-8000-000000000001','OPENING',20000,'PEN','96000000-0000-4000-8000-000000000012','96000000-0000-4000-8000-000000000012'),
(extensions.gen_random_uuid(),'96000000-0000-4000-8000-000000000002','OPENING',10000,'PEN','96000000-0000-4000-8000-000000000013','96000000-0000-4000-8000-000000000013');

-- Test-only SECURITY DEFINER signing helper: production never grants access to
-- internal.goal_canonical_json for authenticated callers.
CREATE FUNCTION pg_temp.goal_cmd(t text, g uuid, rev bigint, body jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE p jsonb;
BEGIN
  p := pg_catalog.jsonb_build_object('contract_version',1,'command_type',t,'goal_id',g,'payload',body);
  IF rev IS NOT NULL THEN p := p || pg_catalog.jsonb_build_object('expected_revision',rev); END IF;
  RETURN p || pg_catalog.jsonb_build_object('operation_id',extensions.gen_random_uuid(),
    'request_hash',pg_catalog.encode(extensions.digest(pg_catalog.convert_to(
      internal.goal_canonical_json(p),'UTF8'),'sha256'),'hex'));
END;
$$;

-- Narrow test-only SECURITY DEFINER oracles let authenticated RPC blocks assert
-- internal effects without granting that role access to the internal schema.
CREATE FUNCTION pg_temp.goal_reserved_test(p_user uuid, p_goal uuid)
RETURNS bigint LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT internal.goal_reserved(p_user, p_goal)
$$;
CREATE FUNCTION pg_temp.goal_sync_exists_test(p_entity uuid)
RETURNS boolean LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (SELECT 1 FROM internal.sync_changes WHERE entity_id = p_entity)
$$;
CREATE FUNCTION pg_temp.goal_receipt_exists_test(p_operation uuid)
RETURNS boolean LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (SELECT 1 FROM internal.command_receipts WHERE idempotency_key = p_operation::text)
$$;
CREATE FUNCTION pg_temp.goal_sync_selection_exists_test(p_entities uuid[])
RETURNS boolean LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (SELECT 1 FROM internal.sync_changes WHERE entity_id = ANY (p_entities))
$$;
CREATE FUNCTION pg_temp.goal_receipt_selection_exists_test(p_operations uuid[])
RETURNS boolean LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (SELECT 1 FROM internal.command_receipts
    WHERE idempotency_key = ANY (ARRAY(SELECT unnest(p_operations)::text)))
$$;
CREATE FUNCTION pg_temp.goal_canonical_test(p_command jsonb)
RETURNS text LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT internal.goal_canonical_json(p_command)
$$;

-- Run SQL blocks under `authenticated` role, with explicit synthetic JWT identity.
SELECT pg_catalog.set_config('request.jwt.claim.sub','96000000-0000-4000-8000-000000000001',true);

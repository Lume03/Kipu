-- Bind an explicit goal-funding transfer to the client operation identity.
-- Room must be able to persist the transfer and allocation together while offline;
-- the same UUID is returned by the RPC and reconciled from the transaction feed.
BEGIN;
DO $migration$
DECLARE
  v_definition text;
  v_old text := 'tx := extensions.gen_random_uuid();';
  v_new text := 'tx := (p_payload->>''operation_id'')::uuid;';
BEGIN
  v_definition := pg_catalog.pg_get_functiondef('public.reserve_goal_v1(jsonb)'::regprocedure);
  IF pg_catalog.strpos(v_definition, v_old) = 0 THEN
    RAISE EXCEPTION 'reserve_goal_v1 transfer identity implementation changed; review migration manually';
  END IF;
  IF pg_catalog.length(v_definition) - pg_catalog.length(pg_catalog.replace(v_definition, v_old, '')) <> pg_catalog.length(v_old) THEN
    RAISE EXCEPTION 'reserve_goal_v1 identity substitution is not unique; review migration manually';
  END IF;
  EXECUTE pg_catalog.replace(v_definition, v_old, v_new);
END;
$migration$;
REVOKE ALL ON FUNCTION public.reserve_goal_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.reserve_goal_v1(jsonb) TO authenticated;
COMMIT;

-- Local pre-rebuild audit: deny anonymous access and table-wide operations
-- that bypass RLS. Android uses authenticated reads/RPCs; BudgetApi also
-- performs owner-scoped INSERT/UPDATE. Preserve those existing DML grants.
BEGIN;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, anon;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public, private, internal FROM PUBLIC, anon;
REVOKE TRUNCATE, REFERENCES, TRIGGER, MAINTAIN
    ON ALL TABLES IN SCHEMA public, private, internal FROM authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.currencies, public.referential_rate_catalog
    FROM authenticated;

-- Trigger helpers and pure validators are not anonymous API endpoints.
-- Explicit authenticated/executor/service_role EXECUTE grants remain intact.
DO $migration$
DECLARE
    function_row record;
BEGIN
    FOR function_row IN
        SELECT p.oid::regprocedure AS signature
        FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname IN ('public', 'private', 'internal')
          AND pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE')
    LOOP
        -- Executor-owned functions already deny anonymous calls. Do not try
        -- to re-grant their ACLs as postgres, which is not their owner.
        EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon',
            function_row.signature);
    END LOOP;
END;
$migration$;

-- These unversioned endpoints have no Android/Edge consumers and are only
-- referenced by the legacy dispatcher itself. Keep definitions and backend
-- access for compatibility, but prevent bypassing current financial commands.
REVOKE EXECUTE ON FUNCTION public.apply_sync_command(jsonb),
    public.register_transaction(jsonb), public.allocate_credit_payment(jsonb)
    FROM authenticated;

-- Future migrations must explicitly grant client access after adding RLS.
-- PostgreSQL's global PUBLIC function default must be revoked globally;
-- revoking only the schema-level defaults would not remove it.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated;

-- These internal append-only records are read/written only by trusted
-- postgres-owned financial RPCs. No client policies or grants are needed.
ALTER TABLE internal.movement_ledger_aliases ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.movement_ledger_aliases FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.movement_ledger_effects ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.movement_ledger_effects FORCE ROW LEVEL SECURITY;

ALTER TABLE public.category_conflicts FORCE ROW LEVEL SECURITY;
ALTER TABLE public.category_presentations FORCE ROW LEVEL SECURITY;
ALTER TABLE public.credit_products FORCE ROW LEVEL SECURITY;
ALTER TABLE public.currencies FORCE ROW LEVEL SECURITY;
ALTER TABLE public.movement_official_revisions FORCE ROW LEVEL SECURITY;
ALTER TABLE public.user_devices FORCE ROW LEVEL SECURITY;

-- Cache request identity once per statement, including the private helper
-- used by least-privilege executor roles. The substitutions apply only to
-- bare identity calls: existing SELECT-wrapped expressions are unchanged.
DO $migration$
DECLARE
    policy_row record;
    using_expression text;
    check_expression text;
BEGIN
    FOR policy_row IN
        SELECT * FROM pg_catalog.pg_policies
        WHERE schemaname IN ('public', 'private', 'internal')
          AND (coalesce(qual, '') ~ '(auth.uid\(\)|private.request_user_id\(\))'
            OR coalesce(with_check, '') ~ '(auth.uid\(\)|private.request_user_id\(\))')
    LOOP
        using_expression := policy_row.qual;
        check_expression := policy_row.with_check;
        IF coalesce(using_expression, '') NOT ILIKE '%SELECT%auth.uid()%'
           AND coalesce(using_expression, '') LIKE '%auth.uid()%' THEN
            using_expression := replace(using_expression, 'auth.uid()', '(SELECT auth.uid())');
        END IF;
        IF coalesce(check_expression, '') NOT ILIKE '%SELECT%auth.uid()%'
           AND coalesce(check_expression, '') LIKE '%auth.uid()%' THEN
            check_expression := replace(check_expression, 'auth.uid()', '(SELECT auth.uid())');
        END IF;
        using_expression := replace(using_expression, 'private.request_user_id()',
            '(SELECT private.request_user_id())');
        check_expression := replace(check_expression, 'private.request_user_id()',
            '(SELECT private.request_user_id())');
        EXECUTE pg_catalog.format('ALTER POLICY %I ON %I.%I%s%s',
            policy_row.policyname, policy_row.schemaname, policy_row.tablename,
            CASE WHEN using_expression IS NULL THEN '' ELSE ' USING (' || using_expression || ')' END,
            CASE WHEN check_expression IS NULL THEN '' ELSE ' WITH CHECK (' || check_expression || ')' END);
    END LOOP;
END;
$migration$;

-- Scope client policies by operation and existing grants. Keep each original
-- name for SELECT to preserve documented/tested policy identities. Shared
-- catalog predicates belong only to SELECT, never UPDATE or DELETE.
DO $migration$
DECLARE
    policy_row record;
    operation text;
    owner_expression text;
BEGIN
    FOR policy_row IN
        SELECT * FROM pg_catalog.pg_policies
        WHERE schemaname IN ('public', 'private', 'internal')
          AND roles = ARRAY['authenticated']::name[] AND cmd = 'ALL'
    LOOP
        owner_expression := coalesce(policy_row.with_check, policy_row.qual);
        EXECUTE pg_catalog.format('DROP POLICY %I ON %I.%I',
            policy_row.policyname, policy_row.schemaname, policy_row.tablename);
        EXECUTE pg_catalog.format(
            'CREATE POLICY %I ON %I.%I FOR SELECT TO authenticated USING (%s)',
            policy_row.policyname, policy_row.schemaname, policy_row.tablename, policy_row.qual);
        FOREACH operation IN ARRAY ARRAY['INSERT', 'UPDATE', 'DELETE'] LOOP
            IF pg_catalog.has_table_privilege('authenticated',
                pg_catalog.format('%I.%I', policy_row.schemaname, policy_row.tablename), operation)
                OR (CASE WHEN operation IN ('INSERT', 'UPDATE') THEN
                    pg_catalog.has_any_column_privilege('authenticated',
                        pg_catalog.format('%I.%I', policy_row.schemaname, policy_row.tablename), operation)
                    ELSE false END) THEN
                EXECUTE pg_catalog.format('CREATE POLICY %I ON %I.%I FOR %s TO authenticated%s%s',
                    policy_row.policyname || '_' || lower(operation),
                    policy_row.schemaname, policy_row.tablename, operation,
                    CASE WHEN operation = 'INSERT' THEN '' ELSE ' USING (' || owner_expression || ')' END,
                    CASE WHEN operation = 'DELETE' THEN '' ELSE ' WITH CHECK (' || owner_expression || ')' END);
            END IF;
        END LOOP;
    END LOOP;
END;
$migration$;

COMMIT;

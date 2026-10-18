-- EP-MET preflight. Run against the intended Supabase project in a read-only
-- session. This script reports legacy state; it never repairs or rewrites it.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '60s';

-- 1) Confirm the expected relations and Supabase migration history are present.
SELECT
    to_regclass('public.goals') AS goals_relation,
    to_regclass('public.goal_events') AS goal_events_relation,
    to_regclass('public.transactions') AS transactions_relation,
    to_regclass('public.accounts') AS accounts_relation,
    to_regclass('internal.ledger_entries') AS ledger_relation,
    to_regclass('supabase_migrations.schema_migrations') AS migration_history_relation;

SELECT version, name
FROM supabase_migrations.schema_migrations
WHERE version >= '20260920000000'
ORDER BY version;

-- 2) Table security, policies and direct grants for the canonical records.
SELECT
    n.nspname AS schema_name,
    c.relname AS table_name,
    c.relrowsecurity AS rls_enabled,
    c.relforcerowsecurity AS rls_forced,
    COALESCE(
        (SELECT jsonb_agg(jsonb_build_object(
            'policy', p.policyname,
            'roles', p.roles,
            'command', p.cmd,
            'using', p.qual,
            'with_check', p.with_check
        ) ORDER BY p.policyname)
         FROM pg_policies p
         WHERE p.schemaname = n.nspname AND p.tablename = c.relname),
        '[]'::jsonb
    ) AS policies
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE (n.nspname, c.relname) IN (
    ('public', 'goals'),
    ('public', 'goal_events'),
    ('public', 'transactions'),
    ('public', 'accounts'),
    ('internal', 'ledger_entries')
)
ORDER BY n.nspname, c.relname;

SELECT table_schema, table_name, grantee, privilege_type, is_grantable
FROM information_schema.table_privileges
WHERE (table_schema, table_name) IN (
    ('public', 'goals'),
    ('public', 'goal_events'),
    ('public', 'transactions'),
    ('public', 'accounts'),
    ('internal', 'ledger_entries')
)
ORDER BY table_schema, table_name, grantee, privilege_type;

-- 3) Legacy goal RPC signatures and EXECUTE grants.
SELECT
    p.oid::regprocedure AS function_signature,
    pg_get_userbyid(p.proowner) AS owner,
    p.prosecdef AS security_definer,
    p.proconfig AS function_settings,
    has_function_privilege('anon', p.oid, 'EXECUTE') AS anon_can_execute,
    has_function_privilege('authenticated', p.oid, 'EXECUTE') AS authenticated_can_execute,
    has_function_privilege('service_role', p.oid, 'EXECUTE') AS service_role_can_execute
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND p.proname IN ('allocate_goal_funds', 'release_goal_funds')
ORDER BY p.proname, p.oid::regprocedure::text;

-- 4) Events by legacy type and transaction linkage.
SELECT
    e.event_type,
    CASE WHEN e.transaction_id IS NULL THEN 'NO_TRANSACTION' ELSE 'HAS_TRANSACTION' END AS transaction_link,
    COUNT(*) AS event_count,
    COUNT(*) FILTER (WHERE g.id IS NULL) AS missing_goal_count,
    COUNT(*) FILTER (WHERE g.user_id IS DISTINCT FROM e.user_id) AS owner_mismatch_count,
    COUNT(*) FILTER (WHERE t.id IS NULL AND e.transaction_id IS NOT NULL) AS missing_transaction_count
FROM public.goal_events e
LEFT JOIN public.goals g ON g.id = e.goal_id
LEFT JOIN public.transactions t ON t.id = e.transaction_id
GROUP BY e.event_type, transaction_link
ORDER BY e.event_type, transaction_link;

-- 5) Event-to-transaction-to-ledger detail. Account IDs and ledger facts are
-- emitted for review; backing_account_id is read dynamically so this query
-- works before or after an additive column is introduced.
WITH ledger_by_transaction AS (
    SELECT
        le.transaction_id,
        COUNT(*) AS ledger_entry_count,
        ARRAY_AGG(DISTINCT le.user_id::text ORDER BY le.user_id::text) AS ledger_user_ids,
        ARRAY_AGG(DISTINCT le.account_id::text ORDER BY le.account_id::text) AS ledger_account_ids,
        ARRAY_AGG(DISTINCT le.currency_code::text ORDER BY le.currency_code::text) AS ledger_currencies,
        ARRAY_AGG(DISTINCT a.user_id::text ORDER BY a.user_id::text) AS ledger_account_owner_ids,
        ARRAY_AGG(DISTINCT a.currency_code::text ORDER BY a.currency_code::text) AS ledger_account_currencies,
        SUM(le.signed_amount_minor) AS ledger_net_minor
    FROM internal.ledger_entries le
    LEFT JOIN public.accounts a ON a.id = le.account_id
    GROUP BY le.transaction_id
)
SELECT
    e.id AS event_id,
    e.user_id AS event_user_id,
    e.goal_id,
    e.event_type,
    e.amount_minor,
    e.transaction_id,
    to_jsonb(e)->>'backing_account_id' AS event_backing_account_id,
    g.user_id AS goal_user_id,
    to_jsonb(g)->>'currency_code' AS goal_currency,
    t.user_id AS transaction_user_id,
    COALESCE(to_jsonb(t)->>'type', to_jsonb(t)->>'transaction_type') AS transaction_type,
    COALESCE(to_jsonb(t)->>'status', to_jsonb(t)->>'state') AS transaction_status,
    COALESCE(
        to_jsonb(t)->>'account_id',
        to_jsonb(t)->>'source_account_id',
        to_jsonb(t)->>'origin_account_id'
    ) AS transaction_account_id,
    ta.user_id AS transaction_account_owner_id,
    ta.currency_code AS transaction_account_currency,
    ba.user_id AS event_backing_account_owner_id,
    ba.currency_code AS event_backing_account_currency,
    l.ledger_entry_count,
    l.ledger_user_ids,
    l.ledger_account_ids,
    l.ledger_currencies,
    l.ledger_account_owner_ids,
    l.ledger_account_currencies,
    l.ledger_net_minor,
    (to_jsonb(e)->>'backing_account_id' IS NULL) AS legacy_unlinked,
    (g.id IS NULL
        OR g.user_id IS DISTINCT FROM e.user_id
        OR (e.transaction_id IS NOT NULL AND t.id IS NULL)
        OR (t.id IS NOT NULL AND t.user_id IS DISTINCT FROM e.user_id)
        OR (e.transaction_id IS NOT NULL AND l.transaction_id IS NULL)
        OR (t.id IS NOT NULL AND g.id IS NOT NULL AND t.currency_code IS DISTINCT FROM g.currency_code)
        OR (t.id IS NOT NULL AND t.account_id IS NOT NULL AND (
            ta.id IS NULL
            OR ta.user_id IS DISTINCT FROM e.user_id
            OR ta.currency_code IS DISTINCT FROM t.currency_code
        ))
        OR (t.id IS NOT NULL AND e.event_type = 'ALLOCATION' AND (
            e.amount_minor <= 0
            OR e.amount_minor IS DISTINCT FROM t.amount_minor
            OR t.transaction_type::text IS DISTINCT FROM 'EXPENSE'
            OR t.operation_kind::text IS DISTINCT FROM 'GOAL_FUNDING'
        ))
        OR (t.id IS NOT NULL AND e.event_type = 'RELEASE' AND (
            e.amount_minor >= 0
            OR e.amount_minor IS DISTINCT FROM -t.amount_minor
            OR t.transaction_type::text IS DISTINCT FROM 'INCOME'
            OR t.operation_kind::text IS DISTINCT FROM 'GOAL_FUNDING'
        ))
        OR (t.id IS NOT NULL AND l.transaction_id IS NOT NULL AND (
            l.ledger_user_ids IS DISTINCT FROM ARRAY[e.user_id::text]
            OR l.ledger_account_owner_ids IS DISTINCT FROM ARRAY[e.user_id::text]
            OR NOT (t.currency_code::text = ANY(l.ledger_currencies))
            OR NOT (t.currency_code::text = ANY(l.ledger_account_currencies))
            OR (
                t.transaction_type::text IN ('EXPENSE', 'INCOME')
                AND t.account_id IS NOT NULL
                AND NOT (t.account_id::text = ANY(l.ledger_account_ids))
            )
            OR (t.transaction_type::text = 'EXPENSE' AND l.ledger_net_minor IS DISTINCT FROM -t.amount_minor)
            OR (t.transaction_type::text = 'INCOME' AND l.ledger_net_minor IS DISTINCT FROM t.amount_minor)
        ))
        OR (to_jsonb(e)->>'backing_account_id' IS NOT NULL AND (
            ba.id IS NULL
            OR ba.user_id IS DISTINCT FROM e.user_id
            OR (g.id IS NOT NULL AND ba.currency_code IS DISTINCT FROM g.currency_code)
        ))
    ) AS requires_manual_review
FROM public.goal_events e
LEFT JOIN public.goals g ON g.id = e.goal_id
LEFT JOIN public.transactions t ON t.id = e.transaction_id
LEFT JOIN public.accounts ta ON ta.id = t.account_id
LEFT JOIN public.accounts ba ON ba.id = NULLIF(to_jsonb(e)->>'backing_account_id', '')::uuid
LEFT JOIN ledger_by_transaction l ON l.transaction_id = e.transaction_id
ORDER BY e.created_at, e.id;

-- 6) Discover legacy goal-related outbox, receipt and sync tables without
-- assuming their deployed names or exposing their payloads.
SELECT
    table_schema,
    table_name,
    column_name,
    data_type
FROM information_schema.columns
WHERE table_schema IN ('public', 'internal')
  AND (
      table_name ILIKE '%goal%'
      OR table_name ILIKE '%outbox%'
      OR table_name ILIKE '%receipt%'
      OR table_name ILIKE '%sync%'
  )
ORDER BY table_schema, table_name, ordinal_position;

COMMIT;

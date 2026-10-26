-- Debt and explicit goal transfers historically emitted raw transaction rows.
-- Project current owner-scoped financial facts without changing historical feed rows.
BEGIN;
CREATE OR REPLACE FUNCTION internal.planning_transaction_sync_projection_v1(t public.transactions)
RETURNS jsonb LANGUAGE sql STABLE SET search_path = '' AS $$
  SELECT pg_catalog.jsonb_build_object(
    'id',t.id,'type',t.transaction_type,'operation_kind',t.operation_kind,
    'amount_minor',t.amount_minor,'currency_code',t.currency_code,
    'source_account_id',CASE WHEN t.transaction_type::text IN ('EXPENSE','TRANSFER') THEN t.account_id ELSE NULL END,
    'destination_account_id',CASE WHEN t.transaction_type::text='INCOME' THEN t.account_id
      WHEN t.transaction_type::text='TRANSFER' THEN (SELECT l.account_id FROM internal.ledger_entries l
        WHERE l.user_id=t.user_id AND l.transaction_id=t.id AND l.entry_role::text='DESTINATION' ORDER BY l.created_at,l.id LIMIT 1)
      ELSE NULL END,
    'category_id',t.category_id,'merchant_id',t.merchant_service_id,'occurred_at',t.occurred_at,'note',t.notes,
    'status',CASE WHEN t.deleted_at IS NOT NULL OR t.status::text='VOIDED' THEN 'VOIDED'
      WHEN t.status::text='CONFIRMED' THEN 'ACTIVE' ELSE t.status::text END,
    'ledger_entries',CASE WHEN t.deleted_at IS NOT NULL OR t.status::text='VOIDED' THEN '[]'::jsonb
      ELSE COALESCE((SELECT pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
        'id',l.id,'account_id',l.account_id,'role',l.entry_role,'signed_amount_minor',l.signed_amount_minor,
        'currency_code',l.currency_code,'created_at',l.created_at) ORDER BY l.created_at,l.id)
        FROM internal.ledger_entries l WHERE l.user_id=t.user_id AND l.transaction_id=t.id),'[]'::jsonb) END);
$$;
REVOKE ALL ON FUNCTION internal.planning_transaction_sync_projection_v1(public.transactions) FROM PUBLIC,anon,authenticated,service_role;

DO $$
DECLARE definition text; old_revision text; old_branch text; condition text; executor_name text;
BEGIN
  SELECT pg_catalog.pg_get_userbyid(proowner) INTO executor_name FROM pg_catalog.pg_proc
    WHERE oid='public.pull_financial_changes_v1(integer,bigint,integer)'::regprocedure;
  EXECUTE pg_catalog.format('ALTER FUNCTION internal.planning_transaction_sync_projection_v1(public.transactions) OWNER TO %I',executor_name);
  definition := pg_catalog.pg_get_functiondef('public.pull_financial_changes_v1(integer,bigint,integer)'::regprocedure);
  IF pg_catalog.strpos(definition,'internal.planning_transaction_sync_projection_v1(t)') > 0 THEN RETURN; END IF;
  old_revision := 'CASE WHEN sc.entity_type = ''CARD'' AND c.id IS NOT NULL THEN c.revision ELSE sc.revision END AS revision';
  old_branch := 'WHEN sc.entity_type = ''TRANSACTION'' AND sc.operation = ''UPSERT''';
  condition := 'sc.entity_type = ''TRANSACTION'' AND sc.operation = ''UPSERT'' AND t.id IS NOT NULL AND t.operation_kind::text IN (''DEBT_DISBURSEMENT'',''DEBT_PAYMENT'',''DEBT_AMORTIZATION'',''GOAL_FUNDING'')';
  IF pg_catalog.strpos(definition,old_revision)=0 OR pg_catalog.strpos(definition,old_branch)=0 THEN
    RAISE EXCEPTION 'PLANNING_PULL_IMPLEMENTATION_CHANGED: review projection before applying';
  END IF;
  definition := pg_catalog.replace(definition,old_branch,
    'WHEN ' || condition || ' THEN internal.planning_transaction_sync_projection_v1(t) ' || old_branch);
  definition := pg_catalog.replace(definition,old_revision,
    'CASE WHEN sc.entity_type = ''CARD'' AND c.id IS NOT NULL THEN c.revision WHEN ' || condition || ' THEN t.revision ELSE sc.revision END AS revision');
  EXECUTE definition;
END;
$$;
REVOKE ALL ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) TO authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;

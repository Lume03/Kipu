-- Match the Android single-account INCOME contract; ledger facts remain unchanged.
BEGIN;
CREATE OR REPLACE FUNCTION internal.planning_transaction_sync_projection_v1(t public.transactions)
RETURNS jsonb LANGUAGE sql STABLE SET search_path = '' AS $$
  SELECT pg_catalog.jsonb_build_object(
    'id',t.id,'type',t.transaction_type,'operation_kind',t.operation_kind,
    'amount_minor',t.amount_minor,'currency_code',t.currency_code,
    'source_account_id',t.account_id,
    'destination_account_id',CASE WHEN t.transaction_type::text='TRANSFER' THEN (SELECT l.account_id FROM internal.ledger_entries l
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

NOTIFY pgrst, 'reload schema';
COMMIT;

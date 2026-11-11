-- Normalize feed snapshots, never mutate owner data or historical feed rows.
BEGIN;

CREATE OR REPLACE FUNCTION internal.transaction_sync_projection_v1(t public.transactions)
RETURNS jsonb LANGUAGE sql STABLE SET search_path = '' AS $$
    SELECT CASE WHEN t.operation_kind::text IN ('CARD_PURCHASE','CARD_PAYMENT')
        THEN internal.credit_transaction_sync_projection_v1(t)
        ELSE internal.planning_transaction_sync_projection_v1(t) || jsonb_build_object(
            -- Android uses source_account_id for both single-account movement types.
            'source_account_id', t.account_id,
            'destination_account_id', CASE WHEN t.transaction_type::text = 'TRANSFER' THEN
                COALESCE(
                    (SELECT r.new_payload->>'destination_account_id'
                     FROM public.transaction_revisions r
                     WHERE r.user_id=t.user_id AND r.transaction_id=t.id AND r.revision_number=t.revision),
                    (SELECT l.account_id::text FROM internal.ledger_entries l
                     WHERE l.user_id=t.user_id AND l.transaction_id=t.id
                     GROUP BY l.account_id HAVING sum(l.signed_amount_minor)>0
                     ORDER BY l.account_id LIMIT 1)) ELSE NULL END,
            'merchant_provisional_text', t.merchant_provisional_text,
            'payment_channel', to_jsonb(t)->>'payment_channel',
            -- Includes original, reversal and replacement rows: their sum is the
            -- current authoritative effect, including zero after a canonical void.
            'ledger_entries', COALESCE((
                SELECT jsonb_agg(jsonb_build_object(
                    'id',l.id,'account_id',l.account_id,'role',l.entry_role,
                    'signed_amount_minor',l.signed_amount_minor,'currency_code',l.currency_code,
                    'created_at',l.created_at) ORDER BY l.created_at,l.id)
                FROM internal.ledger_entries l
                WHERE l.user_id=t.user_id AND l.transaction_id=t.id), '[]'::jsonb)) END
$$;
REVOKE ALL ON FUNCTION internal.transaction_sync_projection_v1(public.transactions)
    FROM PUBLIC,anon,authenticated,service_role;
-- Keep the private helper callable by the existing SECURITY DEFINER executor,
-- including installations where that owner differs from the migration role.
DO $$
DECLARE executor_name text;
BEGIN
    SELECT pg_catalog.pg_get_userbyid(proowner) INTO executor_name FROM pg_catalog.pg_proc
    WHERE oid='public.pull_financial_changes_v1(integer,bigint,integer)'::regprocedure;
    EXECUTE pg_catalog.format('ALTER FUNCTION internal.transaction_sync_projection_v1(public.transactions) OWNER TO %I',executor_name);
END $$;

-- Same wrapper/delegate as 20261031163043: only the projection predicate
-- broadens. Delegate retains owner scope, validation, page size and cursor.
CREATE OR REPLACE FUNCTION public.pull_financial_changes_v1(
    contract_version integer DEFAULT 1, after_sequence bigint DEFAULT 0, "limit" integer DEFAULT 100
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner uuid:=auth.uid(); v_result jsonb; v_changes jsonb;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_result:=internal.pull_financial_changes_pre_credit_terms_v1(contract_version,after_sequence,"limit");
    SELECT coalesce(jsonb_agg(
        CASE WHEN item->>'entity_type'='TRANSACTION' AND item->>'operation'='UPSERT'
             AND t.id IS NOT NULL
        THEN jsonb_set(jsonb_set(item,'{payload}',internal.transaction_sync_projection_v1(t)),
            '{revision}',to_jsonb(t.revision)) ELSE item END ORDER BY ordinal), '[]'::jsonb)
    INTO v_changes
    FROM jsonb_array_elements(coalesce(v_result->'changes','[]'::jsonb)) WITH ORDINALITY changes(item,ordinal)
    LEFT JOIN public.transactions t ON t.user_id=v_owner AND t.id=(item->>'entity_id')::uuid;
    RETURN v_result || jsonb_build_object('changes',v_changes);
END $$;
REVOKE ALL ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) FROM PUBLIC,anon,service_role;
GRANT EXECUTE ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;

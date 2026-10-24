BEGIN;

-- Reimbursements may only create their canonical source/target link through
-- the specialized RPC. Authenticated clients retain owner-scoped reads.
DROP POLICY IF EXISTS transaction_links_own ON public.transaction_links;
DROP POLICY IF EXISTS transaction_links_select_own ON public.transaction_links;
CREATE POLICY transaction_links_select_own ON public.transaction_links
    FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) = user_id);
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
    ON public.transaction_links FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.transaction_links TO authenticated;

CREATE UNIQUE INDEX IF NOT EXISTS transaction_links_one_refund_target
    ON public.transaction_links(user_id, target_transaction_id)
    WHERE link_type = 'REFUND';

CREATE OR REPLACE FUNCTION internal.refund_transaction_hash_v1(p_command jsonb)
RETURNS text
LANGUAGE plpgsql
IMMUTABLE
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    v_purchase_id text;
    v_refund_id text;
    v_amount bigint;
    v_currency text;
    v_occurred_at timestamptz;
    v_epoch_millis bigint;
    v_canonical text;
BEGIN
    v_purchase_id := lower((p_command->>'purchase_transaction_id')::uuid::text);
    v_refund_id := lower((p_command#>>'{refund_transaction,id}')::uuid::text);
    v_amount := (p_command#>>'{refund_transaction,amount_minor}')::bigint;
    v_currency := upper(p_command#>>'{refund_transaction,currency_code}');
    v_occurred_at := (p_command#>>'{refund_transaction,occurred_at}')::timestamptz;
    v_epoch_millis := (extract(epoch FROM v_occurred_at) * 1000)::bigint;
    IF extract(epoch FROM v_occurred_at) * 1000 <> v_epoch_millis THEN
        RETURN NULL;
    END IF;
    v_canonical := 'refund-v1' || chr(10) || v_purchase_id || chr(10) || v_refund_id || chr(10) ||
        v_amount::text || chr(10) || v_currency || chr(10) || v_epoch_millis::text;
    RETURN pg_catalog.encode(
        extensions.digest(pg_catalog.convert_to(v_canonical, 'UTF8'), 'sha256'),
        'hex'
    );
EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow OR numeric_value_out_of_range THEN
    RETURN NULL;
END;
$$;
REVOKE ALL ON FUNCTION internal.refund_transaction_hash_v1(jsonb) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.refund_transaction_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_owner uuid := auth.uid();
    v_refund jsonb := p_command->'refund_transaction';
    v_purchase_id uuid;
    v_refund_id uuid;
    v_idempotency_key text;
    v_amount bigint;
    v_currency char(3);
    v_occurred_at timestamptz;
    v_hash text;
    v_prior record;
    v_purchase public.transactions%ROWTYPE;
    v_account public.accounts%ROWTYPE;
    v_card public.cards%ROWTYPE;
    v_refunded numeric;
    v_remaining bigint;
    -- %TYPE: el enum vive en internal (baseline del repo) o en public (proyecto remoto).
    v_ledger_role internal.ledger_entries.entry_role%TYPE;
    v_receipt_id uuid;
    v_response jsonb;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF v_owner IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED','retryable',false));
    END IF;
    IF jsonb_typeof(p_command) IS DISTINCT FROM 'object'
       OR p_command->>'contract_version' IS DISTINCT FROM '1'
       OR (p_command - ARRAY['contract_version','idempotency_key','request_hash','purchase_transaction_id','refund_transaction']) <> '{}'::jsonb
       OR jsonb_typeof(v_refund) IS DISTINCT FROM 'object'
       OR (v_refund - ARRAY['id','amount_minor','currency_code','occurred_at']) <> '{}'::jsonb
       OR jsonb_typeof(p_command->'idempotency_key') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_command->'request_hash') IS DISTINCT FROM 'string'
       OR (p_command->>'request_hash') !~ '^[0-9a-f]{64}$'
       OR jsonb_typeof(p_command->'purchase_transaction_id') IS DISTINCT FROM 'string'
       OR jsonb_typeof(v_refund->'id') IS DISTINCT FROM 'string'
       OR jsonb_typeof(v_refund->'amount_minor') IS DISTINCT FROM 'number'
       OR (v_refund->>'amount_minor') !~ '^[1-9][0-9]*$'
       OR jsonb_typeof(v_refund->'currency_code') IS DISTINCT FROM 'string'
       OR jsonb_typeof(v_refund->'occurred_at') IS DISTINCT FROM 'string'
       OR (v_refund->>'occurred_at') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$'
       OR (p_command->>'idempotency_key') !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
       OR (p_command->>'purchase_transaction_id') !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
       OR (v_refund->>'id') !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
       OR (v_refund->>'currency_code') !~ '^[A-Za-z]{3}$' THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
    END IF;
    BEGIN
        v_idempotency_key := (p_command->>'idempotency_key')::uuid::text;
        v_purchase_id := (p_command->>'purchase_transaction_id')::uuid;
        v_refund_id := (v_refund->>'id')::uuid;
        v_amount := (v_refund->>'amount_minor')::bigint;
        v_currency := upper(v_refund->>'currency_code')::char(3);
        v_occurred_at := (v_refund->>'occurred_at')::timestamptz;
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow OR numeric_value_out_of_range THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
    END;
    IF extract(epoch FROM v_occurred_at) * 1000 <> trunc(extract(epoch FROM v_occurred_at) * 1000) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_TIMESTAMP_PRECISION','retryable',false));
    END IF;
    IF v_amount <= 0 OR v_currency IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.currencies c WHERE c.code = v_currency
    ) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_AMOUNT_OR_CURRENCY','retryable',false));
    END IF;
    v_hash := internal.refund_transaction_hash_v1(p_command);
    IF v_hash IS NULL OR v_hash IS DISTINCT FROM p_command->>'request_hash' THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REQUEST_HASH','retryable',false));
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner::text || ':' || v_idempotency_key, 0));
    SELECT command_type, contract_version, request_hash, response_payload
      INTO v_prior
      FROM internal.command_receipts
     WHERE user_id = v_owner AND idempotency_key = v_idempotency_key;
    IF FOUND THEN
        IF v_prior.command_type = 'REFUND_TRANSACTION_V1' AND v_prior.contract_version = 1
           AND v_prior.request_hash = v_hash THEN
            RETURN pg_catalog.jsonb_set(v_prior.response_payload, '{status}', '"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','retryable',false));
    END IF;

    SELECT * INTO v_purchase
      FROM public.transactions
     WHERE id = v_purchase_id AND user_id = v_owner
     FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','PURCHASE_NOT_FOUND','retryable',false));
    END IF;
    IF v_purchase.transaction_type <> 'EXPENSE'
       OR v_purchase.status NOT IN ('CONFIRMED','REVISED')
       OR v_purchase.deleted_at IS NOT NULL
       OR v_purchase.refund_of_transaction_id IS NOT NULL
       OR v_purchase.installment_count <> 1
       OR v_purchase.operation_kind::text NOT IN ('STANDARD','CARD_PURCHASE')
       OR v_purchase.currency_code <> v_currency
       OR EXISTS (
            SELECT 1 FROM public.credit_payment_allocations a
             WHERE a.user_id = v_owner AND a.payment_transaction_id = v_purchase.id
       )
       OR EXISTS (
            SELECT 1 FROM public.debt_events e
             WHERE e.user_id = v_owner AND e.transaction_id = v_purchase.id
       ) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','PURCHASE_NOT_ELIGIBLE','retryable',false));
    END IF;
    IF v_purchase.operation_kind::text = 'CARD_PURCHASE' THEN
        SELECT * INTO v_card FROM public.cards c
         WHERE c.id = v_purchase.card_id AND c.user_id = v_owner
           AND c.account_id = v_purchase.account_id AND c.is_credit
           AND c.deleted_at IS NULL
         FOR UPDATE;
        IF NOT FOUND THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','PURCHASE_NOT_ELIGIBLE','retryable',false));
        END IF;
        v_ledger_role := 'LIABILITY';
    ELSE
        IF v_purchase.card_id IS NOT NULL THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','PURCHASE_NOT_ELIGIBLE','retryable',false));
        END IF;
        SELECT * INTO v_account FROM public.accounts a
         WHERE a.id = v_purchase.account_id AND a.user_id = v_owner
           AND a.currency_code = v_currency AND a.account_type <> 'CREDIT_LIABILITY'
         FOR UPDATE;
        IF NOT FOUND THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','PURCHASE_NOT_ELIGIBLE','retryable',false));
        END IF;
        v_ledger_role := 'PRIMARY';
    END IF;
    IF EXISTS (SELECT 1 FROM public.transactions t WHERE t.id = v_refund_id) THEN
        RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','TRANSACTION_ID_ALREADY_EXISTS','retryable',false));
    END IF;

    SELECT COALESCE(sum(refund.amount_minor), 0)::numeric INTO v_refunded
      FROM public.transaction_links link
      JOIN public.transactions refund ON refund.id = link.target_transaction_id AND refund.user_id = link.user_id
     WHERE link.user_id = v_owner AND link.source_transaction_id = v_purchase.id
       AND link.link_type = 'REFUND'
       AND refund.transaction_type = 'INCOME' AND refund.operation_kind::text = 'REFUND'
       AND refund.status IN ('CONFIRMED','REVISED') AND refund.deleted_at IS NULL
       AND refund.refund_of_transaction_id IS NULL;
    v_remaining := greatest(v_purchase.amount_minor::numeric - v_refunded, 0)::bigint;
    IF v_remaining <= 0 OR v_amount > v_remaining THEN
        RETURN jsonb_build_object(
            'status','REJECTED',
            'error',jsonb_build_object('code','REFUND_EXCEEDS_REMAINDER','retryable',false,'remaining_minor',v_remaining)
        );
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,category_id,merchant_service_id,merchant_provisional_text,
        transaction_type,operation_kind,amount_minor,currency_code,occurred_at,installment_count,
        notes,status,revision
    ) VALUES (
        v_refund_id,v_owner,v_purchase.account_id,v_purchase.card_id,NULL,
        v_purchase.merchant_service_id,v_purchase.merchant_provisional_text,
        'INCOME','REFUND',v_amount,v_purchase.currency_code,v_occurred_at,1,
        NULL,'CONFIRMED',1
    );
    INSERT INTO public.transaction_links(user_id,source_transaction_id,target_transaction_id,link_type,metadata)
    VALUES(v_owner,v_purchase.id,v_refund_id,'REFUND','{}'::jsonb);
    INSERT INTO internal.ledger_entries(
        transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
    ) VALUES(v_refund_id,v_owner,v_purchase.account_id,v_amount,v_purchase.currency_code,v_ledger_role,v_occurred_at);

    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(
        v_owner,'TRANSACTION',v_refund_id,1,'UPSERT',
        jsonb_build_object(
            'id',v_refund_id,'type','INCOME','operation_kind','REFUND','amount_minor',v_amount,
            'currency_code',v_purchase.currency_code,
            'source_account_id',CASE WHEN v_purchase.card_id IS NULL THEN v_purchase.account_id ELSE NULL END,
            'destination_account_id',NULL,'category_id',v_purchase.category_id,
            'merchant_id',v_purchase.merchant_service_id,
            'merchant_provisional_text',v_purchase.merchant_provisional_text,
            'card_id',v_purchase.card_id,'installment_count',NULL,
            'occurred_at',v_occurred_at,'note',NULL,'status','ACTIVE',
            'ledger_entries',jsonb_build_array(jsonb_build_object(
                'account_id',v_purchase.account_id,'role',v_ledger_role,
                'signed_amount_minor',v_amount,'currency_code',v_purchase.currency_code,
                'created_at',v_now
            ))
        )
    );
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status','APPLIED','transaction_id',v_refund_id,'receipt_id',v_receipt_id,
        'server_updated_at',v_now,'error',NULL
    );
    INSERT INTO internal.command_receipts(
        id,user_id,idempotency_key,command_type,request_hash,response_payload,status,contract_version,command_payload
    ) VALUES(
        v_receipt_id,v_owner,v_idempotency_key,'REFUND_TRANSACTION_V1',v_hash,v_response,'APPLIED',1,p_command
    );
    RETURN v_response;
EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow OR numeric_value_out_of_range THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
END;
$$;

REVOKE ALL ON FUNCTION public.refund_transaction_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.refund_transaction_v1(jsonb) TO authenticated;

COMMIT;

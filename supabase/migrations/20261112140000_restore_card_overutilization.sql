-- P10 permits purchases exceeding the credit limit.
CREATE OR REPLACE FUNCTION public.register_transaction_v1_pre_liability_s3(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_type text;
    v_operation_kind text;
    v_idempotency_key text;
    v_request_hash text;
    v_client_hash text;
    v_tx_id uuid;
    v_card_id uuid;
    v_category_id uuid;
    v_merchant_id uuid;
    v_provisional_merchant text;
    v_amount bigint;
    v_currency char(3);
    v_installments integer;
    v_occurred_at timestamptz;
    v_interest_mode text;
    v_purchase_type text; v_financing text; v_tea integer; v_rate_source text; v_rounding text;
    v_processed_at timestamptz; v_closing_date date; v_schedule record; v_financed_total bigint;
    v_card record;
    v_existing record;
    v_receipt_id uuid;
    v_response jsonb;
    v_before_debt bigint;
    v_after_debt bigint;
    v_credit_limit bigint;
    v_base bigint;
    v_remainder integer;
    v_principal bigint;
    v_due_date date;
    v_installment_ids jsonb := '[]'::jsonb;
    v_now timestamptz := clock_timestamp();
    v_installment_number integer;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','UNAUTHORIZED','field',NULL,'retryable',false));
    END IF;
    IF v_tx IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field','transaction','retryable',false));
    END IF;
    v_idempotency_key := NULLIF(p_command->>'idempotency_key','');
    v_client_hash := NULLIF(p_command->>'request_hash','');
    v_type := upper(COALESCE(v_tx->>'type',''));
    v_operation_kind := upper(COALESCE(v_tx->>'operation_kind','STANDARD'));

    -- Ordinary transaction behavior remains delegated to the last checked RPC.
    IF v_operation_kind = 'CARD_PAYMENT' THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','USE_ALLOCATE_CREDIT_PAYMENT','field','operation_kind','retryable',false));
    END IF;
    IF NOT (v_type = 'EXPENSE' AND v_operation_kind = 'CARD_PURCHASE') THEN
        RETURN internal.register_transaction_v1_pre_credit_s3(p_command);
    END IF;

    IF COALESCE((p_command->>'contract_version')::integer,1) <> 1
       OR v_idempotency_key IS NULL OR v_client_hash IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field','contract_version/idempotency_key/request_hash','retryable',false));
    END IF;
    IF NULLIF(v_tx->>'source_account_id','') IS NOT NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CARD_PURCHASE_CANNOT_DEBIT_CASH','field','source_account_id','retryable',false));
    END IF;
    BEGIN
        v_tx_id := (v_tx->>'id')::uuid;
        v_card_id := (v_tx->>'card_id')::uuid;
        v_category_id := NULLIF(v_tx->>'category_id','')::uuid;
        v_merchant_id := (v_tx->>'merchant_id')::uuid;
        v_amount := (v_tx->>'amount_minor')::bigint;
        v_currency := (v_tx->>'currency_code')::char(3);
        v_installments := COALESCE((v_tx->>'installment_count')::integer,1);
        v_occurred_at := COALESCE((v_tx->>'occurred_at')::timestamptz,v_now);
        v_interest_mode := upper(COALESCE(v_tx->>'interest_mode','NONE'));
        v_purchase_type:=coalesce(v_tx->>'purchase_type',CASE WHEN v_installments=1 THEN 'DIRECT' ELSE 'INSTALLMENTS' END);
        v_financing:=coalesce(v_tx->>'financing','INTEREST_FREE');
        v_tea:=(v_tx->>'tea_bps')::integer; v_rate_source:=v_tx->>'rate_source';
        v_rounding:=coalesce(v_tx->>'rounding_policy','LAST_INSTALLMENT_RESIDUAL');
        v_processed_at:=(v_tx->>'processed_at')::timestamptz;
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND_FIELD','field',NULL,'retryable',false));
    END;
    v_provisional_merchant := NULLIF(trim(v_tx->>'merchant_provisional_text'),'');
    IF v_tx_id IS NULL OR v_card_id IS NULL OR v_amount IS NULL OR v_currency IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','REQUIRED_FIELD_MISSING','field',NULL,'retryable',false));
    END IF;
    IF v_amount < 1 OR v_amount > 10000000 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_AMOUNT','field','amount_minor','retryable',false));
    END IF;
    IF v_installments < 1 OR v_installments > 36 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_INSTALLMENT_COUNT','field','installment_count','retryable',false));
    END IF;
    IF length(trim(v_currency::text))<>3 OR NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code=v_currency) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CURRENCY','field','currency_code','retryable',false));
    END IF;
    IF v_interest_mode NOT IN ('NONE','INCLUDED','REFERENTIAL') THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_INTEREST_MODE','field','interest_mode','retryable',false));
    END IF;
    IF v_purchase_type NOT IN ('DIRECT','INSTALLMENTS') OR v_financing NOT IN ('WITH_INTEREST','INTEREST_FREE')
       OR v_rounding NOT IN ('LAST_INSTALLMENT_RESIDUAL','FIRST_INSTALLMENT_RESIDUAL')
       OR (v_purchase_type='DIRECT' AND (v_installments<>1 OR v_financing<>'INTEREST_FREE'))
       OR (v_financing='INTEREST_FREE' AND (v_tea IS NOT NULL OR v_rate_source IS NOT NULL))
       OR (v_financing='WITH_INTEREST' AND (v_tea IS NULL OR v_tea NOT BETWEEN 1 AND 100000
           OR v_rate_source IS NULL OR v_rate_source NOT IN ('PERSONAL','REFERENCE','USER_ENTERED'))) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_PURCHASE_TERMS','retryable',false));
    END IF;
    IF v_merchant_id IS NOT NULL AND v_provisional_merchant IS NOT NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','MERCHANT_CONFLICT','field','merchant_id','retryable',false));
    END IF;
    IF v_category_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.categories c
        WHERE c.id=v_category_id AND c.is_active AND (c.user_id IS NULL OR c.user_id=v_user_id)
          AND (c.parent_id IS NULL OR EXISTS (SELECT 1 FROM public.categories p WHERE p.id=c.parent_id AND p.is_active))
    ) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CATEGORY_UNAVAILABLE','field','category_id','retryable',false));
    END IF;
    IF v_merchant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.merchant_services m WHERE m.id=v_merchant_id AND m.is_active) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','MERCHANT_UNAVAILABLE','field','merchant_id','retryable',false));
    END IF;

    -- Derive the idempotency hash from normalized PostgreSQL JSONB. The client
    -- hash remains an envelope field but is not trusted as the request identity.
    v_request_hash := encode(extensions.digest(convert_to(jsonb_build_object('contract_version',1,'transaction',v_tx)::text,'UTF8'),'sha256'),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key,0));
    SELECT id,request_hash,response_payload INTO v_existing
    FROM internal.command_receipts WHERE user_id=v_user_id AND idempotency_key=v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash=v_request_hash THEN
            PERFORM private.reconcile_credit_purchase_terms(v_user_id,v_tx);
            SELECT response_payload INTO v_existing.response_payload FROM internal.command_receipts WHERE id=v_existing.id;
            RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',NULL,'receipt_id',v_existing.id,'server_updated_at',v_now,
            'error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','field','idempotency_key','retryable',false));
    END IF;
    IF EXISTS (SELECT 1 FROM public.transactions WHERE id=v_tx_id) THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_tx_id,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','TRANSACTION_ID_ALREADY_EXISTS','field','transaction.id','retryable',false));
    END IF;

    SELECT c.id,c.user_id,c.is_credit,c.is_archived,c.credit_limit_minor,c.closing_day,c.due_day,c.closing_day_inclusive,c.cycle_date_basis,c.account_id,c.revision
    INTO v_card
    FROM public.cards c
    WHERE c.id=v_card_id AND c.user_id=v_user_id
    FOR UPDATE;
    IF NOT FOUND OR NOT v_card.is_credit OR v_card.is_archived THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_CARD_UNAVAILABLE','field','card_id','retryable',false));
    END IF;
    IF v_card.closing_day IS NULL OR v_card.due_day IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CARD_CYCLE_NOT_CONFIGURED','field','closing_day/due_day','retryable',false));
    END IF;
    SELECT coalesce(sum(b.outstanding_minor),0)::bigint INTO v_before_debt
    FROM private.credit_installment_balances(v_user_id,v_card_id,v_currency) b;
    SELECT sum(s.principal_minor+s.interest_minor)::bigint INTO v_financed_total
    FROM private.credit_purchase_schedule(v_amount,v_installments,v_tea,v_rounding) s;
    v_credit_limit := v_card.credit_limit_minor;
    -- P10: preserve the full financed purchase even above the limit.

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,category_id,merchant_service_id,merchant_provisional_text,
        transaction_type,operation_kind,amount_minor,currency_code,occurred_at,installment_count,
        interest_mode,notes,status,revision,created_at,updated_at,processed_at
    ) VALUES (
        v_tx_id,v_user_id,NULL,v_card_id,v_category_id,v_merchant_id,v_provisional_merchant,
        'EXPENSE','CARD_PURCHASE',v_amount,v_currency,v_occurred_at,v_installments,
        v_interest_mode,v_tx->>'note','CONFIRMED',1,v_now,v_now,v_processed_at
    );

    FOR v_schedule IN SELECT * FROM private.credit_purchase_schedule(v_amount,v_installments,v_tea,v_rounding) LOOP
        v_installment_number:=v_schedule.installment_number;
        v_principal:=v_schedule.principal_minor;
        v_due_date:=private.credit_cycle_installment_due_date(v_occurred_at,v_card.closing_day,v_card.due_day,
            v_installment_number,v_card.closing_day_inclusive,v_card.cycle_date_basis,v_processed_at);
        v_closing_date:=private.credit_cycle_closing_date(v_occurred_at,v_card.closing_day,v_installment_number,
            v_card.closing_day_inclusive,v_card.cycle_date_basis,v_processed_at);
        INSERT INTO public.credit_installments (
            user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status,revision,created_at,updated_at,
            purchase_type,financing,installments_count,tea_bps,rate_source,rounding_policy,billing_closing_date
        ) VALUES (
            v_user_id,v_tx_id,v_installment_number,v_due_date,v_principal,v_schedule.interest_minor,'PENDING',1,v_now,v_now,
            v_purchase_type,v_financing,v_installments,v_tea,v_rate_source,v_rounding,v_closing_date
        ) RETURNING id INTO STRICT v_receipt_id;
        v_installment_ids:=v_installment_ids || jsonb_build_array(jsonb_build_object(
            'id',v_receipt_id,'installment_number',v_installment_number,'due_date',v_due_date,
            'principal_minor',v_principal,'interest_minor',v_schedule.interest_minor,'billing_closing_date',v_closing_date,
            'purchase_type',v_purchase_type,'financing',v_financing,'installments_count',v_installments,
            'tea_bps',v_tea,'rate_source',v_rate_source,'rounding_policy',v_rounding));
    END LOOP;

    v_after_debt := v_before_debt + v_financed_total;
    PERFORM private.emit_credit_utilization_crossings(v_user_id,v_card_id,v_tx_id,v_before_debt,v_after_debt,v_credit_limit);

    UPDATE public.cards SET revision=revision+1,updated_at=v_now WHERE id=v_card_id AND user_id=v_user_id;
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status','APPLIED','transaction_id',v_tx_id,'receipt_id',v_receipt_id,'server_updated_at',v_now,'error',NULL,
        'installments',v_installment_ids,'amount_minor',v_amount,'remaining_debt_minor',v_after_debt
    );
    INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_receipt_id,v_user_id,v_idempotency_key,'REGISTER_CARD_PURCHASE_V1',v_request_hash,v_response,'APPLIED');
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'TRANSACTION',v_tx_id,1,'UPSERT',jsonb_build_object(
        'id',v_tx_id,'type','EXPENSE','operation_kind','CARD_PURCHASE','card_id',v_card_id,
        'amount_minor',v_amount,'currency_code',v_currency,'category_id',v_category_id,
        'merchant_id',v_merchant_id,'merchant_provisional_text',v_provisional_merchant,
        'occurred_at',v_occurred_at,'installment_count',v_installments,'installments',v_installment_ids,
        'status','ACTIVE','ledger_entries','[]'::jsonb
    ));
    RETURN v_response;
END;
$$;

-- Source: 20261031163043

-- L9c / ACC1-01: confirmed WITH_INTEREST schedules create principal + interest debt.
-- Simulations do not write schedules. Both components consume credit line.
-- Payments: due_date, purchase timestamp, installment number, UUID; principal then interest
-- within each installment. allocated_minor is cumulative over both components; refunds
-- retain the existing FIFO credit policy over the remaining installment total.
BEGIN;

-- Source: 20261101140000
CREATE OR REPLACE FUNCTION private.credit_installment_balances(p_owner uuid,p_card uuid,p_currency bpchar)
RETURNS TABLE(id uuid,transaction_id uuid,installment_number smallint,due_date date,
    principal_minor bigint,interest_minor bigint,paid_minor bigint,refunded_minor bigint,
    outstanding_minor bigint,purchase_occurred_at timestamptz)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    WITH base AS (
        SELECT ci.*, purchase.occurred_at AS purchase_occurred_at,
               coalesce(paid.amount,0)::bigint AS paid_minor, coalesce(refunds.amount,0)::bigint AS purchase_refund,
               greatest(ci.principal_minor+CASE WHEN ci.financing='WITH_INTEREST' THEN ci.interest_minor ELSE 0 END-coalesce(paid.amount,0),0)::bigint AS before_refund
        FROM public.credit_installments ci
        JOIN public.transactions purchase ON purchase.user_id=ci.user_id AND purchase.id=ci.transaction_id
        LEFT JOIN LATERAL (
            SELECT sum(a.allocated_minor) AS amount FROM public.credit_payment_allocations a
            JOIN public.transactions payment ON payment.user_id=a.user_id AND payment.id=a.payment_transaction_id
            WHERE a.user_id=ci.user_id AND a.installment_id=ci.id
              AND payment.status IN ('CONFIRMED','REVISED') AND payment.deleted_at IS NULL
        ) paid ON true
        LEFT JOIN LATERAL (
            SELECT sum(refund.amount_minor) AS amount FROM public.transaction_links links
            JOIN public.transactions refund ON refund.user_id=links.user_id AND refund.id=links.target_transaction_id
            WHERE links.user_id=ci.user_id AND links.source_transaction_id=ci.transaction_id AND links.link_type='REFUND'
              AND refund.transaction_type='INCOME' AND refund.operation_kind::text='REFUND'
              AND refund.status IN ('CONFIRMED','REVISED') AND refund.deleted_at IS NULL
              AND refund.refund_of_transaction_id IS NULL
        ) refunds ON true
        WHERE ci.user_id=p_owner AND (p_card IS NULL OR purchase.card_id=p_card)
          AND (p_currency IS NULL OR purchase.currency_code=p_currency)
          AND ci.deleted_at IS NULL AND ci.status<>'VOIDED'
          AND purchase.status IN ('CONFIRMED','REVISED') AND purchase.deleted_at IS NULL
    ), credited AS (
        SELECT b.*, least(b.before_refund,greatest(b.purchase_refund-coalesce((
            SELECT sum(prior.before_refund) FROM base prior WHERE prior.transaction_id=b.transaction_id
                AND prior.installment_number<b.installment_number
        ),0),0))::bigint AS refunded_minor FROM base b
    )
    SELECT id,transaction_id,installment_number,due_date,principal_minor,interest_minor,paid_minor,refunded_minor,
           (before_refund-refunded_minor)::bigint,purchase_occurred_at FROM credited
$$;

-- Source: 20261112120000
CREATE OR REPLACE FUNCTION public.allocate_credit_payment_v1_pre_liability(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_idempotency_key text := NULLIF(p_command->>'idempotency_key','');
    v_request_hash text;
    v_client_hash text := NULLIF(p_command->>'request_hash','');
    v_tx_id uuid;
    v_card_id uuid;
    v_account_id uuid;
    v_amount bigint;
    v_currency char(3);
    v_occurred_at timestamptz;
    v_card record;
    v_account record;
    v_existing record;
    v_receipt_id uuid;
    v_response jsonb;
    v_debt bigint;
    v_funds bigint;
    v_left bigint;
    v_alloc bigint;
    v_outstanding bigint;
    v_total_allocated bigint := 0;
    v_remaining_debt bigint;
    v_allocations jsonb := '[]'::jsonb;
    v_installment record;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','UNAUTHORIZED','field',NULL,'retryable',false));
    END IF;
    IF COALESCE((p_command->>'contract_version')::integer,1) <> 1 OR v_tx IS NULL OR v_idempotency_key IS NULL OR v_client_hash IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field',NULL,'retryable',false));
    END IF;
    IF upper(COALESCE(v_tx->>'type','')) <> 'TRANSFER' OR upper(COALESCE(v_tx->>'operation_kind','')) <> 'CARD_PAYMENT' THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_PAYMENT_KIND','field','transaction.type/operation_kind','retryable',false));
    END IF;
    BEGIN
        v_tx_id := (v_tx->>'id')::uuid;
        v_card_id := (v_tx->>'card_id')::uuid;
        v_account_id := (v_tx->>'source_account_id')::uuid;
        v_amount := (v_tx->>'amount_minor')::bigint;
        v_currency := (v_tx->>'currency_code')::char(3);
        v_occurred_at := COALESCE((v_tx->>'occurred_at')::timestamptz,v_now);
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND_FIELD','field',NULL,'retryable',false));
    END;
    IF v_tx_id IS NULL OR v_card_id IS NULL OR v_account_id IS NULL OR v_amount IS NULL OR v_currency IS NULL OR v_amount < 1 OR v_amount > 10000000 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_PAYMENT_FIELDS','field',NULL,'retryable',false));
    END IF;
    IF length(trim(v_currency::text))<>3 OR NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code=v_currency) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CURRENCY','field','currency_code','retryable',false));
    END IF;

    -- Use the server's normalized JSONB representation as the authoritative
    -- request identity. A caller cannot force an idempotency hit with a reused hash.
    v_request_hash := encode(extensions.digest(convert_to(jsonb_build_object('contract_version',1,'transaction',v_tx)::text,'UTF8'),'sha256'),'hex');

    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key,0));
    SELECT id,request_hash,response_payload INTO v_existing
    FROM internal.command_receipts WHERE user_id=v_user_id AND idempotency_key=v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash=v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',NULL,'receipt_id',v_existing.id,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','field','idempotency_key','retryable',false));
    END IF;
    IF EXISTS (SELECT 1 FROM public.transactions WHERE id=v_tx_id) THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_tx_id,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','TRANSACTION_ID_ALREADY_EXISTS','field','transaction.id','retryable',false));
    END IF;

    -- Lock in a consistent order: card, then source account, then installments by FIFO.
    SELECT c.id,c.user_id,c.is_credit,c.is_archived,c.account_id,c.revision INTO v_card
    FROM public.cards c WHERE c.id=v_card_id AND c.user_id=v_user_id FOR UPDATE;
    IF NOT FOUND OR NOT v_card.is_credit OR v_card.is_archived THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_CARD_UNAVAILABLE','field','card_id','retryable',false));
    END IF;
    SELECT a.id,a.user_id,a.currency_code,a.account_type,a.is_archived,a.initial_balance_minor_units INTO v_account
    FROM public.accounts a WHERE a.id=v_account_id AND a.user_id=v_user_id FOR UPDATE;
    IF NOT FOUND OR v_account.is_archived OR v_account.account_type='CREDIT_LIABILITY' OR v_account.currency_code<>v_currency THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','SOURCE_ACCOUNT_UNAVAILABLE_OR_CURRENCY_MISMATCH','field','source_account_id/currency_code','retryable',false));
    END IF;
    -- Canonical openings and net original/reversal/replacement entries, once.
    v_funds := internal.goal_confirmed_balance(v_user_id,v_account_id);
    IF v_funds < v_amount THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INSUFFICIENT_FUNDS','field','amount_minor','retryable',false));
    END IF;

    SELECT coalesce(sum(b.outstanding_minor),0)::bigint INTO v_debt
    FROM private.credit_installment_balances(v_user_id,v_card_id,v_currency) b;
    IF v_debt=0 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',0,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','NO_OUTSTANDING_DEBT','field','card_id','retryable',false));
    END IF;
    IF v_amount > v_debt THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',v_debt,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','PAYMENT_EXCEEDS_OUTSTANDING_DEBT','field','amount_minor','retryable',false));
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
        occurred_at,installment_count,interest_mode,notes,status,revision,created_at,updated_at
    ) VALUES (
        v_tx_id,v_user_id,v_account_id,v_card_id,'TRANSFER','CARD_PAYMENT',v_amount,v_currency,
        v_occurred_at,1,'NONE',v_tx->>'note','CONFIRMED',1,v_now,v_now
    );
    INSERT INTO internal.ledger_entries (
        transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
    ) VALUES (v_tx_id,v_user_id,v_account_id,-v_amount,v_currency,'SOURCE',v_occurred_at);

    v_left := v_amount;
    FOR v_installment IN
        SELECT balances.* FROM private.credit_installment_balances(v_user_id,v_card_id,v_currency) balances
        JOIN public.credit_installments ci ON ci.id=balances.id AND ci.user_id=v_user_id
        WHERE balances.outstanding_minor>0
        ORDER BY balances.due_date,balances.purchase_occurred_at,balances.installment_number,balances.id
        FOR UPDATE OF ci
    LOOP
        EXIT WHEN v_left=0;
        v_outstanding := v_installment.outstanding_minor;
        IF v_outstanding=0 THEN CONTINUE; END IF;
        -- Cumulative allocation covers principal first, then contractual interest, per FIFO installment.
        v_alloc := least(v_outstanding,v_left);
        INSERT INTO public.credit_payment_allocations(user_id,payment_transaction_id,installment_id,allocated_minor,created_at)
        VALUES(v_user_id,v_tx_id,v_installment.id,v_alloc,v_now);
        UPDATE public.credit_installments
        SET status=CASE WHEN v_alloc=v_outstanding THEN 'PAID' ELSE 'PARTIAL' END,
            revision=revision+1,updated_at=v_now
        WHERE id=v_installment.id AND user_id=v_user_id;
        v_allocations := v_allocations || jsonb_build_array(jsonb_build_object('installment_id',v_installment.id,'amount_minor',v_alloc));
        v_total_allocated := v_total_allocated+v_alloc;
        v_left := v_left-v_alloc;
    END LOOP;
    IF v_left<>0 OR v_total_allocated<>v_amount THEN
        RAISE EXCEPTION 'Credit payment allocation invariant failed for transaction %',v_tx_id USING ERRCODE='23514';
    END IF;

    UPDATE public.cards SET revision=revision+1,updated_at=v_now WHERE id=v_card_id AND user_id=v_user_id;
    v_remaining_debt := v_debt-v_total_allocated;
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status','APPLIED','transaction_id',v_tx_id,'receipt_id',v_receipt_id,
        'allocated_total_minor',v_total_allocated,'remaining_debt_minor',v_remaining_debt,
        'allocations',v_allocations,'server_updated_at',v_now,'error',NULL
    );
    INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_receipt_id,v_user_id,v_idempotency_key,'ALLOCATE_CREDIT_PAYMENT_V1',v_request_hash,v_response,'APPLIED');
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'TRANSACTION',v_tx_id,1,'UPSERT',jsonb_build_object(
        'id',v_tx_id,'type','TRANSFER','operation_kind','CARD_PAYMENT','card_id',v_card_id,
        'amount_minor',v_amount,'currency_code',v_currency,'source_account_id',v_account_id,
        'occurred_at',v_occurred_at,'status','ACTIVE','allocations',v_allocations,
        'ledger_entries',jsonb_build_array(jsonb_build_object('account_id',v_account_id,'role','SOURCE','signed_amount_minor',-v_amount,'currency_code',v_currency))
    ));
    RETURN v_response;
END;
$$;

-- Source: 20260925022130
CREATE OR REPLACE FUNCTION public.pay_credit_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_result jsonb; v_op_id text; v_hash text; v_user_id uuid := auth.uid(); v_legacy record;
BEGIN
    v_op_id := p_command->>'operation_id';
    v_hash := COALESCE(p_command->>'payload_hash',p_command->>'request_hash');
    IF v_user_id IS NOT NULL AND v_op_id IS NOT NULL AND to_regclass('public.financial_movements') IS NOT NULL THEN
        SELECT cash.id,cash.account_id,liability.card_id,abs(cash.amount_minor_units)::bigint AS amount_minor,cash.currency,cash.effective_at,tx.id AS transaction_id
        INTO v_legacy
        FROM public.financial_movements cash
        JOIN public.financial_movements liability ON liability.user_id=cash.user_id AND liability.operation_id=cash.operation_id
            AND liability.kind='CARD_PAYMENT_LIABILITY' AND liability.status='POSTED'
        LEFT JOIN public.transactions tx ON tx.id=cash.id AND tx.user_id=cash.user_id
        WHERE cash.user_id=v_user_id AND cash.operation_id::text=v_op_id AND cash.kind='CARD_PAYMENT_CASH' AND cash.status='POSTED'
        ORDER BY cash.operation_sequence,cash.id LIMIT 1;
        IF FOUND AND v_legacy.transaction_id IS NOT NULL THEN
            RETURN jsonb_build_object('status','DUPLICATE','transaction_id',v_legacy.transaction_id,'receipt_id',NULL,
                'allocated_total_minor',(SELECT COALESCE(sum(cpa.allocated_minor),0)::bigint FROM public.credit_payment_allocations cpa WHERE cpa.user_id=v_user_id AND cpa.payment_transaction_id=v_legacy.transaction_id),
                'remaining_debt_minor',(SELECT coalesce(sum(b.outstanding_minor),0)::bigint FROM private.credit_installment_balances(v_user_id,v_legacy.card_id,v_legacy.currency) b),
                'allocations',(SELECT COALESCE(jsonb_agg(jsonb_build_object('installment_id',cpa.installment_id,'amount_minor',cpa.allocated_minor) ORDER BY cpa.created_at,cpa.id),'[]'::jsonb) FROM public.credit_payment_allocations cpa WHERE cpa.user_id=v_user_id AND cpa.payment_transaction_id=v_legacy.transaction_id),
                'server_updated_at',v_legacy.effective_at,'error',NULL);
        END IF;
    END IF;
    v_result := public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','compat-pay-credit-card-v1:'||v_op_id,'request_hash',v_hash,
        'transaction',jsonb_build_object(
            'id',COALESCE(p_command->>'transaction_id',p_command->>'operation_id'),
            'type','TRANSFER','operation_kind','CARD_PAYMENT','card_id',p_command->>'card_id',
            'source_account_id',p_command->>'source_account_id',
            'amount_minor',COALESCE(p_command->>'amount_minor_units',p_command->>'amount_minor'),
            'currency_code',COALESCE(p_command->>'currency',p_command->>'currency_code'),
            'occurred_at',COALESCE(p_command->>'effective_at',p_command->>'occurred_at'),
            'note',p_command->>'note'
        )
    ));
    RETURN v_result;
END;
$$;

-- Source: 20261101140000
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
    IF v_credit_limit IS NOT NULL AND v_before_debt + v_financed_total > v_credit_limit THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_LIMIT_EXCEEDED','field','amount_minor','retryable',false));
    END IF;

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
CREATE OR REPLACE FUNCTION internal.credit_transaction_sync_projection_v1(p_transaction public.transactions)
RETURNS jsonb
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
    SELECT jsonb_build_object(
        'id', p_transaction.id,
        'type', p_transaction.transaction_type,
        'operation_kind', p_transaction.operation_kind,
        'amount_minor', p_transaction.amount_minor,
        'currency_code', p_transaction.currency_code,
        'source_account_id', CASE WHEN p_transaction.operation_kind = 'CARD_PAYMENT' THEN p_transaction.account_id ELSE NULL END,
        'destination_account_id', NULL,
        'category_id', p_transaction.category_id,
        'merchant_id', p_transaction.merchant_service_id,
        'merchant_provisional_text', p_transaction.merchant_provisional_text,
        'card_id', p_transaction.card_id,
        'installment_count', p_transaction.installment_count,
        'occurred_at', p_transaction.occurred_at, 'processed_at',p_transaction.processed_at,
        'note', p_transaction.notes,
        'status', CASE WHEN p_transaction.status='VOIDED' THEN 'VOIDED' ELSE 'ACTIVE' END,
        'ledger_entries', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', le.id, 'account_id', le.account_id, 'role', le.entry_role,
                'signed_amount_minor', le.signed_amount_minor,
                'currency_code', le.currency_code, 'created_at', le.created_at
            ) ORDER BY le.created_at, le.id)
            FROM internal.ledger_entries le
            WHERE le.user_id = p_transaction.user_id AND le.transaction_id = p_transaction.id
        ), '[]'::jsonb),
        'installments', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', ci.id, 'installment_number', ci.installment_number,
                'due_date', ci.due_date, 'principal_minor', ci.principal_minor,
                'interest_minor', ci.interest_minor,
                'status', ci.status,
                'outstanding_minor', (SELECT b.outstanding_minor FROM private.credit_installment_balances(p_transaction.user_id,p_transaction.card_id,p_transaction.currency_code) b WHERE b.id=ci.id),
                'billing_closing_date',ci.billing_closing_date,'purchase_type',ci.purchase_type,
                'financing',ci.financing,'installments_count',ci.installments_count,'tea_bps',ci.tea_bps,
                'rate_source',ci.rate_source,'rounding_policy',ci.rounding_policy
            ) ORDER BY ci.installment_number, ci.id)
            FROM public.credit_installments ci
            WHERE ci.user_id = p_transaction.user_id AND ci.transaction_id = p_transaction.id AND ci.deleted_at IS NULL
        ), '[]'::jsonb),
        'allocations', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'installment_id', cpa.installment_id, 'amount_minor', cpa.allocated_minor
            ) ORDER BY cpa.created_at, cpa.id)
            FROM public.credit_payment_allocations cpa
            WHERE cpa.user_id = p_transaction.user_id AND cpa.payment_transaction_id = p_transaction.id
        ), '[]'::jsonb)
    )
$$;

-- Source: 20260920162500 (security_invoker: 20260930025207)
CREATE OR REPLACE VIEW public.v_credit_summary WITH (security_invoker=true) AS
SELECT c.id AS card_id,c.user_id,c.alias AS card_alias,c.network,c.last4,c.credit_limit_minor,
 c.closing_day,c.due_day,c.style_preset_id,
 coalesce(debt.total,0)::numeric AS used_credit_minor,
 CASE WHEN c.credit_limit_minor IS NOT NULL THEN greatest(0,c.credit_limit_minor-coalesce(debt.total,0))::numeric ELSE NULL::numeric END AS available_credit_minor
FROM public.cards c JOIN public.accounts a ON a.id=c.account_id
LEFT JOIN LATERAL (SELECT sum(b.outstanding_minor) AS total FROM (

    WITH base AS (
        SELECT ci.*, purchase.occurred_at AS purchase_occurred_at,
               coalesce(paid.amount,0)::bigint AS paid_minor, coalesce(refunds.amount,0)::bigint AS purchase_refund,
               greatest(ci.principal_minor+CASE WHEN ci.financing='WITH_INTEREST' THEN ci.interest_minor ELSE 0 END-coalesce(paid.amount,0),0)::bigint AS before_refund
        FROM public.credit_installments ci
        JOIN public.transactions purchase ON purchase.user_id=ci.user_id AND purchase.id=ci.transaction_id
        LEFT JOIN LATERAL (
            SELECT sum(a.allocated_minor) AS amount FROM public.credit_payment_allocations a
            JOIN public.transactions payment ON payment.user_id=a.user_id AND payment.id=a.payment_transaction_id
            WHERE a.user_id=ci.user_id AND a.installment_id=ci.id
              AND payment.status IN ('CONFIRMED','REVISED') AND payment.deleted_at IS NULL
        ) paid ON true
        LEFT JOIN LATERAL (
            SELECT sum(refund.amount_minor) AS amount FROM public.transaction_links links
            JOIN public.transactions refund ON refund.user_id=links.user_id AND refund.id=links.target_transaction_id
            WHERE links.user_id=ci.user_id AND links.source_transaction_id=ci.transaction_id AND links.link_type='REFUND'
              AND refund.transaction_type='INCOME' AND refund.operation_kind::text='REFUND'
              AND refund.status IN ('CONFIRMED','REVISED') AND refund.deleted_at IS NULL
              AND refund.refund_of_transaction_id IS NULL
        ) refunds ON true
        WHERE ci.user_id=c.user_id AND (c.id IS NULL OR purchase.card_id=c.id)
          AND (NULL::bpchar IS NULL OR purchase.currency_code=NULL::bpchar)
          AND ci.deleted_at IS NULL AND ci.status<>'VOIDED'
          AND purchase.status IN ('CONFIRMED','REVISED') AND purchase.deleted_at IS NULL
    ), credited AS (
        SELECT b.*, least(b.before_refund,greatest(b.purchase_refund-coalesce((
            SELECT sum(prior.before_refund) FROM base prior WHERE prior.transaction_id=b.transaction_id
                AND prior.installment_number<b.installment_number
        ),0),0))::bigint AS refunded_minor FROM base b
    )
    SELECT id,transaction_id,installment_number,due_date,principal_minor,interest_minor,paid_minor,refunded_minor,
           (before_refund-refunded_minor)::bigint AS outstanding_minor,purchase_occurred_at FROM credited
) b) debt ON true
WHERE c.is_credit=true AND c.deleted_at IS NULL;

-- Legacy allocator uses the same live balances and FIFO order.
-- Source: 20261102013000_restore_baseline_dependencies.sql
CREATE OR REPLACE FUNCTION public.allocate_credit_payment(p_command jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid;
    v_tx_id uuid;
    v_card_id uuid;
    v_source_account_id uuid;
    v_liability_account_id uuid;
    v_amount_minor bigint;
    v_available_to_allocate bigint;
    v_currency_code char(3);
    v_inst record;
    v_alloc_amt bigint;
    v_unpaid_quota bigint;
BEGIN
    v_user_id := auth.uid();
    v_card_id := (p_command->>'card_id')::uuid;
    v_source_account_id := (p_command->>'source_account_id')::uuid;
    v_amount_minor := (p_command->>'amount_minor')::bigint;
    v_currency_code := (p_command->>'currency_code')::char(3);
    v_available_to_allocate := v_amount_minor;

    -- Get liability account of card
    SELECT account_id INTO v_liability_account_id FROM public.cards WHERE id = v_card_id AND user_id = v_user_id;

    -- 1. Create payment transaction
    v_tx_id := extensions.gen_random_uuid();
    INSERT INTO public.transactions (
        id, user_id, account_id, card_id, transaction_type, operation_kind,
        amount_minor, currency_code, status, revision
    ) VALUES (
        v_tx_id, v_user_id, v_source_account_id, v_card_id, 'TRANSFER', 'CARD_PAYMENT',
        v_amount_minor, v_currency_code, 'CONFIRMED', 1
    );

    -- 2. Ledger entries: reduce bank asset (-), reduce card liability (+)
    INSERT INTO internal.ledger_entries (
        transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role
    ) VALUES 
        (v_tx_id, v_user_id, v_source_account_id, -v_amount_minor, v_currency_code, 'SOURCE'),
        (v_tx_id, v_user_id, v_liability_account_id, v_amount_minor, v_currency_code, 'LIABILITY');

    -- 3. Greedy allocation across oldest pending installments
    FOR v_inst IN 
        SELECT b.* FROM private.credit_installment_balances(v_user_id,v_card_id,v_currency_code) b
        JOIN public.credit_installments ci ON ci.user_id=v_user_id AND ci.id=b.id
        WHERE b.outstanding_minor>0
        ORDER BY b.due_date,b.purchase_occurred_at,b.installment_number,b.id
        FOR UPDATE OF ci
    LOOP
        EXIT WHEN v_available_to_allocate <= 0;

        v_unpaid_quota := v_inst.outstanding_minor;
        v_alloc_amt := LEAST(v_available_to_allocate, v_unpaid_quota);

        IF v_alloc_amt > 0 THEN
            INSERT INTO public.credit_payment_allocations (
                user_id, payment_transaction_id, installment_id, allocated_minor
            ) VALUES (
                v_user_id, v_tx_id, v_inst.id, v_alloc_amt
            );

            IF v_alloc_amt >= v_inst.outstanding_minor THEN
                UPDATE public.credit_installments SET status = 'PAID', updated_at = now() WHERE id = v_inst.id;
            ELSE
                UPDATE public.credit_installments SET status = 'PARTIAL', updated_at = now() WHERE id = v_inst.id;
            END IF;

            v_available_to_allocate := v_available_to_allocate - v_alloc_amt;
        END IF;
    END LOOP;

    RETURN jsonb_build_object('success', true, 'payment_transaction_id', v_tx_id);
END;
$function$;

-- Archive guard (20261112120000) and status refresh (20261031163043)
-- already use canonical balances; reopen historical principal-only PAID installments.
DO $$ DECLARE owner_id uuid; BEGIN
 FOR owner_id IN SELECT DISTINCT user_id FROM public.credit_installments LOOP
  PERFORM private.refresh_credit_installment_statuses(owner_id);
 END LOOP;
END $$;
COMMIT;

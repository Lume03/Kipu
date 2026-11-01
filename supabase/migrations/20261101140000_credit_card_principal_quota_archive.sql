BEGIN;

ALTER TABLE public.credit_installments DROP CONSTRAINT credit_installments_terms_check;
ALTER TABLE public.credit_installments ADD CONSTRAINT credit_installments_terms_check CHECK (
    installments_count BETWEEN 1 AND 36 AND purchase_type IN ('DIRECT','INSTALLMENTS')
    AND financing IN ('WITH_INTEREST','INTEREST_FREE')
    AND rounding_policy IN ('LAST_INSTALLMENT_RESIDUAL','FIRST_INSTALLMENT_RESIDUAL')
    AND (purchase_type<>'DIRECT' OR (installments_count=1 AND financing='INTEREST_FREE'))
    AND ((financing='INTEREST_FREE' AND tea_bps IS NULL AND rate_source IS NULL)
      OR (financing='WITH_INTEREST' AND tea_bps IS NOT NULL AND rate_source IS NOT NULL AND tea_bps BETWEEN 1 AND 100000 AND rate_source IN ('PERSONAL','REFERENCE','USER_ENTERED')))
) NOT VALID;

CREATE OR REPLACE FUNCTION private.credit_purchase_schedule(p_amount bigint,p_count integer,p_tea_bps integer)
RETURNS TABLE(installment_number integer,principal_minor bigint,interest_minor bigint)
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE v_monthly numeric; v_payment bigint; v_outstanding bigint := p_amount; k integer;
BEGIN
    IF p_amount<=0 OR p_count NOT BETWEEN 1 AND 36 OR coalesce(p_tea_bps,0) NOT BETWEEN 0 AND 100000 THEN
        RAISE EXCEPTION 'Invalid financing input' USING ERRCODE='22023';
    END IF;
    v_monthly := CASE WHEN coalesce(p_tea_bps,0)>0 THEN power(1.0+p_tea_bps/10000.0,1.0/12.0)-1.0 ELSE 0 END;
    v_payment := CASE WHEN v_monthly>0 THEN round((p_amount*v_monthly/(1.0-power(1.0+v_monthly,-p_count)))::numeric)::bigint
                      ELSE p_amount/p_count END;
    FOR k IN 1..p_count LOOP
        installment_number := k;
        interest_minor := CASE WHEN v_monthly>0 THEN round((v_outstanding*v_monthly)::numeric)::bigint ELSE 0 END;
        principal_minor := CASE WHEN k=p_count THEN v_outstanding ELSE greatest(0,least(v_payment-interest_minor,v_outstanding)) END;
        v_outstanding := v_outstanding-principal_minor;
        RETURN NEXT;
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION private.credit_purchase_schedule(p_amount bigint,p_count integer,p_tea_bps integer,p_rounding text)
RETURNS TABLE(installment_number integer,principal_minor bigint,interest_minor bigint)
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE v_principals bigint[] := ARRAY[]::bigint[]; v_interests bigint[] := ARRAY[]::bigint[];
    v_item record; v_residual bigint; v_remaining bigint; v_moved bigint; k integer;
    v_monthly numeric; v_payment bigint;
BEGIN
    IF p_rounding NOT IN ('LAST_INSTALLMENT_RESIDUAL','FIRST_INSTALLMENT_RESIDUAL') THEN
        RAISE EXCEPTION 'Invalid rounding policy' USING ERRCODE='22023';
    END IF;
    FOR v_item IN SELECT * FROM private.credit_purchase_schedule(p_amount,p_count,p_tea_bps) LOOP
        v_principals := array_append(v_principals,v_item.principal_minor);
        v_interests := array_append(v_interests,v_item.interest_minor);
    END LOOP;
    IF p_rounding='FIRST_INSTALLMENT_RESIDUAL' AND p_count>1 THEN
        v_monthly := CASE WHEN coalesce(p_tea_bps,0)>0 THEN power(1.0+p_tea_bps/10000.0,1.0/12.0)-1.0 ELSE 0 END;
        v_payment := CASE WHEN v_monthly>0 THEN round(p_amount*v_monthly/(1.0-power(1.0+v_monthly,-p_count)))::bigint ELSE p_amount/p_count END;
        v_residual := v_principals[p_count]+v_interests[p_count]-v_payment;
        IF v_residual>=0 THEN
            v_principals[1] := v_principals[1]+v_residual;
            v_principals[p_count] := v_principals[p_count]-v_residual;
        ELSE
            v_remaining := -v_residual;
            FOR k IN 1..p_count-1 LOOP
                v_moved := least(v_remaining,v_principals[k]);
                v_principals[k] := v_principals[k]-v_moved;
                v_principals[p_count] := v_principals[p_count]+v_moved;
                v_remaining := v_remaining-v_moved;
                EXIT WHEN v_remaining=0;
            END LOOP;
        END IF;
    END IF;
    FOR k IN 1..p_count LOOP
        installment_number:=k; principal_minor:=v_principals[k]; interest_minor:=v_interests[k]; RETURN NEXT;
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION private.credit_purchase_schedule(bigint,integer,integer,text) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION private.credit_installment_balances(p_owner uuid,p_card uuid,p_currency bpchar)
RETURNS TABLE(id uuid,transaction_id uuid,installment_number smallint,due_date date,
    principal_minor bigint,interest_minor bigint,paid_minor bigint,refunded_minor bigint,
    outstanding_minor bigint,purchase_occurred_at timestamptz)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    WITH base AS (
        SELECT ci.*, purchase.occurred_at AS purchase_occurred_at,
               coalesce(paid.amount,0)::bigint AS paid_minor, coalesce(refunds.amount,0)::bigint AS purchase_refund,
               greatest(ci.principal_minor-coalesce(paid.amount,0),0)::bigint AS before_refund
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

CREATE OR REPLACE FUNCTION public.register_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_card_id uuid;
    v_account_id uuid;
    v_alias text;
    v_type text;
    v_currency bpchar(3);
    v_network text;
    v_issuer text;
    v_last_four text;
    v_credit_limit bigint;
    v_billing_day integer;
    v_due_day integer;
    v_preset_id text;
    v_style_preset_id text;
    v_color text;
    v_icon text;
    v_request_hash text;
    v_client_request_hash text;
    v_existing_receipt record;
    v_computable_count integer;
    v_linked_account record;
    v_expected_liability_account_id uuid;
    v_response jsonb;
    v_closing_inclusive boolean := coalesce((p_command->>'closing_day_inclusive')::boolean,false);
    v_cycle_basis text := coalesce(p_command->>'cycle_date_basis','OPERATION');
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_operation_id := (p_command->>'operation_id')::uuid;
    v_card_id := (p_command->>'card_id')::uuid;
    v_account_id := NULLIF(p_command->>'account_id', '')::uuid;
    v_alias := NULLIF(trim(p_command->>'alias'), '');
    IF v_cycle_basis NOT IN ('OPERATION','PROCESSING') THEN RAISE EXCEPTION 'INVALID_REQUEST: Invalid cycle basis'; END IF;
    v_type := upper(p_command->>'type');
    v_currency := NULLIF(trim(p_command->>'currency'), '')::bpchar(3);
    v_network := upper(p_command->>'network');
    v_issuer := NULLIF(trim(p_command->>'issuer'), '');
    v_last_four := trim(p_command->>'last_four_digits');
    v_credit_limit := (p_command->>'credit_limit_minor_units')::bigint;
    v_billing_day := (p_command->>'billing_day')::integer;
    v_due_day := (p_command->>'due_day')::integer;
    v_preset_id := p_command->>'preset_id';
    v_style_preset_id := p_command->>'style_preset_id';
    v_color := p_command->>'color';
    v_icon := p_command->>'icon';
    v_client_request_hash := p_command->>'payload_hash';

    IF v_card_id IS NULL OR v_operation_id IS NULL OR v_client_request_hash IS NULL
       OR v_issuer IS NULL OR length(v_issuer) > 80 OR v_alias IS NOT NULL AND length(v_alias) > 80
       OR v_currency IS NULL OR length(v_last_four) <> 4
       OR v_last_four !~ '^[0-9]{4}$' THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Required card fields are missing or invalid';
    END IF;

    -- Serialize duplicate submissions and compute the receipt identity from
    -- the complete normalized request, never from the client-provided digest.
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id::text || ':' || v_operation_id::text, 0)
    );
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('instrument-quota:' || v_user_id::text, 0));
    v_request_hash := pg_catalog.encode(
        extensions.digest(convert_to((p_command - 'payload_hash')::text, 'UTF8'), 'sha256'),
        'hex'
    );

    SELECT * INTO v_existing_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text;
    IF FOUND THEN
        -- Accept the previous client-hash format for in-flight commands created
        -- before this migration; all new receipts store the server hash above.
        IF v_existing_receipt.request_hash IN (v_request_hash, v_client_request_hash) THEN
            RETURN v_existing_receipt.response_payload;
        END IF;
        RAISE EXCEPTION 'OPERATION_COLLISION: Operation ID reused with differing payload';
    END IF;

    SELECT count(DISTINCT quota_id) INTO v_computable_count FROM private.instrument_quota_groups(v_user_id);
    IF v_type <> 'DEBIT' AND private.is_instrument_quota_blocked(v_user_id, v_computable_count) THEN
        RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier';
    END IF;

    IF v_type = 'DEBIT' THEN
        IF v_account_id IS NULL THEN RAISE EXCEPTION 'INVALID_REQUEST: Debit card requires linked account_id'; END IF;
        SELECT * INTO v_linked_account FROM public.accounts
        WHERE id = v_account_id AND user_id = v_user_id AND is_archived = false;
        IF NOT FOUND THEN RAISE EXCEPTION 'ACCOUNT_NOT_FOUND: Linked account must exist and be active'; END IF;
        IF v_linked_account.account_type = 'CREDIT_LIABILITY' THEN
            RAISE EXCEPTION 'INVALID_REQUEST: Debit card cannot link to a credit-liability account';
        END IF;
        v_currency := v_linked_account.currency_code;
    ELSIF v_type = 'CREDIT' THEN
        v_expected_liability_account_id := internal.credit_liability_identity_v1('account', v_user_id, v_card_id);
        IF v_account_id IS DISTINCT FROM v_expected_liability_account_id
           OR v_credit_limit IS NULL OR v_credit_limit < 0
           OR v_billing_day IS NULL OR v_billing_day NOT BETWEEN 1 AND 31
           OR v_due_day IS NULL OR v_due_day NOT BETWEEN 1 AND 31 THEN
            RAISE EXCEPTION 'INVALID_REQUEST: Credit card fields are invalid';
        END IF;
        SELECT * INTO v_linked_account FROM public.accounts
        WHERE id = v_account_id AND user_id = v_user_id;
        IF FOUND THEN
            IF v_linked_account.account_type <> 'CREDIT_LIABILITY'
               OR v_linked_account.currency_code <> v_currency OR v_linked_account.is_archived THEN
                RAISE EXCEPTION 'INVALID_REQUEST: Credit card liability account is incompatible';
            END IF;
        ELSE
            INSERT INTO public.accounts (
                id, user_id, creation_operation_id, name, account_type, currency_code,
                initial_balance_minor_units, opened_at, is_archived, revision
            ) VALUES (
                v_account_id, v_user_id,
                internal.credit_liability_identity_v1('create-operation', v_user_id, v_card_id),
                'Pasivo tarjeta •••• ' || v_last_four, 'CREDIT_LIABILITY', v_currency,
                0, clock_timestamp(), false, 1
            );
        END IF;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown card type %', v_type;
    END IF;
    IF v_currency NOT IN ('PEN', 'USD') OR NOT EXISTS (SELECT 1 FROM public.currencies WHERE code = v_currency) THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Unsupported card currency';
    END IF;

    INSERT INTO public.cards (
        id, user_id, creation_operation_id, account_id, network, alias, last4,
        is_credit, credit_limit_minor, closing_day, due_day, closing_day_inclusive, cycle_date_basis, currency_code, issuer,
        preset_id, style_preset_id, color, icon, is_archived, revision
    ) VALUES (
        v_card_id, v_user_id, v_operation_id, v_account_id, v_network,
        COALESCE(v_alias, v_issuer), v_last_four, (v_type = 'CREDIT'), v_credit_limit,
        v_billing_day, v_due_day, v_closing_inclusive, v_cycle_basis, v_currency, v_issuer, v_preset_id,
        v_style_preset_id, v_color, v_icon, false, 1
    );

    IF v_type = 'CREDIT' THEN
        INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
        SELECT a.user_id, 'ACCOUNT', a.id, a.revision, 'UPSERT', jsonb_build_object(
            'id', a.id, 'creation_operation_id', a.creation_operation_id,
            'alias', a.name, 'type', a.account_type, 'currency', a.currency_code,
            'initial_balance_minor_units', a.initial_balance_minor_units,
            'opened_at', a.opened_at, 'is_archived', a.is_archived
        )
        FROM public.accounts a WHERE a.id = v_account_id AND a.user_id = v_user_id
          AND NOT EXISTS (
              SELECT 1 FROM internal.sync_changes sc
              WHERE sc.user_id = v_user_id AND sc.entity_type = 'ACCOUNT' AND sc.entity_id = v_account_id
          );
    END IF;

    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'CARD', c.id, c.revision, 'UPSERT', internal.card_sync_projection_v1(c)
    FROM public.cards c WHERE c.id = v_card_id AND c.user_id = v_user_id;

    v_response := jsonb_build_object('status', 'APPLIED', 'card_id', v_card_id, 'revision', 1);
    INSERT INTO internal.command_receipts(user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_user_id, v_operation_id::text, 'REGISTER_CARD', v_request_hash, v_response, 'APPLIED');
    RETURN v_response;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_instrument_archived_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_instrument_id uuid;
    v_instrument_type text;
    v_is_archived boolean;
    v_current_revision bigint;
    v_computable_count integer;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_instrument_id := (p_command->>'instrument_id')::uuid;
    v_instrument_type := p_command->>'instrument_type';
    v_is_archived := (p_command->>'is_archived')::boolean;

    -- Reactivating consumes quota
    IF NOT v_is_archived THEN
        SELECT (
            (SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
            (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)
        ) INTO v_computable_count;

        IF v_computable_count >= 4 THEN
            RAISE EXCEPTION 'FREE_LIMIT_REACHED: Reactivation would exceed Free tier limit of 4 active instruments';
        END IF;
    END IF;

    IF v_instrument_type = 'ACCOUNT' THEN
        UPDATE public.accounts
        SET is_archived = v_is_archived, revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSIF v_instrument_type = 'CARD' THEN
        PERFORM 1 FROM public.cards WHERE id = v_instrument_id AND user_id = v_user_id FOR UPDATE;
        IF v_is_archived AND EXISTS (
            SELECT 1 FROM public.cards c WHERE c.id = v_instrument_id AND c.user_id = v_user_id AND c.is_credit
              AND EXISTS (SELECT 1 FROM private.credit_installment_balances(v_user_id, v_instrument_id, NULL) b WHERE b.outstanding_minor > 0)
        ) THEN
            RAISE EXCEPTION 'CARD_HAS_OUTSTANDING_DEBT: Debes liquidar la deuda de la tarjeta antes de archivarla.';
        END IF;
        UPDATE public.cards
        SET is_archived = v_is_archived, revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown instrument type %', v_instrument_type;
    END IF;

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'instrument_id', v_instrument_id,
        'is_archived', v_is_archived,
        'revision', v_current_revision
    );

    RETURN v_response;
END;
$$;

CREATE OR REPLACE FUNCTION private.reconcile_credit_purchase_terms(p_owner uuid,p_tx jsonb)
RETURNS void LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE v_purchase public.transactions; v_card public.cards; v_item record; v_closed date; v_due date;
    v_financing text:=coalesce(p_tx->>'financing','INTEREST_FREE');
    v_count integer:=coalesce((p_tx->>'installment_count')::integer,1);
    v_tea integer:=(p_tx->>'tea_bps')::integer;
BEGIN
    IF p_tx->>'purchase_type' IS NULL THEN RETURN; END IF;
    SELECT * INTO v_purchase FROM public.transactions WHERE user_id=p_owner AND id=(p_tx->>'id')::uuid FOR UPDATE;
    IF NOT FOUND OR v_purchase.status NOT IN ('CONFIRMED','REVISED') THEN RETURN; END IF;
    IF NOT EXISTS(SELECT 1 FROM public.credit_installments WHERE user_id=p_owner AND transaction_id=v_purchase.id
        AND billing_closing_date IS NULL) THEN RETURN; END IF;
    IF EXISTS(SELECT 1 FROM public.credit_payment_allocations a JOIN public.credit_installments i ON i.id=a.installment_id
        WHERE i.user_id=p_owner AND i.transaction_id=v_purchase.id) THEN RETURN; END IF;
    SELECT * INTO STRICT v_card FROM public.cards WHERE user_id=p_owner AND id=v_purchase.card_id FOR UPDATE;
    FOR v_item IN SELECT * FROM private.credit_purchase_schedule(v_purchase.amount_minor,v_count,v_tea,coalesce(p_tx->>'rounding_policy','LAST_INSTALLMENT_RESIDUAL')) LOOP
        v_closed:=private.credit_cycle_closing_date(v_purchase.occurred_at,v_card.closing_day,v_item.installment_number,
            v_card.closing_day_inclusive,v_card.cycle_date_basis,(p_tx->>'processed_at')::timestamptz);
        v_due:=private.credit_cycle_installment_due_date(v_purchase.occurred_at,v_card.closing_day,v_card.due_day,
            v_item.installment_number,v_card.closing_day_inclusive,v_card.cycle_date_basis,(p_tx->>'processed_at')::timestamptz);
        UPDATE public.credit_installments SET principal_minor=v_item.principal_minor,interest_minor=v_item.interest_minor,
            due_date=v_due,billing_closing_date=v_closed,purchase_type=p_tx->>'purchase_type',financing=v_financing,
            installments_count=v_count,tea_bps=v_tea,rate_source=p_tx->>'rate_source',rounding_policy=coalesce(p_tx->>'rounding_policy','LAST_INSTALLMENT_RESIDUAL')
        WHERE user_id=p_owner AND transaction_id=v_purchase.id AND installment_number=v_item.installment_number;
    END LOOP;
    UPDATE public.transactions SET processed_at=(p_tx->>'processed_at')::timestamptz,revision=revision+1
        WHERE user_id=p_owner AND id=v_purchase.id RETURNING * INTO v_purchase;
    -- Receipts are keyed by the command identity, not a transaction foreign key.
    UPDATE internal.command_receipts SET response_payload=response_payload || jsonb_build_object(
        'installments',internal.credit_transaction_sync_projection_v1(v_purchase)->'installments',
        'remaining_debt_minor',(SELECT coalesce(sum(b.outstanding_minor),0) FROM private.credit_installment_balances(p_owner,v_card.id,v_purchase.currency_code) b))
        WHERE user_id=p_owner AND response_payload->>'transaction_id'=v_purchase.id::text;
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
        VALUES(p_owner,'TRANSACTION',v_purchase.id,v_purchase.revision,'UPSERT',internal.credit_transaction_sync_projection_v1(v_purchase));
END $$;

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
    SELECT sum(s.principal_minor)::bigint INTO v_financed_total
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

COMMIT;


-- D1-D8: immutable purchase terms, contractual interest and civil-date billing cycles.
-- Prepared only: this migration must be deployed separately from the Android release.
BEGIN;

ALTER TABLE public.cards
    ADD COLUMN IF NOT EXISTS closing_day_inclusive boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS cycle_date_basis text NOT NULL DEFAULT 'OPERATION';
ALTER TABLE public.cards ADD CONSTRAINT cards_cycle_date_basis_check
    CHECK (cycle_date_basis IN ('OPERATION','PROCESSING'));
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS processed_at timestamptz;
ALTER TABLE public.credit_installments
    ADD COLUMN purchase_type text NOT NULL DEFAULT 'DIRECT',
    ADD COLUMN financing text NOT NULL DEFAULT 'INTEREST_FREE',
    ADD COLUMN installments_count integer NOT NULL DEFAULT 1,
    ADD COLUMN tea_bps integer,
    ADD COLUMN rate_source text,
    ADD COLUMN rounding_policy text NOT NULL DEFAULT 'LAST_INSTALLMENT_RESIDUAL',
    ADD COLUMN billing_closing_date date;
UPDATE public.credit_installments ci SET installments_count = greatest(1, coalesce(t.installment_count,1),
    (SELECT max(other.installment_number) FROM public.credit_installments other
     WHERE other.user_id=ci.user_id AND other.transaction_id=ci.transaction_id))
FROM public.transactions t WHERE t.user_id=ci.user_id AND t.id=ci.transaction_id;
UPDATE public.credit_installments SET purchase_type=CASE WHEN installments_count=1 THEN 'DIRECT' ELSE 'INSTALLMENTS' END;
-- Old dates and monetary components are historical evidence and are not recalculated here.
ALTER TABLE public.credit_installments ADD CONSTRAINT credit_installments_terms_check CHECK (
    installments_count BETWEEN 1 AND 36 AND purchase_type IN ('DIRECT','INSTALLMENTS')
    AND financing IN ('WITH_INTEREST','INTEREST_FREE')
    AND rounding_policy='LAST_INSTALLMENT_RESIDUAL'
    AND (purchase_type<>'DIRECT' OR (installments_count=1 AND financing='INTEREST_FREE'))
    AND ((financing='INTEREST_FREE' AND tea_bps IS NULL AND rate_source IS NULL)
      OR (financing='WITH_INTEREST' AND tea_bps IS NOT NULL AND rate_source IS NOT NULL AND tea_bps BETWEEN 1 AND 100000 AND rate_source IN ('PERSONAL','REFERENCE','USER_ENTERED')))
) NOT VALID;

CREATE OR REPLACE FUNCTION private.credit_cycle_closing_date(
    p_occurred_at timestamptz, p_closing_day smallint, p_installment_number integer,
    p_closing_day_inclusive boolean, p_cycle_date_basis text, p_processed_at timestamptz
) RETURNS date LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE v_date date; v_month date; v_close date;
BEGIN
    IF p_closing_day NOT BETWEEN 1 AND 31 OR p_installment_number NOT BETWEEN 1 AND 36
       OR p_cycle_date_basis NOT IN ('OPERATION','PROCESSING') OR p_occurred_at IS NULL THEN
        RAISE EXCEPTION 'Invalid card cycle input' USING ERRCODE='22023';
    END IF;
    v_date := ((CASE WHEN p_cycle_date_basis='PROCESSING' THEN coalesce(p_processed_at,p_occurred_at)
                    ELSE p_occurred_at END) AT TIME ZONE 'America/Lima')::date;
    v_month := date_trunc('month',v_date)::date;
    v_close := v_month + least(p_closing_day,extract(day FROM v_month+interval '1 month - 1 day')::integer)-1;
    IF v_date>v_close OR (v_date=v_close AND NOT p_closing_day_inclusive) THEN
        v_month := (v_month+interval '1 month')::date;
    END IF;
    v_month := (v_month+make_interval(months=>p_installment_number-1))::date;
    RETURN v_month + least(p_closing_day,extract(day FROM v_month+interval '1 month - 1 day')::integer)-1;
END $$;
REVOKE ALL ON FUNCTION private.credit_cycle_closing_date(timestamptz,smallint,integer,boolean,text,timestamptz) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION private.credit_cycle_installment_due_date(
    p_occurred_at timestamptz, p_closing_day smallint, p_due_day smallint, p_installment_number integer,
    p_closing_day_inclusive boolean, p_cycle_date_basis text, p_processed_at timestamptz
) RETURNS date LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE v_close date; v_month date; v_due date;
BEGIN
    IF p_due_day NOT BETWEEN 1 AND 31 THEN RAISE EXCEPTION 'Invalid due day' USING ERRCODE='22023'; END IF;
    v_close := private.credit_cycle_closing_date(p_occurred_at,p_closing_day,p_installment_number,
        p_closing_day_inclusive,p_cycle_date_basis,p_processed_at);
    v_month := date_trunc('month',v_close)::date;
    v_due := v_month + least(p_due_day,extract(day FROM v_month+interval '1 month - 1 day')::integer)-1;
    IF v_due<=v_close THEN
        v_month := (v_month+interval '1 month')::date;
        v_due := v_month + least(p_due_day,extract(day FROM v_month+interval '1 month - 1 day')::integer)-1;
    END IF;
    RETURN v_due;
END $$;
REVOKE ALL ON FUNCTION private.credit_cycle_installment_due_date(timestamptz,smallint,smallint,integer,boolean,text,timestamptz) FROM PUBLIC,anon,authenticated,service_role;

-- Preserve the original four-argument signature for already queued legacy commands.
CREATE OR REPLACE FUNCTION private.credit_cycle_installment_due_date(
    p_occurred_at timestamptz, p_closing_day smallint, p_due_day smallint, p_installment_number integer
) RETURNS date LANGUAGE sql IMMUTABLE SET search_path='' AS $$
    SELECT private.credit_cycle_installment_due_date(p_occurred_at,p_closing_day,p_due_day,p_installment_number,false,'OPERATION',NULL)
$$;
REVOKE ALL ON FUNCTION private.credit_cycle_installment_due_date(timestamptz,smallint,smallint,integer) FROM PUBLIC,anon,authenticated,service_role;

-- French amortization: round interest on the remaining balance each period, reconcile principal last.
CREATE OR REPLACE FUNCTION private.credit_purchase_schedule(p_amount bigint,p_count integer,p_tea_bps integer)
RETURNS TABLE(installment_number integer,principal_minor bigint,interest_minor bigint)
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE v_monthly double precision; v_payment bigint; v_outstanding bigint := p_amount; k integer;
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
REVOKE ALL ON FUNCTION private.credit_purchase_schedule(bigint,integer,integer) FROM PUBLIC,anon,authenticated,service_role;

-- Refunds are applied once to the remaining installments of their own purchase.
CREATE OR REPLACE FUNCTION private.credit_installment_balances(p_owner uuid,p_card uuid,p_currency bpchar)
RETURNS TABLE(id uuid,transaction_id uuid,installment_number smallint,due_date date,
    principal_minor bigint,interest_minor bigint,paid_minor bigint,refunded_minor bigint,
    outstanding_minor bigint,purchase_occurred_at timestamptz)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    WITH base AS (
        SELECT ci.*, purchase.occurred_at AS purchase_occurred_at,
               coalesce(paid.amount,0)::bigint AS paid_minor, coalesce(refunds.amount,0)::bigint AS purchase_refund,
               greatest(ci.principal_minor+ci.interest_minor-coalesce(paid.amount,0),0)::bigint AS before_refund
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
REVOKE ALL ON FUNCTION private.credit_installment_balances(uuid,uuid,bpchar) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION private.refresh_credit_installment_statuses(p_owner uuid)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    UPDATE public.credit_installments ci
    SET status=CASE WHEN b.outstanding_minor=0 THEN 'PAID'
                    WHEN b.paid_minor+b.refunded_minor>0 THEN 'PARTIAL' ELSE 'PENDING' END
    FROM private.credit_installment_balances(p_owner,NULL,NULL) b
    WHERE ci.user_id=p_owner AND ci.id=b.id AND ci.status IS DISTINCT FROM
        CASE WHEN b.outstanding_minor=0 THEN 'PAID' WHEN b.paid_minor+b.refunded_minor>0 THEN 'PARTIAL' ELSE 'PENDING' END
$$;
REVOKE ALL ON FUNCTION private.refresh_credit_installment_statuses(uuid) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION private.refresh_credit_installments_after_change()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
    IF TG_OP='DELETE' THEN PERFORM private.refresh_credit_installment_statuses(OLD.user_id); RETURN OLD;
    ELSE PERFORM private.refresh_credit_installment_statuses(NEW.user_id); RETURN NEW; END IF;
END $$;
REVOKE ALL ON FUNCTION private.refresh_credit_installments_after_change() FROM PUBLIC,anon,authenticated,service_role;
CREATE TRIGGER credit_allocations_refresh_status AFTER INSERT OR UPDATE OR DELETE ON public.credit_payment_allocations
    FOR EACH ROW EXECUTE FUNCTION private.refresh_credit_installments_after_change();
CREATE TRIGGER credit_transactions_refresh_status AFTER UPDATE OF status,deleted_at ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION private.refresh_credit_installments_after_change();
CREATE TRIGGER credit_refunds_refresh_status AFTER INSERT OR UPDATE OR DELETE ON public.transaction_links
    FOR EACH ROW EXECUTE FUNCTION private.refresh_credit_installments_after_change();
CREATE TRIGGER credit_installments_refresh_status AFTER INSERT OR UPDATE OF principal_minor,interest_minor,deleted_at ON public.credit_installments
    FOR EACH ROW EXECUTE FUNCTION private.refresh_credit_installments_after_change();

CREATE OR REPLACE FUNCTION internal.card_sync_projection_v1(p_card public.cards)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT jsonb_build_object(
        'id', p_card.id,
        'creation_operation_id', p_card.creation_operation_id,
        'account_id', p_card.account_id,
        'alias', NULLIF(btrim(p_card.alias), ''),
        'type', CASE WHEN p_card.is_credit THEN 'CREDIT' ELSE 'DEBIT' END,
        'currency', NULLIF(btrim(p_card.currency_code::text), ''),
        'network', COALESCE(NULLIF(btrim(p_card.network), ''), 'OTHER'),
        'issuer', COALESCE(NULLIF(btrim(p_card.issuer), ''), 'Desconocido'),
        'last_four_digits', btrim(p_card.last4::text),
        'credit_limit_minor_units', p_card.credit_limit_minor,
        'billing_day', p_card.closing_day,
        'due_day', p_card.due_day,
        'closing_day_inclusive',p_card.closing_day_inclusive, 'cycle_date_basis',p_card.cycle_date_basis,
        'preset_id', p_card.preset_id,
        'style_preset_id', p_card.style_preset_id,
        'color', p_card.color,
        'icon', p_card.icon,
        'is_archived', p_card.is_archived,
        'personal_tea_bps', p_card.personal_tea_bps,
        'created_at', p_card.created_at
    )
$$;

CREATE OR REPLACE FUNCTION internal.emit_card_sync_projection_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_operation text;
BEGIN
    IF ROW(NEW.alias, NEW.network, NEW.issuer, NEW.last4, NEW.currency_code,
           NEW.account_id, NEW.credit_limit_minor, NEW.closing_day, NEW.due_day, NEW.closing_day_inclusive, NEW.cycle_date_basis, NEW.preset_id,
           NEW.style_preset_id, NEW.color, NEW.icon, NEW.is_archived)
       IS NOT DISTINCT FROM
       ROW(OLD.alias, OLD.network, OLD.issuer, OLD.last4, OLD.currency_code,
           OLD.account_id, OLD.credit_limit_minor, OLD.closing_day, OLD.due_day, OLD.closing_day_inclusive, OLD.cycle_date_basis, OLD.preset_id,
           OLD.style_preset_id, OLD.color, OLD.icon, OLD.is_archived) THEN
        RETURN NEW;
    END IF;

    v_operation := CASE WHEN NEW.is_archived THEN 'ARCHIVE' ELSE 'UPSERT' END;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (NEW.user_id, 'CARD', NEW.id, NEW.revision, v_operation,
            internal.card_sync_projection_v1(NEW));
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS cards_emit_sync_projection_v1 ON public.cards;
CREATE TRIGGER cards_emit_sync_projection_v1
AFTER UPDATE OF account_id,alias,network,issuer,last4,currency_code,credit_limit_minor,closing_day,due_day,
    closing_day_inclusive,cycle_date_basis,preset_id,style_preset_id,color,icon,is_archived
ON public.cards FOR EACH ROW EXECUTE FUNCTION internal.emit_card_sync_projection_v1();

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

    SELECT ((SELECT count(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY')) +
            (SELECT count(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false))
    INTO v_computable_count;
    IF v_computable_count >= 4 THEN RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier'; END IF;

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

CREATE OR REPLACE FUNCTION public.update_credit_card_terms_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_card jsonb := p_command->'card';
    v_idempotency_key text := NULLIF(p_command->>'idempotency_key', '');
    v_card_id uuid;
    v_credit_limit bigint;
    v_billing_day integer;
    v_due_day integer;
    v_expected_revision bigint;
    v_request_hash text;
    v_existing record;
    v_card_owner uuid;
    v_is_credit boolean;
    v_archived boolean;
    v_revision bigint;
    v_receipt_id uuid;
    v_now timestamptz := clock_timestamp();
    v_response jsonb;
    v_card_payload jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', NULL, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'UNAUTHORIZED', 'field', NULL, 'retryable', false)
        );
    END IF;

    IF COALESCE((p_command->>'contract_version')::integer, 1) <> 1
       OR v_card IS NULL
       OR v_idempotency_key IS NULL
       OR NULLIF(p_command->>'request_hash', '') IS NULL
       OR NOT (v_card ? 'credit_limit_minor_units')
       OR NOT (v_card ? 'billing_day')
       OR NOT (v_card ? 'due_day')
       OR NOT (v_card ? 'expected_revision') THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', NULL, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_COMMAND', 'field', NULL, 'retryable', false)
        );
    END IF;

    BEGIN
        v_card_id := (v_card->>'id')::uuid;
        v_credit_limit := (v_card->>'credit_limit_minor_units')::bigint;
        v_billing_day := (v_card->>'billing_day')::integer;
        v_due_day := (v_card->>'due_day')::integer;
        v_expected_revision := (v_card->>'expected_revision')::bigint;
    EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', NULL, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_CARD_TERMS', 'field', 'card', 'retryable', false)
        );
    END;

    IF v_card_id IS NULL OR v_credit_limit < 0 OR v_billing_day NOT BETWEEN 1 AND 31
       OR v_due_day NOT BETWEEN 1 AND 31 OR v_expected_revision < 0
       OR (v_card->>'cycle_date_basis' IS NOT NULL AND v_card->>'cycle_date_basis' NOT IN ('OPERATION','PROCESSING'))
       OR (v_card->>'closing_day_inclusive' IS NOT NULL AND jsonb_typeof(v_card->'closing_day_inclusive')<>'boolean')
       OR (v_card ? 'alias' AND (NULLIF(btrim(v_card->>'alias'), '') IS NULL OR length(v_card->>'alias') > 120))
       OR (v_card ? 'last_four_digits' AND (v_card->>'last_four_digits') !~ '^[0-9]{4}$') THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', v_card_id, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_CARD_TERMS', 'field', 'card', 'retryable', false)
        );
    END IF;

    v_request_hash := encode(
        extensions.digest(
            convert_to(jsonb_build_object('contract_version', 1, 'card', v_card)::text, 'UTF8'),
            'sha256'
        ),
        'hex'
    );
    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key, 0));

    SELECT id, request_hash, response_payload
      INTO v_existing
      FROM internal.command_receipts
     WHERE user_id = v_user_id AND idempotency_key = v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash = v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload, '{status}', '"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object(
            'status', 'CONFLICT', 'card_id', v_card_id, 'receipt_id', v_existing.id,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'IDEMPOTENCY_CONFLICT', 'field', 'idempotency_key', 'retryable', false)
        );
    END IF;

    SELECT c.user_id, c.is_credit, c.is_archived, c.revision
      INTO v_card_owner, v_is_credit, v_archived, v_revision
      FROM public.cards AS c
     WHERE c.id = v_card_id
     FOR UPDATE;
    IF NOT FOUND OR v_card_owner <> v_user_id OR NOT v_is_credit OR v_archived THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', v_card_id, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'CREDIT_CARD_UNAVAILABLE', 'field', 'card.id', 'retryable', false)
        );
    END IF;
    IF v_revision <> v_expected_revision THEN
        RETURN jsonb_build_object(
            'status', 'CONFLICT', 'card_id', v_card_id, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'REVISION_CONFLICT', 'field', 'expected_revision', 'retryable', true)
        );
    END IF;

    UPDATE public.cards
       SET alias = COALESCE(NULLIF(btrim(v_card->>'alias'), ''), alias),
           last4 = COALESCE(v_card->>'last_four_digits', last4),
           credit_limit_minor = v_credit_limit,
           closing_day = v_billing_day,
           due_day = v_due_day,
           closing_day_inclusive=coalesce((v_card->>'closing_day_inclusive')::boolean,closing_day_inclusive),
           cycle_date_basis=coalesce(v_card->>'cycle_date_basis',cycle_date_basis),
           revision = revision + 1,
           updated_at = v_now
     WHERE id = v_card_id AND user_id = v_user_id
     RETURNING revision INTO v_revision;

    SELECT jsonb_build_object(
        'id', c.id,
        'creation_operation_id', c.creation_operation_id,
        'account_id', c.account_id,
        'alias', c.alias,
        'type', CASE WHEN c.is_credit THEN 'CREDIT' ELSE 'DEBIT' END,
        'currency', c.currency_code,
        'network', c.network,
        'issuer', c.issuer,
        'last_four_digits', c.last4,
        'credit_limit_minor_units', c.credit_limit_minor,
        'billing_day', c.closing_day,
        'due_day', c.due_day,
        'closing_day_inclusive',c.closing_day_inclusive,'cycle_date_basis',c.cycle_date_basis,
        'preset_id', c.preset_id,
        'style_preset_id', c.style_preset_id,
        'color', c.color,
        'icon', c.icon,
        'is_archived', c.is_archived,
        'created_at', c.created_at,
        'personal_tea_bps', c.personal_tea_bps
    )
      INTO v_card_payload
      FROM public.cards AS c
     WHERE c.id = v_card_id AND c.user_id = v_user_id;

    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status', 'APPLIED', 'card_id', v_card_id,
        'credit_limit_minor_units', v_credit_limit,
        'billing_day', v_billing_day,
        'due_day', v_due_day,
        'revision', v_revision,
        'receipt_id', v_receipt_id,
        'server_updated_at', v_now,
        'error', NULL
    );
    INSERT INTO internal.command_receipts(id, user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_receipt_id, v_user_id, v_idempotency_key, 'UPDATE_CREDIT_CARD_TERMS_V1', v_request_hash, v_response, 'APPLIED');
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, v_revision, 'UPSERT', v_card_payload);

    RETURN v_response;
END;
$$;

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
    FOR v_item IN SELECT * FROM private.credit_purchase_schedule(v_purchase.amount_minor,v_count,v_tea) LOOP
        v_closed:=private.credit_cycle_closing_date(v_purchase.occurred_at,v_card.closing_day,v_item.installment_number,
            v_card.closing_day_inclusive,v_card.cycle_date_basis,(p_tx->>'processed_at')::timestamptz);
        v_due:=private.credit_cycle_installment_due_date(v_purchase.occurred_at,v_card.closing_day,v_card.due_day,
            v_item.installment_number,v_card.closing_day_inclusive,v_card.cycle_date_basis,(p_tx->>'processed_at')::timestamptz);
        UPDATE public.credit_installments SET principal_minor=v_item.principal_minor,interest_minor=v_item.interest_minor,
            due_date=v_due,billing_closing_date=v_closed,purchase_type=p_tx->>'purchase_type',financing=v_financing,
            installments_count=v_count,tea_bps=v_tea,rate_source=p_tx->>'rate_source',rounding_policy='LAST_INSTALLMENT_RESIDUAL'
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
REVOKE ALL ON FUNCTION private.reconcile_credit_purchase_terms(uuid,jsonb) FROM PUBLIC,anon,authenticated,service_role;

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
       OR v_rounding<>'LAST_INSTALLMENT_RESIDUAL'
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
    FROM private.credit_purchase_schedule(v_amount,v_installments,v_tea) s;
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

    FOR v_schedule IN SELECT * FROM private.credit_purchase_schedule(v_amount,v_installments,v_tea) LOOP
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

REVOKE ALL ON FUNCTION public.register_transaction_v1_pre_liability_s3(jsonb) FROM PUBLIC,anon,authenticated,service_role;

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
    SELECT COALESCE(v_account.initial_balance_minor_units,0)+COALESCE(sum(le.signed_amount_minor),0)::bigint INTO v_funds
    FROM public.accounts a
    LEFT JOIN internal.ledger_entries le ON le.account_id=a.id AND le.user_id=a.user_id
    LEFT JOIN public.transactions tx ON tx.id=le.transaction_id AND tx.user_id=le.user_id
    WHERE a.id=v_account_id AND a.user_id=v_user_id
      AND (le.id IS NULL OR (tx.status='CONFIRMED' AND tx.deleted_at IS NULL));
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

REVOKE ALL ON FUNCTION public.allocate_credit_payment_v1_pre_liability(jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- Keep the latest planning/refund projections and enrich only credit-card changes.
ALTER FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) SET SCHEMA internal;
ALTER FUNCTION internal.pull_financial_changes_v1(integer,bigint,integer) RENAME TO pull_financial_changes_pre_credit_terms_v1;
REVOKE ALL ON FUNCTION internal.pull_financial_changes_pre_credit_terms_v1(integer,bigint,integer) FROM PUBLIC,anon,authenticated,service_role;
CREATE OR REPLACE FUNCTION public.pull_financial_changes_v1(
    contract_version integer DEFAULT 1, after_sequence bigint DEFAULT 0, "limit" integer DEFAULT 100
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner uuid:=auth.uid(); v_result jsonb; v_changes jsonb;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_result:=internal.pull_financial_changes_pre_credit_terms_v1(contract_version,after_sequence,"limit");
    SELECT coalesce(jsonb_agg(
        CASE WHEN item->>'entity_type'='TRANSACTION' AND item->>'operation'='UPSERT'
             AND t.id IS NOT NULL AND t.operation_kind::text IN ('CARD_PURCHASE','CARD_PAYMENT')
             AND t.status IN ('CONFIRMED','REVISED') AND t.deleted_at IS NULL
        THEN jsonb_set(jsonb_set(item,'{payload}',internal.credit_transaction_sync_projection_v1(t)),
            '{revision}',to_jsonb(t.revision)) ELSE item END ORDER BY ordinal), '[]'::jsonb)
    INTO v_changes
    FROM jsonb_array_elements(coalesce(v_result->'changes','[]'::jsonb)) WITH ORDINALITY changes(item,ordinal)
    LEFT JOIN public.transactions t ON t.user_id=v_owner AND t.id=(item->>'entity_id')::uuid;
    RETURN v_result || jsonb_build_object('changes',v_changes);
END $$;
REVOKE ALL ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) FROM PUBLIC,anon,service_role;
GRANT EXECUTE ON FUNCTION public.pull_financial_changes_v1(integer,bigint,integer) TO authenticated;
NOTIFY pgrst,'reload schema';

SELECT private.refresh_credit_installment_statuses(owner.user_id) FROM (SELECT DISTINCT user_id FROM public.credit_installments) owner;
COMMIT;

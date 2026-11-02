-- Restore consumed objects present in the remote baseline but absent from history.
-- Source: read-only remote-snapshot JSON (2026-11-01); no remote operation here.
BEGIN;
CREATE TABLE public.user_devices (
    id uuid NOT NULL DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL,
    device_fingerprint text NOT NULL,
    device_name text NOT NULL,
    platform text NOT NULL DEFAULT 'ANDROID'::text,
    app_version text,
    last_sync_at timestamptz NOT NULL DEFAULT now(),
    is_active boolean NOT NULL DEFAULT true,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.user_devices ADD CONSTRAINT uq_user_device_fingerprint UNIQUE (user_id, device_fingerprint);
ALTER TABLE public.user_devices ADD CONSTRAINT user_devices_pkey PRIMARY KEY (id);
ALTER TABLE public.user_devices ADD CONSTRAINT user_devices_platform_check CHECK ((platform = ANY (ARRAY['ANDROID'::text, 'IOS'::text, 'WEB'::text])));
ALTER TABLE public.user_devices ADD CONSTRAINT user_devices_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE INDEX idx_user_devices_active ON public.user_devices USING btree (user_id, is_active);
CREATE INDEX idx_user_devices_user_id ON public.user_devices USING btree (user_id);
ALTER TABLE public.user_devices ENABLE ROW LEVEL SECURITY;
CREATE POLICY user_devices_own ON public.user_devices FOR ALL TO authenticated
USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);

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
        SELECT ci.id, ci.principal_minor, ci.interest_minor, 
               COALESCE(SUM(cpa.allocated_minor), 0) AS already_paid
        FROM public.credit_installments ci
        JOIN public.transactions t ON ci.transaction_id = t.id
        LEFT JOIN public.credit_payment_allocations cpa ON ci.id = cpa.installment_id
        WHERE t.card_id = v_card_id AND ci.user_id = v_user_id AND ci.status IN ('PENDING', 'PARTIAL')
        GROUP BY ci.id, ci.principal_minor, ci.interest_minor, ci.due_date
        ORDER BY ci.due_date ASC
    LOOP
        EXIT WHEN v_available_to_allocate <= 0;

        v_unpaid_quota := (v_inst.principal_minor + v_inst.interest_minor) - v_inst.already_paid;
        v_alloc_amt := LEAST(v_available_to_allocate, v_unpaid_quota);

        IF v_alloc_amt > 0 THEN
            INSERT INTO public.credit_payment_allocations (
                user_id, payment_transaction_id, installment_id, allocated_minor
            ) VALUES (
                v_user_id, v_tx_id, v_inst.id, v_alloc_amt
            );

            IF (v_inst.already_paid + v_alloc_amt) >= (v_inst.principal_minor + v_inst.interest_minor) THEN
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

-- Explicit arguments avoid ambiguity with the existing one-argument overload.
-- The remote function body and identity are preserved; only defaults are removed.
CREATE OR REPLACE FUNCTION public.consume_auth_rate_bucket(p_bucket_key text, p_window_seconds integer, p_max_attempts integer, p_cooldown_base_seconds integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
    RETURN private.consume_rate_bucket(
        sha256(p_bucket_key::bytea),
        p_window_seconds,
        p_max_attempts,
        p_cooldown_base_seconds
    );
END;
$function$;

-- Restore explicit routine ACLs from pg_proc.proacl in the supplemental snapshot.
REVOKE ALL ON FUNCTION public.consume_auth_rate_bucket(text,integer,integer,integer) FROM PUBLIC,anon,authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.consume_auth_rate_bucket(text,integer,integer,integer) TO service_role;
REVOKE ALL ON FUNCTION public.allocate_credit_payment(jsonb) FROM PUBLIC,anon,authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.allocate_credit_payment(jsonb) TO authenticated,service_role;
-- Snapshot granted ALL to anon too. Apply the approved minimum instead:
-- owner DML under RLS, trusted server DML, no anonymous access or TRUNCATE/DDL grants.
REVOKE ALL ON public.user_devices FROM PUBLIC,anon,authenticated,service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.user_devices TO authenticated,service_role;
COMMIT;

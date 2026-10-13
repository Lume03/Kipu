-- A wallet may present the balance of one compatible bank account, but never
-- creates a second financial balance or ledger effect.
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS linked_account_id uuid;

CREATE INDEX IF NOT EXISTS idx_accounts_user_linked_account
    ON public.accounts(user_id, linked_account_id)
    WHERE linked_account_id IS NOT NULL;

ALTER TABLE public.accounts
    DROP CONSTRAINT IF EXISTS accounts_wallet_link_shape_check;
ALTER TABLE public.accounts
    ADD CONSTRAINT accounts_wallet_link_shape_check CHECK (
        linked_account_id IS NULL OR account_type = 'WALLET'
    );

-- Existing account change events also need preset metadata so newly paired
-- devices can offer the right Yape/Plin eligibility choices.
UPDATE internal.sync_changes sc
SET payload = COALESCE(sc.payload, '{}'::jsonb) || jsonb_build_object(
    'preset_id', a.preset_id,
    'linked_account_id', a.linked_account_id
)
FROM public.accounts a
WHERE sc.entity_type = 'ACCOUNT' AND sc.entity_id = a.id AND sc.user_id = a.user_id;

CREATE OR REPLACE FUNCTION public.create_account_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_account_id uuid;
    v_opening_movement_id uuid;
    v_alias text;
    v_type public.account_type;
    v_currency bpchar(3);
    v_preset_id text;
    v_color text;
    v_icon text;
    v_linked_account_id uuid;
    v_required_preset text;
    v_initial_balance bigint;
    v_opened_at timestamptz;
    v_request_hash text;
    v_existing_receipt record;
    v_computable_count integer;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_operation_id := (p_command->>'operation_id')::uuid;
    v_account_id := (p_command->>'account_id')::uuid;
    v_opening_movement_id := (p_command->>'opening_movement_id')::uuid;
    v_alias := trim(p_command->>'alias');
    v_type := (p_command->>'type')::public.account_type;
    v_currency := (p_command->>'currency')::bpchar(3);
    v_preset_id := p_command->>'preset_id';
    v_color := p_command->>'color';
    v_icon := p_command->>'icon';
    v_initial_balance := (p_command->>'initial_balance_minor_units')::bigint;
    v_opened_at := (p_command->>'opened_at')::timestamptz;
    v_request_hash := p_command->>'payload_hash';
    v_linked_account_id := NULLIF(p_command->>'linked_account_id', '')::uuid;

    IF v_operation_id IS NULL OR v_account_id IS NULL OR v_opening_movement_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Missing required UUIDs';
    END IF;
    IF v_initial_balance < 0 THEN RAISE EXCEPTION 'INVALID_AMOUNT: Initial balance cannot be negative'; END IF;
    IF v_linked_account_id IS NOT NULL THEN
        v_required_preset := CASE v_preset_id WHEN 'YAPE' THEN 'BCP' WHEN 'PLIN' THEN 'BBVA' ELSE NULL END;
        IF v_type <> 'WALLET' OR v_required_preset IS NULL OR v_initial_balance <> 0 OR NOT EXISTS (
            SELECT 1 FROM public.accounts a
            WHERE a.id = v_linked_account_id AND a.user_id = v_user_id
              AND a.account_type IN ('SAVINGS', 'BANK') AND NOT a.is_archived
              AND a.currency_code = v_currency AND a.preset_id = v_required_preset
              AND EXISTS (SELECT 1 FROM public.cards c WHERE c.user_id=v_user_id
                  AND c.account_id=a.id AND NOT c.is_credit AND NOT c.is_archived)
        ) THEN
            RAISE EXCEPTION 'INVALID_WALLET_AFFILIATION: Wallet requires a compatible active bank account and zero own balance';
        END IF;
    END IF;

    SELECT * INTO v_existing_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_existing_receipt.request_hash = v_request_hash THEN RETURN v_existing_receipt.response_payload; END IF;
        RAISE EXCEPTION 'OPERATION_COLLISION: Operation ID reused with differing payload';
    END IF;

    IF v_type <> 'CASH' THEN
        SELECT ((SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
                (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)) INTO v_computable_count;
        IF v_computable_count >= 4 THEN RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier'; END IF;
    END IF;

    INSERT INTO public.accounts (
        id, user_id, creation_operation_id, name, account_type, currency_code,
        institution_code, color_argb, icon_key, initial_balance_minor_units, opened_at,
        preset_id, color, icon, is_archived, revision, linked_account_id
    ) VALUES (
        v_account_id, v_user_id, v_operation_id, v_alias, v_type, v_currency,
        v_preset_id, NULL, v_icon, v_initial_balance, v_opened_at,
        v_preset_id, v_color, v_icon, false, 1, v_linked_account_id
    );

    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units,
        currency, account_id, opening_account_id, effective_at, status
    ) VALUES (
        v_opening_movement_id, v_operation_id, 0, v_user_id, 'OPENING', v_initial_balance,
        v_currency, v_account_id, v_account_id, v_opened_at, 'POSTED'
    );

    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'ACCOUNT', v_account_id, 1, 'UPSERT', jsonb_build_object(
        'id', v_account_id, 'creation_operation_id', v_operation_id, 'alias', v_alias,
        'type', v_type, 'currency', v_currency, 'preset_id', v_preset_id,
        'initial_balance_minor_units', v_initial_balance,
        'opened_at', v_opened_at, 'linked_account_id', v_linked_account_id
    ));
    v_response := jsonb_build_object('status', 'APPLIED', 'account_id', v_account_id,
        'opening_movement_id', v_opening_movement_id, 'revision', 1);
    INSERT INTO internal.command_receipts(user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_user_id, v_operation_id::text, 'CREATE_ACCOUNT', v_request_hash, v_response, 'APPLIED');
    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.create_account_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.create_account_v1(jsonb) TO authenticated;

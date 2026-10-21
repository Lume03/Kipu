-- Owner-approved correction, 2026-10-21. Does not rewrite balances or historical movements.
-- A wallet may present the balance of one compatible bank account, but never
-- creates a second financial balance or ledger effect.
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS linked_account_id uuid;

CREATE INDEX IF NOT EXISTS idx_accounts_user_linked_account
    ON public.accounts(user_id, linked_account_id)
    WHERE linked_account_id IS NOT NULL;

-- A linked account must belong to the same owner as its wallet.
CREATE UNIQUE INDEX IF NOT EXISTS accounts_user_id_id_key ON public.accounts(user_id, id);
ALTER TABLE public.accounts
    DROP CONSTRAINT IF EXISTS accounts_linked_account_owner_fkey;
ALTER TABLE public.accounts
    ADD CONSTRAINT accounts_linked_account_owner_fkey
    FOREIGN KEY (user_id, linked_account_id)
    REFERENCES public.accounts(user_id, id);

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

-- Shared quota identity: bank, debit card and affiliated wallet use one slot.
CREATE OR REPLACE FUNCTION private.instrument_quota_groups(p_user_id uuid)
RETURNS TABLE(instrument_id uuid, quota_id uuid)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $groups$
  SELECT a.id, COALESCE(parent.id, a.id)
  FROM public.accounts a
  LEFT JOIN public.accounts parent ON parent.id = a.linked_account_id AND parent.user_id = a.user_id
    AND parent.account_type IN ('SAVINGS','BANK') AND NOT parent.is_archived AND parent.deleted_at IS NULL
  WHERE a.user_id = p_user_id AND NOT a.is_archived AND a.deleted_at IS NULL
    AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY')
  UNION ALL
  SELECT c.id, CASE WHEN NOT c.is_credit THEN COALESCE(parent.id, c.id) ELSE c.id END
  FROM public.cards c
  LEFT JOIN public.accounts parent ON parent.id = c.account_id AND parent.user_id = c.user_id
    AND parent.account_type IN ('SAVINGS','BANK') AND NOT parent.is_archived AND parent.deleted_at IS NULL
  WHERE c.user_id = p_user_id AND NOT c.is_archived AND c.deleted_at IS NULL;
$groups$;
REVOKE ALL ON FUNCTION private.instrument_quota_groups(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.instrument_quota_groups(uuid) TO plan_quota_selection_executor;
GRANT EXECUTE ON FUNCTION private.is_user_pro(uuid) TO plan_quota_selection_executor;

CREATE OR REPLACE FUNCTION private.is_instrument_quota_blocked(p_user_id uuid, p_current_count integer)
RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $quota$
BEGIN
  IF p_user_id IS NULL OR auth.uid() IS DISTINCT FROM p_user_id THEN
    RAISE EXCEPTION 'FORBIDDEN: Instrument quota owner mismatch' USING ERRCODE = '42501';
  END IF;
  RETURN (SELECT count(DISTINCT quota_id) FROM private.instrument_quota_groups(p_user_id)) >= 4
    AND NOT private.is_user_pro(p_user_id);
END;
$quota$;
REVOKE ALL ON FUNCTION private.is_instrument_quota_blocked(uuid, integer) FROM PUBLIC, anon, authenticated, service_role;

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
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('instrument-quota:' || v_user_id::text, 0));
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
    SELECT * INTO v_existing_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_existing_receipt.request_hash = v_request_hash THEN RETURN v_existing_receipt.response_payload; END IF;
        RAISE EXCEPTION 'OPERATION_COLLISION: Operation ID reused with differing payload';
    END IF;

    IF v_linked_account_id IS NOT NULL THEN
        v_required_preset := CASE v_preset_id WHEN 'YAPE' THEN 'BCP' WHEN 'PLIN' THEN 'BBVA' ELSE NULL END;
        IF v_type <> 'WALLET' OR v_required_preset IS NULL OR v_initial_balance <> 0 OR NOT EXISTS (
            SELECT 1 FROM public.accounts a
            WHERE a.id = v_linked_account_id AND a.user_id = v_user_id
              AND a.account_type IN ('SAVINGS', 'BANK') AND NOT a.is_archived
              AND a.currency_code = v_currency AND a.preset_id = v_required_preset
        ) THEN
            RAISE EXCEPTION 'INVALID_WALLET_AFFILIATION: Wallet requires a compatible active bank account and zero own balance';
        END IF;
    END IF;

    IF v_type <> 'CASH' AND v_linked_account_id IS NULL THEN
        SELECT ((SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
                (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)) INTO v_computable_count;
        IF private.is_instrument_quota_blocked(v_user_id, v_computable_count) THEN RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier'; END IF;
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

-- Preserve the deployed RPC's idempotency, validation and projection logic.
DO $patch$
DECLARE definition text;
BEGIN
  definition := pg_catalog.pg_get_functiondef('public.register_card_v1(jsonb)'::regprocedure);
  IF position('IF private.is_instrument_quota_blocked(v_user_id, v_computable_count) THEN' in definition) = 0 THEN
    RAISE EXCEPTION 'Unexpected register_card_v1 quota guard';
  END IF;
  definition := replace(definition, 'IF private.is_instrument_quota_blocked(v_user_id, v_computable_count) THEN',
    'IF v_type <> ''DEBIT'' AND private.is_instrument_quota_blocked(v_user_id, v_computable_count) THEN');
  definition := replace(definition, '    v_operation_id :=',
    '    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(''instrument-quota:'' || v_user_id::text, 0));' || chr(10) || '    v_operation_id :=');
  EXECUTE definition;

  definition := pg_catalog.pg_get_functiondef('private.is_plan_quota_resource_locked(uuid,text,uuid)'::regprocedure);
  definition := replace(definition, '    IF feature = ''INSTRUMENTS'' THEN', $branch$
    IF feature = 'INSTRUMENTS' THEN
      IF private.is_user_pro(p_user_id) THEN RETURN false; END IF;
      SELECT g.quota_id INTO target_id FROM private.instrument_quota_groups(p_user_id) g WHERE g.instrument_id = p_resource_id;
      IF target_id IS NULL THEN RETURN false; END IF;
      IF (SELECT count(DISTINCT g.quota_id) FROM private.instrument_quota_groups(p_user_id) g) <= 4 THEN RETURN false; END IF;
      RETURN NOT EXISTS (
        SELECT 1 FROM private.plan_quota_selection_items i
        JOIN private.instrument_quota_groups(p_user_id) g ON g.instrument_id = i.resource_id
        WHERE i.user_id = p_user_id AND i.feature_key = feature AND g.quota_id = target_id
      );
    END IF;
    IF feature = 'INSTRUMENTS' THEN$branch$);
  EXECUTE definition;
END;
$patch$;

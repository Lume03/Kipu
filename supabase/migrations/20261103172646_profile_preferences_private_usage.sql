-- QA-S9-01: preserve least-privilege RPC roles without requiring grants on auth.
BEGIN;
GRANT USAGE ON SCHEMA private TO profile_preferences_executor, profile_bootstrap_executor;
-- The postgres owner can call auth.uid(); executors receive only the current subject,
-- never arbitrary user lookup or access to auth tables. This schema is not exposed.
CREATE FUNCTION private.profile_session_uid() RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $$ SELECT auth.uid() $$;
ALTER FUNCTION private.profile_session_uid() OWNER TO postgres;
REVOKE ALL ON FUNCTION private.profile_session_uid() FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.profile_session_uid() TO profile_preferences_executor, profile_bootstrap_executor;

-- Preserve signature, dedicated owner, RLS and revision/idempotency protocol.
CREATE OR REPLACE FUNCTION public.update_profile_preferences(
    p_operation_id uuid,
    p_expected_revision bigint,
    p_payload jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := private.profile_session_uid();
    v_now timestamptz := clock_timestamp();
    v_hash bytea;
    v_existing_receipt private.profile_preference_receipts%ROWTYPE;
    v_profile public.profiles%ROWTYPE;
    v_result jsonb;
    v_new_revision bigint;
    v_month_start smallint;
    v_currency_code text;
    v_hide_balances boolean;
    v_theme_mode text;
    v_display_name text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Canonical SHA-256 hash
    v_hash := sha256(convert_to('v1\n' || p_operation_id::text || '\n' || p_expected_revision::text || '\n' || p_payload::text, 'UTF8'));

    -- Check duplicate receipt
    SELECT * INTO v_existing_receipt
    FROM private.profile_preference_receipts
    WHERE user_id = v_user_id AND operation_id = p_operation_id;

    IF FOUND THEN
        IF v_existing_receipt.payload_hash = v_hash THEN
            RETURN jsonb_build_object(
                'result', 'DUPLICATE',
                'accepted_revision', v_existing_receipt.resulting_revision,
                'data', v_existing_receipt.result
            );
        ELSE
            RETURN jsonb_build_object('result', 'OPERATION_COLLISION');
        END IF;
    END IF;

    -- Lock profile row
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE user_id = v_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('result', 'NOT_FOUND');
    END IF;

    -- Optimistic revision check
    IF v_profile.revision != p_expected_revision THEN
        v_result := jsonb_build_object(
            'result', 'CONFLICT',
            'current_revision', v_profile.revision,
            'current_profile', row_to_json(v_profile)
        );
        INSERT INTO private.profile_preference_receipts (user_id, operation_id, payload_hash, result, resulting_revision, created_at)
        VALUES (v_user_id, p_operation_id, v_hash, v_result, v_profile.revision, v_now);
        RETURN v_result;
    END IF;

    -- Extract values from payload
    v_month_start := COALESCE((p_payload->>'month_start')::smallint, v_profile.month_start);
    v_currency_code := COALESCE(p_payload->>'currency_code', v_profile.currency_code);
    v_hide_balances := COALESCE((p_payload->>'hide_balances')::boolean, v_profile.hide_balances);
    v_theme_mode := COALESCE(p_payload->>'theme_mode', v_profile.theme_mode);
    v_display_name := COALESCE(p_payload->>'display_name', v_profile.display_name);

    IF v_month_start < 1 OR v_month_start > 28 THEN
        RAISE EXCEPTION 'Invalid month_start %: must be between 1 and 28', v_month_start;
    END IF;

    v_new_revision := v_profile.revision + 1;

    UPDATE public.profiles
    SET display_name = v_display_name,
        currency_code = v_currency_code,
        month_start = v_month_start,
        hide_balances = v_hide_balances,
        theme_mode = v_theme_mode,
        revision = v_new_revision,
        updated_at = v_now
    WHERE user_id = v_user_id;

    v_result := jsonb_build_object(
        'result', 'APPLIED',
        'accepted_revision', v_new_revision,
        'updated_at', v_now
    );

    INSERT INTO private.profile_preference_receipts (user_id, operation_id, payload_hash, result, resulting_revision, created_at)
    VALUES (v_user_id, p_operation_id, v_hash, v_result, v_new_revision, v_now);

    RETURN v_result;
END;
$$;


CREATE OR REPLACE FUNCTION public.ensure_profile()
RETURNS public.profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := private.profile_session_uid();
    v_profile public.profiles%ROWTYPE;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    INSERT INTO public.profiles (user_id)
    VALUES (v_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT * INTO v_profile
    FROM public.profiles
    WHERE user_id = v_user_id;

    RETURN v_profile;
END;
$$;


COMMIT;

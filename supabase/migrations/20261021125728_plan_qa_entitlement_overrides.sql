-- QA plan overrides are inert unless the database-wide test gate is explicitly enabled.
CREATE TABLE internal.test_plan_override_gate (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    enabled boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by text NOT NULL DEFAULT current_user,
    reason text NOT NULL DEFAULT 'initially disabled'
);

INSERT INTO internal.test_plan_override_gate(singleton, enabled, reason)
VALUES (true, false, 'QA test-plan overrides default off');

CREATE TABLE internal.test_plan_overrides (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    tier text NOT NULL CHECK (tier IN ('FREE', 'PREMIUM')),
    expires_at timestamptz NOT NULL,
    reason text NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by text NOT NULL DEFAULT current_user,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by text NOT NULL DEFAULT current_user
);
CREATE INDEX test_plan_overrides_expiry_idx ON internal.test_plan_overrides(expires_at);

CREATE TABLE internal.test_plan_override_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id uuid NOT NULL,
    action text NOT NULL CHECK (action IN ('SET_FREE', 'SET_PREMIUM', 'RESET', 'GATE_ENABLED', 'GATE_DISABLED')),
    tier text CHECK (tier IS NULL OR tier IN ('FREE', 'PREMIUM')),
    expires_at timestamptz,
    reason text NOT NULL,
    actor text NOT NULL DEFAULT current_user,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE internal.test_plan_override_gate ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.test_plan_override_gate FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.test_plan_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.test_plan_overrides FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.test_plan_override_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.test_plan_override_audit FORCE ROW LEVEL SECURITY;
REVOKE ALL ON internal.test_plan_override_gate, internal.test_plan_overrides,
    internal.test_plan_override_audit FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON SEQUENCE internal.test_plan_override_audit_id_seq FROM PUBLIC, anon, authenticated, service_role;

-- Replace the existing projection without changing its public column contract. Commercial
-- entitlement is evaluated from billing_purchases directly; the override only wins while
-- both the global gate and its per-user expiry are active.
DO $feature_access_view$
DECLARE
    v_verb text := CASE WHEN pg_catalog.to_regclass('public.v_feature_access') IS NULL
                        THEN 'CREATE VIEW' ELSE 'CREATE OR REPLACE VIEW' END;
BEGIN
    EXECUTE v_verb || $view$
        public.v_feature_access AS
        SELECT u.id AS user_id,
               CASE WHEN qa.enabled AND override.user_id IS NOT NULL
                         THEN CASE WHEN override.tier = 'PREMIUM' THEN 'PRO'::text ELSE 'FREE'::text END
                    WHEN commercial.is_premium THEN 'PRO'::text ELSE 'FREE'::text END AS plan_tier,
               CASE WHEN (qa.enabled AND override.tier = 'PREMIUM') OR
                              (NOT (qa.enabled AND override.user_id IS NOT NULL) AND commercial.is_premium)
                    THEN 5 ELSE 1 END AS max_devices,
               CASE WHEN (qa.enabled AND override.tier = 'PREMIUM') OR
                              (NOT (qa.enabled AND override.user_id IS NOT NULL) AND commercial.is_premium)
                    THEN 100 ELSE 2 END AS max_budgets,
               CASE WHEN (qa.enabled AND override.tier = 'PREMIUM') OR
                              (NOT (qa.enabled AND override.user_id IS NOT NULL) AND commercial.is_premium)
                    THEN 100 ELSE 2 END AS max_obligations
        FROM auth.users AS u
        CROSS JOIN (SELECT enabled FROM internal.test_plan_override_gate WHERE singleton) AS qa
        LEFT JOIN internal.test_plan_overrides AS override
               ON override.user_id = u.id AND override.expires_at > now()
        CROSS JOIN LATERAL (
            SELECT EXISTS (
                SELECT 1
                FROM public.billing_purchases AS bp
                WHERE bp.user_id = u.id
                  AND bp.entitlement_state = 'ACTIVE'
                  AND (bp.expires_at IS NULL OR bp.expires_at > now())
            ) AS is_premium
        ) AS commercial
    $view$;
END;
$feature_access_view$;

-- All server quota guards use this shared tier projection, including the direct
-- billing lookup previously used by category-quota commands.
CREATE OR REPLACE FUNCTION private.is_user_pro(p_user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
    SELECT COALESCE((
        SELECT fa.plan_tier = 'PRO'
        FROM public.v_feature_access AS fa
        WHERE fa.user_id = p_user_id
    ), false);
$function$;
REVOKE ALL ON FUNCTION private.is_user_pro(uuid) FROM PUBLIC, anon, authenticated, service_role;

-- Legacy account/card registration RPCs had a hard-coded four-instrument limit.
-- Patch only their guard expression, preserving the rest of each RPC byte-for-byte.
CREATE OR REPLACE FUNCTION private.is_instrument_quota_blocked(p_user_id uuid, p_current_count integer)
RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_user_id IS NULL OR p_current_count IS NULL OR p_current_count < 0
       OR auth.uid() IS DISTINCT FROM p_user_id THEN
        RAISE EXCEPTION 'FORBIDDEN: Instrument quota owner mismatch' USING ERRCODE = '42501';
    END IF;
    RETURN p_current_count >= 4 AND NOT private.is_user_pro(p_user_id);
END;
$function$;
REVOKE ALL ON FUNCTION private.is_instrument_quota_blocked(uuid, integer) FROM PUBLIC, anon, authenticated, service_role;

DO $patch_instrument_quota_guards$
DECLARE
    v_signature text;
    v_definition text;
    v_guard text := 'IF v_computable_count >= 4 THEN';
    v_shared_guard text := 'IF private.is_instrument_quota_blocked(v_user_id, v_computable_count) THEN';
BEGIN
    FOREACH v_signature IN ARRAY ARRAY[
        'public.create_account_v1(jsonb)',
        'public.register_card_v1(jsonb)'
    ] LOOP
        IF pg_catalog.to_regprocedure(v_signature) IS NULL THEN
            RAISE EXCEPTION 'Required instrument quota RPC is missing: %', v_signature;
        END IF;
        v_definition := pg_catalog.pg_get_functiondef(pg_catalog.to_regprocedure(v_signature));
        IF (length(v_definition) - length(replace(v_definition, v_guard, ''))) / length(v_guard) <> 1 THEN
            RAISE EXCEPTION 'Expected exactly one legacy four-instrument guard in %', v_signature;
        END IF;
        EXECUTE replace(v_definition, v_guard, v_shared_guard);
    END LOOP;
END;
$patch_instrument_quota_guards$;

-- Server-only projection for refresh-entitlement. User identity must already have been
-- validated against Supabase Auth by the Edge Function. This function never writes billing.
CREATE OR REPLACE FUNCTION public.get_effective_plan_for_refresh(p_user_id uuid)
RETURNS TABLE(effective_tier text, source text, expires_at timestamptz, test_plans_enabled boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
    SELECT CASE WHEN qa.enabled AND override.user_id IS NOT NULL THEN override.tier
                WHEN commercial.is_premium THEN 'PREMIUM'
                ELSE 'FREE' END,
           CASE WHEN qa.enabled AND override.user_id IS NOT NULL THEN 'TEST_OVERRIDE' ELSE 'COMMERCIAL' END,
           CASE WHEN qa.enabled AND override.user_id IS NOT NULL THEN override.expires_at
                WHEN commercial.is_lifetime THEN NULL::timestamptz ELSE commercial.expires_at END,
           qa.enabled
    FROM (SELECT true AS singleton) AS one
    CROSS JOIN (SELECT enabled FROM internal.test_plan_override_gate WHERE singleton) AS qa
    LEFT JOIN internal.test_plan_overrides AS override
           ON override.user_id = p_user_id AND override.expires_at > now()
    CROSS JOIN LATERAL (
        SELECT bool_or(bp.expires_at IS NULL AND product.plan_type = 'PRO_LIFETIME') AS is_lifetime,
                max(bp.expires_at) FILTER (WHERE bp.expires_at > now()) AS expires_at,
               bool_or(bp.expires_at IS NULL OR bp.expires_at > now()) AS is_premium
        FROM public.billing_purchases AS bp
        JOIN public.billing_products AS product ON product.id = bp.product_id
        WHERE bp.user_id = p_user_id
          AND bp.entitlement_state = 'ACTIVE'
    ) AS commercial
    WHERE p_user_id IS NOT NULL;
$function$;
REVOKE ALL ON FUNCTION public.get_effective_plan_for_refresh(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_effective_plan_for_refresh(uuid) TO service_role;

-- Operator-only helpers. The owner is postgres; no client or service role can execute them.
CREATE OR REPLACE FUNCTION internal.set_test_plans_enabled(p_enabled boolean, p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_enabled IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'A reason of 1..500 characters is required' USING ERRCODE = '22023';
    END IF;
    UPDATE internal.test_plan_override_gate
       SET enabled = p_enabled, updated_at = now(), updated_by = session_user, reason = p_reason
     WHERE singleton;
    INSERT INTO internal.test_plan_override_audit(user_id, action, reason, actor)
    VALUES ('00000000-0000-0000-0000-000000000000',
            CASE WHEN p_enabled THEN 'GATE_ENABLED' ELSE 'GATE_DISABLED' END,
            p_reason, session_user);
END;
$function$;

CREATE OR REPLACE FUNCTION internal.set_test_plan_override(
    p_user_id uuid, p_tier text, p_expires_at timestamptz, p_reason text
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_user_id IS NULL OR p_tier IS NULL OR p_tier NOT IN ('FREE', 'PREMIUM')
       OR p_expires_at IS NULL OR p_expires_at <= now()
       OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'Invalid QA plan override' USING ERRCODE = '22023';
    END IF;
    INSERT INTO internal.test_plan_overrides(user_id, tier, expires_at, reason, created_by, updated_by)
    VALUES (p_user_id, p_tier, p_expires_at, p_reason, session_user, session_user)
    ON CONFLICT (user_id) DO UPDATE SET tier = excluded.tier, expires_at = excluded.expires_at,
        reason = excluded.reason, updated_at = now(), updated_by = session_user;
    INSERT INTO internal.test_plan_override_audit(user_id, action, tier, expires_at, reason, actor)
    VALUES (p_user_id, CASE WHEN p_tier = 'FREE' THEN 'SET_FREE' ELSE 'SET_PREMIUM' END,
            p_tier, p_expires_at, p_reason, session_user);
END;
$function$;

CREATE OR REPLACE FUNCTION internal.reset_test_plan_override(p_user_id uuid, p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_user_id IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'A user and reason are required' USING ERRCODE = '22023';
    END IF;
    DELETE FROM internal.test_plan_overrides WHERE user_id = p_user_id;
    INSERT INTO internal.test_plan_override_audit(user_id, action, reason, actor)
    VALUES (p_user_id, 'RESET', p_reason, session_user);
END;
$function$;

REVOKE ALL ON FUNCTION internal.set_test_plans_enabled(boolean, text),
    internal.set_test_plan_override(uuid, text, timestamptz, text),
    internal.reset_test_plan_override(uuid, text) FROM PUBLIC, anon, authenticated, service_role;

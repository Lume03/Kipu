-- Permanent operator-managed Premium. No payment, QA gate, or client-write path.
CREATE TABLE internal.developer_entitlements (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    reason text NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    granted_at timestamptz NOT NULL DEFAULT now(),
    granted_by text NOT NULL DEFAULT current_user
);
CREATE TABLE internal.developer_entitlement_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id uuid NOT NULL,
    action text NOT NULL CHECK (action IN ('GRANT', 'REVOKE')),
    reason text NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    actor text NOT NULL DEFAULT current_user,
    occurred_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE internal.developer_entitlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.developer_entitlements FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.developer_entitlement_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.developer_entitlement_audit FORCE ROW LEVEL SECURITY;
REVOKE ALL ON internal.developer_entitlements, internal.developer_entitlement_audit
    FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON SEQUENCE internal.developer_entitlement_audit_id_seq
    FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION internal.grant_developer_plan(p_user_id uuid, p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_user_id IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'A user and reason of 1..500 characters are required' USING ERRCODE = '22023';
    END IF;
    INSERT INTO internal.developer_entitlements(user_id, reason, granted_by)
    VALUES (p_user_id, p_reason, session_user)
    ON CONFLICT (user_id) DO UPDATE SET reason = excluded.reason,
        granted_at = now(), granted_by = session_user;
    INSERT INTO internal.developer_entitlement_audit(user_id, action, reason, actor)
    VALUES (p_user_id, 'GRANT', p_reason, session_user);
END;
$function$;
CREATE OR REPLACE FUNCTION internal.revoke_developer_plan(p_user_id uuid, p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_user_id IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'A user and reason of 1..500 characters are required' USING ERRCODE = '22023';
    END IF;
    DELETE FROM internal.developer_entitlements WHERE user_id = p_user_id;
    INSERT INTO internal.developer_entitlement_audit(user_id, action, reason, actor)
    VALUES (p_user_id, 'REVOKE', p_reason, session_user);
END;
$function$;
REVOKE ALL ON FUNCTION internal.grant_developer_plan(uuid, text),
    internal.revoke_developer_plan(uuid, text) FROM PUBLIC, anon, authenticated, service_role;

-- Preserve the legacy view columns and ACLs; private.is_user_pro reads this view.
DO $feature_access_view$
DECLARE
    v_verb text := CASE WHEN pg_catalog.to_regclass('public.v_feature_access') IS NULL
                        THEN 'CREATE VIEW' ELSE 'CREATE OR REPLACE VIEW' END;
BEGIN
    EXECUTE v_verb || $view$
        public.v_feature_access AS
        SELECT u.id AS user_id,
               CASE WHEN developer.user_id IS NOT NULL THEN 'PRO'::text
                    WHEN qa.enabled AND override.user_id IS NOT NULL
                         THEN CASE WHEN override.tier = 'PREMIUM' THEN 'PRO'::text ELSE 'FREE'::text END
                    WHEN commercial.is_premium THEN 'PRO'::text ELSE 'FREE'::text END AS plan_tier,
               CASE WHEN developer.user_id IS NOT NULL OR (qa.enabled AND override.tier = 'PREMIUM') OR
                              (NOT (qa.enabled AND override.user_id IS NOT NULL) AND commercial.is_premium)
                    THEN 5 ELSE 1 END AS max_devices,
               CASE WHEN developer.user_id IS NOT NULL OR (qa.enabled AND override.tier = 'PREMIUM') OR
                              (NOT (qa.enabled AND override.user_id IS NOT NULL) AND commercial.is_premium)
                    THEN 100 ELSE 2 END AS max_budgets,
               CASE WHEN developer.user_id IS NOT NULL OR (qa.enabled AND override.tier = 'PREMIUM') OR
                              (NOT (qa.enabled AND override.user_id IS NOT NULL) AND commercial.is_premium)
                    THEN 100 ELSE 2 END AS max_obligations
        FROM auth.users AS u
        LEFT JOIN internal.developer_entitlements AS developer ON developer.user_id = u.id
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


CREATE OR REPLACE FUNCTION public.get_effective_plan_for_refresh(p_user_id uuid)
RETURNS TABLE(effective_tier text, source text, expires_at timestamptz, test_plans_enabled boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
    SELECT CASE WHEN developer.user_id IS NOT NULL THEN 'PREMIUM'
                WHEN qa.enabled AND override.user_id IS NOT NULL THEN override.tier
                WHEN commercial.is_premium THEN 'PREMIUM'
                ELSE 'FREE' END,
           CASE WHEN developer.user_id IS NOT NULL THEN 'DEVELOPER'
                WHEN qa.enabled AND override.user_id IS NOT NULL THEN 'TEST_OVERRIDE' ELSE 'COMMERCIAL' END,
           CASE WHEN developer.user_id IS NOT NULL THEN NULL::timestamptz
                WHEN qa.enabled AND override.user_id IS NOT NULL THEN override.expires_at
                WHEN commercial.is_lifetime THEN NULL::timestamptz ELSE commercial.expires_at END,
           qa.enabled
    FROM (SELECT true AS singleton) AS one
    LEFT JOIN internal.developer_entitlements AS developer ON developer.user_id = p_user_id
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


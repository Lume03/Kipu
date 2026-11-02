-- D7 (PO 2026-11-02): server-controlled development plans, isolated from purchases.
BEGIN;

ALTER TABLE internal.test_plan_overrides ADD COLUMN plan_type text;
UPDATE internal.test_plan_overrides
SET plan_type = CASE WHEN tier = 'FREE' THEN 'FREE' ELSE 'PRO_MONTHLY' END;
ALTER TABLE internal.test_plan_overrides
    ALTER COLUMN plan_type SET NOT NULL,
    ALTER COLUMN expires_at DROP NOT NULL,
    ADD CONSTRAINT test_plan_overrides_plan_type_check
        CHECK (plan_type IN ('FREE','PRO_MONTHLY','PRO_ANNUAL','PRO_LIFETIME')),
    ADD CONSTRAINT test_plan_overrides_expiry_check
        CHECK (expires_at IS NOT NULL OR plan_type IN ('FREE','PRO_LIFETIME')),
    ADD CONSTRAINT test_plan_overrides_tier_plan_check
        CHECK ((tier = 'FREE') = (plan_type = 'FREE'));

ALTER TABLE internal.test_plan_override_audit
    ADD COLUMN plan_type text CHECK (plan_type IN ('FREE','PRO_MONTHLY','PRO_ANNUAL','PRO_LIFETIME')),
    DROP CONSTRAINT test_plan_override_audit_action_check,
    ADD CONSTRAINT test_plan_override_audit_action_check
        CHECK (action IN ('SET_FREE','SET_PREMIUM','RESET','GATE_ENABLED','GATE_DISABLED','SET_PLAN'));

-- Dedicated receipts avoid coupling a non-financial command to financial command payloads.
CREATE TABLE internal.dev_plan_receipts (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation_id uuid NOT NULL,
    plan_type text NOT NULL CHECK (plan_type IN ('FREE','PRO_MONTHLY','PRO_ANNUAL','PRO_LIFETIME')),
    result jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, operation_id)
);
ALTER TABLE internal.dev_plan_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.dev_plan_receipts FORCE ROW LEVEL SECURITY;
REVOKE ALL ON internal.dev_plan_receipts FROM PUBLIC, anon, authenticated, service_role;

CREATE FUNCTION public.dev_plan_mode()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
    SELECT auth.uid() IS NOT NULL AND COALESCE((
        SELECT enabled FROM internal.test_plan_override_gate WHERE singleton
    ), false);
$function$;
REVOKE ALL ON FUNCTION public.dev_plan_mode() FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.dev_plan_mode() TO authenticated;

CREATE FUNCTION public.dev_activate_plan(p_operation_id uuid, p_plan_type text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
DECLARE
    v_user uuid := auth.uid();
    v_enabled boolean;
    v_receipt internal.dev_plan_receipts%ROWTYPE;
    v_expiry timestamptz;
    v_result jsonb;
BEGIN
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;
    -- Hold the gate until commit: disabling it cannot race a successful activation.
    SELECT enabled INTO v_enabled FROM internal.test_plan_override_gate WHERE singleton FOR SHARE;
    IF NOT COALESCE(v_enabled, false) THEN
        RETURN jsonb_build_object('outcome','REJECTED','code','DEV_MODE_DISABLED');
    END IF;
    IF p_operation_id IS NULL OR p_plan_type IS NULL OR
       p_plan_type NOT IN ('FREE','PRO_MONTHLY','PRO_ANNUAL','PRO_LIFETIME') THEN
        RETURN jsonb_build_object('outcome','REJECTED','code','INVALID_REQUEST');
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('dev_plan:' || v_user::text, 0));
    SELECT * INTO v_receipt FROM internal.dev_plan_receipts
      WHERE user_id = v_user AND operation_id = p_operation_id;
    IF FOUND THEN
        IF v_receipt.plan_type <> p_plan_type THEN
            RETURN jsonb_build_object('outcome','CONFLICT','code','OPERATION_CONFLICT');
        END IF;
        RETURN v_receipt.result;
    END IF;
    v_expiry := CASE p_plan_type
        WHEN 'PRO_MONTHLY' THEN now() + interval '30 days'
        WHEN 'PRO_ANNUAL' THEN now() + interval '365 days'
        ELSE NULL END;
    -- A persistent FREE override also masks commercial access while the gate is on.
    INSERT INTO internal.test_plan_overrides(user_id,tier,plan_type,expires_at,reason,created_by,updated_by)
    VALUES (v_user, CASE WHEN p_plan_type = 'FREE' THEN 'FREE' ELSE 'PREMIUM' END,
        p_plan_type,v_expiry,'D7 development plan selection',v_user::text,v_user::text)
    ON CONFLICT (user_id) DO UPDATE SET tier = excluded.tier, plan_type = excluded.plan_type,
        expires_at = excluded.expires_at, reason = excluded.reason,
        updated_at = now(), updated_by = v_user::text;
    INSERT INTO internal.test_plan_override_audit(user_id,action,tier,plan_type,expires_at,reason,actor)
    VALUES (v_user,'SET_PLAN',CASE WHEN p_plan_type = 'FREE' THEN 'FREE' ELSE 'PREMIUM' END,
        p_plan_type,v_expiry,'D7 development plan selection',v_user::text);
    v_result := jsonb_build_object('outcome','APPLIED','planType',p_plan_type,
        'operationId',p_operation_id,'expiresAt',v_expiry);
    INSERT INTO internal.dev_plan_receipts(user_id,operation_id,plan_type,result)
    VALUES (v_user,p_operation_id,p_plan_type,v_result);
    RETURN v_result;
END;
$function$;
REVOKE ALL ON FUNCTION public.dev_activate_plan(uuid,text) FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.dev_activate_plan(uuid,text) TO authenticated;

CREATE FUNCTION internal.set_dev_plan_mode(p_enabled boolean, p_reason text)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = ''
AS $function$
    SELECT internal.set_test_plans_enabled(p_enabled,p_reason);
$function$;
REVOKE ALL ON FUNCTION internal.set_dev_plan_mode(boolean,text) FROM PUBLIC, anon, authenticated, service_role;

-- Preserve the existing operator helper and its bounded FREE/PREMIUM semantics.
CREATE OR REPLACE FUNCTION internal.set_test_plan_override(
    p_user_id uuid, p_tier text, p_expires_at timestamptz, p_reason text
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
    IF p_user_id IS NULL OR p_tier IS NULL OR p_tier NOT IN ('FREE','PREMIUM')
       OR p_expires_at IS NULL OR p_expires_at <= now()
       OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'Invalid QA plan override' USING ERRCODE = '22023';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('dev_plan:' || p_user_id::text, 0));
    INSERT INTO internal.test_plan_overrides(user_id,tier,plan_type,expires_at,reason,created_by,updated_by)
    VALUES (p_user_id,p_tier,CASE WHEN p_tier = 'FREE' THEN 'FREE' ELSE 'PRO_MONTHLY' END,
        p_expires_at,p_reason,session_user,session_user)
    ON CONFLICT (user_id) DO UPDATE SET tier = excluded.tier, plan_type = excluded.plan_type,
        expires_at = excluded.expires_at, reason = excluded.reason, updated_at = now(), updated_by = session_user;
    INSERT INTO internal.test_plan_override_audit(user_id,action,tier,plan_type,expires_at,reason,actor)
    VALUES (p_user_id,CASE WHEN p_tier = 'FREE' THEN 'SET_FREE' ELSE 'SET_PREMIUM' END,
        p_tier,CASE WHEN p_tier = 'FREE' THEN 'FREE' ELSE 'PRO_MONTHLY' END,p_expires_at,p_reason,session_user);
END;
$function$;
REVOKE ALL ON FUNCTION internal.set_test_plan_override(uuid,text,timestamptz,text)
    FROM PUBLIC, anon, authenticated, service_role;

-- Keep commercial PURCHASED/ACTIVE and recurring-expiry rules from 20261102013246.
-- Only widen the override join to admit permanent FREE and Lifetime overrides.
CREATE OR REPLACE VIEW public.v_feature_access AS
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
       ON override.user_id = u.id AND (override.expires_at > now() OR
           (override.expires_at IS NULL AND override.plan_type IN ('FREE','PRO_LIFETIME')))
CROSS JOIN LATERAL (
    SELECT EXISTS (
        SELECT 1 FROM public.billing_purchases AS bp
        JOIN public.billing_products AS product ON product.id = bp.product_id
        WHERE bp.user_id = u.id AND bp.entitlement_state = 'ACTIVE' AND bp.purchase_state = 'PURCHASED'
          AND ((product.plan_type = 'PRO_LIFETIME' AND bp.expires_at IS NULL)
               OR (product.plan_type IN ('PRO_MONTHLY','PRO_ANNUAL') AND bp.expires_at > now()))
    ) AS is_premium
) AS commercial;

CREATE OR REPLACE FUNCTION public.get_effective_plan_for_refresh(p_user_id uuid)
RETURNS TABLE(effective_tier text, source text, expires_at timestamptz, test_plans_enabled boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
    SELECT CASE WHEN developer.user_id IS NOT NULL THEN 'PREMIUM'
                WHEN qa.enabled AND override.user_id IS NOT NULL THEN override.tier
                WHEN commercial.is_premium THEN 'PREMIUM' ELSE 'FREE' END,
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
           ON override.user_id = p_user_id AND (override.expires_at > now() OR
               (override.expires_at IS NULL AND override.plan_type IN ('FREE','PRO_LIFETIME')))
    CROSS JOIN LATERAL (
        SELECT bool_or(bp.expires_at IS NULL AND product.plan_type = 'PRO_LIFETIME') AS is_lifetime,
               max(bp.expires_at) FILTER (WHERE bp.expires_at > now()) AS expires_at,
               bool_or((product.plan_type = 'PRO_LIFETIME' AND bp.expires_at IS NULL)
                       OR (product.plan_type IN ('PRO_MONTHLY','PRO_ANNUAL') AND bp.expires_at > now())) AS is_premium
        FROM public.billing_purchases AS bp
        JOIN public.billing_products AS product ON product.id = bp.product_id
        WHERE bp.user_id = p_user_id AND bp.entitlement_state = 'ACTIVE' AND bp.purchase_state = 'PURCHASED'
    ) AS commercial
    WHERE p_user_id IS NOT NULL;
$function$;
REVOKE ALL ON FUNCTION public.get_effective_plan_for_refresh(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_effective_plan_for_refresh(uuid) TO service_role;

-- Deliberately do not enable the gate. An operator must enable it with a reason.
COMMIT;

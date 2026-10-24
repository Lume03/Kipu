-- EP-PRE (HU-37/38): snapshot de alcance por periodo y cambios pendientes de presupuesto.
-- Migracion aditiva. NO aplicar al remoto en el Sprint 7 (research.md, gate G4).
--
-- Cierra la brecha entre Room y PostgreSQL:
--   * budget_periods.scope_category_id: alcance que rigio el periodo (NULL = TOTAL).
--   * budgets.pending_*: cambio de alcance/calendario que entra en vigor en la
--     siguiente vigencia (mismos nombres y semantica que BudgetEntity en Room).

---------------------------------------------------------------------------
-- 1. Snapshot de alcance por periodo.
---------------------------------------------------------------------------
ALTER TABLE public.budget_periods ADD COLUMN IF NOT EXISTS scope_category_id uuid;

-- Backfill: los periodos existentes se rigieron por el alcance actual del presupuesto
-- (mejor informacion disponible; el esquema anterior no guardaba el historico).
-- Se hace antes de crear el trigger de validacion.
UPDATE public.budget_periods bp
   SET scope_category_id = b.category_id
  FROM public.budgets b
 WHERE b.id = bp.budget_id
   AND b.user_id = bp.user_id
   AND bp.scope_category_id IS DISTINCT FROM b.category_id;

CREATE INDEX IF NOT EXISTS idx_budget_periods_scope_category
    ON public.budget_periods (scope_category_id) WHERE scope_category_id IS NOT NULL;

CREATE OR REPLACE FUNCTION internal.budget_periods_guard_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_cat_owner uuid;
    v_cat_deleted timestamptz;
BEGIN
    IF NEW.scope_category_id IS NULL THEN
        RETURN NEW;
    END IF;
    -- Reenvio idempotente del mismo snapshot: no se revalida (la categoria pudo borrarse despues).
    IF EXISTS (
        SELECT 1 FROM public.budget_periods p
        WHERE p.id = NEW.id AND p.scope_category_id IS NOT DISTINCT FROM NEW.scope_category_id
    ) THEN
        RETURN NEW;
    END IF;
    SELECT c.user_id, c.deleted_at INTO v_cat_owner, v_cat_deleted
    FROM public.categories c WHERE c.id = NEW.scope_category_id;
    IF NOT FOUND OR v_cat_deleted IS NOT NULL OR (v_cat_owner IS NOT NULL AND v_cat_owner <> NEW.user_id) THEN
        RAISE EXCEPTION 'INVALID_CATEGORY: Category is not available for this user';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION internal.budget_periods_guard_v1() FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS budget_periods_guard_v1 ON public.budget_periods;
CREATE TRIGGER budget_periods_guard_v1 BEFORE INSERT OR UPDATE ON public.budget_periods
    FOR EACH ROW EXECUTE FUNCTION internal.budget_periods_guard_v1();

---------------------------------------------------------------------------
-- 2. Cambios pendientes en budgets (equivalentes a pending_* de Room).
---------------------------------------------------------------------------
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS pending_scope_type text;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS pending_scope_category_id uuid;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS pending_schedule_type text;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS pending_range_start_on date;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS pending_range_end_on date;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS pending_effective_from date;

ALTER TABLE public.budgets DROP CONSTRAINT IF EXISTS budgets_pending_scope_check;
ALTER TABLE public.budgets ADD CONSTRAINT budgets_pending_scope_check CHECK (
    (pending_scope_type IS NULL AND pending_scope_category_id IS NULL)
    OR (pending_scope_type = 'TOTAL' AND pending_scope_category_id IS NULL)
    OR (pending_scope_type = 'CATEGORY' AND pending_scope_category_id IS NOT NULL)
);

ALTER TABLE public.budgets DROP CONSTRAINT IF EXISTS budgets_pending_schedule_check;
ALTER TABLE public.budgets ADD CONSTRAINT budgets_pending_schedule_check CHECK (
    (pending_schedule_type IS NULL AND pending_range_start_on IS NULL AND pending_range_end_on IS NULL)
    OR (pending_schedule_type IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY')
        AND pending_range_start_on IS NULL AND pending_range_end_on IS NULL)
    OR (pending_schedule_type = 'CUSTOM_RANGE'
        AND pending_range_start_on IS NOT NULL AND pending_range_end_on IS NOT NULL
        AND pending_range_end_on >= pending_range_start_on)
);

-- Un cambio pendiente exige fecha de entrada en vigor y al menos un componente.
ALTER TABLE public.budgets DROP CONSTRAINT IF EXISTS budgets_pending_effective_check;
ALTER TABLE public.budgets ADD CONSTRAINT budgets_pending_effective_check CHECK (
    (pending_effective_from IS NULL
        AND pending_scope_type IS NULL AND pending_schedule_type IS NULL)
    OR (pending_effective_from IS NOT NULL
        AND (pending_scope_type IS NOT NULL OR pending_schedule_type IS NOT NULL))
);

-- Misma regla de categoria que budgets.category_id, ahora tambien para pending_scope_category_id.
CREATE OR REPLACE FUNCTION internal.budgets_guard_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_cat_owner uuid;
    v_cat_deleted timestamptz;
    v_became_active boolean;
BEGIN
    -- 4.1 Un tombstone impide recrear el mismo presupuesto (comando atrasado de otro dispositivo).
    IF TG_OP = 'INSERT' AND EXISTS (
        SELECT 1 FROM public.budget_tombstones t WHERE t.budget_id = NEW.id AND t.user_id = NEW.user_id
    ) THEN
        RAISE EXCEPTION 'BUDGET_DELETED: Budget % was deleted and cannot be recreated', NEW.id;
    END IF;

    -- 4.2 status es la fuente; is_active se conserva por compatibilidad con cuotas y sync legado.
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'ACTIVE' OR NEW.is_active = false THEN
            NEW.status := 'FINISHED'; NEW.is_active := false;
        ELSE
            NEW.is_active := true;
        END IF;
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.is_active := (NEW.status = 'ACTIVE');
    ELSIF NEW.is_active IS DISTINCT FROM OLD.is_active THEN
        NEW.status := CASE WHEN NEW.is_active THEN 'ACTIVE' ELSE 'FINISHED' END;
    END IF;

    -- 4.3 La categoria debe ser propia (o de sistema) y no estar borrada.
    IF NEW.category_id IS NOT NULL AND (TG_OP = 'INSERT' OR NEW.category_id IS DISTINCT FROM OLD.category_id) THEN
        SELECT c.user_id, c.deleted_at INTO v_cat_owner, v_cat_deleted
        FROM public.categories c WHERE c.id = NEW.category_id;
        IF NOT FOUND OR v_cat_deleted IS NOT NULL OR (v_cat_owner IS NOT NULL AND v_cat_owner <> NEW.user_id) THEN
            RAISE EXCEPTION 'INVALID_CATEGORY: Category is not available for this user';
        END IF;
    END IF;
    IF NEW.pending_scope_category_id IS NOT NULL
       AND (TG_OP = 'INSERT' OR NEW.pending_scope_category_id IS DISTINCT FROM OLD.pending_scope_category_id) THEN
        SELECT c.user_id, c.deleted_at INTO v_cat_owner, v_cat_deleted
        FROM public.categories c WHERE c.id = NEW.pending_scope_category_id;
        IF NOT FOUND OR v_cat_deleted IS NOT NULL OR (v_cat_owner IS NOT NULL AND v_cat_owner <> NEW.user_id) THEN
            RAISE EXCEPTION 'INVALID_CATEGORY: Category is not available for this user';
        END IF;
    END IF;

    -- 4.4 Cupo Free: maximo 2 presupuestos ACTIVE no borrados.
    v_became_active := NEW.status = 'ACTIVE' AND NEW.deleted_at IS NULL
        AND (TG_OP = 'INSERT' OR OLD.status <> 'ACTIVE' OR OLD.deleted_at IS NOT NULL);
    IF v_became_active THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('kipu:budget-quota:' || NEW.user_id::text, 0));
        IF NOT private.is_user_pro(NEW.user_id) AND (
            SELECT pg_catalog.count(*) FROM public.budgets b
            WHERE b.user_id = NEW.user_id AND b.status = 'ACTIVE' AND b.deleted_at IS NULL AND b.id <> NEW.id
        ) >= 2 THEN
            RAISE EXCEPTION 'BUDGET_QUOTA_EXCEEDED: Free plan allows at most 2 active budgets';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION internal.budgets_guard_v1() FROM PUBLIC, anon, authenticated, service_role;

---------------------------------------------------------------------------
-- 3. get_budget_status: el alcance del periodo es su snapshot cuando el periodo existe
--    (NULL en el snapshot = TOTAL); sin periodo persistido rige el alcance actual.
---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_budget_status(
    p_budget_id uuid, p_on date DEFAULT NULL, p_time_zone text DEFAULT 'UTC'
)
RETURNS TABLE (
    budget_id uuid, status text, currency_code bpchar, period_starts_on date, period_ends_on date,
    limit_minor bigint, consumed_minor bigint, remaining_minor bigint, usage_bps integer
)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
    WITH RECURSIVE b AS (
        SELECT * FROM public.budgets WHERE id = p_budget_id AND deleted_at IS NULL
    ), bounds AS (
        SELECT b.id, bd.starts_on, bd.ends_on
        FROM b, LATERAL public.budget_period_bounds_v1(
            b.period_type::text,
            COALESCE(p_on, (pg_catalog.now() AT TIME ZONE p_time_zone)::date),
            b.range_start_on, b.range_end_on) bd
    ), period AS (
        SELECT bp.limit_minor, bp.scope_category_id
        FROM public.budget_periods bp, b, bounds
        WHERE bp.budget_id = b.id AND bp.starts_on = bounds.starts_on AND bp.ends_on = bounds.ends_on
    ), scope AS (
        SELECT CASE WHEN EXISTS (SELECT 1 FROM period)
                    THEN (SELECT p.scope_category_id FROM period p)
                    ELSE (SELECT b.category_id FROM b)
               END AS category_id
    ), tree AS (
        SELECT c.id FROM public.categories c, scope WHERE c.id = scope.category_id
        UNION
        SELECT c.id FROM public.categories c JOIN tree t ON c.parent_id = t.id
    ), eligible AS (
        SELECT t.id, t.amount_minor
        FROM public.transactions t, b, bounds, scope
        WHERE t.user_id = b.user_id
          AND t.transaction_type = 'EXPENSE'
          AND t.operation_kind IN ('STANDARD', 'CARD_PURCHASE')
          AND t.status IN ('CONFIRMED', 'REVISED')
          AND t.deleted_at IS NULL
          AND t.transfer_group_id IS NULL
          AND t.currency_code = b.currency_code
          AND (t.occurred_at AT TIME ZONE p_time_zone)::date >= bounds.starts_on
          AND (t.occurred_at AT TIME ZONE p_time_zone)::date < bounds.ends_on
          AND (scope.category_id IS NULL OR t.category_id IN (SELECT id FROM tree))
    ), refunded AS (
        SELECT e.id, pg_catalog.sum(r.amount_minor) AS amount_minor
        FROM eligible e
        JOIN public.transaction_links l
          ON l.link_type = 'REFUND' AND e.id IN (l.source_transaction_id, l.target_transaction_id)
        JOIN public.transactions r
          ON r.id = CASE WHEN l.source_transaction_id = e.id THEN l.target_transaction_id ELSE l.source_transaction_id END
         AND r.operation_kind = 'REFUND'
         AND r.status IN ('CONFIRMED', 'REVISED')
         AND r.deleted_at IS NULL
        JOIN b ON r.user_id = b.user_id AND r.currency_code = b.currency_code
        GROUP BY e.id
    ), net AS (
        SELECT COALESCE(pg_catalog.sum(e.amount_minor - LEAST(e.amount_minor, COALESCE(rf.amount_minor, 0))), 0)::bigint AS consumed
        FROM eligible e LEFT JOIN refunded rf ON rf.id = e.id
    )
    SELECT b.id, b.status, b.currency_code, bounds.starts_on, bounds.ends_on,
           COALESCE((SELECT p.limit_minor FROM period p), b.limit_minor),
           net.consumed,
           COALESCE((SELECT p.limit_minor FROM period p), b.limit_minor) - net.consumed,
           LEAST(2147483647, (net.consumed * 10000 / COALESCE((SELECT p.limit_minor FROM period p), b.limit_minor)))::integer
    FROM b
    JOIN bounds ON bounds.id = b.id
    CROSS JOIN net;
$$;
REVOKE ALL ON FUNCTION public.get_budget_status(uuid, date, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_budget_status(uuid, date, text) TO authenticated;

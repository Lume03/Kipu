-- EP-PRE (HU-36/37/38): presupuestos, vigencias, tombstone de borrado y consumo.
-- Migracion aditiva. NO aplicar al remoto en el Sprint 7 (research.md, gate G4).
--
-- Decisiones (research.md "Resolucion de gates - 2026-10-24"):
--   G1  DAILY / WEEKLY (lun-dom) / BIWEEKLY (1-15, 16-fin) / MONTHLY / CUSTOM_RANGE.
--       Intervalos [inicio, fin) sobre fechas civiles de la zona del dispositivo.
--   G5  Borrar = borrado fisico de budgets + budget_periods y tombstone en
--       public.budget_tombstones; nunca cascada hacia transactions/ledger/cuentas/categorias.
--   G6  Una moneda por presupuesto; solo cuenta gasto en esa moneda, sin conversion.

---------------------------------------------------------------------------
-- 1. Periodos: extension aditiva del enum.
--    CUSTOM (legado) se conserva y se trata como alias de CUSTOM_RANGE: no se
--    reescriben filas. Los clientes nuevos escriben CUSTOM_RANGE. Los nuevos
--    valores no se usan como literales en esta migracion (limitacion de
--    ALTER TYPE ... ADD VALUE dentro de una transaccion); los CHECK comparan ::text.
---------------------------------------------------------------------------
ALTER TYPE public.budget_period_type ADD VALUE IF NOT EXISTS 'DAILY';
ALTER TYPE public.budget_period_type ADD VALUE IF NOT EXISTS 'BIWEEKLY';
ALTER TYPE public.budget_period_type ADD VALUE IF NOT EXISTS 'CUSTOM_RANGE';

ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS range_start_on date;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS range_end_on date;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'ACTIVE';

-- Compatibilidad: is_active=false historico equivale a FINISHED. (Antes de crear triggers.)
UPDATE public.budgets SET status = 'FINISHED' WHERE is_active = false AND status = 'ACTIVE';

ALTER TABLE public.budgets DROP CONSTRAINT IF EXISTS budgets_status_check;
ALTER TABLE public.budgets ADD CONSTRAINT budgets_status_check CHECK (status IN ('ACTIVE', 'FINISHED'));

ALTER TABLE public.budgets DROP CONSTRAINT IF EXISTS budgets_custom_range_check;
ALTER TABLE public.budgets ADD CONSTRAINT budgets_custom_range_check CHECK (
    CASE
        WHEN period_type::text = 'CUSTOM_RANGE'
            THEN range_start_on IS NOT NULL AND range_end_on IS NOT NULL AND range_end_on >= range_start_on
        WHEN period_type::text = 'CUSTOM'
            THEN (range_start_on IS NULL AND range_end_on IS NULL)
              OR (range_start_on IS NOT NULL AND range_end_on IS NOT NULL AND range_end_on >= range_start_on)
        ELSE range_start_on IS NULL AND range_end_on IS NULL
    END
);

-- Funcion pura: la evalua el CHECK con los privilegios del invocador.
CREATE OR REPLACE FUNCTION public.budget_thresholds_valid_v1(p_thresholds smallint[])
RETURNS boolean
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
SET search_path = ''
AS $$
    SELECT p_thresholds IS NOT NULL
       AND pg_catalog.cardinality(p_thresholds) BETWEEN 1 AND 10
       AND NOT EXISTS (SELECT 1 FROM pg_catalog.unnest(p_thresholds) AS t(v) WHERE t.v IS NULL OR t.v NOT BETWEEN 1 AND 10000)
       AND (SELECT pg_catalog.count(DISTINCT t.v) FROM pg_catalog.unnest(p_thresholds) AS t(v))
           = pg_catalog.cardinality(p_thresholds);
$$;

ALTER TABLE public.budgets DROP CONSTRAINT IF EXISTS budgets_thresholds_check;
ALTER TABLE public.budgets ADD CONSTRAINT budgets_thresholds_check
    CHECK (public.budget_thresholds_valid_v1(alert_thresholds_bps));

CREATE UNIQUE INDEX IF NOT EXISTS budgets_owner_ref ON public.budgets (id, user_id);
CREATE INDEX IF NOT EXISTS idx_budgets_user_status ON public.budgets (user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_budgets_category_id ON public.budgets (category_id) WHERE category_id IS NOT NULL;

---------------------------------------------------------------------------
-- 2. budget_periods: snapshot de vigencia.
---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.budget_periods (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    budget_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    limit_minor bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT budget_periods_limit_positive CHECK (limit_minor > 0),
    CONSTRAINT budget_periods_range_ordered CHECK (ends_on > starts_on),
    CONSTRAINT budget_periods_unique_interval UNIQUE (budget_id, starts_on, ends_on),
    -- Mismo propietario que el presupuesto; borrar el presupuesto borra sus periodos.
    CONSTRAINT budget_periods_owner_budget_fk FOREIGN KEY (budget_id, user_id)
        REFERENCES public.budgets (id, user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_budget_periods_budget_starts ON public.budget_periods (budget_id, starts_on);
CREATE INDEX IF NOT EXISTS idx_budget_periods_user_id ON public.budget_periods (user_id);

ALTER TABLE public.budget_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budget_periods FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS budget_periods_own ON public.budget_periods;
CREATE POLICY budget_periods_own ON public.budget_periods FOR ALL TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

REVOKE ALL ON public.budget_periods FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.budget_periods TO authenticated;

---------------------------------------------------------------------------
-- 3. Tombstones de sincronizacion (G5).
---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.budget_tombstones (
    budget_id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    last_revision bigint NOT NULL,
    deleted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_budget_tombstones_user_id ON public.budget_tombstones (user_id);

ALTER TABLE public.budget_tombstones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budget_tombstones FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS budget_tombstones_owner_read ON public.budget_tombstones;
CREATE POLICY budget_tombstones_owner_read ON public.budget_tombstones FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) = user_id);
REVOKE ALL ON public.budget_tombstones FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.budget_tombstones TO authenticated;

-- El borrado fisico solo se hace por esta funcion (RPC o conversion del soft-delete legado).
CREATE OR REPLACE FUNCTION internal.budget_physical_delete_v1(p_budget_id uuid, p_user_id uuid, p_last_revision bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    INSERT INTO public.budget_tombstones (budget_id, user_id, last_revision)
    VALUES (p_budget_id, p_user_id, COALESCE(p_last_revision, 0))
    ON CONFLICT (budget_id) DO NOTHING;

    -- budget_periods cae por ON DELETE CASCADE; transactions/ledger/cuentas/categorias no se tocan.
    DELETE FROM public.budgets WHERE id = p_budget_id AND user_id = p_user_id;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (p_user_id, 'BUDGET', p_budget_id, COALESCE(p_last_revision, 0) + 1, 'DELETE',
            pg_catalog.jsonb_build_object('id', p_budget_id));
END;
$$;
REVOKE ALL ON FUNCTION internal.budget_physical_delete_v1(uuid, uuid, bigint) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.delete_budget_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_budget_id uuid;
    v_owner uuid;
    v_revision bigint;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    IF p_command IS NULL OR pg_catalog.jsonb_typeof(p_command) <> 'object' THEN
        RAISE EXCEPTION 'INVALID_REQUEST: command must be an object';
    END IF;
    BEGIN
        v_budget_id := (p_command->>'budget_id')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'INVALID_REQUEST: budget_id is not a uuid';
    END;
    IF v_budget_id IS NULL THEN RAISE EXCEPTION 'INVALID_REQUEST: budget_id is required'; END IF;

    SELECT user_id, revision INTO v_owner, v_revision FROM public.budgets WHERE id = v_budget_id FOR UPDATE;
    IF FOUND AND v_owner <> v_user_id THEN
        RAISE EXCEPTION 'NOT_FOUND: Budget not found';
    END IF;

    -- Idempotente: si ya no existe igualmente se asegura el tombstone (un borrado
    -- recibido antes que un upsert atrasado debe impedir su resurreccion).
    PERFORM internal.budget_physical_delete_v1(v_budget_id, v_user_id, v_revision);

    RETURN pg_catalog.jsonb_build_object('status', 'APPLIED', 'budget_id', v_budget_id, 'deleted', true);
END;
$$;
REVOKE ALL ON FUNCTION public.delete_budget_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_budget_v1(jsonb) TO authenticated;

-- Los clientes ya no borran filas directamente (evita borrar sin tombstone).
REVOKE ALL ON public.budgets FROM anon;
REVOKE DELETE, TRUNCATE ON public.budgets FROM PUBLIC, anon, authenticated;

---------------------------------------------------------------------------
-- 4. Guardas de budgets: tombstone, estado/is_active, categoria propia, cupo Free.
---------------------------------------------------------------------------
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

DROP TRIGGER IF EXISTS budgets_guard_v1 ON public.budgets;
CREATE TRIGGER budgets_guard_v1 BEFORE INSERT OR UPDATE ON public.budgets
    FOR EACH ROW EXECUTE FUNCTION internal.budgets_guard_v1();

-- El soft-delete legado (comando DELETE_ENTITY/BUDGET => deleted_at) pasa a borrado fisico + tombstone.
CREATE OR REPLACE FUNCTION internal.budgets_soft_delete_to_physical_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    PERFORM internal.budget_physical_delete_v1(NEW.id, NEW.user_id, NEW.revision);
    RETURN NULL;
END;
$$;
REVOKE ALL ON FUNCTION internal.budgets_soft_delete_to_physical_v1() FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS budgets_soft_delete_to_physical_v1 ON public.budgets;
CREATE TRIGGER budgets_soft_delete_to_physical_v1 AFTER UPDATE OF deleted_at ON public.budgets
    FOR EACH ROW WHEN (NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL)
    EXECUTE FUNCTION internal.budgets_soft_delete_to_physical_v1();

---------------------------------------------------------------------------
-- 5. Calendario de periodos y consumo (gasto neto).
---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.budget_period_bounds_v1(
    p_period_type text, p_on date, p_range_start date DEFAULT NULL, p_range_end date DEFAULT NULL
)
RETURNS TABLE (starts_on date, ends_on date)
LANGUAGE plpgsql
IMMUTABLE
SET search_path = ''
AS $$
DECLARE
    v_month_start date;
BEGIN
    CASE p_period_type
        WHEN 'DAILY' THEN
            RETURN QUERY SELECT p_on, p_on + 1;
        WHEN 'WEEKLY' THEN
            RETURN QUERY SELECT pg_catalog.date_trunc('week', p_on)::date, pg_catalog.date_trunc('week', p_on)::date + 7;
        WHEN 'BIWEEKLY' THEN
            v_month_start := pg_catalog.date_trunc('month', p_on)::date;
            IF pg_catalog.date_part('day', p_on) <= 15 THEN
                RETURN QUERY SELECT v_month_start, v_month_start + 15;
            ELSE
                RETURN QUERY SELECT v_month_start + 15, (v_month_start + interval '1 month')::date;
            END IF;
        WHEN 'MONTHLY' THEN
            v_month_start := pg_catalog.date_trunc('month', p_on)::date;
            RETURN QUERY SELECT v_month_start, (v_month_start + interval '1 month')::date;
        WHEN 'CUSTOM', 'CUSTOM_RANGE' THEN
            IF p_range_start IS NOT NULL AND p_range_end IS NOT NULL AND p_range_end >= p_range_start THEN
                RETURN QUERY SELECT p_range_start, p_range_end + 1;
            END IF;
        ELSE
            RAISE EXCEPTION 'INVALID_PERIOD_TYPE: %', p_period_type;
    END CASE;
END;
$$;

-- Consumo del periodo que contiene p_on (fecha civil en p_time_zone). Gasto neto:
-- compra elegible menos reembolsos vinculados (transaction_links REFUND), imputado a la
-- fecha de la compra original. Excluye VOIDED, borrados, transferencias, pagos de tarjeta,
-- deuda, metas y ajustes. Jerarquia: cada transaccion se cuenta una vez (filas, no sumas por nivel).
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
    ), tree AS (
        SELECT c.id FROM public.categories c, b WHERE c.id = b.category_id
        UNION
        SELECT c.id FROM public.categories c JOIN tree t ON c.parent_category_id = t.id
    ), eligible AS (
        SELECT t.id, t.amount_minor
        FROM public.transactions t, b, bounds
        WHERE t.user_id = b.user_id
          AND t.transaction_type = 'EXPENSE'
          AND t.operation_kind IN ('STANDARD', 'CARD_PURCHASE')
          AND t.status IN ('CONFIRMED', 'REVISED')
          AND t.deleted_at IS NULL
          AND t.transfer_group_id IS NULL
          AND t.currency_code = b.currency_code
          AND (t.occurred_at AT TIME ZONE p_time_zone)::date >= bounds.starts_on
          AND (t.occurred_at AT TIME ZONE p_time_zone)::date < bounds.ends_on
          AND (b.category_id IS NULL OR t.category_id IN (SELECT id FROM tree))
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
           COALESCE(bp.limit_minor, b.limit_minor),
           net.consumed,
           COALESCE(bp.limit_minor, b.limit_minor) - net.consumed,
           LEAST(2147483647, (net.consumed * 10000 / COALESCE(bp.limit_minor, b.limit_minor)))::integer
    FROM b
    JOIN bounds ON bounds.id = b.id
    CROSS JOIN net
    LEFT JOIN public.budget_periods bp
      ON bp.budget_id = b.id AND bp.starts_on = bounds.starts_on AND bp.ends_on = bounds.ends_on;
$$;
REVOKE ALL ON FUNCTION public.get_budget_status(uuid, date, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_budget_status(uuid, date, text) TO authenticated;

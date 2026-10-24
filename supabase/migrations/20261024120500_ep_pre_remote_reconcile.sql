-- EP-PRE (Sprint 7): reconcilia el esquema remoto con 20261024120000_ep_pre_budgets.sql.
--
-- El proyecto remoto conserva objetos de presupuesto de migraciones previas que no
-- existen en este repositorio:
--   * public.get_budget_status(uuid, date) y el trigger on_transaction_budget_alert
--     (public.trg_check_budget_alerts) sobre public.transactions. Junto con la nueva
--     public.get_budget_status(uuid, date, text) la llamada de dos argumentos del trigger
--     es ambigua y haria fallar el registro de gastos de usuarios con presupuestos.
--     Ademas generaba alertas en el servidor que duplicarian las del cliente, que ahora
--     las emite con deduplicacion por propietario/presupuesto/periodo/umbral (G3).
--   * public.budget_periods sin la FK compuesta de propietario ni el CHECK de rango,
--     porque CREATE TABLE IF NOT EXISTS no la recrea.
-- En una base creada solo desde este repositorio esta migracion no cambia nada.

DROP TRIGGER IF EXISTS on_transaction_budget_alert ON public.transactions;
DROP FUNCTION IF EXISTS public.trg_check_budget_alerts();
DROP FUNCTION IF EXISTS public.get_budget_status(uuid, date);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conrelid = 'public.budget_periods'::regclass AND conname = 'budget_periods_range_ordered'
    ) THEN
        ALTER TABLE public.budget_periods
            ADD CONSTRAINT budget_periods_range_ordered CHECK (ends_on > starts_on);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conrelid = 'public.budget_periods'::regclass AND conname = 'budget_periods_owner_budget_fk'
    ) THEN
        ALTER TABLE public.budget_periods
            ADD CONSTRAINT budget_periods_owner_budget_fk FOREIGN KEY (budget_id, user_id)
            REFERENCES public.budgets (id, user_id) ON DELETE CASCADE;
    END IF;
END;
$$;

ALTER TABLE public.budget_periods FORCE ROW LEVEL SECURITY;

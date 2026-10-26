-- Current command RPCs emit canonical GOAL/DEBT snapshots atomically themselves.
-- Retain historical feed entries; stop only the obsolete duplicate emitters.
BEGIN;
DROP TRIGGER IF EXISTS trg_savings_goals_sync ON public.goals;
DROP TRIGGER IF EXISTS trg_obligations_sync ON public.debts;
NOTIFY pgrst, 'reload schema';
COMMIT;

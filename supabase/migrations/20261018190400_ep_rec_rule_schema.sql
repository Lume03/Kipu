BEGIN;

CREATE TABLE IF NOT EXISTS public.recurrences (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  template_transaction jsonb NOT NULL,
  amount_mode text NOT NULL CHECK (amount_mode IN ('FIXED', 'APPROXIMATE')),
  frequency text NOT NULL CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'ANNUALLY', 'CUSTOM')),
  interval_count bigint NOT NULL CHECK (interval_count > 0),
  custom_interval_unit text,
  start_date date NOT NULL CHECK (start_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
  end_date date CHECK (end_date IS NULL OR end_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
  next_due_date date NOT NULL CHECK (next_due_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
  reminder_lead_days smallint NOT NULL CHECK (reminder_lead_days BETWEEN 0 AND 30),
  status text NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
  is_active boolean GENERATED ALWAYS AS (status = 'ACTIVE') STORED,
  revision bigint NOT NULL CHECK (revision > 0),
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT pg_catalog.now(),
  updated_at timestamptz NOT NULL DEFAULT pg_catalog.now(),
  CONSTRAINT recurrences_schedule_bounds CHECK (end_date IS NULL OR end_date >= start_date),
  CONSTRAINT recurrences_builtin_interval CHECK (
    (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'ANNUALLY')
      AND interval_count = 1 AND custom_interval_unit IS NULL)
    OR (frequency = 'CUSTOM' AND custom_interval_unit = 'WEEK' AND interval_count BETWEEN 1 AND 5200)
    OR (frequency = 'CUSTOM' AND custom_interval_unit = 'MONTH' AND interval_count BETWEEN 1 AND 1200)
  ),
  CONSTRAINT recurrences_owner_id_unique UNIQUE (id, user_id)
);

CREATE INDEX IF NOT EXISTS recurrences_owner_status_next_due_idx
  ON public.recurrences (user_id, status, next_due_date) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS recurrences_owner_updated_idx
  ON public.recurrences (user_id, updated_at DESC, id);

ALTER TABLE public.recurrences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recurrences FORCE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_catalog.pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'recurrences'
      AND policyname = 'recurrences_select_own'
  ) THEN
    CREATE POLICY recurrences_select_own ON public.recurrences
      FOR SELECT TO authenticated USING (auth.uid() = user_id);
  END IF;
END;
$$;
REVOKE ALL ON public.recurrences FROM PUBLIC, anon;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.recurrences FROM authenticated;
GRANT SELECT ON public.recurrences TO authenticated;

COMMIT;

BEGIN;
SELECT plan(26);
\ir _recurrence_test_fixture.sql

SELECT has_table('public', 'recurrences', 'public.recurrences exists');
SELECT has_column('public', 'recurrences', 'user_id', 'recurrences has owner key');
SELECT has_column('public', 'recurrences', 'template_transaction', 'recurrences stores template JSON');
SELECT has_column('public', 'recurrences', 'amount_mode', 'recurrences stores amount mode');
SELECT has_column('public', 'recurrences', 'frequency', 'recurrences stores frequency');
SELECT has_column('public', 'recurrences', 'interval_count', 'recurrences stores interval count');
SELECT has_column('public', 'recurrences', 'custom_interval_unit', 'recurrences stores custom unit');
SELECT has_column('public', 'recurrences', 'start_date', 'recurrences stores immutable start date');
SELECT has_column('public', 'recurrences', 'end_date', 'recurrences stores optional end date');
SELECT has_column('public', 'recurrences', 'next_due_date', 'recurrences stores next due date');
SELECT has_column('public', 'recurrences', 'reminder_lead_days', 'recurrences stores required reminder lead time');
SELECT has_column('public', 'recurrences', 'status', 'recurrences stores lifecycle status');
SELECT has_column('public', 'recurrences', 'is_active', 'legacy read projection is retained');
SELECT has_column('public', 'recurrences', 'revision', 'recurrences stores revision');
SELECT has_column('public', 'recurrences', 'deleted_at', 'recurrences stores sync tombstone');

SELECT ok(
  coalesce((SELECT c.relrowsecurity FROM pg_class c
    WHERE c.oid = pg_catalog.to_regclass('public.recurrences')), false),
  'recurrences enables row-level security'
);
SELECT ok(
  CASE WHEN pg_catalog.to_regclass('public.recurrences') IS NULL THEN false
    ELSE NOT has_table_privilege('anon', pg_catalog.to_regclass('public.recurrences'), 'SELECT,INSERT,UPDATE,DELETE') END,
  'anon has no direct recurrence table access'
);
SELECT ok(
  CASE WHEN pg_catalog.to_regclass('public.recurrences') IS NULL THEN false
    ELSE NOT has_table_privilege('authenticated', pg_catalog.to_regclass('public.recurrences'), 'INSERT,UPDATE,DELETE') END,
  'authenticated writes are mediated by RPCs'
);
SELECT ok(
  EXISTS (SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'recurrences'
      AND policyname = 'recurrences_select_own' AND cmd = 'SELECT'
      AND qual LIKE '%auth.uid()%user_id%'),
  'select policy is scoped to auth.uid owner'
);
SELECT ok(
  EXISTS (SELECT 1 FROM pg_class c
    WHERE c.oid = pg_catalog.to_regclass('public.recurrences')
      AND c.relforcerowsecurity),
  'row-level security applies to table owner too'
);
SELECT ok(EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid=pg_catalog.to_regclass('public.recurrences') AND conname='recurrences_schedule_bounds'),
  'end date cannot precede start date');
SELECT ok(EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid=pg_catalog.to_regclass('public.recurrences') AND conname='recurrences_builtin_interval'),
  'built-in/custom interval rules are constrained');
SELECT ok(EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid=pg_catalog.to_regclass('public.recurrences') AND conname='recurrences_owner_id_unique'),
  'owner key supports composite references without cross-owner aliasing');
SELECT ok(to_regclass('public.recurrences_owner_status_next_due_idx') IS NOT NULL,
  'owner/status/due-date query index exists');
SELECT ok(to_regclass('public.recurrences_owner_updated_idx') IS NOT NULL,
  'owner/update-feed query index exists');
SELECT ok(CASE WHEN pg_catalog.to_regclass('public.recurrences') IS NULL THEN false
    ELSE NOT has_table_privilege('authenticated', pg_catalog.to_regclass('public.recurrences'), 'TRUNCATE') END,
  'authenticated cannot truncate recurrence data');

SELECT * FROM finish();
ROLLBACK;

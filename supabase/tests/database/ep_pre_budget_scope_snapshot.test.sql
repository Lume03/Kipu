-- EP-PRE: snapshot de alcance por periodo, cambios pendientes y get_budget_status con alcance historico.
BEGIN;
SELECT plan(47);

-- ---------------------------------------------------------------------------
-- Fixture
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (id, email) VALUES
    ('9b000000-0000-4000-8000-000000000001', 'snap-owner@example.test'),
    ('9b000000-0000-4000-8000-000000000002', 'snap-other@example.test');

INSERT INTO public.categories (id, user_id, name, origin) VALUES
    ('9b000000-0000-4000-8000-0000000000c1', '9b000000-0000-4000-8000-000000000001', 'Comida', 'CUSTOM'),
    ('9b000000-0000-4000-8000-0000000000c2', '9b000000-0000-4000-8000-000000000001', 'Restaurantes', 'CUSTOM'),
    ('9b000000-0000-4000-8000-0000000000c3', '9b000000-0000-4000-8000-000000000002', 'Ajena', 'CUSTOM'),
    ('9b000000-0000-4000-8000-0000000000c4', '9b000000-0000-4000-8000-000000000001', 'Borrada', 'CUSTOM'),
    ('9b000000-0000-4000-8000-0000000000c5', '9b000000-0000-4000-8000-000000000001', 'Transporte', 'CUSTOM');
UPDATE public.categories SET parent_id = '9b000000-0000-4000-8000-0000000000c1'
 WHERE id = '9b000000-0000-4000-8000-0000000000c2';
UPDATE public.categories SET deleted_at = now() WHERE id = '9b000000-0000-4000-8000-0000000000c4';

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, initial_balance_minor_units, creation_operation_id, preset_id)
VALUES ('9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-000000000001', 'Efectivo', 'CASH', 'PEN', 0, extensions.gen_random_uuid(), 'CASH');

-- b1: alcance actual = Comida (c1). b2: total. b3/b4: historicos (no consumen cupo). b9: ajeno.
INSERT INTO public.budgets (id, user_id, name, limit_minor, currency_code, period_type, category_id) VALUES
    ('9b000000-0000-4000-8000-0000000000b1', '9b000000-0000-4000-8000-000000000001', 'Comida', 50000, 'PEN', 'MONTHLY', '9b000000-0000-4000-8000-0000000000c1'),
    ('9b000000-0000-4000-8000-0000000000b2', '9b000000-0000-4000-8000-000000000001', 'Total', 90000, 'PEN', 'MONTHLY', NULL),
    ('9b000000-0000-4000-8000-0000000000b9', '9b000000-0000-4000-8000-000000000002', 'Ajeno', 10000, 'PEN', 'WEEKLY', NULL);
INSERT INTO public.budgets (id, user_id, name, limit_minor, currency_code, period_type, category_id, status) VALUES
    ('9b000000-0000-4000-8000-0000000000b3', '9b000000-0000-4000-8000-000000000001', 'Hist cat', 1000, 'PEN', 'MONTHLY', '9b000000-0000-4000-8000-0000000000c1', 'FINISHED'),
    ('9b000000-0000-4000-8000-0000000000b4', '9b000000-0000-4000-8000-000000000001', 'Hist total', 1000, 'PEN', 'MONTHLY', NULL, 'FINISHED');

-- Periodos "preexistentes": insertados sin scope_category_id (NULL), como antes de esta migracion.
INSERT INTO public.budget_periods (id, user_id, budget_id, starts_on, ends_on, limit_minor) VALUES
    ('9b000000-0000-4000-8000-0000000000d3', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000b3', '2026-03-01', '2026-04-01', 1000),
    ('9b000000-0000-4000-8000-0000000000d4', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000b4', '2026-03-01', '2026-04-01', 1000),
    ('9b000000-0000-4000-8000-0000000000d9', '9b000000-0000-4000-8000-000000000002', '9b000000-0000-4000-8000-0000000000b9', '2026-10-19', '2026-10-26', 10000);

-- ---------------------------------------------------------------------------
-- Estructura
-- ---------------------------------------------------------------------------
SELECT col_type_is('public', 'budget_periods', 'scope_category_id', 'uuid', 'scope_category_id es uuid');
SELECT col_is_null('public', 'budget_periods', 'scope_category_id', 'scope_category_id admite NULL (= TOTAL)');
SELECT col_type_is('public', 'budgets', 'pending_scope_type', 'text', 'pending_scope_type text');
SELECT col_type_is('public', 'budgets', 'pending_scope_category_id', 'uuid', 'pending_scope_category_id uuid');
SELECT col_type_is('public', 'budgets', 'pending_schedule_type', 'text', 'pending_schedule_type text');
SELECT col_type_is('public', 'budgets', 'pending_range_start_on', 'date', 'pending_range_start_on date');
SELECT col_type_is('public', 'budgets', 'pending_range_end_on', 'date', 'pending_range_end_on date');
SELECT col_type_is('public', 'budgets', 'pending_effective_from', 'date', 'pending_effective_from date');
SELECT ok(NOT has_table_privilege('authenticated', 'public.budget_periods', 'DELETE'), 'authenticated sigue sin DELETE directo sobre periodos');
SELECT ok(NOT has_table_privilege('anon', 'public.budget_periods', 'SELECT'), 'anon sigue sin acceso a periodos');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid = 'public.budget_periods'::regclass), 'RLS forzada se conserva');

-- ---------------------------------------------------------------------------
-- Backfill: se ejecuta la misma sentencia de la migracion sobre los periodos sin snapshot.
-- ---------------------------------------------------------------------------
SELECT is((SELECT scope_category_id FROM public.budget_periods WHERE id = '9b000000-0000-4000-8000-0000000000d3'), NULL::uuid, 'antes del backfill el periodo no tiene snapshot');
UPDATE public.budget_periods bp
   SET scope_category_id = b.category_id
  FROM public.budgets b
 WHERE b.id = bp.budget_id
   AND b.user_id = bp.user_id
   AND bp.scope_category_id IS DISTINCT FROM b.category_id;
SELECT is((SELECT scope_category_id FROM public.budget_periods WHERE id = '9b000000-0000-4000-8000-0000000000d3'), '9b000000-0000-4000-8000-0000000000c1'::uuid, 'backfill copia la categoria del presupuesto');
SELECT is((SELECT scope_category_id FROM public.budget_periods WHERE id = '9b000000-0000-4000-8000-0000000000d4'), NULL::uuid, 'backfill de presupuesto total deja NULL');
SELECT is((SELECT scope_category_id FROM public.budget_periods WHERE id = '9b000000-0000-4000-8000-0000000000d9'), NULL::uuid, 'backfill respeta presupuestos de otro usuario (total)');

-- ---------------------------------------------------------------------------
-- Propiedad / validez de la categoria snapshot (como postgres, sin RLS)
-- ---------------------------------------------------------------------------
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES ('9b000000-0000-4000-8000-000000000001','9b000000-0000-4000-8000-0000000000b1','2026-01-01','2026-02-01',100,'9b000000-0000-4000-8000-0000000000c3')$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'snapshot con categoria de otro usuario rechazado');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES ('9b000000-0000-4000-8000-000000000001','9b000000-0000-4000-8000-0000000000b1','2026-01-01','2026-02-01',100,'9b000000-0000-4000-8000-0000000000c4')$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'snapshot con categoria borrada rechazado');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES ('9b000000-0000-4000-8000-000000000001','9b000000-0000-4000-8000-0000000000b1','2026-01-01','2026-02-01',100,'9b000000-0000-4000-8000-0000000000ff')$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'snapshot con categoria inexistente rechazado');
SELECT lives_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES ('9b000000-0000-4000-8000-000000000001','9b000000-0000-4000-8000-0000000000b1','2026-01-01','2026-02-01',100,'9b000000-0000-4000-8000-0000000000c2')$$, 'snapshot con subcategoria propia aceptado');
SELECT throws_ok($$UPDATE public.budget_periods SET scope_category_id = '9b000000-0000-4000-8000-0000000000c3' WHERE budget_id = '9b000000-0000-4000-8000-0000000000b1' AND starts_on = '2026-01-01'$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'cambiar el snapshot a categoria ajena se rechaza');

-- Reenvio idempotente: la categoria se borra despues y el mismo snapshot sigue aceptandose.
UPDATE public.categories SET deleted_at = now() WHERE id = '9b000000-0000-4000-8000-0000000000c2';
SELECT lives_ok($$INSERT INTO public.budget_periods (id, user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id)
    SELECT id, user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id FROM public.budget_periods WHERE budget_id = '9b000000-0000-4000-8000-0000000000b1' AND starts_on = '2026-01-01'
    ON CONFLICT (id) DO UPDATE SET limit_minor = EXCLUDED.limit_minor$$, 'reenvio del mismo snapshot con categoria luego borrada sigue valido');
UPDATE public.categories SET deleted_at = NULL WHERE id = '9b000000-0000-4000-8000-0000000000c2';

-- ---------------------------------------------------------------------------
-- Cambios pendientes: CHECKs y categoria propia
-- ---------------------------------------------------------------------------
SELECT lives_ok($$UPDATE public.budgets SET pending_scope_type = 'CATEGORY', pending_scope_category_id = '9b000000-0000-4000-8000-0000000000c5', pending_schedule_type = 'CUSTOM_RANGE', pending_range_start_on = '2026-09-01', pending_range_end_on = '2026-09-30', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, 'pendiente valido (categoria + rango custom)');
SELECT lives_ok($$UPDATE public.budgets SET pending_scope_type = 'TOTAL', pending_scope_category_id = NULL, pending_schedule_type = NULL, pending_range_start_on = NULL, pending_range_end_on = NULL, pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, 'pendiente valido solo alcance TOTAL');
SELECT lives_ok($$UPDATE public.budgets SET pending_scope_type = NULL, pending_schedule_type = 'WEEKLY', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, 'pendiente valido solo calendario recurrente');
SELECT lives_ok($$UPDATE public.budgets SET pending_schedule_type = NULL, pending_effective_from = NULL WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, 'se puede limpiar el pendiente');
SELECT is((SELECT pending_effective_from FROM public.budgets WHERE id = '9b000000-0000-4000-8000-0000000000b3'), NULL::date, 'pendiente limpiado');
SELECT throws_ok($$UPDATE public.budgets SET pending_scope_type = 'CATEGORY', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'alcance CATEGORY exige categoria');
SELECT throws_ok($$UPDATE public.budgets SET pending_scope_type = 'TOTAL', pending_scope_category_id = '9b000000-0000-4000-8000-0000000000c5', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'alcance TOTAL no admite categoria');
SELECT throws_ok($$UPDATE public.budgets SET pending_scope_type = 'OTRO', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'tipo de alcance pendiente invalido');
SELECT throws_ok($$UPDATE public.budgets SET pending_schedule_type = 'CUSTOM_RANGE', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'CUSTOM_RANGE pendiente exige rango');
SELECT throws_ok($$UPDATE public.budgets SET pending_schedule_type = 'CUSTOM_RANGE', pending_range_start_on = '2026-09-10', pending_range_end_on = '2026-09-09', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'rango pendiente invertido rechazado');
SELECT throws_ok($$UPDATE public.budgets SET pending_schedule_type = 'MONTHLY', pending_range_start_on = '2026-09-01', pending_range_end_on = '2026-09-30', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'calendario recurrente pendiente no admite rango');
SELECT throws_ok($$UPDATE public.budgets SET pending_schedule_type = 'CUSTOM', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'CUSTOM legado no es un calendario pendiente valido');
SELECT throws_ok($$UPDATE public.budgets SET pending_schedule_type = 'WEEKLY' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'componente pendiente sin fecha de vigencia rechazado');
SELECT throws_ok($$UPDATE public.budgets SET pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, '23514', NULL, 'fecha de vigencia sin componente rechazada');
SELECT throws_ok($$UPDATE public.budgets SET pending_scope_type = 'CATEGORY', pending_scope_category_id = '9b000000-0000-4000-8000-0000000000c3', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'categoria pendiente de otro usuario rechazada');
SELECT throws_ok($$UPDATE public.budgets SET pending_scope_type = 'CATEGORY', pending_scope_category_id = '9b000000-0000-4000-8000-0000000000c4', pending_effective_from = '2026-09-01' WHERE id = '9b000000-0000-4000-8000-0000000000b3'$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'categoria pendiente borrada rechazada');

-- ---------------------------------------------------------------------------
-- get_budget_status con alcance historico
-- b1 hoy = Comida (c1). Junio: snapshot TOTAL. Julio: snapshot Comida. Agosto: sin periodo.
-- ---------------------------------------------------------------------------
INSERT INTO public.budget_periods (id, user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES
    ('9b000000-0000-4000-8000-0000000000e6', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000b1', '2026-06-01', '2026-07-01', 30000, NULL),
    ('9b000000-0000-4000-8000-0000000000e7', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000b1', '2026-07-01', '2026-08-01', 40000, '9b000000-0000-4000-8000-0000000000c1');

INSERT INTO public.transactions (id, user_id, account_id, category_id, transaction_type, operation_kind, amount_minor, currency_code, occurred_at, status) VALUES
    ('9b000000-0000-4000-8000-000000000f01', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 10000, 'PEN', '2026-06-10 12:00+00', 'CONFIRMED'),
    ('9b000000-0000-4000-8000-000000000f02', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c2', 'EXPENSE', 'STANDARD', 2000, 'PEN', '2026-06-20 12:00+00', 'CONFIRMED'),
    ('9b000000-0000-4000-8000-000000000f03', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c5', 'EXPENSE', 'STANDARD', 5000, 'PEN', '2026-06-15 12:00+00', 'CONFIRMED'),
    ('9b000000-0000-4000-8000-000000000f04', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 1000, 'PEN', '2026-07-10 12:00+00', 'CONFIRMED'),
    ('9b000000-0000-4000-8000-000000000f05', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c5', 'EXPENSE', 'STANDARD', 4000, 'PEN', '2026-07-12 12:00+00', 'CONFIRMED'),
    ('9b000000-0000-4000-8000-000000000f06', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 300, 'PEN', '2026-08-05 12:00+00', 'CONFIRMED'),
    ('9b000000-0000-4000-8000-000000000f07', '9b000000-0000-4000-8000-000000000001', '9b000000-0000-4000-8000-0000000000a1', '9b000000-0000-4000-8000-0000000000c5', 'EXPENSE', 'STANDARD', 500, 'PEN', '2026-08-06 12:00+00', 'CONFIRMED');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', '9b000000-0000-4000-8000-000000000001', true);

SELECT is((SELECT consumed_minor FROM public.get_budget_status('9b000000-0000-4000-8000-0000000000b1', '2026-06-15')), 17000::bigint,
    'junio: el snapshot TOTAL cuenta todo el gasto aunque el presupuesto hoy sea de categoria');
SELECT is((SELECT limit_minor FROM public.get_budget_status('9b000000-0000-4000-8000-0000000000b1', '2026-06-15')), 30000::bigint, 'junio: limite del snapshot');
SELECT is((SELECT consumed_minor FROM public.get_budget_status('9b000000-0000-4000-8000-0000000000b1', '2026-07-15')), 1000::bigint,
    'julio: el snapshot Comida excluye Transporte');
SELECT is((SELECT consumed_minor FROM public.get_budget_status('9b000000-0000-4000-8000-0000000000b1', '2026-08-15')), 300::bigint,
    'agosto: sin periodo persistido rige el alcance actual del presupuesto');
SELECT is((SELECT limit_minor FROM public.get_budget_status('9b000000-0000-4000-8000-0000000000b1', '2026-08-15')), 50000::bigint, 'agosto: limite del presupuesto');

-- Un snapshot Comida sobre un presupuesto hoy total debe restringir; y la subcategoria entra.
UPDATE public.budget_periods SET scope_category_id = '9b000000-0000-4000-8000-0000000000c1'
 WHERE id = '9b000000-0000-4000-8000-0000000000e6';
SELECT is((SELECT consumed_minor FROM public.get_budget_status('9b000000-0000-4000-8000-0000000000b1', '2026-06-15')), 12000::bigint,
    'cambiar el snapshot a Comida recalcula junio incluyendo la subcategoria');

-- ---------------------------------------------------------------------------
-- RLS del snapshot
-- ---------------------------------------------------------------------------
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES ('9b000000-0000-4000-8000-000000000001','9b000000-0000-4000-8000-0000000000b2','2026-02-01','2026-03-01',100,'9b000000-0000-4000-8000-0000000000c3')$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'authenticated no usa categoria ajena como snapshot');
UPDATE public.budget_periods SET scope_category_id = '9b000000-0000-4000-8000-0000000000c1' WHERE id = '9b000000-0000-4000-8000-0000000000d9';
SELECT lives_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor, scope_category_id) VALUES ('9b000000-0000-4000-8000-000000000001','9b000000-0000-4000-8000-0000000000b2','2026-02-01','2026-03-01',100,'9b000000-0000-4000-8000-0000000000c5')$$, 'authenticated guarda snapshot con categoria propia');
SELECT is((SELECT count(*)::int FROM public.budget_periods WHERE id = '9b000000-0000-4000-8000-0000000000d9'), 0, 'no ve el snapshot de periodos ajenos');

RESET ROLE;
SELECT is((SELECT scope_category_id FROM public.budget_periods WHERE id = '9b000000-0000-4000-8000-0000000000d9'), NULL::uuid, 'no modifico el snapshot de periodos ajenos');

SELECT * FROM finish();
ROLLBACK;

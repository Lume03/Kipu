-- EP-PRE: presupuestos, vigencias, tombstone de borrado, cupo Free y consumo neto.
BEGIN;
SELECT plan(54);

-- ---------------------------------------------------------------------------
-- Fixture
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (id, email) VALUES
    ('9a000000-0000-4000-8000-000000000001', 'pre-owner@example.test'),
    ('9a000000-0000-4000-8000-000000000002', 'pre-other@example.test');

INSERT INTO public.categories (id, user_id, name) VALUES
    ('9a000000-0000-4000-8000-0000000000c1', '9a000000-0000-4000-8000-000000000001', 'Comida'),
    ('9a000000-0000-4000-8000-0000000000c2', '9a000000-0000-4000-8000-000000000001', 'Restaurantes'),
    ('9a000000-0000-4000-8000-0000000000c3', '9a000000-0000-4000-8000-000000000002', 'Ajena');
UPDATE public.categories SET parent_category_id = '9a000000-0000-4000-8000-0000000000c1'
 WHERE id = '9a000000-0000-4000-8000-0000000000c2';

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code, initial_balance_minor_units, creation_operation_id, preset_id)
VALUES ('9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-000000000001', 'Efectivo', 'CASH', 'PEN', 0, extensions.gen_random_uuid(), 'CASH');

-- Presupuestos: dos activos del propietario (cupo Free lleno) y uno ajeno.
INSERT INTO public.budgets (id, user_id, name, limit_minor, currency_code, period_type, category_id) VALUES
    ('9a000000-0000-4000-8000-0000000000b1', '9a000000-0000-4000-8000-000000000001', 'Total', 50000, 'PEN', 'MONTHLY', NULL),
    ('9a000000-0000-4000-8000-0000000000b2', '9a000000-0000-4000-8000-000000000001', 'Comida', 20000, 'PEN', 'MONTHLY', '9a000000-0000-4000-8000-0000000000c1'),
    ('9a000000-0000-4000-8000-0000000000b9', '9a000000-0000-4000-8000-000000000002', 'Ajeno', 10000, 'PEN', 'WEEKLY', NULL);

INSERT INTO public.budget_periods (id, user_id, budget_id, starts_on, ends_on, limit_minor) VALUES
    ('9a000000-0000-4000-8000-0000000000d1', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000b1', '2026-05-01', '2026-06-01', 40000),
    ('9a000000-0000-4000-8000-0000000000d9', '9a000000-0000-4000-8000-000000000002', '9a000000-0000-4000-8000-0000000000b9', '2026-10-19', '2026-10-26', 10000);

-- Movimientos de junio 2026 (UTC)
INSERT INTO public.transactions (id, user_id, account_id, category_id, transaction_type, operation_kind, amount_minor, currency_code, occurred_at, status, transfer_group_id) VALUES
    ('9a000000-0000-4000-8000-000000000e01', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 10000, 'PEN', '2026-06-10 12:00+00', 'CONFIRMED', NULL),
    ('9a000000-0000-4000-8000-000000000e02', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-0000000000c2', 'EXPENSE', 'STANDARD', 2000, 'PEN', '2026-06-20 12:00+00', 'REVISED', NULL),
    ('9a000000-0000-4000-8000-000000000e03', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 5000, 'PEN', '2026-06-11 12:00+00', 'VOIDED', NULL),
    ('9a000000-0000-4000-8000-000000000e04', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 7000, 'USD', '2026-06-12 12:00+00', 'CONFIRMED', NULL),
    ('9a000000-0000-4000-8000-000000000e05', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', NULL, 'EXPENSE', 'GOAL_FUNDING', 4000, 'PEN', '2026-06-13 12:00+00', 'CONFIRMED', NULL),
    ('9a000000-0000-4000-8000-000000000e06', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', NULL, 'EXPENSE', 'STANDARD', 3000, 'PEN', '2026-06-14 12:00+00', 'CONFIRMED', '9a000000-0000-4000-8000-000000000f01'),
    ('9a000000-0000-4000-8000-000000000e07', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-0000000000c1', 'EXPENSE', 'STANDARD', 900, 'PEN', '2026-07-01 12:00+00', 'CONFIRMED', NULL),
    -- reembolso (julio) de la compra e01 (junio): se imputa a junio
    ('9a000000-0000-4000-8000-000000000e08', '9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-0000000000a1', '9a000000-0000-4000-8000-0000000000c1', 'INCOME', 'REFUND', 3000, 'PEN', '2026-07-02 12:00+00', 'CONFIRMED', NULL);
INSERT INTO public.transaction_links (user_id, source_transaction_id, target_transaction_id, link_type) VALUES
    ('9a000000-0000-4000-8000-000000000001', '9a000000-0000-4000-8000-000000000e01', '9a000000-0000-4000-8000-000000000e08', 'REFUND');

-- ---------------------------------------------------------------------------
-- Estructura
-- ---------------------------------------------------------------------------
SELECT has_table('public', 'budget_periods', 'budget_periods existe');
SELECT has_table('public', 'budget_tombstones', 'budget_tombstones existe');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid = 'public.budget_periods'::regclass), 'budget_periods con RLS forzada');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid = 'public.budget_tombstones'::regclass), 'tombstones con RLS forzada');
SELECT ok(enum_range(NULL::public.budget_period_type)::text[] @> ARRAY['DAILY','WEEKLY','BIWEEKLY','MONTHLY','CUSTOM_RANGE','CUSTOM'], 'enum extendido de forma aditiva (CUSTOM legado conservado)');
SELECT ok(NOT has_table_privilege('anon', 'public.budget_periods', 'SELECT'), 'anon no accede a periodos');
SELECT ok(NOT has_table_privilege('authenticated', 'public.budgets', 'DELETE'), 'authenticated no borra budgets directamente');
SELECT ok(NOT has_table_privilege('authenticated', 'public.budget_tombstones', 'INSERT'), 'authenticated no escribe tombstones');
SELECT is((SELECT status FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b1'), 'ACTIVE', 'estado por defecto ACTIVE');
SELECT is((SELECT alert_thresholds_bps FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b1'), ARRAY[5000,8000,10000]::smallint[], 'umbrales por defecto 5000/8000/10000');

-- ---------------------------------------------------------------------------
-- Restricciones de dominio (como postgres, sin RLS)
-- ---------------------------------------------------------------------------
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status) VALUES ('9a000000-0000-4000-8000-000000000002','x',0,'PEN','FINISHED')$$, '23514', NULL, 'limite <= 0 rechazado');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, alert_thresholds_bps) VALUES ('9a000000-0000-4000-8000-000000000002','x',100,'PEN','FINISHED','{5000,5000}')$$, '23514', NULL, 'umbrales duplicados rechazados');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, alert_thresholds_bps) VALUES ('9a000000-0000-4000-8000-000000000002','x',100,'PEN','FINISHED','{0,5000}')$$, '23514', NULL, 'umbral 0 rechazado');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, alert_thresholds_bps) VALUES ('9a000000-0000-4000-8000-000000000002','x',100,'PEN','FINISHED','{10001}')$$, '23514', NULL, 'umbral > 10000 rechazado');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, period_type, range_start_on, range_end_on) VALUES ('9a000000-0000-4000-8000-000000000002','x',100,'PEN','FINISHED','CUSTOM_RANGE','2026-06-10','2026-06-09')$$, '23514', NULL, 'rango invertido rechazado');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, period_type) VALUES ('9a000000-0000-4000-8000-000000000002','x',100,'PEN','FINISHED','CUSTOM_RANGE')$$, '23514', NULL, 'CUSTOM_RANGE exige rango');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, period_type, range_start_on, range_end_on) VALUES ('9a000000-0000-4000-8000-000000000002','x',100,'PEN','FINISHED','MONTHLY','2026-06-01','2026-06-30')$$, '23514', NULL, 'periodos recurrentes no aceptan rango');
SELECT lives_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, period_type, range_start_on, range_end_on) VALUES ('9a000000-0000-4000-8000-000000000002','rango',100,'PEN','FINISHED','CUSTOM_RANGE','2026-06-10','2026-06-10')$$, 'rango de un dia (end = start) permitido');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor) VALUES ('9a000000-0000-4000-8000-000000000001','9a000000-0000-4000-8000-0000000000b1','2026-05-01','2026-06-01',100)$$, '23505', NULL, 'periodo duplicado rechazado');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor) VALUES ('9a000000-0000-4000-8000-000000000001','9a000000-0000-4000-8000-0000000000b1','2026-04-01','2026-05-01',0)$$, '23514', NULL, 'limite de periodo > 0');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor) VALUES ('9a000000-0000-4000-8000-000000000001','9a000000-0000-4000-8000-0000000000b1','2026-04-01','2026-04-01',100)$$, '23514', NULL, 'periodo con fin <= inicio rechazado');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor) VALUES ('9a000000-0000-4000-8000-000000000002','9a000000-0000-4000-8000-0000000000b1','2026-04-01','2026-05-01',100)$$, '23503', NULL, 'periodo con propietario distinto al del presupuesto rechazado (FK compuesta)');

-- Cupo Free: el propietario ya tiene 2 activos.
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code) VALUES ('9a000000-0000-4000-8000-000000000001','tercero',100,'PEN')$$, 'P0001', 'BUDGET_QUOTA_EXCEEDED: Free plan allows at most 2 active budgets', 'Free no crea un tercer presupuesto activo');
SELECT lives_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status) VALUES ('9a000000-0000-4000-8000-000000000001','historico',100,'PEN','FINISHED')$$, 'FINISHED no consume cupo');
SELECT is((SELECT is_active FROM public.budgets WHERE name = 'historico'), false, 'is_active se sincroniza con status');
UPDATE public.budgets SET is_active = false WHERE id = '9a000000-0000-4000-8000-0000000000b2';
SELECT is((SELECT status FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b2'), 'FINISHED', 'is_active=false legado => FINISHED');
SELECT lives_ok($$INSERT INTO public.budgets (id, user_id, name, limit_minor, currency_code) VALUES ('9a000000-0000-4000-8000-0000000000b3','9a000000-0000-4000-8000-000000000001','tercero',100,'PEN')$$, 'al liberar cupo se puede crear otro activo');
SELECT throws_ok($$UPDATE public.budgets SET status = 'ACTIVE' WHERE id = '9a000000-0000-4000-8000-0000000000b2'$$, 'P0001', 'BUDGET_QUOTA_EXCEEDED: Free plan allows at most 2 active budgets', 'reactivar sobre el cupo tambien se rechaza');
DELETE FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b3';
UPDATE public.budgets SET status = 'ACTIVE' WHERE id = '9a000000-0000-4000-8000-0000000000b2';

-- ---------------------------------------------------------------------------
-- RLS cross-user
-- ---------------------------------------------------------------------------
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', '9a000000-0000-4000-8000-000000000001', true);

SELECT is((SELECT count(*)::int FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b9'), 0, 'no lee presupuestos ajenos');
SELECT is((SELECT count(*)::int FROM public.budget_periods WHERE id = '9a000000-0000-4000-8000-0000000000d9'), 0, 'no lee periodos ajenos');
SELECT is((SELECT count(*)::int FROM public.budget_periods WHERE id = '9a000000-0000-4000-8000-0000000000d1'), 1, 'lee sus propios periodos');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status) VALUES ('9a000000-0000-4000-8000-000000000002','suplanto',100,'PEN','FINISHED')$$, '42501', NULL, 'no inserta presupuestos a nombre de otro usuario');
SELECT is((WITH u AS (UPDATE public.budgets SET name = 'hack' WHERE id = '9a000000-0000-4000-8000-0000000000b9' RETURNING 1) SELECT count(*)::int FROM u), 0, 'no actualiza presupuestos ajenos');
SELECT is((WITH u AS (UPDATE public.budget_periods SET limit_minor = 1 WHERE id = '9a000000-0000-4000-8000-0000000000d9' RETURNING 1) SELECT count(*)::int FROM u), 0, 'no actualiza periodos ajenos');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor) VALUES ('9a000000-0000-4000-8000-000000000002','9a000000-0000-4000-8000-0000000000b9','2026-10-26','2026-11-02',100)$$, '42501', NULL, 'no inserta periodos a nombre de otro usuario');
SELECT throws_ok($$INSERT INTO public.budget_periods (user_id, budget_id, starts_on, ends_on, limit_minor) VALUES ('9a000000-0000-4000-8000-000000000001','9a000000-0000-4000-8000-0000000000b9','2026-10-26','2026-11-02',100)$$, '23503', NULL, 'no cuelga periodos propios de un presupuesto ajeno');
SELECT throws_ok($$INSERT INTO public.budgets (user_id, name, limit_minor, currency_code, status, category_id) VALUES ('9a000000-0000-4000-8000-000000000001','catajena',100,'PEN','FINISHED','9a000000-0000-4000-8000-0000000000c3')$$, 'P0001', 'INVALID_CATEGORY: Category is not available for this user', 'categoria de otro usuario rechazada');
SELECT throws_ok($$DELETE FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b1'$$, '42501', NULL, 'DELETE directo no permitido');
SELECT throws_ok($$SELECT public.delete_budget_v1(jsonb_build_object('budget_id','9a000000-0000-4000-8000-0000000000b9'))$$, 'P0001', 'NOT_FOUND: Budget not found', 'no borra presupuestos ajenos por RPC');

-- Consumo neto (jerarquia sin doble conteo, reembolso imputado a la compra, sin otras monedas)
SELECT is((SELECT consumed_minor FROM public.get_budget_status('9a000000-0000-4000-8000-0000000000b1', '2026-06-15')), 9000::bigint,
    'total junio = compra 10000 + subcategoria 2000 - reembolso 3000; excluye VOIDED, USD, meta y transferencia');
SELECT is((SELECT consumed_minor FROM public.get_budget_status('9a000000-0000-4000-8000-0000000000b2', '2026-06-15')), 9000::bigint,
    'presupuesto de categoria padre incluye subcategorias una sola vez');
SELECT is((SELECT limit_minor FROM public.get_budget_status('9a000000-0000-4000-8000-0000000000b1', '2026-05-15')), 40000::bigint, 'usa el limite snapshot del periodo cuando existe');
SELECT is((SELECT consumed_minor FROM public.get_budget_status('9a000000-0000-4000-8000-0000000000b1', '2026-07-15')), 900::bigint, 'el reembolso de julio no reduce julio (se imputa a la compra de junio)');
SELECT is((SELECT count(*)::int FROM public.get_budget_status('9a000000-0000-4000-8000-0000000000b9', '2026-10-24')), 0, 'consumo de presupuesto ajeno no se expone');

-- Borrado (G5)
SELECT is(public.delete_budget_v1(jsonb_build_object('budget_id','9a000000-0000-4000-8000-0000000000b1'))->>'status', 'APPLIED', 'RPC borra el presupuesto propio');
SELECT is((SELECT count(*)::int FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b1') + (SELECT count(*)::int FROM public.budget_periods WHERE budget_id = '9a000000-0000-4000-8000-0000000000b1'), 0, 'definicion y periodos eliminados fisicamente');
SELECT is((SELECT count(*)::int FROM public.budget_tombstones WHERE budget_id = '9a000000-0000-4000-8000-0000000000b1'), 1, 'queda tombstone visible para su propietario');
SELECT is((SELECT count(*)::int FROM public.transactions WHERE user_id = '9a000000-0000-4000-8000-000000000001'), 8, 'borrar presupuesto no toca transactions');
SELECT is((SELECT count(*)::int FROM public.transaction_links WHERE link_type = 'REFUND'), 1, 'ni vinculos de reembolso');
SELECT is((SELECT count(*)::int FROM public.categories WHERE user_id = '9a000000-0000-4000-8000-000000000001'), 2, 'ni categorias');
SELECT lives_ok($$SELECT public.delete_budget_v1(jsonb_build_object('budget_id','9a000000-0000-4000-8000-0000000000b1'))$$, 'borrado idempotente');
SELECT throws_ok($$INSERT INTO public.budgets (id, user_id, name, limit_minor, currency_code, status) VALUES ('9a000000-0000-4000-8000-0000000000b1','9a000000-0000-4000-8000-000000000001','resucitado',100,'PEN','FINISHED')$$, 'P0001', 'BUDGET_DELETED: Budget 9a000000-0000-4000-8000-0000000000b1 was deleted and cannot be recreated', 'upsert atrasado no resucita el presupuesto');
SELECT throws_ok($$INSERT INTO public.budgets (id, user_id, name, limit_minor, currency_code, status) VALUES ('9a000000-0000-4000-8000-0000000000b1','9a000000-0000-4000-8000-000000000001','resucitado',100,'PEN','FINISHED') ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name$$, 'P0001', 'BUDGET_DELETED: Budget 9a000000-0000-4000-8000-0000000000b1 was deleted and cannot be recreated', 'tampoco con ON CONFLICT DO UPDATE (comando de sync)');

-- Soft-delete legado => borrado fisico + tombstone
SELECT is((WITH u AS (UPDATE public.budgets SET deleted_at = now() WHERE id = '9a000000-0000-4000-8000-0000000000b2' RETURNING 1) SELECT count(*)::int FROM u), 1, 'soft-delete legado actualiza una fila');
SELECT is((SELECT count(*)::int FROM public.budgets WHERE id = '9a000000-0000-4000-8000-0000000000b2') * 10 + (SELECT count(*)::int FROM public.budget_tombstones WHERE budget_id = '9a000000-0000-4000-8000-0000000000b2'), 1, 'soft-delete legado se convierte en borrado fisico con tombstone');

RESET ROLE;
SELECT is((SELECT count(*)::int FROM internal.sync_changes WHERE entity_type = 'BUDGET' AND operation = 'DELETE' AND entity_id = '9a000000-0000-4000-8000-0000000000b1'), 1, 'borrado publicado en sync_changes una sola vez');

-- Calendario (G1)
SELECT results_eq($$SELECT starts_on, ends_on FROM public.budget_period_bounds_v1('WEEKLY','2026-10-24')$$, $$VALUES ('2026-10-19'::date,'2026-10-26'::date)$$, 'semana lunes-domingo');
SELECT results_eq($$SELECT starts_on, ends_on FROM public.budget_period_bounds_v1('BIWEEKLY','2026-02-10')$$, $$VALUES ('2026-02-01'::date,'2026-02-16'::date)$$, 'primera quincena 1-15');
SELECT results_eq($$SELECT starts_on, ends_on FROM public.budget_period_bounds_v1('BIWEEKLY','2026-02-28')$$, $$VALUES ('2026-02-16'::date,'2026-03-01'::date)$$, 'segunda quincena 16-fin de mes (febrero)');
SELECT results_eq($$SELECT starts_on, ends_on FROM public.budget_period_bounds_v1('MONTHLY','2026-12-31')$$, $$VALUES ('2026-12-01'::date,'2027-01-01'::date)$$, 'mes calendario');
SELECT results_eq($$SELECT starts_on, ends_on FROM public.budget_period_bounds_v1('CUSTOM_RANGE','2026-06-12','2026-06-10','2026-06-20')$$, $$VALUES ('2026-06-10'::date,'2026-06-21'::date)$$, 'rango custom con fin inclusivo => exclusivo');

SELECT * FROM finish();
ROLLBACK;

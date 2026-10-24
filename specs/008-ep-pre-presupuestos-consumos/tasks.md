# Tasks: EP-PRE — Presupuestos y Consumos

**Input**: Artefactos de `specs/008-ep-pre-presupuestos-consumos/`<br>
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`
**Scope**: Futuro trabajo de implementación; NO realizado en esta tarea. `[P]` indica archivos/trabajo separables sin dependencias incompletas. Dependencias se indican como `depends on Txxx`.

## Fase 1: Setup / descubrimiento obligatorio

- [ ] T001 Verificar HU-14/HU-18/HU-19/HU-25/HU-42/HU-57, inspeccionar código actual de categorías/movimientos/planes/notificaciones, identificar estado real y registrar evidencia en `specs/008-ep-pre-presupuestos-consumos/research.md` (gate previo; no asumir completitud).
- [ ] T002 Resolver G1-G6 con responsables de EP-MOV, EP-NOT, bases/sync y producto; actualizar decisiones aprobadas y `specs/008-ep-pre-presupuestos-consumos/{research.md,plan.md,data-model.md}` antes de abrir implementación, incluida elegibilidad de moneda y explicación al usuario.
- [X] T003 [P] Definir dataset sintético y matriz de pruebas financiera/propiedad/sync en `app/src/test/java/com/kipu/app/feature/budgets/` conforme `specs/008-ep-pre-presupuestos-consumos/quickstart.md` (no incluir fixtures como aceptación HU-25).

## Fase 2: Fundacional (bloqueante)

- [X] T004 Aprobar y documentar el modelo Room/PostgreSQL, estados, rango/calendario, snapshot local por periodo y plan de migración/rollback en `data-model.md` y `plan.md`; registrar la diferencia de snapshot/pending con el SQL de referencia (depends on T001,T002).
- [X] T005 Implementar y ejecutar en emulador la migración Room aditiva 25→26; preservar filas de cuentas, movimientos y ledger financiero (`BudgetRoomMigrationTest`, `:app:connectedDebugAndroidTest`) (depends on T004).
- [X] T006 [P] Implementar migración PostgreSQL/RLS/índices en `supabase/migrations/20261024120000_ep_pre_budgets.sql` y pruebas pgTAP (61/61); no aplicada al remoto (depends on T004).
- [X] T007 Integrar el lector de consumo existente de EP-MOV, jerarquía real de categorías y centro de avisos `BUDGET_ALERT` con dedupe owner/budget/period/threshold (depends on T001,T002,T004).
- [X] T008 Implementar entidades/DAO/repository owner-scoped, escrituras locales transaccionales, outbox y tombstones (depends on T005,T007).

## Fase 3: User Story 1 — HU-36 Crear y consultar (P1)

**Goal**: Crear presupuesto total/categoría/subcategoría, respetar dos presupuestos activos Free y mostrar límite/consumo/restante/porcentaje.<br>
**Independent Test**: Alta local con referencia propia y consulta sintética de gastos elegibles; HU-25 no se considera demostrada por fixture.

- [X] T009 [P] [US1] Probar validaciones de importes/moneda, periodos aprobados y rangos invertidos en `app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetValidationTest.kt` (depends on T004).
- [ ] T010 [P] [US1] (parcial: jerarquía/moneda/neto cubiertas en `BudgetHierarchyConsumptionTest` y `BudgetConsumptionTest`; falta `ExpenseEligibilityTest` contra el query real, que pertenece a EP-MOV) Probar elegibilidad y exclusión de pendientes/VOIDED/transferencias/principal/metas en `app/src/test/java/com/kipu/app/feature/budgets/domain/ExpenseEligibilityTest.kt` (depends on T007).
- [X] T011 [US1] Implementar reglas de alta, consulta, money exacto y estado/vigencia en `app/src/main/java/com/kipu/app/feature/budgets/domain/` (depends on T008,T009,T010).
- [X] T012 [US1] Integrar quota policy de HU-57 para límite Free de dos activos sin eliminar/ocultar objetos en `app/src/main/java/com/kipu/app/feature/budgets/domain/` y adaptador de planes existente (depends on T011).
- [X] T013 [US1] Integrar consumo derivado por movimiento único, categoría/jerarquía, owner/currency/period mediante `ExpenseConsumptionRepository` y el query real (depends on T011).
- [X] T014 [US1] (capa presentation completa: `BudgetFormViewModel`/`BudgetFormScreen`, validación y aviso de vigencia; verificada con tests JVM; la persistencia real la aporta T016) Implementar alta/edición básica de presupuesto en UI/estados en `app/src/main/java/com/kipu/app/feature/budgets/presentation/` (depends on T011,T012).
- [X] T015 [US1] (capa presentation completa: lista, detalle, barra accesible; consumo vía casos de uso; verificada con tests JVM; el lector de datos real es T013) Implementar lista/detalle/consulta con limit, consumed, remaining y porcentaje en `app/src/main/java/com/kipu/app/feature/budgets/presentation/` (depends on T013,T014).
- [X] T016 [US1] Integrar persistencia local-before-success y comandos de replay idempotentes; probar repositorio Room en emulador (reintento de renovación sin periodo duplicado) (depends on T008,T013).
- [ ] T017 [US1] (parcial: `BudgetScreensTest` compila y cubre alta, consulta, vacío, error y cupo; NO ejecutado en emulador y falta escenario offline real, que depende de T016) Añadir test UI/instrumentado de alta, consulta, vacío, error, offline y límite de cupo en `app/src/androidTest/java/com/kipu/app/feature/budgets/` (depends on T014,T015,T016).

## Fase 4: User Story 2 — HU-37 Renovación, consumo jerárquico y alertas (P2)

**Goal**: Una vigencia por intervalo, jerarquía sin doble conteo, alertas deduplicadas y reembolso HU-25 real al periodo original.<br>
**Independent Test**: Ciclo/alerta en integración local; aceptación completa requiere capacidades reales HU-25 y HU-42 (T002/T007), no mocks.

- [X] T018 [P] [US2] Probar periodos recurrentes/custom, límites snapshot y retries/concurrencia en `app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetPeriodRenewalTest.kt` (depends on T008).
- [X] T019 [P] [US2] Probar ids de gastos únicos en alcance padre/hijo/total sin doble conteo en `app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetHierarchyConsumptionTest.kt` (depends on T013).
- [X] T020 [US2] Implementar creación idempotente de vigencia y finalización automática de rango custom vencido, conservando snapshots de periodo (depends on T018).
- [X] T021 [US2] Implementar cruces editables de umbral y deduplicación por propietario/presupuesto/periodo/threshold en el centro de notificaciones (depends on T007,T020).
- [ ] T022 [US2] Implementar recalculo por reembolso HU-25 en el periodo de compra. Abierto: el modelo/query actual de EP-MOV no expone un vínculo real compra↔reembolso ni la fecha de imputación; G2 prohíbe inferirlo o cambiar reglas financieras desde EP-PRE (depends on T002,T007,T020).
- [ ] T023 [US2] (parcial: historial con límite snapshot en detalle hecho y probado; falta la representación del aviso `BUDGET_ALERT` en el centro de avisos, que depende de T021) Implementar consulta de historial de periodos y representación de alertas en `app/src/main/java/com/kipu/app/feature/budgets/presentation/` (depends on T020,T021).
- [ ] T024 [US2] Añadir integración E2E real HU-25 + HU-42, cross-period, replay y reentrada, en `app/src/androidTest/java/com/kipu/app/feature/budgets/` (depends on T021,T022,T023).

## Fase 5: User Story 3 — HU-38 Editar, finalizar y eliminar (P3)

**Goal**: Editar límite/alcance según regla temporal, finalizar sin borrar historia, eliminación confirmada de planificación solamente.<br>
**Independent Test**: Comparar datos financieros antes/después de edición, finalización, confirmación y cancelación; debe pasar solo después de HU-36/37.

- [X] T025 [P] [US3] Probar edición de límite, inmutabilidad de periodo cerrado, finalización y cancelación del borrado en `app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleTest.kt` (depends on T020).
- [X] T026 [US3] Implementar cambio de límite actual/futuro, preservación de snapshot y restricciones de cambio de scope/schedule en `app/src/main/java/com/kipu/app/feature/budgets/domain/` (depends on T020,T025).
- [X] T027 [US3] Finalizar detiene renovaciones/alertas y conserva consulta histórica; incluye fin natural automático de `CUSTOM_RANGE` vencido (depends on T021,T026).
- [X] T028 [US3] Implementar borrado local transaccional, tombstone y sync RPC conforme G5, sin tocar movimientos (depends on T004,T008,T027).
- [ ] T029 [US3] (parcial — datos: tests Room comparan cuenta, movimiento y asiento antes/después del borrado y rechazan recrear un presupuesto tombstoned; falta E2E de CRUD completo y replay remoto atrasado) (depends on T028).

## Fase 6: Polish transversal y aceptación

- [X] T030 [P] Auditar RLS/owner-bound references y acceso cross-user de presupuestos/periodos mediante tests en `supabase/tests/ep_pre_budget_rls.sql` (depends on T006,T008).
- [ ] T031 [P] (parcial: semántica de barra, roles, contentDescription y confirmaciones cubiertos en `BudgetScreensTest`, sin ejecutar en dispositivo; falta auditoría TalkBack manual y offline) Auditar TalkBack, foco, errores/empty/offline/cupo/confirmación destructiva y textos accesibles en `app/src/androidTest/java/com/kipu/app/feature/budgets/` (depends on T017,T024,T029).
- [ ] T032 Ejecutar la estrategia de `specs/008-ep-pre-presupuestos-consumos/quickstart.md`, build/tests aplicables, migración representativa y regresiones EP-MOV; registrar salida y revisión cruzada sin afirmar resultado antes de ejecución (depends on T017,T024,T029,T030,T031).
- [ ] T033 Actualizar `specs/008-ep-pre-presupuestos-consumos/{spec.md,plan.md,data-model.md,tasks.md}` solo con decisiones/evidencia aprobadas, y enlazar resultados; nunca sustituir gates por asunciones (depends on T032).

## Cobertura trazable

### FR a tareas

| Requisito | Tareas |
|---|---|
| FR-001 | T009,T011,T014 |
| FR-002 | T004,T009,T011 |
| FR-003 | T011,T013,T015 |
| FR-004 | T010,T013,T019 |
| FR-005 | T012,T017 |
| FR-006 | T006,T008,T030 |
| FR-007 | T018,T020 |
| FR-008 | T018,T020 |
| FR-009 | T019,T013 |
| FR-010 | T021,T023,T024 |
| FR-011 | T018,T020 |
| FR-012 | T002,T007,T022,T024 |
| FR-013 | T025,T026 |
| FR-014 | T021,T027 |
| FR-015 | T028,T029 |
| FR-016 | T025,T028,T029 |
| FR-017 | T010,T013,T030 |
| FR-018 | T005,T008,T016,T020,T028,T029 |
| FR-019 | T012,T017,T030 |

### SC a tareas

| Criterio | Tareas |
|---|---|
| SC-001 | T004,T009,T011,T014 |
| SC-002 | T010,T013,T015,T016 |
| SC-003 | T019,T024 |
| SC-004 | T018,T020,T024 |
| SC-005 | T002,T022,T024 |
| SC-006 | T021,T023,T024,T027 |
| SC-007 | T025,T028,T029 |
| SC-008 | T012,T017 |
| SC-009 | T005,T006,T016,T024,T029,T030,T031,T032 |

### HU a tareas

- HU-36 / US1: T001–T017; gates dependencias confirmados en T001/T002.
- HU-37 / US2: T018–T024; gates HU-25/HU-42 en T002/T007.
- HU-38 / US3: T025–T029; requiere HU-36/37 y G5.

## Dependencias y DAG de tareas

```text
T001 ─┬─> T002 ─┬─> T004 ─┬─> T005 ─┐
      │         │         ├─> T006  │
      │         │         ├─> T007 ─┴─> T008 ─> T009/T010/T018
      │         │         └───────────> T011 ─> T012/T013 ─> T014/T015/T016 ─> T017
      │         └────────────────────────────> T007
      └─> T003
T008/T013 ─> T019 ─┐
T018 ─> T020 ──────┼─> T021 ─> T023 ─┐
T002/T007/T020 ────────> T022 ─────────┴─> T024
T020 ─> T025 ─> T026 ─> T027 ─> T028 ─> T029
T006/T008 ─> T030; T017/T024/T029 ─> T031 ─┐
T017/T024/T029/T030/T031 ───────────────────> T032 ─> T033
```

Este grafo es acíclico. Paralelismo seguro: T003 tras T001; T005/T006 después de T004 y en archivos diferentes; T009/T010 luego de T008/T007; T018/T019; T030 durante implementación de historias después de T006/T008; T031 después de E2E individuales.

## Estrategia incremental

1. Cerrar setup/gates y fundación sin introducir funciones ad hoc.
2. MVP funcional de presupuesto es US1, pero implementación no puede pasar T002/T004.
3. Integrar HU-25/HU-42 reales para completar US2.
4. Implementar ciclo destructivo y sync después de las historias anteriores.
5. DoD transversal solo se acepta con evidencia ejecutada, revisión cruzada y criterios financieros/seguridad cubiertos.

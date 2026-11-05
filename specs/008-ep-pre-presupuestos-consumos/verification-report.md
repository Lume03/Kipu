# EP-PRE — Informe de Verificación y Aprobación Final

**Fecha**: 2026-11-05
**Alcance**: HU-36 (Crear y consultar presupuestos), HU-37 (Renovación, consumo jerárquico y alertas), HU-38 (Editar, finalizar y eliminar).
**Target**: Local Supabase (127.0.0.1:54322) & Samsung Galaxy A16 (`SM-A165M`, Android 16) / Samsung Galaxy S24+ físico.
**Veredicto General**: **APPROVED** (con auditoría manual de accesibilidad TalkBack pendiente del Owner).

---

## 1. Resumen Ejecutivo de la Épica

La Épica **EP-PRE** (Presupuestos y Consumos, correspondiente a las historias **HU-36**, **HU-37** y **HU-38**) ha completado su implementación, pruebas de base de datos, integración local-first y validación en hardware:

1. **Base de Datos PostgreSQL (Supabase)**:
   - 108 de 108 pruebas pgTAP específicas de presupuestos ejecutadas con resultado **PASS (Exit Code 0)**:
     - `ep_pre_budget_rls.test.sql`: 61 / 61 PASS (aislamiento por propietario, RLS estricto y prevención de accesos cruzados).
     - `ep_pre_budget_scope_snapshot.test.sql`: 47 / 47 PASS (alcance por periodo snapshot, columnas pending_* y cálculo exacto de estado).
   - Migraciones aditivas aplicadas y sincronizadas en local y remoto (114 migraciones totales al 2026-11-05):
     - `20261024120000_ep_pre_budgets.sql`
     - `20261024190000_ep_pre_budget_scope_snapshot.sql`
   - Suite total de base de datos: 74 archivos / 1.422 tests pgTAP en verde.

2. **Capa Android & Persistencia Room**:
   - Migración Room 25→26 (`MIGRATION_25_26`): preservación íntegra de cuentas, movimientos y ledger financiero validada.
   - Pruebas unitarias de presupuestos: 101/101 PASS en 16 suites JVM (`BudgetValidationTest`, `ExpenseEligibilityTest`, `BudgetPeriodRenewalTest`, `BudgetHierarchyConsumptionTest`, `BudgetLifecycleTest`, `BudgetRemoteMappingsTest`).
   - Pruebas instrumentadas en hardware Samsung Galaxy A16 (`SM-A165M`, 2026-11-05):
     - `com.kipu.app.feature.budgets.data.sync.BudgetOfflineRestartIntegrationTest`: 1 PASS (HU-36 E6 / T017: creación offline, persistencia durable, supervivencia al reinicio de Room y sincronización con reintento sin pérdida ni duplicados).
     - `com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest`: 2 PASS (recálculo exacto de consumo por reembolsos HU-25 y refresco de patrimonio neto / gasto sin alterar periodos cerrados).
   - Pruebas instrumentadas previas en hardware físico Samsung Galaxy S24+ (2026-10-26):
     - 32/32 tests PASS (`OK (32 tests)`, 34,042 s): formularios crear/editar, selector de categorías global compartido, catálogos de iconos, periodos recurrentes y personalizados, confirmaciones y máscaras de privacidad.
   - Pruebas de integración previas en emulador:
     - `BudgetCrudFinancialIsolationIntegrationTest`: 1 PASS (tombstones y aislamiento financiero).
     - `BudgetTombstoneReplayIntegrationTest`: 1 PASS (replay remoto diferido sin resucitar objetos).
     - `BudgetHierarchyAlertsIntegrationTest`: 1 PASS (renovación y deduplicación de alertas).

---

## 2. Evidencia QA Previa

- `docs/qa/presupuestos-visual-2026-10-26.md`:
  - Formulario unificado de crear y editar con límite manual exacto, moneda y alcance en dos cajas, selector compartido de categorías con iconos jerárquicos.
  - Lista con tarjetas compactas y colores de consumo por umbrales configurados.
  - Finalizados como desplegable inferior sin filtros innecesarios.
  - Confirmación explícita de creación y finalización detallando historial conservado y cupo liberado.
  - Eliminación con diálogo de confirmación que distingue el presupuesto de saldos y movimientos, preservando el ledger intacto.
  - Verificación en hardware físico S24+ con fuentes estándar y aumentada (1,3x).
- `docs/qa/planificacion-2026-10-25.md` y `docs/qa/sincronizacion-planificacion-2026-10-25.md`:
  - Contratos de outbox y sincronización de planificaciones validados.

---

## 3. Cambios y Verificaciones de esta Ronda (2026-11-05)

- **Cierre de T017 (Resiliencia Offline)**:
  - Verificación automatizada en Galaxy A16 del escenario offline completo mediante `BudgetOfflineRestartIntegrationTest::offlineCreationSurvivesNewRoomInstanceAndSyncRetryWithoutLossOrDuplicates` (1 PASS).
  - La creación sin red persiste de forma local-first; al cerrar Room y abrir una nueva instancia, los datos y comandos de sincronización pendientes sobreviven intactos y se despachan al recuperar conectividad.
- **Integración con Reembolsos (HU-25/HU-37)**:
  - Verificación en Galaxy A16 mediante `MovementRefundBudgetIntegrationTest` (2 PASS) del recálculo de gasto y consumo presupuestario tras reembolsos parciales o totales entre meses distintos.

---

## 4. Pendientes y Bloqueos

- **TalkBack Manual (T031)**: Pendiente auditoría manual de accesibilidad TalkBack por parte del Owner. La semántica de Compose, `contentDescription`, roles y confirmaciones ya están cubiertos y probados en `BudgetScreensTest` y en pruebas instrumentadas.
- **Dispositivo Samsung Galaxy S24**: La tanda de instrumentación automatizada de esta ronda no se ejecutó en el S24 debido a conflicto de firmas con el APK debug (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); el S24 conservó su versión previa sin alteraciones y la validación en hardware se completó exitosamente en el Samsung Galaxy A16 (`SM-A165M`).

---

## 5. Matriz de Evidencias

| Componente / Suite | Tipo | Resultado | Estado |
|---|---|---|---|
| `ep_pre_budget_rls.test.sql` | pgTAP (DB) | 61 / 61 PASS | ✅ Aprobado |
| `ep_pre_budget_scope_snapshot.test.sql` | pgTAP (DB) | 47 / 47 PASS | ✅ Aprobado |
| Migración Room 25→26 | AndroidTest | 1 / 1 PASS | ✅ Aprobado |
| Unitarias de dominio y presentación | JVM UnitTest | 101 / 101 PASS | ✅ Aprobado |
| `BudgetOfflineRestartIntegrationTest` | AndroidTest (A16) | 1 / 1 PASS | ✅ Aprobado |
| `MovementRefundBudgetIntegrationTest` | AndroidTest (A16) | 2 / 2 PASS | ✅ Aprobado |
| Suites visuales y pantallas (S24+) | AndroidTest (S24+) | 32 / 32 PASS | ✅ Aprobado |
| Aislamiento financiero y tombstones | AndroidTest | 2 / 2 PASS | ✅ Aprobado |
| `git diff --check` | Calidad | 0 advertencias | ✅ Aprobado |

---

## 6. Veredicto por Historia de Usuario

| Historia de Usuario | Veredicto | Resumen de Justificación |
|---|---|---|
| **HU-36** (Crear y consultar presupuestos) | **CUMPLIDO** | Creación total y por categoría, cálculo exacto de consumo/restante, límite de 2 activos en plan Free, y persistencia offline con reintento (T017) verificados en hardware. |
| **HU-37** (Renovación, jerarquía y alertas) | **CUMPLIDO** | Snapshots inmutables de periodo, acumulación jerárquica sin doble cómputo, alertas `BUDGET_ALERT` deduplicadas en notificación center y recálculo por reembolsos HU-25. |
| **HU-38** (Editar, finalizar y eliminar) | **CUMPLIDO** | Edición de límites preservando snapshots, finalización sin borrado de historial con liberación de cupo, y borrado lógico vía tombstones sin impacto en finanzas. |
| **Accesibilidad TalkBack** (T031) | **VALIDACIÓN PENDIENTE** | Estructura y semántica completas; pendiente recorrido auditivo manual por el Owner. |

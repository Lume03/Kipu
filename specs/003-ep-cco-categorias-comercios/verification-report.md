# Reporte de Verificación — EP-CCO Categorías, Subcategorías y Comercios

**Fecha:** 2026-11-05  
**Épica:** `003-ep-cco-categorias-comercios`  
**Veredicto:** **CUMPLIDO (APPROVED)**  
**Línea base / HEAD:** `main` (ronda de corrección y reconciliación 2026-11-05)  

---

## 1. Alcance

El alcance de EP-CCO comprende la clasificación contable en dos niveles, la personalización visual de categorías, la búsqueda y asignación independiente de comercios del catálogo, las reglas de alias con preservación de texto original, las preferencias personales por comercio, el descubrimiento de comercios en Logo.dev y la recuperación de logos con render determinista:
- **HU-14**: Separar categorías de gastos e ingresos (tipos `EXPENSE`, `INCOME` y `GENERAL`; herencia estricta en subcategorías).
- **HU-15**: Personalizar catálogo de categorías (nombre, icono y color sin romper enlaces de movimientos históricos; cupo Free de 5 raíces de gasto y 5 de ingreso).
- **HU-16**: Conservar texto original y reutilizar alias de comercio (búsqueda normalizada por igualdad exacta, preservación de texto fuente, revisión ante ambigüedad y puerta Premium).
- **HU-17**: Preferencia personal de categoría por comercio (relación privada futura comercio → categoría elegible, sin alterar la historia ni afectar a otros usuarios).
- **Bloque 3 (Catálogo Base del Sistema)**: Tipificación de las 51 entradas del catálogo predeterminado Kipu a `EXPENSE` (12 raíces y 39 subcategorías) y filtros en selectores de movimiento.
- **M3 (Merchant Discovery)**: Búsqueda remota bajo demanda en Logo.dev vía Edge Function `merchant-search`, creación de comercios privados owner-scoped y altas globales compartidas con deduplicación por dominio.
- **Bloque 4 (Comercios Compartidos y Logos)**: Room v39 con persistencia de dominio, estado y origen de logo; reintento de descargas fallidas; interruptor `shared_additions_enabled` y política visual de avatares `MovementAvatarPolicy.kt`.

---

## 2. Evidencia Automatizada

### 2.1 Pruebas Unitarias (JVM / Robolectric / Deno)
- **Suite global ejecutada (`:app:testDebugUnitTest`)**: 1.678 pruebas totales (1.675 PASS, 0 FAIL, 3 SKIPPED).
- **Bloque 3 — Categorías y Subcategorías**:
  - `CategoryCatalogHydrationTest`, `CategoryCatalogMigrationTest`, `CategoryCatalogRegressionTest`: 85 tests PASS. Verifican la corrección a `EXPENSE` de las 12 raíces y 39 subcategorías, la resolución de 11 iconos faltantes de subcategorías y la inicialización offline.
  - `MovementCategoryOptionsTest`, `QuickMovementViewModelTest`, `MovementEditorRememberTest`, `OfflineFirstCategoriesRepositoryTest`: rechazo de selección incompatible de categoría según tipo contable del movimiento, preservando selecciones históricas.
- **M3 — Descubrimiento de Comercios**:
  - 9 tests Kotlin (`MerchantDiscoveryRepositoryTest`, `MerchantDiscoveryRoomTest`, `MerchantDiscoveryViewModelTest`).
  - 38 pruebas Deno PASS (`supabase/functions/merchant-search/index_test.ts` y `merchant-logo-sync/index_test.ts`) sin requerir permisos de red.
- **Bloque 4 — Comercios Compartidos y Logos**:
  - 23 pruebas Kotlin JVM PASS (`MerchantDiscoveryRepositoryTest`, `MerchantLogoPersistenceTest`, `MovementAvatarPolicyTest`, `MerchantPickerDiscoveryTest`, `MovementPresentationObservationTest`).
  - 41 pruebas Deno PASS en funciones Edge (`merchant-search`, `merchant-logo-sync`).
- **Sprint 5 Base (HU-16 / HU-17)**:
  - 472 pruebas unitarias PASS (`CategoryRulesTest`, `MerchantAliasRulesTest`, `MerchantCategoryPreferenceTest`, `MerchantRuleModelsTest`, `ScopeBoundaryTest`, `MovementClassificationIntegrityTest`).

### 2.2 Pruebas Instrumentadas en Hardware (Galaxy A16 / Android 16)
Ejecutadas en dispositivo físico Samsung Galaxy A16 (`SM-A165M`, Android 16) en la ronda 2026-11-05:
- `com.kipu.app.feature.movements.data.local.MovementHistoryQueryTest`: **11 PASS A16 2026-11-05** (consultas filtradas por dueño y categorías).
- `com.kipu.app.feature.movements.data.local.MovementLocalDataSourceTest`: **18 PASS A16 2026-11-05** (persistencia y lectura de movimientos clasificados).
- `com.kipu.app.navigation.ObjectSourceNavigationTest`: **4 PASS A16 2026-11-05** (navegación e integridad de referencias).
- Pruebas instrumentadas previas en SM-A165M: `MerchantPickerTest` (7 PASS), `CategoriesScreenTest`, `CategoryTypeMigrationTest` (Room 9→10), `CategoryDatabaseTest` (Room 3→4), `MerchantRuleDatabaseTest` (Room 18→19).

*Nota sobre Galaxy S24*: El dispositivo Galaxy S24 (`SM-S926B`) no fue ejecutado en esta ronda debido a incompatibilidad de firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

### 2.3 Base de Datos y Contratos (PostgreSQL / pgTAP)
- **Ejecución local 2026-11-05**: 74 archivos / 1.422 pruebas pgTAP PASS (exit 0).
- **Suites pgTAP específicas de EP-CCO**:
  - `system_category_catalog.test.sql`: 20 aserciones PASS.
  - `category_type_test.sql`: 14 aserciones PASS.
  - `category_merchant.test.sql`: 22 aserciones PASS.
  - `merchant_discovery_test.sql`: 20/20 PASS.
  - `merchant_logo_block4.test.sql`: 17/17 PASS.
  - `merchant_logo_repair_block4.test.sql`: 5/5 PASS.
  - `merchant_rules_s5.test.sql`: 75 aserciones PASS (aislamiento owner-scoped, no mutación del catálogo, idempotencia).
- **Despliegue Remoto (2026-11-05)**:
  Se aplicaron exitosamente en el servidor Supabase remoto las migraciones:
  - `20261104051901_system_category_expense_catalog.sql`: 12 raíces SYSTEM y 39 subcategorías actualizadas a `EXPENSE` (0 subcategorías de usuarios afectadas).
  - `20261104053612_merchant_discovery_logo_recovery.sql`: recuperación de logos reintentables e interruptor `shared_additions_enabled`.
  - `20261105180419_reactivate_goal_premium_entitlement.sql`.
  - `20261105190000_merchant_catalog_audit_fk_index.sql`: índice de clave foránea en `merchant_catalog_audit.added_by`.
  **Estado final**: Remoto = local = 114 migraciones aplicadas. El gate de desalineación remota queda formalmente cerrado y resuelto.

---

## 3. Evidencia QA Previa

- **`docs/qa/category-catalog-block3-verification.md`**: Corrección y verificación de las 51 categorías predeterminadas Kipu a `EXPENSE`, Room v38 y selectores de movimiento.
- **`docs/qa/m3-merchant-discovery-verification.md`**: Implementación y verificación del buscador Logo.dev, rate limits y comercios privados.
- **`docs/qa/merchant-logo-block4-verification.md`**: Soporte de Room v39, render determinista de avatares `MovementAvatarPolicy.kt` y recuperación de logos reintentables.
- **`docs/qa/ui-catalog-2026-11-04`**: Revisión de diseño visual del catálogo y tokens de interfaz.

---

## 4. Cambios y Reconciliaciones en Esta Ronda (2026-11-05)

1. **Catálogo predeterminado SYSTEM a EXPENSE**: Se registró formalmente en la especificación, plan y tareas la migración remota `20261104051901_system_category_expense_catalog.sql` y Room v38 (`38.json`). 12 raíces y 39 subcategorías son ahora formalmente de tipo `EXPENSE`; 0 subcategorías de usuario resultaron afectadas.
2. **Reconciliación de Room v38 y v39**: Se actualizó el plan para incorporar la cadena evolutiva de Room: v3→v4 (S2 base), v18→v19 (S5 alias y preferencias), v35 (aislamiento por propietario en comercios), v38 (catálogo base a EXPENSE) y v39 (persistencia de dominio, estado y origen de logo).
3. **Comercios compartidos y control operativo**: Se registró la aplicación remota de `20261104053612_merchant_discovery_logo_recovery.sql`, permitiendo reintentar la descarga de logos pendientes/fallidos tras 5 minutos sin consumir cupos de alta, e introduciendo el control `shared_additions_enabled` en `internal.merchant_discovery_settings`.
4. **Política de Avatares**: Se reconcilió la especificación con la política implementada en `MovementAvatarPolicy.kt`, asegurando que transacciones vinculadas a un comercio sin logo muestren iniciales deterministas en lugar de degradar a icono de categoría.
5. **Índice FK `merchant_catalog_audit.added_by`**: Añadido en la migración `20261105190000` y desplegado en el servidor remoto el 2026-11-05.

---

## 5. Pendientes y Bloqueos

1. **Ejecución en Galaxy S24**: Bloqueada en esta tanda por incompatibilidad de firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
2. **Llamadas en vivo a proveedor Logo.dev**: Las pruebas ejecutadas operan con mocks y fixtures Deno; la integración en producción depende de las credenciales de proveedor provistas vía secrets en el despliegue del release.

---

## 6. Veredicto por Historia de Usuario

| Historia de Usuario / Componente | Descripción | Estado | Evidencia Principal |
|---|---|---|---|
| **HU-14** | Separar categorías de gastos e ingresos | **CUMPLIDO** | `CategoryRulesTest`, Room 9→10, `category_type_test.sql`, `MovementCategoryOptionsTest` |
| **HU-15** | Personalizar catálogo y presentaciones | **CUMPLIDO** | `CategoryCatalogHydrationTest`, `CategoryCatalogMigrationTest`, `CategoriesScreenTest`, 110 pgTAP |
| **HU-16** | Conservar texto original y alias de comercio | **CUMPLIDO** | `MerchantAliasRulesTest`, Room 18→19, `merchant_rules_s5.test.sql`, 472 unitarios |
| **HU-17** | Preferencia personal de categoría por comercio | **CUMPLIDO** | `MerchantCategoryPreferenceTest`, `merchant_rules_s5.test.sql`, `OfflineFirstCategoriesRepositoryTest` |
| **Catálogo Base (Bloque 3)** | 51 categorías base SYSTEM a `EXPENSE` | **CUMPLIDO** | Migración remota `20261104051901`, Room v38 (`38.json`), 0 subcategorías de usuario afectadas |
| **M3 Discovery** | Búsqueda remota en Logo.dev y comercios privados | **CUMPLIDO** | Migración `20261102222216`, Room v35 (`35.json`), 38 Deno PASS, 20 pgTAP PASS |
| **Bloque 4 Logos** | Room v39, avatares y recuperación de logos | **CUMPLIDO** | Migración remota `20261104053612`, Room v39 (`39.json`), `MovementAvatarPolicyTest`, 42 pgTAP PASS |

**Veredicto Final EP-CCO:** **CUMPLIDO (APPROVED)**.

# Reporte de Verificación Consolidado — EP-PLA Planes y Monetización

**Fecha:** 2026-11-05  
**Épica:** `012-ep-pla-planes-monetizacion`  
**Veredicto:** **PARCIAL / BLOQUEADO EN CAPACIDADES EXTERNAS**  
**Línea base / HEAD:** `main` (ronda de corrección 2026-11-05)  
**Consolida:** `validation/quickstart-results.md` y `validation/security-release-audit.md` con la evidencia de ejecución de la ronda 2026-11-05.

---

## 1. Alcance

La épica EP-PLA abarca el modelo comercial de Kipu, la distinción de capacidades Free vs. Premium, la política de acceso desconectada, la sincronización de cupos informativos/downgrade y el ciclo de vida de compras y suscripciones:
- **HU-52 (Sprint 1)**: Elegir Free o conocer la prueba Premium voluntaria (Pantalla 1B, texto de Trial solo si elegible).
- **HU-57 (Sprint 2)**: Cupos, selección de excedentes y downgrade (regla de 4 instrumentos computables con débito compartido; deudas, metas, presupuestos y categorías).
- **HU-53 (Sprint 3)**: Comprar suscripciones y plan Lifetime vía Google Play Billing.
- **HU-54 (Sprint 3)**: Verificación remota autenticada de compras de Google Play y deduplicación por token SHA-256.
- **HU-55 (Sprint 5)**: Reconciliación asíncrona mediante Real-Time Developer Notifications (RTDN Pub/Sub) y restauración de compras.
- **HU-56 (Sprint 3)**: Estados de suscripción, periodos de gracia, retención de historial tras cancelación o expiración.
- **HU-58 (Sprint 4)**: Autorización y aplicación de capacidades Free y Premium (núcleo local Free, OCR local de comprobantes Yape permitido en Free con consentimiento y revisión; filtro avanzado por fuente Manual / Notificación / OCR como capacidad Premium).
- **HU-59 (Sprint 4)**: Concesión offline de entitlement firmada por servidor (ES256), ligada a dispositivo/Keystore, acotada por reloj monotónico y boot count (máx. 72 horas).

---

## 2. Evidencia Automatizada

### 2.1 Pruebas Unitarias (JVM / Robolectric / Deno)
- **Suite global ejecutada (`:app:testDebugUnitTest`)**: 1.678 pruebas totales (1.675 PASS, 0 FAIL, 3 SKIPPED).
- **Cobertura unitaria de EP-PLA**:
  - `FeatureAccessPolicyTest` y `OfflineEntitlementLeasePolicyTest`: validación de capacidades Free/Premium, expiración de lease, rechazo de civil-clock y fallback no destructivo.
  - `OfflineEntitlementGrantVerifierTest`: verificación de firmas ES256, thumbprints P-256 y reclamos del token.
  - `PlanSelectionViewModelTest` y `PlansMovementEntitlementProviderTest`: selección de planes, mapeo de elegibilidad y aislamiento de facturación.
  - `MovementHistoryAccessPolicyTest` y `MovementHistoryViewModelTest`: acceso condicional a filtros avanzados, estados de reconexión/upsell amigables y preservación de filtros básicos.
  - Pruebas unitarias de facturación y RTDN: 244 tests JVM de billing (Sprint 3), 36 pruebas Deno PASS en `play-rtdn` y `reconcile-billing` (Sprint 5), 17 pruebas Deno PASS en `verify-purchase`.

### 2.2 Pruebas Instrumentadas en Hardware (Galaxy A16 / Android 16)
Ejecutadas en dispositivo físico Samsung Galaxy A16 (`SM-A165M`, Android 16) en la ronda 2026-11-05:
- `com.kipu.app.core.database.MovementSourceMigrationTest`: **1 PASS A16 2026-11-05** (migración Room 39→40 con persistencia de columna `capture_source` y backfill owner-scoped).
- `com.kipu.app.feature.movements.presentation.MovementSourceFiltersTest`: **4 PASS A16 2026-11-05** (filtro avanzado por fuente Manual / Notificación / OCR, chips de selección múltiple, bloqueo/upsell en Free).
- `com.kipu.app.feature.movements.data.local.MovementHistoryQueryTest`: **11 PASS A16 2026-11-05** (consultas con criterios avanzados y fallback en Free).
- `com.kipu.app.feature.movements.data.local.MovementLocalDataSourceTest`: **18 PASS A16 2026-11-05**.
- `com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest`: **25 PASS A16 2026-11-05**.
- `com.kipu.app.feature.settings.presentation.SettingsFinancialIntegrityTest`: **4 PASS A16 2026-11-05**.
- `com.kipu.app.navigation.ObjectSourceNavigationTest`: **4 PASS A16 2026-11-05**.
- `com.kipu.app.navigation.PlanningRemoteAcceptanceTest`: 4 omitidos por opt-in remoto desactivado (`assumeTrue`).
- **Pruebas instrumentadas previas documentadas**:
  - Pixel 8 (API 36): 31 pruebas instrumentadas de selección de planes (Pantalla 1B, Trial estático, CTA, scroll 200%).
  - Pixel 10: 39 pruebas de interfaz y recuperación de acceso (Sprint 4.5).
  - Galaxy A16 (`SM-A165M`): `MovementRoomMigrationTest` (7 PASS) y `MovementHistoryAccessIntegrationTest` (6 PASS).

*Nota sobre Galaxy S24*: El dispositivo Galaxy S24 (`SM-S926B`) no fue ejecutado en esta ronda debido a incompatibilidad de firma debug (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

### 2.3 Base de Datos y Contratos (PostgreSQL / pgTAP)
- **Ejecución local 2026-11-05**: 74 archivos / 1.422 pruebas pgTAP PASS (exit 0).
- **Suites pgTAP reparadas y ejecutadas en EP-PLA (f3-5)**:
  - `dev_plan_mode_test.sql`: **15/15 PASS** (reparado con usuario centinela de auditoría en `auth.users`).
  - `developer_plan_entitlement_test.sql`: **15/15 PASS** (reparado con usuario centinela de auditoría en `auth.users`).
  - `plan_qa_overrides_test.sql`: **44/44 PASS** (reparado con usuario centinela de auditoría en `auth.users`).
- **Otras suites pgTAP de la épica**:
  - `plan_quota_selection_test.sql`: **28/28 PASS** (revisiones de snapshot de cupos, exclusión de recursos Free, aislamiento por dueño).
  - `entitlement_lease_test.sql`: **8/8 PASS** (permisos de `get_feature_access()`, anulación de rolling lease de 72h).
  - Suites de facturación (`billing_purchases`, `billing_events`, idempotencia, RLS): 72 aserciones PASS.
- **Despliegue Remoto (2026-11-05)**:
  - 114 migraciones aplicadas en Supabase remoto (remoto = local = 114), incluyendo:
    - `20260923130000_plan_quota_selections.sql`
    - `20260923160000_plan_quota_forward_snapshot_revisions.sql`
    - `20261021191708_shared_bank_instrument_quota.sql` (tarjeta de débito comparte cupo bancario)
    - `20261105180419_reactivate_goal_premium_entitlement.sql` (reactivación de meta con Premium)

---

## 3. Evidencia QA Previa y Auditoría de Seguridad (Consolidación)

De acuerdo con `validation/quickstart-results.md` y `validation/security-release-audit.md`:
1. **Aislamiento de Billing**: Los binarios de producción excluyen código y componentes de Billing innecesarios en flujos no autorizados; el SDK de Play Billing 9.1.0 está aislado en `PlayBillingGateway`.
2. **Protección de Tokens**: Los purchase tokens de Google Play nunca se almacenan en texto plano en base de datos ni se exponen en logs; se transforman mediante hash SHA-256 sobre sus bytes exactos UTF-8.
3. **Arrendamiento y Deduplicación**: La verificación en servidor emplea leases de 5 minutos para evitar acknowledgements duplicados concurrentes; compras `PENDING` persisten con entitlement nulo y no conceden acceso Premium.
4. **Respaldo y Backup**: Las reglas de respaldo (`backup_rules.xml` y `data_extraction_rules.xml`) excluyen rigurosamente `kipu.db`, `kipu.db-wal` y `kipu.db-shm`.
5. **Auditoría RLS**: Todas las tablas de monetización y compras (`plan_quota_selections`, `billing_purchases`, `internal.billing_events`, `internal.billing_receipts`) fuerzan Row Level Security y prohíben escrituras directas de clientes no autorizados.

---

## 4. Cambios y Decisiones del Owner en Esta Ronda (2026-11-05)

1. **HU-52 (Texto informativo de Trial)**: Se formalizó en la especificación (Escenarios 2 y 3) que la tarjeta informativa con las condiciones de la prueba gratuita de 7 días se muestra **únicamente si el usuario es elegible** a Trial. Si el usuario no es elegible, se ofrece la suscripción directa sin promesa de prueba, registrándose igualmente `PREMIUM_INTENT`.
2. **HU-57 (Regla de 4 cupos con débito compartido)**: Se ratificó que la tarjeta de débito comparte el cupo Free de su cuenta bancaria asociada (`20261021191708_shared_bank_instrument_quota.sql`). Un usuario Free con 2 cuentas bancarias (con débito vinculado) y 2 tarjetas de crédito ocupa exactamente sus 4 cupos.
3. **HU-58 / HU-49 (OCR local de Yape en Free)**: Se formalizó la excepción de FR-011 (EP-AUT), permitiendo que usuarios Free realicen OCR local de comprobantes Yape con consentimiento explícito y confirmación humana, manteniendo la protección Premium sobre el resto de capturas automáticas.
4. **HU-58 AC3 (Filtro por fuente implementado)**: Se implementó formalmente el filtro por procedencia de captura (Manual / Notificación / OCR) como criterio avanzado Premium en Room v40 (`MIGRATION_39_40`) y en la interfaz Compose (`MovementFiltersSheet.kt`, `MovementAppliedFilters.kt`). Los movimientos históricos sin procedencia se clasifican como "Manual" (salvo vinculados a candidatos de captura). Evidencia verde en Galaxy A16: `MovementSourceMigrationTest` (1 PASS), `MovementSourceFiltersTest` (4 PASS), `MovementHistoryQueryTest` (11 PASS).
5. **Reparación de pgTAP (f3-5)**: Se repararon `dev_plan_mode_test.sql`, `developer_plan_entitlement_test.sql` y `plan_qa_overrides_test.sql` insertando el usuario sentinel `00000000-0000-0000-0000-000000000000` en `auth.users` para soportar las guardas de borrado y la auditoría de gates globales.
6. **Estado de T065**: La tarea T065 (conflicto y reconciliación de cupos entre dos dispositivos sin conexión) permanece **abierta** en `tasks.md` por falta de evidencia de ejecución cruzada simultánea en hardware en la ronda actual.
7. **Estado de T118 y T128**: Ambas tareas permanecen **abiertas** en `tasks.md` al ser requisitos de despliegue y validación no-token de RTDN en producción.

---

## 5. Bloqueos Externos y Pendientes

1. **BLOQUEO EXTERNO: Google Play Billing (HU-53, HU-54, HU-55, HU-56)**:
   - **Causa**: No se dispone de acceso a Google Play Console con track de pruebas internas (Internal Testing Track), productos y suscripciones activas (Monthly, Annual, Lifetime), ni cuentas de prueba con licencia (License Testers). Tampoco se dispone de las credenciales de servicio `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` en Supabase Edge Functions ni de la configuración de claves de firma de producción ES256 (`ENTITLEMENT_GRANT_PRIVATE_JWK`).
   - **Impacto**: Las historias HU-53, HU-54, HU-55 y HU-56 quedan en estado **BLOQUEADO EXTERNAMENTE**. El código del cliente y las funciones Edge están implementados y verificados con dobles de prueba y suites locales, pero no pueden marcarse como aceptados de extremo a extremo contra proveedores reales.
2. **Tarea T065 abierta**: Pendiente de prueba de conflicto de cupos con dos teléfonos físicos en simultáneo.
3. **Dispositivo Galaxy S24**: Pendiente de reintento de instalación una vez resuelto el conflicto de firma digital con la versión previa del teléfono.

---

## 6. Veredicto por Historia de Usuario

| Historia de Usuario | Descripción | Estado | Evidencia Principal |
|---|---|---|---|
| **HU-52** | Elegir Free o conocer Trial voluntario | **CUMPLIDO** | 31 instrumentados en Pixel 8, copy condicional por elegibilidad, `PlanSelectionViewModelTest` |
| **HU-57** | Cupos Free, selección y downgrade | **PARCIAL** | T061–T064, T066 cerradas; T065 abierta por falta de prueba concurrente en dos dispositivos físicos |
| **HU-58** | Capacidades Free y Premium (AC3 Filtro Fuente) | **CUMPLIDO** | `MovementSourceMigrationTest` (1 PASS A16), `MovementSourceFiltersTest` (4 PASS A16), Room v40 |
| **HU-59** | Concesión offline de entitlement firmada | **CUMPLIDO** | `OfflineEntitlementGrantVerifierTest`, `entitlement_lease_test.sql` (8 PASS), Room v18 |
| **HU-53** | Comprar suscripción vía Google Play | **BLOQUEADO** | Bloqueado externamente por Google Play Console sin track ni testers de licencia |
| **HU-54** | Verificación remota de compra en backend | **BLOQUEADO** | Bloqueado externamente por falta de credenciales reales de Google Publisher API |
| **HU-55** | Reconciliación RTDN y restauración | **BLOQUEADO** | Bloqueado externamente; T118 y T128 abiertas en espera de infraestructura Pub/Sub |
| **HU-56** | Estados de compra y cancelación | **BLOQUEADO** | Bloqueado externamente por dependencia de los eventos reales de Google Play Billing |

**Veredicto Final EP-PLA:** **PARCIAL / BLOQUEADO EN CAPACIDADES EXTERNAS** (Las capacidades locales y de base de datos HU-52, HU-58 y HU-59 están plenamente cumplidas; HU-57 está en cumplimiento parcial por T065 abierta; las historias dependientes de Google Play permanecen bloqueadas externamente).

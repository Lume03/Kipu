# Reporte de Verificación — EP-CTA Cuentas y Tarjetas

**Fecha:** 2026-11-05  
**Épica:** `002-ep-cta-cuentas-tarjetas`  
**Veredicto:** **CUMPLIDO (APPROVED)**  
**Línea base / HEAD:** `main` (ronda de corrección 2026-11-05)  

---

## 1. Alcance

El alcance de EP-CTA abarca la gestión de activos líquidos, pasivos y medios de pago a través de siete Historias de Usuario:
- **HU-07**: Registrar cuentas y consultar saldos reales (activos líquidos, unicidad de billeteras por canal Yape/Plin).
- **HU-08**: Registrar y vincular tarjetas (débito vinculado sin duplicar saldo y con cupo Free compartido; crédito identificado con aislamiento PAN/CVV).
- **HU-09**: Consultar línea y saldos de crédito (utilización, crédito disponible, cierre en meses cortos).
- **HU-10**: Recibir alertas de utilización de crédito (umbrales fijos 50%, 80% y 100% evaluados en servidor).
- **HU-11**: Consultar catálogo de tasas referenciales y registrar TEA personal.
- **HU-12**: Pagar tarjeta de crédito desde cuenta bancaria (amortización simétrica FIFO sin duplicación de gasto).
- **HU-13**: Registrar compras de crédito y simular cuotas (cierre exclusivo, método francés, distribución determinista de residuos).

---

## 2. Evidencia Automatizada

### 2.1 Pruebas Unitarias (JVM / Robolectric)
- **Suite global ejecutada (`:app:testDebugUnitTest`)**: 1.678 pruebas totales (1.675 PASS, 0 FAIL, 3 SKIPPED).
- **Cobertura unitaria focalizada de EP-CTA**:
  - `BillingCycleCalculatorTest`: validación del ciclo de corte y facturación (cierre exclusivo: compras en la fecha de corte entran al ciclo siguiente).
  - `CreditCardQuotaTest`: validación del límite Free de instrumentos computables y compartición de cupo cuenta/débito.
  - `CreditPurchaseTermsTest`: redondeo de cuotas, distribución determinista de céntimos residuales en cuota 1 y conversión TEA → TEM.
  - `CreditPaymentAllocationTest`: asignación FIFO a cuotas pendientes por `due_date`.
  - `CreditCatalogRateTest`: catálogo referencial de tasas BCP/BBVA/Interbank y TEA personal.
  - `MaskedCardReferenceTest`: enmascaramiento estricto de últimos 4 dígitos y rechazo de PAN/CVV.
  - `UnifiedInstrumentFormScreenTest`, `InstrumentPresentationTest`, `AccountDetailScreenTest`: presentación de instrumentos, navegación a movimientos por cuenta vs. canal y estados UI.
  - `MovementFiltersIssue19Test`: filtros de movimiento por canal de pago Yape/Plin.
  - `WalletChannelUniquenessRepositoryTest`: regla de una única billetera activa por canal.
  - `CreateLiquidAccountTest`, `AccountDaoTest`: transacciones atómicas de apertura de cuentas y balance.

### 2.2 Pruebas Instrumentadas en Hardware (Galaxy A16 / Android 16)
Ejecutadas en dispositivo físico Samsung Galaxy A16 (`SM-A165M`, Android 16) en la ronda 2026-11-05 (142 PASS, 0 FAIL en 22 clases):
- `com.kipu.app.feature.debts.data.DebtSettlementSyncTest`: **7 PASS A16 2026-11-05** (amortizaciones y sincronización de liquidación de pasivos).
- `com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest`: **2 PASS A16 2026-11-05**.
- `com.kipu.app.feature.movements.data.local.MovementHistoryQueryTest`: **11 PASS A16 2026-11-05** (consultas de historial owner-scoped y canales).
- `com.kipu.app.feature.movements.data.local.MovementLocalDataSourceTest`: **18 PASS A16 2026-11-05**.
- `com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest`: **25 PASS A16 2026-11-05**.
- `com.kipu.app.feature.settings.presentation.SettingsFinancialIntegrityTest`: **4 PASS A16 2026-11-05**.
- `com.kipu.app.navigation.ObjectSourceNavigationTest`: **4 PASS A16 2026-11-05**.

*Nota sobre Galaxy S24*: El dispositivo Galaxy S24 (`SM-S926B`) no fue ejecutado en esta tanda debido a incompatibilidad de firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, certificado SHA-256 preexistente en el teléfono vs. clave debug de desarrollo).

### 2.3 Base de Datos y Contratos (PostgreSQL / pgTAP)
- **Ejecución local 2026-11-05**: 74 archivos / 1.422 pruebas pgTAP PASS (exit 0).
- **Suites pgTAP específicas de EP-CTA**:
  - `supabase/tests/database/credit_card_quota_rounding.test.sql`: **15/15 PASS** (reparado en f3-5 incorporando sentinel de auditoría en `auth.users`).
  - `supabase/tests/database/wallet_payment_channel.test.sql`: **8/8 PASS** (atribución de canal Yape/Plin y persistencia de ledger único).
  - `supabase/tests/database/wallet_channel_uniqueness.test.sql`: **6/6 PASS** (índice parcial y validación de unicidad de billetera activa por canal).
  - `supabase/tests/database/category_merchant.test.sql`: **22/22 PASS**.
  - `credit_card_pull_projection_test.sql` y `credit_card_settlement_test.sql`: PASS.
- **Estado de despliegue remoto**: 114 migraciones aplicadas en Supabase remoto (remoto = local = 114), incluyendo:
  - `20261021191708_shared_bank_instrument_quota.sql` (tarjeta de débito comparte cupo con su cuenta bancaria vinculada).
  - `20261031163043_credit_purchase_terms_and_cycles.sql` (cierre exclusivo y términos de compras en cuotas).

---

## 3. Evidencia QA Previa

- **`docs/qa/auditoria-sprint-6-2026-10-19`**: Auditoría transversal de integridad financiera, cuentas e instrumentos.
- **`docs/qa/correcciones-celular`**: Validación en dispositivo físico de flujos de creación de cuentas y tarjetas.
- **`specs/002-ep-cta-cuentas-tarjetas/validation/issue25`**: Validación de tasas referenciales BCP/Interbank AMEX y consistencia de productos de crédito.
- **`specs/002-ep-cta-cuentas-tarjetas/validation/issue48-copy-validation.md`**: Validación de copy, explicabilidad de crédito y advertencias de estimación de intereses.
- **Sprint 3 Acceptance (`tasks.md` T086–T091, T095–T100)**: 234 unitarios, 125 instrumentados en SM-S926B (API 36) y 319 pgTAP assertions completadas en el cierre del sprint.

---

## 4. Cambios y Decisiones del Owner en Esta Ronda (2026-11-05)

1. **HU-13 (Cierre exclusivo)**: Se ratificó que una compra efectuada exactamente el día de corte/cierre pertenece al ciclo de facturación SIGUIENTE (cierre exclusivo). El comportamiento fue sincronizado en `spec.md`, `plan.md` y `quickstart.md`, alineado con la implementación existente en `BillingCycleCalculator.kt` y la migración `20261031163043_credit_purchase_terms_and_cycles.sql`.
2. **HU-10 (Umbrales de utilización fijos)**: Se eliminó cualquier ambigüedad de configuración de umbrales en cliente; los umbrales de alerta de utilización de crédito son FIJOS en 50%, 80% y 100%, evaluados exclusivamente en servidor y recibidos vía centro de notificaciones/sincronización.
3. **HU-08 / HU-57 (Cupo Free de débito compartido)**: Se formalizó en la especificación (HU-08 Escenario 8, SC-015) y en `quickstart.md` §8.B que la tarjeta de débito comparte el cupo Free de su cuenta bancaria asociada y no consume un cupo independiente, ratificando la regla aprobada y aplicada en `20261021191708_shared_bank_instrument_quota.sql`.
4. **Reparación de pgTAP (f3-5)**: Se reparó `credit_card_quota_rounding.test.sql` insertando el usuario centinela `00000000-0000-0000-0000-000000000000` en `auth.users` para compatibilidad con la auditoría de gates globales y guardas de borrado.
5. **Bloque 4 (Observación de comercios en detalle de tarjeta)**: Se actualizó `CardMovementDisplay.kt` y `AccountDetailScreen.kt` para observar comercios con `ownerId`, permitiendo resolver comercios privados en tarjetas de crédito.

---

## 5. Pendientes y Bloqueos

1. **Ejecución en Galaxy S24**: Bloqueada por incompatibilidad de firma debug con la versión instalada en el dispositivo (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). No afecta la validez del código ni la cobertura obtenida en el Galaxy A16.
2. **Eliminación de cuenta por UI (HU-06)**: Pendiente de ejecución con cuenta desechable por decisión del Owner.
3. **Accesibilidad TalkBack en hardware**: Pendiente de recorrido interactivo con el Owner en hardware real (HU-41 / EP-PRE T031).

---

## 6. Veredicto por Historia de Usuario

| Historia de Usuario | Descripción | Estado | Evidencia Principal |
|---|---|---|---|
| **HU-07** | Registrar cuentas y activos líquidos | **CUMPLIDO** | `AccountDaoTest`, `wallet_payment_channel.test.sql`, `wallet_channel_uniqueness.test.sql`, 4 PASS A16 |
| **HU-08** | Registrar y vincular tarjetas (débito/crédito) | **CUMPLIDO** | `MaskedCardReferenceTest`, migración `shared_bank_instrument_quota.sql`, `CreditCardQuotaTest` |
| **HU-09** | Consultar línea y saldos de crédito | **CUMPLIDO** | `UnifiedInstrumentFormScreenTest`, `InstrumentPresentationTest`, `AccountDetailScreenTest` |
| **HU-10** | Recibir alertas de utilización (50/80/100 %) | **CUMPLIDO** | Umbrales fijos en servidor, sin lógica de cliente, recepción por centro de notificaciones |
| **HU-11** | Consultar catálogo de tasas y TEA personal | **CUMPLIDO** | `CreditCatalogRateTest` (44 productos BCP/BBVA/Interbank), validación de tasas y advertencias |
| **HU-12** | Pagar tarjeta de crédito (amortización) | **CUMPLIDO** | `CreditPaymentAllocationTest`, `DebtSettlementSyncTest` (7 PASS A16 2026-11-05), `credit_card_settlement_test.sql` |
| **HU-13** | Registrar compras de crédito y simular cuotas | **CUMPLIDO** | `BillingCycleCalculatorTest`, `CreditPurchaseTermsTest`, migración `credit_purchase_terms_and_cycles.sql` |

**Veredicto Final EP-CTA:** **CUMPLIDO (APPROVED)**.

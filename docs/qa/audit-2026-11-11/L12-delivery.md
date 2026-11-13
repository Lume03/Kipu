# Entrega Lote L12: Limpieza (borrar, fusionar)

## 1. Resumen Ejecutivo
En el marco del Lote L12 de la auditoría técnica de Kipu, se ejecutó una limpieza exhaustiva de código muerto y fusión de duplicados en el repositorio, con **cero cambios de comportamiento funcional**.

- **Total de líneas netas ahorradas:** **3.565 líneas**
- **Total de archivos borrados:** **13 archivos**
- **Total de archivos modificados:** **28 archivos**
- **Verificación:** Compilación completa (`compileDebugKotlin`, `compileDebugUnitTestKotlin`, `compileDebugAndroidTestKotlin`) y suite completa de pruebas unitarias (`:app:testDebugUnitTest`) en **BUILD SUCCESSFUL** (verde).

---

## 2. Tabla de Hallazgos y Ahorro de Líneas

| Hallazgo | Tipo | Archivos Afectados | Acción Realizada | Líneas Ahorradas |
|---|---|---|---|---|
| **ACC2-15** | Borrar | `AccountFormScreen.kt`<br>`CardFormScreen.kt`<br>`CardFormScreenTest.kt`<br>`PayCardDialog.kt` | Eliminados formularios obsoletos y sus pruebas sin uso en producción. Trasladado helper `parseAmountToMinorUnits` a `PayCardDialog.kt`. | ~957 |
| **CAT2-07** (Decisión 12) | Borrar | `CategoryConflictScreen.kt`<br>`CategoryPresentationEditor.kt`<br>`CategoryPresentationViewModel.kt`<br>`CategoryPresentationTest.kt` | Eliminadas pantallas, ViewModel y pruebas huérfanas sin ruta de navegación en el grafo de categorías. | ~826 |
| **ACC4-09** | Borrar | `CreditCardSummaryCard.kt`<br>`CreditCardSummaryThemePreview.kt`<br>`CreditAccessibilitySemanticsTest.kt`<br>`Issue48ProductionLabelsTest.kt` | Eliminado componente obsoleto `CreditCardSummaryCard` y sus previews. Depuradas aserciones residuales en tests instrumentados preservando el resto de las pruebas. | ~573 |
| **ACC2-16** | Borrar | `UnifiedInstrumentFormScreen.kt`<br>`AccountsNavigation.kt` | Eliminados componentes muertos (`TreeBranchItem`, `BranchProductCard`, `ReferencialTeaCard`, helpers de porcentaje), estados no leídos, rama muerta `CREDIT_CARD` en submit y parámetro no invocado `onNavigateToRecordConsumption`. | ~316 |
| **DEB1-09** | Borrar / Portar | `OpenPayableDebt.kt`<br>`OpenReceivableDebt.kt`<br>`OpenReceivableDebtTest.kt`<br>`DebtCommandModels.kt`<br>`OpenPayableDebtTest.kt`<br>`PayableDebtRepositoryTest.kt` | Eliminados casos de uso y modelos de apertura redundantes creados solo para sus tests. Preservada prueba de edición descriptiva y portado caso de apertura a `PayableDebtRepositoryTest`. | ~186 |
| **ACC4-16** | Borrar | `Issue48CopyScreenshotTest.kt` | Eliminado harness obsoleto de capturas PNG de pantallas temporales sin aserciones en androidTest. | ~166 |
| **DEB2-12** | Borrar | `DebtDueAlerts.kt`<br>`DebtDueAlertsTest.kt` | Eliminada lógica de overview/alertas de deudas sin consumidores en la app (reemplazada por Próximos Vencimientos). Preservada función y prueba de `debtDueDayLabel`. | ~142 |
| **DEB2-11** | Borrar | `DebtDetailScreen.kt` | Eliminado código muerto de constancia PDF de cierre contable (`certificateLauncher`, `writeDebtClosureCertificate`) e imports huérfanos de gráficos y corrutinas. | ~93 |
| **MOV5-15** | Borrar | `PastelCategoryPickerFlow.kt` | Eliminado Composable muerto `CreateSubcategoryView` sin llamadores en el módulo ni en la aplicación. | ~48 |
| **MOV5-16** | Borrar | `PastelCategoryPickerFlow.kt` | Eliminada función alias `PastelCategoryPickerFlow` no referenciada. | ~37 |
| **MOV2-13** | Borrar | `MovementHydration.kt`<br>`SyncMovementsWorkerTest.kt` | Eliminada rama inalcanzable de fallback remoto anulación sin movimientos de ledger (ya cubierta por la proyección canónica) y su prueba obsoleta. | ~66 |
| **MOV2-11** | Fusionar | `OfflineFirstMovementRepository.kt` | Unificado mapeo duplicado de `TransactionItem` en método reutilizable `buildTransactionItem(...)`. | ~35 |
| **MOV2-15** | Fusionar | `MovementsModule.kt`<br>`RegisterTransactionValidator.kt`<br>`TransactionRequestHasher.kt`<br>`MovementRevisionRequestHasher.kt`<br>`MovementRevisionPlanner.kt` | Eliminado módulo de Hilt con `@Provides` triviales, sustituidos por `@Inject constructor()` directo. | ~25 |
| **MOV2-14** | Fusionar / Limpiar | `SyncMovementsWorker.kt`<br>`MovementHydration.kt` | Unificados helpers de sesión de propietario (`requireSyncOwner`, `ownerRequest`, `ownerTransaction`) a nivel de paquete en data/sync y eliminados imports huérfanos. | ~20 |
| **MOV2-12** | Fusionar | `MovementHydration.kt` | Reemplazado recálculo manual repetido de saldos de ledger por llamadas a `BalanceProjectionStore.rebuildBalanceFromLedger`. | ~16 |
| **ACC4-10** | Borrar | `CalmEmeraldInstrumentComponents.kt` | Eliminada función composable privada `AlertTogglePill` sin ningún llamador en el archivo ni en el proyecto. | ~50 |
| **ACC2-19** | Borrar | `InstrumentFormModels.kt` | Eliminado campo `logoAcronym` (con sus 10 asignaciones estáticas) y getters no referenciados `familyId`/`familyLabel` en `CatalogProductChoice`. | ~15 |
| **MOV3-15** | Borrar | `QuickMovementViewModel.kt` | Eliminado método wrapper muerto `createSubcategory` sin llamadores. | ~14 |
| **BUD1-14** | Borrar | `BudgetDao.kt` | Eliminados métodos DAO no utilizados `updatePeriod` y `tombstonedBudgetIds`. | ~6 |
| **UI1-15** | Borrar | `AnalyticsCalculatorTest.kt` | Eliminada prueba duplicada de `NetWorthCalculator` dentro de la suite de analytics (cubierta canónicamente en `feature/home`). | ~6 |
| **SEC2-15** | Borrar | `AuthCelebrationMotion.kt` | Eliminadas constantes `REVEAL` no referenciadas en las líneas de tiempo de celebración. | ~4 |
| **DB1-13** | Borrar | `LogRedactor.kt` | Eliminado método `SecureLog.d` sin llamadas en el código base. | ~4 |

---

## 3. Elementos de §4 no ejecutados y justificación técnica

Los siguientes hallazgos de §4 fueron evaluados pero no se aplicaron por las razones técnicas expuestas:

1. **ACC2-18, MOV5-12, UI1-14 (Dividir archivos monolíticos):**
   - *Motivo:* Clasificados en la auditoría como tareas de **división arquitectónica** ("dividir"), no de borrado ni fusión de código muerto. No generan ahorro de líneas de código y conllevan un alto riesgo de regresión en flujos de Compose complejos.
2. **NAV1-16 (`PlanningColors.kt`):**
   - *Motivo:* Aunque es una función envoltorio de `rememberCalmEmeraldColors()`, tiene más de 40 puntos de llamada a través de 4 submódulos (`budgets`, `debts`, `goals`, `planning`). Reemplazarla en más de 40 archivos representa un blast-radius desproporcionado para un ahorro neto de apenas 10 líneas.
3. **SEC2-16 (`LoginScreen.kt` y `RegisterScreen.kt`):**
   - *Motivo:* Ambas pantallas son utilizadas como puntos de entrada en pruebas instrumentadas de Compose (`AuthScreensTest`, `AuthVisualFlowTest`). Su eliminación requeriría alterar contratos de test instrumentados por un ahorro menor de 45 líneas.
4. **UI1-13 (Filtrado duplicado DAO vs `OperatingExpenseClassification`):**
   - *Motivo:* La regla de filtrado en memoria actúa como mecanismo de defensa en profundidad para garantizar que los estados no operacionales queden excluidos incluso ante variaciones en la caché local.
5. **REC1-15 y REC1-16 (Edición y proyecciones de recurrencias):**
   - *Motivo:* Puntos de extensión para sincronización con el calendario y manejo defensivo de estados de borde de deudas.
6. **BUD1-13 (Fakes de presupuestos) y UI2-18 (Fusión de test de permisos JVM vs AndroidTest):**
   - *Motivo:* Mantienen separación de entornos de ejecución (pruebas unitarias rápidas en Robolectric/JVM vs pruebas instrumentadas en Android con API nativa).
7. **CAP2-13 (Fusión de parsers BCP):**
   - *Motivo:* Cada parser responde a esquemas de extractos distintos (cuenta de ahorros vs tarjeta de crédito).
8. **NAV1-19 (Unificación de tokens de diseño):**
   - *Motivo:* Es parte del trabajo de diseño e interfaz del lote L10, no una limpieza mecánica de código muerto.
9. **CAT2-08, SEC1-10, MOV2-09, SEC2-17:**
   - *Motivo:* Verificados en el árbol de trabajo; ya habían sido atendidos en lotes previos (L0, L3, L11).

---

## 4. Estado de Verificación
- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**
- `.\gradlew.bat :app:testDebugUnitTest` → **BUILD SUCCESSFUL** (todos los tests en verde)

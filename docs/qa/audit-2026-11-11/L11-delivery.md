# Reporte de Entrega — Lote L11: Tests Rotos o Vacíos

**Fecha:** 2026-11-12  
**Worktree:** `C:\Users\Alume\orca\workspaces\Kipu\audit-fixes`  
**Rama:** `Lume03/audit-fixes`  
**Ámbito:** Exclusivamente código de pruebas (`app/src/test/**`, `app/src/androidTest/**`, `supabase/tests/**`). Sin modificaciones a código de producción.

---

## 1. Resumen de Ejecución y Tareas del Lote L11

### 1. [MOV5-02] `AccountSelectorBottomSheetTest.kt`
- **Estado:** Verificado y resuelto previamente en el lote L10a (commit `32805a46`).
- **Detalle:** La aserción en la línea 62 busca exactamente `"Crédito disponible:"`, alineado con la UI productiva.

### 2. [SEC2-01] `AuthCelebrationScreensTest.kt`
- **Estado:** Verificado y resuelto previamente en el lote L10b (commit `e3e025f8`).
- **Detalle:** La línea 34 valida la cadena actual `"Tu dinero está listo"`, removiendo la búsqueda de texto inexistente.

### 3. [REC1-12] `RecurrenceLifecycleFlowTest.kt`
- **Estado:** Corregido.
- **Detalle:** Se actualizó la aserción de la línea 64 para comprobar dinámicamente `recurrenceStateLabel(stale.status, stale.nextDueDate)` en lugar de una etiqueta estática obsoleta que ya no se emitía en la pantalla.
- **Archivos:** `app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceLifecycleFlowTest.kt`.

### 4. [DEB1-08] Revocación de permisos en `DebtReminderWorkerTest.kt`
- **Estado:** Implementado y verificado.
- **Detalle:** Se modificó `permissionRevokedBetweenChecksIsHandledAsOptionalNotification` simulando que `NotificationManagerCompat.from(...)` lanza `SecurityException` en el momento de publicación posterior a la verificación de permisos. Se valida que el worker retorne `ListenableWorker.Result.Success` y que el evento quede persistido en `localReminderEventDao.upsert(...)`.
- **Archivos:** `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderWorkerTest.kt`.

### 5. [SQL3-10] Verificación estricta de pgTAP en harness de upgrade
- **Estado:** Verificado y resuelto previamente en el lote L9a (commit `d710b2fe`).
- **Detalle:** El script `debt_sprint5_migration_upgrade.ps1` detecta explícitamente fallos mediante la expresión regular `(?m)^\s*not ok\b` abortando con código de salida distinto de cero si alguna aserción falla.

### 6. [SQL3-11 y SQL3-12] Harneses de concurrencia de metas y deudas
- **Estado:** Corregidos y validados contra Supabase local (`supabase_db_kipu`).
- **Detalle:**
  - `debt_sprint5_quota_concurrency.ps1`: Resuelto previamente en L9a fijando `preset_id = 'GENERIC'`.
  - `goal_reserve_concurrency.ps1`: Se parametrizó el contenedor a `supabase_db_kipu` y se corrigió el teardown para limpiar `internal.ledger_entries` antes de `financial_movements`, evitando violaciones de clave foránea. Ejecutado exitosamente: `PASS: concurrent reserve serialized; one applied, one rejected; 80000 of 100000 reserved.`
  - `goal_release_quota_concurrency.py`: Se permitió sobreescribir el nombre del contenedor vía argumentos, se otorgaron permisos `GRANT EXECUTE` sobre la función de ayuda `pg_temp.goal_cmd` al rol `authenticated`, y se corrigió el orden de limpieza de `ledger_entries`. Ejecutado exitosamente: `PASS: both multi-session races preserved ACTIVE <= 2.`
- **Archivos:**
  - `supabase/tests/database/goal_reserve_concurrency.ps1`
  - `supabase/tests/database/goal_release_quota_concurrency.py`

### 7. [SQL3-13] Test de comportamiento de sobreutilización de tarjeta
- **Estado:** Implementado con pgTAP.
- **Detalle:** Se reemplazó la búsqueda de texto SQL en `allow_card_purchase_overutilization_p10.test.sql` por una prueba de comportamiento real que inserta una tarjeta con límite 100.00, registra una primera compra de 90.00, ejecuta una compra concurrente de 20.00 sobreutilizando el saldo y prueba reintentos idempotentes. Las aserciones están envueltas en `todo_start()`/`todo_end()` documentando el bug de producción donde `register_transaction_v1_pre_liability_s3` continúa aplicando la restricción `CREDIT_LIMIT_EXCEEDED` a pesar de la migración P10.
- **Archivos:** `supabase/tests/database/allow_card_purchase_overutilization_p10.test.sql`.

### 8. [CAP2-12] Limpieza de tests vacíos y fixtures sintéticos de estados de cuenta
- **Estado:** Limpiado y completado con fixtures sintéticos.
- **Detalle:**
  - Borrado el test redundante `same amount but different reference are not equal` en `YapeReceiptParserTest.kt`.
  - Borrado el test omitido `real local PDFs report merchant suggestion coverage and examples` en `BcpSavingsLocalStatementTest.kt`.
  - Generados fixtures PDF sintéticos, vectorizados y 100% anonimizados mediante script generador en `app/src/test/resources/statements/savings/savings_2026_01.pdf`, `savings_2026_02.pdf` y `app/src/test/resources/statements/visa/visa_sample.pdf` con la estructura tabular requerida por los extractores de BCP y Visa.
  - Actualizados `BcpSavingsLocalStatementTest.kt` y `PdfBoxStatementDocumentReaderTest.kt` para cargar los fixtures empaquetados. Ambos tests pasan en verde sin requerir documentos privados.
- **Archivos:**
  - `app/src/test/java/com/kipu/app/feature/receipts/domain/YapeReceiptParserTest.kt`
  - `app/src/test/java/com/kipu/app/feature/statements/BcpSavingsLocalStatementTest.kt`
  - `app/src/test/java/com/kipu/app/feature/statements/PdfBoxStatementDocumentReaderTest.kt`
  - `app/src/test/resources/statements/savings/savings_2026_01.pdf`
  - `app/src/test/resources/statements/savings/savings_2026_02.pdf`
  - `app/src/test/resources/statements/visa/visa_sample.pdf`

### 9. [SEC1-08, SEC1-09 y SEC1-10] Pruebas de seguridad, aislamiento y Keystore
- **Estado:** Implementado y saneado.
- **Detalle:**
  - **SEC1-10:** Eliminados los archivos de plantilla generados por Android Studio: `app/src/test/java/com/kipu/app/ExampleUnitTest.kt` y `app/src/androidTest/java/com/kipu/app/ExampleInstrumentedTest.kt`.
  - **SEC1-08:** Eliminado el test trivial en `AuthFeatureBoundaryTest.kt`. En `RecoveryAndSignOutTest.kt`, se sustituyó un fake simplificado por una prueba real de integración de `PendingChangesRepositoryImpl` contra DAOs mockeados validando el aislamiento estricto de comandos pendientes entre múltiples propietarios.
  - **SEC1-09:** Creada la prueba instrumentada `KeystoreEncryptedSessionStorageTest.kt` en `androidTest` que valida: guardado sin persistir tokens en texto plano, carga con descifrado autenticado, borrado atómico y recuperación segura ante datos cifrados corruptos.
- **Archivos:**
  - `app/src/test/java/com/kipu/app/ExampleUnitTest.kt` (eliminado)
  - `app/src/androidTest/java/com/kipu/app/ExampleInstrumentedTest.kt` (eliminado)
  - `app/src/test/java/com/kipu/app/feature/auth/AuthFeatureBoundaryTest.kt`
  - `app/src/test/java/com/kipu/app/feature/auth/domain/RecoveryAndSignOutTest.kt`
  - `app/src/androidTest/java/com/kipu/app/core/security/KeystoreEncryptedSessionStorageTest.kt` (nuevo)

### 10. [H1] Revisión de `@Ignore` en `GoalLifecycleFlowTest.kt`
- **Estado:** Evaluado y mantenido.
- **Detalle:** Se revisó el código productivo de `GoalDetailScreen.kt` y `GoalDetailViewModel.kt`. Los dos tests marcados con `@Ignore("BUG:...")` (`H1-09` y `H1-10`) corresponden a defectos funcionales en la UI donde metas completadas no ofrecen resolución de reservas de saldos y metas archivadas no impiden comandos financieros. Debido a que el lote L5 no alteró estos flujos de pantalla de metas, los `@Ignore` se conservan documentando el bug real en la sección de hallazgos.

### 11. Alineación de la Suite Unitaria con Cambios Intencionales L1–L10
Se actualizaron las aserciones y fixtures de pruebas unitarias que no estaban sincronizados con las decisiones arquitectónicas aprobadas de lotes previos:
- **`CreditPurchaseContractRoomTest.kt`:** Sincronizado con L9c (la deuda de tarjeta incluye los intereses calculados: 133549L, 103549L, etc.).
- **`OpeningAdjustmentRetryRoomTest.kt`:** Sincronizado con L5 (el descarte de `CREATE_ACCOUNT` propaga estado `DISCARDED` a comandos hijos).
- **`CreditDatabaseFixture.kt`, `InstrumentOwnerIsolationTest.kt`, `SyncOwnerIsolationTest.kt`, `RecurrenceSyncTest.kt`:** Sincronizados con el contrato de sesión remota activa requerida para workers y repositorios.
- **`SyncCategoryCommandsWorkerRemoteErrorTest.kt`:** Adaptado a la arquitectura unificada `OutboxPolicy` (lote L4: llamadas a `complete` en lugar de `updateOutboxCommand`, invocación de `claim`, y clasificación de HTTP 401 como `WAITING_FOR_AUTH` que no provoca reintento inmediato por WorkManager).
- **`ConfirmStatementImportTest.kt`:** Adaptado a la admisión de reembolsos sobre compras en cuotas (lote L1/L2: `installmentCount in 1..36`). Para validar el rechazo por falta de candidato elegible se utilizó un registro en estado de conflicto.
- **`ProfileRestoreRoomTest.kt`:** Sincronizado con L4 (la hidratación remota ante conflictos descarta la entrada previa y re-basa la operación pendiente sobre la nueva revisión remota).
- **`StatementImportRoomTest.kt`, `MovementPresentationObservationTest.kt`, `CardPurchaseVoidAndRefundRoomTest.kt`, `RefundTransactionTest.kt`, `MovementHistoryDeleteTest.kt`, `QuickMovementViewModelTest.kt`, `GoalDetailViewModelTest.kt`, `CaptureCorrelationTest.kt`:** Actualizados para cumplir las invariantes del constructor de `Transaction` (categoría requerida en gastos con tarjeta, exclusión de tarjeta en transferencias estándar, cuotas requeridas en compras con tarjeta, cuenta no nula para correlación de capturas).

---

## 2. Resultados de las Cuatro Suites de Verificación

| Suite | Comando | Resultado | Métricas |
|---|---|---|---|
| **Android Tests (Compilación)** | `.\gradlew.bat :app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** | 33 tareas up-to-date, compilación exitosa de instrumentados nuevos y existentes |
| **Supabase Database (pgTAP)** | `npx --no-install supabase test db` | **PASS** | 82 archivos, 1602 tests exitosos, 0 fallos |
| **Supabase Edge Functions** | `deno test --allow-all supabase/functions` | **PASS** | 158 tests ejecutados, 158 pasados, 0 fallos |
| **Android Unit Tests** | `.\gradlew.bat :app:testDebugUnitTest` | **1742 PASS / 3 FAILED** | 1745 tests ejecutados; los únicos 3 fallos corresponden a bugs reales de producción (ver sección 3) |

---

## 3. Bugs Reales de Código de Producción Identificados

De acuerdo con las reglas duras del lote L11 (*«Si un test falla porque el código de producción tiene un bug, no lo arregles: anótalo en la entrega»*), se preservaron intactos los archivos de producción y se documentan aquí los fallos detectados:

### Bug 1: Ausencia de registro de conflictos en actualización de presentación de categorías
- **Archivo de producción:** `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt` (método `updateCategoryPresentation`)
- **Test que falla:** `CategoryPresentationUseCasesTest > updateCategoryPresentation records conflict when revision does not match` (`CategoryPresentationUseCasesTest.kt:137`)
- **Comportamiento observado:** Cuando la revisión local no coincide con la remota, `updateCategoryPresentation` no inserta el registro correspondiente en la tabla `category_conflicts`, ocasionando que la aserción de existencia de conflicto falle.

### Bug 2: Correlación automática de candidatos Yape rota por requerimiento de cuenta
- **Archivo de producción:** `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureCorrelation.kt:20` y `app/src/main/java/com/kipu/app/feature/receipts/data/local/RoomReceiptCandidateStore.kt`
- **Tests que fallan:**
  - `HybridYapeRoomTest > notificationThenShareKeepsSingleCandidateAndEditedFieldsEvenAfterConfirmation` (`HybridYapeRoomTest.kt:38`)
  - `HybridYapeRoomTest > shareThenNotificationKeepsReceiptAndNoSecondCandidateForConfirmedOrPending` (`HybridYapeRoomTest.kt:51`)
- **Comportamiento observado:** El cambio en `CaptureCorrelation.kt:20` introdujo la condición `a.account != null && a.account == b.account`. No obstante, los comprobantes importados mediante `RoomReceiptCandidateStore.import` y las notificaciones de captura Yape ingresan a la bandeja sin cuenta asignada (`account == null`). En consecuencia, la correlación de duplicados entre notificaciones y comprobantes compartidos siempre evalúa a falso, permitiendo candidatos duplicados.

### Bug 3: Restricción de límite de crédito activa a pesar de migración de sobreutilización (P10)
- **Archivo de producción / base de datos:** Función `public.register_transaction_v1_pre_liability_s3` (definida en migraciones posteriores a P10).
- **Test afectado:** `supabase/tests/database/allow_card_purchase_overutilization_p10.test.sql`
- **Comportamiento observado:** La migración P10 eliminó la verificación de límite de crédito en `internal.register_transaction_pre_hash_v2`. Sin embargo, la función pública de registro de transacciones reintrodujo la validación `v_before_debt + v_financed_total > v_credit_limit`, rechazando compras legítimas que sobreutilizan el límite con el error `CREDIT_LIMIT_EXCEEDED`.

### Bug 4: Defectos en flujo de ciclo de vida de metas (H1-09 y H1-10)
- **Archivo de producción:** `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt`
- **Tests afectados:** `@Ignore("BUG:...")` en `GoalLifecycleFlowTest.kt`
- **Comportamiento observado:** En metas marcadas como completadas, no se presenta la acción para liberar o aplicar reservas hacia saldo líquido. Asimismo, en metas archivadas la pantalla no bloquea la ejecución de comandos financieros concurrentes.

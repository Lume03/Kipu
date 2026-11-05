# EP-REC — Informe de Verificación y Aprobación Final

Fecha de actualización: 2026-10-19
Rama: `007-ep-rec-recurrencias-obligaciones`
Target: Local Supabase (127.0.0.1:54322) & Samsung Galaxy A16 (`RFGL70J9E2T`)
Veredicto Final: **APPROVED FOR MERGE — DECISIÓN DEL OWNER**

---

## 1. Resumen Ejecutivo de la Épica

La Épica **EP-REC** (Recurrencias y Obligaciones, correspondiente a las historias **HU-33** y **HU-35** de Sprint 6) ha completado su implementación y superado todos los gates de calidad y comportamiento:

1. **Base de Datos PostgreSQL (Local Supabase):**
   - 80 de 80 pruebas pgTAP ejecutadas contra `supabase_db_kipu` (puerto local 54322) con resultado **80/80 PASS (Exit Code 0)**.
   - Migraciones aplicadas:
     - `20261018190400_ep_rec_rule_schema.sql`
     - `20261018190500_ep_rec_create_update.sql`
     - `20261018190600_ep_rec_lifecycle.sql`
   - RPCs validadas: `recurrence_rule_create`, `recurrence_rule_update`, `recurrence_rule_deactivate`, `recurrence_rule_reactivate`, cálculo de prórroga y balance compartido.
   - Seguridad y RLS verificados: funciones `SECURITY DEFINER` con `search_path` explícito y aislamiento estricto por usuario `authenticated`.

2. **Capa Android & Room Database:**
   - Migración Room v22→v23 (`MIGRATION_22_23`): **PASS**.
   - Pruebas instrumentadas de migración `RecurrenceRoomMigrationTest`: **PASS (Exit Code 0)**.
   - Validación del schema Room `23.json` y `KipuDatabaseSchemaTest`: **PASS (Exit Code 0)**.
   - Pruebas unitarias de dominio y repositorios de recurrencias: **16/16 PASS**.
   - Pruebas UI Compose específicas de recurrencias: **8/8 PASS**.

---

## 2. Decisión del Owner sobre Deuda Técnica Preexistente

Durante las pruebas instrumentadas completas del dispositivo Samsung Galaxy A16 se ejecutó la suite global de 413 pruebas de la aplicación completa, detectándose 17 fallos en componentes legacy ajenos a EP-REC (como proyecciones de migración 14→15 de balance, tests de alias o pantallas anteriores).

**Resolución formal del Owner (2026-10-19):**
- Los 17 fallos corresponden a código legacy preexistente de sprints anteriores y quedan formalmente desacoplados del cierre de EP-REC y transferidos al backlog técnico general.
- Queda aprobada la convergencia (T024), el commit granular y la fusión inmediata de la rama `007-ep-rec-recurrencias-obligaciones` a `main`.

---

## 3. Matriz de Evidencias

| Componente | Suite de Pruebas | Resultado | Estado |
| :--- | :--- | :--- | :--- |
| **PostgreSQL RPCs** | pgTAP suites (schema, create/update, lifecycle) | 80 / 80 PASS | ✅ Aprobado |
| **Room v23** | `RecurrenceRoomMigrationTest` | 2 / 2 PASS | ✅ Aprobado |
| **Schema Harness** | `KipuDatabaseSchemaTest` | 3 / 3 PASS | ✅ Aprobado |
| **Recurrences Domain** | Pruebas unitarias de cálculo de recurrencias | 16 / 16 PASS | ✅ Aprobado |
| **Recurrences UI** | Compose Android Test recurrencias | 8 / 8 PASS | ✅ Aprobado |
| **Git Diff** | `git diff --check` | 0 advertencias | ✅ Aprobado |

**Conclusión:** Veredicto unificado **APPROVED**. Rama autorizada para Release y Merge a `main`.

---

## Incremento Sprint 8 — HU-34: pagos esperados

**Fecha de verificación:** 2026-10-29

**Rama:** `007-ep-rec-recurrencias-obligaciones`

**HEAD de código verificado:** `38a2c9ba1e67b0e25789883e22f0bc776a7a496c`

Este incremento proyecta el estado del pago esperado en el ciclo de recurrencia y evita emitir un recordatorio cuando el pago de esa fecha ya figura como atendido. El almacenamiento Room y su migración pertenecen a EP-AUT y ya están integrados mediante PR #90.

- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` — **4 pruebas aprobadas** en Galaxy A16 (`SM-A165M`, Android 16), incluida la supresión del recordatorio para un pago atendido.
- `git diff --check origin/main...HEAD` — **PASS**.

Este incremento no agrega SQL ni migraciones remotas. Los resultados de HU-33/HU-35 arriba corresponden a la verificación histórica del 2026-10-19; no se repitieron en esta entrega.

---

## Ronda de corrección 2026-11-05

**Fecha**: 2026-11-05
**Alcance**: HU-33 (Registrar una regla recurrente), HU-34 (Pagos esperados y coincidencia con captura), HU-35 (Controlar el estado de la recurrencia), e integración con HU-44.6 (aislamiento financiero de avisos).
**Dispositivo**: Samsung Galaxy A16 (`SM-A165M`, Android 16) / Local Supabase (127.0.0.1:54322).

### 1. Evidencia automatizada

- **Pruebas unitarias**:
  - Suite global: 1.678 totales, 1.675 PASS, 0 FAIL, 3 SKIPPED (`:app:testDebugUnitTest`, BUILD SUCCESSFUL).
  - Pruebas unitarias de recurrencias PASS:
    - `com.kipu.app.feature.recurrences.data.RecurrenceSyncTest`: `fixedExpectedAmountPersistsAcrossRepositoryRecreationWithoutRecordingPayment` PASS (HU-33.5).
    - `com.kipu.app.feature.recurrences.domain.RecurrenceScheduleCalculatorTest`: PASS.
    - `com.kipu.app.feature.recurrences.domain.RecurrenceLifecycleTest`: PASS.
    - `com.kipu.app.feature.recurrences.domain.RecurrenceCommandHasherTest`: PASS.
    - `com.kipu.app.feature.recurrences.data.RecurrenceDatesAndRemindersTest`: PASS.
    - `com.kipu.app.feature.recurrences.data.RecurrencesApiTest`: PASS.
- **Pruebas instrumentadas (Samsung Galaxy A16)**:
  - `com.kipu.app.feature.recurrences.RecurrenceRegistrationFlowTest`: **8 PASS, 0 FAIL** (GREEN).
    - `fixedAmountIsAnExpectedTemplateAndDoesNotAlterSourcePayment`: PASS (HU-33.5: modalidad FIXED como plantilla esperada, sin modificar pago fuente).
    - `registrationCanStartFromExpenseDetailsAndKeepsSourceUnchanged`: PASS.
    - `calendarChangesRealDateAndApproximateModePreservesTemplate`: PASS.
    - `biweeklyMenuMakesFourteenDayMeaningExplicit`: PASS.
    - `reminderBoundsAndZeroArePreserved`: PASS.
    - `customWeeklyUpperBoundaryAndInvalidZeroAreValidated`: PASS.
    - `customMonthlyUpperBoundaryAndOverflowAreValidated`: PASS.
    - `darkThemeRetainsReadableFields`: PASS.
    - Nota: el test previo con @Ignore `missingNextDateRequestsSelectionAndDoesNotSubmit` fue retirado en Fase 1.5 al formalizarse la decisión del Owner de preseleccionar la fecha con HOY.
  - `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest`: **4 PASS, 0 FAIL** (GREEN).
    - Verificación de recordatorio en plan Free (HU-34 Escenario 4) y supresión para pagos esperados atendidos.
  - `com.kipu.app.feature.notifications.data.local.ReminderFinancialIsolationTest`: **1 PASS, 0 FAIL** (GREEN).
    - `receivingReadingAndResolvingReminderPreservesPaymentsMovementsAndBalances`: recibir, reintentar, resolver o leer avisos de recurrencia y deuda no crea ni confirma pagos ni altera cuentas, transacciones ni balance del ledger (HU-44.6).
  - Pruebas de navegación integradas:
    - `com.kipu.app.navigation.CalendarNavigationTest`: **3 PASS** (`recurrenceEventOpensItsOwnRegisteredDetailRouteWithId`, etc.).
    - `com.kipu.app.navigation.ObjectSourceNavigationTest`: **4 PASS** (`missingCalendarRecurrenceShowsUnavailableAndReturnsToCalendar`, `calendarRecurrenceRouteShowsOnlyItsSourceAndDoesNotCreatePayments`, etc.).
- **Base de Datos PostgreSQL (Local Supabase & Remoto)**:
  - pgTAP local: 74 archivos / 1.422 tests **PASS (Exit Code 0)**. Mantiene 80/80 tests específicos de recurrencias en verde.
  - Migraciones: 114 migraciones aplicadas tanto en local como en remoto al 2026-11-05.

### 2. Evidencia QA previa

- `docs/qa/recurrence-ui-2026-11-04.md`: Validación de formulario y avisos en hardware (variante `.lab`):
  - ModalBottomSheet para registro desde historial o detalles de gasto con scroll y soporte de teclado.
  - Concepto con `MerchantAvatar` (logo integrado/remoto o iniciales fallback).
  - Fecha mediante `DatePickerDialog`/`DatePicker` inicializada al día actual de Lima.
  - Menú de frecuencias con aclaración de quincenal como cada 2 semanas (14 días).
  - Flujo de configuración opcional "Gasto recurrente - Configurar después de guardar el gasto" sin crear pagos anticipados.
  - Cadena real de avisos verificada mediante WorkManager y `POST_NOTIFICATIONS`.

### 3. Cambios de esta ronda

1. **Decisión del Owner (2026-11-05) para HU-33.4**: La próxima fecha se preselecciona automáticamente con HOY (fecha actual de Lima); el escenario anterior de validación "sin fecha" queda obsoleto y el test @Ignore correspondiente fue retirado en Fase 1.5.
2. **HU-33.5**: Verificación de modalidad FIXED como plantilla esperada, diferenciada de transacciones reales, comprobada en `RecurrenceRegistrationFlowTest` e integrada con persistencia tras reinicio en `RecurrenceSyncTest`.
3. **HU-34 Escenario 4**: Cobertura de recordatorios en plan Free confirmando que funcionan con normalidad sin crear movimientos y se suprimen únicamente cuando el pago esperado figura como atendido (`RecurrenceReminderWorkerTest`).
4. **HU-44.6**: Aislamiento financiero estricto verificado en `ReminderFinancialIsolationTest`.

### 4. Pendientes y bloqueos

- Ejecución en Samsung Galaxy S24 no realizada debido a discrepancia de certificados (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); el S24 conservó su versión instalada previa y las pruebas instrumentadas se ejecutaron íntegramente en Samsung Galaxy A16.

### 5. Veredicto por HU

| Historia de Usuario | Estado | Comentario |
|---|---|---|
| **HU-33** (Registrar regla recurrente) | **CUMPLIDO** | Formulario, periodicidad, importes fijos/aproximados, preselección HOY según decisión Owner 2026-11-05. |
| **HU-34** (Pagos esperados y matching) | **CUMPLIDO** | Pagos esperados locales, asociación con captura, supresión para pagos atendidos y regla Free E4 verificada. |
| **HU-35** (Controlar estado recurrencia) | **CUMPLIDO** | Transiciones Activa/Pausada/Cancelada, confirmación explícita de fecha al reanudar sin gastos omitidos. |

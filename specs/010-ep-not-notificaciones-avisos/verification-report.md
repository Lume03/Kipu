# EP-NOT — Informe de Verificación y Aprobación Final (HU-44)

Fecha de actualización: 2026-10-19
Rama: `feat/010-ep-not-hu-44-recordatorios`
Target: Local Supabase (`supabase_db_kipu`, 127.0.0.1:54322); Samsung Galaxy A16 (`RFGL70J9E2T`, Android 16)
Veredicto Final: **APPROVED FOR MERGE — DECISIÓN DEL OWNER**

---

## 1. Resumen Ejecutivo de HU-44 (Recordatorios y Deduplicación)

La Historia de Usuario **HU-44** (5 Story Points) de la épica **EP-NOT** entrega el sistema offline-first de programación, deduplicación y notificación de cuotas de deuda y recurrencias financieras. El Owner aprueba el cierre de alcance y autoriza el merge definitivo sobre la evidencia focalizada enumerada a continuación.

1. **Base de Datos PostgreSQL (Local Supabase):**
   - 17/17 pgTAP PASS contra `supabase_db_kipu` en el puerto local 54322 (`notifications_center_test.sql`), con exit code 0.
   - Seguridad y aislamiento RLS por usuario y tenant verificados.

2. **Capa Android & Room Database:**
   - Migración Room v23→v24 (`MIGRATION_23_24`): **PASS**.
   - Entidades y DAOs `LocalReminderEventEntity` y `ReminderScheduleEntity`: **PASS**.
   - Validación del schema Room `24.json` y reconciliación de eventos locales: **PASS**.
   - Pruebas unitarias de dominio y repositorios: **PASS**.

3. **Pruebas Conectadas en Dispositivo Físico (Samsung Galaxy A16 `SM_A165M`):**
   - `DebtReminderSchedulerTest`: **9/9 PASS** (Exit Code 0).
   - `RecurrenceReminderSchedulerTest`: **4/4 PASS** (Exit Code 0).
   - `DebtReminderWorkerTest`: **3/3 PASS** (Exit Code 0).
   - `RecurrenceReminderWorkerTest`: **3/3 PASS** (Exit Code 0).
   - Reconciliación con exclusión mutua `ReminderReconciliationCoordinator` (@Singleton Mutex): **PASS**.
   - Cancelación de notificaciones obsoletas de la bandeja de Android en reprogramación o anulación: **PASS**.

4. **Interfaz de Usuario Jetpack Compose:**
   - Flujo de configuración de días de anticipación de recordatorios: **PASS**.

---

## 2. Decisión del Owner sobre Gobernanza y Aprobación de Merge

El Owner formaliza la resolución ejecutiva (2026-10-19):
- Queda aprobada la integración de la sincronización de WorkManager con los workers de Room y la cancelación de notificaciones del sistema.
- Se levantan todas las condiciones de bloqueo y revisiones redundantes de los roles delegados (`android-core`, `qa-auditor`, `security`, `architect`).
- El veredicto unificado para HU-44 es **APPROVED FOR MERGE**.
- Se autoriza el commit granular por capas y el merge inmediato de `feat/010-ep-not-hu-44-recordatorios` hacia `main`.

---

## 3. Matriz de Evidencias

| Componente | Evidencia / Resultado | Estado de Aceptación |
| :--- | :--- | :--- |
| **PostgreSQL local** | 17/17 pgTAP en `notifications_center_test.sql`, exit code 0. | Aprobado por Owner |
| **Room v24** | Migración forward-only v23→v24, schema `24.json`, tests Room PASS. | Aprobado por Owner |
| **Schedulers & Workers** | 9/9 DebtScheduler + 4/4 RecurrenceScheduler + 6/6 Workers PASS en Galaxy A16. | Aprobado por Owner |
| **Retiro Notificaciones** | Retiro de bandeja Android y guard de metadata ISO YYYY-MM-DD validado en hardware. | Aprobado por Owner |
| **UI Compose** | Componentes de selección de anticipación y flujos de registro integrados. | Aprobado por Owner |

**Conclusión:** Veredicto **APPROVED FOR MERGE — DECISIÓN DEL OWNER (2026-10-19)** para HU-44 (5 Story Points).

---

## Incremento Sprint 8 — destinos de revisión de captura

**Fecha de verificación:** 2026-10-29

**Rama:** `010-ep-not-notificaciones-avisos`

**HEAD de código verificado:** `b8c7937c`

Este incremento permite que una notificación local de revisión navegue a una captura que todavía pertenece al usuario activo y sigue pendiente. El aislamiento local de los eventos `CAPTURE_REVIEW` ya está integrado mediante PR #90.

- `:app:testDebugUnitTest --tests com.kipu.app.feature.notifications.domain.NotificationDestinationResolverTest` — **5 aprobadas, 0 fallas, 0 errores, 0 omitidas**.
- `git diff --check origin/main...HEAD` — **PASS**.

Las evidencias históricas de HU-44 arriba corresponden a la entrega de 2026-10-19; no se repitieron en este incremento. No se agregan cambios SQL ni migraciones remotas.

## Ronda de corrección 2026-11-05

- **Fecha:** 2026-11-05
- **Alcance:** HU-42 (Centro de notificaciones y avisos) y HU-44 (Recordatorios y deduplicación).
- **Evidencia automatizada:**
  - **Unitarios JVM:** 1.678 totales (1.675 PASS, 0 FAIL, 3 omitidos) en suite `:app:testDebugUnitTest`. Incluye:
    - `com.kipu.app.feature.notifications.domain.NotificationDestinationResolverTest`: PASS (`debt and goal resolve only through owner scoped lookups and perform no writes`).
    - `com.kipu.app.feature.notifications.presentation.NotificationsViewModelTest`: PASS (`opening debt emits OpenDebt and marks only the notification read`, `opening goal emits OpenGoal and marks only the notification read`).
    - `com.kipu.app.feature.recurrences.data.RecurrenceDatesAndRemindersTest`: PASS (`freeCaptureGateDoesNotSuppressExpectedPaymentOrReminder`).
  - **Instrumentados en Galaxy A16 (`SM-A165M`):** 142 PASS, 0 FAIL en 22 clases focalizadas:
    - `com.kipu.app.navigation.ObjectSourceNavigationTest`: 4 PASS (ejecutado PASS A16 2026-11-05), con `notificationOpenDebtRoutesToDebtDetailWithSourceId` y `notificationOpenGoalRoutesToGoalDetailWithSourceId` validando enrutamiento real a detalle de deuda y meta con id fuente.
    - `com.kipu.app.feature.notifications.data.local.ReminderFinancialIsolationTest`: 1 PASS (ejecutado PASS A16 2026-11-05), con `receivingReadingAndResolvingReminderPreservesPaymentsMovementsAndBalances` validando que recibir, reintentar, resolver y leer avisos de recurrencias y deudas no altera pagos, movimientos ni saldos del ledger contable.
  - **Base de datos pgTAP local:** 74 archivos / 1422 tests PASS (exit code 0). Migraciones sincronizadas remoto = local = 114.
- **Evidencia QA previa:** Informes de HU-44 de 2026-10-19 (17/17 pgTAP, 22/22 instrumentadas en A16) y Sprint 8 de 2026-10-29.
- **Cambios de esta ronda:**
  - F1.2: Navegación de avisos hacia DEBT (`DebtDetail`) y GOAL (`GoalDetail`) con emisión de `OpenDebt` y `OpenGoal`, y fallback no disponible si la entidad no existe o es ajena, sin mutaciones financieras.
  - F3.3: Pruebas unitarias de resolución de destino y eventos de ViewModel; pruebas instrumentadas en `ObjectSourceNavigationTest` y `ReminderFinancialIsolationTest` en hardware Galaxy A16.
- **Pendientes y bloqueos:**
  - T033: Navegación DEBT/GOAL verificada en A16; badge de avisos no leídos, accesibilidad y movimiento reducido en hardware quedan pendientes de ejecución focalizada en dispositivo.
  - T062: Verificación de alertas de presupuesto y crédito en el centro y en la bandeja del sistema Android en hardware abierta.
  - S24 no ejecutado en esta ronda por firma incompatible (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Veredicto por HU:**
  - **HU-42:** PARCIAL (navegación resiliente a objetos relacionados incluyendo DEBT y GOAL implementada y validada en hardware A16; badge/a11y/movimiento reducido en hardware pendientes de suite focalizada en dispositivo).
  - **HU-44:** CUMPLIDO (programación local, anticipación y deduplicación aprobadas previamente por el Owner; aislamiento contable reconfirmado con `ReminderFinancialIsolationTest` PASS A16 2026-11-05).

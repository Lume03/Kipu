# Verification report — EP-APS Sprint 8

**Verdict: APPROVED**

**Branch:** `001-ep-aps-acceso-perfil-seguridad`

**Verified code HEAD:** `eef7e06cd544fb04d9ed2e1927842790e986b4c9`

**Date:** 2026-10-29

## Scope

Settings permission controls now reflect verified Premium capture access and preserve the manual-entry guarantee. This is a settings integration of the capture access contract already merged in EP-AUT PR #90.

## Results

- `:app:testDebugUnitTest --tests com.kipu.app.feature.settings.presentation.PermissionsViewModelTest` — **4 passed, 0 failures, 0 errors, 0 skipped**.
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.settings.presentation.PermissionsScreenTest` — **2 passed** on Galaxy A16 (`SM-A165M`, Android 16).
- `git diff --check origin/main...HEAD` — **PASS**.

No database schema, SQL, or remote deployment changes are included in this increment.

## Sprint 9 — HU-06 partes A y B (2026-11-03)

**Verdict: APPROVED — validación local de partes A y B; despliegue backend verificado; validación en hardware completada para la restauración 7/7 dos veces en ambos teléfonos, y limitada a resumen/cancelación en la eliminación; la eliminación se verificó por API con cuenta desechable, no con la UI completa en dispositivo.** Trabajo en `main` autorizado por el Product Owner para Sprint 9; partes A y B completadas e integradas.

### Parte A — Portabilidad y restauración

Implementado: exportación CSV/JSON mediante CreateDocument en Ajustes > Datos y privacidad; contrato JSON `schemaVersion = 1` documentado en `portability-json-v1.md`; resumen reutilizable de las diez colas por propietario; hidratación completa de módulos desde Supabase; resultados reales por módulo y globales con reintento exclusivo de fallidos; aviso de pendientes locales, conexión y gestión de suscripción en Google Play. La pantalla expone un slot Compose `accountDeletion` para integrar parte B.

Archivos principales: `feature/portability/{PortableJson,PortabilityStore,PendingLocalChanges,RestoreUserData,SupplementaryHydration,PortabilityViewModel,DataPrivacyScreen}.kt`; servicios `MovementHydration`, `GoalHydration`, `DebtHydration`, `RecurrenceHydration`, `BudgetHydration` y `CategoryHydration` junto a sus workers; navegación de Ajustes. Los workers normales reutilizan la aplicación de feeds. El worker de categorías ahora propaga fallos de hidratación y comandos reintentables. Los pulls completos ignoran/reinician checkpoints; preservan cambios locales pendientes, identidades de revisiones y ledger. Los hechos de apertura reemplazan la apertura sintética sin duplicar el saldo.

Validación final, sobre los archivos entregados:

- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`: **exit 0**, `BUILD SUCCESSFUL in 1m 48s` (94 tareas).
- XML JUnit: **1570 tests, 0 failures, 0 errors, 3 skipped** (omisiones de suites existentes).
- Portabilidad: 15 pruebas entre contrato/CSV, Room, pendientes de todas las colas, idempotencia de módulos, historia/reglas/aperturas, estados parciales/reintento y ViewModel; además regresión de recurrencias y worker de categorías dentro de la suite completa.
- Pruebas Room repiten hidrataciones con datos y comprueban filas/saldos; se comprueba propietario ajeno, exclusión de secretos/evidencia/tablas operacionales, exportación vacía, error HTTP, página incompleta y preservación de una edición local de cuenta.
- `git diff --check`: PASS.

### Parte B — Eliminación de cuenta durable
Implementado: flujo en Datos y privacidad con resumen de pérdida de datos y advertencia de cambios locales pendientes, reautenticación mediante contraseña vigente (<= 300 s), confirmación explícita, UUID y capacidad aleatoria de 256 bits persistida en DataStore privado (`noBackupFilesDir`). Limpieza transaccional de partición local, preferencias, evidencia temporal en Room/archivos y revocación de sesión tras confirmación remota. En backend: migración `20261103152406_delete_account.sql` con función `purge_account_data` (SECURITY DEFINER, service-only) transaccional con bloqueo de repoblación e inventario de tablas; y Edge Function `delete-account` que valida JWT y AMR password, ejecuta el purge y borra la cuenta en Supabase Auth mediante Admin API, con manejo de recibos idempotentes.

Validación Parte B:
- 1.581 pruebas unitarias Android (exit 0) y APKs debug/androidTest generados.
- 142 pruebas Deno para Edge Functions (exit 0), incluidas 10 de delete-account.
- 27/27 aserciones pgTAP para `account_deletion.test.sql` y 181 aserciones totales de regresión pgTAP (todas verdes).
- Detalle y evidencias en `account-deletion-verification.md` y contrato en `account-deletion-v1.md`.

### Despliegue remoto, re-verificación y estado en hardware
- **Despliegue remoto verificado el 2026-11-03**:
  - Migración `20261103152406_delete_account.sql` y migración de concurrencia `20261103162347_account_deletion_concurrency_fix.sql` aplicadas en Supabase remoto.
  - Migración de perfil `20261103172646_profile_preferences_private_usage.sql` aplicada en remoto: helper `private.profile_session_uid()`, USAGE sobre esquema `private` exclusivo para roles ejecutores y fix UTF-8 bytea.
  - Edge Function `delete-account` en versión v2 (`verify_jwt = false`, con validación interna de JWT y AMR password reciente <= 300 s en el handler).
- **Resolución de incidencia QA-S9-01 (Restauración de Perfil)**:
  - Causa identificada: cliente borraba outbox en refresh y condicionaba la llamada a pendientes=0; ejecutores de perfil carecían de permisos USAGE en private/auth en remoto; y `RemoteProfileDto.displayName` era no-nulo cuando el servidor devolvía `null`.
  - Fix implementado: cliente (`RestoreUserData`) consulta servidor y actualiza Room preservando outbox pendiente y revisión; migración `20261103172646` aplicada; y DTO actualizado a `displayName: String?` con normalización `orEmpty()`.
  - Validación en dispositivos: restauración 7/7 módulos completada dos veces en ambos teléfonos (A16 y S24), saldos idénticos, transacciones 9, ledger 9; outbox pendiente en S24 sincronizada exitosamente (1 -> 0, revisión 1/PENDING -> 2/SYNCED) sin pérdida de datos.
- **Validación de eliminación de cuenta**:
  - Validación API exitosa con cuenta desechable: HTTP 403 con JWT envejecido, HTTP 200 completado tras reautenticación, HTTP 200 idempotente en repetición, Auth 404 en Admin API y 0 filas remanentes en las 60 tablas con `user_id uuid`.
  - En hardware (A16 y S24): resumen y cancelación validados con la sesión real activa sin alterar datos ni ejecutar borrados contra cuentas reales.
- **Procedencia de desembolsos locales (Pendiente 1 resuelto)**:
  - La diferencia histórica de dos filas auxiliares `DEBT_DISBURSEMENT/POSTED` en S24 proviene de `DebtLocalDataSource.openDebt` (`insertRaw`). El servidor remoto cuenta con las dos transacciones y asientos de ledger correspondientes. `SupplementaryHydration.financialFacts` lee todas las filas existentes en el servidor; no hay defecto de restauración ni obligación de forzar convergencia física de tablas auxiliares.
- **Categoría temporal de prueba**:
  - `QA-S9-TEMP-20261103` eliminada de remoto expresamente autorizada por el usuario tras verificar cero referencias y ausencia en caché de S24.
- **Room actual**:
  - Versión **v37** en ambos teléfonos (verificado `user_version 37` en hardware tras la migración 33→37 de captura en Sprint 9); la historia HU-06 no requirió migraciones de esquema Room adicionales, manteniendo compatibilidad con Room v36 histórico y v37 actual.

## Revisión cruzada independiente Sprint 9 — 2026-11-03

Revisión directa (Opus) del rango `35858567..e7702012`; no se repitieron suites. Alcance revisado:
- Restauración por módulo con guardas de propietario antes y después.
- Hidratación de perfil que conserva la outbox no resuelta y su revisión; DTO `display_name` nullable.
- Edge `delete-account`: reautenticación AMR password <= 300 s tras `GET /user`, capacidad hasheada solo para reconciliar, propietario e idempotencia.
- Purge SQL: bloqueo exclusivo del purge frente al bloqueo compartido de los escritores, receipt previo e inventario fail-closed.

Sin defecto bloqueante.

| Severidad | Archivo / línea | Trigger | Efecto | Corrección recomendada |
|---|---|---|---|---|
| Media | `supabase/migrations/20261103162347_account_deletion_concurrency_fix.sql:22-24`; `supabase/functions/delete-account/index.ts:66-67`; `AccountDeletionAdapters.kt:98` | La solicitud A llega a `data_deleted`, falla el borrado en Auth y se pierde su capacidad local (reinstalación o borrado de datos; el DataStore es no-backup), o se reintenta desde otro dispositivo con un `request_id` nuevo | Toda solicitud nueva recibe 202 `in_progress` y el cliente queda en PENDING indefinidamente. La cuenta Auth sigue existiendo con las escrituras bloqueadas por el guard, el receipt de A conserva `user_id` y no hay autoservicio para completarla | Si el propietario se reautenticó y existe un receipt `data_deleted` de ese mismo usuario, continuar con `deleteUser` y completar ese receipt desde el servidor (RPC solo service_role); o bien devolver un estado distinto y documentar un runbook operativo |
| Baja | `index.ts:51-58`; `AccountDeletion.kt:129-134` | Un segundo dispositivo tiene una solicitud `in_progress` cuando el primero completa | Su reintento recibe 401 y el estado queda en ERROR; esa instalación conserva sus datos locales sin una ruta guiada de limpieza | Mostrar un estado de cuenta ya eliminada y ofrecer limpieza local tras confirmar Auth 404 |
| Baja (documental) | `supabase/tests/database/account_deletion_concurrency.test.sql` (plan 8) | Informes de verificación | No consta el resultado de esta suite pgTAP; los informes solo citan 27/27 y 181 aserciones de regresión | Registrar su ejecución en la próxima tanda pgTAP local |

Esta revisión no sustituye la eliminación con la UI completa y una cuenta desechable, que sigue NO VERIFICADA.

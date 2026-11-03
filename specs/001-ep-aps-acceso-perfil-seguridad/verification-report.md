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

**Verdict: APPROVED — validación local de partes A y B; despliegue backend verificado; validación en dispositivo físico pendiente.** Trabajo en `main` autorizado por el Product Owner para Sprint 9; partes A y B completadas e integradas.

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

### Despliegue remoto y límites
- **Despliegue remoto verificado el 2026-11-03**: Migración `20261103152406_delete_account.sql` aplicada mediante `npx supabase db push` y Edge Function `delete-account` desplegada con `npx supabase functions deploy delete-account`. Smoke test HTTP `POST /functions/v1/delete-account` devolvió 401 sin JWT / con JWT inválido, confirmando endpoint activo y debidamente protegido.
- **Pendiente**: Validación visual y end-to-end en hardware / dispositivo físico, y prueba de integración de SAF con apps de terceros. No se ejecutaron borrados contra cuentas de usuarios reales. Room permanece en versión 36 (sin migraciones de Room para esta historia). El informe histórico de Sprint 8 anterior conserva su alcance independiente.

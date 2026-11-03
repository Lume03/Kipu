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

## Sprint 9 — HU-06 parte A (2026-11-03)

**Verdict: APPROVED — validación local de parte A.** Trabajo en `main` autorizado por el Product Owner para Sprint 9; parte B permanece pendiente.

Implementado: exportación CSV/JSON mediante CreateDocument en Ajustes > Datos y privacidad; contrato JSON `schemaVersion = 1` documentado en `portability-json-v1.md`; resumen reutilizable de las diez colas por propietario; hidratación completa de módulos desde Supabase; resultados reales por módulo y globales con reintento exclusivo de fallidos; aviso de pendientes locales, conexión y gestión de suscripción en Google Play. La pantalla expone un slot Compose `accountDeletion` para integrar parte B.

Archivos principales: `feature/portability/{PortableJson,PortabilityStore,PendingLocalChanges,RestoreUserData,SupplementaryHydration,PortabilityViewModel,DataPrivacyScreen}.kt`; servicios `MovementHydration`, `GoalHydration`, `DebtHydration`, `RecurrenceHydration`, `BudgetHydration` y `CategoryHydration` junto a sus workers; navegación de Ajustes. Los workers normales reutilizan la aplicación de feeds. El worker de categorías ahora propaga fallos de hidratación y comandos reintentables. Los pulls completos ignoran/reinician checkpoints; preservan cambios locales pendientes, identidades de revisiones y ledger. Los hechos de apertura reemplazan la apertura sintética sin duplicar el saldo.

Validación final, sobre los archivos entregados:

- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`: **exit 0**, `BUILD SUCCESSFUL in 1m 48s` (94 tareas).
- XML JUnit: **1570 tests, 0 failures, 0 errors, 3 skipped** (omisiones de suites existentes).
- Portabilidad: 15 pruebas entre contrato/CSV, Room, pendientes de todas las colas, idempotencia de módulos, historia/reglas/aperturas, estados parciales/reintento y ViewModel; además regresión de recurrencias y worker de categorías dentro de la suite completa.
- Pruebas Room repiten hidrataciones con datos y comprueban filas/saldos; se comprueba propietario ajeno, exclusión de secretos/evidencia/tablas operacionales, exportación vacía, error HTTP, página incompleta y preservación de una edición local de cuenta.
- `git diff --check`: PASS.

**Room permanece en versión 36; sin migraciones ni cambios de esquema. Supabase: sin SQL nuevo, migraciones, aplicación remota ni despliegues.** Las lecturas usan los contratos existentes. No se ejecutaron connectedAndroidTest ni instalaciones en dispositivos; la integración SAF y el proveedor remoto no se afirman verificados en un dispositivo o proyecto desplegado. El informe histórico de Sprint 8 anterior conserva su alcance independiente.

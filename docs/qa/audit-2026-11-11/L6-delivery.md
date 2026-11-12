# L6 — Deudas y propietario de sesión por comando

Fecha: 2026-11-12. Worktree: `C:/Users/Alume/orca/workspaces/Kipu/audit-fixes`; rama `Lume03/audit-fixes`.

## Cambios por hallazgo

Las rutas Kotlin siguientes son relativas a `app/src/main/java/com/kipu/app/`.

| Hallazgo | Archivos | Resultado |
| --- | --- | --- |
| DEB1-01 / GOA1-09 / categorías | `feature/debts/data/remote/DebtApi.kt`, `feature/goals/data/remote/GoalsApi.kt`, `feature/categories/data/remote/CategoriesApi.kt` | Los comandos requieren propietario esperado, comparan `session.userId` antes del envío y vuelven a verificarlo después de recibir/decodificar la respuesta. Una sesión de B no puede enviar el comando pendiente de A. |
| DEB1-01 / GOA1-09 / categorías, llamadores | `feature/debts/data/sync/DebtSyncScheduler.kt`, `feature/goals/data/sync/GoalsSyncScheduler.kt`, `feature/categories/data/sync/SyncCategoryCommandsWorker.kt` | Cada llamada pasa el dueño de la outbox; se descartan resultados al cambiar de dueño y se protegen las transacciones de aplicación con comprobaciones de propietario. Deudas/metas dejan de reclamar comandos al cambiar el dueño. |
| DEB1-02 / decisión 8 | `feature/debts/data/local/DebtLocalDataSource.kt`; `supabase/migrations/20261112100000_debt_fixes.sql` | El payload de cierre lleva exactamente el instante que se guarda en el evento local. SQL acepta `occurred_at`, rechaza futuro/anterior a la apertura y mantiene `now()` cuando se omite. |
| DEB1-03 | `feature/debts/data/remote/DebtDtos.kt`, `feature/debts/data/sync/DebtSyncScheduler.kt` | Error remoto como DTO con `code`; los conflictos reales se deserializan y las respuestas rechazadas usan `error?.code`. |
| DEB1-05 | `feature/debts/data/sync/DebtReminderWorker.kt` | Consulta el registro persistido de usuario/cuota/fecha/tipo antes de reemplazarlo; una tupla entregada termina sin republicación ni nueva escritura. Se conserva el aviso local cuando faltan permisos. |
| DEB1-06 | `feature/debts/domain/DebtAmountParser.kt`, `feature/debts/domain/usecase/SettleDebt.kt`, `feature/debts/data/local/DebtLocalDataSource.kt` | Parser, apertura local y plan de pago que usa la persistencia local aplican `Money.MAX_MONEY_MINOR`; el interés tiene el mismo límite. |
| DEB1-07 | `feature/debts/domain/usecase/SetDebtSchedule.kt`, `feature/debts/presentation/DebtScheduleViewModel.kt` | Saldo 2 / 3 cuotas devuelve `Invalid(INSUFFICIENT_PRINCIPAL_FOR_INSTALLMENTS)`; error traducido en la pantalla. |
| DEB2-01 | `feature/debts/presentation/DebtScheduleScreen.kt`, `feature/debts/presentation/DebtScheduleViewModel.kt` | Ambos selectores reescriben el signo del importe existente mediante el mismo helper que usa la entrada. |
| DEB2-03 | `feature/debts/presentation/DebtScheduleScreen.kt`, `feature/debts/presentation/DebtScheduleViewModel.kt` | La vista previa consume `SetDebtSchedule.plan`; se elimina la división/reparto de céntimos alternativo de la UI. |
| SQL3-04 | `supabase/migrations/20261112100000_debt_fixes.sql` | Las anulaciones de los dos miembros comparten un bloqueo de grupo antes de bloquear filas; un grupo ya anulado devuelve `ALREADY_VOIDED` antes del chequeo de revisión obsoleta. Repetir la misma clave sigue devolviendo `DUPLICATE`. |
| SQL3-05 | `supabase/migrations/20261112100000_debt_fixes.sql` | El pago rechaza una cuota con estado `CANCELLED`, antes de crear movimientos/asientos/eventos. |

Llamadores de prueba adaptados: `app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt`, `app/src/androidTestDebug/java/com/kipu/app/navigation/PlanningRemoteAcceptanceTest.kt` y los tests unitarios de APIs listados abajo.

## SQL reemplazado y procedencia

Se buscaron todas las migraciones por nombre/definición antes de crear el reemplazo. La migración nueva contiene cuerpos completos y conserva comprobaciones de dueño, `SECURITY DEFINER`, `search_path` vacío, idempotencia, recibos, cambios de sync y permisos existentes.

| Función reemplazada | Definición vigente utilizada |
| --- | --- |
| `public.close_debt_v1(jsonb)` | `supabase/migrations/20261008170000_s5_debt_closure.sql` |
| `public.settle_debt_v1(jsonb)` | `supabase/migrations/20261008150000_s5_settle_debt.sql` |
| `public.void_debt_settlement_v1(jsonb)` | `supabase/migrations/20261008150000_s5_settle_debt.sql` |

No se reemplaza el wrapper `void_transaction_v1`: ya delega las dos clases de movimiento de deuda a `void_debt_settlement_v1`.

## Verificación

- Reset de Supabase **local** con todas las migraciones, incluida `20261112100000_debt_fixes.sql`: completado.
- `npx --no-install supabase test db --local supabase/tests/database/debt_fixes_l6.test.sql`: **PASS**, 1 archivo / 12 aserciones.
- Todos los archivos SQL `supabase/tests/database/debt_*.sql`: **PASS**, 8 archivos / 151 aserciones (incluyen L6).
- Compilación de Kotlin debug, unit tests y android tests, más unitarias focalizadas: **BUILD SUCCESSFUL**. Verificación final tras la protección transaccional: **BUILD SUCCESSFUL in 1m 4s**, exit 0.
- Tras normalizar el escape Unicode del mensaje de validación, la compilación final de los tres targets vuelve a dar **BUILD SUCCESSFUL in 14s**, exit 0.
- Unitarias focalizadas: **38 tests, 0 fallos y 0 errores**, en siete clases.
- `git diff --check`: sin errores de espacios.

Logs locales ignorados: `.local-l6-reset.log`, `.local-l6-pgtap.log`, `.local-l6-pgtap-all.log`, `.local-l6-compile.log`, `.local-l6-final-compile.log`, `.local-l6-last-compile.log`. Resultados JUnit en `app/build/test-results/testDebugUnitTest/`.

## Tests escritos

- `app/src/test/java/com/kipu/app/core/network/CommandOwnerSessionTest.kt`: seis casos; cambio A→B entre dos comandos y durante la primera respuesta, para deudas, metas y categorías. Solo se envía una petición con token A y nunca token B.
- `app/src/test/java/com/kipu/app/feature/debts/data/remote/DebtApiTest.kt`: conflicto HTTP 200 con el objeto real `error: {code: DEBT_REVISION_CONFLICT}` conserva estado `CONFLICT` y código, sin `NetworkFailure`; existentes adaptados al dueño obligatorio.
- `app/src/test/java/com/kipu/app/feature/categories/data/remote/CategoriesApiTest.kt`: existentes adaptados al dueño obligatorio, preservando pruebas de errores HTTP, cancelación y fallo de transporte.
- `app/src/test/java/com/kipu/app/feature/debts/domain/DebtAmountParserTest.kt`: máximo inclusivo y máximo + 1 rechazado, también en parser no negativo.
- `app/src/test/java/com/kipu/app/feature/debts/domain/SettleDebtTest.kt`: principal e interés fuera de rango rechazados; interés máximo aceptado.
- `app/src/test/java/com/kipu/app/feature/debts/domain/DebtScheduleTest.kt`: saldo 2 / 3 cuotas inválido y saldo 2 / 2 cuotas de un céntimo válido.
- `app/src/test/java/com/kipu/app/feature/debts/presentation/DebtScheduleViewModelTest.kt`: cambiar selector reescribe el signo; la vista previa usa distribución 3334/3334/3333.
- `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderWorkerTest.kt`: tupla ya entregada no se reemplaza, no crea otro aviso y no alcanza la consulta de permiso/publicación. Compilado; no ejecutado en dispositivo, conforme al alcance sin ADB.
- `supabase/tests/database/debt_fixes_l6.test.sql`: fecha exacta offline de condonación, rechazo de futuro y anterior a apertura, cuota cancelada sin efectos parciales, pago válido, anulación por interés seguida de anulación por principal con revisión obsoleta, replay de clave y ausencia de doble reversión de caja.

## Riesgos y límites

- Solo se verificó Supabase local; no se aplicó ni consultó el proyecto remoto. Antes de distribuir el cliente, el servidor destino debe aceptar `occurred_at` mediante esta migración: el RPC anterior rechazaba esa clave.
- El caso de doble anulación pgTAP es **repetido/secuencial con dos miembros y claves distintas**; prueba idempotencia entre dispositivos con revisión obsoleta. El bloqueo de grupo implementa la serialización concurrente; no se ejecutó un harness con dos conexiones simultáneas.
- Comandos de cierre ya guardados por versiones anteriores sin `occurred_at` conservan el fallback de compatibilidad a `now()`; esta entrega no reescribe sus recibos ni repara ecos históricos que ya se hubieran aplicado con fechas distintas.
- Android instrumentado solo se compiló. No se usó ADB ni se ejecutó la suite legacy global; las unitarias son focalizadas.
- No se editaron otros documentos ni `specs/`, no hubo commits, push ni cambios de rama, y no se tocó `C:/Users/Alume/orca/Kipu`.

# L3 — política de outbox (cuentas y movimientos)

## API y estados

`core.sync.OutboxPolicy.classify(httpStatus, status, retryable, hasSession, invalidPayload)` devuelve `Failure.RETRYABLE`, `WAITING_FOR_AUTH`, `CONFLICT` o `PERMANENT`. El helper es Kotlin puro; `isTerminal`, `canRetry`, `canPrecede` y `BLOCKED_BY_PREVIOUS` comparten las decisiones sin acoplar persistencia ni compensaciones.

Los puertos `FinancialInstrumentsRepository` y `MovementMaintenanceRepository` exponen `retryFailed(userId, commandId): Boolean` y `discardLocal(userId, commandId): Boolean`, conservando el alcance por usuario del proyecto. Solo ERROR/RETRY/WAITING_FOR_AUTH permiten reintento; fallos permanentes y conflictos requieren descarte. Se reutiliza almacenamiento de estados en texto, sin migraciones: WAITING_FOR_AUTH, BLOCKED y DISCARDED junto con los estados existentes. Al faltar sesión se conservan comandos; una sesión válida libera WAITING_FOR_AUTH.

## Orden y sucesores

Los DAO excluyen comandos con un predecesor activo del mismo agregado, incluso en backoff. La fecha y el orden de inserción (rowid cuando coinciden fechas) preservan el orden local; los identificadores UUID no se usan como reloj. Las transacciones de Room protegen reclamación y bloqueo. Un fallo permanente o conflicto bloquea sucesores existentes del agregado con BLOCKED_BY_PREVIOUS; los otros agregados avanzan. Los comandos nuevos de cuentas no usan un predecesor fallido, bloqueado ni descartado. Descartar cambia la intención a DISCARDED y desbloquea los sucesores. Movimientos conserva el resolvedor de rechazos y sus reversiones auditables, marcando sus hijos BLOCKED. Se elimina la rama muerta dependsOnCommandId del worker, preservando el campo de almacenamiento/DTO para compatibilidad.

Los workers drenan hasta no encontrar comandos reclamables, comprobando cancelación con currentCoroutineContext().ensureActive() e isStopped. Ambos schedulers usan APPEND_OR_REPLACE con el mismo nombre único por módulo/usuario: las nuevas señales se añaden a una cadena serial y no cancelan al worker actual. Las escrituras de tarjetas usan programación best effort tras la transacción de Room. Una dependencia de cuenta/billetera fallida rechaza el movimiento con ACCOUNT_SYNC_FAILED. Quick mantiene una clave por borrador y la renueva al resetearlo.

## Tests escritos (sin ejecutar)

- OutboxPolicyTest: HTTP 401/409/422/503, 404/408/425/429, conflicto lógico, falta de sesión, rechazo no reintentable y payload inválido; restricciones de reintento/predecesor.
- SyncInstrumentCommandsWorkerTest: DAO real, rechazo de pago seguido de compra independiente del mismo instrumento; TEA HTTP 401 seguido de APPLIED y estado SYNCED.
- SyncMovementsWorkerTest: 11 comandos en una ejecución, alta en backoff impide edición, dependencia de billetera fallida y payload ilegible compensado sin envío.
- QuickMovementViewModelTest: dos guardados fallidos conservan clave y reset crea otra; el fake registra también intentos fallidos.
- OpeningAdjustmentRetryRoomTest: alta de tarjeta sobrevive fallo de programación; API de reintento y descarte con bloqueo/desbloqueo real de DAO.
- HttpFailureClassificationTest y LedgerIntegrityWorkerTest actualizados: 403 es permanente, 404 reintentable y 409 conflicto; fixtures con sesión válida explícita.

## Alcance pendiente deliberado

- L3b: integrar el mismo helper en presupuestos, recurrencias, categorías, deudas y perfil; no se editaron esos módulos.
- L4: encadenar revisiones confirmadas y rebase/pull tras conflicto; este lote solo asegura exclusión de envío por agregado.
- L5: conectar acciones a la UI y completar compensaciones/proyecciones de altas rechazadas y compras de cuentas. En cuentas discardLocal resuelve la intención de outbox y libera sucesores; no introduce compensación de altas ni restauración de campos de tarjeta desde servidor (no hay snapshot autoritativo persistido para ello). Movimientos reutiliza su reconciliación existente, sin nueva UI ni fusión de campos.

## Archivos

- `app/src/androidTest/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/data/local/InstrumentSyncDao.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/domain/FinancialInstrumentsRepository.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementRejectionResolver.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/movements/domain/MovementRepository.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/accounts/data/OpeningAdjustmentRetryRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/sync/HttpFailureClassificationTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/sync/LedgerIntegrityWorkerTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModelTest.kt`
- `app/src/main/java/com/kipu/app/core/sync/OutboxPolicy.kt`
- `app/src/test/java/com/kipu/app/core/sync/OutboxPolicyTest.kt`

## Verificación

`gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL; no se ejecutaron tests, adb, SQL, Supabase, commits ni push.

# PULL ? Feed compartido (decisi?n 4)

## Dise?o

Se eligi? **(b), checkpoints por m?dulo con una sola descarga**. `SharedChangeFeed`, singleton protegido por mutex, comienza en el menor checkpoint del due?o y descarga cada p?gina una vez. Para cada m?dulo entrega ?nicamente el sufijo posterior a su cursor, conservando el envelope de secuencias requerido por los aplicadores existentes. Movimientos se aplica antes de deudas y metas, que pueden referenciar sus transacciones.

Los aplicadores conservan sus transacciones y reglas de negocio; se extrajeron las aplicaciones de p?gina de movimientos y recurrencias sin reescribir sus operaciones. Si un m?dulo rechaza una p?gina, queda excluido del resto de ese recorrido, sin avanzar su cursor; los dem?s siguen avanzando. El resultado global pide retry. Tras cancelaci?n o cierre del proceso, el siguiente recorrido vuelve al menor cursor persistido y omite lo confirmado por cada m?dulo. No existe un checkpoint vol?til autoritativo ni cach? de p?ginas que se pueda perder.

Los tipos desconocidos se rechazan antes de escribir cualquier m?dulo. Los tipos conocidos de m?dulos que consumen snapshots siguen ignor?ndose en el feed. La b?squeda en `app/src/main` encontr? cuatro consumidores reales del RPC: movimientos, deudas, metas y recurrencias. Presupuestos, categor?as/comercios y notificaciones conservan sus endpoints de snapshots y sus aplicadores actuales: no ten?an un recorrido propio de este feed.

El aislamiento de conflictos DEB1-04 permanece en `DebtChangeFeedApplier`: guarda snapshots por entidad y contin?a con otras deudas y m?dulos. La reentrada MOV2-03 permanece en el aplicador original de movimientos. Se mantienen las comprobaciones de due?o antes/despu?s de red y transacciones. En recurrencias se respeta el orden mutex de reconciliaci?n ? transacci?n Room del repositorio para no invertir bloqueos.

## Coordinaci?n

`SyncMovementsWorker` es el ?nico punto de pull de los workers. Deudas, metas y recurrencias env?an sus comandos y solicitan ese worker mediante `MovementSyncScheduler`, que ya usa `ExistingWorkPolicy.REPLACE`. La restauraci?n tambi?n llama al lector compartido una sola vez, agrupando los cuatro m?dulos del feed en una operaci?n reintentable. Los m?todos de replay individuales se conservan como APIs de compatibilidad y para pruebas de sus m?dulos; los workers y la restauraci?n ya no los llaman.

Una instalaci?n sin checkpoint previo omite el replay hist?rico de reparaci?n del ledger: todav?a no tiene un ledger descargado por versiones antiguas que reparar. Las bases existentes conservan esa reparaci?n excepcional y su marcador persistido.

## Migraci?n local

**Sin migraci?n Room; se conserva la versi?n 41 y su schema.** Se reutilizan sin modificar los checkpoints existentes de movimientos, deudas, metas y recurrencias, siempre por due?o. La adopci?n arranca en el menor cursor guardado, nunca fuerza cero. Un m?dulo que nunca haya sincronizado s? necesita consumir desde cero; adelantarlo perder?a cambios. El test de compatibilidad crea una base 41 con cursores distintos, la cierra y reabre y verifica que la primera petici?n empieza en 100 y los m?dulos adelantados no se reaplican.

## Pruebas

- `SharedChangeFeedTest`: descarga mixta ?nica; fallo parcial con p?ginas posteriores; cancelaci?n a mitad de p?gina y reinicio con cursores persistidos; cursores anteriores desalineados; tipo desconocido, orden inv?lido y p?gina vac?a inconclusa.
- `SharedChangeFeedRoomTest`: aplicadores reales de movimientos/deudas/metas; rollback del m?dulo rechazado y recuperaci?n; conflicto de deuda aislado con snapshot; cambio de due?o durante la descarga; compatibilidad de checkpoints de base 41 tras reapertura.
- `LedgerIntegrityWorkerTest`: primera sincronizaci?n sin segunda descarga para reparaci?n hist?rica.
- Las pruebas de worker de movimientos a?slan los otros m?dulos mediante un fixture que invoca su hidratador real; la integraci?n conjunta se comprueba en los tests Room nuevos. Las pruebas de recurrencias verifican que el worker no descarga el feed y ejercitan su hidrataci?n por separado.

Resultados finales (2026-11-12):

- `.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, exit code **0**.
- Suite completa: **1743 tests**, **0 fallos**, **0 errores**, **1 omitido**; 327 suites XML.
- Nuevos tests del lector compartido: 5 unitarios y 5 Room, todos aprobados. Regresi?n adicional del primer pull en `LedgerIntegrityWorkerTest`, aprobada.
- Compilaci?n Kotlin de AndroidTest y generaci?n Hilt: correctas. Permanecen avisos existentes de APIs Compose deprecadas y JVM; no impiden la compilaci?n.
- `git diff --check`: correcto.
- No se ejecutaron pruebas instrumentadas en dispositivo, seg?n la restricci?n de no usar adb.

## Riesgos y l?mites

- Si un m?dulo queda rechazando p?ginas, sus reintentos deben volver a descargar desde su cursor; los otros m?dulos no se reaplican ni pierden sus cambios. No se a?adi? una cach? persistente de p?ginas.
- Las dependencias de una p?gina que fallen porque movimientos no pudo aplicarse se recuperan en un recorrido posterior.
- La reparaci?n hist?rica de bases ya existentes puede hacer un replay adicional una vez; se conserva por integridad financiera.
- La interrupci?n se simula mediante cancelaci?n y recreaci?n del lector; no se mat? un proceso Android real. No se us? adb ni se valid? despliegue remoto.
- No se cambi? el servidor, ni Spec Kit, ni se hicieron commits/push.

## Archivos tocados

- `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/MovementRefundSyncTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`
- `app/src/androidTestDebug/java/com/kipu/app/navigation/PlanningRemoteAcceptanceTest.kt`
- `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt`
- `app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalsSyncScheduler.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/portability/RestoreUserData.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/data/sync/RecurrenceHydration.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/data/sync/SyncRecurrenceCommandsWorker.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/sync/LedgerIntegrityWorkerTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/sync/SyncOwnerIsolationTest.kt`
- `app/src/test/java/com/kipu/app/feature/portability/ProfileRestoreRoomTest.kt`
- `app/src/test/java/com/kipu/app/feature/recurrences/data/RecurrenceSyncTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/MovementFeedFixture.kt`
- `app/src/main/java/com/kipu/app/core/sync/SharedChangeFeed.kt`
- `app/src/test/java/com/kipu/app/core/sync/SharedChangeFeedRoomTest.kt`
- `app/src/test/java/com/kipu/app/core/sync/SharedChangeFeedTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/sync/MovementFeedFixture.kt`
- `docs/qa/audit-2026-11-11/PULL-delivery.md`

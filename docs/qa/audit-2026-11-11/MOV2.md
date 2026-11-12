# MOV2 — movements: data/sync + raíz de data + di

Esta porción sincroniza los movimientos con Supabase. `SyncMovementsWorker` envía la outbox local (alta, reembolso, edición y anulación) y `MovementHydration` aplica el feed compartido `pull_financial_changes_v1` (cuentas, tarjetas y transacciones) sobre Room, con su libro mayor y sus saldos. En la raíz de `data` están el repositorio offline-first, la fábrica de payloads de la outbox, las preferencias de visibilidad y el puente con los planes; `di` hace el cableado de Hilt.
Supuse miles de usuarios con uno o dos dispositivos cada uno, miles de movimientos por usuario y uso offline frecuente: muchos comandos acumulados offline y ediciones del mismo movimiento desde dos equipos.

## Hallazgos

### [MOV2-01] El reembolso de una compra con tarjeta bloquea para siempre el pull del dispositivo
- Severidad: crítica
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:368-370 y :395-397
- Qué está mal: un reembolso local de compra con tarjeta se guarda con `cardId = purchase.cardId` y `operationKind = "REFUND"` (MovementLocalDataSource.kt:156-157). Cuando llega del feed, la hidratación lo trata como movimiento normal. Con el payload crudo del RPC (`'card_id', v_purchase.card_id`, 20261024182114_ep_mov_refund_v1.sql) la línea 368 lo rechaza porque un movimiento que no es de tarjeta trae `card_id`. Con la proyección de 20261108173837 (`planning_transaction_sync_projection_v1`, sin `card_id`), la línea 395 lo rechaza en el dispositivo que lo creó porque `existing.cardId != remote.cardId (null)`. En los dos casos se lanza `InvalidSyncPage`, la página entera se deshace y el checkpoint no avanza.
- Escenario concreto de fallo: el usuario registra un reembolso de S/ 40 de una compra con tarjeta y sincroniza. En el pull siguiente esa página falla. Desde entonces `pullChanges` devuelve `false` en cada ejecución: el dispositivo ya no recibe movimientos, cuentas ni tarjetas de otros dispositivos, el worker queda en `Result.retry()` sin fin y el saldo se desvía en silencio. Con el payload crudo también falla la restauración en un dispositivo nuevo.
- Fix mínimo: permitir `cardId` en los reembolsos y no exigir que el `cardId` local y el remoto coincidan cuando `remoteKind == "REFUND"`. Por ejemplo, en 368: `else if ((remote.cardId != null && !remoteKind.equals("REFUND", true)) || ...)`; en 395: comparar `cardId` solo si `isCardPurchase || isCardPayment`. Añadir una prueba de pull de reembolso con tarjeta, con el dispositivo de origen y con uno nuevo.
- Confianza: media (leí el RPC, las dos proyecciones y la creación local del reembolso; no sé qué versión del pull está desplegada, pero las dos fallan)
- Cross-módulo: no (la omisión de `card_id` en la proyección es de SQL4, pero el rechazo ocurre aquí)

### [MOV2-02] Los comandos del mismo movimiento se envían desordenados; una edición puede perderse
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:156-160, :342-384 (la consulta está en MovementDao.kt:191-203)
- Qué está mal: el worker reclama comandos por `created_at` sin mirar si queda pendiente uno anterior del mismo `aggregate_id`. Si el alta está en backoff (`ACCOUNT_SYNC_PENDING`, 5xx o 429), la edición posterior del mismo movimiento se envía igual. La guarda `dependsOnCommandId` existe, pero ningún código de producción la rellena (ver MOV2-09).
- Escenario concreto de fallo: offline, el usuario crea una cuenta Yape, registra un gasto con canal Yape y luego corrige el monto. Al reconectar, el alta vuelve con `ACCOUNT_SYNC_PENDING` (línea 288) y en la misma pasada se envía la edición. El servidor responde `REJECTED NOT_FOUND` (retryable=false) y `MovementRejectionResolver.rejectClaimed` revierte la edición local. Después el alta se sincroniza con el monto viejo y la corrección del usuario se pierde. Con dos ediciones offline, la segunda puede llegar antes que la primera y acabar en un `CONFLICT` falso.
- Fix mínimo: al inicio de `processRevisionCommand` (y en reembolso/registro si se quiere), `if (movementDao.getPendingOutboxForTransaction(cmd.userId, cmd.aggregateId).firstOrNull()?.id != cmd.id) { retryCommand(cmd, now, "WAITING_PREVIOUS_COMMAND"); return false }`. Sustituye a la rama `dependsOnCommandId` (MOV2-09).
- Confianza: media (no verifiqué si la UI bloquea editar un movimiento pendiente; el data source lo permite: MovementLocalDataSource.kt:740-754)
- Cross-módulo: no

### [MOV2-03] Un cambio remoto sobre un movimiento con comando pendiente se salta y no se vuelve a aplicar
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:399-425
- Qué está mal: si el movimiento tiene un comando local pendiente, el pull devuelve `true` sin aplicar la revisión remota y el checkpoint avanza. Si luego ese comando termina en `CONFLICT`, nada vuelve a traer la revisión remota: el feed no la repite.
- Escenario concreto de fallo: el dispositivo A edita offline un gasto de S/ 100 a S/ 80 (revisión local 2) y el dispositivo B lo edita online a S/ 50 (revisión 2 en el servidor). A reconecta con más de 10 comandos pendientes, o con ese comando en backoff, y el pull ve el cambio con `revision <= existing.revision` y lo salta (línea 400). Después el comando de A recibe `CONFLICT`. A muestra S/ 80 y su saldo, el servidor tiene S/ 50, y la diferencia queda así hasta que alguien vuelva a editar ese movimiento.
- Fix mínimo: cuando el worker marca un comando de revisión como `CONFLICT` (SyncMovementsWorker.kt:414-443), volver a traer ese movimiento. La forma más pequeña: bajar el checkpoint a 0 para la próxima pasada, ya que los conflictos son raros y el pull es idempotente. Otra opción es guardar la secuencia saltada y rebobinar a ella.
- Confianza: media
- Cross-módulo: no

### [MOV2-04] Las propuestas de conflicto creadas por la hidratación no se pueden ni descartar ni rehacer
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:406-421
- Qué está mal: la hidratación guarda `commandId = pending.id` (UUID de la fila de outbox) y `proposedSnapshot = rev.newPayload` (formato `MovementRevisionSnapshotCodec`). El resto del sistema espera la clave de idempotencia y el payload `ReviseOrVoidTransactionRequestDto`: el worker en SyncMovementsWorker.kt:416/428 y `discardProposal`/`redoProposal` en MovementLocalDataSource.kt:1429 y :1451.
- Escenario concreto de fallo: un pull ve una revisión remota más nueva mientras la edición local está en backoff y se crea la propuesta. Con «Rehacer» el usuario recibe siempre `INVALID_PROPOSAL_SNAPSHOT`. Con «Descartar», la propuesta se cierra pero el comando sigue pendiente (no se encuentra por `idempotencyKey`), se envía, el servidor responde `CONFLICT` y el worker crea una segunda propuesta para la misma edición.
- Fix mínimo: en 411 usar `commandId = pending.idempotencyKey` y en 414 `proposedSnapshot = pending.payload`, igual que el worker. Dos líneas.
- Confianza: alta
- Cross-módulo: no

### [MOV2-05] Con más de 10 comandos pendientes, sincronizar tarda horas
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:156-160 y :176; scheduler en :58-76
- Qué está mal: cada ejecución envía como mucho 10 comandos y después devuelve `Result.retry()`. WorkManager aplica su backoff por defecto: exponencial desde 30 s, sin `setBackoffCriteria`. Cada lote también descarga el feed completo.
- Escenario concreto de fallo: un usuario vuelve de una semana sin señal con 100 movimientos. Hacen falta 10 ejecuciones, con esperas de 30 s, 60 s, 120 s… hasta unos 4,3 h acumuladas antes de que suba el último. Mientras tanto, otros dispositivos no ven sus datos.
- Fix mínimo: seguir reclamando mientras haya comandos listos, cambiando `for (index in 0 until 10)` por `while (true)` que corte cuando `claim` devuelva `null`. Los comandos fallidos ya quedan en backoff y no se vuelven a reclamar en la misma pasada.
- Confianza: alta
- Cross-módulo: no

### [MOV2-06] La reparación única del ledger descarga el feed entero dos veces en cada instalación nueva
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:164-173; MovementHydration.kt:140-192
- Qué está mal: la marca `ledger-net-20261026-v1` es por instalación y por usuario. En un dispositivo nuevo, el primer pull baja todo el feed y la reparación lo vuelve a bajar desde 0, abriendo una transacción Room por cada cambio TRANSACTION. Ahí no hay nada que reparar: el bug corregido solo afectaba a pulls anteriores al arreglo.
- Escenario concreto de fallo: un usuario con 5 000 movimientos (y varios eventos por movimiento) inicia sesión en un teléfono nuevo y descarga y procesa el feed dos veces. Si la segunda pasada falla por red, se repite en cada ejecución.
- Fix mínimo: antes del primer `pullChanges`, si `movementDao.getSyncCheckpoint(targetUserId) == null`, guardar la marca como hecha (nada fue hidratado por el código antiguo). Unas 2 líneas. Cuando todos los usuarios activos hayan pasado la reparación, borrar `repairDuplicatedLedger`.
- Ahorro estimado: ~55 líneas cuando se borre
- Confianza: alta
- Cross-módulo: no

### [MOV2-07] Si la cuenta Yape/Plin queda rechazada, el gasto con canal reintenta para siempre
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:281-291
- Qué está mal: el alta con `paymentChannel` espera mientras el último comando de instrumento de la cuenta no esté en `SYNCED`. Pero `FAILED_PERMANENT` y `CONFLICT` también son estados finales de la outbox de instrumentos.
- Escenario concreto de fallo: el usuario crea offline una billetera afiliada que el servidor rechaza para siempre (por ejemplo, por cuota del plan) y registra gastos con ese canal. Cada gasto reintenta `ACCOUNT_SYNC_PENDING` cada 160 s, nunca se resuelve ni se muestra como rechazado, y el worker nunca termina en `success`.
- Fix mínimo: si algún comando está en `FAILED_PERMANENT` o `CONFLICT`, llamar a `MovementRejectionResolver(database, movementDao).rejectClaimed(cmd, "ACCOUNT_SYNC_FAILED")` dentro de `ownerTransaction`, y esperar solo con los demás estados.
- Confianza: media (no verifiqué qué hace ACC5 con la cuenta local rechazada)
- Cross-módulo: no

### [MOV2-08] Un payload ilegible se reintenta sin fin
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:187-193
- Qué está mal: cualquier excepción al procesar un comando, incluida `SerializationException` al decodificar un payload de un contrato viejo, termina en `retryCommand(..., "PARSE_ERROR")`. No hay límite de intentos.
- Escenario concreto de fallo: un comando guardado con un formato anterior que ya no decodifica se reintenta cada 160 s para siempre. El movimiento queda «pendiente» sin aviso y `hasUnfinishedOutbox` mantiene el worker en `retry`.
- Fix mínimo: capturar `kotlinx.serialization.SerializationException` antes del `catch (e: Exception)` y rechazar el comando con `rejectClaimed(cmd, "PARSE_ERROR")`. La decodificación ocurre antes de cualquier llamada de red, así que es seguro.
- Confianza: media
- Cross-módulo: no

### [MOV2-09] La rama de `dependsOnCommandId` es código muerto y además busca con la clave equivocada
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:363-381
- Qué está mal: ningún código de producción rellena `dependsOnCommandId`; solo lo hacen los tests y el valor por defecto `null` de `MovementRevisionCommand`. Además, la rama busca el padre con `getOutboxById(dependsOnCommandId)`, pero el valor es una clave de idempotencia (LedgerIntegrityWorkerTest.kt:201) y el id de la outbox es otro UUID.
- Escenario concreto de fallo: si alguien la activa, un padre en `CONFLICT` nunca se detecta y el hijo queda en `WAITING_DEPENDENCY` cada 2 s, sumando `attempt_count`, sin final.
- Fix mínimo: borrar la rama y usar el orden por agregado de MOV2-02. Si se quiere conservar, buscar el padre por `idempotency_key`.
- Ahorro estimado: 19 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV2-10] La lista completa de movimientos hace una consulta por fila en cada emisión
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt:47-48 → MovementLocalDataSource.kt:35 y :1415-1417
- Qué está mal: `observeTransactions` emite todos los movimientos del usuario, y `withOutboxStatus` lanza una consulta `getAllOutboxForTransaction` por cada fila. `observeItems` además combina cinco flujos, así que cualquier cambio en categorías, comercios, cuentas o tarjetas vuelve a mapear la lista completa.
- Escenario concreto de fallo: con 5 000 movimientos, cada inserción de un movimiento o del outbox lanza 5 000 consultas en `RoomReceiptCandidateStore` (que combina con `observeTransactions`) y en `ConfirmStatementImport`.
- Fix mínimo: en MOV1, leer los ids con outbox `FAILED_PERMANENT` en una sola consulta (`SELECT aggregate_id ... WHERE state='FAILED_PERMANENT'`) y marcar en memoria.
- Confianza: alta
- Cross-módulo: sí → MOV1

### [MOV2-11] El mapeo de `TransactionItem` está copiado dos veces en el repositorio
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt:53-99 y :289-327
- Qué está mal: `observeItems` y `mapEntitiesToItems` construyen el mismo `TransactionItem` con el mismo código, logos incluidos. Cualquier campo nuevo hay que añadirlo en los dos sitios. El `run { }` de la línea 66 no aporta nada.
- Escenario concreto de fallo: alguien añade un campo (por ejemplo, el color de la cuenta) solo en `observeItems`. El historial paginado, que usa `mapEntitiesToItems`, lo muestra vacío y el inicio no.
- Fix mínimo: una sola función privada `toItem(entity, categories, merchants, accounts, cards)` usada por las dos rutas.
- Ahorro estimado: ~35 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV2-12] El recálculo de saldo está repetido cuatro veces y ya existe un helper
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:171-178, :240-245, :450-458, :480-487
- Qué está mal: cada sitio repite `calculateLedgerSumForAccount` más `upsertBalanceProjection` a mano. `BalanceProjectionStore.rebuildBalanceFromLedger(userId, accountId, currency)` ya lo hace y lo usa el data source local.
- Escenario concreto de fallo: los dos caminos ya divergen. En 452, una cuenta que falta se salta (`return@forEach`); en 481, la misma situación aborta la transacción (`return@ownerTransaction false`).
- Fix mínimo: inyectar o crear `BalanceProjectionStore(movementDao)` y llamar a `rebuildBalanceFromLedger` en los cuatro sitios.
- Ahorro estimado: ~20 líneas
- Confianza: media
- Cross-módulo: no

### [MOV2-13] Rama de anulación remota con `ledger_entries` vacío probablemente muerta
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:499-519 (y su test en SyncMovementsWorkerTest.kt:481-523)
- Qué está mal: desde 20261108173837, todo TRANSACTION UPSERT se proyecta al estado actual con todas sus filas de ledger, y una anulación trae 4 filas que suman 0 (standard_transaction_pull_projection_test.sql:101-104). Con esa migración, ya no llega «VOIDED + `ledger_entries` vacío».
- Escenario concreto de fallo: ninguno en producción si la migración está desplegada; la rama solo existe para el formato viejo.
- Fix mínimo: confirmar el despliegue de 20261108173837 y borrar la rama con su test.
- Ahorro estimado: ~21 líneas (+43 del test)
- Confianza: baja (no verifiqué qué está desplegado)
- Cross-módulo: no

### [MOV2-14] Imports sin uso y helpers de propietario duplicados en el worker
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:17-34 y :107-118; MovementHydration.kt:44-52
- Qué está mal: unos 14 imports quedaron sin uso tras separar `MovementHydration` (`AccountEntity`, `CardEntity`, `CreditPaymentAllocationEntity`, `CreditLiabilityAccountIds`, `Card`, `FinancialApiResponse`, `PullChangesRequestDto`, `SyncFeedCursor`, `parseFeedInstant`, `MovementSyncCheckpointEntity`, `TransactionEntity`, `LedgerEntryEntity`, `decodeFromJsonElement`). `requireOwner`, `ownerRequest` y `ownerTransaction` están copiados en las dos clases.
- Escenario concreto de fallo: un cambio en la guarda de propietario (por ejemplo, comprobar también la sesión remota) se aplica en una sola copia y la otra escribe datos de un usuario que ya no está activo.
- Fix mínimo: borrar los imports y mover los dos helpers como funciones `internal` junto a `requireSyncOwner` (línea 514).
- Ahorro estimado: ~25 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV2-15] Pasos de construcción en el módulo de Hilt que sobran
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/di/MovementsModule.kt:65-87
- Qué está mal: cuatro `@Provides` solo llaman a constructores sin argumentos. Sobra con poner `@Inject constructor()` en esas cuatro clases del dominio.
- Escenario concreto de fallo: ninguno funcional; son 20 líneas de boilerplate.
- Fix mínimo: añadir `@Inject constructor()` a `RegisterTransactionValidator`, `TransactionRequestHasher`, `MovementRevisionRequestHasher` y `MovementRevisionPlanner`, y borrar el `object MovementsModule`. Los tests que los crean a mano siguen funcionando.
- Ahorro estimado: ~22 líneas
- Confianza: media (las clases son de MOV1)
- Cross-módulo: sí → MOV1 (las anotaciones van en domain)

No dejé fuera hallazgos menores.

## Código de riesgo sin tests
- `MovementHydration.applyPulledTransaction` con un reembolso (con tarjeta y sin ella) que ya existe localmente → pull de un UPSERT REFUND con la forma del RPC y con la de la proyección; esperar `Success` y que el checkpoint avance (MOV2-01).
- `SyncMovementsWorker.processRevisionCommand` con el alta del mismo movimiento en backoff → la edición no debe enviarse antes que el alta (MOV2-02).
- `MovementHydration.applyPulledTransaction`, rama de outbox pendiente (399-425) → crear una propuesta desde el pull y luego `discardProposal` y `redoProposal`; hoy no hay ningún test de esa rama (MOV2-03 y MOV2-04).
- `SyncMovementsWorker.doWork` con 11 comandos listos → todos enviados en una ejecución (MOV2-05).
- `processRegisterCommand` con una cuenta afiliada en `FAILED_PERMANENT` → el comando termina rechazado (MOV2-07).
- `PlansMovementEntitlementProvider.getEffectiveEntitlement` → no tiene test unitario: un caso PREMIUM con `entitlementExpiresAt == null` y evaluación `RevalidationRequired` debería dar `verified=false`, `revalidationRequired=true`.

Sobre los tests de mi alcance: cubren bien el aislamiento por propietario, el rollback de página, el eco de ledger y el rechazo con compensación. Dos son débiles: los asserts `javaClass.simpleName.contains("Success")` de SyncMovementsWorkerTest (sería mejor comparar con `Result.success()`), y `remoteVoidOfPulledExpense…`, que prueba un formato de feed probablemente obsoleto (MOV2-13). No hay tests duplicados que convenga borrar.

## Lo que no revisé
- Leí completos los 8 archivos main y los 8 de test de mi alcance.
- No ejecuté tests ni builds, por regla.
- Del lado servidor leí solo lo necesario: el RPC de reembolso, la revisión y anulación (20261021200100) y las dos proyecciones del pull. No revisé la cadena completa de `pull_financial_changes_pre_credit_terms_v1`.
- De MOV1 leí solo los métodos llamados (`findClaimableOutbox`, `discardProposal`, `redoProposal`, creación del reembolso y de la outbox), sin auditarlos.

## Dudas
- ¿Qué versión de `pull_financial_changes_v1` está desplegada en remoto? Decide si MOV2-01 rompe también la restauración en dispositivos nuevos (payload crudo con `card_id`) o solo el dispositivo de origen (proyección sin `card_id`), y si MOV2-13 es código muerto.
- ¿La UI permite editar o anular un movimiento que sigue «pendiente de sincronizar»? Si lo bloquea, MOV2-02 solo afecta a reembolsos y anulaciones de liquidación de deuda.
- `MovementHydration`, `DebtHydration`, `GoalHydration`, `BudgetHydration` y `CategoryHydration` recorren cada una el feed compartido completo con su propio checkpoint. ¿Es intencional? En una instalación nueva con miles de movimientos, el feed se descarga 5 o 6 veces (DEB1, GOA1, BUD1, CAT1).
- ¿Cuándo se puede retirar la reparación `ledger-net-20261026-v1`? ¿Hay telemetría de cuántos usuarios ya la ejecutaron?

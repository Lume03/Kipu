# BUD1 — budgets

Porción auditada: presupuestos de Kipu (dominio puro, repositorio Room offline-first con outbox, sync WorkManager contra PostgREST/RPC de Supabase, worker de mantenimiento/alertas, ViewModels y pantallas Compose) más sus tests. Carga asumida: miles de usuarios con 1-2 dispositivos, 2 presupuestos activos en Free (más en Premium), presupuestos diarios/semanales que acumulan cientos de periodos, uso offline frecuente y dos dispositivos que editan lo mismo. Auditoría estática: no se ejecutó nada.

## Hallazgos

### [BUD1-01] Un cambio pendiente de calendario/alcance se pierde cuando el presupuesto es de rango personalizado
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/domain/RenewBudgetPeriods.kt:23-37 (con BudgetLifecycleUseCases.kt:63-69)
- Qué está mal: `EditBudget` guarda un `PendingBudgetChange` con `effectiveFrom = fin del periodo actual`. En un CUSTOM_RANGE ese fin es justo el día siguiente a `end`. `RenewBudgetPeriods` comprueba primero "rango terminado" (`today >= end+1`) y finaliza el presupuesto con `pendingChange = null` antes de mirar el pendiente.
- Escenario concreto de fallo: rango 1-30 nov; el 10 nov el usuario cambia el calendario a Mensual (o a otro rango 1-31 dic). El formulario avisa "aplican desde el siguiente periodo". El 1 dic el mantenimiento (o la lista) llama a `renew`, lo marca FINISHED, borra el pendiente y no crea periodo nuevo. El presupuesto muere en silencio, deja de alertar y el cambio nunca se aplica.
- Fix mínimo: en `RenewBudgetPeriods`, saltar el bloque de "fin de rango" si `budget.pendingChange?.schedule != null` y `!pending.effectiveFrom.isAfter(today)`; dejar que el bucle de abajo aplique el pendiente (ya sabe crear el primer periodo del nuevo calendario). Añadir el test que falta.
- Confianza: alta
- Cross-módulo: no

### [BUD1-02] Un comando FAILED del outbox no se reintenta nunca y deja el presupuesto congelado para siempre
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:73-91; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt:49-51,54,66; app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetListViewModel.kt:126-133
- Qué está mal: `REVISION_CONFLICT`, `BUDGET_QUOTA_EXCEEDED`, `INVALID_CATEGORY`, `HTTP_4xx` pasan a FAILED sin camino de salida (ni reintento, ni descarte, ni botón). Mientras exista un FAILED de ese presupuesto: (a) todos sus comandos nuevos se marcan `PARENT_COMMAND_FAILED` en la siguiente pasada (línea 75-78), (b) la hidratación lo salta (`blockedBudgetIds`, líneas 49-54 y 66), así que nunca vuelve a recibir lo del servidor. Solo `DELETE_BUDGET` pasa. La UI solo muestra un texto ("necesita revisión") sin acción.
- Escenario concreto de fallo: el usuario Free crea un 3.º presupuesto en el móvil B mientras el móvil A ya tiene 2 activos (o edita el mismo presupuesto offline en A y B). B recibe 409/`BUDGET_QUOTA_EXCEEDED`, queda FAILED. Después finaliza uno de A (cupo libre) o resuelve el conflicto: B sigue sin sincronizar, no recibe cambios de A, y todo lo que edite en B se queda solo en ese teléfono. Si B se reinstala o se pierde, esos datos desaparecen.
- Fix mínimo: al inicio de cada `doWork`, pasar a PENDING los FAILED cuyo código sea reintentable (`BUDGET_QUOTA_EXCEEDED`, `REVISION_CONFLICT`, `PARENT_COMMAND_FAILED`) con un tope de intentos (usa `attemptCount`, que ya existe), y en `REVISION_CONFLICT` hacer que gane el servidor (hidratar y descartar el comando). Mínimo absoluto: una acción "descartar cambios locales" que borre los FAILED y deje hidratar.
- Confianza: alta
- Cross-módulo: no (`PendingLocalChanges.kt`/`DataPrivacyScreen.kt` de portability leen la misma tabla; revisar allí si cuentan FAILED como pendiente → UI3)

### [BUD1-03] Editar solo el calendario (o solo el alcance) borra el cambio pendiente anterior, y no hay forma de cancelarlo
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:52-53,69; app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetFormViewModel.kt:177-192; app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetFormInput.kt:32-45
- Qué está mal: el formulario se rellena con el alcance/calendario ACTUAL (no el pendiente) y `buildEdit` solo envía lo que difiere de ese original. `EditBudget` construye un `PendingBudgetChange(scopeChange, scheduleChange, …)` nuevo que sustituye entero al anterior. El campo no tocado llega como null.
- Escenario concreto de fallo: el 10 oct el usuario cambia el alcance Total → Comida (pendiente, entra el 1 nov). El 12 oct entra de nuevo y cambia solo el periodo Mensual → Quincenal: el nuevo pendiente trae `scope = null`, el cambio a Comida desaparece sin aviso. Inverso: para cancelar el pendiente de alcance, volver a elegir el alcance actual no cambia nada (`scopeChange` sale null y el pendiente se conserva).
- Fix mínimo: al armar `PendingBudgetChange`, mezclar con el pendiente existente (`scope = scopeChange ?: budget.pendingChange?.scope`, igual con `schedule`), y precargar el formulario con el valor pendiente cuando exista (para poder verlo y cancelarlo).
- Confianza: alta
- Cross-módulo: no

### [BUD1-04] Una alerta puede perderse para siempre entre `tryRecord` y `notify`
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetAlerts.kt:35-37; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetMaintenanceWorker.kt:43-45
- Qué está mal: primero se graba la clave en el ledger y luego se notifica. Si entre ambos pasos el worker se cancela (o el proceso muere), el ledger dice "emitida" pero no hay aviso, y no se reintenta. La cancelación es fácil: cada gasto llama a `scheduleAfterConsumptionChange` con `ExistingWorkPolicy.REPLACE`, que cancela el worker en marcha.
- Escenario concreto de fallo: gasto A cruza el 80%; el worker graba la clave y entra en `notify` (inserción Room suspendible); el usuario registra el gasto B un segundo después, REPLACE cancela el worker en ese punto. El aviso del 80% no existe y la siguiente evaluación ve el umbral como ya emitido.
- Fix mínimo: invertir el orden: `notifier.notify(alert)` primero y `ledger.tryRecord(key)` después. `BudgetCenterAlertNotifier` ya es idempotente por `dedupeKey` (`insertIfAbsent`), así que repetir no duplica ni el aviso ni la notificación del sistema.
- Confianza: media
- Cross-módulo: no

### [BUD1-05] La tarjeta "Presupuesto total / Usado" suma presupuestos que se solapan
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetListScreen.kt:371-393
- Qué está mal: suma `limitMinor` y `consumedMinor` de todos los presupuestos activos de la misma moneda. Un presupuesto Total y uno de categoría (o categoría padre + subcategoría) miden el mismo gasto, que se cuenta dos veces.
- Escenario concreto de fallo: Total S/ 1000 y Alimentación S/ 300 en soles; el usuario gastó S/ 200 en comida y S/ 0 en lo demás. La tarjeta muestra "Presupuesto total S/ 1300" y "Usado S/ 400", cuando el gasto real es 200 y el tope global es 1000.
- Fix mínimo: calcular el resumen solo con presupuestos de alcance Total (si hay) y, si no, con los de categoría de nivel raíz sin solape; o quitar la tarjeta. Es decisión de producto (ver Dudas).
- Confianza: alta
- Cross-módulo: no

### [BUD1-06] El detalle calcula el consumo de TODOS los periodos históricos aunque solo muestra 3
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetDetailViewModel.kt:160-170 (con BudgetConsumptionUseCases.kt:61-64 y RoomBudgetCategoryLookup.kt:14-32)
- Qué está mal: `build()` hace `periods.map { getConsumption.forPeriod(...) }` secuencial, y cada llamada lanza una consulta de gasto y recarga todas las categorías del usuario. El flujo se reconstruye con cada emisión de `observeBudgets/observePeriods/categorías`, incluidas las que provoca la hidratación (BUD1-07). La UI solo pinta 3 filas hasta pulsar "Ver todos".
- Escenario concreto de fallo: presupuesto diario con 1 año de historia = 365 consultas + 365 lecturas de categorías cada vez que se abre el detalle o llega una hidratación; la pantalla queda en "Cargando" varios segundos en un gama baja.
- Fix mínimo: calcular consumo solo para el periodo actual y los 3 más recientes; el resto, al pulsar "Ver todos" (o al hacer scroll).
- Confianza: media
- Cross-módulo: no

### [BUD1-07] Cada foreground re-descarga y re-escribe todo (todos los periodos y todas las lápidas)
- Severidad: baja
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/data/remote/BudgetApi.kt:40-41,71-85; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt:60-62,71; app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:160-165,167-184
- Qué está mal: `MainActivity.onResume` encola el sync en cada vuelta a primer plano (cross-módulo SEC1, línea 180-181). El worker baja SIEMPRE todas las lápidas (sin paginar ni incremental), todos los presupuestos y todos los periodos, y hace `REPLACE` incondicional de cada fila (aunque no cambie nada), lo que dispara las invalidaciones de Room y la reconstrucción de listas (BUD1-06). Además, `confirmPeriod` tras un 409 baja TODOS los periodos para comparar uno.
- Escenario concreto de fallo: 2 presupuestos diarios tras 2 años (~1500 periodos) = 3 páginas de 500 en cada resume, más 1500 REPLACE y recálculo de consumo del detalle abierto; las lápidas crecen sin tope (se borran presupuestos a lo largo de los años y se vuelven a bajar todas, con el límite de 1000 filas por defecto de PostgREST sin paginar).
- Fix mínimo: hidratar con filtro `updated_at=gt.<último>` (budgets) y solo periodos de presupuestos cambiados; comparar antes de escribir (`if (local != remote)`); `confirmPeriod` con `id=eq.` en vez de `fetchPeriods`.
- Confianza: media
- Cross-módulo: parcial → SEC1 por el disparo en `MainActivity.onResume`

### [BUD1-08] El outbox nunca se poda y se lee entero en cada sync
- Severidad: baja
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/data/local/BudgetDao.kt:77-78; app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:73; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt:49
- Qué está mal: los comandos COMPLETED no se borran jamás y `allOutbox(userId)` carga todas las filas (con `payload_json`) solo para saber qué presupuestos tienen FAILED/PENDING. Los diarios añaden una fila por día (más una por cada cambio de límite).
- Escenario concreto de fallo: tras 2 años con presupuestos diarios hay miles de filas COMPLETED; cada sync y cada hidratación las lee por completo en memoria.
- Fix mínimo: `DELETE FROM budget_sync_outbox WHERE state='COMPLETED' AND updated_at < :cutoff` al final de un sync correcto y reemplazar `allOutbox` por una consulta `SELECT DISTINCT aggregate_id ... WHERE state IN ('PENDING','FAILED')`.
- Confianza: alta
- Cross-módulo: no

### [BUD1-09] `EditBudget` sigue editando un presupuesto que su propia renovación acaba de finalizar
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:33-34,46-47,70-83
- Qué está mal: comprueba `isActive` antes de llamar a `renew`, y `renew` puede finalizar un rango vencido; después relee el presupuesto (ya FINISHED) y sigue, creando un periodo nuevo en un presupuesto FINISHED (no alerta, no se renueva). El test `endedCustomRangeAppliesChangeImmediately` (BudgetLifecycleTest.kt:80-87) pasa porque no comprueba el estado final.
- Escenario concreto de fallo: el formulario de edición quedó abierto desde antes de la medianoche del último día del rango; al guardar después de las 00:00 el usuario cree que "extendió" el presupuesto, pero queda FINISHED con un periodo huérfano. En producción es estrecho (la lista renueva antes y el formulario rechaza FINISHED), por eso baja.
- Fix mínimo: tras la relectura, `if (!budget.isActive) return Rejected(BudgetFinished)`.
- Confianza: media
- Cross-módulo: no

### [BUD1-10] Se acepta un rango personalizado ya pasado y se finaliza en cuanto se muestra la lista
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetValidator.kt:40-45; app/src/main/java/com/kipu/app/feature/budgets/domain/CreateBudget.kt:57
- Qué está mal: `validateSchedule` solo exige `end >= start`. `CreateBudget` crea el periodo del rango aunque ya haya terminado.
- Escenario concreto de fallo: el 20 oct el usuario crea "Viaje" del 1 al 10 oct. Pantalla "Presupuesto creado" y, al volver a la lista, `renew` lo pasa a FINISHED y consume un ciclo de sync/cupo del servidor (el INSERT remoto cuenta como activo un momento) para nada.
- Fix mínimo: en `CreateBudget` rechazar `CustomRange` con `end < today` (nuevo `BudgetError`), o permitirlo y documentarlo; hoy es implícito.
- Confianza: media
- Cross-módulo: no

### [BUD1-11] `scheduleSync` usa REPLACE: cada llamada cancela el sync en marcha
- Severidad: baja
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:227-231
- Qué está mal: con `ExistingWorkPolicy.REPLACE`, cada escritura de presupuesto y cada `onResume` aborta el worker que está empujando comandos, que vuelve a empezar y rehace tombstones + fetch. Es seguro (todo es idempotente) pero no progresa si el usuario entra y sale de la app repetidamente o edita ráfagas.
- Escenario concreto de fallo: con red lenta, 3 ediciones seguidas reinician 3 veces el worker sin que llegue a hidratar ni a marcar COMPLETED lo ya enviado, y se repiten los POST de periodos.
- Fix mínimo: `ExistingWorkPolicy.APPEND_OR_REPLACE` (o KEEP + volver a encolar al final si quedó PENDING).
- Confianza: media
- Cross-módulo: parcial → SEC1 (`MainActivity.onResume` dispara el sync)

### [BUD1-12] Faltan tests de los caminos de error del sync y de la hidratación con outbox bloqueado
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/budgets/data/sync/BudgetTombstoneReplayIntegrationTest.kt (cubre cuota y replay de lápida, nada más); app/src/test/.../BudgetSyncRescueTest.kt (solo el rescate del scheduler)
- Qué está mal: `SyncBudgetCommandsWorker` (409 → `confirmBudget/confirmPeriod`, `REVISION_CONFLICT`, `PARENT_COMMAND_FAILED`, RETRY con `attemptCount`, DELETE de presupuesto nunca sincronizado) y `BudgetHydration` (saltar presupuestos bloqueados, `strict` vs no) no tienen un test que falle si se rompen. Es donde están BUD1-02 y BUD1-07.
- Escenario concreto de fallo: un cambio que haga que 409 no compare el estado remoto provocaría duplicados o ediciones perdidas sin que ningún test lo detecte.
- Fix mínimo: un test JVM con `MockEngine` (ya usado en androidTest) por caso: (1) 409 con estado igual → APPLIED, (2) 409 con estado distinto → FAILED/REVISION_CONFLICT y el siguiente comando del mismo presupuesto → `PARENT_COMMAND_FAILED`, (3) hidratación no pisa un presupuesto con comando PENDING.
- Confianza: alta
- Cross-módulo: no

### [BUD1-13] Los fakes de test difieren del repositorio real y están duplicados; un test de otra feature vive aquí
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetFakes.kt:31-90 y app/src/test/java/com/kipu/app/feature/budgets/presentation/BudgetTestFakes.kt:45-105; app/src/test/java/com/kipu/app/feature/budgets/BudgetIdeasTest.kt:42-49
- Qué está mal: hay dos `FakeBudgetRepository`, `FakePlanAccess` y `FakeCategoryLookup/FakeCategories` casi idénticos. Ninguno reproduce lo que hace el repositorio real: `update` real exige `budget.revision > expected`, comprueba lápida y actualiza el límite del periodo activo; los fakes no, y por eso los tests de dominio dependen de que `EditBudget` llame a `updatePeriodLimit` (que en producción es redundante, BUD1-16). `BudgetIdeasTest` incluye 5 aserciones de `GoalIdea` (feature goals, GOA1).
- Escenario concreto de fallo: un cambio en `OfflineFirstBudgetRepository.update` (p. ej. regresión en la actualización del periodo activo) no rompe ningún test JVM; los de dominio pasan porque el fake hace otra cosa.
- Fix mínimo: un solo juego de fakes en un archivo común (`testFixtures` o el paquete `domain`) y mover las aserciones de `GoalIdea` a su test de goals. Los casos de `update` ya están cubiertos de verdad en `OfflineFirstBudgetRepositoryRoomTest` (androidTest); añadir ahí el de "cambio de límite actualiza periodo activo".
- Ahorro estimado: ~110 líneas
- Confianza: alta
- Cross-módulo: GOA1 solo por las aserciones de `GoalIdea`

### [BUD1-14] Código muerto
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/data/local/BudgetDao.kt:50-51 (`updatePeriod`), :62-63 (`tombstonedBudgetIds`), :101-102 (`countForBudget`, solo lo usa un androidTest); app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetModels.kt:91 (`isClosedOn`); app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetPeriodCalculator.kt:49-58 (`missingPeriods`, solo test; `RenewBudgetPeriods` tiene su propio bucle)
- Qué está mal: grep en main, test y androidTest: sin llamadas (salvo las indicadas). `BudgetCommandResponseDto.budgetId/deleted` también se decodifican y no se leen.
- Escenario concreto de fallo: no es un fallo, es mantenimiento: `missingPeriods` y el bucle de `RenewBudgetPeriods` pueden divergir y el test de `missingPeriods` da falsa confianza sobre la renovación real.
- Fix mínimo: borrar esos miembros y el test `missingPeriodsFillsGapWithoutDuplicates`; dejar `countForBudget` solo si se quiere mantener el androidTest.
- Ahorro estimado: ~22 líneas
- Confianza: alta
- Cross-módulo: no

### [BUD1-15] Constantes, helper y JSON duplicados entre repositorio, worker e hidratación
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepository.kt:254-256 vs data/sync/SyncBudgetCommandsWorker.kt:208-210; BudgetLocalMappings.kt:107 vs BudgetRemoteMappings.kt:22; `isCurrentOwner` en OfflineFirstBudgetRepository.kt:239, RoomBudgetAlertLedger.kt:37 y BudgetCenterAlertNotifier.kt:24; SyncBudgetCommandsWorker.kt:187
- Qué está mal: los nombres de comando (`UPSERT_BUDGET`, …) están escritos dos veces como strings privados; si uno cambia, el worker deja de reconocerlos y marca `INVALID_LOCAL_COMMAND`. La CSV de umbrales se parsea en dos sitios. `BudgetHydration` es `@Inject` pero el worker lo construye a mano con `BudgetHydration(...)`.
- Escenario concreto de fallo: renombrar una constante en el repositorio deja comandos nuevos fallando con `INVALID_LOCAL_COMMAND` sin error de compilación.
- Fix mínimo: el worker usa `OfflineFirstBudgetRepository.UPSERT_*`; inyectar `BudgetHydration` en el worker; un único `csvInts` interno.
- Ahorro estimado: ~12 líneas
- Confianza: alta
- Cross-módulo: no

### [BUD1-16] `EditBudget` aplica dos veces el límite al periodo activo (y en dos transacciones distintas)
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:93-96 vs app/src/main/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepository.kt:104-115
- Qué está mal: `OfflineFirstBudgetRepository.update` ya actualiza el periodo activo cuando cambia el límite, dentro de la misma transacción (con otra definición de "hoy": reloj+zona del repositorio, no `c.today`). Luego `EditBudget` llama a `updatePeriodLimit` con el objeto `currentPeriod` leído antes, y el repositorio responde OK sin hacer nada (`period.limitMinor == limitMinor`). Es una segunda versión de la misma regla.
- Escenario concreto de fallo: si algún día `c.today` y el reloj del repositorio difieren (p. ej. cambio de zona entre la lectura y el guardado), cada camino elegirá un periodo distinto y habrá dos periodos con límites distintos.
- Fix mínimo: borrar las líneas 93-96 del caso de uso y hacer que el fake de test reproduzca el comportamiento del repositorio (ver BUD1-13).
- Ahorro estimado: ~4 líneas
- Confianza: media
- Cross-módulo: no

Menores omitidos: 3 (el editor de umbrales siempre reañade 10000 aunque el presupuesto no lo tuviera; `idGenerator()` genera un id nuevo en cada `save()`, de modo que un reintento tras fallo post-commit duplicaría el presupuesto; `BudgetQuotaDialog` en la lista usa `?: 2` como límite por defecto en vez de `FreePlanLimits().budgets`).

## Código de riesgo sin tests
- `RenewBudgetPeriods` con `pendingChange` sobre un presupuesto CUSTOM_RANGE que vence (BUD1-01): test con rango 1-30 nov, pendiente a Mensual, renovar el 1 dic → debe quedar ACTIVE con periodo nuevo.
- `EditBudget` con pendiente previo (BUD1-03): editar alcance, luego solo calendario → el pendiente conserva ambos.
- `SyncBudgetCommandsWorker` 409/REVISION_CONFLICT/PARENT_COMMAND_FAILED/RETRY y `BudgetHydration` con outbox bloqueado (BUD1-12).
- `EvaluateBudgetAlerts` con `notify` que lanza o se cancela tras `tryRecord` (BUD1-04): la alerta debe poder emitirse otra vez.
- `BudgetCenterAlertNotifier` (ningún test: dedupe por id, no publicar en el sistema si ya existía, owner distinto).
- `OfflineFirstBudgetRepository.delete` de un presupuesto creado offline y aún no sincronizado (borra el UPSERT pendiente y encola `DELETE_BUDGET`): no hay test.
- `BudgetTotalsCard` (BUD1-05): sin test de la suma con alcances solapados.
- `RoomBudgetCategoryLookup.idsIncludingDescendants` solo se prueba indirectamente en `InactiveCategoryBudgetConsumptionTest`; falta un caso con categoría de sistema (`user_id` null) y árbol de 2 niveles.

## Lo que no revisé
- Todos los archivos del alcance en main (47) y los 23 tests JVM se leyeron completos. De androidTest leí solo `BudgetScreensTest.kt` parcialmente (líneas 60-160 y 280-345), los nombres de test de los demás y el arranque de `BudgetTombstoneReplayIntegrationTest.kt`; no audité en detalle `BudgetCrudFinancialIsolationIntegrationTest`, `BudgetHierarchyAlertsIntegrationTest`, `OfflineFirstBudgetRepositoryRoomTest`, `BudgetOfflineRestartIntegrationTest`.
- Fuera de alcance, leídos solo para contrato: `MainActivity` (disparadores de sync/mantenimiento), `CategoryDao.getCategoriesForUser`, `20261024120000_ep_pre_budgets.sql` (`delete_budget_v1`, guarda de cupo/tombstone). No leí `ExpenseConsumptionRepository`/`queryExpenseConsumption` (MOV1) ni `FinancialAlertSystemNotifier`.

## Dudas
- ¿"Presupuesto total / Usado" debe sumar todos los presupuestos o solo el de alcance Total? (BUD1-05)
- ¿Un cambio de zona horaria del dispositivo (viaje) debe reinterpretar los periodos ya creados? Hoy las fechas civiles se convierten con la zona actual del dispositivo, y dos dispositivos en zonas distintas calculan consumos distintos del mismo periodo.
- ¿Una alerta ya emitida debe poder repetirse si el consumo baja (reembolso, subir el límite) y vuelve a cruzar el umbral dentro del mismo periodo? Hoy no (una por periodo y umbral).
- ¿Debe haber una acción de usuario para resolver un presupuesto con `REVISION_CONFLICT`/cuota (reintentar, descartar lo local, quedarse con el servidor)? Hoy solo se muestra un texto. (BUD1-02)
- ¿Las notificaciones del sistema muestran el nombre del presupuesto en pantalla bloqueada? `BudgetCenterAlertNotifier` pasa el nombre al `FinancialAlertSystemNotifier` (privacidad, UI3/CAP1).
- ¿Es intencional que un `CUSTOM_RANGE` no admita cambio de calendario pendiente (cambiar de rango a recurrente), o debe aplicarse al terminar el rango? (BUD1-01)

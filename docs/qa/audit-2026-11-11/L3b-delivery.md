# L3b — política de outbox en módulos restantes

## Resultado y alcance

Se conservaron y revisaron los cambios parciales y se completaron presupuestos, categorías, recurrencias, deudas y perfil/preferencias reutilizando `core.sync.OutboxPolicy`. Sin cambios de servidor, esquemas ni migraciones; sin Spec Kit, adb, llamadas a Supabase, commits ni push. Las API de recuperación están en los puertos y repositorios; no hay UI nueva.

`retryFailed(userId, commandId)` reactiva errores transitorios y esperas de autenticación; recurrencias conserva el nombre de estado nativo RETRYABLE. Los fallos permanentes y conflictos requieren `discardLocal`; los estados históricos FAILED pueden descartarse. El descarte conserva trazabilidad como DISCARDED y libera los sucesores bloqueados cuando ya no queda otro fallo terminal del agregado. No compensa efectos financieros locales ni reescribe revisiones optimistas.

## Workers y schedulers

- **Categorías / CAT1-02:** el scheduler usa REPLACE. El worker reclama una intención por transacción, drena sin límite, clasifica HTTP y respuestas lógicas, elimina el límite de cinco reintentos y conserva errores de red en ERROR con backoff. IN_FLIGHT usa next_attempt_at como vencimiento de lease de 60 segundos, sin columna nueva. La finalización verifica el lease y bloquea únicamente sucesores del mismo tipo/id de agregado. La hidratación conserva todas las intenciones no resueltas. Payload ilegible y comando desconocido fallan permanentemente.
- **Recurrencias / REC1-01, REC1-03:** se mantiene el scheduler REPLACE y su rescate periódico existente; observeAll ya no programa trabajo. El worker drena sin el tope de diez, aparca 401 como WAITING_FOR_AUTH y aplica la clasificación común de HTTP. El DAO excluye predecesores activos por fecha/rowid del mismo recurrence_id, también en backoff, y considera DISCARDED resuelto. Conflictos/rechazos bloquean sucesores del mismo agregado; retry/discard permiten salir del bloqueo. Al descartar se libera el estado de la regla para hidrataciones posteriores (también con revisión igual); el rebase de revisiones optimistas mayores queda en L4.
- **Presupuestos / BUD1-02:** REPLACE ya existía y se conserva. El worker reclama y finaliza transaccionalmente; updated_at permite recuperar leases vencidos (60 segundos) y espaciar errores transitorios (30 segundos). Drena agregados independientes aunque uno falle. Los fallos permanentes y conflictos bloquean solo sucesores del mismo presupuesto por sequence; 401 espera autenticación. El descarte libera hidratación y comandos posteriores; la hidratación preserva intenciones pendientes, bloqueadas y errores hasta su resolución explícita. Se conservan verificaciones de tombstones y confirmaciones de escrituras duplicadas.
- **Deudas:** REPLACE se conserva; drenado sin tope, sesión válida para reanudar WAITING_FOR_AUTH, clasificación común, FIFO por created_at/rowid y leases existentes. La finalización terminal bloquea sucesores del mismo debt_id en su transacción; otros agregados siguen. Recuperación en repositorio vía DebtLocalDataSource. DebtOpeningViewModel conserva operationId/debtId por borrador; DebtSettlementViewModel conserva operationId/occurredAt; los identificadores se renuevan tras éxito/reset y permanecen tras error.
- **Perfil / UI2-01:** el scheduler existente sigue con REPLACE. El repositorio drena con claim transaccional y lease mediante updated_at, respeta FIFO del perfil del usuario, clasifica excepciones HTTP usando el statusCode tipado del SDK y aplica espera de sesión/errores permanentes. hydrateProfile adopta remoteRevision cuando hay CONFLICT, conserva el último valor local por campo, descarta intenciones conflictivas y crea una operación nueva con revisión fresca. El siguiente intento publica esos valores: último escritor gana. Una nueva intención no reutiliza una clave cuyo payload ya se intentó. El worker solicita retry mientras se completa esa resolución automática en lugar de terminar permanentemente ante conflicto.
- **Captura / CAP1-06:** se mantiene el fallback autorizado de tres intentos con esperas de 1 y 2 segundos, reevaluando el acceso en cada ingreso. Al agotarlos queda un aviso genérico sin contenido, título, paquete, propietario ni excepción. No se guarda texto adicional. Los stores actuales persisten candidatos ya procesados y no una señal mínima pendiente: los metadatos/fingerprint por sí solos no permiten reconstruir el análisis ni la resolución de instrumento/comercio; añadir una cola de señales completas ampliaría la persistencia de texto privado. El fallback solo vive mientras el servicio permanece activo; no cubre muerte del proceso antes de persistir. Los followups ya persistidos conservan su worker durable existente.

## DEB1-04: aislamiento y snapshot

DebtChangeFeedApplier captura StaleDebtRevision dentro del procesamiento de cada cambio. Conserva la proyección financiera local de esa deuda, la marca CONFLICT, bloquea su cola pendiente y guarda el snapshot remoto antes de avanzar. Los cambios posteriores de la misma deuda (incluidos eventos y cuotas) se guardan como evidencia pendiente, mientras otras deudas de la página se aplican normalmente. Snapshot, cambios independientes y checkpoint se confirman en una sola transacción. Las páginas inválidas conservan su rechazo transaccional, y la cancelación se propaga.

Sin migraciones: los snapshots son filas internas de debt_command_outbox con command_type=REMOTE_SNAPSHOT y state=CONFLICT, identificadas por entidad/id/revisión y acotadas al propietario. payload conserva entidad, operación, revisión y snapshot; nunca se envían como comandos ni participan como predecesores ejecutables. La fila de deuda sigue en CONFLICT y el resolvedor/rebase de esa evidencia pertenece a L4; no se introduce resolución financiera automática.

## Verificación

Pendiente de registrar el resultado de la compilación final. No se escribieron ni ejecutaron pruebas; únicamente se adaptaron dos fakes existentes a las nuevas consultas DAO para mantener la compilación, sin cambiar aserciones.

## Casos que habrá que probar al final

1. HTTP 401, 404, 408, 409, 425, 429, otros 4xx, 5xx y fallo de red en cada módulo; conflicto/rechazo lógico, payload ilegible y comando desconocido.
2. Más de diez comandos; FIFO con timestamps iguales y backoff; lease cancelado por REPLACE, vencimiento, idempotencia y finalización tardía de un intento anterior.
3. Un agregado falla y bloquea solo sus sucesores, incluso los creados después; agregados independientes drenan. Dos fallos del mismo agregado no se liberan al descartar solo uno.
4. retryFailed y discardLocal con usuario correcto/incorrecto, commandId inexistente y estados no permitidos; errores legacy FAILED, sesión ausente/restaurada y descarte que libera hidratación.
5. Recurrencias: abrir calendario/cambiar mes no programa ni cancela sync; conflicto/rechazo, descarte y nueva hidratación con revisión igual, además del caso separado de rebase pendiente de L4.
6. DEB1-04: deuda X en conflicto y Y nueva en una página; X conserva proyección y snapshot, Y converge y cursor avanza; cuotas/eventos posteriores de X se preservan en snapshots; reinicio/página repetida y aislamiento entre usuarios; página inválida revierte.
7. Alta y liquidación de deuda: dos fallos consecutivos conservan clave y campos temporales del borrador; éxito/reset genera otra identidad.
8. Perfil: conflicto rev1/rev2, hydrate adopta rev2 y nueva operación publica último valor por campo; nuevas ediciones concurrentes, operación en vuelo, HTTP 401/422/503, cierre de proceso entre conflicto y refresh y nuevo conflicto durante recuperación.
9. Captura: resolutor falla dos veces y se recupera sin segundo callback; tres fallos producen solo aviso genérico; revocación/cambio de propietario durante espera; cancelación al destruir servicio, deduplicación tras persistencia y ausencia de contenido en logs.

## L4 y L5

- **L4:** encadenamiento y orden por revisión confirmada, rebase después de descartar/conflicto y aplicación de snapshots remotos pendientes de deudas. El FIFO local de este lote no modifica expectedRevision ni reconstruye comandos financieros. Las revisiones optimistas locales superiores al servidor requieren esa reconciliación posterior.
- **L5:** conectar retry/discard a UI de No sincronizado y compensar altas rechazadas con efecto local. Descartar una intención de outbox no equivale a revertir una deuda, un presupuesto ni un movimiento vinculado. Se conservan estos efectos hasta implementar la compensación aprobada.

## Archivos por módulo

### budgets

- `app/src/main/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepository.kt`
- `app/src/main/java/com/kipu/app/feature/budgets/data/local/BudgetDao.kt`
- `app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt`
- `app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetPorts.kt`

### categories

- `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/local/CategoryDao.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/CategorySyncScheduler.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/categories/domain/CategoriesRepository.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepositoryTest.kt`

### recurrences

- `app/src/main/java/com/kipu/app/feature/recurrences/data/local/RecurrenceOutboxDao.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/data/sync/SyncRecurrenceCommandsWorker.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/domain/RecurrenceRepository.kt`

### debts

- `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtOutboxDao.kt`
- `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtChangeFeedApplier.kt`
- `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt`
- `app/src/main/java/com/kipu/app/feature/debts/domain/DebtRepository.kt`
- `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtOpeningViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtSettlementViewModel.kt`

### settings

- `app/src/main/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepository.kt`
- `app/src/main/java/com/kipu/app/feature/settings/data/local/ProfilePreferencesDao.kt`
- `app/src/main/java/com/kipu/app/feature/settings/data/sync/SyncProfilePreferencesWorker.kt`
- `app/src/main/java/com/kipu/app/feature/settings/domain/ProfilePreferencesRepository.kt`
- `app/src/test/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepositoryTest.kt`

### capture

- `app/src/main/java/com/kipu/app/feature/capture/data/listener/KipuNotificationListenerService.kt`


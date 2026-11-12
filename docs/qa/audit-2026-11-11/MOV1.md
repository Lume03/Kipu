# MOV1 — movements: domain + data/local + data/remote

Esta porción registra movimientos, reembolsos y correcciones, conserva asientos contables y comandos pendientes en Room, consulta el historial y envía contratos HTTP. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos locales y periodos frecuentes sin conexión. Auditoría estática en `main`; se aplicaron los criterios ponytail proporcionados, sin modificar implementación ni ejecutar pruebas.

## Hallazgos

### [MOV1-01] Anular una compra reembolsada deja un abono sin compra
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:756-763,981-987; app/src/main/java/com/kipu/app/feature/movements/data/local/TransactionLinkEntity.kt:71
- Qué está mal: Las comprobaciones de relaciones especializadas solo miran tarjeta, cuotas y `legacyKind`; ignoran los vínculos REFUND de la compra. El planificador revierte el gasto completo sin revisar los reembolsos de otras transacciones. Tampoco limita una reducción del importe al total ya reembolsado.
- Escenario concreto de fallo: Compra estándar confirmada de S/100 con asiento -100, reembolso válido de S/40 con asiento +40 y posterior anulación de la compra offline → se añade +100, queda saldo neto +40 y consumo cero, mientras el reembolso sigue vigente. Reducir esa compra a S/20 deja un saldo neto +20 y consumo cero por el recorte del consumo a cero.
- Fix mínimo: Consultar los vínculos de reembolso dentro de las transacciones de edición/anulación y rechazar cambios financieros incompatibles antes de escribir. Como solución conservadora, bloquear cambios de importe/cuenta y anulación mientras haya reembolsos vigentes; conservar cambios puramente descriptivos.
- Confianza: alta
- Cross-módulo: no

### [MOV1-02] Un gasto estándar sincronizado sigue sin admitir reembolso
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/domain/RegisterRefundTransaction.kt:105-108; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:454,1545-1547
- Qué está mal: El registro crea gastos estándar ACTIVE y ambas comprobaciones de elegibilidad exigen CONFIRMED o REVISED. ACTIVE también es la representación que el feed histórico usa para movimientos confirmados; SYNCED no cambia esa comprobación.
- Escenario concreto de fallo: Registrar gasto estándar, sincronizarlo correctamente y abrir su detalle → ACTIVE/SYNCED no ofrece reembolso y el caso de uso devuelve PURCHASE_NOT_ELIGIBLE. Un gasto igual pasado primero por edición sí queda REVISED y admite el reembolso. Los tests de reembolso siembran compras CONFIRMED y ocultan esa diferencia.
- Fix mínimo: Alinear ambas comprobaciones con la representación confirmada real: admitir ACTIVE cuando exista evidencia de aceptación, por ejemplo SYNCED, sin habilitar indiscriminadamente gastos pendientes. Añadir un caso Room de registro → aceptación → reembolso, sin sembrar a mano CONFIRMED.
- Confianza: alta
- Cross-módulo: no

### [MOV1-03] Rehacer una propuesta hidratada usa otro formato JSON
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:1450-1470; app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:414
- Qué está mal: El lector `redoProposal` espera un comando ReviseOrVoidTransactionRequestDto, pero el productor de propuestas durante la descarga guarda `rev.newPayload`, un snapshot de transacción. Es una incompatibilidad entre productor y consumidor; se remite el productor al dueño de MOV2 sin ampliar esta auditoría a su implementación.
- Escenario concreto de fallo: El dispositivo tiene una corrección local pendiente y descarga una revisión remota superior → se conserva una propuesta con snapshot; elegir rehacerla intenta decodificar campos de comando ausentes y devuelve INVALID_PROPOSAL_SNAPSHOT.
- Fix mínimo: Acordar un único formato persistido y reutilizar el decodificador de snapshot para los registros ya existentes. El test mínimo debe crear una propuesta con MovementRevisionSnapshotCodec.encode y pasarla a redoProposal.
- Confianza: alta
- Cross-módulo: sí → MOV2

### [MOV1-04] Un rechazo antiguo marca para siempre la operación vigente como fallida
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:1415-1417; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementRejectionResolver.kt:101-107,141-145
- Qué está mal: El rechazo de una corrección restaura el movimiento oficial con SYNCED, pero conserva el comando rechazado como FAILED_PERMANENT. `withOutboxStatus` convierte cualquier fallo histórico de ese movimiento en fallo de su estado actual, incluso después de otra corrección aceptada.
- Escenario concreto de fallo: Gasto confirmado de S/100, edición a S/90 rechazada y compensada → la fila vuelve a S/100/SYNCED; consultar por MovementLocalDataSource devuelve FAILED_PERMANENT por el intento antiguo. Una nueva edición exitosa sigue apareciendo fallida hasta descartar expresamente la evidencia vieja.
- Fix mínimo: Hacer que el estado derivado mire solo comandos que todavía afectan a la cabeza actual, excluyendo los ya compensados mediante evidencia de reconciliación. Añadir un caso de rechazo compensado seguido por lectura y nueva aceptación.
- Confianza: alta
- Cross-módulo: no

### [MOV1-05] Cada actualización del historial ejecuta una consulta por movimiento
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:34-39,286-287,1415-1417; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt:152-153
- Qué está mal: `withOutboxStatus` carga todos los comandos, incluidos sus payloads JSON, individualmente para cada fila. El flujo de historial completo repite ese trabajo en cada invalidación de transactions; no solo carga los movimientos visibles.
- Escenario concreto de fallo: Usuario con 5.000 movimientos registra o sincroniza uno → el observador completo vuelve a emitir y hace 5.000 consultas secuenciales adicionales, materializando también comandos antiguos. Una página de 200 filas añade otras 200 consultas a la consulta paginada.
- Fix mínimo: Resolver el estado necesario con EXISTS en la consulta original, o cargar en una sola consulta el conjunto de aggregate_id afectados y aplicarlo en memoria. No cargar payloads para decidir un estado.
- Confianza: alta
- Cross-módulo: no

### [MOV1-06] Filtrar solo por moneda evita la comprobación Premium
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/domain/model/MovementHistoryQuery.kt:49-60,83-84; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilder.kt:148-150
- Qué está mal: `hasCriteria` omite `currency`, aunque SQL sí aplica ese campo como criterio avanzado. La política permite la consulta como básica y no ejecuta el fallback que elimina los filtros avanzados.
- Escenario concreto de fallo: Usuario Free solicita MovementHistoryQuery(advancedCriteria = AdvancedHistoryCriteria(currency = "USD")) → recibe Allowed y solo movimientos USD, sin concesión Premium. Los tests de importes siempre añaden minAmountMinor y no detectan la consulta de moneda sola.
- Fix mínimo: Incluir `!currency.isNullOrBlank()` en hasCriteria. Añadir un test de QueryMovementHistory con solo moneda que compruebe PremiumRequired y fallback sin advancedCriteria.
- Confianza: alta
- Cross-módulo: no

### [MOV1-07] El filtro de fallos históricos siempre devuelve vacío
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilder.kt:52,156; app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementHistoryQueryTest.kt:424
- Qué está mal: El predicado inicial excluye FAILED incondicionalmente y el filtro LEGACY_FAILED añade status IN ('FAILED'). La conjunción es imposible. Ya existe un test que espera la fila FAILED: el código actual contradice esa aserción, comprobado por lectura, no por ejecución.
- Escenario concreto de fallo: Hay un movimiento legacy FAILED y el usuario consulta exclusivamente LEGACY_FAILED → lista y conteo vacíos en vez del movimiento. El mismo fallo ocurre al combinar ese estado con otros.
- Fix mínimo: Distinguir los tombstones de registros rechazados que deben ocultarse de los fallos históricos que el filtro admite; no usar FAILED como exclusión universal si el filtro explícito pide esos registros. Mantener el test existente como regresión.
- Confianza: alta
- Cross-módulo: no

### [MOV1-08] La edición permite añadir categoría a una transferencia
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/domain/MovementRevisionPlanner.kt:42-58
- Qué está mal: El registro prohíbe categorías en transferencias, pero el planificador de edición no. Una categoría GENERAL se convierte en referencia compatible con TRANSFER en collectReferences, así que supera también la comprobación de tipo. El contrato remoto rechaza esa combinación.
- Escenario concreto de fallo: Transferencia estándar válida entre A y B; se manda una revisión que conserva sus cuentas y añade una categoría GENERAL activa → la revisión local se acepta y se encola, pero la RPC devuelve INVALID_REFERENCE por categoría en transferencia. Queda una corrección que nunca podía ser aceptada.
- Fix mínimo: Rechazar after.type == TRANSFER con categoryId != null, igual que RegisterTransactionValidator. Añadir un test del planificador para transferencia con categoría GENERAL.
- Confianza: alta
- Cross-módulo: no

### [MOV1-09] Corregir un movimiento desactiva la advertencia de duplicado
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt:82-93; app/src/main/java/com/kipu/app/feature/movements/domain/RegisterTransaction.kt:23-35
- Qué está mal: La consulta de similitud solo admite ACTIVE. Movimientos CONFIRMED o REVISED tienen efecto financiero vigente, pero no se buscan. El fake de FreeDeduplicationTest filtra sin estado y por tanto no representa esta restricción del DAO.
- Escenario concreto de fallo: Registrar un gasto S/20 a las 10:00, corregir únicamente su nota y volver a registrarlo a las 10:01 con la misma cuenta/importe/moneda → la fila REVISED queda fuera y se registra otro gasto sin advertencia.
- Fix mínimo: Buscar los tres estados financieros vigentes y seguir excluyendo VOIDED y FAILED. Añadir un caso Room con REVISED que compruebe la advertencia del caso de uso.
- Confianza: alta
- Cross-módulo: no

### [MOV1-10] La prueba de límites ignora dependencias con nombre completo
- Severidad: baja
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/movements/MovementsFeatureBoundaryTest.kt:33,44-48; app/src/main/java/com/kipu/app/feature/movements/domain/MovementRepository.kt:47
- Qué está mal: La prueba solo mira líneas import, aunque el dominio ya referencia tipos data.local con su nombre completo. Además, si no encuentra las carpetas, aprueba mediante assertTrue(true).
- Escenario concreto de fallo: Añadir com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity a una firma de dominio sin import → la dependencia prohibida queda presente y el test pasa. Ejecutar desde una carpeta donde no resuelve ninguna ruta también produce un verde sin inspección.
- Fix mínimo: Fallar si no se encuentra la carpeta y detectar las referencias calificadas al paquete prohibido, además de imports. El test mínimo de la comprobación debe incluir una firma con nombre completo y exigir una violación.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- MovementLocalDataSource.commitRevisionAtomic/commitVoidAtomic → compra confirmada con reembolso vinculado seguida por reducción o anulación; verificar que no queda un abono huérfano (MOV1-01).
- RegisterRefundTransaction + elegibilidad local → gasto creado por el registro real y aceptado, todavía ACTIVE/SYNCED, admite devolución (MOV1-02).
- MovementLocalDataSource.redoProposal → propuesta con snapshot, no solamente JSON del comando (MOV1-03).
- MovementRejectionResolver + lectura local → rechazo compensado no marca fallido el movimiento oficial ni una revisión posterior aceptada (MOV1-04).
- QueryMovementHistory → filtro de moneda como único criterio avanzado (MOV1-06).
- MovementRevisionPlanner → transferencia con categoría GENERAL (MOV1-08).
- MovementDao.findSimilarTransactions → movimiento vigente REVISED produce advertencia (MOV1-09).

Los tests monetarios revisados sí verifican asientos exactos, rollback de escritura, reinicio Room, remanente de reembolsos, paginación y leases vencidos. No se infiere que hayan pasado: no se ejecutaron.

## Lo que no revisé

Leí completos los 32 archivos main, 18 test y 7 androidTest asignados, recuperando por tramos las salidas largas. Fuera del alcance solo consulté contratos y llamadores para fundamentar los escenarios; MOV1-03 es una referencia cross-módulo para MOV2. No revisé seguridad remota, migraciones completas, despliegue, rendimiento medido, dispositivos, compilación ni ejecución Gradle; no accedí a red ni a Supabase.

El árbol ya contenía `.agents/skills/ponytail/`, `.claude/` y `skills-lock.json` sin seguimiento antes de esta auditoría; no los modifiqué. Otros informes del mismo directorio pueden pertenecer a los demás workers.

## Dudas

- El contrato debería precisar la recuperación permitida de una compra que ya tiene reembolsos: bloqueo financiero conservador o compensación conjunta. La combinación actual de anulación completa y abono vigente de MOV1-01 es incoherente cualquiera que sea la opción elegida.
- FAILED sirve tanto para tombstones rechazados como para fallos históricos consultables; falta resolver esa distinción para conservar la intención de ocultar rechazos descartados y el filtro que exige el test de MOV1-07.

Balance: 0 críticas, 3 altas (una cross-módulo), 6 medias y 1 baja. Prioridad: integridad de compras reembolsadas, elegibilidad de gastos aceptados y formato de propuestas; no se incluyeron observaciones de estilo ni estimaciones de ahorro sin evidencia.

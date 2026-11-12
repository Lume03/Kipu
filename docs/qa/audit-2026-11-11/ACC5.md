# ACC5 — accounts: OfflineFirstFinancialInstrumentsRepository + data/sync

Esta porción guarda cuentas, tarjetas, aperturas, compras y pagos en Room y envía los comandos pendientes al servidor mediante WorkManager. También prepara la primera entrada al dashboard y expone saldos e historial. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y sesiones offline frecuentes.

Auditoría estática en `main`, sin red, builds, tests ejecutados ni dispositivos. Leí completos los tres archivos productivos y los siete archivos de tests asignados; las referencias externas al alcance se leyeron únicamente para comprobar contratos.

## Hallazgos

### [ACC5-01] La segunda corrección de apertura usa contratos distintos en móvil y servidor
- Severidad: crítica
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:306-375; supabase/migrations/20260921000000_ep_cta_baseline.sql:557-580
- Qué está mal: El móvil revierte la última apertura efectiva; el RPC versionado siempre revierte el importe de la apertura original. Es una incompatibilidad cross-módulo observada al seguir el contrato, no una auditoría adicional de SQL.
- Escenario concreto de fallo: Apertura S/100, corrección a S/200 y luego a S/300, ambas aceptadas: el móvil suma 100−100+200−200+300 = 300, mientras el RPC produce 100−100+200−100+300 = 400. El trigger versionado en `supabase/migrations/20261026013311_planning_remote_sync_activation.sql:39-63` publica esos hechos en el libro contable; no se verificó despliegue remoto.
- Fix mínimo: El dueño del RPC debe alinear la selección de la apertura efectiva y hacer idempotente el ajuste; añadir un caso con dos correcciones sucesivas antes de dar el contrato por compatible.
- Confianza: media
- Cross-módulo: sí → SQL1 (RPC); SQL4 (trigger de contabilización)

### [ACC5-02] Dos ediciones offline envían la misma revisión antigua
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:458-480; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:221-224,467-489
- Qué está mal: Se captura `remoteRevision` al guardar cada edición y el worker la reenvía sin actualizarla tras confirmar su predecesor. La corrección para revisión cero no cubre esta situación; solo la edición de identidad de débito tiene una actualización antes del primer envío.
- Escenario concreto de fallo: Cuenta en revisión 5; offline se cambia el alias a A y después a B. Ambos comandos esperan 5; el primero deja el servidor en 6, y el segundo falla por revisión aunque sea la siguiente edición del mismo dispositivo. Sucede también con dos cambios de condiciones de crédito.
- Fix mínimo: Antes del primer envío de un sucesor local, persistir la revisión confirmada de su predecesor en el payload, como hace la ruta de identidad de débito; conservar ese payload para reintentos y no sobrescribir conflictos reales de otro dispositivo. Test mínimo: dos ediciones offline sobre revisión 5 terminan sincronizadas en orden.
- Confianza: alta
- Cross-módulo: no

### [ACC5-03] Un rechazo bloquea todos los comandos posteriores del instrumento
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:1204-1212,1464-1472; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:269-280,769-776
- Qué está mal: Los comandos nuevos usan siempre el último comando como predecesor, incluso si terminó en rechazo o conflicto. El contrato de `InstrumentSyncDao.getPendingCommands` y `claimCommand` solo permite continuar si ese predecesor está `SYNCED`; el worker nunca resuelve la cadena fallida.
- Escenario concreto de fallo: Un pago es rechazado y compensado; el usuario registra después una compra válida con otra operación. Esta queda pendiente detrás del pago `FAILED_PERMANENT` y nunca se envía, por más reintentos que haga WorkManager.
- Fix mínimo: Resolver los sucesores al terminar un rechazo: rechazar explícitamente los que dependen del efecto fallido y permitir que las operaciones independientes posteriores partan del último estado confirmado. Test mínimo con DAO real: rechazo de un pago, nueva compra válida y nueva ejecución del worker.
- Confianza: alta
- Cross-módulo: no

### [ACC5-04] Un 401 convierte cambios de tarjeta en fallos permanentes
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:453-455,497-499
- Qué está mal: Las rutas de TEA personal y condiciones de crédito clasifican HTTP 401 como permanente. Las rutas generales y de compras/pagos sí lo excluyen. La API devuelve 401 cuando falta temporalmente la sesión autenticada o el servidor rechaza el token.
- Escenario concreto de fallo: Se cambian las condiciones offline; al reconectar, el primer envío recibe 401 con el propietario todavía activo. El comando queda `FAILED_PERMANENT`, el valor local sigue cambiado y renovar la sesión no provoca su reenvío; además bloquea sucesores por ACC5-03.
- Fix mínimo: Excluir 401 de la condición permanente en ambas rutas y mantener el comando reintentable. Test mínimo: 401 seguido de APPLIED conserva la operación y termina en SYNCED.
- Confianza: alta
- Cross-módulo: no

### [ACC5-05] Una creación rechazada conserva una cuenta y un saldo ficticios
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:197-253; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:269-280
- Qué está mal: Un rechazo definitivo de CREATE_ACCOUNT solo modifica la cola. No compensa el OPENING, no retira la cuenta local ni expone su fallo en el modelo Account. Los observadores continúan incluyendo la cuenta activa y su saldo.
- Escenario concreto de fallo: Usuario Free con tres instrumentos: dos dispositivos offline crean uno cada uno con S/100 de apertura. Cada dispositivo ve cuatro y acepta el alta; el servidor acepta una y rechaza la quinta por cupo. El dispositivo rechazado conserva su cuenta inexistente y S/100 adicionales; un pull no puede borrar una entidad que nunca existió remotamente.
- Fix mínimo: Compensar de forma atómica una creación definitivamente rechazada y representar su estado fallido; si ya hay movimientos dependientes, conservar evidencia y resolverlos sin mostrar ese saldo como confirmado. Test mínimo: rechazo FREE_LIMIT_REACHED tras crear una cuenta con apertura no deja saldo utilizable ni una cuenta activa normal.
- Confianza: alta
- Cross-módulo: no

### [ACC5-06] La compra rechazada permanece publicada en el historial de tarjeta
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:689-705; app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:1387-1404,1934-1943
- Qué está mal: El rechazo anula la transacción y las cuotas, pero deja POSTED el `financial_movements` creado para esa compra. El historial de tarjeta se alimenta de esa tabla y no consulta el estado anulado de la transacción.
- Escenario concreto de fallo: Una compra de S/100 recibe REJECTED. La deuda se compensa a cero, pero `observeMovementsByCard` continúa entregando la compra como POSTED. El test instrumentado comprueba cuotas y ledger, pero no esta proyección.
- Fix mínimo: Actualizar también el estado de la proyección de movimientos por operación dentro de la transacción de rechazo. Extender el test de rechazo para comprobar que la compra ya no se entrega como publicada.
- Confianza: alta
- Cross-módulo: no

### [ACC5-07] Fallar al programar sync devuelve error después de guardar
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:498-577,701-738,782-842
- Qué está mal: Varias escrituras incluyen `scheduleSync` en el mismo `runCatching` que devuelve el resultado, aunque Room ya hizo commit. Crear cuentas y corregir aperturas ya separan correctamente esas dos operaciones mediante `scheduleSyncBestEffort`, pero las altas de tarjetas y sus ediciones no.
- Escenario concreto de fallo: WorkManager lanza una excepción al programar una tarjeta recién guardada. El llamador recibe fallo y permite reintentar; la tarjeta y su comando ya existen, por lo que el reintento puede fallar por duplicado o registrar otra operación distinta. La UI informa que no se guardó algo que sí está guardado.
- Fix mínimo: Reutilizar `scheduleSyncBestEffort` después de un commit exitoso en las demás escrituras, propagando cancelación. Test mínimo equivalente al de OpeningAdjustmentRetryRoomTest, aplicado a registrar tarjeta.
- Confianza: alta
- Cross-módulo: no

### [ACC5-08] KEEP puede perder el aviso del último comando
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:67-70,125-126,151-153
- Qué está mal: El último conteo de pendientes y la finalización del worker no son atómicos con las escrituras del repositorio. `ExistingWorkPolicy.KEEP` descarta una programación si el worker anterior todavía está ejecutándose, aunque ya haya decidido devolver éxito.
- Escenario concreto de fallo: El worker lee cero pendientes y decide success; antes de terminar, el usuario guarda otro comando. Su scheduleSync encuentra el trabajo RUNNING y KEEP descarta el aviso. El trabajo termina y el comando queda sin ejecución hasta otra acción que vuelva a programarla.
- Fix mínimo: Usar una política que conserve una ejecución posterior, por ejemplo APPEND_OR_REPLACE, y probar la ventana entre el último conteo y la finalización. No basta con otro conteo sin cambiar la programación, porque desplaza la misma carrera.
- Confianza: alta
- Cross-módulo: no

### [ACC5-09] Borrar una tarjeta de crédito aún no enviada rompe su registro pendiente
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:980-1006; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:201-207
- Qué está mal: El borrado elimina inmediatamente la fila Card. Al procesar un REGISTER_CARD de crédito, el worker exige obtener su cuenta de pasivo de esa fila, aunque el DTO persistido ya lleva accountId.
- Escenario concreto de fallo: Registrar tarjeta de crédito offline y llamar DeleteUnusedCard antes del primer sync: el registro recibe el error local «Credit card liability account is missing» y queda permanente; el DELETE que lo sigue queda bloqueado. La API de borrado existe en dominio; no encontré un llamador de UI, por eso la severidad es media.
- Fix mínimo: Para una tarjeta creada y eliminada sin ningún envío, cancelar ambas operaciones y limpiar el pasivo que no tenga uso; cuando hubo envío, conservar el dato necesario para completar el protocolo. Test mínimo: registrar, borrar offline y sincronizar sin dejar una cadena pendiente permanente.
- Confianza: alta
- Cross-módulo: no

No se añadieron hallazgos de estilo ni estimaciones de ahorro sin una eliminación demostrable.

## Código de riesgo sin tests

- `updateAccountAppearance` / `updateCreditCardTerms` → dos ediciones offline consecutivas, confirmación del predecesor y revisión correcta del sucesor (ACC5-02).
- `doWork` → rechazo permanente seguido de un comando válido sobre el mismo instrumento, con InstrumentSyncDao real (ACC5-03). Los mocks relajados del test instrumentado no comprueban esta dependencia.
- `processPersonalTeaCommand` / `processCreditCardTermsCommand` → 401 recuperable seguido de aceptación (ACC5-04).
- `rejectCreditCommand` → comprobar la proyección financial_movements además de transacciones, cuotas y ledger (ACC5-06).
- `InstrumentSyncScheduler` → nueva escritura durante la finalización del worker, sin depender de otra entrada a la app (ACC5-08).
- `DashboardEntryPreloader.awaitInitialData` → integración con WorkManager que pruebe la selección KEEP/REPLACE y la espera de la ejecución recién encolada. Sus tests prueban principalmente el Flow auxiliar con WorkInfo simulados; no demuestran ese protocolo real.

## Lo que no revisé

Ningún archivo del alcance quedó sin leer completo: 3 productivos, 6 unitarios y 1 instrumentado. No ejecuté tests, builds, ADB, llamadas de red ni comandos Supabase. No audité DAOs, API, UI ni migraciones ajenas al reparto; los leí selectivamente para verificar los contratos citados. No se certifica el estado desplegado ni el comportamiento temporal real de WorkManager.

## Dudas

- ¿Qué resolución debe ofrecer el producto para un alta local con movimientos dependientes que el servidor rechaza definitivamente? No encontré en esta porción un estado visible de alta rechazada ni una acción para resolverla; esto no cambia la necesidad de dejar de presentar un saldo ficticio.
- ¿El RPC desplegado de ajuste de apertura sigue el contrato SQL versionado? La discrepancia ACC5-01 requiere coordinación con SQL1/SQL4 y verificación separada del despliegue; esta auditoría no hizo ninguna consulta remota.

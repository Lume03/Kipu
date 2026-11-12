# REC1 — recurrences + calendar (programación de fechas)

Esta porción guarda reglas de pago recurrente (offline-first: Room + outbox de comandos + sync contra RPC de Supabase), calcula fechas de la secuencia, genera "pagos esperados" locales, programa recordatorios con WorkManager y proyecta un calendario mensual de solo lectura (movimientos, tarjetas, deudas, recurrencias). Carga asumida: miles de usuarios con 1 o 2 dispositivos, pocas reglas por usuario (decenas como mucho), miles de movimientos por usuario, uso offline frecuente y zona de negocio America/Lima.

Auditoría estática: no se ejecutó ningún build ni test. Raíz Kotlin: `app/src/main/java/com/kipu/app/`.

## Hallazgos

### [REC1-01] Una regla en CONFLICT o FAILED queda bloqueada para siempre
- Severidad: alta
- Categoría: datos
- Ubicación: feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:244 (applyChangeFeed), :140-176 (setStatus), feature/recurrences/data/local/RecurrenceOutboxDao.kt:24-36 (getReady), feature/recurrences/presentation/RecurrenceLifecycleScreen.kt:65-73
- Qué está mal: la regla pasa a CONFLICT/FAILED (comando rechazado, 409, o servidor por delante) y no existe ninguna salida. El feed remoto no la pisa porque `applyChangeFeed` ignora filas con `syncState in (CONFLICT, FAILED)`. Los comandos nuevos se encolan pero `getReady` no envía ninguno mientras exista uno anterior de la misma regla que no esté APPLIED/DUPLICATE. La UI sigue mostrando Pausar/Reanudar/Cancelar y el texto "vuelve a intentarlo", pero no hay ningún reintento. `setStatus` tampoco mira `syncState`.
- Escenario concreto de fallo: el usuario tiene 2 dispositivos. A pausa la regla (rev 2 aplicada en servidor). B estaba offline en rev 1 y cancela; al reconectar envía `expected_revision=1`, el servidor responde REVISION_MISMATCH y B queda en CONFLICT. B nunca recibe la rev 2 del servidor. Si luego B pulsa "Cancelar" o "Reanudar", el cambio queda solo en B (el comando nuevo está bloqueado detrás del conflictivo) y A sigue con la regla activa. Además `reconcileReminder` exige `syncState == SYNCED`, así que B deja de recordar esa regla.
- Fix mínimo: en `applyChangeFeed` aceptar el remoto cuando `current.syncState` es CONFLICT/FAILED y `remote.revision >= current.revision` (descartando de paso los comandos CONFLICT/FAILED de esa regla), es decir, "gana el servidor" y se vuelve a SYNCED. Alternativa mínima: ocultar los botones y mostrar un botón "Usar la versión del servidor" que haga lo mismo.
- Confianza: alta
- Cross-módulo: no

### [REC1-02] El recordatorio de una regla solo se dispara una vez en su vida
- Severidad: alta
- Categoría: bug
- Ubicación: feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:323-341 (reconcileReminder), feature/recurrences/data/sync/RecurrenceReminderWorker.kt:54-58, feature/recurrences/data/sync/RecurrenceReminderScheduler.kt:54-70
- Qué está mal: nada avanza `next_due_date` (se grepeó app y `supabase/migrations`: solo lo escriben create, update y la reanudación). El recordatorio se programa solo para `nextDueDate`, y el worker se descarta si `recurrence.nextDueDate != dueDate`. Cuando pasa esa fecha no se programa la siguiente ocurrencia. En cambio `GenerateExpectedPayments` sí calcula la ocurrencia futura con `firstOccurrenceAfter`, y el calendario también proyecta ocurrencias posteriores. Es decir, el calendario y los pagos esperados muestran el mes siguiente pero no hay aviso.
- Escenario concreto de fallo: regla mensual creada el 1-nov con `nextDueDate=2026-11-01` y aviso de 2 días. El 30-oct llega el aviso. El 1-dic, 1-ene, etc. no llega ninguno, aunque el calendario y el pago esperado sí muestran esas fechas. La app no tiene pantalla de editar (ver REC1-15), así que el usuario solo puede reiniciar el ciclo con pausar + reanudar.
- Fix mínimo: en `reconcileReminder` usar `firstOccurrenceAfter(start, freq, interval, unit, hoy_Lima - 1)` (igual que `GenerateExpectedPayments`), validar en el worker con `isOccurrence` en vez de igualdad con `nextDueDate`, y que el worker reprograme la siguiente ocurrencia al terminar. Si la intención de producto es otra, ver Dudas.
- Confianza: media (el código es claro; falta confirmar la intención de producto)
- Cross-módulo: no

### [REC1-03] Leer la lista de recurrencias reinicia el trabajo de sincronización
- Severidad: media
- Categoría: concurrencia
- Ubicación: feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:57-60 y :257-265, feature/recurrences/data/sync/RecurrencesSyncScheduler.kt:37-42
- Qué está mal: `observeAll()` es una lectura pero llama a `scheduleSync`, que hace `enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE)`. REPLACE cancela el worker que esté corriendo y lo vuelve a encolar. Se invoca desde `ObserveCalendarMonth` (cada vez que se abre o se cambia de mes), el ViewModel de recurrencias y `ExpectedPaymentWorker` (cada 6 h).
- Escenario concreto de fallo: hay 1 comando pendiente y el usuario abre el calendario y pasa 5 meses seguidos. Cada cambio de mes cancela el worker a mitad de la llamada HTTP y lo reinicia. El comando queda LEASED 60 s sin recibo (se reenvía luego, el servidor responde DUPLICATE, así que no se pierden datos), pero la sincronización se retrasa y se multiplican las llamadas.
- Fix mínimo: quitar `scheduleSync` de `observeAll` (ya se llama tras cada escritura y existe el periodic de rescate) o usar `ExistingWorkPolicy.KEEP`.
- Confianza: alta
- Cross-módulo: no

### [REC1-04] Pagos esperados de reglas pausadas, canceladas o editadas siguen casando con capturas
- Severidad: media
- Categoría: bug
- Ubicación: feature/recurrences/domain/ExpectedPayments.kt:24-36, feature/recurrences/data/local/ExpectedPaymentEntity.kt:56-67 y :85-87
- Qué está mal: `GenerateExpectedPayments` solo inserta filas EXPECTED; si la regla no está ACTIVE solo sincroniza importe/moneda y sale. Nunca marca SKIPPED (el estado existe pero ningún código lo escribe) ni borra filas viejas. `findExpectedByAmount` filtra por status, importe, moneda y ±3 días, sin mirar el estado de la regla.
- Escenario concreto de fallo: (a) regla de S/ 100 con EXPECTED el día 12; el usuario la pausa el día 10; el día 11 llega una captura de S/ 100 sin relación y `MatchCapturedMovementToExpected` la enlaza a esa regla pausada. (b) Se cambia `nextDueDate` de 15 a 20 (por pausa + reanudar): quedan dos filas EXPECTED, y un pago del 18 casa con ambas y fuerza un PENDING_CHOICE innecesario.
- Fix mínimo: en `GenerateExpectedPayments`, antes de insertar, un `UPDATE ... SET status='SKIPPED' WHERE recurrence_id=:id AND status='EXPECTED' AND (:inactive OR due_date <> :due)` (una query en el DAO) y que el generador se invoque también con la regla pausada o cancelada.
- Confianza: alta
- Cross-módulo: parcial (el matching vive en capture → CAP1; el origen de las filas es de esta porción)

### [REC1-05] `isOccurrence` falla para anuales ancladas al 29 de febrero
- Severidad: media
- Categoría: bug
- Ubicación: feature/recurrences/domain/schedule/RecurrenceScheduleCalculator.kt:31-42 y :58-66 (línea 64, `ChronoUnit.YEARS.between`)
- Qué está mal: `estimateIndex` usa `ChronoUnit.YEARS.between(2024-02-29, 2026-02-28)` = 1, pero la ocurrencia del 28-feb-2026 es la de índice 2 (se recorta el día). `isOccurrence` solo mira desde el índice estimado hacia atrás y devuelve false. El test existente cubre `occurrenceAt` para este caso, pero no `isOccurrence`.
- Escenario concreto de fallo: regla ANUAL con `startDate=2024-02-29`. En febrero de 2027 el calendario no muestra el 28-feb (`fromRecurrences` usa `isOccurrence`). Además `create`/`update` rechazan ese 28-feb como "Next due date is not on the schedule" aunque `firstOccurrenceAfter` y la reanudación sí la consideran válida. El servidor (`_rec_schedule_valid`) no se revisó en esta porción.
- Fix mínimo: en `estimateIndex` para Years, `(date.year - anchor.year) / amount` (sin la regla de día) y dejar que el bucle de `isOccurrence` corrija; añadir también el paso hacia adelante (un `index+1` de comprobación).
- Confianza: alta
- Cross-módulo: no

### [REC1-06] `update` y `setStatus` no protegen el trabajo posterior al commit
- Severidad: media
- Categoría: bug
- Ubicación: feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:127-133 y :176-185 (comparar con :83-89 en create)
- Qué está mal: `create` envuelve `reconcileReminder` y `generateExpected` en try/catch porque la regla ya está guardada (hay un doc QA que lo menciona). `setStatus` y `update` no. Si lanzan después del commit, la excepción sube y el ViewModel muestra "No se pudo pausar..." con la regla ya cambiada. Si lo que lanza es una `IllegalArgumentException` (por ejemplo `firstOccurrenceAfter` por desbordar fechas), el `catch` final la convierte en `Rejected("VALIDATION_FAILED")`.
- Escenario concreto de fallo: el usuario pulsa Pausar, la fila ya está PAUSED con su comando en el outbox, pero `reminderScheduler.cancel` o WorkManager lanzan una excepción. La pantalla dice que falló; el usuario reintenta y recibe "Conflicto de revisión: se esperaba N, versión actual N+1".
- Fix mínimo: mover `reconcileReminder` + `generateExpected` de ambos métodos al mismo helper protegido que usa `create`.
- Confianza: alta
- Cross-módulo: no

### [REC1-07] El mismo recordatorio puede notificarse varias veces
- Severidad: media
- Categoría: bug
- Ubicación: feature/recurrences/data/sync/RecurrenceReminderScheduler.kt:57-70, feature/recurrences/data/sync/RecurrenceReminderWorker.kt:93-106, feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:192-231 y :254
- Qué está mal: cada `reconcileReminder` re-encola con REPLACE. Si la hora de disparo (fecha - lead, 00:00 Lima) ya pasó, el delay es 0 y el worker corre de inmediato. El worker no comprueba si ya notificó esa fecha (`markDelivered` solo escribe) y publica con prioridad DEFAULT sin `setOnlyAlertOnce`. Se reconcilia tras cada recibo y por cada cambio del feed (incluida una edición hecha en el otro dispositivo).
- Escenario concreto de fallo: aviso de 3 días, vencimiento el día 20. El día 17 ya sonó. El día 18 el usuario pausa y reanuda en su otro teléfono; este dispositivo recibe el cambio del feed y vuelve a mostrar y sonar la notificación aunque el usuario ya la había descartado. El test androidTest solo comprueba que quede 1 notificación activa, no que no vuelva a sonar.
- Fix mínimo: en el worker, saltar si el `LocalReminderEventEntity` de ese `id` determinista ya existe, o en el scheduler usar KEEP cuando la fecha y el lead no cambiaron.
- Confianza: media
- Cross-módulo: parcial (tablas de eventos de notificación en UI3)

### [REC1-08] Una página de feed "rara" bloquea la sincronización de recurrencias para siempre
- Severidad: media
- Categoría: escala
- Ubicación: feature/recurrences/data/sync/RecurrenceHydration.kt:24-43 y :45-66 (`require(operation == "UPSERT")`, `RecurrenceStatus.valueOf`)
- Qué está mal: cualquier `IllegalArgumentException` al convertir un cambio (operación distinta de UPSERT, estado nuevo en el enum, campo ausente) escapa de `pullChanges`, el worker hace `Result.retry()` y el checkpoint no avanza, así que se reintenta la misma página eternamente (backoff, pero sin límite).
- Escenario concreto de fallo: se despliega una versión de servidor que añade un estado o una operación DELETE para recurrencias. Todos los clientes con la versión anterior dejan de sincronizar recurrencias (la fase de envío de comandos va antes del pull, pero el worker nunca llega a `Success`).
- Fix mínimo: tratar el cambio ilegible como "saltar y avanzar el checkpoint" (log con SecureLog), o convertir la excepción en `false` sin reintento infinito.
- Confianza: media (hoy el servidor solo emite UPSERT; es riesgo de despliegue)
- Cross-módulo: no

### [REC1-09] El recordatorio se dispara a las 00:00 de Lima, con sonido
- Severidad: baja
- Categoría: bug
- Ubicación: feature/recurrences/data/sync/RecurrenceReminderScheduler.kt:55-56 y feature/recurrences/data/sync/RecurrenceReminderWorker.kt:113-118
- Qué está mal: `fireAt = (dueDate - lead).atStartOfDay(Lima)`. El canal es IMPORTANCE_DEFAULT (suena). El test `reminderDelayUsesLimaMidnightAndInjectedClock` fija ese comportamiento como correcto.
- Escenario concreto de fallo: aviso de 2 días para un vencimiento del 10: suena a las 00:00 del día 8.
- Fix mínimo: sumar una hora fija de aviso (p. ej. 09:00 Lima) a `fireAt`; ver Dudas por si es decisión de producto.
- Confianza: media
- Cross-módulo: no

### [REC1-10] El concepto con comillas se ve con barras en "Mis recurrencias"
- Severidad: baja
- Categoría: bug
- Ubicación: feature/recurrences/presentation/RecurrenceLifecycleScreen.kt:59
- Qué está mal: `templateTransaction["concept"]?.toString()?.trim('"')` serializa el JSON (escapa `"` y `\`) en vez de leer el contenido.
- Escenario concreto de fallo: concepto `Pago "Luz"` se muestra `Pago \"Luz\"`; uno que termine en comilla pierde ese carácter.
- Fix mínimo: `(rule.templateTransaction["concept"] as? JsonPrimitive)?.content ?: "Obligación"`.
- Confianza: alta
- Cross-módulo: no

### [REC1-11] El calendario congela "hoy" y usa la zona del dispositivo; el resto usa Lima
- Severidad: baja
- Categoría: bug
- Ubicación: feature/calendar/presentation/CalendarViewModel.kt:52-53, feature/calendar/domain/ObserveCalendarMonth.kt:39
- Qué está mal: `today` se calcula una vez al crear el ViewModel y la zona es `ZoneId.systemDefault()`. Pagos esperados, recordatorios y registro usan `LIMA_BUSINESS_ZONE`.
- Escenario concreto de fallo: se abre el calendario a las 23:50 y la app queda en segundo plano; al día siguiente "hoy" y los estados "Vencido" siguen en el día anterior. Un usuario con el teléfono en otra zona (viaje) ve movimientos agrupados por otro día que los avisos.
- Fix mínimo: derivar `today` de un flujo con el reloj en cada resubscripción (`stateIn` con `WhileSubscribed` ya lo permite) y usar `LIMA_BUSINESS_ZONE` como en el resto.
- Confianza: media
- Cross-módulo: no

### [REC1-12] Un test instrumentado aserta un texto que la pantalla ya no muestra
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceLifecycleFlowTest.kt:59-65
- Qué está mal: `staleOfflineConflictAndRevisionAreVisible` busca `"Estado: ACTIVE · Próxima fecha: ... · Revisión 4"`. `RecurrenceLifecycleScreen` solo pinta `recurrenceStateLabel` ("Activa · próximo ..."); en `feature/recurrences` no existe la cadena "Estado:" ni "Revisión N" en la UI (grep). El test no puede pasar.
- Escenario concreto de fallo: la próxima ejecución de androidTest falla en este caso (o se ignora y se pierde la confianza en la suite).
- Fix mínimo: cambiar la aserción al texto actual (`recurrenceStateLabel(...)`) o borrarla; el resto del test (tag de conflicto) sí es válido.
- Confianza: alta (estático; no se ejecutó)
- Cross-módulo: no

### [REC1-13] Test que verifica su propio fake
- Severidad: baja
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/recurrences/data/RecurrenceDatesAndRemindersTest.kt:89-102
- Qué está mal: `freeCaptureGateDoesNotSuppressExpectedPaymentOrReminder` crea un `FakeCaptureAccessGate`, lo evalúa él mismo y asserta que devolvió lo que se le configuró, con `evaluations == 1` (la llamada que acaba de hacer). El gate no se pasa a `GenerateExpectedPayments` (que ni lo recibe) ni al worker; las otras aserciones ya existen en `serverConfirmedActiveRuleCreatesReminderEvent`.
- Escenario concreto de fallo: si alguien añade el gate al camino de recordatorios, el test sigue verde.
- Fix mínimo: quitar las líneas del fake (91, 92, 101) o inyectar el gate en el worker y comprobar que sigue publicando.
- Confianza: alta
- Cross-módulo: no

### [REC1-14] Sin test para el feed que pisa una regla local (conflicto "servidor por delante")
- Severidad: media
- Categoría: test-faltante
- Ubicación: feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:233-255
- Qué está mal: `RecurrenceSyncTest` cubre crear, recibos y un feed solo para una regla nueva. Ninguna prueba cubre `applyChangeFeed` con una regla existente (remoto > local sin pendientes, remoto > local con pendientes, remoto con CONFLICT local), que es donde viven REC1-01 y REC1-02.
- Escenario concreto de fallo: cualquier cambio en esa rama (p. ej. el arreglo de REC1-01) no tiene red de seguridad.
- Fix mínimo: un test: regla local rev 1 SYNCED, feed trae rev 2 → fila rev 2 SYNCED; mismo feed con un comando pendiente → CONFLICT/`REMOTE_REVISION_AHEAD`.
- Confianza: alta
- Cross-módulo: no

### [REC1-15] Código de edición de recurrencias sin ningún llamador
- Severidad: baja
- Categoría: borrar
- Ubicación: feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:95-138 y :301-307; feature/recurrences/domain/RecurrenceRepository.kt:13 y :41-50; feature/recurrences/data/local/RecurrenceDao.kt:26-42 y :57-58 (`applyRemoteState`); feature/recurrences/domain/model/RecurrenceRule.kt:31 (`Synced`); feature/recurrences/data/sync/SyncRecurrenceCommandsWorker.kt:110 (`MAX_CHANGE_PAGES`)
- Qué está mal: `repository.update` y `RecurrenceUpdate` solo se usan en tests (grep en `app/src`); ningún ViewModel ni pantalla edita una regla. `applyRemoteState`, `RecurrenceCommandResult.Synced` y `MAX_CHANGE_PAGES` no tienen usos fuera de definiciones y un `when`. `ExpectedPaymentStatus.SKIPPED` tampoco se escribe nunca (ver REC1-04).
- Escenario concreto de fallo: no rompe nada; son rutas sin cobertura de uso real que hay que mantener (la de `update` además duplica validaciones de `validateDraft`).
- Fix mínimo: borrar lo muerto, o conectar una pantalla de editar (que además resolvería la imposibilidad de mover `nextDueDate` de REC1-02). Decidir con producto antes de borrar `update`, porque el contrato servidor `UPDATE_RECURRENCE` existe.
- Confianza: alta
- Ahorro estimado: ~85 líneas
- Cross-módulo: no

### [REC1-16] Rama de "deuda no disponible" inalcanzable desde el caso de uso
- Severidad: baja
- Categoría: borrar
- Ubicación: feature/calendar/domain/CalendarEventProjector.kt:98-118
- Qué está mal: `ObserveCalendarMonth.debtsWithInstallments` solo observa cuotas de deudas ACTIVE que están en `debts`, así que `debtsById[debtId]` nunca es null en producción; solo el test unitario la ejercita.
- Escenario concreto de fallo: ninguno; código y chips (`isSourceAvailable`, `UnavailableChip`, aviso del sheet) que nunca se ven.
- Fix mínimo: o se borra la rama, o se pasa también el mapa de cuotas de deudas no activas si el producto quiere ese caso.
- Confianza: baja (puede ser deliberado por la especificación HU-40)
- Ahorro estimado: ~35 líneas (con la UI asociada)
- Cross-módulo: no

Menores omitidos: 2 (`ExpectedPaymentWorker` no aísla errores por regla, un `Json.parse` corrupto aborta el resto; `syncExpectedTemplate` reescribe `updated_at` en cada pasada de 6 h y reemite el flow de pagos esperados).

## Código de riesgo sin tests
- `OfflineFirstRecurrenceRepository.applyChangeFeed` (rama regla existente): feed con rev mayor, con y sin comando pendiente (REC1-14).
- `RecurrenceScheduleCalculator.isOccurrence` con ancla 29-feb anual: candidato 28-feb de un año no bisiesto debe ser true (REC1-05).
- `GenerateExpectedPayments`: `nextDueDate` pasada con recálculo, `endDate` anterior al día, regla pausada (no debe dejar filas EXPECTED vivas, REC1-04).
- `MatchCapturedMovementToExpected` (varios caminos: 0, 1 y más de 1 coincidencia, `chooseNone`): sin test en el alcance.
- `OfflineFirstRecurrenceRepository.update`: sin ningún test de camino feliz ni de conflicto (si se conserva, REC1-15).
- `reconcileReminder`: no hay test de que reprograme tras el recibo ni de que no re-notifique (REC1-02, REC1-07).
- `RecurrenceHydration.pullChanges`: página con operación o estado desconocido (REC1-08).

## Lo que no revisé
- Leí completos los 28 archivos main y los 10 tests unitarios del alcance. Los 5 androidTest los leí enteros pero no se ejecutaron (auditoría estática).
- No audité fuera del alcance: `ExpectedPaymentStore` (RoomCaptureStores), `ReminderEventReconciler`, `QueryMovementHistory`, `CreditCalculations`, RPC `create/update/set_recurrence_status` y `_rec_schedule_valid` en `supabase/`. Solo los leí para entender contratos.
- No comprobé el comportamiento real del servidor ante `expected_revision` desfasado más allá de lo que implica el código cliente.

## Dudas
- ¿Es intencional que `next_due_date` no avance nunca solo (ni cliente ni servidor)? Si sí, ¿cómo debe seguir avisando una regla mensual después del primer vencimiento? (REC1-02)
- ¿La hora de aviso debe ser 00:00 Lima o una hora del día? El test la fija en medianoche. (REC1-09)
- Los recordatorios exigen `syncState == SYNCED`; una regla creada sin conexión no avisa hasta sincronizar. ¿Es la decisión final (contrato `recurrence-notification-boundary.md`) aun cuando el vencimiento cae mientras se está offline?
- El calendario muestra `principalMinor` completo para cuotas en estado PARTIAL (el modelo `DebtScheduleItem` no trae el importe pendiente). ¿Es suficiente o debe mostrar el saldo de la cuota? (cross-módulo DEB1)
- El calendario ignora `accessDecision` de `QueryMovementHistory` (NotAuthorized o fallback premium se pintan como "Sin eventos este mes"). ¿Está bien dado que el historial básico es gratis?
- ¿Se espera una pantalla para editar recurrencias? (REC1-15)

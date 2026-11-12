# DEB2 — debts/presentation

Porción: capa de presentación de deudas (23 archivos main, 6 tests unitarios, 8 androidTest): lista, detalle, liquidación (pago/cobro), cronograma y cierre, edición, alta, vencimientos próximos. Asumí miles de usuarios con uno o dos dispositivos, pocas deudas por usuario (límite Free: 2 activas) pero con uso offline y sincronización en segundo plano; lo que importa aquí es la corrección de lo que ve y ordena el usuario sobre dinero, más la robustez de los ViewModels.

Rutas base: `app/src/main/java/com/kipu/app/feature/debts/presentation/` (abreviado `P/`), tests en `app/src/test/.../debts/presentation/` y `app/src/androidTest/.../debts/presentation/`.

## Hallazgos

Resumen: 1 alta, 9 media, 9 baja. Nada crítico. No dejé hallazgos menores fuera.

### [DEB2-01] El selector "Reducir / Aumentar" del ajuste no cambia el signo ya escrito
- Severidad: alta
- Categoría: bug
- Ubicación: P/DebtScheduleScreen.kt:828, 1071-1102; P/DebtScheduleViewModel.kt:94, 169-174, 229-234
- Qué está mal: el signo vive en dos sitios. `isReduce` es un `remember` local de la pantalla; el signo real viaja dentro del texto `state.adjustmentAmount` (`"-$it"` solo cuando se escribe, línea 1102). Al pulsar el otro botón de la píldora (líneas 1075/1088) solo cambia `isReduce`; el texto del ViewModel conserva el signo antiguo y el campo lo oculta con `removePrefix("-")` (línea 1101).
- Escenario concreto de fallo: deuda de S/ 100. El usuario deja "- Reducir" (por defecto), escribe 10 (el estado queda `-10`), cambia a "+ Aumentar" y pulsa "Aplicar ajuste de saldo". La UI muestra "Aumentar 10", pero `closeDebt(ADJUST)` parsea `-10` y el saldo baja a S/ 90. Es un movimiento de principal en dirección contraria a la elegida, y queda en el historial auditable.
- Fix mínimo: al cambiar el selector, reescribir el signo: `onAdjustmentAmountChange(if (reduce) "-$digits" else digits)` con `digits = state.adjustmentAmount.removePrefix("-").removePrefix("+")`. Mejor aún: no guardar el signo en el texto y pasar `isReduce` a `closeDebt`.
- Confianza: alta
- Cross-módulo: no

### [DEB2-02] La pestaña de cierre inventa datos cuando el saldo es cero
- Severidad: media
- Categoría: bug
- Ubicación: P/DebtScheduleScreen.kt:1188-1233 (en concreto 1210, 1215, 1227), 929-934, 1182
- Qué está mal: con `remainingPrincipalMinor == 0` la sección "2. Condonación" siempre muestra "S/ 0.00 exonerados", la fecha de apertura de la deuda como si fuera la fecha de condonación y el motivo `debt.notes ?: "Acuerdo mutuo de exoneración comercial"`. También pone "Completada con éxito" y "100% amortizado". Nada de eso sale de los eventos reales (hay `DebtSettlementActivity` con `FORGIVENESS`, que la pantalla ni recibe).
- Escenario concreto de fallo: una deuda pagada íntegramente con pagos normales y sin ninguna condonación. Al abrir "Ajustes y cierre" el usuario lee una condonación completada con un "acuerdo mutuo de exoneración comercial" que nunca existió. Es un texto de auditoría falso en una pantalla que se vende como constancia contable.
- Fix mínimo: borrar el bloque `if (isZeroBalance) { Surface(...) }` de la sección 2 (líneas 1193-1233) y el subtítulo "Completada con éxito"; si se quiere mostrar la condonación real, pasar `closure` (ya existe `DebtClosureSummary`) en lugar de literales.
- Ahorro estimado: 40 líneas
- Confianza: alta
- Cross-módulo: no

### [DEB2-03] La vista previa de cuotas no coincide con lo que se guarda
- Severidad: media
- Categoría: bug
- Ubicación: P/DebtScheduleScreen.kt:1546-1567 (1553), 640-642, 669-677, 608; domain/usecase/SetDebtSchedule.kt:35-45; P/DebtScheduleViewModel.kt:108
- Qué está mal: tres diferencias entre `calculateProjections` (solo UI) y `SetDebtSchedule.plan` (lo que se persiste): (a) la UI suma todo el resto de céntimos a la cuota 1 (`base + remainder`), el plan reparte 1 céntimo a cada una de las primeras `remainder` cuotas; (b) la UI numera siempre 1..n, el ViewModel numera desde `max(installmentNumber)+1` (línea 108); (c) la etiqueta dice "Mensual (cada 30 días)" pero ambos usan `plusMonths`.
- Escenario concreto de fallo: saldo S/ 100.01 en 3 cuotas. Previsualiza 33.35 / 33.33 / 33.33; se guarda 33.34 / 33.34 / 33.33. Además, si había un cronograma cancelado con 3 cuotas, el usuario ve "Cuota 1, 2, 3" y después el detalle muestra "Cuota 4 de 3...". El texto "exactos" de la línea 724 afirma una suma que la UI no calcula con la misma regla.
- Fix mínimo: borrar `calculateProjections` y llamar a `SetDebtSchedule().plan(...)` desde la UI (o exponer la lista desde el ViewModel) para pintar la vista previa; cambiar la etiqueta a "Mensual".
- Ahorro estimado: 25 líneas
- Confianza: alta
- Cross-módulo: no

### [DEB2-04] Editar una deuda pierde lo que el usuario está escribiendo cuando llega un cambio de Room
- Severidad: media
- Categoría: bug
- Ubicación: P/DebtEditViewModel.kt:48-66
- Qué está mal: `observeDebt` se recoge de forma continua y cada emisión hace `current.copy(counterpartyName = debt.counterpartyName, dueDate = ..., notes = ...)`. Cualquier cambio de la fila (sync que pasa `syncState` de PENDING a SYNCED, reconciliación de recordatorios, cambio desde otro dispositivo) pisa los campos editados.
- Escenario concreto de fallo: el usuario abre "Editar deuda" mientras hay una sincronización en segundo plano, escribe una nota larga; termina el sync, Room emite y el texto vuelve al valor guardado. Sin aviso. (El `revision` también se actualiza en silencio, por lo que un guardado posterior pasa aunque el contenido remoto haya cambiado.)
- Fix mínimo: cargar una sola vez (`repository.observeDebt(...).first()` o `firstOrNull` dentro del `launch`) y no volver a copiar campos editables; solo refrescar `revision` si se quiere detectar conflicto.
- Confianza: media (no medí con qué frecuencia emite `observeSummary`, pero la consulta incluye `syncState`)
- Cross-módulo: no

### [DEB2-05] Detalle, cronograma y hoja de pago ignoran el enmascarado de saldos
- Severidad: media
- Categoría: seguridad
- Ubicación: P/DebtDetailScreen.kt:344, 354, 358, 420, 483, 543, 550, 633-636; P/DebtScheduleScreen.kt:400, 511, 698, 724, 917, 1248-1263, 1282, 1302, 1480, 1525; P/DebtSettlementSheet.kt:89, 160, 167, 301, 325; P/PayableDebtFormScreen.kt:179
- Qué está mal: la app tiene un modo de ocultar importes (`LocalBalanceMasked`, provisto en `MainActivity.kt:310` y respetado por `MoneyText` y por la lista de deudas). Estas pantallas formatean con `NumberFormat.format(minor / 100.0)` o `debtMoney(...)`, que escriben el número sin consultar el local.
- Escenario concreto de fallo: el usuario activa "ocultar saldos" para usar la app en público. La lista de deudas muestra "••••", pero al tocar una deuda el detalle, el cronograma y la hoja de "Registrar pago" muestran saldo pendiente, monto original, cuotas e historial en claro.
- Fix mínimo: sustituir esos `formatter.format(x / 100.0)` por `MoneyText`/`formatMoneyTextDisplay` (ya usados en DebtListScreen y DebtHistorySheet) o envolver `debtMoney` con `if (LocalBalanceMasked.current) "••••"`. Elimina de paso tres rutas de formateo duplicadas y el paso por `Double`.
- Confianza: media (supuse que el enmascarado es una promesa de privacidad para todas las pantallas; está en `docs`/UX, no lo comprobé)
- Cross-módulo: no

### [DEB2-06] El interés de un cobro (Me deben) se registra con una categoría elegida al azar
- Severidad: media
- Categoría: bug
- Ubicación: P/DebtSettlementViewModel.kt:90-99, 109-113, 168-169, 176-188; P/DebtSettlementSheet.kt:80, 240
- Qué está mal: el ViewModel autoselecciona la primera categoría válida (`categories.firstOrNull()`) y, si hay interés, la envía siempre (`categoryId = selected.takeIf { interest > 0L }`). La hoja solo muestra el selector cuando es `payable` (`needsInterestCategory = payable && ...`), así que en cobros el usuario no la ve ni la puede cambiar. `DebtLocalDataSource.kt:338,371` persiste ese `categoryId` en el movimiento de interés.
- Escenario concreto de fallo: préstamo a un amigo, cobro con S/ 5 de interés. El ingreso queda categorizado como la primera categoría `INCOME/GENERAL` de la lista (por ejemplo "Sueldo"), que además puede variar entre dispositivos. Analytics y presupuestos suman ese ingreso donde no corresponde. Además las categorías sin presentación se llaman todas "Interés" (línea 96), indistinguibles.
- Fix mínimo: mostrar el selector también en cobros (quitar `payable &&` en la línea 80 y filtrar por `INCOME/GENERAL`), o enviar `null` en cobros si el dominio lo admite. Usar el nombre real de la categoría en lugar del literal "Interés".
- Confianza: media (no verifiqué qué hace DEB1 con `categoryId = null` en cobros)
- Cross-módulo: sí → DEB1 (decidir si el interés de cobros exige categoría)

### [DEB2-07] Una cuota PARCIAL se muestra con el monto planificado, no con lo que falta
- Severidad: media
- Categoría: bug
- Ubicación: P/DebtDetailScreen.kt:317, 410-421, 483; P/DebtListScreen.kt:575-583; P/DebtListViewModel.kt:76-87; el propio comentario en P/DebtDueAlerts.kt:55
- Qué está mal: la lista y el detalle usan `DebtScheduleItem.principalMinor` (monto planificado) para "Próxima cuota" y "Cuota S/ X". Cuando el estado es `PARTIAL` (`DebtLocalDataSource.kt:412`), ya se pagó una parte. La pantalla de vencimientos sí usa el saldo restante (`DebtDueBalance.remainingMinor`).
- Escenario concreto de fallo: cuota 1 de S/ 300; se paga S/ 100 de principal y pasa a PARTIAL. La lista y el detalle dicen "Cuota S/ 300.00 · vence en 3 días"; "Próximos vencimientos" muestra pendiente S/ 200.00 para la misma cuota. El usuario ve dos importes distintos para la misma obligación.
- Fix mínimo: en `DebtListViewModel`/`DebtDetailViewModel` mapear con el restante (`debtDao.getInstallmentRemaining` / `DebtDueBalance`) o, mínimo, no mostrar importe cuando `status == "PARTIAL"` y mostrar "Pago parcial".
- Confianza: media (no leí por completo la consulta de restante)
- Cross-módulo: no

### [DEB2-08] Cancelar cronograma, cerrar deuda y eliminar no capturan excepciones ni protegen doble toque
- Severidad: media
- Categoría: concurrencia
- Ubicación: P/DebtScheduleViewModel.kt:142-163 (`cancelSchedule`), 165-197 (`closeDebt`); P/DebtDetailViewModel.kt:158-175 (`delete`), 109-112
- Qué está mal: `saveSchedule` sí tiene `try/catch` y `AtomicBoolean`; sus tres hermanos no. Una excepción de Room o del programador de recordatorios dentro de `viewModelScope.launch` sin handler tumba la app. Sin guardia, dos toques en el mismo frame lanzan dos comandos con la misma `expectedRevision` (el segundo da "cambió en otro dispositivo" aunque el primero se aplicó). En `delete` además `errorMessage` nunca se limpia: el `copy` de la línea 109-112 la conserva para siempre.
- Escenario concreto de fallo: sin espacio en disco o con la base bloqueada, el usuario pulsa "Archivar obligación" → `repository.closeDebt` lanza `SQLiteException` → crash en lugar de un mensaje. Segundo escenario: eliminar una deuda cuyo borrado es rechazado deja el aviso rojo "No se pudo completar la acción" en la pantalla hasta salir.
- Fix mínimo: reutilizar el patrón de `saveSchedule` (envolver en `try/catch (Exception)` re-lanzando `CancellationException`, y comprobar `isSaving` al entrar). En `delete`, limpiar `errorMessage` al empezar.
- Confianza: alta
- Cross-módulo: no

### [DEB2-09] Reintentar tras un fallo posterior al commit local puede duplicar un pago o una deuda
- Severidad: media
- Categoría: datos
- Ubicación: P/DebtSettlementViewModel.kt:190-195; P/DebtOpeningViewModel.kt:136-141; data/OfflineFirstDebtRepository.kt:67-78, 103-109
- Qué está mal: el repositorio hace `localDataSource.settleDebt(...)` (commit) y después `.also { syncScheduler.schedule; reminderReconciler.reconcile }`. Si cualquiera de estos lanza (WorkManager, `LocalDate.parse` en el reconciliador), la excepción llega al ViewModel, que la trata como "no se guardó" (`getOrElse` → "Revisa la conexión e inténtalo otra vez") y deja el formulario abierto. El siguiente toque genera otro `operationId`/`debtId`, así que la idempotencia no lo frena.
- Escenario concreto de fallo: en una liquidación, el pago se guarda y el reconciliador lanza. El usuario ve error, vuelve a pulsar "Registrar pago" y registra el mismo pago dos veces (con la revisión nueva ya sincronizada en la pantalla), o crea la deuda duplicada.
- Fix mínimo: en el repositorio, aislar `schedule`/`reconcile` con `runCatching` para que un fallo del recordatorio no se propague después del commit. (Defecto del repo; aquí solo se nota.)
- Confianza: baja (no comprobé si `reconcile`/`schedule` lanzan en la práctica; solo que pueden)
- Cross-módulo: sí → DEB1 (`OfflineFirstDebtRepository`)

### [DEB2-10] Los ViewModels que mueven dinero no tienen test unitario
- Severidad: media
- Categoría: test-faltante
- Ubicación: P/DebtSettlementViewModel.kt, P/DebtOpeningViewModel.kt, P/DebtEditViewModel.kt, P/DebtListViewModel.kt, P/DebtScheduleViewModel.kt:142-229
- Qué está mal: solo hay test de `DebtScheduleViewModel.saveSchedule`, `DebtDetailViewModel` (anulación) y `UpcomingDuesViewModel`. Los androidTest montan las pantallas con estado falso y lambdas escritas a mano, así que no ejecutan estos ViewModels. Los hallazgos 01, 06 y 08 no los atrapa nada.
- Escenario concreto de fallo: cualquier cambio en `save()` de liquidación (por ejemplo, aceptar principal mayor al saldo o quitar la comprobación de moneda de cuenta) pasa CI sin que nada falle.
- Fix mínimo (tests que faltan, un caso cada uno): (1) `DebtSettlementViewModel.save` con principal > restante no llama a `repository.settleDebt`; (2) `closeDebt(ADJUST)` con "-10" envía `principalDeltaMinor = -1000` y con "10" envía `+1000` (ligado a DEB2-01); (3) `DebtOpeningViewModel.save` en modo NEW_CASH_FLOW sin cuenta de la misma moneda no guarda; (4) `DebtEditViewModel` conserva el texto tras una segunda emisión del flow (ligado a DEB2-04).
- Confianza: alta
- Cross-módulo: no

### [DEB2-11] Código muerto: constancia PDF de cierre
- Severidad: baja
- Categoría: borrar
- Ubicación: P/DebtDetailScreen.kt:129-151, 720-778, y los imports `android.graphics.*`, `rememberLauncherForActivityResult`, `Dispatchers`, `withContext`
- Qué está mal: `certificateLauncher` se crea pero nunca se lanza (`grep` en todo `app/src`: solo hay referencias dentro de este archivo, ninguna llamada a `.launch`), `certificateMessage` nunca se muestra, y `writeDebtClosureCertificate` solo la llama ese callback. Cada recomposición registra un `ActivityResultLauncher` inútil.
- Escenario concreto de fallo: nadie puede generar la constancia; si se reactiva sin revisar, el PDF corta nombres largos (se dibujan sin salto de línea a 595 px) y usa `Double`.
- Fix mínimo: borrar ambos bloques y los imports huérfanos (o cablear un botón, si es una función pendiente de producto).
- Ahorro estimado: 90 líneas
- Confianza: alta
- Cross-módulo: no

### [DEB2-12] Código muerto: alertas de vencimiento de deudas y sus tests
- Severidad: baja
- Categoría: borrar
- Ubicación: P/DebtDueAlerts.kt:9-69 (todo excepto `debtDueDayLabel`), test/.../DebtDueAlertsTest.kt (casi completo)
- Qué está mal: `buildDebtDueAlerts`, `buildDebtDueOverview`, `DebtDueAlert`, `DebtDueOverview` y `DEFAULT_DEBT_ALERT_LEAD_DAYS` solo se usan en el test (`grep` en `app/src/main`: solo la propia definición). El único símbolo vivo es `debtDueDayLabel` (usado en UpcomingDuesScreen.kt:306). `DebtListScreen` ya reemplazó las alertas por el botón "Ver vencimientos" (línea 171), y `UpcomingDues` calcula sus propias ventanas, por lo que esta lógica y sus 8 tests prueban un camino que ya no existe. Su regla de ventana (7 días por defecto) además difiere de la de `ObserveUpcomingDues`.
- Escenario concreto de fallo: alguien arregla un bug en `buildDebtDueOverview`, CI queda en verde y el usuario no nota ningún cambio; o reutiliza la función creyendo que es la regla vigente.
- Fix mínimo: mover `debtDueDayLabel` junto a `UpcomingDuesScreen` y borrar el resto y los tests del overview (conservar el test de `debtDueDayLabel`).
- Ahorro estimado: 65 líneas de main y 80 de tests
- Confianza: alta
- Cross-módulo: no

### [DEB2-13] `hasVerifiedPremium` copiado en dos ViewModels de deudas
- Severidad: baja
- Categoría: fusionar
- Ubicación: P/DebtListViewModel.kt:114-119, P/DebtOpeningViewModel.kt:188-193 (existen otras dos copias idénticas en Goals)
- Qué está mal: cuatro copias idénticas de la verificación de plan Premium (UUID → cache de acceso → evaluador). Si cambia la regla de entitlement hay que acordarse de cuatro sitios; la lista y el alta ya deciden el límite Free con esta lógica.
- Escenario concreto de fallo: se endurece la regla en una copia (por ejemplo, lease vencido) y la otra sigue dejando crear deudas, o al revés: la lista muestra el límite pero el alta no lo aplica.
- Fix mínimo: un único `suspend fun hasVerifiedPremium(...)` en un colaborador de `plans/data/entitlement` y llamarlo desde los cuatro.
- Ahorro estimado: 12 líneas
- Confianza: alta
- Cross-módulo: sí → GOA1 (las dos copias de goals) y UI2 (dueño del entitlement)

### [DEB2-14] Fechas con zonas horarias distintas según la pantalla
- Severidad: baja
- Categoría: bug
- Ubicación: P/DebtDetailScreen.kt:369, 533, 613; P/DebtListScreen.kt:104; P/DebtHistorySheet.kt:59; P/DebtScheduleViewModel.kt:38; P/DebtFormUiState.kt:35; P/UpcomingDuesViewModel.kt:35, 99
- Qué está mal: `UpcomingDuesViewModel` y el cierre de deuda usan `America/Lima`; "hoy" en lista/detalle (`LocalDate.now()`), la fecha de cada evento (`ZoneId.systemDefault()`) y `openedOn`/primer vencimiento usan la zona del teléfono.
- Escenario concreto de fallo: usuario peruano de viaje (o con el reloj en otra zona). A las 22:00 hora de Lima, "Vence hoy" en el detalle y "Vence mañana" en Próximos vencimientos; el mismo pago aparece con un día distinto en el historial (zona del teléfono) y en la fecha de cierre (Lima).
- Fix mínimo: una constante `LIMA_ZONE` compartida y usar `LocalDate.now(LIMA_ZONE)` / `atZone(LIMA_ZONE)` en estos sitios.
- Confianza: media
- Cross-módulo: no

### [DEB2-15] El motivo del cierre es un solo campo compartido por ajuste, condonación, liquidación y archivo
- Severidad: baja
- Categoría: bug
- Ubicación: P/DebtScheduleScreen.kt:1118-1126, 1316-1321, 313; P/DebtScheduleViewModel.kt:96, 165-186
- Qué está mal: `closureReason` es un único `String` del estado. El texto escrito en "Motivo de condonación" aparece como "Motivo del ajuste" y viaja también con "Liquidar" y "Archivar".
- Escenario concreto de fallo: el usuario escribe "Acuerdo con Carlos" para condonar, cambia de idea y aplica un ajuste de redondeo: el ajuste queda en el historial con el motivo "Acuerdo con Carlos".
- Fix mínimo: limpiar `closureReason` al abrir/cerrar cada sección o separar `adjustmentReason` y `forgivenessReason`.
- Confianza: alta
- Cross-módulo: no

### [DEB2-16] "Reintentar" de Próximos vencimientos no hace nada tras un error de mapeo
- Severidad: baja
- Categoría: bug
- Ubicación: P/UpcomingDuesViewModel.kt:68-102, 108
- Qué está mal: el `.catch { emit(UpcomingDuesUiState(...error...)) }` del `combine` externo termina el flujo. `retry()` solo incrementa `retries`, que alimenta el flujo interno ya cancelado. El error puede venir del propio mapeo (`Math.addExact`, `Currency.fromCode` con un código no soportado).
- Escenario concreto de fallo: una fila con moneda no PEN/USD hace lanzar `Currency.fromCode` dentro del `combine`; la pantalla queda en "No se pudieron mostrar los vencimientos" y "Reintentar" no recarga ni reacciona a los filtros hasta salir de la pantalla y esperar 5 s.
- Fix mínimo: mover el `.catch` dentro del bloque que calcula cada estado (`runCatching` por emisión) para que el flujo siga vivo, o hacer que `retry()` reinicie el `stateIn`.
- Confianza: media (el escenario requiere datos fuera de lo esperado; el camino de error sí es real)
- Cross-módulo: no

### [DEB2-17] Acciones de cierre ofrecidas en deudas no activas y errores con código crudo
- Severidad: baja
- Categoría: bug
- Ubicación: P/DebtScheduleScreen.kt:219-258, 1439-1463, 1509-1520, 1128-1139; P/DebtScheduleViewModel.kt:221-227
- Qué está mal: la pantalla de cronograma/cierre no mira `debt.status`. Una deuda `CANCELLED` (el detalle sigue mostrando el botón "Cuotas") ofrece ajustar, condonar y archivar; el repositorio las rechaza con `DEBT_NOT_ACTIVE` y `closureError` lo muestra como "No se pudo cerrar la deuda (DEBT_NOT_ACTIVE)".
- Escenario concreto de fallo: usuario entra a una deuda cancelada con saldo pendiente, pulsa "Condonar" y recibe un mensaje técnico en lugar de ver las acciones deshabilitadas.
- Fix mínimo: `enabled = debt.status == ACTIVE && !state.isSaving` en los botones y un caso `"DEBT_NOT_ACTIVE"` en `closureError`.
- Confianza: media
- Cross-módulo: no

### [DEB2-18] Tests que no prueban lo que dicen o están en el sitio equivocado
- Severidad: baja
- Categoría: test-faltante
- Ubicación: androidTest/.../DebtMovementDetailTest.kt:20-46; androidTest/.../DebtEditDetailsFlowTest.kt:15-50; androidTest/.../ReceivableDebtFlowTest.kt:20-48
- Qué está mal: `DebtMovementDetailTest` prueba `MovementDetailSheet` y `VoidMovementDialog` (movimientos, no deudas). `DebtEditDetailsFlowTest` verifica las lambdas que el propio test define (`state.value = ...copy(...)`) y que el principal no es editable, pero no ejercita `DebtEditViewModel`. `ReceivableDebtFlowTest` solo comprueba que un texto y un botón deshabilitado aparecen, con callbacks vacíos.
- Escenario concreto de fallo: se rompe el flujo guardar→`editDebtDetails` y estos tests siguen pasando; un cambio en el detalle de movimientos hace fallar un archivo de deudas.
- Fix mínimo: mover `DebtMovementDetailTest` a movements/presentation (MOV3); añadir el test unitario del ViewModel de edición descrito en DEB2-10 y borrar el androidTest redundante si no aporta más que la comprobación de "principal no editable".
- Confianza: media (leí completos DebtEditDetailsFlowTest, ReceivableDebtFlowTest y DebtMovementDetailTest; el resto solo por nombres de pruebas)
- Cross-módulo: sí → MOV3 (reubicar el test de movimientos)

### [DEB2-19] Rama vacía y ramas inalcanzables
- Severidad: baja
- Categoría: borrar
- Ubicación: P/DebtListScreen.kt:577-578; P/DebtOpeningViewModel.kt:153; P/DebtSettlementViewModel.kt:208-210; P/DebtScheduleViewModel.kt:193; P/DebtActionsSheet.kt:25 y P/DebtDetailScreen.kt:262
- Qué está mal: en `DebtStatusLine`, si no hay fecha y la cuota está PARTIAL o PAID se abre un `Column` vacío (no pinta nada). `DebtCommandResult.Retryable` no la produce ningún origen en `debts/data/local` (grep sin resultados), así que las cuatro ramas `Retryable` de los ViewModels son inalcanzables. `onDetail` de `DebtActionsSheet` se recibe y no se usa; en el detalle se pasa `{}`.
- Escenario concreto de fallo: ninguno visible; es ruido que oculta el defecto DEB2-09, porque se asume que "Retryable" cubre los errores de guardado.
- Fix mínimo: borrar el `Column` vacío, el parámetro `onDetail` y las ramas `Retryable` hasta que exista un productor (el `when` seguirá exhaustivo porque la clase sellada lo exige: dejar una rama común).
- Ahorro estimado: 15 líneas
- Confianza: media
- Cross-módulo: no

## Código de riesgo sin tests

- `DebtScheduleViewModel.closeDebt` (ADJUST/FORGIVE, signo y monto): test con "-10" y "10" que compruebe `principalDeltaMinor` de `DebtClosureCommand`.
- `DebtScheduleViewModel.cancelSchedule`: test de que una excepción del repositorio deja `isSaving = false` y un mensaje, sin propagar.
- `DebtSettlementViewModel.save`: principal > restante no llama al repositorio; interés > 0 en cobro envía categoría válida (relacionado con DEB2-06).
- `DebtOpeningViewModel.save`: cuenta de moneda distinta no guarda; `Duplicate` emite `Saved`.
- `DebtEditViewModel`: segunda emisión del flow no pisa el texto editado.
- `DebtDetailViewModel.delete`: rechazo `HISTORY_PRESERVED` muestra el mensaje y no navega.
- `UpcomingDuesViewModel`: flujo de error y `retry()` (DEB2-16).
- `summarizeClosure` está probado; falta un caso de ADJUSTMENT positivo (aumento) para ver el signo de `adjustedMinor`.

## Lo que no revisé

- `DebtCreationTypeSheet.kt`: leí solo las primeras 60 líneas (cabecera, estado de la hoja y `selectType`); las 91 restantes son diseño y no entraron en el análisis.
- androidTest `DebtPlanningTabTest`, `DebtScheduleAndClosureFlowTest`, `DebtSettlementFlowTest`, `PayableDebtFlowTest`, `UpcomingDuesScreenTest`: solo la lista de casos y el comienzo de dos archivos; los consideré pruebas de UI con estado inyectado.
- `DebtThemePreview.kt`: leído completo (solo previews, nada que reportar).
- Paquetes fuera de alcance que leí solo para entender contratos: `OfflineFirstDebtRepository`, `SetDebtSchedule`, `DebtAmountParser`, `DebtReminderScheduler`, `DebtsNavigation`, `PlanningActionsSheet`.

## Dudas

- ¿El modo de ocultar saldos (`LocalBalanceMasked`) debe cubrir también el detalle, el cronograma y la hoja de pago? La lista sí lo respeta, y así lo supuse en DEB2-05.
- Cobros con interés: ¿debe el usuario elegir categoría de ingreso (como en pagos) o el interés de un cobro no lleva categoría? (DEB2-06)
- ¿La constancia PDF de cierre es una función planeada o un residuo? (DEB2-11)
- ¿Debe una cuota parcial mostrar el monto restante o el planificado en la lista y el detalle? Hoy lo hacen distinto que Próximos vencimientos. (DEB2-07)

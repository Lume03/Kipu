# MOV3 — movements/presentation: formularios y edición (editor, quick, refund, void, detalle)

Esta porción contiene los formularios con los que el usuario crea, corrige, anula y reembolsa movimientos: registro rápido (`QuickMovementScreen` y su ViewModel), editor de correcciones (`MovementEditorSheet` y su ViewModel), reembolso, anulación y hoja de detalle. Asumí miles de usuarios con uno o dos dispositivos, miles de movimientos cada uno, uso offline frecuente y sincronización posterior; por eso pesé más los casos de reintento, conflicto de revisión y fechas en America/Lima (UTC-5). Auditoría estática: no ejecuté nada. Leí los 12 archivos de main y los 9 de test/androidTest completos; el dominio, el DAO y el Planner se leyeron solo para entender contratos.

## Hallazgos

### [MOV3-01] El editor guarda la fecha elegida como medianoche UTC (día y hora incorrectos en Lima)
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorSheet.kt:771-801 (también 1054-1065)
- Qué está mal: el `DatePicker` de Material3 trabaja en milisegundos de medianoche UTC. El editor le pasa `occurredAt` tal cual como `initialSelectedDateMillis` (línea 773) y devuelve `selectedDateMillis` tal cual a `onDateChanged` (780-782). No convierte entre UTC y zona local, y pierde la hora del día. El registro rápido sí lo hace bien (`toUtcDateMillis` / `datePickerMillisToLocalInstant`, QuickMovementScreen.kt:1687-1698) y el reembolso también. Es la misma regla aplicada distinto en dos sitios.
- Escenario concreto de fallo: gasto del 15-nov a las 20:00 Lima (= 16-nov 01:00Z). Al abrir el selector se marca el 16. Si el usuario solo pulsa "Aceptar" sin tocar nada, `onDateChanged(16-nov 00:00Z)` marca el campo como modificado y, al guardar, el movimiento pasa a 15-nov 19:00 Lima, sin hora real. Si elige el 20-nov, se guarda 20-nov 00:00Z = 19-nov 19:00 Lima y la etiqueta (`editorDateLabel`, usa `systemDefault`) muestra "19 nov". El movimiento cae un día antes, y puede cambiar de periodo de presupuesto, de corte de tarjeta o de mes en analytics.
- Fix mínimo: copiar las dos funciones `toUtcDateMillis`/`datePickerMillisToLocalInstant` (ya existen en QuickMovementScreen.kt, hacerlas `internal` y reutilizarlas) en el `DatePickerDialog` del editor. Conserva la hora original.
- Confianza: alta
- Cross-módulo: no

### [MOV3-02] Tras un `Conflict` al guardar, el editor borra lo que el usuario escribió y el mensaje de error
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorViewModel.kt:418-427 y 207-210, 248-283
- Qué está mal: en la rama `Conflict` se pone `hasConflict=true` y `generalError="Conflicto de sincronización..."` y luego se llama `loadTransaction`. Esa función empieza con `generalError = null` (línea 210) y vuelve a rellenar todo el formulario desde la cabecera, con `hasConflict = activeProposal != null`. El `Conflict` de `commitRevise` es `REVISION_CONFLICT` (MovementLocalDataSource.kt:750), que no crea ninguna propuesta; por tanto `hasConflict` vuelve a `false`.
- Escenario concreto de fallo: dos dispositivos; el dispositivo B corrige el monto y su cambio llega por sync mientras A tiene el editor abierto. En A el usuario cambia la categoría y nota, pulsa Guardar: el formulario se recarga con los valores de B, sin mensaje y sin los cambios del usuario. El usuario cree que guardó o no entiende qué pasó.
- Fix mínimo: guardar el mensaje y mostrarlo después de recargar (por ejemplo `loadTransaction(id, errorAfterLoad = "...")`), o no tocar los campos editados y solo refrescar `expectedRevision`/valores iniciales.
- Confianza: media (no ejecuté el flujo; la secuencia sale de leer VM y data source)
- Cross-módulo: no

### [MOV3-03] El editor ofrece cuentas de otra moneda y el rechazo llega como código crudo
- Severidad: media
- Categoría: bug
- Ubicación: MovementEditorSheet.kt:659-678 (pickers con `uiState.availableAccounts` sin filtrar), MovementEditorViewModel.kt:171-173 y 428-435
- Qué está mal: el selector de origen/destino muestra todas las cuentas activas. El Planner rechaza cualquier cuenta cuya moneda difiera de la del movimiento (`INVALID_REFERENCE`, MovementRevisionPlanner.kt:47). El mensaje al usuario es literalmente `No se pudo guardar la corrección (INVALID_REFERENCE)`. Ningún código se traduce. En el registro rápido, en cambio, el selector sí filtra por moneda.
- Escenario concreto de fallo: gasto en PEN, el usuario cambia la cuenta a "Dólares" y pulsa "Guardar 1 cambio" → ve "(INVALID_REFERENCE)" sin saber qué corregir. Lo mismo con `REFERENCE_NOT_ELIGIBLE`, `OPERATION_SPECIALIZED`, `IMMUTABLE_FINANCIAL_KIND`.
- Fix mínimo: filtrar `accounts = uiState.availableAccounts.filter { it.currency.name == uiState.currency }` en los dos `AccountPickerSheet`, y mapear 3-4 códigos comunes a texto en español (o mostrar texto genérico sin código).
- Confianza: alta
- Cross-módulo: no

### [MOV3-04] La tarjeta de conflicto no enseña qué cambia; "Reaplicar" falla en silencio
- Severidad: media
- Categoría: bug
- Ubicación: MovementEditorSheet.kt:394-402; MovementEditorViewModel.kt:92-93 y 279-280 (se calculan y no se usan); 449-463 (`onRedoConflict`); 440-447 (`onDiscardConflict` ignora el resultado)
- Qué está mal: el VM decodifica `conflictProposedOccurredAt` y `conflictProposedSourceAccountId`, pero la tarjeta solo pinta el monto. Si la propuesta cambia fecha o cuenta, ambos montos son iguales. En `onRedoConflict` cualquier resultado distinto de `Success` (Conflict, Rejected) solo recarga: no hay mensaje.
- Escenario concreto de fallo: la propuesta en conflicto solo cambia la cuenta de origen. La tarjeta muestra "Versión oficial actual S/ 50.00" y "Propuesta en conflicto S/ 50.00". El usuario no puede decidir entre Descartar y Reaplicar. Si pulsa Reaplicar y el dominio la rechaza, el spinner termina y no ocurre nada visible.
- Fix mínimo: mostrar fecha y cuenta de la propuesta junto al monto cuando difieran (los datos ya están en el estado), y poner `generalError` en la rama `else` de `onRedoConflict`.
- Confianza: media
- Cross-módulo: no

### [MOV3-05] Reintentar un guardado fallido crea una clave de idempotencia nueva, aunque el mensaje promete lo contrario
- Severidad: media
- Categoría: datos
- Ubicación: QuickMovementViewModel.kt:1018 y 1196-1205
- Qué está mal: `saveCurrentMovement` genera `UUID.randomUUID()` en cada pulsación (solo el flujo de comprobante usa una clave estable). `executeRegister` captura cualquier excepción y muestra "No se pudo guardar. Puedes reintentar sin duplicar la operación." `OfflineFirstMovementRepository.registerTransaction` (líneas 110-120) hace el commit en Room y después llama a `syncScheduler.scheduleSync` y a `expenseChangeListener.onExpenseConsumptionChanged`. Si cualquiera de las dos lanza tras el commit, el movimiento ya existe pero la UI dice que falló.
- Escenario concreto de fallo: gasto S/ 80 guardado en Room; el listener de gasto lanza (por ejemplo error al evaluar alertas de presupuesto). La UI muestra el error. El usuario pulsa "Reintentar guardar": clave nueva, `occurredAt` refrescado a "ahora". Si pasan menos de 5 min aparece el aviso de duplicado (el usuario puede confirmar y duplicar); si pasan más, se guarda un segundo gasto sin aviso y el saldo se descuenta dos veces.
- Fix mínimo: conservar la clave en el VM (`pendingKey`) desde el primer intento y limpiarla en éxito o cuando cambien monto/cuenta/categoría; así el mensaje deja de ser falso. Alternativa: que el repositorio no propague excepciones de los hooks posteriores al commit.
- Confianza: media (requiere que un hook post-commit lance; no lo comprobé en `expenseChangeListener`)
- Cross-módulo: sí → MOV2 (el repositorio/hook que lanza tras el commit); la clave por intento está en MOV3.

### [MOV3-06] "Aplicar a deuda/meta" (saveApplication) mueve dinero y no tiene ningún test
- Severidad: media
- Categoría: test-faltante
- Ubicación: QuickMovementViewModel.kt:1038-1126; QuickMovementViewModelTest.kt (no hay llamadas con `onApplicationSelected`)
- Qué está mal: 90 líneas de validación y envío de `settleDebt` y `reserveGoalFunds` (moneda, tipo PAYABLE/RECEIVABLE, principal contra saldo pendiente, meta bloqueada por plan, cuenta destino distinta). El constructor del test ni siquiera pasa `debtRepository` ni `goalRepository`, así que estas ramas nunca se ejecutan.
- Escenario concreto de fallo: un cambio en la comparación `amountMinor > debt.remainingPrincipalMinor` o en `debt.currencyCode != currency.name` permitiría pagar de más o en otra moneda sin que ningún test falle.
- Fix mínimo: un test con `DebtRepository` falso: deuda PAYABLE de S/ 100, cuenta PEN, monto 150 → no se llama a `settleDebt` y `generalError` = "El principal supera el saldo pendiente.". Y un caso feliz de 60 que verifique `principalMinor=6000`.
- Confianza: alta
- Cross-módulo: no

### [MOV3-07] El pago de tarjeta desde Quick no tiene test de fallo y depende de textos en inglés de una excepción
- Severidad: media
- Categoría: test-faltante
- Ubicación: QuickMovementViewModel.kt:979-1010 (mapeo 1002-1006); QuickMovementViewModelTest.kt:1060 (`payCreditCard` falso siempre devuelve éxito)
- Qué está mal: el mensaje al usuario se decide comparando `error.message` con `"Insufficient funds in source account"` y `"Payment amount exceeds current card debt"`, copiados de `require(...)` en OfflineFirstFinancialInstrumentsRepository.kt:1087 y 1094. Si alguien cambia esos textos, el usuario pasa a ver el mensaje genérico sin que falle nada. Tampoco hay test de la regla "origen debe ser BANK o SAVINGS" (981-985).
- Escenario concreto de fallo: se traduce o edita el `require` del repositorio; el pago con saldo insuficiente muestra "No se pudo guardar el pago de tarjeta. Revisa la cuenta..." en vez de "Saldo insuficiente".
- Fix mínimo: test con `payCreditCard` que devuelva `Result.failure(IllegalArgumentException("Insufficient funds in source account"))` y aserte el texto; y otro con origen de efectivo/billetera que aserte `accountError`. (Mejor a medio plazo: excepciones tipadas en ACC1/ACC5.)
- Confianza: alta
- Cross-módulo: sí → ACC5 (los textos de `require` viven en OfflineFirstFinancialInstrumentsRepository)

### [MOV3-08] El selector de comercio de Quick conserva el comercio del movimiento anterior
- Severidad: media
- Categoría: bug
- Ubicación: QuickMovementScreen.kt:158, 183-201, 302-321
- Qué está mal: `MerchantPickerViewModel` guarda `selectedMerchant`/`provisionalText` y la hoja los pinta arriba como "Comercio del catálogo" (MerchantPicker.kt:674-711). Tras un guardado, `QuickMovementViewModel.resetForm()` limpia el formulario pero nadie llama a `merchantPickerViewModel.clearSelection()`. `QuickMovementBottomSheet` usa `hiltViewModel()` en la entrada de navegación del historial, así que el VM del selector sobrevive. Tampoco se inicializa desde `prepareReceiptDraft` (el comercio del comprobante no aparece).
- Escenario concreto de fallo: el usuario guarda un gasto en "Bembos". Abre otro gasto, entra a "Más detalles" > Comercio: el selector muestra "Bembos" como elegido aunque el formulario está vacío. Si lo toca de nuevo y el selector lo trata como ya seleccionado, la acción puede no disparar `onSelectMerchant`.
- Fix mínimo: en el `LaunchedEffect` de eventos, llamar `merchantPickerViewModel.clearSelection()` junto con el cierre (o llamar `initialize(...)` con el estado del VM al abrir el selector).
- Confianza: media (no verifiqué qué hace la fila ya seleccionada al tocarse)
- Cross-módulo: no

### [MOV3-09] El editor no tiene tests de `onSave` (validaciones, Conflict, especializados)
- Severidad: media
- Categoría: test-faltante
- Ubicación: MovementEditorViewModel.kt:347-438; MovementEditorRememberTest.kt y MovementEditorUiStateTest.kt
- Qué está mal: los tests existentes cubren recordar categoría y `hasUnsavedChanges`. No hay test de: monto 0 o inválido, transferencia con destino igual al origen, gasto sin categoría, la rama `Conflict`/`Rejected`, ni de `isSpecialized` (cuotas, tarjeta, kind distinto). Estas ramas deciden qué dinero se corrige.
- Escenario concreto de fallo: quitar el `|| currentState.selectedDestinationAccountId == currentState.selectedSourceAccountId` (línea 367) dejaría pasar una transferencia a la misma cuenta hasta el Planner, y ningún test fallaría.
- Fix mínimo: tres casos en `MovementEditorRememberTest`: (a) `onAmountChanged("0")` + `onSave` → `amountError` y sin llamada a `revise`; (b) head con `cardId` → `isSpecialized` y `onSave` no llama a `revise`; (c) `revise` devuelve `Conflict` → verificar que el mensaje sobrevive (cubre también MOV3-02).
- Confianza: alta
- Cross-módulo: no

### [MOV3-10] La carga inicial del editor puede dejar `initialCategoryName` en null
- Severidad: baja
- Categoría: bug
- Ubicación: MovementEditorViewModel.kt:191-197 y 248-262
- Qué está mal: si `loadTransaction` termina antes de que lleguen las categorías, `initialCategoryName = matchingCat?.name` queda null. El colector de categorías actualiza `selectedCategoryName` pero nunca `initialCategoryName`.
- Escenario concreto de fallo: catálogo grande o dispositivo lento; el usuario cambia la categoría y el resumen "Qué pasará al guardar" dice "Categoría — Antes: Ninguno" y la fila "Antes: Sin categoría", aunque el movimiento tenía categoría.
- Fix mínimo: en el colector (línea 193-196) añadir `initialCategoryName = options.firstOrNull { it.id == current.initialCategoryId }?.name ?: current.initialCategoryName`.
- Confianza: media
- Cross-módulo: no

### [MOV3-11] Quick transforma "," en "." y rechaza montos con separador de miles
- Severidad: baja
- Categoría: bug
- Ubicación: QuickMovementViewModel.kt:499-503; core/finance/domain/MoneyInputParser.kt (cross-módulo, solo lectura)
- Qué está mal: `onAmountChanged` hace `replace(',', '.')`. `MoneyInputParser` sabe distinguir "1,234.50", pero recibe "1.234.50" y devuelve null. La capacidad del parser queda inalcanzable desde Quick.
- Escenario concreto de fallo: el usuario pega o escribe "1,500.50" → `amountError` "Ingresa un monto válido mayor a 0" sin explicación. El editor no tiene este problema (usa el texto sin reemplazar).
- Fix mínimo: quitar el `.replace(',', '.')` y dejar que el parser decida (el teclado decimal ya limita a dígitos y separador).
- Confianza: media
- Cross-módulo: no

### [MOV3-12] El impacto de presupuesto hace una consulta por presupuesto en cada tecla y parpadea
- Severidad: baja
- Categoría: rendimiento
- Ubicación: QuickMovementViewModel.kt:325-355 (línea 333)
- Qué está mal: el flujo `combine` se reinicia con cada cambio de `amountText`. Cada vez se vacía `budgetImpacts` y se ejecuta `getBudgetConsumption.current` por cada presupuesto activo. `collectLatest` cancela el trabajo anterior, pero el vaciado inmediato hace que la tarjeta de presupuesto desaparezca y reaparezca en cada dígito (y mueve el layout).
- Escenario concreto de fallo: usuario con 8 presupuestos activos escribe "125.50": 6 consultas a Room por tecla en main-safe pero con parpadeo de la tarjeta "Impacto en presupuesto" en cada dígito.
- Fix mínimo: `debounce(250)` sobre el `_uiState.map{...}` y no vaciar `budgetImpacts` hasta tener el nuevo resultado (o hasta que la condición deje de cumplirse).
- Confianza: media
- Cross-módulo: no

### [MOV3-13] El reembolso permite cualquier fecha (antes de la compra o futura)
- Severidad: baja
- Categoría: bug
- Ubicación: MovementRefundSheet.kt:204-218; dominio: domain/RegisterRefundTransaction.kt:67-86 (sin validación de `occurredAt`)
- Qué está mal: el `DatePickerDialog` no restringe fechas, y el dominio no compara `occurredAt` con la fecha de la compra ni con "hoy". El texto de la hoja dice "Fecha real del reembolso".
- Escenario concreto de fallo: compra del 15-sep, el usuario toca por error el 15-ago o 2027: el ingreso por reembolso queda antes de la compra o en el futuro, y puede cambiar saldos por fecha y los reportes.
- Fix mínimo: `DatePicker(selectableDates = ...)` con rango [fecha de compra, hoy] y la misma validación en el caso de uso.
- Confianza: baja (no sé si el servidor lo rechaza; no lo comprobé)
- Cross-módulo: sí → MOV1 (la validación de dominio de `RegisterRefundTransaction`)

### [MOV3-14] Dos definiciones distintas de "movimiento especializado"
- Severidad: baja
- Categoría: fusionar
- Ubicación: MovementEditorViewModel.kt:220-226 vs VoidMovementDialog.kt:57-65
- Qué está mal: el VM considera especializado cualquier `operationKind` no vacío (incluido "STANDARD") y no incluye `legacyKind = DEBT_PAYMENT`; `isSpecializedMovement` (usada por el detalle) hace lo contrario en ambos puntos. Hoy `STANDARD` se guarda como null en local, así que no falla, pero es una regla duplicada que puede divergir.
- Escenario concreto de fallo: un movimiento con `legacyKind=DEBT_PAYMENT` y `operationKind=null` se abre en el editor por otra ruta (por ejemplo desde ReceiptsNavigation.kt:108): el VM lo trata como editable y el Planner lo rechaza con `OPERATION_SPECIALIZED`, mostrado como código crudo.
- Fix mínimo: construir un `Transaction` en el VM ya cargado (`tx`) y usar `isSpecializedMovement(TransactionItem(tx))`; borrar las 6 líneas locales.
- Confianza: media
- Cross-módulo: no
- Ahorro estimado: 7 líneas

### [MOV3-15] Código muerto en el área de formularios
- Severidad: baja
- Categoría: borrar
- Ubicación: QuickMovementViewModel.kt:733-745 (`createSubcategory`, sin llamadores), 179 y QuickMovementScreen.kt:198 (`ShowMessage` nunca se emite), MovementEditorViewModel.kt:129 y MovementEditorSheet.kt:128 (idem), MovementEditorViewModel.kt:92-93, 279-280 (campos del conflicto que nadie lee, ver MOV3-04), parámetro `rememberFrequentMerchant` (QuickMovementViewModel.kt:661, ignorado), MovementDetailSheet.kt:54 (`onRemoveReceipt` jamás se usa)
- Qué está mal: tras grep de todo el árbol (main, test, androidTest) no hay llamadores ni emisores. Consecuencia visible: `MovementHistoryScreen` pasa `onRemoveReceipt`, pero el detalle no tiene botón para quitar el comprobante, así que un PDF adjuntado por error no se puede quitar.
- Escenario concreto de fallo: el usuario adjunta el PDF equivocado y no hay forma de quitarlo; el parámetro y el código del llamador existen pero la UI no lo expone.
- Fix mínimo: o borrar el código muerto, o añadir el botón "Quitar" en el banner de comprobante (la función ya existe en el llamador). Para `onRemoveReceipt` decide producto (ver Dudas).
- Confianza: alta (grep completo)
- Cross-módulo: no
- Ahorro estimado: 30 líneas

### [MOV3-16] La vista previa de anulación de un ingreso no nombra la cuenta
- Severidad: baja
- Categoría: bug
- Ubicación: VoidMovementDialog.kt:436
- Qué está mal: para `INCOME` el rótulo usa `item.destinationAccountAlias ?: "Cuenta"`. El registro rápido y el editor guardan el ingreso con la cuenta en `sourceAccountId` y `destinationAccountId = null`; el propio detalle (MovementDetailSheet.kt:453) usa `sourceAccountAlias ?: destinationAccountAlias`. Se proyecta el saldo de `sourceAccountId` (línea 394-401) pero se etiqueta con otro campo.
- Escenario concreto de fallo: usuario con 3 cuentas anula un ingreso: la tarjeta "Impacto en saldos" dice "Cuenta · ingreso" sin decir cuál; no puede verificar qué cuenta se descuenta.
- Fix mínimo: `item.sourceAccountAlias ?: item.destinationAccountAlias ?: "Cuenta"`.
- Confianza: media
- Cross-módulo: no

### [MOV3-17] Los textos libres (nota, comercio, motivo de anulación) no tienen longitud máxima
- Severidad: baja
- Categoría: escala
- Ubicación: QuickMovementScreen.kt:1029-1044; MovementEditorSheet.kt:1003-1017; VoidMovementDialog.kt:284-298; RegisterTransactionValidator.kt (sin límite); supabase/migrations (sin CHECK de longitud en `note`)
- Qué está mal: ni la UI, ni el validador, ni la base remota limitan la longitud. El texto viaja en el outbox y en el payload de sync.
- Escenario concreto de fallo: el usuario pega un texto de varios MB en la nota; Room lo guarda, pero la petición de sync supera el límite del cuerpo de la edge function y el movimiento queda en `FAILED_PERMANENT` (reaparece como "No se pudo sincronizar").
- Fix mínimo: `onValueChange = { if (it.length <= 500) ... }` en la nota y el motivo, y la misma comprobación en el validador.
- Confianza: baja
- Cross-módulo: sí → SQL4 (si se quiere un CHECK en servidor)

### [MOV3-18] Tests débiles o frágiles
- Severidad: baja
- Categoría: test-faltante
- Ubicación: app/src/androidTest/.../MovementRefundScreenTest.kt:94-123; app/src/test/.../QuickMovementViewModelTest.kt:336-339
- Qué está mal: `showsPurchaseAndRefundPeriodsMasksMoneyAndSubmitsMinorUnits` promete comprobar periodos, pero solo afirma `submittedDate > 0L`. No afirma que la fecha enviada sea la de hoy a las 00:00 locales ni que respete el cambio de fecha. El test `stale form currency...` fuerza el estado con reflexión sobre el campo privado `_uiState`, que se rompe con cualquier renombre.
- Escenario concreto de fallo: cambiar `occurredAt` de refund por `System.currentTimeMillis() - 86_400_000` dejaría el test en verde.
- Fix mínimo: afirmar `submittedDate == LocalDate.now().atStartOfDay(zone)...` tras elegir fecha (o al menos que esté dentro del día actual); sustituir la reflexión por una función `internal` de prueba o por un `SavedStateHandle` precargado.
- Confianza: media
- Cross-módulo: no

## Código de riesgo sin tests

- `QuickMovementViewModel.saveApplication` (QuickMovementViewModel.kt:1038): deuda PAYABLE de S/ 100 y principal S/ 150 → no se llama a `settleDebt`.
- `QuickMovementViewModel` pago de tarjeta (979-1010): `payCreditCard` falla con "Insufficient funds..." → mensaje en español; origen CASH → `accountError`.
- `MovementEditorViewModel.onSave` (347-438): monto 0 → `amountError`; `Conflict` conserva mensaje; head con `cardId` → no llama a `revise`.
- Selector de fecha del editor (MovementEditorSheet.kt:771): un test Compose con `occurredAt` 20:00 Lima que pulse "Aceptar" sin cambios y verifique que `onDateChanged` no se dispara (o devuelve el mismo instante).
- `VoidMovementDialog` y `MovementVoidBalancePreview`: ningún test (ni unit ni androidTest) cubre el saldo proyectado de gasto, ingreso y transferencia (3 asserts bastan).
- `MovementEditorViewModel.onRedoConflict/onDiscardConflict`: sin test de resultado no exitoso.

## Lo que no revisé

- Todo lo listado en el alcance fue leído completo. De `QuickMovementScreenTest.kt` y `MovementEditorScreenTest.kt` leí solo los nombres de test y busqué fechas/guardado (grep), no cada aserción.
- Contratos externos solo leídos en parte, sin auditar: `MovementRevisionPlanner`, `MovementLocalDataSource` (revise, refund), `OfflineFirstMovementRepository.registerTransaction`, `MoneyInputParser`, `MerchantPickerViewModel`, `MovementHistoryScreen/ViewModel` (llamadores del editor, del refund y del void).

## Dudas

1. ¿Debe existir el botón "Quitar comprobante" en el detalle? Hoy `onRemoveReceipt` llega al detalle y no se usa (MOV3-15).
2. ¿Se permiten reembolsos con fecha anterior a la compra o futura? No hay regla escrita en el código (MOV3-13).
3. ¿Es intencional que `QuickMovementViewModel` tome `ownerId` una sola vez en `init` (líneas 287-391)? Si la sesión no está lista al crear el VM, nunca cargan categorías, deudas, metas ni presupuestos. Lo asumí siempre listo por el control de navegación (NAV1/SEC1 deberían confirmarlo).
4. `prepareReceiptDraft` elige la cuenta antes de que carguen las cuentas (líneas 236-249). Si el VM es nuevo, `selectedSourceAccountId` queda null y luego el colector toma la primera cuenta (puede ser USD y bloquear el guardado). No pude confirmar si ocurre en la práctica; lo dejé fuera de los hallazgos.

Se dejaron fuera 3 menores: la nota de Quick no se recorta (`trim`) y la del editor sí (inconsistencia sin efecto), el borrador de Quick guarda nota y comercio en `SavedStateHandle` (privacidad menor), y `rememberNetworkOffline` puede mostrar estado obsoleto en `onLost`.

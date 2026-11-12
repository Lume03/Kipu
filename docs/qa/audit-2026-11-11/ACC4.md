# ACC4 — accounts/presentation: components + raíz + CalmEmeraldInstrumentComponents y previews

Esta porción gestiona la presentación de cuentas y tarjetas en la raíz del módulo, componentes visuales de tarjetas (arte vectorial ID-1, presets de diseño, resumen y desglose de cuotas), flujos modales de alta con diseño Calm Emerald y previsualizaciones temáticas. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y uso offline frecuente en soles y dólares peruanos. Auditoría estática sobre `main`, sin red, builds, emuladores ni dispositivos.

## Hallazgos

20 hallazgos: 0 críticos, 4 altos, 10 medios y 6 bajos. Se omitieron 3 detalles cosméticos menores de estilo sin impacto funcional.

### [ACC4-01] Árbol de productos de crédito bloquea el plegado si hay un producto seleccionado o si el banco tiene una sola red/nivel
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1439,1486
- Qué está mal: En `CalmEmeraldCreditProductTree`, las condiciones de apertura de grupos evalúan `val netOpen = netKey in expanded.value || hasSelection || byNetwork.size == 1` y `val tierOpen = tierKey in expanded.value || tierHasSelection || byTier.size == 1`. Si un producto está seleccionado (`hasSelection == true`), o si la entidad emisora tiene una sola red (como Banco Falabella CMR que solo tiene Visa) o un solo nivel, la expresión booleana es invariablemente `true`. Al tocar el encabezado para contraer el grupo, `toggle()` añade o elimina la clave del conjunto `expanded.value`, pero el `|| hasSelection` o `|| byNetwork.size == 1` anula la acción y mantiene la rama abierta. El usuario queda completamente imposibilitado de plegar la sección.
- Escenario concreto de fallo: El usuario selecciona una tarjeta o abre un banco con una sola red (ej. Banco Falabella CMR) → pulsa el encabezado "Visa" para contraer la lista y despejar la pantalla → la lista no responde y permanece expandida.
- Fix mínimo: Inicializar el conjunto `expanded` con las claves seleccionadas o iniciales, y determinar `netOpen` únicamente por la presencia de `netKey` en `expanded` (o usar un conjunto de claves explícitamente colapsadas).
- Confianza: alta
- Cross-módulo: no

### [ACC4-02] Vinculación de débito en edición de cuenta bloquea a BanBif y lo degrada a 'Otra entidad'
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:594-601
- Qué está mal: En `registerDebitForEditedAccount`, la resolución del banco excluye explícitamente a BanBif: `bankChoices.firstOrNull { it.accountPreset == preset && it.code != "BANBIF" } ?: bankChoices.first { it.code == "OTHER" }`. Esto se introdujo como parche porque BanBif y "Otra entidad" comparten `AccountPreset.GENERIC` en `InstrumentFormModels.kt:93,104`. En consecuencia, si un usuario edita una cuenta de ahorros BanBif e ingresa los 4 dígitos para asociarle su tarjeta de débito, el selector descarta BanBif y cae al fallback `OTHER`. La tarjeta se persiste con `issuer = "Otra entidad"`, `preset = CardPreset.GENERIC` y `stylePresetId = null` (ya que `"OTHER"` no existe en `CardStylePresets`), perdiendo el arte oficial de `banbif-debit-classic`.
- Escenario concreto de fallo: Usuario con cuenta BanBif edita la cuenta e ingresa "4321" en los últimos dígitos de débito → guarda cambios → la tarjeta de débito se crea como "Otra entidad" con estilo genérico Kipu verde azulado, perdiendo la marca oficial BanBif y su arte vectorial verificado.
- Fix mínimo: Asignar un `accountPreset = AccountPreset.BANBIF` propio a BanBif en `InstrumentFormModels.kt` y eliminar el filtro excluyente `&& it.code != "BANBIF"` en `AccountsViewModel.kt`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-03] Vinculación de débito en edición asigna erróneamente red VISA a tarjetas Mastercard
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:598
- Qué está mal: `registerDebitForEditedAccount` construye la instancia de `DebitCard` con la red cableada fija: `network = com.kipu.app.feature.accounts.domain.model.CardNetwork.VISA`. Entidades emisoras cuyo débito estándar en Kipu es Mastercard (como Scotiabank con `CardPreset.SCOTIABANK_MASTERCARD` o Ripley con `CardPreset.RIPLEY_MASTERCARD`, definidos en `InstrumentFormModels.kt:72,127`) son registradas forzosamente como VISA.
- Escenario concreto de fallo: Usuario con cuenta Scotiabank o Ripley ingresa dígitos de débito en la pantalla de edición de cuenta → la tarjeta de débito vinculada se guarda con red VISA en Room y metadatos de sincronización, contradiciendo el contrato del banco.
- Fix mínimo: Derivar la red del `cardPreset` del banco: `network = if (bank.cardPreset.name.contains("MASTERCARD")) CardNetwork.MASTERCARD else CardNetwork.VISA`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-04] Gestos de arrastre 3D en InstrumentCardPreview bloquean el desplazamiento vertical en formularios y saturan corrutinas
- Severidad: alta
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/InstrumentCardPreview.kt:107-136
- Qué está mal: `detectDragGestures` ejecuta `change.consume()` incondicionalmente en cada movimiento del puntero para calcular el brillo e inclinación 3D. Al estar embebida en formularios con scroll vertical (`AccountFormScreen`, `CardFormScreen`, `DebitAccountSummary`), cualquier gesto vertical iniciado sobre la tarjeta es interceptado y consumido, impidiendo al usuario deslizar la pantalla hacia abajo. Además, el callback ejecuta `scope.launch { rotationY.snapTo(...); rotationX.snapTo(...) }` en cada evento de arrastre (hasta 120 fps), disparando cientos de corrutinas concurrentes efímeras para actualizar valores que no requieren suspensión.
- Escenario concreto de fallo: En `CardFormScreen` en un teléfono de pantalla media o pequeña, el usuario intenta deslizar la pantalla apoyando el pulgar sobre la tarjeta preview superior → la pantalla no se desplaza porque el gesto es interceptado y consumido por la tarjeta.
- Fix mínimo: Consumir el cambio únicamente cuando el desplazamiento sea predominantemente horizontal (`abs(change.position.x - lastX) > abs(change.position.y - lastY)`), y actualizar las rotaciones mediante estado mutable simple (`mutableFloatStateOf`) en lugar de despachar corrutinas en cada frame táctil.
- Confianza: alta
- Cross-módulo: no

### [ACC4-05] Pérdida de estado UI en árbol de productos de crédito ante rotación de pantalla
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1426
- Qué está mal: En `CalmEmeraldCreditProductTree`, el estado de ramas expandidas se declara como `val expanded = remember { mutableStateOf(setOf<String>()) }`. Al usar `remember` simple en vez de `rememberSaveable`, el estado se destruye ante rotaciones de pantalla, cambios de tamaño de ventana o cambios entre tema claro y oscuro.
- Escenario concreto de fallo: El usuario expande la red "Mastercard" y el nivel "Black" en el selector de tarjetas → gira el teléfono o cambia el modo oscuro → el árbol colapsa a su estado inicial, perdiendo la posición de navegación del usuario.
- Fix mínimo: Sustituir `remember { mutableStateOf(...) }` por `rememberSaveable { mutableStateOf(setOf<String>()) }`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-06] Corrupción de codificación en mensajes de error de notificaciones de crédito
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:132,137
- Qué está mal: El literal de cadena contiene un carácter corrupto `?` en lugar de la vocal con tilde `é`: `_creditNotificationsError.value = "No se pudieron cargar los avisos de cr?dito."`. Se repite tanto en la verificación de resultado fallido como en el bloque `catch`.
- Escenario concreto de fallo: Al fallar la consulta de avisos de sobregiro o vencer el timeout de 10 segundos, la interfaz de usuario muestra textualmente: "No se pudieron cargar los avisos de cr?dito.".
- Fix mínimo: Corregir la cadena a `"No se pudieron cargar los avisos de crédito."` o extraerla a un recurso en `strings.xml`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-07] Carreras concurrentes en refreshCreditUtilizationNotifications y estado de carga desincronizado
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:121-141,144-157
- Qué está mal: `refreshCreditUtilizationNotifications` y `loadCreditProductCatalog` lanzan corrutinas en `viewModelScope` sin cancelar el trabajo previo en vuelo (`Job`). Si el usuario pulsa reintentar varias veces o navega con rapidez, múltiples peticiones concurrentes compiten; una petición lenta anterior puede finalizar después de una reciente y sobreescribir el estado con datos viejos o marcar `_creditNotificationsSettled = true` prematuramente. Además, en `loadCreditProductCatalog`, `_creditCatalogLoading.value = false` no se protege en un bloque `finally`, por lo que una cancelación de corrutina deja la bandera de carga en `true` permanentemente.
- Escenario concreto de fallo: El usuario pulsa reintentar en el dashboard con conexión inestable → la primera petición falla después de que la segunda ya había tenido éxito → `_creditNotificationsError` se sobreescribe con el error tardío y oculta las alertas válidas.
- Fix mínimo: Almacenar `private var notificationsJob: Job? = null` y `private var catalogJob: Job? = null`, cancelando el job anterior al iniciar una nueva recarga, y ubicar el reset de carga dentro de un bloque `finally`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-08] Paréntesis de cierre huérfano en resumen de utilización de tarjeta de crédito
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditCardSummaryCard.kt:416
- Qué está mal: En la fila de utilización de la línea de crédito, tras mostrar el importe de la línea mediante `MoneyText`, se renderiza un componente `Text(text = ")", ...)` sin que exista ningún paréntesis de apertura en los textos precedentes.
- Escenario concreto de fallo: En `CreditCardSummaryCard`, la etiqueta de utilización muestra literalmente en pantalla: `"Uso de línea total de crédito: S/ 5,000.00)"`.
- Fix mínimo: Eliminar el componente `Text(text = ")", ...)` en las líneas 415-419.
- Confianza: alta
- Cross-módulo: no

### [ACC4-09] Componente obsoleto y no referenciado en producción: CreditCardSummaryCard
- Severidad: media
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditCardSummaryCard.kt:1-498
- Qué está mal: `CreditCardSummaryCard` (498 líneas) implementaba la ficha de resumen de tarjeta de crédito para la US3 / T042. Durante el rediseño Calm Emerald, `DashboardScreen.kt` implementó `DashboardCreditCardRow` de forma independiente, dejando a `CreditCardSummaryCard` sin ningún llamador en el código productivo de la app (solo lo invocan una preview temática y dos tests de instrumentación). Mantiene además una variable inútil `val nextBilling = remember(card.billingDay) { CreditCalculations.calculateNextDate(card.billingDay) }` en la línea 64 que nunca se lee en ninguna parte.
- Escenario concreto de fallo: Modificaciones en las reglas visuales o de negocio de tarjetas de crédito no impactan a `CreditCardSummaryCard` y viceversa, manteniendo medio millar de líneas de código y pruebas desacopladas de lo que realmente ve el usuario en producción.
- Fix mínimo: Si `DashboardCreditCardRow` es la implementación definitiva de Calm Emerald, eliminar `CreditCardSummaryCard.kt` y actualizar los tests de etiquetas para inspeccionar el componente productivo. Si se busca unificar bajo la propuesta HI-08, reemplazar `DashboardCreditCardRow` por `CreditCardSummaryCard` en `DashboardScreen.kt`.
- Ahorro estimado: 498 líneas
- Confianza: alta
- Cross-módulo: no

### [ACC4-10] Código muerto: AlertTogglePill en CalmEmeraldInstrumentComponents
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1761-1806
- Qué está mal: La función composable privada `AlertTogglePill` (46 líneas) no es llamada desde ninguna parte de `CalmEmeraldInstrumentComponents.kt` ni del resto del proyecto. Quedó huérfana cuando se descartaron los chips interactivos de umbrales 50/80/100 (como documenta el comentario en líneas 1104-1107).
- Escenario concreto de fallo: Código muerto residual que incrementa el peso del archivo sin aportar funcionalidad.
- Fix mínimo: Borrar la función `AlertTogglePill`.
- Ahorro estimado: 46 líneas
- Confianza: alta
- Cross-módulo: no

### [ACC4-11] Discrepancia en nombres de productos entre CardArt y CardStylePresets rompe la resolución de estilo en forProduct
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CardStylePresets.kt:170-183
- Qué está mal: `CardStylePresets.forProduct` busca coincidencias exactas entre el nombre suministrado y `preset.productName` o `preset.aliases`. `CardStylePresets.all` inicializa `verifiedBankPresets` dejando `aliases = emptySet()` para todos los presets bancarios. Al mismo tiempo, los nombres en `CardStylePresets` omiten el prefijo de la entidad (ej. `"CMR Visa"`, `"Visa Singular Signature"`, `"Tarjeta de Débito BBVA"`), mientras que `CardArtCatalog`, catálogos externos o el alias del usuario suelen incluirlo (`"Banco Falabella CMR Visa"`, `"Scotiabank Visa Singular Signature"`, `"Débito BBVA"`). Al no haber coincidencia exacta ni alias registrados, `forProduct` devuelve `null`.
- Escenario concreto de fallo: En `UnifiedInstrumentFormScreen:397` o `AccountDetailScreen:1026`, cuando una tarjeta llega con su nombre oficial de catálogo `"Banco Falabella CMR Visa"`, `CardStylePresets.forProduct("BANCO_FALABELLA", "Banco Falabella CMR Visa")` retorna `null`, perdiendo el estilo y vector oficial.
- Fix mínimo: Incluir los nombres completos de `CardArtCatalog` como `aliases` en la definición de cada preset bancario o normalizar eliminando el prefijo del nombre de la entidad emisora antes de comparar en `forProduct`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-12] Inconsistencia en altura mínima de tarjeta con arte ID-1 en InstrumentCardPreview
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/InstrumentCardPreview.kt:105,146
- Qué está mal: La altura mínima se define como `min = if (art != null && stylePreset?.cardType == CardStyleType.DEBIT) 0.dp else 160.dp`. Ambas tarjetas (débito y crédito) usan `CardArtFace` con la misma proporción ID-1 (`aspectRatio = 1.586f`). Si una tarjeta de crédito con arte se renderiza en un contenedor de ancho inferior a 254dp (donde la altura proporcional natural es menor a 160dp), la `Card` contenedora se expande forzadamente a 160dp mientras que la imagen interna se ajusta a su proporción, generando espacio vertical vacío asimétrico sólo para crédito.
- Escenario concreto de fallo: Al mostrar una tarjeta de crédito con arte en un panel estrecho (ej. pantalla dividida de 220dp de ancho, donde la altura proporcional es ~138dp), el contenedor queda inflado a 160dp dejando 22dp de franja transparente innecesaria.
- Fix mínimo: Aplicar `min = if (art != null) 0.dp else 160.dp` sin discriminar por tipo de tarjeta.
- Confianza: alta
- Cross-módulo: no

### [ACC4-13] BasicTextFields en fechas de tarjeta sin placeholder ni pista visual de entrada
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:2024-2036,2075-2087
- Qué está mal: En `CalmEmeraldDateInputsCard`, los campos `billingDayInput` y `dueDayInput` son `BasicTextField` sin `decorationBox` ni placeholder. A diferencia de `CalmEmeraldCreditLimitInputCard` (que muestra "0.00") y `CalmEmeraldLastFourDigitsCard` (que muestra "0000"), los campos de día de corte y pago quedan totalmente vacíos e invisibles excepto por un cursor estrecho en un cuadro de 48dp de ancho.
- Escenario concreto de fallo: El usuario entra al Paso 3 de alta de tarjeta de crédito y observa dos cajas en blanco bajo "Día de cierre" y "Día de pago" sin ninguna pista del formato esperado (ej. "DD" o "15").
- Fix mínimo: Añadir un `decorationBox` que muestre un placeholder tenue (ej. "15" y "05") cuando el campo esté vacío, consistente con los otros campos del paso 3.
- Confianza: alta
- Cross-módulo: no

### [ACC4-14] Formateo manual ad-hoc de línea de crédito en CalmEmeraldSuccessBottomSheet en lugar de MoneyText
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:2376-2377
- Qué está mal: En el bottom sheet de éxito al registrar tarjeta, la celda de línea total formatea el importe con concatenación manual: `if (LocalBalanceMasked.current) "••••" else "${if (card.currency == Currency.PEN) "S/" else "$"} ${formatMinorUnits(card.creditLimitMinorUnits)}"`, en lugar de invocar `MoneyText(Money(card.creditLimitMinorUnits, card.currency))` que ya implementa tipografía, colores, formato y soporte para enmascaramiento.
- Escenario concreto de fallo: Si se modifica el símbolo o formato de privacidad en `MoneyText`, esta pantalla mostrará un formato desalineado con el resto de la aplicación.
- Fix mínimo: Reemplazar el bloque `Text(...)` manual por `MoneyText(money = Money(card.creditLimitMinorUnits, card.currency), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-15] Fila de cuotas de tarjeta omite desglosar el importe de intereses pese a verificar su existencia
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CardInstallmentsSection.kt:271-286
- Qué está mal: `CardInstallmentRow` verifica `if (details.isNotEmpty() || item.interest.minorUnits > 0L)` para renderizar la fila inferior de detalles. Sin embargo, dentro de dicha fila únicamente se imprime `details.joinToString(" · ")` (descripción y fecha de corte) y `"Total: S/ total"`. El importe de los intereses de la cuota (`item.interest`) nunca se incluye en el texto visible, por lo que la condición `item.interest.minorUnits > 0L` no produce ninguna visualización del interés generado.
- Escenario concreto de fallo: Una cuota con intereses confirmada (ej. S/ 100 de capital y S/ 10 de interés) sin descripción ni fecha de corte renderiza un espacio en blanco en la columna izquierda y el total a la derecha, sin que el usuario pueda saber que se le cobraron S/ 10 de interés en esa cuota.
- Fix mínimo: Si `item.interest.minorUnits > 0L`, incorporar a `details` el texto `"Interés: ${formatMoney(item.interest)}"`.
- Confianza: alta
- Cross-módulo: no

### [ACC4-16] Harness obsoleto de captura de pantallas sin aserciones en androidTest
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/Issue48CopyScreenshotTest.kt:1-166
- Qué está mal: Este archivo no prueba componentes de producción ni contiene aserciones de regresión. Crea una pantalla composable interna sintética (`CopyComparisonScreen`) y exporta dos imágenes PNG al disco del dispositivo (`issue48-before.png` e `issue48-after.png`) que sirvieron como evidencia fotográfica temporal para el PR del Issue 48. Sigue ejecutándose en cada suite de pruebas instrumentadas consumiendo ciclos sin validar nada.
- Escenario concreto de fallo: Tiempo de ejecución de pruebas instrumentadas desperdiciado en renderizar y escribir a disco dos imágenes de una UI sintética que no existe en la app real.
- Fix mínimo: Eliminar el archivo `Issue48CopyScreenshotTest.kt`.
- Ahorro estimado: 166 líneas
- Confianza: alta
- Cross-módulo: no

### [ACC4-17] Sobrecarga de I/O en CardArtFaceTest por generación masiva de bitmaps
- Severidad: baja
- Categoría: rendimiento
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/components/CardArtFaceTest.kt:68-82,172-174
- Qué está mal: Durante la ejecución de `CardArtFaceTest`, se comprimen a PNG y escriben a almacenamiento 198 archivos de imagen en cada ejecución (78 imágenes en `verifiedBbvaAndScotiaFaces...` y 120 imágenes en `everyCatalogProductRendersFrontAndBackOnDevice`). Esto satura el disco en pruebas instrumentadas continuas.
- Escenario concreto de fallo: Lentitud significativa en la suite de `androidTest` por operaciones de compresión de imágenes y escritura síncrona en disco que no son parte de las aserciones de prueba.
- Fix mínimo: Condicionar la exportación de archivos a un flag booleano o de depuración (`exportImages = false` por defecto en CI), preservando únicamente las aserciones semánticas y de diseño en memoria.
- Confianza: alta
- Cross-módulo: no

### [ACC4-18] Falta de tests de robustez en parser y validación de TEA en borradores de compra a crédito
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditPurchaseDraft.kt:57-62
- Qué está mal: La conversión de TEA utiliza `toBigDecimal().movePointRight(2).intValueExact()`. No existen pruebas unitarias en `test/` que cubran entradas con más de 2 decimales, formatos científicos, comas decimales europeas o números negativos para validar que no lancen excepciones no controladas fuera del `runCatching`.
- Escenario concreto de fallo: Una modificación en `validateCreditPurchaseDraft` que altere el manejo de excepciones numéricas podría propagar `ArithmeticException` o `NumberFormatException` a la UI sin que ningún test unitario falle.
- Fix mínimo: Añadir un test unitario en `CreditPurchaseDraftValidationTest` con casos de prueba para: `"15.255"`, `"1e2"`, `"-5"`, `"12,5"`, y valores límites en 0 bps y 50_000 bps.
- Confianza: alta
- Cross-módulo: no

### [ACC4-19] Falta de tests para vinculación de tarjetas de débito de entidades distintas a BCP en edición de cuenta
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/accounts/presentation/EditAccountDebitRegistrationTest.kt:14-28
- Qué está mal: La suite `EditAccountDebitRegistrationTest` únicamente comprueba el escenario de una cuenta BCP. No existen tests para BanBif (donde ocurre la exclusión indebida), Scotiabank (donde la red se corrompe a VISA), ni para cuentas bancarias sin preset definido (OTHER).
- Escenario concreto de fallo: Los defectos ACC4-02 y ACC4-03 no fueron detectados por las pruebas existentes porque sólo se probó la entidad BCP con datos estáticos favorables.
- Fix mínimo: Añadir casos de prueba para `AccountPreset.SCOTIABANK` (afirmando que no fuerza VISA si el banco es Mastercard) y para `AccountPreset.GENERIC` / BanBif comprobando que se preserva la entidad correcta.
- Confianza: alta
- Cross-módulo: no

### [ACC4-20] Inyección de ObserveCardInstallments con valor por defecto hardcodeado en AccountsViewModel
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:95-96
- Qué está mal: El constructor de `@HiltViewModel class AccountsViewModel` define un valor por defecto que instancia directamente el caso de uso: `private val observeCardInstallmentsUseCase: ObserveCardInstallments = ObserveCardInstallments(financialInstrumentsRepository, java.time.Clock.systemUTC())`. En Hilt, el inyector no utiliza argumentos por defecto de Kotlin a menos que exista un Provider, y la instanciación manual salta el grafo de dependencias de Dagger fijando un reloj UTC no configurable en lugar de inyectar la instancia provista por Hilt.
- Escenario concreto de fallo: Si se configura un reloj simulado para pruebas de integración o Hilt cambia la provisión de `ObserveCardInstallments`, `AccountsViewModel` mantiene su instancia por defecto ligada a `Clock.systemUTC()`.
- Fix mínimo: Eliminar el valor por defecto en el constructor de `AccountsViewModel` e inyectar `observeCardInstallmentsUseCase: ObserveCardInstallments` como dependencia regular obligatoria en el `@Inject constructor`.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `CreditPurchaseDraft.kt:57-62` (`validateCreditPurchaseDraft`) → parser de TEA con decimales excesivos, exponentes o comas, verificando que devuelva error de validación sin propagar excepción aritmética no controlada (ACC4-18).
- `AccountsViewModel.kt:583-604` (`registerDebitForEditedAccount`) → registro de débito vinculado para cuentas BanBif, Scotiabank Mastercard y bancos no catalogados (ACC4-19).
- `CalmEmeraldInstrumentComponents.kt:1417-1531` (`CalmEmeraldCreditProductTree`) → colapso interactivo de grupos con producto seleccionado y entidades mono-red (ACC4-01).
- `InstrumentCardPreview.kt:107-136` → interacción de arrastre en contenedor con scroll vertical asegurando que no consuma el desplazamiento de la pantalla padre (ACC4-04).
- `CardInstallmentsSection.kt:215-289` (`CardInstallmentRow`) → visualización y legibilidad de cuotas con interés no nulo e importes de dos dígitos bajo escala de fuentes de accesibilidad.

## Lo que no revisé

Ninguno de los 31 archivos asignados (17 en main, 6 en test, 8 en androidTest) quedó sin lectura completa. Se leyeron fragmentos de `InstrumentFormModels.kt`, `UnifiedInstrumentFormScreen.kt`, `DashboardScreen.kt`, `ObserveCardInstallments.kt` y `DashboardEntryPreloader.kt` exclusivamente para comprender contratos, orígenes de datos y llamadores de los componentes auditados. No audité los formularios completos de alta ni el detalle de cuentas (asignados a ACC2 y ACC3), el repositorio ni workers (ACC5/ACC1). No ejecuté Gradle, ADB, tests instrumentados ni red; los escenarios de fallo fueron determinados analíticamente mediante revisión estática del código.

`git status --short` inicial mostraba sin seguimiento `.agents/skills/ponytail/`, `.claude/`, `skills-lock.json` y los reportes de otros workers en `docs/qa/audit-2026-11-11/`; ninguno fue modificado por esta auditoría.

## Dudas

- La convivencia entre `CreditCardSummaryCard.kt` (498 líneas) y `DashboardCreditCardRow` en `DashboardScreen.kt`: la propuesta HI-08 en la documentación de UX señalaba unificar sobre `CreditCardSummaryCard`, pero el rediseño productivo Calm Emerald utilizó `DashboardCreditCardRow`. Se requiere confirmación de diseño/producto para determinar si `CreditCardSummaryCard` debe adoptarse formalmente en el dashboard o borrarse para reducir deuda técnica.
- Regla de negocio en `registerDebitForEditedAccount`: se debe definir si la vinculación automática de débito debe permitir al usuario seleccionar la red (Visa/Mastercard) cuando la entidad ofrece ambas opciones, o si debe guiarse estrictamente por el `cardPreset` del banco emisor.

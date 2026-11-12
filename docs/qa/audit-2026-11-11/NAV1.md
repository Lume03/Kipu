# NAV1 — navigation (sin auth) + ui/component, ui/theme, ui/motion

Esta porción abarca el grafo de navegación integral de la aplicación Kipu (excluyendo el subsistema de autenticación de `SEC1`), el host retenido de pestañas inferiores (`RetainedRootTabs`), el flujo y ViewModel de importación de comprobantes (`ReceiptsNavigation`, `ReceiptFlowViewModel`), las rutas de captura, planificación, deudas, metas, presupuestos y calendario, así como el catálogo de componentes base de interfaz (`MoneyText`, `BrandLogo`, `KipuOptionalDateField`, `KipuFormRows`, `KipuStates`, etc.), tokens de movimiento (`ui/motion`) y el sistema de temas y barras del sistema (`CalmEmeraldTokens`, `KipuUiColors`, `ThemeTransition`, `KipuSystemBars`). Se asumió una carga de miles de usuarios con uno o dos dispositivos móviles Android, miles de movimientos registrados por usuario, y un uso offline intensivo con sincronizaciones periódicas y entradas por múltiples canales (deep links, intents de compartir comprobantes Yape/Plin y widgets).

## Hallazgos

### [NAV1-01] `ReceiptFlowViewModel` bloquea permanentemente a nuevos usuarios tras cambio de sesión (`owner` nunca se actualiza si no es nulo)
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:56, 65, 88
- Qué está mal: En `ReceiptFlowViewModel`, `owner` se inicializa con el `userId` actual de la sesión al crearse el ViewModel. En el bloque `init`, se observa `session.localAccess`, pero la línea 65 evalúa estrictamente `if (owner == null && access is LocalAccess.Available) owner = access.userId`. Si el usuario A cierra sesión y un usuario B inicia sesión en el mismo dispositivo, `owner` continúa siendo `"UserA"`. Como consecuencia, `hasAccess()` evalúa `(session.localAccess.value as? LocalAccess.Available)?.userId == owner`, lo que devuelve `false` (`"UserB" == "UserA"`).
- Escenario concreto de fallo: El usuario A cierra sesión y el usuario B inicia sesión en el mismo teléfono. Al entrar a la bandeja de comprobantes Yape, el ViewModel queda bloqueado con el mensaje `"Desbloquea la sesión propietaria para revisar comprobantes."` y emite `candidates = emptyList()`, impidiendo que el usuario B revise, importe o registre comprobantes hasta que la aplicación sea detenida por completo y se destruya el ViewModel.
- Fix mínimo: En `session.localAccess.collectLatest`, cuando `access is LocalAccess.Available`, si `access.userId != owner`, resetear el estado y actualizar `owner = access.userId` en lugar de requerir que `owner == null`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-02] `MaskedCardReference` lanza `IllegalArgumentException` no capturada en la composición ante formatos no canónicos de tarjeta
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/ui/component/MaskedCardReference.kt:30-33
- Qué está mal: `MaskedCardReference` ejecuta `require(Card.isValidLastFourDigits(sanitized))` directamente en el cuerpo del composable `@Composable`. Si una tarjeta posee dígitos malformados, vacíos o no numéricos (por ejemplo, importación de extracto bancario con datos incompletos, tarjeta en borrador o error de parseo OCR en recibos), Compose lanza `IllegalArgumentException` en el hilo principal durante la medición/layout, lo que produce un crash fatal de la aplicación.
- Escenario concreto de fallo: Una tarjeta en base de datos local o recibida de sincronización tiene `lastFourDigits = ""` o `"123"`. Al abrir la pantalla de Dashboard (`DashboardSections.kt:182`) o el resumen de tarjetas de crédito (`CreditCardSummaryCard.kt`), Compose invoca `MaskedCardReference`. La aserción falla y la aplicación crashea de inmediato (`FATAL EXCEPTION: main`).
- Fix mínimo: En lugar de `require(...)`, verificar `if (!Card.isValidLastFourDigits(sanitized))` y renderizar una máscara de degradación segura (`"•••• ----"` o `"•••• $sanitized"` truncado), garantizando que un fallo de formato visual nunca detenga la Activity.
- Confianza: alta
- Cross-módulo: no

### [NAV1-03] `ReceiptMovementEditorHost` carece de `BackHandler` y el botón Atrás expulsa al usuario del flujo completo de comprobantes
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:106-121
- Qué está mal: Cuando el usuario revisa un comprobante confirmado o duplicado y abre el editor de movimiento (`editingMovementId != null`), `ReceiptMovementEditorHost` compone `MovementEditorRoute` como overlay sin registrar un `BackHandler`. En `ReceiptFlowScreen`, el `BackHandler` local solo está habilitado si `candidate != null || state.savedCandidate != null`. Si el usuario abrió el editor desde la bandeja o un comprobante ya registrado, ningún composable intercepta el evento Atrás, por lo que el `NavController` desapila `RECEIPT_IMPORT_ROUTE` por completo.
- Escenario concreto de fallo: El usuario importa un comprobante duplicado o toca un comprobante registrado en la bandeja. Toca "Ver movimiento" para editarlo en el editor. Al estar dentro del editor, presiona la tecla o gesto Atrás del sistema esperando cerrar el editor: en lugar de cerrar el editor (`editingMovementId = null`), la navegación expulsa al usuario al dashboard o pantalla previa, abandonando la pantalla de comprobantes.
- Fix mínimo: En `ReceiptMovementEditorHost`, agregar `BackHandler(enabled = editingMovementId != null) { editingMovementId = null }`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-04] `RetainedRootTabs` causa `IndexOutOfBoundsException` al usar el valor por defecto `pageCount = 3` con los 4 tabs de la barra inferior
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/RetainedRootTabs.kt:27-68
- Qué está mal: `RetainedRootTabs` tiene la firma `fun RetainedRootTabs(selectedTabIndex: State<Int>, pageCount: Int = 3, ...)`. Sin embargo, `RootTab.COUNT` es 4 (Dinero, Movimientos, Planificación, Análisis). Si un llamador utiliza `RetainedRootTabs(selectedTabIndex) { ... }` omitiendo el parámetro opcional `pageCount`, o si el contenido emite 4 nodos de layout (measurables), `pageAlpha` y `pageOffset` se construyen con tamaño 3. Al iterar `pages.forEachIndexed` en el índice 3, la línea 66 invoca `pageOffset[index].value.toPx()`, arrojando `IndexOutOfBoundsException: Index: 3, Size: 3`.
- Escenario concreto de fallo: Cualquier componente o pantalla que instancie `RetainedRootTabs` usando su valor por defecto `pageCount = 3` para las 4 pestañas de la app o con 4 hijos sufre un crash inmediato al renderizar el cuarto hijo.
- Fix mínimo: Actualizar el valor por defecto a `pageCount: Int = RootTab.COUNT` y dimensionar `pageAlpha` y `pageOffset` dinámicamente según el número de measurables provistos en el layout.
- Confianza: alta
- Cross-módulo: no

### [NAV1-05] El Snackbar "Comprobante agregado" se cancela instantáneamente al consumir su propia clave en `LaunchedEffect`
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:165-170
- Qué está mal: En `ReceiptFlowScreen`, se observa el evento de comprobante agregado mediante `LaunchedEffect(state.addedEventId)`. La primera instrucción dentro del bloque es `viewModel.consumeAddedEvent()`, la cual muta de inmediato el estado del ViewModel poniendo `addedEventId = null`. En Compose, mutar la clave sobre la que se ejecuta el `LaunchedEffect` cancela de forma inmediata la corrutina en ejecución. Por tanto, `snackbar.showSnackbar("Comprobante agregado", "Ver", ...)` es cancelado antes de que el snackbar llegue a dibujarse o pueda ser pulsado.
- Escenario concreto de fallo: El usuario escanea o importa un comprobante Yape exitosamente. El ViewModel genera `addedEventId = id`. La UI inicia `LaunchedEffect`, consume el evento inmediatamente, lo que cancela la corrutina del Snackbar. El usuario nunca ve la notificación "Comprobante agregado" ni puede pulsar la acción "Ver" para abrir el comprobante recién agregado.
- Fix mínimo: Consumir el evento `viewModel.consumeAddedEvent()` después de que `snackbar.showSnackbar` retorne su resultado, evitando cancelar la corrutina en vuelo.
- Confianza: alta
- Cross-módulo: no

### [NAV1-06] `ThemeTransition` retiene la `Activity` y un `Bitmap` de pantalla completa en memoria estática sin reciclaje explícito
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/ui/theme/ThemeTransition.kt:31, 44, 72-73, 91-96
- Qué está mal: `ThemeTransition` es un objeto singleton (`object`). En `show()`, guarda una referencia a un `ImageView(activity)` que contiene un `Bitmap` ARGB_8888 del tamaño completo de la pantalla (~18 MB en pantallas 1440x3120). En `clear()`, se remueve la vista y se anula `overlay`, pero nunca se invoca `bitmap.recycle()`. Además, si la actividad se recrea durante la transición o el cambio de tema, la referencia estática a `ImageView` retiene la instancia destruida de `Activity` hasta el timeout de seguridad (1.2s) o indefinidamente si ocurre una excepción previa.
- Escenario concreto de fallo: En dispositivos con pantallas QHD+ o memoria RAM ajustada (2–3 GB), cambios repetidos de tema o rotación durante la transición provocan retención de bitmaps de 18 MB en la memoria nativa, disparando pausas de Garbage Collector (jank) o riesgo de `OutOfMemoryError`.
- Fix mínimo: En `clear()`, extraer el drawable/bitmap del `ImageView` antes de removerlo y llamar a `recycle()`. Anular las referencias inmediatamente y no retener `ImageView` en variables estáticas más allá del frame de fade.
- Confianza: alta
- Cross-módulo: no

### [NAV1-07] `KipuSystemBars` re-ejecuta `activity.enableEdgeToEdge()` en cada recomposición dentro de un `SideEffect`
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/ui/theme/KipuSystemBars.kt:46-59
- Qué está mal: `KipuSystemBars` se invoca en la raíz de `KipuTheme`. En su interior, `SideEffect` ejecuta `activity.enableEdgeToEdge(...)` en cada frame en que `KipuTheme` se recompone. `enableEdgeToEdge()` es una operación de ciclo de vida de la Activity diseñada para llamarse una sola vez en `Activity.onCreate()`; re-ejecutarla en cada recomposición de Compose reinstala listeners de insets, recrea estilos del sistema y compite contra el `WindowInsetsControllerCompat` que `MainActivity.kt:304` configura por su cuenta.
- Escenario concreto de fallo: Cada vez que cambia un estado global o se anima la UI dentro de `KipuTheme`, `SideEffect` llama a `enableEdgeToEdge()`, forzando al framework de Android a re-evaluar los insets de la ventana y los estilos del sistema, generando trabajo redundante en el hilo de UI.
- Fix mínimo: Controlar los system bars en Compose utilizando `WindowCompat.getInsetsController()` solo cuando el valor de `darkTheme` cambie (mediante `LaunchedEffect(darkTheme)` o `DisposableEffect`), sin volver a invocar `activity.enableEdgeToEdge()`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-08] `KipuSkeletonBlock` lee `.value` de animación en la fase de composición, invalidando y recomponiendo la UI a 60-120 fps
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/ui/component/KipuStates.kt:69-79
- Qué está mal: En `KipuSkeletonBlock`, `alpha` se extrae llamando a `.value` en el cuerpo del composable: `val alpha = ...animateFloat(...).value`. Luego se pasa a `graphicsLayer { this.alpha = alpha }`. Leer `.value` durante la fase de composición suscribe al composable a los cambios del `State<Float>`, obligando a Compose a recomponer el bloque esqueleto completo en cada frame del pulso (60 o 120 veces por segundo).
- Escenario concreto de fallo: En pantallas que muestran listas de esqueletos de carga (por ejemplo, el historial de movimientos o el dashboard con múltiples cuentas y tarjetas cargando), cada bloque esqueleto se recompone 60-120 veces por segundo, disparando el uso de CPU y consumiendo batería innecesariamente.
- Fix mínimo: Retener la referencia `val alphaState = transition.animateFloat(...)` y leer `alphaState.value` únicamente dentro de la lambda diferida de `graphicsLayer { this.alpha = alphaState.value }`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-09] `GoalsNavigation` accede a `ACCOUNTS_DASHBOARD_ROUTE` con `getBackStackEntry` sin verificar su presencia en la pila
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/GoalsNavigation.kt:38-39, 63-64
- Qué está mal: En las rutas `GOAL_CREATE_ROUTE` y `GOAL_EDIT_ROUTE`, se ejecuta `val ownerEntry = remember(backStackEntry) { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }` sin comprobar si `ACCOUNTS_DASHBOARD_ROUTE` existe en la pila. En contraste con `AccountsNavigation.kt:150-154` (que utiliza `previousBackStackEntry?.takeIf { ... } ?: hiltViewModel(backStackEntry)`), si la ruta se abre desde un deep link frío, notificación o tras una limpieza de pila, `getBackStackEntry` lanza `IllegalArgumentException` no capturada.
- Escenario concreto de fallo: Si una meta se abre para edición desde un flujo donde el dashboard no está en el backstack, la llamada a `navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE)` lanza `IllegalArgumentException: No destination with route accounts/dashboard is on the NavController's back stack` y la app crashea.
- Fix mínimo: Usar `runCatching { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }.getOrNull()` y si es nulo obtener el ViewModel desde `backStackEntry`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-10] `DebtsNavigation` no provee interfaz de navegación ni acción de retorno cuando la deuda no existe (`state.debt == null`)
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/DebtsNavigation.kt:176-180, 248-252
- Qué está mal: En `DEBT_DETAIL_ROUTE` y `DEBT_SETTLE_ROUTE`, cuando `state.debt == null` y no está cargando, se emite un simple `Text(state.errorMessage ?: "No se encontró esta deuda.", ...)` sin estar contenido en un `Scaffold`, sin `TopAppBar` y sin botón de volver. Si el usuario llegó a esta ruta desde una notificación de recordatorio (donde `MainActivity.kt:656-660` limpió la pila con `popUpTo(0)`), la pantalla queda en un callejón sin salida (deadlock de navegación) donde la única salida es pulsar el botón Atrás del sistema que cierra la app.
- Escenario concreto de fallo: Usuario abre una notificación de recordatorio para una deuda que fue cancelada o eliminada en otro dispositivo sincronizado. La pantalla muestra un texto rojo en la esquina superior izquierda sobre fondo por defecto sin barra de navegación ni botón de retorno. Al presionar Atrás del sistema, la app se cierra.
- Fix mínimo: Envolver el estado de error en un `Scaffold` con botón de navegación hacia atrás (`onNavigateBack = { if (!navController.popBackStack()) navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) }`) o mostrar `KipuEmptyState`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-11] `CaptureNavigation` pierde el mensaje de confirmación (`leave`) cuando se accede a un candidato mediante deep link frío
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/navigation/CaptureNavigation.kt:76-84
- Qué está mal: En `CAPTURE_CANDIDATE_ROUTE`, la función `leave` ejecuta: `navController.previousBackStackEntry?.savedStateHandle?.set(CAPTURE_MESSAGE_KEY, message)`. Luego evalúa `if (!navController.popBackStack()) { navController.navigate(CAPTURE_INBOX_ROUTE) { popUpTo(CAPTURE_CANDIDATE_ROUTE) { inclusive = true } } }`. Si el usuario ingresó por deep link (`kipu://capture/candidate/{id}`), `previousBackStackEntry` es `null`. El mensaje nunca se guarda en el savedStateHandle de la bandeja y se pierde silenciosamente.
- Escenario concreto de fallo: Usuario pulsa una notificación de comprobante capturado por OCR (`kipu://capture/candidate/{id}`). Revisa el comprobante y confirma el registro. La función `leave` intenta escribir el mensaje en `previousBackStackEntry` (que es nulo), luego abre la bandeja. La bandeja se muestra sin el banner de feedback "Movimiento registrado".
- Fix mínimo: Si `!navController.popBackStack()`, pasar el mensaje al `currentBackStackEntry` de la nueva navegación: `navController.currentBackStackEntry?.savedStateHandle?.set(CAPTURE_MESSAGE_KEY, message)`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-12] `SettingsNavigation` redirige a `MOVEMENTS_HISTORY_ROUTE` en lugar de `ACCOUNTS_DASHBOARD_ROUTE` al finalizar onboarding biométrico
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/SettingsNavigation.kt:37-40
- Qué está mal: Al completar el paso biométrico del onboarding (`BIOMETRIC_ROUTE`), la acción de continuar navega a `MOVEMENTS_HISTORY_ROUTE` con `popUpTo(PLAN_SELECTION_ROUTE) { inclusive = true }`. Como `ACCOUNTS_DASHBOARD_ROUTE` nunca se incluyó en la pila, `movements/history` se convierte en la raíz del backstack en vez del dashboard. Cuando el usuario pulsa luego "Dinero" en la barra inferior, el dashboard se apila encima de `movements/history`, impidiendo salir de la app con el botón Atrás desde "Dinero".
- Escenario concreto de fallo: Usuario nuevo finaliza el onboarding biométrico. Aparece la pantalla de historial de movimientos. Toca "Dinero" en la barra inferior. En lugar de estar en el inicio canónico, ahora el botón Atrás del sistema lo regresa a "Movimientos" en vez de salir de la app.
- Fix mínimo: Cambiar la navegación de finalización de onboarding a `ACCOUNTS_DASHBOARD_ROUTE` con `backStackEntry.savedStateHandle[ROOT_TAB_KEY] = RootTab.MONEY`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-13] `DatePickerDialog` y `TimePickerDialog` nativos de Android en `ReceiptsNavigation` se destruyen y pueden fugar ventana ante cambios de configuración
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:495-498, 509-512
- Qué está mal: En `ReceiptMetadataEditor`, para editar la fecha y hora de un comprobante se instancian directamente diálogos del framework clásico (`android.app.DatePickerDialog(context, ...).show()` y `android.app.TimePickerDialog(context, ...).show()`), pasando el `Context` de la composición. Estos diálogos no sobreviven a la recreación de la Activity (por rotación o cambio de tema), provocando posibles excepciones de ventana huérfana (`BadTokenException`) o pérdida del estado en edición, además de no respetar el tema visual Material 3 de Kipu.
- Escenario concreto de fallo: Usuario está revisando un comprobante en `ReceiptsNavigation` y abre el diálogo de fecha. Mientras el diálogo está abierto, el usuario gira el teléfono o cambia el modo oscuro del sistema. El diálogo desaparece, se pierde la selección y se fuga el contexto de la Activity anterior en los logs de Android (`Activity has leaked window`).
- Fix mínimo: Utilizar componentes Compose como `KipuOptionalDateField` / `androidx.compose.material3.DatePickerDialog` que manejan su ciclo de vida y recomposición de manera reactiva y segura.
- Confianza: alta
- Cross-módulo: no

### [NAV1-14] `MoneyText` oculta el símbolo de moneda en modo privacidad cuando el parámetro `currencySymbol` es nulo pero el texto lo incluye
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/ui/component/MoneyText.kt:38-40
- Qué está mal: En `formatMoneyTextDisplay`, cuando `isMasked == true`, la lógica evalúa `if (currencySymbol != null) "$currencySymbol ••••••" else "••••••"`. Si un llamador invoca `MoneyText(amount = "S/ 120.00")` sin pasar explícitamente `currencySymbol = "S/"`, el texto se enmascara como `"••••••"` perdiendo por completo la indicación de moneda. En cuentas bimonetarias (PEN y USD), el usuario pierde visibilidad de qué divisa corresponde a cada monto oculto.
- Escenario concreto de fallo: En pantallas que formatean montos concatenados o rangos ("S/ 50.00"), al activar el modo de ocultar saldos el texto se convierte en `"••••••"` en lugar de `"S/ ••••••"`, impidiendo al usuario saber si el monto ocultado es en Soles o Dólares.
- Fix mínimo: En `formatMoneyTextDisplay`, extraer el prefijo de moneda del texto (`"S/"` o `"$"` si está presente) antes de enmascarar si `currencySymbol` es nulo.
- Confianza: alta
- Cross-módulo: no

### [NAV1-15] Función muerta `ReceiptFlowViewModel.manual()` (5 líneas no invocadas en ningún flujo)
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:185-189
- Qué está mal: La función `fun manual() = work { consumeImport(); val result = store.import(...) }` no es llamada desde ninguna pantalla, navegación ni test del repositorio. La creación manual de movimientos se realiza exclusivamente a través de los formularios de `MovementsNavigation` o `QuickMovementBottomSheet`.
- Escenario concreto de fallo: Código muerto que debe mantenerse y compilarse sin uso real.
- Fix mínimo: Eliminar la función `manual()` de `ReceiptFlowViewModel.kt`.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 5 líneas

### [NAV1-16] `PlanningColors.kt` es un archivo redundante con una función envoltorio idéntica a `rememberCalmEmeraldColors()`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/ui/theme/PlanningColors.kt:1-10
- Qué está mal: `PlanningColors.kt` define `rememberPlanningColors()` que contiene únicamente `val base = rememberCalmEmeraldColors(); return base`. No añade ni modifica ningún token visual respecto a `rememberCalmEmeraldColors()`.
- Escenario concreto de fallo: Indirección innecesaria que obliga a importar y mantener un archivo adicional de 10 líneas.
- Fix mínimo: Sustituir las llamadas a `rememberPlanningColors()` por `rememberCalmEmeraldColors()` y eliminar el archivo `PlanningColors.kt`.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 10 líneas (elimina 1 archivo)

### [NAV1-17] Recompilación redundante de expresiones regulares en `normalizeBrandKey` durante cada renderizado de `BrandLogo`
- Severidad: baja
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/ui/component/BrandLogo.kt:67, 74-80
- Qué está mal: En `normalizeBrandKey`, las expresiones regulares `"\\p{Mn}+".toRegex()` y `"[^a-z0-9]+".toRegex()` se compilan en cada invocación. Además, `KipuBrand.from()` evalúa `normalizeBrandKey(brand.name)` dinámicamente en un loop para cada entrada del enum. En listas con decenas o cientos de transacciones o cuentas, cada item re-compila los patrones Regex en el hilo de UI.
- Escenario concreto de fallo: Desplazamiento rápido por una lista larga de cuentas o movimientos en teléfonos de gama baja causa micro-pausas (jank) al compilar repetidamente patrones Regex.
- Fix mínimo: Declarar los `Regex` como constantes `private val` de nivel superior y precomputar el mapa o clave normalizada de cada `KipuBrand`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-18] `KipuSegmentedControl` usa `.clickable(role = Role.RadioButton)` en lugar de `.selectable()` dentro de un `.selectableGroup()`
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/ui/component/KipuFormRows.kt:324, 339-340
- Qué está mal: `KipuSegmentedControl` aplica `Modifier.selectableGroup()` en el contenedor `Row`, pero los elementos individuales aplican `Modifier.clickable(role = role) { onSelect(option) }` junto con `semantics { this.selected = isSelected }`. Las directrices de accesibilidad de Jetpack Compose indican que los hijos directos de un `selectableGroup` deben usar `Modifier.selectable(selected = isSelected, role = role, onClick = ...)`, garantizando que servicios como TalkBack anuncien correctamente el grupo de selección y el estado de botón radial.
- Escenario concreto de fallo: Usuarios con lector de pantalla TalkBack pueden escuchar un anuncio genérico de botón en lugar de la semántica de opción de radio seleccionada dentro de un grupo de opciones mutuamente excluyentes.
- Fix mínimo: Reemplazar `.clickable(...)` y `.semantics { this.selected = isSelected }` por `.selectable(selected = isSelected, enabled = enabled && !isSelected, role = role, onClick = { onSelect(option) })`.
- Confianza: alta
- Cross-módulo: no

### [NAV1-19] Divergencia y duplicación de tokens de color en `Color.kt`, `KipuUiColors.kt` y `CalmEmeraldTokens.kt`
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/ui/theme/Color.kt:50-52, KipuUiColors.kt:82-86, CalmEmeraldTokens.kt:53-57
- Qué está mal: Coexisten tres definiciones semánticas para los mismos colores financieros: `KipuIncome` / `KipuExpense` en `Color.kt`, `KipuUiColors.positive` / `debt` en `KipuUiColors.kt`, y `incomeText` / `expenseText` en `CalmEmeraldTokens.kt`. Ciertos componentes leen de uno u otro, lo que dificulta auditorías de contraste WCAG y refactorizaciones temáticas.
- Escenario concreto de fallo: Cambios en el diseño visual de colores semánticos pueden actualizarse en `CalmEmeraldTokens` pero quedar desactualizados en componentes que lean directamente `KipuIncome` o `rememberKipuColors().positive`.
- Fix mínimo: Unificar `KipuIncome` y `KipuExpense` para que deleguen directamente en `CalmEmeraldColors`.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 8 líneas

## Código de riesgo sin tests
1. `ReceiptMovementEditorHost` (`app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:106-121`): No existe prueba que valide el comportamiento del botón Atrás cuando `editingMovementId != null`. Test mínimo: abrir el editor sobre `ReceiptMovementEditorHost`, disparar el evento Atrás y verificar que `editingMovementId` vuelve a `null` mientras la pantalla contenedora permanece intacta en el NavController.
2. `MaskedCardReference` (`app/src/main/java/com/kipu/app/ui/component/MaskedCardReference.kt:31-33`): No existe prueba que compruebe la resistencia a datos corruptos o no numéricos. Test mínimo: renderizar `MaskedCardReference` con `""`, `"123"` y `"ABCD"` verificando que degrade sin lanzar `IllegalArgumentException`.
3. `RetainedRootTabs` (`app/src/main/java/com/kipu/app/navigation/RetainedRootTabs.kt:27`): No existe prueba unitaria con la firma por defecto `pageCount = 3` recibiendo 4 pestañas. Test mínimo: invocar `RetainedRootTabs(selectedTabIndex)` con 4 composables hijos y verificar que no se produzca `IndexOutOfBoundsException`.
4. `ReceiptFlowViewModel` (`app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:56-88`): No existe prueba de cambio de sesión/usuario mientras el ViewModel vive. Test mínimo: emitir `LocalAccess.Available("userA")` seguido de `LocalAccess.Available("userB")` y asegurar que `hasAccess()` es `true` y los datos cargados corresponden a `userB`.
5. `ThemeTransition` (`app/src/main/java/com/kipu/app/ui/theme/ThemeTransition.kt:38-96`): No existe prueba que verifique que el bitmap de captura de pantalla sea reciclado y liberado tras la animación o al destruirse la actividad.

## Lo que no revisé
- Se revisaron los 53 archivos de código principal (`main`), los 11 archivos de tests unitarios (`test`), los 16 archivos de tests instrumentados (`androidTest`), el archivo de `androidTestDebug` y los archivos de scaffolding en `debug` y `lab` pertenecientes al alcance exacto asignado a NAV1.
- No se ejecutaron pruebas en dispositivos físicos o emuladores adb ni builds locales de Gradle de acuerdo con las reglas duras de auditoría estática.
- Las implementaciones de lógica de negocio y presentación de transacciones dentro de `MovementEditorRoute` y `QuickMovementViewModel` fueron inspeccionadas únicamente en el punto de contacto con la navegación, delegando su auditoría a `MOV3` y `MOV4`.

## Dudas
1. ¿Por qué en `SettingsNavigation.kt:37-40`, al finalizar el paso biométrico del onboarding (`BIOMETRIC_ROUTE`), se fuerza la navegación a `MOVEMENTS_HISTORY_ROUTE` en lugar de abrir el dashboard principal `ACCOUNTS_DASHBOARD_ROUTE` como indica la especificación general del producto?
2. En `ReceiptFlowViewModel.kt:211`, `savedCandidate = selected.takeIf { next != null }` omite la tarjeta de confirmación de registro ("Registrado") al procesar el último comprobante de la cola. ¿Fue una decisión intencionada de UX para regresar inmediatamente a la bandeja vacía, o debería mostrarse siempre el feedback de confirmación?

# MOV5 — movements/presentation: selectores (categoría pastel, cuenta)

Esta porción implementa los componentes modales y filas de selección para movimientos financieros: el selector global de instrumentos (cuentas bancarias, billeteras digitales, efectivo, tarjetas de crédito y tarjetas de débito) y el selector jerárquico de categorías en estilo pastel andino (con acordeones expandibles, búsqueda en vivo y creación contextual de categorías). Se asumió una carga de miles de usuarios con uno o dos dispositivos, miles de movimientos por usuario y uso frecuente sin conexión en soles y dólares.

## Hallazgos

### [MOV5-01] Crash por `ArrayIndexOutOfBoundsException` con hash codes negativos en selección de paleta pastel
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:271
- Qué está mal: En `resolvePastelToken`, la selección de chip pastel por defecto ante nombres no reconocidos ejecuta `val index = Math.abs(name.hashCode()) % BaseRecommendedPastelChips.size`. En la JVM, `Math.abs(Int.MIN_VALUE)` desborda el rango de enteros con signo y retorna `-2147483648`. Al calcular el módulo (`% 12`), el resultado es `-8`, lo que lanza `ArrayIndexOutOfBoundsException` al indexar la lista inmutable y aborta la composición de la UI.
- Escenario concreto de fallo: Si una categoría existente o sincronizada tiene un nombre cuyo hash code sea exactamente `Integer.MIN_VALUE` (o cualquier caso de desbordamiento de `Math.abs`), abrir el selector de categorías lanza de inmediato `java.lang.ArrayIndexOutOfBoundsException: Index -8 out of bounds for length 12` y cierra la aplicación.
- Fix mínimo: Reemplazar por `(name.hashCode() and 0x7FFFFFFF) % BaseRecommendedPastelChips.size` o `Math.floorMod(name.hashCode(), BaseRecommendedPastelChips.size)`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-02] Test instrumentado roto: aserción contra texto desactualizado ("Créd. disp.:" vs "Crédito disponible:")
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheetTest.kt:62
- Qué está mal: El commit `66a8193ac` modificó el texto visible de la fila de tarjeta de crédito en `AccountSelectorBottomSheet.kt:770` de `"Créd. disp.: "` a `"Crédito disponible: "`. No obstante, el test instrumentado `AccountSelectorBottomSheetTest.kt:62` no fue actualizado y continúa buscando el nodo con substring `"Créd. disp.:"`, fallando inmediatamente con `AssertionError`.
- Escenario concreto de fallo: Al ejecutar la suite de pruebas instrumentadas en `AccountSelectorBottomSheetTest`, el método `creditCardRowShowsAvailableCreditCopyAndAccessibleMeaning` falla con `AssertionError: Failed to find node with text containing 'Créd. disp.:'`.
- Fix mínimo: Actualizar la aserción del test a `compose.onNodeWithText("Crédito disponible:", substring = true).assertIsDisplayed()`, o revertir el texto a `"Créd. disp.: "` si se prioriza evitar desbordamiento en pantallas pequeñas de 360dp según la especificación de Issue 48.
- Confianza: alta
- Cross-módulo: no

### [MOV5-03] En tema oscuro, los chips pastel guardan el fondo oscuro en BD en vez del código pastel canónico y no marcan selección
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:215-250 y 2346-2364
- Qué está mal: En `CustomPastelColorDialog`, `recommendedPastelChips` provee tokens adaptados por `themed(base)` en tema oscuro, donde `containerColor` es un fondo oscuro (ej. `Color(0xFF14532D)`), pero `hexCode` se conserva como el pastel claro (`"#DCFCE7"`). Al pulsar un chip, el diálogo extrae el HSV de `token.containerColor` (`#14532D`). Como `isSelected = currentHex.equals(token.hexCode)` compara `#14532D` con `#DCFCE7`, `isSelected` resulta siempre `false` en tema oscuro (el check no aparece nunca). Además, al guardar, se persiste `#14532D` en base de datos; cuando el usuario cambia al tema claro, la categoría queda con fondo oscuro e ilegible.
- Escenario concreto de fallo: Un usuario con tema oscuro crea una subcategoría y selecciona el chip "Mint". El check no se dibuja. Al confirmar y guardar, el color persistido en Room y Supabase es `#14532D` en vez de `#DCFCE7`. Cuando el usuario usa la aplicación en tema claro, su categoría muestra un color verde oscuro opaco en lugar del tono pastel menta previsto.
- Fix mínimo: En `CustomPastelColorDialog.kt:2358`, calcular el HSV a partir del valor hexadecimal canónico: `val hsv = PastelColorUtils.colorToHsv(PastelColorUtils.hexToColor(token.hexCode))`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-04] En `directSelectionMode`, tocar una fila raíz con subcategorías confirma inmediatamente la raíz y cierra la hoja
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:955-962
- Qué está mal: En `BrowseCategoriesView`, el click en la fila raíz ejecuta:
  ```kotlin
  onSelectCategory(root)
  if (directSelectionMode) {
      onConfirmSelection(root)
  } else if (children.isNotEmpty() && !multiSelect) {
      onToggleRootExpand(root.id)
  }
  ```
  Al estar en `directSelectionMode = true` (modo activo por defecto en `QuickMovementScreen`, `MovementEditorSheet` e `InstallmentSimulatorScreen`), tocar el cuerpo de una fila raíz llama de inmediato a `onConfirmSelection(root)`, seleccionando la categoría padre y cerrando la hoja. La rama `else if` que expandiría el acordeón nunca se alcanza. La única manera de expandir las subcategorías es pulsar con precisión el pequeño botón chevron en el borde derecho.
- Escenario concreto de fallo: Un usuario en registro rápido desea asignar la subcategoría "Supermercados" dentro de "Alimentación". Toca la fila "Alimentación" esperando ver sus opciones: el selector se cierra de inmediato y asigna la categoría raíz "Alimentación", sin permitirle ver ni seleccionar la subcategoría.
- Fix mínimo: En el click de la fila raíz, si `children.isNotEmpty()`, ejecutar `onToggleRootExpand(root.id)` para expandir el acordeón en lugar de confirmar de inmediato, reservando la confirmación directa para hojas sin hijos o añadiendo una acción explícita.
- Confianza: alta
- Cross-módulo: no

### [MOV5-05] Etiqueta "Actual" en categorías es semánticamente errónea: se asigna a cualquier ítem recién pulsado
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:552, 926, 1072 y 1297
- Qué está mal: `BrowseCategoriesView` recibe `currentIds = effectiveSelectedIds`, el cual muta con cada clic del usuario. Además, en las líneas 926 y 1072 se evalúa `root.id in currentIds || (!multiSelect && isRootSelected)`. Esto hace que "Actual" no indique la categoría previa del movimiento, sino que se convierta en un duplicado exacto del estado seleccionado, saltando en tiempo real al ítem recién pulsado junto con el icono de check.
- Escenario concreto de fallo: Al editar un movimiento que tenía categoría "Restaurantes", el usuario abre el selector y toca "Transporte". De inmediato, "Restaurantes" pierde la etiqueta "Actual" y "Transporte" pasa a mostrar "Actual", sugiriendo falsamente que "Transporte" ya estaba guardada.
- Fix mínimo: Guardar `val initialCategory = remember(selectedCategoryId) { selectedCategoryId }` y pasar `setOfNotNull(initialCategory)` como `currentIds`, eliminando la condición redundante `|| (!multiSelect && isRootSelected)`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-06] Pantalla de creación de categoría dentro del selector modal carece de botón "Atrás"
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:602-646
- Qué está mal: Al pasar a `PastelFlowScreen.CREATE_SUBCATEGORY`, `CategoryFormContent` se invoca sin `showBack = true`. Como `showBack` es `false` por defecto en `CategoryFormContent`, no se muestra la flecha de volver. El único botón en la cabecera es la "X" (`onDismiss`), que cierra toda la hoja en lugar de regresar al explorador de categorías.
- Escenario concreto de fallo: El usuario pulsa "+ Nueva categoría", cambia de opinión y quiere volver a la lista de categorías. Al no haber botón de retorno, presionar la "X" descarta el picker por completo obligándolo a reabrirlo desde la pantalla de movimiento.
- Fix mínimo: Pasar `showBack = true` y `selectOnSave = true` en la invocación de `CategoryFormContent`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-07] Acordeón de la subcategoría seleccionada no se expande al entrar si los datos llegan asíncronamente
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:496-501
- Qué está mal: `var expandedRootIds by remember { mutableStateOf(...) }` no define claves de recuerdo (`remember(categories, selectedCategoryId)`). Si en el primer frame de renderizado `categories` llega vacía mientras el flujo de Room/ViewModel termina de cargar, `currentParents` se evalúa como vacío y nunca se vuelve a calcular cuando la lista de categorías se puebla.
- Escenario concreto de fallo: Al abrir la edición de un movimiento con una subcategoría seleccionada, la consulta a base de datos tarda unos milisegundos en emitir: la hoja se dibuja con todos los acordeones cerrados y la subcategoría actual queda escondida dentro del acordeón colapsado.
- Fix mínimo: Añadir claves a `remember`: `remember(categories, selectedCategoryId, selectedCategoryIds)`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-08] Error de truncamiento y degradación cromática en ciclo HSV <-> RGB y Color a HEX
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:416-418, 1949-1951, 2268, 2291 y 2314
- Qué está mal: `PastelColorUtils.colorToHex` y los campos numéricos R, G, B convierten los canales float de Compose mediante `(x * 255).toInt()`. Al convertir float IEEE 754 (donde `128f / 255f * 255f = 127.99999f`), `.toInt()` trunca hacia abajo a 127 en vez de redondear a 128 (generando `#7F` en vez de `#80`). Modificar un canal en el modal altera los otros canales hacia abajo sucesivamente.
- Escenario concreto de fallo: En el modal de color personalizado, el usuario tiene un color con G=128 y B=64. Al cambiar el valor de R, los campos de G y B parpadean y disminuyen a 127 y 63 sin que el usuario los haya tocado.
- Fix mínimo: Reemplazar `(x * 255).toInt()` por `(x * 255f).roundToInt().coerceIn(0, 255)`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-09] Resumen de `IconCatalogDialog` muestra "TV Streaming" erróneamente cuando el icono inicial no está en el catálogo hardcodeado
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1684-1687
- Qué está mal: `selectedCatalogItem = remember(tempSelectedId) { CATALOG_ICONS.find { ... } ?: CATALOG_ICONS.first() }`. Si el icono inicial recibido no está entre los 48 de `CATALOG_ICONS` (por ejemplo `"pie_chart"` en presupuestos o `"category"` genérico), el elvis selecciona `"tv"`. El banner inferior afirma que el icono elegido es "TV Streaming", contradiciendo el estado real.
- Escenario concreto de fallo: En `BudgetFormScreen`, al abrir el catálogo de iconos (iniciando en `"pie_chart"`), el diálogo indica abajo "Icono seleccionado: TV Streaming / Categoría: Películas & Series", a pesar de que "TV Streaming" no fue elegido.
- Fix mínimo: Proveer un `CatalogIconItem` dinámico de respaldo si no está en la lista o mostrar `"Icono actual: $tempSelectedId"` con `resolveCatalogIcon(tempSelectedId)`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-10] En `AccountSelectorBottomSheet`, las tarjetas de débito ignoran `cardsAllowed = false` en `ALL` y en el conteo total
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:236, 244 y 251
- Qué está mal: Cuando `cardsAllowed` es `false` (propósitos `INCOME_DESTINATION` o `TRANSFER_SOURCE`), `cards` se vacía, pero `debitCards` no se condiciona por `cardsAllowed`. `cardsCount` suma `debitCards.size`, y `displayedItems` en `InstrumentFilter.ALL` incluye `debitCards`, mientras que la píldora "Tarjetas" se oculta.
- Escenario concreto de fallo: Si se pasa `debitCards` con `purpose = INCOME_DESTINATION`, las tarjetas de débito aparecen listadas en la pestaña "Todas", pero la píldora "Tarjetas" no existe y el conteo de la pestaña "Todas" supera la suma de las pestañas hijas.
- Fix mínimo: Filtrar `debitCards` con `if (cardsAllowed) debitCards else emptyList()` tanto en `cardsCount` como en `displayedItems`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-11] Reasignación ineficiente O(N*M) de `categories.filter` en cada composición de fila de la LazyColumn
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:923
- Qué está mal: Dentro de `items(roots)` en la `LazyColumn`, cada composición de categoría raíz ejecuta `val children = categories.filter { it.parentCategoryId == root.id }`. En una lista con decenas de categorías, el scroll genera filtrados lineales completos repetidos en cada frame.
- Escenario concreto de fallo: Lag y jank de scroll observable al desplazarse rápidamente por la lista de categorías en dispositivos de gama baja con muchas categorías creadas.
- Fix mínimo: Pre-agrupar fuera de la LazyColumn con `val childrenByParent = remember(categories) { categories.filter { it.parentCategoryId != null }.groupBy { it.parentCategoryId } }` y acceder con `childrenByParent[root.id].orEmpty()`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-12] Acoplamiento arquitectónico invertido: modales de categorías residen en `movements` y generan dependencia circular con `categories`
- Severidad: media
- Categoría: dividir
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1479-2418
- Qué está mal: `PastelCategoryPickerFlow.kt` (en `movements`) contiene modales completos de configuración de categorías (`ParentCategoryChooserDialog`, `IconCatalogDialog`, `CustomPastelColorDialog`) y un catálogo propio de iconos (`CATALOG_ICONS`). Son importados por `feature/categories` y `feature/budgets`. Al mismo tiempo, `PastelCategoryPickerFlow.kt` importa `CategoryFormContent` desde `feature/categories`, creando un acoplamiento circular entre módulos de presentación.
- Escenario concreto de fallo: Cambios en el diseño de formularios de categorías requieren tocar `movements`, y cambios en selectores de movimientos pueden romper presupuestos y categorías.
- Fix mínimo: Mover `ParentCategoryChooserDialog`, `IconCatalogDialog`, `CustomPastelColorDialog`, `CATALOG_ICONS` y `PastelColorUtils` a `feature/categories/presentation/` o a `ui/component/`, dejando en `movements` solo el selector.
- Ahorro estimado: 940 líneas en `movements`
- Confianza: alta
- Cross-módulo: sí → CAT2 (categories/presentation) y BUD1 (budgets)

### [MOV5-13] Tests unitarios incompletos: propósito `CARD_PAYMENT` y títulos de `FILTER` sin cobertura en `AccountSelectorRulesTest`
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/movements/presentation/AccountSelectorRulesTest.kt:39-60
- Qué está mal: `AccountSelectorRulesTest` no contiene ninguna prueba para `AccountSelectorPurpose.CARD_PAYMENT` (que debe prohibir tarjetas de crédito y mostrar el copy específico de pago de tarjeta) ni para los títulos de `FILTER`.
- Escenario concreto de fallo: Una modificación accidental en `AccountSelectorDefaults.allowsCreditCards` que permita tarjetas de crédito en `CARD_PAYMENT` (permitiendo pagar una tarjeta con otra tarjeta de crédito) no es detectada por ningún test.
- Fix mínimo: Agregar test unitario en `AccountSelectorRulesTest` comprobando `assertFalse(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.CARD_PAYMENT, callerAllows = true))` y `assertEquals("Elige la cuenta de origen del pago de tarjeta", AccountSelectorDefaults.subtitle(AccountSelectorPurpose.CARD_PAYMENT))`.
- Confianza: alta
- Cross-módulo: no

### [MOV5-14] Parámetro `mostUsedAccountId` es código muerto sin efecto en `AccountSelectorBottomSheet.kt`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:188, 422, 430 y 637
- Qué está mal: El badge "Más usada" fue eliminado en refactorizaciones previas. El parámetro `mostUsedAccountId: String? = null` se mantiene, se evalúa `val isMostUsed = ...` para cada fila, pero `isMostUsed` es totalmente ignorado en `AccountItemRow` y no afecta el orden ni la presentación.
- Escenario concreto de fallo: Los llamadores pasan `mostUsedAccountId` esperando un impacto visual o de ordenamiento que no existe.
- Fix mínimo: Eliminar el parámetro `isMostUsed` de `AccountItemRow` y retirar el cómputo redundante en `AccountSelectorBottomSheet`.
- Ahorro estimado: 12 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV5-15] `CreateSubcategoryView` es una función Composable muerta sin ningún llamador
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1431-1472
- Qué está mal: Función interna de 42 líneas que envuelve `CategoryFormContent`, nunca invocada en ningún lugar del repositorio.
- Escenario concreto de fallo: Código residual no utilizado que aumenta el tamaño del archivo y el coste de lectura.
- Fix mínimo: Eliminar `CreateSubcategoryView`.
- Ahorro estimado: 42 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV5-16] `PastelCategoryPickerFlow` es una función alias sin llamadores
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:657-687
- Qué está mal: Función alias que redirige a `PastelCategoryPickerBottomSheet`. Ningún archivo en todo el proyecto la utiliza (todos llaman directamente a `PastelCategoryPickerBottomSheet`).
- Escenario concreto de fallo: Sobrecarga innecesaria de APIs públicas internas en el paquete.
- Fix mínimo: Eliminar la función alias `PastelCategoryPickerFlow`.
- Ahorro estimado: 31 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV5-17] Row huérfano con espaciado sin elementos hermanos en `AccountSelectionRow`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectionRow.kt:108-124
- Qué está mal: `Text(text = alias)` está contenido dentro de un `Row(horizontalArrangement = Arrangement.spacedBy(6.dp))` sin ningún otro hijo y con `Modifier.weight(1f, fill = false)` redundante.
- Escenario concreto de fallo: Estructura de layout innecesariamente anidada que añade sobrecarga de composición a cada fila de cuenta.
- Fix mínimo: Eliminar el `Row` intermedio y dejar `Text(text = alias)` directamente en el `Column`.
- Ahorro estimado: 15 líneas
- Confianza: alta
- Cross-módulo: no

### [MOV5-18] Duplicación de lógica: `accountTypeLabel` y `networkLabel` duplicados en múltiples módulos
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:881-895
- Qué está mal: `networkLabel` está duplicado exactamente entre `AccountSelectorBottomSheet.kt:881` y `UnifiedInstrumentFormScreen.kt:1521`. `accountTypeLabel` está duplicado idénticamente entre `AccountSelectorBottomSheet.kt:889` y `QuickMovementScreen.kt:1482`.
- Escenario concreto de fallo: Riesgo de divergencia de textos si se agrega un nuevo tipo de cuenta o red de tarjeta y se olvida actualizar uno de los archivos.
- Fix mínimo: Centralizar `accountTypeLabel(AccountType)` y `networkLabel(CardNetwork)` en `AccountModels.kt` o `CalmEmeraldInstrumentComponents.kt`.
- Ahorro estimado: 25 líneas
- Confianza: alta
- Cross-módulo: sí → ACC2 (accounts/presentation/instruments) y MOV3 (movements/presentation)

### [MOV5-19] Parámetro obligatorio no nulo `onCreateCategory` fuerza a múltiples pantallas a pasar lambdas que lanzan excepciones
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:465-471
- Qué está mal: En `MovementFiltersSheet`, `MovementEditorSheet`, `BudgetFormScreen` y `PastelCategoryPickerSelectorTest`, donde `allowCreate = false`, los llamadores deben escribir `{ _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) }` porque el parámetro no es anulable ni tiene valor por defecto.
- Escenario concreto de fallo: Código repetitivo y frágil en múltiples características.
- Fix mínimo: Definir `onCreateCategory: (suspend (parentId: String?, name: String, iconId: String, colorHex: String, rememberFrequentMerchant: Boolean) -> Result<CategoryOption>)? = null`.
- Ahorro estimado: 20 líneas en llamadores
- Confianza: alta
- Cross-módulo: no

### [MOV5-20] Riesgo de clipping horizontal en filas de tarjetas de crédito en pantallas estrechas (360dp)
- Severidad: baja
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:768-810
- Qué está mal: En `CreditCardItemRow`, el bloque de montos contiene dos filas con texto largo: `"Crédito disponible: "` y `"Deuda: "` seguidas de `MoneyText`. En dispositivos con ancho de 360dp, este bloque consume ~170dp, reduciendo drásticamente el espacio del título y alias de la tarjeta, que sufren truncamiento severo a pocos caracteres.
- Escenario concreto de fallo: En un teléfono de 360dp, una tarjeta "BCP Visa Platinum" muestra su nombre cortado como "BCP..." debido al ancho fijo consumido por las etiquetas de crédito y deuda.
- Fix mínimo: Restaurar la etiqueta concisa `"Créd. disp.: "` (como exigía la prueba de copy de Issue 48) o estructurar etiqueta y monto verticalmente en anchos reducidos.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `AccountSelectorDefaults.allowsCreditCards` y `title/subtitle` con `AccountSelectorPurpose.CARD_PAYMENT`: verificar que el selector para pagar tarjeta rechace tarjetas de crédito como fuente y muestre "Elige la cuenta de origen del pago de tarjeta".
- `PastelCategoryPickerBottomSheet` en `directSelectionMode = true` al pulsar una categoría raíz con subcategorías: verificar que no cierre la hoja de golpe y permita expandir el acordeón.
- `CustomPastelColorDialog` en modo oscuro con `KipuTheme(darkTheme = true)`: comprobar que seleccionar un chip pastel recomendado preserve su valor hexadecimal canónico (ej. `#DCFCE7`) y dibuje el check de selección.
- `AccountSelectorBottomSheet` con `debitCards` y `purpose = INCOME_DESTINATION`: verificar que las tarjetas de débito no se listen en la categoría Todas si las tarjetas están deshabilitadas.
- `PastelCategoryPickerBottomSheet` con emisión asíncrona de categorías: comprobar que el acordeón de la categoría actual se expanda cuando la lista se puebla con retraso.

## Lo que no revisé

Se leyeron e inspeccionaron íntegramente los 5 archivos productivos (`AccountSelectionRow.kt`, `AccountSelectorBottomSheet.kt`, `AccountSelectorThemePreview.kt`, `MovementCategoryOptions.kt`, `PastelCategoryPickerFlow.kt`), los 2 archivos de tests unitarios (`AccountSelectorRulesTest.kt`, `MovementCategoryOptionsTest.kt`) y los 2 de tests instrumentados (`AccountSelectorBottomSheetTest.kt`, `PastelCategoryPickerSelectorTest.kt`). No se ejecutaron pruebas de Gradle ni emuladores, conforme al mandato de auditoría estática.

## Dudas

- ¿Se pretendía que en `directSelectionMode` tocar la fila de una categoría raíz con subcategorías abriera el acordeón en lugar de seleccionar la raíz y cerrar la hoja de inmediato? Hoy, cerrar la hoja de golpe al pulsar la fila raíz impide explorar las subcategorías a menos que el usuario acierte en el pequeño botón de chevron.
- ¿La etiqueta `"Créd. disp.: "` en la tarjeta de crédito debe mantenerse abreviada según la especificación de Issue 48 para pantallas pequeñas (360dp), o la intención era cambiarla a `"Crédito disponible: "` y simplemente se olvidó actualizar las pruebas instrumentadas (`AccountSelectorBottomSheetTest` e `Issue48ProductionCreditLabelsTest`)?
- ¿El badge "Más usada" (`mostUsedAccountId`) fue retirado intencionalmente de la interfaz del selector de cuentas en el rediseño visual de octubre, o debe restablecerse su visualización/ordenamiento prioritario?

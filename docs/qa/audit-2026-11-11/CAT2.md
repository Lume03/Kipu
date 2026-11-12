# CAT2 — categories/presentation (categories, components, raíz)

Esta porción gestiona la interfaz y presentación del catálogo de categorías (árbol raíz/subcategorías, límites del Plan Free, formularios de edición visual de color/icono) y los selectores de comercios (búsqueda en catálogo local, filtrado taxonómico UI, integración con búsqueda en internet y comercios privados). Se asumió una carga de miles de usuarios con 1 a 2 dispositivos cada uno, operando habitualmente sin conexión, con un catálogo base de decenas de comercios y sincronización eventual de categorías y aspectos visuales. Auditoría estática sobre `main`, sin ejecución de Gradle, ADB, tests instrumentados ni red/Supabase.

## Hallazgos

18 hallazgos: 0 críticos, 3 altos, 7 medios y 8 bajos. No se omitieron hallazgos confirmados.

### [CAT2-01] Subcategorías inactivas se muestran como activas y no pueden reactivarse en la interfaz
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:372, 670, 850, 933-953
- Qué está mal: La lista principal filtra únicamente por la condición de categoría raíz activa (`displayCategories.filter { it.category.isActive }`). Dentro de `CategoryRootCard`, `item.subcategories.forEach` itera sobre todas las subcategorías sin filtrar `subItem.category.isActive`. Como la raíz padre está activa, la condición `if (!isRootActive)` de la línea 946 nunca se cumple y la subcategoría inactiva se renderiza idéntica a una activa. Además, el menú contextual de subcategoría (líneas 933-942) ofrece únicamente "Inactivar", el callback `onToggleSubcategoryActive` (línea 670) nunca se invoca ni se suministra desde la pantalla, y la sección inferior "Inactivas" solo lista raíces (`displayCategories.filterNot { it.category.isActive }`).
- Escenario concreto de fallo: El usuario inactiva la subcategoría "Restaurantes" dentro de la categoría raíz activa "Alimentación". Al refrescar la pantalla, "Restaurantes" sigue apareciendo en la lista desplegada de "Alimentación" sin ninguna indicación visual de inactiva; su menú contextual solo ofrece "Inactivar" (que no produce ningún efecto); el contador de "Inactivas" al pie marca 0; y el usuario no tiene ninguna vía en la interfaz para volver a reactivar su subcategoría.
- Fix mínimo: Filtrar `item.subcategories.filter { it.category.isActive }` para la vista normal de la tarjeta raíz; en el menú contextual de subcategoría, conmutar entre "Inactivar" y "Reactivar" según `subItem.category.isActive` invocando `viewModel.toggleCategoryActive(subItem.category.id, subItem.category.isActive)`; y si la sección "Inactivas" incluye subcategorías huérfanas o inactivas, mostrarlas bajo su padre con etiqueta explícita de inactiva.
- Confianza: alta
- Cross-módulo: no

### [CAT2-02] `CategoryPresentationViewModel` no consume el Flow de presentaciones y envía revisiones desfasadas provocando conflictos espurios
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationViewModel.kt:79-90, 133-136
- Qué está mal: En `loadCategoryData()`, la línea 81 ejecuta `val presentation = category?.let { repository.observeCategoryPresentations(userId) }`, instanciando un `Flow` que nunca se colecta ni se asigna al estado. El estado UI se actualiza manteniendo `name = ""` y valores por defecto arbitrarios. Adicionalmente, asigna `expectedRevision = category?.revision ?: 1L`, confundiendo la revisión de la entidad `Category` con la revisión de `CategoryPresentation`. Al guardar (línea 136), `updateCategoryPresentation` compara `expectedRevision` contra `existing.remoteRevision` de la presentación en Room (`OfflineFirstCategoriesRepository.kt:450`); al discrepar, Room inserta inmediatamente un `CategoryConflictEntity` en vez de actualizar la presentación.
- Escenario concreto de fallo: Se abre `CategoryPresentationEditor` para una categoría existente con nombre "Compras" y revisión de presentación 3 (mientras la categoría base está en revisión 1). La pantalla muestra el nombre vacío. Si el usuario ingresa un nombre y pulsa "Guardar Cambios", envía `expectedRevision = 1`. El repositorio detecta la discrepancia con la revisión 3 de la presentación en base de datos e inserta un conflicto de sincronización bloqueante en estado OPEN sin actualizar los datos locales.
- Fix mínimo: Colectar la presentación actual desde `repository.observeCategoryPresentations(userId)` tomando el primer elemento que coincida con `cid`, poblar `name`, `icon`, `color` y fijar `expectedRevision = presentation.revision`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-03] `CategoryFormDialog` oculta errores de guardado y permite pulsaciones concurrentes sin bloqueo
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt:379-447; app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryFormScreen.kt:92-152, 650-678
- Qué está mal: `CategoryFormDialog` (ModalBottomSheet) no recibe ni propaga el parámetro `errorMessage` a `CategoryFormContent`. Cuando `createCategory` o `updateCategoryPresentation` fallan, el ViewModel actualiza `_uiState.errorMessage`, el cual dispara un Snackbar en el `Scaffold` que queda completamente oculto detrás de la hoja modal abierta. Además, `CategoriesUiState` no cuenta con bandera de `isSaving`/`isCreating`, por lo que el botón "Guardar categoría" permanece activo durante la operación asíncrona y `submitCreateCategory()` no comprueba si ya existe un guardado en curso.
- Escenario concreto de fallo: En un dispositivo con Room saturado o retraso de I/O, el usuario presiona dos veces seguidas "Guardar categoría". Se lanzan dos corrutinas concurrentes creando categorías duplicadas con el mismo nombre; si una de ellas falla por restricción de unicidad, el error se emite a un Snackbar oculto detrás de la hoja modal: la hoja no se cierra y el usuario no recibe ningún mensaje explicativo.
- Fix mínimo: Añadir `isSaving: Boolean = false` y `errorMessage: String? = null` a `CategoriesUiState`, propagar ambos a `CategoryFormDialog` -> `CategoryFormContent`, inhabilitar el botón de confirmación mientras `isSaving` sea verdadero, y en `submitCreateCategory()` retornar inmediatamente si `_uiState.value.isSaving` es true.
- Confianza: alta
- Cross-módulo: no

### [CAT2-04] Búsqueda de categorías en `CategoriesScreen` falla ante nombres con tildes al no normalizar diacríticos
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:348-356, 1120-1128
- Qué está mal: La búsqueda en `CategoriesScreen` filtra mediante `rootItem.displayName.lowercase().contains(q)` y resalta con `indexOf(query.trim(), ignoreCase = true)`. A diferencia de `MerchantPicker` y `CategoryRules.normalizeText()`, no aplica descomposición canónica NFD ni elimina marcas diacríticas.
- Escenario concreto de fallo: Un usuario en Perú escribe "alimentacion", "musica" o "educacion" (sin tilde, habitual en teclados móviles) en la barra de búsqueda de categorías. Las categorías predeterminadas del catálogo "Alimentación", "Música" y "Educación" se descartan por completo y la pantalla muestra "Resultados para ... · 0".
- Fix mínimo: Reemplazar el filtrado manual por `CategoryRules.matchesNormalized(rootItem.displayName, searchQuery)` y normalizar de igual forma las subcategorías.
- Confianza: alta
- Cross-módulo: no

### [CAT2-05] Triple consulta redundante a Room por cada carácter ingresado en el buscador de comercios
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPickerViewModel.kt:73-91; app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt:388-390, 400-406, 559; app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantDiscoveryViewModel.kt:36-38
- Qué está mal: Por cada tecla pulsada en `MerchantPicker`: (1) `MerchantPickerViewModel.onQueryChanged` dispara `searchMerchantCatalog(query)` consultando Room vía Flow; (2) el `LaunchedEffect(state.query)` en `MerchantPicker` notifica a `discovery.onQueryChanged(query)`, que lanza otra consulta a Room vía `catalog.searchMerchants(query)`; (3) la línea 404 de `MerchantPicker` evalúa `val baseList = if (state.catalogEntries.isNotEmpty()) state.catalogEntries else state.searchResults` y filtra `baseList` en memoria. Al estar `catalogEntries` precargado con todos los comercios activos desde `init`, los resultados de ambas consultas Room (1 y 2) son sistemáticamente ignorados.
- Escenario concreto de fallo: Al escribir "tambo" (5 caracteres), se disparan 10 consultas asíncronas a SQLite/Room cuyos resultados se descartan sin mostrarse nunca en pantalla, consumiendo innecesariamente hilos del dispatcher de I/O y memoria.
- Fix mínimo: Eliminar la llamada a `catalog.searchMerchants(query)` en `MerchantDiscoveryViewModel.onQueryChanged` (usando el catálogo en memoria) y unificar la fuente de búsqueda en `MerchantPickerViewModel`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-06] `LazyColumn` anidada dentro de `Column(verticalScroll)` en `MerchantPickerBottomSheet` genera conflictos de scroll
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt:308-314, 526-531
- Qué está mal: `MerchantPickerBottomSheet` contiene un `Column` con modificador `.verticalScroll(rememberScrollState())`. A su vez, el `MerchantPicker` embebido contiene un `LazyColumn` con altura acotada (`heightIn(max = listHeight)`). En Compose, anidar listas perezosas dentro de un contenedor desplazable dentro de un `ModalBottomSheet` produce colisiones de captura de gestos táctiles.
- Escenario concreto de fallo: Al abrir el selector de comercios en un dispositivo físico y arrastrar verticalmente la lista de comercios para ver más resultados, el gesto es interceptado por el scroll del contenedor padre o por el swipe-down de cierre del `ModalBottomSheet`, cerrando la hoja accidentalmente o impidiendo el scroll fluido de los comercios.
- Fix mínimo: Quitar el modificador `verticalScroll` del contenedor raíz en `MerchantPickerBottomSheet` y dejar que `LazyColumn` maneje el desplazamiento vertical internamente.
- Confianza: alta
- Cross-módulo: no

### [CAT2-07] Pantallas y ViewModel huérfanos sin registro en rutas de navegación
- Severidad: media
- Categoría: borrar
- Ahorro estimado: 734 líneas
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryConflictScreen.kt:52-150; app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationEditor.kt:77-303; app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationViewModel.kt:42-187
- Qué está mal: Ninguno de estos 3 componentes está registrado en `SettingsNavigation.kt`, `AppNavHost` ni en ningún punto de la navegación de la app. `CategoryPresentationEditor` fue reemplazado funcionalmente por `CategoryFormDialog` dentro de `CategoriesScreen.kt`. `CategoryConflictScreen` es la única pantalla diseñada para resolver `CategoryConflictEntity`; al no tener ruta de navegación, si se produce un conflicto en Room, el usuario nunca dispone de interfaz para resolverlo.
- Escenario concreto de fallo: Si se genera un conflicto de sincronización en Room, la entidad queda abierta indefinidamente porque el usuario no puede acceder a `CategoryConflictScreen`. `CategoryPresentationEditor` acumula 322 líneas de código muerto que confunden el mantenimiento del módulo.
- Fix mínimo: Registrar la ruta para resolución de conflictos en `SettingsNavigation.kt` si el producto requiere resolver conflictos manualmente, y eliminar `CategoryPresentationEditor.kt` junto con los métodos obsoletos de `CategoryPresentationViewModel`.
- Confianza: alta
- Cross-módulo: sí → NAV1 / UI2

### [CAT2-08] Código muerto en `CategoryDesignTokens.kt` (paletas no utilizadas y bucle inalcanzable)
- Severidad: baja
- Categoría: borrar
- Ahorro estimado: 70 líneas
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/CategoryDesignTokens.kt:55-116, 169-174
- Qué está mal:
  1. `CATEGORY_PALETTE` (líneas 57-66) no se usa en todo el repositorio (`CategoryFormScreen` y `CategoryPresentationEditor` usan listas privadas de colores no alineadas con esta paleta).
  2. `CATEGORY_ICON_SECTIONS` (líneas 79-116) y sus clases de datos `CategoryIconItem` y `CategoryIconSection` solo son leídas por el bloque `else` de `resolveCategoryIcon` (líneas 169-174).
  3. Los 16 iconos de `CATEGORY_ICON_SECTIONS` ya están listados explícitamente en el `when` de `resolveCategoryIcon` (líneas 133-150), por lo que el bucle del bloque `else` jamás coincide con ningún valor.
- Escenario concreto de fallo: 70 líneas de código y estructuras de datos estáticas que se cargan en memoria pero no tienen ningún consumidor activo ni ruta de ejecución posible.
- Fix mínimo: Eliminar `CATEGORY_PALETTE`, `CATEGORY_ICON_SECTIONS`, `CategoryColor`, `CategoryIconItem`, `CategoryIconSection` y el bucle en el `else` de `resolveCategoryIcon`, retornando directamente `Icons.Default.Label`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-09] Insignias numéricas de las pestañas "Gastos" e "Ingresos" incluyen categorías inactivas falseando el conteo
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:305-306
- Qué está mal: `expenseCount` e `incomeCount` se calculan como `CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot }`. `state.categories` contiene tanto categorías activas como inactivas, y `filterCategories()` solo discrimina por tipo (EXPENSE/INCOME/GENERAL), sin verificar `isActive`.
- Escenario concreto de fallo: Un usuario tiene 8 categorías activas de gasto y 3 inactivadas. La pestaña "Gastos" muestra una insignia con el número "11". Al revisar la lista, el usuario solo observa 8 tarjetas visibles, generando aparente pérdida o desajuste de datos hasta que expande la sección inferior.
- Fix mínimo: Modificar el cálculo a: `CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot && it.category.isActive }`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-10] Modal de selección de cupo Free bloqueado cuando el cupo está exactamente en su límite (5/5)
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:324-326
- Qué está mal: En `CategoriesScreen.kt`, el callback para abrir el diálogo de selección de cupo en `QuotaBanner` se asigna con:
  `if (state.activeCustomExpenseRootsCount > state.maxCustomRoots || state.activeCustomIncomeRootsCount > state.maxCustomRoots) viewModel::openQuotaSelection else null`.
  Al usar comparador estrictamente mayor que (`>`), no se ofrece acceso cuando el usuario tiene exactamente 5 categorías activas.
- Escenario concreto de fallo: Un usuario Free tiene 5 categorías activas de gasto (5/5) y desea cambiar cuáles 5 están activas en su dispositivo mediante el modal `openQuotaSelection`. El botón con el chevron hacia el modal no aparece porque `5 > 5` es falso. El usuario no puede reconfigurar su selección a menos que desactive una categoría o supere el límite.
- Fix mínimo: Cambiar la condición a `>= state.maxCustomRoots` o habilitar el acceso a `openQuotaSelection` siempre que existan raíces personalizadas activas.
- Confianza: alta
- Cross-módulo: no

### [CAT2-11] `CategoriesViewModel` re-implementa el conteo de raíces ignorando las funciones canónicas de `CategoryRules`
- Severidad: baja
- Categoría: fusionar
- Ahorro estimado: 10 líneas
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt:158-168
- Qué está mal: `CategoriesViewModel` calcula manualmente en un bucle `activeRoots`, `activeExpense` y `activeIncome`, duplicando la lógica centralizada en `CategoryRules.countActiveCustomRoots()`, `CategoryRules.countActiveCustomExpenseRoots()` y `CategoryRules.countActiveCustomIncomeRoots()`.
- Escenario concreto de fallo: Si las reglas de imputación de cuotas (por ejemplo, el tratamiento de categorías GENERAL sobre los dos ejes) cambian en el dominio, el ViewModel mantendrá un cálculo desfasado respecto al repositorio y a Room.
- Fix mínimo: Reemplazar el conteo manual por llamadas a `CategoryRules.countActiveCustomRoots(categories)`, `CategoryRules.countActiveCustomExpenseRoots(categories)` y `CategoryRules.countActiveCustomIncomeRoots(categories)`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-12] Etiqueta engañosa "Usa 1 cupo" para categorías personalizadas de tipo GENERAL
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:759
- Qué está mal: La tarjeta muestra `Text(if (item.category.isCustom) "Usa 1 cupo" else "No consume cupo")`. De acuerdo con las reglas de negocio de la app (`CategoryRules`), una categoría `GENERAL` consume 1 cupo en el eje de gastos y 1 cupo en el eje de ingresos (2 cupos en total).
- Escenario concreto de fallo: Un usuario Free con 4 categorías de gasto y 4 de ingreso crea una categoría General. La tarjeta le indica "Usa 1 cupo", pero inmediatamente ambas barras de gasto e ingreso se saturan al 5/5, provocando confusión sobre el consumo real de su plan.
- Fix mínimo: Ajustar el texto informativo a: `if (item.category.categoryType == CategoryType.GENERAL) "Usa 1 cupo en gastos e ingresos" else if (item.category.isCustom) "Usa 1 cupo" else "No consume cupo"`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-13] Ausencia total de pruebas unitarias para `MerchantPickerViewModel`
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPickerViewModel.kt:1-127
- Qué está mal: `MerchantPickerViewModel` implementa la exclusividad crítica entre `selectedMerchant` y `provisionalText`, la limpieza de consultas y la reactividad del catálogo. No existe ningún archivo `MerchantPickerViewModelTest.kt` en toda la suite de pruebas.
- Escenario concreto de fallo: Un cambio inadvertido en `selectMerchant()` olvida limpiar `provisionalText` o en `setProvisionalText()` olvida limpiar `selectedMerchant`, permitiendo que coexistan ambos valores y generando inconsistencias en el registro de movimientos en `QuickMovementScreen`, `MovementEditorSheet` y `CaptureReviewScreen`. Ningún test unitario detecta el fallo.
- Fix mínimo: Crear `app/src/test/java/com/kipu/app/feature/categories/presentation/MerchantPickerViewModelTest.kt` con pruebas que verifiquen: (1) `selectMerchant` limpia `provisionalText` y fija el comercio; (2) `setProvisionalText` limpia `selectedMerchant` y asigna el texto recortado; (3) `clearSelection` limpia ambos.
- Confianza: alta
- Cross-módulo: no

### [CAT2-14] Casos de mutación en `CategoriesViewModel` carecen de pruebas unitarias
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModelTest.kt:73-77
- Qué está mal: `CategoriesViewModelTest` utiliza mocks relajados (`relaxed = true`) para `createCategory`, `setCategoryActive`, `updateCategoryPresentation` y `saveSelectedFreeCategoryRoots`, pero ninguno de sus tests invoca ni valida `submitCreateCategory()`, `confirmDeleteCategory()`, `toggleCategoryActive()` ni `saveQuotaSelection()`.
- Escenario concreto de fallo: Una regresión en `submitCreateCategory()` omite enviar `categoryType` o intercambia `ownerId`, o `confirmDeleteCategory()` deja de llamar a `setCategoryActive(id, false)`. La suite de pruebas de `CategoriesViewModelTest` pasa completamente en verde.
- Fix mínimo: Añadir pruebas unitarias específicas en `CategoriesViewModelTest` que invoquen `submitCreateCategory()` (tanto en modo creación como edición) y `confirmDeleteCategory()`, verificando con `coVerify` que los casos de uso reciben los parámetros correctos.
- Confianza: alta
- Cross-módulo: no

### [CAT2-15] Sección "Inactivas (0)" se muestra de forma permanente aunque no existan categorías inactivas
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:391-403
- Qué está mal: El elemento `item { Row(...) { Text("  Inactivas (${inactive.size})") } }` se inserta en el `LazyColumn` sin verificar si `inactive.isNotEmpty()`.
- Escenario concreto de fallo: Un usuario nuevo o sin categorías dadas de baja ve siempre un botón "Inactivas (0)" con icono de papelera al final de su lista. Al pulsarlo, se expande una sección vacía sin contenido.
- Fix mínimo: Envolver la sección en `if (inactive.isNotEmpty()) { ... }`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-16] Valores por defecto de nueva categoría no forman parte de las listas rápidas en `CategoryFormContent`
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryFormScreen.kt:81-88, 519, 602; CategoriesViewModel.kt:68-69
- Qué está mal: `CategoriesViewModel` inicializa una nueva categoría raíz con icono `"restaurant"` y color `"#0F766E"` (Verde Kipu). Sin embargo, `"restaurant"` no está en `QUICK_ICONS` (`listOf("tv", "music_note", "directions_car", "content_cut", "receipt", "shopping_cart")`), y `"#0F766E"` no está en `QUICK_COLORS` (`listOf("#06B6D4", "#3B82F6", "#8B5CF6", "#F97316", "#F43F5E", "#10B981")`). Al abrir el diálogo, ninguno de los 6 iconos rápidos ni de los 6 colores rápidos aparece seleccionado; ambos seleccionan el 7º botón ("+") de opciones avanzadas.
- Escenario concreto de fallo: El usuario abre "Nueva categoría" esperando una selección ágil entre las opciones visibles, pero los valores por defecto del sistema no coinciden con las opciones rápidas, forzando la selección sobre los botones de catálogo extendido.
- Fix mínimo: Incluir `"restaurant"` en `QUICK_ICONS` y `"#0F766E"` en `QUICK_COLORS`.
- Confianza: alta
- Cross-módulo: no

### [CAT2-17] `CategoryPresentationEditor` tiene icono de carpeta hardcodeado y paleta desconectada del sistema de diseño
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationEditor.kt:61-65, 186-192
- Qué está mal: En la tarjeta de previsualización en vivo, el icono está fijado estáticamente a `Icons.Default.Folder`, ignorando `state.icon` y sin proporcionar selector de iconos. Asimismo, define una paleta privada de 10 colores (`PRESENTATION_PALETTE`) que no coincide con `CATEGORY_PALETTE` ni con `QUICK_COLORS`.
- Escenario concreto de fallo: Al abrir este editor para una categoría de transporte o salud, la vista previa siempre muestra una carpeta genérica. (Nota: pantalla huérfana de navegación, candidata a borrado en CAT2-07).
- Fix mínimo: Reemplazar por `resolveCategoryIcon(state.icon)` o eliminar la pantalla huérfana.
- Confianza: alta
- Cross-módulo: no

### [CAT2-18] Lista de opciones de filtrado en `MerchantPicker` se re-instancia en cada recomposición sin `remember`
- Severidad: baja
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt:422-426
- Qué está mal: `val groupOptions = listOf(...) + availableGroups.map { ... }` se evalúa directamente en el cuerpo del composable `MerchantPicker` sin `remember`. Con cada recomposición provocada por la escritura del usuario o cambios de foco, se crean y destruyen 12+ instancias de `FilterOption`.
- Escenario concreto de fallo: Al escribir rápidamente un término de búsqueda, se generan asignaciones redundantes de memoria en el hilo principal durante la animación de la interfaz.
- Fix mínimo: Envolver la creación de `groupOptions` en `remember(availableGroups, filterColor) { ... }`.
- Confianza: alta
- Cross-módulo: no

---

## Código de riesgo sin tests

- `MerchantPickerViewModel.kt` / `selectMerchant` y `setProvisionalText` → Comprobar la exclusividad estricta entre comercio del catálogo y texto provisional (CAT2-13).
- `CategoriesViewModel.kt` / `submitCreateCategory` → Comprobar la creación de raíz y subcategoría con parámetros correctos y bloqueo de envíos concurrentes (CAT2-14).
- `CategoriesViewModel.kt` / `confirmDeleteCategory` → Comprobar que la confirmación de borrado invoca `setCategoryActive(id, false)` y maneja fallos (CAT2-14).
- `CategoriesScreen.kt` / `CategoryRootCard` → Comprobar que subcategorías inactivas bajo raíces activas son visualmente identificables y pueden ser reactivadas (CAT2-01).

---

## Lo que no revisé

Se leyeron en su totalidad los 16 archivos de `main`, los 6 archivos de `test` y los 6 archivos de `androidTest` asignados a CAT2. Se consultaron fragmentos externos de `OfflineFirstCategoriesRepository.kt`, `CategoryRules.kt`, `FreePlanLimits.kt` y `SettingsNavigation.kt` exclusivamente para verificar contratos de invocación y persistencia; no se auditó la lógica completa de sincronización de categorías ni las funciones edge de Supabase (asignadas a CAT1). No se ejecutó Gradle, emuladores Android, comandos ADB ni consultas directas a Supabase; todas las deducciones derivan del análisis estático de código.

---

## Dudas

1. `CategoryConflictScreen` y `CategoryPresentationEditor` quedaron completamente desconectadas de las rutas de navegación en `SettingsNavigation.kt`. ¿Debe integrarse una pantalla de resolución de conflictos de categorías en el flujo de ajustes/sincronización, o la resolución de conflictos debe resolverse automáticamente en el repositorio (last-write-wins) eliminando estas pantallas huérfanas?
2. La taxonomía de interfaz de comercios (`UiMerchantGroup`) clasifica los comercios en 11 categorías fijas con nombres canónicos e IDs fijos en código Kotlin. Si se añaden nuevos comercios vía `MerchantDiscovery` desde Logo.dev, ¿debería existir un mapeo dinámico desde la categoría por defecto del comercio para que no queden sin grupo taxonómico?

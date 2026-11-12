# UI1 — home + analytics

Esta porción calcula y presenta el patrimonio neto en el inicio (Inicio / Net Worth) y el módulo completo de análisis financiero (Análisis: distribución del dinero, flujos de ingresos/gastos, semanas, desglose de categorías y comercios, saldo diario día a día, utilización de crédito y comparativas mensuales). Se asumieron miles de usuarios, cada uno con uno o dos dispositivos, miles de movimientos acumulados por usuario y uso frecuente offline con sincronización en segundo plano. La revisión fue estática, basada en lectura de código, consultas Room SQL y flujos reactivos.

## Hallazgos

### [UI1-01] `AnalyticsPeriod.previous()` descarta días al comparar meses completos cerrados más cortos con meses anteriores más largos (Feb -> Ene, Abr -> Mar)
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/domain/Analytics.kt:23-28
- Qué está mal: En `AnalyticsPeriod.previous()`, cuando `start.dayOfMonth == 1`, calcula `val span = ChronoUnit.DAYS.between(start, endInclusive)` y `val previousEnd = minOf(previousStart.plusDays(span), previousStart.withDayOfMonth(previousStart.lengthOfMonth()))`. Si el periodo analizado es un mes cerrado completo que tiene menos días que el mes anterior (por ejemplo, Febrero tiene 28 días y Enero tiene 31; o Abril tiene 30 y Marzo 31), `span` es 27 días (28 - 1). `previousStart.plusDays(27)` da el 28 de enero. La función `minOf` selecciona el 28 de enero. Como resultado, la comparación de todo Febrero ignora el 29, 30 y 31 de Enero (3 días completos de gastos). De igual forma, comparar Abril (30 días) contra Marzo (31 días) ignora el 31 de Marzo.
- Escenario concreto de fallo: Usuario en plan Premium analiza o compara Febrero 2026 completo (mes cerrado, 2026-02-01 a 2026-02-28). Llama a `previous()`. El periodo retornado es `2026-01-01 a 2026-01-28`. Cualquier gasto realizado el 29, 30 o 31 de enero queda excluido de la comparación previa, distorsionando el total del mes anterior y el porcentaje de cambio reportado.
- Fix mínimo: Si el mes actual está cerrado (`endInclusive == start.withDayOfMonth(start.lengthOfMonth())`), `previousEnd` debe ser el último día del mes anterior (`previousStart.withDayOfMonth(previousStart.lengthOfMonth())`). Solo si es un mes parcial (`isPartial`) debe aplicarse `minOf(previousStart.plusDays(span), ...)`.
- Confianza: alta
- Cross-módulo: no

### [UI1-02] La vista de comparación (`AnalyticsComparison`) oculta y falla ante periodos que solo tienen ingresos
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:824-829 y app/src/main/java/com/kipu/app/feature/analytics/domain/Analytics.kt:170-179
- Qué está mal: En `AnalyticsScreen.kt`, `comparisonItems` verifica: `if (!result.previous.hasRecords && !result.current.hasRecords)` para mostrar el estado vacío `comparison_empty`. Pero `hasRecords` solo verifica si hay registros de gastos (`matched.isNotEmpty()`). Si un usuario aplica el filtro de tipo "Ingresos" (`MovementType.INCOME`), o si en los periodos solo hay ingresos y no gastos, `hasRecords` es falso en ambos, y la UI muestra erróneamente "Sin movimientos en los periodos seleccionados" (`analytics_comparison_empty`). Además, `AnalyticsCalculator.compare` solo itera sobre `previous.totals.keys + current.totals.keys` (que son exclusivamente totales de gastos) y omite por completo `incomeTotals`.
- Escenario concreto de fallo: Un usuario con ingresos en octubre y noviembre entra a "Comparar" y en el filtro selecciona Tipo: "Ingresos". La pantalla muestra el estado vacío "Sin movimientos en los periodos seleccionados" a pesar de tener ingresos en ambos meses.
- Fix mínimo: En `comparisonItems` verificar `!result.previous.hasMovements && !result.current.hasMovements`. En `AnalyticsCalculator.compare`, si se filtran ingresos, comparar `incomeTotals` o deshabilitar/ocultar el filtro de ingresos en modo comparación.
- Confianza: alta
- Cross-módulo: no

### [UI1-03] `AnalyticsHeader` coloca `HorizontalDivider` al tope de la pantalla solapando el header
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:381-421
- Qué está mal: `AnalyticsHeader` emite dos composables raíz independientes: un `Row` con el header y un `HorizontalDivider(color = colors.borderSubtle)` sin estar envueltos en un `Column`. Al ser pasado a `Scaffold(topBar = { AnalyticsHeader(...) })`, Compose Scaffold mide ambos placeables de `topBar` y los posiciona en `(0, 0)`. Como consecuencia, el divider horizontal no se dibuja debajo del header, sino en Y=0 (al tope absoluto de la barra de estado o encabezado).
- Escenario concreto de fallo: Al renderizar `AnalyticsScreen`, la línea divisoria inferior del encabezado no aparece separando el header del contenido scrolleable, sino superpuesta en la parte superior del status bar / app bar.
- Fix mínimo: Envolver el `Row` y el `HorizontalDivider` dentro de un `Column(Modifier.fillMaxWidth())`.
- Confianza: alta
- Cross-módulo: no

### [UI1-04] El gráfico de saldo diario (`BalanceLineSection`) ignora los filtros de cuenta activos
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:533-536
- Qué está mal: Cuando el usuario filtra por una cuenta específica (ej. Cuenta BCP), `PeriodSubline` muestra "Filtros (1)" y los KPIs/semanas/categorías muestran los gastos de esa cuenta. Sin embargo, el bloque de saldo diario construye `ids` como `overview.accountsIn(currency).map { it.account.id.value }.toSet()` y pasa `overview.total(currency)`. Ignora por completo `ui.filters.accountIds`. Por tanto, el gráfico muestra la evolución patrimonial y el saldo conjunto de TODAS las cuentas líquidas del usuario, contradiciendo el filtro seleccionado.
- Escenario concreto de fallo: Usuario con dos cuentas (A con S/ 5,000 y B con S/ 100). Filtra por la cuenta B para auditar sus movimientos. El gráfico de saldo diario muestra S/ 5,100 y la curva combinada de ambas cuentas en vez de los S/ 100 de la cuenta B.
- Fix mínimo: En `AnalyticsScreen.kt:534`, si `ui.filters.accountIds.isNotEmpty()`, filtrar los IDs y sumar solo los saldos de las cuentas incluidas en el filtro.
- Confianza: alta
- Cross-módulo: no

### [UI1-05] `BalanceSeriesCalculator.series` ejecuta algoritmo O(D * N) en el hilo principal durante la recomposición
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/domain/AnalyticsOverview.kt:20-28 y app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:533-536
- Qué está mal: Para cada día del periodo analizado (D = 28..31 días), `BalanceSeriesCalculator.series` recorre la lista completa de `deltas` con `.filter { it.atMillis >= endOfDay }.sumOf { it.deltaMinor }`. Con miles de movimientos de usuario (N deltas acumulados desde el inicio del periodo hasta hoy), esto ejecuta 31 * N operaciones lineales dentro de un bloque `remember` en el hilo de UI de Compose.
- Escenario concreto de fallo: Un usuario con 5,000 movimientos acumulados cambia de tab o rota la pantalla. En el hilo principal de renderizado se realizan ~150,000 iteraciones y cálculos de fecha de forma síncrona, provocando jank y pérdida de frames (lag perceptible).
- Fix mínimo: Calcular la serie en un solo pase acumulativo inverso O(D + N) ordenando/agrupando deltas por día, o precomputarlo en background en el ViewModel/UseCase antes de pasarlo al estado de la UI.
- Confianza: alta
- Cross-módulo: no

### [UI1-06] `AnalyticsDao.observeLedgerRows` realiza un escaneo completo sin índice al filtrar con expresión `COALESCE`
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:118-123
- Qué está mal: La consulta `observeLedgerRows` filtra con `WHERE le.user_id = :owner AND COALESCE(t.occurred_at, le.created_at) >= :fromMillis`. Dado que `COALESCE` combina columnas de dos tablas en un LEFT JOIN, SQLite no puede utilizar ningún índice B-Tree (`index_ledger_entries_user_id_account_id` ni `index_transactions_user_id_occurred_at`). Realiza un escaneo de tabla completo de `ledger_entries` para el usuario y hace join con `transactions` para cada fila histórica.
- Escenario concreto de fallo: Con 10,000 movimientos en la base de datos local, cada vez que el usuario abre Análisis o cambia de mes, la consulta lee e inspecciona las 10,000 filas de libro mayor sin filtrado indexado.
- Fix mínimo: Almacenar la fecha efectiva/occurred_at directamente en `ledger_entries` (o filtrar por `le.created_at >= :fromMillis` si las fechas son aproximadas) para aprovechar el índice por fecha.
- Confianza: alta
- Cross-módulo: no

### [UI1-07] `AnalyticsViewModel` crashea con `Periodo inválido` si un periodo usa una zona horaria distinta a `ZoneId.systemDefault()`
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsViewModel.kt:86
- Qué está mal: `report()` impone `require(period.zone == zone && !period.endInclusive.isAfter(today)) { "Periodo inválido" }`, donde `zone = ZoneId.systemDefault()`. Si un caller inyecta o construye un periodo con la zona de negocio de Kipu (`ZoneId.of("America/Lima")`) o si cambia la zona del sistema mientras la pantalla o el ViewModel están activos, la aserción falla lanzando `IllegalArgumentException`, que es capturada por el catch de la Flow emitiendo `AnalyticsUiState.Error`.
- Escenario concreto de fallo: Una prueba unitaria, deeplink o componente que configure el periodo usando `America/Lima` (la zona oficial de Kipu) en un dispositivo cuya zona sea distinta de UTC-5 provoca que la pantalla muestre inmediatamente "No se pudieron cargar las estadísticas".
- Fix mínimo: Eliminar la restricción de igualdad estricta de objeto `period.zone == zone` (la conversión de epoch millis ya encapsula la zona del periodo) o normalizar el periodo recibido a la zona de negocio antes de consultar.
- Confianza: alta
- Cross-módulo: no

### [UI1-08] `Currency.fromCode` no controlado en `ObserveNetWorthSummary` ante códigos de moneda corruptos o no soportados
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/home/domain/ObserveNetWorthSummary.kt:31
- Qué está mal: En `ObserveNetWorthSummary.kt:31`, se hace `Currency.fromCode(it.currencyCode)`. Si `debtRepository` devuelve una deuda con un código de moneda inesperado o en minúsculas no estándar (distinto de "PEN" o "USD"), `Currency.fromCode` lanza `IllegalArgumentException("Unsupported currency code: ...")`. A diferencia de `ObserveAnalyticsOverview`, `ObserveNetWorthSummary` no usa `runCatching` localmente y provoca que el Flow de patrimonio falle completamente.
- Escenario concreto de fallo: Una sincronización remota o registro corrupto en la tabla de deudas con `currency_code = 'EUR'` hace que el inicio de la app muestre un error permanente "No se pudo calcular tu patrimonio".
- Fix mínimo: Usar `runCatching { Currency.fromCode(it.currencyCode) }.getOrNull()` y filtrar los que sean nulos.
- Confianza: alta
- Cross-módulo: no

### [UI1-09] `ObserveAnalyticsOverview` carece totalmente de pruebas unitarias
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/domain/ObserveAnalyticsOverview.kt:31-81
- Qué está mal: `ObserveAnalyticsOverview` es un caso de uso central que combina 5 fuentes (dashboard de instrumentos, metas activas con eventos, vencimientos de deudas y tarjetas, deudas principales y deltas de libro mayor). Contiene lógica financiera compleja de selección de mejor meta por progreso, filtrado de vencimientos por fin de mes y acumulación de saldos de préstamos por moneda. No tiene ningún test unitario en el repositorio (solo se mockea en `AnalyticsViewModelTest`).
- Escenario concreto de fallo: Un cambio o regresión en el filtrado de `duesPart` (`it.dueDate <= endOfMonth`) o en la selección de metas pasa desapercibido en la suite de pruebas automatizadas.
- Test mínimo que falta: Un test unitario `ObserveAnalyticsOverviewTest` con repositorios simulados que valide que: 1) deudas por pagar vencidas o del mes se suman a `upcoming`; 2) metas inactivas se excluyen y la meta activa con mayor porcentaje se selecciona como snapshot.
- Confianza: alta
- Cross-módulo: no

### [UI1-10] `AnalyticsDao` no tiene pruebas para `observeIncomes`, `observeLedgerRows` ni opciones de filtros
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/analytics/AnalyticsDaoTest.kt:15
- Qué está mal: `AnalyticsDaoTest.kt` solo prueba la consulta `observeExpenses`. Las consultas `observeIncomes` (con su compleja lista de exclusión de kinds), `observeLedgerRows` (con su LEFT JOIN y COALESCE), `observeCategoryOptions`, `observeAccountOptions`, `observeCardOptions` y `observeMerchantOptions` no tienen ningún test instrumentado de base de datos.
- Escenario concreto de fallo: Si se introduce una errata en la consulta SQL de `observeIncomes` o una exclusión errónea de `legacy_kind`, ningún test falla.
- Test mínimo que falta: En `AnalyticsDaoTest`, insertar transacciones de tipo `INCOME` con distintos `operation_kind` y `legacy_kind` (ej. REFUND vs STANDARD vs OPENING) y verificar que `observeIncomes` solo retorne ingresos operativos.
- Confianza: alta
- Cross-módulo: no

### [UI1-11] Asimetría de sesión entre `ObserveFinancialDashboard` (sin owner) y `observeDebts(userId)` en `ObserveNetWorthSummary`
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/home/domain/ObserveNetWorthSummary.kt:22-23
- Qué está mal: `ObserveNetWorthSummary.invoke(userId: String)` combina `observeFinancialDashboard()` (que no acepta `userId` y depende del estado global de sesión de `OfflineFirstFinancialInstrumentsRepository`) con `debtRepository.observeDebts(userId)` (que sí filtra explícitamente por el parámetro `userId`). Durante una transición de cuenta de usuario, ambas fuentes pueden emitir en momentos distintos, existiendo una ventana donde el dashboard pertenece al usuario anterior mientras las deudas pertenecen al nuevo usuario.
- Escenario concreto de fallo: En un cambio de usuario rápido en el mismo dispositivo, el flujo emite transitoriamente un `NetWorthSummary` que mezcla los saldos bancarios del usuario previo con las deudas del nuevo usuario.
- Fix mínimo: Hacer que `ObserveFinancialDashboard` acepte `userId` explícito como todas las demás funciones de consulta del dominio Kipu.
- Confianza: alta
- Cross-módulo: sí → ACC1

### [UI1-12] `ObserveNetWorthSummary` no protege contra deudas con `remainingPrincipalMinor` negativo
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/home/domain/ObserveNetWorthSummary.kt:31
- Qué está mal: En `ObserveNetWorthSummary.kt:31`, se mapea: `Money(it.remainingPrincipalMinor, Currency.fromCode(it.currencyCode))`. Si un pago o liquidación excede el principal debido a ajustes o amortizaciones manuales, `remainingPrincipalMinor` puede ser menor a cero. A diferencia de `ObserveAnalyticsOverview.kt:62` (`coerceAtLeast(0L)`) y `UpcomingDuesViewModel.kt` (`coerceAtLeast(0)`), aquí no se acota a 0, creando importes monetarios negativos que distorsionan los pasivos o por cobrar (un pasivo negativo se suma como activo).
- Escenario concreto de fallo: Deuda por pagar con saldo de -S/ 50. En el cálculo de patrimonio, `liabilities` se vuelve -50, lo que provoca que `netWorth = realAssets - (-50) = realAssets + 50`, inflando el patrimonio neto como si la deuda fuera un activo.
- Fix mínimo: Agregar `.coerceAtLeast(0L)` al valor de `it.remainingPrincipalMinor` al igual que en `ObserveAnalyticsOverview`.
- Confianza: alta
- Cross-módulo: no

### [UI1-13] Filtrado duplicado idéntico entre `AnalyticsDao.observeExpenses` y `OperatingExpenseClassification`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsRepository.kt:20 y app/src/main/java/com/kipu/app/feature/analytics/domain/OperatingExpenseClassification.kt
- Qué está mal: `AnalyticsDao.observeExpenses` ya filtra exactamente en SQL: `type = 'EXPENSE'`, `status IN ('ACTIVE','CONFIRMED','REVISED')`, `sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')`, `operation_kind` estándar y `legacy_kind` no operativo. Inmediatamente después, `AnalyticsRepository.observe` re-ejecuta `OperatingExpenseClassification.includes` en memoria sobre todas las filas obtenidas. La clase `OperatingExpenseClassification` existe solo para este doble filtrado redundante (que además no se hace en ingresos).
- Escenario concreto de fallo: Por cada fila leída de Room, se normalizan strings (`uppercase(Locale.ROOT)`) y se evalúan sets repetidos que la base de datos ya filtró.
- Fix mínimo: Eliminar la clase `OperatingExpenseClassification.kt` y su llamada en `AnalyticsRepository.kt`, o delegar la validación a la capa de datos como se hace en `observeIncomes`.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 38 líneas (14 en `OperatingExpenseClassification.kt` + 24 en su test).

### [UI1-14] `AnalyticsScreen.kt` monolítico de 1090 líneas mezcla navegación, selección de fechas, modales de filtros y modo comparación
- Severidad: baja
- Categoría: dividir
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:1-1090
- Qué está mal: El archivo tiene 1,090 líneas y agrupa responsabilidades no relacionadas: pantalla principal, encabezado con menú de periodo, diálogo modal de rango de fechas de Material3 (`RangeDialog`), bottom sheet de selección de periodo (`PeriodSheet`), bottom sheet de catálogo y filtros (`FilterSheet`), y toda la UI completa del modo de comparación (`comparisonItems`, `ComparisonCard`, `LegendBar`).
- Escenario concreto de fallo: Dificulta la lectura, testing y mantenimiento aislado de los filtros o selectores de fecha; cualquier cambio a los filtros obliga a recomponer y tocar el archivo principal de la pantalla.
- Fix mínimo: Extraer `PeriodSheet.kt` + `RangeDialog.kt` (~200 líneas), `FilterSheet.kt` (~150 líneas) y `AnalyticsComparisonSection.kt` (~150 líneas) a archivos separados en el mismo paquete.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: Archivo principal se reduce de 1090 a ~450 líneas.

### [UI1-15] `AnalyticsCalculatorTest` contiene un test redundante de `NetWorthCalculator`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/test/java/com/kipu/app/feature/analytics/AnalyticsCalculatorTest.kt:60-64
- Qué está mal: El test `netWorthIncludesReceivablesAndDeductsCardDebtOnce` en `AnalyticsCalculatorTest.kt` prueba `NetWorthCalculator.calculate`. Esta prueba es idéntica a `la deuda de tarjeta se resta una vez como pasivo` en `NetWorthCalculatorTest.kt:31-39` y no pertenece al módulo de analytics.
- Escenario concreto de fallo: Mantenimiento duplicado si cambian los modelos de entrada de `NetWorthInputs`.
- Fix mínimo: Eliminar `netWorthIncludesReceivablesAndDeductsCardDebtOnce` de `AnalyticsCalculatorTest.kt`.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 5 líneas.

### [UI1-16] `observeMerchantOptions` invalida y refresca los filtros ante cualquier mutación en la tabla `transactions`
- Severidad: baja
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:76-83
- Qué está mal: La consulta `observeMerchantOptions` hace `FROM transactions t JOIN merchant_catalog_cache m`. Room registra un observador de invalidación en la tabla `transactions`. Cada vez que el usuario crea, edita o sincroniza cualquier transacción (aunque no sea de un comercio nuevo ni afecte a las opciones de filtro), Room re-ejecuta `observeMerchantOptions`, lo que dispara `observeFilterOptions` en el ViewModel y provoca la recomposición de los selectores en pantalla.
- Escenario concreto de fallo: Durante una sincronización de fondo (SyncMovementsWorker) de 50 transacciones, se producen decenas de invalidaciones innecesarias de `filterOptions`.
- Fix mínimo: En `observeFilterOptions` aplicar `distinctUntilChanged()` en el flujo de opciones o consultar los comercios desde una tabla derivada/caché que no se invalide en cada movimiento.
- Confianza: alta
- Cross-módulo: no

### [UI1-17] `percentChange` puede desbordar `Int` si el importe previo es 1 céntimo y el actual es alto
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsFlowSections.kt:85-89
- Qué está mal: En `percentChange`: `BigDecimal.valueOf(current.minorUnits - previous.minorUnits).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(previous.minorUnits), 0, RoundingMode.HALF_UP).toInt()`. Si `previous` es S/ 0.01 (1 céntimo) y `current` es un monto significativo como S/ 250,000.00 (25,000,000 minor units), el cálculo da `2,499,999,900`, superando `Int.MAX_VALUE` (2,147,483,647). La conversión `.toInt()` desborda a un entero negativo.
- Escenario concreto de fallo: Un usuario con un gasto de S/ 0.01 en el mes anterior y S/ 30,000 en el mes actual ve un porcentaje negativo o distorsionado en la tarjeta de KPI de gastos.
- Fix mínimo: Acotar el porcentaje resultante con `.coerceIn(-999_999, 999_999)` o usar `Long` antes de convertir.
- Confianza: alta
- Cross-módulo: no

### [UI1-18] `HomeNetWorthViewModel` oculta el estado protegido biométrico (`Protected`) bajo `Loading` perpetuo
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/home/presentation/HomeNetWorthViewModel.kt:65 y app/src/main/java/com/kipu/app/feature/home/presentation/NetWorthCard.kt:162-166
- Qué está mal: Cuando `sessionCoordinator.localAccess` es `LocalAccess.Protected` (la app requiere desbloqueo biométrico), `HomeNetWorthViewModel` emite `NetWorthUiState.Loading` en lugar de un estado explícito de bloqueo. En la pantalla de inicio, la tarjeta de patrimonio muestra permanentemente un esqueleto de carga con el texto de accesibilidad "Calculando patrimonio", sugiriendo que la app está trabada o calculando datos, en lugar de indicar que está bloqueada por seguridad.
- Escenario concreto de fallo: Usuario con bloqueo biométrico configurado abre la app y permanece en la pantalla bloqueada: la tarjeta de patrimonio parpadea con skeleton loaders como si estuviera pendiente de respuesta de red.
- Fix mínimo: Agregar `data object Locked : NetWorthUiState` y mostrar un icono o etiqueta de bloqueo como en `AnalyticsScreen`.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `ObserveAnalyticsOverview.invoke` (app/src/main/java/com/kipu/app/feature/analytics/domain/ObserveAnalyticsOverview.kt:38-80) → Test mínimo que falta: `ObserveAnalyticsOverviewTest` con repositorios mock que verifique que los vencimientos por pagar de préstamos y cuotas de tarjetas se sumen a `upcoming` agrupados por moneda hasta fin de mes, y que se seleccione la meta activa con mayor avance.
- `AnalyticsDao.observeIncomes` (app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:103-114) → Test mínimo que falta: inserción en `AnalyticsDaoTest` de transacciones `INCOME` con `operation_kind` estándar, `REFUND`, `DEBT_DISBURSEMENT` y `OPENING`, comprobando que solo los ingresos operativos sean emitidos.
- `AnalyticsDao.observeLedgerRows` (app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:117-124) → Test mínimo que falta: inserción en `AnalyticsDaoTest` de filas en `ledger_entries` vinculadas y desvinculadas de `transactions`, validando el fallback de `COALESCE(t.occurred_at, le.created_at)`.
- `AnalyticsRepository.observeFilterOptions` (app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsRepository.kt:35-56) → Test mínimo que falta: test unitario con DAO simulado que valide la deduplicación por ID de comercios y la resolución correcta de URLs de logos mediante `MerchantLogoUrlResolver`.
- `AnalyticsPeriod.previous` (app/src/main/java/com/kipu/app/feature/analytics/domain/Analytics.kt:23-32) → Test mínimo que falta: prueba unitaria con un mes de 28 días (febrero) verificando que el mes anterior resulte en 31 días completos (enero completo) en lugar de truncarse a 28 días.

## Lo que no revisé

- Se leyeron y auditaron completos los 16 archivos de `main`, los 9 archivos de `test` y los 3 archivos de `androidTest` asignados a UI1 (100% de los 28 archivos cubiertos).
- No se ejecutó código en dispositivos ni emuladores físicos (por regla dura de auditoría estática).
- No se auditaron las implementaciones internas de los casos de uso fuera de alcance (`ObserveFinancialDashboard`, `DebtRepository`, `GoalRepository`, `MovementFiltersCatalogViewModel`), limitándose la revisión a sus interfaces públicas y contratos de interacción.

## Dudas

- ¿La vista de comparación de Análisis fue concebida exclusivamente para comparar gastos operativos, o debería permitir comparar también ingresos y saldo neto entre periodos?
- ¿Por qué el resumen patrimonial (`ObserveNetWorthSummary`) y la vista de análisis (`ObserveAnalyticsOverview`) consumen `ObserveFinancialDashboard` de forma ambiental sin recibir el `userId`, mientras todas las demás llamadas del repositorio y casos de uso reciben el `userId` explícito?
- ¿Por qué el selector de meses (`selectableMonths`) está restringido a 12 meses hacia atrás sin paginación para usuarios antiguos, obligando a usar el selector de rango libre para meses anteriores?

# ACC2 — accounts/presentation/instruments (tarjetas, cuotas, tasas, formularios)

Esta porción tiene las pantallas para dar de alta cuentas, billeteras, efectivo y tarjetas (asistente de 3 pasos para tarjetas de crédito). También incluye la hoja "Editar tarjeta", el diálogo de pago de tarjeta, el simulador de cuotas, la pantalla de tasas (TEA personal frente al tarifario SBS) y el editor de clasificación de movimientos. Asumí miles de usuarios con uno o dos dispositivos, uso offline frecuente, bancos peruanos y tiendas por departamento (CMR, Ripley, Cencosud), y montos en PEN y USD.

## Hallazgos

### [ACC2-01] "Editar tarjeta" permite guardar un número de tarjeta completo (PAN) como alias
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CreditCardTermsSheet.kt:117-124 (campo alias sin validación); destino app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:788
- Qué está mal: el campo alias solo recorta a 40 caracteres. No llama a `Card.isForbiddenSensitiveCardInput`, cosa que el formulario de alta sí hacía. El repositorio escribe `aliasToSave` directo en Room y en la cola de sincronización, sin construir un `CreditCard` que lo valide.
- Escenario concreto de fallo: el usuario abre "Editar tarjeta" y escribe "4557 8800 1122 3344" (o "2024") en "Nombre visible o alias" → se guarda en la base local y se encola hacia Supabase. Al leer la tarjeta otra vez, `CardEntity.toDomain()` (repo:1628+) construye `CreditCard` y su `init` lanza `require(!isForbiddenSensitiveCardInput(alias))`. La tarjeta deja de cargarse o la pantalla falla.
- Fix mínimo: en `CreditCardTermsSheet`, `val validAlias = alias.isBlank() || !FinancialCard.isForbiddenSensitiveCardInput(alias)`, mostrar el error y sumarlo a `canSave`. El repositorio también debería rechazarlo (ACC5).
- Confianza: alta para la persistencia del PAN; media para el fallo posterior de lectura (no seguí cada colector del flujo).
- Cross-módulo: sí → ACC5 (el repositorio `updateCreditCardTerms` no valida el alias)

### [ACC2-02] El alta crashea la app con un alias válido para la UI pero inválido para el dominio
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt:455-533 (`handleSaveCreditCard` no valida el alias), :985-986 (alias de tarjeta personalizada sin límite), :1177-1182 y :541-549 (alias de cuenta sin tope de 80); AccountAddFormComponents.kt:130-173 (`AddFormTextField` sin `maxLength`)
- Qué está mal: `handleSaveCreditCard` envía el alias sin revisar si está vacío, si parece un PAN o CVV, ni su largo. `handleSubmit` revisa vacío y PAN, pero no el máximo de 80. `AccountsViewModel.registerCreditCard` y `createAccount` construyen `CreditCard` y `Account` dentro de `viewModelScope.launch` sin try. Los `require` de dominio lanzan la excepción y nadie la captura: el repo no tiene `CoroutineExceptionHandler`.
- Escenario concreto de fallo: el asistente de crédito, con "Registrar tarjeta personalizada", nombre "1234" (o 81 caracteres) y "Guardar tarjeta" → `IllegalArgumentException` en `Card.validateCardCommon` → la app se cierra. Lo mismo pasa con una cuenta, billetera o efectivo con un alias de 81 caracteres (`Account.init`, Account.kt:81).
- Fix mínimo: en el formulario, `onValueChange = { alias = it.take(80) }` en los dos campos de alias, y en `handleSaveCreditCard` repetir el chequeo de `handleSubmit` (vacío / `Card.isForbiddenSensitiveCardInput`) antes de llamar al VM. En ACC4, envolver la construcción en `runCatching` y emitir `AccountUiEvent.Error`.
- Confianza: alta
- Cross-módulo: sí → ACC4 (`AccountsViewModel.registerCreditCard/createAccount` construyen el dominio sin capturar)

### [ACC2-03] Las tarjetas CMR Falabella, Ripley y Cencosud nunca encuentran su tasa referencial
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogContext.kt:52-64; origen UnifiedInstrumentFormScreen.kt:516 (`issuer = bank.name`) e InstrumentFormModels.kt:112-144
- Qué está mal: el alta guarda `issuer = bank.name` ("Tarjeta CMR Falabella", "Tarjeta Ripley", "Tarjeta Cencosud / Metro", "Banco de la Nación"). `resolveRateCatalogContext` exige `product.institutionCode.equals(card.issuer, ignoreCase = true)`, y el código es "BANCO_FALABELLA", "RIPLEY", "CENCOSUD" o "BANCO_NACION". Solo funciona cuando nombre y código coinciden (BCP, BBVA, Interbank, Scotiabank, BanBif). El fallback `CardStylePresets.forProduct(card.issuer, ...)` falla por la misma razón.
- Escenario concreto de fallo: el usuario registra "CMR Visa" eligiéndola del catálogo SBS (paso 2 filtra por `bank.code` = BANCO_FALABELLA) y abre "Tasas de Interés y TEA" → ve "No hay una referencia inequívoca para CMR Visa", aunque el producto exacto está en el catálogo.
- Fix mínimo: en `resolveRateCatalogContext`, normalizar el emisor antes de comparar: `val issuerCode = bankChoices.firstOrNull { it.name.equals(card.issuer, true) }?.code ?: card.issuer`, y usar `issuerCode` en el filtro y en `forProduct`. Añadir un caso a `RateCatalogContextTest` con `issuer = "Tarjeta CMR Falabella"`.
- Confianza: alta
- Cross-módulo: no

### [ACC2-04] La ficha del tarifario muestra cifras inventadas como "datos oficiales SBS"
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt:1177, :1188-1193, :1215-1273, :1322, :1354, :1389, :1405-1432; también :898-900 y :1036-1048
- Qué está mal: `ReferentialRateCard` muestra lo siguiente para cualquier producto:
  - "Disposición de efectivo 81.50%" fijo.
  - TCEA "161.74%" si `publishedTceaSummary` es null.
  - "100% Exonerable: Consumo mínimo mensual de S/ 1,200…" si no hay condición.
  - "Datos oficiales verificados al 24/09/2026" si no hay fecha.
  - "Cálculo a 12 cuotas de S/ 1,000" y la membresía en `membershipFeePenMinor` con "S/", incluso para una tarjeta en USD.

  El botón "Ver tarifario oficial" no tiene `onClick` y `sourceUrl` nunca se usa. `PersonalTeaComparisonCard`, cuando el producto no publica TEA (caso cubierto por test), dibuja un rango de 0.00% a 100.00% como "Tasa mínima" y "Tasa máxima SBS".
- Escenario concreto de fallo: una tarjeta BBVA en USD con un producto resuelto que no publica TCEA ni membresía en USD → la pantalla dice "TCEA 161.74%", "TEA de retiro 81.50%" y "Membresía S/ …", y además "Transparencia SBS Regulada · Datos oficiales verificados". El usuario decide con números que no son de su tarjeta.
- Fix mínimo: borrar la tarjeta "Disposición de efectivo" y todos los literales de fallback. Mostrar "No publicado" cuando el campo sea null, usar `membershipFeeUsdMinor` y el símbolo según `currency`, y quitar el botón (o abrir `sourceUrl`). En la comparativa, no dibujar la barra si `minBps` o `maxBps` es null. Es casi todo borrado.
- Confianza: alta
- Cross-módulo: no

### [ACC2-05] La pantalla de tasas propone una TEA de 42% inventada como "tu tasa"
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt:133
- Qué está mal: si la tarjeta no tiene `personalTeaBps`, el campo se rellena con "42.00". La comparativa dibuja "Tu tasa: 42.00%" y el botón "Actualizar mi TEA" queda activo.
- Escenario concreto de fallo: una tarjeta recién creada sin TEA → el usuario entra a "Tasas", ve 42.00% y toca "Actualizar mi TEA" pensando que es su dato → se guarda 4200 bps y las simulaciones de cuotas usan una tasa que no es la de su contrato.
- Fix mínimo: `personalTeaInput = draft` (vacío si no hay dato). El botón ya se desactiva con el campo en blanco.
- Confianza: alta
- Cross-módulo: no

### [ACC2-06] El alta de crédito no avisa de tarjetas duplicadas (regla de la spec)
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt:455-533 (y 600-634 para débito); la lógica solo existe en CardFormScreen.kt:468-477, que es código muerto
- Qué está mal: specs/002-ep-cta-cuentas-tarjetas/spec.md:268 exige que, si emisor, red y últimos 4 dígitos coinciden, "el segundo registro exige advertencia y alias distinguible". La pantalla en producción nunca llama a `viewModel.checkDuplicateCard`. Además, con un producto de catálogo el alias es siempre el nombre del producto (línea 510).
- Escenario concreto de fallo: el usuario registra dos veces "Visa Oro LATAM Pass ••••1234" de BCP → se crean dos tarjetas idénticas sin aviso, con el mismo nombre visible, y no se distinguen en el dashboard ni en el selector de pago.
- Fix mínimo: en `handleSaveCreditCard`, antes de `registerCreditCard`, hacer `scope.launch { if (viewModel.checkDuplicateCard(bank.name, network, lastFourDigits)) { mostrar aviso; exigir alias editable } }`, que es lo mismo que hace CardFormScreen. Después, borrar CardFormScreen (ACC2-15).
- Confianza: alta
- Cross-módulo: no

### [ACC2-07] Controles visibles que no hacen nada en la pantalla de tasas
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt:169-174 (Compartir y Ayuda con `onClick` vacío), :389 y :589-646 (switch "Aplicar en próximas simulaciones"), :1405-1432 ("Ver tarifario oficial")
- Qué está mal: el switch solo cambia un `remember` local que nadie lee. El guardado siempre persiste la TEA y la usa el simulador. Los iconos Compartir y Ayuda, y el botón del tarifario, son decorativos pero TalkBack los anuncia como botones.
- Escenario concreto de fallo: el usuario apaga "Aplicar en próximas simulaciones" y guarda → la TEA se usa igual en el simulador de cuotas, al contrario de lo que dice el control.
- Fix mínimo: borrar el switch, los dos `IconButton` y la tarjeta-botón (o cablearlos si producto los quiere).
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 70 líneas

### [ACC2-08] La TEA se interpreta con reglas distintas al crear la tarjeta y en la pantalla de tasas
- Severidad: media
- Categoría: fusionar
- Ubicación: UnifiedInstrumentFormScreen.kt:495-498 (`MoneyInputParser`, sin tope) frente a RateCatalogScreen.kt:244-247, :391, :696-702 (`toDoubleOrNull` + `roundToInt`, tope 1000%)
- Qué está mal: el mismo dato (TEA en bps) se valida con dos reglas. En el alta se acepta hasta `Int.MAX_VALUE` bps y se rechazan 3 decimales. En tasas se acepta 0 a 1000% y se redondean 3 decimales.
- Escenario concreto de fallo: en el paso 3 el usuario escribe "4550" queriendo decir 45.50 → se guarda 4550% sin aviso. La misma entrada en la pantalla de tasas se rechaza con "Ingresa una TEA entre 0% y 1000%". Y "45.555" se rechaza en el alta, pero en tasas se guarda como 45.56%.
- Fix mínimo: crear una sola función `parseTeaBps(text): Int?` (por ejemplo, en RateCatalogContext.kt, que ya tiene `formatTeaDraft`), con `MoneyInputParser` y tope 100000, y usarla en los tres sitios.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 10 líneas

### [ACC2-09] El alta rechaza lo que la edición acepta: día de corte igual al de pago y línea 0
- Severidad: media
- Categoría: bug
- Ubicación: UnifiedInstrumentFormScreen.kt:460-486 (línea > 0, corte ≠ pago) frente a CreditCardTermsSheet.kt:71-74 (línea ≥ 0, sin regla corte ≠ pago)
- Qué está mal: la misma regla de la tarjeta se aplica distinto en dos sitios. El dominio (`CreditCard.init`) acepta los dos casos.
- Escenario concreto de fallo: el alta con corte 5 y pago 5 muestra el error "Elige días distintos". Si el usuario crea la tarjeta con 5/20 y luego la edita a 5/5, se guarda. Con línea 0 pasa lo mismo: el alta pide "> 0" y la edición guarda S/ 0.00, y la utilización queda en 0% para siempre.
- Fix mínimo: decidir la regla (ver Dudas) y aplicarla en un solo helper usado por los dos formularios.
- Confianza: alta
- Cross-módulo: no

### [ACC2-10] El editor de clasificación acumula colectores y puede mezclar datos de otro perfil
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/MovementClassificationEditorViewModel.kt:64-72 y :76-111
- Qué está mal: `observeData` lanza dos corrutinas nuevas en `viewModelScope` en cada emisión `Available` de `localAccess`. `collectLatest` cancela su bloque, pero no estos hijos, porque se lanzan en otro scope. Los colectores viejos siguen escribiendo en `_uiState`.
- Escenario concreto de fallo: con el editor abierto, la sesión remota pasa de Absent a Present (nuevo valor `Available`) → hay 4 colectores. Si cambia el propietario local, los colectores del usuario anterior siguen poniendo sus categorías en `availableCategories`.
- Fix mínimo: en `observeData`, `coroutineScope { launch { … }; launch { … } }` y llamarlo como suspend dentro de `collectLatest`. Así se cancela al cambiar el acceso.
- Confianza: media (no verifiqué cada cuándo `localAccess` reemite `Available`)
- Cross-módulo: no

### [ACC2-11] El editor de clasificación pisa la selección sin guardar del usuario
- Severidad: media
- Categoría: bug
- Ubicación: MovementClassificationEditorViewModel.kt:92-109
- Qué está mal: cada emisión del flujo de clasificación desde la base sobrescribe `selectedCategoryId` y el comercio, aunque el usuario ya haya elegido otros sin guardar.
- Escenario concreto de fallo: el usuario cambia la categoría a "Supermercado" y, antes de tocar "Guardar", llega una sincronización o hidratación que actualiza la fila del movimiento → la selección vuelve a la categoría anterior y se guarda la vieja.
- Fix mínimo: aplicar la clasificación de la base solo la primera vez, con `.first()` en lugar de `collectLatest`, o con un flag `userEdited`.
- Confianza: media
- Cross-módulo: no

### [ACC2-12] El editor de clasificación ofrece categorías inválidas y oculta el comercio real
- Severidad: media
- Categoría: bug
- Ubicación: MovementClassificationEditor.kt:353-371; MovementClassificationEditorViewModel.kt:101-103
- Qué está mal: el diálogo lista todas las categorías activas, incluidas las de INGRESO y las bloqueadas por plan. El simulador sí las filtra (InstallmentSimulatorViewModel.kt:102-104). Además, el comercio ya asignado se muestra con el texto fijo "Comercio asignado" en vez de su nombre.
- Escenario concreto de fallo: en un gasto, el usuario elige "Sueldo" (ingreso) → se guarda, porque el repositorio no compara tipos, y el gasto queda en la analítica de ingresos. Si elige una categoría bloqueada, falla con el mensaje en inglés "Category is blocked by the Free plan selection".
- Fix mínimo: filtrar `!isPlanLocked` y por tipo del movimiento, igual que el simulador. Resolver el nombre del comercio con el catálogo, o mostrar `provisionalText`.
- Confianza: media
- Cross-módulo: sí → CAT1 (el repositorio `updateMovementClassification` no valida tipo de categoría contra tipo de movimiento)

### [ACC2-13] El guardado del asistente de crédito y la resolución de tasas por emisor no tienen test
- Severidad: media
- Categoría: test-faltante
- Ubicación: UnifiedInstrumentFormScreen.kt:455-533; RateCatalogContextTest.kt (solo prueba emisores donde nombre = código)
- Qué está mal: el camino que crea tarjetas de crédito en producción (paso 3) no tiene test. Ese camino maneja el alias por producto, la TEA, la regla corte ≠ pago y `issuer = bank.name`. Los tests de formulario cubren cuentas, billeteras y la pantalla muerta CardFormScreen. `UnifiedInstrumentWalletAffiliationTest` crea `debitCards` (línea 51) y no lo usa.
- Escenario concreto de fallo: ACC2-02 y ACC2-03 entraron sin que falle ningún test.
- Fix mínimo: un test de unidad en `RateCatalogContextTest` con `issuer = "Tarjeta CMR Falabella"` y un producto `BANCO_FALABELLA` que espere `Resolved`. Un test Compose del paso 3 con alias "1234" que espere un error en pantalla y que `registerCreditCard` no se llame.
- Confianza: alta
- Cross-módulo: no

### [ACC2-14] El pago de tarjeta valida contra un saldo viejo de la cuenta origen
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/PayCardDialog.kt:67, :193-201
- Qué está mal: `selectedAccount` se recuerda sin clave y guarda el `AccountWithBalance` del momento en que se eligió. Si el dashboard se recarga, el control "Fondos insuficientes" sigue usando el saldo anterior. Si el diálogo se abre antes de cargar las cuentas, no queda ninguna preseleccionada.
- Escenario concreto de fallo: el diálogo está abierto, entra por sincronización un gasto de la otra cuenta del usuario que baja el saldo de S/ 500 a S/ 100, y el usuario paga S/ 300 → la UI no lo frena (depende de que el caso de uso lo rechace).
- Fix mínimo: guardar solo el id (`var selectedAccountId by remember { … }`) y resolver `matchingAccounts.firstOrNull { it.account.id == selectedAccountId } ?: matchingAccounts.firstOrNull()` en cada recomposición.
- Confianza: media
- Cross-módulo: no

### [ACC2-15] AccountFormScreen y CardFormScreen ya no se usan
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/AccountFormScreen.kt:59-304; CardFormScreen.kt (completo); app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/instruments/CardFormScreenTest.kt (completo)
- Qué está mal: la navegación usa solo `UnifiedInstrumentFormScreen` (AccountsNavigation.kt:183, :201). `AccountFormScreen` no tiene llamadores, y `CardFormScreen` solo lo llama su test. De AccountFormScreen.kt solo se usa `parseAmountToMinorUnits` (PayCardDialog.kt:184).
- Escenario concreto de fallo: el aviso de duplicados y los textos de cupo "1 de los 4 cupos" existen solo en estas pantallas, y sus tests pasan mientras la pantalla real no lo hace (ver ACC2-06).
- Fix mínimo: borrar los dos archivos y el test. Llamar a `MoneyInputParser.parseMinorUnits` directo desde PayCardDialog, o mover `parseAmountToMinorUnits` a PayCardDialog.kt.
- Confianza: alta (grep en main, test y androidTest)
- Cross-módulo: no
- Ahorro estimado: 900 líneas

### [ACC2-16] Código muerto dentro de UnifiedInstrumentFormScreen
- Severidad: baja
- Categoría: borrar
- Ubicación: UnifiedInstrumentFormScreen.kt:1298-1375 (`TreeBranchItem`), :1377-1461 (`BranchProductCard`), :1465-1486 (`ReferencialTeaCard`), :1530 (`percentage`), :113-119 (colores solo usados por esos composables), :421-453 (`parsedAmountForValidation`, `cycleIsValid`, `lastFourIsValid`, `cardBackground`, `previewStylePreset`, `cardForeground`, `previewTitle`), :258-262 y :296-300 (estado que solo se escribe), :313 y :318 (`cycleError` y su requester, nunca mostrados), :561-564, :580-584 y :636-656 (rama CREDIT_CARD de `handleSubmit`, inalcanzable porque CREDIT_CARD nunca llega a FORM_OTHER), :208 (`onNavigateToRecordConsumption`, nunca invocado), :1503-1519 (`toCardNetwork` y `networkFromCatalog` hacen lo mismo)
- Qué está mal: hay restos del diseño anterior que hay que leer y compilar, y que confunden. Por ejemplo, la rama de crédito de `handleSubmit` no aplica la regla corte ≠ pago y parece una segunda verdad.
- Escenario concreto de fallo: quien corrija ACC2-02 puede arreglar la rama muerta de `handleSubmit` y dejar rota la real, `handleSaveCreditCard`.
- Fix mínimo: borrar todo lo listado y el argumento `onNavigateToRecordConsumption` en AccountsNavigation.kt:191-194 y :209-212.
- Confianza: alta (conteo de usos por grep en todo `app/src`)
- Cross-módulo: no
- Ahorro estimado: 300 líneas

### [ACC2-17] "Recordar comercio" en el simulador solo vive en memoria
- Severidad: baja
- Categoría: borrar
- Ubicación: InstallmentSimulatorViewModel.kt:73, :141, :175, :197, :210
- Qué está mal: `rememberedCategories` es un mapa del ViewModel, que tiene clave por candidato. Se pierde al cerrar el simulador, así que el interruptor "Recordar" del selector no tiene efecto entre compras.
- Escenario concreto de fallo: el usuario crea la categoría "Gimnasio" con "Recordar" activado para "SmartFit" y confirma. En la siguiente compra en SmartFit no se sugiere nada.
- Fix mínimo: borrar el mapa y sus 4 usos. Si producto quiere recordar, la vía es la preferencia persistida de comercio (`ResolvePreferredCategory`, CAT1).
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 6 líneas

### [ACC2-18] UnifiedInstrumentFormScreen mezcla dos flujos en una función de ~1100 líneas
- Severidad: baja
- Categoría: dividir
- Ubicación: UnifiedInstrumentFormScreen.kt:203-1293
- Qué está mal: el asistente de crédito (3 pasos, `handleSaveCreditCard`) y el formulario de cuenta, billetera o efectivo (`handleSubmit`) comparten unas 60 variables de estado y dos validadores casi iguales. Por eso aparecen ACC2-02 (valida uno y el otro no) y ACC2-16.
- Escenario concreto de fallo: ver ACC2-02. La validación del alias se añadió en `handleSubmit` y no en `handleSaveCreditCard`.
- Fix mínimo: después de ACC2-16, extraer `CreditCardWizard(...)` con su propio estado. El composable raíz solo elige la ruta.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 0 líneas (gana legibilidad, no tamaño)

### [ACC2-19] Campos de modelo sin uso
- Severidad: baja
- Categoría: borrar
- Ubicación: InstrumentFormModels.kt:21 (`logoAcronym`, más las 10 asignaciones), :30-31 (`CatalogProductChoice.familyId/familyLabel`)
- Qué está mal: ningún archivo de `app/src` lee estos campos.
- Escenario concreto de fallo: ninguno funcional. Son líneas que hay que mantener al añadir bancos.
- Fix mínimo: borrarlos.
- Confianza: alta (grep `\.logoAcronym`, `familyId`)
- Cross-módulo: no
- Ahorro estimado: 13 líneas

Dejé fuera 4 hallazgos menores: los colores fijos amarillo-BCP en `CardContextHeader` para cualquier emisor, `personalTeaSaved` acoplado al prefijo "TEA personal" del mensaje, el `selectedAccount` inicial de CardFormScreen (pantalla muerta) y un mensaje de error en inglés que viene del repositorio.

## Código de riesgo sin tests
- `UnifiedInstrumentFormScreen.handleSaveCreditCard`: test Compose del paso 3 con alias "1234" → error visible y `registerCreditCard` no llamado. Otro caso: corte = pago → error.
- `resolveRateCatalogContext` con emisores retail: test de unidad `issuer = "Tarjeta CMR Falabella"` + producto `BANCO_FALABELLA` → `Resolved`.
- `CreditCardTermsSheet`: test Compose con alias "4557880011223344" → "Guardar cambios" deshabilitado.
- `MovementClassificationEditorViewModel`: test de unidad que emita `Available` dos veces y verifique una sola suscripción a `observeCategories`. Otro que emita una clasificación nueva tras `selectCategory` y verifique que no pisa la selección.
- `RateCatalogScreen` sin TEA guardada: test Compose que espere el campo vacío (no "42.00").

## Lo que no revisé
- Leí completos los 14 archivos main y los 6 tests de mi alcance.
- No audité `CalmEmeraldInstrumentComponents.kt`, `RateCatalogThemePreview.kt` ni `PayCardDialogTest.kt` (detail/), aunque usan o prueban esta porción: son de ACC4/ACC3.
- Del VM compartido `AccountsViewModel` (ACC4) y de los repositorios (ACC5, CAT1) solo leí los contratos que llaman estas pantallas.
- No ejecuté tests ni builds (auditoría estática).

## Dudas
- ¿El día de corte puede coincidir con el día de pago? El alta lo prohíbe y la edición y el dominio lo permiten (ACC2-09). ¿Se permite una línea de crédito 0?
- ¿Banco de la Nación tiene productos de crédito en el catálogo SBS? Si los tiene, también le afecta ACC2-03.
- ¿Qué debía hacer "Aplicar en próximas simulaciones"? Si es una preferencia real, falta modelarla. Si no, sobra (ACC2-07).
- ¿El editor de clasificación debe limitar las categorías al tipo del movimiento? Hoy el repositorio no lo exige (ACC2-12).
- ¿"Recordar comercio" debe persistir la preferencia comercio→categoría? Hoy ni el simulador ni el registro rápido (MOV3) la guardan.

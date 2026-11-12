# UI2 — settings + setup + plans/presentation

Esta porción gestiona la configuración de preferencias de usuario (divisa base, inicio del ciclo contable, tema y ocultamiento de saldos), seguridad biométrica local, permisos del sistema y consentimientos de captura (notificaciones y OCR), la incorporación inicial (setup/onboarding con selección de cuenta, catálogo inicial de categorías y confirmación OTP) y la selección y compra de planes (Free, Premium y Developer). Se asumió una carga de miles de usuarios, cada uno con uno o dos dispositivos, uso frecuente offline y sincronización reactiva en segundo plano con Supabase y Google Play Billing. La auditoría fue estática, basada en análisis exhaustivo de código fuente Kotlin, composables de Compose, entidades/DAOs de Room y suites de pruebas unitarias e instrumentadas.

## Hallazgos

### [UI2-01] Bloqueo irreversible en estado CONFLICT de sincronización de perfil por persistencia de revisión obsoleta
- Severidad: crítica
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepository.kt:181-188 y app/src/main/java/com/kipu/app/feature/settings/data/local/ProfilePreferencesDao.kt:54-78, 87-98
- Qué está mal: Cuando `update_profile_preferences` en Supabase detecta una divergencia de versiones (`p_expected_revision != revision`), devuelve `"CONFLICT"`. El repositorio marca la fila de outbox como `"CONFLICT"`, el perfil local como `SyncState.CONFLICT` y falla. En Room, `savePreferencesWithOutbox` solo busca filas con `status = 'PENDING'` (`findOldestPendingOutbox`); al haber una fila previa en conflicto, no la fusiona y crea una nueva fila con la revisión local antigua (`expectedRevision = current.remoteRevision`). Peor aún, al invocar `refreshProfile(userId)` para intentar recuperarse contra el servidor, `ProfilePreferencesDao.hydrateProfile` busca filas no resueltas mediante `findUnresolvedOutbox`. Como la fila con `status = 'CONFLICT'` sigue existiendo en el outbox, `unresolved.isNotEmpty()` es verdadero y `hydrateProfile` sobreescribe la revisión remota entrante con la revisión local obsoleta (`remoteRevision = local.remoteRevision, syncState = local.syncState`). Como resultado, el perfil queda atrapado para siempre en conflicto: ninguna sincronización futura tendrá éxito y ningún refresco remoto podrá elevar la revisión local.
- Escenario concreto de fallo: Un usuario modifica una preferencia (ej. cambia de tema o divisa) en otro dispositivo o en la web, elevando la revisión en Supabase a 2. En el dispositivo local (en revisión 1), cambia el día de inicio contable a 5. El worker de sincronización intenta aplicar el cambio con `p_expected_revision = 1`; el servidor responde `CONFLICT`. El outbox queda en estado `CONFLICT`. Cuando el usuario abre Ajustes, la app invoca `refreshProfile`, el cual obtiene del servidor la revisión 2; sin embargo, `hydrateProfile` detecta el outbox en `CONFLICT`, descarta la revisión 2 y reescribe `remoteRevision = 1`. A partir de ese momento, cualquier ajuste futuro del usuario se emitirá con revisión 1 y chocará indefinidamente contra el servidor sin posibilidad de recuperación.
- Fix mínimo: En `ProfilePreferencesDao.hydrateProfile`, si las outboxes no resueltas están en estado `CONFLICT`, no forzar `remoteRevision = local.remoteRevision`; adoptar `remote.remoteRevision` del servidor y marcar o purgar las outboxes conflictivas para permitir que las nuevas ediciones locales partan de la revisión fresca del servidor.
- Confianza: alta
- Cross-módulo: no

### [UI2-02] Corrupción financiera de saldo inicial al ingresar decimales con coma en `SetupAccountDraft` (error de 100x)
- Severidad: crítica
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/setup/SetupDraft.kt:43-50 y app/src/main/java/com/kipu/app/feature/setup/SetupScreens.kt:173
- Qué está mal: En `SetupDraft.balanceMinorUnits`, el cálculo del importe en céntimos realiza:
  `val text = balanceText.trim().replace(",", "")`
  `val value = text.toBigDecimalOrNull() ?: return null`
  `return value.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toLong()`
  En Perú y en dispositivos Android configurados en español, el teclado numérico de tipo `KeyboardType.Decimal` frecuentemente suministra o permite ingresar la coma `,` como separador decimal. Al ejecutar `replace(",", "")` incondicionalmente, un saldo de `150,50` o `12,50` se transforma en `"15050"` o `"1250"`, multiplicando el valor por 100x. El saldo resultante en `balanceMinorUnits` es `1505000L` (S/ 15,050.00 en vez de S/ 150.50).
- Escenario concreto de fallo: Un usuario nuevo en el paso 1 del setup elige cuenta bancaria BCP e ingresa `150,50` soles en el campo "Saldo inicial". `balanceMinorUnits` calcula `1505000L`. `SetupFinalizer` crea la cuenta y su balance de apertura con S/ 15,050.00 en el libro contable de Room en vez de S/ 150.50, corrompiendo el patrimonio inicial del usuario en cien veces su valor real.
- Fix mínimo: Normalizar la coma decimal antes de purgar separadores de miles: si el texto contiene coma y no punto, reemplazar `,` por `.`: `val text = if ("," in balanceText && "." !in balanceText) balanceText.trim().replace(',', '.') else balanceText.trim().replace(",", "")`.
- Confianza: alta
- Cross-módulo: no

### [UI2-03] Carrera de estados en `PlanPurchaseViewModel` permite compras concurrentes en Google Play
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseViewModel.kt:30-36, 44-55
- Qué está mal: En `PlanPurchaseViewModel.selectOffer(productId)`, la función actualiza el estado directamente:
  `state.copy(selectedProductId = productId, status = if (state.effectivePremium) PlanPurchaseStatus.VERIFIED else PlanPurchaseStatus.READY, errorCode = null)`
  Si un proceso de compra o verificación ya está en vuelo (`status == LAUNCHING` o `VERIFYING`), las tarjetas de oferta en `PlanPurchaseScreen` no deshabilitan su `onClick`. Al tocar otra tarjeta, `status` se reinicia a `READY`. Esto reactiva de inmediato el botón principal `purchase-cta` (`enabled = state.status !in setOf(PlanPurchaseStatus.LAUNCHING, PlanPurchaseStatus.VERIFYING)`). Si el usuario pulsa el botón de nuevo, `startPurchase` supera la validación y lanza una segunda llamada simultánea a `repository.startPurchase(activity, offer)` contra el `BillingClient` de Google Play, lo que desencadena excepciones de estado ilegal (`IllegalStateException`) o transacciones duplicadas.
- Escenario concreto de fallo: Usuario pulsa "Comprar" en el plan anual. Mientras la pasarela de Google Play Billing se inicializa (`status = LAUNCHING`), el usuario toca la tarjeta del plan mensual. El estado pasa inmediatamente a `READY`. El botón "Comprar" vuelve a quedar activo; el usuario lo vuelve a presionar. Se invocan dos flujos de facturación concurrentes sobre la misma Activity.
- Fix mínimo: En `selectOffer`, no mutar `status` si `state.status == PlanPurchaseStatus.LAUNCHING || state.status == PlanPurchaseStatus.VERIFYING`.
- Confianza: alta
- Cross-módulo: no

### [UI2-04] Bloqueo permanente de desactivación biométrica si el dispositivo pierde la capacidad de autenticar
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsScreen.kt:211-215 y app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt:83-89
- Qué está mal: En `BiometricSettingsScreen.kt`, el `Switch` de activación/desactivación del bloqueo biométrico define:
  `Switch(checked = state.isLocalUnlockEnabled, onCheckedChange = { onToggleLocalUnlock() }, enabled = state.canAuthenticate)`
  Si un usuario tenía el bloqueo local activado y luego desconfigura su huella digital, borra su PIN de pantalla en Android o sufre una falla de hardware en el sensor biométrico, `canAuthenticate` pasa a ser `false`. Como el `Switch` queda inhabilitado con `enabled = false`, el usuario NO PUEDE apagar el bloqueo local desde la interfaz. Cada vez que la app entra en segundo plano, `LocalLockCoordinator` activará la pantalla de bloqueo pidiendo una autenticación imposible de cumplir, bloqueando al usuario fuera de su cuenta sin opción de desactivación.
- Escenario concreto de fallo: Usuario con "Bloqueo Local" activado decide borrar sus huellas dactilares registradas en los ajustes de Android. Abre Kipu para desactivar la protección biométrica en la app. Al entrar a Ajustes > Bloqueo local, encuentra el switch apagado/gris sin posibilidad de tocarlo. Al salir de la pantalla, la app se bloquea y el usuario no puede volver a ingresar.
- Fix mínimo: Permitir la interacción del switch siempre que ya esté activo: `enabled = state.canAuthenticate || state.isLocalUnlockEnabled`.
- Confianza: alta
- Cross-módulo: no

### [UI2-05] Vulnerabilidad de seguridad: `toggleLocalUnlock` desactiva la protección biométrica sin solicitar autenticación previa
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt:73-82
- Qué está mal: Para habilitar el bloqueo biométrico, `toggleLocalUnlock` exige autenticación vía `gateway.authenticate(activity, ...)` con biometría o PIN. Sin embargo, para desactivarlo (`currentEnabled == true`), la función apaga directamente la protección en Room y en memoria sin requerir autenticación alguna:
  `lockCoordinator.setLocalUnlockEnabled(userId, false)`
  Cualquier persona que acceda al teléfono desbloqueado por unos segundos puede ingresar a Ajustes > Bloqueo local y desactivar permanentemente la protección biométrica con un solo toque, vulnerando la privacidad de todos los datos financieros.
- Escenario concreto de fallo: Un usuario presta su teléfono para ver una foto o hacer una llamada. El tercero abre Kipu (que en ese momento no ha bloqueado por timeout de 60s), entra a Ajustes > Bloqueo local y toca el switch. La protección se desactiva instantáneamente sin pedir confirmación de huella ni PIN del dispositivo.
- Fix mínimo: Requerir `gateway.authenticate` también cuando `currentEnabled == true` antes de llamar a `lockCoordinator.setLocalUnlockEnabled(userId, false)`.
- Confianza: alta
- Cross-módulo: no

### [UI2-06] Mutaciones concurrentes y encolamiento duplicado en WorkManager al alternar `hideBalances` en `ProfileSettingsScreen`
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:426-429
- Qué está mal: En `ProfileSettingsScreen.kt`, al presionar el switch "Ocultar saldos e importes", el callback ejecuta:
  `onCheckedChange = { viewModel.toggleHideBalances(); viewModel.savePreferences() }`
  `toggleHideBalances()` lanza una corrutina que actualiza el perfil en Room con `setHideBalances` y programa un trabajo único en WorkManager con `profileSyncScheduler.scheduleSync(userId)`. Inmediatamente después, `savePreferences()` lanza OTRA corrutina que guarda el perfil con `updatePreferences` y vuelve a programar WorkManager con `ExistingWorkPolicy.REPLACE`. Como `toggleHideBalances()` no utiliza `saveMutex`, ambas corrutinas acceden a Room en paralelo. Además, `ProfilePreferenceDelta` en `savePreferences()` ni siquiera propaga `hideBalances`, por lo que la segunda llamada es un guardado redundante y duplicado que compite por la base de datos.
- Escenario concreto de fallo: Usuario cambia el switch de ocultar saldos. Dos corrutinas separadas disparan transacciones Room simultáneas y dos encolamientos consecutivos en WorkManager, generando escrituras de outbox redundantes.
- Fix mínimo: Eliminar la llamada `viewModel.savePreferences()` dentro del `onCheckedChange` de la línea 428; `toggleHideBalances()` ya realiza la persistencia completa.
- Confianza: alta
- Cross-módulo: no

### [UI2-07] Carrera de hilos por variable no sincronizada `pendingSaveRequests` en `SettingsViewModel`
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/SettingsViewModel.kt:74, 182, 200, 204, 209
- Qué está mal: `pendingSaveRequests` es un campo mutable primitivo `private var pendingSaveRequests = 0` sin `@Volatile` ni sincronización atómica. En `savePreferences()`, se incrementa fuera del lock (`pendingSaveRequests += 1`) en el hilo de llamada de la UI, mientras que dentro de la corrutina en background se decrementa dentro de `saveMutex.withLock` (`pendingSaveRequests -= 1`). Ante eventos rápidos consecutivos (por ejemplo, pulsaciones rápidas del stepper de corte contable `stepMonthStart`), los accesos multihilo no atómicos provocan lecturas y escrituras desincronizadas, lo que puede causar que `pendingSaveRequests` quede en un valor mayor a cero y mantenga permanentemente `isSaving = true` en la UI.
- Escenario concreto de fallo: El usuario presiona rápidamente varias veces el botón `+` para ajustar el día de inicio contable. La carrera de datos deja `pendingSaveRequests` con valor 1 tras completarse todas las escrituras, dejando `isSaving = true` indefinidamente y deshabilitando el botón de refresco de plan de pruebas.
- Fix mínimo: Mover el incremento de `pendingSaveRequests` al interior de `saveMutex.withLock` o tipar la variable como `AtomicInteger`.
- Confianza: alta
- Cross-módulo: no

### [UI2-08] Posible pérdida de evento único `Confirmed` en `PlanSelectionRoute` ante recreación del ciclo de vida
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionViewModel.kt:27-28 y app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionRoute.kt:14-20
- Qué está mal: `PlanSelectionViewModel` expone sus eventos mediante `private val eventChannel = Channel<PlanSelectionEvent>(Channel.BUFFERED)` y `val events = eventChannel.receiveAsFlow()`. En `PlanSelectionRoute`, el colector usa `repeatOnLifecycle(Lifecycle.State.STARTED)`. Si la confirmación ocurre en el instante exacto en que la Activity entra en `STOPPED` (por ejemplo, el usuario rota la pantalla o entra una llamada en el momento de confirmar), el colector se cancela. Al reanudarse en `STARTED`, `receiveAsFlow()` no preserva el evento si el canal fue recreado o si se suscribe un nuevo colector en una recomposición estructural, perdiéndose la señal de navegación.
- Escenario concreto de fallo: El usuario confirma el plan y gira el dispositivo inmediatamente. El evento de confirmación se emite durante la transición de ciclo de vida y no es recibido tras el reinicio, dejando al usuario congelado en la pantalla de selección a pesar de haber confirmado su elección.
- Fix mínimo: Sustituir `Channel.receiveAsFlow()` por un `MutableSharedFlow<PlanSelectionEvent>(extraBufferCapacity = 1)` que no pierda eventos durante cambios de recolector.
- Confianza: media
- Cross-módulo: no

### [UI2-09] `SetupDraftStore.bindOwner` descarta el correo del usuario si se invoca con owner previo
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/setup/SetupDraft.kt:89-93
- Qué está mal: La función `bindOwner` implementa:
  `fun bindOwner(owner: String) { if (ownerId == owner) return; _draft.value = SetupDraft(email = if (ownerId == null) _draft.value.email else ""); ownerId = owner }`
  Si por alguna razón la sesión ya tenía un `ownerId` registrado (por ejemplo, al inicializar `SetupViewModel.init` con un usuario previo o al verificar la cuenta), cualquier llamada subsecuente a `bindOwner` borra el campo `email` dejándolo en blanco (`""`). Posteriormente, en `SetupViewModel.confirmAll()`:
  `curtain.start(store.draft.value.email, registering = true)`
  Se pasa un correo vacío a `SessionEntryCurtain`, afectando la presentación del saludo inicial.
- Escenario concreto de fallo: Un usuario que inició sesión previamente se registra con una nueva cuenta; al verificarse el OTP e invocarse `store.bindOwner(userId)`, el borrador descarta el email y la cortina de bienvenida posterior muestra un texto en blanco sin dirección de correo.
- Fix mínimo: Preservar el correo si el borrador ya lo contenía: `_draft.value = SetupDraft(email = _draft.value.email)`.
- Confianza: media
- Cross-módulo: no

### [UI2-10] Cast directo `context as? FragmentActivity` en `BiometricSettingsScreen` falla silenciosamente ante `ContextWrapper`
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsScreen.kt:124-126
- Qué está mal: En `BiometricSettingsScreen.kt`, al presionar el switch se obtiene la Activity con:
  `val activity = context as? FragmentActivity`
  `if (activity != null) viewModel.toggleLocalUnlock(activity)`
  En Jetpack Compose, `LocalContext.current` es comúnmente una instancia de `ContextThemeWrapper`, `TintContextWrapper` o wrappers inyectados por temas/Hilt. Un cast directo `as? FragmentActivity` evalúa a `null`. En ese caso, la pulsación del switch no produce ninguna acción ni mensaje de error. En `PlanPurchaseRoute.kt:41-45` se implementó correctamente una función de desenredo recursivo `findActivity()`, pero se omitió en esta pantalla.
- Escenario concreto de fallo: En dispositivos donde el contexto es envuelto por el tema de Compose o inyección de recursos, el usuario pulsa para activar el bloqueo biométrico y el switch no responde ni lanza el prompt biométrico.
- Fix mínimo: Usar una función recursiva de desenredo:
  `private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) { is FragmentActivity -> this; is ContextWrapper -> baseContext.findFragmentActivity(); else -> null }`.
- Confianza: alta
- Cross-módulo: no

### [UI2-11] Control fantasma en `ProfileSettingsScreen`: "Alertas de vencimiento" es texto estático sin modelo subyacente
- Severidad: media
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:439-472
- Qué está mal: La tarjeta "Alertas de vencimiento - Días de anticipación para recordatorios" muestra un badge con "3 días" completamente cableado en código estático. No tiene `clickable`, no responde a toques, y no existe ningún campo en `UserProfile`, `UserProfileCacheEntity`, ni `SettingsUiState` para almacenar o modificar esta preferencia. Es un control ficticio que confunde al usuario haciéndole creer que puede configurar sus alertas.
- Escenario concreto de fallo: El usuario intenta cambiar los días de anticipación de sus recordatorios de vencimiento; toca la tarjeta repetidamente y no reacciona, generando frustración y reportes de bug de UI.
- Fix mínimo: Eliminar la tarjeta hasta que el modelo de datos de preferencias soporte la configuración de alertas de vencimiento, o enlazarla a la pantalla correspondiente si pertenecía a Notificaciones (`UI3`).
- Ahorro estimado: 34 líneas.
- Confianza: alta
- Cross-módulo: sí → UI3

### [UI2-12] Omisión generalizada de externalización i18n en pantallas de ajustes, permisos y biometría
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsScreen.kt:125, 171, 249, 257, 348, 378, 390, 400; ProfileSettingsScreen.kt:150, 184, 255, 329, 421, 533; BiometricSettingsScreen.kt:87, 166, 172, 203, 205, 237, 256, 262
- Qué está mal: A diferencia de `PlanPurchaseScreen.kt` y `PlanSelectionScreen.kt` que usan rigurosamente `stringResource(R.string...)`, los módulos de `PermissionsScreen`, `ProfileSettingsScreen` y `BiometricSettingsScreen` tienen decenas de textos de interfaz de usuario hardcodeados en español ("Ajustes", "Bloqueo Local", "Permisos y Automatización", "Fuentes Disponibles", "Aceptar y Continuar", "Cerrar", "Explicación", "Revocar", "Habilitar", "Abrir ajustes", "Cerrar sesión", etc.). Esto impide la localización a otros idiomas o la personalización de textos según lineamientos del sistema.
- Escenario concreto de fallo: Si se despliega la app en inglés u otro idioma en Google Play, las pantallas de ajustes, permisos y biometría permanecen completamente en español.
- Fix mínimo: Extraer todas las cadenas literales a `res/values/strings.xml` y consumirlas mediante `stringResource(R.string...)`.
- Confianza: alta
- Cross-módulo: no

### [UI2-13] Falta de pruebas para la resolución de conflictos remotos en `OfflineFirstProfilePreferencesRepository`
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepositoryTest.kt:64-71
- Qué está mal: El test existente `a row already in conflict is not merged into` solo valida que una fila en `CONFLICT` no se fusione con una nueva fila `PENDING`. No existe ninguna prueba que verifique qué ocurre cuando el worker recibe un `CONFLICT` desde el backend Supabase, ni cómo el repositorio recupera el estado de sincronización cuando se llama a `refreshProfile` o `hydrateProfile`. De hecho, como se demostró en el hallazgo UI2-01, el código actual no puede recuperarse nunca.
- Escenario concreto de fallo: Al simular un conflicto de revisión devuelto por Supabase, el sistema queda bloqueado indefinidamente sin que ninguna prueba automatizada falle ni alerte de la imposibilidad de reanudar la sincronización.
- Fix mínimo: Añadir un test unitario `syncPendingPreferences_recovers_from_server_conflict_after_refresh` que simule una respuesta CONFLICT del RPC, invoque `refreshProfile` y compruebe que la revisión local se actualiza a la del servidor y que el outbox se desbloquea.
- Confianza: alta
- Cross-módulo: no

### [UI2-14] Falta de prueba unitaria para números decimales con coma en `SetupDraft`
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/setup/SetupViewModelTest.kt:90-101
- Qué está mal: En `SetupViewModelTest`, el test `balance text accepts at most two decimals and never negatives` valida únicamente `"1,250.50"` (coma como separador de miles y punto como decimal). No contiene ningún caso de prueba para números ingresados con coma decimal como `"150,50"` o `"1250,50"`. Por este motivo, el bug que multiplica por 100 el saldo inicial pasó inadvertido en el CI.
- Escenario concreto de fallo: Un test con `balanceText = "150,50"` falla de inmediato en el código actual al comprobar que `balanceMinorUnits` devuelve `1505000L` en lugar de `15050L`.
- Fix mínimo: Añadir la aserción `assertEquals(15_050L, base.copy(balanceText = "150,50").balanceMinorUnits)` en `SetupViewModelTest`.
- Confianza: alta
- Cross-módulo: no

### [UI2-15] Reasignación e instanciación repetida de `selectTheme` en `ProfileSettingsScreen` en cada recomposición
- Severidad: baja
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:313-324
- Qué está mal: La lambda `selectTheme` se declara directamente en el cuerpo del composable:
  `val selectTheme: (ThemeMode) -> Unit = { mode -> ... }`
  sin estar envuelta en `remember`. Como `ProfileSettingsScreen` se recompone ante cualquier cambio de estado (ej. al actualizar `isSaving`, `syncState` o recibir actualizaciones periódicas), se asigna una nueva instancia de lambda en cada frame, invalidando los composables hijos `ThemeOptionChip` y forzando recomposiciones innecesarias de la barra de temas.
- Escenario concreto de fallo: Recomposiciones innecesarias de los 3 chips de tema cada vez que cambia cualquier campo en `SettingsUiState` (como `isSaving` o el reloj de sincronización).
- Fix mínimo: Envolver `selectTheme` en `remember(themeContext, reducedMotion, currentlyDark, systemDark) { { mode -> ... } }`.
- Confianza: alta
- Cross-módulo: no

### [UI2-16] Código muerto: `PermissionSourceGateway.createSettingsIntent` no tiene consumidores en producción
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/settings/domain/PermissionSourceGateway.kt:9 y app/src/main/java/com/kipu/app/feature/settings/data/AndroidPermissionSourceGateway.kt:51
- Qué está mal: La interfaz `PermissionSourceGateway` declara `fun createSettingsIntent(source: PermissionSource): Intent`. Ninguna clase del dominio, presentación o casos de uso la invoca en producción. La navegación a ajustes se realiza a través de `AndroidPermissionSettings.open(context, source)` o delegados de UI. Mantenerla en el gateway de dominio acopla innecesariamente la interfaz de dominio a `android.content.Intent`.
- Escenario concreto de fallo: Contrato hinchado que obliga a implementar intents ficticios en todos los mocks y fakes de tests (`Intent()`, `error("unused")`).
- Fix mínimo: Eliminar `createSettingsIntent` de `PermissionSourceGateway` y de `AndroidPermissionSourceGateway`.
- Ahorro estimado: 6 líneas en código principal y más de 25 líneas en mocks/fakes de pruebas.
- Confianza: alta
- Cross-módulo: no

### [UI2-17] `PlanPurchaseScreen` asume `ZoneId.systemDefault()` para cálculo de fechas de prueba y expiración
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt:220, 452-454
- Qué está mal: En `PlanPurchaseScreen.kt`, el primer cobro de la prueba se calcula como `LocalDate.now().plusDays(offer.trialPeriodDays.toLong())` y las fechas de vigencia usan `Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()`. Si un usuario peruano viaja al extranjero o su dispositivo se encuentra en zona horaria UTC u otra distinta a `America/Lima`, cerca de la medianoche (23:30 Lima = 04:30 UTC del día siguiente), la fecha mostrada para el cobro o vencimiento se desfasa un día respecto a la fecha contable oficial de Kipu.
- Escenario concreto de fallo: Un usuario consulta su plan a las 23:45 del 11 de noviembre en un teléfono con hora UTC; la pantalla calcula la fecha base como 12 de noviembre y muestra una fecha de cobro desfasada por un día.
- Fix mínimo: Utilizar la zona horaria oficial `ZoneId.of("America/Lima")` para cálculos de periodicidad y vigencia de planes.
- Confianza: media
- Cross-módulo: no

### [UI2-18] Duplicación redundante de `AndroidPermissionSettingsTest` entre robolectric y androidTest
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/test/java/com/kipu/app/feature/settings/data/AndroidPermissionSettingsTest.kt (28 líneas) vs app/src/androidTest/java/com/kipu/app/feature/settings/data/AndroidPermissionSettingsTest.kt (70 líneas)
- Qué está mal: Ambos archivos prueban exactamente lo mismo: que en API 24 se use `ACTION_APPLICATION_DETAILS_SETTINGS` y en API 26+ se use `ACTION_APP_NOTIFICATION_SETTINGS`. La suite de tests unitarios locales con Robolectric (`src/test`) ya cubre y valida ambos SDKs en menos de 100 ms. Mantener el duplicado idéntico en `src/androidTest` alarga los tiempos de pruebas instrumentadas sin aportar cobertura diferencial.
- Escenario concreto de fallo: Duplicación de código de prueba que requiere doble mantenimiento ante cambios en los intents del sistema.
- Fix mínimo: Eliminar el archivo duplicado `app/src/androidTest/.../AndroidPermissionSettingsTest.kt` conservando la suite rápida de Robolectric en `src/test`.
- Ahorro estimado: 70 líneas de tests.
- Confianza: alta
- Cross-módulo: no

### [UI2-19] Renderizado de tarjeta vacía en `PlanPurchaseScreen` ante estado `READY` con `effectivePremium`
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt:330-345, 392
- Qué está mal: En `PurchaseStatus(state, ...)`:
  `if (state.status == PlanPurchaseStatus.READY && !state.effectivePremium) return`
  Si `state.status == PlanPurchaseStatus.READY` pero `state.effectivePremium == true`, la función no retorna. Pasa a renderizar `Card(...)`, pero dentro del `when (state.status)` no existe rama para `PlanPurchaseStatus.READY`, cayendo en `else -> Unit`. Si además `state.lifecycle == null`, se dibuja una tarjeta gris vacía con padding en medio de la pantalla.
- Escenario concreto de fallo: Un usuario premium ingresa a la pantalla de compra en estado READY sin haber cargado el lifecycle de su suscripción: se dibuja una tarjeta gris en blanco sin contenido.
- Fix mínimo: En la línea 330, retornar directamente si el estado es `READY`: `if (state.status == PlanPurchaseStatus.READY) return`.
- Confianza: alta
- Cross-módulo: no

*(Se dejaron fuera 2 detalles menores de espaciado y alineación cosmética en previews de Compose).*

---

## Código de riesgo sin tests

- `OfflineFirstProfilePreferencesRepository.syncPendingPreferences` (app/src/main/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepository.kt:137-195) → Test mínimo que falta: prueba unitaria con DAO simulado que inyecte un resultado `"CONFLICT"` desde Supabase y compruebe que no se bloquea permanentemente la reconciliación del outbox tras invocar `refreshProfile`.
- `SetupDraft.balanceMinorUnits` (app/src/main/java/com/kipu/app/feature/setup/SetupDraft.kt:43-50) → Test mínimo que falta: prueba unitaria que pase `balanceText = "150,50"` y valide que retorne `15050L` (S/ 150.50) y no `1505000L`.
- `BiometricSettingsViewModel.toggleLocalUnlock` ante fallo de Activity (app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt:91) → Test mínimo que falta: prueba unitaria pasando `activity = null` que verifique que el estado UI no quede congelado ni en inconsistencia si no hay Activity disponible.
- `SettingsViewModel.stepMonthStart` en límites extremos (app/src/main/java/com/kipu/app/feature/settings/presentation/SettingsViewModel.kt:144-150) → Test mínimo que falta: prueba unitaria verificando que `stepMonthStart(1)` cuando `monthStart == 28` y `stepMonthStart(-1)` cuando `monthStart == 1` sean no-op y no disparen `savePreferences()`.
- `PermissionsViewModel.grantConsent` para `OTHER_APP_NOTIFICATION_CONTENT` (app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsViewModel.kt:163-198) → Test mínimo que falta: prueba unitaria donde `captureGate.currentOwnerId()` no coincida con `userId.toString()`, validando que se emita el mensaje de error y no se persista el consentimiento en el DAO.

---

## Lo que no revisé

- Se leyeron y auditaron completos los 46 archivos de `main`, los 14 archivos de `test` y los 12 archivos de `androidTest` asignados a UI2 (100% de los 72 archivos cubiertos).
- No se ejecutó código en dispositivos ni emuladores físicos (por regla estricta de auditoría estática).
- No se interactuó con instancias remotas de Supabase ni APIs de Google Play Console.
- No se auditaron las implementaciones internas de componentes fuera de alcance (`CaptureAccessGate`, `LocalLockCoordinator`, `CategoriesRepository`, `AuthRepository`), evaluándose únicamente sus interfaces públicas y contratos de interacción hacia settings, setup y planes.

---

## Dudas

- ¿Por qué `ProfileSettingsScreen` incluye una tarjeta fija de "Alertas de vencimiento (3 días)" si en el modelo de dominio `UserProfile` no existe ningún atributo para configurar días de anticipación de alertas? ¿Debería delegarse a `UI3` (notificaciones)?
- ¿Por qué `SetupDraft.balanceMinorUnits` purga las comas en lugar de reemplazarlas por puntos decimales, cuando en Android en español para Perú la coma es el separador decimal natural en el teclado numérico?
- ¿Por qué en `ProfilePreferencesDao.hydrateProfile` se preserva la revisión local ante filas no resueltas en el outbox (`remoteRevision = local.remoteRevision`), impidiendo que el cliente adopte la nueva revisión del servidor tras un conflicto?

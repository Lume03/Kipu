# SEC2 — auth/presentation

Esta porción gestiona la presentación de autenticación de Kipu en Android: pantallas de inicio de sesión, registro, recuperación de contraseña, restablecimiento mediante deep links, flujo de bienvenida/onboarding de tres láminas, transiciones visuales inspiradas en quipus y el diálogo de cierre de sesión con cambios pendientes. Asumí miles de usuarios personales en Perú operando con dispositivos móviles de gama de entrada a media, uso frecuente sin conexión a internet y sesiones seguras donde las credenciales no deben persistir en memoria ni en registros del sistema.

## Hallazgos

17 hallazgos: 0 críticos, 4 altos, 8 medios y 5 bajos. Es una auditoría estática de código; no se ejecutaron pruebas en dispositivos ni se alteraron servicios remotos.

### [SEC2-01] Aserción rota en test instrumentado `AuthCelebrationScreensTest.kt` contra texto inexistente
- Severidad: alta
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/auth/presentation/AuthCelebrationScreensTest.kt:34
- Qué está mal: El test instrumentado `accountCreatedShowsTheAddressAndContinuesOnce` afirma `rule.onNodeWithText("Siguiente paso: elegir tu plan").assertIsDisplayed()`. Sin embargo, `AccountCreatedScreen.kt:156` fue modificado para mostrar `"Tu dinero está listo"`. El string antiguo ya no existe en el árbol semántico del composable, provocando que este test falle de inmediato si se ejecuta contra un emulador o dispositivo real.
- Escenario concreto de fallo: Al correr la suite de pruebas instrumentadas de autenticación, Compose UI busca el nodo con texto "Siguiente paso: elegir tu plan", falla por elemento ausente y lanza `AssertionError`, rompiendo la integración continua.
- Fix mínimo: Reemplazar `"Siguiente paso: elegir tu plan"` por `"Tu dinero está listo"` en `AuthCelebrationScreensTest.kt:34`. Test mínimo: verificar que el nodo `"Tu dinero está listo"` se muestre con visibilidad y estilo correctos.
- Confianza: alta
- Cross-módulo: no

### [SEC2-02] Spinner invisible en `AuthPrimaryButton` por colisión de color con el fondo
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthComponents.kt:185-186
- Qué está mal: En `AuthPrimaryButton`, el contenedor del botón tiene color de fondo `colors.primaryDeep`. El indicador de progreso circular `CircularProgressIndicator` se dibuja con `color = colors.primaryDeep`. Al coincidir exactamente el color del spinner con el color del contenedor sobre el que se dibuja, el indicador de carga resulta invisible para el usuario.
- Escenario concreto de fallo: En `RecoveryScreen` (al pulsar "Enviar enlace de recuperación"), en `ResetPasswordScreen` (al pulsar "Guardar contraseña") o en `OnboardingScreen` (al pulsar "Empezar" mientras persiste), el botón pasa a estado `loading = true` mostrando "Procesando…", pero el spinner circular a su izquierda no se percibe en pantalla al ser del mismo verde esmeralda que el botón.
- Fix mínimo: En `AuthComponents.kt:186`, cambiar `color = colors.primaryDeep` a `color = colors.onPrimaryDeep` (blanco o color contrastante del tema, tal como hace `AuthCtaButton` en `AuthFormComponents.kt:409`).
- Confianza: alta
- Cross-módulo: no

### [SEC2-03] Bloqueo indefinido de la aplicación detrás de `SessionEntryCurtainHost` sin mecanismo de timeout
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/SessionEntryCurtain.kt:91-123
- Qué está mal: `SessionEntryCurtainHost` dibuja una cortina opaca sobre toda la pantalla consumiendo el botón atrás con `BackHandler(true) {}`. La cortina únicamente se levanta si `ready` es verdadero, lo cual exige estrictamente que la pantalla destino reporte estar lista (`homeReady = true`). Si el dashboard o la pantalla destino experimentan un error de red offline, fallo de consulta local Room o si `currentRoute` difiere por parámetros dinámicos, `markHomeReady()` nunca se invoca. No existe un tiempo límite de seguridad (timeout de escape).
- Escenario concreto de fallo: Un usuario inicia sesión mientras la base local Room está ocupada o con datos corruptos/vacíos que dejan `dashboardLoaded == false` indefinidamente en `AccountsNavigation.kt:265`. La cortina "¡Hola de nuevo!" permanece en pantalla para siempre; el botón atrás está deshabilitado y el usuario no puede ni reintentar ni salir, viéndose forzado a forzar el cierre del proceso desde los ajustes de Android.
- Fix mínimo: En `SessionEntryCurtainHost`, agregar un `LaunchedEffect` con un retardo máximo de seguridad (p. ej., 6 a 8 segundos) que invoque `curtain.dismiss()` de manera forzosa si `ready` no se ha cumplido.
- Confianza: alta
- Cross-módulo: sí → ACC3 (AccountsNavigation / DashboardScreen es quien marca `markHomeReady()`)

### [SEC2-04] Bloqueo permanente de `login()` y `register()` si la animación de `exitWithError` en `SessionEntryCurtainHost` se interrumpe
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt:76,133; app/src/main/java/com/kipu/app/feature/auth/presentation/SessionEntryCurtain.kt:61-75,126-130
- Qué está mal: En `AuthViewModel`, tanto `login()` como `register()` descartan cualquier ejecución si `entryCurtain.state.value?.errorExiting == true`. Cuando ocurre un error de login, se marca `errorExiting = true` y se delega la finalización (`finishErrorExit()`) a un `LaunchedEffect` en Compose que anima el alfa durante 180 ms. Si la actividad pasa a segundo plano, rota de pantalla o si el host de la cortina no está compuesto en ese instante, el efecto se cancela o no se ejecuta, `finishErrorExit()` nunca es invocado y `errorExiting` se queda en `true` permanentemente en el singleton `SessionEntryCurtain`.
- Escenario concreto de fallo: Un usuario ingresa credenciales erróneas y presiona "Ingresar"; inmediatamente bloquea el teléfono o cambia a otra aplicación. La respuesta de error llega del backend en segundo plano y se invoca `exitWithError`. Al no completarse la animación en Compose, `entryCurtain.state.value.errorExiting` permanece en `true`. Al volver a abrir la app, los botones "Ingresar" y "Crear cuenta" quedan completamente muertos: pulsar cualquiera de ellos retorna de inmediato sin hacer nada.
- Fix mínimo: No supeditar el estado de negocio del ViewModel a un callback de animación de Compose; limpiar `errorExiting` de inmediato o con un timeout en `SessionEntryCurtain`, o restablecerlo en `AuthViewModel` ante cualquier cambio de entrada del usuario en `onEmailChanged`/`onPasswordChanged`.
- Confianza: alta
- Cross-módulo: no

### [SEC2-05] Desincronización y congelamiento de cooldowns por retrasos basados en `delay()` en `AuthViewModel` y `RecoveryViewModel`
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt:45-48; app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryViewModel.kt:47-52
- Qué está mal: Ambas clases implementan la cuenta regresiva mediante bucles que hacen `delay(1_000)` y restan 1 segundo al estado. Si la app pasa a segundo plano, Android suspende la corrutina de `viewModelScope`. Cuando el usuario reabre la aplicación varios segundos o minutos después, el contador no ha avanzado según el tiempo real transcurrido y obliga al usuario a esperar el tiempo restante completo, a pesar de que el bloqueo del servidor ya expiró.
- Escenario concreto de fallo: Un usuario falla sus intentos y entra en cooldown de 45 segundos. Inmediatamente minimiza la app durante 40 segundos para buscar su clave en un gestor de contraseñas y vuelve. El contador se reanuda en 44 segundos en lugar de mostrar los 5 segundos reales restantes.
- Fix mínimo: Calcular en cada iteración los segundos restantes comparando el reloj actual (`Instant.now()` o `System.currentTimeMillis()`) con la marca de tiempo de expiración absoluta (`blockedUntil` o `resendUntil`), en lugar de restar un acumulador ciego con `delay(1_000)`.
- Confianza: alta
- Cross-módulo: no

### [SEC2-06] Saturación de TalkBack por live region en `AuthBanner` durante la cuenta regresiva de cooldown
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormComponents.kt:374; app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:168-169
- Qué está mal: `AuthBanner` incluye `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`. En `AuthFormScreen`, el banner muestra el mensaje de cooldown parametrizado con `uiState.cooldownSeconds`. Como este valor cambia cada 1.000 ms, la región activa encola una lectura de accesibilidad por cada segundo (hasta 60 seguidas), saturando la cola de voz de TalkBack e impidiendo al usuario escuchar el foco de otros controles.
- Escenario concreto de fallo: Un usuario con discapacidad visual activa un cooldown de 30 segundos. TalkBack comienza a anunciar repetidamente cada segundo: "Demasiados intentos. Espera 29 segundos.", "Demasiados intentos. Espera 28 segundos.", etc., hablando por encima de cualquier otro elemento que el usuario intente tocar en el formulario.
- Fix mínimo: Quitar `liveRegion` del banner de cooldown o usar una región estática que no anuncie cada segundo transcurrido, permitiendo que el usuario lea el tiempo restante únicamente cuando enfoque el componente.
- Confianza: alta
- Cross-módulo: no

### [SEC2-07] Borrado prematuro de `confirmation` en `AuthFormScreen` antes de comprobar validación en ViewModel
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:125
- Qué está mal: Al presionar "Crear cuenta", la función `submit()` ejecuta `confirmation = ""` antes de llamar a `onRegisterClick()`. Si la validación local del ViewModel falla (por ejemplo, el correo no tiene formato válido o la contraseña no cumple la longitud requerida en `PasswordValidator`), el correo o la contraseña muestran error, pero el campo de confirmación ya fue vaciado, forzando al usuario a volver a escribirlo.
- Escenario concreto de fallo: El usuario ingresa `usuario@invalido`, contraseña `MiPassword123` y confirmación `MiPassword123`. Pulsa "Crear cuenta". La validación de email falla y muestra "Formato de correo no válido.", pero el campo "Confirmar contraseña" aparece en blanco y los chips marcan error, requiriendo reescribir toda la confirmación.
- Fix mínimo: No borrar `confirmation` de forma anticipada en `submit()`; borrarlo únicamente cuando el registro tenga éxito o cuando se cambie explícitamente de pestaña.
- Confianza: alta
- Cross-módulo: no

### [SEC2-08] Ausencia de campo de confirmación de contraseña en `ResetPasswordScreen`
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/ResetPasswordScreen.kt:23-26; app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryUiState.kt:3-14
- Qué está mal: A diferencia de `AuthFormScreen` que exige confirmar la contraseña, `ResetPasswordScreen` solo presenta un único campo con máscara oculta (`AuthPasswordField`). Dado que el enlace de recuperación es de un solo uso y se consume al instante, si el usuario comete un error tipográfico en el teclado móvil al escribir su nueva clave, la contraseña errónea se guarda en el servidor sin posibilidad de verificación previa, dejando al usuario bloqueado sin saber qué clave quedó grabada.
- Escenario concreto de fallo: Un usuario con un enlace de recuperación válido escribe su nueva clave con un carácter accidental (p. ej. `Contrasena1!` en vez de `Contrasena1`). Pulsa "Guardar contraseña". La clave errónea se actualiza con éxito y el token se consume. Cuando el usuario intenta iniciar sesión con `Contrasena1`, el acceso es denegado y debe solicitar otro enlace.
- Fix mínimo: Incorporar el campo `confirmPassword` en `RecoveryUiState` y en `ResetPasswordScreen`, validando que ambas coincidan con `PasswordValidator.validatePasswordConfirmation` antes de enviar la petición.
- Confianza: alta
- Cross-módulo: no

### [SEC2-09] Posible caída por `SecurityException` no capturada al invocar app de correo en `RecoveryScreen`
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryScreen.kt:87-92
- Qué está mal: Al presionar "Abrir aplicación de correo", se ejecuta `context.startActivity(...)` dentro de un bloque `try-catch` que únicamente captura `ActivityNotFoundException`. En perfiles de trabajo (Android Enterprise) o dispositivos con restricciones de visibilidad de paquetes (Android 11+), lanzar un `Intent.makeMainSelectorActivity` puede arrojar `SecurityException`, causando un crash no controlado del proceso.
- Escenario concreto de fallo: Un dispositivo con perfil corporativo o políticas estrictas de aislamiento de intents pulsa "Abrir aplicación de correo" tras solicitar recuperación. El sistema lanza `SecurityException` y la aplicación se cierra inesperadamente.
- Fix mínimo: Capturar `Exception` de forma general o agregar `catch (_: SecurityException)` junto con `ActivityNotFoundException` para asignar el mensaje en `mailError`.
- Confianza: alta
- Cross-módulo: no

### [SEC2-10] Asignación continua de `Path` y `PathMeasure` en cada frame de dibujo en animaciones
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthCelebrationMotion.kt:259-260,368; app/src/main/java/com/kipu/app/feature/auth/presentation/WelcomeBackCurtain.kt:195-202; app/src/main/java/com/kipu/app/feature/auth/presentation/OnboardingIllustrations.kt:279-281
- Qué está mal: En `drawQuipuField`, `drawAccountSeal`, `LogoTile` y en `OnboardingPrivacyIllustration`, se instancian objetos `Path()` y `PathMeasure()` directamente en el cuerpo de dibujo de Compose en cada frame (60-120 Hz). Esto genera una constante recolección de basura (GC churn) durante transiciones complejas.
- Escenario concreto de fallo: En dispositivos de gama de entrada o media (comunes en el mercado peruano), durante el iris de salida de `WelcomeBackCurtain` o el tejido de `AccountCreatedScreen`, el GC de Android se activa reiteradamente para limpiar centenares de `PathMeasure`, provocando micro-congelamientos y pérdida visible de fluidez (jank).
- Fix mínimo: Reutilizar o almacenar en caché las instancias de `Path` y `PathMeasure` fuera del lambda de dibujo (usando `remember` o `drawWithCache`).
- Confianza: alta
- Cross-módulo: no

### [SEC2-11] `rememberSceneClock` detiene su avance a los 40 segundos congelando animaciones continuas
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/OnboardingMotion.kt:101-106; app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:97; app/src/main/java/com/kipu/app/feature/auth/presentation/WelcomeBackCurtain.kt:81
- Qué está mal: `rememberSceneClock` anima el reloj hasta un límite fijo de 40 segundos (`time.animateTo(OnboardingTimeline.LOOP_END)`). Al ser reutilizado en `AuthFormScreen` para el balanceo y destello de los quipus del encabezado y en `WelcomeBackCurtain` para los puntos suspensivos ("WaitingDot"), al transcurrir 40 segundos la animación se congela por completo y nunca se reanuda.
- Escenario concreto de fallo: Un usuario se toma más de 40 segundos en la pantalla de login leyendo los términos o verificando su contraseña. Al cumplirse 40 segundos, los hilos del quipu y los brillos del encabezado se paralizan abruptamente como si la app estuviera trabada.
- Fix mínimo: Para componentes continuos como `AuthHero` y `WelcomeBackCurtain`, desacoplar la animación de un reloj finito de 40 segundos y utilizar un ciclo continuo o `rememberInfiniteTransition()`.
- Confianza: alta
- Cross-módulo: no

### [SEC2-12] `contentDescription = label` en campos de texto enmascara el contenido para TalkBack
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthComponents.kt:115; app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormComponents.kt:305
- Qué está mal: Tanto `AuthTextField` como `AuthFloatingField` asignan `.semantics { contentDescription = label }` directamente sobre el composable `TextField`. En Jetpack Compose, aplicar `contentDescription` a un nodo editable sustituye la descripción semántica del texto ingresado, impidiendo que el lector de pantalla anuncie el valor real escrito por el usuario.
- Escenario concreto de fallo: Un usuario invidente escribe su correo en el campo "Correo electrónico". Al volver a enfocar el campo para verificar lo que escribió, TalkBack lee únicamente "Correo electrónico" en lugar de leer el valor ingresado (`usuario@ejemplo.com`) ni la posición del cursor.
- Fix mínimo: Eliminar `.semantics { contentDescription = label }` de los `TextField`; Material 3 ya expone el label y el texto editable de forma accesible y estándar.
- Confianza: alta
- Cross-módulo: no

### [SEC2-13] Cadenas de correo sin sanitizar (`trim`) enviadas a `AuthCredentials` y `SetupDraftStore`
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt:87,145; app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryViewModel.kt:87
- Qué está mal: `PasswordValidator.validateEmail` ejecuta `email.trim()` internamente, por lo que valida correctamente correos con espacios al inicio o final. Sin embargo, en `login()`, `register()` y `submitRecoveryRequest()`, se envían las cadenas crudas `currentState.email` sin recortar hacia `AuthCredentials`, `setupDraft.begin()` y `savedStateHandle["submittedEmail"]`.
- Escenario concreto de fallo: Un usuario que selecciona su correo mediante el teclado predictivo inserta un espacio al final (`"correo@ejemplo.com "`). La validación local pasa, pero las credenciales se transportan con el espacio superfluo.
- Fix mínimo: Aplicar `.trim()` o `PasswordValidator.normalizeEmail()` al construir `AuthCredentials` y registrar los drafts en `AuthViewModel` y `RecoveryViewModel`.
- Confianza: alta
- Cross-módulo: no

### [SEC2-14] Color `0xFF0A5A54` hardcodeado en scrim de barra de estado en `AuthFormScreen` rompe tema oscuro
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:245
- Qué está mal: Al desplazarse la pantalla de login/registro, la barra de estado se oscurece mediante un scrim que tiene `Color(0xFF0A5A54)` hardcodeado. En modo oscuro, el fondo del encabezado utiliza tonos más profundos (`0xFF052E2A`), por lo que al hacer scroll aparece una barra verde claro discordante.
- Escenario concreto de fallo: Con el modo oscuro del sistema activo, el usuario abre la pantalla de inicio de sesión y se desplaza hacia abajo para ver el botón de términos. La barra de estado superior cambia a un tono verde claro en vez de integrarse con la paleta oscura.
- Fix mínimo: Utilizar `if (colors.isDark) Color(0xFF052E2A) else Color(0xFF0A5A54)` o `colors.primaryDeep` en lugar del valor numérico fijo.
- Confianza: alta
- Cross-módulo: no

### [SEC2-15] Constantes y funciones de extensión huérfanas en `AuthCelebrationMotion.kt` y `AccountCreatedScreen.kt`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/AuthCelebrationMotion.kt:44,77; app/src/main/java/com/kipu/app/feature/auth/presentation/AccountCreatedScreen.kt:70-86
- Qué está mal: `AccountCreatedTimeline.REVEAL` y `WelcomeBackTimeline.REVEAL` (`const val REVEAL = 0.95f`) no se usan en ningún lugar del repositorio. Asimismo, la función de extensión `circleReveal` quedó huérfana dentro de `AccountCreatedScreen.kt` tras un refactor y únicamente es consumida por `WelcomeBackCurtain.kt`.
- Escenario concreto de fallo: Mantenimiento confuso y código muerto que induce a error al sincronizar las líneas de tiempo de animación.
- Fix mínimo: Eliminar las constantes `REVEAL` y mover `circleReveal` a `WelcomeBackCurtain.kt` o `AuthCelebrationMotion.kt`.
- Ahorro estimado: 25 líneas.
- Confianza: alta
- Cross-módulo: no

### [SEC2-16] Duplicación de wrappers `LoginScreen.kt` y `RegisterScreen.kt`
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/LoginScreen.kt:1-23; app/src/main/java/com/kipu/app/feature/auth/presentation/RegisterScreen.kt:1-22
- Qué está mal: `LoginScreen.kt` y `RegisterScreen.kt` son archivos independientes de 23 y 22 líneas que no contienen lógica propia y se limitan a llamar a `AuthFormScreen` con `initialRegisterMode` en false o true. `AuthFormScreen` ya contiene la lógica de ambas pantallas y la pestaña de alternancia.
- Escenario concreto de fallo: Indirección innecesaria que obliga a mantener firmas y parámetros duplicados en dos archivos adicionales.
- Fix mínimo: Invocar `AuthFormScreen` directamente desde `AuthNavigation.kt` y retirar los wrappers redundantes `LoginScreen` y `RegisterScreen`.
- Ahorro estimado: 45 líneas.
- Confianza: alta
- Cross-módulo: no

### [SEC2-17] Import no utilizado de `androidx.compose.ui.graphics.Color` en `SignOutDialog.kt`
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/presentation/SignOutDialog.kt:10
- Qué está mal: `import androidx.compose.ui.graphics.Color` no se utiliza en ninguna parte del archivo `SignOutDialog.kt`.
- Escenario concreto de fallo: Advertencia de compilador y código residual sin uso.
- Fix mínimo: Eliminar la línea de import.
- Ahorro estimado: 1 línea.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `SessionEntryCurtainHost` (SessionEntryCurtain.kt:126-131): no hay test que simule la destrucción/desmontaje del composable mientras `errorExiting == true`, para verificar si `curtain.finishErrorExit()` queda bloqueado sin ejecutarse.
- `SessionEntryCurtainHost` con rutas parametrizadas (SessionEntryCurtain.kt:112-119): no hay test que verifique el levantamiento de la cortina cuando `destinationRoute` contiene argumentos dinámicos (p. ej. `debts/detail/{debtId}`) y difiere del patrón de ruta.
- `RecoveryViewModel` frente a saltos de reloj en cooldown (RecoveryViewModel.kt:47-54): no hay test que verifique el comportamiento si la hora del sistema cambia o si la app se minimiza durante 30 segundos mientras corre la corrutina en memoria.
- `OnboardingViewModel.savePage` ante fallos de persistencia (OnboardingViewModel.kt:21-27): no hay test unitario que verifique que `_error` se actualiza cuando `preferences.savePage` lanza una `IOException`.
- `OnboardingViewModel.complete` ante fallos de persistencia (OnboardingViewModel.kt:29-40): no hay test unitario que verifique que `_saving` vuelve a `false` y `_error` se puebla cuando `preferences.complete` lanza una `IOException`.

## Lo que no revisé

- Todo el alcance asignado a SEC2 (23 archivos en `app/src/main/.../auth/presentation/`, 6 archivos en `app/src/test/.../auth/presentation/` y 5 archivos en `app/src/androidTest/.../auth/presentation/`) fue leído y auditado en su totalidad.
- No se realizaron pruebas dinámicas en emuladores ni hardware físico (por mandato de auditoría estática).
- No se auditó la lógica interna de repositorios, criptografía o edge functions de Supabase fuera del alcance de SEC2 (pertenecientes a SEC1 y SQL), limitándose la lectura a sus firmas públicas y contratos consumidos por la capa de presentación.

## Dudas

1. ¿Por qué `OnboardingScreen` presenta acciones separadas para "Empezar" y "Ya tengo una cuenta" si ambas conducen idénticamente a `AUTH_LOGIN_ROUTE` (modo login)? Para usuarios nuevos que presionan "Empezar", la expectativa natural de UX suele ser navegar directamente a la pantalla de registro (`AUTH_REGISTER_ROUTE` o `register = true`).
2. ¿Por qué se mantienen dos destinos de navegación independientes (`AUTH_LOGIN_ROUTE` y `AUTH_REGISTER_ROUTE`) con ViewModels desconectados si `AuthFormScreen` gestiona el estado y la alternancia de pestañas en un solo componente?
3. ¿Por qué `ResetPasswordScreen` no solicita confirmación de contraseña, a diferencia del formulario de registro en `AuthFormScreen` que sí valida coincidencia antes de procesar?

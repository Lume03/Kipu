# Evidencia de #39: temas y filtros

Evidencia histórica generada el 2026-10-13, America/Lima. Base anterior: `f8e7ea3`. Las capturas y resultados detallados abajo corresponden a `0118a19` (PR head documental `d22e308`), antes de integrar `origin/main` (`d5667ab`) el 2026-10-17. No se deben atribuir al código integrado actual. [Issue](https://github.com/Lume03/Kipu/issues/39), [plan](../../issue-39-dark-theme-plan.md), [auditoría y excepciones](../../issue-39-dark-theme-audit.md).

## Revalidación actual de la integración (2026-10-17)

Esta evidencia se generó sobre la rama de #55 después de integrar #51, #52 y #56 y de adaptar filtros/acciones para viewport compacto y texto ampliado. Las capturas nuevas están en [`integrated-head-2026-10-17/`](integrated-head-2026-10-17/); incluyen filtros y superficies en ambos temas, dos estados compactos y el informe de contraste. Los PNG se guardaron sin edición.

| Estado | Evidencia actual |
|---|---|
| Filtros Free claro/oscuro | [Claro](integrated-head-2026-10-17/after-free-filters-light.png) · [Oscuro](integrated-head-2026-10-17/after-free-filters-dark.png) |
| Filtros, viewport compacto y fuente ×1,6 | [Importe y acciones](integrated-head-2026-10-17/after-compact-large-text-dark.png) |
| Calendario en viewport compacto | [Entrada manual](integrated-head-2026-10-17/after-compact-calendar-input-dark.png) |
| Selector de cuenta claro/oscuro | [Claro](integrated-head-2026-10-17/after-account-selector-light.png) · [Oscuro](integrated-head-2026-10-17/after-account-selector-dark.png) |
| Contraste de roles medidos | [contrast.json](integrated-head-2026-10-17/contrast.json) |

Validación sobre el código actual:

- `:app:testDebugUnitTest`: **526 tests, 0 fallos, 0 errores, 0 omitidos**.
- `:app:compileDebugKotlin` y `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**.
- `KipuThemeContrastTest` + `ThemeScreenEvidenceTest`: **4 tests, 0 fallos**.
- `MovementFiltersIssue19Test`: **10 tests, 0 fallos**. El caso compacto comprueba PEN/USD, ambos importes y las dos acciones con ancho aproximado de 320 dp y fuente ×1,6.
- `:app:lintDebug` y `:app:assembleLab`: **BUILD SUCCESSFUL**, sin errores de lint.

Dispositivo de las capturas de superficie: Pixel 10 AVD, Android 17/API 37, 1080×2424, 420 dpi. El caso compacto usa un `ContextThemeWrapper` con configuración local; no cambia la configuración global del emulador. La captura compacta muestra el contenido Compose después de introducir el importe; no contiene una captura del teclado IME del sistema. Por eso no se afirma que el IME haya sido revisado visualmente.

La suite instrumentada completa anterior ejecutó 348 pruebas y tuvo 17 fallos; cuatro se reprodujeron en la base integrada y doce aún no se atribuyeron individualmente. No se repitió esa suite completa después del ajuste compacto. La cobertura de previews sigue parcial por `UnifiedInstrumentFormScreen`, y falta aceptación visual humana de esta integración. T122, T123 y T125 permanecen abiertas; TalkBack hablado y runtime API 24 no se ejecutaron.

## Revalidación dirigida del código integrado

Código de aplicación validado: `836415cd04aa657731c71ad1bcdc7bba016ae80f` (basado en la integración que partía de `1346ed965f5d4c810ad717fa67984259062b05a0`; base `origin/main` `d5667abdf66d85a32fae43d10de8233381e84051`). La corrección elimina el bloque de cupo y la navegación a Planes del dashboard vacío. Los accesos del dashboard activo y del flujo de introducción de instrumentos se conservan.

Checks completados para ese código:

- `:app:testDebugUnitTest --tests com.kipu.app.feature.accounts.presentation.dashboard.DashboardEmptyStatePolicyTest`: **PASS, 3 tests**.
- `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**; compila el source set instrumentado, pero no ejecuta pruebas en dispositivo.
- `git diff --check`: **PASS** para el commit de código.

No se completó `assembleLab`, no se ejecutó `DashboardEmptyStateTest` ni otra instrumentación, y no se generaron capturas nuevas. Por ello este bloque no prueba el render/runtime integrado. Los APK/PNGs y resultados JVM, instrumentados, lint y contraste listados en el resto de este README siguen siendo históricos de `0118a19`. T122/T123 permanecen abiertos; T125 y la aceptación visual humana siguen pendientes. No se declara TalkBack hablado ni runtime API 24.

Dispositivo físico 23129RA5FL, Android 15/API 35, 1080×2400, 440 dpi, fuente del sistema 1,0. La prueba compacta usa un contexto Android local con densidad ×1,25 y fuente ×1,6; no altera los ajustes del celular. Las pruebas usan `com.kipu.app.lab`, configuración remota vacía, modelos sintéticos válidos y callbacks sin efectos financieros. No contienen datos de la sesión personal.

La copia de uso existente `com.kipu.app.preview` se compiló con `gradle/preview-device.init.gradle` y se actualizó mediante `adb install -r`: **Success**. Inicio de `com.kipu.app.MainActivity`: **Status: ok**, proceso activo. No se desinstaló ni se limpió su almacenamiento. Build/lint finales de esa copia: **BUILD SUCCESSFUL**, 0 errores y 300 advertencias. El intento de lint sobre una versión intermedia se reinició con las fuentes finales estables; no cuenta como validación aceptada.

## Comparativa del mismo fixture

La APK base y la posterior usan el mismo fixture `MovementFilterVisualEvidenceTest`, dispositivo y configuración. Son PNG obtenidos directamente de Compose; no se retocaron. Los selectores de estas comparativas son referencias de filtros; el selector de cuentas del registro tiene nombres propios `after-account-selector-*`.

| Estado | Antes claro | Después claro | Antes oscuro | Después oscuro |
|---|---|---|---|---|
| Filtros/importe | [PNG](before-amount-light.png) | [PNG](after-amount-light.png) | [PNG](before-amount-dark.png) | [PNG](after-amount-dark.png) |
| Selector de referencias | [PNG](before-selector-light.png) | [PNG](after-selector-light.png) | [PNG](before-selector-dark.png) | [PNG](after-selector-dark.png) |
| Calendario | [PNG](before-calendar-light.png) | [PNG](after-calendar-light.png) | [PNG](before-calendar-dark.png) | [PNG](after-calendar-dark.png) |

## Componentes reales después del cambio

`ThemeScreenEvidenceTest` espera a que el componente esté visible, selecciona la ventana real del modal y rechaza capturas uniformes de su fondo. Las acciones y estados adicionales se verifican mediante regresiones instrumentadas.

| Componente | Claro | Oscuro |
|---|---|---|
| Filtros Free | [PNG](after-free-filters-light.png) | [PNG](after-free-filters-dark.png) |
| Upsell | [PNG](after-upsell-light.png) | [PNG](after-upsell-dark.png) |
| Cuenta | [PNG](after-accounts-light.png) | [PNG](after-accounts-dark.png) |
| Selector de cuentas | [PNG](after-account-selector-light.png) | [PNG](after-account-selector-dark.png) |
| Detalle de movimiento | [PNG](after-movement-detail-light.png) | [PNG](after-movement-detail-dark.png) |
| Editor | [PNG](after-editor-light.png) | [PNG](after-editor-dark.png) |
| Tasas | [PNG](after-rates-light.png) | [PNG](after-rates-dark.png) |
| Cuotas | [PNG](after-installments-light.png) | [PNG](after-installments-dark.png) |
| Deudas | [PNG](after-debts-light.png) | [PNG](after-debts-dark.png) |
| Detalle de deuda | [PNG](after-debt-detail-light.png) | [PNG](after-debt-detail-dark.png) |
| Cronograma | [PNG](after-schedule-light.png) | [PNG](after-schedule-dark.png) |
| Liquidación | [PNG](after-settlement-light.png) | [PNG](after-settlement-dark.png) |
| Tabs de categorías/banner de permisos | [PNG](after-categories-permissions-light.png) | [PNG](after-categories-permissions-dark.png) |
| Editor de color | [PNG](after-color-picker-light.png) | [PNG](after-color-picker-dark.png) |
| Catálogo de iconos | [PNG](after-icon-picker-light.png) | [PNG](after-icon-picker-dark.png) |

Pantalla compacta y texto ampliado: [filtros con teclado](after-compact-keyboard-dark.png), [calendario con entrada manual](after-compact-calendar-input-dark.png).

## Previews de fuente añadidas en la integración local

Estas previews envuelven contenido real de producción en `KipuTheme` y generan variantes claro/oscuro. Son declaraciones de preview en Kotlin; no se renderizaron ni sustituyen capturas de la integración actual.

- Dashboard de primer uso (`EmptyDashboardContent`), ahora coincide con la estructura incorporada desde `main`.
- Formulario de acceso, diálogo de cierre de sesión con cambios pendientes y contenido visual del bloqueo local.
- Contenido de ajustes biométricos en estado disponible/no disponible y componentes visuales de perfil/tema.
- Formulario real de categoría, búsqueda/lista de comercios, resumen de tarjeta de crédito, diálogo de anulación y `PurchasePriceShimmer`.
- Editores de presentación de categoría y clasificación de movimiento, renderizando su contenido real con estado de muestra y callbacks aislados del `ViewModel`.

El preview del bloqueo muestra `LockScreenContent` sin disparar el gateway biométrico. El preview biométrico cubre el cuerpo que consume el `ViewModel`; el preview de perfil cubre tarjetas, estado de sincronización y selector de tema, no la pantalla completa. La lista de comercios renderiza su contenido real dentro de `MerchantPicker`, no la ventana de `MerchantPickerBottomSheet`. `CategoryPresentationEditor` y `MovementClassificationEditor` exponen su contenido real con estado/callbacks de muestra, sin instanciar sus `ViewModel`. `UnifiedInstrumentFormScreen` sigue sin preview de pantalla completa: concentra un flujo multietapa con efectos de ciclo de vida y muchas operaciones del `AccountsViewModel`; extraer un estado de preview fiable excede esta integración. I39-15 continúa parcial por esa cobertura y porque las previews nuevas aún no se han renderizado sobre el código integrado.

## Validación

Los resultados instrumentados por método y el recuento JVM históricos están en [validation-results.json](validation-results.json). Se registra el último resultado de cada método; no se suman ejecuciones repetidas. Las comparativas de filtros se ejecutan además en ambos temas. Los logs completos, incluidos intentos fallidos, permanecen en `scratch/issue-39/` y no se presentan como PASS. La revalidación dirigida posterior al merge está delimitada arriba y no actualiza estos recuentos históricos.

- JVM: 521 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Dispositivo: 84 métodos distintos con último resultado PASS, sin pendientes de ejecución en la selección. La corrida conjunta de 83 dejó dos assertions de formato monetario; su reejecución pasó y se añadió el caso de barras de la ventana modal. La última tanda de tema/capturas/contexto compacto es `OK (7 tests)` y la comparativa clara adicional es `OK (1 test)`.
- Compilación de APK debug, APK lab y AndroidTest lab: exitosa.
- Lint debug: 0 errores; 300 advertencias existentes registradas, sin crear baseline ni suprimirlas.
- Contraste: [28 parejas texto/fondo](contrast.json) en ambos temas cumplen ≥4,5:1; mínimo 4,749:1. Contornos de campos ≥3:1. También se verifican foregrounds del catálogo bancario y 76 muestras cromáticas personalizadas.
- Tema de app frente a sistema opuesto: roles efectivos y flags de iconos de barras Android correctos en Activity y ventana modal. Cambio claro→oscuro→claro con filtros abiertos conserva periodo y fechas aplicadas; las barras se actualizan sobre la ventana existente.
- Regresión: borrador, cancelar/aplicar/limpiar, errores locales, referencias históricas/duplicadas, 1.000 referencias, acceso/propietario, Free, privacidad, editor/registro/historial, categorías, permisos, cuentas, cuotas, persistencia de tasas y deudas.

Inventario: [conteos antes/después](color-counts.csv) y [usos restantes](remaining-color-uses.csv). El recuento mide expresiones de color y es un insumo de revisión; no equivale a una medición de contraste por píxel. Las excepciones y el alcance de cada pantalla se explican en la auditoría.

## Reproducción

Ejecutar JVM/debug separado del build lab: `isolatedAndroidTests` cambia la variante de pruebas y no expone `testDebugUnitTest`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
.\gradlew.bat -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest --console=plain
adb install -r app/build/outputs/apk/lab/app-lab.apk
adb install -r app/build/outputs/apk/androidTest/lab/app-lab-androidTest.apk
python scripts/android/run-issue39-instrumentation.py com.kipu.app.ui.theme.KipuThemeContrastTest,com.kipu.app.feature.movements.presentation.MovementThemeRegressionTest,com.kipu.app.ui.theme.ThemeScreenEvidenceTest after true
python scripts/android/run-issue39-instrumentation.py com.kipu.app.feature.movements.presentation.MovementFilterVisualEvidenceTest after false
python scripts/android/run-issue39-instrumentation.py com.kipu.app.feature.movements.presentation.MovementFilterVisualEvidenceTest after true
python scripts/android/run-issue39-instrumentation.py com.kipu.app.feature.movements.presentation.MovementFiltersIssue19Test after true
adb pull /sdcard/Android/data/com.kipu.app.lab/files/issue-39 docs/audits/evidence
```

El runner acepta clases o `clase#método` separadas por comas, `--serial`/`ANDROID_SERIAL` y usa el workaround de lanzamiento de ActivityScenario requerido por MIUI. Para reproducir la línea base instalar la APK lab compilada desde `f8e7ea3`, conservar el APK de tests actualizado y usar prefijo `before`.

## Límites de aceptación

No se ejecutó TalkBack hablado ni runtime API 24. La cobertura de semántica y acciones no equivale a una sesión manual de escucha. Las parejas medidas no certifican todos los píxeles, logos, estados deshabilitados ni toda la accesibilidad de la aplicación. No se simularon compras, permisos concedidos por el usuario ni consultas remotas. Una ejecución interrumpida por bloqueo del celular fue descartada y repetida con el dispositivo desbloqueado. La aceptación visual humana de la integración actual no se realizó; la revisión independiente y la aceptación visual siguen pendientes y el issue permanece abierto.

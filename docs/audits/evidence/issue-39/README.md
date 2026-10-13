# Evidencia de #39: temas y filtros

Fecha de ejecución: 2026-10-13, America/Lima. Base anterior: `f8e7ea3`. El estado posterior corresponde al código incluido en el commit de implementación de esta rama. [Issue](https://github.com/Lume03/Kipu/issues/39), [plan](../../issue-39-dark-theme-plan.md), [auditoría y excepciones](../../issue-39-dark-theme-audit.md).

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

## Validación

Los resultados instrumentados por método y el recuento JVM están en [validation-results.json](validation-results.json). Se registra el último resultado de cada método; no se suman ejecuciones repetidas. Las comparativas de filtros se ejecutan además en ambos temas. Los logs completos, incluidos intentos fallidos, permanecen en `scratch/issue-39/` y no se presentan como PASS.

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

No se ejecutó TalkBack hablado ni runtime API 24. La cobertura de semántica y acciones no equivale a una sesión manual de escucha. Las parejas medidas no certifican todos los píxeles, logos, estados deshabilitados ni toda la accesibilidad de la aplicación. No se simularon compras, permisos concedidos por el usuario ni consultas remotas. Una ejecución interrumpida por bloqueo del celular fue descartada y repetida con el dispositivo desbloqueado. La revisión independiente y aceptación visual humana corresponden al PR; el issue permanece abierto hasta entonces.

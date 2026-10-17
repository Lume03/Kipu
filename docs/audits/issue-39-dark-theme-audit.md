# Auditoría de tema — issue #39

Fecha de ejecución: 2026-10-13 (America/Lima).
Base: `f8e7ea3`, rama `fix/dark-mode-filters-theme-audit`.
Requisito: [issue #39](https://github.com/Lume03/Kipu/issues/39).
Plan: [plan de implementación](issue-39-dark-theme-plan.md).

## Resultado del análisis y cambios

El contenedor del modal ya seguía MaterialTheme; el defecto estaba en sus superficies internas y contenido. Se reemplazaron las 151 expresiones directas de color de `MovementFiltersSheet.kt` por roles del tema, incluidos presets, tipos, importes, errores, pie y upsell. `MovementReferenceSelector.kt` pasó de 16 expresiones a cero.

El tema efectivo sigue siendo el resuelto por `MainActivity` a partir de LIGHT/DARK/SYSTEM. Las pantallas utilizan `MaterialTheme`, `rememberKipuColors()` y `rememberCalmEmeraldColors()`; no se añadieron consultas independientes al tema del sistema ni una nueva paleta general.

CalmEmerald ahora toma los roles generales del MaterialTheme activo. Se añadieron `incomeText` y `expenseText` para texto pequeño, conservando los acentos decorativos originales. Las acciones usan sus parejas `primary/onPrimary` y `error/onError`. Se corrigieron los foregrounds sobre colores bancarios y personalizados con luminancia sRGB, sin cambiar colores persistidos.

## Matriz de auditoría del código

Rutas relativas a `app/src/main/java/com/kipu/app/`. Los conteos incluyen `Color(0x...)`, `Color.White` y `Color.Black`; no equivalen a cantidad de defectos.

| Recorrido / archivo | Antes → después | Resultado |
|---|---|---|
| Movimientos / `MovementFiltersSheet` | 151 → 0 | Superficies, foregrounds, switches, chips, calendario y upsell tematizados. |
| Movimientos / `MovementReferenceSelector` | 16 → 0 | Superficie, búsqueda, selección, históricos y contornos de campos tematizados. |
| Movimientos / `MovementDetailSheet` | 1 → 0 | Acción usa onPrimary; estados financieros usan foregrounds accesibles. |
| Movimientos / `MovementEditorSheet` | 0 → 0 | Ya usaba roles del tema; se conserva comportamiento y se añade preview real. |
| Movimientos / `AccountSelectorBottomSheet` | 26 → 18 | Acción y selección usan onPrimary; badges financieros adaptativos; marcas conservadas con foreground legible. |
| Movimientos / `QuickMovementScreen` | 53 → 51 | Textos financieros, aviso y drag handle corregidos; pares pastel claro/oscuro existentes conservados. |
| Movimientos / `MovementHistoryScreen` | 0 → 0 | Foregrounds financieros accesibles; se elimina alpha de texto secundario pequeño. |
| Movimientos / `VoidMovementDialog` | 2 → 0 | Acción destructiva usa error/onError y spinner correspondiente. |
| Movimientos / `PastelCategoryPickerFlow` | 84 → 82 | Acciones onPrimary, etiqueta sobre fondo compuesto y contraste del cursor/slider; gamut HSV conservado. |
| Cuentas / `AccountDetailScreen` | 27 → 22 | Aviso del simulador y foregrounds financieros tematizados; gráfica de tarjeta física conservada. |
| Cuentas / `DashboardScreen` | 78 → 78 | Foregrounds financieros accesibles y contraste de badges; gradientes hero y pares adaptativos existentes revisados. |
| Cuentas / `RateCatalogScreen` | 116 → 16 | Todo el contenido general tematizado; solo permanece la tarjeta visual oscura de identidad. |
| Cuentas / `InstrumentFormModels` | 20 → 20 | Fondos bancarios conservados; foreground preferido se conserva si pasa 4,5:1 y se corrige si falla. |
| Cuentas / `UnifiedInstrumentFormScreen` | 4 → 2 | Selección onPrimary y borde semántico de aviso; getter adaptativo y representación de wallet conservados. |
| Cuentas / `MovementClassificationEditor` | 4 → 2 | Foreground sobre colores elegidos por usuario; dos fallbacks de color de datos conservados. |
| Cuentas / `InstallmentSimulatorScreen` | 1 → 0 | Chips usan onPrimary y foregrounds financieros accesibles. |
| Cuentas / `CreditCardSummaryCard`, `CalmEmeraldInstrumentComponents` | Acoplamiento a roles | Estados financieros usan incomeText/expenseText/warningText. |
| Cuentas / `CreditCardTermsDialog`, `InstrumentCardPreview` | Colores de representación | Tarjeta y logos de identidad conservados; sus superficies son gráficas acotadas. |
| Categorías / `CategoriesScreen` | 14 → 3 | Acción/tabs onPrimary; extracción del componente real de tabs; categorías archivadas mantienen texto sin alpha global. |
| Categorías / `CategoryFormScreen` | 1 → 0 | Spinner usa onPrimary. |
| Categorías / `CategoryPresentationEditor` | 3 → 1 | Iconos y check sobre color personalizado legibles; fallback de datos conservado. |
| Comercios / `MerchantPicker` | 14 → 11 | Foreground calculado para marcas; avatar general usa primary/onPrimary; logos conservados. |
| Ajustes / `PermissionsScreen` | 12 → 0 | Avisos, estados, bordes y acciones adaptativos; extracción de banner real para preview. |
| Ajustes / `ProfileSettingsScreen` | 4 → 0 | Moneda, switch y cierre de sesión tematizados. |
| Ajustes / `BiometricSettingsScreen` | 1 → 0 | Primary viene del tema efectivo. |
| Acceso / `LockScreenOverlay` | 1 → 0 | Primary viene del tema efectivo. |
| Acceso / `AuthFormScreen` | 2 → 2 | Color de éxito ya distingue tema; foreground financiero usa rol accesible; indicador decorativo conservado. |
| Acceso / `SignOutDialog` | 1 → 0 | Parejas primary/onPrimary o error/onError según estado. |
| Planes / `PlanPurchaseMotion` | 3 → 0 | Skeleton/shimmer usa surfaceContainerHigh/Highest. |
| Planes, notificaciones, navegación y restantes formularios | Revisión de consumidores | Se revisaron los roles heredados y las referencias a paletas; no se encontraron otros paneles claros fijos. |
| Metas y presupuestos | No aplicable | No existen paquetes/pantallas de esas funciones en esta base; las cuotas de planes no constituyen pantallas. |

El inventario completo está en [conteos](evidence/issue-39/color-counts.csv) y [usos de color restantes](evidence/issue-39/remaining-color-uses.csv).

## Excepciones cromáticas revisadas

- **Paletas** (`ui/theme`): definiciones de colores de ambos temas y funciones de contraste, no overrides dentro de una pantalla.
- **Pasteles adaptativos** (`PastelCategoryPickerFlow`, `QuickMovementScreen`, badges de dashboard): pares claros/oscuros dependientes del tema efectivo.
- **Colores personalizados y fallbacks**: identifican la elección del usuario; no se reescriben al cambiar tema. Los foregrounds y muestras se resuelven al renderizar.
- **HSV**: blanco, negro y colores primarios representan el gamut. El cursor conserva un borde negro/blanco y los sliders tienen thumb/contorno del tema.
- **Tarjetas físicas**: gradientes, chip dorado, banda, shadows y nombres de redes forman parte de la representación acotada de la tarjeta.
- **Logos de comercios**: el fondo blanco está limitado al círculo que contiene el recurso gráfico. Las superficies externas sí siguen el tema.
- **Marcas bancarias**: se conservan los fondos; los textos/iconos de UI se ajustan cuando no alcanzan contraste. Los recursos de logo siguen su diseño original.
- **Bordes decorativos y controles deshabilitados**: separados de textos y controles habilitados al evaluar los umbrales.

## Previews y validación

`KipuThemePreviews` proporciona Light Theme/Dark Theme con configuración explícita. Los nuevos previews renderizan los componentes reales de filtros Free/avanzados/borrador inválido, referencias, detalle/editor/registro, selector de cuentas, account detail, tasas, cuotas, deudas, liquidación, categorías, permisos, iconos, color y upsell. El preview de importe inválido requiere usar Apply en la preview interactiva para mostrar la validación real.

Las pruebas nuevas comprueban el tema efectivo frente a una configuración de sistema opuesta, 28 parejas de texto/fondo en dos temas, el contorno de campos, los foregrounds bancarios/personalizados y el cambio de tema con el modal abierto sin perder el borrador. Se utilizan los roles resueltos por el código de producción.

Las capturas usan modelos sintéticos y callbacks sin efectos financieros en `com.kipu.app.lab`. Los resultados finales y comparativas se registran en [evidencia](evidence/issue-39/README.md). No se considera PASS un intento fallido o una compilación sin ejecución en dispositivo.

## Ajustes necesarios para verificar la entrega

1. El test de capturas anterior buscaba «Cuentas», mientras el control visible mostraba «Cuenta». Ahora usa su tag estable, y admite un directorio propio para #39.
2. Pruebas previas de detalle, registro, cuotas y pago buscaban textos/controles de versiones anteriores. Se actualizan las comprobaciones para la interfaz vigente, conservando cancelación, readonly, importes, selección, callbacks y falta de tasa contractual.
3. Las pruebas de persistencia de tasas deben localizar elementos de una LazyColumn antes de inspeccionarlos. Se añade el tag `rate_catalog_list` y se recorren los elementos reales; se mantienen los asserts de DB, producto, moneda, estilo y TEA por tarjeta.
4. Los primeros fixtures nuevos tuvieron identificadores o una categoría inválidos según el dominio. Se corrigieron los datos sintéticos; esos intentos no cuentan como evidencia aceptada.
5. Lint detectó una llamada a notify en `DebtReminderWorker` sin manejo de una revocación de permiso entre verificación y publicación. Se conserva el checker/owner y se captura SecurityException. El recordatorio es opcional, el worker termina sin fallo y no escribe registros financieros. No cambian concesión de permisos, fechas ni planificación de recordatorios.
6. La columna lateral de TCEA podía consumir el ancho del producto y ocultar el texto TEA. Se limita a 112 dp; se verifican recorrido, moneda y persistencia de TEA por tarjeta.
7. El cronograma usaba la etiqueta fija «Activa» incluso con status CANCELLED. Ahora presenta el estado recibido y avisa del saldo pendiente no pagado. El listado vuelve a distinguir estado pendiente/parcial/pagado de cada cuota con foregrounds semánticos; no modifica estados ni operaciones.
8. Las capturas seleccionan el nodo de la ventana visible y rechazan imágenes uniformes del backdrop. El selector de cuentas usa nombres de evidencia propios para evitar colisión con el selector de referencias. El test compacto admite el directorio #39 sin modificar las imágenes versionadas de #19.
9. En el cronograma, el nombre/estado consumía el ancho del importe; en el editor TEA, el campo desplazaba el sufijo %. Se reparte el ancho con weight para preservar importes y unidades. Se verifican visualmente las capturas finales.
10. `KipuSystemBars` sincroniza iconos y scrim de la ventana Android con el tema efectivo de la app. Desactiva el scrim automático claro de navegación en API 29+ y conserva fondo oscuro compatible con iconos claros en API 24/25. Los previews de editor no ejecutan efectos de ventana. Las assertions de tema comprueban los flags nativos en API 35. La implementación usa [SystemBarStyle](https://developer.android.com/reference/androidx/activity/SystemBarStyle) y [edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge); no se atribuye runtime API 24.
11. Cada `ModalBottomSheet` tiene otra ventana Android: recibe explícitamente la preferencia mediante `kipuBottomSheetProperties`, preservando los defaults de seguridad y dismiss. La prueba instrumentada inspecciona también sus flags nativos. Se aplica a los 13 usos en los 12 archivos, incluido `KipuBottomSheet`; ajustar solo la Activity no elimina el scrim claro del modal.
12. La prueba de cambio de tema detectó que Material3 1.4 aplica esos flags solo en el constructor de su ventana; se corroboró en el artefacto local `material3-android:1.4.0`. `KipuModalSystemBars` actualiza los flags sobre esa misma ventana y desactiva su contraste automático en API 29+, conservando la instancia y el estado del modal. Se verifica claro→oscuro sin recreación del borrador.

Las aserciones obsoletas de cuentas/tasas también fallaron contra la APK base `f8e7ea3`; los logs de comparación se conservan localmente en `scratch/issue-39/`. Su corrección no consiste en omitir las pruebas ni cambiar reglas financieras para hacerlas pasar.

## Límites y revisión

- Validación física en 23129RA5FL, Android 15/API 35, 1080×2400, 440 dpi; fuente del sistema 1,0. La configuración compacta se aplica solamente al contexto del fixture.
- No se activó TalkBack hablado; se prueban semántica, orden/acciones y ausencia de importes ocultos. Esto no equivale a una sesión de escucha manual.
- No se dispone de un emulador API 24 en PATH; no se atribuye cobertura runtime de esa API.
- Las imágenes prueban apariencia de los estados capturados; las pruebas y revisión de código complementan esa evidencia.
- La revisión independiente de código y la aceptación visual humana permanecen como pasos del PR. No se realiza merge ni se cierra el issue automáticamente.

## Estado de integración y revalidación parcial (2026-10-17)

La base integrada es `origin/main` `d5667abdf66d85a32fae43d10de8233381e84051`; el código recibido en este seguimiento partía del head `1346ed965f5d4c810ad717fa67984259062b05a0`. El código de la corrección quedó en `836415cd04aa657731c71ad1bcdc7bba016ae80f`; la actualización documental posterior no cambia fuentes de aplicación.

Se retiró de `EmptyDashboardContent` el bloque `quota_strip`, sus valores de cupo y su callback de navegación a Planes, cumpliendo la regla de este plan de conservar navegación en cambios de presentación. Se mantienen la navegación a Planes del flujo de introducción de instrumentos y el bloque de límite del dashboard activo. `DashboardEmptyStateTest` ahora comprueba que no aparezcan «Cupo Free» ni «Ver planes» en el estado vacío. Los fixtures de `AccountDetailThemePreview` y `ThemeScreenEvidenceTest` usan `AccountPreset.GENERIC`, compatible con `AccountType.SAVINGS`.

Revalidación dirigida del código fuente `836415cd04aa657731c71ad1bcdc7bba016ae80f`:

- `:app:testDebugUnitTest --tests com.kipu.app.feature.accounts.presentation.dashboard.DashboardEmptyStatePolicyTest`: **PASS, 3 tests**.
- `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL** (compilación; no ejecuta AndroidTest).
- `git diff --check`: **PASS** para la corrección de código.
- No se completó el empaquetado lab ni se ejecutó instrumentación o captura nueva. Los PNG, la matriz completa, lint y los resultados de 521 pruebas/84 métodos siguen siendo evidencia histórica de `0118a19`.

T122 permanece abierto por previews/cobertura no renderizadas completamente. T123 permanece abierto: estos checks dirigidos no sustituyen build/lint y la regresión/dispositivo/capturas completos sobre la integración. T125, la revisión independiente final y la aceptación visual humana siguen abiertos; no se declara aprobación visual. TalkBack hablado y runtime API 24 también quedan pendientes. T110 sigue abierto y fuera de este alcance; metas/presupuestos son no aplicables.

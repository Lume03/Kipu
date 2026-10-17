# Plan de solución del issue #39: tema oscuro y auditoría visual

Fecha: 2026-10-13, America/Lima.
Origen: [Lume03/Kipu #39](https://github.com/Lume03/Kipu/issues/39).
Estado: implementación realizada en la rama indicada; resultados y límites en [auditoría](issue-39-dark-theme-audit.md) y [evidencia](evidence/issue-39/README.md). Revisión independiente y aceptación humana pendientes.
Rama de implementación: `fix/dark-mode-filters-theme-audit`.

## 1. Resultado esperado y alcance

El modal de filtros de movimientos y las demás superficies de la aplicación deben respetar el tema efectivo elegido por el usuario. Fondos, textos, iconos, bordes y estados interactivos deben conservar su significado, ser legibles y funcionar en claro y oscuro.

La entrega comprende:

- Corrección completa de `MovementFiltersSheet.kt`, incluidos sus componentes privados, calendario, controles avanzados, selectores y modal Premium.
- Auditoría de todos los recorridos disponibles y corrección de inconsistencias encontradas, también en dashboard, instrumentos, tasas y deudas.
- Consolidación de los roles de color que falten en las paletas existentes.
- Previews que rendericen los componentes intervenidos, capturas comparativas y registro de contraste.
- Regresión funcional de los flujos afectados y registro de resultados reales.

Los cambios de presentación conservarán consultas, reglas financieras, acceso Premium, datos persistidos, comportamiento de filtros y navegación. La revisión de metas y presupuestos se registrará como no aplicable si al implementar sigue sin haber pantallas de esas funciones. Las referencias a sus cuotas en planes no son pantallas implementadas.

Trazabilidad: el issue es la corrección solicitada; mantener los identificadores existentes de Spec Kit. Relacionar los cambios de movimientos con el refinamiento vigente de `specs/004-ep-mov-movimientos-ledger/` y sus criterios SC-014 a SC-017. Actualizar los registros pertinentes al implementar, sin reutilizar casillas o evidencia del issue #19 como prueba de #39. Los IDs `I39-*` de este documento identifican únicamente este trabajo.

## 2. Hallazgos comprobados en la lectura inicial

Rutas Kotlin relativas a `app/src/main/java/com/kipu/app/`:

| Archivo | Ocurrencias detectadas | Lectura inicial / acción |
|---|---:|---|
| `feature/movements/presentation/MovementFiltersSheet.kt` | 151 | 134 expresiones hexadecimales y 17 usos de blanco/negro; principal corrección. |
| `feature/debts/presentation/DebtScheduleScreen.kt` | 126 | Incluir cronograma, estados y acciones en auditoría. |
| `feature/accounts/presentation/instruments/RateCatalogScreen.kt` | 116 | Incluir catálogo, búsqueda, tarjetas y estados. |
| `feature/movements/presentation/PastelCategoryPickerFlow.kt` | 84 | Hay tokens adaptativos y colores del editor HSV; clasificar cada uso. |
| `feature/accounts/presentation/dashboard/DashboardScreen.kt` | 78 | Revisar tarjetas, resúmenes, gráficos y acciones. |
| `feature/debts/presentation/DebtDetailScreen.kt` | 62 | Se encontraron superficies claras y textos fijos. |
| `feature/movements/presentation/QuickMovementScreen.kt` | 53 | Revisar registro, estados y contenido sobre colores de acento. |
| `feature/debts/presentation/DebtSettlementSheet.kt` | 31 | Revisar registro de pago/cobro, advertencias y acciones. |
| `feature/accounts/presentation/detail/AccountDetailScreen.kt` | 27 | Soporte parcial; revisar componentes y modales dependientes. |
| `feature/movements/presentation/AccountSelectorBottomSheet.kt` | 26 | Muchos roles ya adaptativos; quedan contenido blanco y colores de marca. |
| `feature/accounts/presentation/instruments/InstrumentFormModels.kt` | 20 | Colores bancarios; verificar uso antes de modificar. |
| `feature/movements/presentation/MovementReferenceSelector.kt` | 16 | Modal dependiente del filtro, parte del recorrido principal. |
| `feature/categories/presentation/categories/CategoriesScreen.kt` | 14 | Revisar listado, selección, vacíos y menús. |
| `feature/categories/presentation/components/MerchantPicker.kt` | 14 | Revisar búsqueda, resultados y selección. |
| `feature/settings/presentation/PermissionsScreen.kt` | 12 | Revisar avisos y estados de permisos. |
| `feature/accounts/presentation/instruments/CreditCardTermsDialog.kt` | 11 | Revisar diálogo y contenido informativo. |
| `feature/movements/presentation/MovementDetailSheet.kt` | 1 | Usa CalmEmerald; queda blanco fijo en una acción. |
| `feature/movements/presentation/MovementEditorSheet.kt` | 0 | Ya usa roles temáticos; verificar combinaciones y dependencias. |

Conteo de candidatos, no de defectos: regex `Color\(0x|Color\.(White|Black)`. No incluye colores construidos de otras formas ni constantes heredadas. Una pantalla con cero coincidencias también necesita revisión visual.

Otros hechos relevantes:

- `MainActivity.kt` resuelve `LIGHT`, `DARK` y `SYSTEM` a partir del perfil y pasa `isDark` a `KipuTheme`.
- `Theme.kt` define ambos esquemas y `LocalKipuDarkTheme`; `CalmEmeraldTokens.kt` y `KipuUiColors.kt` ya tienen roles adaptativos.
- `KipuBottomSheet.kt` y `KipuFilterChip.kt` ya tienen valores predeterminados alineados al tema. Revisar sus consumidores antes de cambiar la infraestructura compartida.
- Existen previews de tokens y composiciones de ejemplo; no cubren todo el modal real de filtros.
- Hay `MovementFiltersIssue19Test`, `MovementFilterVisualEvidenceTest` y `AccountPresetScreenshotTest` reutilizables.
- La variante `lab`, activada para AndroidTest con `-PisolatedAndroidTests=true`, usa `com.kipu.app.lab` y configuración remota vacía.
- El CI actual ejecuta pruebas JVM y construye la APK; no ejecuta pruebas de dispositivo.
- `res/values/themes.xml` hereda un tema Android claro. Revisar ventanas y arranque en dispositivo; cambiar recursos solamente si se reproduce una inconsistencia fuera de Compose.

Cálculos preliminares de contraste, colores opacos sRGB:

| Contenido / fondo | Relación aproximada | Consecuencia |
|---|---:|---|
| `#FFFFFF` / primary oscuro `#14B8A6` | 2,49:1 | Blanco fijo sobre primary oscuro no alcanza 4,5:1. |
| onPrimary oscuro `#0B1220` / `#14B8A6` | 7,52:1 | La pareja temática existente resuelve este caso. |
| ingreso `#16A34A` / blanco | 3,30:1 | Evaluar variante más oscura si se usa en texto normal. |
| gasto `#E85D5D` / blanco | 3,41:1 | Evaluar variante de texto, manteniendo el significado del acento. |

Estos cálculos no certifican la interfaz completa; todavía falta verificar fondos efectivos, transparencias, estados y renderización.

## 3. Decisiones técnicas

### 3.1 Fuente de verdad del tema

Usar el tema efectivo de `KipuTheme`, no nuevas consultas a `isSystemInDarkTheme()` dentro de pantallas. El sistema puede estar claro mientras el perfil selecciona oscuro, y viceversa. Los roles nuevos seguirán `LocalKipuDarkTheme` mediante los proveedores existentes.

Mantener las tres capas existentes con responsabilidades claras:

1. `MaterialTheme.colorScheme`: superficies, contenido, acciones, bordes y errores generales.
2. `rememberKipuColors()`: roles semánticos compartidos del producto.
3. `rememberCalmEmeraldColors()`: roles financieros y visuales CalmEmerald que ya usan los consumidores.

No crear una cuarta paleta general. Añadir un rol solamente cuando no exista uno adecuado y haya un uso concreto. Alinear las equivalencias generales entre las paletas existentes; conservar roles específicos como gradientes y estados financieros.

Material 3 ofrece parejas de roles para contenedores y contenido; utilizarlas juntas en los consumidores. Referencia: [Material Design 3 en Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).

### 3.2 Mapeo de colores

| Uso actual | Rol previsto | Verificación |
|---|---|---|
| Texto principal oscuro fijo | `onSurface` / `inkPrimary` | Contraste con superficie real. |
| Texto secundario / placeholder | `onSurfaceVariant` / `inkSecondary` | Mantener legibilidad en ambos temas. |
| Fondo general | `background` | Coherencia con pantalla contenedora. |
| Modal / tarjeta neutral | `surface` o `surfaceContainer*` | Jerarquía entre modal, tarjetas y campos. |
| Fondo menta de selección | `primaryContainer` / `selectedSurface` | Contenido `onPrimaryContainer` o pareja semántica validada. |
| Acción principal | `primary` + `onPrimary` | Evitar blanco fijo. |
| Bordes de separación | `outlineVariant` / `border` | No exigir contraste de control a un borde decorativo. |
| Bordes necesarios para identificar controles | `outline` o rol medido | Identificación visible frente al fondo. |
| Error | `errorContainer` + `onErrorContainer`, o `error` sobre superficie validada | Texto y estado identificables. |
| Ingreso / gasto / transferencia | Roles CalmEmerald y sus contenedores | Conservar verde / coral / azul y comprobar contraste. |
| Aviso o bloqueo Premium | Roles warning y/o primary según significado | No tratar todos los estados como selección verde. |
| Acción deshabilitada | Defaults Material o roles explícitos | Distinguirla visualmente de la acción habilitada. |

Para chips, botones, switches, radio buttons, campos y calendarios, preferir los defaults Material que ya respeten el tema; personalizar únicamente lo necesario para el diseño vigente.

### 3.3 Colores que requieren tratamiento especial

- Marcas bancarias, tarjetas simuladas y logos: conservar identidad; tematizar la superficie externa y revisar el contenido superpuesto.
- Colores personalizados de categorías: conservar el valor persistido y la elección del usuario. Si se necesita adaptación visual, calcularla solo al renderizar y no guardar una versión distinta.
- Selector HSV, gradientes y muestras: blanco/negro pueden representar extremos del color y no ser un error de tema. Documentar estos usos.
- Acentos financieros: cuando una pareja no alcance el contraste, definir un rol de contenido apropiado en lugar de oscurecer indiscriminadamente todos los acentos.

El objetivo de búsqueda final es cero colores de interfaz inexplicados en los componentes corregidos. Las excepciones deben indicar uso, motivo y contraste si tienen contenido.

## 4. Fases de ejecución

### Fase A — Inventario y línea base

1. Crear la rama de implementación desde la base vigente, conservando cualquier trabajo local previo.
2. Volver a leer el issue y registrar commit base, versión de APK, dispositivo/API, resolución, densidad y escala de texto.
3. Inventariar rutas reales de navegación y sus modales, menús, selectores, banners y estados.
4. Buscar expresiones directas, `Color.White`, `Color.Black`, constantes de paleta clara, `Color(...)`, hexadecimales en strings, colores obtenidos de modelos, brushes y tintes de recursos.
5. Clasificar cada hallazgo como defecto, color semántico ya adaptativo, marca, color elegido por usuario, muestra cromática o caso pendiente de reproducción.
6. Capturar antes/después con el mismo fixture sintético. Obtener la línea base de filtros claro/oscuro antes de editar y recorrer ambos extremos de scroll.
7. Crear una matriz con ruta, componente, estado, color/fondo, tema, impacto, solución y evidencia.

Salida: inventario completo y línea base reproducible. No concluir que cada coincidencia es un bug.

### Fase B — Roles y contraste del tema

1. Revisar `Theme.kt`, `Color.kt`, `KipuUiColors.kt` y `CalmEmeraldTokens.kt`.
2. Medir las parejas realmente utilizadas y definir los roles faltantes de contenido, contenedor o estado.
3. Verificar superficies, texto secundario, primary/onPrimary, error, warning e ingreso/gasto/transferencia en claro/oscuro.
4. Distinguir el rol para texto financiero del acento decorativo si necesitan diferente luminosidad.
5. Revisar defaults compartidos de `KipuBottomSheet`, `KipuCard`, `KipuFilterChip`, estados vacíos y `MoneyText`.
6. Cambiar un componente compartido solo cuando el defecto esté en él; revisar sus consumidores afectados.

Salida: contrato de roles aprobado técnicamente y parejas medidas. Los valores finales se deciden por contraste y diseño, no por equivalencia mecánica de hexadecimales.

### Fase C — Corrección completa de filtros

Recorrer `MovementFiltersSheet.kt` en este orden:

1. Contenedor y cabecera: título, contador, cierre, iconos y resumen de filtros activos.
2. Periodo y fecha: tarjeta, presets, seleccionado/no seleccionado, fechas de inicio/fin, resumen, errores, comparación de mes anterior y switch.
3. Tipo de movimiento: segmentos para todos/gasto/ingreso/transferencia, contenido seleccionado, indicador y fondos.
4. Filtros avanzados: tarjeta, badges, expansión, filas bloqueadas, moneda, mínimo/máximo, campos enfocados, errores y chips de estados.
5. Referencias: `MovementReferenceSelector.kt`, búsquedas, filas seleccionadas, históricos ausentes, vacíos y acciones.
6. Pie fijo: fondo, borde o elevación tonal, Limpiar, Ver movimientos, contador, cero coincidencias y estado deshabilitado.
7. Calendario: `DateRangePicker`, entrada manual, confirmar/cancelar y rama compacta de `MovementDateRangeDialog`.
8. Premium: `KipuProUpsellDialog`, tarjetas de planes, radio buttons, beneficios, acción principal y cierre.
9. Helpers privados: `ActiveFilterDismissChip`, `FilterPresetChip`, `MovementTypeSegment`, `LockedAdvancedRow`, `AdvancedFiltersControls`, `UpsellBenefitItem`, `FilterDateEndpoint` y `FilterFieldError`.

Mantener firmas públicas, estado de borrador, `rememberSaveable`, key de propietario, tags existentes, límites de acceso y callbacks. Mantener las correcciones de espacio, calendario compacto, errores locales y selector perezoso del issue #19.

Salida: recorrido completo de filtros oscuro/claro, incluyendo modales anidados y estados de error/bloqueo, sin superficies claras forzadas de interfaz.

### Fase D — Auditoría y corrección por recorrido

| Prioridad | Recorrido | Archivos / superficies a revisar |
|---|---|---|
| P1 | Movimientos | `AccountSelectorBottomSheet`, `MovementDetailSheet`, `MovementEditorSheet`, `QuickMovementScreen`, `PastelCategoryPickerFlow`, `MovementHistoryScreen`, `MovementAppliedFilters`, `VoidMovementDialog`, recuperación/conflictos. |
| P1 | Cuentas e instrumentos | `AccountDetailScreen`, `DashboardScreen`, formularios de instrumentos, `RateCatalogScreen`, `CreditCardTermsDialog`, `CreditCardSummaryCard`, clasificación y simulador de cuotas. |
| P1 | Categorías y comercios | `CategoriesScreen`, `CategoryFormScreen`, `CategoryPresentationEditor`, `MerchantPicker` y flujos vinculados. |
| P1 | Deudas y préstamos | `DebtListScreen`, `DebtDetailScreen`, `DebtScheduleScreen`, `DebtSettlementSheet`, formularios y diálogos dependientes. |
| P2 | Ajustes | Perfil, selector de tema, permisos, biometría y diálogos. |
| P2 | Acceso, planes, notificaciones y navegación | Formularios, cierre de sesión, bloqueo local, compra/selección de planes, centro de notificaciones, banners, barra de navegación y ventanas Android. |
| Verificación | Metas y presupuestos | Confirmar existencia en la base usada; si siguen ausentes, registrar no aplicable con evidencia de rutas. |

Para cada recorrido comprobar normal, vacío, error, carga, selección, deshabilitado y modal si están implementados. Registrar “revisado sin cambios” cuando corresponda. Expandir la lista a componentes dependientes que el inventario descubra.

Salida: matriz cerrada con cada defecto corregido o cada excepción justificada. Una corrección aislada de filtros no permite cerrar la auditoría integral.

### Fase E — Previews representativas

1. Reutilizar los componentes de producción con fixtures sintéticos; evitar una composición de ejemplo que solo dibuje los tokens.
2. Agregar claro y `@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)` para cada componente intervenido.
3. Envolver previews en `KipuTheme`; usar `darkTheme = true/false` explícito cuando la preview represente un tema concreto.
4. Para filtros cubrir al menos Free, avanzado disponible, avanzado bloqueado, fechas seleccionadas, error de importe y cero resultados.
5. Para modales anidados usar la preview interactiva del flujo y añadir previews del contenido real cuando una ventana no se renderice bien en la preview estática.
6. Extraer contenido sin ViewModel/Hilt únicamente donde sea necesario para renderizar el mismo componente y conservar su API externa.
7. Incluir una configuración compacta y texto ×1,6 en los componentes con mayor densidad.

Salida: previews útiles para revisar futuros cambios. Los previews existentes de paletas siguen siendo complementarios.

### Fase F — Validación automatizada y manual

Pruebas nuevas limitadas a riesgos reales:

- Prueba instrumentada de paletas y parejas semánticas: comprobar el tema efectivo y contraste de roles utilizados, en ambos temas; usar valores resueltos de producción.
- Regresión de cambio de tema con el modal abierto: cambiar claro/oscuro y comprobar que contenido real se actualiza sin perder borrador ni selección.
- Fixtures de sistema claro/app oscuro y sistema oscuro/app claro para verificar que los helpers siguen `KipuTheme`.
- Evidencia visual de filtros y superficies corregidas: ampliar o reutilizar infraestructura de `MovementFilterVisualEvidenceTest` y `AccountPresetScreenshotTest`, guardando #39 en su propio directorio.
- Ejecutar pruebas existentes del filtro, selector, editor y recorridos modificados. Capturar imágenes no sustituye las aserciones funcionales ni una comparación visual revisada.

No añadir una prueba que solo cuente hexadecimales o replique el mapeo implementado. No introducir un framework nuevo de screenshots para esta corrección si la infraestructura existente cubre la evidencia necesaria.

Matriz mínima de aceptación:

| Escenario | Resultado esperado |
|---|---|
| App claro / sistema claro y app oscuro / sistema oscuro | Tema coherente en pantalla, tarjetas, modal y acciones. |
| App oscuro / sistema claro y app claro / sistema oscuro | Prevalece la selección de la app. |
| App SYSTEM y cambio de tema del sistema | UI se actualiza al tema efectivo. |
| Filtros Free y avanzado disponible/bloqueado | Colores correctos sin alterar acceso. |
| Presets, fechas y comparación mensual | Selección, resumen y switch legibles. |
| Monto/moneda inválidos, cero resultados | Mensajes visibles; comportamiento vigente de aplicar/limpiar conservado. |
| Selector con búsqueda, referencias históricas y 1.000 filas | Selección y scroll correctos en ambos temas. |
| Calendario visual/manual y ancho compacto | Acciones y contenido accesibles, sin fondo claro forzado. |
| Upsell Premium y planes seleccionados | Modal y estados tematizados, sin cambios en autorización o compra. |
| Enmascaramiento y TalkBack | No exponer importes ocultos; conservar etiquetas/roles y estados. |
| Fuente ×1,6, ancho aproximado 320 dp y teclado visible | Controles y pie alcanzables, sin recortes nuevos. |
| Cambio de propietario, cerrar/cancelar/reabrir | Conservar las invariantes existentes del borrador. |

Cobertura de dispositivos: ejecutar el recorrido principal en un emulador/dispositivo con la API de uso vigente y hacer smoke en API 24 si hay un entorno disponible. Registrar API, densidad y escala; la falta de un segundo entorno debe quedar como límite explícito.

Contraste:

- Aplicar el requisito del issue de al menos 4,5:1 a textos normales y chips habilitados en ambos temas. La norma admite 3:1 para texto grande, pero usar 4,5:1 como objetivo conservador de esta corrección. [WCAG 2.2, contraste mínimo](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).
- Comprobar al menos 3:1 en iconos y rasgos visuales necesarios para identificar controles o estados frente al color adyacente. Los elementos puramente decorativos y controles inactivos tienen excepciones normativas; documentarlas. [WCAG 2.2, contraste no textual](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html).
- Medir el fondo final: componer alpha sobre la superficie real y considerar elevación tonal. Para gradientes, medir las zonas donde hay contenido y su caso de menor contraste.
- Usar `(Lmayor + 0,05) / (Lmenor + 0,05)` con luminancia relativa sRGB; guardar contenido/fondo, tema, ratio y resultado. Revisar capturas para confirmar que la pareja medida corresponde a lo renderizado.

Comandos propuestos desde la raíz, para la fase de implementación:

```powershell
# Comprobación local y equivalentes de CI.
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

# Construcción de pruebas en variante aislada.
.\gradlew.bat -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest

# Con emulador/dispositivo preparado; usar ANDROID_SERIAL si hay varios.
.\gradlew.bat -PisolatedAndroidTests=true :app:connectedLabAndroidTest
```

Si el entorno limita la suite de dispositivo, ejecutar explícitamente las clases pertinentes con el runner existente y registrar qué se ejecutó y qué quedó pendiente. `scripts/android/run-issue19-instrumentation.py` es una referencia de arranque MIUI; revisar su contenido antes de reutilizarlo o extenderlo para #39.

Salida: resultados JVM/build/lint, pruebas runtime, ratios y revisión visual. Una APK compilada o AndroidTest compilado no equivale a aceptación runtime.

### Fase G — Evidencia y entrega

1. Guardar la evidencia de #39 en `docs/audits/evidence/issue-39/`, con README, matriz de auditoría, tabla de contraste y resultados de validación.
2. Registrar commit antes/después, mismo fixture, dispositivo/API y metadatos. No retocar capturas usadas como evidencia.
3. Comparar antes/después del modal oscuro y claro/oscuro final de filtros, calendario, selector, upsell, cuentas, categorías y demás superficies modificadas.
4. Mantener capturas y resultados antiguos del issue #19 intactos.
5. Registrar trazabilidad en los artefactos/review-record pertinentes de Spec Kit para los cambios efectivamente realizados; no marcar tareas históricas como evidencia nueva.
6. Preparar commits revisables: roles compartidos, filtros, demás recorridos, previews/evidencia según dependencia. Cada unidad debe compilar.
7. Preparar descripción de PR con problema, comportamiento resultante, archivos/recorridos, validación, excepciones y capturas. Incluir `Closes #39` solo cuando estén satisfechos todos sus criterios.
8. Realizar revisión de código por otro revisor y resolver hallazgos antes de considerar terminada la corrección.

Salida: cambio revisable con evidencia. El responsable autorizó posteriormente ejecutar el plan, usar el dispositivo conectado y guardar/subir la rama. La integración local con `origin/main` se realizó el 2026-10-17 en el worktree de revisión; sigue sin commit ni push y no equivale al merge de la PR en GitHub.

## 5. Tareas y dependencias

La implementación y la validación originales corresponden a `0118a19` (PR head documental `d22e308`), antes de integrar `origin/main` en `d5667ab`. El código de aplicación corregido y revalidado parcialmente está en `836415cd04aa657731c71ad1bcdc7bba016ae80f`, basado en el head integrado `1346ed965f5d4c810ad717fa67984259062b05a`. Se retiró el nuevo bloque de cupo/navegación del estado vacío; las rutas preexistentes del dashboard activo y de la introducción se conservan. El fixture de cuenta de ahorro ahora usa el preset compatible `GENERIC`.

Revalidación parcial del código `836415c`: `DashboardEmptyStatePolicyTest` pasó 3 tests y `compileDebugAndroidTestKotlin` compiló el source set AndroidTest. No se completó el empaquetado lab ni se ejecutó instrumentación, lint, capturas ni aceptación visual sobre este head. I39-15 sigue parcial: hay componentes sin previews completas/renderizadas. I39-18/I39-19 no se consideran completadas por estos checks dirigidos. TalkBack hablado, runtime API 24 y aceptación visual humana siguen pendientes. I39-22 permanece abierta. Ver [README de evidencia](evidence/issue-39/README.md) para alcance exacto; los resultados de 521 pruebas, 84 métodos, lint, contraste y PNG siguen siendo históricos de `0118a19`.

| ID | Trabajo | Depende de | Criterio de terminación |
|---|---|---|---|
| I39-01 | Registrar base e inventario de rutas/superficies | — | Todos los recorridos reales identificados; metas/presupuestos con estado explícito. |
| I39-02 | Capturar línea base sintética y clasificar colores | 01 | Antes claro/oscuro de filtros y matriz inicial con excepciones. |
| I39-03 | Definir mapeo y medir roles existentes | 02 | Tabla de parejas y gaps de contraste. |
| I39-04 | Corregir/ampliar paletas y defaults defectuosos | 03 | Roles semánticos disponibles en ambos temas; revisión de consumidores compartidos. |
| I39-05 | Corregir cabecera, resumen, periodo y fechas | 04 | Selección y estados legibles sin colores claros forzados. |
| I39-06 | Corregir tipos, filtros avanzados y helpers | 05 | Estados normales/seleccionados/errores/bloqueados tematizados. |
| I39-07 | Corregir pie, calendario y modal Premium | 06 | Recorrido completo y acciones habilitadas/deshabilitadas tematizadas. |
| I39-08 | Revisar/corregir selector de referencias | 04, 06 | Búsqueda, filas e históricos tematizados; comportamiento de #19 conservado. |
| I39-09 | Revisar/corregir selector de cuentas y detalles | 04 | Parejas de contenido, marcas y modales documentadas. |
| I39-10 | Revisar/corregir registro rápido, editor e historial | 04 | Interacciones y estados de movimientos revisados. |
| I39-11 | Revisar/corregir categorías y comercios | 04 | Tema correcto; colores persistidos y editor cromático conservados. |
| I39-12 | Revisar/corregir dashboard e instrumentos/tasas | 04 | Tarjetas, catálogo, formularios y diálogos revisados. |
| I39-13 | Revisar/corregir deudas y préstamos | 04 | Listado, detalle, cronograma y liquidación revisados. |
| I39-14 | Revisar/corregir ajustes, acceso, planes y notificaciones | 04 | Estados y ventanas del resto de recorridos revisados. |
| I39-15 | Agregar previews del contenido real intervenido | 07–14 | Claro/oscuro y estados representativos renderizan componentes reales. |
| I39-16 | Validar contraste de roles y tema efectivo | 04, 07–14 | Parejas utilizadas pasan umbrales; override de app cubierto. |
| I39-17 | Validar cambio de tema con modal/borrador | 07, 08 | Se actualiza presentación sin perder selección ni borrador. |
| I39-18 | Ejecutar build, lint y regresiones JVM/runtime | 15–17 | Resultados registrados; fallos nuevos resueltos. |
| I39-19 | Revisar manualmente matriz y capturar comparativas | 18 | Evidencia del tema, estados, teclado y texto ampliado. |
| I39-20 | Cerrar inventario y registrar excepciones | 19 | Sin defectos visuales pendientes ni colores inexplicados. |
| I39-21 | Actualizar trazabilidad y preparar PR | 20 | Relación requisito/cambio/validación y entrega revisable. |
| I39-22 | Revisión de código y aceptación final | 21 | Criterios del issue satisfechos con evidencia vigente. |

Las tareas 09–14 comparten la dependencia de roles, pero pueden resolverse secuencialmente por recorrido. No continuar ampliando la auditoría mientras el flujo principal de filtros tenga defectos pendientes que impidan validarlo.

## 6. Riesgos y decisiones para mitigarlos

| Riesgo | Medida |
|---|---|
| Reemplazar colores visualmente parecidos pero semánticamente distintos | Clasificar uso y elegir roles por función; revisar cada pareja. |
| Blanco sobre primary oscuro poco legible | Sustituir por `onPrimary` y medir todos los estados. |
| Corrección global afecta consumidores que ya funcionaban | Cambios compartidos mínimos, inventario de consumidores y smoke por recorrido. |
| Tema del sistema contradice preferencia de app | Usar el tema efectivo y validar combinaciones opuestas. |
| Pérdida de color de categoría o marca | Adaptación solo de presentación; excepciones documentadas. |
| Previews muestran ejemplos distintos de producción | Renderizar contenido real y corroborar con dispositivo. |
| Evidencia nueva sobrescribe #19 | Directorio y nombres propios para #39. |
| Auditoría queda limitada al archivo reportado | Inventario por navegación, estados y superficies dependientes. |
| El build pasa pero la UI sigue fallando | Validación runtime, ratios y revisión de capturas obligatorios. |

## 7. Criterios de cierre

- [x] Filtros oscuro integrado: contenedor, tarjetas, campos, iconos, chips, pie y modales anidados.
- [ ] Textos habilitados cumplen el requisito de contraste en claro y oscuro; controles e iconos necesarios también tienen contraste medido.
- [x] Claro/oscuro/SYSTEM siguen la preferencia efectiva de la app.
- [x] Las rutas implementadas del inventario original fueron revisadas; la verificación de la nueva versión integrada queda limitada a las superficies de dashboard y registro rápido señaladas en esta actualización.
- [x] Metas/presupuestos no tienen pantallas en el código integrado; quedan documentados como no aplicables, sin marcar pantallas inexistentes como completadas.
- [x] Excepciones cromáticas justificadas; colores personalizados persistidos conservados.
- [ ] Previews claras/oscuras para todos los componentes intervenidos; ya existen previews reales para los componentes enumerados en el README, pero quedan componentes acoplados a ViewModels/fixtures sin cubrir.
- [ ] Regresión de borrador, selección, aplicar/cancelar/limpiar, acceso, enmascaramiento y cambio de propietario validada sobre el resultado integrado. Solo se ejecutó la prueba JVM dirigida de política del estado vacío; no sustituye estas regresiones.
- [ ] Build/JVM/lint y pruebas de dispositivo aplicables sobre el resultado integrado. En `836415c` pasaron 3 tests JVM dirigidos y compiló `compileDebugAndroidTestKotlin`; el empaquetado lab, la instrumentación completa y lint siguen sin validar.
- [ ] Capturas comparativas actuales de las superficies cambiadas por la integración; las capturas versionadas son de `0118a19` y no muestran el dashboard/registro rápido integrados.
- [ ] Trazabilidad de la corrección y revisión de código completas. La retirada del CTA y el SHA verificado se registran en el audit/README; la revisión final y aprobación siguen pendientes.

## 8. Registro inicial y ejecución

Se leyó el issue y se inspeccionaron tema, archivos candidatos, navegación disponible, configuración de pruebas y evidencia existente. Se calcularon cuatro parejas preliminares de contraste.

En la entrega inicial de planificación no se ejecutaron builds ni suites. La implementación original registró compilación, lint, regresiones JVM y pruebas instrumentadas en un dispositivo físico; esas ejecuciones y capturas corresponden a `0118a19`, no al resultado integrado local. La [auditoría](issue-39-dark-theme-audit.md) y el [README de evidencia](evidence/issue-39/README.md) conservan ambos alcances. Las 28 parejas registradas pasan 4,5:1 dentro de esa matriz; esto no certifica todos los textos, píxeles ni la accesibilidad completa. No se atribuyen TalkBack hablado, runtime API 24 ni aceptación visual humana.

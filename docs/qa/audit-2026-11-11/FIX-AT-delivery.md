# FIX-AT — androidTest y A16-02

Fecha: 2026-11-14. Rama: `Lume03/audit-fixes`. Samsung A16 `RFGL70J9E2T`, Android 16.

**Cierre parcial: las 20 clases originales pasan completas; A16-02 continúa abierto tras dos intentos de arreglo. No se declara aprobación global de la rama.**

## Alcance y evidencia

Se leyeron las salidas `FIX-<FQCN>.txt`, el código y los informes de entrega de la auditoría. Las referencias principales para distinguir fixtures antiguos de regresiones fueron `L3b-delivery.md`, `L4-delivery.md`, `L5-delivery.md`, los lotes de UI/limpieza L10–L12 y `FIX-A16-delivery.md`. No se cambiaron requisitos ni artefactos Spec Kit.

La evidencia de dispositivo permanece fuera del repositorio en `C:\Users\Alume\AppData\Local\Temp\kipu-a16-20261114`:

- `FIX-AT-attempt1-results.json`: primera pasada, 13 clases PASS y 8 FAIL.
- `FIX-AT-results.json`: segunda pasada, solo las 8 clases fallidas, 7 PASS y 1 FAIL.
- `FIX-AT-results-final.json`: consolidación, 20 clases PASS y 1 FAIL.
- `FIX-AT-attempt1-<FQCN>.txt`: salidas de la primera pasada; `FIX-AT-<FQCN>.txt`: última salida de cada clase.
- `FIX-AT-final-build.txt`, `FIX-AT-install-rerun-final.txt`, `FIX-AT-counts.json`, `FIX-AT-final-settings.json` y `FIX-AT-stability-final.txt`.
- Capturas/XML de dashboard e historial y `FIX-AT-compact-before-clearance-check.png`. Las capturas reales contienen datos del dueño y no se versionan.

Cada clase se ejecutó con `adb -s RFGL70J9E2T shell am instrument -w -e class <FQCN> com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`. El dictamen usa el resumen JUnit y los errores explícitos, no el exit code de adb.

## Clasificación y resultado por clase

Los nombres cortos se corresponden con los FQCN de `FIX-reviewed-results.json` y de las salidas privadas anteriores. PASS significa clase completa, sin asunciones omitidas ni timeouts.

| Clase | Clasificación | Arreglo | Resultado en A16 |
|---|---|---|---|
| `AccountDetailScreenTest` | Test desactualizado y pérdida real de semántica accesible | Textos visibles abreviados actuales y restauración de la descripción «Crédito disponible, no es dinero propio» en producción. | PASS, 23 casos |
| `Issue48ProductionLabelsTest` | Host sin Hilt y fixture desactualizado | Inyección explícita de `DashboardProfileViewModel` mock; flujo nullable `creditNotificationsError`; total real S/ 2.300 separado del crédito y ausencia del antiguo banner permanente de cupo. | PASS, 1 caso |
| `AuthVisualFlowTest` | Test desactualizado | Búsqueda en el árbol semántico fusionado de los campos de contraseña; conserva confirmación, validación y contador de envíos. | PASS, 12 casos |
| `BudgetScreensTest` | Test desactualizado | Acciones por overflow, solicitud de alta desde el host, descendientes semánticos de importes, textos actuales de confirmación/cupo y entrada de finalizados. Conserva importes 500/120/380/100, porcentajes, cancelación sin efectos y borrado solo confirmado. | PASS, 31 casos |
| `SyncCategoryCommandsWorkerTest` | Fixture desactualizado; sin regresión de sync reproducida | Lookup nullable explícito, outbox mock que retira comandos completados y revisión esperada de los borrados. Verifica revisión final 2 y que un recibo anterior no publique SYNCED mientras exista sucesor. | PASS, 5 casos |
| `DebtChangeFeedTest` | Expectativa de checkpoint incorrecta; sin regresión de sync reproducida | Una página rechazada conserva el checkpoint inexistente; no lo inventa ni lo avanza a 1. Mantiene rechazo, ausencia de escrituras parciales y conservación de deuda/eventos financieros. | PASS, 8 casos |
| `DebtPlanningTabTest` | Test desactualizado | Texto de vencimiento actual, alta mediante `openRegisterDebt` del host y denominación Plan Free del sheet. | PASS, 6 casos |
| `DebtScheduleAndClosureFlowTest` | Test desactualizado | Corrige el literal corrupto `planificaci?n`; mantiene la afirmación de que las cuotas no registran pagos. | PASS, 6 casos |
| `DebtSettlementFlowTest` | Test desactualizado y fixture financiero inconsistente | Corrige `inter?s`, fechas españolas y copy de cuota; la obligación pagada usa SETTLED/saldo cero y se abre la sección Saldadas. Se comprueba vencimiento de la cuota parcial, sin presentar su planificación como un pago nuevo. | PASS, 7 casos |
| `PayableDebtFlowTest` | Test desactualizado | Selección explícita de día en calendario; editar/eliminar por overflow; diálogo Historial protegido con confirmación deshabilitada y saldo S/ 40 conservado. | PASS, 4 casos |
| `GoalLifecycleFlowTest` | Fixture desactualizado | Proporciona una incidencia realista al host compartido y verifica el aviso unificado y recuperación desde servidor, conservando reserva cero y objetivo/faltante S/ 1.000. | PASS, 13 casos |
| `GoalManagementFlowTest` | Test desactualizado | Solicitud de alta desde el host; cupo comprobado mediante bloqueo real de creación; meta completada permite una creación; copy actual de acciones y cierre del sheet. | PASS, 14 casos |
| `Issue48ProductionCreditLabelsTest` | Test desactualizado y pérdida real de semántica accesible | Deuda en lugar de Usado; textos visibles actuales, descripciones financieras completas restauradas e importes 4.000/1.420/2.580. Conserva la distinción accesible entre crédito y dinero propio en el selector. | PASS, 2 casos |
| `MovementRefundScreenTest` | Test desactualizado | Invoca SetText accesible del importe enmascarado, sin exigir RequestFocus eliminado por privacidad; copy completo del diálogo de anulación. Conserva ocultación de dinero, remanente, envío de 2.500 unidades menores y confirmación. | PASS, 4 casos |
| `PlanPurchaseScreenTest` | Suposición de densidad incorrecta | Tamaño del shimmer comparado en píxeles redondeados por la densidad real, manteniendo anclaje y tamaño invariable durante animación. | PASS, 8 casos |
| `RecurrenceLifecycleFlowTest` | Fixture desactualizado | Estado de recurrencia actual e incidencia suministrada al aviso compartido No sincronizado; conserva contadores de comandos y cancelación sin efectos. | PASS, 4 casos |
| `CellularCorrectionsTest` | Test desactualizado | Progreso comprobado mediante la semántica actual del anillo: `0 % reservado`. | PASS, 11 casos |
| `OptionalDateFormsTest` | Expectativa de fixture incorrecta | El editor de deuda conserva entrada ISO editable; los otros cuatro formularios siguen siendo solo calendario. Conserva selección, limpieza y fechas efectivamente enviadas. | PASS, 9 casos |
| `PlanningVisualEvidenceTest` | Test desactualizado | Abre el borrado de presupuesto mediante su menú de acciones; conserva todos los estados, temas y capturas con texto grande. | PASS, 1 caso |
| `ThemeScreenEvidenceTest` | Host sin Hilt | El host compartido aporta también un `PlanPurchaseViewModel` mock para el upsell, sin resolver dependencias reales ni comprar/cargar datos del dueño. No se omitió el cuerpo del test. | PASS, 1 caso |
| `MovementFiltersIssue19Test` — adicional | Bug real de producción A16-02, pendiente | Medición del pie, padding condicionado al IME y BringIntoViewRequester; dos intentos descritos debajo. No se modificó este test. | **FAIL: 9 PASS y 1 FAIL de 10 casos** |

El «host Hilt» citado en el despacho es, en el árbol recibido, `testing/SyncNoticeComposeRule.kt`: un host ComponentActivity con inyección explícita por CompositionLocal, no una Activity anotada con Hilt. Se extendió ese mecanismo de aislamiento y se pasó explícitamente el ViewModel de perfil al dashboard; no se usó la base persistente como fixture.

## Bugs de producción y análisis de sync

### Semántica financiera de crédito: corregida

El panel activo/archivado de tarjeta muestra copy visual breve. Había perdido la descripción accesible que distingue el crédito disponible del dinero propio, y el nombre completo de línea total de crédito. `AccountDetailScreen.kt` vuelve a exponer ambos mediante semántica, conservando diseño, importes y máscara. Las dos clases instrumentadas de detalle/etiquetas pasan; no se modificaron cálculos ni persistencia.

### A16-02: implementado parcialmente, sin cierre

`MovementFiltersSheet.kt` mide el pie con `onSizeChanged`, reserva en el contenido desplazable su altura real más 8 dp cuando el IME está visible y añade `BringIntoViewRequester` a ambos campos de importe al recibir foco/cambiar el teclado. Se mantiene además una separación de 8 dp en el límite del viewport.

Primer intento: la lectura del IME se hacía en la composición que lanza el modal; el caso compacto seguía fallando. Segundo intento: se trasladó la lectura de visibilidad a la ventana del propio sheet y se reservó la separación en el viewport. **El resultado instrumentado sigue siendo el mismo:** campo bottom=281,6 dp, pie top=282,51428 dp; separación **0,91428 dp**, inferior a 8 dp. La captura vuelve a mostrar Máximo recortado. El caso usa viewport compacto oscuro y fuente 1,6; los otros nueve métodos de la clase pasan.

Por la regla de dos intentos del despacho, no se hizo un tercer cambio ni se rebajó la aserción. Queda por resolver la restricción efectiva de altura/scroll del cuerpo compacto con su cabecera y pie; no se presenta el padding añadido como solución validada. Evidencia: ambas salidas de la clase y `FIX-AT-compact-before-clearance-check.png`.

### Deudas y categorías: fixtures, sin cambio en producción

`nonIncreasingSequenceRejectsWholePageWithoutPartialWrites` no había sembrado checkpoint alguno, pero esperaba 1 después del rechazo. El borrado remoto que eliminaría historial repetía esa expectativa. Ambas aserciones ahora exigen que siga ausente, y permanecen las comprobaciones de rechazo y conservación financiera. No se avanzó el cursor ni se relajó la validación de páginas.

En categorías, el mock devolvía siempre los mismos comandos no resueltos incluso tras `complete`, por lo que la protección de L4 contra acuses antiguos hacía correctamente que no publicara SYNCED. El mock ahora reproduce la transición de la outbox y las revisiones esperadas de borrado. Además, el mock relajado inventaba presentaciones existentes; el lookup nullable explícito permite comprobar las tres presentaciones hidratadas. Las cinco pruebas pasan sin modificar el worker ni el pull compartido.

## Verificación

- `gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL** en la última compilación del código de producción. XML JVM: **1.756 casos, cero fallos, cero errores y una omisión**, en 332 clases. Los últimos ajustes posteriores fueron únicamente selectores/fixtures instrumentados y se recompilaron con la instalación final.
- `gradlew.bat :app:installDebug :app:installDebugAndroidTest`: **BUILD SUCCESSFUL**, incluida la instalación final de los fixtures. Conserva los datos.
- A16: **20/20 clases originales PASS, 170 casos PASS**. Sumando filtros: **21 clases, 180 casos, 179 PASS y 1 FAIL**. Sin timeouts ni clases omitidas.
- No se repitieron las 13 clases verdes de la primera pasada después de reinstalar. La segunda instalación mantiene esos cambios e incorpora el segundo intento de filtros y los siete arreglos pendientes; esa distinción limita la evidencia de cada APK.
- `git diff --check`: PASS.
- Logcat desde las 16:33 hasta la comprobación final: **cero FATAL EXCEPTION** de la app. El buffer original conserva errores históricos de la mañana; no se borró logcat ni se cuentan como errores de este despacho.
- Dashboard e historial reales cargan después de instalar. El historial muestra los registros preexistentes de apertura y reembolso anulado, sin error de carga. No se crearon operaciones financieras manuales.

## Estado final del teléfono

- Siguió con la sesión del dueño iniciada; se dejó el dashboard visible. Disponible S/ 584,29, BCP S/ 372,71 y efectivo S/ 211,58, coincidentes con el informe previo.
- Se leyeron al terminar `window_animation_scale=1.0`, `transition_animation_scale=1.0`, `animator_duration_scale=1.0`, `font_scale=0.9`, `airplane_mode_on=0`.
- Se usó exclusivamente el adb del SDK con `-s RFGL70J9E2T`. No hubo tareas connected, desinstalaciones, `pm clear`, borrado/restauración de datos, cierre de sesión ni ejecución de las clases prohibidas. Las pruebas ejecutadas utilizan modelos sintéticos, mocks o Room en memoria.
- No se usó Supabase remoto, SQL manual ni Spec Kit. No se hicieron commits ni push. Los cambios quedan en el worktree para revisión.
- **Pendiente único de este despacho: A16-02.** Se entrega como fallo explícito tras dos intentos, junto con el código parcial y su evidencia; requiere continuación antes de aprobar ese caso.

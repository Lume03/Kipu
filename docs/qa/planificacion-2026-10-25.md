# Revisión de Planificación — 25 de octubre de 2026

Trabajo realizado en `main` y en el worktree recibido, sin ejecutar Spec Kit. No se modificaron referencias visuales, especificaciones, migraciones ni datos remotos directamente. Las cuatro eliminaciones de imágenes que ya estaban en el worktree se excluyeron de los commits.

**Resultado:** correcciones enumeradas verificadas localmente, con las diferencias y límites documentados al final. No certifica despliegue remoto ni paridad total con funciones del mockup fuera del contrato aprobado.

**Comprobación remota posterior:** la [auditoría de sincronización](sincronizacion-planificacion-2026-10-25.md) confirmó RPC/esquema pendientes de Metas y Deudas y un fallo de fechas de Presupuestos, corregido posteriormente. Los resultados de interfaz de este informe no acreditan respaldo remoto de sus operaciones.

## Referencias y autoridad

Se revisaron el README de Planificación, M1–M9, Presupuesto 1–8 y P1–P6, su guía de colores, D0–D17 y las variantes D2–D5. Los HTML empaquetados se descomprimieron localmente para leer 42 plantillas; los `.dc.html` también se inspeccionaron como fuentes de artboards. La ausencia de `support.js` no se interpretó como ausencia del diseño: se contrastaron las capturas.

Las reglas financieras se contrastaron con [EP-MET](../../specs/006-ep-met-metas-reservas/spec.md), [EP-PRE](../../specs/008-ep-pre-presupuestos-consumos/spec.md), [EP-DEU](../../specs/005-ep-deu-deudas-prestamos/spec.md) y la [constitución](../../.specify/memory/constitution.md).

## Cambios

- **Metas:** tarjeta completa pulsable sin interferir con los tres puntos; hoja de acciones con iconos, colores y opciones por estado; total reservado separado por moneda; límite del plan también desde el (+); sugerencias y plazos rápidos en el formulario; protección del botón Guardar frente a la navegación de Android; detalle con tres importes paralelos; acceso al historial y al flujo real de archivo, exigiendo resolver antes cualquier reserva. Revalidación del plan sin reiniciar la lista y mensaje de borrado que incluye operaciones pendientes de sincronización.
- **Presupuestos:** tres puntos independientes de la tarjeta; menú con detalle, edición, finalización en ámbar y eliminación; confirmaciones conectadas; pantalla de presupuesto creado con datos persistidos reales; tarjetas compactas; detalle con anillo, importes paralelos, estado, alcance, moneda, renovación, alertas y periodos anteriores. Se distinguen atención, proximidad al límite y exceso; se conserva la configuración de umbrales. Los importes mantienen sus grupos semánticos para accesibilidad y la máscara existente.
- **Deudas:** tres puntos en cada tarjeta y acciones según obligación/estado; historial completo con pagos, intereses, ajustes y anulaciones; vista de vencimientos con filtros vencidas/por vencer y acceso al pago/cobro correcto; Cerrar deuda abre directamente la pestaña de cierre; formulario de edición protegido frente al teclado y las barras del sistema; acceso al movimiento real desde cada pago del historial. La consulta Room conserva el identificador del movimiento, sin cambiar el esquema.
- **Movimientos vinculados a deudas:** el capital se presenta como pago/cobro de deuda y el interés conserva su identidad. La anulación de las operaciones que el dominio admite abre una confirmación y utiliza la reversión atómica existente de capital e interés; la edición financiera sigue bloqueada. Se contempla el formato legado de pagos. No se calcula una previsualización incompleta que omita el interés asociado.

## Navegación y carga

La causa principal era `navigateRootTab`: descartaba la entrada de Planificación y su almacén de ViewModels al volver al dashboard. Dinero y Movimientos, en cambio, comparten el contenedor retenido. La vuelta a Planificación creaba estados iniciales de carga y perdía selección, filtros y scroll.

La navegación ahora guarda/restaura la entrada y sus ViewModels, conserva el estado guardable y garantiza que una pestaña inferior abra su raíz aunque hubiera un detalle guardado. Una regresión instrumentada verifica identidad de entrada y almacén durante cuatro ciclos. Las pruebas del contenedor retenido verifican scroll e interacción.

Presupuestos también repetía la lectura inicial desde `init` y `ON_RESUME`. La primera reanudación aprovecha la carga inicial; las posteriores actualizan consumo manteniendo el contenido visible. Una prueba cambia el consumo del lector y comprueba tanto la ausencia de la consulta inicial duplicada como la actualización al volver. Se mantienen las observaciones Room de los registros y las revalidaciones del plan. No se reemplazó esta lógica por una caché que ignore operaciones nuevas.

Análisis sigue siendo provisional. No tiene consultas ni una carga de datos propia que corregir; participa en la navegación de raíces. No se modificó su diseño.

## Validación

Se usó el Samsung SM-A165M conectado y se mantuvo la sesión existente. El ADB de `C:\Windows` era incompatible con el servidor; se usó el de `Android/Sdk/platform-tools`.

| Comprobación | Resultado y alcance |
| --- | --- |
| Compilación de aplicación y APK de instrumentación | Correcta: `:app:assembleDebug :app:assembleDebugAndroidTest` |
| Unitarias iniciales | 161 aprobadas en presupuestos, metas, deudas y transiciones de raíces |
| Regresión de acceso al movimiento por ID | 22 unitarias de `MovementHistoryViewModelTest` aprobadas, incluyendo propiedad y acceso fuera de la página cargada |
| Regresión de carga de Presupuestos | 10 unitarias de `BudgetListViewModelTest` aprobadas, incluyendo primera reanudación y actualización posterior |
| Revalidación de Metas | `GoalListViewModelTest` aprobada después del cambio |
| Instrumentación | 85 casos distintos aprobados al consolidar el último resultado de cada caso; se reejecutaron únicamente casos afectados por correcciones posteriores |
| Evidencia visual | 37 capturas con fixtures sintéticos: temas claro/oscuro y siete casos de texto grande; revisión visual de detalles, menús y confirmaciones |
| Integración financiera local | CRUD presupuestario sin modificar movimientos, cuotas/cierre de deudas y reversión conjunta de capital/interés en Room; no son pruebas de despliegue remoto |
| Lint | `:app:lintDebug` terminó correctamente, con 0 errores, 312 advertencias y 2 sugerencias. El primer intento falló internamente con `ClassCastException` en el analizador Kotlin; no se ocultó el detector ni se desactivaron reglas para conseguir el resultado |

Las pruebas instrumentadas usan composables de producción y bases aisladas cuando prueban persistencia financiera. Las capturas de la cuenta y los logs permanecen en la carpeta local ignorada `scratch/planning-review`; no se publican bases, sesiones ni datos personales.

### Cuenta real

- **Metas:** creación de `QA Planificacion 2510`, edición, apertura diferenciada de tarjeta/menú, cancelación de borrado y persistencia tras reiniciar la aplicación. El borrado físico fue rechazado por operaciones pendientes de sincronización, conforme a la regla existente. Se archivó `QA Planificacion editada 2510`, sin reservas, y se retiró de las activas. Permanece como registro de prueba archivado; no se afirma que haya sido eliminado físicamente.
- **Presupuestos:** creación de `QA Presupuesto 2510`, confirmación de alta, menú desde lista, edición de nombre/límite, recálculo de exceso a proximidad al límite, detalle, cancelación de finalización, finalización efectiva, menú reducido para finalizados y eliminación confirmada. El presupuesto de prueba quedó eliminado y se actualizó el cupo. No se crearon movimientos para simular gasto: la pantalla mostró los gastos elegibles que ya existían.
- **Deudas:** apertura de menús, historial, formulario de pago y límite compartido de dos obligaciones activas. Se abrió el movimiento real desde el historial y se comprobó su etiqueta de pago de deuda, su confirmación de anulación y la cancelación sin ejecutar la reversión. Se verificó que Cerrar deuda abre Ajustes y cierre. La cuenta ya estaba al límite: no se alteraron obligaciones existentes para habilitar una nueva. Alta, guardado de edición, pago/cobro, cierre efectivo y reversión se verifican con los fixtures aislados; no se presentan como CRUD completo sobre esta cuenta.
- **Navegación:** tres ciclos Dinero → Movimientos → Análisis → Planificación conservaron Deudas y el filtro Me deben. También se comprobó actualización tras guardar y borrar el presupuesto de prueba. No se midieron FPS ni se afirma una mejora cuantificada de tiempo de frame.

## Diferencias conservadas y límites

- M6–M7 proponen pausar, completar liberando automáticamente y borrar historia/reservas. EP-MET exige completar según el progreso, mantener la reserva y conservar historia; solo permite borrar metas vacías y sin operaciones pendientes. Se implementaron retiro y archivo seguros, sin simular esos efectos.
- Los diseños de Presupuestos incluyen pausa/reanudación, duplicado y deshacer una eliminación. La especificación consultada contempla finalizar y eliminación irreversible confirmada. No se añadieron botones para funciones sin ese contrato. El estado exactamente al 100 % mantiene «Límite alcanzado»; no se inventa un importe excedido cuando el restante es cero.
- El resumen agregado P3 y las predicciones de ritmo no se añadieron como funciones nuevas. Se conserva el historial real por presupuesto. Sumar consumos de presupuestos superpuestos como si fueran gasto global duplicaría operaciones; no se introdujo esa interpretación del mockup.
- D17 propone archivo/reapertura de una obligación con saldo. No existe un estado de archivo equivalente en el contrato consultado. Se ofrecen cierre explícito y protección del historial; una cancelación no se presenta como pago ni se oculta el saldo.
- Se reutiliza el detalle real de Movimientos para D15–D16; no se creó una segunda implementación de la anulación financiera. La edición financiera de un pago especializado permanece bloqueada.
- No se verificó sincronización remota de los registros de prueba, entrega de notificaciones del sistema ni todos los dispositivos/orientaciones. No hubo cambios de base de datos remota y no se ejecutó pgTAP. Las capturas y pruebas de comportamiento no equivalen a homologación píxel a píxel de todos los artboards.

## Ejecuciones reproducibles

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:testDebugUnitTest --tests 'com.kipu.app.feature.budgets.*' --tests 'com.kipu.app.feature.goals.*' --tests 'com.kipu.app.feature.debts.*' --tests 'com.kipu.app.navigation.RootTabMotionTest'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.kipu.app.feature.movements.presentation.MovementHistoryViewModelTest'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.kipu.app.feature.budgets.presentation.BudgetListViewModelTest'
.\gradlew.bat :app:lintDebug
```

La instrumentación se ejecutó con `adb shell am instrument -w -r -e class <clases o clase#caso> com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`. Se comprobó el resultado `OK` y los códigos de cada caso: el código de salida del proceso ADB por sí solo no acredita que las pruebas hayan pasado. Los conjuntos cubrieron navegación, contenedor retenido, pantallas de Presupuestos/Metas/Deudas, aportes/liberaciones, edición/cierre/pago, Room, detalles/anulación de movimientos y evidencia visual. Los fallos de expectativas de disposición, alcance de nodos y fixture incompleto se corrigieron y sus casos se volvieron a ejecutar; no se contabilizan como aprobados sus intentos fallidos.

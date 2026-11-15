# Revisión PR #100 — UX de Dinero: escala de texto y TEA

- **PR:** [#100](https://github.com/Lume03/Kipu/pull/100)
- **Repositorio:** `Lume03/Kipu`
- **Autor / asignado:** LeonidasGarcia
- **Fecha de revisión:** 2026-11-15
- **Rama:** `ux/dinero` → `main`
- **HEAD remoto al revisar:** `da28d994`; este informe incluye además cambios locales aún no enviados.
- **Estado del PR al revisar:** abierto, borrador

## Dictamen breve

**Resultado:** Inconcluso

Los defectos confirmados de legibilidad del detalle de tarjeta, del aviso de saldo al archivar y del estado de TEA sin valor personal están corregidos en los escenarios comprobados. El build y pruebas unitarias focalizadas pasan; 23 pruebas instrumentadas previas del detalle también pasaron. La revisión UI/UX completa de Dinero no queda cerrada: varios flujos/estados del inventario de #95 no son alcanzables con el fixture permitido y la barra inferior compartida sigue apretada a escala grande.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#95](https://github.com/Lume03/Kipu/issues/95) | Confirmado | Inventario verificado en dispositivo; defectos de severidad alta resueltos o justificados; capturas por cambio; cambios limitados al módulo. |

- **Issues no encontrados o relación incierta:** Ninguno.
- **Fuente de autoridad usada:** issue #95 y [sistema visual vigente](../../../../DESIGN.md). El issue excluye cambios a la barra raíz y componentes compartidos.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| #95 - Inventario de flujos completo y verificado en dispositivo | Parcial | Se recorrieron dashboard, resumen/ocultar importes, calendario de entrada, cuentas y billeteras, tarjeta de crédito, detalle/edición, corrección de saldo, confirmación de archivo (sin confirmar), compra/simulador (sin guardar), cuotas/movimientos vacíos, catálogo y su error offline, tema y selectores de banco/emisor. Siguen sin verificarse todos los formularios, pagos, límites Free, reactivación, estados de error por sección, animaciones/reduced motion y recorridos accesibles con TalkBack. |
| #95 — Hallazgos de alta severidad resueltos o justificados | Parcial | Se corrigió el desbordamiento del aviso de saldo al archivar a 200 %/oscuro; se conservan como fuera de alcance los problemas de navegación inferior compartida. No es posible declarar clasificados todos los flujos no accesibles. |
| #95 — Capturas antes/después por cambio | Parcial | Comparativas sanitizadas disponibles para detalle, acciones, catálogo y confirmación de archivo. No hay captura comparativa de cada estado del inventario. |
| #95 — Sin archivos fuera del reparto del módulo | Cumple para código de Dinero | Los cambios de app se limitan a archivos expresamente permitidos del dashboard, detalle y catálogo. No se tocó navegación raíz ni UI compartida; el informe/evidencia QA permanece en `docs/qa/`. |

## Cambios realizados

- El detalle de tarjeta adapta acciones largas y encabezado de movimientos a escala de fuente grande; permite que el texto ocupe varias líneas sin truncarse.
- El catálogo apila la línea de crédito y el estado de TEA personal a escala grande para evitar colisiones.
- La advertencia de saldo pendiente en la confirmación de archivo se apila desde 1.3×; el importe deja de partirse en una columna estrecha.
- El dashboard adapta encabezado, hero, acceso al calendario y filas de cuenta a fuente ampliada/pantalla compacta. Los controles inertes de compartir/ayuda del catálogo se eliminaron.

## Hallazgos y mejoras

### Importantes / pendientes

- **Confirmación de archivo, 200 % y oscuro (alto, corregido):** el importe se partía carácter por carácter; se apiló el aviso y se verificó manualmente sin confirmar el archivado. Evidencia: [comparativa sanitizada](evidence/archive-warning-200-comparison.png).
- **Barra inferior compartida (pendiente de integrador):** las etiquetas se amontonan a escala grande; no se modificó por exclusión expresa de #95.
- No se encontraron otros defectos altos confirmados en los escenarios alcanzados; esto no cubre los estados no disponibles.

### Menores / sugerencias

- La barra inferior amontonada a 200 % permanece fuera del alcance de Dinero; el issue #95 la asigna al integrador.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `:app:assembleDebug` | Pasa | Ejecutado localmente después de los cambios actuales, con `ANDROID_HOME` apuntando al SDK instalado. `local.properties` mantiene una ruta SDK inválida y emite una advertencia. |
| `:app:testDebugUnitTest --tests ...DashboardAccountGroupingTest --tests ...RateCatalogContextTest` | Pasa | Dos pruebas focalizadas, ejecutadas el 2026-11-15. La primera invocación agotó el timeout durante la inicialización; el reintento finalizó verde. |
| `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest` | Pasa | Pixel 8 AVD, Android 16/API 36; 23 pruebas, 0 fallas. |
| `DashboardEmptyStateTest`, `UnifiedInstrumentFormScreenTest` y `CreditAccessibilitySemanticsTest` | Pasan | Fixtures de Compose; 7/7, 2/2 y 4/4 pruebas, respectivamente. Verifican estados vacíos, presentación del formulario y semántica accesible; no sustituyen el recorrido de producción con sesión real. |
| `DashboardRedesignStatesTest` | Parcial | 7 de 8 casos terminan sin fallas de aserción; `h4ArchivedInstrumentsExpand` falla durante el cierre de `ActivityScenario` (se queda en `PAUSED`, no llega a `DESTROYED`). No se cuenta como suite verde ni como defecto visual confirmado. |
| Revisión manual a escala de fuente 200 % | Parcial | Detalle/acciones, encabezado de movimientos, estado vacío de cuotas, catálogo y aviso de archivo se revisaron en oscuro. También se comprobó dashboard en tamaño compacto; la barra inferior compartida sigue congestionada. |
| Recorrido autenticado con sesión dummy (15-nov-2026) | Parcial, sin persistir datos | Se recorrieron los flujos enumerados arriba; se usó importe ficticio temporal para abrir el simulador y se salió sin confirmar. Se activó/desactivó modo avión para comprobar el mensaje de tasas sin conexión; conectividad restaurada. No se guardaron formularios ni se confirmó compra/pago/archivo. Sin tarjeta de débito ni instrumento archivado en el fixture; deuda de tarjeta en cero impide abrir pago. No se alcanzó el límite Free ni el diálogo de cupo. |
| `git diff --check` | Pasa | Sin errores de whitespace. |

## Revisión visual

- **¿Hay cambios visuales?** Sí.
- **Pantallas y escenarios revisados:** dashboard, cuenta/billetera y tarjeta, edición/corrección/archivo, compra y simulador, cuotas/movimientos, catálogo y error offline; tema oscuro, escala 200 % y tamaño compacto.
- **Evidencia:** [detalle](evidence/card-detail-200-comparison.png), [acciones](evidence/card-actions-200-comparison.png), [catálogo](evidence/rate-catalog-200-comparison.png), [aviso de archivo](evidence/archive-warning-200-comparison.png) — emulador Pixel 8, Android 16/API 36. Las tres primeras comparativas preexistentes son de tema claro; el aviso se capturó en oscuro.
- **Privacidad:** se ocultaron saldos, línea de crédito y datos/números de tarjeta del fixture en las capturas versionadas. Los originales permanecen fuera del repositorio.

## Pendientes / límites de la revisión

- Completar revisión de formularios completos, pago cuando haya deuda/instrumento permitido, variantes de débito, reactivación, cuota Free, cupos, errores parciales, TalkBack y reduced motion. No crear ni archivar instrumentos para fabricar precondiciones.
- Inspeccionar estados sin conexión de formularios, simulador y pago; solo se alcanzó el catálogo de tasas. Modo avión se desactivó y Wi-Fi volvió a conectar.
- Integrador: revisar barra inferior compartida a 200 %; queda fuera de esta rama.
- La sesión y el fixture dummy permanecieron intactos; tamaño del emulador y escala restaurados a 1080×2400, 1.0×, modo avión desactivado. Tema oscuro conservado, consistente con el estado del dispositivo observado al inicio.
- El informe no implica aprobación formal ni cierre del issue.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios publicados:** [Avance en issue #95](https://github.com/Lume03/Kipu/issues/95#issuecomment-6889721991).
- **Solicitudes de cambio:** No.

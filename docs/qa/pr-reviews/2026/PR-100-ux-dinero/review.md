# Revisión PR #100 — UX de Dinero: escala de texto y TEA

- **PR:** [#100](https://github.com/Lume03/Kipu/pull/100)
- **Repositorio:** `Lume03/Kipu`
- **Autor / asignado:** LeonidasGarcia
- **Fecha de revisión:** 2026-11-15
- **Rama:** `ux/dinero` → `main`
- **HEAD remoto previo a esta actualización:** `f57a43b1`; el informe incluye los cambios locales validados que se publicarán en la rama.
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
| #95 - Inventario de flujos completo y verificado en dispositivo | Parcial | Además de dashboard, detalle/edición, corrección y archivo cancelado, se recorrieron selector y formulario de cuenta bancaria, selector de emisor/producto y configuración de tarjeta, y simulador offline con importe ficticio sin confirmar. Se verificaron tema claro/oscuro, tamaño compacto, texto 200 % y giro de tarjeta con animaciones del sistema a 0. El dashboard conserva su contenido al desconectarse y no muestra aviso offline explícito; tampoco se vio error de sección en esa condición. TalkBack se habilitó temporalmente para inspeccionar el árbol de dashboard, detalle y formulario: se exponen acciones, radios y campos editables, pero no se certificó la verbalización/foco de cada elemento. No están disponibles fixture de débito/archivados ni deuda para pago. El selector informa plan Free 3/4, así que no se alcanza el límite/cupos. Reactivación y algunos estados parciales/error siguen sin verificarse. |
| #95 — Hallazgos de alta severidad resueltos o justificados | Parcial | Se corrigió el desbordamiento del aviso de saldo al archivar a 200 %/oscuro; se conservan como fuera de alcance los problemas de navegación inferior compartida. No es posible declarar clasificados todos los flujos no accesibles. |
| #95 — Capturas antes/después por cambio | Parcial | Se añadieron comparativas sanitizadas de dashboard compacto/200 % y cabecera del formulario bancario; las existentes cubren detalle, acciones, catálogo y confirmación de archivo. No hay captura comparativa de cada estado del inventario. |
| #95 — Sin archivos fuera del reparto del módulo | Cumple para código de Dinero | Los cambios de app se limitan a archivos expresamente permitidos del dashboard, detalle, catálogo y componentes del formulario de alta. No se tocó navegación raíz ni UI compartida; el informe/evidencia QA permanece en `docs/qa/`. |

## Cambios realizados

- El detalle de tarjeta adapta acciones largas y encabezado de movimientos a escala de fuente grande; permite que el texto ocupe varias líneas sin truncarse.
- El catálogo apila la línea de crédito y el estado de TEA personal a escala grande para evitar colisiones.
- La advertencia de saldo pendiente en la confirmación de archivo se apila desde 1.3×; el importe deja de partirse en una columna estrecha.
- El dashboard adapta encabezado, hero, acceso al calendario y filas de cuenta a fuente ampliada/pantalla compacta. Los controles inertes de compartir/ayuda del catálogo se eliminaron.
- La cabecera del alta de cuenta se reorganiza en ancho compacto/200 %: Atrás ocupa su propia fila y el título deja de competir por ancho; se elimina el prefijo redundante “Agregar”. En la barra inferior se oculta el estado redundante de éxito para dejar más espacio al botón principal; los errores permanecen visibles.

## Hallazgos y mejoras

### Importantes / pendientes

- **Confirmación de archivo, 200 % y oscuro (alto, corregido):** el importe se partía carácter por carácter; se apiló el aviso y se verificó manualmente sin confirmar el archivado. Evidencia: [comparativa sanitizada](evidence/archive-warning-200-comparison.png).
- **Barra inferior compartida (pendiente de integrador):** las etiquetas se amontonan a escala grande; no se modificó por exclusión expresa de #95.
- **Alta de cuenta bancaria, 200 % y ancho compacto (medio, corregido):** Atrás y “Agregar cuenta bancaria” competían en una fila y el título ocupaba tres líneas. Se separó Atrás, se conserva el título “Cuenta bancaria” y se retira el texto de estado redundante del footer en este breakpoint. Se comprobó visualmente y se activó Atrás sin guardar. Evidencia: [comparativa sanitizada](evidence/account-form-compact-200-comparison.png).
- **Desconexión sin indicación (candidato medio, pendiente de decisión):** offline, el dashboard conserva contenido cacheado y el formulario/simulador siguen utilizables sin mensaje explícito de estado de red. Registrar para que producto confirme si el contenido potencialmente desactualizado debe señalarse; no se implementó sin requisito aprobado.
- No se encontraron otros defectos altos confirmados en los escenarios alcanzados; esto no cubre los estados no disponibles.

### Menores / sugerencias

- La barra inferior amontonada a 200 % permanece fuera del alcance de Dinero; el issue #95 la asigna al integrador.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `:app:assembleDebug` | Pasa | Ejecutado localmente después de los cambios actuales, con `ANDROID_HOME` apuntando al SDK instalado. `local.properties` mantiene una ruta SDK inválida y emite una advertencia. |
| `:app:testDebugUnitTest --tests ...DashboardAccountGroupingTest --tests ...RateCatalogContextTest` | Pasa | Dos pruebas focalizadas, ejecutadas el 2026-11-15 junto con `assembleDebug`; la ruta `sdk.dir` de `local.properties` emite advertencia, pero `ANDROID_HOME` apunta al SDK correcto. |
| `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest` | Pasa | Pixel 8 AVD, Android 16/API 36; 23 pruebas, 0 fallas. |
| `DashboardEmptyStateTest`, `UnifiedInstrumentFormScreenTest` y `CreditAccessibilitySemanticsTest` | Pasan | Fixtures de Compose; 7/7, 2/2 y 4/4 pruebas, respectivamente. Verifican estados vacíos, presentación del formulario y semántica accesible; no sustituyen el recorrido de producción con sesión real. |
| `DashboardRedesignStatesTest` | Parcial | 7 de 8 casos terminan sin fallas de aserción; `h4ArchivedInstrumentsExpand` falla durante el cierre de `ActivityScenario` (se queda en `PAUSED`, no llega a `DESTROYED`). No se cuenta como suite verde ni como defecto visual confirmado. |
| Revisión manual a escala de fuente 200 % | Parcial | Detalle/acciones, encabezado de movimientos, vacío de cuotas, catálogo, aviso de archivo, dashboard y formulario bancario se revisaron en oscuro; dashboard también se comprobó en claro. La barra inferior compartida sigue congestionada. |
| Recorrido autenticado con sesión dummy (15-nov-2026) | Parcial, sin persistir datos | Se recorrieron los flujos enumerados arriba. Dashboard, formulario de tarjeta y simulador abrieron sin conexión sin aviso offline explícito; la vista previa se calculó y se salió por “Rechazar”. Giro de tarjeta inspeccionado con escalas de animación a 0 y escalas restauradas a 1.0. TalkBack se habilitó temporalmente y el árbol expuso las etiquetas visibles, acciones y campos/radios; no se certificó toda la verbalización ni el orden de foco. Selector y formulario bancario/card fueron recorridos sin guardar; Atrás respondió en alta de cuenta. El selector indica plan Free 3/4, sin llegar a cuota ni cupos excedidos. No se guardaron formularios ni se confirmó compra/pago/archivo. Sin tarjeta de débito ni instrumento archivado; deuda de tarjeta en cero impide abrir pago. |
| `git diff --check` | Pasa | Sin errores de whitespace. |

## Revisión visual

- **¿Hay cambios visuales?** Sí.
- **Pantallas y escenarios revisados:** dashboard (también offline), cuenta/billetera y tarjeta, edición/corrección/archivo, compra/simulador, cuotas/movimientos, catálogo/error offline, selectores y formularios de alta; tema claro/oscuro, escala 200 %, tamaño compacto, reduced motion e inspección parcial del árbol TalkBack.
- **Evidencia:** [dashboard compacto](evidence/dashboard-compact-200-comparison.png), [alta de cuenta compacta](evidence/account-form-compact-200-comparison.png), [detalle](evidence/card-detail-200-comparison.png), [acciones](evidence/card-actions-200-comparison.png), [catálogo](evidence/rate-catalog-200-comparison.png), [aviso de archivo](evidence/archive-warning-200-comparison.png) — emulador Pixel 8, Android 16/API 36. Las comparativas originales de detalle/acciones/catálogo son de tema claro; aviso de archivo y alta se capturaron en oscuro.
- **Privacidad:** se ocultaron saldos, línea de crédito y datos/números de tarjeta del fixture en las capturas versionadas. Los originales permanecen fuera del repositorio.

## Pendientes / límites de la revisión

- Completar revisión de estados de pago cuando haya deuda/fixture permitido, variantes de débito, reactivación, cuota Free/cupos, errores parciales y offline del pago. Para TalkBack falta una comprobación completa de verbalización y orden de foco. No crear ni archivar instrumentos para fabricar precondiciones.
- La desconexión se probó en dashboard, catálogo, formulario de tarjeta y simulador; el dashboard conserva contenido en caché y no presenta aviso explícito, igual que formulario/simulador. Modo avión se desactivó y Wi-Fi volvió a conectar.
- Integrador: revisar barra inferior compartida a 200 %; queda fuera de esta rama.
- La sesión y el fixture dummy permanecieron intactos; tamaño del emulador y escala restaurados a 1080×2400, 1.0×, modo avión desactivado. Tema oscuro conservado, consistente con el estado del dispositivo observado al inicio.
- El informe no implica aprobación formal ni cierre del issue.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios publicados:** [Avance final de verificación en issue #95](https://github.com/Lume03/Kipu/issues/95#issuecomment-6897279538).
- **Solicitudes de cambio:** No.

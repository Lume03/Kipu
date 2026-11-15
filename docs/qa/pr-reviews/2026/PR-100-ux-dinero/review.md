# Revisión PR #100 — UX de Dinero: escala de texto y TEA

- **PR:** [#100](https://github.com/Lume03/Kipu/pull/100)
- **Repositorio:** `Lume03/Kipu`
- **Autor / asignado:** LeonidasGarcia
- **Fecha de revisión:** 2026-11-15
- **Rama:** `ux/dinero` → `main`
- **HEAD de los cambios de app revisado:** `4f73c3c6`; este informe registra la QA posterior ejecutada sobre ese build.
- **Estado del PR al revisar:** abierto, listo para revisión de código (no borrador)

## Dictamen breve

**Resultado:** Inconcluso

Los defectos confirmados de legibilidad del detalle de tarjeta, del aviso de saldo al archivar y del estado de TEA sin valor personal están corregidos en los escenarios comprobados. Se confirmó Plan Premium en Ajustes y se permitió crear y archivar un quinto grupo QA sin aviso de límite; no se degradó el plan. Los estados focalizados H6 (patrimonio parcial/no disponible), H7 (carga) y H8 (error con reintento) pasaron individualmente. La auditoría sigue inconclusa: TalkBack y TTS se activaron, pero no se certificó de forma fiable toda la verbalización y secuencia de foco; la barra inferior compartida sigue apretada a escala grande. #95 permanece abierto.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#95](https://github.com/Lume03/Kipu/issues/95) | Confirmado | Inventario verificado en dispositivo; defectos de severidad alta resueltos o justificados; capturas por cambio; cambios limitados al módulo. |

- **Issues no encontrados o relación incierta:** Ninguno.
- **Fuente de autoridad usada:** issue #95 y [sistema visual vigente](../../../../DESIGN.md). El issue excluye cambios a la barra raíz y componentes compartidos.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| #95 - Inventario de flujos completo y verificado en dispositivo | Parcial | Además de dashboard, detalle/edición/corrección, se guardaron la cuenta y tarjeta de débito QA; ambas se archivaron y reactivaron. Se registraron y pagaron consumos ficticios `QA95_*`, incluido uno durante modo avión verificado; la deuda volvió a cero y el pago offline no mostró aviso de conexión. También se crearon una billetera Plin y efectivo QA con saldo cero, y se archivaron tras probar sus formularios. En Ajustes se confirmó Plan Premium; se creó y archivó `QA95_PREMIUM_EFECTIVO` como quinto grupo, sin aviso de cuota. “Gestionar cupos” no aplica en este estado: el dashboard solo lo muestra cuando se excede la cuota Free (`maxFreeQuota > 0`), y Premium devuelve cuota Free cero; no se degradó el plan para fabricar el caso. Siguen pendientes una comprobación manual fiable de verbalización/orden de foco TalkBack y estados parciales/error en la sesión real cuando no los reproduce el caché. |
| #95 — Hallazgos de alta severidad resueltos o justificados | Cumple en los recorridos alcanzados | Se corrigió el desbordamiento del aviso de saldo al archivar a 200 %/oscuro; se conservan como fuera de alcance los problemas de navegación inferior compartida. No se confirma otro defecto alto en los nuevos recorridos. Plan Premium quedó confirmado en Ajustes y una quinta alta QA se permitió sin aviso de límite. No se cambió ni degradó el plan. |
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
| `DashboardRedesignStatesTest` | Parcial | `h6UnavailableNetWorthIsNeverZero`, `h7LoadingShowsSkeletonWithoutFigures` y `h8ErrorOffersRetryAndNoFigures` se ejecutaron de forma focalizada y pasaron (1/1 cada uno). La suite completa sigue parcial: en la ejecución previa, 7 de 8 casos no fallaron por aserción y `h4ArchivedInstrumentsExpand` falla al cerrar `ActivityScenario` (queda en `PAUSED`, no llega a `DESTROYED`). No se cuenta como suite verde ni como defecto visual confirmado. |
| Revisión manual a escala de fuente 200 % | Parcial | Detalle/acciones, encabezado de movimientos, vacío de cuotas, catálogo, aviso de archivo, dashboard y formulario bancario se revisaron en oscuro; dashboard también se comprobó en claro. La barra inferior compartida sigue congestionada. |
| Recorrido autenticado con sesión dummy (15-nov-2026) | Parcial, con datos de prueba autorizados | La cuenta y tarjeta de débito `QA95_DEBITO` se archivaron/reactivaron y quedaron activas. Se registraron y pagaron consumos ficticios `QA95_*` dentro de la app, incluido uno con modo avión confirmado (`cmd connectivity airplane-mode=enabled` y sin redes conectadas); la deuda quedó en cero. No hubo operación con agente externo; los pagos redujeron el saldo ficticio de la cuenta QA. En el pago offline no apareció aviso de conexión. Se crearon `QA95_BILLETERA` (Plin, saldo cero) y `QA95_EFECTIVO` (saldo cero); ambas pruebas quedaron archivadas. Ajustes mostró “Plan Premium”; se creó `QA95_PREMIUM_EFECTIVO` como quinto grupo y se archivó después, sin bloqueo ni degradación. La gestión de cupos no aparece bajo Premium porque la UI solo la presenta al superar una cuota Free positiva. El flujo de archivo/reactivación se completó al archivar también la tarjeta de débito vinculada. TalkBack se habilitó temporalmente; se verificaron servicio activo, inicialización de TTS y solicitudes de síntesis, y la jerarquía expone etiquetas/acciones/campos. Sin embargo, la inyección táctil no permitió certificar cada texto hablado ni el orden completo de foco, por lo que esa parte queda inconclusa. TalkBack se desactivó y se restauraron sus permisos, tamaño de fuente, modo avión, Wi-Fi/datos y `show_ime_with_hard_keyboard`. |
| `git diff --check` | Pasa | Sin errores de whitespace. |

## Revisión visual

- **¿Hay cambios visuales?** Sí.
- **Pantallas y escenarios revisados:** dashboard (también offline), cuenta/billetera y tarjeta, edición/corrección/archivo, compra/simulador, cuotas/movimientos, catálogo/error offline, selectores y formularios de alta; tema claro/oscuro, escala 200 %, tamaño compacto, reduced motion y TalkBack activo con revisión parcial de foco/TTS (sin certificar secuencia completa).
- **Evidencia:** [dashboard compacto](evidence/dashboard-compact-200-comparison.png), [alta de cuenta compacta](evidence/account-form-compact-200-comparison.png), [detalle](evidence/card-detail-200-comparison.png), [acciones](evidence/card-actions-200-comparison.png), [catálogo](evidence/rate-catalog-200-comparison.png), [aviso de archivo](evidence/archive-warning-200-comparison.png) — emulador Pixel 8, Android 16/API 36. Las comparativas originales de detalle/acciones/catálogo son de tema claro; aviso de archivo y alta se capturaron en oscuro.
- **Privacidad:** se ocultaron saldos, línea de crédito y datos/números de tarjeta del fixture en las capturas versionadas. Los originales permanecen fuera del repositorio.

## Pendientes / límites de la revisión

- “Gestionar cupos” no es alcanzable con Premium: la UI lo reserva para exceso sobre una cuota Free positiva. No degradar el plan para fabricar el escenario.
- Para TalkBack falta una comprobación manual fiable de verbalización exacta y orden completo de foco. El servicio/TTS sí se inició; los tests focalizados de estados H6–H8 pasaron, pero la sesión cacheada no reproduce todos esos errores parciales en vivo.
- La desconexión se probó en dashboard, catálogo, formularios, simulador y pago. El dashboard conserva contenido en caché; formulario, simulador y pago no presentan aviso explícito de conexión. Modo avión se desactivó y Wi-Fi volvió a conectar.
- Integrador: revisar barra inferior compartida a 200 %; queda fuera de esta rama.
- Los datos `QA95_*` de prueba autorizados permanecen en la cuenta dummy: cuenta/débito activos; billetera Plin y efectivo archivados; consumo ficticio pagado y deuda de tarjeta en cero; el quinto grupo `QA95_PREMIUM_EFECTIVO` quedó archivado. El saldo de la cuenta QA refleja el pago de prueba. La app puede sincronizar estos cambios con el proyecto configurado en el APK.
- TalkBack quedó desactivado y el permiso de notificaciones temporal para el servicio fue revocado; escala de fuente 1.0×, modo avión desactivado y `show_ime_with_hard_keyboard=0`. Resolución 1080×2400; tema oscuro conservado, consistente con el estado inicial.
- El informe no implica aprobación formal ni cierre del issue. PR #100 está listo para revisión de código con este dictamen inconcluso; #95 sigue abierto hasta resolver los pendientes de aceptación.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios publicados:** [Ampliación del recorrido con sesión dummy](https://github.com/Lume03/Kipu/issues/95#issuecomment-6898202625); [avance anterior de TalkBack y desconexión](https://github.com/Lume03/Kipu/issues/95#issuecomment-6897279538).
- **Solicitudes de cambio:** No.

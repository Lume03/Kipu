# Revisión PR #100 — UX de Dinero: escala de texto y TEA

- **PR:** [#100](https://github.com/Lume03/Kipu/pull/100)
- **Repositorio:** `Lume03/Kipu`
- **Autor / asignado:** LeonidasGarcia
- **Fecha de revisión:** 2026-11-15; seguimiento: 2026-11-16
- **Rama:** `ux/dinero` → `main`
- **HEAD de partida:** `08478d5a`; seguimiento de los seis comentarios de Lume03 en el árbol de trabajo posterior a ese HEAD.
- **Estado del PR:** abierto, con cambios solicitados; la nueva revisión aún no se ha solicitado.

## Dictamen breve

**Resultado: Inconcluso.**

En el seguimiento se atendieron en código los seis comentarios de Lume03 sobre layout de archivo, breakpoint común, `FlowRow`, título compacto explícito, contenido de acciones único y tokens de color. La prueba Compose aislada del aviso de archivo pasó a 1.0× y 2.0× con un importe ficticio. El flujo real de archivo en la app no se volvió a recorrer: el AVD actual no tiene sesión iniciada. La evidencia de fixture no sustituye esa verificación. La auditoría de #95 sigue parcial: TalkBack no se confirmó por escucha, el escenario de cuotas Free no es alcanzable con Premium, los candidatos offline esperan decisión de producto y la barra inferior compartida sigue fuera de alcance. #95 permanece abierto.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#95](https://github.com/Lume03/Kipu/issues/95) | Confirmado | Inventario verificado en dispositivo; defectos de severidad alta resueltos o justificados; capturas por cambio; cambios limitados al módulo. |

- **Issues no encontrados o relación incierta:** Ninguno.
- **Fuente de autoridad usada:** issue #95 y [sistema visual vigente](../../../../DESIGN.md). El issue excluye cambios a la barra raíz y componentes compartidos.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| #95 - Inventario de flujos completo y verificado en dispositivo | Parcial | La auditoría anterior cubrió dashboard, detalle/edición/corrección, altas, archivo/reactivación y operaciones ficticias `QA95_*`, incluido un pago durante modo avión verificado. En esa sesión se confirmó Plan Premium y se creó/archivó un quinto grupo sin aviso de cuota. “Gestionar cupos” solo aparece con exceso de una cuota Free positiva (`maxFreeQuota > 0`); no se degradó el plan para fabricar ese escenario. TalkBack movió visualmente el foco y el logcat registró actividad TTS, pero no se confirmó por escucha el texto ni se completó el recorrido. H6–H8 tienen pruebas focalizadas, no una verificación de todos los estados en la sesión real. En el seguimiento actual el AVD no tiene sesión y no se repitió el flujo real de archivo. |
| #95 — Hallazgos de alta severidad resueltos o justificados | Parcial | El aviso de archivo se corrigió en código y la prueba visual aislada confirmó que no se solapa a 1.0×/2.0×; la repetición en el flujo real queda pendiente. La navegación inferior compartida está excluida por el issue. No se degradó Premium para forzar el escenario de cuotas. |
| #95 — Capturas antes/después por cambio | Parcial | Se conservan las seis comparativas adjuntas existentes y se agregan dos capturas del fixture aislado del aviso de archivo a 1.0×/2.0×. Estas capturas nuevas no representan el flujo autenticado real. No hay comparativa de cada estado del inventario. |
| #95 — Sin archivos fuera del reparto del módulo | Cumple para código de Dinero | Los cambios de app se limitan a archivos expresamente permitidos del dashboard, detalle, catálogo y componentes del formulario de alta. No se tocó navegación raíz ni UI compartida; el informe/evidencia QA permanece en `docs/qa/`. |

## Cambios realizados

- El detalle de tarjeta adapta acciones largas y encabezado de movimientos a escala de fuente grande; permite que el texto ocupe varias líneas sin truncarse.
- El catálogo apila la línea de crédito y el estado de TEA personal a escala grande para evitar colisiones.
- La advertencia de saldo pendiente en la confirmación de archivo usa una columna única para todas las escalas; el importe no queda en una columna estrecha.
- El dashboard adapta encabezado, hero, acceso al calendario y filas de cuenta a fuente ampliada/pantalla compacta. Los controles inertes de compartir/ayuda del catálogo se eliminaron.
- La cabecera del alta de cuenta se reorganiza en ancho compacto/200 %: Atrás ocupa su propia fila y el título deja de competir por ancho; se elimina el prefijo redundante “Agregar”. En la barra inferior se oculta el estado redundante de éxito para dejar más espacio al botón principal; los errores permanecen visibles.

## Seguimiento de los comentarios de Lume03 — 2026-11-16

- **Aviso de archivo:** el contenido ahora se organiza en columna con etiqueta e importe en una fila y la sugerencia debajo; no depende de una rama por escala. Prueba Compose aislada con saldo ficticio pasó en Pixel 8 / Android 16 a 1.0× y 2.0×. Las capturas nuevas son del fixture, no del diálogo real de la app.
- **Breakpoint:** se unificó en `isCompactAccountsLayout()` con `fontScale >= 1.3f || screenWidthDp < 360` y comentario documentando ambos umbrales.
- **Filas de cuenta:** `FlowRow` mantiene un solo contenido y permite que el importe baje cuando no cabe.
- **Título compacto:** el título breve se pasa explícitamente desde el llamador; no se deriva recortando texto visible.
- **Acciones de tarjeta:** se comparte una sola definición de los controles y cambia el contenedor para adaptarse; incluye acciones, periodo y encabezado de movimientos.
- **Colores y montos:** colores funcionales migrados a tokens; se mantienen únicamente colores de marca/hero respaldados por `docs/DESIGN.md`. Se reutiliza `MoneyText` para presentación monetaria.
- **Límite de verificación:** el AVD actual está sin sesión. No se autenticó, no se realizaron operaciones financieras y no se recorrió el flujo real de archivo después de estas correcciones. La prueba aislada no se incorpora al commit; sus PNG se conservan como evidencia.

## Hallazgos y mejoras

### Importantes / pendientes

- **Confirmación de archivo, 200 % y oscuro (hallazgo alto):** la auditoría anterior registró que el importe se partía carácter por carácter y una comparativa antes/después. La corrección actual pasa la prueba aislada a 1.0×/2.0×; queda pendiente repetirla en el flujo real, sin confirmar el archivado.
- **Barra inferior compartida (pendiente de integrador):** las etiquetas se amontonan a escala grande; no se modificó por exclusión expresa de #95.
- **Alta de cuenta bancaria, 200 % y ancho compacto (medio, corregido):** Atrás y “Agregar cuenta bancaria” competían en una fila y el título ocupaba tres líneas. Se separó Atrás, se conserva el título “Cuenta bancaria” y se retira el texto de estado redundante del footer en este breakpoint. Se comprobó visualmente y se activó Atrás sin guardar. Evidencia: [comparativa sanitizada antes/después](https://github.com/user-attachments/assets/e2f326d9-c661-4e2c-b520-a929be338bf0).
- **Desconexión sin indicación (candidato medio, pendiente de decisión):** offline, el dashboard conserva contenido cacheado y el formulario/simulador siguen utilizables sin mensaje explícito de estado de red. Registrar para que producto confirme si el contenido potencialmente desactualizado debe señalarse; no se implementó sin requisito aprobado.
- No se encontraron otros defectos altos confirmados en los escenarios alcanzados; esto no cubre los estados no disponibles.

### Menores / sugerencias

- La barra inferior amontonada a 200 % permanece fuera del alcance de Dinero; el issue #95 la asigna al integrador.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `:app:assembleDebug` y `:app:compileDebugKotlin` | Pasa | Ejecutados después de las correcciones de #100 con `ANDROID_HOME` apuntando al SDK instalado. `local.properties` conserva una ruta SDK inválida y emite advertencia. |
| `:app:testDebugUnitTest --tests ...DashboardAccountGroupingTest --tests ...RateCatalogContextTest` | Pasa | Dos pruebas focalizadas, ejecutadas junto con `assembleDebug`; no se repitió la suite legacy completa. |
| `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest` | Pasa | Pixel 8 AVD, Android 16/API 36; 23 pruebas, 0 fallas. |
| `DashboardEmptyStateTest`, `UnifiedInstrumentFormScreenTest` y `CreditAccessibilitySemanticsTest` | Pasan | Fixtures de Compose; 7/7, 2/2 y 4/4 pruebas, respectivamente. Verifican estados vacíos, presentación del formulario y semántica accesible; no sustituyen el recorrido de producción con sesión real. |
| `DashboardRedesignStatesTest` | Parcial | `h6UnavailableNetWorthIsNeverZero`, `h7LoadingShowsSkeletonWithoutFigures` y `h8ErrorOffersRetryAndNoFigures` se ejecutaron de forma focalizada y pasaron (1/1 cada uno). La suite completa sigue parcial: en la ejecución previa, 7 de 8 casos no fallaron por aserción y `h4ArchivedInstrumentsExpand` falla al cerrar `ActivityScenario` (queda en `PAUSED`, no llega a `DESTROYED`). No se cuenta como suite verde ni como defecto visual confirmado. |
| Revisión manual a escala de fuente 200 % | Parcial | Detalle/acciones, encabezado de movimientos, vacío de cuotas, catálogo, aviso de archivo, dashboard y formulario bancario se revisaron en oscuro; dashboard también se comprobó en claro. La barra inferior compartida sigue congestionada. |
| Recorrido autenticado con sesión dummy (15-nov-2026) | Parcial, con datos de prueba autorizados | La cuenta y tarjeta de débito `QA95_DEBITO` se archivaron/reactivaron y quedaron activas. Se registraron y pagaron consumos ficticios `QA95_*` dentro de la app, incluido uno con modo avión confirmado (`cmd connectivity airplane-mode=enabled` y sin redes conectadas); la deuda quedó en cero. No hubo operación con agente externo; los pagos redujeron el saldo ficticio de la cuenta QA. En el pago offline no apareció aviso de conexión. Se crearon `QA95_BILLETERA` (Plin, saldo cero) y `QA95_EFECTIVO` (saldo cero); ambas pruebas quedaron archivadas. Ajustes mostró “Plan Premium”; se creó `QA95_PREMIUM_EFECTIVO` como quinto grupo y se archivó después, sin bloqueo ni degradación. La gestión de cupos no aparece bajo Premium porque la UI solo la presenta al superar una cuota Free positiva. El flujo de archivo/reactivación se completó al archivar también la tarjeta de débito vinculada. Tras reinstalar el APK debug, inicié sesión de nuevo con autorización del usuario; el dashboard autenticado y los instrumentos QA volvieron y la sesión se dejó abierta. Durante TalkBack, swipes físicos cambiaron el foco visual de Ajustes a “Mi dinero” y luego a “Dinero real disponible”. El logcat registró foco de audio de accesibilidad y solicitudes de síntesis/despachos al motor `com.google.android.tts` (`en-us-x-iog-lstm-embedded`); no se verificó por escucha la salida audible ni el contenido exacto, por lo que esta comprobación queda parcial y no se marca como verbalización confirmada. No se recorrió toda la secuencia de foco. TalkBack quedó apagado (`accessibility_enabled=0`, servicio habilitado nulo) y `POST_NOTIFICATIONS` del servicio fue revocado (`granted=false`). No se hicieron operaciones financieras durante la recuperación de sesión ni la comprobación de TalkBack. No se reconfirmó la persistencia/sincronización remota de los datos QA tras reinstalar. |
| `git diff --check` | Pasa | Sin errores de whitespace. |
| `ArchiveAccountNotesVisualEvidenceTest` | Pasa, aislada | Pixel 8 AVD / Android 16 API 36, fixture Compose con saldo ficticio; 1/1 a 1.0× y 1/1 a 2.0×. No abre la cuenta ni el diálogo real de archivo. El AVD se restauró a 1.0×. |

## Revisión visual

- **¿Hay cambios visuales?** Sí.
- **Pantallas y escenarios revisados:** la auditoría previa cubrió dashboard (también offline), cuenta/billetera y tarjeta, edición/corrección/archivo, compra/simulador, cuotas/movimientos, catálogo/error offline, selectores y formularios de alta; tema claro/oscuro, escala 200 %, tamaño compacto, reduced motion y TalkBack activo. El seguimiento actual añade una prueba aislada del aviso de archivo a 1.0×/2.0×, no una nueva verificación de producción autenticada. La salida audible de TalkBack no se confirmó.
- **Evidencia adjunta al PR:** [dashboard compacto antes/después](https://github.com/user-attachments/assets/7387a1da-8eb1-4737-bf22-8e18e5654963), [alta de cuenta](https://github.com/user-attachments/assets/e2f326d9-c661-4e2c-b520-a929be338bf0), [detalle de tarjeta](https://github.com/user-attachments/assets/a0ae69c1-5a01-49f8-8e0a-976224168676), [acciones de tarjeta](https://github.com/user-attachments/assets/4b15dcea-2457-484b-822f-09334c2a004d), [catálogo](https://github.com/user-attachments/assets/c56e7b02-7325-4c6e-b8fc-475452c52a70), [aviso de archivo anterior](https://github.com/user-attachments/assets/dd9fbe40-a33e-421e-817b-e447f211d7e3), [fixture de aviso a 1.0×](https://github.com/user-attachments/assets/bfe0a574-8f54-4e15-bbda-8635464fcbef) y [fixture de aviso a 2.0×](https://github.com/user-attachments/assets/381cc06f-00f2-4d82-96d7-1aa190a0a709). Las ocho imágenes están adjuntas al PR; las dos nuevas son evidencia aislada, no sustituyen el recorrido real.
- **Privacidad:** las capturas existentes sanitizan saldos, línea de crédito y datos/números de tarjeta. Las nuevas contienen solo el importe ficticio del fixture Compose. Ninguna imagen se guarda como archivo de evidencia en el repositorio.

## Pendientes / límites de la revisión

- “Gestionar cupos” no es alcanzable con Premium: la UI lo reserva para exceso sobre una cuota Free positiva. No degradar el plan para fabricar el escenario.
- Para TalkBack falta confirmar por escucha la salida audible y la verbalización exacta, además del orden completo de foco. La prueba sí observó avance visual del foco y registró foco de audio de accesibilidad junto con solicitudes y despachos de síntesis al motor TTS; por ello el resultado es parcial, no una inferencia basada solo en etiquetas. Los tests focalizados de estados H6–H8 pasaron, pero la sesión cacheada no reproduce todos esos errores parciales en vivo.
- La desconexión se probó en dashboard, catálogo, formularios, simulador y pago. El dashboard conserva contenido en caché; formulario, simulador y pago no presentan aviso explícito de conexión. Modo avión se desactivó y Wi-Fi volvió a conectar.
- Integrador: revisar barra inferior compartida a 200 %; queda fuera de esta rama.
- La sesión autorizada descrita en la auditoría del 15-nov se considera histórica; tras las pruebas instrumentadas actuales el AVD no tiene sesión iniciada. No se verificó la persistencia/sincronización remota de los datos QA ni se inició una nueva sesión para repetir el flujo de archivo.
- El informe no implica aprobación formal ni cierre del issue. PR #100 permanece abierto; tras publicar las correcciones se solicitará nueva revisión a Lume03. #95 sigue abierto hasta resolver los pendientes de aceptación.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios publicados:** [Ampliación del recorrido con sesión dummy](https://github.com/Lume03/Kipu/issues/95#issuecomment-6898202625); [avance anterior de TalkBack y desconexión](https://github.com/Lume03/Kipu/issues/95#issuecomment-6897279538).
- **Seguimiento de #95 pendiente:** registrar la corrección de los seis comentarios de #100, las dos capturas aisladas y la limitación de sesión actual sin elevar el resultado parcial a aprobación.
- **Solicitudes de cambio / aprobación formal:** no se envía aprobación ni se cierra #95. Se solicitará re-review del PR #100 una vez publicado el nuevo HEAD.

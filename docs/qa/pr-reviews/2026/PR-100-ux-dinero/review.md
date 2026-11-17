# Revisión PR #100 — UX de Dinero: escala de texto y TEA

- **PR:** [#100](https://github.com/Lume03/Kipu/pull/100)
- **Repositorio:** `Lume03/Kipu`
- **Autor / asignado:** LeonidasGarcia
- **Fecha de revisión:** 2026-11-15; seguimientos: 2026-11-16 y 2026-11-17 UTC
- **Rama:** `ux/dinero` → `main`
- **HEAD de esta iteración:** `e17aa122` (cambios de código publicados en `ux/dinero`).
- **Estado del PR:** abierto, con cambios solicitados; se está actualizando descripción y seguimiento de #95.

## Dictamen breve

**Resultado: Inconcluso.**

Esta iteración corrige los cuatro comentarios nuevos de Lume03: el contenido del `AlertDialog` de archivo puede desplazarse; el encabezado comparativo de TEA se adapta; la membresía anual respeta “ocultar importes”; y `AddFormTopBar` declara una sola vez Atrás y el título. Pasaron `assembleDebug`, 26 pruebas unitarias focalizadas, 24 pruebas de `AccountDetailScreenTest` y 3 pruebas de `RateCatalogResponsiveLayoutTest`. Los fixtures usan saldos, tarjeta y billetera ficticios; no se inició sesión ni se archivó un instrumento ni se ejecutó una operación financiera. Esto no reemplaza el recorrido de producción autenticado. La auditoría de Fase 0 queda registrada abajo con decisión, evidencia y responsable por indicio; varios asuntos continúan pendientes o parcialmente verificados. PR #100 y #95 siguen abiertos, sin aprobación ni merge.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#95](https://github.com/Lume03/Kipu/issues/95) | Confirmado | Inventario verificado en dispositivo; defectos de severidad alta resueltos o justificados; capturas por cambio; cambios limitados al módulo. |

- **Issues no encontrados o relación incierta:** Ninguno.
- **Fuente de autoridad usada:** issue #95 y [sistema visual vigente](../../../../DESIGN.md). El issue excluye cambios a la barra raíz y componentes compartidos.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| #95 - Inventario de flujos completo y verificado en dispositivo | Parcial | Se conserva la auditoría histórica del 15-nov y se añadió una matriz explícita de todos los indicios de Fase 0. No se reejecutaron todos los flujos en la app; cuotas Free, cadena de compra, errores parciales, tablet/landscape y salida audible de TalkBack tienen límites descritos. |
| #95 — Hallazgos de alta severidad resueltos o justificados | Parcial | Los defectos de escala señalados en la última revisión tienen corrección y prueba focalizada de fixture. Falta la repetición autenticada del archivo. No se degradó Premium ni se ejecutaron escrituras para forzar escenarios. |
| #95 — Capturas antes/después por cambio | Parcial | Se mantienen los seis enlaces de comparativas previos y los dos enlaces de fixture del aviso de archivo. No se generaron capturas nuevas para los cuatro cambios de esta revisión; las pruebas instrumentadas no se presentan como capturas ni como recorrido real. |
| #95 — Sin archivos fuera del reparto del módulo | Cumple para código de Dinero | Los cambios de app se limitan a `feature/accounts/presentation/` y sus pruebas. No se tocó navegación raíz ni componentes compartidos. El informe está bajo `docs/qa/`. |

## Cambios realizados

- El detalle de tarjeta adapta acciones largas y encabezado de movimientos a escala de fuente grande; permite que el texto ocupe varias líneas sin truncarse.
- El catálogo apila la línea de crédito y el estado de TEA personal a escala grande para evitar colisiones.
- La advertencia de saldo pendiente en la confirmación de archivo usa una columna única para todas las escalas; el importe no queda en una columna estrecha.
- El dashboard adapta encabezado, hero, acceso al calendario y filas de cuenta a fuente ampliada/pantalla compacta. Los controles inertes de compartir/ayuda del catálogo se eliminaron.
- La cabecera del alta de cuenta se reorganiza en ancho compacto/200 %: Atrás ocupa su propia fila y el título deja de competir por ancho; se elimina el prefijo redundante “Agregar”. En el footer del formulario se oculta el estado redundante de éxito para dejar más espacio al botón principal; los errores permanecen visibles.

## Cambios de esta revisión

- **Diálogo de archivo:** el cuerpo del `AlertDialog` usa `verticalScroll(rememberScrollState())`; el test instrumentado monta el diálogo real de presentación con billetera y saldo ficticios y verifica los textos a 1.0×, 1.3× y 2.0×. No toca “Archivar”.
- **Comparativa TEA:** `FlowRow` apila el título y el chip según `isCompactAccountsLayout()`; prueba con y sin TEA personal a las tres escalas.
- **Privacidad:** la membresía anual pasa `LocalBalanceMasked.current` a `MoneyText`; prueba comprueba oculto/visible.
- **Top bar:** `FlowRow` conserva una sola declaración del botón Atrás y título; test confirma un título y una invocación de Atrás por escala.
- **Breakpoint común:** `isCompactAccountsLayout()` vive en `feature/accounts/presentation/AccountsLayout.kt`, fuera de `dashboard/`, y se comparte en dashboard, detalle, catálogo y formulario.
- **Cambios previos del PR:** siguen las adaptaciones `FlowRow`, la eliminación de contenido duplicado, títulos compactos explícitos, tokens de color y controles inertes retirados, con evidencia histórica enlazada más abajo.
- **Límite:** la prueba del diálogo es instrumental y usa callbacks/entidades ficticios. No se autenticó, no se archivó el fixture y no se repitió el flujo autenticado en la app real. No se tomaron capturas nuevas.

## Matriz Fase 0 — indicio, severidad, decisión y evidencia

“Código” indica inspección estática; “fixture” indica una prueba Compose con datos/callbacks ficticios. Ninguna de las dos se cuenta como recorrido manual autenticado. La evidencia del 15-nov se conserva como histórica y no se afirma que se haya repetido en esta iteración.

| Indicio | Severidad | Decisión | Evidencia, límite y responsable |
| --- | --- | --- | --- |
| Selectores de banco para cuenta y tarjeta usan `ModalBottomSheet` | Baja | **Justificado** | `AccountAddFormComponents.kt` y `CalmEmeraldInstrumentComponents.kt` declaran hojas de selección dentro de formularios; no se identificó por código un fallo de cierre. No se repitió manualmente ahora. **Dinero** conserva el patrón salvo reproducción de un defecto. |
| Hoja de éxito de registro usa `ModalBottomSheet` | Baja | **Justificado** | La llamada está en `CalmEmeraldInstrumentComponents.kt`; `UnifiedInstrumentFormScreenTest` cubre el formulario con fixture en el historial QA, pero no el recorrido completo en esta iteración. No hay defecto concreto reproducido. **Dinero**. |
| Cadena de compra: sheet de importe → simulador en `Dialog` → confirmación en `AlertDialog`; foco, Atrás y teclado | Media | **Pendiente** | La secuencia existe en `AccountDetailScreen.kt`. Hay pruebas de preview/simulador con fixture, pero no una verificación de foco/teclado/Atrás de las tres ventanas en dispositivo. No se confirmó compra ni se hizo otra operación financiera. **QA de Dinero** antes de marcarla completa. |
| Anatomía del `AlertDialog` de límite del Plan Free | Media | **Pendiente** | Estático en `DashboardScreen.kt`; no se alcanzó con el estado Premium registrado en la sesión anterior. No se cambió de plan para forzar el caso. **QA de Dinero** cuando haya fixture autorizado Free. |
| Anatomía del `AlertDialog` “Gestionar cupos” | Media | **Pendiente** | El propio flujo requiere exceder una cuota Free positiva (`maxFreeQuota > 0`); no es alcanzable con Premium y no se degradó el plan. **QA de Dinero** cuando exista precondición autorizada. |
| Anatomía/orden de acciones de archivar y reactivar | Media | **Parcial** | `AccountDetailScreenTest`: 24/24, con callbacks falsos; el caso nuevo comprueba cuenta, billetera afiliada y mensaje de saldo a 1.0×/1.3×/2.0× y solo cancela. No se compara en vivo la anatomía con los diálogos de Plan. **Dinero**; queda el flujo real pendiente. |
| Comportamiento al cerrar `PayCardDialog` | Media | **Pendiente** | `PayCardDialog.kt` usa `KipuBottomSheet`; `PayCardDialogTest` cubre importes, cuenta fuente y privacidad, no una secuencia de cierre/Atrás en dispositivo. No se pagó una tarjeta. **QA de Dinero**. |
| “Editar condiciones” aparece en barra y sección | Baja | **Justificado provisionalmente** | El código ofrece un atajo en la barra y una acción contextual junto a las condiciones. No hay evidencia de dos controles equivalentes en el mismo grupo ni de acción inerte; no se eliminó ninguno sin reproducción visual actual. **Dinero** reabre si la captura muestra redundancia real. |
| Cinco contextos de limpiar filtros: chips individuales, acción general, vacío y hoja de filtros | Baja | **Justificado con nota** | `CreditCardMovementsSection` usa `clearAllMovementFilters` para limpiar filtros globalmente; cada chip remueve solo su propio filtro, y el botón del vacío/hoja restablece el conjunto. Los rótulos varían entre “Limpiar” y “Restablecer”; candidato de homogeneización, sin duplicación de lógica. **Dinero**; la prueba manual de los cinco estados sigue pendiente. |
| Colores del hero y tonos de marca de billeteras/cuentas | Baja | **Justificado** | `docs/DESIGN.md` documenta el hero, sus colores locales de texto/subtarjeta y las excepciones locales de `accountSurfaceColor`; los tonos de identidad vienen de `KipuBrand`/presets. No se cambian marcas por tokens semánticos de acción. **Dinero**. |
| Colores funcionales escritos a mano en catálogo TEA | Baja | **Resuelto en el código actual** | Revisión estática de `RateCatalogScreen.kt`: no quedan literales hex directos; las superficies y estados usan colores del tema/tokens. La prueba responsive nueva cubre presentación del chip, no contraste exhaustivo de todos los estados. **Dinero**. |
| Colores de alertas de formulario y `AccountAddFormComponents` | Baja | **Justificado** | Revisión estática no encontró hex directos en esos dos archivos; errores usan el rol de error del tema y estados Calm Emerald. Sin cambio pendiente identificado. **Dinero**. |
| `AccountVisuals`, arte 3D y colores de marca de `CardArt` | Baja | **Justificado por autoridad visual** | `docs/DESIGN.md` explica los valores especializados de `accountSurfaceColor`, marca, efectivo y arte de tarjeta; el issue indica explícitamente no tratar el arte deliberado como defecto sin contradicción visual. **Dinero**; sin cambio de firma/aspecto en componentes consumidores. |
| 54 tamaños `sp` locales señalados en dashboard/detalle | Baja | **Parcial** | `sp` sigue la escala de fuente; `docs/DESIGN.md` registra la escala tipográfica y excepciones del hero. La revisión corrigió defectos concretos de layout, no hizo una auditoría visual individual de los 54 sitios ni un reemplazo masivo de estilos. **Dinero** si se reproduce un corte concreto. |
| Importes del arte de crédito y del catálogo deben respetar “ocultar importes” | Alta | **Resuelto y probado** | El arte usa `MoneyText` con `LocalBalanceMasked`; membresía anual ahora pasa la misma preferencia. `RateCatalogResponsiveLayoutTest` comprueba importe visible/oculto; 3/3 pruebas responsive pasan. **Dinero**. |
| `AnimatedVisibility` del catálogo (error, guardado, advertencia) y política ReducedMotion | Baja | **Pendiente** | Inspección de `RateCatalogScreen.kt` no encontró `rememberReducedMotionEnabled` asociado a esas tres transiciones. No se verificó con reducción de movimiento del sistema. **Dinero**. |
| `AnimatedVisibility` del formulario (últimos cuatro dígitos) y ReducedMotion | Baja | **Pendiente** | `UnifiedInstrumentFormScreen.kt` consulta ReducedMotion para otros comportamientos, pero la transición de visibilidad señalada no muestra una política propia. Falta prueba con el ajuste del sistema. **Dinero**. |
| Springs de `InstrumentCardPreview` | Baja | **Justificado por código, verificación parcial** | `rememberReducedMotionEnabled()` bloquea el gesto y, al activarse, lleva la tarjeta a posición neutral con `snapTo`; no se verificó el toggle del sistema en este seguimiento. **Dinero**. |
| Desplazamiento automático a Cuotas con ReducedMotion | Baja | **Pendiente** | `BringIntoViewRequester` se activa al abrir `section=installments`, sin rama explícita de ReducedMotion. Mantener el acceso profundo es necesario; probar su movimiento/lectura en dispositivo antes de alterar el comportamiento. **Dinero**. |
| “Nueva compra” / “Registrar consumo” / “Consumo con tarjeta” | Baja | **Justificado provisionalmente** | Los dos primeros son título de borrador/CTA; el tercero es el descriptor de un movimiento histórico. Son contextos distintos y no se reprodujo confusión. No se cambió microcopy sin evidencia/decisión de producto. **Dinero** puede homologar si QA de comprensión lo requiere. |
| “Pago Tarjeta de Crédito” / “Pagar tarjeta” | Baja | **Justificado provisionalmente** | El primero titula la hoja y el segundo nombra la acción de detalle; comparten significado, pero cumplen roles distintos. La capitalización difiere; no hay confusión reproducida. **Dinero**; homogeneización de estilo queda como sugerencia. |
| “Uso de línea” frente a `FinancialTerminology.TOTAL_CREDIT_LINE` | Baja | **Justificado semánticamente** | “Uso” etiqueta utilización actual; `Línea total de crédito` nombra el límite total. No son magnitudes intercambiables. Se conserva el término canónico para el límite y el término de uso para la utilización. **Dinero**. |
| Error de resumen/saldo con instrumento ya cargado | Media | **Pendiente** | La condición de error del detalle contempla ausencia de instrumento; no hay fixture que inyecte fallo de resumen/saldo conservando el instrumento. No se afirma que el estado esté resuelto. **QA/Dinero** para reproducción y prueba. |
| Carga/error de la lista de movimientos de tarjeta | Media | **Pendiente** | La sección recibe `List<CardMovementDisplay>` y muestra vacío si la lista filtrada queda vacía; no tiene estado propio de carga/error/reintento. No se inyectó fallo del flujo en esta iteración. **Dinero** debe decidir el estado UI sin modificar datos/repositorio. |
| Indicador de desconexión en dashboard, formulario, catálogo, simulador y pago | Media | **Pendiente de decisión de producto** | En la sesión histórica del 15-nov se observó dashboard cacheado y ausencia de aviso explícito en formulario/simulador/pago; catálogo se inspeccionó offline. No se hizo otra sesión ni se añadió señal de red sin requisito aprobado. **Producto** decide si avisar de posible antigüedad; **Dinero** implementa después. |
| Carga, error, error parcial, primer uso y vacíos del dashboard (H6–H8) | Media | **Parcial, pruebas focalizadas verdes** | `h6UnavailableNetWorthIsNeverZero`, `h7LoadingShowsSkeletonWithoutFigures` y `h8ErrorOffersRetryAndNoFigures` pasaron 1/1 cada una en ejecución previa; `DashboardEmptyStateTest` también está registrado. La suite completa mantiene un teardown conocido en H4 y la sesión real no reproduce cada estado. **QA/Dinero**. |
| Iconos con `contentDescription = null` citados por #95 | Media | **Parcial** | Revisión estática: los casos listados son iconos internos junto a texto/semántica del control y se tratan como decorativos; pruebas de semántica accesible previas pasaron 4/4. TalkBack avanzó foco y registró actividad TTS en la sesión histórica, pero no se confirmó por escucha la verbalización ni el orden completo. **QA/Dinero**. |
| Objetivos táctiles y escala al 200 % | Media | **Parcial** | Los controles nuevos de top bar mantienen 48dp; las pruebas del diálogo/catálogo cubren 1.0×/1.3×/2.0×. No se midieron todos los objetivos táctiles de todos los flujos ni se recorrieron todos a 200 %. **Dinero/QA**. |
| Tablet y landscape | Media | **Pendiente** | No se dispone de evidencia de una pantalla grande/landscape para este seguimiento. La prueba responsive del catálogo fija 400dp para los casos focalizados; no equivale a validar tablet o rotación. **QA/Dinero**. |
| Temas claro y oscuro de las pantallas afectadas | Media | **Parcial** | La auditoría histórica contiene inspecciones en claro y oscuro para escenarios anteriores; las pruebas nuevas usan fixtures y no producen una comparativa de ambos temas. No se generaron capturas nuevas de los cuatro cambios. **QA/Dinero** antes de aceptar la variante. |
| Barra inferior con texto grande | Media | **Fuera de alcance; pendiente transversal** | Es componente compartido reservado al integrador por #95; no se modificó en la rama Dinero. No se aporta una captura nueva de esta revisión. **Integrador / `ux/compartido`**. |

La matriz justifica los hallazgos no implementados sin convertirlos en verificados. “Pendiente” conserva explícitamente el escenario, la evidencia que falta y el responsable.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `:app:assembleDebug` | Pasa | Para ejecutar Gradle se sobreescribió temporalmente el `sdk.dir` de `local.properties` y se restauró al finalizar. El archivo local conserva la ruta SDK anterior del entorno; no se añadió al diff. |
| `:app:testDebugUnitTest --tests ...DashboardAccountGroupingTest --tests ...RateCatalogContextTest` | Pasa | 26 pruebas focalizadas (4 + 22), 0 fallas; no se repitió la suite legacy completa. |
| `:app:connectedDebugAndroidTest ...AccountDetailScreenTest` | Pasa | Pixel 8 AVD / Android 16 API 36; 24 pruebas, 0 fallas, incluida la confirmación de archivo con billetera/saldo ficticios a 1.0×/1.3×/2.0×; canceló sin archivar. |
| `:app:connectedDebugAndroidTest ...RateCatalogResponsiveLayoutTest` | Pasa | Pixel 8 AVD / Android 16 API 36; 3/3: chip con/sin TEA a 1.0×/1.3×/2.0×, membresía visible/oculta y top bar con una acción Atrás/título. |
| `DashboardEmptyStateTest`, `UnifiedInstrumentFormScreenTest` y `CreditAccessibilitySemanticsTest` | Pasan | Fixtures de Compose; 7/7, 2/2 y 4/4 pruebas, respectivamente. Verifican estados vacíos, presentación del formulario y semántica accesible; no sustituyen el recorrido de producción con sesión real. |
| `DashboardRedesignStatesTest` | Parcial | `h6UnavailableNetWorthIsNeverZero`, `h7LoadingShowsSkeletonWithoutFigures` y `h8ErrorOffersRetryAndNoFigures` se ejecutaron de forma focalizada y pasaron (1/1 cada uno). La suite completa sigue parcial: en la ejecución previa, 7 de 8 casos no fallaron por aserción y `h4ArchivedInstrumentsExpand` falla al cerrar `ActivityScenario` (queda en `PAUSED`, no llega a `DESTROYED`). No se cuenta como suite verde ni como defecto visual confirmado. |
| Revisión manual a escala de fuente 200 % | Parcial | Detalle/acciones, encabezado de movimientos, vacío de cuotas, catálogo, aviso de archivo, dashboard y formulario bancario se revisaron en oscuro; dashboard también se comprobó en claro. La barra inferior compartida sigue congestionada. |
| Recorrido autenticado con sesión dummy (15-nov-2026) | Parcial, con datos de prueba autorizados | La cuenta y tarjeta de débito `QA95_DEBITO` se archivaron/reactivaron y quedaron activas. Se registraron y pagaron consumos ficticios `QA95_*` dentro de la app, incluido uno con modo avión confirmado (`cmd connectivity airplane-mode=enabled` y sin redes conectadas); la deuda quedó en cero. No hubo operación con agente externo; los pagos redujeron el saldo ficticio de la cuenta QA. En el pago offline no apareció aviso de conexión. Se crearon `QA95_BILLETERA` (Plin, saldo cero) y `QA95_EFECTIVO` (saldo cero); ambas pruebas quedaron archivadas. Ajustes mostró “Plan Premium”; se creó `QA95_PREMIUM_EFECTIVO` como quinto grupo y se archivó después, sin bloqueo ni degradación. La gestión de cupos no aparece bajo Premium porque la UI solo la presenta al superar una cuota Free positiva. El flujo de archivo/reactivación se completó al archivar también la tarjeta de débito vinculada. Tras reinstalar el APK debug, inicié sesión de nuevo con autorización del usuario; el dashboard autenticado y los instrumentos QA volvieron y la sesión se dejó abierta. Durante TalkBack, swipes físicos cambiaron el foco visual de Ajustes a “Mi dinero” y luego a “Dinero real disponible”. El logcat registró foco de audio de accesibilidad y solicitudes de síntesis/despachos al motor `com.google.android.tts` (`en-us-x-iog-lstm-embedded`); no se verificó por escucha la salida audible ni el contenido exacto, por lo que esta comprobación queda parcial y no se marca como verbalización confirmada. No se recorrió toda la secuencia de foco. TalkBack quedó apagado (`accessibility_enabled=0`, servicio habilitado nulo) y `POST_NOTIFICATIONS` del servicio fue revocado (`granted=false`). No se hicieron operaciones financieras durante la recuperación de sesión ni la comprobación de TalkBack. No se reconfirmó la persistencia/sincronización remota de los datos QA tras reinstalar. |
| `git diff --check` | Pasa | Ejecutado después de editar el informe; sin errores de whitespace. |

## Revisión visual

- **¿Hay cambios visuales?** Sí.
- **Pantallas y escenarios revisados:** la auditoría previa cubrió dashboard (también offline), cuenta/billetera y tarjeta, edición/corrección/archivo, compra/simulador, cuotas/movimientos, catálogo/error offline, selectores y formularios de alta; tema claro/oscuro, escala 200 %, tamaño compacto, reduced motion y TalkBack activo. Este seguimiento añade pruebas instrumentadas del diálogo de presentación y catálogo con fixtures a 1.0×/1.3×/2.0×; no es una nueva verificación autenticada de producción. La salida audible de TalkBack no se confirmó.
- **Evidencia adjunta al PR:** se conservarán los seis enlaces de comparativas existentes y los dos del fixture de archivo a 1.0×/2.0×: [dashboard compacto](https://github.com/user-attachments/assets/7387a1da-8eb1-4737-bf22-8e18e5654963), [alta de cuenta](https://github.com/user-attachments/assets/e2f326d9-c661-4e2c-b520-a929be338bf0), [detalle de tarjeta](https://github.com/user-attachments/assets/a0ae69c1-5a01-49f8-8e0a-976224168676), [acciones de tarjeta](https://github.com/user-attachments/assets/4b15dcea-2457-484b-822f-09334c2a004d), [catálogo](https://github.com/user-attachments/assets/c56e7b02-7325-4c6e-b8fc-475452c52a70), [comparativa anterior del aviso](https://github.com/user-attachments/assets/dd9fbe40-a33e-421e-817b-e447f211d7e3), [fixture 1.0×](https://github.com/user-attachments/assets/bfe0a574-8f54-4e15-bbda-8635464fcbef) y [fixture 2.0×](https://github.com/user-attachments/assets/381cc06f-00f2-4d82-96d7-1aa190a0a709). No se generaron capturas nuevas para los cuatro comentarios de esta iteración.
- **Privacidad:** comparativas sanitizadas; los fixtures de la prueba usan solo saldo/alias ficticios. Ninguna imagen se añade al repositorio.

## Pendientes / límites de la revisión

- Los pendientes de modales, filtros, ReducedMotion, estados de resumen/movimientos, accesibilidad y variantes se detallan fila por fila en la matriz Fase 0.
- “Gestionar cupos” y el límite Free no se forzaron: la sesión histórica confirmó Premium. No degradar el plan para fabricar el escenario.
- Para TalkBack falta confirmar por escucha el texto y el orden completo. El foco visual y actividad TTS quedaron registrados en la sesión histórica; eso no demuestra la verbalización exacta.
- La revisión autenticada del 15-nov es histórica. En este seguimiento el AVD no tiene sesión; no se inició sesión, no se reconfirmó persistencia/sincronización remota de alias QA y no se realizaron operaciones financieras nuevas.
- No hay capturas nuevas de estos cuatro cambios. Las ocho existentes se conservan; el fixture no reemplaza la evidencia del flujo real. La barra inferior requiere seguimiento separado del integrador.
- El informe no implica aprobación formal ni cierre. PR #100 y #95 permanecen abiertos; no se fusiona.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios históricos:** [ampliación de sesión dummy](https://github.com/Lume03/Kipu/issues/95#issuecomment-6898202625), [TalkBack/desconexión](https://github.com/Lume03/Kipu/issues/95#issuecomment-6897279538) y [auditoría parcial anterior](https://github.com/Lume03/Kipu/issues/95#issuecomment-6930663491).
- **Respuestas a los cuatro comentarios nuevos:** pendientes de publicar tras subir esta iteración.
- **Respuestas a los seis hilos anteriores:** [archivo](https://github.com/Lume03/Kipu/pull/100#discussion_r4592195994), [breakpoint](https://github.com/Lume03/Kipu/pull/100#discussion_r4592195971), [FlowRow](https://github.com/Lume03/Kipu/pull/100#discussion_r4592195986), [título compacto](https://github.com/Lume03/Kipu/pull/100#discussion_r4592195996), [acciones reutilizadas](https://github.com/Lume03/Kipu/pull/100#discussion_r4592195989), [tokens](https://github.com/Lume03/Kipu/pull/100#discussion_r4592196001).
- **Revisión y cierre:** no se envió aprobación, no se cerró #95 y no se fusionó PR #100.

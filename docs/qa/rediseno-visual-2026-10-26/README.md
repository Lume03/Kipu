# Rediseño visual de Kipu — 26 de octubre de 2026

**Resultado: presentación integrada y verificada; cierre financiero bloqueado por limpieza QA pendiente.** No constituye aprobación de publicación ni certificación íntegra de todos los flujos.

## 1. Resumen ejecutivo

Se integra la primera tanda del rediseño nativo Android/Compose, con referencias Calm Emerald entregadas por el propietario. El alcance se limita a presentación, datos auxiliares disponibles y conexión con casos de uso existentes. Compilación de app/pruebas correcta, **850 unitarias sin fallos ni errores (2 omitidas)** y **88 instrumentadas focalizadas correctas en A16**. No se ejecutan commits, push, cambios de rama, desinstalaciones, borrado de datos ni emuladores.

El cierre corrigió dos problemas visibles con fuente 1.3×: contador comprimido en el cupo de Presupuestos y alias largo truncado en archivadas. Ambos se comprobaron en el APK actualizado. Persisten tres movimientos `[QA]` de B5 porque la anulación devuelve errores del ledger; se conservan sin alterar la contabilidad por fuera de las operaciones de la app. Esta limitación impide declarar completada la limpieza y certificar el flujo de anulación.

La segunda tanda de Planificación —Deudas D7–D17 y detalles de Metas/Presupuestos— queda expresamente fuera. La evidencia de esta ejecución debe distinguirse de los resultados anteriores declarados por los agentes de cada bloque.

## 2. Pantallas, archivos y decisiones por bloque

### B0 — Componentes, marcas y selectores

**Hecho.** Marcas de bancos/billeteras, artes de tarjetas, skeleton, error, pendiente de sincronización y escala visual 50/80/100. Selector de cuenta/tarjeta con propósito, filtros, contadores y saldo compartido; categorías con acordeón/búsqueda y comercio con catálogo real/texto libre. Parámetros nuevos con valores por defecto para conservar compatibilidad. A1 mantiene icono normal para Billetera digital. B4 y B5 ya conectaron los propósitos y la multiselección que B0 había dejado pendientes en sus llamadores.

Archivos principales: `ui/component/BrandLogo.kt`, `ui/component/KipuStates.kt`, `res/drawable-nodpi/*.webp`, `movements/presentation/AccountSelectorBottomSheet.kt`, `AccountSelectorThemePreview.kt`, `PastelCategoryPickerFlow.kt`, `categories/presentation/components/MerchantPicker.kt`; pruebas de marcas y selectores.

### B1 — Dinero

**Hecho en esta tanda.** H1–H8: hero de dinero propio, máscara, patrimonio desplegable, calendario, cuota real de instrumentos, cuentas con billeteras afiliadas agrupadas, tarjetas separando deuda y crédito, archivadas, carga y error con reintento. El hero apila los paneles con fuente grande; no se duplica saldo de Yape/Plin. H4 se abrió físicamente en A16 y se corrigió el truncado de alias largos con fuente 1.3×. Los estados sintéticos de carga/error/vacío se verificaron en componentes instrumentados del teléfono.

Archivos: `accounts/presentation/AccountsViewModel.kt`, `dashboard/DashboardScreen.kt`, nuevo `dashboard/DashboardSections.kt`, `home/presentation/NetWorthCard.kt`; pruebas `DashboardAccountGroupingTest`, `DashboardRedesignStatesTest`, `DashboardEmptyStateTest`, `Issue48ProductionLabelsTest`.

### B2 — Cuentas y tarjetas

**Presentación hecha; cobertura física parcial por estado.** B2a integra detalles de efectivo, banco y billetera; presentación/alias, corrección auditada, archivo, skeleton/error, frente/reverso de tarjeta y uso 50/80/100. Se corrigió una fuga del límite de crédito en texto y semántica bajo máscara. Los diálogos físicos de corrección/archivo se observaron sin confirmar nuevas operaciones sobre instrumentos del usuario; sus efectos se probaron mediante fixtures.

B2b implementa A1–A7 y el asistente entidad → tarjeta real agrupada por red/nivel → «Configura tu tarjeta». El simulador conserva `SimulateInstallments`. Tasas deja explícita la información ausente: se retiraron la TEA personal prellenada y datos ficticios de TCEA/retiro/exoneración/respaldo; no se compara sin TEA ni se extrapolan monedas. Se retiró un switch sin efecto y se explica el guardado real. A1 conserva la pantalla completa de navegación existente.

Archivos: `accounts/presentation/detail/AccountDetailScreen.kt`, nuevo `detail/AccountDetailSections.kt`, `instruments/UnifiedInstrumentFormScreen.kt`, `CalmEmeraldInstrumentComponents.kt`, `InstrumentFormModels.kt`, `RateCatalogScreen.kt`, nuevo `AccountAddFormComponents.kt` y pruebas correspondientes, incluida `RateCatalogNoDataTest`.

### B3 — Calendario

**Hecho.** C1–C6: mes actual/pasado/futuro, selección de día, filtros de previstos/realizados, detalle, acceso al objeto de origen, mes vacío y máscara. Error/reintento y mes vacío pasaron en componentes instrumentados; la carga no se forzó como fallo persistente de producción. Se comprobó el calendario local con modo avión e importes ocultos. No se agrega un importe desconocido a los vencimientos de tarjeta: se usa «—» y «Sin cargo automático». `moneyDirection` e `institution` son metadatos opcionales de presentación, sin escritura ni nuevas operaciones financieras.

Archivos: `calendar/domain/CalendarEvent.kt`, `CalendarEventProjector.kt`, `calendar/presentation/CalendarScreen.kt`, `CalendarViewModel.kt`, nuevo `CalendarPresentation.kt`; pruebas `CalendarPresentationTest`, `CalendarAndHomeAccessibilityTest`.

### B4 — Movimientos

**Presentación hecha; anulación financiera bloqueada.** Lista/búsqueda, «Filtrando por», carga/error/vacío, anulado, pendientes de sincronizar y editor M16–M23 con «Qué pasará al guardar», validación, nota, descarte y pie fijo. Se conservan revisión, conflicto y callbacks de anulación sin destrucción de historial. El banner cuenta únicamente filas realmente cargadas PENDING/IN_FLIGHT y dice «Sincronización pendiente», sin deducir conectividad.

Los filtros Premium usan los selectores B0 y un catálogo de lectura, sin crear borradores de registro ni conceder Premium ficticio a la cuenta Free. Se corrigieron la cabecera comprimida con fuente grande y los insets duplicados del pie. El fallo unitario de error/reintento provenía del fixture JVM sin aislamiento de `android.util.Log.e`; se aisló el log en la prueba sin eliminar el manejo de errores de producción.

Archivos: `movements/presentation/MovementHistoryScreen.kt`, `MovementHistoryViewModel.kt`, `MovementAppliedFilters.kt`, `MovementDetailSheet.kt`, `MovementEditorSheet.kt`, `MovementChangeSummary.kt`, `MovementFiltersSheet.kt`, `MovementReferenceSelector.kt`, nuevo `MovementFiltersCatalog.kt` y pruebas de historial/editor/filtros/catálogo.

### B5 — Registrar movimiento

**Presentación hecha; limpieza bloqueada.** Gasto/ingreso/transferencia, atajos de monto, recientes con reutilización explícita solo de monto, selectores por propósito, presupuesto aplicable y aviso de exceso sin bloqueo, guardado, duplicados e información de sincronización real. El destino tarjeta reutiliza `PayCreditCard`; sus notas no se ofrecen porque el contrato no las acepta. La compra con tarjeta reutiliza `ConfirmCreditPurchase` y no se trata como gasto de efectivo. No se introduce un nuevo bloqueo por saldo en presentación.

Archivos: `movements/presentation/QuickMovementScreen.kt`, `QuickMovementViewModel.kt`, pruebas `QuickMovementViewModelTest` y `QuickMovementScreenTest`. B5 declara 30 unitarias correctas, 7 instrumentadas y verificación física de guardado, aviso presupuestal no bloqueante, modo avión, monto inválido, transferencia y rechazo de pago por saldo insuficiente. Su informe definitivo registra los tres QA cuya anulación no pudo completar.

### B6 — Planificación (solo listas)

**Parcial por alcance acordado.** Metas: hero de reserva, plan y tarjetas. Presupuestos: Plan Básico, cupo segmentado, estados con icono/texto y CTA de planes. Se conserva el cálculo actual y no se inventan ritmo de ahorro, «Atrasada» o «Quedan N días». El cierre verificó las pestañas y ambas listas con fuente 1.3×, corrigió la compresión del contador de cupo y comprobó la máscara entre Dinero, Metas y Presupuestos.

Archivos: `goals/presentation/GoalListScreen.kt`, `budgets/presentation/BudgetListScreen.kt`, `BudgetComponents.kt`; navegación compartida por separado.

### B7 — Notificaciones

**Hecho.** Centro compacto con X en leídas, «Borrar leídas» con confirmación, filtros existentes y animación respetando movimiento reducido. El borrado usa el repositorio/outbox existente; las no leídas se conservan. La acción colectiva opera sobre las leídas visibles del filtro actual. En el cierre físico el centro estaba vacío: el badge/no leídas, las acciones y su accesibilidad se comprobaron en fixtures instrumentados, sin afirmar una nueva transición persistida sobre un aviso real.

Archivos: `notifications/presentation/NotificationCenterScreen.kt`, `NotificationsViewModel.kt`, nuevo `NotificationRelativeTime.kt`, `navigation/NotificationsNavigation.kt`; pruebas `NotificationActionsTest`, nueva `NotificationsDismissTest`.

### Navegación compartida

Cuatro pestañas inferiores en destino retenido (cambio previo identificado en REPORTS.md); etiquetas inferiores y de Planificación con autoajuste a una línea, comprobadas en A16 con fuente 1.3×. Archivos del ajuste en el working tree: `navigation/KipuNavigationBar.kt`, `PlanningNavigation.kt`. No se genera una nueva rama ni se altera el historial Git.

## 3. Diferencias con la referencia

- Datos y eventos visibles dependen del estado existente; los ejemplos del mockup no se convierten en movimientos, cuentas o deudas reales.
- Dinero omite «Próximo…»: no existe ese dato listo en el contrato actual del dashboard.
- Calendario conserva el mes real del teléfono y sus fuentes; una tarjeta sin importe no equivale a importe cero.
- Billeteras afiliadas se agrupan con su cuenta y muestran saldo compartido.
- Fuente 1.3× puede aumentar la altura y apilar contenido; no se fuerza una copia pixel a pixel a costa de legibilidad.
- Las listas de Planificación no incorporan cálculos de ritmo/plazos o estados sin respaldo implementado.
- Notificaciones conserva la distinción recordatorio/alerta y los filtros reales; la X y el borrado colectivo proceden de la solicitud explícita del propietario.

## 4. Supuestos del mockup no implementados

El límite de **4 instrumentos sí existe**: EP-PLA `specs/012-ep-pla-planes-monetizacion/spec.md`, FR-003, y `FreePlanLimits.instruments`. No se presenta como una limitación inventada del mockup. Se conservan también los cupos de categorías/deudas/metas/presupuestos existentes y su semántica específica.

No se incorporan catálogos ficticios BBVA/Interbank/Scotiabank/BanBif/retail, tasas nuevas ni reglas financieras deducidas del diseño. Solo se usa `CardStylePresets` y la referencia bancaria ya integrada. No se afirma que las tasas hayan sido revalidadas externamente.

Requieren trabajo o decisión posterior: segunda tanda de Planificación; ampliación de catálogos con fuentes aprobadas; cualquier nuevo dato/cálculo para completar los textos del mockup; permisos de publicación de marcas. La coexistencia histórica de guías visuales registrada en DOC-03 no se elimina por este informe: esta tanda sigue las referencias expresamente indicadas en el encargo.

## 5. Assets y publicación

Hay **28 WebP locales (157124 bytes)**: once marcas y diecisiete artes BCP. Logos hasta 192×192; artes hasta 478×301. Los originales y su procedencia están en `docs/referencias/Marcas/`; el mapa usa los identificadores reales de presets. Visa iO no tiene arte propio.

Respaldo: iniciales/color de marca para institución desconocida; degradado/nivel del preset para tarjeta sin arte. La ausencia de red no impide dibujarlos. Los recursos enlazados mediante `R.drawable` deben mantenerse coherentes con el mapa: retirar físicamente un WebP requiere actualizar su referencia de código.

Permisos legales de uso/kit de marca pendientes antes de publicar. No se convierte la disponibilidad pública de una imagen en autorización de uso. No se descargan ni validan nuevas tasas en esta ejecución.

## 6. Verificación y evidencia

### Compilación y unitarias del cierre

En Windows se ejecutó en primer plano, con candado Gradle propio:

```text
gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -q
```

**Exit 0.** 164 suites XML, **850 pruebas, 0 fallos, 0 errores, 2 omitidas**. Copia de los XML en `scratch/qa-visual/CLOSE/unit-xml/`; resumen en `CLOSE/unit-results.json`. Este resultado incluye la disposición adaptable de cupo de B6.

Tras quitar únicamente el límite de una línea al alias de archivadas de B1, se recompilaron app y pruebas y se generaron ambos APK con:

```text
gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest -q
```

**Exit 0**, `CLOSE/gradle-final.log`. No se repitieron las unitarias completas por ese ajuste de texto; la versión posterior pasó las instrumentadas del dashboard y la comprobación visual. App y APK de pruebas se instalaron mediante `adb -s <serial físico> install -r <apk>` en A16 y S24+, sin desinstalar ni borrar datos.

### Instrumentadas propias del cierre — A16

Cada clase se ejecutó con el patrón solicitado, sin tareas `connected*AndroidTest`:

```text
adb -s <A16 físico> shell am instrument -w -e class <FQCN> com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner
```

| Clase | Pruebas | Resultado |
| --- | ---: | --- |
| `com.kipu.app.navigation.RetainedRootTabsTest` | 5 | OK |
| `com.kipu.app.navigation.PlanningTabsTest` | 4 | OK |
| `com.kipu.app.navigation.CalendarNavigationTest` | 1 | OK |
| `com.kipu.app.navigation.MovementHistoryNavigationTest` | 3 | OK |
| `com.kipu.app.feature.accounts.presentation.dashboard.DashboardEmptyStateTest` | 7 | OK |
| `com.kipu.app.feature.accounts.presentation.dashboard.DashboardFinancialMessagingTest` | 3 | OK |
| `com.kipu.app.feature.accounts.presentation.dashboard.DashboardRedesignStatesTest` | 8 | OK |
| `com.kipu.app.feature.calendar.presentation.CalendarAndHomeAccessibilityTest` | 7 | OK |
| `com.kipu.app.feature.notifications.presentation.NotificationCenterScreenTest` | 5 | OK |
| `com.kipu.app.feature.notifications.presentation.NotificationActionsTest` | 4 | OK |
| `com.kipu.app.feature.movements.presentation.MovementHistoryScreenTest` | 11 | OK |
| `com.kipu.app.feature.movements.presentation.MovementEditorScreenTest` | 6 | OK |
| `com.kipu.app.feature.movements.presentation.QuickMovementScreenTest` | 7 | OK |
| `com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest` | 17 | OK |
| **Total propio, 14 clases** | **88** | **Sin fallos** |

Logs por clase: `scratch/qa-visual/CLOSE/a16-<Clase>.log`; comandos/resultados estructurados en `CLOSE/a16-instrumentation-results.json` y ejecutor `CLOSE/run-instrumentation.py`. Son pruebas de componentes y navegación con fixtures aislados; no certifican por sí mismas una escritura remota en la cuenta real.

### Resultados de los bloques/relevo, separados del total anterior

- B0 declara AccountSelector 6/6, categorías 3/3 y comercios 11/11 en S24+; detalles en `B0/REPORT.md`.
- B2a declara 17/17 de detalle en S24+, incluida privacidad del límite de crédito; `B2a/REPORT.md` y `RELEVO/s24-integrated-tests.log`.
- B2b/B4: una suite integrada de 52 pruebas detectó un fallo de cabecera de filtros con fuente 1.6 y teclado. Se reprodujo y corrigió; la suite focalizada posterior dio 17/17 OK, incluidas diez de filtros, catálogo, ausencia de TEA y cinco de crédito/simulador/pago. Logs `RELEVO/s24-header-retry.log` y `RELEVO/s24-final-focused.log`. No se presenta la primera ejecución como íntegramente verde.
- B5 registra 30 unitarias y 7 instrumentadas correctas y los fallos reales de limpieza; `B5/REPORT.md`. Las siete instrumentadas están incluidas también en la ejecución propia posterior; no se suman dos veces.

### Comprobaciones físicas y rutas de capturas

Se contrastaron los diffs y las capturas disponibles con PNG de `docs/referencias/`. Las hojas locales `review-finished-blocks.jpg`, `CLOSE/review-active-snapshots.jpg` y `RELEVO/previous-review.jpg` son ayudas de comparación, no certificación de equivalencia pixel a pixel ni evidencia aprobada para publicar.

| Área/estado | Evidencia local bajo `scratch/qa-visual/` |
| --- | --- |
| B0 cuenta, categoría y comercio | `B0/s24_04_cuenta_gasto.png`, `s24_05_ingreso_cuenta.png`, `s24_06_categoria.png`, `s24_08_comercio.png` |
| B1 hero, máscara y patrimonio | `B1/H1.png`, `H2_masked.png`, `H3_patrimonio.png` |
| B1 H4 y alias largo, fuente 1.3× | `CLOSE/a16-home-archived-font13.png` antes y `a16-archived-fixed-font13.png` después |
| B2 cuentas y tarjeta | `B2a/s24-c1-cash.png`, `s24-c2-bank.png`, `s24-c3-wallet.png`, `s24-c6-correction.png`, `s24-c8-archive-linked.png`, `s24-t1-card-front.png`, `s24-t2-card-back.png` |
| B2 asistente y fuente 1.3× | `B2b/s24-a1-font13.png`, `s24-n1-font13.png`, `s24-n2-art-font13.png`, `s24-n3-font13.png`, `s24-n3-invalid-font13.png` |
| B3 meses, detalle y máscara | Capturas `B3/C1-*` a `C6-*`; fuentes ampliadas `B3/C3-mes-pasado-fuente13.png`, `F13-*` |
| B3 sin conexión y máscara | `CLOSE/a16-calendar-offline.png`; modo avión restituido al terminar |
| B4 lista, búsqueda, editor, validación y offline | `B4/m3_search.png`, `m16_editor.png`, `m17_amount.png`, `m22_invalid.png`, `s24-m1-current.png`, `s24-filters-current.png`, `s24-m8-offline-pending.png`, `s24-m4-no-results.png` |
| B5 gasto/ingreso/transferencia/presupuesto | Capturas `B5/a16_01_*` a `a16_17_*`, detalladas en su informe; incluye errores de anulación |
| B6 pestañas/listas y máscara, fuente 1.3× | `CLOSE/a16-planning-goals-font13.png`, `a16-planning-budgets-font13.png` antes y `a16-budget-fixed-font13.png` después |
| B7 centro y borrado previo | `B7/despues-centro.png`, `despues-borrar-x.png`, `despues-dialogo-borrar-leidas.png`, `despues-vacio.png` |
| B7 centro vacío en el cierre | `CLOSE/a16-notifications.png`; no había aviso real no leído para repetir la transición de badge |

Los estados vacíos/error/reintento, máscara, conflicto/revisión, anulado, filtros y objetivo táctil se comprobaron en las clases que contienen esos casos. Fuente 1.3× se verificó físicamente en las superficies indicadas; movimiento reducido y tema oscuro se ejercitaron en los fixtures que los configuran. No se forzaron datos financieros falsos para producir umbrales 50/80/100 sobre los instrumentos del usuario.

### Estado de dispositivos y datos QA

**A16 y S24+ conservan sesión iniciada**: después de instalar se comprobó el inicio «Mi dinero». **Ambos quedaron con `font_scale=0.9` y `airplane_mode_on=0`**, con candados liberados. Registros sin contenido financiero en `CLOSE/a16-final-state.json` y `CLOSE/s24-final-state.json`; XML/capturas originales permanecen privados en scratch.

**Limpieza no completada:** permanecen los tres registros de prueba `[QA] B5 Ingreso`, `[QA] B5 Gasto` y `[QA] B5 Transferencia`. B5 intentó anularlos mediante la app: ingreso/gasto fallan con `No enum constant ...LedgerRole.PRIMARY` y transferencia con `LEDGER_MISMATCH`. El cierre verificó la evidencia y escaló el bloqueo; no repitió operaciones idénticas sin una corrección de la causa ni ejecutó SQL, borrado directo o reescritura de saldos. Tampoco convirtió anulación en eliminación física del historial.

### Límites explícitos

No se verificaron todas las combinaciones N1–N15/T1–T19/M1–M23 individualmente en ambos teléfonos, pago válido nuevo de tarjeta, confirmación física de duplicado, guardado offline nuevo/persistencia remota de todas las operaciones, ni una nueva corrección/archivo de instrumento del usuario. Carga transitoria y error artificial no equivalen a un fallo remoto real. No se hizo medición exhaustiva de contraste, auditoría completa TalkBack, landscape ni todos los tamaños de fuente en todas las pantallas. No se certifican aislamiento/RLS, compras, tasas vigentes, despliegue remoto ni permisos de distribución de marcas.

La evidencia visual permanece en `scratch/qa-visual/`, local e ignorada. No se publican capturas sin aprobación de privacidad ni datos personales, sesiones o importes de la cuenta. Las rutas de capturas en el informe indican ubicaciones locales, no archivos incorporados a Git.

## 7. Riesgos y deuda técnica

- **Bloqueante financiero:** `LedgerRole.PRIMARY` y `LEDGER_MISMATCH` impiden anular los tres QA y certificar ese flujo. Las variaciones aparentes de saldo observadas por B5 requieren investigación separada; no se atribuye aquí una causa ni se certifica la proyección contable a partir de una captura. Debe corregirse la capa responsable, verificar compensaciones/sync y limpiar los fixtures mediante operaciones auditadas antes de aprobar el cierre financiero.
- Diferencias entre diseño y datos actualmente disponibles, enumeradas arriba.
- Legalidad de distribución de marcas/artes sin cerrar.
- Cambios simultáneos en un mismo working tree: el resultado de una compilación anterior no acredita el árbol final.
- La revisión visual no certifica por sí sola persistencia/sync remota, aislamiento, compras, RLS ni todo el catálogo de estados.
- Segunda tanda de Planificación pendiente por decisión del propietario.
- Resultados de agente y comprobaciones independientes del cierre deben figurar por separado.

## 8. Commits sugeridos (no ejecutados)

Propuestas por bloque con los archivos de implementación/pruebas actualmente modificados o nuevos. Antes de seleccionarlos, revisar el diff completo del archivo compartido y la evidencia final. No se ha ejecutado ningún commit, push ni staging.

Los PNG/HTML de referencia aportados y sus reorganizaciones preexistentes requieren una selección documental separada; no se mezclan automáticamente en estos commits. Las capturas y logs de `scratch/qa-visual/` son evidencia local y no se incluyen.

### B0

`feat(ui): add brand assets, shared states and reusable movement selectors`

- `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/MerchantPickerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheetTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerSelectorTest.kt`
- `app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorThemePreview.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt`
- `app/src/main/java/com/kipu/app/ui/component/BrandLogo.kt`
- `app/src/main/java/com/kipu/app/ui/component/KipuStates.kt`
- `app/src/main/res/drawable-nodpi/ic_brand_banbif.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_banco_nacion.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_bbva.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_bcp.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_cencosud.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_falabella.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_interbank.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_plin.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_ripley.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_scotiabank.webp`
- `app/src/main/res/drawable-nodpi/ic_brand_yape.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_amex_latam_black.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_amex_latam_classic.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_amex_latam_gold.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_amex_latam_platinum.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_classic.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_latam_classic.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_latam_gold.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_latam_iridium.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_latam_platinum.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_latam_sapphire.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_latam_signature.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_light.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_qore_classic.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_qore_gold.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_qore_infinite.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_qore_platinum.webp`
- `app/src/main/res/drawable-nodpi/img_card_bcp_visa_qore_signature.webp`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/AccountSelectorRulesTest.kt`
- `app/src/test/java/com/kipu/app/ui/component/BrandAssetsTest.kt`
- `docs/referencias/Marcas/README.md`

### B1

`feat(dashboard): redesign money overview with shared balances and explicit states`

- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/Issue48ProductionLabelsTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardEmptyStateTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardRedesignStatesTest.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardSections.kt`
- `app/src/main/java/com/kipu/app/feature/home/presentation/NetWorthCard.kt`
- `app/src/test/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardAccountGroupingTest.kt`

### B2a

`feat(accounts): redesign instrument details and audited correction sheets`

- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreenTest.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailSections.kt`

### B2b

`feat(accounts): redesign account forms and three-step card setup`

- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogNoDataTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentWalletAffiliationTest.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/AccountAddFormComponents.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/InstrumentFormModels.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt`

### B3

`feat(calendar): redesign event calendar and source detail sheets`

- `app/src/androidTest/java/com/kipu/app/feature/calendar/presentation/CalendarAndHomeAccessibilityTest.kt`
- `app/src/main/java/com/kipu/app/feature/calendar/domain/CalendarEvent.kt`
- `app/src/main/java/com/kipu/app/feature/calendar/domain/CalendarEventProjector.kt`
- `app/src/main/java/com/kipu/app/feature/calendar/presentation/CalendarPresentation.kt`
- `app/src/main/java/com/kipu/app/feature/calendar/presentation/CalendarScreen.kt`
- `app/src/main/java/com/kipu/app/feature/calendar/presentation/CalendarViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/calendar/presentation/CalendarPresentationTest.kt`

### B4

`feat(movements): redesign history filters and revision editor`

- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementEditorScreenTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementFiltersCatalogTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementFiltersIssue19Test.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreenTest.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementAppliedFilters.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementChangeSummary.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementDetailSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersCatalog.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementReferenceSelector.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModelTest.kt`

### B5

`feat(movements): enhance quick registration with budget impact and card payments`

- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/QuickMovementScreenTest.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModelTest.kt`

### B6

`feat(planning): align goals and budgets lists with visual references`

- `app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetComponents.kt`
- `app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetListScreen.kt`
- `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalListScreen.kt`

### B7

`feat(notifications): compact notification center and dismiss read notices`

- `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationActionsTest.kt`
- `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt`
- `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationRelativeTime.kt`
- `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModel.kt`
- `app/src/main/java/com/kipu/app/navigation/NotificationsNavigation.kt`
- `app/src/test/java/com/kipu/app/feature/notifications/presentation/NotificationsDismissTest.kt`

### NAV

`fix(navigation): keep bottom and planning labels readable with large text`

- `app/src/main/java/com/kipu/app/navigation/KipuNavigationBar.kt`
- `app/src/main/java/com/kipu/app/navigation/PlanningNavigation.kt`

### Documentación de cierre

`docs(qa): document visual redesign verification and remaining limitations`

- `docs/qa/rediseno-visual-2026-10-26/README.md`
- `docs/qa/README.md`

# Reporte de Entrega — Lote L10b: UI del resto de módulos

**Fecha:** 2026-11-12  
**Worktree:** `C:\Users\Alume\orca\workspaces\Kipu\audit-fixes`  
**Rama:** `Lume03/audit-fixes`  
**Módulos intervenidos:** `feature/categories/`, `navigation/`, `ui/component/`, `feature/widgets/`, `feature/auth/`, `feature/analytics/`, `feature/settings/`, `feature/debts/`, `feature/goals/`, `feature/recurrences/`, `feature/notifications/`.

---

## 1. Resumen de Hallazgos Principales y Archivos Modificados

### [CAT2-01] Gestión y visibilidad de subcategorías inactivas
- **Problema:** Las subcategorías inactivas se listaban mezcladas como si estuvieran activas en la lista principal sin distinción visual y no existía mecanismo interactivo para reactivarlas desde la UI.
- **Solución:** Se filtran las subcategorías activas en el árbol principal (`subItem.category.isActive`); se añade la etiqueta visual explícita «Inactiva» cuando corresponde; en el menú contextual se conmuta dinámicamente entre «Inactivar» y «Reactivar» con llamada a `onToggleSubcategoryActive`; y en la sección inferior de inactivas se incluyen raíces con subcategorías inactivas.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`

### [CAT2-03] `CategoryFormDialog` oculta errores de guardado y permite pulsaciones concurrentes
- **Problema:** El diálogo no exponía estado de guardado ni mensajes de error, permitiendo clics múltiples repetidos y no informando fallos al usuario.
- **Solución:** Se añadió `isSaving: Boolean = false` al `CategoriesUiState`, se protegió `submitCreateCategory()` con guard clause contra reentrancia concurrente, y se agregaron los parámetros `isSaving` y `errorMessage` a `CategoryFormDialog` propagándolos a `CategoryFormContent(isCreating = isSaving, errorMessage = errorMessage)`.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt`
  - `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryFormScreen.kt`
  - `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`

### [NAV1-03] Botón Atrás en editor de movimientos desde comprobantes cierra todo el flujo
- **Problema:** Al abrir el editor de un movimiento desde la bandeja de comprobantes (`ReceiptMovementEditorHost`), pulsar el botón Atrás del sistema descartaba toda la pantalla de comprobantes en lugar de regresar a la lista de candidatos.
- **Solución:** Se integró `BackHandler(enabled = editingMovementId != null) { editingMovementId = null }` para interceptar la navegación atrás y volver al listado de comprobantes.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt`

### [NAV1-05] Snackbar «Comprobante agregado» se cancela por consumo prematuro del evento
- **Problema:** En `ReceiptFlowScreen`, el `LaunchedEffect(state.addedEventId)` consumía el evento antes o al mismo tiempo que lanzaba el Snackbar, lo cual cancelaba la corrutina del Snackbar inmediatamente al recomponer.
- **Solución:** Se reordenó la ejecución para mostrar primero el Snackbar (`snackbarHost.showSnackbar(...)`) y consumir el evento tras su finalización o descarte.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt`

### [NAV1-01] `ReceiptFlowViewModel` fija `owner` una sola vez y bloquea nuevas sesiones
- **Problema:** `ReceiptFlowViewModel` no actualizaba `owner` si cambiaba el usuario activo en `session.localAccess`, bloqueando permanentemente a nuevos usuarios en el mismo dispositivo.
- **Solución:** En la recolección de `session.localAccess`, cuando `access is LocalAccess.Available`, si `owner != null && owner != access.userId` se actualiza `owner = access.userId`, se limpia el `SavedStateHandle` y se reinicia el estado de flujo. Si `owner == null` simplemente se inicializa sin borrar estado restaurado.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt`
  - `app/src/test/java/com/kipu/app/navigation/ReceiptFlowViewModelTest.kt`

### [NAV1-04] `RetainedRootTabs` valor por defecto `pageCount = 3` no coincide con 4 pestañas
- **Problema:** El parámetro `pageCount: Int = 3` provocaba inconsistencias con la navegación real de 4 pestañas de la aplicación.
- **Solución:** Se removió el valor por defecto forzando a los llamadores a especificar el número exacto de páginas.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/navigation/RetainedRootTabs.kt`
  - `app/src/androidTest/java/com/kipu/app/navigation/RetainedRootTabsTest.kt`

### [NAV1-02] `MaskedCardReference` lanza excepción no capturada ante dígitos no canónicos
- **Problema:** La presencia de `require(Card.isValidLastFourDigits(sanitized))` crasheaba la composición ante tarjetas con dígitos vacíos o incompletos.
- **Solución:** Se retiró el `require` y se implementó fallback elegante a `"••••"` y nombre genérico `"Tarjeta"` preservando la estabilidad de Compose.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/ui/component/MaskedCardReference.kt`

### [UI3-05] Widgets de clima/vencimientos limitaban la suma a solo seis obligaciones
- **Problema:** En `WidgetDataProvider.kt`, `loadDues` aplicaba `.take(MAX_DUES)` antes de calcular el clima financiero y montos totales, ignorando los vencimientos posteriores a los primeros 6.
- **Solución:** Se removió la limitación prematura para que todos los vencimientos aporten al cálculo de cobertura y clima, mientras la vista de lista maneja su propia presentación acotada.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/widgets/data/WidgetDataProvider.kt`

### [SEC2-02] Spinner de `AuthPrimaryButton` con contraste insuficiente
- **Problema:** `CircularProgressIndicator` utilizaba `colors.primaryDeep` sobre fondo `colors.primaryDeep`, haciéndolo invisible.
- **Solución:** Se corrigió a `colors.onPrimaryDeep` garantizando contraste óptimo y legible.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/auth/presentation/AuthComponents.kt`

---

## 2. Medios y Bajos Atendidos y Omitidos

### Medios y Bajos Atendidos:
1. **CAT2-04 (Medio):** Búsqueda y resaltado de categorías normalizado con `CategoryRules.matchesNormalized`.
2. **CAT2-06 (Medio):** Eliminación de scroll anidado redundante en `MerchantPicker` para evitar conflictos con `LazyColumn`.
3. **CAT2-08 (Bajo):** Limpieza de tokens estáticos muertos en `CategoryDesignTokens.kt` y fallback seguro a `Icons.Default.Label`.
4. **CAT2-09 (Bajo):** Conteo de pestañas de categorías considerando únicamente raíces activas (`count { it.category.isRoot && it.category.isActive }`).
5. **CAT2-10 (Bajo):** Banner de cupo de categorías permite navegar y ver planes al alcanzar o superar el límite (`>= state.maxCustomRoots`).
6. **CAT2-11 (Bajo):** Uso de métodos canónicos de `CategoryRules` (`countActiveCustomRoots`, `countActiveCustomExpenseRoots`, `countActiveCustomIncomeRoots`) en `CategoriesViewModel`.
7. **CAT2-12 (Bajo):** Etiqueta explicativa de cupo en categorías generales: `"Usa 1 cupo en gastos e ingresos"`.
8. **CAT2-15 (Bajo):** Sección de inactivas condicionada a `totalInactiveCount > 0`.
9. **CAT2-16 (Bajo):** Agregados icono `"restaurant"` y color `"#0F766E"` a las opciones rápidas de creación de categorías.
10. **CAT2-18 (Bajo):** Optimización con `remember(availableGroups, filterColor)` en `MerchantPicker`.
11. **SEC2-01 (Medio - Test):** Adaptación de aserción en `AuthCelebrationScreensTest` a `"Tu dinero está listo"`.
12. **SEC2-06 (Bajo):** Eliminación de `LiveRegionMode.Polite` redundante en `AuthBanner`.
13. **SEC2-07 (Bajo):** Eliminación del borrado prematuro de confirmación en `AuthFormScreen.submit()`.
14. **SEC2-12 (Bajo):** Eliminación de semánticas duplicadas de `contentDescription` en campos de autenticación que confundían a lectores de pantalla.
15. **SEC2-14 (Bajo):** Color de fondo consciente de tema oscuro para el scrim de status bar en `AuthFormScreen`.
16. **SEC2-17 (Bajo):** Limpieza de import no utilizado en `SignOutDialog.kt`.
17. **UI1-03 (Bajo):** Envolvimiento de fila y divisor en `Column` en `AnalyticsHeader` evitando saltos visuales.
18. **UI1-04 (Medio):** `BalanceLineSection` en `AnalyticsScreen` respeta los filtros activos de cuenta (`filterAccountIds`).
19. **UI1-17 (Bajo):** Limitación de `percentChange` en `AnalyticsFlowSections.kt` a `[-999_999, 999_999]`.
20. **UI2-06 (Bajo):** Eliminación de guardado doble innecesario de preferencias en toggle `hideBalances`.
21. **UI2-11 (Bajo):** Eliminación de tarjeta estática ficticia en `ProfileSettingsScreen`.
22. **UI2-15 (Bajo):** `remember` para callback `selectTheme` en `ProfileSettingsScreen`.
23. **DEB2-02 (Medio):** Eliminación de tarjeta ficticia de condonación de deuda cuando el saldo es cero en `DebtScheduleScreen`.
24. **GOA1-07 (Medio):** Sugerencia mensual consistente entre tarjeta de meta y detalle usando `goalMonthlySuggestion`.
25. **REC1-10 (Bajo):** Extracción segura de concepto con comillas en `RecurrenceLifecycleScreen` con `(rule.templateTransaction["concept"] as? JsonPrimitive)?.content`.
26. **NAV1-08 (Bajo):** Lectura diferida de alfa en `KipuSkeletonBlock` para reducir recomposiciones.
27. **NAV1-14 (Bajo):** Detección de prefijo de moneda ("S/" o "$") en `MoneyText` cuando `isMasked`.
28. **NAV1-17 (Bajo):** Reutilización de expresiones regulares compiladas en `BrandLogo`.
29. **NAV1-18 (Bajo):** Uso de `Modifier.selectable` en filas de opciones de formulario.
30. **UI3-07 (Medio - Test):** Actualización de búsquedas de texto («Marcar todas como leídas», «Recordatorio») en `NotificationActionsTest` y `NotificationCenterScreenTest`.

### Medios y Bajos Omitidos (y justificación):
- **UI1-05 (Medio):** Optimización algorítmica de deltas en `BalanceSeriesCalculator.series` pertenece al módulo de dominio / capa analítica compartida fuera del alcance estricto de UI del resto de módulos.
- **REC1-11 (Bajo):** Ajuste de zona horaria Lima en `CalendarViewModel` y `ObserveCalendarMonth` involucra lógica de dominio y flujos de negocio.
- **DEB2-03 (Medio):** Desfase de amortizaciones entre `calculateProjections` y `SetDebtSchedule.plan` reside en casos de uso de dominio.
- **GOA1-08 (Medio):** Pruebas de integración de gestión de metas legacy que prueban flujos deprecados de creación de metas en arquitectura anterior.

---

## 3. Casos Visuales para Validación en Teléfono

1. **Gestión de Subcategorías (CAT2-01):**
   - Ir a Menú > Categorías.
   - En una categoría raíz con subcategorías, inactivar una subcategoría desde el menú contextual.
   - *Verificación:* La subcategoría desaparece de la lista activa principal. En la sección inferior «Inactivas» aparece la categoría raíz con la subcategoría mostrando la etiqueta «Inactiva» y la opción de menú «Reactivar». Al reactivarla vuelve inmediatamente a la lista principal.
2. **Formulario de Categoría y Protección de Guardado (CAT2-03):**
   - Abrir el diálogo de creación de nueva categoría.
   - Pulsar «Crear categoría».
   - *Verificación:* El botón muestra indicador de progreso y se deshabilita evitando pulsaciones duplicadas; si ocurre un error, se muestra el mensaje de error directamente en el diálogo sin cerrarse bruscamente.
3. **Editor de Movimiento desde Comprobantes (NAV1-03):**
   - Abrir un comprobante Yape y pulsar editar movimiento.
   - Presionar el botón Atrás del sistema o de la barra superior.
   - *Verificación:* Solo se cierra el editor del movimiento, regresando a la vista previa/lista de comprobantes en lugar de salir al menú principal de la aplicación.
4. **Notificación de Comprobante Agregado (NAV1-05):**
   - Importar un comprobante.
   - *Verificación:* El Snackbar «Comprobante agregado» se muestra completo en pantalla durante su duración esperada sin cancelarse abruptamente.
5. **Cierre y Condonación de Deuda (DEB2-02):**
   - Abrir una deuda totalmente pagada (saldo S/ 0.00).
   - Navegar a la pestaña «Ajustes y cierre».
   - *Verificación:* La sección de condonación informa de forma limpia que la deuda ya tiene saldo cero y no inventa datos de auditoría ni acuerdos ficticios de exoneración comercial.
6. **Sugerencia de Ahorro en Metas (GOA1-07):**
   - En la lista de metas, revisar una meta activa con fecha límite futura.
   - *Verificación:* La sugerencia de ahorro mensual «Ahorra S/ XX al mes» coincide exactamente con el valor calculado en la pantalla de detalle de la meta.
7. **Botón Primario de Autenticación (SEC2-02):**
   - En la pantalla de login/registro, presionar el botón principal.
   - *Verificación:* El spinner de carga se visualiza en blanco/onPrimary con contraste perfecto sobre el fondo esmeralda profundo.
8. **Widgets de Vencimientos (UI3-05):**
   - Agregar el widget de Kipu con más de seis compromisos próximos.
   - *Verificación:* El balance y total exigible toma en cuenta la totalidad de compromisos y no solo los seis visibles.

---

## 4. Estado de Verificación y Compilación

- `.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**
- `.\gradlew.bat :app:compileDebugUnitTestKotlin` → **BUILD SUCCESSFUL**
- `.\gradlew.bat :app:compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**
- Suites unitarias focalizadas (`ReceiptFlowViewModelTest`, `CategoriesViewModelTest`, `RetainedRootTabsTest`, `RateCatalogContextTest`) en estado verde.

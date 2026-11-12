# Reporte de Entrega — Lote L10a: UI de Cuentas, Tarjetas y Movimientos

**Fecha:** 2026-11-12  
**Worktree:** `C:\Users\Alume\orca\workspaces\Kipu\audit-fixes`  
**Rama:** `Lume03/audit-fixes`  
**Módulos intervenidos:** `app/src/main/java/com/kipu/app/feature/accounts/presentation/`, `app/src/main/java/com/kipu/app/feature/movements/presentation/`, `app/src/test/`, `app/src/androidTest/`.

---

## 1. Resumen de Hallazgos Principales y Archivos Modificados

### [ACC2-03] Tasa referencial para tarjetas CMR Falabella, Ripley y Cencosud
- **Problema:** La búsqueda de tasas SBS en el catálogo utilizaba el nombre descriptivo comercial de la tarjeta en vez del código SBS o emisor normalizado (`bank.code`), provocando que tarjetas retail no encontraran sus tasas oficiales.
- **Solución:** Normalización del emisor hacia `bank.code` (`RateCatalogContext.kt`) y búsqueda directa por código de emisor (`UnifiedInstrumentFormScreen.kt`).
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogContext.kt`
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt`
  - `app/src/test/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogContextTest.kt`

### [ACC2-04] Catálogo de tasas SBS mostraba cifras ficticias fijas
- **Problema:** En ausencia de datos SBS en el catálogo, se mostraban valores de ejemplo quemados en código («161.74%», «81.50%», «S/ 1,200», «24/09/2026») como si fuesen información real.
- **Solución:** Reemplazo de los fallbacks fijos por la cadena oficial «Sin dato SBS» y renderizado condicional sin barra de termómetro ficticia cuando no hay límites SBS.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt`

### [ACC2-02] Alta de cuenta/tarjeta colgaba la app por alias no validado en UI
- **Problema:** Nombres/alias inválidos por reglas de dominio lanzaban excepciones no atrapadas que abortaban corrutinas; además faltaba limitación de longitud a 80 caracteres en campos de texto.
- **Solución:** Validación previa en UI (longitud máxima de 80 caracteres, prohibición de PAN/CVV en alias), `maxLength = 80` en `AddFormTextField`, y protección con `runCatching` en llamadas de creación de `AccountsViewModel` para emitir eventos de error en lugar de crasheos.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/AccountAddFormComponents.kt`
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt`
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt`

### [ACC4-01 & ACC4-05] Árbol de productos de crédito bloqueaba el plegado y perdía estado ante rotación
- **Problema:** `netOpen` y `tierOpen` evaluaban `|| hasSelection || byNetwork.size == 1`, impidiendo cerrar secciones seleccionadas o de una sola red. Además, `remember` no sobrevivía a recreación de configuración.
- **Solución:** `openOverrides` con `rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }`, calculando apertura por defecto pero permitiendo al usuario contraer o expandir cualquier nodo explícitamente.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt`

### [ACC4-02 & ACC4-03] Vinculación de débito en edición de cuenta excluía a BanBif y forzaba red VISA
- **Problema:** `registerDebitForEditedAccount` descartaba BanBif con `&& it.code != "BANBIF"` y forzaba `network = CardNetwork.VISA`.
- **Solución:** Inclusión de BanBif reconociendo su preset/código/alias oficial (`banbif-debit-classic`), deducción de red Mastercard para bancos emisores correspondientes (Scotiabank) y selector interactivo de red Visa/Mastercard en la hoja de edición de cuenta.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt`
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt`
  - `app/src/test/java/com/kipu/app/feature/accounts/presentation/EditAccountDebitRegistrationTest.kt`

### [ACC4-04] Gesto 3D en InstrumentCardPreview bloqueaba desplazamiento vertical
- **Problema:** `detectDragGestures` con `change.consume()` incondicional capturaba todo toque vertical impidiendo hacer scroll en formularios y pantallas de detalle.
- **Solución:** Migración a `detectHorizontalDragGestures`, permitiendo que los gestos de deslizamiento vertical pasen transparentemente a los contenedores desplazables padres.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/components/InstrumentCardPreview.kt`

### [MOV4-01] Interruptor "Comparar con mes anterior" no realizaba ninguna acción
- **Problema:** `comparePreviousMonth` era un control sin implementación en repositorio o dominio que reaparecía apagado tras aplicar filtros.
- **Solución:** Eliminación limpia de la opción y sus referencias en modelo borrador, saver serializador y hoja de filtros.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFilterDraft.kt`
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFilterDraftSaver.kt`
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt`

### [MOV5-01] Crash potencial por Math.abs(hashCode) % size con Integer.MIN_VALUE
- **Problema:** `Math.abs(Int.MIN_VALUE)` producía un índice negativo lanzando `ArrayIndexOutOfBoundsException`.
- **Solución:** Uso de `Math.floorMod(name.hashCode(), BaseRecommendedPastelChips.size)`.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt`

### [MOV5-03] Guardado de color oscuro en tema oscuro en vez del código pastel canónico
- **Problema:** El diálogo customizaba HSV a partir del fondo tematizado (`token.containerColor`) oscuro en lugar del color pastel original.
- **Solución:** Conversión a HSV desde el código canónico `PastelColorUtils.hexToColor(token.hexCode)`.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt`

### [MOV5-04 & MOV5-05] Apertura de subcategorías en directSelectionMode y corrección de etiqueta "Actual"
- **Problema:** Tocar una categoría raíz con subcategorías en modo de selección directa la seleccionaba y cerraba el modal inmediatamente sin permitir ver sus subcategorías. Además, "Actual" se marcaba sobre cualquier ítem recién tocado.
- **Solución:** En categorías con hijos, pulsar la fila raíz siempre abre/cierra el acordeón. Se incorporó la fila «Usar "<raíz>"» al inicio de la lista desplegada para seleccionar la categoría padre directamente. Se acotó la etiqueta "Actual" estrictamente a `root.id in currentIds` y `child.id in currentIds`.
- **Archivos modificados:**
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt`

---

## 2. Hallazgos Medios y Bajos (Hechos y No Hechos)

### Medios y Bajos Realizados:
1. **MOV5-02 (Alto / Test):** Corrección de aserción en `AccountSelectorBottomSheetTest.kt` actualizando el texto buscado de `"Créd. disp.:"` a `"Crédito disponible:"`.
2. **MOV4-06 (Bajo):** Soporte de símbolo de moneda dinámico (`$ ` o `S/ `) en chip de importe y resumen en `MovementFiltersSheet.kt` según `draft.currency == "USD"`.
3. **MOV5-17 (Bajo):** Eliminación del `Row` redundante con `spacedBy(6.dp)` y `weight(1f)` innecesario en `AccountSelectionRow.kt`.
4. **MOV5-19 (Bajo):** Parámetro `onCreateCategory` con valor por defecto `= null` en `PastelCategoryPickerBottomSheet`, eliminando lambdas ficticias en llamadores sin creación.
5. **MOV3-16 (Bajo):** En `VoidMovementDialog.kt`, uso de `item.sourceAccountAlias ?: item.destinationAccountAlias ?: "Cuenta"` para ingresos, mostrando la cuenta real impactada.
6. **ACC3-10 (Medio):** Enmascaramiento de importe en `AccountDetailScreen.kt` mediante `PasswordVisualTransformation('•')` cuando `LocalBalanceMasked.current == true`.
7. **ACC4-06 (Medio):** Corrección de codificación en mensaje de error de avisos de crédito: `"No se pudieron cargar los avisos de crédito."` en lugar de `cr?dito`.
8. **ACC4-15 (Bajo):** En `CardInstallmentsSection.kt`, visualización explícita del interés en la fila de detalles cuando `item.interest.minorUnits > 0L`.
9. **ACC3-03 (Bajo):** Verificado ya presente (`"Uso oculto"` con semánticas accesibles en `AccountDetailScreen.kt:1839-1845`).
10. **ACC4-14 (Bajo):** Verificado ya implementado usando `MoneyText` en `CalmEmeraldInstrumentComponents.kt:2379`.

### Medios y Bajos Omitidos (y justificación):
- **ACC4-20 (Medio - Inyección Dagger/Hilt):** Requiere refactorizar el grafo de dependencias de Dagger y actualizar constructores en tests de integración fuera del alcance de UI presentation.
- **MOV3-17 (Medio - Longitud máxima de notas libres):** Requiere validación transversal en casos de uso de dominio/Room y compatibilidad con sincronización remota.
- **MOV5-06 & MOV5-18 (Bajo - Navegación de creación en bottom sheet):** Implica reestructuración del flujo de pantallas secundarias dentro del modal, reservado para iteración de navegación.
- **ACC4-16 (Bajo - Capturas de pantalla androidTest sin aserciones):** Limpieza de harness de pruebas visuales legacy sin impacto en runtime.

---

## 3. Casos Visuales para Validación en Teléfono

1. **Catálogo de Tasas SBS (ACC2-03, ACC2-04):**
   - Abrir catálogo de tasas referenciales.
   - Seleccionar tarjetas retail: CMR Falabella, Tarjeta Ripley, Tarjeta Cencosud.
   - *Verificación:* Muestra su tasa oficial o «Sin dato SBS» de forma limpia, sin mostrar porcentajes ficticios (161.74%, 81.50%) ni condiciones inventadas de S/ 1,200.

2. **Formulario de Tarjeta y Cuentas (ACC2-02):**
   - Ir a Nueva Tarjeta o Nueva Cuenta.
   - Intentar ingresar un alias mayor a 80 caracteres.
   - *Verificación:* El campo trunca a 80 caracteres sin desbordar ni permitir guardar texto excesivo; la app no se congela ante datos inválidos.

3. **Árbol de Productos de Crédito (ACC4-01, ACC4-05):**
   - Abrir selector de productos de crédito por red y nivel (ej. Banco Falabella que tiene solo Visa, o BCP con producto seleccionado).
   - Tocar el encabezado de red "Visa" o el nivel de la tarjeta.
   - *Verificación:* La sección se pliega y despliega fluidamente al toque. Al rotar la pantalla, se preserva el estado de acordeón abierto/cerrado.

4. **Desplazamiento sobre Tarjeta 3D (ACC4-04):**
   - En una pantalla con vista previa de tarjeta (`CardFormScreen` o Detalle), deslizar verticalmente colocando el dedo directamente sobre la tarjeta.
   - *Verificación:* La pantalla hace scroll vertical suavemente. Al deslizar horizontalmente, se activa el brillo e inclinación 3D sin bloquear la interacción.

5. **Vinculación de Débito en Edición de Cuenta (ACC4-02, ACC4-03):**
   - Abrir el detalle de una cuenta BanBif y pulsar editar apariencia / vincular débito.
   - *Verificación:* La entidad se reconoce como BanBif (no "Otra entidad"). Aparecen los chips de red (Visa / Mastercard), permitiendo seleccionar la red deseada antes de guardar los 4 dígitos.

6. **Selector de Categorías Pastel (MOV5-04, MOV5-05, MOV5-03):**
   - Al registrar un gasto rápido, tocar el selector de categorías.
   - Tocar una categoría raíz con subcategorías (ej. "Transporte" o "Alimentación").
   - *Verificación:* Se despliegan las subcategorías en vez de cerrarse inmediatamente. Aparece la opción «Usar "Transporte"» para seleccionar la categoría principal.
   - Con el tema oscuro activo, crear una categoría personalizada y seleccionar un chip pastel: el selector registra el color canónico pastel claro sin ensuciar la paleta.

7. **Filtros de Movimientos (MOV4-01, MOV4-06):**
   - Abrir la hoja de filtros avanzados en el historial de movimientos.
   - *Verificación:* No aparece el interruptor fantasma "Comparar con mes anterior". Al seleccionar moneda USD e ingresar rangos de monto, los chips y resúmenes muestran `$ 10 – $ 50` en lugar de `S/`.

8. **Privacidad de Importes en Compra a Crédito (ACC3-10):**
   - Con "Ocultar saldos" activado, abrir "Nueva compra" en el detalle de una tarjeta de crédito.
   - *Verificación:* El campo de importe muestra puntos ocultos (`••••`) protegiendo el monto en pantalla.

---

## 4. Estado de Verificación y Compilación

- `.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**
- `.\gradlew.bat :app:compileDebugUnitTestKotlin` → **BUILD SUCCESSFUL**
- `.\gradlew.bat :app:compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**
- Pruebas unitarias focalizadas (`RateCatalogContextTest`, `EditAccountDebitRegistrationTest`, `MovementFilterDraftTest`) ejecutadas con éxito (0 fallos).

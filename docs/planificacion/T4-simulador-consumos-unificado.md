# T4 — KIP-03: simulador de consumos unificado

## Resultado y acceso

Desde Inicio/Cuentas → tarjeta de crédito → Nueva compra → ingresar importe → Ver simulación se abre `InstallmentSimulatorRoute`. La simulación permite elegir/editar comercio, categoría y condiciones antes de confirmar; conserva la fecha original de operación y, cuando existe, la de procesamiento. El paso previo del detalle ahora solo pide importe, eliminando su segundo formulario de clasificación.

```text
AccountDetailScreen
├── Nueva compra: importe
└── InstallmentSimulatorRoute
    ├── InstallmentSimulatorScreen
    │   ├── MerchantSelectionRow
    │   ├── CategorySelectionRow
    │   └── CreditPurchaseTermsSection
    │       └── CreditPurchasePreview
    ├── MerchantPickerBottomSheet + MerchantPickerViewModel
    └── PastelCategoryPickerBottomSheet

QuickMovementContent
├── MerchantSelectionRow (mismo disparador extraído)
└── CategorySelectionRow (mismo disparador extraído)
```

Los disparadores están en `feature/movements/presentation/components/MovementClassificationRows.kt`; mantienen aspecto, textos, semántica, estados vacíos, errores, acciones de editar/limpiar y tags de captura rápida. Los pickers originales no se modificaron: se conservan catálogo, búsqueda, texto provisional, creación/alias y creación de categorías/subcategorías. La sugerencia usa `ResolvePreferredCategory`, la sugerencia general del catálogo y la misma prioridad de selección explícita que captura rápida; se cancela la resolución anterior y se bloquea confirmar mientras está pendiente.

`InstallmentSimulatorViewModel` observa categorías válidas de gasto, carga tasas, gestiona clasificación y guarda comercio/categoría/términos en `SavedStateHandle`. `CreditPurchaseDraftSaver` comparte la representación guardable con la pantalla de preview; `PurchaseCandidateStateSaver` conserva el candidato y sus instantes al recrearse el detalle. El borrador de términos permanece en el ViewModel si una confirmación falla y se reabre el simulador.

## Componentes ad hoc eliminados o unificados

- Eliminados `selectedInstallments`, los chips propios 1/3/6/12 y la semántica que restringía esas opciones.
- Eliminados `merchantConfirmsZeroInterest` y la casilla de promoción exclusiva para tres cuotas.
- Eliminados el bloque propio de totales/interés, la fila privada `InstallmentRow` y su cronograma separado.
- Eliminados el campo de comercio libre, dropdown de categorías y preview fijo de tres cuotas del paso previo en `AccountDetailScreen`.
- Eliminadas la proyección `PurchaseCategoryOption`/`purchaseCategories` y su suscripción en `AccountsViewModel`, sustituidas por el estado del simulador; se conserva el argumento de constructor `observeCategories` para compatibilidad con los tests existentes.
- Extraídos los disparadores privados del registro y su base `CalmEmeraldSelectionRow`, incluyendo los colores de categoría, sin crear una variante visual para el simulador.
- Unificados tipo de compra, financiación, accesos 3/6/12/24, cantidad personalizada 2–36 (directa = 1), TEA editable/fuente, validación, resumen y cronograma en `CreditPurchaseDraft`/`CreditPurchaseTermsSection`/`CreditPurchasePreview` de T3.

La vista compartida muestra cuota, última ajustada si difiere, interés total, total, TEA/TEM y fuente. Sin intereses muestra tasas cero; con tasa referencial muestra explícitamente «Estimado, no contractual». No se cambió la política de cálculo ni el dominio: `SimulateInstallments` y la persistencia ya usan `InstallmentCalculator.simulateForPurchase` y `BillingCycleCalculator` desde T2.

## Confirmación y coherencia del cronograma

La ruta confirma el mismo candidato y `simulation.terms` mostrados, junto con `merchantId`; el detalle los pasa a `AccountsViewModel.confirmCreditPurchase` y `ConfirmCreditPurchase`, incluyendo categoría, fecha de operación/procesamiento, moneda y principal. No se reconstruyen fecha ni tasa al confirmar, no hay llamada paralela a `RegisterTransaction` y no se añade matemática financiera a Compose.

Se ejecutó un **main temporal fuera del repositorio**, sin crear ni modificar tests: `/var/folders/07/bvj8jr4j2js98s3jbk09vzd00000gn/T/kipu-t4-main-ajoxf915/VerifySchedules.kt`.

Caso: principal S/ 1,200; seis cuotas; TEA personal 45%; operación 25/06/2026 a las 13:00 Lima; cierre 26, pago 19, cierre exclusivo predeterminado D1. El main llama a los casos de uso compilados `SimulateInstallments` y `ConfirmCreditPurchase`, captura los argumentos del segundo con un repositorio proxy y calcula el cronograma con la misma entrada de producción que usa la persistencia. Compara igualdad completa de filas, términos, interés y total; también afirma comercio, categoría, merchantId y fecha exactos. Esta verificación ejecuta el cálculo y el comando, no una escritura Room ni el backend.

```text
PASS: ConfirmCreditPurchase forwards exact merchant/category/date/terms
PASS: SimulateInstallments schedule equals production persistence calculator schedule
TEA = 45%; TEM = 3.144798913430824%
Interés total = S/ 135.49; total = S/ 1,335.49
1/6: cierre 26/06/2026; vence 19/07/2026; principal 184.84; interés 37.74; cuota 222.58
2/6: cierre 26/07/2026; vence 19/08/2026; principal 190.66; interés 31.92; cuota 222.58
3/6: cierre 26/08/2026; vence 19/09/2026; principal 196.65; interés 25.93; cuota 222.58
4/6: cierre 26/09/2026; vence 19/10/2026; principal 202.84; interés 19.74; cuota 222.58
5/6: cierre 26/10/2026; vence 19/11/2026; principal 209.21; interés 13.37; cuota 222.58
6/6: cierre 26/11/2026; vence 19/12/2026; principal 215.80; interés 6.79; cuota 222.59
```

## Validación y continuidad

- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`: **BUILD SUCCESSFUL**.
- `git diff --check`: sin errores.
- No se modificaron tests, notificaciones, deudas, `app/build.gradle.kts` ni `.run/`.
- Se conservan las firmas legacy de `SimulateInstallments` para los consumidores/tests actuales; ninguna UI sigue usando la promoción de solo tres cuotas.
- Fase de pruebas posterior: adaptar expectativas del test Android `CreditAccessibilitySemanticsTest` que todavía busca el selector fijo 1/3/6/12 y verificar interacción de pickers, rotación/restauración, tasa referencial, conflictos de categoría y registro real en Room/backend; el test no se modificó en T4.
- El pulido visual del nuevo paso de importe y el resto del simulador corresponde al coordinador.

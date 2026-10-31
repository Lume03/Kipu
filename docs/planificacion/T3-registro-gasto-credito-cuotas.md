# T3 — Alta de gastos con tarjeta y detalle de cuotas

## Resultado

La captura rápida de movimientos permite declarar compra directa (una cuota sin interés) o compra en cuotas (2–36), con accesos 3/6/12/24 y cantidad personalizada. Ofrece financiación sin interés o con TEA editable entre más de 0 y 500%, con hasta dos decimales. La fuente inicial es la TEA personal; si falta, se consulta el catálogo por identidad de producto y moneda, indicando el mínimo publicado disponible. Una edición establece `USER_ENTERED`; si no existe referencia aplicable, la tasa debe ingresarse manualmente.

La vista previa usa exclusivamente `SimulateInstallments` con `PurchaseTerms`: cuota regular, última ajustada cuando difiere, interés total, total a pagar, cierre y primer vencimiento, y cronograma desplegable. Los mensajes de validación están en español. El guardado deriva nuevamente los términos validados y llama al flujo existente `ConfirmCreditPurchase`, sin ejecutar `RegisterTransaction` para esa compra. El ledger y el cronograma siguen siendo responsabilidad de T2.

El detalle de tarjeta observa `ObserveCardInstallments` y presenta saldo pendiente, próximas a siete días, vencidas y total pagado, filtros por estado y agrupación opcional. Cada cuota muestra compra, descripción si existe, k/n, vencimiento, cierre si existe, total, pendiente y chip de estado con texto y semántica. Las asignaciones de `PayCreditCard` actualizan la proyección observada; la presentación no cambia estados por su cuenta.

## Árbol de composables y acceso

```text
Inicio/Cuentas o Movimientos → botón + «Registrar movimiento»
└── QuickMovementBottomSheet
    └── QuickMovementContent → Gasto → selector de cuenta/tarjeta → tarjeta de crédito
        └── CreditPurchaseTermsSection
            ├── KipuSegmentedControl (tipo de compra)
            ├── KipuSegmentedControl (financiación, cuando hay cuotas)
            ├── accesos rápidos, cantidad personalizada y TEA
            └── CreditPurchasePreview
                ├── CreditInstallmentAmountRow (importes)
                └── cronograma desplegable

Inicio/Cuentas → tocar tarjeta de crédito → AccountDetailScreen
└── AccountDetailContent
    └── CardInstallmentsSection
        ├── CreditInstallmentAmountRow (resumen)
        ├── filtros y agrupación por estado
        └── CardInstallmentRow
            ├── InstallmentStatusChip
            └── CreditInstallmentAmountRow (total y pendiente)
```

Los nuevos composables y el borrador/validador están bajo `feature/accounts/presentation/components`; textos Compose en `res/values/strings_credit_installments.xml`, siguiendo los recursos ya utilizados en captura rápida. Se usan controles Kipu existentes y tokens CalmEmerald. Los mensajes del ViewModel/validador siguen el patrón vecino de cadenas españolas.

`QuickMovementViewModel` conserva sus valores editables (monto, tarjeta/cuentas, comercio, categoría, fecha, nota y expansión) y las condiciones de crédito en `SavedStateHandle`; no persiste comandos en curso ni errores transitorios. Los controles desplegables y filtros usan `rememberSaveable`. Hilt obtiene las nuevas dependencias por sus constructores existentes; los parámetros nuevos tienen valores predeterminados para conservar compatibilidad con los tests actuales.

## Verificación

- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`: **BUILD SUCCESSFUL**.
- `git diff --check`: sin errores.
- No se crearon ni modificaron tests; se compiló el conjunto existente por la ampliación de constructores de presentación.
- `app/build.gradle.kts` y `.run/` permanecen fuera del trabajo y del commit.

## Continuidad

T4 puede reutilizar `CreditPurchaseDraft`, `validateCreditPurchaseDraft`, `CreditPurchaseTermsSection` y `CreditPurchasePreview`; no se modificó `InstallmentSimulatorScreen`. El acceso del detalle «Nueva compra» → «Ver simulación» sigue usando ese simulador y ya recibe/pasa `PurchaseTerms` por el contrato existente de T2; sus selectores de comercio/categoría corresponden a T4. Quedan para la fase posterior la comprobación UI de rotación/restauración, importes con redondeo y reactividad tras pagos parciales/completos, además del pulido visual del coordinador.

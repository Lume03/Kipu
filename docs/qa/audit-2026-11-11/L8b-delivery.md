# L8b — Entrega: Enmascarado de saldos en todas las pantallas

## 1. Tabla de importes modificados

| Archivo:Línea | Qué importe | Cómo quedó enmascarado |
|---|---|---|
| `feature/movements/presentation/MovementHistoryDelete.kt:108` | Importe en diálogo de confirmación de borrado de movimiento (MOV4-04) | Se utiliza `formatMoneyTextDisplay` y semántica accesible `"Monto oculto"`. Muestra `"$currencySymbol ••••••"` con `LocalBalanceMasked.current`. |
| `feature/accounts/presentation/detail/AccountDetailScreen.kt:1615, 1805` | Barra de porcentaje de uso de crédito y banner de riesgo de utilización (ACC3-03) | `animatedProgress` se fija en `0f`, la etiqueta pasa a `"Uso oculto"`, semántica `"Uso de crédito oculto"` y el banner de riesgo alto se suprime si `isMasked`. |
| `feature/debts/presentation/DebtFormComponents.kt:53, 176` | Monto formateado en `debtMoney` y saldo de cuenta amortizadora en `DebtAccountOptionCard` (DEB2-05) | `debtMoney` lee `LocalBalanceMasked.current` y emite `"$symbol ••••••"`. `DebtAccountOptionCard` anuncia semántica accesible con `"Saldo: Monto oculto"`. |
| `feature/debts/presentation/DebtDetailScreen.kt:355, 364, 373, 458, 533, 598, 608, 690` | Saldo pendiente, monto original, amortización, próxima cuota, cronograma, intereses y liquidación de deuda (DEB2-05) | Se sustituyeron formateos planos por `MoneyText` y `formatMoneyTextDisplay`, y progreso de liquidación en 0f si está enmascarado. |
| `feature/debts/presentation/DebtScheduleScreen.kt:117, 137, 203, 237, 305, 335, 417, 442` | Deuda pendiente total, cuota aprox, celdas de cronograma, totales proyectados y límites de condonación (DEB2-05) | Reemplazados por `MoneyText` y `formatMoneyTextDisplay` vinculados a `LocalBalanceMasked.current`. |
| `feature/goals/presentation/GoalFormScreen.kt:355, 477, 583` | Saldo libre de cuenta vinculada y saldo de cuentas disponibles en selector `AccountChoice` (GOA1-12) | Se aplica `formatMoneyTextDisplay` con semánticas accesibles `"... Saldo libre oculto"` y `"... Saldo oculto"`. |
| `feature/goals/presentation/GoalContributionSheet.kt:292, 318` | Saldo libre en origen para aporte/retiro y opciones del selector desplegable de cuentas (GOA1-12) | Formateados con `formatMoneyTextDisplay` y descripciones de accesibilidad `"... libre oculto"`. |
| `feature/goals/presentation/GoalDetailScreen.kt:378` | Saldo libre en opciones del selector de cuenta (`AccountPicker`) para reasignación de reservas (GOA1-12) | Formateado con `formatMoneyTextDisplay` y descripción accesible `"... libre oculto"`. |
| `feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:2375` | Línea de crédito total en celda de tarjeta de instrumento | Migrado de formateo ad-hoc con `"••••"` a `MoneyText(Money(...))` estándar de Kipu. |
| `feature/accounts/presentation/instruments/PayCardDialog.kt:100, 172, 212, 222` | Deuda actual, chip "Pagar total" y mensajes de validación de fondos o tope | Muestra `"Deuda actual: ••••••"`, chip `"Pagar total ••••••"` y validaciones con `($debtDisplay)` / `($balanceDisplay)` enmascarados. |
| `feature/accounts/presentation/instruments/RateCatalogScreen.kt:865` | Línea de crédito total en cabecera de tarjeta del catálogo de tasas | Enmascara la cifra a `"••••••"` y añade semántica accesible `"Monto oculto"`. |
| `feature/movements/presentation/MovementDetailSheet.kt:288` | Importe mantenido tras intento fallido de corrección | Se formatea con `formatMoneyTextDisplay` y semántica `"Se mantiene monto oculto"`. |
| `feature/recurrences/presentation/RecurrenceRegistrationScreen.kt:120` | Importe esperado de pago recurrente | Se sustituyó texto plano por `Row` con `MoneyText` enmascarable. |
| `feature/setup/SetupScreens.kt:489` | Saldo inicial de cuenta en fila resumen de setup (`AccountSummaryRow`) | Migrado a `MoneyText(Money(...))` para respetar el modo privado. |
| `feature/receipts/presentation/ReceiptReviewScreen.kt:252, 307, 346, 397, 490` | Cambio estimado en saldo, filas de comprobantes en bandeja y diálogo de descarte | Migrados a `formatMoneyTextDisplay` y `MoneyText` con prefijos de signo (`+S/ ••••••`, `−S/ ••••••`) y semánticas protegidas. |
| `feature/receipts/presentation/ReceiptSavedContent.kt:67` | Importe de comprobante confirmado y registrado | Formateado con `formatMoneyTextDisplay` y semántica accesible oculta. |
| `feature/statements/presentation/StatementDesign.kt:68, 87` | Funciones `statementCurrencyTotal` y `statementFlowTotal` | Añadido soporte `isMasked: Boolean = false` para retornar `statementSymbol + "••••••"`. |
| `feature/statements/presentation/StatementLineEditSheet.kt:193` | Texto informativo auxiliar `"Leído del PDF: ..."` al editar renglón | Oculta la cifra del PDF mostrando `"Leído del PDF: ••••••"` cuando `isMasked` es true. |
| `feature/statements/presentation/StatementReviewScreen.kt:233, 292, 464, 506, 536` | Totales por moneda, saldos anterior/final, abonos/cargos, lista de renglones y hoja de confirmación | Pasan `isMasked = LocalBalanceMasked.current` a `statementCurrencyTotal`, `statementFlowTotal` y usan semánticas accesibles. |

---

## 2. Importes que no se enmascararon y justificación

1. **Campos de entrada editable (`TextField`, `OutlinedTextField`, `BasicTextField`):**
   - Cumpliendo con la regla dura 3 de la especificación: *"No toques los campos de entrada donde el usuario escribe un importe: el enmascarado es solo para mostrar."*
   - Ubicaciones: `DebtAmountCard`, `CreditCardTermsSheet`, campo de monto de `PayCardDialog`, campo de edición de renglón en `StatementLineEditSheet`, etc.
2. **Precios comerciales de catálogo de planes (`SetupScreens.kt:510`, `PlanPurchaseScreen.kt`, `PlanSelectionScreen.kt`):**
   - Corresponden a la oferta comercial de la aplicación (suscripción Free, Premium anual S/ 99.00, Premium mensual S/ 9.90), no a balances financieros privados del usuario.
3. **Guías didácticas e ilustraciones tutoriales fijas (`ReceiptInboxComponents.kt:153`, `ReceiptShareGuide.kt:377, 458`, `CaptureHowItWorksScreen.kt:201, 301, 358, 417`, `OnboardingIllustrations.kt:85, 110, 149`):**
   - Son gráficos y mockups educativos predefinidos para enseñar a usar la app (ej. "Ejemplo: BCP notifica consumo de S/ 50.00 en Tambo", "MONTO S/ 25.00 PARA Carlos M."). No contienen datos ni cuentas reales.
4. **Términos y condiciones bancarias del catálogo de tarjetas (`RateCatalogScreen.kt:1187, 1364`):**
   - Son ejemplos hipotéticos fijos del tarifario del banco emisor ("Cálculo a 12 cuotas de S/ 1,000", "Consumo mínimo mensual de S/ 1,200 para exonerar membresía"). No corresponden a deuda del usuario.

---

## 3. Casos visuales para probar en el teléfono

1. **Confirmación de borrado en historial (MOV4-04):**
   - En el Dashboard o Perfil, activar el modo «ocultar saldos» (icono de ojo).
   - Ir a la pantalla de Historial de Movimientos y pulsar en eliminar un movimiento.
   - **Verificación:** En el diálogo emergente `MovementHistoryDelete`, verificar que el importe de la contraparte aparece como `S/ ••••••` (o `$ ••••••`), sin revelar la cifra real.
2. **Detalle de tarjeta de crédito (ACC3-03):**
   - Con saldos ocultos, abrir el detalle de una tarjeta de crédito (`AccountDetailScreen`).
   - **Verificación:** La barra de progreso de utilización debe permanecer en 0% con color atenuado y el texto debe mostrar `"Uso oculto"`. El banner de advertencia de riesgo alto de crédito (≥80%) debe estar oculto.
3. **Detalle de deuda y cronograma (DEB2-05):**
   - Con saldos ocultos, ingresar a una deuda activa (`DebtDetailScreen`).
   - **Verificación:** El saldo pendiente hero, el capital original, el capital amortizado y las cuotas de amortización muestran `••••••`.
   - Abrir el cronograma proyectado (`DebtScheduleScreen`): verificar que las cuotas y los totales proyectados muestran `••••••`.
4. **Formulario y hoja de aportes de metas (GOA1-12):**
   - En el formulario de creación de meta (`GoalFormScreen`), tocar en vincular cuenta.
   - **Verificación:** El saldo de cada cuenta en el selector muestra `Saldo: S/ ••••••`.
   - En la hoja de aporte o retiro de meta (`GoalContributionSheet`), comprobar que el texto de `"Saldo libre en origen"` muestra `••••••`.
5. **Hoja de pago de tarjeta de crédito:**
   - Abrir el diálogo de amortizar deuda de tarjeta (`PayCardDialog`).
   - **Verificación:** "Deuda actual" muestra `Deuda actual: ••••••` y el chip rápido muestra `Pagar total ••••••`.
6. **Bandeja de comprobantes y estados de cuenta:**
   - En la bandeja de comprobantes (`ReceiptReviewScreen`), el cambio neto de saldo y cada fila de comprobante muestran `+S/ ••••••` o `−S/ ••••••`.
   - En el revisor de estados de cuenta PDF (`StatementReviewScreen`), los totales por moneda, la lista de movimientos y los saldos de depósitos muestran `••••••`.

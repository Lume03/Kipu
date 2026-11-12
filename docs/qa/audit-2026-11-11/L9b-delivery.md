# L9b ? entrega

No se hicieron commits ni push.

## Cambios por hallazgo

- MOV3-01: `MovementEditorSheet.kt` convierte el d?a UTC del selector a fecha civil de Lima y conserva la hora original; `MovementEditorDateTest.kt`.
- BUD1-01: `RenewBudgetPeriods.kt` aplica el alcance pendiente al terminar el rango y permite procesar un calendario pendiente.
- ACC3-01: `AccountDetailScreen.kt` muestra ?Sin datos? si falta el resumen y no interpreta esa ausencia como deuda cero.
- ACC3-02: `AccountDetailScreen.kt` calcula el ciclo con corte inclusivo en Lima; `CardCycleDateTest.kt`.
- ACC2-09: `Card.kt` y `CreditCardTermsSheet.kt` impiden corte y pago iguales en el dominio y la edici?n; el alta ya lo imped?a.
- ACC1-01: `CreditEntities.kt` incorpora el inter?s de cuotas `WITH_INTEREST` a la consulta de saldos. Es una consulta Room embebida, no una vista de esquema; no requiere migraci?n ni cambio de schema JSON. No se realiz? prueba Room de comportamiento.
- ACC1-02: `AccountDetailScreen.kt` no solicita cupo adicional al reactivar d?bito con banco activo. La protecci?n reside en el llamador de detalle y debe revisarse para cualquier otro llamador futuro.
- UI1-01: `Analytics.kt` compara meses cerrados completos contra el mes anterior entero; `AnalyticsPreviousMonthTest.kt`.
- UI1-02: `Analytics.kt` y `AnalyticsScreen.kt` calculan y presentan ingresos separados de gastos.
- UI2-02: `SetupDraft.kt` usa `MoneyInputParser`; `SetupMoneyParsingTest.kt`.
- CAP1-01: `CaptureCorrelation.kt`, `RoomCaptureCorrelation.kt` y `RoomCaptureStores.kt` requieren fuentes diferentes, cuenta/tarjeta coincidente y proximidad temporal para uni?n por referencia. `RoomReceiptCandidateStore.kt` conserva recibos OCR distintos con referencia repetida cuando falta cuenta conocida; la huella de imagen id?ntica sigue deduplic?ndose y la correlaci?n autom?tica exige evidencia suficiente.
- CAP1-04: `CaptureReviewViewModel.kt` bloquea elegir instrumento con moneda incompatible.
- CAP1-05: `NotificationCaptureParser.kt` reconoce agrupaci?n con espacios; `NotificationCaptureParserTest.kt`.
- CAP2-01: `RoomStatementImportStore.kt` recalcula duplicados al elegir una tarjeta de cr?dito.

- REC1-02: `RecurrenceScheduleCalculator.kt` calcula la pr?xima fecha desde el inicio y la cadencia; `OfflineFirstRecurrenceRepository.kt`, `RecurrenceReminderWorker.kt`, `RecurrenceReminderScheduler.kt` y `CalendarEventProjector.kt` comparten el c?lculo, sin actualizar `next_due_date`. El worker encadena el aviso siguiente mediante WorkManager; `NextRecurrenceTest.kt` cubre d?a 31, febrero, semanal y regla pausada.

## Pendiente

- Revisar y probar los cambios funcionales en tel?fono y los casos de datos existentes, especialmente la validaci?n de tarjetas antiguas con corte y pago iguales y cuotas con inter?s y reembolsos.

## Verificaci?n

Se ejecut? compilaci?n de Kotlin de app, unit tests e instrumentados, y solo las cinco pruebas nuevas: `MovementEditorDateTest`, `CardCycleDateTest`, `AnalyticsPreviousMonthTest`, `SetupMoneyParsingTest` y el nuevo m?todo de `NotificationCaptureParserTest`. Resultado: **BUILD SUCCESSFUL**, incluidas las cinco pruebas focalizadas de la primera entrega. En la continuaci?n se ejecutaron las tres compilaciones y `NextRecurrenceTest` con **BUILD SUCCESSFUL**; no se us? adb.

## Casos para tel?fono

Editar la fecha de un movimiento cerca de medianoche de Lima; seleccionar una cuenta de moneda diferente en captura; importar un estado Visa y elegir tarjeta manualmente; verificar deuda con inter?s y pago parcial; filtrar movimientos alrededor del corte; probar comparaciones de ingresos y meses cerrados; verificar avisos recurrentes consecutivos, pausa y fin de regla; importar recibos distintos con referencia repetida; editar un presupuesto de rango con cambio pendiente; y reactivar d?bito con su banco activo en el l?mite de cupos.

# T6 — KIP-19: pantalla de próximos vencimientos

## Ruta y acceso

Nueva ruta: **`debts/upcoming-dues`** (`UPCOMING_DUES_ROUTE` en `DebtsNavigation.kt`). Desde Planificación → Deudas → **Ver vencimientos** se abre una pantalla independiente con Back, conservando la lista de deudas en el back stack. El botón siempre está disponible, incluso si no hay deudas: permite ver tarjetas con cuotas sin tener una deuda registrada.

Una fila de deuda abre su detalle; «Registrar pago» / «Registrar cobro» abre la liquidación existente de esa deuda y vuelve a vencimientos después del pago. No se inventó un pago por cuota ni una acción nueva para tarjeta. Una fila de tarjeta abre el detalle con `section=installments`; `BringIntoViewRequester` lleva al encabezado Cuotas una vez cargado el detalle. Back vuelve a esta pantalla.

## Lectura unificada y organización

```text
UpcomingDuesViewModel (usuario activo + filtros guardados + retry)
└── ObserveUpcomingDues
    ├── DebtRepository.observeDebts
    ├── DebtRepository.observeDueBalances (nueva consulta de lectura)
    ├── ObserveUpcomingCardInstallments(includeAllDues = true)
    └── observeLimaCivilDate (reutilizado de cuentas, actualización a medianoche)
        └── buildUpcomingDues → UpcomingDuesSnapshot / UpcomingDue
            └── UpcomingDuesScreen
```

Cada fila contiene origen, id de destino, nombre/descripción, cuota k/n cuando existe, vencimiento, total, pendiente con moneda, estado y fecha de liquidación conocida. Se leen deudas sin cronograma con fecha y cuotas PENDING/PARTIAL/PAID; PLANNED se admite como estado pendiente legacy. CANCELLED se excluye. Las filas y las secciones van por vencimiento ascendente, incluidas las vencidas desde la más antigua.

Secciones: **Vencidas**, **Esta semana** (hoy y los siguientes siete días), **Este mes** (el resto del mes), **Más adelante** y **Pagadas recientemente** (últimos 30 días, colapsada por defecto). Los estados derivan del saldo: cero → PAGADA; positivo con fecha anterior a hoy → VENCIDA; hasta siete días → PRÓXIMA; el resto → PENDIENTE. Una fecha de vencimiento nunca acredita un pago.

La cabecera conserva separados **por pagar / por cobrar**, con un total por moneda y conteo de cuotas vencidas. Los saldos respetan origen, tipo y moneda seleccionados; el filtro de estado afecta la lista, no convierte el saldo total en la suma de un subconjunto de cuotas. No se mezclan PEN/USD ni se netean cobros contra pagos.

Filtros: Todos / Deudas / Tarjetas; estados Todos / Pendientes / Próximas / Vencidas / Pagadas y **Por vencer** (conserva el filtro útil anterior); obligación Por pagar / Por cobrar; moneda. Los filtros sobreviven a la recreación mediante `SavedStateHandle`. Hay estados de carga, vacío y error con Reintentar. Cada fila anuncia estado y saldo en su `contentDescription`, además del chip de estado con texto.

## Criterios de saldo y fechas

El sheet anterior mostraba principal programado por cuota, incluso tras pagos parciales. La consulta nueva descuenta solo pagos válidos asignados a la cuota, reutilizando la regla de `getInstallmentRemaining` e ignorando transacciones anuladas. Se añade únicamente lectura, sin migración, cambios de entidades persistidas ni comandos financieros nuevos.

**Decisión del coordinador recibida por `orca orchestration ask`:** `remainingPrincipalMinor` es el tope y fuente de verdad de los totales de deuda. Si el cronograma tiene más pendiente que ese saldo porque hubo pagos generales sin `installment_id`, la diferencia cubre las cuotas más antiguas FIFO **solo como proyección de presentación**. Una cuota cubierta se muestra PAGADA; no se guardan asignaciones, no se cambian estados persistidos ni HU-44. El criterio queda en el KDoc de `buildUpcomingDues`; no se presentan avisos técnicos al usuario. Matemáticamente, la suma pendiente proyectada es `min(pendiente del cronograma, saldo real de deuda)`.

Para tarjetas se conserva por defecto el contrato anterior de `ObserveUpcomingCardInstallments` (vencidas y próximas a siete días), y se añade una opción explícita para el calendario completo y pagadas recientes. La consulta existente de saldos incorpora la fecha de pago/reembolso válido en la proyección; `CardInstallment.settledAt` es opcional y no se persiste en una columna nueva. Las pagadas recientes requieren fecha de liquidación conocida. En una cuota cubierta por la proyección FIFO de pagos generales se usa el último evento general válido de reducción de principal; la atribución se documenta como proyección, no como nueva asignación contable.

## Eliminaciones y reutilización

- Eliminado **`DebtDueAlertsSheet.kt`** de producción, incluyendo sus tarjetas, filas, avatar y controles de sheet privados.
- Eliminados `showDueAlerts` de la lista y `allInstallments` de `DebtListUiState`/su agregación; el nuevo observador obtiene saldos reales por su propia lectura.
- Reutilizados `debtDueDayLabel`, los componentes Kipu (`KipuFilterChip`, `MoneyText`), los tokens de planificación, el detalle de tarjeta con Cuotas y la liquidación de deuda existente.
- `DebtDueAlerts.kt` conserva los helpers de agrupación legacy que consumen los unit tests actuales, además del formateador de fecha relativa que sí utiliza la pantalla nueva; no mantiene un sheet ni otra ruta de UI.
- No se tocaron notificaciones, programación de recordatorios HU-44 ni anticipación de avisos del sistema.

## Verificación y ajustes mínimos de compatibilidad

`./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**. No se ejecutaron ni añadieron pruebas, conforme a la fase asignada. `git diff --check` no presenta errores.

La eliminación de la firma del sheet dejaba dos llamadas directas en `DebtPlanningTabTest` sin compilar. Se añadió únicamente un adaptador local del fixture que convierte sus entradas al nuevo modelo y renderiza `UpcomingDuesScreen`; **no se cambiaron aserciones ni se añadieron casos**. La comprobación Android detectó además un mock de `purchaseCategories` eliminado en T4: se retiró esa sola línea obsoleta en `AccountDetailScreenTest`. Son los únicos ajustes de tests y corresponden a compatibilidad de firmas; las expectativas antiguas del sheet deben migrarse en la fase posterior.

`app/build.gradle.kts` y `.run/` no se modificaron ni se incluyen en el commit.

## Continuidad

Queda para la fase de pruebas posterior verificar navegación/back, foco en Cuotas, pagos parciales/generales y anulaciones, monedas, pagos recientes/anticipados, fechas límite de siete/treinta días, cambios de usuario, medianoche y recuperación de errores; también migrar las expectativas antiguas de UI al nuevo contrato de pantalla. La compilación no sustituye esa verificación de ejecución en dispositivo.

# Análisis del ciclo de tarjetas, cuotas y avisos

**Alcance:** lectura de `specs/002-ep-cta-cuentas-tarjetas`, `specs/005-ep-deu-deudas-prestamos`, `specs/010-ep-not-notificaciones-avisos`, `docs/DESIGN.md` y código Android indicado. No se encontró `DESIGN.md` en la raíz. Este documento distingue el contrato documentado del comportamiento observado en la app.

## 1. Inventario de tarjetas de crédito existente

### Configuración de la tarjeta

- `app/src/main/java/com/kipu/app/feature/accounts/data/local/CardEntity.kt`, `CardEntity`: persiste `billingDay`, `dueDay`, límite, moneda, emisor, últimos cuatro dígitos y `personalTeaBps`. `CardDao.updateCreditTerms()` actualiza cierre y pago; `updatePersonalTea()` actualiza la TEA.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt`, `UnifiedInstrumentFormScreen()`: permite ingresar días de cierre y pago entre 1 y 31 y configurar TEA; actualmente exige que cierre y pago sean distintos. `CreditCalculations.calculateEffectiveDay()` conserva el día preferido y ajusta su fecha efectiva al último día existente del mes.
- `specs/002-ep-cta-cuentas-tarjetas/spec.md` HU-08/RF-C02 contempla la configuración. La TEA personal y el catálogo referencial están especificados en RF-C08; en código, el modelo de tarjeta guarda TEA personal y `RateCatalogScreen.kt` presenta el catálogo.

### Cuotas, fórmula y cronograma

- `app/src/main/java/com/kipu/app/core/finance/domain/CreditCalculations.kt`: `calculateFirstInstallmentDueDate()` determina la primera fecha según cierre y vencimiento; `calculateEffectiveDate()` recorta los días 29/30/31 en meses cortos.
- `app/src/main/java/com/kipu/app/core/finance/domain/InstallmentCalculator.kt`, `InstallmentCalculator.simulate()`: admite de 1 a 36 cuotas, aplica TEM derivada de TEA y pago fijo francés cuando recibe una tasa positiva; sin tasa divide el principal. Devuelve principal, interés estimado, total y `InstallmentScheduleItem` con número, fecha, importe y componentes. El redondeo asigna el residuo al primer pago y verifica sumas exactas.
- `app/src/main/java/com/kipu/app/feature/accounts/domain/model/InstallmentModels.kt`: `PurchaseCandidate` (comercio, categoría opcional, fecha, sugerencia y estado de revisión), `InstallmentSimulation` y `InstallmentScheduleItem`. Los estados de candidato son `PENDING`, `CONFIRMED`, `REJECTED`; la cuota contiene fecha e importes, pero el estado de ciclo de vida se guarda en la entidad local.
- `app/src/main/java/com/kipu/app/feature/accounts/domain/usecase/SimulateInstallments.kt`: selecciona TEA personal o referencial, permite simular sin interés y deriva la fecha de operación a día civil de `America/Lima`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/InstallmentSimulatorScreen.kt`: vista previa y cronograma, con opciones visibles 1, 3, 6 o 12 (aunque dominio admite 1–36). El control de promoción sin interés solo está disponible para 3 cuotas.
- La compra confirmada se registra por `ConfirmCreditPurchase` y `OfflineFirstFinancialInstrumentsRepository.confirmCreditPurchase()`. El repositorio llama a `InstallmentCalculator` con `teaBps = null`, crea cuotas con `interest_minor = 0` y deuda contable igual al principal. La confirmación no conserva una promoción ni interés simulado; el simulador no persiste su plan.

### Pagos y saldo

- `app/src/main/java/com/kipu/app/feature/accounts/data/local/CreditEntities.kt`: `CreditInstallmentEntity` persiste `due_date`, `principal_minor`, `interest_minor`, `status`, revisión y baja lógica. `CreditPaymentAllocationEntity` vincula transacción de pago a cuota e importe asignado. `CreditDao.getOutstandingInstallmentsForCard()` deriva principal pendiente restando asignaciones de pagos válidos y reembolsos válidos; `observeOutstandingPrincipalForCard()` agrega cuotas para el saldo de principal.
- `app/src/main/java/com/kipu/app/feature/accounts/domain/CreditPaymentAllocator.kt`, `CreditPaymentAllocator.allocate()`: aplica pago en FIFO estricto por vencimiento, fecha de compra y número de cuota; acepta amortización parcial y rechaza importe mayor al saldo agregado.
- `app/src/main/java/com/kipu/app/feature/accounts/domain/usecase/PayCreditCard.kt` y `OfflineFirstFinancialInstrumentsRepository`: materializan pago y asignaciones sobre el ledger; el pago no vuelve a ser gasto de consumo. La entidad nueva nace `PENDING`; los importes pendientes pueden derivarse de asignaciones, pero no hay una pantalla integrada de detalle de estado próximo/vencido/pagado para cuotas de tarjeta.
- `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`: base Room versión 30, registra `CreditInstallmentEntity`, `CreditPaymentAllocationEntity`, `DebtInstallmentEntity`, notificaciones y eventos de recordatorio. `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt` crea `credit_installments` y `credit_payment_allocations` en la migración 20→21, y conserva saldos antiguos como una cuota de apertura; el historial anterior no recupera cronogramas inexistentes.

## 2. Ciclo de facturación y brechas

### Cálculo observado y ejemplo Qore

El cálculo está implementado, no es solo una regla aspiracional. `CreditCalculations.calculateFirstInstallmentDueDate(purchaseDate, preferredClosingDay, preferredDueDay)`:

1. Ajusta el cierre preferido al último día efectivo del mes.
2. Considera que la fecha de compra **hasta el cierre inclusive** pertenece al ciclo que cierra ese mes; una fecha posterior cae en el cierre del mes siguiente.
3. Devuelve el primer día de pago efectivo estrictamente posterior a ese cierre. Las cuotas siguientes avanzan por meses y vuelven a ajustar el día efectivo.

Con cierre 26 y pago máximo día 19, la compra del **25/06** cae en el ciclo que cierra **26/06** y vence **19/07**, como en el ejemplo Qore: consumos 26/05–25/06 → cierre 26/06 → vence 19/07. Sin embargo, el código y la decisión escrita en `specs/002-ep-cta-cuentas-tarjetas/spec.md` dicen que el propio **26/06 es inclusivo** en ese cierre, por lo que asignan ese consumo al vencimiento 19/07. La lectura pedida para Qore implica que el 26/06 abre el ciclo siguiente y debería ir al cierre 26/07, con vencimiento 19/08. Hay que elegir entre el contrato actual y la regla observada en el estado de cuenta antes de cambiar la comparación.

### Casos límite y decisiones de modelado

- **Meses cortos:** `calculateEffectiveDate()` lleva cierre 29/30/31 al último día de febrero (28 o 29) y al último día válido de otros meses. El día preferido original permanece en `CardEntity`; no debe sobrescribirse por el ajuste temporal.
- **Fin de semana o feriado:** el cálculo actual conserva el día calendario preferido. No existe ajuste bancario por feriados. Recomiendo mantener fecha contractual por defecto y, si se requiere, modelar como parámetro/regla de tarjeta la convención del emisor (día calendario, día hábil anterior o siguiente) con calendario de feriados explícito; no inferir una regla universal.
- **Operación frente a procesamiento:** el código usa `effectiveAt`/`occurredAt` convertido a `America/Lima`. Aún no hay fecha de procesamiento para una compra ni selector de base de ciclo. Recomiendo parámetro configurable por tarjeta (`OPERACION`/`PROCESAMIENTO`), con default `OPERACION`, marcado **“a validar con estado de cuenta real”**. Guardar ambas fechas cuando se conozcan y usar una sola base elegida para clasificar el ciclo, sin reescribir el instante original de la operación.
- **Vencimiento máximo:** el ejemplo dice “pago máximo día 19”; la app almacena solo el entero `dueDay`, no un offset cierre→vencimiento ni una regla por ciclo. Se deriva el primer vencimiento calendario posterior al corte. Validar con estado real casos en que el emisor publica excepciones o modifica el calendario.

## 3. Compras en cuotas: cobertura y propuesta

### Brechas funcionales

| Necesidad | Situación observada |
|---|---|
| Compra directa frente a financiada | El dominio acepta `installments = 1..36`; el simulador muestra “1 pago” o cuotas. La confirmación mantiene el conteo, crea una cuota para 1 pago y reconoce todo el principal al comprar. |
| Con/sin interés | El simulador calcula tasa personal/referencial o distribución cero; la promoción sin interés es una casilla de 3 cuotas. `onConfirmPurchase` solo pasa el conteo y la compra real fuerza interés cero. No hay persistencia de la promoción ni de una tasa comercial confirmada. |
| Tasa | `personalTeaBps` y `RateSource` existen; aceptación de tasa referencial es parámetro del simulador. No se guarda snapshot de tasa aplicada con el plan real porque este no genera interés. |
| Importe por cuota y cronograma | La simulación muestra fechas, cuota, principal e interés; la confirmación guarda `credit_installments` con principal y fecha, tasa cero. |
| Estados | Cuota nueva: `PENDING`. El vencimiento/próxima/vencida puede derivarse por fecha; saldo de principal y cuotas cubiertas por allocations. No hay estados de UI integrados para próxima y vencida ni política visible para actualizar `PAID` en la vista de tarjeta. |
| Pagos y saldo | Existen allocations FIFO y saldo pendiente derivado, pero principal es la medida contable actual. No se reconoce automáticamente como deuda real el interés de la simulación. |

### Fórmula propuesta y encaje

- **Con interés:** sistema francés con TEA efectiva anual convertida a tasa efectiva mensual: `TEM = (1 + TEA)^(1/12) - 1`; cuota teórica `A = P × TEM / (1 − (1 + TEM)^(-n))`. Redondear a unidades menores de moneda con método documentado; recalcular principal/interés por periodo sobre saldo, y reconciliar el residuo determinísticamente en la última cuota (o cuota final ajustada) para que principal e interés sumen exactamente el total. La simulación debe mostrar TEA, TEM, fuente, importe estimado e incluir advertencia de no contractual.
- **Sin interés:** dividir `P / n` en unidades menores; distribuir cociente y residuo de modo determinista y ajustar la **última** cuota para que el cronograma sume exactamente `P`.
- **No duplicar:** reutilizar `InstallmentCalculator` y `InstallmentScheduleItem` como proyección única. Extender la entrada con condición de financiamiento/tasa/fuente/promo confirmada y una política explícita de redondeo; en confirmación persistir la decisión contractual vigente en `credit_installments` y metadatos de la transacción ya existente. Mantener `credit_payment_allocations` como fuente de pagos y saldo; el cronograma no debe contabilizar otra vez principal. Usar preview para no persistir; el comando de compra debe recibir la opción aceptada por el usuario y preservar idempotencia. Antes de contabilizar interés real, separar interés contractual verificado de interés meramente estimado.
- **Estado:** conservar un único estado de fila (`PENDING`, `PARTIAL`, `PAID`, `VOIDED` según contrato remoto) y derivar etiquetas de presentación por combinación de saldo pendiente, estado y fecha civil: `PAGADA` si saldo cero, `VENCIDA` si saldo positivo y fecha < hoy, `PRÓXIMA` en el horizonte elegido y `PENDIENTE` fuera de él. Una fecha por sí sola nunca marca pago.

## 4. KIP-03 — Simulador frente al registro de movimientos

- En registro normal, `feature/movements/presentation/QuickMovementScreen.kt` y `MovementEditorSheet.kt` usan el mismo selector de comercio `feature/categories/presentation/components/MerchantPicker.kt` (`MerchantPickerBottomSheet`) y el mismo selector de categoría `PastelCategoryPickerFlow.kt` (`PastelCategoryPickerBottomSheet`). Hay dos puntos de integración (captura rápida y edición), pero comparten componentes y `MerchantPickerViewModel`; no son dos catálogos independientes.
- `MovementReferenceSelector.kt` contiene `ReferenceSelector` para seleccionar referencias de cuenta/tarjeta en movimientos, no para comercio/categoría.
- `InstallmentSimulatorScreen.kt` no contiene selector editable de comercio ni categoría: muestra `candidate.merchant` como texto; `PurchaseCandidate.categoryId` es opcional y la pantalla no lo edita. La aceptación de promoción es una casilla local al simulador. Por tanto, hoy no hay widget duplicado allí; si KIP-03 necesita selección/edición en simulador, debe incrustar los pickers compartidos, mapear el comercio elegido a `merchantId` o texto provisional, requerir categoría de gasto y pasar ambos al comando de compra.
- Diferencias de cálculo: preview llama a `SimulateInstallments` y puede usar TEA personal o referencial; el comando real `confirmCreditPurchase()` calcula fecha mediante `CreditCalculations` pero crea cronograma principal sin tasa (`teaBps = null`, `interest_minor = 0`). Además el simulador UI limita opciones a 1/3/6/12, pese al rango de dominio 1–36. La casilla de 3 cuotas sin interés cambia el preview, pero su booleano no forma parte de `onConfirmPurchase(installments)`; confirmar no persiste esa opción. Debería existir una sola función de cálculo/fechas y un contrato de confirmación que reciba exactamente los términos aprobados, sin tratar un valor referencial como tasa contractual.

## 5. KIP-16/17/18/19 — Badge, centro, explicación y vencimientos

### Badge y centro de notificaciones

- `feature/notifications/presentation/UnreadNotificationBadge.kt` implementa campana, contador oculto en cero, `99+`, descripción semántica pluralizada y respeto de reducción de movimiento. `NotificationBadgeViewModel` observa `NotificationsRepository.observeUnreadCount()` con scope del usuario activo.
- `NotificationCenterScreen.kt` implementa filtros Todos/Presupuestos/Metas/Alertas/Recordatorios, estados vacíos, leer todo, retirar avisos leídos y retirar un aviso individual. `NotificationsViewModel` mantiene orden y aplica filtros.
- La retirada es **borrado lógico**: `AppNotificationDao.dismiss()` hace `UPDATE deleted_at`, `NotificationsRepository.dismiss()` la expone y `OfflineFirstNotificationsRepository` actualiza Room/outbox antes de sincronizar. `NotificationsApi` ofrece GET y PATCH; el contrato REST prohíbe DELETE físico y el cliente no lo implementa. El DAO sí contiene `DELETE`, pero solo para limpiar elementos de `notification_sync_outbox`, no para borrar una notificación.
- La explicación existente cubre destinos indisponibles: `UNAVAILABLE_DESTINATION_MESSAGE` indica “El elemento relacionado ya no existe o fue eliminado”; el ViewModel lo presenta como mensaje/snackbar. Para avisos `BILLING_DUE`, `NotificationDueDate` muestra la fecha prevista desde `event_payload.due_date`; conserva el cuerpo del productor si falta. No se deriva vencimiento de `created_at` ni se dice que hubo pago. No se observa una explicación contextual general de por qué se generó cada tipo de notificación.
- El alcance HU-44 de `specs/010-ep-not-notificaciones-avisos` y su `verification-report.md` documenta recordatorios locales por Room/WorkManager para deudas y recurrencias, deduplicados/reprogramables, visibles en centro aun sin permiso; son eventos locales y no se sincronizan entre dispositivos. La verificación histórica del informe corresponde a octubre de 2026.

### Desplegable y datos para próximos vencimientos

- `feature/debts/presentation/DebtListScreen.kt` ofrece “Ver vencimientos” y pasa deudas/cronogramas a `DebtDueAlertsSheet.kt`. `DebtDueAlerts.kt` calcula vencidas y próximas para obligaciones activas con saldo, prioriza cuotas `PENDING`/`PARTIAL`, agrupa vencimientos pasados y aplica ventana de alerta (7 días para la vista in-app si la deuda no especifica otra; la configuración del recordatorio del sistema es aparte). El sheet tiene filtros, sumas por moneda y navegación a deuda/pago. Solo enumera deudas y cuotas de deuda, no tarjeta de crédito.
- Datos existentes para combinar: `DebtRepository.observeDebts()` y `observeInstallments(userId, debtId)`; `DebtListViewModel` agrega `allInstallments`. Para tarjeta, `CreditDao.observeInstallmentsForCard()` y consulta `getOutstandingInstallmentsForCard()` proveen fechas, cuota, principal restante, transacción y tarjeta. `CardDao.observeActive()` provee emisor/alias. `KipuDatabase` expone ambos DAOs. `ReminderScheduleEntity` y `ReminderScheduleDao`/`ReminderEventReconciler` ya almacenan y reconcilian programación local para cuotas de deuda y recurrencias; confirmar si tarjetas quedan dentro de esa fuente antes de reutilizar, pues el informe de HU-44 los describe solo para deuda/recurrencia.
- Para una pantalla unificada, proyectar deudas y cuotas de tarjeta en un modelo de lectura común ordenado por fecha, divisa y origen, consultando los DAOs existentes; calcular saldo de cuota a partir de allocations/refunds y no sumar una cuota pagada. Mantener navegación y acciones específicas del origen, etiquetar recordatorio frente a pago y no duplicar recordatorios locales hasta definir si tarjeta se incorpora al scheduler.

## 6. Decisiones abiertas para el coordinador

1. ¿Debe prevalecer la regla actual/spec HU-13 (día de cierre inclusive) o la interpretación Qore (día 26 abre ciclo nuevo)? Validar el caso 26/06 con un estado de cuenta real y actualizar conjuntamente contrato, cálculo y ejemplos.
2. ¿La fecha base del ciclo será operación, procesamiento o configurable por tarjeta? Propuesta: parámetro por tarjeta, default operación y marca **“a validar con estado de cuenta real”**; definir cómo llenar/corregir procesamiento.
3. ¿Cómo tratar vencimientos en sábado, domingo y feriado para cada emisor? Mantener día calendario hasta disponer de una convención verificada; si se confirma ajuste, definir catálogo/regla y zona civil por tarjeta.
4. ¿La pantalla de tarjeta permitirá cuotas 1–36 como dominio/especificación o se restringirá a 1/3/6/12 por catálogo/promoción? Alinear UI, validación y pruebas de aceptación.
5. ¿Qué constituye una tasa contractual confirmada y quién puede declarar una promoción sin interés? Separar tasa personal, referencial y condición comercial; decidir qué se persiste en compra real y qué queda solo en simulación.
6. ¿Se contabiliza interés de compra en cuotas como deuda al confirmar términos, al facturarse o nunca hasta importar el estado de cuenta? La implementación actual reconoce solo principal y debe conservarse así hasta una decisión contable explícita.
7. ¿Dónde se guarda snapshot de los términos (método, TEA/TEM, fuente, redondeo, promoción) para que una edición posterior de TEA no cambie cronogramas existentes? Reutilizar transacción/cuotas y evitar una tabla paralela.
8. ¿Qué política exacta marca cuota como `PARTIAL`/`PAID` y qué horizonte significa “próxima”? Definir relación entre status remoto, allocations vigentes, refunds, vencimiento y etiquetas calculadas.
9. ¿KIP-03 necesita editar comercio y categoría en el simulador? Hoy no hay pickers allí. Si la respuesta es sí, reutilizar `MerchantPickerBottomSheet` y `PastelCategoryPickerBottomSheet` y confirmar categoría obligatoria para compra.
10. ¿Los avisos de KIP-16/17/18/19 requieren explicación por productor/tipo además del destino no disponible y la fecha prevista ya soportados? Especificar contenido y datos de origen; mantener payload no autoritativo y no inferir pago.
11. ¿La vista unificada de próximos vencimientos incluirá compras de tarjeta desde el primer alcance? Si sí, decidir si solo informa o también programa recordatorio y cómo evita duplicar el aviso HU-44.
12. ¿La regla de ventana de 7 días del sheet de deuda y la anticipación predeterminada de 2 días de HU-44 deben seguir siendo distintas? Aclarar UX para que “próximo vencimiento” no parezca un recordatorio enviado.

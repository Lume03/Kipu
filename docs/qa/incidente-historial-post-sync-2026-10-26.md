# Historial no disponible después de sincronizar — 2026-10-26 UTC

## Incidente y causa

El Owner informó que Movimientos mostraba «No pudimos cargar el historial», con contador y resumen en cero. Se reprodujo en el teléfono conectado. Los registros seguían en Room: la consulta fallaba al convertir sus filas a los modelos de presentación, y el ViewModel mostraba su estado de error. El cero visible no acreditaba ausencia de datos.

Había dos incompatibilidades:

1. Los pagos de **capital** sincronizados usan `operation_kind=DEBT_PAYMENT`, sin categoría de gasto. `Transaction` exigía categoría salvo que existiera `legacy_kind`; rechazaba esos registros canónicos válidos. Los intereses (`DEBT_AMORTIZATION`) siguen requiriendo categoría cuando son gasto.
2. La proyección remota introducida en la activación colocaba la cuenta de los ingresos en `destination_account_id`, mientras el contrato Android usa `source_account_id` también para el ingreso de una sola cuenta. Esa fila persistía y conciliaba su ledger, pero el constructor del historial la rechazaba.

**Límite de la validación anterior:** se habían comprobado persistencia, recibos, restauración y saldos, sin ejecutar esta conversión a modelos ni la lectura del historial de la cuenta real. La aprobación previa no detectó esta regresión. Se amplió expresamente esa cobertura.

## Corrección

- El modelo admite capital de deuda sin categoría, manteniendo la exigencia para gastos ordinarios e intereses.
- El mapper lee las filas de ingreso ya descargadas con el campo anterior. No necesita borrar datos ni esperar a una nueva sincronización.
- La migración aditiva `20261026031551_planning_income_account_contract.sql` corrige la proyección para las descargas futuras; se aplicó remotamente como versión `20261026031846`. Solo cambia la proyección, sin reescribir transacciones ni asientos.
- El resumen SQL se alineó con `MovementNetFlowCalculator`: excluye capital de deuda del flujo operativo y conserva el efecto del interés. Los registros permanecen visibles y contados en el historial.
- El ViewModel registra la clase de excepción, sin contenido financiero, si vuelve a fallar una lectura.

## Verificación

- **RED:** cuatro pruebas JVM nuevas, dos fallidas antes de corregir el mapper/modelo. **GREEN:** las cuatro pasan; también comprueban que gasto ordinario e interés sin categoría sigan siendo rechazados.
- **SQL:** dos aserciones nuevas de ingresos fallaron con la proyección anterior. Después de la migración, las diez aserciones de `planning_transaction_pull_projection_test.sql` pasan en la base local aislada.
- **Room en teléfono:** lectura de capital pagado/cobrado e interés, con comprobación de que solo el interés cambie el neto operativo: PASS.
- **Cuenta real:** `connectedAccountHistoryLoadsThroughThePresentationUseCase`, optativa y de lectura, pasa con el mismo caso de uso que consume el ViewModel.
- **Restauración real ampliada:** el test existente descarga a Room vacía y ahora convierte también las filas restauradas al modelo de historial. Mantiene la comparación de datos y saldos: PASS.
- **Pantalla real:** contador «16 movs», sin error. Verificación manual de filtros Gastos, Ingresos, Transferencias y Todos; evidencia local ignorada por privacidad.
- Build debug/AndroidTest y `lintDebug`: PASS. No se ejecutó la suite global ni se borró/reinstaló la cuenta.

**Veredicto: APPROVED para la corrección de este incidente**, con los límites generales de la [activación](activacion-sync-planificacion-2026-10-26.md). Las capturas y logs técnicos permanecen en `scratch/planning-review/remote-audit` y `scratch/planning-review/history-fixed-device.png`, fuera de Git. La sesión y los registros del teléfono se conservaron.

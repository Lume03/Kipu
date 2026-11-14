# FIX-HISTORY - historial tras reembolso sincronizado

Fecha: 2026-11-14. Rama: `Lume03/audit-fixes`. Correccion local, sin commits ni ejecucion en telefono.

## Reproduccion y traza real

Test JVM Robolectric SDK 34 con Room en memoria y DAOs reales: `CardPurchaseVoidAndRefundRoomTest.syncedCardRefundHistoryAndDetailsSurviveCanonicalFeed`.
Se persiste una compra `CARD_PURCHASE` de 400 centimos, una cuota, un reembolso `INCOME/REFUND` de 100 centimos con la misma tarjeta y el vinculo `transaction_links`. Ambos estan sincronizados; despues se aplica el feed real mediante `MovementHydration.hydrateFully`, con cuenta de pasivo como `source_account_id` y sin `card_id`, conservando la tarjeta local.

La primera ejecucion, antes de modificar produccion, termino con exit code 1 y esta traza (lineas de la version anterior al arreglo):

```text
java.lang.IllegalArgumentException: Refund income requires exactly one original account or credit card
    at com.kipu.app.feature.movements.domain.model.Transaction.<init>(MovementModels.kt:120)
    at com.kipu.app.feature.movements.data.local.TransactionMappersKt.toDomain(TransactionMappers.kt:8)
    at com.kipu.app.feature.movements.data.OfflineFirstMovementRepository.buildTransactionItem(OfflineFirstMovementRepository.kt:292)
    at com.kipu.app.feature.movements.data.OfflineFirstMovementRepository.mapEntitiesToItems(OfflineFirstMovementRepository.kt:279)
```

## Causa

La proyeccion de feed de `20261108173837_canonical_transaction_revision_pull.sql` trata los reembolsos por la proyeccion de planificacion y expone `t.account_id` como origen. `MovementHydration.updateTransactionFromRemote` actualiza esa cuenta y conserva el `cardId` local. La fila acaba con tarjeta y cuenta de pasivo. El mapper copiaba ambos campos al dominio, cuyo invariante L2 exige un solo instrumento. La excepcion de una fila invalidaba la pagina completa; no la causan las consultas en lote de bloqueos ni `FAILED_PERMANENT`.

## Arreglo

- `TransactionMappers`: para `INCOME/REFUND` con tarjeta, representa el instrumento del dominio mediante `cardId`; la cuenta de pasivo del feed queda como detalle de persistencia/ledger. Tambien repara la lectura de filas que ya estaban hidratadas, sin otra sincronizacion ni migracion.
- Se conservan las validaciones de `Transaction`, la elegibilidad y fecha L2, los importes, los enlaces, las cuotas y el ledger; no hay escritura financiera adicional.
- `OfflineFirstMovementRepository`: los listados paginados y observados omiten una fila cuyo mapeo lance `IllegalArgumentException`, y registran un aviso fijo sin identificadores, valores ni mensaje de excepcion. No se capturan cancelaciones ni errores de consulta de Room. El cursor sigue avanzando por filas persistidas, incluso cuando una pagina queda vacia por omisiones.
- El detalle `getHistoryItemById` devuelve `null` si la fila se omite, mediante `singleOrNull`, en lugar de lanzar otra excepcion.

## Cobertura

- Reproduccion: consulta `QueryMovementHistory`, resumen sin paginacion y busqueda textual despues de hidratar; aparecen ambos movimientos, el resumen conserva 2 filas, el reembolso no se cuenta como ingreso operativo y la deuda queda en 300 centimos.
- Detalle del reembolso: tarjeta original y origen de cuenta nulo en el dominio; funciona tambien `MovementRepository.getTransactionById`.
- Detalle de compra: conserva bloqueo `PURCHASE_HAS_ACTIVE_REFUNDS`.
- `malformedRowDoesNotHideHistoryOrBreakPagingAndDetail`: enum `captureSource` inesperado en Room; pagina vacia con cursor valido, siguiente pagina con compra valida, detalle invalido nulo y listado observado con la compra intacta.

## Verificacion

`./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` termino con `BUILD SUCCESSFUL in 1m 42s` (48 tareas: 6 ejecutadas, 42 actualizadas).

Resultados XML: 1756 tests, 0 fallos, 0 errores, 1 omitido; la clase `CardPurchaseVoidAndRefundRoomTest` tiene 9 tests y 0 fallos. `git diff --check` sin errores.
No se ejecuto `adb`, instalacion ni prueba sobre el telefono. No se modifico `PRUEBAS-A16.md`.

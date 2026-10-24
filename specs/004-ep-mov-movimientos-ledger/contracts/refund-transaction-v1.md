# Contrato: reembolso vinculado v1

**Estado**: Aprobado para implementación S7 (2026-10-24). La revisión independiente de Arquitectura, Finanzas y Seguridad permanece pendiente en T149.
**Alcance**: EP-MOV, HU-25, FR-023–FR-028.
**Autoridad de usuario/decisiones**: respuestas S7-01–S7-04 de `team-questions.md` y encargo de implementación S7.

## Invariantes

1. El reembolso es una transacción positiva `INCOME` con `operation_kind=REFUND`; G/I/T no cambia y REFUND no es ingreso operativo.
2. Sólo se reembolsan gastos propios confirmados y vigentes, no anulados ni rechazados permanentemente, pagados en efectivo, débito o tarjeta de crédito sin cuotas. Se excluyen cuotas, transferencias, principal de deuda y movimientos especializados de deuda/metas.
3. El saldo es `importe_compra - suma(reembolsos válidos vinculados)`. El importe solicitado debe ser positivo, de la misma moneda y no superar el saldo. Dominio y servidor validan; el servidor es autoridad entre dispositivos.
4. El destino se deriva del origen: la cuenta de la compra para efectivo/débito o la misma tarjeta para compra a crédito. El cliente no aporta ni elige otro destino.
5. La relación única es `transaction_links.link_type=REFUND`, compra como `source_transaction_id`, reembolso como `target_transaction_id`. No se escribe `transactions.refund_of_transaction_id`.
6. La fecha del reembolso afecta caja; gasto neto y consumo se atribuyen a fecha y categoría originales. No se modifica la compra.
7. Rechazo remoto por remanente agotado conserva el reembolso local visible con motivo seguro. La persona puede anularlo por ruta especializada; jamás se borra silenciosamente.

## Comando `refund_transaction_v1`

La API expone `POST /rest/v1/rpc/refund_transaction_v1` con `p_command`:

```json
{
  "contract_version": 1,
  "idempotency_key": "uuid estable del comando",
  "request_hash": "sha256 hexadecimal minúsculo",
  "purchase_transaction_id": "uuid de la compra",
  "refund_transaction": {
    "id": "uuid estable del reembolso",
    "amount_minor": 4000,
    "currency_code": "PEN",
    "occurred_at": "2026-10-24T15:00:00.000Z"
  }
}
```

`user_id`, `source_account_id` y `card_id` no son campos del comando: se derivan de la sesión autenticada y de la compra validada. El movimiento REFUND deja `category_id=NULL` porque el catálogo rechaza categorías EXPENSE en movimientos INCOME. La consulta de consumo deriva la categoría desde la compra enlazada. `occurred_at` se limita a milisegundos. El hash cubre exactamente la cadena UTF-8 `refund-v1\n<purchase UUID en minúsculas>\n<refund UUID en minúsculas>\n<amount_minor decimal>\n<currency_code en mayúsculas>\n<epoch_millis decimal>`; el resultado es SHA-256 hexadecimal minúsculo. Las referencias son UUID y el código de moneda ISO de tres letras, por lo que los separadores no son ambiguos.

### Validación y serialización del servidor

- Exige sesión autenticada; deriva `user_id` de `auth.uid()` y rechaza parámetros adicionales de propietario.
- Reproduce el hash y valida versión, UUID, importe positivo y timestamp.
- Recupera primero el recibo por `(user_id, idempotency_key)`: mismo hash devuelve el resultado guardado sin nuevos efectos; hash distinto devuelve `CONFLICT / IDEMPOTENCY_KEY_REUSED`.
- Adquiere lock transaccional por usuario+compra y bloquea la fila de compra. Verifica que ambas operaciones sean del propietario, que la compra sea `EXPENSE` confirmada/vigente y elegible, que cuenta/tarjeta de origen siga siendo la asociada, que moneda coincida y que el importe no exceda el remanente válido.
- En una sola transacción crea el movimiento REFUND, su asiento al instrumento original, el vínculo orientado compra→reembolso y el recibo. La unicidad del target impide un segundo vínculo para el mismo reembolso.
- `transaction_links` conserva RLS de lectura por propietario. `authenticated` no recibe `INSERT`, `UPDATE` ni `DELETE`; las escrituras ocurren dentro de la RPC privilegiada con `search_path` fijo y validaciones explícitas. No se añade política que permita DML financiero directo.

### Respuesta

```json
{
  "status": "APPLIED | DUPLICATE | CONFLICT | REJECTED",
  "transaction_id": "uuid del reembolso si fue aceptado",
  "receipt_id": "uuid del recibo",
  "error": { "code": "REFUND_EXCEEDS_REMAINDER", "field": "amount_minor", "retryable": false }
}
```

`APPLIED` y `DUPLICATE` llevan el mismo `transaction_id` y recibo. `CONFLICT` no añade efectos. `REJECTED` usa códigos estables y no incluye importes, notas, nombres de comercio, tokens ni SQL. Códigos no reintentables: `INVALID_COMMAND`, `PURCHASE_NOT_FOUND`, `PURCHASE_NOT_ELIGIBLE`, `CURRENCY_MISMATCH`, `SOURCE_CHANGED`, `REFUND_EXCEEDS_REMAINDER`, `IDEMPOTENCY_KEY_REUSED`. Fallos transitorios de red/servidor conservan payload y clave en outbox y se reintentan.

## Persistencia Android y reconciliación

- El comando se guarda como outbox `command_type=CREATE_REFUND`, `contract_version=1`, con payload serializado inmutable; no modifica bytes ni hashes de `register_transaction_v1/v2`.
- Room confirma en una transacción movimiento, vínculo, ledger, proyección, recibo y outbox. La idempotencia local usa el mismo hash. El rechazo remoto marca el outbox `FAILED_PERMANENT`, recibo `REJECTED` y transacción local `sync_status=FAILED_PERMANENT`; conserva hechos y vínculo. El gasto neto no descuenta ese intento.
- Anular un rechazo es una acción especializada local: cambia el estado del reembolso a `VOIDED` y agrega contrapartida al ledger/proyección dentro de una transacción; no entra en las rutas genéricas de editar/anular ni toca la compra. Mantiene el recibo, enlace y outbox rechazado como auditoría.
- Los mensajes visibles traducen códigos estables. Logs técnicos no contienen monto, comercio, nota, cuenta, tarjeta, payload, hash ni tokens.

## Consulta de gasto neto y EP-PRE

`ExpenseConsumptionRepository.queryConsumption` conserva su contrato. Su implementación local resta de cada gasto elegible los importes de reembolsos válidos vinculados y usa la fecha/categoría del gasto origen. Un reembolso parcial de S/40 sobre compra S/100 deja S/60; uno total deja S/0; un reembolso de octubre a compra de septiembre ajusta septiembre y no el gasto de octubre. EP-PRE/HU-36 obtiene ese resultado de la consulta compartida. EP-MOV no crea adaptador, contador ni reglas presupuestarias. La prueba integrada contra el provider real de HU-36 queda fuera de este contrato local y pendiente de T144.

## Estado y verificación

Este contrato cierra el gate documental para implementar S7. T149 conserva la revisión cruzada. Las migraciones Room/Postgres son aditivas y locales; este contrato no afirma que se hayan ejecutado, aplicado a remoto ni que el provider de EP-PRE esté integrado.

# Contrato de comandos de metas

**Estado**: contrato de planificación para HU-30, HU-31 y HU-32; reactivación atómica revisada por Arquitectura el 2026-10-18; implementación local pendiente en T013/T015/T043/T044–T047. Los nombres son tipos conceptuales del contrato, no nombres definitivos de RPC.

**Architecture decision**: Keep `UPDATE_GOAL` and `RELEASE_GOAL`; add optional `replacement_goal_id` and `replacement_expected_revision` fields to each canonical request/hash. Require them only when a Free user's operation would reactivate a `COMPLETED` goal while already at the two-`ACTIVE` limit. Apply the initiating operation and both goal transitions atomically; older clients without the fields fail safely with `REJECTED/REPLACEMENT_REQUIRED`. Do not implement the former single-goal behavior for these cases.

## Reglas comunes

- El usuario autenticado es el propietario; el cliente no puede elegir `user_id`.
- Los importes son enteros de unidades menores más código de moneda. Nunca enviar/aceptar floats.
- Cada escritura incluye `operation_id` estable, `request_hash` del contenido canónico y versión del contrato acordada con la envoltura de sync actual. Comandos sobre objetos existentes incluyen `expected_revision`.
- El servidor deriva el hash del payload normalizado y comprueba su correspondencia. Clave única por `(user_id, operation_id)`; mismo ID/hash devuelve el recibo previo y mismo ID/hash distinto devuelve conflicto.
- Toda meta, cuenta, evento y referencia de movimiento se valida como propia y de moneda compatible. RLS protege lecturas y escrituras de tablas expuestas; el RPC repite comprobaciones de propiedad y revisión.
- La escritura de evento, posible transferencia, recibo y cambio de feed es atómica. Fallas reintentables no dejan efectos parciales.
- Operaciones offline se persisten primero en Room con outbox. La app puede informar “guardado local / pendiente de sincronizar”; no presenta el rechazo remoto como confirmado. Una discrepancia de revisión, saldo o límite se muestra al usuario para resolverla.
- Los comandos que pueden alterar el cupo/estado (`CREATE_GOAL`, `UPDATE_GOAL`, `RELEASE_GOAL`) toman primero el advisory lock de cuota por usuario; después bloquean cuentas de respaldo en orden UUID ascendente y filas de metas (origen y reemplazo) en orden UUID ascendente. Validan ownership, estado y revisiones antes de escribir. Los comandos de reserva siguen serializando por cuenta antes de comprobar saldo libre; ningún comando debe adquirir posteriormente un lock anterior de cuota.
- Cuando un comando afecta dos metas, persiste un único recibo idempotente compuesto con el evento desencadenante, ambas revisiones/secuencias y ambas proyecciones de estado; publica los cambios de ambas metas al feed en la misma transacción. La reconciliación local actualiza ambas metas y la outbox de un solo comando en una transacción Room.

## `CREATE_GOAL`

### Solicitud

| Campo | Requerido | Regla |
| --- | --- | --- |
| `operation_id`, `request_hash`, versión | sí | Identidad idempotente y contrato soportado. |
| `goal_id` | sí | UUID estable del cliente. |
| `name` | sí | Texto válido tras normalización. |
| `target_minor`, `currency_code` | sí | Objetivo positivo y moneda soportada. |
| `target_date` | no | Fecha opcional. |
| `linked_account_id` | no | Si se informa, cuenta propia compatible. Si no, la meta inicia sin cuenta real vinculada. |
| datos visuales | no | Solo campos permitidos por el modelo, sin efecto financiero. |

### Resultado

`APPLIED`/`DUPLICATE` con `goal_id`, revisión, estado y resumen. Crear no crea evento, reserva, transferencia ni movimiento. El límite Free de dos metas activas se valida bajo exclusión en el servidor y un rechazo no elimina datos existentes.

## `UPDATE_GOAL`

- Recibe `goal_id`, `expected_revision`, `operation_id`/hash y los campos modificables.
- Opcionalmente recibe `replacement_goal_id` y `replacement_expected_revision`; ambos forman parte del payload canónico y su hash. Son obligatorios solo cuando el cambio de objetivo reactivaría una meta `COMPLETED` de un usuario Free que ya tiene dos metas `ACTIVE`.
- Nombre, apariencia, fecha u objetivo no alteran reserva ni eventos.
- Cambiar cuenta enlazada con reservas pendientes exige primero resolverlas; un conflicto de revisión no sobreescribe.
- Al aumentar el objetivo de una meta completada por encima de la reserva, cambia a ACTIVE conservando reservas/eventos. Si el nuevo objetivo sigue alcanzado, permanece COMPLETED.
- Cuando se exige reemplazo, validar que la meta elegida pertenece al usuario, sigue `ACTIVE` y su revisión coincide; bloquearla como `LOCKED_BY_PLAN` y reactivar la fuente en la misma transacción, junto con un único recibo y entradas de feed/revisión para ambas metas. Ausencia de selección rechaza sin cambiar nada; cancelar en UI no crea outbox.
- Al reducir el objetivo de una meta activa a un importe `<= current_reserved`, cambia inmediatamente a COMPLETED conservando íntegras su reserva e historia sin mover dinero.
- Respuesta incluye revisión/estado recalculados; si hubo reemplazo, incluye ambas metas y revisiones/secuencias en el mismo recibo.
- Implementación SQL local: `20261018190200_ep_met_active_goal_quota_atomic_replacement.sql` conserva las firmas RPC v1 y los campos de respuesta `goal_id`, `revision` y `sequence`; la extensión `payload.replacement_goal_id` + `payload.replacement_expected_revision` es opcional para ediciones ordinarias y forma parte del hash canónico. Si un cliente antiguo omite el selector cuando se requiere, recibe `REJECTED/REPLACEMENT_REQUIRED` sin escritura. Cuando se usa reemplazo, la respuesta agrega `goals: [{goal_id, status, revision, sequence}, ...]` y sigue incluyendo los campos legacy de la meta editada; debe desplegarse junto con un cliente que reconcilie ambas metas atómicamente.

## `RESERVE_GOAL`

### Solicitud

| Campo | Requerido | Regla |
| --- | --- | --- |
| `operation_id`, `request_hash`, versión | sí | Idempotencia/versionado. |
| `event_id`, `goal_id`, `expected_revision` | sí | IDs estables y revisión que vio el cliente. Meta propia activa. |
| `amount_minor`, `currency_code` | sí | Importe positivo; debe coincidir con meta/cuenta. |
| `backing_account_id` | sí, directo o derivable | Cuenta real propia que respalda la reserva. Si la meta tiene cuenta enlazada se puede usar como predeterminada; sin vínculo, el usuario elige una cuenta en este comando. Una meta puede recibir aportes de múltiples cuentas en la misma moneda. |
| `transfer` | no | Presente solo si el usuario confirma un traslado interno a otra cuenta. Requiere `source_account_id` y `destination_account_id`; destino coincide con `backing_account_id`. |

### Comportamiento

- Reserva directa: bloquear cuenta de respaldo (`FOR UPDATE`); recalcular saldo confirmado (excluyendo pending/voided) menos reservas de la cuenta; rechazar íntegramente si el saldo libre es insuficiente (`REJECTED/INSUFFICIENT_BACKING`). Crear evento sin transacción ni asiento de ledger.
- Con `transfer`: validar propiedad, moneda, saldo y elegibilidad de origen/destino; ejecutar el comando de transferencia existente y registrar evento usando el destino como cuenta de respaldo, todo atómicamente en una sola transacción. Si cualquiera de los dos efectos falla, se revierte todo. No crear gasto/ingreso ni ejecutar transferencia bancaria externa.
- Origen=destino no es un traslado: tratarlo como reserva-only y no crear movimiento. El cliente puede enviar la cuenta como respaldo sin bloque `transfer`; si envía ambos IDs iguales, normalizar a reserva-only después de validar la cuenta y devolver el resultado sin una transferencia.
- Actualizar revisión de meta y publicar `GOAL`/evento en feed con cursor vigente. El saldo real solo cambia en transferencia explícita.

### Respuesta

`APPLIED`/`DUPLICATE` con evento, revisión, importe reservado de esa meta/cuenta, saldo confirmado, saldo libre y referencia de transferencia nula o existente. No exponer datos de otro propietario.

## `RELEASE_GOAL`

- Solicitud: `operation_id`, hash/version, `event_id`, `goal_id`, `expected_revision`, `backing_account_id`, importe positivo y moneda; opcionalmente `replacement_goal_id` + `replacement_expected_revision`, incluidos en el payload canónico/hash.
- La liberación no supera la reserva actual de esa meta en esa cuenta. Se rechaza por completo si el monto es excesivo.
- Agrega evento de reducción y aumenta saldo libre derivado. No crea `INCOME`, gasto, transacción ni asiento, aunque la meta tenga una cuenta enlazada.
- Si la meta estaba COMPLETED y queda bajo su objetivo, pasa a ACTIVE. Si el usuario Free ya tiene dos metas `ACTIVE`, exige el reemplazo explícito; verifica propiedad/estado/revisión de la elegida, bloquea esa meta y aplica evento RELEASE + ambas transiciones en una sola transacción. Sin selección, o con selección stale/no propia/no activa, no persiste evento ni cambia estado/reserva.
- Una liberación que no reactive la meta o tenga cupo libre no requiere reemplazo. Cancelar no crea evento ni outbox. Un cliente antiguo que no envía selección falla de forma segura cuando la cuota lo requiere.
- Si hay reemplazo, el recibo único incluye el evento y ambas metas/revisiones/secuencias; ambas metas se publican en el feed y se reconcilian localmente de forma atómica.
- Transferir los fondos después de liberar es otro comando financiero y requiere confirmación explícita en el flujo existente.

## `REASSIGN_GOAL_RESERVE`

- Recibe meta origen/destino, cuenta de respaldo, importe positivo y revisiones de ambas metas.
- Requiere propiedad común, moneda idéntica y reserva disponible suficiente en origen. Registra la salida/entrada de reserva de manera atómica y conserva una pista de auditoría.
- Si no cambia la cuenta que respalda el dinero, no crea movimiento en ledger. Si el respaldo debe cambiar, no simula un traslado: exige un flujo de transferencia explícito según reglas aprobadas.
- Si es parte del archivo, se reasigna primero y el archivo solo se completa cuando la reserva restante sea cero.

## `ARCHIVE_GOAL` y `DELETE_EMPTY_GOAL`

- `ARCHIVE_GOAL` recibe revisión, operación estable y meta propia. Solo acepta reserva vigente igual a cero (`reserved == 0`). Si `reserved > 0`, la UI guía al usuario a liberar a saldo libre, reasignar a otra meta activa o cancelar el archivo. Conserva la meta y toda la historia; no crea movimiento.
- `DELETE_EMPTY_GOAL` requiere confirmación en UI, cero eventos, ninguna transferencia vinculada y cero operaciones pendientes en el outbox local. Cualquier historia o comando pendiente responde `REJECTED/HISTORY_PRESERVED`; la FK no debe borrar eventos en cascada.
- Ninguno de los dos comandos omite el límite/ownership check ni permite escritura directa para evitar auditoría.

## Respuestas y errores estables

| Estado/código | Significado | Efectos persistidos |
| --- | --- | --- |
| `APPLIED` | Comando aplicado una vez. | Sí, completo y atómico. |
| `DUPLICATE` | Mismo usuario, operación e idéntico hash ya aplicada. | No se repiten; se devuelve recibo anterior. |
| `CONFLICT/REVISION_MISMATCH` | La entidad cambió desde la revisión observada. | Ninguno para este intento; cliente muestra opción de recargar/reintentar. |
| `CONFLICT/OPERATION_ID_REUSED` | ID previamente asociado a otro hash/payload. | Ninguno para este intento; cliente genera nuevo ID. |
| `REJECTED/INSUFFICIENT_BACKING` | Saldo libre insuficiente. | Ninguno; sin parcialidad. Cliente ofrece ajustar monto o fondear cuenta. |
| `REJECTED/RESERVE_EXCEEDED` | Liberación o reasignación excede reserva vigente. | Ninguno. |
| `REJECTED/OWNER_OR_ACCOUNT_INVALID` | Meta/cuenta no propia, no disponible o incompatible. | Ninguno; respuesta no filtra existencia de objetos ajenos. |
| `REJECTED/GOAL_LIMIT` | La operación violaría HU-57 / P31. | Ninguno; metas existentes quedan intactas. |
| `REJECTED/REPLACEMENT_REQUIRED` | La reactivación al límite Free requiere seleccionar una meta `ACTIVE` para bloquear; también aplica a clientes antiguos que no envían el nuevo campo opcional. | Ninguno; sin evento, reserva ni cambio de estado. |
| `REJECTED/RESERVE_UNRESOLVED` | No se puede cambiar cuenta/archivar con reserva activa. | Ninguno. |
| `REJECTED/HISTORY_PRESERVED` | Borrado físico de meta con historia o comandos en outbox. | Ninguno; conservar historia. |
| `RETRYABLE` | Falla transitoria antes del commit. | Ninguno; el mismo comando puede reintentarse con backoff. |

Cada respuesta aplicada publica cambio con revisión real y cursor/feed. Vista de resumen y pull deben leer bajo identidad autenticada y respetar RLS. Operaciones offline rechazadas en sync muestran una insignia de conflicto con el motivo y permiten descartar, corregir o resolver la precondición. Una reversión/corrección posterior es una nueva operación auditable, nunca una modificación silenciosa del recibo anterior.

## Seguridad de funciones de base de datos

Preferir funciones `SECURITY INVOKER`. Si la implementación requiere `SECURITY DEFINER`, fijar `search_path`, restringir `EXECUTE` a roles necesarios, revisar propiedad/permisos y validar `auth.uid()` más cada referencia de cuenta/meta. Reconciliar los grants directos actuales antes de cerrar el contrato; el SQL local no prueba los ACL desplegados.

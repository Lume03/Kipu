# Contrato candidato: reembolso vinculado v1

**Estado**: BORRADOR de diseño — no aprobado para implementación
**Alcance**: EP-MOV, Sprint 7, HU-25 / FR-023–FR-025
**Fuente funcional**: `spec.md`, `docs/producto/backlog.md` (HU-25)
**Última revisión estática**: 2026-10-21

> Este documento conserva los invariantes funcionales aprobados y registra la propuesta de frontera técnica. No define todavía un payload/API ejecutable: primero deben resolverse las decisiones abiertas y obtenerse el contrato real de EP-PRE/HU-36.

## Invariantes funcionales

1. Sólo se devuelve dinero contra una compra propia confirmada y con saldo reembolsable.
2. Cada reembolso es parcial o total, queda vinculado al origen y la suma válida no puede superar el importe reembolsable pendiente.
3. La caja se afecta en la fecha efectiva del reembolso; el gasto neto y el presupuesto se corrigen en el periodo de la compra original.
4. El reembolso no crea ingreso operativo, no modifica ni elimina el hecho de compra original y no duplica saldo, gasto ni presupuesto.
5. La sesión autenticada determina al propietario. El cliente no puede elegir `user_id` ni vincular transacciones de distintos propietarios.

## Hallazgos del esquema y cliente (inspección local, no despliegue)

- La migración base `supabase/migrations/20260920000000_financial_core_baseline.sql` incluye `operation_kind=REFUND` y la columna nullable `transactions.refund_of_transaction_id` con `ON DELETE SET NULL`.
- `supabase/migrations/20260920162500_align_domain_schema_names.sql` incluye `transaction_link_type=REFUND` y `public.transaction_links`. La misma migración declara RLS por `user_id` y concede `SELECT/INSERT/UPDATE/DELETE` a `authenticated`; el enlace no tiene en esa definición una FK compuesta que demuestre que ambas transacciones pertenecen al mismo usuario.
- Existe una segunda representación potencial del vínculo (`refund_of_transaction_id`). La investigación aprobada de EP-MOV describe `transaction_links` para relaciones entre operaciones independientes. Para S7 se recomienda usar un único vínculo canónico `transaction_links` con `link_type=REFUND`, y no escribir ambos mecanismos; Seguridad/Arquitectura debe revisar la autorización, ownership y retención antes de aprobarlo.
- En el código actual de movimientos, `MovementModels.Transaction`, `MovementEntities.TransactionEntity` y `MovementDtos.TransactionDto` no modelan este vínculo ni existe un `TransactionLinkEntity`. La rama observada usa `KipuDatabase` versión 25; las referencias v16/v17 de S4 son históricas, no el origen de una migración S7.
- La RPC genérica de revisión trata un `refund_of_transaction_id` no nulo como operación especializada. No se debe reutilizar edición/anulación genérica para registrar un reembolso.
- Estas son observaciones de archivos versionados; no demuestran el esquema remoto desplegado.

## Dirección técnica recomendada, pendiente de aprobación

- Mantener los tipos principales existentes `EXPENSE`, `INCOME` y `TRANSFER`; representar el flujo de efectivo entrante como un movimiento `INCOME` con `operation_kind=REFUND`, excluyéndolo expresamente de métricas de ingreso operativo y relacionándolo una sola vez con la compra original mediante `transaction_links`.
- Como candidata de orientación del vínculo, usar la compra original como `source_transaction_id` y el reembolso como `target_transaction_id`, siguiendo el patrón de operación→operación relacionado; confirmarlo en el diseño final.
- Crear el reembolso con un comando financiero versionado propio, sin reinterpretar ni cambiar los bytes de `register_transaction_v1/v2` ni los contratos de corrección/anulación existentes.
- El saldo reembolsable se valida autoritativamente en servidor, serializando por compra original dentro de la misma transacción que comprueba el recibo y registra el reembolso. Replays idénticos devuelven el recibo sin nuevos efectos; una clave reutilizada con otro hash no aplica efectos.
- En local-first, la confirmación local, su ledger/proyección, recibo y outbox deben persistir atómicamente. Los reintentos conservan identidad/hash/payload. Una respuesta remota de exceso/conflicto no puede borrar la compra original ni generar una segunda compensación; su resultado y resolución visible deben cerrarse con Producto/Finanzas antes del código.
- El contrato debe impedir DML de cliente que permita inventar, cambiar o borrar enlaces financieros; validar que compra, reembolso, cuenta y usuario autenticado coincidan y preservar historial sin `DELETE` físico. La estrategia concreta (RPC, grants, constraints y migración) requiere revisión de Seguridad/DB.

## Frontera con EP-PRE

EP-MOV registra el hecho de devolución y su efecto de caja. EP-MOV no implementa la administración de presupuestos ni define por sí sola cómo el proveedor de EP-PRE actualiza el consumo de la compra en su periodo original. La interfaz de ajuste debe acordarse con el contrato real de HU-36; una fixture o un stub no cierra la aceptación integrada de HU-25. Hasta que ese contrato exista, puede avanzarse en slices independientes de EP-MOV después de aprobar el diseño, pero no declararse completa la conciliación entre periodos.

## Decisiones que bloquean el contrato ejecutable

1. Qué clases/estados de compra son elegibles además de la compra propia confirmada: compras estándar, tarjeta/cuotas, relaciones con deuda y compras corregidas/anuladas.
2. A qué cuenta o instrumento se devuelve el efectivo y si se permite seleccionar un destino distinto al de la compra.
3. Qué estado y acción ofrece la UI si otra operación offline consume el saldo reembolsable antes de que el servidor acepte el comando, considerando que la devolución puede haber ocurrido fuera de Kipu.
4. Qué versión, identidad de consumo, resultado idempotente y manejo de rechazo usa el contrato de EP-PRE para corregir el periodo original.

## Verificación prevista

- Pruebas de dominio de devolución parcial, total, exceso, propiedad, moneda, vínculo único, replay exacto y misma clave/hash distinto.
- Pruebas de persistencia/migración Room desde la versión vigente de la rama, atomicidad de ledger/proyección/recibo/outbox y recuperación tras reinicio.
- pgTAP de propiedad de ambos movimientos, límite concurrente por compra, grants/RLS, rollback, unicidad y cero DML destructivo.
- Pruebas de sincronización offline/reintento/conflicto y prueba integrada con el proveedor real de EP-PRE para la reasignación al periodo original.
- Todas son criterios futuros. No se ejecutaron ni se usaron para afirmar aceptación en este refinamiento.

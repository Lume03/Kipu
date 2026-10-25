# Incidente: los datos no se restauran tras reinstalar (2026-10-25)

## Clasificación
- **Tipo:** defecto confirmado de sincronización (cliente) + migraciones remotas sin desplegar.
- **Alcance:** cuentas, tarjetas y movimientos (corregido en cliente); deudas y metas (bloqueadas por backend).
- **Datos:** solo agregados. Sin IDs de usuario, tokens ni payloads. Investigación en solo lectura: no se escribió en Supabase ni se borraron datos locales.

## Observado
Tras desinstalar y volver a iniciar sesión, la app quedó vacía. En el servidor sí existían las cuentas, tarjetas y transacciones del usuario (cuentas, tarjetas y transacciones, con decenas de cambios en `internal.sync_changes`). En Room, `movement_sync_checkpoints` estaba vacío: el *pull* nunca había completado una página. Los logs de API mostraban **404 en cada llamada** a `pull_financial_changes_v1`.

## Causas raíz (cadena, en orden de aparición)
1. **Parámetros RPC omitidos.** `SupabaseNetworkModule` no codifica valores por defecto; `PullChangesRequestDto` salía como `{"after_sequence":0}`. PostgREST no encuentra la firma `(contract_version, after_sequence, limit)` y responde 404. *Corrección:* `@EncodeDefault` en ese DTO.
2. **Cursor contiguo.** Movimientos y deudas exigían `sequence == anterior + 1`, pero la secuencia es global entre usuarios (`docs/arquitectura/modelo-de-datos.md`: «secuencia global monotónica creciente»). *Corrección:* `SyncFeedCursor.isValidPage` (estrictamente creciente y `next_sequence` = último), como ya hacía Metas.
3. **Tipos ajenos.** El worker de movimientos revertía la página ante `CATEGORY`/`BUDGET`. *Corrección:* se omiten solo tipos conocidos de otros syncs; un tipo futuro desconocido sigue revirtiendo sin avanzar el cursor.
4. **Dos formas de ACCOUNT.** El feed emite la instantánea de comando (`alias/type/currency`) y la de fila (`name/account_type/currency_code`). *Corrección:* `@JsonNames` en `SyncAccountPayload`.
5. **Fechas con offset.** `Instant.parse` en Android rechaza `+00:00`. *Corrección:* `parseFeedInstant` con `OffsetDateTime`, como ya hacían categorías y planes.
6. **Anulación remota.** `operation_kind = STANDARD` (nulo en local) bloqueaba la revisión 2, y la anulación llega con `ledger_effects` sin `ledger_entries`. *Corrección:* normalizar `STANDARD` y compensar idempotentemente el neto restante por cuenta, como una anulación local.

## Deudas y metas
Las RPC de deudas (`open_debt_v1`, `settle_debt_v1`, …) y de metas **no existen en el servidor**: sus migraciones locales (`20261008*_s5_debt_*`, `20261017*_ep_met_goal_*`, `20261018*_ep_rec_*`, entre otras) no están desplegadas. Lo creado en deudas solo vivía en Room y se pierde al desinstalar. En cliente: el 404 de deudas pasa a reintentable (misma política que metas), los comandos antes marcados `FAILED_PERMANENT` por `HTTP_404` vuelven a la cola, y el sync de deudas y metas se programa al abrir sesión. **Pendiente (requiere decisión del Owner):** desplegar esas migraciones; no se desplegó nada en esta investigación.

## Verificación
- Servidor vs. dispositivo tras la corrección: los saldos (apertura + libro mayor) coinciden al céntimo; mismo número de cuentas, tarjetas y transacciones; checkpoint en la última secuencia del usuario.
- La pequeña diferencia frente al saldo previo a reinstalar corresponde a movimientos locales que nunca llegaron al servidor; no son recuperables.
- Pruebas: `SyncFeedCursorTest`, `SyncAccountPayloadTest` (unitarias); `SyncMovementsWorkerTest` (restauración con huecos, tipos ajenos, ACCOUNT en forma de fila y anulación remota), `DebtChangeFeedTest`, `DebtOutboxRecoveryTest`, `GoalReplacementRoomIntegrationTest` (instrumentadas).

## Relación
Responde en parte a [INV-sync-outbox-pending](../auditoria-sprint-6-2026-10-19/issues/INV-sync-outbox-pending.md): los comandos de deuda no avanzan porque sus RPC no están desplegadas.

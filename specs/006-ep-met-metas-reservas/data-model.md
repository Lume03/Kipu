# Modelo de datos — EP-MET

**Estado**: modelo local implementado y probado en sandbox aislado; la validación de datos alojados y cualquier activación siguen bloqueadas por T040.

## Principios

- Los importes de meta y evento son enteros de unidades menores con moneda, representables por el tipo de dominio `Money` (`Long` + currency). Nunca usar `Float`/`Double` para cálculos financieros.
- `goals` y `goal_events` siguen siendo la fuente persistida de metas y reservas. No se crea una cuenta de activo virtual ni una segunda entrada de patrimonio.
- El saldo de cuenta se deriva del ledger confirmado. La reserva reduce saldo disponible para nuevas asignaciones, pero no cambia el balance contable ni el patrimonio.
- La reserva de cada evento nuevo identifica la cuenta propia que la respalda. El vínculo de cuenta en la meta es opcional.
- Proyecciones Room/vistas pueden acelerar lecturas, pero deben poder reconstruirse desde eventos y el ledger.

## Entidades

### Goal (`public.goals`, Room `GoalEntity`)

| Campo conceptual | Tipo | Regla |
| --- | --- | --- |
| `id` | UUID | Identificador estable creado por el cliente y preservado al sincronizar. |
| `user_id` | UUID | Propietario. Lo deriva la sesión en operaciones remotas; no se acepta como autoridad del cliente. |
| `name` | texto | Obligatorio; validación de nombre normalizado según los controles existentes de producto. |
| `target_minor` | entero positivo | Objetivo expresado en unidades menores. |
| `currency_code` | código de moneda | Debe coincidir con la cuenta que respalda cada reserva/transferencia. |
| `target_date` | fecha nullable | Opcional, sin efecto financiero. |
| `linked_account_id` | UUID nullable | Cuenta real vinculada/predeterminada, opcional. Propietario y moneda deben coincidir. |
| `icon` / `color` | presentación nullable | Metadatos visuales existentes o aprobados; no afectan cálculos. |
| `status` | ACTIVE / COMPLETED / ARCHIVED / LOCKED_BY_PLAN | Proyección de ciclo de vida. La condición financiera se recalcula desde eventos y objetivo. `LOCKED_BY_PLAN` aplica a metas excedentes tras downgrade Freemium. |
| `revision` | entero | Incrementa en todo cambio aceptado que deba aparecer en sync; permite detectar edición obsoleta. |
| `created_at`, `updated_at` | instante | Auditoría y orden de cambios. |

**Relaciones**: una meta tiene cero o más eventos. Puede tener cero o una cuenta enlazada (`linked_account_id`), que actúa como referencia organizativa y respaldo predeterminado pero no bloquea fondos por sí sola. Las referencias deben incluir propietario o asegurar mediante FK compuesta que meta, cuenta y evento pertenecen al mismo usuario (`user_id = auth.uid()`).

### GoalEvent (`public.goal_events`, Room `GoalEventEntity`)

Hecho inmutable que modifica la reserva o documenta su reasignación. Correcciones agregan eventos de ajuste auditables; no reescriben importes históricos.

| Campo conceptual | Tipo | Regla |
| --- | --- | --- |
| `id` | UUID | Estable e idempotente; se comparte con outbox/comando cuando se sincroniza. |
| `user_id` | UUID | Igual al propietario de la meta y de toda cuenta/transacción relacionada (`auth.uid()`). |
| `goal_id` | UUID | Meta propietaria del evento. |
| `event_type` | enum/texto controlado | Como mínimo: ALLOCATION, RELEASE y ajuste/reasignación auditable. La asignación futura de tipos debe preservar compatibilidad con valores históricos. |
| `delta_minor` | entero con signo | Aumenta la reserva con signo positivo y la reduce con signo negativo; magnitud validada contra moneda y reserva vigente. El mapeo físico debe reconciliarse con el `amount_minor` actual. |
| `currency_code` | código de moneda | Debe coincidir con la meta y cuenta de respaldo. |
| `backing_account_id` | UUID | Obligatorio para reservas/liberaciones nuevas; identifica el saldo real afectado por la asignación. Nullable temporalmente solo para filas históricas previas (`LEGACY_UNLINKED`) no reconstruibles hasta resolver el preflight. |
| `transaction_id` / referencia al grupo de transferencia | UUID nullable | Solo para una transferencia interna explícita. Una reserva-only y una liberación no llevan movimiento. Usar la identidad existente de transferencias, evitando inventar una transacción de gasto/ingreso. |
| `operation_id` | UUID | Clave estable de la operación que originó el evento; única por propietario para impedir duplicación. |
| `occurred_at`, `created_at` | instante | Tiempo efectivo y de persistencia, diferenciados si el modelo actual lo permite. |

**Relaciones**: FK owner-scoped a meta y cuenta; referencia a transacción/grupo existente solo en traslado real. Borrar una meta con eventos no debe eliminar esos eventos en cascada.

### Account ledger (existente)

No se crea entidad ledger nueva. Cada asiento tiene exactamente un origen: una transacción (`transaction_id`) o un movimiento financiero explícito (`financial_movement_id`), tipado por `source_kind`; el origen no transaccional conserva FK owner/account-scoped y clave única para evitar posting duplicado. Las aperturas y solo sus reversos/ajustes explícitamente enlazados se reconcilian desde `financial_movements`, preservando esas filas históricas; fuentes ambiguas se informan y no se infieren. La reconciliación es idempotente y rechaza divergencia de una fuente ya posteada.

`confirmed_balance(account)` se calcula exclusivamente desde `internal.ledger_entries`: asientos de transacciones confirmadas no borradas y fuentes financieras posteadas. Excluye transacciones `PENDING`, `VOIDED` o borradas. `financial_movements` y `accounts.initial_balance_minor_units` son espejos/proyecciones legacy, no sumandos paralelos del balance canónico. `active_reserved(account)` suma deltas válidos de eventos vigentes para esa cuenta a lo largo de todas las metas.

```text
free_backing(account) = confirmed_balance(account) - sum(active_reserved(account across all goals))
goal_reserved_by_account(goal, account) = sum(valid goal event deltas for that goal and account)
goal_reserved(goal) = sum(valid goal event deltas for that goal across all backing accounts)
goal_progress(goal) = goal_reserved(goal) / target_minor
```

Los cálculos se hacen por moneda y cuenta. Una meta puede acumular reservas de múltiples cuentas de respaldo en la misma moneda. Si una operación confirmada reduce un saldo por debajo de sus reservas (`confirmed_balance(account) < active_reserved(account)`), no se oculta ni revierte: se conserva la operación, se expone el faltante (`shortfall = active_reserved - confirmed_balance`) y se permite al usuario corregir metas (ingreso de fondos, liberación o reasignación).

### Goal operation receipt / outbox (patrón de sincronización)

| Dato | Local Room | Remoto |
| --- | --- | --- |
| `operation_id` | UUID estable, único por usuario | Clave del recibo idempotente. |
| `request_hash` | Hash de representación canónica | Recalculado/validado en servidor; mismo ID con idéntico hash devuelve recibo previo (`DUPLICATE`); mismo ID con distinto hash produce conflicto (`CONFLICT/OPERATION_ID_REUSED`). |
| `contract_version` | Versión del contrato | Versión aceptada por endpoint/RPC. |
| `expected_revision` | Revisión observada al componer comando | Revisión comparada y actualizada en transacción. |
| `sync_state` | PENDING, LEASED, APPLIED, CONFLICT, REJECTED, RETRYABLE | El servidor persiste resultado y cursor/feed de cambios. |
| `result/error` | Resultado mínimo para recuperar estado local | Recibo con IDs, revisión, resumen y código estable de error. |

La escritura local de evento/proyección/outbox debe ser una transacción Room. La sincronización remota incluye evento, cambios de ledger cuando corresponda, recibo y feed en una transacción PostgreSQL. Los datos sensibles no se copian a logs.

## Transiciones de estado

| Estado actual | Evento/cambio | Estado resultante | Efecto sobre reserva |
| --- | --- | --- | --- |
| ACTIVE | Reserva alcanza o supera objetivo (`reserved >= target`) | COMPLETED | La reserva permanece intacta (no se trunca ni limita si supera el objetivo). |
| ACTIVE | Reducción de objetivo a importe `<= current_reserved` | COMPLETED | La reserva permanece intacta; conserva eventos e historia sin mover fondos. |
| COMPLETED | Aumentar objetivo por encima de la reserva actual | ACTIVE | Conserva reserva e historia; recalcula progreso hacia el nuevo objetivo. |
| COMPLETED | Liberación deja reserva bajo objetivo | ACTIVE si hay cupo; al límite Free requiere selección explícita | Reduce reserva solo por el importe explícitamente liberado; al límite, el evento de liberación, el bloqueo de la `ACTIVE` elegida y la reactivación son una sola operación atómica. |
| ACTIVE | Downgrade Freemium excede cupo de 2 metas activas | LOCKED_BY_PLAN | Solo metas `ACTIVE` excedentes no seleccionadas quedan bloqueadas para nuevos aportes; conservan reservas e historia íntegras. `COMPLETED` no consume cupo ni es candidata a bloqueo por esta regla. |
| COMPLETED + ACTIVE elegida | Reactivación por edición de objetivo o liberación en Free cuando ya existen 2 metas `ACTIVE` | COMPLETED → ACTIVE y la meta `ACTIVE` elegida → LOCKED_BY_PLAN (incluida la operación iniciadora, atómica) | La persona elige explícitamente qué meta activa bloquear; ambas reservas e historias se conservan. Cancelar no cambia ninguna meta ni registra la liberación. |
| LOCKED_BY_PLAN | Restablecer Premium o liberar cupo activo | ACTIVE / COMPLETED | Se levanta el bloqueo de plan sin alterar estados funcionales ni reabrir metas archivadas. |
| ACTIVE o COMPLETED | Archivar con reserva pendiente (`reserved > 0`) | No permitido aún | Resolver liberando a saldo libre, reasignando a otra meta o cancelando archivo. |
| ACTIVE o COMPLETED | Archivar con reserva cero (`reserved == 0`) | ARCHIVED | Conserva objetivo y eventos consultables; no cambia ledger. |
| Cualquiera | Borrado físico con eventos, traslados u outbox pendiente | Rechazado | Preserva la historia (`REJECTED/HISTORY_PRESERVED`). |
| Meta vacía | Confirmar borrado físico sin eventos, traslados ni outbox | Eliminada | No hay historia ni efecto financiero que conservar. |

El porcentaje puede superar 100% si el dominio ya admite ese caso; cualquier límite visual no cambia el importe reservado. La etiqueta de estado no es una fuente de saldo.

## Invariantes y validaciones

1. `target_minor > 0`; montos de aporte/liberación son positivos en la solicitud y no nulos/negativos.
2. Cada cuenta es propiedad del mismo usuario (`user_id = auth.uid()`), está disponible y tiene la moneda compatible; cliente no elige `user_id`.
3. Una reserva nueva solo se acepta si `free_backing(account) >= amount`; rechazar todo el comando si no alcanza, sin parcialidad.
4. Serialización concurrente: transacciones que modifican reservas adquieren bloqueo por fila (`FOR UPDATE`) en la cuenta de respaldo en orden determinista por ID de cuenta, garantizando que `sum(active_reserved(account)) <= confirmed_balance(account)` se cumpla siempre.
5. Reserva-only, liberación, finalización y archivo no escriben transacciones ni asientos del ledger.
6. Un traslado explícito requiere origen/destino propios y misma moneda. Debe registrar ambos efectos de cuenta según el comando de transferencia existente y asociarlos a la reserva atómicamente en la misma transacción de BD. Origen=destino se trata como reserva-only.
7. Liberar no puede superar la reserva existente de la pareja meta/cuenta. Una liberación excesiva se rechaza por completo.
8. Reasignar sin cambiar respaldo registra su historia, sin movimiento. Reasignar a una cuenta de respaldo distinta no simula un traslado; requiere una acción explícita con el flujo aprobado de transferencias.
9. La identidad de reintento se guarda por usuario. El hash igual devuelve el resultado anterior (`DUPLICATE`); el hash distinto es conflicto (`CONFLICT/OPERATION_ID_REUSED`).
10. RLS y FKs owner-scoped protegen meta/evento/cuenta. Los RPC también comprueban propiedad y revisión; no se permite eludir reglas mediante CRUD directo.
11. Archivo conserva eventos; borrar con historia siempre falla. Toda corrección financiera es un evento/ajuste explícito y auditable.
12. Eventos heredados sin respaldo conocido permanecen identificables como `LEGACY_UNLINKED` y no se convierten a una cuenta por conjetura.
13. El cupo Free es como máximo dos metas `ACTIVE`; `COMPLETED`, `ARCHIVED` y `LOCKED_BY_PLAN` están excluidas. Reactivar una completada al límite por edición o liberación requiere selección explícita de una activa y atomicidad de la operación iniciadora más ambas transiciones.

## Migración y compatibilidad

- Room: migración versionada v21→v22, registrada en el punto de inyección actual, con snapshot de esquema nuevo. No recrear tablas ni limpiar datos locales.
- Supabase: migración SQL aditiva que amplíe el modelo y comandos canónicos. Ajustar restricciones, RLS, grants, RPC, recibos y feed sin borrar eventos. Mantener compatibilidad/corte de outbox antiguo decidido antes de despliegue.
- Antes de backfill, cruzar eventos, transacciones, asientos, cuentas, moneda y dueño. Si no puede reconstruirse `backing_account_id`, preservar fila y reportarla para decisión; no fabricar el vínculo.
- Probar migraciones sobre datos representativos y verificar que reservas, balances, revisiones, cursor y recibos coinciden antes y después.

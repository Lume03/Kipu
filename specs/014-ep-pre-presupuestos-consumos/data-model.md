# Data Model: EP-PRE — Presupuestos y Consumos

**Estado**: modelo lógico propuesto para planificar; no especifica schema final ni prueba implementación/despliegue. Ver [research gates](research.md).

## Principios de modelo

- El presupuesto es intención/planificación y no tiene efecto contable ni retiene fondos.
- Dinero: entero en unidad menor + `currency_code`; no `Float`/`Double` autoritativo.
- Cada entidad de negocio está aislada por propietario autenticado. Referencias solo al mismo usuario.
- Consumo es una proyección consultada desde los movimientos elegibles; no es campo autoritativo mutable ni asiento.
- Intervalos usan límites inclusivo-inicio/exclusivo-fin en tiempo de consulta; su derivación de fecha local depende de la decisión G1 (calendario y zona).
- El modelo global V4.2 propone budgets + budget_periods, pero solo la tabla `public.budgets` se localizó en una migración del checkout; el modelo de abajo no afirma equivalencia con Room ni deployment remoto.

## Budget — definición de presupuesto

| Campo conceptual | Tipo lógico | Reglas |
|---|---|---|
| `id` | UUID | Identidad estable, generada antes de sincronizar. |
| `user_id` | UUID | Propietario autenticado; no controlable por otra sesión. |
| `name` | texto | Nombre descriptivo requerido, con límite de longitud por validar contra convenciones existentes. |
| `scope` | unión | `TOTAL` o `CATEGORY(category_id)`; category_id debe ser propia, válida y no borrada. El requisito distingue categoría/subcategoría, ambas se representan por el mismo árbol aprobado de HU-14. |
| `limit_minor` | entero positivo | Límite por ciclo; en unidad menor de currency. |
| `currency_code` | ISO-4217 | **Propuesta pendiente de G6**: incluir solo operaciones de moneda coincidente, sin conversión. La regla de elegibilidad y su explicación al usuario requieren aprobación de producto; no se prescribe conducta antes de esa decisión. |
| `schedule` | frecuencia + configuración | Valores funcionales: diario, semanal, quincenal, mensual o rango custom; forma física pendiente G1. Para rango, start/end requeridos y end >= start. |
| `alert_thresholds_bps` | conjunto ordenado de enteros | Defaults 5000/8000/10000; editable. Validación de duplicados/rango requiere acordar con HU-37; no se aceptan valores inválidos o fuera de 0..10000 como propuesta segura. |
| `status` | estado | `ACTIVE`, `FINISHED` y borrado físico confirmado funcionalmente; el mecanismo local/remote puede requerir tombstone técnico por G5. No se exponen acciones Pause/Resume. |
| `revision` | entero positivo | Control optimista siguiendo convenciones de sync disponibles. |
| `created_at`, `updated_at` | instante | Auditoría técnica, no determinan por sí solos precedencia de conflictos. |
| `deleted_at` | instante opcional | Solo si el sync vigente requiere tombstone; no equivale a mantener activo el presupuesto ni sustituye eliminación final según G5. |

## BudgetPeriod — snapshot de vigencia

| Campo conceptual | Tipo lógico | Reglas |
|---|---|---|
| `id` | UUID | Identidad de la vigencia. |
| `user_id` | UUID | Propietario; debe coincidir con Budget. |
| `budget_id` | UUID | Referencia a definición propia. |
| `starts_on` | fecha civil/instante de frontera | Inicio inclusivo. Debe seguir calendario aprobado G1. |
| `ends_on` | fecha civil/instante de frontera | Fin exclusivo para consulta; > inicio. Definición exacta a decidir según modelado existente. |
| `limit_minor` | entero positivo | Snapshot del límite que rigió ese periodo; no se reescribe en periodos cerrados. |
| `created_at` | instante | Creación de la vigencia. |
| uniqueness | budget + intervalo | Un solo periodo por definición y fechas; condición debe ser segura ante reintento/concurrencia. |

El consumo consultado no se persiste como propiedad financiera de periodo: se deriva desde transacciones vigentes y una query coherente del dominio financiero. La lista de periodos posibilita conservar el límite histórico y consultar fechas anteriores. Qué metadatos técnicos de renovación sobreviven a borrado depende de G5.

## ExpenseEligibility — criterio de consulta, no entidad persistida

- Propietario actual derivado de sesión.
- Movimiento actual vigente con naturaleza de gasto (`EXPENSE`) y estado elegible confirmado (`CONFIRMED`/`REVISED` solo según estado real y política vigente de EP-MOV).
- Intersección de periodo y moneda es propuesta pendiente de G6; categoría opcional se evalúa sobre ids de movimiento únicos. La moneda elegible y la explicación al usuario requieren aprobación de producto.
- Excluir `VOIDED`, pendientes, transferencias, pagos de crédito/principal y movimientos de reserva/meta. Compras de tarjeta son gasto una sola vez en la compra conforme a Constitución I.
- Un reembolso solo ajusta gasto base si es un vínculo validado HU-25, según contrato real aprobado. No inferir semántica de signo/periodo fuera de ese contrato.
- Consulta por un padre y su subcategoría nunca suma la misma transacción dos veces al mismo resultado global.

La definición precedente consolida requisitos y diseño EP-MOV T059; no crea interfaz externa ni garantiza que la consulta ya exista en código.

## BudgetAlert — evento/aviso derivado

Identidad de deduplicación propuesta: propietario + presupuesto + periodo + umbral (cruce) + generación/version del ciclo. Se debe confirmar con EP-NOT que esa clave corresponde a su interfaz y semántica. La alerta no forma parte del saldo/ledger; relectura, reintento o restauración de estado no vuelve a emitir el mismo cruce. Finalizar presupuestos impide alertas de vigencias futuras. Retención/lectura/borrado del aviso se rigen por EP-NOT.

## Relaciones y cardinalidades

```text
User 1 ── N Budget
Budget 1 ── N BudgetPeriod
BudgetPeriod 1 ── N consulta de movimientos elegibles (solo derivada)
Budget + BudgetPeriod + threshold crossing 0..1 ── BudgetAlert
Category 0..1 ── Budget scope; Category hierarchy → parent
```

Los periodos contienen snapshots de límites, no copias de operaciones financieras. No hay relación presupuesto→ledger que cree asientos.

## Estados y transiciones

```text
ACTIVE ── editar datos ordinarios ──> ACTIVE (nueva revisión)
ACTIVE recurrente ── renovación idempotente ──> ACTIVE + periodo nuevo; anteriores conservados
ACTIVE custom ── supera ends_on ──> fin natural; no nueva vigencia
ACTIVE ── confirmar finalizar ──> FINISHED (no renueva ni alerta, historia consultable)
ACTIVE/FINISHED ── confirmar borrado ──> eliminación de definición/periodos (HU-38; G5 sync/retención)
Borrado solicitado ── cancelar confirmación ──> sin transición
```

Cambiar categoría/alcance o agenda entra en vigor desde siguiente vigencia o requiere finalizar y crear presupuesto nuevo, conforme HU-38. El modelo final debe especificar cómo opera cuando no existe siguiente periodo (rango terminado) antes de implementar.

## Integridad, seguridad y consultas

- Constraints: positive `limit_minor`, currency válida, periodo ordenado, propietario coherente, category válida y propia, únicos periodos, threshold validado; detalles exactos deben armonizar con schema y contratos existentes.
- Índices: candidatos a verificar con patrones reales: (user_id, status/is_active), (budget_id, starts_on), period by owner/end. No agregar índices sin consultas/profiling ni conflicto con existentes.
- Room: transacción para mutación + outbox/estado sync; migración no destructiva para datos financieros, rollback/preflight. Confirmar entidades/version de `KipuDatabase` antes de proponer versión.
- PostgreSQL/Supabase, si aplica: RLS por `user_id`; autorización explícita de toda función privilegiada; referencias compuestas evitan vincular categorías/recursos de usuario distinto. Confirmar despliegue separado del repositorio local.
- Borrar budget/period no puede cascade-delete movimientos, categorías, notificaciones no relacionadas ni ledger.

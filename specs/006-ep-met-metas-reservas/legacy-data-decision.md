# Decisiones sobre datos heredados — EP-MET

**Fecha:** 2026-10-17
**Estado:** decisiones de producto aprobadas; preflight sintético local completado (T004); auditoría del historial real y de outboxes antiguos pendiente como gate de activación T040.

## Decisiones aprobadas

1. Los gastos e ingresos históricos asociados a metas se conservan exactamente como están. No se reescriben ni se compensan automáticamente.
2. Los comandos offline pendientes existentes antes de activar el backend nuevo solo se reintentan si conservan una identidad de operación y un hash verificables y el servidor puede validar su revisión/idempotencia. Si falta esa identidad o el resultado es ambiguo/conflictivo, se detienen para revisión manual; no se reproducen a ciegas.
3. El corte para clasificar los comandos heredados es el instante en que se active el backend nuevo.

## Evidencia local

Se inició una instancia Supabase local aislada, identificada como `kipu-met-local-20261017`, a partir de las migraciones existentes del repositorio. El preflight `supabase/diagnostics/006_ep_met_goal_history_preflight.sql` se ejecutó dentro de una transacción `READ ONLY`, primero con el esquema vacío y después con un fixture sintético.

La instancia se creó vacía. Se insertaron temporalmente dos usuarios/cuentas sintéticos, una meta, cuatro transacciones y cinco eventos: asignación y liberación con ledger conciliado, evento con ledger de otro propietario/cuenta, evento con transacción sin ledger y evento sin transacción. T003 dejó sin revisión los dos eventos conciliados, marcó para revisión los dos eventos inconsistentes y devolvió `legacy_unlinked=true` para los eventos sin `backing_account_id`; el evento sin transacción no se trató como inconsistencia por sí solo. La prueba detectó que la versión previa de T003 no marcaba un ledger existente bajo otro propietario/cuenta; se corrigió `requires_manual_review` para revisar propietario, cuenta, moneda y efecto neto.

Tras la ejecución se borraron los usuarios sintéticos, lo que eliminó sus cuentas, meta, transacciones, ledger y eventos por cascada. Una consulta posterior confirmó cero filas sintéticas. La salida local `ep-met-preflight-synthetic.out` conserva el resultado técnico.

Esta ejecución verifica el comportamiento del diagnóstico frente a casos sintéticos. **No es el preflight de un entorno con datos de usuarios** y no permite concluir qué eventos, movimientos, recibos o reintentos existen en otro entorno ni en outboxes guardados en dispositivos.

## Gate pendiente antes de activar el backend (T040)

No hay un entorno QA/staging con datos representativos disponible. Antes de aplicar migraciones alojadas o activar los RPC nuevos, alguien del equipo debe disponer de un conjunto no productivo representativo (idealmente restaurado desde una copia anonimizada), ejecutar allí el mismo preflight y revisar los outboxes pendientes de clientes antiguos. Reintentar solo operaciones con identidad/hash verificables y revisión/idempotencia validable; detener las ambiguas para revisión manual. No ejecutar esta revisión en producción. La revisión real no bloquea completar la implementación local, pero sí bloquea la activación alojada.

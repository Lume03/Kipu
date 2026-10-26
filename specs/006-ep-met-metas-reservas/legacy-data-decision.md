# Decisiones sobre datos heredados — EP-MET

**Fecha:** 2026-10-17
**Estado:** decisiones de producto aprobadas; T004 completado; ver actualización de activación T040 del 2026-10-26 UTC al final. Las limitaciones históricas siguientes describen el corte original.

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

## Actualización de activación T040 — 2026-10-26 UTC

El Owner solicitó completar la sincronización después de recibir la auditoría de bloqueos («Hazlo»). Se preparó la base aislada `kipu_sync_gate_verified_20261026` en el contenedor local de EP-MET, sin resetear bases compartidas. Se reprodujeron columnas, defaults, constraints e índices de las cinco tablas de Planificación desde el catálogo alojado y se contrastaron sus políticas/grants. En el proyecto de destino no existen metas, eventos de metas, deudas ni eventos de deudas heredados. No se convirtió esa ausencia en evidencia sobre dispositivos: se inspeccionó separadamente la base del teléfono conectado.

La copia no productiva incorpora las dos metas y su evento de reserva del teléfono con identidades anonimizadas. T003 se ejecutó allí en READ ONLY, antes de las migraciones de Metas; conserva la historia sin inferir cuenta de respaldo para eventos legados. No hay gastos/ingresos históricos de metas en esta cohorte. Los escenarios sintéticos históricos siguen cubriendo ledger ausente, propietario ajeno y vínculos ambiguos; no sustituyen esta inspección del dispositivo.

Se verificaron 12 identidades pendientes (5 de Metas y 7 de Deudas), incluyendo igualdad entre ID/hash del sobre y la cola. En Deudas se reprodujo el hash de intención de los creadores originales: dos pagos sin interés incluían una categoría de UI que el payload omitía por no tener efecto financiero; se confirmó la variante exacta y se corrigió la normalización futura. Se conservan los IDs/hashes de los reintentos de Deudas. En Metas se descubrió la divergencia revisión local inicial 0 / servidor 1. La reparación solo admite cadenas completas nunca intentadas, con todos sus hashes, recibos, revisiones y estado pendientes verificables; conserva IDs, cuerpo de negocio y solicitud original en el recibo local. Toda cadena intentada/incompleta/compuesta se detiene en `CONFLICT/LEGACY_REVISION_REVIEW_REQUIRED` sin modificar payload/hash. No se autoriza reproducir ciegamente colas de otros clientes que aparezcan posteriormente.

Las siete migraciones de Deudas, las seis de Metas, ledger de apertura y su guard de fuentes ambiguas, identidad de transferencia y activación se validaron en la base aislada. Once suites SQL focalizadas pasan sin aserciones pgTAP fallidas; se actualizaron fixtures antiguos para cumplir la compatibilidad vigente de presets de cuentas. El cliente repara las revisiones solo antes del primer intento y evita enviar hijos cuando su comando padre está pendiente o falló. La aprobación técnica queda limitada al contrato y cohorte inspeccionados; la aceptación de sincronización final depende además de las pruebas remotas y de dispositivo registradas en el informe de activación.

**Decisión:** requisito previo de despliegue satisfecho para las migraciones seleccionadas y el teléfono inspeccionado. Activación autorizada por la solicitud del Owner; no hay aprobación para reinterpretar datos ambiguos ni para desactivar invariantes. [Informe de activación y resultados](../../docs/qa/activacion-sync-planificacion-2026-10-26.md).

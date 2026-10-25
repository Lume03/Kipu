# Verificación de sincronización remota de Planificación — 2026-10-25

## Dictamen

**NO APROBADA como sincronización integral.** La persistencia local y una interfaz actualizada no prueban persistencia remota. Se contrastaron el APK instalado, las colas Room del teléfono conectado, el catálogo/ACL/RLS de Supabase y sus logs. No se desplegaron migraciones ni se activaron comandos remotos de Metas.

Esta revisión amplía la [validación de interfaz de Planificación](planificacion-2026-10-25.md) y el [incidente previo de restauración](incidente-sync-restauracion-2026-10-25/README.md). Los resultados anteriores de UI no certifican sincronización remota.

## Estado comprobado

| Área | Evidencia actual | Resultado |
| --- | --- | --- |
| Metas | `GOALS_REMOTE_COMMANDS_ENABLED=false` en el APK; 5 comandos locales `PENDING`; ninguna fila remota de metas/eventos | Solo local en esta cuenta; no certificada |
| Deudas | 6 comandos `RETRY/HTTP_404`: 3 aperturas, 2 liquidaciones, 1 cronograma; ninguna fila remota de deudas/eventos | Bloqueada por backend |
| Presupuestos | Fallo real de fechas corregido; descarga al iniciar/reanudar añadida; recuperación y borrados remotos observados | Recuperación parcial verificada; CRUD completo pendiente |
| Movimientos | Cola vacía; llamadas reales a `pull_financial_changes_v1` con HTTP 200 y avance de checkpoint hasta 266 | Transporte/pull observado; no constituye nueva certificación de todas sus operaciones |

### Contrato remoto incompleto

No existen las seis RPC usadas por el cliente de Deudas: `open_debt_v1`, `edit_debt_details_v1`, `delete_debt_if_unreferenced_v1`, `settle_debt_v1`, `set_debt_schedule_v1`, `close_debt_v1`.

Tampoco existen las siete de Metas: `create_goal_v1`, `update_goal_v1`, `delete_empty_goal_v1`, `reserve_goal_v1`, `release_goal_v1`, `reassign_goal_reserve_v1`, `archive_goal_v1`.

Faltan además estas columnas del contrato actual:

- `debts`: `opened_on`, `opening_mode`, `reminder_lead_days`.
- `debt_events`: `interest_transaction_id`, `principal_delta_minor`.
- `goals`: `linked_account_id`.
- `goal_events`: `backing_account_id`, `currency_code`, `delta_minor`, `operation_id`.

Las RPC antiguas `allocate_goal_funds`/`release_goal_funds` siguen ejecutables para `authenticated` y sus definiciones usan egresos/ingresos. No sustituyen las reservas actuales. También persiste CRUD directo autenticado sobre tablas de metas/deudas/eventos; hay que conciliar esos permisos con las migraciones actuales, además de crear endpoints. Tener RLS habilitado no basta para certificar el contrato de escritura.

Las migraciones recientes de Presupuestos sí figuran alojadas. Las series locales de Deudas del 8 de octubre y de Metas del 17–19 de octubre no están reconciliadas con el servidor. Los nombres/versiones del historial remoto difieren en varios puntos del local: un `db push` indiscriminado no es una solución validada.

## Correcciones de cliente realizadas

1. **Fechas Supabase con offset.** El teléfono registró `DateTimeParseException` para `2026-10-24T19:53:56.490936+00:00` en `applyRemoteTombstones`. El worker fallaba antes de enviar la cola. Se reutiliza `parseFeedInstant` (`OffsetDateTime`) tanto para borrados como para presupuestos y periodos descargados. Se preservan los milisegundos del instante.
2. **Sincronización al recuperar sesión y primer plano.** `MainActivity` programaba mantenimiento local de presupuestos, pero no su sincronización remota. Se añade `BudgetSyncScheduler` en ambos puntos; permite recuperar registros del servidor y reactivar trabajos anteriormente fallidos aunque no haya una nueva edición local.

No se cambió la bandera de Metas ni se utilizó una RPC financiera antigua como alternativa.

## Pruebas realizadas y límites

- Build debug y AndroidTest: **PASS**. JVM `BudgetRemoteMappingsTest`: **PASS**, incluye fechas equivalentes con `+00:00` y `-05:00`.
- `:app:lintDebug`: **PASS**; comprobación de enlaces locales del informe/índice y `git diff --check`: **PASS**.
- Teléfono conectado: `BudgetTombstoneReplayIntegrationTest`: **PASS, 1 prueba**. Usa Room en memoria y MockEngine; verifica fechas con offset, hidratación de presupuesto/periodo y que un registro borrado no reaparezca. Esta prueba no llama al servidor real.
- APK corregido instalado conservando sesión y base existente. Se observó el worker real de presupuestos en `SUCCESS`, después del fallo previo reproducido.
- Servidor real: HTTP 201 al enviar un presupuesto y su periodo que ya estaban pendientes; HTTP 200 en el borrado pendiente de un registro QA de la revisión anterior. Al recuperar registros remotos aparecieron los dos presupuestos existentes del usuario.
- Nuevo registro identificable `QA Sync 2510`, S/ 17.25: creado localmente antes de recuperar el estado remoto. Su alta remota fue **rechazada**, no aprobada: el servidor ya tenía dos presupuestos activos Free. Logs corroboran `BUDGET_QUOTA_EXCEEDED`, POST de presupuesto HTTP 400 y periodo HTTP 409 por FK. El cliente guardó ambos como `REVISION_CONFLICT`, ocultando el motivo de negocio. La edición prevista no se completó; no se presenta como verificada.
- Registro QA eliminado desde la aplicación. Estado final: **2 presupuestos y 2 periodos locales/remotos, 3 tombstones remotos, 0 presupuestos QA remotos**. Cola de presupuestos: 4 comandos `COMPLETED`, sin `PENDING` ni `FAILED`. Los borrados retiraron comandos de alta/periodo superados. No se alteraron los presupuestos originales para liberar cupo.
- RLS de lectura: con rol `authenticated`, el propietario ve sus registros y un `sub` sintético distinto ve cero presupuestos/periodos/tombstones. Se usaron transacciones de solo lectura con rollback. **No es una auditoría completa de RLS de escritura ni una prueba con una segunda sesión HTTP real.**
- No se borraron datos de la aplicación, no se desinstaló y no se cerró sesión. **Restauración desde instalación limpia, segundo dispositivo, offline/reconexión, conflictos concurrentes y CRUD remoto completo de las tres secciones quedan sin verificar.**

La cola de Metas conserva 5 comandos pendientes y la de Deudas 6 reintentos. Fuera del alcance funcional de Planificación se detectaron también 2 preferencias pendientes y una selección de plan en conflicto; requieren diagnóstico propio antes de certificar la sincronización global. Una cola de movimientos vacía no demuestra que estas otras colas estén sanas.

## Ajustes necesarios para certificarla

1. **Completar el gate existente de Metas.** [Plan, gate de datos legados](../../specs/006-ep-met-metas-reservas/plan.md) y [T040](../../specs/006-ep-met-metas-reservas/tasks.md) exigen historial no productivo representativo aprobado, conciliación de migraciones/ACL y revisión de outboxes de clientes antiguos antes del despliegue alojado/activación. Cero filas remotas actuales no sustituye esa evidencia. Conservar IDs, reservas e historia sin reinterpretar respaldos ambiguos.
2. **Preparar un despliegue seleccionado y probado.** Conciliar las migraciones de lifecycle/apertura/historia/liquidación/cronogramas/cierre de Deudas y las de esquema/CRUD/reserva/lifecycle/reemplazo atómico/identidad de transferencia de Metas, con sus dependencias del ledger de apertura. Validar en copia representativa: pgTAP focalizado, RLS, grants, invariantes e idempotencia; documentar recuperación operativa. No marcar migraciones como aplicadas solo para silenciar diferencias.
3. **Validar el contrato desplegado antes de habilitarlo.** Comprobar firmas, columnas, ACL y llamadas autenticadas reales. Solo tras satisfacer T040, habilitar `goalRemoteCommandsEnabled` en el build correspondiente y probarlo en el teléfono.
4. **Conciliar las operaciones ya pendientes.** Reutilizar los mismos IDs de operación, preservar orden/dependencias y resolver rechazos explícitamente. No vaciar Room ni regenerar operaciones para conseguir una cola aparentemente limpia.
5. **Corregir la conciliación de errores de Presupuestos.** Distinguir cuota, categoría inválida y otros rechazos de un conflicto real de revisión; impedir el envío del periodo cuando su alta padre fue rechazada; ofrecer resolución visible. Validar también la cuota cuando la caché local todavía no incorpora registros remotos. Las correcciones de fechas/arranque no resuelven por sí solas estos casos.
6. **Ejecutar la matriz final de aceptación.** Para cada sección: crear/editar/acciones/borrar o archivar según historia; confirmar filas, eventos, revisiones y dinero en servidor; reintentar la misma operación sin duplicar efectos; offline/reconexión; fallo entre envío y acuse; conflicto de dos clientes; eliminación sin resurrección; cambio de propietario sin filtraciones; restauración en entorno aislado con la misma cuenta. Comparar importes y vínculos, no solo cantidades de filas. Cerrar con colas sin errores no resueltos y datos locales/remotos conciliados.

## Evidencia y privacidad

Las consultas de catálogo y datos fueron de solo lectura; las únicas escrituras remotas procedieron de la aplicación con la sesión existente y sus operaciones autorizadas. No se publican bases Room, WAL, tokens, payloads, identificadores personales ni capturas financieras. Los archivos temporales privados se eliminaron; logs/resúmenes técnicos locales están ignorados por Git.

# Activación de sincronización de Planificación — 2026-10-26 UTC

**Seguimiento del historial:** después de esta validación se detectó y corrigió una incompatibilidad de presentación que no cubrían las comparaciones de persistencia. Ver [incidente, causa y nueva validación real](incidente-historial-post-sync-2026-10-26.md). La migración correctiva adicional lleva el despliegue de esta intervención a 20 migraciones; los conteos de la ejecución inicial siguientes se conservan como corte histórico.

Seguimiento de la [auditoría de sincronización](sincronizacion-planificacion-2026-10-25.md). Trabajo autorizado por el Owner tras conocer los bloqueos. Se conserva la rama/worktree y los cambios de navegación de otro trabajo; no se ejecuta Spec Kit.

## Requisito previo

El [registro T040](../../specs/006-ep-met-metas-reservas/legacy-data-decision.md) documenta la base local aislada, la copia anonimizada representativa del teléfono, el preflight READ ONLY, conciliación de catálogo/ACL y auditoría de los doce comandos pendientes. Once suites SQL focalizadas pasan. Las tres pruebas instrumentadas iniciales pasan: cadena nunca enviada corregida con auditoría, cadena intentada detenida intacta y snapshots anteriores ignorados después de confirmar una revisión posterior.

## Resultado final

**APPROVED para la sincronización de Planificación en el proyecto y teléfono inspeccionados.** Se aplicaron 19 migraciones seleccionadas, se habilitaron las RPC de Metas en debug/release y se recuperaron las doce operaciones pendientes: cinco de Metas y siete de Deudas. No se ejecutó `db push` general ni se falsificaron versiones de migraciones. La variante `lab` conserva endpoints vacíos y comandos remotos de Metas desactivados.

Estado local al cierre de pruebas: Metas sin comandos en outbox; siete comandos de Deudas `SYNCED`, sin error; cuatro comandos anteriores de Presupuestos `COMPLETED`, sin pendientes ni fallos. Metas, Deudas y Movimientos alcanzaron el checkpoint 316. Estos estados se contrastaron con recibos, filas y eventos remotos; no se dedujeron solo de un resultado `SUCCESS` del worker.

## Correcciones que hicieron posible la activación

- Se desplegaron esquema, comandos, reemplazo atómico de cuota y vínculo de transferencias de Metas, más lifecycle, apertura, liquidación, cronograma y cierre de Deudas. Las RPC financieras antiguas incompatibles quedaron sin permiso para clientes autenticados; las escrituras directas de deuda/eventos/cuotas también se revocaron para impedir eludir comandos y cuotas.
- Se corrigió la revisión inicial de Metas a 1. Las cadenas locales completas nunca enviadas se normalizan antes del primer intento, conservando IDs y solicitud original en recibos de auditoría. Las cadenas intentadas o no demostrables se detienen para revisión; no se reescriben a ciegas.
- Se conserva el orden de dependencias en las colas y la revisión optimista mientras quedan operaciones posteriores. Un worker con trabajo pendiente sigue reintentando. Las solicitudes nuevas reemplazan la espera de trabajos antiguos: `KEEP` mantenía las ediciones offline detrás del backoff acumulado durante la ausencia de RPC.
- Presupuestos conserva el motivo de rechazo remoto (cuota, categoría, permisos o revisión), lo muestra en la tarjeta y bloquea el envío de periodos cuyo padre falló. El rechazo por cuota ya no se etiqueta como conflicto de revisión.
- Los snapshots anteriores se ignoran cuando una revisión posterior ya está confirmada. Las fechas asignadas por el servidor se reconcilian sin confundirlas con una reutilización de evento/cuota, conservando comprobaciones de identidad, propietario, dinero y respaldo.
- Se conciliaron dos diferencias alojadas que la base local no reproducía inicialmente: el enum de roles está en `public`, y el ledger rechaza asientos de importe cero. La activación usa el tipo real de la columna y conserva las aperturas cero en la historia sin crear un efecto financiero nulo. Las dos ejecuciones fallidas de esa migración se revirtieron; solo la ejecución corregida figura aplicada.
- Las aperturas y correcciones inequívocas alimentan el ledger automáticamente. Los originales no se reescribieron y la suma de aperturas conciliadas coincide con su fuente.
- Se retiraron los emisores duplicados `SAVINGS_GOAL`/`OBLIGATION`; sus entradas históricas se conservan y el cliente las reconoce. El pull proyecta transacciones de deuda y transferencias explícitas de metas desde sus filas y asientos canónicos, con revisión actual coherente. Antes llegaban filas sin el campo `type` ni ledger y la descarga se detenía.
- La conciliación compara el efecto neto de los asientos locales y remotos de Planificación. IDs de asiento distintos no duplican dinero; las correcciones se agregan sin borrar la historia local. Se comprobaron eco, revisión posterior y anulación.

## Validación efectivamente ejecutada

| Caso | Resultado y alcance |
| --- | --- |
| Base aislada | 13 suites SQL focalizadas: siete de Deudas, cuatro de Metas, activación y proyección de transacciones. Se probaron también enum en `public`, aperturas cero, permisos, aislamiento y anulaciones. Sin aserciones fallidas en las ejecuciones finales. |
| Cliente | Cuatro pruebas JVM de `GoalManagementTest`; build debug/AndroidTest y lint debug aprobados. Compilación de AndroidTest de `lab` aprobada; no se instaló esa variante en la cuenta real. |
| Instrumentación focalizada | 20 casos únicos aprobados en esta intervención: tres de reparación/feed de Metas, dos de Presupuestos, diez de recuperación/feed de Deudas, dos de Movimientos y tres de aceptación remota. Las ejecuciones repetidas durante la corrección no se cuentan como casos adicionales. |
| Recuperación real | Las doce operaciones anteriores tienen recibos remotos. No se eliminaron las colas para aparentar éxito ni se regeneraron sus IDs. |
| Restauración real | Con la sesión existente y los clientes HTTP reales, se descargó desde cursor cero a una base Room vacía. Coinciden metas, reservas, deudas, eventos, cuotas, presupuestos, periodos y saldos por cuenta. Se repitió después de la limpieza y la edición demo: PASS. No se desinstaló ni borró la base del teléfono. |
| Presupuestos remoto | Alta y periodo de un presupuesto QA finalizado, edición, rechazo de revisión obsoleta sin sobrescribir, borrado, tombstone y repetición idempotente del borrado: PASS. El registro finalizado permitió probar sin desplazar los dos presupuestos activos del usuario. |
| Metas sin conexión | WiFi y datos desactivados; creación de `QA Offline 2610` con objetivo S/500 y edición a S/600. Persistió tras reiniciar el proceso y no existía en servidor durante la desconexión. Tras corregir la espera de WorkManager llegó automáticamente con revisión 2. Borrado desde UI confirmado también en servidor. |
| Deuda demo remota | Edición de metadatos, repetición de la misma operación con `DUPLICATE`, rechazo `HISTORY_PRESERVED` del borrado y restauración de todos los metadatos originales: PASS. No se modificaron principal, estado financiero ni pagos para esa comprobación. |
| RLS alojada | Rol `authenticated` con `sub` sintético distinto: cero metas/eventos/deudas/eventos/presupuestos/periodos visibles. Transacción READ ONLY con rollback. No equivale a una segunda sesión física completa. |
| Limpieza | Cero metas `QA Offline 2610`, presupuestos `QA Sync%` o nombres temporales de edición demo en servidor. WiFi/datos volvieron a sus valores iniciales. |

El harness de aceptación reside solo en `androidTestDebug`; requiere el argumento explícito `planningRemoteAcceptance=true`. Su acceso Hilt existe solo en debug. Usa Room en memoria para restauración y nunca limpia la base real. Se preservaron los cambios ajenos de navegación y las cuatro eliminaciones de referencias que ya existían.

## Límites y estado ajeno al alcance

No se probó un segundo teléfono físico, una reinstalación destructiva ni cada posible combinación concurrente de acciones en producción. El conflicto de revisión se comprobó mediante una petición obsoleta autenticada; las carreras financieras y el resto de invariantes se cubren con las suites SQL locales. La aprobación se limita al contrato/cohorte auditados; un cliente antiguo con cadena ambigua sigue requiriendo revisión.

Fuera de Planificación permanecen dos preferencias de perfil `PENDING` y una selección de plan `CONFLICT`, ya presentes en la auditoría inicial. No se cambiaron esas decisiones del usuario ni se certifica la sincronización global de todas las funciones de Kipu. Los avisos de asesores sobre protección de contraseñas e infraestructura interna también siguen fuera de esta corrección. Las RPC `SECURITY DEFINER` autenticadas de comandos son intencionales y conservan validación de propietario; no deben interpretarse como autorización de acceso anónimo.

## Evidencia y reproducción

Logs de ejecución y resúmenes técnicos permanecen en `scratch/planning-review/remote-audit` (ignorado por Git). No se publican bases, WAL, tokens ni datos personales. Los temporales privados se cerraron y eliminaron automáticamente. El informe inicial queda como evidencia histórica del fallo, no como dictamen vigente.

Comando de aceptación: instalar los APK debug y AndroidTest compilados, y ejecutar `adb shell am instrument -w -r -e planningRemoteAcceptance true -e class com.kipu.app.navigation.PlanningRemoteAcceptanceTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`. Es una prueba optativa que usa la sesión real y puede enviar sus operaciones pendientes; no ejecutarla sobre una cuenta distinta sin autorización.


## Migraciones alojadas verificadas

| Versión remota | Archivo local |
| --- | --- |
| 20261026014026 | [20261008120000_s5_debt_lifecycle.sql](../../supabase/migrations/20261008120000_s5_debt_lifecycle.sql) |
| 20261026014028 | [20261008130000_s5_open_payable_debt.sql](../../supabase/migrations/20261008130000_s5_open_payable_debt.sql) |
| 20261026014029 | [20261008135000_s5_open_receivable_debt.sql](../../supabase/migrations/20261008135000_s5_open_receivable_debt.sql) |
| 20261026014031 | [20261008140000_s5_debt_history_guard.sql](../../supabase/migrations/20261008140000_s5_debt_history_guard.sql) |
| 20261026014033 | [20261008150000_s5_settle_debt.sql](../../supabase/migrations/20261008150000_s5_settle_debt.sql) |
| 20261026014035 | [20261008160000_s5_debt_schedule.sql](../../supabase/migrations/20261008160000_s5_debt_schedule.sql) |
| 20261026014036 | [20261008170000_s5_debt_closure.sql](../../supabase/migrations/20261008170000_s5_debt_closure.sql) |
| 20261026014043 | [20261017180000_ep_met_goal_schema.sql](../../supabase/migrations/20261017180000_ep_met_goal_schema.sql) |
| 20261026014045 | [20261017180100_ep_met_goal_crud_commands.sql](../../supabase/migrations/20261017180100_ep_met_goal_crud_commands.sql) |
| 20261026014047 | [20261017180200_ep_met_goal_reserve_commands.sql](../../supabase/migrations/20261017180200_ep_met_goal_reserve_commands.sql) |
| 20261026014049 | [20261017180300_ep_met_goal_lifecycle_commands.sql](../../supabase/migrations/20261017180300_ep_met_goal_lifecycle_commands.sql) |
| 20261026014051 | [20261018190000_ep_mov_opening_ledger.sql](../../supabase/migrations/20261018190000_ep_mov_opening_ledger.sql) |
| 20261026014052 | [20261018190100_ep_mov_opening_ledger_ambiguous_source_guard.sql](../../supabase/migrations/20261018190100_ep_mov_opening_ledger_ambiguous_source_guard.sql) |
| 20261026014054 | [20261018190200_ep_met_active_goal_quota_atomic_replacement.sql](../../supabase/migrations/20261018190200_ep_met_active_goal_quota_atomic_replacement.sql) |
| 20261026014056 | [20261018190300_ep_met_release_goal_atomic_quota_replacement.sql](../../supabase/migrations/20261018190300_ep_met_release_goal_atomic_quota_replacement.sql) |
| 20261026014057 | [20261019190000_ep_mov_goal_transfer_identity.sql](../../supabase/migrations/20261019190000_ep_mov_goal_transfer_identity.sql) |
| 20261026014356 | [20261026013311_planning_remote_sync_activation.sql](../../supabase/migrations/20261026013311_planning_remote_sync_activation.sql) |
| 20261026020111 | [20261026015954_planning_sync_feed_aliases.sql](../../supabase/migrations/20261026015954_planning_sync_feed_aliases.sql) |
| 20261026021203 | [20261026020557_planning_transaction_pull_projection.sql](../../supabase/migrations/20261026020557_planning_transaction_pull_projection.sql) |
| 20261026031846 | [20261026031551_planning_income_account_contract.sql](../../supabase/migrations/20261026031551_planning_income_account_contract.sql) |

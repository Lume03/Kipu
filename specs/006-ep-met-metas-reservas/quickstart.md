# Guía de validación — EP-MET

**Estado (2026-10-18)**: T013/T015 implementadas localmente y QA independiente `APPROVED WITH OBSERVATIONS`; las suites SQL CRUD/reserve/lifecycle/opening-ledger pasaron en el sandbox aislado con rollback y limpieza verificada. La cobertura Compose active-only T042 está en RED esperado y la integración Android local-first T043 sigue pendiente; la extensión de `RELEASE_GOAL` permanece en T044/T045/T046/T047. T048 cubre el harness instrumentado Room legado. No se habilitan comandos remotos; T040 sigue pendiente y prohíbe la activación alojada.

## Requisitos previos

- Rama `006-ep-met-metas-reservas` y Sprint 5 integrado según el gate del equipo.
- Android SDK/JDK requeridos por el proyecto.
- Supabase CLI (se ejecutó `npx --yes supabase@2.121.0`) y Docker para pruebas locales. No se requiere instalación global de la CLI. Usar exclusivamente la instancia local aislada y usuarios/cuentas sintéticos; no apuntar a producción.
- Para las pruebas Room, crear fixture desde el snapshot v21; para las pruebas SQL, partir de migraciones locales y datos sintéticos representativos de cada caso.
- T004 solo valida localmente el diagnóstico con fixtures sintéticos. Antes de cualquier migración alojada o activación remota, completar T040 con un preflight de solo lectura sobre un conjunto no productivo representativo y revisar outboxes de clientes antiguos.
- El cliente solo habilita los comandos remotos al compilar con `-PgoalRemoteCommandsEnabled=true`; mantener el valor predeterminado (`false`) hasta aprobar T040 y desplegar/verificar el backend correspondiente.

## Escenarios de aceptación end-to-end

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| Meta sin cuenta enlazada | Crear meta indicando objetivo/moneda y dejando vínculo vacío. | Se guarda localmente y sincroniza; no crea evento, transacción, asiento, ingreso/gasto ni cambio de patrimonio. |
| Cuenta enlazada | Crear meta con una cuenta propia de moneda compatible. | Vínculo opcional persiste; el balance de cuenta se cuenta una sola vez. |
| Reserva-only | Con S/1,000 confirmados y sin reservas, asignar S/200 a una meta usando esa cuenta. | Evento reserva S/200; saldo contable y patrimonio siguen S/1,000; saldo libre S/800; no movimiento. |
| Aportes multicuenta | Aportar S/200 desde Cuenta A y luego S/100 desde Cuenta B (misma moneda). | Meta acumula S/300 de reserva; Cuenta A descuenta S/200 de saldo libre y Cuenta B descuenta S/100. |
| Evitar doble reserva | Intentar reservar S/700 adicional sobre el mismo respaldo. | Rechazo total, sin evento parcial; una concurrencia entre metas deja como máximo aceptadas S/1,000 en total. |
| Transferencia explícita | Confirmar traslado de una cuenta propia a otra de igual moneda y reservar en destino. | Transferencia interna existente y evento se confirman juntos atómicamente; dos efectos de cuenta equilibrados; sin ingreso/gasto; destino respalda reserva. |
| Mismo origen/destino | Contribuir usando la misma cuenta de origen y destino. | Solo se agrega la reserva; no se genera transferencia propia. |
| Transferencia/reintento offline | Guardar localmente sin red; reconectar y repetir el mismo ID/hash. | La operación se conserva localmente y se aplica una sola vez (`DUPLICATE`); un ID con hash distinto devuelve conflicto visible (`CONFLICT/OPERATION_ID_REUSED`). |
| Rechazo offline en sync | Operación offline rechazada por saldo o límite durante sync. | Se muestra insignia de conflicto en UI y se ofrecen opciones de descartar, ajustar/reintentar o resolver bloqueo. |
| Gasto confirmado reduce respaldo | Confirmar gasto legítimo que lleva saldo bajo reservas. | El gasto se conserva; se muestra faltante de respaldo y se puede corregir explícitamente la meta (fondear, liberar o reasignar). |
| Finalizar y editar objetivo | Alcanzar el objetivo; luego aumentar la meta completada por encima de reserva. | Primero queda COMPLETED con reserva; después vuelve ACTIVE conservando importe/eventos; no hay liberación ni gasto automático. |
| Reducción de objetivo | Reducir el objetivo de una meta activa a un importe `<= current_reserved`. | Pasa inmediatamente a COMPLETED conservando íntegras su reserva e historia sin mover fondos. |
| Liberar/archivar | Liberar importe parcial y luego archivar al llegar a reserva cero. | Reserva baja y saldo libre sube, sin ingreso/transferencia automática; archivo conserva historial. Liberar más de reservado o archivar con reserva se rechaza (guía a liberar, reasignar o cancelar). |
| Borrado físico restringido | Intentar borrar meta con eventos, transferencias o comandos pendientes en outbox. | Rechazo con `REJECTED/HISTORY_PRESERVED`; solo se permite archivar. Solo meta completamente vacía se elimina tras confirmación. |
| Downgrade Freemium | Usuario con >2 metas `ACTIVE` pasa a Free. | Solo se cuentan metas `ACTIVE`; se solicita elegir las 2 que continúan activas y se bloquean únicamente las excedentes activas. `COMPLETED` permanece completada; no se borra ni altera reservas o historia. |
| Reactivación al límite Free | Usuario Free tiene 2 metas `ACTIVE` y eleva el objetivo de una `COMPLETED` por encima de su reserva. | Se solicita elegir cuál activa pasa a `LOCKED_BY_PLAN`; tras selección ambas transiciones se aplican atómicamente y preservan reservas/historia. Cancelar no cambia ninguna meta; la meta completada no consume cupo antes de esa acción. |
| Liberación con reactivación al límite Free | Usuario Free tiene 2 metas `ACTIVE` y libera de una meta `COMPLETED` dejando la reserva por debajo del objetivo. | Se solicita elegir cuál activa pasa a `LOCKED_BY_PLAN`; confirmar registra liberación, bloqueo y reactivación en la misma operación atómica; cancelar no agrega evento ni cambia metas/reservas. |
| Usuario ajeno | Enviar ID de meta/cuenta de otro usuario a lectura y cada RPC. | RLS/RPC no permiten acceso/escritura ni filtran datos (`REJECTED/OWNER_OR_ACCOUNT_INVALID`); ningún ledger cambia. |
| Migración local | Abrir base Room v21 con datos sintéticos, migrar a v22 y consultar. | Cuentas/movimientos existentes y nuevos datos quedan intactos; esquema/snapshot coinciden. |
| Historial legado | Ejecutar T003 sobre fixtures sintéticos: asignación/liberación conciliadas, ledger ausente, ledger con propietario/cuenta distintos y evento sin transacción. | Las filas conciliadas no requieren revisión; las dos inconsistentes sí; los eventos sin cuenta de respaldo muestran `legacy_unlinked=true` (candidato a `LEGACY_UNLINKED`). La consulta es de solo lectura y no asigna respaldo supuesto. |

## Comandos previstos

Desde PowerShell, en la raíz del repositorio, una vez que las tareas implementen los casos:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Para pruebas Android que necesiten Room migraciones instrumentadas, Compose o WorkManager:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para pruebas SQL/RLS, usar la carpeta del sandbox local aislado de EP-MET (su `project_id` y sus puertos son distintos de los otros worktrees). No iniciar desde el `supabase/config.toml` del checkout, que comparte `project_id = "kipu"` con otros stacks locales:

```powershell
# Desde la raíz del sandbox local aislado
npx --yes supabase@2.121.0 start --exclude gotrue,realtime,storage-api,imgproxy,kong,mailpit,postgrest,postgres-meta,studio,edge-runtime,logflare,vector,supavisor
npx --yes supabase@2.121.0 test db
# Para detener los contenedores y conservar sus datos locales:
npx --yes supabase@2.121.0 stop
```

La instancia local aislada no está vinculada a un proyecto alojado. Estos comandos no aplican migraciones al proyecto remoto; no sustituir el preflight de datos por una comprobación contra producción.

## Evidencia local registrada

- `:app:compileDebugKotlin`: correcto.
- Pruebas unitarias de `GoalManagementTest`, `GoalBackingCalculatorTest`, `GoalLifecycleTest`, `ReserveGoalFundsTest` y `GoalCommandHasherTest`: correctas.
- `:app:compileDebugAndroidTestKotlin`: correcto.
- Pruebas Compose de `GoalManagementFlowTest`, `GoalContributionFlowTest` y `GoalLifecycleFlowTest`: correctas en `Pixel_10` con Android 17.
- `GoalRoomMigrationTest` v21→v22: correcto en `Pixel_10` con Android 17.
- `git diff --check`: correcto.
- Los comandos remotos de metas permanecen desactivados en la configuración predeterminada; las operaciones locales muestran su estado de sincronización.
- Se levantó Supabase local aislado desde las migraciones existentes del repositorio (CLI 2.121.0, solo PostgreSQL; sin conexión a un proyecto alojado). T003 corrió en transacción `READ ONLY` tanto con el esquema vacío como con fixtures sintéticos.
- El fixture tuvo dos usuarios y cuentas sintéticos, una meta, asignación y liberación conciliadas, un asiento bajo otro propietario/cuenta, una transacción sin asiento y un evento sin transacción. T003 dejó sin revisión manual los dos casos conciliados, marcó los dos casos inconsistentes y devolvió `legacy_unlinked=true` para los eventos sin `backing_account_id`. Se corrigió el SQL porque antes no marcaba el asiento de otro propietario/cuenta.
- Después de verificar el resultado, se eliminaron en cascada los usuarios y filas sintéticos; se confirmó que no quedan en la instancia local. La salida de preflight quedó en `ep-met-preflight-synthetic.out` dentro del sandbox local para consultar la evidencia.
- Esta fixture cierra T004 como validación técnica local. No demuestra qué existe en bases usadas por clientes ni revisa outboxes de dispositivos; no hubo acceso ni consulta a una instancia alojada.
- No se ejecutaron migraciones/RPC de EP-MET contra un proyecto alojado. T040 sigue pendiente antes de cualquier activación remota.
- En el contenedor local aislado `supabase_db_kipu-met-local-20261017`, se aplicó directamente con `psql -f` la migración aditiva `supabase/migrations/20261018190000_ep_mov_opening_ledger.sql`. Pasaron `goal_crud_test.sql`, `goal_reserve_test.sql`, `goal_lifecycle_test.sql` y `goal_opening_ledger_test.sql`; todos usan fixture sintético y `ROLLBACK`. La prueba nueva primero falló (RED) al no existir `internal.reconcile_opening_ledger()`, y pasó (GREEN) tras aplicar la migración.
- Comandos reproducibles desde la raíz: `docker cp supabase/migrations/20261018190000_ep_mov_opening_ledger.sql supabase_db_kipu-met-local-20261017:/tmp/20261018190000_ep_mov_opening_ledger.sql`; `docker exec supabase_db_kipu-met-local-20261017 psql -U postgres -d postgres -v ON_ERROR_STOP=1 -f /tmp/20261018190000_ep_mov_opening_ledger.sql`; para cada test: `docker cp supabase/tests/database/<archivo>.sql supabase_db_kipu-met-local-20261017:/tmp/<archivo>.sql` y `docker exec supabase_db_kipu-met-local-20261017 psql -U postgres -d postgres -v ON_ERROR_STOP=1 -f /tmp/<archivo>.sql` (copiar también `_goal_test_fixture.sql`). El `.ps1` de concurrencia fue bloqueado por el guard de ejecución de scripts; en su lugar se ejercitaron dos sesiones `psql` concurrentes con IDs sintéticos previamente verificados en cero. Resultado: una reserva `APPLIED`, otra `INSUFFICIENT_BACKING`, total reservado `80000` sobre balance confirmado `100000`; la limpieza eliminó los fixtures creados y la consulta final confirmó cero usuarios, cuentas, metas, eventos, movimientos y asientos de esos IDs.

La evidencia local cubre dominio, pantallas, migración Room, diagnóstico sintético, el ledger canónico y una carrera concurrente entre reservas. No demuestra RLS/grants ni atomicidad del backend desplegado ni sustituye la auditoría representativa de T040.

## Señal de aceptación

T004 ya está completo para la implementación local. La épica aún tiene tareas de migraciones/RPC y pruebas SQL/RLS pendientes. Además, antes de habilitar el backend alojado se debe completar T040 con datos no productivos representativos y revisar outboxes antiguos. No se usó producción.

### QA independiente T039 — 2026-10-18

- `./gradlew testDebugUnitTest --rerun-tasks`: BUILD SUCCESSFUL (exit 0, 1m31s; 33 tareas ejecutadas).
- Dispositivo conectado: SM-A165M, Android 16/API 36. `./gradlew :app:connectedDebugAndroidTest`: BUILD FAILED (exit 1, 6m33s), 381 tests, 16 failures. Las 12 pruebas instrumentadas EP-MET pasaron. El triage independiente vinculó 1 fallo al harness legado `KipuDatabaseSchemaTest#representativeVersionOneRowsSurviveValidationAndNormalReopen`: fixture v11 solo registra 1→11 y no llega a v22; no se confirmó defecto productivo, T048 corrige el harness. Los otros 15 no tienen evidencia de causalidad EP-MET; cuatro fallos de `MovementRevisionLocalTest` se reprodujeron con categoría SYSTEM `userId=null` rechazada por el planner. Suite global sigue CHANGES REQUESTED; XML: `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A165M - 16.xml`.
- Confirmé que `supabase_db_kipu-met-local-20261017` estaba healthy y que era PostgreSQL 17.11; los IDs sintéticos del fixture estaban ausentes antes de la prueba. Ejecuté allí `goal_opening_ledger_test.sql`, `goal_crud_test.sql`, `goal_reserve_test.sql` y `goal_lifecycle_test.sql`; cada comando terminó exit 0 en 0–1 s y cada script hizo `ROLLBACK`. El fixture usa dos usuarios/cuentas sintéticos y los comandos no accedieron a un proyecto alojado.
- Opening-ledger comprobó la referencia de origen dual como ambigua: `inserted=0`, `ambiguous=1`, `candidates=5`, cero filas ledger para esa referencia. CRUD comprobó creación sin eventos, retry idempotente, hash/revisión, owner y protección de historia; todavía no cubre la política aprobada de cupo Free/COMPLETED ni la reactivación atómica (T013/T015/T042/T043 pendientes). Reserve comprobó reserva sin transaction (`available_minor=80000` desde 100000), retry DUPLICATE, rechazo íntegro, owner/moneda, origen=destino sin movimiento, transferencia explícita net-zero y ausencia de ingreso/gasto implícito. Lifecycle comprobó que completar preserva reserva, archivo exige reserva cero, liberar/reasignar no crea efectos monetarios implícitos y el historial se conserva; no cubre todavía la reactivación por liberación al cupo Free (T044/T045/T046/T047 pendientes).
- `git diff --check` pasó (exit 0). No se ejecutaron migraciones alojadas ni comandos remotos. En T039 aún estaban pendientes T013/T015; su implementación y QA independiente se registran en la sección siguiente. T042/T043/T044/T045/T046/T047 siguen abiertos para completar y probar la política Free aprobada; T048 debe corregir el harness Room; T040 sigue pendiente como gate de activación remota.

### T013/T015 — cupo Free active-only y reemplazo atómico (2026-10-18)

- Backend añadió la migración local aditiva `supabase/migrations/20261018190200_ep_met_active_goal_quota_atomic_replacement.sql`, pruebas en `supabase/tests/database/goal_crud_test.sql` y actualización del contrato. El RED previo reprodujo `GOAL_LIMIT` al crear con cuatro metas `COMPLETED`; tras el cambio solo `ACTIVE` consume cupo y la reactivación a capacidad exige `replacement_goal_id` más revisión esperada.
- Backend reportó `goal_crud_test.sql`, `goal_reserve_test.sql`, `goal_lifecycle_test.sql` y `goal_opening_ledger_test.sql` PASS, cada uno con `ROLLBACK`; la limpieza de fixtures se verificó. La carrera CREATE/UPDATE dio `CREATE APPLIED`, `UPDATE REJECTED/REPLACEMENT_REQUIRED`, conteo final `ACTIVE=2`, con fixtures limpiados.
- QA independiente `t_6b704eeb` aprobó con observaciones y sin defectos confirmados: ejecutó las cuatro suites en `supabase_db_kipu-met-local-20261017`, verificó ROLLBACK y limpieza (`users=0`, `goals=0`), `git diff --check` PASS y definición efectiva del RPC. No volvió a ejecutar el script PowerShell de concurrencia por su limpieza no transaccional.
- Observaciones y límites: el cliente Android aún debe reconciliar atómicamente ambas metas antes de habilitarse; `RELEASE_GOAL` no formó parte de esta revisión y continúa en T044/T045/T046/T047. No hubo build Android, migración alojada ni despliegue. T040 sigue siendo un gate independiente.

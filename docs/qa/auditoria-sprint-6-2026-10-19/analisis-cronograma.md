# Análisis estático: guardado del cronograma de deuda

Fecha: 2026-10-19
Commit inspeccionado: `dd6758a01a236aa61053ebf8ab24f25e0c835ba5` (`main`)
Alcance: lectura estática del repositorio; no se usó ADB, no se instaló ni ejecutó APK, no se ejecutó Gradle/tests ni se accedió a bases operativas o red. Este análisis no confirma el síntoma observado ni demuestra que el APK auditado corresponda a este commit. Los documentos del producto consultados fueron los artefactos locales autorizados; no se consultó Obsidian Mind porque las herramientas `om` no estaban disponibles.

## Conclusión breve

El guardado crea un comando nuevo por invocación (`operationId` aleatorio) y asigna números de cuota superiores al máximo histórico, mientras el guardado local y RPC reemplazan las cuotas activas marcándolas `CANCELLED` y conservan sus filas/eventos. Así, guardados consecutivos después de que la pantalla refleja la revisión nueva pueden parecer una acumulación en una vista que muestre también cuotas canceladas; sin embargo, cada cronograma nuevo debe sumar exactamente al principal pendiente y las filas canceladas no reducen el principal. Durante un guardado activo, el botón está deshabilitado en Compose, pero el método del ViewModel no aplica su propio guard; dos llamadas concurridas creadas desde la misma revisión deberían competir por la revisión y la segunda ser rechazada como conflicto local. Son mecanismos e hipótesis verificables, no una causa confirmada del reporte.

## Call graph (referencias al commit indicado)

1. `DebtsNavigation.kt:167-183` crea/obtiene `DebtScheduleViewModel`, recoge estado y conecta `onSchedule = viewModel::saveSchedule` con la pantalla.
2. `DebtScheduleScreen.kt:679-693` invoca el callback y deshabilita el botón con `enabled = !state.isSaving` (`:682`); presenta “Guardando…” mientras guarda (`:692`). Esto protege la interacción UI durante el estado observado, no constituye un guard atómico dentro del ViewModel.
3. `DebtScheduleViewModel.kt:96-123`: valida sesión/deuda/cantidad/fecha; genera `UUID.randomUUID()` como identidad distinta en cada llamada (`:104`); usa `max(installmentNumber)+1` (`:105`); planifica contra `debt.remainingPrincipalMinor` (`:107`); el hash incorpora `operationId` (`:109-110`); pone `isSaving=true` solo dentro de la coroutine (`:119-121`) y llama al repositorio.
4. `SetDebtSchedule.kt:13-61`: valida deuda activa y saldo positivo; distribuye el saldo pendiente exactamente entre N cuotas, reparte residuo de unidades menores desde las primeras cuotas (`:35-45`) y deriva cada ID de cuota del operationId y número (`:76-77`). La cancelación solo retorna plan vacío (`:63-73`).
5. `OfflineFirstDebtRepository.kt:101-107`: delega a `DebtLocalDataSource.setDebtSchedule`; para `Applied`, `Duplicate` o `Retryable`, agenda sync y reconcilia recordatorios.
6. `DebtLocalDataSource.kt:459-575` realiza la operación dentro de `database.withTransaction` (`:459`). Repite primero la comprobación de recibo por usuario+operationId: mismo tipo/hash da `Duplicate`, identidad reutilizada con otro hash se rechaza (`:463-471`). Luego verifica revisión (`:472-476`), estado/reminder (`:477-480`), secuencia/IDs, fechas y suma al saldo pendiente (`:485-513`). Cancela cuotas activas `PENDING`/`PARTIAL` (`:515-520`), inserta las nuevas (`:521-535`), incrementa revisión y pone sync pendiente (`:537-545`), y guarda outbox y recibo local en la misma transacción (`:546-574`).
7. El DAO declara inserción de cuotas con conflicto `ABORT` (`DebtDao.kt:158-160`) y la entidad tiene unicidad local de `(debt_id, installment_number)` (`DebtEntities.kt:37-52`). La consulta de cuotas incluye todos los estados no borrados, ordenados por número (`DebtDao.kt:88-100`); los registros cancelados siguen siendo visibles para el flujo observado.
8. `DebtSyncScheduler.kt:88-123,126-151,154-184` reclama el outbox con lease, envía y trata `APPLIED`/`DUPLICATE` como sincronizado; red y errores transitorios quedan para retry. `DebtApi.kt:29-48,51-59` ejecuta RPC `set_debt_schedule_v1`. El resultado actualiza estado de outbox, deuda y recibo en transacción (`DebtSyncScheduler.kt:154-184`).
9. `20261008160000_s5_debt_schedule.sql:29-76` valida el payload y deduplica por operación + request hash + payload; la repetición exacta retorna el recibo previo como `DUPLICATE`. Bloquea la deuda y valida revisión (`:79-91`), deriva pendiente de principal y eventos válidos (`:93-103`), comprueba suma y secuencia (`:105-178`), cancela activas y agrega filas nuevas (`:180-198`), incrementa revisión/feed y crea recibo (`:200-218`). La RPC opera dentro de la transacción SQL implícita de la llamada.

## Hechos e invariantes documentados

- `specs/013-ep-deu-deudas-prestamos/spec.md:76-85,115-120` exige que el principal planificado sume exactamente el principal configurado y que guardar/alcanzar vencimientos no registre movimientos ni reduzca principal; reintentos no deben duplicar efectos y las cuotas canceladas conservan historial.
- `specs/013-ep-deu-deudas-prestamos/data-model.md:54-60,82-89` define importe positivo, suma exacta, unicidad `(debt_id, installment_number)`, estados derivados por liquidaciones y sync idempotente por operación/hash.
- `specs/013-ep-deu-deudas-prestamos/contracts/debt-command-contract.md:74-79` describe `SET_DEBT_SCHEDULE`: operación idempotente, cuota exacta; guardar no es pagar; reemplazar cancela las cuotas PENDING/PARTIAL y reemplaza/cancela recordatorios pendientes.
- `.specify/memory/constitution.md:70-79` exige persistencia local atómica, identificadores estables y sincronización idempotente bajo reintentos/reordenamientos; conflictos que alteren verdad financiera no se resuelven silenciosamente.
- `DebtDao.kt:25-45,48-69` calcula el principal pendiente desde principal inicial más deltas válidos, excluyendo eventos ligados a transacciones anuladas. No suma cronogramas al principal. `DebtLocalDataSource.kt:505-509` exige que las cuotas entrantes sean iguales al saldo pendiente; la RPC replica esta validación (`20261008160000_s5_debt_schedule.sql:93-103,161-163`).
- No hay una clave única local/remota que exija “solo un cronograma por deuda”: la unicidad es por número de cuota, mientras se avanza el número al mayor existente. El reemplazo conserva filas canceladas, y agrega números nuevos.

## Análisis de escenarios de duplicación

- **Replay de la misma operación**: el mismo `operationId`, hash y payload debería devolver `Duplicate` localmente/remotamente, sin insertar nuevas cuotas. Reutilizar operationId con distinto hash se rechaza. Los retries del outbox conservan la misma identidad/payload. Referencias: `DebtLocalDataSource.kt:463-471,546-574`; SQL `:65-76,212-218`.
- **Nuevo toque tras terminar un guardado**: genera operationId e IDs de cuota nuevos, lee revisión actualizada y el siguiente número disponible; el guardado reemplaza cuotas activas anteriores con `CANCELLED` y añade las nuevas. La suma del conjunto recién enviado sigue igual al pendiente; no hay efecto de caja ni de saldo por guardar cronograma.
- **Dos invocaciones rápidas concurrentes**: el CTA se deshabilita al entrar en `isSaving`, pero `saveSchedule()` no retorna al detectar `isSaving` (`DebtScheduleViewModel.kt:96-123`). Si ambas llamadas ya tomaron la misma revisión, la transacción local acepta una e incrementa revisión; la otra ve revisión obsoleta y retorna `Conflict` (`DebtLocalDataSource.kt:472-476`). En servidor también se bloquea la deuda y se chequea revisión (`SQL :79-91`). El intervalo entre toque y actualización del estado, la concurrencia real de dispatch y cualquier segundo origen de callback requieren evidencia runtime.
- **Edición con cuotas pagadas/parciales**: las filas `PAID` no se marcan canceladas; `PENDING`/`PARTIAL` sí. Aun así el total del nuevo plan debe ser el pendiente total, no el monto original ni “pendiente menos lo asignado a cuota”, porque la fuente del balance son eventos de principal. Cuotas y eventos previos conservan su identidad/referencias; la unicidad de números provoca numeración creciente. La interacción concreta de una cuota parcial con referencias históricas debe verificarse con datos de prueba controlados, no con cuenta real.

## Efectos esperados según código

- **Cantidad mostrada**: el flujo local emite todas las cuotas no borradas, incluidas las `CANCELLED` (`DebtDao.kt:88-100`); en consecuencia, si el composable lista todas sin filtrar estado, las sustituciones pueden aparentar acumulación. La persistencia conserva intencionalmente historial; hay que verificar en pantalla qué estados se muestran/distinguen.
- **Suma**: para cada comando aceptado, la suma de las cuotas nuevas debe ser el saldo principal pendiente observado al validar. Las antiguas activas pasan a canceladas; sumar indiscriminadamente filas activas+canceladas daría un total superior, pero no representa el cronograma vigente.
- **Principal/caja/estado**: el comando solo actualiza cuotas, `reminderLeadDays`, revisión y sync; respuesta indica `cash_delta_minor=0` en servidor (`SQL :200-218`). No crea evento de deuda ni movimiento de caja. La cuota planificada no es pago. Un cambio de principal requiere un evento de pago/ajuste/condonación distinto.
- **Atomicidad**: en local, cuotas antiguas, nuevas, deuda, outbox y recibo se modifican dentro de una transacción Room; en servidor la función RPC aplica mutaciones y recibo en una transacción PostgreSQL. Un error en inserción/constraint no debería dejar reemplazo parcial.

## Hipótesis falsables / pendientes de QA

1. Repetir el mismo gesto varias veces durante “Guardando…”: confirmar número de llamadas/resultados y que solo exista un comando local. Si se duplica lo visible en esa ventana, registrar estado UI, tiempos, revisión y IDs de operation/cuotas desde instrumentación de test (no logs personales).
2. Tras guardar una vez y esperar resultado, guardar otra vez sin cambiar nada: comparar cuotas `PENDING` vs `CANCELLED`, números e IDs. El código predice historial cancelado más un nuevo set vigente, no dos sets vigentes.
3. Hacer retry tras desconexión solo dentro del procedimiento QA autorizado: verificar que el outbox repite el mismo operationId/hash y que el servidor devuelve `DUPLICATE`; contar cuotas activas antes/después. No inferir resultado de una observación solo visual.
4. Diferenciar defecto visual de persistencia: comprobar en la pantalla si cada cuota duplicada está pendiente o cancelada; contrastar conteo de activas con filas locales/snapshot de pruebas aisladas. No extraer base de datos ni finanzas de la cuenta real.
5. Con cuota parcialmente pagada, comparar antes/después saldo derivado por eventos, pagos y estado/importe de cuotas; verificar que pagos y principal no cambien al guardar. Capturar si el formulario envía el saldo pendiente esperado y si RPC devuelve conflicto/rechazo.
6. La atribución al APK queda pendiente de establecer correspondencia entre APK/version instalada y commit/build. No fue posible ni autorizado en esta tarjeta.

No se ejecutaron pruebas. Los tests citados en `specs/013-ep-deu-deudas-prestamos/tasks.md` son tareas/cobertura documentadas, no resultados de ejecución.

## Trazabilidad de estado e integridad

Estado antes del análisis: `git status --short --branch` mostró `## main...origin/main` y `?? .worktrees/` (untracked preexistente, ajeno a este entregable). HEAD: `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`. No se modificó código, test, artefacto Spec Kit, informe/capturas/logs de QA ni estado de dispositivo/red. El único archivo nuevo autorizado es este Markdown.

SHA-256 de fuentes relevantes leídas:

- `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleViewModel.kt`: `63b6f6d991ff0b80482e7350357dc7b36f5f588ff73d6a379cd88f7a4c3644ae`
- `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/SetDebtSchedule.kt`: `06a7fdb45fc479d4880b34a3563ed2c09a2b596849e96437e694e4d8ebad0a7a`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt`: `91a674c3f8309479d306e7560ea74a32ac3a2e46c7c134031d44663075caab6f`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtDao.kt`: `02e3a73bbcf1a444ecd7f807c5500b6b9754b7516533d2f8b4fe64e1d96dc0b0`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtEntities.kt`: `64e329b43ef4e0280383315c291d605b8ebf9c60df075721f4c808cb69f2cd94`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtOutboxDao.kt`: `4fb00aa6e48b54ca26bc18baae05c90566d2e874cb1fb38d49c3e62eeeec9104`
- `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleScreen.kt`: `de7a2655e854e565eb3ce02a9335869fc376657ba4722935d653aa88ade35cb1`
- `app/src/main/java/com/kipu/app/navigation/DebtsNavigation.kt`: `84476dd7618815227a4ae0979f3941c1b3dcebd7dec2e255bd4747ab528e2356`
- `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt`: `2c3e3043b1688b63ebbf0da1525faeee687089e2029328a1454b227f79d2187d`
- `app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt`: `a9196c537e290af8ef46788933a6821633c9e593fac91cb08219ab67ebc6d458`
- `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt`: `3e12217503292cb1eb4757bddc165b15d00cc38d1c7c28d78009e6ea332c2b94`
- `supabase/migrations/20261008160000_s5_debt_schedule.sql`: `13b09951b0e671b40bd85f662f949d07258460839a6923e73cb05bc2e3b55569`

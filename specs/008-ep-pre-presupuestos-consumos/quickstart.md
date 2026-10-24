# Quickstart: EP-PRE — Validación y resultados focalizados de presupuestos

La guía de escenarios se redactó antes de la implementación. La sección final registra la verificación focalizada ejecutada el 2026-10-24; no representa aceptación completa del EP-PRE ni cierre de los gates pendientes.

## Requisitos previos de validación

1. Checkout con dependencias bloqueantes integradas y versiones de código identificadas.
2. Contrato aprobado de HU-25 con acceso a reembolso real vinculado y ajuste del periodo original; fixture por sí sola no cuenta como aceptación.
3. Interface HU-42/EP-NOT aprobada para alertas, deduplicación y navegación.
4. Decisión aprobada de periodo/calendario (G1), schema local/remote (G4) y borrado/sync (G5).
5. Decisión de producto sobre moneda elegible y explicación al usuario (G6); la recomendación del modelo de coincidencia sin conversión no es aprobación.
6. Entorno local de pruebas y datos sintéticos propios de usuario; nunca usar PII o datos reales. DB/migraciones solo en entorno local desechable y tras autorización del implementador.
7. Antes de ejecutar comandos, revisar build scripts y nombres de tareas actuales. Los comandos que sí se ejecutaron y sus resultados están registrados al final de este documento.

## Escenarios de aceptación que se deben automatizar

| Escenario | Preparación | Resultado esperado |
|---|---|---|
| Alta y consulta (HU-36) | Dueño propio, categoría válida, Free con cupo, límite S/500; gastos elegibles acumulados S/120 | Límite 50000 minor units PEN, consumo 12000, restante 38000, porcentaje 24%; atomicidad local antes del éxito |
| Alcances y jerarquía (HU-36/37) | Presupuestos total, padre Alimentación y subcategoría Restaurantes; gasto confirmado único S/50 | Cada consulta coincidente refleja 5000; agregado global = 5000, no 10000/15000 |
| Exclusiones | Gastos confirmados junto a transferencia, principal, meta, pendiente y VOIDED | Solo gasto elegible incluido, sin asiento/total duplicado |
| Rango inválido (HU-36) | Fecha final < fecha inicial | Rechazo sin presupuesto/periodo parcial |
| Free quota (HU-36) | Dos activos existentes | Tercera alta bloqueada; los objetos/datos previos siguen intactos |
| Renovación (HU-37) | Periodo mensual previo S/500 con S/420; primer día de ciclo nuevo; reintentar varias veces | Un periodo nuevo, límite 50000, consumo propio, periodo anterior intacto |
| Custom terminado (HU-37) | Budget custom cuyo end pasó | No nace otro periodo automáticamente |
| Alertas (HU-37) | Consumo cruza 50/80/100 y repite consulta/comando | Un aviso por cruce/umbral/periodo y ningún duplicado |
| HU-25 real (HU-37/38) | Compra original elegible en periodo A, reembolso HU-25 parcial/tardío confirmado | Caja se rige por HU-25 y consumo de A se ajusta exactamente una vez; no cambia movimiento original por recalculo; sync/replay estable |
| Editar (HU-38) | Presupuesto límite 500 con historial de periodo cerrado | Límite vigente se hace 600 y porcentaje actual cambia; periodos cerrados conservan límite/consumo |
| Finalizar (HU-38) | Budget recurrente con historial | No nuevas vigencias ni avisos; consulta histórica intacta |
| Eliminar/Cancelar (HU-38) | Presupuesto con varios periodos y transacciones asociadas | Confirmar borra budget/periodos únicamente; cancelar no altera nada; gastos/ledger/cuentas/categorías/saldos no cambian |
| Seguridad/offline | Dos propietarios, red caída, retry/reinicio; sincronización posterior | Sin lectura/escritura cross-user; persistencia local confirmada; retry no duplica ni resucita borrado |

## Evidencia esperada para aceptar

- Tests unitarios del dominio para dinero, elegibilidad, intervalos, categoría única y umbrales.
- Tests Room y migración de upgrade con datos previos representativos, rollback/preflight y sumas/IDs financieros sin cambios.
- Tests de sync/seguridad con doble replay, dos propietarios y dispositivo atrasado.
- E2E del reembolso con capacidad real de HU-25 y notificación real de HU-42.
- Evidencia accesible de estados vacío, error, offline, límite, confirmación destructiva y navegación.
- Reporte identifica revisiones/commits/ambiente y comandos exactos; no marcar aceptación con fixtures o modelo documental.

## Alcance del borrador inicial

La indicación de no ejecutar Gradle pertenecía al borrador de planificación, antes de contar con la implementación integrada. La verificación focalizada posterior se limita a los comandos y clases listados a continuación; no se ejecutó la suite instrumentada completa ni una migración en este pase.

## Verificación focalizada ejecutada — 2026-10-24

Entorno: worktree `s7-e2e`, emulador `emulator-5554` (Pixel_10 AVD), Room real en memoria/archivo según cada integración. No se usó Supabase remoto/hosted. El replay atrasado se simuló con Ktor `MockEngine` local en memoria.

### Comandos y resultados

- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.budgets.BudgetHierarchyAlertsIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.budgets.BudgetCrudFinancialIsolationIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.budgets.data.sync.BudgetTombstoneReplayIntegrationTest"` — `BUILD SUCCESSFUL`, 1/1 instrumentado.
- `.\gradlew.bat :app:testDebugUnitTest --tests com.kipu.app.feature.budgets.domain.ExpenseEligibilityTest` — `BUILD SUCCESSFUL`, 1/1 JVM.
- `.\gradlew.bat :app:testDebugUnitTest` — `BUILD SUCCESSFUL`, 800 tests descubiertos, 0 fallos, 0 errores y 2 omitidos.

T010 tuvo primero resultado RED en la consulta Room: consumo esperado de 3.700 minor units, resultado 8.700 por incluir pagos de principal. El filtro de `MovementDao.getExpensesInPeriod` se corrigió para excluir `DEBT_PAYMENT` y otras operaciones no elegibles, y `ExpenseEligibilityTest` pasó 1/1.

### Cobertura integrada comprobada

- HU-25/T022: compra de S/100 en septiembre, presupuesto mensual S/500 y reembolso parcial de S/40 en octubre. El consumo neto de septiembre pasa a S/60; octubre queda en S/0 de gasto; caja recupera S/40. El replay de reembolso no duplica asientos. Se rechaza S/70 cuando restan S/60; el reembolso final de S/60 deja el consumo en S/0. La transacción y el ledger originales permanecen iguales.
- HU-37: gasto S/50 en Restaurantes aparece una vez como S/50 en Restaurantes, Alimentación y el presupuesto total. Con límite S/100, el gasto cambia de S/49 a S/51; al cruzar el 50% queda un aviso `BUDGET_ALERT` en Room, y tres reevaluaciones adicionales no crean duplicados.
- HU-38/T029: crear, editar límite, finalizar y eliminar confirmado; snapshots de cuenta, movimiento, ledger, saldos y conteos coinciden antes/después. El tombstone local bloquea creación atrasada y una respuesta antigua de presupuesto/periodo, entregada al worker por `MockEngine` local, no resucita el presupuesto.

### Pendiente

No se ejecutó la suite instrumentada completa, la migración de Room en runtime, auditoría manual TalkBack/UI, ni revisión independiente. T023 (presentación de historial/aviso), T031, T032, T033 y la revisión EP-MOV T149 siguen abiertas; este resultado no declara aceptación integral. Los detalles y hashes de implementación se registran en `specs/004-ep-mov-movimientos-ledger/verification-report.md`.

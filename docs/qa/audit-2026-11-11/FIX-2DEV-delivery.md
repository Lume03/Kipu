# FIX-2DEV — entrega del 2026-11-14

**Veredicto: APPROVED para 2DEV-01, 2DEV-02 y 2DEV-03 en los escenarios verificados.** Se corrigieron los tres fallos, se añadieron regresiones y se probaron apertura, edición, reembolso/anulación, conflicto real y regla de comercio con ambos emuladores. La observación de arranque lento de A se documenta por separado; este informe no certifica ausencia universal de ANR.

## Entorno y alcance

- Worktree `C:\Users\Alume\orca\workspaces\Kipu\audit-fixes`, rama `Lume03/audit-fixes`. Sin commits, push, Spec Kit, cambios SQL remotos ni uso de MCP/SQL remoto.
- A: `emulator-5554`, transporte `localhost:5555`; B: `emulator-5556`, transporte `localhost:5557`. Siempre adb del SDK, puerto 5038 y serial explícito. No se operó sobre el A16 ni sobre el servidor 5037.
- Ambas instalaciones usaron `install -r`, conservando sesión y datos QA. Las mutaciones de negocio se hicieron mediante la UI. Las extracciones de Room con `run-as` fueron de lectura.
- Evidencia externa **E**: `C:\Users\Alume\AppData\Local\Temp\kipu-2dev-2026-11-14\`, archivos con prefijo `fix-`. `fix-events.txt` registra los hitos. Horas del host en America/Lima; logcat usa UTC.

Hubo dos builds: la primera verificó los escenarios de dinero; la segunda añadió únicamente la protección del borrador de categoría descubierta al probar las reglas, más su test. El código financiero no cambió entre builds. La segunda está instalada en **ambos** dispositivos y se comprobó el SHA-256 del APK instalado:

| Build | SHA-256 | Ventana de verificación |
| --- | --- | --- |
| Correcciones iniciales | `10cd7a7de6c51460b2908e380a1d161c1b0b1e6db2d339d64587d18648d4c2c8` | Apertura, edición, reembolso, anulación y conflicto; B mantuvo PID **12167** durante todos esos escenarios. |
| Final, instalada | `def9b31415c65e0cca2c8e3e951297f627045a446fe763b62de9631f75e0b9a9` | Borrador, reglas, foreground, conservación del estado financiero y comprobaciones finales; B mantuvo PID **3025**, incluso al ir a Inicio y regresar. |

La segunda instalación fue una nueva preparación de prueba. No se usó reiniciar B como mecanismo para recibir cambios dentro de ninguna de las dos ventanas.

## Causas raíz y correcciones

### 2DEV-03 — Reembolso sincronizado sin reducción de deuda

`CREDIT_INSTALLMENT_BALANCES_SQL` calcula los reembolsos desde `transaction_links`. La hidratación refrescaba los estados de cuotas sin crear ese vínculo; además, el DTO y la proyección financiera versionada no transportaban la compra asociada. El ingreso podía figurar `SYNCED` mientras la consulta de deuda seguía sin crédito por reembolso.

`FinancialInstrumentsApi.pullChanges` completa cada página que contiene reembolsos con una lectura autenticada de la relación REST existente, filtrada por propietario, tipo `REFUND` y targets de la página. Añade `refund_purchase_id` al payload interno. `MovementHydration` valida la referencia local, inserta el vínculo idempotentemente y después refresca cuotas. Una relación ausente o incoherente hace fallar la página, sin confirmar su checkpoint. No se infiere la compra por importe o tarjeta.

La repetición completa única del feed recuperó también los dos vínculos ausentes de los reembolsos antiguos de B. Un reembolso nuevo redujo su deuda a 3, y su anulación restauró 4 en ambos dispositivos.

### 2DEV-02 — Aceptar servidor fallaba y conservaba el aviso

El feed sustituía el monto y reconciliaba el ledger, pero conservaba la referencia de revisión local y no registraba la cabecera oficial reconocida. La extracción inicial de B mostró monto **2100**, revisión **4**, `acknowledged_revision=NULL`, `sync_status=SYNCED`, referencia local y outbox `CONFLICT`, con una propuesta sin resolver. El descarte intentaba compensar otra vez la rama local sobre un ledger ya reconciliado y fallaba sus invariantes.

La hidratación conserva evidencia de la cabecera `REMOTE`, actualiza `acknowledged_revision` y `current_revision_id`; el descarte de un conflicto ya hidratado cierra los comandos y sus propuestas sin compensar de nuevo el valor oficial. El outbox termina `DISCARDED`. `discardProposal` busca también el comando `CONFLICT`, que no aparecía en la antigua consulta de pendientes, y usa la misma recuperación. La evidencia local rechazada permanece en el historial.

Se resolvieron tanto el rechazo heredado de la auditoría como un conflicto nuevo, creado desde una base común y con ambos dispositivos realmente offline; sus outboxes estaban `PENDING`, `expected_revision=5` y Wi-Fi desactivado antes de conectar A primero.

### 2DEV-01 — Faltaban disparadores de descarga durante la sesión

El arranque/restauración y las escrituras podían activar sincronización, pero volver al foreground o al dashboard/historial no garantizaba una descarga. Categorías y reglas de comercio utilizan además hidratación por snapshots, fuera del pull financiero compartido.

`ForegroundSyncCoordinator` comparte un mínimo de **30 s** entre foreground, navegación y un tick mientras la actividad está `STARTED`. `MainActivity.onStart` y la entrada al dashboard/historial lo activan. Programa los schedulers existentes de movimientos —que descarga mediante `SharedChangeFeed`— y categorías —que incluye reglas CAT1-04—; ambos usan `REPLACE`. El tick se cancela al dejar de estar visible.

Se revisaron los periódicos de WorkManager: existen para recurrencias, planes, widgets y mantenimiento, pero no un refresco periódico equivalente de dinero/categorías. No se añadió otro periódico: el caso probado necesita refresco durante la sesión activa, con cadencia de 30 s.

Durante la prueba de reglas se descubrió que una nueva instantánea reemplazaba una selección de categoría sin guardar. `MerchantCategoryPreferenceViewModel` ahora conserva una elección válida marcada como borrador; tras guardarla vuelve a seguir los cambios remotos. Se comprobó con un test que fallaba antes del cambio y con la UI de B: la elección persistió **71,3 s** y luego se guardó.

### Reparación de datos locales existentes

`SyncMovementsWorker` ejecuta una sola repetición completa del feed por propietario y versión de reparación para recuperar vínculos y cabeceras ya descargados por la build anterior. Los comandos pendientes mantienen sus protecciones existentes. No se cambia el esquema Room ni se borran datos.

## Pruebas automáticas

Comando final:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug
```

**BUILD SUCCESSFUL; 1.762 tests unitarios, cero fallos y cero errores.** Compilación Kotlin e instrumentada correctas. Log de la ejecución: `fix-final-verification.txt`. La comprobación posterior reutilizó las tareas al día, sin volver a ejecutar la suite, y registró explícitamente **`GRADLE_EXIT_CODE=0`** (`fix-final-command-exit.txt`). `git diff --check` también pasó. No se ejecutó una suite instrumentada global.

Tests añadidos:

- `CardPurchaseVoidAndRefundRoomTest.hydratedRefundReducesDebtAndReplayAndVoidRemainIdempotent`: hidratación real con Room, compra 400, reembolso 100, deuda 300, repetición sin duplicar y anulación que devuelve 400.
- `CardPurchaseVoidAndRefundRoomTest.acceptingHydratedServerHeadClosesConflictWithoutCompensatingRemoteLedger`: revisión optimista, rechazo, hidratación real y descarte conservando monto y ledger oficiales.
- `CardPurchaseVoidAndRefundRoomTest.discardingHydratedProposalClosesTerminalConflict`: misma regresión desde la propuesta.
- `FinancialInstrumentsApiTest.financial feed joins refunds to owner scoped links before checkpointing`: MockEngine verifica propietario, tipo, targets y enriquecimiento de la página.
- `ForegroundSyncCoordinatorTest.foregroundAndNavigationShareThrottleAndRefreshBothTransports`: llamadas a 0/1/29.999/30.000 ms y cambio de propietario.
- `MerchantCategoryPreferenceViewModelTest.refresh preserves an unsaved choice and follows remote preference after save`: conserva el borrador durante refresh y vuelve a seguir cambios remotos tras guardar.

Se actualizó además el fixture de hidratación de reembolsos para incluir la compra y su relación, en vez de aceptar un reembolso huérfano. Los tests existentes de hidratación de categorías y reglas forman parte de la suite final; no se repitió manualmente el escenario 7 de renombrar categorías.

## Resultados en dos dispositivos

Los tiempos son **cotas de observación** desde la acción indicada e incluyen UI, navegación y consulta de estado; no son latencia de red aislada ni un SLA máximo de 30 s.

| Escenario | Antes | Ahora | Tiempo observado |
| --- | --- | --- | --- |
| 2. Corregir apertura | B permanecía atrasado hasta reiniciar. | Apertura 300→400 en A; ambos dashboards muestran **363**. B permaneció en su dashboard. | **≤46,2 s** desde guardar. |
| Edición simple | Recepción acreditada mediante reinicio. | Gasto 21→23 en A; B muestra saldo **361**, navegando entre historial y dashboard. | **≤17,7 s** desde guardar. |
| 4. Reembolso e historial | A deuda 3, B deuda 4 pese a ingreso sincronizado. | Ambos deuda **3**, disponible **997**; el historial recibido abre sin error. | **≤27,1 s** desde confirmar reembolso de 1 sobre compra de 4. |
| 4. Anular reembolso en B | La reversión se había comprobado con reinicio receptor. | Ambos deuda **4**, disponible **996**, sin reinicio receptor. | B local **≤8,4 s**; A recibe **≤35,1 s** desde confirmar. |
| 6. Conflicto real nuevo | Aceptar servidor fallaba y dejaba el aviso. | Base común 23; offline A→31 y B→32; A conecta primero; B acepta **31**, sin aviso, último outbox `DISCARDED`, cero propuestas pendientes. | Conflicto B **≤9,5 s** desde conectar; aceptar **≤7,5 s**. A confirmó su escritura en **≤108,4 s** desde reconectar. |
| Rechazo heredado de auditoría | Aviso persistente, monto visible 21. | Aceptar servidor conserva 21, descarta el comando y elimina el aviso. | **≤6,5 s** desde la acción. |
| 8. Regla CAT1-04 y foreground | B requería reinicio para recibir la regla. | Se verificó una preferencia intermedia a Alimentación y luego A guardó **Bembos → Alimentación › Cafeterías**. B mantenía revisión 2 en Inicio; al regresar recibió revisión **3**, `SYNCED`, y la UI actualizó la regla sin reiniciar. | **≤10,2 s** desde volver al foreground; **≤65,7 s** desde guardar en A, cuya confirmación se observó a **≤54,6 s**. |

Evidencias principales en E:

- Apertura: `fix-02-A-opening400`, `fix-02-B-opening400`; edición: `fix-simple-B-received`.
- Reembolso: `fix-04-A-debt3`, `fix-04-B-debt3`, `fix-04-B-refund-history`, `fix-04-A-void-debt4`, `fix-04-B-void-debt4`.
- Conflicto: `fix-06-A-baseline23`, `fix-06-B-baseline23`, `fix-06-*-offline*`, `fix-06-A-first-synced31`, `fix-06-B-conflict-new`, `fix-06-B-resolved-new`, `fix-06-B-final-kipu.db`.
- Rechazo previo: `fix-B-old-conflict-resolved`, `fix-old-conflict-B-kipu.db`.
- Borrador: `fix-draft-B-selected-root`, `fix-draft-B-after-refresh`, `fix-draft-B-saved-root`.
- Regla final: `fix-08-A-saved-cafeterias`, `fix-08-B-before-resume-kipu.db`, `fix-08-B-foreground-cafeterias`.
- Capturas finales: `fix-final-A-dashboard`, `fix-final-B-dashboard`. Las capturas citadas tienen PNG y XML homónimos.

## Estabilidad y límites

Cero **`FATAL EXCEPTION`** en las capturas continuas de cada proceso recogidas en ambas ventanas y en los buffers completos finales de ambos dispositivos. Archivos: `fix-A-continuous.log`, `fix-B-continuous.log`, `fix-A-final-continuous.log`, `fix-B-final-continuous.log`, `fix-final-A-logcat-all.txt`, `fix-final-B-logcat-all.txt`. Los buffers son finitos; las capturas describen estas ventanas.

A tuvo un arranque inicial lento, con verificación de clases, frames omitidos y diálogo ANR; el primer proceso murió y se repitió la preparación de A. Se ejecutó compilación/verificación ART local del paquete, sin borrar datos. No se aisló la causa del arranque lento y no se atribuye por suposición a los tres bugs. Los arranques posteriores observados fueron aproximadamente 22,5 s y 27,4 s hasta la primera pantalla. La espera y preparación de A quedan fuera de los tiempos de mutación de la tabla; el retraso real de 108,4 s al reconectar A sí queda incluido.

## Migración SQL

**Ninguna.** Se usa la relación REST y los permisos/RLS existentes de `transaction_links` (migración versionada `20261024182114_ep_mov_refund_v1.sql`). No se desplegó ni aplicó SQL remoto y no hay migración pendiente de subir. No fue necesario iniciar Supabase local ni ejecutar pgTAP para esta corrección de cliente.

## Estado final

Ambos emuladores quedan encendidos, con sesión QA, Wi-Fi activo, modo avión **0**, animaciones restauradas a **1** y app abierta en el dashboard. APK final idéntica en ambos. PID final A **28661**, B **3025**.

- Cuenta `QA2-ahorro`: **S/ 353**, apertura efectiva **S/ 400**.
- Gasto `QA2-m1`: **S/ 31**, versión servidor; B descartó su propuesta de 32.
- Tarjeta `QA2-tarjeta`: deuda **S/ 4**, crédito disponible **S/ 996**. El reembolso nuevo de 1 quedó anulado tras validar alta, historial y reversión.
- Regla: **Bembos → Alimentación › Cafeterías**, revisión **3**, `SYNCED` en ambos.
- Extracciones finales de ambos: **0** outboxes `CONFLICT`, **0** propuestas sin resolver (`fix-final-state-*-kipu.db`). Los restantes datos QA se conservaron.

## Archivos modificados

- `app/src/main/java/com/kipu/app/MainActivity.kt`
- `app/src/main/java/com/kipu/app/core/sync/ForegroundSyncCoordinator.kt`
- `app/src/main/java/com/kipu/app/feature/accounts/data/remote/FinancialInstrumentsApi.kt`
- `app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncChangePayloads.kt`
- `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt`
- `app/src/test/java/com/kipu/app/core/sync/ForegroundSyncCoordinatorTest.kt`
- `app/src/test/java/com/kipu/app/feature/accounts/data/remote/FinancialInstrumentsApiTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/presentation/MerchantCategoryPreferenceViewModelTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/data/local/CardPurchaseVoidAndRefundRoomTest.kt`
- `docs/qa/audit-2026-11-11/FIX-2DEV-delivery.md`

# Diagnóstico estático complementario — sincronización, edición y BUG-001

Fecha de inspección: 2026-10-20. Checkout: `main`, HEAD `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`. Alcance: lectura de fuentes y documentos locales, sin ADB, dispositivo/app/sesión operativa, DB operativa, Gradle/tests, red ni cambios de código. El APK observado por QA no está vinculado de manera probatoria a este checkout; ninguna atribución de runtime a este commit es concluyente. Obsidian Mind no se consultó (herramientas `om` no expuestas).

## 1. Outbox PENDING sin intentos: flujo y posibilidades

### Flujo estático

1. La aplicación declara `KipuApplication` como `Application` (`app/src/main/AndroidManifest.xml:8-10`). `KipuApplication` proporciona configuración de WorkManager con `HiltWorkerFactory` (`app/src/main/java/com/kipu/app/KipuApplication.kt:9-18`). El manifest quita el inicializador automático de WorkManager (`AndroidManifest.xml:18-25`); esto no elimina el WorkManager configurado por `Configuration.Provider`, pero convierte el arranque/configuración efectiva del proveedor en condición runtime que aquí no se inspeccionó.
2. El binding Hilt enlaza `DebtSyncScheduler` a `WorkManagerDebtSyncScheduler` (`feature/debts/di/DebtsModule.kt:18-28`). Existe `NoOpDebtSyncScheduler` (`data/sync/DebtSyncScheduler.kt:36-42`), pero no aparece enlazado por este módulo. Su existencia por sí sola no demuestra inyección NoOp en la app. `OfflineFirstDebtRepository` recibe el scheduler por constructor y llama `schedule(userId)` en aperturas, liquidaciones, set schedule, cierre y otras mutaciones aceptadas (`data/OfflineFirstDebtRepository.kt:31-36,57-67,93-115`). Para `SET_DEBT_SCHEDULE`, `Applied`, `Duplicate` y `Retryable` disparan agenda (`:101-107`). El camino visible no contiene un gating Premium/config previo a dicho enqueue.
3. El scheduler crea `OneTimeWorkRequest` para `SyncDebtChangesWorker`, requiere `NetworkType.CONNECTED`, agrega userId, backoff exponencial desde 30 s y usa trabajo único `debt-sync_<userId>` con política `KEEP` (`data/sync/DebtSyncScheduler.kt:45-63`). `KEEP` puede conservar el trabajo único ya encolado en vez de crear un nuevo request; en una observación debe identificarse el existente y su estado, no inferir “no enqueue” por ausencia de un request adicional.
4. Al ejecutar, el Worker compara el owner objetivo (input o owner activo) con `currentOwner?.verifiedUserId`; owner ausente/no coincidente retorna `Result.retry()` antes de reclamar comandos (`DebtSyncScheduler.kt:80-86`). Para owner verificado reclama por lotes hasta 10, procesa comandos, hace pull del feed y reconcilia recordatorios (`:88-98`). `DebtOutboxDao.claimPending` reclama solo filas PENDING/RETRY vencidas o IN_FLIGHT con lease vencido; cambia a IN_FLIGHT mediante actualización condicional transaccional (`data/local/DebtOutboxDao.kt:14-41`). Los PENDING sin intento son compatibles con Worker aún no ejecutado, petición retenida por constraints, owner que bloquea antes del claim, o error/versión runtime distinta; no prueban fallo del worker.
5. `DebtApi.execute` mapea `OPEN_DEBT` y `SET_DEBT_SCHEDULE` a RPC y obtiene sesión autenticada; no tener sesión retorna 401, fallos HTTP quedan diferenciados de red (`data/remote/DebtApi.kt:29-48,51-60`). Worker clasifica 401/408/429/5xx como retry, otros HTTP como fallo permanente; errores de red son retry. Respuestas `APPLIED`/`DUPLICATE` sincronizan; `CONFLICT`, `REJECTED`, estado desconocido tienen caminos diferenciados (`DebtSyncScheduler.kt:100-151`). El intento/last_error se escribe al terminar claim/process (`:154-184`); ausencia de intento/error en snapshot puede significar que no llegó a esa fase.

### Evaluación de evidencia y límites

- QA reporta 8 `SET_DEBT_SCHEDULE` y 1 `OPEN_DEBT` en `PENDING`, `attempt_count=0`, sin `last_error_code` tras 20 s, con Wi-Fi validada (log local autorizado, `logs/BUG-001-local-persistencia.txt:12-14`). Es una observación puntual del Room local, no prueba de conectividad efectiva de proceso, estado de WorkManager, sesión remota válida, servidor, ni ventana de ejecución garantizada.
- Código demuestra enqueue por repositorio tras resultados previstos y binding concreto a scheduler WorkManager. No demuestra que en el dispositivo QA ese binario use ese código, que el worker se haya iniciado, que exista su registro, que WorkManager haya resuelto constraints, o el estado del owner/sesión. No se encontró una llamada de negocio de backend realizada en esta investigación.
- No se comprobó causa raíz: causas condicionales a verificar incluyen worker encolado/ENQUEUED a la espera de red/Doze/batería, trabajo KEEP preexistente, owner inactivo/no verificado, error de inicialización/configuración, sesión autenticada no disponible (que solo se conocería después de entrar al API) y diferencia APK-checkout. No confundir permiso de notificaciones con requisito para sincronizar este Worker; el constraint aquí es CONNECTED.

### Checklist QA, solo lectura y sin forzar

1. Registrar timestamp monotónico/UTC del snapshot y si Wi-Fi aparece conectada/validada; no alternar red, reiniciar ni ejecutar jobs manualmente.
2. Sin abrir/copiar DB completa, capturar únicamente campos agregados del fixture QA ya autorizado: estados/cantidades por tipo, attempt_count, next_attempt_at, last_error_code, updated_at. No incluir userId, payload, token, UUIDs ni otras filas.
3. Consultar visualmente/mediante observabilidad autorizada el estado ya existente del trabajo único `debt-sync_<owner>` (ENQUEUED/RUNNING/BLOCKED/FAILED/SUCCEEDED, constraints, próxima ejecución); no cancelar, encolar, reintentar ni lanzar worker. Preservar evidencia de que es lectura.
4. Anotar si owner local verificado y sesión remota autenticada/refresh están disponibles sin exponer credenciales; no cerrar/abrir sesión para provocar cambios.
5. Si hay logs existentes, recoger solo códigos/etapas sanitizados (worker started, guard owner, claim, API status/network class); nunca headers/payload/tokens/PII. No activar logging adicional que cambie el build.
6. Releer el mismo agregado filtrado del fixture en un momento posterior acordado por QA, sin SQL de escritura. Correlacionar estado del job con si hubo incremento de attempt/last_error. No llamar RPC financiera ni probar sync mediante mutación.
7. Si runtime evidencia ausencia de enqueue aun con código/binario identificado, registrar artefacto/version/build id y cadena de enqueue; de lo contrario dejar causa como indeterminada. No atribuir a servidor remoto sin telemetría autorizada.

## 2. Vista en blanco después de editar movimiento

### Flujo estático

- `movementsDestinations` registra la ruta editor y conserva el destino previo; al guardar escribe `movement_saved=true` en el `savedStateHandle` de la entrada anterior y ejecuta `popBackStack()` (`app/src/main/java/com/kipu/app/navigation/MovementsNavigation.kt:81-101`). El editor se abre desde history (`:106-123`).
- `MovementEditorRoute` recoge estado con Lifecycle, carga por `transactionId`, colecta eventos y llama `onDismiss` ante `CloseEditor`; el evento de revisión exitosa también activa `onSaved` (`feature/movements/presentation/MovementEditorSheet.kt:93-134`). La navegación realmente hace pop desde el callback `onSaved` del destino; el ViewModel emite `TransactionRevised` y luego `CloseEditor` (`MovementEditorViewModel.kt:359-364`), por lo que hay potencial de dos eventos de cierre para una misma operación: primer evento puede hacer pop y el segundo intentar pop adicional en la entrada que queda. Static no establece que ese segundo pop quite historia visible o cause blanco (el NavController puede no completar un pop cuando no hay destino previo), pero es un escenario concreto que amerita verificar con timeline.
- `onSave` evita llamada si `isSpecialized`, `isSaving` o conflicto; crea revisión con UUID idempotente, llama al caso de uso; éxito marca `isSuccess` y emite ambos eventos. Conflicto recarga, rechazo conserva pantalla y expone `generalError` (`MovementEditorViewModel.kt:301-385`). `loadTransaction` establece `isLoading`, obtiene head y transacción, informa “No se encontró…” si faltan y en éxito completa campos; no se observa allí un catch que convierta excepciones de consultas en estado de error (`:181-246`).
- El CTA de la hoja queda habilitado solo con cambios, no especializado, sin conflicto ni guardando (`MovementEditorSheet.kt:571-588`). El destino history usa `savedStateHandle` como flujo y consume mensaje al mostrarse (`MovementsNavigation.kt:106-112`). Las pestañas root se conservan compuestas y animan opacidad/offset (`AccountsNavigation.kt:101-123`, `RetainedRootTabs.kt:19-67`): al volver de un editor a otra raíz, transición/selección es otra variable independiente.

### Qué se puede inferir

El reporte QA de blanco persistente tras relanzar hasta navegar a Movimientos describe estado de presentación/ruta, no prueba pérdida o corrupción de movimiento. La existencia de una ruta concreta de guardado con pop, más de un evento de cierre, ausencia de manejo explícito de excepción en `loadTransaction` y retención/animación de roots hacen plausibles fallos de navegación/renderizado, pero no verifican causa. No afirmar bug confirmado ni atribuirlo al APK sin provenance.

### Escenarios y probes para QA (solo lectura)

1. Antes de cualquier nueva prueba, documentar versión/build/hash instalados solo si QA ya dispone de ello; si no, marcar desconocido. Registrar ruta visible, pestaña seleccionada y si aparece barra de navegación; no extraer datos personales.
2. Para una edición controlada ya autorizada: registrar en vídeo/capturas la secuencia guardar → primer frame de feedback → destino visible; distinguir ruta editor, history, dashboard/tab y pantalla en blanco. Anotar si la hoja/editor desaparece y si vuelve history antes de quedar blanco.
3. Sin generar otra edición, observar una vez el mismo estado tras relanzamiento y luego tras navegar manualmente a Movimientos (comportamiento ya informado). Esto diferencia ruta restaurada defectuosa de contenido inexistente; no borrar estado ni repetir guardado.
4. Si se conserva logcat existente de la sesión, filtrar sanitizado por excepción Compose/NavController/lifecycle y marcas de navegación/eventos; no iniciar ADB ni recolección en esta auditoría. QA dueño del dispositivo decide/ejecuta cualquier captura runtime.
5. Próxima reproducción autorizada: comparar un guardado exitoso frente a rechazo/conflicto y back manual, siempre con fixture aislado; para cada una observar si se consumen `TransactionRevised` y `CloseEditor` ambos, si el stack termina en history/root, y si se restaura el root/tab correctos. No asumir que causa es el doble evento hasta correlacionar timeline.
6. Comparar estado local del fixture por interfaz autorizada agregada (movimiento presente y revisión) sin SQL/dump ni mutación. Si persiste en almacenamiento pero no se dibuja, orienta a navegación/UI; si no está, separar rechazo/transacción antes de concluir.

## 3. BUG-001 reevaluado contra contrato HU-29

### Oráculo documental y evidencia

El contrato de deuda define `SET_DEBT_SCHEDULE`: deuda/revisión/lista de cuotas ordenada, suma exacta y montos positivos; guardar/vencer no es pagar; reemplazar cancela cuotas PENDING/PARTIAL y conserva idempotencia (`specs/013-ep-deu-deudas-prestamos/contracts/debt-command-contract.md:74-79`). La spec establece igualmente que cronograma no crea pago ni reduce principal (`spec.md:76-85,115-120`); el modelo describe importes positivos y suma exacta, y estados/historial cancelado (`data-model.md:54-60,79-89`). Son criterios para juzgar el dato, no prohibición explícita de un nuevo reemplazo tras completar el guardado anterior.

El informe QA reporta cuatro taps que resultaron en dos revisiones aceptadas, con tres cuotas `PENDING` por PEN 100 y canceladas históricas, sin cambio observado de principal/pagos/caja; adicionalmente, un guardado posterior a relanzamiento/reentrada produjo otra revisión, listado como control (`informe.md:43-59`, en especial :52-56). Snapshot local anonimizado confirma rev. 8 tras ráfaga y rev. 9 tras guardado posterior, una sola terna pendiente y acumulación cancelada; 8 comandos SET y 1 OPEN seguían sin intento (`logs/BUG-001-local-persistencia.txt:5-14`).

### Distinciones

- **Observado por QA:** hubo dos reemplazos/revisiones aceptados en la ráfaga de cuatro taps; la pantalla proyectó una sola terna vigente. Permanecieron registros `CANCELLED` por diseño de historial. Este diagnóstico no repitió la prueba.
- **Incumplimiento documentado:** el oracle disponible garantiza exactitud de suma, no efectos financieros y reemplazo de cuotas activas. Las fuentes examinadas no contienen un criterio normativo explícito de “máximo una revisión por ráfaga/intención de usuario” ni definen el intervalo para considerar que una segunda acción es duplicada. Por tanto, no está probado un incumplimiento contractual de HU-29 solo por aceptar dos llamadas nuevas, si la segunda llegó tras completar/actualizar el primer guardado. La salida de QA describe taps consecutivos “sin esperar confirmación”, lo que aporta evidencia de ráfaga pero no timeline de eventos/estado `isSaving`; tampoco demuestra concurrencia superpuesta.
- **Causa:** código estático apoya la posibilidad de callbacks repetidos: CTA deshabilitado por estado, ViewModel genera operación nueva y asigna `isSaving` dentro de coroutine; no hay retorno atómico equivalente en método (`analisis-cronograma.md:13-18,36`; las referencias siguen el mismo SHA inspeccionado). Revisión transaccional y recibo idempotente protegen comandos repetidos con identidad igual; cada callback usa identidad nueva. Compatible con revisión redundante, no demuestra que el callback ocurriera durante la ventana de guardado ni causa de lo observado.
- **Hipótesis alternativa/normal:** un guardado voluntario posterior a finalización es un nuevo reemplazo permitido por el contrato. El reporte de control lo trata como tal. Cuotas `CANCELLED` retenidas son historial no obligación activa; no representan doble principal ni segunda terna pendiente. Conteo indiscriminado de canceladas + activas no es un oracle de deuda vigente.
- **Conclusión:** bug de experiencia/robustez plausible y observado comportamiento redundante en una secuencia rápida; incumplimiento funcional no queda cerrado/probado sin interpretación del requisito de intención y timeline del estado/callback. Mantener el hallazgo tal como lo reportó QA y solicitar confirmación funcional solo mediante QA/owner; no editar `informe.md`, no prescribir aquí un fix autorizado.

### Pruebas estáticas seguras adicionales sugeridas

QA puede contrastar solo desde fixtures y herramientas de lectura ya permitidas: snapshot agregado del número de revisiones, cuotas por estado/suma y saldo/eventos antes/después; validar que una terna PENDING suma el principal pendiente y que CANCELLED se excluyen del conteo activo. Para comprobar solapamiento hace falta correlación temporal (eventos/callbacks, isSaving, revisión esperada/aceptada) en reproducción aislada autorizada; las capturas actuales no bastan. Sync permanece no confirmado, separado del oracle local de reemplazo.

## Integridad de auditoría y fuentes

Estado antes: `git status --short` mostró `?? .worktrees/` y `?? docs/qa/` (untracked preexistentes). HEAD `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`, rama `main`. Este archivo es el único archivo creado/modificado en esta ejecución. Estado después y hash de este artefacto se registran en el handoff Kanban. No se modificó el código, tests, docs de QA existentes, DB, dispositivo ni Git history. Tests ejecutados: 0.

SHA-256 relevantes (SHA del contenido leído en checkout):

- `AGENTS.md`: `32a2b191ee5f43058d127f328bed35af3f76d2de3e06e02d30bb530ba377721f`
- `.specify/memory/constitution.md`: `7bf921f1a9106084574fcd747efb372465e9414f84fa0ecb74e0308672f45c0a`
- `specs/013-ep-deu-deudas-prestamos/spec.md`: `48761e2e5b9ad42d07140ba790d2dec55af42d825fa68bc7cf662ff7f23b409c`
- `specs/013-ep-deu-deudas-prestamos/contracts/debt-command-contract.md`: `2b2e764398c467bb27b9117115564f3a0ab024f1c7467382551b77d6bab21811`
- `specs/013-ep-deu-deudas-prestamos/data-model.md`: `9bfd89d8062273b0e59793c471b21d1dd519e33fd9fa873b7ee4c75496408843`
- `docs/qa/auditoria-sprint-6-2026-10-19/informe.md`: `1ff753e7114170258f0bf36d8cb0f51aced7c6341735c829bb3dab450aaeb83e`
- `docs/qa/auditoria-sprint-6-2026-10-19/analisis-cronograma.md`: `76dae5e20b252a5113963de2f9394c8cb581847512ff1ba6f485f38ebacd1b84`
- `docs/qa/auditoria-sprint-6-2026-10-19/logs/BUG-001-local-persistencia.txt`: `c5ec12d940c218726a576fc01ff668bedad4bf2d842c49842bf59d8f213bf553`
- `app/src/main/java/com/kipu/app/KipuApplication.kt`: `da30d562f21261a2c781e2aa8533b78f2559f375dcf080b186ca6ee08314306f`
- `app/src/main/AndroidManifest.xml`: `7939d94c6411c7ca8b28dfef128122b9dd2d43f2487a08993871d704b743dc12`
- `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt`: `3e12217503292cb1eb4757bddc165b15d00cc38d1c7c28d78009e6ea332c2b94`
- `app/src/main/java/com/kipu/app/feature/debts/di/DebtsModule.kt`: `15829850b4d82fe0cdaae2fa9b43ac8825e8052a8fbf95190368764a7f927266`
- `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt`: `2c3e3043b1688b63ebbf0da1525faeee687089e2029328a1454b227f79d2187d`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtOutboxDao.kt`: `4fb00aa6e48b54ca26bc18baae05c90566d2e874cb1fb38d49c3e62eeeec9104`
- `app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt`: `a9196c537e290af8ef46788933a6821633c9e593fac91cb08219ab67ebc6d458`
- `app/src/main/java/com/kipu/app/core/session/SessionCoordinator.kt`: `4f09bf8a1e67719d6522e936735755a01485d1235ee2446aeb55d81bfcb98ee2`
- `app/src/main/java/com/kipu/app/navigation/MovementsNavigation.kt`: `6f29589b0a804045bb6ad20a4dfdff79f2d78574b8f1d15d40922dca87718832`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorViewModel.kt`: `d824db8ca405ddd0e950412313b4c1495f896353a925eacbe0e71f9733fe607d`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorSheet.kt`: `5c9e9b09670067e1b83bd2fc71fb5a1e1d8d81af9a9796eaea24c0ee2c79338a`
- `app/src/main/java/com/kipu/app/navigation/AccountsNavigation.kt`: `d615aeac98cb140c4628fe997ba91a1927c41275276ca8f4b5aac22da490569b`
- `app/src/main/java/com/kipu/app/navigation/RetainedRootTabs.kt`: `29738f15f9d17f732462b0306d4f7271a9ceccbb5bf15d15327ce5bc188073c8`
- `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleViewModel.kt`: `63b6f6d991ff0b80482e7350357dc7b36f5f588ff73d6a379cd88f7a4c3644ae`
- `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleScreen.kt`: `de7a2655e854e565eb3ce02a9335869fc376657ba4722935d653aa88ade35cb1`
- `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt`: `91a674c3f8309479d306e7560ea74a32ac3a2e46c7c134031d44663075caab6f`

Fuentes principales con ubicaciones: sincronización `DebtSyncScheduler.kt:36-63,66-98,100-184`; DAO `DebtOutboxDao.kt:14-64`; repository `OfflineFirstDebtRepository.kt:31-36,101-107`; API `DebtApi.kt:18-60`; navegación/edición `MovementsNavigation.kt:81-123`, `MovementEditorViewModel.kt:181-246,301-385`, `MovementEditorSheet.kt:93-134,571-588`; tabs `AccountsNavigation.kt:101-123`, `RetainedRootTabs.kt:19-67`; contrato HU-29 y análisis de guardado citados arriba.

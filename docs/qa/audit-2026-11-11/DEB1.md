# DEB1 — debts: domain + data + core/finance

Esta porción calcula dinero y ciclos de tarjeta, registra deudas y préstamos en Room, y sincroniza comandos e historial con Supabase. También genera cronogramas y recordatorios locales sin convertir una cuota en un pago. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos por usuario y trabajo offline frecuente; revisión estática de `main`, sin red, dispositivos, builds ni ejecución de tests.

## Hallazgos

9 hallazgos: **1 crítica, 3 altas, 4 medias y 1 baja**. Las referencias SQL y de otros módulos se usaron para verificar contratos; los defectos enumerados tienen su causa en DEB1.

### [DEB1-01] Un worker de A puede enviar una deuda con la sesión de B
- Severidad: crítica
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt:29-38; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt:81-95,104-110
- Qué está mal: El worker comprueba al propietario una vez al comenzar, pero `DebtApi.execute` obtiene la sesión vigente para cada comando sin recibir ni comparar el propietario de la outbox. `AuthenticatedSession` ya proporciona `userId`, pero se usa solamente su token. El cambio de sesión puede ocurrir mientras el worker espera una petición anterior; la cancelación de sesión consultada no cancela este worker de deudas.
- Escenario concreto de fallo: A tiene dos aperturas históricas pendientes; el worker comienza como A y espera la primera petición. A cierra sesión y B inicia sesión antes de procesar la segunda: la segunda apertura se manda con el token de B. Al no referenciar cuenta y tomar la RPC al dueño de `auth.uid()`, se crea en B la deuda con nombre, notas y monto privados de A; el resultado también puede marcar como sincronizado el comando local de A. RLS no lo impide porque la petición efectivamente se autentica como B.
- Fix mínimo: Pasar `command.userId` como propietario esperado a `DebtApi.execute` y rechazar antes de enviar cuando `session.userId` no coincida; verificar también el propietario tras las suspensiones antes de aplicar resultados. Añadir un test que cambie A por B entre dos comandos y compruebe que la apertura de A nunca sale con el token de B.
- Confianza: alta
- Cross-módulo: no

### [DEB1-02] Ajustar o condonar deja un evento que bloquea su propio eco remoto
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt:592-605,832-841; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtChangeFeedApplier.kt:199-206,260-269
- Qué está mal: El ajuste/condonación local guarda `occurredAt = clock.millis()`, pero no envía esa fecha en el comando. La RPC versionada genera la fecha al procesarlo (`supabase/migrations/20261008170000_s5_debt_closure.sql:28,131-133`). Ambos eventos tienen el mismo ID estable; el applier exige igualdad de `occurredAt` y rechaza el evento remoto como `DEBT_EVENT_ID_REUSED`.
- Escenario concreto de fallo: Registrar una condonación offline el lunes y sincronizar el martes produce un evento local del lunes y otro remoto del martes con el mismo ID. La RPC se aplica, pero la página de cambios se revierte y el checkpoint no avanza. Los cambios posteriores de cualquier deuda de ese usuario quedan detrás de la página rechazada, incluso tras reiniciar.
- Fix mínimo: En el eco de un evento de cierre reconocido por su recibo/comando local, aceptar y actualizar la fecha que genera el servidor, conservando la comprobación de identidad, montos, delta y vínculos. Añadir un test local → respuesta RPC → página remota con fechas distintas; no relajar la igualdad para eventos arbitrarios.
- Confianza: alta
- Cross-módulo: no

### [DEB1-03] El DTO no puede leer los conflictos reales del servidor
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtDtos.kt:12-23; app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt:40-47; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt:124-126,144-149
- Qué está mal: `DebtCommandRemoteResult.error` es `String?`, mientras las RPC de pago, cronograma y cierre devuelven un objeto `{"code":"DEBT_REVISION_CONFLICT"}`. La deserialización falla y `DebtApi` lo clasifica como fallo de red, por lo que no se ejecuta el tratamiento de `CONFLICT`. Contratos comprobados en `supabase/migrations/20261008150000_s5_settle_debt.sql:116-118`, `20261008160000_s5_debt_schedule.sql:87-89` y `20261008170000_s5_debt_closure.sql:88-90`.
- Escenario concreto de fallo: Dos dispositivos pagan sobre la revisión 1; el primero crea la revisión 2. El segundo recibe HTTP 200 con `status=CONFLICT` y `error` objeto: el cliente lo convierte en `NetworkFailure`, conserva su pago optimista y reintenta indefinidamente el mismo comando obsoleto. Los comandos posteriores de esa deuda esperan al anterior.
- Fix mínimo: Representar `error` con un DTO que contenga `code` y leer `error?.code` al finalizar. Añadir a `DebtApiTest` una respuesta idéntica al conflicto SQL y verificar `Success` con estado `CONFLICT`, seguido de la transición de la outbox sin reintento de red.
- Confianza: alta
- Cross-módulo: no

### [DEB1-04] El conflicto de una deuda detiene todas las demás
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtChangeFeedApplier.kt:48-76,103-138; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtHydration.kt:26-30
- Qué está mal: Un conflicto lanza una excepción que revierte la página completa, deja el cursor anterior y marca la deuda como `CONFLICT`. En sincronización normal, `DebtHydration` devuelve éxito al recibir ese conflicto. No hay una ruta de resolución/rebase de outbox en esta porción; las siguientes lecturas encuentran el mismo conflicto y tampoco alcanzan cambios de otras deudas.
- Escenario concreto de fallo: A y B editan el nombre de la misma deuda desde la revisión 1; A sincroniza primero. B ya tiene su nombre distinto en revisión 2 y recibe el snapshot remoto de revisión 2: `sameDebtContent` difiere y la página queda detenida. Una nueva deuda o un cobro independiente registrado en A, detrás de ese cambio, nunca aparece en B aunque su propia sincronización termine con éxito. Este caso usa la RPC de edición, cuyo conflicto no incluye el objeto de error de DEB1-03, por lo que es independiente de ese hallazgo.
- Fix mínimo: Aislar el cambio conflictivo y conservar su snapshot remoto pendiente de resolución antes de avanzar el cursor, permitiendo aplicar cambios de otras deudas sin sobrescribir la verdad financiera local. Añadir un test con deuda X en conflicto y una deuda Y nueva en la misma página, verificando que Y converge y X conserva el conflicto explícito.
- Confianza: alta
- Cross-módulo: no

### [DEB1-05] Cada reconciliación puede volver a emitir un aviso ya entregado
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderScheduler.kt:90-115,192-209; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderWorker.kt:71-95,132-137
- Qué está mal: La reconciliación vuelve a encolar con `REPLACE` toda cuota pendiente. Cuando la fecha del aviso ya pasó, el retraso es cero; el worker no consulta si esa tupla usuario/cuota/fecha ya fue entregada y reemplaza su registro de programación antes de marcarlo entregado otra vez. El ID estable evita duplicar la fila del aviso local, pero no evita otro `notify` del sistema.
- Escenario concreto de fallo: Una cuota vence el 20 y el aviso del día 18 ya se entregó y fue descartado. El día 19 se edita una nota de la deuda o acaba otra sincronización, se reconcilia y el aviso reaparece inmediatamente; posteriores reconciliaciones vuelven a emitirlo mientras siga pendiente.
- Fix mínimo: Consultar el registro existente de la misma tupla antes de reemplazarlo; si ya está entregado, no volver a publicar la notificación. Mantener el comportamiento actual de persistir el aviso local con permiso denegado. Test mínimo: ejecutar el recordatorio, reconciliar sin cambiar fecha y comprobar una sola publicación total.
- Confianza: alta
- Cross-módulo: no

### [DEB1-06] El parser de deudas admite importes que Money y el servidor rechazan
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/domain/DebtAmountParser.kt:10-18; app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt:76-78; app/src/main/java/com/kipu/app/feature/debts/domain/model/UpcomingDue.kt:77-83
- Qué está mal: Se valida únicamente que el importe positivo quepa en `Long`, sin el límite de `Money.MAX_MONEY_MINOR`. La apertura local tampoco aplica ese límite. El servidor admite hasta 99_999_999_999_999 centavos y la proyección de vencimientos construye `Money`, cuyo constructor lanza una excepción por encima de ese valor.
- Escenario concreto de fallo: Introducir `1000000000000.00` en una deuda histórica con vencimiento produce 100_000_000_000_000 centavos: el parser y la escritura local lo aceptan. Al abrir vencimientos, `Money(remaining, currency)` falla; al sincronizar, la RPC rechaza la apertura que ya aparece guardada localmente. Es un caso de entrada extrema, no una corrupción demostrada con importes cotidianos.
- Fix mínimo: Aplicar el mismo límite compartido en el parser y en las fronteras de apertura/pago locales, también para intereses. Test mínimo: aceptar el máximo y rechazar máximo + 1 sin ninguna escritura; conservar la gramática decimal actual de deudas.
- Confianza: alta
- Cross-módulo: no

### [DEB1-07] El planificador devuelve cuotas de principal cero como válidas
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/domain/usecase/SetDebtSchedule.kt:24-26,35-45; app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt:485-491
- Qué está mal: El planificador permite más cuotas que centavos disponibles. Devuelve un plan `Valid` con cuotas de cero, aunque el repositorio y FR-014 exigen principal positivo para cada cuota.
- Escenario concreto de fallo: Una deuda con 2 centavos restantes y 3 cuotas genera `[1,1,0]`, que la pantalla acepta como plan válido. Al guardar, el repositorio responde `INVALID_INSTALLMENT_SEQUENCE`, aunque la secuencia es correcta: falla el importe cero. Los tests del planificador solo cubren repartos con una base positiva.
- Fix mínimo: Rechazar al planificar cuando `installmentCount > remainingPrincipalMinor`, con un error específico de importe insuficiente para el número de cuotas. Test mínimo: saldo 2, cantidad 3 → `Invalid`, y cantidad 2 → dos cuotas de 1.
- Confianza: alta
- Cross-módulo: no

### [DEB1-08] El test de revocación de permisos no provoca ninguna revocación
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderWorkerTest.kt:50-65,146-149
- Qué está mal: `permissionRevokedBetweenChecksIsHandledAsOptionalNotification` pasa una secuencia `[true,false]`, pero comprueba que el permiso se consultó una sola vez. Solo se consume `true`; ni se simula una revocación del sistema ni se fuerza la `SecurityException` que captura el worker.
- Escenario concreto de fallo: Si se elimina el `catch (SecurityException)` de `DebtReminderWorker.kt:132-141`, este test conserva el mismo camino y puede seguir pasando cuando el entorno permite notificar. Por tanto no detecta la regresión que su nombre afirma proteger: una revocación después de la comprobación haría fallar el worker.
- Fix mínimo: Sustituir este caso por uno que haga que la publicación de la notificación lance `SecurityException` después de devolver permiso concedido y verificar resultado `Success` más aviso local persistido. El test actual de permiso denegado ya cubre el otro camino; no duplicarlo.
- Confianza: alta
- Cross-módulo: no

### [DEB1-09] Dos planificadores de apertura solo existen para sus propios tests
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenPayableDebt.kt:10-43; app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenReceivableDebt.kt:10-44; app/src/main/java/com/kipu/app/feature/debts/domain/model/DebtCommandModels.kt:143-163
- Qué está mal: Ambos planificadores y sus dos modelos de resultado no tienen llamadores de producción. La apertura real valida y calcula de nuevo en `DebtLocalDataSource.openDebt`. La búsqueda completa, incluidos archivos ocultos, tests y referencias documentales, solo encontró definiciones, tests y menciones de tareas.
- Escenario concreto de fallo: Cambiar en cualquiera de estos planificadores una validación de apertura o el signo del efectivo y actualizar su test no cambia ninguna apertura real de la app. Se mantiene una segunda representación de reglas financieras que los tests unitarios ejercitan sin proteger el código ejecutado.
- Fix mínimo: Borrar los dos planificadores y sus modelos sin uso; trasladar sus casos financieros útiles a las pruebas existentes del repositorio/datasource. Conservar el test de edición descriptiva en `OpenPayableDebtTest`, pues sí prueba un componente usado.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 109 líneas de producción; sin dependencias nuevas ni eliminación de verificaciones útiles.

## Código de riesgo sin tests

- `DebtApi.execute` / `SyncDebtChangesWorker`: cambio de propietario entre dos comandos → nunca enviar el segundo comando con la sesión de otra persona (DEB1-01).
- `DebtLocalDataSource.closeDebt` + `DebtChangeFeedApplier.applyPage`: condonación local y eco de servidor con otra fecha → un evento, cursor avanzado y saldo único (DEB1-02).
- `DebtApi` + DTO de respuesta: HTTP 200 con `error` objeto → conflicto de dominio y no fallo de red (DEB1-03). `DebtApiTest` prueba apertura exitosa, sesión ausente y comando desconocido; no prueba esa respuesta.
- `DebtChangeFeedApplier`: conflicto de X seguido por datos independientes de Y → Y no queda bloqueada (DEB1-04). El test actual verifica expresamente que el conflicto no avance el checkpoint, pero no prueba la convergencia de las demás deudas.
- `DebtReminderWorker`: entrega + reconciliación idéntica → no publicar otra vez (DEB1-05); publicación que lanza `SecurityException` → conservar éxito y aviso local (DEB1-08).
- `SetDebtSchedule.plan`: menos centavos que cuotas → rechazo antes de construir un plan inválido (DEB1-07).

Los tests de dinero usan resultados concretos y el de precisión compara contra un cálculo decimal de mayor precisión; no los conté como tests vacíos. `DebtSettlementSyncTest` verifica persistencia e idempotencia local y anulaciones, pero no ejecuta el worker de sincronización ni una secuencia RPC/pull real; su nombre no demuestra cobertura de los contratos anteriores.

## Lo que no revisé

- Ninguno de los **36 archivos main, 13 tests unitarios y 9 androidTest asignados** quedó sin leer completo. `.codegraph/` no existe en este checkout, por lo que se usaron lecturas directas y `rg`.
- No ejecuté tests, Gradle, compilación, ADB, dispositivos ni consultas SQL. No accedí a red, Supabase local/remoto, MCP de Supabase ni desplegué cambios. Los resultados son deducciones estáticas; no se afirma reproducción instrumentada ni vigencia del despliegue remoto.
- Fuera del alcance solo leí contratos necesarios: proveedor y coordinación de sesión, llamadas/hidratación financiera, consumidores de apertura/cronograma/vencimientos, persistencia de avisos y migraciones que definen las RPC de deuda. Los tests de hidratación de portability se consultaron para no atribuirles falsamente ausencia de cobertura; no se auditaron sus otros dominios.
- El workspace es compartido: ya había `.agents/skills/ponytail/`, `.claude/` y `skills-lock.json` sin seguimiento al iniciar; durante la auditoría aparecieron informes de otros workers. Mi única escritura es este informe.

## Dudas

- No encontré un flujo de resolución de conflictos de deudas que permita elegir/reconciliar el valor remoto con los comandos optimistas. La spec exige que se muestre el conflicto (FR-024), pero no define esa resolución; hace falta precisar la acción de usuario manteniendo visibles los movimientos pendientes.
- La RPC de cierre fija la fecha efectiva del ajuste/condonación al momento del procesamiento, mientras la app la fija al registro offline. Hay que decidir cuál representa el hecho financiero; DEB1-02 describe el bloqueo técnico independientemente de esa decisión.

Prioridad: corregir primero la identidad de sesión en cada comando, después los contratos de respuesta y de eco de cierre. Limpieza identificada: 109 líneas de producción prescindibles, 0 dependencias.

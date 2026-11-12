# BIL1 — billing: plans/data + domain + di + purchase + edge functions de billing

Esta porción registra preferencias de planes, sincroniza selecciones de cupos, procesa compras de Google Play y verifica el acceso Premium mediante concesiones firmadas con vencimiento offline. Asumí miles de usuarios, uno o dos dispositivos por cuenta, cambios de sesión y uso offline frecuente. Auditoría estática de `main`, sin red, dispositivos, builds, tests ejecutados ni consultas a Supabase; los resultados describen el código versionado, no su despliegue.

## Hallazgos

9 hallazgos: 0 críticos, 6 altos y 3 medios; uno de los altos es una referencia cross-módulo para SQL4. No se incluyen preferencias de estilo ni propuestas de limpieza sin un fallo concreto.

### [BIL1-01] La primera verificación puede adjudicar la compra a otra cuenta
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt:111-123; supabase/functions/verify-purchase/google-play-api.ts:241-345; supabase/functions/verify-purchase/index.ts:232-245
- Qué está mal: Al lanzar la compra se envía un identificador derivado del usuario a Play, pero el adaptador servidor descarta la identidad de cuenta que devuelve el proveedor. El callback y la restauración toman la sesión actual; solo se comprueba un propietario previo si el hash del token ya fue registrado.
- Escenario concreto de fallo: A inicia un pago pendiente y cambia a B antes de su aprobación; B abre compras y restaura ese token todavía no registrado → la primera persistencia lo vincula a B y las restauraciones posteriores de A encuentran `TOKEN_ACCOUNT_CONFLICT`. No hace falta sustraer un token: basta compartir el dispositivo y su cuenta de Play.
- Fix mínimo: Conservar la identidad de cuenta del resultado verificado de Play y compararla en servidor con el hash esperado del usuario autenticado antes de la primera vinculación. Rechazar el desacuerdo; definir expresamente la compatibilidad para compras antiguas sin identificador.
- Confianza: alta
- Cross-módulo: no

### [BIL1-02] Lifetime lee el estado de otro formato de respuesta
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/functions/verify-purchase/google-play-api.ts:246-278
- Qué está mal: Se consulta `purchases/productsv2`, pero se lee `details.purchaseState` en la raíz. El estado de ese recurso se encuentra en `purchaseStateContext.purchaseState`; al no hallarlo se transforma la cadena vacía en `REVOKED` y, simultáneamente, se devuelve `purchaseState: PURCHASED`.
- Escenario concreto de fallo: Una respuesta de compra única con `productLineItem` coincidente y `purchaseStateContext: { purchaseState: "PURCHASED" }` → una compra Lifetime pagada se persiste como revocada y no concede Premium. Un estado anidado `PENDING` también termina como compra adquirida/revocada y entra indebidamente en la ruta de reconocimiento.
- Fix mínimo: Leer y validar el estado anidado del recurso v2; rechazar formatos desconocidos antes de persistir o reconocer. Añadir un test del adaptador con esa respuesta JSON, en vez de simular directamente un `ProviderPurchase` ya normalizado.
- Confianza: media
- Cross-módulo: no

### [BIL1-03] El pago deja de procesarse al salir de la pantalla
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt:36-48; app/src/main/java/com/kipu/app/feature/plans/data/billing/PlayBillingGateway.kt:34-35,101-103
- Qué está mal: La verificación es un efecto del Flow que recoge la pantalla de compra. `MutableSharedFlow` no conserva eventos sin suscriptores; su capacidad adicional no equivale a replay. El refresco automático de entitlement solo consulta la proyección del servidor, sin recuperar compras de Play no registradas.
- Escenario concreto de fallo: El usuario recibe `PENDING`, vuelve al inicio y el ViewModel se destruye; después se aprueba el pago → el callback se descarta y no se ejecutan verificación ni reconocimiento. Si el RTDN llega antes de la primera vinculación queda esperando propietario/token, y usar el resto de la app no recupera la compra; se requiere abrir compras o restaurar manualmente, con riesgo de reembolso por falta de reconocimiento.
- Fix mínimo: Ejecutar el procesamiento de callbacks en un scope de aplicación e iniciar una recuperación de compras al recuperar una sesión/en primer plano. Mantener la UI como observadora de resultados y los tokens únicamente en memoria.
- Confianza: alta
- Cross-módulo: no

### [BIL1-04] La Edge Function rechaza el cupo aprobado de cinco más cinco
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/functions/plans/index.ts:69-75,91-98
- Qué está mal: `CUSTOM_CATEGORIES` tiene límite total 5 antes de llegar a la RPC. El llamador `saveSelectedFreeCategoryRoots` permite cinco raíces de gasto y cinco de ingreso y envía ambas en una única selección; la RPC versionada admite hasta diez y valida el límite por tipo.
- Escenario concreto de fallo: Tras bajar a Free, el usuario selecciona cinco categorías de gasto y una de ingreso → Room guarda seis elementos válidos, pero `/plans/quota-selection` responde `INVALID_REQUEST`; el worker termina en fallo y la selección no converge en el segundo dispositivo.
- Fix mínimo: Cambiar a 10 el máximo de elementos de `CUSTOM_CATEGORIES` en esta validación y conservar la validación de cinco por tipo de la RPC. Añadir un test de la función que acepte diez IDs distintos de categorías y rechace once.
- Confianza: alta
- Cross-módulo: no

### [BIL1-05] Una suscripción cancelada pero pagada no recibe grant al restaurarse
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/functions/verify-purchase/index.ts:308-317; app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt:134-137
- Qué está mal: El adaptador representa la cancelación vigente como `CANCELLED` + `CANCELED_ACTIVE`; la persistencia devuelve Premium mientras quede periodo pagado. La emisión de grant, sin embargo, exige que la compra candidata tenga estado `PURCHASED`, aunque el acceso agregado de la cuenta sea válido.
- Escenario concreto de fallo: Se cancela la renovación de una suscripción que vence mañana y se restaura en otra instalación → el servidor responde `VERIFIED`, `effectivePremium: true` y grant nulo; Android devuelve siempre `OFFLINE_GRANT_UNAVAILABLE` y no instala acceso, aun con el periodo pagado vigente.
- Fix mínimo: Emitir el grant desde el acceso efectivo persistido para todo candidato no pendiente verificado, incluyendo `CANCELED_ACTIVE`, con el fin agregado como límite. Añadir un caso de cancelación vigente que compruebe tanto la respuesta del servidor como la escritura de cache en Android.
- Confianza: alta
- Cross-módulo: no

### [BIL1-06] El refresco periódico puede quitar acceso durante gracia o cancelación vigente
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20261102034340_dev_plan_mode.sql:166-192; app/src/main/java/com/kipu/app/feature/plans/data/entitlement/TestPlanRefreshRepository.kt:49-55,87
- Qué está mal: Referencia de contrato para SQL4: la última definición localizada de `get_effective_plan_for_refresh` filtra únicamente `ACTIVE` + `PURCHASED`. El consumidor reemplaza su cache por Free cuando recibe esa proyección, mientras el contrato de billing también concede acceso a gracia y cancelación con periodo vigente.
- Escenario concreto de fallo: Una compra queda verificada en `IN_GRACE_PERIOD` o `CANCELED_ACTIVE`, con vencimiento futuro y sin otro entitlement → un refresco automático obtiene Free y reemplaza el grant vigente.
- Fix mínimo: SQL4 debe comprobar y alinear el predicado de esa proyección con el de acceso comercial ya usado por la verificación; conservar los límites de vigencia y probar un periodo cancelado aún pagado. No se amplió esta revisión a la auditoría de migraciones.
- Confianza: alta
- Cross-módulo: sí → SQL4

### [BIL1-07] Las preferencias esperando autenticación nunca vuelven a enviarse
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/data/local/PlanPreferencesDao.kt:35-47; app/src/main/java/com/kipu/app/feature/plans/data/sync/SyncPlanSelectionWorker.kt:48-50,66-68
- Qué está mal: El worker cambia las filas a `WAITING_FOR_AUTH` y termina con éxito al no quedar filas `PENDING`. La siguiente ejecución solo selecciona `PENDING`; no existe un llamador de producción que reactive las filas esperando autenticación, y `ensurePeriodic` tampoco tiene llamadores de producción.
- Escenario concreto de fallo: Una intención queda pendiente offline y se cierra sesión conservando cambios, o el envío recibe 401 → al volver a iniciar sesión con la misma cuenta, la intención sigue contándose como pendiente pero nunca se envía. Incluso ejecutar de nuevo el worker no la selecciona.
- Fix mínimo: Reponer a `PENDING` las filas de ese propietario al disponer de su sesión válida y encolar su sincronización. Añadir un test que recorra 401 → nueva sesión del mismo usuario → envío y estado `COMPLETED`.
- Confianza: alta
- Cross-módulo: no

### [BIL1-08] La respuesta antigua pisa la elección local más reciente
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/plans/data/local/PlanPreferencesDao.kt:51-57
- Qué está mal: `reconcile` reemplaza incondicionalmente la preferencia local con la respuesta del servidor, sin comprobar si ya se emitió una revisión local posterior. La transacción protege las escrituras, pero no evita aplicar una respuesta antigua sobre un cambio nuevo.
- Escenario concreto de fallo: Se envía revisión 1 con Free; mientras viaja, se confirma revisión 2 con intención Premium; llega la respuesta de revisión 1 y falla la red antes de enviar la segunda → la app vuelve a mostrar Free aunque su última elección y el outbox pendiente dicen Premium.
- Fix mínimo: Actualizar la revisión aceptada y completar la operación, pero conservar la preferencia local cuando exista una revisión emitida posterior que todavía no esté reconciliada. Añadir un test DAO con dos confirmaciones y respuesta únicamente para la primera.
- Confianza: alta
- Cross-módulo: no

### [BIL1-09] Los tests de esquema prueban versiones obsoletas y otra ruta de fallo
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/plans/data/local/KipuDatabaseSchemaTest.kt:105-112,132-147,181-188
- Qué está mal: La prueba de reapertura migra hasta 25 y registra migraciones solo hasta esa versión, pero `KipuDatabase` declara versión 40; la reapertura no puede probar el camino exitoso actual. Además, la prueba llamada `newerUnknownSchemaFailsSafelyWithoutDeletingRepresentativeData` crea versión 11, que es anterior, por lo que verifica una subida sin migración y no el rechazo de una versión futura.
- Escenario concreto de fallo: Con la base de versión 40, el test de reapertura encuentra que falta la ruta 25 → 40; por separado, una regresión que permitiese borrar datos al abrir una versión futura podría pasar el test del sentinel, porque nunca ejercita un downgrade real.
- Fix mínimo: Usar la lista de migraciones de producción hasta la versión actual para la reapertura y crear el sentinel con una versión superior a la actual para el otro caso. El test mínimo faltante es abrir ese sentinel futuro y verificar que sus datos sobreviven al rechazo.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `verify-purchase/google-play-api.ts:241` → probar la respuesta JSON de `productsv2` con `purchaseStateContext`, sin sustituir el adaptador por un `ProviderPurchase` prefabricado (BIL1-02). Los tests actuales solo ejercitan los mapeadores de strings y el handler con proveedor simulado.
- `BillingRepository.purchaseResults` / `PlayBillingGateway` → aprobar un pago pendiente sin observador de pantalla y comprobar que se verifica y reconoce una vez (BIL1-03). `PlayBillingGatewayTest` prueba modelos, no llama al gateway.
- Primera vinculación del token → compra iniciada por A y recuperada por B antes de cualquier persistencia; comprobar rechazo y ausencia de escritura/reconocimiento (BIL1-01). El test de conflicto actual ya presupone un propietario registrado.
- `verifyCurrentPurchaseForOwner` → `CANCELLED` + `CANCELED_ACTIVE` vigente devuelve grant con vencimiento pagado (BIL1-05).
- `plans.validQuotaSelection` → selección de cinco raíces de gasto más cinco de ingreso alcanza la RPC (BIL1-04).
- `SyncPlanSelectionWorker` / `PlanPreferencesDao.reconcile` → recuperación tras autenticar y respuesta de revisión antigua con otra local pendiente (BIL1-07/08).

## Lo que no revisé

- Ningún archivo enumerado del alcance quedó sin lectura completa: se leyeron los 55 Kotlin de producción, 26 tests unitarios y seis instrumentados, los 19 TypeScript de las cinco funciones y los dos archivos de `supabase/operations/`.
- No ejecuté tests, Gradle, ADB, servicios locales ni llamadas de red. No validé productos, credenciales, estados de Play Console, trabajos cron ni despliegues de Supabase.
- Fuera del alcance solo leí contratos y llamadores para confirmar los escenarios: categorías, sesión, arranque, pantallas de compra/restauración, versión de Room y definiciones SQL puntuales. BIL1-06 queda para SQL4; no se auditó el resto de esos módulos.
- BIL1-02 depende del contrato externo de `productsv2`: no había una fixture de respuesta real versionada y la prohibición de red impidió contrastarlo con el servicio o documentación remota; por eso su confianza es media. La validación comercial Lifetime figura pendiente en `docs/control-documental.md` (D7/D-20261105-09).
- Había archivos sin seguimiento antes de comenzar (`.agents/skills/ponytail/`, `.claude/`, `skills-lock.json` y otros informes del directorio compartido). Esta tarea solo creó `BIL1.md`.

## Dudas

- Para compras anteriores sin identidad de cuenta de Play, ¿se permite primera vinculación por posesión del token o se exige recuperación asistida? No encontré una excepción aprobada que resuelva ese caso; no debe usarse para omitir el control en compras nuevas.
- No se detectaron otras dudas de producto necesarias para los hallazgos. La espera sin token, la pérdida de continuidad tras reinicio y el lease de 72 horas están aprobados y no se reportan como defectos.

# DB1 — base local: core/database + migraciones/schemas Room + core/di, storage, logging

Esta porción define la base Room `KipuDatabase` (versión 40, 64 entidades), sus 39 migraciones, los triggers de integridad (evidencia inmutable de movimientos y estado de cuotas de crédito), el módulo Hilt de la base, el redactor de logs y el resolvedor de URLs de logos. Asumí miles de usuarios con 1-2 dispositivos, miles de movimientos por usuario, uso offline frecuente y actualizaciones de app desde versiones antiguas de la base. Verifiqué por script que `app/schemas/.../40.json` (64 tablas) coincide con las 64 entidades de `@Database` y que los diffs de tablas/columnas entre schemas consecutivos 24→40 coinciden con las migraciones (sin hallazgo ahí).

## Hallazgos

### [DB1-01] Los triggers de estado de cuotas congelan el SQL del momento de instalación
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:1457-1473, 1493-1498
- Qué está mal: `ensureCreditInstallmentStatusGuards` crea los triggers con el texto de `REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL` y `CREATE TRIGGER IF NOT EXISTS`. Los triggers viven en SQLite, no en el schema de Room, así que si ese SQL cambia en el futuro, los dispositivos ya instalados conservan el trigger viejo y las instalaciones nuevas (vía `MovementSchemaCallback.onCreate`) reciben el nuevo.
- Escenario concreto de fallo: se corrige `CREDIT_INSTALLMENT_BALANCES_SQL` (por ejemplo un estado más que cuenta como pago) y se sube la versión sin recrear triggers. El usuario antiguo ve la cuota PENDING tras un pago; el usuario nuevo la ve PAID, con los mismos datos. Room no valida triggers, así que ningún test de schema lo detecta.
- Fix mínimo: hacer `DROP TRIGGER IF EXISTS` antes de cada `CREATE TRIGGER` y llamar a `ensureCreditInstallmentStatusGuards` también desde `Callback.onOpen` (o desde cada migración que toque ese SQL).
- Confianza: alta
- Cross-módulo: no (el SQL vive en accounts/ACC1, pero el defecto de versionado de triggers es de esta porción)

### [DB1-02] Cada cambio de status/sync_status de cualquier movimiento recalcula todas las cuotas del usuario
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:1461, 1465-1471 (SQL en feature/accounts/data/local/CreditEntities.kt:280-289)
- Qué está mal: el trigger `AFTER UPDATE OF status, sync_status ON transactions` no tiene cláusula `WHEN`, y ejecuta un `UPDATE credit_installments ... WHERE user_id = NEW.user_id` que reevalúa un CTE con joins (allocations, refunds, cards, catálogo de comercios) por cada cuota del usuario.
- Escenario concreto de fallo: un usuario con 3.000 movimientos pendientes (uso offline) sincroniza; cada ack cambia `sync_status` y dispara el recálculo completo de todas sus cuotas, aunque el movimiento sea un gasto en efectivo sin relación con tarjetas. Son miles de recálculos dentro de las transacciones de sync: lentitud notable y bloqueo de escritura de la base.
- Fix mínimo: añadir `WHEN NEW.card_id IS NOT NULL OR NEW.operation_kind IN ('CARD_PURCHASE','CARD_PAYMENT','REFUND')` a los triggers de `transactions` (y acotar el UPDATE a las cuotas de esa transacción).
- Confianza: media (no medí el plan de ejecución; el coste depende de cómo SQLite materialice el CTE)
- Cross-módulo: no

### [DB1-03] `SecureLog` reemplaza la excepción por una nueva y pierde stack trace y causa
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:49-68
- Qué está mal: `sanitizeThrowable` devuelve `RuntimeException(message)` creada dentro de `SecureLog`; su stack trace apunta a `SecureLog.sanitizeThrowable`, y el tipo y la causa originales se pierden. Hay ~11 llamadas con throwable (p. ej. KeystoreEncryptedSessionStorage.kt:97/122/136, AuthApi.kt:78/103, SupabaseAuthRepository.kt:188).
- Escenario concreto de fallo: falla el descifrado de la sesión (`SecureLog.w(TAG, "Failed to decrypt session...", e)`); en logcat solo se ve un `RuntimeException` con frames de logging, sin saber si fue `AEADBadTagException`, `KeyStoreException`, etc. Diagnosticar un cierre de sesión inesperado en campo es casi imposible.
- Fix mínimo: `RuntimeException(redactedMessage, throwable.cause).apply { stackTrace = throwable.stackTrace }`, o pasar el throwable original a `Log.w/Log.e` (el mensaje ya se redacta aparte).
- Confianza: alta
- Cross-módulo: no

### [DB1-04] Migraciones que ejecutan código de dominio vivo y un `replace` frágil sobre SQL
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:723, 1424-1430, 1452, 1563, 1605
- Qué está mal: 31→32 usa `BillingCycleCalculator`/`CycleDateBasis`, 14→15 usa `CreditLiabilityAccountIds`, 36→37 usa `CaptureCorrelation.reference`, 39→40 usa `receiptRegistrationKey`, y 31→32 usa `REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL.replace("WHERE user_id = :userId AND", "WHERE 1 = 1 AND")`. Una migración debe ser una foto inmutable; aquí su resultado cambia cuando cambia el código de la app.
- Escenario concreto de fallo: (a) se cambia el esquema del UUID de `receiptRegistrationKey`; un usuario que salta de v39 a la nueva versión no obtiene la coincidencia y sus movimientos OCR quedan como `MANUAL`. (b) se reformatea el SQL de `CreditEntities.kt` (espacios o salto de línea en `WHERE user_id = :userId AND`): el `replace` no encuentra el texto, queda `:userId` sin enlazar, `execSQL` lanza y la migración 31→32 falla; Room la reintenta en cada arranque y la app queda en bucle de cierre (no hay `fallbackToDestructiveMigration`).
- Fix mínimo: copiar a la migración la lógica mínima (UUID v3 de una línea, normalización de referencia, SQL de refresco literal con `WHERE 1 = 1`) en lugar de importar dominio; como mínimo, un `check(sql != replaced)` tras el `replace`.
- Confianza: alta
- Cross-módulo: no

### [DB1-05] Cobertura mínima del redactor de logs (2 casos para 6 regex)
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/core/logging/MovementLogRedactionTest.kt:10-25
- Qué está mal: solo se prueban JWT/Bearer y email. `PASSWORD_JSON_REGEX`, `PASSWORD_PARAM_REGEX`, `RECOVERY_TOKEN_REGEX` y `SecureLog` no tienen ningún test; es código de seguridad (FR-006/FR-043).
- Escenario concreto de fallo: alguien "simplifica" `RECOVERY_TOKEN_REGEX` y los `recovery_token=...` pasan a logcat en claro; ningún test falla.
- Fix mínimo (test): un test con 4 entradas (`"password":"x"`, `password=x`, `recovery_token=abc`, `refresh_token=abc`) que compruebe que la salida no contiene el secreto.
- Confianza: alta
- Cross-módulo: no

### [DB1-06] Los triggers de estado de cuotas y su paridad instalación nueva/migrada no tienen test
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt:100-118 (solo cuenta triggers `immutable_%` y `movement_head_owner_%`)
- Qué está mal: no encontré en `androidTest` ni `test` ninguna referencia a `credit_installment_status_*` ni a `ensureCreditInstallmentStatusGuards`; el estado PAID/PARTIAL de dinero depende de esos triggers.
- Escenario concreto de fallo: se renombra una tabla/columna y `onCreate` deja de crear algún trigger sin que nada falle; en instalaciones nuevas la cuota sigue PENDING tras pagar.
- Fix mínimo (test): un test de instrumentación que cree la base con `MovementSchemaCallback`, inserte compra + asignación de pago y compruebe que la cuota pasa a PAID; y que el conjunto de nombres `credit_installment_status_%` es el mismo en base nueva y tras migrar 31→32.
- Confianza: media (CreditPurchaseContractRoomTest, fuera de mi alcance, podría cubrirlo indirectamente)
- Cross-módulo: no

### [DB1-07] Fecha de vencimiento del saldo de crédito heredado calculada en UTC
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:629
- Qué está mal: `date(t.occurred_at / 1000, 'unixepoch')` usa fecha UTC; el resto del sistema usa America/Lima (la propia migración 31→32 en la línea 1428).
- Escenario concreto de fallo: el último movimiento de una tarjeta es a las 20:00 hora de Lima (01:00 UTC del día siguiente); la cuota del saldo heredado queda con `due_date` un día después del real. Solo afecta a usuarios que migraron desde la versión 10.
- Fix mínimo: `date(t.occurred_at / 1000 - 18000, 'unixepoch')` (Lima es UTC-5 sin horario de verano).
- Confianza: media
- Cross-módulo: no

### [DB1-08] Migraciones con `check()` estricto que impiden abrir la base sin camino de recuperación
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:416-418, 768-806 (y CoreModule.kt:27-71, sin fallback)
- Qué está mal: 5→6 y 14→15 abortan con `IllegalStateException` si encuentran importes fuera de rango, pagos con asignaciones que no suman o ledger duplicado. Es una decisión consciente (no corromper dinero), pero el resultado es un cierre en cada arranque.
- Escenario concreto de fallo: un usuario que sigue en v14 con una asignación de pago incompleta actualiza: la app no abre nunca más, sin mensaje ni forma de exportar sus datos.
- Fix mínimo: si ya no quedan usuarios en versiones menores a 15, borrar esas migraciones y subir la versión mínima soportada; si quedan, mostrar una pantalla de recuperación al fallar el arranque.
- Confianza: baja (no sé qué versiones hay en producción)
- Cross-módulo: no

### [DB1-09] `MerchantLogoUrlResolver` deja pasar `..` en la clave del logo
- Severidad: baja
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/core/storage/data/MerchantLogoUrlResolver.kt:13-15 (test en MerchantLogoUrlResolverTest.kt:28-42)
- Qué está mal: `Uri.encode(key, "/")` no codifica `.`, así que `logo-dev/../../otro-bucket/x.png` se normaliza por el cliente HTTP a otro objeto público del mismo proyecto.
- Escenario concreto de fallo: una fila del catálogo con `logo_path` malicioso o erróneo hace que la app pida un archivo de otro bucket público; el impacto es bajo (solo lectura de objetos ya públicos) y requiere escribir en el catálogo global.
- Fix mínimo: `if (key.split('/').any { it == ".." || it == "." }) return null` más un caso en el test.
- Confianza: baja
- Cross-módulo: sí → CAT1 (quién escribe `logo_path` en el catálogo)

### [DB1-10] Converters de enum lanzan con valores desconocidos
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/core/database/DatabaseConverters.kt:32, 34
- Qué está mal: `PlanSelection.valueOf` y `OutboxStatus.valueOf` lanzan `IllegalArgumentException` ante un nombre desconocido, lo que rompe cualquier lectura de la tabla. Room tampoco tiene política de downgrade (instalar un APK más antiguo con base v40 lanza `IllegalStateException` al abrir).
- Escenario concreto de fallo: se renombra `OutboxStatus.TERMINAL_ERROR` sin migración de datos, o se hace rollback a una compilación anterior: la app se cae al leer `sync_outbox`/`plan_preferences`.
- Fix mínimo: `runCatching { valueOf(it) }.getOrNull()` con valor por defecto en las dos conversiones de planes.
- Confianza: media
- Cross-módulo: no

### [DB1-11] Tests duplicados y mal ubicados
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt:57-98 vs DebtRoomMigrationTest.kt:19-46; app/src/test/java/com/kipu/app/core/storage/data/MerchantLogoUrlResolverTest.kt:44-68
- Qué está mal: `MovementRoomMigrationTest` prueba la migración 19→20 (deudas), que ya cubre `DebtRoomMigrationTest` (18→20); `MerchantLogoUrlResolverTest` contiene dos tests de `MerchantLogoPrefetchPlan`, que no es de esta clase ni de este paquete.
- Escenario concreto de fallo: al cambiar la migración 19→20 hay que actualizar dos tests; los tests de `MerchantLogoPrefetchPlan` no aparecen donde se buscan.
- Fix mínimo: borrar el caso de deudas de `MovementRoomMigrationTest` y mover los dos tests de prefetch a un `MerchantLogoPrefetchPlanTest` en categories.
- Confianza: media
- Ahorro estimado: 40 líneas
- Cross-módulo: no

### [DB1-12] Redactor: falsos positivos y huecos (refresh_token JSON, tarjetas)
- Severidad: baja
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:15-16
- Qué está mal: `PASSWORD_PARAM_REGEX` incluye espacio como separador, así que "Sync pass completed" se convierte en "Sync pass [REDACTED_PASSWORD]" (se pierde información de diagnóstico), mientras `RECOVERY_TOKEN_REGEX` exige `=`: `"refresh_token":"abcdefghijkl"` (token opaco, no JWT) pasa sin redactar. Tampoco hay patrón para números de tarjeta de 13-19 dígitos pese a que el KDoc promete "datos financieros sensibles".
- Escenario concreto de fallo: un cuerpo de error de auth con `"refresh_token":"..."` se registra por un `SecureLog.e` futuro y el token queda en logcat.
- Fix mínimo: cambiar `[:= ]` por `[:=]` en la línea 15 y permitir `["']?\s*[:=]\s*["']?` en la 16.
- Confianza: media (hoy los llamadores no registran cuerpos de respuesta)
- Cross-módulo: no

### [DB1-13] `SecureLog.d` sin llamadas
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:41-43
- Qué está mal: grep en `app/src/main` solo encuentra usos de `SecureLog.e` (17), `.w` (35) y `.i` (5); `.d` no se llama.
- Escenario concreto de fallo: ninguno funcional; código muerto que mantener.
- Fix mínimo: borrar la función.
- Confianza: alta
- Ahorro estimado: 4 líneas
- Cross-módulo: no

Menores no listados (3 aprox.): comentario obsoleto "renumbered at integration" en BudgetRoomMigration.kt:6 y nombre `MIGRATION_BUDGETS_25_26` fuera de convención; FQN repetidos en CoreModule.kt; migraciones 27→28 y 34→35 sin test dedicado (son `ADD COLUMN` triviales cuyo schema sí se compara en los tests encadenados).

## Código de riesgo sin tests
- RoomMigrations.kt `ensureCreditInstallmentStatusGuards` (1457): pagar una cuota y comprobar PAID/PARTIAL, en base nueva y migrada.
- RoomMigrations.kt `MIGRATION_36_37` (1556): caso con `reference` nula en `receipt_candidates` (el único test usa referencia no nula).
- LogRedactor.kt: password/recovery_token y `SecureLog` con throwable (que conserve el stack).
- MerchantLogoUrlResolver.kt: clave `../x` y clave con espacios/acentos.
- Migraciones 5→6 y 14→15: sin test del camino que aborta por `check`, solo el camino feliz.

## Lo que no revisé
- Leí completos los 8 archivos main y los 3 tests unitarios. De androidTest leí en detalle BudgetRoomMigrationTest, HybridYapeMigrationTest, MovementSourceMigrationTest, el arranque de ExpectedPaymentAssociationTest y la lista de tests de CaptureAtomicResolutionTest; del resto (CaptureRoomMigrationTest, CardCycleTerms, CreditLiability, CreditRoom, Debt, Goal, MerchantCatalog, MerchantRule, Receipt, Recurrence, Statement, MovementRoomMigrationTest) comprobé solo versiones migradas, nº de tests y de asserts.
- Las 64 entidades (fuera de alcance): solo comprobé coincidencia de nombres y deltas de schemas JSON, no tipos columna a columna contra el código de cada entidad.
- Rendimiento real de los triggers (no se puede ejecutar nada) y el cuerpo de `CREDIT_INSTALLMENT_BALANCES_SQL` más allá de la línea 132.

## Dudas
- ¿Qué versiones de la base existen en producción? Define si DB1-08 y las migraciones 1→15 siguen siendo relevantes o pueden retirarse.
- Unidades de tiempo mezcladas: `accounts.opened_at/updated_at` y los `Instant` usan microsegundos; `transactions`, `ledger_entries` y `balance_projections` milisegundos. La migración 14→15 (líneas 829-834) escribe `opened_at` (micros) y `created_at` (ms) en `balance_projections.last_transaction_at`. Hoy ninguna consulta lee esa columna, pero ¿es intencional la mezcla? (cross-módulo ACC1/MOV1).
- ¿La zona de negocio `America/Lima` fija (CoreModule.kt:23, migración 31→32) es definitiva aunque `user_profiles` tenga `time_zone`?

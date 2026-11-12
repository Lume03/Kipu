# SQL3 — supabase/migrations 43–72 (20261008 … 20261018190600) + sus tests

Esta porción implementa comandos de deudas, conciliación de compras de Google Play, alias de comercios, billeteras, reservas de metas y reglas recurrentes. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y reintentos frecuentes después de trabajar sin conexión. Auditoría estática en `main`: leí completas las 30 migraciones y los 30 archivos de pruebas asignados, y busqué sustituciones y modificaciones de los objetos en todas las migraciones posteriores.

## Hallazgos

Resultado: **0 críticos, 3 altos, 10 medios, 0 bajos**. No incluí sospechas sin un escenario demostrable ni contabilicé como defectos actuales las definiciones históricas ya corregidas.

### [SQL3-01] Editar un gasto devuelve respaldo inexistente a las metas
- Severidad: alta
- Categoría: datos
- Ubicación: supabase/migrations/20261018190000_ep_mov_opening_ledger.sql:125–137
- Qué está mal: `goal_confirmed_balance` solo suma asientos de transacciones `CONFIRMED`. Una edición válida deja el movimiento en `REVISED`, que sigue vigente y conserva su efecto financiero neto; el cálculo elimina todos sus asientos. La definición final de este helper sigue en este archivo.
- Escenario concreto de fallo: apertura de 100000 centavos y gasto confirmado de 60000; se edita únicamente su nota y queda `REVISED` → el respaldo pasa de 40000 a 100000 y se pueden aceptar reservas por dinero ya gastado. El camino de edición asigna `REVISED` en `supabase/migrations/20261021200100_reconcile_movement_revision_contract.sql:373`; la prueba de saldos solo cubre `CONFIRMED`, `VOIDED` y eliminados (`goal_opening_ledger_test.sql:104–116`).
- Fix mínimo: incluir `REVISED` junto a `CONFIRMED`, sumando el neto del ledger existente. Añadir un caso que edite la nota de un gasto y compruebe que el respaldo no aumenta.
- Confianza: alta
- Cross-módulo: no

### [SQL3-02] Reasignar reservas permite superar las dos metas activas Free
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20261017180300_ep_met_goal_lifecycle_commands.sql:89–110
- Qué está mal: `reassign_goal_reserve_v1` puede convertir la meta origen de `COMPLETED` a `ACTIVE` sin comprobar el cupo ni tomar su bloqueo. CREATE, UPDATE y RELEASE sí tienen controles para impedir esa transición por encima del límite. No hay una definición posterior que sustituya esta función.
- Escenario concreto de fallo: usuario Free con dos metas activas B/C y una A completada con reserva 100 y objetivo 100; reasigna 10 de A a B, cuyo objetivo es 1000 → A queda activa con 90, B y C siguen activas: tres metas activas. Incluso sin concurrencia se viola el máximo de FR-005 de EP-MET.
- Fix mínimo: comprobar esta reactivación bajo el mismo bloqueo de cupo y rechazarla sin efectos si no existe una resolución explícita del exceso. Test mínimo: el caso A/B/C anterior debe conservar estados y reservas al rechazar.
- Confianza: alta
- Cross-módulo: no

### [SQL3-03] Un CREATE de recurrencia aplicado puede rechazarse al reintentarlo
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20261018190500_ep_rec_create_update.sql:202–213
- Qué está mal: `create_recurrence_v1` valida el estado actual de las referencias antes de consultar el recibo de la operación. El recibo debe permitir recuperar un resultado ya aplicado aunque después cambie la cuenta o categoría.
- Escenario concreto de fallo: dispositivo A crea la regla y pierde la respuesta; B archiva su cuenta; A reenvía exactamente la misma operación y hash → recibe `REFERENCE_NOT_FOUND` en lugar del resultado guardado, aunque la recurrencia ya existe. El cliente recibe un rechazo de una creación que sí quedó persistida.
- Fix mínimo: validar identidad, estructura y hash, consultar el recibo y devolverlo antes de validar referencias para una creación nueva. Test mínimo: crear, archivar la cuenta y repetir el mismo comando; debe devolver el recibo original sin otra fila ni evento de sync.
- Confianza: alta
- Cross-módulo: no

### [SQL3-04] Anular principal e interés desde dos dispositivos causa un fallo definitivo evitable
- Severidad: media
- Categoría: concurrencia
- Ubicación: supabase/migrations/20261008150000_s5_settle_debt.sql:351–380,524–525
- Qué está mal: cada anulación bloquea primero el movimiento seleccionado, después el principal y después el interés. Seleccionar distintos miembros del mismo pago invierte el orden de bloqueo. Además, el manejador captura el bloqueo mutuo y lo devuelve como `SETTLEMENT_VOID_FAILED` con `retryable=false`.
- Escenario concreto de fallo: A anula el principal y mantiene su bloqueo; B anula el interés y mantiene el suyo; A espera el interés y B espera el principal → PostgreSQL aborta una operación por bloqueo mutuo y esa operación se presenta como fallo no reintentable. El test actual anula solo desde el interés en una sesión (`debt_settlement_test.sql:176–196`).
- Fix mínimo: descubrir el grupo sin bloquear un miembro arbitrario y bloquear el grupo siempre en el mismo orden antes de revalidar revisiones. Test mínimo: dos sesiones anulando simultáneamente principal e interés, sin error definitivo y con una sola compensación financiera.
- Confianza: alta
- Cross-módulo: no

### [SQL3-05] Se puede pagar una cuota de un cronograma cancelado
- Severidad: media
- Categoría: bug
- Ubicación: supabase/migrations/20261008150000_s5_settle_debt.sql:166–184,250–255
- Qué está mal: al seleccionar `installment_id`, el RPC comprueba dueño y borrado pero no que la cuota siga pendiente o parcial. Después sobrescribe su estado, incluso si era `CANCELLED`. El reemplazo de cronograma conserva precisamente esas cuotas como historial cancelado (`20261008160000_s5_debt_schedule.sql:180–182`).
- Escenario concreto de fallo: se reemplaza un cronograma y la cuota antigua I queda cancelada; llega un comando con la revisión vigente de la deuda pero todavía referido a I y un principal menor que su importe pendiente → se aplica el pago y I vuelve a `PARTIAL` o `PAID`, mientras el cronograma nuevo sigue intacto. Se contabiliza el pago contra un plan que ya no está vigente.
- Fix mínimo: rechazar cuotas cuyo estado no sea `PENDING` o `PARTIAL` antes de insertar movimientos. Test mínimo: cancelar/reemplazar el cronograma e intentar pagar una cuota anterior; no deben aparecer movimientos ni eventos.
- Confianza: alta
- Cross-módulo: no

### [SQL3-06] Alias y preferencias también pierden la respuesta idempotente al cambiar sus referencias
- Severidad: media
- Categoría: bug
- Ubicación: supabase/migrations/20261008161540_ep_cco_s5_alias_commands.sql:39–57; supabase/migrations/20261008161631_ep_cco_s5_preference_commands.sql:28–66
- Qué está mal: ambos upserts validan comercio/categoría vigentes antes de consultar el recibo. Un cambio posterior de esas referencias transforma una repetición válida en un error nuevo.
- Escenario concreto de fallo: una preferencia se aplica, se pierde su respuesta y después se inactiva la categoría; repetir el mismo comando devuelve `CATEGORY_NOT_ACTIVE_OR_OWNED` en vez de `DUPLICATE`. Para un alias, inactivar el comercio provoca `MERCHANT_NOT_ACTIVE` en la misma situación.
- Fix mínimo: recuperar primero el recibo bajo el bloqueo de operación y comprobar las referencias solo si la operación es nueva. Test mínimo: repetir una preferencia ya aplicada después de inactivar su categoría y exigir el recibo original.
- Confianza: alta
- Cross-módulo: no

### [SQL3-07] La plantilla recurrente admite categorías incompatibles con el gasto
- Severidad: media
- Categoría: bug
- Ubicación: supabase/migrations/20261018190500_ep_rec_create_update.sql:134–136
- Qué está mal: `_rec_template_references_valid` acepta cualquier categoría propia activa para `EXPENSE`, sin comprobar `category_type`. La escritura de movimientos sí rechaza categorías de ingresos para gastos; la validación final está en `supabase/migrations/20261104051901_system_category_expense_catalog.sql:41–47`.
- Escenario concreto de fallo: se crea una recurrencia de gasto con una categoría propia `INCOME` → CREATE responde `APPLIED`, pero su plantilla no puede registrarse como gasto con esa misma categoría. La regla queda aceptada con referencias que contradicen el contrato de movimientos que debe cumplir.
- Fix mínimo: exigir categoría `GENERAL` o `EXPENSE` en esta rama. Test mínimo: CREATE de gasto con categoría de ingresos debe rechazarse sin recurrencia, recibo ni fila de sync.
- Confianza: alta
- Cross-módulo: no

### [SQL3-08] Cambiar el intervalo CUSTOM evita la validación de cambio de cadencia
- Severidad: media
- Categoría: bug
- Ubicación: supabase/migrations/20261018190500_ep_rec_create_update.sql:266–268
- Qué está mal: exigir los cuatro campos de calendario depende solo de que cambie `frequency`. También cambia la cadencia al modificar `interval_count` o `custom_interval_unit` manteniendo `frequency='CUSTOM'`. El contrato aprobado exige los cuatro campos para cualquier cambio de cadencia (`specs/007-ep-rec-recurrencias-obligaciones/contracts/recurrence-command-contract.md:84,91`).
- Escenario concreto de fallo: regla CUSTOM semanal con intervalo 1, inicio 2026-01-01 y próxima fecha 2026-01-15; se envía únicamente intervalo 2 y `reminder_lead_days` vigente → el RPC cambia la cadencia y responde `APPLIED` sin que el comando incluya frecuencia, unidad ni próxima fecha. La fecha heredada pertenece a ambas secuencias y la validación posterior no detecta la omisión.
- Fix mínimo: comparar los tres componentes de cadencia antes de exigir el conjunto completo. Test mínimo: ese parche parcial debe devolver `CADENCE_FIELDS_REQUIRED` y conservar la regla.
- Confianza: alta
- Cross-módulo: no

### [SQL3-09] Números enteros escritos con decimales fallan después de validarse
- Severidad: media
- Categoría: bug
- Ubicación: supabase/migrations/20261018190500_ep_rec_create_update.sql:168,198,216,239–249
- Qué está mal: el validador acepta números JSON matemáticamente enteros, pero después algunos campos se convierten de texto directamente a `bigint`/`smallint`. PostgreSQL no acepta `1.0` como entrada textual de `bigint`. El contrato sí acepta esa grafía; el test numérico actual verifica el helper, no un RPC con esos campos (`recurrence_create_update_test.sql:24–52`).
- Escenario concreto de fallo: un CREATE correcto con `interval_count: 1.0` y hash canónico correcto supera `_rec_valid_integer`, pero el cast de calendario lanza una excepción → devuelve `INVALID_COMMAND`. Un UPDATE con `expected_revision: 1.0` falla por la misma razón aunque sea equivalente a la revisión 1.
- Fix mínimo: tras validar integralidad y rango, convertir mediante `numeric` al entero requerido. Test mínimo: el mismo CREATE con intervalo `1` y `1.0` debe tener el mismo resultado semántico; comprobar también revisión decimal en UPDATE.
- Confianza: alta
- Cross-módulo: no

### [SQL3-10] El harness de migración puede imprimir PASS con aserciones pgTAP fallidas
- Severidad: media
- Categoría: bug
- Ubicación: supabase/tests/database/debt_sprint5_migration_upgrade.ps1:45–49
- Qué está mal: se ejecuta pgTAP mediante `psql` y se usa únicamente su código de salida. Una aserción pgTAP fallida produce una fila TAP `not ok`, sin ser un error SQL; `ON_ERROR_STOP` no convierte ese resultado en fallo del proceso.
- Escenario concreto de fallo: la comprobación de saldo esperado 8250 en `debt_sprint5_migration_test.sql:71–72` devuelve `not ok` por una regresión, pero ninguna consulta lanza excepción → `psql` termina con 0 y el script imprime que la actualización preservó los datos.
- Fix mínimo: ejecutar el archivo con un runner TAP que devuelva error cuando falle una aserción, o comprobar explícitamente sus resultados antes del mensaje PASS. Test mínimo del harness: introducir un `SELECT fail(...)` en una entrada sintética y exigir salida no cero.
- Confianza: alta
- Cross-módulo: no

### [SQL3-11] El test de concurrencia de deudas falla durante el setup actual
- Severidad: media
- Categoría: bug
- Ubicación: supabase/tests/database/debt_sprint5_quota_concurrency.ps1:16–17
- Qué está mal: el fixture inserta una cuenta `SAVINGS` sin `preset_id`. En el esquema final, `accounts_validate_preset_compatibility` exige un preset bancario no nulo para ese tipo (`20261012100000_enforce_account_preset_compatibility.sql:14–16` y trigger en `20261012100100_enable_account_preset_compatibility.sql:6–8`).
- Escenario concreto de fallo: ejecutar el harness sobre todas las migraciones de `main` → el INSERT de setup lanza `INVALID_REQUEST: Savings and bank accounts require a bank preset`; nunca se lanzan las dos aperturas que deberían probar el último cupo Free.
- Fix mínimo: añadir `preset_id='GENERIC'` al fixture, como ya hacen los tests SQL de deudas. Comprobar que el harness alcanza y verifica ambos resultados concurrentes.
- Confianza: alta
- Cross-módulo: no

### [SQL3-12] Los harnesses de metas no completan su ejecución y limpieza con el esquema final
- Severidad: media
- Categoría: bug
- Ubicación: supabase/tests/database/goal_release_quota_concurrency.py:50–63,228–236; supabase/tests/database/goal_reserve_concurrency.ps1:96–105
- Qué está mal: el harness Python crea `pg_temp.goal_cmd` como postgres y luego lo llama como `authenticated` sin conceder EXECUTE, que ya no se hereda de PUBLIC tras el cambio de privilegios por defecto. Además, ambos harnesses borran `financial_movements` antes de borrar sus asientos de apertura, cuya referencia usa `ON DELETE RESTRICT`.
- Escenario concreto de fallo: en el esquema completo, el Python falla por permisos al invocar el firmante; su `finally` intenta borrar una apertura con asientos creados por el trigger y vuelve a fallar por la FK. El PowerShell alcanza la carrera, pero su limpieza falla por la misma FK y deja datos sintéticos persistidos.
- Fix mínimo: conceder EXECUTE al firmante temporal de Python, como hace `_goal_test_fixture.sql.inc:63–67`, y borrar primero los asientos de los usuarios sintéticos en ambas limpiezas. Verificar después que no quedan filas sintéticas, también al provocar un error durante la carrera.
- Confianza: alta
- Cross-módulo: no

### [SQL3-13] La sobreutilización de tarjeta solo se comprueba buscando texto SQL
- Severidad: media
- Categoría: test-faltante
- Ubicación: supabase/tests/database/allow_card_purchase_overutilization_p10.test.sql:11–24
- Qué está mal: el test busca la ausencia de una cadena de error y la presencia de una asignación en cuerpos de funciones. Nunca registra una compra que supere la línea ni comprueba el pasivo y los asientos resultantes. No encontré otro caso de sobreutilización en `supabase/tests`.
- Escenario concreto de fallo: una regresión rechaza la compra mediante otro código de error o persiste un principal menor, manteniendo el texto buscado en otra rama → estas tres aserciones siguen aprobando aunque el flujo de dinero sea incorrecto.
- Fix mínimo: un caso funcional con línea 10000, deuda previa 9000 y compra 2000; exigir `APPLIED`, aumento íntegro del principal a 11000 y ningún asiento duplicado al repetir la misma operación. Sustituir las aserciones de texto por ese caso.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `internal.goal_confirmed_balance` → editar la nota de un gasto vigente y comprobar que su importe sigue descontándose del respaldo (SQL3-01).
- `reassign_goal_reserve_v1` → origen completado y dos destinos/otras metas activas de usuario Free; no producir una tercera activa (SQL3-02).
- `create_recurrence_v1` y upserts de comercios → reintento idéntico después de inactivar una referencia ya utilizada (SQL3-03/06).
- `void_debt_settlement_v1` → dos sesiones seleccionando miembros distintos del mismo pago (SQL3-04).
- `settle_debt_v1` → cuota cancelada de un cronograma reemplazado, sin residuos financieros al rechazar (SQL3-05).
- `_rec_template_references_valid` / `update_recurrence_v1` → categoría de ingresos en gasto y cambio parcial de intervalo CUSTOM (SQL3-07/08).
- RPC de recurrencias → valores integrales decimales en campos realmente convertidos a enteros, además del helper puro (SQL3-09).
- Registro de compra con sobreutilización → principal íntegro y reintento sin duplicados mediante el RPC público (SQL3-13).
- Billing RTDN → los tests asignados cubren redelivery y leases de forma secuencial; falta una prueba de dos conexiones para adquisición simultánea del mismo token y expiración de lease durante persistencia. No lo contabilicé como defecto de producción demostrado.

## Lo que no revisé

- Ningún archivo asignado quedó sin leer completo. Las pruebas se auditaron como código; **no se ejecutaron** SQL, servicios Supabase, contenedores, red, Gradle ni dispositivos.
- Fuera del rango solo leí definiciones, restricciones, modificaciones posteriores y referencias documentales necesarias para comprobar la vigencia de los objetos. No audité los módulos Android ni las edge functions, ni certifiqué el estado desplegado.
- Descarté como hallazgos históricos la identidad aleatoria de transferencia de meta (corregida en `20261019190000`), el índice único incompatible con revisiones sucesivas (`20261102013425`), la falta de activación del ledger de apertura (`20261026013311`), la fecha UTC de reanudación (`20261101150000`) y el bloqueo de reactivación Premium (`20261105180419`). Sus definiciones finales pertenecen a SQL4.
- El estado inicial ya incluía archivos no versionados ajenos en `.agents/skills/ponytail/`, `.claude/`, `skills-lock.json` y otros informes del directorio de auditoría. Mi única escritura fue este informe; no cambié rama, código, tests ni otros informes.

## Dudas

- EP-DEU exige como máximo dos deudas activas Free y también exige reabrir una deuda cuando se anula un pago liquidatorio. `void_debt_settlement_v1` reabre sin comprobar ese cupo: liquidar A, abrir C y anular la liquidación de A deja A/B/C activas. Falta precisar si esa corrección financiera permite exceso temporal y cuál es la resolución de producto; no propongo bloquear la corrección ni ocultar historia por suposición.
- La edición de configuración de recurrencias `CANCELLED` no está bloqueada en UPDATE, aunque la cancelación se declara terminal. El estado no se reactiva por ese RPC; falta aclarar si se permite editar metadatos históricos o si toda edición exige clonar la regla.

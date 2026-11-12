# SQL4 — supabase/migrations 73–116 (20261019 … 20261108) + tests de RLS y resto de tests

Esta porción define los contratos recientes de movimientos, reembolsos, tarjetas, presupuestos, planes, comercios y borrado de cuenta, junto con sus permisos y pruebas SQL. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y reintentos frecuentes después de trabajar sin conexión. Auditoría estática en `main`: 44 migraciones, los 27 tests asignados y los archivos auxiliares indicados leídos completos; no ejecuté SQL, pruebas, builds ni operaciones de red.

## Hallazgos

9 hallazgos: 1 crítico, 4 altos y 4 medios. Busqué cada objeto reportado en todo `supabase/migrations/` para comprobar su definición final; los reemplazos y parches posteriores se tuvieron en cuenta. No contabilicé sospechas sin escenario demostrable ni defectos históricos ya corregidos.

### [SQL4-01] Anular una compra reembolsada deja dinero sin contrapartida
- Severidad: crítica
- Categoría: datos
- Ubicación: supabase/migrations/20261021200100_reconcile_movement_revision_contract.sql:125–130,307–375; supabase/migrations/20261024182114_ep_mov_refund_v1.sql:194–224
- Qué está mal: La revisión genérica excluye tarjetas, cuotas y el puntero antiguo de reembolso, pero no los enlaces `transaction_links/REFUND` del contrato nuevo. Puede revertir o reducir una compra estándar y conservar íntegro su reembolso; el parche final de 20261104051901 solo cambia la validación de categorías.
- Escenario concreto de fallo: Una compra estándar resta S/100 y su reembolso completo suma S/100. Otro dispositivo envía una anulación pendiente de esa compra con su revisión todavía válida: el servidor suma S/100 de reversión y deja el reembolso confirmado, por lo que el saldo termina S/100 por encima del saldo anterior a la compra; también permite reducir la compra por debajo de lo ya reembolsado.
- Fix mínimo: Bajo el bloqueo existente de la compra, rechazar edición/anulación genérica cuando existan reembolsos válidos vinculados, con `OPERATION_SPECIALIZED`. Añadir un test compra → reembolso total → anulación atrasada que compruebe rechazo y saldo intacto.
- Confianza: alta
- Cross-módulo: no

### [SQL4-02] El pago de tarjeta calcula fondos ignorando movimientos revisados
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20261031163043_credit_purchase_terms_and_cycles.sql:1007–1015
- Qué está mal: `allocate_credit_payment_v1_pre_liability` suma solo asientos cuya transacción sigue `CONFIRMED`. La edición canónica cambia el estado a `REVISED` y agrega compensaciones: al excluir toda esa transacción se pierde su efecto vigente. El wrapper público final de SQL2 llama a esta implementación antes de publicar el pasivo y no recalcula los fondos.
- Escenario concreto de fallo: Una cuenta tiene S/100 iniciales y un gasto de S/90 editado a S/95. El saldo real es S/5, pero este cálculo descarta los tres asientos del gasto revisado y obtiene S/100: acepta pagar S/20 a la tarjeta y deja la cuenta sobregirada; un ingreso revisado produce el error contrario y rechaza pagos válidos.
- Fix mínimo: Reutilizar el cálculo canónico que suma asientos originales, compensaciones y reemplazos una sola vez, incluyendo los hechos de apertura con el contrato actual. Test mínimo: gasto revisado a S/95 desde cuenta con S/100 → pago de S/20 rechazado sin nuevos asientos.
- Confianza: alta
- Cross-módulo: no

### [SQL4-03] Reactivar instrumentos aplica un cupo distinto al de crearlos
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20261101140000_credit_card_principal_quota_archive.sql:304–313
- Qué está mal: La reactivación cuenta filas de cuentas y tarjetas, incluye pasivos y omite tanto `private.instrument_quota_groups` como `private.is_user_pro`. La corrección de cupo compartido sí se usa en creación y registro, pero no aquí.
- Escenario concreto de fallo: Un usuario Premium con cuatro instrumentos activos intenta reactivar una cuenta archivada y recibe `FREE_LIMIT_REACHED`. Un usuario Free con cuatro bancos tampoco puede reactivar la tarjeta de débito de uno de esos bancos, aunque comparte un cupo que ya estaba ocupado.
- Fix mínimo: Aplicar el guard compartido al número de grupos que quedará después de reactivar, bajo el mismo bloqueo de cupo por propietario usado por creación. Probar una reactivación Premium sobre cuatro instrumentos y una tarjeta de débito Free que comparte grupo.
- Confianza: alta
- Cross-módulo: no

### [SQL4-04] Un reintento antiguo de archivo sobrescribe una reactivación posterior
- Severidad: alta
- Categoría: concurrencia
- Ubicación: supabase/migrations/20261101140000_credit_card_principal_quota_archive.sql:299–344
- Qué está mal: El RPC extrae `operation_id` pero nunca lo consulta ni lo guarda; tampoco compara una revisión esperada. Cada reenvío vuelve a actualizar el estado e incrementar la revisión, incluso después de otra acción confirmada.
- Escenario concreto de fallo: El dispositivo A archiva una cuenta con operación X y pierde la respuesta. B la reactiva y sincroniza; al reconectarse A reenvía X y el servidor vuelve a archivarla, haciendo desaparecer de los selectores una cuenta que B ya había reactivado.
- Fix mínimo: Guardar y consultar un recibo por propietario/operación antes de mutar, con hash del comando y bloqueo de idempotencia, como los demás RPC del módulo. Test mínimo: archivo X → reactivación Y → reenvío X devuelve su recibo y mantiene la cuenta activa.
- Confianza: alta
- Cross-módulo: no

### [SQL4-05] El interruptor de planes depende de un usuario ficticio ausente
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20261021125728_plan_qa_entitlement_overrides.sql:176–182; supabase/migrations/20261103152406_delete_account.sql:40–47; supabase/migrations/20261103162347_account_deletion_concurrency_fix.sql:6–13; supabase/tests/database/dev_plan_mode_test.sql:3–5
- Qué está mal: `set_test_plans_enabled` registra el cambio global con `user_id=00000000-0000-0000-0000-000000000000`. El guard de borrado se instala también sobre esa auditoría y exige que cada `user_id` exista en Auth. Ninguna migración crea ese usuario; los tests lo inventan expresamente antes de usar el interruptor, ocultando la incompatibilidad.
- Escenario concreto de fallo: Tras aplicar las migraciones a una base sin ese usuario artificial, el operador ejecuta `internal.set_dev_plan_mode(false, 'preparación del lanzamiento')`. El INSERT de auditoría lanza `42501` y revierte también el UPDATE del interruptor; si estaba habilitado, sigue habilitado.
- Fix mínimo: Separar la auditoría global de la propiedad de usuarios: permitir sujeto NULL para eventos del interruptor y escribir NULL en esos eventos. El test debe cambiar el interruptor en ambos sentidos sin crear un usuario centinela y comprobar el estado persistido.
- Confianza: alta
- Cross-módulo: no

### [SQL4-06] Cambiar el calendario recalcula mal los presupuestos históricos
- Severidad: media
- Categoría: bug
- Ubicación: supabase/migrations/20261024190000_ep_pre_budget_scope_snapshot.sql:182–196
- Qué está mal: `get_budget_status` calcula primero las fechas con el calendario actual del presupuesto y solo busca un snapshot que tenga exactamente esas fechas. Nunca elige primero el periodo histórico persistido que contiene la fecha consultada.
- Escenario concreto de fallo: Junio tiene un snapshot mensual [01/06,01/07), límite S/300 y alcance Comida; en julio el presupuesto pasa a semanal y alcance Total. Consultar 10/06 calcula la semana [08/06,15/06), no encuentra el snapshot mensual y devuelve límite/alcance actuales y únicamente el gasto de esa semana.
- Fix mínimo: Para una fecha con periodo persistido, obtener primero ese periodo y usar sus fechas, límite y alcance; calcular límites desde el presupuesto solo cuando no exista snapshot. Test mínimo: snapshot mensual antiguo seguido de cambio a semanal y consulta dentro del mes antiguo.
- Confianza: alta
- Cross-módulo: no

### [SQL4-07] Cada cuota o asignación recalcula todas las tarjetas del usuario
- Severidad: media
- Categoría: escala
- Ubicación: supabase/migrations/20261031163043_credit_purchase_terms_and_cycles.sql:146–171,1041–1057; supabase/migrations/20261101140000_credit_card_principal_quota_archive.sql:100–104
- Qué está mal: Los triggers por fila de cuotas y asignaciones llaman a un refresco de todos los vencimientos del usuario. Cada refresco vuelve a agregar pagos y reembolsos, y la distribución de reembolsos consulta de nuevo la CTE `base` por cada cuota, incluidas compras antiguas sin relación con el cambio.
- Escenario concreto de fallo: Un usuario con miles de cuotas históricas paga una tarjeta y el pago se distribuye entre 36 cuotas. Las 36 inserciones de asignaciones disparan 36 recorridos completos y consultas correlacionadas sobre ese historial dentro de la misma transacción, manteniendo bloqueada la tarjeta y retrasando la sincronización del otro dispositivo.
- Fix mínimo: Acotar el refresco a la compra o tarjeta afectada y ejecutarlo una vez al terminar el comando de pago, conservando la atomicidad. Test mínimo de regresión: un pago distribuido mantiene los mismos estados y no actualiza cuotas de otra tarjeta; medir el plan local en una fase posterior autorizada.
- Confianza: alta
- Cross-módulo: no

### [SQL4-08] Dos reintentos simultáneos de preferencias fallan con clave duplicada
- Severidad: media
- Categoría: concurrencia
- Ubicación: supabase/migrations/20261103172646_profile_preferences_private_usage.sql:45–80,113–114
- Qué está mal: El RPC busca el recibo antes de bloquear el perfil. Después de esperar el bloqueo no vuelve a buscarlo, por lo que un segundo envío simultáneo intenta insertar el mismo recibo como conflicto en vez de devolver `DUPLICATE`.
- Escenario concreto de fallo: Dos peticiones con igual operación y revisión 1 llegan juntas y ambas ven que no hay recibo. La primera aplica revisión 2; la segunda espera, lee revisión 2, entra en CONFLICT e inserta un recibo cuya clave ya existe: recibe `23505` en lugar del resultado idempotente esperado.
- Fix mínimo: Serializar propietario/operación antes de leer el recibo, o volver a consultarlo después de adquirir el bloqueo del perfil. Test mínimo: dos conexiones envían exactamente la misma operación a la vez; una aplica y la otra devuelve DUPLICATE sin excepción.
- Confianza: alta
- Cross-módulo: no

### [SQL4-09] La versión de comercios hace un barrido global bajo bloqueo
- Severidad: media
- Categoría: rendimiento
- Ubicación: supabase/migrations/20261102222216_merchant_discovery.sql:82–91
- Qué está mal: Cada INSERT/UPDATE de comercio obtiene un bloqueo global y calcula `MAX(version)` recorriendo la tabla, sin índice sobre `version` en las migraciones. Incluye comercios privados y las actualizaciones masivas de logos, por lo que el trabajo crece con el catálogo completo y se repite por cada fila modificada.
- Escenario concreto de fallo: Miles de usuarios acumulan decenas de miles de comercios; la sincronización mensual actualiza miles de logos. Cada logo vuelve a recorrer el catálogo mientras conserva el mismo bloqueo hasta el commit, bloqueando también la creación de un comercio privado desde cualquier usuario.
- Fix mínimo: Añadir un índice sobre `merchant_services(version)` para que MAX lea el extremo del índice; conservar de momento la serialización y verificar después su coste con una carga representativa. Test mínimo: verificar con EXPLAIN, en una fase posterior autorizada, que MAX usa el índice y no un barrido completo.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `internal.apply_movement_revision_v1` + `refund_transaction_v1` → compra estándar reembolsada, seguida de anulación/edición desde otro dispositivo; véase SQL4-01. `movements_refund_test.sql` prueba reintentos del reembolso, pero no su interacción con mantenimiento de la compra.
- `allocate_credit_payment_v1_pre_liability` → fondos después de un gasto revisado y de un ajuste de apertura; véase SQL4-02.
- `set_instrument_archived_v1` → reactivación Premium, grupo banco/débito compartido y reenvío atrasado tras reactivación; SQL4-03/04. `credit_card_principal_archive.test.sql:36–43` verifica el pago y PAID, pero no vuelve a intentar archivar después de liquidar.
- `internal.set_dev_plan_mode` → activar/desactivar sin usuario centinela de Auth; SQL4-05.
- `get_budget_status` → cambio de calendario entre periodos; SQL4-06. Los tests de snapshots cambian alcance manteniendo el mismo calendario mensual.
- `update_profile_preferences` → dos conexiones con el mismo operation_id; SQL4-08. La prueba actual solo repite secuencialmente.
- `account_deletion_concurrency.test.sql:5–14` → una conexión escribe mientras otra purga. El archivo comprueba modos de bloqueo y dos solicitudes secuenciales dentro de una sola transacción; no prueba la carrera que anuncia su nombre.

## Lo que no revisé

- Ningún archivo del alcance quedó sin lectura completa: revisé las 44 migraciones y los 27 tests enumerados, `supabase/sprint_3_remote_baseline_routines.sql`, los cinco archivos de `supabase/local-baseline/`, el diagnóstico de metas y el snippet de programación de logos.
- Leí definiciones anteriores y contratos Kotlin únicamente para seguir los llamadores y distinguir objetos reemplazados. No hice una auditoría adicional de los módulos de otros workers.
- No ejecuté pgTAP, SQL, Gradle, ADB, despliegues ni consultas a Supabase local/remoto. Las conclusiones describen el árbol versionado, no acreditan el estado desplegado ni tiempos medidos.
- El estado inicial ya contenía archivos ajenos sin seguimiento (`.agents/skills/ponytail/`, `.claude/`, `skills-lock.json` e informes de la ronda); no los modifiqué. Mi única escritura fue este informe.

## Dudas

- El RPC de consumo de presupuestos conserva `p_time_zone DEFAULT 'UTC'`, mientras control documental declara America/Lima para fechas de negocio y la migración original menciona zona del dispositivo. No agregué un hallazgo separado: se necesita confirmar si el contrato del RPC debe aceptar zonas variables o fijar Lima; el cliente Android leído calcula el consumo localmente y no llama a este RPC.
- El código SQL de reactivación de metas Premium sí está presente, pero `goal_plan_renewal_test.sql` prueba contribuir fuera de la selección Free, no la transición `LOCKED_BY_PLAN → ACTIVE` del nuevo `update_goal_v1`. Falta un caso específico para acreditar esa intención.

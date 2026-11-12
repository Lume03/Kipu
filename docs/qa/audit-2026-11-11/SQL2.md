# SQL2 — supabase/migrations 24–42 (20260925 … 20261007144500) + sus tests

Esta porción incorpora compras y pagos de tarjeta al registro contable, proyecciones para sincronización, verificación de compras de planes, notificaciones y cupos de categorías Free. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y reintentos frecuentes después de trabajar sin conexión. La revisión fue estática, en `main`: leí completas las 19 migraciones y los nueve tests asignados y busqué reemplazos y parches de los objetos señalados en todas las migraciones.

## Hallazgos

Resumen: **0 críticos, 3 altos y 3 medios**; uno de los altos es una referencia cross-módulo a SQL4. No se incluyen como defectos vigentes las implementaciones antiguas reemplazadas ni se propone borrar migraciones históricas.

### [SQL2-01] Borrar una tarjeta puede separarla de una compra concurrente
- Severidad: alta
- Categoría: concurrencia
- Ubicación: `supabase/migrations/20260928110000_credit_card_pull_projection.sql:525–541`.
- Qué está mal: `delete_unused_card_v1` consulta el historial antes de bloquear la tarjeta. El `DELETE` puede esperar a una compra y después borrar la tarjeta sin repetir la comprobación; la relación de `transactions.card_id` usa `ON DELETE SET NULL` (`20260920000000_financial_core_baseline.sql:198`). La función de borrado no tiene reemplazo posterior.
- Escenario concreto de fallo: el dispositivo A inicia la primera compra y mantiene bloqueada la tarjeta; B intenta borrarla y no ve la compra todavía sin confirmar. B supera ambos `EXISTS` y espera en el `DELETE`; A confirma, B continúa y elimina la tarjeta, dejando la compra con `card_id = NULL`, cuotas sin tarjeta localizable y el pasivo contable en la cuenta oculta. La compra vigente bloquea la tarjeta con `FOR UPDATE` en `20261101140000_credit_card_principal_quota_archive.sql:541–545`, pero el borrado toma ese bloqueo demasiado tarde.
- Fix mínimo: bloquear primero la tarjeta del dueño con `SELECT … FOR UPDATE`, y consultar ambos historiales después de adquirir el bloqueo, antes del `DELETE`. Añadir un caso con dos conexiones: compra pendiente de confirmar frente a borrado; tras confirmar la compra, el borrado debe devolver `CARD_HAS_HISTORY`.
- Confianza: alta
- Cross-módulo: no

### [SQL2-02] La edición sigue bloqueando Premium y cuenta débito por separado
- Severidad: alta
- Categoría: bug
- Ubicación: `supabase/migrations/20261003004348_s4_movement_revision_rpc_v1.sql:158–177`.
- Qué está mal: `internal.movement_instrument_locked_v1` mantiene el conteo antiguo de cuentas más tarjetas y no consulta el plan. Sigue siendo la definición final del helper y el RPC vigente de edición lo llama para cambiar cuentas (`20261021200100_reconcile_movement_revision_contract.sql:222–232`). El control compartido ya contempla Premium y grupos banco/débito/billetera, pero este helper lo duplica sin esas reglas.
- Escenario concreto de fallo: un usuario Premium con cinco cuentas bancarias, sin selección Free, edita un gasto para moverlo de una cuenta a otra: recibe `INVALID_REFERENCE` aunque ambas sean válidas. También ocurre en Free con tres bancos y tres tarjetas de débito asociadas: son tres grupos permitidos, pero este helper cuenta seis instrumentos y bloquea el cambio.
- Fix mínimo: sustituir el cálculo duplicado por `private.is_plan_quota_resource_locked(p_user_id,'INSTRUMENTS',p_account_id)`. Añadir una edición entre cuentas con Premium y cinco bancos, y otra con tres grupos banco/débito; el test actual solo cubre el rechazo Free con cinco bancos (`supabase/tests/database/movements_revision_test.sql:94–114`).
- Confianza: alta
- Cross-módulo: no

### [SQL2-03] Un reintento de selección deja de ser idempotente al archivar un recurso
- Severidad: media
- Categoría: bug
- Ubicación: `supabase/migrations/20261007144500_category_free_quota_5_plus_5.sql:409–431,448–454`.
- Qué está mal: `apply_plan_quota_selection` comprueba la elegibilidad actual de los recursos antes de consultar el recibo de la operación ya aceptada. No hay una definición posterior de este RPC que cambie ese orden.
- Escenario concreto de fallo: A envía una selección de cuentas, el servidor la acepta y se pierde la respuesta; B archiva una de esas cuentas. Al reintentar exactamente la operación aceptada, A recibe `Selection contains an ineligible resource` en lugar de `DUPLICATE`. El mismo problema aparece con una categoría desactivada o una deuda cerrada entre aceptación y reintento.
- Fix mínimo: normalizar y calcular la identidad de la petición, bloquear y consultar su recibo antes de validar la elegibilidad mutable; validar recursos solo para una operación nueva. Añadir el caso mínimo aceptar selección → archivar cuenta → reenviar misma operación, comprobando `DUPLICATE` y ausencia de mutaciones nuevas.
- Confianza: alta
- Cross-módulo: no

### [SQL2-04] Un verificador vencido puede liberar el trabajo de su reemplazo
- Severidad: media
- Categoría: concurrencia
- Ubicación: `supabase/migrations/20260927003156_billing_purchase_verification_s3.sql:316–353`.
- Qué está mal: el permiso temporal de acknowledgement se recupera tras cinco minutos, pero `complete_billing_purchase_acknowledgement` y `release_billing_purchase_acknowledgement` reciben únicamente el hash de la compra. No identifican qué adquisición están completando o liberando. Estas tres funciones permanecen vigentes.
- Escenario concreto de fallo: A adquiere el trabajo y queda demorado; cinco minutos después B lo recupera. A recibe finalmente un error y ejecuta `release`, que cambia a `PENDING` el trabajo que ahora pertenece a B; C puede adquirirlo mientras B sigue llamando al proveedor. Los llamadores efectivamente liberan por hash al fallar (`supabase/functions/verify-purchase/index.ts:263–270,288–302`). No se afirma un cobro duplicado: falla la exclusión entre verificadores y el estado deja de representar al trabajador activo.
- Fix mínimo: devolver un identificador de adquisición, exigirlo al completar/liberar y actualizar solo si coincide con el actual; adaptar esos dos llamadores. El test mínimo debe adquirir A, vencer su plazo, adquirir B y verificar que liberar A no modifica B; las llamadas consecutivas del test actual (`supabase/tests/database/billing_purchase_lifecycle_test.sql:53–56`) no cubren recuperación de leases.
- Confianza: alta
- Cross-módulo: no

### [SQL2-05] El backfill de compras legacy no supera la validación posterior
- Severidad: media
- Categoría: datos
- Ubicación: `supabase/migrations/20260925022130_s3_credit_canonical_purchase_payment.sql:1154–1164`; `supabase/migrations/20260930034213_sprint_3_validate_integrity_constraints.sql:11`.
- Qué está mal: el importador inserta compras `EXPENSE/CARD_PURCHASE` sin `category_id`. La migración de proyección añade la obligación de categoría como `NOT VALID` (`20260928110000_credit_card_pull_projection.sql:296–307`), pero la migración del 30 de septiembre la valida sin reparar esas filas. No hay backfill intermedio que les asigne una clasificación; el cambio posterior del CHECK en SQL3 sigue exigiendo categoría para `CARD_PURCHASE` y se ejecuta después de este punto de fallo.
- Escenario concreto de fallo: una instalación anterior a S3 contiene una compra `CREDIT_PURCHASE/POSTED` válida. El backfill la importa y la actualización llega al `VALIDATE CONSTRAINT`, que falla por esa misma compra con categoría nula; las migraciones posteriores no pueden aplicarse. Es un problema de actualización con datos legacy, no del arranque de una base vacía; la documentación indica que los datos actuales de prueba se eliminarán antes de producción.
- Fix mínimo: establecer y ejecutar una reparación explícita de categorías legacy antes de validar, conservando la clasificación de origen cuando exista y requiriendo decisión de producto si falta. Añadir un test de actualización con una compra legacy previa a S3 hasta el paso de validación: el test asignado de backfill crea su compra por el RPC nuevo y con categoría (`supabase/tests/database/credit_sprint3_legacy_backfill.test.sql:28–34`), por lo que no prueba este camino.
- Confianza: alta
- Cross-módulo: no

### [SQL2-06] Referencia cross-módulo: fondos disponibles omiten movimientos editados
- Severidad: alta
- Categoría: datos
- Ubicación: definición final en `supabase/migrations/20261031163043_credit_purchase_terms_and_cycles.sql:1007–1015`; antecedente del alcance en `supabase/migrations/20260925022130_s3_credit_canonical_purchase_payment.sql:714–722`.
- Qué está mal: el cálculo del saldo para pagar tarjetas conserva únicamente transacciones `CONFIRMED`, mientras que una edición válida deja la transacción en `REVISED`. Se señala a SQL4 para su auditoría y corrección; la definición final está fuera de SQL2.
- Escenario concreto de fallo: cuenta con saldo inicial S/100 y gasto de S/40 corregido a S/60: el saldo contable es S/40, pero la comprobación de pago omite todos los apuntes del gasto editado y calcula S/100. Un pago de tarjeta de S/80 puede superar esa comprobación pese a exceder los fondos reales.
- Fix mínimo: SQL4 debe comprobar el filtro de estados contra el modelo de apuntes compensatorios y cubrir un pago posterior a editar un gasto; el caso mínimo anterior debe rechazarse por fondos insuficientes.
- Confianza: alta
- Cross-módulo: sí → SQL4

## Código de riesgo sin tests

- `delete_unused_card_v1` → primera compra concurrente con borrado, conservando tarjeta, vínculo y cuotas cuando la compra se confirma primero (SQL2-01).
- `movement_instrument_locked_v1` → edición entre cuentas con Premium y cinco bancos, y con Free dentro de cuatro grupos compartidos (SQL2-02).
- `apply_plan_quota_selection` → pérdida de respuesta seguida de archivo del recurso y reintento de la operación aceptada (SQL2-03). Los tests de selección consultados como referencia prueban reenvíos mientras los recursos siguen activos.
- Claim/complete/release de acknowledgement → recuperación del plazo y liberación tardía por el trabajador anterior (SQL2-04).
- Backfill S3 → compra legacy real sin categoría seguida de las migraciones de validación (SQL2-05); el test asignado cubre la importación de pagos, no la actualización completa de compras antiguas.
- `get_feature_access` → invocar el RPC como un usuario de prueba y comprobar que `offline_valid_until` es JSON null. `supabase/tests/database/entitlement_lease_test.sql:25–36` inspecciona el texto de la función; no verifica la respuesta y podría seguir pasando si el literal esperado quedara en una rama no ejecutada.

## Lo que no revisé

- Ningún archivo asignado quedó sin leer completo. Se revisaron las 19 migraciones y los nueve tests, incluidos datos de catálogo, backfills, grants, políticas y cuerpos de funciones.
- Los archivos fuera del alcance se leyeron únicamente para localizar definiciones finales y comprobar contratos, relaciones y llamadores. No se auditó integralmente SQL1, SQL3, SQL4, Kotlin ni Edge Functions.
- No se ejecutaron pgTAP, SQL, Supabase, Gradle, builds, dispositivos ni consultas de red. No se comprobaron despliegue remoto, planes de ejecución ni intercalaciones de concurrencia en una base real; los escenarios descritos proceden del análisis estático.
- No se cotejaron tasas bancarias ni marcas con fuentes externas: el catálogo conserva su corte histórico y esta tarea prohíbe acceso a red.

## Dudas

- Si se admite actualizar instalaciones legacy con compras anteriores a S3, falta definir qué categoría asignar a aquellas cuyo registro original no la conserva (SQL2-05). No debe inventarse una clasificación financiera para hacer pasar el CHECK; la eliminación prevista de datos de prueba tampoco demuestra que el camino de actualización funcione.
- No hay otras dudas de producto necesarias para los fixes señalados. Las reglas vigentes de cierre exclusivo, residuo de nuevas cuotas y cupo 5+5 se contrastaron con `docs/control-documental.md`; no se reportaron como bugs las variantes antiguas que las migraciones posteriores sustituyen.

# Verificación — Correcciones en el celular

Fecha: 21/10/2026. Rama: `main`. Seguimiento: [tasks.md](tasks.md). Antecedentes y evidencia de la sesión anterior: [HANDOFF.md](HANDOFF.md).

> Ampliación posterior del mismo día: las correcciones solicitadas mediante diez capturas también están **APPROVED** para su alcance local/UI. Véase «Ampliación visual posterior» al final. Los importes y datos del recorrido original documentados abajo son históricos; no describen las operaciones posteriores del propietario.

**Veredicto: APPROVED para las correcciones y recorridos expresamente verificados.** Las pruebas focalizadas y la compilación están verdes. Este veredicto no aprueba sincronización completa entre dispositivos: la descarga financiera está bloqueada y las metas tienen sincronización remota desactivada.

## Cambios entregados

- Navegación a las raíces sin restaurar historiales secundarios; salida correcta del alta de tarjeta; etiquetas inferiores visibles y acceso a Nueva meta desde Planificación.
- Comprobantes con etiquetas separadas, fecha DD/MM/AAAA, calendario/reloj nativos y colores semánticos para ingreso/gasto.
- Metas sin errores al abrir el formulario vacío; detalle, respaldo e importes reorganizados; reserva desde el banco real, excluyendo la billetera afiliada como respaldo duplicado.
- Afiliación de YAPE/PLIN sin exigir débito ni cuatro dígitos; banco, débito y billetera afiliada comparten cupo. El dashboard evita duplicar su saldo y mantiene el contexto de historial en Free.
- Recuperación de rechazos antiguos acotada al propietario y a errores conocidos; renovación de sesión; despacho de revisión/anulación y serialización explícita de defaults/nulls; alias/dígitos de crédito persistidos en el servidor.
- En el cierre se corrigió un caso adicional: una edición antigua con revisión cero ya no queda excluida permanentemente si su alta todavía estaba pendiente al ejecutar la recuperación inicial. Se vuelve a consultar su elegibilidad en cada lote y ejecución, conservando operación y esperando al predecesor sincronizado.

## Inspección final de sincronización

Lectura de una copia privada de Room con DB/WAL/SHM del Samsung; `PRAGMA quick_check`: `ok`. La copia permanece en `.gradle/celular-cierre/`, fuera de Git. No se modificaron filas locales ni se adelantaron cursores manualmente.

| Cola o checkpoint | Resultado observado |
| --- | --- |
| `instrument_sync_outbox` | 11 `SYNCED`: 5 altas de cuenta, 2 tarjetas, 2 cambios de presentación y 2 ediciones de crédito; cero pendientes |
| `movement_outbox` | 4 `SYNCED`: 2 registros y 2 anulaciones; cero pendientes |
| `movement_sync_checkpoints` | Sin filas; no hay descarga completa acreditada |
| `goal_command_outbox` / `goal_sync_checkpoints` | 3 `PENDING` / sin filas; sincronización remota desactivada |
| `profile_preference_outbox` | 1 `PENDING`; no se certifica sincronización de preferencias |
| `sync_outbox` de selección de cupos | 1 `CONFLICT`; no se certifica selección de cupos sincronizada |
| Categorías y comandos de deudas | Colas sin filas |

Se consultó nuevamente el servidor: hay una transacción YAPE y una PLIN, ambas `VOIDED`, con suma del ledger cero por canal.

### Bloqueo de descarga identificado

La función remota `pull_financial_changes_v1` lee el flujo general del propietario sin limitar tipos. Sus primeros eventos son `CATEGORY` (secuencias 1–16); el flujo también presenta un salto de 16 a 23. El consumidor financiero solo acepta `ACCOUNT`, `CARD`, `TRANSACTION` y el alias histórico `MOVEMENT`, y exige continuidad numérica. La primera categoría hace rechazar la página y conservar el checkpoint sin avanzar.

Esto contradice el [contrato de pull vigente](../../../specs/002-ep-cta-cuentas-tarjetas/contracts/sync-contract.md), que limita los tipos y ordena rechazar entidades desconocidas y confirmar páginas completas antes de avanzar. No se ocultaron eventos ni se relajó esa protección. Queda pendiente reconciliar el contrato remoto, la paginación y el consumidor antes de validar bootstrap u otro dispositivo. No se desplegaron otras épicas para intentar igualar todo el servidor.

## Revisión final del diff

Revisión directa, sin subagentes, de recuperación por propietario, revisiones, dependencias, scheduling y cancelación/reintentos. Se corrigió la recuperación diferida descrita arriba. En movimientos, la cancelación se propaga, Room conserva los comandos y la finalización condicionada al lease evita que una respuesta antigua complete un lease reemplazado; la identidad remota conserva la idempotencia. La revisión no constituye una nueva auditoría global.

`git diff --check` no detectó errores. Se corrigieron los signos `?` que habían reemplazado tildes en el checklist inicial y se validó UTF-8 y los destinos de enlaces de los documentos de cierre.

## Pruebas y compilación

No se repitió la suite legacy ni los recorridos ya confirmados. Los resultados anteriores se aceptaron según el mandato del propietario y se contrastaron con sus logs locales; no se suman ejecuciones repetidas como si fueran casos distintos.

| Evidencia local ignorada en `.gradle/` | Resultado y alcance |
| --- | --- |
| `celular-tests.log`, `celular-final-build.log`, `celular-history-tests.log` | Unitarias focalizadas de cupo, dashboard, cuentas, comprobantes, metas e historial; RegisterCardTest: 9 casos |
| `celular-flows-results.log` | 16 instrumentadas de afiliación, metas y navegación |
| `celular-recovery-ui-results.log` | 16 casos de Room/UI; incluye recuperación |
| `celular-dispatch-results.log` | 1 caso de conflicto de revisión; resuelve el fallo de despacho intermedio |
| `celular-last-recovery-results.log` | 2 casos: revisión cero y cancelación con lease |
| `celular-wire-unit-results.log` | Unitarias HTTP finales: build exitoso; null/default con `encodeDefaults=false` |
| `celular-final-dao-result.log` | 1 caso final de recuperación Room |
| `celular-final-installed-build.log` | Compilación previa debug/lab/androidTest exitosa |
| `celular-cierre-red-results.log` | Nueva regresión reproduce la edición omitida tras terminar el alta: verificación de actualización fallida |
| `celular-cierre-green-results.log` | 2 pruebas verdes tras corregir: edición recuperada en otra ejecución y conservación de revisión/identidad |
| `celular-cierre-green-build.log` | Nueva compilación debug/lab/androidTest: `BUILD SUCCESSFUL`, salida 0; APK debug reinstalada con `-r`, `Success` |

El Samsung S24+ conserva los datos y tiene instalada la APK con la corrección final. Las capturas claras/oscuras y de texto ampliado de la sesión anterior permanecen en `.gradle/celular-evidencia/`.

pgTAP heredados del traspaso: cupo/presentación/ledger 14/14; canal de billetera 8/8; archivo 2/2; revisión/anulación 42/42; edición de crédito 12/12. Se conservaron esos resultados sin repetirlos; los fixtures SQL se revirtieron con `ROLLBACK`. Los logs intermedios fallidos quedan como diagnóstico y no sustituyen sus resultados posteriores verdes.

## Migraciones desplegadas

El historial remoto se volvió a consultar durante el cierre. No se reaplicaron migraciones. Los timestamps MCP difieren de los nombres locales; antes de un futuro `db push` hay que reconciliar nombres, contenido e historial.

| Archivo local en `supabase/migrations/` | Versión registrada remotamente |
| --- | --- |
| `20261021191708_shared_bank_instrument_quota.sql` | `20261021192212` — `shared_bank_instrument_quota` |
| `20261021194835_normalize_card_archive_sync_operation.sql` | `20261021194930` — `normalize_card_archive_sync_operation` |
| `20261021200100_reconcile_movement_revision_contract.sql` | `20261021200236` — `reconcile_movement_revision_contract` |
| `20261021200500_sync_credit_card_edit_identity.sql` | `20261021200659` — `sync_credit_card_edit_identity` |
| `20261021201500_align_ledger_quota_and_appearance.sql` | `20261021201131` — `align_ledger_quota_and_appearance` |
| `20261002234503_s4_register_transaction_v2_hash.sql` | `20261021193938` — nombre con prefijo local |
| `20261013110000_wallet_payment_channel.sql` | `20261021193939` — nombre con prefijo local |
| `20261010045615_update_credit_card_terms.sql` | `20261021200234` — `update_credit_card_terms_v1` |

## Datos conservados y límites

Permanecen las dos cuentas BCP originales (S/0 y S/1,000), YAPE afiliada, BBVA con S/100, PLIN afiliada y VISA con línea S/1,000 y últimos dígitos 4242. Patrimonio local final del recorrido: **S/1,100**, sin duplicar billeteras ni sumar la línea de crédito. Alias VISA confirmado remoto en la sesión anterior, revisión 3.

Los movimientos temporales Transporte S/10 e ingreso PLIN S/1 permanecen anulados y auditables, con efecto neto cero. La meta existente conserva reserva final **S/0** tras reservar/liberar S/10. El comprobante original pendiente permanece; el borrador temporal fue descartado. Las cinco tarjetas remotas antiguas permanecen archivadas, sin borrar su historia.

La reserva/liberación se validó localmente; no se valida persistencia remota de metas. Tampoco se validaron segundo dispositivo, instalación nueva, reconciliación integral de épicas, sincronización de preferencias/cupos ni identidad visual píxel por píxel de toda la app. Los originales de referencias, worktrees, bases, sesiones y capturas privadas quedan fuera de los commits. No se solicitó ni se ejecutó push.

## Ampliación visual posterior — 21/10/2026

**Veredicto: APPROVED.** Implementación directa en `main`, sin subagentes. Las diez capturas del propietario son la referencia de este ajuste acotado sobre los flujos existentes.

- Comprobante ya confirmado: aviso amarillo «Posible movimiento duplicado», con referencia, importe y fecha cuando existen y cierre accesible. Pendientes repetidos y descartados tienen explicaciones distintas. No se confirma ni se duplica ningún movimiento al importar.
- Revisión: una sola instrucción breve; se suprime el aviso redundante de OCR terminado y, si hay advertencia, no se añade otra caja de instrucciones.
- Dashboard: se retira el acceso redundante «Metas y reservas» y «Mejorar a PRO» usa los tokens amarillos de advertencia con texto contrastado.
- Aportes: teclado decimal, rechazo de letras y formulario desplazable con espacio para el teclado. Se conservan validación de saldo y cantidades en unidades menores.
- Planificación: capacidad/contador simplificados, sin mensajes rutinarios de sincronización. Se conservan mensajes de conflictos/errores que necesitan acción. Archivadas al final, contraídas por defecto y con flecha para abrir/cerrar.
- Metas: selector de ocho iconos vectoriales (ahorro, tecnología, emergencias, viajes, hogar, vehículo, estudios y celular), disponible al crear/editar. La selección llega al dominio, Room mediante el flujo existente y el payload del comando; tarjetas y opciones usan esa selección. Metas antiguas sin icono usan ahorro como alternativa.

Validación focalizada:

| Evidencia local ignorada | Resultado |
| --- | --- |
| `.gradle/celular-polish-red.log` | Dos regresiones reproducidas: información insuficiente de duplicado y confusión entre descartado/registrado |
| `.gradle/celular-polish-unit.log` y XML de `testDebugUnitTest` | 12 pruebas: ReceiptFlowViewModelTest 6, OfflineFirstGoalRepositoryTest 5, GoalListViewModelTest 1; cero fallos/errores |
| `.gradle/celular-polish-ui-results.log` | 23 instrumentadas: CellularCorrectionsTest 11 y GoalManagementFlowTest 12; `OK (23 tests)` |
| `.gradle/celular-polish-final-ui-results.log` | 3 casos afectados por los últimos ajustes de tema/texto/accesibilidad; `OK (3 tests)`; son repeticiones de casos del bloque anterior |
| `.gradle/celular-polish-final-build.log` | debug/lab/androidTest: `BUILD SUCCESSFUL`, salida 0; instalación final del paquete principal con `-r`, `Success` |
| `.gradle/celular-polish-evidencia/` | Capturas privadas de aviso, formulario, aportes y metas; revisión visual, fuera de Git |

No se repitió la suite legacy. Se validó UTF-8/enlaces de documentación y whitespace del diff. Esta ampliación no cambia esquema ni despliega migraciones, por lo que no requiere repetir pgTAP. Las pruebas instrumentadas usan el paquete lab y fixtures; no restauran saldos, anulan operaciones ni reservan dinero en la cuenta del propietario.

Después de instalar, se abrió la aplicación principal: dashboard sin el acceso repetido y Planificación sin mensajes técnicos, con Grafica visible y archivadas contraídas. Al enfocar Importe en el aporte real se confirmó el teclado numérico con separador decimal. Se volvió atrás sin introducir ni guardar una reserva. La captura de esa comprobación contiene información privada y permanece ignorada.

La sincronización remota de metas continúa desactivada; retirar el texto técnico no implica habilitarla ni certificarla. Siguen abiertos el bloqueo de pull, preferencias y selección de cupos documentados arriba. La APK principal se actualiza conservando datos. No se hace push ni se incluyen worktrees, bases, imágenes de referencia o capturas privadas.

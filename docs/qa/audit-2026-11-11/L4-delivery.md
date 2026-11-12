# L4 — revisiones y orden de comandos

## Alcance y archivos por hallazgo

Base: rama `Lume03/audit-fixes`, commits L3 `bf163645` y L3b `98af521d`. Cambios locales sin commit, push, Spec Kit, adb ni acceso a Supabase. No se escribieron ni ejecutaron pruebas nuevas. `CategoryConflictScreen` permanece intacta para L12.

Las rutas Kotlin siguientes son relativas a `app/src/main/java/com/kipu/app/feature/`:

| Hallazgo | Archivos | Resultado |
| --- | --- | --- |
| ACC5-02 | `accounts/data/sync/SyncInstrumentCommandsWorker.kt` | Antes del primer envío de apariencia o condiciones de crédito con predecesor SYNCED, persiste la revisión confirmada del agregado y el payload/hash nuevo. |
| MOV2-03 | `movements/data/sync/MovementHydration.kt`, `movements/data/local/MovementDao.kt` | Persiste la primera secuencia omitida en el recibo local; CONFLICT rebobina el checkpoint transaccionalmente y permite adoptar la versión remota aunque la revisión optimista local fuera mayor. |
| CAT1-01, CAT1-07 | `categories/data/OfflineFirstCategoriesRepository.kt`, `categories/data/sync/SyncCategoryCommandsWorker.kt` | último escritor en presentación; no se inventan conflictos locales ni UUID remotos. El último acuse adopta la revisión real del servidor. |
| CAT1-03 | `categories/data/sync/SyncCategoryCommandsWorker.kt` | Acuse y finalización se confirman juntos, comprobando el lease. Mientras queden intenciones del agregado, conserva la proyección y revisión optimistas con estado PENDING; solo el último acuse publica SYNCED. |
| CAT2-02 | `categories/presentation/categories/CategoryPresentationViewModel.kt` | Colecta presentaciones, carga nombre/icono/color y usa su revisión; mantiene el borrador editado ante emisiones posteriores. |
| GOA1-01 | `goals/data/local/GoalOutboxDao.kt` | FIFO por fecha/rowid para cualquier intersección entre meta principal, destino y reemplazo de ambos comandos, incluyendo backoff y leases. |
| GOA1-03 | `goals/data/local/GoalLocalDataSource.kt`, `goals/data/sync/GoalChangeFeedApplier.kt` | Propaga el resultado a destino/reemplazo; conserva PENDING ante operaciones posteriores. Reconoce el contrato vigente de reasignación sin lista goals mediante destination_expected_revision + 1. |
| UI3-03, UI3-04 | `notifications/data/OfflineFirstNotificationsRepository.kt` | Borra el comando enviado solo si la fila aún coincide, dentro de transacción; consulta pendientes y filtra el pull dentro de su transacción de escritura. |
| Snapshots de deuda L3b | `debts/data/local/DebtLocalDataSource.kt`, `debts/data/local/DebtOutboxDao.kt`, `debts/data/sync/DebtChangeFeedApplier.kt` | discardLocal aplica los REMOTE_SNAPSHOT pendientes con validación de propietario, identidad y referencias; adopta las revisiones remotas aunque sean menores. Padres/cuotas preceden a eventos y se elige la última revisión por entidad; todo confirma en una transacción. Descarta las intenciones locales de esa deuda para que sucesores obsoletos no vuelvan a sobrescribirla. |

Adaptaciones de constructores existentes, sin cambiar aserciones:

- `app/src/test/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerRemoteErrorTest.kt`.
- `app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt`.

## Encadenamiento de cuentas y tarjetas

La reclamación existente entrega al worker el comando con su contador anterior al intento. Si es el primer intento y el predecesor ya está SYNCED, se lee remoteRevision de la cuenta o tarjeta que actualizó ese acuse y se guarda en el payload antes del POST. En condiciones de crédito también se recalcula request_hash con el patrón canónico existente. Los reintentos conservan ese payload: no se rebasa un intento enviado ni se oculta un conflicto real entre dispositivos. La ruta existente de identidad de débito sigue vigente. TEA personal no tiene expected_revision en su contrato wire: queda serializada por el FIFO y actualiza remoteRevision tras el acuse; no se inventó un campo que el servidor no consume.

## Recuperación de movimientos

Al omitir un cambio por una intención pendiente, se guarda el mónimo skipped_remote_sequence en responsePayload del recibo local de cada intención afectada, sin alterar el comando ni su hash remoto. Al finalizar CONFLICT, el DAO verifica el lease y rebobina el checkpoint hasta secuencia − 1 en la misma transacción. Si un conflicto legacy no tiene marcador, usa cero como fallback seguro. El pull normal del worker vuelve a recorrerlo y reconcilia ledger y saldos; acepta una revisión remota inferior a la optimista cuando la transacción está CONFLICT. La propuesta local sigue conservada para la resolución existente.

## Migración preparada, no aplicada

Archivo: `supabase/migrations/20261111140000_revision_order_fixes.sql`. Se localizaron todos los nombres en todas las migraciones antes de reemplazarlos:

| Función | Definición final de partida | Corrección |
| --- | --- | --- |
| `update_category_presentation_v1(jsonb)` | `20260922000000_ep_cco_baseline.sql`; endurecimiento posterior de grants/search_path en `20260930025207_sprint_3_db_hardening.sql` | FOR UPDATE de presentación y padre estable para primeras inserciones concurrentes; recibo/hash calculado por servidor por propietario/operation_id, con lock de idempotencia; último escritor siempre aplica y devuelve revisión. |
| `resolve_category_conflict_v1(jsonb)` | `20260923010000_fix_category_conflict_resolution.sql` | Conserva compatibilidad LOCAL/REMOTE, aplica realmente la versión elegida mediante el RPC idempotente y después resuelve el conflicto. |
| `delete_unused_card_v1(jsonb)` | `20260928110000_credit_card_pull_projection.sql` | Bloquea la fila de tarjeta antes de consultar ambos historiales, como la compra vigente de `20261101140000_credit_card_principal_quota_archive.sql`; conserva tombstone en sync_changes. |
| `set_instrument_archived_v1(jsonb)` | `20261101140000_credit_card_principal_quota_archive.sql` | Consulta el recibo antes de validar o escribir; lock por propietario/operación, hash completo y recibo atómico impiden que archivo X → reactivación Y → reintento X archive otra vez. Conserva el control de deuda pendiente de tarjetas y los demás controles del cuerpo vigente. |

Un bloque de datos resuelve los conflictos OPEN de presentación existentes en orden cronológico: aplica la propuesta más reciente si no hay una presentación posteriormente actualizada; conserva las escrituras posteriores. Los cuatro reemplazos usan CREATE OR REPLACE, SECURITY DEFINER, search_path vacío y EXECUTE solo para authenticated, con revocación a PUBLIC/anon. No se alteraron otros cuerpos ni parches posteriores.

## Verificación

`.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL en 16 s, exit 0, sobre el arbol final. `git diff --check`: limpio. No se ejecutaron tests ni SQL; compilar no valida comportamiento concurrente ni despliegue remoto.

## Pendientes para L5

Conectar retry/discard a la UI de «No sincronizado», conservar la evidencia de fallos y completar las compensaciones financieras locales ya diferidas por L3/L3b. Aplicar snapshots de deuda no sustituye la compensación de altas o movimientos financieros rechazados que no están representados en dichos snapshots. Mantener propuestas de movimientos accesibles en el flujo de resolución. La retirada de CategoryConflictScreen corresponde a L12.

## Casos a probar al final

1. Cuenta en revisión 5: dos cambios offline de alias/apariencia terminan en 6 y 7; condiciones de crédito equivalentes, identidad de débito y TEA intercaladas. Reintento tras perder respuesta conserva payload/hash, y cambio real de otro dispositivo sigue en conflicto.
2. Pull omite una revisión mientras una edición está en backoff; reiniciar proceso, recibir CONFLICT y comprobar nuevo pull, revisión, importe, ledger y saldos remotos. Varias secuencias omitidas usan el mónimo; HTTP 409 y conflictos legacy sin marcador también recuperan.
3. Primera personalización de categoría sin fila remota, segunda edición tras acuse y varias offline; confirmar revisión real final. Dos dispositivos escriben presentación: gana el último sin OPEN; misma operación/payload devuelve recibo, operación reutilizada con campos distintos falla.
4. Alias y preferencias: crear → editar → eliminar offline, intercalar otra edición mientras llega el acuse, backoff y lease vencido; PENDING hasta el último acuse, sin retroceder revisión ni hidratar una proyección prematuramente confirmada.
5. Editor de presentación usa los valores/revisión del Flow y conserva un borrador ante emisiones de sincronización.
6. Reasignación A→B en backoff bloquea edición de B, B→C y reemplazos que compartan cualquiera de las metas; otras metas avanzan. Empates de fecha respetan rowid. éxito/conflicto/rechazo afectan ambos destinos; acuse antiguo no pisa una edición posterior.
7. Notificaciones: envío de leído suspendido, descarte local y acuse anterior; el descarte sigue en cola. Pull suspendido, cambio local, escritura del pull; conserva proyección local pendiente. Reintento después de acuse perdido.
8. Deuda con varios snapshots de revisiones inferiores a la optimista, cuotas y eventos: discardLocal aplica la última versión por entidad, conserva aislamiento de propietario y confirma atómicamente; snapshot inválido revierte también el descarte. Repetir descarte y verificar que no reenvía snapshots ni sucesores obsoletos.
9. SQL con dos sesiones: primera personalización concurrente, presentación concurrente con el mismo operation_id y compra abierta frente a borrado; al confirmar compra el borrado devuelve CARD_HAS_HISTORY. OPEN anteriores se resuelven preservando la escritura más reciente; compatibilidad LOCAL/REMOTE aplica cada versión elegida.
10. Archivo X aceptado con respuesta perdida → reactivación Y → reintento X: mantiene activo y devuelve recibo; payload distinto con X falla. Mantener controles de deuda pendiente y grants de las cuatro RPC.

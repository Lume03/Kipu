# L5 — rechazos locales y «No sincronizado»

## Alcance

Trabajo local en `Lume03/audit-fixes`, sobre L4 `895ab2c3`. Sin commits, push, servidor, migraciones, Supabase, adb ni Spec Kit. No se escribieron ni ejecutaron tests; no fue necesario adaptar tests para compilar.

## Archivos por hallazgo

Las rutas de módulos son relativas a `app/src/main/java/com/kipu/app/feature/`.

| Hallazgo | Archivos | Cambio |
| --- | --- | --- |
| ACC5-05 | `accounts/data/sync/SyncInstrumentCommandsWorker.kt`, `accounts/data/OfflineFirstFinancialInstrumentsRepository.kt`, `accounts/data/local/AccountDao.kt`, `movements/data/local/MovementLocalDataSource.kt` | El rechazo terminal del alta compensa OPENING y movimientos dependientes dentro de la transacción del resultado; archiva la cuenta, pone saldo inicial en cero y bloquea comandos dependientes. Descarte no reactiva sucesores de un alta rechazada. |
| ACC5-06 | `accounts/data/sync/SyncInstrumentCommandsWorker.kt`, `accounts/data/local/FinancialMovementDao.kt`, `accounts/presentation/detail/AccountDetailScreen.kt`, `core/finance/domain/model/Movement.kt` (desde la raíz Kotlin) | La misma transacción que revierte la compra marca también `financial_movements` como REJECTED. El historial de tarjeta conserva el registro con etiqueta y lo excluye de la deuda POSTED. |
| ACC5-09 | `accounts/data/OfflineFirstFinancialInstrumentsRepository.kt`, `accounts/data/sync/SyncInstrumentCommandsWorker.kt`, `accounts/data/local/CardDao.kt` | Registro y ediciones nunca intentados se cancelan localmente; no se crea un borrado remoto y se limpia el pasivo sin uso. Si hubo intento, se conservan tarjeta/pasivo archivados hasta el acuse del borrado remoto. Se comprueba ausencia de historial en ambas proyecciones. |
| GOA1-05 | `goals/data/local/GoalLocalDataSource.kt`, `goals/data/local/GoalDao.kt`, `goals/data/sync/GoalConflictResolver.kt`, `goals/data/sync/GoalChangeFeedApplier.kt` | Rechazo/conflicto neutraliza traslados, bloquea sucesores locales y excluye eventos rechazados/bloqueados/descartados de reservas efectivas. Conserva eventos, recibos y revisiones. Usar servidor recupera snapshots completos de las metas relacionadas y los aplica junto al descarte y compensación, sin fusión de campos. |
| GOA1-02 | `goals/domain/GoalRepository.kt`, `goals/data/OfflineFirstGoalRepository.kt`, `goals/data/local/GoalLocalDataSource.kt`, `goals/presentation/GoalDetailViewModel.kt` | `deleteWithReserves` realiza todas las liberaciones y el archivo bajo una sola transacción Room; un resultado rechazado/conflictivo revierte también los pasos previos. El ViewModel deja de liberar cuenta por cuenta. El reemplazo se aplica únicamente en la transición que lo requiere. |
| GOA1-06 | `goals/presentation/GoalContributionSheet.kt`, `goals/presentation/GoalDetailScreen.kt` | Selección y conteo de reemplazos consideran todas las metas activas, incluso en otra moneda. El filtro de moneda se conserva para reasignar reservas y seleccionar cuentas compatibles. |
| Altas de deuda rechazadas | `debts/data/sync/DebtSyncScheduler.kt`, `debts/data/local/DebtDao.kt` | Compensa operaciones financieras de la apertura rechazada y sus dependientes; el saldo pendiente resumido es cero mientras se revisa el alta. |

Infraestructura común nueva, desde la raíz Kotlin:

- `core/sync/LocalRejectionCompensator.kt`: revisión de compensación y contrapartidas del neto por cuenta/moneda; reconstrucción de proyecciones. Se ejecuta dentro de la transacción del llamador y evita repetir una compensación ya identificada.
- `core/sync/SyncRecoveryRepository.kt`: observación de evidencia local de outbox, clasificación por entidad y recuperación mediante los puertos existentes; alcance por propietario, comprobación de estado vigente y trabajo de lectura fuera del hilo principal. Coordina la eliminación local de altas rechazadas y conserva evidencia referenciada.
- `ui/component/SyncRecoveryViewModel.kt`: sesión, colección, acciones, bloqueo durante la operación y errores de recuperación.

## Componente y pantallas

`ui/component/UnsyncedNotice.kt` añade un único patrón visual sobre `PlanningNotice`, después de revisar también `KipuStates.kt` y `docs/DESIGN.md`. Usa tokens semánticos del tema, texto explícito y botones de al menos 48 dp; las acciones se apilan para permitir texto largo. La variante compacta solo muestra «No sincronizado». La variante de detalle ofrece «Reintentar» exclusivamente para un estado reintentable y, para estados terminales, «Usar la versión del servidor» o «Eliminar» si fue un alta rechazada. Se deshabilitan acciones mientras se procesa y se informa si la recuperación falla.

La UI enlaza con `SyncRecoveryViewModel` y este con el repositorio coordinador; los composables no consultan DAO ni hacen compensaciones. Los repositorios/schedulers vuelven a programar sync al recuperar. No se ofrece reenvío de un conflicto como si fuera un fallo transitorio.

| Superficie | Integración |
| --- | --- |
| Cuenta/tarjeta | Aviso en `accounts/presentation/detail/AccountDetailScreen.kt`; etiquetas en `accounts/presentation/dashboard/DashboardSections.kt`; sección separada de altas rechazadas en `DashboardScreen.kt`, que abre su detalle. |
| Movimiento | Aviso en `movements/presentation/MovementDetailSheet.kt`; etiqueta en `MovementHistoryScreen.kt`. Se retira la acción de descarte duplicada del detalle. |
| Deuda | Aviso en `debts/presentation/DebtDetailScreen.kt`; etiqueta en `DebtListScreen.kt`. |
| Meta | Aviso en `goals/presentation/GoalDetailScreen.kt`; etiqueta en `GoalListScreen.kt`. Se retiran las acciones de resolución duplicadas en la lista y el reintento de conflictos en el diálogo antiguo. |
| Presupuesto | Aviso en `budgets/presentation/BudgetDetailScreen.kt`; etiqueta en `BudgetListScreen.kt`, sin mostrar códigos técnicos de outbox en las tarjetas. |
| Recurrencia | Etiqueta en `recurrences/presentation/RecurrenceLifecycleScreen.kt`; «Ver detalle» abre la superficie con el aviso y las acciones comunes. |
| Categoría | Etiquetas de raíz y subcategoría en `categories/presentation/categories/CategoriesScreen.kt`; aviso al editar en `CategoryFormScreen.kt`, pasando la identidad de la categoría. `CategoryConflictScreen` permanece fuera del lote, como se acordó para L12. |
| Perfil/preferencias | Aviso en `settings/presentation/ProfileSettingsScreen.kt`. |

## Cuenta rechazada: saldos, listas y eliminación

El rechazo definitivo de CREATE_ACCOUNT y sus efectos locales confirman juntos. Se conservan transacciones y revisiones para revisión, se añaden contrapartidas a sus efectos netos y se reconstruyen saldos de todas las cuentas afectadas, incluidas las dos puntas de transferencias. OPENING deja de estar POSTED; la cuenta se archiva y su saldo inicial queda en cero. Por ello no entra en saldos normales ni en respaldo disponible de metas.

Las consultas normales y de archivados excluyen altas rechazadas; el dashboard muestra su nombre en «Requieren revisión», con la etiqueta pequeña. Su detalle conserva «No sincronizado» y «Eliminar». La eliminación descarta el alta y sus intenciones sucesoras. Si no hay referencias de metas ni historial financiero dependiente, elimina la cuenta y su OPENING local, conservando ledger/revisiones y desvinculando referencias vivas anulables. Si hay referencias o historial dependiente, conserva un tombstone interno oculto: borrarlo físicamente haría perder historial por CASCADE o rompería las FK. El tombstone tampoco aparece en la consulta de detalle una vez descartado. Los movimientos dependientes no se borran y siguen fuera de saldos.

El mismo principio conserva evidencia referenciada al eliminar metas/deudas/recurrencias rechazadas. Presupuestos y categorías sin dependencias se retiran con sus proyecciones locales. Un fallo de integridad revierte la operación y muestra error, sin ocultar el problema.

## Verificación

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, ejecución final en 39 s, exit 0.
- `git diff --check`: limpio.
- Detector mecánico de diseño sobre el componente nuevo y detalle de instrumento: `[]`; no sustituye validación visual en Android.
- Se corrigieron errores de tipos encontrados al compilar y la corrupción de codificación de una edición interrumpida; los archivos se guardan en UTF-8. Persisten avisos existentes de Room y de APIs deprecadas. No se ejecutó ninguna prueba de comportamiento ni se afirma validación en teléfono.

## Casos a probar al final

1. Crear cuenta con OPENING cero/no cero; rechazo lógico, HTTP permanente, conflicto y payload ilegible: transacción atómica, saldo cero, ausencia en listas normales/archivadas y aparición en revisión. Repetir resultado, reiniciar proceso y comprobar que no hay doble contrapartida.
2. Cuenta rechazada con gasto, ingreso, transferencia, corrección de apertura y referencias de metas: preservar historial; verificar ambos saldos de una transferencia, reservas, comandos BLOCKED y eliminación sin cascadas sobre movimientos dependientes. Contrastar aislamiento de usuarios y cambio de sesión durante la acción.
3. Compra de tarjeta rechazada: transacción, ledger, cuotas y `financial_movements` convergen en la misma transacción; historial visible con etiqueta, importe fuera de deuda y ausencia de duplicados tras reinicio/repetición.
4. Registrar y borrar tarjeta sin envío: ninguna llamada remota, registro/ediciones descartados, tarjeta y pasivo retirados. Repetir con registro IN_FLIGHT, respuesta perdida, backoff y registro confirmado: conservar proyecciones hasta completar el protocolo. Impedir borrado si cualquiera de los historiales tiene uso.
5. Meta: reserva sin traslado y reserva con traslado rechazadas; comprobar reservas por cuenta, disponible, progreso y saldos origen/destino. Bloquear sucesores y conservar eventos. Descartar con snapshots remotos de revisiones inferiores a la optimista, varias metas relacionadas y snapshot inválido: todo o nada.
6. Eliminar meta respaldada por dos o más cuentas: segunda liberación rechazada/conflictiva o archivo rechazado revierte también la primera. Éxito libera todas y archiva. Meta completada Free: reemplazo aplicado una sola vez; no se impone en liberaciones posteriores.
7. Free con metas PEN y USD: conteo global y selección de cualquiera como reemplazo al retirar/eliminar o reabrir una meta completada. Reasignar reservas sigue exigiendo moneda compatible.
8. En cada módulo: conflicto, fallo permanente, bloqueo, error reintentable y espera de autenticación; no mostrar «Reintentar» para terminales. Reintentar/descarte reprograman sync, actualizan el aviso y no repiten comandos resueltos. Probar commandId obsoleto, propietario incorrecto y cambio de estado entre render y clic.
9. Alta rechazada de movimiento, tarjeta, deuda, meta, presupuesto, recurrencia y categoría: «Eliminar» retira su proyección operativa; verificar saldos, historial referenciado, reinicio e idempotencia. Deuda en modo financiero frente a histórica, con cuotas y operaciones dependientes.
10. **Teléfono, claro/oscuro:** aviso en todos los detalles, etiquetas compactas en todas las listas, categorías raíz/subcategoría, Ajustes y detalle de recurrencia. Probar ancho estrecho, fuente ampliada, TalkBack, orden de foco, contraste de tokens, área táctil de 48 dp, texto de acciones sin corte, estado ocupado, error de recuperación y regreso desde un elemento eliminado. Las previews omiten la conexión Hilt de recuperación.

## Pendientes y límites

Pendiente ejecutar los casos anteriores, en especial transacciones/concurrencia y revisión visual en teléfono; este lote únicamente compila. La atomicidad de la eliminación multicuenta de metas corresponde a la transacción local: no introduce una RPC de eliminación remota compuesta. Los módulos sin snapshot remoto persistido conservan el contrato existente de descarte y posterior hidratación; no se inventó una versión remota ni se añadió fusión de campos. La evidencia interna referenciada puede permanecer como tombstone tras «Eliminar», aunque la entidad operativa ya no se muestre. No se validó ni modificó el estado desplegado.

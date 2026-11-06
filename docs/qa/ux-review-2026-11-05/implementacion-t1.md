# A16-UX-002 — correcciones de importe

Fecha: 2026-11-06. Rama: `ux/mejoras-2026-11-06`. Solo A16 `RFGL70J9E2T`.

## Causa comprobada

Room del A16 conservaba tres comandos `REVISE_TRANSACTION`, todos `FAILED_PERMANENT` con `REQUEST_HASH_MISMATCH`. Conservaba tres revisiones `VOID`, procedencia `RECONCILIATION`, con ese mismo motivo. No era una anulación solicitada por el usuario.

El recorrido es:

1. `MovementEditorViewModel.onSave` crea una propuesta con `operationKind=null` y fecha en milisegundos.
2. `ReviseTransaction` delega en `OfflineFirstMovementRepository.revise`, que calcula el hash con `MovementRevisionRequestHasher` antes del commit.
3. `MovementLocalDataSource.commitRevisionAtomic` registra snapshot, efectos compensatorios, saldo, recibo y outbox en la misma transacción de Room. `MovementOutboxPayloadFactory.buildRevise` envía `STANDARD` por defecto y fecha ISO UTC.
4. El repositorio agenda `SyncMovementsWorker`; este reclama el comando con lease, comprueba dependencias y llama `MovementApi.reviseOrVoidTransaction` → `revise_transaction_v1`.
5. `internal.apply_movement_revision_v1` valida el hash antes de comprobar `expected_revision` o crear revisiones. El hash remoto usa `STANDARD` y fecha ISO; la app usaba `null` y fecha numérica. La definición desplegada se verificó mediante SELECT; coincide con las migraciones del repositorio.
6. El rechazo lleva a `MovementRejectionResolver.rejectClaimed`: restaura snapshot anterior, neutraliza los efectos locales, recalcula saldo, marca recibo rechazado y outbox fallido. Añade evidencia compensatoria `VOID / RECONCILIATION` y deja la transacción oficial como `SYNCED`.
7. `MovementHydration` protege las propuestas pendientes frente a páginas antiguas, concilia el ledger por identidades y actualiza desde el servidor después de la aceptación. Lista y detalle leen Room; el detalle consulta también auditoría. La restauración observada ocurría en el rechazo, antes de que la hidratación pudiera aceptar una revisión.

Por tanto: el comando sí sale, el servidor lo rechaza por hash, no se alcanza la comprobación de revisión esperada, y la compensación local explica tanto el importe anterior como la anulación aparente. No se modificó Supabase.

## Cambios

- El hash de revisión usa el mismo tipo estándar y fecha ISO UTC que el payload y la función remota. El contrato de anulación no cambia.
- Confirmación unificada: «Corrección pendiente de sincronizar», también estando online.
- Detalle pendiente visible; rechazo explícito «No se pudo aplicar la corrección. Se mantiene S/ X.XX.» y estado «Corrección no aplicada».
- La auditoría distingue rechazo compensatorio de anulación solicitada, conservando las filas originales inmutables.
- «Revisar cambios» recupera el snapshot completo retenido por la compensación y compara el borrador con el head oficial actual. Guardar genera un comando nuevo. No hay cambios en Room (versión 40).
- Una revisión posterior, también recibida de otro dispositivo, deja de ofrecer un borrador rechazado obsoleto.
- El botón admite dos líneas sin altura fija; colores e iconos proceden de los tokens y librería existentes.

## Verificación

- Prueba de regresión del hash: falló antes del arreglo y pasó después. Vector consultado con SELECT a `internal.movement_revision_hash_v1`, datos sintéticos: `c2f268972b38dcd068708668bf49743926d372507bd3ba20dea1e87c369727ed`.
- Resultado final: 239 pruebas, 32 suites, cero fallos y errores. `BUILD SUCCESSFUL`.
- SELECT adicional del ledger remoto: neto ingreso 245 y gasto -141, sin duplicación de los importes.
- `:app:compileDebugKotlin`, `:app:testDebugUnitTest --tests '*feature.movements*'` y `:app:installDebug`, con `ANDROID_SERIAL=RFGL70J9E2T`.
- Pruebas de auditoría: rechazo con borrador, anulación y registro rechazado, reintento posterior con número local menor, y head oficial nuevo de otro dispositivo. Prueba de ViewModel: restaura importe/nota y guarda contra la revisión oficial vigente.
- A16 online: «UX-ingreso» 2.40 → borrador recuperado 2.45 → sincronizado; SELECT: `amount_minor=245`, `revision=2`, `updated_at>created_at`, dos revisiones remotas. Tras force-stop/reapertura: lista y detalle 2.45, efectivo 208.80 → 208.85, total 580.51 → 580.56.
- A16 offline: «UX-gasto-tiempo» 1.20 → borrador recuperado 1.41; lista y detalle retienen 1.41 pendiente. Al restaurar conectividad: Room `SYNCED`, SELECT `amount_minor=141`, `revision=2`; efectivo 208.64, total 580.35. Wifi y datos móviles restaurados a su estado previo, habilitados.
- Pruebas existentes de movimientos cubren anulación, idempotencia, conflictos y efectos financieros. No se reprodujo conflicto real entre dos teléfonos; el S24 no se tocó. Sin tareas `connected*AndroidTest`.
- `git diff --check` limpio; detector Impeccable `[]`. Revisión visual de capturas del A16.

Evidencia local: `C:\Users\Alume\AppData\Roaming\orca\chat-scratch\228b4f16ea9c9b5706771b404f0e9eb4\ux-impl\evidencia\t1\`. `root-cause.json` contiene conteos sin información personal. Capturas 01–09 muestran rechazo, borrador recuperado, sincronización, reapertura, saldo y pendiente offline. La captura 01 precede al ajuste final de altura del botón.

No se identificaron contradicciones con un spec; no se modificaron `specs/`, `.specify/` ni `AGENTS.md`. No se reenvían automáticamente correcciones históricas rechazadas: el usuario revisa el borrador y decide guardarlo.

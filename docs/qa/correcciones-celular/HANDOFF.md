# Traspaso: correcciones en el celular

Estado comprobado: 21/10/2026, después del recorrido final. El agente anterior deja el trabajo sin commits para que el siguiente cierre la entrega.

> Cierre posterior del 21/10/2026: consultar [verification-report.md](verification-report.md) y [tasks.md](tasks.md). Se inspeccionaron las colas (11 instrumentos y 4 movimientos SYNCED), se identificó el bloqueo del pull por tipos/secuencias incompatibles, se corrigió una recuperación de edición diferida con 2 pruebas focalizadas verdes y se reinstaló la APK actualizada. Los apartados siguientes conservan el estado histórico recibido; la entrega final registra los commits. La sincronización completa y las metas remotas siguen sin aprobación.

## Reglas del propietario

- Repositorio: `C:\Users\Alume\orca\Kipu`; rama actual `main`.
- Trabajar directamente aquí. Sin Spec Kit, nuevas ramas ni worktrees.
- Mantener el seguimiento en `docs/qa/correcciones-celular/tasks.md`.
- Referencias: `docs/referencias/Comprobantes/`, `docs/referencias/metas/` y `docs/DESIGN.md`.
- Puede crear, editar, anular y eliminar datos de prueba en la cuenta real. Alias cortos: YAPE, PLIN, BBVA, VISA; no nombres QA ni códigos.
- Si usa subagentes, el único modelo autorizado es GPT 6 LUNA en razonamiento Alto. No estaba disponible en las herramientas del agente anterior; no se usaron otros modelos.
- No tocar ni incorporar `worktrees/`. Las carpetas de imágenes de referencia ya estaban sin seguimiento antes del trabajo.
- No incluir bases SQLite, sesiones, capturas privadas ni `.gradle/` en Git.

## Implementación ya realizada

### Navegación y comprobantes

- `MainActivity.kt`, nuevo `navigation/RootNavigation.kt`: navegar a raíces sin restaurar historiales secundarios encima de las pestañas. Reutilizar Movimientos desde comprobantes/cuentas.
- `KipuNavigationBar.kt`: etiquetas visibles, selección alineada, icono de objetivo para Planificación, adaptación a texto ampliado; + abre Nueva meta.
- `AccountsNavigation.kt`: al terminar el alta de tarjeta, retirar el formulario de creación antes de abrir el detalle; evita volver al formulario ya guardado.
- `ReceiptsNavigation.kt`: etiquetas y ejemplos separados, Fecha/Hora alineadas, calendario y reloj nativos, fecha DD/MM/AAAA, selección gasto roja e ingreso verde mediante tokens.

### Metas

- `GoalFormScreen.kt`: no mostrar errores al abrir vacío; validar tras editar.
- `GoalDetailScreen.kt`, `GoalBackingStatus.kt`, `GoalListScreen.kt`: tarjetas, espaciado, importes, acciones y progreso; quitar punto final engañoso de las barras.
- `GoalDao.kt`: excluir billeteras afiliadas como respaldo independiente; se reserva desde el banco real.
- Reserva real S/10 en BBVA y liberación probadas. El patrimonio no cambia y la reserva final es cero.

### Cuentas, billeteras e historial

- `UnifiedInstrumentFormScreen.kt`, repositorio financiero: afiliación sin exigir débito ni últimos cuatro dígitos. Conservar compatibilidad de banco/moneda.
- Nuevo `InstrumentQuotaGroups.kt`: banco, tarjeta de débito y billetera afiliada comparten identidad de cupo; crédito independiente ocupa un cupo.
- Repositorio, `MovementLocalDataSource.kt` y `Account.kt`: aplicar la misma agrupación para conteos/bloqueos.
- `DashboardScreen.kt`: no sumar otra vez el saldo de la billetera afiliada; indicar «Saldo compartido».
- `MovementHistoryQuery.kt`, SQL builder y ViewModel: contexto de cuenta/canal separado de filtros Premium. En Free, YAPE conserva su historial propio.

### Sincronización corregida durante las pruebas reales

- Recuperación puntual por propietario de rechazos de contratos antiguos, conservando identidades de operaciones.
- `FinancialInstrumentsApi.kt` y `MovementApi.kt`: renovar sesión expirada antes de enviar.
- `MovementLocalDataSource.kt`: guardar los tipos `REVISE_TRANSACTION` y `VOID_TRANSACTION` y metadatos de revisión en el outbox; antes quedaban como REGISTER por defecto.
- `MovementApi.kt`: serializar la revisión/anulación con defaults y nulls explícitos. La configuración HTTP omitía campos que el contrato exige y devolvía INVALID_COMMAND.
- `SyncMovementsWorker.kt`: esperar el alta/afiliación antes del movimiento; REPLACE para que un nuevo movimiento no espere el backoff de un contrato antiguo. Room, leases e idempotencia mantienen los comandos.
- Repositorio financiero: los comandos dependientes del alta parten de revisión oficial mínima 1. El worker recupera ediciones antiguas rechazadas con revisión local cero cuando su predecesor de alta está sincronizado.
- `FinancialDtos.kt` y contrato remoto: persistir alias y últimos dígitos editados junto a las condiciones de crédito.

## Estado real al dejar el teléfono

Samsung S24+, paquete principal `com.kipu.app`, APK final instalada conservando datos.

| Elemento | Resultado |
| --- | --- |
| Dos cuentas BCP originales | Conservadas: una S/0 y otra S/1,000 |
| YAPE | Afiliada a BCP sin tarjeta, comparte S/1,000 |
| BBVA | Nueva cuenta sin tarjeta, S/100 |
| PLIN | Afiliada a BBVA, comparte S/100 |
| VISA | Crédito S/1,000, últimos dígitos 4242, corte 15/pago 5; alias VISA confirmado remoto, revisión 3 |
| Patrimonio local final | S/1,100; sin sumar dos veces billeteras ni sumar línea de crédito |
| Cupo | 4/4: dos bancos BCP, BBVA y VISA; débito/billeteras no añaden cupos |
| Gasto Transporte S/10 por YAPE | Anulado local y remotamente |
| Ingreso S/1 por PLIN | Anulado local y remotamente |
| Meta existente gg | Conservada; reserva/liberación S/10 completadas, reserva final S/0 |
| Comprobante original pendiente | Conservado; borrador manual temporal descartado |
| Cinco tarjetas remotas antiguas | Archivadas con autorización, tras comprobar que no tenían movimientos; no borradas |

La última consulta remota confirmó para YAPE y PLIN: una transacción por canal, estado VOIDED y suma de ledger cero para cada operación. No quedan esos importes alterando el patrimonio. Los registros anulados permanecen auditables.

## Base de datos: ya desplegado, no volver a aplicar a ciegas

Nuevas migraciones locales aplicadas al proyecto remoto mediante MCP:

1. `20261021191708_shared_bank_instrument_quota.sql`: afiliación sin débito, enum correcto de cuenta, cupo compartido, selección y metadatos.
2. `20261021194835_normalize_card_archive_sync_operation.sql`: permitir ARCHIVE en sync_changes.
3. `20261021200100_reconcile_movement_revision_contract.sql`: contratos de revisión/anulación usando el enum real `public.entry_role`.
4. `20261021200500_sync_credit_card_edit_identity.sql`: edición de alias/dígitos junto a condiciones de crédito.
5. `20261021201500_align_ledger_quota_and_appearance.sql`: aplicar cupo compartido también en el trigger contable y corregir presentación alias/nombre con idempotencia.

También se desplegaron contratos locales existentes que faltaban remotamente: registro V2 (`20261002234503_s4_register_transaction_v2_hash.sql`), canal de billetera (`20261013110000_wallet_payment_channel.sql`) y edición de condiciones (`20261010045615_update_credit_card_terms.sql`).

Los timestamps del historial remoto pueden diferir de los nombres locales porque se aplicó con MCP. Antes de usar `db push`, comparar nombres, contenido e historial. La existencia de una migración local no demuestra despliegue. No desplegar épicas adicionales para intentar igualar todo el servidor.

pgTAP focalizados verdes: cupo/presentación/ledger 14/14; canal de billetera 8/8; archivo 2/2; revisión/anulación 42/42; edición de crédito 12/12. Los fixtures de pruebas SQL se revirtieron con ROLLBACK.

## Evidencia de pruebas disponible

Los logs y capturas están en `.gradle/`, ignorados por Git. No repetir toda la suite legacy.

| Evidencia | Resultado |
| --- | --- |
| `celular-tests.log` | Unitarias iniciales focalizadas: cupo, dashboard, cuentas, comprobantes, contexto de historial y metas |
| `celular-final-build.log` | RegisterCardTest 9 casos verdes y compilación de esa etapa |
| `celular-history-tests.log` | SQL builder, ViewModel y casos de historial verdes |
| `celular-flows-results.log` | 16 instrumentadas de afiliación/metas/navegación verdes |
| `celular-recovery-ui-results.log` | 16 casos Room/UI verdes, incluidas recuperaciones |
| `celular-fixture-results.log` | Dos de los tres casos reparados verdes; contiene un fallo posterior ya resuelto |
| `celular-dispatch-results.log` | El caso de conflicto de revisión que faltaba: OK (1 test) tras corregir despacho |
| `celular-closed-focal-results.log` | Respaldo de metas y leasing verdes; contiene el fallo de despacho anterior, ya resuelto |
| `celular-last-recovery-results.log` | Recuperación de revisión cero y cancelación con lease: OK (2 tests) |
| `celular-wire-unit-results.log` | Unitarias HTTP finales verdes; verifica null/default aun con encodeDefaults=false |
| `celular-final-dao-result.log` | Recuperación Room final: OK (1 test) |
| `celular-final-installed-build.log` | Última compilación debug/lab/androidTest: BUILD SUCCESSFUL; APK debug instalada después |
| `celular-evidencia/` | Capturas de UI claras/oscuras y tamaño de texto ampliado |

Hay logs intermedios con FAILURES porque se conservaron para diagnosticar. No son fallos pendientes por sí mismos: usar el resultado posterior del mismo caso. No afirmar un total global de tests sumando ejecuciones repetidas.

## Qué falta, en orden

1. **Inspección final acotada de sincronización.** Las operaciones de prueba y sus anulaciones sí están confirmadas remotamente. Falta revisar una última vez los estados de `instrument_sync_outbox`, `movement_outbox` y el checkpoint de descarga para distinguir envíos aceptados de un ciclo completo de pull. No se ha validado una instalación nueva ni otro dispositivo. Si aparece un problema reproducible pendiente, corregir solo ese problema y su prueba focalizada.
2. **Revisión final del diff.** Comprobar especialmente recuperación por propietario, condiciones de revisión y cambios de scheduling/cancelación. No convertir esto en otra auditoría global ni en revisión redundante con varios agentes. `git diff --check` no informó errores de whitespace.
3. **Actualizar documentación de cierre.** `tasks.md` ya recoge los recorridos finales. Crear `docs/qa/correcciones-celular/verification-report.md` con veredicto para el alcance verificado, pruebas, despliegues, estado de datos y límites. Las instrucciones del owner piden APPROVED cuando las pruebas focalizadas están verdes; no inventar aprobación de sincronización remota de metas ni de todos los contratos del proyecto.
4. **Revisar texto/encoding de los Markdown.** Algunas ediciones previas por tubería PowerShell no sustituyeron texto con tildes correctamente. Las últimas correcciones de `tasks.md` y este traspaso se hicieron con apply_patch. Normalizar cualquier texto defectuoso del checklist inicial; el seguimiento principal es tasks.md.
5. **Commits convencionales por capas en main.** No hay commits de este trabajo. Seleccionar rutas explícitas: documentación, migraciones/tests SQL, aplicación/tests. No usar `git add .` ni incorporar `worktrees/` o imágenes originales sin decisión expresa. No crear PR main→main ni otra rama; la instrucción expresa del propietario es trabajo directo en main.
6. **Entrega breve al usuario.** Enlazar tareas/informe, resumir correcciones, indicar APK instalada, datos que permanecen y límites. Registrar hashes de commits. No se solicitó push y no se ha hecho.

## Límites que deben quedar explícitos

- La sincronización remota de metas está desactivada por la configuración actual de compilación. Reserva y liberación se validaron localmente; no anunciar persistencia remota de metas como verificada.
- La comprobación visual fue contra referencias y DESIGN.md para las pantallas afectadas, no una certificación de identidad píxel por píxel de toda la aplicación.
- No se validó migración integral de todas las épicas ni inicio de sesión en un segundo dispositivo.

## Herramientas y comandos útiles

- ADB correcto: `C:/Users/Alume/AppData/Local/Android/Sdk/platform-tools/adb.exe`. El `adb` antiguo del PATH provocaba conflictos de servidor.
- Dispositivo conocido: `R5CX10W7CZD`; resolución 1080x2340.
- Helper local ignorado: `python .gradle/celular.py` imprime árbol accesible; con dos argumentos hace tap X Y y vuelve a leerlo.
- No borrar datos ni reinstalar sin `-r`.
- Build instrumentado: `.\gradlew.bat :app:assembleDebug :app:assembleLab :app:assembleLabAndroidTest -PisolatedAndroidTests=true --console=plain`.
- Unitarias: usar `:app:testDebugUnitTest --tests <clase>` SIN `-PisolatedAndroidTests=true`; ese flag cambia los targets disponibles.
- Paquete lab separado: `com.kipu.app.lab`; runner `com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner`.
- ADB puede devolver exit 0 aunque el runner falle: leer `OK (N tests)` o `FAILURES` en el log.
- Skills ya usadas: android-kotlin, ui-ux-pro-max, supabase y supabase-postgres-best-practices. No se usó Spec Kit.

El siguiente agente debe continuar desde este estado, no rehacer la implementación ni repetir los recorridos ya confirmados.

# Verificación HU-06 parte B — 2026-11-03

**Veredicto: APPROVED para implementación, validación local, despliegue backend y validación API. Migraciones `20261103152406_delete_account` y `20261103162347_account_deletion_concurrency_fix`, junto con Edge Function `delete-account` v2 desplegadas en remoto el 2026-11-03. Validación API exitosa con cuenta desechable. Resumen y cancelación probados en dispositivos reales; eliminación integral UI de cuenta real omitida por protección de datos.**

Alcance: escenarios 4–6 de HU-06, implementados en `main` por autorización explícita del Owner. No se hicieron push, PR, merge, instalación en dispositivos ni `connectedAndroidTest`. No se borró ningún usuario real. El contrato y la política completa por tabla están en [account-deletion-v1.md](account-deletion-v1.md).

## Evidencia ejecutada

| Validación | Resultado final |
| --- | --- |
| `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` | Exit 0, `BUILD SUCCESSFUL in 1m 44s`; 1.581 casos, 0 fallos, 3 omitidos por suites existentes. Ambos APK generados. |
| Pruebas nuevas Android | 11 casos: 8 de coordinación/cancelación/reauth/retry/estados, 1 Room con ambas columnas de owner y aislamiento, 1 DataStore con reapertura real del archivo, 1 limpieza de preferencias/archivos/tokens/cancelación de workers. Todas verdes. |
| `deno test --allow-env supabase/functions` | Exit 0: **142 passed, 0 failed**, incluidas 10 nuevas para delete-account. |
| `supabase/tests/database/account_deletion.test.sql` | Exit 0: **27/27** aserciones pgTAP, sin `not ok`; fixtures en transacción con rollback. |
| Regresiones pgTAP focalizadas | Crédito 46/46; ciclo de compras 36/36; RTDN 64/64; preferencias de perfil 8/8. Total con eliminación: **181 aserciones**, sin fallos. |
| Migración nueva y baseline | 107 migraciones reproducidas en PostgreSQL 17/Supabase en contenedor local aislado `kipu-hu06-deletion-test`, con esquemas de infraestructura Auth/Storage sin datos importados. Ninguna migración remota aplicada. |
| `git diff --check` | Sin errores de whitespace. |

Las pruebas cubren cancelación comparando estado persistido antes/después; reauth y confirmación obligatorias; error remoto sin limpieza local; timeout/reapertura con el mismo UUID/capacidad; fallo de limpieza con remoto completado; fase durable de cierre de sesión; aislamiento entre usuarios; borrado de Room en orden FK; preferencias y evidencia propias; archivo temporal de sesión; revocación de SDK/tokens y cancelación por propietario.

pgTAP verifica service-only y search_path fijo, excepción append-only limitada al purge, rollback completo si aparece una tabla no cubierta, borrado de historial/ledger y operacionales, compras/recibos con propietario nulo vinculados a la compra, OTP/rate buckets, conservación byte por byte del otro usuario, bloqueo de repoblación, imposibilidad de completar mientras exista Auth y recibo final sin `user_id`. Deno verifica completed/pending/error, JWT ausente/inválido, AMR password reciente frente a refresh o método incorrecto, confirmación, aislamiento, idempotencia, Admin Auth fallido y respuestas perdidas.

La prueba de persistencia usa Okio/NIO como almacenamiento de prueba por la semántica de reemplazo de archivos de Windows; producción Android conserva PreferenceDataStoreFactory normal. Room permanece compatible sin cambios de esquema para esta historia (versión 36 en el diseño inicial y versión 37 en la aplicación actual tras la migración de captura en Sprint 9, verificada con user_version 37 en hardware). Los avisos existentes de KSP/Room y de JVM no son fallos. La validación API contra el proveedor remoto fue exitosa con cuenta desechable; la eliminación integral en UI de cuenta real se omite para proteger datos del usuario.

## Estado remoto y pasos exactos

El despliegue remoto y la validación fueron completados por el coordinador el 2026-11-03:

1. Migraciones remotas aplicadas: `20261103152406_delete_account.sql` y `20261103162347_account_deletion_concurrency_fix.sql` aplicadas en el proyecto remoto (`https://tnvapmertsncbslafoug.supabase.co`).
2. Edge Function `delete-account` desplegada en v2 (`verify_jwt = false`, con validación interna de JWT y AMR password reciente <= 300 s en el handler).
3. Smoke test:
   ```powershell
   curl.exe -i -X POST https://tnvapmertsncbslafoug.supabase.co/functions/v1/delete-account -H "Content-Type: application/json" -d '{}'
   ```
   Resultado: HTTP/2 401 Unauthorized (`{"code":401,"error":"missing_authorization"}`), verificando función activa y endpoint correctamente protegido contra accesos sin JWT.
4. Validación API con cuenta desechable de prueba:
   - Token envejecido (321 s): HTTP 403 `recent_reauthentication_required`.
   - Con login password reciente: HTTP 200 `completed`.
   - Segunda llamada con misma capacidad: HTTP 200 `completed` (idempotencia verificada).
   - Auth Admin GET `/admin/users/{id}`: HTTP 404.
   - Verificación SQL de 60 tablas con `user_id`: 0 filas remanentes.
5. Validación en hardware: en A16 y S24 se verificaron resumen y cancelación sin pérdida de datos. La eliminación completa no se ejecutó contra la sesión real del usuario para proteger su información.

Los logs brutos locales y evidencias privadas se conservan fuera del repositorio (`C:/Users/Alume/s9-device-validation-20261103/remote-result.json`, etc.). Este informe conserva el resultado verificable sin payloads ni credenciales.

Los logs brutos locales `account-deletion-validation.log`, `account-deletion-deno-all.log`, `account-deletion-pgtap.log` y los cuatro logs pgTAP de regresión están excluidos de Git mediante las reglas existentes de logs. Este informe conserva el resultado verificable sin payloads ni credenciales. Las tres bases temporales creadas durante preparación se retiraron; el contenedor de prueba independiente se detuvo al terminar.


## Corrección de recuperación HU-06 B — revisión 3e5d8d8e (2026-11-03 Lima)

Alcance: hallazgo MEDIUM y consecuencia del segundo cliente. Migración aditiva `20261104021333_account_deletion_owner_recovery.sql`; no se editaron migraciones ya desplegadas. El RPC service-only `prepare_account_deletion` recupera el recibo canónico del mismo propietario y registra aliases privados de las capacidades ya confirmadas. Edge valida JWT contra Auth y exige AMR password <=300 s antes de incorporar una capacidad nueva; usa las credenciales canónicas solo dentro del servidor. Recuperación no repite purge ni crea otro recibo. Aliases conservan el resultado tras desaparecer Auth, resolviendo el ERROR del segundo cliente que antes solo recibía in_progress. Tabla sin user_id, con RLS y privilegios directos revocados; ningún HTTP incluye hashes o capacidades. El guard de escrituras permanece intacto.

Android no cambia: `RemoteAccountDeletion` ya traduce completed/pending correctamente y `AccountDeletion` limpia solo tras completed durable. 401 sin recibo verificable conserva locales: no se interpreta como prueba de eliminación. Una petición legacy que nunca fue autorizada ni obtuvo alias antes de desaparecer Auth no permite inferir eliminación ni borrar cache; la corrección garantiza reconciliación para los nuevos intentos autorizados.

| Verificación focalizada | Resultado |
| --- | --- |
| Deno delete-account | 15/15, exit 0: fallo Auth con capacidad previa/nueva, reintento, propietario ajeno, reauth vencida, aliases, timeout y no exposición de credenciales. |
| pgTAP local con psql ON_ERROR_STOP y comprobación de TAP | Eliminación 27/27; concurrencia existente plan 8: 8/8; recuperación: 21/21; perfil 8/8; compras 36/36. Total 100 aserciones, cero not ok o errores. |
| Concurrencia real local | 7 conexiones contra PostgreSQL: una preparación retenía la transacción mientras seis esperaban; un recibo canónico y seis aliases, todos reconciliaron completed. |
| Prueba de no repetir purge | Trigger temporal rechaza todo DELETE en accounts durante recuperación; preparación y recuperación idempotentes pasan sin ejecutarlo. Se retira antes de simular borrado Auth por sus cascadas. |
| Android focalizado, sin hardware | `:app:testDebugUnitTest --tests '*AccountDeletionTest' --tests '*AccountDeletionRemoteTest'`: BUILD SUCCESSFUL, 10 casos, cero fallos/errores. Conservación local PENDING/ERROR y limpieza tras completed. |
| Despliegue remoto MCP kipu | Migración aplicada y versión de historial alineada a 20261104021333; delete-account ACTIVE v3, verify_jwt=false, descarga remota contiene prepare_account_deletion. Privilegios RPC verificados: anon=false, authenticated=false, service_role=true. |

Evidencia privada: `C:/Users/Alume/s9-device-validation-20261103/deletion-recovery-*` (Deno, pgTAP, concurrencia, Android y API). No tokens ni datos de cuentas reales en el repositorio. El primer intento local encontró baseline anterior sin migración de concurrencia; se aplicaron 20261103162347/20261103172646 ya desplegadas y la tanda final pasó. Un trigger de prueba inicialmente interceptó la cascada de Auth; se acotó su duración y el resultado final pasó. pg_prove no estaba instalado; se ejecutó pgTAP mediante psql y se verificaron planes y aserciones.

Advisors remoto: alias privado con RLS sin políticas informado, intencional por acceso exclusivo mediante RPC SECURITY DEFINER con privilegios service-only; sin hallazgo del RPC nuevo. Avisos globales preexistentes quedan fuera de este fix. No se añadieron políticas que expongan aliases.

No hay APPROVED integral de Sprint 9: T021, TalkBack hablado, A16 en última tanda, Yape real/notificación viva en ambos órdenes y ocho muestras, UI completa de eliminación con cuenta desechable y cambio offline nuevo conservan sus límites anteriores. El LOW documental FR-012 sigue con contradicción específica registrada en EP-AUT: D1/opción A no determina la unicidad física de evidencia con hechos incompatibles; no se inventó aprobación de producto ni se cambió Yape.

**Corrección VALIDADA; sprint PARCIAL.** Prueba API real final: dos usuarios desechables nuevos, 403 cross-owner y 403 AMR realmente envejecido >305 s, recuperación 200 completed con capacidad nueva y login reciente sobre recibo data_deleted/Auth vivo, reconciliación 200 con capacidades original/nueva/segundo dispositivo aun sin Auth, tres reintentos paralelos completed, Admin 404 y cero filas en 60 tablas. Ambas cuentas de prueba eliminadas; secretos temporales retirados. Fallo Auth inyectado en Deno; remoto reproduce el estado persistido mediante purge RPC sin provocar fallo del proveedor. Concurrencia de preparación: siete conexiones SQL locales; concurrencia remota: reconciliación posterior a completed. Evidencia privada `deletion-recovery-api-result.json`; detalle completo en verification-report.md. Ninguna sesión ni datos reales alterados.

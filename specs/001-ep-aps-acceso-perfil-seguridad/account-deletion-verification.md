# Verificación HU-06 parte B — 2026-11-03

**Veredicto: APPROVED para implementación, validación local y despliegue backend. Migración y Edge Function desplegadas en remoto el 2026-11-03. Validación en dispositivo físico pendiente.**

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

La prueba de persistencia usa Okio/NIO como almacenamiento de prueba por la semántica de reemplazo de archivos de Windows; producción Android conserva PreferenceDataStoreFactory normal. Room permanece en versión 36, sin cambio de esquema. Los avisos existentes de KSP/Room y de JVM no son fallos. No se afirma validación visual en dispositivo ni eliminación integral contra el proveedor remoto.

## Estado remoto y pasos exactos

El despliegue remoto fue completado por el coordinador el 2026-11-03:

1. `npx supabase db push` aplicó la migración `20261103152406_delete_account.sql` en el proyecto remoto (`https://tnvapmertsncbslafoug.supabase.co`).
2. `npx supabase functions deploy delete-account` desplegó la Edge Function `delete-account`.
3. Smoke test ejecutado:
   ```powershell
   curl.exe -i -X POST https://tnvapmertsncbslafoug.supabase.co/functions/v1/delete-account -H "Content-Type: application/json" -d '{}'
   ```
   Resultado: HTTP/2 401 Unauthorized (`{"code":401,"error":"missing_authorization"}`), verificando función activa y endpoint correctamente protegido contra accesos sin JWT.

No se ejecutaron eliminaciones contra credenciales o datos de usuarios reales durante la verificación. La validación end-to-end en dispositivo físico permanece pendiente.

Los logs brutos locales `account-deletion-validation.log`, `account-deletion-deno-all.log`, `account-deletion-pgtap.log` y los cuatro logs pgTAP de regresión están excluidos de Git mediante las reglas existentes de logs. Este informe conserva el resultado verificable sin payloads ni credenciales. Las tres bases temporales creadas durante preparación se retiraron; el contenedor de prueba independiente se detuvo al terminar.

# HU-06 B — eliminación de cuenta, contrato y política

Fecha: 2026-11-03. Autoridad: Product Owner, Sprint 9; `docs/producto/backlog.md`, HU-06 escenarios 4–6 y reglas 3–4. Se implementa en `main`, con actualización manual de artefactos y sin rama ni comandos Spec Kit, por autorización explícita del despacho. La eliminación remota está autorizada para desplegar, pero nunca para ejecutar contra una cuenta real durante validación.

## Flujo y autorización

Datos y privacidad → resumen de datos y `PendingLocalChanges` → contraseña actual → confirmación explícita de pérdida de datos → solicitud. Cancelar antes de enviar no escribe solicitud ni elimina información. La suscripción **no se cancela automáticamente**; se enlaza a Google Play > Pagos y suscripciones > Suscripciones. Cerrar un diálogo después de enviar no cancela una operación irreversible ya recibida.

El método vigente es correo y contraseña mediante `auth-access/login`; el OTP de la app confirma el registro, no es su método normal de login. Reautenticar obtiene una sesión temporal del mismo propietario sin instalarla en el SDK ni sobrescribir las credenciales actuales. Contraseña y JWT de prueba permanecen solo en memoria. Edge valida ese JWT contra `GET /auth/v1/user`, compara sujeto y usuario y exige un elemento AMR `method=password` con `timestamp` de los últimos 300 segundos, no futuro. No usa `iat` de refresh ni `user_metadata` como autorización. Si el token caduca antes de confirmar, el servidor rechaza la solicitud.

Referencias del proveedor: [claims y AMR](https://supabase.com/docs/guides/auth/jwt-fields), [Admin deleteUser](https://supabase.com/docs/reference/javascript/auth-admin-deleteuser). La implementación usa la llamada REST equivalente a Admin `deleteUser`, sin SDK adicional. `SUPABASE_SERVICE_ROLE_KEY` existe exclusivamente en Edge y nunca se envía a Android.

## Endpoint y recibo

`POST /functions/v1/delete-account`, `Authorization: Bearer <JWT>`, cuerpo:

```json
{"request_id":"UUID","secret":"64 caracteres hex aleatorios","confirm":true}
```

Android genera 256 bits de capacidad aleatoria y un UUID, y los guarda antes del primer envío en DataStore privado bajo `noBackupFilesDir`. Edge guarda solo SHA-256 de la capacidad en `private.account_deletion_requests`. Ninguna respuesta incluye información financiera, email ni credenciales.

| Respuesta | Significado |
| --- | --- |
| 200 `completed` | Datos y Auth eliminados, recibo final persistido. |
| 202 `pending` | Los datos ya se eliminaron; Auth o persistencia del cierre necesitan reintento. |
| 503 `error` | No se pudo confirmar el borrado de datos; resultado reintentable, nunca éxito supuesto. |
| 401 | Falta autenticación válida para iniciar/continuar una operación sin resultado final verificable. |
| 403 | Falta reautenticación reciente para una solicitud nueva o identidad distinta de la del recibo. |
| 400 | Falta confirmación explícita, UUID/capacidad inválidos o JSON inválido. |

Un recibo previamente autorizado permite continuar sin exigir otra autenticación reciente, pero el JWT debe seguir verificando al mismo usuario mientras Auth exista. Si Auth ya desapareció, la capacidad privada permite reconciliar el resultado incluso con un bearer vencido o con el marcador `receipt-reconciliation`: solo devuelve un recibo completado o verifica con Admin que el usuario del recibo ya no existe y finaliza ese recibo. Esto no inicia un borrado ni permite borrar Auth sin JWT válido. Sin header bearer, siempre 401. Una capacidad incorrecta no revela un recibo.

`purge_account_data` es `SECURITY DEFINER`, `search_path=''`, propiedad del rol de migraciones `postgres`; solo `service_role` puede ejecutar el RPC, igual que `account_deletion_status` y `complete_account_deletion`. El propietario se toma exclusivamente del JWT validado, nunca del cuerpo. El RPC borra en orden de FK dentro de una transacción; un inventario final de todas las tablas con `user_id uuid` aborta si queda cualquier fila, incluyendo futuras tablas no incorporadas. Los recibos de eliminación se excluyen deliberadamente de ese inventario.

Los bloqueos transaccionales por usuario y triggers de escritura impiden que dispositivos o trabajos concurrentes repueblen filas tras el purge y durante un fallo de Auth. El historial append-only solo acepta DELETE en la transacción privilegiada con el sujeto exacto y un recibo `deleting`; una variable de sesión falsificada no basta. UPDATE de evidencia continúa prohibido. No se desactivan FK ni triggers. El borrado de Auth se efectúa después del commit de los datos; el recibo no cambia a `completed` hasta verificar su ausencia en `auth.users`. Entonces se elimina el `user_id` del recibo.

## Política por tabla

No se establece una retención legal nueva ni se simula cancelación de Google Play. Se borran los registros propios de Kipu de facturación y compra; la suscripción y registros del proveedor permanecen bajo la gestión de Google Play.

| Tablas | Política |
| --- | --- |
| `public.profiles`, `plan_preferences`, `user_devices` | Borrar partición del usuario. |
| `accounts`, `cards`, `card_personal_teas`, `financial_movements`, `transactions`, `transaction_links`, `transaction_revisions`, `movement_official_revisions`, `credit_installments`, `credit_payment_allocations` | Borrar datos e historial del usuario por solicitud explícita de cuenta. La excepción no se aplica al borrado normal de objetos financieros. |
| `internal.ledger_entries`, `movement_ledger_effects`, `movement_ledger_aliases` | Borrar después de las referencias dependientes y antes de las fuentes. |
| `public.debts`, `debt_installments`, `debt_events`, `goals`, `goal_events`, `budgets`, `budget_periods`, `budget_tombstones`, `recurrences`, `import_candidates` | Borrar hijos y luego padres del usuario. |
| `public.categories`, `category_presentations`, `category_conflicts`, `merchant_services`, `merchant_rules`, `merchant_alias_rules`, `merchant_category_preferences` | Borrar filas privadas del usuario; conservar catálogos globales con propietario nulo. |
| `public.app_notifications`, `internal.command_receipts`, `internal.sync_changes` | Borrar notificaciones, recibos operacionales y feed/outbox remoto propios, incluidos payloads. |
| `public.billing_purchases`, `internal.billing_events`, `billing_event_receipts`, `billing_reconciliation_jobs` | Borrar filas del usuario, jobs primero. También borrar eventos y recibos con `user_id NULL` vinculados por hash de una compra verificada del usuario, antes de borrar esa compra. No cancelar Play. |
| `private.plan_selection_heads`, `plan_selection_receipts`, `plan_quota_selection_heads`, `plan_quota_selection_items`, `plan_quota_selection_receipts`, `trial_eligibility_snapshots`, `profile_preference_receipts` | Borrar partición, sin conservar acceso o elegibilidad reutilizable. |
| `internal.dev_plan_receipts`, `developer_entitlements`, `developer_entitlement_audit`, `test_plan_overrides`, `test_plan_override_audit` | Borrar filas del sujeto. Anonimizar atribuciones textuales exactas al UUID en filas de otros sujetos (`created_by`, `updated_by`, `actor`, `granted_by`) con `account-deleted`. |
| `internal.merchant_search_access`, `merchant_discovery_events` | Borrar búsquedas autorizadas y eventos atribuidos al usuario. |
| `internal.merchant_catalog_audit` | Mantener procedencia de catálogo compartido, con `added_by=NULL`; las entradas de comercios privados se eliminan por FK. |
| `private.auth_otp_guards`, `registration_rate_buckets` | Borrar guard por hash del correo normalizado y buckets de identidad de register/login/recovery/verify-otp/resend-otp. Conservar contadores IP/globales no atribuibles a un único usuario bajo la política existente de abuso. |
| `private.account_deletion_requests` | Mantener identificación del sujeto solo mientras queda trabajo remoto. Al completar, retener UUID de solicitud, hash de capacidad, tiempos y estado; `user_id=NULL`, sin FK huérfana ni payload personal. |
| Catálogos `currencies`, `billing_products`, `credit_products`, `referential_rate_catalog`, caches globales de comercios/dominios | Conservar datos compartidos, no pertenecen a la cuenta. |
| Storage `merchant-logos`, `email-assets` | Conservar activos compartidos. Son los dos buckets existentes inspeccionados; no existe bucket privado de evidencia OCR de usuarios en este incremento. |

## Estado y limpieza Android

El diseño original de eliminación operó sobre Room v36; la versión actual de Room en la aplicación es **v37** (tras la migración v36→v37 incorporada en Sprint 9 para evidencia de captura), y la eliminación no requiere migraciones adicionales de esquema Room, manteniendo compatibilidad plena con el esquema vigente (verificado `user_version 37` en ambos dispositivos reales). DataStore guarda una solicitud por propietario con estado remoto `PENDING/ERROR/COMPLETED` y local `RETAINED/PENDING/SESSION_PENDING/CLEARED`. El bearer temporal y la contraseña nunca se guardan. El diario se excluye de backup por ubicación; al confirmar limpieza de datos se borra su capacidad y solo queda un marcador mínimo de resultado.

Timeout, offline o error del servidor conservan los datos locales, incluidos los no sincronizados. Al confirmar remoto `COMPLETED`, se guarda ese resultado antes de limpiar; un fallo local conserva el estado remoto completado y permite reintentar solo la limpieza. Antes de borrar Room se cancelan trabajos propios de sincronización/entitlement/recordatorios y se revoca el propietario activo, de modo que respuestas tardías fallen sus guards. Todas las tablas Room con `user_id` o `owner_id` se borran en orden topológico de FK, dentro de una transacción. Se conservan permisos de instalación y catálogos globales.

Se eliminan claves propias en `plan_onboarding`, `goal_display`, `budget_appearance_v1`, `movement_receipts_v1`, `instrument_contract_recovery`, `movement_contract_recovery`; se liberan permisos SAF de recibos, sin borrar el documento original elegido por el usuario. La evidencia OCR/notificaciones actual vive en Room; también se limpian directorios temporales `ocr/<owner>`, `evidence/<owner>`, `receipts/<owner>` y `portability/<owner>` dentro de files/cache, sin tocar otros propietarios. Finalmente se limpia SDK, almacenamiento cifrado de tokens y sesión. El estado `SESSION_PENDING` conserva reintento durable si falla esa última fase.

Desde el acceso se puede consultar una solicitud inconclusa del dispositivo aunque restaurar sesión haya fallado porque Auth fue borrado. No se muestran datos financieros ni resúmenes de pendientes de otra identidad en ese modo. Si Auth todavía existe y el JWT ya no es válido, se solicita de nuevo correo/contraseña y se verifica que correspondan al UUID del recibo; la capacidad por sí sola no autoriza borrar esa cuenta.

## Despliegue y verificación

La migración `20261103152406_delete_account.sql` y la Edge Function `delete-account` fueron **DESPLEGADAS en remoto el 2026-11-03**. Posteriormente, se desplegó la migración `20261103162347_account_deletion_concurrency_fix.sql` para reconciliar bloqueos y solicitudes concurrentes, y la Edge Function `delete-account` se actualizó a la versión **v2** con `verify_jwt = false` (la validación del token JWT del usuario y la comprobación de AMR password reciente <= 300 s se ejecutan internamente en el handler de la función).

Comprobaciones ejecutadas por el coordinador:
- `npx supabase db push` / MCP `kipu.apply_migration`: migraciones `20261103152406_delete_account.sql` y `20261103162347_account_deletion_concurrency_fix.sql` aplicadas en Supabase remoto (`https://tnvapmertsncbslafoug.supabase.co`).
- `npx supabase functions deploy delete-account`: función `delete-account` v2 desplegada y activa con `verify_jwt = false`.
- Smoke test inicial: llamada `POST https://tnvapmertsncbslafoug.supabase.co/functions/v1/delete-account` con cuerpo JSON sin JWT / con JWT inválido devolvió HTTP 401 (`{"code":401,"error":"missing_authorization"}`), verificando función activa y debidamente protegida.
- **Validación API con cuenta desechable de prueba**:
  1. JWT envejecido (321 s): devolvió HTTP 403 `recent_reauthentication_required`, confirmando la barrera de reautenticación por contraseña reciente.
  2. Reautenticación con contraseña completó la llamada: HTTP 200 `completed`.
  3. Repetición de la solicitud con la misma capacidad: HTTP 200 `completed` idempotente sin duplicar escrituras ni generar estados inconsistentes.
  4. Verificación en Supabase Auth Admin API (`GET /admin/users/{id}`): HTTP 404, confirmando eliminación completa del usuario en Auth.
  5. Inspección de base de datos remota: se verificaron 60 tablas que contienen `user_id uuid` y se confirmó **0 filas** remanentes para dicho usuario.
- **Validación en dispositivos reales (A16 y S24)**: se probó el diálogo de resumen y el botón Cancelar con la sesión real activa; se confirmó la advertencia de pérdida de datos, Google Play y cambios pendientes, cancelando sin avanzar a confirmación ni ejecutar borrados sobre la cuenta real. La UI de eliminación completa no se ejecutó contra la sesión real para proteger los datos reales del usuario.

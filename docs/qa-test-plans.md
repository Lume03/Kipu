# QA test plans administrados desde la base de datos

Este mecanismo permite validar capacidades Free/Premium sin Google Play. No crea ni modifica compras comerciales. La migración `20261021125728_plan_qa_entitlement_overrides.sql` instala el mecanismo con el gate global apagado; no contiene asignaciones de usuarios.

## Contrato de refresh

`POST /functions/v1/refresh-entitlement`

- Requiere `Authorization: Bearer <JWT de Supabase>`; el usuario se deriva de `/auth/v1/user`.
- El body JSON debe contener exactamente `{ "installationPublicKey": "<Base64 DER SPKI P-256>" }`. Se rechazan `userId`, `tier`, expiración y otras propiedades.
- Si el gate DB `internal.test_plan_override_gate.enabled` está apagado, responde `403 {"outcome":"REJECTED","code":"TEST_PLANS_DISABLED","retryable":false}`.
- Éxito: `{"effectiveTier":"FREE"|"PREMIUM","source":"TEST_OVERRIDE"|"COMMERCIAL","expiresAt":"<RFC3339 UTC>"|null,"offlineEntitlementGrant":{"payload":"...","signature":"...","keyId":"..."}|null}`. Para `FREE`, `expiresAt` y el grant son siempre `null`; para Premium, `expiresAt` coincide exactamente con `entitlementEndsAt` firmado (puede ser null para entitlement sin fin comercial).
- Free siempre devuelve `offlineEntitlementGrant: null`, que indica al cliente reemplazar/limpiar el cache firmado. Premium requiere grant ES256 firmado por el issuer existente. El grant QA incluye `source:"TEST_OVERRIDE"` dentro del payload firmado; concesión máxima 72 horas y nunca posterior a la expiración del override. El grant comercial conserva su payload actual.
- Override ausente, expirado o gate apagado equivale al plan comercial, calculado directamente desde `billing_purchases`/`billing_products`; sólo entitlement `ACTIVE` vigente (o sin expiry) cuenta como Premium comercial. No se escriben `billing_purchases` ni `internal.billing_events`.
- Ningún fallo del endpoint autoriza Premium. Requiere los secretos server-only ya usados por el issuer: `ENTITLEMENT_GRANT_PRIVATE_JWK`, `ENTITLEMENT_GRANT_KEY_ID`; no son configuración cliente ni parte de esta migración.

## Administración SQL

Sólo operador DB con privilegios de `postgres` puede ejecutar las funciones internas. No se concedió acceso a `anon`, `authenticated` ni `service_role` para modificar gate/overrides. Las tablas están en `internal`, con RLS forzado; todos los cambios dejan registro de actor, motivo y fecha en `internal.test_plan_override_audit`.

```sql
-- Activar/desactivar el gate global (apagado por defecto).
select internal.set_test_plans_enabled(true, 'QA session <ticket>');

-- Asignación acotada; elegir un usuario de prueba por ID sólo al iniciar QA.
select internal.set_test_plan_override(
  '<test-user-uuid>', 'PREMIUM', now() + interval '2 hours', 'QA <ticket>'
);
select internal.set_test_plan_override(
  '<test-user-uuid>', 'FREE', now() + interval '2 hours', 'QA <ticket>'
);

-- Eliminar override; el usuario vuelve al cálculo comercial si el gate continúa activo.
select internal.reset_test_plan_override('<test-user-uuid>', 'QA cleanup <ticket>');

-- Cerrar gate al terminar: fallback SQL inmediato y refresh online siguiente;
-- una concesión offline ya emitida no se revoca y puede durar hasta 72 horas.
select internal.set_test_plans_enabled(false, 'QA session complete <ticket>');
```

No ejecutar asignaciones sobre usuarios reales. Operadores deben usar exclusivamente cuentas dedicadas de QA, incluir motivo/ticket y cerrar el gate al finalizar. El gate desactivado hace fallback comercial inmediatamente en SQL y al siguiente refresh online; **no revoca una concesión offline ya firmada**: puede seguir siendo válida hasta su límite de 72 horas o expiración comercial, y se reevalúa al volver online. Las cuotas existentes leen `public.v_feature_access`; conserva las columnas y `plan_tier='PRO'` para consumidores actuales, pero refleja el tier QA sólo bajo gate y vigencia válidos.

## Validación

```powershell
deno test supabase/functions/refresh-entitlement/index_test.ts supabase/functions/verify-purchase/offline-entitlement-grant_test.ts
```

`supabase/tests/database/plan_qa_overrides_test.sql` es pgTAP transaccional y hace `ROLLBACK`; ejecutar sólo en la base aislada apropiada.

## Estado de despliegue (2026-10-21)

- PgTAP: **44/44** aserciones aprobadas en la base aislada `qa_test_plans_f9662dcad957`; el test hizo rollback. La migración inicial y la corrección de privilegios se aplicaron una sola vez en remoto; no volver a aplicarlas manualmente.
- Seguridad de `public.v_feature_access`: la migración `20261021132326_revoke_direct_feature_access_view.sql` revoca `ALL` a `PUBLIC`, `anon`, `authenticated` y `service_role`, y concede sólo `SELECT` a `service_role`. El dueño `postgres` conserva acceso; el RPC `get_feature_access()` conserva su contrato de usuario autenticado y las guardas de cuotas siguen pasando.
- Edge Function `refresh-entitlement`: desplegada y `ACTIVE`, versión 1, con `verify_jwt=true`. El issuer ES256 relativo se incluyó en el bundle; los secretos permanecen server-only.
- Gate remoto: **desactivado** tras retirar el único override QA Premium asignado a la cuenta del propietario mediante `reset_test_plan_override`; quedaron **0 overrides**. El cierre se registró en `internal.test_plan_override_audit`. El siguiente refresh usa el entitlement comercial verificado; no se modificaron compras ni billing.
- Verificaciones de código: Deno **10/10**, `deno check` y `git diff --check` aprobados.
- Advisory de seguridad posterior a la corrección: los errores `auth_users_exposed` y `security_definer_view` de `public.v_feature_access` ya no aparecen. Los **36** avisos `authenticated_security_definer_function_executable` se mantienen sin cambio. Persisten cinco hallazgos INFO `rls_enabled_no_policy` (tres tablas QA y dos tablas preexistentes); las tablas QA tienen RLS forzado y acceso directo revocado. No se alteraron advisories fuera del alcance.

El backend QA está operativo y los dos errores de seguridad de la vista quedaron corregidos; los avisos no relacionados permanecen sin cambios.

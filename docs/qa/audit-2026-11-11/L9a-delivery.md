# L9a — Reglas de dinero y fechas SQL

Implementado en `Lume03/audit-fixes`, sin commits, push ni cambios remotos. Migración: `supabase/migrations/20261112120000_money_and_date_rules.sql`. Resultado local: **PASS**.

## Funciones reemplazadas y origen vigente

Antes de reemplazar cada función se buscaron sus definiciones y referencias en todas las migraciones. `CREATE OR REPLACE` conserva propietario y ACL; se mantienen las firmas, los modos de seguridad y `search_path = ''`. `get_budget_status` y `_rec_template_references_valid` siguen siendo invoker, como en sus definiciones de origen; el resto mantiene `SECURITY DEFINER`, incluido el propietario dedicado `profile_preferences_executor` de preferencias.

| Función | Migración de origen | Cambio |
|---|---|---|
| `internal.goal_confirmed_balance(uuid,uuid)` | `20261018190000_ep_mov_opening_ledger.sql` | SQL3-01: incluye `REVISED` y suma el neto del ledger; conserva las aperturas canónicas y excluye anulados/borrados. |
| `internal.movement_instrument_locked_v1(uuid,uuid)` | `20261003004348_s4_movement_revision_rpc_v1.sql` | SQL2-02: delega en `private.is_plan_quota_resource_locked`, con Premium y grupos compartidos. |
| `public.allocate_credit_payment_v1_pre_liability(jsonb)` | `20261031163043_credit_purchase_terms_and_cycles.sql` | SQL4-02 = SQL2-06: usa el saldo canónico anterior; elimina la suma del espejo `initial_balance_minor_units`. |
| `public.reassign_goal_reserve_v1(jsonb)` | `20261017180300_ep_met_goal_lifecycle_commands.sql` | SQL3-02: bloqueo compartido de cupo y validación del número final de metas activas antes de generar eventos; completar el destino puede liberar el cupo del origen. |
| `public._rec_template_references_valid(jsonb,uuid)` | `20261018190500_ep_rec_create_update.sql` | SQL3-07: una plantilla de gasto exige categoría `GENERAL` o `EXPENSE`. |
| `public.create_recurrence_v1(jsonb)` | `20261018190500_ep_rec_create_update.sql` | SQL3-03/09: recupera el recibo antes de validar referencias mutables y convierte enteros JSON mediante `numeric`. |
| `public.update_recurrence_v1(jsonb)` | `20261018190500_ep_rec_create_update.sql` | SQL3-08/09: compara frecuencia, intervalo y unidad para exigir la cadencia completa; admite enteros decimales integrales. |
| `public.set_recurrence_status_v1(jsonb)` | `20261101150000_recurrence_resume_lima_date.sql` | SQL3-09: admite revisión decimal integral y conserva la fecha Lima del parche posterior. |
| `public.set_instrument_archived_v1(jsonb)` | `20261111140000_revision_order_fixes.sql` | SQL4-03: bloqueo compartido con creación y validación de grupos finales, exención Premium y rollback atómico del exceso Free; conserva recibos idempotentes L8 y protección de deuda pendiente. |
| `public.get_budget_status(uuid,date,text)` | `20261024190000_ep_pre_budget_scope_snapshot.sql` | SQL4-06: busca primero el snapshot que contiene la fecha y utiliza sus límites, alcance y fechas; solo calcula calendario actual cuando no hay snapshot. Decisión 14: default `America/Lima`. |
| `public.update_profile_preferences(uuid,bigint,jsonb)` | `20261103172646_profile_preferences_private_usage.sql` | SQL4-08: serializa propietario/operación antes de leer el recibo. Conserva propietario, RLS y protocolo de revisión. |
| `public.upsert_merchant_alias_rule_v1(jsonb)` | `20261008161540_ep_cco_s5_alias_commands.sql` | SQL3-06: consulta el recibo antes del estado mutable del comercio. |
| `public.upsert_merchant_category_preference_v1(jsonb)` | `20261008161631_ep_cco_s5_preference_commands.sql` | SQL3-06: consulta el recibo antes del estado mutable del comercio y categoría. |

SQL4-09: índice nuevo `merchant_services_version_idx` sobre `merchant_services(version)`, conservando el asignador y su bloqueo global.

SQL3-10: `debt_sprint5_migration_upgrade.ps1` captura TAP y rechaza `not ok` aunque psql salga con 0. SQL3-11: `debt_sprint5_quota_concurrency.ps1` incluye `preset_id='GENERIC'` en su fixture bancario.

## Validación

- `npx --no-install supabase db reset`: **exit 0**, migración nueva aplicada en `supabase_db_kipu`.
- `npx --no-install supabase test db`: **exit 0**, **81 archivos / 1.583 aserciones**, `All tests successful`, `Result: PASS`.
- `money_and_date_rules.test.sql`: **47 aserciones**. Cubre edición real de nota/importe e ingreso con compensaciones, apertura corregida, rechazo sin asientos y pagos válidos; reasignación Free sin efectos al rechazar y finalización simultánea del destino; reintento de recurrencia tras archivo, números integrales decimales, categoría incompatible y cadencia CUSTOM parcial; cambio entre cuentas Free agrupadas/Premium y reactivaciones; snapshot mensual sobre calendario semanal, alcance histórico y medianoche Lima; reintentos de alias/preferencias tras inactivación.
- Los fixtures de `credit_card_principal_archive.test.sql`, `credit_sprint3_commands.test.sql` y `financial_payment.test.sql` ahora crean hechos de apertura. Antes solo daban fondos al espejo de cuenta. La aserción de saldo de `financial_payment.test.sql` también consulta el saldo canónico. Se conservan los resultados financieros esperados.
- SQL4-08: dos conexiones psql, mismo propietario/operación/revisión/payload, rol `authenticated`. Primera petición `APPLIED` sin commit; segunda observada en `pg_stat_activity` esperando `advisory`; después del commit devuelve `DUPLICATE`, revisión **2**, recibos **1**. Fixture eliminado y ausencia de usuario sintético verificada. Prueba adicional ejecutada desde shell, fuera de la suite pgTAP.
- SQL3-11: harness de concurrencia ejecutado en `supabase_db_kipu`, **exit 0**: una apertura aplicada, otra rechazada por cupo, dos deudas activas; limpieza completada.
- SQL3-10: entrada sintética `SELECT fail(...)`, psql **exit 0** y `not ok` detectado por la nueva condición. No se ejecutó el upgrade histórico completo de ese harness.
- SQL4-09: `EXPLAIN (COSTS OFF) SELECT MAX(version) ...`, con `enable_seqscan=off` local y rollback, muestra `Limit → Index Only Scan Backward using merchant_services_version_idx`. Demuestra que el índice permite el acceso; no es una medición de carga representativa.
- Inspección de catálogo: ACL de helpers internos cerrada, propietario dedicado de preferencias conservado y `search_path` vacío. `git diff --check`: limpio.

## Hallazgos no realizados en este lote

| Hallazgo | Motivo |
|---|---|
| SQL2-03 | El reordenamiento del RPC de selección de cupo requiere revisar identidad, bloqueo y elegibilidad en varias ramas y su cobertura; excede el ajuste medio de una o dos líneas solicitado. |
| SQL2-04 | Requiere un identificador de adquisición y cambios de firma/llamadores de acknowledgement: cambia contratos, fuera del alcance de medios pequeños. |
| SQL2-05 | Falla antes de alcanzar la nueva migración y requiere resolver categorías de datos legacy sin inventarlas; una migración final no repara una validación histórica ya fallida. |
| SQL3-12 | Combina permisos del firmante y limpieza de ledger en dos harnesses, además de validación de limpieza al fallar; se deja como corrección de harness independiente, superior al ajuste de una o dos líneas conjunto. |
| SQL3-13 | Sustituir aserciones textuales por un caso funcional de sobreutilización es una ampliación de prueba, superior a una o dos líneas. |
| SQL4-07 | Acotar y agrupar refrescos exige modificar helpers/triggers y decidir dónde ejecutar el refresco atómico del comando; no se simuló una optimización con un cambio superficial de filtro. |

SQL3-04/05 ya se corrigieron en L6 (`20261112100000_debt_fixes.sql`), SQL4-05 en L7 y SQL2-01/SQL4-04 en la migración de orden de revisiones de L8; no se reemplazan aquí esas implementaciones. SQL4-01 pertenece al lote previo de reglas de anulación/reembolso de tarjeta.

## Riesgos y límites

- La validación es exclusivamente local; no acredita despliegue ni datos remotos. No se aplicó ninguna migración remota.
- El saldo exige hechos de apertura canónicos. Datos creados mediante DML que solo complete el espejo de cuenta, sin un movimiento de apertura, no constituyen fondos; los fixtures antiguos se corrigieron explícitamente.
- Ante snapshots solapados, la consulta elige de forma determinista el de inicio más reciente. No se agregaron restricciones ni reparaciones de periodos históricos solapados.
- La reactivación Free actualiza bajo bloqueo y comprueba los grupos resultantes; una excepción por exceso revierte la actualización y sus efectos de triggers. El bloqueo global de versiones de comercios permanece; el índice no elimina esa serialización.
- El RPC de presupuesto conserva su parámetro explícito de zona; cambia únicamente el default aprobado a Lima.

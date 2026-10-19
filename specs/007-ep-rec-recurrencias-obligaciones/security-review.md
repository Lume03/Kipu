# EP-REC — Revisión de seguridad independiente T022

Fecha de revisión: 2026-10-18  
Branch revisada: `007-ep-rec-recurrencias-obligaciones`  
Alcance: revisión estática read-only del candidato Supabase EP-REC y sus pruebas locales; no se aplicaron migraciones.

## Veredicto

**APPROVED WITH OBSERVATIONS** — habilita únicamente la etapa de prueba SQL local descartable `t_50ebd716`. La re-revisión estática confirma que SEC-REC-001 quedó corregido: UPDATE limita el canonical JSON a 16,384 bytes y la regresión cubre rechazo de un comando válido de 16,385 bytes sin escrituras. No se aplicaron migraciones ni se ejecutó el candidato; este dictamen no es aprobación de runtime, hosted Supabase ni release.

Este dictamen cubre solamente el diff local candidato. No es aprobación de runtime, RLS dinámica, hosted Supabase ni release.

> **Resolución Ejecutiva del Owner (2026-10-19)**: Queda cerrado y anulado formalmente SEC-CAND-002 al tratarse de un entorno de desarrollo local 100% aislado. Se autorizó la instancia local oficial `supabase_db_kipu` (127.0.0.1:54322), en la cual se ejecutó la suite completa pgTAP 80/80 con resultado PASS (exit code 0), certificada en `pgtap-80-80-execution.log`. Queda desbloqueado el avance para converge (T024) y release (T025).

## Hallazgos

### SEC-REC-001 — límite de tamaño en UPDATE (corregido; requiere GREEN local)

- **Severidad:** MEDIUM (hallazgo original; remediación confirmada estáticamente)
- **Estado:** Corregido en el diff candidato; comportamiento aún no verificado en runtime
- **Categoría OWASP/CWE:** CWE-400 — Uncontrolled Resource Consumption; OWASP API4:2023 — Unrestricted Resource Consumption
- **Ubicación:** `supabase/migrations/20261018190500_ep_rec_create_update.sql:252-258`; regresión `supabase/tests/database/recurrence_create_update_test.sql:147-174`.
- **Descripción/evidencia de remediación:** UPDATE ahora comprueba `pg_catalog.octet_length(v_canonical) > 16384` inmediatamente después de canonicalizar y antes de calcular SHA-256, adquirir el advisory lock, buscar el receipt o mutar datos (líneas 252-258). CREATE y SET_STATUS mantienen el mismo límite. El test construye un comando semánticamente válido cuyo canonical JSON ASCII es exactamente 16,385 bytes, calcula el digest correcto y comprueba `REJECTED` junto con estado idéntico de `recurrences`, `command_receipts` y `internal.sync_changes` (líneas 147-174).
- **Riesgo residual:** el límite contractual está definido en bytes UTF-8 y el guard usa `octet_length`, apropiado para esa unidad. No se añadió un caso de frontera de exactamente 16,384 bytes; la aceptación del límite inclusivo y la regresión negativa deben verificarse al ejecutar la suite candidata local. No inferir protección frente al coste de parsear JSON antes de que PostgreSQL reciba el valor `jsonb`.
- **Prioridad:** Media; sin defecto estático pendiente que bloquee el test-runner local autorizado.
- **Método de verificación pendiente:** en `t_50ebd716`, ejecutar las suites contra el candidato aplicado solo a la DB local descartable; comprobar GREEN de la regresión 16,385 bytes y, si procede, añadir/verificar aceptación del límite 16,384 bytes.

## Controles observados en la revisión estática

- `20261018190400_ep_rec_rule_schema.sql:37-53`: RLS habilitada y `FORCE ROW LEVEL SECURITY`; política SELECT compara `auth.uid()` con `user_id`; acceso directo de escritura de `authenticated` revocado y SELECT concedido. Esta evidencia es de código/configuración, no prueba dinámica bajo roles autenticados.
- `20261018190500_ep_rec_create_update.sql:148-151, 226-230` y `20261018190600_ep_rec_lifecycle.sql:3-6`: RPCs `SECURITY DEFINER` fijan `search_path = ''`; los helpers revocan EXECUTE a `PUBLIC`, `anon` y `authenticated`; RPCs revocan a `PUBLIC`/`anon` y conceden a `authenticated`.
- CREATE obtiene el propietario desde `auth.uid()` y usa ese UID para insertar; UPDATE/lifecycle filtran la fila por `id` y `user_id`. Las referencias a cuentas/categoría en el helper se cotejan con el UID autenticado. Los errores para objeto no encontrado no exponen existencia de otra fila (`NOT_FOUND`).
- Se inspeccionaron las tres migraciones candidatas: no crean/llaman/habilitan `project_recurrence_occurrences` y no contienen escrituras a tabla de ocurrencias o tablas financieras. Las escrituras de comandos son a `recurrences`, `internal.sync_changes` y `internal.command_receipts`; los tests comparan efectos financieros en sus escenarios.
- Escrituras de fila, feed y receipt están en la misma invocación SQL/transacción; un error SQL entra en el bloque `EXCEPTION` y devuelve rechazo. La revisión estática no sustituye una prueba de atomicidad en runtime.

## Evidencia y límites de verificación

Archivos del candidato revisados:

- `supabase/migrations/20261018190400_ep_rec_rule_schema.sql`
- `supabase/migrations/20261018190500_ep_rec_create_update.sql`
- `supabase/migrations/20261018190600_ep_rec_lifecycle.sql`
- `supabase/tests/database/_recurrence_test_fixture.sql`
- `supabase/tests/database/recurrence_schema_test.sql`
- `supabase/tests/database/recurrence_create_update_test.sql`
- `supabase/tests/database/recurrence_lifecycle_test.sql`

También se consultó `specs/007-ep-rec-recurrencias-obligaciones/tasks.md`, el contrato `contracts/recurrence-command-contract.md`, y las migraciones locales relevantes para las tablas internas/policies existentes.

El handoff backend reporta que ejecutó (contra baseline intacto, antes del parche):

`pnpm exec supabase test db --local supabase/tests/database/recurrence_schema_test.sql supabase/tests/database/recurrence_create_update_test.sql supabase/tests/database/recurrence_lifecycle_test.sql`

Resultado comunicado: exit 1, 79 aserciones pgTAP; las suites fallan en baseline sin objetos EP-REC (schema 26/26, create/update 22/29 y lifecycle 18/24 fallidas), sin error de harness. No inspeccioné un archivo de log bruto independiente; registro el resultado como evidencia reportada en el handoff backend, no como ejecución propia. En esta re-revisión ejecuté `git diff --no-index --check /dev/null` para la migración 1905 y el test T010; ambos dieron solo el aviso LF→CRLF, sin diagnósticos de whitespace (exit 1 esperado porque son archivos nuevos comparados con `/dev/null`).

**No se aplicaron migraciones ni ejecuté tests contra el candidato.** Por lo tanto, SQL parse/runtime, RLS bajo roles autenticados, aislamiento dinámico entre owners, atomicidad ejecutada, canonicalización/hash golden parity Kotlin/PostgreSQL y ausencia dinámica de efectos no están verificados. El RED contra el baseline no constituye evidencia GREEN del candidato.

## Re-revisión del fix SQLSTATE 42601

Fecha: 2026-10-18  
Alcance: revisión independiente, estática y read-only del candidato final tras el fix de `_rec_valid_integer`; se preserva arriba el dictamen T022 y su historial, pero este dictamen evalúa explícitamente el SQL actualizado.

### Veredicto

**APPROVED WITH OBSERVATIONS** — sin defecto estático pendiente en el cambio revisado; habilita únicamente el siguiente gate de prueba SQL en instancia local descartable (`t_50ebd716`). No es aprobación de ejecución, release ni entorno hosted.

### Evidencia inspeccionada

- `supabase/migrations/20261018190500_ep_rec_create_update.sql:33-45`: `_rec_valid_integer` conserva `SET search_path = ''`, rechaza valores JSON que no sean `number`, convierte el escalar a `numeric`, valida integralidad y límites inclusivos, y mantiene el fallback `EXCEPTION WHEN OTHERS THEN RETURN false`. La llamada está escrita como `pg_catalog.trunc(v)`, calificación explícita correcta del builtin `trunc(numeric)` pese al `search_path` vacío.
- El diff final disponible en este workspace presenta las migraciones candidatas como archivos no rastreados (no hay diff Git contra una versión base rastreada para demostrar el tamaño exacto del delta); el handoff del fix afirma que su único cambio fue la calificación `pg_catalog.trunc(v)`. La revisión confirma el estado final y compatibilidad semántica aparente, no una comparación verificable byte a byte con un padre Git.
- SEC-REC-001 continúa presente: `supabase/migrations/20261018190500_ep_rec_create_update.sql:252-257` aplica el límite de `octet_length(v_canonical) > 16384` al UPDATE antes de locks, receipt lookup o mutaciones. La regresión previamente documentada permanece en `supabase/tests/database/recurrence_create_update_test.sql:147-174` (corrección estática; no re-ejecutada en esta re-revisión).
- Se confirma estáticamente la preservación de controles previamente revisados en el candidato: `search_path = ''` en las funciones relevantes (`:33-37`, `:148-152`, `:226-230`); los RPC filtran por `auth.uid()` y `user_id` (`:154-158`, `:264-266`); grants/revokes de helpers y RPC al final (`:300-302`).

### Observaciones y límites

- **Runtime/parser pendiente:** no se ejecutó PostgreSQL parser contra esta versión corregida ni se aplicó/ejecutó SQL candidato. El error SQLSTATE 42601 previo corresponde únicamente a la versión anterior y no demuestra el resultado del fix. El parser/apply debe verificarse en el gate local descartable.
- La integralidad decimal (`v = trunc(v)`) y validación de rango se conservan estáticamente; no se afirma su ejecución ni paridad Kotlin/PostgreSQL.
- No se afirma GREEN de tests, RLS dinámica, aislamiento multi-owner, atomicidad runtime, efectos financieros dinámicos, hosted Supabase ni aprobación de release.

### Verificación requerida en el siguiente gate

Aplicar y ejecutar únicamente en instancia local descartable autorizada; comprobar que la migración corregida parsea y que las suites candidatas pasan, incluyendo la regresión de SEC-REC-001. Mantener separado cualquier dictamen posterior de este aprobado estático.

## Re-revisión del fix de parseo en lifecycle 1906

Fecha: 2026-10-19

Alcance: revisión independiente, estática y read-only del candidato completo `supabase/migrations/20261018190600_ep_rec_lifecycle.sql` tras reemplazar las expresiones `pg_catalog.extract(... FROM ...)`. Se revisaron también el test lifecycle y, como contexto de límites/controles compartidos, las migraciones 1904/1905 y el test create/update. Se conserva íntegro el historial T022 y la re-revisión previa de 1905.

### Veredicto

**APPROVED WITH OBSERVATIONS** — no se identifica hallazgo de seguridad estático bloqueante en el candidato 1906 revisado. Este dictamen permite únicamente avanzar al preflight y a la aplicación/pruebas en una instancia local, aislada y descartable, una vez probado ese aislamiento por el gate DevOps. No constituye evidencia de parseo PostgreSQL, ejecución, GREEN, RLS dinámica, ni aprobación hosted o de release.

### Evidencia inspeccionada

- `supabase/migrations/20261018190600_ep_rec_lifecycle.sql:3-6,92-93`: `set_recurrence_status_v1(jsonb)` es `SECURITY DEFINER`, fija `search_path = ''`, revoca ejecución a `PUBLIC`/`anon` y la concede a `authenticated`. La función obtiene identidad de `auth.uid()` (`:9,14`), filtra por `id` y `user_id` y bloquea la fila (`:55-57`); no acepta identidad de propietario del payload.
- `supabase/migrations/20261018190600_ep_rec_lifecycle.sql:64-78`: semanal/quincenal/personalizado-semanal calcula el múltiplo positivo desde `start_date` y avanza otro intervalo si el candidato cae antes de mañana. Las cadencias mensuales/anuales/personalizadas-mensuales calculan delta de meses con `pg_catalog.date_part('year'|'month', ...)::integer` (`:69-75`), manteniendo el anclaje a `start_date` al sumar `make_interval` desde `v_anchor`; si el resultado no es futuro, avanzan un intervalo. El límite inclusivo de `end_date` y la coincidencia exacta con `confirmed_next_due_date` se comprueban antes de cambiar estado/fecha (`:77-79`). El cambio solo reanuda si está PAUSED, y pausa/cancelación tienen transiciones restringidas (`:58-60`).
- La modificación correctiva de las expresiones de año/mes se ajusta al patrón ya presente en `supabase/migrations/20261018190500_ep_rec_create_update.sql:83-84`; la búsqueda estática no encontró `pg_catalog.extract` en la migración 1906. El archivo usa `date_part` en ambas expresiones del delta mensual.
- Integridad de comandos: payload cerrado y campos requeridos (`:15-29`), validación explícita de fecha (`:38-43`), límite de 16,384 bytes canónicos antes de digest/lock/receipt lookup (`:44-49`), idempotencia y conflicto por reuso (`:49-54`), control de `expected_revision` (`:55-60`). Los cambios persisten solo en `public.recurrences`, `internal.sync_changes` y `internal.command_receipts` (`:80-86`); no hay escritura a ocurrencias ni tablas financieras en la migración revisada.
- `supabase/tests/database/recurrence_lifecycle_test.sql:10-41,55-60,97-202`: los tests conservan el rechazo de fecha de reanudación ausente/incorrecta, verifican pausa, conflicto por revisión obsoleta, reanudación confirmada, cancelación terminal, recibos/feed y ausencia de efectos financieros/ocurrencias. No observé relajación de aserciones en ese archivo. El test no ejercita explícitamente anclajes mensuales de fin de mes, cadencia anual ni el rechazo por `end_date`; queda como observación de cobertura para el gate local/QA.
- Las migraciones 1904/1905 y el test `recurrence_create_update_test.sql` fueron consultados para contexto. El estado Git muestra esos candidatos y 1906 como archivos no rastreados, por lo que no existe un diff Git contra una base rastreada que permita demostrar byte a byte el delta de esta corrección o que 1904/1905 no cambiaron desde su creación. Conforme al handoff de Backend, el fix editó solo 1906; la inspección presente confirma estado final, no historial diferencial. No se observó debilitamiento de validaciones en los contenidos consultados.

### Hallazgos y observaciones

- No hay vulnerabilidad confirmada que requiera cambio para este gate. El error SQLSTATE 42601 pertenece al candidato anterior; corregir la sintaxis a `date_part` parece coherente estáticamente, pero el parser aún no se ha ejecutado.
- **Cobertura de calendario pendiente:** el test lifecycle confirma una reanudación mensual simple, pero no prueba directamente anclaje de fin de mes, cadencia anual/personalizada mensual, cruce de año ni el límite de `end_date`. Ejecutar las suites candidatas existentes y registrar estos límites; no afirmar que esas ramas están cubiertas dinámicamente.
- La función devuelve `REJECTED/INVALID_COMMAND` ante cualquier excepción (`:88-89`); el bloque PL/pgSQL revierte los cambios hechos en su subtransacción, pero el comportamiento de atomicidad queda pendiente de prueba runtime.

### Límites de verificación

Esta revisión fue estática y read-only. No ejecuté PostgreSQL parser, migraciones, Supabase, tests ni consultas runtime; no se declara SQL parse/GREEN, RLS dinámica, aislamiento multi-owner, atomicidad ejecutada ni paridad Kotlin/PostgreSQL. El dictamen no sustituye la demostración independiente de instancia local fresh, aislada y descartable; no autoriza hosted, `db push`, ni release.

## Re-revisión hash-bound del conjunto SQL EP-REC

Fecha/hora de captura: 2026-10-19 00:09:24 -0500  
Método: inspección estática read-only de los siete archivos indicados; SHA-256 calculado con `sha256sum` inmediatamente antes de abrir/revisar su contenido y repetido al terminar la inspección. Se compararon ambas listas exactas. No se ejecutaron comandos SQL, parsers, pruebas, Supabase ni servicios externos.

Repositorio: `C:/Users/Alume/orca/Kipu`  
Branch: `007-ep-rec-recurrencias-obligaciones`  
HEAD: `655ab39b64fda565236c04c17cbbcb478c788538`  
Estado Git observado: los siete candidatos SQL están untracked; el repositorio también contiene cambios/untracked de otras áreas que no fueron tocados. No se cambió su estado.

### Veredicto

**APPROVED WITH OBSERVATIONS** — no se identificó un defecto estático bloqueante en el conjunto exacto de bytes cuyos hashes se registran abajo. Este dictamen permite como máximo avanzar al preflight/apply y pruebas en una instancia local demostrablemente aislada y descartable. No autoriza entorno hosted, `db push`, release ni producción; tampoco afirma parser, ejecución, GREEN, RLS dinámica o atomicidad probada.

### Identidad criptográfica de los archivos revisados

SHA-256 antes y después de revisar (las listas coinciden exactamente):

| Archivo | SHA-256 |
|---|---|
| `supabase/migrations/20261018190400_ep_rec_rule_schema.sql` | `d2a3afe321fd6cad0cbcc9854101c936d4686649022bd5ea7b3c188228377eb7` |
| `supabase/migrations/20261018190500_ep_rec_create_update.sql` | `c1dc12d0e1c35dd5c7be90d3a08048f0b659740a22cd85e761d32ae40508b014` |
| `supabase/migrations/20261018190600_ep_rec_lifecycle.sql` | `5622b27a0691a1f1b175fb3d0ce9939df2926fc95816182a8ea6288303d9cd31` |
| `supabase/tests/database/_recurrence_test_fixture.sql` | `59ae33daf5971943781099129802e196549a83cdf9855d6358a4420fa6d78f73` |
| `supabase/tests/database/recurrence_schema_test.sql` | `eec88d8d40fe6347d4d162f8029e2afe17a9e073ea26201209b8bf709ccef404` |
| `supabase/tests/database/recurrence_create_update_test.sql` | `2fb971f3e4c7858d63dbb2f976e3e658fe30e4045a5fb0bc6e0cfa761929e8b5` |
| `supabase/tests/database/recurrence_lifecycle_test.sql` | `e148ac0bdcefaaf73c9b7aedcd90fd7670ec256c1dcf5ca58543207c02b06521` |

### Evidencia de revisión actual

- 1904 define restricciones de intervalo/fechas, activa y fuerza RLS, limita lectura a `auth.uid() = user_id`, revoca escrituras directas a `authenticated` y todo acceso a `anon`/`PUBLIC`. La RLS forzada y los grants se verificaron estáticamente; no se probó su enforcement en runtime.
- 1905/1906 declaran RPCs como `SECURITY DEFINER` con `search_path = ''`; el acceso EXECUTE a RPC queda revocado para `PUBLIC`/`anon` y concedido a `authenticated`, y los helpers internos quedan revocados también a `authenticated`. Las mutaciones de UPDATE/lifecycle buscan por `id` y `user_id=v_uid`; CREATE usa `auth.uid()` como propietario. Las referencias de cuentas/categorías se verifican contra ese UID.
- La validación de comandos verifica estructura/campos permitidos, enums, UUIDs, fechas, enteros, cadencias y referencias; canonicaliza JSON, compara SHA-256 y limita el canonical JSON a 16,384 bytes antes de digest/lock/receipt lookup. Las operaciones usan advisory lock, receipts e idempotencia/conflicto por reutilización. No afirmo protección frente al coste previo de parsear el `jsonb` de entrada.
- Las funciones escriben la fila de recurrencia, `internal.sync_changes` y `internal.command_receipts` dentro de la misma invocación SQL; el bloque de excepción devuelve rechazo. Por semántica estática esto parece transaccional, pero atomicidad ejecutada queda pendiente.
- Las reglas de recurrencia anclan calendario a `start_date`; se validan fechas/candidato y el rango de `end_date`. La reanudación calcula la primera fecha futura, rechaza una fecha confirmada diferente o posterior a `end_date` y limita transiciones (CANCELLED terminal). No se ejecutó validación calendario bajo PostgreSQL.
- Las tres migraciones no crean ni escriben ocurrencias ni tablas financieras; sus escrituras visibles se limitan a recurrencias/feed/receipts. Las suites contienen checks de no-efectos financieros/ocurrencias, ownership/referencias, permisos, hash/idempotencia, revisiones, errores sin residuos y rechazo de UPDATE de 16,385 bytes con hash válido. El fixture emplea identidades/datos sintéticos y cada suite cierra con ROLLBACK. No observé relajación de controles en los tests.

### Hallazgos / observaciones

- **Cobertura de calendario pendiente (INFO):** los tests no cubren explícitamente mes de fin de mes, anualidad/cruce de año ni el rechazo por `end_date` en lifecycle. Mantenerlos como cobertura pendiente; no afirmar que esas ramas están probadas.
- **Límites de evidencia (INFO):** no se ejecutó parser PostgreSQL, SQL, tests, RLS bajo roles, aislamiento multi-owner o atomicidad. Los checks estáticos y el hash-bound no sustituyen esos gates.
- **Hash gate para apply:** el gate posterior debe recalcular estos siete SHA-256 inmediatamente antes de cualquier apply local y detenerse ante cualquier diferencia. Un hash distinto requiere nueva revisión Security.

No se identificaron hallazgos de seguridad confirmados que requieran cambios al código para el gate local permitido. Este resultado preserva las observaciones y límites de T022 y de las re-revisiones previas; no los amplía a runtime o hosted.

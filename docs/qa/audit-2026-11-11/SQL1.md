# SQL1 — supabase/migrations 1–23 (20260915 … 20260923160000) + sus tests

Esta porción crea las cuentas, tarjetas, categorías, preferencias, límites de autenticación y primeras operaciones contables y de sincronización. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y reintentos frecuentes después de trabajar sin conexión. La auditoría es estática sobre `main`: leí completas las 23 migraciones y los 17 tests asignados, sin ejecutar SQL, builds, tests ni solicitudes de red.

## Hallazgos

Ocho hallazgos: una crítica, cinco altas y dos medias. No omití hallazgos menores confirmados. Las ubicaciones indican el cuerpo que sigue vigente; comprobé los nombres de los objetos en las 116 migraciones, incluidas sus modificaciones de permisos y sus sustituciones posteriores.

### [SQL1-01] La segunda corrección de apertura suma dinero de más
- Severidad: crítica
- Categoría: datos
- Ubicación: supabase/migrations/20260921000000_ep_cta_baseline.sql:557–580
- Qué está mal: `record_opening_adjustment_v1` siempre revierte el `OPENING` original, aunque ya haya una corrección vigente. No busca el último ajuste sin reversar ni serializa las correcciones de la cuenta; su cuerpo no fue reemplazado en migraciones posteriores.
- Escenario concreto de fallo: apertura de 10000 céntimos → corrección a 20000 → segunda corrección a 30000: los hechos quedan `10000 - 10000 + 20000 - 10000 + 30000 = 40000`, aunque el saldo corregido debe ser 30000. El trigger posterior `internal.post_opening_movement_ledger_v1` también contabiliza estos hechos, por lo que el error alcanza el ledger del servidor; Android ya calcula su reversión contra el ajuste vigente y diverge del servidor.
- Fix mínimo: bloquear la cuenta, localizar el hecho vigente sin reversión y revertir ese importe/identidad antes de insertar el nuevo ajuste. Mantener el vínculo del ajuste con la apertura original; al coordinarlo con el trigger de contabilización, asegurar que también se reconozcan reversiones de ajustes.
- Confianza: alta
- Cross-módulo: no

### [SQL1-02] Un ajuste confirmado nunca aparece en el feed de sincronización
- Severidad: alta
- Categoría: datos
- Ubicación: supabase/migrations/20260921000000_ep_cta_baseline.sql:565–590
- Qué está mal: la RPC escribe reversión y ajuste, pero no publica ningún cambio en `internal.sync_changes`, no actualiza la revisión de la cuenta y no actualiza su proyección de apertura. No hay un trigger posterior que publique estos cambios: el de `20261026013311_planning_remote_sync_activation.sql:37–67` solo escribe el ledger.
- Escenario concreto de fallo: dos dispositivos ya sincronizaron una apertura de S/100; A la corrige a S/200 y recibe `APPLIED`. B solicita cambios posteriores a su cursor y no recibe ningún evento por esa corrección; conserva S/100 mientras el servidor ya contabiliza S/200. Una restauración desde el feed también recibe la apertura anterior.
- Fix mínimo: publicar dentro de la misma transacción una proyección versionada de la apertura corregida que incluya sus hechos de reversión/ajuste. Acordar su consumo con MOV2; reenviar únicamente el importe original de `accounts` no restaura esos hechos.
- Confianza: alta
- Cross-módulo: no

### [SQL1-03] Reintentar una corrección confirmada devuelve una violación de unicidad
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20260921000000_ep_cta_baseline.sql:546–590
- Qué está mal: `record_opening_adjustment_v1` recibe `operation_id` e identidades estables de ambos movimientos, pero no consulta ni guarda un recibo de operación. El reintento vuelve a ejecutar los dos `INSERT`.
- Escenario concreto de fallo: el servidor confirma el ajuste, pero se pierde la respuesta HTTP; el outbox vuelve a mandar exactamente el mismo comando. El primer `INSERT` colisiona con la clave primaria o con `(user_id, operation_id, operation_sequence)` y devuelve `23505`, en vez del éxito original; el cliente no puede conciliar la operación que ya se aplicó.
- Fix mínimo: guardar el resultado y el hash del contenido en `internal.command_receipts` y consultar ese recibo bajo un bloqueo por operación antes de escribir. Un reintento idéntico debe devolver el resultado guardado y un contenido diferente debe producir una colisión explícita.
- Confianza: alta
- Cross-módulo: no

### [SQL1-04] Resolver un conflicto descarta la versión elegida
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20260923010000_fix_category_conflict_resolution.sql:11–30
- Qué está mal: `chosen_version` solo se valida; nunca se lee `local_version`/`remote_version` ni se aplica la versión seleccionada a la presentación o al estado de la categoría. Se marca el conflicto como resuelto sin resolver los datos, contrario a FR-018 de EP-CCO.
- Escenario concreto de fallo: la presentación remota dice «Educación» y la versión local guardada en el conflicto dice «Universidad»; el usuario elige `LOCAL`. La RPC devuelve `RESOLVED`, pero la presentación sigue diciendo «Educación»; el siguiente pull conserva el valor que el usuario rechazó. El llamador Android solo envía la elección, no otro comando que aplique esa versión.
- Fix mínimo: bloquear el conflicto, aplicar el contenido elegido a la entidad correspondiente con su revisión y marcarlo resuelto en esa misma transacción. El test actual `category_merchant.test.sql:223–232` debe comprobar el valor resultante, además de `RESOLVED`.
- Confianza: alta
- Cross-módulo: no

### [SQL1-05] Dos dispositivos pueden sobrescribir una presentación sin conflicto
- Severidad: alta
- Categoría: concurrencia
- Ubicación: supabase/migrations/20260922000000_ep_cco_baseline.sql:246–279
- Qué está mal: `update_category_presentation_v1` lee la revisión sin bloqueo y actualiza después sin comprobar la revisión en el `WHERE`. Dos llamadas pueden validar la misma revisión y ambas escribir la misma revisión siguiente; la migración de hardening posterior solo fija `search_path` y permisos, no cambia este cuerpo.
- Escenario concreto de fallo: presentación en revisión 1; A quiere «Casa» y B quiere «Hogar», ambos con `expected_revision=1`. Ambos leen 1 antes de escribir; A guarda revisión 2 y B espera su `UPDATE`, luego sobrescribe con «Hogar», también revisión 2; ambas llamadas responden `UPDATED` y no se conserva ningún conflicto de A.
- Fix mínimo: añadir `FOR UPDATE` a la lectura de la presentación antes de comparar revisiones. Probar dos sesiones que intenten editar la misma revisión: una debe actualizar y la otra debe conservar ambas versiones como conflicto.
- Confianza: alta
- Cross-módulo: no

### [SQL1-06] Un reintento de presentación se convierte en un conflicto ficticio
- Severidad: alta
- Categoría: bug
- Ubicación: supabase/migrations/20260922000000_ep_cco_baseline.sql:234–269
- Qué está mal: `update_category_presentation_v1` ignora la identidad de operación del comando y no conserva su resultado. Cada reintento compara nuevamente la revisión inicial contra una revisión que la propia operación ya incrementó.
- Escenario concreto de fallo: una edición con `operation_id=X` y revisión esperada 1 se aplica como revisión 2; se pierde la respuesta. El mismo outbox reenvía X y el servidor crea un conflicto `OPEN`, aunque no hubo ninguna segunda edición; Android interpreta ese resultado como conflicto y detiene la sincronización normal de la edición.
- Fix mínimo: deduplicar por propietario e identidad de operación con hash y resultado persistido antes de comparar revisiones. El caso mínimo de test es repetir exactamente una actualización aplicada y comprobar que no aparece ningún conflicto ni otro incremento de revisión.
- Confianza: alta
- Cross-módulo: no

### [SQL1-07] Dos primeras peticiones del mismo bucket provocan un rechazo falso
- Severidad: media
- Categoría: concurrencia
- Ubicación: supabase/migrations/20260920170000_create_auth_rate_buckets.sql:36–41,78–81
- Qué está mal: `FOR UPDATE` no bloquea una fila inexistente. Si dos llamadas leen el mismo bucket nuevo, ambas van al `INSERT` sin manejo de colisión; este helper sigue vigente y también lo usa el wrapper de cuatro argumentos restaurado posteriormente.
- Escenario concreto de fallo: dos inicios de sesión simultáneos desde un origen cuyo bucket aún no existe leen «sin fila»; una inserción gana y la otra falla con `23505`. `auth-access` convierte ese fallo del limitador en una denegación de 60 segundos, aunque solo se consumió un intento y el máximo normal es cinco.
- Fix mínimo: crear primero el bucket con `INSERT ... ON CONFLICT DO NOTHING` y después bloquearlo e incrementar el contador, preservando que la primera llamada cuente como un intento. Test mínimo: dos sesiones con la misma clave nueva devuelven resultados válidos y dejan contador 2.
- Confianza: alta
- Cross-módulo: no

### [SQL1-08] La deduplicación de selección de plan ocurre antes del bloqueo
- Severidad: media
- Categoría: concurrencia
- Ubicación: supabase/migrations/20260915000000_create_plan_selection.sql:67–77
- Qué está mal: `apply_plan_selection` lee el recibo antes de tomar el bloqueo de la cabecera y no vuelve a leerlo después de esperar. La cabecera serializa las revisiones, pero el valor `known` sigue siendo el que se leyó antes del bloqueo.
- Escenario concreto de fallo: dos solicitudes concurrentes llevan el mismo propietario, operación y revisión; ambas leen que no hay recibo. La primera confirma; la segunda obtiene después el bloqueo, calcula `CONFLICT` con su copia vacía de `known` e intenta insertar otra vez el mismo recibo, fallando con `23505` en vez de devolver `DUPLICATE`. Los tests existentes solo reenvían en secuencia.
- Fix mínimo: mover la lectura del recibo después del `SELECT ... FOR UPDATE` de la cabecera, como ya hace el flujo de selección de cupos. Test mínimo: dos sesiones enviando exactamente la misma operación deben producir `APPLIED`/`DUPLICATE` y un solo recibo.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `20260921000000_ep_cta_baseline.sql:524`, `record_opening_adjustment_v1` → apertura 10000, corrección 20000 y corrección 30000: exigir saldo final 30000 en movimientos y ledger. `financial_accounts.test.sql:8` solo comprueba que la función exista.
- La misma RPC → perder la respuesta y reenviar exactamente el comando; exigir el resultado original y un único par de hechos. Añadir además una comprobación de que un cursor anterior obtiene la corrección confirmada.
- `20260923010000_fix_category_conflict_resolution.sql:1` → elegir `LOCAL` y comprobar que cambia el nombre/estado real; el test actual solo comprueba la etiqueta `RESOLVED`. Repetir esa resolución con la misma operación también debe tener éxito: actualmente el filtro `status='OPEN'` produce `P0002` después de una respuesta perdida.
- `20260922000000_ep_cco_baseline.sql:230`, `update_category_presentation_v1` → dos sesiones editan revisión 1 y se conserva la edición perdedora como conflicto; repetir una misma operación aplicada no debe crear un conflicto ficticio.
- `20260920170000_create_auth_rate_buckets.sql:19` y `20260915000000_create_plan_selection.sql:59` → los casos concurrentes concretos de SQL1-07 y SQL1-08. Sus tests actuales cubren consumo/reintentos secuenciales.
- `movements_idempotency_stress_test.sql:25–42` ejecuta los 100 reintentos dentro de una misma sesión/transacción. Es útil para replay secuencial, pero no demuestra que dos dispositivos compitiendo por la misma operación reciban una respuesta idempotente; el test mínimo faltante usa dos conexiones y comprueba un solo efecto y respuestas conciliables. No atribuyo a SQL1 un defecto en la implementación final de registro de movimientos, que fue envuelta/redefinida en SQL2/SQL3/SQL4.
- `profile_preferences_test.sql:4–22` solo comprueba estructura y privilegios. Falta al menos un caso que llame `ensure_profile`, actualice una preferencia, repita la operación y verifique el valor y la revisión; la implementación final está en `20261103172646_profile_preferences_private_usage.sql` (SQL4), por lo que no se auditó aquí como función propia.

## Lo que no revisé

- Ningún archivo del alcance quedó sin leer completo: 23 migraciones y 17 tests.
- Leí fuera del rango únicamente las sustituciones, grants/policies, triggers y consumidores necesarios para determinar si un defecto seguía vigente. No hice una auditoría independiente de esas migraciones ni de Kotlin/Edge Functions; sus dueños conservan esos módulos.
- No ejecuté pgTAP, SQL local/remoto, Supabase CLI/MCP, Gradle, dispositivos ni red. Las secuencias de fallo son deducciones del flujo SQL, no resultados de una ejecución.
- No reporté como vigentes la exposición original de vistas, la política permisiva de comercios, el bypass del RPC financiero sin versión ni los primeros cálculos de cuotas: las migraciones posteriores cambian expresamente esos caminos. Tampoco confundí los tests de existencia con pruebas funcionales de esos RPC.
- El estado de trabajo ya contenía archivos sin seguimiento de otros trabajadores; únicamente creé este informe.

## Dudas

- No queda una duda de producto que impida describir los ocho fallos: la corrección debe sustituir el saldo vigente, la sincronización debe converger y FR-018 exige aplicar la versión elegida.
- El transporte de los hechos de apertura corregida debe acordarse con MOV2/ACC5 antes de implementar SQL1-02; el feed actual no tiene un evento de reversión/ajuste consumido por el hidratador. Esto es una decisión de contrato para la corrección, no evidencia de que el comportamiento actual sea intencional.

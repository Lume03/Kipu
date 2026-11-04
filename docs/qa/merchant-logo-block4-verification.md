# Bloque 4 - Comercios compartidos y logos

Veredicto: **APPROVED (implementacion y verificacion local)**; revision integral y aceptacion remota pendientes.

Fecha: 2026-11-04. Alcance: implementacion y pruebas locales; pendiente revision integral del coordinador. Sin commits, push, merge ni despliegue remoto. Se conservaron los cambios existentes de Terra y del bloque 3 (incluida Room 38); este bloque incrementa Room a 39.

## Diagnostico y recorrido

- **Busqueda y seleccion:** `MerchantDiscoveryViewModel`/picker distinguen un candidato de internet, un comercio existente y un nombre privado/provisional. Solo el candidato cacheado y autorizado por el servidor puede crear un comercio global. No se promueven nombres de personas ni pagadores privados. Elegir un candidato devuelve su ID; `QuickMovementViewModel` y el editor conservan `merchant_id`; el texto provisional sigue siendo una ruta distinta.
- **Alta remota:** M3 ya persistia `USER_ADDED` con `is_system=true`, propietario nulo y dominio, deduplicando por dominio con bloqueo e indice. Faltaba un interruptor operativo para desactivar nuevas altas. No se amplia la publicacion a texto libre.
- **Logo pendiente:** el RPC anterior devolvia `needs_logo_sync` solo en la primera insercion. Si esa descarga fallaba, repetir el alta nunca la recuperaba. Ahora los pendientes, fallidos y aun indexandose pueden reintentar tras cinco minutos desde el ultimo chequeo (inmediato si nunca se comprobo), sin consumir otra alta. READY con ruta y MANUAL no se descargan de nuevo. La seleccion local de un comercio existente sigue sin invocar el proveedor; el mantenimiento existente o un nuevo `add` autorizado recupera sus pendientes.
- **Respuesta fresca:** despues de sincronizar el logo, el handler buscaba el ID recorriendo todo el catalogo REST, sujeto al limite de filas. Ahora consulta el ID exacto y devuelve sus rutas actuales. Un fallo del proveedor conserva el ID/dominio sin inventar un logo ni guardar el preview como activo duradero.
- **Persistencia e hidratacion Android:** `logo_path`, variante oscura y fuente ya se guardaban. Se perdian dominio, estado y origen al deserializar/hidratar; ahora viajan por DTO/API y Room 39, tanto en alta inmediata como en sincronizacion. Se conserva la version 0 del alta inmediata para no saltar filas aun no hidratadas.
- **Render:** el historial priorizaba icono de categoria frente a iniciales cuando faltaba logo, y usaba categoria tambien durante carga/error. Para `merchant_id` vinculado ahora utiliza las mismas iniciales deterministas del selector. Pagadores sin vinculo mantienen semantica de categoria/billetera. El detalle de tarjeta ademas consultaba el catalogo sin propietario y excluia comercios privados legitimos: ahora observa por propietario y conserva el ID en `CardMovementDisplay`.

Estos fallos se verificaron en codigo/pruebas locales. No se inspecciono la fila real MINISO del usuario ni su bucket remoto: no se atribuye el incidente concreto a una respuesta de proveedor no observada. Un MINISO creado como privado o provisional sin dominio no permite inferir una marca ni fabricar un logo.

## Desactivacion y reparacion conservadora

Aplicar despues de M3: `20261104053612_merchant_discovery_logo_recovery.sql`.

```sql
-- Operador SQL / service_role; nunca desde el cliente Android.
UPDATE internal.merchant_discovery_settings
SET shared_additions_enabled = false WHERE singleton;
```

Valor inicial `true`, conforme a autorizacion del propietario para la etapa inicial. `false` rechaza solo nuevas publicaciones compartidas con `SHARED_MERCHANT_ADDS_DISABLED`; Android muestra una alternativa privada. No elimina ni oculta catalogo existente, no bloquea seleccion de existentes ni altas privadas. Los clientes no pueden cambiar el interruptor. La comprobacion bloquea la fila del ajuste hasta commit para serializarse con el apagado.

La migracion recupera una ruta faltante unicamente desde una fila global activa con **el mismo dominio ya acreditado** y logo READY. No modifica IDs, asociaciones de compras/alias, nombres privados, logos MANUAL ni registros identificados solo por nombre. Una fila historica MINISO sin dominio debe conservarse hasta que el usuario seleccione explicitamente el comercio correcto; la edicion existente permite hacerlo sin reasignaciones masivas. No se adivina que dos nombres iguales sean la misma entidad.

## Archivos del bloque

- Android datos: `CategoryEntities.kt`, `CategoryDtos.kt`, `CategoriesApi.kt`, `MerchantDiscoveryRepositoryImpl.kt`, seccion de comercios de `CategoryHydration.kt`.
- Room: `KipuDatabase.kt`, `RoomMigrations.kt` (`MIGRATION_38_39`), `CoreModule.kt`, esquema `39.json`; se preserva migracion y esquema 38 del bloque 3.
- Presentacion: `MovementAvatarPolicy.kt`, `MovementHistoryScreen.kt`, `AccountDetailScreen.kt`, `CardMovementDisplay.kt`, `OfflineFirstFinancialInstrumentsRepository.kt` (observacion del catalogo de tarjeta).
- Servidor: `supabase/functions/merchant-search/index.ts`, migracion nueva indicada arriba.
- Pruebas: `MerchantDiscoveryRepositoryTest.kt`, nuevo `MerchantLogoPersistenceTest.kt`, `MovementAvatarPolicyTest.kt`, `merchant-search/index_test.ts`, nuevos `merchant_logo_block4.test.sql` y `merchant_logo_repair_block4.test.sql`; expectativa M3 actualizada para un pendiente reintentable.
- Este informe. No se edito `tesis.md` ni se revirtieron modificaciones ajenas del picker/perfil/categorias.

## Verificacion

- Android: `./gradlew.bat :app:testDebugUnitTest --tests '*MerchantDiscovery*' --tests '*MerchantLogoPersistenceTest' --tests '*MerchantPickerDiscoveryTest' --tests '*MovementAvatarPolicyTest' --tests '*MovementPresentationObservationTest' :app:compileDebugAndroidTestKotlin --console=plain`: **BUILD SUCCESSFUL, exit 0**, 24 s, 48 tareas; **23 pruebas, 0 fallos, 0 errores**. Incluye los seis casos existentes del picker de Terra, persistencia/reapertura en archivo, relacion de compra, migracion conservadora y politica visual. No se ejecuto suite global ni instrumentados en dispositivo.
- Durante la iteracion se corrigieron fixtures nuevos: entorno Android para `Uri.encode`, ruta absoluta temporal SQLite en Windows, cuenta/categoria validas de la compra y su FK. Los fallos intermedios no se presentan como evidencia positiva; la ejecucion final anterior paso.
- `git diff --check` acotado a los archivos de codigo/SQL del bloque: exit 0. El chequeo general conserva un aviso de linea vacia final preexistente en `MerchantPicker.kt` de Terra; no se modifica ese trabajo para limpiar formato.

- Deno: `deno test supabase/functions/merchant-search/index_test.ts supabase/functions/merchant-logo-sync/index_test.ts`: **41/41 PASS**, exit 0; sin permisos de red. Incluye respuesta fresca por ID, fallo sin inventar logo y apagado.
- Migracion aplicada con `Get-Content <migration> | docker exec -i supabase_db_kipu-met-local-20261017 psql -U postgres -d postgres -v ON_ERROR_STOP=1`: exit 0. Solo Docker local; reparacion de datos locales existentes: UPDATE 0.
- pgTAP con el mismo pipe/`psql` para `merchant_logo_block4.test.sql`: **17/17 PASS**; `merchant_logo_repair_block4.test.sql`: **5/5 PASS**; regresion `merchant_discovery_test.sql`: **20/20 PASS**. Sin `not ok`; fixtures en transacciones revertidas.
- Logs locales excluidos de Git: `.run/block4-migration.log`, `.run/block4-pgtap.log`, `.run/block4-repair-pgtap.log`, `.run/block4-m3-pgtap.log`, `.run/block4-deno.log`, `.run/block4-gradle-final.log`.

## Limites

No se realizaron llamadas reales a Logo.dev, comprobacion visual en hardware ni prueba de dos sesiones contra Supabase remoto. Los casos de dos cuentas se comprobaron con roles/JWT simulados en PostgreSQL local y con visibilidad Room. La prueba de reapertura cierra/abre una base de archivo real bajo Robolectric y vuelve a resolver la compra por `merchant_id`; no certifica el cache de imagenes de un telefono. Compilar instrumentados no equivale a ejecutarlos. El 401 remoto informado antes del bloque no demuestra despliegue: falta aplicar migracion y desplegar `merchant-search` en el entorno objetivo, comprobar credenciales del proveedor y validar MINISO con dos cuentas alli. Ninguna credencial se incluyo en este informe.

# Bloque 3 - categorias y subcategorias

Fecha de ejecucion: 2026-11-04. Veredicto: **APPROVED para el alcance local focalizado**. Revision integral del coordinador y despliegue remoto pendientes; sin commit, push ni merge.

## Causa y evidencia

- `20260923140000_category_types.sql` convirtio las categorias historicas a `GENERAL`, conservando asociaciones. El seed posterior `20260929235340_merchant_catalog_priority_a.sql` tambien inserto explicitamente como `GENERAL` las 12 raices y 39 subcategorias conocidas. `InitialCategoryCatalog` reproducia ese tipo. Los filtros de gestion y creacion incluian correctamente `GENERAL` en ambas pestanas: el error estaba en la clasificacion del catalogo, no en la regla de las categorias personalizadas compartidas.
- Antes de corregir Docker, `select category_type,count(*) from public.categories group by 1` devolvio `GENERAL | 51`. Esto es evidencia local, no remota.
- `ensureInitialCatalog` creaba las presentaciones con `category` y `#757575`; la hidratacion insertaba solo si faltaban y asignaba siempre gris. La tarjeta de gestion aplicaba un parche por nombre unicamente para Alimentacion/Transporte/Servicios, dejando las otras nueve raices grises. Se retiro ese parche local a favor de la presentacion comun.
- `resolveCategoryIcon` ya resolvia los iconos de las raices, pero faltaban 11 claves del seed para subcategorias: delivery_dining, toll, local_fire_department, cloud, smart_toy, terminal, shopping_bag, confirmation_number, hotel, security y assignment.
- El editor recibia todas las categorias sin filtro por tipo; creacion ya filtraba. Ambos selectores ahora comparten la misma regla y el editor rechaza selecciones incompatibles tambien en su callback.
- La revision se basa en `docs/README.md`, `docs/control-documental.md`, la constitucion y EP-CCO. La correccion explicita del catalogo conocido no infiere tipos por nombres ni por movimientos de categorias personalizadas, ni inventa categorias de ingreso.

## Cambios e impacto en datos

- Catalogo Android completo con los mismos 51 IDs, nombres, jerarquia e iconos del seed; tipo `EXPENSE` y colores de presentacion definidos con la paleta existente. El onboarding sigue exponiendo solo las 12 raices. Inicializacion offline incorpora ahora tambien los 39 hijos.
- `CategoryItem` proporciona la presentacion predeterminada comun cuando falta y reconoce los placeholders v1 exactos. Nombres, iconos y colores personalizados distintos del placeholder se conservan. Un tuple indistinguible del placeholder v1 (nombre canonico, category o icono canonico y gris) se considera predeterminado; no existe procedencia historica que permita diferenciar una eleccion manual identica.
- Hidratacion evita reintroducir `GENERAL` desde un servidor todavia sin migrar en las ramas conocidas; conserva raices personalizadas `INCOME`/`GENERAL`. Repara caches grises generadas solo sin presentacion personal remota ni comando local pendiente, incluso con revision de cache superior a 1. Una presentacion personal remota puede reemplazar el placeholder generado aunque su revision sea menor que la revision del catalogo que se uso erroneamente para generar esa cache.
- Room 37 a 38 corrige solo raices SYSTEM conocidas y sus hijos. Se exporto `app/schemas/com.kipu.app.core.database.KipuDatabase/38.json` y se registro la migracion en CoreModule. No reescribe presentaciones, movimientos ni flags activos.
- `20261104051901_system_category_expense_catalog.sql` corrige esas ramas atomicamente; incrementa las revisiones de las filas cuyo tipo cambia y restaura el trigger que protege la herencia. Los hijos personalizados de una raiz SYSTEM corregida heredan `EXPENSE`, conservando ID, propietario, nombre y asociaciones; las raices personalizadas no cambian.
- Las asociaciones historicas, incluso ingresos vinculados antes a una categoria del catalogo, siguen intactas y admiten ediciones que mantengan categoria y tipo de movimiento. Nuevas asociaciones incompatibles se rechazan. El RPC de revision se alinea con la excepcion historica ya existente en `MovementRevisionPlanner`.
- Los creates de subcategorias pendientes de clientes anteriores, enviados con `GENERAL` bajo una de esas raices conocidas, heredan el tipo corregido. Los recibos antiguos de esas operaciones se reconocen unicamente si todo el payload restante coincide; no se reescriben ni se duplican filas y los cambios reales de payload siguen rechazandose.
- La migracion parchea dos funciones grandes mediante sustituciones acotadas de su definicion, comprueba el contrato esperado y falla si no coincide. Conserva firmas y privilegios. No cambia otros contratos ni desactiva permanentemente validaciones.

## Validacion ejecutada

### Android/JVM

Comando final (ejecutado mediante cmd.exe para conservar el exit code nativo):

```bat
.\gradlew.bat :app:testDebugUnitTest --tests com.kipu.app.feature.categories.data.CategoryCatalogHydrationTest --tests com.kipu.app.feature.categories.presentation.CategoryCatalogRegressionTest --tests com.kipu.app.feature.categories.data.CategoryCatalogMigrationTest --tests com.kipu.app.feature.movements.presentation.MovementCategoryOptionsTest --tests com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepositoryTest --tests com.kipu.app.feature.categories.presentation.categories.CategoryTabTest --tests com.kipu.app.feature.movements.presentation.QuickMovementViewModelTest --tests com.kipu.app.feature.movements.presentation.MovementEditorRememberTest --tests com.kipu.app.feature.movements.domain.MovementRevisionTest --console=plain
```

Resultado: **exit 0, BUILD SUCCESSFUL in 27s; 85 tests, 0 failures/errors**. Log local: `_local-only/category-block3-gradle-final.log`. Los XML de `app/build/test-results/testDebugUnitTest/` contienen respectivamente 1, 3, 1, 2, 10, 1, 42, 5 y 20 tests. Incluye iconos y presentacion de las 51 entradas, separacion de raices/hijos por tipo, categoria historica retenida en el VM de edicion INCOME, rechazo de nueva seleccion incompatible, reparacion Room y proteccion de presentacion personal/pending durante hidratacion.

### PostgreSQL/pgTAP

Base utilizada: contenedor **supabase_db_kipu-met-local-20261017**, base postgres. La migracion nueva se aplico por psql con `ON_ERROR_STOP=1`; primera aplicacion: 12 raices identificadas y 51 filas actualizadas. No se registro historial de migraciones del CLI: estas son verificaciones directas de esquema/datos local, no un replay limpio desde cero.

La prueba de revisiones detecto inicialmente el indice UNIQUE obsoleto `internal.ledger_entries_transaction_account_role`; el mismo fallo 23505 se reprodujo restaurando temporalmente la funcion de revision baseline dentro de una transaccion descartada. Se aplico exclusivamente a Docker la correccion **ya versionada** `20261102013425_allow_append_only_movement_revisions.sql` (sin editar ese archivo), tras lo cual la prueba paso.

Comandos de aplicacion:

```powershell
Get-Content -Raw -Encoding utf8 supabase/migrations/20261104051901_system_category_expense_catalog.sql | docker exec -i supabase_db_kipu-met-local-20261017 psql -U postgres -d postgres -v ON_ERROR_STOP=1
Get-Content -Raw -Encoding utf8 supabase/migrations/20261102013425_allow_append_only_movement_revisions.sql | docker exec -i supabase_db_kipu-met-local-20261017 psql -U postgres -d postgres -v ON_ERROR_STOP=1
```

Se copiaron los tests de `supabase/tests/database/` a `/tmp/` del contenedor con `docker cp`. Comando final:

```powershell
$tapOutput = docker exec supabase_db_kipu-met-local-20261017 psql -U postgres -d postgres -v ON_ERROR_STOP=1 -At -f /tmp/category_type_test.sql -f /tmp/category_merchant.test.sql -f /tmp/system_category_catalog.test.sql -f /tmp/movements_revision_test.sql -f /tmp/category-block3-migration-fixture.sql
$tapOutput | Select-String '^(1\.\.|not ok|# Looks like)'
Write-Output "Passed pgTAP assertions: $(@($tapOutput | Select-String '^ok ').Count)"
if ($LASTEXITCODE -ne 0 -or ($tapOutput -match '^not ok') -or ($tapOutput -match '^# Looks like')) { exit 1 }
```

Resultado: **exit 0; 110 aserciones pgTAP** = 14 tipos + 22 categorias/comercios + 20 catalogo/reintentos/historico + 47 revision + 7 fixtures de migracion. `pg_prove` no esta instalado en el contenedor; se comprobo TAP usando psql y se convirtio cualquier `not ok` o fallo del plan en exit 1.

Fixture local: `_local-only/category-block3-migration-fixture.sql`. Ejecuta el contenido real de la migracion dentro de una transaccion con rollback, sobre catalogo legacy GENERAL y datos preparados. Verifica igualdad de transacciones y presentaciones mediante EXCEPT, conservacion de raiz personalizada llamada Compras, de flag oculto y de trigger habilitado, y herencia del hijo personalizado. No conserva esos datos sinteticos en la base.

`git diff --check` de los archivos propios: exit 0. El diff completo detecta una linea vacia final preexistente en MerchantPicker, archivo ajeno que se preservo.

## Limites y entrega

- Intento de lectura REST remota con la configuracion existente: **HTTP 401**. No se verifico catalogo ni migraciones desplegadas; no hubo cambios remotos. El coordinador debe revisar y desplegar la nueva migracion por el procedimiento del proyecto.
- No se ejecuto validacion visual mediante capturas, app instalada ni dispositivo; las pruebas de presentacion son JVM/Robolectric y verifican resolucion de iconos/datos. No se ejecuto la suite global de Android.
- No se crearon categorias de ingreso nuevas: sin raices propias INCOME/GENERAL, la pestana Ingresos puede quedar vacia y ofrece la creacion existente.
- No se modificaron MerchantPicker.kt, ProfileSettingsScreen.kt, MerchantPickerDiscoveryTest.kt ni `specs/001-ep-aps-acceso-perfil-seguridad/tesis.md`. No se ejecuto workflow Spec Kit, ni hubo cambio de rama/worktree, workers adicionales, commits, push o merge.

## Archivos propios entregados

- `app/schemas/com.kipu.app.core.database.KipuDatabase/38.json`
- `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`
- `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`
- `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`
- `app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt`
- `app/src/main/java/com/kipu/app/feature/categories/domain/model/InitialCategoryCatalog.kt`
- `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/ObserveCategories.kt`
- `app/src/main/java/com/kipu/app/feature/categories/presentation/CategoryDesignTokens.kt`
- `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementCategoryOptions.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorSheet.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/CategoryCatalogHydrationTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/CategoryCatalogMigrationTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepositoryTest.kt`
- `app/src/test/java/com/kipu/app/feature/categories/presentation/CategoryCatalogRegressionTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/MovementCategoryOptionsTest.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/MovementEditorRememberTest.kt`
- `docs/qa/category-catalog-block3-verification.md`
- `supabase/migrations/20261104051901_system_category_expense_catalog.sql`
- `supabase/tests/database/system_category_catalog.test.sql`

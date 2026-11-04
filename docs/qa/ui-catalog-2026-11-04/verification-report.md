# Filtros, categorias y comercios - verificacion integrada

Fecha: 2026-11-04. Veredicto: **APPROVED para implementacion y validacion local**.
No certifica despliegue remoto, reparacion del MINISO real ni llamadas reales a Logo.dev.

## Resultado

- MerchantPicker reutiliza OptionFilterButton/OptionFilterSheet de notificaciones. Se retiro la fila horizontal de chips; busqueda, filtro, limpieza y seleccion conservan su comportamiento.
- Ajustes diferencia plan, importacion, capturas, privacidad y configuracion mediante iconos semanticos existentes.
- Las 12 raices y 39 subcategorias SYSTEM conocidas son EXPENSE, con presentacion predeterminada completa y claves de icono resueltas. Creacion y edicion filtran por tipo. GENERAL personalizado continua siendo compartido deliberadamente.
- Room 37 a 38 y PostgreSQL preservan IDs, asociaciones historicas y personalizaciones. No se invento un catalogo predeterminado de ingresos: una cuenta sin categorias INCOME/GENERAL propias puede mostrar esa pestana vacia y usar la creacion existente.
- Room 38 a 39 conserva dominio, estado y origen del comercio. La respuesta de alta refresca por ID, y logos pendientes pueden reintentarse con cooldown. Historial y tarjeta usan iniciales coherentes cuando un comercio vinculado no tiene logo.
- El alta global solo acepta candidatos del proveedor autorizados para el usuario; los pagadores privados permanecen privados. El interruptor operativo permite detener nuevas altas compartidas sin eliminar el catalogo.

## Evidencia revisada

| Alcance | Resultado | Evidencia |
| --- | --- | --- |
| Filtros | 10 pruebas JVM iniciales en verde | MerchantPickerDiscoveryTest y UiMerchantGroupTest |
| Categorias | 85 pruebas Android/JVM, 110 aserciones pgTAP, exit 0 | [Informe bloque 3](../category-catalog-block3-verification.md) |
| Comercios | 23 pruebas Android, 41 Deno, 42 aserciones pgTAP, exit 0 | [Informe bloque 4](../merchant-logo-block4-verification.md) |
| Telefono SM-S926B | OK (3 tests), aplicacion aislada com.kipu.app.lab | PickerSettingsDeviceTest y CategoriesScreenTest#capturesCompactCategoryTabsAndExpandedBranch |
| Revision final | Codigo y migraciones revisados por coordinador; git diff --check exit 0 | Rama actual main, sin cambio de rama/worktree |

Los conjuntos Android se solapan; no sumar sus cantidades como pruebas unicas. La compilacion de instrumentados por si sola no se conto como ejecucion.

La ejecucion en telefono construyo `-PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest` y uso `adb shell am instrument` contra `com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner`. El test abre el picker y su filtro anidado con fixtures; no reproduce la navegacion completa desde Registrar movimiento. No se borraron datos ni se cambio la cuenta de la app real.

Las siete capturas permanecen fuera de Git en `_local-only/block2-device-validation/`. El coordinador inspecciono picker, filtro, acciones de ajustes y pestanas/expansion de categorias. Las capturas de categorias son fixtures personalizados, no prueba visual de las 51 entradas SYSTEM; esa cobertura es automatizada. No se certifican todos los tamanos, fuentes, orientaciones ni temas.

## Estado remoto y limites

- La lectura REST intentada por el worker devolvio HTTP 401. No se comprobaron filas remotas de MINISO ni se desplegaron migraciones/Edge Functions.
- Pendientes de despliegue: `20261104051901_system_category_expense_catalog.sql`, `20261104053612_merchant_discovery_logo_recovery.sql` y `merchant-search`, respetando dependencias existentes. Las pruebas de dos cuentas fueron locales con roles/JWT de fixture.
- Un nombre privado/provisional sin dominio o merchant_id no identifica por si solo una marca. No se reasignan compras por coincidencia de nombre ni se fabrican logos. El usuario puede seleccionar explicitamente el comercio correcto en la edicion existente.
- Las pruebas locales usaron Docker con migraciones aplicadas directamente, no un replay limpio de todo el historial. El indice legacy que bloqueaba revisiones se corrigio localmente con la migracion ya versionada 20261102013425, sin editarla.
- No se ejecutaron Spec Kit ni refactors adicionales del inventario de reutilizacion. El archivo ajeno `specs/001-ep-aps-acceso-perfil-seguridad/tesis.md` permanece intacto y excluido de los commits.

## Reutilizacion

Ver [auditoria de componentes](reusable-components-audit.md). Las recomendaciones restantes son solo informe.

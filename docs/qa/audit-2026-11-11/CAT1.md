# CAT1 — categories: domain + data + presentation/merchantrules + edge functions de comercios

Esta porción administra categorías, reglas y catálogo de comercios en almacenamiento local y los sincroniza con el servidor. Las funciones de comercios consultan y publican logos. Se asumieron miles de usuarios, uno o dos dispositivos por usuario y uso offline frecuente.

## Hallazgos

### [CAT1-01] Un conflicto remoto recibe un identificador local inventado
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:118-133; app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:935-984
- Qué está mal: Ante un conflicto de presentación, el servidor crea su propio registro, pero el worker crea otro con un UUID aleatorio y sin la versión remota real. La resolución envía ese UUID local al servidor y oculta el conflicto local antes de confirmar el resultado.
- Escenario concreto de fallo: Dos dispositivos editan la misma presentación con revisiones distintas → el segundo ve un conflicto; al resolverlo, el servidor no encuentra el UUID inventado y el conflicto desaparece localmente sin resolverse.
- Fix mínimo: Recuperar el conflicto remoto con su ID y versiones reales, y marcarlo resuelto localmente solo después de aceptar la resolución remota.
- Confianza: alta
- Cross-módulo: no

### [CAT1-02] La cola puede quedar pendiente sin otro worker
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/data/sync/CategorySyncScheduler.kt:27-40; app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:83-105
- Qué está mal: El worker toma una sola instantánea de comandos y el programador usa KEEP, que descarta una nueva solicitud mientras existe trabajo activo.
- Escenario concreto de fallo: El usuario edita una categoría después de que el worker leyó la cola pero antes de terminar → KEEP descarta el nuevo trabajo, el worker acaba y el comando queda pendiente hasta otro evento que programe sincronización.
- Fix mínimo: Encadenar una ejecución posterior cuando se agrega trabajo durante una ejecución, o usar un mecanismo que garantice un nuevo drenaje tras el worker activo.
- Confianza: alta
- Cross-módulo: no

### [CAT1-03] Cambios consecutivos de reglas quedan en conflicto o marcados como sincronizados prematuramente
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:581-615,698-734; app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:156-179,213-250
- Qué está mal: Cada edición offline usa la revisión local optimista y la respuesta de cada comando cambia el estado y la revisión de la fila actual sin comprobar si ya existe una edición posterior.
- Escenario concreto de fallo: Crear una regla, editarla y borrarla offline antes de sincronizar hace que el primer acuse reemplace la revisión de la fila más reciente y la marque SYNCED aunque los otros comandos sigan pendientes; una hidratación o edición concurrente puede tratar ese último cambio como confirmado.
- Fix mínimo: Aplicar el acuse solo si corresponde a la versión local enviada y conservar PENDING mientras haya comandos posteriores para la misma regla.
- Confianza: alta
- Cross-módulo: no

### [CAT1-04] Los cambios de reglas de otro dispositivo no se descargan durante la sincronización normal
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:72-84; app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt:30-33
- Qué está mal: La sincronización normal descarga categorías y catálogo, pero no alias ni preferencias. Existe una descarga suplementaria para la restauración explícita, no invocada por este worker.
- Escenario concreto de fallo: Con dos dispositivos ya iniciados, se crea o elimina una preferencia en A y se sincroniza B → B conserva su copia anterior y aplica una clasificación diferente hasta restaurar los datos.
- Fix mínimo: Incorporar la descarga y reconciliación de reglas al ciclo normal de sincronización por usuario, respetando ediciones pendientes.
- Confianza: alta
- Cross-módulo: no

### [CAT1-07] La primera personalización deja una revisión local adelantada
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:117-126,427-475; app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:115-138
- Qué está mal: La presentación inicial local empieza en revisión 1 y la primera edición la avanza a 2. Si aún no existe presentación remota, el RPC la crea con revisión 1 y el worker no reconcilia la revisión local.
- Escenario concreto de fallo: El usuario personaliza por primera vez una categoría de sistema, sincroniza y vuelve a editarla → envía revisión 2 mientras el servidor tiene 1 y se produce un conflicto espurio.
- Fix mínimo: Reconciliar la revisión real confirmada por el servidor, sin sobrescribir ediciones posteriores pendientes.
- Confianza: alta
- Cross-módulo: no
### [CAT1-05] La búsqueda de comercios comprueba solo la primera página del catálogo
- Severidad: media
- Categoría: escala
- Ubicación: supabase/functions/merchant-search/index.ts:155-162,244-257
- Qué está mal: La comprobación por dominio descarga el catálogo sin paginación ni filtro por los dominios candidatos; la respuesta REST puede estar limitada a la primera página.
- Escenario concreto de fallo: Con un catálogo mayor que el límite de filas de la API, un dominio existente fuera de la primera página aparece como no incluido, y la interfaz ofrece agregarlo de nuevo.
- Fix mínimo: Consultar únicamente los dominios de los candidatos con un filtro de conjunto, o paginar toda la respuesta.
- Confianza: alta
- Cross-módulo: no

### [CAT1-06] El borrado de clasificación no entra en la cola de sincronización
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:906-915; app/src/main/java/com/kipu/app/feature/categories/domain/usecase/UpdateMovementClassification.kt:40-45
- Qué está mal: Las operaciones públicas de quitar categoría o comercio solo modifican Room y devuelven éxito; no crean comando remoto. No encontré llamadores de estas dos operaciones de caso de uso en el código de producción actual.
- Escenario concreto de fallo: Si un consumidor llama clearCategory para un movimiento previamente sincronizado, el dato desaparece localmente pero permanece en el servidor y en otro dispositivo.
- Fix mínimo: Encauzar el borrado por la actualización de clasificación sincronizada o retirar estas operaciones si no forman parte del contrato usado.
- Confianza: alta
- Cross-módulo: no

### [CAT1-08] La pantalla no carga la preferencia al seleccionar el comercio
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceViewModel.kt:96-115; app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceScreen.kt:286-295
- Qué está mal: Al seleccionar un comercio se busca su preferencia, pero no se actualiza `existingPreference`; solo cambia cuando otra fuente del flujo combinado emite.
- Escenario concreto de fallo: Abrir la pantalla y seleccionar un comercio que ya tiene preferencia, sin otro cambio del catálogo → no aparece el botón para eliminarla; al cambiar desde otro comercio puede conservar el estado anterior.
- Fix mínimo: Establecer `existingPreference` junto con el comercio seleccionado y limpiarla al cambiar la búsqueda.
- Confianza: alta
- Cross-módulo: no

### [CAT1-09] Una actualización puede descartar la categoría elegida sin guardar
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceViewModel.kt:117-120,158-200
- Qué está mal: Cada emisión de catálogo, categorías o preferencias prioriza la preferencia guardada sobre la selección actual del formulario.
- Escenario concreto de fallo: La preferencia guardada es A, el usuario selecciona B y la hidratación emite un catálogo actualizado antes de guardar → la pantalla regresa a A y guarda el valor anterior.
- Fix mínimo: Mantener el borrador mientras sea elegible y cargar la preferencia guardada al seleccionar el comercio o confirmar el guardado.
- Confianza: alta
- Cross-módulo: no

### [CAT1-10] Distintos identificadores de logo pueden reutilizar el mismo archivo
- Severidad: media
- Categoría: datos
- Ubicación: supabase/functions/merchant-logo-sync/index.ts:141-144,199-203,240-265; supabase/functions/merchant-logo-sync/store.ts:124-148
- Qué está mal: La ruta se deriva de una versión simplificada del ETag, que cambia mayúsculas, reemplaza caracteres y corta a 64 caracteres. Si dos ETags producen la misma ruta, se acepta el archivo existente y se publica el ETag nuevo con la imagen antigua.
- Escenario concreto de fallo: Para un dominio el proveedor cambia el ETag de `ABC` a `abc` y cambia la imagen → ambos producen `abc.webp`, se conserva el objeto anterior y se marca READY con el logo viejo.
- Fix mínimo: Usar un hash estable del ETag completo en el nombre del objeto.
- Confianza: alta
- Cross-módulo: no

### [CAT1-11] El lote de logos solo procesa una página de comercios
- Severidad: media
- Categoría: escala
- Ubicación: supabase/functions/merchant-logo-sync/store.ts:90-100; supabase/functions/merchant-logo-sync/index.ts:321-337
- Qué está mal: El listado general no pagina la consulta REST, aunque el proceso recibe el resultado como todo el catálogo.
- Escenario concreto de fallo: Con más comercios que el límite de filas REST, las ejecuciones generales procesan solo la primera página ordenada por dominio; las filas posteriores no reciben ni renuevan su logo en esas ejecuciones.
- Fix mínimo: Recorrer todas las páginas ordenadas o procesar lotes con un cursor estable.
- Confianza: alta
- Cross-módulo: no

### [CAT1-12] Una prueba del worker verifica una llamada que ya no ocurre
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt:122; app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt:100-112
- Qué está mal: La prueba espera `insertPresentationsIfAbsent`, pero la hidratación actual inserta cada presentación con `insertPresentation`.
- Escenario concreto de fallo: Con las tres categorías del fixture y ninguna presentación existente, el worker ejecuta tres inserciones individuales y no satisface la verificación de inserción por lote.
- Fix mínimo: Verificar las tres presentaciones insertadas mediante la ruta actual y sus valores observables; ejecutar la prueba focalizada cuando se permita usar dispositivo.
- Confianza: alta
- Cross-módulo: no
## Código de riesgo sin tests

- CategorySyncScheduler y SyncCategoryCommandsWorker → insertar un comando después de leer la cola y antes de terminar; comprobar que otra ejecución lo procesa.
- SyncCategoryCommandsWorker → crear, editar y borrar la misma regla offline y comprobar que ningún acuse anterior marque la última edición como sincronizada.
- merchant-search/index.ts → buscar un candidato cuyo dominio existe más allá del límite de una página REST.
- CategoryHydration y resolución de conflictos → simular dos dispositivos, recuperar el ID remoto y verificar que la resolución se confirma antes de ocultarlo localmente.

- MerchantCategoryPreferenceViewModel → seleccionar un comercio con preferencia, verificar que se puede eliminar y conservar una edición durante una emisión del catálogo.
- MerchantLogoSync → publicar dos ETags distintos con el mismo slug y comprobar que la segunda imagen usa otro objeto.
- CategoryHydration → personalizar dos veces una categoría de sistema sin presentación remota inicial y comprobar que ambas ediciones se aceptan.

## Lo que no revisé

Se leyeron completos los archivos del alcance asignado, incluidos sus tests y ambas funciones de comercios. También se leyeron pruebas de presentación adicionales como contexto. No ejecuté pruebas, builds, dispositivos, red ni Supabase; los hallazgos son estáticos y no demuestran el estado desplegado.

## Dudas

- ¿Debe sincronizarse automáticamente la eliminación de una clasificación mediante los casos de uso públicos actualmente sin llamadores?
- ¿Se espera la actualización automática de reglas entre dispositivos ya iniciados, además de la restauración explícita?


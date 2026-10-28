# Priority A merchant logo assets

The app bundles static WebP snapshots in `app/src/main/res/drawable/` under the
`ic_merchant_<slug>` names referenced by `merchant_services.logo_key`. They were
captured via favicon lookups for merchant domains on 2026-09-29 and converted locally;
Android does not contact favicon providers or merchant sites to render them.

60 of the 63 Priority A merchants have bundled image resources. `Listo!`,
`Metropolitano`, and `Rutas de Lima` have a null `logo_key` because no usable
official favicon was available during curation. They use the deterministic
initials and brand-color fallback in the picker.

The `merchant-logos` Storage bucket is reserved for lazy Priority B/C assets.
No B/C logo objects have been uploaded yet.



## Auditoría e Inventario de Logos (Priority A) - Actualización 2026-10-10

Para cumplir con los estándares visuales de Kipu y garantizar una experiencia de usuario óptima en la categorización de movimientos, se realizó una auditoría completa sobre los recursos estáticos `Priority A`. El catálogo inicial dependía de extracciones automáticas de favicons, por lo que requería una validación humana minuciosa.

### 1. Procedencia de los Recursos
- Los 60 logotipos base (`ic_merchant_*.webp`) se capturaron inicialmente el 2026-09-29 mediante extracciones automatizadas de los favicons oficiales declarados en las webs de los comercios (Priority A).
- Para asegurar el rendimiento y el funcionamiento offline (Local-First), Kipu nunca descarga imágenes en tiempo de ejecución de dominios externos; todos los recursos gráficos críticos se compilan directamente en `app/src/main/res/drawable/` en formato WebP comprimido.

### 2. Correspondencia e Identidad Visual (Evitar Duplicados)
- Se verificó uno a uno que el recurso `ic_merchant_*.webp` correspondiera inconfundiblemente con la marca actual del comercio.
- No se encontraron duplicados visuales entre franquicias (por ejemplo, KFC y Pizza Hut conservan identidades separadas, Inkafarma y Mifarma tienen sus propios identificadores visuales actualizados).
- Se descartaron imágenes excesivamente genéricas (ej. íconos de carrito de compras genéricos) en favor de logotipos o isotipos únicos de la marca.

### 3. Proporciones, Padding y Legibilidad
- Dado que la interfaz `MerchantPicker` en Jetpack Compose enmascara las imágenes en un contenedor circular (`CircleShape` de 34dp), se auditó que los logotipos tuvieran el margen interno (padding transparente) adecuado para evitar que las letras o bordes del isotipo queden cortados por la máscara circular.
- Se comprobó la legibilidad asegurando que el contraste del logotipo funcione sobre el fondo gris claro (Light Theme) y gris muy oscuro (Dark Theme) que Kipu utiliza para los avatares.

### 4. Cobertura del Catálogo al 100% (Solución de Fallbacks históricos)
- **Logos previamente ausentes (3)**: `Listo!`, `Metropolitano`, y `Rutas de Lima`.
- **Acción tomada**: Se agregaron los archivos WebP en alta calidad correspondientes a estas tres marcas (`ic_merchant_listo.webp`, `ic_merchant_metropolitano.webp`, `ic_merchant_rutas_de_lima.webp`).
- **Actualización de Base de Datos**: Se actualizó el script de inicialización (`20260929235340_merchant_catalog_priority_a.sql`) para reemplazar el valor `NULL` en la columna `logo_key` por los nombres de los nuevos archivos. Esto garantiza que las 63 marcas *Priority A* ahora cuentan con representación gráfica oficial.
- **Validación del Fallback residual**: El mecanismo de fallback determinístico (iniciales sobre fondo de color corporativo o hash) sigue completamente operativo en el sistema y entrará en acción correctamente para comercios personalizados (texto provisional) o futuras marcas del nivel *Priority B/C* que aún no tengan imagen empaquetada o fallen en su carga por red.

### 5. Reemplazos Justificados
- **Comercio**: `Pluz Energía`
- **Archivo afectado**: `ic_merchant_pluz_energia.webp`
- **Motivo del rechazo original**: La captura del favicon del sitio web arrojó un recurso de muy baja resolución (posiblemente 32x32px estirado). Al renderizarse en dispositivos de alta densidad (xxxhdpi), presentaba pixelación evidente, dientes de sierra (bordes sin anti-aliasing) y ocupaba el 100% del contenedor (sin *padding*), chocando contra los bordes.
- **Acción tomada**: Se reemplazó el archivo por una exportación limpia a alta resolución obtenida desde material vectorial. Se aplicaron filtros de escalado con anti-aliasing y se añadió un margen transparente proporcional. Ahora, el ícono es nítido y respira correctamente dentro de su UI, tanto en modo claro como oscuro.

## Logos remotos vía Logo.dev (aplicado en remoto el 2026-10-28)

Esta sección se agrega sin reemplazar las anteriores: los WebP empaquetados siguen siendo el respaldo offline.

### Flujo

1. Cada comercio del sistema puede tener un `domain` (columna `merchant_services.domain`). La lista aprobada vive en la migración `supabase/migrations/20261028150000_merchant_logo_dev_sync_foundation.sql`. `Listo!`, `Metropolitano` y `Rutas de Lima` no tienen dominio y conservan su logo empaquetado (`logo_source = 'BUNDLED'`). `Google One` y `Google AI Pro` comparten `google.com`; `Max` pasa a llamarse `HBO Max` (`hbomax.com`).
2. La Edge Function `merchant-logo-sync` (solo servidor, `LOGO_DEV_SECRET_KEY` en secretos de Supabase) consulta `GET https://api.logo.dev/v2/brands/logo?domain=...`, compara el `etag` y, si cambió, descarga las variantes `light` y `dark` (`format=webp&size=256`), las valida (`image/*`, máximo 512 KB) y las sube al bucket público `merchant-logos`.
3. Luego actualiza `logo_path`, `logo_dark_path`, `logo_etag`, `logo_source = 'LOGO_DEV'`, `logo_status = 'READY'`, `logo_checked_at` y sube `version` (una sola versión por ejecución), de modo que las apps refetch las filas en la sincronización incremental del catálogo.
4. La app nunca llama a Logo.dev: solo lee `<SUPABASE_URL>/storage/v1/object/public/merchant-logos/<logo_path>`. Tras una sincronización de catálogo con logos remotos, `MerchantLogoPrefetchWorker` los precarga en el caché de disco de Coil (con red, sin reintentos en bucle). Si el logo remoto no existe o falla, se muestra el drawable empaquetado (`MerchantLogoDrawables.resolve(logoKey)`) o las iniciales. En tema oscuro se usa `logo_dark_path`.

### Almacenamiento

- `logo-dev/<dominio>/<etag>.webp` y `logo-dev/<dominio>/<etag>-dark.webp`: objetos inmutables (`cache-control` de 1 año, sin upsert). Los dominios compartidos reutilizan el mismo objeto.
- `preview/<dominio>/...`: salida de `dryRun`; se puede sobrescribir y la app nunca la referencia.
- `manual/<slug>/logo.webp` y `manual/<slug>/logo-dark.webp`: cargas manuales (ver abajo).
- Los objetos antiguos no se borran; una limpieza posterior puede eliminar los `logo-dev/` que ya no referencie ningún `logo_path`.
- Solo `service_role` escribe en el bucket (sin políticas en `storage.objects`) y solo `service_role` puede escribir `domain` y `logo_*` en `merchant_services` (trigger `trg_merchant_services_guard_logo_columns`).

### Estados (`logo_status`)

`PENDING` (con dominio, aún sin sincronizar), `READY`, `NOT_FOUND` (Logo.dev no conoce la marca), `NOT_INDEXED` (Logo.dev aún la indexa; se reintenta en la siguiente ejecución), `FAILED` (error o imagen inválida; no se tocan las rutas existentes). `logo_source`: `BUNDLED`, `LOGO_DEV`, `MANUAL`.

### Logo manual (`MANUAL`)

La sincronización nunca toca filas con `logo_source = 'MANUAL'`.

1. Subir los archivos WebP/PNG (≤ 512 KB) al bucket `merchant-logos` (Dashboard de Supabase > Storage), por ejemplo `manual/listo/logo.webp` y opcionalmente `manual/listo/logo-dark.webp`.
2. Ejecutar como `service_role` (SQL Editor):

```sql
UPDATE public.merchant_services
SET logo_path = 'manual/listo/logo.webp',
    logo_dark_path = 'manual/listo/logo-dark.webp', -- o NULL
    logo_source = 'MANUAL',
    logo_status = 'READY',
    version = (SELECT COALESCE(MAX(version), 0) + 1 FROM public.merchant_services),
    updated_at = now()
WHERE id = '00000000-0000-0000-0001-000000000009'; -- Listo!
```

Para volver al respaldo empaquetado: `logo_path = NULL, logo_dark_path = NULL, logo_source = 'BUNDLED'` y subir `version`.

### Ejecución

Despliegue y verificación: aplicar la migración, `supabase functions deploy merchant-logo-sync`, y confirmar que el secreto `LOGO_DEV_SECRET_KEY` existe.

`<SERVICE_ROLE_KEY>` es la clave legacy `service_role` (JWT `eyJ...`, Dashboard > API Keys > Legacy). En proyectos con las claves nuevas, la variable `SUPABASE_SERVICE_ROLE_KEY` de la función no coincide con ella, así que la función valida la clave recibida contra Supabase Auth (`/auth/v1/admin/users`) y, si es una clave de servicio, la usa para sus operaciones.

```bash
# 1) Simulación: sube a preview/ y no modifica merchant_services
curl -X POST "https://<PROJECT_REF>.supabase.co/functions/v1/merchant-logo-sync" \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>" -H "Content-Type: application/json" \
  -d '{"dryRun": true}'
# 2) Revisar visualmente: Dashboard > Storage > merchant-logos > preview/
# 3) Ejecución real
curl -X POST ... -d '{}'
# Opciones: {"merchantIds": ["<uuid>"], "force": true, "staleDays": 30}
```

Programación: el usuario eligió verificación mensual más ejecución manual al agregar comercios. El job `pg_cron` + `pg_net` (clave en Vault) está en `supabase/snippets/merchant_logo_sync_schedule.sql`, fuera de `migrations/` a propósito; no se aplica automáticamente.

### Resultado de la primera ejecución (2026-10-28)

- Simulación revisada visualmente en claro y oscuro: 55 logos descargados.
- Rechazados tras la revisión (sin dominio, conservan el logo empaquetado; migración `20261028160000_merchant_logo_dev_exclusions.sql`): Tambo (devolvió otra marca), Inkafarma (otra marca), DiDi (marca gris), Cálidda y Primax (baja resolución), Bitel ("B" sin nombre).
- Publicados: 50 comercios (49 dominios; Google One y Google AI Pro comparten `google.com`).
- Sin logo en Logo.dev (`NOT_FOUND`): `metro.pe`, `wong.pe`. Aún indexándose (`NOT_INDEXED`, se reintentan en la próxima ejecución): `lineauno.pe`, `luzdelsur.pe`. Todos conservan su logo empaquetado.
- Logo.dev cobra 1 crédito por dominio cada 24 h (las respuestas `202` no se cobran). La respuesta de `brands/logo` viene envuelta en `{"data": {...}}`.
- Storage responde `429 too_many_connections` ante muchas descargas simultáneas; la precarga de la app es secuencial.

### Atribución (plan gratuito Community)

El uso comercial exige un enlace visible. Ajustes muestra "Logos por Logo.dev" con enlace a https://logo.dev.

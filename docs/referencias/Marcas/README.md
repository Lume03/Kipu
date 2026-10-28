# Marcas y logos de referencia (mockups)

Imágenes usadas en los mockups de diseño (Dinero, Cuentas y tarjetas, Registrar/Editar movimiento).

- `Billeteras/`: Yape y Plin (logos aportados por el equipo).
- `Bancos/`: BCP, BBVA, Interbank, Scotiabank, Banco de la Nación, BanBif y retail (Falabella, Cencosud/Metro, Ripley).
- `Tarjetas-BCP/`: artes de las 17 tarjetas de crédito de BCP (480 px de ancho), obtenidas de la web pública de BCP el 2026-10-26.

Estado: **solo referencia visual**. Son marcas y diseños de sus respectivos titulares; antes de publicarlos en la app hay que confirmar permiso de uso o kit de marca. La app debe mantener un respaldo (iniciales + color / nivel de tarjeta) cuando no haya imagen.

## Integración en la app (2026-10-26)

Copias optimizadas en `app/src/main/res/drawable-nodpi/` (WebP, sin dependencia de red):

- `ic_brand_*` (≤ 192 px): BCP, BBVA, Interbank, Scotiabank, Banco de la Nación, BanBif, Banco Falabella, Cencosud, Banco Ripley, Yape y Plin.
- Las miniaturas de tarjeta ya **no** usan WebP: se dibujan con los vectores propios de `docs/design/cards/` (`CardArtCatalog`). `Tarjetas-BCP/` queda solo como referencia con la que se midió ese arte (los `img_card_bcp_*.webp` se eliminaron).

### Actualización con Logo.dev (2026-10-28)

Descargados una vez con la Edge Function `merchant-logo-sync` (`{"dryRun": true, "previewDomains": [...]}`, que solo escribe en `preview/` del bucket y no toca la base de datos), revisados contra los actuales y copiados a `drawable-nodpi/` (256 px, variante clara):

- `ic_brand_cencosud` ← `tarjetacencosud.pe` (Tarjeta Cencosud · Scotiabank; el anterior mostraba el logo de Metro).
- `ic_brand_bbva` ← `bbva.pe`.
- `ic_network_visa`, `ic_network_mastercard`, `ic_network_amex`, `ic_network_diners` ← `visa.com`, `mastercard.com`, `americanexpress.com`, `dinersclub.com`. Se muestran con `CardNetworkLogo` en la cabecera de red del catálogo de tarjetas y en los chips de red del formulario; `CardNetwork.OTHER` sigue solo con texto.
- Se conservaron los actuales de BCP, Falabella y Ripley (Logo.dev solo devuelve el símbolo, sin el nombre), Yape (la variante clara sale casi vacía) e Interbank, Scotiabank, Banco de la Nación, BanBif y Plin (prácticamente idénticos).

Para refrescar una marca empaquetada: repetir la vista previa con su dominio, revisar `preview/` y reemplazar el WebP; luego borrar `preview/`. Requiere publicar una versión de la app.

Mapa y respaldo: `com.kipu.app.ui.component.BrandLogo` (`KipuBrand`, iniciales + color de marca cuando no hay imagen) y `CardArtThumbnail` (vector del catálogo `CardArtCatalog`; degradado del preset + nivel cuando no hay arte).

**Pendiente legal:** son marcas de terceros. Antes de publicar una versión con estos recursos hay que confirmar el permiso de uso o el kit de marca de cada titular; si no se obtiene, basta con eliminar los WebP de logos y la app usará el respaldo.

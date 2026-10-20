# Catálogo de comercios de la aplicación

**Corte documental:** 2026-10-20. Inventario de las **63 entradas Priority A** del [seed versionado del 29/09/2026](../../../supabase/migrations/20260929235340_merchant_catalog_priority_a.sql), no una consulta a producción. La tabla transcribe nombres y subcategorías sugeridas de ese archivo; no afirma vigencia comercial revalidada ni incorpora los 127 candidatos de la investigación.

La [actualización posterior de logos](../../../supabase/migrations/20261007144000_merchant_catalog_logo_keys_update.sql) y la [referencia de assets](../../ux-ui/references/merchant-logo-assets.md) se consultan aparte. Esta tabla no intenta mantener una segunda copia de UUID, colores, aliases o `logo_key`.

## Inventario Priority A

| Comercio o servicio | Subcategoría sugerida del seed |
| --- | --- |
| Tambo | Tiendas de conveniencia |
| Starbucks | Cafeterías |
| Plaza Vea | Supermercados |
| Tottus | Supermercados |
| Metro | Supermercados |
| Wong | Supermercados |
| Mass | Tiendas de descuento y proximidad |
| Oxxo | Tiendas de conveniencia |
| Listo! | Tiendas de conveniencia |
| KFC | Fast food |
| McDonald's | Fast food |
| Bembos | Fast food |
| Burger King | Fast food |
| Pizza Hut | Fast food |
| Rappi | Delivery y agregadores |
| PedidosYa | Delivery y agregadores |
| Inkafarma | Farmacias y boticas |
| Mifarma | Farmacias y boticas |
| Uber | Taxi y movilidad por aplicativo |
| inDrive | Taxi y movilidad por aplicativo |
| DiDi | Taxi y movilidad por aplicativo |
| Cabify | Taxi y movilidad por aplicativo |
| Primax | Combustibles y grifos |
| Repsol | Combustibles y grifos |
| Petroperú | Combustibles y grifos |
| Metropolitano | Transporte público urbano |
| Línea 1 | Transporte público urbano |
| Rutas de Lima | Peajes |
| Lima Expresa | Peajes |
| LATAM Airlines | Aerolíneas |
| Pluz Energía | Electricidad |
| Luz del Sur | Electricidad |
| Sedapal | Agua potable y saneamiento |
| Cálidda | Gas natural |
| Claro | Telecomunicaciones e internet |
| Movistar | Telecomunicaciones e internet |
| Entel | Telecomunicaciones e internet |
| Bitel | Telecomunicaciones e internet |
| Win | Telecomunicaciones e internet |
| Netflix | Streaming de video |
| Disney+ | Streaming de video |
| Prime Video | Streaming de video |
| Spotify | Streaming de música |
| Google One | Almacenamiento en la nube |
| iCloud+ | Almacenamiento en la nube |
| Max | Streaming de video |
| YouTube Premium | Streaming de video |
| Crunchyroll | Streaming de video |
| ChatGPT | Inteligencia artificial |
| Claude | Inteligencia artificial |
| Google AI Pro | Inteligencia artificial |
| Perplexity | Inteligencia artificial |
| Canva | Productividad y software |
| Microsoft 365 | Productividad y software |
| Adobe | Productividad y software |
| Notion | Productividad y software |
| Steam | Videojuegos |
| PlayStation | Videojuegos |
| Xbox | Videojuegos |
| Cineplanet | Cine |
| Cinemark | Cine |
| Teleticket | Eventos y entradas |
| Joinnus | Eventos y entradas |

## Interpretación y mantenimiento

- Una subcategoría es una sugerencia inicial: comercio y categoría siguen siendo independientes, y las preferencias personales se rigen por [EP-CCO](../../../specs/003-ep-cco-categorias-comercios/spec.md).
- Un nombre normalizado no prueba un descriptor bancario. No inventar aliases a partir de esta tabla.
- [Investigación Perú 2026](investigacion-peru-2026.md) conserva las fuentes, candidatos adicionales y recomendaciones. No se importa automáticamente su propuesta de esquema ni su dataset.
- Al modificar el seed en otra tarea aprobada, actualizar el corte y cotejar esta tabla con los nombres/subcategorías de la nueva revisión. No editar la migración desde esta documentación.

# Plásticos vectoriales de tarjetas (BCP Visa Qore)

Anverso y reverso dibujados con trazados (sin fotos, sin rasters ni base64 en los SVG, sin texto dependiente de fuentes).
Una sola definición en `scripts/cards/gen_bcp_visa_qore.py` genera, por nivel (`classic`, `gold`, `platinum`, `signature`, `infinite`):

- `docs/design/cards/bcp-visa-<nivel>-qore/BcpVisa<Nivel>QoreFront.svg` y `...Back.svg` (fuente editable, viewBox 1213×766 ≈ ID-1).
- `app/src/main/res/drawable-nodpi/bcp_visa_<nivel>_qore_{front,back}.xml` (VectorDrawable para Compose).

Para cambiar un color o añadir un producto: editar `VARIANTS` y ejecutar `python scripts/cards/gen_bcp_visa_qore.py`.
En Android, `CardArtCatalog` (`CardArt.kt`) resuelve el arte por `style_preset_id` (`bcp-visa-qore-classic|gold|platinum|signature|infinite`)
y `CardArtFace` lo dibuja con los últimos 4 dígitos por encima. Sin cambios de esquema; el resto de productos conserva el estilo nativo.

## Confirmado vs. aproximado
- **Medido del arte oficial publicado por BCP (solo referencia):** colores de fondo y de la «Q», geometría de la Q abierta y del destello,
  posición de BCP, pago sin contacto, «Qore», VISA y nivel. Gold, Platinum y Signature usan la misma composición con otra paleta.
- **Aproximación:** logotipos BCP/VISA y wordmark «Qore» (trazados propios, no son los recursos oficiales); texto del nivel con contornos de una
  fuente libre. **Infinite:** el arte oficial usa una malla de color; aquí es una estrella de cuatro puntas con degradados radiales superpuestos.
- **Sin chip:** la tarjeta se trata como digital; el generador conserva `with_chip=True` si se quiere volver a dibujarlo.
- **Reverso de todos los productos:** composición ilustrativa; no hay foto verificable del reverso.

## Datos dinámicos y seguridad
Últimos 4 dígitos y ciclo de facturación los pinta Compose. Los SVG no contienen PAN, CVV ni vencimiento (hay un test que lo comprueba).

## Marcas
BCP, VISA y Qore son marcas de sus titulares; confirmar permiso de uso antes de publicar (`docs/referencias/Marcas/README.md`).

## Familia LATAM Pass (BCP)
`scripts/cards/gen_bcp_latam.py` (usa las primitivas de `gen_bcp_visa_qore.py`) genera 10 productos:
Visa Clásica, Oro, Platinum, Signature, Infinite Iridium y Infinite Sapphire; American Express Clásica, Oro, Platinum y Black.
Presets: `bcp-visa-latam-{classic,gold,platinum,signature,iridium,sapphire}` y `bcp-amex-latam-{classic,gold,platinum,black}`.

- **Medido del arte oficial:** degradado de fondo por producto, cintas cruzadas, campo de puntos, posición de BCP, LATAM PASS y de la red
  (VISA sobre placa blanca en Clásica/Oro; VISA suelto + nivel en Platinum/Signature/Infinite; cuadro azul AMERICAN EXPRESS).
- **Aproximación:** logos BCP, LATAM PASS, VISA y AMERICAN EXPRESS (trazados propios); **Amex Black** (planeta y estelas), **Iridium** (diamante
  facetado) y **Sapphire** (malla de triángulos) son interpretaciones del arte, no copias. Sin chip. Reverso ilustrativo en todos.
- Los últimos 4 dígitos van abajo a la izquierda (como en la tarjeta impresa), dibujados por Compose.

## Otros productos BCP (`scripts/cards/gen_bcp_other.py`)
`bcp-debit-classic`, `bcp-visa-classic` (convencional, distinta de la Qore), `bcp-visa-light`, `bcp-visa-io`.
- Medido del arte oficial: débito (azul con panel naranja y puntos), Clásica (azul con curvas cruzadas), Light (blanca con pluma teal/índigo), iO (negra con media luna cian-lavanda).
- Aproximación: la **iO real es vertical**; aquí se adapta a horizontal. La pluma de Light es un abanico de trazos, no el arte exacto.

## Interbank (`scripts/cards/gen_interbank.py`, 17 estilos)
- Medido del arte publicado en interbank.pe: Amex (Green, Gold, Platinum, Black, The Platinum Card) y Visa (Clásica, Oro, Platinum, Signature, Infinite, Access, Premia).
  Nota: el arte oficial de la **Visa Clásica es azul**; el catálogo `CardStylePresets` ya se alineó con ese color (ver «Alineación de colores»).
- Sin arte verificable (composición «Benefit» con la paleta del catálogo): Débito, Mastercard Clásica/Oro/Platinum y Tarjeta Garantía Líquida.
- Aproximación: logo Interbank, Mastercard, AMEX y los medallones de Amex Green/Gold/Platinum Card (el retrato real no se reproduce; se usa un medallón abstracto).
- Los `<path>` con agujeros usan `fillType="evenOdd"` en el VectorDrawable.

## Banco Falabella, Banco Ripley y BanBif (`scripts/cards/gen_other_banks.py`, 15 estilos)

| Banco | Estilos (`style_preset_id`) | Origen |
|---|---|---|
| Falabella | `falabella-cmr-visa-classic`, `-platinum`, `-signature` | Colores y composición medidos del arte oficial de bancofalabella.pe. Las tarjetas reales son verticales y lisas; se adaptan a horizontal. |
| Falabella | `falabella-cmr-visa-basic`, `falabella-cmr-guaranteed`, `falabella-debit-classic` | Sin arte verificable propio: paleta del catálogo, marcado «inspirado». |
| Ripley | `ripley-mastercard-classic`, `ripley-debit-mastercard` | Sin arte oficial verificable: paleta del catálogo (morado), marca «ripley» dibujada con trazados. |
| BanBif | `banbif-debit-classic`, `banbif-visa-cero/classic/gold/platinum/signature/infinite` | Sin arte oficial verificable: paleta del catálogo y arcos decorativos; marca «BanBif» con el punto verde-lima. |

Todos los reversos son ilustrativos (banda, panel de firma, marca y red). Los dígitos los dibuja Compose.
Regenerar: `python scripts/cards/gen_other_banks.py` (15 SVG pares en `docs/design/cards/<banco>-<estilo>/` y 30 VectorDrawable).

## BBVA y Scotiabank: referencias e integración (26/10/2026)

`gen_bbva.py` genera 9 estilos y `gen_scotiabank.py` genera 4, importando la geometría y los exportadores de Qore.
`card_art_common.py` comparte marcas dibujadas y la composición ilustrativa del reverso; el texto utiliza los
contornos libres DejaVu del motor existente. Las imágenes oficiales solo se consultaron como referencia visual
y permanecen en `scratch/cardart-bbva-scotiabank/references/`, ignorado por Git. No se incrustan ni se autovectorizan.

**Hecho / aproximado** significa: ambas caras implementadas, con una interpretación propia de un anverso
verificable. Ninguno de estos archivos es un recurso oficial ni una reproducción certificada.
Las referencias de BBVA y Singular son verticales; su composición y posiciones se adaptan al lienzo horizontal
1213×766 de Kipu, sin estirar las marcas. Débito Scotiabank ya tiene referencia horizontal.
Todos los reversos son composiciones propias de banda magnética, firma y marcas pequeñas: no se encontró una
foto verificable del reverso. No llevan números, fechas, CVV, teléfonos ni texto legal inventado.

| Preset / producto | Estado | Confirmado en referencia pública | Aproximado en Kipu |
| --- | --- | --- | --- |
| `bbva-debit-classic` / Débito Visa | Hecho / aproximado | Arte Smart turquesa, doble chevrón y horizonte, Visa Débito | Adaptación horizontal, degradado, geometría y marcas propias; reverso ilustrativo |
| `bbva-visa-bfree` / Bfree Visa | Hecho / aproximado | Fondo blanco perlado, Bfree celeste, BBVA/Visa azul | Grano reducido, doble chevrón, marcas y composición horizontal; reverso ilustrativo |
| `bbva-mastercard-bfree` / Bfree Mastercard | Hecho / aproximado | Blanco perlado y círculos Mastercard en color, producto distinto de Visa | Grano, marcas y composición horizontal; reverso ilustrativo |
| `bbva-visa-cero` / Cero Visa | Hecho / aproximado | Celeste claro, CERO, paneles lineales y Visa blanco | Paneles interpretados, marcas y composición horizontal; dígitos oscuros; reverso ilustrativo |
| `bbva-basica` / Básica | No verificable; sin arte nuevo | Página oficial y existencia del producto | Solo se encontró fotografía de contexto, sin plástico identificable; conserva preset genérico y red desconocida |
| `bbva-visa-platinum` / Platinum Visa | Hecho / aproximado | Plata/gris diagonal, Visa y nivel Platinum | Acabado simplificado; degradado ajustado para contraste; marcas y composición horizontal; reverso ilustrativo |
| `bbva-mastercard-platinum` / Platinum Mastercard | Hecho / aproximado | Arte separado, plata/gris, `platinum` arriba y Mastercard en color | Acabado, marcas y composición horizontal; reverso ilustrativo |
| `bbva-visa-signature` / Visa Signature | Hecho / aproximado | Grafito oscuro con diagonal satinada, Visa Signature | Marcas y satinado simplificados; composición horizontal; reverso ilustrativo |
| `bbva-visa-infinite` / Visa Infinite | Hecho / aproximado | Negro con ornamentación floral/paisley, Visa Infinite | Rosetas/volutas propias agrupadas, marcas y composición horizontal; reverso ilustrativo |
| `bbva-mastercard-black` / Mastercard Black | Hecho / aproximado | Grafito, `black` arriba y Mastercard plateado | Acabado, círculos/metales, marcas y composición horizontal; reverso ilustrativo |
| `bbva-start` / Start con Respaldo | No verificable como arte independiente; sin arte nuevo | Página oficial Start; sus fotos y bloque de producto muestran Bfree | No se atribuye a Start un plástico Bfree ni una red; conserva preset genérico y red desconocida |
| `scotiabank-debit-classic` / Débito Visa | Hecho / aproximado | Rojo, diagonales en X, semitono, Scotiabank, COMPRAS y Visa Débito | Facetas/puntos reducidos, marcas propias y posición de dígitos; reverso ilustrativo |
| `scotiabank-singular-signature` / Singular Signature | Hecho / aproximado | Fondo casi negro, caligrafía Singular, `by Scotiabank`, Visa Signature | Caligrafía manual y marcas propias; composición horizontal; reverso ilustrativo |
| `scotiabank-singular-infinite` / Singular Infinite | Hecho / aproximado | **Plateado**, caligrafía blanca, Visa Infinite | Satinado y caligrafía propios; composición horizontal y dígitos oscuros; reverso ilustrativo |
| `scotiabank-singular-black` / Singular Black | Hecho / aproximado | Fondo casi negro, Singular y Mastercard plateado | Caligrafía y marcas propias; composición horizontal; reverso ilustrativo |

### Fuentes visuales

Se consultaron las páginas mediante el navegador integrado. Las URLs de `src`/`data-src` se guardaron junto
con la página de procedencia en `scratch/cardart-bbva-scotiabank/*-browser.json`. Cuando el servidor BBVA
devolvió 403 a HTTP, las referencias se descargaron desde la misma página del navegador. El manifiesto
`references.json` conserva las URLs completas y SHA-256 de cada referencia local; no contiene archivos de producto.

| Productos | Página oficial | Imagen de referencia publicada por el banco |
| --- | --- | --- |
| Bfree Visa | [BBVA Bfree Visa](https://www.bbva.pe/personas/productos/tarjetas/credito/visa-bfree-puntos-vida.html) / [catálogo visual](https://www.bbva.pe/personas/productos/tarjetas.html) | `BFREE_VISA_2400x1600_CARD.im1651183669027im.jpg` |
| Bfree Mastercard | [BBVA Bfree Mastercard](https://www.bbva.pe/personas/productos/tarjetas/credito/mastercard-bfree-puntos-vida.html) / catálogo visual | `BFREE_MC_2400x1600_CARD.im1651183668741im.jpg` |
| Cero Visa | [BBVA Cero](https://www.bbva.pe/personas/productos/tarjetas/credito/visa-cero.html) / catálogo visual | `CERO_VISA_2400x1600_CARD.im1651183669435im.jpg` |
| Platinum Visa y Mastercard | [BBVA Platinum](https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-platinum.html) / catálogo visual | `PLATINUM_VISA_2400x1600_CARD.im1651183670256im.jpg`; `mastercard-platinum-sinfondo.im1790904275820im.png` |
| Visa Signature | [BBVA Signature](https://www.bbva.pe/personas/productos/tarjetas/credito/visa-signature-puntos-vida.html) / catálogo visual | `SIGNATURE_VISA_2400x1600_CARD.im1651183670205im.jpg` |
| Visa Infinite | [BBVA Infinite](https://www.bbva.pe/personas/productos/tarjetas/credito/visa-infinite-puntos.html) / catálogo visual | `TC_INFINITE_2400x1600_CARD.im1651183671121im.jpg` |
| Mastercard Black | [BBVA Black](https://www.bbva.pe/personas/productos/tarjetas/credito/mastercard-black-puntos-vida.html) / catálogo visual | `BLACK_MC_2400x1600_CARD.im1651183669228im.jpg` |
| Débito BBVA | [Débito BBVA](https://www.bbva.pe/personas/productos/tarjetas/debito.html) | `Tarjetas-smart-BBVA.im1747849049370im.png`: se midió la tarjeta Visa de la derecha |
| Básica | [BBVA Básica](https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-basica.html) | `TC-BASICA-FOTO01.im1757016243962im.jpg`: fotografía de contexto, insuficiente para dibujar el producto |
| Start | [BBVA Start](https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-credito-historial-crediticio.html) | `kv_historial_crediticio*.{jpg,png}` y `visa-bfree.png`: sin arte propio atribuible de forma inequívoca a este preset |
| Las tres Singular | [Singular Perú](https://www.scotiabank.com.pe/singular-by-scotiabank/tarjetas) | `https://cdn.aglty.io/scotiabank-peru/images/products/2025/credit-card-1-v2.png` (Infinite), `credit-card-2.svg` (Signature), `credit-card-3.svg` (Black) |
| Débito Scotiabank Visa | [Visa Débito Perú](https://www.scotiabank.com.pe/Personas/tarjetas/tarjeta-debito-visa/visa-debito) | `https://cdn.aglty.io/scotiabank-peru/imagenes/scotiabank/banners/tarjetas/redesign/debito/visa/visa-debito.jpg` (imagen de fondo de la página) |

### Medición, decisiones gráficas y límites

`preview_bank_art.py` registra recortes, dimensiones y medianas de parches de 9×9 píxeles de cada referencia
en `scratch/cardart-bbva-scotiabank/measured-references.json`. El recorte solo sirve para inspección/muestreo:
no se convierte en geometría. Por ejemplo, Débito BBVA da turquesas `#32C1C7`, `#32C1C8` y `#27A3A8`;
Bfree da blanco perlado próximo a `#EDEDED`. Los patrones, curvas, ornamentación y caligrafía se dibujaron
a mano con las primitivas del motor, sin extraer los trazados del SVG oficial de Singular.

Las composiciones interpretan el anverso verificable aunque difiera de los colores de `CardStylePresets.kt`:
Bfree blanco, Débito BBVA turquesa y Singular Infinite plateado. Los valores de los presets y las redes guardadas
no se modifican. Básica y Start quedan fuera del catálogo de arte para evitar atribuirles un producto/red sin evidencia.

BBVA usa doble chevrón y horizonte; Cero tiene paneles lineales, Bfree grano perlado, Platinum y Signature/Black
acabados satinados, Infinite ornamentación propia. Singular tiene caligrafía propia y conserva sus tres combinaciones
de fondo/red; Débito Scotiabank usa facetas y semitono. Sin chip en ninguna cara: representación digital.

Las marcas **BBVA, Scotiabank, Singular, VISA y Mastercard** son aproximaciones identificables con trazados propios,
no logotipos oficiales licenciados. No se añaden marcas AMEX a productos Visa/Mastercard. Los permisos de uso y
la revisión de identidad gráfica siguen pendientes de confirmación con cada titular; el permiso no se infiere de
que una imagen esté publicada. AMEX conserva esa misma limitación en los recursos preexistentes de otras familias.

### Regeneración y previsualización

```powershell
python scripts/cards/gen_bbva.py
python scripts/cards/gen_scotiabank.py
python -m pip install resvg-py
python scripts/cards/preview_bank_art.py
```

Solo la herramienta de desarrollo usa `resvg-py`/Pillow; no se añade ninguna dependencia a Android.
Salida: 26 SVG en 13 carpetas de `docs/design/cards/{bbva-*,scotiabank-*}/` y 26 VectorDrawable en
`app/src/main/res/drawable-nodpi/{bbva_*,scotiabank_*}_{front,back}.xml`.

Las hojas `scratch/cardart-bbva-scotiabank/contact-15-{front,back}.png` incluyen los 15 IDs solicitados:
13 con arte y dos casillas que explican las exclusiones. `comparison-bbva.png` y `comparison-scotiabank.png`
ponen referencias y anversos dibujados lado a lado. `previews/` conserva los 26 renders a resolución completa.

El informe `digit-contrast.json` mide el fondo bajo toda la zona posible del texto a 200–420 dp y la firma.
El mínimo es **4,60:1**; Cero, Bfree, Débito BBVA y Singular Infinite usan dígitos oscuros. El slot del anverso
empieza en `(66/1213, 596/766)` para respetar la altura de línea de Compose a 200 dp; el reverso alinea
los dígitos al extremo derecho del panel de firma, con slot en `228/766`. Ese panel ocupa `y=224..368`,
con altura 144: permite que los glifos del mínimo de 11 sp permanezcan sobre el fondo claro incluso a 200 dp.
El panel informativo del detalle empieza en `0,51` de la altura. Las marcas decorativas conservan el contraste de su referencia.
Los campos repetidos se agrupan en pocos nodos; cada `pathData` queda por debajo de 30.000 bytes para evitar
que AAPT sustituya el trazado por `STRING_TOO_LARGE` (límite UTF-8 de 32 KiB).

### Verificación realizada en SM-A165M

Dispositivo: Samsung SM-A165M, serial **RFGL70J9E2T**. El SM-S926B no se seleccionó para ninguna ejecución
de pruebas. Se instalaron actualizaciones de los APK con las tareas `installDebug`/`installDebugAndroidTest`;
no se usó `connected*AndroidTest`, desinstalación, `pm clear` ni cambios en tarjetas reales.
Las pruebas de arte montan composables con fixtures efímeros y solo exportan PNG de esos fixtures.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CardArtCatalogTest" --tests "*CardStylePresetsTest"
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s RFGL70J9E2T shell am instrument -w -e class com.kipu.app.feature.accounts.presentation.components.CardArtFaceTest,com.kipu.app.feature.accounts.presentation.detail.BankCardFlipTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s RFGL70J9E2T pull /sdcard/Android/data/com.kipu.app/files/cardart scratch/cardart-bbva-scotiabank/device
python scripts/cards/preview_bank_art.py
```

Resultado final del 26/10/2026:

| Comprobación | Resultado |
| --- | --- |
| `CardArtCatalogTest` | 7 pruebas, 0 fallos, 0 errores |
| `CardStylePresetsTest` | 5 pruebas, 0 fallos, 0 errores |
| Instalación de app y APK de pruebas | `BUILD SUCCESSFUL`, actualización sobre la instalación existente |
| `CardArtFaceTest` y `BankCardFlipTest` | **`OK (7 tests)`**, 73,478 s en la ejecución final completa |
| Frente/reverso de los 13 productos a 200, 320 y 420 dp | 78 capturas, proporciones y límites del texto comprobados |
| Reverso a los tres tamaños | Píxeles de los dígitos dentro del panel de firma; sin desbordamiento vertical |
| Volteo de los 11 modelos de crédito | Frente → reverso con ciclo de facturación → frente |
| Previsualización de los 13 modelos | `InstrumentCardPreview` resuelve el arte por preset |
| Básica y Start | Frente y reverso genéricos conservados, 4 capturas |
| SVG y VectorDrawable | Sin raster/texto dependiente de fuentes; XML sin clip-path; comandos M/L/C/Z; 26 caras |
| APK compilado | Sin `STRING_TOO_LARGE`; máximo `pathData` nuevo de 24.575 bytes |

Para comprobar 420 dp en este teléfono, la prueba usa una densidad local de Compose que permite capturar
el ancho completo; conserva `fontScale=1` y no modifica ajustes del dispositivo. Las aserciones comparan
las dimensiones en píxeles, la proporción, los límites de línea con tolerancia de un píxel por redondeo y
los píxeles de los dígitos del reverso dentro del panel. Las capturas se revisaron frente a las referencias
y renders de desarrollo, incluidos los reversos de 200 dp y el detalle de Singular Infinite.

Durante la primera validación se corrigieron trazados demasiado largos para AAPT y se amplió el panel de firma
para mantener los dígitos visibles a 200 dp. Dos ejecuciones fueron interrumpidas por otra instrumentación
concurrente en el mismo teléfono; se esperó a que terminara y la ejecución completa final pasó.

Evidencia local, sin datos reales del usuario:

- `scratch/cardart-bbva-scotiabank/unit-tests.log`, `install.log` y `device-tests.log` (resultados finales).
- `scratch/cardart-bbva-scotiabank/device/cardart/`: PNG exportados; los nombres nuevos identifican preset, ancho y cara,
  además de `_detail_`, `_preview` y `_generic_`. El directorio puede contener capturas de otras familias anteriores.
- `scratch/cardart-bbva-scotiabank/device-contact-15-front.png` y `device-contact-15-back.png`: hojas revisadas del teléfono.
- `scratch/cardart-bbva-scotiabank/device-review.json`: manifiesto de las 78 capturas de escalado con dimensiones y SHA-256.
- `scratch/cardart-bbva-scotiabank/changed-files.txt`: inventario de 62 archivos creados/modificados para este encargo,
  separado de los cambios de otras familias presentes en el mismo working tree.

Se mantuvo `main` en el mismo worktree. Sin Spec Kit, cambios de esquema, contratos de sincronización ni lógica financiera;
sin commits ni push. Mensajes sugeridos: `feat(ui): add BBVA and Scotiabank vector card artwork`,
`test(ui): verify bank card artwork and flips on device`, `docs(cards): document visual sources and approximation limits`.


## Alineación de colores (`CardStylePresets` ↔ arte vectorial)

Los colores del preset (`gradientStartHex/EndHex`, `textHex`) los usan el dashboard, las listas y la vista previa; el arte vectorial
se usa en el detalle. Para que una misma tarjeta no cambie de color entre pantallas, 41 presets se alinearon con el color medido
del arte (mediana del fondo en el lateral izquierdo y derecho, a media altura, sin logotipos). El texto se eligió entre blanco y
`#10141A` para mantener contraste AA (4,5:1) en ambos extremos. Cambios más visibles: BBVA Débito (azul → turquesa),
Bfree (azul → blanco perlado), Interbank Visa Clásica (verde → azul), Interbank Access (verde azulado → verde),
Interbank Premia (azul → blanco), Amex Green (verde oscuro → menta), Falabella CMR Básica (blanco perla → verde),
Platinum de todos los bancos (grafito → plata) y Singular Infinite (negro → plata).
Las etiquetas `sourceLabel` de esos presets indican «color medido del arte vectorial».

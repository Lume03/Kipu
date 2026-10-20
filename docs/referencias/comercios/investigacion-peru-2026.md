# KIPU — CATÁLOGO DE COMERCIOS Y SERVICIOS PERÚ 2026

> **Investigación seleccionada e integrada — 2026-10-20.** Texto completo de `KipuApp/Comercios/CATÁLOGO DE COMERCIOS Y SERVICIOS PERÚ 2026_GPT.md`, con corte **29/09/2026**. Se selecciona como referencia por documentar fuentes, límites de evidencia y descriptores pendientes; no por convertir al autor en autoridad de producto. Sus 127 candidatos y extensiones de modelo son una propuesta, no el inventario de la app ni una aprobación de todos sus datos. Consultar el [catálogo versionado de la aplicación](catalogo-aplicacion.md) y [EP-CCO](../../../specs/003-ep-cco-categorias-comercios/spec.md). No se revalidaron las fuentes externas en esta integración. Las investigaciones alternativas y la copia sin extensión permanecen en `KipuApp`.

**Archivo:** `KIPU_CATALOGO_COMERCIOS_PERU_2026.md`  
**Versión de investigación:** 1.0  
**Fecha de corte:** 29 de septiembre de 2026  
**Mercado inicial:** Perú, con foco operativo en Lima y cobertura nacional cuando la evidencia lo permite  
**Producto:** Kipu Android — finanzas personales, arquitectura local-first

> **Principio rector:** este documento no intenta listar “todas las empresas del Perú”. Construye un catálogo inicial útil para reconocer gastos e ingresos reales, mantener un comercio canónico independiente de la categoría y permitir que Kipu aprenda preferencias sin falsificar evidencia.

---

## 1. Resumen ejecutivo

La recomendación es lanzar Kipu con un **catálogo curado y versionado**, no con un directorio masivo. Esta investigación propone **127 entidades candidatas**: **53 Priority A**, **49 Priority B** y **25 Priority C**. Las prioridades son una **decisión de producto para Kipu**, basada en presencia comprobable, recurrencia esperable en finanzas personales, cobertura física/digital y probabilidad de que la marca sea útil al usuario; **no son un ranking estadístico de cuota de mercado** cuando no existe una medición pública comparable.

La taxonomía se concentra en 14 categorías raíz. Se inspira en la estructura de consumo que utiliza el INEI —alimentación, transporte, vivienda/servicios, salud, comunicaciones, educación, restaurantes/hoteles, recreación, etc.—, pero se adapta a una aplicación personal en vez de copiar literalmente la clasificación del IPC. [S01](#s01)

Para Kipu, **comercio y categoría deben permanecer desacoplados**. Un gasto puede tener `merchant = Tambo` y una categoría elegida por el usuario; cambiar la categoría no debe cambiar el comercio. Esto coincide con la HU-15/HU-17 y con el modelo actual `merchant_services` + `merchant_rules`. La investigación recomienda **extender** ese diseño, no sustituirlo.

La clasificación de **Tambo** queda resuelta como **Alimentación → Tiendas de conveniencia**, porque la propia documentación legal de la marca usa la expresión “tiendas de conveniencia Tambo”. [S10](#s10) Mass se trata aparte como **tienda de descuento/proximidad**, no como la misma clase que Tambo.

La normalización automática debe ser conservadora. No se encontraron, de forma pública y suficientemente sistemática, descriptores bancarios/POS de todas las marcas. Por ello, el seed propuesto deja `transaction_aliases: []` y `transaction_descriptor_status: POR_VERIFICAR` salvo que Kipu obtenga evidencia real. Un nombre visible, un dominio o una razón social **no deben convertirse automáticamente en un descriptor bancario supuesto**.

Para logos, la estrategia recomendada es **curación propia con manifiesto de licencias**: assets locales verificados para Priority A, catálogo remoto en Supabase para el resto, proveedor externo solo como herramienta de ingestión/administración y fallback genérico determinístico en el dispositivo. Simple Icons puede ayudar, pero su propio disclaimer advierte que que el proyecto sea CC0 **no significa que cada icono de marca sea CC0**, y recomienda revisar licencias y permisos individuales. [S77](#s77)

---

## 2. Objetivo de la investigación

Construir una base inicial de comercios y servicios que:

1. sea reconocible para usuarios peruanos;
2. aparezca con frecuencia razonable en operaciones cotidianas;
3. funcione tanto para movimientos manuales como para captura asistida;
4. mantenga el texto fuente separado del comercio canónico;
5. permita preferencias personales de categoría;
6. funcione offline en Android/Room y sincronice con Supabase/PostgreSQL;
7. sea versionable y auditable;
8. no dependa de descriptores transaccionales inventados;
9. pueda crecer de Perú hacia otros mercados sin rediseñar todo el dominio;
10. trate logos y marcas con una política técnica y legal explícita.

---

## 3. Metodología

### 3.1 Fuentes y fecha de corte

Se priorizaron fuentes primarias: sitios corporativos, localizadores oficiales, aplicaciones oficiales, reguladores y entidades estatales. Cuando una marca era razonablemente conocida pero la evidencia primaria específica no quedó capturada en esta pasada, se mantuvo como `SOURCE_PENDING_*` en lugar de presentarla como verificada.

La investigación se realizó con corte al **29/09/2026**. Los datos operativos de comercios, precios, locales, planes y disponibilidad cambian; por eso el catálogo debe tener `verified_at`, `effective_from/effective_to` o una versión de catálogo.

### 3.2 Señales consideradas

Para cada entidad se consideró, cualitativamente:

- presencia actual en Perú;
- cobertura nacional, regional o digital;
- capacidad de generar cargos recurrentes o consumos cotidianos;
- probabilidad de aparecer en una cuenta, tarjeta, comprobante o billetera;
- reconocimiento suficiente para que preconfigurarla ahorre trabajo;
- disponibilidad de fuente primaria;
- estabilidad de la marca;
- ambigüedad entre marca, razón social, adquirente y canal de pago.

### 3.3 Escala de evidencia

| Estado | Interpretación |
|---|---|
| `VERIFIED_PRIMARY` | Presencia o dato respaldado por fuente oficial vigente. |
| `VERIFIED_REGULATOR` | Respaldado por regulador/Estado. |
| `VERIFIED_SECONDARY_PRIMARY_PLATFORM` | Presencia observada en una plataforma primaria de distribución/venta, pero falta fuente propia específica del comercio. |
| `PRESENCE_VERIFIED_PROVIDER_NEEDS_PE_PLAN_CAPTURE` | Servicio existente, pero falta capturar plan/precio/condición local exacta. |
| `SOURCE_PENDING_*` | Candidato razonable; no debe alimentar reglas automáticas hasta completar verificación. |

### 3.4 Asignación A/B/C

- **Priority A:** útil desde primera versión del catálogo porque cubre consumo masivo/cotidiano o un servicio recurrente muy común y cuenta con evidencia suficiente.
- **Priority B:** relevante para catálogo extendido; útil para muchos usuarios, pero más sectorial, regional o especializado.
- **Priority C:** válido para futuras iteraciones; no justifica inflar el seed inicial o aún requiere verificación adicional.

No se asignan porcentajes de “popularidad” cuando no existe una fuente comparable. Cuando sí hay un indicador oficial, se usa solo como contexto. Por ejemplo, OSIPTEL reporta el mercado móvil y permite justificar que Claro, Bitel, Entel y Movistar son actores materialmente presentes, sin convertir esa medición en un score universal de “frecuencia de gasto”. [S02](#s02)

---

## 4. Criterios de inclusión

Un comercio o servicio entra al catálogo si satisface una combinación suficiente de los siguientes criterios:

| Criterio | Pregunta operativa |
|---|---|
| Presencia real | ¿Existe/atiende actualmente en Perú? |
| Frecuencia | ¿Es razonable que un usuario lo vea repetidamente en sus finanzas? |
| Cobertura | ¿Tiene red física significativa o alcance digital nacional? |
| Trazabilidad | ¿Puede aparecer en tarjeta, cuenta, recibo, boleta/factura o billetera? |
| Reconocimiento | ¿El usuario entendería la marca sin aclaración adicional? |
| Valor de precarga | ¿Evita escribir/corregir el mismo comercio repetidamente? |
| Estabilidad | ¿Es una marca/servicio suficientemente estable para un catálogo de sistema? |
| Evidencia | ¿Hay fuente primaria o regulatoria disponible? |

**Exclusiones deliberadas:** bodegas individuales, restaurantes independientes sin presencia suficiente, profesionales independientes, pequeños comercios locales y contrapartes personales. Esos casos se resuelven con **merchant personalizado por usuario**.

---

## 5. Taxonomía propuesta de categorías

La clasificación recomendada usa pocas raíces y evita que “supermercado”, “farmacia”, “cine”, “gasolinera” o “IA” se conviertan en categorías raíz separadas. El INEI evidencia que alimentación, transporte, vivienda/servicios, comunicaciones, salud, educación, recreación y restaurantes/hoteles son divisiones relevantes del gasto de los hogares; Kipu las traduce a etiquetas más operativas. [S01](#s01)

### Alimentación

`Supermercados`, `Tiendas de descuento y proximidad`, `Tiendas de conveniencia`, `Restaurantes`, `Fast food`, `Pollerías`, `Chifas / comida china`, `Pizzerías`, `Cafeterías`, `Panaderías`, `Delivery y agregadores`.

### Vivienda y servicios

`Electricidad`, `Agua y saneamiento`, `Gas natural`, `Internet del hogar (cuando se trate como servicio domiciliario)`, `Mantenimiento/servicios del hogar`.

### Transporte y movilidad

`Transporte público urbano`, `Metro / tren urbano`, `Taxi y movilidad por aplicativo`, `Combustible y grifos`, `Estacionamientos`, `Peajes`.

### Salud y bienestar

`Farmacias`, `Clínicas`, `Laboratorios`, `Gimnasios`, `Ópticas`, `Odontología`, `Bienestar`.

### Comunicaciones

`Telefonía móvil`, `Internet fijo`, `Paquetes convergentes`, `Recargas`.

### Suscripciones digitales

`Streaming de video`, `Streaming de música`, `Inteligencia artificial`, `Productividad y software`, `Almacenamiento en la nube`, `Videojuegos`, `Apps y servicios digitales`.

### Compras y retail

`Tiendas por departamento`, `Marketplace / e-commerce`, `Ropa y moda`, `Calzado`, `Belleza y cuidado personal`, `Tecnología y accesorios`, `Electrodomésticos`, `Mejoramiento del hogar y ferretería`.

### Educación

`Universidades`, `Institutos`, `Idiomas`, `Cursos online`, `Librerías`, `Pagos académicos`.

### Finanzas y seguros

`Bancos`, `Cajas municipales`, `Billeteras digitales`, `Seguros`, `Comisiones y servicios financieros`.

### Entretenimiento y ocio

`Cine`, `Eventos`, `Parques / experiencias`, `Gaming presencial`.

### Viajes y alojamiento

`Aerolíneas`, `Buses interprovinciales`, `Hoteles`, `Reservas y turismo`.

### Mascotas

`Tiendas de mascotas`, `Veterinaria`, `Alimentos y servicios`.

### Servicios personales y del hogar

`Peluquería / barbería`, `Lavandería`, `Limpieza`, `Reparaciones`, `Courier / paquetería`.

### Gobierno y trámites

`Tributos y tasas`, `Documentos / trámites`, `Servicios públicos no domiciliarios`.

---

## 6. Taxonomía de subcategorías y cobertura de sectores


| Sector solicitado | Tratamiento en la taxonomía / catálogo |
|---|---|
| Supermercados / hipermercados | Alimentación → Supermercados. Hipermercado se conserva como atributo/formato de tienda, no como raíz distinta. |
| Conveniencia / minimarkets / bodegas | Alimentación → Tiendas de conveniencia o Tiendas de descuento y proximidad. Bodega pequeña no entra al catálogo global salvo marca; se crea como comercio personalizado. |
| Mercados | Solo marca/administrador reconocible y señal transaccional consistente; de lo contrario comercio personalizado. |
| Restaurantes / fast food / cafeterías / panaderías / pollerías / chifas | Alimentación con subcategorías específicas. |
| Delivery | Alimentación → Delivery y agregadores; el merchant puede ser el agregador o el restaurante según la evidencia del movimiento. |
| Farmacias / clínicas / laboratorios / salud / gimnasios | Salud y bienestar. |
| Transporte público / taxi apps / grifos / parking / peajes | Transporte y movilidad. |
| Aerolíneas / buses | Viajes y alojamiento. |
| Telecom / internet / móvil | Comunicaciones. |
| Electricidad / agua / gas | Vivienda y servicios. |
| Streaming / música / videojuegos / IA / cloud / productividad / software | Suscripciones digitales. |
| Educación / universidades / institutos / cursos / librerías | Educación. |
| Retail / moda / calzado / belleza / tecnología / electrodomésticos / hogar / ferretería / marketplace | Compras y retail. |
| Bancos / cajas / billeteras / seguros | Finanzas y seguros. No toda aparición de una entidad financiera es un gasto. |
| Mascotas | Mascotas. |
| Cine / eventos | Entretenimiento y ocio. |
| Hoteles / turismo | Viajes y alojamiento. |
| Servicios del hogar | Servicios personales y del hogar. |
| Tributos / tasas | Gobierno y trámites; se agrega porque son salidas reales y recurrentes que no encajan bien en retail ni servicios privados. |


### Decisión relevante: Tambo

**Tambo se clasifica como `Alimentación → Tiendas de conveniencia`.** La decisión no se basa en intuición: los términos legales de la empresa se refieren explícitamente a “tiendas seleccionadas de conveniencia Tambo a nivel nacional”. [S10](#s10)

### Decisión relevante: Mass

Mass se ubica en `Alimentación → Tiendas de descuento y proximidad`. Aunque vende alimentos y productos de hogar como un supermercado, para Kipu conviene distinguir su formato de proximidad/descuento de un supermercado completo. La prioridad A se apoya en su presencia oficial y utilidad como comercio cotidiano, no en un número de locales inventado. [S08](#s08)

### Regla para hipermercados

“Hipermercado” se recomienda como **atributo de formato/local**, no como subcategoría financiera distinta. Desde el punto de vista del gasto del usuario, Tottus/Plaza Vea/Metro/Wong siguen siendo `Supermercados`; separar “hipermercado” añade complejidad sin mejorar la lectura de presupuesto.

---

## 7. Catálogo de comercios — Priority A

**Lectura:** A significa “debería venir precargado en la primera versión del catálogo”, no “es objetivamente más popular que todos los B”.

| Comercio/servicio | Categoría → subcategoría | Tipo / alcance | Evidencia | Fuentes | Observación |
|---|---|---|---|---|---|
| Plaza Vea | Alimentación → Supermercados | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S06](#s06) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Tottus | Alimentación → Supermercados | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S07](#s07) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Metro | Alimentación → Supermercados | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S12](#s12) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Wong | Alimentación → Supermercados | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S11](#s11) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Mass | Alimentación → Tiendas de descuento y proximidad | `PHYSICAL` / `PERU` | `VERIFIED_PRIMARY` | [S08](#s08) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Tambo | Alimentación → Tiendas de conveniencia | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S09](#s09), [S10](#s10) | Clasificación respaldada por la propia marca: 'tiendas de conveniencia'. |
| KFC | Alimentación → Fast food | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S14](#s14) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Bembos | Alimentación → Fast food | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S13](#s13) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| McDonald's | Alimentación → Fast food | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S15](#s15) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Papa Johns | Alimentación → Pizzerías / fast food | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S16](#s16) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Starbucks | Alimentación → Cafeterías | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S84](#s84) | Presencia oficial; fuente específica de locales debe mantenerse versionada. |
| Dunkin' | Alimentación → Cafeterías y snacks | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S17](#s17) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Chinawok | Alimentación → Chifas / comida china | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S18](#s18) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Pardos Chicken | Alimentación → Pollerías | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S19](#s19), [S20](#s20) | La marca puede operar mediante distintas razones sociales; normalizar a la marca y separar razón social/sucursal. |
| Norky's | Alimentación → Pollerías | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S21](#s21) | El localizador muestra variación de razones sociales; no crear un merchant por razón social. |
| Rappi | Alimentación → Delivery y agregadores | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S22](#s22) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| PedidosYa | Alimentación → Delivery y agregadores | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S23](#s23) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Inkafarma | Salud y bienestar → Farmacias | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S24](#s24) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Mifarma | Salud y bienestar → Farmacias | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S25](#s25) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Smart Fit | Salud y bienestar → Gimnasios | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S30](#s30) | El sitio oficial mostraba 98 locales encontrados en la consulta de septiembre de 2026. |
| Metropolitano | Transporte y movilidad → Transporte público urbano | `PHYSICAL` / `REGIONAL` | `VERIFIED_PRIMARY` | [S38](#s38) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Línea 1 Metro de Lima | Transporte y movilidad → Metro / tren urbano | `PHYSICAL` / `REGIONAL` | `VERIFIED_PRIMARY` | [S39](#s39) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Uber | Transporte y movilidad → Taxi y movilidad por aplicativo | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S31](#s31) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| DiDi | Transporte y movilidad → Taxi y movilidad por aplicativo | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S32](#s32) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Primax | Transporte y movilidad → Combustible y grifos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S34](#s34) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Repsol | Transporte y movilidad → Combustible y grifos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S35](#s35) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Petroperú / Petrored | Transporte y movilidad → Combustible y grifos | `PHYSICAL` / `PERU` | `VERIFIED_PRIMARY` | [S36](#s36) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Claro | Comunicaciones → Telefonía móvil e internet | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S02](#s02), [S48](#s48) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Movistar | Comunicaciones → Telefonía móvil e internet | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S02](#s02), [S49](#s49) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Entel | Comunicaciones → Telefonía móvil e internet | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S02](#s02), [S50](#s50) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Bitel | Comunicaciones → Telefonía móvil e internet | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S02](#s02), [S51](#s51) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Luz del Sur | Vivienda y servicios → Electricidad | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S44](#s44) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Pluz | Vivienda y servicios → Electricidad | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S45](#s45) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Sedapal | Vivienda y servicios → Agua y saneamiento | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S46](#s46) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Cálidda | Vivienda y servicios → Gas natural | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S47](#s47) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Yape | Finanzas y seguros → Billeteras digitales | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S04](#s04) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Plin | Finanzas y seguros → Billeteras y transferencias | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S05](#s05) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| BCP | Finanzas y seguros → Bancos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Interbank | Finanzas y seguros → Bancos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| BBVA Perú | Finanzas y seguros → Bancos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Scotiabank Perú | Finanzas y seguros → Bancos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Netflix | Suscripciones digitales → Streaming de video | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S54](#s54) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Spotify | Suscripciones digitales → Streaming de música | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S55](#s55) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Disney+ | Suscripciones digitales → Streaming de video | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S56](#s56) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Google One | Suscripciones digitales → Almacenamiento en la nube | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S58](#s58) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| iCloud+ | Suscripciones digitales → Almacenamiento en la nube | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S59](#s59) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Falabella | Compras y retail → Tiendas por departamento / marketplace | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S71](#s71) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Ripley | Compras y retail → Tiendas por departamento | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S69](#s69) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Sodimac | Compras y retail → Mejoramiento del hogar y ferretería | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S70](#s70) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Promart | Compras y retail → Mejoramiento del hogar y ferretería | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S68](#s68) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Mercado Libre | Compras y retail → Marketplace / e-commerce | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S72](#s72) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Cineplanet | Entretenimiento y ocio → Cine | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S75](#s75) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| LATAM Airlines | Viajes y alojamiento → Aerolíneas | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S40](#s40) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |

---

## 8. Catálogo de comercios — Priority B

| Comercio/servicio | Categoría → subcategoría | Tipo / alcance | Evidencia | Fuentes | Observación |
|---|---|---|---|---|---|
| Listo! | Alimentación → Tiendas de conveniencia | `PHYSICAL` / `PERU` | `VERIFIED_PRIMARY` | [S34](#s34) | Integrada a estaciones Primax; separar compra de tienda de combustible si la señal lo permite. |
| Burger King | Alimentación → Fast food | `HYBRID` / `PERU` | `VERIFIED_SECONDARY_PRIMARY_PLATFORM` | [S23](#s23) | Presencia corroborada por plataforma de delivery; pendiente registrar fuente oficial de locales en el catálogo de evidencias. |
| Popeyes | Alimentación → Fast food | `HYBRID` / `PERU` | `VERIFIED_SECONDARY_PRIMARY_PLATFORM` | [S23](#s23) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Pizza Hut | Alimentación → Pizzerías / fast food | `HYBRID` / `PERU` | `VERIFIED_SECONDARY_PRIMARY_PLATFORM` | [S23](#s23) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Auna | Salud y bienestar → Clínicas | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S26](#s26) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| SANNA | Salud y bienestar → Clínicas | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S27](#s27) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| ROE | Salud y bienestar → Laboratorios | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S28](#s28) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Suiza Lab | Salud y bienestar → Laboratorios | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S29](#s29) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Cabify | Transporte y movilidad → Taxi y movilidad por aplicativo | `DIGITAL` / `REGIONAL` | `VERIFIED_PRIMARY` | [S33](#s33) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Lima Expresa | Transporte y movilidad → Peajes | `PHYSICAL` / `REGIONAL` | `VERIFIED_PRIMARY` | [S37](#s37) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Línea 2 Metro de Lima | Transporte y movilidad → Metro / tren urbano | `PHYSICAL` / `REGIONAL` | `VERIFIED_PRIMARY` | [S38](#s38) | Catálogo útil, pero la cobertura es más acotada que Línea 1 en la fecha de corte. |
| SKY Airline | Viajes y alojamiento → Aerolíneas | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S41](#s41) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| JetSMART | Viajes y alojamiento → Aerolíneas | `DIGITAL` / `PERU` | `VERIFIED_PRIMARY` | [S42](#s42) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Cruz del Sur | Viajes y alojamiento → Buses interprovinciales | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S43](#s43) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Win | Comunicaciones → Internet fijo | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S52](#s52) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| WOW | Comunicaciones → Internet fijo | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S53](#s53) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Max | Suscripciones digitales → Streaming de video | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S57](#s57) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| YouTube Premium | Suscripciones digitales → Streaming de video y música | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PLAN_CAPTURE` | — | Servicio global; conservar evidencia de plan/precio PE antes de precargar reglas de cobro. |
| ChatGPT | Suscripciones digitales → Inteligencia artificial | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S65](#s65) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Claude | Suscripciones digitales → Inteligencia artificial | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S66](#s66) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Google AI Pro | Suscripciones digitales → Inteligencia artificial | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S58](#s58) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Perplexity | Suscripciones digitales → Inteligencia artificial | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_AVAILABILITY` | — | No promover a reglas automáticas hasta capturar evidencia vigente específica para Perú. |
| Canva | Suscripciones digitales → Productividad y diseño | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S64](#s64) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Microsoft 365 | Suscripciones digitales → Productividad y software | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S62](#s62) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Adobe | Suscripciones digitales → Productividad y software | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S63](#s63) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Steam | Suscripciones digitales → Videojuegos | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S60](#s60) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| PlayStation | Suscripciones digitales → Videojuegos | `DIGITAL` / `GLOBAL` | `VERIFIED_PRIMARY` | [S61](#s61) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Xbox | Suscripciones digitales → Videojuegos | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_CATALOG` | — | Mantener como candidato hasta verificar oferta/moneda local vigente. |
| Aruma | Compras y retail → Belleza y cuidado personal | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S67](#s67) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Oechsle | Compras y retail → Tiendas por departamento | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S73](#s73) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Coolbox | Compras y retail → Tecnología y accesorios | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S74](#s74) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Maestro | Compras y retail → Mejoramiento del hogar y ferretería | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S70](#s70) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Cinemark | Entretenimiento y ocio → Cine | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S76](#s76) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Banco Falabella | Finanzas y seguros → Bancos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03), [S71](#s71) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Banco Ripley | Finanzas y seguros → Bancos | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Mibanco | Finanzas y seguros → Bancos / microfinanzas | `HYBRID` / `PERU` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| RIMAC | Finanzas y seguros → Seguros | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Pacífico Seguros | Finanzas y seguros → Seguros | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| MAPFRE | Finanzas y seguros → Seguros | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| La Positiva | Finanzas y seguros → Seguros | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| UPC | Educación → Universidades | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| PUCP | Educación → Universidades | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| UTP | Educación → Universidades e institutos | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| UPN | Educación → Universidades | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Cibertec | Educación → Institutos | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| BRITÁNICO | Educación → Idiomas | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| ICPNA | Educación → Idiomas | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Coursera | Educación → Cursos online | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Udemy | Educación → Cursos online | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |

---

## 9. Catálogo de comercios — Priority C

Priority C debe vivir en un **backlog de catálogo**. No se recomienda embarcar todos estos registros como seed hasta completar sus fuentes o demostrar utilidad suficiente.

| Comercio/servicio | Categoría → subcategoría | Tipo / alcance | Evidencia | Fuentes | Observación |
|---|---|---|---|---|---|
| Civa | Viajes y alojamiento → Buses interprovinciales | `HYBRID` / `PERU` | `SOURCE_PENDING_MAIN_SITE_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Oltursa | Viajes y alojamiento → Buses interprovinciales | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| inDrive | Transporte y movilidad → Taxi y movilidad por aplicativo | `DIGITAL` / `PERU` | `SOURCE_PENDING_PE_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Los Portales Estacionamientos | Transporte y movilidad → Estacionamientos | `PHYSICAL` / `REGIONAL` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Bodytech | Salud y bienestar → Gimnasios | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Boticas Perú | Salud y bienestar → Farmacias | `HYBRID` / `PERU` | `SOURCE_PENDING` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Hiraoka | Compras y retail → Tecnología y electrodomésticos | `HYBRID` / `REGIONAL` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Tiendas EFE | Compras y retail → Electrodomésticos y tecnología | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| La Curacao | Compras y retail → Electrodomésticos y tecnología | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Caja Arequipa | Finanzas y seguros → Cajas municipales | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Caja Huancayo | Finanzas y seguros → Cajas municipales | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Caja Piura | Finanzas y seguros → Cajas municipales | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Caja Cusco | Finanzas y seguros → Cajas municipales | `HYBRID` / `REGIONAL` | `VERIFIED_PRIMARY` | [S03](#s03) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| UNMSM | Educación → Universidades | `HYBRID` / `PERU` | `SOURCE_PENDING_PAYMENT_CHANNEL` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| UNI | Educación → Universidades | `HYBRID` / `PERU` | `SOURCE_PENDING_PAYMENT_CHANNEL` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Crisol | Educación → Librerías | `HYBRID` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Buscalibre | Educación → Librerías / e-commerce | `DIGITAL` / `PERU` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Pet Center | Mascotas → Tiendas y veterinaria | `HYBRID` / `REGIONAL` | `SOURCE_PENDING_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Booking.com | Viajes y alojamiento → Hoteles / reservas | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Airbnb | Viajes y alojamiento → Alojamiento / marketplace | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Google Play | Suscripciones digitales → Apps y videojuegos | `DIGITAL` / `GLOBAL` | `PRESENCE_GLOBAL` | [S58](#s58) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Apple Services | Suscripciones digitales → Apps y servicios | `DIGITAL` / `GLOBAL` | `PRESENCE_GLOBAL` | [S59](#s59) | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Amazon Prime / Prime Video | Suscripciones digitales → Streaming y membresías | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_LOCAL_OFFER_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Notion | Suscripciones digitales → Productividad y software | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |
| Dropbox | Suscripciones digitales → Almacenamiento en la nube | `DIGITAL` / `GLOBAL` | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — | Presencia verificada; prioridad es decisión de producto Kipu, no ranking de mercado. |

---

## 10. Servicios digitales internacionales relevantes para Perú

Estos servicios merecen tratamiento distinto de un comercio físico: suelen ser globales, recurren mensualmente/anualmente, pueden facturarse con nombres corporativos distintos a la marca visible y algunos usan tiendas de aplicaciones como intermediario.

| Servicio | Subcategoría | Prioridad | Verificación | Fuentes |
|---|---|---:|---|---|
| Netflix | Streaming de video | A | `VERIFIED_PRIMARY` | [S54](#s54) |
| Spotify | Streaming de música | A | `VERIFIED_PRIMARY` | [S55](#s55) |
| Disney+ | Streaming de video | A | `VERIFIED_PRIMARY` | [S56](#s56) |
| Google One | Almacenamiento en la nube | A | `VERIFIED_PRIMARY` | [S58](#s58) |
| iCloud+ | Almacenamiento en la nube | A | `VERIFIED_PRIMARY` | [S59](#s59) |
| Max | Streaming de video | B | `VERIFIED_PRIMARY` | [S57](#s57) |
| YouTube Premium | Streaming de video y música | B | `SOURCE_PENDING_PE_PLAN_CAPTURE` | — |
| ChatGPT | Inteligencia artificial | B | `VERIFIED_PRIMARY` | [S65](#s65) |
| Claude | Inteligencia artificial | B | `VERIFIED_PRIMARY` | [S66](#s66) |
| Google AI Pro | Inteligencia artificial | B | `VERIFIED_PRIMARY` | [S58](#s58) |
| Perplexity | Inteligencia artificial | B | `SOURCE_PENDING_PE_AVAILABILITY` | — |
| Canva | Productividad y diseño | B | `VERIFIED_PRIMARY` | [S64](#s64) |
| Microsoft 365 | Productividad y software | B | `VERIFIED_PRIMARY` | [S62](#s62) |
| Adobe | Productividad y software | B | `VERIFIED_PRIMARY` | [S63](#s63) |
| Steam | Videojuegos | B | `VERIFIED_PRIMARY` | [S60](#s60) |
| PlayStation | Videojuegos | B | `VERIFIED_PRIMARY` | [S61](#s61) |
| Xbox | Videojuegos | B | `SOURCE_PENDING_PE_CATALOG` | — |
| Google Play | Apps y videojuegos | C | `PRESENCE_GLOBAL` | [S58](#s58) |
| Apple Services | Apps y servicios | C | `PRESENCE_GLOBAL` | [S59](#s59) |
| Amazon Prime / Prime Video | Streaming y membresías | C | `SOURCE_PENDING_LOCAL_OFFER_CAPTURE` | — |
| Notion | Productividad y software | C | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — |
| Dropbox | Almacenamiento en la nube | C | `SOURCE_PENDING_PE_PAYMENT_CAPTURE` | — |

### Política de disponibilidad

- `GLOBAL` significa que la entidad canónica no pertenece a un mercado específico.
- La disponibilidad y precio local se modelan aparte (`merchant_market_presence` o metadato equivalente).
- ChatGPT y Claude cuentan con fuentes oficiales que incluyen a Perú entre los países compatibles. [S65](#s65) [S66](#s66)
- Netflix, Spotify y Disney+ mantienen superficies específicas para Perú. [S54](#s54) [S55](#s55) [S56](#s56)
- No se debe inferir un descriptor como `OPENAI*CHATGPT`, `GOOGLE*YOUTUBE` o similar si Kipu no tiene una observación verificada.

---

## 11. Comercio → categoría → subcategoría

La siguiente relación es una **sugerencia de catálogo**, no un vínculo rígido. Kipu debe permitir que el usuario cambie la categoría sin alterar el merchant.

| Comercio/servicio | Categoría sugerida | Subcategoría sugerida | Regla |
|---|---|---|---|
| Plaza Vea | Alimentación | Supermercados | `SUGGESTION_ONLY` |
| Tottus | Alimentación | Supermercados | `SUGGESTION_ONLY` |
| Metro | Alimentación | Supermercados | `SUGGESTION_ONLY` |
| Wong | Alimentación | Supermercados | `SUGGESTION_ONLY` |
| Mass | Alimentación | Tiendas de descuento y proximidad | `SUGGESTION_ONLY` |
| Tambo | Alimentación | Tiendas de conveniencia | `SUGGESTION_ONLY` |
| KFC | Alimentación | Fast food | `SUGGESTION_ONLY` |
| Bembos | Alimentación | Fast food | `SUGGESTION_ONLY` |
| McDonald's | Alimentación | Fast food | `SUGGESTION_ONLY` |
| Papa Johns | Alimentación | Pizzerías / fast food | `SUGGESTION_ONLY` |
| Starbucks | Alimentación | Cafeterías | `SUGGESTION_ONLY` |
| Dunkin' | Alimentación | Cafeterías y snacks | `SUGGESTION_ONLY` |
| Chinawok | Alimentación | Chifas / comida china | `SUGGESTION_ONLY` |
| Pardos Chicken | Alimentación | Pollerías | `SUGGESTION_ONLY` |
| Norky's | Alimentación | Pollerías | `SUGGESTION_ONLY` |
| Rappi | Alimentación | Delivery y agregadores | `SUGGESTION_ONLY` |
| PedidosYa | Alimentación | Delivery y agregadores | `SUGGESTION_ONLY` |
| Inkafarma | Salud y bienestar | Farmacias | `SUGGESTION_ONLY` |
| Mifarma | Salud y bienestar | Farmacias | `SUGGESTION_ONLY` |
| Smart Fit | Salud y bienestar | Gimnasios | `SUGGESTION_ONLY` |
| Metropolitano | Transporte y movilidad | Transporte público urbano | `SUGGESTION_ONLY` |
| Línea 1 Metro de Lima | Transporte y movilidad | Metro / tren urbano | `SUGGESTION_ONLY` |
| Uber | Transporte y movilidad | Taxi y movilidad por aplicativo | `SUGGESTION_ONLY` |
| DiDi | Transporte y movilidad | Taxi y movilidad por aplicativo | `SUGGESTION_ONLY` |
| Primax | Transporte y movilidad | Combustible y grifos | `SUGGESTION_ONLY` |
| Repsol | Transporte y movilidad | Combustible y grifos | `SUGGESTION_ONLY` |
| Petroperú / Petrored | Transporte y movilidad | Combustible y grifos | `SUGGESTION_ONLY` |
| Claro | Comunicaciones | Telefonía móvil e internet | `SUGGESTION_ONLY` |
| Movistar | Comunicaciones | Telefonía móvil e internet | `SUGGESTION_ONLY` |
| Entel | Comunicaciones | Telefonía móvil e internet | `SUGGESTION_ONLY` |
| Bitel | Comunicaciones | Telefonía móvil e internet | `SUGGESTION_ONLY` |
| Luz del Sur | Vivienda y servicios | Electricidad | `SUGGESTION_ONLY` |
| Pluz | Vivienda y servicios | Electricidad | `SUGGESTION_ONLY` |
| Sedapal | Vivienda y servicios | Agua y saneamiento | `SUGGESTION_ONLY` |
| Cálidda | Vivienda y servicios | Gas natural | `SUGGESTION_ONLY` |
| Yape | Finanzas y seguros | Billeteras digitales | `SUGGESTION_ONLY` |
| Plin | Finanzas y seguros | Billeteras y transferencias | `SUGGESTION_ONLY` |
| BCP | Finanzas y seguros | Bancos | `SUGGESTION_ONLY` |
| Interbank | Finanzas y seguros | Bancos | `SUGGESTION_ONLY` |
| BBVA Perú | Finanzas y seguros | Bancos | `SUGGESTION_ONLY` |
| Scotiabank Perú | Finanzas y seguros | Bancos | `SUGGESTION_ONLY` |
| Netflix | Suscripciones digitales | Streaming de video | `SUGGESTION_ONLY` |
| Spotify | Suscripciones digitales | Streaming de música | `SUGGESTION_ONLY` |
| Disney+ | Suscripciones digitales | Streaming de video | `SUGGESTION_ONLY` |
| Google One | Suscripciones digitales | Almacenamiento en la nube | `SUGGESTION_ONLY` |
| iCloud+ | Suscripciones digitales | Almacenamiento en la nube | `SUGGESTION_ONLY` |
| Falabella | Compras y retail | Tiendas por departamento / marketplace | `SUGGESTION_ONLY` |
| Ripley | Compras y retail | Tiendas por departamento | `SUGGESTION_ONLY` |
| Sodimac | Compras y retail | Mejoramiento del hogar y ferretería | `SUGGESTION_ONLY` |
| Promart | Compras y retail | Mejoramiento del hogar y ferretería | `SUGGESTION_ONLY` |
| Mercado Libre | Compras y retail | Marketplace / e-commerce | `SUGGESTION_ONLY` |
| Cineplanet | Entretenimiento y ocio | Cine | `SUGGESTION_ONLY` |
| LATAM Airlines | Viajes y alojamiento | Aerolíneas | `SUGGESTION_ONLY` |
| Listo! | Alimentación | Tiendas de conveniencia | `SUGGESTION_ONLY` |
| Burger King | Alimentación | Fast food | `SUGGESTION_ONLY` |
| Popeyes | Alimentación | Fast food | `SUGGESTION_ONLY` |
| Pizza Hut | Alimentación | Pizzerías / fast food | `SUGGESTION_ONLY` |
| Auna | Salud y bienestar | Clínicas | `SUGGESTION_ONLY` |
| SANNA | Salud y bienestar | Clínicas | `SUGGESTION_ONLY` |
| ROE | Salud y bienestar | Laboratorios | `SUGGESTION_ONLY` |
| Suiza Lab | Salud y bienestar | Laboratorios | `SUGGESTION_ONLY` |
| Cabify | Transporte y movilidad | Taxi y movilidad por aplicativo | `SUGGESTION_ONLY` |
| Lima Expresa | Transporte y movilidad | Peajes | `SUGGESTION_ONLY` |
| Línea 2 Metro de Lima | Transporte y movilidad | Metro / tren urbano | `SUGGESTION_ONLY` |
| SKY Airline | Viajes y alojamiento | Aerolíneas | `SUGGESTION_ONLY` |
| JetSMART | Viajes y alojamiento | Aerolíneas | `SUGGESTION_ONLY` |
| Cruz del Sur | Viajes y alojamiento | Buses interprovinciales | `SUGGESTION_ONLY` |
| Win | Comunicaciones | Internet fijo | `SUGGESTION_ONLY` |
| WOW | Comunicaciones | Internet fijo | `SUGGESTION_ONLY` |
| Max | Suscripciones digitales | Streaming de video | `SUGGESTION_ONLY` |
| YouTube Premium | Suscripciones digitales | Streaming de video y música | `SUGGESTION_ONLY` |
| ChatGPT | Suscripciones digitales | Inteligencia artificial | `SUGGESTION_ONLY` |
| Claude | Suscripciones digitales | Inteligencia artificial | `SUGGESTION_ONLY` |
| Google AI Pro | Suscripciones digitales | Inteligencia artificial | `SUGGESTION_ONLY` |
| Perplexity | Suscripciones digitales | Inteligencia artificial | `SUGGESTION_ONLY` |
| Canva | Suscripciones digitales | Productividad y diseño | `SUGGESTION_ONLY` |
| Microsoft 365 | Suscripciones digitales | Productividad y software | `SUGGESTION_ONLY` |
| Adobe | Suscripciones digitales | Productividad y software | `SUGGESTION_ONLY` |
| Steam | Suscripciones digitales | Videojuegos | `SUGGESTION_ONLY` |
| PlayStation | Suscripciones digitales | Videojuegos | `SUGGESTION_ONLY` |
| Xbox | Suscripciones digitales | Videojuegos | `SUGGESTION_ONLY` |
| Aruma | Compras y retail | Belleza y cuidado personal | `SUGGESTION_ONLY` |
| Oechsle | Compras y retail | Tiendas por departamento | `SUGGESTION_ONLY` |
| Coolbox | Compras y retail | Tecnología y accesorios | `SUGGESTION_ONLY` |
| Maestro | Compras y retail | Mejoramiento del hogar y ferretería | `SUGGESTION_ONLY` |
| Cinemark | Entretenimiento y ocio | Cine | `SUGGESTION_ONLY` |
| Banco Falabella | Finanzas y seguros | Bancos | `SUGGESTION_ONLY` |
| Banco Ripley | Finanzas y seguros | Bancos | `SUGGESTION_ONLY` |
| Mibanco | Finanzas y seguros | Bancos / microfinanzas | `SUGGESTION_ONLY` |
| RIMAC | Finanzas y seguros | Seguros | `SUGGESTION_ONLY` |
| Pacífico Seguros | Finanzas y seguros | Seguros | `SUGGESTION_ONLY` |
| MAPFRE | Finanzas y seguros | Seguros | `SUGGESTION_ONLY` |
| La Positiva | Finanzas y seguros | Seguros | `SUGGESTION_ONLY` |
| UPC | Educación | Universidades | `SUGGESTION_ONLY` |
| PUCP | Educación | Universidades | `SUGGESTION_ONLY` |
| UTP | Educación | Universidades e institutos | `SUGGESTION_ONLY` |
| UPN | Educación | Universidades | `SUGGESTION_ONLY` |
| Cibertec | Educación | Institutos | `SUGGESTION_ONLY` |
| BRITÁNICO | Educación | Idiomas | `SUGGESTION_ONLY` |
| ICPNA | Educación | Idiomas | `SUGGESTION_ONLY` |
| Coursera | Educación | Cursos online | `SUGGESTION_ONLY` |
| Udemy | Educación | Cursos online | `SUGGESTION_ONLY` |

### Por qué `SUGGESTION_ONLY`

La categoría expresa **para qué usó el usuario el dinero**, mientras que el comercio expresa **con quién ocurrió**. Un Tambo puede ser alimentación, higiene o incluso una compra para el hogar; una compra en Mercado Libre puede ser tecnología, mascotas o ropa. Por eso el catálogo debe sugerir, no imponer.

---

## 12. Alias y normalización

| Canónico | Alias/forma | Tipo | Estado | Nota |
|---|---|---|---|---|
| Tambo | Tambo; Tambo+ | `DISPLAY / DISPLAY_VARIANT` | `VERIFIED` | No se crean merchants por distrito/sucursal. |
| McDonald's | McDonald's; McDonalds | `DISPLAY / ORTHOGRAPHIC_NORMALIZATION` | `VERIFIED_NORMALIZATION` | Quitar diacríticos/apóstrofes para búsqueda no equivale a validar descriptor bancario. |
| Plaza Vea | Plaza Vea; PLAZA VEA | `DISPLAY / NORMALIZED_SEARCH` | `VERIFIED_NORMALIZATION` | Mayúsculas son forma normalizada de búsqueda, no descriptor. |
| Pardos Chicken | Pardos Chicken + razones sociales operadoras | `DISPLAY / LEGAL_ENTITY` | `PARTIAL` | La razón social debe poder apuntar a la marca canónica sin perder evidencia. |
| Norky's | Norky's + razones sociales por local | `DISPLAY / LEGAL_ENTITY` | `PARTIAL` | Mismo principio: marca canónica + sucursal/razón social separadas. |
| Google One | Google One | `DISPLAY` | `VERIFIED` | No asumir que todo cargo 'GOOGLE' es Google One. |
| ChatGPT | ChatGPT | `DISPLAY` | `VERIFIED` | No mapear genéricamente 'OPENAI' a ChatGPT sin evidencia específica del descriptor. |
| Yape | Yape | `PAYMENT_CHANNEL` | `VERIFIED` | Yape suele ser medio/canal; el receptor puede ser otro merchant o persona. |
| Plin | Plin | `PAYMENT_CHANNEL` | `VERIFIED` | Plin suele ser medio/canal; no convertir la billetera automáticamente en categoría de gasto. |

### 12.1 Tipos de alias recomendados

`DISPLAY`, `DISPLAY_VARIANT`, `LEGAL_ENTITY`, `DOMAIN`, `APP`, `TRANSACTION_DESCRIPTOR`, `POS_TEXT`, `NOTIFICATION_TEXT`.

Cada alias debe llevar:

- `verification_status`;
- `source_id` o evidencia;
- `country_code`;
- `matcher_kind`;
- vigencia;
- versión;
- opcionalmente `confidence_bps`.

### 12.2 Lo que NO debe llamarse alias verificado

Transformaciones técnicas como:

- pasar a mayúsculas;
- quitar acentos/apóstrofes;
- colapsar espacios;
- retirar símbolos no significativos;

son **normalización de búsqueda**, no evidencia de que el banco emita ese texto.

### 12.3 Cadena original

Kipu debe conservar tres cosas distintas:

1. `source_text_original`: lo que realmente llegó desde notificación/OCR/entrada;
2. `merchant_service_id`: el comercio canónico resuelto;
3. `matching_evidence`: qué regla/alias produjo la propuesta.

Nunca se debe sobreescribir la señal original con el nombre bonito.

---

## 13. Patrones de reconocimiento encontrados y pipeline recomendado

### 13.1 Hallazgo principal

No existe una fuente pública uniforme que exponga descriptores de tarjeta/POS para todas las marcas peruanas. Los sitios oficiales permiten verificar **marca, locales, dominio, app y razón social**, pero no necesariamente el texto exacto que emite cada adquirente, POS, banco o billetera.

Por esa razón, este documento **no inventa `transaction_aliases`**. El seed machine-readable los deja vacíos y marca `POR_VERIFICAR`.

### 13.2 Pipeline de reconocimiento

```text
señal original
   ↓
normalización Unicode
   ↓
uppercase para comparación + espacios/puntuación normalizados
   ↓
alias EXACT verificado
   ↓
regla PREFIX/CONTAINS verificada
   ↓
regla REGEX versionada
   ↓
señales auxiliares: dominio/app/razón social
   ↓
preferencia histórica del usuario
   ↓
si hay 0 o >1 candidatos fuertes → Bandeja de revisión
```

### 13.3 Reglas de seguridad

- No usar distancia Levenshtein/fuzzy como única razón para registrar dinero automáticamente.
- No retirar prefijos de adquirentes (`IZIPAY`, `NIUBIZ`, etc.) con una regla global hasta probar que no genera colisiones.
- No considerar el ejemplo de una HU como prueba de un descriptor real de producción.
- Una regla de usuario nunca debe alterar el catálogo global.
- Dos reglas que conducen a merchants diferentes deben producir `AMBIGUOUS`, no elegir “la primera”.

### 13.4 Adquirente ≠ comercio

Si el texto incluye un adquirente y una marca, el modelo debería poder representar ambos conceptos. En una evolución posterior puede existir `payment_processor`/`acquirer`, sin convertirlo en merchant del gasto.

---

## 14. Comercios ambiguos o difíciles de clasificar

| Caso | Riesgo | Decisión recomendada |
|---|---|---|
| **Tambo** | Conveniencia vs minimarket vs supermercado | Resuelto: `Tiendas de conveniencia`, apoyado por fuente oficial. [S10](#s10) |
| **Mass** | Supermercado vs discount/proximidad | `Tiendas de descuento y proximidad`; no equipararlo a hipermercado. |
| **Listo!** | Compra de tienda vs estación Primax | Merchant `Listo!` cuando la evidencia lo identifique; `Primax` para combustible/estación. |
| **Rappi / PedidosYa** | Agregador cobra, pero el consumo fue en otro restaurante | Conservar payee observado; permitir `underlying_merchant_id` opcional si hay evidencia. |
| **Yape / Plin** | Son canal de pago, no necesariamente el receptor económico | Modelar `payment_channel`; merchant/counterparty por separado. |
| **BCP / Interbank / BBVA / Scotiabank** | Transferencia propia puede parecer gasto | Banco solo como merchant si realmente es contraparte de una comisión/servicio; transferencias internas no son gasto. |
| **Falabella.com** | Marketplace, tienda Falabella y sellers | Guardar canal/marketplace y seller cuando exista; no asumir que todo cobro es “Saga Falabella”. |
| **Pardos / Norky's** | Varias razones sociales/franquicias | Resolver a una marca canónica y conservar legal entity/sucursal en metadata. |
| **Google / Apple** | Un mismo proveedor cobra múltiples servicios | No mapear un texto genérico `GOOGLE`/`APPLE` a un producto concreto. Requiere descriptor específico. |
| **Servicios públicos** | Cobertura geográfica | Filtrar/sugerir por región del usuario; no mostrar todas las utilities como si fueran nacionales. |

---

## 15. Estrategia para comercios personalizados

Un comercio personalizado es un dato del usuario, no una ampliación automática del catálogo global.

### Flujo

1. búsqueda sin resultados;
2. opción “Usar este nombre”;
3. crear `merchant_services.user_id = <usuario>`;
4. generar `normalized_name`;
5. avatar fallback con inicial/color determinístico;
6. permitir elegir categoría;
7. sincronizar como dato privado;
8. jamás promoverlo al catálogo global sin proceso de moderación/evidencia.

### Dedupe local

Antes de crear, buscar:

- coincidencia exacta normalizada entre merchants del usuario;
- coincidencia exacta contra catálogo del sistema;
- alias verificado del sistema;
- colisión de nombre con merchant eliminado/tombstone.

Un comercio personalizado puede posteriormente vincularse a una entidad global mediante una acción explícita, conservando historial.

---

## 16. Investigación de iconos y logos

### Principios

1. **Una imagen en Internet no es una licencia.**
2. Un favicon no se vuelve “libre” por ser descargable.
3. Una librería de iconos no transfiere automáticamente derechos sobre las marcas.
4. El asset visual debe tener procedencia, versión y estado de revisión.
5. Si la situación legal no está clara, el fallback genérico es mejor que una recreación no autorizada.

Simple Icons ofrece miles de SVG de marcas, pero su disclaimer pide revisar licencia y pautas por icono y aclara que la licencia CC0 del proyecto no convierte automáticamente cada marca en CC0. [S77](#s77) [S78](#s78)

### Tipos de asset

- `OFFICIAL_LOGO`: descargado de kit/guía/fuente de la marca.
- `LIBRARY_ICON`: obtenido de Simple Icons/Iconify u otra biblioteca, conservando licencia y fuente.
- `FAVICON`: recurso técnico del sitio, **no tratado como logo oficial ni como permiso**.
- `RECREATION`: desaconsejada para marcas registradas salvo autorización.
- `GENERIC_FALLBACK`: inicial + fondo determinístico o icono semántico; no pretende imitar la marca.

---

## 17. Comparación de proveedores y librerías de logos

| Alternativa | Cobertura / calidad | Formato | Licencia / marca | Coste | Internet/API | Android | Riesgo / recomendación |
|---|---|---|---|---|---|---|---|
| **Brand assets / media kits oficiales** | Menor cobertura, máxima fidelidad | SVG/PNG/EPS/PDF, depende de marca | Reglas de marca propias | Normalmente sin coste de descarga | No en runtime si se empaqueta | Excelente tras convertir/optimizar | **Primera opción** para A. Revisar guía por marca. |
| **Simple Icons** | Amplia para marcas globales; menos Perú local | SVG | Proyecto CC0, pero derechos/licencias de cada marca pueden diferir | Gratis | No obligatorio; se puede vendorizar | Muy buena | Útil para ingestión curada, nunca “licencia automática”. [S77](#s77) |
| **Iconify** | Muy amplia, agrega múltiples sets | SVG/datos | La licencia depende del icon set | Gratis/open-source en muchos sets | Puede autohospedarse | Buena si se materializa asset | Útil como índice; revisar licencia colección por colección. [S79](#s79) |
| **Brandfetch** | Alta para marcas conocidas | API/assets de marca | Sujeto a términos/proveedor y uso de marca | Planes gratuitos/pagados según uso | API | Buena vía backend/admin | Útil para pipeline de curación; no crear dependencia crítica del cliente. [S80](#s80) |
| **Logo.dev** | Alta para dominios conocidos | PNG/SVG/API según plan | Sujeto a términos y marcas | Free/paid; revisar plan vigente | API | Fácil | Alternativa de proveedor, preferible en backoffice. [S81](#s81) |
| **Favicons oficiales** | Casi universal, calidad variable | ICO/PNG/SVG | No otorga licencia por sí mismo | Gratis | Normalmente web | Fácil | Solo provisional/admin; no presentarlo como logo oficial sin revisión. |
| **Supabase Storage propio** | La cobertura la controla Kipu | Cualquier formato | Kipu conserva metadata de derechos; almacenar no crea derechos | Coste de storage/egress | Internet para remoto | Excelente | **Canal de distribución recomendado** para B/C. [S82](#s82) |
| **Assets locales Android** | Curado y limitado | VectorDrawable/WebP/PNG | Misma obligación legal | Tamaño APK/AAB | No | Excelente offline | **Recomendado para A** y fallback. Vector Asset Studio soporta importar SVG a vector drawable. [S83](#s83) |
| **Fallback inicial + color** | 100% | Generado | Sin uso del logo de terceros | Casi cero | No | Excelente | **Fallback final obligatorio**. |

### Recomendación de proveedor

No elegir un único “Logo API” como fuente de verdad. Usar proveedores como **herramientas de descubrimiento/ingesta**, y luego pasar cada asset por validación, checksum, metadata legal y almacenamiento controlado por Kipu.

---

## 18. Riesgos legales y de marca

Este documento propone controles de producto, no sustituye asesoría legal.

### Riesgos

- uso de marca fuera de sus brand guidelines;
- atribuir licencia inexistente;
- cambiar colores/proporciones prohibidas;
- cachear assets si el contrato del proveedor no lo permite;
- usar un favicon como logo comercial;
- mantener un logo retirado o rebrandeado;
- usar una recreación generada por IA demasiado parecida a una marca protegida.

### Controles mínimos

```text
logo_source
source_url
license_type
license_url
trademark_guideline_url
commercial_use_review
reviewed_by
reviewed_at
asset_checksum
asset_version
effective_from
effective_to
```

### Regla de degradación

Si `commercial_use_review != APPROVED`, el cliente debe mostrar `GENERIC_FALLBACK`, no intentar “completar” la marca con una imagen encontrada dinámicamente.

---

## 19. Arquitectura recomendada para assets

La arquitectura de cuatro niveles propuesta originalmente es buena, con un cambio importante: **el proveedor externo no debe ser una dependencia de runtime del Android**.

### Nivel 0 — fallback siempre disponible

Incluido en Android:

- inicial;
- color derivado de `merchant_id`;
- icono semántico por subcategoría;
- accesibilidad/contraste controlados.

### Nivel 1 — bundle local Priority A

Assets aprobados de los merchants A:

```text
res/drawable/merchant_<canonical_id>.xml|webp
manifest local:
  merchant_id
  asset_version
  checksum
  license_status
```

Ventajas: instantáneo, offline, no hay salto visual.

### Nivel 2 — catálogo remoto Kipu

Bucket Supabase, por ejemplo:

```text
merchant-assets/
  v2026.09/
    tambo/logo.svg
    netflix/logo.svg
```

La app recibe un manifiesto versionado y cachea. El backend puede retirar/reemplazar assets sin actualización de APK.

### Nivel 3 — proveedores externos, solo backoffice

Brandfetch/Logo.dev/Iconify/Simple Icons se usan para:

- descubrir;
- comparar;
- obtener candidato;
- revisar;
- importar al storage propio cuando términos/licencia lo permitan.

### Nivel 4 — favicon provisional

Solo en backoffice para ayudar al revisor. No se promueve automáticamente al cliente.

### Resolución Android

```text
asset local aprobado
→ asset remoto aprobado y cacheado
→ icono semántico de subcategoría
→ inicial + color determinístico
```

---

## 20. Propuesta de modelo de datos

### 20.1 Lo que Kipu ya tiene y debe aprovecharse

El modelo actual contiene:

- `categories`: árbol de máximo dos niveles;
- `merchant_services`: merchant canónico, independiente de categoría, con `user_id NULL` para sistema;
- `merchant_rules`: patrón → merchant y opcionalmente categoría;
- Room + Supabase;
- `revision` y `deleted_at`.

Por tanto, **no recomiendo crear `merchant_categories` y `merchant_subcategories` nuevas**: duplicarían `categories`.

### 20.2 Extensión opcional de `merchant_services`

Sin romper la tabla actual:

```sql
-- propuesta conceptual, NO migración automática
canonical_key       TEXT UNIQUE
display_name        TEXT
legal_or_parent_name TEXT NULL
merchant_type       TEXT CHECK (merchant_type IN ('PHYSICAL','DIGITAL','HYBRID'))
country_code        CHAR(2) NULL
scope               TEXT CHECK (scope IN ('GLOBAL','PERU','REGIONAL','LOCAL'))
priority_tier       CHAR(1) CHECK (priority_tier IN ('A','B','C'))
website_url         TEXT NULL
is_active           BOOLEAN NOT NULL DEFAULT TRUE
catalog_version     TEXT NULL
verified_at         TIMESTAMPTZ NULL
verification_status TEXT NOT NULL
```

`name` puede mantenerse por compatibilidad y evolucionar hacia `display_name`.

### 20.3 Nueva tabla opcional `merchant_aliases`

```sql
merchant_aliases (
  id UUID PK,
  merchant_service_id UUID NOT NULL,
  user_id UUID NULL,
  alias_text TEXT NOT NULL,
  normalized_alias TEXT NOT NULL,
  alias_type TEXT NOT NULL,
  matcher_kind TEXT NOT NULL,          -- EXACT/PREFIX/CONTAINS/REGEX
  verification_status TEXT NOT NULL,   -- VERIFIED/POR_VERIFICAR/REJECTED
  evidence_source_id UUID NULL,
  confidence_bps INTEGER NULL,
  country_code CHAR(2) NULL,
  effective_from DATE NULL,
  effective_to DATE NULL,
  revision BIGINT NOT NULL,
  deleted_at TIMESTAMPTZ NULL
)
```

**Diferencia con `merchant_rules`:** `merchant_aliases` describe identidades/nombres; `merchant_rules` conserva reglas ejecutables de inferencia. No toda variante de nombre debe convertirse en regex.

### 20.4 Nueva tabla `merchant_assets`

```sql
merchant_assets (
  id UUID PK,
  merchant_service_id UUID NOT NULL,
  asset_kind TEXT NOT NULL,
  storage_path TEXT NULL,
  local_resource_key TEXT NULL,
  format TEXT NULL,
  source_url TEXT NULL,
  provider TEXT NULL,
  license_type TEXT NULL,
  license_url TEXT NULL,
  trademark_guideline_url TEXT NULL,
  commercial_use_review TEXT NOT NULL,
  checksum_sha256 TEXT NULL,
  asset_version TEXT NOT NULL,
  brand_color TEXT NULL,
  fallback_color TEXT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  revision BIGINT NOT NULL,
  deleted_at TIMESTAMPTZ NULL
)
```

### 20.5 Nueva tabla `merchant_catalog_sources`

Permite que un cambio de prioridad, nombre, website o disponibilidad sea auditable:

```sql
merchant_catalog_sources (
  id UUID PK,
  merchant_service_id UUID NULL,
  source_type TEXT NOT NULL,
  source_url TEXT NOT NULL,
  title TEXT,
  publisher TEXT,
  observed_at TIMESTAMPTZ NOT NULL,
  valid_from DATE NULL,
  valid_to DATE NULL,
  evidence_hash TEXT NULL,
  notes TEXT NULL
)
```

### 20.6 Clasificación por defecto sin acoplar merchant/categoría

No añadir `category_id NOT NULL` a `merchant_services`.

Opción recomendada:

```sql
merchant_category_suggestions (
  merchant_service_id UUID NOT NULL,
  category_id UUID NOT NULL,
  country_code CHAR(2) NULL,
  source TEXT NOT NULL,       -- SYSTEM / USER_HISTORY
  weight_bps INTEGER,
  PRIMARY KEY (...)
)
```

La preferencia del usuario gana sobre la sugerencia del sistema.

### 20.7 Sucursal/local: solo si se necesita

No crear `Tambo Miraflores`, `Tambo San Isidro`, etc. como merchants. Una futura `merchant_locations` puede guardar:

```text
merchant_service_id
external_location_id
display_label
address
lat/lon
legal_entity_name
```

El movimiento sigue apuntando a `merchant_service_id = tambo`.

---

## 21. Estrategia local-first y sincronización

### Catálogo de sistema

- Supabase mantiene el catálogo canónico versionado.
- Android trae un snapshot mínimo A.
- En primer sync se descargan deltas B/C necesarios.
- Room es la fuente inmediata para búsqueda/autocomplete.
- El picker nunca debe bloquearse esperando red.

### Comercios del usuario

- UUID generado en cliente.
- `user_id` obligatorio.
- outbox local;
- sync idempotente;
- tombstone en eliminación;
- RLS impide cruce de usuarios.

### Manifiesto de catálogo

```json
{
  "catalog_version": "2026.09",
  "min_app_version": 1,
  "merchants_checksum": "...",
  "assets_version": "2026.09.1",
  "generated_at": "2026-09-29T00:00:00Z"
}
```

### Actualizaciones

1. bajar manifest;
2. si `catalog_version` no cambia, no hacer nada;
3. descargar delta;
4. aplicar en una transacción Room;
5. invalidar solo assets cuyo `asset_version` cambió;
6. nunca sobrescribir merchants privados del usuario.

---

## 22. Estrategia de actualización futura del catálogo

### Cadencia propuesta

- **mensual:** disponibilidad, dominios, cierres/rebrandings, telecom, digital;
- **trimestral:** prioridades, retail, salud, transporte;
- **semestral:** auditoría de logos/licencias;
- **por evento:** rebranding, adquisición, salida de mercado, fusión.

### Flujo editorial

```text
candidate
→ evidence collected
→ taxonomy review
→ alias review
→ logo/legal review
→ APPROVED
→ seed/delta
→ telemetry aggregate
→ periodic reevaluation
```

### Métricas agregadas útiles

Sin guardar texto sensible de usuarios:

- búsquedas de merchant sin resultado;
- porcentaje de selección tras búsqueda;
- merchant custom creado después de 0 resultados;
- frecuencia agregada de merchant del sistema;
- ambigüedades por regla;
- tasa de corrección de merchant sugerido.

Estas métricas permiten ascender B→A o C→B con datos reales de Kipu, en vez de seguir intuición indefinidamente.

---

## 23. Consideraciones para internacionalización

### Scope

| Scope | Significado | Ejemplo |
|---|---|---|
| `GLOBAL` | Marca/servicio transversal; disponibilidad por país aparte | Netflix, Spotify, ChatGPT |
| `PERU` | Catálogo pensado para mercado peruano | Tambo, Plaza Vea, Yape |
| `REGIONAL` | Opera en ciertas ciudades/regiones | Sedapal, Luz del Sur, Metropolitano |
| `LOCAL` | Comercio específico; preferentemente user-owned | cafetería de barrio |

### Campos clave

- `country_code` ISO 3166-1 alpha-2;
- `locale`;
- `currency_code` solo para ofertas/precios, no para identidad;
- `scope`;
- `market_presence`;
- `effective_from/effective_to`.

### Separar identidad de presencia

Netflix debe tener un `merchant_id` global; `merchant_market_presence` responde si está disponible en PE, CL, MX, etc. No duplicar `netflix_pe`, `netflix_cl` salvo razones operativas reales.

---

## 24. Riesgos y decisiones pendientes

### Riesgos técnicos

- reglas demasiado amplias que asignen el merchant incorrecto;
- crecimiento excesivo del catálogo y peor autocomplete;
- assets desactualizados;
- conflicto entre nombre de marca y razón social;
- marketplace/agregador que oculta al merchant real;
- dependencia de un proveedor externo de logos;
- fuzzy matching agresivo.

### Riesgos de datos

- tratar una hipótesis de descriptor como evidencia;
- promover un comercio personalizado al sistema;
- confundir canal de pago con merchant;
- clasificar transferencias internas como gasto;
- usar local/store count no verificable.

### Decisiones pendientes antes de producción

1. definir si el catálogo A completo se empaqueta en APK o solo los 20–30 más frecuentes según beta;
2. decidir si `merchant_aliases` se implementa como tabla propia o se incorpora a `merchant_rules` con un `rule_type`;
3. crear dataset de descriptores **anonimizado y consentido** para validar reglas;
4. definir proceso legal de revisión de logos;
5. acordar si marketplace y seller se guardan como dos campos;
6. crear `payment_channel` separado para Yape/Plin/adquirentes;
7. decidir política de country-specific availability para servicios globales;
8. validar con telemetría beta qué Priority B merece pasar a A.

---

## 25. Fuentes consultadas

### Fuentes internas de Kipu utilizadas para compatibilidad

- `01_Kipu_V4.2_Especificacion_Producto.md`
- `02_Kipu_V4.2_Product_Backlog.md`
- `03_Kipu_V4.2_Arquitectura_y_Datos.md`
- `04_Kipu_V4.2_Trazabilidad_y_Dependencias.md`
- `Descripción de Pantallas según Historias de Usuario.md`

### Fuentes externas

<a id="s01"></a>
- **S01. INEI — Índice de Precios al Consumidor, marzo 2026** — [Oficial/Estado](https://www.inei.gob.pe/media/MenuRecursivo/boletines/boletin-precios-mar2026.pdf) — Tipo: Oficial/Estado.
<a id="s02"></a>
- **S02. OSIPTEL — Mercado móvil, I trimestre 2026** — [Regulador](https://www.osiptel.gob.pe/portal-del-usuario/noticias/mercado-peruano-alcanzo-los-39-4-millones-de-lineas-moviles-al-cierre-del-primer-trimestre-de-2026/) — Tipo: Regulador.
<a id="s03"></a>
- **S03. SBS — Empresas autorizadas a captar depósitos** — [Regulador](https://www.sbs.gob.pe/app/pp/empresasweb/Paginas/EmpCaptarDep.aspx) — Tipo: Regulador.
<a id="s04"></a>
- **S04. Yape — sitio oficial** — [Oficial](https://www.yape.com.pe/) — Tipo: Oficial.
<a id="s05"></a>
- **S05. Plin — sitio oficial** — [Oficial](https://plin.pe/) — Tipo: Oficial.
<a id="s06"></a>
- **S06. Plaza Vea — nuestras tiendas** — [Oficial](https://vea.plazavea.com.pe/principal/nuestras-tiendas) — Tipo: Oficial.
<a id="s07"></a>
- **S07. Tottus Perú — horario/tiendas** — [Oficial](https://www.tottus.com.pe/tottus-pe/content/horario-tiendas) — Tipo: Oficial.
<a id="s08"></a>
- **S08. Tiendas Mass — sitio oficial** — [Oficial](https://www.tiendasmass.com.pe/) — Tipo: Oficial.
<a id="s09"></a>
- **S09. Tambo — locales** — [Oficial](https://www.tambo.pe/locales/) — Tipo: Oficial.
<a id="s10"></a>
- **S10. Tambo — legales; la marca denomina sus locales 'tiendas de conveniencia'** — [Oficial](https://www.tambo.pe/legales) — Tipo: Oficial.
<a id="s11"></a>
- **S11. Wong — tiendas** — [Oficial](https://www.wong.pe/stores) — Tipo: Oficial.
<a id="s12"></a>
- **S12. Metro — tiendas** — [Oficial](https://www.metro.pe/stores) — Tipo: Oficial.
<a id="s13"></a>
- **S13. Bembos — locales** — [Oficial](https://www.bembos.com.pe/locales) — Tipo: Oficial.
<a id="s14"></a>
- **S14. KFC Perú — store locator** — [Oficial](https://www.kfc.com.pe/es/store-locator) — Tipo: Oficial.
<a id="s15"></a>
- **S15. McDonald's Perú — restaurantes** — [Oficial](https://www.mcdonalds.com.pe/restaurantes) — Tipo: Oficial.
<a id="s16"></a>
- **S16. Papa Johns Perú — locales** — [Oficial](https://www.papajohns.com.pe/locales) — Tipo: Oficial.
<a id="s17"></a>
- **S17. Dunkin Perú — locales** — [Oficial](https://www.dunkin.pe/locales) — Tipo: Oficial.
<a id="s18"></a>
- **S18. Chinawok — locales** — [Oficial](https://www.chinawok.com.pe/locales) — Tipo: Oficial.
<a id="s19"></a>
- **S19. Pardos Chicken — restaurantes** — [Oficial](https://www.pardoschicken.pe/restaurantes) — Tipo: Oficial.
<a id="s20"></a>
- **S20. Pardos Chicken — términos y condiciones** — [Oficial](https://www.pardoschicken.pe/terminos-y-condiciones) — Tipo: Oficial.
<a id="s21"></a>
- **S21. Norky's — locales** — [Oficial](https://norkys.pe/locales) — Tipo: Oficial.
<a id="s22"></a>
- **S22. Rappi Perú** — [Oficial](https://www.rappi.com.pe/) — Tipo: Oficial.
<a id="s23"></a>
- **S23. PedidosYa Perú — cadenas** — [Oficial](https://www.pedidosya.com.pe/cadenas) — Tipo: Oficial.
<a id="s24"></a>
- **S24. Inkafarma — app oficial Google Play** — [App oficial](https://play.google.com/store/apps/details?id=com.globant.inkafarma&hl=es) — Tipo: App oficial.
<a id="s25"></a>
- **S25. Mifarma — app oficial Google Play** — [App oficial](https://play.google.com/store/apps/details?id=com.globant.mifarma&hl=es) — Tipo: App oficial.
<a id="s26"></a>
- **S26. Auna Perú — sedes** — [Oficial](https://auna.org/pe/sedes) — Tipo: Oficial.
<a id="s27"></a>
- **S27. SANNA — red/sedes** — [Oficial](https://www.sanna.pe/red-sanna/centros-clinicos/) — Tipo: Oficial.
<a id="s28"></a>
- **S28. ROE Laboratorio — sedes** — [Oficial](https://www.labroe.com/sedes) — Tipo: Oficial.
<a id="s29"></a>
- **S29. Suiza Lab — sedes** — [Oficial](https://www.suizalab.com/especialidades/sedes/) — Tipo: Oficial.
<a id="s30"></a>
- **S30. Smart Fit Perú — gimnasios** — [Oficial](https://www.smartfit.com.pe/gimnasios) — Tipo: Oficial.
<a id="s31"></a>
- **S31. Uber — ciudades de Perú** — [Oficial](https://www.uber.com/global/es/r/peru/cities/) — Tipo: Oficial.
<a id="s32"></a>
- **S32. DiDi Pasajero Perú** — [Oficial](https://web.didiglobal.com/pe/pasajero/) — Tipo: Oficial.
<a id="s33"></a>
- **S33. Cabify — tarifas Lima** — [Oficial](https://cabify.com/pe/tarifas/lima) — Tipo: Oficial.
<a id="s34"></a>
- **S34. Primax — estaciones de servicio** — [Oficial](https://creaturuta.primax.com.pe/estaciones-de-servicio) — Tipo: Oficial.
<a id="s35"></a>
- **S35. Repsol Perú — localizador de estaciones** — [Oficial](https://www.repsol.pe/es/productos-servicios/estaciones-servicio/localizador-de-estaciones/index.cshtml) — Tipo: Oficial.
<a id="s36"></a>
- **S36. Petroperú — red comercial** — [Oficial/Estado](https://comercial.petroperu.com.pe/) — Tipo: Oficial/Estado.
<a id="s37"></a>
- **S37. Lima Expresa — preguntas frecuentes/peajes** — [Oficial](https://www.limaexpresa.pe/preguntas-frecuentes/) — Tipo: Oficial.
<a id="s38"></a>
- **S38. ATU — portal institucional** — [Estado](https://www.gob.pe/atu) — Tipo: Estado.
<a id="s39"></a>
- **S39. Línea 1 Metro de Lima — sitio oficial** — [Oficial](https://www.lineauno.pe/) — Tipo: Oficial.
<a id="s40"></a>
- **S40. LATAM Airlines Perú** — [Oficial](https://www.latamairlines.com/pe/es) — Tipo: Oficial.
<a id="s41"></a>
- **S41. SKY Airline — destinos, incluye Perú** — [Oficial](https://www.skyairline.com/flights/es/destinos) — Tipo: Oficial.
<a id="s42"></a>
- **S42. JetSMART Perú — sala de prensa 2026** — [Oficial](https://jetsmart.com/pe/es/minisitios/prensa) — Tipo: Oficial.
<a id="s43"></a>
- **S43. Cruz del Sur — pasajes** — [Oficial](https://www.cruzdelsur.com.pe/pasajes/) — Tipo: Oficial.
<a id="s44"></a>
- **S44. Luz del Sur — sitio oficial** — [Oficial](https://www.luzdelsur.pe/es) — Tipo: Oficial.
<a id="s45"></a>
- **S45. Pluz — sitio oficial** — [Oficial](https://www.pluz.pe/) — Tipo: Oficial.
<a id="s46"></a>
- **S46. Sedapal — portal institucional** — [Estado](https://www.gob.pe/sedapal) — Tipo: Estado.
<a id="s47"></a>
- **S47. Cálidda — sitio oficial** — [Oficial](https://www.calidda.com.pe/) — Tipo: Oficial.
<a id="s48"></a>
- **S48. Claro Perú — Mi Claro** — [Oficial](https://www.claro.com.pe/personas/mi-claro/) — Tipo: Oficial.
<a id="s49"></a>
- **S49. Movistar Perú — sitio oficial** — [Oficial](https://www.movistar.com.pe/) — Tipo: Oficial.
<a id="s50"></a>
- **S50. Entel Perú — sitio oficial** — [Oficial](https://www.entel.pe/) — Tipo: Oficial.
<a id="s51"></a>
- **S51. Bitel Perú — sitio oficial** — [Oficial](https://bitel.com.pe/) — Tipo: Oficial.
<a id="s52"></a>
- **S52. Win Perú — sitio oficial** — [Oficial](https://win.pe/) — Tipo: Oficial.
<a id="s53"></a>
- **S53. WOW Perú — sitio oficial** — [Oficial](https://wowperu.pe/) — Tipo: Oficial.
<a id="s54"></a>
- **S54. Netflix Perú** — [Oficial](https://www.netflix.com/pe/) — Tipo: Oficial.
<a id="s55"></a>
- **S55. Spotify Premium Perú** — [Oficial](https://www.spotify.com/pe/premium/) — Tipo: Oficial.
<a id="s56"></a>
- **S56. Disney+ Perú** — [Oficial](https://www.disneyplus.com/es-pe) — Tipo: Oficial.
<a id="s57"></a>
- **S57. Max — ayuda para suscripción/impuestos por país** — [Oficial](https://help.max.com/) — Tipo: Oficial.
<a id="s58"></a>
- **S58. Google One / Google AI — planes** — [Oficial](https://one.google.com/about/plans?hl=es) — Tipo: Oficial.
<a id="s59"></a>
- **S59. Apple — planes iCloud+ para Latinoamérica** — [Oficial](https://support.apple.com/en-la/108047) — Tipo: Oficial.
<a id="s60"></a>
- **S60. Steam Store localizado para Perú** — [Oficial](https://store.steampowered.com/?cc=pe&l=spanish) — Tipo: Oficial.
<a id="s61"></a>
- **S61. PlayStation Perú** — [Oficial](https://www.playstation.com/es-pe/) — Tipo: Oficial.
<a id="s62"></a>
- **S62. Microsoft 365 Perú** — [Oficial](https://www.microsoft.com/es-pe/microsoft-365) — Tipo: Oficial.
<a id="s63"></a>
- **S63. Adobe Perú** — [Oficial](https://www.adobe.com/pe/) — Tipo: Oficial.
<a id="s64"></a>
- **S64. Canva — app disponible en App Store Perú** — [App oficial](https://apps.apple.com/pe/app/canva-editor-de-fotos-y-video/id897446215) — Tipo: App oficial.
<a id="s65"></a>
- **S65. OpenAI — países compatibles con ChatGPT, incluye Perú** — [Oficial](https://help.openai.com/en/articles/7947663-chatgpt-supported-countries) — Tipo: Oficial.
<a id="s66"></a>
- **S66. Anthropic — países compatibles, incluye Perú** — [Oficial](https://www.anthropic.com/supported-countries) — Tipo: Oficial.
<a id="s67"></a>
- **S67. Aruma — nuestras tiendas** — [Oficial](https://www.aruma.pe/nuestras-tiendas) — Tipo: Oficial.
<a id="s68"></a>
- **S68. Promart — tiendas** — [Oficial](https://www.promart.pe/Tiendas) — Tipo: Oficial.
<a id="s69"></a>
- **S69. Ripley Perú — ubicaciones** — [Oficial](https://simple.ripley.com.pe/minisitios/especial/servicio-cliente/ubicaciones) — Tipo: Oficial.
<a id="s70"></a>
- **S70. Sodimac/Maestro — tiendas** — [Oficial](https://www.sodimac.com.pe/sodimac-pe/content/nuestras-tiendas-fake) — Tipo: Oficial.
<a id="s71"></a>
- **S71. Falabella Perú — ecosistema y presencia** — [Oficial](https://www.falabella.com.pe/falabella-pe/page/grupo-falabella) — Tipo: Oficial.
<a id="s72"></a>
- **S72. Mercado Libre Perú** — [Oficial](https://www.mercadolibre.com.pe/) — Tipo: Oficial.
<a id="s73"></a>
- **S73. Oechsle Perú** — [Oficial](https://www.oechsle.pe/) — Tipo: Oficial.
<a id="s74"></a>
- **S74. Coolbox Perú** — [Oficial](https://www.coolbox.pe/) — Tipo: Oficial.
<a id="s75"></a>
- **S75. Cineplanet Perú** — [Oficial](https://www.cineplanet.com.pe/) — Tipo: Oficial.
<a id="s76"></a>
- **S76. Cinemark Perú** — [Oficial](https://www.cinemark-peru.com/) — Tipo: Oficial.
<a id="s77"></a>
- **S77. Simple Icons — disclaimer legal** — [Proyecto oficial](https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md) — Tipo: Proyecto oficial.
<a id="s78"></a>
- **S78. Simple Icons — repositorio/uso** — [Proyecto oficial](https://github.com/simple-icons/simple-icons) — Tipo: Proyecto oficial.
<a id="s79"></a>
- **S79. Iconify — catálogo de icon sets** — [Proveedor](https://icon-sets.iconify.design/) — Tipo: Proveedor.
<a id="s80"></a>
- **S80. Brandfetch Developers — pricing** — [Proveedor](https://brandfetch.com/developers/pricing) — Tipo: Proveedor.
<a id="s81"></a>
- **S81. Logo.dev — pricing** — [Proveedor](https://www.logo.dev/pricing) — Tipo: Proveedor.
<a id="s82"></a>
- **S82. Supabase Storage — image transformations** — [Documentación oficial](https://supabase.com/docs/guides/storage/serving/image-transformations) — Tipo: Documentación oficial.
<a id="s83"></a>
- **S83. Android Studio — Vector Asset Studio** — [Documentación oficial](https://developer.android.com/studio/write/vector-asset-studio) — Tipo: Documentación oficial.
<a id="s84"></a>
- **S84. Starbucks Perú — términos y canales/locales** — [Oficial](https://www.starbucks.pe/empresa/politicas/terminos-y-condiciones-canales) — Tipo: Oficial.

---

# Anexo A — Política de seed y verificación

## Estados recomendados

```text
VERIFIED
POR_VERIFICAR
REJECTED
DEPRECATED
```

Para la UI del catálogo, solo `VERIFIED` y `POR_VERIFICAR` con evidencia de presencia pueden mostrarse como opciones del sistema; `POR_VERIFICAR` **no puede ejecutar normalización automática sensible**.

## Regla para descriptores

```text
VISIBLE_BRAND ≠ LEGAL_ENTITY ≠ DOMAIN ≠ TRANSACTION_DESCRIPTOR
```

Ninguno debe copiarse al otro campo automáticamente.

---

# Anexo B — Propuesta machine-readable para seed

Este bloque contiene Priority A + B. **`transaction_aliases` queda vacío de forma deliberada**: el catálogo de identidad puede sembrarse antes que el catálogo de descriptores.

```yaml
catalog_version: "2026.09"
market: "PE"
cutoff_date: "2026-09-29"
merchants:
  - canonical_id: "plaza_vea"
    display_name: "Plaza Vea"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Supermercados"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.plazavea.com.pe/"
    domains_associated: ["plazavea.com.pe"]
    company: "Supermercados Peruanos S.A."
    aliases:
      - value: "Plaza Vea"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S06"]
  - canonical_id: "tottus"
    display_name: "Tottus"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Supermercados"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.tottus.com.pe/"
    domains_associated: ["tottus.com.pe"]
    company: "Hipermercados Tottus S.A."
    aliases:
      - value: "Tottus"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S07"]
  - canonical_id: "metro"
    display_name: "Metro"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Supermercados"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.metro.pe/"
    domains_associated: ["metro.pe"]
    company: "Cencosud"
    aliases:
      - value: "Metro"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S12"]
  - canonical_id: "wong"
    display_name: "Wong"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Supermercados"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.wong.pe/"
    domains_associated: ["wong.pe"]
    company: "Cencosud"
    aliases:
      - value: "Wong"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S11"]
  - canonical_id: "mass"
    display_name: "Mass"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "PHYSICAL"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Tiendas de descuento y proximidad"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.tiendasmass.com.pe/"
    domains_associated: ["tiendasmass.com.pe"]
    company: "Supermercados Peruanos S.A."
    aliases:
      - value: "Mass"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S08"]
  - canonical_id: "tambo"
    display_name: "Tambo"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Tiendas de conveniencia"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.tambo.pe/"
    domains_associated: ["tambo.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Tambo"
        type: "DISPLAY"
        status: "VERIFIED"
      - value: "Tambo+"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S09", "S10"]
  - canonical_id: "kfc"
    display_name: "KFC"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.kfc.com.pe/"
    domains_associated: ["kfc.com.pe"]
    company: "Delosi"
    aliases:
      - value: "KFC"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S14"]
  - canonical_id: "bembos"
    display_name: "Bembos"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.bembos.com.pe/"
    domains_associated: ["bembos.com.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Bembos"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S13"]
  - canonical_id: "mcdonalds"
    display_name: "McDonald's"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.mcdonalds.com.pe/"
    domains_associated: ["mcdonalds.com.pe"]
    company: "Arcos Dorados"
    aliases:
      - value: "McDonald's"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S15"]
  - canonical_id: "papa_johns"
    display_name: "Papa Johns"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Pizzerías / fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.papajohns.com.pe/"
    domains_associated: ["papajohns.com.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Papa Johns"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S16"]
  - canonical_id: "starbucks"
    display_name: "Starbucks"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Cafeterías"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.starbucks.pe/"
    domains_associated: ["starbucks.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Starbucks"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S84"]
  - canonical_id: "dunkin"
    display_name: "Dunkin'"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Cafeterías y snacks"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.dunkin.pe/"
    domains_associated: ["dunkin.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Dunkin'"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S17"]
  - canonical_id: "chinawok"
    display_name: "Chinawok"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Chifas / comida china"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.chinawok.com.pe/"
    domains_associated: ["chinawok.com.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Chinawok"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S18"]
  - canonical_id: "pardos_chicken"
    display_name: "Pardos Chicken"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Pollerías"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.pardoschicken.pe/"
    domains_associated: ["pardoschicken.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Pardos Chicken"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S19", "S20"]
  - canonical_id: "norkys"
    display_name: "Norky's"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Pollerías"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://norkys.pe/"
    domains_associated: ["norkys.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Norky's"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S21"]
  - canonical_id: "rappi"
    display_name: "Rappi"
    market_country: "PE"
    origin_country: "CO"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Delivery y agregadores"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.rappi.com.pe/"
    domains_associated: ["rappi.com.pe"]
    company: "Rappi"
    aliases:
      - value: "Rappi"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S22"]
  - canonical_id: "pedidosya"
    display_name: "PedidosYa"
    market_country: "PE"
    origin_country: "UY"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Delivery y agregadores"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.pedidosya.com.pe/"
    domains_associated: ["pedidosya.com.pe"]
    company: "Delivery Hero"
    aliases:
      - value: "PedidosYa"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S23"]
  - canonical_id: "inkafarma"
    display_name: "Inkafarma"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Farmacias"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://inkafarma.pe/"
    domains_associated: ["inkafarma.pe"]
    company: "Farmacias Peruanas S.A.C."
    aliases:
      - value: "Inkafarma"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S24"]
  - canonical_id: "mifarma"
    display_name: "Mifarma"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Farmacias"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.mifarma.com.pe/"
    domains_associated: ["mifarma.com.pe"]
    company: "Farmacias Peruanas S.A.C."
    aliases:
      - value: "Mifarma"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S25"]
  - canonical_id: "smart_fit"
    display_name: "Smart Fit"
    market_country: "PE"
    origin_country: "BR"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Gimnasios"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.smartfit.com.pe/"
    domains_associated: ["smartfit.com.pe"]
    company: "Smart Fit"
    aliases:
      - value: "Smart Fit"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S30"]
  - canonical_id: "metropolitano"
    display_name: "Metropolitano"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "PHYSICAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Transporte público urbano"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.gob.pe/atu"
    domains_associated: ["gob.pe"]
    company: "ATU"
    aliases:
      - value: "Metropolitano"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S38"]
  - canonical_id: "linea_1_metro_lima"
    display_name: "Línea 1 Metro de Lima"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "PHYSICAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Metro / tren urbano"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.lineauno.pe/"
    domains_associated: ["lineauno.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Línea 1 Metro de Lima"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S39"]
  - canonical_id: "uber"
    display_name: "Uber"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Taxi y movilidad por aplicativo"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.uber.com/pe/es/"
    domains_associated: ["uber.com"]
    company: "Uber Technologies"
    aliases:
      - value: "Uber"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S31"]
  - canonical_id: "didi"
    display_name: "DiDi"
    market_country: "PE"
    origin_country: "CN"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Taxi y movilidad por aplicativo"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://web.didiglobal.com/pe/pasajero/"
    domains_associated: ["web.didiglobal.com"]
    company: "DiDi"
    aliases:
      - value: "DiDi"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S32"]
  - canonical_id: "primax"
    display_name: "Primax"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Combustible y grifos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.primax.com.pe/"
    domains_associated: ["primax.com.pe"]
    company: "Primax"
    aliases:
      - value: "Primax"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S34"]
  - canonical_id: "repsol"
    display_name: "Repsol"
    market_country: "PE"
    origin_country: "ES"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Combustible y grifos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.repsol.pe/"
    domains_associated: ["repsol.pe"]
    company: "Repsol"
    aliases:
      - value: "Repsol"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S35"]
  - canonical_id: "petroperu"
    display_name: "Petroperú / Petrored"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "PHYSICAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Combustible y grifos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.petroperu.com.pe/"
    domains_associated: ["petroperu.com.pe"]
    company: "Petroperú S.A."
    aliases:
      - value: "Petroperú / Petrored"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S36"]
  - canonical_id: "claro"
    display_name: "Claro"
    market_country: "PE"
    origin_country: "MX"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Comunicaciones"
    subcategory_suggestion: "Telefonía móvil e internet"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.claro.com.pe/"
    domains_associated: ["claro.com.pe"]
    company: "América Móvil"
    aliases:
      - value: "Claro"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S02", "S48"]
  - canonical_id: "movistar"
    display_name: "Movistar"
    market_country: "PE"
    origin_country: "ES"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Comunicaciones"
    subcategory_suggestion: "Telefonía móvil e internet"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.movistar.com.pe/"
    domains_associated: ["movistar.com.pe"]
    company: "Telefónica"
    aliases:
      - value: "Movistar"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S02", "S49"]
  - canonical_id: "entel"
    display_name: "Entel"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Comunicaciones"
    subcategory_suggestion: "Telefonía móvil e internet"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.entel.pe/"
    domains_associated: ["entel.pe"]
    company: "Entel"
    aliases:
      - value: "Entel"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S02", "S50"]
  - canonical_id: "bitel"
    display_name: "Bitel"
    market_country: "PE"
    origin_country: "VN"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Comunicaciones"
    subcategory_suggestion: "Telefonía móvil e internet"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://bitel.com.pe/"
    domains_associated: ["bitel.com.pe"]
    company: "Viettel"
    aliases:
      - value: "Bitel"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S02", "S51"]
  - canonical_id: "luz_del_sur"
    display_name: "Luz del Sur"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "HYBRID"
    category_suggestion: "Vivienda y servicios"
    subcategory_suggestion: "Electricidad"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.luzdelsur.pe/es"
    domains_associated: ["luzdelsur.pe"]
    company: "Luz del Sur"
    aliases:
      - value: "Luz del Sur"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S44"]
  - canonical_id: "pluz"
    display_name: "Pluz"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "HYBRID"
    category_suggestion: "Vivienda y servicios"
    subcategory_suggestion: "Electricidad"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.pluz.pe/"
    domains_associated: ["pluz.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Pluz"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S45"]
  - canonical_id: "sedapal"
    display_name: "Sedapal"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "HYBRID"
    category_suggestion: "Vivienda y servicios"
    subcategory_suggestion: "Agua y saneamiento"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.gob.pe/sedapal"
    domains_associated: ["gob.pe"]
    company: "SEDAPAL"
    aliases:
      - value: "Sedapal"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S46"]
  - canonical_id: "calidda"
    display_name: "Cálidda"
    market_country: "PE"
    origin_country: "CO"
    scope: "REGIONAL"
    type: "HYBRID"
    category_suggestion: "Vivienda y servicios"
    subcategory_suggestion: "Gas natural"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.calidda.com.pe/"
    domains_associated: ["calidda.com.pe"]
    company: "Cálidda"
    aliases:
      - value: "Cálidda"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S47"]
  - canonical_id: "yape"
    display_name: "Yape"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Billeteras digitales"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.yape.com.pe/"
    domains_associated: ["yape.com.pe"]
    company: "Banco de Crédito del Perú"
    aliases:
      - value: "Yape"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S04"]
  - canonical_id: "plin"
    display_name: "Plin"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Billeteras y transferencias"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://plin.pe/"
    domains_associated: ["plin.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Plin"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S05"]
  - canonical_id: "bcp"
    display_name: "BCP"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.viabcp.com/"
    domains_associated: ["viabcp.com"]
    company: "Banco de Crédito del Perú"
    aliases:
      - value: "BCP"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03"]
  - canonical_id: "interbank"
    display_name: "Interbank"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://interbank.pe/"
    domains_associated: ["interbank.pe"]
    company: "Interbank"
    aliases:
      - value: "Interbank"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03"]
  - canonical_id: "bbva_peru"
    display_name: "BBVA Perú"
    market_country: "PE"
    origin_country: "ES"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.bbva.pe/"
    domains_associated: ["bbva.pe"]
    company: "BBVA"
    aliases:
      - value: "BBVA Perú"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03"]
  - canonical_id: "scotiabank_peru"
    display_name: "Scotiabank Perú"
    market_country: "PE"
    origin_country: "CA"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.scotiabank.com.pe/"
    domains_associated: ["scotiabank.com.pe"]
    company: "Scotiabank"
    aliases:
      - value: "Scotiabank Perú"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03"]
  - canonical_id: "netflix"
    display_name: "Netflix"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Streaming de video"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.netflix.com/pe/"
    domains_associated: ["netflix.com"]
    company: "Netflix"
    aliases:
      - value: "Netflix"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S54"]
  - canonical_id: "spotify"
    display_name: "Spotify"
    market_country: "PE"
    origin_country: "SE"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Streaming de música"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.spotify.com/pe/"
    domains_associated: ["spotify.com"]
    company: "Spotify"
    aliases:
      - value: "Spotify"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S55"]
  - canonical_id: "disney_plus"
    display_name: "Disney+"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Streaming de video"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.disneyplus.com/es-pe"
    domains_associated: ["disneyplus.com"]
    company: "The Walt Disney Company"
    aliases:
      - value: "Disney+"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S56"]
  - canonical_id: "google_one"
    display_name: "Google One"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Almacenamiento en la nube"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://one.google.com/"
    domains_associated: ["one.google.com"]
    company: "Google"
    aliases:
      - value: "Google One"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S58"]
  - canonical_id: "icloud_plus"
    display_name: "iCloud+"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Almacenamiento en la nube"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.icloud.com/"
    domains_associated: ["icloud.com"]
    company: "Apple"
    aliases:
      - value: "iCloud+"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S59"]
  - canonical_id: "falabella"
    display_name: "Falabella"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Tiendas por departamento / marketplace"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.falabella.com.pe/"
    domains_associated: ["falabella.com.pe"]
    company: "Falabella"
    aliases:
      - value: "Falabella"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S71"]
  - canonical_id: "ripley"
    display_name: "Ripley"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Tiendas por departamento"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://simple.ripley.com.pe/"
    domains_associated: ["simple.ripley.com.pe"]
    company: "Ripley Corp"
    aliases:
      - value: "Ripley"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S69"]
  - canonical_id: "sodimac"
    display_name: "Sodimac"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Mejoramiento del hogar y ferretería"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.sodimac.com.pe/"
    domains_associated: ["sodimac.com.pe"]
    company: "Falabella"
    aliases:
      - value: "Sodimac"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S70"]
  - canonical_id: "promart"
    display_name: "Promart"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Mejoramiento del hogar y ferretería"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.promart.pe/"
    domains_associated: ["promart.pe"]
    company: "Intercorp Retail"
    aliases:
      - value: "Promart"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S68"]
  - canonical_id: "mercado_libre"
    display_name: "Mercado Libre"
    market_country: "PE"
    origin_country: "AR"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Marketplace / e-commerce"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.mercadolibre.com.pe/"
    domains_associated: ["mercadolibre.com.pe"]
    company: "Mercado Libre"
    aliases:
      - value: "Mercado Libre"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S72"]
  - canonical_id: "cineplanet"
    display_name: "Cineplanet"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Entretenimiento y ocio"
    subcategory_suggestion: "Cine"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.cineplanet.com.pe/"
    domains_associated: ["cineplanet.com.pe"]
    company: "Cineplex S.A."
    aliases:
      - value: "Cineplanet"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S75"]
  - canonical_id: "latam_airlines"
    display_name: "LATAM Airlines"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Viajes y alojamiento"
    subcategory_suggestion: "Aerolíneas"
    category_binding: "SUGGESTION_ONLY"
    priority: "A"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.latamairlines.com/pe/es"
    domains_associated: ["latamairlines.com"]
    company: "LATAM Airlines Group"
    aliases:
      - value: "LATAM Airlines"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S40"]
  - canonical_id: "listo"
    display_name: "Listo!"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "PHYSICAL"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Tiendas de conveniencia"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://creaturuta.primax.com.pe/estaciones-de-servicio"
    domains_associated: ["creaturuta.primax.com.pe"]
    company: "Primax"
    aliases:
      - value: "Listo!"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S34"]
  - canonical_id: "burger_king"
    display_name: "Burger King"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_SECONDARY_PRIMARY_PLATFORM"
    domains_associated: []
    company: "UNKNOWN"
    aliases:
      - value: "Burger King"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S23"]
  - canonical_id: "popeyes"
    display_name: "Popeyes"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_SECONDARY_PRIMARY_PLATFORM"
    domains_associated: []
    company: "UNKNOWN"
    aliases:
      - value: "Popeyes"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S23"]
  - canonical_id: "pizza_hut"
    display_name: "Pizza Hut"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Alimentación"
    subcategory_suggestion: "Pizzerías / fast food"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_SECONDARY_PRIMARY_PLATFORM"
    domains_associated: []
    company: "UNKNOWN"
    aliases:
      - value: "Pizza Hut"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S23"]
  - canonical_id: "auna"
    display_name: "Auna"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Clínicas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://auna.org/pe/"
    domains_associated: ["auna.org"]
    company: "Auna"
    aliases:
      - value: "Auna"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S26"]
  - canonical_id: "sanna"
    display_name: "SANNA"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Clínicas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.sanna.pe/"
    domains_associated: ["sanna.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "SANNA"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S27"]
  - canonical_id: "roe"
    display_name: "ROE"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Laboratorios"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.labroe.com/"
    domains_associated: ["labroe.com"]
    company: "Laboratorios ROE"
    aliases:
      - value: "ROE"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S28"]
  - canonical_id: "suiza_lab"
    display_name: "Suiza Lab"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Salud y bienestar"
    subcategory_suggestion: "Laboratorios"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.suizalab.com/"
    domains_associated: ["suizalab.com"]
    company: "UNKNOWN"
    aliases:
      - value: "Suiza Lab"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S29"]
  - canonical_id: "cabify"
    display_name: "Cabify"
    market_country: "PE"
    origin_country: "ES"
    scope: "REGIONAL"
    type: "DIGITAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Taxi y movilidad por aplicativo"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://cabify.com/pe/"
    domains_associated: ["cabify.com"]
    company: "Cabify"
    aliases:
      - value: "Cabify"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S33"]
  - canonical_id: "lima_expresa"
    display_name: "Lima Expresa"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "PHYSICAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Peajes"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.limaexpresa.pe/"
    domains_associated: ["limaexpresa.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Lima Expresa"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S37"]
  - canonical_id: "linea_2_metro_lima"
    display_name: "Línea 2 Metro de Lima"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "PHYSICAL"
    category_suggestion: "Transporte y movilidad"
    subcategory_suggestion: "Metro / tren urbano"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.gob.pe/atu"
    domains_associated: ["gob.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Línea 2 Metro de Lima"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S38"]
  - canonical_id: "sky_airline"
    display_name: "SKY Airline"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Viajes y alojamiento"
    subcategory_suggestion: "Aerolíneas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.skyairline.com/"
    domains_associated: ["skyairline.com"]
    company: "SKY Airline"
    aliases:
      - value: "SKY Airline"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S41"]
  - canonical_id: "jetsmart"
    display_name: "JetSMART"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "DIGITAL"
    category_suggestion: "Viajes y alojamiento"
    subcategory_suggestion: "Aerolíneas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://jetsmart.com/pe/es"
    domains_associated: ["jetsmart.com"]
    company: "JetSMART"
    aliases:
      - value: "JetSMART"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S42"]
  - canonical_id: "cruz_del_sur"
    display_name: "Cruz del Sur"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Viajes y alojamiento"
    subcategory_suggestion: "Buses interprovinciales"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.cruzdelsur.com.pe/"
    domains_associated: ["cruzdelsur.com.pe"]
    company: "Cruz del Sur"
    aliases:
      - value: "Cruz del Sur"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S43"]
  - canonical_id: "win"
    display_name: "Win"
    market_country: "PE"
    origin_country: "PE"
    scope: "REGIONAL"
    type: "HYBRID"
    category_suggestion: "Comunicaciones"
    subcategory_suggestion: "Internet fijo"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://win.pe/"
    domains_associated: ["win.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "Win"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S52"]
  - canonical_id: "wow"
    display_name: "WOW"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Comunicaciones"
    subcategory_suggestion: "Internet fijo"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://wowperu.pe/"
    domains_associated: ["wowperu.pe"]
    company: "UNKNOWN"
    aliases:
      - value: "WOW"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S53"]
  - canonical_id: "max"
    display_name: "Max"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Streaming de video"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.max.com/"
    domains_associated: ["max.com"]
    company: "Warner Bros. Discovery"
    aliases:
      - value: "Max"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S57"]
  - canonical_id: "youtube_premium"
    display_name: "YouTube Premium"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Streaming de video y música"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_PE_PLAN_CAPTURE"
    website: "https://www.youtube.com/premium"
    domains_associated: ["youtube.com"]
    company: "Google"
    aliases:
      - value: "YouTube Premium"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "chatgpt"
    display_name: "ChatGPT"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Inteligencia artificial"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://chatgpt.com/"
    domains_associated: ["chatgpt.com"]
    company: "OpenAI"
    aliases:
      - value: "ChatGPT"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S65"]
  - canonical_id: "claude"
    display_name: "Claude"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Inteligencia artificial"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://claude.ai/"
    domains_associated: ["claude.ai"]
    company: "Anthropic"
    aliases:
      - value: "Claude"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S66"]
  - canonical_id: "google_ai_pro"
    display_name: "Google AI Pro"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Inteligencia artificial"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://one.google.com/"
    domains_associated: ["one.google.com"]
    company: "Google"
    aliases:
      - value: "Google AI Pro"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S58"]
  - canonical_id: "perplexity"
    display_name: "Perplexity"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Inteligencia artificial"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_PE_AVAILABILITY"
    website: "https://www.perplexity.ai/"
    domains_associated: ["perplexity.ai"]
    company: "Perplexity AI"
    aliases:
      - value: "Perplexity"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "canva"
    display_name: "Canva"
    market_country: "PE"
    origin_country: "AU"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Productividad y diseño"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.canva.com/"
    domains_associated: ["canva.com"]
    company: "Canva"
    aliases:
      - value: "Canva"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S64"]
  - canonical_id: "microsoft_365"
    display_name: "Microsoft 365"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Productividad y software"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.microsoft.com/es-pe/microsoft-365"
    domains_associated: ["microsoft.com"]
    company: "Microsoft"
    aliases:
      - value: "Microsoft 365"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S62"]
  - canonical_id: "adobe"
    display_name: "Adobe"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Productividad y software"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.adobe.com/pe/"
    domains_associated: ["adobe.com"]
    company: "Adobe"
    aliases:
      - value: "Adobe"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S63"]
  - canonical_id: "steam"
    display_name: "Steam"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Videojuegos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://store.steampowered.com/"
    domains_associated: ["store.steampowered.com"]
    company: "Valve"
    aliases:
      - value: "Steam"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S60"]
  - canonical_id: "playstation"
    display_name: "PlayStation"
    market_country: "PE"
    origin_country: "JP"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Videojuegos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.playstation.com/es-pe/"
    domains_associated: ["playstation.com"]
    company: "Sony Interactive Entertainment"
    aliases:
      - value: "PlayStation"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S61"]
  - canonical_id: "xbox"
    display_name: "Xbox"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Suscripciones digitales"
    subcategory_suggestion: "Videojuegos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_PE_CATALOG"
    website: "https://www.xbox.com/"
    domains_associated: ["xbox.com"]
    company: "Microsoft"
    aliases:
      - value: "Xbox"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "aruma"
    display_name: "Aruma"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Belleza y cuidado personal"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.aruma.pe/"
    domains_associated: ["aruma.pe"]
    company: "Aruma"
    aliases:
      - value: "Aruma"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S67"]
  - canonical_id: "oechsle"
    display_name: "Oechsle"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Tiendas por departamento"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.oechsle.pe/"
    domains_associated: ["oechsle.pe"]
    company: "Intercorp Retail"
    aliases:
      - value: "Oechsle"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S73"]
  - canonical_id: "coolbox"
    display_name: "Coolbox"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Tecnología y accesorios"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.coolbox.pe/"
    domains_associated: ["coolbox.pe"]
    company: "Rash Peru"
    aliases:
      - value: "Coolbox"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S74"]
  - canonical_id: "maestro"
    display_name: "Maestro"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Compras y retail"
    subcategory_suggestion: "Mejoramiento del hogar y ferretería"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.sodimac.com.pe/"
    domains_associated: ["sodimac.com.pe"]
    company: "Falabella"
    aliases:
      - value: "Maestro"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S70"]
  - canonical_id: "cinemark"
    display_name: "Cinemark"
    market_country: "PE"
    origin_country: "US"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Entretenimiento y ocio"
    subcategory_suggestion: "Cine"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.cinemark-peru.com/"
    domains_associated: ["cinemark-peru.com"]
    company: "Cinemark del Perú S.R.L."
    aliases:
      - value: "Cinemark"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S76"]
  - canonical_id: "banco_falabella"
    display_name: "Banco Falabella"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.bancofalabella.pe/"
    domains_associated: ["bancofalabella.pe"]
    company: "Falabella"
    aliases:
      - value: "Banco Falabella"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03", "S71"]
  - canonical_id: "banco_ripley"
    display_name: "Banco Ripley"
    market_country: "PE"
    origin_country: "CL"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.bancoripley.com.pe/"
    domains_associated: ["bancoripley.com.pe"]
    company: "Ripley Corp"
    aliases:
      - value: "Banco Ripley"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03"]
  - canonical_id: "mibanco"
    display_name: "Mibanco"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Bancos / microfinanzas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "VERIFIED_PRIMARY"
    website: "https://www.mibanco.com.pe/"
    domains_associated: ["mibanco.com.pe"]
    company: "Credicorp"
    aliases:
      - value: "Mibanco"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: ["S03"]
  - canonical_id: "rimac"
    display_name: "RIMAC"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Seguros"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.rimac.com/"
    domains_associated: ["rimac.com"]
    company: "RIMAC Seguros"
    aliases:
      - value: "RIMAC"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "pacifico"
    display_name: "Pacífico Seguros"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Seguros"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.pacifico.com.pe/"
    domains_associated: ["pacifico.com.pe"]
    company: "Pacífico Seguros"
    aliases:
      - value: "Pacífico Seguros"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "mapfre"
    display_name: "MAPFRE"
    market_country: "PE"
    origin_country: "ES"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Seguros"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.mapfre.com.pe/"
    domains_associated: ["mapfre.com.pe"]
    company: "MAPFRE"
    aliases:
      - value: "MAPFRE"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "la_positiva"
    display_name: "La Positiva"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Finanzas y seguros"
    subcategory_suggestion: "Seguros"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.lapositiva.com.pe/"
    domains_associated: ["lapositiva.com.pe"]
    company: "La Positiva"
    aliases:
      - value: "La Positiva"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "upc"
    display_name: "UPC"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Universidades"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.upc.edu.pe/"
    domains_associated: ["upc.edu.pe"]
    company: "Universidad Peruana de Ciencias Aplicadas"
    aliases:
      - value: "UPC"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "pucp"
    display_name: "PUCP"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Universidades"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.pucp.edu.pe/"
    domains_associated: ["pucp.edu.pe"]
    company: "Pontificia Universidad Católica del Perú"
    aliases:
      - value: "PUCP"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "utp"
    display_name: "UTP"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Universidades e institutos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.utp.edu.pe/"
    domains_associated: ["utp.edu.pe"]
    company: "Universidad Tecnológica del Perú"
    aliases:
      - value: "UTP"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "upn"
    display_name: "UPN"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Universidades"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.upn.edu.pe/"
    domains_associated: ["upn.edu.pe"]
    company: "Universidad Privada del Norte"
    aliases:
      - value: "UPN"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "cibertec"
    display_name: "Cibertec"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Institutos"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.cibertec.edu.pe/"
    domains_associated: ["cibertec.edu.pe"]
    company: "Cibertec"
    aliases:
      - value: "Cibertec"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "britanico"
    display_name: "BRITÁNICO"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Idiomas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://britanico.edu.pe/"
    domains_associated: ["britanico.edu.pe"]
    company: "Asociación Cultural Peruano Británica"
    aliases:
      - value: "BRITÁNICO"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "icpna"
    display_name: "ICPNA"
    market_country: "PE"
    origin_country: "PE"
    scope: "PERU"
    type: "HYBRID"
    category_suggestion: "Educación"
    subcategory_suggestion: "Idiomas"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_CAPTURE"
    website: "https://www.icpna.edu.pe/"
    domains_associated: ["icpna.edu.pe"]
    company: "ICPNA"
    aliases:
      - value: "ICPNA"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "coursera"
    display_name: "Coursera"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Educación"
    subcategory_suggestion: "Cursos online"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_PE_PAYMENT_CAPTURE"
    website: "https://www.coursera.org/"
    domains_associated: ["coursera.org"]
    company: "Coursera"
    aliases:
      - value: "Coursera"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
  - canonical_id: "udemy"
    display_name: "Udemy"
    market_country: "PE"
    origin_country: "US"
    scope: "GLOBAL"
    type: "DIGITAL"
    category_suggestion: "Educación"
    subcategory_suggestion: "Cursos online"
    category_binding: "SUGGESTION_ONLY"
    priority: "B"
    verification_status: "SOURCE_PENDING_PE_PAYMENT_CAPTURE"
    website: "https://www.udemy.com/"
    domains_associated: ["udemy.com"]
    company: "Udemy"
    aliases:
      - value: "Udemy"
        type: "DISPLAY"
        status: "VERIFIED"
    apps: []
    transaction_aliases: []
    transaction_descriptor_status: "POR_VERIFICAR"
    logo:
      source: "NOT_CURATED"
      status: "POR_VERIFICAR"
    source_ids: []
```

---

# Anexo C — Checklist antes de aprobar un merchant nuevo

- [ ] Presencia real en Perú o disponibilidad digital confirmada.
- [ ] Fuente primaria/regulatoria archivada.
- [ ] `canonical_id` estable y no dependiente del nombre comercial actual.
- [ ] `display_name` vigente.
- [ ] Scope definido.
- [ ] Categoría/subcategoría solo como sugerencia.
- [ ] Aliases separados por tipo.
- [ ] Ningún descriptor transaccional inventado.
- [ ] Razón social separada de marca.
- [ ] Reglas de sucursal no crean merchants duplicados.
- [ ] Logo con procedencia/licencia/revisión.
- [ ] Fallback funcional sin red.
- [ ] Prueba de búsqueda y ambigüedad.
- [ ] Prueba Room offline.
- [ ] Prueba de sync idempotente.
- [ ] Si es global, market presence separada por país.

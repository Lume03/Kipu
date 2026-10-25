# Referencias visuales de Planificación

Mockups y flujos visuales para las secciones **Presupuestos**, **Metas** y **Deudas** de Planificación. Son referencias de diseño, no implementación ni fuente de requisitos. Cualquier cambio funcional debe seguir la especificación de su épica y el flujo Spec Kit.

## Organización

Cada subcarpeta agrupa las capturas de una sección. Las imágenes numeradas muestran pantallas, estados y pasos del flujo; los archivos HTML conservan fuentes interactivas o artboards cuando están disponibles.

### Deudas

Capturas `D0`–`D17` en `Deudas/`, con dos variantes PNG de nombres legados para D2–D5 que se conservan porque difieren de las capturas tituladas. Los archivos `*.dc.html` son fuentes de algunos artboards y no tienen necesariamente una captura con el mismo nombre. También hay un flujo HTML consolidado.

| Grupo | Contenido |
|---|---|
| D0–D6 | Entrada, estados vacíos/lista, alta, detalle, pago/cobro y límite del plan |
| D7–D17 | Historial, edición, cierre, alertas, menú, estados de deuda y acciones de pago |

Las capturas están exportadas a 780 px de ancho. Los `.dc.html` usan el runtime del lienzo de diseño; sus enlaces internos conservan nombres originales y no necesariamente se abren correctamente con doble clic.

### Metas

Capturas `M1`–`M9` en `Metas/`: estado vacío, lista, creación, detalle, aportes/retiros, acciones, eliminación, meta lograda y límite del plan. `Kipu · Flujo de presupuestos - Metas.html` contiene el flujo consolidado.

### Presupuestos

Capturas `1`–`8` en `Presupuesto/` documentan el recorrido principal: lista, creación, selección de categoría, confirmación, detalle, acciones, edición y eliminación. Las capturas `P1`–`P6` documentan estados complementarios: vacío, límite, cierre de periodo, notificaciones y detalles excedido/en control. La guía de umbrales y colores acompaña estos estados. El HTML de la carpeta conserva el flujo consolidado.

## Navegación representada

- Barra inferior: Dinero | Movimientos | Planificación | Análisis | (+).
- Pestañas de Planificación: Metas | Presupuestos | Deudas.
- Filtros de Deudas: Todas | Debo | Me deben.
- Dinero y Movimientos existen en la aplicación y no tienen mockup aquí. Análisis es provisional y no tiene diseño definido.

## Valores de diseño de Deudas

| Uso | Valor |
|---|---|
| Primario (teal) | `#0F766E` |
| Fondo / tarjeta | `#F8FAFC` / `#FFFFFF` |
| Texto principal / secundario | `#0F172A` / `#475569` |
| Debo (gasto) | texto `#B42F2F` sobre `#FDECEC` |
| Me deben (ingreso) | texto `#166534` sobre `#DCFCE7`, barra `#16A34A` |
| Atención / vencimiento próximo | texto `#8A4B00` sobre `#FEF3C7` (ámbar `#F59E0B`) |
| Avance de pago (debo) | barra `#14A38F` sobre `#E8EEF2` |
| Radios | tarjetas 18 px, botones 14–16 px, chips y pestañas 20–22 px |
| Alturas táctiles | mínimo 44 px; botón principal 52 px; (+) 56 px |
| Fuente | Inter (aproximada a la tipografía de la app) |

## Reglas y ejemplos representados en Deudas

- Plan Básico: máximo 2 deudas o préstamos activos en total, compartidos entre Debo y Me deben; PRO sin límite. D6 representa el límite alcanzado.
- Sin registros se muestra D1; con registros, D2.
- D0 representa «Me prestaron» y «Presté dinero». D3 propone una mejora del formulario real (origen del dinero, cuenta y vencimiento), con cuotas opcionales; debe validarse contra la spec.
- Ejemplos con fecha de referencia 24 Oct 2026: préstamo BCP de S/ 4,000 en 10 cuotas de S/ 400, cuatro pagadas y saldo S/ 2,400; Carlos R., S/ 300 prestados, S/ 150 cobrados y saldo vencido de S/ 150.

## Duplicados y variantes

Se eliminaron solo copias PNG idénticas confirmadas por SHA-256: las versiones de nombres legados D1 y D6 en `Deudas/`, y dos capturas antiguas de la raíz que duplicaban las pantallas 6 (acciones) y 8 (eliminar) de `Presupuesto/`. Se conservaron las demás capturas, incluidas variantes visuales con nombres parecidos.

# Dashboard empty state — antes/después

Capturas del contenido del estado vacío del Dashboard generadas con `DashboardEmptyStateTest` en el emulador Pixel_8, usando un fixture determinista sin cuentas ni tarjetas activas y cupo Free 0/4. Se conserva el mismo viewport, tema y escala de texto para la comparación.

## Antes

| Claro | Oscuro |
| --- | --- |
| ![Dashboard vacío antes, tema claro](dashboard-empty-before-light.png) | ![Dashboard vacío antes, tema oscuro](dashboard-empty-before-dark.png) |

## Después

El resumen conserva un único importe en cero y lo llama **«Saldo registrado»**, sin sugerir que ya exista dinero confirmado. Una tarjeta concentra el primer paso para agregar una cuenta; el alta de tarjeta de crédito sigue disponible como acción secundaria.

| Claro | Oscuro |
| --- | --- |
| ![Dashboard vacío después, tema claro](dashboard-empty-after-light.png) | ![Dashboard vacío después, tema oscuro](dashboard-empty-after-dark.png) |

## Acceso a planes y cupo

El estado vacío no muestra el bloque de cupo/planes. La CTA «Agregar primera cuenta» abre la introducción de instrumentos («¿Qué quieres agregar?»), que ya presenta `CalmEmeraldQuotaNotice` con el límite actual y «Ver planes». El Dashboard con instrumentos conserva su propio acceso a planes y gestión de cupos.

## Revisión del PR #49

- El estado inicial espera a que carguen tanto Dashboard como instrumentos; datos existentes, instrumentos archivados o errores no se interpretan como primera visita.
- La fusión de main/PR #43 se integra en esta rama. Se conserva la cobertura de deuda/pago activo, deuda de tarjeta archivada, reactivación y edición admitida de condiciones.
- `PayCardDialog` en main no recibe ni presenta una sugerencia de próxima cuota. La prueba se adapta a la conducta vigente: importe total precargado, acción «Total» y confirmación del importe íntegro.
- La consulta de tarjeta ahora emite `FinancialMovementEntity` (`financial_movements`), que no contiene `merchantId` ni `merchantName`; por eso la antigua aserción de nombre de comercio no corresponde a este modelo. Se refuerza la prueba de movimientos posteados de tarjeta, orden y exclusión de pendientes; la resolución del nombre de comercio se mantiene cubierta en `MovementHistoryQueryTest`.
- El detalle de tarjeta no ofrece edición de apariencia en la API actual de `AccountDetailContent`; se prueba la acción admitida para editar línea, ciclo y fechas, en lugar de mantener una prueba de una callback inexistente.

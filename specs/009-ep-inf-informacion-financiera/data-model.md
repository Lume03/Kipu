# Modelo conceptual de datos — EP-INF

Este documento define conceptos para consultas/presentación, no esquema de persistencia ni migración. La forma física debe reutilizar entidades/repositorios existentes; confirmar con sus propietarios antes de implementar. Modelo global V4.2 y refinamientos por épica son referencias versionadas, no estado remoto desplegado verificado (DOC-02 de `docs/control-documental.md`).

## Conceptos

| Concepto | Atributos de consulta relevantes | Relaciones / reglas |
|---|---|---|
| Patrimonio resumido | moneda, total, componentes, frescura/estado | Se deriva: activos reales + cuentas por cobrar − pasivos pendientes. No suma líneas de crédito ni reservas aparte. |
| Activo real/cuenta | identidad, alias, moneda, saldo y condición | Solo dinero/recursos propios existentes; tarjeta débito vinculada no duplica cuenta. |
| Cuenta por cobrar | objeto origen, pendiente vigente, moneda | Se incluye una vez como receivable; cobro de principal reduce receivable, no es ingreso operativo. |
| Pasivo/deuda | objeto origen, pendiente, moneda | Se resta una vez; principal pagado reduce deuda sin gasto operativo. |
| Reserva de meta | objetivo, importe reservado, activo de respaldo | Etiqueta/asignación interna del activo; no crear activo adicional ni doble respaldo. |
| Movimiento | id, propietario, fecha, tipo, monto/moneda, estado, categoría, referencias | Gasto/ingreso/transferencia; transferencia no altera métricas de ingreso/gasto. Anulado excluido. Reembolso vinculado a origen y reglas temporales existentes. |
| Evento calendario | id de presentación, tipo/estado, fecha real o prevista, origen y referencia navegable | Referencia a movimiento, tarjeta, obligación/deuda o recurrencia según fuentes existentes. Varios eventos pueden compartir fecha. No es una transacción nueva. |
| Serie/resultado estadístico | métrica aprobada, periodo, valor, moneda, etiqueta de parcialidad/base | Solo HU-41; definiciones y conversión pendiente de gate. Transferencias y principal excluidos de operaciones. |
| Periodo | inicio/fin, zona/calendario si definido, completo/parcial | Comparaciones identifican fechas exactas; periodos abiertos marcados parciales. |
| Privacidad/acceso | preferencia de ocultamiento, capacidad efectiva | Máscara afecta presentación; capacidades consultadas según entitlement de EP-PLA, nunca UI como autoridad. |

## Estados requeridos en consultas

Diferenciar explícitamente: Loading; contenido con valor confirmado; cero confirmado; vacío (sin registros); no disponible/desconocido por falta de fuente; error recuperable con acción de reintento. Los contratos existentes pueden modelarlo de otra forma siempre que no se colapse «desconocido» a cero ni se invente resultado.

## Invariantes de relación

- Cada lectura está limitada al propietario/sesión autorizada.
- Referencias del calendario conservan identidad del objeto fuente y ruta contextual; colisión de fechas no colapsa registros.
- Transferencias, pago de tarjeta, principal de deuda y reservas no duplican gasto/ingreso/activo.
- Total patrimonio usa una sola representación económica por valor y moneda; no se propone conversión entre monedas sin regla aprobada.
- Reembolsos y anulaciones respetan estado y reglas del ledger, no se vuelven movimientos autónomos duplicados.

## Trazabilidad con fuentes actuales

`feature/accounts/domain/model/DashboardModels.kt` declara `FinancialDashboardData`, `AccountWithBalance` y `CreditCardWithSummary`; el modelo se limita a totales PEN/USD, cuentas y tarjetas. Antes de ampliar, inspeccionar usos y casos de uso reales; no concluir que sea agregador patrimonial. Fuentes de deuda, recurrencias, metas y movimientos deben ser verificadas en sus módulos y confirmadas con android-core. No hay tabla, API, índice ni migración autorizada por este documento.

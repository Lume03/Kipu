# Investigación — EP-INF

**Fecha de revisión local**: 2026-10-24. Esta nota registra decisiones provisionales y preguntas abiertas; no aprueba contratos ni implementaciones.

## Base documental y autoridad

- Backlog HU-39/40/41, matriz de trazabilidad y cronograma son autoridad funcional para HUs, dependencias, prioridad, estimación y sprint de cierre. HU-39 (Alta, 8) y HU-40 (Alta, 5) cierran planificadamente en S7; HU-41 (Alta, 8) cierra en S9. El Sprint 7 declara 42 puntos agregados; S9 declara 29 en el roadmap, aunque otra sección calcula 29 y versiones anteriores documentales pueden diferir. No usar total sprint para reestimar épica.
- Reglas inmutables derivan de `.specify/memory/constitution.md`: patrimonio = activos reales + cuentas por cobrar − pasivos; transferencias no crean ingreso/gasto; reservas son asignación, no activo adicional; crédito disponible no es activo; compra de crédito se reconoce una vez; principal no afecta ingreso/gasto operativo; anulaciones/reembolsos respetan dominio.
- `docs/control-documental.md` DOC-03 confirma conflicto de autoridad visual: DESIGN.md declara Andean Modernist y el índice mockups Calm Emerald. No es seguro elegir una. Hay nueve imágenes no disponibles en checkout.

## Arquitectura/código observado

- `docs/arquitectura/vision-general.md`: Android nativo local-first; presentación observa local, dominio independiente; Room, sincronización y backend con responsabilidades separadas.
- `app/src/main/java/com/kipu/app/feature/accounts/domain/model/DashboardModels.kt`: modelo `FinancialDashboardData` contiene totales PEN/USD, cuentas líquidas, tarjetas y utilización/cupo; no es evidencia de que ya calcule patrimonio consolidado.
- Árbol de código local incluye `feature/accounts/presentation/dashboard/DashboardScreen.kt`, `feature/movements/presentation/MovementHistoryViewModel.kt`, varios ViewModels de debts/recurrences y `navigation/KipuNavHost.kt`; `app/src/test/java/com/kipu/app/feature/accounts/domain/ObserveFinancialDashboardTest.kt` existe. No se ha comprobado todavía la totalidad de las consultas que cada módulo expone ni el destino exacto de cada ruta.
- No se encontró evidencia revisada de un contrato unificado de calendario o estadísticas; ausencia de inspección no equivale a inexistencia. Investigar definiciones y usos de símbolos en siguiente fase, sin crear entidad/API anticipadamente.

## Decisiones

1. Tratar estados realizados (hecho financiero registrado) y previstos (fecha programada/esperada) como categorías distinguibles; nunca convertir una previsión en movimiento ni ejecutar pago automático.
2. Representar eventos coincidentes independientemente, preservar sus referencias de origen y navegar al detalle propio correspondiente. Política de elemento eliminado/deshabilitado pendiente de contrato.
3. Dejar HU-39/40 en incremento S7; HU-41 como incremento futuro S9. Mantener sus identificadores, puntos y dependencias de origen.
4. No inventar montos, datasets reales, objetivos de KPI, métricas ni endpoints. Los casos con cifras son fixtures de aceptación del backlog.
5. Free mantiene Inicio/historia completa/calendario pasado; HU-41 Free solo estadísticas básicas actuales. Premium ampliado depende de entitlement efectivo y autorización EP-PLA.
6. No presentar ausencia de fuente de un módulo como un cero confirmado. El contrato definirá estados.

## Preguntas/gates

- GATE-DESIGN-01: qué sistema visual gobierna Inicio/calendario/estadísticas y estado de tarjetas faltante.
- GATE-PRODUCT-02: jerarquía/orden de componentes, semántica de dato desconocido vs cero, rutas al objeto fuente ya archivado/borrado y representación de colisiones del calendario.
- GATE-ARCH-03: firmas UiState/eventos/casos de uso de cuentas, crédito, deuda, por cobrar, recurrencias, metas y movimientos; fuente de consulta local y política de actualización.
- GATE-ANALYTICS-04: diccionario de métricas, moneda/conversión si hay divisas, reglas de periodo/zona y filtros autorizados para HU-41.
- GATE-ACCESS-05: contrato de capacidades y estados del entitlement efectivo para Free/Premium, incluido offline.

Cualquiera que afecte un requisito de aceptación bloquea implementación hasta quedar resuelto y registrado por autoridad responsable. No se infiere respuesta desde mocks ni rutas actuales.

## Resolución de gates S7 — 2026-10-24

| Gate | Resolución | Origen |
|---|---|---|
| GATE-DESIGN-01 | Inicio y calendario usan el tema y los componentes ya implementados en la app (`KipuTheme`). La unificación DESIGN.md / Calm Emerald (DOC-03) sigue abierta y no se resuelve aquí. | Producto |
| GATE-PRODUCT-02 | Inicio conserva la estructura actual del dashboard y añade el resumen patrimonial; sin widgets de presupuesto ni predicciones. «Sin datos» se distingue de cero confirmado. Un evento cuyo objeto fuente ya no existe se muestra como no disponible, sin navegar. Eventos del mismo día se listan por separado. | Técnica, dentro de las reglas del backlog |
| GATE-ARCH-03 | Las consultas reutilizan repositorios y casos de uso existentes de cuentas, deudas, recurrencias, metas y movimientos. El calendario es una proyección de lectura; no se persisten eventos ni se crean APIs. | Técnica |
| GATE-ANALYTICS-04 / GATE-ACCESS-05 | Fuera del Sprint 7 (HU-41, S9). | — |

## Verificación de código real para S7 — 2026-10-24 (T001–T006)

Fuentes y rutas reales inspeccionadas; ningún esquema, API ni migración nuevos.

| Dato | Fuente real (reutilizada) | Notas |
|---|---|---|
| Saldos de cuentas y deuda de tarjeta | `feature/accounts/domain/usecase/ObserveInstrumentsUseCases.kt` (`ObserveFinancialDashboard`) | `totalPen/totalUsd` cuentan una vez cada cuenta (billeteras afiliadas no duplican) e incluyen archivadas; las cuentas `CREDIT_LIABILITY` no son visibles como cuentas. `creditCards[].debt` es la deuda vigente. |
| Cuentas por cobrar / por pagar | `feature/debts/domain/DebtRepository.observeDebts` (`DebtSummary.remainingPrincipalMinor`, `obligationType`, `status`) | Solo deudas `ACTIVE`. Cronograma: `observeInstallments` (`DebtScheduleItem`). |
| Línea de crédito y reservas | `CreditCardWithSummary.availableCredit`; `GoalBackingCalculator` (reservas) | No son entrada del patrimonio: línea disponible no es activo y la reserva es asignación del activo de respaldo. |
| Movimientos realizados | `feature/movements/domain/QueryMovementHistory` (consulta básica, sin criterios avanzados) | `DefaultMovementHistoryAccessPolicy` permite la consulta básica a todos (Free incluye historia completa y meses pasados). `occurredAt` en milisegundos. |
| Cierre/vencimiento de tarjeta | `CreditCard.billingDay/dueDay` + `core/finance/domain/CreditCalculations.calculateEffectiveDate` | Día efectivo en meses cortos. |
| Recurrencias | `feature/recurrences/domain/RecurrenceRepository.observeAll`, `RecurrenceScheduleCalculator.isOccurrence`, plantilla `templateTransaction` (`concept`, `amount_minor`, `currency_code`) | Solo `ACTIVE`, desde `nextDueDate` y hasta `endDate`. |

Rutas de navegación existentes usadas por el calendario (`app/src/main/java/com/kipu/app/navigation/`):

| Origen del evento | Ruta |
|---|---|
| Movimiento realizado | `navigateToMovementEditor(transactionId)` (`MovementsNavigation.kt`) |
| Cierre/vencimiento de tarjeta | `navigateToCardDetail(cardId)` (`AccountsNavigation.kt`) |
| Cuota o vencimiento de deuda | `navigateToDebtDetail(debtId)` (`DebtsNavigation.kt`) |
| Recurrencia | No existe ruta propia: la gestión vive en un diálogo del historial. Se navega a `navigateToMovementHistory()`. **Limitación a revisar por producto.** |
| Objeto fuente ausente | Sin navegación; se muestra «No disponible». |

Decisiones de interpretación a validar:
- Los pasivos pendientes incluyen deudas por pagar activas y la deuda vigente de tarjetas de crédito (se resta una sola vez; las cuentas `CREDIT_LIABILITY` no entran como activos).
- Moneda: sin conversión; se muestra un patrimonio por moneda con datos (PEN/USD). Una moneda sin cuentas ni deudas no genera fila (sin datos distinto de cero confirmado).
- T005 (entitlement): para S7 solo se confirma que la consulta básica de historial no depende de Premium; capacidades Premium/HU-41 quedan para S9.
- T006 (upstream): se confirmó la existencia en el código de cuentas/tarjetas (HU-07/18/19/22), movimientos, deudas y recurrencias (HU-09/29/33) y metas; HU-36/26/30/58 no se verificaron individualmente porque Inicio y calendario no los consumen; HU-25 (reembolsos) lo implementa otro agente y Inicio solo consume los saldos del dominio (T013: no se redefine ninguna regla de reembolso en Inicio ni en el calendario; los movimientos anulados o fallidos se excluyen del calendario).
- T003: estados `Loading/Content/Empty/Error` (patrimonio) y `Loading/Loaded/Empty/Error` (calendario); `NoData` ≠ cero confirmado.

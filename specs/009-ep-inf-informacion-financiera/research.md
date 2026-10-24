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

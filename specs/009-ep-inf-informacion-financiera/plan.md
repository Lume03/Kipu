# Plan técnico: EP-INF — Inicio e Información Financiera

**Rama**: `009-ep-inf-informacion-financiera` | **Fecha**: 2026-10-24 | **Spec**: [spec.md](spec.md)
**Fase documental**: planificación previa; no autoriza implementación.

## Resumen

Definir consultas de lectura para Inicio/patrimonio e historial (HU-39) y calendario de eventos (HU-40) del S7 (13 pts), y dejar especificado por separado el análisis estadístico futuro HU-41/S9 (8 pts). Consumir fuentes locales/domain actuales sin duplicar su verdad; aprobación visual y contratos de consulta pendientes bloquean implementación según gates del spec.

## Contexto técnico (inspección local, no certificación de completitud)

- **Plataforma**: Android nativo, Kotlin, Jetpack Compose; Gradle Kotlin DSL. Paths confirmados: `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`, `feature/accounts/domain/model/DashboardModels.kt`, `feature/movements/presentation/MovementHistoryViewModel.kt`, `feature/debts/presentation/DebtScheduleViewModel.kt`, `feature/recurrences/presentation/RecurrencePresentationViewModel.kt` y `navigation/KipuNavHost.kt`. Prueba confirmada: `app/src/test/java/com/kipu/app/feature/accounts/domain/ObserveFinancialDashboardTest.kt`.
- Inspección verificable en esta fase: existe `FinancialDashboardData` con totales PEN/USD, cuentas líquidas, tarjetas/resumen de crédito y cupo computable en `DashboardModels.kt`. La búsqueda de `ObserveFinancialDashboard` por nombre de archivo no localizó una definición; no se afirma que ese caso de uso exista. La presencia de los artefactos confirmados no acredita patrimonio neto consolidado, calendario unificado ni HU-39/40/41 completas. Deben trazarse definiciones/usos de los símbolos referidos por tests/ViewModels antes de implementar.
- **Límites**: Presentación observa UiState/ViewModel y emite acciones; ViewModel coordina casos de uso; dominio define métricas y efectos; persistencia local Room y repositorios son fuente local; sync/backend mantiene su autorización. No introducir acceso SQL/HTTP ni cálculo financiero autoritativo en Compose.
- **Dependencias**: HU-39 requiere HU-07/18/19; HU-22 es parcial. HU-40 requiere HU-09/29/33. HU-41/S9 depende de HU-39/36/09/26/30/58; HU-22 parcial, HU-25 relacionada.
- **Planes**: lectura de historial y capacidades básicas de Inicio/calendario en Free. Estadística básica del mes actual en Free; profundidades/comparaciones Premium según política efectiva de EP-PLA (HU-58), no flags UI o compra pendiente. La expiración no borra ni oculta historia básica.
- **Privacidad**: aislar por sesión/propietario según arquitectura; respetar máscara de importes; no exponer datos sensibles en logs/telemetría. Offline-first conforme acceso existente.
- **Métricas**: patrimonio = activos reales + cuentas por cobrar − pasivos pendientes. No sumar crédito disponible ni reserva como activo extra; excluir transferencias y principal de métricas operativas; reutilizar semántica de reembolsos/anulaciones del dominio.
- **Rendimiento/escala**: no se fijan presupuestos numéricos nuevos. Mantener consultas acotadas al periodo visible, paginación si la fuente existente la exige, evitar duplicar agregaciones en UI; objetivo no cuantificado permanece pendiente, no se declara medido.

## Revisión de constitución

- Financial Integrity First: cálculos de dominio, moneda/unidades correctas, sin transferencias/reservas/crédito duplicados; obligatoria antes de aprobar diseño.
- Local-first: consultas consumen estado local existente; sin conexión no bloquea lectura disponible. No ampliar garantías no definidas.
- Security & Privacy: aislamiento de datos propios, máscara y minimización; cualquier API/consulta backend debe seguir RLS/autorización existente.
- Freemium: Free no pierde historia; autorización Premium efectiva para HU-41 ampliada.
- Arquitectura: nativa Android con límites presentation/domain/data respetados.
- Specification-driven: HU IDs estables, S7 y S9 separados y claims documentales únicamente.
- Gates abiertos: GATE-DESIGN-01, GATE-PRODUCT-02, GATE-ARCH-03, GATE-ANALYTICS-04, GATE-ACCESS-05; bloquean implementación hasta decisión/contratos.

## Diseño y estructura afectada prevista (no compromete paths nuevos)

Áreas existentes inspeccionadas: `app/src/main/java/com/kipu/app/feature/accounts/`, `feature/movements/`, `feature/debts/`, `feature/recurrences/`, `feature/goals/`, `feature/plans/`, `navigation/`; tests de dominio/presentación y navigation. Las rutas definitivas de componentes y casos de uso se seleccionan tras trazar cada símbolo y contrato con android-core. No se declara nuevo módulo ni endpoint. La arquitectura remota/local detallada del modelo V4.2 se considera base documental, no esquema desplegado verificado.

## Riesgos y respuestas

| Riesgo | Respuesta/gate |
|---|---|
| Divergencia visual Andean Modernist / Calm Emerald | Resolver GATE-DESIGN-01 con autoridad producto/diseño; no escoger arbitrariamente. |
| Suma doble de reservas, transferencias, crédito o principal | Validar contra constitución y golden fixtures del dominio; consultas reutilizadas. |
| Evento sin fuente/ruta o con fuente eliminada | Contrato con propietarios de módulos; especificar estado no navegable, no inventar ruta. |
| Datos ausentes interpretados como cero | Modelar Unknown/Empty/Zero como estados diferenciados según contrato. |
| Premium implementado en UI o Free bloqueado | Consultar entitlement efectivo EP-PLA; preservar historial básico. |
| Contratos de módulos incompletos | Bloqueo explícito con android-core, sin API/Room nuevos por inferencia. |
| Estadísticas S9 contaminan alcance S7 | HU-41 se mantiene en fase y tareas distintas. |

## Verificación futura

La guía [quickstart.md](quickstart.md) describe validación de aceptación exclusivamente futura. En esta tarea documental no se ejecutan Gradle, pruebas de aplicación, migraciones ni despliegues. Requisitos, paths y DAG se verifican con checks documentales y Spec Kit read-only; ningún resultado equivale a implementación.

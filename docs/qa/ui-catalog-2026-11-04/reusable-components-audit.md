# Auditoria de componentes reutilizables

Fecha: 2026-11-04. Alcance: inspeccion de codigo por Luna y revision del coordinador; sin refactor general. Las rutas siguientes son relativas a `app/src/main/java/com/kipu/app/`.

| Prioridad | Componente existente | Destino / oportunidad | Limite |
| --- | --- | --- | --- |
| Alta, aplicada en esta tarea | `ui/component/OptionFilter.kt`: FilterOption, OptionFilterButton, OptionFilterSheet; patron utilizado por NotificationCenterScreen | `feature/categories/presentation/components/MerchantPicker.kt`: sustituir chips horizontales por selector vertical | Reutilizar la presentacion; conservar la busqueda y taxonomia propias del comercio |
| Media | `ui/component/KipuFilterChip.kt`, utilizado por MovementFiltersSheet | AccountSelectorBottomSheet tiene InstrumentFilter/FilterPill privados; evaluar reutilizar el chip | Un chip inline no sustituye automaticamente una hoja de filtro; conservar seleccion y accesibilidad |
| Media | `ui/component/MovementAvatarPolicy.kt` y MerchantAvatar | Mantener consistente selector, historial y detalle de tarjeta para logos, iniciales y estados de carga/error | BrandLogo identifica marcas financieras; no equivale a un comercio o pagador. En esta tarea solo se corrigio la inconsistencia solicitada |
| Media | `feature/categories/presentation/CategoryDesignTokens.kt`: resolveCategoryIcon; `feature/categories/domain/usecase/ObserveCategories.kt`: CategoryItem | Gestion de categorias, selectores y movimientos deben consumir la misma presentacion | Colores de gasto/ingreso y color de categoria cumplen roles distintos; evitar unificacion indiscriminada |
| Baja | `ui/component/KipuFormRows.kt` y SettingsCard/SettingsActionCard privados de ProfileSettingsScreen | Evaluar extraccion de filas de navegacion visualmente repetidas | Acciones, toggles y campos editables no son funcionalmente equivalentes; extraer solo cuando exista otro consumidor real |

La oportunidad principal ya se aprovecho sin crear otro filtro. Las demas quedan como recomendaciones: no se modificaron pantallas adicionales para cumplir este inventario.

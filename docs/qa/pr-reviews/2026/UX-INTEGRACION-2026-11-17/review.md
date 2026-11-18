# Integración UX — PRs #101, #102, #103 y #105 (2026-11-17)

Revisión final antes de integrar en `main` los PRs de UX de Planificación (#101), Movimientos (#102), Ajustes (#103) y Análisis (#105). Dinero (#100) ya se integró el 2026-11-16 y su issue #95 está cerrado.

## Cómo se verificó

- Rama local con `main` + los 4 PRs: se fusionaron sin conflictos.
- `:app:assembleDebug` OK y suite completa `:app:testDebugUnitTest`: **1769 casos, 0 fallos, 1 omitido**.
- Galaxy A16 (cuenta real, Premium) y emulador Pixel (cuenta de pruebas Free), a fuente 1.0 y 2.0, tema claro y oscuro. Sin crear ni modificar datos en la cuenta real; en la Free se crearon dos presupuestos `QA_UX_Integration_*` para llegar al límite del plan.
- Ningún cierre inesperado de la app durante los recorridos.

## Resultado por PR

| PR | Módulo | Resultado |
|---|---|---|
| #101 | Planificación | Integrado. Correcciones de la última revisión verificadas: "Presupuestos" ya no se corta a 200 %; aviso "2 de 2 presupuestos" legible |
| #102 | Movimientos | Integrado. "No disponible" ya no se parte en el selector de tarjeta a 1.0; el chip de sincronización es rojo cuando falla |
| #103 | Ajustes | Integrado con pendientes (ver abajo). Formulario de categoría a 200 % corregido |
| #105 | Análisis | Integrado. Rango personalizado (Inicio/Fin) y comparación de gastos/ingresos legibles; la frase de la comparación no muestra cifras con "ocultar importes"; planes Premium legibles en oscuro |

## Pendientes pequeños

Ninguno bloquea; se pueden resolver en un PR corto por módulo.

### #101 — Planificación (RedSteinYT, #97)
- [ ] A 200 % "Presupuestos" baja a dos líneas partiendo la palabra ("Presupu / estos"). Se lee, pero conviene que la segunda línea empiece en una palabra completa o reducir el padding del segmento con fuente grande (`navigation/PlanningNavigation.kt`, `PlanningSegmentedTabs`).
- [ ] Completar la tabla del issue #97 con una fila para: los tres avisos de límite de plan, los diálogos de confirmación, pausadas frente a finalizados, `ModalBottomSheet` y modales encadenados, sin conexión, carga/error de "Meta alcanzada" y "Presupuesto creado", TalkBack y pantalla grande.

### #102 — Movimientos (andreus710, #96)
- [ ] Contenido duplicado entre la rama normal y la de fuente grande: presets de filtros (`MovementFiltersSheet.kt`), fila del historial (`MovementHistoryScreen.kt`), candidatos de Yape (`ReceiptReviewScreen.kt`) y fecha en `ReceiptsNavigation.kt`. Definir el contenido una vez y cambiar solo el contenedor.
- [ ] Registrar en #96 lo corregido y lo que no se pudo probar (reembolso, candidatos de captura, duplicado/guardado de Yape, revisión de PDF).
- [ ] Ajustar el cuerpo del PR: `statementTone` conserva los tonos "violet" y "blue" escritos a mano.

### #103 — Ajustes (GiulianoMaPe, #99)
- [ ] **Selector de categoría a 200 %:** el botón Cerrar desaparece del encabezado (y de TalkBack) porque "51 categorías" ocupa todo el ancho. Reservar el espacio del botón (`movements/presentation/PastelCategoryPickerFlow.kt`, encabezado del selector).
- [ ] **Listado de Categorías a 200 %:** "No consume cupo" se parte en seis líneas (`categories/CategoriesScreen.kt`, insignia de cupo). Usar `FlowRow` o permitir que la insignia baje completa.
- [ ] **Notificaciones:** si la lista ya mostraba avisos y un reintento falla antes de emitir, la lista visible se vacía (`notifications/presentation/NotificationsViewModel.kt`, `latest` se recrea en cada reintento). Conservar el último snapshot por usuario y añadir el test "emitir → fallar → reintentar → fallar".
- [ ] Botones −/+ del día de corte en Ajustes con 36dp: subir a 48dp (`settings/presentation/ProfileSettingsScreen.kt`).
- [ ] "Restablecer" se parte dentro de la palabra en el color personalizado a 200 % (`PastelCategoryPickerFlow.kt`).
- [ ] Documentación: tabla de hallazgos en #99 y capturas Antes/Después en el PR (hoy el Antes/Después es texto).

### #105 — Análisis (saire22, #98)
- [ ] El archivo de planificación `PLAN-ISSUE-98-ANALISIS.md` venía en la raíz del repo; se retiró al integrar. Los planes de trabajo van fuera del repo o en `docs/qa`.
- [ ] Confirmar el texto de la comparación cuando el rango personalizado abarca dos meses: la etiqueta del periodo usa solo el mes de inicio (`AnalyticsScreen.kt`, `comparisonPeriodLabel`).
- [ ] Decisión de producto pendiente (propuesta en #98): en Free el panel de Filtros se abre y deja elegir criterios que luego no se aplican; indicar que son Premium.

### Transversal (integrador)
- [ ] Barra inferior a 200 %: "Movimientos" y "Planificación" se recortan (zona compartida, `ux/compartido`).
- [ ] #104: cierre de la app al recrear la pantalla con una petición de auth en curso.
- [ ] Revisar los issues #96–#99 y cerrarlos cuando cada módulo resuelva o justifique sus pendientes.

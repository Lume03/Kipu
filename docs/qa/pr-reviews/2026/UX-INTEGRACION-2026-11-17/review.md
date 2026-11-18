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

## Cierre del integrador (2026-11-17)

Los pendientes de arriba se resolvieron en `main` con estos commits, probados en el Galaxy A16 (cuenta Premium) a fuente 1.0, 1.3 y 2.0; suite unitaria en verde en cada paso y logcat sin cierres de la app:

| Commit | Qué resuelve |
|---|---|
| `f7d168d6` | #104: llamadas de auth y renovación de sesión aisladas en IO; la app ya no se cierra al girar o cambiar la fuente durante una carga (16 cambios seguidos en el A16 sin cierre) |
| `588d07bb` | #103: Cerrar visible en el selector de categoría, "No consume cupo" y "Restablecer" sin partir palabras, botones −/+ de 48dp, reintento de notificaciones que conserva la lista |
| `e91e4673` | #101/#105: pestañas de Planificación en una línea con autoajuste; rango en la etiqueta de comparación; filtros de Análisis Premium en Free (candado y aviso) |
| `77cca564` | #102: contenido definido una sola vez entre la versión normal y la de fuente grande (sin cambio visual) |
| `9f48537a` | Línea discreta "Sin conexión · se sincronizará al volver" en las cuatro pestañas (reemplaza el banner grande de Movimientos); barra inferior a fuente grande con solo la etiqueta seleccionada |
| `68c57977` | #94: borrado del código sin entrada (editor antiguo de clasificación, interruptor de saldos, consentimiento de comprobantes, tarjeta de calendario y tarjeta de patrimonio; el widget se conserva), callbacks sin efecto y errores del importador de PDF visibles en un snackbar |

Las tareas de documentación de los autores (tablas de #97 y #96) quedan cubiertas por este informe.

### Observaciones de la prueba final (no bloquean)
- **Comparación de Análisis:** la comparación siempre es mes contra mes anterior, aunque el resumen use un rango personalizado; la etiqueta de rango solo aplica si el periodo comparado abarca varios meses. Si se quiere comparar rangos personalizados, es un cambio funcional aparte.
- **Sin conexión:** en una pasada, tras cambiar la fuente de 200 % a 100 % con la app abierta y en modo avión, la línea no apareció; al reabrir la app y desconectar de nuevo apareció en las cuatro pestañas, y a 200 % siempre. Revisar el estado inicial de `rememberNetworkOffline` tras recrear la Activity.
- Detalles visuales menores: el título "Notificaciones" deja la "s" en otra línea a 100 % en el A16; a 200 % se abrevian el subtítulo del periodo en Movimientos y el chip "Ahorros / débito" en Importar, y el título de Análisis se parte cuando muestra un rango.
- Sin cobertura en dispositivo: editor de comprobantes Yape (no había pendientes) y el snackbar del importador de PDF (no se importaron archivos).

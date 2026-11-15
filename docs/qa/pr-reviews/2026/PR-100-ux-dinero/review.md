# Revisión PR #100 — UX de Dinero: escala de texto y TEA

- **PR:** [#100](https://github.com/Lume03/Kipu/pull/100)
- **Repositorio:** `Lume03/Kipu`
- **Autor / asignado:** LeonidasGarcia
- **Fecha de revisión:** 2026-11-15
- **Rama:** `ux/dinero` → `main`
- **Commits revisados:** `39e6171c` (UI) y `8ad27c1b` (informe y evidencia), sobre `ef2e8b5c`
- **Estado del PR al revisar:** abierto, borrador

## Dictamen breve

**Resultado:** Inconcluso

Los defectos confirmados de legibilidad a escala de fuente grande en el detalle de tarjeta y el estado de TEA sin valor personal están corregidos en el alcance revisado. Build, prueba unitaria focalizada y 23 pruebas instrumentadas del detalle pasan. No se considera cerrada la revisión UI/UX completa de Dinero: falta terminar y registrar el inventario de flujos del issue #95.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#95](https://github.com/Lume03/Kipu/issues/95) | Confirmado | Inventario verificado en dispositivo; defectos de severidad alta resueltos o justificados; capturas por cambio; cambios limitados al módulo. |

- **Issues no encontrados o relación incierta:** Ninguno.
- **Fuente de autoridad usada:** issue #95 y [sistema visual vigente](../../../../DESIGN.md). El issue excluye cambios a la barra raíz y componentes compartidos.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| #95 — Inventario de flujos completo y verificado en dispositivo | Parcial | Se recorrieron detalle de tarjeta, acciones, estado de cuotas vacío y catálogo de tasas; el resto del inventario sigue pendiente. |
| #95 — Hallazgos de alta severidad resueltos o justificados | Parcial | Se corrigieron los defectos confirmados de escala 200 % tratados por este PR; no se ha terminado de clasificar todos los hallazgos del módulo. |
| #95 — Capturas antes/después por cambio | Parcial | Comparativas en `evidence/`; están recortadas/redactadas para ocultar importes y datos de tarjeta. Se conserva evidencia de las pantallas revisadas, no de todo el inventario. |
| #95 — Sin archivos fuera del reparto del módulo | Cumple | Los cambios de app se limitan a `AccountDetailScreen.kt` y `RateCatalogScreen.kt`, ambos permitidos por el issue. No se modificó navegación raíz ni UI compartida. |

## Cambios realizados

- El detalle de tarjeta adapta acciones largas y encabezado de movimientos a escala de fuente grande; permite que el texto ocupe varias líneas sin truncarse.
- El catálogo apila la línea de crédito y el estado de TEA personal a escala grande para evitar colisiones.

## Hallazgos y mejoras

### Bloqueantes / importantes

- Ninguno identificado en los flujos recorridos. La revisión global del issue continúa incompleta.

### Menores / sugerencias

- La barra inferior amontonada a 200 % permanece fuera del alcance de Dinero; el issue #95 la asigna al integrador.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `:app:assembleDebug` y `:app:testDebugUnitTest --tests com.kipu.app.feature.accounts.presentation.instruments.RateCatalogContextTest` | Pasa | Ejecutado localmente el 2026-11-15. |
| `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest` | Pasa | Pixel 8 AVD, Android 16/API 36; 23 pruebas, 0 fallas. |
| `DashboardEmptyStateTest`, `UnifiedInstrumentFormScreenTest` y `CreditAccessibilitySemanticsTest` | Pasan | Fixtures de Compose; 7/7, 2/2 y 4/4 pruebas, respectivamente. Verifican estados vacíos, presentación del formulario y semántica accesible; no sustituyen el recorrido de producción con sesión real. |
| `DashboardRedesignStatesTest` | Parcial | 7 de 8 casos terminan sin fallas de aserción; `h4ArchivedInstrumentsExpand` falla durante el cierre de `ActivityScenario` (se queda en `PAUSED`, no llega a `DESTROYED`). No se cuenta como suite verde ni como defecto visual confirmado. |
| Revisión manual a escala de fuente 200 % | Pasa en los escenarios revisados | Detalle/acciones, encabezado de movimientos, estado vacío de cuotas y catálogo de tasas. No se realizó un recorrido completo del inventario. |
| `git diff --check` | Pasa | Sin errores de whitespace. |

## Revisión visual

- **¿Hay cambios visuales?** Sí.
- **Pantallas y escenarios revisados:** detalle y acciones de tarjeta, encabezado y lista vacía de movimientos/cuotas, catálogo de tasas; escala de fuente 200 %, tema claro.
- **Evidencia:** [detalle](evidence/card-detail-200-comparison.png), [acciones](evidence/card-actions-200-comparison.png), [catálogo](evidence/rate-catalog-200-comparison.png) — comparativas antes/después en el emulador Pixel 8, Android 16/API 36.
- **Privacidad:** se ocultaron saldos, línea de crédito y datos/números de tarjeta del fixture en las capturas versionadas. Los originales permanecen fuera del repositorio.

## Pendientes / límites de la revisión

- Completar el inventario y checklist restante de #95, incluidas las pantallas y variantes aún no recorridas.
- Continuar la verificación manual autenticada: la app dejó de estar instalada en el emulador tras ejecutar pruebas instrumentadas y la sesión dummy ya no está disponible localmente. No se intentará crear una cuenta, registrarse, ni recuperar credenciales del chat.
- Revisar si la barra inferior a 200 % requiere corrección en la rama del integrador.
- El informe no implica aprobación formal ni cierre del issue.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios publicados:** [Avance en issue #95](https://github.com/Lume03/Kipu/issues/95#issuecomment-6889721991).
- **Solicitudes de cambio:** No.

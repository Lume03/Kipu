# Plan de trabajo — GitHub Issue #98: Revisión UI/UX de Análisis

**Estado:** borrador para revisión del propietario  
**Issue:** [#98 — Revisión UI/UX — Análisis](https://github.com/Lume03/Kipu/issues/98)  
**Responsable asignado:** `saire22`  
**Rama prevista:** `ux/analisis`  
**Convención de commits:** `fix(ux-analisis): ...`

Este plan organiza el recorrido, los cambios permitidos y los momentos para informar avances en el issue. La implementación y los comentarios en GitHub empezarán cuando el propietario revise este plan y dé la indicación correspondiente.

## 1. Alcance acordado por el issue

### Incluye

- Pestaña Análisis: periodos, filtros, comparación, anillo y gráficas, tarjetas informativas, estados vacíos, restricciones Premium y navegación propia del módulo.
- Selección de plan en onboarding y en Ajustes, comprobando que la misma pantalla se entienda en ambos contextos.
- Pantalla de compra de plan y estados de carga, verificación, pendiente, error, reintento y disponibilidad.
- Comparación documental de los avisos de plan gratuito de los demás módulos, entregada como propuesta transversal para el integrador.

### Archivos de implementación permitidos

- `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt`
- `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsPresentation.kt`
- `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsMoneySection.kt`
- `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsFlowSections.kt`
- Los archivos `PlanSelection*` y `PlanPurchase*` enumerados en el issue, dentro de `feature/plans/presentation/`.
- `app/src/main/java/com/kipu/app/navigation/AnalysisNavigation.kt`
- Solo las claves usadas por estas pantallas en `app/src/main/res/values/strings.xml` y `strings_sprint4_ux.xml`.

### Límites

- No cambiar `data/`, `domain/`, `di/`, compras, sincronización, Room, Supabase ni reglas de negocio.
- No editar `KipuNavHost.kt`, componentes/temas compartidos, pantallas de otros módulos ni `strings_dev_plans.xml`.
- No modificar los avisos Premium de Cuentas, Movimientos, Planificación o Ajustes. Compararlos y documentar diferencias para el integrador.
- Si aparece un defecto compartido, incluirlo en **Propuestas transversales** con captura, justificación y archivo/componente; dejar su corrección al integrador.
- No actualizar `specs/` ni `.specify/`; el issue define esta revisión como UX sin Spec Kit.

## 2. Preparación del repositorio

- [ ] Confirmar que el checkout corresponde a `Lume03/Kipu`, que la rama base está actualizada y que el árbol de trabajo está limpio.
- [ ] Corregir o seleccionar el remoto correcto antes de publicar cambios. El checkout local está en el mismo commit que `Lume03/Kipu/main` (`fd03cd47`), pero su `origin` apunta a `LeonidasGarcia/Kipu`.
- [ ] Crear `ux/analisis` desde `main` actualizado. No se encontró todavía una rama remota con ese nombre.
- [ ] Guardar el SHA base en las notas del trabajo. El issue mapeó rutas sobre `2edc52bc`; `main` avanzó después, así que las referencias de archivo y línea deben confirmarse sobre el código actual.
- [ ] Revisar `docs/DESIGN.md` como autoridad visual vigente. `docs/README.md` y `docs/control-documental.md` registran la decisión visual del 2026-11-06; algunas referencias antiguas de specs siguen pendientes de propagación, y este trabajo no las modifica.

## 3. Recorrido y comprobación de flujos

Para cada hallazgo, registrar pantalla/flujo, pasos, estado de cuenta (Free/Premium), tema, tamaño de fuente, resultado observado, severidad y propuesta. Confirmar cada indicio del issue en la versión actual y en dispositivo antes de clasificarlo como defecto.

### Análisis

- [ ] Entrada a la pestaña y cabecera: título, periodo, ocultar saldos y navegación atrás cuando aplique.
- [ ] Selector de periodo, rango personalizado, botón atrás y encadenamiento con `DatePickerDialog`.
- [ ] Filtros: abrir/cerrar; selectores anidados de cuenta, categoría y comercio; conservar y limpiar selección.
- [ ] Comparación entre meses: meses completos, mes actual parcial, base vacía, gastos e ingresos; verificar el filtro por cuenta en la serie de saldo.
- [ ] Anillo patrimonial/monetario, detalle por cuenta, cero y múltiples monedas.
- [ ] Secciones de flujo, semanas, categorías, saldo y crédito; estados con datos escasos o vacíos.
- [ ] Avisos `PremiumBanner`, `RestrictedNotice` y `PremiumRequiredCard`, en Free y Premium; validar texto, redundancia y navegación a compra.
- [ ] Estados de carga, error/reintento, ausencia de movimientos, ausencia de cuentas y uso sin conexión.
- [ ] Rutas desde Análisis hacia cuentas, movimientos, metas, vencimientos, deudas y compra.

### Selección y compra de plan

- [ ] Selección de plan desde onboarding: título, explicación, selección, carga, error, botón principal y destino al confirmar.
- [ ] Preferencia de plan desde Ajustes: mismo contenido, botón atrás y textos apropiados al contexto.
- [ ] Compra desde Análisis y desde entradas representativas de otros módulos; comprobar que cada origen llega a la pantalla correcta.
- [ ] Ofertas cargando, catálogo vacío/no disponible, estados de compra, mensajes, colores y acciones de reintento.
- [ ] Repetir compra con conectividad limitada o sin conexión y registrar exactamente el estado visible.
- [ ] Comprobar comportamiento con Reduced Motion.

### Accesibilidad y presentación

- [ ] Temas claro y oscuro.
- [ ] Escala de fuente normal y 200 %, pantalla pequeña y pantalla grande disponibles.
- [ ] TalkBack: revisar los 22 iconos decorativos sin descripción de Análisis y los 3 de Compra; comprobar que los controles sí tengan nombre y acción.
- [ ] TalkBack: escuchar descripciones de anillo, gráficas de semanas y gráficas de saldo; evaluar si comunican los datos relevantes.
- [ ] Formato monetario y fechas; ocultar/mostrar importes también en descripciones accesibles.
- [ ] Objetivos táctiles de 48 dp, contraste, orden de foco y cierre de hojas/diálogos con atrás.
- [ ] Animación del anillo al activar Reduced Motion y al cambiar datos; revisar continuidad y saltos visuales.

## 4. Encargo transversal: avisos de acceso gratuito

Recorrer las entradas enumeradas en el issue y completar una tabla comparativa. El resultado irá al PR en **Propuestas transversales**, sin cambios de código en esos módulos.

| Área de entrada | Aviso/componente que comparar | Texto | Estructura | Acción | Evidencia |
|---|---|---|---|---|---|
| Análisis | `PremiumBanner`, `RestrictedNotice`, `PremiumRequiredCard` | Pendiente | Pendiente | Pendiente | Pendiente |
| Cuentas | Diálogo de cupo del dashboard (`DashboardScreen.kt`) | Pendiente | Pendiente | Pendiente | Pendiente |
| Movimientos | `MovementAccessCard` y aviso Kipu Pro | Pendiente | Pendiente | Pendiente | Pendiente |
| Importación de estados | `StatementPremiumSheet` | Pendiente | Pendiente | Pendiente | Pendiente |
| Presupuestos | Aviso de cuota en `BudgetComponents.kt` | Pendiente | Pendiente | Pendiente | Pendiente |
| Metas y deudas | `GoalPlanLimitSheet`, `DebtPlanLimitSheet` | Pendiente | Pendiente | Pendiente | Pendiente |
| Reglas de comercios | Aviso de Premium en reglas de alias | Pendiente | Pendiente | Pendiente | Pendiente |
| Categorías | `QuotaBanner` y aviso de límite | Pendiente | Pendiente | Pendiente | Pendiente |

## 5. Registro y priorización de hallazgos

- [ ] Mantener una fila por hallazgo con el formato solicitado por el issue:

  | Pantalla/flujo | Problema observado | Severidad | Propuesta | ¿Módulo o compartido? | Evidencia |
  |---|---|---|---|---|---|

- [ ] Clasificar severidad alta/media/baja según impacto en comprensión, navegación, acceso, privacidad o ejecución de acciones.
- [ ] Separar hallazgos confirmados en dispositivo de indicios estáticos o propuestas de mejora.
- [ ] Priorizar defectos altos. Para cada punto no corregido, dejar justificación y evidencia en el PR.
- [ ] Revisar que los cambios no dependan de los elementos sin entrada en la app cubiertos por el issue #94.

### Indicios iniciales del issue para confirmar

- [ ] El anillo usa `tween(700)` sin Reduced Motion.
- [ ] `plainAmount`/`maskedPlain`: validar formato y privacidad; no reemplazar automáticamente, pues el código también usa `MoneyText` para montos visibles.
- [ ] Revisar los tamaños explícitos de fuente (22, 16 y 24 sp) con escala de fuente ampliada.
- [ ] Determinar cuáles iconos con `contentDescription = null` son decorativos y cuáles necesitan descripción.
- [ ] Validar el contenido oral de las gráficas; hay descripciones semánticas en el código que deben comprobarse con TalkBack.
- [ ] Comprobar si el estado sin conexión necesita una indicación, considerando que Análisis puede mostrar datos locales.
- [ ] Comprobar si selección de plan necesita mejor indicación de carga/reintento y si sus textos cambian apropiadamente entre onboarding y Ajustes.
- [ ] Comparar visualmente `PENDING`, `RETRYABLE`, `ERROR` y `UNAVAILABLE`; el código actual reutiliza el color de contenedor para varios estados.

## 6. Comentarios de avance en GitHub

Publicar comentarios breves en el issue cuando el propietario lo indique. Propuesta de hitos:

1. **Inventario completado:** resumen de recorridos probados y hallazgos confirmados, con severidad; distinguir pendientes por evidencia.
2. **Cambios listos para revisar:** resumen por pantalla, decisiones tomadas y propuestas transversales para el integrador.
3. **Verificación completada:** dispositivo/Android, tema, escala de fuente, comandos y resultados; limitaciones concretas si las hubiera.
4. **PR abierto:** enlazar el PR y verificar que incluya `Closes #98`.

No publicar desde este borrador. Antes de enviar cada comentario, preparar el texto en la conversación para revisión del propietario.

## 7. Implementación

- [ ] Confirmar el inventario y los hallazgos con evidencia antes de editar.
- [ ] Implementar únicamente correcciones confirmadas y de severidad alta/media acordes con `docs/DESIGN.md`.
- [ ] Reutilizar tokens, componentes, `MoneyText`, navegación y semántica existentes; no crear sistemas paralelos.
- [ ] Mantener cada cambio enfocado; agrupar commits por hallazgo o grupo relacionado con formato `fix(ux-analisis): ...`.
- [ ] Registrar los componentes compartidos para el integrador, sin modificarlos.
- [ ] Revisar `git diff --name-only main...` antes de continuar y retirar del alcance cualquier archivo no permitido.

## 8. Verificación y PR

- [ ] Recorrer en dispositivo todos los flujos del inventario que estén disponibles y registrar Android/dispositivo, tema y escala de fuente.
- [ ] Construir con `./gradlew :app:assembleDebug`.
- [ ] Ejecutar las pruebas unitarias focalizadas del módulo en verde; anotar comandos y resultados en el PR.
- [ ] Si se agregan pruebas instrumentadas, otorgar los permisos requeridos desde su setup (`@Before`/helper con `uiAutomation`). Si ADB no está disponible, continuar con verificaciones posibles y anotar con precisión la cobertura pendiente.
- [ ] Adjuntar capturas antes/después con el mismo dispositivo, tema y datos. Para animaciones, adjuntar GIF o video corto. Subir evidencia al PR, no al repositorio.
- [ ] Revisar diff, `git diff --check`, rama basada en el `main` actualizado y archivos dentro del reparto.
- [ ] Abrir un PR único para Análisis, usar la plantilla del issue, incluir sección **Propuestas transversales** y `Closes #98`.

### Criterios de salida

- Inventario de flujos recorrido y documentado.
- Hallazgos de severidad alta corregidos o justificados con evidencia.
- PR con capturas de cada cambio, propuestas transversales y verificación registrada.
- Cambios limitados al reparto de Análisis.

## Referencias

- [Issue #98 — Revisión UI/UX — Análisis](https://github.com/Lume03/Kipu/issues/98)
- [Issue #94 — Código de presentación sin entrada en la app](https://github.com/Lume03/Kipu/issues/94)
- `docs/README.md`
- `docs/DESIGN.md`
- `docs/control-documental.md`
- `specs/009-ep-inf-informacion-financiera/spec.md` (solo para contexto de producto; este trabajo no cambia specs)


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

### Observación preliminar del primer recorrido — 2026-11-16

- En Free, al elegir criterios como comercio/categoría/cuenta, las estadísticas permanecen como vista general y no muestran el resultado filtrado. En Premium, los mismos tipos de filtro sí modifican las estadísticas.
- **Clarificación del Owner — 2026-11-16:** Free no puede aplicar ningún filtro, ni individual ni combinado. «Filtros combinados» significa combinar criterios de grupos distintos, por ejemplo cuenta/tarjeta + categoría + comercio. La vista general sin filtrar en Free es el comportamiento esperado.
- **Hallazgo UX candidato confirmado:** Free permite abrir el panel y seleccionar criterios que no aplican las estadísticas; en Premium, sí se aplican. El Owner observa que la oferta de planes aparece después de intentar usar un filtro, cuando el usuario ya invirtió tiempo configurándolo.
- **Propuesta del Owner para evaluar, aún no aprobada para implementación:** mostrar un candado junto a «Filtros» en Free y, al tocarlo, abrir la explicación/entrada actual a planes Premium antes de permitir configurar criterios. Esto anticipa la restricción y evita hacerle completar una acción que no tendrá efecto.
- Mantener pendiente la confirmación del momento exacto en que aparece la oferta Premium en Free, porque en una prueba anterior el Owner indicó que la pantalla no cambiaba al aplicar el filtro. Severidad provisional media; revisar el flujo completo antes de decidir el cambio.
- Estado: hallazgo reportado por el Owner en dispositivo; comentario de avance del issue pendiente de confirmar.

### Observación del selector de rango personalizado — 2026-11-16

- En Premium, al abrir «Rango personalizado», el encabezado «Fecha de finalización» se distribuye en muchas líneas dentro de una columna estrecha, junto a «Fecha de inicio» y el icono de edición. La captura recibida evidencia un defecto de presentación del selector en el dispositivo probado.
- El Owner confirmó que pudo seleccionar el rango, aplicarlo y actualizar las estadísticas. El problema observado es principalmente visual.
- Severidad: media, por legibilidad del encabezado.
- Corrección implementada en `RangeDialog`: encabezado compacto con los campos «Inicio» y «Fin», fecha seleccionada y año. El Owner confirmó que funciona bien en su dispositivo.

### Observación de comparación de periodos — 2026-11-16

- El Owner revisó comparaciones en ambos órdenes: al invertir los meses, se invierten los valores de «Periodo anterior» y «Periodo comparado» y cambia el signo de la diferencia.
- La tarjeta utiliza el título genérico «Comparación» y las etiquetas «Diferencia»/«Variación». No expresa en lenguaje directo si subió o bajó el gasto o el ingreso.
- **Hallazgo UX candidato:** el usuario puede interpretar una diferencia positiva/negativa como ahorro, aunque la pantalla compara gasto o ingreso y no calcula ahorro neto. La tarjeta tampoco identifica claramente en su título cuál de esas métricas se está comparando.
- Resolución acordada: titular las tarjetas «Gastos» e «Ingresos» y añadir al final una frase dinámica que nombre la dirección y los periodos, por ejemplo «Gastaste S/37 más en noviembre que en octubre» / «Ingresaste S/200 más en noviembre que en octubre». Adaptar la frase cuando la diferencia sea negativa o cero. No llamar «ahorro» a la reducción de gasto sin una métrica aprobada de ahorro.
- Ajuste aplicado al estado sin base: el aviso ahora indica que no se registró el tipo de movimiento comparado, sin afirmar incorrectamente que faltó gasto en la tarjeta de ingresos.
- Severidad provisional: media; las fechas y montos se muestran, pero la interpretación del resultado puede ser ambigua. Evidencia: capturas de comparación Jun/Jul 2026 y Oct/Nov 2026 recibidas en la conversación.
- Corrección implementada y validada por el Owner en dispositivo: las tarjetas identifican «Gastos» e «Ingresos» y explican el cambio con los nombres de los periodos, incluida la condición parcial. Se corrigió también el aviso de porcentaje sin base para no afirmar que faltó gasto cuando se compara ingreso.
- El Owner validó en dispositivo el caso sin base para gastos: agosto 2026 S/ 0 frente a octubre 2026 S/ 45; no aparece porcentaje y el aviso/frase describen el resultado correctamente.
- El Owner confirmó también el caso sin base para ingresos: aparece «Sin base de comparación» y la explicación usa «Ingresaste».
- El Owner comprobó que el filtro de una cuenta cambia la gráfica de barras de saldo y que, al quitarlo, la vista general vuelve correctamente.
- El Owner confirmó que al seleccionar un segmento/cuenta del anillo se actualizan el centro y la fila, al deseleccionar vuelve el total y el modo «Por destino» permite el mismo detalle.
- El Owner confirmó que los accesos de «Debo» y de la meta «Viaje a Lurin» desde Análisis abren Deudas y Metas, respectivamente.
- El Owner confirmó que al seleccionar una cuenta en el anillo y pulsar «Ver cuenta» se abre el detalle de esa cuenta.
- El Owner confirmó que desde «Por destino» > «Próximos pagos» > «Ver pagos» se abre la lista de vencimientos.
- El Owner confirmó que desde el destino «Próximos pagos», «Ver pagos» abre los vencimientos y considera completo el recorrido de Análisis: todo lo revisado funciona correctamente. Se conserva como propuesta pendiente el candado para Filtros Free; Reduced Motion quedó sin probar por falta del ajuste en el dispositivo.
- El Owner revisó el flujo semanal y las categorías; confirma que las semanas y sus datos se muestran correctamente. Falta cerrar la revisión de las secciones de saldo y crédito.
- El Owner confirmó que, al elegir un periodo sin gastos ni ingresos, Análisis muestra un mensaje claro de ausencia de movimientos.
- El Owner abrió Análisis sin conexión: los datos se conservaron y la pantalla volvió a la normalidad al desactivar Modo avión.
- El Owner confirmó que el icono del ojo oculta los importes de Análisis y que al volver a tocarlo se muestran correctamente.
- En la captura del 17 nov 2026, el resumen de deuda muestra S/ 20 y coincide con los S/ 20 pendientes de Planificación (S/ 25 total, 20 % pagado). El Owner considera clara la meta «Viaje a Lurin» dentro del bloque «Crédito y deudas».
- La serie de saldo muestra S/ 1,360 al 17 nov 2026, mínimo S/ 147, máximo S/ 1,360 y una variación de +S/ 1,188. Según `BalanceLineSection`, esa variación es el cambio neto desde el primer día del periodo hasta el día seleccionado, no necesariamente una operación individual.
- El Owner comprobó que el aviso de Análisis en Free abre la pantalla de planes al tocar «Ver planes Premium» y que, al volver atrás, regresa a Análisis.
- El Owner confirmó que desde Ajustes, al abrir el resumen del Plan Free y elegir/ver planes, llega a la pantalla de planes y que al volver regresa al resumen del plan.
- El Owner revisó visualmente la selección de plan desde Ajustes y confirma que se ve bien.
- El Owner confirma que ya probó al inicio la selección de plan durante el alta inicial y funcionó bien; no necesita repetir ese flujo.
- El Owner probó el límite Free de cuentas/tarjetas: el diálogo muestra «Ver planes» y permite regresar a Cuentas sin completar una acción de archivo o alta.
- En Presupuestos, la cuenta Free del Owner muestra 0 de 2 activos y no permite reproducir el límite sin crear datos. Un compañero ya probó el aviso y reporta que funciona correctamente; queda registrado como verificación externa, no como prueba directa del Owner.
- El Owner confirma que aparecen los avisos de límite Free en Metas y Deudas y que ambos funcionan correctamente.
- En Gestionar Categorías, el Owner ve un contador de cupo Free de 2 de 5 en la pestaña revisada; está bajo el límite y no se crearán categorías de prueba para forzar el aviso.
- En Ajustes > Reglas de alias, el Owner observó en Free «Ver Premium», confirmó que abre los planes y que al volver regresa a Reglas de alias.
- El Owner revisó en Movimientos el aviso de filtros Kipu Pro y confirma que queda claro qué está bloqueado y cómo volver a Plan Free.
- El Owner comprobó el aviso Premium de importación BCP: explica la restricción y «Ahora no» regresa a la pantalla de importación.
- En Free, el selector marca «Mes anterior», «Rango personalizado», «Comparar periodos» y los meses anteriores con candado y etiqueta «Premium». Al intentar uno, aparece una tarjeta amarilla con el botón «Ver Premium»; el Owner confirmó que abre los planes y que al volver regresa al selector.
- **Hallazgo visual confirmado:** en modo oscuro, las tarjetas de ofertas de la pantalla «Kipu Premium» tenían fondo blanco y texto claro, por lo que los nombres y precios casi no se distinguían.
- **Corrección implementada y validada por el Owner en modo oscuro y claro:** las tarjetas de ofertas y carga usan la superficie y el color de contenido del tema actual; el borde y el indicador de selección también usan el color primario del tema. En ambas apariencias se distinguen los nombres, condiciones y duraciones.
- El Owner confirmó que al seleccionar distintos planes el borde y la marca visual se mueven al plan elegido; no activó ninguno.
- El Owner confirmó que la pantalla de planes pasó la prueba de texto grande sin problemas visibles.
- TalkBack: el Owner indica que un compañero ya revisó esta comprobación y reporta que funciona bien; queda registrado como verificación externa.
- Reduced Motion: no probado; el Owner no encuentra el ajuste en su teléfono y decidió omitir esta comprobación manual.

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


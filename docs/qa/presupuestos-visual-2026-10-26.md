# Presupuestos: ajuste visual del 26 de octubre de 2026

Encargo directo del propietario basado en diez referencias visuales. Se trabaja en la rama existente, sin Spec Kit, commits ni push. El propietario confirmó mantener el alcance visual y mostrar finalizados; pausa/reanudación quedan excluidas.

## Cambios

- Crear y editar comparten límite manual sin importes sugeridos, icono elegible, moneda y alcance en dos cajas, categoría seleccionada con acceso al selector global existente, periodo expandible y umbrales con controles de incremento/decremento. El 100 % permanece fijo.
- Se reutilizan el catálogo de iconos y el selector de categorías de la aplicación. El catálogo admite un subtítulo contextual; conserva su texto previo en los demás usos.
- Lista con tarjetas compactas, importes destacados y colores de consumo. Tras las observaciones del propietario se eliminan los filtros y Finalizados pasa a ser un desplegable al final, debajo de crear. Cuando no hay activos se muestra la pantalla inicial, aunque queden finalizados.
- Confirmación de creación con resumen de alcance, periodo, consumo y alertas. El check tiene un pulso suave que se desactiva con movimiento reducido.
- Detalle con importes y configuración más legibles. La confirmación de eliminación distingue el presupuesto de los movimientos y saldos, informa del borrado de su historial y ofrece finalizarlo como alternativa real. No se simulan pausa ni deshacer.
- Los estados de atención respetan los umbrales configurados, incluidos valores diferentes de 50/80. Se conserva la máscara de privacidad de los importes.

En presupuestos por categoría prevalece el icono de la categoría o subcategoría elegida; el formulario lo explica y desactiva el selector manual. En «Todos los gastos» el icono elegido es una preferencia visual local por usuario y presupuesto: persiste al reabrir la aplicación en este dispositivo y no se sincroniza entre teléfonos. No se modifican modelos financieros, migraciones ni contratos remotos. Tarjetas de crédito, tasas, Metas y Deudas quedan fuera de este encargo.

Las cajas de moneda se compactan y mantienen igual altura. Los periodos recurrentes se ordenan en dos columnas con Personalizado ocupando una fila completa. La confirmación de finalizar usa acentos verde y ámbar, resumen del periodo e importes, explica el historial conservado, el cupo liberado y la detención de renovaciones/alertas, y confirma que los saldos no cambian.

## Verificación

Corrección de observaciones: compilación correcta, **101 unitarias** sin fallos/errores y **32 instrumentadas** correctas en S24+ físico (`OK (32 tests)`, 34,042 segundos). Se verifica expresamente el estado vacío con solo finalizados, expansión/contracción del historial, ausencia de filtros, precedencia de iconos y confirmación/cancelación de finalizar. Logs: `gradle-observaciones.log` y `s24-observaciones.log` en la carpeta local de esta ejecución.

Revisión manual de esta corrección: lista y desplegable con los datos preexistentes, nuevo formulario sin guardar, periodos alineados, diálogo de finalizar cancelado y edición sin guardar. Se conservan un activo y un finalizado; no se generan fixtures ni movimientos nuevos. Se comprueba también el periodo con fuente 1,3 y se restaura 0,9. Capturas locales con prefijo `s24-observaciones-`, sin publicación de datos personales.

Registro de la primera entrega, anterior a estas observaciones:

- Compilación de aplicación y APK de pruebas correcta.
- 100 pruebas unitarias focalizadas en Presupuestos, distribuidas en 16 suites: cero fallos y cero errores.
- 30 pruebas instrumentadas correctas en el S24+ físico: 29 de pantallas y una de persistencia/aislamiento del icono. Resultado `OK (30 tests)`, 31,297 segundos. Incluyen selector compartido, iconos, umbrales, finalizados, navegación, confirmaciones y máscara de privacidad.
- Tras adaptar los filtros a dos columnas con fuente ampliada, nueva compilación e instalación correctas y dos pruebas focalizadas de filtros/finalizados en verde (`OK (2 tests)`). Inspección final en el teléfono con fuente 1,3: nombres completos y contadores legibles.
- Recorrido manual real en el S24+: crear `[QA] Visual presupuesto`, seleccionar Alimentación mediante el selector compartido y un icono distinto del predeterminado, comprobar la confirmación, abrir detalle, editar límite de 600 a 128 PEN, comprobar atención al 78 %, consultar alertas, eliminar y abrir finalizados.
- El presupuesto de prueba quedó eliminado: se verificaron 0 activos y el mismo finalizado que existía antes. No se crearon movimientos financieros ni se modificó el presupuesto preexistente.
- Revisión con fuente del sistema a 1,3: formulario, periodos y umbrales accesibles mediante desplazamiento y acción principal visible. Se restaura la fuente original 0,9 y se conserva modo avión desactivado.

Los logs y capturas se conservan localmente en `scratch/qa-visual/presupuestos-2026-10-26/`. Las capturas contienen datos de la sesión real y no se publican como evidencia anonimizada. Los datos QA de movimientos del encargo anterior no forman parte de esta limpieza.

Instalación mediante actualización sobre la aplicación existente, sin desinstalación, borrado de datos ni cierre de sesión. Se usa exclusivamente el S24+ físico para este encargo y se respetan los candados de Gradle y del dispositivo.

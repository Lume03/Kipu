# Metas: revisión visual del 26 de octubre de 2026

Encargo directo del propietario a partir de nueve capturas. Misma rama, sin Spec Kit, commits ni push. Tarjetas/tasas, Presupuestos y Deudas quedan fuera de esta revisión.

## Resultado

- En Planificación se conserva el botón flotante «+» para crear metas y se retira la barra duplicada de crear. El cupo sigue comprobándose en el flujo existente.
- Aportar y Retirar comparten pantalla y pestañas. Retirar muestra solo las cuentas con reserva en esa meta y valida contra su reserva, sin utilizar lo reservado para otras metas. Se conserva la selección explícita de otra meta cuando retirar desde una completada obliga a resolver el límite del plan.
- Sin atajos de 100/300/500. El propietario aprobó mantener el cálculo de «Aportar desde» y aclararlo: disponible para reservar es saldo confirmado menos reservas de todas las metas. Se mantienen las cuentas y el traslado interno opcional; no se cambian saldos ni operaciones del dominio.
- Se retira del detalle el bloque completo «Respaldo real». La comprobación de fondos permanece en los flujos de aportar y retirar.
- Gráfico con Día (siete días de la semana), Semana (semanas del mes) y Mes (enero a diciembre), navegación entre periodos, total y consulta de barras. Incluye periodos sin aportes y excluye retiros, reasignaciones y eventos anulados. Los importes respetan la máscara de privacidad.
- En vista mensual, solo con fecha de meta vigente y dinero pendiente, se ofrece una guía mensual: pendiente dividido entre los meses calendario restantes, incluido el actual, redondeando hacia arriba a la unidad menor. No es un cargo ni una aportación automática. No se muestra para metas sin fecha, vencidas, completadas o totalmente financiadas.
- Crear/editar: icono a la izquierda del nombre, sin «Parte de una idea», sin atajos de meses/años. Fecha opcional seleccionada mediante calendario, sin escritura manual.
- La cuenta vinculada usa el selector compartido y sus logos/iconos reales. Se observan los instrumentos existentes únicamente para su presentación, se restringe la lista a cuentas compatibles y se conserva la posibilidad de quitar la vinculación. Sin cambios de modelos financieros ni de base de datos.

## Verificación

- Compilación de aplicación y APK instrumentado correcta.
- 31 unitarias de Metas, ocho suites, cero fallos/errores. Incluyen cinco casos nuevos de agregación temporal, zonas horarias, meses vacíos, exclusión de retiros/anulaciones y cálculo condicionado de la sugerencia.
- 28 instrumentadas existentes correctas: 14 de gestión, nueve de ciclo de vida, cuatro de aportes y una de navegación.
- Seis instrumentadas nuevas correctas en S24+ físico: creación única en la lista integrada, formulario/calendario, selector compartido con metadatos de instrumento, gráfico día/semana/mes, sugerencia condicionada y alternancia de fondos con límite por meta. Resultado `OK (6 tests)`, 8,429 segundos.
- Tras conectar el estado de guardado de ambos flujos y quitar el foco al cambiar de pestaña: nueva compilación/instalación y comprobación focalizada de alternancia/límite por meta correcta (`OK (1 test)`, 2,567 segundos). No se repiten suites generales que ya estaban en verde.
- La primera corrida detectó dos errores en fixtures/interacción de los nuevos tests: identificador de cuenta sin UUID y pulsación de pestaña con teclado/desplazamiento. Se corrigieron los fixtures y la interacción; no se relajó la validación financiera.

Validación manual en S24+ físico: lista sin crear duplicado, gráfico con doce meses, detalle sin respaldo, Retirar sin diálogo y cambio a Aportar, formulario sin ideas/fechas sugeridas y selector compartido con logos BBVA/BCP e icono de efectivo. Se consulta la sesión real sin ejecutar aportes, retiros, traslados ni cambios sobre la meta Laptop. Los datos de la cuenta y sus reservas se conservan.

Se verifica el calendario pulsando el cuerpo del campo de fecha, sin abrir teclado. Se cancela sin guardar. El teléfono conserva fuente 0,9, modo avión desactivado y sesión; se deja en la lista de Metas y se liberan los candados.

Logs y capturas locales en `scratch/qa-visual/metas-2026-10-26/`; no se publican las capturas de la sesión por contener información personal. Se usa exclusivamente el S24+ físico, con candados de dispositivo/Gradle, instalación por actualización y sesión conservada.

# ACC3 — accounts/presentation: detail + dashboard

Esta porción muestra cuentas, saldos, tarjetas y sus movimientos, y permite iniciar operaciones y editar instrumentos. Se asumieron miles de usuarios con uno o dos dispositivos, miles de movimientos por usuario y uso frecuente sin conexión.

## Hallazgos

### [ACC3-01] Un resumen de tarjeta ausente se presenta como deuda cero
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:203-205, 1449-1452, 1890-1892
- Qué está mal: El detalle obtiene el resumen del dashboard, pero cuando no está disponible inventa deuda cero, crédito disponible igual a la línea y uso cero. También oculta el botón de pago al interpretar el resumen ausente como deuda cero.
- Escenario concreto de fallo: Si falla la carga del dashboard mientras sí carga una tarjeta con deuda de S/ 900, el detalle muestra S/ 0 de deuda y toda la línea disponible, y no ofrece pagarla.
- Fix mínimo: Representar el resumen ausente como carga o error con reintento, sin cifras ni decisiones basadas en cero; agregar un test con tarjeta cargada y resumen ausente.
- Confianza: alta
- Cross-módulo: no

### [ACC3-02] El filtro de ciclo no usa la fecha de corte
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:2105-2137, 2418-2422
- Qué está mal: «Ciclo actual» filtra los últimos 30 días desde el instante actual y la función ni siquiera recibe el día de corte de la tarjeta.
- Escenario concreto de fallo: Con corte el día 20 y fecha actual 21, una compra del día 19 aparece en el ciclo actual aunque pertenece al anterior; un ciclo que cruce febrero tampoco dura siempre 30 días.
- Fix mínimo: Pasar el día de corte y filtrar por los límites reales del ciclo usando fechas locales; probar movimientos a ambos lados del corte y febrero.
- Confianza: alta
- Cross-módulo: no

### [ACC3-03] El modo privado deja visible el porcentaje de uso
- Severidad: media
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:1613-1616, 1802-1811, 1821-1825
- Qué está mal: La pantalla oculta importes con el modo privado, pero conserva el porcentaje exacto y la barra proporcional de utilización de la tarjeta.
- Escenario concreto de fallo: Al ocultar importes, una persona que mira la pantalla sigue viendo «Uso 80 %» y la barra correspondiente; el dashboard sí oculta esta información cuando está en modo privado.
- Fix mínimo: Ocultar porcentaje y progreso cuando LocalBalanceMasked esté activo, también en las propiedades de accesibilidad; probar una tarjeta con uso no nulo.
- Confianza: alta
- Cross-módulo: no

### [ACC3-04] Las fechas de pago quedan congeladas al cambiar de día
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:1603-1610; app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardSections.kt:547-548
- Qué está mal: Las fechas calculadas se recuerdan solo por el día configurado y la fecha actual se recuerda sin clave; no se actualizan al volver a primer plano o cruzar medianoche.
- Escenario concreto de fallo: Si el detalle permanece abierto desde el día anterior al vencimiento, tras medianoche sigue diciendo que vence en un día; al pasar el vencimiento el dashboard puede mantener la fecha pasada.
- Fix mínimo: Actualizar la fecha local al reanudar la pantalla y al cambio de día, y usarla como clave de los cálculos; probar el cambio de fecha con reloj controlado.
- Confianza: alta
- Cross-módulo: no

### [ACC3-05] El test de borradores prueba una validación que producción no usa
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/CreditPurchaseDraftValidation.kt:3-7; app/src/test/java/com/kipu/app/feature/accounts/presentation/detail/CreditPurchaseDraftValidationTest.kt:8-19; app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:408-424
- Qué está mal: La búsqueda de referencias encuentra el validador únicamente en su archivo y su test; la confirmación real usa otro camino. Por tanto los tests siguen verdes aunque se rompa la validación real de categoría.
- Escenario concreto de fallo: Si el simulador permite confirmar sin categoría, este test aislado sigue pasando y el `requireNotNull` del callback puede lanzar una excepción.
- Fix mínimo: Probar la confirmación real con categoría ausente y borrar el helper y test aislados si no se conectan al flujo real.
- Confianza: alta
- Cross-módulo: no

### [ACC3-06] El test de archivados pasa sin comprobar la condición archivada
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardEmptyStatePolicyTest.kt:45-56
- Qué está mal: Ambos casos pasan un DashboardUiState sin dashboardData; la función devuelve false por ese motivo antes de evaluar las listas archivadas.
- Escenario concreto de fallo: Si se eliminan las comprobaciones de cuentas y tarjetas archivadas, este test sigue pasando y una cuenta archivada podría mostrarse como primera visita sin detectarlo.
- Fix mínimo: Pasar `dashboardData = emptyDashboard()` en ambos casos y mantener las listas archivadas como única diferencia.
- Confianza: alta
- Cross-módulo: no

### [ACC3-07] Todas las compras aparecen como una sola cuota
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:2544-2548
- Qué está mal: La etiqueta de cada compra de crédito está fijada a «1 cuota» sin consultar sus condiciones.
- Escenario concreto de fallo: Una compra confirmada a 12 cuotas aparece en movimientos con la etiqueta «1 cuota».
- Fix mínimo: Mostrar el número real asociado a la compra o eliminar la etiqueta si no está disponible; probar una compra a 12 cuotas.
- Confianza: alta
- Cross-módulo: no

### [ACC3-08] El botón de eliminación permanente solo intenta archivar
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:726-729, 2062-2083, 938-954
- Qué está mal: En una tarjeta ya archivada, «Eliminar tarjeta permanentemente» llama a la acción de archivar y abre una confirmación que promete conservar el historial.
- Escenario concreto de fallo: Al pedir borrar una tarjeta archivada aparece «¿Archivar la tarjeta?» y al confirmar se intenta archivarla de nuevo, sin eliminarla.
- Fix mínimo: Quitar el botón si no existe borrado permanente; si es un requisito aprobado, conectarlo a su operación con confirmación precisa. Probar la acción de una tarjeta archivada.
- Confianza: alta
- Cross-módulo: no

### [ACC3-09] La deuda total se etiqueta como facturada
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:1705-1719
- Qué está mal: La pantalla muestra la deuda actual junto a «Facturado» sin calcular qué parte pasó por el corte.
- Escenario concreto de fallo: Una compra recién registrada antes del corte aumenta la deuda actual y se presenta como facturada aunque pertenezca al ciclo abierto.
- Fix mínimo: Quitar «Facturado» o mostrar el importe facturado calculado del ciclo; probar una compra previa al corte.
- Confianza: alta
- Cross-módulo: no

### [ACC3-10] El importe del borrador queda expuesto en modo privado
- Severidad: media
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:365-378
- Qué está mal: El campo de importe de una compra se muestra como texto normal y no consulta `LocalBalanceMasked`, a diferencia del campo de corrección de saldo.
- Escenario concreto de fallo: Con importes ocultos, al escribir 250.00 en «Nueva compra» la cifra queda visible en pantalla y en la semántica del campo.
- Fix mínimo: Aplicar el tratamiento privado de `OpeningBalanceCorrectionSheet`; probar texto y semántica con privacidad activa.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `CreditCardPeriodPanel` → resumen ausente mientras la tarjeta tiene deuda: no mostrar cifras inventadas ni habilitar decisiones basadas en cero.
- `CreditCardMovementsSection` → movimientos antes y después del corte, incluido febrero.
- `CreditCardPeriodPanel` → modo privado con utilización distinta de cero, incluido el árbol de accesibilidad.
- `DashboardEmptyStatePolicyTest.archivedAccountsAndCardsDoNotGetMistakenForFirstUse` → datos de dashboard vacíos válidos con solo un instrumento archivado.

- CreditCardMovementRow → compra financiada a 12 cuotas no muestra «1 cuota».
- CreditCardActionsSection → tarjeta archivada no promete eliminar cuando solo archiva.
- AccountDetailScreen → importe de compra oculto visualmente y en accesibilidad con privacidad activa.

## Lo que no revisé

Se leyeron completos los archivos de producción pendientes y se revisó qué cubre cada test instrumentado del alcance. No ejecuté pruebas, builds, dispositivos ni servicios por las restricciones del encargo; tampoco audité módulos ajenos.

## Dudas

- Confirmar la convención de límites del ciclo en el día de corte antes de implementar el filtro; el código actual no la establece en esta pantalla.


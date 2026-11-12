# MOV4 — movements/presentation: historial, filtros y componentes

Esta porción presenta el historial paginado, sus filtros y las acciones sobre movimientos. Se asumieron miles de movimientos por usuario, uso frecuente sin conexión y uno o dos dispositivos.

## Hallazgos

### [MOV4-01] La comparación con el mes anterior no hace nada
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:710 y app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFilterDraft.kt:45
- Qué está mal: El interruptor cambia `comparePreviousMonth`, pero la validación no lo incluye en el estado aplicado y el historial no recibe ninguna comparación.
- Escenario concreto de fallo: El usuario activa «Comparar con mes anterior» y aplica: ve el historial normal, sin comparación; al reabrir el filtro el interruptor vuelve a apagarse.
- Fix mínimo: Implementar la comparación hasta su resultado visible, o retirar el interruptor hasta que exista dicha funcionalidad.
- Confianza: alta
- Cross-módulo: no

### [MOV4-04] La confirmación de eliminación revela el importe oculto
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryDelete.kt:103
- Qué está mal: El diálogo inserta el importe real en un Text sin consultar la preferencia que oculta saldos. El importe también queda en las semánticas accesibles.
- Escenario concreto de fallo: Con «Ocultar importes» activo, deslizar una operación y abrir la confirmación muestra su cantidad a quien mire la pantalla o use el lector de pantalla.
- Fix mínimo: Mostrar el importe mediante MoneyText, que respeta el estado de privacidad, y comprobar también sus semánticas.
- Confianza: alta
- Cross-módulo: no

### [MOV4-05] La oferta fija un precio y un descuento sin consultar Google Play
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:1794
- Qué está mal: El anuncio siempre afirma 40 % de ahorro y S/ 59.90 por año, mientras la pantalla de compra obtiene el precio localizado de Play.
- Escenario concreto de fallo: Si Play ofrece otro precio, moneda o promoción, el usuario ve una cifra en el anuncio y otra al abrir la compra.
- Fix mínimo: Retirar precio, equivalencia y descuento del anuncio o mostrar los datos reales de la oferta cargada.
- Confianza: alta
- Cross-módulo: no

### [MOV4-02] Una actualización descarta las páginas que el usuario ya cargó
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt:940
- Qué está mal: Una invalidación conserva temporalmente la lista, pero al terminar la lectura la reemplaza por solo la primera página y su cursor.
- Escenario concreto de fallo: Al consultar movimientos antiguos tras cargar varias páginas, llega una sincronización o modificación local: desaparecen las páginas posteriores y el usuario debe volver a cargarlas.
- Fix mínimo: En una actualización del mismo filtro, recargar y conservar el rango de páginas que estaba visible, manejando cambios en los cursores.
- Confianza: alta
- Cross-módulo: no

### [MOV4-03] El número previo de resultados no representa la consulta aplicada
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:113 y app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:158
- Qué está mal: El contador filtra únicamente las filas ya cargadas de la consulta anterior; además usa medianoche UTC y no comprueba la moneda, mientras que el filtro aplicado usa días locales y moneda.
- Escenario concreto de fallo: Si la primera página no contiene gastos y hay gastos en las siguientes, elegir gastos muestra «Ver 0 movimientos» aunque al aplicar sí aparecen resultados; un movimiento a las 20:00 en Lima también puede contarse en otro día.
- Fix mínimo: Calcular el conteo mediante la misma consulta y límites que se aplicarían al borrador, o eliminar el número hasta disponer de un conteo fiable.
- Confianza: alta
- Cross-módulo: no

### [MOV4-06] Un rango en dólares se muestra con símbolo de soles
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:389 y app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:1498
- Qué está mal: Las dos etiquetas del borrador imprimen S/ sin tener en cuenta draft.currency.
- Escenario concreto de fallo: Al seleccionar USD y un mínimo de 100, la hoja muestra «S/ 100» aunque la consulta filtra dólares.
- Fix mínimo: Usar la moneda seleccionada en ambas etiquetas o quitar el símbolo si todavía no se eligió moneda.
- Confianza: alta
- Cross-módulo: no

### [MOV4-07] La paginación puede detenerse con una página casi toda oculta
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt:198
- Qué está mal: La observación emite solo cuando cambia el booleano «cerca del final»; el segundo disparador carga páginas solo si no queda ninguna fila visible.
- Escenario concreto de fallo: Si una primera página tiene una sola fila visible porque las demás fueron ocultadas, la lista puede permanecer cerca del final desde el estado inicial de carga. La observación no emite de nuevo al recibir la página y la condición de lista vacía es falsa, de modo que las páginas restantes no se piden aunque existan.
- Fix mínimo: Volver a evaluar la carga al cambiar cursor o estado de carga mientras la vista siga cerca del final, evitando solicitar otra página cuando haya error o una carga en curso.
- Confianza: media
- Cross-módulo: no

### [MOV4-08] El aviso y el reintento al reconectar ignoran pendientes fuera de la página
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt:232
- Qué está mal: El número de pendientes se calcula sobre los movimientos visibles de la consulta paginada, y ese mismo número decide mostrar el aviso y programar sincronización al volver la red.
- Escenario concreto de fallo: Si hay un movimiento pendiente en una página anterior o excluido por el filtro, el número queda en cero; no aparece el aviso de reconexión ni se solicita el envío inmediato al volver la red.
- Fix mínimo: Obtener el estado de pendientes del propietario desde la fuente completa de sincronización y usarlo para el aviso y el disparador.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `MovementHistoryViewModel.loadFirstPage` → cargar dos páginas, emitir una invalidación y comprobar que los movimientos consultados siguen accesibles sin reiniciar la navegación.
- `MovementFiltersSheetContent` → con resultados fuera de la primera página y un movimiento cerca de medianoche de Lima, verificar que el contador coincide con la consulta aplicada.
- `MovementFilterDraft.validate` y la hoja → activar comparación, aplicar y comprobar el resultado visible o la ausencia del control si se retira.

- DeleteMovementDialog → con importes ocultos, comprobar que el valor real no aparece en pantalla ni en semánticas.
- MovementHistoryRoute → dejar un solo elemento visible en una página con elementos ocultos y verificar que se carga la siguiente.
- MovementHistoryRoute → filtrar fuera una operación pendiente y verificar que sigue apareciendo el aviso de reconexión.
- MovementFiltersSheetContent → elegir USD y comprobar que el resumen no muestra soles.

## Lo que no revisé

Se leyeron completos los siete archivos de implementación solicitados y los cuatro tests unitarios; se revisó qué cubre cada uno de los 17 archivos instrumentados y se inspeccionaron los previews. Los tests instrumentados no se leyeron íntegros, según el alcance reducido de esta continuación. No se ejecutaron builds ni tests, ni se accedió a servicios, conforme a las restricciones.

## Dudas

- ¿Debe mantenerse la comparación con el mes anterior y, de ser así, qué resultado visible y periodo debe mostrar?


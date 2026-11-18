# Auditoría visual de modales de Kipu · 18 de noviembre de 2026

Pixel_10 (`emulator-5554`), checkout `main` (`f2d40c6e`), variante Debug. La referencia visual es [DESIGN.md](../../DESIGN.md). Se usó la cuenta QA Premium para la cobertura general y QA Free para cupos y pantallas de plan. Los PNG de la carpeta `antes/` son capturas directas de ADB.

## Hallazgos

| ID | Módulo y pantalla | Tema y escala observados | Severidad y tipo | Archivo y línea | Problema y corrección mínima | Reproducción y captura |
| --- | --- | --- | --- | --- | --- | --- |
| VIS-01 | Movimientos, Metas, Análisis y Recurrencias · selectores de fechas | Claro 1.3 en registro rápido. Oscuro 1.3 en Meta, Análisis y Recurrencias. Android en inglés. | Baja · defecto | `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt:268`, `app/src/main/java/com/kipu/app/ui/component/KipuOptionalDateField.kt:154`, `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:723`, `app/src/main/java/com/kipu/app/feature/recurrences/presentation/RecurrenceRegistrationScreen.kt:109` | El registro rápido muestra “Select date” y “November” junto a “Cancelar” y “Listo”. Meta reproduce la mezcla con “Aceptar” y Análisis con “Rango personalizado”. Resolver el locale efectivo de Kipu en el ámbito de los selectores Material y utilizarlo también para el título/encabezado y las descripciones. | Con Android en inglés, abrir **+ → Más detalles → Fecha y hora**. También se reproduce en **Planificación → Metas → + → Fecha objetivo**, **Análisis → selector de periodo → Rango personalizado** o **Movimientos → detalle de un gasto → Registrar pago recurrente → Próxima fecha**. [Captura](antes/VIS-01.png) |
| VIS-02 | Metas · selector de icono | Oscuro 1.3 | Baja · defecto | `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalFormScreen.kt:399` | La grilla de cuatro columnas fuerza un salto dentro de “Emergencias” y deja la s sola en el renglón siguiente. Usar tres columnas cuando la escala sea 1.3 o mayor, y centrar la etiqueta dentro de la celda. | **Planificación → Metas → + → Cambiar icono de la meta**. [Captura](antes/VIS-02.png) |
| VIS-03 | Datos y privacidad · confirmación de eliminación de cuenta | Claro y oscuro 1.0. Oscuro 1.3. | Media · defecto | `app/src/main/java/com/kipu/app/feature/portability/AccountDeletionSection.kt:76` | “Eliminar definitivamente” conserva el rojo de acción habilitada con la casilla de consentimiento vacía. El `Text` fija el color y anula el estado deshabilitado del `TextButton`. Aplicar `error` con `TextButtonDefaults` al color habilitado y dejar que Material resuelva el color deshabilitado. | **Ajustes → Datos y privacidad → Eliminar cuenta → Continuar → Verificar** con credenciales de QA. Observar con la casilla vacía y salir mediante **Cancelar**. [Captura](antes/VIS-03.png) |
| VIS-04 | Categorías · pestaña Gastos seleccionada | Claro 1.0 | Media · defecto | `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:1060` | El texto blanco “Gastos” sobre `expenseCoral` presenta 3.41:1. El contador blanco sobre el coral aclarado del mismo control tiene aún menos contraste. Usar el par `expenseText`/`expenseBg` de DESIGN.md para etiqueta, icono y contador, preservando la señal de selección. | **Ajustes → Gestionar Categorías → Gastos**. [Captura](antes/VIS-04.png) |
| VIS-05 | Categorías · modal Color personalizado | Claro y oscuro 1.0. Oscuro 1.3. | Media · defecto | `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:2197` | El campo HEX oculta el final de un valor normal de siete caracteres. En 1.3 el valor inicial se ve como “#0F7…” mientras los tres campos RGB dejan espacio libre. Dar al campo HEX un ancho mínimo que incluya el texto y el icono de copia, con RGB en la fila siguiente cuando el ancho disponible sea insuficiente. | **Ajustes → Gestionar Categorías → + → Color de categoría → Selector de color personalizado**. Desplazar el contenido hasta HEX si hace falta. [Captura](antes/VIS-05.png) |
| VIS-06 | Movimientos · hojas Filtros y Detalle, componente compartido | Oscuro 1.3 | Media · defecto | `app/src/main/java/com/kipu/app/ui/component/KipuBottomSheet.kt:40` | Al quedar expandida a pantalla completa, la hoja dibuja el tirador a la misma altura que el reloj y los indicadores del sistema. Respetar el inset superior en el contenedor de la hoja expandida antes de medir/colocar el tirador. Conservar el inset inferior y el ajuste al teclado existentes. | **Movimientos → Abrir filtros** con la cuenta QA Premium. Comparar la altura del tirador con la barra de estado. También se reproduce al abrir el detalle de un gasto y al editar una fila del PDF de prueba (Estados de cuenta). [Captura](antes/VIS-06.png) |
| VIS-07 | Movimientos · hoja Filtros, fila Cuenta | Oscuro 1.3 | Baja · defecto | `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementReferenceSelector.kt:117` | La opción por defecto “Todas las cuentas” se muestra como “Todas las cuent…”. El resumen y el título compiten por el ancho y el resumen solo admite una línea. Admitir dos líneas para el resumen a escala grande y conservar espacio para la flecha. | **Movimientos → Abrir filtros → Filtros avanzados**, con cuenta QA Premium y sin selección de cuenta. [Captura](antes/VIS-07.png) |
| VIS-08 | Movimientos · hoja Registrar movimiento | Claro 1.0 | Baja · defecto | `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt:1311` | La pestaña con icono horizontal parte “Transferencia” en “Transferenc” e “ia”, mientras Gasto e Ingreso caben. Aplicar a esa fila la variante de icono sobre el texto cuando el ancho de la celda sea insuficiente para las tres etiquetas. | **Mi dinero → +** con letra estándar. [Captura](antes/VIS-08.png) |
| VIS-09 | Movimientos · confirmar anulación | Oscuro 1.3 | Media · defecto | `app/src/main/java/com/kipu/app/feature/movements/presentation/VoidMovementDialog.kt:182` | El resumen de la operación corta “Alimentación” como “Alimenta…”. En una confirmación destructiva ese texto ayuda a identificar el movimiento antes de confirmar. Permitir que el título ocupe dos líneas dentro de su columna, preservando el importe visible. | **Movimientos → abrir un gasto de Alimentación → Anular**. Si el botón queda abajo, desplazar el detalle. Salir mediante **Cancelar**. [Captura](antes/VIS-09.png) |
| VIS-10 | Estados de cuenta · resumen de cargos y abonos | Oscuro 1.3 | Baja · defecto | `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt:317` | El ancho fijo de 56 dp divide “Abonos” en “Abono” y “s”. Dimensionar la primera columna según el ancho medido de las etiquetas con la tipografía actual, conservando la alineación de los importes. | **Importar movimientos → Estado de cuenta BCP → Elegir un PDF**. Abrir el PDF sintético `app/src/test/resources/statements/savings/savings_2026_01.pdf` y mirar el resumen. [Captura](antes/VIS-10.png) |
| VIS-11 | Estados de cuenta · barra de selección masiva | Oscuro 1.3 | Media · defecto | `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt:395` | La acción “Seleccionar todo” pierde “todo” junto a “Categorizar 2”. La etiqueta de una sola línea deja ambiguo el alcance de la selección antes de un registro masivo. Permitir dos líneas en la etiqueta y usar la altura mínima del control para conservar el área táctil. | Abrir el mismo PDF sintético que en VIS-10, mantener seleccionadas las dos filas y desplazar hasta la fila de acciones. [Captura](antes/VIS-11.png) |
| VIS-12 | Comprobantes Yape · fecha y hora | Oscuro 1.0 y 1.3. Claro 1.0 para el texto inglés. | Media · defecto | `app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:500` y `:514` | Los dos selectores nativos muestran fondo blanco y acento de plataforma encima del editor oscuro. En Android en inglés también presentan “CANCEL/OK” dentro de un flujo en español. Aplicar un tema de diálogo que derive los pares de superficie y contenido de Kipu y resolver el idioma de sus controles igual que el resto del flujo. | **Importar movimientos → Comprobantes Yape → Subir una imagen → Empezar a revisar**. En el editor, tocar **Fecha** o desplazar a **Hora**. [Captura](antes/VIS-12.png) |
| VIS-13 | Presupuestos · diálogo Finalizar presupuesto | Claro y oscuro 1.0. Oscuro 1.3. | Media · defecto | `app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetComponents.kt:214`, usado en `app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetFinishDialog.kt:42` | En el resumen, el límite “S/ 75.00” se parte en tres líneas a 1.0 y cinco a 1.3. En letra grande el bloque crece hasta desplazar “Seguir con mi presupuesto” debajo de la primera vista del diálogo. Cuando ambos importes no caben en la fila consumida, colocar el límite completo debajo del consumido y dejar cada monto en una sola línea. | Crear un presupuesto mensual de S/ 75, abrir su detalle y elegir **Acciones del presupuesto → Finalizar**. [Captura](antes/VIS-13.png) |
| VIS-14 | Deudas · Cuotas y cronograma | Oscuro 1.3 | Baja · defecto | `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleScreen.kt:705` y `:720` | El título, el contador y el estado comparten una fila. “Sin amortizar” termina en cuatro líneas, con cortes dentro de la palabra. Permitir que contador y estado pasen juntos a una segunda fila cuando el ancho no alcance. | Crear una deuda de prueba sin amortizaciones. Abrir **Cuotas** y desplazar hasta **Cronograma proyectado**, con el cronograma sin amortizar. [Captura](antes/VIS-14.png) |
| VIS-15 | Widgets · Libre para gastar, tamaño inicial 4 × 2 | Claro y oscuro 1.0. Oscuro 1.3. | Media · defecto | `app/src/main/java/com/kipu/app/feature/widgets/ui/FreeToSpendWidget.kt:94` | El widget inicial dibuja “Gastado” y “Próximo pago” fuera de la altura útil. En 1.0 los rótulos se ven sin sus importes. Con 1.3 solo queda el borde de las dos tarjetas. Usar la variante ancha sin el resumen inferior a esa altura. Reservar las tarjetas completas para tamaños que puedan contenerlas. | En el launcher del Pixel_10, añadir **Widgets → Kipu → Libre para gastar** y dejar el tamaño inicial 4 × 2. [Captura](antes/VIS-15.png) |

## Cobertura

La instalación con `:app:installDebug` terminó en un solo dispositivo, Pixel_10. La configuración de Debug usa HTTPS remoto. El filtro de Logcat del proceso no registró peticiones HTTP a `localhost`, `127.0.0.1` ni `10.0.2.2`, y ambas cuentas iniciaron sesión y sincronizaron datos. Logcat no expuso los destinos de las peticiones exitosas.

Se alternaron claro y oscuro con `cmd uimode night` y se probó `font_scale 1.3`. Se anotan las combinaciones reproducidas por hallazgo. La cobertura de pantallas y estados fue parcial.

| Módulo | Revisado en el Pixel_10 | Sin verificar |
| --- | --- | --- |
| Accounts | Selector de tipo de instrumento, alta bancaria, selector de banco, detalle y cuota Free en claro, oscuro y 1.3. | Detalle de tarjeta con compras, cuotas antiguas y salvaguardas de pérdida de datos. |
| Analytics | Pantalla principal, periodo, rango personalizado, filtros y selector de cuentas. | Datos de comparación, series con varias cuentas y errores de carga. |
| Auth | Inicio de sesión, validación del formulario, aviso legal y confirmación de cierre de sesión. | Sesión sin autenticar en oscuro. La pantalla usa claro de forma explícita cuando no existe perfil. También quedó pendiente el diálogo de cuenta existente y el flujo de recuperación. |
| Budgets | Lista vacía, formulario, detalle, menú y confirmaciones de finalizar y eliminar con un presupuesto de QA. | Validación exhaustiva de límites, periodos y alertas. |
| Calendar y Home | Mi dinero y calendario cargado en claro, oscuro y 1.3, con días vacíos y con movimientos. | Eventos proyectados y vencimientos de tarjetas. `feature/home` contiene lógica de patrimonio y presenta su UI en Mi dinero. |
| Capture | Bandeja vacía con captura automática desactivada. | Revisión, fusión y conflictos de capturas bancarias. La cuenta Premium no tenía propuestas pendientes de una fuente bancaria. |
| Categories | Lista, alta, filtros y modal de color. | Confirmaciones de inactivación, cambios de padres y cupo de categorías. La cuenta Free estaba por debajo del límite. |
| Debts | Lista vacía, selector de tipo, alta de deuda de QA, detalle, hoja de pago, cronograma, fecha, ayuda, ajustes y cierre contable, y diálogo de historial protegido. | Confirmaciones de reprogramación, liquidación y anulación de un pago ya registrado. La deuda de prueba quedó protegida por historial y no se registraron pagos. |
| Goals | Lista vacía, alta, iconos, cuenta de respaldo, fecha objetivo, menú y confirmaciones de meta lograda sin aportes y borrado de la meta de QA. | Conflictos de sincronización y flujo con aportes registrados. |
| Movements | Registro rápido, historial, filtros, detalle y confirmación de anulación. | Anulación masiva, devolución, enlace y algunos estados de error. |
| Notifications | Centro de notificaciones vacío. | Confirmación de eliminación de avisos leídos. No había avisos en la cuenta Premium. |
| Plans | Hoja de plan, selección de plan, cupo de instrumentos Free y acceso Premium desde filtros y estados de cuenta. | Cupos de metas, deudas, categorías y presupuestos con listas saturadas. |
| Portability | Pantalla de datos, resumen del borrado, reautenticación y confirmación deshabilitada en los estados de fuente y tema anotados. | Operaciones reales de exportación, restauración y borrado de cuenta. |
| Receipts | Bandeja, guía, consentimiento OCR, revisión de una imagen sintética, selectores nativos de fecha y hora y descarte de la muestra. | Registro final, vista posterior al guardado y confirmación por gesto desde la bandeja. |
| Recurrences | Alta desde un gasto, menú de frecuencia, fecha y lista vacía. | Pausa, reanudación, cancelación y confirmación de una regla con ocurrencias. |
| Settings | Preferencias, permisos, explicación de captura bancaria y estado de bloqueo local sin credencial de Android configurada. | Autenticación biométrica en un dispositivo con credencial. |
| Setup | Rutas y composables localizados en código. | Primer inicio y OTP. Las cuentas QA ya estaban activadas. |
| Statements | Aviso de privacidad, lectura de PDF BCP de pruebas, revisión con dos filas en los tres ajustes, editor, selector de categorías y confirmación de descarte. | PDF cifrado, asociación con tarjeta y estado posterior al registro. |
| Widgets | Selector de Android con los diez widgets de Kipu y render de Libre para gastar anclado en los tres ajustes. | Render y acciones de los otros nueve widgets y tamaños alternativos. |
| Componentes compartidos | Hojas, campos, confirmaciones, pickers y estados vacíos alcanzados desde los módulos anteriores. | Variantes que solo aparecen en los estados pendientes. |

El emulador se cerró durante la auditoría y se reanudó en el mismo AVD. Al terminar se verificaron la fuente en 1.0 y el modo nocturno original (`no`). Se restablecieron las preferencias de tema de las cuentas de QA y el consentimiento OCR inicial. Se descartaron la muestra OCR y el borrador PDF, se retiraron los archivos de prueba del emulador y se archivó la cuenta `QA_UX_CUPO`. Se retiró del launcher el widget de prueba. La deuda `QA_UX_DEUDA` quedó activa en QA Premium por la protección de historial.

## Lotes de corrección

- **Componentes compartidos** · VIS-01, VIS-06 y VIS-08
  - `app/src/main/java/com/kipu/app/ui/component/KipuBottomSheet.kt`
  - `app/src/main/java/com/kipu/app/ui/component/KipuOptionalDateField.kt`
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`
  - `app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt`
  - `app/src/main/java/com/kipu/app/feature/recurrences/presentation/RecurrenceRegistrationScreen.kt`
- **Metas** · VIS-02
  - `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalFormScreen.kt`
- **Datos y privacidad** · VIS-03
  - `app/src/main/java/com/kipu/app/feature/portability/AccountDeletionSection.kt`
- **Categorías y selector de color** · VIS-04 y VIS-05
  - `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt`
- **Filtros de movimientos** · VIS-07
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementReferenceSelector.kt`

- **Anulación de movimientos** · VIS-09
  - `app/src/main/java/com/kipu/app/feature/movements/presentation/VoidMovementDialog.kt`

- **Estados de cuenta** · VIS-10 y VIS-11
  - `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt`

- **Comprobantes Yape** · VIS-12
  - `app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt`

- **Presupuestos** · VIS-13
  - `app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetComponents.kt`
  - `app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetFinishDialog.kt`

- **Deudas** · VIS-14
  - `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleScreen.kt`

- **Widgets** · VIS-15
  - `app/src/main/java/com/kipu/app/feature/widgets/ui/FreeToSpendWidget.kt`

# Movimientos: eliminar del historial y editar desde comprobantes — 2026-11-04

## Alcance y autoridad

Corrección solicitada por el propietario, realizada en `main` y en el checkout compartido, sin commits, push, worktrees, migraciones ni acceso remoto. Se consultaron `docs/README.md`, control documental, constitución, EP-MOV y su contrato de corrección/anulación; no se ejecutó workflow Spec Kit por instrucción expresa. Se aplicaron las skills Android Kotlin, UI/UX y animaciones Compose.

Se preservan los cambios previos de recurrencias descritos en [su informe](recurrence-ui-2026-11-04.md), incluido el contenedor de recurrencias en `MovementHistoryScreen`. El archivo ajeno `tesis.md` queda intacto. No se instaló release ni se modificó `com.kipu.app`.

## Semántica exacta de «Eliminar»

- Deslizar una fila a la izquierda revela icono/acción **Eliminar** y solicita confirmación. Usa `SwipeToDismissBox` con el mismo patrón de comprobantes: el gesto no descarta la fila por sí solo. Las filas tienen claves estables y `animateItem`; cancelar conserva el movimiento y no ejecuta acciones financieras. Existe una acción de accesibilidad equivalente.
- La confirmación muestra comercio/concepto e importe; utiliza superficies, iconografía, botones de 56 dp y tokens de la confirmación de comprobantes. El contenido puede desplazarse. Explica la consecuencia para el saldo y el alcance local de la ocultación.
- Para un movimiento activo estándar (`ACTIVE`, `CONFIRMED` o `REVISED`), se consulta el head actual y se llama al `VoidTransaction` existente. Se conserva la contabilidad original y se agregan las contrapartidas mediante el repositorio y outbox existentes. **No se elimina físicamente ninguna transacción ni asiento.**
- Para un movimiento ya `VOIDED`, incluido el caso MINISO solicitado, se verifica su head y únicamente se guarda su ocultación: no se llama de nuevo a `VoidTransaction` ni se compensa nuevamente el saldo.
- Los activos especializados —tarjetas, cuotas, apertura/ajuste/reversión, reembolso, liquidación de deuda y otras operaciones especiales— no ofrecen este gesto genérico. Conservan sus acciones de dominio en detalle/origen. Una fila especializada ya anulada sí puede ocultarse, porque no precisa nueva reversión. `FAILED` tampoco ofrece eliminación genérica. El repositorio sigue validando cualquier relación especializada no representada por la fila.
- La confirmación bloquea doble envío y cancelación mientras trabaja. Conflicto, rechazo o excepción conserva el diálogo y ofrece reintentar/cancelar; no se oculta ante un fallo de anulación. La clave del comando se conserva en reintentos de resultado incierto; un conflicto permite revalidar con una nueva acción. Si la anulación fue durable pero falla guardar la ocultación, el próximo intento lee `VOIDED` y solo intenta ocultar.
- La lectura vuelve a invalidarse tras ocultar, incluso si la fila mostraba un snapshot anterior al head. Las filas ocultas se filtran antes de agrupar por fecha. Se conservan cursores y paginación; si una página completa queda oculta, se continúa hasta obtener filas visibles o llegar al final. Si el dato financiero vuelve a estado activo tras restauración/rechazo, reaparece para revisión.

## Persistencia y sincronización

`MovementDisplayPreferences` guarda un conjunto de IDs en SharedPreferences `movement_display`, separado por propietario, con `commit()` en IO y comprobación de éxito, siguiendo el patrón local de `GoalDisplayPreferences`. La preferencia se mantiene al reconstruir la instancia/proceso y al volver a consultar/sincronizar filas anuladas en el mismo almacenamiento de la app.

La ocultación es **solo para el historial de este dispositivo y usuario**: no se agrega a sync, no oculta el registro asociado en la bandeja de comprobantes, no cambia informes ni totales contables, y no promete sobrevivir a borrar datos/reinstalar o cambiar de dispositivo. La anulación financiera conserva la sincronización/outbox, idempotencia, conflictos y reintentos ya existentes. Ocultar no equivale a confirmar el resultado remoto de la anulación. No se modificaron contratos ni esquema remoto.

## Causa del fondo blanco y corrección

`ReceiptsNavigation` llamaba a `navigateToMovementEditor`, cuyo destino `composable` tiene únicamente `MovementEditorRoute` (un `ModalBottomSheet`). Al sustituir el destino visible, la hoja carecía de la pantalla de comprobantes debajo: aparecía el fondo vacío del destino.

Ahora `ReceiptMovementEditorHost` mantiene la pantalla real de comprobantes compuesta y abre la misma `MovementEditorRoute` encima, dentro del destino de comprobantes. El ID abierto se conserva con `rememberSaveable`, el ViewModel se identifica por movimiento y cerrar/guardar limpia la selección sin introducir otra entrada en el back stack. Se preservan bandeja, selección, estado y regreso. No se añadió un color de relleno. Las otras entradas al editor conservan su navegación previa.

## Validación focalizada

| Comando | Resultado |
| --- | --- |
| `gradlew.bat :app:compileLabKotlin -PisolatedAndroidTests=true --console=plain` | Exit 0, `BUILD SUCCESSFUL`. |
| `gradlew.bat :app:testLabUnitTest --tests *MovementHistoryDeleteTest --tests *MovementHistoryViewModelTest --tests *VoidTransactionTest :app:assembleLab -PisolatedAndroidTests=true --console=plain` | Exit 0, `BUILD SUCCESSFUL`; **39 pruebas**, 0 fallos/errores: 8 eliminación, 24 historial y 7 dominio/anulación. Log local: `app/build/movement-swipe-final-verification.log`. |
| `gradlew.bat :app:assembleLabAndroidTest -PisolatedAndroidTests=true --console=plain` | Exit 0, `BUILD SUCCESSFUL`. Log local: `app/build/movement-swipe-test-build.log`. |
| SDK `platform-tools/adb.exe install -r app/build/outputs/apk/lab/app-lab.apk` y correspondiente APK `androidTest/lab/app-lab-androidTest.apk` | Exit 0, `Success`; exclusivamente `com.kipu.app.lab` y su runner. |
| SDK `platform-tools/adb.exe shell am instrument -w -r -e class com.kipu.app.feature.movements.presentation.MovementSwipeReceiptsTest com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner` | Exit 0, **OK (7 tests)**. Log local: `app/build/movement-swipe-final-instrument.log`. |
| `git diff --check` | Exit 0. |

Se usó el teléfono conectado con backend vacío de lab y permisos autoconcedidos en setup por `uiAutomation`. El `adb` antiguo de `C:/Windows` era incompatible con el servidor; se usó el binario del SDK. Los primeros intentos de captura movían la fila más allá del umbral y esperaban conservar una hoja que el arrastre ya había cerrado; se corrigieron las expectativas/gestos de prueba. Las capturas esperan también la animación de ventana, que no depende del reloj Compose. La ejecución final es verde; `INSTRUMENTATION_CODE: -1` es la finalización normal del runner, no un fallo de shell.

Cobertura: confirmación explícita, una sola anulación pese a doble envío, anulado especializado sin anular otra vez, cancelación sin escrituras, error de ocultación después del commit y retry, clave estable ante fallo incierto, rechazo/conflicto sin ocultación, elegibilidad/propietario, filtrado persistente tras reconsulta y restauración a activo, gesto parcial cancelado, scroll vertical, especial activo sin swipe, error y botones ocupados, reconstrucción de preferencias y aislamiento de usuario. La prueba de retorno usa `ReceiptMovementEditorHost`, `ReceiptReviewScreen` y `MovementEditorSheet` reales con fixture controlado; comprueba que el contenido no se desmonta al abrir, retorno por arrastre/cerrar y callback de guardar.

No se afirma una edición remota end-to-end: en esa prueba el callback de guardar está controlado, y los ViewModels/repositorios financieros se prueban aparte. La reconstrucción de preferencias se verifica con una nueva instancia sobre el almacenamiento real, no mediante reinicio forzado del teléfono. Fuente máxima, horizontal y TalkBack no tienen ejecución específica. No se repitió recurrencia ni la suite global; no se ejecutó pgTAP porque no hubo cambio de base de datos.

## Capturas locales revisadas

Fixtures sintéticos de MINISO/S/15, sin datos financieros del usuario. Permanecen ignorados dentro de `app/build/`; no se agregan imágenes al repositorio. Se revisaron visualmente estas cinco capturas de la ejecución final:

| Archivo local | Evidencia |
| --- | --- |
| `app/build/movement-swipe-evidence/movement-swipe/swipe-action-light.png` | Fila desplazada y acción roja Eliminar visible. |
| `app/build/movement-swipe-evidence/movement-swipe/voided-confirm-light.png` | Confirmación clara de anulado, importe y explicación de no cambiar de nuevo el saldo. |
| `app/build/movement-swipe-evidence/movement-swipe/active-retry-dark.png` | Confirmación oscura, consecuencia de anular, error y reintento. |
| `app/build/movement-swipe-evidence/movement-swipe/receipts-editor-background.png` | Hoja real arrastrada; bandeja real de comprobantes visible debajo. |
| `app/build/movement-swipe-evidence/movement-swipe/receipts-return.png` | Bandeja con el mismo comprobante tras cerrar la hoja. |

## Archivos y entrega

Cambios propios: `MovementDisplayPreferences.kt`, `MovementHistoryDelete.kt`, `MovementHistoryScreen.kt`, `MovementHistoryViewModel.kt`, `ReceiptsNavigation.kt`, `MovementHistoryDeleteTest.kt`, `MovementHistoryViewModelTest.kt`, `MovementSwipeReceiptsTest.kt` y este informe. Los otros cambios presentes pertenecen al trabajo previo de recurrencias y permanecen intactos.

Queda al coordinador revisar los cambios y actualizar la release con la misma firma, conservando instalación y datos. No se realizó esa instalación desde este worker.

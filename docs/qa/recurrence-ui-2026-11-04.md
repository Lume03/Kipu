# Recurrencias: formulario y avisos - 2026-11-04

## Alcance y autoridad

Corrección solicitada por el propietario para HU-33/HU-44, implementada en el checkout actual de `main`, sin commits, push, worktrees, cambios Spec Kit ni migraciones. Referencias consultadas: `docs/README.md`, `docs/control-documental.md`, constitución y spec/plan/tasks de EP-REC; se conserva el contrato de calendario y anticipación existente. `tesis.md` no versionado queda intacto. No se modificaron swipe del historial ni fondos de comprobantes.

## Causa y cambios

- El registro del historial estaba dentro de un `Dialog` estrecho, con fecha editable como texto y cinco chips de frecuencia. Ahora abre un `ModalBottomSheet` con las propiedades y superficies existentes de Kipu, contenido desplazable y espacio para teclado.
- Concepto conserva su edición y muestra `MerchantAvatar`: logo integrado o remoto claro/oscuro del comercio, con iniciales como fallback. El nombre del comercio permanece visible aunque se edite el concepto.
- Fecha usa `DatePickerDialog`/`DatePicker`, inicializada al día actual de Lima; la selección UTC del calendario se convierte a `LocalDate` sin restarle un día por zona horaria.
- Frecuencia se elige en un menú ordenado: Semanal, Quincenal, Mensual, Anual, Personalizada. Quincenal se explica como **cada 2 semanas (14 días)**; el valor `BIWEEKLY` y el calculador no cambian. Se conservan semanas/meses personalizados y sus límites de 5200/1200, fijo/aproximado y anticipación 0–30, default 2. Botones usan tokens Calm Emerald; no se agregan colores celestes.
- Más detalles del formulario de crear gasto incluye **Gasto recurrente - Configurar después de guardar el gasto**. Tras un guardado exitoso, la misma hoja muestra el formulario compartido; no exige entrar al detalle. Confirmar la recurrencia sigue siendo una acción explícita: cerrar esa hoja conserva el gasto y no crea una regla.
- La transición recibe el movimiento efectivamente guardado; fallos/validaciones/advertencias de duplicado no emiten ese traspaso. El opt-in se conserva con el borrador y se limpia al guardar. Ingresos/transferencias no crean recurrencia. Para compras con tarjeta se reutiliza la operación confirmada como clave de presentación; esa clave no se persiste como un segundo movimiento ni se incluye en el contrato de recurrencia.
- Se difiere `onSaved` del contenedor hasta cerrar la configuración, porque Dashboard y comprobantes pueden cerrar la hoja al recibirlo. Ninguna regla se crea desde recomposición o `LaunchedEffect`: solo desde Guardar y con la protección `isSaving`/`savedRule` del ViewModel.
- Había un fallo real después del commit local de CREATE: una excepción en reconciliar recordatorios/generar esperados podía reportar que crear la regla falló, aunque la regla y su comando ya existieran. Ahora devuelve el resultado durable pendiente y deja la reconciliación para la sincronización. La transacción Room regla+outbox y sus IDs permanecen intactos; un fallo de escritura sigue haciendo rollback.

## Cadena real de avisos

1. `RecurrencePresentationViewModel.create` llama al repositorio offline. Regla y comando se guardan juntos. Una regla `PENDING`, `CONFLICT` o `FAILED` no activa avisos ordinarios.
2. Al aplicar el recibo de sincronización o hidratar cambios, `OfflineFirstRecurrenceRepository.reconcileReminder` vuelve a consultar la regla. Solo `ACTIVE` + `SYNCED`, no borrada y con anticipación 1–30, llama a `RecurrenceReminderScheduler.schedule`; las otras condiciones cancelan trabajo/aviso y reconcilian eventos locales.
3. `WorkManagerRecurrenceReminderScheduler` calcula `dueDate - leadDays` a las 00:00 de **America/Lima**. Usa un `OneTimeWorkRequest` único por usuario/regla con `REPLACE`. Un instante pasado genera demora cero. **0 desactiva** y cancela: no significa avisar el mismo día.
4. `RecurrenceReminderWorker` revalida propietario de sesión, estado, sincronización, fecha e intervalo actuales y omite esperados `ATTENDED`. Persiste el evento del centro local antes de intentar la notificación del sistema.
5. El permiso lo verifica `DebtNotificationPermissionChecker` (permiso Android 13+ y notificaciones globales). Sin permiso, queda el evento local; eso no demuestra publicación del sistema. El canal puede estar bloqueado también. La UI ahora muestra ese estado y ofrece solicitar permiso o abrir Ajustes de notificaciones; tras una denegación ofrece Ajustes en el siguiente intento. Reconsulta al volver a primer plano.
6. Con permiso, el worker publica en el canal de recurrencias usando tag estable usuario/regla. La prueba física confirmó un aviso activo con la fecha esperada tras dos invocaciones del worker. Esto prueba publicación y sustitución por tag; no certifica que el usuario lo haya visto/oído, ni una entrega a una hora exacta.

**Límites:** WorkManager permite demoras por ahorro de batería/Doze; no es una alarma exacta. La prueba del worker usa DAOs/sesión controlados y notificador Android real. La prueba del scheduler inspecciona trabajo real en WorkManager de pruebas. No se ejecutó un recorrido completo gasto remoto real - sincronización hosted - espera del reloj - notificación; no se afirma despliegue ni entrega end-to-end para datos del usuario. Sin conexión, la regla queda en cola y los avisos esperan confirmación de sincronización según el contrato existente.

## Validación focalizada

Se usó el teléfono Android conectado mediante la variante **`com.kipu.app.lab`**, sin backend configurado. No se desinstaló ni actualizó `com.kipu.app`, ni se borraron sus datos. Los tests que publican avisos se autoconceden `POST_NOTIFICATIONS` en setup mediante `uiAutomation`.

| Comando | Resultado |
| --- | --- |
| `gradlew.bat :app:compileDebugKotlin --console=plain` | `BUILD SUCCESSFUL`; compilación inicial. |
| `gradlew.bat :app:testDebugUnitTest --tests '*Recurrence*Test' --tests '*QuickMovementViewModelTest' --console=plain` | 86 pruebas, 0 fallos/errores/omitidas; `BUILD SUCCESSFUL`. El wrapper PowerShell inicial devolvió 1 por stderr del aviso JVM, no por fallos JUnit. |
| `gradlew.bat :app:testLabUnitTest --tests '*Recurrence*Test' --tests '*QuickMovementViewModelTest' :app:assembleLabAndroidTest -PisolatedAndroidTests=true --console=plain` | Exit 0, `BUILD SUCCESSFUL`; 87 pruebas, 0 fallos/errores/omitidas; incluye compra con tarjeta y fallo auxiliar posterior al commit local. |
| `adb install -r app/build/outputs/apk/lab/app-lab.apk` y `adb install -r app/build/outputs/apk/androidTest/lab/app-lab-androidTest.apk` | Exit 0, `Success`, exclusivamente paquetes lab. |
| `adb shell am instrument -w -r -e class com.kipu.app.feature.recurrences.RecurrenceRegistrationFlowTest com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner` | Exit 0; `OK (7 tests)`. Calendario real, fijo/aproximado, 14 días explícitos, límites personalizados/avisos, apertura desde detalle, tema oscuro. |
| Mismo runner con `com.kipu.app.feature.movements.presentation.QuickMovementRecurrenceOptionTest` | Exit 0; `OK (2 tests)`. Opt-in en Más detalles claro/oscuro, sin guardar al alternarlo y ausente en ingresos. |
| Mismo runner con `com.kipu.app.feature.recurrences.data.RecurrenceReminderSchedulerTest` | Exit 0; `OK (4 tests)`. 0 cancela, replanificación, aislamiento de owner, metadatos de avisos. |
| Mismo runner con `com.kipu.app.feature.recurrences.data.RecurrenceReminderWorkerTest` | Exit 0; `OK (5 tests)`. Notificación Android real con permiso y un solo aviso tras retry; denegado conserva evento; pausa/cancelación/fecha antigua/owner/atendido suprimen. |
| `git diff --check` | Exit 0. |

El runner Android comunica `INSTRUMENTATION_CODE: -1` al finalizar; se evalúan `OK (N tests)` y estados individuales, no ese valor como exit de shell. Los primeros intentos del calendario fallaron por el selector de prueba (el día expone la fecha completa localizada como texto accesible); se corrigió contra la semántica observada y la clase final pasó. Un intento de seleccionar `testDebugUnitTest` junto con `isolatedAndroidTests=true` falló en configuración: esa opción habilita exclusivamente lab; se sustituyó por `testLabUnitTest`.

Se inspeccionaron imágenes de fixtures en claro/oscuro dentro de `app/build/` (sin datos privados); no se adjuntan al repositorio por defecto. No se hizo suite global, SQL/pgTAP, migración, acceso a bases reales ni instalación de release. Orientación horizontal, fuente máxima y cierre/recreación de proceso durante el paso entre gasto guardado y configuración quedan sin evidencia específica; al recrearse ese paso la regla no se genera automáticamente.

## Archivos

- `app/src/main/java/com/kipu/app/feature/recurrences/presentation/RecurrenceRegistrationScreen.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/presentation/RecurrencePresentationViewModel.kt`
- `app/src/main/java/com/kipu/app/feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt` (solo contenedor del registro recurrente)
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`
- `app/src/test/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModelTest.kt`
- `app/src/test/java/com/kipu/app/feature/recurrences/data/RecurrenceSyncTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceRegistrationFlowTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/recurrences/data/RecurrenceReminderWorkerTest.kt`
- `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/QuickMovementRecurrenceOptionTest.kt`
- Este informe.

Queda al coordinador la revisión final y la actualización con release firmada, conservando instalación y datos del teléfono.

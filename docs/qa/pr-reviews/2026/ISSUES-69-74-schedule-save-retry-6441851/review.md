# Revisión local de issues #69/#74 — guardado de cronogramas

- **PR:** No aplica — revisión local de issues (no creado)
- **Repositorio:** Lume03/Kipu
- **Autor / asignado:** android-ui
- **Fecha de revisión:** 2026-10-20
- **Rama:** `013-ep-deu-deudas-prestamos` → `origin/main`
- **Commit revisado:** `6441851db93b0c23655283a2c4a257845592875f`
- **Estado del PR al revisar:** No aplica — no existe PR
- **Estado del diff:** Código/tests y reviews locales sin commit; `git diff --check` pasa. El diff de producto incluye tres archivos tracked y el test unitario nuevo; hay informes QA locales adicionales. El HEAD sigue en `6441851`; el diff no está incluido en ese commit.

## Dictamen breve

**Resultado actualizado (2026-10-20):** #69 PASS acotado a código y 2/2 unit tests; la concurrencia observada históricamente en el APK sigue sin reproducirse en runtime. #74 PASS acotado en lab (1/1) y en el candidato integrado exacto: cancelación sin reemplazo, confirmación explícita, cuatro cuotas proyectadas y feedback visible cerca del CTA. TalkBack, persistencia Room/ledger e historial CANCELLED no se verificaron. El selector posicional `[1]` del test lab sigue siendo una sugerencia de robustez. No es aprobación de release.

El bloque #69 terminó primero: los dos tests unitarios focalizados pasaron y cubren callbacks solapados, liberación ante excepción y retry; no se confirma la secuencia histórica del APK. Después, #74 falló inicialmente porque el texto de título y botón era duplicado. En el retest, el selector posicional eligió el botón de confirmación; se verificaron `saveCalls=0` tras cancelar y `saveCalls=1` tras confirmar. BUG-074-001 queda resuelto como bloqueo de ejecución del test, no como bug productivo. La fragilidad posicional se conserva como observación. La ronda manual posterior confirmó feedback visual y reemplazo en el candidato integrado, pero no persistencia Room/ledger, TalkBack ni la secuencia histórica del APK.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#74](https://github.com/Lume03/Kipu/issues/74) | Confirmado por el encargo de auditoría | Feedback inequívoco y próximo al CTA; confirmación antes de reemplazar cuotas pendientes/parciales e información de historial CANCELLED; evitar efectos financieros adicionales al guardar. |
| [#69](https://github.com/Lume03/Kipu/issues/69) | Confirmado por el encargo de auditoría | Rechazar callbacks concurrentes sin mutación adicional y aceptar nuevos guardados después de completar. |

- **Issues no encontrados o relación incierta:** Ninguno en el alcance revisado.
- **Fuente de autoridad usada:** Cuerpos vigentes de GitHub #69/#74 y criterios de la tarjeta Kanban. Los artefactos Spec Kit no fueron modificados y no se ejecutó su workflow.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| #69 / criterio 1: callback concurrente no duplica mutaciones; bloqueo sin ventana y liberación ante error | Cumple en código y test unitario | `DebtScheduleViewModel.kt:99-100,122-138` establece el `AtomicBoolean.compareAndSet` sin suspender, pone `isSaving` antes del lanzamiento y libera en `finally`. `DebtScheduleViewModelTest.kt:92-130` mantiene la primera llamada pendiente, verifica una sola mutación, induce excepción y valida liberación/retry exitoso. |
| #74 / criterio 2: feedback próximo/accesible y sin asegurar sync remoto no probado | Cumple con límites | `DebtScheduleScreen.kt:720-739` representa feedback cercano al CTA y usa `liveRegion = Polite`; la verificación manual del candidato integrado observó el texto de guardado local bajo el CTA. No se verificó anuncio por TalkBack ni persistencia Room/ledger. |
| #74 / criterio 3: confirmar reemplazo y explicar el efecto sobre cuotas | Cumple en lab/manual con límites | `DebtScheduleScreen.kt:234-275` solicita confirmación para `PENDING`/`PARTIAL` y explica historial cancelado, saldo intacto y ausencia de pagos. El retest lab comprobó cancelar sin guardar y confirmar con un callback; el candidato integrado mostró la sustitución de 3 por 4 cuotas y una proyección sin duplicación activa. Room/ledger e historial CANCELLED no se inspeccionaron. |
| #74 / repetir plan idéntico sin revisión innecesaria o confirmación explícita | Cumple por confirmación explícita, sin verificación de revisión persistida | En la prueba manual el plan idéntico volvió a mostrar la confirmación; `Conservar actual` cerró el diálogo y mantuvo la proyección de cuatro cuotas. No se inspeccionó Room/historial para afirmar que no se creó una revisión. |
| #69/#74 / criterio 4: no crear pagos ni cambiar principal; cubrir no duplicación, retry y error recuperable | Cumple en cobertura focalizada y revisión estática | `DebtScheduleViewModelTest.kt:50-130` verifica callbacks solapados, guardados posteriores, resultados Retryable/Applied y excepción recuperable con retry exitoso. La inspección estática de `DebtLocalDataSource.kt:516-545` muestra actualización de deuda sin modificar `remainingPrincipalMinor`; no se inserta un movimiento en el camino `setDebtSchedule`. La prueba unitaria usa repositorio mock; no prueba persistencia completa. |
| Tarjeta, criterio 5: separar estática, tests y runtime pendiente | Cumple | Este informe diferencia inspección de código, ejecución de tests y limitaciones de runtime. No se atribuye causa confirmada al APK. |
| Tarjeta, criterio 6: pruebas focalizadas, sin suite global legacy | Cumple para checks ejecutados | La prueba unitaria filtrada de #69 pasó 2/2; el test instrumentado de #74 pasó 1/1 en lab en el retest. No se ejecutó la suite global de 413 tests. |
| Tarjeta, criterio 7: alcance, resumen, riesgos y sin acciones GitHub | Cumple | Sin commits, push, PR, merge o acciones formales GitHub. |

## Bloque serial de verificación — Issue #69

- **Criterio propio:** distinguir callbacks concurrentes rechazados de invocaciones nuevas después de completar, y liberar el exclusor ante resultados y excepción recuperable.
- **Evidencia de código:** `DebtScheduleViewModel.kt:98-100,122-139` toma el gate con `compareAndSet` antes de crear la coroutine, y lo libera en validación inválida o en `finally`; cada comando aceptado genera un `operationId` nuevo (`:107-116`). No se atribuye una causa a la secuencia histórica del APK.
- **Prueba ejecutada antes de pasar al bloque #74:** `./gradlew :app:testDebugUnitTest --tests com.kipu.app.feature.debts.presentation.DebtScheduleViewModelTest --rerun-tasks` — `BUILD SUCCESSFUL`; 2 tests, 0 failures/errors/skips. El primero mantiene la llamada pendiente, ignora la segunda invocación y acepta llamadas posteriores; el segundo verifica exclusión durante excepción y retry exitoso.
- **Resultado del bloque #69:** PASS acotado a código y unit tests del ViewModel. No prueba runtime de APK, backend, persistencia ni secuencia real de taps.
- **Secuencia histórica de tests:** el bloque unitario #69 se cerró antes del retest lab #74; la evidencia por issue se conserva separada.

### Addendum de ejecución serial en candidato integrado — Issue #69 (2026-10-20)

- Tras el update autorizado al candidato exacto (`38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce`), QA inspeccionó el flujo de cronograma del fixture QA `QA_AUDIT_20261020_CRON` sin guardar cambios. El fixture ya tenía cuotas pendientes; al variar el borrador apareció el diálogo de reemplazo de #74. Se eligió `Conservar actual`, se salió del editor y no se despachó un guardado. El fixture no se alteró en esta inspección.
- **No cuenta como runtime PASS de #69:** el flujo UI no permitió aislar un cronograma sin reemplazo, y no se controló latencia del repositorio ni se instrumentaron timestamps/callbacks. No es seguro ni válido inferir concurrencia/retry a partir de taps sobre la confirmación o de historial visual. No se forzó red, worker, retry ni comandos.
- **Dictamen propio #69:** PASS acotado en JVM (2/2) y revisión estática previamente registrada; **manual/runtime de callbacks solapados, exclusión, excepción/retry y persistencia queda NOT TESTED**. La evidencia unitaria mockeada no se presenta como evidencia de dispositivo o backend.
- **Impedimento concreto para cerrar la aceptación manual #69:** en el UI normal solo se observó el diálogo de reemplazo antes del dispatch; no hay una forma observable de mantener la llamada de repositorio en vuelo o provocar una excepción recuperable sin controlar latencia, forzar red/worker/retry o añadir instrumentación de source/build. Esas acciones exceden el alcance y las restricciones vigentes. Taps rápidos sin un callback bloqueado no demuestran concurrencia ni su ausencia. Se requiere decisión de Tech Lead/Owner: aceptar el PASS unitario acotado para este criterio, o autorizar/entregar un test harness/candidato aislado con demora/error controlados para una validación de dispositivo; hasta entonces, no reportar runtime PASS ni aprobación completa.

## Bloque serial de verificación — Issue #74

- **Preflight lab:** antes de instalación, AAPT verificó APK app `com.kipu.app.lab` v0.1.0/versionCode 1 y runner `com.kipu.app.lab.test`; BuildConfig lab reportó `GOALS_REMOTE_COMMANDS_ENABLED=false` y `SUPABASE_URL=""`. La variante lab no estaba instalada. Se probó exclusivamente el estado sintético de Compose; no se abrió la app personal ni se usaron datos/remoto.
- **Comando:** `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest#replacingScheduleRequiresExplicitConfirmationAndExplainsHistoryEffect' :app:connectedLabAndroidTest` — exit 1; 1 test, 1 failure, 0 errors/skips.
- **Observado:** en el mismo método, asserts de texto de reemplazo visibles, `saveCalls=0` antes y después de “Conservar actual” pasaron; al buscar el texto “Reemplazar cronograma” para confirmar, Compose encontró 2 nodos: título y botón. Por ello no se llegó a pulsar confirmación ni a verificar `saveCalls=1`.
- **Evidencia:** [QA-74-replacement-lab.xml](evidence/QA-74-replacement-lab.xml), SHA-256 `d7b9d441ce35e40dbe6013472df753e5112d35b407540c0e9d0b33da2a074fff`; se redactó el serial del XML. No se adjunta logcat bruto por ruido/privacidad.
- **Build:** `:app:assembleLab :app:assembleLabAndroidTest` — BUILD SUCCESSFUL. APK lab SHA-256 `059f4e5fe54ee33f2e925dba8ab26cf181a46be42e86ef44a5cd09c1c937312c`; APK test SHA-256 `823ed0d1248084364f67b98a80cb961c71f59d1d5db37df3817191ab4fd2cb56`.
- **Estado tras prueba:** `com.kipu.app.lab` no quedó instalado; el paquete personal permaneció en versionCode 1, SHA-256 `e218941857b805335e932ac78b6412ef31e316607a0775ca2824db4eda8c2864`. No hubo clear-data, cambio de cuenta ni alteración de registros.
- **Resultado #74 histórico:** BUG-074-001 bloqueó inicialmente la comprobación del callback. El retest posterior queda documentado abajo; no hubo evidencia de fallo productivo.

## Cambios revisados

- `DebtScheduleViewModel.kt`: protección atómica contra guardados solapados, resultado cercano en estado UI y recuperación tras excepción.
- `DebtScheduleScreen.kt`: confirmación inequívoca para reemplazar cronograma y feedback accesible con live region.
- `DebtScheduleViewModelTest.kt`: cobertura focalizada de callback simultáneo, resultados previos y excepción recuperable seguida de retry exitoso.
- `DebtScheduleAndClosureFlowTest.kt`: caso Compose de confirmar/descartar reemplazo; el retest lab focalizado pasó 1/1 con cancelar sin callback y confirmación con una llamada.

## Hallazgos y mejoras

### Bloqueantes / importantes

- **BUG-074-001 — MEDIUM, confirmado históricamente, test-only; RESUELTO EN RETEST:** la ejecución inicial de `onNodeWithText("Reemplazar cronograma")` encontró título y botón y no alcanzó el callback. El selector corregido usa `onAllNodesWithText(...)[1]`. El retest ejecutó cancelar con cero callbacks y confirmar con exactamente uno. No se halló un defecto productivo. Observación residual: `[1]` depende del orden de nodos; un selector semántico tipado o testTag sería más robusto, aunque no bloquea el resultado runtime observado.
- El hallazgo previo `QA-ISSUES-69-74-001` queda cerrado por el nuevo test de excepción y la ejecución focalizada 2/2 PASS. La falla actual no reabre ese hallazgo de #69.

### Menores / sugerencias

- Ninguna adicional identificada.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `./gradlew :app:testDebugUnitTest --tests com.kipu.app.feature.debts.presentation.DebtScheduleViewModelTest --rerun-tasks` | Pasa | Reejecutado por este operador: `BUILD SUCCESSFUL`, 33 tareas ejecutadas; XML `TEST-com.kipu.app.feature.debts.presentation.DebtScheduleViewModelTest.xml`: 2 tests, 0 failures, 0 errors, 0 skipped. No se ejecutó la suite global. |
| `./gradlew :app:compileDebugAndroidTestKotlin` | Pasa | Ejecutado en esta revisión: `BUILD SUCCESSFUL`; tarea compilada/UP-TO-DATE. Solo compilación, no ejecución instrumentada. |
| `git diff --check` | Pasa | Ejecutado en esta revisión; sin errores de whitespace. |
| Test instrumentado `replacingScheduleRequiresExplicitConfirmationAndExplainsHistoryEffect` en lab (ejecución inicial) | Falla histórica | 1 test, 1 failure, 0 errors/skips por selector ambiguo; XML previo preservado en [QA-74-replacement-lab.xml](evidence/QA-74-replacement-lab.xml). |
| Test instrumentado `replacingScheduleRequiresExplicitConfirmationAndExplainsHistoryEffect` en lab (retest) | Pasa | 1 test, 0 failures/errors/skips; comprobó cancelación sin dispatch y confirmación con dispatch una vez. Evidencia nueva abajo. |
| Revisión visual / TalkBack | No ejecutada | Sin screenshot manual ni inspección TalkBack. El APK personal instalado es baseline, no se verificó como candidato; no se reemplazó sin autorización adicional. |
| Reproducción del APK observado | No ejecutada | No se confirma la secuencia ni la causa runtime del comportamiento reportado. |

## Revisión visual (solo si aplica)

- **¿Hay cambios visuales?** Sí
- **Pantallas y escenarios revisados:** Retest Compose lab y verificación manual del candidato integrado con fixture sintético de deuda.
- **Evidencia:** [diálogo de reemplazo](evidence/manual-qa74-replacement-confirmation-crop.png) y [proyección/feedback](evidence/manual-qa74-replacement-success-crop.png), crops sanitizados con hashes registrados abajo.
- **Privacidad:** Los recortes muestran solo el diálogo o la proyección del fixture QA; no se duplican capturas ni datos de otras cuentas.

## Pendientes / límites de la revisión

- La confirmación Compose aislada y el feedback visual en el candidato integrado quedaron verificados en alcance acotado. TalkBack y reproducción del APK histórico siguen pendientes; la causa no está confirmada.
- La evidencia financiera combina inspección estática del call path local y unit tests con repositorio mock; no prueba persistencia integrada del reemplazo.
- La verificación manual de #74 en el candidato integrado ya se completó con `com.kipu.app` v0.1.0/code 1, SHA-256 `38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce`; los crops sanitizados están en `evidence/`. Se volvió a leer la app instalada y el hash coincide con el candidato. La ejecución lab usa aparte `com.kipu.app.lab`, SHA-256 `059f4e5fe54ee33f2e925dba8ab26cf181a46be42e86ef44a5cd09c1c937312c`.
- Diff local sin commit; branch `013-ep-deu-deudas-prestamos` y HEAD `6441851` permanecen intactos.

## Acciones en GitHub

- **Aprobación formal:** No enviada
- **Comentarios publicados:** No
- **Solicitudes de cambio:** No se publicaron en GitHub; BUG-074-001 quedó resuelto en retest local.

## Retest focalizado posterior — Issue #74 (2026-10-20)

- **Orden serial:** ejecutado después de cerrar y actualizar el bloque #64. El bloque #69 había sido cerrado antes de iniciar #74; su evidencia unit 2/2 PASS permanece independiente.
- **Preflight lab:** ADB por ruta explícita `C:/Users/Alume/AppData/Local/Android/Sdk/platform-tools/adb.exe`; SM-A165M, Android 16/API 36 (serial omitido). Antes del test app y runner lab no estaban instalados. App `com.kipu.app.lab` v0.1.0/versionCode 1, SHA-256 `059f4e5fe54ee33f2e925dba8ab26cf181a46be42e86ef44a5cd09c1c937312c`; runner `com.kipu.app.lab.test`, SHA-256 `ee712cb0db24345ffc41914e0f2d6edd0acd7340f229086e9b1bb5dca79ac6ec`. BuildConfig: `GOALS_REMOTE_COMMANDS_ENABLED=false`, `SUPABASE_URL=""`. Tras ejecución lab y runner no quedaron instalados; paquete personal sigue v0.1.0/versionCode 1 y no se abrió ni actualizó.
- **Comando:** `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest#replacingScheduleRequiresExplicitConfirmationAndExplainsHistoryEffect' :app:connectedLabAndroidTest` — exit 0, `BUILD SUCCESSFUL`; 1 test, 0 failures, 0 errors, 0 skipped.
- **Asserts observados:** al abrir el diálogo `saveCalls=0`; “Conservar actual” mantuvo `saveCalls=0`; después de reabrir el diálogo, selector `[1]` activó confirmación y `saveCalls=1`. Esto verifica el flujo Compose del test, no persistencia local/remota ni ausencia de pagos en ejecución integrada.
- **Artefactos:** APK app SHA-256 `059f4e5fe54ee33f2e925dba8ab26cf181a46be42e86ef44a5cd09c1c937312c`; runner SHA-256 `ee712cb0db24345ffc41914e0f2d6edd0acd7340f229086e9b1bb5dca79ac6ec`. XML sanitizada [QA-74-replacement-lab-retest-20261020.xml](evidence/QA-74-replacement-lab-retest-20261020.xml), SHA-256 `1caf8b7731073e630734a7ca7a78bcb55eff141f20b72fef55021a63333fb721`; serial omitido.
- **Observación del selector:** el índice `[1]` llegó al botón y pasó en este árbol runtime; es posicional y frágil ante cambios de orden/estructura. Se registra como recomendación de robustez, no como fallo funcional ni motivo para invalidar este pase.
- **Pendiente:** anuncio por TalkBack, persistencia integrada, historial CANCELLED, si una repetición idéntica aceptada crea una revisión adicional y reproducción del APK histórico. El candidato mostró feedback visible; no se declara confirmación remota ni aprobación completa del issue/release.

Acciones formales GitHub no enviadas. Este dictamen y el informe local no equivalen a aprobación ni publicación de una revisión en GitHub.

## Revalidación secuencial del worktree #69/#74 (2026-10-20)

- **Worktree/estado:** `013-ep-deu-deudas-prestamos`, HEAD/base `6441851`; se conservaron los cambios locales y no hubo commit.
- **Compilación lab:** `$env:ANDROID_HOME='C:\Users\Alume\AppData\Local\Android\Sdk'; .\gradlew.bat --no-daemon -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest :app:compileLabAndroidTestKotlin :app:testLabUnitTest --tests com.kipu.app.feature.debts.presentation.DebtScheduleViewModelTest --rerun-tasks` — `BUILD SUCCESSFUL`, 90 tareas ejecutadas. XML JVM: 2 tests, 0 failures/errors/skips.
- **Prueba de reemplazo en el teléfono:** `$env:ANDROID_HOME='C:\Users\Alume\AppData\Local\Android\Sdk'; .\gradlew.bat --no-daemon -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.debts.presentation.DebtScheduleAndClosureFlowTest#replacingScheduleRequiresExplicitConfirmationAndExplainsHistoryEffect' :app:connectedLabAndroidTest` — `BUILD SUCCESSFUL`, 1 test, 0 failures/errors/skips; XML local `TEST-SM-A165M - 16.xml`, SHA-256 `5bb7bfb19b336e816ae1b7dbda423d2b37f542d827dac884f2ef322c99acc50d`. Se conserva el XML bruto en build output local, no se copia al review por incluir metadatos del dispositivo.
- **Aislamiento:** Gradle instaló/ejecutó la variante `com.kipu.app.lab`; los tests son Compose/fixtures sintéticos. No se abrió ni modificó la sesión o base de datos de `com.kipu.app` durante estas ejecuciones.
- **Alcance:** esto revalida el snapshot fuente de #69/#74; no reproduce la carrera real de #69, no prueba persistencia/ledger y no reconstruye `validation-candidate` (el manifest prohíbe perder su configuración pública aprobada sin la misma inyección).

## Addendum — candidato integrado validado en modo lab (2026-10-21)

- En `kipu-validation-candidate` se compiló el conjunto `lab`/`labAndroidTest` y se ejecutó `DebtScheduleViewModelTest` 2/2 PASS. El APK debug con configuración pública aprobada no se reconstruyó.
- En tu teléfono, secuencialmente, el candidato integrado pasó `AccountDetailScreenTest` 16/16, ARCHIVED #64 1/1, ACTIVE #64 1/1 y el test de reemplazo #74 1/1. Cada instrumentación usó `com.kipu.app.lab` y datos Compose sintéticos; `com.kipu.app` no participó en esas cuatro corridas.
- Después se verificó de solo lectura que `base.apk` de `com.kipu.app` seguía con SHA-256 `38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce`, idéntico al candidato; `com.kipu.app.lab` no quedó instalado.
- **Límite:** esto aumenta confianza en la integración de los archivos cotejados, pero no prueba callback simultáneo en runtime, persistencia Room/ledger, TalkBack ni sincronización. No se ejecutó la suite global y no se hicieron acciones de PR.

## Addendum — verificación manual serial del candidato integrado — Issue #74 (2026-10-20)

- **Orden y target:** #69 quedó documentado antes de comenzar este bloque. En el único SM-A165M/API 36 se usó el APK autorizado `com.kipu.app` 0.1.0/code 1, SHA-256 `38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce`; `adb install -r` se ejecutó sin borrar datos. Readback posterior al install coincidió en package/version, SHA-256 del `base.apk` y certificado esperado. No se tocó DB/WAL, red, credenciales ni sesión.
- **Fixture / baseline observable:** deuda QA sintética `QA_AUDIT_20261020_CRON`, con cronograma previo de 3 cuotas pendientes y saldo a programar. No se usaron otras deudas ni movimientos personales.
- **Cancelación de reemplazo:** borrador de 4 cuotas abrió el diálogo explícito. La UI informó que cuotas PENDING/PARTIAL pasan a historial CANCELLED, el saldo pendiente no cambia y no se registran pagos. Se pulsó `Conservar actual`; al reabrir el editor se observó el cronograma original de 3 cuotas (sin borrador de 4), evidencia de que cancelar no aplicó el reemplazo.
- **Confirmación/reemplazo:** se repitió el borrador de 4 cuotas y se confirmó `Reemplazar cronograma`. El texto visible y expuesto en la jerarquía UI fue `Guardado en este dispositivo. No se registraron pagos.` (esto no acredita anuncio por TalkBack). La proyección final mostró exactamente 4 cuotas (1 PENDING, 3 FUTURE), suma total igual al principal pendiente del fixture. No se muestran cuotas activas duplicadas en esa proyección. No se consultaron Room, ledger ni el historial CANCELLED, por lo que no afirmo verificación de persistencia de esas filas ni de ausencia de movimientos fuera de la señal UI.
- **Identidad / repetición idéntica:** sin modificar el cronograma ya guardado de 4 cuotas, un guardado idéntico volvió a requerir la misma confirmación explícita; `Conservar actual` cerró el diálogo y dejó visible la proyección de 4 cuotas. Esto satisface la alternativa de confirmación transparente para la sustitución idéntica permitida por los criterios, pero no se confirmó una segunda mutación/revisión.
- **Evidencia visual sanitizada:** [confirmación de reemplazo](evidence/manual-qa74-replacement-confirmation-crop.png), SHA-256 `0cb3b2a6b689c2b67537af6f9c72faded4da0235cab0080626fb89e6087d255e`; [4 cuotas, total exacto, CTA y feedback](evidence/manual-qa74-replacement-success-crop.png), SHA-256 `af7c52ce7836574830ba5824cde8e15b8203ee9c84899bdc88978a28f5a3b012`. Los crops contienen solo el diálogo o la proyección del fixture QA; omiten otros registros.
- **Resultado manual #74:** PASS acotado para diálogo de reemplazo/cancelación/confirmación, identidad del plan proyectado, ausencia de duplicación en la lista actual y feedback próximo al CTA. El texto de éxito distingue guardado local y no afirma sync remoto. #74 lab 1/1 PASS previo se mantiene separado.
- **Límites:** no se verificó TalkBack/anuncio por lector, persistencia Room/ledger, historial CANCELLED, sync/backend ni el APK histórico reportado. No se ejecutaron payments ni movimientos; el mensaje UI confirma que el flujo no registró pagos, pero no sustituye una inspección del ledger. La fragilidad del selector `[1]` del test instrumentado queda como observación anterior, no queda evaluada por esta prueba manual.

# Revisión Issue #63 — retorno tras archivar tarjeta (ronda 4)

- **PR:** No aplica; revisión del worktree local, sin PR asociado.
- **Repositorio:** Lume03/Kipu
- **Autor / asignado:** android-ui
- **Fecha de revisión:** 2026-10-20
- **Rama:** `002-ep-cta-cuentas-tarjetas` → `main`
- **Commit revisado:** `6441851db93b0c23655283a2c4a257845592875f` (cambios auditados sin commit)
- **Worktree:** `C:/Users/Alume/orca/workspaces/kipu-ep-cta-issue63`

## Dictamen breve

**Resultado:** Requiere cambios

Revisé en el dispositivo autorizado la corrección de QA-63-03: las pruebas de cancelar y error ya pasan, pero el caso de descartar confirmación falla con “No compose hierarchies found in the app” después de enviar Atrás al `onBackPressedDispatcher`. La clase enfocada registra 16 tests, 1 failure, 0 errors y 0 skipped; por tanto la cobertura de cancelación/dismiss aún no está completamente validada.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#63](https://github.com/Lume03/Kipu/issues/63) | Confirmado; mejora UX/P3, no bug financiero confirmado | Historial/estado archivado; feedback y retorno; no reabrir wizard; error/cancelación sin navegación de éxito. |

- **Issues no encontrados o relación incierta:** Ninguno.
- **Fuente de autoridad usada:** tarea Kanban `t_1afa93fe`, decisiones Tech Lead en el hilo, findings QA-63-01/02/03, informe ronda 3 e issue #63 previamente consultado.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| Archivo exitoso conserva historial, queda fuera de activas y no altera ledger | No verificable | La revisión no ejecutó archivo real ni inspeccionó Room/ledger; el cambio auditado es de navegación/presentación y pruebas. |
| Feedback/destino tras retorno directo y tras wizard | Cumple con límites | La prueba Robolectric focalizada aprobada en ronda 2 ejercita el helper productivo en grafo sintético (3/3); no es E2E de composables reales. |
| Atrás no expone wizard ni produce efectos financieros | Parcial | Back stack sintético previamente validado; no se ejecutó el flujo real de alta/archivo completo. La prueba de dismiss actual falla antes de la aserción de destino. |
| Error y cancelar no navegan como éxito | Parcial | En ejecución instrumentada, `archiveErrorShowsErrorAndDoesNotReturnToDashboard` y `cancellingArchiveKeepsDetailAndDoesNotInvokeArchiveOrReturn` pasaron. `dismissingArchiveConfirmationKeepsDetailAndDoesNotInvokeArchiveOrReturn` falla al comprobar el nodo tras back. |
| Test JVM focalizado y compileDebugKotlin | Cumple con límites anteriores | 3/3 Robolectric ya verificados por QA en ronda 2. En esta ronda se construyeron `assembleLab`/`assembleLabAndroidTest`; androidTest compiló. No repetí la suite JVM, fuera del alcance de re-test solicitado. |
| Handoff con comandos, resultados, conteos y hashes | Parcial | Build APKs y XML actual registrados aquí. La clase instrumentada no está GREEN; falta re-test tras corregir dismiss/back. |

## Cambios revisados

- `AccountDetailScreenTest.kt`: reemplazo de `createComposeRule` por `createAndroidComposeRule<ComponentActivity>`, casos de cancelación/dismiss/error; selectores ahora usan `account_alias` y dispatcher se invoca en `runOnUiThread`.
- `AccountsNavigation.kt`, `AccountsReturnNavigationTest.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`: sin cambios respecto a los hashes previamente auditados.
- Los cambios a `docs/qa` son de informes e índice del auditor.

## Hallazgos y mejoras

### Bloqueantes / importantes

- **QA-63-04 — El test de dismiss/back pierde la jerarquía Compose. Severidad: MEDIUM.**
  - **Ubicación:** `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreenTest.kt:178-183`.
  - **Reproducción:** ejecutar `:app:connectedLabAndroidTest` con el filtro `com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest`; el test `dismissingArchiveConfirmationKeepsDetailAndDoesNotInvokeArchiveOrReturn` hace click en “Archivar cuenta”, llama `activity.onBackPressedDispatcher.onBackPressed()` desde `runOnUiThread` y luego `onNodeWithTag("account_alias").assertIsDisplayed()`.
  - **Resultado observado:** `IllegalStateException: No compose hierarchies found in the app` en línea 182. La invocación de back no conserva una jerarquía Compose consultable en ese momento; no se alcanzan las aserciones que prueban que el detalle continúa visible y que no se llamó archivo/retorno.
  - **Esperado:** cerrar únicamente el diálogo de confirmación mediante una interacción de back/dismiss válida y verificar que se conserva el detalle, sin invocar archivo ni retorno.
  - **Evidencia:** `app/build/outputs/androidTest-results/connected/lab/TEST-SM-A165M - 16.xml`, SHA-256 `9a9c3ac1d7c9b4b14bc8ecb9696a2417f50f6d8f6a838a3d932dbaf01ccf7281`; 1 suite, 16 tests, 1 failure, 0 errors, 0 skipped. Los tests de cancelar y error aparecen pasados.
  - **Corrección mínima:** ajustar el mecanismo del test para descartar el diálogo sin cerrar/sacar de estado la Activity o reemplazarlo por una interacción Back válida de Compose/Activity; preservar y ejecutar las aserciones de detalle visible y callbacks no invocados. Volver a ejecutar únicamente la clase y requerir 16/16.

### Menores / sugerencias

- El build reporta deprecación de `createAndroidComposeRule` y recomienda `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`; no es el motivo del rechazo ni se requiere migración para resolverlo.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `./gradlew -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest` sin ANDROID_HOME explícito | Falla de precondición | SDK location not found; no se editaron `local.properties` ni credenciales. |
| `ANDROID_HOME=C:/Users/Alume/AppData/Local/Android/Sdk ./gradlew -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest` | Pasa | `BUILD SUCCESSFUL`, 82 tareas (6 ejecutadas, 76 up-to-date); compila app/test APK lab. |
| `ANDROID_HOME=C:/Users/Alume/AppData/Local/Android/Sdk ./gradlew -PisolatedAndroidTests=true -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest :app:connectedLabAndroidTest` | Falla | Device SM-A165M/API 36; 16 tests, 15 pasaron y 1 falló (QA-63-04). No se ejecutó otra suite. |
| `git diff --check` | Pasa | Sin errores de whitespace. |
| Inspección de paquete y readback | Pasa, aislado | APK candidate `com.kipu.app.lab`, version `0.1.0`, target SDK 36; test APK `com.kipu.app.lab.test`. Después del test, ambos paquetes lab no están instalados. `com.kipu.app` y `com.kipu.app.test` siguen instalados; no se abrió ni modificó la app personal/sesión. |

Dispositivo: serial omitido, modelo `SM-A165M`, API 36. APK SHA-256 `b2c46298c837d554703e9240c114e6cf4d15b9dceacc0cd7856e1c17a6ce84be`; test APK SHA-256 `2c7044a3b4c91bc9082d84ead41778b9f248902b9395f4dad1a53dbc38b784a5`.

Hashes SHA-256 de archivos revisados:

- `app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreenTest.kt`: `1de57d6bdee614718bd705524519976832243f64c184c5aff8d542ae86b1444e`
- `app/src/main/java/com/kipu/app/navigation/AccountsNavigation.kt`: `7e843b332db0ada9747a287ecf8ca809722117d3f4dc05109c4a484647765234`
- `app/src/test/java/com/kipu/app/navigation/AccountsReturnNavigationTest.kt`: `cff77ffd0e01123270531e8f79573e3851aeede798fc4e1d1f6ed49d55c9cfb1`
- `app/build.gradle.kts`: `2148caba3750ee139499d4b2854a3f831c0babf584181cdfffcd4b8e2b59ba1a`
- `gradle/libs.versions.toml`: `95755db9a7818f694610f8443d2a5e2f228a29e7000d22dfb30490af55d3e77c`
- `app/build/outputs/androidTest-results/connected/lab/TEST-SM-A165M - 16.xml`: `9a9c3ac1d7c9b4b14bc8ecb9696a2417f50f6d8f6a838a3d932dbaf01ccf7281`

## Revisión visual (solo si aplica)

- **¿Hay cambios visuales?** No; es navegación/feedback y pruebas de presentación.
- **Pantallas y escenarios revisados:** Solo tests instrumentados con contenido sintético; no hubo exploración manual de la app.
- **Evidencia:** No se adjuntan capturas; el XML prueba resultados de tests, no una revisión visual manual.
- **Privacidad:** No se leyó sesión ni se capturaron datos personales.

## Pendientes / límites de la revisión

- Corregir QA-63-04 y re-ejecutar solo `AccountDetailScreenTest`; exigir 16/16.
- El test Robolectric usa un grafo sintético, no E2E desde composables reales.
- No se verificó archivo, persistencia/historial/estado activo-archivado en Room ni ledger.
- No se probó manualmente el flujo con cuenta existente; el APK personal baseline no se abrió/actualizó.
- La ejecución Robolectric previa requiere JDK 21 y `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` por init script temporal; CI portable pendiente.

## Acciones en GitHub

- **Aprobación formal:** No enviada.
- **Comentarios publicados:** No.
- **Solicitudes de cambio:** Kanban, no en GitHub.

## Addendum — ronda 5: re-test independiente de QA-63-04

- **Fecha:** 2026-10-20.
- **Dictamen de esta ronda:** QA-63-04 resuelto en la prueba instrumentada. No equivale a aprobación completa de #63 ni de release: el flujo manual con el candidato instalado, archivo/persistencia/ledger y revisión visual siguen sin verificarse.
- **Cambio bajo prueba:** `AccountDetailScreenTest.kt:168-186` envía Back mediante `Espresso.pressBack()` y conserva las aserciones de detalle visible, diálogo cerrado y `onArchive`/`onNavigateBack` no invocados. No se llamó directamente a `onDismiss` ni se simuló el cambio de estado.
- **Ejecución real:** `ANDROID_HOME=C:/Users/Alume/AppData/Local/Android/Sdk ./gradlew -PisolatedAndroidTests=true -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest :app:connectedLabAndroidTest` — exit code 0, `BUILD SUCCESSFUL`; dispositivo SM-A165M/API 36; 16 tests, 0 failures, 0 errors, 0 skipped. El test de dismiss y las pruebas previamente relevantes de cancelar/error pasaron.
- **XML:** `app/build/outputs/androidTest-results/connected/lab/TEST-SM-A165M - 16.xml`, SHA-256 `cf15e28eace222ec52fc0f17b732e9b339d4645f803c190ea1653abcb54ddfdf`. El XML previo se preservó antes de la nueva corrida; la copia publicable [evidence/QA-63-04-pre-remediation-16-sanitized.xml](evidence/QA-63-04-pre-remediation-16-sanitized.xml) redacta el serial del dispositivo y tiene SHA-256 `619390f720b62f3a896887cae562a1fed756c9582781e8878d34653ddb1222b8` (16 tests, 1 failure, 0 errors, 0 skipped). El XML original queda local y no se versiona.
- **Identidad/aislamiento:** APK lab `com.kipu.app.lab` version `0.1.0`, SHA-256 `b2c46298c837d554703e9240c114e6cf4d15b9dceacc0cd7856e1c17a6ce84be`; runner `com.kipu.app.lab.test`, SHA-256 `a40cb767d3c00bed82a64596f26d2b0d545eaf97adb5fdf0f63f9fc70c112506`. Badging verificó los application IDs antes de ejecutar. Después, ambos paquetes lab ya no estaban instalados; `com.kipu.app` permaneció instalado, version `0.1.0`, versionCode `1`, y el APK leído antes y después tuvo el mismo SHA-256 `e218941857b805335e932ac78b6412ef31e316607a0775ca2824db4eda8c2864`.
- **Hashes de esta verificación:** test `AccountDetailScreenTest.kt` SHA-256 `fe194c7171c0503aa5b374f978d656bd7246dc72f9e42145aad5c5179cb6f885`; APK lab y test APK según arriba. No se modificó código productivo.
- **Revisión visual/manual:** no ejecutada. El APK personal instalado es baseline y no se verificó correspondencia con este candidato; no se abrió ni modificó la sesión personal y no se adjuntan screenshots para evitar exponer datos sin un fixture QA aprobado. Para validar visualmente el candidato hace falta una autorización del Owner para un plan seguro de instalación/ejecución sobre sesión existente, con hash del APK y revisión de privacidad previos; no instalar ni reemplazar la app personal sin esa autorización.
- **Límites restantes:** no se verificó archivo real, persistencia/historial/estado activo-archivado en Room o ledger ni comportamiento E2E con cuenta existente. Mantener esos criterios como no verificados; esta evidencia solo cierra el fallo del harness QA-63-04.

## Addendum — revalidación fresca del worktree #63 (2026-10-20)

- **Worktree/estado:** `002-ep-cta-cuentas-tarjetas`, HEAD/base `6441851`; los cambios locales existentes se conservaron. No se hizo commit ni push.
- **Compilación lab:** `ANDROID_HOME=C:/Users/Alume/AppData/Local/Android/Sdk ./gradlew.bat --no-daemon -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest :app:compileLabAndroidTestKotlin` — `BUILD SUCCESSFUL`, 82 tareas `UP-TO-DATE`.
- **Prueba en el teléfono, ejecución aislada:** `$env:ANDROID_HOME='C:\Users\Alume\AppData\Local\Android\Sdk'; .\gradlew.bat --no-daemon -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest' :app:connectedLabAndroidTest` — `BUILD SUCCESSFUL`; XML `TEST-SM-A165M - 16.xml`: 16 tests, 0 failures, 0 errors, 0 skipped; SHA-256 `7c5c9d7a10a44379d2f6ae9d2e001cba079b769effadc8af09bd05f4c59d2188`. La variante `com.kipu.app.lab` usa contenido sintético; no consultó ni modificó la sesión/datos de `com.kipu.app`.
- **Test JVM del helper:** `$env:ANDROID_HOME='C:\Users\Alume\AppData\Local\Android\Sdk'; $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; $env:JAVA_TOOL_OPTIONS='--add-exports=java.base/jdk.internal.access=ALL-UNNAMED'; .\gradlew.bat --no-daemon -PisolatedAndroidTests=true :app:testLabUnitTest --tests com.kipu.app.navigation.AccountsReturnNavigationTest --rerun-tasks`. No llegó a ejecutar el test: Robolectric informó `Android SDK 36 requires Java 21 (have Java 17)` en el worker Gradle. JBR 25 estaba disponible, pero el worker se resolvió a Java 17. No se usó el init script fuera del repo; el resultado previo 3/3 con Temurin 21 se conserva, pero no se presenta como revalidado ahora.
- **Resultado del worktree:** la instrumentación específica de #63 queda fresca en 16/16 PASS. El helper Robolectric conserva el resultado previo 3/3, pero esta ronda no pudo revalidarlo: JBR25 estaba disponible, el worker Gradle usó Java17 y Robolectric rechazó SDK36 antes de ejecutar el test. No es fallo de una aserción de producto.
- **Resultado:** QA-63-04 y los 16 tests instrumentados vuelven a pasar en el teléfono. El cambio #63 sigue sin verificación manual específica de **tarjeta de crédito**: el recorrido manual documentado en ronda 6 fue una cuenta de efectivo QA vacía. No elevar ese PASS a prueba de tarjeta, Room o ledger.

## Addendum — ronda 6: verificación manual acotada en candidato integrado

- **Fecha / orden:** 2026-10-20. Ejecutado después de recibir `GO_BOUNDED` de `t_7f5569ab`; QA fue el único operador del dispositivo. Esta ronda no sustituye los retests lab 16/16 ni aprueba #63/release.
- **Preflight e instalación:** ADB por ruta explícita `C:/Users/Alume/AppData/Local/Android/Sdk/platform-tools/adb.exe`, un dispositivo `device` (serial omitido), SM-A165M, API 36. Antes de instalar, el APK candidato verificó SHA-256 `38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce`, identidad `com.kipu.app` 0.1.0/code 1 y firma SHA-256 `cb755d522a2dd5ce4fae360b33534f3594788fd01c10967917788b7a738395b8`, coincidentes con el GO específico. Se ejecutó `adb install -r` (Success), sin uninstall/clear-data/reset. Readback posterior: paquete/version correctos, base.apk instalado SHA-256 igual al candidato y certificado coincidente. No se consultó DB/WAL.
- **Inicio/sesión:** la app abrió en `MainActivity` y llegó al dashboard autenticado existente sin pantalla de login ni error visible de base de datos; la sesión y los datos existentes estaban presentes visualmente. No se afirma supervivencia remota ni estado de sincronización.
- **Fixture manual:** se creó por UI únicamente `QA_AUDIT_20261020_I63_RET`, tipo efectivo, PEN, saldo inicial S/0.00. Se revisó su detalle aislado antes de archivar. La confirmación indicó preservación de movimientos/historial y ubicación en Archivados; al cancelar, el detalle permaneció con estado `Cuenta activa`. Después se confirmó archivo y la app regresó al dashboard `Mi dinero`; no se observó pantalla de error. No se generaron movimientos/pagos y el fixture quedó archivado (saldo cero, historial vacío). No se eliminaron ni modificaron otros registros.
- **Evidencia visual privada:** [detalle antes del archivo](evidence/manual-qa63-before-archive.png) (SHA-256 `69afd698d18e3d6a9a4bdfa1fbb724035df802c45c1fbb99df583de2495d3105`), [confirmación](evidence/manual-qa63-confirmation.png) (SHA-256 `c30bb207299bbd1c9d8fbb482d6a5dd49da1825f6e25e73848c69ac553b3a105`) y [retorno al dashboard — crop sanitizado](evidence/manual-qa63-after-return-crop.png) (SHA-256 `db0ae70f268b5f18cb8b9eaa55fd7498d1d9e9fc110e6ab9d84a0f0e1e8e7f52`). Las dos capturas del fixture contienen solo el registro sintético recién creado y saldo cero; el retorno se recortó para excluir usuario, balances, cuentas y notificaciones. Los originales de pantallas con otros datos observados en la sesión se conservaron solo temporalmente en scratch y no se distribuyeron.
- **Estado de aceptación manual:** PASS acotado para confirmación/cancelación y retorno del archivo de una cuenta QA vacía en este build exacto. **NOT TESTED** para tarjeta de crédito/tarjeta archivada específica, wizard completo, persistencia/ledger Room, reactivación/Archivados, TalkBack y estado remoto; no usar una tarjeta preexistente no-QA para completar esos casos. La captura de la confirmación solo prueba la explicación visible, no persistencia de historial.
- **Residuos / límites:** queda el fixture QA propio archivado, sin movimientos y con saldo inicial cero. App personal continúa instalada en versión 0.1.0/code 1 con el APK candidato; no se cambió red, credenciales, sesión ni permisos. El WAL/PRAGMA efectivo y la supervivencia de sesión siguen dentro de los límites del GO; sin afirmaciones de sync remoto.

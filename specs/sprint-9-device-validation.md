# Sprint 9 — validación funcional en dispositivos reales

**Lectura del estado:** la matriz inicial es histórica; las re-verificaciones posteriores reemplazan resultados únicamente para el criterio indicado. La última tanda focalizada figura al final; persiste aceptación parcial.

Fecha: 2026-11-03 (America/Lima). Código probado: `main`, `19312794065054a5c1cefc208ab036e9c9ee0c77`.

**Resultado: ejecución completada con fallos y cobertura pendiente; no constituye aprobación integral del sprint.** No se modificó código de producción ni se desplegó/pusheó nada. No se ejecutó `connectedAndroidTest`, desinstalación, `pm clear`, cierre de sesión real ni eliminación de cuenta real.

## Entorno y evidencia privada

Directorio externo al repositorio: `C:/Users/Alume/s9-device-validation-20261103/`. Todos los nombres de evidencia siguientes son relativos a ese directorio. Contiene capturas, XML de UI, exportaciones y copias privadas de Room; no publicar esos archivos porque contienen datos reales. Los scripts auxiliares también están allí. Ninguna credencial se imprime ni se versiona; el archivo transitorio de credenciales de la cuenta desechable se eliminó tras confirmar Auth 404.

| Teléfono | Serial ADB | Instalación anterior y actual | Cuenta |
|---|---|---|---|
| A16 / SM-A165M | `RFGL70J9E2T` | versionName 0.1.0, versionCode 1; Room 33 → 37 | Cuenta real conservada; fingerprint SHA256 del user_id `1a252f6a742b` |
| S24 / SM-S926B | `adb-R5CX10W7CZD-UQMXjN._adb-tls-connect._tcp` | versionName 0.1.0, versionCode 1; Room 33 → 37 | La misma cuenta real; fingerprint `1a252f6a742b` |

ADB usado exclusivamente: `%LOCALAPPDATA%/Android/Sdk/platform-tools/adb.exe`, siempre con `-s SERIAL`. Aunque inicialmente `run-as` del S24 indicó no depurable, `install -r` del APK debug y del APK de pruebas terminó **Success** en ambos y después permitió inspección privada. No hubo recuperación mediante desinstalación.

Autoridades consultadas: `docs/README.md`, control documental, constitución, spec/plan/tasks de EP-APS, EP-INF y EP-AUT. La validación remota se hizo contra el backend existente; no se infiere despliegue a partir de archivos locales. Referencia de Management API consultada: [Get project API keys](https://supabase.com/docs/reference/api/v1-get-project-api-keys).

## Matriz de aceptación ejecutada

| HU / criterio | Teléfono | Resultado | Evidencia y alcance |
|---|---|---|---|
| Base: build actual e instalación conservando datos | Ambos | PASS | `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`, `build.log`: BUILD SUCCESSFUL; `adb -s SERIAL install -r app-debug.apk` y APK androidTest: Success |
| Base: apertura y migración real 33 → 37 | Ambos | PASS | Baselines privados y `final-validation.json`: user_version 37; `a16-session-final.png`, `s24-home-final.png` |
| Base: movimientos, ledger y saldos conservados | Ambos | PASS | A16: 7 transacciones / 9 ledger; S24: 9 / 9 antes y después; cuatro proyecciones de saldo por teléfono sin cambio de valores, `balance-final.json` |
| HU-49: OCR sintético local | A16 | PASS | `YapeReceiptOcrInstrumentedTest`, `a16-instrumentation.txt`; además selección real de PNG sintético mediante Photo Picker, `a16-yape-read.png`, `a16-yape-review.png` |
| HU-49: prellenado sin movimiento confirmado | A16 | PASS | Importe 46.70, contraparte ficticia y referencia 998877665; aviso de revisión; transacciones siguen 7 y ledger 9; candidato final DISCARDED, `final-validation.json` |
| HU-49: ACTION_SEND imagen desde shell | A16 | NO VERIFICADO | Intent abre consentimiento, `a16-yape-share.png`; después la URI MediaStore inyectada por shell no pudo leerse. La misma imagen sí se leyó con concesión del Photo Picker. No se certifica sharing completo desde Yape ni se atribuye ese fallo al OCR |
| HU-49: archivo no imagen | A16 | PASS | ACTION_SEND text/plain; mensaje «Comparte una sola imagen legible de Yape…», capturado y revisado en `a16-nonimage-toast.png` |
| HU-49: persistencia, resolución atómica y ledger de recibos | A16 | PASS | `ReceiptCandidateRoomTest`, `CaptureAtomicResolutionTest`, `YapeReceiptLedgerAcceptanceTest`, `ReceiptRoomMigrationTest`, `HybridYapeMigrationTest` en `a16-instrumentation.txt`; bases aisladas |
| HU-49: migración 36 → 37 con evidencia histórica | A16 | PASS | `HybridYapeMigrationTest.v36To37SeedsHistoricalProvenanceAndValidatesSchema`, validación completa del esquema |
| HU-49: correlación en ambos órdenes, ambigüedad y ediciones | Host | PASS | `HybridYapeRoomTest`, `CaptureCorrelationTest` en la tanda de 48 unitarias focalizadas; `unit.log`, `unit-summary.json`. Cobertura local/Robolectric, no notificación real |
| HU-49: ocho imágenes privadas del propietario | A16 | NO VERIFICADO | `YapePrivateSamplesAcceptanceTest` invocado; fixture opcional ausente y caso omitido por assumeTrue. No equivale a ocho muestras validadas |
| HU-49: notificación Yape viva + OCR en ambos órdenes | Ambos | NO VERIFICADO | Listener desactivado y sin consentimiento en A16; no se habilitó automatización capaz de crear movimientos falsos en la cuenta real. Cubierto parcialmente por pruebas aisladas, no por el sistema de notificaciones vivo |
| HU-41: mes actual, fechas y periodo parcial | Ambos | PASS | `a16-analytics.png`, `s24-analytics.png`: 1–3 nov 2026, aviso parcial y gasto operativo |
| HU-41: filtros reales | A16 | PASS | `a16-analytics-filters.png`, `a16-analytics-filtered.png`: Cafeterías, una categoría, total 38.00 |
| HU-41: comparación Premium y base cero | A16 | PASS | `a16-analytics-comparison.png`: octubre frente a noviembre, base 0 y «Sin base de comparación»; sesión muestra acceso Premium |
| HU-41: gating Free sobre cuenta real | Ambos | NO VERIFICADO | No se cambió plan/entitlement real. El estado Free se prueba aisladamente con AnalyticsScreenTest, cuyo conjunto tiene otro fallo descrito abajo |
| HU-41: AnalyticsDaoTest | Ambos | PASS | `a16-instrumentation.txt`, `s24-analytics-instrumentation.txt` |
| HU-41: AnalyticsScreenTest | Ambos | FAIL | `currentMonthShowsTotalCategoryAndPartialDates`: analytics_partial_warning no visible según assertIsDisplayed; reproducido en ambos. Las pantallas reales sí lo muestran; ver QA-S9-02 |
| HU-06 A: exportar CSV a Descargas con SAF | S24 | PASS | `s24-csv-picker.png`, `s24-csv-exported.png`, `kipu-movimientos.csv`, `export-validation.json`: nueve filas; IDs únicos, montos enteros, moneda y fechas presentes |
| HU-06 A: exportar JSON con SAF y esquema | S24 | PASS | `s24-json-picker.png`, `s24-json-exported.png`, `kipu-datos.json`: schemaVersion 1, integer_minor_units, entidades propias. Ausencia de access_token, refresh_token, password, service_role y secret_hash comprobada; también contrato unitario |
| HU-06 A: restaurar dos veces desde misma cuenta | S24 | FAIL | `s24-restore-first-complete.png`, `s24-restore-second.png`: seis módulos completados; Perfil y preferencias fallido/reintentable en ambos intentos. Se informa restauración parcial; ver QA-S9-01 |
| HU-06 A: idempotencia financiera al restaurar | S24 | PASS | `s24-after-first-summary.json`, `s24-after-second-summary.json`: transacciones 9, financial_movements 5, ledger 9, cuentas 4, tarjetas 2, sin duplicación; saldos iguales al baseline. Cambian timestamps y representación icon/color de cuentas al hidratar |
| HU-06 A: comparación entre teléfonos | Ambos | PASS | Misma cuenta y mismos valores de saldo iniciales/finales. El conteo previo ya era distinto (A16 7, S24 9 transacciones); no se afirma convergencia del historial A16 sin restaurarlo |
| HU-06 A: aviso de pendientes locales | Ambos | PASS | A16 0; S24 1 de Perfil, preexistente y conservado; `s24-privacy.png`, `a16-deletion-cancelled.png` |
| HU-06 A: crear cambio offline y recuperación online | Ambos | NO VERIFICADO | No se usó modo avión; el aviso se comprobó con pendiente real preexistente. No se certifica recuperación de ese cambio de Perfil, que sigue pendiente |
| HU-06 B: resumen y Cancelar con cuenta real | Ambos | PASS | `a16-deletion-summary.png`, `a16-deletion-cancelled.png`, equivalentes s24; advertencia de pérdida/Google Play/pendientes, sin avanzar a contraseña ni confirmar eliminación |
| HU-06 B: eliminación completa de cuenta desechable | API | PASS | `remote-result.json`: profile_before count 1, password login, delete-account HTTP 200 completed, repetición 200 completed, Auth GET admin/users/{id} 404, 60 tablas con user_id verificadas y cero filas remanentes |
| HU-06 B: reautenticación reciente requerida | API | PASS | JWT real de la cuenta desechable envejecido 321 s: HTTP 403 recent_reauthentication_required; posterior password login permitió completar. No se falsificó JWT |
| HU-06 B: estados, cancelación y limpieza aislados | Host | PASS | AccountDeletionTest/RoomTest/RemoteTest/CleanupTest y persistencia, incluidos en `unit-summary.json`; esas clases viven en src/test, no son instrumentadas |
| HU-06 B: UI completa con cuenta desechable | Ambos | NO VERIFICADO | Habría requerido sustituir la sesión real; se mantuvo la cobertura API y unitaria |
| Regresión: Inicio/Cuentas, Movimientos, Presupuestos, Calendario | Ambos | PASS | `a16-session-final.png`, `s24-home-final.png`, `*-reg-movements-final.png`, `*-reg-budgets-final.png`, `*-reg-calendar-final.png`; apertura sin crash |
| Regresión: crear y retirar categoría temporal con sync normal | S24 | PASS | `s24-category-saved.png`, `s24-category-inactivated.png`; CREATE_CATEGORY y SET_CATEGORY_ACTIVE COMPLETED, is_active=0. Retiro lógico, no borrado físico; residual detallado abajo |
| Cierre: logcat crash | Ambos | PASS | `*-crash-final.txt`: ningún FATAL EXCEPTION de Kipu; A16 solo mensajes informativos de librerías MediaTek, S24 vacío |
| Cierre: animaciones | Ambos | PASS | settings put global de animator_duration_scale, transition_animation_scale y window_animation_scale a 1.0; los seis settings get devolvieron 1.0 |

## Comandos reproducibles y resultados técnicos

Se construyeron los APK con `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`. Se instalaron ambos mediante `adb -s SERIAL install -r RUTA_APK`.

La ejecución focalizada fue `adb -s SERIAL shell am instrument -w -e class LISTA_FQCN com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`. En A16 la lista fue:

```text
com.kipu.app.feature.receipts.data.YapeReceiptOcrInstrumentedTest
com.kipu.app.feature.receipts.data.ReceiptCandidateRoomTest
com.kipu.app.core.database.CaptureAtomicResolutionTest
com.kipu.app.navigation.YapePrivateSamplesAcceptanceTest
com.kipu.app.feature.movements.data.local.YapeReceiptLedgerAcceptanceTest
com.kipu.app.core.database.ReceiptRoomMigrationTest
com.kipu.app.core.database.HybridYapeMigrationTest
com.kipu.app.feature.analytics.presentation.AnalyticsScreenTest
com.kipu.app.feature.analytics.AnalyticsDaoTest
```

Runner A16: **Tests run: 25, Failures: 1**, con fixture privado opcional omitido. S24: AnalyticsScreenTest + AnalyticsDaoTest, **Tests run: 7, Failures: 1**. El exit del shell no se tomó como prueba de éxito: se inspeccionó FAILURES del runner. No se repitieron pruebas verdes.

Pruebas host: `gradlew.bat :app:testDebugUnitTest --tests 'com.kipu.app.feature.portability.*' --tests 'com.kipu.app.navigation.ReceiptShareIntentTest' --tests 'com.kipu.app.feature.capture.data.HybridYapeRoomTest' --tests 'com.kipu.app.feature.capture.domain.CaptureCorrelationTest'`: **48 tests, 16 clases, cero fallos, cero omitidos**, BUILD SUCCESSFUL. ReceiptShareIntentTest y AccountDeletion* no existen como clases androidTest; no se inventó cobertura instrumentada.

## Incidencias y límites

### QA-S9-01 — Restauración de Perfil y preferencias falla (severidad media)

En S24, sesión real existente con un cambio pendiente de Perfil: Ajustes → Datos y privacidad → Restaurar mis datos → Continuar. Esperado: resultado de Perfil recuperado o resolución específica del pendiente, preservándolo; observado: seis módulos completados y «Perfil y preferencias: fallido, reintentable», con restauración parcial. Repetir restauración completa reproduce el fallo. Evidencia: `s24-restore-first-complete.png`, `s24-restore-second.png`. No se determinó causa raíz ni se modificó backend/código. No hubo duplicación financiera; el pendiente preexistente sigue en 1.

### QA-S9-02 — Aserción de visibilidad del aviso parcial falla en ambos dispositivos (severidad baja; defecto de prueba/integración por investigar)

Ejecutar AnalyticsScreenTest con am instrument. `currentMonthShowsTotalCategoryAndPartialDates` falla en línea 59 con «analytics_partial_warning is not displayed». Reproducido A16/S24. Las capturas de la app real muestran aviso y fechas correctamente, por lo que no se afirma un fallo funcional general del aviso. Evidencia: ambos logs instrumentados y `a16-analytics.png`, `s24-analytics.png`. No se corrigió el test ni la UI.

### Residual de prueba y diferencias previas

La categoría `QA-S9-TEMP-20261103` fue creada y luego **inactivada por la UI**, ambas operaciones sincronizadas COMPLETED. La UI de categoría inactiva solo ofrece Editar/Reactivar; no ofrece borrar físicamente. Queda una categoría histórica inactiva, sin movimientos asociados; no se borraron filas manualmente ni se operó contra Supabase de la cuenta real por fuera de la app. Por tanto, no se afirma limpieza física total de los datos de prueba. El candidato OCR sintético permanece como DISCARDED en Room; no hay movimiento/ledger falso. El consentimiento OCR temporal fue revocado y el PNG sintético se retiró de Download.

Los dos teléfonos ya diferían en transacciones (7 frente a 9) antes de la actualización y el calendario refleja esa diferencia. La restauración S24 conserva sus nueve; A16 no se restauró en este caso. Las dos evidencias receipt CONFIRMED del S24 eran preexistentes y no se tocaron.

El sharing inicial por ADB no concedió acceso legible a la URI MediaStore: se separa esa limitación del transporte shell del reconocimiento, que sí pasó al elegir la misma imagen con Photo Picker. No se publicaron notificaciones financieras falsas sobre la cuenta real. Los estados Free y fallo del servidor se cubrieron en pruebas aisladas, sin cambiar el plan o interrumpir conexiones de la cuenta real.

## Estado final confirmado

Sesiones reales intactas en ambos teléfonos, nuevamente en Inicio; mismos propietarios y saldos. Room v37, conteos de transacciones/ledger preservados, ninguna cuenta real borrada o deslogueada. Las seis escalas de animación leen **1.0**. Cuenta desechable borrada de Auth y sin filas propias en las 60 tablas verificadas; segunda llamada idempotente completada. Sin cambios de producción, sin push. Pendientes: QA-S9-01, QA-S9-02, cobertura marcada NO VERIFICADO y el residual histórico inactivo indicado arriba.


## Re-verificación de incidencias — 2026-11-03

Alcance: correcciones QA-S9-01/HU-06 A y QA-S9-02/HU-41 en `main`, sin push ni despliegue remoto por este worker. Este apartado actualiza los dos hallazgos; no convierte los casos NO VERIFICADO anteriores en PASS. Evidencia privada en el mismo directorio externo, prefijo `fix-` o `profile-rpc-`.

### QA-S9-01: causa raíz y corrección preparada

**Estado: cliente corregido y backend corregido/probado localmente; restauración contra remoto todavía FAIL.** El S24 tenía una operación de perfil PENDING: `RestoreUserData` evaluaba `pending == 0 && refreshProfile(...)`, devolviendo falso antes de consultar remoto. Quitar solo esa condición habría sobrescrito el perfil local: `refreshProfile` reemplazaba la caché completa. Ahora consulta realmente el servidor, verifica sesión/propietario y aplica los campos remotos en una transacción Room conservando campos incluidos en outboxes sin resolver, revisión local, estado local y operación pendiente (incluido CONFLICT). No sube ni elimina la outbox para aparentar éxito. Los fallos reales se mantienen como fallo reintentable; el RPC de refresco registra errores mediante SecureLog.

Hay además defectos **preexistentes de septiembre**, no una regresión de los triggers de eliminación desplegados hoy:

- Logcat S24 previo: `update_profile_preferences` devuelve `42501: permission denied for schema private` (`s24-fix-before-logcat.txt`). El rol `profile_preferences_executor` tenía permisos sobre receipts pero no USAGE sobre su esquema; el error se reproduce al compilar la declaración de receipt, antes de ejecutar triggers.
- Logcat A16 con el cliente corregido: `ensure_profile` devuelve `42501: permission denied for schema auth` (`a16-fix-after-logcat.txt`, entradas 12:32:24/12:33:07/12:33:29). Ambos ejecutores de perfil carecían de USAGE en auth, confirmado por el coordinador también en remoto. La restauración muestra seis módulos completados y Perfil fallido, conservando el error real.
- Al resolver los permisos localmente aparece `invalid input syntax for type bytea`: el hash de `update_profile_preferences` convierte texto con barras invertidas directamente a bytea. Se cambia a `convert_to(..., 'UTF8')`; mismo protocolo de revisión e idempotencia. También se reproduce con los guards de eliminación retirados dentro de una transacción aislada (`profile-rpc-without-deletion-triggers.txt`).

Migración nueva preparada: `supabase/migrations/20261103172646_profile_preferences_private_usage.sql`. No modifica migraciones desplegadas. No requiere supabase_admin ni grants sobre auth: un helper **privado**, `private.profile_session_uid()`, propiedad de postgres, SECURITY DEFINER y search_path vacío, devuelve exclusivamente `auth.uid()`. Solo los dos ejecutores pueden llamarlo; los RPC conservan firma/owner/RLS y usan ese helper. USAGE private se concede únicamente a esos ejecutores, sin grants de tablas privadas ni membresía authenticated/service_role. La migración **no fue aplicada al remoto**: el coordinador hará ese paso y la nueva validación real.

`ProfileRestoreRoomTest` cubre hidratación repetida con PENDING/WAITING_FOR_AUTH/IN_FLIGHT/CONFLICT, campos remotos sin edición local, outbox intacta, revisión preservada y fallo real frente a consulta exitosa. El fixture usa fechas exactas para evitar diferencias de precisión de Instant en Room.

### QA-S9-02: defecto de prueba corregido

**PASS en A16 y S24: AnalyticsScreenTest, OK (6 tests) en cada teléfono.** `PeriodCard` agrupa descendientes mediante `semantics(mergeDescendants = true)`: la prueba buscaba el aviso hijo en el árbol combinado. Se usa `useUnmergedTree = true` y `performScrollTo()` antes de verificar aviso y fechas, manteniendo `assertIsDisplayed`. No fue necesario cambiar UI ni texto. Evidencia: `a16-fix-analytics.txt` (9.468 s), `s24-fix-analytics.txt` (7.041 s).

### Matriz de re-verificación

| Comprobación | Resultado | Evidencia / límite |
|---|---|---|
| Suite Android completa | PASS | 1596 tests, 294 clases, 0 fallos, 0 errores, 3 omitidos; `fix-unit-summary.json`, `fix-build-unit-final.log`: BUILD SUCCESSFUL |
| assembleDebug + assembleDebugAndroidTest | PASS | mismo log; `fix-gradle-exit.txt` confirma exit 0 con tareas ya al día (sin repetir tests verdes) |
| Instalación conservando datos | PASS ambos | `adb -s SERIAL install -r` devolvió Success para app y androidTest; se reinstaló app A16 al añadir diagnóstico del RPC |
| AnalyticsScreenTest | PASS ambos | runner explícito `am instrument -w -e class com.kipu.app.feature.analytics.presentation.AnalyticsScreenTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`, 6/6 cada uno |
| pgTAP RPC perfil con authenticated | PASS local | 11/11; `profile-rpc-after-final.txt`; clone nuevo `kipu_s9_profile_final_20261103`, migración aplicada como postgres sin grant option de auth y sin USAGE auth para ejecutores |
| pgTAP esquema de perfil | PASS local | 8/8; `profile-schema-final.txt` |
| pgTAP eliminación | PASS local | 27/27; `profile-rpc-deletion-final.txt`; también 27/27 en clone control sin migración nueva |
| Restaurar dos veces | FAIL A16; pendiente S24 | A16 `a16-fix-restore-first-complete.*`, `a16-fix-restore-second-final.*`: seis módulos completados, Perfil fallido por auth/42501; se probó en A16 por indicación del coordinador al estar S24 en uso humano |
| S24: pendientes y datos conservados | PASS | outbox 1, hash de outbox y caché de perfil intactos; 9 transacciones / 5 financial_movements / 9 ledger / 4 cuentas / 2 tarjetas sin cambios |
| A16: recuperación e integridad | PASS acotado | 7 → 9 transacciones, 0 → 3 financial_movements al recuperar registros faltantes; ledger 9, cuentas 4, tarjetas 2; no se afirma convergencia completa con las 5 financial_movements del S24 |
| Saldos y propietario | PASS ambos | cuatro proyecciones por teléfono conservan sus saldos, mismos valores entre teléfonos; owner fingerprint `1a252f6a742b`; `fix-device-summary.json` |
| Sesión real y animaciones | PASS ambos | ninguna desinstalación, pm clear, cierre de sesión ni eliminación; acceso real A16 y copias privadas confirman propietario conservado; tres escalas por teléfono verificadas 1.0 en `fix-device-summary.json`; no se desplazó la app en uso del S24 al cierre |

La suite de eliminación inicialmente dio 26/27 tanto en el clone corregido como en el control sin migración: el clone contenía una receipt ajena al fixture y el assert #10 contaba toda la tabla. Se acotó ese assert al request_id de la prueba; ambas variantes pasan 27/27 (`profile-rpc-deletion-control-final.txt`, `profile-rpc-deletion-after-final.txt`). No era una regresión de la migración de perfil.

**Pendiente concreto:** aplicar la migración nueva al remoto y repetir dos restauraciones completas en S24 cuando esté disponible, comprobando 7/7 módulos, conteos/saldos/idempotencia y conservación de su pendiente. No se certifica ese resultado antes de la ejecución. Las limitaciones de cobertura iniciales siguen vigentes.

## Cierre tras despliegue remoto y corrección del DTO — 2026-11-03

Este apartado reemplaza el estado pendiente de restauración del apartado anterior. El coordinador confirmó el despliegue remoto de `20261103172646_profile_preferences_private_usage`; este worker no ejecutó cambios remotos directos.

**QA-S9-01: PASS en restauración y envío del pendiente.** Tras corregir los permisos, el primer intento en A16 reveló otro defecto: `ensure_profile` devolvía `display_name: null`, permitido por la columna remota, mientras `RemoteProfileDto` exigía String. Logcat privado `a16-postdeploy-profile-logcat.txt` acredita el error de deserialización sin 42501. Se hizo nullable únicamente `displayName` y se normaliza con `orEmpty()` al construir la caché; la hidratación sigue preservando campos pendientes. Se revisaron las columnas del esquema local equivalente: `display_name` es nullable; `timeZone` y `updatedAt` ya admitían null en el DTO. No se aplica coerción global que oculte valores inválidos de otros campos. `RemoteProfileDtoTest` cubre nombre nulo, ausente y con valor.

| Validación final | A16 | S24 | Evidencia privada |
|---|---|---|---|
| Restauración completa, primera ejecución con DTO corregido | PASS 7/7 | PASS 7/7 | `a16-postdeploy-dto-restore-first.*`, `s24-postdeploy-dto-restore-first.*` |
| Restauración completa, segunda ejecución | PASS 7/7 | PASS 7/7 | `a16-postdeploy-dto-restore-second.*`, `s24-postdeploy-dto-restore-second.*` |
| Perfil: fallo real no ocultado | PASS | PASS | El DTO anterior siguió mostrando fallo; con respuesta válida ambos muestran Perfil completado |
| Logcat tras instalar DTO corregido | PASS | PASS | `*-postdeploy-dto-logcat.txt`, desde 12:42:00 Lima: cero líneas de error de refresh/sync de perfil y cero 42501 de perfil |
| Idempotencia de dos restauraciones | PASS | PASS | `postdeploy-device-summary.json`: conteos constantes en baseline/primera/segunda, IDs únicos en transacciones, hechos financieros y ledger, saldos invariables |
| Outbox de perfil | 0 pendientes | 1 → 0, revisión 1/PENDING → 2/SYNCED | `s24-postdeploy-profile-sync-summary.json`, copias Room `s24-postdeploy-before-*` y `s24-postdeploy-dto-sent-*` |
| Sesión real y escalas | PASS | PASS | Mismo owner fingerprint `1a252f6a742b`; tres escalas por teléfono en 1.0, `postdeploy-final-state.json` |

El pendiente S24 estaba en backoff de WorkManager de más de cuatro horas. Forzar el job Android 355 en el namespace `androidx.work.systemjobscheduler` no eliminó el backoff interno. Se volvió a pulsar el tema **Claro ya seleccionado**, sin modificar sus valores: la UI reencoló mediante el scheduler normal y la outbox fusionó la solicitud con la operación PENDING original. Tras el envío, la fila desapareció y el perfil quedó revisión 2/SYNCED. Se compararon los cinco campos del payload original con la caché posterior (`hide_balances`, `display_name`, `currency_code`, `month_start`, `theme_mode`): todos conservan exactamente el valor pendiente anterior. No se borró la outbox por shell ni se escribió contra la cuenta real por fuera de la app.

Los conteos comunes convergen: **9 transacciones, 9 ledger, 4 cuentas, 2 tarjetas, 2 deudas, 0 cuotas, 0 metas, 0 presupuestos y 0 recurrencias** en ambos teléfonos; las cuatro proyecciones de saldo coinciden entre teléfonos y permanecen iguales antes y después. Se conserva una diferencia histórica de `financial_movements`: A16 tiene 3 OPENING y S24 esos mismos 3 más 2 DEBT_DISBURSEMENT/POSTED preexistentes. No hubo incremento durante estas restauraciones. La igualdad total de esa tabla sigue pendiente de reconciliación de procedencia; no se eliminan hechos locales para aparentar convergencia. Detalle privado con fingerprints, sin datos personales: `postdeploy-financial-differences.json`.

Validación host final: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`, **exit 0, BUILD SUCCESSFUL**, 1599 pruebas en 295 clases, cero fallos/errores y 3 omitidas (`postdeploy-dto-build.log`, `postdeploy-unit-summary.json`). App y androidTest se instalaron mediante el ADB del SDK con `install -r`, Success en ambos teléfonos. No se ejecutó connected*, desinstalación, pm clear, cierre de sesión ni eliminación de cuenta. No se tocaron otras apps. Los casos NO VERIFICADO de la validación inicial siguen fuera de esta certificación acotada.

## Procedencia de los desembolsos locales — 2026-11-03

**Resultado: diferencia histórica explicada; no hay defecto de sincronización/restauración demostrado en estas dos operaciones.** Este apartado resuelve el pendiente de procedencia del apartado anterior, sin aprobar integralmente el sprint. Se trabajó en `main`, sin comandos Spec Kit, cambios de producción, migración, instalación, push ni modificación remota por este worker.

### Causa y evidencia

Las dos filas adicionales del S24 son representaciones auxiliares legítimas de aperturas NEW_CASH_FLOW, no pendientes ni huérfanos. En `DebtLocalDataSource.openDebt`, la transacción usa `operation_id` como ID, escribe un asiento y después una copia DEBT_DISBURSEMENT/POSTED mediante `FinancialMovementDao.insertRaw`, que no genera otro asiento. Se registra además el evento DISBURSEMENT, el recibo y la outbox OPEN_DEBT. En el baseline anterior al sprint, ambas transacciones/recibos ya estaban SYNCED; en la copia actual del S24 las dos filas de `debt_command_outbox` también están SYNCED. El estado POSTED del hecho auxiliar no representa por sí mismo un estado de transporte.

Se consultó directamente MCP `kipu.execute_sql`: el remoto tiene exclusivamente tres OPENING/POSTED en `public.financial_movements`, pero **sí tiene las dos transacciones DEBT_DISBURSEMENT/CONFIRMED correspondientes**, cada una con un evento de apertura y un único asiento. Las uniones remotas validan propietario común, NEW_CASH_FLOW, importe y fecha evento/transacción, moneda, cuenta y signo PAYABLE/RECEIVABLE del asiento. Las huellas de identidad y los hashes de importe/moneda/cuenta coinciden exactamente con los registros locales; no se publican esos datos ni hashes financieros en el repositorio.

La definición desplegada de `private.open_payable_debt_v1` y `private.open_receivable_debt_v1` menciona `internal.ledger_entries` y no `financial_movements`, coherente con las migraciones versionadas de apertura. `SyncDebtChangesWorker` confirma el mismo comando mediante APPLIED/DUPLICATE y actualiza recibo/outbox; no hay un segundo envío de la fila auxiliar. `DebtChangeFeedApplier` recupera deuda/evento y MOV recupera transacción/ledger. `SupplementaryHydration.financialFacts` lee las filas realmente existentes en remoto: no falta ninguna de esas filas remotas en A16.

Las copias previas de ambos teléfonos acreditan las mismas dos transacciones, eventos y efectos, con un único asiento por operación. S24 conserva además las dos copias auxiliares; A16 no las necesita para obtener el mismo saldo e historial. La API `observeMovementsByAccount` puede leer esas copias, pero no tiene consumidores de producción en el código examinado; esta observación no certifica futuros consumidores ni otros tipos de movimiento. No se elimina historia válida, no se reenvía OPEN_DEBT y no se crean hechos remotos para igualar conteos. El export JSON conserva la representación local permitida; no se promete igualdad física de todas las tablas entre dispositivos.

### Comprobaciones y límites de esta investigación

| Comprobación | Resultado y alcance |
|---|---|
| Baseline S24 y copia postdeploy | Dos hechos auxiliares coherentes con sus transacciones, eventos, signo, importe, moneda, cuenta y propietario; recibos OPEN_DEBT/SYNCED |
| Copia A16 de segunda restauración postdeploy | Mismas dos operaciones y eventos, con un asiento por operación y sin las copias auxiliares; transacciones SYNCED |
| Remoto actual | Dos operaciones CONFIRMED con efectos coherentes, tres OPENING/POSTED; handlers de apertura sin escritura de `financial_movements` |
| S24 actual, lectura privada mediante ADB del SDK | 9 transacciones, 9 ledger, 5 financial_movements, 4 cuentas, 2 tarjetas, 2 deudas y 2 eventos; cuatro saldos conservados; un único propietario igual al baseline |
| Comparación S24 actual con postdeploy | Hechos, ledger, saldos, deudas y eventos idénticos; transacciones/cuentas/tarjetas difieren únicamente en `updated_at`, sin cambio financiero |
| Pendientes S24 | Dos filas históricas de outbox de deuda SYNCED; recibos conservados. No se altera ninguna cola |
| Pruebas host focalizadas | `:app:testDebugUnitTest --tests com.kipu.app.feature.portability.SupplementaryHydrationRoomTest --tests com.kipu.app.feature.portability.RestoreAllTest`: **exit 0, BUILD SUCCESSFUL**, 2 pruebas, cero fallos/errores/omitidas; hidratación repetida sin duplicar ni contabilizar dos veces y ejecución de módulos tras fallo |
| Verificación reproducible de datos privados | `python debt-investigation-verify.py`: **exit 0, PASS**; contrasta cuatro copias locales con las dos operaciones remotas, hashes de efecto, handlers y cambios exclusivos de timestamps; salida `debt-investigation-verification.json` |
| Nuevas restauraciones reales | No se repitieron: no hubo fix funcional; siguen como evidencia las dos restauraciones 7/7 por teléfono del apartado anterior. A16 no aparece en ADB durante esta investigación; no se afirma comprobación actual de su sesión/caché |
| Escalas y sesión | No se modificaron; S24 lee 1.0 en las tres escalas y conserva el propietario. No se ejecutaron logout, uninstall, pm clear ni borrado de cuenta |
| Categoría QA | El coordinador comunicó autorización del usuario y borrado remoto condicionado. Consulta remota posterior: cero filas con el nombre de prueba; la copia actual S24 también tiene cero. No se borró caché por SQL ni se comprobó la caché actual A16; no se certifica ausencia futura de recreación |

Evidencia privada adicional en el directorio externo ya indicado: `debt-facts-investigation-private.json`, `debt-investigation-remote-mcp.json`, `debt-investigation-effect-digests.json`, `debt-investigation-s24/` (Room/WAL y extracción privada), `debt-investigation-device-summary.json`, `debt-investigation-focused-tests.log` y `debt-investigation-focused-exit.txt`. El resumen privado conserva también la comparación estricta que detectó las diferencias de timestamps; esas diferencias se inspeccionaron por columna antes de clasificarlas como metadatos. No se añade ninguna base, payload, captura ni export financiero a Git.

Se actualizaron manualmente los artefactos APS/DEU y el contrato de sync MOV para dejar trazable esta explicación, sin nuevos requisitos de producto. Permanecen abiertos los casos NO VERIFICADO y los demás pendientes del sprint; esta investigación no amplía su cobertura.

## Cobertura focalizada adicional — 2026-11-03

Base `a10e7540`, trabajo secuencial en main. **PASS focalizado, aceptación PARCIAL.** Informe detallado: [EP-INF verification-report](009-ep-inf-informacion-financiera/verification-report.md). Evidencia privada en el directorio externo ya indicado, prefijo nuevo `coverage2-`. Solo pruebas y documentación, sin cambios remotos, datos de producción, sesión ni entitlement.

| Criterio pendiente / dispositivo | Resultado actual | Evidencia y límite |
|---|---|---|
| HU-41 T027: semántica y texto 200 %, S24 | PASS parcial | Inicio-patrimonio, Calendario y Análisis con fixtures Compose/densidad 2.0, acciones expandir/abrir/máscara, estado previsto/realizado y destino no disponible. 10 pruebas de componentes en coverage2-s24-final-instrumentation.txt; sin garantía de ausencia de todo truncamiento |
| T027: TalkBack, tres journeys completos | NO VERIFICADO | Google y Samsung TalkBack instalados, servicio desactivado originalmente (null), conservado. Sin lectura hablada, orden de foco o gestos reales; T027 abierta |
| T028: navegación y errores/reintentos/propietario | PASS parcial | 5 pruebas instrumentadas del grafo y 11 JVM: entrada directa/volver, argumentos/deep link de historial; retry recupera, error genérico, cambio propietario cancela fuente anterior, bloqueo y NoOwner. Fronteras separadas con fixtures; faltan journeys integrados con sesión real, T028 abierta |
| HU-41 Free | PASS aislado; real NO VERIFICADO | AnalyticsViewModelTest con plan/sesión de prueba. Previa AnalyticsScreenTest 6/6 por teléfono sigue válida; no se repitió ni se cambió plan real |
| Compartir imagen, frontera URI Android S24 | PASS aislado | ReceiptShareUriGrantTest: proveedor no exportado en UID emisor, rechazo inicial, concesión revocable desde proceso emisor Android, ACTION_SEND parseado y ContentResolver/ML Kit leen imagen sintética; revocar vuelve a rechazar. Sin MainActivity/selector/candidatos/movimientos: no acredita flujo completo desde Yape |
| HU-49 notificación real + OCR ambos órdenes / ocho muestras privadas | NO VERIFICADO | Sin notificaciones nuevas; muestras ausentes. El fixture URI no acredita estos criterios |
| HU-06 crear cambio offline y recuperar remoto | NO VERIFICADO en esta tanda | No se alteró red ni se crearon cambios reales. La recuperación del pendiente histórico sí consta en el cierre postdeploy anterior; no equivale a este escenario nuevo |
| HU-06 eliminación UI completa desechable | NO VERIFICADO | Sin sustituir sesión real; cobertura API previa conservada |
| A16 | NO VERIFICADO en esta tanda | Ausente en ADB; se preservan resultados históricos |
| Conservación S24 | PASS | coverage2-all-tables-comparison.json: 66 tablas iguales antes/después, incluidas colas, saldos y preferencias; coverage2-integrity-comparison.json: settings iguales. Tres escalas 1.0 restauradas en finally; sin cambios de font_scale o servicio accesible; app reabierta al terminar |
| GATE-ANALYTICS-04 / revisión cruzada | T021 PENDIENTE; revisión realizada | No se localizó aprobación del catálogo; T021 sigue abierta. Opus revisó independientemente (`3e5d8d8e`); cierra el componente de revisión de T029, no la aceptación integral |

Ejecución final: **S24 16/16 instrumentadas, 6.985 s, runner OK; host 11/11, cero fallos/omisiones; builds finales exit 0**. únicamente APK androidTest reinstalado mediante install -r; sin suite global ni repetición de AnalyticsScreenTest/AnalyticsDaoTest ya verdes. El primer intento instrumentado se interrumpió tras crash del proveedor test (AndroidX ausente en APK test independiente); se corrigió con proveedor/receptor Java Android puro. Compilación intermedia fallida por BOM Java corregida; logs fallidos conservados sin contarlos como PASS. El informe EP-INF detalla evidencia y comandos.

No se encontró defecto nuevo de producción. Siguen abiertas T021/T027–T030 y los criterios NO VERIFICADO; pruebas de semántica, grafo y URI con fixtures en hardware no sustituyen proveedor Yape ni revisión cruzada.

## Revisión cruzada independiente — 2026-11-03

Revisor: Opus, independiente de los workers implementadores. Revisión directa de `35858567..e7702012` (31 commits) y de la exactitud documental de `e7702012`, sin repetir suites, sin cambios remotos ni en dispositivos.

**Veredicto técnico acotado: sin defectos bloqueantes de código; aceptación PARCIAL; no APPROVED integral.**

Revisado sin hallazgos:
- Restauración 7/7 con guardas de propietario, e hidratación de perfil que preserva la outbox y su revisión; DTO `display_name` nullable.
- Analítica: invariantes, reembolsos, exclusiones y propietario.
- Correlación Yape por referencia validada por hechos en ambos órdenes, y sugerencia única sin referencia; migración Room 36→37.
- Serialización de los escritores frente al purge y aislamiento del receipt.

La diferencia de `financial_movements` (dos proyecciones auxiliares OPEN_DEBT locales) y la eliminación autorizada de la categoría de prueba quedan explicadas, sin acción.

| Severidad | Archivo / línea | Trigger | Efecto | Corrección recomendada |
|---|---|---|---|---|
| Media | `20261103162347_account_deletion_concurrency_fix.sql:22-24`, `delete-account/index.ts:66-67` | Receipt `data_deleted` con fallo en Auth, y capacidad local perdida o uso desde otro dispositivo | 202 `in_progress` permanente; Auth persiste con las escrituras bloqueadas y sin autoservicio | Completar desde el servidor el receipt existente del mismo propietario reautenticado, o un runbook (worker implementador) |
| Baja | `index.ts:51-58`, `AccountDeletion.kt:129-134` | Segundo dispositivo después de que el primero completa | ERROR con los datos locales conservados | Estado "cuenta eliminada" con limpieza local guiada |
| Baja (doc) | `specs/011.../spec.md` FR-012 y Key Entities | Room v37 | El texto dice índice único; en v37 es no único y la PK es owner+fuente+event_id | Reconciliar el requisito con el Product Owner; se añadió una nota y FR-012 no se modificó |
| Baja (doc) | `account_deletion_concurrency.test.sql` | Informes | No consta el resultado de la suite pgTAP de concurrencia (plan 8) | Registrar su ejecución en la próxima tanda local |
| Trivial | `SecureShareFixtureProvider.java:44` | `git diff --check` | Línea en blanco al final del archivo | Corregido en este commit; sin rerun porque es solo whitespace |

Correcciones documentales de esta revisión:
- El veredicto de EP-APS distingue ahora la restauración en hardware de la eliminación verificada solo por API.
- El informe EP-AUT describe el índice real de v37.
- La nota raíz de `verification-report.md` marca como superado el "despliegue pendiente".

Siguen pendientes: T021 GATE-ANALYTICS-04, TalkBack hablado, el A16 en la última tanda, la UI de compartir desde Yape real, la notificación viva en ambos órdenes, las ocho muestras privadas, la eliminación con la UI completa y una cuenta desechable, y un cambio offline nuevo.

## Corrección backend de recuperación HU-06 B — 2026-11-03 Lima

MEDIUM de revisión Opus corregido: migración aditiva `20261104021333_account_deletion_owner_recovery` y delete-account v3 desplegadas/verificadas por MCP kipu, historial remoto alineado, verify_jwt=false con JWT/AMR validados en el handler. El propietario reautenticado recupera el mismo recibo data_deleted sin repetir purge; aliases privados permiten reconciliar al segundo cliente tras desaparecer Auth. Android ya conserva locales hasta completed verificable; no requirió cambios ni instalación.

Validación: Deno 15/15; pgTAP 100/100 (eliminación 27, concurrencia plan8 8, recuperación 21, perfil 8, compras 36); siete conexiones SQL concurrentes conservaron un recibo y seis aliases; Android focalizado 10/10. API remota con dos usuarios desechables: 403 propietario ajeno y AMR realmente vencido; login reciente/capacidad nueva recuperó y completó; capacidades original/nueva/segundo dispositivo reconciliaron completed sin Auth, incluidos tres reintentos paralelos; Admin 404 y cero filas en 60 tablas. Ambas cuentas de prueba eliminadas, sin tocar cuentas reales ni sesiones de teléfonos. Fallo Auth inyectado en Deno; remoto partió del límite data_deleted/Auth vivo preparado por RPC, sin inducir indisponibilidad del proveedor. Preparación concurrente validada localmente; concurrencia remota verifica reconciliación posterior al cierre.

Evidencia privada: `C:/Users/Alume/s9-device-validation-20261103/deletion-recovery-*`; [detalle de verificación](001-ep-aps-acceso-perfil-seguridad/verification-report.md). MEDIUM y LOW de segundo cliente resueltos para las nuevas solicitudes autorizadas con alias; un 401 legacy sin recibo verificable nunca autoriza borrar caché. LOW documental de plan8 cerrado con 8/8. FR-012 sigue con contradicción específica documentada en EP-AUT: D1/opción A no aprueba explícitamente la cardinalidad física ante hechos incompatibles, por lo que no se modifica el requisito ni Yape. **Aceptación del sprint PARCIAL**; todos los pendientes del párrafo anterior siguen abiertos.


## Journeys integrados de lectura y cierre factible — 2026-11-03 (`closure3`)

Base `main` / `9552a613`. **PASS de lectura S24, aceptación PARCIAL**. [Informe completo](009-ep-inf-informacion-financiera/verification-report.md#cierre-factible-de-journeys-reales--2026-11-03-closure3), evidencia privada externa con prefijo `closure3-`. Actualiza el pendiente de navegación real de coverage2; conserva sus pruebas verdes sin repetirlas.

| Criterio | Resultado actual |
| --- | --- |
| T028 Inicio/historial/volver | PASS con sesión real, sin editar movimientos |
| T028 Calendario/mes anterior/actual/evento previsto/detalle de tarjeta/volver | PASS integrado, máscara local y aviso de no cargo automático; sin pagos |
| T028 Análisis/periodo parcial/máscara/scroll/volver | PASS integrado con sesión real; sin alterar entitlement |
| T028 errores/reintentos/cambio propietario | Pendiente integrado; pruebas aisladas previas conservadas. No se provocaron fallos ni cambiaron cuentas reales |
| T027 TalkBack hablado/foco/gestos | NO VERIFICADO: sin herramienta acreditada de escucha/captura segura de audio; no habilitado para deducir voz desde logs |
| Integridad S24 | PASS: 67 tablas comparadas, 65 idénticas; finanzas y diez outboxes íntegramente iguales, propietarios conservados, ajustes originales exactos y sesión operativa al final |
| Preferencia máscara Inicio | Restaurada a false/SYNCED, revisión 2→4 y outbox perfil 0→0. Desviación de solo lectura: el toggle persistió dos cambios por UI antes de identificar ese comportamiento; no se afirma cero escrituras remotas. Solo cambian metadatos de perfil y renovación de feature_access_cache |
| Entorno desechable UI eliminación | NO VERIFICADO: emulator-5554 listado pero pm list users timeout 8 s, boot_completed y servicio package timeout 6 s cada uno; S24 solo usuario Propietario, sin perfil de prueba. No crear infraestructura ni sustituir login real |
| A16 | Ausente en ADB; resultados históricos preservados |
| Yape real, notificación viva ambos órdenes, ocho muestras | NO VERIFICADO: sin acceso automático a Yape o datos privados de otra app; sin simulaciones sobre cuenta real |
| T029 | Componente revisión cerrado por Opus `3e5d8d8e`; aceptación integral pendiente |
| Decisiones de producto | RESUELTAS por el Owner: T021 GATE-ANALYTICS-04 aprobada para alcance incremental; EP-AUT FR-012 reconciliado con Room v37 (PK owner+fuente+event_id, índice no único; eventos incompatibles conservados para revisión sin fusión automática ni duplicación contable). Ver [decisiones del Owner](sprint-9-owner-decisions.md) |

No hubo nuevos tests de código, builds, instalaciones, despliegues o cambios remotos por API/SQL; no se repiten Deno15/pgTAP100/Android10 ni pruebas API verdes. Solo se recorrió UI y se verificaron las copias privadas con `closure3-integrity.py` (exit 0). Se guardaron snapshots y comparación de ajustes mediante finally, sin activar TalkBack. T021 queda CUMPLIDA tras la decisión del Owner; no se declara APPROVED integral ni se cierran T014/T027–T030 por esta cobertura acotada.

## Formalización de decisiones del Product Owner — 2026-11-03 [2026-11-04 UTC]

Documento oficial de decisiones: [specs/sprint-9-owner-decisions.md](sprint-9-owner-decisions.md). El Product Owner emitió dos decisiones explícitas en el chat de Sprint 9 de Kipu:
1. **HU-41 / T021 (GATE-ANALYTICS-04):** Aprobación del alcance incremental implementado en la pestaña Análisis (gasto operativo neto de reembolsos, desglose por categoría y patrimonio, monedas separadas sin conversión, zona horaria del teléfono, selección de meses con mes actual hasta hoy parcial, comparación de totales con «Sin base de comparación» si base cero, Free mes actual y Premium históricos/comparación/filtros; excluyendo rangos personalizados, comparación por categoría y otros indicadores específicos de crédito/deuda/metas). Se marca T021 como **CUMPLIDA**. Aceptación formal de este alcance incremental, sin constituir prueba de todo el backlog ni completitud de pruebas del sprint.
2. **HU-49 / EP-AUT (FR-012):** Aprobación explícita de «Conservar eventos incompatibles para revision». Reconcilia FR-012, la entidad `capture_evidence`, `plan.md` y `tasks.md` con Room v37 (PK `(owner_id, source, event_id)`, índice no único `(owner_id, reference, source)` e índice `(owner_id, root)`). Misma referencia solo fusiona compatibles según los invariantes vigentes; incompatibles se conservan separados para revisión del usuario sin fusión automática ni duplicación de efectos contables en el ledger.

**Estado resultante de pendientes del Sprint 9 (actualizado por decisión del Owner 2026-11-08):**
- **T021:** CUMPLIDA para alcance incremental. **FR-012:** reconciliada con Room v37.
- **T027:** RETIRADA del alcance por el Owner; no se marca cumplida.
- **T028 y T014:** CUMPLIDAS por prueba manual del Owner (journeys integrados y lectura offline real en Inicio).
- **T029:** ABIERTA y a cargo del Owner; no se declara aceptación integral.
- **Criterios de hardware:** compartir UI real desde Yape, notificación viva y crear cambio offline fueron probados manualmente por el Owner. Recuperación remota del cambio, eliminación UI con cuenta desechable, A16 y ocho muestras privadas quedan a cargo del Owner y sin cerrar.
- **Backend recovery v3:** Validado (15 Deno, 100 pgTAP, 10 Android API QA) en reportes existentes; no se repiten pruebas.
- **Veredicto general:** aceptación del Owner para lo probado; T029 y los puntos reservados al Owner siguen pendientes. **NO se declara APPROVED integral.**

## Addendum de estado por decisión del Owner — 2026-11-08 (Lima)

Según decisión explícita del Product Owner, T027 (TalkBack hablado) se retira del alcance; T028 (journeys integrados de errores/reintentos/cambio de propietario) y T014 (lectura offline real en Inicio) se consideran cumplidas por prueba manual del Owner. El Owner también confirma que probó y funcionan compartir UI desde Yape, notificación viva de Yape y crear un cambio offline. Estos resultados se registran como aceptación manual comunicada por el Owner, sin añadir evidencia independiente del agente ni inferir despliegues.

Siguen **a cargo del Owner, fuera del flujo de agentes, y no cerrados**: T029 aceptación integral (el Owner anticipa mejoras posteriores a los diez sprints), recuperación remota del cambio offline, eliminación de cuenta por UI con cuenta desechable, pruebas en Samsung Galaxy A16 y validación con las ocho muestras privadas. El Sprint 9 queda con aceptación del Owner para lo probado, pero **no se declara APPROVED integral**. Ver el registro formal en [decisiones del Owner](sprint-9-owner-decisions.md).

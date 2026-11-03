# Sprint 9 — validación funcional en dispositivos reales

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


## Re-verificaci?n de incidencias ? 2026-11-03

Alcance: correcciones QA-S9-01/HU-06 A y QA-S9-02/HU-41 en `main`, sin push ni despliegue remoto por este worker. Este apartado actualiza los dos hallazgos; no convierte los casos NO VERIFICADO anteriores en PASS. Evidencia privada en el mismo directorio externo, prefijo `fix-` o `profile-rpc-`.

### QA-S9-01: causa ra?z y correcci?n preparada

**Estado: cliente corregido y backend corregido/probado localmente; restauraci?n contra remoto todav?a FAIL.** El S24 ten?a una operaci?n de perfil PENDING: `RestoreUserData` evaluaba `pending == 0 && refreshProfile(...)`, devolviendo falso antes de consultar remoto. Quitar solo esa condici?n habr?a sobrescrito el perfil local: `refreshProfile` reemplazaba la cach? completa. Ahora consulta realmente el servidor, verifica sesi?n/propietario y aplica los campos remotos en una transacci?n Room conservando campos incluidos en outboxes sin resolver, revisi?n local, estado local y operaci?n pendiente (incluido CONFLICT). No sube ni elimina la outbox para aparentar ?xito. Los fallos reales se mantienen como fallo reintentable; el RPC de refresco registra errores mediante SecureLog.

Hay adem?s defectos **preexistentes de septiembre**, no una regresi?n de los triggers de eliminaci?n desplegados hoy:

- Logcat S24 previo: `update_profile_preferences` devuelve `42501: permission denied for schema private` (`s24-fix-before-logcat.txt`). El rol `profile_preferences_executor` ten?a permisos sobre receipts pero no USAGE sobre su esquema; el error se reproduce al compilar la declaraci?n de receipt, antes de ejecutar triggers.
- Logcat A16 con el cliente corregido: `ensure_profile` devuelve `42501: permission denied for schema auth` (`a16-fix-after-logcat.txt`, entradas 12:32:24/12:33:07/12:33:29). Ambos ejecutores de perfil carec?an de USAGE en auth, confirmado por el coordinador tambi?n en remoto. La restauraci?n muestra seis m?dulos completados y Perfil fallido, conservando el error real.
- Al resolver los permisos localmente aparece `invalid input syntax for type bytea`: el hash de `update_profile_preferences` convierte texto con barras invertidas directamente a bytea. Se cambia a `convert_to(..., 'UTF8')`; mismo protocolo de revisi?n e idempotencia. Tambi?n se reproduce con los guards de eliminaci?n retirados dentro de una transacci?n aislada (`profile-rpc-without-deletion-triggers.txt`).

Migraci?n nueva preparada: `supabase/migrations/20261103172646_profile_preferences_private_usage.sql`. No modifica migraciones desplegadas. No requiere supabase_admin ni grants sobre auth: un helper **privado**, `private.profile_session_uid()`, propiedad de postgres, SECURITY DEFINER y search_path vac?o, devuelve exclusivamente `auth.uid()`. Solo los dos ejecutores pueden llamarlo; los RPC conservan firma/owner/RLS y usan ese helper. USAGE private se concede ?nicamente a esos ejecutores, sin grants de tablas privadas ni membres?a authenticated/service_role. La migraci?n **no fue aplicada al remoto**: el coordinador har? ese paso y la nueva validaci?n real.

`ProfileRestoreRoomTest` cubre hidrataci?n repetida con PENDING/WAITING_FOR_AUTH/IN_FLIGHT/CONFLICT, campos remotos sin edici?n local, outbox intacta, revisi?n preservada y fallo real frente a consulta exitosa. El fixture usa fechas exactas para evitar diferencias de precisi?n de Instant en Room.

### QA-S9-02: defecto de prueba corregido

**PASS en A16 y S24: AnalyticsScreenTest, OK (6 tests) en cada tel?fono.** `PeriodCard` agrupa descendientes mediante `semantics(mergeDescendants = true)`: la prueba buscaba el aviso hijo en el ?rbol combinado. Se usa `useUnmergedTree = true` y `performScrollTo()` antes de verificar aviso y fechas, manteniendo `assertIsDisplayed`. No fue necesario cambiar UI ni texto. Evidencia: `a16-fix-analytics.txt` (9.468 s), `s24-fix-analytics.txt` (7.041 s).

### Matriz de re-verificaci?n

| Comprobaci?n | Resultado | Evidencia / l?mite |
|---|---|---|
| Suite Android completa | PASS | 1596 tests, 294 clases, 0 fallos, 0 errores, 3 omitidos; `fix-unit-summary.json`, `fix-build-unit-final.log`: BUILD SUCCESSFUL |
| assembleDebug + assembleDebugAndroidTest | PASS | mismo log; `fix-gradle-exit.txt` confirma exit 0 con tareas ya al d?a (sin repetir tests verdes) |
| Instalaci?n conservando datos | PASS ambos | `adb -s SERIAL install -r` devolvi? Success para app y androidTest; se reinstal? app A16 al a?adir diagn?stico del RPC |
| AnalyticsScreenTest | PASS ambos | runner expl?cito `am instrument -w -e class com.kipu.app.feature.analytics.presentation.AnalyticsScreenTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`, 6/6 cada uno |
| pgTAP RPC perfil con authenticated | PASS local | 11/11; `profile-rpc-after-final.txt`; clone nuevo `kipu_s9_profile_final_20261103`, migraci?n aplicada como postgres sin grant option de auth y sin USAGE auth para ejecutores |
| pgTAP esquema de perfil | PASS local | 8/8; `profile-schema-final.txt` |
| pgTAP eliminaci?n | PASS local | 27/27; `profile-rpc-deletion-final.txt`; tambi?n 27/27 en clone control sin migraci?n nueva |
| Restaurar dos veces | FAIL A16; pendiente S24 | A16 `a16-fix-restore-first-complete.*`, `a16-fix-restore-second-final.*`: seis m?dulos completados, Perfil fallido por auth/42501; se prob? en A16 por indicaci?n del coordinador al estar S24 en uso humano |
| S24: pendientes y datos conservados | PASS | outbox 1, hash de outbox y cach? de perfil intactos; 9 transacciones / 5 financial_movements / 9 ledger / 4 cuentas / 2 tarjetas sin cambios |
| A16: recuperaci?n e integridad | PASS acotado | 7 ? 9 transacciones, 0 ? 3 financial_movements al recuperar registros faltantes; ledger 9, cuentas 4, tarjetas 2; no se afirma convergencia completa con las 5 financial_movements del S24 |
| Saldos y propietario | PASS ambos | cuatro proyecciones por tel?fono conservan sus saldos, mismos valores entre tel?fonos; owner fingerprint `1a252f6a742b`; `fix-device-summary.json` |
| Sesi?n real y animaciones | PASS ambos | ninguna desinstalaci?n, pm clear, cierre de sesi?n ni eliminaci?n; acceso real A16 y copias privadas confirman propietario conservado; tres escalas por tel?fono verificadas 1.0 en `fix-device-summary.json`; no se desplaz? la app en uso del S24 al cierre |

La suite de eliminaci?n inicialmente dio 26/27 tanto en el clone corregido como en el control sin migraci?n: el clone conten?a una receipt ajena al fixture y el assert #10 contaba toda la tabla. Se acot? ese assert al request_id de la prueba; ambas variantes pasan 27/27 (`profile-rpc-deletion-control-final.txt`, `profile-rpc-deletion-after-final.txt`). No era una regresi?n de la migraci?n de perfil.

**Pendiente concreto:** aplicar la migraci?n nueva al remoto y repetir dos restauraciones completas en S24 cuando est? disponible, comprobando 7/7 m?dulos, conteos/saldos/idempotencia y conservaci?n de su pendiente. No se certifica ese resultado antes de la ejecuci?n. Las limitaciones de cobertura iniciales siguen vigentes.

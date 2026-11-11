# EP-INF — verificación focalizada de Sprint 9

Fecha de esta verificación: 2026-11-03, America/Lima. Base `main` / `a10e7540`; trabajo secuencial autorizado, artefactos editados a mano. **Veredicto de esa tanda: PASS focalizado, aceptación PARCIAL; no APPROVED integral.** La decisión posterior del Owner (2026-11-08) actualiza T014/T028 como cumplidas manualmente y T027 como retirada; T029 sigue abierta y reservada al Owner. Sin cambios de código de producción, base de datos o despliegue remoto en esta verificación. Ver [decisiones Sprint 9](../sprint-9-owner-decisions.md).

Pruebas versionadas en `8da42128` (`test(qa): cover analytics accessibility isolation and Android URI grants`). Sin push, PR ni merge remoto.

## Evidencia y ejecución

Evidencia privada fuera del repositorio: `C:/Users/Alume/s9-device-validation-20261103/`, prefijo nuevo `coverage2-`. No versionar bases, capturas, hashes financieros ni payloads.

| Frontera | Resultado | Evidencia / alcance |
|---|---|---|
| Host: AnalyticsViewModelTest, CalendarViewModelTest, HomeNetWorthViewModelTest | PASS 11/11, 0 omitidas | `coverage2-build.log`, `coverage2-unit-summary.json`; retry recupera fuente, error genérico sin detalle privado, fuente anterior cancelada al cambiar propietario, bloqueo/NoOwner, Free/Premium/expiración y estados de Inicio/Calendario. Sesiones/planes aislados |
| Build APK androidTest final | PASS, exit 0 | `coverage2-share-fixed-build.log`; assembleDebugAndroidTest. No se reconstruye/reinstala app de producción sin necesidad |
| S24: CalendarAndHomeAccessibilityTest | PASS 9/9 | Semántica realizado/previsto, eventos concurrentes, destino no disponible, retry/vacío/máscara; nuevos casos fuente 2.0 en Inicio y Calendario mantienen acciones de expandir/abrir y máscara |
| S24: AnalyticsAccessibilityTest | PASS 1/1 | Fuente Compose 2.0: aviso parcial alcanzable por scroll, acción de máscara etiquetada y gráfico sin importe sintético en texto/contentDescription renderizados. No verifica por sí solo todo truncamiento ni contenido fuera del viewport |
| S24: AnalyticsNavigationTest, CalendarNavigationTest, MovementHistoryNavigationTest | PASS 5/5 | Grafo real con TestNavHostController: entrada directa Análisis, singleTop/volver, Calendario/volver, argumentos cuenta/canal y deep link de historial. No renderiza destinos con sesión real |
| S24: ReceiptShareUriGrantTest | PASS 1/1 | Proveedor no exportado en UID del APK test; permiso revocable concedido al UID de Kipu por componente Android del emisor. ContentResolver rechaza antes/después de la concesión; ACTION_SEND/EXTRA_STREAM/ClipData → parser de intent → lectura URI/OCR ML Kit local → importe sintético esperado. No abre MainActivity, no persiste candidatos, no ejecuta confirmación ni ledger |
| Runner completo focalizado S24 | PASS 16/16, 6.985 s | `coverage2-s24-final-instrumentation.txt`; `coverage2-run-final.py` termina exit 0 solo tras comprobar OK y ausencia de FAILURES; APK test instalado con install -r, Success |
| Conservación S24 | PASS acotado a la ejecución | Las 66 tablas Room son idénticas antes/después, incluidas outboxes, perfiles y saldos; `coverage2-all-tables-comparison.json`. Tres escalas restauradas 1.0 en finally; font_scale y enabled_accessibility_services idénticos, `coverage2-integrity-comparison.json`. MainActivity reabierta al final, captura privada `coverage2-final-session.png` |
| A16 | NO VERIFICADO en esta tanda | No aparece en ADB; no se exige intervención humana ni se atribuye cobertura S24 a ese teléfono |

Comando host: `gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest --tests com.kipu.app.feature.analytics.AnalyticsViewModelTest --tests com.kipu.app.feature.calendar.presentation.CalendarViewModelTest --tests com.kipu.app.feature.home.presentation.HomeNetWorthViewModelTest`. Runner: ADB del SDK, serial S24, `shell am instrument -w -e class LISTA com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`; lista exacta en script privado. No connectedAndroidTest, uninstall, pm clear, logout ni sustitución de sesión. Los fixtures no requieren permisos runtime; no se añadieron grants de permisos opcionales de la cuenta real.

## Intentos fallidos y corrección del fixture

El primer APK test usaba FileProvider de AndroidX en su proceso independiente: ClassNotFoundException porque ese APK no incluye las dependencias del target. Se detuvo la instrumentación incompleta sin borrar datos (am force-stop de Kipu), se sustituyó por proveedor y receptor Java basados solo en Android, no exportando el proveedor; una compilación intermedia también falló por BOM Java generado en PowerShell y se corrigió UTF-8 sin BOM. Evidencia conservada: `coverage2-crash.txt`, `coverage2-s24-instrumentation.txt` (Process crashed), `coverage2-share-fix-build.log` (exit 1). Son fallos del fixture corregidos, no pruebas aprobadas ni defectos de producción. La tanda final con APK corregido es la que respalda el PASS; no se repitieron AnalyticsScreenTest, AnalyticsDaoTest ni la suite global previamente verdes.

## Límites que impiden cierre

- **T021**: no se encontró aprobación registrada de GATE-ANALYTICS-04 en la documentación consultada/búsqueda `docs/` y `specs/`. Se conserva abierto; sin cambios de catálogo, zonas, filtros, periodos ni comparación.
- **T027**: TalkBack de Google y Samsung están instalados, servicio habilitado originalmente `null`; se mantuvo desactivado. No se ejecutaron lectura hablada, orden de foco, gestos ni tres journeys completos con TalkBack. Las pruebas de componentes y texto 200 % aportan cobertura parcial, no equivalencia a ese criterio.
- **T028**: navegación y errores/sesiones se verifican en fronteras separadas (grafo/Compose/JVM); faltan recorridos integrados completos con sesión/propietario. No se certifica nueva operación offline/recuperación remota.
- **T029**: revisión cruzada independiente registrada al final de este informe; la verificación funcional aprobada y la aceptación integral siguen pendientes.
- Compartir: PASS del acceso URI Android real con fixture aislado; **NO VERIFICADO** el envío desde Yape o selector de compartir hasta UI de revisión, notificación Yape real en ambos órdenes y ocho muestras privadas ausentes. El fixture no demuestra origen Yape ni cambia esos resultados.
- Free se cubre con fixtures; no se cambió entitlement real. Eliminación completa UI con otra sesión y creación offline de cambios reales permanecen fuera de la cobertura autorizada.

No se encontró un defecto nuevo de producción en esta tanda. La matriz de [dispositivos](../sprint-9-device-validation.md) mantiene la historia previa y agrega estos resultados sin convertir límites en aprobaciones.

## Revisión cruzada independiente Sprint 9 — 2026-11-03

Revisor: Opus, independiente de los workers implementadores y de las pruebas. Revisión directa del rango `35858567..e7702012`, sin ejecutar suites nuevas ni cambiar código de producción.

Alcance HU-41:
- Consulta Room acotada al propietario, incluidos los filtros de categorías, cuentas, tarjetas y comercios usados.
- No cuentan como gasto las transferencias, aperturas, principal de deudas, reservas ni reembolsos.
- El reembolso neto se limita al importe del gasto y nunca da negativo; se excluyen los estados CONFLICT/FAILED_PERMANENT.
- Free/Premium y el cambio de propietario solo tienen la cobertura aislada ya registrada.

**Sin defecto bloqueante de código.** Los hallazgos y el alcance transversal (HU-06, HU-49) están en el [informe Sprint 9](../sprint-9-device-validation.md#revisión-cruzada-independiente--2026-11-03).

Límites: esto completa solo el componente de revisión de T029. T021 cuenta con aprobación formal del alcance incremental por el Owner ([decisiones](../sprint-9-owner-decisions.md)). Siguen abiertos T027 (TalkBack hablado), T028 (journeys integrados de errores/reintentos/propietario), T029 (verificación funcional y aceptación integral), el A16 en la última tanda y los criterios NO VERIFICADO. Se mantienen las limitaciones de meses completos y de comparación por totales sin categoría. Veredicto: **no APPROVED integral**.


## Cierre factible de journeys reales — 2026-11-03 (`closure3`)

Base `main` / `9552a613`; trabajo secuencial, sin ramas, comandos Spec Kit, instalación, builds ni repetición de suites ya verdes. **PASS de recorridos de lectura S24; aceptación PARCIAL.** Este apartado actualiza los límites de T028 de la tanda coverage2: ya hay navegación integrada con la sesión real, pero no errores/reintentos integrados ni cambio de propietario real. La revisión independiente Opus consta en `3e5d8d8e`: el componente de revisión de T029 está cerrado; T029 integral sigue abierta.

Evidencia privada exclusivamente en `C:/Users/Alume/s9-device-validation-20261103/`, prefijo `closure3-`: scripts `device.py`, `journeys.py`, `source.py`, `integrity.py`; resultados `journeys-result.json`, `source-result.json`, `integrity.json`; capturas/XML por paso y copias Room before/after/final. Los nombres anteriores se entienden con el prefijo. Sin datos financieros, credenciales ni capturas en Git.

| Comprobación real S24 | Resultado y frontera |
| --- | --- |
| Inicio → historial → regreso | PASS: MainActivity y destinos reales, sesión preexistente, historial accesible y vuelta a Inicio; sin guardar/editar movimientos |
| Inicio: máscara y restitución | PASS visual/UI; control guarda preferencia global. Desviación de la intención de solo lectura: se pulsó antes de identificar su persistencia, ocasionando dos escrituras de preferencia por el camino normal de la app (activar/restaurar), sin cambios financieros. No se afirma cero escrituras remotas |
| Inicio → Calendario | PASS: mes actual, mes anterior y vuelta al actual; selección de día con evento previsto, detalle con advertencia de que no descuenta saldo automáticamente, máscara local y restitución |
| Calendario → origen → regreso | PASS: «Abrir tarjeta» abre detalle de tarjeta existente; volver retorna a Calendario y después a Inicio. No se accionan pagos, edición ni creación. Capturas `calendar-source-sheet`, `calendar-source-card-detail`, `calendar-back-from-card`, `home-final` |
| Inicio → Análisis → Inicio | PASS: pestaña real, resumen del periodo actual con fechas y aviso de periodo parcial; máscara local, scroll, restitución y retorno a Dinero. Revisión visual de captura enmascarada y árbol UI; no es salida hablada |
| Propietario/sesión | PASS acotado: los conjuntos de propietario de todas las columnas user_id/owner_id reconocidas conservan sus huellas entre copias; sesión real sigue operativa al terminar, sin logout o sustitución. No prueba cambiar de usuario ni compara tokens cifrados antes/después |
| Datos y colas | PASS: 67 tablas Room comparadas, 65 idénticas íntegramente; transacciones, hechos, ledger, saldos y las diez outboxes iguales. Perfil `hide_balances=0` tanto al inicio como al final, `SYNCED`, revisión 2→4; outbox de perfil 0→0 sin borrarla por shell |
| Diferencias de metadatos | Perfil: solo remote_revision, remote_updated_at y updated_locally_at. feature_access_cache: solo offline_anchor_elapsed_ms, offline_grant_payload, offline_grant_signature y verified_at; renovación normal de autorización, sin cambiar plan/capacidades efectivas |
| Ajustes y estado final | PASS: fuente, tres escalas, accessibility_enabled y enabled_accessibility_services exactamente iguales a baseline; bloque finally repone valores originales si difieren y toma snapshot. Servicio TalkBack no cambiado. Inicio queda con importes visibles, igual al inicio |

**Errores y aislamiento:** no apareció un error de fuente real ni un objeto no disponible durante estos recorridos. No se corrompieron registros, invalidaron sesiones, cambiaron propietarios ni provocaron fallos de red para fabricarlos. La cobertura previa de errores genéricos/retry/cancelación de propietario sigue siendo aislada; **T028 permanece abierta** por esa integración pendiente y T014 no se cierra (sin nueva lectura offline real). No se deduce Free real desde la sesión usada ni se cambió su entitlement.

**T027 / TalkBack: NO VERIFICADO hablado.** Paquetes de TalkBack instalados, pero los tools disponibles permiten shell, XML y capturas visuales; no hay escucha, micrófono o captura de audio accesible segura acreditada. No se abrió una grabación que pudiera contener información financiera privada ni se activó el servicio para producir logs: ni XML, ni logcat, ni servicios habilitados prueban voz, orden de foco o gestos TalkBack. Evidencia `closure3-talkback-capability.json`; ajustes originales preservados. Las pruebas previas de semántica y texto 200 % siguen válidas, sin equivalencia a los tres journeys hablados.

**Eliminación UI aislada: NO VERIFICADO.** Existe emulator-5554 / AVD Pixel_10 y get-state devuelve device, pero pm list users vence a 8 s, sys.boot_completed y service check package vencen cada uno a 6 s. No hay readiness acreditada ni se pudo verificar una sesión aislada utilizable. No se reinició ni creó infraestructura, usuario o cuenta; S24 tiene solo usuario 0 Propietario, sin perfil de prueba existente. A16 ausente en ADB. No se cerró/reemplazó la sesión real de ningún teléfono. Sigue válida la prueba API desechable HU-06 B y recuperación v3; no se repite ni se presenta como UI completa.

No se accedió a datos de la app Yape ni a otras aplicaciones; no se generaron notificaciones/movimientos sintéticos sobre la cuenta real. Compartir Yape real, notificación viva en ambos órdenes y ocho muestras privadas siguen NO VERIFICADO. Sin defecto nuevo de producción detectado ni fix/deploy necesario; no se envían cambios remotos mediante APIs/SQL.

Decisiones de producto resueltas (actualizado 2026-11-03 [2026-11-04 UTC]): el Product Owner aprobó explícitamente («Aprobar este alcance incremental», [decisiones del Owner](../sprint-9-owner-decisions.md)) el catálogo de métricas, reglas de corte, monedas, periodos y filtros de HU-41, resolviendo formalmente **GATE-ANALYTICS-04 / T021** para este incremento (meses completos y comparación por totales conservados). Asimismo, el Owner aprobó conservar eventos incompatibles para revisión, reconciliando **FR-012 de EP-AUT/011** con Room v37 (PK owner+fuente+event_id e índice no único). No se declara APPROVED integral ni se certifica todo el backlog por estas decisiones. Permanecen abiertos T027, T028 (errores/reintentos/propietario integrados), T029 (aceptación integral) y los criterios NO VERIFICADO en hardware.

## Ronda de corrección 2026-11-05

- **Fecha:** 2026-11-05
- **Alcance:** HU-39 (Inicio e historial), HU-40 (Calendario financiero) y HU-41 (Estadísticas y analítica).
- **Evidencia automatizada:**
  - **Unitarios JVM:** 1.678 totales (1.675 PASS, 0 FAIL, 3 omitidos) en `:app:testDebugUnitTest`. Incluye:
    - `com.kipu.app.feature.calendar.presentation.CalendarPresentationTest`: PASS (etiqueta actualizada a «Abrir recurrencia»).
    - `com.kipu.app.feature.analytics.AnalyticsViewModelTest`: PASS (incluye `combinedFiltersApplyToBothPeriodsAndFreeFallsBackToUnfilteredCurrentMonth`).
    - `com.kipu.app.feature.home.presentation.HomeNetWorthViewModelTest`: PASS.
  - **Instrumentados en Galaxy A16 (`SM-A165M`):** 142 PASS, 0 FAIL:
    - `com.kipu.app.feature.movements.MovementRefundBudgetIntegrationTest`: 2 PASS (ejecutado PASS A16 2026-11-05), con `returningHomeAfterCrossMonthRefundRefreshesNetWorthAndBothRelevantDates` validando HU-39 escenario 5 (reembolso en periodo posterior refresca patrimonio y ambas fechas en Inicio).
    - `com.kipu.app.navigation.CalendarNavigationTest`: 3 PASS (ejecutado PASS A16 2026-11-05), con `recurrenceEventOpensItsOwnRegisteredDetailRouteWithId` y `unavailableRecurrenceEventLeavesCalendarOpen` validando HU-40 (navegación a recurrencia y fallback no disponible).
    - `com.kipu.app.navigation.ObjectSourceNavigationTest`: 4 PASS (ejecutado PASS A16 2026-11-05), con `missingCalendarRecurrenceShowsUnavailableAndReturnsToCalendar` y `calendarRecurrenceRouteShowsOnlyItsSourceAndDoesNotCreatePayments` validando HU-40 (origen faltante sin mutación financiera).
    - `com.kipu.app.feature.analytics.AnalyticsDaoTest`: 2 PASS (ejecutado PASS A16 2026-11-05), con `repositoryComparisonCombinesFiltersAndKeepsPeriodBoundariesAndOwnerIsolation` validando HU-41 escenario 2 (comparación combinada de filtros y periodos con aislamiento de propietario).
  - **Base de datos pgTAP local:** 74 archivos / 1422 tests PASS (exit code 0). Migraciones sincronizadas remoto = local = 114.
- **Evidencia QA previa:** Informes de Sprint 7 y Sprint 9 (`coverage2-` y `closure3-`), y `docs/qa/incidente-historial-post-sync-2026-10-26.md`.
- **Cambios de esta ronda:**
  - F1.2: Navegación de recurrencia corregida en Calendario (`CalendarPresentation.kt` muestra "Abrir recurrencia" y `CalendarNavigation.kt` enruta a `calendarRecurrenceDestination` con fallback no disponible seguro si el objeto no existe o es ajeno, sin efectos contables).
  - F3.3 y F3.4: Incorporación de tests instrumentados para HU-39 esc. 5 (reembolso trans-mes), HU-40 (navegación de recurrencia y aislamiento contable) y HU-41 esc. 2 (comparación y filtros combinados).
- **Pendientes y bloqueos:**
  - T027 (TalkBack hablado) sigue abierta por requerir entorno de escucha/audio accesible en hardware; pendiente del Owner.
  - S24 no ejecutado en esta ronda por firma incompatible (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Veredicto por HU:**
  - **HU-39:** CUMPLIDO (patrimonio, historial y escenario 5 de reembolso trans-mes validados con tests instrumentados en hardware A16).
  - **HU-40:** CUMPLIDO (eventos ocurridos y previstos, distinción accesible y navegación corregida F1.2 con fallback no disponible validados en hardware A16).
  - **HU-41:** PARCIAL (resumen Free y comparación Premium con filtros combinados validados con `AnalyticsDaoTest` en A16; limitaciones de meses completos y comparación por totales se mantienen; T027 TalkBack permanece abierta).

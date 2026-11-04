# EP-INF — verificación focalizada de Sprint 9

Fecha: 2026-11-03, America/Lima. Base `main` / `a10e7540`; trabajo secuencial autorizado, artefactos editados a mano. **Veredicto: PASS focalizado, aceptación PARCIAL; no APPROVED integral.** T021, T027–T030 y revisión cruzada permanecen pendientes. No hubo cambios de código de producción, base de datos o despliegue remoto.

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

Límites: esto completa solo el componente de revisión de T029. Siguen abiertos T021 (GATE-ANALYTICS-04 sin aprobación localizada), T027 (TalkBack hablado), T028 (journeys integrados), el A16 en la última tanda y los criterios NO VERIFICADO. Se mantienen las limitaciones de meses completos y de comparación por totales sin categoría. Veredicto: **no APPROVED integral**.

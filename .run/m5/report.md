# M5 — Prueba funcional en Galaxy A16

Fecha: 2026-11-02. Dispositivo solicitado: `RFGL70J9E2T`. Resultado global: **FALLA — ejecución bloqueada por dispositivo ausente en ADB**.

No se modificó código, no se hicieron commits, no se desinstaló la aplicación y no se limpiaron sus datos. No se crearon movimientos, preferencias, comercios ni importaciones. No se confirmó ninguna importación.

## Evidencia y reproducción del bloqueo

1. Ejecutar `%LOCALAPPDATA%/Android/Sdk/platform-tools/adb.exe start-server`.
2. Ejecutar el mismo binario con `devices -l`: muestra únicamente `List of devices attached`, sin teléfonos ni dispositivos no autorizados. Evidencia: [inicio](adb-devices-initial.txt) y [final](adb-devices-final.txt).
3. Ejecutar `./gradlew :app:installDebug :app:installDebugAndroidTest`: termina con código 1. Evidencia completa: [install.log](install.log).
4. Intentar la clase solicitada mediante `adb -s RFGL70J9E2T shell am instrument -w -e class com.kipu.app.core.database.StatementRoomMigrationTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`: el dispositivo no está disponible. Evidencia: [migration-instrumentation.log](migration-instrumentation.log).

No se dispone de capturas del teléfono: al no existir transporte ADB no es posible capturar su pantalla. No se reutilizó evidencia de ejecuciones anteriores ni se simuló una captura. El intento de recuperar logcat se conserva en [logcat-attempt.log](logcat-attempt.log); no demuestra presencia o ausencia de crashes de Kipu.

## Resultado por paso

| Paso | Estado | Alcance comprobado / pendiente |
| --- | --- | --- |
| 1. Instalar, abrir y migrar Room 33→34→35 | FALLA de entorno | La instalación no pudo acceder al teléfono; arranque y migraciones no verificados. Instrumentación intentada por `am instrument`, sin ejecución de tests. |
| 2. Recordar categoría para KFC | NO EJECUTADO | Pendiente selección, aprendizaje, interruptor apagado y persistencia. No se creó preferencia ni movimiento que limpiar. |
| 3. Yoyoso / Logo.dev / opción privada | NO EJECUTADO | Pendiente búsqueda explícita, resultados, alta global autorizada, logo y retorno al catálogo. No se creó comercio privado ni global. |
| 4. Importación y sugerencias | NO EJECUTADO | No se transfirió el PDF, no se inició ni confirmó importación; pendientes chips, edición, interruptores y preservación de categoría. |
| 5. Notificaciones | NO EJECUTADO | No es posible determinar si hay notificaciones pendientes. |
| 6. Limpieza y escalas | PARCIAL / NO VERIFICABLE | Sin datos de prueba creados por esta ejecución; se intentó establecer y leer las tres escalas en 1.0, sin transporte al teléfono. |

## Cierre y limitaciones

Los intentos de restaurar y verificar `window_animation_scale`, `transition_animation_scale` y `animator_duration_scale` están en [animation-scales.log](animation-scales.log). No se afirma que valgan 1.0 porque ADB no pudo comunicarse con el dispositivo; esta ejecución tampoco logró modificarlas.

No hay bugs funcionales confirmados de la aplicación en esta ejecución. Es necesario repetir M5 con el Galaxy accesible por el ADB del SDK; el bloqueo de entorno impide emitir PASA para los criterios funcionales y verificar las escalas finales.

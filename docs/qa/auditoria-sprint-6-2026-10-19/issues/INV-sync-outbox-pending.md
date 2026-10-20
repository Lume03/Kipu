## Clasificación
- **Tipo:** investigación de observabilidad/sincronización; no bug remoto confirmado.
- **Prioridad:** investigar con cuidado por el alcance financiero; no se infiere pérdida de datos.
- **Datos:** solo agregados sanitizados de fixtures QA. No adjuntar DB/WAL, payloads, IDs de usuario ni tokens.

## Observado
En la lectura local del fixture QA, los comandos `OPEN_DEBT` (1), `SETTLE_DEBT` (1) y `SET_DEBT_SCHEDULE` (8) aparecían `PENDING`, `attempt_count=0`, sin `last_error_code` o respuesta accesible. En una observación posterior, el request existente de `SyncDebtChangesWorker` tenía conectividad satisfecha pero `Ready: false`; no se pudo vincularlo con un comando concreto. El logcat filtrado no aportó eventos del worker/session. No se confirmó persistencia remota.

## Hipótesis no confirmadas
La revisión estática describe que el worker puede devolver retry antes de reclamar comandos si el owner actual no coincide con el owner verificado, además de constraints/estado de sesión. Ninguna de estas causas se observó directamente en el APK. Un request `Ready: false` o un periodo de espera no demuestra por sí solo fallo de sync.

## Trabajo solicitado
Con instrumentación/observabilidad autorizada futura, correlacionar booleanos de owner cargado/verificado/coincidente, estado y constraints del request identificable, y transición agregada de comandos QA (`PENDING` → `IN_FLIGHT`/attempt/error) con timestamps. No exponer identidad.

## Límites y cierre
No forzar workers/retries, invocar RPC financieras, ejecutar SQL de escritura ni cambiar red/permisos. Cerrar la investigación con causa respaldada o con el punto exacto de observabilidad que aún falta. No declarar pérdida remota ni sincronización exitosa sin una señal del servidor.

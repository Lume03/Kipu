# UI3 — notifications + widgets + portability (portability exporta datos: revisar privacidad)

Esta porción muestra avisos y widgets financieros, y permite exportar, restaurar y eliminar datos de la cuenta. Se asumieron miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y uso frecuente sin conexión. La revisión fue estática, sin ejecutar pruebas ni consultar servicios remotos.

## Hallazgos

### [UI3-01] La restauración multiplica por mil las fechas financieras
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/portability/SupplementaryHydration.kt:129
- Qué está mal: `timestamp()` ya devuelve milisegundos y `financialFacts` vuelve a multiplicarlos por 1000, tanto para `effective_at` como para `created_at`. El DAO financiero interpreta `effectiveAt` como milisegundos y lo convierte a segundos para el asiento.
- Escenario concreto de fallo: al restaurar una apertura fechada el 1 de noviembre de 2026, se guarda una fecha miles de años posterior y se genera el asiento con esa fecha errónea; los hechos históricos quedan fuera de su periodo real.
- Fix mínimo: eliminar las dos multiplicaciones y comprobar las fechas restauradas y del asiento en la prueba de Room existente.
- Confianza: alta
- Cross-módulo: no

### [UI3-02] Los widgets muestran datos con la aplicación bloqueada
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/widgets/WidgetRefresher.kt:89
- Qué está mal: el estado `Protected`, que incluye `APP_LOCKED`, conserva el propietario para los widgets; `WidgetDataProvider.load()` lee ese identificador sin comprobar el bloqueo y presenta datos financieros.
- Escenario concreto de fallo: con bloqueo biométrico activo y saldos visibles, otra persona mira la pantalla de inicio y ve importes, nombres de acreedores o capturas sin desbloquear Kipu.
- Fix mínimo: al bloquear, redibujar inmediatamente un estado protegido sin datos y mantenerlo también cuando un proceso nuevo atienda una actualización del widget; restaurar la información solo tras desbloquear.
- Confianza: alta
- Cross-módulo: no

### [UI3-03] Una actualización simultánea pierde el descarte de un aviso
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt:160
- Qué está mal: el envío lee una copia de la cola, mientras un descarte posterior reutiliza el mismo `operationId`; al terminar el primer envío se elimina esa fila por identificador, aunque su contenido haya cambiado.
- Escenario concreto de fallo: se envía «leído» y, antes de la respuesta, el usuario descarta el mismo aviso; la respuesta del primer envío borra también el descarte pendiente y otro dispositivo mantiene el aviso.
- Fix mínimo: borrar únicamente si la fila todavía coincide con el estado enviado, o asignar una nueva versión de operación al cambiarla; probar la intercalación entre envío y descarte.
- Confianza: alta
- Cross-módulo: no

### [UI3-04] La descarga puede sobrescribir cambios locales recién encolados
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt:60
- Qué está mal: la lista de pendientes se consulta fuera de la transacción que guarda la respuesta remota. Un cambio local intercalado queda fuera de esa lista y el `upsertAll` reemplaza su proyección.
- Escenario concreto de fallo: llega una respuesta que aún dice «no leído», se marca el aviso como leído antes de guardar esa respuesta y el refresco vuelve a mostrarlo sin leer aunque exista el cambio en cola.
- Fix mínimo: consultar y filtrar los pendientes dentro de la misma transacción que actualiza las notificaciones; añadir un test que suspenda el refresco e intercale la mutación.
- Confianza: alta
- Cross-módulo: no

### [UI3-05] Los widgets suman solo seis vencimientos
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/widgets/data/WidgetDataProvider.kt:138
- Qué está mal: la lista compartida se recorta a seis antes de calcular el clima, la suma semanal y la cobertura de pagos. Esos cálculos presentan el resultado como si incluyera todos los vencimientos.
- Escenario concreto de fallo: con siete pagos de S/100 que vencen hoy y S/650 disponibles, el clima descuenta solo S/600 y no detecta que falta dinero para el séptimo pago.
- Fix mínimo: conservar todos los vencimientos para cálculos y limitar únicamente las filas dibujadas; probar siete pagos en el mismo día.
- Confianza: alta
- Cross-módulo: no

### [UI3-06] Las notas CSV pueden interpretarse como fórmulas
- Severidad: media
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/portability/MovementExport.kt:12
- Qué está mal: el escape protege separadores y comillas, pero deja intactos valores de texto que una hoja de cálculo interpreta como fórmula.
- Escenario concreto de fallo: una nota que empieza por `=HYPERLINK(...)` se exporta como celda ejecutable; al abrir el CSV en una aplicación de hojas de cálculo compatible puede ejecutar la fórmula o presentar un enlace engañoso.
- Fix mínimo: neutralizar los prefijos de fórmula en los campos de texto exportados y probar prefijos `=`, `+`, `-` y `@` junto con el escape CSV.
- Confianza: alta
- Cross-módulo: no

### [UI3-07] Pruebas instrumentadas buscan textos que ya no existen
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationActionsTest.kt:55
- Qué está mal: las pruebas esperan «Marcar leídas» y etiquetas completas como «Alerta · Condición ocurrida», mientras la pantalla muestra «Marcar todas como leídas» y separa el tipo de la fecha relativa. `NotificationCenterScreenTest.kt:103` y `:148` repite expectativas desactualizadas.
- Escenario concreto de fallo: al ejecutar esas pruebas sobre la pantalla actual, las búsquedas exactas no encuentran los nodos esperados y fallan antes de validar las acciones o el recordatorio.
- Fix mínimo: actualizar las búsquedas para comprobar el texto y la semántica actuales, manteniendo las verificaciones de comportamiento.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- `SupplementaryHydration.financialFacts` → comprobar fecha efectiva y fecha del asiento tras restaurar; la prueba existente solo cuenta filas y suma saldos.
- `OfflineFirstNotificationsRepository.syncPending` y `refresh` → intercalar una mutación local con cada operación de red, como en UI3-03 y UI3-04.
- `WidgetDataProvider` y `ClimateWidget` → siete vencimientos en un día y estado protegido durante la actualización del widget.
- `MovementExport.csv` → nota que empieza por fórmula y conserva separadores y comillas correctamente.

## Lo que no revisé

No ejecuté pruebas, Gradle ni dispositivos, según las restricciones. No comprobé despliegues ni servicios remotos; el alcance se limita a lectura estática de los archivos asignados y contratos necesarios para entenderlos.

## Dudas

- ¿Se pretende permitir deliberadamente que el launcher muestre información financiera mientras Kipu está bloqueado? El tratamiento de `LocalAccess.Protected` en los widgets entra en tensión con el significado de bloqueo de la sesión.

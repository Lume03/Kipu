# T5 — KIP-17: deslizar y deshacer eliminación de notificaciones

## Implementación

`NotificationCenterScreen` envuelve cada tarjeta con `SwipeToDismissBox` de Material 3. Solo habilita `EndToStart`, por lo que respeta la dirección de lectura/RTL y no ofrece retiro en la dirección contraria. El umbral de confirmación es el 40% del ancho; el fondo de eliminación usa papelera y texto «Eliminar» y cambia de intensidad al cruzar ese ancho real.

La `LazyColumn` mantiene la key estable `notification.id`; conserva su composición incluso al quedar vacía para permitir la salida de la última tarjeta. `animateItem` anima salida y recolocación de las demás filas; se omite al reducir movimiento. El fondo usa `snap` con esa preferencia y los tokens existentes de Kipu para el resto; Material respeta además la escala de animación del sistema. El estado local del gesto no es guardable, para que Deshacer reinserte una tarjeta en reposo.

Cada tarjeta, leída o no leída, tiene la acción semántica personalizada «Eliminar notificación». La X existente de las tarjetas leídas usa el mismo flujo. «Marcar leído» y el retiro por lote de avisos leídos se conservan; el lote confirma primero cualquier eliminación pendiente y mantiene su comportamiento de borrado lógico inmediato.

## Flujo de estados del ViewModel

`NotificationsViewModel.pendingDismissals` expone el estado de `NotificationDismissalQueue`, una cola singleton del propio feature, compartida por el centro y `NotificationBadgeViewModel`. Su scope de aplicación permite terminar un commit aunque se destruya el ViewModel.

```text
VISIBLE
  └─ swipe / acción accesible / X individual
       └─ PENDING (token único, userId e id de notificación)
            ├─ Se oculta de la lista y de todos sus filtros
            ├─ Si seguía sin leer, deja de contar en el badge
            ├─ Snackbar «Notificación eliminada» + «Deshacer»
            ├─ Deshacer antes del commit
            │    └─ VISIBLE / RESTAURADA
            │         temporizador cancelado, misma posición por createdAt y mismo estado de lectura
            └─ Expiran 5 s / otra eliminación / cerrar Snackbar /
               salir de la pantalla / pasar a segundo plano / cambiar de usuario
                 └─ COMMITTING
                      ├─ repository.dismiss(userId, notificationId)
                      ├─ Se mantiene oculta hasta observar la baja en Room
                      ├─ Éxito → CONFIRMADA; se libera el estado pendiente
                      └─ Fallo → VISIBLE + mensaje de error
```

Solo la eliminación más reciente tiene ventana de Deshacer por usuario; al eliminar otra se confirma la anterior. Los tokens y la fase evitan que un Snackbar viejo actúe sobre una eliminación nueva o que se ejecute dos veces el mismo commit. Salir por navegación, Back o destrucción confirma lo pendiente; la comprobación de recreación de Activity conserva la ventana durante un cambio de configuración, sin reiniciar los cinco segundos.

El contador se calcula a partir de filas activas sin leer, excluyendo los mismos ids ocultados por la cola, con scope por usuario. Así evita restar dos veces cuando el borrado pasa de optimista a persistido. Deshacer restaura el conteo según el estado de lectura actual de Room. No se modificó el composable del badge ni la explicación de notificaciones.

## Persistencia y sincronización

Se reutiliza **sin cambios** `NotificationsRepository.dismiss` y su implementación:

- Aviso remoto: `deleted_at` en Room + outbox coalescido → sincronización `PATCH`.
- Avisos locales y de revisión de capturas: siguen sus rutas de retiro locales ya existentes.
- No se añadió `DELETE` físico ni una tabla o migración nueva.

No se añadió restore después del commit: el DTO/outbox actual usa `deletedAt = null` como ausencia de una modificación y su coalescencia conserva el borrado previo, sin una operación explícita para limpiar la baja remota. Deshacer ocurre antes de llamar a `dismiss`, como permite el alcance.

`NotificationsNavigation.kt` solo conecta tres callbacks nuevos del centro: deshacer un token, confirmar un token y confirmar lo pendiente al salir. Las firmas existentes para callers/tests del ViewModel se preservan mediante el constructor de compatibilidad; no se modificaron tests.

## Verificación y continuidad

- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`: **BUILD SUCCESSFUL**.
- `git diff --check` y revisión del diff staged: sin errores.
- `app/build.gradle.kts` y `.run/` permanecen fuera del trabajo y del commit.
- Fase de pruebas posterior: gesto y umbral en LTR/RTL; Deshacer de aviso leído/no leído; segunda eliminación rápida; último item; navegación/segundo plano; recreación; falla de persistencia; aviso local y consistencia del badge durante el commit.

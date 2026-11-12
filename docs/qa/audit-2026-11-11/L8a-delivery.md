# L8a - entrega: seguridad y privacidad

## Archivos por hallazgo

- SEC1-01: `app/src/main/java/com/kipu/app/MainActivity.kt` aplica FLAG_SECURE en builds no debug mientras el estado del bloqueo no es DISABLED y controla capturas de recientes en API 33+. Durante la lectura de ajustes se protege la ventana para el propietario en estado desconocido.
- UI3-02: `core/security/LocalLockCoordinator.kt`, `feature/widgets/WidgetRefresher.kt`, `feature/widgets/data/WidgetSessionStore.kt`, `WidgetDataProvider.kt`, `WidgetModels.kt` y `feature/widgets/ui/WidgetKit.kt` conectan el estado protegido, guardan la barrera y muestran el aviso de bloqueo. Queda pendiente validar la concurrencia y las transiciones en dispositivo.
- SEC1-02: `core/security/AndroidXBiometricGateway.kt` selecciona autenticadores por version y usa Keyguard para capacidad de credencial anterior a API 30. Requiere validacion real en API 28 y 29 con huella y solo PIN.
- SEC1-03: `feature/auth/domain/PasswordValidator.kt`, `supabase/functions/auth-access/index.ts`, `index_test.ts` y `app/src/test/java/com/kipu/app/feature/auth/domain/PasswordUnicodeTest.kt` validan NFC y letras/numeros Unicode.
- SEC1-04: `feature/auth/data/SupabaseAuthRepository.kt` registra sin datos sensibles un fallo ordinario al marcar pendientes y continua el cierre local. La limpieza y la espera del bloqueo de sesion se ejecutan en NonCancellable.
- SEC1-05: `core/security/LocalLockCoordinator.kt`, `core/security/model/LocalProtectionModels.kt`, `core/session/SessionCoordinator.kt`, `SessionCoordinatorImpl.kt` y `MainActivity.kt` mantienen protegido al propietario y muestran un fondo neutro hasta resolver Room; la resolucion se limita al propietario actual.
- SEC1-06: `navigation/AuthDeepLinkHandler.kt`, `feature/auth/data/RecoverySessionInstaller.kt` y `navigation/AuthDeepLinkHandlerTest.kt` reservan el enlace durante el intento, lo liberan tras fallo y lo consumen tras exito.
- SEC1-07: `supabase/functions/auth-access/index.ts` y `index_test.ts` registran la cuota de IP solo tras un fallo; la cuota por identidad se sigue aplicando antes de Auth. La cuota de IP se comprueba despues de consultar al proveedor, por lo que no evita esa llamada para intentos ya limitados.
- SEC1-08, SEC1-09 y SEC1-10: diferidos al lote L11 segun instrucciones.
- CAP1-02: `feature/capture/data/local/CaptureDaos.kt` limpia merchant_source_text en el mismo UPDATE de descarte.
- ACC2-01/02: `feature/accounts/presentation/instruments/CreditCardTermsSheet.kt` consulta el validador existente de dominio `feature/accounts/domain/model/Card.kt` y muestra error; `app/src/test/java/com/kipu/app/feature/accounts/domain/model/CardSensitiveAliasTest.kt` cubre PAN y alias normal.
- UI2-04/05: `feature/settings/presentation/BiometricSettingsScreen.kt` y `BiometricSettingsViewModel.kt` habilitan apagar el ajuste, exigen autenticacion si esta disponible y piden confirmacion explicita si no lo esta.
- SEC2-03/04: `feature/auth/presentation/SessionEntryCurtain.kt` limita la espera a 10 segundos y finaliza salida de error en finally.

## Verificacion

- `deno test --allow-all supabase/functions/auth-access`: 38 tests, 0 fallos.
- `gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Tests unitarios focalizados PasswordUnicodeTest, CardSensitiveAliasTest, AuthDeepLinkHandlerTest y el caso de barrera inicial de SessionOwnerSyncTest: BUILD SUCCESSFUL.
- No se uso ADB ni se hicieron despliegues, commits o push.

## Despliegue pendiente

- Edge function `auth-access`: desplegar posteriormente cuando el coordinador lo autorice; este trabajo no la desplego.

## Casos para telefono

1. Release con proteccion activada/desactivada: capturas y recientes, cambio en caliente, y comparacion con debug; comprobar API 33+.
2. Widgets con datos: bloquear y desbloquear, refresco y reinicio del proceso, sin nombres ni cifras mientras estan protegidos.
3. API 28 y 29: huella con boton cancelar, solo PIN, sin autenticador, y acceso al bloqueo existente.
4. Ajustes: autenticacion para desactivar; quitar huella/PIN y confirmar desactivacion; cancelar ambos flujos.
5. Cerrar sesion con fallo del marcado de pendientes o cancelacion de coroutine y comprobar propietario y sesion.
6. Descartar captura y verificar que no se conserva texto extraido; editar tarjeta con PAN con espacios y guiones y con alias valido.
7. Login y registro: dashboard lento mas de 10 s, error, y salida interrumpida; verificar que no quedan bloqueados.
8. Arranque con bloqueo activo y lectura de ajustes lenta: verificar que permanece el fondo neutro hasta mostrar el bloqueo; repetir con bloqueo desactivado.
9. Abrir enlace de recuperacion sin red y reintentarlo con red; verificar rechazo del enlace tras uso exitoso.

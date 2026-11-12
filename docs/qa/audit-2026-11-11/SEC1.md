# SEC1 — seguridad y auth: core/security, session, network, auth/data+domain, MainActivity, deep links

Esta porción registra y autentica usuarios, cifra su sesión, conserva el propietario local para el trabajo offline y coordina el desbloqueo y los enlaces de recuperación. Incluye los endpoints de acceso y eliminación de cuenta, sus tests y la configuración Android asociada. Asumí miles de usuarios, uno o dos dispositivos por usuario, datos financieros privados, conexiones intermitentes y usuarios que comparten una IP pública.

## Hallazgos

10 hallazgos: 0 críticos, 4 altos, 5 medios y 1 bajo. Son conclusiones estáticas; no se ejecutaron pruebas ni se verificó ningún despliegue.

### [SEC1-01] Recientes conserva los saldos aunque después se bloquee la app
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/MainActivity.kt:185-188,528-544; app/src/main/java/com/kipu/app/core/security/LocalLockCoordinator.kt:108-123
- Qué está mal: Al salir se registra una hora, pero se mantiene la pantalla financiera visible. No hay protección de la captura de ventana ni de la miniatura de recientes; el bloqueo se aplica al volver, después del minuto. Esto incumple la protección de vistas previas de FR-047.
- Escenario concreto de fallo: Con desbloqueo local habilitado, abrir un saldo, pulsar Inicio y esperar dos minutos; otra persona abre recientes y ve la miniatura del saldo sin superar el desbloqueo de Kipu. El overlay que aparecerá al volver no borra esa captura anterior.
- Fix mínimo: Proteger la ventana frente a capturas cuando la protección local esté activa, incluyendo la miniatura de recientes; el cambio más pequeño es aplicar FLAG_SECURE durante ese modo. Añadir una comprobación de la política de ventana, además del test visual del overlay.
- Confianza: alta
- Cross-módulo: no

### [SEC1-02] La combinación biométrica excluye Android 9 y 10
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/core/security/AndroidXBiometricGateway.kt:19-24,66-70
- Qué está mal: Se exige BIOMETRIC_STRONG | DEVICE_CREDENTIAL en todas las versiones. AndroidX Biometric 1.1.0 no admite esa combinación en API 28 y 29, aunque ambos sistemas están dentro del minSdk 24; la capacidad se presenta como no disponible y construir el prompt con ella puede lanzar IllegalArgumentException. El test del gateway solo usa API 34.
- Escenario concreto de fallo: Un teléfono Android 10 con huella y PIN configurados abre el ajuste de protección: canAuthenticate para la combinación no da éxito y Kipu dice que no existe autenticación configurada. Si la preferencia ya está activa, intentar mostrar ese prompt usa una combinación rechazada.
- Fix mínimo: Elegir autenticadores compatibles con la versión del sistema y ofrecer la alternativa de credencial correspondiente; usar la misma elección al consultar capacidad y construir el prompt. Añadir un caso API 29 con huella/PIN y otro de credencial sin huella.
- Confianza: alta
- Cross-módulo: no

### [SEC1-03] Recuperar la contraseña puede crear una clave que el login rechaza
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/domain/PasswordValidator.kt:16-18,22-35; app/src/main/java/com/kipu/app/feature/auth/domain/RecoveryUseCases.kt:60-71; supabase/functions/auth-access/index.ts:85-90,266-277
- Qué está mal: Kotlin acepta letras y dígitos Unicode; auth-access solo acepta A-Z/a-z y 0-9. El cambio de contraseña usa directamente updateUser, por lo que no atraviesa la validación ASCII del login.
- Escenario concreto de fallo: Establecer «ááááááá1» desde un enlace válido: la validación Android lo acepta y envía el cambio a Auth. Si Auth acepta esa clave, el siguiente inicio con la misma clave obtiene 401 antes de comprobarla, porque no contiene una letra ASCII. La pantalla de registro también habilita contraseñas que el endpoint luego rechaza.
- Fix mínimo: Aplicar la misma política en Android, registro, login y restablecimiento; con la política actual del endpoint basta alinear las comprobaciones de letra/dígito en Kotlin. Test mínimo: la misma contraseña Unicode produce la misma decisión en ambos validadores y no queda aceptada solo para el reset.
- Confianza: alta
- Cross-módulo: no

### [SEC1-04] Un error al marcar pendientes impide retirar la sesión
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/auth/data/SupabaseAuthRepository.kt:250-270; app/src/main/java/com/kipu/app/MainActivity.kt:532-540
- Qué está mal: markWaitingForAuth se ejecuta antes de revocar el propietario y borrar las credenciales. Si falla una escritura local, se devuelve failure sin hacer esa limpieza; el botón del bloqueo ignora el resultado y navega al login.
- Escenario concreto de fallo: Con la app bloqueada y pendientes, SQLite falla al actualizar el outbox, por ejemplo por falta de espacio. Pulsar «Cerrar sesión» lleva al login, pero el propietario y el archivo de sesión siguen activos; volver a iniciar el proceso puede restaurar la sesión que el usuario creyó cerrar.
- Fix mínimo: Garantizar la revocación local y eliminación de credenciales aunque falle el marcado del outbox; conservar las filas con su propietario y reportar el fallo por separado. Test mínimo: hacer que markWaitingForAuth lance una excepción y comprobar NoOwner, borrado de credenciales y ausencia de restauración posterior.
- Confianza: alta
- Cross-módulo: no

### [SEC1-05] El arranque publica acceso antes de conocer la protección local
- Severidad: media
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/core/session/SessionCoordinatorImpl.kt:28-35; app/src/main/java/com/kipu/app/core/security/LocalLockCoordinator.kt:44,53-79; app/src/main/java/com/kipu/app/MainActivity.kt:250-269,528-529
- Qué está mal: setActiveOwner publica Available inmediatamente. Otro collector consulta después Room para conocer la preferencia de bloqueo; mientras espera, lockState sigue DISABLED. Además, LocalLockCoordinator nunca llama a updateLockState: la barrera de sesión sigue Available aun cuando aparece el overlay. La restauración no usa la cortina de entrada del login interactivo.
- Escenario concreto de fallo: Arrancar con sesión guardada y bloqueo activo, retrasando findSettings por una cola de trabajo de Room. La restauración puede abrir el dashboard mientras la consulta sigue pendiente y todavía no hay overlay. El test instrumentado espera al bloqueo y sustituye la pantalla por un if/else, mientras MainActivity compone el NavHost debajo del overlay, por lo que no detecta esta ventana.
- Fix mínimo: Mantener protegido el acceso mientras se carga la preferencia y publicar Available únicamente después de decidir si exige desbloqueo; conectar las transiciones de bloqueo con SessionCoordinator. Test mínimo: suspender findSettings y comprobar que ninguna pantalla privada ni Available se expone hasta resolverlo y, si está activo, autenticar.
- Confianza: alta
- Cross-módulo: no

### [SEC1-06] Una interrupción de red consume el enlace de recuperación localmente
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/navigation/AuthDeepLinkHandler.kt:45-49,60-68; app/src/main/java/com/kipu/app/feature/auth/data/RecoverySessionInstaller.kt:25-42
- Qué está mal: Se añade el enlace al conjunto de consumidos antes de intercambiar o verificar su sesión. Si la red falla antes de que Auth lo reciba, el mismo enlace aún válido ya no puede reintentarse en el proceso actual.
- Escenario concreto de fallo: Abrir por primera vez un enlace válido sin red: retrieveUser falla y ready queda false. Recuperar la conexión y volver a tocar el mismo correo produce InvalidOrConsumed; no vuelve a ejecutarse la instalación, aunque el proveedor nunca consumió el enlace.
- Fix mínimo: Registrar el consumo después de instalar correctamente, distinguiendo un intento en curso de uno completado; liberar el intento al fallar por transporte. Test mínimo: primer intento falla por IOException, segundo con el mismo enlace verifica e instala correctamente.
- Confianza: alta
- Cross-módulo: no

### [SEC1-07] Cinco accesos correctos agotan la cuota de toda una IP
- Severidad: media
- Categoría: escala
- Ubicación: supabase/functions/auth-access/index.ts:131-139,280-290,472-483
- Qué está mal: Cada petición de login consume la cuota de IP antes de saber si es correcta y el adaptador fija cinco intentos por 900 segundos. Los accesos exitosos no liberan ese presupuesto. La política de intentos fallidos termina limitando también a usuarios independientes detrás de una IP compartida.
- Escenario concreto de fallo: Seis usuarios de una oficina o una red móvil comparten IP y acceden con credenciales correctas dentro de quince minutos. Los cinco primeros consumen la cuota común y el sexto recibe 429 pese a no haber fallado nunca.
- Fix mínimo: Reservar el umbral estricto por identidad y establecer para la IP un límite que admita accesos legítimos compartidos, o contabilizar fallos para la espera progresiva. Test mínimo: seis logins correctos de identidades distintas detrás de una IP no disparan el umbral de fallos de una cuenta.
- Confianza: alta
- Cross-módulo: no

### [SEC1-08] Tests de aislamiento y pendientes comprueban sus propios valores
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/auth/domain/RecoveryAndSignOutTest.kt:13-21; app/src/test/java/com/kipu/app/feature/auth/AuthFeatureBoundaryTest.kt:41-53
- Qué está mal: El test de pendientes devuelve 3 + 2 desde un fake y comprueba 5, sin usar PendingChangesRepositoryImpl. El supuesto test de no convertir movimientos cambia una copia de UserProfile y comprueba que una variable local constante sigue valiendo 500. Ambos pasan aunque se rompa la integración que dicen proteger.
- Escenario concreto de fallo: Quitar instrumentPending o customPending de count deja verde el test de pendientes, y el aviso de cierre puede omitir cambios reales. Modificar código real de persistencia de preferencias tampoco afecta al segundo test, porque no lo ejecuta.
- Fix mínimo: Sustituir el primero por una instancia real de PendingChangesRepositoryImpl con DAOs y un customSource controlados, comprobando la suma; retirar la afirmación de integración del segundo hasta probar el contrato real en el módulo correspondiente. El caso mínimo es un pendiente por cada fuente y propietario, con una segunda cuenta excluida.
- Confianza: alta
- Cross-módulo: no

### [SEC1-09] El almacenamiento cifrado no tiene un test sobre su implementación
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/main/java/com/kipu/app/core/security/KeystoreEncryptedSessionStorage.kt:78-125,129-137; app/src/test/java/com/kipu/app/feature/auth/data/SupabaseAuthRepositoryTest.kt:26
- Qué está mal: Los tests de sesión sustituyen este almacenamiento por mocks; StoredAuthSessionTest solo prueba JSON y vencimiento. No comprueban escritura cifrada, lectura, rechazo de un archivo alterado ni borrado real, que forman la frontera de persistencia de credenciales.
- Escenario concreto de fallo: Una regresión que guarde texto sin cifrar o que conserve una sesión después de delete puede pasar todos esos tests, aunque exponga credenciales o restaure una sesión cerrada.
- Fix mínimo: Añadir un test instrumentado del almacenamiento real: guardar una sesión, comprobar que el archivo no contiene el token en claro, recuperarla y verificar que delete impide recuperarla. Un segundo caso mínimo debe alterar el ciphertext y comprobar que no devuelve la sesión. No se propone ejecutarlo en esta auditoría.
- Confianza: alta
- Cross-módulo: no

### [SEC1-10] Permanecen dos tests de plantilla sin comportamiento de Kipu
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/test/java/com/kipu/app/ExampleUnitTest.kt:1-16; app/src/androidTest/java/com/kipu/app/ExampleInstrumentedTest.kt:1-24
- Qué está mal: Uno comprueba 2 + 2 y el otro compara el packageName con APPLICATION_ID. No protegen un flujo de negocio ni la seguridad de la app. Se buscaron referencias en todo el árbol: solo aparecen sus propias declaraciones.
- Escenario concreto de fallo: Un APK que no autentica ni oculta saldos sigue satisfaciendo ambas comprobaciones; conservarlas añade resultados verdes sin detectar esos errores.
- Fix mínimo: Borrar ambos archivos; conservar los tests que sí invocan comportamiento real.
- Confianza: alta
- Cross-módulo: no
- Ahorro estimado: 40 líneas

## Código de riesgo sin tests

- LocalLockCoordinator + SessionCoordinatorImpl → bloquear findSettings durante restauración y verificar que no se publica acceso privado; hoy LocalLockTest usa un coordinador cuyos métodos no hacen nada.
- AndroidXBiometricGateway → capacidad y prompt en API 29, además del único caso API 34 existente.
- SupabaseAuthRepository.signOut → fallo al marcar pendientes y retirada local garantizada de la sesión.
- AuthDeepLinkHandler + RecoverySessionInstaller → fallo transitorio y reintento del mismo callback sin reiniciar el proceso. RecoveryAcceptanceTest, leído como contrato adicional de SEC2, prueba éxito y rechazo del proveedor, pero no este caso.
- PasswordValidator + reset + login de auth-access → una contraseña Unicode atraviesa la misma política en los tres pasos. El test adicional de aceptación usa login directo del SDK, sin pasar por auth-access.
- KeystoreEncryptedSessionStorage → round trip real, ausencia de token en claro, corrupción y borrado; actualmente los consumidores lo mockean.
- PendingChangesRepositoryImpl → suma real de todas las fuentes y aislamiento entre dos propietarios.
- MainActivity → protección de la miniatura de recientes con el bloqueo activo; el test del overlay no comprueba la ventana ni el ciclo de la Activity.

## Lo que no revisé

- Ningún archivo de código o test textual asignado quedó sin leer completo: 37 main Kotlin, 18 tests JVM, 3 instrumentados, ambos endpoints con sus tests TypeScript, config.toml, plantilla HTML/SVG, manifests, XML de recursos, ProGuard y build.gradle.kts. Los PNG de supabase/templates/assets se trataron como recursos binarios: no se evaluó su diseño visual.
- No ejecuté Gradle, builds, tests JVM/Deno/instrumentados, ADB, SQL, Supabase CLI/MCP ni solicitudes de red. Las conclusiones de plataforma se contrastaron con la versión local declarada de AndroidX Biometric y el artefacto ya presente en caché; no se validó comportamiento en hardware.
- No audité los módulos ajenos, migraciones, RLS ni funciones SQL llamadas por los endpoints. Solo leí algunos llamadores y tests externos para entender contratos; no atribuyo aquí defectos internos de esos módulos.
- El despliegue remoto, las cabeceras efectivamente saneadas por el proxy, la asociación HTTPS del dominio y la firma de distribución no pueden acreditarse con estos archivos. No existe configuración de firma release en app/build.gradle.kts; eso por sí solo no prueba ausencia de firma en el proceso de publicación.
- El árbol compartido ya tenía cambios ajenos: .agents/skills/ponytail/, .claude/ y skills-lock.json; durante la auditoría aparecieron informes de otros workers. Mi única escritura es este informe.

## Dudas

- ¿Qué cabecera de IP garantiza y reemplaza el proxy de producción? getClientIp prioriza cf-connecting-ip y x-real-ip sobre x-forwarded-for; sin comprobar esa frontera no afirmo que sea explotable la suplantación de origen.
- ¿La política aprobada de contraseña admite Unicode o solo ASCII? SEC1-03 identifica la divergencia observable; la respuesta define cuál de las dos validaciones debe conservarse.
- No se pudo confirmar la política real de envío SMTP ni las claves/configuración remota de Auth. El endpoint de recuperación espera la llamada de correo y añade solo 50 ms, mientras resend normaliza a un plazo común; se necesita evidencia del proveedor para medir diferencias observables de tiempo entre correos existentes e inexistentes.

Prioridad: corregir SEC1-01 a SEC1-04 y cerrar la ventana de inicio de SEC1-05. Limpieza demostrable: 40 líneas de tests de plantilla, sin retirar dependencias.

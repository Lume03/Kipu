# Evidencia de validación — Issue #40

**Fecha**: 2026-10-13
**Rama**: `fix/alias-rules-insets-uiux-polish`

## Entorno

- Android Studio con emulador **Small Phone API 37.1**.
- Validación prevista en tema claro y oscuro de Kipu, fuente grande y teclado abierto.

## Pasos reproducibles

1. Abrir Perfil y entrar en **Reglas de alias**.
2. Con un usuario Free, comprobar la tarjeta compacta Premium y usar **Ver Premium**.
3. Con Premium, crear o editar una regla y desplegar **Cómo funcionan las reglas**.
4. Abrir **Categoría por comercio**, elegir un comercio, filtrar categorías y abrir el teclado: **Guardar preferencia** debe permanecer en la barra inferior.
5. Revisar las tres entradas de Perfil: Gestionar categorías, Reglas de alias y Categoría por comercio.

## Build y pruebas JVM

| Comando | Resultado |
|---|---|
| `./gradlew.bat :app:assembleDebug --no-daemon` | `BUILD SUCCESSFUL` |
| `./gradlew.bat :app:testDebugUnitTest --no-daemon` | `BUILD SUCCESSFUL` |

## Intento de validación actual

El 2026-10-13 se repitieron los mismos comandos después de los ajustes de revisión. Esta sesión de terminal no pudo iniciar Gradle porque el entorno bloqueó la conexión local requerida: `java.io.IOException: Unable to establish loopback connection`. El bloqueo ocurre antes de compilar o ejecutar pruebas, por lo que no sustituye los resultados reales anteriores ni confirma ejecución nueva. Debe repetirse desde Android Studio o una terminal con loopback disponible.

## Pruebas Android y evidencia visual

`compileDebugAndroidTestKotlin` continúa bloqueado por pruebas Android preexistentes fuera del alcance de la issue #40, entre ellas `CreditCardLedgerQueriesTest`, `AccountDetailScreenTest`, `PayCardDialogTest` y `MovementFiltersIssue19Test`. No se modificaron esas pruebas ni existe un ticket asociado a este bloqueo en esta evidencia.

Las pruebas de UI nuevas no se ejecutaron mientras ese paso de compilación siga bloqueado. Tampoco se registran aún capturas Light/Dark ni la verificación manual de fuente grande, teclado, cabecera y CTA; deben obtenerse desde el emulador indicado antes de cerrar el PR.

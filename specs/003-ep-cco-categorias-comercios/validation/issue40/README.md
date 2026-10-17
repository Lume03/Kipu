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

| Comando | Resultado real |
|---|---|
| `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon` | La compilación llegó a Kotlin. No reportó errores en `MerchantAliasRulesScreenTest.kt` ni `MerchantCategoryPreferenceScreenTest.kt`; falló únicamente por errores preexistentes en otros archivos. |

Los errores propios introducidos por el PR fueron corregidos. El bloqueo restante requiere un ticket o trabajo separado.

## Pruebas Android y evidencia visual

Los únicos bloqueadores restantes de `compileDebugAndroidTestKotlin` son preexistentes y están fuera del alcance de la issue #40: `CreditCardLedgerQueriesTest.kt`, `AccountDetailScreenTest.kt`, `PayCardDialogTest.kt` y `MovementFiltersIssue19Test.kt`. No se modificaron esos archivos; deben registrarse en un ticket o abordarse en trabajo separado.

Las pruebas de UI nuevas no se ejecutaron mientras ese paso de compilación siga bloqueado. Tampoco se registran aún capturas Light/Dark ni la verificación manual de fuente grande, teclado, cabecera y CTA; deben obtenerse desde el emulador indicado antes de cerrar el PR.

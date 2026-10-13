# Evidencia de validación — Issue #40

**Fecha**: 2026-10-12  
**Rama**: `fix/alias-rules-insets-uiux-polish`

## Build y pruebas JVM

| Comando | Resultado |
|---|---|
| `./gradlew.bat :app:assembleDebug --no-daemon` | `BUILD SUCCESSFUL` |
| `./gradlew.bat :app:testDebugUnitTest --no-daemon` | `BUILD SUCCESSFUL` |

## Pruebas Android y evidencia visual

`compileDebugAndroidTestKotlin` continúa fallando por pruebas Android preexistentes fuera del alcance de la issue #40. No se atribuye ese fallo a los cambios de reglas de alias, preferencias de categoría o ajustes.

Las nuevas pruebas de UI no se ejecutaron en esta validación. Esta evidencia no afirma ejecución de pruebas instrumentadas ni capturas Light/Dark.

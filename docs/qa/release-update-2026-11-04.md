# Actualización release Kipu — 2026-11-04

## Resultado

Actualización autorizada de `com.kipu.app` en el dispositivo conectado, sobre la instalación existente y sin desinstalar ni borrar datos. Se lanzó normalmente la actividad principal; el proceso permaneció activo tras la espera de arranque y no hubo entradas del paquete en el búfer de crashes consultado de los últimos dos minutos. Esta comprobación es de arranque, no una prueba funcional de datos financieros ni de sincronización remota.

## Compilación y controles previos

- Checkout `main`, con los cambios de recurrencia, swipe y comprobantes ya presentes y descritos en [recurrencias](recurrence-ui-2026-11-04.md) y [movimientos](movement-swipe-receipts-2026-11-04.md). `git status --short --branch` antes y después de compilar mostró los mismos cambios; se conservaron los archivos ajenos, incluido `tesis.md`.
- Se calcularon y compararon SHA-256 de los archivos versionados y no versionados de `app/src/main`, el build de app y el init script antes y después de la compilación: sin diferencias. La compilación ejecutó Kotlin release sobre los cambios actuales, incluyendo los archivos nuevos de movimientos.
- `gradlew.bat :app:assembleRelease -I gradle/run/release-debug-signing.gradle --console=plain`: **BUILD SUCCESSFUL**, con optimización R8 y reducción de recursos. El build exige configuración existente de backend no vacía; no se registran valores ni secretos.
- El init script existente asigna la firma debug local al build **release**; no se generó ninguna clave. Con `apksigner verify --print-certs`, el certificado del APK previamente instalado y el del nuevo APK tienen la misma huella SHA-256: `cb755d522a2dd5ce4fae360b33534f3594788fd01c10967917788b7a738395b8`.
- `aapt dump badging` y `aapt dump xmltree`: `applicationId=com.kipu.app`, `versionCode=1`, `versionName=0.1.0`, sin atributo `android:debuggable` en el manifiesto (valor por defecto false). La versión instalada previamente también era 1 / 0.1.0; no hubo downgrade.
- APK `app/build/outputs/apk/release/app-release.apk`, SHA-256: `039F1DDC7E8BEEAC41958050874AB2900D0D33DD9A26A01A4D3FFBB059E767F7`.

## Instalación y verificación

Se utilizó `adb.exe` de `LOCALAPPDATA/Android/Sdk/platform-tools`:

| Comando sanitizado | Resultado |
| --- | --- |
| `adb install -r app/build/outputs/apk/release/app-release.apk` | Exit 0, **Success**. |
| `adb shell monkey -p com.kipu.app -c android.intent.category.LAUNCHER 1` | Un evento de lanzamiento enviado. |
| `adb shell dumpsys package com.kipu.app` | Versión 1 / 0.1.0; `pkgFlags` sin `DEBUGGABLE`. |
| `adb shell dumpsys activity activities` y `adb shell pidof com.kipu.app` | `MainActivity` reanudada en primer plano y proceso activo tras ocho segundos. |
| `adb logcat -b crash -d -t 2m` | Ninguna entrada reciente menciona `com.kipu.app`. |
| `adb shell pm path com.kipu.app`, `adb pull <base.apk> <temporal>` y SHA-256 | APK instalado idéntico byte a byte al APK compilado. |

No se repitieron las suites focalizadas verdes documentadas en los informes anteriores; no se modificó código funcional ni se realizaron commits, push o cambios Spec Kit.

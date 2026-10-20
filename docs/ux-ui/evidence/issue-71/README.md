# Issue #71 — Fechas opcionales de deudas y metas

Validación del 20 de octubre de 2026 en `work/issues-70-71`, implementación hasta `4ebdeba`.

El control `KipuOptionalDateField` conserva la entrada manual ISO y añade calendario y **Quitar fecha**. Se usa al registrar deuda, registrar dinero prestado, editar deuda y crear/editar meta. Cancelar descarta el borrador; aceptar emite ISO; quitar emite vacío al formulario. Guardar sigue pasando por los callbacks existentes.

No hay selección automática de hoy ni una restricción nueva de fechas futuras. El calendario admite años 1–9999; una fecha manual válida fuera de ese intervalo se conserva y muestra una explicación al abrir el selector. Los milisegundos del calendario se convierten en UTC en ambas direcciones. El formulario de metas permite desplazarse con el teclado abierto y con texto grande.

## Capturas con datos ficticios

Las imágenes se obtienen de los componentes de producción mediante `OptionalDateFormsTest`, ejecutado en el celular. Los modelos «Persona de ejemplo» y «Meta de ejemplo» son sintéticos; estas capturas no contienen cuentas, saldos ni movimientos del usuario. No son capturas de un recorrido autenticado con sincronización remota.

| Formulario | Claro | Oscuro |
| --- | --- | --- |
| Registrar deuda | [Captura](payable-light.png) | [Captura](payable-dark.png) |
| Registrar dinero prestado | [Captura](receivable-light.png) | [Captura](receivable-dark.png) |
| Editar deuda | [Captura](debt_edit-light.png) | [Captura](debt_edit-dark.png) |
| Nueva meta sin fecha | [Captura](goal_new-light.png) | [Captura](goal_new-dark.png) |
| Editar meta con fecha | [Captura](goal_edit-light.png) | [Captura](goal_edit-dark.png) |
| Calendario, noviembre de 2026 | [Captura](calendar-light.png) | [Captura](calendar-dark.png) |

## Entorno y aislamiento

- Xiaomi 23129RA5FL, Android 15 / API 35, 1080 × 2400 px, 440 dpi.
- APK `lab` separado (`com.kipu.app.lab`), sin configuración de Supabase. Español de Perú (`es-PE`) aplicado solo a esta instalación durante QA.
- Texto al 160 % mediante `LocalDensity` en el fixture del formulario; no se cambia la escala global del celular.
- La persistencia se verifica con Room en memoria y el flujo local existente; en metas el programador de sincronización está sustituido por un mock. La base se destruye al terminar cada prueba.
- Las entidades de control mantienen `2027-06-30`; las editadas pasan de `2026-11-10` a `2028-02-29` y luego a `null`. Se comprueba que no se escriben transacciones ni asientos contables, y que metas no añade reservas/eventos.
- TalkBack excluido de esta validación a petición del usuario. No se activa ni se certifica escucha asistida.

## Resultados

| Cobertura | Resultado |
| --- | --- |
| Conversión UTC, bisiestos, límites y zonas negativas/positivas | 2/2 pruebas unitarias aprobadas. |
| Seleccionar/aceptar, limpiar, cancelar/reabrir, vacío sin hoy automático, entrada incompleta, año heredado, estado deshabilitado, restauración del estado guardado, bisiesto y cambio de mes | 8/8 pruebas de `KipuOptionalDateFieldTest` aprobadas, incluidas las de foco y teclado. |
| Calendario → guardar ISO → quitar → guardar sin fecha en los cinco formularios; invalidación de entrada manual; texto al 160 %; capturas claro/oscuro | 7/7 pruebas de `OptionalDateFormsTest` aprobadas. |
| Persistencia y payload de limpieza de deuda, dinero prestado y meta; entidad de control intacta; sin escrituras financieras | 3/3 pruebas de `OptionalDatePersistenceTest` aprobadas. |
| Regresiones de apertura, edición descriptiva, eliminación, cuentas compatibles, metas virtuales y sustitución por límite del plan | 15/15 pruebas existentes aprobadas. |
| Compilación de app y pruebas `lab`, y APK `preview` final | `BUILD SUCCESSFUL`. |

Son 33 casos instrumentados distintos y 2 unitarios. Los resultados corresponden a ejecuciones focalizadas por clase; no se ejecutó la suite completa. Tras corregir la devolución de foco se repitieron los ocho casos del componente. Se probó Atrás para descartar el borrador y Enter para reabrir desde el botón enfocado. La restauración usa el foco de ventana y permite foco programático tras una interacción táctil, sin temporizadores en producción.

El APK final se genera con el script existente que instala junto a la app original:

```powershell
.\gradlew.bat -I gradle/preview-device.init.gradle :app:assembleDebug
```

Paquete `com.kipu.app.preview`, SHA-256 `C4FA50547C26EE7F1FED9FB3F2FA274D4AB6F10BD3296458E15BE69FD31AD211`. Se actualiza con `adb install -r`, conservando sus datos. No se realizaron operaciones contra un backend desplegado: la validación de persistencia cubre Room y los payloads locales existentes.

## Reproducción

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.kipu.app.ui.component.OptionalDatePickerConversionTest
.\gradlew.bat -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest
adb install -r app/build/outputs/apk/lab/app-lab.apk
adb install -r app/build/outputs/apk/androidTest/lab/app-lab-androidTest.apk
adb shell cmd locale set-app-locales com.kipu.app.lab --user 0 --locales es-PE
```

Clases instrumentadas: `KipuOptionalDateFieldTest`, `OptionalDateFormsTest`, `OptionalDatePersistenceTest` (paquete `com.kipu.app.ui.component`); regresiones: `PayableDebtFlowTest`, `ReceivableDebtFlowTest`, `DebtEditDetailsFlowTest` y `GoalManagementFlowTest` en sus paquetes de presentación.

Ejemplo para el componente:

```powershell
adb shell am instrument -w -r -e class com.kipu.app.ui.component.KipuOptionalDateFieldTest com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner
```

En este Xiaomi, MIUI bloquea el lanzamiento de `ActivityScenario` desde segundo plano. El auxiliar local de QA abre la misma actividad exportada cuando está esperando, con el intent de la infraestructura de Compose:

```powershell
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10008000 -n com.kipu.app.lab/androidx.activity.ComponentActivity
```

Las capturas se generan en `getExternalFilesDir(null)/issue-71`. Logs, dumps y auxiliares de trabajo quedan en `scratch/issue-71/`, excluido de Git, junto con el plan local de la raíz. Solo se versionan las capturas sintéticas y este registro de QA.

Al finalizar se deja vacía la selección de idioma propia de `lab`, sin cambiar la configuración regional del celular. Se verifica que `accessibility_enabled=0` y no haya servicios de accesibilidad habilitados.

# W5c — Reprueba final Galaxy A16

Fecha: 2026-11-02. Dispositivo RFGL70J9E2T / SM-A165M, Android 16. **Veredicto global: FALLA por clase instrumentada (3 de 23). Los bloqueos anteriores de reimportación y validación parcial están corregidos en la prueba manual.**

Se leyó `.run/w5b/report.md`. Se usó exclusivamente el adb de `%LOCALAPPDATA%/Android/Sdk/platform-tools/adb.exe`. Sin modificaciones de código, commits, connectedAndroidTest, desinstalación ni borrado de datos. No se pulsó Guardar en la cuenta real ni Registrar movimientos. Solo se escribieron 123 y abc (filtrado); nunca cuatro dígitos. Evidencias privadas contienen datos reales: no publicar.

| Punto | Resultado | Evidencia |
|---|---|---|
| 1. Instalación encima y sesión | PASA | `install.log`: `:app:installDebug :app:installDebugAndroidTest`, BUILD SUCCESSFUL in 16s, ambos instalados. `01-launch.png` y `34-final-money.png`: sesión conservada y mismos saldos. Buffer `crash-logcat.txt` vacío y sin FATAL EXCEPTION / Fatal signal / fallo de migración en `full-logcat.txt`. |
| 2a. Campo vacío, validación parcial y letras | PASA funcional | `03-edit-empty.png`: Últimos 4 dígitos vacío. `04-three-digits.png`: 123, error «Deben ser exactamente 4 números» y Guardar gris. En XML el padre clickable de Guardar tiene enabled=false (el TextView hijo tiene enabled=true y NO determina habilitación). `05-letters.xml`: tras adb input text abc permanece 123. Se cerró con X sin guardar. |
| 2b. Sin tarjeta creada ni persistencia | PASA visible | `17-debit-guide.png`: selector débito con Todas (0), Cuentas (0), Tarjetas (0). `35-final-empty.png`: campo nuevamente vacío al relanzar tras instrumentación. `36-clean-final.png`: total S/784.21, BCP S/388.71, efectivo S/395.50, iguales al inicio. No se auditó backend remoto. |
| 3a. Agosto previamente descartado abre revisión nueva editable | PASA | `16-review-loaded.png`: 165 líneas, menú y Registrar presentes, sin «Esta importación fue descartada». `18-cargo-edit.png` y `22-abono-edit.png` demuestran edición disponible. Se infiere nueva revisión por comportamiento; no se leyó ID interno de sesión. |
| 3b. Resumen | PASA | `16-review-loaded.png`: Saldo anterior → Saldo final; S/826.75 → S/881.49; Cargos −S/3,748.99; Abonos +S/3,803.73; Verificado; sin interrogaciones en resumen. |
| 3c. Guía sin débito | PASA en revisión; FALLA literal dentro del selector | `16-review-loaded.png`: texto exacto «Agrega los últimos 4 dígitos de tu tarjeta de débito en Cuentas › editar cuenta» en el control amarillo. Al tocarlo abre selector vacío (`17-debit-guide.png`) que dice «No hay instrumentos disponibles en esta categoría», pero no repite la guía dentro del selector. Si se exige que la guía aparezca después de tocar, esa ubicación no cumple. |
| 3d. Cargo → gasto / Abono → ingreso | PASA | `18-cargo-edit.png` Cargo Yape Doris Luq → `19-cargo-category.png`: «Selecciona una categoría o subcategoría para clasificar tu gasto». `22-abono-edit.png` Abono TRAN.CEL.BM. +14.89 → `23-abono-category.png`: mismo subtítulo terminado en «ingreso». Se cancelaron ambos editores sin guardar. |
| 3e. Descartar y reimportar agosto otra vez | PASA | `25-discard-dialog.png` → `26-discarded.png`; mismo archivo original elegido de nuevo en `27-second-picker.png`; `29-second-loaded.png` vuelve con revisión activa y `30-second-editable.png` abre editor. Segundo descarte `31-second-discard-dialog.png` → `32-second-discarded.png`, pantalla sin pendientes. No se alteró PDF ni se forzó deduplicación. |
| 4. Limpieza | PASA | Ambos borradores descartados; archivo agosto eliminado de Download (`download-final.txt`). `animation-scales.txt`: window/transition/animator en 1.0. `36-clean-final.png`: app en inicio con saldos originales. |
| Adicional: AccountDetailScreenTest por am instrument | FALLA | `instrumentation.log`: Tests run: 23, Failures: 3 (20 pasan). `partialDebitDigitsShowErrorAndDisableSave` pasa. No se repitió suite ni se ejecutó connectedAndroidTest. |

Comando ejecutado:

```text
adb -s RFGL70J9E2T shell am instrument -w -r -e class com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreenTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner
```

El proceso adb finaliza con exit code 0, pero el resultado real del runner es FAILURES / 3 fallas; no se interpreta ese exit code como GREEN. INSTRUMENTATION_CODE=-1 corresponde al fin de instrumentación, no invalida los tres fallos de JUnit.

Fallas instrumentadas, sin modificaciones ni reintentos:

1. `savesAliasAndPresentationWithoutChangingOpeningBalance`: expected `<Ahorro de viaje>` but was `<null>` (`instrumentation.log`, líneas 41 y 139 en adelante).
2. `legacySavingsWalletPresetDisplaysAndSavesAsOther`: expected `<GENERIC>` but was `<null>` (líneas 250 y 348 en adelante).
3. `creditCardDetailShowsCanonicalLimitAndAvailableCreditCopy`: AssertionError en `AccountDetailScreenTest.kt:337`, consulta `FinancialTerminology.AVAILABLE_CREDIT_NOT_OWN_MONEY` sin nodos (líneas 697 y 704 en adelante). No se determina aquí si es defecto de UI o de sincronización/fixture de la prueba.

Observaciones adicionales de UI:

- El campo no muestra literalmente «opcional» junto al rótulo; vacío es el estado inicial, sin error. No se guardó vacío para validar persistencia por la restricción de cuenta real.
- Cerrar la edición con X y reabrir en la misma pantalla conserva el borrador 123 y su error (`09-close-edit.png` seguido de `10-empty-reopen.png`, este último nombre NO representa su contenido: contiene 123). Al salir de la pantalla y relanzar la app desaparece (`35-final-empty.png`). Es retención de borrador visible, no prueba de guardado de tarjeta; el selector permaneció en cero. `06-empty-reopened` y `08-empty-after-cancel` también contienen 123: usar contenido/XML, no sus nombres tentativos.

Pendiente para el coordinador: resolver/triagear tres fallas instrumentadas y decidir si la guía debe repetirse dentro del selector y si cancelar debe limpiar el borrador inmediatamente. No quedó importación confirmada, tarjeta creada observable, PDF de agosto en Download ni escala distinta de 1.0.

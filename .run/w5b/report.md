# W5b — Reprueba focalizada en Galaxy A16

Fecha: 2026-11-02. Dispositivo: RFGL70J9E2T / SM-A165M. **Veredicto: FALLA / cobertura incompleta.**

Se leyó `.run/w5/report.md` y se instaló encima con `./gradlew :app:installDebug`; no se modificó código ni se hicieron commits. No se ejecutó connectedAndroidTest, no se borraron datos ni se desinstaló la app. Evidencia local privada con datos reales: `.run/w5b/`.

| Punto | Resultado | Evidencia y alcance |
|---|---|---|
| 1. Instalación y sesión | PASA | `install.log`: BUILD SUCCESSFUL in 31s, Installed on 1 device. Sesión conservada, cuenta BCP visible; `03-loaded.png`. Buffer crash vacío en `crash-logcat.txt`; búsqueda FATAL EXCEPTION / fallo de migración sin coincidencias en `full-logcat.txt`. |
| 2a. Campo vacío y cancelación | PASA | Campo Últimos 4 dígitos visible y vacío. `28-empty-field-stable.png` confirma que sigue vacío después de cancelar la prueba; `29-account-final.xml` y `30-money-final.xml` conservan BCP S/388.71 y total S/784.21. No se introdujeron cuatro dígitos, no se pulsó Guardar cambios y no se observó tarjeta creada. |
| 2b. Letras | PASA filtrado | Con `123` presente, `adb shell input text abc` no agrega letras: `08-letters.xml` conserva únicamente `123`. No se guardó. |
| 2c. Rechazo de tres dígitos | FALLA de cobertura, NO confirma defecto de persistencia | `07-three-stable.png` muestra `123`, sin error visible; Guardar cambios continúa habilitado (`08-letters.xml`). No se pulsó Guardar para cumplir la restricción de salir sin guardar en la cuenta real: el rechazo al enviar NO está certificado. Tampoco se muestra literalmente la palabra opcional junto al campo. |
| 3a. Resumen agosto | PASA | `16-review-stable.png` / XML: Saldo anterior → Saldo final; S/ 826.75 → S/ 881.49; −S/ 3,748.99; +S/ 3,803.73. Sin interrogaciones en el resumen. |
| 3b. Guía débito | PASA texto; FALLA interacción bloqueada | La guía exacta «Agrega los últimos 4 dígitos de tu tarjeta de débito en Cuentas › editar cuenta» aparece en `16-review-stable.png`. Tocar Elige tu tarjeta de débito no abre selector: `17-debit.xml` conserva pantalla, porque el documento se reabre ya descartado. |
| 3c. Categoría Abono/ingreso y Cargo/gasto | FALLA de cobertura por bloqueo | La revisión indica «Esta importación fue descartada» desde que se eligió el PDF original; no ofrece menú/Registrar. Tocar Cargo Yape Doris Luq no abre editor (`18-cargo-attempt.xml`), ni Abono TRAN.CEL.BM. +14.89 (`19-lines.xml`, `20-abono-attempt.xml`). No se puede certificar el arreglo de subtítulos. |
| 4. Limpieza | PASA | La importación ya estaba descartada y se salió sin confirmar ni crear borrador activo visible (`21-import-after-exit.xml`, pantalla sin pendientes). PDF copiado eliminado de Download: `download-final.txt`. Escalas verificadas 1.0/1.0/1.0: `animation-scales.txt`. Campo vacío al reabrir y saldos conservados. |

## Bloqueo reproducible

1. En el teléfono usado en W5, cuyo PDF agosto fue descartado entonces, instalar APK corregido sin borrar datos.
2. Movimientos → Importar movimientos → Estado de cuenta BCP → elegir `EECC082026_14811479-1_unlocked.pdf` copiado a Download.
3. Abre directamente la revisión con «Esta importación fue descartada», sin posibilidad de editar filas ni elegir tarjeta; sin menú para descartar nuevamente.

Esto impide repetir la revisión editable del mismo archivo tras descarte. No se alteró el PDF para evitar la deduplicación, ni se tocaron bases de datos locales/remotas para forzar una sesión distinta.

## Límites y transparencia

- La prueba comprueba estado visible y ausencia de acciones de guardado, no realiza auditoría de tablas remotas. No se ejercitó RPC de creación/actualización de tarjeta.
- Algunas capturas inmediatas preceden a la estabilización de la animación; para campo usar `28-empty-field-stable.png`, para tres dígitos `07-three-stable.png` y para resumen `16-review-stable.png`; los XML asociados son posteriores al dump de UI.
- En navegación final se abrió accidentalmente el diálogo de archivar por un tap antes de estabilizarse la pantalla (`25-account.xml`); se pulsó Cancelar, nunca Archivar (`26-cancel-archive.xml`), y la cuenta sigue visible. No se guardaron cambios de cuenta ni preferencias.
- Pendiente: habilitar reprueba editable del PDF descartado y verificar los dos subtítulos; para certificar rechazo al enviar tres dígitos sin riesgo sobre cuenta real, usar cuenta de prueba autorizada o validación focalizada separada.

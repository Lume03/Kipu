# W5 — Prueba funcional en Galaxy A16

Fecha: 2026-11-02. Dispositivo RFGL70J9E2T / SM-A165M. Veredicto: FALLA (defectos funcionales/visuales y cobertura limitada por fixtures disponibles).

No se editó código ni se hicieron commits. Se probó el APK instalado al inicio de esta ejecución; modificaciones posteriores de otros agentes no quedan certificadas por este informe. Evidencia privada local: no publicar capturas/logs con datos reales. `git status` muestra `.run/` sin seguimiento, por lo que no debe asumirse que esté ignorada.

| Paso | Resultado | Evidencia / observación |
| --- | --- | --- |
| 1. Instalación y arranque | PASA | `install.log`: BUILD SUCCESSFUL, instalación encima de app y test APK. Sesión conservada, cuentas visibles en `02-open.png`. Sin crash ni fallo de Room observado. La versión previa de la BD del usuario no se consultó: el arranque real sin error y la migración sintética se verificaron por separado. |
| 2. Migración instrumentada | PASA | `migration-test.txt`: OK (2 tests), 1.097 s; incluye 32→33 y 29→30. Se ejecutó exclusivamente `am instrument -w -e class com.kipu.app.core.database.StatementRoomMigrationTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner`. |
| 3. PDF agosto | PASA | Copiado el archivo solicitado a Download, posteriormente eliminado. |
| 4. Lectura y revisión agosto | PASA en datos, FALLA visual | `08-reading.png`: lectura sin error; `10-review.png`: BCP · Cuenta de ahorros, Cuenta •••• 0022, 1 ago–31 ago 2026, cargos 3,748.99, abonos 3,803.73, saldo anterior 826.75, saldo final 881.49 y Verificado. 165 líneas / 159 seleccionadas. `16-lines.png`: Cargo y Abono; TRANSF.BCO.NACION +499.71 tiene chip ¿Transferencia propia? y checkbox sin seleccionar. No se verificó cada aparición de Yape Jorge Luq; la fila visible Cargo −30.00 estaba seleccionada, sin chip. |
| 5. Selector débito y edición | FALLA en categorías de Abono; selector sin datos | `11-debit-selector.png`: Elegir tarjeta de débito, todas las pestañas con 0 instrumentos; ninguna tarjeta de crédito ofrecida. No fue posible elegir tarjeta. Cargo Yape Doris Luq: `12-edit-cargo.png` / `13-cargo-categories.png` ofrecen gasto correctamente. Abono TRAN.CEL.BM. +14.89: `17-edit-abono.png` / `18-abono-categories.png` muestran Alimentación, Transporte, Servicios y texto gasto, incorrectamente. Ambas hojas cerradas con Cancelar, sin guardar. |
| 6. Descartar y duplicados | PASA descarte y reutilización; FALLA aviso esperado | Agosto descartado mediante menú y confirmación (`19-menu.png`, `20-discard.png`, `21-discarded.png`). Julio 14814628 importado como borrador, se salió con flecha; al importar 14829113 vuelve a la revisión del mismo periodo y después hay un único borrador listado (`25-july-back.png`, `28-second-july-review.png`, `29-dedup-one-draft.png`). No apareció aviso ya existe/ya importaste. El código consultado solo emite ALREADY_IMPORTED si completed=true; esta prueba no confirma movimientos y por tanto cubre borrador repetido, no duplicado de importación completada. Julio descartado mediante menú y confirmación (`31-july-menu.png`, `32-july-discard-confirm.png`, `33-july-discarded.png` sin borradores). |
| 7. Regresión crédito | NO PROBADO: sin fixture | En docs/ solo están los cuatro estados de ahorro julio/agosto/septiembre. Download original contiene dos cierres de deuda y un comprobante de gasto, ningún estado BCP Visa crédito. No se generó ni inventó un PDF de crédito. |
| 8. Editar last4 | NO PROBADO: sin tarjeta vinculada | Única cuenta de ahorros visible BCP, detalle sin tarjeta vinculada (`35-account-detail.png`), edición con Nombre/Institución únicamente (`36-edit-account.png`), coherente con selector de débito vacío. No se crearon datos ni se cambió last4. Validación de 3 dígitos/letras y RPC no ejercitadas; ausencia de errores del RPC no prueba su éxito. |
| 9. Limpieza | PASA | Agosto y julio descartados; no se pulsó Registrar ni se guardó edición de cuenta. `37-final-accounts.png` conserva importes iniciales. Los tres PDFs copiados eliminados; archivos originales de Download conservados (`download-final.txt`). Las tres escalas fijadas explícitamente y verificadas a 1.0 (`animation-scales.txt`). |

## Defectos reproducidos

1. **Abono ofrece categorías de gasto.** Importar agosto → desplazar hasta 3 ago → abrir TRAN.CEL.BM. +14.89 (Abono) → Elegir categoría. Resultado: 51 categorías de gasto y texto «clasificar tu gasto», con Alimentación/Transporte/Servicios. Esperado: categorías de ingreso. Evidencia correlacionada `17-edit-abono.png` y `18-abono-categories.png`; comparación Cargo `13-cargo-categories.png`.
2. **Signos de interrogación literales en resumen.** Importar agosto y ver hero: «Saldo anterior ? Saldo final», «S/ 826.75 ? S/ 881.49» y «?S/ 3,748.99». Confirmado visualmente en `10-review.png`, no es únicamente la codificación del dump. Esperado: flecha de saldo y signo correcto de cargo.
3. **Sin aviso para PDF equivalente ya pendiente.** Importar julio 14814628 → salir con flecha dejando borrador → importar julio 14829113. Retoma revisión sin aviso de duplicado; se conserva un borrador. Discrepancia con el aviso solicitado, no evidencia de duplicación de movimientos. Capturas `25-july-back.png`, `28-second-july-review.png`, `29-dedup-one-draft.png`.

## Logcat y resultados

- `crash-logcat.txt`: vacío al consultar buffer crash.
- `launch-logcat.txt`: captura desde arranque posterior a instalación; no se encontraron FATAL EXCEPTION, Migration didn't properly handle, migración requerida ni IllegalStateException.
- `full-logcat.txt` y `app-logcat.txt`: copias locales; búsquedas de `update_debit_card_identity_v1`, FATAL EXCEPTION y errores de migración sin coincidencias. RPC no ejercitada al faltar tarjeta.
- `app-logcat.txt` contiene actividad normal de UI, por ejemplo `11-02 15:28:26.745 14236 14236 I VRI[MainActivity]@bd0addd: synced displayState. AttachInfo displayState=2`.
- La captura genérica `relevant-logcat.txt` incluye advertencias SQLite de otros procesos Samsung/Google; no se atribuyen a Kipu.
- Extracto literal `migration-test.txt`: `com.kipu.app.core.database.StatementRoomMigrationTest:..`, `Time: 1.097`, `OK (2 tests)`.
- Comparación de `git-before.txt` y `git-after.txt`: sin diferencias en nombres/estados; no acredita contenido estable si otro agente edita archivos concurrentemente.

Pendiente: corrección de defectos por el coordinador, repetición focalizada sobre APK corregido y fixtures reales autorizados de tarjeta de débito/PDF de crédito para cubrir los casos omitidos.

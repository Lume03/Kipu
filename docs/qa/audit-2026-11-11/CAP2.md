# CAP2 — receipts + statements (parsing)

Esta porción convierte capturas de recibos Yape (OCR local con ML Kit + `YapeReceiptParser`) en candidatos revisables, y PDFs de estados de cuenta BCP (Visa y ahorros, vía PDFBox + parsers por posición) en borradores de importación que se confirman línea a línea contra el ledger. Carga asumida: miles de usuarios, un usuario con miles de movimientos y 1-2 PDFs mensuales; uso offline; los PDFs y capturas son entradas no confiables (el formato del banco puede cambiar).

Lectura completa: parsers (Yape, BCP Visa, BCP ahorros), `ConfirmStatementImport`, `ImportCardStatement`, `PrepareStatementImportReview`, modelos/store/DAO/entidades de statements y receipts, `AndroidReceiptImageRecognizer`, `PdfBoxStatementDocumentReader`, ambos ViewModels de statements, `StatementDesign/Messages/LineEditSheet`, `ReceiptReviewScreen`. Tests: leí los parsers Visa y ahorros completos; del resto solo nombres y casos clave.

## Hallazgos

### [CAP2-01] Al elegir tarjeta a mano en un estado Visa no se recalculan los duplicados
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt:78-103 (duplicados solo en 96-100); app/src/main/java/com/kipu/app/feature/statements/domain/ImportCardStatement.kt:45-58
- Qué está mal: `hasDuplicate` solo se evalúa al crear el import y solo si la tarjeta ya se resolvió sola (`card != null`). Si no hubo match (últimos 4 dígitos distintos o ausentes, varias tarjetas), todas las líneas nacen `selected=true, possibleDuplicate=false`. `setCard` para tarjetas de crédito solo cambia `card_id`/fingerprint; el bloque que recalcula duplicados y des-selecciona existe únicamente para `deposit` (débito).
- Escenario concreto de fallo: el usuario ya registró (manual o por captura) la compra "TIENDA 50.00" del 30/12 en su Visa; importa el PDF, la tarjeta no se auto-asocia, la elige en el selector → la línea "TIENDA 50.00" sigue seleccionada y sin marca de duplicado; "Registrar" crea un segundo gasto con otra clave idempotente (la del estado) y duplica gasto y deuda de la tarjeta.
- Fix mínimo: en `setCard`, rama crédito, repetir el bucle de las líneas 96-100 usando `matching.hasDuplicate(owner, cardId, amount, currency, occurredDate)` + `dao.setDuplicate`. (Extraer el bucle a una función compartida por ambas ramas.)
- Confianza: alta (leí create, setCard, VM y el test de Room; este solo cubre duplicados en la rama deposit, `StatementImportRoomTest.kt:277`).
- Cross-módulo: no

### [CAP2-02] Filas de resumen/cargo con importe negativo se importan como cargo positivo
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/BcpVisaStatementParser.kt:82-90 y 121-141
- Qué está mal: en las filas de resumen (intereses, seguros, comisiones) el total se suma con signo (`state.summary`, línea 86) pero la línea candidata se crea con `abs(amount)` y tipo INTEREST/INSURANCE/FEE seleccionada por defecto (línea 87). Lo mismo en `parseMovement`: operación `CARGO` con importe `5.00-` da `postedAmount = +amount`. El signo se pierde.
- Escenario concreto de fallo: el PDF trae "EXTORNO INTERES MORATORIO 10.00-" (o una bonificación con "INTERES"/"SEGURO"/"COMISION" en el texto) → se propone un interés de +10.00 marcado para registrar; al confirmar sube la deuda de la tarjeta en vez de bajarla. La conciliación sí falla (banner), pero la línea sigue seleccionada.
- Fix mínimo: si `amount < 0` en esas dos ramas, emitir `StatementLineKind.REFUND` (o `UNKNOWN`, no seleccionada) con `abs`, y llevar el signo real a `posted`/`summary`. Alternativa más corta: `defaultSelected=false` para importes negativos.
- Confianza: media (no vi un PDF real con estas filas; el test `parses thousands separators and summary fees` solo usa importes positivos).
- Cross-módulo: no

### [CAP2-03] El parser de ahorros descarta filas y páginas sin avisar; el banner de descuadre no señala nada
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:47-48 (página sin anclas se salta) y 70-72 (fila con `values.size != 1` se salta); app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt:142-146
- Qué está mal: una fila con fecha pero con 0 o 2 importes legibles, o una página de continuación sin repetir "CARGOS / DEBE / ABONOS / HABER", se ignora en silencio (el parser Visa, en cambio, arrastra `columns` entre páginas). Lo único que lo delata es `reconciledPen=false`, que solo muestra un banner que dice "Revisa los movimientos marcados", pero ningún movimiento está marcado. La importación no se bloquea.
- Escenario concreto de fallo: página 2 del PDF de ahorros sin cabecera repetida → se pierden todos sus movimientos; el usuario ve 12 de 30 líneas, un banner ambiguo, pulsa "Registrar" y su saldo queda sin esos movimientos.
- Fix mínimo: (a) reutilizar las anclas de la página anterior (variable fuera del bucle, como hace Visa) en vez de `continue`; (b) texto del banner sin "marcados" o marcar las líneas; opcional: pedir confirmación extra si `matches=false`.
- Confianza: media (no pude comprobar si BCP repite siempre la cabecera; el descarte silencioso es seguro por lectura).
- Cross-módulo: no

### [CAP2-04] Sin límite de tamaño ni de páginas al abrir el PDF: un OOM cierra la app
- Severidad: media
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/data/PdfBoxStatementDocumentReader.kt:27-53
- Qué está mal: se carga todo con `MemoryUsageSetting.setupMainMemoryOnly()` (decisión de privacidad correcta) y se extrae texto de cada página a una lista de `StatementWord`, sin tope. Solo se capturan `IOException/SecurityException/IllegalArgumentException`; un `OutOfMemoryError` no se captura. El recognizer de recibos sí acota (12 MB, 40 MP).
- Escenario concreto de fallo: el usuario elige por error un PDF de 150 páginas / 80 MB desde el selector → PDFBox agota el heap y Android mata la app, sin mensaje.
- Fix mínimo: leer con tope (como `readBounded` de recibos): rechazar > ~15 MB o > ~30 páginas devolviendo `Unreadable`.
- Confianza: media
- Cross-módulo: no

### [CAP2-05] La tarjeta "Registrados desde Yape" recarga todos los movimientos en cada cambio
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/receipts/data/local/RoomReceiptCandidateStore.kt:94-111
- Qué está mal: `observeRegistered` combina `observeConfirmed` con `movements.observeTransactions(owner)` (toda la tabla del usuario), construye un mapa de todos los activos y hace un `getReceipt` por cada candidato confirmado hasta juntar 3. Solo se muestran 3 filas.
- Escenario concreto de fallo: usuario con 5.000 movimientos y 300 recibos confirmados abre la bandeja de recibos: cada escritura en `transactions` (también de sync) vuelve a emitir y recorre las 5.000 filas + hasta 300 consultas.
- Fix mínimo: una consulta DAO con JOIN entre los recibos confirmados y `transactions` activas, `ORDER BY updated_at DESC LIMIT 3`.
- Confianza: alta
- Cross-módulo: no (usa DAOs de movements, pero el defecto es de esta consulta)

### [CAP2-06] Conciliar una devolución carga todos los movimientos dentro de una transacción de escritura
- Severidad: baja
- Categoría: escala
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt:239-256 (llamado desde la 218, dentro de `transactions { }` en la 102)
- Qué está mal: `refundPurchase` hace `movements.observeTransactions(ownerId).first()` (todo el historial), filtra en memoria y llama `refunds.getRefundedAmount` por candidato; se ejecuta por cada línea REFUND con la transacción de Room abierta.
- Escenario concreto de fallo: estado con 8 devoluciones y 6.000 movimientos → 8 cargas completas del historial con el lock de escritura tomado; otras pantallas que escriben se bloquean unos segundos.
- Fix mínimo: consulta DAO acotada por `card_id`, moneda y ventana de fechas (ya se filtra por 120 días) en lugar de cargar todo.
- Confianza: alta
- Cross-módulo: no

### [CAP2-07] Confirmar un recibo no es atómico y tiene un reintento sin sentido
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/receipts/domain/ConfirmReceiptCandidate.kt:23-37
- Qué está mal: `store.edit`, `registerTransaction` y `markConfirmed` son tres operaciones separadas, sin `DatabaseTransactionRunner` (statements sí lo usa). Además `if (Success && !markConfirmed) markConfirmed(...)` reintenta inmediatamente la misma llamada e ignora el resultado.
- Escenario concreto de fallo: el proceso muere entre `registerTransaction` y `markConfirmed` → el movimiento existe pero el candidato sigue PENDING en la bandeja; el usuario lo ve "listo" y lo confirma otra vez (la clave idempotente evita duplicar, pero la UX es confusa).
- Fix mínimo: envolver las tres llamadas en `transactions { }` y borrar el segundo `markConfirmed`.
- Confianza: media (no verifiqué qué devuelve `registerTransaction` al replay de la misma clave)
- Cross-módulo: no

### [CAP2-08] Los motivos de fallo de pago/registro se deducen del texto del mensaje de error
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt:177-180 y 258-264
- Qué está mal: `contains("Insufficient funds")`, `"exceeds current card debt"`, `"currency mismatch"` y, como último caso, `contains("account")` → `SourceAccountInvalid`. Si alguien cambia o traduce el mensaje en `PayCreditCard`/`RegisterTransaction`, el motivo cae a `RegistrationFailed`; y cualquier mensaje que contenga "account" se muestra como cuenta de origen inválida.
- Escenario concreto de fallo: PayCreditCard falla con "Credit card account archived" → el usuario lee "La cuenta de origen no está disponible o tiene otra moneda" y busca el problema en la cuenta equivocada.
- Fix mínimo: que los casos de uso devuelvan un error tipado y mapear por tipo; mientras, quitar el `contains("account")`.
- Confianza: alta
- Cross-módulo: sí → ACC1 (mensajes de `PayCreditCard`) / MOV1 (`RegisterTransactionResult.Failure`)

### [CAP2-09] Moneda de la cuenta de ahorros: se busca "SOLES" antes que "DOLARES" en toda la primera página
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:27,33-37
- Qué está mal: `header` es el texto completo de la página 1 y se decide PEN si aparece la palabra suelta SOLES en cualquier lugar.
- Escenario concreto de fallo: estado de una cuenta en dólares cuya primera página menciona "soles" (tipo de cambio, nota al pie) → se lee como PEN. No se registra mal por la comprobación `CurrencyMismatch` posterior, pero la cuenta débito USD no se puede elegir y las cifras salen con "S/".
- Fix mínimo: tomar la moneda de la celda bajo la cabecera "MONEDA" (el test ya la posiciona, `500f to "SOLES"`) en vez de buscar en todo el texto.
- Confianza: baja (no vi PDFs reales de cuenta USD)
- Cross-módulo: no

### [CAP2-10] Yape: "S/ 1,500" o "1.500" (miles sin decimales) se descarta como inválido
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/receipts/domain/YapeReceiptParser.kt:197-215
- Qué está mal: con un solo separador y 3 dígitos tras él se devuelve `Invalid`, aunque en Perú la coma es separador de miles y ningún importe en soles tiene 3 decimales. No inventa un valor (es seguro), pero el candidato nace sin monto.
- Escenario concreto de fallo: comprobante "¡Yapeaste! S/ 1,500" → `amountMinor=null`, `MISSING_AMOUNT`; el usuario debe teclear el monto aunque el OCR lo leyó bien. No hay test (los de miles solo usan `1,200.50` y `1.200,50`).
- Fix mínimo: en la rama `count == 1` con `fracStr.length == 3`, tratar la coma como miles (`1,500` → 150000) y dejar `.` con 3 dígitos como inválido; añadir un test.
- Confianza: media (no sé cómo formatea Yape los miles)
- Cross-módulo: no

### [CAP2-11] Los borradores vencidos de estados de cuenta solo se borran cuando el usuario vuelve a importar
- Severidad: baja
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/ImportCardStatement.kt:32; app/src/main/java/com/kipu/app/feature/statements/presentation/StatementImportViewModel.kt:83; app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt:61,141
- Qué está mal: `observe` oculta los borradores vencidos (7 días) pero el `DELETE` solo corre al abrir la pantalla de importar o al importar (grep: no hay worker; el de capture no toca esta tabla). Las líneas guardan descripciones de comercio e importes del banco.
- Escenario concreto de fallo: el usuario importa un PDF, no confirma y no vuelve a la función → descripciones y montos de su estado de cuenta quedan en la base local indefinidamente aunque la UI dice que vencen.
- Fix mínimo: llamar `purgeExpired` desde el worker de purga existente (`EvidencePurgeWorker`) o al iniciar sesión.
- Confianza: alta
- Cross-módulo: sí → CAP1 (el worker vive allí; la llamada se añadiría ahí)

### [CAP2-12] Tests que no prueban nada o que nunca se ejecutan en CI
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/receipts/domain/YapeReceiptParserTest.kt:365-390; app/src/test/java/com/kipu/app/feature/statements/BcpSavingsLocalStatementTest.kt:23-47 y 49-77; app/src/test/java/com/kipu/app/feature/statements/PdfBoxStatementDocumentReaderTest.kt:80-102
- Qué está mal: (1) `same amount but different reference are not equal` compara dos `data class` con `assertNotEquals`: prueba el compilador, no el parser. (2) `real local PDFs report merchant suggestion coverage` termina en `assertTrue(suggested in 0..total)`, que no puede fallar. (3) Los tres tests con PDFs reales dependen de `docs/EECC/*.pdf`, que no está versionado (`git ls-files docs/EECC` vacío) y usan `assumeTrue`: en CI se saltan siempre, así que la conciliación sobre PDFs reales no está protegida.
- Escenario concreto de fallo: un cambio en `BcpVisaStatementParser.findColumns` rompe el parseo de PDFs reales y la suite sigue en verde.
- Fix mínimo: borrar (1) y (2) (~45 líneas); para (3), mantener un PDF sintético anonimizado como fixture o documentar que es solo manual.
- Ahorro estimado: 45 líneas
- Confianza: alta
- Cross-módulo: no

### [CAP2-13] Los dos parsers BCP duplican utilidades y ya divergen en la validación de fechas
- Severidad: baja
- Categoría: fusionar
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:29,52-56,98-100 vs BcpVisaStatementParser.kt:185-193,234-241
- Qué está mal: `inferDate`, el mapa de meses, la regex `shortDate` y la lectura de fechas/importes están copiados. Visa usa `ResolverStyle.STRICT` (31/02/26 es inválido); ahorros usa `DateTimeFormatter.ofPattern(...)` con el resolver por defecto (SMART), que ajusta 31/02/26 a 28/02/26 sin error. Además el `money` de ahorros no admite signo y el de Visa sí.
- Escenario concreto de fallo: un periodo "DEL 01/02/26 AL 31/02/26" mal extraído se acepta como válido en ahorros y se rechaza en Visa.
- Fix mínimo: mover `inferDate`, `months`, `shortDate`, `parseMinor` y el formateador estricto a `StatementLayout.kt` (internal) y usarlos en ambos.
- Ahorro estimado: 25 líneas
- Confianza: alta
- Cross-módulo: no

### [CAP2-14] Código muerto en recibos y statements
- Severidad: baja
- Categoría: borrar
- Ubicación: app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptReviewScreen.kt:422-437 (`ReceiptShareConsentDialog`, sin usos) y 93,98 (`onEdit` con `@Suppress("UNUSED_PARAMETER")`); app/src/main/java/com/kipu/app/feature/receipts/data/local/ReceiptCandidateDao.kt:14-15 (`all`); app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportDao.kt:37-38 (`observe`); app/src/main/java/com/kipu/app/feature/statements/presentation/StatementDesign.kt:90-91 (`maybeOwnTransfer()`, envoltorio de `possibleOwnTransfer`)
- Qué está mal: grep en `app/src` (main, test, androidTest) no encuentra otros usos de `ReceiptShareConsentDialog`, `ReceiptCandidateDao.all` ni `StatementImportDao.observe`; `onEdit` se pasa en las llamadas pero nunca se lee.
- Escenario concreto de fallo: no hay fallo; son líneas que mantener, y un diálogo de consentimiento sin uso puede desincronizarse del texto real de consentimiento.
- Fix mínimo: borrar los símbolos, quitar `onEdit` y sus argumentos (tests de UI en androidTest, incluido `MovementSwipeReceiptsTest` de movements), sustituir `maybeOwnTransfer()` por `possibleOwnTransfer`.
- Ahorro estimado: 30 líneas
- Confianza: alta
- Cross-módulo: no

### [CAP2-15] La prueba de avisos no comprueba las acciones del comprobante duplicado
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/receipts/presentation/ReceiptExperienceTest.kt:95-106; app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptImportNoticeSheet.kt:90-102
- Qué está mal: la prueba `allImportNoticesExposeTheirMatchingActionAndAnotherImage` solo comprueba textos; todos los callbacks son vacíos y nunca pulsa el botón principal. No detecta que un aviso invoque la acción de otro estado.
- Escenario concreto de fallo: si `ALREADY_DISCARDED` se conecta por error a `onMovement` en vez de `onRestore`, la prueba sigue en verde aunque «Agregar de nuevo» no restaure el comprobante.
- Fix mínimo: pulsar `receipt_notice_primary` para `ALREADY_DISCARDED` y comprobar que se invoca `onRestore` y no `onMovement`.
- Confianza: alta
- Cross-módulo: no

### [CAP2-16] Un error inesperado de lectura desaparece sin aviso
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/presentation/StatementImportViewModel.kt:219-226; app/src/main/java/com/kipu/app/feature/statements/presentation/ImportMovementsScreen.kt:147-207
- Qué está mal: el ViewModel guarda el mensaje de error en `state.message`, pero la pantalla de importación no lo muestra. El mismo problema afecta los errores al cargar borradores o limpiar vencidos.
- Escenario concreto de fallo: el lector o el almacenamiento lanza una excepción inesperada → termina la animación de lectura y vuelve la pantalla inicial sin explicar que falló ni cómo reintentar.
- Fix mínimo: mostrar `state.message` en la pantalla y conectar una acción para descartarlo; añadir un test que fuerce una excepción y compruebe que se ve el mensaje.
- Confianza: alta
- Cross-módulo: no

### [CAP2-17] Cancelar la edición conserva el comercio y puede cambiar la categoría
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/presentation/StatementLineEditSheet.kt:117-118,131-137,172-173; app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewViewModel.kt:229-235; app/src/main/java/com/kipu/app/feature/statements/domain/PrepareStatementImportReview.kt:89-94
- Qué está mal: elegir, aceptar o quitar un comercio dentro de «Editar movimiento» lo guarda inmediatamente en el borrador; al elegir uno también puede reemplazar la categoría. El botón «Cancelar» solo cierra la hoja y no revierte esos cambios, a diferencia del importe, fecha y descripción que sí esperan a «Guardar cambios».
- Escenario concreto de fallo: una compra tiene categoría Transporte; el usuario abre la edición, elige por error un comercio con categoría Comida y pulsa «Cancelar» → el borrador queda con ese comercio y categoría Comida, que después puede registrar sin advertencia.
- Fix mínimo: mantener comercio y categoría propuestos en el estado temporal de la hoja y persistirlos juntos al pulsar «Guardar cambios»; cancelar debe descartar ambos. Añadir un test que seleccione el comercio, cancele y compruebe que no cambia el borrador.
- Confianza: alta
- Cross-módulo: no

### [CAP2-18] El mensaje de éxito afirma que la deuda refleja todo el estado tras importar solo una parte
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt:567-586; app/src/test/java/com/kipu/app/feature/statements/ConfirmStatementImportTest.kt:403-414
- Qué está mal: el mensaje de éxito afirma que la deuda refleja el estado de cuenta aunque la confirmación admite excluir líneas y completar la importación con solo las seleccionadas.
- Escenario concreto de fallo: el usuario desmarca una compra de S/ 100 y registra las demás → la app confirma que la deuda refleja el estado, aunque esa compra no se agregó.
- Fix mínimo: decir que se registraron los movimientos seleccionados y que actualizaron la deuda, sin afirmar que refleja todo el estado.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests
- `StatementLineEditSheet` → elegir un comercio con categoría predeterminada, cancelar y verificar que comercio y categoría del borrador no cambian.
- `ImportMovementsScreen` → simular una excepción de importación y verificar que se muestra el mensaje de error.
- `ReceiptImportNoticeSheet` → pulsar «Agregar de nuevo» en `ALREADY_DISCARDED` y verificar que llama a `onRestore`.
- `RoomStatementImportStore.setCard` rama crédito → import con tarjeta sin match y un movimiento existente igual; `setCard` debe dejar la línea `possibleDuplicate=true, selected=false`.
- `BcpVisaStatementParser` filas de resumen/`CARGO` con importe negativo → caso "EXTORNO INTERES 10.00-" que no genere INTEREST positivo.
- `BcpSavingsStatementParser` página 2 sin cabecera de columnas y fila con dos importes → caso que verifique que no se pierden líneas (o que la conciliación falla).
- `YapeReceiptParser` importe "S/ 1,500" (y "1.500") → un caso con el resultado esperado.
- `ConfirmStatementImport.paymentFailure` con mensajes distintos (sin "account") → un caso por motivo.
- `RoomReceiptCandidateStore.import/edit` solo tiene androidTest (`ReceiptCandidateRoomTest`); no corre en la suite JVM.
- `PdfBoxStatementDocumentReader` con archivo grande/muchas páginas → un caso de tope.

## Lo que no revisé
- Lectura completa de los archivos pendientes de esta pasada: `ReceiptInboxComponents.kt`, `ReceiptReviewScreen.kt`, `StatementReviewScreen.kt`, `StatementLineEditSheet.kt`, `StatementScanOverlay.kt`, `StatementDesign.kt`, `StatementMerchantComponents.kt`, `StatementMessages.kt`, `StatementReviewViewModel.kt`, `StatementImportViewModel.kt` y `ConfirmStatementImportTest.kt`. También leí completos en la pasada anterior `ReceiptShareGuide.kt`, `ReceiptImportNoticeSheet.kt`, `ReceiptSavedContent.kt`, `ImportMovementsScreen.kt`, `YapeReceiptParserTest.kt`, `StatementImportRoomTest.kt`, `StatementViewModelsTest.kt` y los cinco androidTest.
- Los demás tests del alcance original, salvo los parsers Visa y ahorros ya identificados arriba, fueron revisados solo por nombres y casos clave en la auditoría anterior; no afirmo haberlos leído completos.
- No ejecuté pruebas, builds ni dispositivos: auditoría estática según el encargo.
- Fuera de alcance, no auditado: `RegisterTransaction`, `PayCreditCard`, `ConfirmCreditPurchase` (MOV1/ACC1), `RoomCaptureCorrelation` (CAP1), `ReceiptFlowViewModel` (NAV1).
- Menores no reportados: 3 (heurística de transferencia propia en ahorros `names.getOrNull(2)` solo desmarca por defecto; regex recompiladas por línea en ahorros; la contraseña del PDF pasa por un `String` inmutable en el estado de Compose).

## Dudas
- ¿Aparecen en los PDFs reales de BCP filas negativas de intereses/seguros/comisiones (extornos, bonificaciones)? Decide la severidad de CAP2-02.
- ¿BCP repite la cabecera "CARGOS / DEBE / ABONOS / HABER" en cada página de ahorros? Decide la severidad de CAP2-03.
- ¿Cómo formatea Yape los importes de 1.000 soles o más (con coma, sin decimales)? Decide CAP2-10.
- ¿Un Visa BCP es bimoneda y Kipu lo modela con una tarjeta por moneda? Hoy las líneas en la otra moneda quedan no seleccionables (`selectable`), lo que parece decisión de producto, pero un estado bimoneda no se puede importar completo.
- ¿La retención de 7 días de borradores es un compromiso de privacidad (entonces CAP2-11 sube a media) o solo comodidad?

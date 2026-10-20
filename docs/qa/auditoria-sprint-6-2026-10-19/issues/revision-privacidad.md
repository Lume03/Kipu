# Revisión visual de privacidad de adjuntos públicos

Revisión nativa de las tres imágenes realizada por un subagente sin ADB, modificaciones ni publicaciones. Fuente: delegación `deleg_9f83bcea`, concluida el 2026-10-20. La revisión evalúa publicación de píxeles, no reproduce los bugs ni confirma una causa.

| Archivo | SHA-256 revisado | Resultado visual | Disposición del Coordinador |
|---|---|---|---|
| `capturas/incidencias/bug-002-movimientos-blank-after-edit.png` | `0feb2b39fdf4189b6e516e38d62554a8d33446d208d159430daabeec596cad14` | SAFE_TO_PUBLISH: contenido vacío, navegación y statusbar genérica; sin datos personales visibles | Adjunto permitido para BUG-002 si el hash sigue coincidiendo; demuestra blanco, no causa ni frecuencia por sí solo |
| `capturas/incidencias/bug-003-void-blocked-linked-debt.png` | `806ed430225d8c38229812ef6fb427fd9ca0f5f831fef4bfcca52ae4efcb16ce` | NEEDS_REDACTION: aviso de anulación, Efectivo e importe S/1.00; la imagen aislada no identifica el importe como QA | NO SUBIR esta imagen; conservarla localmente bajo autoría de QA, sin modificarla desde publicación. El texto del aviso se transcribe en el issue desde el registro QA |
| `capturas/incidencias/bug-003-debt-detail-no-void-action.png` | `2a63f14e46b1286b89f38d850a193d5670ad8fc4f1956a479a76294a14f3cde8` | SAFE_TO_PUBLISH: menú Editar datos, Eliminar deuda deshabilitado y texto parcial Moneda PEN; sin datos personales visibles | Adjunto permitido para BUG-003 si el hash sigue coincidiendo; demuestra las opciones del menú, no la ausencia universal de otras rutas |

Se elige no publicar el aviso con importe en lugar de editar concurrentemente las capturas del auditor. Los originales no se borran ni se sobrescriben. Ninguna base de datos, WAL, dump, sesión o payload puede ser adjuntado.

## Ampliación — ocho capturas nuevas, 2026-10-20

Revisión visual de privacidad e integridad semántica: delegación `deleg_84e24a5f`, sin ADB, cambios de imágenes/documentos ni publicaciones. El Coordinador recalculó los ocho hashes tras recibir la revisión y todos coinciden con los bytes enviados al revisor. Resultado: **7 SAFE y 1 NEEDS_CONTEXT**. SAFE solo describe la ausencia observable de datos ajenos con el contexto disponible; no confirma un flujo, una causa, atomicidad, persistencia remota ni todos los criterios funcionales. La revisión no sustituye `revision-ui.md` o `revision-ux.md`.

| Archivo bajo `capturas/` | SHA-256 revisado y comprobado | Privacidad | Qué muestra / límite |
|---|---|---|---|
| `capturas/funcional/hu-22-search-exact-qa.png` | `27039b464fe211b22ed09408fa3644d7310c7a4434514e1a0895d8f74ca778dc` | SAFE | Búsqueda y gasto sobre wallet QA; no acredita todos los filtros ni remoto |
| `capturas/funcional/hu-22-search-partial-qa.png` | `d4e089347e2607712bd62e7bd4cb6ed141ffd922519b4113bc21a2065b8815c7` | SAFE | Búsqueda parcial y movimiento QA; no prueba todos los campos/coincidencias |
| `capturas/funcional/hu-22-search-no-results.png` | `d4bfe57088e007efa7108e1713e78ad601dbe8a13bc19f118cde60d389cc76b0` | SAFE | Estado vacío de esa consulta/filtros; no ausencia universal de resultados |
| `capturas/funcional/hu-18-draft-retained-after-dismiss.png` | `91b2ae0ae38c8d38dd04e8f867d63a0f798b506e20693889d40bd33d26257e12` | SAFE | Solo formulario de gasto QA abierto; **no demuestra cierre, descarte, retención ni guardado** |
| `capturas/funcional/hu-24-receivable-partial-collection.png` | `85326865668eb0a8dbd29aeafa10c44ea4aef53d3db8c39d44bca082cdd65c1e` | SAFE | Detalle de obligación QA activa con amortización parcial; no secuencia de creación/cobro ni remoto |
| `capturas/funcional/hu-24-receivable-principal-ledger-label.png` | `c7656eb0a69e2a67c05dd59f42e59f9d42f021bf8e74be6da263001b30030506` | SAFE | Movimiento en wallet QA y nota de cobro de obligación; etiqueta/badge no acredita clasificación operativa ni sync remoto |
| `capturas/_local-only/hu-24-goal-internal-transfer.png` | `2209d767b9a66551177b272fdf94c22d4f7b9e57cb52fe3ebd3480f7fbb65698` | **NEEDS_CONTEXT — NO PUBLICAR ORIGINAL** | Meta/reserva QA visible, pero en la zona inferior hay otra cuenta y saldo no atribuibles al inventario QA disponible; no se transcriben sus valores. No prueba persistencia tras reinicio ni transferencia ledger completa |
| `capturas/funcional/hu-33-recurrence-after-relaunch-qa.png` | `1baf468e636e9f03683aeb501c975a5f5d04ae96e95561d45551f1e536b407c1` | SAFE | Regla QA cancelada, revisión y pendiente local; la secuencia de relanzamiento procede de observación QA, no de una imagen aislada; no prueba servidor |

### Acción sobre la evidencia de meta

QA, único autor de las capturas, debe producir una copia derivada sanitizada, por ejemplo `capturas/funcional/hu-24-goal-internal-transfer-sanitizada.png`: recortar o enmascarar opacamente únicamente la zona de cuenta/saldo no acreditados, preservando el estado de la meta, reserva, respaldo QA y cualquier parte relevante para el hallazgo. No alterar importes QA ni modificar una imagen de forma que cree/oculte el defecto. **No hace falta repetir el aporte financiero.** Si no se puede conservar la evidencia relevante con esa redacción, obtener otra vista de solo lectura enfocada en el fixture propio.

El original queda excluido de la inclusión/adjuntos de los entregables hasta resolver esta condición; no publicarlo en GitHub ni usarlo en un bundle. QA gestiona su original bajo los límites de privacidad y ownership, sin edición paralela del Coordinador. La nueva copia tendrá otro hash y debe revisarse antes de aprobarla: este dictamen no se traslada automáticamente al derivado. Si QA acredita inequívocamente que todo el contenido inferior también es propio de esta auditoría, registrar esa atribución mínima sin datos personales y volver a revisar la decisión; no darlo por supuesto.

Las otras siete capturas pueden incorporarse con captions ajustados a lo que prueban y con sintaxis Markdown relativa mientras sus hashes coincidan. Esta condición sobre una imagen no detiene la consolidación, UI/UX ni los demás casos autorizados. No hubo publicaciones ni cambios de píxeles en esta revisión.

## Revisión posterior — derivados y evidencia CRUD/crédito/histórica

Delegación `deleg_65e394d2`, tres revisores de privacidad/evidencia sin ADB, escrituras ni publicación. El Coordinador recalculó los 22 hashes y todos coincidieron con los revisados. Dictámenes brutos: **18 SAFE, 3 NEEDS_CONTEXT y 1 NEEDS_REDACTION**. No son conteos de casos funcionales ni implican validación UI/UX.

### Derivado de meta aprobado

- `capturas/funcional/hu-24-goal-internal-transfer-sanitizada.png` — **SAFE**, SHA-256 `59646135f1335936bf8c244c25475ba54362423f8aa09ac4bca917b773edce17`. Ya no muestra la cuenta/saldo inferior ajenos que motivaron el rechazo del original; conserva meta/reserva y las dos cuentas QA. Puede usarse en informe y revisiones con caption de estado posterior; no demuestra por sí sola el traslado, atomicidad ni sync.
- `capturas/funcional/hu-24-goal-internal-transfer-before-qa.png` — **SAFE**, SHA-256 `65c5abd7ae7f8fec498782903c4745d17aad1131912e57fc69ed5d31c01d251d`. Corresponde al estado previo de las dos cuentas QA; el orden de la secuencia procede del registro de QA. No sumar reserva virtual como activo.
- Esta aprobación es del **derivado y de esos bytes**, no del original `capturas/_local-only/hu-24-goal-internal-transfer.png`, que conserva su dictamen NEEDS_CONTEXT y debe retirarse de cualquier informe que aún lo embeba. No hace falta repetir el aporte.

### Resto de entradas verificadas

| Archivo bajo `capturas/` | SHA-256 | Dictamen bruto / límite |
|---|---|---|
| `capturas/funcional/hu-09-credit-card-archive-returned-to-setup.png` | `3d6eef2076e0503f2be56b2fd2ad6e834bd63d02a24cb09aac225902c5cbbb06` | NEEDS_CONTEXT: paso 3/3; no prueba acción previa, archivo fallido, causa o duplicación |
| `capturas/funcional/hu-09-credit-card-archive-backstack.png` | `5930b7bf819b40202e5f63f97adc3fd5274cc24e3f5244269b328f3354d31208` | NEEDS_CONTEXT: paso 2/3 y búsqueda vacía; necesita el relato QA del recorrido, no implica fallo de archivo |
| `capturas/_local-only/hu-09-credit-card-archive-section.png` | `99ccc56508e5e9b7b32c9d5bbdc36c06e31e06e49fb5917a63a4e1f7f80c6528` | NEEDS_REDACTION conservador: contador global que puede incluir instrumentos ajenos; no identifica la tarjeta QA. Omitir de publicación o sanitizar antes de usar |
| `capturas/funcional/hu-07-cash-account-crud-created.png` | `e8912dc6686ba6e7431a22af39a9a5f068cfc6bf9ac59a0659a4680c0df8c2c3` | SAFE: estado de cuenta QA creada; no infracción de cuota ni remoto |
| `capturas/funcional/hu-07-cash-account-crud-after-archive.png` | `f294931d56449b891f762d9d67126f9bc8cf8006a84e68bc2aad93c24fc4a2f0` | SAFE: listado posterior QA; no prueba regla de cupo ni sync |
| `capturas/funcional/hu-18-positive-expense-detail-qa.png` | `a56aa003a93664dc3f68987ae371b299343622acfa6149f20238ab69ad4bc218` | SAFE: gasto QA y etiquetas; `Verificado` no acredita sync |
| `capturas/funcional/hu-07-cash-account-crud-before.png` | `9b5f95e8144bf87c301a661cdaa277fa998ea0de1df61b3c0952596bfea93a05` | SAFE: ficha QA activa; no secuencia de creación ni remoto |
| `capturas/funcional/hu-07-cash-account-crud-after-edit.png` | `37e2e34d2c98a2f3bfbfd25da5dcf28b9a5418aae266067db961e7a4317fb7f1` | SAFE: listado QA; no demuestra por sí solo cambio de alias |
| `capturas/funcional/hu-07-cash-account-crud-archived-toast.png` | `8058f4e5012fa2c4f35a182e171271167e2dfe651923f4b1c058b88b2deb87b5` | SAFE: aviso local de archivo, pendiente; no éxito remoto |
| `capturas/funcional/hu-09-credit-card-created-confirmation.png` | `21b848516497d813e73892a89cd084dcef4b8ba6c211dd5ecac1a1a80727db7f` | SAFE: confirmación QA; no banco, compra, pago o remoto |
| `capturas/funcional/hu-09-credit-card-limit-cycle.png` | `c695cf784f0b1609e31aaa978e088945feceaa0e5c39089d9cae8f16b5eb92a1` | SAFE: línea/ciclo QA sin número de tarjeta; no consumos |
| `capturas/funcional/hu-09-credit-card-archive-confirmation.png` | `874d29f928ff53826e6b84df193284d05a16c80becadb63478a2b604492ec5dd` | SAFE: diálogo previo; no prueba completar el archivo |
| `capturas/funcional/hu-24-archived-goal-no-contribution.png` | `31ef9cc55a378d4289fb6500c4f937221dc35d424253616d99341bc70418e188` | SAFE: meta QA archivada y controles deshabilitados; no intento guardado rechazado |
| `capturas/funcional/hu-24-receivable-after-relaunch-qa.png` | `8ff5614ef5437d606646a461e51850a46c9595007cbf6d34795730ee1a11ec37` | SAFE: saldo de receivable QA; no prueba secuencia/servidor por sí sola |
| `capturas/incidencias/bug-001-antes.png` | `8ed81bb0714b417f920f3c432d84c1c315443cbf732b0f5085ccfd598e6b8fd5` | SAFE: estado histórico de deuda QA, no estado actual |
| `capturas/incidencias/bug-001-multiples-pulsaciones.png` | `1e0406f90c6e29f1821c6e1cc65ce4d09e73d3d2f65aae36edf524bb665780ea` | SAFE: cronograma QA, no temporalidad/solapamiento de callbacks |
| `capturas/incidencias/bug-001-post-reinicio.png` | `ba92bc96958c15c7c508b13afb9bcc4ce2b5b55e814292d19665a26d8799e78c` | SAFE: snapshot histórico QA; relanzamiento se atribuye al registro, no a la imagen sola |
| `capturas/incidencias/bug-003-void-blocked-linked-debt.png` | `806ed430225d8c38229812ef6fb427fd9ca0f5f831fef4bfcca52ae4efcb16ce` | SAFE con contexto QA explícito del pago PEN 1.00. Se conserva arriba el NEEDS_REDACTION histórico y su motivo; el cambio no es retroactivo ni prueba otro fondo |
| `capturas/_local-only/hu-02-qa-transfers-voided-expense.png` | `d497e3e572164de771796d2d6282b2e114b5ea8b4c5384d29f380485709dd294` | NEEDS_CONTEXT: el importe del gasto anulado no coincide con el contexto de gasto dado al revisor. Acreditar ese fixture/momento o recortar/enmascarar la fila antes de publicar, sin transcribir el valor |
| `capturas/funcional/hu-31-meta-release-restored.png` | `f667e817a85929e0b5c80d47747b40d0dd9d6ee8b7bdb1f63b27962e43bb7fba` | SAFE: meta QA en medición histórica; no estado actual ni flujo completo |

### Disposición de condiciones

Los dos PNG del wizard no fueron señalados por datos personales identificados, sino por falta de contexto que una imagen aislada no contiene. El revisor permite que acompañen el relato de QA con explicación explícita: únicamente documentan pasos visibles 3/3 y 2/3, el orden procede de QA, el archivo sí se observó efectivo y causa/duplicación no están demostradas. Mantenerlos como **evidencia condicionada a ese texto**, no como demostración autónoma de un bug financiero.

El contador de archivados se omite de nuevos adjuntos públicos mientras no se sanee o se acredite que su contenido es publicable; además no es prueba de identidad del fixture. La imagen histórica HU-02 necesita atribución mínima o derivado: no reinterpretar su cifra ni repetir operaciones financieras para corregir una evidencia antigua. Estos límites son de imágenes concretas, no motivos para detener los casos independientes.

El resultado SAFE del derivado de meta permite sustituir el original en `revision-ui.md` y usarlo en `revision-ux.md`; ambos informes permanecen bajo sus autores. UX ya convirtió sus nueve referencias a nueve imágenes embebidas y el Coordinador comprobó su existencia, sin modificar ese informe. Ninguna revisión de privacidad reetiqueta las observaciones heurísticas como defectos confirmados ni altera los conteos funcionales.

## Nuevas capturas del run QA 288 — teclado/error y recurrencia ACTIVE

Revisión visual independiente `deleg_52de0c9c`, sin ADB ni escrituras/publicaciones; el Coordinador recalculó los cinco SHA-256 al recibir el resultado y coinciden con los bytes revisados. Dictámenes: **3 NEEDS_CONTEXT y 2 SAFE**; son decisiones sobre archivos, no casos funcionales. El par ACTIVE también había sido revisado por el follow-up terminal `t_c5432de9` / run 289.

| Archivo bajo `capturas/` | SHA-256 comprobado | Dictamen / disposición |
|---|---|---|
| `capturas/_local-only/hu-18-form-keyboard-open-qa.png` | `ab7e8fe5a1acbcb96cfefb141ef7595ba0e0dcd1404f9c9e58b3cef90bc642bc` | **NEEDS_CONTEXT — no publicar ni incluir en bundle entregable**: formulario y teclado visibles, pero el selector muestra cuenta/saldo no atribuidos inequívocamente a un fixture QA. Acreditar esa procedencia concreta o sanear opacamente solo el selector en un derivado. |
| `capturas/_local-only/hu-18-form-keyboard-closed-qa.png` | `43fc871392825d875988a2239faff4066898d453fe509eeb341dddd50cea6e64` | **NEEDS_CONTEXT — no publicar ni incluir en bundle entregable**: mismo selector de cuenta/saldo; la etiqueta de archivo QA no demuestra su procedencia. Misma condición de contexto mínimo o derivado. |
| `capturas/_local-only/hu-18-form-zero-error-qa.png` | `d39ba13ba1203c18766f785520fe1668f98b540c5e7d475dc8f45a38f1cfd62c` | **NEEDS_CONTEXT — no publicar ni incluir en bundle entregable**: conserva validación de cero, pero también el selector no atribuido; redactar ese selector sin ocultar el error, importe/formulario ni CTA relevantes si no puede acreditarse. |
| `capturas/funcional/hu-33-recurrence-active-before-relaunch-qa.png` | `29c00b4d62acf7a2ff754baabb7597b157d638626021f750ec400443b2b3a639` | **SAFE**: solo fila de la regla QA ACTIVE, fecha/revisión y pendiente local. La imagen acredita estado visible, no temporalidad autónoma. |
| `capturas/funcional/hu-33-recurrence-active-after-relaunch-qa.png` | `29c00b4d62acf7a2ff754baabb7597b157d638626021f750ec400443b2b3a639` | **SAFE**: mismos píxeles/estado que la anterior. La secuencia antes→cerrar→abrir→después procede del registro QA del run 288; igualdad de imágenes no demuestra sync remoto ni causalidad. |

QA conserva la autoría de las tres imágenes condicionadas. No sobrescribir sus originales ni repetir rechazos/operaciones financieras para resolver privacidad; producir derivados desde los archivos existentes, con nombres distintos, revisar píxeles y hashes antes de sustituir referencias en entregables. Puede omitirse una imagen condicionada si el texto/otra evidencia segura conserva el resultado. UI/UX no deben embebarlas ni publicarlas mientras siga esta condición; esto no bloquea los filtros u otros casos independientes. Una fila de cuenta/saldo no atribuida no es por sí misma evidencia de un defecto de seguridad de la app ni autoriza investigar datos ajenos.


## Capturas proporcionadas por el Owner — derivados publicados (2026-10-20)

Los diez originales `image*.png` / `image copy*.png` bajo `capturas/Capturas/` se dejaron intactos. Las derivaciones fueron recortes (sin alterar contenido del defecto) para excluir saldos, avatar, nombres de cuenta y/o columna monetaria que no eran necesarios. Solo los derivados listados se adjuntaron a GitHub; no se publicaron los originales completos.

| Derivado | SHA-256 | Uso y contenido retirado |
|---|---|---|
| `ui-ux/issue-70-configuracion-mi-dinero.png` | `a507bb7f613e41149935c9d0bde24630fc74031fe38d77d64ffa2603fbd22a02` | Cabecera e icono; fuera avatar y todos los saldos. |
| `ui-ux/issue-70-configuracion-movimientos.png` | `009392813e57f78640debf055f344d76f9c85ff1ad96794c17509a32389e3ffb` | Cabecera e icono; fuera listado y balance. |
| `ui-ux/issue-71-fecha-editar-deuda.png` | `cdc00eb7de62fbf1d37a0336dbf7dd29cc1337206c31eb4900245b406a9b1e44` | Solo campo de vencimiento. |
| `ui-ux/issue-71-fecha-registro-prestamo.png` | `734a51ad6b7547c1a1e16ee06da8251f73b9ae60a2a6ef38abbf25c6ea811b46` | Solo campo de vencimiento; fuera cuentas/personas. |
| `ui-ux/issue-71-fecha-meta.png` | `ff74159bc0cccc04b2e04be2931853f657d69b5c748cab90b2f03bcf4c1012ec` | Solo campo de fecha objetivo. |
| `ui-ux/issue-72-selector-comercio-no-visible.png` | `82dd647fb5a039c49384c026985d8af24b791cd72cbd2acb756208fd594e96ae` | Categoría/detalles/fecha/nota; fuera selector de cuenta y saldo. |
| `ui-ux/issue-73-ayuda-cronograma.png` | `ceaa27fbee75066620289dc7eca53d98e2057a10bb07c503ba43ca1056ae85d7` | Cabecera del cronograma con `?`, sin detalle de deuda. |
| `ui-ux/issue-73-info-ajustes.png` | `d17a2282816b0cb41f81dcee7999ad4359829ace067be299201eb268d069b94c` | Cabecera de ajustes con `i`, sin importes/detalle. |
| `ui-ux/issue-74-historial-cuotas.png` | `cd0af7dec4cdbeda4223f02a8d66f3275352d9befb128aeeabaf3dbfb612fcac` | Filas/estados de cuotas; columna de importes recortada. No prueba por sí sola falta de feedback ni secuencia de taps. |

Los títulos anteriores reflejan inspección visual de los derivados; no se declaran mediciones, secuencias, frecuencia ni resultados de tap que no estén reportados. El recorte de campos fecha no demuestra por sí solo qué abre al tocarlos. El screenshot de comercio muestra un formulario de gasto con detalles expandidos sin esa fila en pantalla; el checkout inspeccionado sí implementa una fila condicional, por lo que el reporte mantiene explícita la discrepancia de artefacto/estado. Las URLs de adjuntos publicados respondieron `200 image/png`.

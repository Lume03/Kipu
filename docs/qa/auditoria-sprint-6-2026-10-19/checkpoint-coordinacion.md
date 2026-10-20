# Checkpoint de coordinación — auditoría S1–S6 EN CURSO

## Estado operativo vigente — posterior al run QA 294

Esta sección supersede los baselines y asignaciones históricas de continuación incluidos más abajo; no autoriza repetir los casos ya verificados.

- **Último archivo comprobado:** `informe.md` SHA-256 `7796ab6c1c9cbdc4f87c7b8568d0e316a6797be4523cfe22bb089ea2c57c632c`; **55 filas = 33 PASS / 2 FAIL / 1 BLOCKED / 19 NOT TESTED**, 45 embeds / 33 PNG y ninguna referencia relativa ausente. Las dos filas nuevas desglosan HU-10/HU-11 pendientes: no son pruebas adicionales ejecutadas ni otro PASS.
- **Conexión y recuperación acotada:** `t_7e4281f4` / run 294 terminó `blocked`: ADB no enumeró dispositivos; HU-10/HU-11 no se ejecutaron en UI. Se reactivó la MISMA tarjeta con comentario 621 y brief actualizado para que el único QA compruebe el adb.exe/endpoint existentes y enumeración USB local de solo lectura, ejecute `adb start-server` idempotente y una comprobación posterior; reconectar transporte existente solo si está offline y es el Android previamente autorizado. Máximo 3 minutos, sin kill-server global, drivers, nuevos permisos, modo USB, red/pairing, reboot ni reinstalación. Si reaparece como `device`, continuar los dos casos S3; si no, registrar la dependencia física/autorización concreta y no relanzar más pruebas hasta que cambie ese gate. El Coordinador no toma ADB. No convertir una desconexión en bug del producto ni en agotamiento de la auditoría.

### Historial inmediato — cierre HU-22 del run QA 293

- **Baseline actual verificado:** `t_83ae00f6` / run 293 terminó `done`; **53 filas = 33 PASS / 2 FAIL / 1 BLOCKED / 17 NOT TESTED**, 45 embeds / 33 PNG y 0 referencias relativas ausentes. Hash REAL recomputado de `informe.md`: `e62d3972ae6cd1f9add66fdeac1cf00d92a585430eaaeb1810bd769d0a6fe4d2`. El token del handoff acababa en `...fe4d`, tiene 63 caracteres y no es un SHA-256 válido: no se normaliza ni se afirma coincidencia de ese token; el valor anterior procede de un cálculo independiente de los bytes. Los snapshots 288/292 conservados abajo son históricos.
- **HU-22: cierre de bloque, no auditoría:** la lista/checkbox permitió seleccionar `Billetera QA20261020CTAA` sin input textual. Se observaron Gastos y presets Hoy/7 días/Este mes; no se estableció/verificó rango calendario incluyente/excluyente, no se aplicó la combinación ni se leyó/interpretó el movimiento. El panel se canceló sin cambios. Se mantiene NOT TESTED y la limitación observada; **no otra continuación HU-22 sin nueva precondición/material probatorio**, ni repetir métodos de foco ya fallidos.
- **Siguiente operador único asignado:** `t_7e4281f4`, bloque S3 HU-10/HU-11: consulta de catálogo/tasas y edición/cancelación de borradores QA si la UI los expone, sin compras, pagos, reactivación o creación de otra tarjeta ni guardar parámetros. Padre `t_83ae00f6` terminal y tablero sin running comprobados antes de crear. QA posee exclusivamente ADB, informe/capturas/logs; Coordinador/UI/UX no toman dispositivo ni escriben esos archivos. La tarjeta incluye correcciones documentales puntuales de limitaciones ya superadas (ACTIVE y derivado meta SAFE), no repetición funcional ni ampliación de permisos.
- **Publicaciones:** se detectó que UX-004/OBS-005 ya existe como #63; no duplicar el borrador. Nuevos issues públicos no cambian por sí solos los resultados o severidades del snapshot QA; revisar su evidencia/oráculo y consolidarlos bajo autoría QA antes de sumar casos.

### Historial inmediato — baseline del run QA 292

- **Baseline documental actual verificado:** el run 292 retiró del informe los tres embeds HU-18 condicionados, sin volver a ejecutar validaciones ni cambiar datos financieros. SHA-256 `761861e7453ca49bb1ed2f95358230eab82763da9a36bf4b110ec99ab29ec14d`; matriz intacta **53 filas = 33 PASS / 2 FAIL / 1 BLOCKED / 17 NOT TESTED**, 45 embeds / 33 PNG distintos y 0 referencias relativas ausentes. El hash/conteo de embeds del run 288 conservado abajo es histórico, no el archivo actual.
- **HU-22 / corrección del criterio de continuidad:** el run 292 terminó `blocked` tras dos rutas de entrada/foco fallidas; no aplicó filtros ni interpretó resultados y salió sin guardar. La ausencia del método exacto del run 288 NO es un permiso pendiente del usuario ni una condición indispensable. El Coordinador autoriza una ruta distinta sin entrada textual: Movimientos → Abrir filtros → Cuenta → seleccionar por checkbox SOLO `Billetera QA20261020CTAA` desde la lista → Listo/Aplicar → Gastos y rango/chips/calendario. Cuenta QA inequívoca primero, query de alias opcional; no tocar FAB/edición ni coordenadas antiguas. Ubicar y verificar cada control en la UI actual, nunca taps ciegos. La ruta se verificó en fuentes (`MovementReferenceSelector.kt`, `MovementFiltersSheet.kt`), no todavía en el dispositivo. Se reanuda **la misma tarjeta `t_83ae00f6`**, sin operador adicional, una pasada acotada y sin repetir las dos rutas fallidas ni esperar un método antiguo. Si la superficie no permite la prueba, registrar el impedimento real y terminar este bloque con limitación, no la auditoría global.

- **Entrega aceptada como avance, no cierre global:** `t_3fab3a86` y run 288 terminales `done`; el informe coincide con su handoff, SHA-256 `85883e1e8612048a9081a81d3a06e09fd089e1af98f2ffb432113c205bc0f970`. Recuento independiente: **53 filas = 33 PASS / 2 FAIL / 1 BLOCKED / 17 NOT TESTED**; 48 embeds, 36 PNG distintos, ninguna referencia relativa ausente. Conteos válidos para ese snapshot, no para cambios posteriores de QA.
- **Persistencia ACTIVE resuelta:** la misma `QA20261020RECACT1` fue observada antes de cerrar únicamente Kipu y después de relanzar/volver por la ruta de recurrencias: ACTIVE, próxima 2026-10-31, rev.1 y pendiente local. Secuencia ejecutada/documentada por QA; ambas capturas tienen bytes idénticos (`29c00b4d62acf7a2ff754baabb7597b157d638626021f750ec400443b2b3a639`) y muestran campos iguales, pero no acreditan por sí solas la cronología. No afirmar sync remoto ni crear/editar/cancelar la regla para repetir el PASS.
- **Seguimiento redundante:** `t_c5432de9` fue cerrado por su worker / run 289 tras comprobar que el padre ya resolvió el criterio; no tomó ADB ni modificó archivos. No volver a despacharlo. Su revisión visual SAFE se limita al par de PNG ACTIVE; no es aprobación de release ni Security formal.
- **Operador/escritor exclusivo asignado:** `t_83ae00f6`, QA HU-22 — filtros combinados de fecha/tipo/cuenta sobre `QA20261020MOVCTAA` y `Billetera QA20261020CTAA`, sin nuevas mutaciones financieras. **Corrección de despacho:** `t_013e6f5b` / runs 290–291 fallaron durante inicialización (`Unknown skill(s): software-kanban-orchestration`), antes de toda acción QA/ADB: el Coordinador había forzado una skill no instalada en el perfil QA. Ese intento no ejecutó pruebas ni es bloqueo del producto; se archiva por trazabilidad y se reemplaza con la nueva tarjeta sin skills forzadas, sin modificar perfiles ni instalar nada. QA de chat y Coordinador no operan ADB ni editan informe/capturas/logs en paralelo. No repetir búsquedas simples ni ACTIVE. El run 288 sí consiguió introducir al final el alias exacto: reutilizar el método ganador, no tratar el intento inicial fallido como imposibilidad de HU-22. Verificar inclusión/exclusión por rango y limpiar filtros con valor de consulta observado; después resolver las tres imágenes condicionadas desde archivos existentes o retirar sus embeds, sin recapturar/repetir el rechazo de cero.
- **Continuidad probatoria:** sync sin logs de owner/claim/resultado y BUG-001 sin timeline callback/isSaving/recibo siguen no confirmados; no forzar reintentos ni nuevas ráfagas para repetir la falta de observabilidad. Las tres capturas nuevas de teclado/error fueron revisadas (`deleg_52de0c9c`) y quedan **NEEDS_CONTEXT**, por selector de cuenta/saldo no atribuido: no publicarlas ni incluirlas en el bundle mientras no se acredite ese contexto o se revisen derivados sanitizados. No volver a ejecutar el rechazo de cero para arreglar privacidad ni sumar otro PASS. El par ACTIVE es SAFE para sus píxeles QA, no prueba temporal autónoma. Dictámenes/hashes en `issues/revision-privacidad.md`. El resto de cobertura S1–S6 viable se mantiene, sin convertir toda S1/S3 en exclusión por permisos/compras.
- **Estado general:** auditoría ABIERTA / `CHANGES REQUESTED`; no release aprobado, no nuevos defectos funcionales confirmados por este relevo, sin cambios productivos/builds/commits/push.

## Mandato vigente del Owner — reanudación del 2026-10-20

La entrega parcial **no cierra el encargo**. Continuar hasta ejecutar los casos autorizados y viables; cada residual debe tener impedimento observado concreto y requisito para desbloquearlo. No volver a presentar una corrida `done`, una matriz validada o `CHANGES REQUESTED` como agotamiento de la auditoría.

La disposición anterior del Coordinador de no abrir más corridas runtime queda **supersedida por este mandato**. Conservar resultados, cronología y fixtures; no repetir PASS sin dependencia real ni volver a empezar desde cero. El mandato de reanudación anterior se recibió completo; no reconstruir su sección truncada histórica. La ampliación posterior UI/UX llega cortada en la lista de entrega final, después de «Hallazgos nue…»: se registra el faltante sin adivinarlo ni detener las responsabilidades y límites que sí llegaron explícitos. Las reglas actuales de issues son explícitas y suficientes.

## Propiedad y límites

- **Propiedad actual del celular y del informe: QA de `kipu/t_3fab3a86`**, continuación nativa secuencial desde el checkpoint de 50 casos. La corrida QA del chat alcanzó explícitamente el límite de herramientas y entregó su estado final antes del relevo; el Coordinador verificó el tablero sin corridas activas y creó esta tarjeta. Mientras esta tarjeta esté en curso, **QA del chat no opera ADB ni edita `informe.md`, capturas o logs en paralelo**. El reemplazo mantiene el mismo perfil y fixtures, no reinicia la auditoría. Al terminar, verificar el run y transferir este checkpoint con el último estado real antes del siguiente operador.
- Coordinador: este archivo, `issues-publicacion.md`, deduplicación/publicación y verificación de issues; sin ADB en paralelo.
- Android Core: solo `continuacion-tecnica-2026-10-20.md`, tarjeta `kipu/t_a98dcc9b`; análisis de fuentes/artefactos/probes, **sin ADB, build, cambios de código ni operaciones de backend**.
- Solo fixtures QA propios, identificados inequívocamente. No tocar datos preexistentes ajenos ni borrar usuario/historial. Registros financieros ficticios dentro de Kipu están autorizados; compras, pagos bancarios, suscripciones o envíos externos no.
- Sin modificaciones de código productivo, infraestructura, permisos globales, instalación/reinstalación/desinstalación, borrado de app, cambios de reloj/red ni commits/push/PR/merge. No SQL de escritura, RPC financieras manuales, jobs forzados ni reintentos inseguros.
- El registro de activación contiene instrucciones históricas para compilar/provisionar: **no ejecutarlas**. No exportar/importar/emitir/revocar credenciales ni renovar o sortear expiración. Si caduca Premium QA, continuar caminos Free y registrar límites Premium reales.

## Actualización tras checkpoint de 50 casos — relevo secuencial

El Coordinador leyó las entregas completas de UI/UX y el informe real. Baseline documental comprobado: `informe.md` SHA-256 `8c42a558ba3b8f7cf1a49b2dc7bd8773e6be8f818d6a1d387caa3af3dc731b02`, **50 casos: 32 PASS / 2 FAIL / 1 BLOCKED / 15 NOT TESTED**. Hay 43 ocurrencias de imágenes embebidas, **31 PNG distintos**, y sus referencias existen; no llamarlas 43 imágenes únicas ni interpretar existencia como revisión visual de privacidad.

`revision-ui.md` y `revision-ux.md` existen y fueron leídos; no sigue pendiente la existencia del informe UX. UI-001 documenta discrepancia fuente–canon, no un defecto renderizado/contraste medido. UX-001–004 son observaciones heurísticas/potenciales; mapeo: UX-002 ↔ OBS-003, UX-003 ↔ OBS-002 y UX-004 ↔ OBS-005. No crear un issue por cada etiqueta de un mismo problema ni alterar PASS funcionales por sugerencias.

La corrida QA previa del chat agotó herramientas y entregó handoff final: dejó `QA20261020RECACT1` ACTIVE, fecha siguiente 2026-10-31, guardado local pendiente, sin incorporarlo al informe. Hay una ambigüedad temporal que debe resolverse: «creé después de relanzar» no prueba persistencia de ACTIVE. **No contar un alta nueva ya cubierta como otro PASS de persistencia**. La continuación `t_3fab3a86` se creó después de verificar que no había otra corrida Kanban activa y mantiene operación exclusiva: (1) consolidar ambas revisiones y ese fixture; (2) verificar regla existente antes de cerrar → relanzar → volver a leer ACTIVE/campos y completar filtros/evidencia teclado; (3) diagnóstico mínimo sync/BUG-001 con pruebas discriminantes o instrumentación precisa faltante. No es un cierre de toda la auditoría.

Acciones de autores independientes, sin ADB:

- **UI:** `revision-ui.md` todavía embebía el original `HU-24-goal-internal-transfer.png` al último control. Sustituirlo por `HU-24-goal-internal-transfer-sanitizada.png`, **ya SAFE** tras revisión y hash `59646135f1335936bf8c244c25475ba54362423f8aa09ac4bca917b773edce17`; la vista previa de las dos cuentas QA también es SAFE. Mantener el alcance de los recortes sin repetir el aporte.
- **UX:** se verificó la corrección: nueve referencias locales y **nueve imágenes embebidas**, todas existentes; no sigue pendiente ese cambio. Puede usar el derivado de meta ya aprobado. Al describir los pasos del wizard, aclarar que la secuencia es observación QA y que las fotos solo muestran 3/3 y 2/3, no causa/fallo de archivo. Omitir o sanear el contador global de archivados antes de adjuntarlo públicamente.
- **Coordinador:** terminó la revisión de 22 fotos (`deleg_65e394d2`) y comprobó sus hashes: 18 SAFE, 3 NEEDS_CONTEXT y 1 NEEDS_REDACTION en dictámenes brutos, **no casos funcionales**. El derivado de meta está aprobado; los dos estados del wizard pueden acompañar el relato QA con sus límites, sin afirmar fallo financiero. El contador de archivados se omite de nuevos adjuntos; la imagen histórica HU-02 requiere atribuir el gasto anulado o recortarlo/enmascararlo sin repetir operaciones. El aviso BUG-003 se reconsideró SAFE con contexto QA explícito, conservando la decisión histórica y sin subirlo innecesariamente. Se publicó y releyó **UI-001 → https://github.com/Lume03/Kipu/issues/62**, investigación documentación/código P3 (`documentation`, `UI/UX`, `question`), sin label `bug`, adjuntos ni asignaciones; no confirma defecto renderizado. UX-004/OBS-005 sigue como un único borrador de mejora, no dos bugs nuevos.

## Baseline comprobado antes de reanudar (no conteo final)

- Checkout: `C:/Users/Alume/orca/Kipu`, `main`, HEAD `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`; sin cambios tracked/staged en el preflight. `.worktrees/` y `docs/qa/` ya eran untracked: preservar.
- Informe: SHA-256 `df13a0b7815759ae268b950027dd9536f3718454bab9c7893f06a5cdba116663`, 26 filas S1–S6 = 15 PASS / 2 FAIL / 1 BLOCKED / 8 NOT TESTED. Los PASS son casos acotados, no HU completas. Desglosar filas amplias por escenario y aceptación real, sin contar dos veces un mismo resultado ni rellenar números esperados.
- Issues ya publicados: BUG-002 → https://github.com/Lume03/Kipu/issues/60 ; BUG-003 → https://github.com/Lume03/Kipu/issues/61 . No duplicar. BUG-001 y OBS-001 siguen potenciales; `PENDING` aislado no prueba bug.
- Dispositivo previamente probado: Samsung SM-A165M, Android 16/API 36, `com.kipu.app 0.1.0`, versionCode 1. QA refresca autorización/foreground/versión con el SDK ADB seleccionado, sin publicar serial.

## Nueva evidencia de trazabilidad (fuentes, todavía no igualdad del APK instalado)

El Owner señaló `C:/Users/Alume/AppData/Local/Temp/opencode/kipu-qa-premium/docs/qa-premium.md`; el Coordinador leyó el documento. El worktree está limpio en `012-ep-pla-qa-premium`, HEAD `d5943e8748490765caec95638802bef160e50d30`.

`git merge-base main 012-ep-pla-qa-premium` devuelve exactamente `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`. Hay tres commits exclusivos de QA sobre esa base: tooling de credenciales, provisioning debug y documentación. El diff inspeccionado añade configuración de build, helper en androidTest, scripts y documentación/spec012; esto no demuestra por sí solo igualdad con el binario instalado.

Existe `app/build/outputs/apk/debug/app-debug.apk` y `output-metadata.json` en ese worktree. Android Core entregó [continuacion-tecnica-2026-10-20.md](continuacion-tecnica-2026-10-20.md); el Coordinador recalculó independientemente el SHA-256 del APK local: **`e218941857b805335e932ac78b6412ef31e316607a0775ca2824db4eda8c2864`**, tamaño 35 067 164 bytes, metadata `com.kipu.app`/`debug`/`0.1.0`/versionCode 1. **QA único operador** comparará el hash del APK instalado con ese artefacto mediante lectura del paquete: preferir `pm path com.kipu.app` y SHA-256 del archivo instalado, sin instalar ni ejecutar el provisioning helper. Si no son iguales, conservar limitación, contrastar evidencias disponibles y no inferir equivalencia por versión/fechas/certificado solo.

Activación documentada: debug opt-in, acceso `SIGNED_QA_GRANT`, base Room 24 y expiración **2026-10-21 04:51:58 UTC = 2026-10-20 23:51:58 UTC−5**. No acredita suscripción, entitlement remoto o cuotas de servidor. No copiar claves, grant, identidad, UUIDs, tokens ni configuraciones privadas al informe/chat.

## Ampliación vigente — consolidación P1 y revisión UI/UX

### Propiedad de esta fase en el chat (sin workers duplicados)

- QA (`@auditor-de-calidad-qa`): único operador del teléfono y escritor de `informe.md`, capturas y logs. **Antes de nuevas pruebas**, consolidar el avance ya ejecutado descrito abajo.
- UI (`@dise-ador-visual-ui`, perfil `ui-specialist`): autor único de `revision-ui.md`; lectura de fuentes/docs/evidencia. No editar tokens, estilos, componentes, `DESIGN.md`, imágenes QA, informe ni checkpoint. Sin ADB, ni siquiera comandos de lectura del dispositivo.
- UX (`@dise-ador-de-experiencia-ux`, perfil `ux-specialist`): autor único de `revision-ux.md`; evaluación heurística y de flujos documentados, no investigación validada con usuarios. Mismos límites de solo lectura y sin ADB; no editar documentos de UI/QA ni este checkpoint.
- Coordinador: checkpoint y control/borradores/publicación de issues; verificación/deduplicación transversal. Las solicitudes de capturas se agrupan para que QA haga una sola interacción por estado y ambos revisores reutilicen evidencia.
- No lanzar otra corrida nativa para estos participantes mientras trabajan desde el chat. Si una corrida termina por presupuesto, entregar checkpoint y confirmar terminal antes de trasladar ownership a su reemplazo.

### P1 — brecha documental verificada por el Coordinador

Lectura del archivo al inicio de esta fase: `informe.md`, SHA-256 `2bce9f2e0e89de6325a5c10dba4b47431018fc348f3f54e33c2bbd52bcd9d8d5`; **35 filas: 21 PASS / 2 FAIL / 1 BLOCKED / 11 NOT TESTED**. Son conteos del documento sin reconciliar, no cierre ni cobertura final. QA comunicó resultados posteriores que aún no están allí:

1. Receivable histórica QA de PEN 1.00 con cobro parcial PEN 0.50: pendiente PEN 0.50/activa y persistencia visible tras cerrar/reabrir. Registrar alias exacto QA, pasos/fecha y límites; no repetir la mutación para documentarla ni declarar cumplida la exclusión de ingresos operativos sin before/after.
2. Reserva/aporte de PEN 0.50 desde wallet QA a meta QA mediante la opción que QA llamó «transferencia interna»: describir controles realmente usados, cuentas/efectos observados y respaldo; distinguir reserva virtual de transferencia ledger explícita. **No dar por cubierto HU-24 completo** ni afirmar conservación entre dos cuentas, atomicidad o clasificación contable sin evidencia de esos efectos. Meta tras reinicio sigue sin verificar.
3. Regla `QA20261020RECCTB` CANCELLED/revisión 4 y pendiente de sincronización visible **tras relanzar Kipu**: incorporar persistencia local acotada; actualizar las afirmaciones actuales «reinicio/reapertura no probado» de introducción, fila y conclusión. Mantener esas afirmaciones solo como antecedentes históricos rotulados; no inferir sync remoto ni persistencia de estado activo/pausado.
4. Las ocho capturas nuevas existen y pasaron verificación de archivo PNG; ninguna estaba embebida aún en el informe al comprobarlo. Incorporar las pertinentes con `![...](capturas/...)`, caption de qué prueban/no prueban y revisión de privacidad. Una captura o su nombre no sustituye ejecución, causalidad, before/after ni ownership QA.
5. Reconciliar introducción/matriz/hallazgos/fixtures/pendientes/conclusión y recalcular desde filas reales; no fijar por anticipado un número nuevo. Desglosar criterios verificables sin contar de nuevo resultados ya incluidos. Mantener CHANGES REQUESTED, #60/#61 existentes y potenciales con su incertidumbre.

Capturas nuevas disponibles: `HU-22-search-exact-qa.png`, `HU-22-search-partial-qa.png`, `HU-22-search-no-results.png`, `HU-18-draft-retained-after-dismiss.png`, `HU-24-receivable-partial-collection.png`, `HU-24-receivable-principal-ledger-label.png`, `HU-24-goal-internal-transfer.png`, `HU-33-recurrence-after-relaunch-qa.png` (todas bajo `capturas/`). Revisión visual adicional completada (`deleg_84e24a5f`), con hashes de las ocho entradas recalculados y coincidentes: **7 SAFE; `HU-24-goal-internal-transfer.png` NEEDS_CONTEXT** por cuenta/saldo inferior no atribuibles a QA. Disposición detallada en [issues/revision-privacidad.md](issues/revision-privacidad.md): no publicar/incluir ese original; QA debe obtener un derivado con recorte o máscara opaca de lo ajeno, sin alterar la evidencia ni repetir el aporte, y solicitar revisión del nuevo hash. Las otras siete imágenes pueden incorporarse con captions acotados. El formulario de borrador solo muestra un formulario abierto, no el descarte/retención/guardado; tampoco se infiere por un basename que la reserva generó transferencia ledger. Una captura de recurrencia cancelada no prueba por sí sola la secuencia de relanzamiento ni sync remoto.

### P2 — continuar sin repetir casos aprobados

Una vez guardada P1, seguir B2–B4 y el residual de B1, sin volver a ejecutar las tres búsquedas y las validaciones cero/categoría/precisión ya observadas, salvo razón concreta como obtener una evidencia visual que falta. Para cada repetición justificada anotar motivo y no sumar el mismo resultado dos veces. Métodos negativos/instrumento vacío/fecha inválida que la UI no permite introducir requieren distinguir impedimento de entrada de validación de negocio no comprobada.

Completar comparación de bytes del APK instalado con el hash local ya obtenido; no hay necesidad de recompilar. Diagnóstico sync y BUG-001 solo observación/probes existentes y correlación; no esperas repetidas sin dato nuevo ni force/retry/RPC/SQL. El estado de Premium debe observarse, no inferirse: el reloj del host fue 2026-10-20 08:15 UTC−5 en el preflight y el vencimiento documentado es esa noche, pero eso no prueba entitlement activo en el teléfono; no renovar ni modificar reloj/plan. Mantener caminos Free disponibles si expira o no es válido.

### P3 — contratos de revisión visual y de usabilidad

Fuentes visuales localizadas: `docs/stitch-design-system.md` declara el canon **Kipu Andean Modernist**; `docs/ux-ui/references/stitch-redesign-catalog.md` tiene alcance congelado hasta S3. Complementar con `app/src/main/java/com/kipu/app/ui/theme/Theme.kt` y tokens/tipografía reales. No convertir un mockup S3 en requisito funcional S6/S7, ni juzgar modo oscuro exclusivamente con tokens claros. Si hay discrepancias entre documentos, identificarlas, no resolverlas por preferencia estética ni modificar el canon.

Cada informe UI/UX debe incluir alcance por pantalla/estado, fuente de evidencia (captura actual / observación QA / código / heurística), limitaciones y hallazgos diferenciados `UI-001...` / `UX-001...` sin renumerar BUG existentes. Por hallazgo: categoría funcional/visual/usabilidad-accesibilidad/mejora/potencial; título, pantalla/flujo/estado, confirmación, severidad/prioridad razonadas, pasos/frecuencia realmente observados, esperado/actual, imagen relativa explicada, impacto, fundamento, causa demostrada o hipótesis, propuesta sin aplicarla, responsable sugerido, criterios de aceptación y test futuro. No inventar frecuencia, usuarios entrevistados ni mediciones.

Contraste/tamaño táctil/rendimiento sin medición sustentada: **pendiente de medir**. Los píxeles de un recorte no establecen dp/sp ni el hit target efectivo de Compose. Una declaración en código no prueba tema/renderizado del APK; una captura no demuestra navegación, duración o comportamiento de un lector de pantalla. No activar TalkBack, cambiar tamaño de fuente/tema global, permisos o densidad para medir. Métricas de lectura pertinentes del dispositivo solo las obtiene QA, sin publicar identificadores.

Solicitudes concretas adicionales se hacen a QA con pantalla/estado/acción, defecto o criterio a evaluar, encuadre y necesidad de antes/después. Revisores deben consultar primero capturas existentes, **no limitarse a las de bugs** y no pedir otra visita si una captura actual ya responde. Prioridades de captura para integrar en los recorridos funcionales pendientes, no como ocho reejecuciones obligatorias:

| ID de evidencia (no hallazgo ni caso PASS) | Estado o secuencia requerida si falta evidencia útil | Motivo / límite |
|---|---|---|
| EV-01 | Navegación disponible S1–S6 y entrada a detalle desde registros QA | Ubicación/alcance de acciones; no fotografiar saldos personales o evaluar ausencias S7+ como bugs |
| EV-02 | Formulario propio con teclado abierto/cerrado y error de precisión o campo obligatorio | Legibilidad, oclusión/scroll y mensaje que explica el rechazo; reutilizar borrador sin confirmar si basta |
| EV-03 | Selección de cuenta/categoría: seleccionado y deshabilitado cuando existan | Affordance, semántica y área táctil; no inferir mediciones del icono visible |
| EV-04 | Detalle de deuda QA con cuotas activas/históricas y acción de guardar/estado posterior | Jerarquía y distinción plan/pago/historia; no recrear la batería BUG-001 sin evidencia temporal nueva |
| EV-05 | Meta QA y aporte: opción elegida, respaldo y resultado | Distinguir reserva/transferencia; falta la consulta de esa meta después del relanzamiento |
| EV-06 | Recurrencia QA futura: configuración/cancelada y estado local pendiente | Estado y claridad; reutilizar captura tras relanzamiento ya creada |
| EV-07 | Confirmación de editar/archivar/anular en fixture QA | Prevención de errores y salida segura; cancelar si solo se necesita revisar el diálogo |
| EV-08 | Cuentas/tarjetas/crédito QA y formulario sin credenciales ni pagos externos | Evaluar estados aún no cubiertos y su adaptación; no usar tarjetas personales como fixture |

UI y UX conservan hallazgos propios pero enlazan entre sí cuando describen el mismo problema. La cobertura funcional se cuenta separada de superficies revisadas heurísticamente y de sugerencias; no convertir una opinión en PASS/FAIL de negocio. No publicar dos issues para el mismo problema: contrastar contenido completo de #60/#61 y otros candidatos; una mejora/problema UX debe titularse y etiquetarse como tal, no como fallo funcional demostrado. Publicaciones solo con datos sanitizados, borrador local, destino verificado y readback.

## Bloques para QA (IDs de trabajo locales, no tarjetas adicionales)

### B1 — búsqueda y validaciones, sin repetir creación/transferencia ya aprobadas

1. Buscar el movimiento propio `QA20261020MOVCTAA` con consulta exacta, parcial y término único sin coincidencias. Verificar texto realmente introducido, filtros activos, resultado y regreso con Atrás. Usar únicamente datos QA al guardar evidencia.
2. Validar en borradores propios importes cero, negativos, precisión decimal permitida/excesiva, campos obligatorios y fechas/límites pertinentes **con oráculos de la spec del campo**; no imponer que todo saldo o fecha negativa/futura sea inválido. Comprobar que rechazos/cancelaciones no cambian saldos ni crean registros.
3. Ante fallo de ADB: leer UI actual, fijar foco del campo correcto, limpiar solo ese campo, introducir un valor y **releerlo antes de guardar**. Probar alternativa soportada, como eventos de teclas ASCII/números si `input text` no funciona, consultando ayuda del SDK. No instalar teclados, cambiar IME global ni sobrescribir/leer el portapapeles personal. El fallo de un método no vuelve imposible el caso.
4. Tras intentos razonables por ruta, registrar método/valor esperado vs observado y pasar a casos independientes. Guardar resultados de este bloque en el informe antes de seguir.

### B2 — HU-24 y persistencia de recurrencias

1. Leer HU-24/FR-020–022 en EP-MOV y su backlog: separar aplicación de movimientos a obligaciones, contribución/meta y transferencia explícita; una reserva virtual sola no cubre HU-24. Usar rutas y datos elegibles según fuente aprobada, sin crear nuevos requisitos.
2. Probar vínculos/desvínculos y rechazos que realmente contemple la spec, con before/after de obligación/meta/movimiento y saldos auditables; no duplicar pago o registrar ingreso/gasto por inferencia. Si la ruta falta, documentar pantalla exacta y contraste con el APK identificado.
3. La regla propia `QA20261020RECCTB` terminó CANCELLED/revisión 4: consultar estado, cerrar solo Kipu y reabrir para comprobar persistencia. Para probar persistencia de un estado activo/pausado, crear una regla QA futura inequívoca si la UI lo permite, comprobar tras relanzar y cancelarla al terminar. No cambiar reloj ni reiniciar celular; no reactivar a ciegas una cancelada si el contrato no lo permite.
4. Guardar resultados y nuevo inventario al concluir el bloque.

### B3 — CRUD residual, cuentas, tarjetas/crédito y demás S1–S6

Desglosar las filas genéricas del informe y recorrer criterios aún no verificados: consultar/editar/cancelar y eliminar o archivar únicamente entidades QA según restricciones de historia; ingresos, límites/fechas de crédito, catálogos y formularios que no ejecuten compras/pagos reales, y caminos de S1 compatibles con preservar la sesión. No excluir todo S3 por Play billing ni todo S1 por permisos/biometría. No hacer login/logout si arriesga perder la sesión sin credenciales; precisar esa dependencia en el caso concreto.

Reutilizar fixtures existentes, no crear cuentas masivamente. Si se necesita cupo, primero probar la operación documentada de baja/archivo de un fixture QA propio y neutral, conservando historia; no eludir restricciones ni tocar cuentas preexistentes ajenas. Registrar el rechazo real y continuar otros casos. Una cuota de un tipo de billetera no demuestra cuota global agotada.

### B4 — diagnóstico correlacionado y BUG-001

1. Incorporar probes de Android Core y observar sesión/owner verificado (solo comparación booleana, sin UUID), request/worker/constraints, timestamps e intentos/errores del outbox de fixtures. Correlacionar evidencias, no atribuir un `RUNNING` genérico ni repetir esperas arbitrarias sin nuevo dato. No forzar jobs/retries/RPC ni alterar permisos/red.
2. BUG-001: identificar si una **misma intención** genera operaciones repetidas. No repetir toda la batería aprobada; usar un control que aporte temporalidad/identidad nueva y solo si se puede observar sin modificar código. Distinguir callback/operación, nuevo guardado terminado e historia cancelada. Si falta observabilidad, enumerar exactamente qué timestamps/operationId/isSaving/receipts/tracing habría que instrumentar en un trabajo futuro, sin implementarlo ahora.
3. Obtener solo agregados mínimos sanitizados de datos QA si hace falta una lectura local ya autorizada. No conservar/copiar bases completas en entregables. Tras cerrar lectores, eliminar únicamente temporales propios autorizados y comprobar DB, WAL y SHM exactos.

## Inventario de partida conocido (último estado reportado, no lectura actual del celular)

- `Billetera QA20261020CTAA`: wallet Plin QA, último saldo PEN 4.75.
- `QA20261020MOVCTAA`: gasto ordinario QA PEN 1.25 de esa wallet; no confundir con movimientos personales.
- `QA20261020CASHB`: cuenta Efectivo QA, último saldo PEN 0.00; transferencia QA y anulación permanecen en historia.
- `QA20261020RECCTB`: regla mensual QA, PEN 1.25, próxima fecha 2026-10-31, último estado CANCELLED/revisión 4.
- `QA20261020METAA`: meta QA archivada, objetivo PEN 2.00 y reserva 0.00.
- `QA20261020RCVA`: receivable histórica QA PEN 1.00, liquidada y saldo 0.00.
- `QA_AUDIT_20261020_CRON`: deuda QA de cronograma con pago parcial; último saldo reportado PEN 99.00. Los conteos/importe del cronograma anteriores corresponden a snapshots de otra etapa; refrescar baseline antes de cualquier nuevo guardado.
- Los otros fixtures anteriores están inventariados en `informe.md`; no abrir registros ambiguos para reconstruirlos.

## Checkpoint obligatorio por bloque y cambio de corrida

Guardar en `informe.md` cada caso ejecutado con HU/sprint/criterio, precondición, resultado, evidencia y limitación. Antes de agotar una corrida, dejar último screen/foco/valor validado, última acción confirmada, fixtures exactos creados/editados, balances conocidos y timestamps, capturas sanitizadas, casos completados, siguiente paso y qué NO debe repetirse. El informe no debe depender solo del chat.

El Coordinador verifica estado terminal de la corrida anterior antes de reasignar el dispositivo a la siguiente; conserva el mismo estado de prueba y no lanza dos operadores. Un límite de herramientas/tiempo de una corrida es un checkpoint, **no el cierre del encargo**.

## Hallazgos, issues y condición de cierre

Para cada nuevo defecto confirmado: oracle de requisitos, reproducción/frecuencia real, impacto y evidencia anonimizada; causa demostrada o hipótesis, propuesta y criterios de aceptación. Coordinador busca duplicados en `Lume03/Kipu`, añade evidencia a #60/#61 si corresponde o crea el nuevo issue, relee target/adjuntos y comunica URL a QA. No publicar potenciales como confirmados ni enlaces GitHub a archivos locales no subidos.

**Auditoría ABIERTA.** Cerrar solo cuando no queden casos autorizados y viables sin ejecutar. Al entregar: conteos actuales derivados de la matriz desglosada, nuevas pruebas/evidencias, issues creados/actualizados y cada residual con impedimento concreto y requisito de desbloqueo. Mantener separadas finalización de investigación, defectos abiertos y aprobación del release.

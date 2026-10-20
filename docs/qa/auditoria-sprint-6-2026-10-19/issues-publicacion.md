# Control de publicación de issues — Auditoría S1–S6

## Seguimiento vigente desde los 50 casos

Ambas revisiones UI/UX existen y fueron leídas; no sigue pendiente encontrar `revision-ux.md`. Baseline del informe comprobado: **50 casos (32 PASS, 2 FAIL, 1 BLOCKED, 15 NOT TESTED)**, 43 ocurrencias de imágenes embebidas que corresponden a 31 PNG distintos. Los resultados posteriores, incluida la regla activa nueva, requieren reconciliación por QA; esta cifra no cierra la auditoría.

Tras el agotamiento explícito de la corrida QA del chat y su handoff final, se creó la continuación secuencial `kipu/t_3fab3a86`; es el único operador/autor del informe mientras esté en curso. UI/UX mantienen sus archivos y no operan ADB. El Coordinador prepara y publica exclusivamente los seguimientos, sin tocar el informe ni diseños/código.

### Actualización de evidencia en issues existentes

Se publicó el borrador local `issues/seguimiento-artefacto-2026-10-20.md` como comentario en cada issue y se releyó cada comment ID exacto por API, comprobando URL, issue/repo de destino y cuerpo completo:

- BUG-002/#60: https://github.com/Lume03/Kipu/issues/60#issuecomment-6293231782
- BUG-003/#61: https://github.com/Lume03/Kipu/issues/61#issuecomment-6293232904

El seguimiento registra que QA observó igualdad del SHA-256 instalado con el candidato local y que el Coordinador recalculó el hash del candidato; aporta base/delta QA sin atribuir una build attestation ni causalidad/identidad retrospectiva a todos los síntomas. No se añadieron capturas nuevas ni se cambiaron estado, clasificación, frecuencia o labels de los issues; no se crearon duplicados.

### Borradores nuevos y condiciones previas

- `issues/UI-001-investigacion-tokens.md`: investigación de discrepancia fuente–canon, INFO/P3; no bug visual renderizado confirmado. Comparación directa de `Color.kt` con la tabla normativa y lectura de #39/#46/#48 ya realizadas; completar la búsqueda final de duplicados antes de publicar con tipo/labels de documentación/investigación, no como fallo funcional.
- `issues/UX-004-mejora-salida-archivo-tarjeta.md`: unifica UX-004/OBS-005 como mejora de navegación observada 1/1, causa y repetibilidad pendientes; no archivo fallido ni duplicación financiera demostrada. Esperar revisión de privacidad de los adjuntos y deduplicación final; una publicación potencial debe identificar claramente sus límites.
- Nueva revisión visual de privacidad en curso sobre el derivado de meta y las capturas de CRUD/crédito referenciadas, más las históricas aún no cubiertas; sin ADB, escrituras ni otra auditoría de diseño. No transferir al derivado el SAFE de otra imagen ni llamar SAFE a un nombre de archivo.

Se detectaron dos correcciones de autores: UI aún embebe el original de meta NEEDS_CONTEXT y debe usar un derivado aprobado; UX usa nueve links pero ninguna imagen embebida y debe convertir la evidencia pertinente a sintaxis `![...](...)`. El Coordinador no modifica esos informes bajo autoría ajena.

### UI-001 publicado como investigación, no bug visual renderizado

Tras la comparación de fuentes y lectura de #39/#46/#48, se repitieron búsquedas de tokens/canon/título y no se encontró un duplicado del conflicto normativo concreto. Se publicó el borrador `issues/UI-001-investigacion-tokens.md`:

- **https://github.com/Lume03/Kipu/issues/62** — OPEN; labels existentes `documentation`, `UI/UX`, `question`; sin label `bug`, asignaciones ni adjuntos.
- El Coordinador releyó el target exacto y verificó URL/repo, título, cuerpo completo, estado, labels y ausencia de assignees. El primer intento del wrapper local falló por sintaxis antes de ejecutar ninguna llamada; se corrigió y la creación efectiva fue única, con deduplicación y readback.
- Clasificación: discrepancia documental/código confirmada, motivo/override e impacto por pantalla pendientes; INFO/P3. No aumenta el conteo de bugs funcionales confirmados, no confirma incumplimiento WCAG, no altera la matriz PASS/FAIL ni aprueba un release. No se corrigió ningún diseño/código.

La revisión posterior de privacidad `deleg_65e394d2` ya terminó y sus 22 hashes coinciden: 18 SAFE, 3 NEEDS_CONTEXT y 1 NEEDS_REDACTION brutos. El derivado de meta y su vista anterior son SAFE; el original sigue excluido. Los dos PNG del wizard requieren contexto probatorio que limite la conclusión a los estados observados y relato QA, no causa/archivo fallido. El contador global de archivados se omite de nuevos adjuntos y HU-02 necesita atribución/derivado. Ver `issues/revision-privacidad.md`.

### Candidato UX-004 y documentación restante

`issues/UX-004-mejora-salida-archivo-tarjeta.md` continúa como borrador unificado UX-004/OBS-005, observado 1/1 y claramente identificado como mejora/potencial, sin fallo financiero demostrado. Antes de publicar: comprobar duplicados de archivo/wizard y usar únicamente imágenes permitidas con captions acotados; no adjuntar el contador global ni afirmar que una captura aislada demuestra la acción previa.

UX ya corrigió sus nueve referencias a nueve imágenes embebidas y el Coordinador verificó su existencia. UI debe sustituir el original de meta por el derivado **ya aprobado**; no esperar otra reproducción/aporte financiero ni modificar el defecto visual. Los autores conservan sus archivos y el nuevo QA mantiene dispositivo/informe exclusivos.

## Mandato de referencia — ampliación UI/UX y consolidación previa

El Owner mantiene la auditoría abierta y amplió la revisión a UI/usabilidad. La autorización actual permite publicar nuevos bugs confirmados con evidencia suficiente y problemas de UX/mejoras claramente identificados como tales; la disposición inicial de preparar solo BUG-002/003 se conserva abajo como historia, **no es un límite a los hallazgos nuevos**. No convertir investigaciones o propuestas de rediseño en fallos funcionales demostrados.

Se releyeron los cuerpos completos de #60 y #61 en el preflight de esta fase: ambos siguen OPEN y describen respectivamente pantalla vacía tras editar y ausencia de ruta UI de anulación de settlement vinculado, con causa pendiente y alcance acotado. Primero deduplicar contra esos cuerpos y el resto de candidatos; aportar evidencia a los existentes solo si es el mismo problema y la aportación es sustantiva. No crear otro issue por su interpretación UI y otro por UX.

Prioridad inmediata: QA debe incorporar HU-24/cobro parcial y persistencia de recurrencia ya comunicados, imágenes pertinentes con sintaxis Markdown y conteos reconciliados. El informe comprobado aún tenía 35 casos (21 PASS, 2 FAIL, 1 BLOCKED, 11 NOT TESTED), cifra provisional que no incluía todo el último avance. Ver [checkpoint-coordinacion.md](checkpoint-coordinacion.md) para contratos de QA/UI/UX y solicitudes de evidencia agrupadas. UI y UX escriben cada uno su revisión; no se lanzan escritores/operadores nativos duplicados mientras los participantes del chat tienen esas responsabilidades.

Las ocho capturas recientes pasaron comprobación PNG y revisión visual de privacidad (`deleg_84e24a5f`), con hashes comprobados por el Coordinador: **7 SAFE; `HU-24-goal-internal-transfer.png` NEEDS_CONTEXT**. Ese original contiene una zona inferior de cuenta/saldo no atribuibles a QA y queda excluido de publicación y adjuntos; QA debe producir una copia sanitizada sin repetir el aporte, manteniendo la evidencia relevante, y se revisará su nuevo hash antes de aprobarla. Ver [issues/revision-privacidad.md](issues/revision-privacidad.md). Las siete SAFE no acreditan por sí mismas secuencias completas, clasificación operativa ni sincronización remota. La revisión no usó ADB ni modificó imágenes. Conservar borrador local antes de cualquier publicación, usar mecanismos soportados, etiquetas existentes y sin asignaciones arbitrarias; releer exactamente issue/comentario/adjuntos después de publicarlos. No hay nueva publicación en este preflight.

La ampliación recibida se trunca en el listado de entrega final tras «Hallazgos nue…». Se registra ese punto sin reconstruir el resto; las responsabilidades, entregables conocidos y autorización explícita anteriores al corte permiten avanzar sin esperar confirmaciones menores.

## Responsabilidad y autorización

- Autor y publicador exclusivo de este registro y de los issues: Coordinador (`tech-lead`). QA conserva exclusivamente el celular, `informe.md`, capturas y logs. Android Core posee solo sus apéndices técnicos.
- El Owner autorizó continuar pruebas posibles y crear issues sin confirmaciones menores. Este permiso sustituye la prohibición anterior de crear issues, **no** las prohibiciones de implementar, recompilar, instalar, modificar infraestructura o hacer commits/push/PR/merge.
- El mensaje recibido está truncado durante la sección 7 y alude a una sección 8 no disponible. Se intentó recuperar el original mediante búsquedas de sesiones, lectura focalizada del mensaje grupal guardado y la copia local del mismo grupo: esta última conserva 8 000 caracteres y tampoco contiene la sección 8. No se accedió a mensajes privados para suplirla ni se inventan sus condiciones.
- **Disposición autónoma y reversible:** la autorización explícita visible permite crear issues y ordena no esperar decisiones menores. Se retira el bloqueo de publicación que había añadido el Coordinador: publicar únicamente BUG-002 y BUG-003 sustentados, tras deduplicación y revisión de privacidad, usando las reglas conservadoras visibles de las secciones 3, 4, 6 y 7. Esta es una suposición procedimental documentada, no una afirmación de haber recibido o cumplido el texto ausente; no amplía permisos ni autoriza publicar potenciales/datos sensibles.
- Premium QA local autorizado hasta **2026-10-20 23:51 UTC−5**. No acreditar Premium comercial, derechos remotos ni cuotas del servidor a partir de ese acceso; no renovar ni eludir su vencimiento.

## Preflight GitHub verificado

- Remoto: `https://github.com/Lume03/Kipu.git`.
- Repositorio de destino confirmado por API: **Lume03/Kipu**, `https://github.com/Lume03/Kipu`.
- Visibilidad: **PUBLIC**. Issues habilitados y acceso autenticado suficiente comprobados; no se guardan credenciales en este documento.
- HEAD de fuentes inspeccionadas: `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`. No demuestra la procedencia del APK instalado.
- Consultados issues abiertos/cerrados y búsquedas de cronograma, sincronización y pantalla en blanco. No se identificó un duplicado específico del guardado del cronograma en esta consulta; repetir la comprobación justo antes de cualquier creación y leer por completo los candidatos relacionados.
- La CLI instalada ofrece `gh issue create --attach` para imágenes/vídeos y `--body-file` para cuerpos autosuficientes; su ayuda advierte que una creación con adjuntos parcialmente fallidos puede devolver código distinto de cero **aunque el issue ya exista**. Ante ese resultado, releer el URL/issue antes de reintentar para no duplicarlo.
- No crear labels, milestones, projects ni asignaciones arbitrarias. Usar labels existentes pertinentes y expresar severidad/prioridad en el cuerpo cuando no exista label equivalente.

## Reglas conservadoras de preparación

1. Clasificar por separado comportamiento observado, incumplimiento documentado, causa confirmada e hipótesis. Un `PENDING` aislado o filas `CANCELLED` históricas no prueban un defecto.
2. No publicar un hallazgo potencial como bug confirmado. Registrar frecuencia como reproducciones/intentos realmente medidos; si no existe una medición, declararlo explícitamente.
3. No publicar capturas con datos ajenos a los fixtures QA, secretos, emails, cuentas, UUIDs o saldos personales; revisar todas las zonas, no solo la zona del defecto. Mantener fuera del repositorio y GitHub las bases completas, WAL y dumps.
4. Los artefactos son locales y no se autoriza commit/push: **no fabricar enlaces GitHub blob a archivos sin publicar**. El cuerpo del issue debe ser autosuficiente, incorporar solo agregados sanitizados y usar únicamente adjuntos sanitizados cuya subida se verifique.
5. Distinguir evidencia del APK en el dispositivo, evidencia SQLite local y evidencia del servidor. No presentar unit tests del checkout como aceptación del APK o confirmación remota.
6. Tras crear cada issue, releer el número exacto con GitHub y verificar repo, título, estado, cuerpo, labels y URLs reales de adjuntos antes de registrar éxito.
7. Devolver a QA el mapeo hallazgo → issue URL para que QA, y no otro escritor, lo incorpore al informe final.

## Estado de candidatos

| Candidato | Observación disponible | Condición pendiente antes de decidir publicación |
|---|---|---|
| BUG-001 — reemplazos de cronograma en ráfaga | Se observaron dos revisiones en una ráfaga de cuatro taps; un único conjunto activo de tres cuotas por PEN 100.00, sin doble principal/pagos/caja; historia cancelada conservada | Contrastar contrato y nuevo control de guardados: no se demostró solapamiento durante `isSaving`. Evaluar incumplimiento sin convertir el historial legítimo en duplicación financiera |
| Outbox `PENDING` sin intentos | En la observación inicial, deuda y comandos locales seguían pendientes; no se confirmó persistencia remota | Diagnóstico de sesión, Worker, restricciones e intentos/respuestas. No forzar jobs, SQL de escritura ni RPC financieras manuales |
| BUG-002 — pantalla vacía tras guardar edición QA | QA confirmó el defecto visual en 2/2 intentos y recuperación tras relanzar; causa desconocida y APK sin vínculo al checkout | Borrador autosuficiente en `issues/BUG-002.md`; revisión visual de privacidad del PNG y deduplicación antes de crear |
| BUG-003 — anulación de pago ligado remite a gestión sin acción visible | QA confirmó el recorrido bloqueado 1/1; HU-28 AC6/FR-012 y HU-29 AC6, sin cambios por el intento | Borrador en `issues/BUG-003.md`; revisión de privacidad de dos recortes y deduplicación antes de crear |
| Otros hallazgos S1–S6 | Pruebas y consolidación todavía en curso por QA | Evidencia por caso, oracle documentado y deduplicación |

## Investigación técnica paralela

- Kanban `kipu/t_eabed8f9`, asignada a Android Core: outbox, navegación de edición y contrato del cronograma, **sin ADB ni fixes**.
- Entregable autorizado: `diagnostico-sync-nav.md`.
- El apéndice anterior `analisis-cronograma.md` y el informe principal se preservan.
- Continuación QA canónica `kipu/t_f1994435`: nueva corrida para completar casos aún posibles y el informe, sustituyendo el run grupal agotado por límite de herramientas; sigue siendo el mismo perfil QA y el único operador. No hay un segundo auditor del dispositivo.

## Disposición técnica tras la investigación complementaria

Revisión del Coordinador: 2026-10-20, aproximadamente 01:00 UTC−5. `diagnostico-sync-nav.md` existe y su SHA-256 es `bc135023c33d5dbd584c89dfdc4b35d268bae44dee88385130dbe1ff3257517a`; los 24 hashes de documentos y fuentes declarados coinciden con sus archivos actuales. El análisis es estático, no una reproducción adicional ni prueba de correspondencia APK/commit.

- **BUG-001:** el contrato de `SET_DEBT_SCHEDULE` permite reemplazar/cancelar cuotas activas y conservar historial; no define un máximo de revisiones por ráfaga ni prohíbe un guardado nuevo después de finalizar. La observación de dos revisiones se conserva, pero sin timeline de `isSaving`/callbacks no se demuestra que ambas pertenecieran a una operación todavía en curso. No publicar como bug confirmado de duplicación activa/financiera ni alterar retrospectivamente los resultados de QA; QA debe separar observado, incumplimiento demostrado e hipótesis en la versión consolidada.
- **Outbox:** en el código, el Worker retorna `retry` antes de reclamar comandos cuando el owner objetivo no coincide con `currentOwner?.verifiedUserId`. Ello es compatible con `PENDING` y cero intentos, pero no demuestra que sucediera en el APK. QA debe observar estado/constraints/timestamps del trabajo existente y disponibilidad de owner/sesión, sin forzar jobs ni llamadas financieras. Un plazo aislado de 20 segundos no prueba fallo de sincronización.
- **Edición en blanco:** verifiqué en fuentes que un éxito emite `TransactionRevised` y `CloseEditor` y que sus dos callbacks solicitan `popBackStack()`. Es un candidato concreto para correlación con el estado de navegación, no causa confirmada del APK ni pérdida de datos demostrada. Priorizar una reproducción controlada con frecuencia, persistencia del movimiento y recuperación.

## Issues publicados y verificados

Publicación realizada el 2026-10-20. Se releyeron ambos targets exactos en GitHub y se comprobó repo, título, estado `OPEN`, cuerpo completo de los borradores, labels existentes `bug` y `UI/UX`, y ausencia de asignaciones arbitrarias.

| Hallazgo | Issue verificado | Severidad / prioridad | Frecuencia de QA | Adjunto público verificado |
|---|---|---|---|---|
| BUG-002 — pantalla vacía tras editar | [Lume03/Kipu #60](https://github.com/Lume03/Kipu/issues/60) | Media / P2 | 2/2 | [Imagen del contenido vacío](https://github.com/user-attachments/assets/5a3f3759-d49b-44e0-b576-60b5876da256) |
| BUG-003 — anulación de pago ligado sin acción en gestión | [Lume03/Kipu #61](https://github.com/Lume03/Kipu/issues/61) | Media / P2 | 1/1 | [Recorte del menú de deuda](https://github.com/user-attachments/assets/435a3f89-ea02-41f3-a5d9-c2d4d11ab678) |

Los dos adjuntos se descargaron desde sus URLs públicas: respuesta `image/png` y SHA-256 idéntico al PNG local aprobado en la revisión visual. BUG-002: `0feb2b39fdf4189b6e516e38d62554a8d33446d208d159430daabeec596cad14`; BUG-003 menú: `2a63f14e46b1286b89f38d850a193d5670ad8fc4f1956a479a76294a14f3cde8`. La captura del aviso de anulación con importe **no se subió**; se transcribió el aviso desde el registro de QA. El alcance de cada imagen y la causa técnica desconocida figuran explícitamente en los cuerpos.

BUG-001, OBS-001 y el outbox PENDING **no** se publicaron como bugs confirmados. No se hicieron commits, push, PR, merges, cambios de código, instalaciones ni modificaciones de infraestructura para esta publicación.

QA incorporó el mapeo al informe bajo su autoría exclusiva. `t_f1994435` cerró un avance acotado; `t_93b49fd6` creó y ejerció fixtures propios de wallet/gasto/meta/receivable; `t_f57c9bf4` verificó la ruta real de recurrencia y su lifecycle hasta cancelación, creó la cuenta B propia y comprobó transferencia/anulación con suma QA conservada. **Disposición histórica, supersedida por el nuevo mandato del Owner:** se había detenido la continuidad runtime tras fallos de foco/texto. El Owner ordenó continuar: corregir foco, comprobar valores y probar alternativas soportadas antes de declarar limitación; conservar checkpoints y reanudar secuencialmente hasta agotar los casos autorizados y viables. La entrega parcial y las tarjetas `done` no cierran la auditoría. El estado vigente y los bloques pendientes están en [checkpoint-coordinacion.md](checkpoint-coordinacion.md). `t_4c8a0111` terminó la reconciliación editorial final del informe (introducción, estados permitidos y conteos), sin ADB ni nuevos fixtures. El Coordinador verificó el archivo real: **26 casos S1–S6: 15 PASS, 2 FAIL, 1 BLOCKED y 8 NOT TESTED**. El conteo anticipado de 27 en el briefing era incorrecto; no se añadieron casos para forzarlo. Los PASS son casos acotados, no HU completas. La cobertura S1–S6 sigue parcial y el dictamen `CHANGES REQUESTED` no aprueba el release.

### Comprobación final del artefacto y privacidad — 2026-10-20

- SHA-256 de `informe.md`: `df13a0b7815759ae268b950027dd9536f3718454bab9c7893f06a5cdba116663`, coincidente con el handoff QA terminal.
- Matriz parseada independientemente, solo S1–S6: 26 filas, estados válidos; 14 referencias relativas existentes y 8 imágenes embebidas verificadas por Pillow. Esta verificación de archivo/decodificación no es una revisión visual nueva ni demuestra los casos observados en el celular.
- `git diff --check` sin errores; comprobación adicional del informe no tracked mediante `git -c core.autocrlf=false diff --no-index --check -- NUL <informe>` sin diagnósticos. El primer intento con autocrlf por defecto emitió un warning LF→CRLF, no un defecto del Markdown; no se normalizaron ni alteraron los bytes para silenciarlo. Sin cambios tracked ni staged; HEAD sigue `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`.
- Se releyeron #60 y #61: ambos OPEN. No se publicaron nuevos issues ni se ejecutaron pruebas/builds nuevos en el cierre editorial.
- Control de privacidad: se detectó un sidecar regenerable residual `qa-audit-final.db-shm` del snapshot QA anterior (32 768 bytes). Tras terminar todos los runs, el Coordinador retiró únicamente ese temporal propio autorizado y comprobó ausencia de las seis rutas exactas conocidas: `kipu.db`, `kipu.db-wal`, `kipu.db-shm`, `qa-audit-final.db`, `qa-audit-final.db-wal`, `qa-audit-final.db-shm`. No se abrió ni modificó la DB del dispositivo, no se creó otra copia, no se tocaron datos personales/preexistentes y no hay DB/WAL/SHM en los entregables.
- Permanecen fixtures QA en la app según el inventario del informe; la limpieza de temporales no equivale a borrarlos ni demuestra sincronización remota. Reinicio de la regla, backend, provenance del APK y cobertura residual conservan sus límites explícitos.

## Publicación de hallazgos restantes — 2026-10-20

A petición del Owner, se publicaron siete seguimientos nuevos en `Lume03/Kipu`. Antes de publicar se revisaron los issues abiertos/cerrados disponibles y se deduplicó contra #60–#62 y el antecedente #48. Se usaron exclusivamente labels existentes; ninguno se presentó como bug financiero/visual confirmado. Cada URL exacta se releyó con `gh issue view`: estado OPEN, cuerpo, labels y sin asignaciones arbitrarias. Los siete adjuntos fueron releídos desde el cuerpo publicado y respondieron HTTP 200 `image/png`.

| Hallazgo | Issue | Clasificación / labels | Evidencia adjunta verificada |
|---|---|---|---|
| UX-004 / OBS-005 — archivo de tarjeta devuelve al wizard | [#63](https://github.com/Lume03/Kipu/issues/63) | Mejora UX observada 1 secuencia; `enhancement`, `UI/UX`; archivo sí efectivo, no se afirma duplicación | [Paso 3 tras archivo](https://github.com/user-attachments/assets/700ee2f8-f023-4898-90d6-8a04c1df6812); [paso 2 al volver](https://github.com/user-attachments/assets/c481af33-f32b-4dd0-b8ce-4bd82d530532). Ambas SAFE con contexto QA; no se adjuntó contador global de archivados. |
| UX-001 — acciones deshabilitadas en meta archivada | [#64](https://github.com/Lume03/Kipu/issues/64) | Usabilidad/criterio pendiente; `question`, `UI/UX`; no bug funcional confirmado | [Meta archivada QA](https://github.com/user-attachments/assets/f73ea8be-0852-493a-9946-8a916a640600), SAFE; no demuestra aporte rechazado. |
| UX-002 / OBS-003 — significado de “Verificado” | [#65](https://github.com/Lume03/Kipu/issues/65) | Semántica de producto; `question`, `UI/UX`; no implica sync | [Detalle de movimiento QA](https://github.com/user-attachments/assets/b2f58e95-138f-4140-af35-dbc196735c9f), SAFE; no demuestra sincronización. |
| UX-003 / OBS-002 — semántica de X en borrador | [#66](https://github.com/Lume03/Kipu/issues/66) | Decisión de producto/usabilidad; `question`, `UI/UX`; comportamiento esperado pendiente | [Formulario QA reabierto](https://github.com/user-attachments/assets/c4c31cc8-4324-41b1-928e-4c83fd92be0b), SAFE; se aclara que imagen sola no prueba la secuencia. |
| OBS-004 — si efectivo consume cupo Free | [#67](https://github.com/Lume03/Kipu/issues/67) | Investigación/documentación; `question`, `documentation`, `UI/UX`; no incumplimiento demostrado; se referencia #48 como antecedente relacionado | [Cuenta QA creada](https://github.com/user-attachments/assets/ea360fc6-972c-48c5-9d8b-7ce9b282b40e); [estado posterior a archivar](https://github.com/user-attachments/assets/f3bdea52-221b-4a0b-b96c-00ac0a73fb90). SAFE; no establecen por sí solas la regla contractual. |
| Outbox QA PENDING / causa de sync | [#68](https://github.com/Lume03/Kipu/issues/68) | Investigación; `question`; no se atribuye fallo remoto | Sin imagen: el agregado sanitizado y la observabilidad requerida se expresan en el cuerpo. No se adjuntaron DB/WAL, payloads ni identificadores. |
| Taps rápidos del cronograma / posible intención repetida | [#69](https://github.com/Lume03/Kipu/issues/69) | Investigación HU-29; `question`; explícitamente no confirma duplicación financiera ni toma CANCELLED como cuotas activas | Sin imagen: las capturas locales no demuestran temporalidad/solapamiento y se evita presentarlas como evidencia de concurrencia. |

Los borradores correspondientes permanecen en `issues/` como copia local de publicación. No se hicieron cambios de código, builds, instalaciones, commits, push, PR o merges. La auditoría funcional conserva sus conteos/estado: los issues de seguimiento no convierten NOT TESTED en PASS ni aprueban un release.


## Issues nuevos por capturas del Owner — 2026-10-20

El Owner dejó diez capturas originales en `capturas/Capturas/`. Se conservaron sin modificación. Para GitHub se generaron únicamente recortes sanitizados en `capturas/Capturas/sanitizadas/`, retirando avatar, saldos, cuentas y columnas de importes cuando no eran necesarios. Se preservaron los PNG originales. Los nueve adjuntos publicados fueron verificados por lectura del issue y HTTP 200 `image/png`.

| Hallazgo | Issue verificado | Clasificación / labels | Capturas públicas |
|---|---|---|---|
| Iconos distintos para configuración en Mi dinero / Movimientos | [#70](https://github.com/Lume03/Kipu/issues/70) | Mejora P3; `enhancement`, `UI/UX`; fuente muestra callbacks al mismo destino | [Mi dinero](https://github.com/user-attachments/assets/29703c0e-36c5-4785-9b32-361999de9fbe); [Movimientos](https://github.com/user-attachments/assets/218eeb4f-1ef2-483c-95d1-cb4bb8edb538) |
| Fechas opcionales presentadas como texto en deuda, dinero prestado y meta | [#71](https://github.com/Lume03/Kipu/issues/71) | Mejora P3; `enhancement`, `UI/UX`; no se atribuye incumplimiento de requisito que exija calendario | [Edición deuda](https://github.com/user-attachments/assets/651ed292-da00-4e7b-a34f-d96d0e3d4b90); [dinero prestado](https://github.com/user-attachments/assets/9d37d00c-154f-4ef0-a999-40b5cd6ee8c4); [meta](https://github.com/user-attachments/assets/1664c1a3-911a-4e1e-9bed-1842574ae789) |
| Selector de comercio no visible en formulario de gasto con detalles expandidos | [#72](https://github.com/Lume03/Kipu/issues/72) | Bug reportado; `bug`, `UI/UX`; FR-021 lo exige, pero el checkout contiene fila condicional, así que el origen/build debe verificarse; relacionado, no duplicado de #46 | [Formulario recortado](https://github.com/user-attachments/assets/00a06254-6110-42c7-954f-09516e12c0c4) |
| Iconos `?`/`i` de cronograma y ajustes sin respuesta | [#73](https://github.com/Lume03/Kipu/issues/73) | Bug reportado; `bug`, `UI/UX`; checkout tiene callback placeholder vacío; frecuencia no medida | [Cuotas](https://github.com/user-attachments/assets/d0ddf218-1ad6-47ce-9cd9-22f8c3a4e1ad); [ajustes](https://github.com/user-attachments/assets/84011f27-b9fa-4285-82d7-6ced6cf787fb) |
| Feedback de guardado/reemplazo de cronograma no percibido | [#74](https://github.com/Lume03/Kipu/issues/74) | Investigación UX; `question`, `UI/UX`; no confirma ausencia en todas las builds ni duplicación financiera; relacionado con #69 | [Historial sin columna de importes](https://github.com/user-attachments/assets/db2f1852-f382-4ea7-9ca1-6e6bd271e08c) |

Se añadió además el comentario de contexto a [#69](https://github.com/Lume03/Kipu/issues/69#issuecomment-6294214921), manteniendo separados idempotencia/taps ( #69 ) y feedback/confirmación de reemplazo ( #74 ). No se tocaron código, datos financieros, build, issues existentes fuera de ese comentario, ni se hicieron commits/push.

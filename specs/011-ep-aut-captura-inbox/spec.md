# Feature Specification: EP-AUT — Captura Automática e Import Inbox

**Feature Branch**: `011-ep-aut-captura-inbox`
**Created**: 2026-10-28
**Status**: Incrementos S8 y S9 documentados; HU-49 implementada en Room v37 (migración 35→36 y 36→37 validadas en esquema y hardware, user_version 37 verificado en ambos dispositivos). FR-012 reconciliado formalmente con Room v37 tras la aprobación explícita del Product Owner («Conservar eventos incompatibles para revision», [decisiones del Owner](../sprint-9-owner-decisions.md)). Pruebas en hardware realizadas: OCR sintético local en A16 (PASS) y frontera URI Android en S24 (PASS aislado). Casos NO VERIFICADO permanecen abiertos (sharing UI real desde Yape, notificación viva en ambos órdenes y ocho muestras privadas ausentes).

## Scope & Traceability

Incluye HU-45, HU-46, HU-47, HU-48, HU-49, HU-50 y HU-51 del backlog (S8 y S9, 2026-10-28–2026-11-03). No incluye SMS ni sincronización de candidatos, eventos fuente o evidencia (captura y evidencia son exclusivamente locales en Room). Accesibilidad no se añade como requisito de aceptación para este alcance.

Las fuentes aprobadas por el Owner el 2026-10-28 son Yape, Plin dentro de apps bancarias, BCP, Interbank, BBVA y Scotiabank, mediante catálogo versionado y parsers con fixtures. Solo en builds debug `com.android.shell` simula el proveedor indicado por el título; nunca es fuente en release. Candidatos, eventos y evidencia viven solo en Room. El aprendizaje usa las tablas/casos existentes HU-16/HU-17, que ya sincronizan; no hay migración Supabase nueva.

## Clarifications (decisiones del Owner, 2026-10-28 y 2026-11-03)

1. Catálogo de seis fuentes compatibles; `com.android.shell` se admite solo en debug y enruta por título.
2. Persistencia de captura Room-only, sin outbox/Supabase; preferencias aprendidas reutilizan HU-16/HU-17.
3. HU-43 usa `CAPTURE_REVIEW`, referencia `CAPTURE_CANDIDATE`, canal `capture-review`, abre candidato y se deduplica por candidato (detalle en EP-NOT).
4. Specs/011 y actualizaciones 007/010 se documentan manualmente para este sprint.
5. Decisiones del Product Owner para HU-49 (2026-11-03):
   - **(D1)**: Misma referencia de operación normalizada vincula automáticamente las evidencias (notificación y OCR) a un único movimiento/candidato raíz en ambos órdenes de llegada. Sin referencia, si coinciden importe + moneda + dirección + contraparte normalizada + hora dentro de ±10 minutos y existe exactamente un candidato compatible que aún no posea esa fuente, se propone la unión explícita al usuario para su confirmación; referencias distintas o múltiples candidatos nunca se fusionan automáticamente. Las ediciones previas del usuario se preservan.
   - **(D2)**: OCR local Yape permitido en plan Free con consentimiento y revisión (refinamiento 2026-10-21); el escenario 7 del backlog (Free sin OCR) queda superado. Sin consentimiento no se extrae la imagen. Captura y evidencia operan exclusivamente en Room v37 (esquema v36 histórico con capture_evidence y fields_edited, actualizado a v37 en Sprint 9 con HybridYapeMigrationTest sin sincronización remota).
6. Decisión del Product Owner sobre persistencia de eventos incompatibles (2026-11-03 [2026-11-04 UTC]): a la consulta sobre la contradicción de FR-012 y el índice no único de Room v37, el Owner respondió exactamente: **«Conservar eventos incompatibles para revision»** ([decisiones del Owner](../sprint-9-owner-decisions.md)). Se aprueba conservar eventos con datos incompatibles separados sin fusión automática ni duplicación de efectos contables en el ledger. Se reconcilian FR-012, la entidad `capture_evidence`, contratos, modelo, plan y tareas con Room v37 (PK `owner_id, source, event_id`, índice no único `owner_id, reference, source`, e índice `owner_id, root`). Misma referencia solo fusiona compatibles según invariantes actuales; incompatibles se conservan para revisión, sin inventar nuevos criterios de compatibilidad ni cambiar comportamiento no probado.

## Dependencies

| HU | Dependencias de backlog | Estado informado |
| --- | --- | --- |
| HU-45 | HU-05, HU-58, HU-59 bloqueantes; HU-46/HU-49 relacionadas | Gate reportado valida sesión, permiso vivo, consentimiento y entitlement; HU-49 implementada en S9. Requiere verificación real en dispositivo. |
| HU-46 | HU-45 bloqueante; HU-15/HU-16 parciales | Pipeline y parsers reportados en T1/T2; no prueba compatibilidad real de proveedores. |
| HU-47 | HU-46, HU-18, HU-23 bloqueantes; HU-51 parcial | Scorer/autorregistro reportados en T3; pruebas no ejecutadas. |
| HU-48 | HU-46, HU-08, HU-13 bloqueantes | Confirmación reutiliza registro financiero; validar en dispositivo. |
| HU-49 | HU-45, HU-46, HU-23, HU-58, HU-59 bloqueantes; HU-51, HU-16 parciales; HU-34, HU-50 relacionadas | Correlación híbrida Yape (notificación + OCR/Share) implementada en S9 en Room v37 (capture_evidence, fields_edited); validación en hardware ejecutada para OCR sintético y frontera URI Android; sharing real Yape, notificación viva y ocho muestras privadas permanecen NO VERIFICADO. |
| HU-50 | HU-16, HU-17, HU-46, HU-51 bloqueantes | Aprendizaje posterior a confirmación y selector canónico reportados en T5/T5b. |
| HU-51 | HU-46 bloqueante; HU-47/HU-43/HU-50 relacionadas | Bandeja/revisión reportadas en T4; pruebas no ejecutadas. |

## User Scenarios & Acceptance Criteria

### HU-45 — Activación del listener
1. Premium y permiso activos + notificación compatible autorizada → se entrega al flujo de candidato.
2. Fuente excluida → se ignora sin persistir contenido.
3. Permiso revocado → callback no procesa señal.
4. Premium vencido → no inicia captura; registro manual sigue disponible.

### HU-46 — Extracción y candidato
1. Señal con importe, fecha y tarjeta única → candidato prellenado con origen.
2. Últimos cuatro coinciden con dos instrumentos → pide selección y no autorregistra.
3. Fecha no fiable/ausente → queda marcada inferida y exige revisión.
4. Parser sin importe → no crea movimiento confirmado y explica incertidumbre.

### HU-47 — Confianza y autorregistro
1. Campos requeridos válidos, sin conflicto → autorregistro único si es débito elegible.
2. Categoría dudosa → revisión sin alterar saldo.
3. Varias carencias → motivos visibles y opción corregir/descartar.
4. Crédito fiable → sigue pendiente de confirmación.
5. Premium vence antes de guardar → revalida y no autorregistra.

### HU-48 — Confirmación de crédito
1. Directo confirmado → principal registrado una vez.
2. Cuotas confirmadas → plan consistente con principal.
3. Salir sin confirmar → no cambia banco ni deuda.
4. Corregir instrumento a débito → desaparecen cuotas y se valida operación corregida.

### HU-50 — Aprendizaje desde correcciones
1. Corregir comercio/categoría y confirmar → asociación para señales futuras.
2. Descartar edición no confirmada → no aprende.
3. Preferencias aisladas por usuario.
4. Cambiar preferencia no reclasifica historia.

### HU-51 — Bandeja y evidencia mínima
1. Completar y confirmar → un movimiento y candidato resuelto.
2. Descartar → sin efecto en saldo.
3. Reconfirmar → devuelve movimiento existente.
4. Premium vencido: convertir explícitamente a manual sin extracción nueva y preservar dedupe.
5. Limpiar evidencia temporal; no incluir contenido sensible en logs.

### HU-49 — Yape híbrido, OCR y deduplicación
1. **Misma referencia (ambos órdenes)**: Al recibir notificación y Share/OCR de la misma operación en cualquier orden (notificación primero u OCR primero), la misma referencia normalizada vincula la segunda fuente como evidencia al candidato/movimiento raíz existente, sin duplicar en ledger ni crear un segundo movimiento. Raíces ya confirmadas siguen siendo destinos válidos.
2. **Sin referencia y coincidencia única**: Si una fuente carece de referencia pero coinciden importe, moneda, dirección, contraparte normalizada y hora dentro de ±10 minutos, y hay exactamente una raíz compatible sin esa fuente, se propone la unión y el usuario confirma si une o mantiene separado.
3. **Múltiples candidatos o referencias distintas**: Si existen múltiples candidatos compatibles o las referencias de operación difieren, nunca se realiza fusión automática; se conservan como candidatos independientes.
4. **Preservación de campos editados**: Al asociar una segunda evidencia, los campos previamente modificados por el usuario (`fields_edited`) se preservan intactos sin sobrescritura.
5. **OCR local Yape en Free**: Permitido con consentimiento explícito `OTHER_APP_NOTIFICATION_CONTENT=GRANTED` y revisión obligatoria (decisión 2026-10-21; escenario 7 del backlog superado). Sin consentimiento no se extrae la imagen.
6. **Reembolsos e importes iguales**: Operaciones con distinta dirección o historia (ej. reembolsos) no se fusionan por simple coincidencia de importe.
7. **Persistencia local**: Candidatos y evidencia viven estrictamente en Room v37 (`capture_evidence`, `fields_edited`; esquema histórico v36 preservado), sin outbox ni sincronización a Supabase.

## Functional Requirements

- **FR-001 (HU-45/E1)**: Leer contenido solo tras validar fuente, sesión/owner, permiso de listener, consentimiento `OTHER_APP_NOTIFICATION_CONTENT=GRANTED` y entitlement Premium efectivo verificado. Revocación/falta de acceso detiene captura; registro manual disponible.
- **FR-002 (HU-45/E2–E3)**: Ignorar fuentes no catalogadas y callbacks sin permiso sin persistir contenido; no procesar historia anterior ni prometer cobertura universal.
- **FR-003 (HU-46/E1–E4)**: Extraer importe, moneda, dirección, fecha, comercio y últimos cuatro cuando existan y crear primero un candidato, nunca una transacción confirmada. Marcar inferencias; no adivinar instrumento ni fecha.
- **FR-004 (HU-47/E1–E5)**: Confianza determinista: **Alta** solo con importe, moneda, dirección, instrumento único, categoría resuelta, fecha fiable y sin conflicto/duplicado; **Media** si hay exactamente una duda no crítica; **Baja** ante varias carencias o falta de importe/dirección. Autorregistro solo de Alta débito/gasto, revalidando acceso. Crédito siempre exige confirmación.
- **FR-005 (HU-48/E1–E4)**: La confirmación es la única puerta al ledger para candidatos revisados; reutiliza el registro financiero manual, admite directo/cuotas solo con crédito y es idempotente. Salir no produce efectos.
- **FR-006 (HU-50/E1–E4)**: Aprender solo correcciones confirmadas, por owner, mediante HU-16/HU-17; no aprende al descartar, no cambia catálogo general ni reclasifica historia. El diseño técnico exige Premium verificado para guardar preferencias.
- **FR-007 (HU-51/E1–E5)**: Bandeja de candidatos propios con origen/motivos; permite editar, confirmar y descartar. Reconfirmar devuelve movimiento existente. Al vencer Premium se permite conversión explícita a manual sin nueva extracción y con la misma clave de dedupe.
- **FR-008 (decisión Owner 2026-10-28)**: Persistir eventos, candidatos y evidencia solo en Room con consultas filtradas por owner; excluir DB del backup; no sincronizar filas de captura.
- **FR-009 (HU-46/HU-51, Constitución III)**: No registrar en logs títulos, textos, importes ni comercios. No leer extras antes de validar catálogo y gate. Evidencia mínima, máximo 280 caracteres, sin PAN completo, expira a siete días y se borra al resolver/expirar.
- **FR-010 (HU-49/D1 Deduplicación simétrica)**: Correlación entre notificación y Share/OCR en ambos órdenes. Misma referencia normalizada vincula la evidencia al candidato/movimiento raíz existente sin segundo movimiento en ledger. Sin referencia, si coinciden importe + moneda + dirección + contraparte normalizada + hora dentro de ±10 minutos con exactamente un candidato compatible sin esa fuente, proponer unión al usuario; prohibida la fusión automática si hay múltiples candidatos o referencias distintas. Preservar campos editados por el usuario.
- **FR-011 (HU-49/D2 OCR local en Free)**: Permitir extracción OCR local de comprobantes Yape a usuarios del plan Free mediando consentimiento explícito `OTHER_APP_NOTIFICATION_CONTENT=GRANTED` y revisión obligatoria; supera el escenario 7 del backlog. Archivos no imagen o inválidos se rechazan antes de procesar bytes.
- **FR-012 (HU-49/Room v37 Persistencia local y eventos incompatibles)**: Persistir evidencia local en tabla Room `capture_evidence` bajo esquema Room v37 con clave primaria compuesta `(owner_id, source, event_id)`, índice no único sobre `(owner_id, reference, source)` e índice sobre `(owner_id, root)`. Cuando existan eventos con la misma referencia normalizada pero datos incompatibles, se conservan separados para revisión del usuario sin fusión automática ni duplicación de efectos contables en el ledger (decisión del Owner 2026-11-03 [2026-11-04 UTC], [decisiones del Owner](../sprint-9-owner-decisions.md)). Misma referencia solo fusiona eventos compatibles según los invariantes vigentes. Captura y evidencia son estrictamente locales en Room (sin outbox ni sincronización remota).

## Key Entities — Room v37 (historial v36 preservado)

- `capture_source_events`: owner, huella SHA-256 de paquete/clave/postTime/texto normalizado (sin texto persistido), paquete, hora y resultado; PK owner+huella.
- `capture_candidates`: UUID, owner, proveedor, estado (`PENDING|CONFIRMED|AUTO_REGISTERED|DISCARDED`), campos extraídos/corregidos, instrumento, confianza/motivos, crédito/cuotas, idempotencia y evidencia temporal; único por owner+huella fuente.
- `capture_evidence` (Room v37; reconciliado y formalizado con decisión del Owner 2026-11-03 [2026-11-04 UTC], [decisiones del Owner](../sprint-9-owner-decisions.md)): owner_id, source (`NOTIFICATION|OCR`), event_id, root (`capture:<id>` o `receipt:<id>`), reference (referencia normalizada), separated (indicador de separación explícita). Clave primaria compuesta `(owner_id, source, event_id)`; índice no único sobre `(owner_id, reference, source)` para permitir preservar eventos incompatibles con misma referencia sin colisión ni fusión forzada; índice sobre `(owner_id, root)`. (Nota histórica: en v36 existió índice único que Room 37 sustituyó por este índice no único y PK compuesta, ahora formalmente aprobado).
- `receipt_candidates`: campo `fields_edited` para registrar campos modificados por el usuario y protegerlos de sobrescritura al correlacionar nuevas fuentes.
- Importes en unidades menores enteras (`Long`). Captura y evidencia son exclusivamente locales en Room sin outbox ni sincronización.

## Privacy & Financial Invariants

El gate usa sesión/owner, permiso vigente, consentimiento y entitlement verificado; un booleano de UI no autoriza. Texto fuente vive en memoria durante la extracción. Candidato no afecta ledger/saldos hasta confirmación, salvo autorregistro estrictamente permitido por FR-004. Crédito siempre requiere acción explícita. Aislamiento por propietario.

## Success Criteria & Demo Evidence

- **SC-001**: Evidencia focalizada para cada escenario; compilar no equivale a ejecutar pruebas ni validar proveedor.
- **SC-002**: Cada candidato produce como máximo un movimiento; incertidumbre y crédito no cambian saldo antes de confirmar.
- **SC-003**: Verificar aislamiento por owner, ausencia de sincronización de candidatos y ausencia de contenido sensible en logs; evidencia se limpia al resolver/expirar.
- **SC-004**: Demo en debug con permiso de listener, consentimiento y Premium/lease: `adb shell cmd notification post -S bigtext -t "Yape" cap-yape "Yapeaste S/ 25.50 a Juan P."`; crédito: `adb shell cmd notification post -S bigtext -t "BCP" cap-bcp "Consumo de S/ 120.00 en PLAZA VEA con tu tarjeta de credito terminada en 1234"`; incertidumbre: `adb shell cmd notification post -S bigtext -t "Yape" cap-sin-monto "Yapeaste a Ana"`. Revocar con `adb shell cmd notification disallow_listener com.kipu.app/com.kipu.app.feature.capture.data.listener.KipuNotificationListenerService`. Comandos de simulación de `impl-T2.md`; repost puede cambiar `postTime`, así que no simula replay idéntico.
- **SC-005**: Correlación híbrida Yape verificada con pruebas unitarias, migración Room 35→36 y 36→37 (HybridYapeMigrationTest, exit 0); validación en hardware comprobó migración 33→37 en A16 y S24, OCR sintético local en A16 y frontera de permisos URI Android en S24. Notificación viva de Yape en ambos órdenes, sharing desde app Yape real y ocho muestras privadas ausentes permanecen como NO VERIFICADO.

## F1 — Correcciones incorporadas tras revisión (2026-10-28)

- Estados fallidos, rechazados, anulados, cancelados, pendientes, revertidos, no procesados/no autorizados/no aprobados/no realizados/no completados o en proceso se clasifican como `IGNORED_UNPARSEABLE`: no producen candidato ni llegan al scoring/autorregistro.
- La selección de una billetera afiliada conserva el ID seleccionado para revalidación, pero asienta en la cuenta bancaria padre con el canal YAPE/PLIN vigente. Se revalidan owner, vínculo, moneda, estado y tipo de cuenta; una billetera independiente conserva su cuenta sin canal afiliado.
- Confirmar/autorregistrar y descartar serializan estado, efecto en ledger y resolución en una transacción Room común. Si resolver falla, se revierte el efecto financiero; el recibo aplicado se consulta antes de similitud para recuperación idempotente.
- El gate revalida identidad de sesión, permiso vivo y consentimiento después de consultas suspendidas y justo antes de lectura diferida/efecto financiero. Las consultas de billetera/canal preceden al gate final.
- Seguimientos (aprendizaje, asociación a esperado, limpieza/publicación de aviso y postprocesamiento) se guardan como bits pendientes en la transacción que resuelve el candidato. Un worker durable los reintenta; repetir una captura resuelta completa seguimientos sin registrar de nuevo.
- Candidato y evento `CANDIDATE_CREATED` se escriben atómicamente tras completar parsing/consultas. Un fallo revierte ambas escrituras; eventos ignorados son terminales y replay recupera trabajo pendiente del candidato existente.
- La asociación con pagos esperados distingue `expected_match_state = PENDING_CHOICE | LINKED | NONE`; elección ambigua o «Ninguno» persiste y es recuperable.

## Evidencia de verificación — 2026-10-28

La suite `:app:testDebugUnitTest` terminó con **1014 pruebas, 0 fallos, 0 errores y 2 omitidas** (exit 0). Se ejecutaron 17 pruebas instrumentadas: `CaptureRoomMigrationTest` 2/2, `CaptureAtomicResolutionTest` 6/6, `ExpectedPaymentAssociationTest` 4/4, `RecurrenceReminderWorkerTest` 3/3 y `PermissionsScreenTest` 2/2. La compilación F1 de targets debug/unit/instrumented terminó correctamente.

| HU / escenario | Resultado E2E | Evidencia y límite |
| --- | --- | --- |
| HU-45 gate, listener, consentimiento y Premium | PASS para activación/revocación con Premium; PASS Free para bloquear nueva extracción | QA inicial probó denegación Free/consentimiento y revocación con dos barreras simultáneas (`qa-free.png`), sin aislar cada una. QA2 activó Premium, listener y consentimiento (`qa2-1.png`, `qa2-2.png`), revocó listener sin nuevo candidato y lo restauró. QA3 volvió a Free: nueva señal no cambió conteos (`qa3-2.png`). Expiración real independiente: SIN EVIDENCIA. |
| HU-46/47 captura, débito y scoring | PASS en escenarios ejecutados | Yape HIGH auto registrado con un movimiento en QA2. Visa débito 5678 produjo HIGH y un movimiento en cuenta vinculada en QA3 (`qa3-1.png`). Captura incierta quedó LOW con aviso (`qa2-3.png`). |
| HU-46/47 repost con nuevo `postTime` | PASS para evitar segundo movimiento; no deduplica candidato | QA3 creó otro candidato `PENDING/MEDIUM` con `POSSIBLE_DUPLICATE` y evento nuevo; quedó un único movimiento. No fue replay con fingerprint idéntico (`qa3-3.png`). Dedupe exacto E2E: SIN EVIDENCIA. |
| HU-47 operación rechazada | PASS | Compra BCP rechazada S/75 generó `IGNORED_UNPARSEABLE`, cero candidatos por ese importe (`impl-QA2.md`; sin captura específica). Unitarias cubren seis proveedores. |
| HU-48 crédito directo/cuotas y cambio a débito | PASS en los casos probados | QA2 confirmó DIRECT y 3 cuotas sin interés; cambiar a cuenta ocultó opciones de crédito y guardó débito/cuenta (`qa2-4.png`, `qa2-5.png`). |
| HU-50 corrección y aprendizaje | PASS | Corrección confirmada de categoría/instrumento; captura posterior mostró valores preseleccionados (`qa2-3.png`, `qa2-6.png`). |
| HU-51 bandeja, descarte y conversión manual | PASS para descarte y conversión | Descarte sin movimiento (`qa2-6.png`); candidato conservado en Free y conversión manual guardada con recibo aplicado y un movimiento (`qa3-3.png`–`qa3-5.png`). Replay del mismo comando: SIN EVIDENCIA. |
| HU-34 esperado y asociación | PASS en asociación única y ambigua | Esperado sin movimiento adicional, asociación única y elección entre dos; uno quedó atendido y otro pendiente, sin segundo movimiento por asociar (`qa2-8.png`, `qa2-9.png`). |
| HU-43 aviso/candidato y presupuesto | PASS | Aviso `CAPTURE_REVIEW`, apertura del candidato y presupuesto excedido sin bloqueo (`qa2-6.png`, `qa2-7.png`). |
| HU-45 Premium vencido / estado remoto tras limpiar override | SIN EVIDENCIA de expiración y refresco remoto | Premium se habilitó temporalmente mediante override QA, sin cambiar Supabase. Al final gate QA cerrado y 0 overrides. QA3 aún mostró `Free de pruebas` y caché Room `FREE/TEST_OVERRIDE` (`qa3-7.png`); la pantalla no demuestra estado remoto nuevo. |
| HU-46 débito en QA2, permiso aislado y HU-47 replay manual | SIN EVIDENCIA en QA2 / replay | QA3 después aportó evidencia de débito. No se aisló permiso frente a Free/consentimiento. No se reejecutó el comando manual para probar replay. |

Capturas: `qa-free.png`, `qa2-1.png`–`qa2-9.png`, `qa3-1.png`–`qa3-7.png`. QA3 no aportó evidencia del replay del mismo comando manual ni de estado remoto nuevo tras retirar override; el registro manual ordinario se abrió, pero no se guardó. El repost con nuevo `postTime` creó candidato `POSSIBLE_DUPLICATE` sin segundo movimiento y no se describe como duplicado exacto.

El override Premium QA fue temporal; el gate quedó cerrado y los overrides quedaron en cero al final. El caché local de QA3 no se refrescó concluyentemente. Esta evidencia local no acredita despliegue ni estado remoto.

## Refinamiento HU-49 aprobado por Owner - 2026-11-03

D1: evidencias locales de notificación y OCR comparten raíz de candidato/movimiento en ambos órdenes cuando coinciden referencias normalizadas. Adjuntar evidencia nunca escribe ledger ni sobrescribe campos existentes/editados. Las raíces confirmadas siguen siendo destinos válidos. Sin referencia en al menos una fuente, importe + moneda + dirección + contraparte normalizada + hora dentro de ±10 minutos solo propone unión cuando hay exactamente una raíz compatible que aún no tenga esa fuente; el usuario elige unir o mantener separado. Referencias distintas, ambigüedad y reembolsos históricos no se fusionan por importe. Evidencias y candidatos permanecen solo en Room.

D2: OCR Yape local sigue disponible en Free con consentimiento y revisión (decisión 2026-10-21); sustituye escenario 7 del backlog. Sin consentimiento no se procesa imagen. Una captura Premium vencida mantiene descarte/conversión manual explícita, sin repetir OCR. Imagen incompleta requiere corrección y MIME no imagen se rechaza antes de abrir bytes.

D3 (2026-11-03 [2026-11-04 UTC]): el Product Owner aprobó explícitamente **«Conservar eventos incompatibles para revision»** ([decisiones del Owner](../sprint-9-owner-decisions.md)). Si dos eventos comparten la misma referencia pero sus datos son incompatibles, se conservan separados para revisión del usuario sin fusión automática ni duplicación de efectos contables en el ledger; reconciliando FR-012 y la entidad `capture_evidence` con Room v37 (PK `owner_id, source, event_id`, índice no único `owner_id, reference, source` e índice `owner_id, root`). Misma referencia solo fusiona eventos compatibles según los invariantes actuales.

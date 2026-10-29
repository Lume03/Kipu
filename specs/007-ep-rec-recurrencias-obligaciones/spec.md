# Feature Specification: EP-REC — Recurrencias y obligaciones (HU-33 / HU-35)

**Feature Branch**: `007-ep-rec-recurrencias-obligaciones`

**Created**: 2026-10-18

**Status**: Refined — cierre/merge de EP-REC aprobado por decisión del Owner del 2026-10-19; no autoriza migraciones hosted ni despliegue.

**Refined**: 2026-10-18 — Registradas las aprobaciones de producto T001 para DP-REC-01..06; en ese momento seguían abiertas las subdecisiones de compatibilidad `is_active` y confirmación de fecha de reanudación, resueltas en la respuesta complementaria documentada abajo.

**Refined**: 2026-10-18 — Incorporadas las respuestas formales posteriores de producto: proyección legacy `is_active`, confirmación explícita de fecha al reanudar y presencia/nullability de `reminder_lead_days`.

**Refined**: 2026-10-18 — T002 definió TECH-REC-HASH-01: canonicalización numérica decimal exacta por valor semántico JSONB y validación integral/rango por campo, con vectores para decimal/fracción y exponentes; requiere revisión T003 antes de writers.

**Refined**: 2026-10-18 — T002 follow-up definió TECH-REC-STRING-01 (bytes exactos UTF-8/escapes), TECH-REC-PAYLOAD-01 (schemas cerrados por comando y valores omit/null) y TECH-REC-CALENDAR-01 (límites operativos y cálculo anclado); pendiente T003 antes de writers.

**Refined**: 2026-10-18 — T002-follow-up precisó el lead time por comando (default 2 solo al crear; UPDATE reenvía el valor vigente salvo cambio), añadió un vector CREATE schema-complete y aclaró que los hashes de fragmentos inválidos son oráculos de canonicalizer, no request hashes aceptables; todo queda pendiente de T003.

**Refined**: 2026-10-18 — TECH-REC-JSON-01 fija la frontera de RPC JSONB: validar solo el objeto semántico post-parseo; los nombres de miembro JSON duplicados ya no son observables ni rechazables por el handler y el cliente debe emitir objetos sin duplicados. Los tests verifican el comportamiento en la interfaz JSONB real, no un rechazo lexical imposible; pendiente de T003.

**Refined**: 2026-10-19 — El Owner difiere BUG-CORE-MIG-001 (corrección de la migración histórica 14→15) y los fallos de suites core al backlog técnico general; acepta la evidencia focalizada de HU-33/HU-35 para cierre de EP-REC y autoriza PR/merge. Esta decisión no cambia retroactivamente los dictámenes independientes de QA ni autoriza cambios hosted.

**Refined**: 2026-10-19 — Security `t_b729959c` bloqueó hash-bound el candidato Compose v2: el runner SQL no puede activarse en el `docker compose up` predeterminado. Se incorpora la ejecución SQL solo mediante perfil no predeterminado e invocación explícita como gate de validación; el flujo `supabase test db --local` se retira como procedimiento utilizable porque no hay target aprobado. SEC-CAND-002 requiere aceptación Security expresa contra hash exacto; SEC-CAND-003 exige evidencia QA runtime tras aprobación humana escrita para el target exacto. No se autoriza provisioning, SQL, tests ni apply.

**Refined**: 2026-10-19 — T025 `t_d3d157a6` preparó Compose v3; T026 `t_9640404b` completó re-review hash-bound con dictamen **BLOCKING / NO-GO**. Archive SHA-256 `0a4658a1e933a84c75ecb6394e1bbfcc71f1b4ee670a5d8bfe26a13e0f7fc6b6` (44,842 bytes); Compose SHA-256 `f95fef8536d75da0b421f6eff17f9e2ae5af49590333757ec89e705590f08bfb`. SEC-CAND-004 se resolvió solo estáticamente; SEC-CAND-001 acredita completitud estática, no ACL efectiva ni provenance Git; SEC-CAND-002 fue **NOT ACCEPTED** y bloquea; SEC-CAND-003 sigue abierto para QA runtime autorizada. El review no habilita operaciones. T030/T031 son el follow-up estático v4 y su re-review; `t_52d47cb0` permanece bloqueado.

**Architectural Review**: T003 (`t_a0fdee9b`) aprobó el contrato/modelo documental el 2026-10-18. La paridad ejecutada Kotlin/PostgreSQL y las suites de implementación siguen pendientes; esta aprobación no habilita migraciones hosted.

**Input**: HU-33 Registro de recurrencias y HU-35 Estados de recurrencias, Sprint S6. Alcance confirmado para esta entrega: solo HU-33 y HU-35; HU-34 queda fuera por corresponder a S8 y depender de HU-46/HU-51.

## Scope & Traceability

- **Incluido**: crear/configurar una regla recurrente y controlar sus estados Activa, Pausada y Cancelada, conforme a HU-33 y HU-35 del Product Backlog Kipu V4.2.
- **Fuera de alcance**: HU-34 (generación de pagos esperados y asociación con capturas), HU-40 (calendario financiero), HU-44 (centro de notificaciones y deduplicación), generación automática de ocurrencias o transacciones, ejecución de pagos externos, fechas de corte y generación de cuotas.
- HU-18 figura como dependencia de HU-33 en el backlog y HU-35 depende de HU-33. En T001 se aceptó formalmente una dependencia parcial de contrato para HU-18 que no bloquea el trabajo HU-33/HU-35; esto no afirma que HU-18 esté íntegramente completada ni amplía el alcance de EP-REC. La relación con HU-44 es de integración, no transfiere a EP-REC la propiedad de la entrega/deduplicación de notificaciones.
- **Fuentes**: `KipuApp/Kipu md/02_Kipu_V4.2_Product_Backlog.md` (HU-33, HU-35 y HU-44, cuyo ejemplo expresa anticipación en días); `KipuApp/Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` (modelo `recurrences`); proceso P19 `19-gestionar-ciclo-de-recurrencias.md` solo para detalles operativos compatibles con esas HU. El backlog gobierna el alcance.

## User Scenarios & Testing

### User Story 1 — Registrar una regla recurrente (HU-33, Priority: P1)

Como usuario de Kipu, quiero marcar un movimiento como pago recurrente y definir su periodicidad, importe esperado y próxima fecha, para planificar una obligación repetida sin confundir la expectativa con un pago ya realizado.

**Why this priority**: HU-33 es la capacidad base y dependencia bloqueante de HU-35; el backlog la asigna a S6 con prioridad Alta.

**Independent Test**: Desde los detalles de un movimiento, configurar y guardar una regla válida; comprobar frecuencia, modalidad de importe y próxima fecha, y confirmar que el movimiento histórico no cambia ni aparece un pago nuevo.

**Acceptance Scenarios**:

1. **Given** un movimiento que la persona desea repetir, **When** activa Pago recurrente en Detalles y guarda una regla válida, **Then** queda registrada con su próxima fecha y configuración.
2. **Given** una obligación cuyo importe puede variar, **When** la persona indica importe aproximado, **Then** Kipu lo identifica como estimado y no como pago real.
3. **Given** una regla mensual o anual cuyo día no existe en el periodo siguiente, **When** Kipu calcula la fecha, **Then** propone una fecha válida de calendario y no duplica el periodo.
4. **Given** que falta la próxima fecha, **When** la persona intenta guardar, **Then** Kipu solicita completarla y no crea una regla incompleta.
5. **Given** que la regla tiene importe fijo, **When** se guarda, **Then** la modalidad fija y el importe esperado quedan diferenciados de cualquier transacción real.

---

### User Story 2 — Controlar el estado de la recurrencia (HU-35, Priority: P2)

Como usuario de Kipu, quiero pausar, reanudar o cancelar una recurrencia, para controlar sus avisos futuros sin perder pagos ni historia anteriores.

**Why this priority**: HU-35 requiere una regla existente (HU-33); el orden P1→P2 representa esta dependencia, no una prioridad de backlog distinta (ambas HU tienen prioridad Alta).

**Independent Test**: Con una regla de prueba ya registrada, pausar, solicitar reanudación, revisar y confirmar explícitamente la primera fecha futura válida, y cancelar mediante confirmación; verificar estado/próxima fecha y que no se hayan añadido, borrado ni reescrito movimientos históricos.

**Acceptance Scenarios**:

1. **Given** una regla Activa, **When** la persona la pausa, **Then** queda Pausada, cesan nuevos avisos de esa regla y se conserva la historia previa.
2. **Given** una regla Pausada con periodos omitidos, **When** la persona solicita reanudar, **Then** Kipu muestra la primera fecha futura válida calculada, conserva el estado Pausado hasta la confirmación y no genera gastos de periodos omitidos.
3. **Given** la fecha futura válida propuesta, **When** la persona la confirma explícitamente, **Then** Kipu persiste la transición a Activa con esa fecha, sin rellenar periodos pasados.
4. **Given** una regla que la persona ya no utiliza, **When** confirma su cancelación, **Then** queda Cancelada, cesan los avisos futuros y la historia permanece consultable.
5. **Given** que se muestra la confirmación de cancelación, **When** la persona regresa sin confirmar, **Then** la regla conserva su estado previo y su configuración.

### Edge Cases

- Una fecha final opcional anterior a la fecha inicial se rechaza; una regla sin fecha final puede continuar según su periodicidad.
- Los intervalos de frecuencia personalizada usan semanas o meses; el contrato técnico limita semanas a [1,5200] y meses a [1,1200]. El `interval_count` de frecuencias integradas es exactamente 1; estos límites son guardrails de implementación, no nuevas cadencias de producto.
- Recalcular una fecha no puede desplazar progresivamente el calendario por el retraso accidental de un temporizador.
- La reanudación no crea registros financieros ni rellena periodos omitidos con pagos ficticios.
- Un cambio de estado repetido no debe revertir ni duplicar historia; los conflictos de revisión deben informarse para recargar el estado vigente.
- Cancelar solo surte efecto al confirmar; salir del diálogo no modifica la regla.
- Una regla Pausada o Cancelada no debe originar nuevos avisos; la integración concreta con la entrega de notificaciones queda en el límite de HU-44.

## Requirements

### Functional Requirements

- **FR-001 (HU-33)**: Kipu MUST permitir iniciar el registro recurrente desde los detalles de un movimiento y conservar ese movimiento como hecho histórico sin volverlo a registrar.
- **FR-002 (HU-33)**: La persona MUST poder definir frecuencia semanal, quincenal, mensual, anual o personalizada, importe fijo o aproximado, próxima fecha y anticipación del aviso en días (default 2; entero requerido no nulo, rango inclusivo 0–30, DP-REC-05). El cliente MUST resolver el default 2 antes de calcular el hash del comando; un comando con el campo omitido o `null` MUST rechazarse. Este requisito configura la preferencia; no asigna a EP-REC la entrega del aviso.
- **FR-003 (HU-33)**: Kipu MUST identificar visualmente el importe aproximado como estimado, y ninguna regla recurrente ni estimación MUST presentarse como pago realizado.
- **FR-004 (HU-33)**: Kipu MUST rechazar la creación si falta la próxima fecha requerida o si la configuración temporal es inválida, sin persistir una regla incompleta. Las fechas del contrato MUST ser fechas gregorianas válidas entre `0001-01-01` y `9999-12-31`.
- **FR-005 (HU-33)**: El cálculo de fechas MUST respetar el calendario para periodos mensuales/anuales, producir fechas válidas al llegar al fin de mes y evitar periodos duplicados. La secuencia MUST derivarse del `start_date` original para evitar deriva después del ajuste de fin de mes.
- **FR-006 (HU-35)**: Una regla MUST distinguir los estados Activa, Pausada y Cancelada; pausar y cancelar conservan el historial.
- **FR-007 (HU-35)**: Pausar o cancelar MUST detener avisos futuros de la regla; la entrega, deduplicación y centro de notificaciones son responsabilidad de HU-44 y quedan fuera de esta implementación.
- **FR-008 (HU-35)**: Al solicitar reanudación de una regla Pausada, Kipu MUST mostrar la primera próxima fecha futura válida calculada conforme a la regla de calendario (DP-REC-02) y MUST mantener la regla Pausada hasta que la persona confirme explícitamente esa fecha. Solo tras esa confirmación se puede persistir `ACTIVE`; no se crean transacciones, gastos ni pagos ficticios para periodos omitidos.
- **FR-009 (HU-35)**: La cancelación MUST requerir confirmación; abandonar el diálogo MUST dejar la regla en su estado previo.
- **FR-010 (HU-33/HU-35)**: La capacidad MUST permanecer local-first y mantener los cambios pendientes seguros ante desconexión y reintentos; conflictos de revisión no pueden sobrescribir silenciosamente un cambio vigente.
- **FR-011 (Constitución)**: La regla recurrente MUST pertenecer a su usuario y las lecturas/escrituras remotas deben estar aisladas por autorización de objeto; una expectativa recurrente MUST NOT alterar saldos, ledger o consumo presupuestario.

### Key Entities

- **Regla recurrente**: obligación repetida propiedad de una persona; contiene la plantilla, periodicidad, intervalo, inicio, fin opcional, próxima fecha, anticipación, estado y revisión.
- **Plantilla recurrente**: datos que describen el movimiento que se espera (concepto, tipo, importe en unidad menor, moneda y referencias aplicables), junto con la modalidad fija/aproximada. No es una transacción ejecutada.
- **Movimiento de origen**: movimiento histórico cuyos datos pueden iniciar la regla; permanece inalterado y no se duplica.
- **Ocurrencia esperada**: entidad del modelo V4.2 que se reserva para HU-34. Su generación, conciliación y asociación con transacciones no forman parte de este alcance.

## Success Criteria

### Measurable Outcomes

- **SC-001**: El 100% de los escenarios de aceptación de HU-33 y HU-35 puede verificarse sin crear una transacción financiera ficticia.
- **SC-002**: Toda regla válida probada conserva una próxima fecha válida; una regla sin próxima fecha o con intervalo inválido no se guarda.
- **SC-003**: La misma regla de calendario no produce dos fechas para el mismo periodo al repetir su cálculo o guardado.
- **SC-004**: Tras pausar/cancelar no se entrega un nuevo aviso de la regla; tras reanudar no aparecen cargos ni pagos de periodos omitidos.
- **SC-005**: En todos los escenarios probados, los movimientos anteriores y su historia sobreviven a pausa, reanudación, cancelación y reintentos.
- **SC-006**: El arranque predeterminado del target SQL local no inicia ni ejecuta suites pgTAP; las suites solo se ejecutan mediante una invocación explícita de un perfil no predeterminado y después de los gates de Security, aprobación humana del target exacto y QA independiente de aislamiento.

## Assumptions

- El flujo S6 cubre solo HU-33/HU-35; el backlog V4.2 y la decisión explícita de alcance prevalecen sobre cualquier artefacto legado o sugerencia del brief inicial.
- La regla recurrente planifica una obligación; Kipu no ejecuta pagos externos ni confirma por sí sola que el dinero se movió.
- P19 desarrolla la frecuencia personalizada como un intervalo positivo de semanas o meses y especifica el ajuste al último día válido del mes. TECH-REC-CALENDAR-01 fija límites operativos conservadores y cálculo desde el ancla original sin deriva; cualquier cambio de esos límites/criterios requiere revisión arquitectónica.
- La anticipación se expresa en días, con default 2 y rango inclusivo 0–30 por DP-REC-05; EP-REC solo conserva la configuración y estado requeridos por la frontera de HU-44.
- HU-44 es responsable de entregar y deduplicar notificaciones. EP-REC almacena configuración/estado y expone la condición necesaria para que avisos futuros se supriman tras pausa o cancelación.
- La tabla `recurrence_occurrences` existente en el baseline local no implica que la generación de esperados esté habilitada ni autoriza implementarla en HU-33/HU-35.
- Fechas de corte, cuotas, pagos esperados, captura/matching, calendario financiero y cualquier operación bancaria están fuera de alcance.

## Decisions Recorded and Remaining Gates

### Owner Decision — cierre de alcance EP-REC (2026-10-19)

- **Veredicto de cierre**: el Owner aprueba el cierre de EP-REC y autoriza PR/merge con la evidencia acotada de HU-33/HU-35 registrada en `verification-report.md`. Es una aceptación ejecutiva del alcance/riesgo, no un dictamen APPROVED emitido por QA.
- **Fuera de alcance/diferido**: BUG-CORE-MIG-001 (migración histórica 14→15) y los fallos de suites core quedan en el backlog técnico general y no bloquean esta rama. No incluir esos cambios de triage en los commits EP-REC.
- **Límite de evidencia**: los runs anteriores de suite conectada completa y sus fallos permanecen como resultados históricos; esta decisión no los convierte en PASS ni demuestra que fueran preexistentes. Se acepta para EP-REC la evidencia focalizada de pgTAP local, migración Room v23 y flujos UI indicados en el informe.
- **No autorizado**: migraciones hosted, `db push` y despliegue.

Las respuestas aprobadas por el responsable de producto/épica están registradas en el hilo de Kanban T001 (`t_00451d7d`) y las precisiones complementarias en el comentario #83 de T003 (`t_a0fdee9b`). Solo los alcances expresos de esas respuestas se consideran decididos:

- **DP-REC-01 — Estado**: aprobado: usar los estados de negocio `ACTIVE`, `PAUSED` y `CANCELLED`, independientes de `deleted_at`. `is_active` se conserva como proyección de solo lectura: `ACTIVE=true`; `PAUSED`/`CANCELLED=false`. Retenerla hasta que un inventario autorizado confirme que no quedan lectores legacy; retirarla solo en una migración posterior con revisión separada. El inventario no se ha realizado y no se infieren lectores/escritores legacy.
- **DP-REC-02 — Reanudación**: aprobado: mostrar la primera fecha futura válida calculada y exigir confirmación explícita de la persona antes de persistir `ACTIVE`; no generar gastos por periodos omitidos.
- **DP-REC-03 — Fecha final**: aprobado: `end_date` es opcional en S6 y, si se define, debe cumplir `end_date >= start_date`.
- **DP-REC-04 — Cancelación**: aprobado: `CANCELLED` es terminal; reutilizar la configuración requiere clonar/crear una nueva recurrencia.
- **DP-REC-05 — Anticipación**: aprobado: entero no nulo requerido en CREATE y UPDATE; el cliente resuelve el default de 2 días al crear y reenvía el valor configurado actual en UPDATE salvo cambio explícito; omisión o `null` se rechazan; rango inclusivo de 0–30. La entrega/deduplicación sigue perteneciendo a HU-44 y el significado de cero días permanece en la frontera EP-NOT.
- **DP-REC-06 — Dependencia HU-18**: aprobado aceptar una dependencia parcial de contrato; no bloquea la implementación HU-33/HU-35. Esto no equivale a certificar HU-18 completa ni autoriza ampliar el alcance de esta épica.

**Gates todavía vigentes**: T003 aprobó el contrato final (TECH-REC-HASH-01, TECH-REC-STRING-01, TECH-REC-PAYLOAD-01, TECH-REC-CALENDAR-01 y TECH-REC-JSON-01) en revisión read-only. Siguen pendientes la paridad ejecutada Kotlin/PostgreSQL y las suites de implementación. DB-REC-01 bloquea cualquier aplicación hosted hasta un inventario autorizado; dicho inventario no se ha realizado y no se ha consultado ni autorizado el esquema remoto. La interpretación de `reminder_lead_days=0` sigue siendo responsabilidad de la frontera EP-NOT/HU-44, no una decisión de entrega de EP-REC.

### SEC-CAND-001..004 — target de tests SQL local

El dictamen Security hash-bound `t_b729959c` sobre el candidato v2 es **BLOCKING**, no una habilitación operativa. Archive revisado: SHA-256 `19185b90b0297e7a25b185cafbb5eedd54f3b8c235fa1160c85e088a078ccd1e`; `bundle/compose.candidate.yaml`: `7e4ccf5ca93f1ef9053ffb2bc3d0ca85d74422dc65c3c8b4a7b802b01150bea1`. SEC-CAND-001 se resolvió solo como completitud estática del paquete; SEC-CAND-002 continúa abierto hasta aceptación Security explícita del residual de credenciales contra un hash exacto; SEC-CAND-003 requiere QA independiente con evidencia runtime en Windows/Docker Desktop; SEC-CAND-004 bloquea v2 porque el servicio `test-runner` sin perfil ejecutaría las tres suites SQL al usar `docker compose up` ordinario.

T025 preparó el paquete v3 y la inspección estática confirma que `test-runner` usa solo el perfil opt-in `ep-rec-tests` con `restart: no`; no hubo validación de parser/runtime. T026 revisó hash-bound y dictaminó **BLOCKING / NO-GO**: SEC-CAND-002 **NOT ACCEPTED**, SEC-CAND-003 requiere QA runtime independiente y SEC-CAND-001 no prueba ACL/provenance efectivas. T030 `t_00bff9a1` entregó v4 estático adjunto (archive SHA-256 `bf6bc260f18129ac9014660f652a7403e63b61b7825256031879537fd4650a34`, 51,025 bytes; Compose `f95fef8536d75da0b421f6eff17f9e2ae5af49590333757ec89e705590f08bfb`), con 36/36 checksums internas verificadas. Security `t_56ebf6c1` reportó HTTP 401 en los 12 GET anónimos de manifests; los 12 digests siguen sin mapeo verificable a source/version ni soporte `_FILE` comprobado. El re-review final T031 `t_c2f897bb` concluyó **BLOCKING / NO-GO** contra ambos hashes: SEC-CAND-002 **NOT ACCEPTED**; además, ACL/mount/provenance efectivos y aislamiento runtime siguen sin verificar. Se requiere decisión humana escrita del owner/Security sobre rechazar el modelo, aceptar expresamente el residual contra esos hashes o autorizar remediación/verificación separada; no se inferirá aceptación ni se autoriza runtime. La invocación `docker compose --profile ep-rec-tests run --rm test-runner` es solo referencia futura/no autorizada. `pnpm exec supabase test db --local` no es un target aprobado. No Docker/Compose, provisioning, SQL, pgTAP, migraciones, apply ni QA runtime hasta superar los gates explícitos.

### TECH-REC-HASH-01 — canonicalización numérica semántica

Decisión técnica de T002 aprobada en la revisión arquitectónica T003 (`t_a0fdee9b`): dado que la RPC recibe `jsonb`, el hash se calcula sobre el valor numérico semántico y no sobre la grafía JSON original. Para cada número JSON, Kotlin y PostgreSQL deben producir decimal exacto en notación base 10 sin exponente, quitar ceros fraccionarios finales y el punto decimal sobrante, y emitir cualquier cero como `0` (incluido cero negativo). No se usa `Float`/`Double` ni se rechaza lexicalmente una forma decimal o exponencial después del parseo. Por tanto `100`, `100.00` y `1e2` canonicalizan a `100`; `1e-1` canonicaliza a `0.1`. La paridad runtime aún requiere pruebas de ambas implementaciones.

Antes de aceptar o verificar el hash, validar en cada path numérico el valor exacto, su integridad matemática y su rango: `contract_version` debe ser exactamente 1; `expected_revision` y `revision` deben estar en [1, 9223372036854775807]; `payload.reminder_lead_days` en [0, 30]; `payload.interval_count` en [1, 9223372036854775807]; y `payload.template_transaction.amount_minor` debe ser entero en [1, 9223372036854775807], además de satisfacer la validación existente de la plantilla/moneda. Rechazar fracciones como revisión/intervalo/importe/lead time, valores fuera de rango, tipos distintos de número y cualquier path numérico no permitido por el schema. Una forma decimal/exponencial cuyo valor exacto sea entero y esté en rango es equivalente a su entero. Los vectores negativos pueden incluir bytes/digest de canonicalizador como oráculo puro, pero no constituyen un hash de comando aceptable ni autorizan escritura.

### TECH-REC-JSON-01 — validación en la frontera JSONB

La interfaz RPC de EP-REC recibe el comando como `jsonb`; por ello el handler valida el objeto semántico que queda después del parseo de PostgreSQL, no el texto JSON original. `jsonb` no conserva nombres de miembro repetidos y, cuando el texto de entrada contiene duplicados, mantiene solo el último valor para cada clave. El servidor no puede ni promete rechazar lexicalmente claves JSON duplicadas. Aplica sobre el valor resultante la allowlist cerrada por comando, required/omit/null, tipos, reglas de negocio y canonicalización/hash; el hash se calcula sobre ese valor JSONB ya parseado, nunca sobre bytes JSON originales.

Los clientes MUST construir y emitir objetos JSON sin nombres de miembro duplicados; Android debe usar el DTO/estructura tipada del comando y no concatenar JSON manualmente. Un caller que envía JSON crudo con duplicados al límite JSONB queda sujeto al objeto semántico resultante según la regla anterior. Los tests SQL deben pasar el texto duplicado por la conversión `::jsonb` usada por la interfaz y comprobar el valor superviviente/validado y el hash semántico; no deben exigir rechazo lexical del duplicado. Los tests Kotlin deben comprobar que la serialización del comando produce miembros únicos. Esto no reduce la validación de unknown keys ni el orden obligatorio de validación antes de aceptar hash, consultar recibo o mutar estado.

### TECH-REC-STRING-01 — serialización canónica de strings

La función de canonización recibe valores Unicode ya interpretados por JSONB y emite un único literal JSON por valor: comillas dobles delimitan; `"` y `\` se escapan; `/` queda literal; U+0008/U+0009/U+000A/U+000C/U+000D usan `\b`, `\t`, `\n`, `\f`, `\r`; los demás U+0001..U+001F usan `\u00xx` con hexadecimal minúsculo. Los demás escalares Unicode se emiten sin escape como UTF-8, incluidos caracteres no ASCII, U+2028 y U+2029. No se agrega BOM, no se normaliza ni cambia mayúsculas/minúsculas. Se rechazan U+0000 y secuencias surrogate inválidas; JSONB no puede representar U+0000. Grafías de entrada JSON equivalentes (literal/escape Unicode o slash escapado/no escapado) deben producir los mismos bytes; NFC y NFD distintos conservan bytes distintos. Ver vectores normativos de §3 del command contract.

### TECH-REC-PAYLOAD-01 — schemas cerrados por comando

`recurrence-command-contract.md` define allowlists, presencia, tipo, nullability y reglas de referencias para CREATE, UPDATE y SET_STATUS. Las claves requeridas que son nullable se envían explícitamente como `null`; UPDATE omite solo los campos patch que no cambia, salvo `reminder_lead_days`, requerido/no nulo en todos los UPDATE. No hay defaults server-side: el cliente materializa `interval_count=1` para frecuencias integradas y `reminder_lead_days=2` al crear; el UPDATE reenvía el valor vigente antes del hash. Validar estructura, referencias, campos y valores antes de aceptar/verificar digest, consultar recibos o mutar estado. T003 aprobó este contrato documental; implementación y validación pre-hash siguen pendientes.

### TECH-REC-CALENDAR-01 — límites y ancla de calendario

Usar fechas gregorianas estrictas `YYYY-MM-DD` en el rango común `0001-01-01`..`9999-12-31`; rechazar valores inválidos, desbordamiento o fechas fuera del rango. Las frecuencias WEEKLY, BIWEEKLY, MONTHLY y ANNUALLY llevan `interval_count=1`; CUSTOM requiere `custom_interval_unit` `WEEK` o `MONTH` e intervalo entero 1..5200 semanas o 1..1200 meses. Para mensual/anual y CUSTOM/MONTH, cada ocurrencia se calcula desde `start_date` original y su día/mes ancla, recortando al último día válido sin deriva; semanal y CUSTOM/WEEK se calculan sumando semanas completas al ancla; BIWEEKLY suma 14 días. `next_due_date` debe pertenecer a esa secuencia, no anteceder `start_date` ni exceder `end_date` cuando esté definido. Cálculo fuera de rango se rechaza, nunca se trunca/satura. Los topes son guardrails técnicos acotados a 100 años por unidad personalizada, no expansión funcional.

## Incremento Sprint 8: HU-34 — Pagos esperados y coincidencia con captura

**Refined**: 2026-10-28. HU-34 se incorpora como incremento S8; la especificación aprobada de HU-33 se conserva intacta. Fuente: `docs/producto/backlog.md` HU-34 y decisiones del Owner registradas en el diseño técnico Sprint 8.

**Alcance**: desde recurrencias vigentes generar pagos esperados locales, sin crear transacciones financieras ni rellenar periodos pasados; asociar movimientos capturados ya confirmados. La tabla local `recurrence_expected_payments` usa unicidad (`owner_id`, `recurrence_id`, `due_date`) y estados `EXPECTED`, `ATTENDED`, `SKIPPED`; «atendida» significa `ATTENDED`. Vincular un candidato confirmado reutiliza el movimiento, no registra gasto. Una elección entre varias coincidencias debe persistir hasta que la persona elija una opción o «Ninguno». Free conserva su recordatorio ordinario de recurrencia de HU-44.

### Escenarios de aceptación (HU-34)

1. Dado que vence una recurrencia, cuando se genera el esperado, aparece el recordatorio sin cambiar banco, ledger o saldo.
2. Dado que un candidato confirmado coincide con una obligación, cuando se vincula, se marca atendida sin duplicar gasto.
3. Si varias obligaciones coinciden, la elección queda persistida y se recupera después de salir/reabrir; elegir «Ninguno» la resuelve sin otro movimiento.

### Requisitos funcionales

- **FR-012 (HU-34, escenario 1)**: Para reglas `ACTIVE`, generar de manera idempotente un esperado por recurrencia/fecha, sin periodos perdidos y sin efecto en ledger, saldo o presupuesto.
- **FR-013 (HU-34, escenario 2)**: Una coincidencia única por importe, moneda y ventana de fecha MUST vincular el movimiento existente, marcar `ATTENDED` y no crear otro movimiento.
- **FR-014 (HU-34, escenario 3)**: Coincidencias múltiples MUST conservar una elección pendiente; actualizar expectativa y candidato de modo atómico. El mismo vínculo repetido es idempotente.
- **FR-015 (HU-34/Free)**: Free conserva recordatorios ordinarios; el estado esperado no suprime recordatorios antes de ser atendido.

### Evidencia pendiente en dispositivo

`impl-T6b.md` reporta generación, sincronización de importes esperados, elección persistente y vínculo atómico, con compilación exitosa; no ejecutó pruebas ni ADB. Pendiente validar recordatorio Free, ausencia de periodos anteriores, sin segundo movimiento, recuperación de elección tras cerrar/reabrir y comportamiento de recordatorios para `ATTENDED`.


### Escenarios de aceptación (HU-34)

1. Dado que vence una recurrencia, cuando se genera el esperado, aparece el recordatorio sin cambiar banco, ledger o saldo.
2. Dado que un candidato confirmado coincide con una obligación, cuando se vincula, se marca atendida sin duplicar gasto.
3. Si varias obligaciones coinciden, la elección queda persistida y se recupera después de salir/reabrir; elegir «Ninguno» la resuelve sin otro movimiento.

### Requisitos funcionales

- **FR-012 (HU-34, escenario 1)**: Para reglas `ACTIVE`, generar de manera idempotente un esperado por recurrencia/fecha, sin periodos perdidos y sin efecto en ledger, saldo o presupuesto.
- **FR-013 (HU-34, escenario 2)**: Una coincidencia única por importe, moneda y ventana de fecha MUST vincular el movimiento existente, marcar `ATTENDED` y no crear otro movimiento.
- **FR-014 (HU-34, escenario 3)**: Coincidencias múltiples MUST conservar una elección pendiente; actualizar expectativa y candidato de modo atómico. El mismo vínculo repetido es idempotente.
- **FR-015 (HU-34/Free)**: Free conserva recordatorios ordinarios; el estado esperado no suprime recordatorios antes de ser atendido.

### Evidencia pendiente en dispositivo

`impl-T6b.md` reporta generación, sincronización de importes esperados, elección persistente y vínculo atómico, con compilación exitosa; no ejecutó pruebas ni ADB. Pendiente validar recordatorio Free, ausencia de periodos anteriores, sin segundo movimiento, recuperación de elección tras cerrar/reabrir y comportamiento de recordatorios para `ATTENDED`.

## Evidencia de verificación Sprint 8 — HU-34 (2026-10-28)

La instrumentación `ExpectedPaymentAssociationTest` pasó 4/4 y `RecurrenceReminderWorkerTest` pasó 3/3. La suite unitaria total pasó con 1014 pruebas, 0 fallos, 0 errores y 2 omitidas. En E2E con Premium QA temporal, una recurrencia mensual S/40 creó un esperado `EXPECTED` sin movimiento adicional; una captura confirmada vinculó el movimiento existente y dejó el esperado `ATTENDED/LINKED`. Para dos esperados coincidentes se presentó una elección persistente; al elegir uno quedó `ATTENDED` y el otro `EXPECTED`, sin crear otro movimiento. Evidencia: `qa2-8.png`, `qa2-9.png`.

| Escenario | Resultado | Límite |
| --- | --- | --- |
| Generación de esperado sin efecto financiero | PASS | Demostrado en dispositivo para recurrencia mensual S/40. |
| Asociación única con movimiento existente | PASS | Se reutilizó el movimiento; no hubo otro registro. |
| Asociación ambigua y elección persistente | PASS | Dos opciones sobrevivieron navegación/reapertura; se eligió una sin segundo movimiento. |
| Recordatorio Free, periodos omitidos y casos `ATTENDED` adicionales | SIN EVIDENCIA E2E | Las pruebas instrumentadas del worker/asociación no sustituyen estas comprobaciones físicas específicas. |

La asociación usa `expected_match_state` = `PENDING_CHOICE`, `LINKED` o `NONE`. Los seguimientos pendientes se conservan y son reintentables por worker; asociar un esperado no crea ni modifica un movimiento. Premium QA fue temporal: gate cerrado y 0 overrides al final. Esta prueba local no demuestra despliegue remoto.
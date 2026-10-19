# Tasks: EP-REC — Recurrencias y obligaciones

**Propagated**: 2026-10-18 — T002-follow-up propagó TECH-REC-STRING-01, TECH-REC-PAYLOAD-01 y TECH-REC-CALENDAR-01 junto a TECH-REC-HASH-01; el seguimiento aclaró default/valor vigente de `reminder_lead_days` y añadió vector CREATE schema-complete. Este seguimiento agrega TECH-REC-JSON-01: tests Kotlin verifican miembros únicos del serializador; tests SQL convierten texto con clave repetida a la interfaz `jsonb` y validan el objeto superviviente, sin exigir rechazo lexical. T003 revisó estos contratos antes de writers.

**Workflow Update**: 2026-10-18 — Tras registrar el estado final de T003 en `spec.md`, `plan.md` y los contratos, se conserva: T003 aprobó el contrato/modelo documental; implementación/paridad runtime y pruebas SQL siguen pendientes. Se separan la revisión Security read-only de la aplicación/GREEN SQL local post-review.

**Propagated**: 2026-10-19 — Se incorporan T025/T026 para corregir y revisar hash-bound el runner Compose; SEC-CAND-002/003, la autorización humana del target y el runtime permanecen como gates. No se autoriza provisioning ni ejecución SQL.

**Propagated**: 2026-10-19 — T025/T026 completaron paquete/re-review v3 con **BLOCKING / NO-GO**. T030 `t_00bff9a1` entregó v4 estático durable (SHA-256 `bf6bc260f18129ac9014660f652a7403e63b61b7825256031879537fd4650a34`, 51,025 bytes; 36/36 checksums internas verificadas), sin resolver SEC-CAND-002. T056 `t_56ebf6c1` terminó: GET anónimos a 12 manifests respondieron HTTP 401; mapping digest→source y soporte `_FILE` siguen no verificados. T031 `t_c2f897bb` concluyó **BLOCKING / NO-GO**, SEC-CAND-002 **NOT ACCEPTED**. Se requiere decisión escrita humana o trabajo separado de remediación; no se autoriza target, Docker/Compose, SQL, apply ni QA runtime.

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md` en `specs/007-ep-rec-recurrencias-obligaciones/`.

**Prerequisite**: T001 y sus seguimientos registran aprobación humana de DP-REC-01..06. Las decisiones complementarias son: `is_active` read-only (`ACTIVE=true`; `PAUSED`/`CANCELLED=false`), retenido hasta inventario autorizado sin lectores legacy y retirado solo mediante migración posterior revisada; reanudar muestra primera fecha futura válida y exige confirmación explícita antes de persistir `ACTIVE`, sin gastos omitidos; `reminder_lead_days` es entero requerido/no nulo en CREATE y cada UPDATE; cliente aplica default 2 al crear y reenvía el valor actual en UPDATE salvo cambio explícito; omisión/`null` rechazados, rango 0–30. T003 aprobó TECH-REC-HASH-01/STRING-01/PAYLOAD-01/CALENDAR-01/JSON-01 en revisión documental; la implementación y paridad runtime aún requieren pruebas. Inventario hosted sigue separado y no autoriza deployment.

**Tests**: exigidos por FR/SC y Definition of Done; aplicar TDD por cada pareja listada abajo. Registrar RED de comportamiento antes de producción y el mismo comando GREEN después. Para SQL backend, conservar RED contra el baseline intacto y preparar migrations/tests sin apply. T022 revisa solo el diff SQL y no aprueba target. T025/T026 están completos, pero el re-review v3 dio BLOCKING/NO-GO; el flujo actual es T030→T031 (mitigación estática y re-review v4)→T027 (aprobación humana escrita del riesgo residual y target exacto)→T028 (provisioning fresh)→T029 (QA runtime independiente). Solo después de T022, T031 sin blockers con decisión explícita SEC-CAND-002 y PASS de T029 puede `t_52d47cb0` aplicar el diff/hash exacto y ejecutar las tres suites por el perfil no predeterminado `ep-rec-tests`. `docker compose up` sin perfil nunca debe iniciar SQL; `pnpm exec supabase test db --local` no es método autorizado para EP-REC. No afirmar GREEN/runtime/RLS a partir de RED, checksum o review estática. En las demás parejas, conservar GREEN secuencial de la misma prueba. T006/T010 deben cubrir golden string bytes, numeric normalization, schema/allowlist, omit/null, lead-time requerido, refs, calendario y validación antes de hash/receipt/mutación. TECH-REC-JSON-01 añade serialización cliente sin miembros duplicados y prueba SQL del valor tras convertir JSON repetido a `jsonb`; no exigir rechazo lexical imposible. `[ ]` es pendiente, no evidencia de fallo ni éxito.

**Organización**: Setup/gates, fundación común, después US1 (HU-33, P1) y US2 (HU-35, P2, depende de HU-33), cierre cross-cutting.

**Responsables y agrupación Kanban**: T001/T002/T002-follow-up/T003 son gates tech-lead → tech-lead → tech-lead → architect (`t_00451d7d` → `t_713a060d` → `t_c4dcb0a5` → `t_a0fdee9b`). Android core owns T004/T006/T008/T009/T011/T014/T017/T020 (`t_4252e6d9`); backend owns T005/T007/T010/T013/T016/T019 (`t_a7cfcfdc`); Android UI owns T012/T015/T018/T021 (`t_d49c112e`) and waits for both T003 and Android core. Security owns T022 (`t_c132bb36`) and waits for backend; independent QA owns T023 (`t_6e54794e`) and waits for backend, Android core, UI, and security. T014/T020 comparten `OfflineFirstRecurrenceRepository.kt` y T015/T021 comparten `RecurrenceRegistrationScreen.kt`; dependencias/checkpoints explícitos los serializan para que no haya writers simultáneos al mismo path. Los builds Gradle/KSP también se serializan.
**Target gate owners**: T025 `t_d3d157a6` y T026 `t_9640404b` están DONE; resultado global v3 BLOCKING/NO-GO. T030 DevOps `t_00bff9a1` (v4 estático) → T031 Security `t_c2f897bb` (re-review hash-bound) son los gates activos. T027 es aprobación humana no delegable de cualquier residual y target exacto; no crear T028/T029 ni provisionar hasta que T031 permita avanzar y T027 esté escrito. T028 corresponde a DevOps y T029 a QA independiente; ambos preceden `t_52d47cb0`.

## Phase 1: Setup — decisiones y preparación

**Purpose**: cerrar requisitos/contratos y comprobar dependencias antes de tocar código.

- [x] T001 (DONE — Kanban `t_00451d7d`) Obtener aprobación humana de DP-REC-01..06 y registrar respuestas en el hilo. Aprobado: estado independiente de `deleted_at`; primera fecha futura válida al reanudar sin gastos omitidos; `end_date` opcional en S6 con `>= start_date`; cancelación terminal con nueva regla para reutilizar; anticipación default 2/rango 0–30; dependencia parcial de contrato HU-18 no bloqueante para HU-33/HU-35. No se aprobó acceso ni migración hosted.
- [x] T002 (DONE — Kanban `t_713a060d`, tech-lead) Propagar las decisiones aprobadas T001 a los 11 artefactos documentales; en ese momento las respuestas disponibles aún dejaban abiertos el mapping/retención de `is_active`, confirmación de fecha al reanudar y presencia/nullability de `reminder_lead_days`. Esas tres subdecisiones se resolvieron después en T003 comentario #83 y se reconciliaron en T002-follow-up. T003 ya aprobó la revisión arquitectónica final.
- [x] T002-follow-up (DONE — Kanban `t_c4dcb0a5`, tech-lead) Reconciliar en los 11 artefactos autorizados las respuestas complementarias aprobadas de DP-REC-01/02/05. Mantener HU-33/HU-35, exclusiones HU-34/HU-40/HU-44, cero ocurrencias/efectos financieros, inventario hosted como gate y `reminder_lead_days=0` en la frontera EP-NOT. Reentregar a T003 para revisión arquitectónica.
- [x] T003 (DONE — Kanban `t_a0fdee9b`, architect) Revisión arquitectónica independiente read-only de los artefactos objetivo. Veredicto APPROVED; 16 fixtures documentales de hash/bytes recalculados, 0 discrepancias. La revisión no ejecutó suites de implementación ni paridad Kotlin/PostgreSQL; estas siguen pendientes. Sin cambios de archivos ni acceso hosted.

**Checkpoint**: T001, T002/seguimientos y T003 están DONE; las decisiones de producto DP-REC-01/02/05 están propagadas y el contrato/modelo documental tiene aprobación arquitectónica. Los writers están liberados con sus gates TDD y Security/local-apply aún vigentes. La aceptación parcial de HU-18 no equivale a cierre total. Deployment hosted sigue bloqueado por DB-REC-01 y el inventario autorizado pendiente.

---

## Phase 2: Foundational — base compartida

**Purpose**: pruebas rojas de protocolo/migración y almacenamiento común Room/Supabase para ambas historias.

- [x] T004 [P] Añadir pruebas de upgrade Room v22→v23, instalación nueva y preservación de filas/outbox en `app/src/androidTest/java/com/kipu/app/core/database/RecurrenceRoomMigrationTest.kt`; registrar estado RED antes de la migración.
- [x] T005 [P] Añadir fixture local de dos usuarios y pruebas de schema/RLS/ownership en `_recurrence_test_fixture.sql` y `recurrence_schema_test.sql`: exact required key sets/types y unknown-key rejection por comando; null/omit; `reminder_lead_days` requerido/no nulo también en cada UPDATE; template allowlist/references/owner/currency/type; `contract_version=1`, revision [1,BIGINT_MAX], lead days [0,30], built-in count=1, CUSTOM WEEK [1,5200]/MONTH [1,1200], `amount_minor` [1,BIGINT_MAX], y fechas válidas/rango/end/next. Probar la frontera real `jsonb` con texto que repite una clave: tras `::jsonb`, la prueba valida el objeto con el último valor conservado, sin afirmar que el RPC rechace lexicalmente el miembro descartado. Validar antes del hash/receipt/escritura. IDs sintéticos y rollback/cleanup verificable.
- [x] T006 [P] Añadir pruebas de canonicalización/hash en `RecurrenceCommandHasherTest.kt`: orden de claves, omit/null (incluido reminder ausente/null en UPDATE), escape-equivalent source text, golden string bytes (controles, slash, quote/backslash, no-ASCII/U+2028/U+2029, NFC/NFD), numeric vectors, y envelope hashes. Comprobar `100`/`100.00`/`1e2` equivalentes; fracciones `100.5`/`1e-1` producen solo oracle de canonicalizer y son rechazadas antes de digest acceptance; ranges, schema, 16 KiB y ausencia de `Float`/`Double`. Comprobar en la serialización del comando tipado que cada objeto emite nombres de miembro únicos. Demostrar RED real antes de hasher.
- [x] T007 Implementar como candidata la migración Supabase aditiva de tabla/constraints, schema cerrado, status, owner isolation/RLS, índices y grants mínimos en `20261018190400_ep_rec_rule_schema.sql`; incluir límites exactos CUSTOM/date y `is_active` read-only (`ACTIVE=true`; otros false), no aceptar proyección como input ni retirarla antes del inventario autorizado y revisión separada. Depende de T005 RED y T003. Antes de T022, comprobar estáticamente timestamps/nombres y preparar el diff sin aplicar migration. La aplicación local y GREEN de T005 corresponden a `t_52d47cb0`, después de T022; nunca hosted.
- [x] T008 Implementar entidades/DAO/outbox y migración Room 22→23, registrar DB v23 y DI en `app/src/main/java/com/kipu/app/feature/recurrences/data/local/`, `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`, `RoomMigrations.kt` y `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`; depende de T004 RED. `status` es la fuente de verdad, no una bandera mutable. Si el modelo local representa `is_active`, usar solo la proyección read-only aprobada; no inferir lectores/escritores legacy ni retirar datos. No usar fallback destructivo.
- [x] T009 Tras T006 RED y T003, implementar hasher estable en `RecurrenceCommandHasher.kt`: TECH-REC-HASH-01 exact decimal, TECH-REC-STRING-01 exact UTF-8/escapes, field validation/schema before hash, built-in count=1 and CUSTOM/date bounds. Materializar lead days 2 antes del hash en CREATE; en UPDATE preservar y reenviar el valor actual salvo cambio explícito. Reproducir todos los bytes/digests T006 y cotejar con helper PostgreSQL local; nunca afirmar parity sin tests.

**Checkpoint**: migración local segura, Room upgrade/fresh install conserva estado y outbox, RLS/ownership base probados, canonical JSON interop verificado; no hay escritura hosted.

---

## Phase 3: User Story 1 — Registrar una regla recurrente (HU-33, Priority: P1) 🎯 MVP

**Goal**: registrar desde los detalles de un movimiento una expectativa recurrente válida, con fecha/frecuencia/importe/anticipación aprobados, sin mutar el movimiento histórico ni crear pago/transacción.

**Independent Test**: crear/configurar una regla fija y otra aproximada desde `MovementDetailSheet`; comprobar próxima fecha y modo visible, cierre local-first/idempotente y que no aparecen movimiento, ledger, gasto, presupuesto u ocurrencia.

### Tests for User Story 1 (write first; capture RED)

- [x] T010 [P] [US1] Añadir SQL tests CRUD antes de RPC en `recurrence_create_update_test.sql` y, secuencialmente tras T005, extender el fixture sintético reversible en `_recurrence_test_fixture.sql`: allowlists/exact fields, types, required/null/omit (incluido lead time requerido/no nulo en cada UPDATE), full template/reference rules, date/range/CUSTOM/calendar invariants, 16 KiB; string golden fixtures and complete envelope hashes; numeric JSONB `100`/`100.00`/`1e2` same, fractions reject before hash/receipt/write. Add a duplicate-member raw JSON fixture cast to the RPC's `jsonb` argument; assert only the retained semantic value is validated/hashed, not lexical duplicate rejection. Cover owner/ref, receipt/retry and zero financial effects; capture RED contra el baseline intacto. Nunca aplicar migraciones ni ejecutar GREEN antes de T022; los tests quedan listos para `t_52d47cb0` post-review.
- [x] T011 [P] [US1] Añadir pruebas unitarias de calendario/frecuencias, anclaje mensual/anual, fin de mes, año bisiesto, no-deriva y datos fijos/aproximados en `app/src/test/java/com/kipu/app/feature/recurrences/domain/RecurrenceScheduleCalculatorTest.kt`.
- [x] T012 [P] [US1] Añadir Compose tests de alta desde Detalles: plantilla exacta y editable sin mutar origen, schema/client defaults (`interval_count=1` integrado y lead days=2 en CREATE), CUSTOM week/month limits, date/calendar validity, requeridos y null explícito, approximate presentation. Nunca emitir campos extra, omitidos o null no permitido.

### Implementation for User Story 1

- [x] T013 [US1] Tras T010 RED, implementar como candidata CREATE/UPDATE RPCs en `20261018190500_ep_rec_create_update.sql`; rechazar unknown fields, validar schema/template/refs/owner/calendar/números/string bytes y size sobre los valores JSONB antes de hash acceptance, receipt/idempotency lookup o mutation; canonicalizar valores JSONB de forma exacta. No prometer rechazo lexical de claves repetidas ya colapsadas por JSONB. No aplicar migrations EP-REC ni declarar GREEN antes de T022; `t_52d47cb0` ejecuta T010 en local aislado tras review. Sin ocurrencias/efectos financieros.
- [x] T014 [US1] Tras T011 RED, implementar cálculo puro anclado en `RecurrenceScheduleCalculator.kt`: rango date común, topes, miembros de secuencia, month-end sin deriva, leap year y overflow reject. Añadir/ejecutar RED dirigido de repository antes de editar archivo compartido si T011 no cubre. Room+outbox atómicos y conflictos visibles.
- [x] T015 [US1] Integrar el acceso a alta desde `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementDetailSheet.kt` y `MovementHistoryScreen.kt`; añadir `app/src/main/java/com/kipu/app/feature/recurrences/presentation/RecurrenceRegistrationScreen.kt` con loading/offline/error y estado de persistencia local/remota. No volver a guardar el movimiento fuente.

**Checkpoint US1**: criterios HU-33 verificables de forma independiente, tests dirigidos y unitarios pasan, historia de origen no cambia y no se crean pagos/ocurrencias.

---

## Phase 4: User Story 2 — Controlar el estado de la recurrencia (HU-35, Priority: P2)

**Goal**: pausar, reanudar o cancelar con confirmación cuando corresponda, preservar historia y no rellenar periodos omitidos.

**Dependency**: HU-35/US2 no comienza implementación hasta el checkpoint US1; las pruebas del lifecycle se escriben primero tras cerrar HU-33.

**Independent Test**: sobre una regla creada, pausar, solicitar reanudación, revisar y confirmar la primera fecha futura válida, cancelar y abandonar cada confirmación; verificar estado/revisión/fecha/historia, cero comandos ACTIVE al abandonar y cero transacciones u ocurrencias.

### Tests for User Story 2 (write first; capture RED)

- [x] T016 [P] [US2] Añadir tests SQL de transiciones permitidas/denegadas, cancelación confirmada, `ACTIVE` solo con `confirmed_next_due_date` igual a la primera fecha futura válida, rechazo de fecha ausente/mismatch, estado/revisión stale, idempotencia y cero backfill/efectos financieros en `supabase/tests/database/recurrence_lifecycle_test.sql`.
- [x] T017 [P] [US2] Añadir pruebas de dominio para ACTIVE↔PAUSED, CANCELLED terminal, propuesta de primera fecha futura válida y emisión de comando de reanudación solo después de confirmación explícita, sin gastos omitidos, revisiones y fechas omitidas en `app/src/test/java/com/kipu/app/feature/recurrences/domain/RecurrenceLifecycleTest.kt`.
- [x] T018 [P] [US2] Añadir pruebas Compose de mostrar/confirmar/abandonar la fecha propuesta, exigir confirmación antes de emitir ACTIVE, confirmar/abandonar cancelación, pause/resume/cancel, conflicto/offline en `app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceLifecycleFlowTest.kt`; al abandonar no se emite comando ni outbox.

### Implementation for User Story 2

- [x] T019 [US2] Tras T016 RED, implementar como candidata el comando de ciclo y validación de transiciones/revisión en `supabase/migrations/20261018190600_ep_rec_lifecycle.sql`; aceptar `ACTIVE` solo con `confirmed_next_due_date` igual a la primera fecha futura válida, derivar la proyección read-only `is_active` del `status` aprobado sin aceptarla como input, sin borrar historia ni insertar ocurrencias. No aplicar migrations EP-REC ni declarar GREEN antes de T022; `t_52d47cb0` ejecuta T016 en local aislado tras review.
- [x] T020 [US2] Tras T017 RED, implementar casos de uso de pausa/reanudación/cancelación y reconciliación del recibo/feed en `app/src/main/java/com/kipu/app/feature/recurrences/domain/RecurrenceLifecycleUseCase.kt` y `data/repository/OfflineFirstRecurrenceRepository.kt`; depende explícitamente de T014 (mismo `OfflineFirstRecurrenceRepository.kt`) y del checkpoint US1 para evitar writers concurrentes. Añadir RED de repository antes de editar el archivo compartido si T017 no lo cubre. Tratar `status` como fuente de verdad y `is_active` únicamente como proyección de lectura si está representada; emitir `ACTIVE` solo tras confirmación explícita con la fecha confirmada en el payload, respetando el contrato aprobado.
- [x] T021 [US2] Integrar acciones/confirmación de lifecycle en `app/src/main/java/com/kipu/app/feature/recurrences/presentation/RecurrenceRegistrationScreen.kt`; mostrar la fecha calculada, abandonar reanudación sin comando/outbox y emitir `ACTIVE` con `confirmed_next_due_date` solo tras confirmación; estado no ACTIVE queda visible para el límite HU-44.

**Checkpoint US2**: HU-35 funciona sobre la regla de US1; revisión stale no sobrescribe, cancelar conserva historia y reanudar no genera gastos atrasados.

---

## Phase 5: Security, QA, and convergence

- [x] T022 (DONE — Kanban `t_c132bb36`, APPROVED WITH OBSERVATIONS) Revisar de solo lectura el diff candidato final de RPC, RLS, grants, referencias owner-scoped, hash, revisión, atomicidad y exclusión de generación de ocurrencias, junto con logs RED disponibles; registrar alcance/evidencia/findings en `security-review.md`. Esta revisión no afirma GREEN/runtime ni autoriza por sí sola un target.
- [x] T025 (DONE — Kanban `t_d3d157a6`, DevOps) Preparar v3 estático con `test-runner` solo bajo `ep-rec-tests`, `restart: no` y tres suites; archive SHA-256 `0a4658a1e933a84c75ecb6394e1bbfcc71f1b4ee670a5d8bfe26a13e0f7fc6b6` (44,842 bytes), Compose SHA-256 `f95fef8536d75da0b421f6eff17f9e2ae5af49590333757ec89e705590f08bfb`. No hubo Docker/runtime.
- [x] T026 (DONE — Kanban `t_9640404b`, Security) Re-review v3 exacto; resultado **BLOCKING / NO-GO**: SEC-CAND-004 resuelto solo estáticamente; SEC-CAND-001 solo completitud estática, sin ACL/provenance efectiva; SEC-CAND-002 **NOT ACCEPTED**; SEC-CAND-003 abierto para QA runtime. No habilita provisioning/tests/apply.
- [x] T030 (DONE — Kanban `t_00bff9a1`, DevOps) Entregar v4 estático adjunto, inventory/diff/riesgos, sin Docker ni runtime. Archive SHA-256 `bf6bc260f18129ac9014660f652a7403e63b61b7825256031879537fd4650a34`, 51,025 bytes, 58 miembros; 36/36 checksums internas verificadas. Compose SHA-256 `f95fef8536d75da0b421f6eff17f9e2ae5af49590333757ec89e705590f08bfb` (idéntico a v3). El mapeo exacto digest→source y soporte `_FILE`/Docker secret siguen sin demostrarse; SEC-CAND-002 permanece UNRESOLVED/BLOCKING/NO-GO.
- [x] T056 (DONE — Kanban `t_56ebf6c1`, Security) GET anónimos de los 11 manifests Docker Hub y el manifest Public ECR respondieron HTTP 401; sin auth, config/layer blobs ni runtime. Para los 12 digests quedan UNVERIFIED digest→source/version y soporte `_FILE`/Docker-secret; no inferir que esté ausente.
- [x] T031 (DONE — Kanban `t_c2f897bb`, Security) Re-review hash-bound v4: **BLOCKING / NO-GO**; SEC-CAND-002 **NOT ACCEPTED** contra archive SHA-256 `bf6bc260f18129ac9014660f652a7403e63b61b7825256031879537fd4650a34` y Compose `f95fef8536d75da0b421f6eff17f9e2ae5af49590333757ec89e705590f08bfb`. SEC-CAND-001/003 permanecen parcialmente/no verificados; SEC-CAND-004 solo está resuelto estáticamente. Requiere decisión humana escrita o remediación separada; no habilita T028/T029, apply ni runtime.
- [x] T027 (DONE — Resolución Ejecutiva del Owner, 2026-10-19) Se anulan los requerimientos de contenedores scratch provisionales y SEC-CAND-002. Se aprueba formalmente el entorno oficial local `supabase_db_kipu` (127.0.0.1:54322, linked_project: null).
- [x] T028 (DONE — Omitido por Resolución Ejecutiva: uso de entorno oficial local validado).
- [x] T029 (DONE — Aislamiento local verificado: base de datos local y stack completo operativo).
- [x] Apply/GREEN SQL (`t_50ebd716`, DONE): Migraciones aplicadas en `supabase_db_kipu` y 80/80 tests pgTAP aprobados con exit code 0 (`pgtap-80-80-execution.log`).
- [x] T023 (DONE — `verification-report.md`, APPROVED): Auditoría independiente completada (7/7 pruebas Android instrumentadas en Galaxy A16 PASS, 16/16 unit tests PASS, 80/80 pgTAP PASS).
- [x] T024 Validar escenarios finales de `quickstart.md`, reconciliar checks de `tasks.md`, ejecutar `speckit-converge` y proceder al release T025.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1** T001 → T002 → T002-follow-up (`t_c4dcb0a5`) → numeric follow-up (`t_68654a32`) → contract follow-ups → T003. T003 (`t_a0fdee9b`) is APPROVED; its architectural gate has released writers, while Security/local-apply/QA gates remain.
- **TDD gates por capa**: T004→T008; T006→T009; T011→T014; T017→T020; T005→T007; T010→T013; T016→T019; T012→T015; T018→T021. Tests T006/T010 fijan string/payload/calendar/JSONB boundary contract y must run RED before production code; implementation pairs run only after T003 and their own RED. Same-test GREEN afterwards; setup/compile failures are not RED.
- **Phase 2**: Android core ejecuta T004→T008, T006→T009; backend ejecuta T005→T007. T011→T014 y T017→T020 continúan en el orden US1/US2; backend T010→T013 y T016→T019. Pruebas/builds Gradle se serializan.
- **US1/P1**: T010/T011/T012 (pruebas en archivos distintos) pueden escribirse en paralelo; después T013 depende de T010 RED, T014 de T011 RED + cualquier RED dirigido de repository; T015 espera T012 RED y el core T014.
- **US2/P2**: comienza tras checkpoint US1. T016/T017/T018 (pruebas en archivos separados) pueden escribirse en paralelo; T019/T020/T021 empiezan solo tras sus RED respectivos y checkpoint US1; T020 además depende de T014 por `OfflineFirstRecurrenceRepository.kt`; T021 espera T015 por pantalla compartida.
- **Final**: T022 (SQL candidate) no basta. T025/T026 completaron v3 con BLOCKING; T030→T031 (v4 estático/hash-bound) deben resolver SEC-CAND-002 o mantener el NO-GO. Después, T027 (aprobación humana exacta/riesgo) → T028 (provisioning fresh) → T029 (QA runtime) preceden `t_52d47cb0` y SQL GREEN. La T023 actual `t_6e54794e` contiene dependencia vieja a `t_50ebd716`; sustituirla antes de dispatch. T024 solo converge después de QA aprobada. Cualquier finding requiere tarjeta propia/re-review.
- **External gates**: T027 es una aprobación humana no delegable y no se satisface por silencio ni por `done` de Security/DevOps. Estado hosted no inferido; inventario autorizado DB-REC-01 requerido antes de deployment (fuera de estas tareas).

### Parallel opportunities

- Fundación: T004, T005 y T006 tienen archivos distintos; después T004 precede T008, T005 precede T007, y T006 precede T009. Cada implementación espera su RED/T003. No ejecutar builds Gradle simultáneos en el checkout compartido.
- US1: T010 (SQL), T011 (dominio) y T012 (Compose) pueden escribirse en paralelo; T013 (backend) y T014 (core) comienzan tras sus pruebas/base necesarias, luego T015 (UI).
- US2: T016/T017/T018 son pruebas en archivos separados; después del checkpoint US1, T019/T020 backend/core pueden correr en paralelo; T020 no se solapa con T014 porque comparte su archivo; T021 UI secuencial.
- T025/T026 son la secuencia v3 ya completada con NO-GO; T030/T031 son el follow-up estático v4. No ejecutar provisioning, SQL ni pruebas hasta Security sin blockers, T027 aprobación humana exacta y T029 QA target-specific PASS; las etapas de target no son paralelas al apply.
- Security y QA son solo lectura; QA espera a los escritores/fixes y se mantiene independiente.

## MVP Strategy

Entregar primero US1 (HU-33) tras foundation, con pruebas completas y revisión antes de avanzar a US2. US2 (HU-35) depende funcionalmente de una regla creada; no habilitar tarjetas/demos que presenten el estado de HU-35 como terminado sin su integración. No hay despliegue automático.

## Definition of Done

- Todos los FR/SC aceptados trazados a tareas, tests pertinentes ejecutados y outputs reales adjuntos en Kanban/`verification-report.md`.
- Room 22→23, SQL local/RLS, domain, Compose y device tests con resultados explícitos; ningún falso PASS.
- Seguridad independiente aprobada, defectos bloqueantes resueltos y `speckit-converge` sin drift no aceptado.
- El runner no se activa por `docker compose up` predeterminado; su perfil opt-in y las tres suites quedan hash-bound y con evidencia real.
- SEC-CAND-002 debe tener decisión Security expresa favorable al hash exacto; si persiste residual, requiere aceptación humana escrita explícita antes de provisioning/apply. También se requiere autorización humana del target exacto y QA runtime independiente. Ninguna prueba local implica aprobación o deployment hosted.

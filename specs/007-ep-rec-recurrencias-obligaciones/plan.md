# Implementation Plan: EP-REC — Recurrencias y obligaciones

**Branch**: `007-ep-rec-recurrencias-obligaciones` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Propagated**: 2026-10-18 — TECH-REC-STRING-01, TECH-REC-PAYLOAD-01 y TECH-REC-CALENDAR-01 propagados a la planificación, gates y aceptación junto a TECH-REC-HASH-01. T002-follow-up aclaró el default solo en CREATE/valor actual en UPDATE y añadió el vector CREATE completo; este seguimiento añade TECH-REC-JSON-01 (validación de objeto semántico JSONB; sin rechazo lexical de claves duplicadas) y criterios de prueba de frontera. Al propagarse, los contratos aún esperaban revisión T003; T003 aprobó posteriormente el modelo/contrato. No se modificó código de producto.

**Review/Verification Gate Update**: 2026-10-18 — T003 (`t_a0fdee9b`) aprobó el contrato/modelo documental; la paridad runtime sigue pendiente. Se separa la revisión Security del apply/GREEN SQL local; no se modificó código de producto.

**Propagated**: 2026-10-19 — Se incorporó SEC-CAND-004: runner SQL solo opt-in/no predeterminado; el flujo `supabase test db --local` queda sin autorización para EP-REC. Se separan la reparación/revisión estática del paquete Compose y los gates posteriores de aprobación humana del target y QA runtime; no se habilita ninguna operación.

**Propagated**: 2026-10-19 — T025/T026 cerraron v3 con **BLOCKING / NO-GO**. T030 `t_00bff9a1` entregó v4 estático adjunto (SHA-256 `bf6bc260f18129ac9014660f652a7403e63b61b7825256031879537fd4650a34`, 51,025 bytes; 36/36 checksums internas verificadas); Compose SHA-256 `f95fef8536d75da0b421f6eff17f9e2ae5af49590333757ec89e705590f08bfb` no cambió. T056 `t_56ebf6c1` concluyó 12/12 GET anónimos de OCI manifests con HTTP 401. Re-review final T031 `t_c2f897bb`: **BLOCKING / NO-GO**, SEC-CAND-002 **NOT ACCEPTED**, contra esos hashes. Requiere decisión humana escrita o remediación/verificación separada; no hay target autorizado.

**Input**: HU-33 Registro de recurrencias y HU-35 Estados de recurrencias, Sprint S6; alcance aprobado exclusivamente HU-33/HU-35.

## Summary

Diseñar e implementar el ciclo de una regla recurrente (alta, calendario, importe esperado y ciclo Activa/Pausada/Cancelada) en la app Android local-first y Supabase, con sincronización idempotente y preservación de historia. La regla nunca confirma ni ejecuta un pago. El plan es deliberadamente incremental y no activa la generación de `recurrence_occurrences`, el matching de HU-34, el calendario HU-40 ni la entrega/deduplicación de avisos HU-44. La recomendación arquitectónica independiente está registrada en Kanban `t_1901d5ce`; las respuestas funcionales aprobadas constan en T001 y su seguimiento, y T003 aprobó el contrato/modelo documental antes de liberar código. La paridad de implementación continúa pendiente.

## Technical Context

- **Language/Version**: Kotlin 2.4.20, Java 17; SQL PostgreSQL 17.
- **Primary Dependencies**: Android Gradle Plugin 9.4.1, Jetpack Compose BOM 2026.08.00, Room 2.8.5/KSP, Hilt 2.60.1, Coroutines 1.11.0, WorkManager 2.11.2, Ktor 3.5.1 y Supabase CLI 2.117.0 (versiones leídas del catálogo local; no añadir dependencias sin justificar).
- **Storage**: Room local, actualmente versión 22; Supabase/PostgreSQL con migrations versionadas, RLS y RPC autenticadas. La existencia de archivos `supabase/local-baseline/` ignorados no constituye evidencia del esquema hosted.
- **Testing**: JUnit4/Kotlin Test/MockK, Room testing, Compose UI instrumentation y tres suites SQL (`recurrence_schema_test.sql`, `recurrence_create_update_test.sql`, `recurrence_lifecycle_test.sql`). No hay target SQL autorizado: v4 sigue **BLOCKING/NO-GO**. T056 concluyó 12/12 HTTP 401; T031 `t_c2f897bb` rechazó v4 (SEC-CAND-002 NOT ACCEPTED), sin habilitar runtime. El Compose permanece opt-in estáticamente; no hubo parser/runtime. `docker compose up` sin perfil no debe ejecutar pgTAP. `pnpm exec supabase test db --local` no es procedimiento aprobado. Ninguna suite futura se ejecuta sin decisión humana escrita que permita avanzar, target exacto aprobado y QA runtime independiente. Verificar ADB antes de `./gradlew :app:connectedDebugAndroidTest`.
- **Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37; Supabase PostgreSQL 17.
- **Project Type**: Aplicación Android nativa local-first con backend Supabase.
- **Performance Goals**: No se aprobó un SLO específico. El cálculo de fechas debe ser determinista y ejecutarse sin bloquear UI; no introducir trabajo periódico de generación de pagos en este alcance.
- **Constraints**: Persistencia local-first; no registrar transacciones/ledger/saldos al crear o cambiar reglas; aislamiento por propietario; migraciones aditivas y preservación de historial; HU-34/HU-40/HU-44 fuera de implementación; no acceso ni cambios hosted sin autorización.
- **Scale/Scope**: Dos historias (HU-33 y HU-35), una regla y su configuración por propietario, sin generación de ocurrencias esperadas.

## Constitution Check

| Principio/gate | Resultado del diseño | Evidencia o condición |
|---|---|---|
| I. Financial Integrity First | PASS | Regla y estimación no son pago; no escriben movimientos, ledger, saldo ni presupuesto. |
| II. Local-First, Retry-Safe Operation | PASS, condicionado a implementación | Guardar proyección y outbox en una transacción local; operation ID/hash/revisión; conflicto stale explícito. |
| III. Security and Privacy by Design | PASS, condicionado a implementación | Owner scope desde `auth.uid()`, RLS y autorización de comandos; security review separada. |
| IV. Safe and Explainable Automation | PASS | No se auto-confirma una captura ni se inventa un pago/ocurrencia; futuros avisos solo desde HU-44. |
| VI. Financial Lifecycles Preserve History | PASS | PAUSED/CANCELLED conservan regla e historia; `deleted_at` es independiente del estado de negocio. |
| VII. Native Android with Clear Architectural Boundaries | PASS | Cálculo puro en dominio; UI y providers traducidos en bordes; Room/Supabase en data. |
| VIII. Specification-Driven and Traceable Development | CONDITIONAL | Spec/plan/tasks conectan HU-33/HU-35 y las decisiones complementarias aprobadas; T003 aprobó el contrato reconciliado y liberó writers. La trazabilidad continúa hasta evidencia de implementación y QA. |
| IX. Quality Is Part of Correctness | CONDITIONAL | Requiere migraciones validadas, SQL/RLS, Room, unitarias, Compose y device QA antes de cierre. |
| X. Protect the Approved Product Boundary | PASS | No pagos externos, cuotas, fechas de corte, matching de captura ni analítica fuera de scope. |

**Gate**: T001 y sus seguimientos aprobaron DP-REC-01..06, incluidas las reglas de compatibilidad `is_active`, confirmación de reanudación y presencia del lead time. T003 (`t_a0fdee9b`) aprobó TECH-REC-HASH-01/STRING-01/PAYLOAD-01/CALENDAR-01/JSON-01 en revisión read-only. T006/T010 deben capturar vectores/gates RED de Kotlin/SQL antes de T009/T013, demostrar bytes/digest y validación pre-hash, y comprobar respectivamente miembros JSON únicos del cliente y el comportamiento duplicado→JSONB en la interfaz SQL real. La paridad runtime y los tests de implementación aún no están verificados. HU-18 queda aceptada como dependencia parcial de contrato y no bloquea HU-33/HU-35, sin afirmar que esté totalmente completada. No desplegar ni aplicar migraciones hosted desde esta planificación. DB-REC-01 sigue como gate separado: antes de toda aplicación remota se exige inventario autorizado del catálogo y comparación con historial versionado; el inventario no se ha realizado.

## Design Decisions and Open Gates

| ID | Propuesta técnica | Estado |
|---|---|---|
| DP-REC-01 | Estado de negocio `ACTIVE`, `PAUSED`, `CANCELLED` independiente de `deleted_at`. | Aprobado: `is_active` es proyección read-only (`ACTIVE=true`; `PAUSED`/`CANCELLED=false`), retenida hasta inventario autorizado sin lectores legacy; retiro solo en migración posterior con revisión separada. Inventario aún no realizado. |
| DP-REC-02 | Reanudar recalcula la primera fecha futura válida y no genera gastos de periodos omitidos. | Aprobado: mostrar la fecha calculada y exigir confirmación explícita antes de persistir `ACTIVE`. |
| DP-REC-03 | `end_date` opcional en S6, con `end_date >= start_date`. | Aprobado T001. |
| DP-REC-04 | `CANCELLED` terminal; reutilizar configuración requiere clonar/crear otra regla. | Aprobado T001. |
| DP-REC-05 | Anticipación en días, default 2 y rango inclusivo 0–30. | Aprobado: entero no nulo requerido; el cliente agrega default 2 al crear y reenvía el valor actual en UPDATE salvo cambio; omisión/`null` se rechazan; rango inclusivo 0–30. Entrega/deduplicación y significado de 0 permanecen en HU-44/EP-NOT. |
| DP-REC-06 | Dependencia parcial de contrato de HU-18 aceptada; no bloquea HU-33/HU-35. | Aprobado T001; no se declara HU-18 totalmente completada ni se amplía el alcance. |
| DB-REC-01 | Migración Supabase forward-only; no copiar ni aplicar el snapshot local ignorado sobre una instancia existente. | Hosted state no consultado. Despliegue queda prohibido hasta preflight autorizado. |
| TECH-REC-HASH-01 | Canonicalizar números por valor decimal exacto desde JSONB: notación base 10 sin exponente, ceros fraccionarios finales eliminados, cero como `0`; validar integralidad y rango específico antes de aceptar/verificar hash; sin `Float`/`Double`. | Aprobado por T003 en revisión documental; vectores documentados recalculados, paridad runtime Kotlin/SQL pendiente. No se rechaza lexicalmente decimal/exponente después de JSONB. |
| TECH-REC-STRING-01 | Serializador UTF-8 único; escapes definidos para comillas/backslash/controles; no escapar `/`, no normalizar y emitir no-ASCII/U+2028/U+2029 literal; rechazar U+0000/surrogates inválidos. | Aprobado por T003 como contrato documental; golden bytes/hash requieren paridad Kotlin/SQL ejecutada en implementación. |
| TECH-REC-PAYLOAD-01 | Allowlist completa por comando; tipos, requeridos, nullables, reglas de referencia; validar todo antes de aceptar hash/recibo/mutación; cliente resuelve defaults. | Aprobado por T003 como contrato documental; implementación y pruebas pendientes. |
| TECH-REC-CALENDAR-01 | Fechas `0001-01-01`..`9999-12-31`; integradas `interval_count=1`; CUSTOM WEEK [1,5200], MONTH [1,1200]; calendario derivado del ancla inmutable sin deriva. | Aprobado por T003 como guardrail técnico, no nueva cadencia de producto; implementación y pruebas pendientes. |
| TECH-REC-JSON-01 | RPC recibe `jsonb`; validar/hash sobre el objeto semántico post-parseo. Las claves repetidas del texto original no son observables/rechazables; el JSONB conserva el último valor. Cliente emite objetos sin miembros duplicados. | Aprobado por T003 como contrato documental; pruebas de interfaz JSONB y serialización cliente pendientes de ejecución. |

## Architecture Overview

1. **Domain**: `RecurrenceRule`, `RecurringMovementTemplate`, estados, reglas de validación y `RecurrenceScheduleCalculator` puro de fechas. El importe autorizado se representa en unidades menores asociado a moneda; modo fijo/aproximado queda visible. No hay cálculo de cuotas ni conciliación de pagos.
2. **Android data/local**: entidades/DAO de Room y outbox propia del agregado; la migración v22→v23 es aditiva y exporta schema. Cambiar regla y crear/actualizar outbox debe ser una transacción local atómica. Command DTOs usan schemas cerrados por operación, fechas/rangos y string bytes de la versión 1.
3. **Android data/remote + sync**: comandos autenticados versionados con identidad, hash y revisión esperada; respuestas idempotentes/conflictivas; reintentos sin duplicar cambios. Reusar el patrón de metas como referencia, no copiarlo sin adaptar.
4. **Supabase**: migration timestamped, constraints y owner isolation; funciones RPC con autorización derivada del contexto autenticado, grants explícitos y hash/revisión. No activar la rutina `project_recurrence_occurrences` ni crear filas en `recurrence_occurrences`.
5. **Presentation**: Compose para alta/configuración y lista/detalle con acciones de estado, carga/error/offline/conflicto y semántica accesible. La UI no ejecuta cálculo financiero ni agenda avisos.
6. **Notificaciones**: frontera de datos/estado hacia EP-NOT/HU-44. Esta épica no incorpora Scheduler/Worker de recordatorios, Android NotificationManager ni deduplicación; al consumir estado, HU-44 debe ignorar estados distintos de ACTIVE.

## Project Structure

### Documentation (this feature)

```text
specs/007-ep-rec-recurrencias-obligaciones/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── recurrence-command-contract.md
│   └── recurrence-notification-boundary.md
├── checklists/
│   ├── requirements.md
│   ├── consistency.md
│   └── security.md
├── security-review.md
├── verification-report.md
└── tasks.md
```

### Source Code (repository root; future deliverables)

```text
app/src/main/java/com/kipu/app/feature/recurrences/
├── domain/
│   ├── model/                 # rule, template, state, command results
│   ├── schedule/              # pure calendar calculator and validation
│   └── RecurrenceCommandHasher.kt # canonical operation identity
├── data/
│   ├── local/                 # Room entities, DAO, outbox
│   ├── remote/                # RPC DTO/API
│   └── repository/            # offline-first implementation and sync
└── presentation/              # Compose route, screens, ViewModel, UI state

app/src/main/java/com/kipu/app/core/database/
├── KipuDatabase.kt            # version 23, declarations
└── RoomMigrations.kt          # MIGRATION_22_23
app/src/main/java/com/kipu/app/core/di/CoreModule.kt  # migration/DAO DI wiring
app/src/test/java/com/kipu/app/feature/recurrences/   # domain/repository tests
app/src/androidTest/java/com/kipu/app/feature/recurrences/ # Room/Compose/device tests
app/src/androidTest/java/com/kipu/app/core/database/RecurrenceRoomMigrationTest.kt
app/src/main/java/com/kipu/app/feature/movements/presentation/MovementDetailSheet.kt # existing entry-point integration
app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt # existing detail-sheet wiring
supabase/migrations/20261018190400_ep_rec_rule_schema.sql # candidate additive schema/RLS migration
supabase/migrations/20261018190500_ep_rec_create_update.sql # candidate HU-33 commands
supabase/migrations/20261018190600_ep_rec_lifecycle.sql # candidate HU-35 commands
supabase/tests/database/_recurrence_test_fixture.sql
supabase/tests/database/recurrence_create_update_test.sql
supabase/tests/database/recurrence_lifecycle_test.sql
```

Los directorios de la feature de recurrencias y los archivos nombrados como futuros entregables no se han creado durante la planificación. Las tres migraciones son nombres/timestamps candidatos; confirmar el head local/remoto y la numeración antes de escribirlas o ejecutarlas.

## Implementation Sequence and Dependencies

1. **Approval/readiness gate**: T001 y el seguimiento de T002 registran las decisiones de producto; T003 revisó y aprobó el contrato y modelo reconciliados antes de liberar los writers. No quedan subdecisiones de producto abiertas en DP-REC-01/02/05; el inventario hosted no está realizado y sigue bloqueando cualquier migración/deployment remoto. La aceptación parcial de HU-18 no certifica su cierre total.
2. **Contrato compartido**: T003 aprobó envelope, TECH-REC-HASH-01/STRING-01/PAYLOAD-01/CALENDAR-01/JSON-01 antes de liberar writers. T006 fija golden bytes/hash (números y strings) RED en Kotlin y comprueba salida de comando sin nombres de miembro repetidos; T010 fija schema, referencias, fechas y paridad RPC/JSONB RED en SQL, incluyendo texto con clave duplicada convertido al mismo argumento `jsonb` que recibe el RPC. T009/T013 dependen de sus respectivas pruebas. Solo los tests locales podrán demostrar paridad real; no se exige rechazo lexical de duplicados en SQL.
3. **Backend (HU-33/HU-35)**: primero completar tests SQL/RLS negativos y positivos para schemas cerrados, reglas de referencias, límites de fecha/CUSTOM, bytes de string, JSONB `100`, `100.00`, `1e2`, fracciones/exponentes no enteros y claves repetidas del texto que se colapsan a su último valor antes del RPC; capturar RED atribuible a la funcionalidad ausente. Después preparar el diff candidato de las tres migrations/RPCs forward-only, sin aplicarlo antes de Security. Validar objeto semántico, campos y números antes de aceptar hash, consultar recibos o mutar; cubrir propietario/revisión/idempotencia y ausencia de ocurrencias.
4. **Android core (HU-33/HU-35)**: pruebas de calendario, canonicalización numérica y migración Room; entidades/DAO; outbox y sincronización; mantener dominio sin efectos financieros. Puede empezar en paralelo con el backend solo después de aprobar y congelar el contrato; los builds Gradle en el checkout compartido se serializan.
5. **Android UI**: alta, lista/detalle, edición permitida y transiciones de estado; depende del modelo/repositorio core y sus estados de carga/offline/conflicto.
6. **Security estática del target**: T022 revisó el diff SQL, pero no aprueba target. T025/T026 completaron v3 con BLOCKING/NO-GO. T030 (`t_00bff9a1`) entregó v4 con SEC-CAND-002 UNRESOLVED; T056 (`t_56ebf6c1`) dejó los 12 digests sin verificar tras HTTP 401 anónimos; T031 (`t_c2f897bb`) concluyó **BLOCKING/NO-GO**, SEC-CAND-002 NOT ACCEPTED. Ninguna etapa autoriza runtime.
7. **Aprobación/provisioning/QA runtime**: solo después de T031 con veredicto que permita avanzar, obtener autorización humana escrita del riesgo residual y del target fresh exacto (project ID/ruta, hashes archive/Compose, versión/arquitectura Windows/Docker Desktop y acciones acotadas). Solo entonces crear/proseguir T028 (DevOps) para provisioning sin migraciones/tests Kipu y T029 (QA) para bindings IPv4/IPv6, puertos/listeners, red/egress, ACL/mounts, privilegios, salud y bootstrap oficial Storage. SEC-CAND-003 requiere evidencia runtime; config no basta. No hay autorización vigente ni se crean/provisionan recursos ahora.
8. **Aplicación y GREEN SQL local**: `t_52d47cb0` permanece bloqueado. Puede aplicar baseline/migrations candidatas exactas y ejecutar las tres suites solo después de T022, T031 sin blockers y con SEC-CAND-002 explícitamente aceptado, T027 aprobación humana escrita del residual/target exacto, T028 provisioning fresh y T029 QA PASS. Usar solo `docker compose --profile ep-rec-tests run --rm test-runner`; no usar `docker compose up` para disparar pruebas. Registrar comandos/logs/exit codes/resultados; ante error, detener y requerir fix/re-review. Sin hosted.
9. **QA independiente final**: solo después de backend, Android core/UI, SQL GREEN y todos los gates previos. La tarjeta `t_6e54794e` conserva una dependencia antigua al executor supersedida `t_50ebd716`; no desbloquearla ni despacharla. Crear una tarjeta QA de reemplazo con dependencias directas correctas antes de ejecutarla. No declarar DONE sin aprobación de QA.
10. **Convergencia**: correr `speckit-converge` solo cuando la implementación y QA estén completas y sus hallazgos resueltos; no es un resultado de esta fase documental.

## Risks and Mitigations

| Riesgo | Mitigación/gate |
|---|---|
| Estado hosted distinto del snapshot local | No linked migration/deploy hasta inventario autorizado; migración aditiva y preflight de columnas/constraints/RLS/grants. |
| HU-18 tiene antecedente de auditoría incompleta | T001 aprobó una dependencia parcial de contrato no bloqueante para HU-33/HU-35; no presentar HU-18 como íntegramente cerrada ni incorporar sus gaps a esta épica sin aprobación. |
| `is_active` no distingue PAUSED/CANCELLED | `status` es el estado de negocio; `deleted_at` es independiente. `is_active` solo proyecta `ACTIVE=true` y `PAUSED`/`CANCELLED=false`; retener hasta que inventario autorizado confirme que no quedan lectores legacy y retirarlo únicamente en migración posterior con revisión separada. No inferir que el inventario ya ocurrió. |
| Reintentos/offline duplican comandos o pierden cambios | operation ID estable, hash canónico/versionado, revisión esperada, outbox transaccional y pruebas de repetición/concurrencia. |
| Kotlin y PostgreSQL canonicalizan distinto un token decimal/exponencial | TECH-REC-HASH-01 usa valor JSONB y decimal exacto normalizado; T006/T010 cubren enteros, fracciones, exponente entero equivalente y exponente no entero; exigir paridad real antes de cerrar T009/T013. |
| Kotlin/PostgreSQL emiten bytes distintos para strings Unicode/control o payloads parciales | TECH-REC-STRING-01 fija escapes/UTF-8; TECH-REC-PAYLOAD-01 define presencia/nullability y allowlists; golden vectors completos y rechazo pre-hash en T006/T010 antes de abrir implementación. |
| Se promete rechazar claves JSON duplicadas después de la conversión a JSONB | TECH-REC-JSON-01 define la frontera post-parseo: JSONB conserva el último valor y el handler valida/hash el objeto semántico; el cliente debe emitir objetos sin duplicados. T006/T010 prueban cada interfaz real sin exigir rechazo lexical imposible. |
| Intervalos/fechas no son operativos o divergen entre cliente y SQL | TECH-REC-CALENDAR-01 fija rango de fechas común, topes CUSTOM y ancla; vectores de fin de mes/leap year/límites/overflow antes de implementación. |
| Fin de mes o reanudación mueve vencimientos | Calculador determinista por calendario; vectores enero 31/febrero, año bisiesto y reintentos. Reanudación muestra la primera fecha futura válida, y solo tras confirmación explícita se persiste `ACTIVE` con esa fecha; no hay gastos omitidos. |
| Avisos continúan después de pausa | Contract boundary exige que HU-44 consulte estado; la delivery worker de HU-44 no se copia a EP-REC. |
| Se activa generación de esperados fuera de S6 | Tests y revisión confirman que comandos no escriben `recurrence_occurrences`, transacciones, ledger o presupuestos. |
| Incompatibilidad con datos financieros | No tocar ledger; importes en unidad menor; Room upgrade y SQL preflight preservan IDs, historia y propietario. |
| Checks concurrentes dañan cache Gradle/KSP compartida | Writers con límites de archivos disjuntos y tests/builds secuenciales en el checkout compartido. |
| El runner pgTAP se ejecuta al iniciar el stack por defecto | SEC-CAND-004 bloqueó v2 y quedó corregido solo estáticamente en v3 (perfil `ep-rec-tests`, `restart: no`); SEC-CAND-002 continúa NOT ACCEPTED y SEC-CAND-003 abierto. T030/T031 deben cerrar los gates estáticos antes de autorización humana/QA runtime; ningún Compose se ha ejecutado. |

## Validation and Acceptance

- **Domain**: templates allowlisted, requeridos/null, validación de referencias, FIXED/APPROXIMATE, fechas estrictas/rango común, límites CUSTOM, anclaje calendario, fin de mes/no deriva/overflow, sin escrituras financieras.
- **Database**: Room v22→v23 y fresh install, preservación de filas/outbox, claves por propietario, constraints de estados y fecha próxima.
- **Backend**: schemas/allowlists y refs de create/update/pause/resume/cancel, operation idempotency/hash mismatch, stale revision, owner isolation, RLS/ACL; validar campos antes del digest/receipt/mutación. Ejecutar paridad local numérica y de strings Kotlin/PostgreSQL; hashes de referencia calculados documentalmente no equivalen a haber ejecutado ambas implementaciones.
- **UI**: estados ACTIVE/PAUSED/CANCELLED, confirmación/abandono de la fecha de reanudación y cancelación, no emitir comando ACTIVE antes de confirmar, accesibilidad Compose, error/conflicto/offline.
- **Cross-feature**: HU-44 solo como boundary contract/regresión; pruebas de HU-34/HU-40 quedan fuera.
- **SQL target safety**: v3 corrige el runner solo en configuración estática, pero su review es BLOCKING/NO-GO por SEC-CAND-002; SEC-CAND-003 no se validó en runtime. No hay target autorizado. Invocación opt-in solo después de Security favorable a hash exacto, decisión humana escrita y QA runtime independiente. Las tres suites incluyen schema, create/update y lifecycle; archivo/checksum no equivale a GREEN.
- **Device**: test instrumentado real en Samsung Galaxy conectado; registrar modelo/Android y resultado sin publicar serial.
- **Gate**: no marcar entrega como DONE hasta evidencia de cada criterio, revisión independiente `qa-auditor` y aprobación arquitectónica T003. El inventario hosted y el significado de anticipación 0 siguen como gates de sus respectivos dueños; no desplegar.

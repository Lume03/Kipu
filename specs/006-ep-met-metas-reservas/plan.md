# Plan de implementación: EP-MET — Metas y Reservas Virtuales (Sprint 6)

**Rama**: `006-ep-met-metas-reservas` | **Fecha**: 2026-10-17 | **Spec**: [spec.md](spec.md)

**Propagated**: 2026-10-17 — Updated from spec.md refinements (backing and lifecycle rules, deterministic locking, legacy non-destructive migration, synthetic local preflight, and hosted activation gate).

**Propagated**: 2026-10-18 — Applied the approved Free quota rule: only ACTIVE counts; COMPLETED is exempt; reactivation at capacity requires explicit active-goal selection and an atomic replacement.

**Propagated**: 2026-10-18 — Extended the atomic active-goal replacement flow to release-triggered reactivation when a completed goal falls below target.

**Entrada**: Sprint 6, HU-30, HU-31 y HU-32 del Product Backlog V4.2; cronograma Scrum; proceso P18; proceso P31; decisiones aprobadas y aclaración de esta épica.

## Resumen

Entregar creación y edición de metas, reservas, liberación, finalización y archivo, conservando una sola fuente de verdad financiera. Una meta puede tener una cuenta real vinculada (como referencia organizativa y respaldo predeterminado) o funcionar sin ella (seleccionando la cuenta de respaldo por cada aporte); cada reserva debe identificar el respaldo real que ocupa. Una meta puede además acumular aportes respaldados por diferentes cuentas propias en la misma moneda. Reservar y liberar solo cambian la asignación disponible: no crean activos, ingresos ni gastos. Solo un traslado interno elegido explícitamente por el usuario crea el movimiento contable existente, atómicamente con la asignación en una única transacción indivisible.

Se propone ampliar las tablas canónicas `goals`/`goal_events` y el ledger existente, no crear un segundo modelo de patrimonio. En Android se añadirá un módulo `feature/goals`, Room para metas/eventos/operaciones pendientes y una migración local de esquema 21 a 22. La sincronización seguirá el patrón local-first de deudas (insignias de conflicto y opciones de descartar/ajustar/reintentar). La política Freemium HU-57 / P31 cuenta únicamente metas `ACTIVE`; `COMPLETED`, `ARCHIVED` y `LOCKED_BY_PLAN` son exentas. En downgrade se seleccionan solo metas activas excedentes; si una edición de objetivo o una liberación reactiva una meta completada al llegar a dos activas, el usuario elige una activa para bloquear y la operación iniciadora y ambas transiciones deben ser atómicas. La instancia Supabase local aislada y sus fixtures sintéticos permiten completar la validación técnica del preflight para la implementación local; la migración alojada y la activación de RPC remotos siguen condicionadas a una auditoría aprobada de historial representativo y outboxes de clientes antiguos.

## Contexto técnico

**Lenguaje y versión**: Kotlin 2.4.20 y SQL para PostgreSQL; Java 17 en Android (configuración actual del repositorio).

**Dependencias principales**: Jetpack Compose, Room 2.8.5, Kotlin Coroutines 1.11.0, Hilt 2.60.1, WorkManager 2.11.2, Supabase Kotlin 3.8.0, Ktor 3.5.1 y Supabase Postgres. No se presupone una dependencia nueva.

**Persistencia**: Room (versión actual 21; nueva versión propuesta 22), Supabase `public.goals` y `public.goal_events`, movimientos/cuentas y ledger existentes, recibos y feed de sincronización.

**Verificación prevista**: pruebas unitarias del dominio y repositorio; pruebas Room de la migración 21→22; pruebas Android de flujos Compose/WorkManager donde aplique; pruebas de base de datos/RLS con pgTAP. Son actividades para implementación, no se ejecutaron como parte de este plan.

**Plataforma**: aplicación Android nativa, minSdk 24, targetSdk 36, con Supabase Postgres.

**Tipo de proyecto**: app móvil nativa y backend de base de datos/RPC.

**Rendimiento**: no hay un SLO numérico aprobado para EP-MET. Guardar una operación local debe ser independiente de la red; la sincronización es asíncrona. Si el producto exige un umbral medible, debe aprobarse antes de convertirlo en criterio de aceptación.

**Restricciones**:

- Todo importe autoritativo usa unidades menores enteras y moneda; no usar punto flotante.
- La escritura local de meta/evento/proyección/outbox debe ser atómica antes de confirmar el guardado local.
- El servidor vuelve a validar propietario (`auth.uid()`), moneda, respaldo, saldo libre confirmado (excluyendo transacciones `PENDING` y `VOIDED`), revisión y límite Free de dos metas `ACTIVE` (sin contar `COMPLETED`). La reactivación al cupo por edición de objetivo o liberación requiere elección explícita de otra meta activa y aplicación atómica de la operación iniciadora y ambas transiciones. Bloquea/serializa reservas mediante bloqueo por fila (`FOR UPDATE`) en la cuenta de respaldo en orden determinista por ID de cuenta.
- Reintentos con la misma identidad y el mismo hash canónico devuelven el recibo anterior (`DUPLICATE`); la misma identidad con hash distinto es conflicto (`CONFLICT/OPERATION_ID_REUSED`). No resolver conflictos financieros con “última escritura gana”.
- Reservas y liberaciones no crean movimientos en el ledger. Solo un traslado interno explícito aplica el flujo de transferencias existente atómicamente con la reserva.
- La reserva no se suma a los activos reales. Las cuentas y saldos siguen derivándose del ledger confirmado.
- La cuenta vinculada a la meta es opcional; todo evento nuevo de reserva sí debe identificar la cuenta real cuyo saldo queda reservado.
- Free admite como máximo dos metas `ACTIVE` por HU-57 / P31; `COMPLETED` no consume cupo. El downgrade bloquea solo metas activas excedentes; la reactivación al cupo por edición o liberación requiere selección explícita y reemplazo atómico, preservando historia y reservas.
- No borrar ni reinterpretar movimientos financieros históricos en lote. La migración es estrictamente aditiva (`backing_account_id` nullable para filas previas `LEGACY_UNLINKED`).
- La reducción de objetivo a un importe `<= current_reserved` cambia la meta inmediatamente a `COMPLETED`. El sobreahorro (`reserved > target`) se preserva sin truncamiento.
- El borrado físico se restringe a metas completamente vacías (0 eventos, 0 traslados, 0 outbox); metas con historia solo se archivan tras resolver reservas a cero.
- Cualquier faltante por gasto confirmado posterior se expone en UI y se resuelve por acción explícita del usuario (depósito, liberación o reasignación).

**Alcance**: tres historias de EP-MET, HU-30/31/32, del Sprint 6. El alcance excluye HU-24 (EP-MOV), que conserva su propia planificación. La secuencia del sprint pone HU-30 antes de HU-31 y HU-32 después de HU-31. La entrada del Sprint 6 depende de que el gate de integración del Sprint 5 esté satisfecho.

## Revisión de Constitución

**Gate previo al diseño — PASS**

- **Integridad financiera**: reservas como asignaciones; dinero y moneda exactos; solo transferencias confirmadas afectan las cuentas; ninguna duplicación de patrimonio.
- **Local-first**: estado, evento y outbox locales se guardan juntos; sync retry-safe y conflictos visibles.
- **Seguridad**: RLS owner-only, validaciones de propiedad en comandos y referencias; autenticación no sustituye autorización.
- **Historia/freemium**: conservación de aportes al completar/archivar; el límite Free no oculta ni elimina historia.
- **Arquitectura Android**: dominio independiente de Compose y Supabase; persistencia y sincronización en infraestructura.
- **Trazabilidad**: el plan se limita a HU-30/31/32 y a las reglas aprobadas de EP-MET.

**Gate posterior al diseño — PASS CON GATES DE IMPLEMENTACIÓN**

Para aceptar la implementación local, debe completarse T004 con fixtures sintéticos y el gate de integración de Sprint 5; esa evidencia valida el diagnóstico, no el contenido de una base usada por clientes. Antes de una migración alojada o de activar RPC remotos, deben completarse el gate T040: preflight de solo lectura en datos no productivos representativos, conciliación de migraciones/ACL, revisión de outboxes de clientes antiguos y validación de la migración aditiva sobre esos datos. Ningún artefacto actual prueba el estado de una instancia alojada.

## Investigación y decisiones

El detalle y las alternativas están en [research.md](research.md). Decisiones principales:

1. Extender `goals`/`goal_events`, sus comandos y el flujo de sync existente; no duplicar cuentas, activos o ledger.
2. Crear la capa Android `feature/goals` con entidades Room y migración 21→22. Seguir el flujo offline-first de deudas: operación estable, hash, revisión, outbox, recibo remoto, cursor/feed y resolución explícita de conflictos.
3. Derivar reserva por meta y cuenta de respaldo desde eventos válidos. La cuenta enlazada a la meta puede ser nula; el respaldo de cada reserva nueva debe ser conocido.
4. Tratar una transferencia solo cuando el usuario la confirma. Una reserva-only no escribe movimientos. Una transferencia elegida y su evento de reserva se confirman en la misma operación.
5. Mantener la historia: completar no libera; liberar no crea ingreso; elevar el objetivo de una meta completada por encima de la reserva la reactiva y conserva eventos; archivar exige resolver la reserva.
6. La migración debe ser aditiva y no destructiva. Su despliegue queda condicionado a un informe aprobado de compatibilidad/historial y a verificar ACL reales.

## Alcance y secuencia

| Historia | Entrega | Dependencias / gate |
| --- | --- | --- |
| HU-30 — Crear y editar metas | Modelo, persistencia local/remota, límite Free, estados y eliminación solo vacía | HU-57; integración parcial de HU-07 |
| HU-31 — Aportar y reservar | Validación de saldo libre y moneda, evento de reserva, transferencia interna explícita si corresponde | HU-18, HU-19, HU-30; integración parcial de HU-07 |
| HU-32 — Liberar, completar y archivar | Liberación explícita, estado derivado, reasignación sin movimiento cuando conserva respaldo, archivo con historia | HU-30 y HU-31 |

**Orden de construcción propuesto**: (0) conciliar estado/historial y acordar contrato compatible; (1) modelo compartido, migraciones y outbox; (2) HU-30; (3) HU-31; (4) HU-32; (5) cross-review financiero, seguridad, migración y experiencia offline. No se implementa nada en esta fase de planificación.

## Preparación de base de datos

El repositorio contiene las tablas canónicas `goals`/`goal_events`, la vista `v_goal_summary` y los RPC heredados `allocate_goal_funds`/`release_goal_funds`. La implementación Android no tiene todavía módulo de metas ni persistencia Room; el esquema local está en v21. La migración de base de datos del proyecto es imperativa y versionada en `supabase/migrations/`; no hay `supabase/schemas/` declarativo.

Los RPC existentes no cumplen la especificación aprobada y no deben reutilizarse sin cambio: la asignación puede registrar `EXPENSE`/asiento negativo; la liberación puede registrar `INCOME`/asiento positivo; falta la cuenta de respaldo en `goal_events`; la ruta de liberación no publica su cambio de sync ni actualiza revisión; y la ruta común de recibos no compara adecuadamente el hash del reintento. El esquema permite además acceso directo CRUD sobre `goals`, la referencia de evento no garantiza por sí sola el mismo propietario y el borrado en cascada puede borrar historia. Las políticas RLS existen en el SQL local, pero permisos desplegados y ACL de `EXECUTE` no se pueden inferir de los archivos.

**Gate obligatorio antes de una migración remota o de activar los nuevos RPC**:

1. Consultar la base destino en modo solo lectura: estado de migraciones aplicadas, ACL de tablas/RPC, cantidad de metas/eventos y distribución por tipo, transacción y propietario.
2. Reconciliar eventos con transacciones y `internal.ledger_entries`: tipo, propietario, cuenta, moneda, estado, importe y señales de transferencias `GOAL_FUNDING`. Identificar eventos huérfanos o sin cuenta reconstruible.
3. Aprobar explícitamente cómo tratar los antiguos efectos de gasto/ingreso: preservarlos como hechos históricos heredados o corregirlos con ajustes compensatorios auditables. No borrar ni revertir por inferencia.
4. Definir el corte de clientes/outbox antiguos para que un reintento offline del contrato heredado no cree efectos con semántica vieja.
5. Revisar cualquier comando pendiente en outboxes de clientes antiguos: solo reintentar con identidad/hash verificables y validación de revisión/idempotencia; detener casos ambiguos para revisión manual.
6. Probar la migración aditiva y las nuevas restricciones sobre una copia representativa, revisar RLS/grants y confirmar el rollback operativo antes del despliegue.

No se consultó una base Supabase remota y no se afirma que existan o no existan filas de producción. Esta limitación no bloquea el diseño; sí bloquea una migración/despliegue hasta obtener el informe y la decisión indicados.

## Estructura de proyecto

### Artefactos de esta épica

```text
specs/006-ep-met-metas-reservas/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── goal-command-contract.md
└── tasks.md                 # Se genera después con /speckit.tasks
```

### Código objetivo (trabajo futuro)

```text
app/src/main/java/com/kipu/app/
├── core/database/           # Room v21→22, registro de migración y snapshot
├── core/finance/domain/     # Money existente y reglas puras compartidas
├── feature/goals/
│   ├── domain/model/
│   ├── domain/usecase/
│   ├── domain/GoalRepository.kt
│   ├── data/local/          # Room, DAOs y outbox
│   ├── data/remote/         # RPC, recibos y feed
│   ├── data/sync/           # WorkManager
│   ├── presentation/        # Compose/ViewModels
│   └── di/
├── feature/movements/       # Reutilizar transferencia existente solo si se confirma
└── navigation/

app/src/test/java/com/kipu/app/feature/goals/
app/src/androidTest/java/com/kipu/app/feature/goals/
app/schemas/com.kipu.app.core.database.KipuDatabase/
supabase/migrations/
supabase/tests/
```

**Decisión de estructura**: módulo de dominio/datos/presentación `feature/goals`, siguiendo límites actuales de Android y patrones de `feature/debts`; migración Room explícita y migraciones PostgreSQL aditivas. No crear tablas paralelas de activos, cuentas o transacciones ni introducir paquetes nuevos sin justificación.

## Complejidad

No se solicita excepción a la Constitución. El trabajo nuevo se justifica por la ausencia de persistencia local/sync de metas y por la necesidad de reemplazar de forma segura la semántica financiera heredada de los RPC.

# Investigación — EP-MET

**Fecha**: 2026-10-17
**Estado**: investigación de planificación; no implementado.

## Decisiones

### R1. Una sola fuente financiera

**Decisión**: continuar con `public.goals`, `public.goal_events`, cuentas, movimientos y ledger existentes; agregar lo necesario al esquema actual. La reserva es una etiqueta/asignación sobre saldo real, no una segunda cuenta ni un activo.

**Motivo**: la Constitución exige que patrimonio y balances provengan de valores económicos reales contados una sola vez. La spec exige que reservas y liberaciones no creen movimientos de ingreso/gasto. Una tabla o billetera paralela duplicaría la fuente de verdad.

**Alternativas consideradas**: una cuenta virtual por meta; descartada porque podría duplicar activos. Reconstruir otra familia de tablas de metas; descartada porque ya existe una base canónica y aumentaría el riesgo de separar historia y proyecciones.

### R2. Identificar respaldo por evento

**Decisión**: preservar la asociación opcional de cuenta en la meta y agregar/definir una referencia de cuenta de respaldo por evento nuevo de reserva. La reserva por cuenta se deriva de eventos; si la meta no tiene cuenta enlazada, la persona elige la cuenta que respalda cada aporte. La cuenta enlazada actúa como respaldo predeterminado y cambiarla con reservas vigentes exige resolver primero el respaldo.

**Motivo**: el saldo libre debe calcularse por cuenta (`saldo confirmado − reservas activas`) para que dos metas no reserven el mismo dinero. La tabla actual `goal_events` no identifica esa cuenta.

**Alternativas consideradas**: derivar siempre la cuenta desde el movimiento relacionado; no basta porque las asignaciones sin movimiento no tienen transacción. Hacer obligatoria la cuenta enlazada en la meta; contradice la decisión aprobada de que es opcional.

**Nota de compatibilidad**: la columna de cuenta para eventos heredados podrá quedar nula durante una migración aditiva, pero los comandos de reservas nuevas deben exigir una cuenta de respaldo válida. La estrategia para eventos heredados sin cuenta se decidirá usando el preflight de datos, nunca por inferencia silenciosa.

### R3. Separar reserva de traslado

**Decisión**: un aporte reserve-only escribe evento de asignación, no movimiento ni asiento de ledger. Si el usuario elige trasladar fondos a otra cuenta propia de la misma moneda, aplicar el flujo existente de transferencia y evento de meta en una transacción atómica. Una liberación solo reduce la reserva. Una transferencia posterior a liberar es otra acción explícita.

**Motivo**: al cambiar una asignación no cambia la cantidad de dinero del usuario. Un traslado entre cuentas sí cambia sus saldos, pero no es ingreso ni gasto.

**Alternativas consideradas**: mantener `GOAL_FUNDING` como gasto y la liberación como ingreso; descartada por contradicción con la spec y Constitución. Enviar dinero a un banco; fuera del alcance aprobado.

### R4. Comandos transaccionales con idempotencia y conflicto explícito

**Decisión**: contrato de comandos para alta/edición/reserva/liberación/reasignación/archivo/borrado vacío. Cada operación tiene ID estable, hash de contenido, revisión esperada cuando modifica entidad existente y recibo. Repetir ID+hash devuelve el recibo anterior; ID con hash distinto devuelve conflicto. Eventos, transferencia opcional, recibo y feed se escriben en una transacción.

**Motivo**: corresponde al patrón robusto ya usado en deuda y cumple local-first/reintentos seguros. La ruta de recibos de metas encontrada no compara el hash de un reintento y debe revisarse.

**Alternativas consideradas**: confiar en reintentos de cliente o en una clave idempotente sin comparar el payload; ambas pueden esconder una reutilización incorrecta de identidad. “Última escritura gana”; descartada para operaciones financieras.

### R5. Serializar por cuenta de respaldo

**Decisión**: el servidor debe proteger la fila estable de cada cuenta de respaldo (o un agregado de bloqueo equivalente) mientras valida saldo y reservas y crea el evento. Si una operación usa varias cuentas, adquirir los bloqueos en un orden determinista. No basta bloquear solo la meta.

**Motivo**: dos metas distintas pueden intentar reservar simultáneamente el mismo saldo disponible. La transacción debe garantizar `reservas aceptadas ≤ saldo confirmado`.

**Alternativas consideradas**: validar únicamente en Android; descartada porque hay múltiples dispositivos, operaciones offline y concurrencia. Bloquear solo por meta; no evita que metas distintas consuman la misma cuenta.

### R6. Dominio Android local-first

**Decisión**: nuevo `feature/goals` con dominio puro, Room, repositorio offline-first, outbox y sincronización WorkManager; migración Room explícita de v21 a v22 y snapshot. La persistencia local de operación/evento/outbox es atómica.

**Motivo**: el proyecto es nativo Android; Room está en v21 y los patrones existentes de deudas proporcionan identidad estable, hashes, reintentos, revisiones y conflictos. Hoy no hay módulo local de metas.

**Alternativas consideradas**: tratar Supabase como fuente en línea para la UI; descartada por la arquitectura local-first. Modificar `feature/movements` para alojar metas; descartada porque metas tienen su propio ciclo de vida y dominio; solo se integra con movimientos en un traslado explícito.

### R7. Seguridad y SQL

**Decisión**: RLS owner-only en tablas expuestas; el servidor obtiene el propietario de la sesión y valida explícitamente propiedad de meta, cuentas y referencias dentro del comando. Restringir el CRUD directo que eluda revisión/eventos. Preferir `SECURITY INVOKER`; si un comando requiere `SECURITY DEFINER`, fijar `search_path`, restringir `EXECUTE` y revisar el dueño/privilegios.

**Motivo**: los archivos actuales ya tienen políticas RLS, pero un RPC privilegiado debe repetir comprobaciones de autorización. También existen grants directos y claves foráneas que requieren revisión. Las guías de Supabase recomiendan RLS y privilegios como capas separadas, y funciones de base de datos para operaciones con acceso intensivo a datos.

**Alternativas consideradas**: asumir que autenticación o RLS por sí solos autorizan cada referencia; descartada. Mantener escrituras CRUD directas para el cliente; descartada para cambios que requieren eventos, recibos y feed atómicos.

### R8. Historia y estados

**Decisión**: el estado de negocio se deriva de la reserva confirmada y el objetivo: alcanzar el objetivo marca `COMPLETED`; liberar por debajo del objetivo o subir el objetivo completado por encima de la reserva lo vuelve `ACTIVE`. Ninguna transición borra eventos. Archivar requiere reserva cero o reasignada; el borrado físico se reserva a metas vacías sin historia ni operaciones.

**Motivo**: refleja FR-015–FR-019 y la respuesta aprobada en aclaración: al aumentar el objetivo por encima de la reserva, la meta vuelve a Activa conservando reserva e historia.

**Alternativas consideradas**: resetear aportes al cambiar el objetivo o liberar automáticamente al completar; ambas contradicen requisitos aprobados.

### R9. Migración histórica como gate, no como suposición

**Decisión**: primero inspección remota read-only del estado de migraciones, permisos, filas y efectos de ledger. Luego informe; cualquier corrección de efectos históricos requiere decisión explícita y un ajuste auditable. La migración de esquema es versionada, aditiva y se valida con datos representativos antes de despliegue.

**Motivo**: los RPC existentes pueden haber creado gastos de asignación e ingresos de liberación. No se consultó una instancia remota y las migraciones locales no revelan el número ni estado de filas desplegadas. La Constitución prohíbe borrar/revertir historia en silencio.

**Alternativas consideradas**: revertir automáticamente todos los `GOAL_FUNDING`; descartada sin reconciliar cada hecho y sin aprobación. Suponer que el entorno remoto está al día con Git; descartada porque el repositorio no prueba ACL ni historial remoto.

## Hechos del repositorio

- `Money` usa `Long` de unidades menores y moneda; el modelo existente reconoce PEN/USD.
- `KipuDatabase` está en versión 21, con migraciones explícitas hasta `MIGRATION_20_21`; no hay entidades locales de metas.
- Las migraciones PostgreSQL son imperativas en `supabase/migrations/`; no existe `supabase/schemas/` declarativo.
- Existen `goals`, `goal_events`, `v_goal_summary`, `allocate_goal_funds`, `release_goal_funds` y un despacho de sync común.
- La asignación heredada con cuenta registra gasto; la liberación heredada con cuenta de destino registra ingreso. El evento no identifica respaldo. La liberación no actualiza feed/revisión como requiere el contrato.
- Existe una política RLS para metas, pero también grants CRUD directos que se deben conciliar con el nuevo flujo. Hay cascada que puede eliminar eventos al borrar meta.
- La ruta heredada de recibos no demuestra idempotencia completa frente al mismo operation ID con un hash distinto.
- El patrón de deudas es una referencia local adecuada para outbox, hash, revisión, conflicto y sync por cursor.
- No se inspeccionó una base Supabase remota. La existencia de efectos históricos, ACL desplegados y estado real de migraciones permanece sin verificar.

## Guías técnicas consultadas

- [Supabase: Database migrations](https://supabase.com/docs/guides/local-development/database-migrations) — migraciones versionadas, desarrollo local y despliegue controlado.
- [Supabase: Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security) — políticas RLS y privilegios para tablas expuestas.
- [Supabase: Database functions](https://supabase.com/docs/guides/database/functions) — RPC de base de datos y precauciones para funciones con privilegios.
- Skills locales consultadas: `android-kotlin`, `supabase` y `supabase-postgres-best-practices`.

## Referencias locales de producto y código

- `specs/006-ep-met-metas-reservas/spec.md` y `.specify/memory/constitution.md`.
- Product Backlog V4.2, cronograma Scrum Sprint 6, Arquitectura y Datos V4.2 y proceso P18 (gestionar metas y reservas), consultados en documentación de KipuApp/Obsidian Mind.
- `app/src/main/java/com/kipu/app/core/finance/domain/model/Money.kt`.
- `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`, `RoomMigrations.kt`, `core/di/CoreModule.kt` y `app/schemas/.../21.json`.
- `app/src/main/java/com/kipu/app/feature/debts/` como patrón offline-first.
- `supabase/migrations/20260920000000_financial_core_baseline.sql` y `20260920162500_align_domain_schema_names.sql` como base de metas heredada; `20260930025207_sprint_3_db_hardening.sql` para endurecimiento RLS/funciones.
- `supabase/migrations/20261008120000_s5_debt_lifecycle.sql` y `specs/013-ep-deu-deudas-prestamos/contracts/debt-command-contract.md` como patrón de comando con hash, revisión e idempotencia.

## Pendiente de decisión antes de despliegue

Tras obtener el preflight real, el responsable de producto/datos debe decidir el tratamiento de cualquier gasto/ingreso heredado relacionado con metas y aprobar el corte de clientes/outbox antiguos. Esta decisión no cambia las reglas futuras de la spec: reservas nuevas no son gastos; liberaciones nuevas no son ingresos. No hay base remota inspeccionada que permita decidir ahora el tamaño o la forma de esa conciliación.

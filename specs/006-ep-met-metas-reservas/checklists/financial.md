# Financial Requirements Quality Checklist: EP-MET

**Purpose**: Evaluar la integridad, claridad, consistencia y cobertura de los requisitos financieros de metas y reservas.
**Created**: 2026-10-17
**Feature**: [spec.md](../spec.md)

**Note**: Checklist personalizada generada a partir de la spec y el plan de EP-MET.
**Review Ownership**: Esta lista es un artefacto de revisión de calidad de requisitos. El revisor marca `[x]` solo cuando determina que el criterio está satisfecho en los requisitos.
**Marker Semantics**: `[x]` significa que el requisito fue revisado y está suficientemente definido; no significa que la implementación esté completa.

## Completitud de requisitos financieros

- [x] CHK001 ¿La spec aclara cómo se elige la cuenta de respaldo en cada aporte cuando la cuenta vinculada a la meta es opcional? [Ambiguity, Spec §FR-003, §FR-009]
  - *Resolución*: Resuelto en [spec.md §FR-003, §FR-009 y Clarificaciones](../spec.md#L20-L20). La cuenta vinculada actúa como respaldo predeterminado en el aporte; si no existe o se modifica, el usuario selecciona obligatoriamente una cuenta propia elegible de la misma moneda.
- [x] CHK002 ¿La spec define si una meta puede respaldarse con varias cuentas y cómo se agrega ese respaldo para el progreso? [Gap, Spec §FR-007–§FR-010]
  - *Resolución*: Resuelto en [spec.md §FR-002, §FR-010, §FR-015 y Clarificaciones](../spec.md#L19-L19) y [data-model.md](../data-model.md#L56-L61). Una meta puede acumular reservas de múltiples cuentas propias en la misma moneda. El progreso agrega todos los deltas válidos de la meta (`goal_reserved = sum(valid deltas) / target_minor`).
- [x] CHK003 ¿La spec define qué saldo cuenta como “confirmado” para calcular el respaldo libre, incluidos movimientos pendientes o anulados? [Clarity, Spec §FR-010, §FR-014]
  - *Resolución*: Resuelto en [spec.md §FR-010 y Clarificaciones](../spec.md#L21-L21). Se define como la suma neta acumulada de asientos firmes del ledger (`internal.ledger_entries`), excluyendo taxativamente transacciones pendientes/candidatas (`PENDING`), borradores y transacciones anuladas/compensadas (`VOIDED`).
- [x] CHK004 ¿La spec aclara si el saldo reservado puede superar el objetivo y cómo se refleja ese exceso en progreso y estado? [Clarity, Spec §FR-015–§FR-017]
  - *Resolución*: Resuelto en [spec.md §FR-015, §FR-016 y Escenario 3.1](../spec.md#L76-L76). Se permite sobreahorro (`reserved > target`); el importe reservado real nunca se trunca ni limita; el progreso muestra la proporción exacta o excedente y el estado permanece en `COMPLETED`.
- [x] CHK005 ¿La spec define el estado de una meta cuando se reduce el objetivo hasta un importe ya reservado o inferior? [Coverage, Gap, Spec §FR-006, §FR-015–§FR-017]
  - *Resolución*: Resuelto en [spec.md §FR-006, Clarificaciones y Escenario 1.5](../spec.md#L18-L18). Al reducir el objetivo a un valor `<= current_reserved`, la meta pasa inmediatamente a `COMPLETED` conservando su reserva y eventos íntegros sin mover fondos.

## Claridad y consistencia del ciclo de vida

- [x] CHK006 ¿Los requisitos distinguen con claridad la cuenta enlazada a la meta de la cuenta que respalda cada evento de reserva? [Clarity, Spec §FR-003, §FR-007, §FR-009]
  - *Resolución*: Resuelto en [spec.md §FR-003, §FR-009 y Entidades clave](../spec.md#L137-L138) y [data-model.md](../data-model.md#L25-L45). `linked_account_id` es un atributo organizativo opcional de la meta; `backing_account_id` es el atributo inmutable por evento de reserva que bloquea saldo contable en el ledger.
- [x] CHK007 ¿Las reglas de reasignación especifican el efecto sobre ambas metas, la cuenta de respaldo, el progreso y la historia cuando cambia o no cambia el respaldo? [Completeness, Spec §FR-019–§FR-020]
  - *Resolución*: Resuelto en [spec.md §FR-019, §FR-020 y Escenario 3.5](../spec.md#L80-L80) y [contrato de comandos](../contracts/goal-command-contract.md#L73-L78). Atómico: decremento en origen, incremento en destino, actualización de progreso y auditoría compartida; con mismo respaldo no crea ledger; con distinto respaldo requiere transferencia interna explícita previa.
- [x] CHK008 ¿Las reglas de archivo explican de forma consistente qué ocurre si hay reserva pendiente, si se reasigna, si se libera o si el usuario cancela el archivo? [Clarity, Spec §FR-019]
  - *Resolución*: Resuelto en [spec.md §FR-019 y Escenario 3.4](../spec.md#L79-L79). Detalla las tres alternativas: (a) liberar reservas a saldo libre y archivar, (b) reasignar reservas a otra meta activa y archivar al llegar a cero, o (c) cancelar el archivo manteniendo estado y reservas vigentes.
- [x] CHK009 ¿Los requisitos de eliminación delimitan qué cuenta como operación relacionada, incluidos eventos locales aún no sincronizados? [Ambiguity, Spec §FR-008, §FR-021]
  - *Resolución*: Resuelto en [spec.md §FR-008, Escenarios 1.6–1.7](../spec.md#L41-L42) y [contrato](../contracts/goal-command-contract.md#L83-L84). Se delimita que cualquier evento persistido, transferencia vinculada o comando en outbox local bloquea el borrado (`REJECTED/HISTORY_PRESERVED`). Solo se eliminan físicamente metas vacías (0 eventos, 0 transferencias, 0 outbox).
- [x] CHK010 ¿La política para quien pierde acceso Premium o supera el límite de metas Free explica cómo conserva y administra las metas existentes? [Completeness, Spec §FR-005]
  - *Resolución*: Resuelto en [spec.md §FR-005 y Escenario 1.8](../spec.md#L43-L43) conforme a P31 y HU-57. Las metas excedentes pasan a `LOCKED_BY_PLAN`, permanecen visibles, consultables y con reservas intactas; se restringen solo para nuevos aportes y se reactivan automáticamente al recuperar Premium o liberar cupo.

## Criterios y escenarios medibles

- [x] CHK011 ¿Los criterios permiten medir el saldo libre por cuenta y evitar que varias metas reserven simultáneamente más que el respaldo disponible? [Measurability, Spec §FR-010–§FR-011, §SC-003]
  - *Resolución*: Resuelto en [spec.md §FR-010, §FR-011, §SC-003](../spec.md#L111-L114) y [data-model.md](../data-model.md#L98-L99). Define la fórmula medible `free_backing(account) = confirmed_balance(account) - sum(active_reserved)` y la invariante garantizada por bloqueo serializado por fila en la cuenta de respaldo (`FOR UPDATE`).
- [x] CHK012 ¿La atomicidad de una transferencia interna y su aporte define expresamente que ambos efectos se confirman o se rechazan juntos? [Clarity, Spec §FR-013, §FR-021]
  - *Resolución*: Resuelto en [spec.md §FR-013 y Escenario 2.3](../spec.md#L59-L60). Se exige confirmación indivisible en una sola transacción de BD: los dos asientos de la transferencia y el evento de reserva se confirman o revierten juntos.
- [x] CHK013 ¿Los requisitos distinguen el reintento idéntico de la reutilización de un mismo identificador con contenido diferente y definen el resultado de ambos? [Gap, Spec §FR-021, §SC-006]
  - *Resolución*: Resuelto en [spec.md §FR-021, §SC-006](../spec.md#L126-L130) y [contrato](../contracts/goal-command-contract.md#L91-L93). Reintento con idéntico hash devuelve recibo previo (`DUPLICATE`) sin mutaciones; reutilización de operation ID con hash distinto devuelve conflicto determinista (`CONFLICT/OPERATION_ID_REUSED`).
- [x] CHK014 ¿La spec explica cómo identificar y resolver un faltante cuando un gasto confirmado deja varias metas con respaldo insuficiente? [Coverage, Ambiguity, Spec §FR-014]
  - *Resolución*: Resuelto en [spec.md §FR-014, Clarificaciones y Escenario 2.6](../spec.md#L22-L22). Identificación por `confirmed_balance < active_reserved` con alerta en cuenta y metas; resolución exclusivamente guiada al usuario (fondear cuenta, liberar reserva o reasignar reserva), sin deducciones automáticas arbitrarias.

## Seguridad, sincronización y dependencias

- [x] CHK015 ¿Los requisitos expresan de forma trazable el aislamiento por propietario para metas, eventos, cuentas y transferencias relacionadas? [Completeness, Spec §FR-009, §FR-013, Constitution §III]
  - *Resolución*: Resuelto en [spec.md §FR-001, §FR-009, §FR-013, §FR-021](../spec.md#L102-L102) y [contrato](../contracts/goal-command-contract.md#L7-L11). Todas las referencias y mutaciones se validan estrictamente contra `user_id = auth.uid()` bajo el Principio III de la Constitución; accesos cruzados responden `REJECTED/OWNER_OR_ACCOUNT_INVALID`.
- [x] CHK016 ¿La spec define qué ve y qué puede hacer el usuario cuando una operación guardada localmente es luego rechazada por saldo, límite o revisión durante la sincronización? [Recovery, Gap, Spec §FR-021]
  - *Resolución*: Resuelto en [spec.md §FR-021 y Escenario 3.7](../spec.md#L82-L82). La UI expone una insignia de conflicto de sincronización con el motivo y ofrece tres opciones de recuperación: descartar la mutación local, ajustar y reintentar, o resolver la condición previa.
- [x] CHK017 ¿Los requisitos de compatibilidad indican cómo preservar eventos heredados sin cuenta de respaldo y los gastos/ingresos ya asociados antes de migrar? [Dependency, Gap, Plan §Preparación de base de datos]
  - *Resolución*: Resuelto en [spec.md §FR-022, §SC-008](../spec.md#L131-L131), [data-model.md](../data-model.md#L108-L112) y [plan.md](../plan.md#L85-L93). Migración estrictamente aditiva (`backing_account_id` nullable para filas previas `LEGACY_UNLINKED`), 100% de datos históricos preservados en modo solo lectura, y prohibición de correcciones destructivas sin auditoría ni aprobación previa.

## Notes

- Marcar un ítem `[x]` solo después de revisar la calidad del requisito correspondiente.
- Dejar sin marcar los ítems que necesiten aclaración, ajuste o evaluación del revisor.
- Esta lista examina requisitos escritos; no es una lista de pruebas de implementación.
- `/speckit.implement` puede leer el estado de la checklist como gate y no debe cambiar sus marcas.
- `checklists/requirements.md` es una lista integrada separada, mantenida por `/speckit.specify` y `/speckit.clarify`.
- Anotar hallazgos o decisiones junto al ítem correspondiente y enlazar la sección que los resuelva.

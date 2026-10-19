# Consistency Checklist: EP-REC — Recurrencias y obligaciones

**Purpose**: Revisar la calidad, claridad y consistencia de los requisitos funcionales y decisiones aprobadas de EP-REC.
**Created**: 2026-10-18
**Feature**: [spec.md](../spec.md)

**Note**: Checklist de calidad de requisitos, no de comportamiento ejecutado.
**Review Ownership**: Solo el reviewer marca `[x]` cuando confirma que el criterio de calidad está satisfecho.
**Marker Semantics**: `[x]` valida redacción/requisitos; no significa implementación terminada.

## Requirement Completeness

- [ ] CHK001 ¿La especificación delimita con claridad las HU de S6 incluidas y las HU-34/HU-40/HU-44 excluidas como implementación? [Coverage, Spec §Scope & Traceability]
- [ ] CHK002 ¿Cada requisito FR-001..FR-011 se puede rastrear a una HU, principio o criterio de aceptación verificable? [Traceability, Spec §Requirements]
- [ ] CHK003 ¿La especificación refleja que T001 aceptó una dependencia parcial de contrato de HU-18 no bloqueante para HU-33/HU-35, sin presentarla como evidencia de que HU-18 está íntegramente completada? [Decision/scope, Spec §Scope & Traceability, DP-REC-06]
- [ ] CHK004 ¿La separación entre recurrencia/estimación y pago/transacción real queda consistente en todos los escenarios y criterios? [Consistency, Spec §FR-001..FR-003, SC-001]

## Requirement Clarity

- [ ] CHK005 ¿La política exige mostrar y confirmar explícitamente la primera fecha futura válida antes de persistir `ACTIVE`, y evita crear gastos/ocurrencias de periodos omitidos? [Decision, Spec §FR-008, DP-REC-02]
- [ ] CHK006 ¿La especificación refleja que `end_date` es opcional dentro de S6 y que, si existe, `end_date >= start_date`? [Decision, Spec §Edge Cases, DP-REC-03]
- [ ] CHK007 ¿La especificación exige un entero no nulo en el comando, default cliente 2 antes del hash, rechazo de omitido/`null` y rango inclusivo 0–30, sin atribuir entrega/deduplicación a EP-REC? [Decision/boundary, Spec §FR-002, DP-REC-05]
- [ ] CHK008 ¿La frecuencia personalizada fija unidad, intervalo válido y ajuste de fin de mes de forma coherente entre HU-33 y P19? [Clarity, Spec §Assumptions, Edge Cases]
- [ ] CHK009 ¿El contrato distingue `status` de `deleted_at`, proyecta `is_active` como `ACTIVE=true` y `PAUSED`/`CANCELLED=false`, y retiene/retiro según inventario autorizado y migración posterior revisada? [Decision/gate, Plan §DP-REC-01]
- [ ] CHK010 ¿La especificación deja claro que CANCELLED es terminal y que reutilizar su configuración requiere crear/clonar otra regla? [Decision, Spec §FR-006, DP-REC-04]

## Requirement Consistency

- [ ] CHK011 ¿Los requisitos de pausa/cancelación y el límite de HU-44 son coherentes sin atribuir entrega/deduplicación a EP-REC? [Consistency, Spec §FR-007, contracts/recurrence-notification-boundary.md]
- [ ] CHK012 ¿Los estados de ciclo, `revision`, `deleted_at` y la conservación de historia tienen semántica no contradictoria entre spec, modelo y contrato? [Consistency, Spec §FR-006/FR-010, data-model.md]
- [ ] CHK013 ¿La canonicalización numérica usa el valor semántico JSONB (decimal exacto sin exponente, ceros finales eliminados), valida integralidad/rango por campo antes de hash acceptance y cubre entero, fracción, exponente entero equivalente y exponente no entero con vectores/digests y gate de paridad Kotlin/SQL? [Consistency, Spec §TECH-REC-HASH-01, Contract §3]
- [ ] CHK014 ¿La frontera `jsonb` declara que las claves duplicadas del texto original colapsan antes del handler, no promete rechazo lexical y exige probar el objeto superviviente en SQL más salida sin miembros duplicados en Kotlin? [Consistency, Spec §TECH-REC-JSON-01, Contract §2/§3]

## Notes

- Mantener sin marcar hasta que el reviewer examine los requisitos y las respuestas aprobadas. Los gates externos que persisten son inventario hosted autorizado y definición EP-NOT para entrega con anticipación 0; no reabrir las decisiones de producto DP-REC-01/02/05.
- Esta lista evalúa calidad de requisitos, no el resultado de pruebas ni el estado del código.
- El reporte histórico de HU-18 (2026-09-28) no sustituye evidencia contra el HEAD actual.

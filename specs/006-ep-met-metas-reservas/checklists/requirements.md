# Lista de Calidad de Requisitos: EP-MET — Metas y Reservas Virtuales

**Propósito**: Revisar la trazabilidad, claridad, integridad y facilidad de prueba de la especificación inicial para HU-30/HU-31/HU-32.
**Created**: 2026-10-17
**Feature**: [spec.md](../spec.md)

**Review Ownership**: Mark an item `[x]` only when the requirement-quality criterion is satisfied. This checklist does not indicate implementation completion.

## Scope and traceability

- [x] CHK001 The specification identifies EP-MET and includes only HU-30, HU-31 and HU-32.
- [x] CHK002 Each story's owner, dependencies and Sprint 6 ordering are traceable to the backlog and roadmap.
- [x] CHK003 HU-24's related EP-MOV scope is identified without absorbing it into EP-MET.
- [x] CHK004 The Free allowance counts only `ACTIVE` goals (not `COMPLETED`) and preserves goals, reserves, and history under plan restrictions, matching HU-57.

## Requirement quality

- [x] CHK005 The optional real-account link and the virtual-reserve mode are both explicit and testable.
- [x] CHK006 Reserve, transfer, release, completion and net-worth effects are stated without double-counting.
- [x] CHK007 Insufficient backing, same-account allocation, later expenses and excessive release have defined outcomes.
- [x] CHK008 Completion, archive, reassignment and deletion preserve the approved financial history rules.
- [x] CHK009 Acceptance scenarios cover normal/failure paths for all stories, including COMPLETED exemption and atomic reactivation at the Free active-goal limit.
- [x] CHK010 Financial effects and retry behavior are measurable and do not prescribe implementation technology.
- [x] CHK011 The clarification marker is limited to the lifecycle result of increasing a completed goal's target.
- [x] CHK012 The completed-goal target-edit lifecycle is decided and the clarification marker is removed.
- [x] CHK013 The Free cap defines ACTIVE-only counting, COMPLETED exemption, downgrade selection, and atomic reactivation at capacity for target edits and releases.

## Notes

- P18 resolves the backlog's “reject or request correction” wording for insufficient backing: reject the whole contribution without a partial effect.
- FR-017 is clarified: if a completed goal's target is raised above its reserve, it returns to Active and keeps its reserve and event history.
- Product decision 2026-10-18: only `ACTIVE` consumes Free quota; at capacity, reactivation requires explicit selection of an active goal to lock and an atomic replacement.

# Requirements Quality Review: EP-PRE — Presupuestos y Consumos

**Purpose**: Checklist propia para revisión independiente de calidad de requisitos, no verificación de implementación.<br>
**Created**: 2026-10-24<br>
**Feature**: [spec.md](../spec.md)
**Review Ownership**: reviewer-owned. El autor no marca los checks; `[x]` solo significa que un reviewer examinó y satisfizo el criterio de calidad, no que el código esté completo.

## Completitud

- [ ] CHK001 ¿Los límites de duración/retención de periodos cerrados y alertas están definidos donde el producto los requiere? [Gap, Spec §Assumptions and Gates]
- [ ] CHK002 ¿La experiencia de edición de nombre y selección del alcance está suficientemente definida para el producto? [Completeness, Spec §FR-001/FR-013]
- [ ] CHK003 ¿El criterio de moneda para presupuestos multi-currency está alineado con la regla de no conversión y claramente expresado para el usuario? [Completeness, Spec §FR-003/Edge Cases]

## Claridad

- [ ] CHK004 ¿El tratamiento de porcentaje/rounding en límites exactos y sobreconsumo queda inequívoco? [Ambiguity, Spec §FR-003/SC-002]
- [ ] CHK005 ¿La regla que define el inicio del periodo y la fecha civil para DAILY/WEEKLY/BIWEEKLY/MONTHLY/CUSTOM_RANGE se aprueba sin ambigüedad? [Ambiguity, Spec §Assumptions and Gates — G1]
- [ ] CHK006 ¿Qué configura exactamente un threshold editable y cómo se distingue un cruce descendente de uno ascendente está especificado? [Ambiguity, Spec §FR-010]

## Consistencia

- [ ] CHK007 ¿La representación física aprobada reconcilia los periodos daily/biweekly/custom range con el enum global y mantiene el alcance de HU-36? [Conflict, Spec §Assumptions and Gates]
- [ ] CHK008 ¿La eliminación funcional de budget/periodos es compatible con sync retry/tombstones sin ampliar retención de historia? [Conflict, Spec §FR-015/Assumptions and Gates]
- [ ] CHK009 ¿La definición del periodo origen del reembolso concuerda con contrato HU-25 para reembolso entre ciclos y reembolso parcial? [Dependency, Spec §FR-012/SC-005]

## Criterios de aceptación

- [ ] CHK010 ¿Los valores del cálculo mostrado (porcentaje y restante) tienen política explícita para consumo mayor que el límite, denominador/currency y redondeo? [Measurability, Spec §FR-003/SC-002]
- [ ] CHK011 ¿La aceptación de consumo neto detalla que un mismo reembolso repetido/reordenado no resta dos veces en el periodo de origen? [Coverage, Spec §FR-012/SC-005]
- [ ] CHK012 ¿La regla editable de alertas delimita inequívocamente “un aviso por cruce/periodo/umbral” con configuración cambiada durante el ciclo? [Clarity, Spec §FR-010/SC-006]

## Cobertura de escenarios extremos

- [ ] CHK013 ¿Se especifica comportamiento cuando la categoría de un presupuesto se archiva o se elimina mientras tiene historia? [Gap, Spec §Edge Cases]
- [ ] CHK014 ¿Se especifica una zona horaria/cambio de calendario que cruza medianoche o cambia de dispositivo para periodos diarios/semanales? [Gap, Spec §Assumptions and Gates — G1]
- [ ] CHK015 ¿Se cubre conflicto entre edición offline simultánea en dos dispositivos y reintento de renovación? [Coverage, Spec §FR-018]
- [ ] CHK016 ¿Se define qué ocurre con avisos ya generados cuando se modifica threshold o se finaliza/elimina presupuesto? [Gap, Spec §FR-010/FR-014/FR-015]

## Seguridad y atributos de calidad

- [ ] CHK017 ¿La regla de privacidad define nivel mínimo de datos permitidos en logs/telemetría para consumo y alertas? [Coverage, Spec §Security/privacy context]
- [ ] CHK018 ¿Se definen objetivos observables de latencia/volumen tras conocer carga y entorno reales, evitando targets inventados? [Deferred, Spec §Assumptions and Gates]

## Dependencias y supuestos

- [ ] CHK019 ¿La disponibilidad real de HU-14/HU-18/HU-19/HU-57 se demuestra antes de aceptación y no se infiere por documentación? [Dependency, Spec §Assumptions and Gates]
- [ ] CHK020 ¿El contrato real de HU-25 y la consulta de consumo se revisan con EP-MOV y se ejercitan sin mock para aceptación integrada? [Dependency, Spec §FR-012/SC-005]
- [ ] CHK021 ¿La interface de alertas y deduplicación se confirma con HU-42/EP-NOT, diferenciándola del alcance futuro HU-43? [Dependency, Spec §Assumptions and Gates]
- [ ] CHK022 ¿El schema Room/PostgreSQL y la situación de deployment se verifican separadamente antes de cambiar migraciones? [Assumption, Spec §Assumptions and Gates]

## Ambigüedades y conflictos

- [ ] CHK023 ¿Todos los gates G1–G5 tienen decisión aprobada y trazable antes de iniciar implementación? [Gate, Spec §Assumptions and Gates]
- [ ] CHK024 ¿Los criterios de exactitud de la consulta garantizan estado vigente de gasto revisado/anulado y rechazo seguro en lectura inconsistente? [Coverage, Spec §FR-004/SC-009]

## Notes

- El autor deja todos los elementos sin marcar para revisión independiente, según ownership.
- Los checks evalúan requisitos, supuestos y consistencia; no verifican que la implementación funcione.
- No resolver gaps mediante inferencias: obtener aprobación de producto/arquitectura y propagar por flujo Spec Kit.

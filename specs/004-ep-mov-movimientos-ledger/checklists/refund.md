# Requirements Checklist: HU-25 Reembolsos vinculados

**Purpose**: Evaluar si los requisitos de HU-25 son inequívocos y completos antes de iniciar su implementación.
**Created**: 2026-10-21
**Feature**: [spec.md](../spec.md), User Story 8 / FR-023–FR-025

**Note**: Checklist de calidad de requisitos para Sprint 7.
**Review Ownership**: Artefacto de revisión del equipo; sólo el revisor marca `[x]` cuando el criterio de calidad del requisito está satisfecho.
**Marker Semantics**: `[x]` acredita revisión de calidad del requisito, no implementación ni ejecución de pruebas.

## Completeness and scope

- [ ] CHK001 ¿La spec define qué estados y clases de compra son elegibles para reembolso, incluyendo compras corregidas/anuladas, tarjeta, cuotas y relaciones especializadas? [Gap, FR-023]
- [ ] CHK002 ¿Está especificado qué cuenta o instrumento recibe la devolución y cuándo el usuario puede elegir uno distinto al de la compra? [Gap, HU-25/AC1]

## Accounting and concurrency

- [ ] CHK003 ¿Está explícito que la entrada de caja por reembolso no se presenta como ingreso operativo y cómo reduce el gasto neto original sin contarla dos veces? [Clarity, FR-023–FR-025]
- [ ] CHK004 ¿La regla de saldo pendiente define el resultado observable de reembolsos concurrentes/offline cuando otro dispositivo consume el remanente antes de la aceptación remota? [Gap, FR-024, Edge Case]

## Integration and failure behavior

- [ ] CHK005 ¿El vínculo canónico y la propiedad de ambas transacciones quedan definidos sin ambigüedad entre `transaction_links` y `refund_of_transaction_id`? [Consistency, FR-023]
- [ ] CHK006 ¿El contrato real de EP-PRE/HU-36 especifica cómo se corrige el periodo original y cómo se reportan reintentos, duplicados y rechazos? [Dependency, FR-025]
- [ ] CHK007 ¿Los requisitos describen el resultado visible de un reintento idéntico, una misma clave con contenido distinto y un rechazo definitivo offline? [Coverage, FR-024, FR-026]

## Notes

- Los siete elementos quedan sin evaluar por el revisor; no son pruebas de implementación.
- Las decisiones de producto no se infieren del esquema local ni de una fixture.
- La aceptación integrada requiere contrato real de HU-36, no un stub.

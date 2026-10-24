# Requirements Quality Checklist: EP-INF

**Purpose**: Checklist integrado de calidad del spec (estado del ciclo de vida mantenido por Specify/Clarify).
**Creado**: 2026-10-24
**Feature**: [spec.md](../spec.md)

## Claridad y alcance

- [x] CHK001 Las tres historias conservan los IDs, títulos, prioridad, story points y sprint de cierre de backlog.
- [x] CHK002 El alcance S7 se identifica como HU-39/40 (13 pts) y HU-41/S9 (8 pts) permanece incremento futuro separado.
- [x] CHK003 Las dependencias bloqueantes, parciales y relacionadas se distinguen según trazabilidad fuente.
- [x] CHK004 Ningún requisito transforma consulta de evento previsto en pago o movimiento real.
- [x] CHK005 Inicio e historial Free se distinguen explícitamente de analítica Premium histórica.
- [ ] CHK006 GATE-DESIGN-01 requiere autoridad visual aprobada antes de implementar (no resuelto por el autor).
- [ ] CHK007 GATE-PRODUCT-02 define comportamiento de dato desconocido, widgets, colisiones y origen no disponible con Product Owner (pendiente).

## Reglas financieras

- [x] CHK008 Fórmula de patrimonio coincide con constitución y backlog.
- [x] CHK009 Transferencias, reservas, crédito disponible y principal no se duplican como métricas operativas/activos.
- [x] CHK010 Reembolsos y anulaciones remiten a sus reglas de origen, sin definición contradictoria nueva.
- [x] CHK011 Casos numéricos se limitan a fixtures ya incluidos en backlog.

## Calidad medible y privacidad

- [x] CHK012 Cada story incluye escenarios verificables trazables a aceptación fuente.
- [x] CHK013 SC están verificables con fixtures, sin inventar KPI comercial ni declarar mediciones.
- [x] CHK014 Free, entitlement efectivo Premium y acceso a historia tienen límites expresos.
- [x] CHK015 Se declaran límites de propietario, máscara y minimización de datos.
- [ ] CHK016 GATE-ARCH-03 y GATE-ANALYTICS-04 requieren contratos/decisiones de propietarios antes de fijar métricas o endpoints.

## Notas

Checks [x] reflejan cotejo documental con las fuentes locales citadas, no revisión independiente ni verificación de implementación. Checks pendientes requieren decisión de autoridad responsable. No declarar revisión de autor para ellos.

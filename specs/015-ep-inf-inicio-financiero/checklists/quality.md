# Checklist: Calidad de requisitos — EP-INF

**Propósito**: Revisión independiente de completitud, consistencia, verificabilidad y gates de producto/arquitectura.
**Creada**: 2026-10-24
**Feature**: [spec.md](../spec.md)
**Review Ownership**: reviewer-owned; el autor no marca criterios como revisados.
**Marker Semantics**: `[x]` significa revisión externa del criterio satisfecha, no implementación completa.

## Completitud y trazabilidad

- [ ] CHK001 Reviewer confirma que cada criterio Gherkin de HU-39/40/41 está representado sin cambiar intención.
- [ ] CHK002 Reviewer confirma identificadores, prioridades, puntos, sprints y tipos de dependencia contra backlog y matriz.
- [ ] CHK003 Reviewer confirma cobertura uno-a-uno de FR/SC por stories y tasks, y resolución de cualquier criterio omitido.
- [ ] CHK004 Reviewer confirma separación del incremento de S7 y fase futura HU-41/S9 en todas las secciones.

## Verificabilidad y semántica

- [ ] CHK005 Product Owner define y aprueba estados diferenciados para cero confirmado, vacío y fuente desconocida.
- [ ] CHK006 Product Owner aprueba navegación/resultado cuando evento de calendario comparte fecha o su objeto fuente no existe.
- [ ] CHK007 Reviewer confirma que todos los SC se validan sin métricas inventadas y que los fixtures coinciden con fuente.
- [ ] CHK008 Owner de analítica aprueba definiciones, divisas, periodo parcial, base cero, filtros y porcentajes de HU-41.

## Arquitectura, privacidad y producto

- [ ] CHK009 Android-core confirma fuentes/repositorios/casos de uso existentes y contratos de UiState/eventos para patrimonio, historial y calendario.
- [ ] CHK010 Product/design resuelve conflicto Andean Modernist vs Calm Emerald por pantalla o unificación.
- [ ] CHK011 EP-PLA/android-core confirma entitlement efectivo y comportamiento Free/Premium, online/offline y enlaces directos.
- [ ] CHK012 Security/arquitectura confirma límites de sesión, aislamiento, logs, telemetría y máscara de privacidad.
- [ ] CHK013 Reviewer verifica que no se propongan API, tablas, migraciones o cálculos en UI sin fuente/decisión.

## Modelo e integración

- [ ] CHK014 Owners de deuda, tarjetas, recurrencias y metas confirman referencias y deep links correctos de eventos.
- [ ] CHK015 Reviewer confirma invariantes de patrimonio, transferencias, reservas, línea y principal frente a Constitución.
- [ ] CHK016 Reviewer confirma que tareas tienen paths reales, orden ejecutable y DAG acíclico; no hay tareas S9 como requisito para S7.

## Notas

No marcar checks durante autoría. Pendientes son gates reales, no afirmación de defecto de implementación. La aprobación de checklist no afirma que código exista o haya sido ejecutado.

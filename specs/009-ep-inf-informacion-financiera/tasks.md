# Tareas previstas: EP-INF — Inicio e Información Financiera

**Entrada**: `spec.md`, `plan.md`, `research.md`, `data-model.md`.
**Estado**: descomposición documental para implementación futura; todas las casillas quedan sin marcar. No ejecutar en esta tarea.

## Fase 1 — Clarificación y fundamento (bloquea historias)

- [ ] T001 [P] Resolver GATE-DESIGN-01 con autoridad visual y registrar decisión referenciada desde `specs/009-ep-inf-informacion-financiera/spec.md`.
- [ ] T002 Confirmar con android-core contratos reales de datos/UiState/eventos, fuentes locales y propietario de cada consulta de patrimonio, movimiento, deuda, tarjeta, recurrencia y meta; documentar paths existentes y no crear duplicados.
- [ ] T003 Confirmar estados Empty/Zero/Unknown/Error y semántica de datos faltantes; ajustar spec/modelo únicamente con aprobación.
- [ ] T004 [P] Definir y revisar en contrato de navegación las rutas existentes desde calendario a los objetos (incluido origen no disponible) con propietarios correspondientes.
- [ ] T005 Confirmar modelo de entitlement efectivo y capacidades Free/Premium con EP-PLA/android-core antes de HU-41.
- [ ] T006 Validar dependencias upstream: HU-07/18/19/22 y HU-09/29/33; HU-36/26/30/58 y HU-25 para puntos de integración indicados.

## Fase 2 — HU-39 Inicio e historial (S7, P1)

**Goal**: Presentar patrimonio e historial existentes con acceso Free, privacidad y estados explícitos, preservando invariantes del dominio.
**Independent Test**: Validar los escenarios HU-39 1–5 y FR-001–FR-005/FR-012–FR-014 con fixtures y contratos aprobados, sin requerir calendario ni analítica.

- [ ] T007 [P] [US1] Añadir pruebas de dominio/consulta para patrimonio con assets, receivable, liabilities, reserve y credit capacity del escenario FR-001/SC-001, en paths de test existentes tras inspección.
- [ ] T008 [US1] Implementar/reutilizar consulta de resumen patrimonial en caso de uso de dominio existente; mantener fórmula y moneda aprobadas, sin cálculo autoritativo en presentación.
- [ ] T009 [US1] Extender UiState/ViewModel de Inicio solo si contrato existente no cubre total/estados; cubrir loading, content, empty, unknown, error y retry.
- [ ] T010 [US1] Implementar presentación Inicio según sistema visual aprobado, con máscara de importes y semántica accesible.
- [ ] T011 [P] [US1] Integrar historial paginado/filtrado por periodo vía caso de uso existente; Free conserva consulta de toda la historia accesible.
- [ ] T012 [US1] Implementar estado vacío y navegación hacia registro; mantener lectura/corrección de movimiento desde historial.
- [ ] T013 [US1] Verificar comportamiento de reembolsos/anulaciones con dominio de HU-25/HU-21 sin redefinir reglas en UI.
- [ ] T014 [US1] Validar HU-39 offline, aislamiento por propietario, máscara y fixtures según quickstart.

## Fase 3 — HU-40 Calendario (S7, P1)

**Goal**: Exponer eventos financieros realizados y previstos como elementos independientes con rutas a objetos existentes, sin mutaciones financieras.
**Independent Test**: Validar HU-40 1–5 y FR-006–FR-008 con eventos coincidentes y navegación, independiente de HU-39 y HU-41.

- [ ] T015 [P] [US2] Definir/adaptar modelo de evento de lectura sobre fuentes existentes sin persistir eventos duplicados ni API inventada.
- [ ] T016 [US2] Agregar pruebas de combinación de evento real/previsto y dos eventos concurrentes el mismo día.
- [ ] T017 [US2] Implementar consulta mensual local combinando fuentes confirmadas de HU-09/29/33 con tipos/fechas explícitos.
- [ ] T018 [US2] Presentar navegación mensual/pasada Free, distinción accesible de realizado/previsto y estados de carga/vacío/error.
- [ ] T019 [US2] Conectar cada evento a ruta existente de su objeto fuente; comprobar cuota/deuda/tarjeta/recurrencia y estado de origen no disponible.
- [ ] T020 [US2] Asegurar que abrir/consultar eventos no ejecuta pagos ni crea movimientos; comprobar accesibilidad y duplicados visuales.

## Fase 4 — HU-41 Analítica (S9 futura; no parte de entrega S7)

**Goal**: Añadir analítica S9 sujeta a definición aprobada de métricas y entitlement, sin alterar historial básico Free ni contabilización financiera.
**Independent Test**: Validar HU-41 1–6 y FR-009–FR-011 con golden fixtures, escenarios de base cero/periodo parcial y capacidades efectivas; fuera de la aceptación S7.

- [ ] T021 [US3] Resolver GATE-ANALYTICS-04: catálogo de métricas, moneda, periodos, comparación y reglas de filtrado; registrar aprobación antes de codificar.
- [ ] T022 [P] [US3] Resolver acceso Premium efectivo/Free y filtros con HU-58/EP-PLA, incluyendo expiración/offline.
- [ ] T023 [US3] Añadir pruebas de dominio para excluir transferencias, principal, reservas y anulaciones y para tratar reembolsos según contrato.
- [ ] T024 [US3] Implementar resumen básico Free del periodo actual y estados base cero/desconocido/periodo parcial.
- [ ] T025 [US3] Implementar únicamente capacidades Premium aprobadas; conservar historia básica y validar deep links/entrada directa contra entitlement.
- [ ] T026 [US3] Comparar resultados con golden fixtures y verificar fechas, diferencias absolutas/porcentuales y privacidad.

## Fase 5 — Integración y aceptación

- [ ] T027 [P] Pruebas de accesibilidad (TalkBack, contenido/agrupación semántica, escala de texto) para tres journeys.
- [ ] T028 Pruebas de integración navegación y errores/reintentos con sesión/propietario.
- [ ] T029 Ejecutar verificación técnica y funcional aprobada para la implementación futura, registrar resultados reales y revisión cruzada.
- [ ] T030 Actualizar trazabilidad/documents si una decisión modifica requisitos, modelo o política, con autorización y alcance correspondiente.

## Dependencias y orden (DAG)

`T001–T006` preceden cualquier implementación; T002/T003/T004/T005/T006 pueden avanzar en paralelo como descubrimiento, pero cada gate correspondiente bloquea sus tareas de story. Para HU-39: T007–T008 preceden T009–T010; T011 puede avanzar en paralelo a T007/T008 luego de T002/T006; T012 depende T011; T013 depende T008/T011; T014 depende T010/T012/T013. Para HU-40: T015 depende T002/T004/T006; T016 depende T015; T017 depende T015; T018 depende T017; T019 depende T004/T017; T020 depende T018/T019. HU-41: T021/T022 y dependencias de S9 anteceden T023–T026; T023 antecede T024/T025; T026 depende T024/T025. T027/T028 integran tras stories correspondientes; T029/T030 al final. No existen dependencias de vuelta; grafo acíclico. Las tareas de HU-41 no son prerequisitos para la entrega S7.

## Paths

Los paths de código son deliberadamente no fijados cuando falta inspección de símbolos/contratos de repositorios y rutas. Los paths documentales de decisiones son los archivos bajo `specs/009-ep-inf-informacion-financiera/`. En fase de implementación, cada tarea debe concretar la ruta real existente antes de editar y no crear un módulo por conjetura.

## Matriz explícita de cobertura

| Requisito/criterio | Tareas que lo cubren |
|---|---|
| FR-001, FR-002, SC-001 | T002, T007, T008, T009, T014 |
| FR-003, FR-004, SC-004 | T003, T009, T011, T012, T014 |
| FR-005 | T010, T014, T027 |
| FR-006, FR-007, SC-003 | T004, T015, T016, T017, T019, T020 |
| FR-008 | T018, T020 |
| FR-009, SC-002 | T013, T023, T026 |
| FR-010 | T005, T022, T024, T025 |
| FR-011, FR-012 | T003, T021, T023, T024, T026 |
| FR-013 | T002, T014, T022, T028 |
| FR-014 | T002, T009, T010, T028 |
| SC-005 | T029, T030 |

Las historias están cubiertas por sus fases HU-39 (T007–T014), HU-40 (T015–T020) y HU-41 (T021–T026). T001–T006 son gates de preparación comunes; T027–T030 son aceptación transversal. Tasks FR-001/SC-001 referencian casos de dominio existentes por inspeccionar; sus rutas concretas no se inventan antes de verificar repositorios.

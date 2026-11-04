# Tareas previstas: EP-INF — Inicio e Información Financiera

**Entrada**: `spec.md`, `plan.md`, `research.md`, `data-model.md`.
**Estado**: S7 (HU-39/HU-40) y S9 (HU-41) implementadas en código (lógica y UI de analítica en pestaña Análisis). Limitaciones S9: solo meses completos (sin rango personalizado), comparación por totales por moneda (sin desglose por categoría), T021 y T027–T030 abiertas, AnalyticsScreenTest: 6/6 por teléfono en la re-verificación (QA-S9-02 resuelto); cobertura focalizada adicional S24 (16/16 instrumentadas, 11/11 JVM sobre Room actual v37) en verification-report.md, aceptación transversal parcial.

> Nota histórica 2026-10-24: T014 queda abierta porque la validación offline/propietario/máscara según quickstart no se ejecutó en dispositivo; T027 y T028 quedan abiertas porque las pruebas `androidTest` (`CalendarAndHomeAccessibilityTest`) compilan pero no se ejecutaron (sin dispositivo) ni se hizo la revisión con TalkBack. T011/T012/T013 se cerraron por verificación del historial existente (`MovementHistoryScreen`, estado vacío con CTA «Registrar primer movimiento»; consulta básica abierta a Free) y de que Inicio no redefine reglas de reembolso.

## Fase 1 — Clarificación y fundamento (bloquea historias)

- [X] T001 [P] Resolver GATE-DESIGN-01 con autoridad visual y registrar decisión referenciada desde `specs/009-ep-inf-informacion-financiera/spec.md`.
- [X] T002 Confirmar con android-core contratos reales de datos/UiState/eventos, fuentes locales y propietario de cada consulta de patrimonio, movimiento, deuda, tarjeta, recurrencia y meta; documentar paths existentes y no crear duplicados.
- [X] T003 Confirmar estados Empty/Zero/Unknown/Error y semántica de datos faltantes; ajustar spec/modelo únicamente con aprobación.
- [X] T004 [P] Definir y revisar en contrato de navegación las rutas existentes desde calendario a los objetos (incluido origen no disponible) con propietarios correspondientes.
- [X] T005 Confirmar modelo de entitlement efectivo y capacidades Free/Premium con EP-PLA/android-core antes de HU-41.
- [X] T006 Validar dependencias upstream: HU-07/18/19/22 y HU-09/29/33; HU-36/26/30/58 y HU-25 para puntos de integración indicados.

## Fase 2 — HU-39 Inicio e historial (S7, P1)

**Goal**: Presentar patrimonio e historial existentes con acceso Free, privacidad y estados explícitos, preservando invariantes del dominio.
**Independent Test**: Validar los escenarios HU-39 1–5 y FR-001–FR-005/FR-012–FR-014 con fixtures y contratos aprobados, sin requerir calendario ni analítica.

- [X] T007 [P] [US1] Añadir pruebas de dominio/consulta para patrimonio con assets, receivable, liabilities, reserve y credit capacity del escenario FR-001/SC-001, en paths de test existentes tras inspección.
- [X] T008 [US1] Implementar/reutilizar consulta de resumen patrimonial en caso de uso de dominio existente; mantener fórmula y moneda aprobadas, sin cálculo autoritativo en presentación.
- [X] T009 [US1] Extender UiState/ViewModel de Inicio solo si contrato existente no cubre total/estados; cubrir loading, content, empty, unknown, error y retry.
- [X] T010 [US1] Implementar presentación Inicio según sistema visual aprobado, con máscara de importes y semántica accesible.
- [X] T011 [P] [US1] Integrar historial paginado/filtrado por periodo vía caso de uso existente; Free conserva consulta de toda la historia accesible.
- [X] T012 [US1] Implementar estado vacío y navegación hacia registro; mantener lectura/corrección de movimiento desde historial.
- [X] T013 [US1] Verificar comportamiento de reembolsos/anulaciones con dominio de HU-25/HU-21 sin redefinir reglas en UI.
- [ ] T014 [US1] Validar HU-39 offline, aislamiento por propietario, máscara y fixtures según quickstart.

## Fase 3 — HU-40 Calendario (S7, P1)

**Goal**: Exponer eventos financieros realizados y previstos como elementos independientes con rutas a objetos existentes, sin mutaciones financieras.
**Independent Test**: Validar HU-40 1–5 y FR-006–FR-008 con eventos coincidentes y navegación, independiente de HU-39 y HU-41.

- [X] T015 [P] [US2] Definir/adaptar modelo de evento de lectura sobre fuentes existentes sin persistir eventos duplicados ni API inventada.
- [X] T016 [US2] Agregar pruebas de combinación de evento real/previsto y dos eventos concurrentes el mismo día.
- [X] T017 [US2] Implementar consulta mensual local combinando fuentes confirmadas de HU-09/29/33 con tipos/fechas explícitos.
- [X] T018 [US2] Presentar navegación mensual/pasada Free, distinción accesible de realizado/previsto y estados de carga/vacío/error.
- [X] T019 [US2] Conectar cada evento a ruta existente de su objeto fuente; comprobar cuota/deuda/tarjeta/recurrencia y estado de origen no disponible.
- [X] T020 [US2] Asegurar que abrir/consultar eventos no ejecuta pagos ni crea movimientos; comprobar accesibilidad y duplicados visuales.

## Fase 4 — HU-41 Analítica (S9 futura; no parte de entrega S7)

**Goal**: Añadir analítica S9 sujeta a definición aprobada de métricas y entitlement, sin alterar historial básico Free ni contabilización financiera.
**Independent Test**: Validar HU-41 1–6 y FR-009–FR-011 con golden fixtures, escenarios de base cero/periodo parcial y capacidades efectivas; fuera de la aceptación S7.

- [ ] T021 [US3] Resolver GATE-ANALYTICS-04: catálogo de métricas, moneda, periodos, comparación y reglas de filtrado; registrar aprobación antes de codificar.
- [x] T022 [P] [US3] Resolver acceso Premium efectivo/Free y filtros con HU-58/EP-PLA, incluyendo expiración/offline.
- [x] T023 [US3] Añadir pruebas de dominio para excluir transferencias, principal, reservas y anulaciones y para tratar reembolsos según contrato.
- [x] T024 [US3] Implementar resumen básico Free del periodo actual y estados base cero/desconocido/periodo parcial.
> Nota HU-41: T022–T024 cubren lógica y pruebas JVM; la interfaz Compose y su integración quedan cubiertas en la pestaña Análisis. La consulta Room se validó en hardware con `AnalyticsDaoTest` pasando en ambos teléfonos (A16 y S24) sobre Room actual v37.

- [x] T025 [US3] Implementar únicamente capacidades Premium aprobadas; conservar historia básica y validar deep links/entrada directa contra entitlement.
- [x] T026 [US3] Comparar resultados con golden fixtures y verificar fechas, diferencias absolutas/porcentuales y privacidad.
> Nota HU-41 UI y alcance S9 (2026-11-03): `AnalyticsScreen` en la pestaña Análisis y la ruta `analytics` (entrada directa reevaluada por `AnalyticsViewModel` contra entitlement); Free recibe explicación Premium, estadísticas del mes actual e historial básico existente. Fechas exactas, periodo parcial, diferencia absoluta/porcentual, «Sin base de comparación» y máscara de importes cubiertos por `AnalyticsPresentationTest` (JVM); `AnalyticsScreenTest` (androidTest) pasó 6/6 en A16 y S24 en la re-verificación documentada del sprint; no se repitió en esta tanda.
> Limitaciones registradas del incremento S9:
> 1. Solo meses completos (sin rango de fechas personalizado).
> 2. Comparación por totales por moneda (sin desglose por categoría).
> 3. Tareas T021 y T027–T030 permanecen abiertas.
> 4. Ejecución instrumentada y límites: [verification-report.md](verification-report.md); aceptación transversal parcial.

## Fase 5 — Integración y aceptación

- [ ] T027 [P] Pruebas de accesibilidad (TalkBack, contenido/agrupación semántica, escala de texto) para tres journeys.
- [ ] T028 Pruebas de integración navegación y errores/reintentos con sesión/propietario.
- [ ] T029 Ejecutar verificación técnica y funcional aprobada para la implementación futura, registrar resultados reales y revisión cruzada. *(2026-11-03: componente de revisión cruzada independiente registrado en [verification-report.md](verification-report.md#revisión-cruzada-independiente-sprint-9--2026-11-03); sigue abierta porque faltan la verificación funcional y la aceptación.)*
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

## Cobertura incremental real — 2026-11-03

Trabajo secuencial autorizado en `main`, desde `a10e7540`, sin comandos Spec Kit ni cambios remotos. [Informe](verification-report.md) y [matriz de dispositivos](../sprint-9-device-validation.md#cobertura-focalizada-adicional--2026-11-03) contienen los resultados y límites.

- **T021 abierta**: búsqueda en `docs/` y `specs/` y lectura de research/spec/plan no localizaron una decisión aprobada que cierre GATE-ANALYTICS-04. Las reglas constitucionales y el comportamiento actual no sustituyen ese catálogo. No se amplían periodos, comparaciones ni filtros.
- **T027 parcial, abierta**: S24 pasó semántica/acciones de Inicio-patrimonio, Calendario y Análisis y tres casos nuevos con densidad de fuente Compose 2.0; no se cambió font_scale del sistema. Son recorridos de componentes con fixtures. TalkBack está instalado pero desactivado; no se ejecutó lectura hablada, foco secuencial o tres journeys completos con TalkBack. No se certifica ausencia de todo truncamiento por estas aserciones.
- **T028 parcial, abierta**: 16/16 instrumentadas S24 y 11/11 JVM focalizadas. Navegación del grafo/volver, deep link de historial, evento disponible/no disponible, errores/reintentos, máscara y cancelación al cambiar propietario. Los estados Free, bloqueo y cambio de propietario se prueban aisladamente; falta aceptación integrada con sesión/propietario en los tres journeys. No se cambió sesión o entitlement real.
- **T014 parcial, abierta**: máscara y estados de Inicio cubiertos en componente/JVM; sin prueba nueva de lectura offline real ni cambio de propietario real.
- **T029 abierta**: la revisión cruzada independiente quedó registrada en verification-report.md; siguen pendientes la verificación funcional aprobada y la aceptación integral.
- **T030 abierta**: no se modifica alcance de producto ni se presenta este registro como decisión aprobada.


## Cierre incremental factible — 2026-11-03 (`closure3`, base `9552a613`)

- **T028 parcial, abierta**: recorridos S24 integrados con sesión real Inicio/historial, Calendario/meses/evento previsto/origen tarjeta/volver, Análisis/periodo parcial/máscara/scroll/volver ejecutados. Ya no falta toda navegación real; faltan errores/reintentos y cambio de propietario integrados. No se fabricaron errores en datos reales.
- **T027 abierta**: sin escucha/captura segura del audio de accesibilidad disponible; no se activó TalkBack para inferir voz desde logs. Semántica previa no certifica salida hablada, foco ni gestos.
- **T014 abierta**: sin nueva lectura offline real. **T021 abierta**: sin aprobación nueva de GATE-ANALYTICS-04.
- **T029 abierta solo en su aceptación integral**: revisión cruzada Opus ya registrada en `3e5d8d8e`; sin revisiones redundantes. **T030 abierta**: las decisiones de producto pendientes no fueron inventadas.
- Integridad privada `closure3-integrity.json`: 67 tablas, 65 idénticas, todas las outboxes y datos financieros conservados; solo metadatos de perfil (preferencia de máscara restaurada y SYNCED, revisión 2→4) y renovación de caché de acceso. Ajustes originales exactos, sesión real operativa y owner sets iguales. El toggle de Inicio produjo dos escrituras de preferencia por UI antes de identificar su persistencia; no se afirma ejecución sin escrituras remotas.
- Eliminación UI desechable pendiente: emulador existente sin shell/servicio package operativo; solo perfil propietario en S24 y A16 ausente. No se toca la sesión real para sustituir ese entorno.

Detalle, evidencia y decisiones de producto aún necesarias: [verification-report.md](verification-report.md#cierre-factible-de-journeys-reales--2026-11-03-closure3). Sin repetir suites verdes, comandos Spec Kit, ramas, push, PR, merge o despliegues.

# Implementation Plan: EP-PRE — Presupuestos y Consumos

**Branch**: `008-ep-pre-presupuestos-consumos` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: HU-36, HU-37, HU-38, Sprint 7, 21 puntos. Este plan es propuesta técnica previa a aprobación; no autoriza implementación.

## Summary

Especificar un módulo presupuestario incremental integrado con la arquitectura Android nativa, local-first y el dominio financiero existente. El módulo crea/consulta presupuestos y periodos; deriva consumo solo de gastos confirmados elegibles; evita doble conteo por jerarquía; y conserva la inmutabilidad de los movimientos. Antes de abrir implementación se requiere revisar los gates de periodo/zonas, contrato de reembolso HU-25, alertas HU-42 y estado/schema de persistencia.

## Technical Context

**Language/Version**: Kotlin/Android del proyecto (versiones exactas en configuración existente; no se agregan dependencias en este plan).<br>
**Primary Dependencies**: Android nativo, Compose/StateFlow, Room; Supabase/PostgreSQL solo mediante integración actualmente aprobada.<br>
**Storage**: Room local-first y PostgreSQL/Supabase descritos por el proyecto; la ejecución remota no está verificada.<br>
**Testing**: JUnit/Room tests del proyecto; pruebas de dominio, persistencia/migración, integración sync/seguridad y UI según las capacidades existentes. No se ejecutaron tests en esta tarea.<br>
**Target Platform**: Android nativo, local-first; Kipu preserva operación manual sin conectividad continua.<br>
**Project Type**: Aplicación móvil Android con almacenamiento local y backend/sync.<br>
**Performance Goals**: No hay objetivos cuantitativos aprobados. Definir medición con dataset representativo antes de afirmar targets.<br>
**Constraints**: dinero autoritativo entero + currency; datos por usuario; consumo derivado; escritura local atómica; reintentos idempotentes; no efectos contables por presupuesto; no se presume deployment.
**Scale/Scope**: HU-36/37/38, tres historias en S7, 21 puntos; volumen de usuarios/periodos no especificado.

## Constitution Check

### Antes de diseño

- **I Financial Integrity First — PASS WITH GATE**: Solo gastos confirmados elegibles, sin doble conteo jerárquico; no alterar transacciones ni ledger. La regla de elegibilidad por moneda es G6, pendiente de aprobación; reembolsos dependen del comportamiento de HU-25.
- **II Local-First, Retry-Safe — PASS WITH GATE**: éxito después de persistencia local atómica, comandos/reintentos idempotentes; contrato y comportamiento de conflictos de presupuestos deben inspeccionarse antes del diseño final.
- **III Security/Privacy — PASS WITH GATE**: objetos y referencias por propietario; confirmar ubicación de datos y autorización efectiva RLS/Room al diseñar migración.
- **V Freemium — PASS**: Free con máximo de dos presupuestos activos; downgrade no elimina ni omite historia/efectos.
- **VI Lifecycles Preserve History — PASS WITH GATE**: finalizar conserva historia; eliminación de definición/periodos según HU-38 nunca borra hechos financieros; definir reconciliación de sync/tombstones para eliminar.
- **VII Native Android/Clear Boundaries — PASS**: separar Presentation, Domain, Data e infraestructura existente; consumo fuera de UI/VM.
- **VIII Traceability — PASS**: cada FR/SC/HU trazado a tareas; gates sin resolver bloquean implementación.
- **IX Quality — PASS WITH GATE**: se requieren tests financieros, de persistencia/migración, sync, aislamiento y regresión antes de aceptación; aquí solo se documentan.
- **X Product Boundary — PASS**: planificación personal, no pagos/movimiento de dinero ni nuevos tipos transaccionales.

No se identifican violaciones constitucionales propuestas. Las condiciones indicadas como gates no son aprobaciones tácitas.

### Re-evaluación posterior al diseño documental

PASS sujeto a los gates G1–G6 de `research.md`: periodos/calendario, consumo y HU-25, alertas HU-42, esquema/migración actual, ciclo/sync y borrado, y decisión de moneda elegible/explicación al usuario. No se empieza implementación hasta que responsables aprueben o resuelvan estos puntos y las dependencias bloqueantes estén disponibles.

## Estado de dependencias y gates

| Dependencia | Tipo | Estado documental | Criterio para quitar gate |
|---|---|---|---|
| HU-14 categorías/subcategorías | Bloqueante HU-36 | Dependencia aprobada; se inspeccionan modelos y reglas reales al implementar | categorías propias, jerarquía vigente y referencias verificables |
| HU-18 movimientos y HU-19 atomicidad | Bloqueantes HU-36 | Capacidades existen en repo; consumo interno aparece en diseño EP-MOV T059; no equivale a presupuesto funcional | query real cubre payload vigente, estados, zona/moneda/propiedad y tests |
| HU-57 cuotas Free | Bloqueante HU-36 | Hay política de límites `budgets=2` y código de planes; su integración específica pendiente | autorización en alta/edición y preservación de recursos validada |
| HU-25 reembolsos vinculados | Bloqueante HU-37; Sprint 7 | Sin confirmar implementación/contrato en esta tarea; EP-MOV documenta que el consumidor aporta periodo/elegibilidad | contrato aprobado con EP-MOV; caso real de reembolso aplicado al periodo original cubierto, sin mock como aceptación |
| HU-42 centro de avisos | Bloqueante HU-37 | Tipo/categoría de notificación existente en contratos/código general; ruta de alertas de presupuesto pendiente | interface de EP-NOT aprobada y dedupe por periodo/umbral demostrado |
| HU-37 | Bloqueante HU-38 | Futura dentro de este incremento | periodos, consumo y alertas aceptables |
| Schema local/remote | Bloqueante de diseño | tabla budgets en migración local; no hallada tabla de periodos en baseline; remoto desconocido | inventario de Room/migraciones y PostgreSQL versionados, preflight y plan aditivo aprobados |
| G6 moneda | Bloqueante de producto para elegibilidad/UX FR-003/FR-004 | regla de coincidencia sin conversión es recomendación del modelo, no aprobada | decisión de producto sobre moneda elegible y explicación al usuario documentada y propagada |

## Alternatives Considered

1. **Cálculo dinámico desde gastos/movimientos (recomendado)**: deriva consumo desde fuente financiera vigente y periodos; evita saldos financieros duplicados. El diseño EP-MOV ya describe consulta de gastos por intervalo/categorías únicas, como capacidad interna de dominio, no API final.
2. **Acumular consumo mutable al confirmar cada movimiento**: no recomendado sin decisión; exige compensación por anulación, revisión, reembolso atrasado, edición de fecha/categoría y deduplicación, introduciendo segunda fuente de verdad.
3. **Materializar agregados y recalcular/invalidate con eventos**: opción de optimización futura únicamente si mediciones muestran que consulta dinámica es insuficiente; añade consistencia e invalidación distribuida no justificada por carga conocida.

## Proposed Architecture

Propuesta conservadora dentro de la app existente (no crea microservicios):

- `app/src/main/java/com/kipu/app/feature/budgets/presentation/`: estados, pantallas y ViewModels de administración/consulta, sin matemática financiera autoritativa.
- `.../feature/budgets/domain/`: reglas de elegibilidad/presupuesto, operaciones de ciclo y resultados de consulta; consume interfaces del dominio para operaciones autorizadas.
- `.../feature/budgets/data/`: repositorios, mapeos Room y adaptadores de sync existentes.
- `app/src/main/java/com/kipu/app/core/database/`: entidades/DAO/Database/Migrations solo tras revisar la versión Room real.
- `supabase/migrations/`: cambios PostgreSQL aditivos y políticas solo si el diseño actual exige datos remotos; nunca ejecutar desde esta tarea.
- Pruebas en `app/src/test/java/com/kipu/app/feature/budgets/{domain,data}` y `app/src/androidTest/java/com/kipu/app/feature/budgets/`; paths propuestos para entregables futuros, no archivos ya presentes.

Dependencia hacia adentro: presentation → domain → interfaces; data implementa interfaces y adapta Room/sync. Integración con EP-MOV para el agregado de gasto y HU-25; integración con EP-NOT para aviso. No se define aquí nombre/firma/serialización pública ni se crean contratos externos: el punto de consulta EP-MOV y el punto de aviso EP-NOT deben alinearse con implementadores antes de codificar.

### Diseño temporal de datos

`Budget` y `BudgetPeriod` son conceptos del dominio; se documentan sin afirmar que el esquema descrito globalmente exista completo local o remotamente. No agregar `consumed_minor` autoritativo: consumo deriva de gastos elegibles. Limite/currency enteros. La especificación exige diario, semanal, quincenal, mensual y rango custom; el modelo global enumera solo MONTHLY/WEEKLY/CUSTOM, por lo que G1 exige aprobación de representación de frecuencia antes de migración. Intervalos se comparan con fechas civiles/zoneId explícitos; no se fija zona global diferente a preferencias aprobadas.

### Ciclos de vida y consistencia

- Alta/edición/finalización/eliminación deben ser operaciones locales atómicas y conservar propietario.
- Renovación idempotente por presupuesto+intervalo, no por reloj mutable únicamente.
- El estado histórico de límites cerrados no cambia; al finalizar no se crean ciclos/avisos nuevos.
- Eliminación es irreversible según HU-38, confirmada por usuario; debe propagarse de forma que dispositivos atrasados no resuciten la configuración. Nunca propagar borrado al ledger.
- El consumidor usa gastos vigentes únicos; reembolso se ajusta según contrato real de EP-MOV/HU-25.

## Security and Privacy

- Derivar propietario de sesión autenticada; nunca confiar user_id proporcionado por UI para autorización.
- Si objetos remotos son Supabase/PostgreSQL con propiedad de usuario, verificar RLS por fila y referencias a categorías/gastos del mismo usuario; si la operación usa rol privilegiado/RPC, validar autorización explícita fuera de RLS.
- Tests deben incluir intento de lectura/escritura cross-user y referencias cruzadas.
- Evitar logging de importes detallados, transacciones o notas; telemetría mínima, códigos de error seguros.
- No almacenar PAN/credenciales; presupuesto no necesita dato sensible adicional.

## Implementation Plan (propuesta, gate previo)

1. Verificar estado actual de dependencias (HU-14/18/19/25/42/57) y enumerar archivos/capacidades existentes.
2. Resolver G1–G6, en particular HU-25/EP-MOV y la regla de moneda elegible/explicación al usuario; no implementar con fixtures como sustituto.
3. Aprobar modelo físico/índices/RLS y migración aditiva con análisis de datos existentes/rollback.
4. Implementar P1 HU-36 con dominio/DAO/persistencia/cupo/consulta y tests antes de UI de integración; persistencia Room primero.
5. Añadir P2 HU-37, integrando periodos, cálculo por categorías únicas, reembolsos reales, alertas deduplicadas y sync.
6. Añadir P3 HU-38, ciclo de edición/finalización/borrado y protección de historia con conflict/retry tests.
7. Validar secuencia E2E con dataset controlado, migración compatible con datos previos, regresión contable, seguridad, offline y accesibilidad; revisión cruzada antes de aceptar.

**DAG lógico**:

```text
G1 periodos/calendario + G2 contrato MOV/HU-25 + G3 interfaz NOT/HU-42 + G4 schema/migration + G5 ciclo/sync + G6 moneda/UX
                                           │
HU-14 + HU-18/19 + HU-57 ───────────────► HU-36 ──► HU-37 ──► HU-38
                                              │          ▲
                                              └──────────┘ (HU-25 / HU-42 integrados)
```

La secuencia expresa dependencias; no significa que se haya realizado código ni que gates estén resueltos.

## Verification Strategy

- **Domain**: intervalos y límites, porcentaje/rounding visible, inclusión/exclusión, jerarquía y deduplicación por ID, currency; reembolso válido/duplicado/no válido.
- **Persistence**: alta/edición/finalización/borrado atómicos; migración desde bases representativas preserva datos financieros; reintentos/rollback.
- **Sync/security**: mismo comando repetido; dos dispositivos; versión/conflicto; usuario propio vs ajeno; tombstone no resucitado.
- **Integration**: consulta HU-25 usa un reembolso real vinculado y corrige periodo original; HU-42 crea aviso no duplicado. Fixtures solo para unidad.
- **UI/accessibility**: empty/error/offline/cupo/destructive confirmation, TalkBack y navegación; umbrales/estados accesibles.
- **Performance**: medir consultas con datos representativos antes de diseñar optimización/índices. No hay target numérico aprobado.

## Project Structure

### Documentation (this feature)

```text
specs/008-ep-pre-presupuestos-consumos/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
└── checklists/
    ├── requirements.md
    └── quality.md
```

### Source Code (proposed future paths)

```text
app/src/main/java/com/kipu/app/feature/budgets/{domain,data,presentation}/
app/src/main/java/com/kipu/app/core/database/                 # only if schema change approved
app/src/test/java/com/kipu/app/feature/budgets/{domain,data}/
app/src/androidTest/java/com/kipu/app/feature/budgets/
supabase/migrations/                                           # only if approved remote schema change
```

**Structure Decision**: Extensión de la arquitectura Android y las convenciones ya existentes por feature; no introducir servicio separado. Confirmar los paquetes y archivos actuales en ejecución de cada tarea antes de crear rutas.

## Risks and Open Decisions

Ver [research.md](research.md), G1–G6. En particular: representación de periodos más amplia que enum global; contrato real de reembolso y coherencia de fecha de origen; ciclo de alertas; presencia/ausencia de tablas locales y remotas; borrar físicamente vs restricciones FK/tombstones y offline; concurrencia de renovaciones; aprobación de elegibilidad por moneda y explicación al usuario.

## Complexity Tracking

No se propone una nueva capa distribuida ni una nueva fuente de verdad de consumo. Una eventual proyección materializada/servicio separado requiere evidencia de rendimiento y ADR aprobado.

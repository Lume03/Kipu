# Feature Specification: EP-PRE — Presupuestos y Consumos

**Feature Branch**: `014-ep-pre-presupuestos-consumos`<br>
**Created**: 2026-10-24<br>
**Status**: Draft — requisitos base aprobados; gates de integración abiertos antes de implementar
**Input**: HU-36 Crear y consultar presupuestos, HU-37 Renovación, consumo jerárquico y alertas, HU-38 Edición, finalización y eliminación de presupuestos (Sprint 7, 21 puntos).

## Contexto y autoridad

Este refinamiento desarrolla exclusivamente HU-36, HU-37 y HU-38 del backlog. Las historias y reglas aprobadas permanecen en [Product Backlog](../../docs/producto/backlog.md#hu-36--crear-y-consultar-presupuestos), su [matriz de dependencias](../../docs/producto/trazabilidad-y-dependencias.md) y el [Sprint 7](../../docs/planificacion/cronograma-scrum.md). Los cambios no se presumen aprobados por aparecer aquí: puntos que necesitan una decisión o una capacidad precedente se indican como gate. La documentación/modelo local no demuestra despliegue ni aceptación funcional.

## User Scenarios & Testing

### User Story 1 — HU-36 Crear y consultar presupuestos (Priority: P1)

Como usuario de Kipu, quiero definir límites por total, categoría o subcategoría, para comparar mis gastos con lo planificado. Esta historia tiene 8 puntos; cierra en Sprint 7 y depende de HU-14, HU-18 y HU-57.

**Why this priority**: Es la base que entrega la administración y consulta mínima del presupuesto y desbloquea HU-25, HU-37, HU-38 y HU-41.

**Independent Test**: Con referencias propias y cupo Free disponible, crear un límite de gasto y consultar su alcance, importe asignado y consumo basado en gastos elegibles. Una fixture es útil para pruebas de unidad, pero no demuestra la integración real requerida por HU-25.

**Acceptance Scenarios**:
1. **Given** una categoría propia Alimentación y capacidad disponible, **When** el usuario crea un presupuesto mensual de S/500, **Then** puede consultar límite y consumo del periodo.
2. **Given** la categoría propia Restaurantes, **When** el usuario la selecciona como alcance, **Then** el presupuesto queda limitado a esa subcategoría.
3. **Given** un rango personalizado con fecha final anterior a la inicial, **When** intenta guardarlo, **Then** el sistema rechaza la configuración sin crearla.
4. **Given** un usuario Free con dos presupuestos activos, **When** intenta crear un tercero, **Then** se explica el límite y se conservan los dos existentes y su historia.
5. **Given** S/120 de gasto elegible confirmado contra un límite de S/500, **When** consulta el presupuesto, **Then** ve S/120 consumidos, S/380 restantes y 24% de consumo.
6. **Given** que la app ya registra actividad financiera localmente, **When** crea o consulta un presupuesto sin conectividad, **Then** la acción se persiste localmente antes de comunicar éxito; la sincronización pendiente no duplica ni pierde la operación.

---

### User Story 2 — HU-37 Renovar, computar consumo jerárquico y alertar (Priority: P2)

Como usuario de Kipu, quiero renovar automáticamente mis periodos y recibir alertas, para controlar el consumo sin crear presupuestos a mano cada vez. Esta historia tiene 8 puntos; cierra en Sprint 7 y depende de HU-36, HU-25 y HU-42.

**Why this priority**: Hace que el presupuesto recurrente refleje periodos actuales con consumo derivado, jerarquía correcta y alertas no repetidas. HU-25 es bloqueante: el gasto neto del periodo original debe incluir reembolsos reales, no fixtures sustitutivos.

**Independent Test**: Con presupuesto, operación de gasto y notificaciones propias, comprobar cierre/apertura de periodo, consulta agregada por jerarquía, recalculo tras reembolso vinculado y deduplicación de alerta. La aceptación integrada exige las capacidades reales de HU-25 y HU-42.

**Acceptance Scenarios**:
1. **Given** septiembre consumió S/420 de S/500 en un presupuesto mensual recurrente, **When** inicia octubre, **Then** nace una vigencia de octubre con límite S/500, consumo propio y septiembre conservado como historia.
2. **Given** que termina un rango personalizado, **When** pasa su fecha final, **Then** no nace otro periodo automáticamente.
3. **Given** presupuestos de Restaurantes, Alimentación y total, **When** se confirma un gasto elegible de S/50 en Restaurantes, **Then** los tres alcances reflejan S/50 cada uno y el total global sigue siendo S/50, sin doble conteo.
4. **Given** el consumo pasa de 49% a 51% en el umbral configurado de 50%, **When** se confirma el gasto, **Then** se emite una alerta y reabrir/recargar la pantalla no la duplica.
5. **Given** ya existe un periodo con esas fechas, **When** se reintenta el proceso de renovación, **Then** no se crea una segunda vigencia.
6. **Given** una compra confirmada imputada a un periodo previo recibe un reembolso HU-25 válido, **When** se actualiza el consumo, **Then** el consumo neto del periodo original disminuye según la regla de HU-25; el flujo no inventa gasto ni altera el ledger.

---

### User Story 3 — HU-38 Editar, finalizar o eliminar (Priority: P3)

Como usuario de Kipu, quiero editar, finalizar o eliminar mis presupuestos, para mantener mi planificación sin modificar gastos reales. Esta historia tiene 5 puntos; cierra en Sprint 7 y depende de HU-36 y HU-37.

**Why this priority**: Permite mantener o retirar planificación sin reescribir hechos contables; necesita la base y el historial temporal de las historias anteriores.

**Independent Test**: Con un presupuesto propio y periodos históricos, editar límite, finalizar recurrencia, confirmar o cancelar eliminación y comprobar reglas de inclusión/exclusión. Aceptar únicamente una vez cubiertos los gates de HU-36/HU-37.

**Acceptance Scenarios**:
1. **Given** un presupuesto vigente de S/500, **When** el usuario cambia el límite a S/600, **Then** se recalcula el porcentaje del periodo actual y se conservan el límite y consumo registrados de periodos cerrados.
2. **Given** un presupuesto recurrente activo, **When** el usuario confirma finalizar, **Then** cesan las renovaciones y avisos futuros y se conserva la consulta histórica.
3. **Given** una advertencia de pérdida irreversible para un presupuesto con varios periodos, **When** el usuario confirma, **Then** se eliminan físicamente la definición y sus periodos presupuestarios, pero permanecen banco/cuentas, gastos, categorías y cualquier movimiento financiero.
4. **Given** la advertencia de eliminación, **When** el usuario cancela, **Then** permanecen presupuesto e historia.
5. **Given** un pago de principal de tarjeta por S/100, **When** se recalcula el presupuesto, **Then** el consumo operativo no aumenta.
6. **Given** una devolución vinculada a una compra imputada, **When** se recalcula, **Then** se reduce el consumo del periodo de origen conforme a HU-25, sin predicción.

## Edge Cases

- Se rechazan límites no positivos, rangos invertidos y alcances de categoría que no pertenecen al usuario; las restricciones de importe/currency siguen las reglas de dinero del proyecto. La elegibilidad de operaciones frente a la moneda del presupuesto y cómo explicarla al usuario queda pendiente de aprobación en G6; no se presume conversión ni rechazo de monedas distintas como conducta de producto.
- No son consumo elegible los movimientos pendientes o anulados, transferencias, principal de tarjeta/deuda, asignaciones a metas ni predicciones; solo gastos confirmados elegibles.
- Un gasto perteneciente simultáneamente a un presupuesto de subcategoría, su padre y el presupuesto total cuenta una vez por alcance, y una vez en total global.
- Reintentos, reinicios, cambio de periodo, sincronización tardía y actualización de pantalla no crean periodos ni alertas duplicadas.
- Un error al crear/renovar/editar/eliminar no debe dejar una operación parcial ni reportar éxito antes de persistir la transacción local.
- Un reembolso no elegible, no confirmado, de otro propietario, excedente o sin vínculo válido no debe modificar consumo; su semántica exacta depende del contrato aprobado de HU-25.
- Finalizar detiene renovaciones y alertas futuras, sin mutar gastos ni saldos; eliminar requiere confirmación expresa y nunca elimina transacciones.
- Free excedido no autoriza borrar recursos ni esconderlos de cálculos; el límite aprobado es dos presupuestos activos.

## Requirements

### Functional Requirements

- **FR-001**: Kipu MUST permitir crear un presupuesto propio de gasto con alcance total, categoría o subcategoría y límite monetario positivo.
- **FR-002**: Kipu MUST ofrecer los periodos diario, semanal, quincenal, mensual y rango de fechas propio descritos por HU-36; un rango final anterior al inicial MUST rechazarse.
- **FR-003**: Kipu MUST mostrar para el periodo vigente el límite, consumo, restante y porcentaje, sin usar coma flotante como representación monetaria autoritativa.
- **FR-004**: El consumo MUST derivarse solo de gastos propios confirmados y elegibles, sin mutar transacciones ni ledger; movimientos no elegibles listados en Edge Cases MUST excluirse.
- **FR-005**: Free MUST permitir hasta dos presupuestos activos; alcanzar el límite MUST bloquear nuevas altas y preservar recursos existentes y sus efectos financieros.
- **FR-006**: Kipu MUST mantener identidad/propiedad propia y aislamiento para definición, periodos y operaciones asociadas; no debe exponer datos presupuestarios de otros usuarios.
- **FR-007**: La renovación recurrente MUST crear una nueva vigencia con el límite configurado sin trasladar sobrante por defecto, preservando periodos anteriores.
- **FR-008**: El rango personalizado MUST terminar en su fecha final y no renovarse automáticamente.
- **FR-009**: El sistema MUST computar consumo por categorías jerárquicas sin sumar más de una vez la misma operación dentro de un alcance ni inflar el total global.
- **FR-010**: Los umbrales iniciales de alerta MUST ser 50%, 80% y 100%; el usuario puede editarlos y recibe como máximo un aviso por cruce y periodo/umbral, según la configuración vigente.
- **FR-011**: La creación de periodos ante reintentos MUST ser idempotente por presupuesto y vigencia; no se duplican fechas de periodo.
- **FR-012**: Integración HU-25 MUST ajustar el consumo presupuestario del periodo original de la compra reembolsada usando capacidad real aprobada y su vínculo semántico. No se define ni presume aquí una API, algoritmo, clase, contrato de RPC o estado de implementación; HU-25/EP-MOV debe confirmar el contrato antes de cerrar integración.
- **FR-013**: Editar límite MUST afectar el periodo actual y los futuros y preservar límites de periodos cerrados; cambiar alcance/calendario se aplicará a siguiente vigencia o requerirá finalizar y crear otro, según la regla aprobada de HU-38.
- **FR-014**: Finalizar MUST detener renovaciones y avisos futuros conservando historia consultable.
- **FR-015**: Eliminar, tras confirmación inequívoca, MUST borrar definición e historia de periodos presupuestarios conforme HU-38, pero MUST NOT eliminar ni alterar movimientos, ledger, cuentas, categorías ni sus importes.
- **FR-016**: Kipu MUST permitir cancelar la confirmación de borrado sin cambios.
- **FR-017**: El presupuesto MUST ser planificación y no retener, reservar, transferir ni mover fondos.
- **FR-018**: Los cambios locales deben quedar persistidos atómicamente antes de comunicar éxito; la sincronización debe ser segura ante reintentos y partición por propietario de acuerdo con la arquitectura aprobada.
- **FR-019**: La operación de presupuestos MUST permanecer disponible en el núcleo Free conforme a la capacidad aprobada; el downgrade o exceso de cupo no cambia hechos financieros.

### Success Criteria

- **SC-001**: En pruebas de aceptación de HU-36, todos los tipos de periodo aprobados y los tres alcances (total, categoría, subcategoría) producen la definición solicitada o un rechazo explícito y no destructivo; el rango invertido nunca se guarda.
- **SC-002**: Con fixture base S/120 de gasto elegible sobre límite S/500, consulta devuelve 120, 380 y 24%; operaciones no elegibles dejan el importe consumido sin cambios.
- **SC-003**: Un gasto de S/50 en subcategoría aparece una vez en cada alcance coincidente y el agregado global muestra S/50, no S/100 ni S/150.
- **SC-004**: Renovar/reintentar N veces la misma vigencia resulta en exactamente un periodo; al cambiar de ciclo el límite nuevo es el configurado y el periodo anterior sigue consultable.
- **SC-005**: Un reembolso de HU-25 aplicado en integración real actualiza una sola vez el consumo del periodo de origen, conserva la fecha/efecto de caja según HU-25 y no cambia el principal original ni altera balances por la operación presupuestaria. Fixtures no satisfacen este criterio de integración.
- **SC-006**: Cada cruce de umbral genera como máximo un aviso en ese periodo, incluyendo repetición de consulta/reintento; finalizar evita alertas futuras y mantiene historia existente.
- **SC-007**: En el flujo de eliminación confirmado desaparecen solo presupuesto y periodos; en cancelación no desaparece ninguno; en ambos casos movimientos, ledger y saldos permanecen idénticos.
- **SC-008**: Usuario Free con dos presupuestos activos recibe rechazo claro a una tercera creación, sin pérdida ni ocultamiento de datos existentes; presupuestos fuera de selección/cuota continúan siendo considerados según reglas de HU-57.
- **SC-009**: El plan/test previsto demuestra propiedad, persistencia local previa al éxito, recuperación/reintento sin duplicado y ausencia de efectos financieros por CRUD o renovación; los resultados de ejecución se registrarán durante implementación, no se afirman en este documento.

## Assumptions and Gates

- Las historias base aprobadas son HU-36 (8), HU-37 (8) y HU-38 (5), Sprint 7, 21 puntos; no se crean historias ni se cambia su alcance.
- Bloqueantes según backlog: HU-36 ← HU-14/HU-18/HU-57; HU-37 ← HU-36/HU-25/HU-42; HU-38 ← HU-36/HU-37. Dependencias bloqueantes no satisfechas impiden aceptación.
- HU-25 está planificada en S7 y requiere capacidad presupuestaria real. Contrato de integración está abierto: confirmar punto de consulta/cambio, semántica de periodo original y consistencia transaccional entre movimientos/reembolso/consumo. No declarar HU-25 integrado con fixtures.
- HU-42 es dependencia real de avisos de HU-37; confirmar interface disponible con EP-NOT. HU-43 es relacionada, Sprint 8 y no se incorpora como bloqueante ni alcance.
- `docs/arquitectura/modelo-de-datos.md` describe un diseño base V4.2, no el esquema remoto actual. La migración local `20260920000000_financial_core_baseline.sql` contiene `public.budgets`, pero en lo inspeccionado no se halló una migración de `budget_periods`; su presencia local no prueba despliegue remoto. La diferencia requiere verificar esquema/versiones antes de diseñar cambios SQL.
- En el backlog HU-36 se permiten periodos diario, semanal, quincenal, mensual y rango propio, mientras el modelo global enumera MONTHLY/WEEKLY/CUSTOM. La cobertura de diario/quincenal requiere reconciliar diseño físico y regla de fecha; no descartar los requisitos por divergencia del modelo ni introducir enums sin autorización. Gate de diseño antes de migrar.
- El cronograma Sprint 7 referencia el directorio `008-ep-pre-presupuestos-consumos`, pero rama y ubicación explícitas de esta tarea son `014-ep-pre-presupuestos-consumos`; este refinamiento respeta la rama/carpetas autorizadas y no renumera fuentes globales.
- Los objetivos cuantitativos de rendimiento, volumen, retención y disponibilidad no están aprobados; no se inventan. Se deberá medir perfil local con datos representativos, sin afirmar un umbral contractual.
- No se requieren contratos de API pública para este feature documental. Los seams internos se describen como gates/propuestas hasta revisión de especialistas.
- **G6 — Moneda elegible y explicación al usuario (abierto)**: aprobar si una operación solo es elegible cuando su moneda coincide con la del presupuesto y qué explicación recibe el usuario ante una moneda distinta, o definir otra regla. La coincidencia sin conversión es recomendación propuesta del modelo, no decisión de producto. Resolver y propagar la decisión antes de implementar FR-003/FR-004; no asumir conversión automática.

## Key Entities

- **Presupuesto**: definición propia de planificación; alcance total o a una categoría, importe límite, moneda, regla de periodo, umbrales, estado y revisión.
- **Periodo presupuestario**: intervalo civil de vigencia ligado a un presupuesto; conserva el límite histórico asociado y permite consultar consumo del intervalo. El consumo es derivado, no saldo mutable.
- **Gasto elegible**: gasto propio confirmado que cumple periodo, moneda y reglas de clasificación; no modifica su movimiento ni asientos.
- **Alerta presupuestaria**: aviso relacionado con presupuesto, periodo y umbral cruzado; debe poder deduplicarse y no alterar dinero.

## Out of Scope

- Crear o modificar movimientos, reembolsos, cuentas, categorías, ledger o saldos desde EP-PRE.
- Definir un API/RPC/JSON de HU-25 o modificar la rama/contratos EP-MOV sin acuerdo de sus responsables.
- Reglas de UI visual no especificadas, widgets de Inicio, analítica avanzada, HU-43 (Sprint 8), excedentes/rollover o pausas manuales.
- Afirmar esquema aplicado remotamente, implementación terminada o criterio aceptado solo por fixtures/mockups/documentación.

# Research: EP-PRE — Presupuestos y Consumos

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Fecha de corte**: 2026-10-24

La investigación es local/documental y no consulta un entorno remoto. Los documentos globales declaran su alcance como base de diseño, no fotografía desplegada.

## Decisiones y evidencia

### R1 — Derivar consumo desde hechos financieros vigentes

- **Decisión propuesta**: cálculo de consumo de lectura desde movimientos/gastos confirmados elegibles; no persistir un contador mutable independiente como verdad de consumo.
- **Evidencia**: Constitución I exige consumo desde gastos confirmados elegibles y prohíbe doble conteo; modelo global define `v_budget_status` como cálculo dinámico. `specs/004-ep-mov-movimientos-ledger/data-model.md` §§174–187 documenta `ExpenseConsumptionQuery` de EP-MOV (propietario, moneda, intervalo, zona explícita, ids únicos) y excluye transacciones como valores duplicados por asiento.
- **Trade-off**: cálculo dinámico es más coherente con revisiones/reembolsos; costo de lectura no medido. Materializar solo después de profiling con datos representativos y ADR.

### R2 — Un mismo gasto contribuye una vez por alcance

- **Decisión**: categorías compatibles se resuelven sobre identificadores únicos de operaciones; un gasto en subcategoría puede contribuir a presupuesto de la subcategoría, padre y total, cada uno mostrando el importe una vez. El total general no suma las líneas de presupuesto.
- **Evidencia**: HU-37 escenario Jerarquía y constitución I; modelo EP-MOV T059 incluye intersección por `categoryIds` sobre IDs de transacción únicos.
- **Alternativa descartada**: suma de agregados por niveles (duplica el gasto en total global).

### R3 — Periodos históricos separados de la configuración vigente

- **Decisión aprobada a nivel funcional**: una nueva vigencia conserva periodos previos y límites cerrados; editar límite cambia periodo actual/futuro; editar alcance/calendario inicia siguiente periodo o requiere finalizar/crear. Sin rollover por defecto.
- **Evidencia**: HU-37, HU-38, `docs/arquitectura/modelo-de-datos.md` sección presupuesto describe `budget_periods.limit_minor` snapshot y cálculo desde gastos.
- **Gate G1**: el backlog permite DAILY/WEEKLY/BIWEEKLY/MONTHLY/CUSTOM_RANGE; el modelo físico global `budget_period_type` solo enumera MONTHLY/WEEKLY/CUSTOM. Resolver convención de calendario/zona y representación antes de migración; no restringir HU por el modelo obsoleto ni asumir esquema remoto.

### R4 — Integración de devolución

- **Decisión**: no diseñar una API EP-PRE ni actualizar el contrato EP-MOV desde esta tarea. Se requiere consultar la capacidad real acordada por EP-MOV/HU-25, que corrige el consumo del periodo original de compra sin alterar caja/ledger por el cálculo.
- **Evidencia**: Trazabilidad marca HU-25 bloqueante de HU-37; HU-25 precisa ajuste a periodo original; EP-MOV T059 define solo query interna como proveedor futuro, no implementación de presupuestos.
- **Gate G2**: contrato aprobado debe aclarar objeto/fecha efectiva, signos y elegibilidad del reembolso, consistencia transaccional, idempotencia, acceso local/offline y pruebas. Fixture no satisface SC-005.

### R5 — Alertas y Free

- **Decisión**: umbrales de negocio iniciales 50/80/100 editables, un aviso por cruce y periodo/umbral; máximo Free de dos presupuestos activos según HU-36 y modelos EP-PLA.
- **Evidencia**: HU-36/37, `FreePlanLimits`/política de cuota EP-PLA inspeccionadas; tipo BUDGET_ALERT está presente en el modelo global. La existencia de enum/tipo no prueba un emisor de alertas integrado.
- **Gate G3**: confirmar con EP-NOT/HU-42 la interface de creación, idempotencia/deduplicación y navegación. HU-43 es Sprint 8 relacionada, no gate requerido adicional.

### R6 — Persistencia y despliegue

- **Evidencia inspeccionada**: la migración local `supabase/migrations/20260920000000_financial_core_baseline.sql` crea `public.budgets` con amount `limit_minor bigint`, `currency_code`, tipo de periodo, umbrales, activo, revisión y `deleted_at`; dicha migración habilita RLS/policies en esa base documental. `docs/arquitectura/modelo-de-datos.md` incluye `budget_periods`, pero búsqueda en las migraciones revisadas no localizó creación de esa entidad. `app/src/main/java/com/kipu/app/feature/` y el repositorio no mostraron una implementación de feature de presupuesto; el código contiene referencias auxiliares de cuota y notificaciones, no flujo de presupuesto.
- **No inferir**: ni tabla Room, ni migración remota aplicada, ni view/rpc existente, ni funcionamiento de políticas en remoto. El archivo local no prueba la situación de Supabase remoto.
- **Gate G4**: inspeccionar Room Database/entidades/migraciones y lista completa de migraciones SQL en la tarea de implementación; resolver baseline actual, FK/cascades, RLS, índices y rollback antes de cambiar datos.

### R7 — Sincronización y ciclo de vida

- **Decisión propuesta**: integrar con sync/outbox existente, con identidad estable, idempotencia, usuario como frontera de datos, conflictos explícitos y eliminación que no permita resurrección por dispositivos atrasados. No crear estrategia nueva en paralelo.
- **Evidencia**: Constitución II/III/VI; patrones de `docs/arquitectura/vision-general.md`; entidades comunes globales `revision`, `deleted_at`, receipts/outbox.
- **Gate G5**: confirmar compatibilidad con la versión/contrato de sync actual. Existe tensión de requisitos entre eliminación física explícita HU-38 (presupuesto y periodos) y tombstones/historia de sincronización descritos globalmente; preservar ambos requerimientos sin borrar movimientos y acordar retención/borrado de historial presupuestario.

## Alternativas

| Problema | Opción propuesta | Alternativa | Motivo |
|---|---|---|---|
| Consumo acumulado | Derivar desde transacciones confirmadas únicas | Contador incrementado por cada alta | La reversión/reembolso/reclasificación exige compensaciones delicadas; ya hay consulta de consumo en diseño EP-MOV |
| Jerarquía | Intersección única de ids con filtros por alcance | Sumar subtotales de padres e hijos | Evita doble conteo en total |
| Optimización | Consultar primero; perfilar | Pre-agregados/cache invalidado | No se conocen volumen ni target; cache añade fallos de invalidación |
| Contrato EP-MOV | Reutilizar interface aprobada tras verificar | Definir contrato dentro de EP-PRE | Evita imponer API y crear segunda fuente de verdad |

### R8 — Moneda elegible y explicación al usuario

- **Propuesta, no decisión aprobada**: el modelo de datos considera limitar el consumo a operaciones con moneda coincidente y no convertir; esta conducta no está autorizada como regla de producto.
- **Gate G6**: producto debe aprobar qué monedas son elegibles para un presupuesto y qué explicación recibe el usuario cuando una operación está en otra moneda, o especificar otra regla. Propagar la decisión a FR-003/FR-004, modelo y pruebas antes de implementar.

## Cuestiones que bloquean inicio de implementación

1. **G1** Confirmar representación de DAILY/BIWEEKLY/CUSTOM_RANGE y zona/calendario civil compatible con HU-36 y el enum global.
2. **G2** Acordar con EP-MOV la query real y el comportamiento HU-25 que ajusta periodo de origen; validar integración real y no fixture.
3. **G3** Acordar interface disponible EP-NOT/HU-42 y clave de deduplicación por cruce.
4. **G4** Verificar el estado de Room y todas las migraciones SQL locales; obtener autoridad sobre esquema remoto antes de cambios.
5. **G5** Resolver borrado de datos presupuestarios frente a sync/tombstones/reintentos, respetando eliminación funcional aprobada e historia financiera.
6. **G6** Aprobar regla de moneda elegible y explicación al usuario; la coincidencia sin conversión del modelo es solo una propuesta.

No se solicitó ni ejecutó acceso remoto, SQL, build, pruebas ni cambios de código en esta fase.

# Especificación: EP-INF — Inicio e Información Financiera

**Rama**: `015-ep-inf-inicio-financiero`
**Estado**: Borrador para revisión; no aprobado para implementación
**Fecha**: 2026-10-24
**Fuente de alcance**: `docs/producto/backlog.md` HU-39–HU-41, `docs/producto/trazabilidad-y-dependencias.md`, planificación base del cronograma.

## Propósito y alcance de la épica

Entregar vistas de lectura financiera personal coherentes con el ledger: Inicio e historial básico (HU-39) y calendario financiero (HU-40) en el incremento planificado S7 (13 puntos); HU-41, estadísticas y analítica, es un incremento separado y futuro de S9 (8 puntos), condicionado por sus dependencias. La épica completa suma 21 puntos. Los sprints reflejan el plan base, no aceptación, implementación ni despliegue.

El sistema presenta datos propios disponibles; no mueve dinero, no ejecuta pagos, no crea hechos financieros ni inventa métricas. La presentación consume estado/casos de uso de capas existentes; las reglas y cálculos autoritativos pertenecen al dominio.

## Historias y escenarios

### US1 / HU-39 — Inicio e historial financiero (P1, Alta, 8 pts, S7)
Como usuario quiero ver mi situación y navegar mis movimientos para comprender mi dinero actual y su origen. El alcance Free incluye patrimonio y lectura de todo el historial; no incluye analítica histórica Premium. Mantener estructura Inicio aprobada, sin widgets presupuestarios ni predicciones. Datos de módulos no utilizados se representan como cero/vacío, nunca con datos ficticios.

1. Con activos reales S/1000, por cobrar S/200 y pasivos S/300, Inicio muestra patrimonio S/900; no suma línea de crédito ni reserva.
2. Cuenta nueva muestra estado vacío del historial y acción de registro.
3. Free consulta y corrige movimientos de meses anteriores.
4. La máscara de privacidad oculta importes sin alterar estructura ni cálculos.
5. Tras un reembolso, las métricas reflejan el ajuste y fechas definidas por la regla del dominio.

Dependencias: bloqueantes HU-07, HU-18, HU-19; parcial HU-22; relacionada HU-41. HU-39 desbloquea HU-41.

### US2 / HU-40 — Calendario financiero (P1, Alta, 5 pts, S7)
Como usuario quiero consultar fechas de movimientos y obligaciones para anticipar vencimientos en una vista. Debe distinguir eventos realizados de previstos y dirigir cada evento a su objeto de origen; no ejecutar cobros automáticos.

1. Cierres y vencimientos de tarjeta aparecen con fechas e identidad/tipo distinguibles.
2. Seleccionar una cuota abre la deuda correcta.
3. Free consulta meses pasados sin paywall de historial.
4. Dos obligaciones en el mismo día aparecen ambas, no se sobrescriben.
5. Eventos coincidentes se presentan como conjunto y permiten escoger el objeto correcto; orden y representación visual quedan sujetos al gate de diseño indicado abajo.

Dependencias: bloqueantes HU-09, HU-29, HU-33; relacionada HU-44.

### US3 / HU-41 — Estadísticas y analítica financiera (P2, Alta, 8 pts, S9)
Incremento futuro separado: el usuario analiza gastos, crédito, deudas y metas y compara resultados sin duplicar transferencias. No se compromete a S7.

1. Con gasto S/100 y transferencia S/200, gasto operativo suma S/100.
2. Premium compara periodos y filtros combinados mostrando fechas y diferencias absolutas/porcentuales válidas.
3. Periodo anterior sin gasto se marca «Sin base de comparación», nunca infinito.
4. En Free, estadística básica del mes actual; históricos agregados/filtros premium informan la restricción y conservan acceso al historial básico.
5. Reservas y línea de crédito no se agregan como activos; por cobrar sí integra patrimonio según fórmula.
6. Mes abierto frente a cerrado indica periodo parcial y fechas exactas.

Dependencias: bloqueantes HU-39, HU-36, HU-09, HU-26, HU-30, HU-58; parcial HU-22; relacionada HU-25.

## Requisitos funcionales

- **FR-001** Inicio presenta el patrimonio como activos reales + cuentas por cobrar − pasivos pendientes, con valores obtenidos del estado propio disponible.
- **FR-002** Cuentas, tarjetas, línea y reservas se mantienen diferenciadas; línea disponible no es activo, una reserva es asignación del activo de respaldo y no suma de nuevo.
- **FR-003** El historial permite navegar todos los periodos y consultar movimientos propios en Free, sujeto a los casos de uso y política de acceso existentes.
- **FR-004** Inicio e historial exponen estados de carga, contenido, vacío, error/reintento y datos no disponibles sin confundirlos con cero confirmado.
- **FR-005** La preferencia de privacidad oculta importes en vistas y elementos asociados sin cambiar datos, filtros ni cálculos.
- **FR-006** El calendario muestra eventos ocurridos y previstos con distinción semántica, fecha, tipo, origen y navegación a la entidad correspondiente; no ejecuta pagos ni crea movimientos.
- **FR-007** Eventos múltiples en la misma fecha se conservan como elementos independientes y accesibles.
- **FR-008** El calendario permite consultar periodos pasados en Free.
- **FR-009** Las métricas excluyen transferencias, reservas y principal de pagos/cobros de ingreso/gasto operativo, y respetan anulaciones y reembolsos según reglas financieras existentes.
- **FR-010** Solo HU-41 ofrece estadísticas: Free ve el resumen básico del periodo actual; capacidades Premium de históricos/comparaciones/filtros requieren autorización efectiva, sin borrar ni restringir historia básica.
- **FR-011** Comparaciones identifican periodos, manejo de base cero y periodos parciales sin porcentajes inválidos ni precisión falsa.
- **FR-012** Datos inexistentes de módulos aún no usados se muestran vacíos/ceros explícitos según el dato, no se sintetizan ni se presentan como estado confirmado cuando sea desconocido.
- **FR-013** Toda consulta se limita al propietario autenticado; errores, logs y telemetría no exponen importes o datos financieros sensibles de forma indebida.
- **FR-014** La capa de presentación solo representa estado y emite eventos; no hace acceso de red/base de datos ni cálculos autoritativos.

## Criterios de éxito verificables

- **SC-001** El escenario patrimonial de HU-39 puede validarse contra un fixture controlado con S/1000 en activos reales, S/200 por cobrar y S/300 en pasivos; la salida esperada es S/900. Este criterio no prescribe fixtures ni cálculos para HU-40 o HU-41.
- **SC-002** Transferencia S/200 no altera suma de gasto operativo S/100 en el escenario especificado.
- **SC-003** Dos eventos el mismo día permanecen individualmente visibles y cada ruta llega al objeto fuente correcto.
- **SC-004** La matriz de tareas asigna al menos una tarea verificable a cada FR-001–FR-014 y SC-001–SC-005; cada tarea de historia se traza a HU-39, HU-40 o HU-41, y los escenarios de privacidad, Free/Premium, vacío/error y periodo parcial se validan únicamente donde aplican según FR-004/005/010/011/012/013.
- **SC-005** Ningún criterio afirma implementación, medición de rendimiento, despliegue o integración no verificada.

No se añaden umbrales de conversión, rendimiento o negocio: el backlog no ofrece medidas de producto autorizadas para ello.

## Entidades conceptuales

Resumen patrimonial; activo real/cuenta; cuenta por cobrar; pasivo/deuda; movimiento con tipo, periodo, estado y relaciones; evento financiero con fecha prevista/real, categoría/origen y referencia navegable; serie agregada de métrica; periodo de consulta; preferencia de privacidad; capacidad/entitlement efectivo. Son conceptos de consulta, no propuesta de tablas nuevas.

## Límites y decisiones pendientes (gates)

- **GATE-DESIGN-01 (bloquea cierre de diseño visual e implementación)**: `docs/control-documental.md` registra autoridad visual en conflicto: `docs/DESIGN.md` (Andean Modernist) frente a `docs/ux-ui/mockups/README.md` (Calm Emerald), con proyectos Stitch distintos. Producto/diseño debe determinar autoridad por pantalla o aprobar unificación; no se elige por fecha. Las nueve imágenes de estados de tarjetas referenciadas no están disponibles en checkout y no son fuente para este alcance.
- **GATE-PRODUCT-02**: confirmar composición y orden final de widgets/series de Inicio y calendario, copy para «sin datos» versus cero, navegación/resolución de rutas cuando el objeto fuente ya no esté disponible, y política de eventos concurrentes visualmente. El backlog fija reglas, no layouts exhaustivos.
- **GATE-ARCH-03**: confirmar contratos de consulta/eventos para deuda, recurrencia, crédito, préstamo y patrimonio; reutilizar fuentes existentes y no crear API ni entidad persistente por inferencia.
- **GATE-ANALYTICS-04**: HU-41 requiere decisiones de definición de indicadores, moneda cuando existan divisas, zonas/fechas de corte y filtros Premium autorizados. Usar únicamente reglas y fuentes aprobadas; no inventar conversiones o indicadores.
- **GATE-ACCESS-05**: aplicar el entitlement efectivo de EP-PLA para Premium; resolver con android-core el contrato de capacidades y estados. Free mantiene acceso básico e historial completo.
- El acceso presupone sesión propia autenticada donde corresponda. No expone información de otro usuario; el alcance offline sigue las reglas de acceso local-first existentes.

## Dependencias y trazabilidad

| HU | Pts / cierre base | Dependencias bloqueantes | Parciales | Relacionadas | Incremento |
|---|---:|---|---|---|---|
| HU-39 | 8 / S7 | HU-07, HU-18, HU-19 | HU-22 | HU-41 | S7 |
| HU-40 | 5 / S7 | HU-09, HU-29, HU-33 | Ninguna | HU-44 | S7 |
| HU-41 | 8 / S9 | HU-39, HU-36, HU-09, HU-26, HU-30, HU-58 | HU-22 | HU-25 | S9 futuro |

Las puntuaciones son esfuerzo relativo. La planificación fuente marca S7 como Sprint Goal con 42 puntos incluyendo HU-39/40; S9 incluye HU-41 en 29 puntos. No se redefine el Sprint 7 ni se traslada HU-41 a él.

## Referencias

- [Plan técnico](plan.md), [investigación](research.md), [modelo conceptual](data-model.md), [guía de validación futura](quickstart.md), [tareas previstas](tasks.md), [checklist Spec Kit](checklists/requirements.md), [calidad de requisitos](checklists/quality.md).
- Backlog de producto: `docs/producto/backlog.md`; trazabilidad: `docs/producto/trazabilidad-y-dependencias.md`; control documental: `docs/control-documental.md`.

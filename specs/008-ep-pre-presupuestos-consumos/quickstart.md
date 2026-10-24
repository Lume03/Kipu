# Quickstart: EP-PRE — Validación prevista de presupuestos

Este documento es guía de validación para la fase de implementación futura; no es resultado de pruebas ejecutadas. Está bloqueado hasta resolver los gates G1–G6 de [research.md](research.md), verificar HU-14/18/19/25/42/57 y aprobar migraciones/cambios aplicables.

## Requisitos previos de validación

1. Checkout con dependencias bloqueantes integradas y versiones de código identificadas.
2. Contrato aprobado de HU-25 con acceso a reembolso real vinculado y ajuste del periodo original; fixture por sí sola no cuenta como aceptación.
3. Interface HU-42/EP-NOT aprobada para alertas, deduplicación y navegación.
4. Decisión aprobada de periodo/calendario (G1), schema local/remote (G4) y borrado/sync (G5).
5. Decisión de producto sobre moneda elegible y explicación al usuario (G6); la recomendación del modelo de coincidencia sin conversión no es aprobación.
6. Entorno local de pruebas y datos sintéticos propios de usuario; nunca usar PII o datos reales. DB/migraciones solo en entorno local desechable y tras autorización del implementador.
7. Antes de ejecutar comandos, revisar build scripts y nombres de tareas actuales. En esta fase no se corren build, tests, migraciones ni despliegues.

## Escenarios de aceptación que se deben automatizar

| Escenario | Preparación | Resultado esperado |
|---|---|---|
| Alta y consulta (HU-36) | Dueño propio, categoría válida, Free con cupo, límite S/500; gastos elegibles acumulados S/120 | Límite 50000 minor units PEN, consumo 12000, restante 38000, porcentaje 24%; atomicidad local antes del éxito |
| Alcances y jerarquía (HU-36/37) | Presupuestos total, padre Alimentación y subcategoría Restaurantes; gasto confirmado único S/50 | Cada consulta coincidente refleja 5000; agregado global = 5000, no 10000/15000 |
| Exclusiones | Gastos confirmados junto a transferencia, principal, meta, pendiente y VOIDED | Solo gasto elegible incluido, sin asiento/total duplicado |
| Rango inválido (HU-36) | Fecha final < fecha inicial | Rechazo sin presupuesto/periodo parcial |
| Free quota (HU-36) | Dos activos existentes | Tercera alta bloqueada; los objetos/datos previos siguen intactos |
| Renovación (HU-37) | Periodo mensual previo S/500 con S/420; primer día de ciclo nuevo; reintentar varias veces | Un periodo nuevo, límite 50000, consumo propio, periodo anterior intacto |
| Custom terminado (HU-37) | Budget custom cuyo end pasó | No nace otro periodo automáticamente |
| Alertas (HU-37) | Consumo cruza 50/80/100 y repite consulta/comando | Un aviso por cruce/umbral/periodo y ningún duplicado |
| HU-25 real (HU-37/38) | Compra original elegible en periodo A, reembolso HU-25 parcial/tardío confirmado | Caja se rige por HU-25 y consumo de A se ajusta exactamente una vez; no cambia movimiento original por recalculo; sync/replay estable |
| Editar (HU-38) | Presupuesto límite 500 con historial de periodo cerrado | Límite vigente se hace 600 y porcentaje actual cambia; periodos cerrados conservan límite/consumo |
| Finalizar (HU-38) | Budget recurrente con historial | No nuevas vigencias ni avisos; consulta histórica intacta |
| Eliminar/Cancelar (HU-38) | Presupuesto con varios periodos y transacciones asociadas | Confirmar borra budget/periodos únicamente; cancelar no altera nada; gastos/ledger/cuentas/categorías/saldos no cambian |
| Seguridad/offline | Dos propietarios, red caída, retry/reinicio; sincronización posterior | Sin lectura/escritura cross-user; persistencia local confirmada; retry no duplica ni resucita borrado |

## Evidencia esperada para aceptar

- Tests unitarios del dominio para dinero, elegibilidad, intervalos, categoría única y umbrales.
- Tests Room y migración de upgrade con datos previos representativos, rollback/preflight y sumas/IDs financieros sin cambios.
- Tests de sync/seguridad con doble replay, dos propietarios y dispositivo atrasado.
- E2E del reembolso con capacidad real de HU-25 y notificación real de HU-42.
- Evidencia accesible de estados vacío, error, offline, límite, confirmación destructiva y navegación.
- Reporte identifica revisiones/commits/ambiente y comandos exactos; no marcar aceptación con fixtures o modelo documental.

## No ejecutar aún

No ejecutar Gradle, pruebas, migraciones, consultas a Supabase remoto ni deployment desde este artefacto de planificación. Ningún comando de ejecución fue validado en esta tarea.

# Sprint 9 — Decisiones Explícitas del Product Owner

**Fecha de registro:** 2026-11-03 (America/Lima) [2026-11-04 UTC]<br>
*(Verificación shell `Get-Date`: `2026-11-03 21:43:12 -05:00`)*<br>
**Origen:** Chat oficial de Sprint 9 de Kipu con el Product Owner<br>
**Alcance:** Registro documental formal y manual de dos decisiones explícitas que resuelven bloqueos y reconcilian especificaciones en `specs/009-ep-inf-informacion-financiera/` y `specs/011-ep-aut-captura-inbox/`.<br>
**Condición metodológica:** Documentación autorizada en `main`, sin comandos Spec Kit, sin ramas, push, PR, merge ni modificaciones de backend o base de datos remota. No constituye aprobación integral de Sprint 9 ni certificación de tareas pendientes.

---

## Decisión 1 — Aprobación del alcance incremental de Analítica Financiera (HU-41 / T021 / GATE-ANALYTICS-04)

### 1.1 Consulta planteada al Product Owner
Se consultó la aprobación formal del catálogo de métricas, reglas de corte, moneda, periodos y filtros de HU-41 implementados en el incremento de Sprint 9 (en la pestaña Análisis):
- **Gasto operativo:** Neto de reembolsos (reembolsos imputados a su gasto original sin generar ingresos falsos).
- **Desglose:** Desglose por categoría y patrimonio consolidado (activos reales + cuentas por cobrar − pasivos pendientes).
- **Moneda:** Monedas separadas sin conversión arbitraria entre divisas.
- **Zona horaria:** Zona horaria local del teléfono (`ZoneId` del dispositivo).
- **Periodos:** Selección por meses completos; el mes actual en curso se marca hasta la fecha actual como periodo parcial (`isPartial = true`).
- **Comparación:** Comparación sobre totales globales agregados por moneda con diferencia absoluta y porcentaje; si la base anterior es cero, se muestra explícitamente «Sin base de comparación» (sin porcentaje nulo, infinito o división por cero).
- **Entitlement / Freemium:** En plan Free, acceso al resumen básico del mes actual; en plan Premium, acceso a históricos mensuales, comparaciones entre periodos y filtros por categoría, cuenta, tarjeta, comercio y moneda.
- **Exclusiones explícitas de este incremento:** Rangos de fechas personalizados, comparación analítica por categoría dentro del comparador temporal, y otros indicadores específicos adicionales de crédito, deudas o metas.

### 1.2 Respuesta exacta del Product Owner
> **«Aprobar este alcance incremental»**

### 1.3 Efecto y formalización
- **T021 (GATE-ANALYTICS-04):** Se registra como **CUMPLIDA / CERRADA** para este alcance incremental de analítica financiera.
- **Estado de HU-41:** El incremento implementado en código y validado con pruebas unitarias (`AnalyticsOperatingRulesTest`, `AnalyticsEntitlementTest`), persistencia Room v37 (`AnalyticsDaoTest` en A16 y S24) y presentación Compose (`AnalyticsScreenTest` 6/6 en A16 y S24) cuenta con aprobación formal de producto para su catálogo de métricas y reglas.
- **Límites explícitos (decisión 2026-11-03):** Esta aprobación incremental no constituía prueba de la totalidad del backlog ni completitud del sprint. Estado posterior actualizado por el Owner el 2026-11-08: T027 retirada, T028 cumplida manualmente y T029 sigue abierta a cargo del Owner; ver addendum abajo.

---

## Decisión 2 — Reconciliación de persistencia de eventos incompatibles (HU-49 / FR-012 / Room v37)

### 2.1 Consulta planteada al Product Owner
Se consultó la contradicción entre la especificación previa de FR-012 (que exigía índice único por `(owner, referencia, fuente)` en la tabla `capture_evidence`) y el diseño real desplegado en Room v37 (que define clave primaria `(owner_id, source, event_id)` e índice no único sobre `(owner_id, reference, source)` para permitir que múltiples eventos con la misma referencia puedan coexistir si contienen datos incompatibles sin provocar colisiones destructivas ni fusiones indebidas).

### 2.2 Respuesta exacta del Product Owner
> **«Conservar eventos incompatibles para revision»**

### 2.3 Efecto y formalización
- **Actualización de FR-012:** Se aprueba conservar los eventos con datos incompatibles como registros separados para revisión del usuario, prohibiendo cualquier fusión automática de eventos incompatibles y garantizando que no se dupliquen efectos contables en el ledger.
- **Reconciliación con Room v37:** Se alinea formalmente FR-012, la entidad `capture_evidence`, el plan y las tareas de EP-AUT (`specs/011-ep-aut-captura-inbox/`) con el esquema de Room v37 verificado en hardware:
  - **Clave primaria (PK):** `(owner_id, source, event_id)`.
  - **Índice no único:** `(owner_id, reference, source)`.
  - **Índice por raíz:** `(owner_id, root)`.
- **Regla de correlación con misma referencia:**
  1. Si dos evidencias (notificación y OCR/Share) comparten la misma referencia normalizada y sus datos son compatibles según los invariantes vigentes (importe, moneda, dirección y contraparte), se vinculan al candidato o movimiento raíz existente sin segundo movimiento en el ledger.
  2. Si dos evidencias comparten la misma referencia pero sus datos son **incompatibles**, se conservan separadas como candidatos independientes para revisión del usuario. No se fusionan automáticamente ni se descarta ninguna de ellas.
  3. No se inventan nuevos criterios de compatibilidad ni se altera el comportamiento de producción no probado.
- **Cierre de discrepancia:** Queda superada la discrepancia documental advertida en la revisión cruzada de Opus (`3e5d8d8e`).

---

## Matriz de Impacto en Artefactos de Sprint 9

| Artefacto | Sección afectada | Modificación realizada | Estado resultante |
|---|---|---|---|
| `specs/009-ep-inf-informacion-financiera/spec.md` | Estado, US3, GATE-ANALYTICS-04 | Registrar aprobación formal de catálogo y alcance incremental | T021 cumplida para alcance incremental; estado posterior T027 retirada, T028 cumplida, T029 abierta a cargo del Owner |
| `specs/009-ep-inf-informacion-financiera/plan.md` | Estado, Fase S9, Gates | Registrar resolución de GATE-ANALYTICS-04 conforme a decisión del Owner | Consistente con spec y código |
| `specs/009-ep-inf-informacion-financiera/tasks.md` | Cabecera, T021, cierres S9 | Marcar `[X] T021` y registrar decisiones posteriores del Owner | T014/T028 cumplidas; T027 retirada; T029 abierta a cargo del Owner |
| `specs/009-ep-inf-informacion-financiera/verification-report.md` | Decisiones pendientes, seguimiento | Actualizar GATE-ANALYTICS-04 como resuelto por el Owner | Pendiente T021 retirado |
| `specs/011-ep-aut-captura-inbox/spec.md` | Clarifications #6, FR-012, Key Entities | Actualizar FR-012 y Key Entities a Room v37 (PK owner+fuente+event_id, índice no único) | Reconciliado con Room v37 y decisión Owner |
| `specs/011-ep-aut-captura-inbox/plan.md` | Persistencia Room, estructura | Corregir índice único a índice no único con PK compuesta | Alineado con v37 |
| `specs/011-ep-aut-captura-inbox/tasks.md` | Cabecera y notas v37 | Documentar aprobación de cardinalidad física de Room v37 | Tareas S9 consistentes |
| `specs/011-ep-aut-captura-inbox/verification-report.md` | Revisión cruzada Opus, seguimiento | Retirar la contradicción de FR-012 de los pendientes; registrar aprobación | Contradicción resuelta |
| `specs/sprint-9-device-validation.md` | Decisiones de producto (líneas 282-283) | Actualizar estado: T021 aprobada y FR-012 reconciliado; mantener criterios abiertos | Estado general PARCIAL, sin sobrecertificar |
| `verification-report.md` (raíz) | Cabecera y notas de estado | Registrar ambas decisiones del Owner en el addendum Sprint 9 | Coherente con reportes de épicas |
| `docs/producto/trazabilidad-y-dependencias.md` | EP-INF (HU-41) y EP-AUT (HU-49) | Actualizar estado de Sprint 9 con T021 cumplida y FR-012 reconciliado | Trazabilidad sincronizada |

---

## Pendientes que Permanecen Abiertos en Sprint 9

### Decisiones del Product Owner — 2026-11-08 (Lima)

El Owner comunicó las siguientes decisiones en el chat oficial de Kipu:

- **T027 (TalkBack hablado): RETIRADA del alcance** por decisión del Owner; no es necesaria. No se marca como cumplida.
- **T028 (journeys integrados): CUMPLIDA**. El Owner probó manualmente los flujos integrados de errores, reintentos y cambio de propietario y confirmó que funcionan.
- **T014 (lectura offline real en Inicio): CUMPLIDA**. El Owner la probó manualmente y confirmó que funciona.
- **Criterios de hardware probados manualmente por el Owner:** compartir UI real desde Yape, notificación viva de Yape y creación de cambio offline funcionan.
- **Responsabilidad exclusiva del Owner, fuera del flujo de agentes y aún sin cerrar:** T029 aceptación integral (el Owner prevé mejoras posteriores a los diez sprints), recuperación remota del cambio offline, eliminación de cuenta por UI con cuenta desechable, pruebas en A16 y las ocho muestras privadas.

### Pendientes y responsabilidades al 2026-11-08

1. **T027:** retirada del alcance; no cuenta como completada.
2. **T028:** cumplida por prueba manual del Owner.
3. **T014:** cumplida por prueba manual del Owner.
4. **T029:** abierta y a cargo del Owner; no se declara aceptación integral.
5. **Criterios de hardware:** compartir UI real desde Yape, notificación viva de Yape y creación offline probados y aceptados manualmente por el Owner. Recuperación remota del cambio offline, eliminación UI con cuenta desechable, A16 y ocho muestras privadas quedan a cargo del Owner, fuera del flujo de agentes.
6. **Backend recovery v3:** probado y validado (15 Deno, 100 pgTAP, 10 Android API QA) en [specs/001-ep-aps-acceso-perfil-seguridad/account-deletion-verification.md](001-ep-aps-acceso-perfil-seguridad/account-deletion-verification.md); no se repiten pruebas.
7. **Veredicto Sprint 9:** el Owner acepta lo probado según estas decisiones; **T029 y las responsabilidades expresamente reservadas al Owner permanecen pendientes. No se declara APPROVED integral.**

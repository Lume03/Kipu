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
- **Límites explícitos:** Esta decisión representa la aceptación del alcance incremental especificado; **no constituye prueba de la totalidad del backlog ni completitud de pruebas del sprint**. Las tareas transversales T027 (TalkBack hablado), T028 (integración completa con sesión real) y T029 (verificación funcional y aceptación integral) continúan abiertas.

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
| `specs/009-ep-inf-informacion-financiera/spec.md` | Estado, US3, GATE-ANALYTICS-04 | Registrar aprobación formal de catálogo y alcance incremental; enlazar a esta decisión | T021 cumplida para alcance incremental; T027/T028/T029 abiertas |
| `specs/009-ep-inf-informacion-financiera/plan.md` | Estado, Fase S9, Gates | Registrar resolución de GATE-ANALYTICS-04 conforme a decisión del Owner | Consistente con spec y código |
| `specs/009-ep-inf-informacion-financiera/tasks.md` | Cabecera, T021, Cierres S9 | Marcar `[X] T021`, enlazar decisión; actualizar notas de estado | T021 cumplida; T027–T030 conservan su estado |
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

1. **T027 (TalkBack):** Pruebas de semántica y escala 200 % completadas en S24, pero sin herramienta segura de escucha/captura de audio accesible. Servicio TalkBack no probado en lectura hablada.
2. **T028 (Integración con sesión real):** Recorridos de navegación completados con sesión real en S24 (Inicio, Calendario, Análisis); pendientes journeys integrados de errores, reintentos y cambio de propietario.
3. **T029 (Aceptación integral):** Componente de revisión cruzada completado por Opus (`3e5d8d8e`), pero pendiente la verificación funcional y aceptación integral.
4. **T014 (Offline Inicio):** Sin nueva prueba de lectura offline real en hardware.
5. **Criterios de hardware NO VERIFICADO:**
   - Dispositivo Samsung Galaxy A16 ausente en las tandas de cierre.
   - Compartir UI real desde aplicación Yape instalada.
   - Notificación viva de Yape en ambos órdenes con listener habilitado.
   - 8 muestras privadas del propietario (fixture privado ausente).
   - Crear cambio offline y recuperar remoto con sesión real.
   - Eliminación UI completa con cuenta desechable (emulador sin servicio Android package operativo; validación remota API y recovery v3 verificadas y aprobadas en sus propios reportes).
6. **Backend recovery v3:** Probado y validado (15 Deno, 100 pgTAP, 10 Android API QA) en [specs/001-ep-aps-acceso-perfil-seguridad/account-deletion-verification.md](001-ep-aps-acceso-perfil-seguridad/account-deletion-verification.md). No se repiten pruebas.
7. **Veredicto general:** **Aceptación PARCIAL del Sprint 9; NO se declara APPROVED integral.**

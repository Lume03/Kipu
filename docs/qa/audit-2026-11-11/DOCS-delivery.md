# Entrega de Documentación: Decisiones de Producto del 2026-11-11

**Fecha de entrega**: 2026-11-12  
**Worktree**: `C:\Users\Alume\orca\workspaces\Kipu\audit-fixes`  
**Rama**: `Lume03/audit-fixes`  
**Tarea**: Trasladar a las especificaciones (`specs/`) y documentación institucional (`docs/`) las 16 decisiones de producto aprobadas el 2026-11-11 (`docs/qa/audit-2026-11-11/INFORME.md` §8), registrando sustituciones y contradicciones sin alterar código ni migraciones.

---

## 1. Tabla: Decisión → Archivo(s) Editado(s) → Requisito Añadido o Sustituido

| Decisión (§8 INFORME.md) | Archivo(s) Editado(s) | Requisito Añadido o Sustituido |
|---|---|---|
| **1. Corrección de apertura (L1)** | `specs/002-ep-cta-cuentas-tarjetas/spec.md`<br>`specs/002-ep-cta-cuentas-tarjetas/contracts/sync-contract.md`<br>`specs/002-ep-cta-cuentas-tarjetas/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Actualizado RN-10 y HU-07**: La corrección de apertura se publica en el feed de sync como proyección completa de la cuenta (saldo de apertura efectivo + hechos de apertura, reversiones y ajustes), con el mismo patrón de transacciones. Comprobación de solo lectura en remoto pendiente de permiso para verificar cuentas con más de un ajuste. |
| **2. Reembolsos (L2)** | `specs/004-ep-mov-movimientos-ledger/spec.md`<br>`specs/004-ep-mov-movimientos-ledger/contracts/refund-transaction-v1.md`<br>`specs/004-ep-mov-movimientos-ledger/data-model.md`<br>`specs/002-ep-cta-cuentas-tarjetas/spec.md`<br>`specs/002-ep-cta-cuentas-tarjetas/contracts/credit-commands.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Sustituido FR-023, FR-025, RN-MOV-011, RF-C11, HU-13, HU-25**: En gastos normales se elimina el reembolso (cubierto por anulación o edición de monto). Compra con tarjeta por error se elimina con un toque si no tiene cuotas con pagos aplicados; con pagos se bloquea y ofrece reembolso. Reembolsos quedan exclusivamente para compras reales con tarjeta y extornos de importación. Cualquier reembolso puede eliminarse del historial restaurando el impacto de la compra original. Fecha de reembolso validada en rango `[fecha compra, hoy]`. |
| **3. Comandos rechazados (L3)** | `specs/004-ep-mov-movimientos-ledger/spec.md`<br>`specs/004-ep-mov-movimientos-ledger/contracts/sync-and-deduplication.md`<br>`specs/002-ep-cta-cuentas-tarjetas/contracts/sync-contract.md`<br>`docs/arquitectura/vision-general.md`<br>`docs/control-documental.md` | **Añadido FR-026 y actualizado contrato de sync**: Unifica el patrón de comandos rechazados en todos los módulos: elemento marcado como «No sincronizado» con acciones «Reintentar» (si es temporal) y «Usar la versión del servidor». Altas rechazadas muestran aviso más acción «Eliminar» y no cuentan en saldos. Se suprimen pantallas complejas de fusión campo a campo. |
| **4. Pull compartido (L4)** | `specs/004-ep-mov-movimientos-ledger/contracts/sync-and-deduplication.md`<br>`specs/002-ep-cta-cuentas-tarjetas/contracts/sync-contract.md`<br>`docs/arquitectura/vision-general.md`<br>`docs/control-documental.md` | **Añadido al contrato de sincronización**: Se unifica en un solo pull transversal compartido que reparte entidades por tipo, programado en lote propio después de L1 a L3. |
| **5. Deuda de la tarjeta (L9)** | `specs/002-ep-cta-cuentas-tarjetas/spec.md`<br>`specs/002-ep-cta-cuentas-tarjetas/contracts/credit-commands.md`<br>`specs/002-ep-cta-cuentas-tarjetas/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Actualizado RN-15, RF-C04 y HU-13**: La deuda de la tarjeta incluye el interés de las compras confirmadas con cuotas con interés. El simulador de cuotas sigue sin generar deuda contable. |
| **6. Recurrencias (L9)** | `specs/007-ep-rec-recurrencias-obligaciones/spec.md`<br>`specs/007-ep-rec-recurrencias-obligaciones/contracts/recurrence-command-contract.md`<br>`specs/007-ep-rec-recurrencias-obligaciones/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Sustituido FR-004 y HU-33**: La próxima fecha de la recurrencia se calcula en tiempo de ejecución a partir de la regla (`start_date`, periodicidad, intervalo) y no se persiste en la columna estática `next_due_date` de la base de datos. La pantalla dedicada para editar recurrencias queda aplazada/diferida. |
| **7. Ciclo de tarjeta (L9)** | `specs/002-ep-cta-cuentas-tarjetas/spec.md`<br>`specs/002-ep-cta-cuentas-tarjetas/contracts/credit-commands.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Sustituido RF-C04, RF-C05, RF-C12, HU-13**: Se sustituye el cierre exclusivo del 2026-11-05 (D-20261105-01): el día de corte pertenece formalmente al ciclo que cierra (corte inclusivo). El día de pago no puede coincidir con el día de corte (validado en alta, edición y dominio). Comprobación con estado de cuenta real BCP requerida. |
| **8. Condonaciones (L6)** | `specs/005-ep-deu-deudas-prestamos/spec.md`<br>`specs/005-ep-deu-deudas-prestamos/contracts/debt-command-contract.md`<br>`specs/005-ep-deu-deudas-prestamos/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Añadido FR-018a y actualizado CLOSE_DEBT / HU-29**: En condonaciones y ajustes de deuda (FORGIVE y ADJUST), la fecha del hecho es la del registro offline (`occurred_at`). El RPC y el dominio aceptan `occurred_at` del comando y validan que no sea fecha futura (`<= now()`) ni anterior a la fecha de apertura (`opened_on`) de la obligación. |
| **9. Privacidad (L8)** | `specs/001-ep-aps-acceso-perfil-seguridad/spec.md`<br>`specs/001-ep-aps-acceso-perfil-seguridad/contracts/local-protection-contract.md`<br>`specs/009-ep-inf-informacion-financiera/spec.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Actualizado FR-020, FR-027, FR-015 (EP-INF), HU-03, HU-04**: Se habilita `FLAG_SECURE` en la ventana mientras el bloqueo local esté activo (en builds release; nunca en debug). Los widgets de sistema muestran «Kipu bloqueado» mientras la app permanezca bloqueada. El enmascarado de saldos se aplica universalmente en todas las pantallas vía `MoneyText`. |
| **10. Contraseñas (L8)** | `specs/001-ep-aps-acceso-perfil-seguridad/spec.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Actualizado FR-001 y HU-01**: Se aceptan letras Unicode en contraseñas. La validación exige «al menos una letra y un número en cualquier alfabeto» (8–72 caracteres), normalizando el texto a NFC tanto en el cliente como en el servidor/Edge Function. |
| **11. Billing (L7)** | `specs/012-ep-pla-planes-monetizacion/spec.md`<br>`specs/012-ep-pla-planes-monetizacion/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Actualizado HU-53, HU-54 y verificación de compra**: En `verify-purchase`, el parámetro `obfuscatedAccountId` es obligatorio para vincular la compra con el usuario autenticado (`auth.uid()`). Se comprueba en Play Console compras históricas sin ese dato; solo si existen, se admite vinculación por posesión del token para compras anteriores a una fecha de corte fija. La oferta comercial muestra el precio real consultado a Google Play y se elimina el texto estático «AHORRA 40%» (salvo cálculo dinámico con precios Play). |
| **12. Categorías - Conflictos y reglas (L4 y L12)** | `specs/003-ep-cco-categorias-comercios/spec.md`<br>`specs/003-ep-cco-categorias-comercios/contracts/ui-contract.md`<br>`specs/003-ep-cco-categorias-comercios/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Sustituido FR-018, SC-008, actualizado FR-033 y HU-14**: Sustituye la resolución manual de conflictos de presentación por Last-Write-Wins (gana el último en escribir). Se elimina `CategoryConflictScreen`. Las reglas personales de alias de comercios se sincronizan dentro del sync normal. |
| **13. UI (L10)** | `specs/004-ep-mov-movimientos-ledger/spec.md`<br>`specs/009-ep-inf-informacion-financiera/spec.md`<br>`specs/003-ep-cco-categorias-comercios/spec.md`<br>`specs/003-ep-cco-categorias-comercios/contracts/ui-contract.md`<br>`specs/002-ep-cta-cuentas-tarjetas/spec.md`<br>`docs/referencias/peru-financiero/catalogo-tarjetas.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Actualizado FR-016 (EP-MOV), FR-011 (EP-INF), FR-021a (EP-CCO), RF-C03, RF-C08, HU-08, HU-14, HU-22, HU-41**: Se quita la comparación con el mes anterior de los filtros de movimientos. En Análisis (HU-41) se mantiene la comparación entre periodos y se amplía a ingresos además de gastos. En el selector directo de categorías, tocar una raíz con hijos abre subcategorías e incluye fila «Usar "<raíz>"». BanBif se incluye en cuentas de débito por su código guardado (`bank_code`). El catálogo de tasas SBS muestra «Sin dato SBS» en productos sin tasa oficial en vez de cifras de ejemplo. |
| **14. Zona horaria (L9)** | `specs/008-ep-pre-presupuestos-consumos/spec.md`<br>`specs/008-ep-pre-presupuestos-consumos/data-model.md`<br>`docs/producto/backlog.md`<br>`docs/control-documental.md` | **Añadido FR-020 y actualizado `get_budget_status` / HU-36**: Zona horaria de negocio fija a `America/Lima`. El valor por defecto de `time_zone` en el RPC de presupuestos (`get_budget_status`) pasa de `UTC` a `America/Lima`. La columna o preferencia `user_profiles.time_zone` queda sin usar. |
| **15. Repositorio y gobernanza (L0) [Sin épica]** | `docs/control-documental.md` | **Añadido en Decisiones de producto 2026-11-11 sin épica**: Se versionan obligatoriamente `AGENTS.md` y `CLAUDE.md`. Configuración `.claude/settings.json` y `skills-lock.json` se versionan condicional a clonación compartida. Revisión de capturas de `docs/referencias/` para migrar datos reales a `_local-only/`. |
| **16. Estado de despliegue remoto [Sin épica]** | `docs/control-documental.md` | **Añadido en Decisiones de producto 2026-11-11 sin épica**: Se registra la verificación remota: 116 migraciones aplicadas en remoto y 9 edge functions activas. MOV2-01 bloquea solo al dispositivo que originó el reembolso y MOV2-13 es código muerto probable. Queda pendiente comprobar coincidencia exacta de código de edge functions. |

---

## 2. Lista de Contradicciones Encontradas y Resueltas

Durante la incorporación de las decisiones se detectaron y resolvieron formalmente las siguientes contradicciones con especificaciones y decisiones previas:

1. **Ciclo de corte de tarjeta de crédito (Decisión 7 vs D-20261105-01)**:
   - *Texto anterior*: El 2026-11-05 la decisión D-20261105-01 fijó un corte exclusivo: compras realizadas en la fecha de corte pertenecían al ciclo posterior.
   - *Texto nuevo (sustitución)*: La Decisión 7 revirtió esta regla: el día de corte pertenece al ciclo que cierra (corte inclusivo). Adicionalmente, el día de pago no puede coincidir con el día de corte.
2. **Reembolsos en gastos normales (Decisión 2 vs HU-25 / FR-023 original)**:
   - *Texto anterior*: FR-023 y el contrato `refund-transaction-v1.md` preveían reembolsos generales para cualquier gasto operativo (`EXPENSE`), requiriendo selección de cuenta destino.
   - *Texto nuevo (sustitución)*: La Decisión 2 eliminó el reembolso en gastos normales (cubiertos mediante anulación o edición de importe). Los reembolsos quedan restringidos a compras reales con tarjeta de crédito y extornos de importación.
3. **Resolución de conflictos en categorías (Decisión 12 vs SC-008 & FR-018 original)**:
   - *Texto anterior*: SC-008 y FR-018 exigían almacenar ambas versiones de presentación ante colisión offline y forzar una pantalla de resolución manual (`CategoryConflictScreen`).
   - *Texto nuevo (sustitución)*: La Decisión 12 determinó que los conflictos de presentación (nombre, icono, color) se resuelven automáticamente mediante Last-Write-Wins (gana el último en escribir) y eliminó la pantalla `CategoryConflictScreen`.
4. **Persistencia de próxima fecha de recurrencia (Decisión 6 vs FR-004 y data-model)**:
   - *Texto anterior*: `next_due_date` figuraba como columna obligatoria y persistida en la tabla `recurrences`, recalculada estáticamente tras mutaciones.
   - *Texto nuevo (sustitución)*: La Decisión 6 dictaminó que la próxima fecha se calcula dinámicamente en tiempo de ejecución a partir de la periodicidad, intervalo y fecha de inicio (`start_date`), sin guardarse en la columna `next_due_date`. La pantalla dedicada para editar recurrencias queda aplazada.
5. **Fecha del hecho en condonaciones de deuda (Decisión 8 vs RPC server `now()`)**:
   - *Texto anterior*: El handler de cierre de deudas en servidor estampaba `now()` en el evento al recibirse la llamada, alterando la fecha efectiva cuando el registro se realizaba offline.
   - *Texto nuevo (sustitución)*: La Decisión 8 fijó que la fecha del hecho es la del registro offline (`occurred_at`). El RPC acepta `occurred_at` y valida que esté comprendida entre la fecha de apertura (`opened_on`) y `now()`.
6. **Zona horaria por defecto en presupuestos (Decisión 14 vs migración SQL base)**:
   - *Texto anterior*: El RPC `get_budget_status` utilizaba por defecto `time_zone = 'UTC'` y se contemplaba la columna `user_profiles.time_zone`.
   - *Texto nuevo (sustitución)*: La Decisión 14 unificó la zona horaria en `America/Lima` fija, cambiando el valor por defecto del RPC a `America/Lima` y declarando `user_profiles.time_zone` en desuso para presupuestos.

---

## 3. Verificación de Integridad: `git status --short`

La siguiente salida demuestra que por parte de este worker únicamente se modificaron o crearon archivos `.md` dentro de `specs/` y `docs/` (incluyendo `docs/qa/audit-2026-11-11/DOCS-delivery.md` como entregable autorizado). Los archivos `.kt` en `app/src/main/` corresponden al trabajo en paralelo del worker de código en este worktree y no fueron intervenidos en esta tarea de documentación:

```text
 M app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt
 M app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryDelete.kt
 M docs/arquitectura/vision-general.md
 M docs/control-documental.md
 M docs/producto/backlog.md
 M docs/referencias/peru-financiero/catalogo-tarjetas.md
 M specs/001-ep-aps-acceso-perfil-seguridad/contracts/local-protection-contract.md
 M specs/001-ep-aps-acceso-perfil-seguridad/spec.md
 M specs/002-ep-cta-cuentas-tarjetas/contracts/credit-commands.md
 M specs/002-ep-cta-cuentas-tarjetas/contracts/sync-contract.md
 M specs/002-ep-cta-cuentas-tarjetas/data-model.md
 M specs/002-ep-cta-cuentas-tarjetas/spec.md
 M specs/003-ep-cco-categorias-comercios/contracts/ui-contract.md
 M specs/003-ep-cco-categorias-comercios/data-model.md
 M specs/003-ep-cco-categorias-comercios/spec.md
 M specs/004-ep-mov-movimientos-ledger/contracts/refund-transaction-v1.md
 M specs/004-ep-mov-movimientos-ledger/contracts/sync-and-deduplication.md
 M specs/004-ep-mov-movimientos-ledger/data-model.md
 M specs/004-ep-mov-movimientos-ledger/spec.md
 M specs/005-ep-deu-deudas-prestamos/contracts/debt-command-contract.md
 M specs/005-ep-deu-deudas-prestamos/data-model.md
 M specs/005-ep-deu-deudas-prestamos/spec.md
 M specs/007-ep-rec-recurrencias-obligaciones/contracts/recurrence-command-contract.md
 M specs/007-ep-rec-recurrencias-obligaciones/data-model.md
 M specs/007-ep-rec-recurrencias-obligaciones/spec.md
 M specs/008-ep-pre-presupuestos-consumos/data-model.md
 M specs/008-ep-pre-presupuestos-consumos/spec.md
 M specs/009-ep-inf-informacion-financiera/spec.md
 M specs/012-ep-pla-planes-monetizacion/data-model.md
 M specs/012-ep-pla-planes-monetizacion/spec.md
?? docs/qa/audit-2026-11-11/DOCS-delivery.md
```
